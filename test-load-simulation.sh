#!/usr/bin/env bash

# ============================================================================
# Load Simulation Test Script for Chat4All v2
# ============================================================================
# Purpose: Simulate concurrent traffic and validate asynchronous message flow
# Tests: Group messaging under load with concurrent requests
# ============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
MESSAGE_SERVICE_URL="http://localhost:8081"
USER_SERVICE_URL="http://localhost:8083"
COUNT=100  # Number of messages to send

echo -e "${CYAN}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║         Chat4All v2 - Load Simulation Test                      ║${NC}"
echo -e "${CYAN}║         Concurrent Message Delivery Test                        ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ============================================================================
# SECTION 1: SETUP - Create Group with 3 Participants
# ============================================================================

echo -e "${BLUE}[1/4] SETUP: Creating test group with 3 participants...${NC}"
echo ""

# Create Admin User
echo -e "${YELLOW}Creating Admin user...${NC}"
ADMIN_RESPONSE=$(curl -s -X POST ${USER_SERVICE_URL}/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Load Test Admin",
    "userType": "AGENT"
  }')

ADMIN_ID=$(echo "$ADMIN_RESPONSE" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo -e "${GREEN}✓ Admin created: ${ADMIN_ID}${NC}"

# Create WhatsApp User
echo -e "${YELLOW}Creating WhatsApp user...${NC}"
WHATSAPP_RESPONSE=$(curl -s -X POST ${USER_SERVICE_URL}/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "WhatsApp Load Test User",
    "userType": "CUSTOMER"
  }')

WHATSAPP_ID=$(echo "$WHATSAPP_RESPONSE" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo -e "${GREEN}✓ WhatsApp user created: ${WHATSAPP_ID}${NC}"

# Link WhatsApp identity
curl -s -X POST ${USER_SERVICE_URL}/api/v1/users/${WHATSAPP_ID}/identities \
  -H "Content-Type: application/json" \
  -d '{
    "platform": "WHATSAPP",
    "platformUserId": "whatsapp_load_test_001"
  }' > /dev/null
echo -e "${GREEN}✓ WhatsApp identity linked${NC}"

# Create Telegram User
echo -e "${YELLOW}Creating Telegram user...${NC}"
TELEGRAM_RESPONSE=$(curl -s -X POST ${USER_SERVICE_URL}/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Telegram Load Test User",
    "userType": "CUSTOMER"
  }')

TELEGRAM_ID=$(echo "$TELEGRAM_RESPONSE" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo -e "${GREEN}✓ Telegram user created: ${TELEGRAM_ID}${NC}"

# Link Telegram identity
curl -s -X POST ${USER_SERVICE_URL}/api/v1/users/${TELEGRAM_ID}/identities \
  -H "Content-Type: application/json" \
  -d '{
    "platform": "TELEGRAM",
    "platformUserId": "telegram_load_test_001"
  }' > /dev/null
echo -e "${GREEN}✓ Telegram identity linked${NC}"

# Create Group Conversation
echo ""
echo -e "${YELLOW}Creating group conversation...${NC}"
GROUP_RESPONSE=$(curl -s -X POST ${MESSAGE_SERVICE_URL}/api/v1/conversations \
  -H "Content-Type: application/json" \
  -d "{
    \"type\": \"GROUP\",
    \"participants\": [
      \"${ADMIN_ID}\",
      \"${WHATSAPP_ID}\",
      \"${TELEGRAM_ID}\"
    ]
  }")

GROUP_ID=$(echo "$GROUP_RESPONSE" | grep -o '"conversationId":"[^"]*' | cut -d'"' -f4)
echo -e "${GREEN}✓ Group created: ${GROUP_ID}${NC}"
echo -e "${GREEN}✓ Participants: Admin, WhatsApp User, Telegram User${NC}"
echo ""

# ============================================================================
# SECTION 2: BURST LOAD - Send 100 Concurrent Messages
# ============================================================================

echo -e "${BLUE}[2/4] BURST LOAD: Sending ${COUNT} concurrent messages...${NC}"
echo ""

# Create temp directory for tracking responses
TEMP_DIR="/tmp/chat4all-load-test-$$"
mkdir -p "$TEMP_DIR"

# Capture start time
START_TIME=$(date +%s.%N)

echo -e "${YELLOW}Starting concurrent message burst...${NC}"
echo -e "${CYAN}Messages will be sent in background (asynchronous)${NC}"

# Send messages concurrently in background
for i in $(seq 1 $COUNT); do
  # Send message in background
  (
    RESPONSE=$(curl -s -X POST ${MESSAGE_SERVICE_URL}/api/messages \
      -H "Content-Type: application/json" \
      -d "{
        \"conversationId\": \"${GROUP_ID}\",
        \"senderId\": \"${ADMIN_ID}\",
        \"content\": \"Load Test Message #${i}\",
        \"channel\": \"INTERNAL\"
      }")
    
    # Save response to temp file
    echo "$RESPONSE" > "${TEMP_DIR}/response_${i}.json"
    
    # Extract message ID for tracking
    MESSAGE_ID=$(echo "$RESPONSE" | grep -o '"messageId":"[^"]*' | cut -d'"' -f4)
    if [ -n "$MESSAGE_ID" ]; then
      echo "${i}:${MESSAGE_ID}" >> "${TEMP_DIR}/message_ids.txt"
    fi
  ) &
  
  # Progress indicator
  if [ $((i % 10)) -eq 0 ]; then
    echo -e "${CYAN}  ► ${i}/${COUNT} requests dispatched...${NC}"
  fi
done

echo ""
echo -e "${YELLOW}All ${COUNT} requests dispatched. Waiting for completion...${NC}"

# Wait for all background jobs to complete
wait

# Capture end time
END_TIME=$(date +%s.%N)

# Calculate duration (using awk instead of bc)
DURATION=$(echo "$END_TIME $START_TIME" | awk '{printf "%.2f", $1 - $2}')

echo ""
echo -e "${GREEN}✓ All ${COUNT} requests completed!${NC}"
echo -e "${GREEN}✓ Total client-side time: ${DURATION} seconds${NC}"

# Calculate requests per second
RPS=$(echo "$COUNT $DURATION" | awk '{printf "%.2f", $1 / $2}')
echo -e "${GREEN}✓ Average throughput: ${RPS} requests/second${NC}"

# ============================================================================
# SECTION 3: MONITORING - Analyze Results
# ============================================================================

echo ""
echo -e "${BLUE}[3/4] MONITORING: Analyzing responses...${NC}"
echo ""

# Count successful responses
SUCCESS_COUNT=$(grep -l '"messageId"' ${TEMP_DIR}/response_*.json 2>/dev/null | wc -l)
TOTAL_RESPONSES=$(ls ${TEMP_DIR}/response_*.json 2>/dev/null | wc -l)

echo -e "${YELLOW}Response Summary:${NC}"
echo -e "  Total requests sent:     ${COUNT}"
echo -e "  Total responses received: ${TOTAL_RESPONSES}"
echo -e "  Successful (HTTP 202):    ${SUCCESS_COUNT}"

if [ "$SUCCESS_COUNT" -eq "$COUNT" ]; then
  echo -e "${GREEN}✓ 100% success rate!${NC}"
else
  FAILURE_COUNT=$((COUNT - SUCCESS_COUNT))
  echo -e "${RED}✗ ${FAILURE_COUNT} failures detected${NC}"
  
  # Show sample failures
  echo ""
  echo -e "${YELLOW}Sample failed responses:${NC}"
  for file in ${TEMP_DIR}/response_*.json; do
    if ! grep -q '"messageId"' "$file" 2>/dev/null; then
      echo -e "${RED}  $(basename $file): $(cat $file)${NC}"
      break
    fi
  done
fi

# Count unique message IDs
if [ -f "${TEMP_DIR}/message_ids.txt" ]; then
  UNIQUE_MESSAGES=$(sort "${TEMP_DIR}/message_ids.txt" | uniq | wc -l)
  echo ""
  echo -e "${YELLOW}Message Tracking:${NC}"
  echo -e "  Unique message IDs created: ${UNIQUE_MESSAGES}"
  
  if [ "$UNIQUE_MESSAGES" -eq "$COUNT" ]; then
    echo -e "${GREEN}✓ No duplicate message IDs detected${NC}"
  else
    echo -e "${RED}✗ Warning: Duplicate message IDs found${NC}"
  fi
fi

# ============================================================================
# SECTION 4: LOG ANALYSIS INSTRUCTIONS
# ============================================================================

echo ""
echo -e "${BLUE}[4/4] LOG ANALYSIS: Instructions for manual verification${NC}"
echo ""

echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Router Service Logs - Message Routing Verification${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo ""

echo -e "${YELLOW}1. Count total routing events:${NC}"
echo -e "${GREEN}   docker logs chat4all-router-service --tail 500 | grep 'Routing to' | wc -l${NC}"
echo ""
echo -e "   Expected: ~${COUNT} routing events (1 per message)"
echo ""

echo -e "${YELLOW}2. Count successful deliveries:${NC}"
echo -e "${GREEN}   docker logs chat4all-router-service --tail 500 | grep 'successfully' | wc -l${NC}"
echo ""

echo -e "${YELLOW}3. Check for errors in JSON logs:${NC}"
echo -e "${GREEN}   docker logs chat4all-router-service --tail 500 | grep '^{' | jq 'select(.level==\"ERROR\")'${NC}"
echo ""

echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Message Service Logs - Message Persistence Verification${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo ""

echo -e "${YELLOW}4. Count messages accepted:${NC}"
echo -e "${GREEN}   docker logs chat4all-message-service --tail 500 | grep 'Message accepted' | wc -l${NC}"
echo ""
echo -e "   Expected: ${COUNT} messages"
echo ""

echo -e "${YELLOW}5. Check Kafka events published:${NC}"
echo -e "${GREEN}   docker logs chat4all-message-service --tail 500 | grep 'Published to Kafka' | wc -l${NC}"
echo ""

echo -e "${YELLOW}6. Filter errors with jq:${NC}"
echo -e "${GREEN}   docker logs chat4all-message-service --tail 500 | grep '^{' | jq -r 'select(.level==\"ERROR\") | \"[\(.timestamp)] \(.message)\"'${NC}"
echo ""

echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  MongoDB - Database Verification${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo ""

echo -e "${YELLOW}7. Verify messages in MongoDB:${NC}"
echo -e "${GREEN}   docker exec chat4all-mongodb mongosh chat4all --quiet --eval 'db.messages.countDocuments({conversationId: \"${GROUP_ID}\"})'${NC}"
echo ""
echo -e "   Expected: ${COUNT} messages"
echo ""

echo -e "${YELLOW}8. Check conversation participants:${NC}"
echo -e "${GREEN}   docker exec chat4all-mongodb mongosh chat4all --quiet --eval 'db.conversations.findOne({_id: \"${GROUP_ID}\"})'${NC}"
echo ""

echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Advanced Analysis${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo ""

echo -e "${YELLOW}9. Message latency distribution (JSON logs):${NC}"
echo -e "${GREEN}   docker logs chat4all-message-service --tail 500 | grep '^{' | jq -r 'select(.message | contains(\"latency\")) | .message' | sort -n${NC}"
echo ""

echo -e "${YELLOW}10. Concurrent processing verification:${NC}"
echo -e "${GREEN}   docker logs chat4all-router-service --tail 500 | grep '^{' | jq -r '\"[\(.timestamp)] \(.thread) - \(.message)\"' | head -20${NC}"
echo ""
echo -e "   Look for different thread names processing simultaneously"
echo ""

echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Idempotency & Deduplication Tests${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo ""

echo -e "${YELLOW}11. Test duplicate message handling (send same message twice):${NC}"
echo -e "${GREEN}   MESSAGE_ID=\$(uuidgen); \\
   curl -X POST ${MESSAGE_SERVICE_URL}/api/messages -H 'Content-Type: application/json' -d '{\"messageId\":\"\$MESSAGE_ID\",\"conversationId\":\"${GROUP_ID}\",\"senderId\":\"${ADMIN_ID}\",\"content\":\"Duplicate test\",\"channel\":\"INTERNAL\"}'; \\
   curl -X POST ${MESSAGE_SERVICE_URL}/api/messages -H 'Content-Type: application/json' -d '{\"messageId\":\"\$MESSAGE_ID\",\"conversationId\":\"${GROUP_ID}\",\"senderId\":\"${ADMIN_ID}\",\"content\":\"Duplicate test\",\"channel\":\"INTERNAL\"}'${NC}"
echo ""
echo -e "   Expected: Second request should be rejected or ignored"
echo ""

echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}"
echo ""

# Cleanup option
echo -e "${YELLOW}Cleanup:${NC}"
echo -e "  Temporary files stored in: ${TEMP_DIR}"
echo -e "  To remove: ${GREEN}rm -rf ${TEMP_DIR}${NC}"
echo ""

# Summary
echo -e "${CYAN}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                    Load Test Summary                            ║${NC}"
echo -e "${CYAN}╠══════════════════════════════════════════════════════════════════╣${NC}"
echo -e "${CYAN}║${NC} Messages sent:        ${COUNT}                                      ${CYAN}║${NC}"
SUCCESS_PERCENT=$(echo "$SUCCESS_COUNT $COUNT" | awk '{printf "%.1f", ($1 * 100 / $2)}')
echo -e "${CYAN}║${NC} Success rate:         ${SUCCESS_COUNT}/${COUNT} (${SUCCESS_PERCENT}%)                           ${CYAN}║${NC}"
echo -e "${CYAN}║${NC} Duration:             ${DURATION}s                                ${CYAN}║${NC}"
echo -e "${CYAN}║${NC} Throughput:           ${RPS} req/s                             ${CYAN}║${NC}"
echo -e "${CYAN}║${NC} Group ID:             ${GROUP_ID:0:20}...                  ${CYAN}║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""

if [ "$SUCCESS_COUNT" -eq "$COUNT" ]; then
  echo -e "${GREEN}✓✓✓ LOAD TEST PASSED ✓✓✓${NC}"
  echo -e "${GREEN}All ${COUNT} concurrent messages were successfully accepted!${NC}"
  exit 0
else
  echo -e "${RED}✗✗✗ LOAD TEST FAILED ✗✗✗${NC}"
  echo -e "${RED}Some messages failed. Check logs for details.${NC}"
  exit 1
fi
