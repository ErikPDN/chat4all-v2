# Router-Service ↔ User-Service Integration

## Overview

The Router Service has been enhanced to integrate with the User Service for **identity resolution**. This enables the routing of messages to internal users who may have multiple external platform identities (WhatsApp, Telegram, Instagram).

### Architecture

```
┌─────────────────┐
│ Message Service │
└────────┬────────┘
         │ Kafka (chat-events)
         ▼
┌─────────────────────────────────────────────────┐
│          Router Service                         │
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │      RoutingHandler                      │  │
│  │                                          │  │
│  │  1. Detect UUID vs Platform ID          │  │
│  │  2. If UUID → UserServiceClient          │  │
│  │  3. Resolve external identities          │  │
│  │  4. Fan-out to all platforms            │  │
│  └──────────────┬───────────────────────────┘  │
│                 │                               │
│                 ├─ WebClient ─────────┐         │
│                 │                     │         │
└─────────────────┼─────────────────────┼─────────┘
                  │                     │
                  ▼                     ▼
        ┌──────────────────┐  ┌────────────────┐
        │  User Service    │  │  Connectors    │
        │  (Port 8083)     │  │  (WhatsApp,    │
        │                  │  │   Telegram,    │
        │  GET /users/{id} │  │   Instagram)   │
        └──────────────────┘  └────────────────┘
```

## Components Created

### 1. DTOs (`services/router-service/src/main/java/com/chat4all/router/dto/`)

#### `UserDTO.java`
Maps the User Service API response.

```java
{
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "displayName": "Erik Silva",
    "externalIdentities": [
        {
            "platform": "WHATSAPP",
            "platformUserId": "+5562999999999",
            "verified": false
        },
        {
            "platform": "TELEGRAM",
            "platformUserId": "@erik_dev",
            "verified": false
        }
    ]
}
```

#### `ExternalIdentityDTO.java`
Represents a single platform identity.

### 2. UserServiceClient (`services/router-service/src/main/java/com/chat4all/router/client/`)

Reactive HTTP client using Spring WebClient.

**Features:**
- Non-blocking reactive calls
- Automatic retry (2 attempts with exponential backoff)
- 10-second timeout
- Circuit breaker pattern
- Handles 404 Not Found gracefully (returns empty Mono)

**Configuration:**
```yaml
app:
  services:
    user-service:
      url: ${USER_SERVICE_URL:http://localhost:8083}
```

**Usage:**
```java
Mono<UserDTO> user = userServiceClient.getUser(userId);
```

### 3. Updated RoutingHandler

Enhanced with identity resolution logic.

**Decision Flow:**

```
Is recipientId a UUID?
│
├─ YES (Internal User)
│  │
│  ├─ Call User Service GET /users/{userId}
│  │
│  ├─ User found?
│  │  │
│  │  ├─ YES: Has external identities?
│  │  │  │
│  │  │  ├─ YES: Fan-out to all platforms
│  │  │  │      (WhatsApp + Telegram + Instagram)
│  │  │  │      Return success if ≥1 succeeds
│  │  │  │
│  │  │  └─ NO: Log warning, return failure
│  │  │
│  │  └─ NO: Log warning, return failure
│  │
│  └─ User Service unavailable?
│     └─ Handle circuit breaker / retry
│
└─ NO (Direct Platform ID)
   │
   └─ Deliver directly to specified channel
      (Backward compatibility)
```

## Message Delivery Modes

### Mode 1: Direct Platform ID (Backward Compatible)

**Recipient ID:** `+5562999999999` (phone number)

```json
{
  "recipientId": "+5562999999999",
  "content": "Hello",
  "channel": "WHATSAPP"
}
```

**Behavior:**
- Router detects non-UUID format
- Sends directly to WhatsApp connector
- No User Service call

### Mode 2: Internal User with Single Identity

**Recipient ID:** `550e8400-e29b-41d4-a716-446655440000` (UUID)

```json
{
  "recipientId": "550e8400-e29b-41d4-a716-446655440000",
  "content": "Hello",
  "channel": "WHATSAPP"
}
```

**Behavior:**
1. Router detects UUID format
2. Calls User Service: `GET /users/550e8400-e29b-41d4-a716-446655440000`
3. Resolves to WhatsApp: `+5562999999999`
4. Sends to WhatsApp connector

### Mode 3: Internal User with Multiple Identities (Fan-out)

**Recipient ID:** `550e8400-e29b-41d4-a716-446655440000` (UUID)

User has 3 linked identities:
- WhatsApp: `+5562999999999`
- Telegram: `@erik_dev`
- Instagram: `erik.silva`

```json
{
  "recipientId": "550e8400-e29b-41d4-a716-446655440000",
  "content": "Hello",
  "channel": "WHATSAPP"
}
```

**Behavior:**
1. Router detects UUID format
2. Calls User Service: `GET /users/550e8400-...`
3. Resolves to 3 identities
4. **Fans out to ALL platforms:**
   - ✓ WhatsApp connector (port 8091)
   - ✓ Telegram connector (port 8092)
   - ✓ Instagram connector (port 8093)
5. Returns success if ≥1 delivery succeeds

**Logs:**
```
INFO  - Resolving internal user to external identities: userId=550e8400...
INFO  - User has 3 linked identities - fanning out message
INFO  - Delivering to identity: platform=WHATSAPP, platformUserId=+5562999999999
INFO  - ✓ Delivered to WHATSAPP: +5562999999999
INFO  - Delivering to identity: platform=TELEGRAM, platformUserId=@erik_dev
INFO  - ✓ Delivered to TELEGRAM: @erik_dev
INFO  - Delivering to identity: platform=INSTAGRAM, platformUserId=erik.silva
INFO  - ✓ Delivered to INSTAGRAM: erik.silva
INFO  - Identity fan-out completed: userId=550e8400..., total=3, success=3, failed=0
```

## Error Handling

### Scenario 1: User Not Found

**HTTP Response:** User Service returns 404

**Router Behavior:**
- Logs warning: `User not found in User Service: userId=...`
- Returns `false` (delivery failed)
- Message status set to `FAILED`

### Scenario 2: User Has No Identities

**HTTP Response:** User Service returns 200 with `externalIdentities: []`

**Router Behavior:**
- Logs warning: `User has no linked external identities: userId=...`
- Returns `false` (delivery failed)
- Message status set to `FAILED`

### Scenario 3: User Service Unavailable

**Error:** Connection timeout / 5xx error

**Router Behavior:**
- Retries 2 times with exponential backoff (500ms → 1s)
- If still failing, circuit breaker opens
- Returns `false` (delivery failed)
- Message may be routed to DLQ (Dead Letter Queue)

### Scenario 4: Partial Identity Delivery Failure

**Scenario:** User has 3 identities, but Instagram connector is down

**Router Behavior:**
- Attempts delivery to all 3 platforms
- WhatsApp: ✓ Success
- Telegram: ✓ Success
- Instagram: ✗ Failure (connector unavailable)
- **Final status:** `DELIVERED` (≥1 success)
- Logs: `Partial delivery success: 2 succeeded, 1 failed`

## Configuration

### application.yml

```yaml
app:
  services:
    user-service:
      url: ${USER_SERVICE_URL:http://localhost:8083}
```

**Environment Variable Override:**
```bash
export USER_SERVICE_URL=http://user-service:8083
```

## Testing

### Run Integration Test

```bash
./test-router-identity-integration.sh
```

**Test creates:**
- Test user with UUID
- Links WhatsApp identity
- Links Telegram identity
- Provides manual test commands

### Manual Test: Multi-Platform Delivery

1. **Create user with identities** (use test script)

2. **Send message to internal user:**
   ```bash
   curl -X POST http://localhost:8082/api/v1/messages \
     -H 'Content-Type: application/json' \
     -d '{
       "recipientId": "550e8400-e29b-41d4-a716-446655440000",
       "content": "Test multi-platform",
       "channel": "WHATSAPP"
     }'
   ```

3. **Check router-service logs:**
   ```bash
   tail -f services/router-service/target/logs/router-service.log
   ```

4. **Expected log output:**
   ```
   INFO - Recipient ID is UUID - resolving to external identities
   INFO - Resolving internal user to external identities: userId=550e8400...
   INFO - User has 2 linked identities - fanning out message
   INFO - Delivering to identity: platform=WHATSAPP, platformUserId=+5562999999999
   INFO - ✓ Delivered to WHATSAPP: +5562999999999
   INFO - Delivering to identity: platform=TELEGRAM, platformUserId=@erik_dev
   INFO - ✓ Delivered to TELEGRAM: @erik_dev
   INFO - Identity fan-out completed: userId=..., total=2, success=2, failed=0
   ```

### Manual Test: Direct Platform ID (Backward Compatibility)

```bash
curl -X POST http://localhost:8082/api/v1/messages \
  -H 'Content-Type: application/json' \
  -d '{
    "recipientId": "+5562999999999",
    "content": "Direct WhatsApp",
    "channel": "WHATSAPP"
  }'
```

**Expected log:**
```
INFO - Recipient ID is direct platform ID - delivering directly
INFO - Direct delivery: platformUserId=+5562999999999
```

## Performance Considerations

### Latency Impact

**Without Identity Resolution (Direct):**
- Router → Connector: ~50ms
- **Total:** ~50ms

**With Identity Resolution (1 identity):**
- Router → User Service: ~30ms
- Router → Connector: ~50ms
- **Total:** ~80ms (+30ms overhead)

**With Identity Resolution (3 identities):**
- Router → User Service: ~30ms
- Router → WhatsApp Connector: ~50ms
- Router → Telegram Connector: ~50ms
- Router → Instagram Connector: ~50ms
- **Total:** ~180ms (fan-out in parallel)

### Caching Strategy (Future Enhancement)

To reduce User Service calls:

1. **Add Redis cache in UserServiceClient**
   ```java
   @Cacheable(value = "users", key = "#userId")
   public Mono<UserDTO> getUser(String userId) { ... }
   ```

2. **Cache TTL:** 5 minutes (identities change infrequently)

3. **Cache invalidation:** On identity link/unlink events

## Monitoring & Observability

### Metrics to Track

1. **Identity Resolution Rate**
   - UUID recipients vs Direct recipients
   - Metric: `router.identity.resolution.rate`

2. **User Service Call Latency**
   - P50, P95, P99 latency
   - Metric: `router.user_service.call.latency`

3. **Fan-out Distribution**
   - Average identities per user
   - Metric: `router.identity.fanout.count`

4. **Partial Delivery Rate**
   - Messages with partial identity failures
   - Metric: `router.identity.partial_delivery.rate`

### Alerts

1. **User Service Unavailable**
   - Trigger: Circuit breaker open for >2 minutes
   - Action: Check User Service health

2. **High Identity Resolution Failures**
   - Trigger: >10% users not found in 5 minutes
   - Action: Check data sync between services

## Future Enhancements

### 1. Preferred Channel Selection

Allow users to specify preferred delivery channel:

```json
{
  "externalIdentities": [
    {
      "platform": "WHATSAPP",
      "platformUserId": "+5562999999999",
      "preferred": true
    },
    {
      "platform": "TELEGRAM",
      "platformUserId": "@erik_dev",
      "preferred": false
    }
  ]
}
```

**Router Logic:**
- If `preferred` flag exists, deliver only to preferred channel
- Fallback to other identities if preferred fails

### 2. Channel-Specific Routing

Route based on message type:

```json
{
  "recipientId": "550e8400-...",
  "content": "Meeting at 3pm",
  "contentType": "text",
  "preferredChannel": "TELEGRAM"
}
```

**Router Logic:**
- Check if user has Telegram identity
- If yes, send to Telegram
- If no, fallback to any available identity

### 3. Identity Health Scores

Track delivery success rates per identity:

```json
{
  "platform": "WHATSAPP",
  "platformUserId": "+5562999999999",
  "healthScore": 0.95,
  "lastSuccessfulDelivery": "2025-11-29T12:00:00Z"
}
```

**Router Logic:**
- Prioritize identities with higher health scores
- Skip identities with score < 0.5

## Migration Guide

### Existing Systems

**No breaking changes** - backward compatible with direct platform IDs.

**Gradual Rollout:**

1. **Phase 1:** Deploy router-service with identity resolution (inactive)
2. **Phase 2:** Create users in User Service, link identities
3. **Phase 3:** Update message senders to use UUIDs instead of platform IDs
4. **Phase 4:** Monitor fan-out delivery metrics

### Database Migration

No schema changes required in Router Service.

User Service requires:
- `users` table
- `external_identities` table with FK to users

(Already implemented in User Service Flyway migrations)

## Troubleshooting

### Issue: "User not found" errors

**Cause:** User ID doesn't exist in User Service

**Solution:**
1. Verify user exists: `GET /api/v1/users/{userId}`
2. Check UUID format is correct
3. Verify User Service is running

### Issue: "User has no linked external identities"

**Cause:** User exists but no platforms linked

**Solution:**
1. Link identity: `POST /api/v1/users/{userId}/identities`
2. Verify identity was created: `GET /api/v1/users/{userId}`

### Issue: Messages not being delivered despite successful resolution

**Cause:** Connector services may be down

**Solution:**
1. Check connector health: `GET http://localhost:8091/actuator/health`
2. Verify connector URLs in router logs
3. Check connector logs for errors

## Summary

The Router-User Service integration enables:

✅ **Multi-platform message delivery** - Send to WhatsApp, Telegram, Instagram simultaneously  
✅ **Unified user profiles** - Single recipient ID for all platforms  
✅ **Backward compatibility** - Direct platform IDs still work  
✅ **Resilient delivery** - Partial failures don't block other platforms  
✅ **Observable** - Rich logging and metrics  
✅ **Testable** - Integration test script provided  

**Next Steps:**
- Run integration test: `./test-router-identity-integration.sh`
- Monitor router-service logs for identity resolution
- Track fan-out metrics in production
