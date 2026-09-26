# Proposal

## Why

Com o cadastro de usuários pronto, a API ainda está aberta: qualquer cliente pode listar, criar ou excluir usuários e até se promover a `ADMIN`. Isso impede qualquer deploy exposto e não atende à LGPD, que exige restringir o acesso a dados pessoais de crianças a usuários autenticados. Esta change fecha a API com login próprio e JWT e restringe a gestão de usuários a `ADMIN`, antes dos módulos `child` e `guardian`.

## What Changes

- **Login próprio**: `POST /api/v1/auth/login` recebe email e senha, confere a senha com o hash já guardado e devolve um access token JWT assinado pelo próprio backend.
  - O token vale 8 horas, e não há refresh token. Quando expira, o usuário faz login de novo. Logout é o frontend descartar o token.
  - Falha de login responde `401` com mensagem genérica, sem revelar se o email existe.
  - O token não carrega email nem nome, só o id do usuário.
- **Toda a API exige token** (`Authorization: Bearer <token>`), exceto o login e o health do Actuator. Sem token ou com token inválido/expirado, a resposta é `401`. Com token válido e role insuficiente, `403`. Ambos em `ProblemDetail`.
  - A cada requisição, o usuário do token é conferido no banco: um usuário excluído perde o acesso na hora, e uma mudança de role vale na requisição seguinte.
- **Autoatendimento de qualquer usuário autenticado**:
  - `GET /api/v1/auth/me`: os dados do próprio usuário;
  - `PUT /api/v1/auth/me/password`: troca da própria senha, exigindo a senha atual.
- **BREAKING** — **Gestão de usuários só para `ADMIN`**: todos os endpoints de `/api/v1/users` passam a exigir role `ADMIN`. Clientes sem token recebem `401`, e `USER` recebe `403`.
- **Proteção do acesso administrativo**: um `ADMIN` não pode excluir a si mesmo, e nenhuma alteração ou exclusão pode deixar o sistema sem nenhum `ADMIN` (`409`).
- **CORS**: só as origens do frontend configuradas por variável de ambiente podem chamar a API pelo navegador.
- **Configuração nova**:
  - `EDUCARE_JWT_SECRET`, a chave de assinatura dos tokens (no mínimo 32 bytes), obrigatória em produção;
  - `EDUCARE_CORS_ALLOWED_ORIGINS`, as origens do frontend, obrigatória em produção.
  - Sem elas, a aplicação não inicia no profile `prod`. No `dev`, ambas têm valor padrão.
- Nenhuma mudança de schema: a tabela `users` já tem tudo o que a autenticação usa.

## Non-goals

- Refresh token, revogação de token e logout no servidor. Um token emitido continua válido até expirar, mesmo depois de uma troca de senha (ver os riscos no design).
- Provedor de identidade externo (Keycloak, Auth0 etc.), SSO e login social.
- Limite de tentativas de login ou bloqueio temporário contra força bruta.
- Forçar a troca da senha do administrador inicial no primeiro acesso.
- "Esqueci minha senha", envio de email e políticas de complexidade de senha além do tamanho.
- Permissões mais finas que `ADMIN`/`USER` ou regras por recurso.
- Autoria dos registros (`created_by`/`updated_by`). Passa a ser possível depois desta change, mas fica para uma change própria.
- Documentação OpenAPI (springdoc).

## Capabilities

### New Capabilities
- `auth`: login com email e senha, emissão do access token, consulta dos próprios dados e troca da própria senha. É o novo módulo de negócio `auth` (pacote `com.manuelaalecio.educare_backend.auth`). Ele não tem entidade própria: usa o módulo `user` só pelo service de `application`, que continua sendo o dono das senhas.
- `security`: regras transversais de acesso à API, em `shared/security`: exigência do token em toda rota não pública, validação do token, formato das respostas `401`/`403`, a chave de assinatura obrigatória e o CORS. Segue o padrão da capability `persistence`, que também descreve uma parte de `shared`.

### Modified Capabilities
- `user`: a gestão de usuários passa a exigir role `ADMIN`; entram a proteção contra ficar sem `ADMIN` e a proibição de o `ADMIN` excluir a si mesmo; os cenários de listagem mudam, porque agora sempre existe pelo menos o `ADMIN` que faz a consulta.

A change atravessa três partes porque a autenticação depende das senhas guardadas pelo `user`, e as regras que fecham a API são transversais. O `auth` só chama o `UserService`, e `shared/security` não depende de nenhum módulo: ele define uma interface para consultar o usuário do token, que o `user` implementa.

## Impact

- **Código**:
  - novo módulo `auth/` (`api` e `application`);
  - `shared/security`: configuração do Spring Security, emissão e validação de JWT, CORS e respostas `401`/`403`;
  - `shared/error`: uma exceção base para erro de campo que não vem da Bean Validation (senha atual incorreta);
  - `user/`: `@PreAuthorize` no controller, novas operações no `UserService` para o `auth` (autenticar, consultar os próprios dados e trocar a própria senha) e as regras de proteção do `ADMIN`.
- **API**: novos endpoints em `/api/v1/auth`. Os endpoints de `/api/v1/users` passam a exigir token de `ADMIN` (**BREAKING** para quem os chamava sem token).
- **Banco**: nenhuma migration.
- **Dependências**: `spring-boot-starter-security` e `spring-boot-starter-oauth2-resource-server` (validação e assinatura de JWT com Nimbus), com os starters `-test` correspondentes. O `spring-security-crypto` explícito deixa de ser necessário, porque vem com o starter de segurança.
- **Configuração**: `EDUCARE_JWT_SECRET` e `EDUCARE_CORS_ALLOWED_ORIGINS` em `application-dev.yaml` (com padrão), `application-prod.yaml` (sem padrão) e no serviço `api` do `docker-compose.yaml`.
- **Testes**: os testes de cenário e `@WebMvcTest` existentes do `user` passam a enviar um token de `ADMIN`.
- **Documentação**: `openspec/config.yaml` (a seção Segurança deixa de ter o TODO do emissor) e CLAUDE.md (Armadilhas: novas variáveis e como os testes se autenticam).
