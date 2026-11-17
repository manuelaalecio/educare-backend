-- Responsáveis pelas crianças (dados pessoais: LGPD).
-- created_by/updated_by sem FK para users (excluir um usuário não apaga nem bloqueia a trilha)
-- e NOT NULL: todo responsável é gravado por uma requisição autenticada.
CREATE TABLE guardians (
    id              uuid          PRIMARY KEY,
    name            varchar(150)  NOT NULL,
    marital_status  varchar(20),
    birth_date      date,
    cpf             varchar(11),
    rg              varchar(20),
    phone           varchar(11)   NOT NULL,
    not_working     boolean       NOT NULL,
    workplace       varchar(150),
    work_address    varchar(200),
    work_phone      varchar(11),
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      uuid          NOT NULL,
    updated_by      uuid          NOT NULL,
    CONSTRAINT guardians_cpf_check CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT guardians_rg_check CHECK (rg ~ '^[0-9A-Z]+$'),
    CONSTRAINT guardians_phone_check CHECK (phone ~ '^[0-9]{10,11}$'),
    CONSTRAINT guardians_work_phone_check CHECK (work_phone ~ '^[0-9]{10,11}$'),
    CONSTRAINT guardians_marital_status_check
        CHECK (marital_status IN ('SINGLE', 'MARRIED', 'STABLE_UNION', 'DIVORCED', 'WIDOWED')),
    -- Não trabalha: sem dados de trabalho. Trabalha: os três preenchidos.
    CONSTRAINT guardians_work_check CHECK (
        (not_working AND workplace IS NULL AND work_address IS NULL AND work_phone IS NULL)
        OR (NOT not_working AND workplace IS NOT NULL AND work_address IS NOT NULL AND work_phone IS NOT NULL))
);

-- CPF único entre responsáveis; vários sem CPF.
CREATE UNIQUE INDEX guardians_cpf_key ON guardians (cpf) WHERE cpf IS NOT NULL;

-- Ordenação padrão da listagem.
CREATE INDEX guardians_name_idx ON guardians (name);

-- Vínculo criança–responsável, com o parentesco. Excluir a criança apaga os vínculos dela;
-- um responsável vinculado não pode ser excluído.
-- created_by/updated_by sem FK para users e NOT NULL: todo vínculo é gravado por uma requisição autenticada.
CREATE TABLE child_guardians (
    id            uuid         PRIMARY KEY,
    child_id      uuid         NOT NULL REFERENCES children (id) ON DELETE CASCADE,
    guardian_id   uuid         NOT NULL REFERENCES guardians (id) ON DELETE RESTRICT,
    relationship  varchar(20)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    created_by    uuid         NOT NULL,
    updated_by    uuid         NOT NULL,
    CONSTRAINT child_guardians_child_guardian_key UNIQUE (child_id, guardian_id),
    CONSTRAINT child_guardians_relationship_check CHECK (relationship IN ('MOTHER', 'FATHER', 'GRANDMOTHER',
        'GRANDFATHER', 'AUNT', 'UNCLE', 'SISTER', 'BROTHER', 'STEPMOTHER', 'STEPFATHER', 'LEGAL_GUARDIAN', 'OTHER'))
);

-- Crianças de cada responsável (listagem e consulta) e verificação da FK.
-- A UNIQUE (child_id, guardian_id) já indexa a busca pelos vínculos de uma criança.
CREATE INDEX child_guardians_guardian_id_idx ON child_guardians (guardian_id);
