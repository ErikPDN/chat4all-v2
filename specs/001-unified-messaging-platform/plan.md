# Implementation Plan: Chat4All v2 - Unified Messaging Platform

**Branch**: `001-unified-messaging-platform` | **Date**: 2025-11-23 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-unified-messaging-platform/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Build a high-scale unified messaging platform that routes messages between internal clients and multiple external platforms (WhatsApp, Instagram, Telegram) with strict delivery and ordering guarantees. The system implements event-driven architecture using Kafka for message brokering, dual-database strategy (MongoDB/Cassandra for messages, PostgreSQL for metadata), and pluggable connector pattern for channel integrations. Core capabilities include bidirectional messaging, file attachments up to 2GB, group conversations, and identity mapping across platforms.

## Technical Context

**Language/Version**: Java 21 LTS (with Virtual Threads for high-concurrency handling)  
**Primary Dependencies**: 
- Spring Boot 3.2+ (WebFlux reactive stack)
- Apache Kafka 3.6+ (message broker)
- Spring Data JPA (PostgreSQL access)
- Spring Data MongoDB OR Spring Data Cassandra (message store access)
- Spring Cloud Gateway (API gateway)
- Resilience4j (circuit breakers, retry logic)
- Micrometer (metrics), OpenTelemetry Java Agent (tracing)

**Storage**: 
- **Metadata DB**: PostgreSQL 16+ (users, channels, configurations)
- **Message Store**: MongoDB 7+ OR Cassandra/ScyllaDB 5+ (messages, payloads, history)
- **Cache**: Redis 7+ cluster mode (sessions, rate limiting, hot data)
- **Object Storage**: S3-compatible (MinIO, AWS S3, etc.) for file attachments

**Testing**: 
- JUnit 5 + Mockito (unit tests)
- Testcontainers (integration tests with real Kafka, PostgreSQL, MongoDB)
- RestAssured (API contract tests)
- Gatling or JMeter (performance/load tests)

**Target Platform**: Kubernetes cluster (GKE, EKS, or AKS recommended)  
**Project Type**: Distributed microservices (API-first architecture)  
**Performance Goals**: 
- <500ms API response time (P95)
- <5s message delivery to external platforms (P95)
- 10,000 concurrent conversations
- <2s conversation history retrieval

**Constraints**: 
- <200ms internal service latency (P95) - constitutional requirement
- 99.95% uptime SLA
- At-least-once delivery semantics with idempotency
- Message ordering per conversation guaranteed
- Zero-downtime deployments

**Scale/Scope**: 
- Initial: 10,000 concurrent users, 3 external platforms
- Target: 100,000+ concurrent users, 10+ external platforms
- Message volume: 1M+ messages/day
- File storage: Multi-TB capacity

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### ✅ I. Horizontal Scalability (NON-NEGOTIABLE)
- **Status**: PASS
- **Evidence**: 
  - All services designed as stateless Spring Boot applications
  - State externalized to PostgreSQL, MongoDB/Cassandra, Redis
  - Kafka consumers support auto-scaling via partition assignment
  - No local file system dependencies (S3 for files)
  - Load balancing via Kubernetes Services and Ingress

### ✅ II. High Availability
- **Status**: PASS
- **Evidence**:
  - Multi-instance deployment for all services across availability zones
  - Spring Boot Actuator health checks (`/actuator/health`)
  - Kubernetes liveness/readiness probes configured
  - Resilience4j circuit breakers prevent cascading failures
  - PostgreSQL HA via Patroni or cloud-managed (RDS, Cloud SQL)
  - MongoDB replica sets or Cassandra multi-node clusters
  - Target: 99.95% uptime per spec SC-005

### ✅ III. Message Delivery Guarantees
- **Status**: PASS
- **Evidence**:
  - UUIDv4 `message_id` generated at API layer (FR-002)
  - Idempotent message handlers check `message_id` before processing (FR-006)
  - Kafka persistence before acknowledgment (FR-005)
  - Exponential backoff retry logic with max 3 attempts (FR-008)
  - Dead-letter queue for failed messages (FR-009)

### ✅ IV. Causal Ordering
- **Status**: PASS
- **Evidence**:
  - Kafka topic partitioned by `conversation_id` (FR-007)
  - Single consumer per partition ensures sequential processing
  - Monotonic timestamp + `message_id` tie-breaking for ordering
  - Out-of-order messages buffered and reordered (edge case handled)

### ✅ V. Real-Time Performance
- **Status**: PASS
- **Evidence**:
  - API response <500ms target (SC-001)
  - Message delivery <5s target (SC-002)
  - History retrieval <2s target (SC-009)
  - Reactive Spring WebFlux for non-blocking I/O
  - Redis caching for hot data
  - Database query optimization with indexed lookups
  - Performance tests with Gatling/JMeter (testing requirement)

### ✅ VI. Full-Stack Observability (NON-NEGOTIABLE)
- **Status**: PASS
- **Evidence**:
  - Structured JSON logging via Logback with custom encoder (FR-036)
  - Micrometer + Prometheus metrics on all services (FR-037)
  - OpenTelemetry Java Agent for distributed tracing (FR-038)
  - Health check endpoints (FR-039)
  - Alerts for latency thresholds (FR-040)
  - Grafana dashboards for SLI visualization

### ✅ VII. Pluggable Architecture
- **Status**: PASS
- **Evidence**:
  - `MessageConnector` interface for channel adapters (FR-014)
  - WhatsApp, Instagram, Telegram connectors implement common contract
  - Connectors independently deployable as separate Spring Boot apps
  - Circuit breakers isolate connector failures (FR-015)
  - Externalized configuration via Spring Cloud Config or Kubernetes ConfigMaps
  - Semantic versioning for connector releases

### ✅ Data Storage (Constitution v1.1.0 Amendment)
- **Status**: PASS
- **Evidence**:
  - Message Store: MongoDB or Cassandra for high-volume message data
  - Primary Database: PostgreSQL for metadata and configuration
  - Clear separation: messages → Message Store, users/channels → PostgreSQL
  - Code review checklist includes data storage validation

**Overall Gate Status**: ✅ **PASS** - All constitutional principles satisfied

## Project Structure

### Documentation (this feature)

```text
specs/001-unified-messaging-platform/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   ├── messages-api.yaml      # OpenAPI 3.0 spec for Messages API
│   ├── conversations-api.yaml # OpenAPI 3.0 spec for Conversations API
│   ├── users-api.yaml         # OpenAPI 3.0 spec for Users/Identity API
│   └── connector-interface.yaml # MessageConnector contract
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
chat4all-v2/
├── services/
│   ├── api-gateway/               # Spring Cloud Gateway (API entry point)
│   │   ├── src/main/java/
│   │   │   └── com/chat4all/gateway/
│   │   │       ├── config/        # Route configuration, filters
│   │   │       ├── filter/        # Custom gateway filters
│   │   │       └── security/      # Authentication/authorization
│   │   ├── src/main/resources/
│   │   │   ├── application.yml
│   │   │   └── logback-spring.xml
│   │   └── pom.xml
│   │
│   ├── message-service/           # Core message handling service
│   │   ├── src/main/java/
│   │   │   └── com/chat4all/message/
│   │   │       ├── api/           # REST controllers
│   │   │       ├── domain/        # Message, Conversation entities
│   │   │       ├── repository/    # JPA/MongoDB repositories
│   │   │       ├── service/       # Business logic
│   │   │       ├── kafka/         # Kafka producers/consumers
│   │   │       └── config/        # Spring configuration
│   │   ├── src/main/resources/
│   │   └── src/test/
│   │       ├── java/              # Unit tests
│   │       └── resources/         # Testcontainers config
│   │   └── pom.xml
│   │
│   ├── router-service/            # Message routing workers
│   │   ├── src/main/java/
│   │   │   └── com/chat4all/router/
│   │   │       ├── consumer/      # Kafka consumers
│   │   │       ├── handler/       # Message handlers (dedup, persist, route)
│   │   │       ├── connector/     # Connector client interfaces
│   │   │       ├── retry/         # Retry logic with exponential backoff
│   │   │       └── dlq/           # Dead-letter queue handling
│   │   └── pom.xml
│   │
│   ├── user-service/              # User and identity management
│   │   ├── src/main/java/
│   │   │   └── com/chat4all/user/
│   │   │       ├── api/           # User/identity endpoints
│   │   │       ├── domain/        # User, ExternalIdentity entities
│   │   │       ├── repository/    # PostgreSQL repositories
│   │   │       └── service/       # Identity mapping logic
│   │   └── pom.xml
│   │
│   ├── file-service/              # File upload and storage
│   │   ├── src/main/java/
│   │   │   └── com/chat4all/file/
│   │   │       ├── api/           # Upload/download endpoints
│   │   │       ├── storage/       # S3 client integration
│   │   │       ├── scan/          # Malware scanning
│   │   │       └── thumbnail/     # Image thumbnail generation
│   │   └── pom.xml
│   │
│   └── connectors/                # Pluggable channel connectors
│       ├── whatsapp-connector/    # WhatsApp Business API integration
│       │   ├── src/main/java/
│       │   │   └── com/chat4all/connector/whatsapp/
│       │   │       ├── api/       # Webhook receiver
│       │   │       ├── client/    # WhatsApp API client
│       │   │       ├── transformer/ # Message format mapping
│       │   │       └── config/    # Credentials, rate limits
│       │   └── pom.xml
│       │
│       ├── telegram-connector/    # Telegram Bot API integration
│       │   └── [same structure as whatsapp-connector]
│       │
│       └── instagram-connector/   # Instagram Messaging API integration
│           └── [same structure as whatsapp-connector]
│
├── shared/                        # Shared libraries
│   ├── common-domain/             # Shared domain models
│   │   └── src/main/java/
│   │       └── com/chat4all/common/
│   │           ├── model/         # Message, Conversation DTOs
│   │           ├── event/         # Kafka event schemas
│   │           └── constant/      # Enums, constants
│   │
│   ├── connector-sdk/             # MessageConnector interface & base classes
│   │   └── src/main/java/
│   │       └── com/chat4all/connector/
│   │           ├── api/           # MessageConnector interface
│   │           ├── model/         # Connector DTOs
│   │           └── circuit/       # Circuit breaker utilities
│   │
│   └── observability/             # Observability utilities
│       └── src/main/java/
│           └── com/chat4all/observability/
│               ├── logging/       # JSON log encoder
│               ├── metrics/       # Custom Micrometer metrics
│               └── tracing/       # OpenTelemetry setup
│
├── infrastructure/                # Infrastructure as Code
│   ├── kubernetes/
│   │   ├── base/                  # Base Kustomize configs
│   │   │   ├── api-gateway/
│   │   │   ├── message-service/
│   │   │   └── ...
│   │   ├── overlays/
│   │   │   ├── dev/
│   │   │   ├── staging/
│   │   │   └── production/
│   │   └── kustomization.yaml
│   │
│   ├── helm/                      # Helm charts (alternative to Kustomize)
│   │   └── chat4all/
│   │       ├── Chart.yaml
│   │       ├── values.yaml
│   │       └── templates/
│   │
│   └── terraform/                 # Cloud infrastructure provisioning
│       ├── kafka/
│       ├── databases/
│       └── k8s-cluster/
│
├── docs/
│   ├── adr/                       # Architecture Decision Records
│   │   └── 001-dual-database-architecture.md
│   ├── runbooks/                  # Operational guides
│   └── architecture/              # System diagrams
│
├── .github/
│   └── workflows/                 # CI/CD pipelines
│       ├── build.yml
│       ├── test.yml
│       └── deploy.yml
│
├── pom.xml                        # Parent POM (Maven multi-module)
└── README.md
```

**Structure Decision**: Microservices architecture chosen to align with constitutional principles:
- **Horizontal Scalability**: Each service independently scalable
- **High Availability**: Services can be deployed across multiple zones
- **Pluggable Architecture**: Connectors as separate deployable units
- **Separation of Concerns**: Clear service boundaries (API, Routing, Storage, Connectors)
- **Observability**: Shared observability library ensures consistency

## Complexity Tracking

> **No constitutional violations detected. This section intentionally left minimal.**

All complexity introduced is justified by constitutional requirements and distributed systems best practices:

| Design Choice | Constitutional Alignment | Justification |
|---------------|-------------------------|---------------|
| Microservices architecture | Principles I, II, VII | Horizontal scalability, independent deployability, pluggable connectors |
| Kafka message broker | Principles III, IV | At-least-once delivery, conversation ordering via partitioning |
| Dual-database strategy | Constitution v1.1.0 | Message Store scales writes, PostgreSQL provides ACID for metadata |
| Circuit breakers | Principle II | Prevents cascading failures, ensures high availability |
| Distributed tracing | Principle VI | Required for observability in multi-service architecture |

**Simplicity Principle**: Despite microservices complexity, the system avoids unnecessary abstraraction layers. Each service has a single, well-defined responsibility. Shared libraries are minimal (domain models, connector SDK, observability utilities) to avoid over-engineering.
