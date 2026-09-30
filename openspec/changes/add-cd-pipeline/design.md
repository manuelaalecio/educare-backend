# Design

## Context

- O `.github/workflows/ci.yml` (change `add-ci-workflow`) roda `./gradlew build` só em `pull_request` para a `main`, num job de id `build` sem `name:`. O check `build` é obrigatório na proteção da `main`, então esse id não pode mudar. O workflow usa `permissions: contents: read`, `concurrency` pelo número do PR e `cache-read-only: false` no `setup-gradle`.
- Os testes de persistência e de cenário usam Testcontainers (`TestcontainersConfiguration` com `@ServiceConnection`, e containers próprios em `AdminSeedScenarioTest` e `AuthorshipScenarioTest`). O build não usa `.env`, variáveis `SPRING_*` nem secrets. **Decisão do usuário**: o CI continua com Testcontainers, sem Postgres como service container (proposal, Non-goals).
- O `Dockerfile` é multi-stage: `eclipse-temurin:21-jdk` roda `./gradlew bootJar` (sem testes) e a imagem final `eclipse-temurin:21-jre` executa o jar como root. O comentário do topo fala em VM ARM64, mas a VM de produção é `x86_64`. O `.dockerignore` só exclui `.gradle/`, `build/`, `.idea/`, `.vscode/`, `.env` e `*.log`; o `COPY . .` leva o resto (inclusive `.git`, `openspec/` e `tentar-vm.sh`) para o estágio de build.
- O `docker-compose.yaml` da raiz é o ambiente local (banco + API com `build: .`) e não muda.
- O profile `prod` exige `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `EDUCARE_ADMIN_PASSWORD`, `EDUCARE_JWT_SECRET` e `EDUCARE_CORS_ALLOWED_ORIGINS`, sem padrão. O `~/app/.env` da VM, hoje com `DB_HOST`, `DB_PASSWORD` e `SITE_ADDRESS=:80`, **ganha à mão** as três variáveis `EDUCARE_*` (decisão do usuário); o pipeline nunca lê, cria nem altera esse arquivo. O banco é `app` e o usuário é `app`, fixos, como no compose local (decisão do usuário).
- Não há configuração `management.*`: pelo padrão do Spring Boot só o `health` é exposto na web e sem detalhes, mas nada garante isso. A filter chain libera `/actuator/health` e `/actuator/health/**` sem token (`SecurityConfiguration`).
- VM: `linux/amd64`, 1 GB de RAM + 2 GB de swap, usuário `ubuntu`, diretório `~/app`. Repositório público `manuelaalecio/educare-backend`; o shell da usuária é o fish.

## Goals / Non-Goals

**Goals:**
- A imagem que vai para produção é construída uma única vez, a partir de um commit da `main` que passou no `./gradlew build` e cujo PR tinha o label `build`, e identificada pelo SHA curto desse commit.
- Publicar uma imagem é uma decisão explícita por PR (label `build`); merges só de documentação ou de specs não geram imagem nem mexem no `:latest`.
- Deploy e rollback são a mesma operação: escolher uma tag de imagem já publicada.
- Nenhum secret é acessível a código de PR, e nenhum valor secreto aparece em log.
- Um deploy que não fica saudável falha visivelmente, com os logs da `api` na execução.

**Non-Goals:**
- Rollback automático quando o health falha: o job falha e a usuária decide o rollback pelo dispatch (evita esconder o erro e trocar de versão sem decisão).
- Zero downtime: o `up -d` recria o container da `api`, com alguns segundos a um minuto de indisponibilidade. Aceitável para um sistema interno.
- Testes automatizados do pipeline: o comportamento é validado nas tasks manuais do grupo final (ver D1).

## Decisions

### D1. Pipeline sem specs; só o Actuator ganha requisito

Como no D1 da `add-ci-workflow`, workflows são ferramenta de entrega: um Scenario como "WHEN uma tag `v*` é criada THEN a VM é atualizada" não tem teste de cenário `@SpringBootTest` possível. O pipeline é verificado nas tasks manuais (grupo 6). Já a restrição do Actuator é comportamento da aplicação, testável com `@SpringBootTest`, e passa a ser um requisito da capability `security` (ver `specs/security/spec.md`), porque o health é a única rota aberta na internet e a imagem é pública.

### D2. Dois workflows: `ci.yml` estendido e `deploy.yml` novo

`ci.yml` ganha o gatilho `push` na `main` e mais dois jobs:

```yaml
on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: ci-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

jobs:
  build:   # id inalterado: é o check obrigatório
    ...    # igual ao atual
  publish-gate:
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    permissions:
      contents: read
      pull-requests: read
    outputs:
      publish: ${{ steps.gate.outputs.publish }}
  image:
    needs: [build, publish-gate]
    if: needs.publish-gate.outputs.publish == 'true'
    permissions:
      contents: read
      packages: write
```

- Em PR roda só o `build` (`publish-gate` e `image` ficam como "skipped"). Em push na `main`, `build` e `publish-gate` rodam em paralelo, e o `image` só roda se o build passou e o gate liberou.
- `cancel-in-progress` só em PR: em push na `main`, execuções não são canceladas, para que todo merge com o label que passou no build tenha a sua imagem (o grupo enfileira em vez de cancelar).
- `packages: write` só no job `image` e `pull-requests: read` só no `publish-gate`, sobrescrevendo o `permissions` do workflow; o `build` continua só com `contents: read`.

**Gate pelo label `build` (job `publish-gate`).** O evento `push` não traz o PR, então o job consulta a API: `gh api repos/<repo>/commits/<sha>/pulls` (com `GH_TOKEN` vindo de `github.token` por `env:`) e procura o PR com `merged_at` preenchido, base `main` e `merge_commit_sha` igual a `GITHUB_SHA`, o que cobre merge commit, squash e rebase. `publish=true` só se esse PR existir e tiver o label `build`. Em qualquer outro caso (sem label, push direto sem PR), `publish=false` e o job **não falha**: grava no `$GITHUB_STEP_SUMMARY` o motivo, por exemplo "PR #12 sem o label `build`: imagem não publicada". O job não faz checkout e não executa código do repositório.

- O label é lido na hora da execução, e não no momento do merge. Se o label faltou, basta adicioná-lo ao PR já mergeado e usar **Re-run all jobs** na execução do push: o gate lê o label novo e publica a imagem daquele commit, sem precisar de outro commit.
- Rótulo lido só depois do merge: o label não faz nada no PR aberto (os testes rodam sempre e o check `build` continua obrigatório), então não há build de imagem a partir de código de PR, nem de fork.
- Alternativa: decidir pelo label no próprio PR (`pull_request` com `types: [labeled]`) e publicar dali. Descartada: publicaria código ainda não mergeado e daria `packages: write` a execuções de PR.
- Alternativa: marcar o commit com `[build]` na mensagem. Descartada: menos visível que um label e fácil de esquecer no squash.
- O `setup-gradle` mantém `cache-read-only: false`: agora há execuções na `main`, cujo cache fica disponível para todos os PRs (escopo da branch padrão), e os PRs continuam gravando o próprio cache.
- Nenhum evento `pull_request_target` em nenhum workflow.

`deploy.yml` é um arquivo separado porque tem outro gatilho, outro environment e outra concorrência, e porque um deploy nunca deve depender de mudanças no fluxo de PR.

- Alternativa: um único workflow com os três jobs e condições por evento. Descartada: mistura o gatilho de tag com o de PR, deixa o check obrigatório no meio de um arquivo com secrets de produção e torna as condições mais difíceis de revisar.

### D3. Build e publicação da imagem (job `image`)

Passos: `actions/checkout`, `docker/setup-buildx-action`, `docker/login-action` no `ghcr.io` (usuário `${{ github.actor }}`, senha `${{ secrets.GITHUB_TOKEN }}` no `with:` da action, nunca em `run:`), um passo que calcula o nome e as tags, e `docker/build-push-action` com:

- `platforms: linux/amd64`, `push: true`.
- Tags `ghcr.io/manuelaalecio/educare-backend:<sha7>` e `:latest`, onde `<sha7>` são os 7 primeiros caracteres de `GITHUB_SHA` (sempre 7, não o `--short` do git, que varia; o deploy calcula igual). O nome vem de `github.repository_owner` convertido para minúsculas no passo de cálculo, que recebe os valores por `env:`.
- Labels OCI `org.opencontainers.image.source` (URL do repositório, que vincula o pacote ao repo), `org.opencontainers.image.revision` (SHA completo) e `org.opencontainers.image.licenses`.
- Cache `type=gha` (`cache-from` e `cache-to` com `mode=max`), que reaproveita as camadas do Gradle entre builds.
- Sem `build-args` e sem `secrets` de build.

A imagem é gerada pelo mesmo `Dockerfile` multi-stage, que recompila com `bootJar` (sem testes). Os testes já passaram no job `build` do mesmo commit, e o `needs: build` impede publicar se ele falhar.

- Alternativa: aproveitar o jar do job `build` (artifact) num `Dockerfile` só de runtime. Descartada: exigiria dois Dockerfiles (o compose local usa `build: .`) e o ganho de tempo é pequeno com o cache `gha`.
- Alternativa: `docker/metadata-action` para gerar tags e labels. Descartada: são só duas tags e três labels; um passo de shell é mais simples de ler e controla o tamanho fixo do SHA.

### D4. `Dockerfile` e `.dockerignore` para uma imagem pública

- `.dockerignore` passa a excluir: `.env` e `.env.*` em qualquer nível (`**/.env`, `**/.env.*`), `.git/`, `.github/`, `.claude/`, `.idea/`, `.vscode/`, `.gradle/`, `build/`, `openspec/`, `deploy/`, `tentar-vm.sh`, `docker-compose.yaml`, `*.log` e `*.md` (nenhum Markdown é necessário ao build). O que sobra é o necessário para o `bootJar`: `gradlew`, `gradle/`, `build.gradle`, `settings.gradle`, `lombok.config`, `src/`.
- `Dockerfile`: sem `ARG` ou `ENV` com valores sensíveis; a imagem final cria um usuário de sistema sem privilégios e roda com `USER` dele; mantém `EXPOSE 8080` e o `ENTRYPOINT` em forma exec (a `JAVA_TOOL_OPTIONS` do compose é lida pela JVM sem mudar o entrypoint). O comentário do topo passa a dizer que a produção é `linux/amd64` e que a imagem é construída pelo CI.
- Só a imagem final vai para o GHCR; o estágio de build fica só no cache do Actions, que não é público. Mesmo assim o `.dockerignore` protege os dois.

### D5. Arquivos de produção em `deploy/`

`deploy/compose.yaml`:

```yaml
name: educare

services:
  api:
    image: ghcr.io/manuelaalecio/educare-backend:${IMAGE_TAG:-latest}
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://${DB_HOST:?DB_HOST ausente no .env}:5432/app
      SPRING_DATASOURCE_USERNAME: app
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD ausente no .env}
      EDUCARE_ADMIN_PASSWORD: ${EDUCARE_ADMIN_PASSWORD:?...}
      EDUCARE_JWT_SECRET: ${EDUCARE_JWT_SECRET:?...}
      EDUCARE_CORS_ALLOWED_ORIGINS: ${EDUCARE_CORS_ALLOWED_ORIGINS:?...}
      JAVA_TOOL_OPTIONS: -Xmx300m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m
    ports:
      - "127.0.0.1:8080:8080"

  caddy:
    image: caddy:2
    restart: unless-stopped
    depends_on: [api]
    environment:
      SITE_ADDRESS: ${SITE_ADDRESS:?SITE_ADDRESS ausente no .env}
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config

volumes:
  caddy_data:
  caddy_config:
```

`deploy/Caddyfile`:

```
{$SITE_ADDRESS} {
	reverse_proxy api:8080
}
```

- `${VAR:?mensagem}` faz o Compose parar antes de criar containers quando uma variável falta no `.env`, com uma mensagem que só cita o nome. As três `EDUCARE_*` são obrigatórias sempre, como no profile `prod` (o `EDUCARE_ADMIN_PASSWORD` só é usado na V3, mas continua exigido para não haver dois comportamentos).
- `name: educare` fixa o nome do projeto do Compose, em vez de derivar do diretório `app`.
- A porta 8080 só em `127.0.0.1`: o acesso externo é só pelo Caddy; o health check do deploy usa essa porta de dentro da VM. O Caddy alcança a `api` pela rede do Compose.
- A `api` sem `healthcheck` no Compose: a imagem JRE não tem `curl`, e o health já é conferido pelo deploy.
- Limites da JVM: heap 300 MB + metaspace 128 MB + pilhas de 512 KB + code cache ficam em torno de 500 a 550 MB, o que com o Caddy (~30 MB) e o Docker cabe em 1 GB de RAM com 2 GB de swap de folga. `SerialGC` é o coletor de menor overhead para 1 vCPU e heap pequeno.
- Sem serviço de banco; `DB_HOST` é o IP privado da VM do banco.
- Custo no Oracle Free Tier: um container a mais (Caddy, poucas dezenas de MB). Ele substitui a exposição direta da porta 8080 e prepara o HTTPS automático quando `SITE_ADDRESS` virar um domínio.

### D6. Workflow de deploy (`deploy.yml`)

```yaml
on:
  push:
    tags: ['v*']
  workflow_dispatch:
    inputs:
      image_tag:
        description: Tag da imagem a implantar (ex. SHA curto para rollback). Vazio = SHA curto do commit escolhido.
        required: false
        type: string

permissions:
  contents: read

concurrency:
  group: deploy-production
  cancel-in-progress: false
```

Um job `deploy`, `runs-on: ubuntu-latest`, `environment: production`, `timeout-minutes: 15`. Passos:

1. **Checkout** com `fetch-depth: 0`, para ter o histórico da `main`.
2. **Resolver a tag da imagem** (shell com `set -euo pipefail`; `inputs.image_tag`, `github.ref`, `github.ref_type` e `github.event_name` entram por `env:`, nunca interpolados no `run:`):
   - Tag `v*`: `git merge-base --is-ancestor "$GITHUB_SHA" origin/main`; se não for ancestral, falha com "o commit da tag não está na main". A imagem é `<sha7>` do commit da tag.
   - Dispatch: exige `github.ref` igual a `refs/heads/main` ou uma tag `v*` (a regra do environment já restringe; a verificação no workflow é defesa extra e dá mensagem clara). A imagem é o `image_tag` informado ou, vazio, o `<sha7>` do commit.
   - O `image_tag` informado precisa casar com `^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$` (gramática de tag do Docker); fora disso, falha sem ecoar o valor em comando.
   - Grava a tag em `$GITHUB_OUTPUT` e no resumo da execução (`$GITHUB_STEP_SUMMARY`).
3. **Conferir que a imagem existe** no GHCR, anonimamente (como a VM vai puxar): `docker manifest inspect ghcr.io/manuelaalecio/educare-backend:<tag>`. Se falhar: "imagem não encontrada; confira se o PR desse commit tinha o label `build`, se o CI da `main` terminou e se o pacote é público". Isso cobre tag criada antes do fim do job `image`, num commit cujo PR não tinha o label, ou num commit da `main` que não foi head de push (sem imagem).
4. **Configurar o SSH**: `SSH_PRIVATE_KEY` e `SSH_KNOWN_HOSTS` entram por `env:` e são gravados com `umask 077` em `~/.ssh/deploy_key` e `~/.ssh/known_hosts`; um `~/.ssh/config` define o host `educare-prod` com `HostName` e `User` vindos de `SSH_HOST`/`SSH_USER` (também por `env:`), `IdentityFile`, `IdentitiesOnly yes`, `BatchMode yes` e `StrictHostKeyChecking yes`.
5. **Copiar os arquivos**: `scp deploy/compose.yaml deploy/Caddyfile deploy/deploy.sh educare-prod:app/` (relativo ao home remoto, ou seja, `~/app`).
6. **Executar o deploy remoto**: `ssh educare-prod 'bash app/deploy.sh' "$IMAGE_TAG"` com a tag já validada (D7).
7. **Limpeza** (`if: always()`): apaga `~/.ssh/deploy_key`.

Regras de segurança válidas para os dois workflows:
- Secrets e inputs só por `env:` do passo; nenhum `${{ secrets.* }}` ou `${{ inputs.* }}` dentro de `run:` (evita injeção de script e exposição no texto do comando).
- Nenhum `set -x`, nenhum `StrictHostKeyChecking=no`, nenhum `docker compose config` (imprimiria as variáveis resolvidas do `.env`).
- O GitHub mascara os secrets nos logs; mesmo assim nenhum passo imprime conteúdo de chave, do `.env` ou do ambiente.
- `concurrency` fixo `deploy-production` sem cancelamento: dois deploys nunca rodam juntos e um deploy em andamento não é interrompido.
- O environment `production` (manual) restringe a execução à `main` e a tags `v*`; os secrets SSH só existem nele. PRs, inclusive de forks, nunca rodam um job com esse environment.

### D7. Script remoto `deploy/deploy.sh`

Versionado no repositório e copiado junto com o compose, para ser revisável e checado pelo `shellcheck` (em vez de um comando longo entre aspas no workflow). Recebe a tag como `$1`:

1. `set -euo pipefail`; `cd ~/app`.
2. Valida `$1` com a mesma expressão regular do D6.
3. Confere `test -f .env` e falha com "~/app/.env não existe" sem ler o arquivo.
4. Grava `deploy.env` (via arquivo temporário + `mv`) com `IMAGE_TAG=<tag>`. O `.env` não é tocado.
5. `docker compose --env-file .env --env-file deploy.env pull api caddy` e `... up -d --remove-orphans`. O segundo `--env-file` define só `IMAGE_TAG`; assim a tag implantada fica registrada na VM, e um `up` manual com os mesmos dois arquivos repete o estado atual.
6. Health check: até 36 tentativas com 5 s de intervalo (~3 min) de `curl -fsS --max-time 5 http://localhost:8080/actuator/health`, aceitando quando o corpo tem `"status":"UP"`. Se não ficar `UP`, imprime `docker compose ... ps` e `docker compose ... logs --tail 200 api` e sai com código 1.
7. Em sucesso, `docker image prune -f` (só imagens sem tag, liberando disco da VM) e imprime a tag implantada.

Os logs da `api` não trazem segredos: a aplicação não registra senhas nem a chave do JWT, e a JVM só imprime o `JAVA_TOOL_OPTIONS`, que não é secreto. O `curl` do health só roda na VM, sem expor a porta 8080.

### D8. Actuator restrito (spec `security`)

No `application.yaml` (vale para todos os profiles):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
      show-components: never
```

Testes de cenário (`@SpringBootTest` + Testcontainers) em `SecurityScenarioTest`, um por Scenario do requisito novo, com `@DisplayName` referenciando o cenário: corpo do health só com `status` (com e sem token), `/actuator/env` com token de `ADMIN` → 404 sem conteúdo do ambiente, `/actuator/info` sem token → 401, `/actuator/health/db` com token de `ADMIN` → 404. Não há endpoints novos da API nem alteração de schema (sem migration Flyway).

### D9. Documentação

- **README**, nova seção "Deploy em produção": visão do fluxo (PR → teste; merge na `main` → teste e, com o label `build` no PR, imagem; tag/dispatch → deploy), como publicar a imagem de um PR já mergeado (adicionar o label e reexecutar), arquivos de `deploy/`, e os passos manuais, com comandos que funcionam no fish (sem heredoc, sem `export`, sem `$(...)` dentro de aspas; variáveis com `set`):
  1. Gerar a chave de deploy (`ssh-keygen -t ed25519 -N '' -f ~/.ssh/educare_deploy`) e autorizá-la no `~/.ssh/authorized_keys` do `ubuntu` na VM.
  2. Obter a linha de `known_hosts` (`ssh-keyscan -t ed25519 <ip>`) e conferir a impressão digital com a da VM antes de usar.
  3. Criar o environment `production` com regra de branches e tags `main` e `v*`, e os secrets `SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS` (pela interface ou `gh secret set NOME --env production < arquivo`).
  4. Acrescentar ao `~/app/.env` da VM `EDUCARE_ADMIN_PASSWORD`, `EDUCARE_JWT_SECRET` (mín. 32 bytes) e `EDUCARE_CORS_ALLOWED_ORIGINS`, mantendo a permissão 600.
  5. Liberar as portas 80 e 443 na security list da VCN (e no firewall da VM, se necessário); parar containers antigos do deploy manual que ocupem as portas 8080/80.
  6. Criar o label `build` no repositório (`gh label create build --description "Publica a imagem no GHCR após o merge na main"`) e, depois do primeiro merge com o label, tornar público o pacote `educare-backend` no GHCR.
  7. Criar a primeira tag (`git tag -a v0.1.0 -m "v0.1.0"` num commit da `main` que tem imagem publicada, `git push origin v0.1.0`), e verificar `curl http://<ip-publico>/actuator/health`.
  8. Rollback: `gh workflow run deploy.yml --ref main -f image_tag=<sha7>`; lista de tags disponíveis na página do pacote.
- O deploy manual por `docker save | ssh` deixa de ser documentado como caminho de produção.
- **CLAUDE.md**: seção Testes (o CI também roda em push na `main`, que publica a imagem só quando o PR tem o label `build`) e Armadilhas (um item sobre `deploy/`: compose de produção separado do local, `.env` da VM manual, `deploy.env` gerado pelo pipeline, regras de segurança dos workflows e o id `build` que não pode mudar).
- **`openspec/config.yaml`**: `context` → Topologia (imagem no GHCR, deploy por tag/dispatch com Compose e Caddy na VM, banco em outra VM) e Testes (CI em PR e push na `main`).

## Risks / Trade-offs

- [Tag criada antes de o job `image` terminar, num commit cujo PR não tinha o label `build`, ou num commit da `main` que não foi head de um push] → o passo 3 do D6 falha com mensagem clara; basta esperar o CI, ou adicionar o label ao PR e reexecutar o CI da `main` (D2), e então reexecutar o deploy.
- [Esquecer o label e o `:latest` ficar para trás em relação à `main`] → o resumo do `publish-gate` mostra por que não publicou; o deploy usa sempre o `<sha7>` explícito, nunca o `:latest`, então nada vai para produção por engano.
- [A API de "PRs de um commit" não achar o PR logo após o merge] → comportamento conservador: sem PR encontrado, não publica e registra o motivo; reexecutar resolve.
- [Pacote do GHCR privado por padrão] → o `docker manifest inspect` anônimo falha antes de qualquer SSH; o passo manual 6 do README torna o pacote público.
- [Tags `@vN` das actions e `caddy:2` são móveis] → actions oficiais; o job `image` é o único com escrita (só `packages`), e o deploy só lê. Fixar por SHA/digest fica para a change do Dependabot.
- [`show-components: never` pode não fazer `/actuator/health/db` responder 404 em alguma versão do Spring Boot] → o teste de cenário do requisito detecta; se preciso, ajusta-se a configuração (sem mudar o requisito).
- [Indisponibilidade curta a cada deploy (recriação do container) e memória apertada durante o `pull`] → sistema interno com uso previsível; o swap cobre o pico; o health de ~3 min acomoda uma subida lenta.
- [Health falha e a versão nova fica no ar parcialmente] → o job fica vermelho com os logs; o rollback é um dispatch com a tag anterior (registrada no resumo das execuções anteriores).
- [`--remove-orphans` num projeto de nome novo não remove containers do deploy manual antigo] → passo manual 5 do README.
- [O cache `gha` do Buildx guarda camadas do estágio de build] → o cache não é público e o `.dockerignore` exclui `.env*` e arquivos irrelevantes do contexto.

## Migration Plan

1. Criar o label `build` no repositório. Implementar em `dev` e abrir o PR para a `main` com o label: só o job `build` roda (`publish-gate` e `image` aparecem como skipped). Merge.
2. O push na `main` do merge roda `build` + `publish-gate` + `image` e publica `:<sha7>` e `:latest`. Nenhum deploy.
3. Passos manuais do README (chave, environment, secrets, `.env` da VM, portas, pacote público, parar o deploy manual antigo).
4. Criar `v0.1.0` no head da `main`: o `deploy.yml` roda e termina com health `UP`; conferir `/actuator/health` pelo IP público.
5. Rollback do próprio pipeline: remover `deploy.yml`, o job `image` e o gatilho `push` do `ci.yml`; na VM, o stack continua rodando com a última imagem, e o deploy manual antigo segue possível.
