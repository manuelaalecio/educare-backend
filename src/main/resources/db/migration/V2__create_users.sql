-- Usuários do sistema (funcionários da instituição). O login é um email, sempre gravado em minúsculas
-- pela aplicação, por isso o índice único simples já garante a unicidade sem diferenciar maiúsculas.
CREATE TABLE users (
    id             uuid         PRIMARY KEY,
    name           varchar(150) NOT NULL,
    login          varchar(254) NOT NULL,
    password_hash  varchar(100) NOT NULL,
    role           varchar(20)  NOT NULL DEFAULT 'USER',
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    CONSTRAINT users_login_key UNIQUE (login),
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'USER'))
);
