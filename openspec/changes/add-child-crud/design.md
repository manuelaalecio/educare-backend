# Design

## Context

Motivação em proposal.md (seção Why); comportamento em `specs/child/spec.md`.

Estado atual que molda a abordagem (`add-record-authorship` e `add-classroom-crud` já aplicadas):

- O módulo `user` é o modelo de CRUD: controller com `@PreAuthorize`, `UserMapper` em Java puro, DTOs `record` com Bean Validation, enum recebido como `String` + `@Pattern` e convertido no mapper, unicidade por verificação prévia + `saveAndFlush` convertendo a `DataIntegrityViolationException`, sort restrito por `SORTABLE_PROPERTIES` e `PagedModel`.
- `shared/error` já tem `NotFoundException` (404), `ConflictException` (409), `InvalidFieldException` (400 com uma entrada em `errors`), `InvalidSortPropertyException` e o `GlobalExceptionHandler`, que estende `ResponseEntityExceptionHandler` (JSON ilegível e UUID malformado já viram `400` em `ProblemDetail`, sem `errors`).
- `shared/security` já exige token em toda rota fora das públicas e responde `401`/`403` em `ProblemDetail`; a role vem do banco a cada requisição.
- `BaseEntity` já tem `createdBy`/`updatedBy`, preenchidos pela auditoria a partir do usuário autenticado; `shared/testsupport/AuthenticatedAs` permite gravar como um usuário fora de requisição.
- `spring.data.web.pageable.max-page-size: 100` já está no `application.yaml`; `Clock` é um bean (`ClockConfiguration`), trocado por `MutableClock` nos testes.
- Migrations até `V7__rename_roles_to_coordinator_educator.sql`; `ddl-auto: validate`. A `V6__create_classrooms.sql` já criou a extensão `unaccent` e a função `immutable_unaccent(text)` (`IMMUTABLE`, com função e dicionário qualificados pelo schema), usada pelo `classroom` na unicidade e na ordenação dos nomes.
- `ArchitectureTest` já tem as regras entre módulos, exercidas por `user`, `auth` e `classroom`; nesta change o `child` não depende de nenhum outro módulo.
- Contrato da API em `openapi/educare-api.json`, verificado pelo `ApiContractScenarioTest` e pelo `ApiContractContentTest`: campos de resposta obrigatórios salvo os anotados com `@Nullable` (jspecify), erros declarados com as meta-anotações de `shared/config` (`@BadRequestResponse`, `@ForbiddenResponse`, `@NotFoundResponse`, `@ConflictResponse`) e `@ResponseStatus(HttpStatus.OK)` nas operações que devolvem corpo com `200` (o `ClassroomController` é o modelo).

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
| `POST` | `/api/v1/children` | `ChildRequest` | `201` + `Location` + `ChildResponse` | `400` validação/JSON, `409` CPF, RG ou certidão em uso | autenticado |
| `GET` | `/api/v1/children` | `name?`, `page`, `size`, `sort` | `200` + `PagedModel<ChildSummaryResponse>` | `400` sort inválido | autenticado |
| `GET` | `/api/v1/children/{id}` | — | `200` + `ChildResponse` | `400` id malformado, `404` | autenticado |
| `PUT` | `/api/v1/children/{id}` | `ChildRequest` | `200` + `ChildResponse` | `400`, `404`, `409` | autenticado |
| `DELETE` | `/api/v1/children/{id}` | — | `204` | `400` id malformado, `403`, `404` | `COORDENADOR` |

Todos respondem `401` sem token válido (filter chain já existente).

- **Acesso**: o controller não tem `@PreAuthorize` na classe (o token já é exigido pela filter chain); só o método `delete` tem `@PreAuthorize("hasRole('COORDENADOR')")`.
- **Um único `ChildRequest` para criar e alterar**, em vez do par `CreateChildRequest`/`UpdateChildRequest` da convenção do CLAUDE.md. O `PUT` substitui a criança inteira e aceita exatamente os mesmos campos e regras da criação; dois records idênticos com 27 campos anotados cada tenderiam a divergir numa manutenção.
  - *Alternativa*: seguir a convenção ao pé da letra. Descartada pelo risco de divergência; se um dia os corpos diferirem, o record é separado.
- **`ChildSummaryResponse` na listagem** (`id`, `name`, `birthDate`, `createdAt`, `updatedAt`) e `ChildResponse` completo nos demais: a listagem é a tela mais acessada e não precisa de CPF, certidão, endereço nem dados de saúde (minimização, LGPD). Nenhuma das duas tem `createdBy`/`updatedBy`.
- **`PUT` e não `PATCH`**: o frontend sempre edita a ficha completa; opcional ausente apaga o valor.

### D2. Módulo `child`

```
child/
├── api/
│   ├── ChildController.java
│   ├── ChildMapper.java               # ChildRequest -> ChildDetails; Child -> ChildResponse / ChildSummaryResponse
│   ├── ValidCpf.java                  # constraint Bean Validation do CPF (formato + dígitos)
│   ├── ValidCpfValidator.java         # delega a Cpf.isValid do domínio
│   ├── ValidBirthCertificate.java     # constraint de classe do ChildRequest: coerência dos campos da certidão (D8)
│   ├── ValidBirthCertificateValidator.java
│   ├── ValidBirthRegistrationNumber.java # constraint da matrícula (formato, dígitos verificadores, livro de nascimento)
│   ├── ValidBirthRegistrationNumberValidator.java # delega a BirthRegistrationNumber.check do domínio
│   └── dto/  ChildRequest, ChildResponse, ChildSummaryResponse
├── application/
│   └── ChildService.java              # create, findById, list(name, pageable), update, delete
└── domain/
    ├── Child.java                     # @Entity @Table(name = "children"), estende BaseEntity
    ├── ChildDetails.java              # record com os dados editáveis (entrada de create/update)
    ├── Race.java, Gender.java, MaritalStatus.java
    ├── BrazilianState.java            # enum com as 27 siglas de UF (AC … TO)
    ├── BirthCertificateType.java      # enum REGISTRATION, LEGACY
    ├── Cpf.java, Cep.java             # utilitários: normalize(String) e isValid(String), sem estado
    ├── Rg.java                        # utilitário: normalize(String) (só letras e dígitos, maiúsculas)
    ├── BirthRegistrationNumber.java   # utilitário da matrícula: normalize, check (motivo da recusa), registrationYear (D8)
    ├── ChildRepository.java
    ├── ChildNotFoundException.java    # estende NotFoundException
    ├── CpfAlreadyInUseException.java  # estende ConflictException, mensagem sem o CPF
    ├── RgAlreadyInUseException.java   # estende ConflictException, mensagem sem o RG
    ├── BirthCertificateAlreadyInUseException.java # estende ConflictException, mensagem sem dados da certidão
    ├── BirthDateInFutureException.java # estende InvalidFieldException (campo birthDate)
    └── InvalidRegistrationYearException.java # estende InvalidFieldException (campo birthCertificateNumber)
```

- **Entidade `Child`**: `@Getter`, construtor protegido sem argumentos, sem setters. `Child(ChildDetails)` e `update(ChildDetails)` aplicam os dados de uma vez (a alteração é sempre completa). O domínio garante as invariantes com `IllegalArgumentException`: obrigatórios não nulos/não em branco, tamanhos máximos, `cep` e `cpf` válidos, `rg` com ao menos uma letra ou dígito, certidão coerente com o tipo e matrícula válida (D8). Também normaliza (strip, opcional em branco → `null`, `cep`/`cpf`/matrícula só com dígitos, `rg` só com letras e dígitos em maiúsculas), de forma idempotente, para que outro chamador não grave um estado inválido. Como no `user`, isso duplica de propósito a Bean Validation do DTO, que é quem produz o `400` com `errors`. A autoria vem da `BaseEntity`, sem código no módulo.
- **`ChildDetails`** evita construtores e métodos com 27 parâmetros posicionais e é o contrato entre `application` e `domain`; o mapper da `api` o monta a partir do `ChildRequest`.
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
- `ChildRepository.searchByName` é uma `@Query` JPQL: `where lower(function('immutable_unaccent', c.name)) like lower(function('immutable_unaccent', :pattern)) escape '\'`. O Spring Data deriva a count query para a paginação.
- **Função `immutable_unaccent`**, criada pela `V6` (turmas) sobre a extensão `unaccent`: é a mesma normalização da unicidade dos nomes de turma e da unicidade do cartório na certidão (D8), então a aplicação e o banco concordam sobre o que é "igual sem acento". Nada a criar na `V8`; não é dependência nova nem componente de infraestrutura.
  - *Alternativas*: coluna normalizada (`name_search`) preenchida pela aplicação, que duplica dado e exige normalização Unicode em Java; `ILIKE` sem acento, descartado porque "joao" não acharia "João".
- **Sem índice**: com o volume esperado (algumas centenas a poucos milhares de crianças), um scan sequencial é suficiente. Como a função já é `IMMUTABLE`, um índice trigram sobre `lower(immutable_unaccent(name))` pode entrar numa migration futura sem mudar o código.
- **Sort**: `SORTABLE_PROPERTIES = {name, birthDate, createdAt}`, validado no controller como no `user`; padrão `@PageableDefault(size = 20, sort = "name")`.

### D5. Migration `V8__create_children.sql`

```sql
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
    birth_certificate_type             varchar(20),
    birth_certificate_number           varchar(32),
    birth_certificate_registry_office  varchar(150),
    birth_certificate_book             varchar(20),
    birth_certificate_page             varchar(20),
    birth_certificate_term             varchar(20),
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
        CHECK (marital_status IN ('SINGLE', 'MARRIED', 'STABLE_UNION', 'DIVORCED', 'WIDOWED')),
    CONSTRAINT children_birth_certificate_number_check CHECK (birth_certificate_number ~ '^[0-9]{32}$'),
    -- Sem certidão, modelo novo (só a matrícula) ou modelo antigo (cartório, livro, folha e termo).
    CONSTRAINT children_birth_certificate_check CHECK (
        (birth_certificate_type IS NULL
            AND birth_certificate_number IS NULL AND birth_certificate_registry_office IS NULL
            AND birth_certificate_book IS NULL AND birth_certificate_page IS NULL AND birth_certificate_term IS NULL)
        OR (birth_certificate_type = 'REGISTRATION'
            AND birth_certificate_number IS NOT NULL AND birth_certificate_registry_office IS NULL
            AND birth_certificate_book IS NULL AND birth_certificate_page IS NULL AND birth_certificate_term IS NULL)
        OR (birth_certificate_type = 'LEGACY'
            AND birth_certificate_number IS NULL AND birth_certificate_registry_office IS NOT NULL
            AND birth_certificate_book IS NOT NULL AND birth_certificate_page IS NOT NULL
            AND birth_certificate_term IS NOT NULL)
    )
);

-- Matrícula única (vários NULL permitidos: crianças sem certidão ou com o modelo antigo).
CREATE UNIQUE INDEX children_birth_certificate_number_key ON children (birth_certificate_number)
    WHERE birth_certificate_number IS NOT NULL;

-- Modelo antigo único: cartório sem diferenciar maiúsculas e acentos (mesma expressão das turmas, da V6),
-- livro, folha e termo como gravados (só sem os espaços das pontas).
CREATE UNIQUE INDEX children_birth_certificate_legacy_key ON children (
    lower(immutable_unaccent(birth_certificate_registry_office)),
    birth_certificate_book, birth_certificate_page, birth_certificate_term)
    WHERE birth_certificate_type = 'LEGACY';
```

Tabela e colunas em `snake_case` (convenção do projeto); os valores das listas fixas, da UF e do tipo da certidão ficam em maiúsculas, iguais aos da API. `varchar(n)` com os mesmos limites da spec, para que o `ddl-auto: validate` confira o `@Column(length = …)` da entidade. Nenhuma migration existente é editada. A `children_birth_certificate_check` não lista os tipos à parte: um tipo fora de `REGISTRATION`/`LEGACY` já não satisfaz nenhum dos três ramos.

### D6. Erros de desserialização com `errors`

- O `GlobalExceptionHandler` sobrescreve `handleHttpMessageNotReadable`. Quando a causa é um erro do Jackson ligado a um campo (`InvalidFormatException` ou outro `MismatchedInputException` com `path` não vazio), responde `400` com o `ProblemDetail` de campos inválidos (mesmo `detail` da validação) e `errors: [{field, message}]`, com `field` montado a partir do `path` (nomes de propriedade unidos por `.`, índices de lista como `[n]`) e uma mensagem fixa ("valor em formato inválido"), sem ecoar o valor recebido.
- Sem campo identificável (JSON quebrado, corpo ausente), mantém o `400` genérico de hoje, sem `errors`, como pede o requirement "Corpo de requisição ilegível" de `user` e de `child`.
- Vale para toda a API; no `user` todos os campos são `String`, então na prática nada muda lá, mas o comportamento fica uniforme para os próximos módulos.
- *Alternativa*: receber `birthDate` como `String` e converter no mapper. Descartada: espalha parsing de datas pelos módulos, e o problema reapareceria em todo campo não textual.

### D7. Testes

- Unitários: `CpfTest`, `CepTest`, `RgTest` e `BirthRegistrationNumberTest` (normalização e validação, incluindo os exemplos da spec e as matrículas reais de referência da D8), `ChildTest` (normalização e invariantes, inclusive da certidão), `ChildServiceTest` (Mockito, com `Clock` fixo, incluindo a fronteira de fuso, o ano do registro e o padrão escapado), `ChildMapperTest`, `ValidCpfValidatorTest`, `ValidBirthCertificateValidatorTest`, `ValidBirthRegistrationNumberValidatorTest`, `GlobalExceptionHandlerTest` (D6).
- `ChildControllerTest` (`@WebMvcTest` com a segurança importada, `AuthenticatedUserLookup` mockado e tokens de `AccessTokens`): status, `errors` (inclusive data malformada e cada campo da certidão), `Location`, sort inválido, `409` sem dados do documento, `403` de `EDUCADOR` no `DELETE`, `401` sem token.
- `ChildRepositoryTest` (`@DataJpaTest` + Testcontainers, importando `JpaAuditingConfiguration`): grava dentro de `AuthenticatedAs`, porque `created_by`/`updated_by` são `NOT NULL`; busca com acento/caixa/curingas, `children_cpf_key`, `children_rg_key` e os dois índices da certidão com vários `NULL`, a unicidade do cartório sem caixa e acento, os `CHECK`s via `JdbcTemplate`, e gravação sem usuário recusada pelo `NOT NULL`.
- `ChildScenarioTest` (`@SpringBootTest`): um teste por Scenario da spec, com usuários de `TestUsers` e o `MutableClock`; limpa a tabela `children` no `@BeforeEach`; a autoria é lida via `JdbcTemplate`.
- Contrato: `ApiContractContentTest` estendido com as operações de `/api/v1/children` (D9).

### D8. Certidão de nascimento

- **Campos planos no `ChildRequest`/`ChildResponse`** (`birthCertificateType`, `birthCertificateNumber`, `birthCertificateRegistryOffice`, `birthCertificateBook`, `birthCertificatePage`, `birthCertificateTerm`), e não um objeto `birthCertificate` aninhado: é o formato combinado com o frontend na tarefa, e mantém o `field` de cada entrada de `errors` igual ao nome do campo do formulário. No domínio, ficam como seis colunas da própria `children` (a certidão é 1:1 e opcional; uma tabela à parte só acrescentaria um join).
  - *Alternativa*: `@Embeddable BirthCertificate` na entidade. Possível na implementação, se deixar a `Child` mais legível; não muda API nem schema.
- **Tipo**: `String` com `@Pattern("REGISTRATION|LEGACY")` no DTO (mesmo motivo de `race` etc.), convertido para o enum `BirthCertificateType` no mapper.
- **Coerência entre os campos**: uma constraint de classe `@ValidBirthCertificate` no `ChildRequest`. O validador desliga a violação padrão e adiciona uma por campo (`addPropertyNode`), para que cada problema vire uma entrada em `errors` com o `field` certo, como pede a spec: tipo ausente com outros campos → `birthCertificateType`; matrícula ausente em `REGISTRATION` ou presente em `LEGACY` → `birthCertificateNumber`; cada campo do modelo antigo ausente em `LEGACY` ou presente em `REGISTRATION` → o próprio campo. Com tipo inválido, o validador não avalia a coerência (o `@Pattern` já gera a entrada de `birthCertificateType`). Roda sobre o record já normalizado (opcional em branco já é `null`).
- **Matrícula** (`BirthRegistrationNumber`, no domínio, sem estado):
  - `normalize`: remove pontos, espaços e hífens; o resto é conferido como está (letra → inválida).
  - `check(String)`: devolve o motivo da recusa (formato, dígito verificador, tipo de livro) ou nenhum; o `ValidBirthRegistrationNumberValidator` (constraint de campo em `birthCertificateNumber`, nulo válido) usa o motivo para escolher a mensagem ("Esta matrícula não é de uma certidão de nascimento" para o 15º dígito, "Matrícula inválida" para o resto). A estrutura da matrícula (Provimento nº 3/2009 da Corregedoria Nacional de Justiça) é: dígitos 1–6 serventia (CNS), 7–8 acervo, 9–10 tipo de serviço, 11–14 ano do registro, 15 tipo do livro (`1` = nascimento), 16–20 livro, 21–23 folha, 24–30 termo, 31–32 dígitos verificadores.
  - **Dígitos verificadores**: módulo 11 sobre os 30 primeiros dígitos, conforme o algoritmo publicado com o provimento (o mesmo da CRC Nacional). Os pesos e o tratamento do resto `10` **são conferidos na task 4.1 contra 2–3 matrículas de certidões reais** fornecidas pela Manuela, que viram casos de teste (anonimizados: só a matrícula, sem nomes) antes de qualquer outro código depender do algoritmo.
  - `registrationYear(String)`: os dígitos 11–14 como inteiro.
  - Como `Cpf`, a validade do formato vem por Bean Validation (para gerar o `400` com `errors`) e é repetida como invariante na `Child` (`IllegalArgumentException`).
- **Ano do registro**: depende do relógio (ano atual) e do `birthDate`, então fica no `ChildService`, junto da regra da data futura: ano do registro maior que o ano atual em `America/Sao_Paulo`, ou menor que o ano de `birthDate`, lança `InvalidRegistrationYearException` (`400`, campo `birthCertificateNumber`). Uma requisição com `birthDate` malformado nem chega ao service (falha na desserialização, D6), então "sem `birthDate` válido" da spec é o caso em que o `400` vem do campo `birthDate` e a matrícula só passa pela Bean Validation.
  - *Limitação aceita*: o service lança uma exceção de cada vez, então data futura e ano do registro inválido na mesma requisição geram só a entrada de `birthDate` (a primeira conferida). A spec não exige as duas juntas.
- **Unicidade**, no mesmo padrão do CPF (D3):
  - verificação prévia: `existsByBirthCertificateNumber`/`…AndIdNot` e uma query nativa `existsLegacyCertificate(office, book, page, term, excludedId)` com `lower(immutable_unaccent(birth_certificate_registry_office)) = lower(immutable_unaccent(:office))`, a mesma expressão do índice;
  - na corrida, a violação de `children_birth_certificate_number_key` ou de `children_birth_certificate_legacy_key` no `saveAndFlush` vira `BirthCertificateAlreadyInUseException` (409, `detail` "Certidão de nascimento já cadastrada", sem dados); as demais continuam relançadas;
  - ordem das verificações prévias: CPF, RG, certidão (a primeira que falhar responde).
- **Livro, folha e termo** são comparados como gravados (só strip), sem mudar maiúsculas: os livros antigos têm letras de série ("A-123") que o cartório escreve de um jeito só.

### D9. Contrato da API

- `ChildController` segue o `ClassroomController`: `@ResponseStatus(HttpStatus.OK)` nas operações que devolvem corpo com `200` (`list`, `findById`, `update`), `@ApiResponse` com o header `Location` no `create`, e as meta-anotações conforme a D1: `@BadRequestResponse` em todas as operações com corpo ou id, `@NotFoundResponse` em `findById`/`update`/`delete`, `@ConflictResponse` em `create`/`update`, `@ForbiddenResponse` **só no método `delete`** (o único com `@PreAuthorize`). O `401` é acrescentado pelo `UnauthorizedResponseCustomizer`.
- Todos os componentes opcionais de `ChildResponse` levam `@Nullable` (jspecify): os 13 opcionais da criança e os 6 da certidão. Os de `ChildSummaryResponse` são todos obrigatórios.
- `./gradlew updateApiContract` regenera `openapi/educare-api.json`, commitado junto; o `ApiContractContentTest` ganha as verificações de `/api/v1/children` (status de sucesso, campos obrigatórios e opcionais, erros de cada operação).

## Risks / Trade-offs

- [Busca sem índice] Com muitos milhares de registros, a busca faz scan sequencial → Aceitável no volume esperado; se necessário, função wrapper `IMMUTABLE` e índice trigram numa migration futura.
- [RG repetido entre estados] O mesmo número de RG pode ser emitido por estados diferentes, e sem o órgão emissor duas crianças reais podem colidir, com a segunda recebendo `409` → Caso raro no público atendido (em geral de um só estado); uma change futura adiciona a UF emissora e muda a unicidade para `(rg, rg_state)`.
- [`errors` em erros de desserialização em toda a API] Respostas `400` de campos com tipo ou formato inválido mudam de forma em todos os endpoints → Mudança só aditiva (a propriedade `errors` aparece onde antes não havia); status e `Content-Type` continuam iguais.
- [Algoritmo dos dígitos verificadores da matrícula] Se os pesos ou o tratamento do resto estiverem errados, certidões verdadeiras são recusadas com `400` → Task 4.1 confere o algoritmo com matrículas reais antes de seguir; as matrículas viram testes de regressão.
- [Exclusão definitiva] Dados excluídos não voltam → Restrita a `COORDENADOR`; o vínculo com a turma (`link-child-to-classroom`) apaga o histórico junto (`ON DELETE CASCADE`), e as FKs de responsáveis e matrículas decidem o comportamento quando existirem.
- [Criança sem turma em produção] Esta change sozinha permite criar crianças sem turma → Não vai para produção sem `link-child-to-classroom` (Migration Plan).

## Migration Plan

- Pré-requisitos: `add-record-authorship` e `add-classroom-crud` implantadas (migrations até a `V7`).
- **Vai para produção junto com `link-child-to-classroom`**: os dois PRs podem ser mergeados na `main` separados, mas **sem o label `build`** no PR desta change e sem tag `vX.Y.Z` entre os dois merges; a imagem é publicada (label no último PR ou tag) só quando as duas estiverem na `main`. Assim o Argo CD Image Updater nunca leva para o cluster uma versão em que dá para criar criança sem turma.
- Deploy normal: a `V8` roda no início da aplicação e cria a tabela vazia e os índices. Nenhum dado existente é alterado; compatível com a versão em produção (só acrescenta tabela).
- Rollback: a aplicação anterior ignora a tabela `children` (o `validate` só confere as entidades que ela conhece), então basta voltar a imagem; a `V8` fica aplicada.
