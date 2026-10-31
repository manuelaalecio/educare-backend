# Spec Delta

## MODIFIED Requirements

### Requirement: Autoria da turma
Cada turma SHALL guardar o id do usuário autenticado que a criou e o do que a alterou por último. A renomeação SHALL atualizar só o autor da última alteração. A autoria SHALL NOT aparecer nas respostas da API.

#### Scenario: Autoria de criação e de renomeação
- **WHEN** o `COORDENADOR` `admin@educare.org` cria a turma `Turma 1`, e depois o `COORDENADOR` `bruno@educare.org` a renomeia para `Maternal A`
- **THEN** a turma guarda como autor da criação o id de `admin@educare.org` e como autor da última alteração o id de `bruno@educare.org`

#### Scenario: Autoria fora da resposta
- **WHEN** um cliente envia `POST /api/v1/classrooms` com `{"name": "Turma 1"}`
- **THEN** a resposta `201 Created` não tem as propriedades `createdBy` nem `updatedBy`

### Requirement: Gestão de turmas restrita a COORDENADOR
Todos os endpoints de `/api/v1/classrooms` SHALL exigir um usuário autenticado com role `COORDENADOR`. Sem token válido, SHALL responder `401 Unauthorized`. Com um usuário de role `EDUCADOR`, SHALL responder `403 Forbidden`. Nos dois casos, a resposta é um `ProblemDetail` e a operação não é executada. Nos demais requirements desta capability, "um cliente" é um usuário autenticado com role `COORDENADOR`.

#### Scenario: ADMIN lista turmas
- **WHEN** `admin@educare.org`, com role `COORDENADOR`, envia `GET /api/v1/classrooms` com o token dele
- **THEN** a resposta é `200 OK`

#### Scenario: USER não lista turmas
- **WHEN** a usuária `ana.souza@educare.org`, com role `EDUCADOR`, envia `GET /api/v1/classrooms` com o token dela
- **THEN** a resposta é `403 Forbidden` com `Content-Type: application/problem+json`

#### Scenario: USER não cria turma
- **WHEN** a usuária `ana.souza@educare.org`, com role `EDUCADOR`, envia `POST /api/v1/classrooms` com o token dela e `{"name": "Turma 9"}`
- **THEN** a resposta é `403 Forbidden`, e não existe turma `Turma 9`

#### Scenario: USER não renomeia nem exclui turma
- **WHEN** existe a turma `Turma 1`, e a usuária `ana.souza@educare.org`, com role `EDUCADOR`, envia com o token dela `GET /api/v1/classrooms/{id}`, `PUT /api/v1/classrooms/{id}` com `{"name": "Maternal A"}` e `DELETE /api/v1/classrooms/{id}`
- **THEN** as três respostas são `403 Forbidden`, e a turma continua existindo com o nome `Turma 1`

#### Scenario: Exclusão sem token
- **WHEN** existe a turma `Turma 1` e um cliente envia `DELETE /api/v1/classrooms/{id}` com o id dela, sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized`, e a turma continua existindo

## RENAMED Requirements

- FROM: `### Requirement: Gestão de turmas restrita a ADMIN`
- TO: `### Requirement: Gestão de turmas restrita a COORDENADOR`

