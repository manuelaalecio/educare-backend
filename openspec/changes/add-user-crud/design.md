# Design

## Context

Estado atual (motivação em proposal.md, seção Why):

- Já existem `shared/persistence/BaseEntity`, com `id` UUIDv7 e `createdAt`/`updatedAt` preenchidos pela auditoria a partir do `Clock`, e `shared/config/ClockConfiguration`.
- A única migration é a `V1__baseline.sql`, e o `ddl-auto` está em `validate`.
- O profile `dev` é o padrão, inclusive nos testes, que não ativam nenhum profile. O `prod` só recebe valores por variável de ambiente.
- Nos testes, `TestcontainersConfiguration` e `MutableClock` já ficam em `shared/testsupport`.
- Ainda não existem `shared/error`, `shared/security`, regras ArchUnit nem springdoc.
- O Spring Security não está no classpath, e o `build.gradle` exclui `**/dto/**` e `**/shared/config/**` da cobertura.

Restrições: uma única instância numa VM do Oracle Free Tier, e a autenticação fica para a próxima change.

## Goals / Non-Goals

**Goals:**
- Primeiro módulo de negócio completo, que sirva de modelo para `child` e `guardian`: as quatro camadas, o mapper manual, o handler global de erros e os testes de todos os tipos.
- Senha com hash forte desde o primeiro registro, para que a change de autenticação só precise verificar a senha.

**Non-Goals:**
- Nenhuma configuração de Spring Security (filtros, `SecurityFilterChain`) nesta change.
- Nenhum cache nem busca textual na listagem.

## Decisions

### D1. Endpoints

| Método | Rota | Corpo | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/api/v1/users` | `CreateUserRequest {name, login, password, role?}` | `201` + `Location` + `UserResponse` | `400` validação/JSON, `409` login em uso |
| `GET` | `/api/v1/users` | parâmetros `page`, `size`, `sort` | `200` + `PagedModel<UserResponse>` | `400` sort inválido |
| `GET` | `/api/v1/users/{id}` | — | `200` + `UserResponse` | `400` id malformado, `404` |
| `PUT` | `/api/v1/users/{id}` | `UpdateUserRequest {name, login, role}` | `200` + `UserResponse` | `400`, `404`, `409` |
| `PUT` | `/api/v1/users/{id}/password` | `ChangePasswordRequest {password}` | `204` | `400`, `404` |
| `DELETE` | `/api/v1/users/{id}` | — | `204` | `400` id malformado, `404` |

`UserResponse` é `record UserResponse(UUID id, String name, String login, String role, Instant createdAt, Instant updatedAt)`, sem campo de senha.

- **Senha em endpoint próprio**: uma troca de senha é uma operação diferente de editar dados (a change de autenticação provavelmente vai exigir a senha atual nela), e o `PUT /users/{id}` fica um PUT de verdade, que substitui tudo o que ele representa.
  - *Alternativa*: senha opcional no `PUT /users/{id}`. Descartada porque mistura semânticas e dificulta auditar trocas de senha depois.
- **`PUT` e não `PATCH`** para nome, login e role: são só três campos, todos obrigatórios, e o cliente sempre tem o recurso completo.
- **Role opcional só na criação**: o caso comum é cadastrar um funcionário comum, então a ausência vira `USER`. No `PUT`, que substitui o recurso, a role é obrigatória, para que um cliente não rebaixe um `ADMIN` por esquecer o campo.

### D2. Módulo `user`

```
user/
├── api/
│   ├── UserController.java
│   ├── UserMapper.java                 # User -> UserResponse (Java puro)
│   └── dto/  CreateUserRequest, UpdateUserRequest, ChangePasswordRequest, UserResponse
├── application/
│   └── UserService.java                # create, findById, list, update, changePassword, delete
└── domain/
    ├── User.java                       # @Entity @Table(name = "users"), estende BaseEntity
    ├── Role.java                       # enum ADMIN, USER
    ├── UserRepository.java             # JpaRepository<User, UUID> + existsByLogin, existsByLoginAndIdNot
    ├── UserNotFoundException.java      # estende shared NotFoundException
    └── LoginAlreadyInUseException.java # estende shared ConflictException
```

- **Entidade `User`**:
  - campos `name`, `login`, `passwordHash` e `role` (`@Enumerated(EnumType.STRING)`), com `@Getter`, construtor protegido sem argumentos e sem setters;
  - construtores `User(name, login, passwordHash, role)` e `User(name, login, passwordHash)`, que usa `Role.USER`. O padrão fica no domínio, que é o dono da regra; o `DEFAULT 'USER'` do banco só protege inserções feitas fora da aplicação;
  - métodos `rename(name)`, `changeLogin(login)`, `changeRole(role)` e `changePasswordHash(hash)`;
  - o domínio normaliza (`name.strip()`, `login.toLowerCase(Locale.ROOT)`) e garante as invariantes com `IllegalArgumentException` (nome em branco, login que não é email ou com mais de 254 caracteres, role nula, hash nulo). O padrão de email é uma constante em `User`: `^[^@\s]+@[^@\s]+\.[^@\s]+$`. Essas invariantes duplicam a Bean Validation de propósito: a validação do DTO produz o `400` com `errors`, e a do domínio impede que outro chamador crie um estado inválido;
  - o nome `User` não conflita com nada do classpath atual. Se o Spring Security entrar depois, o `User` dele fica em outro pacote e não é importado aqui.
- **Hash no service, não na entidade**: o domínio só conhece o hash, e o `UserService` recebe `PasswordEncoder` por construtor. Assim o `domain` não depende de nenhuma biblioteca de segurança.
- **DTOs**:
  - `login`: `@NotBlank @Size(max = 254) @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")`. Só o `@Email` aceitaria `ana@educare`, e o `regexp` exige o ponto no domínio. O DTO repete o padrão em vez de referenciar a constante do domínio, para que a mensagem e a validação da API fiquem declarativas no DTO;
  - `role`: `String` com `@Pattern(regexp = "ADMIN|USER")`, e `@NotNull` só no `UpdateUserRequest`. Usar o enum direto no DTO faria um valor inválido virar erro de desserialização (`400` sem `errors`); com `String`, o erro sai no mesmo formato dos outros campos;
  - o `UserMapper` converte `String` → `Role` (`Role.valueOf`) e `Role` → `String` na resposta.
- **`UserService`**:
  - usa `@Transactional` na classe e `readOnly` nas consultas;
  - `create` recebe a role como `Role` ou `null`; com `null`, usa o construtor sem role, e o domínio aplica `USER`;
  - `findById` lança `UserNotFoundException`, e o `delete` busca antes de remover, para diferenciar o 404;
  - `list(Pageable)` devolve `Page<User>`, e o controller converte com o mapper e embrulha em `PagedModel`.

### D3. Unicidade do login: verificação prévia + constraint do banco

- O service verifica antes de gravar: `existsByLogin(normalizado)` na criação e `existsByLoginAndIdNot` na alteração. Se já existir, lança `LoginAlreadyInUseException` (409).
- O banco tem a garantia final com o índice único `users_login_key` em `login`. Como o login é sempre gravado em minúsculas pelo domínio, o índice simples basta, sem `lower(login)` nem `citext`.
- Para cobrir a corrida entre a verificação e o `INSERT`, o service grava com `saveAndFlush` e converte a `DataIntegrityViolationException` em `LoginAlreadyInUseException`. É o único ponto que pode violar essa constraint.
- *Alternativa*: confiar só na constraint. Descartada porque exigiria o flush em todo caso e perderia a clareza do caminho comum; a verificação prévia deixa a regra legível no service.

### D4. Hash da senha: BCrypt via `spring-security-crypto`

- Nova dependência `org.springframework.security:spring-security-crypto`, com versão gerenciada pelo BOM do Boot. É um jar pequeno, sem autoconfiguração e sem filtros, então não protege rotas por acidente nem pesa na VM do Free Tier.
- Bean `PasswordEncoder` (`BCryptPasswordEncoder`, custo padrão 10) em `shared/security/PasswordEncoderConfiguration`. Os módulos recebem a interface `PasswordEncoder`, e a change de autenticação reaproveita o mesmo bean.
- *Alternativas*:
  - `spring-boot-starter-security` agora: traria a autoconfiguração que fecha todas as rotas e obrigaria a decidir a autenticação nesta change;
  - Argon2: exige Bouncy Castle e mais memória por hash, o que é ruim na VM pequena.
- **Limite de 72 bytes**: o BCrypt só considera os primeiros 72 bytes, e o `BCryptPasswordEncoder` atual lança exceção acima disso. Por isso:
  - o DTO valida `@Size(min = 8, max = 72)` em caracteres;
  - uma constraint própria `@MaxUtf8Bytes(72)` (em `shared/validation`, genérica e reaproveitável) valida o tamanho em bytes, para que o erro seja `400` com `errors`, e não `500`.

### D5. Tratamento de erros em `shared/error`

- `NotFoundException` e `ConflictException`: exceções abstratas de runtime, das quais as exceções de domínio de cada módulo estendem.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) estende `ResponseEntityExceptionHandler`, que já converte em `ProblemDetail` os erros do MVC (JSON ilegível, `MethodArgumentTypeMismatchException` do UUID malformado, método não suportado etc.). Ele sobrescreve e trata:
  - `handleMethodArgumentNotValid`: `400` com a propriedade extra `errors: [{field, message}]`;
  - `NotFoundException` → `404`, `ConflictException` → `409`, com `detail` = mensagem da exceção. As mensagens nunca incluem senha nem email: o login é dado pessoal (LGPD), então o `409` diz só "Login já está em uso";
  - `PropertyReferenceException` (sort por propriedade inexistente) → `400`.
- `spring.mvc.problemdetails.enabled` não é necessário, porque o handler próprio já cobre esses casos.
- **Sort restrito**: o controller valida as propriedades do `Pageable` contra `{name, login, createdAt}` e lança `InvalidSortPropertyException` (em `shared/error`, `400`). Sem isso, `sort=passwordHash` seria aceito e vazaria a ordenação por hash, e `sort=password` daria erro de propriedade do Spring Data. A validação fica no controller porque é uma regra de formato da API, não de negócio.
- **Paginação**: `@PageableDefault(size = 20, sort = "name")` no controller e `spring.data.web.pageable.max-page-size: 100` no `application.yaml`, que limita silenciosamente, como a spec pede. O `PagedModel` gera o JSON estável `{content, page:{size, number, totalElements, totalPages}}`.

### D6. Migration `V2__create_users.sql`

```sql
CREATE TABLE users (
    id             uuid         PRIMARY KEY,
    name           varchar(150) NOT NULL,
    login          varchar(254) NOT NULL,
    password_hash  varchar(100) NOT NULL,
    role           varchar(20)  NOT NULL DEFAULT 'USER',
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    CONSTRAINT users_login_key UNIQUE (login),
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'USER'))
);
```

- `login varchar(254)`: é o tamanho máximo prático de um email (RFC 5321).
- `role` como texto com CHECK, e não como tipo `ENUM` do PostgreSQL. Isso casa com o `EnumType.STRING`, e acrescentar uma role depois é só trocar a constraint numa nova migration, sem `ALTER TYPE`.
  - *Alternativa*: tabelas `roles` e `user_roles`. Descartada porque um usuário tem exatamente uma role, e as roles são fixas.

- `password_hash varchar(100)`: um hash BCrypt tem 60 caracteres, e a folga permite trocar o algoritmo depois com prefixo `{id}`.
- Tabela no plural, `users`, que também evita a palavra reservada `user` do PostgreSQL.

### D7. ArchUnit

- Nova dependência de teste `com.tngtech.archunit:archunit` (a biblioteca core, sem o engine JUnit). Ela não está no BOM do Boot, então a versão é fixada no `build.gradle`.
  - *Por que não `archunit-junit5`*: o engine dele é compilado contra o JUnit Platform 1.x, e o Gradle 9 alinha o `junit-platform-launcher` a essa versão (1.14.4), incompatível com o JUnit 6 do Boot 4 (`NoSuchMethodError` ao rodar os testes). Com o core, as regras rodam como testes Jupiter comuns.
- Classe `architecture/ArchitectureTest`, que importa as classes de produção uma vez (`ClassFileImporter` com `DoNotIncludeTests`, pacote `com.manuelaalecio.educare_backend`) e tem um `@Test` por regra, chamando `rule.check(classes)`, com as regras do CLAUDE.md:
  - camadas `api` → `application` → `domain`, e `infrastructure` → `domain`. A `api` também pode acessar o `domain`, porque o mapper converte a entidade e o enum `Role` (é o padrão do CLAUDE.md, com o mapper entidade ↔ DTO); o que continua proibido é o controller chamar repositórios;
  - `domain` sem dependência de `org.springframework.web..`, `jakarta.servlet..` nem das outras camadas;
  - `application` sem `ResponseEntity`/`HttpStatus`;
  - `shared` sem depender de nenhum módulo;
  - módulos sem ciclos (`slices().matching("..educare_backend.(*)..")`, excluindo `shared`);
  - um módulo não acessa `api`, `domain` nem `infrastructure` de outro;
  - nenhum campo com `@Autowired`.
- Com um só módulo, as regras entre módulos passam de forma trivial. Por isso usam `allowEmptyShould(true)` onde for necessário, e passam a valer de verdade quando `child` entrar.

### D8. Estratégia de testes

- `domain/UserTest` (unitário): normalização, invariantes (inclusive email sem ponto no domínio e acima de 254 caracteres), role padrão `USER` e `changeRole`.
- `application/UserServiceTest` (Mockito, com `PasswordEncoder` e `UserRepository` mockados): todos os caminhos, incluindo a corrida da D3.
- `api/UserControllerTest` (`@WebMvcTest(UserController.class)` + `@Import(GlobalExceptionHandler.class)`, com o service mockado via `@MockitoBean`): status, `errors`, `Location`, sort inválido, JSON ilegível e UUID malformado.
- `domain/UserRepositoryTest` (`@DataJpaTest` + Testcontainers): `existsByLogin`, `existsByLoginAndIdNot` e as constraints `users_login_key` e `users_role_check`. Um insert via `JdbcTemplate` com `role = 'SUPER'` deve falhar, e um sem a coluna `role` deve gravar `USER`.
- `shared/error/GlobalExceptionHandlerTest` e o teste do validador `MaxUtf8Bytes`: unitários.
- `user/UserScenarioTest` (`@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc` + Testcontainers + `MutableClockConfiguration`): um teste por Scenario, com `@DisplayName("Scenario: ...")`.
  - Cada teste limpa a tabela `users` no `@BeforeEach`, incluindo o admin criado pela V3, que existe em todo banco de teste. Assim cenários como "Nenhum usuário cadastrado" e as contagens da listagem não dependem da seed nem de outros testes.
  - Para os Scenarios de senha, o teste lê `password_hash` via `JdbcTemplate` e verifica com o `PasswordEncoder` do contexto.
- `user/AdminSeedScenarioTest`: os 3 Scenarios de "Administrador inicial", no mesmo padrão do `PersistenceStartupScenarioTest`. Cada teste usa um container `postgres:16` próprio com um database novo, e sobe a aplicação com `SpringApplicationBuilder`, passando a propriedade do placeholder, o profile `prod` ou uma reinicialização, conforme o Scenario.

### D9. Administrador inicial: `V3__seed_admin_user.sql`

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (id, name, login, password_hash, role, created_at, updated_at)
VALUES (gen_random_uuid(), 'Administrador', 'admin@educare.org',
        crypt($admin_pwd$${admin_password}$admin_pwd$, gen_salt('bf', 10)),
        'ADMIN', now(), now());
```

- **Hash gerado no Postgres**: o `crypt` com `gen_salt('bf')` do `pgcrypto` produz um hash BCrypt `$2a$`, que o `BCryptPasswordEncoder` aceita. Assim a senha em texto não precisa ser convertida em hash antes. O `pgcrypto` é uma extensão *trusted* no PostgreSQL 16, então o usuário dono do database (`app`) pode criá-la sem ser superusuário.
- **Senha por placeholder do Flyway**, com o valor delimitado por dollar-quote (`$admin_pwd$...$admin_pwd$`) para que aspas na senha não quebrem o SQL:
  - `application-dev.yaml`: `spring.flyway.placeholders.admin_password: ${EDUCARE_ADMIN_PASSWORD:educare123}`;
  - `application-prod.yaml`: `spring.flyway.placeholders.admin_password: ${EDUCARE_ADMIN_PASSWORD}`, sem padrão. Sem a variável, a propriedade não resolve e a inicialização falha antes de aplicar a V3;
  - `docker-compose.yaml`: `EDUCARE_ADMIN_PASSWORD: ${EDUCARE_ADMIN_PASSWORD}` no serviço `api`, lido do `.env`.
- **Migration separada da V2**: a estrutura da tabela e os dados iniciais evoluem por motivos diferentes, e a seed pode ser analisada à parte.
- **Rodar uma única vez é o comportamento desejado**: excluir ou alterar o admin não faz ele voltar, porque o Flyway não reexecuta a V3.
- O `id` vem de `gen_random_uuid()` (v4), e não do v7 da aplicação. É só a ordenação do UUID que difere, e isso não afeta nenhuma regra.
- `created_at` e `updated_at` usam `now()`, porque o `Clock` da aplicação não participa de migrations.
- A senha configurada não é validada contra a regra de 8 a 72 caracteres, porque a migration não tem como recusar com `400`. A regra fica documentada no Migration Plan.
- *Alternativas*:
  - hash fixo commitado na migration: descartado, porque a credencial de `ADMIN` ficaria no git e seria a mesma em produção;
  - `ApplicationRunner` que cria o admin se não houver nenhum `ADMIN`: descartado por ora, porque recriaria o admin após a exclusão, e a escolha foi manter a seed no Flyway. É o plano B do risco de checksum.

## Risks / Trade-offs

- [Endpoints abertos até a change de autenticação] → Não publicar esta versão em produção exposta à internet antes da autenticação. O `docker-compose` atual roda na VM, então a próxima change deve vir antes do próximo deploy. Isso está registrado nos Non-goals da proposal.
- [Exclusão definitiva apaga a informação de quem foi o usuário] → É aceitável enquanto não existe autoria (`created_by`/`updated_by`). A change de trilha de auditoria deve rever a decisão, provavelmente trocando para desativação.
- [BCrypt custa ~100 ms de CPU por hash na VM pequena] → O impacto é baixo, porque criar usuário e trocar senha são operações raras. O custo pode ser ajustado por configuração, se for preciso.
- [Resposta 409 confirma que um email está cadastrado] → Isso não é um problema aqui, porque os consumidores são funcionários. A mensagem não repete o email, e na autenticação o login com falha deve usar uma mensagem genérica.
- [Enquanto não houver autenticação, qualquer cliente pode criar um `ADMIN` ou se promover a `ADMIN`] → É mais um motivo para não expor esta versão em produção. A change de autenticação restringe a gestão de usuários a `ADMIN`.
- [Admin inicial com senha padrão conhecida no `dev`] → O padrão existe só no `application-dev.yaml`. No `prod` não há padrão, e a aplicação não inicia sem `EDUCARE_ADMIN_PASSWORD`. Forçar a troca no primeiro acesso fica para a autenticação.
- [Checksum do Flyway incluir o valor do placeholder] → Se incluir, trocar `EDUCARE_ADMIN_PASSWORD` depois da primeira execução quebraria a validação na inicialização. A task 3.4 verifica esse comportamento. Se ele se confirmar, a V3 passa a usar um callback do Flyway ou um `ApplicationRunner` idempotente, e a spec não muda.
- [Placeholder obrigatório mesmo depois de aplicada a V3] → A variável continua exigida em todo start do `prod`. É aceitável: ela fica no `.env` da VM, como a senha do banco.
- [Regras ArchUnit sem um segundo módulo para exercitar as regras entre módulos] → Elas ficam declaradas agora e passam a ter efeito quando `child` entrar.

## Migration Plan

- Antes do deploy, definir `EDUCARE_ADMIN_PASSWORD` no `.env` da VM (senha forte, de 8 a 72 caracteres).
- A `V2` e a `V3` são aplicadas automaticamente na inicialização. É só uma tabela nova mais o admin, sem dados a migrar.
- Rollback: uma nova migration `DROP TABLE users` resolveria (a extensão `pgcrypto` pode ficar). Migration aplicada nunca é editada.
