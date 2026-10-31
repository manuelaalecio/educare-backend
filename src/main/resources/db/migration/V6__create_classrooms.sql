-- unaccent é trusted no PostgreSQL 16: o dono do database a cria sem superusuário (como o pgcrypto da V3).
-- IF NOT EXISTS porque a change add-child-crud também a cria.
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent é STABLE (depende do search_path para achar o dicionário) e não pode ser usada num índice.
-- Com a função e o dicionário qualificados pelo schema, o resultado é sempre o mesmo e IMMUTABLE é seguro.
-- Trocar o dicionário unaccent no banco deixaria os índices que usam esta função inconsistentes (exigiria REINDEX).
CREATE FUNCTION immutable_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN public.unaccent('public.unaccent'::regdictionary, $1);

-- Turmas. created_by/updated_by sem FK para users (excluir um usuário não apaga nem bloqueia a trilha)
-- e NOT NULL: toda turma é gravada por uma requisição autenticada.
CREATE TABLE classrooms (
    id          uuid         PRIMARY KEY,
    name        varchar(60)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    created_by  uuid         NOT NULL,
    updated_by  uuid         NOT NULL
);

-- Nome único sem diferenciar maiúsculas e acentos ("Turma 1", "turma 1" e "Túrma 1" colidem).
CREATE UNIQUE INDEX classrooms_name_key ON classrooms (lower(immutable_unaccent(name)));
