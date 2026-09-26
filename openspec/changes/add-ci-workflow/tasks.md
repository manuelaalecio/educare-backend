# Tasks

## 1. Workflow do CI

- [x] 1.1 Conferir na página de releases a versão principal mais recente de `actions/checkout`, `actions/setup-java`, `gradle/actions` e `actions/upload-artifact` (design D5); verificar anotando as versões escolhidas na descrição do commit da 1.2
- [x] 1.2 Criar `.github/workflows/ci.yml` com `name: CI`, gatilho `pull_request` só para `branches: [main]` (D2), `permissions: contents: read` e `concurrency` com grupo `ci-${{ github.event.pull_request.number }}` e `cancel-in-progress: true` (D4), e um único job de id `build`, sem `name:`, em `ubuntu-latest` com `timeout-minutes: 20` (D3). Passos: checkout, `setup-java` (Temurin 21), `setup-gradle` com `cache-read-only: false` (D7), `./gradlew build` e, com `if: failure()`, upload do artifact `build-reports` com `build/reports/tests/test/`, `build/reports/jacoco/test/` e `build/test-results/test/`, com `retention-days: 7` (D6). Não alterar o `Dockerfile`. Verificar a sintaxe com `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest` sem erros e com `git diff --stat` mostrando só o arquivo novo

## 2. Documentação

- [x] 2.1 Atualizar a seção Testes do CLAUDE.md: o `./gradlew build` também roda no CI (`.github/workflows/ci.yml`) em todo PR para a `main`, o check `build` é obrigatório para o merge e, em falha, os relatórios ficam no artifact `build-reports` da execução (D8). Verificar relendo a seção e conferindo que o nome do check e do artifact batem com o `ci.yml`
- [x] 2.2 Acrescentar ao resumo de Testes no `context` do `openspec/config.yaml` uma linha dizendo que o `./gradlew build` roda no CI em todo PR para a `main`, com o check `build` obrigatório (D8). Verificar com `openspec validate add-ci-workflow` sem erros e com `openspec instructions proposal --change add-ci-workflow --json` mostrando a linha nova no `context`

## 3. Validação no GitHub (manual)

- [x] 3.1 Commitar os grupos 1 e 2 na `dev`, fazer push e abrir o PR `dev` → `main`. Verificar na aba Checks que o check `CI / build (pull_request)` roda, executa o `./gradlew build` (os testes de cenário com Testcontainers aparecem no log) e termina verde, sem gerar o artifact `build-reports`
- [x] 3.2 PR de teste do caminho de falha: criar a partir da `dev` a branch descartável `ci-smoke-failure`, com um commit que faz um teste existente falhar, e abrir um PR para a `main`. Verificar que o check `build` fica vermelho e que a execução publica o artifact `build-reports` com os relatórios de teste (o do JaCoCo não é gerado quando um teste falha, ver D6). Em seguida, fazer dois pushes seguidos na branch e verificar que a execução anterior aparece como cancelada (D4). Por fim, fechar o PR sem merge e apagar a branch (local e remota)
- [ ] 3.3 Configurar a proteção da `main` em Settings → Branches (ou Rules → Rulesets) do repositório: ativar "Require status checks to pass before merging" e selecionar o check **`build`** (origem GitHub Actions). Verificar que o PR da 3.1 mostra `build` como "Required" e que o botão de merge fica bloqueado enquanto o check não estiver verde

## 4. Verificação final

- [ ] 4.1 Rodar `./gradlew build` localmente com o Docker ativo e verificar que termina verde, incluindo `jacocoTestCoverageVerification` (mínimo de 90% de linhas e de branches), e que o check `build` do PR da 3.1 está verde no último commit antes do merge
