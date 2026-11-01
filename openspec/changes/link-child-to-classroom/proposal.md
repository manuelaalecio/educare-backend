# Proposal

## Why

Toda criança atendida pertence a uma turma, mas o cadastro de crianças (`add-child-crud`) ainda não tem esse vínculo, e as turmas mostram sempre 0 crianças porque a contagem é provisória. Sem o vínculo, não dá para filtrar as crianças por turma nem saber por quais turmas uma criança já passou, e o frontend não consegue mostrar contagens reais.

## What Changes

- **Vínculo obrigatório criança–turma, com histórico**: cada criança está em exatamente uma turma ativa; o vínculo tem data de início e, quando a criança sai da turma, data de fim.
  - **BREAKING** (em relação a `add-child-crud`, que ainda não foi para produção): `POST` e `PUT /api/v1/children` passam a exigir `classroomId`. Ausente ou de turma inexistente → `400` com `errors.classroomId` ("Turma não encontrada" para a inexistente).
  - Criar a criança abre o vínculo com início = hoje. No `PUT`, a mesma turma não mexe no vínculo; outra turma encerra o vínculo atual (fim = hoje) e abre um novo (início = hoje). As datas são as de hoje no fuso `America/Sao_Paulo`; não há data retroativa.
  - Excluir a criança apaga o histórico de vínculos dela.
- **Turma atual nas respostas de criança**: `classroom: { id, name }` na criação, alteração, consulta por id e em cada item da listagem.
- **Filtro por turma na listagem**: `GET /api/v1/children?classroomId=...`, combinável com a busca por nome; considera só a turma atual.
- **Histórico de turmas**: `GET /api/v1/children/{id}/classrooms` devolve as turmas da criança (turma, entrada e saída), da mais recente para a mais antiga.
- **Contagens reais nas turmas**: `studentCount`, `maleCount` e `femaleCount` calculados pelos vínculos ativos, numa única consulta para a lista inteira; a contagem provisória (sempre 0) é removida.
- **Exclusão de turma**: continua `409` com crianças ativas, e passa a ser `409` também para turma que só tem histórico (para preservar o histórico), cada uma com a sua mensagem.
- **Leitura de turmas para qualquer usuário autenticado**: `GET /api/v1/classrooms` e `GET /api/v1/classrooms/{id}` liberados para `EDUCADOR` (o formulário de criança precisa da lista de turmas); criar, renomear e excluir continuam só `COORDENADOR`.
- **Mensagens da API**: "alunos" vira "crianças" nas mensagens de turma.
- Contrato `openapi/educare-api.json` atualizado.

## Non-goals

- Escolher a data da troca de turma (é sempre hoje) ou corrigir o histórico à mão.
- Renomear `StudentCounts`, `studentCount`, `maleCount` e `femaleCount` no código e no contrato.
- Matrícula e rematrícula como processo (ano letivo, documentos, status de matrícula).
- Restringir o que o `EDUCADOR` vê às crianças da própria turma (todo usuário vê todas, como hoje).
- Arquivar turma em vez de excluir.
- Ordenar ou filtrar a listagem por turma além do filtro por `classroomId` (sem `sort=classroom`).
- Responsáveis e o vínculo criança–responsável.

## Capabilities

### New Capabilities
<!-- Nenhuma: o vínculo pertence ao módulo `child` (o lado que muda de turma é a criança). -->

### Modified Capabilities
- `child`: turma obrigatória na criação e na alteração, troca de turma com histórico, turma atual nas respostas, filtro da listagem por turma, endpoint de histórico e exclusão da criança levando o histórico. Pressupõe `add-child-crud` sincronizada em `openspec/specs/child/` antes do arquivamento desta change.
- `classroom`: contagens reais pelos vínculos ativos, exclusão recusada também para turma com histórico, leitura liberada para qualquer usuário autenticado e mensagens com "crianças".

A mudança atravessa os dois módulos porque o vínculo liga os dois: o `child` é dono do vínculo e implementa a contagem que o `classroom` consome pela interface que ele mesmo já declara (`ClassroomStudentCounter`), e consulta a existência e o nome das turmas por um serviço de `application` do `classroom`. O `classroom` continua sem depender do `child`.

## Impact

- **Dependências de planejamento**: exige `add-child-crud` aplicada (tabela `children`, módulo `child`). As duas vão juntas para produção: nenhuma imagem publicada pode ter `add-child-crud` sem esta change.
- **Código**:
  - `child`: nova entidade do vínculo e repositório em `domain`, implementação da contagem em `application`, `ChildService`/`ChildController`/DTOs alterados, novo endpoint de histórico;
  - `classroom`: novo serviço de consulta de turmas em `application` para os outros módulos, `ClassroomService.delete` com a regra do histórico, nova exceção de conflito, mensagem de `ClassroomHasStudentsException`, `@PreAuthorize` movido da classe para os métodos de escrita do `ClassroomController`, remoção de `NoEnrollmentStudentCounter`;
  - testes de `child` e `classroom` existentes ajustados (criança passa a precisar de turma; `EDUCADOR` passa a ler turmas).
- **API**: `classroomId` obrigatório em `POST`/`PUT /api/v1/children`; `classroom` nas respostas; parâmetro `classroomId` na listagem; novo `GET /api/v1/children/{id}/classrooms`; `GET` de turmas sem `403` para `EDUCADOR`; novo `409` na exclusão de turma.
- **Contrato**: `openapi/educare-api.json` regenerado; `ApiContractContentTest` atualizado (o `403` sai das operações de leitura de turma).
- **Banco**: nova migration `V9__create_child_classrooms.sql` (tabela `child_classrooms`, FKs, `CHECK` das datas e índices parciais).
- **Dependências de bibliotecas e infraestrutura**: nenhuma nova; nenhuma variável de ambiente nova.
