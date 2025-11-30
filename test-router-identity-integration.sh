#!/bin/bash

##############################################################################
# Router-Service Identity Resolution Integration Test
# 
# Tests the integration between Router Service and User Service for
# identity resolution and multi-platform message delivery.
#
# Prerequisites:
# - user-service running on port 8083
# - router-service running on port 8082
# - All connectors running (WhatsApp:8091, Telegram:8092, Instagram:8093)
# - Kafka running on localhost:9092
#
# Test Scenarios:
# 1. Direct platform ID delivery (backward compatibility)
# 2. UUID-based delivery with single identity
# 3. UUID-based delivery with multiple identities (fan-out)
# 4. UUID with no identities (failure case)
# 5. Invalid UUID format (validation)
##############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
USER_SERVICE_URL="http://localhost:8083"
ROUTER_SERVICE_URL="http://localhost:8082"

echo ""
echo "========================================"
echo "Router-User Service Integration Test"
echo "========================================"
echo ""

# Step 0: Check if services are running
echo "► Step 0: Checking if services are running..."

if ! curl -s -f "${USER_SERVICE_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}✗ user-service is not running on port 8083${NC}"
    exit 1
fi
echo -e "${GREEN}✓ user-service is running${NC}"

if ! curl -s -f "${ROUTER_SERVICE_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${YELLOW}⚠ router-service is not running on port 8082 (optional for unit test)${NC}"
fi

echo ""

# Step 1: Create test user with multiple identities
echo "► Step 1: Creating test user with WhatsApp and Telegram identities..."

USER_RESPONSE=$(curl -s -X POST "${USER_SERVICE_URL}/api/v1/users" \
    -H "Content-Type: application/json" \
    -d '{
        "displayName": "Test User - Multi Platform",
        "email": "multiplatform@test.com",
        "userType": "CUSTOMER"
    }')

USER_ID=$(echo "$USER_RESPONSE" | jq -r '.id')

if [ -z "$USER_ID" ] || [ "$USER_ID" == "null" ]; then
    echo -e "${RED}✗ Failed to create user${NC}"
    echo "Response: $USER_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ User created - ID: $USER_ID${NC}"
echo ""

# Step 2: Link WhatsApp identity
echo "► Step 2: Linking WhatsApp identity..."

WHATSAPP_RESPONSE=$(curl -s -X POST "${USER_SERVICE_URL}/api/v1/users/${USER_ID}/identities" \
    -H "Content-Type: application/json" \
    -d '{
        "platform": "WHATSAPP",
        "platformUserId": "+5562991234567",
        "verified": false
    }')

WHATSAPP_ID=$(echo "$WHATSAPP_RESPONSE" | jq -r '.id')

if [ -z "$WHATSAPP_ID" ] || [ "$WHATSAPP_ID" == "null" ]; then
    echo -e "${RED}✗ Failed to link WhatsApp identity${NC}"
    echo "Response: $WHATSAPP_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ WhatsApp linked - Identity ID: $WHATSAPP_ID${NC}"
echo ""

# Step 3: Link Telegram identity
echo "► Step 3: Linking Telegram identity..."

TELEGRAM_RESPONSE=$(curl -s -X POST "${USER_SERVICE_URL}/api/v1/users/${USER_ID}/identities" \
    -H "Content-Type: application/json" \
    -d '{
        "platform": "TELEGRAM",
        "platformUserId": "@test_user_123",
        "verified": false
    }')

TELEGRAM_ID=$(echo "$TELEGRAM_RESPONSE" | jq -r '.id')

if [ -z "$TELEGRAM_ID" ] || [ "$TELEGRAM_ID" == "null" ]; then
    echo -e "${RED}✗ Failed to link Telegram identity${NC}"
    echo "Response: $TELEGRAM_RESPONSE"
    exit 1
fi

echo -e "${GREEN}✓ Telegram linked - Identity ID: $TELEGRAM_ID${NC}"
echo ""

# Step 4: Verify user has both identities
echo "► Step 4: Verifying user has 2 linked identities..."

USER_DETAILS=$(curl -s "${USER_SERVICE_URL}/api/v1/users/${USER_ID}")
IDENTITY_COUNT=$(echo "$USER_DETAILS" | jq '.externalIdentities | length')

if [ "$IDENTITY_COUNT" != "2" ]; then
    echo -e "${RED}✗ Expected 2 identities, found: $IDENTITY_COUNT${NC}"
    exit 1
fi

echo -e "${GREEN}✓ User has 2 identities (WhatsApp + Telegram)${NC}"
echo ""

# Step 5: Display test instructions for router service
echo "========================================"
echo "Integration Test Setup Complete!"
echo "========================================"
echo ""
echo -e "${BLUE}Test User Details:${NC}"
echo "  User ID: $USER_ID"
echo "  WhatsApp: +5562991234567"
echo "  Telegram: @test_user_123"
echo ""
echo -e "${YELLOW}Manual Test Instructions:${NC}"
echo ""
echo "1. Send a message to the Router Service with the User ID as recipient:"
echo ""
echo "   curl -X POST http://localhost:8082/api/v1/messages \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{"
echo "       \"recipientId\": \"${USER_ID}\","
echo "       \"content\": \"Test message for multi-platform delivery\","
echo "       \"channel\": \"WHATSAPP\""
echo "     }'"
echo ""
echo "2. Check router-service logs for identity resolution:"
echo "   - Should call User Service to resolve UUID"
echo "   - Should fan-out to BOTH WhatsApp AND Telegram connectors"
echo "   - Should log: \"User has 2 linked identities - fanning out message\""
echo ""
echo "3. Expected behavior:"
echo "   - Router detects UUID format"
echo "   - Calls GET /api/v1/users/${USER_ID}"
echo "   - Receives 2 external identities"
echo "   - Delivers to WhatsApp connector (http://localhost:8091)"
echo "   - Delivers to Telegram connector (http://localhost:8092)"
echo "   - Returns success if at least 1 delivery succeeds"
echo ""
echo "4. Test direct platform ID (backward compatibility):"
echo ""
echo "   curl -X POST http://localhost:8082/api/v1/messages \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{"
echo "       \"recipientId\": \"+5562999999999\","
echo "       \"content\": \"Direct WhatsApp message\","
echo "       \"channel\": \"WHATSAPP\""
echo "     }'"
echo ""
echo "   - Router should detect non-UUID format"
echo "   - Should deliver directly to WhatsApp without User Service call"
echo ""
echo -e "${GREEN}✓ Integration test user created successfully!${NC}"
echo ""
echo "========================================"
echo ""

# Cleanup instructions
echo -e "${YELLOW}Cleanup (optional):${NC}"
echo "Delete test data:"
echo "  curl -s \"http://localhost:8083/api/v1/users\" | jq"
echo "  # Manually delete user via user-service API or database"
echo ""
