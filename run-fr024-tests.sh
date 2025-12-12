#!/bin/bash

################################################################################
# FR-024 Upload Testing - Main Entry Point
# Easy command to run all FR-024 related tests
# Usage: ./run-fr024-tests.sh [command]
################################################################################

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Help message
show_help() {
    cat << EOF
${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}
${CYAN}║          FR-024 Upload Testing - Main Entry Point             ║${NC}
${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}

${GREEN}USAGE:${NC}
  ./run-fr024-tests.sh [COMMAND] [OPTIONS]

${GREEN}COMMANDS:${NC}

  ${YELLOW}help${NC}              Show this help message

  ${YELLOW}validate${NC}          Validate environment setup
                    Checks: tools, containers, connectivity, config
                    Usage: ./run-fr024-tests.sh validate

  ${YELLOW}quick${NC}             Quick test (100MB upload)
                    Usage: ./run-fr024-tests.sh quick

  ${YELLOW}full${NC}              Full test (2GB upload)
                    Usage: ./run-fr024-tests.sh full

  ${YELLOW}test SIZE${NC}         Test with custom size (in MB)
                    Usage: ./run-fr024-tests.sh test 500
                    Usage: ./run-fr024-tests.sh test 2048

  ${YELLOW}suite${NC}             Run comprehensive test suite
                    Tests: 10MB, 100MB, 500MB, 1GB, 2GB
                    Usage: ./run-fr024-tests.sh suite

  ${YELLOW}setup${NC}             Start Docker containers
                    Usage: ./run-fr024-tests.sh setup

  ${YELLOW}status${NC}            Check containers and services status
                    Usage: ./run-fr024-tests.sh status

${GREEN}EXAMPLES:${NC}

  # Validate environment before testing
  ${BLUE}./run-fr024-tests.sh validate${NC}

  # Start docker containers
  ${BLUE}./run-fr024-tests.sh setup${NC}

  # Run quick test (100MB)
  ${BLUE}./run-fr024-tests.sh quick${NC}

  # Test with 2GB file
  ${BLUE}./run-fr024-tests.sh full${NC}

  # Test with custom 500MB file
  ${BLUE}./run-fr024-tests.sh test 500${NC}

  # Run comprehensive test suite (all sizes)
  ${BLUE}./run-fr024-tests.sh suite${NC}

${GREEN}TYPICAL WORKFLOW:${NC}

  1. Setup environment:
     ${BLUE}./run-fr024-tests.sh setup${NC}

  2. Validate setup:
     ${BLUE}./run-fr024-tests.sh validate${NC}

  3. Run tests:
     ${BLUE}./run-fr024-tests.sh quick      # quick validation${NC}
     ${BLUE}./run-fr024-tests.sh full       # complete validation (2GB)${NC}
     ${BLUE}./run-fr024-tests.sh suite      # comprehensive suite${NC}

${GREEN}FOR MORE INFORMATION:${NC}

  See TEST_SUITE_README.md for detailed documentation

EOF
}

# Validate environment
cmd_validate() {
    echo ""
    if [ -f "$SCRIPT_DIR/validate-fr024-environment.sh" ]; then
        "$SCRIPT_DIR/validate-fr024-environment.sh"
    else
        echo -e "${RED}✗ Script not found: validate-fr024-environment.sh${NC}"
        exit 1
    fi
}

# Quick test (100MB)
cmd_quick() {
    echo ""
    echo -e "${CYAN}Running quick test (100MB)...${NC}"
    echo ""
    if [ -f "$SCRIPT_DIR/test-fr024-upload.sh" ]; then
        "$SCRIPT_DIR/test-fr024-upload.sh" 100
    else
        echo -e "${RED}✗ Script not found: test-fr024-upload.sh${NC}"
        exit 1
    fi
}

# Full test (2GB)
cmd_full() {
    echo ""
    echo -e "${CYAN}Running full test (2GB - FR-024 maximum)...${NC}"
    echo ""
    if [ -f "$SCRIPT_DIR/test-fr024-upload.sh" ]; then
        "$SCRIPT_DIR/test-fr024-upload.sh" 2048
    else
        echo -e "${RED}✗ Script not found: test-fr024-upload.sh${NC}"
        exit 1
    fi
}

# Custom size test
cmd_test() {
    local SIZE=$1
    
    if [ -z "$SIZE" ]; then
        echo -e "${RED}✗ Size not specified${NC}"
        echo -e "${YELLOW}Usage: ./run-fr024-tests.sh test SIZE_MB${NC}"
        echo -e "${YELLOW}Example: ./run-fr024-tests.sh test 500${NC}"
        exit 1
    fi
    
    if ! [[ "$SIZE" =~ ^[0-9]+$ ]]; then
        echo -e "${RED}✗ Invalid size: $SIZE (must be a number)${NC}"
        exit 1
    fi
    
    echo ""
    echo -e "${CYAN}Running test with ${SIZE}MB file...${NC}"
    echo ""
    if [ -f "$SCRIPT_DIR/test-fr024-upload.sh" ]; then
        "$SCRIPT_DIR/test-fr024-upload.sh" "$SIZE"
    else
        echo -e "${RED}✗ Script not found: test-fr024-upload.sh${NC}"
        exit 1
    fi
}

# Comprehensive suite
cmd_suite() {
    echo ""
    echo -e "${CYAN}Running comprehensive test suite...${NC}"
    echo ""
    if [ -f "$SCRIPT_DIR/test-fr024-comprehensive.sh" ]; then
        "$SCRIPT_DIR/test-fr024-comprehensive.sh"
    else
        echo -e "${RED}✗ Script not found: test-fr024-comprehensive.sh${NC}"
        exit 1
    fi
}

# Setup docker containers
cmd_setup() {
    echo ""
    echo -e "${CYAN}Starting Docker containers...${NC}"
    echo ""
    
    if ! command -v docker-compose &> /dev/null; then
        echo -e "${RED}✗ docker-compose not found${NC}"
        exit 1
    fi
    
    cd "$SCRIPT_DIR" || exit 1
    
    echo -e "${BLUE}Starting: minio, mongodb, file-service${NC}"
    docker-compose up -d minio mongodb file-service
    
    echo ""
    echo -e "${YELLOW}Waiting for services to start (10 seconds)...${NC}"
    sleep 10
    
    echo ""
    echo -e "${GREEN}✓ Containers started${NC}"
    echo ""
    echo -e "${BLUE}Checking status...${NC}"
    docker-compose ps | grep -E "minio|mongodb|file-service"
    
    echo ""
    echo -e "${CYAN}Next steps:${NC}"
    echo -e "  1. Validate: ${BLUE}./run-fr024-tests.sh validate${NC}"
    echo -e "  2. Test: ${BLUE}./run-fr024-tests.sh quick${NC}"
}

# Check status
cmd_status() {
    echo ""
    echo -e "${CYAN}Service Status:${NC}"
    echo ""
    
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}✗ docker not found${NC}"
        exit 1
    fi
    
    cd "$SCRIPT_DIR" || exit 1
    
    # Check containers
    echo -e "${BLUE}Docker Containers:${NC}"
    docker-compose ps | grep -E "minio|mongodb|file-service" || echo "No containers running"
    
    echo ""
    echo -e "${BLUE}Service Health:${NC}"
    
    # Check File Service
    if curl -s http://localhost:8084/actuator/health | jq .status 2>/dev/null | grep -q "UP"; then
        echo -e "  ${GREEN}✓${NC} File Service (http://localhost:8084)"
    else
        echo -e "  ${RED}✗${NC} File Service (http://localhost:8084) - Not responding"
    fi
    
    # Check MinIO
    if curl -s http://localhost:9000/minio/health/live 2>/dev/null | grep -q "."; then
        echo -e "  ${GREEN}✓${NC} MinIO (http://localhost:9000)"
    else
        echo -e "  ${RED}✗${NC} MinIO (http://localhost:9000) - Not responding"
    fi
    
    # Check MongoDB
    if timeout 2 bash -c "echo > /dev/tcp/localhost/27017" 2>/dev/null; then
        echo -e "  ${GREEN}✓${NC} MongoDB (localhost:27017)"
    else
        echo -e "  ${RED}✗${NC} MongoDB (localhost:27017) - Not responding"
    fi
    
    echo ""
}

# Main
main() {
    local COMMAND="${1:-help}"
    
    case "$COMMAND" in
        help)
            show_help
            ;;
        validate)
            cmd_validate
            ;;
        quick)
            cmd_quick
            ;;
        full)
            cmd_full
            ;;
        test)
            cmd_test "$2"
            ;;
        suite)
            cmd_suite
            ;;
        setup)
            cmd_setup
            ;;
        status)
            cmd_status
            ;;
        *)
            echo -e "${RED}✗ Unknown command: $COMMAND${NC}"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# Run
main "$@"
