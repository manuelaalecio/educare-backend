# Proposal

## Why

Hoje o `./gradlew build` (testes unitários, web, persistência, cenário e ArchUnit, mais a verificação de 90% de cobertura do JaCoCo) só roda na máquina local, e nada impede um merge na `main` com o build quebrado. Como o repositório é público, os runners padrão do GitHub Actions não têm custo, então dá para rodar o build completo em todo PR para a `main` e bloquear o merge enquanto ele não estiver verde.

## What Changes

- Novo workflow `.github/workflows/ci.yml`, disparado só por `pull_request` com destino à `main` (roda de novo a cada push na branch do PR aberto).
- Um único job em `ubuntu-latest` que roda `./gradlew build` com Java 21 (Temurin) e cache do Gradle; o Docker do runner atende ao Testcontainers.
- Boas práticas no workflow: permissões mínimas (`contents: read`), `concurrency` que cancela a execução anterior do mesmo PR, `timeout-minutes` no job e actions fixadas por versão principal.
- Em caso de falha, os relatórios de teste e do JaCoCo são publicados como artifact da execução.
- Proteção da `main` (exigir o check do CI para liberar o merge), configurada manualmente no GitHub.
- CLAUDE.md (seção Testes) e o resumo de testes no `openspec/config.yaml` passam a registrar que o `./gradlew build` também roda no CI em todo PR para a `main`.

## Non-goals

- Deploy na VM da Oracle, build ou publicação de imagem Docker (GHCR) e Dependabot: ficam para changes futuras.
- Rodar o CI em push na `dev` (ou em push na `main`): pode ser acrescentado depois.
- Alterar o `Dockerfile`: ele continua usando `./gradlew bootJar`, sem rodar testes.
- Rodar o mutation testing (`./gradlew pitest`) no CI: continua fora do build padrão.
- Mudar o `build.gradle`, os testes ou os limites de cobertura.

## Capabilities

### New Capabilities
<!-- Nenhuma. -->

### Modified Capabilities
<!-- Nenhuma. -->

Esta change não afeta nenhum módulo nem capability: é ferramenta de desenvolvimento e não muda comportamento da API nem do sistema. Por isso ela é declarada sem specs (`skip_specs: true`); a justificativa está no design (D1).

## Impact

- **Código de produção e testes**: nenhuma alteração.
- **Repositório**: novo diretório `.github/workflows/` com `ci.yml`.
- **GitHub**: novo check `build` em todo PR para a `main` e regra de proteção da `main` exigindo esse check (configuração manual).
- **Dependências**: nenhuma nova no build; o workflow usa as actions oficiais `actions/checkout`, `actions/setup-java`, `gradle/actions/setup-gradle` e `actions/upload-artifact`.
- **Infraestrutura**: nenhum impacto na VM do Oracle Free Tier; o CI roda nos runners do GitHub.
- **Documentação**: CLAUDE.md (Testes) e `openspec/config.yaml` (resumo de Testes).
