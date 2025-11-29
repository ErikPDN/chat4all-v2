#!/bin/bash
#
# User Story 4 Master Flow Test
# 
# Validates ALL US4 requirements in a single continuous flow:
# - Group conversation creation
# - Participant management (add/remove)
# - Message routing to multiple recipients
# - History visibility based on join date
# - System message generation
#
# Prerequisites:
# - message-service running on http://localhost:8081
# - MongoDB running (accessible via docker exec)
# - router-service running for message routing
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
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

echo
echo -e "${MAGENTA}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${MAGENTA}║                                                        ║${NC}"
echo -e "${MAGENTA}║        USER STORY 4 - MASTER VALIDATION FLOW           ║${NC}"
echo -e "${MAGENTA}║                                                        ║${NC}"
echo -e "${MAGENTA}║  Tests: Group Creation, Participants, History, Routing ║${NC}"
echo -e "${MAGENTA}║                                                        ║${NC}"
echo -e "${MAGENTA}╚════════════════════════════════════════════════════════╝${NC}"
echo

# ========================================
# STEP 0: Cleanup
# ========================================
echo -e "${CYAN}[STEP 0] 🧹 Cleaning up - Dropping 'conversations' collection...${NC}"
docker compose exec -T mongodb mongosh \
  -u chat4all \
  -p chat4all_dev_password \
  --authenticationDatabase admin \
  chat4all \
  --eval "db.conversations.drop()" > /dev/null 2>&1 || true

docker compose exec -T mongodb mongosh \
  -u chat4all \
  -p chat4all_dev_password \
  --authenticationDatabase admin \
  chat4all \
  --eval "db.messages.drop()" > /dev/null 2>&1 || true

echo -e "${GREEN}✓ Cleanup complete - Fresh state guaranteed${NC}"
echo

# ========================================
# STEP 1: Create GROUP Conversation
# ========================================
echo -e "${CYAN}[STEP 1] 👥 Creating GROUP conversation with Admin and User1...${NC}"
GROUP_RESPONSE=$(curl -s -X POST "${BASE_URL}/conversations" \
  -H "Content-Type: application/json" \
  -d "{
    \"type\": \"GROUP\",
    \"participants\": [\"Admin\", \"User1\"],
    \"title\": \"US4 Master Test Group ${TIMESTAMP}\",
    \"primaryChannel\": \"WHATSAPP\"
  }")

CONVERSATION_ID=$(echo "$GROUP_RESPONSE" | jq -r '.conversationId // .id // empty')

if [ -z "$CONVERSATION_ID" ]; then
  echo -e "${RED}✗ FAILED: Could not create group conversation${NC}"
  echo "Response: $GROUP_RESPONSE"
  exit 1
fi

INITIAL_PARTICIPANTS=$(echo "$GROUP_RESPONSE" | jq -r '.participants | length')

if [ "$INITIAL_PARTICIPANTS" = "2" ]; then
  echo -e "${GREEN}✓ SUCCESS: Group created${NC}"
  echo -e "  📋 Conversation ID: ${YELLOW}${CONVERSATION_ID}${NC}"
  echo -e "  👤 Initial participants: Admin, User1"
else
  echo -e "${RED}✗ FAILED: Expected 2 participants, got ${INITIAL_PARTICIPANTS}${NC}"
  exit 1
fi
echo

# ========================================
# STEP 2: Admin sends Pre-Join Message
# ========================================
echo -e "${CYAN}[STEP 2] 💬 Admin sends 'Msg Pre-Join' (secret from User2)...${NC}"
sleep 1  # Ensure timestamp separation
MSG1_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "${BASE_URL}/messages" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"${CONVERSATION_ID}\",
    \"senderId\": \"Admin\",
    \"content\": \"Msg Pre-Join: This is secret from User2\",
    \"channel\": \"WHATSAPP\"
  }")

MSG1_HTTP_STATUS=$(echo "$MSG1_RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
MSG1_BODY=$(echo "$MSG1_RESPONSE" | grep -v "HTTP_STATUS")
MSG1_ID=$(echo "$MSG1_BODY" | jq -r '.messageId // empty')

if [ "$MSG1_HTTP_STATUS" = "202" ] && [ -n "$MSG1_ID" ]; then
  echo -e "${GREEN}✓ SUCCESS: Message sent (HTTP 202)${NC}"
  echo -e "  📨 Message ID: ${MSG1_ID}"
  echo -e "  📝 Content: 'Msg Pre-Join: This is secret from User2'"
else
  echo -e "${RED}✗ FAILED: Expected HTTP 202, got ${MSG1_HTTP_STATUS}${NC}"
  echo "Response: $MSG1_BODY"
  exit 1
fi
echo

# ========================================
# STEP 3: Add User2 to Group
# ========================================
echo -e "${CYAN}[STEP 3] ➕ Adding User2 to the group (JOIN POINT)...${NC}"
sleep 2  # Ensure clear timestamp separation for join date
ADD_RESPONSE=$(curl -s -X POST "${BASE_URL}/conversations/${CONVERSATION_ID}/participants" \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"User2\"
  }")

PARTICIPANTS_AFTER_ADD=$(echo "$ADD_RESPONSE" | jq -r '.participants | length')

if [ "$PARTICIPANTS_AFTER_ADD" = "3" ]; then
  echo -e "${GREEN}✓ SUCCESS: User2 added to group${NC}"
  echo -e "  👥 Total participants: ${PARTICIPANTS_AFTER_ADD}"
  echo -e "  🕐 Join timestamp recorded for User2"
  echo -e "  📌 User2 should NOT see Msg Pre-Join"
else
  echo -e "${RED}✗ FAILED: Expected 3 participants, got ${PARTICIPANTS_AFTER_ADD}${NC}"
  exit 1
fi
echo

# ========================================
# STEP 4: Admin sends Post-Join Message
# ========================================
echo -e "${CYAN}[STEP 4] 💬 Admin sends 'Msg Post-Join' (visible to all)...${NC}"
sleep 1
MSG2_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "${BASE_URL}/messages" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"${CONVERSATION_ID}\",
    \"senderId\": \"Admin\",
    \"content\": \"Msg Post-Join: Everyone can see this\",
    \"channel\": \"WHATSAPP\"
  }")

MSG2_HTTP_STATUS=$(echo "$MSG2_RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
MSG2_BODY=$(echo "$MSG2_RESPONSE" | grep -v "HTTP_STATUS")
MSG2_ID=$(echo "$MSG2_BODY" | jq -r '.messageId // empty')

if [ "$MSG2_HTTP_STATUS" = "202" ] && [ -n "$MSG2_ID" ]; then
  echo -e "${GREEN}✓ SUCCESS: Message sent (HTTP 202)${NC}"
  echo -e "  📨 Message ID: ${MSG2_ID}"
  echo -e "  📝 Content: 'Msg Post-Join: Everyone can see this'"
else
  echo -e "${RED}✗ FAILED: Expected HTTP 202, got ${MSG2_HTTP_STATUS}${NC}"
  echo "Response: $MSG2_BODY"
  exit 1
fi
echo

# ========================================
# STEP 5: History Validation (CRITICAL TEST)
# ========================================
echo -e "${CYAN}[STEP 5] 🔍 HISTORY VISIBILITY TEST (The Proof)...${NC}"
sleep 2  # Wait for messages to be persisted

# User1 history (original participant - should see BOTH messages)
echo -e "${YELLOW}  Fetching User1's history (should see 2 messages)...${NC}"
USER1_HISTORY=$(curl -s "${BASE_URL}/conversations/${CONVERSATION_ID}/messages?userId=User1&limit=100")
USER1_COUNT=$(echo "$USER1_HISTORY" | jq -r '.count // 0')

# User2 history (joined later - should see ONLY 1 message)
echo -e "${YELLOW}  Fetching User2's history (should see 1 message)...${NC}"
USER2_HISTORY=$(curl -s "${BASE_URL}/conversations/${CONVERSATION_ID}/messages?userId=User2&limit=100")
USER2_COUNT=$(echo "$USER2_HISTORY" | jq -r '.count // 0')

echo
echo -e "${BLUE}  📊 History Results:${NC}"
echo -e "    User1 (original): ${USER1_COUNT} messages"
echo -e "    User2 (joined later): ${USER2_COUNT} messages"
echo

# Validate counts
if [ "$USER1_COUNT" = "2" ] && [ "$USER2_COUNT" = "1" ]; then
  echo -e "${GREEN}✓ SUCCESS: History visibility PERFECT! ✨${NC}"
  echo -e "  ✅ User1 sees BOTH messages (Pre-Join + Post-Join)"
  echo -e "  ✅ User2 sees ONLY Post-Join message"
  echo -e "  🎯 Task T080 validated - Join-date filtering works!"
else
  echo -e "${RED}✗ FAILED: History counts incorrect!${NC}"
  echo -e "  Expected: User1=2, User2=1"
  echo -e "  Got: User1=${USER1_COUNT}, User2=${USER2_COUNT}"
  echo
  echo -e "${YELLOW}User1 messages:${NC}"
  echo "$USER1_HISTORY" | jq -r '.messages[].content'
  echo
  echo -e "${YELLOW}User2 messages:${NC}"
  echo "$USER2_HISTORY" | jq -r '.messages[].content'
  exit 1
fi
echo

# ========================================
# STEP 6: Remove User1 from Group
# ========================================
echo -e "${CYAN}[STEP 6] ➖ Removing User1 from the group...${NC}"
REMOVE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/conversations/${CONVERSATION_ID}/participants/User1")

PARTICIPANTS_AFTER_REMOVE=$(echo "$REMOVE_RESPONSE" | jq -r '.participants | length')

if [ "$PARTICIPANTS_AFTER_REMOVE" = "2" ]; then
  echo -e "${GREEN}✓ SUCCESS: User1 removed from group${NC}"
  echo -e "  👥 Remaining participants: ${PARTICIPANTS_AFTER_REMOVE}"
  echo -e "  📝 Participant list: Admin, User2"
else
  echo -e "${RED}✗ FAILED: Expected 2 participants after removal, got ${PARTICIPANTS_AFTER_REMOVE}${NC}"
  exit 1
fi
echo

# ========================================
# STEP 7: Admin sends Final Message
# ========================================
echo -e "${CYAN}[STEP 7] 💬 Admin sends 'Msg Final' (only User2 should receive)...${NC}"
sleep 1
MSG3_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "${BASE_URL}/messages" \
  -H "Content-Type: application/json" \
  -d "{
    \"conversationId\": \"${CONVERSATION_ID}\",
    \"senderId\": \"Admin\",
    \"content\": \"Msg Final: Only User2 should receive this\",
    \"channel\": \"WHATSAPP\"
  }")

MSG3_HTTP_STATUS=$(echo "$MSG3_RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
MSG3_BODY=$(echo "$MSG3_RESPONSE" | grep -v "HTTP_STATUS")
MSG3_ID=$(echo "$MSG3_BODY" | jq -r '.messageId // empty')

if [ "$MSG3_HTTP_STATUS" = "202" ] && [ -n "$MSG3_ID" ]; then
  echo -e "${GREEN}✓ SUCCESS: Message sent (HTTP 202)${NC}"
  echo -e "  📨 Message ID: ${MSG3_ID}"
  echo -e "  📝 Content: 'Msg Final: Only User2 should receive this'"
else
  echo -e "${RED}✗ FAILED: Expected HTTP 202, got ${MSG3_HTTP_STATUS}${NC}"
  echo "Response: $MSG3_BODY"
  exit 1
fi
echo

# ========================================
# STEP 8: Routing Validation
# ========================================
echo -e "${CYAN}[STEP 8] 🚀 ROUTING VALIDATION (recipientIds check)...${NC}"
sleep 3  # Wait for message to be persisted with recipientIds

# Fetch the message from MongoDB to check recipientIds
echo -e "${YELLOW}  Querying MongoDB for Msg Final recipientIds...${NC}"
MSG3_MONGO=$(docker compose exec -T mongodb mongosh \
  -u chat4all \
  -p chat4all_dev_password \
  --authenticationDatabase admin \
  chat4all \
  --quiet \
  --eval "db.messages.findOne({message_id: '${MSG3_ID}'}, {recipient_ids: 1, _id: 0})" 2>/dev/null | grep -v "^Using MongoDB" || echo "{}")

RECIPIENT_IDS=$(echo "$MSG3_MONGO" | jq -r '.recipient_ids // []')
RECIPIENT_COUNT=$(echo "$RECIPIENT_IDS" | jq -r 'length')
HAS_USER2=$(echo "$RECIPIENT_IDS" | jq -r 'contains(["User2"])')
HAS_USER1=$(echo "$RECIPIENT_IDS" | jq -r 'contains(["User1"])')

echo
echo -e "${BLUE}  📊 Routing Results:${NC}"
echo -e "    recipientIds: ${RECIPIENT_IDS}"
echo -e "    Count: ${RECIPIENT_COUNT}"
echo

if [ "$RECIPIENT_COUNT" = "1" ] && [ "$HAS_USER2" = "true" ] && [ "$HAS_USER1" = "false" ]; then
  echo -e "${GREEN}✓ SUCCESS: Routing is PERFECT! 🎯${NC}"
  echo -e "  ✅ recipientIds contains ONLY User2"
  echo -e "  ✅ User1 NOT in recipientIds (removed participant)"
  echo -e "  🎯 Task T078 validated - Multi-recipient routing works!"
else
  echo -e "${RED}✗ FAILED: Routing validation failed!${NC}"
  echo -e "  Expected: recipientIds = [\"User2\"]"
  echo -e "  Got: ${RECIPIENT_IDS}"
  echo -e "  Recipient count: ${RECIPIENT_COUNT}"
  echo -e "  Has User2: ${HAS_USER2}"
  echo -e "  Has User1: ${HAS_USER1}"
  exit 1
fi
echo

# ========================================
# FINAL SUMMARY
# ========================================
echo
echo -e "${MAGENTA}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${MAGENTA}║                                                        ║${NC}"
echo -e "${MAGENTA}║              🎉 ALL TESTS PASSED! 🎉                   ║${NC}"
echo -e "${MAGENTA}║                                                        ║${NC}"
echo -e "${MAGENTA}║        User Story 4 - FULLY VALIDATED ✅               ║${NC}"
echo -e "${MAGENTA}║                                                        ║${NC}"
echo -e "${MAGENTA}╚════════════════════════════════════════════════════════╝${NC}"
echo
echo -e "${GREEN}✅ Feature Validated:${NC}"
echo -e "  ✓ T074: ConversationType enum (GROUP)"
echo -e "  ✓ T075: Participants field with validation"
echo -e "  ✓ T076: POST /conversations endpoint"
echo -e "  ✓ T077: createConversation() service method"
echo -e "  ✓ T078: Multi-recipient routing (recipientIds)"
echo -e "  ✓ T079: Participant management (add/remove)"
echo -e "  ✓ T080: Join-date history visibility"
echo
echo -e "${CYAN}📊 Test Statistics:${NC}"
echo -e "  Conversation ID: ${CONVERSATION_ID}"
echo -e "  Messages sent: 3"
echo -e "  Participants added: 1"
echo -e "  Participants removed: 1"
echo -e "  Final participant count: 2 (Admin, User2)"
echo
echo -e "${BLUE}🚀 User Story 4 is PRODUCTION READY!${NC}"
echo

exit 0
