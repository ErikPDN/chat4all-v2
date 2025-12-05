#!/bin/bash
#
# Quick test runner for 10,000 req/min target
# Usage: ./test-10k-rpm.sh [duration] [target_rpm]
#
# Examples:
#   ./test-10k-rpm.sh              # 5 minutes, 10,000 req/min
#   ./test-10k-rpm.sh 2m           # 2 minutes, 10,000 req/min
#   ./test-10k-rpm.sh 10m 15000    # 10 minutes, 15,000 req/min
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
DURATION="${1:-5m}"
TARGET_RPM="${2:-10000}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
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

log_title() {
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}$1${NC}"
    echo -e "${CYAN}========================================${NC}"
}

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    log_error "K6 not found. Install it first:"
    echo ""
    echo "  ${BLUE}Ubuntu/Debian:${NC}"
    echo "    sudo gpg -k"
    echo "    sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69"
    echo "    echo \"deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main\" | sudo tee /etc/apt/sources.list.d/k6.list"
    echo "    sudo apt-get update"
    echo "    sudo apt-get install k6"
    echo ""
    echo "  ${BLUE}macOS:${NC}"
    echo "    brew install k6"
    echo ""
    echo "  ${BLUE}Docker:${NC}"
    echo "    alias k6='docker run --rm -i --network=host grafana/k6:latest'"
    exit 1
fi

# Check API Gateway
log_info "Checking API Gateway at ${BASE_URL}..."
if ! curl -sf "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
    log_error "API Gateway not reachable at ${BASE_URL}"
    log_warn "Start services with: ${BLUE}docker-compose up -d${NC}"
    exit 1
fi
log_info "✓ API Gateway is healthy"

# Calculate target RPS
TARGET_RPS=$((TARGET_RPM / 60))

# Display test configuration
log_title "K6 Performance Test: 10,000 req/min"
echo ""
echo "  ${BLUE}Base URL:${NC}      $BASE_URL"
echo "  ${BLUE}Duration:${NC}      $DURATION"
echo "  ${BLUE}Target:${NC}        $TARGET_RPM req/min ($TARGET_RPS req/s)"
echo "  ${BLUE}Test Type:${NC}     Constant Arrival Rate (precise RPS)"
echo "  ${BLUE}Distribution:${NC}  50% POST messages, 30% GET history, 15% health, 5% webhooks"
echo ""

# Ask for confirmation
read -p "$(echo -e ${YELLOW}Start test? [y/N]:${NC} )" -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    log_warn "Test cancelled"
    exit 0
fi

# Run K6 test
log_info "Starting K6 performance test..."
echo ""

cd "$SCRIPT_DIR/performance-tests"

k6 run scenarios/target-10k-rpm.js \
  -e BASE_URL="$BASE_URL" \
  -e TARGET_RPM="$TARGET_RPM" \
  -e DURATION="$DURATION" \
  --out json=results/test-10k-rpm-$(date +%Y%m%d-%H%M%S).json

EXIT_CODE=$?

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    log_title "✓ Test Completed Successfully"
    echo ""
    log_info "Performance target MET: $TARGET_RPM req/min"
    log_info "All thresholds passed:"
    echo "  • P95 latency < 500ms ✓"
    echo "  • Error rate < 1% ✓"
    echo "  • Throughput >= ${TARGET_RPS} req/s ✓"
    echo ""
else
    log_title "✗ Test Failed"
    echo ""
    log_error "Performance target NOT met or thresholds failed"
    log_warn "Check the output above for details"
    echo ""
    echo "Common issues:"
    echo "  1. Service not scaled - increase replicas in docker-compose.yml"
    echo "  2. Database bottleneck - check PostgreSQL/MongoDB connections"
    echo "  3. Kafka lag - verify broker capacity"
    echo "  4. Network limits - check connection pool sizes"
    exit 1
fi
