#!/bin/bash
#
# Quick K6 performance test runner
# Usage: ./run-k6-test.sh [test-type] [vus]
#
# Examples:
#   ./run-k6-test.sh smoke
#   ./run-k6-test.sh load 1000
#   ./run-k6-test.sh spike
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
TEST_TYPE="${1:-smoke}"
VUS="${2:-10000}"
DURATION="${3:-5m}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    log_error "K6 not found. Install it first:"
    echo ""
    echo "  Linux:"
    echo "    sudo apt-get install k6"
    echo ""
    echo "  macOS:"
    echo "    brew install k6"
    echo ""
    echo "  Docker:"
    echo "    docker pull grafana/k6:latest"
    exit 1
fi

# Check API Gateway
log_info "Checking API Gateway at ${BASE_URL}..."
if ! curl -sf "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
    log_error "API Gateway not reachable at ${BASE_URL}"
    log_warn "Start services with: docker-compose up -d"
    exit 1
fi
log_info "✓ API Gateway is healthy"

# Select test script
case "$TEST_TYPE" in
    smoke|s)
        SCRIPT="scenarios/smoke-test.js"
        log_info "Running smoke test (10 users, 1 minute)..."
        ;;
    load|l|concurrent|c)
        SCRIPT="scenarios/concurrent-conversations.js"
        log_info "Running load test (${VUS} users, ${DURATION})..."
        ;;
    spike|sp)
        SCRIPT="scenarios/spike-test.js"
        log_info "Running spike test..."
        ;;
    *)
        log_error "Unknown test type: $TEST_TYPE"
        echo ""
        echo "Available tests:"
        echo "  smoke (s)      - Quick validation (10 users, 1 min)"
        echo "  load (l)       - 10K concurrent conversations [DEFAULT]"
        echo "  spike (sp)     - Sudden traffic surge test"
        exit 1
        ;;
esac

# Run K6 test
log_info "========================================="
log_info "Chat4All Performance Test (K6)"
log_info "========================================="
log_info "Test:     $TEST_TYPE"
log_info "Script:   $SCRIPT"
log_info "Base URL: $BASE_URL"
log_info "VUs:      $VUS"
log_info "Duration: $DURATION"
log_info "========================================="

cd "$SCRIPT_DIR"
k6 run "$SCRIPT" \
  -e BASE_URL="$BASE_URL" \
  -e VUS="$VUS" \
  -e DURATION="$DURATION"

if [ $? -eq 0 ]; then
    log_info "✓ Performance test completed successfully"
else
    log_error "Performance test failed"
    exit 1
fi
