# auth Specification

## Purpose

Permitir que os funcionários entrem no sistema com email e senha e recebam um access token para usar a API, e que cada usuário autenticado consulte os próprios dados e troque a própria senha.

## Requirements

### Requirement: Login com email e senha
O sistema SHALL autenticar em `POST /api/v1/auth/login` um usuário a partir de `login` (email) e `password`, sem exigir token. Quando o login corresponde a um usuário cadastrado, sem diferenciar maiúsculas e minúsculas, e a senha corresponde à dele, SHALL responder `200 OK` com `{"accessToken": "<token>", "tokenType": "Bearer", "expiresIn": 28800}` (`expiresIn` em segundos). O sistema SHALL recusar com `401 Unauthorized` e um `ProblemDetail` quando o login não existir ou a senha não corresponder, com o mesmo `detail` nos dois casos e sem o email no corpo. O sistema SHALL recusar com `400 Bad Request` e um `ProblemDetail` com `errors` (entradas com `field` e `message`) quando `login` ou `password` estiver ausente ou em branco, quando `login` tiver mais de 254 caracteres ou quando `password` tiver mais de 72 caracteres ou mais de 72 bytes em UTF-8; e com `400 Bad Request` e um `ProblemDetail` quando o corpo não for um JSON válido.

#### Scenario: Login com credenciais corretas
- **WHEN** existe o usuário `ana.souza@educare.org` com senha `segredo123`, e um cliente envia `POST /api/v1/auth/login` com `{"login": "ana.souza@educare.org", "password": "segredo123"}`
- **THEN** a resposta é `200 OK` com `accessToken` não vazio, `"tokenType": "Bearer"` e `"expiresIn": 28800`, e `GET /api/v1/auth/me` com `Authorization: Bearer <accessToken>` responde `200 OK` com `"login": "ana.souza@educare.org"`

#### Scenario: Login sem diferenciar maiúsculas
- **WHEN** existe o usuário `ana.souza@educare.org` com senha `segredo123`, e um cliente faz login com `{"login": "Ana.Souza@EDUCARE.org", "password": "segredo123"}`
- **THEN** a resposta é `200 OK` com um `accessToken`

#### Scenario: Senha incorreta
- **WHEN** existe o usuário `ana.souza@educare.org` com senha `segredo123`, e um cliente faz login com `{"login": "ana.souza@educare.org", "password": "errada123"}`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json`, sem `accessToken`, e o corpo não contém `ana.souza@educare.org`

#### Scenario: Email não cadastrado responde igual à senha incorreta
- **WHEN** não existe usuário com login `ninguem@educare.org`, e um cliente faz login com `{"login": "ninguem@educare.org", "password": "segredo123"}`
- **THEN** a resposta é `401 Unauthorized` com o mesmo `title` e o mesmo `detail` do cenário "Senha incorreta", e o corpo não contém `ninguem@educare.org`

#### Scenario: Campos ausentes
- **WHEN** um cliente envia `POST /api/v1/auth/login` com `{}`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`, e `errors` contém uma entrada para `login` e uma para `password`

#### Scenario: Senha acima de 72 bytes
- **WHEN** um cliente faz login com `"login": "ana.souza@educare.org"` e uma `password` formada por 40 caracteres `ç` (80 bytes em UTF-8)
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para `password`

#### Scenario: JSON inválido no login
- **WHEN** um cliente envia `POST /api/v1/auth/login` com o corpo `{"login": "ana` e `Content-Type: application/json`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`

### Requirement: Access token com validade de 8 horas e sem dados pessoais
O access token emitido no login SHALL ser aceito pela API por 8 horas a partir da emissão, medidas pelo relógio da aplicação, e SHALL ser recusado depois disso. O token SHALL identificar o usuário só pelo `id` dele e SHALL NOT conter o email, o nome, a senha nem o hash da senha, em nenhuma parte legível.

#### Scenario: Token aceito dentro da validade
- **WHEN** a usuária `ana.souza@educare.org` faz login com o relógio da aplicação em `2026-03-01T10:00:00Z` e envia `GET /api/v1/auth/me` com o token recebido quando o relógio está em `2026-03-01T17:59:00Z`
- **THEN** a resposta é `200 OK`

#### Scenario: Token expirado
- **WHEN** a usuária `ana.souza@educare.org` faz login com o relógio da aplicação em `2026-03-01T10:00:00Z` e envia `GET /api/v1/auth/me` com o token recebido quando o relógio está em `2026-03-01T18:01:00Z`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json`

#### Scenario: Token não expõe dados pessoais
- **WHEN** a usuária `Ana Souza`, de login `ana.souza@educare.org`, faz login com sucesso
- **THEN** o cabeçalho e o payload do token, decodificados de Base64URL, contêm o `id` dela e não contêm `ana.souza@educare.org`, `Ana Souza` nem a senha

### Requirement: Consultar os próprios dados
O sistema SHALL devolver em `GET /api/v1/auth/me`, para qualquer usuário autenticado (`ADMIN` ou `USER`), `200 OK` com os campos `id`, `name`, `login`, `role`, `createdAt` e `updatedAt` do próprio usuário do token, sem senha nem hash. Sem token válido, SHALL responder `401 Unauthorized`.

#### Scenario: USER consulta os próprios dados
- **WHEN** a usuária `ana.souza@educare.org`, de nome `Ana Souza` e role `USER`, envia `GET /api/v1/auth/me` com o token dela
- **THEN** a resposta é `200 OK` com o `id` dela, `"name": "Ana Souza"`, `"login": "ana.souza@educare.org"`, `"role": "USER"`, `createdAt` e `updatedAt`, e o corpo não tem a propriedade `password`

#### Scenario: Consulta sem token
- **WHEN** um cliente envia `GET /api/v1/auth/me` sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json`

### Requirement: Trocar a própria senha
O sistema SHALL trocar a senha do próprio usuário do token em `PUT /api/v1/auth/me/password`, com o corpo `{"currentPassword": "...", "newPassword": "..."}`, respondendo `204 No Content`, para qualquer usuário autenticado. A troca SHALL exigir que `currentPassword` corresponda à senha atual; se não corresponder, SHALL responder `400 Bad Request` com um `ProblemDetail` cujo `errors` tem uma entrada para `currentPassword`, sem alterar nada. `newPassword` SHALL seguir as regras de senha da criação de usuário (8 a 72 caracteres e no máximo 72 bytes em UTF-8), com recusa em `400 Bad Request` e uma entrada em `errors` para `newPassword`. A troca SHALL atualizar o `updatedAt` do usuário. Sem token válido, SHALL responder `401 Unauthorized`.

#### Scenario: Senha trocada
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia `PUT /api/v1/auth/me/password` com o token dela e `{"currentPassword": "segredo123", "newPassword": "novaSenha456"}`
- **THEN** a resposta é `204 No Content` sem corpo; o login com `novaSenha456` responde `200 OK` e o login com `segredo123` responde `401 Unauthorized`

#### Scenario: Senha atual incorreta
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia `PUT /api/v1/auth/me/password` com `{"currentPassword": "errada123", "newPassword": "novaSenha456"}`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json` e uma entrada em `errors` para `currentPassword`, e o login com `segredo123` continua respondendo `200 OK`

#### Scenario: Nova senha inválida
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia `PUT /api/v1/auth/me/password` com `{"currentPassword": "segredo123", "newPassword": "curta"}`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `newPassword`, e o login com `segredo123` continua respondendo `200 OK`

#### Scenario: Troca sem token
- **WHEN** um cliente envia `PUT /api/v1/auth/me/password` com um corpo válido e sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized`, e nenhuma senha é alterada
