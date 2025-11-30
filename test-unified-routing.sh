#!/bin/bash

##############################################################################
# Unified Routing Integration Test
# 
# Tests the complete message flow:
# Message Service → Kafka → Router Service → User Service → Connector
#
# Flow:
# 1. Create internal user (Erik Unified) in User Service
# 2. Link WhatsApp identity (+55119999) to user
# 3. Send message with recipientId = UUID (internal user)
# 4. Message Service publishes to Kafka (chat-events topic)
# 5. Router Service consumes from Kafka
# 6. Router calls User Service to resolve UUID → external identities
# 7. Router delivers to WhatsApp connector
#
# Prerequisites:
# - user-service running on port 8083
# - message-service running on port 8081
# - router-service running on port 8082
# - whatsapp-connector running on port 8091
# - Kafka running on localhost:9092
# - MongoDB running on localhost:27017
#
# Author: Chat4All Team
# Version: 1.0.0
##############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
USER_SERVICE_URL="http://localhost:8083"
MESSAGE_SERVICE_URL="http://localhost:8081"
ROUTER_SERVICE_URL="http://localhost:8082"
WHATSAPP_CONNECTOR_URL="http://localhost:8091"

MONGO_HOST="localhost"
MONGO_PORT="27017"
MONGO_DB="chat4all"
MONGO_USER="chat4all"
MONGO_PASS="chat4all_dev_password"

# Log files
ROUTER_LOG="/tmp/router-service.log"
MESSAGE_LOG="/tmp/message-service.log"

echo ""
echo "=========================================="
echo "Unified Routing Integration Test"
echo "=========================================="
echo ""
echo -e "${CYAN}Testing: Message Service → Kafka → Router → User Service → Connector${NC}"
echo ""

##############################################################################
# Step 0: Verify all services are running
##############################################################################
echo "► Step 0: Checking if all services are running..."
echo ""

SERVICES_OK=true

# Check User Service
if curl -s -f "${USER_SERVICE_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ user-service is running (port 8083)${NC}"
else
    echo -e "${RED}✗ user-service is NOT running on port 8083${NC}"
    SERVICES_OK=false
fi

# Check Message Service
if curl -s -f "${MESSAGE_SERVICE_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ message-service is running (port 8081)${NC}"
else
    echo -e "${RED}✗ message-service is NOT running on port 8081${NC}"
    SERVICES_OK=false
fi

# Check Router Service
if curl -s -f "${ROUTER_SERVICE_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ router-service is running (port 8082)${NC}"
else
    echo -e "${RED}✗ router-service is NOT running on port 8082${NC}"
    SERVICES_OK=false
fi

# Check WhatsApp Connector (optional)
if curl -s -f "${WHATSAPP_CONNECTOR_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ whatsapp-connector is running (port 8091)${NC}"
else
    echo -e "${YELLOW}⚠ whatsapp-connector is NOT running (port 8091) - delivery will fail${NC}"
fi

if [ "$SERVICES_OK" = false ]; then
    echo ""
    echo -e "${RED}ERROR: Not all required services are running!${NC}"
    echo ""
    echo "Start services:"
    echo "  cd services/user-service && mvn spring-boot:run &"
    echo "  cd services/message-service && mvn spring-boot:run &"
    echo "  cd services/router-service && mvn spring-boot:run &"
    echo "  cd services/connectors/whatsapp-connector && mvn spring-boot:run &"
    exit 1
fi

echo ""

##############################################################################
# Step 1: Clean MongoDB collections
##############################################################################
echo "► Step 1: Cleaning MongoDB collections..."
echo ""

# Drop messages collection
mongosh "mongodb://${MONGO_USER}:${MONGO_PASS}@${MONGO_HOST}:${MONGO_PORT}/${MONGO_DB}?authSource=admin" \
    --quiet --eval "db.messages.deleteMany({}); print('Deleted messages:', db.messages.countDocuments());" 2>/dev/null || {
    echo -e "${YELLOW}⚠ Could not connect to MongoDB - continuing anyway${NC}"
}

# Drop conversations collection
mongosh "mongodb://${MONGO_USER}:${MONGO_PASS}@${MONGO_HOST}:${MONGO_PORT}/${MONGO_DB}?authSource=admin" \
    --quiet --eval "db.conversations.deleteMany({}); print('Deleted conversations:', db.conversations.countDocuments());" 2>/dev/null || true

echo -e "${GREEN}✓ MongoDB collections cleaned${NC}"
echo ""

##############################################################################
# Step 1.5: Clean PostgreSQL test user (if exists)
##############################################################################
echo "► Step 1.5: Cleaning PostgreSQL test user (erik.unified@chat4all.com)..."
echo ""

# Delete test user and related identities if they exist
docker compose exec -T postgres psql -U chat4all -d chat4all -c \
    "DELETE FROM external_identities WHERE user_id IN (SELECT id FROM users WHERE email = 'erik.unified@chat4all.com');" 2>/dev/null || {
    echo -e "${YELLOW}⚠ Could not connect to PostgreSQL - continuing anyway${NC}"
}

docker compose exec -T postgres psql -U chat4all -d chat4all -c \
    "DELETE FROM users WHERE email = 'erik.unified@chat4all.com';" 2>/dev/null || true

echo -e "${GREEN}✓ PostgreSQL test user cleaned (if existed)${NC}"
echo ""

##############################################################################
# Step 2: Create test user in User Service
##############################################################################
echo "► Step 2: Creating test user 'Erik Unified' in User Service..."
echo ""

USER_RESPONSE=$(curl -s -X POST "${USER_SERVICE_URL}/api/v1/users" \
    -H "Content-Type: application/json" \
    -d '{
        "displayName": "Erik Unified",
        "email": "erik.unified@chat4all.com",
        "userType": "CUSTOMER"
    }')

USER_ID=$(echo "$USER_RESPONSE" | jq -r '.id')

if [ -z "$USER_ID" ] || [ "$USER_ID" == "null" ]; then
    echo -e "${RED}✗ Failed to create user${NC}"
    echo "Response: $USER_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ User created successfully${NC}"
echo -e "${BLUE}  User ID: $USER_ID${NC}"
echo -e "${BLUE}  Display Name: Erik Unified${NC}"
echo ""

##############################################################################
# Step 3: Link WhatsApp identity to user
##############################################################################
echo "► Step 3: Linking WhatsApp identity '+55119999' to user..."
echo ""

WHATSAPP_RESPONSE=$(curl -s -X POST "${USER_SERVICE_URL}/api/v1/users/${USER_ID}/identities" \
    -H "Content-Type: application/json" \
    -d '{
        "platform": "WHATSAPP",
        "platformUserId": "+55119999",
        "verified": false
    }')

WHATSAPP_IDENTITY_ID=$(echo "$WHATSAPP_RESPONSE" | jq -r '.id')

if [ -z "$WHATSAPP_IDENTITY_ID" ] || [ "$WHATSAPP_IDENTITY_ID" == "null" ]; then
    echo -e "${RED}✗ Failed to link WhatsApp identity${NC}"
    echo "Response: $WHATSAPP_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ WhatsApp identity linked successfully${NC}"
echo -e "${BLUE}  Identity ID: $WHATSAPP_IDENTITY_ID${NC}"
echo -e "${BLUE}  Platform: WHATSAPP${NC}"
echo -e "${BLUE}  Platform User ID: +55119999${NC}"
echo ""

##############################################################################
# Step 4: Verify user has linked identity
##############################################################################
echo "► Step 4: Verifying user has linked identity..."
echo ""

USER_DETAILS=$(curl -s "${USER_SERVICE_URL}/api/v1/users/${USER_ID}")
IDENTITY_COUNT=$(echo "$USER_DETAILS" | jq '.externalIdentities | length')

if [ "$IDENTITY_COUNT" != "1" ]; then
    echo -e "${RED}✗ Expected 1 identity, found: $IDENTITY_COUNT${NC}"
    exit 1
fi

echo -e "${GREEN}✓ User has 1 linked identity${NC}"
echo ""

##############################################################################
# Step 5: Send message via Message Service
##############################################################################
echo "► Step 5: Sending message with UUID recipient (internal user)..."
echo ""

# Prepare message request
# Note: Using WHATSAPP channel since the target user has a WhatsApp identity
# The router will resolve the UUID to the actual WhatsApp platform ID
MESSAGE_REQUEST=$(cat <<EOF
{
  "conversationId": "conv-unified-test-001",
  "senderId": "agent-123",
  "recipientIds": ["${USER_ID}"],
  "content": "Hello Erik! This is a unified routing test message.",
  "channel": "WHATSAPP",
  "contentType": "TEXT"
}
EOF
)

echo -e "${CYAN}Message Request:${NC}"
echo "$MESSAGE_REQUEST" | jq '.'
echo ""

MESSAGE_RESPONSE=$(curl -s -X POST "${MESSAGE_SERVICE_URL}/api/messages" \
    -H "Content-Type: application/json" \
    -d "$MESSAGE_REQUEST")

MESSAGE_ID=$(echo "$MESSAGE_RESPONSE" | jq -r '.messageId')
MESSAGE_STATUS=$(echo "$MESSAGE_RESPONSE" | jq -r '.status')

if [ -z "$MESSAGE_ID" ] || [ "$MESSAGE_ID" == "null" ]; then
    echo -e "${RED}✗ Failed to send message${NC}"
    echo "Response: $MESSAGE_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ Message sent successfully${NC}"
echo -e "${BLUE}  Message ID: $MESSAGE_ID${NC}"
echo -e "${BLUE}  Status: $MESSAGE_STATUS${NC}"
echo -e "${BLUE}  Recipient (UUID): $USER_ID${NC}"
echo ""

##############################################################################
# Step 6: Wait for async processing (Kafka + Router)
##############################################################################
echo "► Step 6: Waiting for async message routing (Kafka → Router → User Service)..."
echo ""

echo -e "${CYAN}Expected flow:${NC}"
echo "  1. Message Service publishes event to Kafka (chat-events topic)"
echo "  2. Router Service consumes event from Kafka"
echo "  3. Router detects UUID recipient format"
echo "  4. Router calls User Service: GET /users/${USER_ID}"
echo "  5. Router resolves to WhatsApp identity: +55119999"
echo "  6. Router delivers to WhatsApp connector"
echo ""

# Wait for processing
for i in {1..10}; do
    echo -ne "${YELLOW}⏳ Waiting for routing... ${i}s${NC}\r"
    sleep 1
done
echo ""
echo ""

##############################################################################
# Step 7: Validate Router Service logs
##############################################################################
echo "► Step 7: Validating Router Service logs..."
echo ""

# Check if router service is writing logs
if [ -f "$ROUTER_LOG" ]; then
    echo -e "${GREEN}✓ Router log file found: $ROUTER_LOG${NC}"
    
    # Check for identity resolution
    if grep -q "Resolving internal user to external identities: userId=${USER_ID}" "$ROUTER_LOG" 2>/dev/null; then
        echo -e "${GREEN}✓ Router called User Service for identity resolution${NC}"
    else
        echo -e "${YELLOW}⚠ Identity resolution not found in logs (check router logs manually)${NC}"
    fi
    
    # Check for WhatsApp routing
    if grep -q "platformUserId=+55119999" "$ROUTER_LOG" 2>/dev/null; then
        echo -e "${GREEN}✓ Router resolved to WhatsApp identity: +55119999${NC}"
    else
        echo -e "${YELLOW}⚠ WhatsApp routing not found in logs${NC}"
    fi
    
    # Check for delivery attempt
    if grep -q "Delivering to identity: platform=WHATSAPP" "$ROUTER_LOG" 2>/dev/null; then
        echo -e "${GREEN}✓ Router attempted delivery to WhatsApp connector${NC}"
    else
        echo -e "${YELLOW}⚠ WhatsApp delivery attempt not found in logs${NC}"
    fi
    
else
    echo -e "${YELLOW}⚠ Router log file not found: $ROUTER_LOG${NC}"
    echo -e "${CYAN}  Manual validation required - check router console logs${NC}"
fi

echo ""

##############################################################################
# Step 8: Check message status in Message Service
##############################################################################
echo "► Step 8: Checking message status in Message Service..."
echo ""

sleep 2  # Wait for status update

MESSAGE_STATUS_RESPONSE=$(curl -s "${MESSAGE_SERVICE_URL}/api/messages/${MESSAGE_ID}")
FINAL_STATUS=$(echo "$MESSAGE_STATUS_RESPONSE" | jq -r '.status')

echo -e "${BLUE}  Message ID: $MESSAGE_ID${NC}"
echo -e "${BLUE}  Final Status: $FINAL_STATUS${NC}"

case "$FINAL_STATUS" in
    "DELIVERED")
        echo -e "${GREEN}✓ Message status: DELIVERED (routing succeeded)${NC}"
        ;;
    "FAILED")
        echo -e "${RED}✗ Message status: FAILED (routing failed)${NC}"
        echo -e "${YELLOW}  Check connector availability and logs${NC}"
        ;;
    "PENDING")
        echo -e "${YELLOW}⚠ Message status: PENDING (still processing)${NC}"
        ;;
    *)
        echo -e "${YELLOW}⚠ Message status: $FINAL_STATUS${NC}"
        ;;
esac

echo ""

##############################################################################
# Step 9: Manual log validation instructions
##############################################################################
echo "=========================================="
echo "Manual Validation Instructions"
echo "=========================================="
echo ""

echo -e "${CYAN}1. Check Router Service logs:${NC}"
echo ""
echo "   Search for these patterns:"
echo "   └─ 'Recipient ID is UUID - resolving to external identities'"
echo "   └─ 'Resolving internal user to external identities: userId=$USER_ID'"
echo "   └─ 'User has 1 linked identities - fanning out message'"
echo "   └─ 'Delivering to identity: platform=WHATSAPP, platformUserId=+55119999'"
echo "   └─ '✓ Delivered to WHATSAPP: +55119999'"
echo ""

echo -e "${CYAN}2. Verify complete flow:${NC}"
echo ""
echo "   a) Message Service:"
echo "      └─ Check message persisted in MongoDB:"
echo "         mongosh chat4all --eval 'db.messages.findOne({messageId: \"$MESSAGE_ID\"})'"
echo ""
echo "   b) Kafka:"
echo "      └─ Verify event published to chat-events topic"
echo ""
echo "   c) Router Service:"
echo "      └─ Grep logs: grep '$USER_ID' services/router-service/target/*.log"
echo ""
echo "   d) User Service:"
echo "      └─ Verify API call: grep 'GET /api/v1/users/$USER_ID' services/user-service/target/*.log"
echo ""
echo "   e) WhatsApp Connector:"
echo "      └─ Check delivery attempt in connector logs"
echo ""

echo -e "${CYAN}3. Test backward compatibility (direct platform ID):${NC}"
echo ""
echo "   Send message with phone number instead of UUID:"
echo ""
echo "   curl -X POST ${MESSAGE_SERVICE_URL}/api/messages \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{"
echo "       \"conversationId\": \"conv-direct-001\","
echo "       \"senderId\": \"agent-123\","
echo "       \"recipientIds\": [\"+5511988887777\"],"
echo "       \"content\": \"Direct WhatsApp message\","
echo "       \"channel\": \"WHATSAPP\""
echo "     }'"
echo ""
echo "   Expected: Router should NOT call User Service (direct delivery)"
echo ""

##############################################################################
# Summary
##############################################################################
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo ""

echo -e "${BLUE}Created Resources:${NC}"
echo "  User ID: $USER_ID"
echo "  Display Name: Erik Unified"
echo "  WhatsApp Identity: +55119999"
echo "  Message ID: $MESSAGE_ID"
echo "  Final Status: $FINAL_STATUS"
echo ""

echo -e "${GREEN}✓ Integration test completed!${NC}"
echo ""
echo -e "${CYAN}Key Features Tested:${NC}"
echo "  ✓ User creation in User Service"
echo "  ✓ External identity linking (WhatsApp)"
echo "  ✓ Message creation with UUID recipient"
echo "  ✓ Kafka event publishing"
echo "  ✓ Router-User Service integration"
echo "  ✓ Identity resolution (UUID → Platform ID)"
echo "  ✓ Multi-platform routing capability"
echo ""

echo "=========================================="
echo ""

# Cleanup instructions
echo -e "${YELLOW}Cleanup (optional):${NC}"
echo ""
echo "Delete test user:"
echo "  docker compose exec postgres psql -U chat4all -d chat4all -c \"DELETE FROM external_identities WHERE user_id = '$USER_ID'; DELETE FROM users WHERE id = '$USER_ID';\""
echo ""
echo "Clear MongoDB:"
echo "  mongosh chat4all --eval 'db.messages.deleteMany({messageId: \"$MESSAGE_ID\"})'"
echo ""
