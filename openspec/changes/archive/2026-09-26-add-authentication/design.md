# Design

## Context

Estado atual (motivação em proposal.md, seção Why):

- O módulo `user` já guarda as senhas com BCrypt (`PasswordEncoder` em `shared/security/PasswordEncoderConfiguration`, via `spring-security-crypto`, sem o starter de segurança). Não há `SecurityFilterChain`: toda a API está aberta.
- `UserService` devolve a entidade `User` para o `UserController`. As regras ArchUnit proíbem que outro módulo importe `user.domain` ou `user.api`, e a camada `application` só pode ser acessada pela `api`.
- `shared` não pode depender de nenhum módulo de negócio (regra ArchUnit).
- `GlobalExceptionHandler` converte as exceções em `ProblemDetail`. `NotFoundException` e `ConflictException` são as bases das exceções de domínio.
- Há um `Clock` como bean (`ClockConfiguration`), e os testes usam `MutableClock` para controlar o tempo.
- O profile `dev` é o padrão, inclusive nos testes. O `prod` só recebe valores por variável de ambiente, e o Compose passa string vazia quando uma variável falta no `.env`.

Restrições: uma única instância numa VM do Oracle Free Tier, sem Redis nem outro componente de infraestrutura; nenhum dado pessoal em logs nem no token.

## Goals / Non-Goals

**Goals:**
- Fechar toda a API com um mecanismo que não exija infraestrutura extra e continue stateless.
- Manter as regras de dependência entre módulos: o `auth` usa o `user` só pelo `UserService`, e `shared/security` não conhece nenhum módulo.
- Fazer a exclusão e a mudança de role valerem na requisição seguinte, sem esperar o token expirar.

**Non-Goals:**
- Guardar tokens ou sessões no banco (nenhuma migration nesta change).
- Revogar tokens já emitidos após troca de senha (ver Risks).

## Decisions

### D1. Endpoints

| Método | Rota | Corpo | Acesso | Sucesso | Erros |
|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `LoginRequest {login, password}` | público | `200` + `AccessTokenResponse {accessToken, tokenType, expiresIn}` | `400` validação/JSON, `401` credenciais |
| `GET` | `/api/v1/auth/me` | — | autenticado | `200` + `MeResponse {id, name, login, role, createdAt, updatedAt}` | `401` |
| `PUT` | `/api/v1/auth/me/password` | `ChangeOwnPasswordRequest {currentPassword, newPassword}` | autenticado | `204` | `400` validação/senha atual, `401` |
| todos | `/api/v1/users/**` | (sem mudança) | `ADMIN` | (sem mudança) | + `401`, `403`; `409` nas proteções do ADMIN (D8) |
| `GET` | `/actuator/health/**` | — | público | `200` | — |
| `OPTIONS` | qualquer (preflight CORS) | — | público | `200` | `403` origem não permitida |

- `tokenType` é sempre `"Bearer"`, e `expiresIn` são segundos (`28800`), como no OAuth2, para o frontend não precisar decodificar o token.
- **Rotas do próprio usuário em `/api/v1/auth/me`**, e não em `/api/v1/users/me`: `/api/v1/users/**` fica inteiro para `ADMIN`, o que deixa a regra de acesso simples, e o autoatendimento pertence ao módulo `auth`.
- **Validação do login**: `login` com `@NotBlank @Size(max = 254)`, sem validar o formato de email. Um valor que não é email simplesmente não encontra usuário e dá `401`, sem dizer mais nada. `password` com `@NotBlank @Size(max = 72) @MaxUtf8Bytes(72)`: o `BCryptPasswordEncoder` lança exceção acima de 72 bytes também no `matches`, e sem essa validação o erro seria `500`.
- **`ChangeOwnPasswordRequest`**: `currentPassword` com `@NotBlank @Size(max = 72) @MaxUtf8Bytes(72)`, e `newPassword` com as mesmas regras de `ChangePasswordRequest` (`@NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72)`).

### D2. Estrutura do código

```
shared/security/
├── PasswordEncoderConfiguration.java    # (existente)
├── SecurityConfiguration.java           # SecurityFilterChain, @EnableMethodSecurity, CorsConfigurationSource
├── SecurityProperties.java              # @ConfigurationProperties("educare.security"): jwt.secret, jwt.expiration, cors.allowed-origins
├── JwtConfiguration.java                # JwtEncoder e JwtDecoder (HS256) a partir de SecurityProperties e do Clock
├── AccessTokenIssuer.java               # issue(UUID userId) -> IssuedAccessToken(value, expiresIn)
├── IssuedAccessToken.java               # record
├── AuthenticatedUser.java               # record (UUID id, String role): o principal das requisições
├── AuthenticatedUserLookup.java         # interface: Optional<AuthenticatedUser> findById(UUID id)
├── AuthenticatedUserConverter.java      # Jwt -> AuthenticatedUserAuthentication, consultando o lookup
├── AuthenticatedUserAuthentication.java # AbstractAuthenticationToken com AuthenticatedUser e ROLE_<role>
└── ProblemDetailSecurityHandlers.java   # AuthenticationEntryPoint (401) e AccessDeniedHandler (403) em ProblemDetail

shared/error/
├── UnauthorizedException.java           # nova base -> 401
└── InvalidFieldException.java           # nova base (field, message) -> 400 com errors

auth/
├── api/
│   ├── AuthController.java              # /api/v1/auth
│   └── dto/  LoginRequest, AccessTokenResponse, MeResponse, ChangeOwnPasswordRequest
├── application/
│   └── AuthService.java                 # login, me, changeOwnPassword
└── domain/
    └── InvalidCredentialsException.java # estende UnauthorizedException

user/
├── api/UserController.java              # + @PreAuthorize("hasRole('ADMIN')"); passa o id do ADMIN que age
├── application/
│   ├── UserService.java                 # + authenticate, findAccount, changeOwnPassword; proteções do ADMIN
│   ├── UserAccount.java                 # record exposto a outros módulos (sem senha nem hash)
│   └── UserAuthenticationLookup.java    # implementa AuthenticatedUserLookup
└── domain/
    ├── UserRepository.java              # + findAllByRoleForUpdate
    ├── InvalidCurrentPasswordException.java # estende InvalidFieldException
    ├── SelfDeletionException.java       # estende ConflictException
    └── LastAdminException.java          # estende ConflictException
```

- **Módulo `auth` sem entidade**: ele orquestra o login e o autoatendimento, mas quem conhece senhas e usuários continua sendo o `user`. O `auth` chama só o `UserService` (camada `application`), como pede o CLAUDE.md, e é o primeiro caso em que as regras ArchUnit entre módulos passam a valer.
- **`UserAccount` em `user.application`**: o `auth` não pode receber a entidade `User` (fica em `user.domain`) nem o enum `Role`. O record leva `role` como `String`, e o `UserService` converte. O `UserController` continua usando a entidade pelo `UserMapper`, sem mudança.
- **Inversão de dependência para o principal**: `shared/security` precisa saber se o usuário do token existe e qual é a role dele, mas não pode depender do `user`. Ele define `AuthenticatedUserLookup`, e `user.application.UserAuthenticationLookup` implementa, com `findById` em transação `readOnly`. A dependência fica `user` → `shared`, que é permitida.
  - *Alternativa*: role como claim do token, sem consulta. Descartada: um usuário excluído ou rebaixado manteria o acesso por até 8 horas, e a proteção do ADMIN (D8) perderia o sentido.
  - O custo é uma consulta por chave primária a cada requisição autenticada, desprezível no volume de uma instituição.

### D3. Token: JWT HS256 emitido pelo próprio backend

- Dependências novas: `spring-boot-starter-security` e `spring-boot-starter-oauth2-resource-server` (Nimbus JOSE), com `spring-boot-starter-security-test` e `spring-boot-starter-oauth2-resource-server-test`, conforme a regra de starters do CLAUDE.md. O `spring-security-crypto` explícito sai do `build.gradle`, porque vem com o starter de segurança.
  - O resource server já traz o `BearerTokenAuthenticationFilter`, a extração do header e a validação de assinatura e datas. Não há serviço novo na VM: é só código na mesma JVM.
  - *Alternativa*: `jjwt` com um filtro próprio. Descartada: seria reescrever filtro, extração e tratamento de erro que o Spring Security já oferece.
  - *Alternativa*: sessão HTTP com cookie. Descartada: a API é stateless por decisão do `config.yaml`, e cookie exigiria CSRF.
- **HS256 com chave simétrica** (`NimbusJwtEncoder` com `ImmutableSecret` e `NimbusJwtDecoder.withSecretKey`, ambos com `MacAlgorithm.HS256`). Só este backend emite e valida, então uma chave compartilhada basta. Se outro serviço precisar validar tokens, troca-se para RS256 sem mudar a spec.
- **Claims**: `iss = "educare-backend"`, `sub = <id do usuário>`, `iat` e `exp`. Nada de email, nome ou role (spec: token sem dados pessoais; a role vem do banco, D2).
- **Validação**: assinatura, `iss` e datas. O `JwtTimestampValidator` recebe o `Clock` da aplicação e tolerância zero (o padrão é 60 s), porque há um só servidor e os testes de cenário controlam o tempo com `MutableClock`. O `AccessTokenIssuer` também usa o `Clock` para `iat` e `exp`.
- **Validade** em `educare.security.jwt.expiration` (padrão `8h`, no `application.yaml`). Não vira variável de ambiente, porque a spec fixa 8 horas.
- O Boot desliga o usuário em memória gerado (`UserDetailsServiceAutoConfiguration`) quando há um `JwtDecoder` próprio. A task correspondente verifica que o log de "generated security password" não aparece.

### D4. Configuração e validação na inicialização

- `SecurityProperties` é um record `@ConfigurationProperties("educare.security")` com `jwt.secret`, `jwt.expiration` e `cors.allowed-origins` (`List<String>`), validado no construtor compacto. Um valor inválido lança exceção no bind, e a aplicação não inicia:
  - `secret`: obrigatório, com no mínimo 32 bytes em UTF-8 (256 bits, o mínimo do HS256), e não pode ter a forma de placeholder não resolvido (`${...}`);
  - `allowed-origins`: lista não vazia, e cada item é uma URL `http` ou `https` com host, sem caminho, query nem `*`. O `*` é recusado explicitamente.
- Fica em `shared/security`, e não em `shared/config`, porque tem lógica e precisa entrar na medição de cobertura.
- Valores por profile:
  - `application-dev.yaml`: `secret: ${EDUCARE_JWT_SECRET:<chave fixa de dev, com mais de 32 bytes>}` e `allowed-origins: ${EDUCARE_CORS_ALLOWED_ORIGINS:http://localhost:5173}`;
  - `application-prod.yaml`: `${EDUCARE_JWT_SECRET}` e `${EDUCARE_CORS_ALLOWED_ORIGINS}`, sem padrão;
  - `docker-compose.yaml`: as duas variáveis no serviço `api`, lidas do `.env`.
- Várias origens vêm separadas por vírgula na mesma variável, que o binder do Spring converte em lista.
- A variável vazia (Compose sem a variável no `.env`) e a referência não resolvida caem nas mesmas validações, e a aplicação não inicia. É o mesmo comportamento da senha do admin, mas verificado em Java, porque aqui não há migration.

### D5. Filter chain e autorização

- `SecurityFilterChain`:
  - `csrf` desligado (sem cookies; o token vai no header), `sessionManagement` `STATELESS`, `httpBasic`, `formLogin` e `logout` desligados;
  - `cors` com o `CorsConfigurationSource` da D6, que atende o preflight antes da autenticação;
  - `permitAll` para `POST /api/v1/auth/login`, `/actuator/health` e `/actuator/health/**`, e para o dispatch `ERROR` (para que um `404` de um usuário autenticado não vire `401` no `/error`);
  - `anyRequest().authenticated()`. Uma rota inexistente sem token dá `401`, sem revelar se ela existe;
  - `oauth2ResourceServer(jwt -> jwt.jwtAuthenticationConverter(authenticatedUserConverter))`, com o `authenticationEntryPoint` e o `accessDeniedHandler` da D7.
- **Autorização por role no próprio módulo**: `@EnableMethodSecurity` e `@PreAuthorize("hasRole('ADMIN')")` na classe `UserController`. A regra fica junto dos endpoints que ela protege, e `shared` não precisa conhecer as rotas dos módulos.
  - *Alternativa*: `requestMatchers("/api/v1/users/**").hasRole("ADMIN")` na filter chain. Descartada: colocaria conhecimento do módulo `user` em `shared`.
  - O `AccessDeniedException` lançado pelo `@PreAuthorize` não é capturado pelo `GlobalExceptionHandler` (ele não tem handler genérico de `Exception`), então chega ao `ExceptionTranslationFilter` e ao `AccessDeniedHandler`. Um teste cobre isso, para que um handler genérico adicionado no futuro não transforme o `403` em `500`.
- **`AuthenticatedUserConverter`**: lê o `sub` como UUID e chama o `AuthenticatedUserLookup`. `sub` inválido ou usuário inexistente lançam `InvalidBearerTokenException`, que o filtro converte em `401`. A authority é `ROLE_<role>`.
- Os controllers recebem o usuário com `@AuthenticationPrincipal AuthenticatedUser`.

### D6. CORS

- `CorsConfigurationSource` para `/**`: `allowedOrigins` da configuração (exatas, sem padrão), métodos `GET`, `POST`, `PUT`, `DELETE` e `OPTIONS`, headers `Authorization` e `Content-Type`, `exposedHeaders: Location` (o frontend lê o id do recurso criado), `allowCredentials: false` (o token não é cookie) e `maxAge` de 1 hora, para reduzir preflights.
- Uma origem fora da lista recebe `403` no preflight, pelo `DefaultCorsProcessor`, sem `Access-Control-Allow-Origin`.
- Padrão do `dev` `http://localhost:5173` (porta padrão do Vite): é uma suposição sobre o frontend, e mudar é só configurar a variável.

### D7. Respostas 401 e 403 em ProblemDetail

- `AuthenticationEntryPoint` próprio: responde `401` com `application/problem+json`, `title` `Unauthorized` e `detail` genérico ("Autenticação necessária"), e `WWW-Authenticate: Bearer`.
  - O `BearerTokenAuthenticationEntryPoint` padrão não é usado: ele coloca em `error_description` o motivo da recusa (por exemplo, a data de expiração), e a spec proíbe indicar o motivo.
- `AccessDeniedHandler` próprio: `403` com `application/problem+json`, `title` `Forbidden` e `detail` "Acesso negado".
- Os dois escrevem o `ProblemDetail` com o `JsonMapper` do contexto, no mesmo formato do `GlobalExceptionHandler`.
- **Falha de login** não passa pelo entry point: o `/login` é público, e a `InvalidCredentialsException` (base nova `UnauthorizedException`) é convertida em `401` pelo `GlobalExceptionHandler`, com `detail` "Login ou senha inválidos", o mesmo para email inexistente e senha errada.

### D8. Login, autoatendimento e proteções do ADMIN no `UserService`

- **`authenticate(login, password)`** → `Optional<UserAccount>`:
  - normaliza o login com `User.normalizeLogin` e busca com `findByLogin`;
  - se o usuário não existe, ainda roda `passwordEncoder.matches` contra um hash BCrypt fixo, para que o tempo de resposta não revele se o email está cadastrado;
  - nunca registra o login em log.
- `AuthService.login` chama `authenticate`. Se vier vazio, lança `InvalidCredentialsException`; senão, chama `AccessTokenIssuer.issue(account.id())`.
- **`findAccount(id)`** → `UserAccount` para o `/me`.
- **`changeOwnPassword(id, currentPassword, newPassword)`**: se `currentPassword` não corresponde, lança `InvalidCurrentPasswordException`, que estende a nova base `InvalidFieldException("currentPassword", ...)`. O `GlobalExceptionHandler` a converte em `400` com `errors: [{field, message}]`, no mesmo formato da Bean Validation.
  - O nome do campo da API aparece numa exceção do `user`, o que é um acoplamento pequeno. É aceito para manter o formato de erro uniforme, sem que o controller capture exceções.
- **Proteções do ADMIN** (spec `user`, "Sistema nunca fica sem ADMIN"):
  - `delete(id, actorId)`: se `id == actorId`, lança `SelfDeletionException` (`409`);
  - `update(id, ..., role, actorId)` e `delete`: quando o alvo é `ADMIN` e deixa de ser (rebaixamento ou exclusão), o service carrega os `ADMIN` com `findAllByRoleForUpdate(ADMIN)` (`@Lock(PESSIMISTIC_WRITE)`, ou seja, `SELECT ... FOR UPDATE`) e lança `LastAdminException` (`409`) se só houver um;
  - o lock serializa dois `ADMIN` que se rebaixam ao mesmo tempo: no PostgreSQL em `READ COMMITTED`, a segunda transação espera a primeira e, ao reler, já não vê o rebaixado como `ADMIN`. Assim ela também recusa, e o sistema não fica sem `ADMIN`;
  - `actorId` vem do `@AuthenticationPrincipal` no `UserController`. O domínio não conhece o usuário autenticado, só o service recebe o id.
- As mensagens dos `409` não contêm email nem nome.

### D9. Migrations

Nenhuma. A autenticação usa `users.login`, `users.password_hash` e `users.role`, que já existem, e os tokens não são guardados.

### D10. Estratégia de testes

- **Unitários**:
  - `SecurityPropertiesTest`: segredo ausente, vazio, com placeholder, com 31 e com 32 bytes; origens vazia, `*`, com caminho, sem esquema e válidas;
  - `AccessTokenIssuerTest` e `JwtConfiguration`: token emitido é decodificado pelo decoder, recusado depois de `exp` com o `MutableClock`, recusado com outra chave, e sem dados pessoais nas claims;
  - `AuthenticatedUserConverterTest`: `sub` inválido, usuário inexistente e authority `ROLE_<role>`;
  - `AuthServiceTest` e `UserServiceTest` (novos métodos, proteções do ADMIN e a comparação com o hash fixo quando o login não existe).
- **Web** (`@WebMvcTest`, importando `SecurityConfiguration`, `JwtConfiguration`, os handlers e o `GlobalExceptionHandler`, com `AuthenticatedUserLookup` mockado):
  - `AuthControllerTest`: login (`200`, `400`, `401`), `/me` e troca de senha com e sem token;
  - `UserControllerTest`: passa a enviar um token de `ADMIN`, e ganha casos de `401` sem token e `403` com `USER`;
  - `SecurityConfigurationTest`: formato do `401` (com `WWW-Authenticate` e sem motivo), `403`, rotas públicas e CORS (preflight permitido, recusado e `Location` exposto).
  - Um helper em `shared/testsupport` (`AccessTokens`) gera tokens reais com o `AccessTokenIssuer` para um id dado, e os testes configuram o lookup mockado para esse id. Assim os testes web exercitam o caminho verdadeiro do filtro.
- **Persistência**: `UserRepositoryTest` ganha `findByLogin` e `findAllByRoleForUpdate`.
- **Cenário** (`@SpringBootTest` + Testcontainers + `MutableClockConfiguration`):
  - `auth/AuthScenarioTest`: os Scenarios da spec `auth`;
  - `shared/security/SecurityScenarioTest`: os Scenarios da spec `security` que rodam com a aplicação no ar. O CORS usa `@TestPropertySource` com a origem `https://educare.example.org`;
  - `shared/security/SecurityStartupScenarioTest`: os Scenarios de inicialização em `prod` (sem chave, chave curta, chave válida, sem origens e `*`), no padrão do `AdminSeedScenarioTest` (`SpringApplicationBuilder` e um banco próprio);
  - `user/UserScenarioTest`: todos os cenários passam a usar o token de um `ADMIN` criado no `@BeforeEach` (Zélia, `zelia@educare.org`) depois de limpar a tabela, e entram os Scenarios novos da spec `user`.
  - Um helper em `shared/testsupport` (`TestUsers`) insere usuários com `JdbcTemplate` e o `PasswordEncoder`, e faz login pela API para obter o token. Os cenários passam pelo fluxo real de login.
- **ArchUnit**: as regras existentes passam a valer entre `auth` e `user`. Nenhuma regra nova é necessária. Os testes precisam continuar verdes com o `auth` acessando só `user.application`.

## Risks / Trade-offs

- [Token continua válido até expirar após troca de senha] → Janela máxima de 8 horas. Exclusão e mudança de role já valem na hora (D2). Se for preciso, uma change futura pode guardar a data da última troca de senha e recusar tokens emitidos antes, sem mudar o formato do token.
- [Sem limite de tentativas de login] → O custo do BCrypt (~100 ms por tentativa) já limita a força bruta numa instância pequena. O limite de tentativas fica nos Non-goals e pode vir depois sem mudar a API.
- [Chave HS256 vazada permite forjar tokens de qualquer usuário] → A chave só existe no `.env` da VM, nunca no git (o padrão do `dev` é inútil em produção). Trocar a chave invalida todos os tokens, e os usuários só precisam fazer login de novo.
- [Onde o frontend guarda o token (XSS)] → Fora deste repositório. A recomendação ao frontend é guardar em memória ou `sessionStorage`, nunca em `localStorage` compartilhado, e o token não contém dados pessoais.
- [Uma consulta ao banco por requisição autenticada] → Busca por chave primária, dentro dos limites da VM. Se virar gargalo, um cache local curto (Caffeine, sem infraestrutura extra) resolve.
- [**BREAKING** para quem chama a API sem token] → Não há frontend em produção dependendo da API aberta. O frontend passa a fazer login e enviar o token.
- [Admin inicial com senha padrão `educare123` no `dev`] → Continua só no `dev`. Forçar a troca no primeiro acesso segue nos Non-goals.
- [Padrão do CORS no `dev` pode não bater com a porta do frontend] → Basta configurar `EDUCARE_CORS_ALLOWED_ORIGINS` localmente.

## Migration Plan

- Antes do deploy, no `.env` da VM:
  - `EDUCARE_JWT_SECRET`: gerar com `openssl rand -base64 48` (bem mais que 32 bytes);
  - `EDUCARE_CORS_ALLOWED_ORIGINS`: a URL pública do frontend.
- Sem migration de banco. O deploy é trocar a imagem, e o admin inicial já existente consegue fazer login.
- Rollback: voltar a imagem anterior. Não há dados a desfazer, mas a imagem anterior deixa a API aberta, então só deve ser usada fora da internet.
