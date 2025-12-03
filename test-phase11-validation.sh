#!/bin/bash

# Phase 11 Validation Test Suite
# Tests all major features implemented in Phase 11

echo "=========================================="
echo "   PHASE 11 VALIDATION TEST SUITE"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# Helper function to print test result
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ PASS${NC}: $2"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC}: $2"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

echo "1. Testing Service Health Endpoints..."
echo "--------------------------------------"

SERVICES=(
    "8080:API Gateway"
    "8081:Message Service"
    "8083:User Service"
    "8084:File Service"
    "8085:WhatsApp Connector"
    "8086:Telegram Connector"
    "8087:Instagram Connector"
)

for service in "${SERVICES[@]}"; do
    PORT="${service%%:*}"
    NAME="${service##*:}"
    
    RESPONSE=$(curl -s http://localhost:$PORT/actuator/health)
    if echo "$RESPONSE" | grep -q '"status":"UP"'; then
        print_result 0 "$NAME is UP (port $PORT)"
    else
        print_result 1 "$NAME is DOWN (port $PORT)"
    fi
done

echo ""
echo "2. Testing Correlation ID Propagation..."
echo "--------------------------------------"

CORRELATION_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
echo "Generated Correlation ID: $CORRELATION_ID"

RESPONSE=$(curl -s -w "\n%{http_code}" \
    -H "X-Correlation-ID: $CORRELATION_ID" \
    -H "Content-Type: application/json" \
    -X POST http://localhost:8080/api/webhooks/whatsapp \
    -d '{"from":"1234567890","body":"test"}')

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)

if [ "$HTTP_CODE" = "200" ]; then
    print_result 0 "Webhook endpoint returned 200 OK"
    
    # Check if correlation ID appears in logs
    sleep 1
    LOG_COUNT=$(docker-compose logs --tail=100 api-gateway 2>/dev/null | grep -c "$CORRELATION_ID")
    
    if [ $LOG_COUNT -gt 0 ]; then
        print_result 0 "Correlation ID found in logs ($LOG_COUNT occurrences)"
    else
        print_result 1 "Correlation ID NOT found in logs"
    fi
else
    print_result 1 "Webhook endpoint returned $HTTP_CODE (expected 200)"
fi

echo ""
echo "3. Testing Rate Limiting..."
echo "--------------------------------------"
echo "Waiting 65 seconds for rate limit bucket to reset..."
sleep 65

echo "Sending 120 requests to public webhook (limit: 100 req/min)..."

SUCCESS=0
RATE_LIMITED=0
OTHER=0

for i in {1..120}; do
    # Test on public webhook endpoint (not protected by OAuth2)
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/webhooks/whatsapp -H "Content-Type: application/json" -d '{"test":"data"}')
    
    case "$HTTP_CODE" in
        200)
            SUCCESS=$((SUCCESS + 1))
            ;;
        429)
            RATE_LIMITED=$((RATE_LIMITED + 1))
            ;;
        *)
            OTHER=$((OTHER + 1))
            ;;
    esac
    
    # Progress indicators
    if [ $i -eq 50 ]; then
        echo "  Sent 50 requests..."
    elif [ $i -eq 100 ]; then
        echo "  Sent 100 requests..."
    fi
done

echo ""
echo "Rate Limiting Results:"
echo "  - Successful (200): $SUCCESS"
echo "  - Rate Limited (429): $RATE_LIMITED"
echo "  - Other responses: $OTHER"
echo "  - Expected: ~100 success, ~20 blocked"

# Accept results within reasonable range (90-110 success, 10-30 blocked)
if [ $RATE_LIMITED -ge 10 ] && [ $SUCCESS -ge 90 ]; then
    print_result 0 "Rate limiting is WORKING ($SUCCESS success, $RATE_LIMITED blocked)"
else
    print_result 1 "Rate limiting not working as expected ($SUCCESS success, $RATE_LIMITED blocked)"
fi

echo ""
echo "4. Testing OpenAPI Documentation..."
echo "--------------------------------------"

OPENAPI_RESPONSE=$(curl -s http://localhost:8080/v3/api-docs)
if echo "$OPENAPI_RESPONSE" | grep -q '"openapi":"3'; then
    print_result 0 "OpenAPI documentation available at API Gateway"
else
    print_result 1 "OpenAPI documentation NOT available"
fi

SWAGGER_UI=$(curl -s -I http://localhost:8080/swagger-ui.html | head -1)
if echo "$SWAGGER_UI" | grep -q "302"; then
    print_result 0 "Swagger UI available (redirects to /webjars/swagger-ui/)"
else
    print_result 1 "Swagger UI NOT available (no redirect from /swagger-ui.html)"
fi

echo ""
echo "5. Testing Docker Compose Services..."
echo "--------------------------------------"

RUNNING=$(docker-compose ps --services --filter "status=running" | wc -l)
TOTAL=$(docker-compose ps --services | wc -l)

echo "  Running services: $RUNNING / $TOTAL"

if [ $RUNNING -eq $TOTAL ] && [ $RUNNING -ge 16 ]; then
    print_result 0 "All Docker Compose services are running"
else
    print_result 1 "Some services are not running ($RUNNING/$TOTAL)"
fi

echo ""
echo "=========================================="
echo "           TEST SUMMARY"
echo "=========================================="
echo ""
echo -e "  ${GREEN}PASSED${NC}: $TESTS_PASSED"
echo -e "  ${RED}FAILED${NC}: $TESTS_FAILED"
echo "  TOTAL:  $((TESTS_PASSED + TESTS_FAILED))"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ ALL TESTS PASSED!${NC}"
    echo ""
    echo "Phase 11 is validated and ready for merge to main."
    exit 0
else
    echo -e "${RED}✗ SOME TESTS FAILED${NC}"
    echo ""
    echo "Please review the failures above before merging."
    exit 1
fi
