# Spec Delta

## MODIFIED Requirements

### Requirement: Alterar senha
O sistema SHALL substituir a senha do usuário pela recebida em `PUT /api/v1/users/{id}/password` (corpo `{"password": "..."}`), respondendo `204 No Content`. A nova senha segue as regras da criação (8 a 72 caracteres e no máximo 72 bytes em UTF-8; recusa com `400 Bad Request` e `errors`). A alteração SHALL atualizar `updatedAt`. A alteração SHALL desconectar o usuário afetado: todo token dele emitido antes da alteração passa a ser recusado com `401 Unauthorized` (ver a capability `security`), inclusive quando o `ADMIN` altera a própria senha por essa rota; os tokens de outros usuários não são afetados. Se não existir usuário com esse id, SHALL responder `404 Not Found`.

#### Scenario: Senha alterada
- **WHEN** o usuário `ana.souza@educare.org`, com senha `segredo123`, recebe `PUT /api/v1/users/{id}/password` com `{"password": "novaSenha456"}`
- **THEN** a resposta é `204 No Content` sem corpo, a senha guardada passa a corresponder a `novaSenha456` e deixa de corresponder a `segredo123`

#### Scenario: Redefinição desconecta o usuário afetado
- **WHEN** a usuária `ana.souza@educare.org` (role `USER`) faz login às `2026-03-01T10:00:00Z`, um `ADMIN` envia às `2026-03-01T10:30:00Z` `PUT /api/v1/users/{id da Ana}/password` com `{"password": "novaSenha456"}`, e às `2026-03-01T10:31:00Z` Ana envia `GET /api/v1/auth/me` com o token do login
- **THEN** a resposta para Ana é `401 Unauthorized`; o token do `ADMIN` continua aceito em `GET /api/v1/users`; e um novo login de Ana com `novaSenha456` devolve um token aceito em `GET /api/v1/auth/me`

#### Scenario: ADMIN que redefine a própria senha é desconectado
- **WHEN** um `ADMIN` faz login às `2026-03-01T10:00:00Z` e às `2026-03-01T10:30:00Z` envia `PUT /api/v1/users/{o próprio id}/password` com `{"password": "novaSenha456"}`
- **THEN** a resposta é `204 No Content`, e às `2026-03-01T10:31:00Z` `GET /api/v1/users` com o mesmo token responde `401 Unauthorized`

#### Scenario: Nova senha inválida
- **WHEN** o usuário `ana.souza@educare.org`, com senha `segredo123`, recebe `PUT /api/v1/users/{id}/password` com `{"password": "curta"}`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `password`, a senha guardada continua correspondendo a `segredo123`, e os tokens de Ana continuam aceitos

#### Scenario: Senha de usuário inexistente
- **WHEN** um cliente envia `PUT /api/v1/users/{id}/password` com uma senha válida e um id que não existe
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`
