# Tasks

## 1. Probes do Actuator (spec `security`)

- [x] 1.1 Adicionar `management.endpoint.health.probes.enabled: true` ao bloco `management` do `src/main/resources/application.yaml` (design D2), mantendo `exposure.include: health`, `show-details: never` e `show-components: never`, sem grupo `readiness` com `db`. Na mesma task, adicionar ao `shared/security/SecurityStartupScenarioTest` o teste de cenário `@DisplayName("Scenario: Probes disponíveis no profile prod")`:
  - sobe a aplicação com o profile `prod` numa porta real, com as variáveis obrigatórias válidas e sem `KUBERNETES_SERVICE_HOST`, reaproveitando o helper de start do `prod`;
  - `GET /actuator/health/liveness` e `GET /actuator/health/readiness` sem token respondem `200` com o corpo só `{"status":"UP"}`.

  Verificar com `./gradlew test --tests '*SecurityStartupScenarioTest'` verde. Conferir que o teste falha com `probes.enabled: false` (mudança temporária, revertida em seguida).
- [x] 1.2 Criar o teste de cenário `shared/security/ReadinessScenarioTest`, com `@DisplayName("Scenario: Readiness recusa tráfego sem derrubar a liveness")`:
  - usa `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` + `@DirtiesContext` e é estruturado em given/when/then;
  - publica `AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC)`;
  - verifica que `GET /actuator/health/readiness` sem token responde `503` com o corpo só `{"status":"OUT_OF_SERVICE"}`, e que `GET /actuator/health/liveness` responde `200` com o corpo só `{"status":"UP"}`.

  Verificar com `./gradlew test --tests '*ReadinessScenarioTest'` verde.
- [x] 1.3 Testes de cenário da change: conferir que cada Scenario do requisito modificado "Actuator restrito ao health, sem detalhes" tem um teste com `@DisplayName` correspondente. São os seis que já existem no `SecurityScenarioTest` e os dois novos das tasks 1.1 e 1.2. Verificar com `grep -rn 'Scenario: ' src/test/java/com/manuelaalecio/educare_backend/shared/security` listando os oito nomes exatos da spec, e com `./gradlew test --tests '*.shared.security.*'` verde.

## 2. Manifests Kustomize em `deploy/k8s/`

- [x] 2.1 Criar `deploy/k8s/kustomization.yaml` e `deploy/k8s/namespace.yaml` (design D4):
  - `namespace: educare`;
  - label comum `app.kubernetes.io/part-of: educare`;
  - `resources` listando todos os arquivos dos grupos 2.x;
  - `images: [{name: ghcr.io/manuelaalecio/educare-backend, newTag: v0.2.0}]`, com um comentário explicando que é a tag de bootstrap e que a tag em produção vem do Image Updater.

  Verificar com `kubectl kustomize deploy/k8s` sem erro.
- [x] 2.2 Criar `deploy/k8s/postgres/statefulset.yaml` e `deploy/k8s/postgres/service.yaml` (D4):
  - StatefulSet `postgres` com 1 réplica, `postgres:16`, `POSTGRES_DB`/`POSTGRES_USER=app`, `POSTGRES_PASSWORD` de `educare-env`/`DB_PASSWORD` e `PGDATA=/var/lib/postgresql/data/pgdata`;
  - readiness e liveness por `pg_isready -U app -d app -h 127.0.0.1`;
  - resources: request 256Mi, limit 1Gi;
  - `volumeClaimTemplates` `data` de 10Gi, `ReadWriteOnce`, `local-path`, montado em `/var/lib/postgresql/data`;
  - Service `postgres` headless (`clusterIP: None`) na 5432.

  Verificar com `kubectl kustomize deploy/k8s | grep -nE 'NodePort|LoadBalancer|nodePort|hostPort'` sem resultado.
- [x] 2.3 Criar `deploy/k8s/api/deployment.yaml` e `deploy/k8s/api/service.yaml` (D4):
  - Deployment `educare-backend` com 1 réplica, `RollingUpdate` com `maxSurge: 1` e `maxUnavailable: 0`, `revisionHistoryLimit: 3`.
  - Env:
    - `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres.educare.svc:5432/app` e `SPRING_DATASOURCE_USERNAME=app`;
    - de `educare-env`, por `secretKeyRef`: `SPRING_DATASOURCE_PASSWORD` (chave `DB_PASSWORD`), `EDUCARE_JWT_SECRET`, `EDUCARE_ADMIN_PASSWORD` e `EDUCARE_CORS_ALLOWED_ORIGINS`.
  - `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`.
  - Resources: request 512Mi de memória e 250m de CPU, limit 1Gi de memória, sem limit de CPU.
  - Probes:
    - startup na liveness, com período de 5s e `failureThreshold: 36`;
    - liveness e readiness com período de 10s, timeout de 3s e `failureThreshold: 3`.
  - `preStop.sleep.seconds: 10` e `terminationGracePeriodSeconds: 45`.
  - `automountServiceAccountToken: false`; `securityContext` com `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]` e `seccompProfile: RuntimeDefault`.
  - Service ClusterIP `educare-backend` na 8080.

  Verificar com `kubectl kustomize deploy/k8s`:
  - a saída contém a imagem `ghcr.io/manuelaalecio/educare-backend:v0.2.0`;
  - `grep -nE 'imagePullSecrets|Xmx|stringData|^data:'` não acha nada;
  - `grep -c 'name: educare-env'` dá 5 (4 na API e 1 no Postgres).
- [x] 2.4 Criar `deploy/k8s/ingress.yaml` (D4): Ingress `educare-backend` com `ingressClassName: traefik`, regra sem `host` e `path: /` `Prefix` para `educare-backend:8080`. Deixar comentados `host`, `tls` e a anotação `cert-manager.io/cluster-issuer`. Verificar com `kubectl kustomize deploy/k8s` mostrando um único Ingress, cujo backend é `educare-backend`, sem nenhuma referência a `postgres`.
- [x] 2.5 Validar o conjunto contra os critérios CA02 e CA03:
  - rodar `kubectl kustomize deploy/k8s | kubeconform -strict -summary -kubernetes-version 1.36.0`, com o kubeconform v0.8.0 baixado no scratchpad e conferido pelo `CHECKSUMS`: 0 erros;
  - confirmar na saída Namespace, StatefulSet com `volumeClaimTemplates`, os dois Services, Deployment e Ingress, todos com `namespace: educare` (exceto o próprio Namespace).

## 3. Argo CD em `deploy/argocd/`

- [x] 3.1 Criar `deploy/argocd/application.yaml` (design D5):
  - `Application` `educare` no namespace `argocd`, `project: default`;
  - `repoURL: https://github.com/manuelaalecio/educare-backend.git`, `targetRevision: main`, `path: deploy/k8s`;
  - destino `https://kubernetes.default.svc`/`educare`;
  - `syncPolicy.automated` com `prune: true` e `selfHeal: true`;
  - **sem** `spec.source.kustomize`, com um comentário explicando o motivo (D5).

  Verificar com `grep -nE 'prune: true|selfHeal: true|path: deploy/k8s'` achando as três linhas, `grep -n 'kustomize' deploy/argocd/application.yaml` só no comentário, e kubeconform com o schema `argoproj.io/application_v1alpha1` do `datreeio/CRDs-catalog` sem erros.
- [x] 3.2 Criar `deploy/argocd/image-updater.yaml` com o `ImageUpdater` do design D6:
  - `namePattern: educare`, write-back `argocd`;
  - alias `api`, estratégia `semver`, `allowTags: 'regexp:^v\d+\.\d+\.\d+$'` e `manifestTargets.kustomize.name` igual ao `name` do `images:` do `kustomization.yaml`.

  Antes de escrever, conferir na documentação e no `install.yaml` da versão que vai ser instalada (v1.4.0 ou a estável mais recente na hora, ajustando o design se mudar) o formato do CRD, que mudou entre versões (anotações × CRD; `spec.namespace` removido na v1.2). Verificar extraindo o CRD do `install.yaml` dessa versão e validando o arquivo contra o `openAPIV3Schema` dele (por exemplo, schema JSON gerado no scratchpad e passado ao kubeconform com `-schema-location`) sem erros.
- [x] 3.3 Decidir como o CI valida `deploy/argocd/`: se o `datreeio/CRDs-catalog` tiver o schema `argocd-image-updater.argoproj.io/imageupdater_v1alpha1.json`, usá-lo; senão, `-skip ImageUpdater` só para esse arquivo (nunca `-ignore-missing-schemas`). Registrar a escolha no design D3. Verificar rodando localmente o comando exato que vai para o CI, sem erros.

## 4. CI: imagem arm64, tag de versão e validação dos manifests

- [x] 4.1 Alterar `.github/workflows/ci.yml` conforme o design D3:
  - gatilho `push` também com `tags: ['v*']`;
  - `publish-gate` em todo `push`, com `fetch-depth: 0` só quando for tag:
    - em `refs/heads/main`, a regra atual do label `build`, sem mudança;
    - em `refs/tags/v*`, `publish=true` só se a tag casar com `^v[0-9]+\.[0-9]+\.[0-9]+$` e o commit for ancestral de `origin/main`, e senão `publish=false` com o motivo no `$GITHUB_STEP_SUMMARY`;
  - job `build` com o mesmo id e os mesmos passos.

  Verificar com o actionlint (`docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest`) sem erros, e com `grep -nE 'pull_request_target|set -x|StrictHostKeyChecking'` e `grep -nE 'run:.*\$\{\{'` sem resultado nos workflows.
- [x] 4.2 No job `image`:
  - `runs-on: ubuntu-24.04-arm`, sem QEMU, com `platforms: linux/arm64`;
  - o passo `meta` acrescenta `$image:$GITHUB_REF_NAME` quando `GITHUB_REF` começa com `refs/tags/v` (recebido por `env:`) e registra todas as tags no resumo;
  - `needs: [build, publish-gate, manifests]`, mantendo `if: needs.publish-gate.outputs.publish == 'true'` e `packages: write` só nesse job.

  Conferir nas páginas de releases que as actions usadas continuam na major mais recente e anotar no commit. Verificar com o actionlint sem erros.
- [x] 4.3 Criar o job `manifests` (`ubuntu-latest`, `contents: read`, timeout de 5 min):
  - baixa o kubeconform v0.8.0 e confere o `CHECKSUMS` com `sha256sum -c`;
  - roda `kubectl kustomize deploy/k8s | kubeconform -strict -summary -kubernetes-version 1.36.0 -schema-location default` e valida `deploy/argocd/*.yaml` com o comando decidido na 3.3;
  - variáveis só por `env:`.

  Verificar com o actionlint sem erros, e quebrando de propósito um campo num manifest local (ex.: `replicas: "um"`): o mesmo comando falha. Desfazer em seguida.
- [x] 4.4 Atualizar o comentário do topo do `Dockerfile`: a imagem de produção é `linux/arm64` (nó k3s Ampere da Oracle), construída em runner arm nativo pela CI, pública e sem segredos. Verificar com `docker buildx build --platform linux/arm64 -t educare-backend:arm64-local --load .` concluindo (local, com QEMU, se o host for x86_64) e `docker image inspect educare-backend:arm64-local --format '{{.Architecture}}'` mostrando `arm64`.

## 5. Remoção do deploy por SSH e documentação

- [x] 5.1 Remover `.github/workflows/deploy.yml`, `deploy/compose.yaml`, `deploy/Caddyfile` e `deploy/deploy.sh`. Verificar (CA08):
  - `git ls-files .github/workflows deploy` lista só o `ci.yml` e os arquivos de `deploy/k8s` e `deploy/argocd`;
  - `grep -rnE 'DB_HOST|deploy\.sh|deploy\.env|Caddyfile|caddy|IMAGE_TAG|StrictHostKeyChecking|SSH_PRIVATE_KEY' --exclude-dir=archive --exclude-dir=build --exclude-dir=.gradle .` não acha nada fora desta change.
- [x] 5.2 Reescrever a seção "Deploy em produção" do README (design D7, D8 e D9):
  - **como funciona**: PR → testes; merge com label → imagem; tag `v*` → imagem de versão → Image Updater → Argo CD;
  - **pré-requisitos já feitos**: k3s e Argo CD;
  - os cinco procedimentos do RF10 e a instalação do Image Updater, com comandos copiáveis compatíveis com o fish:
    1. abrir os túneis SSH do kube-api e do Argo CD;
    2. criar o namespace e o Secret `educare-env`, citando o sintoma `CreateContainerConfigError` quando ele falta ou falta uma chave;
    3. instalar o Image Updater fixado na versão;
    4. registrar a Application e o ImageUpdater com `kubectl apply`;
    5. publicar uma versão com `git tag -a vX.Y.Z` e conferir;
    6. fazer rollback fixando a tag no CR e pelo histórico do Argo CD (desligando o auto-sync);
  - avisos: sem backup ainda (perder a VM é perder os dados); só HTTP até a change de HTTPS; não excluir o PVC; portas que nunca abrem (6443, Argo CD).

  Verificar com `fish -n` num arquivo do scratchpad com os comandos do README sem erro de sintaxe, e conferindo que nenhuma seção cita VM de banco, SSH no pipeline ou Caddy.
- [x] 5.3 Atualizar o `CLAUDE.md`:
  - as Armadilhas "Deploy de produção em `deploy/`" e "Workflows em repositório público" (D9);
  - a menção do profile `prod` ativado pelo `docker-compose.yaml` passa a citar também o Deployment;
  - nova nota: migrations compatíveis com a versão anterior por causa do rolling update.

  Atualizar no `openspec/config.yaml` os trechos "Entrega" (imagem `linux/arm64`, tag `v*` publica, Argo CD + Image Updater no k3s, Postgres no cluster), "Actuator … health check do deploy" (probes do Kubernetes) e a linha de CI do resumo de Testes, **preservando** a alteração local não commitada da change `add-classroom-crud` (glossário "Turma -> Classroom"). Verificar com `git diff openspec/config.yaml` mostrando as duas alterações, e com `openspec validate migrate-deploy-to-gitops --strict` sem erros.

## 6. Validação no GitHub e no cluster (manual, pela Manuela)

- [ ] 6.1 Abrir o PR `dev` → `main`. Verificar que `build` e `manifests` rodam verdes e que `publish-gate`/`image` não publicam nada no PR. Mergear sem o label `build` e verificar que nenhuma imagem nova aparece.
- [ ] 6.2 (CA01) Criar a tag `v0.2.0` no head da `main` e fazer push. Verificar:
  - a CI publica `v0.2.0`, `<sha7>` e `latest`;
  - `docker buildx imagetools inspect ghcr.io/manuelaalecio/educare-backend:v0.2.0` (e das outras duas) lista só `linux/arm64`;
  - uma tag `v0.0.0` descartável num commit fora da `main` não publica, com o motivo no resumo. Apagar a tag em seguida.
- [ ] 6.3 (CA05) Pelo túnel, sem criar o Secret, aplicar a Application num teste rápido. Verificar que os pods ficam em `CreateContainerConfigError` e que nada sobe com valor padrão. Depois criar o namespace e o Secret `educare-env` conforme o README.
- [ ] 6.4 Instalar o Image Updater e aplicar `deploy/argocd/application.yaml` e `image-updater.yaml` conforme o README. Verificar:
  - a Application fica `Synced`/`Healthy`;
  - `curl http://<IP>/actuator/health/readiness` responde `{"status":"UP"}`;
  - o login do admin com o `EDUCARE_ADMIN_PASSWORD` funciona;
  - `kubectl -n educare get svc,ingress` não tem NodePort nem LoadBalancer, e o Postgres não aparece no Ingress (CA03);
  - excluir o pod do Postgres mantém os dados (RNF04).
- [ ] 6.5 (CA07) Publicar `v0.2.1`. Verificar que, em até alguns minutos, a Application passa a usar `:v0.2.1` (`kubectl -n argocd get application educare -o jsonpath='{.spec.source.kustomize.images}'`) sem nenhum commit novo em nenhuma branch.
- [ ] 6.6 Rollback: fixar `v0.2.0` no CR conforme o README e verificar que o Deployment volta a `:v0.2.0`. Ensaiar também o rollback pelo histórico do Argo CD (auto-sync desligado e religado). Restaurar o CR.
- [ ] 6.7 Checklist de limpeza (subtarefa 4):
  - excluir o environment `production` e os secrets `SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY` e `SSH_KNOWN_HOSTS`;
  - revisar a security list da VCN (só 22 e 80);
  - opcional: exigir o check `manifests` na proteção da `main`.

  Verificar com `gh api repos/manuelaalecio/educare-backend/environments` sem `production`.

## 7. Verificação final

- [ ] 7.1 Rodar `./gradlew build` localmente, com o Docker ativo. Verificar que termina verde, incluindo o contrato da API inalterado e o `jacocoTestCoverageVerification` (mínimo de 90% de linhas e de branches), e que os checks `build` e `manifests` do PR da 6.1 estavam verdes no último commit antes do merge.
