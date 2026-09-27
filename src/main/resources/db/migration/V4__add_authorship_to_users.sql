-- Autoria (quem criou / quem alterou por último). Sem FK para users de propósito: excluir um usuário
-- não pode apagar nem bloquear a trilha dos registros que ele criou ou alterou.
-- Nullable em users: o administrador inicial (V3) é gravado sem usuário autenticado e fica sem autor.
ALTER TABLE users
    ADD COLUMN created_by uuid,
    ADD COLUMN updated_by uuid;
