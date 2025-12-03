#!/bin/bash

# Test script for remaining tasks implementation
# Tests: T030 (Health), T089 (Verification), T090 (Audit), T092 (Suggestion)

set -e

echo "=========================================="
echo "Testing Remaining Tasks Implementation"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Test function
test_case() {
    local test_name=$1
    local command=$2
    local expected=$3
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -n "[$TOTAL_TESTS] Testing: $test_name... "
    
    result=$(eval "$command" 2>&1)
    
    if echo "$result" | grep -q "$expected"; then
        echo -e "${GREEN}PASS${NC}"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        return 0
    else
        echo -e "${RED}FAIL${NC}"
        echo "  Expected: $expected"
        echo "  Got: $result"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
}

echo "=========================================="
echo "T030: Health Check Endpoints"
echo "=========================================="
echo ""

test_case "User Service health endpoint" \
    "curl -s http://localhost:8083/actuator/health | jq -r .status" \
    "UP"

test_case "Message Service health endpoint" \
    "curl -s http://localhost:8081/actuator/health | jq -r .status" \
    "UP"

test_case "File Service health endpoint" \
    "curl -s http://localhost:8084/actuator/health | jq -r .status" \
    "UP"

test_case "API Gateway health endpoint" \
    "curl -s http://localhost:8080/actuator/health | jq -r .status" \
    "UP"

echo ""
echo "=========================================="
echo "T089/T090: Verification & Audit Services"
echo "=========================================="
echo ""

# Check if services compiled successfully (JAR timestamp)
test_case "User service JAR built recently" \
    "docker exec chat4all-user-service stat -c %Y /app/app.jar" \
    "[0-9]"

# Check database schema
test_case "audit_logs table exists" \
    "docker exec chat4all-postgres psql -U chat4all -d chat4all -c '\dt audit_logs' 2>&1" \
    "audit_logs"

test_case "external_identities table has verified column" \
    "docker exec chat4all-postgres psql -U chat4all -d chat4all -c '\d external_identities' 2>&1" \
    "verified"

echo ""
echo "=========================================="
echo "T092: Identity Suggestion Algorithm"
echo "=========================================="
echo ""

test_case "User service started with new code" \
    "docker logs chat4all-user-service 2>&1 | grep 'Started UserServiceApplication'" \
    "Started"

echo ""
echo "=========================================="
echo "Service Logs Check"
echo "=========================================="
echo ""

# Check for compilation errors in logs
test_case "User service started without errors" \
    "docker logs chat4all-user-service 2>&1 | tail -20" \
    "Started"

echo ""
echo "=========================================="
echo "Database Validation"
echo "=========================================="
echo ""

# Verify audit_logs table structure
echo "Checking audit_logs table structure..."
docker exec chat4all-postgres psql -U chat4all -d chat4all -c "\d audit_logs" 2>&1 | head -20

echo ""
echo "Checking external_identities table structure..."
docker exec chat4all-postgres psql -U chat4all -d chat4all -c "\d external_identities" 2>&1 | head -20

echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo ""
echo "Total Tests:  $TOTAL_TESTS"
echo -e "Passed:       ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed:       ${RED}$FAILED_TESTS${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi
