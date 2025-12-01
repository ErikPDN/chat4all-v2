# Chat4All v2 - Unified Messaging Platform

[![Build Status](https://github.com/chat4all/chat4all-v2/workflows/build/badge.svg)](https://github.com/chat4all/chat4all-v2/actions)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

High-scale unified messaging platform that routes messages between internal clients and multiple external platforms (WhatsApp, Instagram, Telegram) with strict delivery and ordering guarantees.

## Architecture Overview

```
┌─────────────┐
│ API Gateway │ ← HTTPS/WebSocket
└──────┬──────┘
       │
   ┌───┴────────────────────────────────────┐
   │                                        │
   ▼                                        ▼
┌──────────────┐                    ┌──────────────┐
│   Message    │────► Kafka ────►   │    Router    │
│   Service    │                    │   Service    │
└──────────────┘                    └──────┬───────┘
       │                                   │
       │                              ┌────┴────────────┬─────────────┐
       │                              │                 │             │
       ▼                              ▼                 ▼             ▼
┌──────────────┐              ┌─────────────┐   ┌─────────┐   ┌─────────┐
│   MongoDB    │              │  WhatsApp   │   │Telegram │   │Instagram│
│ (Messages)   │              │  Connector  │   │Connector│   │Connector│
└──────────────┘              └─────────────┘   └─────────┘   └─────────┘
       │
       │
┌──────────────┐
│  PostgreSQL  │
│  (Metadata)  │
└──────────────┘
```

## Key Features

- **Bidirectional Messaging**: Send and receive messages across WhatsApp, Telegram, Instagram
- **File Attachments**: Support for images, documents, videos up to 2GB
- **Group Conversations**: Multi-party messaging with up to 100 participants
- **Identity Mapping**: Link multiple platform accounts to unified user profiles
- **High Availability**: 99.95% uptime SLA with automatic failover
- **Real-Time Performance**: <500ms API response, <5s external delivery
- **Full Observability**: Structured logs, Prometheus metrics, distributed tracing

## Technology Stack

- **Language**: Java 21 LTS (Virtual Threads)
- **Framework**: Spring Boot 3.2+ (WebFlux), Spring Cloud Gateway
- **Message Broker**: Apache Kafka 3.6+
- **Primary Database**: PostgreSQL 16+ (users, configuration)
- **Message Store**: MongoDB 7+ (messages, conversations, files)
- **Caching**: Redis 7+ (sessions, rate limiting, idempotency)
- **Object Storage**: S3-compatible (MinIO, AWS S3)
- **Observability**: Micrometer, OpenTelemetry, Prometheus, Grafana, Jaeger
- **Infrastructure**: Kubernetes, ArgoCD (GitOps), Terraform

## Quick Start

### Prerequisites

- JDK 21 or higher
- Maven 3.9+
- Docker & Docker Compose
- Kubernetes cluster (optional, for production)

### Local Development

1. **Clone the repository**:
   ```bash
   git clone https://github.com/chat4all/chat4all-v2.git
   cd chat4all-v2
   ```

2. **Start infrastructure services**:
   ```bash
   docker-compose up -d
   ```

   This starts: Kafka, PostgreSQL, MongoDB, Redis, MinIO, Prometheus, Grafana, Jaeger

   **Default Credentials**:
   - **MongoDB**: `chat4all` / `chat4all_dev_password`
   - **PostgreSQL**: `chat4all` / `chat4all_dev_password`
   - **Redis**: `chat4all_dev_password`
   - **MinIO**: `minioadmin` / `minioadmin`

3. **Build all modules**:
   ```bash
   mvn clean install
   ```

4. **Run services**:
   ```bash
   # Terminal 1 - API Gateway
   cd services/api-gateway && mvn spring-boot:run

   # Terminal 2 - Message Service
   cd services/message-service && mvn spring-boot:run

   # Terminal 3 - Router Service
   cd services/router-service && mvn spring-boot:run

   # Terminal 4 - User Service
   cd services/user-service && mvn spring-boot:run
   ```

5. **Send a test message**:
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

   **Expected Response** (HTTP 202 Accepted):
   ```json
   {
     "messageId": "3d63cf3b-466d-46c5-9efe-2569b98a8915",
     "conversationId": "conv-test-001",
     "status": "PENDING",
     "acceptedAt": "2025-11-24T18:47:49.292241137Z",
     "statusUrl": "/api/messages/3d63cf3b-466d-46c5-9efe-2569b98a8915/status"
   }
   ```

6. **Retrieve conversation messages**:
   ```bash
   curl http://localhost:8081/api/v1/conversations/conv-test-001/messages | jq
   ```

   **Expected Response** (HTTP 200 OK):
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
         "timestamp": "2025-11-24T18:47:49.291Z"
       }
     ],
     "nextCursor": null,
     "hasMore": false,
     "count": 1
   }
   ```

   **Pagination options**:
   ```bash
   # Limit results
   curl "http://localhost:8081/api/v1/conversations/conv-test-001/messages?limit=10"

   # Cursor-based pagination (use nextCursor from previous response)
   curl "http://localhost:8081/api/v1/conversations/conv-test-001/messages?before=2025-11-24T18:47:49.291Z&limit=50"
   ```

7. **Access observability tools**:
   - **Grafana**: http://localhost:3000 (admin/admin)
   - **Prometheus**: http://localhost:9090
   - **Jaeger**: http://localhost:16686

## Project Structure

```
chat4all-v2/
├── services/
│   ├── api-gateway/           # Spring Cloud Gateway (API entry point)
│   ├── message-service/       # Core message handling
│   ├── router-service/        # Message routing workers
│   ├── user-service/          # User & identity management
│   ├── file-service/          # File upload & storage
│   └── connectors/
│       ├── whatsapp-connector/
│       ├── telegram-connector/
│       └── instagram-connector/
├── shared/
│   ├── common-domain/         # Shared DTOs, events
│   ├── connector-sdk/         # MessageConnector interface
│   └── observability/         # Logging, metrics, tracing
├── infrastructure/
│   ├── kubernetes/            # K8s manifests (Kustomize)
│   ├── terraform/             # Infrastructure as Code
│   └── docker-compose.yml     # Local development stack
├── docs/
│   ├── adr/                   # Architecture Decision Records
│   └── runbooks/              # Operational guides
└── specs/
    └── 001-unified-messaging-platform/
        ├── spec.md            # Feature specification
        ├── plan.md            # Technical plan
        ├── data-model.md      # Database schemas
        ├── contracts/         # OpenAPI specs
        └── tasks.md           # Implementation tasks
```

## Development Workflow

1. **Feature Development**: Follow tasks in `specs/001-unified-messaging-platform/tasks.md`
2. **Testing**: Run `mvn test` for unit tests, see `quickstart.md` for integration tests
3. **Code Quality**: Pre-commit hooks run linting and formatting
4. **CI/CD**: GitHub Actions build, test, and deploy via ArgoCD
5. **Monitoring**: Check Grafana dashboards for service health


```bash
# Database
DB_POSTGRES_URL=jdbc:postgresql://localhost:5432/chat4all
DB_POSTGRES_USER=chat4all
DB_POSTGRES_PASSWORD=chat4all_dev_password

DB_MONGODB_URI=mongodb://localhost:27017/chat4all
DB_MONGODB_USER=chat4all
DB_MONGODB_PASSWORD=chat4all_dev_password

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=chat4all_dev_password

# S3 (MinIO)
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=minioadmin
S3_SECRET_KEY=minioadmin

# Observability
OTEL_EXPORTER_JAEGER_ENDPOINT=http://localhost:14250
```

## Troubleshooting

### Reset Infrastructure

If you encounter issues with containers or data corruption:

```bash
# Stop all services and remove volumes (⚠️ deletes all data)
docker-compose down -v

# Restart with clean state
docker-compose up -d
```

### Common Issues

- **Port conflicts**: Ensure ports 8081, 8082, 5432, 27017, 6379, 9092 are available
- **MongoDB authentication errors**: Verify credentials match `docker-compose.yml`
- **Kafka connection refused**: Wait 30s after `docker-compose up` for broker initialization
- **Message not found warnings**: Normal after fresh start - old Kafka offsets reference missing messages

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines.

## License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file.

## Documentation

- **Feature Specification**: [specs/001-unified-messaging-platform/spec.md](specs/001-unified-messaging-platform/spec.md)
- **Technical Plan**: [specs/001-unified-messaging-platform/plan.md](specs/001-unified-messaging-platform/plan.md)
- **Data Model**: [specs/001-unified-messaging-platform/data-model.md](specs/001-unified-messaging-platform/data-model.md)
- **API Contracts**: [specs/001-unified-messaging-platform/contracts/](specs/001-unified-messaging-platform/contracts/)
- **Quickstart Guide**: [specs/001-unified-messaging-platform/quickstart.md](specs/001-unified-messaging-platform/quickstart.md)

## Support

For issues and questions, please open a GitHub issue or contact the development team.
