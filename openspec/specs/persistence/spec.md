# persistence Specification

## Purpose

Garantir que o schema do banco de dados evolua só por migrations versionadas, idênticas em todos os ambientes, e que todo registro de negócio tenha um identificador único e as datas de criação e da última alteração.

## Requirements

### Requirement: Migrations versionadas aplicadas na inicialização
O sistema SHALL aplicar, ao iniciar, todas as migrations versionadas ainda não aplicadas ao banco, em ordem de versão, e registrar cada uma no histórico de versões do schema. Migrations já aplicadas SHALL NOT ser executadas de novo.

#### Scenario: Banco vazio recebe todas as migrations
- **WHEN** a aplicação inicia conectada a um banco PostgreSQL 16 vazio
- **THEN** a aplicação fica disponível e o histórico de versões do schema contém a migration de versão `1` (baseline) marcada como aplicada com sucesso

#### Scenario: Reinicialização não reaplica migrations
- **WHEN** a aplicação é reiniciada contra um banco em que todas as migrations já foram aplicadas
- **THEN** a aplicação fica disponível e o histórico de versões do schema continua com o mesmo número de registros de antes da reinicialização

### Requirement: Migrations aplicadas são imutáveis
O sistema SHALL recusar a inicialização quando o conteúdo de uma migration já aplicada for diferente do que foi registrado no histórico no momento da aplicação.

#### Scenario: Migrations íntegras são validadas
- **WHEN** a validação das migrations roda contra um banco cujo histórico corresponde exatamente às migrations do projeto
- **THEN** a validação passa sem erros

#### Scenario: Migration aplicada alterada impede a inicialização
- **WHEN** a validação das migrations roda contra um banco em que o checksum registrado da migration de versão `1` difere do checksum do arquivo atual
- **THEN** a validação falha com erro de checksum divergente para a versão `1`, a aplicação não inicia e nenhuma migration é aplicada

### Requirement: Schema validado contra o mapeamento das entidades
O sistema SHALL NOT criar nem alterar tabelas a partir do mapeamento das entidades; ao iniciar, SHALL validar que cada entidade mapeada corresponde a uma tabela e colunas existentes no schema criado pelas migrations, e recusar a inicialização se houver divergência.

#### Scenario: Entidades compatíveis com o schema
- **WHEN** a aplicação inicia com entidades cujas tabelas e colunas existem no schema criado pelas migrations
- **THEN** a aplicação fica disponível e nenhuma tabela além das criadas pelas migrations existe no banco

#### Scenario: Entidade sem tabela correspondente impede a inicialização
- **WHEN** a aplicação inicia com uma entidade mapeada para uma tabela que não existe no schema do banco
- **THEN** a inicialização falha com erro de validação de schema que cita a tabela ausente, e essa tabela continua não existindo no banco

### Requirement: Limpeza do banco pela ferramenta de migrations é bloqueada
O sistema SHALL impedir que a ferramenta de migrations apague o schema (operação de limpeza), em qualquer ambiente.

#### Scenario: Banco migrado permanece intacto sem pedido de limpeza
- **WHEN** a aplicação inicia e aplica as migrations
- **THEN** o histórico de versões do schema e os dados existentes permanecem no banco

#### Scenario: Pedido de limpeza é recusado
- **WHEN** a operação de limpeza é solicitada, com a configuração da aplicação, contra um banco já migrado
- **THEN** a operação falha com erro informando que a limpeza está desabilitada, e o histórico de versões do schema continua com os mesmos registros

### Requirement: Identificador único dos registros
Todo registro de negócio SHALL receber, no momento em que é gravado pela primeira vez, um identificador UUID gerado pelo sistema, que não muda durante a vida do registro.

#### Scenario: Registro novo recebe identificador
- **WHEN** um registro de negócio sem identificador é gravado
- **THEN** o registro gravado tem um identificador UUID não nulo, e buscá-lo por esse identificador retorna o mesmo registro

#### Scenario: Registros diferentes recebem identificadores diferentes
- **WHEN** dois registros de negócio são gravados em sequência
- **THEN** os dois identificadores são diferentes entre si

### Requirement: Datas de criação e de última alteração
Todo registro de negócio SHALL guardar a data e hora de criação e a da última alteração, com fuso horário, preenchidas pelo sistema a partir do relógio da aplicação. A data de criação SHALL NOT mudar depois da primeira gravação.

#### Scenario: Registro novo recebe as duas datas
- **WHEN** um registro de negócio é gravado pela primeira vez com o relógio da aplicação em `2026-01-10T12:00:00Z`
- **THEN** a data de criação e a data de última alteração do registro são ambas `2026-01-10T12:00:00Z`

#### Scenario: Alteração atualiza só a data de última alteração
- **WHEN** um registro criado em `2026-01-10T12:00:00Z` é alterado e gravado com o relógio da aplicação em `2026-01-11T08:30:00Z`
- **THEN** a data de última alteração passa a ser `2026-01-11T08:30:00Z` e a data de criação continua `2026-01-10T12:00:00Z`

#### Scenario: Tentativa de alterar a data de criação é ignorada
- **WHEN** um registro criado em `2026-01-10T12:00:00Z` tem sua data de criação modificada para `2020-01-01T00:00:00Z` antes de ser gravado de novo
- **THEN** a data de criação gravada no banco continua `2026-01-10T12:00:00Z`

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
