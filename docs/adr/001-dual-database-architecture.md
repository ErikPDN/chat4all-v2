# ADR-001: Dual-Database Architecture (MongoDB + PostgreSQL)

**Status**: Accepted  
**Date**: 2025-11-23  
**Deciders**: Chat4All Architecture Team  
**Constitutional Requirement**: Data Storage Amendment v1.1.0

---

## Context and Problem Statement

The Chat4All v2 platform needs to handle two fundamentally different types of data with vastly different access patterns, scale requirements, and consistency guarantees:

1. **High-volume time-series message data**: Billions of messages, conversation history, file metadata
2. **Structured relational metadata**: Users, external identities, channel configurations, audit logs

A single database solution would face challenges:
- **Relational databases** (PostgreSQL alone): Excellent for ACID transactions and complex joins, but challenging to horizontally scale for billions of message records
- **Document databases** (MongoDB alone): Excellent for horizontal scaling and flexible schemas, but lack strong transactional guarantees for critical metadata operations
- **Wide-column stores** (Cassandra alone): Excellent for massive write throughput, but complex to model relational data and difficult to maintain referential integrity

**Key Requirements**:
- **FR-004**: Message delivery status tracking with state transitions
- **FR-007**: Message ordering per conversation guaranteed
- **FR-019**: Identity mapping across platforms (users ↔ platform accounts)
- **SC-002**: 10,000 concurrent conversations with sub-second query performance
- **SC-003**: 1M+ messages per day scalability
- **Constitution v1.1.0**: Explicit separation of message store from primary database

---

## Decision Drivers

### Performance & Scale
- Message write throughput: 1,000+ messages/second during peak traffic
- Conversation history queries: <2 seconds for 1000+ messages (SC-009)
- User authentication queries: <100ms for login operations
- Horizontal scalability: Must handle 10x growth without architecture changes

### Data Characteristics
- **Messages**: High volume, append-mostly, time-series, flexible schema, eventual consistency acceptable
- **Users/Identities**: Low volume, frequent updates, strict consistency, complex relationships, ACID transactions required
- **Audit Logs**: High volume, append-only, compliance requirements, long-term retention

### Operational Complexity
- Minimize number of database technologies (avoid cognitive overhead)
- Leverage team expertise (PostgreSQL for relational, MongoDB for documents)
- Standard monitoring and backup tools availability
- Docker Compose compatibility for local development

---

## Considered Options

### Option 1: PostgreSQL Only (Single Database)
**Approach**: Store everything in PostgreSQL with partitioning for messages table.

**Pros**:
- ✅ Single database technology reduces operational complexity
- ✅ Strong ACID guarantees for all data
- ✅ Complex joins between users, identities, and messages
- ✅ Mature tooling (pgAdmin, pg_dump, monitoring)
- ✅ Team has deep PostgreSQL expertise

**Cons**:
- ❌ Message table partitioning complex at scale (billions of rows)
- ❌ Horizontal sharding PostgreSQL is operationally challenging
- ❌ Vacuum and index maintenance overhead on large message tables
- ❌ Fixed schema makes message metadata evolution difficult
- ❌ Write-heavy workload impacts query performance
- ❌ Conversation history queries slow without extensive optimization

**Verdict**: Rejected - Does not meet horizontal scalability requirements

---

### Option 2: MongoDB Only (Single Database)
**Approach**: Store everything in MongoDB with embedded/referenced documents.

**Pros**:
- ✅ Horizontal sharding built-in (auto-sharding)
- ✅ Flexible schema for message metadata evolution
- ✅ Fast writes for high-throughput message ingestion
- ✅ Time-series collections optimized for message history
- ✅ Single database technology reduces complexity

**Cons**:
- ❌ No foreign key constraints - referential integrity manual
- ❌ Multi-document ACID transactions limited (MongoDB 4.0+)
- ❌ Identity mapping requires complex denormalization or $lookup joins
- ❌ Audit log compliance requires careful transaction design
- ❌ Team less experienced with MongoDB operational best practices
- ❌ User/identity relationships harder to model than PostgreSQL

**Verdict**: Rejected - Lacks strong consistency guarantees for critical metadata

---

### Option 3: Cassandra Only (Single Database)
**Approach**: Store everything in Cassandra with denormalized tables.

**Pros**:
- ✅ Massive horizontal scalability (proven at billions of records)
- ✅ Excellent write performance for message ingestion
- ✅ Built-in replication and fault tolerance
- ✅ Time-series data is a perfect fit

**Cons**:
- ❌ No joins - requires extensive denormalization
- ❌ No foreign keys or referential integrity
- ❌ Limited transaction support (Lightweight Transactions slow)
- ❌ Identity mapping across users/platforms extremely complex
- ❌ CQL lacks expressive power for complex queries
- ❌ Steep learning curve - team has no Cassandra expertise
- ❌ Operational complexity (compaction tuning, tombstones)

**Verdict**: Rejected - Too complex for team's expertise level, overkill for current scale

---

### Option 4: **Dual-Database (PostgreSQL + MongoDB)** ⭐ SELECTED
**Approach**: Separate concerns - PostgreSQL for metadata, MongoDB for messages.

**PostgreSQL stores**:
- `users` - User profiles and authentication
- `external_identities` - Platform identity mappings (WhatsApp ID → User ID)
- `channel_configurations` - Connector credentials and settings
- `audit_logs` - Compliance and security audit trail

**MongoDB stores**:
- `messages` - Message content, status, timestamps, metadata
- `conversations` - Conversation participants and metadata
- `message_status_history` - Delivery status transition log
- `files` - File attachment metadata (S3 URLs stored here)

**Pros**:
- ✅ **Best of both worlds**: ACID for metadata, horizontal scale for messages
- ✅ **Clear separation of concerns**: Relational data vs time-series data
- ✅ **Optimized queries**: PostgreSQL joins for users/identities, MongoDB indexes for message history
- ✅ **Independent scaling**: Scale MongoDB for message growth, PostgreSQL stays small
- ✅ **Simplified schema evolution**: Message metadata flexible in MongoDB
- ✅ **Team expertise alignment**: PostgreSQL for Java/Spring developers, MongoDB for document storage
- ✅ **Proven pattern**: Used by Slack, Discord, Intercom for similar use cases
- ✅ **Docker Compose ready**: Both databases have official images

**Cons**:
- ❌ **Two databases to maintain**: Backup, monitoring, security for both
- ❌ **No distributed transactions**: Must handle cross-database consistency at application level
- ❌ **Increased complexity**: Developers must understand two paradigms
- ❌ **Data duplication risk**: User IDs stored in both databases
- ❌ **Join challenges**: Cannot SQL join across PostgreSQL and MongoDB

**Mitigation Strategies**:
1. **Eventual consistency pattern**: MongoDB messages reference PostgreSQL user IDs
2. **Application-level validation**: User existence checked before message creation
3. **Idempotency keys**: Prevent duplicate messages during failures (Redis TTL cache)
4. **Standardized tooling**: Prometheus exporters for both databases
5. **Clear data ownership**: Service boundaries enforce which database owns which data
   - `user-service` owns PostgreSQL (users, identities)
   - `message-service` owns MongoDB (messages, conversations)

---

## Decision Outcome

**Chosen Option**: **Option 4 - Dual-Database (PostgreSQL + MongoDB)**

### Rationale

The dual-database approach aligns with constitutional requirements and provides the best long-term solution:

1. **Constitutional Compliance**: Satisfies Data Storage Amendment v1.1.0 requirement for message store separation
2. **Horizontal Scalability**: MongoDB sharding handles message growth to billions of records
3. **ACID Guarantees**: PostgreSQL ensures data integrity for critical user/identity operations
4. **Performance Optimization**: Each database optimized for its workload (relational vs time-series)
5. **Operational Feasibility**: Team has PostgreSQL expertise, MongoDB simpler to learn than Cassandra
6. **Industry Proven**: Pattern used by leading messaging platforms at scale

### Implementation Details

#### Data Ownership by Service

```
┌─────────────────────────────────────────────────────────────┐
│                        PostgreSQL                            │
│  Owner: user-service                                         │
│  ┌─────────────┐  ┌──────────────────┐  ┌────────────────┐ │
│  │   users     │  │ external_        │  │ channel_       │ │
│  │             │  │ identities       │  │ configurations │ │
│  └─────────────┘  └──────────────────┘  └────────────────┘ │
└─────────────────────────────────────────────────────────────┘
         ↑                                          ↑
         │ User ID references                       │ Channel configs
         │ (read-only from message-service)         │
         ↓                                          ↓
┌─────────────────────────────────────────────────────────────┐
│                        MongoDB                               │
│  Owner: message-service, file-service                        │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐ │
│  │  messages   │  │ conversations│  │ message_status_    │ │
│  │             │  │              │  │ history            │ │
│  └─────────────┘  └──────────────┘  └────────────────────┘ │
│                                                              │
│  ┌─────────────┐                                            │
│  │   files     │  (metadata only, actual files in MinIO)   │
│  └─────────────┘                                            │
└─────────────────────────────────────────────────────────────┘
```

#### Cross-Database Consistency Strategy

**Pattern**: Eventual Consistency with Validation

```java
// Example: Creating a message (message-service)
@Transactional(readOnly = true)
public Mono<Message> createMessage(CreateMessageRequest request) {
    // 1. Validate sender exists in PostgreSQL (read-only)
    return userServiceClient.getUserById(request.getSenderId())
        .switchIfEmpty(Mono.error(new UserNotFoundException()))
        
        // 2. Create message in MongoDB (owns this data)
        .flatMap(user -> {
            Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .conversationId(request.getConversationId())
                .senderId(user.getUserId())  // Validated user ID
                .content(request.getContent())
                .status(MessageStatus.PENDING)
                .timestamp(Instant.now())
                .build();
            return messageRepository.save(message);
        })
        
        // 3. Publish Kafka event (for router-service)
        .flatMap(message -> 
            kafkaProducer.send("message-events", message)
                .thenReturn(message)
        );
}
```

**Key Points**:
- User validation against PostgreSQL is read-only (no distributed transaction)
- MongoDB write is atomic (single document)
- Kafka event ensures downstream processing
- If MongoDB write fails, no user changes were made (rollback safe)
- If Kafka publish fails, retry mechanism or manual intervention

#### Scaling Strategy

**PostgreSQL**:
- **Vertical scaling** for predictable metadata growth
- **Read replicas** for user-service read-heavy queries
- **Connection pooling** (HikariCP) limits: 20 connections per service instance
- **Estimated growth**: 100K users = ~50MB, 1M users = ~500MB (small dataset)

**MongoDB**:
- **Horizontal sharding** by `conversation_id` (messages) and `user_id` (conversations)
- **Replica sets**: 3 nodes (1 primary, 2 secondaries) for high availability
- **Time-series collections** for message_status_history (MongoDB 5.0+)
- **Estimated growth**: 1M messages/day = ~1GB/day, 1 year = ~365GB (requires sharding)

**Sharding Configuration** (MongoDB):
```javascript
// Shard key: conversation_id (ensures message ordering per conversation)
sh.shardCollection("chat4all.messages", { conversation_id: 1, timestamp: 1 })

// Shard key: user_id (distributes conversations across shards)
sh.shardCollection("chat4all.conversations", { user_id: 1 })
```

---

## Consequences

### Positive Consequences

✅ **Independent Scaling**: Message volume growth doesn't impact user/identity queries  
✅ **Optimized Performance**: Each database tuned for its specific workload  
✅ **Flexible Message Schema**: Can add custom metadata fields without ALTER TABLE  
✅ **Clear Service Boundaries**: User-service owns PostgreSQL, message-service owns MongoDB  
✅ **Failure Isolation**: MongoDB downtime doesn't block user authentication  
✅ **Cost Optimization**: Small PostgreSQL instance, scale MongoDB as needed  
✅ **Regulatory Compliance**: Audit logs in ACID PostgreSQL for tamper-proof records  

### Negative Consequences

❌ **Operational Overhead**: Two databases to monitor, backup, secure, upgrade  
❌ **No Distributed Transactions**: Cannot atomically update users and messages together  
❌ **Data Duplication**: User IDs stored in both databases (synchronization risk)  
❌ **Query Limitations**: Cannot join users and messages in a single query  
❌ **Learning Curve**: Team must learn MongoDB best practices  
❌ **Backup Complexity**: Two backup schedules, restore testing for both  

### Mitigation Plan

1. **Monitoring**: Unified dashboards (Grafana) for both PostgreSQL and MongoDB metrics
2. **Backup**: Automated backup scripts for both databases with daily testing
3. **Documentation**: ADR (this document) + runbooks for common operations
4. **Training**: MongoDB workshop for development team
5. **Validation Layer**: Application-level checks for cross-database consistency
6. **Circuit Breakers**: Resilience4j protects against cascading failures

---

## Validation & Metrics

### Success Criteria (to be measured after 3 months in production)

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| PostgreSQL P95 query latency | <50ms | TBD | 🟡 Pending |
| MongoDB P95 query latency | <100ms | TBD | 🟡 Pending |
| Message write throughput | >1000/sec | TBD | 🟡 Pending |
| Conversation history retrieval (1000 msgs) | <2s | TBD | 🟡 Pending |
| Database operational incidents | <1/month | TBD | 🟡 Pending |
| Cross-database consistency errors | <0.01% | TBD | 🟡 Pending |

### Review Triggers

This decision will be reviewed if:
- ❗ PostgreSQL hits 80% CPU sustained (vertical scaling limit)
- ❗ MongoDB sharding complexity becomes unmanageable
- ❗ Cross-database consistency issues exceed 0.1% of operations
- ❗ Operational overhead increases team time >20%
- 🔄 New database technology emerges that solves dual-database challenges
- 📈 Message volume exceeds 100M messages/day (consider NewSQL like CockroachDB)

---

## Alternatives for Future Consideration

### If Scale Exceeds Current Plan (>100M messages/day)

**Option A: NewSQL (CockroachDB, YugabyteDB)**
- Combines ACID guarantees with horizontal scalability
- Single database for both metadata and messages
- Requires significant migration effort from dual-database

**Option B: Event Sourcing + CQRS**
- Kafka becomes source of truth
- PostgreSQL and MongoDB become read-only projections
- Increased architectural complexity

**Option C: Cassandra Migration**
- Migrate MongoDB → Cassandra for extreme scale
- Keep PostgreSQL for metadata
- Requires team upskilling and operational expertise

---

## Related Documents

- [Data Model Documentation](../../specs/001-unified-messaging-platform/data-model.md)
- [Architecture Plan](../../specs/001-unified-messaging-platform/plan.md)
- [Message Delivery Failure Runbook](../runbooks/message-delivery-failure.md)
- [Scaling Runbook](../runbooks/scaling.md)
- [PostgreSQL Schema Migration Scripts](../../infrastructure/postgres/migrations/)
- [MongoDB Index Configuration](../../services/message-service/src/main/java/com/chat4all/message/config/MongoIndexConfig.java)

---

## References

### Industry Examples
- **Slack**: PostgreSQL for users/channels, Vitess/MySQL for messages
- **Discord**: Cassandra for messages, PostgreSQL for guilds/users
- **Intercom**: PostgreSQL for customers, MongoDB for conversation history
- **Stripe**: PostgreSQL for transactions, MongoDB for event logs

### Technical Resources
- [MongoDB Sharding Guide](https://docs.mongodb.com/manual/sharding/)
- [PostgreSQL Partitioning](https://www.postgresql.org/docs/current/ddl-partitioning.html)
- [Spring Data MongoDB](https://spring.io/projects/spring-data-mongodb)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Eventual Consistency Patterns](https://martinfowler.com/articles/patterns-of-distributed-systems/eventual-consistency.html)

---

**Document Version**: 1.0  
**Next Review Date**: 2026-03-03 (3 months post-production)  
**Review Owner**: Platform Architecture Team  
**Amendment History**: None (initial version)
