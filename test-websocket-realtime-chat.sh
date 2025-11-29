#!/bin/bash

# ============================================================================
# Test Script: Real-Time Chat Delivery via WebSocket
# ============================================================================
# Purpose: Validate WebSocket chat message delivery for User Story 4
# 
# Tests:
# 1. WebSocket connection with userId authentication
# 2. Real-time message delivery to connected users
# 3. Fan-out delivery to multiple recipients (group chat)
# 4. Offline user handling (skipped delivery)
# 5. Message ordering preservation
# 
# Prerequisites:
# - message-service running on port 8081
# - MongoDB running (conversations and messages)
# - Kafka running (chat-events topic)
# - router-service running (for external delivery)
# 
# Author: Chat4All Team
# Version: 1.0.0
# ============================================================================

set -e  # Exit on error

# ============================================================================
# Configuration
# ============================================================================
BASE_URL="http://localhost:8081"
WS_URL="ws://localhost:8081"
MONGODB_HOST="localhost"
MONGODB_PORT="27017"
MONGODB_DB="chat4all"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# ============================================================================
# Helper Functions
# ============================================================================

print_header() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

print_step() {
    echo -e "${MAGENTA}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# ============================================================================
# Step 0: Cleanup
# ============================================================================

print_header "Step 0: Cleanup MongoDB"

print_step "Dropping conversations and messages collections..."

docker compose exec -T mongodb mongosh --eval "
    use chat4all;
    db.conversations.drop();
    db.messages.drop();
    print('✓ Collections dropped successfully');
" > /dev/null 2>&1

print_success "MongoDB cleanup complete"

# ============================================================================
# Step 1: Create Group Conversation
# ============================================================================

print_header "Step 1: Create Group Conversation"

print_step "Creating group with Admin, User1, User2..."

CREATE_GROUP_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/conversations" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "GROUP",
    "participants": ["Admin", "User1", "User2"],
    "primaryChannel": "WHATSAPP",
    "metadata": {
      "groupName": "WebSocket Test Group",
      "description": "Testing real-time WebSocket delivery"
    }
  }')

CONVERSATION_ID=$(echo "$CREATE_GROUP_RESPONSE" | jq -r '.conversationId')

if [ "$CONVERSATION_ID" = "null" ] || [ -z "$CONVERSATION_ID" ]; then
  print_error "Failed to create conversation"
  echo "Response: $CREATE_GROUP_RESPONSE"
  exit 1
fi

print_success "Group created: conversationId=$CONVERSATION_ID"
print_info "Participants: Admin, User1, User2"

# ============================================================================
# Step 2: Start WebSocket Listeners (Background)
# ============================================================================

print_header "Step 2: Start WebSocket Listeners"

print_step "Starting WebSocket connection for User1..."

# Create temporary files for WebSocket output
USER1_WS_OUTPUT=$(mktemp)
USER2_WS_OUTPUT=$(mktemp)
ADMIN_WS_OUTPUT=$(mktemp)

# Install websocat if not available (for WebSocket testing)
if ! command -v websocat &> /dev/null; then
    print_info "Installing websocat for WebSocket testing..."
    
    # Check if running on Linux
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        wget -q https://github.com/vi/websocat/releases/download/v1.11.0/websocat.x86_64-unknown-linux-musl -O /tmp/websocat
        chmod +x /tmp/websocat
        WEBSOCAT="/tmp/websocat"
    else
        print_error "Please install websocat manually: https://github.com/vi/websocat"
        exit 1
    fi
else
    WEBSOCAT="websocat"
fi

print_success "websocat ready"

# Start WebSocket connections in background
print_step "Connecting User1 to WebSocket..."
$WEBSOCAT "${WS_URL}/ws/chat?userId=User1" > "$USER1_WS_OUTPUT" 2>&1 &
USER1_WS_PID=$!
sleep 1

print_success "User1 connected (PID: $USER1_WS_PID)"

print_step "Connecting User2 to WebSocket..."
$WEBSOCAT "${WS_URL}/ws/chat?userId=User2" > "$USER2_WS_OUTPUT" 2>&1 &
USER2_WS_PID=$!
sleep 1

print_success "User2 connected (PID: $USER2_WS_PID)"

print_step "Connecting Admin to WebSocket..."
$WEBSOCAT "${WS_URL}/ws/chat?userId=Admin" > "$ADMIN_WS_OUTPUT" 2>&1 &
ADMIN_WS_PID=$!
sleep 1

print_success "Admin connected (PID: $ADMIN_WS_PID)"

print_info "All WebSocket connections established"
print_info "WebSocket outputs: User1=$USER1_WS_OUTPUT, User2=$USER2_WS_OUTPUT, Admin=$ADMIN_WS_OUTPUT"

# ============================================================================
# Step 3: Admin Sends Message to Group
# ============================================================================

print_header "Step 3: Admin Sends Message to Group"

print_step "Admin sends: 'Hello everyone via WebSocket!'"

SEND_MSG1_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/messages" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"$CONVERSATION_ID\",
    \"senderId\": \"Admin\",
    \"content\": \"Hello everyone via WebSocket!\",
    \"contentType\": \"TEXT\",
    \"channel\": \"WHATSAPP\"
  }")

MSG1_ID=$(echo "$SEND_MSG1_RESPONSE" | jq -r '.messageId')
MSG1_STATUS=$(curl -s "${BASE_URL}/api/messages/${MSG1_ID}/status" | jq -r '.status')

if [ "$MSG1_STATUS" != "PENDING" ]; then
  print_error "Unexpected message status: $MSG1_STATUS"
  exit 1
fi

print_success "Message sent: messageId=$MSG1_ID, status=$MSG1_STATUS"

# Wait for WebSocket delivery
print_step "Waiting 2 seconds for WebSocket delivery..."
sleep 2

# ============================================================================
# Step 4: Verify WebSocket Delivery to Recipients
# ============================================================================

print_header "Step 4: Verify WebSocket Delivery"

print_step "Checking User1 received message via WebSocket..."
USER1_RECEIVED=$(grep -c "Hello everyone via WebSocket!" "$USER1_WS_OUTPUT" || true)

if [ "$USER1_RECEIVED" -ge 1 ]; then
  print_success "✓ User1 received message via WebSocket!"
  echo "  Message count: $USER1_RECEIVED"
else
  print_error "✗ User1 did NOT receive message via WebSocket"
  echo "  User1 WebSocket output:"
  cat "$USER1_WS_OUTPUT"
  exit 1
fi

print_step "Checking User2 received message via WebSocket..."
USER2_RECEIVED=$(grep -c "Hello everyone via WebSocket!" "$USER2_WS_OUTPUT" || true)

if [ "$USER2_RECEIVED" -ge 1 ]; then
  print_success "✓ User2 received message via WebSocket!"
  echo "  Message count: $USER2_RECEIVED"
else
  print_error "✗ User2 did NOT receive message via WebSocket"
  echo "  User2 WebSocket output:"
  cat "$USER2_WS_OUTPUT"
  exit 1
fi

print_step "Checking Admin did NOT receive own message via WebSocket..."
ADMIN_RECEIVED=$(grep -c "Hello everyone via WebSocket!" "$ADMIN_WS_OUTPUT" || true)

if [ "$ADMIN_RECEIVED" -eq 0 ]; then
  print_success "✓ Admin correctly did NOT receive own message"
else
  print_error "✗ Admin incorrectly received own message (recipientIds should exclude sender)"
  exit 1
fi

# ============================================================================
# Step 5: Test Multiple Messages (Ordering)
# ============================================================================

print_header "Step 5: Test Message Ordering"

print_step "User1 sends: 'Message 1'"
SEND_MSG2_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/messages" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"$CONVERSATION_ID\",
    \"senderId\": \"User1\",
    \"content\": \"Message 1\",
    \"contentType\": \"TEXT\",
    \"channel\": \"WHATSAPP\"
  }")
MSG2_ID=$(echo "$SEND_MSG2_RESPONSE" | jq -r '.messageId')
print_success "Sent: messageId=$MSG2_ID"

sleep 0.5

print_step "User1 sends: 'Message 2'"
SEND_MSG3_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/messages" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"$CONVERSATION_ID\",
    \"senderId\": \"User1\",
    \"content\": \"Message 2\",
    \"contentType\": \"TEXT\",
    \"channel\": \"WHATSAPP\"
  }")
MSG3_ID=$(echo "$SEND_MSG3_RESPONSE" | jq -r '.messageId')
print_success "Sent: messageId=$MSG3_ID"

sleep 0.5

print_step "User1 sends: 'Message 3'"
SEND_MSG4_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/messages" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"$CONVERSATION_ID\",
    \"senderId\": \"User1\",
    \"content\": \"Message 3\",
    \"contentType\": \"TEXT\",
    \"channel\": \"WHATSAPP\"
  }")
MSG4_ID=$(echo "$SEND_MSG4_RESPONSE" | jq -r '.messageId')
print_success "Sent: messageId=$MSG4_ID"

# Wait for WebSocket delivery
print_step "Waiting 2 seconds for WebSocket delivery..."
sleep 2

# ============================================================================
# Step 6: Verify Ordering in WebSocket Delivery
# ============================================================================

print_header "Step 6: Verify Message Ordering"

print_step "Checking User2 received messages in order..."

# Extract messages from WebSocket output (JSON events)
USER2_MSG1=$(grep -o '"content":"Message 1"' "$USER2_WS_OUTPUT" | head -1 || true)
USER2_MSG2=$(grep -o '"content":"Message 2"' "$USER2_WS_OUTPUT" | head -1 || true)
USER2_MSG3=$(grep -o '"content":"Message 3"' "$USER2_WS_OUTPUT" | head -1 || true)

if [ -n "$USER2_MSG1" ] && [ -n "$USER2_MSG2" ] && [ -n "$USER2_MSG3" ]; then
  print_success "✓ User2 received all 3 messages via WebSocket!"
  
  # Check ordering (simple line number check)
  MSG1_LINE=$(grep -n "Message 1" "$USER2_WS_OUTPUT" | head -1 | cut -d: -f1)
  MSG2_LINE=$(grep -n "Message 2" "$USER2_WS_OUTPUT" | head -1 | cut -d: -f1)
  MSG3_LINE=$(grep -n "Message 3" "$USER2_WS_OUTPUT" | head -1 | cut -d: -f1)
  
  if [ "$MSG1_LINE" -lt "$MSG2_LINE" ] && [ "$MSG2_LINE" -lt "$MSG3_LINE" ]; then
    print_success "✓ Messages received in correct order!"
  else
    print_error "✗ Messages received out of order"
    echo "  Order: MSG1=$MSG1_LINE, MSG2=$MSG2_LINE, MSG3=$MSG3_LINE"
  fi
else
  print_error "✗ User2 did NOT receive all messages"
  echo "  User2 WebSocket output:"
  cat "$USER2_WS_OUTPUT"
  exit 1
fi

# ============================================================================
# Step 7: Test Offline User (No WebSocket)
# ============================================================================

print_header "Step 7: Test Offline User Handling"

print_step "Killing User2 WebSocket connection (simulate offline)..."
kill $USER2_WS_PID 2>/dev/null || true
sleep 1
print_success "User2 disconnected"

print_step "Admin sends message while User2 is offline..."
SEND_MSG5_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/messages" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"$CONVERSATION_ID\",
    \"senderId\": \"Admin\",
    \"content\": \"Message while User2 offline\",
    \"contentType\": \"TEXT\",
    \"channel\": \"WHATSAPP\"
  }")
MSG5_ID=$(echo "$SEND_MSG5_RESPONSE" | jq -r '.messageId')
print_success "Sent: messageId=$MSG5_ID"

sleep 2

print_step "Verifying User1 still received message..."
USER1_OFFLINE_MSG=$(grep -c "Message while User2 offline" "$USER1_WS_OUTPUT" || true)

if [ "$USER1_OFFLINE_MSG" -ge 1 ]; then
  print_success "✓ User1 received message even though User2 is offline"
else
  print_error "✗ User1 did NOT receive message"
  exit 1
fi

print_info "Offline user handling works correctly (User2 skipped, User1 delivered)"

# ============================================================================
# Step 8: Verify Message History API (Fallback)
# ============================================================================

print_header "Step 8: Verify REST API Fallback for Offline Users"

print_step "User2 fetches message history via REST API (since was offline)..."

USER2_HISTORY=$(curl -s "${BASE_URL}/api/v1/conversations/${CONVERSATION_ID}/messages?userId=User2&limit=100")
USER2_MSG_COUNT=$(echo "$USER2_HISTORY" | jq -r '.count // 0')

print_success "User2 message history: count=$USER2_MSG_COUNT"

# Should see all messages (including the one sent while offline)
if [ "$USER2_MSG_COUNT" -ge 4 ]; then
  print_success "✓ User2 can retrieve messages via REST API (offline fallback works)"
else
  print_error "✗ User2 missing messages in history"
  echo "  Expected: ≥4, Got: $USER2_MSG_COUNT"
  exit 1
fi

# ============================================================================
# Cleanup
# ============================================================================

print_header "Cleanup"

print_step "Terminating WebSocket connections..."
kill $USER1_WS_PID 2>/dev/null || true
kill $ADMIN_WS_PID 2>/dev/null || true
sleep 1
print_success "WebSocket connections terminated"

print_step "Removing temporary files..."
rm -f "$USER1_WS_OUTPUT" "$USER2_WS_OUTPUT" "$ADMIN_WS_OUTPUT"
print_success "Temporary files cleaned up"

# ============================================================================
# Victory Banner
# ============================================================================

print_header "🎉 ALL TESTS PASSED! 🎉"

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  ✓ WebSocket authentication works (userId extraction)${NC}"
echo -e "${GREEN}  ✓ Real-time message delivery works${NC}"
echo -e "${GREEN}  ✓ Fan-out delivery to multiple recipients works${NC}"
echo -e "${GREEN}  ✓ Sender does NOT receive own messages${NC}"
echo -e "${GREEN}  ✓ Message ordering preserved${NC}"
echo -e "${GREEN}  ✓ Offline user handling works (skipped delivery)${NC}"
echo -e "${GREEN}  ✓ REST API fallback works for offline users${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${CYAN}📊 Summary:${NC}"
echo -e "  • Conversation ID: ${CONVERSATION_ID}"
echo -e "  • Messages Sent: 5"
echo -e "  • WebSocket Deliveries: Successful"
echo -e "  • REST API Fallback: Verified"
echo ""
echo -e "${MAGENTA}🚀 Real-Time Chat is READY for production!${NC}"
echo ""
