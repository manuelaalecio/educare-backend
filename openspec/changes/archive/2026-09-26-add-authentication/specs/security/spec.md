# Spec Delta

## Purpose

Garantir que só usuários autenticados, com a role exigida, acessem a API, que tokens inválidos, expirados ou de usuários excluídos sejam recusados, e que só o frontend configurado possa chamar a API pelo navegador.

## ADDED Requirements

### Requirement: Autenticação obrigatória fora das rotas públicas
O sistema SHALL exigir um access token válido no header `Authorization: Bearer <token>` em todas as rotas, exceto `POST /api/v1/auth/login` e o health do Actuator (`/actuator/health` e sub-rotas), que SHALL funcionar sem token. Sem token, com esquema diferente de `Bearer` ou com token recusado, o sistema SHALL responder `401 Unauthorized` com `Content-Type: application/problem+json` e o header `WWW-Authenticate` começando por `Bearer`, e SHALL NOT executar a operação pedida.

#### Scenario: Rota protegida sem token
- **WHEN** um cliente envia `GET /api/v1/users` sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json` e o header `WWW-Authenticate` começando por `Bearer`

#### Scenario: Esquema de autenticação diferente de Bearer
- **WHEN** um cliente envia `GET /api/v1/auth/me` com `Authorization: Basic YWRtaW5AZWR1Y2FyZS5vcmc6ZWR1Y2FyZTEyMw==`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json`

#### Scenario: Rota inexistente sem token
- **WHEN** um cliente envia `GET /api/v1/nao-existe` sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized`, sem revelar se a rota existe

#### Scenario: Health público
- **WHEN** um cliente envia `GET /actuator/health` sem o header `Authorization`
- **THEN** a resposta é `200 OK`

#### Scenario: Login público
- **WHEN** um cliente envia `POST /api/v1/auth/login` com credenciais corretas e sem o header `Authorization`
- **THEN** a resposta é `200 OK`

### Requirement: Token recusado quando inválido ou de usuário inexistente
O sistema SHALL recusar com `401 Unauthorized` e um `ProblemDetail` o token malformado, com assinatura que não corresponde à chave do sistema, expirado ou cujo usuário não existe mais. A resposta SHALL NOT indicar qual desses motivos causou a recusa.

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

### Requirement: Role vigente decide o acesso
O sistema SHALL decidir o acesso pela role que o usuário tem no momento da requisição, e não pela role que ele tinha quando o token foi emitido. Quando o usuário está autenticado mas a role dele não permite a operação, o sistema SHALL responder `403 Forbidden` com `Content-Type: application/problem+json`, sem executar a operação.

#### Scenario: USER em rota de ADMIN
- **WHEN** a usuária `ana.souza@educare.org`, com role `USER`, envia `GET /api/v1/users` com o token dela
- **THEN** a resposta é `403 Forbidden` com `Content-Type: application/problem+json`

#### Scenario: Rebaixamento vale no mesmo token
- **WHEN** existem os `ADMIN` `admin@educare.org` e `bruno.lima@educare.org`, Bruno faz login, e `admin@educare.org` altera a role de Bruno para `USER`
- **THEN** a requisição seguinte de Bruno a `GET /api/v1/users`, com o token que ele recebeu antes da alteração, responde `403 Forbidden`

#### Scenario: Promoção vale no mesmo token
- **WHEN** a usuária `ana.souza@educare.org`, com role `USER`, faz login, e um `ADMIN` altera a role dela para `ADMIN`
- **THEN** a requisição seguinte de Ana a `GET /api/v1/users`, com o token que ela recebeu antes da alteração, responde `200 OK`

### Requirement: Chave de assinatura obrigatória em produção
O sistema SHALL assinar e validar os tokens com uma chave configurada no ambiente, de no mínimo 32 bytes. No profile `prod` a chave SHALL ser obrigatória, sem valor padrão, e a aplicação SHALL NOT iniciar sem ela, com ela vazia ou com menos de 32 bytes. No profile `dev`, sem configuração, SHALL ser usada uma chave padrão de desenvolvimento.

#### Scenario: Produção sem chave
- **WHEN** a aplicação inicia com o profile `prod`, com banco, senha do administrador inicial e origens de CORS configurados, e sem a chave de assinatura
- **THEN** a inicialização falha

#### Scenario: Chave curta demais
- **WHEN** a aplicação inicia com o profile `prod`, com as demais configurações válidas e a chave de assinatura com 31 bytes
- **THEN** a inicialização falha

#### Scenario: Chave válida
- **WHEN** a aplicação inicia com o profile `prod`, com as demais configurações válidas e a chave de assinatura com 32 bytes
- **THEN** a aplicação fica disponível e `GET /actuator/health` responde `200 OK`

### Requirement: CORS restrito às origens do frontend
O sistema SHALL permitir chamadas de navegador (CORS) só das origens configuradas no ambiente, para os métodos `GET`, `POST`, `PUT` e `DELETE` e os headers `Authorization` e `Content-Type`, e SHALL expor o header `Location` às respostas. Requisições preflight (`OPTIONS`) SHALL funcionar sem token. Uma origem fora da lista SHALL receber `403 Forbidden` no preflight, sem o header `Access-Control-Allow-Origin`. No profile `prod` a lista de origens SHALL ser obrigatória, e a aplicação SHALL NOT iniciar sem ela, com ela vazia ou com uma origem que não seja uma URL `http` ou `https` sem caminho. No profile `dev`, sem configuração, SHALL ser permitida só a origem `http://localhost:5173`.

#### Scenario: Preflight de origem permitida
- **WHEN** as origens configuradas são `https://educare.example.org`, e um cliente envia `OPTIONS /api/v1/users` sem token, com `Origin: https://educare.example.org`, `Access-Control-Request-Method: GET` e `Access-Control-Request-Headers: authorization`
- **THEN** a resposta é `200 OK` com `Access-Control-Allow-Origin: https://educare.example.org`, e `Access-Control-Allow-Headers` inclui `authorization`

#### Scenario: Preflight de origem não permitida
- **WHEN** as origens configuradas são `https://educare.example.org`, e um cliente envia o mesmo preflight com `Origin: https://outro.example.com`
- **THEN** a resposta é `403 Forbidden` e não tem o header `Access-Control-Allow-Origin`

#### Scenario: Header Location exposto
- **WHEN** as origens configuradas são `https://educare.example.org`, e um `ADMIN` cria um usuário com `POST /api/v1/users` e `Origin: https://educare.example.org`
- **THEN** a resposta é `201 Created`, com `Access-Control-Allow-Origin: https://educare.example.org` e `Access-Control-Expose-Headers` incluindo `Location`

#### Scenario: Produção sem origens
- **WHEN** a aplicação inicia com o profile `prod`, com as demais configurações válidas e sem as origens de CORS
- **THEN** a inicialização falha

#### Scenario: Origem com curinga recusada
- **WHEN** a aplicação inicia com o profile `prod`, com as demais configurações válidas e as origens de CORS configuradas como `*`
- **THEN** a inicialização falha
