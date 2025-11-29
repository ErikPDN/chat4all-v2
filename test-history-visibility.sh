#!/bin/bash
#
# Task T080 Test Script: History Visibility Based on Join Date
# 
# Tests:
# - New participants only see messages sent AFTER their join date
# - Original participants see full message history
# - Join date filtering only applies to GROUP conversations
#
# Prerequisites:
# - message-service running on http://localhost:8081
# - MongoDB accessible
#
# Author: Chat4All Team
# Date: 2025-11-28

set -e  # Exit on error

BASE_URL="http://localhost:8081/api/v1"
TIMESTAMP=$(date +%s)

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}Task T080: History Visibility Test${NC}"
echo -e "${BLUE}=====================================${NC}"
echo

# Step 1: Create a GROUP conversation with Admin only
echo -e "${YELLOW}[Step 1] Creating GROUP conversation with Admin and User2...${NC}"
GROUP_RESPONSE=$(curl -s -X POST "${BASE_URL}/conversations" \
  -H "Content-Type: application/json" \
  -d "{
    \"type\": \"GROUP\",
    \"participants\": [\"Admin\", \"User2\"],
    \"title\": \"History Test Group ${TIMESTAMP}\",
    \"primaryChannel\": \"WHATSAPP\"
  }")

CONVERSATION_ID=$(echo "$GROUP_RESPONSE" | jq -r '.conversationId // .id // empty')

if [ -z "$CONVERSATION_ID" ]; then
  echo -e "${RED}✗ Failed to create group conversation${NC}"
  echo "Response: $GROUP_RESPONSE"
  exit 1
fi

echo -e "${GREEN}✓ Group created successfully${NC}"
echo "  Conversation ID: $CONVERSATION_ID"
echo "  Initial participants: Admin, User2"
echo

# Step 2: Admin sends first message (this should be "secret" from UserB)
echo -e "${YELLOW}[Step 2] Admin sends 'Segredo' message...${NC}"
MSG1_RESPONSE=$(curl -s -X POST "${BASE_URL}/messages" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"${CONVERSATION_ID}\",
    \"senderId\": \"Admin\",
    \"content\": \"Segredo - Esta mensagem é anterior à entrada do UserB\",
    \"channel\": \"WHATSAPP\"
  }")

MSG1_ID=$(echo "$MSG1_RESPONSE" | jq -r '.messageId // empty')

if [ -z "$MSG1_ID" ]; then
  echo -e "${RED}✗ Failed to send first message${NC}"
  echo "Response: $MSG1_RESPONSE"
  exit 1
fi

echo -e "${GREEN}✓ Message 1 sent successfully${NC}"
echo "  Message ID: $MSG1_ID"
echo "  Content: 'Segredo - Esta mensagem é anterior à entrada do UserB'"
echo

# Step 3: Wait 2 seconds to ensure clear timestamp separation
echo -e "${YELLOW}[Step 3] Waiting 2 seconds to separate message timestamps...${NC}"
sleep 2
echo -e "${GREEN}✓ Wait complete${NC}"
echo

# Step 4: Add UserB to the group (join point)
echo -e "${YELLOW}[Step 4] Adding UserB to the group (JOIN POINT)...${NC}"
ADD_RESPONSE=$(curl -s -X POST "${BASE_URL}/conversations/${CONVERSATION_ID}/participants" \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"UserB\"
  }")

PARTICIPANTS_AFTER_ADD=$(echo "$ADD_RESPONSE" | jq -r '.participants | length')

if [ "$PARTICIPANTS_AFTER_ADD" = "3" ]; then
  echo -e "${GREEN}✓ UserB added successfully${NC}"
  echo "  Total participants: $PARTICIPANTS_AFTER_ADD"
  echo "  Join timestamp recorded for UserB"
else
  echo -e "${RED}✗ Failed to add UserB${NC}"
  echo "Response: $ADD_RESPONSE"
  exit 1
fi
echo

# Step 5: UserB sends second message
echo -e "${YELLOW}[Step 5] UserB sends 'Ola' message...${NC}"
sleep 1  # Small delay to ensure different timestamp
MSG2_RESPONSE=$(curl -s -X POST "${BASE_URL}/messages" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"${CONVERSATION_ID}\",
    \"senderId\": \"UserB\",
    \"content\": \"Ola! Acabei de entrar no grupo\",
    \"channel\": \"WHATSAPP\"
  }")

MSG2_ID=$(echo "$MSG2_RESPONSE" | jq -r '.messageId // empty')

if [ -z "$MSG2_ID" ]; then
  echo -e "${RED}✗ Failed to send second message${NC}"
  echo "Response: $MSG2_RESPONSE"
  exit 1
fi

echo -e "${GREEN}✓ Message 2 sent successfully${NC}"
echo "  Message ID: $MSG2_ID"
echo "  Content: 'Ola! Acabei de entrar no grupo'"
echo

# Step 6: Wait for messages to be persisted
echo -e "${YELLOW}[Step 6] Waiting for messages to be persisted...${NC}"
sleep 2
echo -e "${GREEN}✓ Wait complete${NC}"
echo

# Step 7: UserB requests message history (should only see Msg 2)
echo -e "${YELLOW}[Step 7] UserB requests message history (should see ONLY Msg 2)...${NC}"
USERB_HISTORY=$(curl -s "${BASE_URL}/conversations/${CONVERSATION_ID}/messages?userId=UserB&limit=100")

USERB_MSG_COUNT=$(echo "$USERB_HISTORY" | jq -r '.count // 0')
USERB_MESSAGES=$(echo "$USERB_HISTORY" | jq -r '.messages[].content')

echo -e "${BLUE}UserB's History:${NC}"
echo "  Message count: $USERB_MSG_COUNT"
echo "  Messages:"
echo "$USERB_MESSAGES" | while read -r msg; do
  echo "    - $msg"
done
echo

# Validate UserB sees only Msg 2
HAS_SEGREDO=$(echo "$USERB_MESSAGES" | grep -i "segredo" || echo "")
HAS_OLA=$(echo "$USERB_MESSAGES" | grep -i "ola" || echo "")

if [ -z "$HAS_SEGREDO" ] && [ -n "$HAS_OLA" ]; then
  echo -e "${GREEN}✓ PASS: UserB correctly sees ONLY messages after join${NC}"
  echo "  - Does NOT see 'Segredo' message (sent before join)"
  echo "  - DOES see 'Ola' message (sent after join)"
else
  echo -e "${RED}✗ FAIL: UserB history filtering incorrect${NC}"
  if [ -n "$HAS_SEGREDO" ]; then
    echo "  - ERROR: UserB should NOT see 'Segredo' message"
  fi
  if [ -z "$HAS_OLA" ]; then
    echo "  - ERROR: UserB should see 'Ola' message"
  fi
fi
echo

# Step 8: Admin requests message history (should see BOTH messages)
echo -e "${YELLOW}[Step 8] Admin requests message history (should see BOTH messages)...${NC}"
ADMIN_HISTORY=$(curl -s "${BASE_URL}/conversations/${CONVERSATION_ID}/messages?userId=Admin&limit=100")

ADMIN_MSG_COUNT=$(echo "$ADMIN_HISTORY" | jq -r '.count // 0')
ADMIN_MESSAGES=$(echo "$ADMIN_HISTORY" | jq -r '.messages[].content')

echo -e "${BLUE}Admin's History:${NC}"
echo "  Message count: $ADMIN_MSG_COUNT"
echo "  Messages:"
echo "$ADMIN_MESSAGES" | while read -r msg; do
  echo "    - $msg"
done
echo

# Validate Admin sees both messages
ADMIN_HAS_SEGREDO=$(echo "$ADMIN_MESSAGES" | grep -i "segredo" || echo "")
ADMIN_HAS_OLA=$(echo "$ADMIN_MESSAGES" | grep -i "ola" || echo "")

if [ -n "$ADMIN_HAS_SEGREDO" ] && [ -n "$ADMIN_HAS_OLA" ]; then
  echo -e "${GREEN}✓ PASS: Admin correctly sees ALL messages${NC}"
  echo "  - DOES see 'Segredo' message (original participant)"
  echo "  - DOES see 'Ola' message"
else
  echo -e "${RED}✗ FAIL: Admin history incomplete${NC}"
  if [ -z "$ADMIN_HAS_SEGREDO" ]; then
    echo "  - ERROR: Admin should see 'Segredo' message"
  fi
  if [ -z "$ADMIN_HAS_OLA" ]; then
    echo "  - ERROR: Admin should see 'Ola' message"
  fi
fi
echo

# Summary
echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}=====================================${NC}"
echo -e "Conversation ID: ${CONVERSATION_ID}"
echo
echo -e "${GREEN}Expected Behavior:${NC}"
echo "  - UserB (joined later): sees ONLY Msg 2 (after join)"
echo "  - Admin (original): sees Msg 1 AND Msg 2 (full history)"
echo
echo -e "${BLUE}Actual Results:${NC}"
echo "  - UserB message count: $USERB_MSG_COUNT"
echo "  - Admin message count: $ADMIN_MSG_COUNT"
echo

if [ -z "$HAS_SEGREDO" ] && [ -n "$HAS_OLA" ] && [ -n "$ADMIN_HAS_SEGREDO" ] && [ -n "$ADMIN_HAS_OLA" ]; then
  echo -e "${GREEN}✅ ALL TESTS PASSED - Task T080 Working Correctly${NC}"
  exit 0
else
  echo -e "${RED}❌ SOME TESTS FAILED - Review Implementation${NC}"
  exit 1
fi
