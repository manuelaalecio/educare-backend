# CLAUDE.md

Este arquivo orienta o Claude Code (claude.ai/code) ao trabalhar com o código deste repositório.

## O que é

`educare-backend`: a API REST do Educare. Serviço Java 21 / Spring Boot 4.1.1, com build em Gradle e banco PostgreSQL 16 (migrations com Flyway). O frontend é um serviço separado, em outro repositório; este repositório é só o backend.

Divisão de responsabilidades entre os dois arquivos de orientação:
- **Este arquivo**: como o código é estruturado, escrito e testado (arquitetura, camadas, nomenclatura, testes e cobertura).
- **`openspec/config.yaml`** (`context:` e `rules:`): produto e domínio, padrões de API, persistência e segurança, e as regras dos artefatos do OpenSpec. Leia antes de projetar ou escrever código.

## Fluxo de trabalho

- Trabalho não trivial passa pelo OpenSpec: propose → apply → sync → archive (skills `/opsx:*` em `.claude/`). As changes ficam em `openspec/changes/` e as specs aceitas em `openspec/specs/`.
- Correções pequenas podem ser feitas direto no código, mas sempre seguindo o `openspec/config.yaml`.
- Testes são obrigatórios em toda mudança de código, dentro ou fora do OpenSpec (ver a seção Testes).
- Decisões que mudam a estrutura do código, a arquitetura ou a estratégia de testes atualizam este arquivo (e o resumo correspondente no `openspec/config.yaml`). Decisões de produto, domínio e padrões vão só para o `openspec/config.yaml`.

## Arquitetura e estrutura do código

Um único serviço implantável, organizado **por feature (package-by-feature)**: cada pacote de primeiro nível abaixo da raiz é um módulo de negócio, e cada módulo corresponde a uma capability do OpenSpec em `openspec/specs/<modulo>/`. Dentro do módulo, o código é dividido em quatro camadas.

```
src/main/java/com/manuelaalecio/educare_backend/
├── EducareBackendApplication.java
├── shared/                          # código transversal, não depende de nenhum módulo de negócio
│   ├── config/                      # CORS, OpenAPI, Jackson, Clock etc.
│   ├── error/                       # exceções base + @RestControllerAdvice -> ProblemDetail
│   ├── persistence/                 # entidade base (id, created_at, updated_at), auditoria
│   └── security/                    # Spring Security + JWT
└── <modulo>/                        # ex.: child/  (singular, minúsculo)
    ├── api/
    │   ├── ChildController.java     # @RestController, /api/v1/children
    │   ├── dto/                     # CreateChildRequest, UpdateChildRequest, ChildResponse (records)
    │   └── ChildMapper.java         # entidade <-> DTO, em Java puro (sem biblioteca de mapeamento)
    ├── application/
    │   └── ChildService.java        # casos de uso, @Transactional, orquestração
    ├── domain/
    │   ├── Child.java               # entidade JPA com suas regras de negócio e invariantes
    │   ├── ChildRepository.java     # interface Spring Data
    │   └── ChildNotFoundException.java
    └── infrastructure/              # só quando necessário: clientes externos, implementações de queries customizadas

src/main/resources/db/migration/     # V<n>__descricao.sql (Flyway)
```

### Responsabilidade de cada camada

| Camada | Contém | Não pode |
|---|---|---|
| `api` | controllers, DTOs de request/response, mappers, anotações de Bean Validation | ter regra de negócio, chamar repositórios, retornar entidades |
| `application` | services (casos de uso), limites de transação, chamadas a outros módulos | conhecer HTTP (`ResponseEntity`, status codes, tipos de servlet) |
| `domain` | entidades, value objects, invariantes, exceções de domínio, interfaces de repositório | depender de `api`, `application`, `infrastructure` ou de qualquer classe web |
| `infrastructure` | integrações com sistemas externos, implementações customizadas de repositório | conter regra de negócio |

Escolha pragmática: as entidades do domínio usam anotações JPA e os repositórios estendem o Spring Data diretamente (sem modelo de persistência separado nem pares port/adapter). Só introduza essa separação se um design justificar explicitamente.

### Regras de dependência (verificadas com ArchUnit)

- `api` → `application` → `domain`; `infrastructure` → `domain`.
- Um módulo nunca importa `api`, `domain` ou `infrastructure` de outro módulo. A interação entre módulos passa pelos services de `application` do outro módulo, ou por eventos de aplicação do Spring quando quem chama não deve esperar nem conhecer quem recebe.
- `shared` nunca depende de um módulo de negócio.
- Dependências circulares entre módulos não são permitidas.

### Fluxo de uma requisição

O `Controller` valida o DTO de request (`@Valid`) → converte e chama o `Service` → o service carrega e altera entidades pelo repositório dentro de uma transação, e as entidades garantem suas próprias invariantes → o controller converte o resultado em DTO de response. Erros são lançados como exceções (as de domínio estendem os tipos base de `shared/error`) e convertidos em `ProblemDetail` pelo handler global; controllers não capturam exceções.

### Nomenclatura e convenções de código

- Código (classes, métodos, variáveis, pacotes) em inglês.
- Módulos: singular, minúsculo (`child`, `guardian`). Rotas REST: plural, kebab-case (`/api/v1/children`, `/api/v1/reenrollment-periods`).
- Classes: `<Entidade>Controller`, `<Entidade>Service`, `<Entidade>Repository`, `<Entidade>Mapper`, `Create<Entidade>Request`, `Update<Entidade>Request`, `<Entidade>Response`, `<Entidade>NotFoundException`.
- DTOs são `record`s. Entidades são classes comuns (não records); prefira métodos que expressem a intenção (`child.addGuardian(guardian)`) a setters públicos.
- Injeção só por construtor (`@RequiredArgsConstructor` pode); nada de `@Autowired` em campo.
- Lombok em entidades: `@Getter` e construtor sem argumentos protegido; evite `@Data`, `@EqualsAndHashCode` e `@ToString` em entidades.
- IDs são `UUID`. Tabelas e colunas em `snake_case`; tabelas no plural.

### Criando um módulo novo

1. Comece com `/opsx:propose`; o módulo vira uma capability em `openspec/specs/<modulo>/`.
2. Crie o pacote com as quatro camadas (pule `infrastructure` se ficar vazia).
3. Adicione uma migration Flyway para as tabelas dele.
4. Escreva os testes de cada camada e os testes de cenário para cada Scenario da spec.

## Testes

Obrigatórios: nenhum código de produção entra sem teste, e o trabalho só está concluído com `./gradlew build` verde, incluindo a verificação de cobertura.

O mesmo `./gradlew build` roda no CI (`.github/workflows/ci.yml`) em todo PR para a `main`, e o check `build` é obrigatório para o merge. Em caso de falha, os relatórios de teste e do JaCoCo ficam no artifact `build-reports` da execução.

### Tipos de teste

| Tipo | Ferramentas | O que cobre |
|---|---|---|
| Unitário | JUnit 5, AssertJ, Mockito | `domain` e `application`: caminho feliz, erros, validações e casos de borda |
| Web | `@WebMvcTest` | cada endpoint: status de sucesso e de erro, validação de entrada, formato `ProblemDetail`, autorização |
| Persistência | `@DataJpaTest` + Testcontainers | queries customizadas e constraints do banco |
| Cenário | `@SpringBootTest` + Testcontainers (PostgreSQL real) | cada Scenario (WHEN/THEN) das specs do OpenSpec, de ponta a ponta pela API |
| Arquitetura | ArchUnit | regras de dependência entre camadas e módulos |

Todo Scenario de spec tem um teste de cenário correspondente, e o `@DisplayName` do teste referencia o cenário.

### Cobertura

- JaCoCo verificado no build: o build falha abaixo de 90% de linhas ou 90% de branches no total. Meta de ~100% em `domain` e `application`.
- Ficam fora da medição apenas: classe main, classes `@Configuration` sem lógica, DTOs/records sem comportamento e código gerado.
- Mutation testing com PIT em `domain` e `application` (`./gradlew pitest`, fora do build padrão): mínimo de 80% de mutantes mortos.
- Não escreva testes sem asserções relevantes só para subir a cobertura.

### Convenções

- Nome do método descreve o comportamento: `should<Resultado>When<Condicao>` (ex.: `shouldRejectReenrollmentWhenPeriodIsClosed`).
- Estrutura given / when / then.
- Testes independentes entre si, sem depender de ordem nem de dados de outro teste.
- Bug corrigido sempre vem com um teste que reproduz o bug.

### Estrutura

Os testes espelham a estrutura de pacotes do código principal. Por módulo:

```
src/test/java/com/manuelaalecio/educare_backend/
├── shared/testsupport/          # config do Testcontainers (@ServiceConnection), fixtures/builders
├── architecture/                # regras ArchUnit
└── child/
    ├── domain/ChildTest.java                       # unitário
    ├── application/ChildServiceTest.java           # unitário (Mockito)
    ├── api/ChildControllerTest.java                # @WebMvcTest
    ├── domain/ChildRepositoryTest.java             # @DataJpaTest + Testcontainers
    └── ChildScenarioTest.java                      # @SpringBootTest, um teste por Scenario da spec
```

## Armadilhas

- **Profiles `dev` e `prod`.** O `dev` é o profile padrão e aponta para o banco do Compose em `localhost:5432/app` (usuário `app`); para o `bootRun` subir, basta `docker compose up -d db` e passar `SPRING_DATASOURCE_PASSWORD` (valor de `DB_PASSWORD` no `.env`). O `prod` exige `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`, sem valor padrão, e é ativado pelo `docker-compose.yaml` (`SPRING_PROFILES_ACTIVE=prod`). Testes não ativam profile.
- **Testes precisam do Docker rodando** (Testcontainers sobe um `postgres:16`). Todo `@SpringBootTest` ou `@DataJpaTest` usa `@Import(TestcontainersConfiguration.class)`, de `shared/testsupport`; nunca aponte testes para o banco do Compose.
- **Nomes de dependências do Spring Boot 4 e Testcontainers 2.** Use `spring-boot-starter-webmvc` (não `-web`). As dependências de teste são por starter (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-flyway-test`, `-validation-test`, `-actuator-test`), em vez de um único `spring-boot-starter-test`. Ao adicionar um starter, adicione também o `-test` correspondente. Os módulos do Testcontainers 2 têm prefixo (`testcontainers-postgresql`), e o `PostgreSQLContainer` fica em `org.testcontainers.postgresql`.
- **O nome do pacote tem underscore**: `com.manuelaalecio.educare_backend` (a forma com hífen não é um nome de pacote Java válido).
- **Flyway**: as migrations ficam em `src/main/resources/db/migration/`, no formato `V<n>__descricao.sql`. Nunca edite uma migration que já foi aplicada. O `clean` está desabilitado em todos os ambientes.
- **Schema só por migration.** `ddl-auto=validate`: o Hibernate não cria tabelas, e a aplicação não sobe se uma entidade não tiver tabela e colunas correspondentes numa migration. Toda entidade de negócio estende `shared/persistence/BaseEntity`, e toda tabela segue a convenção: `id uuid PRIMARY KEY`, colunas do módulo em `snake_case`, `created_at timestamptz NOT NULL` e `updated_at timestamptz NOT NULL` (preenchidos pela auditoria do Spring Data a partir do bean `Clock`).
- **Tabelas de entidades de teste** ficam na migration repetível `src/test/resources/db/migration/R__test_support_schema.sql`, só no classpath de teste; nunca crie migration versionada para elas.
- **`tentar-vm.sh`** é um script operacional de provisionamento da VM na Oracle Cloud. Não faz parte da aplicação; não altere nem inclua no build.
