#!/bin/bash
#
# Script para gerar relatório consolidado de teste de carga
# Coleta logs de todos os serviços e métricas do K6
#
# Uso: ./generate-load-test-report.sh
#

set -e

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
REPORT_DIR="performance-tests/reports"
LOG_DIR="logs/performance-tests"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Gerando Relatório de Teste de Carga${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Criar diretórios
mkdir -p "$REPORT_DIR" "$LOG_DIR"

echo -e "${GREEN}[1/5]${NC} Coletando logs do API Gateway..."
docker logs chat4all-api-gateway --tail 200 > "$LOG_DIR/api-gateway-$TIMESTAMP.log" 2>&1
echo "      ✓ Salvo em $LOG_DIR/api-gateway-$TIMESTAMP.log"

echo -e "${GREEN}[2/5]${NC} Coletando logs do Message Service..."
docker logs chat4all-message-service --tail 200 > "$LOG_DIR/message-service-$TIMESTAMP.log" 2>&1
echo "      ✓ Salvo em $LOG_DIR/message-service-$TIMESTAMP.log"

echo -e "${GREEN}[3/5]${NC} Coletando logs do User Service..."
docker logs chat4all-user-service --tail 100 > "$LOG_DIR/user-service-$TIMESTAMP.log" 2>&1
echo "      ✓ Salvo em $LOG_DIR/user-service-$TIMESTAMP.log"

echo -e "${GREEN}[4/5]${NC} Coletando logs do File Service..."
docker logs chat4all-file-service --tail 100 > "$LOG_DIR/file-service-$TIMESTAMP.log" 2>&1
echo "      ✓ Salvo em $LOG_DIR/file-service-$TIMESTAMP.log"

echo -e "${GREEN}[5/5]${NC} Coletando status dos containers..."
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep chat4all > "$LOG_DIR/containers-status-$TIMESTAMP.log"
echo "      ✓ Salvo em $LOG_DIR/containers-status-$TIMESTAMP.log"

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✓ Relatório gerado com sucesso!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "📁 Arquivos gerados:"
echo ""
echo "  Logs dos serviços:"
echo "    - $LOG_DIR/api-gateway-$TIMESTAMP.log"
echo "    - $LOG_DIR/message-service-$TIMESTAMP.log"
echo "    - $LOG_DIR/user-service-$TIMESTAMP.log"
echo "    - $LOG_DIR/file-service-$TIMESTAMP.log"
echo "    - $LOG_DIR/containers-status-$TIMESTAMP.log"
echo ""
echo "  Relatórios K6:"
echo "    - performance-tests/results/*.json"
echo "    - performance-tests/reports/LOAD_TEST_REPORT.md"
echo ""
echo "📊 Para visualizar métricas:"
echo "    - Prometheus: http://localhost:9090"
echo "    - Grafana:    http://localhost:3000"
echo "    - Jaeger:     http://localhost:16686"
echo ""
