# Design

## Context

Motivação em `proposal.md`. O que existe hoje e molda a abordagem:

- O único limitador é o `PasswordAttemptLimiter` (`user/application`): janela deslizante em memória, `ConcurrentHashMap<UUID, Deque<Instant>>`, relógio pelo bean `Clock`, e a recusa por uma exceção que estende `shared/error/TooManyRequestsException`, convertida em `429` pelo `GlobalExceptionHandler`.
- A autenticação é do resource server na filter chain de `shared/security`; o principal é um `AuthenticatedUser(id, role)`. A regra por role fica em `@PreAuthorize`, nunca na filter chain.
- O `429` só aparece no contrato da troca da própria senha, pela meta-anotação `@TooManyRequestsResponse`. O `401` das operações protegidas é acrescentado pelo `UnauthorizedResponseCustomizer`.
- O Actuator expõe só o health, e a spec de security exige isso. Uma instância da API, num k3s de nó único; os logs do pod são lidos com `kubectl logs`.
- Os `@WebMvcTest` importam `SecurityConfiguration`, `JwtConfiguration` e `ClockConfiguration` à mão. O slice já inclui sozinho os beans `WebMvcConfigurer`, `Filter` e `FilterRegistrationBean`.
- Os testes `@SpringBootTest` compartilham o contexto (e, portanto, o estado em memória dos beans) entre classes, com o relógio real, salvo os que usam o `MutableClock`.

## Goals / Non-Goals

**Goals:**
- Limite por usuário que recusa antes de executar a operação e responde pelo mesmo caminho dos outros erros (`GlobalExceptionHandler` → `ProblemDetail`).
- Log de medição com custo desprezível e sem dados pessoais por construção: só campos fixos, nunca valores da requisição.
- Nenhuma dependência nova nem mudança no Deployment.

**Non-Goals:**
- Limite distribuído (várias instâncias) ou persistido.
- Histograma de latência na aplicação: o p95 sai dos logs, no relatório.
- Limpeza periódica das entradas do limitador (ver Riscos).

## Decisions

### D1. Endpoints e contrato

Nenhum endpoint novo. Toda operação que exige token passa a poder responder `429 Too Many Requests` (`application/problem+json`, header `Retry-After`). As rotas públicas não mudam. Sem migration: nada muda no schema.

No contrato, um `TooManyRequestsResponseCustomizer` em `shared/config`, irmão do `UnauthorizedResponseCustomizer` e com o mesmo critério de "exige token" (a `security` da operação, ou a do documento), acrescenta o `429` (`ProblemResponses.problemResponse("Too Many Requests")`) quando ele ainda não está declarado. A meta-anotação `@TooManyRequestsResponse` fica sem uso (a troca da própria senha passa a receber o `429` pelo customizer) e é removida, junto com o uso dela no `AuthController`.

*Alternativa*: estender o `UnauthorizedResponseCustomizer` para acrescentar `401` e `429`. Descartada para manter uma responsabilidade por customizer e não mudar o nome nem os testes dele.

### D2. Onde o limite é aplicado: `HandlerInterceptor`

`shared/security/RequestRateLimitInterceptor` (`HandlerInterceptor`), registrado por `shared/security/RequestRateLimitConfiguration` (`@Configuration` que implementa `WebMvcConfigurer`, declara o bean do limitador e habilita as propriedades da D4). No `preHandle`, se a `Authentication` do `SecurityContextHolder` tiver um `AuthenticatedUser` como principal, chama `limiter.acquire(userId)`; sem usuário autenticado, passa direto (o `401` já foi respondido pela filter chain nesses casos).

Rotas excluídas no registro (`excludePathPatterns`): `/api/v1/auth/login`, `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`. A exclusão do login é explícita porque o resource server autentica qualquer requisição que traga um Bearer válido, inclusive numa rota `permitAll`.

Por que interceptor e não um filtro na filter chain:
- uma exceção lançada no `preHandle` passa pelos `HandlerExceptionResolver`s, então o `429` sai do `GlobalExceptionHandler` como os demais erros (como pede a tarefa), sem escrever `ProblemDetail` à mão;
- roda depois da autenticação e antes do `@PreAuthorize` e do controller, então a operação não é executada;
- a filter chain de `shared/security` continua só com autenticação.

Como a configuração é um `WebMvcConfigurer` autocontido (depende só do `Clock`, que os `@WebMvcTest` já importam), os testes web passam a ter o limite real sem mudar os `@Import` existentes; com poucas requisições por teste, ele nunca é atingido.

*Alternativa*: `OncePerRequestFilter` depois do `BearerTokenAuthenticationFilter`. Descartada: teria de escrever o `ProblemDetail` por conta própria (como o `ProblemDetailSecurityHandlers`) e colocaria uma regra de uso na filter chain.

### D3. Limitador: `shared/security/RequestRateLimiter`

Mesmo desenho do `PasswordAttemptLimiter`, com uma operação só:

- `Map<UUID, Deque<Instant>>` em `ConcurrentHashMap`; `acquire(userId)` faz, dentro de um `compute` (atômico por usuário), a poda dos instantes que saíram da janela (um instante deixa de contar exatamente `window` depois, igual ao limitador da senha) e então: com menos de `maxRequests` instantes, acrescenta `now` e retorna; senão, não acrescenta nada e lança `RequestRateLimitExceededException` com o `Retry-After` = `ceil((maisAntigo + window - now) / 1s)`, no mínimo 1.
- Recusas não entram na contagem (spec "Requisições recusadas não prolongam o bloqueio").
- Relógio pelo bean `Clock`, como os outros: os testes de cenário controlam o tempo com o `MutableClock`.
- Log no `WARN` na primeira recusa de cada bloqueio, só com o `userId` (mesma prática do `PasswordAttemptLimiter`, que já registra `userId`); as recusas seguintes do mesmo bloqueio não geram log, para um laço não encher o log.

Fica em `shared/security`, e não num módulo, porque vale para a API inteira e depende do principal da autenticação.

*Alternativas*: janela fixa por minuto (um contador por usuário) usa menos memória, mas deixa passar até `2 × maxRequests` na virada do minuto e não segue "no mesmo estilo do limitador da troca de senha". Token bucket (Bucket4j) é dependência nova sem ganho para um único nó.

### D4. Configuração: `educare.rate-limit`

Record `shared/security/RateLimitProperties` (`@ConfigurationProperties("educare.rate-limit")`, validado no construtor compacto, como o `SecurityProperties`): `maxRequests` (positivo, padrão `120`) e `window` (`Duration`, positiva, padrão `1m`), com os padrões no `application.yaml` (todos os profiles). Nenhuma variável de ambiente obrigatória: o `prod` usa os padrões, e ajustar depois da medição é mudar o `application.yaml` ou passar `EDUCARE_RATE_LIMIT_MAX_REQUESTS` no Deployment.

### D5. `429` com `Retry-After` no `GlobalExceptionHandler`

- `shared/error/TooManyRequestsException` ganha `retryAfterSeconds()` (`OptionalLong`, vazio por padrão, então o `TooManyPasswordAttemptsException` não muda).
- `shared/security/RequestRateLimitExceededException` estende a base, com a mensagem `Muitas requisições. Aguarde um instante.` e o `retryAfterSeconds`.
- O `handleTooManyRequests` passa a devolver `ResponseEntity<ProblemDetail>`, com o header `Retry-After` quando presente. O corpo continua igual.

### D6. Log de medição: `shared/observability/RequestLogFilter`

`OncePerRequestFilter` declarado como `@Component` com `@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)` (o Spring Boot registra beans `Filter` com a ordem deles), anterior à da filter chain do Spring Security, para que a duração inclua autenticação, limite e os `401`. Depois do `chain.doFilter` (num `finally`), registra no `INFO`:

```
http method=GET route=/api/v1/children/{id} status=200 durationMs=12
```

- `route`: o atributo `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE` da requisição. Ausente (recusa da filter chain) ou igual a `/**` (o handler de recursos estáticos, que atende as rotas inexistentes) → `unmatched`. Assim nenhum caminho real chega ao log.
- `durationMs`: `System.nanoTime()` antes e depois, em milissegundos inteiros.
- `shouldNotFilter` para `/actuator/**` (os probes do Kubernetes rodam a cada poucos segundos).
- Se o logger não estiver no `INFO`, o filtro só repassa a requisição. Desligar: `logging.level.com.manuelaalecio.educare_backend.shared.observability=WARN` (ou a variável `LOGGING_LEVEL_...` no Deployment).
- Os campos são só os quatro acima, montados a partir de valores do servidor (método, modelo da rota, status, tempo); por isso não há como entrar dado pessoal, query string ou id.

O pacote `shared/observability` é novo e não depende de nenhum módulo de negócio (ArchUnit já verifica `shared`).

*Alternativas*: `http.server.requests` do Micrometer exposto no Actuator. Descartada pela decisão da tarefa (o Actuator continua só com o health) e porque o p95 exigiria ligar histogramas e exportar as métricas. Log de acesso do Tomcat: registraria o caminho real e a query string (ids e nomes).

### D7. Relatório da medição

Seção "Medição de requisições" no README, com o roteiro (navegação + 10 F5, como no CA07 da tarefa) e o comando que lê `kubectl logs -n educare deploy/educare-backend --since=<intervalo>`, filtra as linhas `http method=` e, com `awk` e `sort`, imprime por `method route` o número de chamadas e o p95 de `durationMs` (posição `ceil(0,95 × n)` da lista ordenada). O relatório antes/depois é feito à mão e anexado à tarefa.

### D8. Testes e o estado em memória entre testes

O limitador guarda estado num bean compartilhado pelos `@SpringBootTest` com contexto em cache, e com relógio real; uma suíte rápida pode passar de 120 requisições por minuto com o mesmo usuário (o admin da seed, por exemplo) e quebrar testes que nada têm a ver com o limite. Para isso:

- `src/test/resources/config/application.yaml` (só no classpath de teste; a pasta `config/` tem precedência sobre `classpath:/application.yaml` e não o esconde) define `educare.rate-limit.max-requests: 100000`.
- O `RequestRateLimitScenarioTest` volta ao valor de produção com `@SpringBootTest(properties = "educare.rate-limit.max-requests=120")` e usa o `MutableClock`, com usuários criados pelo próprio teste.
- Um teste de binding confere que o `application.yaml` principal define `120` e `1m`, para o padrão real não ficar sem cobertura.

O CLAUDE.md ganha essa armadilha na seção "Armadilhas".

## Risks / Trade-offs

- [Entradas de usuários que pararam de usar a API ficam no mapa até a próxima requisição deles] → no máximo `maxRequests` instantes por usuário, e o número de usuários é pequeno (funcionários de uma instituição). Se crescer, uma limpeza agendada resolve sem mudar a spec.
- [O BFF chama `/auth/me` a cada carregamento SSR, então ele conta no limite] → 120/min é bem acima do uso normal (o CA03 da tarefa dá 10 F5 em 30 s, cerca de 20 requisições). O valor é configurável e será revisto com a medição.
- [O limite zera ao reiniciar o pod ou num rolling update, e durante o rolling update cada pod tem sua contagem] → aceitável: é proteção contra excesso, não controle de cota.
- [Uma linha de log por requisição aumenta o volume de log do pod] → linha curta, sem o health; pode ser desligada pelo nível de log sem novo deploy de código.
- [Mudança de contrato: `429` em toda operação autenticada] → o frontend regenera os tipos e trata o `429` (já faz parte da tarefa do front); nenhuma resposta de sucesso muda.

## Migration Plan

1. Merge na `main` e publicação de uma tag `vX.Y.Z` (o Argo CD Image Updater sobe a maior tag). Nada a mudar no Deployment nem no Secret `educare-env`.
2. Conferir no pod que as linhas `http method=` aparecem e que uma rota com id aparece como modelo.
3. Registrar a linha de base (roteiro do README) **antes** do deploy das mudanças de cache do frontend; repetir depois e anexar o relatório à tarefa.
4. Conferir a memória da VM depois do deploy.

Rollback: fixar a tag anterior no `ImageUpdater` ou voltar pelo histórico do Argo CD. Sem migration, a volta não tem efeito no banco. Para só aliviar o limite sem rollback, aumentar `educare.rate-limit.max-requests`.
