# Proposal

## Why

A tarefa [Otimizar requisições: cache de consultas e F5](https://app.clickup.com/t/1dfh60ph1zx) quer cortar as chamadas repetidas do frontend à API (hoje, 10 F5 seguidos geram cerca de 20 chamadas ao Spring), o que pesa na VM Always Free e vai piorar com o cadastro de crianças. A maior parte do trabalho é no frontend (cache de consultas, SSR, cache do BFF). Ao backend cabem duas coisas: **medir** as requisições, para ter a linha de base antes da mudança do front e o número depois, e **limitar** as requisições por usuário, para que um laço ou um F5 em excesso não derrube a VM. Esta change cobre só essa parte (subtarefas [Medir requisições e tempos da API](https://app.clickup.com/t/1dfh60ph1zy) e [Limite de requisições por usuário na API](https://app.clickup.com/t/1dfh60ph201)). A medição precisa ir para produção antes das mudanças do front, para registrar a linha de base.

## What Changes

- **Log por requisição para medição**: uma linha de log por requisição à API com método, rota como modelo (ex.: `/api/v1/children/{id}`), status e duração em milissegundos. Sem query string, sem caminho real, sem id de usuário e sem nenhum dado do corpo. O health do Actuator fica fora, para os probes não encherem o log. O Actuator continua expondo só o health.
- **Roteiro do relatório**: o README ganha uma seção com o comando que lê os logs do pod e calcula, por rota, o número de chamadas e o p95 da duração, para o relatório antes/depois.
- **Limite de requisições por usuário autenticado**: até 120 requisições por usuário numa janela deslizante de 1 minuto (valores configuráveis, a ajustar depois da medição). Acima disso, a API responde `429 Too Many Requests` com `ProblemDetail` (`detail`: `Muitas requisições. Aguarde um instante.`) e o header `Retry-After`, sem executar a operação. O limite fica só em memória, por usuário, e não afeta os outros usuários. Rotas públicas (login, health) ficam fora.
- **Contrato**: toda operação que exige token passa a declarar `429` em `openapi/educare-api.json`; o login continua sem `429`. O frontend regenera os tipos e passa a tratar o `429` com a mensagem da tarefa.

## Non-goals

- Cache de respostas na API, ETag/`304` ou qualquer cache de `/auth/me` (a revogação de sessão depende de `/auth/me` sempre ir à API).
- Limite por IP para rotas anônimas (login); fica como pergunta em aberto na tarefa.
- Expor métricas pelo Actuator (`/actuator/metrics`, Prometheus) ou adicionar Redis, Prometheus ou outro serviço: o limite e a medição ficam em memória e no log.
- Todo o trabalho do frontend (chaves de consulta, `staleTime`, `meta.invalidates`, SSR, cache do BFF, indicador "Atualizado há…").
- Mudar o limite de tentativas da troca de senha, que continua como está.
- Gerar o relatório de medição automaticamente: ele é feito à mão a partir dos logs, com o comando do README.

## Capabilities

Todas as mudanças são transversais (código em `shared`) e não tocam nenhum módulo de negócio: o limite vale para a API inteira e o log, para toda requisição.

### New Capabilities
- `observability`: o log por requisição usado na medição (campos, formato, o que fica fora e a garantia de não registrar dados pessoais).

### Modified Capabilities
- `security`: novo requirement de limite de requisições por usuário autenticado (`429`, `Retry-After`, janela deslizante, isolamento entre usuários, rotas públicas fora).
- `api-contract`: o requirement "Respostas de erro descritas como ProblemDetail" passa a exigir `429` em toda operação que exige token; o cenário "Operação sem limite de tentativas não declara 429" muda.

## Impact

- **Código**: `shared/security` (limitador e o interceptor que o aplica), `shared/error` (exceção do limite e header `Retry-After` no `GlobalExceptionHandler`), `shared/config` (customizer do OpenAPI que declara o `429`), novo `shared/observability` (filtro do log). Nenhum módulo de negócio muda.
- **API**: `429` novo em todas as rotas autenticadas. Para o uso normal (um funcionário navegando), o limite não é atingido.
- **Contrato**: `openapi/educare-api.json` regenerado; o frontend precisa regenerar os tipos.
- **Configuração**: propriedades novas com padrão no `application.yaml` (sem variável de ambiente obrigatória); nada muda no Deployment nem no Secret `educare-env`.
- **Infra**: nenhum componente novo. Memória extra do limitador desprezível (até 120 instantes por usuário ativo). O volume de log cresce uma linha por requisição.
- **Dependências**: nenhuma nova.
