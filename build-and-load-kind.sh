#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Cluster name
CLUSTER_NAME="${KIND_CLUSTER_NAME:-chat4all-cluster}"

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     Chat4All v2 - Build and Load to Kind                      ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if Kind cluster exists
echo -e "${YELLOW}🔍 Checking Kind cluster '${CLUSTER_NAME}'...${NC}"
if ! kind get clusters | grep -q "^${CLUSTER_NAME}$"; then
    echo -e "${RED}❌ Kind cluster '${CLUSTER_NAME}' not found!${NC}"
    echo -e "${YELLOW}💡 Create it with: kind create cluster --name ${CLUSTER_NAME}${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Kind cluster found${NC}"
echo ""

# Step 1: Maven Build
echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Step 1: Building all services with Maven${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo ""

mvn clean package -DskipTests

echo ""
echo -e "${GREEN}✓ Maven build completed successfully${NC}"
echo ""

# Step 2: Docker Build and Kind Load
echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Step 2: Building Docker images and loading to Kind${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo ""

# Define services array with name and path
declare -A services=(
    ["api-gateway"]="services/api-gateway"
    ["message-service"]="services/message-service"
    ["router-service"]="services/router-service"
    ["user-service"]="services/user-service"
    ["file-service"]="services/file-service"
    ["whatsapp-connector"]="services/connectors/whatsapp-connector"
    ["telegram-connector"]="services/connectors/telegram-connector"
    ["instagram-connector"]="services/connectors/instagram-connector"
)

# Counter for progress
total=${#services[@]}
current=0

for service_name in "${!services[@]}"; do
    ((current++))
    service_path="${services[$service_name]}"
    image_name="chat4all/${service_name}:latest"
    
    echo -e "${YELLOW}[${current}/${total}] Processing ${service_name}...${NC}"
    
    # Docker build
    echo -e "  ${BLUE}🐳 Building Docker image: ${image_name}${NC}"
    if docker build -t "${image_name}" "${service_path}" > /dev/null 2>&1; then
        echo -e "  ${GREEN}✓ Docker build successful${NC}"
    else
        echo -e "  ${RED}✗ Docker build failed${NC}"
        exit 1
    fi
    
    # Kind load
    echo -e "  ${BLUE}📦 Loading image to Kind cluster: ${CLUSTER_NAME}${NC}"
    if kind load docker-image "${image_name}" --name "${CLUSTER_NAME}" > /dev/null 2>&1; then
        echo -e "  ${GREEN}✓ Image loaded to Kind${NC}"
    else
        echo -e "  ${RED}✗ Failed to load image to Kind${NC}"
        exit 1
    fi
    
    echo ""
done

# Summary
echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}  Summary${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${GREEN}✓ All ${total} services built and loaded successfully!${NC}"
echo ""
echo -e "${YELLOW}Images loaded to Kind cluster '${CLUSTER_NAME}':${NC}"
for service_name in "${!services[@]}"; do
    echo -e "  • chat4all/${service_name}:latest"
done
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo -e "  1. Deploy to Kubernetes:"
echo -e "     ${YELLOW}kubectl apply -k infrastructure/kubernetes/base/${NC}"
echo -e ""
echo -e "  2. Deploy to dev environment:"
echo -e "     ${YELLOW}kubectl apply -k infrastructure/kubernetes/overlays/dev/${NC}"
echo -e ""
echo -e "  3. Check pods:"
echo -e "     ${YELLOW}kubectl get pods -l app.kubernetes.io/platform=chat4all${NC}"
echo -e ""
echo -e "${GREEN}🎉 Build and load completed!${NC}"
