# Tasks

## 1. Pré-requisito

- [ ] 1.1 Confirmar que `add-child-crud` foi aplicada: existem o pacote `child/`, a migration `V8__create_children.sql` e o `ChildScenarioTest`. Verificar com `./gradlew test --tests '*ChildScenarioTest' --tests '*ChildRepositoryTest'`. Se não estiver aplicada, parar e aplicar aquela change primeiro

## 2. Schema

- [ ] 2.1 Criar a nova migration `src/main/resources/db/migration/V9__create_child_classrooms.sql` conforme a D6 (tabela `child_classrooms` com FKs `ON DELETE CASCADE`/`ON DELETE RESTRICT`, `child_classrooms_dates_check`, autoria `NOT NULL` sem FK com o motivo comentado, índice único parcial `child_classrooms_active_key` e os índices de histórico, de turma e de turma ativa), sem editar migrations existentes. Verificar com `./gradlew test --tests '*EducareBackendApplicationTests*'` que o contexto sobe

## 3. Consulta de turmas no `classroom`

- [ ] 3.1 Criar `classroom/application/ClassroomLookup` (`find(UUID)` e `findAll(Collection<UUID>)` numa consulta, `readOnly`) e o record `ClassroomRef` (D3), com `ClassroomLookupTest` (Mockito: turma existente, inexistente, vários ids com um inexistente, coleção vazia sem ir ao repositório). Verificar com `./gradlew test --tests '*ClassroomLookupTest' --tests '*ArchitectureTest'`

## 4. Domínio do vínculo no `child`

- [ ] 4.1 Criar a entidade `child/domain/ChildClassroom` (estende `BaseEntity`, `@Table(name = "child_classrooms")`, `childId`, `classroomId`, `startDate`, `endDate`, `open` e `close` com as invariantes da D2) e a exceção `ClassroomNotFoundForChildException` (`InvalidFieldException`, campo `classroomId`, mensagem "Turma não encontrada"), com `ChildClassroomTest`: abertura sem data de fim; encerramento no mesmo dia (início = fim) e em dia posterior; encerramento antes do início recusado; encerrar vínculo já encerrado recusado; argumentos nulos recusados. Verificar com `./gradlew test --tests '*ChildClassroomTest'` e o contexto subindo (`ddl-auto: validate`)
- [ ] 4.2 Criar `child/domain/ChildClassroomRepository` (`findByChildIdAndEndDateIsNull`, `findActiveByChildIdIn`, `findHistory` com a ordem da D8, `existsByClassroomIdAndEndDateIsNotNull` e a query nativa de contagens da D5 com projeção) e acrescentar `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) e `JpaSpecificationExecutor<Child>` ao `ChildRepository`, com `ChildSpecifications` (`nameContains`, `inClassroom`, D7). Testes em `ChildClassroomRepositoryTest` (`@DataJpaTest` + Testcontainers, `AuthenticatedAs`, turmas e crianças inseridas no teste) cobrindo:
  - `child_classrooms_active_key` recusando um segundo vínculo ativo e aceitando vários encerrados;
  - `child_classrooms_dates_check` recusando fim antes do início via `JdbcTemplate`;
  - `ON DELETE CASCADE` ao excluir a criança e `ON DELETE RESTRICT` (SQLState `23503`) ao excluir turma com vínculo;
  - `findHistory` na ordem da spec, inclusive duas trocas no mesmo dia;
  - contagens: 2 `MALE`, 1 `FEMALE`, 1 sem gênero ativos e 1 encerrada → `4/2/1`, várias turmas numa consulta, turma sem vínculos ausente do resultado;
  - `existsByClassroomIdAndEndDateIsNotNull` com só vínculos ativos (false) e com um encerrado (true);
  - `ChildSpecifications` em `ChildRepositoryTest`: filtro por turma atual (excluindo quem saiu), combinado com o nome, e os casos de nome que antes usavam `searchByName` (acento, caixa, curinga escapado), que é removido.

  Verificar com `./gradlew test --tests '*ChildClassroomRepositoryTest' --tests '*ChildRepositoryTest'`

## 5. Casos de uso do `child`

- [ ] 5.1 Alterar `child/application/ChildService` conforme a D4 e a D7: `create(details, classroomId)` validando a turma antes de gravar e abrindo o vínculo; `update(id, details, classroomId)` com `findByIdForUpdate`, troca de turma com `saveAndFlush` do vínculo encerrado antes do novo, e nada no vínculo para a mesma turma; conversão do SQLState `23503` da FK de turma em `ClassroomNotFoundForChildException`; `findById` devolvendo `ChildView`; `list(name, classroomId, pageable)` com specifications e as duas consultas em lote; novo `history(id)`. Criar os records `ChildView`, `ChildSummaryView` e `ChildClassroomView`. Ajustar o `ChildServiceTest` (Mockito, `Clock` fixo) para:
  - criação abrindo vínculo com a data de hoje em `America/Sao_Paulo` (inclusive a fronteira `2026-11-02T01:00:00Z` → `2026-11-01`); turma inexistente sem gravar nada;
  - alteração com a mesma turma sem tocar no vínculo; com outra turma encerrando e abrindo na ordem certa; turma inexistente sem alterar nada; conflito de CPF sem alterar o vínculo; violação da FK de turma convertida e outra violação relançada;
  - `findById` com a turma atual e `IllegalStateException` sem vínculo ativo;
  - listagem com e sem filtros, montando `classroom` de cada item com uma única chamada ao `ClassroomLookup`;
  - `history` com nomes resolvidos e `ChildNotFoundException`.

  Verificar com `./gradlew test --tests '*ChildServiceTest'`
- [ ] 5.2 Criar `child/application/ActiveChildClassroomCounter` (implementa `ClassroomStudentCounter`, inclusive o novo `hasPastStudents`, D5) e remover `classroom/application/NoEnrollmentStudentCounter` e o teste dele, com `ActiveChildClassroomCounterTest` (Mockito: conversão da projeção em `StudentCounts`, coleção vazia sem consulta, `countFor(UUID)` sem vínculos devolvendo `ZERO`, `hasPastStudents`). Verificar com `./gradlew test --tests '*ActiveChildClassroomCounterTest' --tests '*ArchitectureTest'` (o `classroom` continua sem depender do `child`)

## 6. Turmas: exclusão, mensagens e acesso

- [ ] 6.1 Acrescentar `hasPastStudents(UUID)` a `ClassroomStudentCounter`, criar `classroom/domain/ClassroomHasHistoryException` ("A turma tem histórico de crianças e não pode ser excluída"), trocar a mensagem de `ClassroomHasStudentsException` para "A turma tem crianças. Tire as crianças da turma antes de excluí-la", e alterar `ClassroomService.delete` (ativo → `ClassroomHasStudentsException`; só histórico → `ClassroomHasHistoryException`; `delete` + `flush` convertendo SQLState `23503` em `ClassroomHasStudentsException`, D5), atualizando os Javadocs de "alunos" para "crianças". Ajustar `ClassroomServiceTest`: os três caminhos da exclusão, a corrida na FK e outra violação relançada. Verificar com `./gradlew test --tests '*ClassroomServiceTest'`
- [ ] 6.2 No `ClassroomController`, mover `@PreAuthorize("hasRole('COORDENADOR')")` e `@ForbiddenResponse` da classe para `create`, `rename` e `delete` (D1). Ajustar `ClassroomControllerTest`: `EDUCADOR` recebe `200` em `GET /api/v1/classrooms` e `GET /api/v1/classrooms/{id}` e `403` em `POST`/`PUT`/`DELETE`; `409` de histórico com o `detail` novo; `401` sem token nas leituras. Verificar com `./gradlew test --tests '*ClassroomControllerTest'`

## 7. API do `child`

- [ ] 7.1 Acrescentar `classroomId` (`@NotNull`) ao `ChildRequest`, criar `ClassroomRefResponse` e `ChildClassroomResponse` (`endDate` com `@Nullable`), acrescentar `classroom` a `ChildResponse` e `ChildSummaryResponse`, e ajustar o `ChildMapper` para montar as respostas a partir das views. Ajustar `ChildMapperTest` (classroom nas duas respostas, histórico com e sem `endDate`) e o teste do `ChildRequest`. Verificar com `./gradlew test --tests '*ChildMapperTest' --tests '*ChildRequest*'`
- [ ] 7.2 Alterar `ChildController`: `classroomId` repassado em `create`/`update`, parâmetro opcional `classroomId` (`UUID`) na listagem e o novo `GET /{id}/classrooms` (`@ResponseStatus(HttpStatus.OK)`, `@BadRequestResponse`, `@NotFoundResponse`). Ajustar `ChildControllerTest`: `400` com `errors.classroomId` para ausente, malformado no corpo e turma inexistente (com a mensagem); `400` para `classroomId` malformado na query; `classroom` no corpo de cada resposta e de cada item da listagem; histórico `200`, `404`, `400` e `401`; `EDUCADOR` lendo o histórico. Verificar com `./gradlew test --tests '*ChildControllerTest'`
- [ ] 7.3 Rodar `./gradlew updateApiContract`, conferir no diff de `openapi/educare-api.json` os itens da D9 e atualizar o `ApiContractContentTest` (operação de histórico com `200`/`400`/`404`/`401`, `classroom` e `classroomId` obrigatórios, `endDate` opcional, leituras de turma sem `403`, `DELETE` de turma com `409`). Verificar com `./gradlew test --tests '*ApiContract*'`

## 8. Testes de cenário

- [ ] 8.1 Ajustar o `ChildScenarioTest` existente para criar as crianças com turma (turma criada no `@BeforeEach`, tabelas `child_classrooms`, `children` e `classrooms` limpas nessa ordem) e acrescentar um teste por Scenario dos requirements ADDED e MODIFIED de `specs/child/spec.md` desta change, com `@DisplayName` referenciando requirement e cenário e o `MutableClock` controlando as datas dos vínculos. Ajustar o `ClassroomScenarioTest` para os requirements MODIFIED e ADDED de `specs/classroom/spec.md` (cenários de exclusão, contagens e acesso, criando crianças pela API), removendo o teste do cenário "USER não lista turmas" junto com o requirement removido. Verificar com `./gradlew test --tests '*ChildScenarioTest' --tests '*ClassroomScenarioTest'` que todos passam e que cada Scenario das duas specs (depois de aplicar a delta) tem um teste

## 9. Verificação final

- [ ] 9.1 Rodar `./gradlew build` e verificar que passa, incluindo a verificação de cobertura do JaCoCo (mínimo de 90% de linhas e de branches); rodar também `./gradlew pitest` e conferir no mínimo 80% de mutantes mortos em `child` e `classroom` (`domain` e `application`)
