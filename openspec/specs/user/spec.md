# user Specification

## Purpose

Manter o cadastro dos usuários do sistema, que são os funcionários da instituição: criar, consultar, listar, alterar e excluir usuários, com login por email único, papel (role) de acesso e senha guardada de forma protegida.

## Requirements

### Requirement: Criar usuário
O sistema SHALL criar um usuário a partir de `name`, `login`, `password` e `role` (opcional) recebidos em `POST /api/v1/users`, respondendo `201 Created` com o header `Location` apontando para `/api/v1/users/{id}` e um corpo com `id`, `name`, `login`, `role`, `createdAt` e `updatedAt`. O sistema SHALL recusar com `400 Bad Request` e um `ProblemDetail` que lista cada campo inválido (propriedade `errors`, com `field` e `message`), sem gravar nada, quando:
- `name` estiver ausente, vazio, só com espaços ou tiver mais de 150 caracteres;
- `login` estiver ausente, não for um email no formato `nome@dominio.tld` (sem espaços, com um único `@` e ao menos um ponto no domínio) ou tiver mais de 254 caracteres;
- `password` estiver ausente, tiver menos de 8 ou mais de 72 caracteres, ou ocupar mais de 72 bytes em UTF-8 (limite do algoritmo de hash; só acontece com caracteres acentuados ou especiais);
- `role` estiver presente com valor diferente de `ADMIN` e `USER`.

O `name` SHALL ser gravado sem espaços no início e no fim.

#### Scenario: Usuário criado com dados válidos
- **WHEN** um cliente envia `POST /api/v1/users` com `{"name": "Ana Souza", "login": "ana.souza@educare.org", "password": "segredo123"}` e o relógio da aplicação está em `2026-03-01T10:00:00Z`
- **THEN** a resposta é `201 Created`, o header `Location` é `/api/v1/users/{id}` com o `id` gerado, e o corpo tem esse `id`, `"name": "Ana Souza"`, `"login": "ana.souza@educare.org"`, `"role": "USER"`, `"createdAt": "2026-03-01T10:00:00Z"` e `"updatedAt": "2026-03-01T10:00:00Z"`

#### Scenario: Nome com espaços nas pontas é gravado sem eles
- **WHEN** um cliente cria um usuário com `"name": "  Ana Souza  "` e os demais campos válidos
- **THEN** a resposta é `201 Created` com `"name": "Ana Souza"`

#### Scenario: Campos obrigatórios ausentes
- **WHEN** um cliente envia `POST /api/v1/users` com `{}`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`, e `errors` contém uma entrada para cada um dos campos `name`, `login` e `password`, e nenhuma para `role`; nenhum usuário é gravado

#### Scenario: Login que não é um email
- **WHEN** um cliente cria um usuário com `"login": "ana.souza"` e os demais campos válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `login`; nenhum usuário é gravado

#### Scenario: Email sem domínio completo
- **WHEN** um cliente cria um usuário com `"login": "ana@educare"` (domínio sem ponto) e os demais campos válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `login`; nenhum usuário é gravado

#### Scenario: Senha curta demais
- **WHEN** um cliente cria um usuário com `"password": "1234567"` (7 caracteres) e os demais campos válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `password`; nenhum usuário é gravado

#### Scenario: Senha acima de 72 bytes
- **WHEN** um cliente cria um usuário com uma `password` formada por 40 caracteres `ç` (40 caracteres, 80 bytes em UTF-8) e os demais campos válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `password`; nenhum usuário é gravado

#### Scenario: Nome longo demais
- **WHEN** um cliente cria um usuário com um `name` de 151 caracteres e os demais campos válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `name`; nenhum usuário é gravado

### Requirement: Login por email único sem diferenciar maiúsculas
O sistema SHALL gravar o `login` (um email) em letras minúsculas e SHALL tratar como o mesmo login valores que só diferem em maiúsculas e minúsculas. O sistema SHALL recusar com `409 Conflict` e um `ProblemDetail`, sem gravar nada, a criação ou alteração que deixaria dois usuários com o mesmo login. O `ProblemDetail` do conflito SHALL NOT conter o email.

#### Scenario: Login com maiúsculas é gravado em minúsculas
- **WHEN** um cliente cria um usuário com `"login": "Ana.Souza@Educare.org"`
- **THEN** a resposta é `201 Created` com `"login": "ana.souza@educare.org"`

#### Scenario: Criação com login já usado
- **WHEN** já existe um usuário com login `ana.souza@educare.org` e um cliente cria outro usuário com `"login": "ANA.SOUZA@EDUCARE.ORG"`
- **THEN** a resposta é `409 Conflict` com `Content-Type: application/problem+json`, o corpo não contém `ana.souza@educare.org` e continua existindo só um usuário com esse login

#### Scenario: Alteração para login de outro usuário
- **WHEN** existem os usuários `ana.souza@educare.org` e `bruno.lima@educare.org`, e um cliente altera `bruno.lima@educare.org` para `"login": "ana.souza@educare.org"`
- **THEN** a resposta é `409 Conflict`, e `bruno.lima@educare.org` continua com o login `bruno.lima@educare.org`

### Requirement: Papel do usuário
Todo usuário SHALL ter exatamente uma role, `ADMIN` ou `USER`. Na criação, a `role` é opcional e, quando ausente, o usuário SHALL ser criado como `USER`. Na alteração (`PUT /api/v1/users/{id}`), a `role` é obrigatória. Só os valores `ADMIN` e `USER`, exatamente em maiúsculas, SHALL ser aceitos; qualquer outro valor SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo `role`, sem gravar nada. A role SHALL aparecer em todas as respostas que devolvem usuários.

#### Scenario: Criação sem role vira USER
- **WHEN** um cliente cria um usuário com `name`, `login` e `password` válidos e sem `role`
- **THEN** a resposta é `201 Created` com `"role": "USER"`

#### Scenario: Criação como ADMIN
- **WHEN** um cliente cria um usuário com `"role": "ADMIN"` e os demais campos válidos
- **THEN** a resposta é `201 Created` com `"role": "ADMIN"`, e `GET /api/v1/users/{id}` devolve `"role": "ADMIN"`

#### Scenario: Role inválida
- **WHEN** um cliente cria um usuário com `"role": "SUPER"` e os demais campos válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `role`; nenhum usuário é gravado

#### Scenario: Promoção de USER para ADMIN
- **WHEN** o usuário `ana.souza@educare.org`, com role `USER`, é alterado com `PUT /api/v1/users/{id}` e `{"name": "Ana Souza", "login": "ana.souza@educare.org", "role": "ADMIN"}`
- **THEN** a resposta é `200 OK` com `"role": "ADMIN"`

#### Scenario: Alteração sem role
- **WHEN** o usuário `ana.souza@educare.org`, com role `USER`, é alterado com `{"name": "Ana Souza", "login": "ana.souza@educare.org"}`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `role`, e o usuário continua com role `USER`

### Requirement: Senha protegida
O sistema SHALL guardar a senha só na forma de hash não reversível, nunca o texto recebido. A senha e o hash dela SHALL NOT aparecer em nenhuma resposta da API.

#### Scenario: Senha não é guardada em texto
- **WHEN** um cliente cria um usuário com `"password": "segredo123"`
- **THEN** o valor guardado no banco para a senha desse usuário é diferente de `segredo123` e é reconhecido como correspondente a `segredo123` pela verificação de senha do sistema

#### Scenario: Respostas não expõem a senha
- **WHEN** um cliente cria um usuário e depois o consulta por id e na listagem
- **THEN** nenhuma das três respostas contém uma propriedade `password`, nem o valor da senha, nem o hash guardado

### Requirement: Consultar usuário por id
O sistema SHALL retornar em `GET /api/v1/users/{id}` o usuário com esse id, com `200 OK` e os campos `id`, `name`, `login`, `role`, `createdAt` e `updatedAt`. Se não existir usuário com esse id, SHALL responder `404 Not Found` com um `ProblemDetail`. Se o id não for um UUID válido, SHALL responder `400 Bad Request` com um `ProblemDetail`.

#### Scenario: Usuário existente
- **WHEN** existe o usuário `ana.souza@educare.org`, com role `USER`, e um cliente envia `GET /api/v1/users/{id}` com o id dele
- **THEN** a resposta é `200 OK` com o `id`, `"name": "Ana Souza"`, `"login": "ana.souza@educare.org"`, `"role": "USER"`, `createdAt` e `updatedAt` desse usuário

#### Scenario: Usuário inexistente
- **WHEN** um cliente envia `GET /api/v1/users/0190f4a2-0000-7000-8000-000000000000` e não existe usuário com esse id
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`

#### Scenario: Id malformado
- **WHEN** um cliente envia `GET /api/v1/users/abc`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`

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

### Requirement: Alterar nome, login e role
O sistema SHALL substituir o `name`, o `login` e a `role` do usuário com os valores recebidos em `PUT /api/v1/users/{id}`, respondendo `200 OK` com o usuário atualizado. Os três campos são obrigatórios e seguem as mesmas regras de validação da criação (recusa com `400 Bad Request` e `errors`). A senha SHALL NOT ser alterada por este endpoint. Se não existir usuário com esse id, SHALL responder `404 Not Found`. A alteração SHALL atualizar `updatedAt` e manter `createdAt`.

#### Scenario: Alteração com dados válidos
- **WHEN** o usuário `ana.souza@educare.org`, criado em `2026-03-01T10:00:00Z`, é alterado com `PUT /api/v1/users/{id}` e `{"name": "Ana Souza Lima", "login": "ana.lima@educare.org", "role": "USER"}`, com o relógio da aplicação em `2026-03-02T09:00:00Z`
- **THEN** a resposta é `200 OK` com `"name": "Ana Souza Lima"`, `"login": "ana.lima@educare.org"`, `"role": "USER"`, `"createdAt": "2026-03-01T10:00:00Z"` e `"updatedAt": "2026-03-02T09:00:00Z"`, e a senha do usuário continua a mesma

#### Scenario: Manter o próprio login
- **WHEN** o usuário `ana.souza@educare.org` é alterado com `{"name": "Ana S.", "login": "ANA.SOUZA@EDUCARE.ORG", "role": "USER"}`
- **THEN** a resposta é `200 OK` com `"name": "Ana S."` e `"login": "ana.souza@educare.org"`

#### Scenario: Alteração com dados inválidos
- **WHEN** o usuário `ana.souza@educare.org` é alterado com `{"name": "", "login": "ana", "role": "USER"}`
- **THEN** a resposta é `400 Bad Request` com entradas em `errors` para `name` e `login`, e o usuário continua com nome e login anteriores

#### Scenario: Alteração de usuário inexistente
- **WHEN** um cliente envia `PUT /api/v1/users/{id}` com dados válidos e um id que não existe
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`, e nenhum usuário é criado

### Requirement: Alterar senha
O sistema SHALL substituir a senha do usuário pela recebida em `PUT /api/v1/users/{id}/password` (corpo `{"password": "..."}`), respondendo `204 No Content`. A nova senha segue as regras da criação (8 a 72 caracteres e no máximo 72 bytes em UTF-8; recusa com `400 Bad Request` e `errors`). A alteração SHALL atualizar `updatedAt`. Se não existir usuário com esse id, SHALL responder `404 Not Found`.

#### Scenario: Senha alterada
- **WHEN** o usuário `ana.souza@educare.org`, com senha `segredo123`, recebe `PUT /api/v1/users/{id}/password` com `{"password": "novaSenha456"}`
- **THEN** a resposta é `204 No Content` sem corpo, a senha guardada passa a corresponder a `novaSenha456` e deixa de corresponder a `segredo123`

#### Scenario: Nova senha inválida
- **WHEN** o usuário `ana.souza@educare.org`, com senha `segredo123`, recebe `PUT /api/v1/users/{id}/password` com `{"password": "curta"}`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `password`, e a senha guardada continua correspondendo a `segredo123`

#### Scenario: Senha de usuário inexistente
- **WHEN** um cliente envia `PUT /api/v1/users/{id}/password` com uma senha válida e um id que não existe
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`

### Requirement: Excluir usuário
O sistema SHALL excluir definitivamente o usuário em `DELETE /api/v1/users/{id}`, respondendo `204 No Content`; depois disso, o usuário não aparece na consulta nem na listagem, e o login dele fica livre para outro usuário. Se não existir usuário com esse id, SHALL responder `404 Not Found`.

#### Scenario: Usuário excluído
- **WHEN** existe o usuário `ana.souza@educare.org` e um cliente envia `DELETE /api/v1/users/{id}` com o id dele
- **THEN** a resposta é `204 No Content`, `GET /api/v1/users/{id}` passa a responder `404 Not Found` e a listagem não contém mais esse usuário

#### Scenario: Login liberado após exclusão
- **WHEN** o usuário `ana.souza@educare.org` é excluído e depois um cliente cria outro usuário com `"login": "ana.souza@educare.org"`
- **THEN** a criação responde `201 Created`

#### Scenario: Exclusão de usuário inexistente
- **WHEN** um cliente envia `DELETE /api/v1/users/{id}` com um id que não existe
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`

### Requirement: Administrador inicial
Ao aplicar as migrations num banco vazio, o sistema SHALL criar um único usuário administrador com `"name": "Administrador"`, `"login": "admin@educare.org"` e role `ADMIN`, cuja senha é o valor configurado para o administrador inicial no ambiente (em desenvolvimento, `educare123` quando nada for configurado). Esse usuário SHALL ser criado uma única vez: excluí-lo ou alterá-lo não faz com que seja recriado ou restaurado em inicializações seguintes. Em produção, a senha do administrador inicial SHALL ser obrigatória, e a aplicação SHALL NOT iniciar sem ela.

#### Scenario: Banco vazio recebe o administrador
- **WHEN** a aplicação inicia contra um banco PostgreSQL 16 vazio, com a senha do administrador inicial configurada como `senhaAdmin123`
- **THEN** existe exatamente um usuário com login `admin@educare.org`, com nome `Administrador` e role `ADMIN`, e a senha guardada corresponde a `senhaAdmin123`

#### Scenario: Administrador excluído não é recriado
- **WHEN** o usuário `admin@educare.org` é excluído e a aplicação é reiniciada contra o mesmo banco
- **THEN** a aplicação fica disponível e não existe usuário com login `admin@educare.org`

#### Scenario: Produção sem senha do administrador
- **WHEN** a aplicação inicia com o profile `prod`, contra um banco vazio, sem a senha do administrador inicial configurada
- **THEN** a inicialização falha, e a tabela `users` não contém o usuário `admin@educare.org`

### Requirement: Corpo de requisição ilegível
O sistema SHALL recusar com `400 Bad Request` e um `ProblemDetail`, sem gravar nada, as requisições de criação e alteração cujo corpo não seja um JSON válido.

#### Scenario: JSON inválido na criação
- **WHEN** um cliente envia `POST /api/v1/users` com o corpo `{"name": "Ana"` (JSON incompleto) e `Content-Type: application/json`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`, e nenhum usuário é gravado

#### Scenario: JSON válido é aceito
- **WHEN** um cliente envia `POST /api/v1/users` com um JSON bem formado e campos válidos
- **THEN** a resposta é `201 Created`

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
