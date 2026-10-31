# classroom Specification

## Purpose

Manter o cadastro das turmas em que a instituição organiza as crianças: criar, listar, consultar, renomear e excluir turmas. Cada turma tem um nome único e mostra quantos alunos tem, e a matrícula e os gráficos do Início dependem dela.

## Requirements

### Requirement: Criar turma
O sistema SHALL criar uma turma a partir do `name` recebido em `POST /api/v1/classrooms`. A resposta SHALL ser `201 Created`, com o header `Location` apontando para `/api/v1/classrooms/{id}` e um corpo com `id`, `name`, `studentCount`, `maleCount`, `femaleCount`, `createdAt` e `updatedAt`. O `name` SHALL ser gravado sem espaços no início e no fim. O sistema SHALL recusar com `400 Bad Request`, sem gravar nada, quando o `name` estiver ausente, vazio, só com espaços ou tiver mais de 60 caracteres depois de tirar os espaços das pontas. O `ProblemDetail` da recusa SHALL ter a propriedade `errors` com uma entrada (`field` e `message`) para o campo `name`.

#### Scenario: Turma criada com nome válido
- **WHEN** um cliente envia `POST /api/v1/classrooms` com `{"name": "Turma 1"}` e o relógio da aplicação está em `2026-11-03T10:00:00Z`
- **THEN** a resposta é `201 Created`, o header `Location` é `/api/v1/classrooms/{id}` com o `id` gerado, e o corpo tem esse `id`, `"name": "Turma 1"`, `"studentCount": 0`, `"maleCount": 0`, `"femaleCount": 0`, `"createdAt": "2026-11-03T10:00:00Z"` e `"updatedAt": "2026-11-03T10:00:00Z"`

#### Scenario: Nome com espaços nas pontas é gravado sem eles
- **WHEN** um cliente envia `POST /api/v1/classrooms` com `{"name": "  Turma 3 "}`
- **THEN** a resposta é `201 Created` com `"name": "Turma 3"`, e `GET /api/v1/classrooms/{id}` devolve `"name": "Turma 3"`

#### Scenario: Nome com 60 caracteres sem contar os espaços das pontas
- **WHEN** um cliente envia `POST /api/v1/classrooms` com um `name` de 60 letras `a` precedido e seguido de um espaço
- **THEN** a resposta é `201 Created`, e o `name` gravado tem 60 caracteres

#### Scenario: Nome ausente
- **WHEN** um cliente envia `POST /api/v1/classrooms` com `{}`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json` e uma entrada em `errors` para o campo `name`; nenhuma turma é gravada

#### Scenario: Nome só com espaços
- **WHEN** um cliente envia `POST /api/v1/classrooms` com `{"name": "   "}`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para o campo `name`; nenhuma turma é gravada

#### Scenario: Nome longo demais
- **WHEN** um cliente envia `POST /api/v1/classrooms` com um `name` de 61 letras `a`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para o campo `name`; nenhuma turma é gravada

### Requirement: Nome de turma único sem diferenciar maiúsculas e acentos
O sistema SHALL tratar como o mesmo nome os valores que só diferem em maiúsculas e minúsculas ou em acentos, comparados depois de tirar os espaços das pontas. Por exemplo, `Turma 1`, `turma 1` e `TÚRMA 1` são o mesmo nome. O sistema SHALL recusar com `409 Conflict` e um `ProblemDetail`, sem gravar nada, a criação ou a renomeação que deixaria duas turmas com o mesmo nome. Renomear uma turma para uma variação do próprio nome (por exemplo, só mudando maiúsculas) SHALL ser aceito. O nome SHALL ser gravado como foi enviado, sem os espaços das pontas: a comparação não muda maiúsculas nem acentos do valor gravado.

#### Scenario: Criação com nome já usado em outra caixa
- **WHEN** já existe a turma `Turma 1` e um cliente envia `POST /api/v1/classrooms` com `{"name": "turma 1"}`
- **THEN** a resposta é `409 Conflict` com `Content-Type: application/problem+json`, e continua existindo só uma turma, de nome `Turma 1`

#### Scenario: Criação com nome já usado com acento
- **WHEN** já existe a turma `Turma 1` e um cliente envia `POST /api/v1/classrooms` com `{"name": "Túrma 1"}`
- **THEN** a resposta é `409 Conflict`, e nenhuma turma é gravada

#### Scenario: Renomeação para o nome de outra turma
- **WHEN** existem as turmas `Turma 1` e `Turma 2`, e um cliente envia `PUT /api/v1/classrooms/{id da Turma 2}` com `{"name": "TURMA 1"}`
- **THEN** a resposta é `409 Conflict`, e a segunda turma continua com o nome `Turma 2`

#### Scenario: Renomeação para uma variação do próprio nome
- **WHEN** existe a turma `Turma 1` e um cliente envia `PUT /api/v1/classrooms/{id}` com `{"name": "TURMA 1"}`
- **THEN** a resposta é `200 OK` com `"name": "TURMA 1"`

#### Scenario: Nome livre depois da exclusão
- **WHEN** a turma `Turma 1` é excluída e depois um cliente envia `POST /api/v1/classrooms` com `{"name": "Turma 1"}`
- **THEN** a resposta é `201 Created`

### Requirement: Consultar turma por id
O sistema SHALL devolver em `GET /api/v1/classrooms/{id}` a turma com esse id, com `200 OK` e os campos `id`, `name`, `studentCount`, `maleCount`, `femaleCount`, `createdAt` e `updatedAt`. Se não existir turma com esse id, SHALL responder `404 Not Found` com um `ProblemDetail`. Se o id não for um UUID válido, SHALL responder `400 Bad Request` com um `ProblemDetail`.

#### Scenario: Turma existente
- **WHEN** existe a turma `Maternal A`, criada em `2026-11-03T10:00:00Z`, e um cliente envia `GET /api/v1/classrooms/{id}` com o id dela
- **THEN** a resposta é `200 OK` com o `id`, `"name": "Maternal A"`, `"studentCount": 0`, `"maleCount": 0`, `"femaleCount": 0`, `"createdAt": "2026-11-03T10:00:00Z"` e `"updatedAt": "2026-11-03T10:00:00Z"`

#### Scenario: Turma inexistente
- **WHEN** um cliente envia `GET /api/v1/classrooms/0190f4a2-0000-7000-8000-000000000000` e não existe turma com esse id
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`

#### Scenario: Id malformado
- **WHEN** um cliente envia `GET /api/v1/classrooms/abc`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`

### Requirement: Listar turmas em ordem alfabética
O sistema SHALL listar todas as turmas em `GET /api/v1/classrooms`, com `200 OK` e um array JSON (sem paginação), em que cada item tem os mesmos campos da consulta por id. A ordem SHALL ser alfabética pelo nome, sem diferenciar maiúsculas e acentos. Sem turmas cadastradas, a resposta SHALL ser `200 OK` com um array vazio.

#### Scenario: Turmas em ordem alfabética
- **WHEN** existem só as turmas `Turma 2`, `berçário` e `Turma 1`, criadas nessa ordem, e um cliente envia `GET /api/v1/classrooms`
- **THEN** a resposta é `200 OK` com um array de 3 itens, na ordem `berçário`, `Turma 1` e `Turma 2`, cada um com `"studentCount": 0`, `"maleCount": 0`, `"femaleCount": 0` e o seu `createdAt`

#### Scenario: Acento não muda a posição
- **WHEN** existem só as turmas `Educação Infantil`, `Évora` e `Ensino`, e um cliente envia `GET /api/v1/classrooms`
- **THEN** a resposta é `200 OK` na ordem `Educação Infantil`, `Ensino` e `Évora`

#### Scenario: Nenhuma turma cadastrada
- **WHEN** não existe nenhuma turma e um cliente envia `GET /api/v1/classrooms`
- **THEN** a resposta é `200 OK` com o corpo `[]`

### Requirement: Renomear turma
O sistema SHALL substituir o `name` da turma pelo valor recebido em `PUT /api/v1/classrooms/{id}` (corpo `{"name": "..."}`), respondendo `200 OK` com a turma atualizada. O `name` segue as mesmas regras da criação: recusa com `400 Bad Request` e `errors` para `name`, sem alterar nada. A renomeação SHALL atualizar `updatedAt`, manter `createdAt` e não alterar os alunos da turma nem as contagens. Se não existir turma com esse id, SHALL responder `404 Not Found`.

#### Scenario: Turma renomeada
- **WHEN** a turma `Turma 1`, criada em `2026-11-03T10:00:00Z`, recebe `PUT /api/v1/classrooms/{id}` com `{"name": "Maternal A"}`, com o relógio da aplicação em `2026-11-04T09:00:00Z`
- **THEN** a resposta é `200 OK` com `"name": "Maternal A"`, `"createdAt": "2026-11-03T10:00:00Z"` e `"updatedAt": "2026-11-04T09:00:00Z"`, e `GET /api/v1/classrooms/{id}` devolve `"name": "Maternal A"`

#### Scenario: Renomeação com nome inválido
- **WHEN** a turma `Turma 1` recebe `PUT /api/v1/classrooms/{id}` com `{"name": ""}`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para o campo `name`, e a turma continua com o nome `Turma 1`

#### Scenario: Renomeação de turma inexistente
- **WHEN** um cliente envia `PUT /api/v1/classrooms/0190f4a2-0000-7000-8000-000000000000` com `{"name": "Maternal A"}` e não existe turma com esse id
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`, e nenhuma turma é criada

### Requirement: Excluir turma
O sistema SHALL excluir definitivamente, em `DELETE /api/v1/classrooms/{id}`, a turma sem alunos, respondendo `204 No Content`. Depois disso, a turma não aparece na consulta nem na listagem. O sistema SHALL recusar com `409 Conflict` e um `ProblemDetail`, sem excluir nada, a exclusão de uma turma com ao menos um aluno. Se não existir turma com esse id, SHALL responder `404 Not Found`.

#### Scenario: Turma sem alunos excluída
- **WHEN** existe a turma `Turma 3`, sem alunos, e um cliente envia `DELETE /api/v1/classrooms/{id}` com o id dela
- **THEN** a resposta é `204 No Content` sem corpo, `GET /api/v1/classrooms/{id}` passa a responder `404 Not Found` e a listagem não contém mais `Turma 3`

#### Scenario: Turma com alunos não é excluída
- **WHEN** a turma `Turma 1` tem 2 alunos e um cliente envia `DELETE /api/v1/classrooms/{id}` com o id dela
- **THEN** a resposta é `409 Conflict` com `Content-Type: application/problem+json`, e `GET /api/v1/classrooms/{id}` continua respondendo `200 OK` com `"studentCount": 2`

#### Scenario: Exclusão de turma inexistente
- **WHEN** um cliente envia `DELETE /api/v1/classrooms/0190f4a2-0000-7000-8000-000000000000` e não existe turma com esse id
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`

### Requirement: Contagens de alunos da turma
Toda resposta que devolve turmas SHALL trazer `studentCount` (total de alunos da turma), `maleCount` (alunos de gênero masculino) e `femaleCount` (alunos de gênero feminino), números inteiros não negativos. Alunos de outro gênero ou sem gênero informado SHALL contar só em `studentCount`. Enquanto o sistema não tiver vínculo entre criança e turma (matrícula), as três contagens SHALL ser `0` em toda turma.

#### Scenario: Turma nova sem alunos
- **WHEN** um cliente cria a turma `Turma 1` e depois envia `GET /api/v1/classrooms`
- **THEN** o item `Turma 1` tem `"studentCount": 0`, `"maleCount": 0` e `"femaleCount": 0`

#### Scenario: Aluno de outro gênero conta só no total
- **WHEN** a turma `Turma 1` tem 3 alunos, um de gênero masculino, um feminino e um de outro gênero, e um cliente envia `GET /api/v1/classrooms/{id}` com o id dela
- **THEN** a resposta é `200 OK` com `"studentCount": 3`, `"maleCount": 1` e `"femaleCount": 1`

### Requirement: Autoria da turma
Cada turma SHALL guardar o id do usuário autenticado que a criou e o do que a alterou por último. A renomeação SHALL atualizar só o autor da última alteração. A autoria SHALL NOT aparecer nas respostas da API.

#### Scenario: Autoria de criação e de renomeação
- **WHEN** o `ADMIN` `admin@educare.org` cria a turma `Turma 1`, e depois o `ADMIN` `bruno@educare.org` a renomeia para `Maternal A`
- **THEN** a turma guarda como autor da criação o id de `admin@educare.org` e como autor da última alteração o id de `bruno@educare.org`

#### Scenario: Autoria fora da resposta
- **WHEN** um cliente envia `POST /api/v1/classrooms` com `{"name": "Turma 1"}`
- **THEN** a resposta `201 Created` não tem as propriedades `createdBy` nem `updatedBy`

### Requirement: Corpo de requisição ilegível
O sistema SHALL recusar com `400 Bad Request` e um `ProblemDetail`, sem gravar nada, a criação e a renomeação de turma cujo corpo não seja um JSON válido.

#### Scenario: JSON inválido na criação
- **WHEN** um cliente envia `POST /api/v1/classrooms` com o corpo `{"name": "Turma 1"` (JSON incompleto) e `Content-Type: application/json`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`, e nenhuma turma é gravada

#### Scenario: JSON válido é aceito
- **WHEN** um cliente envia `POST /api/v1/classrooms` com `{"name": "Turma 1"}` e `Content-Type: application/json`
- **THEN** a resposta é `201 Created`

### Requirement: Gestão de turmas restrita a ADMIN
Todos os endpoints de `/api/v1/classrooms` SHALL exigir um usuário autenticado com role `ADMIN`. Sem token válido, SHALL responder `401 Unauthorized`. Com um usuário de role `USER`, SHALL responder `403 Forbidden`. Nos dois casos, a resposta é um `ProblemDetail` e a operação não é executada. Nos demais requirements desta capability, "um cliente" é um usuário autenticado com role `ADMIN`.

#### Scenario: ADMIN lista turmas
- **WHEN** `admin@educare.org`, com role `ADMIN`, envia `GET /api/v1/classrooms` com o token dele
- **THEN** a resposta é `200 OK`

#### Scenario: USER não lista turmas
- **WHEN** a usuária `ana.souza@educare.org`, com role `USER`, envia `GET /api/v1/classrooms` com o token dela
- **THEN** a resposta é `403 Forbidden` com `Content-Type: application/problem+json`

#### Scenario: USER não cria turma
- **WHEN** a usuária `ana.souza@educare.org`, com role `USER`, envia `POST /api/v1/classrooms` com o token dela e `{"name": "Turma 9"}`
- **THEN** a resposta é `403 Forbidden`, e não existe turma `Turma 9`

#### Scenario: USER não renomeia nem exclui turma
- **WHEN** existe a turma `Turma 1`, e a usuária `ana.souza@educare.org`, com role `USER`, envia com o token dela `GET /api/v1/classrooms/{id}`, `PUT /api/v1/classrooms/{id}` com `{"name": "Maternal A"}` e `DELETE /api/v1/classrooms/{id}`
- **THEN** as três respostas são `403 Forbidden`, e a turma continua existindo com o nome `Turma 1`

#### Scenario: Exclusão sem token
- **WHEN** existe a turma `Turma 1` e um cliente envia `DELETE /api/v1/classrooms/{id}` com o id dela, sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized`, e a turma continua existindo
