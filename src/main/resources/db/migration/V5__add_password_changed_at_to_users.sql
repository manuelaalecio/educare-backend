-- Instante da última troca de senha (própria ou redefinição pelo ADMIN), em segundos inteiros.
-- Tokens com iat anterior a ele são recusados. NULL: o usuário não trocou a senha desde esta
-- migration, e nenhum token dele é recusado por esse motivo.
ALTER TABLE users ADD COLUMN password_changed_at timestamptz;
