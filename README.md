# Educare Backend

API REST do **Educare**, sistema de gestão de uma ONG/instituição que atende crianças em situação de vulnerabilidade.

## O que é

O Educare é usado pelos **funcionários da instituição** para gerenciar o cadastro das crianças atendidas e de seus responsáveis. Este repositório é o **backend**: guarda os dados no banco e expõe as rotas consumidas pelo frontend, que fica em um repositório separado.

## Objetivo

Facilitar o dia a dia dos funcionários na gestão das crianças atendidas, com foco em:

- **Cadastro de crianças** e de seus **responsáveis**, com o vínculo entre eles.
- **Cadastro de usuários** (funcionários) com acesso autenticado.
- **Rematrícula**: durante a janela do período de rematrícula, o recadastro de cada criança parte dos dados já existentes, em vez de refazer tudo do zero.

Como o sistema trata dados pessoais de crianças, segue os cuidados da LGPD: só coleta o necessário, restringe o acesso a usuários autenticados e registra quem criou ou alterou cada informação.

> Projeto em fase inicial de desenvolvimento.

## Stack

- Java 21 e Spring Boot 4
- PostgreSQL 16, Spring Data JPA e Flyway (migrations)
- Gradle
- Testes com JUnit 5, Mockito, Testcontainers, JaCoCo (cobertura) e PIT (mutation testing)
- Docker e Docker Compose

## Como iniciar o projeto

### 1. Pré-requisitos

- **Java 21** (JDK). O Gradle não precisa ser instalado: o projeto usa o Gradle Wrapper (`./gradlew`).
- **Docker** com **Docker Compose**. É usado para subir o banco e também pelos testes.

Para conferir:

```bash
java -version
docker --version
docker compose version
```

### 2. Clonar o repositório

```bash
git clone git@github.com:manuelaalecio/educare-backend.git
cd educare-backend
```

### 3. Configurar a senha do banco

Crie um arquivo `.env` na raiz do projeto com a senha que o PostgreSQL vai usar:

```bash
echo "DB_PASSWORD=escolha_uma_senha" > .env
```

O `.env` não vai para o repositório (está no `.gitignore`).

### 4. Subir a aplicação

Escolha uma das duas formas.

**Opção A: tudo com Docker (mais simples)**

Sobe o banco e a API juntos:

```bash
docker compose up --build
```

**Opção B: banco no Docker e API pelo Gradle (melhor para desenvolver)**

Suba só o banco:

```bash
docker compose up -d db
```

Depois rode a API informando a senha do banco (a mesma do `.env`):

```bash
SPRING_DATASOURCE_PASSWORD=escolha_uma_senha ./gradlew bootRun
```

Sem profile ativo, a API sobe com o profile `dev`, que já aponta para o banco do Compose (`localhost:5432`, banco e usuário `app`). Na Opção A, o Compose ativa o profile `prod`, em que URL, usuário e senha do banco vêm só de variáveis de ambiente.

Na inicialização, o Flyway aplica as migrations pendentes de `src/main/resources/db/migration/`.

### 5. Verificar se está no ar

A API fica disponível em `http://localhost:8080`. Para confirmar:

```bash
curl http://localhost:8080/actuator/health
```

A resposta deve conter `"status":"UP"`.

> **Erro `password authentication failed`?** O PostgreSQL só define a senha na primeira vez que o volume do banco é criado. Se você mudou o `DB_PASSWORD` depois disso, use a senha antiga ou recrie o banco com `docker compose down -v` (isso **apaga os dados**) e suba de novo.

### 6. Parar

```bash
docker compose down        # para os containers e mantém os dados do banco
docker compose down -v     # para os containers e apaga os dados do banco
```

## Testes

Os testes sobem um PostgreSQL próprio com Testcontainers, então **o Docker precisa estar rodando**. Não é preciso subir o banco do Compose.

```bash
./gradlew build     # compila, roda todos os testes e verifica a cobertura
./gradlew test      # só os testes
./gradlew pitest    # mutation testing (fora do build padrão)
```

O build **falha se a cobertura ficar abaixo de 90%** de linhas ou de branches.

Relatórios gerados:

| Relatório | Caminho |
|---|---|
| Resultado dos testes | `build/reports/tests/test/index.html` |
| Cobertura (JaCoCo) | `build/reports/jacoco/test/html/index.html` |
| Mutation testing (PIT) | `build/reports/pitest/index.html` |

## Como o projeto é desenvolvido

O desenvolvimento segue **Spec-Driven Development (SDD)** com [OpenSpec](https://github.com/Fission-AI/OpenSpec), assistido por IA:

1. Cada funcionalidade começa como uma proposta (proposal, design, specs e tasks) em `openspec/changes/`.
2. Depois de implementada, suas specs passam para `openspec/specs/`, que descreve o comportamento atual do sistema.
3. A change concluída é arquivada em `openspec/changes/archive/`.

Onde encontrar as decisões do projeto:

| Arquivo | Conteúdo |
|---|---|
| `openspec/config.yaml` | visão do produto, glossário, padrões de API, persistência e segurança |
| `CLAUDE.md` | arquitetura, estrutura do código, convenções e estratégia de testes |
| `openspec/specs/` | especificação de cada funcionalidade |
