# Tasks

## 1. Schema

- [ ] 1.1 Conferir o próximo número livre em `src/main/resources/db/migration/` (hoje `V6`; `V7` se o `add-child-crud` já tiver sido implementado) e criar `V<n>__create_classrooms.sql` como na D7: `CREATE EXTENSION IF NOT EXISTS unaccent`, a função `immutable_unaccent(text)` (`IMMUTABLE PARALLEL SAFE STRICT`, com a função e o dicionário qualificados por `public`), a tabela `classrooms` (`name varchar(60) NOT NULL`, `created_by`/`updated_by` `uuid NOT NULL` sem FK) e o índice único `classrooms_name_key` em `lower(immutable_unaccent(name))`, com os comentários da D7 e sem editar nenhuma migration existente. Verificar com `./gradlew test --tests '*EducareBackendApplicationTests*'` que o contexto sobe com a migration aplicada

## 2. Domínio do módulo `classroom`

- [ ] 2.1 Criar a entidade `classroom/domain/Classroom` (estende `BaseEntity`, `@Table(name = "classrooms")`, `@Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)` com `NAME_MAX_LENGTH = 60`, `@Getter`, construtor protegido sem argumentos, `Classroom(String name)` e `rename(String name)` com strip e invariantes por `IllegalArgumentException`) e as exceções `ClassroomNotFoundException` ("Turma não encontrada"), `ClassroomNameAlreadyInUseException` ("Já existe uma turma com esse nome") e `ClassroomHasStudentsException`, como na D2. Testes em `classroom/domain/ClassroomTest`: strip na criação e na renomeação; nome nulo, vazio e só com espaços recusados; 60 caracteres aceitos (também com espaços nas pontas) e 61 recusados; mensagens das exceções sem o nome da turma. Verificar com `./gradlew test --tests '*ClassroomTest'` e com o contexto subindo (`ddl-auto: validate`)
- [ ] 2.2 Criar `classroom/domain/ClassroomRepository` com as queries nativas da D4/D5 (`existsByNormalizedName`, `existsByNormalizedNameAndIdNot`, `findAllOrderedByName`), todas com a mesma expressão `lower(immutable_unaccent(name))` do índice. Testes em `classroom/domain/ClassroomRepositoryTest` (`@DataJpaTest` + `@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, ClockConfiguration.class})`, gravações dentro de `AuthenticatedAs`), cobrindo:
  - `existsByNormalizedName` achando `Turma 1` por `turma 1`, `TÚRMA 1` e `Túrma 1`, e não achando `Turma 10`;
  - `existsByNormalizedNameAndIdNot` ignorando a própria turma e achando outra;
  - `findAllOrderedByName` na ordem dos scenarios da spec (`berçário`, `Turma 1`, `Turma 2`; `Educação Infantil`, `Ensino`, `Évora`);
  - o índice `classrooms_name_key` recusando um insert via `JdbcTemplate` de `TÚRMA 1` quando já existe `Turma 1`;
  - `created_by`/`updated_by` preenchidos com o usuário do `AuthenticatedAs`, e gravação sem usuário recusada pelo `NOT NULL`.

  Verificar com `./gradlew test --tests '*ClassroomRepositoryTest'`

## 3. Casos de uso

- [ ] 3.1 Criar em `classroom/application` os records `StudentCounts` (`total`, `male`, `female`, constante `ZERO`, Javadoc dizendo que `male`/`female` contam só `MALE`/`FEMALE`) e `ClassroomOverview`, a interface `ClassroomStudentCounter` (`countFor(UUID)` e `countFor(Collection<UUID>)`, D6) e o `@Component` `NoEnrollmentStudentCounter`, que sempre devolve zero (Javadoc dizendo que a change da matrícula o substitui). Testes em `NoEnrollmentStudentCounterTest`: `ZERO` para um id e mapa sem contagens diferentes de zero para uma coleção (inclusive vazia). Verificar com `./gradlew test --tests '*NoEnrollmentStudentCounterTest'`
- [ ] 3.2 Criar `classroom/application/ClassroomService` (`@Transactional` na classe e `readOnly` nas consultas; `create`, `list`, `findById`, `rename` e `delete` devolvendo `ClassroomOverview`; verificação prévia do nome; `saveAndFlush` convertendo a violação de `classrooms_name_key` em `ClassroomNameAlreadyInUseException` e relançando as demais; exclusão recusada com `ClassroomHasStudentsException` quando `total > 0`; listagem com uma única chamada `countFor(Collection)`, D4/D6). Testes em `classroom/application/ClassroomServiceTest` (Mockito), cobrindo:
  - caminho feliz de cada método, com as contagens vindas do counter;
  - `ClassroomNotFoundException` em `findById`, `rename` e `delete`;
  - conflito de nome na criação e na renomeação (verificação prévia) e na corrida (violação de `classrooms_name_key` no flush), e outra violação sendo relançada;
  - renomeação para uma variação do próprio nome aceita;
  - exclusão bloqueada com `total = 1`, liberada com `total = 0`, e `delete` do repositório não chamado quando bloqueada;
  - listagem com `ZERO` para a turma ausente do mapa, e o counter chamado uma vez só.

  Verificar com `./gradlew test --tests '*ClassroomServiceTest'`

## 4. API

- [ ] 4.1 Criar os DTOs `CreateClassroomRequest` e `UpdateClassroomRequest` (compact constructor com strip do `name`; `@NotBlank(message = "é obrigatório")` e `@Size(max = 60, message = "deve ter no máximo {max} caracteres")`, D3), `ClassroomResponse` (`id`, `name`, `studentCount`, `maleCount`, `femaleCount`, `createdAt` e `updatedAt`, sem autoria) e o `ClassroomMapper` (`ClassroomOverview` → `ClassroomResponse`). Testes em `ClassroomMapperTest` (todos os campos, incluindo as contagens) e num teste da validação dos requests (nome com 60 caracteres e espaços nas pontas válido, 61 inválido, só espaços inválido, `null` inválido). Verificar com `./gradlew test --tests '*ClassroomMapperTest' --tests '*ClassroomRequest*'`
- [ ] 4.2 Criar `classroom/api/ClassroomController` (`/api/v1/classrooms`, endpoints da D1, `@PreAuthorize("hasRole('ADMIN')")` e `@ForbiddenResponse` na classe, `Location` na criação, `@ResponseStatus(HttpStatus.OK)` nas operações com corpo `200`, e `@BadRequestResponse`, `@NotFoundResponse` e `@ConflictResponse` conforme a tabela da D1). Testes em `classroom/api/ClassroomControllerTest` (`@WebMvcTest`, segurança importada, `AuthenticatedUserLookup` mockado, tokens de `AccessTokens`), cobrindo para cada endpoint:
  - sucesso e corpo, sem `createdBy`/`updatedBy`;
  - `201` com `Location` e `200` com array (vazio e com itens);
  - `400` com `errors.name` (`{}`, vazio, só espaços, 61 caracteres) e `400` sem `errors` para JSON ilegível e id malformado;
  - `404`, `409` de nome em uso (sem o nome no corpo) e `409` de turma com alunos;
  - `403` para `USER` em todos os endpoints e `401` sem token.

  Verificar com `./gradlew test --tests '*ClassroomControllerTest'` e com `./gradlew test --tests '*ArchitectureTest'` (regras entre módulos, agora com `classroom`)
- [ ] 4.3 Rodar `./gradlew updateApiContract` e conferir no `openapi/educare-api.json` gerado que as cinco operações de `/api/v1/classrooms` declaram o status de sucesso (`201` com `Location`, `200` com array ou objeto, `204` sem `content`), os erros da D1 mais `401` e `403`, e que o schema `ClassroomResponse` tem os sete campos em `required` e não tem `createdBy`/`updatedBy`. Verificar com `./gradlew test --tests '*ApiContract*'`

## 5. Testes de cenário

- [ ] 5.1 Criar `classroom/ClassroomScenarioTest` (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, `ADMIN`s e `USER` criados com `TestUsers`, `MutableClock` para as datas, `classrooms` limpa no `@BeforeEach`, autoria lida de `created_by`/`updated_by` via `JdbcTemplate`) com um teste por Scenario de `specs/classroom/spec.md`, exceto "Turma com alunos não é excluída" e "Aluno de outro gênero conta só no total", cada um com `@DisplayName` referenciando o requirement e o scenario. Verificar com `./gradlew test --tests '*ClassroomScenarioTest'` que todos passam
- [ ] 5.2 Criar `classroom/ClassroomStudentCountScenarioTest` (`@SpringBootTest` com `@MockitoBean ClassroomStudentCounter`, D6) com os dois scenarios que dependem de alunos: `DELETE` respondendo `409` e a turma continuando com `"studentCount": 2`, e `GET` com `3/1/1`. Verificar com `./gradlew test --tests '*ClassroomStudentCountScenarioTest'`, e conferir que os testes das tasks 5.1 e 5.2 somados cobrem todos os Scenarios da spec

## 6. Documentação

- [ ] 6.1 Acrescentar `Turma -> Classroom` ao glossário do `context` em `openspec/config.yaml` e conferir que o arquivo continua válido com `openspec validate add-classroom-crud`

## 7. Verificação final

- [ ] 7.1 Rodar `./gradlew build` e verificar que passa, incluindo a verificação de cobertura do JaCoCo (mínimo de 90% de linhas e de branches)
