# Design

## Context

A motivação e o escopo estão no `proposal.md`. O estado atual que molda a abordagem:

- **Cluster**:
  - k3s de nó único (arm64) com Traefik v3, exposto pelo ServiceLB nas portas 80 (entrypoint `web`) e 443 (entrypoint `websecure`) do nó.
  - O Argo CD sincroniza `deploy/k8s` da `main` na Application `educare` (projeto `default`, destino no namespace `educare`, `prune` e `selfHeal`).
  - O `kustomization.yaml` aplica `namespace: educare` a todos os recursos.
- **Ingress atual** (`deploy/k8s/ingress.yaml`): `educare-backend`, `ingressClassName: traefik`, sem host e sem anotação de entrypoint, ou seja, nos dois entrypoints. A rota `/` vai para o Service `educare-backend:8080`. Os blocos `host`, `tls` e `cert-manager.io/cluster-issuer` já estão comentados, à espera desta change.
- **Firewall**: a VCN e o iptables da VM liberam 22, 80 e 443. Na 443, o Traefik hoje serve o TRAEFIK DEFAULT CERT.
- **Instalação manual de componentes**: o Image Updater é instalado com `kubectl apply` de um manifesto oficial em versão fixa, e a Application e o ImageUpdater são aplicados à mão a partir de `deploy/argocd/`. O CLAUDE.md proíbe app-of-apps.
- **CI, job `manifests`**:
  - `deploy/k8s` passa por `kubectl kustomize | kubeconform -strict` só com os schemas padrão do Kubernetes. Hoje não há CRD ali.
  - `deploy/argocd` passa por kubeconform com o CRDs-catalog do datreeio, com `-skip ImageUpdater`.
- **Aplicação**: nada depende do esquema da requisição. Não há `forward-headers-strategy`, `requiresChannel` nem URL absoluta montada a partir da requisição. O `instance` do `ProblemDetail` é o caminho. A API continua em HTTP dentro do cluster, e o TLS termina no Traefik.

## Goals / Non-Goals

**Goals:**
- `https://136-248-80-75.sslip.io` com certificado da Let's Encrypt, renovado pelo cert-manager sem intervenção.
- HTTP para o host sempre redirecionado para HTTPS, sem quebrar o desafio HTTP-01, que precisa ser atendido em HTTP.
- O estado do cluster continua reproduzível a partir do repositório e dos passos manuais do README: nenhum segredo versionado, e nenhuma automação escreve no git.
- Rollback para "só HTTP" com um revert e um sync.

**Non-Goals:**
- Teste automatizado do TLS ou do redirect. Eles são verificados à mão no cluster (D1), e a CI só valida a sintaxe e os schemas dos manifests.
- Certificado wildcard ou desafio DNS-01. O sslip.io não deixa criar registros TXT, e o HTTP-01 basta para um único host.
- Gerenciar a configuração do Traefik do k3s (HelmChartConfig).

## Decisions

### D1. Change sem specs (`skip_specs: true`)

O critério é o mesmo do D1 das changes `add-ci-workflow` e `add-cd-pipeline`. TLS, certificado e redirect vivem na borda do cluster, e nenhum comportamento da aplicação muda. Um Scenario como "WHEN chamo `http://…/actuator/health` THEN recebo `308`" não teria teste de cenário `@SpringBootTest`, e todo Scenario do projeto precisa ter um.

A verificação fica em dois lugares:
- os critérios de aceite da tarefa (CA01 a CA04, CA07 e CA08) viram tasks manuais no cluster;
- o job `manifests` valida os arquivos (D6).

A DoD da tarefa ("change de HTTPS proposta e arquivada") é atendida por esta change, mesmo sem delta de spec.

**Alternativa rejeitada:** uma capability `delivery` ou `ingress` com Scenarios verificados à mão. Contraria a regra de que todo Scenario tem teste de cenário, e a spec só repetiria os manifests.

### D2. ClusterIssuers em `deploy/cert-manager/`, aplicados à mão

Esta decisão responde à pergunta em aberto da tarefa. Os dois `ClusterIssuer` (`cert-manager.io/v1`) ficam em `deploy/cert-manager/cluster-issuers.yaml` e são aplicados com `kubectl apply`, logo depois da instalação do cert-manager, como a Application e o ImageUpdater de `deploy/argocd/`:

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-staging        # e outro idêntico: letsencrypt, com o server de produção
spec:
  acme:
    server: https://acme-staging-v02.api.letsencrypt.org/directory   # produção: https://acme-v02.api.letsencrypt.org/directory
    email: <e-mail da mantenedora>
    privateKeySecretRef:
      name: letsencrypt-staging-account-key   # criado pelo cert-manager no namespace cert-manager, nunca versionado
    solvers:
      - http01:
          ingress:
            ingressClassName: traefik
```

Motivos para ficar fora da Application `educare`:
- **Escopo do recurso**: o `ClusterIssuer` é de cluster. O Kustomize não conhece o escopo de um CRD e aplicaria `namespace: educare` a ele, o que gera diff permanente no Argo CD. A Application é do namespace `educare`, e um recurso de cluster nela mistura responsabilidades.
- **Ordem de criação**: o CRD só existe depois da instalação manual do cert-manager. Na Application, um cluster recriado falharia o sync até o cert-manager ser instalado.
- **Ciclo de vida**: os issuers acompanham o cert-manager, não a API. Com `prune`, um engano no `kustomization.yaml` apagaria os issuers. A chave da conta ACME continua no Secret, mas a renovação para até o issuer voltar.

**Alternativa rejeitada:** colocar os issuers em `deploy/k8s/`. Exigiria resolver o namespace aplicado pelo Kustomize (um `kustomization` separado ou um patch) e criar dependência de ordem no sync.

**Alternativa rejeitada:** `Issuer`, que é de namespace, em `deploy/k8s/`. Continua dependendo do CRD no sync, e um domínio novo, em outro namespace, precisaria repetir o issuer.

### D3. Dois Ingresses: HTTPS no `websecure` e redirect no `web`

A tarefa sugere um único Ingress com `tls` e a anotação `router.middlewares`. O problema: no provider de Ingress do Traefik, um Ingress com `spec.tls` gera um router só para TLS. Requisições HTTP para o host não casam com esse router e recebem `404`, sem passar pelo Middleware. Por isso o design separa dois Ingresses:

| Ingress | Entrypoint | Host / path | Faz |
|---|---|---|---|
| `educare-backend` (o atual, alterado) | `websecure` | `136-248-80-75.sslip.io`, `/` (Prefix) | TLS com o Secret `educare-backend-tls`; anotação `cert-manager.io/cluster-issuer`; envia para `educare-backend:8080` |
| `educare-backend-http` (novo) | `web` | `136-248-80-75.sslip.io`, `/` (Prefix) | Middleware `educare-redirect-https@kubernetescrd` (devolve `308`); o backend é o mesmo Service, exigido pelo Ingress, mas a requisição nunca chega a ele |
| Solver do cert-manager (temporário) | os dois | o host, `/.well-known/acme-challenge/<token>` | responde o desafio HTTP-01 |

- Os entrypoints são fixados pela anotação `traefik.ingress.kubernetes.io/router.entrypoints` (`websecure` num Ingress, `web` no outro), para que o comportamento não dependa do padrão do Traefik.
- O Middleware `redirect-https` (`traefik.io/v1alpha1`, `redirectScheme`, `scheme: https`, `permanent: true`) fica em `deploy/k8s/middleware-redirect-https.yaml`. O Kustomize põe o Middleware no namespace `educare`, daí a referência `educare-redirect-https@kubernetescrd`.
- **HTTP-01**: o Ingress do solver não tem o Middleware. A regra dele (host + caminho do token) é mais longa que a do `educare-backend-http` (host + `/`), e o Traefik dá prioridade à regra mais longa. Por isso o desafio é respondido em HTTP, sem redirect. Isso é verificado no staging (CA04) antes de qualquer pedido à produção.
- O Ingress que recebe TLS continua sendo o `educare-backend`, o mesmo nome de hoje, e por isso o rollback é só um revert (Migration Plan).
- Resultado visível de fora (não há endpoint novo da aplicação):
  - `GET https://136-248-80-75.sslip.io/actuator/health` → `200`, com certificado da Let's Encrypt;
  - `GET http://136-248-80-75.sslip.io/<qualquer caminho>` → `308` (ou `301`), com `Location: https://136-248-80-75.sslip.io/<mesmo caminho>`;
  - `http://136.248.80.75/`, sem host → `404` do Traefik.

**Alternativa rejeitada:** redirect global no entrypoint `web` do Traefik (`HelmChartConfig` do k3s com `redirectTo`). Seria configuração do cluster fora do repositório e do Argo CD, valeria para todos os hosts e redirecionaria também o desafio HTTP-01. A Let's Encrypt segue o redirect, mas o self-check do cert-manager pode falhar.

**Alternativa rejeitada:** `IngressRoute` do Traefik. O ingress-shim do cert-manager só lê `Ingress`, e então seria preciso declarar o `Certificate` à mão, além de usar outro CRD.

**Alternativa rejeitada:** um Ingress só, sem `tls`, com `router.tls=true`. Esconde do Kubernetes e do cert-manager qual Secret serve o host, e o ingress-shim não cria o Certificate.

### D4. cert-manager pelo manifesto oficial, em versão fixa

`kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/<vX.Y.Z>/cert-manager.yaml`, com a versão anotada no README. É o mesmo padrão do Image Updater, sem Helm no cluster nem no repositório. As imagens são multi-arch, com `arm64`.

Custo no Free Tier: três Deployments (controller, webhook e cainjector), juntos uns 100 a 200 MB de RAM no nó de 12 GB. Não há componente pago. É a dependência nova desta change, e se justifica porque emitir e renovar certificados à mão (certbot na VM, Secret atualizado de tempos em tempos) não sobrevive ao vencimento de 90 dias.

**Alternativa rejeitada:** o ACME embutido do Traefik. O k3s não dá volume persistente para o `acme.json`, a configuração fica no `HelmChartConfig` fora do GitOps, e o certificado não aparece como recurso do Kubernetes para conferir (CA03).

**Alternativa rejeitada:** Helm. Seria uma ferramenta a mais só para este componente.

### D5. Staging primeiro, produção num segundo PR

Para respeitar os limites da Let's Encrypt (RNF04):
1. O primeiro PR entrega os Ingresses com `cert-manager.io/cluster-issuer: letsencrypt-staging`.
2. Depois do merge e do sync, confere-se que o Certificate `educare-backend-tls` está `Ready=True` e que o emissor do certificado servido contém `(STAGING)`. Também se confere o redirect (CA02) e que o desafio passou (CA04).
3. Só então um segundo PR troca a anotação para `letsencrypt`. O ingress-shim atualiza o `issuerRef` do Certificate, e o cert-manager reemite o certificado no mesmo Secret. Se não reemitir, apagar o Secret `educare-backend-tls` força a reemissão.

Os dois PRs só mexem em `deploy/k8s`, então o Argo CD sincroniza sem tag nem imagem nova (RNF03).

### D6. CI valida os CRDs novos

- **Passo `deploy/k8s`**: o kubeconform ganha `-schema-location "$CRD_SCHEMAS"` (o mesmo CRDs-catalog já usado em `deploy/argocd`) para validar o `Middleware` do `traefik.io/v1alpha1`.
- **`deploy/cert-manager/`**: validado com o mesmo catálogo (`cert-manager.io/clusterissuer_v1`).
- Se o catálogo não tiver o schema de algum desses kinds, ou tiver um schema desatualizado, o kind entra em `-skip` com um comentário explicando o motivo, como já acontece com o `ImageUpdater`.
- O id do job `manifests` não muda. A validação de workflows roda com o actionlint, como está no CLAUDE.md.

### D7. E-mail da conta ACME versionado

O e-mail da mantenedora fica no `ClusterIssuer`, num repositório público, para receber os avisos de vencimento e de problemas da Let's Encrypt. A tarefa aceita isso (RNF05): o e-mail não é segredo. A chave privada da conta ACME é gerada pelo cert-manager no Secret indicado em `privateKeySecretRef`, no namespace `cert-manager`, e nunca entra no repositório.

## Risks / Trade-offs

- **[O sslip.io fica fora do ar ou muda de comportamento]** → a emissão e a renovação falham e, com o DNS fora, o front não resolve a API. Mitigação: o certificado vale 90 dias e é renovado com 30 de folga; se a emissão falhar, há o rollback do README. Um domínio próprio fica para uma tarefa futura (só muda o host).
- **[O IP público muda (VM recriada)]** → o host `136-248-80-75` deixa de apontar para o cluster. Mitigação: mudam o host nos dois Ingresses, a `EDUCARE_API_URL` do front e, se for o caso, o CORS. Isso fica documentado no README.
- **[Limite de emissão da Let's Encrypt]** → os pedidos repetidos à produção ficam bloqueados por dias. Mitigação: staging primeiro (D5), e a produção só depois de o staging emitir.
- **[O redirect captura o desafio HTTP-01]** → o Certificate não chega a `Ready`. Mitigação: o solver tem regra mais longa e não usa o Middleware (D3). Isso é verificado no staging; se falhar, `kubectl describe challenge -n educare` mostra o motivo antes de qualquer pedido à produção.
- **[O acesso pelo IP puro para de funcionar]** → quem usa `http://136.248.80.75/` recebe `404`. Mitigação: o README e os comandos de verificação passam a usar o nome sslip.io; o front ainda não aponta para a API.
- **[cert-manager parado ou removido]** → o certificado atual continua sendo servido até vencer, mas não é renovado. Mitigação: o CA03 confere o `Renewal Time`, e a Let's Encrypt avisa por e-mail perto do vencimento.
- **[Trade-off: dois arquivos de Ingress para o mesmo host]** → mais manifestos para manter quando o host mudar. Em troca, o comportamento do HTTP não depende do padrão do Traefik.
- **[Trade-off: passos manuais a mais]** → o cert-manager e os issuers não são reconciliados pelo Argo CD. Em troca, não há app-of-apps nem dependência de ordem de CRD no sync. Os passos ficam no README.

## Migration Plan

1. **Cluster (manual, pelo túnel):**
   - instalar o cert-manager na versão fixa e esperar os três Deployments ficarem disponíveis;
   - aplicar `deploy/cert-manager/cluster-issuers.yaml` e conferir que `kubectl get clusterissuer` mostra os dois `Ready=True`.
2. **PR 1 (staging):** os Ingresses com `letsencrypt-staging`, o Middleware e a CI. Depois do merge, o Argo CD sincroniza. Conferir:
   - o Certificate, CA04;
   - o redirect, CA02;
   - `curl -k https://136-248-80-75.sslip.io/actuator/health` responde `200`.
3. **PR 2 (produção):** a anotação passa a `letsencrypt`. Depois do sync, conferir CA01 (sem `-k`), CA02, CA03 e CA07 (`nmap`).
4. **Front:** fica fora desta change. A subtarefa do front aponta a `EDUCARE_API_URL` para o novo host e faz o login de ponta a ponta (CA05).

**Rollback (CA08):**
1. Revert, na `main`, do commit (ou dos commits) dos Ingresses e do Middleware. O Argo CD sincroniza; com o `prune`, o Middleware e o `educare-backend-http` somem, e o `educare-backend` volta a não ter host, nos dois entrypoints.
2. Se for o caso, `kubectl -n educare delete certificate educare-backend-tls` e `kubectl -n educare delete secret educare-backend-tls`, que ficam órfãos.
3. Se for o caso, `kubectl delete -f deploy/cert-manager/cluster-issuers.yaml` e `kubectl delete -f` do manifesto do cert-manager, na mesma versão.
4. Conferir que `curl http://136.248.80.75/actuator/health` volta a responder `200`.

## Open Questions

- Qual a versão exata do cert-manager: a última estável `v1.x` no momento do apply, conferida em <https://github.com/cert-manager/cert-manager/releases> e anotada no README.
- Qual e-mail vai na conta ACME: o da mantenedora (D7); o endereço exato é confirmado no apply.
