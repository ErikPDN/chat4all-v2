#!/bin/bash
###############################################################################
# Test Script: Identity Mapping Flow Validation
# Purpose: End-to-end testing of User Story 5 (Identity Mapping)
# Author: Chat4All Development Team
# Date: 2025-11-29
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="http://localhost:8083/api/v1"
USERS_ENDPOINT="$BASE_URL/users"
IDENTITIES_ENDPOINT="$BASE_URL/identities"

# Test data
USER1_NAME="Erik Silva"
USER1_EMAIL="erik@chat4all.com"
USER1_TYPE="CUSTOMER"

USER2_NAME="Ana Costa"
USER2_EMAIL="ana@chat4all.com"
USER2_TYPE="AGENT"

WHATSAPP_ID="+5562999999999"
TELEGRAM_ID="erik_dev"
DUPLICATE_TEST_ID="+5562888888888"

# Variables to store created resources
USER1_ID=""
USER2_ID=""
IDENTITY1_ID=""
IDENTITY2_ID=""

###############################################################################
# Helper Functions
###############################################################################

print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_step() {
    echo -e "${YELLOW}► Step $1: $2${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

check_service() {
    print_step "0" "Checking if user-service is running on port 8083..."
    
    if ! curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/users" > /dev/null 2>&1; then
        print_error "user-service is not running on port 8083"
        echo -e "\nPlease start the service with:"
        echo -e "  ${YELLOW}cd services/user-service && mvn spring-boot:run${NC}\n"
        exit 1
    fi
    
    print_success "user-service is running"
}

###############################################################################
# Test Flow
###############################################################################

print_header "User Story 5 - Identity Mapping Flow Test"

# Step 0: Check if service is running
check_service

# Step 1: Create first user (Erik)
print_step "1" "Creating user 'Erik Silva'"

CREATE_USER1_RESPONSE=$(curl -s -X POST "$USERS_ENDPOINT" \
    -H "Content-Type: application/json" \
    -d "{
        \"displayName\": \"$USER1_NAME\",
        \"email\": \"$USER1_EMAIL\",
        \"userType\": \"$USER1_TYPE\"
    }")

USER1_ID=$(echo "$CREATE_USER1_RESPONSE" | jq -r '.id')

if [ "$USER1_ID" == "null" ] || [ -z "$USER1_ID" ]; then
    print_error "Failed to create user Erik"
    echo "Response: $CREATE_USER1_RESPONSE"
    exit 1
fi

print_success "User created successfully - ID: $USER1_ID"

# Step 2: Link WhatsApp identity to Erik
print_step "2" "Linking WhatsApp identity '$WHATSAPP_ID' to Erik"

LINK_WHATSAPP_RESPONSE=$(curl -s -X POST "$USERS_ENDPOINT/$USER1_ID/identities" \
    -H "Content-Type: application/json" \
    -d "{
        \"platform\": \"WHATSAPP\",
        \"platformUserId\": \"$WHATSAPP_ID\",
        \"verified\": false
    }")

IDENTITY1_ID=$(echo "$LINK_WHATSAPP_RESPONSE" | jq -r '.id')

if [ "$IDENTITY1_ID" == "null" ] || [ -z "$IDENTITY1_ID" ]; then
    print_error "Failed to link WhatsApp identity"
    echo "Response: $LINK_WHATSAPP_RESPONSE"
    exit 1
fi

print_success "WhatsApp identity linked - Identity ID: $IDENTITY1_ID"

# Step 3: Link Telegram identity to Erik
print_step "3" "Linking Telegram identity '$TELEGRAM_ID' to Erik"

LINK_TELEGRAM_RESPONSE=$(curl -s -X POST "$USERS_ENDPOINT/$USER1_ID/identities" \
    -H "Content-Type: application/json" \
    -d "{
        \"platform\": \"TELEGRAM\",
        \"platformUserId\": \"$TELEGRAM_ID\",
        \"verified\": false
    }")

IDENTITY2_ID=$(echo "$LINK_TELEGRAM_RESPONSE" | jq -r '.id')

if [ "$IDENTITY2_ID" == "null" ] || [ -z "$IDENTITY2_ID" ]; then
    print_error "Failed to link Telegram identity"
    echo "Response: $LINK_TELEGRAM_RESPONSE"
    exit 1
fi

print_success "Telegram identity linked - Identity ID: $IDENTITY2_ID"

# Step 4: Resolve WhatsApp identity and verify it returns Erik's user ID
print_step "4" "Resolving WhatsApp identity '$WHATSAPP_ID' (should return Erik's ID)"

# URL-encode the phone number (+ becomes %2B)
ENCODED_WHATSAPP=$(echo "$WHATSAPP_ID" | sed 's/+/%2B/g')

RESOLVE_RESPONSE=$(curl -s -X GET "$IDENTITIES_ENDPOINT/resolve?platform=WHATSAPP&id=$ENCODED_WHATSAPP")

RESOLVED_USER_ID=$(echo "$RESOLVE_RESPONSE" | jq -r '.userId')
RESOLVED_DISPLAY_NAME=$(echo "$RESOLVE_RESPONSE" | jq -r '.displayName')

if [ "$RESOLVED_USER_ID" != "$USER1_ID" ]; then
    print_error "Identity resolution failed"
    echo "Expected userId: $USER1_ID"
    echo "Got userId: $RESOLVED_USER_ID"
    echo "Response: $RESOLVE_RESPONSE"
    exit 1
fi

print_success "Identity resolved correctly to user '$RESOLVED_DISPLAY_NAME' (ID: $RESOLVED_USER_ID)"

# Step 5: Verify user has both identities
print_step "5" "Retrieving user details (should show 2 identities)"

GET_USER_RESPONSE=$(curl -s -X GET "$USERS_ENDPOINT/$USER1_ID")

IDENTITIES_COUNT=$(echo "$GET_USER_RESPONSE" | jq '.externalIdentities | length')

if [ "$IDENTITIES_COUNT" != "2" ]; then
    print_error "Expected 2 identities, found $IDENTITIES_COUNT"
    echo "Response: $GET_USER_RESPONSE"
    exit 1
fi

print_success "User has 2 linked identities (WhatsApp + Telegram)"

# Step 6: Create second user (Ana)
print_step "6" "Creating second user 'Ana Costa'"

CREATE_USER2_RESPONSE=$(curl -s -X POST "$USERS_ENDPOINT" \
    -H "Content-Type: application/json" \
    -d "{
        \"displayName\": \"$USER2_NAME\",
        \"email\": \"$USER2_EMAIL\",
        \"userType\": \"$USER2_TYPE\"
    }")

USER2_ID=$(echo "$CREATE_USER2_RESPONSE" | jq -r '.id')

if [ "$USER2_ID" == "null" ] || [ -z "$USER2_ID" ]; then
    print_error "Failed to create user Ana"
    echo "Response: $CREATE_USER2_RESPONSE"
    exit 1
fi

print_success "Second user created - ID: $USER2_ID"

# Step 7: Try to link Erik's WhatsApp to Ana (should fail with 409 Conflict)
print_step "7" "Attempting to link Erik's WhatsApp to Ana (should fail with 409 Conflict)"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$USERS_ENDPOINT/$USER2_ID/identities" \
    -H "Content-Type: application/json" \
    -d "{
        \"platform\": \"WHATSAPP\",
        \"platformUserId\": \"$WHATSAPP_ID\",
        \"verified\": false
    }")

if [ "$HTTP_CODE" == "409" ]; then
    print_success "Duplicate identity rejected correctly (HTTP 409 Conflict)"
elif [ "$HTTP_CODE" == "400" ]; then
    print_success "Duplicate identity rejected (HTTP 400 Bad Request - acceptable)"
else
    print_error "Expected HTTP 409 or 400, got HTTP $HTTP_CODE"
    exit 1
fi

# Step 8: Link a different WhatsApp to Ana (should succeed)
print_step "8" "Linking different WhatsApp '$DUPLICATE_TEST_ID' to Ana (should succeed)"

LINK_ANA_WHATSAPP_RESPONSE=$(curl -s -X POST "$USERS_ENDPOINT/$USER2_ID/identities" \
    -H "Content-Type: application/json" \
    -d "{
        \"platform\": \"WHATSAPP\",
        \"platformUserId\": \"$DUPLICATE_TEST_ID\",
        \"verified\": false
    }")

ANA_IDENTITY_ID=$(echo "$LINK_ANA_WHATSAPP_RESPONSE" | jq -r '.id')

if [ "$ANA_IDENTITY_ID" == "null" ] || [ -z "$ANA_IDENTITY_ID" ]; then
    print_error "Failed to link WhatsApp to Ana"
    echo "Response: $LINK_ANA_WHATSAPP_RESPONSE"
    exit 1
fi

print_success "Ana's WhatsApp linked successfully - Identity ID: $ANA_IDENTITY_ID"

# Step 9: Unlink Erik's Telegram identity
print_step "9" "Unlinking Telegram identity from Erik"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    "$USERS_ENDPOINT/$USER1_ID/identities/TELEGRAM/$TELEGRAM_ID")

if [ "$HTTP_CODE" == "204" ]; then
    print_success "Telegram identity unlinked (HTTP 204 No Content)"
else
    print_error "Expected HTTP 204, got HTTP $HTTP_CODE"
    exit 1
fi

# Step 10: Verify Erik now has only 1 identity (WhatsApp)
print_step "10" "Verifying Erik now has only 1 identity (WhatsApp)"

GET_USER_FINAL_RESPONSE=$(curl -s -X GET "$USERS_ENDPOINT/$USER1_ID")

FINAL_IDENTITIES_COUNT=$(echo "$GET_USER_FINAL_RESPONSE" | jq '.externalIdentities | length')

if [ "$FINAL_IDENTITIES_COUNT" != "1" ]; then
    print_error "Expected 1 identity after unlinking, found $FINAL_IDENTITIES_COUNT"
    echo "Response: $GET_USER_FINAL_RESPONSE"
    exit 1
fi

REMAINING_PLATFORM=$(echo "$GET_USER_FINAL_RESPONSE" | jq -r '.externalIdentities[0].platform')

if [ "$REMAINING_PLATFORM" != "WHATSAPP" ]; then
    print_error "Expected remaining identity to be WHATSAPP, found $REMAINING_PLATFORM"
    exit 1
fi

print_success "Erik has 1 remaining identity (WhatsApp)"

###############################################################################
# Test Summary
###############################################################################

print_header "Test Summary - All Tests Passed! ✓"

echo -e "${GREEN}✓ User creation${NC}"
echo -e "${GREEN}✓ Identity linking (WhatsApp + Telegram)${NC}"
echo -e "${GREEN}✓ Identity resolution (message routing simulation)${NC}"
echo -e "${GREEN}✓ Duplicate identity prevention (409 Conflict)${NC}"
echo -e "${GREEN}✓ Multiple users with different identities${NC}"
echo -e "${GREEN}✓ Identity unlinking${NC}"

echo -e "\n${BLUE}Created Resources:${NC}"
echo -e "  User 1 (Erik):  $USER1_ID"
echo -e "  User 2 (Ana):   $USER2_ID"
echo -e "  WhatsApp (Erik):    $WHATSAPP_ID → User 1"
echo -e "  WhatsApp (Ana):     $DUPLICATE_TEST_ID → User 2"

echo -e "\n${GREEN}🎉 Identity Mapping API is ready for integration!${NC}\n"
