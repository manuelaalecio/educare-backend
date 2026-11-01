# Spec Delta

## ADDED Requirements

### Requirement: Turma obrigatória da criança
Toda criança SHALL estar em exatamente uma turma ativa. As requisições de criação (`POST /api/v1/children`) e de alteração (`PUT /api/v1/children/{id}`) SHALL exigir o campo `classroomId`, com o id de uma turma existente. O sistema SHALL recusar com `400 Bad Request` e uma entrada em `errors` para o campo `classroomId`, sem gravar nada (nem criança, nem vínculo, nem alteração), quando o `classroomId`:
- estiver ausente ou `null`;
- não for um UUID válido;
- não for o id de uma turma existente; nesse caso, a mensagem da entrada SHALL ser "Turma não encontrada".

A criação SHALL abrir o vínculo da criança com a turma, com data de início igual à data atual no fuso `America/Sao_Paulo` e sem data de fim. As demais validações da criança continuam valendo; as entradas de `errors` de `classroomId` e dos outros campos SHALL aparecer juntas quando vierem da validação do corpo.

#### Scenario: Criança criada com turma
- **WHEN** existe a turma `Turma 1`, o relógio da aplicação está em `2026-10-01T13:00:00Z`, e um cliente envia `POST /api/v1/children` com os dados obrigatórios válidos e o `classroomId` da `Turma 1`
- **THEN** a resposta é `201 Created` com `"classroom": {"id": "<id da Turma 1>", "name": "Turma 1"}`, e `GET /api/v1/children/{id}/classrooms` devolve um único item, com a `Turma 1`, `"startDate": "2026-10-01"` e `"endDate": null`

#### Scenario: Criação sem turma
- **WHEN** um cliente envia `POST /api/v1/children` com os dados obrigatórios válidos e sem `classroomId`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json` e uma entrada em `errors` para o campo `classroomId`; nenhuma criança é gravada

#### Scenario: Criação sem turma e com outro campo inválido
- **WHEN** um cliente envia `POST /api/v1/children` com `"cep": "123"`, os demais dados obrigatórios válidos e sem `classroomId`
- **THEN** a resposta é `400 Bad Request`, e `errors` contém uma entrada para `classroomId` e uma para `cep`

#### Scenario: Criação com turma inexistente
- **WHEN** um cliente envia `POST /api/v1/children` com os dados obrigatórios válidos e `"classroomId": "0190f4a2-0000-7000-8000-000000000000"`, e não existe turma com esse id
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `classroomId` com a mensagem "Turma não encontrada"; nenhuma criança nem vínculo é gravado

#### Scenario: Criação com classroomId malformado
- **WHEN** um cliente envia `POST /api/v1/children` com os dados obrigatórios válidos e `"classroomId": "turma-1"`
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `classroomId`; nenhuma criança é gravada

#### Scenario: Alteração sem turma
- **WHEN** a criança `Maria Clara Souza` está na `Turma 1` e um cliente envia `PUT /api/v1/children/{id}` com os dados obrigatórios válidos, `"name": "Maria Clara Lima"` e sem `classroomId`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `classroomId`, a criança continua com o nome `Maria Clara Souza` e na `Turma 1`, e o histórico dela não muda

### Requirement: Troca de turma com histórico
Na alteração da criança, o sistema SHALL comparar o `classroomId` recebido com a turma atual:
- mesma turma: o vínculo atual SHALL continuar como está;
- outra turma: o vínculo atual SHALL receber como data de fim a data atual no fuso `America/Sao_Paulo`, sem ser apagado, e um novo vínculo SHALL ser aberto com a nova turma, com essa mesma data de início e sem data de fim.

Trocar de turma mais de uma vez no mesmo dia SHALL encerrar o vínculo aberto naquele dia com data de início igual à de fim. Uma alteração recusada (`400`, `404` ou `409`) SHALL NOT mudar os vínculos.

#### Scenario: Troca para outra turma
- **WHEN** a criança `Maria Clara Souza` está na `Turma 1` desde `2026-10-01`, o relógio da aplicação está em `2026-11-01T13:00:00Z`, e um cliente envia `PUT /api/v1/children/{id}` com os dados válidos e o `classroomId` da `Turma 2`
- **THEN** a resposta é `200 OK` com `"classroom": {"id": "<id da Turma 2>", "name": "Turma 2"}`, e `GET /api/v1/children/{id}/classrooms` devolve, nessa ordem, a `Turma 2` com `"startDate": "2026-11-01"` e `"endDate": null`, e a `Turma 1` com `"startDate": "2026-10-01"` e `"endDate": "2026-11-01"`

#### Scenario: Alteração mantendo a turma
- **WHEN** a criança `Maria Clara Souza` está na `Turma 1` desde `2026-10-01` e, em `2026-11-01`, um cliente a altera com `"name": "Maria Clara Lima"` e o mesmo `classroomId`
- **THEN** a resposta é `200 OK` com `"name": "Maria Clara Lima"` e a `Turma 1`, e o histórico continua com um único item, com `"startDate": "2026-10-01"` e `"endDate": null`

#### Scenario: Duas trocas no mesmo dia
- **WHEN** a criança `Maria Clara Souza` está na `Turma 1` desde `2026-10-01` e, em `2026-11-01`, um cliente a troca para a `Turma 2` e, mais tarde no mesmo dia, para a `Turma 3`
- **THEN** a turma atual é a `Turma 3`, e o histórico tem, nessa ordem, `Turma 3` (`2026-11-01`, `null`), `Turma 2` (`2026-11-01`, `2026-11-01`) e `Turma 1` (`2026-10-01`, `2026-11-01`)

#### Scenario: Troca para turma inexistente
- **WHEN** a criança `Maria Clara Souza` está na `Turma 1` e um cliente a altera com `"classroomId": "0190f4a2-0000-7000-8000-000000000000"`, que não é de nenhuma turma
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `classroomId` e a mensagem "Turma não encontrada", a criança continua na `Turma 1` e o histórico não muda

#### Scenario: Troca recusada por conflito
- **WHEN** a criança `Maria Clara Souza` está na `Turma 1`, outra criança tem o CPF `52998224725`, e um cliente altera `Maria Clara Souza` com o `classroomId` da `Turma 2` e `"cpf": "52998224725"`
- **THEN** a resposta é `409 Conflict`, a criança continua na `Turma 1` e o histórico não muda

### Requirement: Turma atual nas respostas de criança
As respostas que devolvem a criança completa (criação, alteração e consulta por id) SHALL conter `classroom`, com o `id` e o `name` atuais da turma do vínculo ativo. Renomear a turma SHALL refletir o novo nome nas respostas seguintes de criança.

#### Scenario: Consulta por id com a turma
- **WHEN** a criança `Maria Clara Souza` está na `Turma 1` e um cliente envia `GET /api/v1/children/{id}`
- **THEN** a resposta é `200 OK` com `"classroom": {"id": "<id da Turma 1>", "name": "Turma 1"}` e os demais dados da criança

#### Scenario: Turma renomeada
- **WHEN** a criança `Maria Clara Souza` está na `Turma 1`, um `COORDENADOR` renomeia a turma para `Maternal A`, e depois um cliente envia `GET /api/v1/children/{id}`
- **THEN** a resposta é `200 OK` com `"classroom": {"id": "<id da turma>", "name": "Maternal A"}`

### Requirement: Histórico de turmas da criança
O sistema SHALL devolver em `GET /api/v1/children/{id}/classrooms`, com `200 OK`, um array JSON (sem paginação) com todos os vínculos da criança, cada um com `classroomId`, `classroomName` (nome atual da turma), `startDate` e `endDate` (`null` no vínculo ativo), datas no formato `AAAA-MM-DD`. A ordem SHALL ser do mais recente para o mais antigo: por data de início decrescente; com a mesma data de início, primeiro o vínculo sem data de fim, depois por data de fim decrescente e, por último, do vínculo criado mais recentemente para o mais antigo. Se não existir criança com esse id, SHALL responder `404 Not Found` com um `ProblemDetail`; se o id não for um UUID válido, `400 Bad Request`. O endpoint SHALL seguir as mesmas regras de acesso da consulta por id (qualquer usuário autenticado; sem token, `401 Unauthorized`).

#### Scenario: Histórico de uma criança que nunca trocou de turma
- **WHEN** a criança `Maria Clara Souza` foi criada na `Turma 1` em `2026-10-01` e um usuário `EDUCADOR` envia `GET /api/v1/children/{id}/classrooms`
- **THEN** a resposta é `200 OK` com `[{"classroomId": "<id da Turma 1>", "classroomName": "Turma 1", "startDate": "2026-10-01", "endDate": null}]`

#### Scenario: Histórico de criança inexistente
- **WHEN** um cliente envia `GET /api/v1/children/0190f4a2-0000-7000-8000-000000000000/classrooms` e não existe criança com esse id
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`

#### Scenario: Histórico com id malformado
- **WHEN** um cliente envia `GET /api/v1/children/abc/classrooms`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`

#### Scenario: Histórico sem token
- **WHEN** existe a criança `Maria Clara Souza` e um cliente envia `GET /api/v1/children/{id}/classrooms` sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json`

### Requirement: Exclusão da criança apaga o histórico de turmas
A exclusão da criança (`DELETE /api/v1/children/{id}`) SHALL apagar junto todos os vínculos dela com turmas, ativos e encerrados. Depois disso, a criança deixa de contar nas turmas, e as turmas por onde ela passou deixam de ter histórico por causa dela.

#### Scenario: Criança com dois vínculos excluída
- **WHEN** a criança `Maria Clara Souza` passou pela `Turma 1` e está na `Turma 2`, nenhuma outra criança passou por essas turmas, e um `COORDENADOR` envia `DELETE /api/v1/children/{id}`
- **THEN** a resposta é `204 No Content`, `GET /api/v1/children/{id}/classrooms` passa a responder `404 Not Found`, a `Turma 2` passa a ter `"studentCount": 0`, e depois `DELETE /api/v1/classrooms/{id da Turma 1}` responde `204 No Content`

#### Scenario: EDUCADOR não exclui criança com turma
- **WHEN** a criança `Maria Clara Souza` está na `Turma 1` e um usuário `EDUCADOR` envia `DELETE /api/v1/children/{id}`
- **THEN** a resposta é `403 Forbidden`, a criança continua na `Turma 1` e o histórico dela não muda

## MODIFIED Requirements

### Requirement: Listar crianças com paginação e busca por nome
O sistema SHALL listar as crianças em `GET /api/v1/children` com `200 OK`, paginadas pelos parâmetros `page` (começa em 0, padrão 0), `size` (padrão 20, máximo 100; valores maiores são tratados como 100) e `sort` (`name`, `birthDate` ou `createdAt`, com direção `asc` ou `desc`; padrão `name,asc`). O corpo SHALL ter `content` e `page` (com `size`, `number`, `totalElements` e `totalPages`). Cada item de `content` SHALL ter só `id`, `name`, `birthDate`, `classroom` (com o `id` e o `name` da turma atual), `createdAt` e `updatedAt`, sem os demais dados da criança. Uma ordenação por propriedade fora das permitidas SHALL ser recusada com `400 Bad Request` e um `ProblemDetail`.

O parâmetro opcional `name` SHALL filtrar as crianças cujo nome contém o texto informado, sem diferenciar maiúsculas e minúsculas nem letras com e sem acento, e tratando `%` e `_` como caracteres comuns. Um `name` vazio ou só com espaços SHALL ser ignorado, e os espaços nas pontas do texto buscado SHALL ser removidos.

O parâmetro opcional `classroomId` SHALL filtrar as crianças cuja turma atual é a informada; crianças que já saíram dessa turma SHALL NOT aparecer. Um `classroomId` que não seja um UUID válido SHALL ser recusado com `400 Bad Request` e um `ProblemDetail`; um `classroomId` que não seja de nenhuma turma SHALL resultar numa página vazia. Os filtros `name` e `classroomId` SHALL poder ser usados juntos, e a criança precisa atender aos dois.

#### Scenario: Primeira página ordenada por nome
- **WHEN** existem só as crianças `Carla`, `Ana`, `Bruno` e `Davi`, e um cliente envia `GET /api/v1/children?size=2`
- **THEN** a resposta é `200 OK`, `content` tem `Ana` e `Bruno`, nessa ordem, e `page` tem `"size": 2`, `"number": 0`, `"totalElements": 4` e `"totalPages": 2`

#### Scenario: Itens da listagem sem dados sensíveis
- **WHEN** existe só a criança `Maria Clara Souza`, na `Turma 1`, criada com CPF, RG, certidão de nascimento, endereço e dados de saúde, e um cliente envia `GET /api/v1/children`
- **THEN** o item de `content` tem exatamente as propriedades `id`, `name`, `birthDate`, `classroom`, `createdAt` e `updatedAt`, e `classroom` tem exatamente `"id": "<id da Turma 1>"` e `"name": "Turma 1"`

#### Scenario: Ordenação decrescente por data de nascimento
- **WHEN** existem só as crianças `Ana` (nascida em `2019-01-01`), `Bruno` (`2017-06-15`) e `Carla` (`2020-03-20`), e um cliente envia `GET /api/v1/children?sort=birthDate,desc`
- **THEN** a resposta é `200 OK` e `content` tem `Carla`, `Ana` e `Bruno`, nessa ordem

#### Scenario: Busca sem acento e sem maiúsculas
- **WHEN** existem só as crianças `João Pedro`, `Joana Lima` e `Maria João`, e um cliente envia `GET /api/v1/children?name=joao`
- **THEN** a resposta é `200 OK`, `content` tem `João Pedro` e `Maria João`, nessa ordem, e `page.totalElements` é `2`

#### Scenario: Busca sem resultado
- **WHEN** existem só as crianças `Ana` e `Bruno`, e um cliente envia `GET /api/v1/children?name=zz`
- **THEN** a resposta é `200 OK`, `content` é vazio e `page.totalElements` é `0`

#### Scenario: Caracteres curinga na busca
- **WHEN** existem só as crianças `Ana` e `Bruno`, e um cliente envia `GET /api/v1/children?name=%25` (o texto `%`)
- **THEN** a resposta é `200 OK` e `content` é vazio

#### Scenario: Busca em branco é ignorada
- **WHEN** existem só as crianças `Ana` e `Bruno`, e um cliente envia `GET /api/v1/children?name=%20%20`
- **THEN** a resposta é `200 OK` e `content` tem `Ana` e `Bruno`

#### Scenario: Tamanho de página acima do máximo
- **WHEN** existem só 3 crianças e um cliente envia `GET /api/v1/children?size=500`
- **THEN** a resposta é `200 OK`, `page.size` é `100` e `content` tem as 3 crianças

#### Scenario: Ordenação por propriedade não permitida
- **WHEN** um cliente envia `GET /api/v1/children?sort=cpf`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`

#### Scenario: Filtro por turma com busca por nome
- **WHEN** a `Turma 1` tem as crianças `João Pedro` e `Ana Lima`, a `Turma 2` tem `Maria João`, a criança `João Paulo` saiu da `Turma 1` e está na `Turma 2`, e um cliente envia `GET /api/v1/children?classroomId=<id da Turma 1>&name=joao`
- **THEN** a resposta é `200 OK`, `content` tem só `João Pedro`, com `classroom.name` igual a `Turma 1`, e `page.totalElements` é `1`

#### Scenario: Filtro por turma sem crianças ou inexistente
- **WHEN** existe a `Turma 3`, sem crianças, e um cliente envia `GET /api/v1/children?classroomId=<id da Turma 3>` e depois `GET /api/v1/children?classroomId=0190f4a2-0000-7000-8000-000000000000`, que não é de nenhuma turma
- **THEN** as duas respostas são `200 OK` com `content` vazio e `page.totalElements` igual a `0`

#### Scenario: Filtro por turma malformado
- **WHEN** um cliente envia `GET /api/v1/children?classroomId=turma-1`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`
