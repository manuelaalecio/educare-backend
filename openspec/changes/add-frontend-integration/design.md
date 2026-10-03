# Design

## Context

- O springdoc 3.1.1 já gera o documento em `/v3/api-docs`, ligado só no `dev` (`springdoc.*.enabled` no `application-dev.yaml`). O `OpenApiConfiguration` declara apenas `info` e o esquema `bearer-jwt` global, e os controllers não têm anotações de documentação. Por isso o documento atual:
  - não descreve os erros, que vêm do `GlobalExceptionHandler` e dos handlers de `shared/security` (estes últimos fora do alcance do springdoc);
  - não marca nenhum campo de resposta como obrigatório, porque os DTOs são records sem `@Schema`;
  - traz `servers` com `http://localhost:<porta>`.
- O login já fica sem requisito de segurança por um `@SecurityRequirements` vazio no `AuthController`.
- O `org.jspecify.annotations.Nullable` ainda não é usado no código principal.
- Endpoints atuais: `auth` (`POST /login`, `GET /me`, `PUT /me/password`) e `user` (CRUD em `/api/v1/users`, com `@PreAuthorize("hasRole('ADMIN')")` na classe e listagem em `PagedModel`). A `add-child-crud` ainda não foi aplicada.
- Os testes rodam sem profile, com a documentação desligada. O `ApiDocsScenarioTest` a liga por propriedade.
- O repositório é público, e o frontend (BFF no TanStack Start, na Vercel) vai baixar o contrato de `https://raw.githubusercontent.com/manuelaalecio/educare-backend/main/openapi/educare-api.json`.
- O Caddy do `deploy/` recebe o endereço por `SITE_ADDRESS` (hoje `:80`) e já publica a porta 443.

## Goals / Non-Goals

**Goals:**
- Contrato gerado da aplicação real, sem manter um YAML à mão em paralelo, e verificado em todo build.
- Tipos gerados no frontend sem `?` em campos que sempre vêm e com o corpo de erro tipado.
- Tráfego entre o BFF e a API cifrado.

**Non-Goals:**
- Anotar descrições e exemplos em todos os campos (`@Schema(description=...)`). Só o necessário para a forma do contrato.
- Versionar o contrato por release (tags/semver do contrato). A `main` é a fonte; o frontend fixa o que baixou ao commitar os tipos gerados.

## Decisions

### D1. Contrato gerado por teste e comparado com o arquivo commitado
Um teste de cenário (`shared/config/ApiContractScenarioTest`, `@SpringBootTest` + Testcontainers, com `springdoc.api-docs.enabled=true` por `@TestPropertySource`) busca `/v3/api-docs` e normaliza o JSON: reserializa com as chaves ordenadas, indenta com 2 espaços e termina com `\n`. Depois compara o resultado com `openapi/educare-api.json`.
- Modo normal: se o arquivo faltar ou for diferente, o teste falha com a mensagem `Contrato da API desatualizado: rode ./gradlew updateApiContract e commite openapi/educare-api.json`.
- Modo de atualização: com a system property `educare.apiContract.update=true`, o teste grava o arquivo e passa.
- Nova task Gradle `updateApiContract` (tipo `Test`), que roda só esse teste com a property ligada. A task `test` padrão não repassa a property, então o CI nunca reescreve o arquivo.

**Alternativas:** o plugin `springdoc-openapi-gradle-plugin` sobe a aplicação com `bootRun` e exige banco e profile próprios no build, além de ser uma dependência a mais. Um contrato escrito à mão (contract-first) duplica o trabalho e diverge do código. O teste reaproveita o Testcontainers que o build já usa.

### D2. Documento determinístico e sem ambiente
- `springdoc.writer-with-order-by-keys: true` e `springdoc.writer-with-default-pretty-printer: true` no `application.yaml`, valendo em todos os profiles. A normalização do D1 garante a ordem mesmo se o springdoc mudar.
- O bean `OpenAPI` passa a declarar `servers: [{url: "/"}]`, o que também evita que o springdoc calcule o host da requisição. O Swagger UI do `dev` continua funcionando, porque é servido pela própria API.

### D3. Erros declarados por anotação nas operações e por customizador global
- Schema `ProblemDetail` registrado uma vez em `components.schemas` pelo `OpenApiConfiguration`, com `errors` como array de `FieldError {field, message}` (ambos obrigatórios).
- `OpenApiCustomizer` em `shared/config`, aplicado no fim da geração:
  1. Toda operação com requisito de segurança recebe `401` (`application/problem+json`, `$ref` para `ProblemDetail`).
  2. Remove as respostas genéricas que o springdoc infere a partir do `@RestControllerAdvice` e que não se aplicam à operação. Para isso, liga `springdoc.override-with-generic-response: false` no `application.yaml`, e assim só ficam as respostas declaradas.
- Nos controllers, `@ApiResponse(responseCode = "...", content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/ProblemDetail")))` para os erros que cada operação tem nas specs (`400`, `403`, `404`, `409`). Para não repetir o bloco, cria-se em `shared/config` uma meta-anotação por status (`@BadRequestResponse`, `@ForbiddenResponse`, `@NotFoundResponse`, `@ConflictResponse`). O `@ForbiddenResponse` vai na classe `UserController`, junto do `@PreAuthorize`.
- O login declara `401` explicitamente (credenciais inválidas) e mantém o `@SecurityRequirements` vazio que já tem, o que o deixa sem requisito de segurança e, portanto, fora da regra 1 do customizador.

Essas anotações são de documentação e ficam na camada `api`. Não mudam regra de dependência nenhuma: `shared` não passa a depender de módulos, só os módulos usam as meta-anotações de `shared`.

**Alternativa:** um customizador que deduz os erros das exceções lançadas pelos services. Seria frágil e mágico, e as specs já dizem quais erros cada operação tem.

### D4. Campos obrigatórios nos schemas de resposta
Um `OpenApiCustomizer` marca como `required` todas as propriedades dos schemas usados **só em respostas** cujo campo Java não seja anotado com `@Nullable` (`org.jspecify.annotations.Nullable`, já presente no Spring Boot 4). Os DTOs de resposta atuais não têm campos opcionais. A `add-child-crud` terá vários (`cpf`, `rg` etc.) e deve anotá-los com `@Nullable`; isso fica registrado no CLAUDE.md.
- Nos schemas de request, o springdoc já marca `required` a partir de `@NotNull`/`@NotBlank`, e isso continua como está.
- `PagedModel<UserResponse>` gera `PagedModelUserResponse` com `content` e `page` obrigatórios.

**Alternativa:** `@Schema(requiredMode = REQUIRED)` em cada componente de cada record. Seria verboso, e esquecer uma anotação gera um tipo opcional sem nenhum aviso. O padrão "obrigatório salvo `@Nullable`" falha para o lado seguro.

### D5. HTTPS com Caddy e sslip.io
`SITE_ADDRESS=<ip-publico-com-hifens>.sslip.io` no `.env` da VM (ex.: `129-153-10-20.sslip.io`): o sslip.io resolve o nome para o próprio IP, e o Caddy obtém o certificado do Let's Encrypt pelo desafio HTTP na porta 80. Com isso, o Caddy redireciona HTTP→HTTPS sozinho, o `deploy/` não muda, e o health check do deploy segue em `http://localhost:8080` dentro da VM. Passos manuais (README):
- liberar 443/TCP na security list da Oracle e no firewall da VM (`iptables`/`firewalld` da imagem Oracle Linux/Ubuntu);
- ajustar o `SITE_ADDRESS` e as origens do CORS (`EDUCARE_CORS_ALLOWED_ORIGINS=https://<projeto>.vercel.app`, para cumprir a regra do `prod`, mesmo sem uso pelo BFF);
- reiniciar com `docker compose up -d`.

**Alternativas:** Cloudflare Tunnel (mais um agente na VM de 1 GB e dependência de conta externa) ou um domínio próprio (custo, e fica para quando houver). O sslip.io não exige cadastro e pode ser trocado por um domínio só no `.env`.

## Risks / Trade-offs

- [Mudança no backend quebra o frontend sem aviso] → o build do backend obriga a regenerar o contrato, e o diff do `openapi/educare-api.json` no PR mostra a mudança. O frontend só percebe quando regenerar os tipos; a change irmã documenta esse passo.
- [Atualização do springdoc muda a forma do documento e falha o build sem mudança de API] → é o comportamento desejado: regenerar e revisar o diff. A versão do springdoc fica fixa no `build.gradle`.
- [Esquecer o `@Nullable` num campo de resposta opcional] → o tipo gerado no front diz "obrigatório", e o front pode quebrar com `null`. Mitigação: o CLAUDE.md registra a regra, e a `add-child-crud` passa a ter um teste do contrato listando os campos opcionais de `ChildResponse`.
- [sslip.io indisponível ou limite de emissão do Let's Encrypt] → o Caddy guarda o certificado no volume `caddy_data` e renova com antecedência. Se o sslip.io cair, troca-se por um domínio no `.env`. É um serviço gratuito sem SLA, aceitável para um sistema interno pequeno.
- [Todas as chamadas chegam à API pelos IPs da Vercel] → um rate limit futuro por IP precisaria do IP real repassado pelo BFF. Isso fica registrado aqui e é non-goal agora.
- [Contrato público expõe a superfície da API] → o código já é público, e o contrato não contém segredos nem endereços (spec).

## Migration Plan

1. Fazer o merge desta change (o build já verifica o contrato) e o deploy pela tag, como de costume. Não há migration de banco.
2. Na VM: liberar a porta 443, ajustar `SITE_ADDRESS` e `EDUCARE_CORS_ALLOWED_ORIGINS` no `.env` e rodar `docker compose up -d`. Conferir `curl https://<nome>.sslip.io/actuator/health`.
3. Avisar o frontend da URL `https://...` para `EDUCARE_API_URL` na Vercel.
4. Rollback do HTTPS: voltar o `SITE_ADDRESS=:80` no `.env` e rodar `up -d` (o BFF fica sem conexão segura até corrigir). Rollback do contrato: reverter o commit.

## Open Questions

- Nome final do projeto na Vercel (para o `EDUCARE_CORS_ALLOWED_ORIGINS`). Pode ser preenchido no passo manual sem mudar nada desta change.
