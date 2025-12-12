#!/bin/bash

################################################################################
# FR-024 Environment Setup and Validation
# Verifies all prerequisites and services are running
# Usage: ./validate-fr024-environment.sh
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
WORKSPACE_ROOT="${WORKSPACE_ROOT:-.}"
FILE_SERVICE_URL="http://localhost:8084"
MINIO_URL="http://localhost:9000"
MONGODB_URL="localhost:27017"

# Counters
CHECKS_PASSED=0
CHECKS_FAILED=0

# Logging
log_header() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC} $1"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

log_check() {
    echo -n "  [*] $1... "
}

log_pass() {
    echo -e "${GREEN}✓${NC}"
    ((CHECKS_PASSED++))
}

log_fail() {
    echo -e "${RED}✗${NC}"
    ((CHECKS_FAILED++))
    [ -n "$1" ] && echo -e "       ${RED}Error: $1${NC}"
}

log_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

# Check system tools
check_system_tools() {
    log_header "System Tools Check"
    
    local tools=("curl" "jq" "dd" "git" "docker" "docker-compose")
    
    for tool in "${tools[@]}"; do
        log_check "Checking $tool"
        if command -v "$tool" &> /dev/null; then
            VERSION=$($tool --version 2>&1 | head -1)
            log_pass
            log_info "  $VERSION"
        else
            log_fail "$tool not installed"
        fi
    done
}

# Check Docker containers
check_docker_containers() {
    log_header "Docker Containers Check"
    
    local containers=("chat4all-file-service" "chat4all-minio" "chat4all-mongodb")
    
    for container in "${containers[@]}"; do
        log_check "Checking $container"
        
        STATUS=$(docker ps --format "{{.Names}} {{.State}}" 2>/dev/null | grep "^$container " | awk '{print $2}')
        
        if [ -z "$STATUS" ]; then
            log_fail "Container not running"
        elif [ "$STATUS" = "running" ]; then
            log_pass
            log_info "  Status: $STATUS"
        else
            log_fail "Container status: $STATUS"
        fi
    done
}

# Check File Service
check_file_service() {
    log_header "File Service Check"
    
    log_check "File Service connectivity"
    if HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$FILE_SERVICE_URL/actuator/health" 2>/dev/null); then
        if [ "$HEALTH" = "200" ]; then
            log_pass
            
            # Check endpoints
            log_check "File initiate endpoint"
            if curl -s "$FILE_SERVICE_URL/api/files/initiate" -X OPTIONS &>/dev/null || \
               curl -s "$FILE_SERVICE_URL/api/files/initiate" -X POST \
                   -H "Content-Type: application/json" \
                   -d '{"filename":"test","fileSize":100,"mimeType":"test"}' &>/dev/null; then
                log_pass
            else
                log_fail "Endpoint not accessible"
            fi
        else
            log_fail "File Service returned HTTP $HEALTH"
        fi
    else
        log_fail "Cannot connect to $FILE_SERVICE_URL"
    fi
}

# Check MinIO
check_minio() {
    log_header "MinIO Check"
    
    log_check "MinIO connectivity"
    if HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$MINIO_URL/minio/health/live" 2>/dev/null); then
        if [ "$HEALTH" = "200" ]; then
            log_pass
        else
            log_fail "MinIO health check returned HTTP $HEALTH"
        fi
    else
        log_fail "Cannot connect to $MINIO_URL"
    fi
}

# Check MongoDB
check_mongodb() {
    log_header "MongoDB Check"
    
    log_check "MongoDB connectivity"
    if timeout 2 bash -c "echo > /dev/tcp/$MONGODB_URL" 2>/dev/null; then
        log_pass
    else
        log_fail "Cannot connect to $MONGODB_URL"
    fi
}

# Check source code
check_source_code() {
    log_header "Source Code Check"
    
    local files=(
        "services/file-service/src/main/java/com/chat4all/file/config/S3Config.java"
        "services/file-service/src/main/resources/application.yml"
        "docker-compose.yml"
    )
    
    for file in "${files[@]}"; do
        log_check "Checking $file"
        if [ -f "$WORKSPACE_ROOT/$file" ]; then
            log_pass
        else
            log_fail "File not found"
        fi
    done
    
    # Check for dual endpoint configuration
    log_check "Dual endpoint configuration (S3_PUBLIC_ENDPOINT)"
    if grep -q "public-endpoint" "$WORKSPACE_ROOT/services/file-service/src/main/resources/application.yml" 2>/dev/null; then
        log_pass
    else
        log_fail "S3_PUBLIC_ENDPOINT not configured"
    fi
}

# Test presigned URL generation
test_presigned_url() {
    log_header "Presigned URL Test"
    
    log_check "Generating presigned URL"
    
    RESPONSE=$(curl -s -X POST "$FILE_SERVICE_URL/api/files/initiate" \
        -H "Content-Type: application/json" \
        -d '{
            "filename": "test-presigned.bin",
            "fileSize": 1048576,
            "mimeType": "application/octet-stream"
        }' 2>/dev/null)
    
    FILE_ID=$(echo "$RESPONSE" | jq -r '.fileId' 2>/dev/null)
    UPLOAD_URL=$(echo "$RESPONSE" | jq -r '.uploadUrl' 2>/dev/null)
    
    if [ -n "$FILE_ID" ] && [ "$FILE_ID" != "null" ] && \
       [ -n "$UPLOAD_URL" ] && [ "$UPLOAD_URL" != "null" ]; then
        log_pass
        
        # Verify URL contains correct endpoint
        if echo "$UPLOAD_URL" | grep -q "localhost:9000"; then
            log_info "Presigned URL uses localhost:9000 (correct for client)"
        fi
    else
        log_fail "Failed to generate presigned URL"
    fi
}

# Display summary
display_summary() {
    local TOTAL=$((CHECKS_PASSED + CHECKS_FAILED))
    
    log_header "Validation Summary"
    
    echo -e "Total Checks:  ${CYAN}$TOTAL${NC}"
    echo -e "Passed:        ${GREEN}$CHECKS_PASSED${NC}"
    echo -e "Failed:        ${RED}$CHECKS_FAILED${NC}"
    echo ""
    
    if [ "$CHECKS_FAILED" -eq 0 ]; then
        echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║        ✅ ENVIRONMENT READY - All checks passed                 ║${NC}"
        echo -e "${GREEN}║                                                                ║${NC}"
        echo -e "${GREEN}║  You can now run:                                              ║${NC}"
        echo -e "${GREEN}║    ./test-fr024-upload.sh [size-mb] [filename]                 ║${NC}"
        echo -e "${GREEN}║    ./test-fr024-comprehensive.sh                               ║${NC}"
        echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
        return 0
    else
        echo -e "${RED}╔════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${RED}║        ❌ ENVIRONMENT ISSUES DETECTED                            ║${NC}"
        echo -e "${RED}║                                                                ║${NC}"
        echo -e "${RED}║  Please fix the above issues and try again.                     ║${NC}"
        echo -e "${RED}╚════════════════════════════════════════════════════════════════╝${NC}"
        return 1
    fi
}

# Main execution
main() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║       FR-024 Environment Validation and Setup                  ║${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
    
    # Change to workspace if provided
    if [ -d "$WORKSPACE_ROOT" ]; then
        cd "$WORKSPACE_ROOT"
        log_info "Workspace: $(pwd)"
    fi
    
    echo ""
    
    check_system_tools
    check_docker_containers
    check_file_service
    check_minio
    check_mongodb
    check_source_code
    test_presigned_url
    
    display_summary
}

# Run
main "$@"
