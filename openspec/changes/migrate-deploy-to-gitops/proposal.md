# Proposal

## Why

O deploy atual empurra a imagem por SSH para uma VM com Docker Compose + Caddy, e o banco ficava em outra VM. Essas VMs já foram excluídas. A única infraestrutura agora é uma VM Oracle Ampere **arm64** (2 OCPUs, 12 GB) com k3s de nó único e Argo CD. O pipeline atual não consegue implantar nela por dois motivos: a imagem publicada é só `linux/amd64` e o destino do SSH não existe mais. A troca é por GitOps: a CI só constrói e publica a imagem, e o cluster se sincroniza sozinho a partir dos manifests versionados e das versões publicadas. Assim o pipeline deixa de usar SSH e o rollback fica rastreável (tarefa [[Infra][Back] Migrar deploy para GitOps com Argo CD no k3s](https://app.clickup.com/t/1dfh60ph0ap)).

## What Changes

- **CI (`.github/workflows/ci.yml`)**:
  - A imagem passa a ser só `linux/arm64`. **BREAKING** para quem rodava a imagem em amd64: as novas tags não rodam mais em x86_64.
  - Novo gatilho `push` de tags `v*`. Nele a imagem é publicada com a tag da versão (ex.: `v0.2.0`), além de `:<sha7>` e `:latest`, sem depender do label `build`. Só é publicada se o commit da tag estiver na `main`.
  - Em push na `main`, o gate do label `build` continua como está.
  - O job `build` mantém o id e continua sendo o check obrigatório.
  - Novo passo barato de validação dos manifests: `kubectl kustomize` + kubeconform.
- **Manifests Kustomize em `deploy/k8s/`**:
  - Namespace `educare`.
  - Postgres 16 em StatefulSet com PVC de 10 Gi no `local-path` e Service só ClusterIP.
  - API em Deployment com 1 réplica, probes de liveness, readiness e startup, requests/limits de memória e JVM por `MaxRAMPercentage`, mais um Service ClusterIP.
  - Ingress Traefik sem host, roteando `/` para a API.
  - Os segredos vêm só do Secret `educare-env`, criado à mão no cluster.
- **Argo CD em `deploy/argocd/`**:
  - A `Application` `educare` aponta para `deploy/k8s` na `main`, com sync automático, `prune` e `selfHeal`.
  - Um `ImageUpdater` (Argo CD Image Updater v1.x, CRD) entrega a maior tag `vX.Y.Z` com write-back direto na Application, sem commit em nenhuma branch.
  - Os dois são aplicados uma vez com `kubectl`.
- **Aplicação**:
  - `management.endpoint.health.probes.enabled: true` explícito no `application.yaml`. Hoje os probes existem só pelo padrão do Spring Boot.
  - O requisito do Actuator na spec `security` passa a garantir os probes no profile `prod` e o `503` da readiness quando a aplicação recusa tráfego.
- **Remoção do deploy antigo**: saem `.github/workflows/deploy.yml`, `deploy/compose.yaml`, `deploy/Caddyfile` e `deploy/deploy.sh`.
- **Documentação**:
  - O README ganha uma nova seção "Deploy em produção" com cinco procedimentos: criar o Secret, instalar o Image Updater e registrar a Application, abrir o túnel SSH, publicar uma versão e fazer rollback.
  - `CLAUDE.md` e `openspec/config.yaml` deixam de descrever VM, SSH, Caddy e banco em outra VM.
  - O `Dockerfile` corrige o comentário de arquitetura.

## Non-goals

- Aplicar qualquer coisa no cluster, instalar o Image Updater ou criar Secrets. Tudo isso fica documentado no README e é feito à mão pela mantenedora.
- Migrar dados: o banco novo começa vazio, o Flyway cria o schema e a V3 cria o admin a partir do `EDUCARE_ADMIN_PASSWORD`.
- Backup do Postgres. É uma tarefa separada e pré-requisito antes de haver dados reais.
- Domínio, HTTPS e cert-manager. O Ingress só fica preparado, com `host`/`tls` comentados.
- Expor o painel do Argo CD ou a API do Kubernetes na internet. O acesso é só por túnel SSH.
- Mais de uma réplica da API, HPA, PodDisruptionBudget, NetworkPolicies e observabilidade (métricas, logs centralizados).
- Imagem multi-arquitetura. O único destino é arm64.
- Gerenciar a Application ou o ImageUpdater por app-of-apps (ver design).
- Mudar o `docker-compose.yaml` da raiz, que continua sendo só o ambiente local.

## Capabilities

### New Capabilities
<!-- Nenhuma. CI, manifests e Argo CD são ferramenta de entrega, sem comportamento da API testável por teste de cenário (mesmo critério do D1 das changes add-ci-workflow e add-cd-pipeline). São verificados por kustomize/kubeconform na CI e pelas tasks manuais. -->

### Modified Capabilities
- `security`: o requisito "Actuator restrito ao health, sem detalhes" passa a exigir os probes `liveness` e `readiness` também no profile `prod`, qualquer que seja o ambiente detectado, e define a resposta da readiness quando a aplicação recusa tráfego (`503` só com o status). O Kubernetes depende desses dois comportamentos para decidir quando mandar tráfego para o pod e quando reiniciá-lo.

Nenhum módulo de negócio é afetado. A mudança de código se restringe à configuração transversal (`application.yaml`) e aos testes de `shared/security`.

## Impact

- **Repositório**:
  - Alterados: `.github/workflows/ci.yml`, `Dockerfile` (só o comentário; o `.dockerignore` já exclui `deploy/`), `src/main/resources/application.yaml`, testes em `shared/security`, `README.md`, `CLAUDE.md` e `openspec/config.yaml`.
  - Novos: `deploy/k8s/**` e `deploy/argocd/**`.
  - Removidos: `.github/workflows/deploy.yml`, `deploy/compose.yaml`, `deploy/Caddyfile` e `deploy/deploy.sh`.
- **API**: nenhuma rota nova nem alteração no contrato OpenAPI. `/actuator/health/liveness` e `/readiness` continuam públicos e só com o status.
- **GitHub**: o environment `production` e os secrets `SSH_*` deixam de ser usados. A limpeza é manual e está no checklist pós-validação. O pacote do GHCR continua público.
- **Infraestrutura (Oracle Free Tier, 1 VM Ampere 2 OCPU/12 GB)**:
  - Cabem no nó: Postgres (request 256Mi/limit 1Gi), API (request 512Mi/limit 1Gi) e o Image Updater (componente novo, ~512Mi de request no manifest oficial), além do k3s e do Argo CD que já existem.
  - Os dados ficam no disco local da VM (`local-path`): perder a VM é perder os dados, até existir o backup.
- **Dependências**: nenhuma nova no build Gradle. Na CI, o binário do kubeconform, baixado e conferido por checksum. No cluster, o Argo CD Image Updater (instalação manual).
