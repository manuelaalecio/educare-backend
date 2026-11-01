# Tasks

## 1. cert-manager e ClusterIssuers

- [x] 1.1 Escolher a versão do cert-manager: a última estável `v1.x` em <https://github.com/cert-manager/cert-manager/releases>. Conferir que as imagens publicadas têm `linux/arm64` (por exemplo, `docker buildx imagetools inspect quay.io/jetstack/cert-manager-controller:<versão>`). Confirmar com a Manuela qual e-mail vai na conta ACME (design D7). Verificar anotando a versão e o e-mail escolhidos nesta task antes de seguir.

  Escolhidos: cert-manager **v1.21.3** (maior estável em 2026-11-01; `controller`, `webhook`, `cainjector` e `acmesolver` com `linux/arm64/v8`; Deployments `cert-manager`, `cert-manager-webhook` e `cert-manager-cainjector`). E-mail ACME: `manuelaalecio.dev@gmail.com`, confirmado pela Manuela.
- [x] 1.2 Criar `deploy/cert-manager/cluster-issuers.yaml` com os `ClusterIssuer` `letsencrypt-staging` e `letsencrypt`, como no design D2:
  - ACME HTTP-01 com `ingressClassName: traefik`;
  - `privateKeySecretRef` próprio em cada um;
  - comentário no topo dizendo que o arquivo é aplicado à mão, fora da Application.

  Verificar com `kubeconform -strict` usando o CRDs-catalog (`cert-manager.io/clusterissuer_v1`), localmente ou pela imagem `ghcr.io/yannh/kubeconform`.
- [x] 1.3 No job `manifests` de `.github/workflows/ci.yml`, validar `deploy/cert-manager/` com kubeconform e o CRDs-catalog (design D6). Se o schema faltar no catálogo, usar `-skip ClusterIssuer` com um comentário explicando o motivo. Manter o id do job e passar valores só por `env:`. Verificar com `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest` sem erros e com o passo novo verde no PR (grupo 4).
- [x] 1.4 No README, seção "Deploy em produção › Configuração inicial":
  - incluir um passo "Instalar o cert-manager e os ClusterIssuers": `kubectl apply -f` do manifesto oficial na versão da 1.1, `kubectl -n cert-manager rollout status` dos três Deployments, `kubectl apply -f deploy/cert-manager/cluster-issuers.yaml` e `kubectl get clusterissuer`, esperando os dois `Ready=True`;
  - incluir `deploy/cert-manager/cluster-issuers.yaml` na tabela "Arquivos de produção";
  - registrar que, como a Application, o arquivo é reaplicado à mão quando muda.

  Verificar com `bash -n` e `fish -n` num arquivo do scratchpad com os comandos do passo, sem erro de sintaxe.

## 2. Ingress com TLS e redirect

- [x] 2.1 Criar `deploy/k8s/middleware-redirect-https.yaml` com o `Middleware` `redirect-https` (`traefik.io/v1alpha1`, `redirectScheme`, `scheme: https`, `permanent: true`) e incluí-lo no `kustomization.yaml`. Verificar que `kubectl kustomize deploy/k8s` mostra o Middleware com `namespace: educare`.
- [x] 2.2 Alterar `deploy/k8s/ingress.yaml` (`educare-backend`), como no design D3:
  - `host: 136-248-80-75.sslip.io`;
  - `tls` com o Secret `educare-backend-tls`;
  - anotações `cert-manager.io/cluster-issuer: letsencrypt-staging` e `traefik.ingress.kubernetes.io/router.entrypoints: websecure`;
  - remover os comentários de "change futura" e atualizar o comentário do topo.

  Criar `deploy/k8s/ingress-http.yaml` (`educare-backend-http`):
  - mesmo host e path `/` (Prefix), com o backend `educare-backend:8080`;
  - anotações `router.entrypoints: web` e `router.middlewares: educare-redirect-https@kubernetescrd`;
  - sem `tls`;
  - um comentário explicando por que são dois Ingresses (o router com TLS não atende HTTP) e por que o solver do HTTP-01 não é redirecionado.

  Incluir o arquivo no `kustomization.yaml`. Verificar com `kubectl kustomize deploy/k8s`: os dois Ingresses aparecem no namespace `educare` e nenhum deles contém o Postgres.
- [x] 2.3 No passo "Validate deploy/k8s" do job `manifests`, acrescentar `-schema-location` do CRDs-catalog para validar o `Middleware` (design D6). Se o schema faltar no catálogo, usar `-skip Middleware` com um comentário explicando o motivo. Verificar com `kubectl kustomize deploy/k8s | kubeconform -strict -summary -schema-location default -schema-location <catálogo>` localmente sem erros e com o actionlint sem erros.
- [x] 2.4 Atualizar o README, seção "Deploy em produção":
  - trocar o aviso "Só HTTP" pelo estado novo: a API responde em `https://136-248-80-75.sslip.io`, e o HTTP redireciona;
  - registrar que o IP puro passa a dar `404`;
  - repetir que dados reais só depois do backup;
  - atualizar as portas abertas para 22, 80 e 443, explicando que a 80 é usada pelo HTTP-01 e pelo redirect;
  - atualizar a linha de `deploy/k8s/` na tabela de arquivos (Ingress com host, TLS e redirect);
  - trocar os `curl http://<ip-publico>/...` de verificação pelo host sslip.io;
  - novo trecho "HTTPS": como conferir o Certificate (`kubectl -n educare get certificate`, `kubectl -n educare describe certificate educare-backend-tls`, `kubectl -n educare describe challenge`), a troca de staging para produção (D5, incluindo apagar o Secret se não houver reemissão) e o que muda se o IP ou o domínio mudar;
  - na seção "Rollback", novo item "Voltar para só HTTP", com os passos do Migration Plan do design.

  Verificar com `bash -n` e `fish -n` dos comandos novos num arquivo do scratchpad, e conferindo que o README não diz mais que só há HTTP.
- [x] 2.5 Atualizar o `CLAUDE.md`:
  - a Armadilha "Deploy de produção em `deploy/`" passa a citar o TLS da Let's Encrypt no Ingress (host sslip.io, redirect por um segundo Ingress no `web`);
  - o cert-manager e os ClusterIssuers de `deploy/cert-manager/` são instalados e aplicados à mão, fora da Application e de qualquer app-of-apps;
  - a frase do job `manifests` passa a incluir `deploy/cert-manager`.

  No `openspec/config.yaml`, o trecho "Entrega" passa a citar o Ingress Traefik com TLS da Let's Encrypt (cert-manager instalado à mão), e a linha do job `manifests` no resumo de Testes também é atualizada. Verificar com `git diff CLAUDE.md openspec/config.yaml` mostrando só essas alterações e com `openspec validate enable-https-with-cert-manager --strict` sem erros.

## 3. Testes

- [x] 3.1 Testes de cenário: não se aplicam. A change não tem specs (design D1), e nenhum código de produção muda. Verificar com `git status --short -- src` vazio (a `dev` já tem commits de outras changes à frente da `main`) e `openspec validate enable-https-with-cert-manager --strict` sem erros.

## 4. Validação no cluster (manual, pela Manuela)

- [x] 4.1 Pelo túnel, instalar o cert-manager e aplicar os ClusterIssuers conforme o README (task 1.4). Verificar:
  - `kubectl -n cert-manager get pods` com os três pods `Running`;
  - `kubectl get clusterissuer` com `letsencrypt-staging` e `letsencrypt` em `Ready=True`;
  - `kubectl top pod -n cert-manager` (se o metrics-server estiver ativo) com o consumo dentro de ~200 MB (RNF02).
- [x] 4.2 PR 1 `dev` → `main` com os grupos 1 a 3 e a task 5.1 já rodada localmente (issuer `letsencrypt-staging`). Verificar que `build` e `manifests` passam verdes. Mergear sem o label `build`; não é preciso imagem nova. Esperar o sync do Argo CD.

  Feito no [PR 11](https://github.com/manuelaalecio/educare-backend/pull/11): `build` e `manifests` verdes. O merge foi com o label `build`, porque o PR levou junto a troca de roles; não muda a versão em produção, que só sobe com uma tag `vX.Y.Z`.
- [x] 4.3 (CA04, CA02) Com o staging:
  - `kubectl -n educare get certificate` mostra `educare-backend-tls` em `Ready=True`;
  - `openssl s_client -connect 136-248-80-75.sslip.io:443 -servername 136-248-80-75.sslip.io </dev/null | openssl x509 -noout -issuer` mostra um emissor com `(STAGING)`;
  - `curl -k https://136-248-80-75.sslip.io/actuator/health` responde `200`;
  - `curl -I http://136-248-80-75.sslip.io/actuator/health` responde `301` ou `308`, com `Location: https://136-248-80-75.sslip.io/actuator/health`.

  Se o Certificate não ficar `Ready`, investigar com `kubectl -n educare describe challenge` antes de qualquer pedido à produção.

  Verificado em 2026-11-01: emissor `C=US, O=Let's Encrypt, CN=(STAGING) Ersatz Emmer YR2`; `curl -k` responde `UP`; o HTTP responde `308 Permanent Redirect` com `Location: https://136-248-80-75.sslip.io/actuator/health`.
- [x] 4.4 (CA08) Ensaiar o rollback no staging:
  - desligar o sync automático (`argocd app set educare --sync-policy none`);
  - aplicar o `ingress.yaml` anterior à change (`git show <commit-anterior>:deploy/k8s/ingress.yaml | kubectl -n educare apply -f -`);
  - apagar o Ingress `educare-backend-http` e o Middleware;
  - conferir que `curl http://136.248.80.75/actuator/health` responde `200`;
  - religar o sync (`argocd app set educare --sync-policy automated --auto-prune --self-heal`) e conferir que o estado da 4.3 volta, sem nova emissão (o Secret foi mantido).

  Verificar também que os passos do README para "Voltar para só HTTP" correspondem ao ensaio.

  Verificado em 2026-11-01. O sync automático foi desligado e religado com `kubectl -n argocd patch application educare`, sem o CLI `argocd`. Pelo IP puro, `curl http://136.248.80.75/actuator/health` respondeu `UP`. Com o sync religado, o Argo CD recriou o `educare-backend-http` e o Middleware, e voltaram o `UP` em HTTPS e o `308`, sem emissão nova.
- [ ] 4.5 PR 2 `dev` → `main` trocando a anotação para `cert-manager.io/cluster-issuer: letsencrypt`. Verificar `build` e `manifests` verdes, mergear sem o label e esperar o sync.
- [ ] 4.6 (CA01, CA02, CA03) Com a produção:
  - `curl https://136-248-80-75.sslip.io/actuator/health`, sem `-k`, responde `200`;
  - o `openssl s_client` da 4.3 mostra um emissor da Let's Encrypt sem `(STAGING)`, e não o TRAEFIK DEFAULT CERT;
  - o redirect da 4.3 continua valendo;
  - `kubectl -n educare describe certificate educare-backend-tls` mostra `Ready=True` e um `Renewal Time` antes do `Not After`.

  Se não houver reemissão depois da troca, apagar o Secret `educare-backend-tls` conforme o README.
- [ ] 4.7 (CA07, RNF01) De fora da rede, rodar `nmap -Pn 136.248.80.75`. Verificar que só 22, 80 e 443 respondem e que 5432, 6443 e a porta do Argo CD não respondem. Conferir também que `curl http://136.248.80.75/` responde `404` do Traefik, como esperado.
- [ ] 4.8 Avisar a subtarefa do front ([EDUCARE_API_URL em HTTPS na Vercel e login de ponta a ponta](https://app.clickup.com/t/1dfh60ph131)) de que a API está em `https://136-248-80-75.sslip.io`. Verificar que o `EDUCARE_CORS_ALLOWED_ORIGINS` do Secret `educare-env` contém `https://educare-frontend-ruby.vercel.app`, com `kubectl -n educare get secret educare-env -o jsonpath='{.data.EDUCARE_CORS_ALLOWED_ORIGINS}' | base64 -d`.

## 5. Verificação final

- [ ] 5.1 Rodar `./gradlew build` localmente, com o Docker ativo. Verificar que termina verde, com o contrato da API inalterado e o `jacocoTestCoverageVerification` passando (mínimo de 90% de linhas e de branches), e que os checks `build` e `manifests` dos PRs das tasks 4.2 e 4.5 estavam verdes no último commit antes do merge.
