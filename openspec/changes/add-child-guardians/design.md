# Design

## Context

Motivação em proposal.md (Why); comportamento em `specs/guardian/spec.md` e `specs/child/spec.md`.

Estado atual que molda a abordagem:

- `child` tem `Child`, `ChildRepository` (com `findByIdForUpdate`, lock pessimista), `ChildClassroom`/`ChildClassroomRepository`, `ChildService`, `ChildController`, `ChildRequest` (record com compact constructor que limpa os textos), `ChildResponse`, `ChildMapper` e `ChildView(child, classroom)`. O vínculo com turma usa ids simples (sem `@ManyToOne`) e estende `BaseEntity`.
- Validação de CPF: `child/domain/Cpf` (normalização e dígitos verificadores) e `child/api/ValidCpf` + `ValidCpfValidator`; RG: `child/domain/Rg`. Estado civil: `child/domain/MaritalStatus`.
- Padrão entre módulos já em uso: o `child` lê turmas pelo `ClassroomLookup` (`classroom/application`), e implementa a porta `ClassroomStudentCounter`, declarada pelo `classroom`, para as contagens. O `classroom` não depende do `child`.
- `ArchitectureTest`: um módulo só usa `application` de outro; sem ciclo entre módulos; `domain` não depende de `api`/`application`; `shared` não depende de módulo.
- `GlobalExceptionHandler`: Bean Validation vira `400` com `errors` (o `field` é o caminho do Spring, como `guardians[0].guardian.name`); `InvalidFieldException` vira `400` com uma entrada; `ConflictException` vira `409` só com `detail`. O schema `ProblemDetail` do contrato já tem `errors` (`FieldError`) opcional.
- Busca sem acento: função `immutable_unaccent` criada na `V6`. A última migration é a `V9`.

## Goals / Non-Goals

**Goals:**
- `guardian` não depende de `child`; `child → guardian` só por `application` (ArchUnit sem ciclo e sem acesso a `api`/`domain` de outro módulo).
- Criança, responsáveis novos e vínculos numa transação só: qualquer recusa desfaz tudo.
- Número fixo de consultas na listagem de responsáveis, na consulta de responsável e na consulta de criança, independente da quantidade de crianças ou responsáveis (RNF02, sem N+1).
- Nenhum dado pessoal em mensagens de erro nem em logs (o `409` de CPF não repete o número).

**Non-Goals:**
- Índice trigram para a busca: com até 2.000 responsáveis, a varredura da tabela atende os 500 ms (D7).
- Endpoint de criação ou exclusão de responsável.
- Ordem manual dos responsáveis da criança (a resposta usa ordem alfabética).

## Decisions

### D1. Endpoints

| Método | Rota | Mudança | Sucesso | Erros | Acesso |
|---|---|---|---|---|---|
| `GET` | `/api/v1/guardians` | **novo**: `q?`, `page`, `size`, `sort` (`name`, `createdAt`); `PagedModel<GuardianSummaryResponse>` | `200` | `400` (sort) | autenticado |
| `GET` | `/api/v1/guardians/{id}` | **novo**: `GuardianResponse` | `200` | `400`, `404` | autenticado |
| `PUT` | `/api/v1/guardians/{id}` | **novo**: corpo `GuardianRequest`; `GuardianResponse` | `200` | `400`, `404`, `409` | autenticado |
| `POST` | `/api/v1/children` | `guardians` obrigatório no corpo; `guardians` na resposta | `201` | `400`, `409` | autenticado |
| `PUT` | `/api/v1/children/{id}` | `guardians` obrigatório; substitui os vínculos | `200` | `400`, `404`, `409` | autenticado |
| `GET` | `/api/v1/children/{id}` | `guardians` na resposta | `200` | `400`, `404` | autenticado |

- Sem `@PreAuthorize` no `GuardianController` (qualquer usuário autenticado; o token é exigido pela filter chain, como no `ChildController`). Leituras com `@ResponseStatus(HttpStatus.OK)` por causa dos erros declarados (CLAUDE.md, contrato).
- DTOs do `guardian`:
  - `GuardianRequest(name, maritalStatus, birthDate, cpf, rg, phone, Boolean notWorking, workplace, workAddress, workPhone)`;
  - `GuardianResponse(id, name, @Nullable maritalStatus, @Nullable birthDate, @Nullable cpf, @Nullable rg, phone, notWorking, @Nullable workplace, @Nullable workAddress, @Nullable workPhone, List<GuardianChildResponse> children, createdAt, updatedAt)`;
  - `GuardianSummaryResponse(id, name, phone, List<GuardianChildResponse> children)`;
  - `GuardianChildResponse(id, name, relationship)`.
- DTOs do `child`:
  - `ChildRequest` ganha `List<ChildGuardianRequest> guardians`;
  - `ChildGuardianRequest(UUID guardianId, NewGuardianRequest guardian, String relationship)`;
  - `NewGuardianRequest`, com os mesmos campos e anotações do `GuardianRequest`;
  - `ChildResponse` ganha `List<ChildGuardianResponse> guardians`, com `ChildGuardianResponse(id, name, relationship, phone, notWorking, @Nullable workplace, @Nullable workPhone)`.
  - `ChildSummaryResponse` não muda.

### D2. Quem é dono do vínculo: o `child`

```
guardian/
├── api/          GuardianController, GuardianMapper, dto/ (D1)
├── application/
│   ├── GuardianService           # casos de uso da API: list, findById, update
│   ├── GuardianRegistry          # para outros módulos: create(GuardianInput), findContacts(ids)
│   ├── GuardianInput             # dados de um responsável vindos de outro módulo (record, enums como String)
│   ├── GuardianContact           # id, name, phone, notWorking, workplace, workPhone
│   ├── GuardianChildrenFinder    # porta: crianças de cada responsável, implementada pelo child
│   ├── GuardianChildRef          # childId, childName, relationship
│   └── GuardianView, GuardianSummaryView   # responsável + crianças, entregues ao mapper
└── domain/
    ├── Guardian, GuardianDetails, GuardianRepository, GuardianSpecifications, MaritalStatus
    ├── GuardianNotFoundException                      # NotFoundException
    ├── GuardianCpfAlreadyInUseException               # FieldConflictException (D5), campo cpf
    └── GuardianBirthDateInFutureException             # InvalidFieldException, campo birthDate

child/
├── api/dto/      ChildGuardianRequest, NewGuardianRequest, ChildGuardianResponse; ChildRequest/ChildResponse alterados
├── api/          OneGuardianSource (+ validator), UniqueGuardianIds (+ validator); ChildMapper alterado
├── application/  ChildService alterado; ChildGuardianView; LinkedGuardianChildrenFinder (implementa GuardianChildrenFinder)
└── domain/       ChildGuardian, ChildGuardianRepository, Relationship, GuardianNotFoundForChildException
```

- O vínculo (`child_guardians`, entidade `ChildGuardian`) fica no `child`, como o `child_classrooms`: é a criança que ganha e perde responsáveis, a exclusão em cascata segue a criança, e o `PUT` da criança é quem substitui os vínculos. O `Relationship` também é do `child`, porque é atributo do vínculo.
- O `child` cria e lê responsáveis só pelo `GuardianRegistry`. O `guardian` precisa das crianças de cada responsável (nome e parentesco) e as pede pela porta `GuardianChildrenFinder`, que ele declara e o `child` implementa (`LinkedGuardianChildrenFinder`, uma consulta JPQL com join entre `ChildGuardian` e `Child`, filtrada pelos ids e ordenada por nome da criança e id). Dependência de código: só `child → guardian`.
- `GuardianRegistry` é separado do `GuardianService` pelo mesmo motivo do `ClassroomLookup`: o `GuardianService` depende da porta, implementada no `child`. Se o `ChildService` dependesse do `GuardianService`, haveria um caminho de beans `ChildService → GuardianService → LinkedGuardianChildrenFinder`, frágil. O `LinkedGuardianChildrenFinder` depende só do `ChildGuardianRepository`.
- `ChildGuardian` estende `BaseEntity` (`id uuid`, datas e autoria), com `childId`, `guardianId` (colunas simples, sem `@ManyToOne`: `Guardian` é de outro módulo) e `relationship`. Métodos: `ChildGuardian.link(childId, guardianId, relationship)` e `changeRelationship(relationship)`.
  - *Alternativa*: PK composta `(child_id, guardian_id)`, como sugere a tarefa. Descartada: toda entidade de negócio estende `BaseEntity` com `id uuid` (CLAUDE.md, Armadilhas). A unicidade do par fica numa constraint `UNIQUE`.
  - *Alternativa*: vínculo no `guardian`. Descartada: o `guardian` passaria a precisar do `child` para validar a criança e para apagar os vínculos na exclusão, e o `PUT` da criança dependeria de escrita no outro módulo.

### D3. Regras compartilhadas: CPF, RG, estado civil e campos de trabalho

- `Cpf`, `ValidCpf`/`ValidCpfValidator` e `Rg` vão de `child` para `shared/validation` (onde já está `MaxUtf8Bytes`), sem mudar comportamento; o `child` passa a importá-los de lá, e os testes (`CpfTest`, `RgTest`, `ValidCpfValidatorTest`) vão junto. São regras de documento brasileiro, sem dependência de módulo.
  - *Alternativa*: copiar para o `guardian`. Descartada: duas implementações dos dígitos verificadores podem divergir.
- `MaritalStatus`: o `guardian` tem o próprio enum em `guardian/domain`, com os mesmos valores. Um teste do `guardian` compara as constantes com as de `child/domain/MaritalStatus` (os testes não passam pelo ArchUnit), para a lista não divergir em silêncio (RN08).
  - *Alternativa*: mover o enum para `shared`. Descartada: é um conceito de domínio, não uma regra técnica transversal; o teste resolve a divergência.
- Campos de trabalho condicionais: nova anotação genérica de classe `shared/validation/RequiredUnless(flag = "notWorking", fields = {"workplace", "workAddress", "workPhone"})`, que lê os componentes do record e, com o flag diferente de `true`, gera uma violação **em cada campo** vazio (`addPropertyNode`). Assim os três erros saem juntos com os demais, no caminho certo (`workplace` ou `guardians[0].guardian.workplace`). Usada em `GuardianRequest` e `NewGuardianRequest`.
- Os dois DTOs de responsável (`GuardianRequest` no `guardian`, `NewGuardianRequest` no `child`) têm as mesmas anotações: `@NotBlank`/`@Size` em `name` (150), `workplace` (150), `workAddress` (200) e `rg` (20, mais o `@Pattern` de letra ou dígito do `ChildRequest`); `@Pattern("^[0-9]{10,11}$")` em `phone` e `workPhone`; `@ValidCpf`; `@Pattern` do estado civil; compact constructor que limpa os textos e, com `notWorking == true`, anula os três campos de trabalho. O `child` não pode importar o `api` do `guardian`, por isso a duplicação; os testes de cada controller cobrem as regras, e a entidade `Guardian` valida tudo de novo.
  - *Alternativa*: um record com as anotações em `guardian/application`, usado pelos dois corpos. Descartada: as anotações de Bean Validation ficam em `api` (CLAUDE.md, tabela de camadas).

### D4. Validação dos itens de `guardians`

- No `ChildRequest`: `@NotEmpty(message = "Informe pelo menos um responsável.")`, `@Size(max = 5, message = "Informe no máximo 5 responsáveis.")` e `@UniqueGuardianIds` (validador de lista: `guardianId` não nulo repetido → violação em `guardians` com "O mesmo responsável não pode ser informado mais de uma vez."), sobre `List<@NotNull @OneGuardianSource @Valid ChildGuardianRequest>`.
- `@OneGuardianSource`: constraint de classe no item → violação no próprio item (`guardians[i]`) quando não há exatamente um entre `guardianId` e `guardian`.
- `relationship`: `@NotBlank` + `@Pattern` com os 12 valores (mesmo padrão dos demais enums do `ChildRequest`, que recebem `String`).
- `guardianId` malformado é erro do Jackson com caminho → `400` com `errors.guardians[i].guardianId`, sem código novo.

### D5. Erros com caminho do item e `409` com `errors`

- Nova base `shared/error/FieldConflictException(field, message)`, convertida pelo `GlobalExceptionHandler` em `409` com `detail` = mensagem e `errors` = `[{field, message}]`. `GuardianCpfAlreadyInUseException` estende essa base, com campo `cpf` e a mensagem "Já existe um responsável com este CPF.". Os conflitos da criança (`CpfAlreadyInUseException` etc.) continuam como estão.
- Erros detectados fora da Bean Validation num responsável novo (data de nascimento futura, CPF repetido) nascem com o campo relativo ao responsável (`birthDate`, `cpf`). O `ChildService`, que sabe o índice do item, os relança com o prefixo `guardians[i].guardian.`: `InvalidFieldException` e `FieldConflictException` ganham o método `withFieldPrefix(String prefix)`, que devolve uma exceção do mesmo tipo-base (subclasses concretas `NestedInvalidFieldException`/`NestedFieldConflictException` em `shared/error`) com o campo `prefix + field` e a mesma mensagem.
- `guardianId` inexistente: `GuardianNotFoundForChildException` (`child/domain`, `InvalidFieldException`) com o campo `guardians[i].guardianId` e a mensagem "Responsável não encontrado", como a turma inexistente.

### D6. Casos de uso

**`GuardianRegistry` (para o `child`)**, `@Transactional` (entra na transação de quem chama):
- `UUID create(GuardianInput input)`: converte para `GuardianDetails` (estado civil `String` → enum), recusa data de nascimento depois de hoje em `America/Sao_Paulo` (bean `Clock`), verifica `existsByCpf` quando há CPF, grava com `saveAndFlush` e converte a violação de `guardians_cpf_key` (corrida) em `GuardianCpfAlreadyInUseException`. Outra violação é relançada.
- `Map<UUID, GuardianContact> findContacts(Collection<UUID> ids)`: uma consulta (`findAllById`); ids inexistentes ficam fora do mapa; coleção vazia não consulta.

**`ChildService`**:
- `create(details, classroomId, guardians)` e `update(id, details, classroomId, guardians)`, onde `guardians` é uma lista de `ChildGuardianCommand(UUID guardianId, GuardianInput newGuardian, Relationship relationship)` (record de `child/application`). Ordem, dentro da transação atual:
  1. (no `update`) lock da criança com `findByIdForUpdate`, como hoje;
  2. validações da criança, turma e documentos da criança, como hoje (recusas antes de gravar qualquer responsável);
  3. `findContacts` com todos os `guardianId` informados (uma consulta); o primeiro item com id ausente do mapa → `GuardianNotFoundForChildException` no índice dele;
  4. para cada item com responsável novo, na ordem: `guardianRegistry.create(...)`, relançando `InvalidFieldException`/`FieldConflictException` com o prefixo do índice (D5). Dois novos com o mesmo CPF: o segundo encontra o primeiro (já gravado com flush) e recebe o `409` no próprio índice;
  5. grava a criança e o vínculo com a turma, como hoje;
  6. vínculos com responsáveis: na criação, insere todos; na alteração, carrega os atuais (`findByChildId`), apaga os que saíram (`deleteAllInBatch` + flush antes dos inserts), muda o parentesco dos que ficaram e insere os novos.
- `findById`: vínculos da criança (uma consulta) + `findContacts` (uma consulta).
- A resposta monta `List<ChildGuardianView>` (contato + parentesco) em ordem alfabética de nome, desempate por id. `ChildView` ganha essa lista.
- `delete` não muda: a FK `ON DELETE CASCADE` apaga os vínculos; os responsáveis ficam.

**`GuardianService`** (API do `guardian`):
- `list(q, pageable)`: `q` limpo; se casar `^[0-9]{11}$`, filtro `cpf = q`; senão, filtro de nome com `immutable_unaccent` e o `likePattern` (mesmo escape de `%`, `_` e `\` do `ChildService`). Depois, uma chamada à porta com os ids da página. Total: página + contagem + crianças.
- `findById(id)`: responsável + porta com um id.
- `update(id, input)`: carrega (404), valida a data, verifica `existsByCpfAndIdNot`, `guardian.update(details)`, `saveAndFlush` com a mesma conversão da violação de CPF. Devolve a view com as crianças.

### D7. Migration `V10__create_guardians.sql`

```sql
-- Responsáveis pelas crianças (dados pessoais: LGPD). Autoria sem FK para users e NOT NULL:
-- todo responsável é gravado por uma requisição autenticada.
CREATE TABLE guardians (
    id              uuid          PRIMARY KEY,
    name            varchar(150)  NOT NULL,
    marital_status  varchar(20),
    birth_date      date,
    cpf             varchar(11),
    rg              varchar(20),
    phone           varchar(11)   NOT NULL,
    not_working     boolean       NOT NULL,
    workplace       varchar(150),
    work_address    varchar(200),
    work_phone      varchar(11),
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    created_by      uuid          NOT NULL,
    updated_by      uuid          NOT NULL,
    CONSTRAINT guardians_cpf_check CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT guardians_rg_check CHECK (rg ~ '^[0-9A-Z]+$'),
    CONSTRAINT guardians_phone_check CHECK (phone ~ '^[0-9]{10,11}$'),
    CONSTRAINT guardians_work_phone_check CHECK (work_phone ~ '^[0-9]{10,11}$'),
    CONSTRAINT guardians_marital_status_check
        CHECK (marital_status IN ('SINGLE', 'MARRIED', 'STABLE_UNION', 'DIVORCED', 'WIDOWED')),
    -- Não trabalha: sem dados de trabalho. Trabalha: os três preenchidos.
    CONSTRAINT guardians_work_check CHECK (
        (not_working AND workplace IS NULL AND work_address IS NULL AND work_phone IS NULL)
        OR (NOT not_working AND workplace IS NOT NULL AND work_address IS NOT NULL AND work_phone IS NOT NULL))
);

-- CPF único entre responsáveis; vários sem CPF.
CREATE UNIQUE INDEX guardians_cpf_key ON guardians (cpf) WHERE cpf IS NOT NULL;

-- Ordenação padrão da listagem.
CREATE INDEX guardians_name_idx ON guardians (name);

-- Vínculo criança–responsável, com o parentesco. Excluir a criança apaga os vínculos dela;
-- um responsável vinculado não pode ser excluído. Autoria NOT NULL, sem FK para users.
CREATE TABLE child_guardians (
    id            uuid         PRIMARY KEY,
    child_id      uuid         NOT NULL REFERENCES children (id) ON DELETE CASCADE,
    guardian_id   uuid         NOT NULL REFERENCES guardians (id) ON DELETE RESTRICT,
    relationship  varchar(20)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    created_by    uuid         NOT NULL,
    updated_by    uuid         NOT NULL,
    CONSTRAINT child_guardians_child_guardian_key UNIQUE (child_id, guardian_id),
    CONSTRAINT child_guardians_relationship_check CHECK (relationship IN ('MOTHER', 'FATHER', 'GRANDMOTHER',
        'GRANDFATHER', 'AUNT', 'UNCLE', 'SISTER', 'BROTHER', 'STEPMOTHER', 'STEPFATHER', 'LEGAL_GUARDIAN', 'OTHER'))
);

-- Crianças de cada responsável (listagem e consulta) e verificação da FK.
CREATE INDEX child_guardians_guardian_id_idx ON child_guardians (guardian_id);
```

- A `UNIQUE (child_id, guardian_id)` já indexa a busca pelos vínculos de uma criança.
- O limite de 5 responsáveis fica só na aplicação: é uma regra de produto que pode mudar sem migration, e todo vínculo passa pelo `PUT`/`POST` da criança.
- Sem `pg_trgm`: a busca `%texto%` não usa índice B-tree; com até 2.000 linhas a varredura leva poucos milissegundos, e uma extensão a mais não se justifica na VM Free Tier. Revisitar se a tabela crescer uma ordem de grandeza.

### D8. Contrato

- `./gradlew updateApiContract` regenera `openapi/educare-api.json`. Esperado no diff: as três operações de `/api/v1/guardians` (com `401`/`429` automáticos), `guardians` obrigatório em `ChildRequest` e em `ChildResponse`, os schemas `ChildGuardianRequest`, `NewGuardianRequest`, `ChildGuardianResponse`, `GuardianRequest`, `GuardianResponse`, `GuardianSummaryResponse` e `GuardianChildResponse`, com os campos `@Nullable` opcionais.
- `ApiContractContentTest` passa a verificar as operações de responsáveis (status e erros), os campos obrigatórios das respostas e que `GuardianSummaryResponse` não tem `cpf`, `rg` nem `birthDate`.

## Risks / Trade-offs

- [Frontend em produção quebra ao criar ou alterar criança assim que esta versão subir, porque não envia `guardians`] → publicar a tag do backend junto com o deploy do frontend que tem a seção de responsáveis; o PR do backend só recebe o label `build` (ou a tag `vX.Y.Z`) quando o frontend estiver pronto.
- [Dois DTOs de responsável com as mesmas regras podem divergir] → testes de validação nos dois controllers com os mesmos casos; a entidade `Guardian` valida tudo de novo, então uma divergência vira erro `500` visível nos testes de cenário, não dado inválido gravado.
- [Responsáveis órfãos (sem criança) acumulam dados pessoais] → fora do escopo por decisão da tarefa (pergunta em aberto de LGPD); a FK `RESTRICT` e a ausência de endpoint de exclusão mantêm a decisão reversível.
- [Busca por CPF permite confirmar se um CPF está cadastrado] → só com o número completo e só para usuários autenticados (funcionários), como decidido na tarefa; a resposta da listagem não traz o CPF.
- [Erros de responsável novo que dependem do banco ou do relógio (CPF repetido, data futura) saem um por vez, depois dos erros da Bean Validation] → mesmo comportamento das regras equivalentes da criança; o frontend já valida a data antes de enviar.

## Migration Plan

1. A `V10` só cria tabelas e índices: a versão anterior continua funcionando com o schema novo durante o rolling update.
2. Deploy coordenado com o frontend (ver Risks).
3. Rollback: voltar a tag da imagem no `ImageUpdater` ou pelo histórico do Argo CD. As tabelas novas ficam no banco, sem uso pela versão anterior; os vínculos gravados não atrapalham a exclusão de crianças (cascata). Nada a desfazer no schema.
