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

## Contrato da API

O contrato OpenAPI da API fica versionado em [`openapi/educare-api.json`](openapi/educare-api.json). Ele é gerado pela própria aplicação (springdoc) e é a fonte de onde o frontend gera os tipos dele. Como o repositório é público, o frontend lê o contrato da `main`, sem credenciais:

```
https://raw.githubusercontent.com/manuelaalecio/educare-backend/main/openapi/educare-api.json
```

O `./gradlew build` (e, portanto, o CI) **falha quando o arquivo commitado é diferente do documento que a aplicação gera**, ou quando ele não existe. Isso acontece sempre que um endpoint, um DTO ou uma anotação de documentação muda. A mensagem da falha indica o comando que regenera o arquivo:

```bash
./gradlew updateApiContract    # reescreve openapi/educare-api.json (precisa do Docker, como os testes)
```

Depois de rodar, revise o diff do `openapi/educare-api.json` e commite junto com a mudança. O diff no PR mostra o que mudou para o frontend.

No profile `dev`, o mesmo documento fica em `/v3/api-docs` e no Swagger UI. Fora do `dev` eles não são servidos: o contrato público é o arquivo do repositório.

## Deploy em produção

A produção roda num cluster **k3s** de nó único, numa VM Oracle Cloud Ampere `arm64` (2 OCPUs, 12 GB, Ubuntu 24.04), com Traefik como Ingress e `local-path` como storage. O **Argo CD**, no namespace `argocd`, aplica os manifests versionados neste repositório. O PostgreSQL roda dentro do cluster. O pipeline não usa SSH nem acessa o cluster: a CI só constrói e publica a imagem, e o cluster busca as versões novas sozinho.

### Como funciona

| Evento | O que roda |
|---|---|
| PR para a `main` | os testes (`./gradlew build`, check `build` obrigatório) e a validação dos manifests (job `manifests`) |
| Merge (push) na `main` | os testes e, **se o PR tiver o label `build`**, a publicação de `ghcr.io/manuelaalecio/educare-backend:<sha7>` e `:latest` |
| Tag `vX.Y.Z` num commit da `main` | os testes e a publicação de `:vX.Y.Z`, `:<sha7>` e `:latest`, sem exigir o label. Uma tag fora da `main` ou fora do formato `vX.Y.Z` não publica nada |

As imagens são só `linux/arm64`, construídas num runner arm nativo do GitHub Actions. `<sha7>` são os 7 primeiros caracteres do SHA do commit.

No cluster:

1. O **Argo CD** sincroniza a Application `educare` com `deploy/k8s` da `main`, com `prune` e `selfHeal`. Mudanças nos manifests chegam ao cluster depois do merge.
2. O **Argo CD Image Updater** consulta o GHCR a cada 2 minutos. Quando encontra uma tag `vX.Y.Z` maior que a atual, grava essa tag direto na Application, sem commit em nenhuma branch, e o Argo CD faz o rolling update. A versão nova só recebe tráfego quando `/actuator/health/readiness` responde `UP`.

A tag do `images:` em `deploy/k8s/kustomization.yaml` é só a **tag de bootstrap**: o cluster a usa se a Application for recriada, até o Image Updater subir de novo para a maior `vX.Y.Z`.

**Esqueceu o label?** Adicione o label ao PR já mergeado e reexecute a execução do push na `main`. A imagem daquele commit é publicada sem precisar de outro commit:

```bash
gh pr edit <numero-do-pr> --add-label build
gh run list --workflow ci.yml --branch main --limit 5    # pega o id da execução do merge
gh run rerun <id-da-execucao>
```

Arquivos de produção:

| Caminho | Conteúdo |
|---|---|
| `deploy/k8s/` | Kustomize: Namespace `educare`, Postgres (StatefulSet `postgres:16`, PVC de 10 Gi no `local-path`, Service só interno), API (Deployment e Service `educare-backend`) e Ingress Traefik sem host |
| `deploy/argocd/application.yaml` | Application `educare`, aplicada uma vez com `kubectl` |
| `deploy/argocd/image-updater.yaml` | `ImageUpdater` `educare`: estratégia semver, só tags `vX.Y.Z`, write-back direto na Application |

O `docker-compose.yaml` da raiz continua sendo só o ambiente local.

> **Atenção, antes de ter dados reais:**
> - **Não há backup.** O banco fica no disco local da VM (`local-path`): perder a VM é perder os dados. O backup é uma tarefa separada.
> - **Nunca exclua o PVC** `data-postgres-0` do namespace `educare`: o `local-path` apaga o diretório dos dados junto.
> - **Só HTTP.** O Ingress responde em `http://<ip-publico>/`, sem TLS. Enquanto não houver HTTPS (domínio + cert-manager, já previstos comentados em `deploy/k8s/ingress.yaml`), o frontend de produção não deve apontar para o cluster: o BFF mandaria o token e as senhas do login em texto claro.

### Configuração inicial (manual, uma vez)

O k3s e o Argo CD já estão instalados na VM. Os passos abaixo são feitos à mão, da sua máquina. Os comandos funcionam no fish e no bash; troque `<ip-publico>` pelo IP público da VM.

**1. Túnel para o cluster.** A API do Kubernetes (6443) e o Argo CD **nunca** são expostos na internet. Na security list da VCN e no firewall da VM, só as portas 22 e 80 ficam abertas (443 quando houver HTTPS). Copie o kubeconfig da VM uma vez. Ele já aponta para `https://127.0.0.1:6443`:

```bash
mkdir -p ~/.kube
ssh ubuntu@<ip-publico> sudo cat /etc/rancher/k3s/k3s.yaml > ~/.kube/educare.yaml
chmod 600 ~/.kube/educare.yaml
```

Sempre que for operar o cluster, abra o túnel num terminal e deixe-o aberto:

```bash
ssh -N -L 6443:127.0.0.1:6443 ubuntu@<ip-publico>
```

Em outro terminal, aponte o `kubectl` para o cluster e confira:

```bash
set -gx KUBECONFIG ~/.kube/educare.yaml     # no bash: export KUBECONFIG=~/.kube/educare.yaml
kubectl get nodes
```

Para usar o Argo CD (interface em `https://localhost:8081` e CLI `argocd`), faça um port-forward pelo mesmo túnel e pegue a senha inicial do usuário `admin`:

```bash
kubectl -n argocd port-forward svc/argocd-server 8081:443
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
argocd login localhost:8081 --username admin --insecure
```

**2. Namespace e Secret `educare-env`.** O Secret guarda todos os valores sensíveis e nunca é versionado. Crie-o **antes** de registrar a Application:

```bash
kubectl create namespace educare
openssl rand -base64 48    # gera um valor para EDUCARE_JWT_SECRET (mínimo de 32 bytes)
kubectl -n educare create secret generic educare-env \
  --from-literal=DB_PASSWORD='<senha do banco>' \
  --from-literal=EDUCARE_JWT_SECRET='<valor gerado acima>' \
  --from-literal=EDUCARE_ADMIN_PASSWORD='<senha inicial do admin>' \
  --from-literal=EDUCARE_CORS_ALLOWED_ORIGINS='<origem do frontend, ex. https://educare.exemplo.org>'
```

Se o Secret ou alguma dessas quatro chaves faltar, os pods da API e do Postgres ficam em **`CreateContainerConfigError`** (veja com `kubectl -n educare get pods` e `kubectl -n educare describe pod <nome>`). Nenhum valor padrão é usado no lugar. Para corrigir, crie o Secret, ou recrie-o com todas as chaves, e os pods sobem sozinhos.

O banco começa vazio: o Flyway cria o schema e o usuário `admin@educare.org`, com a senha de `EDUCARE_ADMIN_PASSWORD`. Trocar `DB_PASSWORD` no Secret depois que o banco foi criado **não** muda a senha do Postgres (ela só vale na criação do volume). Trocar `EDUCARE_ADMIN_PASSWORD` também não muda o admin já criado.

**3. Instalar o Argo CD Image Updater.** Instale a versão fixada no namespace do Argo CD:

```bash
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj-labs/argocd-image-updater/v1.4.0/config/install.yaml
kubectl -n argocd rollout status deployment argocd-image-updater-controller
```

O manifest oficial reserva 512Mi de memória para o Image Updater. Para um único app basta menos, e você pode reduzir a reserva (opcional):

```bash
kubectl -n argocd set resources deployment argocd-image-updater-controller --requests=cpu=50m,memory=128Mi --limits=memory=256Mi
```

Se o nome do deployment for outro na versão instalada, confira com `kubectl -n argocd get deployments`.

**4. Primeira versão `arm64`.** A Application usa a tag de bootstrap do `kustomization.yaml` (`v0.2.0`), que precisa existir no GHCR antes do registro. Crie a tag num commit da `main` (passo "Publicar uma versão" abaixo) e espere a CI publicar.

O pacote `educare-backend` no GHCR precisa continuar **público**: o cluster e o Image Updater baixam e listam as tags sem credencial. Se ele virar privado, é preciso um Secret de pull no namespace `educare` e uma credencial no Image Updater.

**5. Registrar a Application e o ImageUpdater.** Aplique os dois arquivos uma vez:

```bash
kubectl apply -f deploy/argocd/application.yaml
kubectl apply -f deploy/argocd/image-updater.yaml
argocd app get educare                                    # Sync Status: Synced, Health Status: Healthy
curl http://<ip-publico>/actuator/health/readiness        # {"status":"UP"}
```

Os dois arquivos não fazem parte do que o Argo CD sincroniza: quando mudarem no repositório, reaplique-os à mão. A Application não declara `spec.source.kustomize` de propósito, para que o reapply não desfaça a tag gravada pelo Image Updater. Não coloque a Application num app-of-apps: o app pai desfaria essa tag a cada sync.

Se a API não responder pelo IP público com o pod `Running` e `Ready`, confira se a porta 80 está liberada na security list da VCN e no `iptables` da VM. As imagens Ubuntu da Oracle trazem regras que recusam tudo o que não foi liberado, e a regra nova precisa ficar antes da linha `REJECT`:

```bash
ssh ubuntu@<ip-publico> sudo iptables -L INPUT -n --line-numbers
ssh ubuntu@<ip-publico> sudo iptables -I INPUT <linha-do-reject> -m state --state NEW -p tcp --dport 80 -j ACCEPT
ssh ubuntu@<ip-publico> sudo netfilter-persistent save
```

### Publicar uma versão

Crie uma tag anotada `vX.Y.Z` num commit da `main` e envie:

```bash
git switch main
git pull
git tag -a v0.2.1 -m v0.2.1
git push origin v0.2.1
gh run watch                                                                  # acompanha a CI
docker buildx imagetools inspect ghcr.io/manuelaalecio/educare-backend:v0.2.1   # só linux/arm64
```

Em alguns minutos o Image Updater leva a Application para a tag nova. Para conferir qual tag o cluster está usando:

```bash
kubectl -n argocd get application educare -o jsonpath='{.spec.source.kustomize.images}'
kubectl -n educare get deployment educare-backend -o jsonpath='{.spec.template.spec.containers[0].image}'
```

As migrations do Flyway rodam no pod novo enquanto o antigo ainda atende. Por isso, uma versão precisa continuar compatível com o schema da versão anterior.

### Rollback

**Fixar uma tag (padrão).** Restrinja o `ImageUpdater` à tag desejada, senão ele volta para a maior `vX.Y.Z` no ciclo seguinte. Depois, aplique a tag na Application sem esperar o ciclo. Nenhum dos dois passos exige commit:

```bash
kubectl -n argocd patch imageupdater educare --type json -p '[{"op":"replace","path":"/spec/applicationRefs/0/images/0/commonUpdateSettings/allowTags","value":"regexp:^v0[.]2[.]0$"}]'
argocd app set educare --kustomize-image ghcr.io/manuelaalecio/educare-backend:v0.2.0
```

Para soltar a fixação, depois de publicar a versão com a correção, reaplique o arquivo original:

```bash
kubectl apply -f deploy/argocd/image-updater.yaml
```

**Pelo histórico do Argo CD.** O `argocd app rollback` não funciona com o sync automático ligado. Desligue o sync, volte para a revisão escolhida e religue o sync depois:

```bash
argocd app set educare --sync-policy none
argocd app history educare
argocd app rollback educare <id-da-revisao>
argocd app set educare --sync-policy automated --auto-prune --self-heal
```

Ao religar o sync, o Image Updater volta para a maior `vX.Y.Z`, a menos que a tag tenha sido fixada antes no `ImageUpdater`, como no rollback padrão. Prefira o rollback padrão, ou publique uma versão nova com a correção.

**Voltar para uma versão anterior à `V7` (funções `COORDENADOR`/`EDUCADOR`).** A `V7__rename_roles_to_coordinator_educator.sql` converte as roles `ADMIN`/`USER` e não é compatível com as versões anteriores: com os dados novos, uma imagem antiga responde `500` em toda requisição autenticada. Antes de fixar a tag antiga, reverta os dados à mão (o Flyway não tem `undo`):

```bash
kubectl -n educare exec -i postgres-0 -- psql -U app -d app -v ON_ERROR_STOP=1 <<'SQL'
BEGIN;
ALTER TABLE users DROP CONSTRAINT users_role_check;
UPDATE users SET role = CASE role WHEN 'COORDENADOR' THEN 'ADMIN' WHEN 'EDUCADOR' THEN 'USER' END;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'USER'));
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'USER';
DELETE FROM flyway_schema_history WHERE version = '7';
COMMIT;
SQL
```

O front precisa voltar junto para a versão que espera `ADMIN`/`USER`.

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
