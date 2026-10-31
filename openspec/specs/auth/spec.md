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
O sistema SHALL trocar a senha do próprio usuário do token em `PUT /api/v1/auth/me/password`, com o corpo `{"currentPassword": "...", "newPassword": "..."}`, para qualquer usuário autenticado, respondendo `200 OK` com um token novo no mesmo formato do login: `{"accessToken": "<token>", "tokenType": "Bearer", "expiresIn": 28800}`. O token novo SHALL ser aceito pela API; todo token do usuário emitido antes da troca, inclusive o usado na própria requisição, SHALL passar a ser recusado com `401 Unauthorized` (ver a capability `security`). A troca SHALL exigir que `currentPassword` corresponda à senha atual; se não corresponder, SHALL responder `400 Bad Request` com um `ProblemDetail` cujo `errors` tem uma entrada para `currentPassword`, sem alterar nada. `newPassword` SHALL seguir as regras de senha da criação de usuário (8 a 72 caracteres e no máximo 72 bytes em UTF-8), SHALL ser diferente da senha atual e SHALL ser diferente do login do usuário, comparado sem diferenciar maiúsculas e minúsculas; a violação de qualquer dessas regras SHALL ser recusada com `400 Bad Request`, uma entrada em `errors` para `newPassword` e nenhuma alteração. Quando `currentPassword` está incorreta, a resposta SHALL trazer só o erro de `currentPassword`. A troca SHALL atualizar o `updatedAt` do usuário. Sem token válido, SHALL responder `401 Unauthorized`.

#### Scenario: Senha trocada
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia `PUT /api/v1/auth/me/password` com o token dela e `{"currentPassword": "segredo123", "newPassword": "novaSenha456"}`
- **THEN** a resposta é `200 OK` com `accessToken` não vazio, `"tokenType": "Bearer"` e `"expiresIn": 28800`; `GET /api/v1/auth/me` com o `accessToken` recebido responde `200 OK`; o login com `novaSenha456` responde `200 OK` e o login com `segredo123` responde `401 Unauthorized`

#### Scenario: Outras sessões encerradas
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, faz login às `2026-03-01T10:00:00Z` (token A) e às `2026-03-01T10:05:00Z` (token B), e às `2026-03-01T10:10:00Z` troca a senha com o token A, para `novaSenha456`
- **THEN** a resposta traz um token C; às `2026-03-01T10:11:00Z`, `GET /api/v1/auth/me` responde `401 Unauthorized` com o token A e com o token B, e `200 OK` com o token C

#### Scenario: Senha atual incorreta
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia `PUT /api/v1/auth/me/password` com `{"currentPassword": "errada123", "newPassword": "novaSenha456"}`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json` e uma entrada em `errors` para `currentPassword`, o login com `segredo123` continua respondendo `200 OK`, e o token usado continua aceito em `GET /api/v1/auth/me`

#### Scenario: Nova senha inválida
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia `PUT /api/v1/auth/me/password` com `{"currentPassword": "segredo123", "newPassword": "curta"}`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `newPassword`, e o login com `segredo123` continua respondendo `200 OK`

#### Scenario: Nova senha igual à atual
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia `PUT /api/v1/auth/me/password` com `{"currentPassword": "segredo123", "newPassword": "segredo123"}`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `newPassword` e nenhuma para `currentPassword`, e o token usado continua aceito em `GET /api/v1/auth/me`

#### Scenario: Nova senha igual ao login em outra combinação de maiúsculas
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia `PUT /api/v1/auth/me/password` com `{"currentPassword": "segredo123", "newPassword": "Ana.Souza@EDUCARE.org"}`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `newPassword`, o corpo não contém `ana.souza@educare.org`, e o login com `segredo123` continua respondendo `200 OK`

#### Scenario: Senha atual incorreta e nova senha igual ao login
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia `PUT /api/v1/auth/me/password` com `{"currentPassword": "errada123", "newPassword": "ana.souza@educare.org"}`
- **THEN** a resposta é `400 Bad Request` com uma única entrada em `errors`, para `currentPassword`

#### Scenario: Troca sem token
- **WHEN** um cliente envia `PUT /api/v1/auth/me/password` com um corpo válido e sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized`, e nenhuma senha é alterada

### Requirement: Limite de tentativas da senha atual
O sistema SHALL contar, por usuário, as respostas de senha atual incorreta em `PUT /api/v1/auth/me/password`. Quando o usuário tiver 5 ou mais erros nos últimos 15 minutos (janela deslizante, medida pelo relógio da aplicação), o sistema SHALL recusar a troca de senha desse usuário com `429 Too Many Requests` e um `ProblemDetail` com o `detail` `Muitas tentativas. Tente novamente em alguns minutos.`, sem `errors`, mesmo que `currentPassword` esteja correta, sem alterar a senha e sem contar a tentativa como novo erro. A troca SHALL voltar a ser aceita assim que menos de 5 erros estiverem dentro da janela. Uma troca bem-sucedida SHALL zerar a contagem do usuário. A contagem de um usuário SHALL NOT afetar outro usuário. Recusas por regras da nova senha (tamanho, igual à atual, igual ao login) SHALL NOT contar como erro. A contagem SHALL ser mantida só em memória: reiniciar a aplicação a zera.

#### Scenario: Bloqueio depois de 5 erros
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia `PUT /api/v1/auth/me/password` com `"currentPassword": "errada123"` às `10:00`, `10:01`, `10:02`, `10:03` e `10:04` de `2026-03-01` (UTC), e às `10:05` envia `{"currentPassword": "segredo123", "newPassword": "novaSenha456"}`
- **THEN** as cinco primeiras respostas são `400 Bad Request` com uma entrada em `errors` para `currentPassword`; a sexta é `429 Too Many Requests` com `Content-Type: application/problem+json`, `"detail": "Muitas tentativas. Tente novamente em alguns minutos."` e sem `errors`; e o login com `segredo123` continua respondendo `200 OK`

#### Scenario: Janela deslizante libera a troca
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, erra a senha atual às `10:00`, `10:01`, `10:02`, `10:03` e `10:04` de `2026-03-01` (UTC), tenta de novo com a senha correta às `10:14:59` e mais uma vez com a senha correta às `10:15:01`
- **THEN** a tentativa das `10:14:59` responde `429 Too Many Requests`, e a das `10:15:01` responde `200 OK` com um token novo

#### Scenario: Troca bem-sucedida zera a contagem
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, erra a senha atual 4 vezes entre `10:00` e `10:03`, troca a senha para `novaSenha456` às `10:04` com sucesso e, com o token novo, erra a senha atual mais 4 vezes entre `10:05` e `10:08` e às `10:09` troca de `novaSenha456` para `outraSenha789`
- **THEN** a troca das `10:04` e a das `10:09` respondem `200 OK`, e nenhuma resposta é `429`

#### Scenario: Bloqueio não afeta outro usuário
- **WHEN** a usuária `ana.souza@educare.org` está bloqueada depois de 5 erros de senha atual em 15 minutos, e o usuário `bruno.lima@educare.org`, com senha `senhaBruno1`, envia `PUT /api/v1/auth/me/password` com o token dele e `{"currentPassword": "senhaBruno1", "newPassword": "novaSenha456"}`
- **THEN** a resposta para Bruno é `200 OK` com um token novo

#### Scenario: Recusa pela nova senha não conta como erro
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, envia 5 vezes em 5 minutos `{"currentPassword": "segredo123", "newPassword": "segredo123"}` e em seguida `{"currentPassword": "segredo123", "newPassword": "novaSenha456"}`
- **THEN** as cinco primeiras respostas são `400 Bad Request` com erro em `newPassword`, e a sexta é `200 OK`
