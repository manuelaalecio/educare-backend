# Design

## Context

Motivação em proposal.md (Why); comportamento em `specs/child/spec.md` e `specs/classroom/spec.md`.

Estado que molda a abordagem (com `add-child-crud` já aplicada):

- `child` existe com `Child`, `ChildRepository`, `ChildService`, `ChildController`, `ChildRequest`/`ChildResponse`/`ChildSummaryResponse` e a migration `V8__create_children.sql`. A busca por nome é uma `@Query` JPQL com `immutable_unaccent`; a listagem usa `findAll`/`searchByName` e devolve `PagedModel<ChildSummaryResponse>`. A data atual em `America/Sao_Paulo` já é calculada no `ChildService` a partir do bean `Clock`.
- `classroom` já declara a porta `ClassroomStudentCounter` (`countFor(UUID)` e `countFor(Collection<UUID>)`, esta numa consulta só) em `application`, com a implementação provisória `NoEnrollmentStudentCounter` (sempre zero). `ClassroomService.list` já pede as contagens de todas as turmas de uma vez, e `delete` já recusa com `ClassroomHasStudentsException` quando `countFor(id).hasStudents()`.
- `ClassroomController` tem `@PreAuthorize("hasRole('COORDENADOR')")` e `@ForbiddenResponse` **na classe**.
- `ArchitectureTest`: um módulo só pode usar `application` de outro módulo; sem ciclos entre módulos; `domain` não depende de `application`.
- `GlobalExceptionHandler` (com a D6 de `add-child-crud`): erro do Jackson ligado a campo vira `400` com `errors`; `InvalidFieldException` vira `400` com uma entrada em `errors`; parâmetro de query com tipo errado vira `400` em `ProblemDetail` (via `ResponseEntityExceptionHandler`).

## Goals / Non-Goals

**Goals:**
- O `child` é dono do vínculo; o `classroom` continua sem depender do `child` (ArchUnit sem ciclo).
- Invariante "exatamente um vínculo ativo por criança" garantida no banco, não só na aplicação.
- Número fixo de consultas na listagem de crianças e na de turmas, independente do tamanho da página (sem N+1).

**Non-Goals:**
- Expor o id do vínculo na API (o histórico só tem turma e datas).
- Reaproveitar o vínculo como "matrícula": ano letivo, status e documentos ficam para a change de matrícula, que pode evoluir esta tabela ou criar outra.

## Decisions

### D1. Endpoints

| Método | Rota | Mudança | Sucesso | Erros | Acesso |
|---|---|---|---|---|---|
| `POST` | `/api/v1/children` | `classroomId` obrigatório no corpo; `classroom` na resposta | `201` | `400` (inclui `classroomId`), `409` | autenticado |
| `PUT` | `/api/v1/children/{id}` | `classroomId` obrigatório; troca de turma | `200` | `400`, `404`, `409` | autenticado |
| `GET` | `/api/v1/children/{id}` | `classroom` na resposta | `200` | `400`, `404` | autenticado |
| `GET` | `/api/v1/children` | parâmetro `classroomId?`; `classroom` em cada item | `200` | `400` (sort ou `classroomId` malformado) | autenticado |
| `GET` | `/api/v1/children/{id}/classrooms` | **novo**: `List<ChildClassroomResponse>` | `200` | `400`, `404` | autenticado |
| `GET` | `/api/v1/classrooms`, `/api/v1/classrooms/{id}` | liberado para `EDUCADOR` | `200` | — / `400`, `404` | autenticado |
| `POST`/`PUT`/`DELETE` | `/api/v1/classrooms...` | `DELETE` ganha o `409` de histórico | inalterado | inalterado (+ `409`) | `COORDENADOR` |

- `ChildRequest` ganha `UUID classroomId` com `@NotNull` (mensagem "Turma é obrigatória"). UUID malformado no JSON é erro de desserialização com `path` → `400` com `errors.classroomId` (D6 de `add-child-crud`), sem código novo.
- `ChildResponse` e `ChildSummaryResponse` ganham `ClassroomRefResponse classroom` (`record (UUID id, String name)`, ambos obrigatórios no contrato). `ChildClassroomResponse` = `(UUID classroomId, String classroomName, LocalDate startDate, @Nullable LocalDate endDate)`, como pediu a tarefa.
  - *Alternativa*: `classroom` aninhado também no histórico. Descartada: o formato plano foi o combinado com o frontend na tarefa, e cada item do histórico é uma linha de tabela.
- `ClassroomController`: o `@PreAuthorize("hasRole('COORDENADOR')")` e o `@ForbiddenResponse` saem da classe e vão para `create`, `rename` e `delete`. A regra continua no controller do módulo (CLAUDE.md), e o contrato deixa de documentar `403` nas leituras.

### D2. Modelo do vínculo (módulo `child`)

```
child/
├── api/
│   ├── dto/ ClassroomRefResponse, ChildClassroomResponse      # novos
│   └── ChildMapper                                           # monta classroom e o histórico
├── application/
│   ├── ChildService                                          # create/update/list/history com o vínculo
│   ├── ChildView, ChildSummaryView, ChildClassroomView       # novos: entidade + nome da turma, entregues ao mapper
│   └── ActiveChildClassroomCounter                           # implementa classroom.application.ClassroomStudentCounter
└── domain/
    ├── ChildClassroom                                        # entidade do vínculo (child_classrooms)
    ├── ChildClassroomRepository
    ├── ChildSpecifications                                   # filtros da listagem (nome, turma atual)
    └── ClassroomNotFoundForChildException                    # InvalidFieldException, campo classroomId, "Turma não encontrada"
```

- **`ChildClassroom`** estende `BaseEntity` (id, datas e autoria pela auditoria), com `UUID childId`, `UUID classroomId`, `LocalDate startDate`, `LocalDate endDate`. Os ids são colunas simples, sem `@ManyToOne`: `Classroom` é de outro módulo (o `domain` do `child` não pode importá-lo), e com `Child` um id simples basta, já que o vínculo é sempre lido pelo id da criança.
  - `ChildClassroom.open(childId, classroomId, today)` e `close(today)`: `close` exige vínculo ativo e `today >= startDate` (`IllegalArgumentException`), espelhando o `CHECK` do banco.
  - *Alternativa*: coleção `@OneToMany` de vínculos dentro de `Child`, com `child.moveTo(classroomId, today)`. Mais "rica", mas carregaria todo o histórico a cada alteração e complica a ordem de flush (D4). Fica o repositório próprio, com a regra de troca no service.
- **Por que o vínculo fica no `child`**: é a criança que entra e sai de turmas; o `classroom` só precisa das contagens e de saber se tem histórico, que ele já pede por uma porta própria (D5). Assim a dependência é `child → classroom`, nunca o contrário.

### D3. Consulta de turmas pelo `child`: `ClassroomLookup`

- Novo `@Service` em `classroom/application`: `ClassroomLookup` com `Optional<ClassroomRef> find(UUID id)` e `Map<UUID, ClassroomRef> findAll(Collection<UUID> ids)` (uma consulta com `findAllById`), e o record `ClassroomRef(UUID id, String name)` no mesmo pacote. `@Transactional(readOnly = true)`.
- O `child` usa só isso: o `ChildService` valida a turma (`find` vazio → `ClassroomNotFoundForChildException`) e resolve os nomes das turmas para respostas, listagem e histórico (`findAll` com os ids da página, uma consulta).
- *Alternativas*: (a) métodos novos no `ClassroomService` — devolvem `ClassroomOverview` com contagens, e o `ClassroomService` depende do `ClassroomStudentCounter`, que o `child` implementa: ficaria um ciclo de beans `ChildService → ClassroomService → ActiveChildClassroomCounter`, inofensivo hoje mas frágil; (b) join SQL do `child` com a tabela `classrooms` — o `child` passaria a conhecer o schema de outro módulo, que as regras de módulos tentam evitar.

### D4. Casos de uso do `ChildService`

- **Data de hoje**: a mesma `today()` já usada para a data de nascimento (`LocalDate.now(clock.withZone(America/Sao_Paulo))`), compartilhada pela abertura e pelo encerramento dos vínculos.
- **`create(details, classroomId)`**: valida a turma pelo `ClassroomLookup` **antes** de gravar a criança (nada é gravado se a turma não existir), grava a criança como hoje (verificações de CPF/RG/certidão e `saveAndFlush`), depois `ChildClassroom.open(child.getId(), classroomId, today)` com `saveAndFlush`. Uma transação só.
- **`update(id, details, classroomId)`**:
  1. carrega a criança **com lock pessimista** (`ChildRepository.findByIdForUpdate`, `@Lock(PESSIMISTIC_WRITE)`), para serializar alterações simultâneas da mesma criança;
  2. valida a turma pelo `ClassroomLookup`;
  3. aplica os dados e as verificações de unicidade, como hoje;
  4. lê o vínculo ativo (`findByChildIdAndEndDateIsNull`); se a turma for a mesma, não faz nada; se for outra, `close(today)` + **`saveAndFlush`** do vínculo antigo e só depois `saveAndFlush` do novo.
  - **Por que o flush entre os dois**: o Hibernate executa os `INSERT`s antes dos `UPDATE`s no flush; sem o flush intermediário, o novo vínculo ativo seria inserido enquanto o antigo ainda está sem data de fim, e o índice `child_classrooms_active_key` recusaria.
  - **Por que o lock na criança e não no vínculo**: com `SELECT … FOR UPDATE` no vínculo ativo, a segunda transação concorrente, ao ganhar o lock, reavaliaria `end_date IS NULL` sobre a linha já encerrada e não acharia vínculo ativo. Travando a linha da criança, a leitura do vínculo ativo acontece depois do lock e enxerga o estado já gravado pela primeira (cada comando do `READ COMMITTED` lê um snapshot novo).
  - Qualquer exceção (`400`, `404`, `409`) desfaz a transação inteira, então uma alteração recusada não mexe nos vínculos.
- **Turma excluída no meio da operação**: se a turma for excluída entre a validação e o `INSERT` do vínculo, a FK `child_classrooms_classroom_id_fkey` falha com SQLState `23503`; o `ChildService` converte essa violação em `ClassroomNotFoundForChildException` (mesmo `400` da turma inexistente). Outras violações continuam relançadas.
- **`findById` / respostas completas**: carrega a criança, o vínculo ativo e o `ClassroomRef` → `ChildView(child, classroomRef)` para o mapper. Toda criança tem vínculo ativo (invariante); a ausência é bug e lança `IllegalStateException`.
- **`history(id)`**: 404 se a criança não existir; senão `findHistory(childId)` ordenado no banco (D6) + `ClassroomLookup.findAll` com os ids distintos → `List<ChildClassroomView>`.
- **`delete(id)`**: inalterado; o `ON DELETE CASCADE` apaga os vínculos. Como o Hibernate não sabe do cascade, o service não carrega vínculos antes de excluir (nenhuma entidade `ChildClassroom` gerenciada fica órfã na sessão).

### D5. Contagens e exclusão de turma

- **`ActiveChildClassroomCounter`** (`child/application`, `@Component`) implementa `ClassroomStudentCounter` e substitui o `NoEnrollmentStudentCounter`, que é **removido**. As contagens vêm de uma query nativa no `ChildClassroomRepository`:

  ```sql
  SELECT cc.classroom_id AS classroomId,
         count(*) AS total,
         count(*) FILTER (WHERE c.gender = 'MALE') AS male,
         count(*) FILTER (WHERE c.gender = 'FEMALE') AS female
  FROM child_classrooms cc
  JOIN children c ON c.id = cc.child_id
  WHERE cc.end_date IS NULL AND cc.classroom_id IN (:classroomIds)
  GROUP BY cc.classroom_id
  ```

  numa projeção de interface, convertida em `Map<UUID, StudentCounts>`; `countFor(UUID)` reaproveita a mesma query com um id. Lista vazia de ids → mapa vazio sem ir ao banco (`IN ()` é inválido). Uma consulta para a lista toda de turmas (RNF03).
- **Histórico na exclusão de turma**: a porta `ClassroomStudentCounter` ganha `boolean hasPastStudents(UUID classroomId)` ("alguma criança já saiu desta turma"), implementado com `existsByClassroomIdAndEndDateIsNotNull`. `ClassroomService.delete`: `countFor(id).hasStudents()` → `ClassroomHasStudentsException`; senão `hasPastStudents(id)` → nova `ClassroomHasHistoryException` (`ConflictException`, "A turma tem histórico de crianças e não pode ser excluída").
  - *Alternativa*: uma porta nova (`ClassroomUsage`). Descartada por ora: são duas perguntas sobre as crianças da turma, e o nome da porta e de `StudentCounts` não muda por decisão da tarefa.
- **Corrida na exclusão**: se um vínculo for criado entre a verificação e o `DELETE`, a FK `ON DELETE RESTRICT` recusa. O `ClassroomService.delete` passa a usar `delete` + `flush` e converte **qualquer** violação de FK (SQLState `23503`) em `ClassroomHasStudentsException`, sem conhecer o nome da constraint da tabela do `child` (toda FK que aponta para `classrooms` significa "a turma está em uso").
- Mensagem de `ClassroomHasStudentsException`: "A turma tem crianças. Tire as crianças da turma antes de excluí-la". O Javadoc de `ClassroomStudentCounter`, `StudentCounts` e `ClassroomService` passa a falar de crianças e do vínculo, sem renomear os tipos.

### D6. Migration `V9__create_child_classrooms.sql`

```sql
-- Vínculo da criança com a turma, com histórico. Exatamente um vínculo ativo (end_date IS NULL) por criança.
-- created_by/updated_by sem FK para users e NOT NULL: todo vínculo é gravado por uma requisição autenticada.
CREATE TABLE child_classrooms (
    id            uuid         PRIMARY KEY,
    child_id      uuid         NOT NULL REFERENCES children (id) ON DELETE CASCADE,
    classroom_id  uuid         NOT NULL REFERENCES classrooms (id) ON DELETE RESTRICT,
    start_date    date         NOT NULL,
    end_date      date,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    created_by    uuid         NOT NULL,
    updated_by    uuid         NOT NULL,
    CONSTRAINT child_classrooms_dates_check CHECK (end_date IS NULL OR end_date >= start_date)
);

-- Um único vínculo ativo por criança.
CREATE UNIQUE INDEX child_classrooms_active_key ON child_classrooms (child_id) WHERE end_date IS NULL;

-- Histórico da criança e "a turma já teve crianças" (FK de classroom_id usada no DELETE da turma).
CREATE INDEX child_classrooms_child_id_idx ON child_classrooms (child_id, start_date DESC);
CREATE INDEX child_classrooms_classroom_id_idx ON child_classrooms (classroom_id);

-- Contagens e filtro da listagem pela turma atual.
CREATE INDEX child_classrooms_active_classroom_idx ON child_classrooms (classroom_id) WHERE end_date IS NULL;
```

- Os nomes das FKs ficam os padrão do PostgreSQL (`child_classrooms_child_id_fkey`, `child_classrooms_classroom_id_fkey`).
- O índice em `classroom_id` (completo) evita scan sequencial no `ON DELETE RESTRICT` e no `hasPastStudents`; o parcial atende as contagens e o filtro. Volume pequeno; os índices custam pouco.
- **"Toda criança tem vínculo ativo"** não é garantido pelo banco (exigiria trigger ou constraint adiada); a garantia é a transação do `create` e do `update`, coberta por testes. O banco garante o "no máximo um".
- `start_date`/`end_date` são `date` (sem hora): a regra é por dia, no fuso da instituição. Nenhuma migration existente é editada.

### D7. Listagem com filtro por turma

- A listagem passa a usar `JpaSpecificationExecutor<Child>` com `ChildSpecifications`: `nameContains(pattern)` (`lower(immutable_unaccent(name)) like lower(immutable_unaccent(:pattern)) escape '\'`, via `cb.function`) e `inClassroom(classroomId)` (subquery `exists` em `ChildClassroom` com `childId = child.id`, `classroomId = :id` e `endDate is null`). Filtros nulos não entram. Substitui o `searchByName` e o `findAll` do `list`.
  - *Alternativa*: uma `@Query` JPQL com `(:param is null or …)`. Descartada: parâmetro `UUID` nulo em comparação exige cast no PostgreSQL, e a query fica menos legível que as duas specifications.
- Depois da página: `findActiveByChildIdIn(ids)` (uma consulta) e `ClassroomLookup.findAll(classroomIds)` (uma consulta) → `ChildSummaryView`. Total por requisição: página + count + vínculos + turmas = 4 consultas, para qualquer `size` (RNF02: até 1.000 crianças em 500 ms p95 na VM é folgado).
- Sort continua `{name, birthDate, createdAt}`; `classroomId` malformado é `400` pelo binding do Spring (`MethodArgumentTypeMismatchException`), com teste.

### D8. Histórico ordenado

`ChildClassroomRepository.findHistory(childId)`: `order by startDate desc, endDate desc nulls first, createdAt desc` (JPQL com `nulls first`). Atende a spec: o ativo vem primeiro entre os do mesmo dia, depois os encerrados mais recentes; `createdAt` só desempata trocas repetidas no mesmo dia.

### D9. Contrato e testes que mudam

- `openapi/educare-api.json` regenerado com `./gradlew updateApiContract`: `classroomId` obrigatório em `ChildRequest`, `classroom` obrigatório nas duas respostas de criança, `ChildClassroomResponse` com `endDate` opcional, a nova operação de histórico (`@BadRequestResponse`, `@NotFoundResponse`, `@ResponseStatus(OK)`), o parâmetro `classroomId` na listagem e `@ConflictResponse` já existente no `DELETE` de turma. O `ApiContractContentTest` verifica a operação nova, o `classroom` obrigatório e que as leituras de turma **não** declaram `403`.
- Testes existentes afetados: `ChildScenarioTest`, `ChildControllerTest`, `ChildServiceTest`, `ChildMapperTest` e `ChildRepositoryTest` passam a criar crianças com turma (um helper em `shared/testsupport` ou no próprio teste para criar a turma e o vínculo); `ClassroomControllerTest`/`ClassroomScenarioTest`/`ClassroomServiceTest` ajustados ao acesso de leitura, às mensagens e ao `409` de histórico; o cenário "USER não lista turmas" sai junto com o requirement removido.

## Risks / Trade-offs

- [Criança sem vínculo ativo por bug] O banco não impede uma criança sem vínculo → Transação única no `create`/`update`, `IllegalStateException` explícita ao montar a resposta, e um teste de persistência que confere que toda criança criada pelo service tem um vínculo ativo.
- [Lock pessimista na alteração] Duas alterações simultâneas da mesma criança esperam uma pela outra → Raro (poucos funcionários) e curto; evita o `409`/`500` que a corrida no índice parcial causaria.
- [Porta `ClassroomStudentCounter` crescendo] `hasPastStudents` não é uma "contagem" → Aceito para não renomear tipos nesta tarefa; quando a matrícula existir, a porta pode virar `ClassroomUsage`.
- [FK de `child_classrooms` para `classrooms`] O schema do `child` referencia a tabela de outro módulo → É a natureza do vínculo e garante a integridade; o código do `classroom` não conhece a tabela (a conversão no `delete` é por SQLState, não por nome).
- [Exclusão de turma bloqueada para sempre por histórico] Uma turma usada uma vez nunca mais pode ser excluída → Decisão de produto (preservar o histórico); "arquivar turma" fica como pergunta em aberto da tarefa.

## Migration Plan

- Pré-requisito: `add-child-crud` aplicada no código (migration `V8`).
- **Deploy conjunto com `add-child-crud`**: esta change é a que libera a publicação da imagem. Os dois PRs entram na `main` sem tag entre eles; o label `build` vai no PR desta change (ou uma tag `vX.Y.Z` é criada depois do merge dela). Assim a primeira imagem com crianças já exige turma.
- A `V9` roda no início da aplicação e só cria a tabela e os índices; compatível com a versão em produção (que não tem `children` nem `child_classrooms`), e o rolling update não quebra o pod antigo.
- Em produção não há crianças antes desta change (a tabela `children` nasce junto), então não é preciso preencher vínculos para registros existentes.
- Rollback: voltar a imagem para a versão anterior às duas changes; as tabelas `children` e `child_classrooms` ficam no banco, ignoradas pelo `validate` da versão antiga. Se houver crianças cadastradas, elas continuam lá para quando a versão nova voltar.
- Depois do deploy: validar na Oracle Cloud que as migrations `V8` e `V9` foram aplicadas (DoD da tarefa) — passo manual da Manuela.
