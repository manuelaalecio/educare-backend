# Educare Backend

API REST do **Educare**, sistema de gestão de uma ONG/instituição que atende crianças em situação de vulnerabilidade.

## O que é

O Educare é usado pelos **funcionários da instituição** para gerenciar o cadastro das crianças atendidas e de seus responsáveis. Este repositório é o **backend**: guarda os dados no banco e expõe as rotas consumidas pelo frontend, que fica em um repositório separado.

## Objetivo

Facilitar o dia a dia dos funcionários na gestão das crianças atendidas, com foco em:

- **Cadastro de crianças** e de seus **responsáveis**, com o vínculo entre eles.
- **Cadastro de usuários** (funcionários) com acesso autenticado.
- **Rematrícula**: durante a janela do período de rematrícula, o recadastro de cada criança parte dos dados já existentes, em vez de refazer tudo do zero.

Como o sistema trata dados pessoais de crianças, segue os cuidados da LGPD: só coleta o necessário, restringe o acesso a usuários autenticados e registra quem criou ou alterou cada informação.

> Projeto em fase inicial de desenvolvimento.

## Stack

- Java 21 e Spring Boot 4
- PostgreSQL 16, Spring Data JPA e Flyway (migrations)
- Gradle
- Testes com JUnit 5, Mockito, Testcontainers, JaCoCo (cobertura) e PIT (mutation testing)
- Docker e Docker Compose

## Como iniciar o projeto

### 1. Pré-requisitos

- **Java 21** (JDK). O Gradle não precisa ser instalado: o projeto usa o Gradle Wrapper (`./gradlew`).
- **Docker** com **Docker Compose**. É usado para subir o banco e também pelos testes.

Para conferir:

```bash
java -version
docker --version
docker compose version
```

### 2. Clonar o repositório

```bash
git clone git@github.com:manuelaalecio/educare-backend.git
cd educare-backend
```

### 3. Configurar a senha do banco

Crie um arquivo `.env` na raiz do projeto com a senha que o PostgreSQL vai usar:

```bash
echo "DB_PASSWORD=escolha_uma_senha" > .env
```

O `.env` não vai para o repositório (está no `.gitignore`).

### 4. Subir a aplicação

Escolha uma das duas formas.

**Opção A: tudo com Docker (mais simples)**

Sobe o banco e a API juntos:

```bash
docker compose up --build
```

**Opção B: banco no Docker e API pelo Gradle (melhor para desenvolver)**

Suba só o banco:

```bash
docker compose up -d db
```

Depois rode a API informando a senha do banco (a mesma do `.env`):

```bash
SPRING_DATASOURCE_PASSWORD=escolha_uma_senha ./gradlew bootRun
```

Sem profile ativo, a API sobe com o profile `dev`, que já aponta para o banco do Compose (`localhost:5432`, banco e usuário `app`). Na Opção A, o Compose ativa o profile `prod`, em que URL, usuário e senha do banco vêm só de variáveis de ambiente.

Na inicialização, o Flyway aplica as migrations pendentes de `src/main/resources/db/migration/`.

### 5. Verificar se está no ar

A API fica disponível em `http://localhost:8080`. Para confirmar:

```bash
curl http://localhost:8080/actuator/health
```

A resposta deve conter `"status":"UP"`.

> **Erro `password authentication failed`?** O PostgreSQL só define a senha na primeira vez que o volume do banco é criado. Se você mudou o `DB_PASSWORD` depois disso, use a senha antiga ou recrie o banco com `docker compose down -v` (isso **apaga os dados**) e suba de novo.

### 6. Parar

```bash
docker compose down        # para os containers e mantém os dados do banco
docker compose down -v     # para os containers e apaga os dados do banco
```

## Testes

Os testes sobem um PostgreSQL próprio com Testcontainers, então **o Docker precisa estar rodando**. Não é preciso subir o banco do Compose.

```bash
./gradlew build     # compila, roda todos os testes e verifica a cobertura
./gradlew test      # só os testes
./gradlew pitest    # mutation testing (fora do build padrão)
```

O build **falha se a cobertura ficar abaixo de 90%** de linhas ou de branches.

Relatórios gerados:

| Relatório | Caminho |
|---|---|
| Resultado dos testes | `build/reports/tests/test/index.html` |
| Cobertura (JaCoCo) | `build/reports/jacoco/test/html/index.html` |
| Mutation testing (PIT) | `build/reports/pitest/index.html` |

## Deploy em produção

A produção roda numa VM `x86_64` da Oracle Cloud (1 GB de RAM + 2 GB de swap, usuário `ubuntu`, diretório `~/app`), com Docker Compose. A VM não compila nada: ela só baixa a imagem publicada no GitHub Container Registry (GHCR). O PostgreSQL fica em outra VM, acessado pelo IP privado.

### Como funciona

| Evento | O que roda | Workflow |
|---|---|---|
| PR para a `main` | só os testes (`./gradlew build`, check `build` obrigatório) | `ci.yml` |
| Merge (push) na `main` | os testes e, **se o PR tiver o label `build`**, a publicação de `ghcr.io/manuelaalecio/educare-backend:<sha7>` e `:latest`. Nunca faz deploy | `ci.yml` |
| Tag `v*` | deploy da imagem `<sha7>` do commit da tag, que precisa estar na `main` | `deploy.yml` |
| Disparo manual (`workflow_dispatch`) | deploy da `image_tag` informada (rollback) ou, vazia, do `<sha7>` do commit | `deploy.yml` |

`<sha7>` são os 7 primeiros caracteres do SHA do commit de merge na `main`. O deploy nunca reconstrói a imagem: ele só implanta uma que já foi publicada. Por isso, uma tag num commit cujo PR não tinha o label `build` falha antes de conectar na VM.

**Esqueceu o label?** Adicione o label ao PR já mergeado e reexecute a execução do push na `main`. A imagem daquele commit é publicada sem precisar de outro commit:

```bash
gh pr edit <numero-do-pr> --add-label build
gh run list --workflow ci.yml --branch main --limit 5    # pega o id da execução do merge
gh run rerun <id-da-execucao>
```

O deploy:

1. copia `deploy/compose.yaml`, `deploy/Caddyfile` e `deploy/deploy.sh` para `~/app`;
2. grava `~/app/deploy.env` só com `IMAGE_TAG=<tag>`, sem tocar no `~/app/.env`;
3. roda `docker compose pull` e `up -d` com os dois arquivos;
4. espera `http://localhost:8080/actuator/health` responder `UP` de dentro da VM por até ~3 minutos. Se não responder, mostra os logs da `api` e falha.

Arquivos de produção, em `deploy/`:

| Arquivo | Conteúdo |
|---|---|
| `compose.yaml` | `api` (imagem do GHCR, porta só em `127.0.0.1:8080`, JVM limitada a 300 MB de heap) e `caddy` (portas 80 e 443, proxy para a `api`). Sem banco |
| `Caddyfile` | proxy reverso no endereço de `SITE_ADDRESS` (`:80` para HTTP pelo IP, ou um domínio para HTTPS automático) |
| `deploy.sh` | script executado na VM pelo workflow |

O `docker-compose.yaml` da raiz continua sendo só o ambiente local.

### Configuração inicial (manual, uma vez)

O pipeline não roda nada na VM fora do deploy e não cria secrets. Os passos abaixo são feitos à mão. Os comandos locais funcionam no fish e no bash; troque `<ip-publico>` pelo IP público da VM.

**1. Chave de deploy.** Gere uma chave só para o GitHub Actions e autorize-a na VM (use o acesso SSH que você já tem):

```bash
ssh-keygen -t ed25519 -N '' -C educare-deploy -f ~/.ssh/educare_deploy
ssh-copy-id -i ~/.ssh/educare_deploy.pub ubuntu@<ip-publico>
```

**2. Chave do host (`known_hosts`).** Obtenha a chave pública do SSH da VM e confira a impressão digital antes de usar. As duas linhas impressas precisam ser iguais:

```bash
ssh-keyscan -t ed25519 <ip-publico> > ~/educare_known_hosts
ssh-keygen -lf ~/educare_known_hosts
ssh ubuntu@<ip-publico> ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

**3. Environment `production` e secrets.** O environment só aceita a `main` e as tags `v*`, e é o único lugar com os secrets de SSH:

```bash
gh api -X PUT repos/manuelaalecio/educare-backend/environments/production -F 'deployment_branch_policy[protected_branches]=false' -F 'deployment_branch_policy[custom_branch_policies]=true'
gh api -X POST repos/manuelaalecio/educare-backend/environments/production/deployment-branch-policies -f name=main -f type=branch
gh api -X POST repos/manuelaalecio/educare-backend/environments/production/deployment-branch-policies -f 'name=v*' -f type=tag
gh secret set SSH_HOST --env production --body <ip-publico>
gh secret set SSH_USER --env production --body ubuntu
gh secret set SSH_PRIVATE_KEY --env production < ~/.ssh/educare_deploy
gh secret set SSH_KNOWN_HOSTS --env production < ~/educare_known_hosts
```

Depois, apague a cópia local do `known_hosts` (`rm ~/educare_known_hosts`). Guarde a chave `~/.ssh/educare_deploy` ou apague-a: ela só é necessária para cadastrar o secret de novo.

**4. `.env` da VM.** O `~/app/.env` (permissão 600) já tem `DB_HOST`, `DB_PASSWORD` e `SITE_ADDRESS=:80`. Acrescente à mão as variáveis que o profile `prod` exige. O pipeline nunca lê nem altera esse arquivo, e o `compose.yaml` se recusa a subir se faltar alguma:

```bash
openssl rand -base64 48    # gera um valor para EDUCARE_JWT_SECRET (mínimo de 32 bytes)
ssh ubuntu@<ip-publico>
nano ~/app/.env            # na VM: acrescente as três linhas abaixo
chmod 600 ~/app/.env
```

```
EDUCARE_ADMIN_PASSWORD=<senha inicial do admin>
EDUCARE_JWT_SECRET=<valor gerado acima>
EDUCARE_CORS_ALLOWED_ORIGINS=<origem do frontend, ex. https://educare.exemplo.org>
```

**5. Portas e deploy antigo.** Na console da Oracle, libere a entrada TCP nas portas 80 e 443 na security list da VCN da VM. A porta 8080 não precisa ser liberada: ela só atende de dentro da VM. Na VM, pare os containers do deploy manual antigo que ocupem as portas 80 ou 8080 (`docker ps` mostra quais são; pare-os com `docker stop <nome>` e `docker rm <nome>`).

**6. Label `build` e pacote público.** Crie o label:

```bash
gh label create build --color 0E8A16 --description "Publica a imagem no GHCR após o merge na main"
```

Depois do primeiro merge com o label, a imagem aparece em **github.com/manuelaalecio → Packages → educare-backend**. Em **Package settings → Change visibility**, torne o pacote **público** (a VM baixa a imagem sem login). Para conferir, rode `docker manifest inspect ghcr.io/manuelaalecio/educare-backend:latest` sem estar logado.

**7. Primeiro deploy.** Crie a tag no commit da `main` que tem imagem publicada:

```bash
git switch main
git pull
git tag -a v0.1.0 -m v0.1.0
git push origin v0.1.0
gh run watch                                   # acompanha o deploy
curl http://<ip-publico>/actuator/health       # deve conter "status":"UP"
```

### Rollback

Dispare o deploy com a tag de uma imagem anterior. As tags disponíveis ficam na página do pacote, e a tag de cada deploy fica no resumo da execução do `deploy.yml`:

```bash
gh workflow run deploy.yml --ref main -f image_tag=<sha7>
```

Na VM, `~/app/deploy.env` mostra a tag em produção. Para repetir o estado atual à mão, rode `docker compose --env-file .env --env-file deploy.env up -d` em `~/app`.

## Como o projeto é desenvolvido

O desenvolvimento segue **Spec-Driven Development (SDD)** com [OpenSpec](https://github.com/Fission-AI/OpenSpec), assistido por IA:

1. Cada funcionalidade começa como uma proposta (proposal, design, specs e tasks) em `openspec/changes/`.
2. Depois de implementada, suas specs passam para `openspec/specs/`, que descreve o comportamento atual do sistema.
3. A change concluída é arquivada em `openspec/changes/archive/`.

Onde encontrar as decisões do projeto:

| Arquivo | Conteúdo |
|---|---|
| `openspec/config.yaml` | visão do produto, glossário, padrões de API, persistência e segurança |
| `CLAUDE.md` | arquitetura, estrutura do código, convenções e estratégia de testes |
| `openspec/specs/` | especificação de cada funcionalidade |
