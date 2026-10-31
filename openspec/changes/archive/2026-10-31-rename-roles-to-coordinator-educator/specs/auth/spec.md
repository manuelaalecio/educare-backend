# Spec Delta

## MODIFIED Requirements

### Requirement: Consultar os próprios dados
O sistema SHALL devolver em `GET /api/v1/auth/me`, para qualquer usuário autenticado (`COORDENADOR` ou `EDUCADOR`), `200 OK` com os campos `id`, `name`, `login`, `role`, `createdAt` e `updatedAt` do próprio usuário do token, sem senha nem hash. Sem token válido, SHALL responder `401 Unauthorized`.

#### Scenario: USER consulta os próprios dados
- **WHEN** a usuária `ana.souza@educare.org`, de nome `Ana Souza` e role `EDUCADOR`, envia `GET /api/v1/auth/me` com o token dela
- **THEN** a resposta é `200 OK` com o `id` dela, `"name": "Ana Souza"`, `"login": "ana.souza@educare.org"`, `"role": "EDUCADOR"`, `createdAt` e `updatedAt`, e o corpo não tem a propriedade `password`

#### Scenario: Consulta sem token
- **WHEN** um cliente envia `GET /api/v1/auth/me` sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json`
