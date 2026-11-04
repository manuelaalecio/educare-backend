# observability Specification

## Purpose

Registrar cada requisição à API num log sem dados pessoais, com rota, status e duração, para medir quantas chamadas cada rota recebe e quanto tempo leva, sem expor métricas nem adicionar serviços.

## Requirements

### Requirement: Log por requisição sem dados pessoais
O sistema SHALL registrar no log da aplicação, no nível `INFO`, uma linha por requisição HTTP concluída, no formato `http method=<método> route=<rota> status=<status> durationMs=<duração>`, em que `<rota>` é o modelo da rota que atendeu a requisição, com as variáveis entre chaves como declaradas na API (ex.: `/api/v1/children/{id}`), `<status>` é o status HTTP da resposta e `<duração>` é o tempo total da requisição em milissegundos, como inteiro não negativo, incluindo autenticação e limite de requisições. Quando a requisição não chega a uma rota da API (recusada antes pela segurança ou rota inexistente), `<rota>` SHALL ser `unmatched`. A linha SHALL NOT conter o caminho real, a query string, o corpo da requisição ou da resposta, headers, o token, o id ou o login do usuário, nem o IP do cliente. As requisições ao Actuator (`/actuator/**`) SHALL NOT ser registradas. O registro SHALL poder ser desligado pela configuração de nível de log, sem mudar o código.

#### Scenario: Consulta registrada com a rota como modelo
- **WHEN** um usuário autenticado envia `GET /api/v1/children/3f1c2a9e-8b7d-4c6e-9a1b-2d3e4f5a6b7c` de uma criança existente
- **THEN** o log tem uma linha `http method=GET route=/api/v1/children/{id} status=200 durationMs=<n>`, com `<n>` inteiro não negativo, e nenhuma linha do log da requisição contém `3f1c2a9e-8b7d-4c6e-9a1b-2d3e4f5a6b7c`

#### Scenario: Query string fora do log
- **WHEN** um usuário autenticado envia `GET /api/v1/children?name=Maria&page=0`
- **THEN** o log tem uma linha `http method=GET route=/api/v1/children status=200 durationMs=<n>`, e nenhuma linha do log da requisição contém `Maria`

#### Scenario: Corpo e usuário fora do log
- **WHEN** a usuária `ana.souza@educare.org` cria uma criança com `POST /api/v1/children`, com o nome `Maria Clara Silva` e o CPF `529.982.247-25`
- **THEN** o log tem uma linha `http method=POST route=/api/v1/children status=201 durationMs=<n>`, e nenhuma linha do log da requisição contém `Maria Clara Silva`, `52998224725`, `529.982.247-25`, `ana.souza@educare.org`, o id da usuária nem o token

#### Scenario: Erro registrado com o status
- **WHEN** um usuário autenticado envia `GET /api/v1/children/00000000-0000-0000-0000-000000000000` (criança inexistente)
- **THEN** o log tem uma linha `http method=GET route=/api/v1/children/{id} status=404 durationMs=<n>`

#### Scenario: Requisição recusada pela segurança
- **WHEN** um cliente envia `GET /api/v1/users` sem o header `Authorization`
- **THEN** o log tem uma linha `http method=GET route=unmatched status=401 durationMs=<n>`

#### Scenario: Requisição recusada pelo limite
- **WHEN** um usuário que já fez 120 requisições no último minuto envia `GET /api/v1/auth/me`
- **THEN** o log tem uma linha `http method=GET route=/api/v1/auth/me status=429 durationMs=<n>`

#### Scenario: Rota inexistente
- **WHEN** um usuário autenticado envia `GET /api/v1/nao-existe/123`
- **THEN** o log tem uma linha `http method=GET route=unmatched status=404 durationMs=<n>`, sem o caminho `/api/v1/nao-existe/123`

#### Scenario: Health fora do log
- **WHEN** um cliente envia `GET /actuator/health` e `GET /actuator/health/readiness`
- **THEN** nenhuma linha `http method=` é registrada para essas requisições

#### Scenario: Log desligado pela configuração
- **WHEN** a aplicação roda com o nível de log do registro de requisições em `WARN` e um usuário autenticado envia `GET /api/v1/auth/me`
- **THEN** nenhuma linha `http method=` é registrada, e a resposta é `200 OK`
