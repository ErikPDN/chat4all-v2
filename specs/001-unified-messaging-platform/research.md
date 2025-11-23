# Research: Chat4All v2 Technical Decisions

**Feature**: Unified Messaging Platform  
**Date**: 2025-11-23  
**Phase**: 0 - Research & Technology Selection

## Overview

This document consolidates research findings and technical decisions for implementing the Chat4All v2 platform. All decisions align with the project constitution (v1.1.0) and address the functional requirements from the feature specification.

---

## 1. Message Store Technology Choice

### Decision
**MongoDB 7+ selected as primary Message Store** (with Cassandra/ScyllaDB as acceptable alternative)

### Rationale
- **Write Performance**: MongoDB handles high-volume inserts efficiently with auto-sharding
- **Flexible Schema**: Message payloads vary by channel (WhatsApp templates, Telegram inline keyboards, etc.)
- **Query Capabilities**: Rich querying needed for conversation history, search, filtering
- **Operational Maturity**: Well-documented, strong community, managed services available (Atlas, AWS DocumentDB)
- **Change Streams**: Built-in change data capture for real-time event streaming if needed
- **Time-Series Collections**: MongoDB 5.0+ optimized for time-series data (message history)

### Alternatives Considered
- **Cassandra/ScyllaDB**: 
  - ✅ Superior write throughput and linear scalability
  - ✅ Better for pure append-only workloads
  - ❌ More complex query model (CQL limitations)
  - ❌ Steeper operational learning curve
  - **Verdict**: Valid choice for teams prioritizing extreme write scale (1M+ msgs/sec)

- **PostgreSQL with JSONB**:
  - ✅ Single database technology
  - ❌ Doesn't scale writes horizontally as well
  - ❌ Violates constitutional dual-database separation of concerns
  - **Verdict**: Rejected - constitutional requirement for separate Message Store

### Implementation Notes
- Use MongoDB Sharded Cluster (3+ shards) for production
- Shard key: `conversation_id` (aligns with Kafka partitioning)
- Indexes: `conversation_id + timestamp`, `message_id` (unique), `sender_id`
- TTL index for automatic message expiration if needed
- Replica set per shard (3 nodes minimum) for HA

---

## 2. Kafka Topic Design

### Decision
**Single topic with conversation-based partitioning** for core message events

### Rationale
- **Ordering Guarantee**: Partitioning by `conversation_id` ensures all messages for a conversation go to the same partition
- **Consumer Parallelism**: Multiple consumers process different conversations concurrently
- **Simpler Operations**: Single topic easier to monitor, manage retention, and configure

### Topic Configuration
```yaml
Topic: chat-events
Partitions: 30 (adjustable based on throughput, aim for <10k messages/sec per partition)
Replication Factor: 3
Retention: 7 days (configurable, balances storage vs replay capability)
Partitioner: Murmur2 hash(conversation_id)
```

### Event Schema (Avro)
```json
{
  "namespace": "com.chat4all.event",
  "type": "record",
  "name": "MessageEvent",
  "fields": [
    {"name": "message_id", "type": "string"},
    {"name": "conversation_id", "type": "string"},
    {"name": "sender_id", "type": "string"},
    {"name": "content", "type": "string"},
    {"name": "channel", "type": {"type": "enum", "name": "Channel", "symbols": ["WHATSAPP", "TELEGRAM", "INSTAGRAM", "INTERNAL"]}},
    {"name": "timestamp", "type": "long"},
    {"name": "metadata", "type": ["null", "string"], "default": null}
  ]
}
```

### Alternatives Considered
- **Multiple topics per channel** (whatsapp-events, telegram-events):
  - ❌ Harder to guarantee global ordering per conversation across channels
  - ❌ More complex consumer coordination
  - **Verdict**: Rejected - single topic simpler and meets requirements

- **Per-conversation topics** (topic-per-conversation):
  - ❌ Topic explosion (100k+ topics not recommended in Kafka)
  - ❌ Operational nightmare (rebalancing, monitoring)
  - **Verdict**: Rejected - anti-pattern

---

## 3. Idempotency Implementation Pattern

### Decision
**Distributed cache (Redis) + database unique constraint** for deduplication

### Rationale
- **Fast Path**: Redis check (sub-millisecond) before expensive database operations
- **Durability**: PostgreSQL/MongoDB unique constraint as fallback if Redis fails
- **TTL Management**: Redis TTL (24 hours) prevents unbounded growth
- **Exactly-Once Semantics**: Combination ensures no duplicate processing even with retries

### Implementation Pattern
```java
public class IdempotentMessageHandler {
    
    @Autowired
    private RedisTemplate<String, String> redis;
    
    @Autowired
    private MessageRepository messageRepo;
    
    public boolean processMessage(MessageEvent event) {
        String messageId = event.getMessageId();
        String cacheKey = "msg:processed:" + messageId;
        
        // Fast path: Check Redis
        if (Boolean.TRUE.equals(redis.hasKey(cacheKey))) {
            log.info("Duplicate message detected in cache: {}", messageId);
            return false; // Already processed
        }
        
        try {
            // Persist to database (unique constraint on message_id)
            messageRepo.save(event);
            
            // Mark as processed in Redis (TTL 24 hours)
            redis.opsForValue().set(cacheKey, "1", 24, TimeUnit.HOURS);
            
            return true; // Successfully processed
        } catch (DuplicateKeyException e) {
            // Database unique constraint violation
            log.warn("Duplicate message detected in DB: {}", messageId);
            redis.opsForValue().set(cacheKey, "1", 24, TimeUnit.HOURS);
            return false;
        }
    }
}
```

### Alternatives Considered
- **Database-only** (unique constraint):
  - ❌ Higher latency (disk I/O for every check)
  - **Verdict**: Rejected - performance concern

- **Redis-only**:
  - ❌ Data loss risk if Redis crashes before TTL expires
  - **Verdict**: Rejected - durability concern

---

## 4. External API Integration Pattern

### Decision
**Spring WebClient (reactive) with Resilience4j** for connector implementations

### Rationale
- **Non-Blocking**: WebFlux reactive stack aligns with high-concurrency requirements
- **Backpressure**: Reactive streams handle rate limiting naturally
- **Circuit Breaker**: Resilience4j provides mature circuit breaker, retry, rate limiter
- **Observability**: Micrometer integration for metrics, OpenTelemetry for tracing

### Sample Connector Implementation
```java
@Service
public class WhatsAppConnector implements MessageConnector {
    
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final RetryRegistry retryRegistry;
    
    @Override
    public Mono<DeliveryResult> sendMessage(OutboundMessage message) {
        return circuitBreaker.executeMono(
            retryRegistry.retry("whatsapp").executeMono(
                () -> webClient.post()
                    .uri("/v1/messages")
                    .bodyValue(transformMessage(message))
                    .retrieve()
                    .bodyToMono(WhatsAppResponse.class)
                    .map(this::toDeliveryResult)
            )
        );
    }
    
    @CircuitBreaker(name = "whatsapp", fallbackMethod = "sendFallback")
    @Retry(name = "whatsapp", fallbackMethod = "sendFallback")
    private WhatsAppResponse transformMessage(OutboundMessage msg) {
        // Transform internal format to WhatsApp Business API format
    }
}
```

### Configuration (application.yml)
```yaml
resilience4j:
  circuitbreaker:
    instances:
      whatsapp:
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        slidingWindowSize: 10
  retry:
    instances:
      whatsapp:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2
```

### Alternatives Considered
- **Blocking RestTemplate**:
  - ❌ Thread-per-request model doesn't scale to 10k concurrent connections
  - **Verdict**: Rejected - performance bottleneck

- **Feign Client**:
  - ✅ Simpler declarative syntax
  - ❌ Less flexible for complex error handling, reactive patterns
  - **Verdict**: Acceptable alternative for simpler use cases

---

## 5. File Upload Strategy

### Decision
**Multipart upload with presigned URLs** for files >100MB

### Rationale
- **Client-Side Upload**: Reduces server load, clients upload directly to S3
- **Resumability**: Multipart protocol supports retry from last successful part
- **Security**: Presigned URLs provide time-limited, scoped access
- **Bandwidth**: Offloads transfer from application servers to S3

### Flow
1. Client requests upload URL: `POST /files/initiate`
2. File Service generates presigned S3 upload URL (valid 1 hour)
3. Client uploads directly to S3 using multipart protocol
4. Client notifies completion: `POST /files/{file_id}/complete`
5. File Service creates FileAttachment record, triggers malware scan

### S3 Configuration
```java
@Bean
public S3Presigner s3Presigner() {
    return S3Presigner.builder()
        .region(Region.US_EAST_1)
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
}

public PresignedPutObjectRequest generateUploadUrl(String fileId, Duration expiration) {
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket("chat4all-files")
        .key("uploads/" + fileId)
        .contentType("application/octet-stream")
        .build();
    
    return s3Presigner.presignPutObject(
        PutObjectPresignRequest.builder()
            .putObjectRequest(request)
            .signatureDuration(expiration)
            .build()
    );
}
```

### Alternatives Considered
- **Direct server upload**:
  - ❌ Bottleneck on application servers
  - ❌ Higher memory usage (buffering 2GB files)
  - **Verdict**: Rejected - scalability concern

---

## 6. Observability Stack Integration

### Decision
**OpenTelemetry Java Agent + Micrometer** for unified observability

### Rationale
- **Standards-Based**: OpenTelemetry is vendor-neutral, future-proof
- **Auto-Instrumentation**: Java agent instruments Spring Boot, Kafka, JDBC automatically
- **Flexible Exporters**: Can switch between Jaeger, Tempo, Zipkin without code changes
- **Metrics Integration**: Micrometer bridges to Prometheus, OpenTelemetry Metrics

### Setup
```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
    <version>1.32.0</version>
</dependency>
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:local}
  tracing:
    sampling:
      probability: 1.0  # 100% in dev, 0.1 (10%) in prod
```

### Structured Logging
```java
// Custom Logback encoder for JSON output
@Configuration
public class LoggingConfig {
    
    @Bean
    public LogstashEncoder logstashEncoder() {
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setIncludeMdcKeyNames("trace_id", "span_id", "user_id", "conversation_id");
        return encoder;
    }
}
```

---

## 7. Authentication & Authorization

### Decision
**OAuth2 with API Gateway-level enforcement** + service-to-service mTLS

### Rationale
- **Centralized Auth**: API Gateway validates tokens, reduces duplication
- **Service Mesh Ready**: mTLS via Istio/Linkerd for zero-trust internal communication
- **Standards-Based**: OAuth2/OpenID Connect widely supported, integrates with Keycloak, Auth0, Okta
- **Scalability**: Stateless JWT tokens, no session store needed

### Implementation
```java
// API Gateway Security Config
@EnableWebFluxSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(jwtDecoder()))
            )
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health").permitAll()
                .pathMatchers("/api/**").authenticated()
            )
            .build();
    }
    
    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation("https://auth.chat4all.com");
    }
}
```

### Alternatives Considered
- **API Keys only**:
  - ✅ Simpler for machine-to-machine
  - ❌ Less granular permissions, harder to revoke
  - **Verdict**: Acceptable for initial MVP, migrate to OAuth2 for production

---

## 8. Database Migration Strategy

### Decision
**Flyway for PostgreSQL**, **Manual scripts for MongoDB**

### Rationale
- **PostgreSQL**: Relational schema requires strict versioning, Flyway battle-tested
- **MongoDB**: Schema-less, migrations mostly for index creation, manual scripts sufficient
- **GitOps**: Migration scripts versioned in repository, applied via CI/CD

### Flyway Setup
```sql
-- V001__create_users_table.sql
CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    user_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

CREATE INDEX idx_users_user_type ON users(user_type);
```

### MongoDB Indexes (executed in init script)
```javascript
// mongo-init.js
db.messages.createIndex({ "conversation_id": 1, "timestamp": -1 });
db.messages.createIndex({ "message_id": 1 }, { unique: true });
db.messages.createIndex({ "sender_id": 1 });
db.conversations.createIndex({ "conversation_id": 1 }, { unique: true });
db.conversations.createIndex({ "participants": 1 });
```

---

## 9. Testing Strategy

### Decision
**Multi-layered testing with Testcontainers** for integration tests

### Test Pyramid
- **Unit Tests (60%)**: JUnit 5 + Mockito, fast, isolated
- **Integration Tests (30%)**: Testcontainers (Kafka, PostgreSQL, MongoDB, Redis)
- **Contract Tests (5%)**: RestAssured for API contracts, Pact for connector contracts
- **E2E Tests (5%)**: Full flow with real external API mocks

### Sample Integration Test
```java
@SpringBootTest
@Testcontainers
class MessageServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    
    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
    
    @Autowired
    private MessageService messageService;
    
    @Test
    void shouldPreserveMessageOrderingPerConversation() {
        // Test implementation
    }
}
```

---

## 10. Deployment Strategy

### Decision
**GitOps with ArgoCD** + **Canary deployments**

### Rationale
- **Declarative**: Kubernetes manifests as source of truth
- **Auditable**: Git history = deployment history
- **Automated Rollback**: Failed deployments auto-revert
- **Canary Releases**: Gradual rollout minimizes blast radius

### Deployment Pipeline
```yaml
# .github/workflows/deploy.yml
name: Deploy to Production
on:
  push:
    branches: [main]
    
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Build & Push Docker Image
        run: |
          docker build -t chat4all/message-service:${{ github.sha }} .
          docker push chat4all/message-service:${{ github.sha }}
      
      - name: Update Kustomize Image
        run: |
          cd infrastructure/kubernetes/overlays/production
          kustomize edit set image message-service=chat4all/message-service:${{ github.sha }}
          git commit -am "Deploy message-service:${{ github.sha }}"
          git push
      
      - name: ArgoCD Sync (Canary)
        run: |
          argocd app sync chat4all --strategy canary --canary-weight 10
          # Wait for health checks, then promote to 50%, 100%
```

---

## Summary

All research decisions align with constitutional principles:
- ✅ MongoDB/Cassandra for horizontal write scaling (Principle I)
- ✅ Resilience4j circuit breakers for high availability (Principle II)
- ✅ Redis + DB idempotency for at-least-once delivery (Principle III)
- ✅ Kafka partitioning for causal ordering (Principle IV)
- ✅ WebFlux + caching for <200ms latency (Principle V)
- ✅ OpenTelemetry + Micrometer for observability (Principle VI)
- ✅ MessageConnector interface for pluggability (Principle VII)

**Next Phase**: Proceed to data model design and API contract generation.
