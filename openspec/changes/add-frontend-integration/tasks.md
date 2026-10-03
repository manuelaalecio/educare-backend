# Tasks

## 1. Geração e verificação do contrato

- [ ] 1.1 Em `application.yaml`, ligar `springdoc.writer-with-order-by-keys`, `springdoc.writer-with-default-pretty-printer` e `springdoc.override-with-generic-response: false` (D2, D3). No `OpenApiConfiguration`, declarar `servers: [{url: "/"}]`. Verificar que o `ApiDocsScenarioTest` e o `ApiDocsDisabledTest` continuam verdes
- [ ] 1.2 Criar `shared/config/ApiContractScenarioTest` (D1): gera `/v3/api-docs` com o springdoc ligado por `@TestPropertySource`, normaliza (chaves ordenadas, 2 espaços, `\n` final) e compara com `openapi/educare-api.json`, ou grava o arquivo quando `educare.apiContract.update=true`. A mensagem de falha contém `./gradlew updateApiContract`. Cobrir com testes os cenários "Contrato em dia", "Contrato ausente" e "Endpoint alterado sem atualizar o contrato" (comparando com um arquivo temporário alterado), com `@DisplayName` referenciando cada cenário
- [ ] 1.3 Adicionar ao `build.gradle` a task `updateApiContract` (tipo `Test`, filtrada para `ApiContractScenarioTest`, com `systemProperty 'educare.apiContract.update', 'true'`), sem repassar a property à task `test`. Rodar `./gradlew updateApiContract` duas vezes e verificar com `cmp` que os arquivos são idênticos (cenário "Geração determinística"). Commitar o primeiro `openapi/educare-api.json`
- [ ] 1.4 Testes de cenário de "Contrato sem dados de ambiente nem dados pessoais": o arquivo gerado (servidor em porta aleatória) não contém `localhost`, `127.0.0.1`, a porta nem o valor da chave de dev do JWT
- [ ] 1.5 Acrescentar `openapi/` ao `.dockerignore`, na seção "Documentação, planejamento e arquivos operacionais" (o contrato não é usado pelo `bootJar`), e verificar que `docker build .` continua passando

## 2. Respostas de sucesso e campos obrigatórios

- [ ] 2.1 Em `shared/config`, criar o `OpenApiCustomizer` de campos obrigatórios (D4): marca como `required` as propriedades dos schemas só de resposta, exceto os campos de record anotados com `@Nullable` (jspecify). Teste unitário do customizador com um schema de exemplo, um campo `@Nullable` e um schema usado em request (este fica intocado)
- [ ] 2.2 Conferir e, se preciso, anotar as operações de `auth` e `user` para que o status de sucesso esteja certo (`200`, `201` com header `Location` no `POST /api/v1/users`, `204` sem `content` nas trocas de senha e na exclusão). Regenerar o contrato. Testes de cenário de "Resposta do login", "Resposta dos próprios dados", "Operação sem corpo de resposta" e "Criação com Location", lendo o `openapi/educare-api.json` com JSON path, e verificação de que nenhum schema de resposta tem `password`, `createdBy` ou `updatedBy`

## 3. Respostas de erro como ProblemDetail

- [ ] 3.1 Registrar no `OpenApiConfiguration` os schemas `ProblemDetail` e `FieldError` (D3), e criar as meta-anotações `@BadRequestResponse`, `@ForbiddenResponse`, `@NotFoundResponse` e `@ConflictResponse` em `shared/config`. Teste de cenário de "Schema de ProblemDetail com erros de campo"
- [ ] 3.2 Criar o `OpenApiCustomizer` que adiciona `401` (`application/problem+json`, `$ref` para `ProblemDetail`) a toda operação com requisito de segurança. Teste unitário com uma operação protegida e outra pública
- [ ] 3.3 Anotar o `AuthController` (login: `400` e `401`; `me`: nada além do `401` global; troca de senha: `400`) e o `UserController` (`@ForbiddenResponse` na classe; `400`, `404` e `409` por operação, conforme a spec `user`). Regenerar o contrato. Testes de cenário de "Erros do login", "Erros de uma operação de ADMIN" e "Operação de qualquer usuário autenticado não declara 403". Rodar o teste de ArchUnit e verificar que `shared` continua sem depender de módulos

## 4. Documentação

- [ ] 4.1 README: seção "Contrato da API" (onde fica, `./gradlew updateApiContract`, quando o build falha, URL raw que o frontend consome). CLAUDE.md: armadilha sobre o contrato (regenerar a cada mudança de endpoint ou DTO, `@Nullable` em campos opcionais de resposta, meta-anotações de erro nos controllers) e linha na tabela de tipos de teste. `openspec/config.yaml`: topologia com o BFF (o navegador não chama a API; o servidor do frontend chama com o token) e o contrato em `openapi/educare-api.json`. Conferir que os três arquivos não se contradizem
- [ ] 4.2 README, seção "Deploy em produção": passos de HTTPS do D5 (porta 443 na security list e no firewall da VM, `SITE_ADDRESS=<ip-com-hifens>.sslip.io`, `EDUCARE_CORS_ALLOWED_ORIGINS` com o domínio da Vercel, `docker compose up -d`) e rollback para `:80`, com comandos compatíveis com o fish

## 5. HTTPS em produção (manual, depois do deploy desta change)

- [ ] 5.1 Executar os passos da 4.2 na VM. Verificar que `curl -sI http://<nome>.sslip.io/actuator/health` responde redirecionamento para `https://` e que `curl -s https://<nome>.sslip.io/actuator/health` responde `{"status":"UP"}` com certificado válido (sem `-k`)
- [ ] 5.2 Verificar que `curl -s -X POST https://<nome>.sslip.io/api/v1/auth/login -H 'Content-Type: application/json' -d '{}'` responde `400` com `application/problem+json`, confirmando que a API está acessível pelo HTTPS para o BFF

## 6. Verificação final

- [ ] 6.1 Rodar `./gradlew build` com o Docker ativo e verificar que termina verde, incluindo a verificação do contrato e o `jacocoTestCoverageVerification` (mínimo de 90% de linhas e de branches)
