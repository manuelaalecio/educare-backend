-- Somente para testes: tabela da TestAuditedEntity, que exercita a BaseEntity.
-- Nunca vai para produção (existe apenas no classpath de teste).
CREATE TABLE IF NOT EXISTS test_audited_entity (
    id          uuid         PRIMARY KEY,
    name        varchar(255) NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL
);
