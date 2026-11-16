# Proposal

## Why

O cadastro de crianças deixou os responsáveis de fora, e hoje nenhuma criança tem responsável registrado: a escola não sabe para quem ligar nem onde o responsável trabalha. Irmãos costumam ter os mesmos responsáveis, então um responsável cadastrado uma vez precisa servir a mais de uma criança, sem digitar os dados de novo. Esta change cobre a parte de back-end da tarefa [Cadastro de responsáveis vinculados às crianças](https://app.clickup.com/t/1dfh60phm2u) (subtarefa [API de responsáveis e vínculo com crianças](https://app.clickup.com/t/1dfh60phm35)); as telas ficam no repositório do frontend.

## What Changes

- **Novo módulo `guardian`** com o cadastro de responsáveis: nome, estado civil, data de nascimento, CPF, RG, telefone de contato, "não trabalha", onde trabalha, endereço do trabalho e telefone do trabalho.
  - Obrigatórios: nome e telefone; os três campos de trabalho são obrigatórios, a não ser que `notWorking` seja `true` (aí são gravados como ausentes). Opcionais: estado civil (mesma lista da criança), data de nascimento (não futura no fuso `America/Sao_Paulo`), CPF (válido e único entre responsáveis, `409` sem expor o número) e RG (não único).
  - `GET /api/v1/guardians`: listagem paginada, em ordem alfabética, com busca `q` (trecho do nome sem diferenciar caixa e acento, ou CPF completo só com números, por correspondência exata). Cada item tem só o resumo (`id`, `name`, `phone` e as crianças vinculadas com o parentesco), sem CPF, RG nem data de nascimento.
  - `GET /api/v1/guardians/{id}`: responsável completo, com as crianças vinculadas e o parentesco de cada uma.
  - `PUT /api/v1/guardians/{id}`: substitui os dados do responsável (não os vínculos); a mudança aparece em todas as crianças vinculadas.
  - Acesso para qualquer usuário autenticado (`COORDENADOR` ou `EDUCADOR`).
- **Vínculo N:N criança–responsável**, com o grau de parentesco no vínculo (`MOTHER`, `FATHER`, `GRANDMOTHER`, `GRANDFATHER`, `AUNT`, `UNCLE`, `SISTER`, `BROTHER`, `STEPMOTHER`, `STEPFATHER`, `LEGAL_GUARDIAN`, `OTHER`).
  - **BREAKING** para o frontend: `POST` e `PUT /api/v1/children` passam a exigir `guardians`, com 1 a 5 itens; cada item é um responsável já cadastrado (`guardianId`) ou um responsável novo (`guardian`, com os dados completos), sempre com `relationship`. Tudo na mesma transação da criança: se algo falha, nem a criança nem os responsáveis novos são gravados.
  - O `PUT` substitui a lista de vínculos da criança. O mesmo responsável não pode aparecer duas vezes na mesma criança.
  - Crianças antigas, sem responsável, continuam válidas na leitura (`guardians: []`), mas qualquer alteração exige pelo menos um responsável.
  - Excluir a criança apaga os vínculos dela, mas não os responsáveis.
- **Responsáveis nas respostas completas da criança** (criação, alteração e consulta por id): `guardians` com `id`, `name`, `relationship`, `phone`, `notWorking`, `workplace` e `workPhone`. A listagem de crianças não muda.
- **Conflito de CPF apontando o campo**: o `409` de CPF de responsável traz, além do `detail`, uma entrada em `errors` com o caminho do campo (`cpf`, ou `guardians[i].guardian.cpf` no corpo da criança), para o frontend mostrar o erro no responsável certo.
- Contrato `openapi/educare-api.json` atualizado.

## Non-goals

- Excluir responsável e limpar responsáveis que ficaram sem criança (pergunta em aberto de LGPD na tarefa). A FK do vínculo impede apagar um responsável vinculado.
- Cadastrar responsável fora do corpo da criança (`POST /api/v1/guardians`).
- Responsável "principal", autorização para buscar a criança na saída, contato de emergência separado.
- Endereço residencial do responsável e endereço do trabalho estruturado (CEP, cidade, UF).
- Acesso ao sistema pelo responsável.
- Busca por trecho do CPF (só o número completo, para não permitir varredura).
- Responsáveis nos itens da listagem de crianças.
- Telas, máscaras e estados do frontend (repositório próprio).

## Capabilities

### New Capabilities
- `guardian`: dados e validações do responsável, listagem com busca, consulta por id, alteração, crianças vinculadas nas respostas, proteção dos documentos (LGPD), autoria e acesso.

### Modified Capabilities
- `child`: responsáveis obrigatórios (1 a 5) na criação e na alteração, com responsável novo ou já cadastrado e o parentesco; substituição dos vínculos no `PUT`; responsáveis nas respostas completas; exclusão da criança apagando os vínculos sem apagar os responsáveis.

A mudança atravessa os dois módulos porque o vínculo liga os dois. O `child` é dono do vínculo (é a criança que ganha e perde responsáveis, como acontece com as turmas): ele cria e consulta responsáveis por um serviço de `application` do `guardian`, e implementa a consulta "crianças de cada responsável" que o `guardian` declara como porta própria (mesmo padrão do `ClassroomStudentCounter`). O `guardian` não depende do `child`.

## Impact

- **Código**:
  - `guardian` (novo): entidade, repositório e exceções em `domain`; `GuardianService` (casos de uso da API), serviço para outros módulos e a porta das crianças vinculadas em `application`; controller, DTOs e mapper em `api`;
  - `child`: entidade e repositório do vínculo, enum de parentesco, `ChildService`/`ChildController`/`ChildRequest`/`ChildResponse`/`ChildMapper` alterados, implementação da porta do `guardian`;
  - `shared`: validação de CPF e normalização de CPF e RG saem de `child` para `shared/validation` (os dois módulos usam); nova exceção base de conflito ligada a campo (`409` com `errors`) e forma de reancorar o campo de um erro dentro de um item da lista;
  - testes existentes de `child` ajustados (criança passa a precisar de responsável).
- **API**: novos `GET /api/v1/guardians`, `GET /api/v1/guardians/{id}` e `PUT /api/v1/guardians/{id}`; `guardians` obrigatório em `POST`/`PUT /api/v1/children` e presente nas respostas completas da criança; `409` de CPF de responsável com `errors`.
- **Contrato**: `openapi/educare-api.json` regenerado; `ApiContractContentTest` atualizado. O frontend regenera `schema.gen.ts`.
- **Banco**: nova migration `V10__create_guardians.sql` (tabelas `guardians` e `child_guardians`, constraints e índices). Compatível com a versão em produção: só cria tabelas.
- **Implantação**: o frontend em produção deixa de conseguir criar ou alterar crianças até receber a versão com a seção de responsáveis; as duas versões precisam ir juntas.
- **Dependências de bibliotecas e infraestrutura**: nenhuma nova; nenhuma variável de ambiente nova.
