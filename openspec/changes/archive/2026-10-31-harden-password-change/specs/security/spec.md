# Spec Delta

## MODIFIED Requirements

### Requirement: Token recusado quando inválido ou de usuário inexistente
O sistema SHALL recusar com `401 Unauthorized` e um `ProblemDetail` o token malformado, com assinatura que não corresponde à chave do sistema, expirado, cujo usuário não existe mais ou emitido antes da última troca de senha do usuário (troca da própria senha ou redefinição por um `ADMIN`). A comparação com a troca de senha SHALL ser feita em segundos inteiros, a mesma precisão da data de emissão do token: o token emitido no mesmo segundo da troca continua aceito. O token de um usuário sem troca de senha registrada SHALL ser aceito normalmente. A recusa SHALL valer já na primeira requisição depois da troca. A resposta SHALL NOT indicar qual desses motivos causou a recusa.

#### Scenario: Token malformado
- **WHEN** um cliente envia `GET /api/v1/auth/me` com `Authorization: Bearer abc.def`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json`

#### Scenario: Token assinado com outra chave
- **WHEN** um cliente envia `GET /api/v1/auth/me` com um token de mesmo formato, com o `id` de um usuário existente e dentro da validade, mas assinado com uma chave diferente da configurada
- **THEN** a resposta é `401 Unauthorized`

#### Scenario: Token de usuário excluído
- **WHEN** a usuária `ana.souza@educare.org` faz login, é excluída por um `ADMIN` e depois envia `GET /api/v1/auth/me` com o token que recebeu no login
- **THEN** a resposta é `401 Unauthorized`

#### Scenario: Token de usuário existente
- **WHEN** a usuária `ana.souza@educare.org` faz login e envia `GET /api/v1/auth/me` com o token recebido
- **THEN** a resposta é `200 OK`

#### Scenario: Token emitido antes da troca de senha
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, faz login às `2026-03-01T10:00:00Z` (token A) e às `2026-03-01T10:20:00Z` (token B), troca a senha com o token B às `2026-03-01T10:30:00Z` e envia `GET /api/v1/auth/me` às `2026-03-01T10:31:00Z` com o token A
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json` e com o mesmo `title` e o mesmo `detail` da resposta ao token malformado

#### Scenario: Token emitido no mesmo segundo da troca
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, faz login às `2026-03-01T10:00:00.100Z` (token A) e às `2026-03-01T10:00:00.500Z` (token B), troca a senha com o token B às `2026-03-01T10:00:00.900Z` e envia `GET /api/v1/auth/me` com o token A às `2026-03-01T10:00:01Z`
- **THEN** a resposta é `200 OK`

#### Scenario: Usuário sem troca de senha registrada
- **WHEN** a usuária `ana.souza@educare.org`, que nunca trocou a senha nem teve a senha redefinida, faz login e envia `GET /api/v1/auth/me` com o token recebido
- **THEN** a resposta é `200 OK`
