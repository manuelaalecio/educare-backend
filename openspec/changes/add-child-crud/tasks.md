# Tasks

## 1. Pré-requisito

- [ ] 1.1 Confirmar que a change `add-record-authorship` já foi aplicada: `BaseEntity` tem `createdBy`/`updatedBy`, existe `src/main/resources/db/migration/V4__add_authorship_to_users.sql` e `shared/testsupport/AuthenticatedAs`; verificar com `./gradlew test --tests '*JpaAuditingConfigurationTest'`. Se não estiver aplicada, parar e aplicar aquela change primeiro

## 2. Schema

- [ ] 2.1 Criar a nova migration `src/main/resources/db/migration/V5__create_children.sql` (extensão `unaccent`, tabela `children` com as colunas em `snake_case`, `address_complement` nullable, `city` e `state` obrigatórias, `created_by`/`updated_by` `uuid NOT NULL` sem FK, tamanhos e constraints `children_cpf_key`, `children_rg_key`, `children_cep_check`, `children_cpf_check`, `children_rg_check`, `children_state_check`, `children_race_check`, `children_gender_check` e `children_marital_status_check` com os valores em inglês, conforme D5), sem editar migrations existentes; verificar com `./gradlew test --tests '*EducareBackendApplicationTests*'` que o contexto sobe com a migration aplicada

## 3. Erros de desserialização em `shared/error`

- [ ] 3.1 Sobrescrever `handleHttpMessageNotReadable` no `GlobalExceptionHandler` (D6), estendendo o `GlobalExceptionHandlerTest` para: data em formato inválido num campo gera `400` com `errors` para esse campo; tipo errado num campo (ex.: objeto onde se espera texto) gera `errors` para o campo; campo aninhado usa o caminho com `.`; a mensagem não contém o valor recebido; JSON quebrado e corpo ausente continuam `400` sem `errors`. Verificar com `./gradlew test --tests '*GlobalExceptionHandlerTest' --tests '*UserControllerTest' --tests '*UserScenarioTest'` (o `user` continua verde)

## 4. Domínio do módulo `child`

- [ ] 4.1 Criar `child/domain/Cpf` e `child/domain/Cep` (`normalize` e `isValid`) e `child/domain/Rg` (`normalize`) (D2/D3), com `CpfTest`, `CepTest` e `RgTest` cobrindo: CPF com e sem máscara válidos (`529.982.247-25`, `11144477735`), dígito verificador errado (`529.982.247-24`), dígitos repetidos (`11111111111`), máscara parcial, letras, tamanho errado, nulo; CEP com e sem hífen, com 7 dígitos, com letras, nulo; RG com pontos, hífen e espaços, letra minúscula virando maiúscula (`12.345.678-x` → `12345678X`), só pontuação virando vazio, nulo. Verificar com `./gradlew test --tests '*CpfTest' --tests '*CepTest' --tests '*RgTest'`
- [ ] 4.2 Criar os enums `Race` (`WHITE`, `BLACK`, `BROWN`, `YELLOW`, `INDIGENOUS`, com o Javadoc da correspondência com o IBGE), `Gender`, `MaritalStatus` e `BrazilianState` (valores da spec), o record `ChildDetails`, a entidade `Child` (estende `BaseEntity`, `@Table(name = "children")`, `@Column` com os mesmos tamanhos da migration, enums com `@Enumerated(EnumType.STRING)`, `Child(ChildDetails)` e `update(ChildDetails)`, normalização idempotente e invariantes com `IllegalArgumentException`, D2) e as exceções `ChildNotFoundException`, `CpfAlreadyInUseException` (mensagem sem o CPF), `RgAlreadyInUseException` (mensagem sem o RG) e `BirthDateInFutureException` (campo `birthDate`). Testes em `child/domain/ChildTest`:
  - strip de todos os textos, opcional em branco virando `null` (inclusive `addressComplement`), `cep` e `cpf` gravados só com dígitos, `rg` só com letras e dígitos em maiúsculas;
  - rejeição de cada obrigatório nulo ou em branco (incluindo `city` e `state` nulos, e não `addressComplement`), de `birthDate` nula, de textos acima do limite (e aceitação no limite), de `cep` e `cpf` inválidos e de `rg` sem letra nem dígito;
  - `update` substituindo todos os campos e apagando opcionais ausentes;
  - mensagens das exceções sem dados pessoais.

  Verificar com `./gradlew test --tests '*ChildTest'` e com o contexto subindo (`ddl-auto: validate`)
- [ ] 4.3 Criar `child/domain/ChildRepository` (`existsByCpf`, `existsByCpfAndIdNot`, `existsByRg`, `existsByRgAndIdNot` e `searchByName` com `@Query` JPQL usando `function('unaccent', …)`, `lower` e `escape '\'`, D4), com `child/domain/ChildRepositoryTest` (`@DataJpaTest` + `@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class, ClockConfiguration.class})`, gravações dentro de `AuthenticatedAs`) cobrindo:
  - `searchByName` ignorando acento e caixa (`%joao%` acha `João Pedro` e `Maria João`, não `Joana Lima`), com curinga escapado (`%\%%` não acha nada) e paginado/ordenado;
  - as queries de CPF e de RG, incluindo o próprio id excluído;
  - `children_cpf_key` e `children_rg_key` recusando valor repetido e aceitando várias linhas com o campo nulo;
  - os `CHECK`s de RG, UF, raça, gênero, estado civil, CEP e CPF recusando valores inválidos inseridos via `JdbcTemplate`;
  - `created_by`/`updated_by` preenchidos com o usuário do `AuthenticatedAs`, e gravação sem usuário recusada pelo `NOT NULL`;
  - enums gravados como texto e lidos de volta.

  Verificar com `./gradlew test --tests '*ChildRepositoryTest'`

## 5. Casos de uso

- [ ] 5.1 Criar `child/application/ChildService` (`create`, `findById`, `list(name, pageable)`, `update`, `delete`; `@Transactional` na classe e `readOnly` nas consultas; data futura comparada com a data atual em `America/Sao_Paulo` a partir do bean `Clock`; padrão da busca com strip, escape de `\`, `%` e `_` e `%…%`; nome nulo ou em branco usando `findAll`; verificação prévia do CPF e do RG + `saveAndFlush` convertendo a violação de `children_cpf_key` em `CpfAlreadyInUseException`, a de `children_rg_key` em `RgAlreadyInUseException` e relançando as demais, D2/D3/D4), com `child/application/ChildServiceTest` (Mockito, `Clock` fixo) cobrindo:
  - caminho feliz de cada método;
  - `ChildNotFoundException` em `findById`/`update`/`delete`;
  - `birthDate` igual a hoje aceita, amanhã recusada, e a fronteira de fuso (`2026-03-02T01:00:00Z` recusando `2026-03-02`);
  - conflito de CPF e de RG na verificação prévia (criação e alteração), na corrida (violação de `children_cpf_key` e de `children_rg_key` no flush) e outra violação sendo relançada;
  - alteração mantendo o próprio CPF e o próprio RG em outra pontuação;
  - busca: nome nulo, em branco, com espaços nas pontas e com `%`, `_` e `\` escapados.

  Verificar com `./gradlew test --tests '*ChildServiceTest'`

## 6. API

- [ ] 6.1 Criar os DTOs `ChildRequest` (compact constructor com strip e vazio → `null`; `@NotBlank` nos obrigatórios, `@NotNull` no `birthDate`, `@Size`, `@Pattern` do CEP, do RG, da UF e das listas fixas em inglês, D2), `ChildResponse` e `ChildSummaryResponse` (sem autoria), a constraint `ValidCpf` com o `ValidCpfValidator` (delegando a `Cpf.isValid`, nulo válido) e o `ChildMapper` (`ChildRequest` → `ChildDetails`, `Child` → `ChildResponse`/`ChildSummaryResponse`). Testes: `ValidCpfValidatorTest`, `ChildMapperTest` (conversão de todos os campos, incluindo `state`, e enums opcionais nulos e preenchidos) e um teste da normalização do `ChildRequest` (textos com espaços, opcional em branco → `null`, `state` em minúsculas convertido para maiúsculas, nome de 150 caracteres com espaços nas pontas válido). Verificar com `./gradlew test --tests '*ValidCpf*' --tests '*ChildMapperTest' --tests '*ChildRequest*'`
- [ ] 6.2 Criar `child/api/ChildController` (`/api/v1/children`, endpoints da D1, `Location` na criação, `SORTABLE_PROPERTIES = {name, birthDate, createdAt}`, `@PageableDefault(size = 20, sort = "name")`, parâmetro opcional `name`, `@PreAuthorize("hasRole('ADMIN')")` só no `delete`), com `child/api/ChildControllerTest` (`@WebMvcTest`, segurança importada, `AuthenticatedUserLookup` mockado, tokens de `AccessTokens`) cobrindo, para cada endpoint: sucesso e corpo (sem propriedades de autoria), `400` com `errors` (obrigatórios, tamanhos, CEP, CPF, RG sem letra nem dígito, UF, listas fixas, data futura, data malformada e inexistente), `400` sem `errors` para JSON ilegível, `400` para id malformado e sort não permitido, `404`, `409` sem o CPF ou o RG no corpo, `401` sem token, `USER` criando/alterando com sucesso e recebendo `403` no `DELETE`, e itens da listagem só com `id`, `name`, `birthDate`, `createdAt` e `updatedAt`. Verificar com `./gradlew test --tests '*ChildControllerTest'` e com `./gradlew test --tests '*ArchitectureTest'` (regras entre módulos agora com dois módulos)

## 7. Testes de cenário

- [ ] 7.1 Criar `child/ChildScenarioTest` (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, usuários `ADMIN` e `USER` criados com `TestUsers`, `MutableClock` para as datas, tabela `children` limpa no `@BeforeEach`, autoria lida de `created_by`/`updated_by` via `JdbcTemplate`) com um teste por Scenario de `specs/child/spec.md`, cada um com `@DisplayName` referenciando o requirement e o cenário; verificar com `./gradlew test --tests '*ChildScenarioTest'` que todos passam e que o número de testes é igual ao número de Scenarios da spec

## 8. Verificação final

- [ ] 8.1 Rodar `./gradlew build` e verificar que passa, incluindo a verificação de cobertura do JaCoCo (mínimo de 90% de linhas e de branches)
