# Design

## Context

A motivação está em proposal.md (seção Why), e o comportamento em `specs/classroom/spec.md`.

O estado atual que define a abordagem:

- O módulo `user` é o modelo de CRUD restrito a `ADMIN`: `@PreAuthorize("hasRole('ADMIN')")` e `@ForbiddenResponse` na classe do controller, `UserMapper` em Java puro, DTOs `record` com Bean Validation, unicidade verificada antes de gravar e garantida pelo banco (`saveAndFlush` converte a `DataIntegrityViolationException` em `409`), e `@ResponseStatus(HttpStatus.OK)` nas operações com corpo, para o springdoc documentar o `200` junto com os erros.
- `shared/error` já tem `NotFoundException` (`404`), `ConflictException` (`409`) e o `GlobalExceptionHandler`. Bean Validation vira `400` com `errors`, e JSON ilegível ou UUID malformado viram `400` sem `errors`. Esta change não mexe em `shared`.
- `BaseEntity` tem `id` (UUID v7), `createdAt`/`updatedAt` (do bean `Clock`) e `createdBy`/`updatedBy` (do usuário autenticado). Nos testes, o `Clock` é um `MutableClock`, e `shared/testsupport/AuthenticatedAs` grava como um usuário fora de uma requisição.
- As migrations vão até `V5__add_password_changed_at_to_users.sql`, com `ddl-auto: validate`. A `V3` já usa uma extensão trusted (`pgcrypto`), criada pelo dono do database sem superusuário.
- A change `add-child-crud` está proposta, mas não implementada. Ela também cria a extensão `unaccent` e planeja uma `V5` que já está ocupada.
- No PostgreSQL, a função `unaccent` é `STABLE`, não `IMMUTABLE`, porque depende do `search_path` para achar o dicionário. Por isso, um índice de expressão sobre `unaccent(name)` é recusado. O índice `lower(unaccent(name))` que a tarefa do ClickUp descreve não funciona como está escrito.
- Ainda não existe matrícula (vínculo entre criança e turma). Quem vai fornecer as contagens e a regra de exclusão é um módulo que ainda não existe.

## Goals / Non-Goals

**Goals:**
- Terceiro módulo de negócio no mesmo padrão do `user`, sem mudança em `shared`.
- Nome único sem diferenciar maiúsculas e acentos, garantido pelo banco e não só pela aplicação.
- Deixar pronto, sem dependência entre módulos, o ponto em que a futura matrícula fornece as contagens e bloqueia a exclusão.

**Non-Goals:**
- Qualquer tabela ou entidade de matrícula, mesmo que provisória.
- Reaproveitar a busca sem acento na listagem de crianças: o `add-child-crud` decide se usa a função criada aqui.

## Decisions

### D1. Endpoints

| Método | Rota | Corpo | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/api/v1/classrooms` | `CreateClassroomRequest` | `201` + `Location` + `ClassroomResponse` | `400` validação/JSON, `409` nome em uso |
| `GET` | `/api/v1/classrooms` | — | `200` + `ClassroomResponse[]` | — |
| `GET` | `/api/v1/classrooms/{id}` | — | `200` + `ClassroomResponse` | `400` id malformado, `404` |
| `PUT` | `/api/v1/classrooms/{id}` | `UpdateClassroomRequest` | `200` + `ClassroomResponse` | `400`, `404`, `409` nome em uso |
| `DELETE` | `/api/v1/classrooms/{id}` | — | `204` | `400` id malformado, `404`, `409` turma com alunos |

Todos os endpoints exigem `ADMIN`: `@PreAuthorize("hasRole('ADMIN')")` e `@ForbiddenResponse` ficam na classe, como no `UserController`. A filter chain devolve `401` sem token, e o `UnauthorizedResponseCustomizer` documenta esse `401`. Os erros são declarados com `@BadRequestResponse`, `@NotFoundResponse` e `@ConflictResponse`, conforme a tabela. As operações com corpo `200` levam `@ResponseStatus(HttpStatus.OK)`.

- **`ClassroomResponse`**: `id` (UUID), `name`, `studentCount`, `maleCount` e `femaleCount` (`long`), `createdAt` e `updatedAt` (`Instant`). Todos os campos são obrigatórios no contrato; nenhum é `@Nullable`. O `updatedAt` não está na lista do ClickUp, mas entra para manter o padrão do `UserResponse`; o frontend pode ignorá-lo. Sem `createdBy` nem `updatedBy`.
- **Listagem sem paginação.** Isso foge do padrão do `openspec/config.yaml` (paginação do Spring Data nas listagens). O motivo: a escola tem poucas turmas (estimativa de até 50, pelo RNF01 do ClickUp), e a tela e os gráficos precisam de todas de uma vez. Por isso, não há `page`, `size` nem `sort`, e não existe `400` de sort inválido.
  - *Alternativa*: `PagedModel` com um `size` alto. Descartada porque obrigaria o frontend a lidar com páginas sem ganho nenhum.
- **`CreateClassroomRequest` e `UpdateClassroomRequest`** separados, seguindo a convenção do CLAUDE.md, mesmo sendo hoje iguais (só `name`). Com um campo só, não há risco real de os dois divergirem, ao contrário do caso do `ChildRequest`.

### D2. Módulo `classroom`

```
classroom/
├── api/
│   ├── ClassroomController.java
│   ├── ClassroomMapper.java            # ClassroomOverview -> ClassroomResponse
│   └── dto/  CreateClassroomRequest, UpdateClassroomRequest, ClassroomResponse
├── application/
│   ├── ClassroomService.java           # create, list, findById, rename, delete
│   ├── ClassroomOverview.java          # record (Classroom classroom, StudentCounts students)
│   ├── StudentCounts.java              # record (long total, long male, long female) + ZERO
│   ├── ClassroomStudentCounter.java    # interface: ponto de extensão da matrícula (D6)
│   └── NoEnrollmentStudentCounter.java # implementação provisória: sempre ZERO
└── domain/
    ├── Classroom.java                  # @Entity @Table(name = "classrooms"), estende BaseEntity
    ├── ClassroomRepository.java
    ├── ClassroomNotFoundException.java          # NotFoundException: "Turma não encontrada"
    ├── ClassroomNameAlreadyInUseException.java  # ConflictException: "Já existe uma turma com esse nome"
    └── ClassroomHasStudentsException.java       # ConflictException: "A turma tem alunos. Tire os alunos da turma antes de excluí-la"
```

- **Entidade `Classroom`**: `@Getter`, construtor protegido sem argumentos, `Classroom(String name)` e `rename(String name)`, sem setters. Como no `User`, ela garante a invariante com `IllegalArgumentException` (nome não nulo, não em branco e com no máximo `NAME_MAX_LENGTH = 60` caracteres depois do strip) e grava o valor com strip. Isso duplica de propósito a Bean Validation do DTO, que é quem produz o `400` com `errors`.
- **`ClassroomService`** devolve `ClassroomOverview`: a entidade junto com as contagens. Assim o mapper monta a resposta sem chamar outro serviço, e o controller nunca recebe a entidade sem as contagens.
- **Mensagens dos `409`**: a do nome é a mesma que o frontend mostra no campo, e nenhuma das duas repete o nome enviado.

### D3. Validação do nome no DTO

- O compact constructor dos dois requests faz o strip do `name` (com `null`, fica `null`). A Bean Validation roda sobre o record já construído, então `@NotBlank(message = "é obrigatório")` e `@Size(max = 60, message = "deve ter no máximo {max} caracteres")` avaliam o valor que vai ser gravado, como a spec pede ("60 caracteres depois de tirar os espaços das pontas"). É a mesma técnica do `ChildRequest` em `add-child-crud`.
  - *Alternativa*: validar o valor bruto, como o `CreateUserRequest`. Descartada porque `" " + 60 letras + " "` seria recusado, contrariando o scenario da spec.
- `@Size` conta unidades UTF-16, e o `varchar(60)` conta code points. Para caracteres fora do BMP (emoji), a aplicação é mais restritiva que o banco, nunca o contrário, então o banco nunca recusa por tamanho um nome que a validação aceitou.

### D4. Nome único sem diferenciar maiúsculas e acentos

- **Função imutável** criada na migration:
  ```sql
  CREATE FUNCTION immutable_unaccent(text) RETURNS text
      LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
      RETURN public.unaccent('public.unaccent'::regdictionary, $1);
  ```
  O dicionário e a função ficam com o schema explícito, então o resultado não depende do `search_path` e marcar a função como `IMMUTABLE` é seguro. Esse é o padrão usual para indexar `unaccent`. O corpo no formato SQL padrão (`RETURN`, PostgreSQL 14+) amarra as dependências na criação e dispensa `$$`.
- **Índice único** `classrooms_name_key ON classrooms (lower(immutable_unaccent(name)))`. Esse índice é a garantia real, inclusive contra duas criações simultâneas.
- **Verificação prévia** no service, com queries nativas no `ClassroomRepository` que usam a mesma expressão do índice: `existsByNormalizedName(name)` na criação e `existsByNormalizedNameAndIdNot(name, id)` na renomeação. Se o nome já existe, o service lança `ClassroomNameAlreadyInUseException`. Como na renomeação a própria turma fica de fora, mudar só as maiúsculas do próprio nome é aceito.
- **Corrida**: o service grava com `saveAndFlush`. Se vier uma `DataIntegrityViolationException` cuja constraint é `classrooms_name_key` (lida da `ConstraintViolationException` do Hibernate, como o `add-child-crud` faz com o CPF), ela vira `ClassroomNameAlreadyInUseException`. Qualquer outra violação é relançada, porque seria um bug e não um conflito.
- O `lower` usa o collation padrão do database, igual nas queries e no índice, então as duas comparações sempre concordam.
  - *Alternativa*: uma coluna `normalized_name` preenchida pela aplicação, com `UNIQUE` simples. Descartada porque duplica um dado e exige normalização Unicode em Java (`Normalizer` + regex) que precisaria concordar com a do banco.
  - *Alternativa*: collation ICU não determinística (`und-u-ks-level1`). Descartada porque o PostgreSQL não aceita `LIKE` nesses collations, o que atrapalharia buscas futuras, e o comportamento fica menos óbvio para quem lê o schema.

### D5. Ordem da listagem

`ClassroomRepository.findAllOrderedByName()` é uma query nativa: `SELECT * FROM classrooms ORDER BY lower(immutable_unaccent(name)), name, id`. A primeira chave dá a ordem sem diferenciar maiúsculas e acentos que a spec pede (`berçário` antes de `Turma 1`, `Évora` depois de `Ensino`). Como o índice impede dois nomes iguais nessa chave, as outras duas só deixam a ordem determinística.

- *Alternativa*: ordenar em Java com um `Collator` pt-BR. Descartada porque a ordem do banco já resolve, com no máximo algumas dezenas de linhas, e fica coerente com a regra de unicidade.

### D6. Contagens de alunos e bloqueio da exclusão

- **`ClassroomStudentCounter`** (interface em `classroom/application`):
  - `StudentCounts countFor(UUID classroomId)`;
  - `Map<UUID, StudentCounts> countFor(Collection<UUID> classroomIds)`: na listagem, faz uma consulta só, sem N+1. Uma turma que não aparece no mapa tem `StudentCounts.ZERO`.
- **`NoEnrollmentStudentCounter`** (`@Component`): a única implementação por enquanto, que sempre devolve zero. A futura change da matrícula cria a implementação real no módulo dela (que já vai depender de `classroom`, pela FK da turma) e remove esta. Assim, `classroom` nunca importa nada da matrícula, as regras do ArchUnit continuam valendo e não surge ciclo entre os módulos.
  - *Alternativa*: `classroom` chamar um service da matrícula. Descartada porque, quando a matrícula existir, ela vai chamar `classroom` para validar a turma, e as duas chamadas formariam um ciclo.
  - *Alternativa*: devolver as contagens fixas em zero no mapper, sem interface. Descartada porque deixaria a regra de exclusão sem lugar para ser testada agora, e a matrícula teria que mexer no service e no mapper.
- **Exclusão**: `delete(id)` carrega a turma (`404` se não existir), pergunta ao counter e lança `ClassroomHasStudentsException` quando `total > 0`. Senão, exclui.
- **Gêneros**: `male` e `female` contam só os gêneros `MALE` e `FEMALE` (valores do `Gender` de `add-child-crud`), e `total` conta todos os alunos. Isso fica documentado no Javadoc de `StudentCounts` para a implementação da matrícula.
- **Como testar sem matrícula**: o scenario "Turma com alunos não é excluída" e o "Aluno de outro gênero conta só no total" usam um teste de cenário próprio, que troca o counter por um mock (`@MockitoBean ClassroomStudentCounter`). O resto do caminho é real: HTTP, segurança, service e banco. Quando a matrícula existir, a change dela troca o mock por dados reais.

### D7. Migration `V6__create_classrooms.sql`

Usa o próximo número livre no momento da implementação (hoje `V6`; se o `add-child-crud` for implementado antes, `V7`).

```sql
-- unaccent é trusted no PostgreSQL 16: o dono do database a cria sem superusuário (como o pgcrypto da V3).
-- IF NOT EXISTS porque a change add-child-crud também a cria.
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent é STABLE (depende do search_path para achar o dicionário) e não pode ser usada num índice.
-- Com a função e o dicionário qualificados pelo schema, o resultado é sempre o mesmo e IMMUTABLE é seguro.
CREATE FUNCTION immutable_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN public.unaccent('public.unaccent'::regdictionary, $1);

-- Turmas. created_by/updated_by sem FK para users (excluir um usuário não apaga nem bloqueia a trilha)
-- e NOT NULL: toda turma é gravada por uma requisição autenticada.
CREATE TABLE classrooms (
    id          uuid         PRIMARY KEY,
    name        varchar(60)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    created_by  uuid         NOT NULL,
    updated_by  uuid         NOT NULL
);

-- Nome único sem diferenciar maiúsculas e acentos ("Turma 1", "turma 1" e "Túrma 1" colidem).
CREATE UNIQUE INDEX classrooms_name_key ON classrooms (lower(immutable_unaccent(name)));
```

Nenhuma migration existente é editada. A entidade mapeia `name` com `@Column(nullable = false, length = 60)`, que o `ddl-auto: validate` confere. O índice não é validado pelo Hibernate.

### D8. Testes

- **Unitários**:
  - `ClassroomTest`: strip, invariantes (nulo, em branco, 61 caracteres recusado e 60 aceito, contando sem os espaços das pontas) e `rename`.
  - `ClassroomServiceTest` (Mockito): conflito na criação e na renomeação, renomeação para o próprio nome, `DataIntegrityViolationException` de `classrooms_name_key` virando `409` e de outra constraint sendo relançada, `404` em `findById`/`rename`/`delete`, exclusão bloqueada com `total > 0`, listagem montando as contagens a partir do mapa e com `ZERO` para a turma ausente dele.
  - `NoEnrollmentStudentCounterTest` e `ClassroomMapperTest`.
- **Web** (`ClassroomControllerTest`, `@WebMvcTest` com a segurança importada, `AuthenticatedUserLookup` mockado e tokens de `AccessTokens`): `201` com `Location`, `200` com array, `204`, `400` com `errors.name` (vazio, espaços, 61 caracteres, `{}`), `400` com JSON ilegível e id malformado, `404`, os dois `409`, `403` para `USER` em cada endpoint e `401` sem token.
- **Persistência** (`ClassroomRepositoryTest`, `@DataJpaTest` + Testcontainers, importando `JpaAuditingConfiguration`, com gravações dentro de `AuthenticatedAs`):
  - `existsByNormalizedName*` com caixa e acento diferentes;
  - o índice `classrooms_name_key` recusando um insert direto com `TÚRMA 1` quando já existe `Turma 1`;
  - a ordem de `findAllOrderedByName`;
  - gravação sem usuário recusada pelo `NOT NULL` da autoria.
- **Cenário**:
  - `ClassroomScenarioTest` (`@SpringBootTest` + Testcontainers, `TestUsers`, `MutableClock`): um teste por Scenario da spec, exceto os dois que dependem de alunos. Limpa `classrooms` no `@BeforeEach`, e a autoria é lida com `JdbcTemplate`.
  - `ClassroomStudentCountScenarioTest`: os dois scenarios com alunos, com o counter mockado (D6).
- **Contrato**: `./gradlew updateApiContract` regenera `openapi/educare-api.json`. O `ApiContractScenarioTest` garante que o arquivo é igual ao documento gerado. Os requirements de `api-contract` são gerais e já valem para as novas operações, então nenhum scenario novo é necessário lá.
- **ArchUnit**: sem mudança. As regras entre módulos passam a valer também para `classroom`.

## Risks / Trade-offs

- [Contagens sempre zero até a matrícula] O frontend mostra "0 alunos" em todas as turmas, e toda exclusão passa → É o comportamento combinado na tarefa (RN05). O ponto de extensão (D6) evita retrabalho na matrícula.
- [Corrida entre exclusão e matrícula] Quando a matrícula existir, uma criança pode ser matriculada entre a contagem e o `DELETE` → Fica para a change da matrícula: a FK `classroom_id` com `ON DELETE RESTRICT` vai ser a garantia final, e essa change converte a violação em `ClassroomHasStudentsException`. Hoje a corrida não existe.
- [Função `immutable_unaccent` marcada `IMMUTABLE`] Se alguém trocar o dicionário `unaccent` no banco, o índice fica inconsistente → Ninguém mexe no dicionário. O risco está documentado no comentário da migration, e mudar o dicionário exigiria `REINDEX`.
- [Listagem sem paginação] Com muitas turmas, a resposta cresce → Volume esperado de algumas dezenas. Paginar depois seria uma mudança incompatível, com nova versão de rota ou coordenação com o frontend; aceitável pelo tamanho da escola.
- [Só `ADMIN` lista turmas] A matrícula e o /turmas do menu vão precisar que `USER` leia as turmas → A change que precisar disso afrouxa o `@PreAuthorize` do `GET` e atualiza o requirement de acesso.
- [Duas changes criam a extensão `unaccent`] → As duas usam `IF NOT EXISTS`. A função `immutable_unaccent` só é criada aqui, e o `add-child-crud` pode usá-la, se quiser, em vez de criar outra.

## Migration Plan

- Deploy normal: a `V6` roda no início da aplicação e cria a extensão (se ainda não existir), a função, a tabela vazia e o índice. Nenhum dado existente é alterado. Nenhuma variável de ambiente nova.
- Rollback: a versão anterior da aplicação ignora a tabela `classrooms`, porque o `validate` só confere as entidades que ela conhece. Basta voltar a imagem pela tag; a `V6` continua aplicada.

## Open Questions

- Se a turma passar a ter ano letivo (ex.: "Turma 1 · 2027"), o índice único muda para `(ano, nome normalizado)` numa nova migration. Isso não afeta esta change.
