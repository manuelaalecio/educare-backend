# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
