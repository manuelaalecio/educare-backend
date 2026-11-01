# Proposal

## Why

Em produção, a API (k3s na VM Oracle Ampere) responde só em `http://136.248.80.75/`. Na porta 443, o Traefik apresenta o certificado autoassinado padrão (TRAEFIK DEFAULT CERT), que nenhum cliente aceita. O BFF do frontend manda a senha no login e o JWT em toda requisição, e em HTTP esses dados passariam em texto claro pela internet, com dados de crianças em jogo (LGPD, art. 14). Além disso, o `src/env.ts` do front recusa uma `EDUCARE_API_URL` sem `https://` em produção, então o front não tem como apontar para a API. Esta change cobre a parte do backend/infra da tarefa [[Infra][Back+Front] HTTPS na API com cert-manager e conexão do front em produção](https://app.clickup.com/t/1dfh60ph12y), que corresponde às subtarefas:
- [Instalar cert-manager e ClusterIssuers da Let's Encrypt](https://app.clickup.com/t/1dfh60ph12z);
- [Ingress com host sslip.io, TLS e redirect para HTTPS](https://app.clickup.com/t/1dfh60ph130);
- [Documentar HTTPS e rollback](https://app.clickup.com/t/1dfh60ph132).

## What Changes

- **Nome DNS**: a API passa a responder em `136-248-80-75.sslip.io`, que resolve para o IP público sem custo nem cadastro.
- **cert-manager**: instalado à mão com `kubectl apply` do manifesto oficial, numa versão fixa anotada no README, como já é feito com o Image Updater.
- **ClusterIssuers** em `deploy/cert-manager/cluster-issuers.yaml`: `letsencrypt-staging` e `letsencrypt` (produção), com ACME HTTP-01 pelo Traefik (`ingressClassName: traefik`). São aplicados à mão, fora da Application `educare` (ver design D2).
- **Ingress (`deploy/k8s/`)**:
  - O Ingress `educare-backend` ganha `host`, `tls` (Secret `educare-backend-tls`) e a anotação `cert-manager.io/cluster-issuer`, e fica restrito ao entrypoint `websecure`.
  - Um novo Ingress `educare-backend-http`, no entrypoint `web`, aplica o Middleware `redirect-https` do Traefik (`redirectScheme`, `scheme: https`, `permanent: true`). Toda requisição HTTP para o host recebe `308` para a mesma URL em HTTPS.
  - O desafio HTTP-01 continua respondendo em HTTP: o Ingress do solver, criado pelo cert-manager, tem uma regra mais específica (ver design D3).
  - O Middleware e o novo Ingress entram no `kustomization.yaml`.
- **Emissão em duas etapas**: a primeira entrega usa `letsencrypt-staging`. Só depois de o Certificate ficar `Ready=True` com o certificado de staging, um segundo PR troca a anotação para `letsencrypt`.
- **Acesso pelo IP**: com o host no Ingress, `http://136.248.80.75/` deixa de cair na API e passa a responder `404` do Traefik. **BREAKING** para quem acessa a API pelo IP puro: só o nome sslip.io passa a valer.
- **CI (job `manifests`)**:
  - valida o Middleware (CRD do Traefik) em `deploy/k8s`, usando o schema do CRDs-catalog;
  - valida os ClusterIssuers em `deploy/cert-manager`;
  - o id do job não muda.
- **Documentação**:
  - README: troca o aviso "Só HTTP", atualiza as portas abertas para 22, 80 e 443 e documenta a instalação do cert-manager, os ClusterIssuers, como conferir o Certificate, a troca de staging para produção e o rollback do HTTPS.
  - `CLAUDE.md` e `openspec/config.yaml`: passam a descrever o TLS da Let's Encrypt no Ingress, com o cert-manager e os ClusterIssuers instalados à mão.

## Non-goals

- Domínio próprio. Para trocar depois, basta mudar o host do Ingress, a `EDUCARE_API_URL` do front e o `dnsNames` do certificado.
- HSTS e preload, WAF e rate limit.
- Mais réplicas da API.
- Backup do Postgres, que tem tarefa própria. Até ele existir, a produção só recebe dados fictícios (RN01 da tarefa).
- Front-end: a `EDUCARE_API_URL` na Vercel, o redeploy e o login de ponta a ponta são a [subtarefa do front](https://app.clickup.com/t/1dfh60ph131). Aqui fica só a verificação de que a API responde em HTTPS e que o CORS já aceita a origem do front.
- Qualquer mudança no código da aplicação. O TLS termina no Traefik e a API continua em HTTP dentro do cluster. Nenhuma resposta da API monta URL absoluta a partir do esquema da requisição, então não é preciso configurar `forward-headers`.
- Pôr o cert-manager ou os ClusterIssuers na Application ou num app-of-apps.
- Abrir outras portas: o Postgres, a API do Kubernetes (6443) e o Argo CD continuam fora da internet.

## Capabilities

### New Capabilities
<!-- Nenhuma. -->

### Modified Capabilities
<!-- Nenhuma. A change não declara specs (skip_specs: true no .openspec.yaml), pelo mesmo critério do D1 das changes add-ci-workflow e add-cd-pipeline: TLS, certificado e redirect são ferramenta de entrega, na borda do cluster, e nenhum comportamento da aplicação muda. Um Scenario como "WHEN chamo http://... THEN recebo 308" não tem teste de cenário @SpringBootTest possível. Os critérios de aceite da tarefa (CA01 a CA04, CA07 e CA08) viram tasks de verificação manual no cluster, e a sintaxe dos manifests é verificada pelo job manifests da CI. -->

Nenhum módulo de negócio é afetado. A change mexe só em `deploy/`, no workflow de CI e na documentação.

## Impact

- **Repositório**:
  - Alterados: `deploy/k8s/ingress.yaml`, `deploy/k8s/kustomization.yaml`, `.github/workflows/ci.yml` (job `manifests`), `README.md`, `CLAUDE.md` e `openspec/config.yaml`.
  - Novos: `deploy/k8s/ingress-http.yaml`, `deploy/k8s/middleware-redirect-https.yaml` e `deploy/cert-manager/cluster-issuers.yaml`.
- **API e contrato OpenAPI**: nenhuma mudança. A URL pública passa a ser `https://136-248-80-75.sslip.io`.
- **Cluster** (passos manuais, documentados no README):
  - instalar o cert-manager no namespace `cert-manager`;
  - aplicar os ClusterIssuers;
  - conferir a emissão em staging antes de pedir o certificado de produção.
  - O CORS (`EDUCARE_CORS_ALLOWED_ORIGINS` no Secret `educare-env`) já tem `https://educare-frontend-ruby.vercel.app` e não muda.
- **Infraestrutura (Oracle Free Tier)**:
  - O cert-manager (controller, webhook e cainjector) usa uns 100 a 200 MB de RAM no nó de 12 GB, sem custo extra.
  - As portas abertas continuam 22, 80 e 443 (a 443 já está liberada na VCN e no iptables). A 80 precisa continuar aberta para o desafio HTTP-01 e para o redirect.
- **Dependências externas**:
  - Let's Encrypt (limites de emissão; por isso o staging vem antes da produção).
  - sslip.io, que não tem SLA: aceitável enquanto não houver domínio próprio.
  - O IP público precisa continuar o mesmo: se a VM for recriada, mudam o host e a URL do front.
- **Dados pessoais**: o e-mail da conta ACME fica no ClusterIssuer, num repositório público. A tarefa aceita isso, porque o e-mail não é segredo.
