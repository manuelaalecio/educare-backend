-- Administrador inicial, criado uma única vez: excluir ou alterar esse usuário não faz ele voltar,
-- porque o Flyway não reexecuta migrations aplicadas.
--
-- A senha vem do placeholder admin_password (spring.flyway.placeholders.admin_password, que lê
-- EDUCARE_ADMIN_PASSWORD) e é delimitada por dollar-quote para que aspas na senha não quebrem o SQL.
-- O espaço depois da tag de abertura, removido pelo substr, é necessário: o Flyway não substitui o
-- placeholder quando ele vem colado no cifrão que fecha a tag.
--
-- A migration falha, sem criar o admin, se a senha vier vazia (variável definida sem valor, como faz o
-- docker compose quando ela falta no .env) ou sem resolver (sem a variável, o Spring mantém a referência
-- a ela como texto literal). O padrão é montado por concatenação para o Flyway não lê-lo como placeholder.
--
-- O hash BCrypt ($2a$, custo 10) é gerado pelo pgcrypto e aceito pelo BCryptPasswordEncoder da aplicação.
-- O pgcrypto é uma extensão trusted no PostgreSQL 16: o dono do database pode criá-la sem ser superusuário.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $seed$
DECLARE
    admin_password text := substr($admin_pwd$ ${admin_password}$admin_pwd$, 2);
BEGIN
    IF admin_password = '' OR admin_password LIKE ('$' || '{%}') THEN
        RAISE EXCEPTION 'Senha do administrador inicial não configurada (EDUCARE_ADMIN_PASSWORD)';
    END IF;

    INSERT INTO users (id, name, login, password_hash, role, created_at, updated_at)
    VALUES (gen_random_uuid(), 'Administrador', 'admin@educare.org',
            crypt(admin_password, gen_salt('bf', 10)),
            'ADMIN', now(), now());
END
$seed$;
