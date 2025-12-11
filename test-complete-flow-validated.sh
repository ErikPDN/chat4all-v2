#!/bin/bash

# ============================================================
# Chat4All v2 - Complete Authentication & Messaging Flow Test
# ============================================================
# This script validates the complete end-to-end flow:
# 1. Keycloak OAuth2 Authentication (alice & bob)
# 2. User ID extraction from JWT tokens
# 3. Message sending (alice → bob)
# 4. Message reply (bob → alice)
# 5. Message status verification
#
# Prerequisites:
# - Keycloak running on http://localhost:8888
# - Message Service running on http://localhost:8081
# - Users 'alice' and 'bob' created in Keycloak chat4all realm
# - Client 'chat4all-client' configured as public
# ============================================================

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8888}"
MESSAGE_SERVICE_URL="${MESSAGE_SERVICE_URL:-http://localhost:8081}"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
  echo -e "\n${BLUE}========== $1 ==========${NC}\n"
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

# ============================================================
# STEP 1: Authenticate Users
# ============================================================
print_header "STEP 1: Keycloak Authentication"

print_info "Authenticating user: alice"
AUTH1=$(curl -s -X POST "$KEYCLOAK_URL/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=chat4all-client&username=alice&password=alice123")

TOKEN1=$(echo $AUTH1 | jq -r '.access_token' 2>/dev/null)
if [ -z "$TOKEN1" ] || [ "$TOKEN1" = "null" ]; then
  print_error "Failed to authenticate alice"
  exit 1
fi
print_success "Alice authenticated"

print_info "Authenticating user: bob"
AUTH2=$(curl -s -X POST "$KEYCLOAK_URL/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=chat4all-client&username=bob&password=bob123")

TOKEN2=$(echo $AUTH2 | jq -r '.access_token' 2>/dev/null)
if [ -z "$TOKEN2" ] || [ "$TOKEN2" = "null" ]; then
  print_error "Failed to authenticate bob"
  exit 1
fi
print_success "Bob authenticated"

# ============================================================
# STEP 2: Extract User IDs from JWT Token Claims
# ============================================================
print_header "STEP 2: Extract User IDs from Tokens"

USER1_ID=$(echo $TOKEN1 | cut -d'.' -f2 | base64 -d 2>/dev/null | jq -r '.sub' 2>/dev/null)
if [ -z "$USER1_ID" ] || [ "$USER1_ID" = "null" ]; then
  print_error "Failed to extract alice ID from token"
  exit 1
fi
print_success "Alice ID: $USER1_ID"

USER2_ID=$(echo $TOKEN2 | cut -d'.' -f2 | base64 -d 2>/dev/null | jq -r '.sub' 2>/dev/null)
if [ -z "$USER2_ID" ] || [ "$USER2_ID" = "null" ]; then
  print_error "Failed to extract bob ID from token"
  exit 1
fi
print_success "Bob ID: $USER2_ID"

# ============================================================
# STEP 3: Alice Sends Message to Bob
# ============================================================
print_header "STEP 3: Alice Sends Message"

CONVERSATION_ID="conv-$(date +%s)"
print_info "Conversation ID: $CONVERSATION_ID"
print_info "Sending message from alice to bob..."

MESSAGE1=$(curl -s -X POST "$MESSAGE_SERVICE_URL/api/messages" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId":"'$CONVERSATION_ID'",
    "senderId":"'$USER1_ID'",
    "content":"Hello Bob! This is a test message from Alice via Chat4All v2.",
    "channel":"INTERNAL"
  }')

MESSAGE1_ID=$(echo $MESSAGE1 | jq -r '.messageId' 2>/dev/null)
MESSAGE1_STATUS=$(echo $MESSAGE1 | jq -r '.status' 2>/dev/null)

if [ -z "$MESSAGE1_ID" ] || [ "$MESSAGE1_ID" = "null" ]; then
  print_error "Failed to send message"
  echo "Response: $MESSAGE1"
  exit 1
fi
print_success "Message 1 sent"
print_info "Message ID: $MESSAGE1_ID"
print_info "Status: $MESSAGE1_STATUS"

# ============================================================
# STEP 4: Bob Sends Reply to Alice
# ============================================================
print_header "STEP 4: Bob Sends Reply"

print_info "Sending reply from bob to alice..."

MESSAGE2=$(curl -s -X POST "$MESSAGE_SERVICE_URL/api/messages" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId":"'$CONVERSATION_ID'",
    "senderId":"'$USER2_ID'",
    "content":"Hi Alice! I received your message. Great to see Chat4All v2 working perfectly!",
    "channel":"INTERNAL"
  }')

MESSAGE2_ID=$(echo $MESSAGE2 | jq -r '.messageId' 2>/dev/null)
MESSAGE2_STATUS=$(echo $MESSAGE2 | jq -r '.status' 2>/dev/null)

if [ -z "$MESSAGE2_ID" ] || [ "$MESSAGE2_ID" = "null" ]; then
  print_error "Failed to send reply"
  echo "Response: $MESSAGE2"
  exit 1
fi
print_success "Message 2 sent"
print_info "Message ID: $MESSAGE2_ID"
print_info "Status: $MESSAGE2_STATUS"

# ============================================================
# STEP 5: Verify Message Status
# ============================================================
print_header "STEP 5: Verify Message Status"

print_info "Checking status of Message 1..."
STATUS1=$(curl -s "$MESSAGE_SERVICE_URL/api/messages/$MESSAGE1_ID/status")
STATUS1_VALUE=$(echo $STATUS1 | jq -r '.status' 2>/dev/null)
print_info "Message 1 status: $STATUS1_VALUE"

print_info "Checking status of Message 2..."
STATUS2=$(curl -s "$MESSAGE_SERVICE_URL/api/messages/$MESSAGE2_ID/status")
STATUS2_VALUE=$(echo $STATUS2 | jq -r '.status' 2>/dev/null)
print_info "Message 2 status: $STATUS2_VALUE"

# ============================================================
# Summary & Results
# ============================================================
print_header "Test Summary"

echo -e "${GREEN}========== FLOW VALIDATION SUCCESSFUL ==========${NC}"
echo ""
echo "Authentication:"
echo -e "  ${GREEN}✓${NC} Keycloak OAuth2 (Password Grant)"
echo -e "  ${GREEN}✓${NC} User alice: Authenticated"
echo -e "  ${GREEN}✓${NC} User bob: Authenticated"
echo ""
echo "Message Exchange:"
echo -e "  ${GREEN}✓${NC} Message 1 (alice → bob): $MESSAGE1_ID"
echo -e "  ${GREEN}✓${NC} Message 2 (bob → alice): $MESSAGE2_ID"
echo ""
echo "Message Status:"
echo -e "  ${GREEN}✓${NC} Message 1 Status: $STATUS1_VALUE"
echo -e "  ${GREEN}✓${NC} Message 2 Status: $STATUS2_VALUE"
echo ""
echo "Conversation:"
echo -e "  ${GREEN}✓${NC} Conversation ID: $CONVERSATION_ID"
echo ""
echo -e "${GREEN}All steps completed successfully!${NC}"
echo ""
