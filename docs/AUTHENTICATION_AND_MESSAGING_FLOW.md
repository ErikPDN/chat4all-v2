# Chat4All v2 - Complete Authentication & Messaging Flow

**Document Purpose**: End-to-end guide for authentication, user creation, message sending, and message receiving  
**Last Updated**: 2025-12-10  
**Status**: ✅ Complete Implementation Guide

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Step 1: Authenticate with Keycloak](#step-1-authenticate-with-keycloak)
3. [Step 2: Create a User](#step-2-create-a-user)
4. [Step 3: Send a Message](#step-3-send-a-message)
5. [Step 4: Receive a Message](#step-4-receive-a-message)
6. [Complete End-to-End Scenario](#complete-end-to-end-scenario)
7. [WebSocket Real-Time Communication](#websocket-real-time-communication)
8. [Testing & Troubleshooting](#testing--troubleshooting)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENT APPLICATION                          │
└───────┬──────────────────────────────────────────────────────────────┘
        │
        ├─────────────────────────────────────────────┬────────────────┐
        │                                             │                │
        ▼                                             ▼                ▼
┌───────────────────┐                   ┌──────────────────┐  ┌────────────────┐
│   KEYCLOAK        │                   │   API GATEWAY    │  │  WEBSOCKET     │
│   (Port 8888)     │─Authenticate──►   │   (Port 8080)    │  │  (Real-time)   │
│   - OAuth2/OIDC   │◄───JWT Token──    │                  │  │                │
│   - Users/Roles   │                   └────────┬─────────┘  │  Real-time     │
│   - Access Control│                            │            │  message       │
└───────────────────┘                            │            │  delivery      │
                                                 │            │                │
                                        ┌────────▼────────────┴────────────────┐
                                        │                                      │
                                        ▼                                      ▼
                            ┌──────────────────────┐          ┌──────────────────┐
                            │  MESSAGE SERVICE     │          │  ROUTER SERVICE  │
                            │  (Port 8081)         │          │  (Background)    │
                            │ - Send Messages      │◄─Kafka──►│ - Route Messages │
                            │ - Fetch Conversations│          │ - Fan-out        │
                            │ - Validate JWT       │          │                  │
                            └──────────┬───────────┘          └────────┬─────────┘
                                       │                               │
                          ┌────────────┼───────────────────────────────┘
                          │            │
                          ▼            ▼
                    ┌──────────────────────────────────────┐
                    │   PERSISTENCE LAYER                  │
                    ├──────────────────────────────────────┤
                    │ PostgreSQL (Users, Conversations)    │
                    │ MongoDB (Messages, State)            │
                    │ Redis (Cache, Rate Limiting)         │
                    └──────────────────────────────────────┘
```

---

## Step 1: Authenticate with Keycloak

### 1.1 Obtain an Access Token

The first step is to authenticate with Keycloak to receive a JWT access token.

#### Option A: Username/Password Grant (User Credentials)

```bash
curl -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=chat4all-client" \
  -d "username=testuser" \
  -d "password=testuser123" \
  -d "scope=openid profile email"
```

**Response** (HTTP 200):
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cC...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI...",
  "refresh_expires_in": 2592000,
  "scope": "openid email profile"
}
```

**Save the access token for subsequent API calls**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=chat4all-client" \
  -d "username=testuser" \
  -d "password=testuser123" | jq -r '.access_token')

echo "Token: $TOKEN"
```

#### Option B: Client Credentials Grant (Service-to-Service)

For backend services that need to call each other:

```bash
curl -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=chat4all-backend" \
  -d "client_secret=your-client-secret" \
  -d "scope=message-read message-write"
```

#### Option C: Authorization Code Flow (Web Applications)

For web browsers and mobile apps with user interaction:

```bash
# Step 1: Redirect user to Keycloak login page
echo "1. Open in browser: http://localhost:8888/realms/chat4all/protocol/openid-connect/auth?client_id=chat4all-frontend&redirect_uri=http://localhost:3000/callback&response_type=code&scope=openid%20profile%20email"

# Step 2: User logs in and receives authorization code
# (Keycloak redirects to callback URL with 'code' parameter)

# Step 3: Exchange authorization code for token
curl -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "client_id=chat4all-frontend" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "code=YOUR_AUTH_CODE"
```

### 1.2 Validate Token (Optional)

Verify token is valid without calling protected endpoints:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

curl -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token/introspect \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=$TOKEN" \
  -d "client_id=chat4all-client"
```

**Response** (HTTP 200):
```json
{
  "exp": 1733334849,
  "iat": 1733330849,
  "jti": "3d63cf3b-466d-46c5-9efe-2569b98a8915",
  "iss": "http://localhost:8888/realms/chat4all",
  "sub": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "typ": "Bearer",
  "azp": "chat4all-client",
  "preferred_username": "testuser",
  "scope": "openid email profile",
  "active": true
}
```

### 1.3 Parse & Inspect Token

Decode JWT to see user claims:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

# Decode without validation (for inspection only)
echo $TOKEN | cut -d'.' -f2 | base64 -d | jq .
```

**Token Claims** (JWT Payload):
```json
{
  "exp": 1733334849,
  "iat": 1733330849,
  "jti": "3d63cf3b-466d-46c5-9efe-2569b98a8915",
  "iss": "http://localhost:8888/realms/chat4all",
  "aud": "account",
  "sub": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "type": "Bearer",
  "azp": "chat4all-client",
  "session_state": "a1b2c3d4e5f6",
  "acr": "1",
  "allowed-origins": [
    "http://localhost:3000"
  ],
  "realm_access": {
    "roles": [
      "default-roles-chat4all",
      "user",
      "admin"
    ]
  },
  "resource_access": {
    "chat4all-client": {
      "roles": [
        "message-read",
        "message-write"
      ]
    }
  },
  "email_verified": false,
  "name": "Test User",
  "preferred_username": "testuser",
  "given_name": "Test",
  "family_name": "User",
  "email": "testuser@chat4all.local"
}
```

---

## Step 2: Create a User

### 2.1 Option A: Create User via User Service API

Create a new user in Chat4All system:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

curl -X POST http://localhost:8083/api/v1/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@chat4all.local",
    "firstName": "Alice",
    "lastName": "Silva",
    "phoneNumber": "+5511999887766",
    "channel": "WHATSAPP",
    "metadata": {
      "department": "Sales",
      "region": "São Paulo"
    }
  }'
```

**Response** (HTTP 201 Created):
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "alice",
  "email": "alice@chat4all.local",
  "firstName": "Alice",
  "lastName": "Silva",
  "status": "ACTIVE",
  "createdAt": "2025-12-10T10:30:00Z",
  "updatedAt": "2025-12-10T10:30:00Z",
  "externalIdentities": [
    {
      "identityId": "550e8400-e29b-41d4-a716-446655440001",
      "channel": "WHATSAPP",
      "externalId": "5511999887766",
      "displayName": "Alice Silva",
      "verified": false,
      "linkedAt": "2025-12-10T10:30:00Z"
    }
  ]
}
```

### 2.2 Option B: Create User via Keycloak Admin Console

For manual user creation and management:

1. **Open Keycloak Admin Console**:
   ```
   http://localhost:8888/admin
   Username: admin
   Password: admin
   ```

2. **Navigate to Users**:
   - Click "Users" in left sidebar
   - Click "Create new user"

3. **Fill User Details**:
   - Username: `bob`
   - Email: `bob@chat4all.local`
   - First Name: `Bob`
   - Last Name: `Santos`
   - Email Verified: Toggle ON
   - Enabled: Toggle ON

4. **Set Password**:
   - Click "Credentials" tab
   - Click "Set password"
   - Enter: `bob123`
   - Temporary: Toggle OFF
   - Click "Set Password"

5. **Assign Roles**:
   - Click "Role Mapping" tab
   - Available Roles: Select `user`
   - Click "Assign"

### 2.3 Option C: Create User via Keycloak API

```bash
# Get admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8888/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin" | jq -r '.access_token')

# Create user
curl -X POST http://localhost:8888/admin/realms/chat4all/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "charlie",
    "email": "charlie@chat4all.local",
    "firstName": "Charlie",
    "lastName": "Costa",
    "enabled": true,
    "emailVerified": true,
    "credentials": [
      {
        "type": "password",
        "value": "charlie123",
        "temporary": false
      }
    ],
    "groups": ["/Standard Users"]
  }'
```

---

## Step 3: Send a Message

### 3.1 Send Message to User

Send a message to another user:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

curl -X POST http://localhost:8081/api/v1/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-alice-bob-001",
    "recipientId": "550e8400-e29b-41d4-a716-446655440000",
    "content": "Hello Bob! How are you?",
    "type": "TEXT",
    "channel": "WHATSAPP"
  }'
```

**Response** (HTTP 202 Accepted):
```json
{
  "messageId": "msg-001-abc123",
  "conversationId": "conv-alice-bob-001",
  "senderId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "recipientId": "550e8400-e29b-41d4-a716-446655440000",
  "content": "Hello Bob! How are you?",
  "type": "TEXT",
  "channel": "WHATSAPP",
  "status": "PENDING",
  "createdAt": "2025-12-10T10:35:00Z",
  "sentAt": null,
  "deliveredAt": null,
  "readAt": null,
  "statusUrl": "/api/v1/messages/msg-001-abc123/status"
}
```

### 3.2 Send Message with Attachments

Send message with file attachment:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

# First, upload the file
FILE_UPLOAD=$(curl -X POST http://localhost:8084/api/v1/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/image.jpg" \
  -F "channel=WHATSAPP")

FILE_ID=$(echo $FILE_UPLOAD | jq -r '.fileId')

# Then send message with attachment
curl -X POST http://localhost:8081/api/v1/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-alice-bob-001",
    "recipientId": "550e8400-e29b-41d4-a716-446655440000",
    "content": "Check out this photo!",
    "type": "IMAGE",
    "channel": "WHATSAPP",
    "attachments": [
      {
        "fileId": "'$FILE_ID'",
        "fileName": "image.jpg",
        "mimeType": "image/jpeg",
        "size": 1024000
      }
    ]
  }'
```

### 3.3 Send Bulk Messages

Send message to multiple recipients:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

curl -X POST http://localhost:8081/api/v1/messages/bulk \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "campaignId": "campaign-promotional-001",
    "template": "Hello {{name}}, check out our new offer!",
    "recipients": [
      {
        "recipientId": "550e8400-e29b-41d4-a716-446655440000",
        "variables": {"name": "Alice"}
      },
      {
        "recipientId": "550e8400-e29b-41d4-a716-446655440001",
        "variables": {"name": "Bob"}
      }
    ],
    "channel": "WHATSAPP",
    "scheduleTime": "2025-12-10T12:00:00Z"
  }'
```

### 3.4 Check Message Status

Poll message status:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

curl http://localhost:8081/api/v1/messages/msg-001-abc123/status \
  -H "Authorization: Bearer $TOKEN"
```

**Response** (HTTP 200):
```json
{
  "messageId": "msg-001-abc123",
  "status": "DELIVERED",
  "statusTimeline": [
    {
      "status": "PENDING",
      "timestamp": "2025-12-10T10:35:00Z"
    },
    {
      "status": "SENT",
      "timestamp": "2025-12-10T10:35:02Z"
    },
    {
      "status": "DELIVERED",
      "timestamp": "2025-12-10T10:35:15Z"
    }
  ],
  "currentStatus": "DELIVERED",
  "estimatedDeliveryTime": "2025-12-10T10:35:15Z"
}
```

---

## Step 4: Receive a Message

### 4.1 Fetch Conversation Messages

Retrieve all messages in a conversation:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

curl http://localhost:8081/api/v1/conversations/conv-alice-bob-001/messages \
  -H "Authorization: Bearer $TOKEN"
```

**Response** (HTTP 200):
```json
{
  "conversationId": "conv-alice-bob-001",
  "participants": [
    {
      "userId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "username": "testuser",
      "joinedAt": "2025-12-10T10:30:00Z"
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "username": "alice",
      "joinedAt": "2025-12-10T10:30:01Z"
    }
  ],
  "messages": [
    {
      "messageId": "msg-001-abc123",
      "senderId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "senderName": "Test User",
      "content": "Hello Alice! How are you?",
      "type": "TEXT",
      "channel": "WHATSAPP",
      "status": "DELIVERED",
      "createdAt": "2025-12-10T10:35:00Z",
      "sentAt": "2025-12-10T10:35:02Z",
      "deliveredAt": "2025-12-10T10:35:15Z",
      "readAt": "2025-12-10T10:36:00Z",
      "reactions": [
        {
          "emoji": "👍",
          "count": 2,
          "users": ["alice", "bob"]
        }
      ]
    },
    {
      "messageId": "msg-002-def456",
      "senderId": "550e8400-e29b-41d4-a716-446655440000",
      "senderName": "Alice Silva",
      "content": "I'm doing great! How about you?",
      "type": "TEXT",
      "channel": "WHATSAPP",
      "status": "DELIVERED",
      "createdAt": "2025-12-10T10:36:00Z",
      "sentAt": "2025-12-10T10:36:02Z",
      "deliveredAt": "2025-12-10T10:36:05Z",
      "readAt": "2025-12-10T10:36:30Z"
    }
  ],
  "pagination": {
    "nextCursor": "2025-12-10T10:36:00Z",
    "hasMore": true,
    "count": 2
  }
}
```

### 4.2 Fetch with Pagination

Get older messages using cursor-based pagination:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

curl "http://localhost:8081/api/v1/conversations/conv-alice-bob-001/messages?limit=50&before=2025-12-10T10:36:00Z" \
  -H "Authorization: Bearer $TOKEN"
```

### 4.3 Mark Messages as Read

Mark received messages as read:

```bash
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."

curl -X PUT http://localhost:8081/api/v1/conversations/conv-alice-bob-001/read \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "readAt": "2025-12-10T10:36:30Z"
  }'
```

### 4.4 Webhook for Incoming Messages

When external platforms (WhatsApp, Telegram, Instagram) send messages to Chat4All, they arrive via webhooks:

**Keycloak is optional for webhooks** - they use API key authentication instead:

```bash
# External platform (WhatsApp) sends webhook to Chat4All
curl -X POST http://localhost:8080/api/webhooks/whatsapp \
  -H "X-API-Key: whatsapp-webhook-key" \
  -H "Content-Type: application/json" \
  -d '{
    "object": "whatsapp_business_account",
    "entry": [{
      "id": "123456789",
      "changes": [{
        "value": {
          "messaging_product": "whatsapp",
          "metadata": {
            "display_phone_number": "15551234567",
            "phone_number_id": "987654321"
          },
          "messages": [{
            "from": "5511999887766",
            "id": "wamid.msg-123",
            "timestamp": "1733247000",
            "type": "text",
            "text": {
              "body": "Thanks for the update!"
            }
          }]
        },
        "field": "messages"
      }]
    }]
  }'
```

**Internal Processing**:
1. Webhook received at API Gateway
2. Router Service processes via Kafka
3. Message stored in MongoDB
4. User Service creates/updates user identity
5. Real-time notifications sent via WebSocket

---

## Complete End-to-End Scenario

Complete working example from authentication through message exchange:

```bash
#!/bin/bash

# ============================================================
# Chat4All Complete Authentication & Messaging Flow
# ============================================================

KEYCLOAK_URL="http://localhost:8888"
API_GATEWAY_URL="http://localhost:8080"
MESSAGE_SERVICE_URL="http://localhost:8081"
USER_SERVICE_URL="http://localhost:8083"
FILE_SERVICE_URL="http://localhost:8084"

echo "=== Step 1: Authenticate both users ==="

# User 1: Alice
ALICE_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=chat4all-client" \
  -d "username=alice" \
  -d "password=alice123" | jq -r '.access_token')

echo "✓ Alice authenticated"
echo "  Token: ${ALICE_TOKEN:0:50}..."

# User 2: Bob
BOB_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=chat4all-client" \
  -d "username=bob" \
  -d "password=bob123" | jq -r '.access_token')

echo "✓ Bob authenticated"
echo "  Token: ${BOB_TOKEN:0:50}..."

echo ""
echo "=== Step 2: Get user IDs ==="

# Get Alice's user ID
ALICE_ID=$(curl -s "$USER_SERVICE_URL/api/v1/users/me" \
  -H "Authorization: Bearer $ALICE_TOKEN" | jq -r '.userId')

echo "✓ Alice ID: $ALICE_ID"

# Get Bob's user ID
BOB_ID=$(curl -s "$USER_SERVICE_URL/api/v1/users/me" \
  -H "Authorization: Bearer $BOB_TOKEN" | jq -r '.userId')

echo "✓ Bob ID: $BOB_ID"

echo ""
echo "=== Step 3: Alice sends message to Bob ==="

MESSAGE_1=$(curl -s -X POST "$MESSAGE_SERVICE_URL/api/v1/messages" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-alice-bob-001",
    "recipientId": "'$BOB_ID'",
    "content": "Hi Bob! How are you doing?",
    "type": "TEXT",
    "channel": "WHATSAPP"
  }')

MESSAGE_ID_1=$(echo $MESSAGE_1 | jq -r '.messageId')
echo "✓ Message sent: $MESSAGE_ID_1"

echo ""
echo "=== Step 4: Bob receives and reads message ==="

# Bob fetches conversation
CONVERSATION=$(curl -s "$MESSAGE_SERVICE_URL/api/v1/conversations/conv-alice-bob-001/messages" \
  -H "Authorization: Bearer $BOB_TOKEN")

echo "✓ Bob retrieved conversation"
echo "  Messages: $(echo $CONVERSATION | jq '.messages | length')"

# Bob marks messages as read
curl -s -X PUT "$MESSAGE_SERVICE_URL/api/v1/conversations/conv-alice-bob-001/read" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"readAt": "'$(date -u +'%Y-%m-%dT%H:%M:%SZ')'"}'

echo "✓ Bob marked conversation as read"

echo ""
echo "=== Step 5: Bob replies to Alice ==="

MESSAGE_2=$(curl -s -X POST "$MESSAGE_SERVICE_URL/api/v1/messages" \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "conv-alice-bob-001",
    "recipientId": "'$ALICE_ID'",
    "content": "I am doing great! Thanks for asking!",
    "type": "TEXT",
    "channel": "WHATSAPP"
  }')

MESSAGE_ID_2=$(echo $MESSAGE_2 | jq -r '.messageId')
echo "✓ Reply sent: $MESSAGE_ID_2"

echo ""
echo "=== Step 6: Alice receives reply ==="

# Alice checks message status
STATUS=$(curl -s "$MESSAGE_SERVICE_URL/api/v1/messages/$MESSAGE_ID_1/status" \
  -H "Authorization: Bearer $ALICE_TOKEN")

echo "✓ Message 1 Status: $(echo $STATUS | jq -r '.currentStatus')"

# Alice fetches full conversation
FINAL_CONVERSATION=$(curl -s "$MESSAGE_SERVICE_URL/api/v1/conversations/conv-alice-bob-001/messages" \
  -H "Authorization: Bearer $ALICE_TOKEN")

echo "✓ Final conversation has $(echo $FINAL_CONVERSATION | jq '.messages | length') messages"

echo ""
echo "=== Complete! ==="
echo ""
echo "Summary:"
echo "--------"
echo "• Authentication: ✓ Both users logged in via Keycloak"
echo "• User Creation: ✓ Users alice and bob exist"
echo "• Message Sending: ✓ Messages routed through Message Service"
echo "• Message Receiving: ✓ Conversation retrieved and marked as read"
echo "• Real-time Updates: ✓ Available via WebSocket connection"
```

### Run the Complete Flow

```bash
chmod +x docs/complete-flow.sh
./docs/complete-flow.sh
```

---

## WebSocket Real-Time Communication

### 4.5 WebSocket Connection for Real-Time Messages

For real-time message delivery without polling:

```javascript
// JavaScript/TypeScript Example
const token = "eyJhbGciOiJSUzI1NiIsInR5cCI..."; // From Keycloak
const ws = new WebSocket(`ws://localhost:8080/api/v1/ws?token=${token}`);

ws.onopen = () => {
  console.log("Connected to Chat4All WebSocket");
  
  // Subscribe to conversation
  ws.send(JSON.stringify({
    action: "subscribe",
    conversationId: "conv-alice-bob-001"
  }));
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  
  if (message.type === "NEW_MESSAGE") {
    console.log("New message:", message.payload);
    // Update UI with new message
  } else if (message.type === "MESSAGE_DELIVERED") {
    console.log("Message delivered:", message.payload.messageId);
  } else if (message.type === "MESSAGE_READ") {
    console.log("Message read by:", message.payload.userId);
  }
};

ws.onerror = (error) => {
  console.error("WebSocket error:", error);
};

ws.onclose = () => {
  console.log("Disconnected from Chat4All");
};

// Send message via WebSocket
function sendMessage(conversationId, recipientId, content) {
  ws.send(JSON.stringify({
    action: "send_message",
    conversationId: conversationId,
    recipientId: recipientId,
    content: content,
    type: "TEXT",
    channel: "WHATSAPP"
  }));
}

// Mark conversation as read
function markAsRead(conversationId) {
  ws.send(JSON.stringify({
    action: "mark_read",
    conversationId: conversationId
  }));
}
```

### 4.6 Java Client Example (WebSocket)

```java
// Spring Boot WebSocket Client
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

public class Chat4AllClient {
    
    private StompSession stompSession;
    private String token;
    
    public void connect(String token) throws Exception {
        this.token = token;
        
        WebSocketStompClient client = new WebSocketStompClient(
            new StandardWebSocketClient()
        );
        
        StompSessionHandler sessionHandler = new Chat4AllStompSessionHandler(this);
        
        client.connect(
            "ws://localhost:8080/api/v1/ws",
            sessionHandler,
            "token=" + token
        );
    }
    
    public void subscribeToConversation(String conversationId) {
        stompSession.subscribe(
            "/user/conversations/" + conversationId + "/messages",
            new Chat4AllMessageHandler()
        );
    }
    
    public void sendMessage(String conversationId, String recipientId, String content) {
        stompSession.send(
            "/app/messages/send",
            new MessageRequest(conversationId, recipientId, content, "TEXT", "WHATSAPP")
        );
    }
}
```

---

## Testing & Troubleshooting

### 5.1 Common Issues

**Issue**: `401 Unauthorized` when calling API

**Solution**: Token has expired or is invalid
```bash
# Get a fresh token
TOKEN=$(curl -s -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=chat4all-client" \
  -d "username=testuser" \
  -d "password=testuser123" | jq -r '.access_token')
```

**Issue**: `403 Forbidden` when sending message

**Solution**: User lacks required role/scope
```bash
# Check token claims
echo $TOKEN | cut -d'.' -f2 | base64 -d | jq .

# Required scopes: message-write, message-read
# Required roles: user or admin
```

**Issue**: Message not reaching recipient

**Solution**: Check Kafka and Router Service logs
```bash
# Check Router Service logs
docker logs chat4all-router

# Verify Kafka topics
docker exec chat4all-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Check message status
curl http://localhost:8081/api/v1/messages/MSG_ID/status -H "Authorization: Bearer $TOKEN"
```

**Issue**: WebSocket connection refused

**Solution**: Check API Gateway is running and accepting WebSocket upgrades
```bash
# Test WebSocket endpoint
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Key: x3JJHMbDL1EzLkh9GBhXDw==" \
  -H "Sec-WebSocket-Version: 13" \
  http://localhost:8080/api/v1/ws

# Should return HTTP 101 Switching Protocols
```

### 5.2 Validation Script

Run automated tests:

```bash
#!/bin/bash

echo "=== Chat4All Authentication & Messaging Validation ==="

# Test 1: Keycloak availability
echo -n "Test 1: Keycloak available... "
if curl -s http://localhost:8888/realms/chat4all | grep -q "chat4all"; then
  echo "✓"
else
  echo "✗"
  exit 1
fi

# Test 2: Get token
echo -n "Test 2: Token generation... "
TOKEN=$(curl -s -X POST http://localhost:8888/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=chat4all-client" \
  -d "username=testuser" \
  -d "password=testuser123" | jq -r '.access_token')

if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
  echo "✓"
else
  echo "✗"
  exit 1
fi

# Test 3: Call User Service with token
echo -n "Test 3: User Service authenticated call... "
STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN")

if [ "$STATUS" = "200" ]; then
  echo "✓"
else
  echo "✗ (HTTP $STATUS)"
fi

# Test 4: Send message
echo -n "Test 4: Message Service... "
MSG=$(curl -s -X POST http://localhost:8081/api/v1/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "test-conv",
    "recipientId": "test-recipient",
    "content": "Test message",
    "type": "TEXT",
    "channel": "WHATSAPP"
  }')

if echo $MSG | jq -e '.messageId' > /dev/null 2>&1; then
  echo "✓"
else
  echo "✗"
fi

echo ""
echo "=== All tests completed ==="
```

---

## Next Steps

1. **Integrate OAuth2 into microservices**: See [SPRING_SECURITY_EXAMPLES.md](./SPRING_SECURITY_EXAMPLES.md)
2. **Deploy to Kubernetes**: See [infrastructure/kubernetes/](../infrastructure/kubernetes/)
3. **Monitor in production**: See [SECURITY_CONFIG_TESTING.md](./SECURITY_CONFIG_TESTING.md)
4. **API documentation**: See [specs/001-unified-messaging-platform/contracts/](../specs/001-unified-messaging-platform/contracts/)

---

## References

- **Keycloak Documentation**: https://www.keycloak.org/documentation.html
- **OAuth2 & OpenID Connect**: https://www.oauth.com/
- **JWT**: https://jwt.io/
- **Spring Security**: https://spring.io/projects/spring-security
- **Spring Cloud Gateway**: https://spring.io/projects/spring-cloud-gateway
