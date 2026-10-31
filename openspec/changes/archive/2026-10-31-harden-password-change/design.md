# Design

## Context

Motivação em proposal.md (seção Why); comportamento em `specs/auth`, `specs/security`, `specs/user` e `specs/api-contract`.

Estado atual que molda a abordagem:

- A troca da própria senha é `AuthController.changeOwnPassword` → `AuthService.changeOwnPassword` → `UserService.changeOwnPassword`, que confere a senha atual com o `PasswordEncoder` e lança `InvalidCurrentPasswordException` (`InvalidFieldException`, `400` com `errors` em `currentPassword`). A redefinição pelo `ADMIN` é `UserController.changePassword` → `UserService.changePassword`. As duas chamam `User.changePasswordHash`, que também é usado pelo construtor de `User`.
- O token é emitido por `shared/security/AccessTokenIssuer`, com `iat` truncado em segundos pelo bean `Clock`, e só tem `iss`, `sub`, `iat` e `exp` (regra do `openspec/config.yaml`).
- Em toda requisição autenticada, `AuthenticatedUserConverter` (`shared/security`) chama `AuthenticatedUserLookup.findById`, implementado por `user/application/UserAuthenticationLookup`, que faz um `findById` no `UserRepository` e devolve `AuthenticatedUser(id, role)`. Usuário inexistente vira `InvalidBearerTokenException("Invalid token")`, convertida em `401` genérico.
- O `auth` não pode importar `user.domain` (ArchUnit): só vê o `UserService` e o `UserAccount`.
- `shared/error/GlobalExceptionHandler` mapeia as bases `InvalidFieldException`, `UnauthorizedException`, `NotFoundException` e `ConflictException`; não há base para `429`. Os erros do contrato são declarados com as meta-anotações de `shared/config` (`@BadRequestResponse` etc.).
- O projeto ainda não registra logs da aplicação (não há `@Slf4j` nem `LoggerFactory` em `src/main`).
- Migrations até `V4__add_authorship_to_users.sql`. Esta change é aplicada antes da `add-child-crud` e fica com a `V5`; a `add-child-crud` passa para a `V6`.

## Goals / Non-Goals

**Goals:**
- Recusar tokens anteriores à última troca de senha sem consulta extra ao banco e sem claim novo no token.
- Manter a regra de senha e a contagem de tentativas no módulo `user`, dono da senha; o `auth` só emite o token novo.

**Non-Goals:**
- Comparação com precisão abaixo de segundos (ver D3).
- Atomicidade estrita do limite sob requisições concorrentes do mesmo usuário (ver Risks).

## Decisions

### D1. Endpoints

| Método | Rota | Sucesso | Erros |
|---|---|---|---|
| `PUT` | `/api/v1/auth/me/password` | **`200 OK`** com `AccessTokenResponse` (`accessToken`, `tokenType`, `expiresIn`); antes `204` | `400` (Bean Validation; `currentPassword` incorreta; `newPassword` igual à atual ou ao login), `401`, **`429`** (novo) |
| `PUT` | `/api/v1/users/{id}/password` | `204 No Content` (sem mudança) | `400`, `401`, `403`, `404` (sem mudança) |

Todos os demais endpoints protegidos passam a responder `401` para token anterior à última troca de senha do usuário, sem mudança no contrato (o `401` já é declarado em todos).

No `AuthController`, a operação ganha `@ResponseStatus(HttpStatus.OK)` (necessário para o springdoc documentar o `200` ao lado dos erros), `@BadRequestResponse` e a nova meta-anotação `@TooManyRequestsResponse` (`shared/config`, no mesmo molde da `@ConflictResponse`). O DTO de resposta é o `AccessTokenResponse` já existente, montado pelo `AuthMapper.toResponse(IssuedAccessToken)`, de modo que o frontend reaproveita o tipo do login.

### D2. Schema

Nova migration `src/main/resources/db/migration/V5__add_password_changed_at_to_users.sql`:

```sql
-- Instante da última troca de senha (própria ou redefinição pelo ADMIN), em segundos inteiros.
-- Tokens com iat anterior a ele são recusados. NULL: o usuário não trocou a senha desde esta
-- migration, e nenhum token dele é recusado por esse motivo.
ALTER TABLE users ADD COLUMN password_changed_at timestamptz;
```

Sem backfill: preencher com `now()` desconectaria todo mundo no deploy sem motivo. Na entidade, `@Column(name = "password_changed_at") private Instant passwordChangedAt;` (nullable), só com `@Getter`.

### D3. Data da troca em segundos, gravada pelo domínio

`User.changePasswordHash(String)` é substituído por `User.changePassword(String passwordHash, Instant changedAt)`, que valida o hash e grava `passwordChangedAt = changedAt.truncatedTo(ChronoUnit.SECONDS)` (com `changedAt` obrigatório). O construtor de `User` passa a validar e atribuir o hash por um método privado e deixa `passwordChangedAt` nulo: um usuário novo não tem token anterior a recusar. O `UserService` passa a receber o bean `Clock` e chama `user.changePassword(passwordEncoder.encode(...), clock.instant())` tanto na troca da própria senha quanto na redefinição pelo `ADMIN` (RN03).

Regra de recusa: `iat < passwordChangedAt`. Como o `iat` é truncado em segundos pelo `AccessTokenIssuer` e a data da troca também, o token novo, emitido depois da troca pelo mesmo `Clock`, tem `iat >= passwordChangedAt` e é aceito.

Alternativas:
- **Comparar em milissegundos**: o token novo, com `iat` truncado para baixo, poderia ficar anterior à troca e ser recusado logo depois de emitido.
- **Contador de versão da senha num claim do token** (`pwdv`): eliminaria a janela de até 1 segundo, mas quebra a regra de claims só com `iss`, `sub`, `iat` e `exp` e exige mudar o emissor; a janela é aceitável (ver Risks).
- **Lista de tokens revogados (`jti`)**: exige tabela e limpeza, e também um claim novo; não se justifica para "derrubar tudo que é anterior a um instante".

### D4. Verificação do token no `shared/security`, sem consulta extra

`AuthenticatedUserLookup.findById(UUID)` passa a devolver `Optional<TokenOwner>`, um novo record de `shared/security`:

```java
public record TokenOwner(AuthenticatedUser user, @Nullable Instant passwordChangedAt) {
    /** A token issued before the last password change (both in whole seconds) is no longer valid. */
    public boolean accepts(@Nullable Instant issuedAt) { ... }
}
```

`accepts` devolve `true` quando `passwordChangedAt` é nulo; quando não é, exige `issuedAt` não nulo e `!issuedAt.isBefore(passwordChangedAt)`. O `UserAuthenticationLookup` monta o `TokenOwner` com o mesmo `findById` de hoje (a coluna vem na mesma linha, RNF01). O `AuthenticatedUserConverter` passa a fazer `lookup.findById(id).filter(owner -> owner.accepts(jwt.getIssuedAt())).map(owner -> new AuthenticatedUserAuthentication(owner.user()))` e, se vazio, lança a mesma `InvalidBearerTokenException("Invalid token")`, de modo que o `401` é idêntico aos outros motivos.

Alternativas:
- **Acrescentar `passwordChangedAt` ao `AuthenticatedUser`**: o principal é injetado nos controllers (`@AuthenticationPrincipal`) e passaria a carregar um dado que só interessa à validação do token.
- **`findById(UUID id, Instant issuedAt)` com a regra no `user`**: a regra de validade de token é de `shared/security`, e o `user` passaria a conhecer detalhes do JWT.
- **Um `OAuth2TokenValidator` no `JwtDecoder`**: roda antes do converter e exigiria uma segunda leitura do usuário.

### D5. Fluxo da troca da própria senha

`UserService.changeOwnPassword(UUID id, String currentPassword, String newPassword)`, na ordem:

1. `passwordAttemptLimiter.ensureAllowed(id)`: se bloqueado, lança `TooManyPasswordAttemptsException` (`429`) antes de qualquer BCrypt, mesmo com a senha certa.
2. `findById(id)`.
3. Senha atual incorreta: `passwordAttemptLimiter.recordFailure(id)` e lança `InvalidCurrentPasswordException` (como hoje). Só essa recusa conta como erro.
4. `newPassword.equals(currentPassword)`: lança `NewPasswordSameAsCurrentException` (`InvalidFieldException` em `newPassword`, "deve ser diferente da senha atual"). Comparar as strings basta, porque a senha atual acabou de ser conferida, e evita um segundo BCrypt.
5. `User.normalizeLogin(newPassword).equals(user.getLogin())` (o login já é gravado em minúsculas com `Locale.ROOT`): lança `NewPasswordSameAsLoginException` (`InvalidFieldException` em `newPassword`, "não pode ser igual ao login"; a mensagem não repete o login).
6. `user.changePassword(passwordEncoder.encode(newPassword), clock.instant())`, `saveAndFlush`, e por fim `passwordAttemptLimiter.reset(id)`.

As regras de tamanho continuam no Bean Validation do `ChangeOwnPasswordRequest`, que roda no controller antes do passo 1; por isso um corpo inválido responde `400` mesmo para um usuário bloqueado, e não conta como erro. As regras 4 e 5 ficam no service (e não numa constraint de classe no DTO) porque dependem da senha atual conferida e do login guardado.

`AuthService.changeOwnPassword` passa a devolver `IssuedAccessToken`: chama `userService.changeOwnPassword(...)` e, depois que a transação dele terminou, `accessTokenIssuer.issue(userId)`. Se a gravação falhar, nenhum token é emitido. O `UserService` continua devolvendo `void` e não conhece o emissor de tokens.

### D6. Limitador de tentativas em memória

Novo `user/application/PasswordAttemptLimiter` (`@Component`, com o bean `Clock`), com `MAX_FAILURES = 5` e `WINDOW = Duration.ofMinutes(15)` como constantes (sem propriedade nem variável de ambiente nova). Estado: `ConcurrentHashMap<UUID, Deque<Instant>>` com os instantes dos erros de cada usuário.

- `ensureAllowed(id)`: descarta os erros com `erro + WINDOW <= agora` e, se restarem 5 ou mais, registra `log.warn("Troca de senha bloqueada por excesso de tentativas: userId={}", id)` e lança `TooManyPasswordAttemptsException`.
- `recordFailure(id)`: descarta os expirados e acrescenta `agora`.
- `reset(id)`: remove a entrada.

Cada operação usa `map.compute(id, ...)`, que serializa o acesso por chave, e remove a entrada quando a fila fica vazia; assim a memória fica limitada a no máximo 5 instantes por usuário com erros recentes. A janela é deslizante: um erro deixa de contar exatamente 15 minutos depois de ocorrido.

Fica em `user/application` porque a regra é sobre a senha do usuário e o `InvalidCurrentPasswordException` é de `user.domain`, que o `auth` não pode ver. Não é `infrastructure` porque não integra sistema externo e contém a regra (5 em 15 min).

Exceções novas: `shared/error/TooManyRequestsException` (base abstrata; o `GlobalExceptionHandler` converte em `429` com `detail` = mensagem, sem `errors`) e `user/domain/TooManyPasswordAttemptsException` ("Muitas tentativas. Tente novamente em alguns minutos."). Os logs (primeiro uso de log na aplicação, com `@Slf4j` do Lombok) levam só o id do usuário: nem senha, nem hash, nem login (RNF04).

Alternativas:
- **Bucket4j/Resilience4j**: dependência nova para algumas dezenas de linhas, e o modelo de "erros em janela deslizante zerados por sucesso" não é um rate limit de requisições.
- **Redis ou tabela no banco**: o `openspec/config.yaml` pede evitar infraestrutura extra no Free Tier; a tabela adicionaria escrita a cada erro. Perder a contagem num restart é aceito pela tarefa.
- **Limite no `auth`**: o `auth` não distingue "senha atual incorreta" de outros `400` sem conhecer exceções de `user.domain`.

### D7. Redefinição pelo `ADMIN`

`UserService.changePassword(UUID id, String password)` passa a chamar `user.changePassword(passwordEncoder.encode(password), clock.instant())`. Não usa o limitador nem as regras "diferente da atual/do login". O `ADMIN` que redefine a própria senha por essa rota também é desconectado (cenário da spec `user`); o frontend já trata `401` levando ao login.

### D8. Testes afetados

- Testes que mudam de comportamento: `AuthScenarioTest` (troca agora `200` com token), `AuthControllerTest`, `AuthServiceTest`, `UserServiceTest` (construtor com `Clock` e limitador), `UserTest` (`changePassword` no lugar de `changePasswordHash`), `UserAuthenticationLookupTest`, `AuthenticatedUserConverterTest`, `AuthorshipScenarioTest` (usa `PUT /api/v1/auth/me/password`) e `ApiContractContentTest` (o `PUT /api/v1/auth/me/password` sai da lista de operações sem corpo).
- O limitador é um singleton compartilhado entre os testes do mesmo contexto Spring; os testes continuam independentes porque cada teste de cenário cria seus usuários (ids novos). Testes de janela usam o `MutableClock`.

## Risks / Trade-offs

- [Um token emitido no mesmo segundo da troca, antes dela, continua válido] → Janela de menos de 1 segundo, aceita pela tarefa e coberta por cenário explícito; eliminá-la exigiria um claim novo (D3).
- [O relógio do sistema voltar (ajuste de NTP) entre a troca e a emissão do token novo] → O token novo poderia ficar com `iat` anterior e ser recusado, e o usuário faria login de novo. Improvável numa única VM com NTP gradual; nenhuma ação.
- [Requisições concorrentes do mesmo usuário podem passar do limite de 5] → `ensureAllowed` e `recordFailure` são operações separadas; com N requisições simultâneas, até N tentativas extras. O BCrypt (custo 10) já limita a vazão e a rota exige um token válido do próprio usuário; aceito.
- [Reiniciar a aplicação zera a contagem] → Aceito pela tarefa (RNF02); o deploy é raro.
- [Mais de uma instância da API no futuro] → Cada instância teria sua contagem. Se isso acontecer, a contagem migra para o banco; registrado aqui para essa decisão.
- [Frontend antigo com o backend novo] → O frontend atual ignora o corpo do `204` e mantém o token antigo, que passa a ser recusado: o usuário é levado ao login depois de trocar a senha. Inofensivo; a subtarefa de front grava o token novo e deve ir ao ar logo depois do backend.
- [A `add-child-crud` ainda cita `V5__create_children.sql` nos artefatos dela] → Ela é aplicada depois desta e precisa usar `V6__create_children.sql`; os artefatos dela são ajustados antes do apply.

## Migration Plan

1. Deploy do backend: o Flyway acrescenta a coluna nula (operação instantânea, sem backfill); nenhuma variável de ambiente nova.
2. Deploy do frontend com o tratamento do `200` com token e do `429`.
3. Validar na Oracle Cloud que a migration foi aplicada (`flyway_schema_history`) e que uma troca de senha desconecta outra sessão.

Rollback: voltar para a imagem anterior pela tag. A coluna extra não quebra a versão anterior (o `ddl-auto: validate` só confere as colunas mapeadas); a versão anterior apenas deixa de recusar os tokens antigos e volta a responder `204`. A coluna não é removida.
