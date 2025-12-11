# Chat4All v2 - Quick Reference Guide

**Purpose**: Quick reference for common authentication and messaging operations  
**Last Updated**: 2025-12-10

---

## Authentication Flow (30 seconds)

### 1. Get Access Token

```bash
curl -s -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=chat4all-client" \
  -d "username=testuser" \
  -d "password=testuser123" | jq -r '.access_token'
```

**Save token**: 
```bash
TOKEN=$(curl -s -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=chat4all-client&username=testuser&password=testuser123" | jq -r '.access_token')
```

### 2. Verify Token

```bash
echo $TOKEN | cut -d'.' -f2 | base64 -d | jq .
```

---

## User Management

### Get Current User Profile

```bash
curl http://localhost:8083/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"
```

### Create New User (Keycloak Admin)

```bash
# Get admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8888/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=admin-cli&username=admin&password=admin" | jq -r '.access_token')

# Create user
curl -X POST http://localhost:8888/admin/realms/chat4all/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "email": "newuser@chat4all.local",
    "firstName": "New",
    "lastName": "User",
    "enabled": true,
    "credentials": [{"type": "password", "value": "password123", "temporary": false}]
  }'
```

---

## Messaging

### Send Message

```bash
curl -X POST http://localhost:8081/api/v1/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-001",
    "recipientId": "user-id-here",
    "content": "Hello!",
    "type": "TEXT",
    "channel": "WHATSAPP"
  }'
```

### Get Conversation

```bash
curl http://localhost:8081/api/v1/conversations/conv-001/messages \
  -H "Authorization: Bearer $TOKEN"
```

### Get Conversation with Pagination

```bash
# Limit to 10 messages
curl "http://localhost:8081/api/v1/conversations/conv-001/messages?limit=10" \
  -H "Authorization: Bearer $TOKEN"

# Get older messages before a timestamp
curl "http://localhost:8081/api/v1/conversations/conv-001/messages?before=2025-12-10T10:00:00Z&limit=50" \
  -H "Authorization: Bearer $TOKEN"
```

### Mark Conversation as Read

```bash
curl -X PUT http://localhost:8081/api/v1/conversations/conv-001/read \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"readAt": "'$(date -u +'%Y-%m-%dT%H:%M:%SZ')'"}'
```

### Check Message Status

```bash
curl http://localhost:8081/api/v1/messages/msg-id-here/status \
  -H "Authorization: Bearer $TOKEN"
```

---

## File Upload

### Upload File

```bash
curl -X POST http://localhost:8084/api/v1/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/image.jpg" \
  -F "channel=WHATSAPP"
```

### Send Message with File

```bash
FILE_ID="file-id-from-upload"

curl -X POST http://localhost:8081/api/v1/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-001",
    "recipientId": "user-id",
    "content": "Check this image!",
    "type": "IMAGE",
    "channel": "WHATSAPP",
    "attachments": [{
      "fileId": "'$FILE_ID'",
      "fileName": "image.jpg",
      "mimeType": "image/jpeg"
    }]
  }'
```

---

## WebSocket Real-Time

### Connect to WebSocket

```javascript
const token = "your-token-here";
const ws = new WebSocket(`ws://localhost:8080/api/v1/ws?token=${token}`);

ws.onopen = () => console.log("Connected");
ws.onmessage = (e) => console.log("Message:", JSON.parse(e.data));
ws.onerror = (e) => console.error("Error:", e);
ws.onclose = () => console.log("Disconnected");

// Subscribe to conversation
ws.send(JSON.stringify({
  action: "subscribe",
  conversationId: "conv-001"
}));

// Send message
ws.send(JSON.stringify({
  action: "send_message",
  conversationId: "conv-001",
  recipientId: "user-id",
  content: "Hello!",
  type: "TEXT",
  channel: "WHATSAPP"
}));

// Mark as read
ws.send(JSON.stringify({
  action: "mark_read",
  conversationId: "conv-001"
}));
```

---

## Testing Complete Flow

### Run Complete Flow Script

```bash
# Make executable
chmod +x test-complete-flow.sh

# Run test (requires users alice and bob to exist)
./test-complete-flow.sh
```

### Manual Test Flow

```bash
#!/bin/bash

# 1. Authenticate
TOKEN=$(curl -s -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=chat4all-client&username=alice&password=alice123" | jq -r '.access_token')

# 2. Get user ID
USER_ID=$(curl -s http://localhost:8083/api/v1/users/me -H "Authorization: Bearer $TOKEN" | jq -r '.userId')

# 3. Send message
MSG=$(curl -s -X POST http://localhost:8081/api/v1/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"test","recipientId":"bob-id","content":"Test","type":"TEXT","channel":"WHATSAPP"}')

MSG_ID=$(echo $MSG | jq -r '.messageId')
echo "Message: $MSG_ID"

# 4. Check status
curl -s http://localhost:8081/api/v1/messages/$MSG_ID/status -H "Authorization: Bearer $TOKEN" | jq .

# 5. Fetch conversation
curl -s http://localhost:8081/api/v1/conversations/test/messages -H "Authorization: Bearer $TOKEN" | jq '.messages'
```

---

## Common Issues & Solutions

### 401 Unauthorized

**Problem**: Token is invalid or expired

**Solution**: Get a fresh token
```bash
TOKEN=$(curl -s -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=chat4all-client&username=testuser&password=testuser123" | jq -r '.access_token')
```

### 403 Forbidden

**Problem**: User lacks required role/scope

**Solution**: Check token claims
```bash
echo $TOKEN | cut -d'.' -f2 | base64 -d | jq .realm_access
```

### Message Not Found

**Problem**: Conversation or message doesn't exist

**Solution**: 
- Check conversation ID is correct
- Verify recipient user exists
- Ensure both users have authenticated

### Service Not Running

**Problem**: `Connection refused`

**Solution**: Start services with Docker Compose
```bash
docker-compose up -d
```

---

## Default Credentials

| Service | URL | Username | Password |
|---------|-----|----------|----------|
| Keycloak | http://localhost:8888/admin | admin | admin |
| Grafana | http://localhost:3000 | admin | admin |
| MinIO | http://localhost:9000 | minioadmin | minioadmin |
| MongoDB | localhost:27017 | chat4all | chat4all_dev_password |
| PostgreSQL | localhost:5433 | chat4all | chat4all_dev_password |

### Test Users

| Username | Password | Email |
|----------|----------|-------|
| testuser | testuser123 | testuser@chat4all.local |
| alice | alice123 | alice@chat4all.local |
| bob | bob123 | bob@chat4all.local |
| admin-user | admin123 | admin@chat4all.local |

---

## Service Ports

| Service | Port | Type |
|---------|------|------|
| API Gateway | 8080 | REST + WebSocket |
| Message Service | 8081 | REST |
| Router Service | 8082 | Internal |
| User Service | 8083 | REST |
| File Service | 8084 | REST |
| WhatsApp Connector | 8085 | REST |
| Telegram Connector | 8086 | REST |
| Instagram Connector | 8087 | REST |
| Keycloak | 8888 | OAuth2/OIDC |
| Prometheus | 9090 | Metrics |
| Grafana | 3000 | Dashboard |
| Jaeger | 16686 | Tracing |
| Kafka | 9092 | Message Broker |
| MongoDB | 27017 | Database |
| PostgreSQL | 5433 | Database |
| Redis | 6379 | Cache |

---

## Useful Links

- **Keycloak Admin**: http://localhost:8888/admin
- **Grafana Dashboards**: http://localhost:3000
- **Prometheus Metrics**: http://localhost:9090
- **Jaeger Traces**: http://localhost:16686
- **API Docs**: http://localhost:8081/swagger-ui.html

---

## API Response Format

### Success Response

```json
{
  "messageId": "msg-001-abc123",
  "conversationId": "conv-001",
  "senderId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "content": "Hello World",
  "status": "DELIVERED",
  "createdAt": "2025-12-10T10:30:00Z",
  "sentAt": "2025-12-10T10:30:02Z",
  "deliveredAt": "2025-12-10T10:30:15Z"
}
```

### Error Response

```json
{
  "error": "UNAUTHORIZED",
  "message": "Invalid or expired token",
  "timestamp": "2025-12-10T10:30:00Z",
  "path": "/api/v1/messages"
}
```

---

## Token Introspection

### Validate Token

```bash
TOKEN="your-token-here"

curl -s -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token/introspect \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=$TOKEN&client_id=chat4all-client" | jq .
```

### Refresh Token

```bash
REFRESH_TOKEN="your-refresh-token"

curl -s -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token&client_id=chat4all-client&refresh_token=$REFRESH_TOKEN" | jq '.access_token'
```

---

## Performance Tips

### Use Pagination for Large Conversations

```bash
# Get first 50 messages
curl "http://localhost:8081/api/v1/conversations/conv-001/messages?limit=50" \
  -H "Authorization: Bearer $TOKEN"

# Get next batch using cursor
curl "http://localhost:8081/api/v1/conversations/conv-001/messages?limit=50&before=2025-12-10T10:00:00Z" \
  -H "Authorization: Bearer $TOKEN"
```

### Use WebSocket for Real-Time Updates

Avoid polling with HTTP. WebSocket reduces latency from 500ms+ to <100ms.

### Cache User IDs

Store user IDs locally to avoid repeated profile lookups.

### Batch Message Sends

For bulk messaging, use the `/api/v1/messages/bulk` endpoint instead of sending individual messages.

---

## Security Best Practices

1. **Never commit tokens**: Use environment variables
2. **Use HTTPS in production**: HTTP only for local development
3. **Validate signatures**: Verify JWT signatures in backend
4. **Refresh tokens**: Implement token refresh logic
5. **Rate limiting**: Implement client-side rate limiting
6. **CORS**: Configure CORS for web clients
7. **Input validation**: Validate all user inputs

---

## Next Steps

1. Read [AUTHENTICATION_AND_MESSAGING_FLOW.md](./AUTHENTICATION_AND_MESSAGING_FLOW.md) for complete details
2. Check [CLIENT_INTEGRATION_EXAMPLES.md](./CLIENT_INTEGRATION_EXAMPLES.md) for your framework
3. Review [KEYCLOAK_SETUP.md](./infrastructure/keycloak/KEYCLOAK_SETUP.md) for OAuth2 flows
4. Check [SPRING_SECURITY_EXAMPLES.md](./infrastructure/keycloak/SPRING_SECURITY_EXAMPLES.md) for backend integration
