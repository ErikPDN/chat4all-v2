# Chat4All v2 - Quick Start Guide

**Feature**: Unified Messaging Platform  
**Audience**: Developers new to the project  
**Last Updated**: 2025-11-23

## Overview

Chat4All v2 is a high-scale unified messaging platform that routes messages between internal clients and external platforms (WhatsApp, Instagram, Telegram). This guide helps you get started with development.

---

## Prerequisites

### Required Tools
- **JDK 21** (Temurin, Corretto, or Oracle)
- **Maven 3.9+** (or use `./mvnw` wrapper)
- **Docker & Docker Compose** (for local infrastructure)
- **Git**
- **IDE**: IntelliJ IDEA (recommended) or VS Code with Java extensions

### Recommended Tools
- **kubectl** (for Kubernetes deployment)
- **k9s** (Kubernetes CLI UI)
- **Postman** or **HTTPie** (API testing)
- **MongoDB Compass** (MongoDB GUI)
- **pgAdmin** (PostgreSQL GUI)

---

## Local Development Setup

### 1. Clone the Repository

```bash
git clone https://github.com/your-org/chat4all-v2.git
cd chat4all-v2
git checkout 001-unified-messaging-platform
```

### 2. Start Local Infrastructure

Use Docker Compose to start all required services:

```bash
docker-compose up -d
```

This starts:
- **Kafka** (localhost:9092)
- **PostgreSQL** (localhost:5433) - Note: Port 5433 to avoid conflicts
- **MongoDB** (localhost:27017)
- **Redis** (localhost:6379)
- **MinIO** (S3-compatible, localhost:9000)
- **Prometheus** (localhost:9090)
- **Grafana** (localhost:3000)
- **Jaeger** (localhost:16686)
- **API Gateway** (localhost:8080)
- **Message Service** (localhost:8081)
- **User Service** (localhost:8083)
- **File Service** (localhost:8084)
- **WhatsApp Connector** (localhost:8085)
- **Telegram Connector** (localhost:8086)
- **Instagram Connector** (localhost:8087)
- **Router Service** (no external port)

### 3. Initialize Databases

**PostgreSQL** migrations are applied automatically on service startup via Flyway.

**MongoDB** indexes are created automatically when services start.

To verify:

```bash
# PostgreSQL tables
docker exec chat4all-postgres psql -U chat4all -d chat4all -c "\dt"

# MongoDB collections
docker exec chat4all-mongodb mongosh --eval "use chat4all; show collections"
```

### 4. Build All Services (Optional)

**Note**: If using `docker-compose up -d`, all services are already running. Building from source is only needed for local development.

```bash
# From repository root
./mvnw clean install
```

### 5. Run Services Locally

#### Option A: Docker Compose (Recommended)

**All services are already running** after `docker-compose up -d`. Skip to [Verify Setup](#verify-setup).

#### Option B: Run Individual Services (Development Only)

For active development with hot reload:

```bash
# Terminal 1: API Gateway
cd services/api-gateway
./mvnw spring-boot:run

# Terminal 2: Message Service
cd services/message-service
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"

# Terminal 3: Router Service
cd services/router-service
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8082"

# Terminal 4: User Service
cd services/user-service
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8083"

# Terminal 5: File Service  
cd services/file-service
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8084"
```

**Note**: When running locally, stop Docker Compose services first to avoid port conflicts:
```bash
docker-compose stop api-gateway message-service user-service file-service
```

---

## Verify Setup

### Health Checks

```bash
# API Gateway
curl http://localhost:8080/actuator/health

# Message Service
curl http://localhost:8081/actuator/health

# Prometheus metrics
curl http://localhost:8081/actuator/prometheus
```

### Send Test Message (via Webhook)

**Note**: Message endpoints require OAuth2 authentication. For quick testing, use the public webhook endpoint:

```bash
curl -X POST http://localhost:8080/api/webhooks/whatsapp \
  -H "Content-Type: application/json" \
  -d '{
    "object": "whatsapp_business_account",
    "entry": [{
      "id": "123456789",
      "changes": [{
        "value": {
          "messaging_product": "whatsapp",
          "metadata": {
            "display_phone_number": "15551234567",
            "phone_number_id": "987654321"
          },
          "messages": [{
            "from": "5511999887766",
            "id": "wamid.test-001",
            "timestamp": "1733247000",
            "type": "text",
            "text": {
              "body": "Hello World!"
            }
          }]
        },
        "field": "messages"
      }]
    }]
  }'
```

Expected response (HTTP 200):
```json
{
  "status": "success"
}
```

**For authenticated endpoints**, you need a valid JWT token. See [OAuth2 Configuration](#oauth2-configuration) section.

---

## Project Structure

```
chat4all-v2/
├── services/               # Microservices
│   ├── api-gateway/        # Entry point (port 8080)
│   ├── message-service/    # Core messaging (port 8081)
│   ├── router-service/     # Message routing workers (port 8082)
│   ├── user-service/       # User/identity management (port 8083)
│   ├── file-service/       # File uploads (port 8084)
│   └── connectors/         # WhatsApp, Telegram, Instagram
├── shared/                 # Shared libraries
│   ├── common-domain/      # DTOs, events
│   ├── connector-sdk/      # MessageConnector interface
│   └── observability/      # Logging, metrics, tracing
├── infrastructure/         # IaC, Kubernetes manifests
└── docs/                   # Architecture, ADRs, runbooks
```

---

## Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
```

### 2. Make Changes

Edit code in your IDE. Spring Boot DevTools supports hot reload:

```xml
<!-- Add to pom.xml for hot reload -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 3. Run Tests

```bash
# Unit tests
./mvnw test

# Integration tests (with Testcontainers)
./mvnw verify

# Specific service
cd services/message-service
./mvnw test
```

### 4. Check Code Quality

```bash
# Format code
./mvnw spotless:apply

# Static analysis
./mvnw checkstyle:check
```

### 5. Commit & Push

```bash
git add .
git commit -m "feat: add message deduplication logic"
git push origin feature/your-feature-name
```

### 6. Open Pull Request

- Pull requests require 1 approval
- CI/CD pipeline must pass (build, tests, linting)
- Constitution compliance checklist reviewed

---

## Configuration

### Environment Variables

```bash
# Database
export DB_POSTGRES_URL=jdbc:postgresql://localhost:5433/chat4all  # Note: Port 5433
export DB_POSTGRES_USER=chat4all  # Changed from postgres
export DB_POSTGRES_PASSWORD=chat4all  # Changed from postgres

export DB_MONGODB_URI=mongodb://localhost:27017/chat4all

export REDIS_HOST=localhost
export REDIS_PORT=6379

# Kafka
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# S3 (MinIO)
export S3_ENDPOINT=http://localhost:9000
export S3_ACCESS_KEY=minioadmin
export S3_SECRET_KEY=minioadmin
export S3_BUCKET=chat4all-files

# Observability
export OTEL_SERVICE_NAME=message-service
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
```

### Application Profiles

```bash
# Development (default)
./mvnw spring-boot:run

# Staging
./mvnw spring-boot:run -Dspring-boot.run.profiles=staging

# Production
./mvnw spring-boot:run -Dspring-boot.run.profiles=production
```

---

## Debugging

### IntelliJ IDEA

1. Open `Run > Edit Configurations`
2. Add `Spring Boot` configuration
3. Main class: `com.chat4all.message.MessageServiceApplication`
4. Set breakpoints and run in Debug mode

### Remote Debugging (Kubernetes)

```bash
# Port-forward to pod
kubectl port-forward pod/message-service-xxx 5005:5005

# Connect IDE debugger to localhost:5005
```

### Logs

```bash
# Docker Compose
docker-compose logs -f message-service

# Kubernetes
kubectl logs -f deployment/message-service

# Loki (if configured)
curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={service="message-service"}' \
  | jq .
```

---

## Testing

### Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    
    @Mock
    private MessageRepository messageRepo;
    
    @InjectMocks
    private MessageService messageService;
    
    @Test
    void shouldDeduplicateMessages() {
        // Test implementation
    }
}
```

### Integration Tests

```java
@SpringBootTest
@Testcontainers
class MessageServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    
    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7");
    
    @Autowired
    private MessageService messageService;
    
    @Test
    void shouldPersistMessageToMongoDB() {
        // Test implementation
    }
}
```

### Contract Tests (Pact)

```java
@PactTestFor(providerName = "message-service")
class MessageServiceContractTest {
    
    @Pact(consumer = "api-gateway")
    public RequestResponsePact createPact(PactDslWithProvider builder) {
        return builder
            .given("messages exist")
            .uponReceiving("a request for messages")
            .path("/v1/messages")
            .method("GET")
            .willRespondWith()
            .status(200)
            .body(/* expected JSON */)
            .toPact();
    }
}
```

---

## Observability

### Metrics (Prometheus)

```bash
# View metrics
open http://localhost:8081/actuator/prometheus

# Query Prometheus
open http://localhost:9090
```

Example queries:
- `rate(http_server_requests_seconds_count[5m])` - Request rate
- `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))` - P95 latency

### Tracing (Jaeger)

```bash
open http://localhost:16686
```

Search for traces by service name, operation, or trace ID.

### Dashboards (Grafana)

```bash
open http://localhost:3000
# Default credentials: admin/admin
```

Import pre-built dashboards from `infrastructure/grafana/dashboards/`.

---

## Common Tasks

### Add a New Connector

1. Create module: `services/connectors/newplatform-connector`
2. Implement `MessageConnector` interface
3. Add configuration in `channel_configurations` table
4. Deploy independently

### Add a New API Endpoint

1. Define in OpenAPI spec: `specs/001-unified-messaging-platform/contracts/`
2. Generate server stubs: `./mvnw openapi-generator:generate`
3. Implement controller in respective service
4. Add integration tests

### Run Performance Tests

```bash
# Gatling
cd services/message-service
./mvnw gatling:test

# Results in target/gatling/
```

---

## Troubleshooting

### Kafka Not Starting

```bash
# Check logs
docker-compose logs kafka

# Recreate
docker-compose down -v
docker-compose up -d kafka
```

### Database Connection Refused

```bash
# Verify containers running
docker-compose ps

# Check PostgreSQL
docker-compose exec postgres psql -U postgres -c "\l"

# Check MongoDB
docker-compose exec mongodb mongosh --eval "db.adminCommand('ping')"
```

### Tests Failing

```bash
# Clean Maven cache
./mvnw clean

# Update Testcontainers images
docker pull postgres:16
docker pull mongo:7
docker pull confluentinc/cp-kafka:7.5.0
```

---

## Resources

### Documentation
- [Architecture Decisions (ADRs)](../../docs/adr/)
- [API Contracts](./contracts/)
- [Data Model](./data-model.md)
- [Constitution](../../.specify/memory/constitution.md)

### External References
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Apache Kafka](https://kafka.apache.org/documentation/)
- [MongoDB Manual](https://docs.mongodb.com/manual/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)

### Support
- **Slack**: #chat4all-dev
- **Email**: platform@chat4all.com
- **Issues**: GitHub Issues

---

**Happy coding! 🚀**
