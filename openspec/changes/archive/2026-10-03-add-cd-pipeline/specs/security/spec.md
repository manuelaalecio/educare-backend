# Spec Delta

## ADDED Requirements

### Requirement: Actuator restrito ao health, sem detalhes
O sistema SHALL expor pela web, entre os endpoints do Actuator, só o health (`/actuator/health`), em todos os profiles. A resposta do health SHALL conter só o status agregado (`"status":"UP"` quando a aplicação está saudável) e, opcionalmente, a lista `groups` com os nomes dos grupos de health disponíveis, sem componentes, detalhes nem informações de infraestrutura (banco, disco, versões), com ou sem token. Os grupos `liveness` e `readiness` SHALL estar disponíveis sem token em `/actuator/health/liveness` e `/actuator/health/readiness`, respondendo também só o status. Os demais endpoints do Actuator (por exemplo `/actuator/env`, `/actuator/info`, `/actuator/beans`, `/actuator/metrics`) e os caminhos de componentes do health (por exemplo `/actuator/health/db`) SHALL NOT existir: sem token respondem como qualquer rota inexistente (`401 Unauthorized`), e com um token válido, inclusive de `ADMIN`, respondem `404 Not Found` sem revelar conteúdo do endpoint.

#### Scenario: Health responde só o status
- **WHEN** um cliente envia `GET /actuator/health` sem o header `Authorization`, com a aplicação e o banco disponíveis
- **THEN** a resposta é `200 OK` com o corpo JSON contendo a propriedade `status` com o valor `UP` e, além dela, no máximo a lista `groups` com os nomes `liveness` e `readiness`, sem as propriedades `components` e `details`

#### Scenario: Health com token também não mostra detalhes
- **WHEN** um usuário com role `ADMIN` envia `GET /actuator/health` com um token válido
- **THEN** a resposta é `200 OK` com o mesmo corpo da resposta sem token: `status` com o valor `UP` e, no máximo, a lista `groups`, sem `components` e `details`

#### Scenario: Probes de liveness e readiness públicos
- **WHEN** um cliente envia `GET /actuator/health/liveness` e `GET /actuator/health/readiness` sem o header `Authorization`, com a aplicação pronta
- **THEN** cada resposta é `200 OK` com o corpo JSON contendo só a propriedade `status` com o valor `UP`

#### Scenario: Endpoint do Actuator não exposto, com token de ADMIN
- **WHEN** um usuário com role `ADMIN` envia `GET /actuator/env` com um token válido
- **THEN** a resposta é `404 Not Found` e o corpo não contém nenhuma propriedade ou variável de ambiente da aplicação

#### Scenario: Endpoint do Actuator não exposto, sem token
- **WHEN** um cliente envia `GET /actuator/info` sem o header `Authorization`
- **THEN** a resposta é `401 Unauthorized`, sem revelar se o endpoint existe

#### Scenario: Componente do health não exposto
- **WHEN** um usuário com role `ADMIN` envia `GET /actuator/health/db` com um token válido
- **THEN** a resposta é `404 Not Found` e o corpo não contém o status nem detalhes do banco
