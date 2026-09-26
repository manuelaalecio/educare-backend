# Proposal

## Why

Os funcionários da instituição são os únicos usuários do Educare, e hoje não há onde cadastrá-los. O cadastro de usuários é o primeiro módulo de negócio e a base da autenticação (próxima change) e da trilha de quem criou ou alterou cada registro, exigida pela LGPD.

## What Changes

- Novo módulo `user` com o CRUD completo de usuários, exposto em `/api/v1/users`:
  - criar usuário (nome, login, senha e role);
  - listar usuários com paginação;
  - buscar usuário por id;
  - alterar nome, login e role;
  - alterar a senha, num endpoint separado;
  - excluir usuário (exclusão definitiva).
- Um usuário tem `id` (UUID), `name`, `login` (um email), senha, `role` e `createdAt`/`updatedAt` (herdados de `BaseEntity`).
- Cada usuário tem exatamente uma role, `ADMIN` ou `USER`. O padrão é `USER` quando a role não é informada na criação.
- Regras de validação:
  - login só aceita email (formato `nome@dominio.tld`, sem espaços e com ponto no domínio), com até 254 caracteres, único sem diferenciar maiúsculas de minúsculas e gravado em minúsculas;
  - senha com 8 a 72 caracteres, guardada só como hash BCrypt;
  - nome obrigatório, com até 150 caracteres;
  - role só aceita `ADMIN` ou `USER`.
- A senha, e também o hash dela, nunca aparece em respostas da API nem em logs. O email do login também não aparece em mensagens de erro nem em logs, por ser dado pessoal (LGPD).
- Administrador inicial criado por migration: usuário `Administrador`, login `admin@educare.org`, role `ADMIN`. A senha vem da variável de ambiente `EDUCARE_ADMIN_PASSWORD`, que é obrigatória em produção; no `dev`, o padrão é `educare123`.
- Primeira peça de tratamento de erros em `shared/error`: exceções base e um `@RestControllerAdvice` global que converte erros de validação, recurso não encontrado e conflito em `ProblemDetail` (RFC 9457).
- Primeiras regras ArchUnit em `architecture/`, cobrindo as dependências entre camadas e entre módulos definidas no CLAUDE.md. Até agora não existia nenhum módulo com camadas para verificar.
- Novas migrations Flyway:
  - `V2__create_users.sql`, com a tabela;
  - `V3__seed_admin_user.sql`, com o administrador inicial.

## Non-goals

- Autenticação e autorização (login, emissão de JWT, Spring Security protegendo rotas). Vêm na próxima change. **Até lá, os endpoints de usuários ficam abertos**, e esta change não deve ir para produção exposta sem a autenticação.
- Troca de senha exigindo a senha atual, "esqueci minha senha" e políticas de expiração ou complexidade além do tamanho.
- Controle de acesso por role (por exemplo, só `ADMIN` gerenciar usuários). A role só é guardada e exposta aqui; a verificação dela vem com a autenticação.
- Forçar a troca da senha do administrador inicial no primeiro acesso.
- Roles além de `ADMIN` e `USER`, ou mais de uma role por usuário.
- Confirmação ou verificação do email (nenhum email é enviado).
- Desativação ou exclusão lógica (soft delete) e bloqueio da exclusão do último usuário.
- Autoria dos registros (`created_by`/`updated_by`), que depende da autenticação.
- Busca ou filtro na listagem, além de paginação e ordenação.
- Documentação OpenAPI (springdoc) e configuração de CORS.

## Capabilities

### New Capabilities
- `user`: cadastro (CRUD) dos usuários do sistema (funcionários da instituição), com login por email único, role (`ADMIN`/`USER`), senha guardada como hash e o administrador inicial. É o módulo de negócio `user` (pacote `com.manuelaalecio.educare_backend.user`). As peças novas em `shared/error` e `architecture/` são transversais e só entram aqui porque este é o primeiro módulo que precisa delas; elas não dependem do módulo `user`.

### Modified Capabilities
<!-- Nenhuma: a capability `persistence` é usada como está (BaseEntity, migrations), sem mudança de requisitos. -->

## Impact

- **Código**:
  - novo pacote `user/` com as camadas `api`, `application` e `domain`;
  - novos `shared/error` (exceções base e handler global), `shared/security` (bean `PasswordEncoder`) e `shared/validation` (constraint de tamanho em bytes).
- **API**: novos endpoints em `/api/v1/users` (detalhados no design).
- **Banco**:
  - nova tabela `users`, com índice único em `login` e CHECK na `role`, pela migration `V2__create_users.sql`;
  - a extensão `pgcrypto` e o administrador inicial, pela migration `V3__seed_admin_user.sql`.
- **Dependências**:
  - `spring-security-crypto`, só para o BCrypt, sem o starter de segurança;
  - `archunit-junit5`, só nos testes.
- **Configuração**:
  - placeholder do Flyway para a senha do admin em `application-dev.yaml` (com padrão) e em `application-prod.yaml` (sem padrão);
  - `EDUCARE_ADMIN_PASSWORD` no serviço `api` do `docker-compose.yaml`.
- **Documentação**: CLAUDE.md (Armadilhas) com a nova variável de ambiente e o admin que existe em todo banco de teste.
