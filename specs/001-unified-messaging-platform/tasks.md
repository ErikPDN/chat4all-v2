---
description: "Task list for Chat4All v2 - Unified Messaging Platform"
---

# Tasks: Chat4All v2 - Unified Messaging Platform

**Input**: Design documents from `/specs/001-unified-messaging-platform/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Not included in this task list (not explicitly requested in specification)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4, US5)
- Include exact file paths in descriptions

## Path Conventions

Based on plan.md structure: Microservices architecture with services/, shared/, infrastructure/

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Create Maven parent POM at `/pom.xml` with Java 21, Spring Boot 3.2+, dependency management for all modules
- [x] T002 [P] Create project directory structure: services/, shared/, infrastructure/, docs/ per plan.md
- [x] T003 [P] Initialize Git repository with .gitignore (Maven, Java, IDE files)
- [x] T004 [P] Create Docker Compose file at `/docker-compose.yml` with Kafka, PostgreSQL, MongoDB, Redis, MinIO, Prometheus, Grafana, Jaeger
- [x] T005 [P] Create README.md with project overview, architecture diagram, quick start instructions
- [x] T006 [P] Setup Maven module structure: services/api-gateway, services/message-service, services/router-service, services/user-service, services/file-service
- [x] T007 [P] Setup Maven module structure: services/connectors/whatsapp-connector, services/connectors/telegram-connector, services/connectors/instagram-connector
- [x] T008 [P] Setup Maven module structure: shared/common-domain, shared/connector-sdk, shared/observability
- [x] T009 [P] Create CI/CD workflow at `.github/workflows/build.yml` with Maven build, unit tests, Docker image build
- [x] T010 [P] Create CI/CD workflow at `.github/workflows/test.yml` with Testcontainers integration tests

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Shared Libraries

- [x] T011 [P] Create Message DTO in `shared/common-domain/src/main/java/com/chat4all/common/model/MessageDTO.java` with fields: messageId, conversationId, senderId, content, channel, timestamp, status
- [x] T012 [P] Create Conversation DTO in `shared/common-domain/src/main/java/com/chat4all/common/model/ConversationDTO.java` with fields: conversationId, type, participants, primaryChannel, createdAt
- [x] T013 [P] Create Channel enum in `shared/common-domain/src/main/java/com/chat4all/common/constant/Channel.java` with values: WHATSAPP, TELEGRAM, INSTAGRAM, INTERNAL
- [x] T014 [P] Create MessageStatus enum in `shared/common-domain/src/main/java/com/chat4all/common/constant/MessageStatus.java` with values: PENDING, SENT, DELIVERED, READ, FAILED
- [x] T015 [P] Create MessageEvent schema in `shared/common-domain/src/main/java/com/chat4all/common/event/MessageEvent.java` for Kafka events
- [x] T016 [P] Create MessageConnector interface in `shared/connector-sdk/src/main/java/com/chat4all/connector/api/MessageConnector.java` with methods: sendMessage(), receiveWebhook(), validateCredentials()
- [x] T017 [P] Create BaseConnector abstract class in `shared/connector-sdk/src/main/java/com/chat4all/connector/api/BaseConnector.java` with circuit breaker, retry logic using Resilience4j
- [x] T018 [P] Create JSON log encoder in `shared/observability/src/main/java/com/chat4all/observability/logging/JsonLogEncoder.java` with fields: timestamp, level, service, trace_id, message
- [x] T019 [P] Create custom Micrometer metrics in `shared/observability/src/main/java/com/chat4all/observability/metrics/MessageMetrics.java` for message throughput, latency, error rates
- [x] T020 [P] Create OpenTelemetry configuration in `shared/observability/src/main/java/com/chat4all/observability/tracing/TracingConfig.java`

### Database Schemas

- [x] T021 Create Flyway migration `V001__create_users_table.sql` in `services/user-service/src/main/resources/db/migration/` (users table with UUID PK, display_name, user_type ENUM, metadata JSONB)
- [x] T022 Create Flyway migration `V002__create_external_identities_table.sql` in `services/user-service/src/main/resources/db/migration/` (external_identities table with platform, platform_user_id, verified, unique constraint)
- [x] T023 Create Flyway migration `V003__create_channel_configurations_table.sql` in `services/user-service/src/main/resources/db/migration/` (channel_configurations table with encrypted credentials JSONB, webhook_url, rate_limits)
- [x] T024 Create Flyway migration `V004__create_audit_logs_table.sql` in `services/user-service/src/main/resources/db/migration/` (audit_logs table immutable, 7-year retention)
- [x] T025 Create MongoDB initialization script `mongo-init.js` in `infrastructure/mongodb/` with collections: messages (sharded by conversation_id), conversations, files (TTL index 24h expiration)
- [x] T026 Create MongoDB schema validators in `mongo-init.js` for messages collection (message_id UUIDv4 unique, content max 10K chars, status enum)

### Configuration & Security

- [x] T027 [P] Create Spring Security OAuth2 configuration in `services/api-gateway/src/main/java/com/chat4all/gateway/security/OAuth2Config.java` with scopes: messages:read, messages:write
- [x] T028 [P] Create API Gateway routes configuration in `services/api-gateway/src/main/resources/application.yml` routing to message-service, user-service, file-service
- [x] T029 [P] Create global exception handler in `services/api-gateway/src/main/java/com/chat4all/gateway/filter/GlobalErrorFilter.java` for standardized error responses
- [ ] T030 [P] Create health check endpoints in all services at `/actuator/health` using Spring Boot Actuator

### Kafka Infrastructure

- [x] T031 Create Kafka topic configuration in `infrastructure/kafka/topics.json` with chat-events topic (partitions: 10, replication: 3, retention: 7 days)
- [x] T032 [P] Create Kafka producer configuration in `services/message-service/src/main/java/com/chat4all/message/kafka/KafkaProducerConfig.java` with idempotence enabled
- [x] T033 [P] Create Kafka consumer configuration in `services/router-service/src/main/java/com/chat4all/router/consumer/KafkaConsumerConfig.java` with auto-offset-commit disabled

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Send Text Message to External Platform (Priority: P1) 🎯 MVP

**Goal**: Enable agents to send text messages to customers via WhatsApp/Telegram/Instagram with delivery tracking

**Independent Test**: Send text message via POST /messages API → verify HTTP 202 response → verify message appears in MongoDB → verify Kafka event published → verify external API receives message (mock)

### Implementation for User Story 1

#### Message Service - Inbound API

- [x] T034 [P] [US1] Create Message entity in `services/message-service/src/main/java/com/chat4all/message/domain/Message.java` with @Document annotation for MongoDB (fields: messageId, conversationId, senderId, content, channel, timestamp, status, metadata)
- [x] T035 [P] [US1] Create Conversation entity in `services/message-service/src/main/java/com/chat4all/message/domain/Conversation.java` with @Document annotation for MongoDB
- [x] T036 [US1] Create MessageRepository in `services/message-service/src/main/java/com/chat4all/message/repository/MessageRepository.java` extending MongoRepository with custom query methods: findByConversationId, findByMessageId
- [x] T037 [US1] Create ConversationRepository in `services/message-service/src/main/java/com/chat4all/message/repository/ConversationRepository.java` extending MongoRepository
- [x] T038 [US1] Implement IdempotencyService in `services/message-service/src/main/java/com/chat4all/message/service/IdempotencyService.java` using Redis to check message_id existence before processing (FR-006)
- [x] T039 [US1] Implement MessageService in `services/message-service/src/main/java/com/chat4all/message/service/MessageService.java` with methods: acceptMessage() (persist to MongoDB), updateStatus(), getMessageById()
- [x] T040 [US1] Create SendMessageRequest DTO in `services/message-service/src/main/java/com/chat4all/message/api/dto/SendMessageRequest.java` with validation annotations (@NotNull, @Size)
- [x] T041 [US1] Create MessageController in `services/message-service/src/main/java/com/chat4all/message/api/MessageController.java` implementing POST /messages endpoint returning HTTP 202
- [x] T042 [US1] Create MessageStatusController in `services/message-service/src/main/java/com/chat4all/message/api/MessageController.java` implementing GET /messages/{id}/status endpoint
- [x] T043 [US1] Implement Kafka message producer in `services/message-service/src/main/java/com/chat4all/message/kafka/MessageProducer.java` publishing to chat-events topic with conversation_id as partition key

#### Router Service - Message Routing

- [x] T044 [US1] Create MessageEventConsumer in `services/router-service/src/main/java/com/chat4all/router/consumer/MessageEventConsumer.java` consuming from chat-events topic
- [x] T045 [US1] Implement DeduplicationHandler in `services/router-service/src/main/java/com/chat4all/router/handler/DeduplicationHandler.java` checking message_id against Redis and MongoDB (FR-006)
- [x] T046 [US1] Implement RoutingHandler in `services/router-service/src/main/java/com/chat4all/router/handler/RoutingHandler.java` determining target connector based on channel type
- [x] T047 [US1] Create ConnectorClient interface in `services/router-service/src/main/java/com/chat4all/router/connector/ConnectorClient.java` with method: deliverMessage(messageId, connectorUrl)
- [x] T048 [US1] Implement RetryHandler in `services/router-service/src/main/java/com/chat4all/router/retry/RetryHandler.java` with exponential backoff (max 3 attempts) using Resilience4j (FR-008)
- [x] T049 [US1] Implement DeadLetterQueueHandler in `services/router-service/src/main/java/com/chat4all/router/dlq/DLQHandler.java` for messages exceeding retry limits (FR-009)
- [x] T050 [US1] Create StatusUpdateProducer in `services/router-service/src/main/java/com/chat4all/router/kafka/StatusUpdateProducer.java` publishing status changes back to Kafka

#### Status Tracking

- [x] T051 [US1] Create MessageStatusHistory entity in `services/message-service/src/main/java/com/chat4all/message/domain/MessageStatusHistory.java` tracking status transitions (PENDING→SENT→DELIVERED→READ)
- [x] T052 [US1] Implement StatusUpdateConsumer in `services/message-service/src/main/java/com/chat4all/message/kafka/StatusUpdateConsumer.java` updating message status in MongoDB
- [x] T053 [US1] Create WebSocket endpoint in `services/message-service/src/main/java/com/chat4all/message/api/MessageStatusWebSocket.java` at /ws/messages/{id}/status for real-time status updates

**Checkpoint**: At this point, User Story 1 should be fully functional - agents can send messages, track delivery status, and messages are routed (to mock connectors)

---

## Phase 4: User Story 2 - Receive Messages from External Platforms (Priority: P1) 🎯 MVP

**Goal**: Enable agents to receive messages from customers sent via WhatsApp/Telegram/Instagram

**Independent Test**: Simulate webhook POST from WhatsApp → verify message persisted to MongoDB → verify agent retrieves message via GET /conversations/{id}/messages

### Implementation for User Story 2

- [x] T054 [P] [US2] Create WebhookController in `services/message-service/src/main/java/com/chat4all/message/api/WebhookController.java` implementing POST /webhooks/{channel} endpoint with signature validation
- [x] T055 [P] [US2] Create InboundMessageDTO in `services/message-service/src/main/java/com/chat4all/message/api/dto/InboundMessageDTO.java` for webhook payloads (platform-agnostic format)
- [x] T056 [US2] Implement WebhookProcessorService in `services/message-service/src/main/java/com/chat4all/message/service/WebhookProcessorService.java` validating webhook signatures, transforming platform formats to internal format
- [x] T057 [US2] Extend MessageService to handle inbound messages: storeInboundMessage() method persisting to MongoDB with status=RECEIVED
- [x] T058 [US2] Create ConversationController in `services/message-service/src/main/java/com/chat4all/message/api/ConversationController.java` implementing GET /conversations endpoint with participant_id filter
- [x] T059 [US2] Create ConversationController endpoint GET /conversations/{id}/messages with pagination (before cursor, limit) returning message history
- [x] T060 [US2] Implement ConversationService in `services/message-service/src/main/java/com/chat4all/message/service/ConversationService.java` with methods: getConversation(), getMessages(), updateLastActivity()
- [x] T061 [US2] Add MongoDB index on messages collection for {conversation_id: 1, timestamp: -1} to optimize history retrieval (SC-009 <2s requirement)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work - full bidirectional messaging is functional

---

## Phase 5: User Story 3 - Send File Attachments (Priority: P2)

**Goal**: Enable agents to send images, documents, videos to customers with malware scanning and thumbnail generation

**Independent Test**: Upload file via POST /files/initiate → verify S3 upload → send message with file_id → verify recipient receives file link

### Implementation for User Story 3

- [x] T062 [P] [US3] Create FileAttachment entity in `services/file-service/src/main/java/com/chat4all/file/domain/FileAttachment.java` with @Document annotation (fields: fileId, messageId, filename, fileSize, mimeType, storageUrl, thumbnailUrl, uploadedAt, expiresAt)
- [x] T063 [P] [US3] Create FileRepository in `services/file-service/src/main/java/com/chat4all/file/repository/FileRepository.java` extending MongoRepository
- [x] T064 [US3] Implement S3StorageService in `services/file-service/src/main/java/com/chat4all/file/storage/S3StorageService.java` using AWS SDK for Java v2 with methods: generatePresignedUploadUrl(), generatePresignedDownloadUrl()
- [x] T065 [US3] Create FileController in `services/file-service/src/main/java/com/chat4all/file/api/FileController.java` implementing POST /files/initiate endpoint returning presigned S3 URL and fileId
- [x] T066 [US3] Implement multipart upload support in `services/file-service/src/main/java/com/chat4all/file/storage/MultipartUploadService.java` for files >100MB (FR-024)
- [x] T067 [US3] Implement file type validation in `services/file-service/src/main/java/com/chat4all/file/service/FileValidationService.java` checking mime types against whitelist (jpg, png, pdf, docx, mp4, etc.) (FR-022)
- [x] T068 [US3] Integrate malware scanning in `services/file-service/src/main/java/com/chat4all/file/scan/MalwareScanService.java` using ClamAV or cloud service API (FR-023)
- [x] T069 [US3] Implement thumbnail generation in `services/file-service/src/main/java/com/chat4all/file/thumbnail/ThumbnailService.java` using Thumbnailator library for images (FR-025)
- [x] T070 [US3] Create FileUploadCompleteEvent in `shared/common-domain/src/main/java/com/chat4all/common/event/FileUploadCompleteEvent.java` for Kafka
- [x] T071 [US3] Update Message entity to include fileAttachments field (List<FileReference>) in `services/message-service/src/main/java/com/chat4all/message/domain/Message.java`
- [x] T072 [US3] Update SendMessageRequest DTO to accept fileIds array in `services/message-service/src/main/java/com/chat4all/message/api/dto/SendMessageRequest.java`
- [x] T073 [US3] Create MongoDB TTL index on files collection with expiresAt field (24h expiration) in mongo-init.js

**Checkpoint**: File attachments are fully functional - agents can upload and send files to customers

---

## Phase 6: User Story 4 - Group Conversation Support (Priority: P2)

**Goal**: Enable multi-party conversations where messages are delivered to all participants

**Independent Test**: Create group via POST /conversations with 3+ participants → send message → verify all participants receive message

### Implementation for User Story 4

- [X] T074 [P] [US4] Update Conversation entity to support conversationType ENUM (ONE_TO_ONE, GROUP) in `services/message-service/src/main/java/com/chat4all/message/domain/Conversation.java`
- [X] T075 [P] [US4] Add participants field (List<String> userIds) to Conversation entity with validation (max 100 participants per FR-027)
- [X] T076 [US4] Create ConversationController endpoint POST /conversations in `services/message-service/src/main/java/com/chat4all/message/api/ConversationController.java` accepting CreateConversationRequest
- [X] T077 [US4] Implement ConversationService.createConversation() method in `services/message-service/src/main/java/com/chat4all/message/service/ConversationService.java` validating participants exist
- [X] T078 [US4] Update RoutingHandler to support multi-recipient delivery in `services/router-service/src/main/java/com/chat4all/router/handler/RoutingHandler.java` iterating over conversation.participants
- [X] T079 [US4] Implement ParticipantManager in `services/message-service/src/main/java/com/chat4all/message/service/ParticipantManager.java` with methods: addParticipant(), removeParticipant(), getConversationHistory()
- [X] T080 [US4] Add business logic: new participants see history from join point forward (not earlier messages) in ParticipantManager

**Checkpoint**: Group conversations are fully functional - multi-party messaging works end-to-end

---

## Phase 7: User Story 5 - Identity Mapping Across Platforms (Priority: P3)

**Goal**: Link multiple external platform identities (WhatsApp, Telegram, Instagram) to a single internal user profile

**Independent Test**: Create user via POST /users → link WhatsApp identity → link Telegram identity → verify messages from both platforms associate with same user

### Implementation for User Story 5

- [x] T081 [P] [US5] Create User entity in `services/user-service/src/main/java/com/chat4all/user/domain/User.java` with @Entity annotation for PostgreSQL (fields: userId UUID PK, displayName, userType ENUM, metadata JSONB)
- [x] T082 [P] [US5] Create ExternalIdentity entity in `services/user-service/src/main/java/com/chat4all/user/domain/ExternalIdentity.java` with @Entity annotation (fields: identityId, userId FK, platform, platformUserId, verified, linkedAt)
- [x] T083 [P] [US5] Create UserRepository in `services/user-service/src/main/java/com/chat4all/user/repository/UserRepository.java` extending JpaRepository
- [x] T084 [P] [US5] Create ExternalIdentityRepository in `services/user-service/src/main/java/com/chat4all/user/repository/ExternalIdentityRepository.java` extending JpaRepository with custom queries: findByPlatformAndPlatformUserId
- [x] T085 [US5] Implement UserService in `services/user-service/src/main/java/com/chat4all/user/service/UserService.java` with methods: createUser(), getUser(), updateUser()
- [x] T086 [US5] Implement IdentityMappingService in `services/user-service/src/main/java/com/chat4all/user/service/IdentityMappingService.java` with methods: linkIdentity(), unlinkIdentity(), suggestMatches()
- [x] T087 [US5] Create UserController in `services/user-service/src/main/java/com/chat4all/user/api/UserController.java` implementing POST /users, GET /users/{id}, GET /users endpoints
- [x] T088 [US5] Create IdentityController in `services/user-service/src/main/java/com/chat4all/user/api/IdentityController.java` implementing POST /users/{id}/identities, DELETE /users/{id}/identities/{identityId}
- [ ] T089 [US5] Implement identity verification workflow in `services/user-service/src/main/java/com/chat4all/user/service/VerificationService.java` for high-security channels (FR-034)
- [ ] T090 [US5] Create audit logging for identity operations in `services/user-service/src/main/java/com/chat4all/user/service/AuditService.java` writing to audit_logs table (FR-035)
- [x] T091 [US5] Add unique constraint on external_identities table (platform, platform_user_id) in Flyway migration V002
- [ ] T092 [US5] Implement identity suggestion algorithm in IdentityMappingService.suggestMatches() comparing phone numbers, email addresses for potential matches

**Checkpoint**: Identity mapping is complete - users can have unified profiles across multiple platforms

---

## Phase 8: Connector Implementations (Parallel Work)

**Goal**: Implement platform-specific connectors for WhatsApp, Telegram, Instagram following MessageConnector interface

### WhatsApp Connector

- [x] T093 [P] [Connector] Create WhatsAppConnector in `services/connectors/whatsapp-connector/src/main/java/com/chat4all/connector/whatsapp/WhatsAppConnector.java` implementing MessageConnector interface
- [x] T094 [P] [Connector] Implement WhatsAppApiClient in `services/connectors/whatsapp-connector/src/main/java/com/chat4all/connector/whatsapp/client/WhatsAppApiClient.java` using Spring WebClient with Resilience4j circuit breaker
- [x] T095 [P] [Connector] Create MessageTransformer in `services/connectors/whatsapp-connector/src/main/java/com/chat4all/connector/whatsapp/transformer/MessageTransformer.java` converting internal Message format to WhatsApp API format
- [x] T096 [P] [Connector] Implement webhook handler in `services/connectors/whatsapp-connector/src/main/java/com/chat4all/connector/whatsapp/api/WebhookController.java` for status updates and incoming messages with signature validation
- [x] T097 [P] [Connector] Create WhatsApp configuration in `services/connectors/whatsapp-connector/src/main/resources/application.yml` with credentials, API URL, rate limits
- [x] T098 [P] [Connector] Implement credential validation in WhatsAppConnector.validateCredentials() method calling WhatsApp API health check

### Telegram Connector

- [x] T099 [P] [Connector] Create TelegramConnector in `services/connectors/telegram-connector/src/main/java/com/chat4all/connector/telegram/TelegramConnector.java` implementing MessageConnector interface
- [x] T100 [P] [Connector] Implement TelegramBotClient in `services/connectors/telegram-connector/src/main/java/com/chat4all/connector/telegram/client/TelegramBotClient.java` using Spring WebClient
- [x] T101 [P] [Connector] Create MessageTransformer in `services/connectors/telegram-connector/src/main/java/com/chat4all/connector/telegram/transformer/MessageTransformer.java` for Telegram Bot API format
- [x] T102 [P] [Connector] Implement webhook handler in `services/connectors/telegram-connector/src/main/java/com/chat4all/connector/telegram/api/WebhookController.java` for Telegram updates
- [x] T103 [P] [Connector] Create Telegram configuration in `services/connectors/telegram-connector/src/main/resources/application.yml` with bot token, API URL
- [x] T104 [P] [Connector] Implement credential validation in TelegramConnector.validateCredentials() using getMe API call

### Instagram Connector

- [x] T105 [P] [Connector] Create InstagramConnector in `services/connectors/instagram-connector/src/main/java/com/chat4all/connector/instagram/InstagramConnector.java` implementing MessageConnector interface
- [x] T106 [P] [Connector] Implement InstagramApiClient in `services/connectors/instagram-connector/src/main/java/com/chat4all/connector/instagram/client/InstagramApiClient.java` using Spring WebClient
- [x] T107 [P] [Connector] Create MessageTransformer in `services/connectors/instagram-connector/src/main/java/com/chat4all/connector/instagram/transformer/MessageTransformer.java` for Instagram Messaging API format
- [x] T108 [P] [Connector] Implement webhook handler in `services/connectors/instagram-connector/src/main/java/com/chat4all/connector/instagram/api/WebhookController.java` for Instagram events
- [x] T109 [P] [Connector] Create Instagram configuration in `services/connectors/instagram-connector/src/main/resources/application.yml` with credentials, API URL
- [x] T110 [P] [Connector] Implement credential validation in InstagramConnector.validateCredentials() method

**Checkpoint**: All three connectors implemented and independently deployable

---

## Phase 9: Observability & Monitoring

**Goal**: Implement full-stack observability per constitutional Principle VI

- [X] T111 [P] Configure Logback JSON encoder in all services at `src/main/resources/logback-spring.xml` using shared/observability library
- [X] T112 [P] Add Micrometer metrics to MessageController (message.send.count, message.send.latency) in `services/message-service/`
- [X] T113 [P] Add Micrometer metrics to RouterService (message.route.count, message.delivery.latency, message.retry.count) in `services/router-service/`
- [ ] T114 [P] Configure OpenTelemetry Java Agent in Dockerfile for all services with OTLP exporter to Jaeger
- [ ] T115 [P] Create Prometheus scrape configuration in `infrastructure/prometheus/prometheus.yml` targeting all services' /actuator/prometheus endpoints
- [ ] T116 [P] Create Grafana dashboards in `infrastructure/grafana/dashboards/` for: Message Throughput, Delivery Latency, Error Rates, System Health
- [ ] T117 [P] Configure Prometheus alerts in `infrastructure/prometheus/alerts.yml` for: message delivery latency >5s (FR-040), error rate >1%, service down
- [ ] T118 [P] Implement distributed tracing context propagation in KafkaProducer and KafkaConsumer using OpenTelemetry instrumentation

---

## Phase 10: Infrastructure & Deployment

**Goal**: Kubernetes manifests, Helm charts, and deployment automation

### Kubernetes Configuration

- [ ] T119 [P] Create Kustomize base for api-gateway in `infrastructure/kubernetes/base/api-gateway/` with Deployment, Service, ConfigMap
- [ ] T120 [P] Create Kustomize base for message-service in `infrastructure/kubernetes/base/message-service/` with Deployment, Service, ConfigMap, liveness/readiness probes
- [ ] T121 [P] Create Kustomize base for router-service in `infrastructure/kubernetes/base/router-service/` with Deployment (no Service - internal consumer)
- [ ] T122 [P] Create Kustomize base for user-service in `infrastructure/kubernetes/base/user-service/` with Deployment, Service, ConfigMap
- [ ] T123 [P] Create Kustomize base for file-service in `infrastructure/kubernetes/base/file-service/` with Deployment, Service, ConfigMap
- [ ] T124 [P] Create Kustomize base for whatsapp-connector in `infrastructure/kubernetes/base/connectors/whatsapp/` with Deployment, Service
- [ ] T125 [P] Create Kustomize base for telegram-connector in `infrastructure/kubernetes/base/connectors/telegram/` with Deployment, Service
- [ ] T126 [P] Create Kustomize base for instagram-connector in `infrastructure/kubernetes/base/connectors/instagram/` with Deployment, Service
- [ ] T127 [P] Create Kustomize overlays for dev environment in `infrastructure/kubernetes/overlays/dev/` with reduced replicas, resource limits
- [ ] T128 [P] Create Kustomize overlays for production in `infrastructure/kubernetes/overlays/production/` with HPA (Horizontal Pod Autoscaler), PodDisruptionBudget, increased resources
- [ ] T129 Create Ingress configuration in `infrastructure/kubernetes/base/ingress.yaml` routing traffic to api-gateway
- [ ] T130 Create NetworkPolicy in `infrastructure/kubernetes/base/network-policies/` restricting inter-service communication

### CI/CD & GitOps

- [ ] T131 [P] Create ArgoCD Application manifest in `infrastructure/argocd/application.yaml` for automated GitOps deployment
- [ ] T132 [P] Add integration test stage to `.github/workflows/test.yml` using Testcontainers with PostgreSQL, MongoDB, Kafka, Redis
- [ ] T133 [P] Add Docker image build and push to GitHub Container Registry in `.github/workflows/build.yml`
- [ ] T134 [P] Create deployment workflow `.github/workflows/deploy.yml` with manual approval for production, auto-deploy to dev/staging
- [ ] T135 Configure canary deployment in `infrastructure/kubernetes/overlays/production/rollout.yaml` using Argo Rollouts (10% → 50% → 100%)

### Database Management

- [ ] T136 Create Terraform module for PostgreSQL HA setup in `infrastructure/terraform/databases/postgres/` using cloud-managed service (RDS, Cloud SQL)
- [ ] T137 Create Terraform module for MongoDB replica set in `infrastructure/terraform/databases/mongodb/` with 3 nodes across availability zones
- [ ] T138 Create Terraform module for Redis cluster in `infrastructure/terraform/databases/redis/` with 3 master nodes, 3 replicas
- [ ] T139 Create Terraform module for Kafka cluster in `infrastructure/terraform/kafka/` with 3 brokers, ZooKeeper ensemble

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T140 [P] Create API documentation using Springdoc OpenAPI in all services generating /v3/api-docs endpoint
- [ ] T141 [P] Add rate limiting to API Gateway using Redis-backed rate limiter in `services/api-gateway/src/main/java/com/chat4all/gateway/filter/RateLimitFilter.java`
- [ ] T142 [P] Implement request/response logging filter in API Gateway at `services/api-gateway/src/main/java/com/chat4all/gateway/filter/LoggingFilter.java`
- [ ] T143 [P] Create runbook in `docs/runbooks/message-delivery-failure.md` for troubleshooting failed deliveries
- [ ] T144 [P] Create runbook in `docs/runbooks/scaling.md` for scaling services during traffic spikes
- [ ] T145 [P] Create Architecture Decision Record (ADR) in `docs/adr/001-dual-database-architecture.md` documenting MongoDB vs PostgreSQL choice
- [ ] T146 [P] Add performance tests with Gatling in `performance-tests/` simulating 10K concurrent conversations (SC-003)
- [ ] T147 [P] Validate quickstart.md works end-to-end: docker-compose up → send message → verify delivery
- [ ] T148 [P] Create security scanning workflow `.github/workflows/security.yml` using Snyk or Dependabot for dependency vulnerabilities
- [ ] T149 [P] Implement secrets management using Kubernetes Secrets or HashiCorp Vault for connector credentials
- [ ] T150 [P] Add correlation ID generation and propagation across all services in `shared/observability/src/main/java/com/chat4all/observability/correlation/CorrelationIdFilter.java`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - **BLOCKS all user stories**
- **User Story 1 (Phase 3)**: Depends on Foundational phase completion
- **User Story 2 (Phase 4)**: Depends on Foundational phase completion (can run parallel to US1 but integrates with it)
- **User Story 3 (Phase 5)**: Depends on Foundational + US1 completion (extends core messaging with files)
- **User Story 4 (Phase 6)**: Depends on Foundational + US1 completion (extends core messaging with groups)
- **User Story 5 (Phase 7)**: Depends on Foundational phase completion (independent of messaging core)
- **Connectors (Phase 8)**: Depends on Foundational + US1 completion (need routing infrastructure)
- **Observability (Phase 9)**: Can start after Foundational, runs parallel to user stories
- **Infrastructure (Phase 10)**: Can start after Foundational, finalized after all user stories complete
- **Polish (Phase 11)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: **MVP BLOCKING** - Must complete first. No dependencies on other stories.
- **User Story 2 (P1)**: **MVP BLOCKING** - Integrates with US1 (bidirectional messaging). Should complete immediately after US1.
- **User Story 3 (P2)**: Depends on US1 (extends messages with files) - Can run parallel to US4, US5
- **User Story 4 (P2)**: Depends on US1 (extends conversations to groups) - Can run parallel to US3, US5
- **User Story 5 (P3)**: Independent of messaging (identity system) - Can run parallel to US3, US4 after US1 completes

### Critical Path (MVP)

1. **Phase 1**: Setup (T001-T010) - ~2-3 days
2. **Phase 2**: Foundational (T011-T033) - ~5-7 days ⚠️ BLOCKS EVERYTHING
3. **Phase 3**: User Story 1 (T034-T053) - ~7-10 days 🎯 FIRST MVP VALUE
4. **Phase 4**: User Story 2 (T054-T061) - ~3-5 days 🎯 COMPLETE MVP
5. **Phase 8**: At least one connector (T093-T098 for WhatsApp) - ~3-5 days
6. **Phase 9**: Basic observability (T111-T118) - ~2-3 days
7. **Phase 10**: Kubernetes deployment (T119-T135) - ~3-5 days

**Total MVP Timeline**: ~25-38 days for bidirectional messaging with one platform

### Parallel Opportunities

**After Foundational Phase Completes:**

```bash
# Three developers can work in parallel:

Developer A: User Story 1 (T034-T053) - Core messaging
Developer B: User Story 5 (T081-T092) - Identity system (independent)
Developer C: Observability (T111-T118) - Metrics and tracing

# After US1 completes, expand parallelism:

Developer A: User Story 2 (T054-T061) - Inbound messages
Developer B: User Story 3 (T062-T073) - File attachments
Developer C: User Story 4 (T074-T080) - Group conversations
Developer D: WhatsApp Connector (T093-T098)
Developer E: Telegram Connector (T099-T104)
```

**Connector Development (Highly Parallel):**

All three connectors (WhatsApp, Telegram, Instagram) can be developed simultaneously by different team members:

```bash
# Launch all connectors in parallel after US1 completes:

Team Member 1: WhatsApp Connector (T093-T098)
Team Member 2: Telegram Connector (T099-T104)
Team Member 3: Instagram Connector (T105-T110)
```

---

## Parallel Example: User Story 1 - Send Message

```bash
# Launch all entity/DTO creation in parallel:
Task T034: "Create Message entity in services/message-service/src/main/java/com/chat4all/message/domain/Message.java"
Task T035: "Create Conversation entity in services/message-service/src/main/java/com/chat4all/message/domain/Conversation.java"
Task T040: "Create SendMessageRequest DTO in services/message-service/src/main/java/com/chat4all/message/api/dto/SendMessageRequest.java"

# After entities complete, launch repositories in parallel:
Task T036: "Create MessageRepository in services/message-service/src/main/java/com/chat4all/message/repository/MessageRepository.java"
Task T037: "Create ConversationRepository in services/message-service/src/main/java/com/chat4all/message/repository/ConversationRepository.java"

# After repositories complete, launch services in parallel:
Task T038: "Implement IdempotencyService in services/message-service/src/main/java/com/chat4all/message/service/IdempotencyService.java"
Task T039: "Implement MessageService in services/message-service/src/main/java/com/chat4all/message/service/MessageService.java"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 Only)

**Timeline**: ~4-6 weeks | **Team**: 2-3 developers

1. ✅ Complete Phase 1: Setup (T001-T010) → Project structure ready
2. ✅ Complete Phase 2: Foundational (T011-T033) → **CRITICAL BLOCKER** - Foundation ready
3. ✅ Complete Phase 3: User Story 1 (T034-T053) → Agents can send messages
4. ✅ Complete Phase 4: User Story 2 (T054-T061) → Agents can receive messages
5. ✅ Complete Phase 8: WhatsApp Connector only (T093-T098) → One working platform
6. ✅ Complete Phase 9: Basic observability (T111-T114) → Logs, metrics, tracing
7. ✅ Complete Phase 10: Kubernetes deployment (T119-T129, T131-T135) → Production-ready
8. **STOP and VALIDATE**: Test end-to-end bidirectional messaging with WhatsApp
9. Deploy/demo MVP

**MVP Value**: Agents can send and receive text messages via WhatsApp with full observability and production deployment.

### Incremental Delivery (Add Features)

**Post-MVP Phases**: Each adds value without breaking previous features

1. **Phase A** (US1 + US2) → MVP deployed ✅
2. **Phase B** (+ US3) → Add file attachments → Test → Deploy
3. **Phase C** (+ US4) → Add group conversations → Test → Deploy
4. **Phase D** (+ US5) → Add identity mapping → Test → Deploy
5. **Phase E** (+ Telegram & Instagram connectors) → Multi-platform support → Test → Deploy

Each phase is independently deployable and testable.

### Parallel Team Strategy (5+ Developers)

**Maximize throughput with parallel workstreams:**

1. **Week 1-2**: All team completes Setup + Foundational together (pair programming on complex parts)
2. **Week 3-4**: Once Foundational is done, split into parallel tracks:
   - **Track 1** (2 devs): User Story 1 - Send Messages (T034-T053)
   - **Track 2** (1 dev): User Story 5 - Identity Mapping (T081-T092) [independent]
   - **Track 3** (1 dev): Observability setup (T111-T118)
   - **Track 4** (1 dev): Kubernetes infrastructure (T119-T130)
3. **Week 5-6**: After US1 completes:
   - **Track 1** (1 dev): User Story 2 - Receive Messages (T054-T061)
   - **Track 2** (1 dev): User Story 3 - File Attachments (T062-T073)
   - **Track 3** (1 dev): User Story 4 - Group Conversations (T074-T080)
   - **Track 4-6** (3 devs): All three connectors in parallel (T093-T110)
4. **Week 7**: Integration, testing, polish (T140-T150)

**Result**: All features delivered in ~7 weeks vs ~15+ weeks sequential.

---

## Notes

- **[P] tasks**: Different files, no dependencies - safe to parallelize
- **[Story] labels**: Map tasks to user stories for traceability and independent testing
- **Tests omitted**: Not explicitly requested in specification (can be added later)
- **Commit strategy**: Commit after each task or logical group (e.g., all entities for a service)
- **Checkpoints**: Stop at each checkpoint to validate story works independently before proceeding
- **MVP focus**: Phases 1-4 + WhatsApp connector = viable product with bidirectional messaging
- **Constitutional compliance**: All tasks align with 7 principles + dual-database architecture (v1.1.0)
- **File paths**: Use exact paths from plan.md project structure for easy navigation
- **Avoid**: Vague tasks, same-file conflicts, cross-story dependencies that break independence

---

## Constitution Alignment

This task breakdown satisfies all constitutional principles:

| Principle | Task Evidence |
|-----------|---------------|
| **I. Horizontal Scalability** | T128 (HPA), T032-T033 (Kafka consumers), T020 (stateless services) |
| **II. High Availability** | T030 (health checks), T120-T123 (liveness/readiness probes), T128 (PodDisruptionBudget) |
| **III. Message Delivery Guarantees** | T038 (idempotency), T048 (retry), T049 (DLQ), T032 (Kafka persistence) |
| **IV. Causal Ordering** | T031 (conversation_id partitioning), T043 (partition key) |
| **V. Real-Time Performance** | T032 (reactive WebFlux), T061 (MongoDB indexes), T141 (rate limiting) |
| **VI. Full-Stack Observability** | T111-T118 (logs, metrics, tracing), T116-T117 (dashboards, alerts) |
| **VII. Pluggable Architecture** | T016-T017 (MessageConnector interface), T093-T110 (independent connectors) |
| **Data Storage (v1.1.0)** | T025-T026 (MongoDB for messages), T021-T024 (PostgreSQL for metadata) |

**Result**: ✅ All constitutional requirements satisfied by task breakdown.
