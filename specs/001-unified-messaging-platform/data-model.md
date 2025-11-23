# Data Model: Chat4All v2

**Feature**: Unified Messaging Platform  
**Date**: 2025-11-23  
**Phase**: 1 - Design & Contracts

## Overview

This document defines the data model for Chat4All v2, covering both the Primary Database (PostgreSQL) for metadata and the Message Store (MongoDB) for high-volume message data. The model is derived from the Key Entities defined in the feature specification and aligned with functional requirements.

---

## Database Strategy (Constitution v1.1.0)

### Primary Database: PostgreSQL 16+
**Purpose**: Relational data requiring ACID guarantees  
**Stores**: Users, External Identities, Channel Configurations, System Metadata

### Message Store: MongoDB 7+
**Purpose**: High-volume time-series message data  
**Stores**: Messages, Conversations, File Attachments

**Rationale**: Separation allows PostgreSQL to provide strong consistency for critical metadata while MongoDB scales horizontally for billions of messages.

---

## Primary Database (PostgreSQL)

### 1. users

**Purpose**: Internal user profiles (agents and customers)

```sql
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name VARCHAR(255) NOT NULL,
    user_type VARCHAR(50) NOT NULL CHECK (user_type IN ('AGENT', 'CUSTOMER')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    
    CONSTRAINT users_display_name_min_length CHECK (LENGTH(display_name) >= 1)
);

CREATE INDEX idx_users_user_type ON users(user_type);
CREATE INDEX idx_users_active ON users(active) WHERE active = TRUE;
CREATE INDEX idx_users_metadata ON users USING GIN(metadata);
```

**Validation Rules**:
- `user_id`: UUID, automatically generated
- `display_name`: Non-empty string, max 255 characters
- `user_type`: Enum ('AGENT', 'CUSTOMER')
- `active`: Soft delete flag

**Relationships**:
- One-to-many with `external_identities`
- Referenced by `conversation_participants` (via MongoDB)

---

### 2. external_identities

**Purpose**: Links internal users to platform-specific accounts

```sql
CREATE TABLE external_identities (
    identity_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL CHECK (platform IN ('WHATSAPP', 'TELEGRAM', 'INSTAGRAM')),
    platform_user_id VARCHAR(255) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    
    CONSTRAINT unique_platform_identity UNIQUE (platform, platform_user_id)
);

CREATE INDEX idx_external_identities_user_id ON external_identities(user_id);
CREATE INDEX idx_external_identities_platform ON external_identities(platform);
CREATE INDEX idx_external_identities_platform_user ON external_identities(platform, platform_user_id);
```

**Validation Rules**:
- `platform_user_id`: Examples:
  - WhatsApp: `+5511999999999` (E.164 format)
  - Telegram: `@username` or numeric ID
  - Instagram: `@handle`
- `verified`: Indicates identity verification completed
- Unique constraint prevents duplicate platform accounts

**Relationships**:
- Many-to-one with `users`

---

### 3. channel_configurations

**Purpose**: External platform integration settings

```sql
CREATE TABLE channel_configurations (
    channel_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform VARCHAR(50) NOT NULL CHECK (platform IN ('WHATSAPP', 'TELEGRAM', 'INSTAGRAM')),
    name VARCHAR(255) NOT NULL,
    credentials JSONB NOT NULL,
    webhook_url VARCHAR(500),
    rate_limits JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT channel_configurations_name_unique UNIQUE (platform, name)
);

CREATE INDEX idx_channel_configurations_platform ON channel_configurations(platform);
CREATE INDEX idx_channel_configurations_enabled ON channel_configurations(enabled) WHERE enabled = TRUE;
```

**Validation Rules**:
- `credentials`: Encrypted JSON containing API keys, tokens
  - WhatsApp: `{"access_token": "...", "phone_number_id": "..."}`
  - Telegram: `{"bot_token": "..."}`
  - Instagram: `{"access_token": "...", "app_id": "..."}`
- `rate_limits`: Platform-specific limits
  - Example: `{"messages_per_second": 10, "daily_limit": 10000}`
- `webhook_url`: Callback URL for receiving messages from platform

**Security**:
- `credentials` field encrypted at rest (PostgreSQL pgcrypto extension)
- Access restricted via database roles and application-level permissions

---

### 4. audit_logs

**Purpose**: Track identity mapping operations and configuration changes

```sql
CREATE TABLE audit_logs (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'LINK', 'UNLINK')),
    performed_by UUID REFERENCES users(user_id),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changes JSONB,
    ip_address INET,
    user_agent TEXT
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_performed_by ON audit_logs(performed_by);
```

**Validation Rules**:
- `entity_type`: Examples: 'USER', 'EXTERNAL_IDENTITY', 'CHANNEL_CONFIGURATION'
- `changes`: JSON diff of before/after state
- Immutable: No UPDATE or DELETE operations allowed on audit logs

**Compliance**:
- Supports FR-035 (audit log requirement)
- Retention policy: 7 years (configurable based on compliance requirements)

---

## Message Store (MongoDB)

### 1. messages Collection

**Purpose**: Stores all messages (text and file references)

```javascript
{
  _id: ObjectId("..."),
  message_id: "550e8400-e29b-41d4-a716-446655440000", // UUIDv4 (unique index)
  conversation_id: "conv-abc123",
  sender_id: "user-uuid",
  recipient_ids: ["user-uuid-1", "user-uuid-2"], // Array for group messages
  content: "Hello, how can I help you?",
  content_type: "TEXT", // Enum: TEXT, FILE, IMAGE, VIDEO, AUDIO
  file_id: null, // Reference to files collection if content_type != TEXT
  channel: "WHATSAPP", // Enum: WHATSAPP, TELEGRAM, INSTAGRAM, INTERNAL
  status: "DELIVERED", // Enum: PENDING, SENT, DELIVERED, READ, FAILED
  timestamp: ISODate("2025-11-23T10:00:00Z"),
  metadata: {
    platform_message_id: "wamid.XXX", // External platform's message ID
    retry_count: 0,
    error_message: null
  },
  created_at: ISODate("2025-11-23T10:00:00Z"),
  updated_at: ISODate("2025-11-23T10:00:05Z")
}
```

**Indexes**:
```javascript
db.messages.createIndex({ message_id: 1 }, { unique: true });
db.messages.createIndex({ conversation_id: 1, timestamp: -1 }); // Sorted conversation history
db.messages.createIndex({ sender_id: 1, timestamp: -1 });
db.messages.createIndex({ status: 1, updated_at: 1 }); // For retry/monitoring queries
```

**Validation Rules**:
- `message_id`: UUIDv4 format, globally unique (FR-002)
- `conversation_id`: References conversations collection
- `content`: Max 10,000 characters for text (FR-003)
- `status` transitions:
  - PENDING → SENT → DELIVERED → READ
  - PENDING/SENT/DELIVERED → FAILED (on error)
  - Idempotent: Status can only progress forward (except to FAILED)

**Sharding Strategy**:
- Shard key: `conversation_id` (aligns with Kafka partitioning)
- Ensures all messages for a conversation are co-located on same shard

**Relationships**:
- Many-to-one with `conversations` (via conversation_id)
- Many-to-one with `files` (via file_id)

---

### 2. conversations Collection

**Purpose**: Represents message threads (1:1 or group)

```javascript
{
  _id: ObjectId("..."),
  conversation_id: "conv-abc123", // Unique identifier (unique index)
  conversation_type: "1:1", // Enum: 1:1, GROUP
  participants: [
    {
      user_id: "user-uuid-1",
      user_type: "AGENT",
      joined_at: ISODate("2025-11-23T09:00:00Z")
    },
    {
      user_id: "user-uuid-2",
      user_type: "CUSTOMER",
      joined_at: ISODate("2025-11-23T09:00:00Z")
    }
  ],
  primary_channel: "WHATSAPP",
  message_count: 42,
  last_message_at: ISODate("2025-11-23T10:00:00Z"),
  created_at: ISODate("2025-11-23T09:00:00Z"),
  updated_at: ISODate("2025-11-23T10:00:00Z"),
  archived: false,
  metadata: {
    tags: ["support", "billing"],
    priority: "HIGH"
  }
}
```

**Indexes**:
```javascript
db.conversations.createIndex({ conversation_id: 1 }, { unique: true });
db.conversations.createIndex({ "participants.user_id": 1, last_message_at: -1 });
db.conversations.createIndex({ primary_channel: 1, archived: 1 });
db.conversations.createIndex({ last_message_at: -1 }); // Recent conversations
```

**Validation Rules**:
- `conversation_type`: '1:1' requires exactly 2 participants, 'GROUP' allows 3-100 (FR-027)
- `participants`: Array of user objects with `user_id`, `user_type`, `joined_at`
- `message_count`: Incremented atomically on new message
- `archived`: Auto-set to true after 90 days of inactivity (configurable)

**State Transitions**:
- `message_count` and `last_message_at` updated on each new message
- `archived` can be toggled manually or automatically

---

### 3. files Collection

**Purpose**: Metadata for uploaded files

```javascript
{
  _id: ObjectId("..."),
  file_id: "file-550e8400", // UUIDv4 (unique index)
  message_id: "550e8400-e29b-41d4-a716-446655440000", // Reference to message
  filename: "invoice_2025.pdf",
  file_size: 2048576, // Bytes (max 2GB per FR-019)
  mime_type: "application/pdf",
  storage_url: "s3://chat4all-files/uploads/file-550e8400",
  thumbnail_url: "s3://chat4all-files/thumbnails/file-550e8400.jpg", // For images
  uploaded_at: ISODate("2025-11-23T10:00:00Z"),
  expires_at: ISODate("2025-11-24T10:00:00Z"), // 24-hour expiration (FR-021)
  scan_status: "CLEAN", // Enum: PENDING, CLEAN, INFECTED (FR-023)
  metadata: {
    uploader_ip: "192.168.1.1",
    original_filename: "Invoice 2025.pdf"
  }
}
```

**Indexes**:
```javascript
db.files.createIndex({ file_id: 1 }, { unique: true });
db.files.createIndex({ message_id: 1 });
db.files.createIndex({ expires_at: 1 }); // TTL index for auto-deletion
db.files.createIndex({ scan_status: 1 });
```

**Validation Rules**:
- `file_size`: Max 2GB (2,147,483,648 bytes) per FR-019
- `mime_type`: Validated against whitelist (FR-022):
  - Images: image/jpeg, image/png, image/gif
  - Documents: application/pdf, application/vnd.openxmlformats-officedocument.wordprocessingml.document
  - Videos: video/mp4, video/quicktime
- `scan_status`: Must be 'CLEAN' before file is accessible
- `expires_at`: TTL index automatically deletes expired files

**Lifecycle**:
1. File uploaded to S3 → `scan_status: PENDING`
2. Malware scan completes → `scan_status: CLEAN | INFECTED`
3. After 24 hours → TTL index removes document and triggers S3 deletion

---

## Entity Relationships

### PostgreSQL Relationships

```
users (1) ──────< external_identities (N)
  │
  └──────< audit_logs (N)

channel_configurations (standalone, referenced by services)
```

### MongoDB Relationships

```
conversations (1) ──────< messages (N)
                            │
                            └──────< files (0..1)
```

### Cross-Database References

- `messages.sender_id` → `users.user_id` (PostgreSQL)
- `messages.recipient_ids[]` → `users.user_id` (PostgreSQL)
- `conversations.participants[].user_id` → `users.user_id` (PostgreSQL)

**Note**: Cross-database joins are resolved at application layer (not database level). Services fetch user data from PostgreSQL separately when rendering messages.

---

## Data Retention & Archival

### Messages (MongoDB)
- **Active**: Unlimited retention (subject to storage costs)
- **Archived Conversations**: Messages retained but marked as `conversation.archived = true`
- **Deletion**: Manual deletion only (compliance with legal holds)

### Audit Logs (PostgreSQL)
- **Retention**: 7 years (default), configurable per jurisdiction
- **Archival**: Old logs (>1 year) moved to cold storage (S3 Glacier)

### Files (MongoDB + S3)
- **Active**: 24-hour expiration via `expires_at` TTL index (FR-021)
- **Extension**: Clients can request URL refresh if needed within 24 hours
- **Permanent Storage**: Files can be flagged as `permanent` to bypass TTL

---

## Migration Scripts

### PostgreSQL (Flyway)

```sql
-- V001__create_users_table.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- For credential encryption

-- [users table creation from above]
-- [external_identities table creation from above]
-- [channel_configurations table creation from above]
-- [audit_logs table creation from above]
```

### MongoDB (Init Script)

```javascript
// mongo-init.js
db = db.getSiblingDB('chat4all');

// Create collections with validators
db.createCollection("messages", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["message_id", "conversation_id", "sender_id", "content_type", "channel", "status", "timestamp"],
      properties: {
        message_id: { bsonType: "string", pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$" },
        content_type: { enum: ["TEXT", "FILE", "IMAGE", "VIDEO", "AUDIO"] },
        channel: { enum: ["WHATSAPP", "TELEGRAM", "INSTAGRAM", "INTERNAL"] },
        status: { enum: ["PENDING", "SENT", "DELIVERED", "READ", "FAILED"] }
      }
    }
  }
});

// Create indexes
db.messages.createIndex({ message_id: 1 }, { unique: true });
db.messages.createIndex({ conversation_id: 1, timestamp: -1 });
db.messages.createIndex({ sender_id: 1, timestamp: -1 });
db.messages.createIndex({ status: 1, updated_at: 1 });

db.createCollection("conversations");
db.conversations.createIndex({ conversation_id: 1 }, { unique: true });
db.conversations.createIndex({ "participants.user_id": 1, last_message_at: -1 });

db.createCollection("files");
db.files.createIndex({ file_id: 1 }, { unique: true });
db.files.createIndex({ message_id: 1 });
db.files.createIndex({ expires_at: 1 }, { expireAfterSeconds: 0 }); // TTL index

// Enable sharding
sh.enableSharding("chat4all");
sh.shardCollection("chat4all.messages", { conversation_id: 1 });
```

---

## Performance Considerations

### PostgreSQL Optimization
- **Connection Pooling**: HikariCP with max 20 connections per service instance
- **Read Replicas**: Use read replicas for analytics queries, route reads via Spring Data
- **Partitioning**: Consider partitioning `audit_logs` by timestamp if volume exceeds 100M rows

### MongoDB Optimization
- **Covered Queries**: Most queries covered by indexes (no document scan)
- **Projection**: Only fetch required fields (e.g., message previews don't need full `metadata`)
- **Aggregation Pipeline**: Use for complex analytics (daily message counts, channel distribution)
- **Change Streams**: Enable for real-time updates to conversation views (optional)

---

## Consistency Guarantees

### PostgreSQL
- **Isolation Level**: READ COMMITTED (default)
- **Transactions**: ACID-compliant, used for multi-table operations (user + external_identity creation)

### MongoDB
- **Write Concern**: `majority` (ensures durability across replica set)
- **Read Concern**: `local` for reads (eventual consistency acceptable for message history)
- **Transactions**: Multi-document ACID transactions available but avoided for performance (designed around single-document atomicity)

### Cross-Database Consistency
- **Pattern**: Eventual consistency between PostgreSQL and MongoDB
- **Example**: User created in PostgreSQL → Messages reference user_id immediately (no foreign key enforcement)
- **Handling**: Application validates user existence before creating message

---

## Summary

This data model supports:
- ✅ Horizontal scalability (MongoDB sharding, PostgreSQL read replicas)
- ✅ High availability (replica sets, multi-AZ deployments)
- ✅ Message delivery guarantees (unique message_id, status tracking)
- ✅ Causal ordering (conversation_id sharding aligns with Kafka partitions)
- ✅ Audit compliance (immutable audit_logs, 7-year retention)
- ✅ Pluggable architecture (channel_configurations table)

**Next**: Generate API contracts from this data model.
