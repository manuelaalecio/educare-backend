# Design

## Context

- Não existe `.github/`. O build completo é `./gradlew build`: o `test` é finalizado pelo `jacocoTestReport` e o `check` depende do `jacocoTestCoverageVerification` (mínimo de 90% de linhas e de branches), conforme o `build.gradle`.
- Os testes de persistência e de cenário sobem um `postgres:16` pelo Testcontainers, então o ambiente do build precisa de Docker.
- O `build.gradle` usa toolchain Java 21, e o wrapper é o Gradle 9.7.1 (`gradlew` já está com permissão de execução no git e com `eol=lf` no `.gitattributes`).
- O fluxo de branches é `dev` → PR → `main`. O repositório é público (`manuelaalecio/educare-backend`).
- O `Dockerfile` gera a imagem com `./gradlew bootJar`, sem testes, e continua assim.
- O build não precisa de variáveis de ambiente, `.env` nem secrets. Nos testes, a conexão com o banco vem do container via `@ServiceConnection` (`TestcontainersConfiguration`), então o `${SPRING_DATASOURCE_PASSWORD}` do profile `dev` (padrão, ativo também nos testes) nunca é resolvido. O `.env` é só do Docker Compose e não é versionado. Isso foi confirmado rodando `./gradlew build --rerun-tasks` sem nenhuma variável `SPRING_*` definida: verde, em cerca de 21 s. O workflow não define `env:` nem usa `secrets`.

## Goals / Non-Goals

**Goals:**
- Nenhum PR para a `main` pode ser mergeado sem o `./gradlew build` verde num ambiente limpo.
- Quando o build falhar, dá para investigar pelos relatórios, sem reproduzir localmente.
- Um push novo na branch do PR não deixa execuções antigas gastando runner.

**Non-Goals:**
- Matriz de versões de Java ou de sistemas operacionais: o alvo é só Java 21 em Linux, igual à produção.
- Publicar relatórios de cobertura em serviços externos (Codecov etc.) ou como comentário no PR.
- Deixar o CI mais rápido que o build local (paralelismo, divisão em jobs).

## Decisions

### D1. Change sem specs (`skip_specs: true`)

As specs do projeto descrevem comportamento do sistema, e cada Scenario tem um teste de cenário `@SpringBootTest` correspondente (CLAUDE.md, Testes). O CI não muda nada que a API ou a aplicação façam; ele é ferramenta de desenvolvimento, do mesmo tipo do `build.gradle`. Uma capability `ci` teria Scenarios ("WHEN um PR para a `main` é aberto THEN o check roda") que só dá para verificar à mão no GitHub, sem teste automatizado. Isso contraria a regra de que todo Scenario tem teste de cenário, e a spec só repetiria o arquivo do workflow.

- Alternativa: criar a capability `ci` com Scenarios validados manualmente. Descartada pelos motivos acima.
- A verificação do comportamento do CI fica nas tasks do grupo 3, feitas num PR real.

### D2. Gatilho: só `pull_request` para a `main`

```yaml
on:
  pull_request:
    branches: [main]
```

Os tipos padrão do `pull_request` (`opened`, `synchronize`, `reopened`) já fazem o workflow rodar de novo a cada push na branch do PR. O evento é `pull_request`, e não `pull_request_target`: PRs de forks rodam com o código do fork e sem acesso a segredos. Isso é seguro num repositório público, e o workflow não precisa de segredos (o banco dos testes é o do Testcontainers).

- Alternativa: incluir `push` na `dev`/`main`. Fora do escopo (proposal, Non-goals).

### D3. Um único job `build`, sem `name:`

O job tem o id `build` e nenhum `name:`. Assim o nome do check que o GitHub mostra no PR e oferece na regra de proteção é exatamente **`build`**. Esse é o nome a selecionar na task manual. O workflow se chama `CI` (`name: CI`), e o PR mostra o check como `CI / build (pull_request)`.

Passos do job:
1. `actions/checkout`.
2. `actions/setup-java` com `distribution: temurin` e `java-version: 21`. O toolchain do Gradle encontra esse JDK, sem download.
3. `gradle/actions/setup-gradle`: cache do Gradle (wrapper, dependências e build cache) e validação do checksum do `gradle-wrapper.jar`, que vem ligada por padrão.
4. `./gradlew build`.
5. Em falha, `actions/upload-artifact` (D6).

`runs-on: ubuntu-latest`, que já tem Docker funcionando para o Testcontainers e não exige configuração extra (Ryuk incluso).

`timeout-minutes: 20` no job. É folga suficiente para o build atual (poucos minutos) e evita que um container ou teste travado consuma as 6 h padrão do runner. O valor deve ser revisto se o build crescer.

### D4. Permissões e concorrência

```yaml
permissions:
  contents: read

concurrency:
  group: ci-${{ github.event.pull_request.number }}
  cancel-in-progress: true
```

`contents: read` no nível do workflow: o job só lê o código. O `upload-artifact` não precisa de permissão extra do `GITHUB_TOKEN`. O grupo de concorrência usa o número do PR, então um push novo cancela a execução em andamento do mesmo PR, e PRs diferentes não se afetam.

### D5. Actions fixadas por versão principal

Cada action é referenciada pela tag da versão principal (`@vN`), sem SHA nem `@main`. Na implementação, usar a versão principal mais recente de cada uma, conferida na página de releases (`actions/checkout`, `actions/setup-java`, `gradle/actions`, `actions/upload-artifact`).

- Alternativa: fixar por SHA de commit, que é mais resistente a uma tag reescrita. Descartada por ora: exige atualizar os SHAs à mão, e sem Dependabot (fora do escopo) eles ficariam desatualizados. Pode ser revisto junto com a change do Dependabot.

### D6. Relatórios como artifact só em falha

```yaml
- if: failure()
  uses: actions/upload-artifact@vN
  with:
    name: build-reports
    path: |
      build/reports/tests/test/
      build/reports/jacoco/test/
      build/test-results/test/
    retention-days: 7
```

O `if: failure()` publica o que existir em cada tipo de falha. O `jacocoTestReport` é finalizador do `test`, mas também declara `dependsOn test`, então o Gradle o pula quando o `test` falha. O conteúdo do artifact fica assim:

- Falha de teste: só os relatórios de teste (`build/reports/tests/test/` e `build/test-results/test/`), sem o relatório do JaCoCo.
- Falha de cobertura: os testes passaram e o `jacocoTestReport` roda, então o artifact traz os relatórios de teste e o do JaCoCo (`build/reports/jacoco/test/`).
- Falha de compilação: nenhum dos diretórios existe, e o upload só avisa que não encontrou arquivos (`if-no-files-found: warn`, o padrão), sem mascarar o erro original.

O `build.gradle` fica como está de propósito: mudá-lo está fora do escopo (proposal, Non-goals). A lista de caminhos também não muda: um diretório ausente não causa erro. A retenção de 7 dias basta para investigar e evita acumular armazenamento.

- Alternativa: publicar sempre. Descartada, porque o pedido é para facilitar a investigação de falhas, e em execução verde os relatórios não são necessários.

### D7. Cache do Gradle gravado também nos PRs

Por padrão, o `setup-gradle` só grava cache em execuções da branch padrão (`main`) e fica somente leitura nas demais. Como o workflow nunca roda em push na `main`, esse padrão faria o cache nunca ser gravado. Por isso o workflow usa `cache-read-only: false`. Caches criados num evento `pull_request` ficam no escopo do PR (`refs/pull/<n>/merge`): pushes seguintes do mesmo PR aproveitam o cache, e um PR novo começa sem ele.

- Alternativa: adicionar `push` na `main` só para gerar o cache. Descartada porque mudaria o gatilho, que está fora do escopo. Pode entrar junto com o CI em push, se ele vier depois.

### D8. Documentação

- CLAUDE.md, seção Testes: registrar que o `./gradlew build` também roda no CI (`.github/workflows/ci.yml`) em todo PR para a `main`, que o check `build` é obrigatório para o merge e que, em falha, os relatórios ficam no artifact `build-reports` da execução.
- `openspec/config.yaml`, `context` → Testes: acrescentar uma linha de resumo com o mesmo registro.

Esta change não tem endpoints nem alterações de schema (sem migration Flyway). Também não acrescenta dependência nem componente de infraestrutura ao sistema implantado, e a VM do Oracle Free Tier não é afetada.

## Risks / Trade-offs

- [Testes de cenário com Testcontainers podem ser mais lentos ou instáveis no runner do que na máquina local] → `timeout-minutes` limita o custo; os relatórios em artifact ajudam a diagnosticar; um teste instável é tratado como bug do teste, com teste que o reproduza.
- [O primeiro build de cada PR não tem cache (D7)] → custo só de tempo, sem custo financeiro num repositório público.
- [Tags `@vN` podem ser movidas pelo mantenedor da action] → são actions oficiais do GitHub e do Gradle, e o workflow tem só `contents: read`, sem segredos; fixar por SHA fica para a change do Dependabot.
- [A regra de proteção é manual e pode ficar diferente do workflow, por exemplo se o job for renomeado] → o nome do check (`build`) está documentado neste design e no CLAUDE.md; renomear o job exige atualizar a regra.
- [O check `build` só aparece para seleção na regra de proteção depois de rodar pelo menos uma vez] → a task manual vem depois da primeira execução do workflow num PR.

## Migration Plan

1. Fazer merge do workflow via PR `dev` → `main`. É o primeiro PR em que o check roda.
2. Depois que o check `build` rodar, configurar a regra de proteção da `main` exigindo esse check.
3. Rollback: remover `.github/workflows/ci.yml` e desmarcar o check na regra de proteção. Nenhum código de produção é afetado.
