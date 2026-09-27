# Tasks

## 1. Schema

- [x] 1.1 Criar a nova migration `src/main/resources/db/migration/V4__add_authorship_to_users.sql` (colunas `created_by uuid` e `updated_by uuid`, nullable, sem FK, com o comentário do motivo, D3), sem editar migrations existentes, e adicionar as mesmas colunas (nullable) à `test_audited_entity` em `src/test/resources/db/migration/R__test_support_schema.sql`; verificar com `./gradlew test --tests '*EducareBackendApplicationTests*'` que o contexto sobe

## 2. Auditoria

- [x] 2.1 Criar `shared/security/AuthenticatedUserAuditor` (`AuditorAware<UUID>`, D2), com teste unitário cobrindo: principal `AuthenticatedUser` devolve o id; sem autenticação, autenticação anônima e principal de outro tipo devolvem vazio. Verificar com `./gradlew test --tests '*AuthenticatedUserAuditorTest'`
- [x] 2.2 Adicionar `createdBy` (`@CreatedBy`, `updatable = false`) e `updatedBy` (`@LastModifiedBy`) à `BaseEntity` e o bean `auditingAuditorProvider` com `auditorAwareRef` na `JpaAuditingConfiguration` (D1, D2); estender o `JpaAuditingConfigurationTest` para o novo bean. Verificar com `./gradlew test --tests '*JpaAuditingConfigurationTest'` e com o contexto subindo (`ddl-auto: validate`)
- [x] 2.3 Criar `shared/testsupport/AuthenticatedAs` (`run` e `call`, restaurando o contexto anterior, D4), com `AuthenticatedAsTest` (contexto definido durante a execução, restaurado depois, inclusive quando a execução lança exceção); usar em um `@DataJpaTest` de `test_audited_entity` que verifica: gravação como usuário preenche os dois autores, alteração como outro usuário muda só `updated_by`, gravação sem usuário deixa os dois nulos e `created_by` modificado por reflexão não é regravado. Verificar com `./gradlew test --tests '*AuthenticatedAsTest' --tests '*TestAuditedEntity*'` (ou o teste de repositório escolhido)
- [x] 2.4 Rodar `./gradlew test --tests '*ArchitectureTest'` e verificar que a dependência `shared/persistence` → `shared/security` não viola nenhuma regra (D2)

## 3. Documentação

- [x] 3.1 Atualizar a convenção de schema (D3) no CLAUDE.md (Armadilhas, "Schema só por migration": colunas `created_by`/`updated_by`, sem FK, `NOT NULL` salvo tabelas com linhas gravadas sem usuário, e o `AuthenticatedAs` para testes que gravam fora de requisição) e no `openspec/config.yaml` (Persistência); verificar relendo as duas seções e com `openspec validate add-record-authorship --strict`

## 4. Testes de cenário

- [x] 4.1 Criar `shared/persistence/AuthorshipScenarioTest` (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, usuários criados com `TestUsers` e tokens reais pela API, autoria lida via `JdbcTemplate`) com um teste por Scenario do requirement "Autoria dos registros" de `specs/persistence/spec.md`, cada um com `@DisplayName` referenciando o cenário (o de "autor da criação modificado" usa a `test_audited_entity` com `AuthenticatedAs`); verificar com `./gradlew test --tests '*AuthorshipScenarioTest'` que todos passam e que há um teste por Scenario

## 5. Verificação final

- [x] 5.1 Rodar `./gradlew build` e verificar que passa, incluindo a verificação de cobertura do JaCoCo (mínimo de 90% de linhas e de branches)
