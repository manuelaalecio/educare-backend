# Proposal

## Why

A instituição organiza as crianças em turmas, e a matrícula e os gráficos do Início ("Alunos por turma") dependem delas. Hoje a API não tem onde cadastrar turmas, e a aba Configurações › Turmas do frontend, especificada na tarefa [CRUD de turmas em Configurações](https://app.clickup.com/t/1dfh60ph06m), precisa dessa API. Esta change cobre só a parte do backend, a [API de turmas](https://app.clickup.com/t/1dfh60ph071).

## What Changes

- Novo módulo `classroom` com o CRUD de turmas, exposto em `/api/v1/classrooms` e restrito a `ADMIN`:
  - criar turma informando só o nome;
  - listar todas as turmas em ordem alfabética, sem paginação;
  - buscar turma por id;
  - renomear turma;
  - excluir turma (exclusão definitiva), recusada com `409 Conflict` quando a turma tem alunos.
- Dados da turma: `id` (UUID), `name`, `createdAt` e `updatedAt`. A resposta traz também as contagens `studentCount`, `maleCount` e `femaleCount`.
- Regras do nome:
  - obrigatório, gravado sem espaços nas pontas, com 1 a 60 caracteres contados depois de tirar esses espaços;
  - único sem diferenciar maiúsculas e acentos ("Turma 1", "turma 1" e "Túrma 1" colidem), com `409 Conflict` se repetir.
- Contagens de alunos: enquanto não existir o vínculo entre criança e turma (matrícula), são sempre `0` e toda turma pode ser excluída. O módulo já tem o ponto de extensão que a matrícula vai implementar para preencher as contagens e bloquear a exclusão.
- Autoria: cada turma guarda quem a criou e quem a alterou por último, sem expor isso na API.
- Nova migration Flyway com a tabela `classrooms`, a extensão `unaccent` e o índice único do nome sem diferenciar maiúsculas e acentos.
- Glossário: Turma → `Classroom`.

## Non-goals

- Vínculo entre criança e turma (matrícula) e as contagens reais de alunos.
- Frontend: a aba Configurações › Turmas é a [tarefa do front](https://app.clickup.com/t/1dfh60ph072), em outro repositório.
- Acesso de `USER` às turmas (ex.: para o menu /turmas ou para a matrícula). Fica para quando a matrícula ou essa tela existirem.
- Ano letivo, turno, faixa etária, capacidade ou professor da turma. Se a turma passar a ter ano letivo, a regra de nome único muda numa change futura.
- Paginação, busca e ordenação escolhida pelo cliente na listagem: a escola tem poucas turmas.
- Ordenação "natural" de números no nome ("Turma 2" antes de "Turma 10"): a ordem é alfabética simples.
- Exclusão lógica (soft delete) e histórico de alterações.
- Alteração parcial (`PATCH`).

## Capabilities

### New Capabilities
- `classroom`: cadastro (CRUD) das turmas da instituição, com nome único sem diferenciar maiúsculas e acentos, contagens de alunos na resposta, exclusão bloqueada para turma com alunos, autoria dos registros e acesso só de `ADMIN`. É o módulo de negócio `classroom` (pacote `com.manuelaalecio.educare_backend.classroom`) e não depende de outros módulos de negócio. A matrícula vai depender dele, e não o contrário.

### Modified Capabilities
<!-- Nenhuma. `security` (token obrigatório, 401/403 em ProblemDetail), `persistence` (BaseEntity, migrations, autoria) e `api-contract` (requisitos gerais de toda operação) são usadas como estão; as novas operações só passam a constar no contrato. -->

## Impact

- **Código**: novo pacote `classroom/` com as camadas `api`, `application` e `domain`. Nenhuma mudança em `shared` nem nos outros módulos.
- **API**: novos endpoints em `/api/v1/classrooms` (detalhados no design) e `openapi/educare-api.json` regenerado com eles. O frontend gera os tipos a partir desse arquivo.
- **Banco**: nova migration com o próximo número livre (hoje `V6__create_classrooms.sql`), com `CREATE EXTENSION IF NOT EXISTS unaccent`, uma função imutável que envolve o `unaccent` e a tabela `classrooms` com o índice único. Nenhuma migration existente é editada.
- **Relação com `add-child-crud`** (proposta, ainda não implementada): as duas changes criam a extensão `unaccent` com `IF NOT EXISTS` e podem ser aplicadas em qualquer ordem. A que for implementada depois usa o próximo número de migration livre. A `V5` planejada em `add-child-crud` já está ocupada por `V5__add_password_changed_at_to_users.sql`.
- **Dependências de bibliotecas e infraestrutura**: nenhuma nova. Nenhuma variável de ambiente nova; no deploy, só a migration roda.
- **Documentação**: nenhuma mudança no CLAUDE.md esperada (o módulo segue a estrutura já descrita). O glossário do `openspec/config.yaml` ganha Turma → `Classroom`.
