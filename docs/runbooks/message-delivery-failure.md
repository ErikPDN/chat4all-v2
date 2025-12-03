# Runbook: Message Delivery Failure Troubleshooting

**Service**: Chat4All v2 - Unified Messaging Platform  
**Last Updated**: 2025-12-03  
**Severity**: P1 (Critical - impacts message delivery SLA)

## Overview

This runbook guides operators through diagnosing and resolving message delivery failures in the Chat4All platform. Message delivery failures can occur at multiple stages: API ingestion, Kafka processing, connector delivery, or external platform issues.

---

## Prerequisites

- Docker and Docker Compose installed
- Access to docker logs and docker exec commands
- Access to Jaeger distributed tracing (http://localhost:16686)
- Access to Grafana dashboards (http://localhost:3000)
- Access to MongoDB message store (port 27017)
- Access to Kafka cluster (port 9092)
- Knowledge of the specific external platform (WhatsApp/Telegram/Instagram)

---

## Quick Reference: Message Status Flow

```
PENDING → SENT → DELIVERED → READ (success path)
   ↓        ↓         ↓
FAILED   FAILED    FAILED    (failure paths)
```

**Status Definitions**:
- **PENDING**: Message accepted by API, awaiting routing
- **SENT**: Message sent to external platform API
- **DELIVERED**: Platform confirmed delivery to recipient device
- **READ**: Recipient read the message (if platform supports read receipts)
- **FAILED**: Delivery failed after retry attempts exhausted

---

## Symptom 1: Message Stuck in PENDING Status

### Indicators
- Message status remains PENDING for >30 seconds
- No status updates in message history
- Customers report messages not being delivered

### Root Causes
1. **Router Service Not Processing**: Kafka consumer lag or service down
2. **Kafka Topic Issues**: Topic not created, partitions unavailable
3. **Database Connection Issues**: MongoDB write/read failures

### Diagnostic Steps

#### 1.1 Check Message Status in MongoDB

```bash
# Connect to MongoDB
docker exec -it chat4all-mongodb mongosh chat4all

# Query message by ID
db.messages.findOne({"message_id": "YOUR_MESSAGE_ID"})

# Check for PENDING messages older than 1 minute
db.messages.find({
  "status": "PENDING",
  "timestamp": { $lt: new Date(Date.now() - 60000) }
}).limit(10)
```

**Expected Output**: Message document with current status and timestamps.

**Problem Indicators**:
- Message not found → API layer issue
- Status stuck at PENDING with old timestamp → Router service issue
- Multiple PENDING messages → System-wide issue

#### 1.2 Check Kafka Topic and Consumer Lag

```bash
# List Kafka topics
docker exec -it chat4all-kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 --list | grep message

# Check consumer groups and lag
docker exec -it chat4all-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group router-service-group \
  --describe

# Check message-events topic
docker exec -it chat4all-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic message-events \
  --from-beginning --max-messages 10
```

**Expected Output**: 
- Topics exist: `message-events`, `whatsapp-outbound`, `telegram-outbound`, `instagram-outbound`
- Consumer lag: <100 messages (normal), >1000 messages (problem)
- Messages flowing through topics

**Problem Indicators**:
- Topics missing → Infrastructure issue (run `infrastructure/kafka/topics.json` setup)
- High consumer lag → Router service overloaded or crashed
- No messages in topic → Message-service not publishing

#### 1.3 Check Router Service Logs

```bash
# Check router service status
docker ps --filter name=router-service

# View recent logs
docker logs chat4all-router-service --tail=100 --since=5m

# Search for specific message ID
docker logs chat4all-router-service --tail=1000 | grep "YOUR_MESSAGE_ID"

# Check for errors
docker logs chat4all-router-service --tail=500 2>&1 | grep -E "ERROR|WARN"
```

**Problem Indicators**:
```log
ERROR - Failed to consume from Kafka: Connection refused
ERROR - MongoDB write failed: timeout
WARN  - Message routing failed for conversation: invalid channel
```

#### 1.4 Check Message Service API Health

```bash
# Health check
docker exec -it chat4all-api-gateway \
  curl http://message-service:8082/actuator/health

# Metrics - check Kafka producer metrics
docker exec -it chat4all-api-gateway \
  curl http://message-service:8082/actuator/metrics/kafka.producer.record-send-total

# Or access directly from host
curl http://localhost:8080/api/messages/actuator/health
```
#### R1.1: Router Service Down/Crashed

```bash
# Restart router service
docker-compose restart router-service

# Wait for healthy status
docker ps --filter name=router-service --format "{{.Status}}"

# Check logs for startup
docker logs chat4all-router-service --tail=50 -f

# Verify consumer group rejoined
docker exec -it chat4all-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group router-service-group \
  --describe
```-bootstrap-server localhost:9092 \
#### R1.2: Kafka Topics Missing

```bash
# Create missing topics from configuration
docker exec -it chat4all-kafka bash -c '
  kafka-topics.sh --bootstrap-server localhost:9092 --create \
    --topic message-events --partitions 10 --replication-factor 1
  kafka-topics.sh --bootstrap-server localhost:9092 --create \
    --topic whatsapp-outbound --partitions 5 --replication-factor 1
  kafka-topics.sh --bootstrap-server localhost:9092 --create \
    --topic telegram-outbound --partitions 5 --replication-factor 1
  kafka-topics.sh --bootstrap-server localhost:9092 --create \
    --topic instagram-outbound --partitions 5 --replication-factor 1
'

# Or use the infrastructure setup script
docker exec -it chat4all-kafka sh -c "cat /kafka/setup/topics.json"

# Restart router service to reconnect
#### R1.3: MongoDB Connection Issues

```bash
# Check MongoDB container status
docker ps --filter name=mongodb

# Check connection from router service
docker exec -it chat4all-router-service \
  nc -zv mongodb 27017

# Check MongoDB logs
docker logs chat4all-mongodb --tail=100

# Restart MongoDB if needed (CAUTION: May cause data loss if not healthy)
docker-compose restart mongodb

# Verify MongoDB health
docker exec -it chat4all-mongodb mongosh --eval "db.adminCommand('ping')"
```heck connection from router service
kubectl exec -it deployment/router-service -- \
  nc -zv chat4all-mongodb 27017

# Restart MongoDB if needed (CAUTION: Check replica set status first)
kubectl rollout restart statefulset/chat4all-mongodb

# Verify MongoDB health
kubectl exec -it chat4all-mongodb-0 -- mongosh --eval "db.adminCommand('ping')"
```

---

## Symptom 2: Message Stuck in SENT Status

### Indicators
- Message transitions from PENDING → SENT but never reaches DELIVERED
- No webhook callback received from external platform
#### 2.1 Check Connector Service Logs

```bash
# Identify which connector based on channel
# For WhatsApp messages:
docker logs chat4all-whatsapp-connector --tail=200 --since=10m

# Search for specific message
docker logs chat4all-whatsapp-connector --tail=1000 2>&1 | grep "YOUR_MESSAGE_ID"

# Check for API errors
docker logs chat4all-whatsapp-connector --tail=500 2>&1 | \
  grep -E "ERROR|timeout|rate.limit|401|403|429|500|502|503"
```or WhatsApp messages:
kubectl logs -l app=whatsapp-connector --tail=200 --since=10m

# Search for specific message
kubectl logs -l app=whatsapp-connector --tail=1000 | grep "YOUR_MESSAGE_ID"

# Check for API errors
kubectl logs -l app=whatsapp-connector --tail=500 | \
  grep -E "ERROR|timeout|rate.limit|401|403|429|500|502|503"
```
#### 2.2 Check External Platform Status

```bash
# WhatsApp Business API Status
curl -I https://graph.facebook.com/v18.0/health

# Check platform-specific metrics
docker exec -it chat4all-api-gateway \
  curl http://whatsapp-connector:8091/actuator/metrics/whatsapp.api.requests.failed

# Or access directly from host
curl http://localhost:8091/actuator/metrics
```# 2.2 Check External Platform Status

```bash
# WhatsApp Business API Status
curl -I https://graph.facebook.com/v18.0/health

# Check platform-specific metrics
#### 2.3 Check Webhook Configuration

```bash
# Verify webhook endpoint is accessible from connector
docker exec -it chat4all-whatsapp-connector \
  curl -I http://api-gateway:8080/api/connectors/whatsapp/webhook

# Or test from host (if exposed)
curl -I http://localhost:8080/api/connectors/whatsapp/webhook

# Check recent webhook deliveries in logs
docker logs chat4all-whatsapp-connector --tail=200 2>&1 | \
  grep "webhook.*delivery"

# Check Jaeger for webhook trace
# Navigate to Jaeger UI: http://localhost:16686
# Search for service: whatsapp-connector
# Filter by tag: webhook=true
```url -I https://YOUR_DOMAIN/api/connectors/whatsapp/webhook

# Check recent webhook deliveries in logs
#### 2.4 Check Message Retry Attempts

```bash
# Query MongoDB for retry count
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.messages.findOne(
  {"message_id": "YOUR_MESSAGE_ID"},
  {"retry_count": 1, "status": 1, "error_message": 1}
)'

# Check status history
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.message_status_history.find(
  {"message_id": "YOUR_MESSAGE_ID"}
).sort({"timestamp": -1})'
```bash
# Query MongoDB for retry count
kubectl exec -it deployment/chat4all-mongodb -- mongosh chat4all --eval '
db.messages.findOne(
  {"message_id": "YOUR_MESSAGE_ID"},
  {"retry_count": 1, "status": 1, "error_message": 1}
)'

# Check status history
kubectl exec -it deployment/chat4all-mongodb -- mongosh chat4all --eval '
db.message_status_history.find(
  {"message_id": "YOUR_MESSAGE_ID"}
).sort({"timestamp": -1})'
#### R2.1: Connector Rate Limited by Platform

```bash
# Check current rate limit metrics
docker exec -it chat4all-api-gateway \
  curl http://whatsapp-connector:8091/actuator/metrics/rate.limit.remaining

# Temporarily reduce throughput (scale down in docker-compose.yml if using replicas)
# Edit docker-compose.yml and reduce deploy.replicas, then:
docker-compose up -d --scale whatsapp-connector=1

# Monitor platform rate limit headers in logs
docker logs chat4all-whatsapp-connector --tail=100 -f 2>&1 | \
  grep "X-RateLimit"
```heck current rate limit metrics
kubectl exec -it deployment/chat4all-api-gateway -- \
  curl http://whatsapp-connector:8091/actuator/metrics/rate.limit.remaining
#### R2.2: Connector Service Authentication Failed

```bash
# Check connector environment variables
docker exec -it chat4all-whatsapp-connector env | grep WHATSAPP

# Rotate credentials (update .env file or docker-compose.yml)
# Edit .env file:
# WHATSAPP_ACCESS_TOKEN=new_token_here
# WHATSAPP_WEBHOOK_SECRET=new_secret_here

# Restart connector to pick up new credentials
docker-compose restart whatsapp-connector

# Verify new credentials are loaded
docker logs chat4all-whatsapp-connector --tail=20 2>&1 | grep "Started"
```erify access token is not expired
kubectl exec -it deployment/whatsapp-connector -- env | grep WHATSAPP_ACCESS_TOKEN

# Rotate credentials (obtain new token from platform)
kubectl create secret generic whatsapp-connector-secret \
  --from-literal=access-token=NEW_TOKEN \
#### R2.3: Webhook Endpoint Unreachable

```bash
# Check Docker network connectivity
docker network inspect chat4all-v2_default

# Test webhook endpoint from within Docker network
docker exec -it chat4all-whatsapp-connector \
  curl -X POST http://api-gateway:8080/api/connectors/whatsapp/webhook \
  -H "Content-Type: application/json" \
  -d '{"test": "connectivity"}'

# Test webhook endpoint externally (if exposed)
curl -X POST http://localhost:8080/api/connectors/whatsapp/webhook \
  -H "Content-Type: application/json" \
  -d '{"test": "connectivity"}'

# Check if firewall/reverse proxy blocking platform IP ranges
#### R2.4: Manual Message Retry (Emergency)

```bash
# Update message status back to PENDING to trigger retry
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.messages.updateOne(
  {"message_id": "YOUR_MESSAGE_ID"},
  {
    $set: {
      "status": "PENDING",
      "retry_count": 0,
      "updated_at": new Date()
    }
  }
)'

# Manually publish message event to Kafka
docker exec -it chat4all-kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic message-events <<EOF
{
  "message_id": "YOUR_MESSAGE_ID",
  "conversation_id": "CONVERSATION_ID",
  "event_type": "MESSAGE_CREATED"
}
EOF
```anually publish message event to Kafka
kubectl exec -it deployment/chat4all-kafka -- kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic message-events <<EOF
{
  "message_id": "YOUR_MESSAGE_ID",
  "conversation_id": "CONVERSATION_ID",
  "event_type": "MESSAGE_CREATED"
}
EOF
```

**CAUTION**: Only use manual retry for critical messages. Check idempotency to avoid duplicates.

---

## Symptom 3: Message Status FAILED

### Indicators
- Message status = FAILED in database
- `error_message` field contains failure reason
#### 3.1 Retrieve Error Details

```bash
# Get message with error details
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.messages.findOne(
  {"message_id": "YOUR_MESSAGE_ID"},
  {
    "status": 1,
    "error_message": 1,
    "retry_count": 1,
    "channel": 1,
    "conversation_id": 1
  }
)'

# Get full status history
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.message_status_history.find(
  {"message_id": "YOUR_MESSAGE_ID"}
).sort({"timestamp": -1}).pretty()'
``` "channel": 1,
    "conversation_id": 1
  }
)'

# Get full status history
kubectl exec -it deployment/chat4all-mongodb -- mongosh chat4all --eval '
db.message_status_history.find(
  {"message_id": "YOUR_MESSAGE_ID"}
#### 3.2 Check Connector Dead Letter Queue

```bash
# Check DLQ topic for failed messages
docker exec -it chat4all-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic whatsapp-outbound-dlq \
  --from-beginning --max-messages 20

# Count messages in DLQ
docker exec -it chat4all-kafka kafka-run-class.sh \
  kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic whatsapp-outbound-dlq
```ectl exec -it deployment/chat4all-kafka -- kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic whatsapp-outbound-dlq \
  --from-beginning --max-messages 20

#### 3.3 Analyze Error Patterns

```bash
# Group FAILED messages by error_message
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.messages.aggregate([
  {
    $match: {
      "status": "FAILED",
      "timestamp": { $gte: new Date(Date.now() - 3600000) }
    }
  },
  {
    $group: {
      _id: "$error_message",
      count: { $sum: 1 }
    }
  },
  { $sort: { count: -1 } }
])'
``` $group: {
      _id: "$error_message",
      count: { $sum: 1 }
    }
  },
  { $sort: { count: -1 } }
])'
```

#### R3.1: Recipient Not Found (Invalid Contact)

```bash
# Verify external identity exists in PostgreSQL
docker exec -it chat4all-postgres psql -U chat4all -d chat4all -c "
SELECT ei.platform, ei.platform_user_id, ei.verified, u.display_name
FROM external_identities ei
JOIN users u ON ei.user_id = u.user_id
WHERE ei.platform_user_id = '+5511999999999'
  AND ei.platform = 'WHATSAPP';"

# Check conversation participants
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.conversations.findOne(
  {"conversation_id": "CONVERSATION_ID"},
  {"participants": 1, "channel": 1}
)'
```
# Check conversation participants
kubectl exec -it deployment/chat4all-mongodb -- mongosh chat4all --eval '
db.conversations.findOne(
  {"conversation_id": "CONVERSATION_ID"},
  {"participants": 1, "channel": 1}
)'
```

**Resolution**:
1. Verify phone number format (E.164 for WhatsApp: `+5511999999999`)
2. Confirm user is registered on the external platform
#### R3.2: Content Validation Failed

```bash
# Retrieve original message content
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.messages.findOne(
  {"message_id": "YOUR_MESSAGE_ID"},
  {"content": 1, "content_type": 1, "file_id": 1}
)'

# Check file metadata if file attachment
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.files.findOne(
  {"file_id": "FILE_ID"},
  {"filename": 1, "size_bytes": 1, "mime_type": 1}
)'
```"file_id": "FILE_ID"},
  {"filename": 1, "size_bytes": 1, "mime_type": 1}
)'
```

**Platform Limits**:
- **WhatsApp**: Max 4096 chars text, max 100MB files
- **Telegram**: Max 4096 chars text, max 2GB files
- **Instagram**: Max 1000 chars text, max 8MB images

**Resolution**:
1. Truncate text messages exceeding platform limits
2. Compress or reject files exceeding size limits
#### R3.3: Platform Temporary Outage (Bulk Retry)

```bash
# Find all FAILED messages from the outage period
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.messages.find({
  "status": "FAILED",
  "timestamp": {
    $gte: ISODate("2025-12-03T14:00:00Z"),
    $lte: ISODate("2025-12-03T15:00:00Z")
  },
  "channel": "WHATSAPP"
}).forEach(function(msg) {
  db.messages.updateOne(
    {"_id": msg._id},
    {
      $set: {
        "status": "PENDING",
        "retry_count": 0,
        "error_message": null,
        "updated_at": new Date()
      }
    }
  );
  print("Reset message: " + msg.message_id);
})'

# Republish events to Kafka for retry
# (This will be handled automatically by router service)
```
# Republish events to Kafka for retry
# (This will be handled automatically by router service)
```
#### R3.4: Authentication/Credentials Invalid (Permanent)

```bash
# This requires new credentials from platform provider
# Follow R2.2 steps to rotate credentials

# After rotation, retry all FAILED messages
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.messages.updateMany(
  {
    "status": "FAILED",
    "error_message": {$regex: /authentication|401|403/i}
  },
  {
    $set: {
      "status": "PENDING",
      "retry_count": 0,
      "error_message": null,
      "updated_at": new Date()
    }
  }
)'
``` }
  }
)'
```

---

## Symptom 4: Messages Delivered Out of Order

### Indicators
- Messages appear in wrong sequence in conversation history
- User reports receiving reply before original message
- `timestamp` field shows correct order but delivery order is wrong

### Root Causes
1. **Kafka Partition Rebalancing**: Consumer reassignment during scaling
2. **Concurrent Connector Processing**: Multiple replicas processing same conversation
3. **Network Delays**: Variable latency to external platforms

#### 4.1 Check Message Sequence in MongoDB

```bash
# Retrieve conversation messages in order
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.messages.find(
  {"conversation_id": "CONVERSATION_ID"}
).sort({"timestamp": 1}).limit(20).forEach(function(msg) {
  print(msg.timestamp + " | " + msg.message_id + " | " + msg.status + " | " + msg.content.substring(0, 50));
})'
```rint(msg.timestamp + " | " + msg.message_id + " | " + msg.status + " | " + msg.content.substring(0, 50));
})'
```

#### 4.2 Check Kafka Partition Assignment

```bash
# Check which partition conversation is assigned to
CONVERSATION_ID="your-conversation-id"
PARTITION=$((16#$(echo -n "$CONVERSATION_ID" | md5sum | head -c 8) % 10))
echo "Conversation $CONVERSATION_ID → Partition $PARTITION"

# Check consumer group partition assignment
docker exec -it chat4all-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group router-service-group \
  --describe
```-group router-service-group \
  --describe
```

**Problem Indicators**:
#### 4.3 Check Connector Concurrency

```bash
# Check number of connector instances running
docker ps --filter name=whatsapp-connector --format "{{.Names}}"

# Check if conversation is being processed by multiple containers
docker-compose logs whatsapp-connector --tail=500 2>&1 | \
  grep "CONVERSATION_ID" | \
  awk '{print $1}' | sort | uniq -c
```ectl logs -l app=whatsapp-connector --tail=500 | \
  grep "CONVERSATION_ID" | \
  awk '{print $1}' | sort | uniq -c
```

**Problem**: If multiple pods processing same conversation simultaneously.
#### R4.1: Ensure Partition-Based Ordering

```bash
# Verify router service partitioning logic
docker exec -it chat4all-router-service env | grep PARTITION

# If partitioning disabled, enable it via environment variable
# Edit docker-compose.yml or .env file:
# KAFKA_PARTITION_ENABLED=true

# Restart to apply changes
docker-compose restart router-service
```
# Restart to apply changes
kubectl rollout restart deployment/router-service
```

#### R4.2: Prevent Concurrent Processing (Connector-Side Locking)

```bash
# Check Redis-based conversation locking
docker exec -it chat4all-redis redis-cli KEYS "conversation:lock:*"

# If locks are stuck (TTL expired), clear them
docker exec -it chat4all-redis redis-cli DEL "conversation:lock:CONVERSATION_ID"
```

**Configuration**: Ensure connector has conversation-level locking enabled in docker-compose.yml or .env:

```yaml
# whatsapp-connector environment variables
environment:
  - CONVERSATION_LOCK_ENABLED=true
  - CONVERSATION_LOCK_TTL_SECONDS=30
```

---

## Monitoring & Alerting

### Key Metrics to Monitor
### Key Metrics to Monitor

```bash
# Message status distribution (should be mostly DELIVERED/READ)
docker exec -it chat4all-mongodb mongosh chat4all --eval '
db.messages.aggregate([
  {
    $match: {
      "timestamp": { $gte: new Date(Date.now() - 3600000) }
    }
  },
  {
    $group: {
      _id: "$status",
      count: { $sum: 1 }
    }
  }
])'

# Average delivery time (PENDING → DELIVERED)
curl http://localhost:8080/api/messages/actuator/metrics/message.delivery.time

# Failed message rate
curl http://localhost:8080/api/messages/actuator/metrics/message.failed.total

# Or check Grafana dashboard: http://localhost:3000
# Navigate to "Chat4All - Message Delivery Metrics"
```
### Recommended Alerts

1. **High PENDING Message Count**: >100 messages stuck in PENDING for >2 minutes
2. **High FAILED Rate**: >5% of messages FAILED in last 5 minutes
3. **Kafka Consumer Lag**: Lag >1000 messages
4. **Connector API Errors**: >10 5xx errors in 1 minute
5. **Delivery Time P95**: >5 seconds (exceeds SLA)

---

## Escalation Paths

### L1 → L2 Escalation
**Criteria**:
- Issue not resolved within 15 minutes
- Multiple services affected (system-wide)
- External platform outage confirmed

**Actions**:
1. Gather all diagnostic outputs from this runbook
2. Export Jaeger traces for affected messages
3. Create incident in ticketing system with priority P1
4. Notify on-call engineering team via PagerDuty/Slack

### L2 → L3 Escalation
**Criteria**:
- Code-level investigation required
- Database corruption suspected
- Kafka cluster issues

**Actions**:
1. Page principal engineer or architect
2. Prepare MongoDB/Kafka cluster snapshots
3. Enable DEBUG logging on affected services
4. Schedule post-incident review

---

## Post-Incident Actions

1. **Root Cause Analysis (RCA)**:
   - Document timeline of events
   - Identify root cause and contributing factors
   - Propose preventive measures

2. **Update Monitoring**:
   - Add alerts for newly discovered failure modes
   - Tune existing alert thresholds

3. **Code Fixes**:
   - Implement improved error handling
   - Add defensive checks for edge cases

4. **Runbook Updates**:
   - Document new diagnostic procedures
   - Add new resolution steps learned

5. **Customer Communication**:
   - Notify affected customers if SLA breached
   - Provide incident report summary

---

## Related Documentation

- [Architecture Overview](../../specs/001-unified-messaging-platform/plan.md)
- [Data Model](../../specs/001-unified-messaging-platform/data-model.md)
- [Messages API Contract](../../specs/001-unified-messaging-platform/contracts/messages-api.yaml)
- [Scaling Runbook](./scaling.md)
- [Kafka Operations Guide](../infrastructure/kafka-operations.md)

---

## Appendix: Common Error Codes

### WhatsApp Business API Error Codes
- `100`: Invalid parameter
- `131047`: Rate limit exceeded (re-engagement window)
- `131056`: Recipient cannot be sender
- `132000`: Message too long
- `133000`: Recipient's phone number not on WhatsApp
- `131053`: Re-engagement message not sent within 24h window

### Telegram Bot API Error Codes
- `400`: Bad Request (invalid parameters)
- `401`: Unauthorized (invalid bot token)
- `403`: Forbidden (user blocked bot)
- `404`: Not Found (user/chat not found)
- `429`: Too Many Requests (rate limited)

### Instagram Graph API Error Codes
- `10`: Permission denied
- `100`: Invalid parameter
- `190`: Access token expired/invalid
- `368`: Temporarily blocked for spam
- `613`: Rate limit exceeded

---

**Document Version**: 1.0  
**Authors**: Chat4All SRE Team  
**Review Cycle**: Monthly or after major incidents
