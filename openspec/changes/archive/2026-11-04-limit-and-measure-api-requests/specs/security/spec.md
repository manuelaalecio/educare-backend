# Spec Delta

## ADDED Requirements

### Requirement: Limite de requisições por usuário autenticado
O sistema SHALL contar, por usuário, as requisições autenticadas à API, qualquer que seja a rota, o método ou o resultado (inclusive `400`, `403` e `404`). Quando o usuário já tiver 120 requisições contadas no último minuto (janela deslizante, medida pelo relógio da aplicação: uma requisição deixa de contar exatamente 1 minuto depois de recebida), o sistema SHALL recusar a requisição seguinte desse usuário com `429 Too Many Requests`, `Content-Type: application/problem+json`, um `ProblemDetail` com o `detail` `Muitas requisições. Aguarde um instante.` e sem `errors`, e o header `Retry-After` com o número inteiro de segundos (no mínimo 1, arredondado para cima) até a requisição mais antiga da janela deixar de contar. A requisição recusada SHALL NOT executar a operação pedida e SHALL NOT entrar na contagem. A contagem é por usuário, não por token: um token novo do mesmo usuário continua limitado, e a contagem de um usuário SHALL NOT afetar outro. As rotas públicas (`POST /api/v1/auth/login`, o health do Actuator e, no `dev`, a documentação) e as requisições recusadas com `401` SHALL NOT contar nem ser recusadas por este limite. O limite (120) e a janela (1 minuto) são os valores padrão, configuráveis sem mudar o código. A contagem SHALL ser mantida só em memória: reiniciar a aplicação a zera.

#### Scenario: Requisições dentro do limite
- **WHEN** a usuária `ana.souza@educare.org` envia 120 vezes `GET /api/v1/auth/me` com um token válido, todas às `10:00:00` de `2026-03-01` (UTC)
- **THEN** todas as 120 respostas são `200 OK`

#### Scenario: Requisição acima do limite
- **WHEN** a usuária `ana.souza@educare.org` envia 120 requisições `GET /api/v1/auth/me` às `10:00:00` de `2026-03-01` (UTC) e mais uma às `10:00:40`
- **THEN** a 121ª resposta é `429 Too Many Requests` com `Content-Type: application/problem+json`, `"status": 429`, `"detail": "Muitas requisições. Aguarde um instante."`, sem `errors`, e o header `Retry-After: 20`

#### Scenario: Requisição recusada não executa a operação
- **WHEN** a coordenadora `ana.souza@educare.org` já fez 120 requisições no último minuto e envia `POST /api/v1/classrooms` com `{"name": "Turma Girassol"}`
- **THEN** a resposta é `429 Too Many Requests`, e `GET /api/v1/classrooms`, enviado depois que a janela libera, não lista nenhuma turma `Turma Girassol`

#### Scenario: Janela deslizante libera o usuário
- **WHEN** a usuária `ana.souza@educare.org` envia 1 requisição `GET /api/v1/auth/me` às `10:00:00` e 119 às `10:00:30` de `2026-03-01` (UTC), e depois uma às `10:00:59` e outra às `10:01:00`
- **THEN** a requisição das `10:00:59` responde `429 Too Many Requests` com `Retry-After: 1`, e a das `10:01:00` responde `200 OK`

#### Scenario: Requisições recusadas não prolongam o bloqueio
- **WHEN** a usuária `ana.souza@educare.org` envia 120 requisições `GET /api/v1/auth/me` às `10:00:00` de `2026-03-01` (UTC), mais 50 às `10:00:30` e uma às `10:01:00`
- **THEN** as 50 requisições das `10:00:30` respondem `429 Too Many Requests`, e a das `10:01:00` responde `200 OK`

#### Scenario: Erros também contam
- **WHEN** a usuária `ana.souza@educare.org` envia 120 requisições `GET /api/v1/children/00000000-0000-0000-0000-000000000000` (criança inexistente) às `10:00:00` de `2026-03-01` (UTC) e em seguida `GET /api/v1/auth/me`
- **THEN** as 120 primeiras respostas são `404 Not Found`, e a última é `429 Too Many Requests`

#### Scenario: Limite não afeta outro usuário
- **WHEN** a usuária `ana.souza@educare.org` está bloqueada depois de 120 requisições no último minuto, e o usuário `bruno.lima@educare.org` envia `GET /api/v1/auth/me` com o token dele
- **THEN** a resposta para Bruno é `200 OK`

#### Scenario: Token novo do mesmo usuário continua limitado
- **WHEN** a usuária `ana.souza@educare.org`, com senha `segredo123`, está bloqueada depois de 120 requisições no último minuto, envia `POST /api/v1/auth/login` com `{"login": "ana.souza@educare.org", "password": "segredo123"}` e usa o token novo em `GET /api/v1/auth/me`
- **THEN** o login responde `200 OK` com um token, e o `GET /api/v1/auth/me` responde `429 Too Many Requests`

#### Scenario: Rotas públicas fora do limite
- **WHEN** a usuária `ana.souza@educare.org` está bloqueada depois de 120 requisições no último minuto e envia `GET /actuator/health` com o token dela
- **THEN** a resposta é `200 OK`

#### Scenario: Requisição sem token responde 401, não 429
- **WHEN** a usuária `ana.souza@educare.org` está bloqueada depois de 120 requisições no último minuto, e um cliente envia `GET /api/v1/auth/me` sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized`
