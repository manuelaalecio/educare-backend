# Design

## Context

Motivação e escopo: ver `proposal.md`. O estado atual que molda a abordagem:

- **CI (`.github/workflows/ci.yml`)**:
  - Gatilhos `pull_request` e `push` na `main`.
  - Jobs:
    - `build`: o id é o check obrigatório e não pode mudar.
    - `publish-gate`: só em push na `main`; publica se o PR mergeado tem o label `build`.
    - `image`: `ubuntu-latest`, `platforms: linux/amd64`.
  - As tags `<sha7>` e `latest` são calculadas num passo bash, sem `docker/metadata-action`.
  - O `concurrency` usa `github.ref` e não cancela em push.
- **Imagem**:
  - O `Dockerfile` compila o jar dentro do Docker (`eclipse-temurin:21-jdk` → `21-jre`), roda como o usuário não root `app` (por nome, sem UID numérico) e não define flags de JVM.
  - As bases e o `postgres:16` são multi-arch (incluem `linux/arm64/v8`).
  - **A imagem publicada hoje no GHCR é só amd64** e não roda no nó arm64.
- **Deploy antigo**:
  - O `deploy.yml` (tag `v*`/`workflow_dispatch` → SSH → `deploy.sh`) já confere que o commit da tag está na `main`.
  - O `deploy/compose.yaml` define `JAVA_TOOL_OPTIONS: -Xmx300m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m` e lê `DB_HOST`.
- **Actuator**:
  - O `application.yaml` expõe só o `health`, com `show-details`/`show-components: never`.
  - Os probes existem só porque o Spring Boot 4.1.1 os habilita por padrão: `AvailabilityProbesAutoConfiguration`, `management.endpoint.health.probes.enabled` com `matchIfMissing = true`.
  - No Boot 4 isso **não depende mais de detectar Kubernetes**, e os grupos de probe ignoram `show-details`.
  - A readiness contém só `readinessState`.
  - O `SecurityScenarioTest` já cobre os probes sem profile.
- **Cluster**:
  - k3s v1.36 de nó único com Traefik v3 (LoadBalancer pelo ServiceLB nas portas 80/443 do nó) e `local-path` (diretório no host, `reclaimPolicy: Delete`).
  - Argo CD no namespace `argocd`, só por túnel SSH.
- **Image Updater**: a versão atual (v1.4.0) só é configurada pelo CRD `ImageUpdater` (`argocd-image-updater.argoproj.io/v1alpha1`). As anotações na Application são legado e só valem através de um CR com `useAnnotations: true`.

## Goals / Non-Goals

**Goals:**
- O estado do cluster é reproduzível a partir de três coisas: o repositório (`deploy/k8s`, `deploy/argocd`), um Secret criado à mão e a maior tag `vX.Y.Z` publicada.
- Nenhuma automação escreve no git. A CI só tem `packages: write` no job da imagem.
- Uma versão nova não derruba a anterior antes de a nova ficar pronta (readiness).

**Non-Goals:**
- Zero downtime em atualização do Postgres ou do nó: o banco tem uma réplica e o volume é RWO.
- Teste automatizado do pipeline ou do cluster. Os workflows e os manifests são verificados por kustomize/kubeconform na CI e pelas tasks manuais do grupo final, como no D1 das changes `add-ci-workflow` e `add-cd-pipeline`.

## Decisions

### D1. Sem capability nova; só o requisito do Actuator muda

CI, manifests e Argo CD são ferramenta de entrega, sem Scenario testável por `@SpringBootTest`. O que o Kubernetes passa a exigir da aplicação entra no requisito "Actuator restrito ao health, sem detalhes" da spec `security` (ver `specs/security/spec.md`):
- os probes existem no profile `prod`, mesmo sem variáveis do Kubernetes;
- a readiness responde `503 OUT_OF_SERVICE` quando a aplicação recusa tráfego, e a liveness continua `UP`.

O delta é MODIFIED e não ADDED porque é o mesmo requisito, com duas garantias a mais.

**Alternativa rejeitada:** uma capability `delivery`. Os Scenarios ("WHEN uma tag é criada THEN o cluster atualiza") não teriam teste correspondente, o que quebra a regra de que todo Scenario tem teste de cenário.

### D2. Probes explícitos, readiness sem o banco

```yaml
management:
  endpoint:
    health:
      show-details: never
      show-components: never
      probes:
        enabled: true      # explícito: o deploy depende disso (spec security)
```

- **Por que explícito:** hoje vale o padrão do framework (`matchIfMissing`). Fixar o valor documenta a dependência do deploy e protege contra mudança de padrão numa versão futura. É o mesmo raciocínio do `exposure.include: health` da change `add-cd-pipeline`.
- **A readiness não inclui `db`.**
  - Com uma réplica, o banco fora do ar deixa o serviço indisponível de qualquer forma.
  - Incluir o `db` só troca o 500 da aplicação por um 503 do Traefik, e acopla a decisão de tráfego a uma dependência externa, o que a documentação do Spring desaconselha.
  - Também faria um soluço do Postgres tirar o pod do Service durante o rolling update.
  - A liveness nunca inclui o banco, para que um banco fora do ar não reinicie a API em loop.
- **Testes:**
  - "Probes disponíveis no profile prod" entra no `SecurityStartupScenarioTest`, que já sobe o `prod` numa porta real.
  - "Readiness recusa tráfego" entra num `@SpringBootTest` próprio, que publica `AvailabilityChangeEvent` com `ReadinessState.REFUSING_TRAFFIC` e verifica 503 na readiness e 200 na liveness. Ele usa `@DirtiesContext` para não contaminar o contexto compartilhado.

**Alternativa rejeitada:** `management.endpoint.health.group.readiness.include: readinessState,db`. Os motivos estão acima.

### D3. CI: imagem arm64 em runner nativo e tag de versão

```yaml
on:
  pull_request:
    branches: [main]
  push:
    branches: [main]
    tags: ['v*']
```

- **`image`**:
  - Passa a rodar em `runs-on: ubuntu-24.04-arm` com `platforms: linux/arm64`, sem QEMU e sem mudar o `Dockerfile`.
  - Runner hospedado padrão: gratuito e ilimitado em repositório público.
  - O `bootJar` sob QEMU levaria muitas vezes mais tempo.
- **Alternativas rejeitadas:**
  - QEMU em `ubuntu-latest`: lento demais.
  - Jar pré-compilado copiado para um Dockerfile só de runtime: exige artifact entre jobs, desligar o jar `plain` e mudar o `.dockerignore`, e o `docker build` local deixaria de compilar.
- **`publish-gate`**:
  - Passa a rodar em todo `push` e decide por tipo de ref:
    - `refs/heads/main`: a regra atual do label `build`, sem mudança.
    - `refs/tags/v*`: publica sem label (criar a tag já é o ato de release), **desde que o commit da tag esteja na `main`** (`git merge-base --is-ancestor`, com `fetch-depth: 0`). É a mesma verificação que hoje fica no `deploy.yml`, que é removido.
    - Tag com o formato fora de `^v[0-9]+\.[0-9]+\.[0-9]+$`: não publica, e o motivo vai para o `$GITHUB_STEP_SUMMARY`.
  - O `if:` do `image` continua `needs.publish-gate.outputs.publish == 'true'`, e o `build` (testes) roda também na tag.
- **Tags**:
  - O passo bash `meta` continua; em tag acrescenta `$image:$GITHUB_REF_NAME`.
  - Resultado: `<sha7>` e `latest` sempre, e `vX.Y.Z` em tag.
  - **Alternativa rejeitada:** `docker/metadata-action`. Seria uma action nova, `type=semver,pattern={{version}}` tira o `v`, e o `latest` automático depende do tipo de evento. O bash atual é explícito e já testado.
- **Novo job `manifests`** (em `ubuntu-latest`, que tem `kubectl`; o runner arm não tem):
  - Roda `kubectl kustomize deploy/k8s` e passa o resultado pelo kubeconform v0.8.0 (binário baixado e conferido pelo `CHECKSUMS` publicado), com `-strict -summary -kubernetes-version 1.36.0`.
  - Valida também `deploy/argocd/*.yaml`, com o schema de CRDs do catálogo `datreeio/CRDs-catalog`.
  - O `ImageUpdater` é pulado com `-skip ImageUpdater` (decisão da task 3.3): o catálogo tem o schema `imageupdater_v1alpha1`, mas ele está desatualizado (ainda exige `spec.namespace`, removido na v1.2) e recusa o arquivo válido para a v1.4.0. O arquivo foi validado localmente contra o CRD do `install.yaml` da v1.4.0. Não se usa `-ignore-missing-schemas`, que esconderia erro de digitação em `kind`.
  - O `image` passa a depender de `manifests`.
  - O check obrigatório da `main` continua sendo só `build`. **Alternativa rejeitada:** torná-lo obrigatório, porque exigiria mudar a proteção da branch à mão; fica como sugestão no README.
- **Regras dos workflows mantidas:** valores só por `env:`, sem `${{ }}` dentro de `run:`; permissões mínimas por job; actionlint.

### D4. Manifests em `deploy/k8s/` (Kustomize)

```
deploy/k8s/
├── kustomization.yaml        # namespace: educare; resources; images: (tag de bootstrap)
├── namespace.yaml
├── ingress.yaml
├── postgres/{statefulset.yaml, service.yaml}
└── api/{deployment.yaml, service.yaml}
```

- **`kustomization.yaml`**:
  - Tem `namespace: educare` e `labels` comuns (`app.kubernetes.io/part-of: educare`).
  - `images: [{name: ghcr.io/manuelaalecio/educare-backend, newTag: v0.2.0}]`. Essa é a **tag de bootstrap**: a primeira versão arm64, publicada antes de registrar a Application.
  - Depois disso, a tag em produção é a que o Image Updater grava na Application (D6). A do arquivo só é usada se a Application for recriada.
- **Postgres**:
  - StatefulSet `postgres` com 1 réplica, `postgres:16` (major fixado: o 18 muda o `PGDATA` e exige migração deliberada).
  - `volumeClaimTemplates` de 10Gi, `ReadWriteOnce`, `storageClassName: local-path`.
  - `PGDATA=/var/lib/postgresql/data/pgdata` (subdiretório, portável para storage em bloco).
  - `POSTGRES_DB=app`, `POSTGRES_USER=app`, `POSTGRES_PASSWORD` de `educare-env/DB_PASSWORD`.
  - Readiness e liveness por `pg_isready -U app -d app -h 127.0.0.1` (TCP, para não marcar pronto durante o initdb, que escuta só no socket).
  - Resources: request 256Mi, limit 1Gi.
  - Service `postgres` headless (`clusterIP: None`, que é do tipo ClusterIP). `postgres.educare.svc:5432` resolve para o pod, e o Service serve de `serviceName` do StatefulSet.
  - Nenhum NodePort, LoadBalancer nem Ingress.
  - Os PVCs de `volumeClaimTemplates` não são rastreados pelo Argo CD (não sofrem prune) e ficam retidos se o StatefulSet for excluído.
- **API**:
  - Deployment `educare-backend` com 1 réplica, `RollingUpdate` com `maxSurge: 1, maxUnavailable: 0` (a API é stateless e o nó comporta dois pods). `revisionHistoryLimit: 3`.
  - **Env**:
    - `SPRING_PROFILES_ACTIVE=prod`
    - `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres.educare.svc:5432/app`
    - `SPRING_DATASOURCE_USERNAME=app`
    - De `educare-env` por `secretKeyRef`: `SPRING_DATASOURCE_PASSWORD` (chave `DB_PASSWORD`), `EDUCARE_JWT_SECRET`, `EDUCARE_ADMIN_PASSWORD` e `EDUCARE_CORS_ALLOWED_ORIGINS`.
    - `secretKeyRef` explícito, e não `envFrom`, para que cada chave ausente também gere `CreateContainerConfigError` e nenhuma chave extra do Secret vire variável. `optional` fica no padrão (`false`).
  - **Memória**:
    - Request 512Mi e limit 1Gi. Request de CPU de 250m, sem limit de CPU, porque o throttling atrasa muito a subida da JVM.
    - `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`, sem `-Xmx`.
    - Por que 60% e não os 75% sugeridos na tarefa: com limit de 1Gi, 75% dá ~768MiB de heap e deixa ~256MiB para metaspace, code cache e threads do Tomcat, no limite de um OOMKill do cgroup. 60% dá ~614MiB, o dobro do heap atual (300MiB), com folga.
    - O `SerialGC` explícito mantém o GC de hoje.
  - **Probes** (porta `http`, 8080):
    - `startupProbe` na liveness: `periodSeconds: 5`, `failureThreshold: 36`, ou seja até 180s para a primeira subida, com Flyway, em 2 OCPUs.
    - `livenessProbe` na liveness e `readinessProbe` na readiness: `periodSeconds: 10`, `timeoutSeconds: 3`, `failureThreshold: 3`.
  - **Desligamento**: `lifecycle.preStop.sleep.seconds: 10`, para o Traefik tirar o pod do balanceamento antes do shutdown gracioso do Spring, e `terminationGracePeriodSeconds: 45`.
  - **Segurança do pod**:
    - `automountServiceAccountToken: false`.
    - `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`, `seccompProfile: RuntimeDefault`.
    - **Sem `runAsNonRoot`**: o `USER app` da imagem é por nome, e o kubelet recusa `runAsNonRoot` sem UID numérico. A imagem já roda como não root. Dar UID fixo à imagem fica para outra change.
  - Service `educare-backend` ClusterIP, porta 8080.
  - Sem `imagePullSecrets`: o pacote é público.
- **Ingress** `educare-backend`:
  - `ingressClassName: traefik`, regra sem `host`, `path: /` `Prefix` → `educare-backend:8080`.
  - Ficam comentados `host`, `tls` e `cert-manager.io/cluster-issuer`, para a change de HTTPS.
  - O Ingress expõe tudo que a API expõe; o Actuator já é limitado pela spec `security`.

**Alternativa rejeitada:** Helm. Para um app e um ambiente, o Kustomize (embutido no `kubectl` e no Argo CD) basta e não traz dependência nova.

### D5. Application do Argo CD em `deploy/argocd/application.yaml`

- `Application` `educare`, namespace `argocd`, `project: default`.
- `source`: `repoURL: https://github.com/manuelaalecio/educare-backend.git`, `targetRevision: main`, `path: deploy/k8s`.
- `destination`: `https://kubernetes.default.svc`, namespace `educare`.
- `syncPolicy.automated: {prune: true, selfHeal: true}` e `syncOptions: [CreateNamespace=false]`. O Namespace vem dos manifests e já existe porque o Secret é criado antes.
- **O arquivo não tem `spec.source.kustomize`.** O Image Updater escreve nesse campo, e um `kubectl apply` posterior do arquivo não o sobrescreve (o merge preserva campos que nunca estiveram no `last-applied`).
- **Aplicada uma vez, com `kubectl apply`, e não por app-of-apps.** Com app-of-apps e `selfHeal`, o pai reverteria o override do Image Updater a cada ciclo (flip-flop).
- **Alternativa rejeitada:** app-of-apps com `ignoreDifferences` em `/spec/source/kustomize` + `RespectIgnoreDifferences`. Seria mais um app e mais uma regra frágil para um único Application.

### D6. Entrega de versão: Argo CD Image Updater com write-back `argocd`

`deploy/argocd/image-updater.yaml`, conferido contra o schema do CRD da v1.4.0:

```yaml
apiVersion: argocd-image-updater.argoproj.io/v1alpha1
kind: ImageUpdater
metadata:
  name: educare
  namespace: argocd
spec:
  applicationRefs:
    - namePattern: educare
      writeBackConfig:
        method: argocd
      images:
        - alias: api
          imageName: ghcr.io/manuelaalecio/educare-backend
          commonUpdateSettings:
            updateStrategy: semver
            allowTags: 'regexp:^v\d+\.\d+\.\d+$'
          manifestTargets:
            kustomize:
              name: ghcr.io/manuelaalecio/educare-backend
```

- **Como funciona**:
  - A cada 2 min (padrão), o updater lista as tags do GHCR anonimamente (pacote público) e escolhe a maior `vX.Y.Z`. O `semver` trata `v1.2.3` e `1.2.3` como iguais.
  - Ele grava `spec.source.kustomize.images` na Application do cluster. O auto-sync dispara a cada combinação nova de commit + parâmetros.
  - O `selfHeal` da própria Application não reverte o override, porque o override faz parte do estado desejado.
- **`allowTags` é obrigatório**: sem ele, um `<sha7>` só com dígitos (ex.: `1234567`) pode ser lido como semver `1234567.0.0` e ganhar. Também exclui `latest`.
- **`platforms` não se aplica**: só vale para as estratégias `digest`/`newest-build`, e a imagem é só arm64.
- **Instalação** (manual, documentada no README): `kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj-labs/argocd-image-updater/v1.4.0/config/install.yaml`. O manifest é fixado na versão, e não em `stable`, para ser reproduzível.
  - O CR e a Application ficam em `deploy/argocd/` e são aplicados uma vez com `kubectl`.
  - Mudanças nesses arquivos exigem reaplicar à mão: eles não estão no `deploy/k8s` sincronizado.
- **Por que write-back `argocd` e não a branch `release`**:
  - A branch `release` exigiria `contents: write` na CI de um repositório público e um token com escrita.
  - Também criaria divergência entre `main` e `release`, que precisaria de merge ou force-push a cada versão, e corridas entre pushes.
  - O write-back `git` na `main` viola a RN01.
  - Custo aceito: o override é "pseudo-persistente". Se a Application for recriada, volta à tag de bootstrap do `kustomization.yaml` até o próximo ciclo do updater (≤ 2 min), que a leva de novo à maior `v*`.
  - Auditoria pelo histórico do Argo CD, eventos e logs do updater.
- **Custo de memória**: o manifest oficial pede 512Mi de request e 1Gi de limit. No nó de 12 GB cabe junto com Postgres, API, k3s e Argo CD. Baixar o request é opcional, por `kubectl patch`, citado no README.

### D7. Rollback

- **Fixar uma tag (padrão)**:
  1. Restringir o CR: `kubectl -n argocd patch imageupdater educare --type json -p '[{"op":"replace","path":"/spec/applicationRefs/0/images/0/commonUpdateSettings/allowTags","value":"regexp:^v0[.]2[.]0$"}]'` (colchetes em vez de `\\.`, para o mesmo comando valer no fish e no bash).
  2. O updater leva a Application a essa tag no ciclo seguinte. Para não esperar, rodar `argocd app set educare --kustomize-image ghcr.io/manuelaalecio/educare-backend:v0.2.0`.
  3. Para soltar, reaplicar `deploy/argocd/image-updater.yaml`. Nada disso exige commit na `main`.
- **Pelo histórico do Argo CD**: `argocd app rollback` não funciona com auto-sync ligado. O procedimento é:
  1. `argocd app set educare --sync-policy none`;
  2. `argocd app history educare`;
  3. `argocd app rollback educare <id>`.
  - O README avisa que, ao religar o auto-sync, o updater volta à maior `v*` se a tag não tiver sido fixada antes no CR.
- **Corrigir para frente** (preferido quando possível): publicar `vX.Y.Z+1`.

### D8. Acesso e operação

- **Argo CD**: `ssh -L 8081:localhost:8081 ubuntu@<IP>` + `kubectl -n argocd port-forward svc/argocd-server 8081:443` na VM. A UI fica em `https://localhost:8081`.
- **kube-api**: `ssh -N -L 6443:127.0.0.1:6443 ubuntu@<IP>`, com o kubeconfig copiado de `/etc/rancher/k3s/k3s.yaml` (já aponta para `127.0.0.1:6443`).
- **Portas**: na security list da VCN e no iptables da VM, só 22 e 80 (e 443 quando houver HTTPS) abertos. 6443, 8472/udp e as portas do Argo CD nunca são abertas.
- **Secret** (antes do primeiro sync): `kubectl create namespace educare` e `kubectl -n educare create secret generic educare-env --from-literal=DB_PASSWORD=… --from-literal=EDUCARE_JWT_SECRET="$(openssl rand -base64 48)" …`. Os comandos no README são compatíveis com o fish (substituição de comando com `(...)`, sem sintaxe exclusiva do bash).

### D9. Remoção e documentação

- **Removidos**: `deploy.yml`, `deploy/compose.yaml`, `deploy/Caddyfile` e `deploy/deploy.sh`.
- **README**: a seção "Deploy em produção" é reescrita com os cinco procedimentos do RF10, mais a instalação do Image Updater. O passo do Secret cita o sintoma `CreateContainerConfigError`.
- **`CLAUDE.md`**:
  - "Deploy de produção em `deploy/`" passa a descrever `deploy/k8s` + `deploy/argocd`, o Secret `educare-env` e a regra "variável nova exigida pelo `prod` entra no Deployment (`secretKeyRef`) e no Secret do cluster antes do merge".
  - "Workflows em repositório público" perde as regras de SSH e `environment: production`, e ganha "tag `v*` publica sem label, só se o commit estiver na `main`".
  - As menções a "`prod` ativado pelo `docker-compose.yaml`" passam a citar também o Deployment.
- **`openspec/config.yaml`**: os trechos "Entrega" e "Actuator … health check do deploy" são atualizados. O arquivo tem uma alteração local não commitada da change `add-classroom-crud`, que precisa ser preservada.
- **`Dockerfile`**: o comentário do topo passa a dizer `linux/arm64` e k3s.

## Risks / Trade-offs

- **[Perda da VM = perda dos dados]**: o `local-path` é disco local, com `reclaimPolicy: Delete`. → Backup é tarefa separada e pré-requisito antes de dados reais (proposal, Non-goals); o README destaca isso. Excluir o PVC à mão apaga o banco, e o README avisa.
- **[Sem HTTPS]**: o Caddy dava HTTPS por sslip.io, e o Ingress sem host é só HTTP. O BFF da Vercel chamaria a API em texto claro, com o JWT. → Até a change de HTTPS, o frontend de produção não deve apontar para o cluster com dados reais. O Ingress já fica preparado para `host`/`tls`. Combina com o pré-requisito de backup: o cluster ainda não recebe dados reais.
- **[Override pseudo-persistente do Image Updater]**: recriar a Application volta por até ~2 min à tag de bootstrap. → Aceito e documentado; a tag de bootstrap é uma versão válida e compatível com o schema (as migrations são só aditivas).
- **[Rolling update com Flyway]**: o pod novo migra o banco enquanto o antigo ainda serve. → As migrations precisam continuar compatíveis com a versão anterior (expand/contract), regra já implícita em "migrations aplicadas nunca são editadas". Registrar isso no CLAUDE.md.
- **[iptables da imagem Ubuntu da Oracle]**: a regra `FORWARD … REJECT` pode bloquear o tráfego de pods/ServiceLB. → O cluster já está instalado e funcionando. O README cita a verificação (`curl http://<IP>/actuator/health`) e a liberação de 80 no INPUT, sem mexer em FORWARD por padrão.
- **[GHCR anônimo]**: não há limite publicado para pulls e listagens anônimas. → Com um app a cada 2 min o volume é desprezível. Se o pacote virar privado, é preciso um Secret de pull e credencial no updater (RN03).
- **[`latest` em tag antiga]**: publicar uma tag `v*` num commit antigo da `main` move o `latest` para trás. → O `latest` não é usado pelo cluster (só `v*`), e o comportamento fica documentado.
- **[`runAsNonRoot` ausente]**: o pod depende do `USER` da imagem. → Mitigado por `allowPrivilegeEscalation: false` e `drop: ALL`; UID numérico na imagem fica para depois.

## Migration Plan

1. Mergear o PR na `main`. A CI não publica a imagem sem o label `build`; os manifests são validados.
2. `git tag -a v0.2.0` num commit da `main` + push. A CI publica `v0.2.0`, `<sha7>` e `latest` só arm64. Conferir com `docker buildx imagetools inspect`.
3. No cluster, pelo túnel: criar o namespace `educare` e o Secret `educare-env`, instalar o Image Updater v1.4.0 e aplicar `deploy/argocd/application.yaml` e `image-updater.yaml`.
4. Validar:
   - Application `Synced/Healthy`;
   - `curl http://<IP>/actuator/health/readiness` responde `UP`;
   - login do admin;
   - `kubectl -n educare get svc` sem NodePort/LoadBalancer.
5. Publicar `v0.2.1` e conferir que chega sozinha. Ensaiar o rollback fixando `v0.2.0`.
6. **Checklist de limpeza** (subtarefa 4, manual):
   - excluir o environment `production` e os secrets `SSH_*` do GitHub;
   - remover regras antigas da security list (443 se não houver HTTPS, IPs das VMs excluídas);
   - revisar a proteção da `main` (opcional: exigir o check `manifests`).

**Rollback da migração**: não há ambiente antigo para onde voltar (as VMs foram excluídas). Se o cluster falhar, a correção é para frente; o código da aplicação muda só na configuração do Actuator, que é retrocompatível.
