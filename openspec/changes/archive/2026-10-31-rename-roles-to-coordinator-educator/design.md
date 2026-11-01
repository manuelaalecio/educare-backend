# Design

## Context

- A role é o enum `user/domain/Role` (`ADMIN`, `USER`), gravado como texto em `users.role` (`varchar(20)`, `CHECK (role IN ('ADMIN', 'USER'))`, `DEFAULT 'USER'`, da `V2`). A `V3` insere o administrador inicial como `ADMIN`.
- A API recebe a role como `String` validada por `@Pattern("ADMIN|USER")` em `CreateUserRequest` e `UpdateUserRequest`, e o `UserMapper.toRole` converte com `Role.valueOf`. `UserResponse` e `UserAccount` devolvem `role.name()`.
- A autorização é por `@PreAuthorize("hasRole('ADMIN')")` na classe de `UserController` e de `ClassroomController`. O `AuthenticatedUserLookup` lê a role do banco a cada requisição e monta a authority `ROLE_<role>`. O JWT não carrega a role.
- `UserService` garante que sempre exista um `ADMIN` (`ensureAnotherAdminRemainsWhenLosingAdmin`, com `findAllByRoleForUpdate(Role.ADMIN)` e `LastAdminException`, `409`).
- O deploy é rolling update de uma réplica (`maxSurge: 1`, `maxUnavailable: 0`): o pod novo roda o Flyway enquanto o antigo ainda atende.
- O front (outro repositório) usa os valores da role para decidir o menu e precisa subir junto (ver proposal).

## Goals / Non-Goals

**Goals:**
- Trocar os valores da role de ponta a ponta (banco, domínio, validação, autorização, contrato) numa única versão.
- Manter os tokens já emitidos válidos, sem exigir novo login.

**Non-Goals:**
- Compatibilidade com a versão anterior durante o rolling update (decisão D5).
- Mudar o nome do campo `role` na API ou a forma de autorização.

## Decisions

### D1. Constantes do enum em português: `COORDENADOR` e `EDUCADOR`

O enum é gravado e exposto pelo `name()`, então a constante é o valor da API. Ficam em português porque é o valor que o front e o banco usam, como a tarefa define. O nome das classes e métodos continua em inglês (`LastCoordinatorException`, `ensureAnotherCoordinatorRemains...`).

*Alternativa*: constantes em inglês (`COORDINATOR`, `EDUCATOR`) com um campo de valor para a API e um `AttributeConverter` para o banco. Foi descartada porque traz mapeamento extra em três lugares (JSON, banco, authority do Spring Security) só para seguir a convenção de nomes.

O glossário do `openspec/config.yaml` ganha Coordenador(a) → `COORDENADOR` e Educador(a) → `EDUCADOR` (valores da role).

### D2. Migration `V7__rename_roles_to_coordinator_educator.sql`

Em uma transação (o Flyway já aplica cada migration do PostgreSQL numa transação):

```sql
ALTER TABLE users DROP CONSTRAINT users_role_check;
UPDATE users SET role = CASE role WHEN 'ADMIN' THEN 'COORDENADOR' WHEN 'USER' THEN 'EDUCADOR' END;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('COORDENADOR', 'EDUCADOR'));
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'EDUCADOR';
```

- O `UPDATE` não mexe em `updated_at` nem em `updated_by`: é uma conversão de dados, não uma alteração feita por um usuário, e a auditoria do Spring Data não roda em SQL.
- Os valores cabem no `varchar(20)` atual.
- O `CASE` sem `ELSE` daria `NULL` para um valor desconhecido e falharia no `NOT NULL`. Isso é o comportamento desejado, mas não acontece porque o `CHECK` antigo só permitia os dois valores.
- `V2` e `V3` não são editadas. Num banco vazio, a `V3` cria o admin como `ADMIN` e a `V7` o converte.

### D3. Código

- `Role`: `COORDENADOR`, `EDUCADOR`. O construtor `User(name, login, passwordHash)` e o `UserService.create` com `role == null` usam `EDUCADOR`.
- `LastAdminException` é renomeada para `LastCoordinatorException`, com a mensagem "O sistema precisa ter ao menos um(a) coordenador(a)". O método do `UserService` passa a ser `ensureAnotherCoordinatorRemainsWhenLosingCoordinator`, com `findAllByRoleForUpdate(Role.COORDENADOR)`.
- `CreateUserRequest`/`UpdateUserRequest`: `@Pattern(regexp = "COORDENADOR|EDUCADOR", message = "deve ser COORDENADOR ou EDUCADOR")`.
- `UserController` e `ClassroomController`: `@PreAuthorize("hasRole('COORDENADOR')")`. Javadocs e comentários que citam `ADMIN` (`UserService`, `UserAccount`, `AuthenticatedUser`, DTOs) acompanham.
- Nada muda em `shared/security`: a authority é montada a partir do valor do banco, que passa a ser `ROLE_COORDENADOR`/`ROLE_EDUCADOR`.

### D4. Endpoints

Nenhum endpoint novo, removido ou com status diferente. Muda só quem acessa e o valor de `role`:

| Método | Rota | Sucesso | Erros | Role exigida |
|---|---|---|---|---|
| `POST` | `/api/v1/users` | `201` | `400` (inclusive `role` `ADMIN`/`USER`), `401`, `403`, `409` | `COORDENADOR` |
| `GET` | `/api/v1/users`, `/api/v1/users/{id}` | `200` | `400`, `401`, `403`, `404` | `COORDENADOR` |
| `PUT` | `/api/v1/users/{id}` | `200` | `400`, `401`, `403`, `404`, `409` (login repetido ou último coordenador) | `COORDENADOR` |
| `PUT` | `/api/v1/users/{id}/password` | `204` | `400`, `401`, `403`, `404` | `COORDENADOR` |
| `DELETE` | `/api/v1/users/{id}` | `204` | `400`, `401`, `403`, `404`, `409` (a si mesmo ou último coordenador) | `COORDENADOR` |
| todos | `/api/v1/classrooms/**` | como hoje | como hoje (`403` para `EDUCADOR`) | `COORDENADOR` |
| `GET` | `/api/v1/auth/me` | `200` | `401` | qualquer autenticado |

No contrato, o `pattern` de `role` nos dois requests passa a ser `COORDENADOR|EDUCADOR`. `UserResponse.role` continua `string` sem enum.

### D5. Uma única versão, aceitando a janela de erro no rolling update

Durante o rolling update, depois que o pod novo aplica a `V7`, o pod antigo não converte `COORDENADOR`/`EDUCADOR` para o enum dele e responde `500` em toda requisição autenticada, até sair. Uma escrita dele com `ADMIN`/`USER` também seria recusada pelo `CHECK` novo. A janela dura o tempo em que o pod novo fica pronto (dezenas de segundos).

Isso contraria a regra de migrations compatíveis com a versão anterior (CLAUDE.md). A exceção foi decidida com a dona do projeto para esta change, porque:
- o front precisa subir junto de qualquer jeito, e o front antigo já fica errado com o back novo;
- o sistema é interno e tem poucos usuários, então o deploy pode ser feito num horário sem uso.

*Alternativas descartadas*:
- Expand/contract em duas versões: uma primeira release com um `AttributeConverter` que lê os quatro valores e continua gravando os antigos, e uma segunda com a `V7`. Evita a janela, mas exige dois deploys coordenados e código temporário.
- Trocar a strategy do Deployment para `Recreate`: troca os `500` por indisponibilidade total, sem ganho real.

### D6. Testes

- `RoleMigrationScenarioTest` (cenários do requirement "Migração das roles existentes"):
  - Usa um `PostgreSQLContainer` próprio, e não o `TestcontainersConfiguration` compartilhado, porque precisa do banco parado na `V6` antes de a aplicação subir. O motivo fica num comentário da classe.
  - Aplica as migrations até a `V6` com a API do Flyway (`target("6")`, placeholder `admin_password`).
  - Insere um `ADMIN` e um `USER` com `JdbcTemplate`, e então sobe o contexto Spring contra esse banco, o que aplica a `V7`.
  - Verifica os dados convertidos, a recusa do `CHECK` a `ADMIN` e o `GET /api/v1/users` com um token do admin. Como o token não carrega a role, um token emitido pelo `AccessTokenIssuer` para o mesmo id é idêntico a um emitido antes da migração. O cenário usa um token com `iat` anterior à subida do contexto, gerado com o `Clock` do teste.
- Os demais testes trocam os valores: `TestUsers` e `AccessTokens` recebem `COORDENADOR`/`EDUCADOR`, `AdminSeedScenarioTest` espera `COORDENADOR`, e os testes de controller, service, domínio e cenário que usam `ADMIN`/`USER` acompanham.
- Novos casos explícitos:
  - `400` para `ADMIN` e para `USER` no `POST` e no `PUT`, com a mensagem;
  - mensagem do `409` do último coordenador;
  - `EDUCADOR` recebendo `403` nas duas rotas.
- `ApiContractContentTest`: o `pattern` de `role` nos dois requests.

## Risks / Trade-offs

- [Janela de `500` no rolling update (D5)] → Deploy num horário sem uso, junto com o front. A janela termina sozinha quando o pod antigo sai.
- [Rollback para a imagem anterior depois da `V7`] → A imagem antiga não entende os valores novos e responde `500`. O rollback de código exige reverter os dados à mão (ver Migration Plan). O Flyway não tem `undo` nem `clean`.
- [Front e back fora de sincronia] → Com só o back novo, o front antigo esconde Configurações de todos. Com só o front novo, ninguém vê Configurações. Os dois sobem na mesma janela.
- [Change `add-child-crud` escrita com `ADMIN`/`USER`] → A spec, o design, as tasks e o proposal dela foram atualizados nesta change: exclusão restrita a `COORDENADOR`, acesso de `COORDENADOR` e `EDUCADOR`.
- [Requirement do Actuator em `security` cita `ADMIN`] → Como `migrate-deploy-to-gitops` foi arquivada antes, o delta desta change modifica esse requirement a partir da versão já arquivada, sem conflito.
- [Constantes do enum em português contra a convenção de código em inglês (D1)] → A exceção vale só para os valores da role e está registrada aqui e no glossário.

## Migration Plan

1. Merge com o label `build` e a tag `vX.Y.Z`, combinados com o deploy do front.
2. O Argo CD Image Updater leva a tag ao cluster, o pod novo aplica a `V7` e fica pronto, e o antigo sai. Nenhuma variável ou Secret novo.
3. Validação: login com `admin@educare.org`, `GET /api/v1/auth/me` com `"role": "COORDENADOR"`, `GET /api/v1/users` com `200`, e Configurações visível no front novo.
4. Rollback, se necessário: fixar a tag anterior no `ImageUpdater` **e** reverter os dados à mão no Postgres:
   - `DROP CONSTRAINT users_role_check`;
   - `UPDATE` inverso;
   - `CHECK` e `DEFAULT` antigos;
   - remover a linha da `V7` em `flyway_schema_history`.

   O passo a passo vai para o README, na seção de rollback.
