# Spec Delta

## ADDED Requirements

### Requirement: Autoria dos registros
Todo registro de negócio SHALL guardar o id do usuário autenticado que o criou e o id do usuário autenticado que o alterou por último, preenchidos pelo sistema a partir do usuário da requisição. Na primeira gravação, os dois SHALL ser o usuário da requisição; a cada alteração gravada, só o autor da última alteração SHALL mudar, e o autor da criação SHALL NOT mudar depois da primeira gravação. Uma requisição recusada, que não grava nada, SHALL NOT alterar a autoria. Um registro gravado sem usuário autenticado SHALL ficar sem autor da criação e sem autor da alteração. A exclusão do usuário autor SHALL NOT alterar a autoria dos registros dele nem ser impedida por ela. A autoria SHALL NOT aparecer nas respostas da API.

Nos cenários abaixo, `admin@educare.org` e `bruno.lima@educare.org` têm role `ADMIN`, e `ana.souza@educare.org` tem role `USER`.

#### Scenario: Criação registra o usuário autenticado
- **WHEN** `admin@educare.org` cria o usuário `ana.souza@educare.org` com `POST /api/v1/users`
- **THEN** a resposta é `201 Created`, e o registro de `ana.souza@educare.org` tem o id de `admin@educare.org` como autor da criação e como autor da última alteração

#### Scenario: Alteração muda só o autor da última alteração
- **WHEN** o usuário `ana.souza@educare.org` foi criado por `admin@educare.org`, e `bruno.lima@educare.org` o altera com `PUT /api/v1/users/{id}` e dados válidos
- **THEN** a resposta é `200 OK`, e o registro continua com `admin@educare.org` como autor da criação e passa a ter `bruno.lima@educare.org` como autor da última alteração

#### Scenario: Usuário que altera o próprio registro
- **WHEN** o usuário `ana.souza@educare.org` foi criado por `admin@educare.org`, e a própria `ana.souza@educare.org` troca a senha com `PUT /api/v1/auth/me/password`
- **THEN** a resposta é `204 No Content`, e o registro passa a ter `ana.souza@educare.org` como autor da última alteração, com `admin@educare.org` ainda como autor da criação

#### Scenario: Requisição recusada não muda a autoria
- **WHEN** o usuário `ana.souza@educare.org` foi criado e alterado por último por `admin@educare.org`, e `bruno.lima@educare.org` tenta alterá-lo com `{"name": "", "login": "ana", "role": "USER"}`
- **THEN** a resposta é `400 Bad Request`, e o registro continua com `admin@educare.org` como autor da criação e da última alteração

#### Scenario: Tentativa de alterar o autor da criação é ignorada
- **WHEN** um registro de negócio criado por `admin@educare.org` tem o autor da criação modificado para o id de `bruno.lima@educare.org` antes de ser gravado de novo, numa requisição de `bruno.lima@educare.org`
- **THEN** o autor da criação gravado no banco continua sendo `admin@educare.org`

#### Scenario: Registro gravado sem usuário autenticado
- **WHEN** a aplicação inicia contra um banco vazio e as migrations criam o administrador inicial `admin@educare.org`
- **THEN** o registro de `admin@educare.org` não tem autor da criação nem autor da última alteração

#### Scenario: Autor excluído mantém a autoria
- **WHEN** `bruno.lima@educare.org` criou o usuário `ana.souza@educare.org`, e depois `admin@educare.org` exclui `bruno.lima@educare.org`
- **THEN** a exclusão responde `204 No Content`, e o registro de `ana.souza@educare.org` continua com o id de `bruno.lima@educare.org` como autor da criação

#### Scenario: Respostas não expõem a autoria
- **WHEN** `admin@educare.org` cria o usuário `ana.souza@educare.org` e depois o consulta por id e na listagem
- **THEN** nenhuma das três respostas contém propriedades com o autor da criação ou da última alteração
