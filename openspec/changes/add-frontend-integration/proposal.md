# Proposal

## Why

O frontend (TanStack Start, na Vercel) vai começar a consumir a API, a partir do login e da sessão do usuário. O desenho escolhido é um BFF: o servidor do frontend chama a API com o token, e o navegador nunca fala com o backend nem vê o JWT. Para isso funcionar sem quebrar a cada mudança, faltam duas coisas do lado do backend:

- **um contrato da API que o frontend possa consumir.** Hoje o documento OpenAPI só existe em tempo de execução no profile `dev`, não descreve as respostas de erro e não marca quais campos das respostas sempre vêm preenchidos. Gerar tipos a partir dele daria tipos frouxos, e nada avisa quando uma mudança no backend quebra o frontend;
- **um transporte seguro até a VM.** O Caddy de produção atende só HTTP pelo IP (`SITE_ADDRESS=:80`), e com o BFF o token e as senhas do login trafegam pela internet entre a Vercel e a VM.

## What Changes

- **Contrato OpenAPI versionado no repositório** (`openapi/educare-api.json`), gerado a partir da própria aplicação. O `./gradlew build` falha quando o arquivo commitado não corresponde à API, e um comando Gradle o regenera. O repositório é público, então o frontend lê o contrato da `main` sem credenciais.
- **Contrato completo para gerar tipos**:
  - toda operação declara a resposta de sucesso com o schema dela e as respostas de erro que pode devolver (`400`, `401`, `403`, `404`, `409`, conforme as specs), como `application/problem+json`;
  - um schema único de `ProblemDetail`, com a propriedade opcional `errors` (`field`, `message`);
  - os campos que a API sempre devolve nas respostas são marcados como obrigatórios;
  - o documento é determinístico: chaves ordenadas e um servidor relativo (`/`), sem porta nem host de ambiente.
- **HTTPS em produção**: o Caddy passa a atender por um nome de domínio, com certificado automático. Enquanto não houver domínio próprio, usa-se um nome do tipo `<ip>.sslip.io`. A mudança fica no `.env` da VM, na liberação da porta 443 e no README; o `deploy/compose.yaml` e o `Caddyfile` já aceitam um domínio em `SITE_ADDRESS`.
- **Documentação**: README e CLAUDE.md explicam o contrato (onde fica, como regenerar e quando o build falha), e o `openspec/config.yaml` passa a registrar o BFF na topologia.

## Non-goals

- Mudar o comportamento de qualquer endpoint, a autenticação (JWT de 8 h sem refresh token) ou o formato dos erros. A change só descreve melhor o que já existe.
- Endpoint de logout ou revogação de token: a API continua stateless, e o logout é o BFF descartar o cookie.
- Remover ou afrouxar o CORS. O BFF não precisa dele, mas a regra continua valendo; em produção, as origens passam a ser o domínio do frontend na Vercel.
- Publicar o Swagger UI ou o `/v3/api-docs` fora do `dev`: o contrato público é o arquivo do repositório.
- Restringir a API a chamadas vindas do BFF (mTLS, segredo compartilhado, lista de IPs da Vercel) e rate limiting do login.
- Testes de contrato orientados ao consumidor (Pact ou similar) e geração de SDK no backend.
- Domínio próprio. O `sslip.io` cobre o HTTPS até lá, e trocar depois é só mudar o `.env`.

## Capabilities

### New Capabilities
- `api-contract`: contrato OpenAPI da API, versionado no repositório e verificado no build, com respostas de sucesso e de erro, schema de `ProblemDetail` e campos obrigatórios declarados, para que o frontend gere tipos a partir dele. É transversal (vive em `shared/config` e nos controllers de todos os módulos) e não é um módulo de negócio: o contrato descreve a API inteira. Por isso atravessa os módulos `auth` e `user`, mas só com anotações de documentação, sem mudar o comportamento deles.

### Modified Capabilities
<!-- Nenhuma. O requirement "Documentação da API só em desenvolvimento" (security) continua valendo como está: o arquivo do repositório não é servido pela aplicação. O HTTPS é configuração de deploy, sem comportamento da API a especificar (mesmo critério da add-cd-pipeline). -->

## Impact

- **Dependências de planejamento**: o deploy da `add-cd-pipeline` precisa estar funcionando (tasks do grupo 6) antes da parte de HTTPS. Se a `add-child-crud` for aplicada depois desta change, ela regenera o contrato com os endpoints de `/api/v1/children`.
- **Código**:
  - `shared/config/OpenApiConfiguration`: servidor relativo, schema `ProblemDetail` e customizações globais (`401` em toda operação protegida, campos obrigatórios das respostas);
  - controllers de `auth` e `user`: anotações de documentação das respostas de erro de cada operação;
  - `application.yaml`: ordenação determinística do documento.
- **Testes**: um teste que gera o documento e o compara com o arquivo commitado (e o reescreve no modo de atualização), mais testes de cenário do conteúdo do contrato.
- **Build**: nova task Gradle `updateApiContract`; o `./gradlew build` (e o CI) passa a falhar com o contrato desatualizado.
- **API**: nenhuma rota ou resposta muda. No `dev`, o `/v3/api-docs` passa a ter o mesmo conteúdo do arquivo.
- **Infraestrutura**: porta 443 liberada na security list da VM, `SITE_ADDRESS` com domínio e `EDUCARE_CORS_ALLOWED_ORIGINS` com o domínio da Vercel no `.env` da VM (manual, documentado). Nenhum componente novo.
- **Dependências de bibliotecas**: nenhuma nova (o springdoc já está no projeto).
- **Frontend**: change irmã `add-backend-integration` no `educare-front`, que consome o contrato e implementa o BFF.
