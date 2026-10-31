# Spec Delta

## MODIFIED Requirements

### Requirement: Token recusado quando inválido ou de usuário inexistente
O sistema SHALL recusar com `401 Unauthorized` e um `ProblemDetail` o token malformado, com assinatura que não corresponde à chave do sistema, expirado, cujo usuário não existe mais ou emitido antes da última troca de senha do usuário (troca da própria senha ou redefinição por um `COORDENADOR`). A comparação com a troca de senha SHALL ser feita em segundos inteiros, a mesma precisão da data de emissão do token: o token emitido no mesmo segundo da troca continua aceito. O token de um usuário sem troca de senha registrada SHALL ser aceito normalmente. A recusa SHALL valer já na primeira requisição depois da troca. A resposta SHALL NOT indicar qual desses motivos causou a recusa.

#### Scenario: Token malformado
- **WHEN** um cliente envia `GET /api/v1/auth/me` com `Authorization: Bearer abc.def`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json`

#### Scenario: Token assinado com outra chave
- **WHEN** um cliente envia `GET /api/v1/auth/me` com um token de mesmo formato, com o `id` de um usuário existente e dentro da validade, mas assinado com uma chave diferente da configurada
- **THEN** a resposta é `401 Unauthorized`

#### Scenario: Token de usuário excluído
- **WHEN** a usuária `ana.souza@educare.org` faz login, é excluída por um `COORDENADOR` e depois envia `GET /api/v1/auth/me` com o token que recebeu no login
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

### Requirement: Role vigente decide o acesso
O sistema SHALL decidir o acesso pela role que o usuário tem no momento da requisição, e não pela role que ele tinha quando o token foi emitido. Quando o usuário está autenticado mas a role dele não permite a operação, o sistema SHALL responder `403 Forbidden` com `Content-Type: application/problem+json`, sem executar a operação.

#### Scenario: USER em rota de ADMIN
- **WHEN** a usuária `ana.souza@educare.org`, com role `EDUCADOR`, envia `GET /api/v1/users` com o token dela
- **THEN** a resposta é `403 Forbidden` com `Content-Type: application/problem+json`

#### Scenario: Rebaixamento vale no mesmo token
- **WHEN** existem os `COORDENADOR` `admin@educare.org` e `bruno.lima@educare.org`, Bruno faz login, e `admin@educare.org` altera a role de Bruno para `EDUCADOR`
- **THEN** a requisição seguinte de Bruno a `GET /api/v1/users`, com o token que ele recebeu antes da alteração, responde `403 Forbidden`

#### Scenario: Promoção vale no mesmo token
- **WHEN** a usuária `ana.souza@educare.org`, com role `EDUCADOR`, faz login, e um `COORDENADOR` altera a role dela para `COORDENADOR`
- **THEN** a requisição seguinte de Ana a `GET /api/v1/users`, com o token que ela recebeu antes da alteração, responde `200 OK`

### Requirement: CORS restrito às origens do frontend
O sistema SHALL permitir chamadas de navegador (CORS) só das origens configuradas no ambiente, para os métodos `GET`, `POST`, `PUT` e `DELETE` e os headers `Authorization` e `Content-Type`, e SHALL expor o header `Location` às respostas. Requisições preflight (`OPTIONS`) SHALL funcionar sem token. Uma origem fora da lista SHALL receber `403 Forbidden` no preflight, sem o header `Access-Control-Allow-Origin`. No profile `prod` a lista de origens SHALL ser obrigatória, e a aplicação SHALL NOT iniciar sem ela, com ela vazia ou com uma origem que não seja uma URL `http` ou `https` sem caminho. No profile `dev`, sem configuração, SHALL ser permitida só a origem `http://localhost:5173`.

#### Scenario: Preflight de origem permitida
- **WHEN** as origens configuradas são `https://educare.example.org`, e um cliente envia `OPTIONS /api/v1/users` sem token, com `Origin: https://educare.example.org`, `Access-Control-Request-Method: GET` e `Access-Control-Request-Headers: authorization`
- **THEN** a resposta é `200 OK` com `Access-Control-Allow-Origin: https://educare.example.org`, e `Access-Control-Allow-Headers` inclui `authorization`

#### Scenario: Preflight de origem não permitida
- **WHEN** as origens configuradas são `https://educare.example.org`, e um cliente envia o mesmo preflight com `Origin: https://outro.example.com`
- **THEN** a resposta é `403 Forbidden` e não tem o header `Access-Control-Allow-Origin`

#### Scenario: Header Location exposto
- **WHEN** as origens configuradas são `https://educare.example.org`, e um `COORDENADOR` cria um usuário com `POST /api/v1/users` e `Origin: https://educare.example.org`
- **THEN** a resposta é `201 Created`, com `Access-Control-Allow-Origin: https://educare.example.org` e `Access-Control-Expose-Headers` incluindo `Location`

#### Scenario: Produção sem origens
- **WHEN** a aplicação inicia com o profile `prod`, com as demais configurações válidas e sem as origens de CORS
- **THEN** a inicialização falha

#### Scenario: Origem com curinga recusada
- **WHEN** a aplicação inicia com o profile `prod`, com as demais configurações válidas e as origens de CORS configuradas como `*`
- **THEN** a inicialização falha
