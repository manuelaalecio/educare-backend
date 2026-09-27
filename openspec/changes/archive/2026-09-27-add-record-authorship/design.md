# Design

## Context

Motivação em proposal.md; comportamento em `specs/persistence/spec.md`.

Estado atual:

- `shared/persistence/BaseEntity` tem `id` (UUIDv7), `createdAt` (`@CreatedDate`, `updatable = false`) e `updatedAt` (`@LastModifiedDate`), com `AuditingEntityListener`.
- `JpaAuditingConfiguration` liga `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")`, com as datas vindas do bean `Clock`. Os `@DataJpaTest` a importam explicitamente (ex.: `UserRepositoryTest`).
- Em toda requisição autenticada, o `SecurityContext` tem um `AuthenticatedUserAuthentication`, cujo principal é `AuthenticatedUser(UUID id, String role)` (`shared/security`).
- Só a tabela `users` estende `BaseEntity` em produção; o administrador inicial é inserido por SQL na `V3`, sem passar pelo JPA. Nos testes, `test_audited_entity` (migration repetível `R__test_support_schema.sql`) exercita a `BaseEntity`.
- Nenhuma escrita acontece em requisição sem autenticação: o `POST /api/v1/auth/login` só lê.

## Goals / Non-Goals

**Goals:**
- Autoria preenchida pelo mesmo mecanismo das datas (auditoria do Spring Data), sem código nos services dos módulos.
- `@DataJpaTest` e testes sem requisição continuam funcionando, e podem gravar como um usuário quando a tabela exigir autoria.

**Non-Goals:**
- Nenhuma mudança nos DTOs, mappers ou respostas da API.
- Nenhuma tabela de log de auditoria (quem leu, quem excluiu).

## Decisions

### D1. Campos na `BaseEntity` via `@CreatedBy`/`@LastModifiedBy`

```java
@CreatedBy
@Column(name = "created_by", updatable = false)
private UUID createdBy;

@LastModifiedBy
@Column(name = "updated_by")
private UUID updatedBy;
```

- Mesmo listener que já preenche as datas: `@PrePersist` define os dois autores (o Spring Data também preenche o "modified" na criação), e `@PreUpdate` só o `updatedBy`. O `@PreUpdate` só roda quando o Hibernate grava uma alteração, então requisições recusadas (validação, 404, 409 com rollback) não mexem na autoria.
- `updatable = false` no `created_by` garante no mapeamento que o autor da criação não muda, como já acontece com o `created_at`.
- O mapeamento é nullable; a obrigatoriedade fica na coluna de cada tabela (D3).
- *Alternativa*: preencher a autoria nos services. Descartada: repetiria código em todo módulo e seria fácil de esquecer.

### D2. Auditor a partir do usuário autenticado

- Nova classe `shared/security/AuthenticatedUserAuditor implements AuditorAware<UUID>`: lê o `SecurityContextHolder` e devolve o `id` quando o principal é um `AuthenticatedUser`; senão (sem autenticação, anônimo, outro tipo de principal), `Optional.empty()`.
- O bean é declarado na própria `JpaAuditingConfiguration` (`@EnableJpaAuditing(dateTimeProviderRef = …, auditorAwareRef = "auditingAuditorProvider")`), e não numa configuração de segurança, para que os `@DataJpaTest`, que já importam essa configuração, tenham o auditor sem carregar o Spring Security.
- `shared/persistence` passa a depender de `shared/security` (só do `AuthenticatedUser` e do auditor). É dependência dentro de `shared`, permitida pelas regras do CLAUDE.md; nenhum módulo de negócio entra.
- *Alternativa*: guardar o email do autor. Descartada: é dado pessoal e mudaria quando o login mudasse; o id é estável.

### D3. Schema e convenção

- Nova migration `V4__add_authorship_to_users.sql`:
  ```sql
  -- Autoria (quem criou / quem alterou por último). Sem FK para users de propósito: excluir um usuário
  -- não pode apagar nem bloquear a trilha dos registros que ele criou ou alterou.
  ALTER TABLE users
      ADD COLUMN created_by uuid,
      ADD COLUMN updated_by uuid;
  ```
  Nullable em `users`: o administrador inicial e as linhas já existentes não têm autor.
- **Convenção nova** para toda tabela de negócio: `created_by uuid` e `updated_by uuid`, sem FK. `NOT NULL` quando toda linha é sempre gravada por uma requisição autenticada (caso de `children`); nullable só quando a tabela pode ter linhas gravadas sem usuário (seed por migration), com o motivo num comentário da migration. Registrada no CLAUDE.md (Armadilhas, "Schema só por migration") e no `openspec/config.yaml` (Persistência).
- `R__test_support_schema.sql` (repetível, só teste): `test_audited_entity` ganha `created_by uuid` e `updated_by uuid`, nullable, para testar os dois casos (com e sem usuário). Como os bancos de teste são sempre novos, basta mudar o `CREATE TABLE`.
- **Numeração**: esta change fica com a `V4`; a `add-child-crud` passa a criar `children` na `V5`, com as colunas de autoria `NOT NULL`.

### D4. Apoio de teste

- Nova classe `shared/testsupport/AuthenticatedAs` com `run(UUID userId, Runnable)` e `call(UUID userId, Supplier<T>)`: coloca um `AuthenticatedUserAuthentication` no `SecurityContextHolder` durante a execução e restaura o contexto anterior no `finally`. Usada em `@DataJpaTest` e testes de service que gravam em tabelas com autoria `NOT NULL`.
- Os testes web e de cenário não precisam dela: a autoria vem do token real, como em produção.

## Risks / Trade-offs

- [Id de usuário excluído] Depois da exclusão, o id na autoria não leva a nenhum usuário, e a trilha não diz mais o nome de quem fez → Aceito por decisão do produto (trilha preservada, exclusão livre); se for preciso identificar autores excluídos, uma change futura troca a exclusão de usuários por desativação.
- [Escrita em thread sem contexto] Tarefas assíncronas ou agendadas futuras gravariam sem autoria (o `SecurityContext` não se propaga) → Hoje não existem; quem introduzir uma deve decidir o autor (ex.: usuário de sistema) e, nas tabelas `NOT NULL`, o banco recusa a gravação sem autor em vez de gravar silenciosamente.
- [Alteração sem mudança real] O Hibernate só grava quando algum campo muda, então um `PUT` com os mesmos dados não atualiza `updated_by` nem `updated_at` → Coerente com o comportamento atual do `updated_at`.
- [Dependência `persistence` → `security` dentro de `shared`] → Limitada a duas classes; se crescer, o auditor pode ser movido para trás de uma interface em `shared/persistence`.

## Migration Plan

- A `V4` só adiciona colunas nullable: é instantânea e compatível com a versão anterior da aplicação, que ignora as colunas novas. Rollback é voltar a imagem; a `V4` continua aplicada sem efeito.
