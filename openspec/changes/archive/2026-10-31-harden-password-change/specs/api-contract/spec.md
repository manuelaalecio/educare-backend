# Spec Delta

## MODIFIED Requirements

### Requirement: Respostas de sucesso com campos obrigatórios declarados
No contrato, toda operação SHALL declarar o status de sucesso que a API devolve, com o schema do corpo quando houver corpo. O corpo de sucesso SHALL usar o media type `application/json`, e nenhuma resposta SHALL usar `*/*`. Nos schemas de resposta, os campos que a API sempre devolve preenchidos SHALL constar em `required`; os campos que podem vir ausentes ou `null` SHALL NOT constar. Os schemas de resposta SHALL NOT conter as propriedades `password`, `createdBy` nem `updatedBy`.

#### Scenario: Resposta do login
- **WHEN** lê-se no contrato a operação `POST /api/v1/auth/login`
- **THEN** ela declara a resposta `200` com `application/json` e um schema cujo `required` contém `accessToken`, `tokenType` e `expiresIn`, e não declara nenhum requisito de segurança

#### Scenario: Resposta dos próprios dados
- **WHEN** lê-se no contrato a operação `GET /api/v1/auth/me`
- **THEN** ela declara a resposta `200` com `application/json` e um schema cujo `required` contém `id`, `name`, `login`, `role`, `createdAt` e `updatedAt`, e que não tem a propriedade `password`

#### Scenario: Resposta da troca da própria senha
- **WHEN** lê-se no contrato a operação `PUT /api/v1/auth/me/password`
- **THEN** ela declara a resposta `200` com `application/json` e o mesmo schema da resposta do login, cujo `required` contém `accessToken`, `tokenType` e `expiresIn`, e não declara a resposta `204`

#### Scenario: Operação sem corpo de resposta
- **WHEN** lê-se no contrato a operação `PUT /api/v1/users/{id}/password`
- **THEN** ela declara a resposta `204` sem `content`

#### Scenario: Criação com Location
- **WHEN** lê-se no contrato a operação `POST /api/v1/users`
- **THEN** ela declara a resposta `201` com o header `Location` e o schema do usuário criado

#### Scenario: Nenhuma resposta com media type genérico
- **WHEN** lê-se o contrato inteiro
- **THEN** nenhuma resposta de nenhuma operação tem `*/*` em `content`

### Requirement: Respostas de erro descritas como ProblemDetail
O contrato SHALL ter um único schema `ProblemDetail`, com as propriedades `type`, `title`, `status`, `detail` e `instance`, e a propriedade opcional `errors`: uma lista de objetos com `field` e `message`, ambos em `required`. O `required` do `ProblemDetail` SHALL conter só `title` e `status`: `type`, `detail`, `instance` e `errors` são opcionais, porque a API omite `type` quando ele é `about:blank` (RFC 9457). Toda operação SHALL declarar, com o media type `application/problem+json` e esse schema, cada status de erro que as specs da operação preveem, inclusive o `429` das operações com limite de tentativas. Toda operação que exige token SHALL declarar `401`, e as que exigem uma role SHALL declarar também `403`. As operações públicas SHALL NOT declarar `401` por falta de token; a única exceção é o `401` de credenciais inválidas no login.

#### Scenario: Erros do login
- **WHEN** lê-se no contrato a operação `POST /api/v1/auth/login`
- **THEN** ela declara `400` e `401` com `application/problem+json` referenciando o schema `ProblemDetail`

#### Scenario: Erros de uma operação de ADMIN
- **WHEN** lê-se no contrato a operação `POST /api/v1/users`
- **THEN** ela declara `400`, `401`, `403` e `409` com `application/problem+json` referenciando o schema `ProblemDetail`

#### Scenario: Erros da troca da própria senha
- **WHEN** lê-se no contrato a operação `PUT /api/v1/auth/me/password`
- **THEN** ela declara `400`, `401` e `429` com `application/problem+json` referenciando o schema `ProblemDetail`, e não declara `403`

#### Scenario: Operação sem limite de tentativas não declara 429
- **WHEN** lê-se no contrato a operação `PUT /api/v1/users/{id}/password`
- **THEN** ela não declara `429`

#### Scenario: Operação de qualquer usuário autenticado não declara 403
- **WHEN** lê-se no contrato a operação `GET /api/v1/auth/me`
- **THEN** ela declara `401` com o schema `ProblemDetail` e não declara `403`

#### Scenario: Schema de ProblemDetail com erros de campo
- **WHEN** lê-se no contrato o schema `ProblemDetail`
- **THEN** ele tem as propriedades `type`, `title`, `status`, `detail`, `instance` e `errors`; o `required` contém exatamente `title` e `status`, e os itens de `errors` têm `field` e `message` em `required`

#### Scenario: Corpo de erro real atende ao schema
- **WHEN** o `POST /api/v1/auth/login` recebe credenciais inválidas
- **THEN** a resposta é `401` com `application/problem+json`, e o corpo tem todos os campos do `required` do schema `ProblemDetail`
