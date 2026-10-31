# Tasks

## 1. Migration

- [x] 1.1 Criar `src/main/resources/db/migration/V7__rename_roles_to_coordinator_educator.sql` conforme a D2: remover o `users_role_check`, fazer o `UPDATE` com `CASE`, recriar o `CHECK (role IN ('COORDENADOR', 'EDUCADOR'))` e definir `DEFAULT 'EDUCADOR'`, com um comentário explicando que o `UPDATE` não mexe em `updated_at`/`updated_by` e que a migration não é compatível com a versão anterior (D5). Nenhuma migration existente é editada. Verificar junto com a task 2.1, porque a aplicação só sobe com o enum novo (`ddl-auto=validate` não valida valores, mas o `AdminSeedScenarioTest` lê o admin).

## 2. Domínio e application do módulo `user`

- [x] 2.1 Trocar `Role` para `COORDENADOR`, `EDUCADOR`, fazer o construtor `User(name, login, passwordHash)` usar `EDUCADOR` e atualizar `UserTest` (role padrão `EDUCADOR`, `changeRole`). Verificar com `./gradlew test --tests '*UserTest'`.
- [x] 2.2 Renomear `LastAdminException` para `LastCoordinatorException`, com a mensagem "O sistema precisa ter ao menos um(a) coordenador(a)", e ajustar o `UserService`: `role == null` → `EDUCADOR`, método `ensureAnotherCoordinatorRemainsWhenLosingCoordinator` com `findAllByRoleForUpdate(Role.COORDENADOR)`, javadocs sem `ADMIN`, e o javadoc de `UserAccount`. Atualizar `UserServiceTest`, inclusive o caso da mensagem da exceção, e `UserRepositoryTest` (`findAllByRoleForUpdate(COORDENADOR)` e a recusa do `CHECK` a `ADMIN` via `JdbcTemplate`). Verificar com `./gradlew test --tests '*UserServiceTest' --tests '*UserRepositoryTest'`.
- [x] 2.3 Atualizar `UserAuthenticationLookupTest` e o javadoc de `shared/security/AuthenticatedUser` (exemplo `COORDENADOR`). Verificar com `./gradlew test --tests '*UserAuthenticationLookupTest'`.

## 3. API dos módulos `user` e `classroom`

- [x] 3.1 Trocar o `@Pattern` de `CreateUserRequest` e `UpdateUserRequest` para `COORDENADOR|EDUCADOR` com a mensagem "deve ser COORDENADOR ou EDUCADOR", atualizar os javadocs, e trocar o `@PreAuthorize` de `UserController` para `hasRole('COORDENADOR')`. Atualizar `UserMapperTest` e `UserControllerTest`:
  - tokens de `COORDENADOR`/`EDUCADOR`;
  - `400` com a mensagem para `ADMIN` e `USER` no `POST` e no `PUT`;
  - `403` para `EDUCADOR`;
  - `409` com o `detail` do último coordenador.

  Verificar com `./gradlew test --tests '*UserControllerTest' --tests '*UserMapperTest'`.
- [x] 3.2 Trocar o `@PreAuthorize` de `ClassroomController` para `hasRole('COORDENADOR')` e atualizar `ClassroomControllerTest` (`200` para `COORDENADOR` e `403` para `EDUCADOR`). Verificar com `./gradlew test --tests '*ClassroomControllerTest'`.
- [x] 3.3 Rodar `./gradlew updateApiContract`, conferir no diff de `openapi/educare-api.json` que só o `pattern` de `role` mudou e atualizar o `ApiContractContentTest` com uma asserção do `pattern` `COORDENADOR|EDUCADOR` nos dois requests. Verificar com `./gradlew test --tests '*ApiContract*'`.

## 4. Suporte de testes e testes transversais

- [x] 4.1 Trocar `ADMIN`/`USER` por `COORDENADOR`/`EDUCADOR` em `shared/testsupport` (`TestUsers`, `AccessTokens`, `AuthenticatedAs`) e nos testes de `auth`, `shared/security` (inclusive `SecurityTestController`) e `shared/persistence`. Ao final, `grep -rnE "\"(ADMIN|USER)\"|Role\.(ADMIN|USER)|hasRole\('ADMIN'\)" src/` não pode achar nada, exceto os testes que verificam a recusa dos valores antigos e a `RoleMigrationScenarioTest`. Verificar com `./gradlew test`.

## 5. Testes de cenário

- [x] 5.1 Criar `user/RoleMigrationScenarioTest` conforme a D6, com um `@DisplayName` por Scenario do requirement "Migração das roles existentes":
  - roles antigas convertidas, com nome, login, hash e datas preservados;
  - token anterior à conversão continua valendo;
  - valor antigo recusado pelo banco.

  O container próprio fica justificado num comentário da classe. Verificar com `./gradlew test --tests '*RoleMigrationScenarioTest'`.
- [x] 5.2 Atualizar os testes de cenário de cada Scenario modificado nas specs desta change, mantendo um teste por Scenario com o `@DisplayName` referenciando o requirement e o cenário (com o nome novo dos requirements renomeados):
  - `UserScenarioTest`: inclusive os dois Scenarios novos de valor antigo recusado e o `detail` do último coordenador;
  - `AdminSeedScenarioTest`: admin com `COORDENADOR`;
  - `AuthScenarioTest`, `SecurityScenarioTest`, `ClassroomScenarioTest`, `ClassroomStudentCountScenarioTest`, `AuthorshipScenarioTest` e os cenários do contrato.

  Verificar com `./gradlew test --tests '*ScenarioTest'` e conferir que todo Scenario de `specs/` desta change tem um teste correspondente.

## 6. Documentação e changes relacionadas

- [x] 6.1 Atualizar o CLAUDE.md:
  - exemplo `@PreAuthorize("hasRole('COORDENADOR')")`;
  - administrador inicial criado pela `V3` e convertido para `COORDENADOR` pela `V7`;
  - exceção das constantes da role em português.

  Atualizar também o `openspec/config.yaml` (glossário: Coordenador(a) → `COORDENADOR`, Educador(a) → `EDUCADOR`) e o README (passo de rollback manual da `V7`, conforme o Migration Plan do design). Verificar relendo os trechos e com `grep -n "ADMIN" CLAUDE.md openspec/config.yaml README.md`: só podem sobrar `EDUCARE_ADMIN_PASSWORD` e o rollback.
- [x] 6.2 Registrar nas changes relacionadas o que precisa acompanhar:
  - em `add-child-crud` (proposal, design, tasks e spec), trocar `ADMIN`/`USER` por `COORDENADOR`/`EDUCADOR`;
  - na change que for arquivada por último entre esta e `migrate-deploy-to-gitops`, atualizar a menção "inclusive de `ADMIN`" e o cenário com role `ADMIN` do requirement "Actuator restrito ao health, sem detalhes" da spec `security`.

  Verificar com `openspec validate --all`.

## 7. Build

- [x] 7.1 Rodar `./gradlew build` e conferir que passa com a verificação de cobertura do JaCoCo (90% de linhas e de branches) e o contrato igual ao gerado.
