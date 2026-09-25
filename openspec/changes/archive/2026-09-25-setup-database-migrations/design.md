# Design

## Context

Estado atual (ver proposal.md, seção Why, para a motivação):

- `build.gradle` já tem `spring-boot-starter-flyway`, `flyway-database-postgresql`, `spring-boot-starter-data-jpa`, Testcontainers e os starters `-test` correspondentes.
- `src/main/resources/db/migration/` existe e está vazio; `application.yaml` só define `spring.application.name`.
- Não há datasource configurado: em dev ele vem de variáveis de ambiente; no Compose, o serviço `api` recebe `SPRING_DATASOURCE_*`; nos testes, o `TestcontainersConfiguration` fornece um `postgres:16` via `@ServiceConnection`.
- Não há nenhuma entidade nem código em `shared/`.

Restrições: uma única instância da API numa VM do Oracle Free Tier; nada de componentes extras de infraestrutura.

## Goals / Non-Goals

**Goals:**
- Um único caminho para mudar o schema (migration Flyway), aplicado automaticamente e validado em toda inicialização, igual em dev, testes e produção.
- Convenção de colunas e uma superclasse que os módulos de negócio só precisam estender.
- Datas de auditoria testáveis de forma determinística (relógio injetável).

**Non-Goals (além dos da proposal):**
- Nenhum endpoint novo. A saúde do banco continua exposta só pelo health indicator padrão do Actuator, sem mudança de configuração.
- Não sobrescrever `equals`/`hashCode` na entidade base nesta change; se um módulo precisar, isso vira decisão do design dele.

## Decisions

### D1. Flyway roda na inicialização da aplicação (autoconfiguração do Spring Boot)
As migrations são aplicadas pelo `FlywayAutoConfiguration` antes de o `EntityManagerFactory` subir.
- *Alternativas*: plugin Gradle do Flyway ou um container/job de migração separado no Compose. Descartadas: com uma única instância não há corrida entre réplicas, e um job extra consome memória da VM e complica o deploy.
- Configuração em `application.yaml` (vale para todos os profiles e para os testes):
  - `spring.flyway.locations: classpath:db/migration` (padrão, explícito para documentar).
  - `spring.flyway.validate-on-migrate: true` → atende "Migrations aplicadas são imutáveis" (checksum divergente derruba a inicialização).
  - `spring.flyway.clean-disabled: true` → atende "Limpeza bloqueada"; explícito mesmo sendo o padrão, para ninguém desligar sem perceber.
  - `spring.flyway.baseline-on-migrate: false` → um banco com tabelas mas sem histórico do Flyway faz a inicialização falhar, em vez de ser "adotado" silenciosamente.

### D2. Hibernate só valida o schema
- `spring.jpa.hibernate.ddl-auto: validate` → atende "Schema validado contra o mapeamento das entidades".
- `spring.jpa.open-in-view: false`: a sessão não fica aberta durante a renderização da resposta; o acesso a dados fica restrito aos services transacionais (coerente com as camadas do CLAUDE.md).
- `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`: timestamps gravados e lidos em UTC, independentemente do fuso da VM.

### D3. Migration `V1__baseline.sql` sem objetos
Contém só um comentário descrevendo a convenção de schema (abaixo). Serve como marco da versão 1 no histórico e prova que o pipeline funciona antes de existir qualquer tabela de negócio.
- *Alternativa*: deixar a primeira tabela do primeiro módulo ser a V1. Descartada: a V1 do módulo misturaria "ligar o Flyway" com regra de negócio, e os testes desta change ficariam sem uma versão conhecida para verificar.
- Não é preciso habilitar extensões: o UUID é gerado pela aplicação (D5) e o `gen_random_uuid()` já é nativo no PostgreSQL 16, se algum módulo precisar.

**Convenção de schema para as próximas migrations** (cada módulo cria as suas):

```sql
CREATE TABLE <tabela_no_plural> (
    id          uuid        PRIMARY KEY,
    -- colunas do módulo, em snake_case
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL
);
```

Nome de arquivo `V<n>__<descricao_em_snake_case>.sql`, com `<n>` sequencial inteiro; migration aplicada nunca é editada.

### D4. Profiles `dev` e `prod`
- `application.yaml`: configuração comum (D1, D2) e `spring.profiles.default: dev`, para que o `bootRun` funcione sem precisar ativar profile.
- `application-dev.yaml`: `spring.datasource.url: jdbc:postgresql://localhost:5432/app` e `username: app` (o banco do Compose publicado em `127.0.0.1`); a senha continua vindo de `SPRING_DATASOURCE_PASSWORD`.
- `application-prod.yaml`: `url`, `username` e `password` como `${SPRING_DATASOURCE_URL}`, `${SPRING_DATASOURCE_USERNAME}` e `${SPRING_DATASOURCE_PASSWORD}`, sem valor padrão, para a inicialização falhar com mensagem clara se faltar alguma variável.
- `docker-compose.yaml`: o serviço `api` recebe `SPRING_PROFILES_ACTIVE: prod`.
- Testes não ativam profile; o `@ServiceConnection` do Testcontainers tem precedência sobre as propriedades de datasource do `dev`.
- *Alternativa*: sem profile padrão. Descartada: obrigaria a passar `SPRING_PROFILES_ACTIVE=dev` em todo `bootRun`. O risco (produção subir como `dev`) é baixo: o `dev` aponta para `localhost` sem senha, então não conecta em nada por engano, e o Compose define `prod` explicitamente.

### D5. Entidade base `shared/persistence/BaseEntity`
`@MappedSuperclass` abstrata com `@EntityListeners(AuditingEntityListener.class)` e `@Getter` (Lombok), sem setters:
- `id`: `UUID`, `@Id @GeneratedValue @UuidGenerator`, com estilo ordenado por tempo (UUID v7, `Style.VERSION_7` do Hibernate 7; se a versão resolvida não tiver esse estilo, `Style.TIME`). Gerado pela aplicação no `persist`, sem ida ao banco; a ordenação por tempo mantém o índice da PK compacto. O `SimpleJpaRepository.save` trata `id == null` como registro novo, sem configuração extra.
  - *Alternativa*: `DEFAULT gen_random_uuid()` no banco. Descartada: exigiria `@Generated` e releitura após o insert, e UUID v4 fragmenta o índice.
- `createdAt`: `Instant`, `@CreatedDate`, `@Column(name = "created_at", nullable = false, updatable = false)`. O `updatable = false` garante o cenário "Tentativa de alterar a data de criação é ignorada" mesmo que alguém altere o campo por reflexão.
- `updatedAt`: `Instant`, `@LastModifiedDate`, `@Column(name = "updated_at", nullable = false)`.

### D6. Auditoria pelo Spring Data JPA com relógio injetável
- `shared/config/ClockConfiguration`: bean `Clock` = `Clock.systemUTC()`. Qualquer código que precise de "agora" injeta esse `Clock`.
- `shared/persistence/JpaAuditingConfiguration`: `@Configuration` com `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")` e o bean `DateTimeProvider` que retorna `Optional.of(Instant.now(clock))`.
  - Fica numa classe própria, não na classe main, para não quebrar os slices `@WebMvcTest` (que não sobem JPA).
- *Alternativas*: `@CreationTimestamp`/`@UpdateTimestamp` do Hibernate ou `@PrePersist`/`@PreUpdate` na entidade. Descartadas: usam o relógio da JVM (não dá para fixar o horário no teste) e não oferecem o gancho de `AuditorAware` que a change de usuários vai usar para `created_by`/`updated_by`.

### D7. Estratégia de testes
Não há endpoint, então os cenários são verificados pelo banco e pelo ciclo de vida da aplicação.

- **Suporte de teste** (`src/test/java/.../shared/testsupport/`):
  - `persistence/TestAuditedEntity` (estende `BaseEntity`, um campo `name`) e `persistence/TestAuditedEntityRepository` (`JpaRepository`), só no classpath de teste.
  - `src/test/resources/db/migration/R__test_support_schema.sql`: migration *repetível*, só no classpath de teste, que cria `test_audited_entity` seguindo a convenção de D3. Como o classpath de teste junta `main` e `test`, ela roda em todos os contextos de teste (necessário, porque a entidade de teste é escaneada em todos eles e o `validate` exigiria a tabela), e roda depois das versionadas, sem disputar número de versão com as migrations reais.
  - `MutableClock` (um `Clock` com `setInstant`) e uma `@TestConfiguration` que o registra como `@Primary`.
- **`shared/persistence/PersistenceScenarioTest`** (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` + `MutableClock`): cenários de identificador, datas de auditoria, "Migrations íntegras são validadas" (`flyway.validate()` no bean injetado), "Pedido de limpeza é recusado" (`flyway.clean()` lança `FlywayException` e o histórico continua igual), "Banco migrado permanece intacto" e "Entidades compatíveis com o schema" (consulta ao `information_schema`: só `flyway_schema_history` e `test_audited_entity`).
- **`shared/persistence/PersistenceStartupScenarioTest`**: cenários que dependem de subir a aplicação mais de uma vez ou contra um banco adulterado. Exceção justificada ao `@Import(TestcontainersConfiguration.class)`: usa um `PostgreSQLContainer` próprio (`@Testcontainers`/`@Container`, `postgres:16`) e, para isolar os testes entre si, cria um database novo por teste (`CREATE DATABASE`), subindo a aplicação com `SpringApplicationBuilder(EducareBackendApplication.class)` e `spring.datasource.*` apontando para ele (`web-application-type=none`).
  - Banco vazio → V1 aplicada; reinício → mesma contagem no histórico.
  - Checksum da V1 alterado via SQL no `flyway_schema_history` → nova inicialização falha com causa `FlywayValidateException`.
  - `DROP TABLE test_audited_entity` após a primeira inicialização → nova inicialização falha com causa `SchemaManagementException` citando a tabela, e a tabela continua ausente (a repetível não roda de novo porque o checksum não mudou).
- Todo teste de cenário tem `@DisplayName` com o nome do Scenario da spec.
- Não há teste `@DataJpaTest` separado: não existem queries customizadas nem constraints próprias, e os comportamentos da entidade base já são cobertos pelo teste de cenário contra PostgreSQL real.
- Cobertura: o código novo de produção é pequeno (`BaseEntity`, `JpaAuditingConfiguration`); `ClockConfiguration` fica em `shared/config`, já excluído do JaCoCo. Os getters do Lombok são marcados como `@Generated` pelo `lombok.config`.

### Endpoints e schema
- **Endpoints**: nenhum criado ou alterado.
- **Schema**: `V1__baseline.sql` (sem objetos). O Flyway cria `flyway_schema_history`. `test_audited_entity` existe só nos bancos de teste.

## Risks / Trade-offs

- [Migration com erro derruba a inicialização em produção] → É o comportamento desejado (falha rápida); os testes de cenário aplicam todas as migrations num PostgreSQL 16 real antes do deploy, e o `restart: unless-stopped` do Compose não mascara o erro, que aparece nos logs.
- [Migration longa atrasa a inicialização e pode estourar o healthcheck] → Com o volume de dados de uma ONG isso é improvável; se acontecer, o módulo que criar a migration pesada avalia rodá-la separadamente.
- [Tabela de teste `test_audited_entity` escaneada em todos os contextos de teste] → Custo mínimo; evita manter entidades de teste fora do pacote raiz. Nunca vai para produção porque a migration repetível e a entidade estão só em `src/test`.
- [`Style.VERSION_7` indisponível na versão do Hibernate trazida pelo Spring Boot 4.1.1] → Usar `Style.TIME` (também ordenado por tempo); a spec não depende da versão do UUID.
- [Profile padrão `dev` ativo por engano em produção] → Ver D4: o Compose define `prod` e o `dev` não tem credenciais.

## Migration Plan

1. Deploy normal da imagem nova; o Compose passa a subir a API com o profile `prod`.
2. Na primeira inicialização contra o banco da VM (vazio), o Flyway cria `flyway_schema_history` e aplica a V1.
3. Se o banco da VM não estiver vazio (tabelas criadas manualmente ou por `ddl-auto` antigo), a inicialização falha por causa de `baseline-on-migrate: false`; nesse caso, inspecionar e limpar o banco manualmente antes de subir de novo.

**Rollback**: voltar para a imagem anterior. A V1 não cria objetos; se for preciso começar do zero, basta `DROP TABLE flyway_schema_history`.
