# Spec Delta

## ADDED Requirements

### Requirement: Gestão de usuários restrita a ADMIN
Todos os endpoints de `/api/v1/users` SHALL exigir um usuário autenticado com role `ADMIN`. Sem token válido, SHALL responder `401 Unauthorized`; com um usuário de role `USER`, SHALL responder `403 Forbidden`; em ambos os casos, com um `ProblemDetail` e sem executar a operação. Nos demais requirements desta capability, "um cliente" é um usuário autenticado com role `ADMIN`.

#### Scenario: ADMIN lista usuários
- **WHEN** `admin@educare.org`, com role `ADMIN`, envia `GET /api/v1/users` com o token dele
- **THEN** a resposta é `200 OK`

#### Scenario: USER não cria usuário
- **WHEN** a usuária `ana.souza@educare.org`, com role `USER`, envia `POST /api/v1/users` com o token dela e `{"name": "Carla", "login": "carla@educare.org", "password": "segredo123", "role": "ADMIN"}`
- **THEN** a resposta é `403 Forbidden` com `Content-Type: application/problem+json`, e não existe usuário com login `carla@educare.org`

#### Scenario: USER não se promove
- **WHEN** a usuária `ana.souza@educare.org`, com role `USER`, envia `PUT /api/v1/users/{id}` com o próprio id, o token dela e `{"name": "Ana Souza", "login": "ana.souza@educare.org", "role": "ADMIN"}`
- **THEN** a resposta é `403 Forbidden`, e ela continua com role `USER`

#### Scenario: Exclusão sem token
- **WHEN** existe o usuário `ana.souza@educare.org` e um cliente envia `DELETE /api/v1/users/{id}` com o id dela, sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized`, e o usuário continua existindo

### Requirement: Sistema nunca fica sem ADMIN
O sistema SHALL recusar com `409 Conflict` e um `ProblemDetail`, sem alterar nada, a exclusão em que um `ADMIN` exclui a si mesmo, e toda alteração de role ou exclusão que deixaria o sistema sem nenhum usuário com role `ADMIN`. Um `ADMIN` SHALL poder rebaixar a si mesmo ou excluir outro `ADMIN` quando continuar existindo ao menos outro `ADMIN`.

#### Scenario: ADMIN tenta se excluir
- **WHEN** existem os `ADMIN` `admin@educare.org` e `bruno.lima@educare.org`, e `admin@educare.org` envia `DELETE /api/v1/users/{id}` com o próprio id
- **THEN** a resposta é `409 Conflict` com `Content-Type: application/problem+json`, e `admin@educare.org` continua existindo

#### Scenario: Último ADMIN tenta se rebaixar
- **WHEN** `admin@educare.org` é o único `ADMIN` e envia `PUT /api/v1/users/{id}` com o próprio id e `{"name": "Administrador", "login": "admin@educare.org", "role": "USER"}`
- **THEN** a resposta é `409 Conflict`, e `admin@educare.org` continua com role `ADMIN`

#### Scenario: ADMIN se rebaixa havendo outro ADMIN
- **WHEN** existem os `ADMIN` `admin@educare.org` e `bruno.lima@educare.org`, e `admin@educare.org` altera a própria role para `USER`
- **THEN** a resposta é `200 OK` com `"role": "USER"`

#### Scenario: ADMIN exclui outro ADMIN
- **WHEN** existem os `ADMIN` `admin@educare.org` e `bruno.lima@educare.org`, e `admin@educare.org` exclui `bruno.lima@educare.org`
- **THEN** a resposta é `204 No Content`, e `admin@educare.org` continua sendo `ADMIN`

## MODIFIED Requirements

### Requirement: Listar usuários com paginação
O sistema SHALL listar os usuários em `GET /api/v1/users` com `200 OK`, paginados pelos parâmetros `page` (começa em 0, padrão 0), `size` (padrão 20, máximo 100; valores maiores são tratados como 100) e `sort` (`name`, `login` ou `createdAt`, com direção `asc` ou `desc`; padrão `name,asc`). O corpo SHALL ter `content` (usuários da página, cada um com `id`, `name`, `login`, `role`, `createdAt` e `updatedAt`) e `page` (com `size`, `number`, `totalElements` e `totalPages`). Uma ordenação por propriedade fora das permitidas SHALL ser recusada com `400 Bad Request` e um `ProblemDetail`. A listagem SHALL incluir o próprio `ADMIN` que faz a consulta, e nos cenários abaixo esse `ADMIN` é `Zélia`, de login `zelia@educare.org`.

#### Scenario: Primeira página ordenada por nome
- **WHEN** existem só os usuários de nome `Carla`, `Ana`, `Bruno` e `Zélia`, e um cliente envia `GET /api/v1/users?size=2`
- **THEN** a resposta é `200 OK`, `content` tem `Ana` e `Bruno`, nessa ordem, e `page` tem `"size": 2`, `"number": 0`, `"totalElements": 4` e `"totalPages": 2`

#### Scenario: Ordenação decrescente por login
- **WHEN** existem só os usuários de login `ana@educare.org`, `bruno@educare.org`, `carla@educare.org` e `zelia@educare.org`, e um cliente envia `GET /api/v1/users?sort=login,desc`
- **THEN** a resposta é `200 OK` e `content` tem `zelia@educare.org`, `carla@educare.org`, `bruno@educare.org` e `ana@educare.org`, nessa ordem

#### Scenario: Nenhum usuário cadastrado
- **WHEN** não existe nenhum usuário além de `zelia@educare.org`, e ela envia `GET /api/v1/users`
- **THEN** a resposta é `200 OK`, `content` tem só `zelia@educare.org` e `page.totalElements` é `1`

#### Scenario: Tamanho de página acima do máximo
- **WHEN** existem só 4 usuários e um cliente envia `GET /api/v1/users?size=500`
- **THEN** a resposta é `200 OK`, `page.size` é `100` e `content` tem os 4 usuários

#### Scenario: Ordenação por propriedade não permitida
- **WHEN** um cliente envia `GET /api/v1/users?sort=password`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`
