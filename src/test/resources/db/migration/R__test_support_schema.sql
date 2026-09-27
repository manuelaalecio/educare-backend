-- Somente para testes: tabela da TestAuditedEntity, que exercita a BaseEntity.
-- Nunca vai para produção (existe apenas no classpath de teste).
-- Autoria nullable para testar gravações com e sem usuário autenticado.
CREATE TABLE IF NOT EXISTS test_audited_entity (
    id          uuid         PRIMARY KEY,
    name        varchar(255) NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    created_by  uuid,
    updated_by  uuid
);
