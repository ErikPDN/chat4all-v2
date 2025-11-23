# Feature Specification: Chat4All v2 - Unified Messaging Platform

**Feature Branch**: `001-unified-messaging-platform`  
**Created**: 2025-11-23  
**Status**: Draft  
**Input**: User description: "Estamos construindo o **Chat4All v2**, uma plataforma de mensageria unificada de alta escala. O sistema deve rotear mensagens entre clientes internos e múltiplas plataformas externas (WhatsApp, Instagram, Telegram) com garantias estritas de entrega e ordem."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Send Text Message to External Platform (Priority: P1)

A customer service agent needs to send a text message to a customer's WhatsApp account through the unified platform.

**Why this priority**: This is the core value proposition - enabling communication across channels. Without this, there is no product.

**Independent Test**: Can be fully tested by sending a text message via API and verifying it arrives on WhatsApp. Delivers immediate value by enabling cross-platform messaging.

**Acceptance Scenarios**:

1. **Given** an authenticated agent with an active conversation, **When** they send a text message with valid content, **Then** the message is accepted (HTTP 202), assigned a unique message_id, and delivered to the recipient's WhatsApp within 5 seconds
2. **Given** a message is sent to WhatsApp, **When** the external platform confirms delivery, **Then** the message status is updated to DELIVERED and the agent receives a status notification
3. **Given** a conversation with multiple messages, **When** messages are sent in sequence, **Then** they arrive at the recipient in the exact same order they were sent

---

### User Story 2 - Receive Messages from External Platforms (Priority: P1)

A customer sends a message via WhatsApp/Instagram/Telegram and the agent receives it in the unified platform.

**Why this priority**: Bidirectional communication is essential. Agents must see incoming customer messages to respond effectively.

**Independent Test**: Can be tested by sending a message from WhatsApp and verifying the agent receives it via polling or webhook. Completes the core messaging loop.

**Acceptance Scenarios**:

1. **Given** a customer sends a WhatsApp message, **When** the message is received by the platform, **Then** it appears in the agent's conversation view within 2 seconds with correct sender identification
2. **Given** messages arrive from multiple channels (WhatsApp, Telegram, Instagram), **When** displayed to the agent, **Then** each message clearly indicates its source channel
3. **Given** a message contains special characters or emojis, **When** received from external platform, **Then** all characters are preserved and displayed correctly

---

### User Story 3 - Send File Attachments (Priority: P2)

An agent needs to send images, documents, or other files to customers across different platforms.

**Why this priority**: Many customer service interactions require sharing documents, receipts, images. Critical for complete communication but can follow basic text messaging.

**Independent Test**: Can be tested by uploading a file via API and verifying it's accessible to the recipient on their platform. Delivers enhanced communication capabilities.

**Acceptance Scenarios**:

1. **Given** an agent has a file (image, PDF, video) up to 100MB, **When** they attach and send it, **Then** the file is uploaded to object storage and a link is sent to the recipient's platform
2. **Given** a file is uploaded, **When** the upload completes, **Then** the message shows a preview (for images) or file metadata (name, size, type)
3. **Given** large files (500MB - 2GB), **When** uploaded, **Then** upload progress is tracked and failures trigger automatic retry with exponential backoff

---

### User Story 4 - Group Conversation Support (Priority: P2)

Agents need to participate in group conversations where multiple customers or agents collaborate.

**Why this priority**: Many customer support scenarios involve multiple participants. Important but can be built after 1:1 messaging is stable.

**Independent Test**: Can be tested by creating a group conversation with 3+ participants and verifying all members receive all messages. Extends core messaging to multi-party scenarios.

**Acceptance Scenarios**:

1. **Given** a group conversation with 5 participants, **When** any member sends a message, **Then** all other members receive it within 3 seconds
2. **Given** a new member is added to an existing group, **When** they join, **Then** they can see the conversation history from the point they joined (not earlier messages)
3. **Given** a group spans multiple platforms (WhatsApp group + Telegram group), **When** a message is sent, **Then** it's delivered to all platforms maintaining thread continuity

---

### User Story 5 - Identity Mapping Across Platforms (Priority: P3)

The system needs to recognize that a customer using WhatsApp +55-11-99999-9999 is the same person as telegram_user_@johndoe.

**Why this priority**: Improves user experience by consolidating customer history, but core messaging works without it. Can be added incrementally.

**Independent Test**: Can be tested by linking two external identities to one internal user profile and verifying messages from both channels appear in a unified view.

**Acceptance Scenarios**:

1. **Given** a customer has WhatsApp and Telegram accounts, **When** an admin links both identities to the same internal profile, **Then** all future messages from either platform are associated with that unified profile
2. **Given** a customer messages from an unlinked account, **When** the system detects potential matches (phone number, email), **Then** it suggests linking to the agent for approval
3. **Given** multiple identities are linked, **When** viewing conversation history, **Then** messages from all channels are displayed chronologically with clear channel indicators

---

### Edge Cases

- What happens when an external platform (WhatsApp API) is down or returns errors?
  - System retries with exponential backoff (max 3 attempts), messages go to dead-letter queue, agents are notified of delivery failure
  
- What happens when a message is sent twice with the same `message_id`?
  - System detects duplicate via `message_id`, acknowledges receipt but does not re-process or re-deliver

- What happens when messages arrive out of order due to network delays?
  - Messages are buffered and reordered using timestamp + `message_id` tie-breaking before delivery to ensure conversation coherence

- What happens when a file upload fails midway?
  - System supports resumable uploads using multipart upload protocols, clients can retry from the last successful chunk

- What happens when a conversation has no activity for extended periods?
  - Conversations remain accessible but may be archived after 90 days of inactivity (configurable), with option to reactivate

- What happens when rate limits from external platforms are exceeded?
  - System implements intelligent throttling and queueing to stay within platform limits, notifies agents when messages are delayed

## Requirements *(mandatory)*

### Functional Requirements

#### Message Handling

- **FR-001**: System MUST accept message send requests via REST API with required fields: `message_id` (UUIDv4), `conversation_id`, `sender_id`, `content`, `channel_type`
- **FR-002**: System MUST assign globally unique `message_id` (UUIDv4) to each message at creation time if not provided by client
- **FR-003**: System MUST validate message content and reject messages exceeding size limits (text: 10,000 characters, files: 2GB)
- **FR-004**: System MUST return HTTP 202 (Accepted) immediately upon valid message submission without waiting for external delivery
- **FR-005**: System MUST persist messages to the Message Store before attempting external delivery
- **FR-006**: System MUST implement idempotent message processing using `message_id` for deduplication
- **FR-007**: System MUST preserve message ordering within a conversation using `conversation_id` partitioning
- **FR-008**: System MUST support retry logic with exponential backoff (max 3 attempts) for failed deliveries
- **FR-009**: System MUST move messages to dead-letter queue after exceeding retry limits
- **FR-010**: System MUST track message status lifecycle: PENDING → SENT → DELIVERED → READ (or FAILED at any stage)

#### Channel Integration

- **FR-011**: System MUST support integration with WhatsApp Business API for sending and receiving messages
- **FR-012**: System MUST support integration with Instagram Messaging API for sending and receiving messages
- **FR-013**: System MUST support integration with Telegram Bot API for sending and receiving messages
- **FR-014**: System MUST implement pluggable connector architecture allowing new channels to be added without core system changes
- **FR-015**: System MUST isolate connector failures to prevent impact on other channels (circuit breaker pattern)
- **FR-016**: System MUST validate external platform credentials and configuration before enabling a channel
- **FR-017**: System MUST handle webhook callbacks from external platforms for delivery status updates
- **FR-018**: System MUST map internal message formats to platform-specific formats (e.g., WhatsApp message templates)

#### File Handling

- **FR-019**: System MUST support file uploads up to 2GB in size
- **FR-020**: System MUST store files in object storage (S3-compatible) separate from message metadata
- **FR-021**: System MUST generate secure, time-limited URLs for file access (expiring after 24 hours)
- **FR-022**: System MUST validate file types against allowed list (images: jpg, png, gif; documents: pdf, docx; videos: mp4, mov)
- **FR-023**: System MUST scan uploaded files for malware before making them accessible
- **FR-024**: System MUST support resumable uploads for files larger than 100MB
- **FR-025**: System MUST generate thumbnails for image files and store alongside original

#### Conversation Management

- **FR-026**: System MUST support 1:1 conversations between agent and customer
- **FR-027**: System MUST support group conversations with up to 100 participants
- **FR-028**: System MUST maintain conversation history for retrieval via API
- **FR-029**: System MUST associate each conversation with a primary channel but allow messages from multiple channels in the same conversation thread
- **FR-030**: System MUST provide conversation metadata including: creation time, participant list, message count, last activity timestamp

#### Identity & Authentication

- **FR-031**: System MUST authenticate all API requests using API keys or OAuth2 bearer tokens
- **FR-032**: System MUST map internal user IDs to external platform identities (WhatsApp phone numbers, Telegram usernames, Instagram handles)
- **FR-033**: System MUST allow administrators to link multiple external identities to a single internal user profile
- **FR-034**: System MUST support identity verification workflows for high-security channels
- **FR-035**: System MUST maintain audit logs of all identity mapping operations

#### Observability & Monitoring

- **FR-036**: System MUST emit structured logs in JSON format with fields: timestamp, level, service, trace_id, message
- **FR-037**: System MUST expose Prometheus metrics endpoint on all services including message throughput, latency, error rates
- **FR-038**: System MUST implement distributed tracing using OpenTelemetry with context propagation across service boundaries
- **FR-039**: System MUST provide health check endpoints for all services reporting UP/DOWN status
- **FR-040**: System MUST alert when message delivery latency exceeds 5 seconds (P95)

### Key Entities

- **Message**: Represents a single communication unit with attributes: `message_id` (UUIDv4), `conversation_id`, `sender_id`, `recipient_id(s)`, `content` (text or file reference), `channel`, `timestamp`, `status`, `metadata` (platform-specific data)

- **Conversation**: Represents a message thread with attributes: `conversation_id`, `conversation_type` (1:1 or GROUP), `participants` (list of user IDs), `primary_channel`, `created_at`, `last_activity_at`, `metadata`

- **User**: Represents an internal user with attributes: `user_id`, `display_name`, `user_type` (AGENT or CUSTOMER), `external_identities` (list of platform-specific IDs), `created_at`, `metadata`

- **External Identity**: Links internal users to platform accounts with attributes: `identity_id`, `user_id`, `platform` (WHATSAPP, TELEGRAM, INSTAGRAM), `platform_user_id` (phone number, username, handle), `verified`, `linked_at`

- **Channel Configuration**: Platform integration settings with attributes: `channel_id`, `platform`, `credentials` (API keys, tokens), `webhook_url`, `rate_limits`, `enabled`, `created_at`, `updated_at`

- **File Attachment**: Metadata for uploaded files with attributes: `file_id`, `message_id`, `filename`, `file_size`, `mime_type`, `storage_url`, `thumbnail_url` (for images), `uploaded_at`, `expires_at`

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can send a text message and see confirmation of acceptance within 500 milliseconds
- **SC-002**: 95% of messages are delivered to external platforms within 5 seconds of submission
- **SC-003**: System handles 10,000 concurrent conversations without performance degradation
- **SC-004**: Message ordering is preserved in 100% of conversations (zero out-of-order deliveries)
- **SC-005**: System achieves 99.95% uptime measured over 30-day periods
- **SC-006**: File uploads up to 100MB complete within 30 seconds on standard broadband connections
- **SC-007**: Duplicate messages (same `message_id`) are detected and prevented from re-delivery in 100% of cases
- **SC-008**: Failed message deliveries are retried automatically with zero manual intervention required
- **SC-009**: Agents can view conversation history going back 1 year within 2 seconds of request
- **SC-010**: System supports simultaneous integration with at least 3 external platforms (WhatsApp, Telegram, Instagram)
- **SC-011**: New channel connectors can be deployed without downtime to existing channels
- **SC-012**: 99% of webhook callbacks from external platforms are processed within 1 second of receipt

## Assumptions

- External platforms (WhatsApp, Telegram, Instagram) provide stable APIs with documented rate limits
- Users have valid credentials for external platform APIs (WhatsApp Business API access, Telegram Bot Tokens, etc.)
- Network connectivity between platform and external APIs is generally reliable (standard internet latency)
- Message content complies with external platform policies (no spam, illegal content, etc.)
- File storage infrastructure (S3-compatible object storage) is available and configured
- Kafka cluster is deployed and operational for message broker functionality
- PostgreSQL and MongoDB/Cassandra clusters are deployed for metadata and message storage respectively
- Authentication infrastructure (API key management or OAuth2 provider) is available
- Standard HTTP/HTTPS protocols are sufficient for API communication
- Time synchronization (NTP) is configured across all services for accurate timestamp ordering

## Dependencies

- **External APIs**: WhatsApp Business API, Telegram Bot API, Instagram Messaging API
- **Infrastructure**: Kubernetes cluster, Kafka brokers, PostgreSQL database, MongoDB/Cassandra cluster, Redis cluster, S3-compatible object storage
- **Observability Stack**: Prometheus, Grafana, Loki/Elasticsearch, Jaeger/Tempo (OpenTelemetry compatible)
- **Security**: Malware scanning service for file uploads, TLS/SSL certificates for HTTPS endpoints

## Out of Scope (Initial Release)

- Voice calling or video conferencing features
- End-to-end encryption (relies on platform-provided encryption)
- Message translation or language detection
- AI-powered chatbots or automated responses
- Advanced analytics or reporting dashboards
- Mobile native applications (API-first, UIs can be built later)
- Multi-tenancy support (single organization deployment initially)
- Message editing or deletion after delivery
- Read receipts from all platforms (depends on platform capabilities)
- Custom message templates or rich media cards (beyond basic file attachments)
