# Spec Delta

## Purpose

Manter o cadastro dos responsáveis pelas crianças atendidas: dados pessoais, documentos, contato e trabalho, consultados, listados com busca e alterados, com as crianças vinculadas e o parentesco de cada vínculo. Um responsável é cadastrado uma vez e pode servir a várias crianças.

## ADDED Requirements

### Requirement: Dados do responsável
Um responsável SHALL ter os campos obrigatórios `name`, `phone` e `notWorking`, os campos de trabalho `workplace`, `workAddress` e `workPhone`, e os campos opcionais `maritalStatus`, `birthDate`, `cpf` e `rg`, além de `id`, `createdAt` e `updatedAt`, gerados pelo sistema. Estas regras SHALL valer em toda gravação de responsável: na alteração (`PUT /api/v1/guardians/{id}`) e no responsável novo informado no corpo da criação ou da alteração de criança (capability `child`, requirement "Responsáveis obrigatórios da criança"), onde os campos aparecem com o prefixo do item (por exemplo, `guardians[0].guardian.name`).

`notWorking` ausente ou `null` SHALL valer `false`. Com `notWorking` igual a `false`, `workplace`, `workAddress` e `workPhone` SHALL ser obrigatórios. Com `notWorking` igual a `true`, esses três campos SHALL ser ignorados e gravados como ausentes, mesmo que tenham sido enviados.

O sistema SHALL recusar com `400 Bad Request` e um `ProblemDetail` que lista cada campo inválido (propriedade `errors`, com `field` e `message`), sem gravar nada, quando:
- `name` ou `phone` estiver ausente, vazio ou só com espaços;
- `notWorking` não for `true` e um dos campos de trabalho estiver ausente, vazio ou só com espaços;
- um campo de texto passar do tamanho máximo, contado depois de remover os espaços das pontas: `name` 150, `workplace` 150, `workAddress` 200 e `rg` 20 caracteres.

Todos os campos de texto SHALL ser gravados sem espaços no início e no fim. Um campo opcional de texto vazio ou só com espaços SHALL ser gravado como ausente e devolvido como `null`.

Nos cenários desta capability, "os dados válidos" são `{"name": "Maria Souza", "phone": "11912345678", "notWorking": false, "workplace": "Padaria Central", "workAddress": "Rua das Flores, 100", "workPhone": "1133334444"}`, e "a responsável Maria Souza" foi cadastrada com esses dados no corpo da criação da criança `Ana Souza`, com parentesco `MOTHER`.

#### Scenario: Alteração só com os dados obrigatórios
- **WHEN** existe a responsável Maria Souza e um cliente envia `PUT /api/v1/guardians/{id}` com os dados válidos e `"name": "Maria Souza Lima"`
- **THEN** a resposta é `200 OK` com `"name": "Maria Souza Lima"`, `"phone": "11912345678"`, `"notWorking": false`, `"workplace": "Padaria Central"`, `"workAddress": "Rua das Flores, 100"`, `"workPhone": "1133334444"` e `"maritalStatus"`, `"birthDate"`, `"cpf"` e `"rg"` com `null`

#### Scenario: Não trabalha dispensa e descarta os campos de trabalho
- **WHEN** existe a responsável Maria Souza e um cliente a altera com `{"name": "Maria Souza", "phone": "11912345678", "notWorking": true, "workplace": "Padaria Central"}`
- **THEN** a resposta é `200 OK` com `"notWorking": true`, `"workplace": null`, `"workAddress": null` e `"workPhone": null`

#### Scenario: Trabalha sem os dados do trabalho
- **WHEN** existe a responsável Maria Souza e um cliente a altera com `{"name": "Maria Souza", "phone": "11912345678", "notWorking": false, "workAddress": "   "}`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`, e `errors` contém uma entrada para cada um dos campos `workplace`, `workAddress` e `workPhone`; a responsável continua com os dados anteriores

#### Scenario: notWorking ausente vale falso
- **WHEN** existe a responsável Maria Souza e um cliente a altera com `{"name": "Maria Souza", "phone": "11912345678"}`
- **THEN** a resposta é `400 Bad Request`, e `errors` contém uma entrada para cada um dos campos `workplace`, `workAddress` e `workPhone`

#### Scenario: Obrigatórios ausentes
- **WHEN** existe a responsável Maria Souza e um cliente a altera com `{}`
- **THEN** a resposta é `400 Bad Request`, e `errors` contém uma entrada para cada um dos campos `name`, `phone`, `workplace`, `workAddress` e `workPhone`, e nenhuma para `maritalStatus`, `birthDate`, `cpf` e `rg`

#### Scenario: Textos com espaços nas pontas e opcional em branco
- **WHEN** existe a responsável Maria Souza e um cliente a altera com os dados válidos, `"name": "  Maria Souza  "` e `"rg": "   "`
- **THEN** a resposta é `200 OK` com `"name": "Maria Souza"` e `"rg": null`

#### Scenario: Textos longos demais e no limite
- **WHEN** existe a responsável Maria Souza e um cliente a altera com os dados válidos, um `name` de 151 caracteres e um `workAddress` de 201 caracteres, e depois com um `name` de 150 caracteres e um `workAddress` de 200 caracteres
- **THEN** a primeira resposta é `400 Bad Request` com uma entrada em `errors` para `name` e uma para `workAddress`, e a segunda é `200 OK`

### Requirement: Telefones do responsável
`phone` e `workPhone` SHALL ter só dígitos, com o DDD: 10 ou 11 dígitos (`1133334444` ou `11912345678`). Qualquer outro valor (com máscara, espaços, letras ou outra quantidade de dígitos) SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo, sem gravar nada. O sistema não verifica se o DDD existe.

#### Scenario: Telefones com 10 e 11 dígitos
- **WHEN** existe a responsável Maria Souza e um cliente a altera com os dados válidos, `"phone": "1133334444"` e `"workPhone": "11987654321"`
- **THEN** a resposta é `200 OK` com `"phone": "1133334444"` e `"workPhone": "11987654321"`

#### Scenario: Telefone curto e telefone com máscara
- **WHEN** existe a responsável Maria Souza e um cliente a altera com os dados válidos, `"phone": "111234"` e `"workPhone": "(11) 3333-4444"`
- **THEN** a resposta é `400 Bad Request`, e `errors` contém uma entrada para `phone` e uma para `workPhone`; a responsável continua com os telefones anteriores

### Requirement: Data de nascimento do responsável
Quando informada, a `birthDate` SHALL ser uma data no formato `AAAA-MM-DD` que não seja posterior à data atual no fuso `America/Sao_Paulo`; a própria data atual SHALL ser aceita. Uma data futura ou que não seja uma data válida nesse formato SHALL ser recusada com `400 Bad Request` e uma entrada em `errors` para o campo `birthDate`, sem gravar nada. Ausente ou `null`, o campo fica sem valor.

#### Scenario: Nascimento hoje no Brasil
- **WHEN** o relógio da aplicação está em `2026-03-02T01:00:00Z` (ainda `2026-03-01` em `America/Sao_Paulo`) e um cliente altera a responsável Maria Souza com os dados válidos e `"birthDate": "2026-03-01"`
- **THEN** a resposta é `200 OK` com `"birthDate": "2026-03-01"`

#### Scenario: Nascimento no futuro
- **WHEN** o relógio da aplicação está em `2026-03-02T01:00:00Z` e um cliente altera a responsável Maria Souza com os dados válidos e `"birthDate": "2026-03-02"`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `birthDate`; a responsável continua sem data de nascimento

#### Scenario: Data em outro formato
- **WHEN** um cliente altera a responsável Maria Souza com os dados válidos e `"birthDate": "10/05/1990"`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json` e uma entrada em `errors` para `birthDate`

### Requirement: Estado civil do responsável
Quando informado, o `maritalStatus` SHALL aceitar só os mesmos valores do estado civil da criança, exatamente em maiúsculas: `SINGLE`, `MARRIED`, `STABLE_UNION`, `DIVORCED`, `WIDOWED`. Qualquer outro valor SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo, sem gravar nada. Ausente ou `null`, o campo fica sem valor.

#### Scenario: Estado civil válido
- **WHEN** um cliente altera a responsável Maria Souza com os dados válidos e `"maritalStatus": "DIVORCED"`
- **THEN** a resposta é `200 OK` com `"maritalStatus": "DIVORCED"`

#### Scenario: Estado civil fora da lista
- **WHEN** um cliente altera a responsável Maria Souza com os dados válidos e `"maritalStatus": "married"`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `maritalStatus`

### Requirement: CPF do responsável válido e único
Quando informado, o `cpf` do responsável SHALL seguir as mesmas regras de formato do CPF da criança: 11 dígitos, sem máscara (`52998224725`) ou com a máscara completa (`529.982.247-25`), com os dígitos verificadores corretos e sem ser formado por um único dígito repetido; caso contrário, SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo, sem gravar nada. O `cpf` SHALL ser gravado e devolvido só com os dígitos.

Dois responsáveis SHALL NOT ter o mesmo CPF. A gravação que causaria isso SHALL ser recusada com `409 Conflict` e um `ProblemDetail` com `"detail": "Já existe um responsável com este CPF."` e uma entrada em `errors` para o campo (`cpf` na alteração do responsável, ou o caminho do item no corpo da criança, como `guardians[1].guardian.cpf`), com a mesma mensagem; nenhuma parte da resposta SHALL conter o CPF. Nada é gravado. Vários responsáveis SHALL poder ficar sem CPF. A unicidade é só entre responsáveis: um responsável e uma criança SHALL poder ter o mesmo CPF.

#### Scenario: CPF com máscara gravado só com dígitos
- **WHEN** um cliente altera a responsável Maria Souza com os dados válidos e `"cpf": "529.982.247-25"`
- **THEN** a resposta é `200 OK` com `"cpf": "52998224725"`

#### Scenario: CPF inválido
- **WHEN** um cliente altera a responsável Maria Souza com os dados válidos e `"cpf": "123.456.789-00"`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `cpf`

#### Scenario: Alteração para o CPF de outro responsável
- **WHEN** existem a responsável Maria Souza, sem CPF, e o responsável João Lima, com CPF `52998224725`, e um cliente altera Maria Souza com os dados válidos e `"cpf": "52998224725"`
- **THEN** a resposta é `409 Conflict` com `Content-Type: application/problem+json`, `"detail": "Já existe um responsável com este CPF."` e uma entrada em `errors` com `"field": "cpf"`; o corpo não contém `52998224725`, e Maria Souza continua sem CPF

#### Scenario: Manter o próprio CPF
- **WHEN** a responsável Maria Souza tem CPF `52998224725` e um cliente a altera com os dados válidos e `"cpf": "529.982.247-25"`
- **THEN** a resposta é `200 OK` com `"cpf": "52998224725"`

#### Scenario: Vários responsáveis sem CPF e CPF igual ao de uma criança
- **WHEN** existe a criança `Ana Souza` com CPF `11144477735`, e um cliente cadastra, no corpo de duas crianças, dois responsáveis novos sem CPF e um terceiro com `"cpf": "11144477735"`
- **THEN** as três gravações são aceitas (`201 Created`)

### Requirement: RG do responsável
Quando informado, o `rg` do responsável SHALL seguir a mesma normalização do RG da criança: aceito com qualquer pontuação, gravado e devolvido só com as letras e os dígitos, com as letras em maiúsculas (`12.345.678-x` vira `12345678X`). Um `rg` sem nenhuma letra nem dígito SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo. O RG do responsável SHALL NOT ser único: vários responsáveis SHALL poder ter o mesmo RG.

#### Scenario: RG normalizado
- **WHEN** um cliente altera a responsável Maria Souza com os dados válidos e `"rg": "12.345.678-x"`
- **THEN** a resposta é `200 OK` com `"rg": "12345678X"`

#### Scenario: RG repetido entre responsáveis
- **WHEN** o responsável João Lima tem RG `12345678X` e um cliente altera a responsável Maria Souza com os dados válidos e `"rg": "12345678-X"`
- **THEN** a resposta é `200 OK` com `"rg": "12345678X"`

#### Scenario: RG sem letras nem dígitos
- **WHEN** um cliente altera a responsável Maria Souza com os dados válidos e `"rg": "..."`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `rg`

### Requirement: Listar responsáveis com paginação e busca
O sistema SHALL listar os responsáveis em `GET /api/v1/guardians` com `200 OK`, paginados por `page` (começa em 0, padrão 0), `size` (padrão 20, máximo 100; valores maiores são tratados como 100) e `sort` (`name` ou `createdAt`, com direção `asc` ou `desc`; padrão `name,asc`). O corpo SHALL ter `content` e `page` (com `size`, `number`, `totalElements` e `totalPages`). Uma ordenação por propriedade fora das permitidas SHALL ser recusada com `400 Bad Request` e um `ProblemDetail`. A listagem SHALL incluir os responsáveis sem nenhuma criança vinculada.

Cada item de `content` SHALL ter só `id`, `name`, `phone` e `children`, sem CPF, RG, data de nascimento, estado civil nem dados de trabalho. `children` SHALL ter as crianças vinculadas ao responsável, em ordem alfabética de nome, cada uma só com `id`, `name` e `relationship` (o parentesco daquele vínculo); vazio quando não houver nenhuma.

O parâmetro opcional `q` SHALL filtrar os responsáveis:
- quando `q`, sem os espaços das pontas, tiver exatamente 11 dígitos e nada mais, pelo CPF igual a esse valor (correspondência exata; um trecho do CPF não encontra ninguém);
- nos demais casos, pelo nome que contém o texto informado, sem diferenciar maiúsculas e minúsculas nem letras com e sem acento, e tratando `%` e `_` como caracteres comuns.

Um `q` vazio ou só com espaços SHALL ser ignorado.

#### Scenario: Primeira página em ordem alfabética
- **WHEN** existem só os responsáveis `Carla`, `Ana`, `Bruno` e `Davi`, e um cliente envia `GET /api/v1/guardians?size=2`
- **THEN** a resposta é `200 OK`, `content` tem `Ana` e `Bruno`, nessa ordem, e `page` tem `"size": 2`, `"number": 0`, `"totalElements": 4` e `"totalPages": 2`

#### Scenario: Itens só com o resumo e as crianças
- **WHEN** existe só a responsável Maria Souza, com CPF, RG e data de nascimento, vinculada como `MOTHER` às crianças `Pedro Souza` e `Ana Souza`, e um cliente envia `GET /api/v1/guardians`
- **THEN** o item de `content` tem exatamente as propriedades `id`, `name`, `phone` e `children`, e `children` tem, nessa ordem, `{"id": "<id da Ana>", "name": "Ana Souza", "relationship": "MOTHER"}` e `{"id": "<id do Pedro>", "name": "Pedro Souza", "relationship": "MOTHER"}`

#### Scenario: Busca por nome sem acento e sem maiúsculas
- **WHEN** existem só os responsáveis `João Pedro`, `Joana Lima` e `Maria João`, e um cliente envia `GET /api/v1/guardians?q=joao`
- **THEN** a resposta é `200 OK`, `content` tem `João Pedro` e `Maria João`, nessa ordem, e `page.totalElements` é `2`

#### Scenario: Busca pelo CPF completo
- **WHEN** existem o responsável `João Lima`, com CPF `52998224725`, e a responsável `Maria Souza`, sem CPF, e um cliente envia `GET /api/v1/guardians?q=52998224725`
- **THEN** a resposta é `200 OK` e `content` tem só `João Lima`

#### Scenario: Trecho do CPF não encontra ninguém
- **WHEN** existe o responsável `João Lima`, com CPF `52998224725`, e um cliente envia `GET /api/v1/guardians?q=529982`
- **THEN** a resposta é `200 OK` e `content` é vazio

#### Scenario: Busca sem resultado e curinga
- **WHEN** existem só os responsáveis `Ana` e `Bruno`, e um cliente envia `GET /api/v1/guardians?q=zz` e depois `GET /api/v1/guardians?q=%25` (o texto `%`)
- **THEN** as duas respostas são `200 OK` com `content` vazio e `page.totalElements` igual a `0`

#### Scenario: Busca em branco é ignorada
- **WHEN** existem só os responsáveis `Ana` e `Bruno`, e um cliente envia `GET /api/v1/guardians?q=%20%20`
- **THEN** a resposta é `200 OK` e `content` tem `Ana` e `Bruno`

#### Scenario: Responsável sem crianças continua na lista
- **WHEN** a responsável Maria Souza era vinculada só à criança `Ana Souza`, um `COORDENADOR` exclui `Ana Souza`, e um cliente envia `GET /api/v1/guardians`
- **THEN** a resposta é `200 OK` e `content` tem Maria Souza com `"children": []`

#### Scenario: Tamanho de página acima do máximo
- **WHEN** existem só 3 responsáveis e um cliente envia `GET /api/v1/guardians?size=500`
- **THEN** a resposta é `200 OK`, `page.size` é `100` e `content` tem os 3 responsáveis

#### Scenario: Ordenação por propriedade não permitida
- **WHEN** um cliente envia `GET /api/v1/guardians?sort=cpf`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`

### Requirement: Consultar responsável por id
O sistema SHALL retornar em `GET /api/v1/guardians/{id}`, com `200 OK`, o responsável com todos os seus campos (`id`, `name`, `maritalStatus`, `birthDate`, `cpf`, `rg`, `phone`, `notWorking`, `workplace`, `workAddress`, `workPhone`, `createdAt` e `updatedAt`, com `null` nos opcionais não informados) e `children`, no mesmo formato e na mesma ordem da listagem. Se não existir responsável com esse id, SHALL responder `404 Not Found` com um `ProblemDetail`. Se o id não for um UUID válido, SHALL responder `400 Bad Request` com um `ProblemDetail`.

#### Scenario: Responsável de dois irmãos
- **WHEN** a responsável Maria Souza, com CPF `52998224725`, foi cadastrada como `MOTHER` de `Ana Souza` e depois vinculada como `STEPMOTHER` de `Pedro Lima`, e um cliente envia `GET /api/v1/guardians/{id}` com o id dela
- **THEN** a resposta é `200 OK` com `"name": "Maria Souza"`, `"cpf": "52998224725"`, os demais campos, `createdAt` e `updatedAt`, e `children` com `Ana Souza` (`MOTHER`) e `Pedro Lima` (`STEPMOTHER`), nessa ordem

#### Scenario: Responsável inexistente
- **WHEN** um cliente envia `GET /api/v1/guardians/0190f4a2-0000-7000-8000-000000000000` e não existe responsável com esse id
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`

#### Scenario: Id malformado
- **WHEN** um cliente envia `GET /api/v1/guardians/abc`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`

### Requirement: Alterar responsável
O sistema SHALL substituir todos os dados do responsável pelos recebidos em `PUT /api/v1/guardians/{id}`, respondendo `200 OK` com o responsável completo atualizado, no formato da consulta por id. Um campo opcional ausente ou `null` SHALL deixar o campo sem valor. A alteração SHALL NOT mudar os vínculos nem o parentesco (que são alterados na criança) e SHALL refletir os novos dados nas respostas de todas as crianças vinculadas. A alteração SHALL atualizar `updatedAt` e manter `createdAt`. Se não existir responsável com esse id, SHALL responder `404 Not Found`. Um corpo que não seja um JSON válido SHALL ser recusado com `400 Bad Request` e um `ProblemDetail`, sem gravar nada.

#### Scenario: Novo telefone aparece nas crianças vinculadas
- **WHEN** a responsável Maria Souza, criada em `2026-03-01T10:00:00Z`, é vinculada a `Ana Souza` e `Pedro Souza`, e um cliente envia `PUT /api/v1/guardians/{id}` com os dados válidos e `"phone": "11955554444"`, com o relógio da aplicação em `2026-03-02T09:00:00Z`
- **THEN** a resposta é `200 OK` com `"phone": "11955554444"`, `"createdAt": "2026-03-01T10:00:00Z"`, `"updatedAt": "2026-03-02T09:00:00Z"` e as duas crianças em `children`, e `GET /api/v1/children/{id}` de `Ana Souza` e de `Pedro Souza` devolve Maria Souza com `"phone": "11955554444"`

#### Scenario: Opcional omitido é apagado
- **WHEN** a responsável Maria Souza tem `"maritalStatus": "MARRIED"` e é alterada com os dados válidos, sem `maritalStatus`
- **THEN** a resposta é `200 OK` com `"maritalStatus": null`

#### Scenario: Alteração de responsável inexistente
- **WHEN** um cliente envia `PUT /api/v1/guardians/{id}` com os dados válidos e um id que não existe
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`, e nenhum responsável é criado

#### Scenario: JSON inválido
- **WHEN** um cliente envia `PUT /api/v1/guardians/{id}` da responsável Maria Souza com o corpo `{"name": "Maria"` e `Content-Type: application/json`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`, e a responsável continua com os dados anteriores

### Requirement: Autoria do responsável
O sistema SHALL registrar, em cada responsável e em cada vínculo com criança, o usuário autenticado que o criou e o que o alterou por último. Uma requisição recusada SHALL NOT alterar esses registros. A autoria SHALL NOT aparecer nas respostas da API.

#### Scenario: Autoria na criação e na alteração
- **WHEN** a usuária `ana.souza@educare.org`, com role `EDUCADOR`, cria uma criança com a responsável nova Maria Souza, e depois o usuário `bruno.lima@educare.org`, com role `EDUCADOR`, altera Maria Souza com dados válidos
- **THEN** Maria Souza fica registrada como criada por `ana.souza@educare.org` e alterada por último por `bruno.lima@educare.org`, e o vínculo com a criança como criado por `ana.souza@educare.org`

#### Scenario: Respostas sem autoria
- **WHEN** um cliente consulta a responsável Maria Souza por id e na listagem
- **THEN** nenhuma das duas respostas contém propriedades com o autor da criação ou da alteração

### Requirement: Acesso ao cadastro de responsáveis
Todos os endpoints de `/api/v1/guardians` SHALL exigir um usuário autenticado; sem token válido, SHALL responder `401 Unauthorized` com um `ProblemDetail`, sem executar a operação. Usuários com role `COORDENADOR` ou `EDUCADOR` SHALL poder listar, consultar e alterar responsáveis. Nos demais requirements desta capability, "um cliente" é um usuário autenticado com role `COORDENADOR` ou `EDUCADOR`.

#### Scenario: EDUCADOR lista, consulta e altera
- **WHEN** a usuária `ana.souza@educare.org`, com role `EDUCADOR`, lista os responsáveis, consulta Maria Souza e a altera com dados válidos, com o token dela
- **THEN** as respostas são `200 OK`, `200 OK` e `200 OK`

#### Scenario: Sem token
- **WHEN** um cliente envia `GET /api/v1/guardians` e `PUT /api/v1/guardians/{id}` com dados válidos, sem o header `Authorization`
- **THEN** as duas respostas são `401 Unauthorized` com `Content-Type: application/problem+json`, e o responsável não é alterado
