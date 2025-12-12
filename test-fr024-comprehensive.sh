#!/bin/bash

################################################################################
# FR-024 Comprehensive Upload Test Suite
# Tests multiple file sizes and scenarios
# Usage: ./test-fr024-comprehensive.sh
################################################################################

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
FILE_SERVICE_URL="${FILE_SERVICE_URL:-http://localhost:8084}"
RESULTS_FILE="/tmp/fr024-test-results.txt"

# Test scenarios
declare -a TEST_SCENARIOS=(
    "10:10MB"
    "100:100MB"
    "500:500MB"
    "1000:1GB"
    "2048:2GB"
)

# Logging
log_header() {
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}  $1"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
}

log_subheader() {
    echo -e "${BLUE}┌─────────────────────────────────────────────────────────────────┐${NC}"
    echo -e "${BLUE}│${NC} $1"
    echo -e "${BLUE}└─────────────────────────────────────────────────────────────────┘${NC}"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_subheader "Checking Prerequisites"
    
    # Check curl
    if ! command -v curl &> /dev/null; then
        log_error "curl not found. Please install curl."
        exit 1
    fi
    log_success "curl found"
    
    # Check jq
    if ! command -v jq &> /dev/null; then
        log_error "jq not found. Please install jq."
        exit 1
    fi
    log_success "jq found"
    
    # Check dd
    if ! command -v dd &> /dev/null; then
        log_error "dd not found"
        exit 1
    fi
    log_success "dd found"
    
    # Check File Service
    HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$FILE_SERVICE_URL/actuator/health" 2>/dev/null)
    if [ "$HEALTH" != "200" ]; then
        log_error "File Service not accessible at $FILE_SERVICE_URL"
        log_info "Start File Service with: docker-compose up -d file-service"
        exit 1
    fi
    log_success "File Service running on $FILE_SERVICE_URL"
    
    echo ""
}

# Test single upload
test_upload() {
    local SIZE_MB=$1
    local LABEL=$2
    local FILENAME="test-fr024-${SIZE_MB}mb.bin"
    local SIZE_BYTES=$((SIZE_MB * 1024 * 1024))
    
    log_info "Testing $LABEL upload..."
    
    # Request presigned URL
    RESPONSE=$(curl -s -X POST "$FILE_SERVICE_URL/api/files/initiate" \
        -H "Content-Type: application/json" \
        -d "{
            \"filename\": \"$FILENAME\",
            \"fileSize\": $SIZE_BYTES,
            \"mimeType\": \"application/octet-stream\"
        }" 2>/dev/null)
    
    FILE_ID=$(echo "$RESPONSE" | jq -r '.fileId' 2>/dev/null)
    UPLOAD_URL=$(echo "$RESPONSE" | jq -r '.uploadUrl' 2>/dev/null)
    
    if [ -z "$FILE_ID" ] || [ "$FILE_ID" = "null" ]; then
        log_error "Failed to get presigned URL for $LABEL"
        echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
        return 1
    fi
    
    # Perform upload with timing
    START=$(date +%s)
    HTTP_CODE=$(curl -w "%{http_code}" -s -X PUT "$UPLOAD_URL" \
        -H "Content-Type: application/octet-stream" \
        --data-binary @<(dd if=/dev/zero bs=1M count=$SIZE_MB 2>/dev/null) \
        -o /dev/null 2>/dev/null)
    END=$(date +%s)
    
    DURATION=$((END - START))
    if [ $DURATION -lt 1 ]; then
        DURATION=1
    fi
    
    # Record results
    if [ "$HTTP_CODE" = "200" ]; then
        SPEED=$((SIZE_MB / DURATION))
        if [ $SPEED -lt 1 ]; then
            SPEED=1
        fi
        
        printf "%-10s %5dMB %4ds %5d MB/s ${GREEN}✓ PASS${NC}\n" \
            "$LABEL" "$SIZE_MB" "$DURATION" "$SPEED"
        
        echo "$LABEL|$SIZE_MB|$DURATION|$SPEED|PASS" >> "$RESULTS_FILE"
        return 0
    else
        printf "%-10s %5dMB %4ds        ${RED}✗ FAIL (HTTP $HTTP_CODE)${NC}\n" \
            "$LABEL" "$SIZE_MB" "$DURATION"
        
        echo "$LABEL|$SIZE_MB|$DURATION|0|FAIL" >> "$RESULTS_FILE"
        return 1
    fi
}

# Run all tests
run_all_tests() {
    log_subheader "Running Upload Tests"
    
    # Clear results file
    > "$RESULTS_FILE"
    
    PASSED=0
    FAILED=0
    
    # Print header
    printf "%-10s %5s %4s %8s %s\n" "Test" "Size" "Time" "Speed" "Result"
    printf "%-10s %5s %4s %8s %s\n" "──────────" "─────" "────" "────────" "──────────"
    
    for scenario in "${TEST_SCENARIOS[@]}"; do
        SIZE_MB="${scenario%:*}"
        LABEL="${scenario#*:}"
        
        if test_upload "$SIZE_MB" "$LABEL"; then
            ((PASSED++))
        else
            ((FAILED++))
        fi
    done
    
    echo ""
    echo "═════════════════════════════════════════════════════════════════"
    
    return $FAILED
}

# Display summary
display_summary() {
    local FAILED=$1
    
    log_subheader "Test Summary"
    
    TOTAL=$(wc -l < "$RESULTS_FILE" 2>/dev/null || echo 0)
    PASSED=$((TOTAL - FAILED))
    
    echo -e "Total Tests:  ${CYAN}$TOTAL${NC}"
    echo -e "Passed:       ${GREEN}$PASSED${NC}"
    echo -e "Failed:       ${RED}$FAILED${NC}"
    
    if [ "$FAILED" -eq 0 ]; then
        echo ""
        echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║          ✅ ALL TESTS PASSED - FR-024 VALIDATED                 ║${NC}"
        echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
        return 0
    else
        echo ""
        echo -e "${RED}╔════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${RED}║          ❌ SOME TESTS FAILED                                    ║${NC}"
        echo -e "${RED}╚════════════════════════════════════════════════════════════════╝${NC}"
        return 1
    fi
}

# Print detailed results
print_detailed_results() {
    log_subheader "Detailed Results"
    
    while IFS='|' read -r label size duration speed status; do
        printf "%-10s: %4dMB uploaded in %3ds (%4d MB/s) - %s\n" \
            "$label" "$size" "$duration" "$speed" "$status"
    done < "$RESULTS_FILE"
    
    echo ""
}

# Main
main() {
    echo ""
    log_header "FR-024 Comprehensive Upload Test Suite"
    echo ""
    
    check_prerequisites
    run_all_tests
    FAILED=$?
    
    print_detailed_results
    display_summary "$FAILED"
    
    # Cleanup
    rm -f "$RESULTS_FILE"
    
    return $FAILED
}

# Run
main "$@"
