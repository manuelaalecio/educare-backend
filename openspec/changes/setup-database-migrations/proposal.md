# Proposal

## Why

O projeto já declara o Flyway como dependência, mas ainda não há configuração, nenhuma migration e nenhuma base comum para as entidades. Hoje o Hibernate está no modo padrão de DDL e nada garante que o schema do banco seja o mesmo em dev, nos testes e em produção. Antes do primeiro módulo de negócio (`child`, `guardian`, `user`), o schema precisa estar sob controle de versão e todas as entidades precisam partir da mesma base (id UUID e datas de auditoria), já que a LGPD exige trilha de criação e alteração dos registros.

## What Changes

- Configurar o Flyway como único responsável pelo schema: migrations em `src/main/resources/db/migration`, aplicadas na inicialização, com validação de checksum ativa e `clean` desabilitado.
- Configurar o JPA com `spring.jpa.hibernate.ddl-auto=validate` (a aplicação não sobe se as entidades não baterem com o schema) e `open-in-view` desligado.
- Criar a migration `V1__baseline.sql`, que marca o início do histórico de versões sem tabelas de negócio.
- Organizar o `application.yaml` com os profiles `dev` e `prod`: o `dev` traz os padrões do banco local do Compose; o `prod` exige tudo por variável de ambiente. O `docker-compose.yaml` passa a ativar o profile `prod`.
- Criar a base de persistência em `shared/persistence`: superclasse mapeada com `id` (UUID gerado pela aplicação), `created_at` e `updated_at` preenchidos automaticamente pela auditoria do Spring Data JPA, com o horário vindo de um `Clock` injetável.
- Testes: cenário (Testcontainers) para aplicação e validação das migrations, e persistência para a entidade base.
- Atualizar o CLAUDE.md (seção Armadilhas) e o README com o novo jeito de subir a aplicação localmente.

## Non-goals

- Tabelas de negócio (crianças, responsáveis, usuários, rematrícula): cada uma vem na change do seu módulo, como nova migration.
- Autoria dos registros (`created_by`, `updated_by`): depende do módulo de usuários e da autenticação, e entra junto com eles.
- Controle de concorrência otimista (`@Version`), soft delete e histórico de alterações (tabelas de auditoria/Envers).
- Regras ArchUnit, handler global de erros e demais peças de `shared` que não sejam de persistência.
- Rodar migrations fora da aplicação (plugin Gradle do Flyway ou job separado) e estratégia de rollback/`undo`.

## Capabilities

### New Capabilities
- `persistence`: gestão do schema do banco (migrations versionadas pelo Flyway, validação do schema na inicialização, proteção contra `clean`) e a base comum das entidades (identificador UUID e datas de criação/alteração). É uma capability transversal, implementada em `shared/persistence` e na configuração da aplicação, e não um módulo de negócio; todos os módulos futuros dependem dela, por isso ela vem antes de qualquer um deles.

### Modified Capabilities
<!-- Nenhuma: ainda não há specs em openspec/specs/. -->

## Impact

- **Código**: novo pacote `shared/persistence` (entidade base e configuração de auditoria) e bean de `Clock` em `shared/config`.
- **Configuração**: `application.yaml` reorganizado; novos `application-dev.yaml` e `application-prod.yaml`; `docker-compose.yaml` passa a definir `SPRING_PROFILES_ACTIVE=prod`.
- **Banco**: primeira migration (`V1__baseline.sql`) e a tabela `flyway_schema_history` criada pelo Flyway.
- **Dependências**: nenhuma nova (Flyway, `flyway-database-postgresql`, Spring Data JPA e Testcontainers já estão no `build.gradle`).
- **API**: nenhum endpoint novo ou alterado.
- **Documentação**: CLAUDE.md (Armadilhas) e README (como iniciar o projeto).
