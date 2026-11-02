# child Specification

## Purpose

Manter o cadastro das crianças atendidas pela instituição: criar, consultar, listar com busca por nome, alterar e excluir crianças, com dados pessoais, endereço, documentos (CPF, RG e certidão de nascimento) e dados de saúde, tratados como dados pessoais sensíveis.

## Requirements

### Requirement: Dados da criança
Uma criança SHALL ter os campos obrigatórios `name`, `birthDate`, `cep`, `address`, `neighborhood`, `addressNumber`, `city` e `state`, e os campos opcionais `addressComplement`, `birthplace`, `race`, `gender`, `cpf`, `rg`, `nationality`, `maritalStatus`, `religion`, `church`, `healthReport`, `medication`, `medicalObservation` e `birthCertificateNumber` (matrícula da certidão de nascimento, com regras próprias no requirement "Certidão de nascimento"), além de `id`, `createdAt` e `updatedAt`, gerados pelo sistema. Nas requisições de criação e alteração, o sistema SHALL recusar com `400 Bad Request` e um `ProblemDetail` que lista cada campo inválido (propriedade `errors`, com `field` e `message`), sem gravar nada, quando:
- um campo obrigatório de texto estiver ausente, vazio ou só com espaços;
- `birthDate` estiver ausente;
- um campo de texto passar do tamanho máximo, contado depois de remover os espaços das pontas: `name` 150, `birthplace` 150, `address` 200, `neighborhood` 100, `addressNumber` 20, `addressComplement` 100, `city` 100, `rg` 20, `nationality` 100, `religion` 100, `church` 150, `medication` 1000, `healthReport` 2000 e `medicalObservation` 2000 caracteres.

Todos os campos de texto SHALL ser gravados sem espaços no início e no fim. Um campo opcional de texto vazio ou só com espaços SHALL ser gravado como ausente e devolvido como `null`. As respostas que devolvem a criança completa SHALL conter todos os campos, com `null` nos opcionais não informados.

#### Scenario: Criança criada só com os dados obrigatórios
- **WHEN** um cliente envia `POST /api/v1/children` com `{"name": "Maria Clara Souza", "birthDate": "2018-05-10", "cep": "01310-100", "address": "Avenida Paulista", "neighborhood": "Bela Vista", "addressNumber": "1000", "city": "São Paulo", "state": "SP"}` e o relógio da aplicação está em `2026-03-01T10:00:00Z`
- **THEN** a resposta é `201 Created`, o header `Location` é `/api/v1/children/{id}` com o `id` gerado, e o corpo tem esse `id`, `"name": "Maria Clara Souza"`, `"birthDate": "2018-05-10"`, `"cep": "01310100"`, `"address": "Avenida Paulista"`, `"neighborhood": "Bela Vista"`, `"addressNumber": "1000"`, `"city": "São Paulo"`, `"state": "SP"`, todos os campos opcionais com `null`, `"createdAt": "2026-03-01T10:00:00Z"` e `"updatedAt": "2026-03-01T10:00:00Z"`

#### Scenario: Criança criada com todos os dados
- **WHEN** um cliente cria uma criança com os dados obrigatórios válidos e `{"addressComplement": "Apto 12", "birthplace": "São Paulo - SP", "race": "BROWN", "gender": "FEMALE", "cpf": "529.982.247-25", "rg": "12.345.678-9", "nationality": "Brasileira", "maritalStatus": "SINGLE", "religion": "Católica", "church": "Paróquia São José", "healthReport": "Asma leve", "medication": "Salbutamol em crises", "medicalObservation": "Evitar poeira"}`
- **THEN** a resposta é `201 Created` e o corpo tem esses mesmos valores, exceto `"cpf": "52998224725"` e `"rg": "123456789"`, e `GET /api/v1/children/{id}` devolve os mesmos dados

#### Scenario: Textos com espaços nas pontas e opcional em branco
- **WHEN** um cliente cria uma criança com `"name": "  Maria Clara Souza  "`, `"religion": "   "` e os demais campos obrigatórios válidos
- **THEN** a resposta é `201 Created` com `"name": "Maria Clara Souza"` e `"religion": null`

#### Scenario: Campos obrigatórios ausentes
- **WHEN** um cliente envia `POST /api/v1/children` com `{}`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`, e `errors` contém uma entrada para cada um dos campos `name`, `birthDate`, `cep`, `address`, `neighborhood`, `addressNumber`, `city` e `state`, e nenhuma para os campos opcionais, inclusive `addressComplement`; nenhuma criança é gravada

#### Scenario: Criança sem complemento
- **WHEN** um cliente cria uma criança com os campos obrigatórios válidos e `"addressComplement": "   "`
- **THEN** a resposta é `201 Created` com `"addressComplement": null`

#### Scenario: Textos longos demais
- **WHEN** um cliente cria uma criança com um `name` de 151 caracteres, uma `medicalObservation` de 2001 caracteres e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para `name` e uma para `medicalObservation`; nenhuma criança é gravada

#### Scenario: Texto no limite do tamanho
- **WHEN** um cliente cria uma criança com um `name` de 150 caracteres e os demais campos obrigatórios válidos
- **THEN** a resposta é `201 Created`

### Requirement: Data de nascimento
O `birthDate` SHALL ser uma data no formato `AAAA-MM-DD` que não seja posterior à data atual no fuso `America/Sao_Paulo`; a própria data atual SHALL ser aceita. Uma data futura SHALL ser recusada com `400 Bad Request` e uma entrada em `errors` para o campo `birthDate`. Um valor que não seja uma data válida nesse formato SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo `birthDate`. Em ambos os casos, nada é gravado.

#### Scenario: Nascimento hoje
- **WHEN** o relógio da aplicação está em `2026-03-01T10:00:00Z` e um cliente cria uma criança com `"birthDate": "2026-03-01"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `201 Created` com `"birthDate": "2026-03-01"`

#### Scenario: Nascimento no futuro
- **WHEN** o relógio da aplicação está em `2026-03-01T10:00:00Z` e um cliente cria uma criança com `"birthDate": "2026-03-02"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `birthDate`; nenhuma criança é gravada

#### Scenario: Data de hoje no Brasil, já amanhã em UTC
- **WHEN** o relógio da aplicação está em `2026-03-02T01:00:00Z` (ainda `2026-03-01` em `America/Sao_Paulo`) e um cliente cria uma criança com `"birthDate": "2026-03-02"`
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `birthDate`

#### Scenario: Data inexistente
- **WHEN** um cliente cria uma criança com `"birthDate": "2018-02-30"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`, e `errors` contém uma entrada para o campo `birthDate`; nenhuma criança é gravada

#### Scenario: Data em outro formato
- **WHEN** um cliente cria uma criança com `"birthDate": "10/05/2018"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`, e `errors` contém uma entrada para o campo `birthDate`; nenhuma criança é gravada

### Requirement: CEP
O `cep` SHALL ter 8 dígitos, aceito com ou sem o hífen depois do quinto dígito (`01310-100` ou `01310100`), e SHALL ser gravado e devolvido só com os dígitos. Qualquer outro formato SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo `cep`, sem gravar nada. O sistema não verifica se o CEP existe.

#### Scenario: CEP sem hífen
- **WHEN** um cliente cria uma criança com `"cep": "01310100"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `201 Created` com `"cep": "01310100"`

#### Scenario: CEP com dígitos a menos
- **WHEN** um cliente cria uma criança com `"cep": "01310-10"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `cep`; nenhuma criança é gravada

#### Scenario: CEP com letras
- **WHEN** um cliente cria uma criança com `"cep": "0131A-100"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `cep`

### Requirement: Estado (UF)
O `state` SHALL ser a sigla de uma das 27 unidades federativas do Brasil (`AC`, `AL`, `AP`, `AM`, `BA`, `CE`, `DF`, `ES`, `GO`, `MA`, `MT`, `MS`, `MG`, `PA`, `PB`, `PR`, `PE`, `PI`, `RJ`, `RN`, `RS`, `RO`, `RR`, `SC`, `SP`, `SE`, `TO`), aceita em maiúsculas ou minúsculas, e SHALL ser gravado e devolvido em maiúsculas. Qualquer outro valor SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo `state`, sem gravar nada. O sistema não verifica se a cidade pertence à UF nem se ambas correspondem ao CEP.

#### Scenario: UF em minúsculas é gravada em maiúsculas
- **WHEN** um cliente cria uma criança com `"state": "sp"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `201 Created` com `"state": "SP"`

#### Scenario: Sigla inexistente
- **WHEN** um cliente cria uma criança com `"state": "XX"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `state`; nenhuma criança é gravada

#### Scenario: Estado por extenso
- **WHEN** um cliente cria uma criança com `"state": "São Paulo"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `state`; nenhuma criança é gravada

### Requirement: Listas fixas de raça, gênero e estado civil
Quando informados, os campos opcionais `race`, `gender` e `maritalStatus` SHALL aceitar só os valores abaixo, exatamente em maiúsculas:
- `race` (categorias do IBGE: branca, preta, parda, amarela e indígena, nessa ordem): `WHITE`, `BLACK`, `BROWN`, `YELLOW`, `INDIGENOUS`;
- `gender`: `MALE`, `FEMALE`, `OTHER`;
- `maritalStatus`: `SINGLE`, `MARRIED`, `STABLE_UNION`, `DIVORCED`, `WIDOWED`.

Qualquer outro valor SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo, sem gravar nada. Ausente ou `null`, o campo fica sem valor.

#### Scenario: Valores válidos
- **WHEN** um cliente cria uma criança com `"race": "INDIGENOUS"`, `"gender": "OTHER"`, `"maritalStatus": "STABLE_UNION"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `201 Created` com esses três valores

#### Scenario: Valores fora das listas
- **WHEN** um cliente cria uma criança com `"race": "brown"`, `"gender": "F"`, `"maritalStatus": "DATING"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para cada um dos campos `race`, `gender` e `maritalStatus`; nenhuma criança é gravada

### Requirement: CPF válido e único
Quando informado, o `cpf` SHALL ter 11 dígitos, aceito sem máscara (`52998224725`) ou com a máscara completa (`529.982.247-25`), com os dois dígitos verificadores corretos e sem ser formado por um único dígito repetido (como `111.111.111-11`); caso contrário, SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo `cpf`, sem gravar nada. O `cpf` SHALL ser gravado e devolvido só com os dígitos. Duas crianças SHALL NOT ter o mesmo CPF: a criação ou alteração que causaria isso SHALL ser recusada com `409 Conflict` e um `ProblemDetail` que não contém o CPF, sem gravar nada. Várias crianças SHALL poder ficar sem CPF.

#### Scenario: CPF com máscara é gravado só com dígitos
- **WHEN** um cliente cria uma criança com `"cpf": "529.982.247-25"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `201 Created` com `"cpf": "52998224725"`

#### Scenario: Dígito verificador errado
- **WHEN** um cliente cria uma criança com `"cpf": "529.982.247-24"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `cpf`; nenhuma criança é gravada

#### Scenario: Dígitos repetidos
- **WHEN** um cliente cria uma criança com `"cpf": "11111111111"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `cpf`

#### Scenario: Criação com CPF já usado
- **WHEN** já existe uma criança com CPF `52998224725` e um cliente cria outra com `"cpf": "529.982.247-25"`
- **THEN** a resposta é `409 Conflict` com `Content-Type: application/problem+json`, o corpo não contém `52998224725` nem `529.982.247-25`, e continua existindo só uma criança com esse CPF

#### Scenario: Alteração para CPF de outra criança
- **WHEN** existem uma criança com CPF `52998224725` e outra com CPF `11144477735`, e um cliente altera a segunda com `"cpf": "52998224725"`
- **THEN** a resposta é `409 Conflict`, e a segunda criança continua com CPF `11144477735`

#### Scenario: Várias crianças sem CPF
- **WHEN** já existe uma criança sem CPF e um cliente cria outra sem CPF
- **THEN** a resposta é `201 Created`

### Requirement: RG único
Quando informado, o `rg` SHALL ser aceito com qualquer pontuação e SHALL ser gravado e devolvido só com as letras e os dígitos, com as letras em maiúsculas (`12.345.678-x` vira `12345678X`). Um `rg` que não tenha nenhuma letra nem dígito SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo `rg`, sem gravar nada. Duas crianças SHALL NOT ter o mesmo RG, comparado depois dessa normalização: a criação ou alteração que causaria isso SHALL ser recusada com `409 Conflict` e um `ProblemDetail` que não contém o RG, sem gravar nada. Várias crianças SHALL poder ficar sem RG.

#### Scenario: RG gravado só com letras e dígitos
- **WHEN** um cliente cria uma criança com `"rg": "12.345.678-x"` e os demais campos obrigatórios válidos
- **THEN** a resposta é `201 Created` com `"rg": "12345678X"`

#### Scenario: Criação com RG já usado em outra pontuação
- **WHEN** já existe uma criança com RG `12345678X` e um cliente cria outra com `"rg": "12345678-X"`
- **THEN** a resposta é `409 Conflict` com `Content-Type: application/problem+json`, o corpo não contém `12345678X` nem `12345678-X`, e continua existindo só uma criança com esse RG

#### Scenario: Alteração para RG de outra criança
- **WHEN** existem uma criança com RG `12345678X` e outra com RG `987654321`, e um cliente altera a segunda com `"rg": "12.345.678-X"`
- **THEN** a resposta é `409 Conflict`, e a segunda criança continua com RG `987654321`

#### Scenario: Várias crianças sem RG
- **WHEN** já existe uma criança sem RG e um cliente cria outra sem RG
- **THEN** a resposta é `201 Created`

#### Scenario: RG sem letras nem dígitos
- **WHEN** um cliente cria uma criança com `"rg": "..."` e os demais campos obrigatórios válidos
- **THEN** a resposta é `400 Bad Request` e `errors` contém uma entrada para o campo `rg`; nenhuma criança é gravada

### Requirement: Certidão de nascimento
O sistema SHALL guardar, como dado opcional da criança, a matrícula da certidão de nascimento (modelo de certidão com matrícula) no campo `birthCertificateNumber`. Quando informada, a matrícula SHALL ser aceita com pontos, espaços e hífens entre os dígitos e SHALL ser gravada e devolvida só com os dígitos. Depois de tirar esses separadores, ela SHALL ter exatamente 32 dígitos; qualquer outro valor (tamanho diferente, letras ou outros caracteres) SHALL ser recusado com `400 Bad Request` e uma entrada em `errors` para o campo `birthCertificateNumber`, sem gravar nada. O sistema não confere a veracidade da matrícula (dígitos verificadores, tipo de livro, ano do registro). Vazia ou só com espaços, a matrícula fica sem valor, como os demais opcionais.

Duas crianças SHALL NOT ter a mesma matrícula: a criação ou alteração que causaria isso SHALL ser recusada com `409 Conflict` e um `ProblemDetail` que não contém a matrícula, sem gravar nada. Várias crianças SHALL poder ficar sem matrícula. A matrícula SHALL aparecer nas respostas que devolvem a criança completa (com `null` quando não informada) e SHALL NOT aparecer nos itens da listagem.

Nos cenários abaixo, "a matrícula M" é `10453901552013100012021000012321` (32 dígitos), e os demais campos obrigatórios da criança são válidos.

#### Scenario: Matrícula com separadores é gravada só com dígitos
- **WHEN** um cliente cria uma criança com `"birthCertificateNumber": "104539.01.55.2013.1.00012.021.0000123-21"`
- **THEN** a resposta é `201 Created` com `"birthCertificateNumber": "10453901552013100012021000012321"`, e `GET /api/v1/children/{id}` devolve o mesmo valor

#### Scenario: Matrícula com espaços entre os grupos
- **WHEN** um cliente cria uma criança com `"birthCertificateNumber": "104539 01 55 2013 1 00012 021 0000123 21"`
- **THEN** a resposta é `201 Created` com `"birthCertificateNumber": "10453901552013100012021000012321"`

#### Scenario: Matrícula com dígitos a menos ou a mais
- **WHEN** um cliente cria uma criança com `"birthCertificateNumber": "1045390155201310001202100001232"` (31 dígitos), e outro cliente cria uma com `"birthCertificateNumber": "104539015520131000120210000123210"` (33 dígitos)
- **THEN** as duas respostas são `400 Bad Request` com uma entrada em `errors` para o campo `birthCertificateNumber`; nenhuma criança é gravada

#### Scenario: Matrícula com letras
- **WHEN** um cliente cria uma criança com `"birthCertificateNumber": "1045390155201310001202100001232X"`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para o campo `birthCertificateNumber`; nenhuma criança é gravada

#### Scenario: Matrícula em branco
- **WHEN** um cliente cria uma criança com `"birthCertificateNumber": "   "`
- **THEN** a resposta é `201 Created` com `"birthCertificateNumber": null`

#### Scenario: Matrícula de outra criança
- **WHEN** já existe uma criança com a matrícula M e um cliente cria outra, ou altera uma terceira criança, com a matrícula M escrita com espaços
- **THEN** as respostas são `409 Conflict` com `Content-Type: application/problem+json`, o corpo não contém nenhum trecho de 5 ou mais dígitos seguidos de M, e continua existindo só uma criança com a matrícula M

#### Scenario: Várias crianças sem matrícula
- **WHEN** já existe uma criança sem matrícula e um cliente cria outra sem matrícula
- **THEN** a resposta é `201 Created` com `"birthCertificateNumber": null`

#### Scenario: Alteração mantendo a própria matrícula
- **WHEN** a criança com a matrícula M é alterada com os dados válidos e a mesma matrícula M, escrita com pontos
- **THEN** a resposta é `200 OK` com `"birthCertificateNumber": "10453901552013100012021000012321"`

#### Scenario: Alteração sem matrícula apaga a matrícula
- **WHEN** a criança com a matrícula M é alterada com os dados obrigatórios e sem `birthCertificateNumber`
- **THEN** a resposta é `200 OK` com `"birthCertificateNumber": null`, e outra criança pode ser criada em seguida com a matrícula M (`201 Created`)

### Requirement: Consultar criança por id
O sistema SHALL retornar em `GET /api/v1/children/{id}` a criança com esse id, com `200 OK` e todos os seus campos. Se não existir criança com esse id, SHALL responder `404 Not Found` com um `ProblemDetail`. Se o id não for um UUID válido, SHALL responder `400 Bad Request` com um `ProblemDetail`.

#### Scenario: Criança existente
- **WHEN** existe a criança `Maria Clara Souza`, criada com CPF `52998224725`, e um cliente envia `GET /api/v1/children/{id}` com o id dela
- **THEN** a resposta é `200 OK` com o `id`, `"name": "Maria Clara Souza"`, `"cpf": "52998224725"` e os demais campos, `createdAt` e `updatedAt` dessa criança

#### Scenario: Criança inexistente
- **WHEN** um cliente envia `GET /api/v1/children/0190f4a2-0000-7000-8000-000000000000` e não existe criança com esse id
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`

#### Scenario: Id malformado
- **WHEN** um cliente envia `GET /api/v1/children/abc`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`

### Requirement: Listar crianças com paginação e busca por nome
O sistema SHALL listar as crianças em `GET /api/v1/children` com `200 OK`, paginadas pelos parâmetros `page` (começa em 0, padrão 0), `size` (padrão 20, máximo 100; valores maiores são tratados como 100) e `sort` (`name`, `birthDate` ou `createdAt`, com direção `asc` ou `desc`; padrão `name,asc`). O corpo SHALL ter `content` e `page` (com `size`, `number`, `totalElements` e `totalPages`). Cada item de `content` SHALL ter só `id`, `name`, `birthDate`, `classroom` (com o `id` e o `name` da turma atual), `createdAt` e `updatedAt`, sem os demais dados da criança. Uma ordenação por propriedade fora das permitidas SHALL ser recusada com `400 Bad Request` e um `ProblemDetail`.

O parâmetro opcional `name` SHALL filtrar as crianças cujo nome contém o texto informado, sem diferenciar maiúsculas e minúsculas nem letras com e sem acento, e tratando `%` e `_` como caracteres comuns. Um `name` vazio ou só com espaços SHALL ser ignorado, e os espaços nas pontas do texto buscado SHALL ser removidos.

O parâmetro opcional `classroomId` SHALL filtrar as crianças cuja turma atual é a informada; crianças que já saíram dessa turma SHALL NOT aparecer. Um `classroomId` que não seja um UUID válido SHALL ser recusado com `400 Bad Request` e um `ProblemDetail`; um `classroomId` que não seja de nenhuma turma SHALL resultar numa página vazia. Os filtros `name` e `classroomId` SHALL poder ser usados juntos, e a criança precisa atender aos dois.

#### Scenario: Primeira página ordenada por nome
- **WHEN** existem só as crianças `Carla`, `Ana`, `Bruno` e `Davi`, e um cliente envia `GET /api/v1/children?size=2`
- **THEN** a resposta é `200 OK`, `content` tem `Ana` e `Bruno`, nessa ordem, e `page` tem `"size": 2`, `"number": 0`, `"totalElements": 4` e `"totalPages": 2`

#### Scenario: Itens da listagem sem dados sensíveis
- **WHEN** existe só a criança `Maria Clara Souza`, na `Turma 1`, criada com CPF, RG, matrícula da certidão de nascimento, endereço e dados de saúde, e um cliente envia `GET /api/v1/children`
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

### Requirement: Alterar criança
O sistema SHALL substituir todos os dados da criança pelos recebidos em `PUT /api/v1/children/{id}`, respondendo `200 OK` com a criança completa atualizada. O corpo segue as mesmas regras e validações da criação; um campo opcional ausente ou `null` SHALL deixar o campo sem valor. Se não existir criança com esse id, SHALL responder `404 Not Found`. A alteração SHALL atualizar `updatedAt` e manter `createdAt`.

#### Scenario: Alteração com dados válidos
- **WHEN** a criança `Maria Clara Souza`, criada em `2026-03-01T10:00:00Z`, é alterada com `PUT /api/v1/children/{id}` e os dados obrigatórios com `"neighborhood": "Consolação"` e `"addressNumber": "250"`, com o relógio da aplicação em `2026-03-02T09:00:00Z`
- **THEN** a resposta é `200 OK` com `"neighborhood": "Consolação"`, `"addressNumber": "250"`, `"createdAt": "2026-03-01T10:00:00Z"` e `"updatedAt": "2026-03-02T09:00:00Z"`

#### Scenario: Opcional omitido é apagado
- **WHEN** a criança `Maria Clara Souza`, com `"medication": "Salbutamol em crises"`, é alterada com os dados obrigatórios e sem `medication`
- **THEN** a resposta é `200 OK` com `"medication": null`, e `GET /api/v1/children/{id}` também devolve `"medication": null`

#### Scenario: Manter o próprio CPF
- **WHEN** a criança com CPF `52998224725` é alterada com `"cpf": "529.982.247-25"` e os demais dados válidos
- **THEN** a resposta é `200 OK` com `"cpf": "52998224725"`

#### Scenario: Alteração com dados inválidos
- **WHEN** a criança `Maria Clara Souza` é alterada com `"name": ""`, `"cep": "123"` e os demais dados válidos
- **THEN** a resposta é `400 Bad Request` com entradas em `errors` para `name` e `cep`, e a criança continua com nome e CEP anteriores

#### Scenario: Alteração de criança inexistente
- **WHEN** um cliente envia `PUT /api/v1/children/{id}` com dados válidos e um id que não existe
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`, e nenhuma criança é criada

### Requirement: Excluir criança
O sistema SHALL excluir definitivamente a criança em `DELETE /api/v1/children/{id}`, respondendo `204 No Content`; depois disso, a criança não aparece na consulta nem na listagem, e o CPF, o RG e a matrícula da certidão de nascimento dela ficam livres para outra criança. Se não existir criança com esse id, SHALL responder `404 Not Found`.

#### Scenario: Criança excluída
- **WHEN** existe a criança `Maria Clara Souza` e um `COORDENADOR` envia `DELETE /api/v1/children/{id}` com o id dela
- **THEN** a resposta é `204 No Content`, `GET /api/v1/children/{id}` passa a responder `404 Not Found` e a listagem não contém mais essa criança

#### Scenario: CPF e matrícula liberados após exclusão
- **WHEN** a criança com CPF `52998224725` e a matrícula M (requirement "Certidão de nascimento") é excluída, e depois um cliente cria outra com `"cpf": "52998224725"` e a matrícula M
- **THEN** a criação responde `201 Created`

#### Scenario: Exclusão de criança inexistente
- **WHEN** um `COORDENADOR` envia `DELETE /api/v1/children/{id}` com um id que não existe
- **THEN** a resposta é `404 Not Found` com `Content-Type: application/problem+json`

### Requirement: Corpo de requisição ilegível
O sistema SHALL recusar com `400 Bad Request` e um `ProblemDetail`, sem gravar nada, as requisições de criação e alteração de crianças cujo corpo não seja um JSON válido.

#### Scenario: JSON inválido na criação
- **WHEN** um cliente envia `POST /api/v1/children` com o corpo `{"name": "Maria"` (JSON incompleto) e `Content-Type: application/json`
- **THEN** a resposta é `400 Bad Request` com `Content-Type: application/problem+json`, e nenhuma criança é gravada

#### Scenario: JSON válido é aceito
- **WHEN** um cliente envia `POST /api/v1/children` com um JSON bem formado e os campos obrigatórios válidos
- **THEN** a resposta é `201 Created`

### Requirement: Autoria da criança
O sistema SHALL registrar, em cada criança, o usuário autenticado que a criou e o usuário autenticado que a alterou por último. Na criação, os dois SHALL ser o usuário que fez a requisição; na alteração, só o autor da última alteração SHALL mudar. Uma requisição recusada (`400`, `401`, `403`, `404` ou `409`) SHALL NOT alterar esses registros. A autoria SHALL NOT aparecer nas respostas da API desta capability.

#### Scenario: Autoria na criação e na alteração
- **WHEN** a usuária `ana.souza@educare.org`, com role `EDUCADOR`, cria a criança `Maria Clara Souza`, e depois o usuário `bruno.lima@educare.org`, com role `EDUCADOR`, a altera com dados válidos
- **THEN** a criança fica registrada como criada por `ana.souza@educare.org` e alterada por último por `bruno.lima@educare.org`

#### Scenario: Alteração recusada não muda a autoria
- **WHEN** a criança `Maria Clara Souza` foi criada por `ana.souza@educare.org`, e `bruno.lima@educare.org` tenta alterá-la com `"cep": "123"`
- **THEN** a resposta é `400 Bad Request`, e a criança continua registrada como alterada por último por `ana.souza@educare.org`

#### Scenario: Respostas sem autoria
- **WHEN** um cliente cria uma criança e depois a consulta por id e na listagem
- **THEN** nenhuma das três respostas contém propriedades com o autor da criação ou da alteração

### Requirement: Acesso ao cadastro de crianças
Todos os endpoints de `/api/v1/children` SHALL exigir um usuário autenticado; sem token válido, SHALL responder `401 Unauthorized`. Usuários com role `COORDENADOR` ou `EDUCADOR` SHALL poder criar, consultar, listar e alterar crianças. Só usuários com role `COORDENADOR` SHALL poder excluir; um `EDUCADOR` que tenta excluir SHALL receber `403 Forbidden`. As recusas por `401` e `403` SHALL ter um `ProblemDetail` e não executar a operação. Nos demais requirements desta capability, "um cliente" é um usuário autenticado com role `COORDENADOR` ou `EDUCADOR`.

#### Scenario: EDUCADOR cria e altera criança
- **WHEN** a usuária `ana.souza@educare.org`, com role `EDUCADOR`, cria uma criança com dados válidos e depois a altera, com o token dela
- **THEN** a criação responde `201 Created` e a alteração responde `200 OK`

#### Scenario: EDUCADOR não exclui criança
- **WHEN** existe a criança `Maria Clara Souza` e a usuária `ana.souza@educare.org`, com role `EDUCADOR`, envia `DELETE /api/v1/children/{id}` com o id dela e o token dela
- **THEN** a resposta é `403 Forbidden` com `Content-Type: application/problem+json`, e a criança continua existindo

#### Scenario: Listagem sem token
- **WHEN** um cliente envia `GET /api/v1/children` sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized` com `Content-Type: application/problem+json`

#### Scenario: Criação sem token
- **WHEN** um cliente envia `POST /api/v1/children` com dados válidos e sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized`, e nenhuma criança é gravada

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
