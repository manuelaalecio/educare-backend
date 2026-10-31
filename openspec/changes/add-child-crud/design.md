# Design

## Context

Motivação em proposal.md (seção Why); comportamento em `specs/child/spec.md`.

Estado atual que molda a abordagem (considerando `add-record-authorship` já aplicada):

- O módulo `user` é o modelo de CRUD: controller com `@PreAuthorize`, `UserMapper` em Java puro, DTOs `record` com Bean Validation, enum recebido como `String` + `@Pattern` e convertido no mapper, unicidade por verificação prévia + `saveAndFlush` convertendo a `DataIntegrityViolationException`, sort restrito por `SORTABLE_PROPERTIES` e `PagedModel`.
- `shared/error` já tem `NotFoundException` (404), `ConflictException` (409), `InvalidFieldException` (400 com uma entrada em `errors`), `InvalidSortPropertyException` e o `GlobalExceptionHandler`, que estende `ResponseEntityExceptionHandler` (JSON ilegível e UUID malformado já viram `400` em `ProblemDetail`, sem `errors`).
- `shared/security` já exige token em toda rota fora das públicas e responde `401`/`403` em `ProblemDetail`; a role vem do banco a cada requisição.
- `BaseEntity` já tem `createdBy`/`updatedBy`, preenchidos pela auditoria a partir do usuário autenticado; `shared/testsupport/AuthenticatedAs` permite gravar como um usuário fora de requisição.
- `spring.data.web.pageable.max-page-size: 100` já está no `application.yaml`; `Clock` é um bean (`ClockConfiguration`), trocado por `MutableClock` nos testes.
- Migrations até `V4__add_authorship_to_users.sql`; `ddl-auto: validate`.
- `ArchitectureTest` já tem as regras entre módulos, que passavam trivialmente com um único módulo e passam a valer com `child`.

## Goals / Non-Goals

**Goals:**
- Segundo módulo de negócio seguindo o mesmo padrão do `user`; em `shared`, só a mudança de erros de desserialização no `GlobalExceptionHandler` (D6).
- Normalização dos textos antes da validação, para que o tamanho máximo e o "obrigatório" sejam avaliados sobre o valor que será gravado.

**Non-Goals:**
- Índice para a busca por nome (volume esperado de algumas centenas ou poucos milhares de crianças).
- `errors` para JSON sintaticamente quebrado: sem um campo identificável, continua `400` genérico.

## Decisions

### D1. Endpoints

| Método | Rota | Corpo / parâmetros | Sucesso | Erros | Acesso |
|---|---|---|---|---|---|
| `POST` | `/api/v1/children` | `ChildRequest` | `201` + `Location` + `ChildResponse` | `400` validação/JSON, `409` CPF ou RG em uso | autenticado |
| `GET` | `/api/v1/children` | `name?`, `page`, `size`, `sort` | `200` + `PagedModel<ChildSummaryResponse>` | `400` sort inválido | autenticado |
| `GET` | `/api/v1/children/{id}` | — | `200` + `ChildResponse` | `400` id malformado, `404` | autenticado |
| `PUT` | `/api/v1/children/{id}` | `ChildRequest` | `200` + `ChildResponse` | `400`, `404`, `409` | autenticado |
| `DELETE` | `/api/v1/children/{id}` | — | `204` | `400` id malformado, `403`, `404` | `ADMIN` |

Todos respondem `401` sem token válido (filter chain já existente).

- **Acesso**: o controller não tem `@PreAuthorize` na classe (o token já é exigido pela filter chain); só o método `delete` tem `@PreAuthorize("hasRole('ADMIN')")`.
- **Um único `ChildRequest` para criar e alterar**, em vez do par `CreateChildRequest`/`UpdateChildRequest` da convenção do CLAUDE.md. O `PUT` substitui a criança inteira e aceita exatamente os mesmos campos e regras da criação; dois records idênticos com 21 campos anotados cada tenderiam a divergir numa manutenção.
  - *Alternativa*: seguir a convenção ao pé da letra. Descartada pelo risco de divergência; se um dia os corpos diferirem, o record é separado.
- **`ChildSummaryResponse` na listagem** (`id`, `name`, `birthDate`, `createdAt`, `updatedAt`) e `ChildResponse` completo nos demais: a listagem é a tela mais acessada e não precisa de CPF, endereço nem dados de saúde (minimização, LGPD). Nenhuma das duas tem `createdBy`/`updatedBy`.
- **`PUT` e não `PATCH`**: o frontend sempre edita a ficha completa; opcional ausente apaga o valor.

### D2. Módulo `child`

```
child/
├── api/
│   ├── ChildController.java
│   ├── ChildMapper.java               # ChildRequest -> ChildDetails; Child -> ChildResponse / ChildSummaryResponse
│   ├── ValidCpf.java                  # constraint Bean Validation do CPF (formato + dígitos)
│   ├── ValidCpfValidator.java         # delega a Cpf.isValid do domínio
│   └── dto/  ChildRequest, ChildResponse, ChildSummaryResponse
├── application/
│   └── ChildService.java              # create, findById, list(name, pageable), update, delete
└── domain/
    ├── Child.java                     # @Entity @Table(name = "children"), estende BaseEntity
    ├── ChildDetails.java              # record com os dados editáveis (entrada de create/update)
    ├── Race.java, Gender.java, MaritalStatus.java
    ├── BrazilianState.java            # enum com as 27 siglas de UF (AC … TO)
    ├── Cpf.java, Cep.java             # utilitários: normalize(String) e isValid(String), sem estado
    ├── Rg.java                        # utilitário: normalize(String) (só letras e dígitos, maiúsculas)
    ├── ChildRepository.java
    ├── ChildNotFoundException.java    # estende NotFoundException
    ├── CpfAlreadyInUseException.java  # estende ConflictException, mensagem sem o CPF
    ├── RgAlreadyInUseException.java   # estende ConflictException, mensagem sem o RG
    └── BirthDateInFutureException.java # estende InvalidFieldException (campo birthDate)
```

- **Entidade `Child`**: `@Getter`, construtor protegido sem argumentos, sem setters. `Child(ChildDetails)` e `update(ChildDetails)` aplicam os dados de uma vez (a alteração é sempre completa). O domínio garante as invariantes com `IllegalArgumentException`: obrigatórios não nulos/não em branco, tamanhos máximos, `cep` e `cpf` válidos, `rg` com ao menos uma letra ou dígito. Também normaliza (strip, opcional em branco → `null`, `cep`/`cpf` só com dígitos, `rg` só com letras e dígitos em maiúsculas), de forma idempotente, para que outro chamador não grave um estado inválido. Como no `user`, isso duplica de propósito a Bean Validation do DTO, que é quem produz o `400` com `errors`. A autoria vem da `BaseEntity`, sem código no módulo.
- **`ChildDetails`** evita construtores e métodos com 21 parâmetros posicionais e é o contrato entre `application` e `domain`; o mapper da `api` o monta a partir do `ChildRequest`.
- **Enums em inglês**, nas constantes, na API e no banco: `Race` (`WHITE`, `BLACK`, `BROWN`, `YELLOW`, `INDIGENOUS`, na ordem das categorias do IBGE branca, preta, parda, amarela e indígena, registrada no Javadoc do enum), `Gender` (`MALE`, `FEMALE`, `OTHER`) e `MaritalStatus` (`SINGLE`, `MARRIED`, `STABLE_UNION`, `DIVORCED`, `WIDOWED`). O mapeamento fica em `valueOf`/`name()`, e o frontend traduz os rótulos. `BrazilianState` é exceção natural: as constantes são as siglas oficiais das UFs.
- **DTO `ChildRequest`**:
  - o compact constructor normaliza todos os textos com strip e converte vazio em `null` antes da Bean Validation, que roda sobre o record já construído. Assim `@NotBlank`/`@NotNull` e `@Size(max = …)` avaliam o valor que será gravado (spec: tamanho contado sem os espaços das pontas);
  - obrigatórios de texto (`name`, `cep`, `address`, `neighborhood`, `addressNumber`, `city`, `state`): `@NotBlank`; `addressComplement` é opcional, só com `@Size(max = 100)`;
  - `birthDate`: `LocalDate` com `@NotNull`; formato inválido ou data inexistente viram erro de desserialização, respondido com `400` e entrada em `errors` para `birthDate` pela D6;
  - `cep`: `@Pattern("^\\d{5}-?\\d{3}$")`;
  - `state`: o compact constructor também o converte para maiúsculas (`Locale.ROOT`), e um `@Pattern` com as 27 siglas o valida; o mapper converte com `BrazilianState.valueOf`;
  - `rg`: `@Pattern(".*[\\p{L}\\p{N}].*")` (ao menos uma letra ou dígito) além do `@Size(max = 20)`; a normalização fica no domínio (`Rg.normalize`), e o mapper passa o valor como veio;
  - `cpf`: `@ValidCpf`, uma só constraint para formato e dígitos (uma única entrada em `errors`); nulo é válido;
  - `race`, `gender`, `maritalStatus`: `String` com `@Pattern` listando os valores, pelo mesmo motivo do `role` no `user` (com o enum direto, um valor fora da lista viraria erro de desserialização, e a mensagem seria a do Jackson, não a nossa).
- **Data de nascimento futura**: a regra depende do relógio, então fica no `ChildService`, que compara com `LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo")))` e lança `BirthDateInFutureException` (`400` com entrada em `errors` para `birthDate`, pelo handler já existente de `InvalidFieldException`). O fuso é o da instituição, e não o do `Clock` (UTC), para que à noite a data de "hoje" não avance antes da hora.
  - *Alternativa*: `@PastOrPresent` no DTO. Descartada porque usa o relógio do validador, não o bean `Clock` (os testes não controlariam a data), e o fuso seria o da JVM.

### D3. CPF e RG

- `Cpf.isValid`: aceita `^\d{11}$` ou `^\d{3}\.\d{3}\.\d{3}-\d{2}$`, recusa 11 dígitos iguais e confere os dois dígitos verificadores (módulo 11). `Cpf.normalize` remove `.` e `-`.
- **Unicidade**: mesmo padrão do login no `user` — `existsByCpf` na criação e `existsByCpfAndIdNot` na alteração, lançando `CpfAlreadyInUseException` (409, `detail` "CPF já cadastrado", sem o número); o banco garante com `children_cpf_key UNIQUE (cpf)`, que permite vários `NULL`. O service grava com `saveAndFlush` e converte a `DataIntegrityViolationException` em `CpfAlreadyInUseException` **só quando a constraint violada é `children_cpf_key`** (nome lido da `ConstraintViolationException` do Hibernate); qualquer outra violação é relançada, porque a tabela também tem `CHECK`s e `NOT NULL` cuja violação seria um bug, não um conflito.
- **RG**: `Rg.normalize` remove tudo o que não é letra ou dígito e passa as letras para maiúsculas (`Locale.ROOT`). A unicidade segue o mesmo padrão do CPF sobre o valor normalizado: `existsByRg`/`existsByRgAndIdNot`, `RgAlreadyInUseException` (409, `detail` "RG já cadastrado") e `children_rg_key UNIQUE (rg)`. No `saveAndFlush`, a conversão é pelo nome da constraint: `children_cpf_key` → `CpfAlreadyInUseException`, `children_rg_key` → `RgAlreadyInUseException`. Quando a criação traz CPF e RG já usados, a verificação prévia do CPF vem primeiro e a resposta é a do CPF.
  - *Alternativa*: guardar o RG como digitado e comparar exatamente. Descartada porque o mesmo RG com e sem pontuação passaria como dois documentos diferentes.

### D4. Busca por nome

- O controller recebe `@RequestParam(required = false) String name` e o service decide: nome nulo ou em branco → `findAll(pageable)`; senão → `searchByName(pattern, pageable)`.
- O service monta o padrão: strip, escapa `\`, `%` e `_` com `\`, e envolve em `%…%`.
- `ChildRepository.searchByName` é uma `@Query` JPQL: `where lower(function('unaccent', c.name)) like lower(function('unaccent', :pattern)) escape '\'`. O Spring Data deriva a count query para a paginação.
- **Extensão `unaccent`** do PostgreSQL, criada na migration. É uma extensão *trusted* no PostgreSQL 16, como o `pgcrypto` da `V3`, então o dono do database a cria sem superusuário. Não é dependência nova da aplicação nem componente de infraestrutura; roda dentro do banco já existente.
  - *Alternativas*: coluna normalizada (`name_search`) preenchida pela aplicação, que indexaria melhor mas duplica dado e exige normalização Unicode em Java; `ILIKE` sem acento, descartado porque "joao" não acharia "João".
- **Sem índice**: `unaccent` não é `IMMUTABLE`, então um índice de expressão exigiria uma função wrapper. Com o volume esperado, um scan sequencial é suficiente.
- **Sort**: `SORTABLE_PROPERTIES = {name, birthDate, createdAt}`, validado no controller como no `user`; padrão `@PageableDefault(size = 20, sort = "name")`.

### D5. Migration `V5__create_children.sql`

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;

-- created_by/updated_by sem FK para users (convenção de add-record-authorship) e NOT NULL:
-- toda criança é gravada por uma requisição autenticada.
CREATE TABLE children (
    id                   uuid          PRIMARY KEY,
    name                 varchar(150)  NOT NULL,
    birth_date           date          NOT NULL,
    cep                  varchar(8)    NOT NULL,
    address              varchar(200)  NOT NULL,
    neighborhood         varchar(100)  NOT NULL,
    address_number       varchar(20)   NOT NULL,
    address_complement   varchar(100),
    city                 varchar(100)  NOT NULL,
    state                varchar(2)    NOT NULL,
    birthplace           varchar(150),
    race                 varchar(20),
    gender               varchar(20),
    cpf                  varchar(11),
    rg                   varchar(20),
    nationality          varchar(100),
    marital_status       varchar(20),
    religion             varchar(100),
    church               varchar(150),
    health_report        varchar(2000),
    medication           varchar(1000),
    medical_observation  varchar(2000),
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    created_by           uuid          NOT NULL,
    updated_by           uuid          NOT NULL,
    CONSTRAINT children_cpf_key UNIQUE (cpf),
    CONSTRAINT children_rg_key UNIQUE (rg),
    CONSTRAINT children_cep_check CHECK (cep ~ '^[0-9]{8}$'),
    CONSTRAINT children_cpf_check CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT children_rg_check CHECK (rg ~ '^[0-9A-Z]+$'),
    CONSTRAINT children_state_check CHECK (state IN ('AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO')),
    CONSTRAINT children_race_check CHECK (race IN ('WHITE', 'BLACK', 'BROWN', 'YELLOW', 'INDIGENOUS')),
    CONSTRAINT children_gender_check CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT children_marital_status_check
        CHECK (marital_status IN ('SINGLE', 'MARRIED', 'STABLE_UNION', 'DIVORCED', 'WIDOWED'))
);
```

Tabela e colunas em `snake_case` (convenção do projeto); os valores das listas fixas e da UF ficam em maiúsculas, iguais aos da API. `varchar(n)` com os mesmos limites da spec, para que o `ddl-auto: validate` confira o `@Column(length = …)` da entidade. Nenhuma migration existente é editada.

### D6. Erros de desserialização com `errors`

- O `GlobalExceptionHandler` sobrescreve `handleHttpMessageNotReadable`. Quando a causa é um erro do Jackson ligado a um campo (`InvalidFormatException` ou outro `MismatchedInputException` com `path` não vazio), responde `400` com o `ProblemDetail` de campos inválidos (mesmo `detail` da validação) e `errors: [{field, message}]`, com `field` montado a partir do `path` (nomes de propriedade unidos por `.`, índices de lista como `[n]`) e uma mensagem fixa ("valor em formato inválido"), sem ecoar o valor recebido.
- Sem campo identificável (JSON quebrado, corpo ausente), mantém o `400` genérico de hoje, sem `errors`, como pede o requirement "Corpo de requisição ilegível" de `user` e de `child`.
- Vale para toda a API; no `user` todos os campos são `String`, então na prática nada muda lá, mas o comportamento fica uniforme para os próximos módulos.
- *Alternativa*: receber `birthDate` como `String` e converter no mapper. Descartada: espalha parsing de datas pelos módulos, e o problema reapareceria em todo campo não textual.

### D7. Testes

- Unitários: `CpfTest`, `CepTest` e `RgTest` (normalização e validação, incluindo os exemplos da spec), `ChildTest` (normalização e invariantes), `ChildServiceTest` (Mockito, com `Clock` fixo, incluindo a fronteira de fuso e o padrão escapado), `ChildMapperTest`, `ValidCpfValidatorTest`, `GlobalExceptionHandlerTest` (D6).
- `ChildControllerTest` (`@WebMvcTest` com a segurança importada, `AuthenticatedUserLookup` mockado e tokens de `AccessTokens`): status, `errors` (inclusive data malformada), `Location`, sort inválido, `403` de `USER` no `DELETE`, `401` sem token.
- `ChildRepositoryTest` (`@DataJpaTest` + Testcontainers, importando `JpaAuditingConfiguration`): grava dentro de `AuthenticatedAs`, porque `created_by`/`updated_by` são `NOT NULL`; busca com acento/caixa/curingas, `children_cpf_key` e `children_rg_key` com vários `NULL`, os `CHECK`s via `JdbcTemplate`, e gravação sem usuário recusada pelo `NOT NULL`.
- `ChildScenarioTest` (`@SpringBootTest`): um teste por Scenario da spec, com usuários de `TestUsers` e o `MutableClock`; limpa a tabela `children` no `@BeforeEach`; a autoria é lida via `JdbcTemplate`.

## Risks / Trade-offs

- [Busca sem índice] Com muitos milhares de registros, a busca faz scan sequencial → Aceitável no volume esperado; se necessário, função wrapper `IMMUTABLE` e índice trigram numa migration futura.
- [RG repetido entre estados] O mesmo número de RG pode ser emitido por estados diferentes, e sem o órgão emissor duas crianças reais podem colidir, com a segunda recebendo `409` → Caso raro no público atendido (em geral de um só estado); uma change futura adiciona a UF emissora e muda a unicidade para `(rg, rg_state)`.
- [`errors` em erros de desserialização em toda a API] Respostas `400` de campos com tipo ou formato inválido mudam de forma em todos os endpoints → Mudança só aditiva (a propriedade `errors` aparece onde antes não havia); status e `Content-Type` continuam iguais.
- [Dependência de outra change] Sem `add-record-authorship` aplicada, a `V5` e a entidade não batem com a `BaseEntity` → Task 1.1 confere o pré-requisito antes de começar.
- [Exclusão definitiva] Dados excluídos não voltam → Restrita a `ADMIN`; quando responsáveis e matrículas existirem, as FKs dessas changes decidem o comportamento da exclusão.

## Migration Plan

- Pré-requisito: `add-record-authorship` implantada (migration `V4`).
- Deploy normal: a `V5` roda no início da aplicação, cria a extensão e a tabela vazia. Nenhum dado existente é alterado.
- Rollback: a aplicação anterior ignora a tabela `children` (o `validate` só confere as entidades que ela conhece), então basta voltar a imagem; a `V5` fica aplicada.
