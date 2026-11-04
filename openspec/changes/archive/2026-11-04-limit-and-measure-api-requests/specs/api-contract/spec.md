# Spec Delta

## MODIFIED Requirements

### Requirement: Respostas de erro descritas como ProblemDetail
O contrato SHALL ter um único schema `ProblemDetail`, com as propriedades `type`, `title`, `status`, `detail` e `instance`, e a propriedade opcional `errors`: uma lista de objetos com `field` e `message`, ambos em `required`. O `required` do `ProblemDetail` SHALL conter só `title` e `status`: `type`, `detail`, `instance` e `errors` são opcionais, porque a API omite `type` quando ele é `about:blank` (RFC 9457). Toda operação SHALL declarar, com o media type `application/problem+json` e esse schema, cada status de erro que as specs da operação preveem. Toda operação que exige token SHALL declarar `401` e `429` (o `429` cobre o limite de requisições por usuário e, na troca da própria senha, também o limite de tentativas), e as que exigem uma role SHALL declarar também `403`. As operações públicas SHALL NOT declarar `401` por falta de token nem `429`; a única exceção é o `401` de credenciais inválidas no login.

#### Scenario: Erros do login
- **WHEN** lê-se no contrato a operação `POST /api/v1/auth/login`
- **THEN** ela declara `400` e `401` com `application/problem+json` referenciando o schema `ProblemDetail`

#### Scenario: Erros de uma operação de ADMIN
- **WHEN** lê-se no contrato a operação `POST /api/v1/users`
- **THEN** ela declara `400`, `401`, `403`, `409` e `429` com `application/problem+json` referenciando o schema `ProblemDetail`

#### Scenario: Erros da troca da própria senha
- **WHEN** lê-se no contrato a operação `PUT /api/v1/auth/me/password`
- **THEN** ela declara `400`, `401` e `429` com `application/problem+json` referenciando o schema `ProblemDetail`, e não declara `403`

#### Scenario: Operação sem limite de tentativas não declara 429
- **WHEN** lê-se no contrato a operação pública `POST /api/v1/auth/login`, que não tem limite de tentativas nem de requisições
- **THEN** ela não declara `429`

#### Scenario: Toda operação autenticada declara 429
- **WHEN** lê-se no contrato cada operação que exige token, entre elas `PUT /api/v1/users/{id}/password`, `GET /api/v1/children` e `GET /api/v1/classrooms`
- **THEN** cada uma declara `429` com `application/problem+json` referenciando o schema `ProblemDetail`

#### Scenario: Operação de qualquer usuário autenticado não declara 403
- **WHEN** lê-se no contrato a operação `GET /api/v1/auth/me`
- **THEN** ela declara `401` e `429` com o schema `ProblemDetail` e não declara `403`

#### Scenario: Schema de ProblemDetail com erros de campo
- **WHEN** lê-se no contrato o schema `ProblemDetail`
- **THEN** ele tem as propriedades `type`, `title`, `status`, `detail`, `instance` e `errors`; o `required` contém exatamente `title` e `status`, e os itens de `errors` têm `field` e `message` em `required`

#### Scenario: Corpo de erro real atende ao schema
- **WHEN** o `POST /api/v1/auth/login` recebe credenciais inválidas
- **THEN** a resposta é `401` com `application/problem+json`, e o corpo tem todos os campos do `required` do schema `ProblemDetail`
