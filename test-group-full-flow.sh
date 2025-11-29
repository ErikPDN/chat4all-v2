#!/bin/bash

#############################################
# Chat4All v2 - Group Messaging Test
# 
# Purpose: Validate group conversation creation
#          and multi-recipient fan-out routing
#############################################

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
MESSAGE_SERVICE_URL="http://localhost:8081"
CONVERSATION_API="${MESSAGE_SERVICE_URL}/api/v1/conversations"
MESSAGE_API="${MESSAGE_SERVICE_URL}/api/messages"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Chat4All v2 - Group Messaging Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

#############################################
# Step 1: Create Group Conversation
#############################################
echo -e "${YELLOW}Step 1: Creating group conversation...${NC}"

CONVERSATION_PAYLOAD='{
  "title": "Equipe de Resposta Rápida",
  "type": "GROUP",
  "participants": [
    "admin-user",
    "whatsapp:551199999999",
    "telegram:123456789"
  ]
}'

echo "Payload:"
echo "$CONVERSATION_PAYLOAD" | jq '.'
echo ""

CONVERSATION_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d "$CONVERSATION_PAYLOAD" \
  "$CONVERSATION_API")

# Extract HTTP status code (last line)
HTTP_STATUS=$(echo "$CONVERSATION_RESPONSE" | tail -n1)
# Extract response body (all but last line)
RESPONSE_BODY=$(echo "$CONVERSATION_RESPONSE" | sed '$d')

echo "Response Status: $HTTP_STATUS"
echo "Response Body:"
echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"
echo ""

# Validate HTTP 201 Created
if [ "$HTTP_STATUS" != "201" ]; then
  echo -e "${RED}❌ FAILED: Expected HTTP 201, got $HTTP_STATUS${NC}"
  exit 1
fi

# Extract conversation ID (use conversationId field, not MongoDB _id)
CONV_ID=$(echo "$RESPONSE_BODY" | jq -r '.conversationId // .conversation_id // .id' 2>/dev/null)

if [ -z "$CONV_ID" ] || [ "$CONV_ID" == "null" ]; then
  echo -e "${RED}❌ FAILED: Could not extract conversation ID from response${NC}"
  exit 1
fi

echo -e "${GREEN}✓ Group conversation created successfully!${NC}"
echo -e "${GREEN}  Conversation ID: $CONV_ID${NC}"
echo ""

#############################################
# Step 2: Send Message to Group
#############################################
echo -e "${YELLOW}Step 2: Sending message to group...${NC}"

MESSAGE_PAYLOAD=$(cat <<EOF
{
  "conversationId": "$CONV_ID",
  "senderId": "admin-user",
  "content": "Alerta: Teste de Fan-out para múltiplos canais!",
  "channel": "INTERNAL"
}
EOF
)

echo "Payload:"
echo "$MESSAGE_PAYLOAD" | jq '.'
echo ""

MESSAGE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d "$MESSAGE_PAYLOAD" \
  "$MESSAGE_API")

# Extract HTTP status code (last line)
HTTP_STATUS=$(echo "$MESSAGE_RESPONSE" | tail -n1)
# Extract response body (all but last line)
RESPONSE_BODY=$(echo "$MESSAGE_RESPONSE" | sed '$d')

echo "Response Status: $HTTP_STATUS"
echo "Response Body:"
echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"
echo ""

# Validate HTTP 202 Accepted
if [ "$HTTP_STATUS" != "202" ]; then
  echo -e "${RED}❌ FAILED: Expected HTTP 202, got $HTTP_STATUS${NC}"
  exit 1
fi

echo -e "${GREEN}✓ Message sent successfully!${NC}"
echo ""

#############################################
# Step 3: Manual Log Validation Instructions
#############################################
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Step 3: Log Validation (Manual)${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}⚠ Please verify the following in router-service logs:${NC}"
echo ""
echo -e "Expected log entries indicating multi-recipient delivery:"
echo ""
echo -e "  ${GREEN}1.${NC} Look for: ${BLUE}'Routing to [whatsapp:551199999999] via WHATSAPP'${NC}"
echo -e "  ${GREEN}2.${NC} Look for: ${BLUE}'Routing to [telegram:123456789] via TELEGRAM'${NC}"
echo ""
echo -e "Commands to check logs:"
echo ""
echo -e "  ${YELLOW}# If running with docker-compose:${NC}"
echo -e "  docker-compose logs -f router-service | grep -E 'Routing to|recipient'"
echo ""
echo -e "  ${YELLOW}# If running locally:${NC}"
echo -e "  tail -f services/router-service/logs/application.log | grep -E 'Routing to|recipient'"
echo ""
echo -e "  ${YELLOW}# Check for fan-out delivery:${NC}"
echo -e "  docker-compose logs router-service | grep -A 5 'routeMultiRecipientMessage'"
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✓ Test script completed successfully!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "Summary:"
echo -e "  • Conversation ID: ${GREEN}$CONV_ID${NC}"
echo -e "  • Participants: 3 (admin-user, whatsapp:551199999999, telegram:123456789)"
echo -e "  • Message sent with fan-out routing"
echo ""
