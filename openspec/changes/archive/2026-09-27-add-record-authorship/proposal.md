# Proposal

## Why

A LGPD pede trilha de quem criou ou alterou cada registro, e o Educare vai guardar dados pessoais e de saúde de crianças. Hoje os registros só têm `createdAt`/`updatedAt`. A autoria precisa existir antes do cadastro de crianças (`add-child-crud`), para que nenhuma criança seja gravada sem ela.

## What Changes

- Todo registro de negócio passa a guardar quem o criou (`createdBy`) e quem o alterou por último (`updatedBy`), como o id do usuário autenticado da requisição, preenchidos automaticamente pelo sistema junto com `createdAt`/`updatedAt`.
- O autor da criação não muda depois da primeira gravação; o autor da última alteração muda a cada alteração gravada.
- Registros gravados sem usuário autenticado (hoje, só o administrador inicial criado por migration) ficam sem autoria.
- Sem chave estrangeira para `users`: excluir um usuário não apaga nem bloqueia a autoria dos registros dele; eles continuam com o id do usuário excluído.
- A autoria não aparece nas respostas da API; é só trilha no banco.
- A tabela `users` ganha as colunas de autoria, por uma nova migration.
- A convenção de schema passa a incluir `created_by` e `updated_by` em toda tabela de negócio (CLAUDE.md e `openspec/config.yaml`).
- Apoio de teste para gravar entidades como um usuário autenticado em testes sem requisição HTTP (`@DataJpaTest`, testes de service).

## Non-goals

- Expor a autoria na API (por exemplo, "cadastrado por" no frontend).
- Registro de exclusões: a exclusão continua definitiva e não deixa rastro de quem excluiu.
- Histórico de versões (o que mudou em cada alteração) e log de acessos/leituras.
- Chave estrangeira entre a autoria e `users`.
- Preencher a autoria de registros já existentes (o administrador inicial continua sem autoria).

## Capabilities

### New Capabilities
<!-- Nenhuma. -->

### Modified Capabilities
- `persistence`: novo requirement de autoria dos registros de negócio (quem criou e quem alterou por último), ao lado do identificador e das datas de criação e alteração que a capability já garante. A mudança é transversal (`shared/persistence` e `shared/security`) porque vale para todas as entidades; o único módulo tocado é o `user`, e só pela migration que adiciona as colunas à tabela `users`.

## Impact

- **Código**: `shared/persistence/BaseEntity` (campos `createdBy`/`updatedBy`), `JpaAuditingConfiguration` (auditor), novo auditor em `shared/security` que lê o usuário autenticado. Nenhuma mudança nos módulos `user` e `auth` além do schema.
- **API**: nenhuma mudança visível.
- **Banco**: nova migration `V4__add_authorship_to_users.sql`. A `add-child-crud`, que planejava a `V4__create_children.sql`, passa a usar a `V5`.
- **Testes**: tabela de teste `test_audited_entity` (migration repetível de teste) com as novas colunas; novo apoio em `shared/testsupport` para executar como um usuário autenticado.
- **Documentação**: CLAUDE.md (convenção de schema em Armadilhas) e `openspec/config.yaml` (Persistência).
- **Dependências**: nenhuma nova.
