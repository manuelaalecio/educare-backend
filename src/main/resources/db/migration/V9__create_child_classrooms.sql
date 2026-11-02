-- Vínculo da criança com a turma, com histórico: exatamente um vínculo ativo (end_date IS NULL) por criança.
-- Excluir a criança apaga os vínculos dela; uma turma com vínculos (ativos ou encerrados) não pode ser excluída.
-- created_by/updated_by sem FK para users (excluir um usuário não apaga nem bloqueia a trilha)
-- e NOT NULL: todo vínculo é gravado por uma requisição autenticada.
CREATE TABLE child_classrooms (
    id            uuid         PRIMARY KEY,
    child_id      uuid         NOT NULL REFERENCES children (id) ON DELETE CASCADE,
    classroom_id  uuid         NOT NULL REFERENCES classrooms (id) ON DELETE RESTRICT,
    start_date    date         NOT NULL,
    end_date      date,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    created_by    uuid         NOT NULL,
    updated_by    uuid         NOT NULL,
    CONSTRAINT child_classrooms_dates_check CHECK (end_date IS NULL OR end_date >= start_date)
);

-- Um único vínculo ativo por criança.
CREATE UNIQUE INDEX child_classrooms_active_key ON child_classrooms (child_id) WHERE end_date IS NULL;

-- Histórico da criança.
CREATE INDEX child_classrooms_child_id_idx ON child_classrooms (child_id, start_date DESC);

-- "A turma já teve crianças" e a verificação da FK no DELETE da turma.
CREATE INDEX child_classrooms_classroom_id_idx ON child_classrooms (classroom_id);

-- Contagens das turmas e filtro da listagem pela turma atual.
CREATE INDEX child_classrooms_active_classroom_idx ON child_classrooms (classroom_id) WHERE end_date IS NULL;
