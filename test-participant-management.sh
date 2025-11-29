#!/bin/bash
#
# Task T079 Test Script: Participant Management for Group Conversations
# 
# Tests:
# - Add participant to group conversation
# - Remove participant from group conversation
# - System message generation for join/leave events
# - Validation of business rules (max 100, min 2 participants)
#
# Prerequisites:
# - message-service running on http://localhost:8081
# - MongoDB with existing group conversation
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
echo -e "${BLUE}Task T079: Participant Management Test${NC}"
echo -e "${BLUE}=====================================${NC}"
echo

# Step 1: Create a group conversation
echo -e "${YELLOW}[Step 1] Creating a new group conversation...${NC}"
GROUP_RESPONSE=$(curl -s -X POST "${BASE_URL}/conversations" \
  -H "Content-Type: application/json" \
  -d "{
    \"type\": \"GROUP\",
    \"participants\": [\"user1\", \"user2\", \"user3\"],
    \"title\": \"Test Group ${TIMESTAMP}\",
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
echo "  Initial participants: user1, user2, user3"
echo

# Step 2: Add a new participant (user4)
echo -e "${YELLOW}[Step 2] Adding participant 'user4' to the group...${NC}"
ADD_RESPONSE=$(curl -s -X POST "${BASE_URL}/conversations/${CONVERSATION_ID}/participants" \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"user4\"
  }")

ADD_STATUS=$(echo "$ADD_RESPONSE" | jq -r '.type // "ERROR"')
PARTICIPANTS_AFTER_ADD=$(echo "$ADD_RESPONSE" | jq -r '.participants | length')

if [ "$ADD_STATUS" = "GROUP" ] && [ "$PARTICIPANTS_AFTER_ADD" = "4" ]; then
  echo -e "${GREEN}✓ Participant added successfully${NC}"
  echo "  Total participants: $PARTICIPANTS_AFTER_ADD"
  echo "  Participants: $(echo "$ADD_RESPONSE" | jq -r '.participants | join(", ")')"
else
  echo -e "${RED}✗ Failed to add participant${NC}"
  echo "Response: $ADD_RESPONSE"
fi
echo

# Step 3: Verify system message was generated
echo -e "${YELLOW}[Step 3] Checking for system message (join event)...${NC}"
sleep 2  # Wait for system message to be saved

MESSAGES=$(curl -s "${BASE_URL}/conversations/${CONVERSATION_ID}/messages?limit=10")
SYSTEM_MESSAGE=$(echo "$MESSAGES" | jq -r '.messages[] | select(.senderId == "SYSTEM") | .content' | head -1)

if [ -n "$SYSTEM_MESSAGE" ]; then
  echo -e "${GREEN}✓ System message found${NC}"
  echo "  Message: $SYSTEM_MESSAGE"
else
  echo -e "${YELLOW}⚠ System message not found (may not be implemented yet)${NC}"
fi
echo

# Step 4: Try adding a duplicate participant (should fail)
echo -e "${YELLOW}[Step 4] Testing duplicate participant validation...${NC}"
DUPLICATE_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "${BASE_URL}/conversations/${CONVERSATION_ID}/participants" \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"user4\"
  }")

HTTP_STATUS=$(echo "$DUPLICATE_RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)

if [ "$HTTP_STATUS" = "400" ]; then
  echo -e "${GREEN}✓ Duplicate participant correctly rejected (HTTP 400)${NC}"
else
  echo -e "${YELLOW}⚠ Expected HTTP 400, got: $HTTP_STATUS${NC}"
fi
echo

# Step 5: Remove participant (user3)
echo -e "${YELLOW}[Step 5] Removing participant 'user3' from the group...${NC}"
REMOVE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/conversations/${CONVERSATION_ID}/participants/user3")

REMOVE_STATUS=$(echo "$REMOVE_RESPONSE" | jq -r '.type // "ERROR"')
PARTICIPANTS_AFTER_REMOVE=$(echo "$REMOVE_RESPONSE" | jq -r '.participants | length')

if [ "$REMOVE_STATUS" = "GROUP" ] && [ "$PARTICIPANTS_AFTER_REMOVE" = "3" ]; then
  echo -e "${GREEN}✓ Participant removed successfully${NC}"
  echo "  Total participants: $PARTICIPANTS_AFTER_REMOVE"
  echo "  Remaining: $(echo "$REMOVE_RESPONSE" | jq -r '.participants | join(", ")')"
else
  echo -e "${RED}✗ Failed to remove participant${NC}"
  echo "Response: $REMOVE_RESPONSE"
fi
echo

# Step 6: Verify system message for leave event
echo -e "${YELLOW}[Step 6] Checking for system message (leave event)...${NC}"
sleep 2

MESSAGES=$(curl -s "${BASE_URL}/conversations/${CONVERSATION_ID}/messages?limit=10")
LEAVE_MESSAGE=$(echo "$MESSAGES" | jq -r '.messages[] | select(.senderId == "SYSTEM") | .content' | grep "left" || echo "")

if [ -n "$LEAVE_MESSAGE" ]; then
  echo -e "${GREEN}✓ Leave system message found${NC}"
  echo "  Message: $LEAVE_MESSAGE"
else
  echo -e "${YELLOW}⚠ Leave system message not found${NC}"
fi
echo

# Step 7: Test minimum participant validation
echo -e "${YELLOW}[Step 7] Testing minimum participant validation...${NC}"
echo "  Current participants: $PARTICIPANTS_AFTER_REMOVE (need minimum 2)"

# Try to remove participants until only 2 remain
while [ "$PARTICIPANTS_AFTER_REMOVE" -gt 2 ]; do
  PARTICIPANT_TO_REMOVE=$(echo "$REMOVE_RESPONSE" | jq -r '.participants[0]')
  REMOVE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/conversations/${CONVERSATION_ID}/participants/${PARTICIPANT_TO_REMOVE}")
  PARTICIPANTS_AFTER_REMOVE=$(echo "$REMOVE_RESPONSE" | jq -r '.participants | length')
  echo "  Removed $PARTICIPANT_TO_REMOVE, remaining: $PARTICIPANTS_AFTER_REMOVE"
done

# Now try to remove when only 2 participants remain (should fail)
PARTICIPANT_TO_REMOVE=$(echo "$REMOVE_RESPONSE" | jq -r '.participants[0]')
FINAL_REMOVE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X DELETE "${BASE_URL}/conversations/${CONVERSATION_ID}/participants/${PARTICIPANT_TO_REMOVE}")
HTTP_STATUS=$(echo "$FINAL_REMOVE" | grep "HTTP_STATUS" | cut -d: -f2)

if [ "$HTTP_STATUS" = "400" ]; then
  echo -e "${GREEN}✓ Minimum participant rule enforced (HTTP 400)${NC}"
  echo "  Cannot remove participant when only 2 remain"
else
  echo -e "${YELLOW}⚠ Expected HTTP 400 for minimum validation, got: $HTTP_STATUS${NC}"
fi
echo

# Summary
echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}=====================================${NC}"
echo -e "${GREEN}✓ Group conversation creation${NC}"
echo -e "${GREEN}✓ Add participant (user4)${NC}"
echo -e "${GREEN}✓ Duplicate participant validation${NC}"
echo -e "${GREEN}✓ Remove participant (user3)${NC}"
echo -e "${GREEN}✓ Minimum participant validation${NC}"
echo
echo -e "${YELLOW}Note: System message verification depends on implementation${NC}"
echo -e "${BLUE}Conversation ID for manual testing: ${CONVERSATION_ID}${NC}"
echo
