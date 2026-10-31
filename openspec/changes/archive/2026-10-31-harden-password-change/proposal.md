# Proposal

## Why

Hoje, quando um usuário troca a própria senha (ou um `ADMIN` redefine a senha de alguém), os tokens emitidos antes continuam valendo até expirar, por até 8 horas: quem roubou um token ou ficou logado num computador compartilhado não perde o acesso. Além disso, a API aceita uma nova senha igual à atual ou igual ao login e não limita as tentativas da senha atual, o que permite adivinhar a senha de quem deixou a sessão aberta. A página "Minha conta" do frontend ([tarefa no ClickUp](https://app.clickup.com/t/1dfh60pgyz9), subtarefa de back-end [Invalidar sessões na troca de senha e endurecer regras](https://app.clickup.com/t/1dfh60pgyza)) depende destas mudanças.

## What Changes

- Novo dado no usuário: o instante da última troca de senha (`password_changed_at`, em segundos), gravado na troca da própria senha e na redefinição feita pelo `ADMIN`. Usuários que nunca trocaram a senha depois da migration ficam sem o valor.
- Token emitido antes da última troca de senha do usuário (`iat` anterior a `password_changed_at`) passa a ser recusado com `401`, em toda a API, na requisição seguinte à troca. A verificação aproveita a leitura do usuário que já acontece em toda requisição autenticada, sem consulta extra.
- **BREAKING** `PUT /api/v1/auth/me/password` deixa de responder `204 No Content` e passa a responder `200 OK` com um token novo, no mesmo formato do login (`accessToken`, `tokenType`, `expiresIn`), para a sessão atual continuar ativa. O frontend precisa gravar o token novo.
- Novas regras para a nova senha na troca da própria senha: diferente da senha atual e diferente do login (sem diferenciar maiúsculas), com `400` e uma entrada em `errors` para `newPassword`.
- Limite de tentativas: depois de 5 erros de senha atual em 15 minutos (janela deslizante), o mesmo usuário recebe `429 Too Many Requests` (ProblemDetail) em `PUT /api/v1/auth/me/password`, mesmo com a senha certa, até a janela liberar; uma troca bem-sucedida zera a contagem. O contador fica em memória e é perdido ao reiniciar a aplicação. Tentativa bloqueada gera log `WARN` com o id do usuário, sem senha nem hash.
- `PUT /api/v1/users/{id}/password` (redefinição pelo `ADMIN`) continua respondendo `204`, mas passa a desconectar o usuário afetado.
- Contrato `openapi/educare-api.json` atualizado: `200` com `AccessTokenResponse` e o novo erro `429` em `PUT /api/v1/auth/me/password`.
- Nova migration Flyway acrescentando `password_changed_at timestamptz NULL` à tabela `users`.

## Non-goals

- Tela ou endpoint para listar ou encerrar sessões manualmente (não há registro de sessões; a invalidação é só pela data da troca).
- Refresh token, lista de tokens revogados ou logout explícito.
- "Esqueci minha senha" ou recuperação por email.
- Editar nome, email ou foto pela própria conta.
- Aplicar as regras "diferente da atual" e "diferente do login" à criação de usuário e à redefinição pelo `ADMIN` (continuam só com 8 a 72 caracteres).
- Limite de tentativas no login (`POST /api/v1/auth/login`) ou por IP.
- Contador de tentativas compartilhado entre instâncias ou persistido (uma única VM; Redis está fora de questão no Free Tier).
- Header `Retry-After` na resposta `429`.
- As mudanças do frontend (página `/minha-conta`, modal e tratamento do `429`), que ficam no repositório do frontend.

## Capabilities

### New Capabilities
<!-- Nenhuma. -->

### Modified Capabilities
- `auth`: a troca da própria senha passa a responder `200` com um token novo, encerra as outras sessões, recusa nova senha igual à atual ou ao login e ganha o limite de tentativas da senha atual (`429`).
- `security`: o token emitido antes da última troca de senha do usuário entra entre os motivos de recusa com `401`.
- `user`: a redefinição de senha pelo `ADMIN` passa a desconectar o usuário afetado.
- `api-contract`: o cenário de "operação sem corpo de resposta" deixa de usar `PUT /api/v1/auth/me/password`, e o contrato passa a declarar o `200` com token e o `429` dessa operação.

A mudança atravessa os módulos `auth` e `user` e o `shared/security` porque a senha e sua data pertencem ao `user` (entidade `User`, `UserService`), a emissão do token e o endpoint da própria conta pertencem ao `auth`, e a validação do token em toda requisição fica em `shared/security`. A comunicação entre eles segue as regras atuais: `auth` chama o `UserService`, e `shared/security` recebe os dados do usuário pela interface `AuthenticatedUserLookup`, implementada pelo `user`.

## Impact

- **Código**:
  - `user`: `User` (novo campo e troca de senha com data), `UserService.changeOwnPassword`/`changePassword`, `UserAuthenticationLookup`, novo limitador de tentativas em `user/application` e novas exceções de domínio (nova senha igual à atual, igual ao login, tentativas esgotadas);
  - `auth`: `AuthService.changeOwnPassword` devolve um token novo; `AuthController` responde `200` com `AccessTokenResponse` e documenta o `429`;
  - `shared/security`: `AuthenticatedUserLookup` passa a devolver também a data da última troca de senha, e `AuthenticatedUserConverter` recusa o token anterior a ela;
  - `shared/error` e `shared/config`: nova exceção base para `429` no `GlobalExceptionHandler` e nova meta-anotação de documentação do `429`.
- **API**: `PUT /api/v1/auth/me/password` muda de `204` para `200` com corpo (**BREAKING** para o frontend, que é atualizado na subtarefa de front da mesma tarefa); novo status `429` nessa operação; tokens antigos recusados após troca ou redefinição de senha.
- **Banco**: nova migration `V5__add_password_changed_at_to_users.sql`. Esta change é aplicada antes da `add-child-crud`, que por isso passa a usar `V6`.
- **Dependências de bibliotecas e infraestrutura**: nenhuma nova; nenhuma variável de ambiente nova.
- **Documentação**: nenhuma mudança esperada no CLAUDE.md.
