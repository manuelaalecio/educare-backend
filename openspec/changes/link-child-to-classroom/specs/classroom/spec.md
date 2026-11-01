# Spec Delta

## MODIFIED Requirements

### Requirement: Excluir turma
O sistema SHALL excluir definitivamente, em `DELETE /api/v1/classrooms/{id}`, a turma que nunca teve crianças, respondendo `204 No Content`. Depois disso, a turma não aparece na consulta nem na listagem. O sistema SHALL recusar com `409 Conflict` e um `ProblemDetail`, sem excluir nada:
- a exclusão de uma turma com ao menos uma criança ativa, com o `detail` "A turma tem crianças. Tire as crianças da turma antes de excluí-la";
- a exclusão de uma turma sem crianças ativas, mas pela qual alguma criança já passou (histórico), para preservar o histórico das crianças, com o `detail` "A turma tem histórico de crianças e não pode ser excluída".

Se não existir turma com esse id, SHALL responder `404 Not Found`.

#### Scenario: Turma sem alunos excluída
- **WHEN** existe a turma `Turma 3`, pela qual nenhuma criança passou, e um cliente envia `DELETE /api/v1/classrooms/{id}` com o id dela
- **THEN** a resposta é `204 No Content` sem corpo, `GET /api/v1/classrooms/{id}` passa a responder `404 Not Found` e a listagem não contém mais `Turma 3`

#### Scenario: Turma com alunos não é excluída
- **WHEN** a turma `Turma 1` tem 2 crianças ativas e um cliente envia `DELETE /api/v1/classrooms/{id}` com o id dela
- **THEN** a resposta é `409 Conflict` com `Content-Type: application/problem+json` e `"detail": "A turma tem crianças. Tire as crianças da turma antes de excluí-la"`, e `GET /api/v1/classrooms/{id}` continua respondendo `200 OK` com `"studentCount": 2`

#### Scenario: Turma só com histórico não é excluída
- **WHEN** a criança `Maria Clara Souza` estava na `Turma 1` e foi trocada para a `Turma 2`, a `Turma 1` ficou sem crianças ativas, e um cliente envia `DELETE /api/v1/classrooms/{id da Turma 1}`
- **THEN** a resposta é `409 Conflict` com `"detail": "A turma tem histórico de crianças e não pode ser excluída"`, `GET /api/v1/classrooms/{id da Turma 1}` continua respondendo `200 OK` com `"studentCount": 0`, e o histórico de `Maria Clara Souza` continua com a `Turma 1`

#### Scenario: Exclusão de turma inexistente
- **WHEN** um cliente envia `DELETE /api/v1/classrooms/0190f4a2-0000-7000-8000-000000000000` e não existe turma com esse id
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`

### Requirement: Contagens de alunos da turma
Toda resposta que devolve turmas SHALL trazer `studentCount` (total de crianças ativas na turma), `maleCount` (crianças ativas de gênero `MALE`) e `femaleCount` (crianças ativas de gênero `FEMALE`), números inteiros não negativos. Uma criança é ativa na turma enquanto o vínculo dela com a turma não tem data de fim; crianças que já saíram da turma SHALL NOT contar. Crianças de gênero `OTHER` ou sem gênero informado SHALL contar só em `studentCount`. As contagens SHALL refletir imediatamente a criação, a troca de turma, a alteração de gênero e a exclusão de crianças. A listagem de turmas SHALL obter as contagens de todas as turmas de uma vez, sem uma consulta por turma.

#### Scenario: Turma nova sem alunos
- **WHEN** um cliente cria a turma `Turma 1` e depois envia `GET /api/v1/classrooms`
- **THEN** o item `Turma 1` tem `"studentCount": 0`, `"maleCount": 0` e `"femaleCount": 0`

#### Scenario: Contagens pelas crianças ativas
- **WHEN** a turma `Turma 1` tem como crianças ativas 2 de gênero `MALE`, 1 de gênero `FEMALE` e 1 sem gênero, outra criança de gênero `FEMALE` saiu da `Turma 1` para a `Turma 2`, e um cliente envia `GET /api/v1/classrooms`
- **THEN** o item `Turma 1` tem `"studentCount": 4`, `"maleCount": 2` e `"femaleCount": 1`, e o item `Turma 2` tem `"studentCount": 1`, `"maleCount": 0` e `"femaleCount": 1`

#### Scenario: Aluno de outro gênero conta só no total
- **WHEN** a turma `Turma 1` tem 3 crianças ativas, uma de gênero `MALE`, uma `FEMALE` e uma `OTHER`, e um cliente envia `GET /api/v1/classrooms/{id}` com o id dela
- **THEN** a resposta é `200 OK` com `"studentCount": 3`, `"maleCount": 1` e `"femaleCount": 1`

#### Scenario: Contagem acompanha a troca de turma e a exclusão
- **WHEN** a criança `Ana`, de gênero `FEMALE`, é criada na `Turma 1`, depois trocada para a `Turma 2`, e depois excluída, e um cliente consulta as duas turmas depois de cada passo
- **THEN** depois da criação a `Turma 1` tem `"studentCount": 1` e `"femaleCount": 1`; depois da troca a `Turma 1` tem `"studentCount": 0` e a `Turma 2` tem `"studentCount": 1` e `"femaleCount": 1`; depois da exclusão as duas têm `"studentCount": 0`

## REMOVED Requirements

### Requirement: Gestão de turmas restrita a COORDENADOR
**Reason**: A leitura das turmas passa a ser permitida também ao `EDUCADOR`, que precisa escolher a turma no cadastro de crianças; só a gestão (criar, renomear e excluir) continua restrita a `COORDENADOR`.
**Migration**: Substituído pelo requirement "Acesso às turmas por role", nesta mesma capability. Clientes `EDUCADOR` que recebiam `403` em `GET /api/v1/classrooms` e `GET /api/v1/classrooms/{id}` passam a receber `200`; nada muda para `COORDENADOR`.

## ADDED Requirements

### Requirement: Acesso às turmas por role
Todos os endpoints de `/api/v1/classrooms` SHALL exigir um usuário autenticado; sem token válido, SHALL responder `401 Unauthorized`. A leitura (`GET /api/v1/classrooms` e `GET /api/v1/classrooms/{id}`) SHALL ser permitida a usuários com role `COORDENADOR` ou `EDUCADOR`. A gestão (criar, renomear e excluir turmas) SHALL exigir a role `COORDENADOR`; com um usuário de role `EDUCADOR`, SHALL responder `403 Forbidden`. Nos casos de `401` e `403`, a resposta é um `ProblemDetail` e a operação não é executada. Nos demais requirements desta capability, "um cliente" é um usuário autenticado com role `COORDENADOR`.

#### Scenario: COORDENADOR lista turmas
- **WHEN** `admin@educare.org`, com role `COORDENADOR`, envia `GET /api/v1/classrooms` com o token dele
- **THEN** a resposta é `200 OK`

#### Scenario: EDUCADOR lista e consulta turmas
- **WHEN** existe a turma `Turma 1` e a usuária `ana.souza@educare.org`, com role `EDUCADOR`, envia com o token dela `GET /api/v1/classrooms` e `GET /api/v1/classrooms/{id}`
- **THEN** as duas respostas são `200 OK`, a primeira com a `Turma 1` na lista e a segunda com `"name": "Turma 1"` e as contagens

#### Scenario: EDUCADOR não cria turma
- **WHEN** a usuária `ana.souza@educare.org`, com role `EDUCADOR`, envia `POST /api/v1/classrooms` com o token dela e `{"name": "Turma 9"}`
- **THEN** a resposta é `403 Forbidden`, e não existe turma `Turma 9`

#### Scenario: EDUCADOR não renomeia nem exclui turma
- **WHEN** existe a turma `Turma 1`, e a usuária `ana.souza@educare.org`, com role `EDUCADOR`, envia com o token dela `PUT /api/v1/classrooms/{id}` com `{"name": "Maternal A"}` e `DELETE /api/v1/classrooms/{id}`
- **THEN** as duas respostas são `403 Forbidden`, e a turma continua existindo com o nome `Turma 1`

#### Scenario: Leitura sem token
- **WHEN** existe a turma `Turma 1` e um cliente envia `GET /api/v1/classrooms` sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json`

#### Scenario: Exclusão sem token
- **WHEN** existe a turma `Turma 1` e um cliente envia `DELETE /api/v1/classrooms/{id}` com o id dela, sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized`, e a turma continua existindo
