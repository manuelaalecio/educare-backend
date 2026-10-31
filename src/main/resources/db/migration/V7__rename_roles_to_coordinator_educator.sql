-- As roles passam a ser as funções da escola: ADMIN vira COORDENADOR e USER vira EDUCADOR, inclusive o
-- administrador inicial criado pela V3.
--
-- O UPDATE é uma conversão de dados, não uma alteração feita por um usuário: não mexe em updated_at nem em
-- updated_by (a auditoria do Spring Data não roda em SQL).
--
-- Esta migration NÃO é compatível com a versão anterior da aplicação: o pod antigo, que ainda atende durante
-- o rolling update, não reconhece os valores novos e responde 500 até ser trocado. A exceção à regra de
-- migrations compatíveis foi aceita para esta mudança (design de rename-roles-to-coordinator-educator, D5).
ALTER TABLE users DROP CONSTRAINT users_role_check;

UPDATE users
SET role = CASE role WHEN 'ADMIN' THEN 'COORDENADOR' WHEN 'USER' THEN 'EDUCADOR' END;

ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('COORDENADOR', 'EDUCADOR'));

ALTER TABLE users ALTER COLUMN role SET DEFAULT 'EDUCADOR';
