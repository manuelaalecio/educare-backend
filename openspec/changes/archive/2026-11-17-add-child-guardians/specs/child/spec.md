# Spec Delta

## ADDED Requirements

### Requirement: Responsáveis obrigatórios da criança
As requisições de criação (`POST /api/v1/children`) e de alteração (`PUT /api/v1/children/{id}`) SHALL exigir o campo `guardians`, uma lista de 1 a 5 itens. Cada item SHALL ter `relationship` (o parentesco do responsável com esta criança) e exatamente um entre:
- `guardianId`: o id de um responsável já cadastrado, que é vinculado sem mudar os dados dele;
- `guardian`: os dados de um responsável novo, com as regras da capability `guardian` (requirements "Dados do responsável", "Telefones do responsável", "Data de nascimento do responsável", "Estado civil do responsável", "CPF do responsável válido e único" e "RG do responsável"), que é cadastrado e vinculado.

`relationship` SHALL aceitar só, exatamente em maiúsculas: `MOTHER`, `FATHER`, `GRANDMOTHER`, `GRANDFATHER`, `AUNT`, `UNCLE`, `SISTER`, `BROTHER`, `STEPMOTHER`, `STEPFATHER`, `LEGAL_GUARDIAN`, `OTHER`.

O sistema SHALL recusar com `400 Bad Request` e um `ProblemDetail` com as entradas em `errors` abaixo, sem gravar nada (nem a criança, nem responsáveis novos, nem vínculos, nem alterações):
- `guardians` ausente, `null` ou vazio: entrada para `guardians` com a mensagem "Informe pelo menos um responsável.";
- mais de 5 itens: entrada para `guardians` com a mensagem "Informe no máximo 5 responsáveis.";
- o mesmo `guardianId` em mais de um item: entrada para `guardians` com a mensagem "O mesmo responsável não pode ser informado mais de uma vez.";
- item `null`, ou item sem `guardianId` e sem `guardian`, ou com os dois: entrada para o item (`guardians[i]`);
- `relationship` ausente ou fora da lista: entrada para `guardians[i].relationship`;
- `guardianId` que não seja um UUID válido: entrada para `guardians[i].guardianId`;
- `guardianId` que não seja de nenhum responsável: entrada para `guardians[i].guardianId` com a mensagem "Responsável não encontrado";
- dado inválido no responsável novo: entrada para o campo com o caminho do item (por exemplo, `guardians[0].guardian.phone`).

As entradas que vêm da validação do corpo SHALL aparecer juntas com as dos demais campos da criança. O CPF de um responsável novo igual ao de um responsável já cadastrado, ou igual ao de outro responsável novo do mesmo corpo, SHALL ser recusado com `409 Conflict`, conforme a capability `guardian`, com a entrada em `errors` no caminho do item (`guardians[i].guardian.cpf`). Nos demais requirements desta capability, "os dados obrigatórios válidos" de uma criança incluem `guardians` com ao menos um item válido.

Nos cenários abaixo, "os dados da Maria" são `{"name": "Maria Souza", "phone": "11912345678", "notWorking": false, "workplace": "Padaria Central", "workAddress": "Rua das Flores, 100", "workPhone": "1133334444"}`, e os demais campos da criança são válidos.

#### Scenario: Criança criada com responsável novo
- **WHEN** um cliente envia `POST /api/v1/children` com `"guardians": [{"guardian": <dados da Maria>, "relationship": "MOTHER"}]`
- **THEN** a resposta é `201 Created` com `guardians` contendo um item com o `id` gerado para Maria Souza, `"name": "Maria Souza"`, `"relationship": "MOTHER"`, `"phone": "11912345678"`, `"notWorking": false`, `"workplace": "Padaria Central"` e `"workPhone": "1133334444"`, e `GET /api/v1/guardians` passa a listar Maria Souza com essa criança em `children`

#### Scenario: Irmão vinculado à responsável já cadastrada
- **WHEN** a responsável Maria Souza foi cadastrada como `MOTHER` de `Ana Souza`, e um cliente cria a criança `Pedro Souza` com `"guardians": [{"guardianId": "<id da Maria>", "relationship": "MOTHER"}]`
- **THEN** a resposta é `201 Created` com Maria Souza (mesmo `id`) em `guardians`, continua existindo um único responsável chamado Maria Souza, e `GET /api/v1/guardians/{id da Maria}` devolve `Ana Souza` e `Pedro Souza` em `children`

#### Scenario: Criança sem responsável
- **WHEN** um cliente envia `POST /api/v1/children` sem `guardians`, e outro cliente envia com `"guardians": []`
- **THEN** as duas respostas são `400 Bad Request` com `Content-Type: application/problem+json` e uma entrada em `errors` para `guardians` com a mensagem "Informe pelo menos um responsável."; nenhuma criança é gravada

#### Scenario: Sem responsável e com outro campo inválido
- **WHEN** um cliente envia `POST /api/v1/children` com `"cep": "123"`, os demais dados válidos e sem `guardians`
- **THEN** a resposta é `400 Bad Request`, e `errors` contém uma entrada para `guardians` e uma para `cep`

#### Scenario: Mais de cinco responsáveis
- **WHEN** um cliente cria uma criança com 6 itens válidos em `guardians`, e depois outra com 5 itens válidos
- **THEN** a primeira resposta é `400 Bad Request` com uma entrada em `errors` para `guardians` com a mensagem "Informe no máximo 5 responsáveis.", e a segunda é `201 Created` com 5 itens em `guardians`

#### Scenario: Mesmo responsável duas vezes
- **WHEN** existe a responsável Maria Souza e um cliente cria uma criança com `"guardians": [{"guardianId": "<id da Maria>", "relationship": "MOTHER"}, {"guardianId": "<id da Maria>", "relationship": "OTHER"}]`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `guardians` com a mensagem "O mesmo responsável não pode ser informado mais de uma vez."; nenhuma criança é gravada

#### Scenario: Item sem responsável ou com os dois
- **WHEN** existe a responsável Maria Souza e um cliente cria uma criança com `"guardians": [{"relationship": "MOTHER"}, {"guardianId": "<id da Maria>", "guardian": <dados da Maria>, "relationship": "FATHER"}]`
- **THEN** a resposta é `400 Bad Request`, e `errors` contém uma entrada para `guardians[0]` e uma para `guardians[1]`; nenhuma criança nem responsável é gravado

#### Scenario: Parentesco ausente e fora da lista
- **WHEN** um cliente cria uma criança com `"guardians": [{"guardian": <dados da Maria>}, {"guardian": <dados da Maria com "name": "João Souza">, "relationship": "COUSIN"}]`
- **THEN** a resposta é `400 Bad Request`, e `errors` contém uma entrada para `guardians[0].relationship` e uma para `guardians[1].relationship`; nenhuma criança nem responsável é gravado

#### Scenario: Responsável cadastrado inexistente
- **WHEN** um cliente cria uma criança com `"guardians": [{"guardianId": "0190f4a2-0000-7000-8000-000000000000", "relationship": "MOTHER"}]`, e não existe responsável com esse id
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `guardians[0].guardianId` com a mensagem "Responsável não encontrado"; nenhuma criança é gravada

#### Scenario: guardianId malformado
- **WHEN** um cliente cria uma criança com `"guardians": [{"guardianId": "maria", "relationship": "MOTHER"}]`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `guardians[0].guardianId`

#### Scenario: Responsável novo com dados inválidos
- **WHEN** um cliente cria uma criança com `"guardians": [{"guardianId": "<id de um responsável existente>", "relationship": "FATHER"}, {"guardian": {"name": "", "phone": "111234", "notWorking": true, "cpf": "123.456.789-00"}, "relationship": "MOTHER"}]`
- **THEN** a resposta é `400 Bad Request`, e `errors` contém uma entrada para cada um dos campos `guardians[1].guardian.name`, `guardians[1].guardian.phone` e `guardians[1].guardian.cpf`; nenhuma criança nem responsável é gravado

#### Scenario: Responsável novo nascido no futuro
- **WHEN** o relógio da aplicação está em `2026-03-01T10:00:00Z` e um cliente cria uma criança com `"guardians": [{"guardian": <dados da Maria com "birthDate": "2026-03-02">, "relationship": "MOTHER"}]`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `guardians[0].guardian.birthDate`; nenhuma criança nem responsável é gravado

#### Scenario: Responsável novo com CPF já cadastrado
- **WHEN** já existe o responsável João Lima com CPF `52998224725`, e um cliente cria uma criança com `"guardians": [{"guardian": <dados da Maria>, "relationship": "MOTHER"}, {"guardian": <dados da Maria com "name": "Joana Lima" e "cpf": "529.982.247-25">, "relationship": "AUNT"}]`
- **THEN** a resposta é `409 Conflict` com `"detail": "Já existe um responsável com este CPF."` e uma entrada em `errors` com `"field": "guardians[1].guardian.cpf"`; o corpo não contém `52998224725` nem `529.982.247-25`, e nem a criança, nem Maria Souza, nem Joana Lima são gravadas

#### Scenario: Dois responsáveis novos com o mesmo CPF
- **WHEN** um cliente cria uma criança com dois responsáveis novos, ambos com `"cpf": "52998224725"`, e não existe responsável com esse CPF
- **THEN** a resposta é `409 Conflict` com uma entrada em `errors` para `guardians[1].guardian.cpf`; nenhuma criança nem responsável é gravado

#### Scenario: Responsável novo com CPF igual ao da própria criança
- **WHEN** um cliente cria uma criança com `"cpf": "52998224725"` e um responsável novo com `"cpf": "52998224725"`
- **THEN** a resposta é `201 Created`

### Requirement: Substituição dos responsáveis na alteração
Na alteração da criança, a lista `guardians` recebida SHALL substituir os vínculos atuais:
- responsável que já estava vinculado e continua na lista: o vínculo SHALL continuar, com o `relationship` recebido;
- responsável vinculado que não está na lista: o vínculo SHALL ser apagado, e o responsável SHALL continuar cadastrado;
- responsável que não estava vinculado (já cadastrado ou novo): SHALL ser vinculado.

Os dados de um responsável já cadastrado SHALL NOT mudar pela alteração da criança; eles só mudam pela capability `guardian`. Uma alteração recusada (`400`, `404` ou `409`) SHALL NOT mudar os vínculos nem gravar responsáveis novos. Uma criança gravada sem responsáveis (antes de os responsáveis serem obrigatórios) SHALL continuar sendo devolvida normalmente, mas a alteração dela SHALL exigir `guardians` como qualquer outra.

#### Scenario: Acrescentar o pai
- **WHEN** a criança `Ana Souza` tem a mãe Maria Souza vinculada e um cliente a altera com `"guardians": [{"guardianId": "<id da Maria>", "relationship": "MOTHER"}, {"guardian": <dados de João Souza>, "relationship": "FATHER"}]`
- **THEN** a resposta é `200 OK` com Maria Souza (`MOTHER`) e João Souza (`FATHER`) em `guardians`

#### Scenario: Remover a mãe
- **WHEN** a criança `Ana Souza` tem Maria Souza (`MOTHER`) e João Souza (`FATHER`) vinculados, e um cliente a altera com `"guardians": [{"guardianId": "<id do João>", "relationship": "FATHER"}]`
- **THEN** a resposta é `200 OK` com só João Souza em `guardians`, e `GET /api/v1/guardians/{id da Maria}` responde `200 OK` sem `Ana Souza` em `children`

#### Scenario: Trocar o parentesco
- **WHEN** a criança `Pedro Lima` tem Maria Souza vinculada como `OTHER` e um cliente a altera com `"guardians": [{"guardianId": "<id da Maria>", "relationship": "STEPMOTHER"}]`
- **THEN** a resposta é `200 OK` com Maria Souza como `STEPMOTHER` em `guardians`, e o parentesco dela com as outras crianças não muda

#### Scenario: Remover todos os responsáveis
- **WHEN** a criança `Ana Souza` tem Maria Souza vinculada e um cliente a altera com `"guardians": []`
- **THEN** a resposta é `400 Bad Request` com uma entrada em `errors` para `guardians` com a mensagem "Informe pelo menos um responsável.", e Maria Souza continua vinculada

#### Scenario: Criança antiga sem responsável
- **WHEN** a criança `Bia Lima` foi gravada antes de os responsáveis serem obrigatórios, sem nenhum, e um cliente envia `GET /api/v1/children/{id}` e depois `PUT /api/v1/children/{id}` com os demais dados válidos e sem `guardians`
- **THEN** o `GET` responde `200 OK` com `"guardians": []`, e o `PUT` responde `400 Bad Request` com uma entrada em `errors` para `guardians`; a criança não muda

#### Scenario: Alteração recusada não muda os vínculos
- **WHEN** a criança `Ana Souza` tem Maria Souza vinculada, outra criança tem o CPF `11144477735`, e um cliente altera `Ana Souza` com `"cpf": "11144477735"` e `"guardians": [{"guardian": <dados de João Souza>, "relationship": "FATHER"}]`
- **THEN** a resposta é `409 Conflict`, Ana Souza continua só com Maria Souza vinculada, e João Souza não é gravado

### Requirement: Responsáveis nas respostas da criança
As respostas que devolvem a criança completa (criação, alteração e consulta por id) SHALL conter `guardians`, com os responsáveis vinculados em ordem alfabética de nome, cada um com `id`, `name`, `relationship`, `phone`, `notWorking`, `workplace` e `workPhone` (estes dois `null` quando `notWorking` é `true`), sem CPF, RG, data de nascimento, estado civil nem endereço do trabalho. Sem responsável vinculado, `guardians` SHALL ser `[]`. Os itens da listagem de crianças (`GET /api/v1/children`) SHALL continuar sem os responsáveis.

#### Scenario: Consulta com dois responsáveis
- **WHEN** a criança `Ana Souza` tem vinculados Maria Souza (`MOTHER`, trabalha) e Carlos Souza (`GRANDFATHER`, com `"notWorking": true`), e um cliente envia `GET /api/v1/children/{id}`
- **THEN** a resposta é `200 OK` com `guardians` contendo, nessa ordem, Carlos Souza com `"relationship": "GRANDFATHER"`, `"notWorking": true`, `"workplace": null` e `"workPhone": null`, e Maria Souza com `"relationship": "MOTHER"`, `"workplace": "Padaria Central"` e `"workPhone": "1133334444"`; nenhum item tem `cpf`, `rg`, `birthDate`, `maritalStatus` ou `workAddress`

#### Scenario: Listagem de crianças sem responsáveis
- **WHEN** existe só a criança `Ana Souza`, com Maria Souza vinculada, e um cliente envia `GET /api/v1/children`
- **THEN** o item de `content` não tem a propriedade `guardians`

### Requirement: Exclusão da criança apaga os vínculos com responsáveis
A exclusão da criança (`DELETE /api/v1/children/{id}`) SHALL apagar junto todos os vínculos dela com responsáveis, e SHALL NOT apagar os responsáveis, que continuam cadastrados e vinculados às demais crianças.

#### Scenario: Criança excluída, responsável mantido
- **WHEN** a responsável Maria Souza é vinculada a `Ana Souza` e a `Pedro Souza`, e um `COORDENADOR` envia `DELETE /api/v1/children/{id da Ana}`
- **THEN** a resposta é `204 No Content`, e `GET /api/v1/guardians/{id da Maria}` responde `200 OK` com só `Pedro Souza` em `children`

#### Scenario: EDUCADOR não exclui criança com responsável
- **WHEN** a criança `Ana Souza` tem Maria Souza vinculada e um usuário `EDUCADOR` envia `DELETE /api/v1/children/{id}`
- **THEN** a resposta é `403 Forbidden`, e Maria Souza continua vinculada a `Ana Souza`
