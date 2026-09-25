# Tasks

## 1. Configuração do Flyway, JPA e profiles

- [ ] 1.1 Adicionar ao `application.yaml` a configuração comum do Flyway (`locations`, `validate-on-migrate: true`, `clean-disabled: true`, `baseline-on-migrate: false`), do JPA (`ddl-auto: validate`, `open-in-view: false`, `hibernate.jdbc.time_zone: UTC`) e `spring.profiles.default: dev` (design D1, D2, D4); verificar com `./gradlew test` que o `EducareBackendApplicationTests.contextLoads` continua passando
- [ ] 1.2 Criar `application-dev.yaml` (URL `jdbc:postgresql://localhost:5432/app`, usuário `app`, senha por `SPRING_DATASOURCE_PASSWORD`) e `application-prod.yaml` (URL, usuário e senha só por `${SPRING_DATASOURCE_*}`, sem padrão), e definir `SPRING_PROFILES_ACTIVE: prod` no serviço `api` do `docker-compose.yaml`; verificar com `docker compose config` que a variável aparece e subindo `docker compose up -d db` + `SPRING_DATASOURCE_PASSWORD=... ./gradlew bootRun` sem ativar profile
- [ ] 1.3 Criar a nova migration `src/main/resources/db/migration/V1__baseline.sql`, só com o comentário da convenção de schema do design (D3); verificar no `bootRun` da 1.2 que `flyway_schema_history` contém a versão `1` com `success = true`
- [ ] 1.4 Atualizar o CLAUDE.md (Armadilhas: profiles `dev`/`prod`, `bootRun` só com `SPRING_DATASOURCE_PASSWORD`, convenção de colunas das migrations) e o README (Como iniciar o projeto, Opção B); verificar seguindo os comandos do README num terminal limpo

## 2. Base de persistência em `shared`

- [ ] 2.1 Criar `shared/config/ClockConfiguration` (bean `Clock.systemUTC()`) e `shared/persistence/JpaAuditingConfiguration` (`@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")` + bean `DateTimeProvider` baseado no `Clock`), com teste unitário `JpaAuditingConfigurationTest` (`shouldProvideCurrentInstantFromClockWhenAskedForNow`, com `Clock.fixed`); verificar com `./gradlew test`
- [ ] 2.2 Criar `shared/persistence/BaseEntity` (`@MappedSuperclass`, `AuditingEntityListener`, `@Getter`, `id` UUID com `@UuidGenerator` ordenado por tempo, `createdAt` com `updatable = false`, `updatedAt`), conforme D5; verificar que compila e que o contexto sobe com `./gradlew test`
- [ ] 2.3 Criar o suporte de teste em `src/test`: `shared/testsupport/persistence/TestAuditedEntity` e `TestAuditedEntityRepository`, a migration repetível `src/test/resources/db/migration/R__test_support_schema.sql` (tabela `test_audited_entity` seguindo a convenção) e `shared/testsupport/MutableClock` com sua `@TestConfiguration` `@Primary`; verificar com `./gradlew test` que todos os contextos de teste sobem com `ddl-auto: validate`

## 3. Testes de cenário da spec `persistence`

- [ ] 3.1 Criar `shared/persistence/PersistenceScenarioTest` (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` + `MutableClock`) com um teste por Scenario, com `@DisplayName` citando o Scenario: "Registro novo recebe identificador", "Registros diferentes recebem identificadores diferentes", "Registro novo recebe as duas datas", "Alteração atualiza só a data de última alteração", "Tentativa de alterar a data de criação é ignorada", "Migrations íntegras são validadas", "Banco migrado permanece intacto sem pedido de limpeza", "Pedido de limpeza é recusado" e "Entidades compatíveis com o schema"; cada teste limpa os próprios dados; verificar com `./gradlew test --tests '*PersistenceScenarioTest'`
- [ ] 3.2 Criar `shared/persistence/PersistenceStartupScenarioTest` (container `postgres:16` próprio, um database novo por teste, aplicação iniciada com `SpringApplicationBuilder`, conforme D7) com os Scenarios "Banco vazio recebe todas as migrations", "Reinicialização não reaplica migrations", "Migration aplicada alterada impede a inicialização" (causa `FlywayValidateException`) e "Entidade sem tabela correspondente impede a inicialização" (causa `SchemaManagementException`, tabela continua ausente); verificar com `./gradlew test --tests '*PersistenceStartupScenarioTest'`
- [ ] 3.3 Conferir que cada um dos 13 Scenarios de `specs/persistence/spec.md` tem exatamente um teste correspondente entre 3.1 e 3.2 (pelo `@DisplayName`); verificar listando os `@DisplayName` com `grep -rn '@DisplayName' src/test/java/com/manuelaalecio/educare_backend/shared/persistence`

## 4. Verificação final

- [ ] 4.1 Rodar `./gradlew build` com o Docker ativo e verificar que termina verde, incluindo `jacocoTestCoverageVerification` (mínimo de 90% de linhas e de branches)
