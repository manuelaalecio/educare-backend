# Proposal

## Why

A escola se organiza em coordenadores(as) e educadores(as), mas a API só conhece as roles `ADMIN` e `USER`, que o front exibe como Administrador e Funcionário. A aba Configurações › Usuários, especificada na tarefa [Aba Usuários em Configurações com funções Coordenador(a) e Educador(a)](https://app.clickup.com/t/1dfh60ph0nd), precisa que a API já fale em funções. Esta change cobre só a parte do backend, a subtarefa [Trocar ADMIN/USER pelas funções COORDENADOR/EDUCADOR](https://app.clickup.com/t/1dfh60ph0ne).

## What Changes

- **BREAKING**: as roles passam a ser `COORDENADOR` e `EDUCADOR`. `ADMIN` e `USER` deixam de existir na API: requisições com esses valores em `role` são recusadas com `400` e a mensagem "deve ser COORDENADOR ou EDUCADOR", e as respostas (`/api/v1/users`, `/api/v1/auth/me`) só devolvem os valores novos.
- Nova migration Flyway que converte os dados existentes (`ADMIN` → `COORDENADOR`, `USER` → `EDUCADOR`, inclusive o administrador inicial) e troca o `CHECK` e o `DEFAULT` (`EDUCADOR`) da coluna `users.role`.
- Usuário criado sem `role` vira `EDUCADOR`.
- `/api/v1/users` e `/api/v1/classrooms` passam a exigir `COORDENADOR` (no lugar de `ADMIN`). `EDUCADOR` tem exatamente o acesso que `USER` tem hoje, então recebe `403` nessas rotas.
- A regra "o sistema nunca fica sem `ADMIN`" vira "o sistema nunca fica sem `COORDENADOR`", com o `409` agora com a mensagem "O sistema precisa ter ao menos um(a) coordenador(a)". O nome da exceção muda de acordo.
- Ninguém precisa fazer login de novo: a role não vai no token e é lida do banco a cada requisição, então os tokens emitidos antes da migração continuam valendo, já com a função nova.
- Contrato `openapi/educare-api.json` regenerado (o `pattern` de `role` muda) e specs atualizadas para os nomes novos.

## Non-goals

- Front-end: a aba Usuários, os rótulos "Função" e os nomes Coordenador(a)/Educador(a) na interface são a [subtarefa do front](https://app.clickup.com/t/1dfh60ph0nf), em outro repositório.
- Funções além de `COORDENADOR` e `EDUCADOR`.
- Permissões próprias de `EDUCADOR` (ex.: ver só as próprias turmas). O acesso continua igual ao do `USER` atual.
- Busca ou filtro na listagem de usuários, convite por email e troca obrigatória de senha no primeiro acesso.
- Aceitar `ADMIN`/`USER` como apelidos por um período de transição. A troca é de uma vez, coordenada com o deploy do front.
- Renomear o administrador inicial (`admin@educare.org`, nome `Administrador`) ou a variável `EDUCARE_ADMIN_PASSWORD`: ele só passa a ter a função `COORDENADOR`.
- Deploy sem nenhuma interrupção: a migration não é compatível com a versão em produção (ver design), e isso foi aceito para esta change.

## Capabilities

### New Capabilities
<!-- Nenhuma. -->

### Modified Capabilities
- `user`: valores aceitos e padrão da role, migração das roles existentes, acesso restrito a `COORDENADOR`, regra de ao menos um `COORDENADOR` (com a nova mensagem do `409`) e o administrador inicial com a função `COORDENADOR`.
- `security`: os cenários de role vigente e de token recusado passam a usar `COORDENADOR`/`EDUCADOR`.
- `classroom`: gestão de turmas restrita a `COORDENADOR`.
- `auth`: `GET /api/v1/auth/me` vale para `COORDENADOR` e `EDUCADOR`, e devolve a role nova.
- `persistence`: os cenários de autoria passam a usar os nomes novos (sem mudança na regra).
- `api-contract`: o cenário de erros de uma operação restrita passa a falar em `COORDENADOR` (sem mudança na regra).

A mudança atravessa módulos porque a role é do módulo `user`, mas é usada no `@PreAuthorize` de outros módulos (`classroom`) e citada nas specs transversais. O código só muda em `user` e `classroom` (e em comentários de `shared/security`); as demais capabilities mudam só no texto dos cenários.

## Impact

- **Código**: `user/domain` (`Role`, `User`, exceção do último coordenador), `user/application/UserService`, `user/api` (DTOs e `@PreAuthorize`), `classroom/api/ClassroomController` (`@PreAuthorize`) e javadocs que citam `ADMIN` em `shared/security`.
- **API**: contrato de `role` em `CreateUserRequest`/`UpdateUserRequest`/`UserResponse` muda. `openapi/educare-api.json` regenerado. O front precisa regenerar os tipos e subir junto: com só o back novo, o front atual (que espera `ADMIN`) esconde Configurações de todo mundo.
- **Banco**: nova migration `V7__rename_roles_to_coordinator_educator.sql`. Nenhuma migration existente é editada (a `V2` e a `V3` continuam com `ADMIN`/`USER`, e a `V7` converte o que elas criaram).
- **Testes**: praticamente toda a suíte que cria usuários ou tokens (`TestUsers`, `AccessTokens`, testes de `user`, `auth`, `classroom`, `shared/security` e `shared/persistence`) troca os valores.
- **Deploy**: nenhuma variável nova. O deploy precisa ser coordenado com o front e feito num horário sem uso (ver design, decisão sobre a compatibilidade da migration).
- **Outras changes**: `add-child-crud` (proposta, não implementada) cita `ADMIN`/`USER` na spec e no design e precisa ser atualizada antes de ser implementada. `migrate-deploy-to-gitops` (em andamento) modifica o requirement do Actuator em `security`, que cita `ADMIN` só de passagem; esta change não mexe nesse requirement para não conflitar.
- **Documentação**: CLAUDE.md (exemplo de `@PreAuthorize` e a descrição do administrador inicial) e o README, se citarem a role.
