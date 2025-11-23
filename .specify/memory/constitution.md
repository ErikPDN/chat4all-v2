<!--
SYNC IMPACT REPORT:
- Version Change: 1.0.0 → 1.1.0
- Amendment Type: MINOR (material expansion of Data Storage guidance)
- Modified Sections: Technology Stack → Data Storage
- Changes Made:
  - Separated Message Store (MongoDB/Cassandra) from Primary Database (PostgreSQL)
  - Added clear separation of concerns: messages/payloads vs metadata/configuration
  - Rationale updated to reflect dual-database strategy
- Templates Status:
  ✅ constitution.md - Updated
  ⚠ plan-template.md - Review for data storage strategy alignment
  ⚠ spec-template.md - Review for data model sections
  ⚠ tasks-template.md - Review for database task categorization
- Follow-up TODOs:
  - Create ADR documenting decision to use dual-database architecture
  - Update data-model.md template to reflect Message Store vs Primary DB separation
-->

# Chat4All v2 Constitution

## Core Principles

### I. Horizontal Scalability (NON-NEGOTIABLE)

The system MUST support horizontal scaling without service interruption.

**Rules**:
- All services MUST be stateless or use externalized state stores
- Auto-scaling policies MUST be configured for all service tiers
- Zero-downtime deployment MUST be enforced via rolling updates or blue-green deployments
- No service MAY maintain local state that prevents horizontal replication
- Load balancing MUST distribute traffic across all healthy instances

**Rationale**: Business growth demands elastic capacity. Manual scaling creates operational bottlenecks and service disruptions. Horizontal scalability ensures the platform can handle unpredictable traffic spikes while maintaining cost efficiency through auto-scaling.

### II. High Availability

The system MUST maintain 99.95% uptime (SLA target) with automated failover mechanisms.

**Rules**:
- All critical services MUST have redundant instances across availability zones
- Health checks MUST detect failures within 10 seconds
- Automatic failover MUST complete within 30 seconds
- Circuit breakers MUST prevent cascading failures
- Database clusters MUST use multi-master or master-replica with automatic promotion
- Single points of failure (SPOF) MUST be identified and eliminated during design review

**Rationale**: Communication platforms are mission-critical for users. Downtime directly impacts user trust and business continuity. The 99.95% SLA allows for approximately 4.38 hours of downtime per year, providing a realistic yet stringent availability target.

### III. Message Delivery Guarantees

The system MUST implement "at-least-once" delivery semantics with client-side idempotency.

**Rules**:
- Every message MUST be assigned a globally unique `message_id` (UUIDv4) at creation
- Message consumers MUST use `message_id` for deduplication
- Retries MUST preserve the original `message_id`
- Message persistence MUST occur before acknowledgment to clients
- Failed deliveries MUST be retried with exponential backoff (max 3 attempts)
- Dead-letter queues MUST capture messages exceeding retry limits

**Rationale**: Network partitions and service failures are inevitable in distributed systems. At-least-once delivery ensures no message is lost, while UUIDv4-based deduplication prevents duplicate processing. This balance protects message integrity without the complexity and performance cost of exactly-once semantics.

### IV. Causal Ordering

The system MUST preserve message ordering within conversation contexts.

**Rules**:
- Messages MUST be partitioned by `conversation_id` in message brokers
- All messages for a given `conversation_id` MUST be processed by the same consumer instance
- Timestamp-based ordering MUST use monotonic clocks with tie-breaking via `message_id`
- Out-of-order messages MUST be buffered and reordered before delivery
- Partition count MUST remain stable to prevent rebalancing-induced disorder

**Rationale**: Conversation coherence depends on message order. Users expect to see replies after questions. Partitioning by `conversation_id` ensures single-threaded processing per conversation while maintaining parallelism across conversations.

### V. Real-Time Performance

The system MUST achieve <200ms end-to-end latency for real-time message paths (P95).

**Rules**:
- Service-to-service calls MUST complete within 50ms (P95) for synchronous operations
- Asynchronous message broker publish/consume latency MUST be <20ms (P95)
- Database queries MUST complete within 10ms (P95) for indexed lookups
- API gateway routing overhead MUST be <5ms (P95)
- Network hop count MUST be minimized (target: <3 hops for critical paths)
- Performance degradation MUST trigger alerts when P95 exceeds thresholds by 20%

**Rationale**: Real-time chat demands responsive interactions. The 200ms budget aligns with human perception of instantaneous response. Breaking down the budget by tier ensures each component maintains accountability for its latency contribution.

### VI. Full-Stack Observability (NON-NEGOTIABLE)

The system MUST provide comprehensive visibility into runtime behavior.

**Rules**:
- All logs MUST use structured JSON format with standardized fields (`timestamp`, `level`, `service`, `trace_id`, `message`)
- Metrics MUST be exposed via Prometheus `/metrics` endpoints on all services
- Distributed tracing MUST be implemented using OpenTelemetry with context propagation
- Golden signals (latency, traffic, errors, saturation) MUST be monitored for all services
- Dashboards MUST visualize SLIs (Service Level Indicators) for SLA tracking
- Alerts MUST fire when SLOs (Service Level Objectives) are breached

**Rationale**: Complex distributed systems are opaque without observability. Structured logging enables automated analysis, Prometheus provides time-series metrics for trend detection, and OpenTelemetry traces requests across service boundaries. This triad is industry standard for production-grade systems.

### VII. Pluggable Architecture

The system MUST support seamless integration of new communication channel adapters.

**Rules**:
- Channel adapters MUST implement a common `MessageConnector` interface
- Adapters MUST be independently deployable without core system changes
- Adapter registration MUST be runtime-discoverable (no compile-time coupling)
- Adapter failures MUST NOT impact other channels (fault isolation)
- Adapter configuration MUST be externalized (environment variables or config service)
- Adapter versioning MUST follow semantic versioning with backward compatibility guarantees

**Rationale**: Communication platforms must evolve with new channels (WhatsApp, Telegram, Slack, etc.). Tight coupling prevents agility and increases regression risk. A plugin architecture with clear contracts enables parallel development and selective rollout of new integrations.

## Technology Stack

### Programming Language
- **Primary**: Java 21+ (LTS) with Virtual Threads for concurrency
- **Rationale**: Enterprise maturity, strong typing, excellent tooling, and virtual threads eliminate thread pool management complexity

### Messaging & Event Streaming
- **Message Broker**: Apache Kafka
- **Rationale**: Proven at scale, strong ordering guarantees via partitioning, replicated persistence, exactly-once semantics when needed

### Data Storage

**Message Store (High-Volume Writes & Reads)**:
- **Technology**: MongoDB 7+ OR Cassandra/ScyllaDB 5+
- **Purpose**: Store messages, message payloads, conversation history
- **Rationale**: 
  - Message data requires horizontal scaling for high write throughput
  - Time-series nature of chat messages aligns with wide-column (Cassandra) or document (MongoDB) models
  - MongoDB provides flexible schema for diverse message types and rich querying
  - Cassandra/ScyllaDB offers superior write performance and linear scalability for append-heavy workloads
  - Both support partition-based sharding aligned with `conversation_id` partitioning strategy

**Primary Database (Metadata & Configuration)**:
- **Technology**: PostgreSQL 16+ with pgvector extension
- **Purpose**: User accounts, channel configurations, adapter settings, system metadata
- **Rationale**: 
  - Relational model fits structured metadata with referential integrity requirements
  - ACID guarantees essential for configuration consistency
  - pgvector enables semantic search capabilities for future AI features
  - Rich query capabilities (JOINs, CTEs) for analytics and reporting
  - Proven HA solutions (Patroni, Citus) for 99.95% availability target

**Caching Layer**:
- **Technology**: Redis 7+ (cluster mode)
- **Purpose**: Session state, rate limiting, temporary data, hot message cache
- **Rationale**: 
  - Sub-millisecond latency critical for <200ms performance budget
  - Pub/sub for real-time notifications
  - Cluster mode provides horizontal scaling and automatic sharding
  - Built-in data structures (sorted sets, HyperLogLog) for advanced features

### API Layer
- **Framework**: Spring Boot 3.2+ with WebFlux (reactive stack)
- **API Gateway**: Spring Cloud Gateway or Kong
- **Rationale**: Spring ecosystem provides comprehensive tooling, and reactive streams support high concurrency with low resource overhead

### Observability Stack
- **Logging**: Logback with JSON encoder → Loki or Elasticsearch
- **Metrics**: Micrometer → Prometheus
- **Tracing**: OpenTelemetry → Jaeger or Tempo
- **Dashboards**: Grafana
- **Rationale**: Industry-standard open-source stack with broad integration support

### Infrastructure
- **Container Orchestration**: Kubernetes (managed service recommended: GKE, EKS, AKS)
- **Service Mesh**: Istio (optional, for advanced traffic management)
- **CI/CD**: GitHub Actions or GitLab CI
- **Rationale**: Kubernetes is the de facto standard for cloud-native applications, enabling declarative infrastructure and auto-scaling

## Development Workflow

### Architecture Decision Records (ADRs)
- Significant architectural decisions MUST be documented in ADR format
- ADRs MUST include context, decision, consequences, and alternatives considered
- ADRs MUST be versioned in the repository under `/docs/adr/`

### Code Review Requirements
- All code changes MUST be reviewed by at least one team member
- Constitution compliance MUST be verified during review:
  - Scalability: No local state, stateless services
  - Availability: Health checks, graceful shutdown
  - Delivery: UUIDv4 message_id, idempotent handlers
  - Ordering: Conversation partitioning
  - Performance: Latency budgets validated
  - Observability: Structured logs, metrics, traces
  - Pluggability: Adapter interface compliance
  - Data Storage: Message data → Message Store (MongoDB/Cassandra), Metadata → PostgreSQL
- Security vulnerabilities MUST be addressed before merge

### Testing Standards
- Unit test coverage MUST be ≥80% for business logic
- Integration tests MUST validate cross-service contracts
- Performance tests MUST verify latency SLOs before production
- Chaos engineering MUST be practiced quarterly to validate resilience

### Deployment Gates
- All deployments MUST pass automated test suites
- Canary deployments MUST be used for production releases (10% → 50% → 100%)
- Rollback procedures MUST be documented and tested
- Post-deployment validation MUST confirm SLIs remain within SLO bounds

## Governance

This Constitution represents the non-negotiable technical foundation of Chat4All v2. All architectural decisions, code reviews, and operational practices MUST align with these principles.

### Amendment Process
1. Proposed amendments MUST be documented with rationale and impact analysis
2. Amendments MUST be reviewed by the technical leadership team
3. Breaking changes (MAJOR version bumps) require team consensus
4. Approved amendments trigger updates to dependent templates and documentation
5. The `CONSTITUTION_VERSION` MUST be incremented following semantic versioning

### Compliance Review
- Architecture reviews MUST verify new designs against constitutional principles
- Quarterly audits MUST assess adherence to observability and performance standards
- Violations MUST be tracked as technical debt with remediation plans

### Exception Handling
- Exceptions to constitutional principles MUST be documented as ADRs
- Exceptions MUST include:
  - Specific principle(s) being violated
  - Business or technical justification
  - Mitigation plan to minimize impact
  - Timeline for remediation (if temporary exception)
- Permanent exceptions MUST be rare and require executive approval

**Version**: 1.1.0 | **Ratified**: 2025-11-23 | **Last Amended**: 2025-11-23
