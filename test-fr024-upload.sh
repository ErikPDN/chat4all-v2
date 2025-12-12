#!/bin/bash

################################################################################
# FR-024 Upload Test Script
# Test file upload functionality (100MB and 2GB)
# Usage: ./test-fr024-upload.sh [size-mb] [file-name]
################################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
FILE_SERVICE_URL="${FILE_SERVICE_URL:-http://localhost:8084}"
TEST_SIZE_MB="${1:-100}"  # Default 100MB, can be overridden
TEST_FILENAME="${2:-test-${TEST_SIZE_MB}mb.bin}"
TEST_SIZE_BYTES=$((TEST_SIZE_MB * 1024 * 1024))

# Logging functions
log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Banner
print_banner() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║           FR-024 Upload Test - ${TEST_SIZE_MB}MB File Upload           ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo ""
}

# Check if File Service is running
check_file_service() {
    log_info "Checking File Service health..."
    
    HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$FILE_SERVICE_URL/actuator/health" 2>/dev/null)
    
    if [ "$HEALTH" != "200" ]; then
        log_error "File Service not responding at $FILE_SERVICE_URL"
        log_info "Expected: http://localhost:8084"
        exit 1
    fi
    
    log_success "File Service is running"
}

# Request presigned URL
get_presigned_url() {
    log_info "Requesting presigned URL for ${TEST_SIZE_MB}MB file..."
    
    RESPONSE=$(curl -s -X POST "$FILE_SERVICE_URL/api/files/initiate" \
        -H "Content-Type: application/json" \
        -d "{
            \"filename\": \"$TEST_FILENAME\",
            \"fileSize\": $TEST_SIZE_BYTES,
            \"mimeType\": \"application/octet-stream\"
        }" 2>/dev/null)
    
    FILE_ID=$(echo "$RESPONSE" | jq -r '.fileId' 2>/dev/null)
    UPLOAD_URL=$(echo "$RESPONSE" | jq -r '.uploadUrl' 2>/dev/null)
    
    if [ -z "$FILE_ID" ] || [ "$FILE_ID" = "null" ]; then
        log_error "Failed to get presigned URL"
        echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
        exit 1
    fi
    
    log_success "Presigned URL received"
    log_info "File ID: $FILE_ID"
    
    # Extract and display endpoint
    ENDPOINT=$(echo "$UPLOAD_URL" | grep -o 'http://[^/]*' | head -1)
    log_info "Upload Endpoint: $ENDPOINT"
}

# Perform upload
perform_upload() {
    log_info "Starting ${TEST_SIZE_MB}MB file upload..."
    log_info "This may take a moment depending on your network speed..."
    echo ""
    
    START_TIME=$(date +%s%N)
    START_EPOCH=$(date +%s)
    
    HTTP_CODE=$(curl -w "%{http_code}" -s -X PUT "$UPLOAD_URL" \
        -H "Content-Type: application/octet-stream" \
        --data-binary @<(dd if=/dev/zero bs=1M count=$TEST_SIZE_MB 2>/dev/null) \
        -o /tmp/upload-response.txt 2>/dev/null)
    
    END_TIME=$(date +%s%N)
    END_EPOCH=$(date +%s)
    
    # Calculate duration
    DURATION_NS=$((END_TIME - START_TIME))
    DURATION_S=$((END_EPOCH - START_EPOCH))
    
    if [ $DURATION_S -lt 1 ]; then
        DURATION_S=1
    fi
    
    echo ""
}

# Calculate and display results
display_results() {
    if [ "$HTTP_CODE" = "200" ]; then
        log_success "Upload successful (HTTP $HTTP_CODE)"
        echo ""
        
        # Calculate speed
        SPEED_MBS=$((TEST_SIZE_MB / DURATION_S))
        if [ $SPEED_MBS -lt 1 ]; then
            SPEED_MBS=1
        fi
        
        echo "╔════════════════════════════════════════════════════════════════╗"
        echo "║                    UPLOAD RESULTS                              ║"
        echo "╠════════════════════════════════════════════════════════════════╣"
        echo "║ File Size:        ${TEST_SIZE_MB}MB                                        │"
        echo "║ Duration:         ${DURATION_S}s                                          │"
        echo "║ Upload Speed:     ${SPEED_MBS} MB/s                                     │"
        echo "║ HTTP Status:      200 OK                                       │"
        echo "║ Status:           ✅ SUCCESS                                   │"
        echo "╚════════════════════════════════════════════════════════════════╝"
        echo ""
        log_success "FR-024 upload validation passed!"
        return 0
    else
        log_error "Upload failed (HTTP $HTTP_CODE)"
        echo ""
        log_info "Response:"
        if [ -f /tmp/upload-response.txt ]; then
            head -20 /tmp/upload-response.txt
        fi
        return 1
    fi
}

# Cleanup
cleanup() {
    rm -f /tmp/upload-response.txt
}

# Main execution
main() {
    trap cleanup EXIT
    
    print_banner
    
    # Validate input
    if ! [[ "$TEST_SIZE_MB" =~ ^[0-9]+$ ]]; then
        log_error "Invalid size: must be a number (in MB)"
        exit 1
    fi
    
    if [ "$TEST_SIZE_MB" -lt 1 ]; then
        log_error "Size must be at least 1MB"
        exit 1
    fi
    
    # Checks and upload
    check_file_service
    get_presigned_url
    perform_upload
    display_results
}

# Run main
main "$@"
