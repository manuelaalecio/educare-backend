# Proposal

## Why

Hoje a produção na VM da Oracle é atualizada à mão: a imagem é gerada localmente e enviada com `docker save | ssh`, sem garantia de que o código implantado passou pelo build e sem um jeito rápido de voltar a uma versão anterior. Com o CI de PR já exigido para o merge na `main`, falta o caminho automatizado e auditável do GitHub até a VM: imagem construída uma única vez no GitHub Actions, publicada no GHCR e implantada por tag ou por disparo manual, com rollback pela escolha da tag da imagem.

## What Changes

- **CI estendido** (`.github/workflows/ci.yml`): continua rodando `./gradlew build` em todo PR para a `main` (o check `build` segue obrigatório e com o mesmo nome) e passa a rodar também em push na `main`. No push na `main`, a imagem `linux/amd64` só é construída e publicada no GHCR (tags `:<sha curto>` e `:latest`) se o PR mergeado tiver o label **`build`**; sem o label, o push na `main` roda só os testes. Nenhum deploy acontece nesse fluxo.
- **Novo workflow de deploy** (`.github/workflows/deploy.yml`), disparado por push de tag `v*` ou por `workflow_dispatch` (input opcional `image_tag` para rollback). Não recompila: implanta a imagem já publicada para o commit. No disparo por tag, confere antes que o commit da tag está na `main`. Roda no environment `production`, que guarda os secrets de SSH.
- **Arquivos de produção em `deploy/`**: `compose.yaml` com os serviços `api` (imagem do GHCR, porta só em `127.0.0.1:8080`, limites de memória da JVM para a VM de 1 GB) e `caddy` (portas 80 e 443, proxy reverso para `api:8080`, endereço vindo de `SITE_ADDRESS`), e o `Caddyfile`. Sem banco: o PostgreSQL fica em outra VM, acessado pelo IP privado de `DB_HOST`.
- **Deploy na VM**: copia `deploy/compose.yaml` e `deploy/Caddyfile` para `~/app`, faz `pull` e `up -d` da tag escolhida e verifica `http://localhost:8080/actuator/health` de dentro da VM por até ~3 minutos; se não ficar `UP`, mostra os logs da `api` e falha. O `~/app/.env` da VM nunca é criado nem alterado pelo pipeline.
- **Imagem pública e segura**: `.dockerignore` ampliado (exclui todo `.env*`, `.git`, `.github`, `openspec`, `deploy`, `tentar-vm.sh` etc.), `Dockerfile` sem argumentos ou segredos de build, rodando como usuário não root e com o comentário de arquitetura corrigido (a VM é `amd64`).
- **Actuator restrito ao health, sem detalhes**: configuração explícita no `application.yaml` (hoje isso vale só pelo padrão do Spring Boot) e requisito novo na spec `security`.
- **Documentação**: README com a seção de deploy e os passos manuais (chave de deploy, environment `production` e secrets, `.env` da VM, tornar o pacote do GHCR público, primeira tag e rollback), com comandos compatíveis com o fish; CLAUDE.md e `openspec/config.yaml` atualizados onde descrevem CI e deploy.

## Non-goals

- Executar qualquer comando na VM, criar a chave SSH, os secrets ou o environment no GitHub: tudo isso é manual e fica documentado no README.
- Alterar ou gerar o `~/app/.env` da VM, e passar variáveis da aplicação (senhas, chave do JWT, CORS) por secrets do GitHub.
- Banco de dados no compose de produção, backup do banco ou migração dos dados.
- Domínio próprio e HTTPS automático no Caddy: `SITE_ADDRESS=:80` por enquanto; trocar para um domínio depois é só mudar o `.env`.
- Trocar o Testcontainers por um Postgres como service container no CI: os testes continuam com Testcontainers, já sem secrets.
- Imagem multi-arquitetura (`arm64`), Dependabot, fixação das actions por SHA, varredura de vulnerabilidades da imagem e ambientes de staging.
- Deploy automático em push na `main` ou na `dev`, e build ou publicação de imagem a partir de PRs (o label só é lido depois do merge).
- Mudar o `docker-compose.yaml` da raiz, que continua sendo o ambiente local (banco + API).

## Capabilities

### New Capabilities
<!-- Nenhuma. O pipeline em si é ferramenta de entrega, sem comportamento da API a especificar (mesmo critério do D1 da change add-ci-workflow); ver design D1. -->

### Modified Capabilities
- `security`: requisito novo garantindo que o Actuator expõe pela web só o endpoint de health, respondendo só o status agregado, sem componentes nem detalhes. A imagem passa a ser pública e o health é a única rota aberta na internet, então esse limite deixa de depender só do padrão do framework.

Nenhum módulo de negócio é afetado. A mudança de código se restringe à configuração transversal (`application.yaml`) e aos testes de `shared/security`.

## Impact

- **Repositório**: `.github/workflows/ci.yml` (alterado), `.github/workflows/deploy.yml` (novo), `deploy/compose.yaml` e `deploy/Caddyfile` (novos), `Dockerfile`, `.dockerignore`, `src/main/resources/application.yaml`, testes em `shared/security`, README, CLAUDE.md e `openspec/config.yaml`.
- **API**: nenhuma rota nova. `/actuator/health` continua público e passa a ter formato garantido (`{"status":"UP"}`); os demais endpoints do Actuator não são expostos (já não eram, por padrão).
- **GitHub**: label `build` no repositório (criação manual), pacote `ghcr.io/manuelaalecio/educare-backend` (público), environment `production` restrito à `main` e a tags `v*`, com os secrets `SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY` e `SSH_KNOWN_HOSTS` (configuração manual).
- **Infraestrutura (Oracle Free Tier)**: a VM do backend passa a rodar dois containers (`api` e `caddy:2`) e deixa de compilar ou receber imagens por SSH; a JVM limitada a 300 MB de heap cabe em 1 GB de RAM + 2 GB de swap. Nenhum componente novo além do Caddy, que substitui o acesso direto à porta 8080.
- **Dependências**: nenhuma nova no build Gradle. Actions novas: `docker/setup-buildx-action`, `docker/login-action` e `docker/build-push-action`, todas oficiais da Docker.
