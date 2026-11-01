# Proposal

## Why

O cadastro das crianças atendidas é o centro do Educare: responsáveis, turmas, matrícula e rematrícula dependem dele. Hoje a API tem usuários, autenticação e turmas, mas os funcionários ainda não têm onde registrar as crianças, e as turmas mostram sempre 0 crianças.

## What Changes

- Novo módulo `child` com o CRUD completo de crianças, exposto em `/api/v1/children`:
  - criar criança;
  - listar crianças com paginação, ordenação e busca por trecho do nome (ignorando maiúsculas e acentos);
  - buscar criança por id;
  - alterar todos os dados da criança (substituição completa);
  - excluir criança (exclusão definitiva).
- Dados da criança, além de `id` (UUID) e `createdAt`/`updatedAt` (herdados de `BaseEntity`):
  - **obrigatórios**: `name`, `birthDate`, `cep`, `address`, `neighborhood`, `addressNumber`, `city`, `state`;
  - **opcionais**: `addressComplement`, `birthplace`, `race`, `gender`, `cpf`, `rg`, `nationality`, `maritalStatus`, `religion`, `church`, `healthReport`, `medication`, `medicalObservation` e a **matrícula da certidão de nascimento** (`birthCertificateNumber`).
- Regras de validação:
  - textos gravados sem espaços nas pontas, com tamanho máximo por campo; opcionais vazios ou só com espaços viram ausentes (`null`);
  - `birthDate` no formato `AAAA-MM-DD`, sem ser futura (fuso `America/Sao_Paulo`); data em outro formato ou inexistente é recusada com `400` e uma entrada em `errors` para o campo;
  - `cep` com 8 dígitos, aceito com ou sem hífen (`01310-100` ou `01310100`) e gravado só com dígitos;
  - `state` é a sigla de uma das 27 UFs (ex.: `SP`, `DF`), aceita em maiúsculas ou minúsculas e gravada em maiúsculas;
  - `race` (`WHITE`, `BLACK`, `BROWN`, `YELLOW`, `INDIGENOUS`, pelas categorias do IBGE), `gender` (`MALE`, `FEMALE`, `OTHER`) e `maritalStatus` (`SINGLE`, `MARRIED`, `STABLE_UNION`, `DIVORCED`, `WIDOWED`) só aceitam esses valores; o frontend traduz para exibir;
  - `cpf` aceito com ou sem máscara, com dígitos verificadores válidos, gravado só com dígitos e único entre as crianças (`409 Conflict` se repetir, sem expor o CPF na resposta);
  - `rg` aceito com qualquer pontuação, gravado só com letras e dígitos em maiúsculas (`12.345.678-x` → `12345678X`) e único entre as crianças (`409 Conflict` se repetir, sem expor o RG na resposta);
  - **matrícula da certidão de nascimento** opcional, só do modelo de certidão com matrícula: aceita com pontos, espaços e hífens, gravada só com os dígitos e com exatamente 32 dígitos, sem conferir a veracidade (dígitos verificadores, tipo de livro, ano do registro); única entre as crianças (`409 Conflict` sem expor a matrícula).
- Acesso: qualquer usuário autenticado (`COORDENADOR` ou `EDUCADOR`) cria, consulta, lista e altera crianças; só `COORDENADOR` exclui.
- Autoria: cada criança guarda quem a criou e quem a alterou por último, sem expor isso na API.
- A listagem devolve só um resumo de cada criança (sem dados de saúde, documentos, matrícula da certidão etc.); os dados completos saem só na consulta por id, na criação e na alteração (minimização, LGPD).
- Erros de desserialização ligados a um campo (data em formato inválido, tipo errado) passam a responder `400` com uma entrada em `errors` para o campo, em toda a API; JSON sintaticamente quebrado continua com `400` sem `errors`.
- Nova migration Flyway com a tabela `children`, já com a coluna da matrícula. A busca por nome usa a função `immutable_unaccent`, que a `V6` (turmas) já criou.
- Contrato `openapi/educare-api.json` atualizado com as operações de `/api/v1/children`.

## Non-goals

- Vínculo da criança com a turma, filtro da listagem por turma e contagens reais das turmas: entram na change `link-child-to-classroom`, que vai para produção junto com esta (nenhuma criança pode existir sem turma em produção).
- Responsáveis e o vínculo entre criança e responsável (change futura do módulo `guardian`).
- Matrícula, rematrícula e período de rematrícula como processo.
- Expor a autoria nas respostas da API.
- Consulta de endereço pelo CEP (ViaCEP ou similar) e conferência de que cidade e UF correspondem ao CEP: os campos de endereço são guardados como informados, sem integração externa.
- Validação do formato do RG por estado e registro do órgão emissor: o RG só é normalizado e tem a unicidade verificada.
- Certidão no modelo antigo (cartório, livro, folha e termo, sem matrícula): só a matrícula é guardada; quem tiver só a certidão antiga pode tirar a segunda via, que já sai com a matrícula.
- Conferência da veracidade da matrícula (dígitos verificadores, tipo de livro, ano do registro), arquivo ou foto da certidão (anexos) e consulta no cartório ou na CRC: o sistema só confere que a matrícula tem 32 dígitos.
- Filtros na listagem além da busca por nome (o filtro por turma vem com `link-child-to-classroom`).
- Índice para a busca por nome.
- Exclusão lógica (soft delete), histórico de alterações e anexos (arquivos de laudo).
- Alteração parcial (`PATCH`).

## Capabilities

### New Capabilities
- `child`: cadastro (CRUD) das crianças atendidas pela instituição, com dados pessoais, endereço, documentos (CPF, RG e matrícula da certidão de nascimento) e dados de saúde, validação de CPF, RG, CEP, UF e do formato da matrícula, listas fixas para raça, gênero e estado civil, busca por nome, autoria dos registros e exclusão restrita a `COORDENADOR`. É o módulo de negócio `child` (pacote `com.manuelaalecio.educare_backend.child`); nesta change não depende de outros módulos de negócio.

### Modified Capabilities
<!-- Nenhuma: `security` (token obrigatório, 401/403 em ProblemDetail), `persistence` (BaseEntity, migrations, autoria) e `api-contract` (contrato igual ao gerado, campos obrigatórios, erros declarados) são usadas como estão. A mudança no GlobalExceptionHandler só acrescenta `errors` a respostas 400 já existentes e não contradiz nenhum requirement de `user` nem de `classroom`. -->

## Impact

- **Dependências de planejamento**: `add-record-authorship` e `add-classroom-crud` já aplicadas (autoria na `BaseEntity`, função `immutable_unaccent` da `V6`). A change `link-child-to-classroom` depende desta e vai para produção junto com ela.
- **Código**:
  - novo pacote `child/` com as camadas `api`, `application` e `domain`;
  - `shared/error/GlobalExceptionHandler`: erros de desserialização ligados a um campo viram entradas em `errors`. A mudança é transversal (vale para toda a API) porque o handler é único; entra aqui porque o `birthDate` é o primeiro campo que precisa dela.
- **API**: novos endpoints em `/api/v1/children` (detalhados no design); respostas `400` de campos com tipo ou formato inválido ganham `errors` em todos os endpoints.
- **Contrato**: `openapi/educare-api.json` regenerado com `./gradlew updateApiContract`; o `ApiContractContentTest` passa a cobrir as operações de crianças.
- **Banco**: nova migration `V8__create_children.sql`, com a tabela `children` (incluindo `created_by`/`updated_by` e a coluna da matrícula), a unicidade do `cpf`, do `rg` e da matrícula e os `CHECK`s das listas fixas, da UF e do formato dos documentos.
- **Dependências de bibliotecas**: nenhuma nova.
- **Documentação**: nenhuma mudança no CLAUDE.md esperada (o módulo segue a estrutura já descrita).
