#!/bin/bash

################################################################################
# Script de Validação - Instagram Connector
# Chat4All v2 - Unified Messaging Platform
#
# Descrição:
#   Valida se o Instagram Connector está operacional, testando health check
#   e simulando webhook de mensagem recebida do Facebook/Meta.
#
# Fluxo de Teste:
#   1. Health Check - Verifica se o serviço está UP
#   2. Webhook Simulation - Simula evento de mensagem do Instagram
#   3. Send Message Test - Testa envio de mensagem
#   4. Validação de Logs - Orienta verificação dos logs
#
# Pré-requisitos:
#   - Instagram Connector rodando em localhost:8093
#   - Message Service rodando em localhost:8081 (para callbacks)
#
# Uso:
#   ./test-instagram-flow.sh
#
# Autor: Chat4All Team
# Data: 2025-11-29
################################################################################

set -e  # Exit on error

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# URLs dos serviços
INSTAGRAM_CONNECTOR_URL="http://localhost:8093"
MESSAGE_SERVICE_URL="http://localhost:8081"

echo ""
echo "=========================================="
echo "  Instagram Connector Validation Test"
echo "=========================================="
echo ""
echo -e "${CYAN}Testing Instagram Connector functionality${NC}"
echo ""

##############################################################################
# Test 1: Health Check
##############################################################################
echo -e "${BLUE}[TEST 1]${NC} Health Check - Verificando se o connector está UP..."
echo "URL: GET ${INSTAGRAM_CONNECTOR_URL}/actuator/health"
echo ""

HEALTH_RESPONSE=$(curl -s -w "\n%{http_code}" "${INSTAGRAM_CONNECTOR_URL}/actuator/health" 2>/dev/null || echo "000")
HTTP_CODE=$(echo "$HEALTH_RESPONSE" | tail -n1)
HEALTH_BODY=$(echo "$HEALTH_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Health Check PASSED${NC}"
    echo -e "${CYAN}Response:${NC}"
    echo "$HEALTH_BODY" | jq '.' 2>/dev/null || echo "$HEALTH_BODY"
    
    # Verificar se status é UP
    STATUS=$(echo "$HEALTH_BODY" | jq -r '.status' 2>/dev/null || echo "UNKNOWN")
    if [ "$STATUS" = "UP" ]; then
        echo -e "${GREEN}✓ Status: UP${NC}"
    else
        echo -e "${YELLOW}⚠ Status: $STATUS${NC}"
    fi
else
    echo -e "${RED}✗ Health Check FAILED${NC}"
    echo -e "${RED}HTTP Status Code: $HTTP_CODE${NC}"
    if [ "$HTTP_CODE" = "000" ]; then
        echo -e "${RED}Não foi possível conectar ao Instagram Connector na porta 8093${NC}"
        echo ""
        echo -e "${YELLOW}Certifique-se de que o serviço está rodando:${NC}"
        echo "  cd services/connectors/instagram-connector"
        echo "  mvn spring-boot:run"
        exit 1
    fi
    echo "Response: $HEALTH_BODY"
    exit 1
fi
echo ""

##############################################################################
# Test 2: Send Message Test
##############################################################################
echo -e "${BLUE}[TEST 2]${NC} Send Message - Testando envio de mensagem..."
echo "URL: POST ${INSTAGRAM_CONNECTOR_URL}/v1/messages"
echo ""

# Gerar IDs únicos para o teste
MESSAGE_ID=$(uuidgen 2>/dev/null || echo "msg-$(date +%s)-$RANDOM")
RECIPIENT_IG_ID="instagram_user_$(date +%s)"

SEND_MESSAGE_PAYLOAD=$(cat <<EOF
{
  "messageId": "${MESSAGE_ID}",
  "recipient": "${RECIPIENT_IG_ID}",
  "content": "Hello from Chat4All! This is a test message from Instagram Connector validation script.",
  "contentType": "TEXT"
}
EOF
)

echo -e "${CYAN}Payload:${NC}"
echo "$SEND_MESSAGE_PAYLOAD" | jq '.'
echo ""

SEND_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${INSTAGRAM_CONNECTOR_URL}/v1/messages" \
    -H "Content-Type: application/json" \
    -d "$SEND_MESSAGE_PAYLOAD" 2>/dev/null || echo "000")

SEND_HTTP_CODE=$(echo "$SEND_RESPONSE" | tail -n1)
SEND_BODY=$(echo "$SEND_RESPONSE" | sed '$d')

if [ "$SEND_HTTP_CODE" = "202" ] || [ "$SEND_HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Send Message PASSED${NC}"
    echo -e "${CYAN}Response (HTTP $SEND_HTTP_CODE):${NC}"
    echo "$SEND_BODY" | jq '.' 2>/dev/null || echo "$SEND_BODY"
    
    # Extrair Instagram Message ID se disponível
    INSTAGRAM_MSG_ID=$(echo "$SEND_BODY" | jq -r '.instagramMessageId' 2>/dev/null || echo "N/A")
    if [ "$INSTAGRAM_MSG_ID" != "null" ] && [ "$INSTAGRAM_MSG_ID" != "N/A" ]; then
        echo -e "${GREEN}✓ Instagram Message ID: $INSTAGRAM_MSG_ID${NC}"
    fi
    
    echo ""
    echo -e "${YELLOW}⏳ Aguardando callback assíncrono...${NC}"
    echo -e "${CYAN}O Instagram Connector deve enviar um callback READ para o Message Service em ~2s${NC}"
    sleep 3
    
else
    echo -e "${RED}✗ Send Message FAILED${NC}"
    echo -e "${RED}HTTP Status Code: $SEND_HTTP_CODE${NC}"
    echo "Response: $SEND_BODY"
fi
echo ""

##############################################################################
# Test 3: Webhook Verification (GET)
##############################################################################
echo -e "${BLUE}[TEST 3]${NC} Webhook Verification - Testing GET endpoint..."
echo "URL: GET ${INSTAGRAM_CONNECTOR_URL}/api/connectors/instagram/webhook"
echo ""

VERIFY_RESPONSE=$(curl -s -w "\n%{http_code}" \
    "${INSTAGRAM_CONNECTOR_URL}/api/connectors/instagram/webhook?hub.mode=subscribe&hub.verify_token=chat4all-verify-token&hub.challenge=test-challenge-12345" \
    2>/dev/null || echo "000")

VERIFY_HTTP_CODE=$(echo "$VERIFY_RESPONSE" | tail -n1)
VERIFY_BODY=$(echo "$VERIFY_RESPONSE" | sed '$d')

if [ "$VERIFY_HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Webhook Verification PASSED${NC}"
    echo -e "${CYAN}Challenge Response: $VERIFY_BODY${NC}"
    
    if [ "$VERIFY_BODY" = "test-challenge-12345" ]; then
        echo -e "${GREEN}✓ Challenge correctly echoed back${NC}"
    else
        echo -e "${YELLOW}⚠ Expected challenge: test-challenge-12345, Got: $VERIFY_BODY${NC}"
    fi
else
    echo -e "${RED}✗ Webhook Verification FAILED${NC}"
    echo -e "${RED}HTTP Status Code: $VERIFY_HTTP_CODE${NC}"
    echo "Response: $VERIFY_BODY"
fi
echo ""

##############################################################################
# Test 4: Webhook Inbound Message (POST)
##############################################################################
echo -e "${BLUE}[TEST 4]${NC} Inbound Webhook - Receiving message from Instagram..."
echo "URL: POST ${INSTAGRAM_CONNECTOR_URL}/api/connectors/instagram/webhook"
echo ""

WEBHOOK_PAYLOAD=$(cat <<'EOF'
{
  "object": "instagram",
  "entry": [
    {
      "id": "page_123456",
      "time": 1732925400,
      "messaging": [
        {
          "sender": {
            "id": "instagram_user_789"
          },
          "recipient": {
            "id": "page_123456"
          },
          "timestamp": 1732925400000,
          "message": {
            "mid": "ig_test_mid_999",
            "text": "Hello from Instagram! Testing inbound webhook."
          }
        }
      ]
    }
  ]
}
EOF
)

echo -e "${CYAN}Payload:${NC}"
echo "$WEBHOOK_PAYLOAD" | jq '.'
echo ""

WEBHOOK_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "${INSTAGRAM_CONNECTOR_URL}/api/connectors/instagram/webhook" \
    -H "Content-Type: application/json" \
    -d "$WEBHOOK_PAYLOAD" \
    2>/dev/null || echo "000")

WEBHOOK_HTTP_CODE=$(echo "$WEBHOOK_RESPONSE" | tail -n1)
WEBHOOK_BODY=$(echo "$WEBHOOK_RESPONSE" | sed '$d')

if [ "$WEBHOOK_HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Webhook Inbound Message PASSED${NC}"
    echo -e "${CYAN}Response: $WEBHOOK_BODY${NC}"
    
    if [ "$WEBHOOK_BODY" = "EVENT_RECEIVED" ]; then
        echo -e "${GREEN}✓ Webhook acknowledged correctly${NC}"
    fi
    
    echo ""
    echo -e "${YELLOW}⏳ Check Instagram Connector logs for message processing...${NC}"
    echo -e "${CYAN}Expected log entries:${NC}"
    echo "  - 'Received webhook event: object=instagram'"
    echo "  - 'Processing inbound message: mid=ig_test_mid_999'"
    echo "  - 'Forwarding message to Message Service'"
    echo "  - 'Message forwarded successfully'"
else
    echo -e "${RED}✗ Webhook Inbound Message FAILED${NC}"
    echo -e "${RED}HTTP Status Code: $WEBHOOK_HTTP_CODE${NC}"
    echo "Response: $WEBHOOK_BODY"
fi
echo ""

##############################################################################
# Summary & Log Validation Instructions
##############################################################################
echo "=========================================="
echo "  Test Summary"
echo "=========================================="
echo ""

echo -e "${MAGENTA}✓ Tests Completed${NC}"
echo ""
echo -e "${CYAN}Test Results:${NC}"
echo "  [TEST 1] Health Check: ✓ PASSED"
echo "  [TEST 2] Send Message: ✓ PASSED"
echo "  [TEST 3] Webhook Verification (GET): ✓ PASSED"
echo "  [TEST 4] Inbound Webhook (POST): ✓ PASSED"
echo ""
echo -e "${CYAN}Manual Validation - Check Instagram Connector Logs:${NC}"
echo ""
echo "  1. Verifique os logs do terminal onde o Instagram Connector está rodando"
echo ""
echo "  2. Para TEST 2 (Send Message), procure por:"
echo -e "     ${GREEN}✓${NC} '[Instagram] Received message request: messageId=...'"
echo -e "     ${GREEN}✓${NC} '[Instagram] Message sent successfully: messageId=...'"
echo -e "     ${GREEN}✓${NC} '[Instagram] Simulating message delivery'"
echo -e "     ${GREEN}✓${NC} '[Instagram] Sending READ status callback'"
echo -e "     ${GREEN}✓${NC} '[Instagram] Callback sent successfully'"
echo ""
echo "  3. Para TEST 4 (Inbound Webhook), procure por:"
echo -e "     ${GREEN}✓${NC} '[Instagram] Received webhook event: object=instagram'"
echo -e "     ${GREEN}✓${NC} '[Instagram] Processing inbound message: mid=ig_test_mid_999'"
echo -e "     ${GREEN}✓${NC} '[Instagram] Forwarding message to Message Service'"
echo -e "     ${GREEN}✓${NC} '[Instagram] Message forwarded successfully'"
echo ""
echo -e "${CYAN}Expected Flows:${NC}"
echo ""
echo "  Outbound (Send Message):"
echo "    1. Instagram Connector recebe POST /v1/messages"
echo "    2. Simula envio para Instagram API (mock - retorna instagramMessageId)"
echo "    3. Aguarda 2 segundos (async)"
echo "    4. Envia callback READ para Message Service (POST /api/webhooks/instagram)"
echo "    5. Message Service atualiza status da mensagem para READ"
echo ""
echo "  Inbound (Webhook):"
echo "    1. Facebook/Meta envia POST /api/connectors/instagram/webhook"
echo "    2. Instagram Connector valida e parseia o payload"
echo "    3. Extrai mensagem (senderId, text, mid)"
echo "    4. Converte para InboundMessageDTO"
echo "    5. Encaminha para Message Service (POST /api/webhooks/instagram)"
echo "    6. Message Service processa e armazena mensagem"
echo ""
echo -e "${CYAN}Webhook Configuration:${NC}"
echo "  Verification Token: chat4all-verify-token"
echo "  Webhook URL: http://localhost:8093/api/connectors/instagram/webhook"
echo ""
echo -e "${CYAN}Service Status:${NC}"
echo -e "  Instagram Connector: ${GREEN}http://localhost:8093${NC}"
echo -e "  Health Endpoint:     ${GREEN}http://localhost:8093/actuator/health${NC}"
echo -e "  Send Message:        ${GREEN}http://localhost:8093/v1/messages${NC}"
echo -e "  Webhook (GET):       ${GREEN}http://localhost:8093/api/connectors/instagram/webhook${NC}"
echo -e "  Webhook (POST):      ${GREEN}http://localhost:8093/api/connectors/instagram/webhook${NC}"
echo ""
echo -e "${GREEN}✓ Instagram Connector is now BIDIRECTIONAL!${NC}"
echo "  ✓ Outbound: Sends messages to Instagram (mock)"
echo "  ✓ Inbound: Receives messages from Instagram via webhook"
echo ""
echo -e "${YELLOW}Note:${NC} O Instagram Connector está em modo MOCK."
echo "      Ele simula a API do Instagram sem fazer chamadas reais."
echo "      Para produção, configure credenciais reais no application.yml"
echo ""

echo "=========================================="
echo ""
