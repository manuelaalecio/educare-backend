# Tasks

## 1. Dependências e configuração

- [x] 1.1 Adicionar ao `build.gradle` `implementation 'org.springframework.security:spring-security-crypto'` (versão do BOM) e `testImplementation 'com.tngtech.archunit:archunit:<versão estável atual>'` (core; ver D7 sobre o `archunit-junit5`) (design D4, D7); adicionar `spring.data.web.pageable.max-page-size: 100` ao `application.yaml` (D5). Verificar com `./gradlew dependencies --configuration runtimeClasspath` que o `spring-security-crypto` aparece e o `spring-security-web`/`-config` não aparecem, e com `./gradlew test` que tudo continua verde

## 2. Peças transversais em `shared`

- [x] 2.1 Criar `shared/security/PasswordEncoderConfiguration` (bean `PasswordEncoder` = `BCryptPasswordEncoder`, D4), com teste unitário `shouldMatchRawPasswordWhenEncodedByProvidedEncoder`; verificar com `./gradlew test --tests '*PasswordEncoderConfiguration*'`
- [x] 2.2 Criar a constraint `shared/validation/MaxUtf8Bytes` e o validador dela (D4), com testes unitários para: valor nulo aceito, exatamente 72 bytes aceito, 73 bytes recusado e 40 × `ç` recusado; verificar com `./gradlew test --tests '*MaxUtf8Bytes*'`
- [x] 2.3 Criar em `shared/error` as exceções `NotFoundException`, `ConflictException` e `InvalidSortPropertyException`, e o `GlobalExceptionHandler` (estende `ResponseEntityExceptionHandler`), conforme D5:
  - `400` com `errors: [{field, message}]` para `MethodArgumentNotValidException`;
  - `404`, `409` e `400` para as exceções base;
  - `400` para `PropertyReferenceException`.

  Testes unitários do handler cobrindo cada tipo, o formato `ProblemDetail` e a presença de `errors`; verificar com `./gradlew test --tests '*GlobalExceptionHandler*'`

## 3. Schema e domínio do módulo `user`

- [x] 3.1 Criar a nova migration `src/main/resources/db/migration/V2__create_users.sql` (tabela `users` com `login varchar(254)`, `role varchar(20) NOT NULL DEFAULT 'USER'` e as constraints `users_login_key` e `users_role_check`, conforme D6), sem editar a `V1`; verificar com `./gradlew test --tests '*EducareBackendApplicationTests*'` que o contexto sobe
- [x] 3.2 Criar o enum `user/domain/Role` (`ADMIN`, `USER`) e `user/domain/User` (estende `BaseEntity`, `@Table(name = "users")`, `role` com `@Enumerated(EnumType.STRING)`, construtores com e sem role, `rename`, `changeLogin`, `changeRole` e `changePasswordHash`, normalização por `strip` no nome e minúsculas no login, padrão de email como constante, D2), `UserNotFoundException` e `LoginAlreadyInUseException` (mensagem sem o email). Testes em `user/domain/UserTest`:
  - normalização do nome e do login;
  - role padrão `USER` no construtor sem role;
  - rejeição de nome em branco, login que não é email (`ana.souza`), email sem ponto no domínio (`ana@educare`), login com mais de 254 caracteres, role nula e hash nulo;
  - `rename`, `changeLogin`, `changeRole` e `changePasswordHash`;
  - a mensagem de `LoginAlreadyInUseException` não contém o email.

  Verificar com `./gradlew test --tests '*UserTest'` e com o contexto subindo (`ddl-auto: validate`)
- [x] 3.3 Criar `user/domain/UserRepository` (`existsByLogin` e `existsByLoginAndIdNot`), com `user/domain/UserRepositoryTest` (`@DataJpaTest` + `@Import(TestcontainersConfiguration.class)`) cobrindo:
  - as duas queries, incluindo o próprio id excluído;
  - a violação de `users_login_key` ao gravar dois usuários com o mesmo login;
  - `users_role_check`: um insert via `JdbcTemplate` com `role = 'SUPER'` falha, e um insert sem a coluna `role` grava `USER`;
  - o `role` gravado como texto (`ADMIN`/`USER`) e lido de volta como enum.

  Verificar com `./gradlew test --tests '*UserRepositoryTest'`
- [x] 3.4 Criar a nova migration `V3__seed_admin_user.sql` (D9: `CREATE EXTENSION IF NOT EXISTS pgcrypto`, admin `Administrador`/`admin@educare.org`/`ADMIN`, senha pelo placeholder `${admin_password}` entre dollar-quotes, hash com `crypt(..., gen_salt('bf', 10))`) e configurar:
  - `spring.flyway.placeholders.admin_password` em `application-dev.yaml` (`${EDUCARE_ADMIN_PASSWORD:educare123}`) e em `application-prod.yaml` (`${EDUCARE_ADMIN_PASSWORD}`, sem padrão);
  - `EDUCARE_ADMIN_PASSWORD` no serviço `api` do `docker-compose.yaml`;
  - a seção Armadilhas do CLAUDE.md: a nova variável, obrigatória no `prod`, e o aviso de que todo banco de teste já contém o admin (testes que contam usuários limpam `users` antes).

  Verificar:
  - com `./gradlew test --tests '*UserRepositoryTest'`, que o hash gerado pelo `pgcrypto` para `educare123` é aceito por `BCryptPasswordEncoder.matches` (teste lendo `password_hash` do admin);
  - com `docker compose config`, que a variável aparece;
  - **o risco de checksum da D9**: num teste de inicialização, aplicar as migrations com um valor de placeholder e reiniciar com outro valor. Se a validação do Flyway falhar, parar e aplicar o plano B da D9 (callback ou `ApplicationRunner`), atualizando o design antes de seguir.

## 4. Casos de uso

- [x] 4.1 Criar `user/application/UserService` (`create` com role opcional, que usa `USER` quando vem `null`; `findById`, `list`, `update` com nome, login e role, `changePassword` e `delete`; hash via `PasswordEncoder`; verificação prévia do login + `saveAndFlush` convertendo `DataIntegrityViolationException` em `LoginAlreadyInUseException`, D2/D3), com `user/application/UserServiceTest` (Mockito) cobrindo:
  - caminho feliz de cada método;
  - `UserNotFoundException` em `findById`/`update`/`changePassword`/`delete`;
  - conflito na verificação prévia e na corrida (exceção do flush);
  - `create` sem role gerando `USER` e com `ADMIN` gerando `ADMIN`;
  - `update` trocando a role;
  - `update` mantendo o próprio login;
  - a senha guardada sendo o valor retornado pelo encoder.

  Verificar com `./gradlew test --tests '*UserServiceTest'`

## 5. API

- [x] 5.1 Criar os DTOs `CreateUserRequest`, `UpdateUserRequest`, `ChangePasswordRequest` e `UserResponse` e o `UserMapper`, conforme D2. Os DTOs são records com Bean Validation:
  - `login` com `@NotBlank @Size(max = 254) @Email(regexp = ...)`;
  - `role` como `String` com `@Pattern("ADMIN|USER")`, e `@NotNull` só no update;
  - `@MaxUtf8Bytes(72)` na senha.

  O mapper converte a role `String` ↔ `Role`. Testes unitários do mapper, verificando que todos os campos de `UserResponse` são copiados, incluindo `role`, e a conversão de `null` e dos dois valores de role. Verificar com `./gradlew test --tests '*UserMapper*'`
- [x] 5.2 Criar `user/api/UserController` com os seis endpoints da D1, conforme D5:
  - `Location` no `201`;
  - `@PageableDefault(size = 20, sort = "name")`;
  - validação das propriedades de sort contra `{name, login, createdAt}`;
  - `PagedModel` na listagem.

  Testes em `user/api/UserControllerTest` (`@WebMvcTest` + `GlobalExceptionHandler`, service com `@MockitoBean`):
  - para cada endpoint, o status de sucesso e os de erro (`400` com `errors`, `404`, `409`);
  - UUID malformado, JSON ilegível e sort não permitido;
  - `login` que não é email e sem ponto no domínio, role inválida (`SUPER`), role ausente no `PUT` (`400`) e ausente no `POST` (aceita);
  - `409` sem o email no corpo;
  - `size` acima de 100 limitado a 100;
  - ausência de `password`/`passwordHash` nos corpos de resposta.

  Verificar com `./gradlew test --tests '*UserControllerTest'`

## 6. Regras de arquitetura

- [x] 6.1 Criar `architecture/ArchitectureTest` com as regras da D7 (camadas, com `api` podendo acessar `domain` via mapper, `domain` sem web, `application` sem HTTP, `shared` independente, módulos sem ciclo e sem acesso a `api`/`domain`/`infrastructure` de outro módulo, sem `@Autowired` em campo). Verificar com `./gradlew test --tests '*ArchitectureTest'` que passa, e confirmar que a regra de camadas falha ao introduzir temporariamente um import de `UserRepository` no `UserController` (desfazer depois)

## 7. Testes de cenário da spec `user`

- [x] 7.1 Criar `user/UserScenarioTest` (`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})`, limpando `users` no `@BeforeEach`, inclusive o admin da seed) com um teste por Scenario, `@DisplayName("Scenario: <nome>")`, cobrindo 38 dos 41 Scenarios de `specs/user/spec.md`:
  - Criar usuário: 8;
  - Login por email único: 3;
  - Papel do usuário: 5;
  - Senha protegida: 2, lendo `password_hash` via `JdbcTemplate` e verificando com o `PasswordEncoder`;
  - Consultar por id: 3;
  - Listar: 5;
  - Alterar nome, login e role: 4;
  - Alterar senha: 3;
  - Excluir: 3;
  - Corpo ilegível: 2.

  Verificar com `./gradlew test --tests '*UserScenarioTest'`
- [x] 7.2 Criar `user/AdminSeedScenarioTest` (D8, no padrão do `PersistenceStartupScenarioTest`: container `postgres:16` próprio, database novo por teste e aplicação iniciada com `SpringApplicationBuilder`) com os 3 Scenarios de "Administrador inicial":
  - "Banco vazio recebe o administrador", com `EDUCARE_ADMIN_PASSWORD=senhaAdmin123`;
  - "Administrador excluído não é recriado", reiniciando contra o mesmo database;
  - "Produção sem senha do administrador", com o profile `prod`, o datasource do container e sem a variável: a inicialização falha e o admin não existe.

  Verificar com `./gradlew test --tests '*AdminSeedScenarioTest'`
- [x] 7.3 Conferir que cada Scenario da spec tem exatamente um teste correspondente, comparando `grep '^#### Scenario' openspec/changes/add-user-crud/specs/user/spec.md` (41) com os `@DisplayName` de `UserScenarioTest` (38) e `AdminSeedScenarioTest` (3), com os mesmos nomes

## 8. Verificação final

- [x] 8.1 Rodar `./gradlew build` com o Docker ativo e verificar que termina verde, incluindo `jacocoTestCoverageVerification` (mínimo de 90% de linhas e de branches)
