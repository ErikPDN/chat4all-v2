# Chat4All v2 - Unified Messaging Platform

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/ErikPDN/chat4all-v2)
[![Version](https://img.shields.io/badge/version-v1.0.0-blue)](https://github.com/ErikPDN/chat4all-v2/releases/tag/v1.0.0)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Plataforma de mensageria unificada de alta escala que roteia mensagens entre clientes internos e múltiplas plataformas externas (WhatsApp, Instagram, Telegram) com garantias estritas de entrega e ordenação.

## Arquitetura

```
┌─────────────┐
│ API Gateway │ ← REST API / WebSocket
└──────┬──────┘
       │
   ┌───┴────────────────────────────────────┐
   │                                        │
   ▼                                        ▼
┌──────────────┐                    ┌──────────────┐
│   Message    │────► Kafka ────►   │    Router    │
│   Service    │                    │   Service    │
└──────┬───────┘                    └──────┬───────┘
       │                                   │
       │                         ┌─────────┼──────────┬─────────────┐
       │                         │         │          │             │
       ▼                         ▼         ▼          ▼             ▼
┌──────────────┐          ┌─────────────┐ │    ┌─────────┐   ┌─────────┐
│  PostgreSQL  │          │  WhatsApp   │ │    │Telegram │   │Instagram│
│   (Users,    │          │  Connector  │ │    │Connector│   │Connector│
│  Identities, │          └─────────────┘ │    └─────────┘   └─────────┘
│   Audit)     │                          │
└──────────────┘                          ▼
       │                           ┌──────────────┐
       │                           │ File Service │
       ▼                           └──────┬───────┘
┌──────────────┐                          │
│   MongoDB    │                          ▼
│ (Messages,   │◄───────────────── ┌──────────────┐
│Conversations)│                   │    MinIO     │
└──────────────┘                   │ (S3 Storage) │
                                   └──────────────┘
```

## Funcionalidades Principais

- **Mensageria Bidirecional**: Envio e recebimento de mensagens através de WhatsApp, Telegram e Instagram
- **Anexos de Arquivos**: Suporte para imagens, documentos e vídeos até 2GB via MinIO/S3
- **Conversas em Grupo**: Mensagens multi-participante com até 100 membros
- **Mapeamento de Identidades**: Vincula múltiplas contas de plataformas a perfis unificados de usuário
- **Verificação de Identidade**: Sistema OTP e verificação manual para canais de alta segurança
- **Auditoria Imutável**: Log completo de operações com retenção de 7 anos (compliance)
- **Sugestão Inteligente**: Algoritmo de matching de usuários usando Levenshtein distance
- **Alta Disponibilidade**: SLA 99.95% com failover automático
- **Performance em Tempo Real**: <500ms resposta API, <5s entrega externa
- **Observabilidade Completa**: Logs estruturados, métricas Prometheus, tracing distribuído

## Stack Tecnológica

- **Linguagem**: Java 21 LTS (Virtual Threads)
- **Framework**: Spring Boot 3.4+, Spring Cloud Gateway
- **Message Broker**: Apache Kafka 3.6+
- **Banco de Dados Principal**: PostgreSQL 16+ (usuários, identidades, auditoria)
- **Armazenamento de Mensagens**: MongoDB 7+ (mensagens, conversas)
- **Cache**: Redis 7+ (sessões, rate limiting, idempotência)
- **Armazenamento de Objetos**: MinIO (compatível S3)
- **Observabilidade**: Micrometer, OpenTelemetry, Prometheus, Grafana, Jaeger
- **Infraestrutura**: Docker Compose (dev), Kubernetes (prod)

## Início Rápido

### Pré-requisitos

- JDK 21 ou superior
- Maven 3.9+
- Docker & Docker Compose

### Desenvolvimento Local

1. **Clone o repositório**:
   ```bash
   git clone https://github.com/ErikPDN/chat4all-v2.git
   cd chat4all-v2
   ```

2. **Inicie os serviços de infraestrutura**:
   ```bash
   docker-compose up -d
   ```

   Isso inicia: Kafka, PostgreSQL, MongoDB, Redis, MinIO, Prometheus, Grafana, Jaeger

   **Credenciais Padrão**:
   - **MongoDB**: `chat4all` / `chat4all_dev_password`
   - **PostgreSQL**: `chat4all` / `chat4all_dev_password`
   - **Redis**: `chat4all_dev_password`
   - **MinIO**: `minioadmin` / `minioadmin`

3. **Compile todos os módulos**:
   ```bash
   mvn clean install -DskipTests
   ```

4. **Execute os serviços**:
   ```bash
   # Todos os serviços via Docker Compose (recomendado)
   docker-compose up -d

   # Aguarde 30 segundos para inicialização completa
   sleep 30

   # Verifique status dos serviços
   docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
   ```

   **Serviços disponíveis**:
   - API Gateway: http://localhost:8080
   - Message Service: http://localhost:8081
   - User Service: http://localhost:8083
   - File Service: http://localhost:8084

5. **Envie uma mensagem de teste**:
   ```bash
   curl -X POST http://localhost:8081/api/messages \
     -H "Content-Type: application/json" \
     -d '{
       "conversationId": "conv-test-001",
       "senderId": "user-001",
       "content": "Hello from Chat4All!",
       "channel": "WHATSAPP"
     }'
   ```

   **Resposta Esperada** (HTTP 202 Accepted):
   ```json
   {
     "messageId": "3d63cf3b-466d-46c5-9efe-2569b98a8915",
     "conversationId": "conv-test-001",
     "status": "PENDING",
     "acceptedAt": "2025-12-03T21:47:49.292Z",
     "statusUrl": "/api/messages/3d63cf3b-466d-46c5-9efe-2569b98a8915/status"
   }
   ```

6. **Recupere mensagens da conversa**:
   ```bash
   curl http://localhost:8081/api/v1/conversations/conv-test-001/messages | jq
   ```

   **Resposta Esperada** (HTTP 200 OK):
   ```json
   {
     "conversationId": "conv-test-001",
     "messages": [
       {
         "messageId": "3d63cf3b-466d-46c5-9efe-2569b98a8915",
         "conversationId": "conv-test-001",
         "senderId": "user-001",
         "content": "Hello from Chat4All!",
         "channel": "WHATSAPP",
         "status": "DELIVERED",
         "timestamp": "2025-12-03T21:47:49.291Z"
       }
     ],
     "nextCursor": null,
     "hasMore": false,
     "count": 1
   }
   ```

   **Opções de paginação**:
   ```bash
   # Limitar resultados
   curl "http://localhost:8081/api/v1/conversations/conv-test-001/messages?limit=10"

   # Paginação baseada em cursor
   curl "http://localhost:8081/api/v1/conversations/conv-test-001/messages?before=2025-12-03T21:47:49.291Z&limit=50"
   ```

7. **Acesse as ferramentas de observabilidade**:
   - **Grafana**: http://localhost:3000 (admin/admin)
   - **Prometheus**: http://localhost:9090
   - **Jaeger**: http://localhost:16686

## Testes de Integração

Execute o script de testes automatizado:

```bash
./test-remaining-tasks.sh
```

**Testes executados**:
- [x] Health check de todos os serviços (user, message, file, gateway)
- [x] Verificação do JAR compilado com novo código
- [x] Validação do schema da tabela `audit_logs`
- [x] Validação da coluna `verified` em `external_identities`
- [x] Logs de startup dos serviços

**Resultado esperado**: 9/9 testes PASSANDO ✓

## Estrutura do Projeto

```
chat4all-v2/
├── services/
│   ├── api-gateway/           # Spring Cloud Gateway (ponto de entrada da API)
│   ├── message-service/       # Processamento central de mensagens
│   ├── router-service/        # Workers de roteamento de mensagens
│   ├── user-service/          # Gerenciamento de usuários e identidades
│   ├── file-service/          # Upload e armazenamento de arquivos
│   └── connectors/
│       ├── whatsapp-connector/
│       ├── telegram-connector/
│       └── instagram-connector/
├── shared/
│   ├── common-domain/         # DTOs e eventos compartilhados
│   ├── connector-sdk/         # Interface MessageConnector
│   └── observability/         # Logging, métricas, tracing
├── infrastructure/
│   ├── kafka/                 # Configuração de tópicos Kafka
│   ├── mongodb/               # Scripts de inicialização MongoDB
│   └── docker-compose.yml     # Stack de desenvolvimento local
├── docs/
│   ├── REMAINING_TASKS_COMPLETION.md  # Documentação de implementação
│   └── STATUS_UPDATE_IMPLEMENTATION.md
└── specs/
    └── 001-unified-messaging-platform/
        ├── spec.md            # Especificação de funcionalidades
        ├── plan.md            # Plano técnico
        ├── data-model.md      # Schemas do banco de dados
        ├── quickstart.md      # Guia de integração
        ├── research.md        # Decisões técnicas
        ├── tasks.md           # Tarefas de implementação
        ├── checklists/        # Checklists de qualidade
        └── contracts/         # Especificações OpenAPI
```

## Fluxo de Desenvolvimento

1. **Desenvolvimento de Funcionalidades**: Siga as tarefas em `specs/001-unified-messaging-platform/tasks.md`
2. **Testes**: Execute `mvn test` para testes unitários, veja `quickstart.md` para testes de integração
3. **Qualidade de Código**: Pre-commit hooks executam linting e formatação
4. **Observabilidade**: Verifique os dashboards do Grafana para saúde dos serviços

## Implementações Recentes

### v1.0.0 - Tarefas Fundamentais Completadas

**User Service - Funcionalidades Implementadas**:

1. **VerificationService** (T089 - FR-034):
   - `initiateVerification()` - Gera token OTP de 6 dígitos
   - `completeVerification()` - Valida token e marca identidade como verificada
   - `manualVerification()` - Verificação manual por admin (bypass)
   - `revokeVerification()` - Revoga verificação em caso de incidente de segurança
   - `isVerified()` - Verifica status de verificação de identidade

2. **AuditService** (T090 - FR-035):
   - Log imutável de operações de identidade para compliance
   - Transação `REQUIRES_NEW` (sobrevive a rollbacks da transação pai)
   - 9 métodos especializados de auditoria
   - Tabela `audit_logs` com retenção de 7 anos
   - Índices otimizados para consultas por timestamp, ação e entidade

3. **IdentityMappingService.suggestMatches()** (T092 - FR-033):
   - Algoritmo de matching inteligente de usuários
   - Scoring: Email exato (100pts), Telefone exato (95pts), Nome similar (50-80pts via Levenshtein)
   - Bônus de plataforma: +10-30pts para sobreposição de canais
   - Threshold de confiança ≥60, máximo 5 resultados, ordenados por score
   - Suporte para normalização de telefone e fuzzy matching de nomes

## Configuração de Ambiente

Variáveis de ambiente podem ser configuradas em `.env` ou passadas diretamente:

```bash
# Banco de Dados PostgreSQL
DB_POSTGRES_URL=jdbc:postgresql://localhost:5432/chat4all
DB_POSTGRES_USER=chat4all
DB_POSTGRES_PASSWORD=chat4all_dev_password

# Banco de Dados MongoDB
DB_MONGODB_URI=mongodb://localhost:27017/chat4all
DB_MONGODB_USER=chat4all
DB_MONGODB_PASSWORD=chat4all_dev_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=chat4all_dev_password

# S3 (MinIO)
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin

# Observabilidade
OTEL_EXPORTER_JAEGER_ENDPOINT=http://localhost:14250
```

## Demonstração de Failover

### Testes de Resiliência e Recuperação Automática

Execute a demonstração funcional de failover para validar a recuperação automática do sistema:

```bash
# Executar demonstração completa de failover
./run-failover-demonstration.sh

# Visualizar relatório gerado
cat logs/failover-tests/FAILOVER_DEMONSTRATION_*.md
```

**O que é testado**:
- ✅ Recuperação automática do Message Service
- ✅ Recuperação automática do Router Service  
- ✅ Recuperação automática do Kafka
- ✅ Zero message loss (preservação de dados)
- ✅ Tempos de recuperação < 30 segundos

**Documentação completa**: [docs/FAILOVER_DEMONSTRATION.md](docs/FAILOVER_DEMONSTRATION.md)

## Solução de Problemas

### Resetar Infraestrutura

Se encontrar problemas com containers ou corrupção de dados:

```bash
# Pare todos os serviços e remova volumes (⚠️ apaga todos os dados)
docker-compose down -v

# Reinicie com estado limpo
docker-compose up -d
```

### Problemas Comuns

- **Conflitos de porta**: Certifique-se que as portas 8080, 8081, 8083, 8084, 5432, 27017, 6379, 9092 estão disponíveis
- **Erros de autenticação MongoDB**: Verifique se as credenciais correspondem ao `docker-compose.yml`
- **Conexão Kafka recusada**: Aguarde 30s após `docker-compose up` para inicialização do broker
- **Avisos de mensagem não encontrada**: Normal após início limpo - offsets antigos do Kafka referenciam mensagens ausentes
- **Container user-service não inicia**: Execute `mvn clean package -DskipTests` antes de `docker-compose build user-service`

## Contribuindo

Veja [CONTRIBUTING.md](CONTRIBUTING.md) para diretrizes de desenvolvimento.

## Licença

Este projeto está licenciado sob a Licença MIT - veja o arquivo [LICENSE](LICENSE).

## Documentação

- **Especificação de Funcionalidades**: [specs/001-unified-messaging-platform/spec.md](specs/001-unified-messaging-platform/spec.md)
- **Plano Técnico**: [specs/001-unified-messaging-platform/plan.md](specs/001-unified-messaging-platform/plan.md)
- **Modelo de Dados**: [specs/001-unified-messaging-platform/data-model.md](specs/001-unified-messaging-platform/data-model.md)
- **Contratos de API**: [specs/001-unified-messaging-platform/contracts/](specs/001-unified-messaging-platform/contracts/)
- **Guia de Início Rápido**: [specs/001-unified-messaging-platform/quickstart.md](specs/001-unified-messaging-platform/quickstart.md)
- **Conclusão de Tarefas**: [docs/REMAINING_TASKS_COMPLETION.md](docs/REMAINING_TASKS_COMPLETION.md)
- **Demonstração de Failover**: [docs/FAILOVER_DEMONSTRATION.md](docs/FAILOVER_DEMONSTRATION.md) ⭐ **NOVO**

## Suporte

Para problemas e questões, abra uma issue no GitHub ou entre em contato com a equipe de desenvolvimento.
