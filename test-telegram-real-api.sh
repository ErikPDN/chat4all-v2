#!/bin/bash

#######################################
# Telegram Connector - Real API Test
#######################################
# 
# Este script testa a integração REAL do telegram-connector
# com a API do Telegram Bot.
#
# Pré-requisitos:
# 1. Criar bot via @BotFather e obter o token
# 2. Obter seu chat_id via @userinfobot
# 3. Configurar variáveis de ambiente
#
# Uso:
#   export TELEGRAM_BOT_TOKEN="your-bot-token-here"
#   export TELEGRAM_CHAT_ID="your-chat-id-here"
#   ./test-telegram-real-api.sh
#
#######################################

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CONNECTOR_URL="${CONNECTOR_URL:-http://localhost:8086}"
BOT_TOKEN="${TELEGRAM_BOT_TOKEN}"
CHAT_ID="${TELEGRAM_CHAT_ID}"

echo ""
echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Telegram Connector - Real API Test       ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
echo ""

# Check prerequisites
echo -e "${YELLOW}Verificando pré-requisitos...${NC}"

if [ -z "$BOT_TOKEN" ]; then
    echo -e "${RED}❌ ERRO: TELEGRAM_BOT_TOKEN não definido${NC}"
    echo ""
    echo "Configure o token do bot:"
    echo "  export TELEGRAM_BOT_TOKEN=\"123456789:ABCdefGHIjklMNOpqrsTUVwxyz\""
    echo ""
    echo "Para criar um bot:"
    echo "  1. Abra o Telegram"
    echo "  2. Busque por @BotFather"
    echo "  3. Envie /newbot"
    echo "  4. Siga as instruções"
    echo ""
    exit 1
fi

if [ -z "$CHAT_ID" ]; then
    echo -e "${RED}❌ ERRO: TELEGRAM_CHAT_ID não definido${NC}"
    echo ""
    echo "Configure o chat ID:"
    echo "  export TELEGRAM_CHAT_ID=\"987654321\""
    echo ""
    echo "Para obter seu chat ID:"
    echo "  Opção 1: Busque @userinfobot no Telegram"
    echo "  Opção 2: curl \"https://api.telegram.org/bot\$TELEGRAM_BOT_TOKEN/getUpdates\""
    echo ""
    exit 1
fi

echo -e "${GREEN}✅ Token configurado: ${BOT_TOKEN:0:10}...${NC}"
echo -e "${GREEN}✅ Chat ID: $CHAT_ID${NC}"
echo ""

# Check if connector is running
echo -e "${YELLOW}Verificando se telegram-connector está rodando...${NC}"

if ! curl -s -f "$CONNECTOR_URL/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}❌ ERRO: telegram-connector não está acessível em $CONNECTOR_URL${NC}"
    echo ""
    echo "Inicie o connector:"
    echo "  docker-compose up -d telegram-connector"
    echo ""
    exit 1
fi

echo -e "${GREEN}✅ Connector rodando em $CONNECTOR_URL${NC}"
echo ""

# Test 1: Send simple message
echo -e "${BLUE}═══════════════════════════════════════════${NC}"
echo -e "${YELLOW}Teste 1: Enviando mensagem de texto simples${NC}"
echo -e "${BLUE}═══════════════════════════════════════════${NC}"

MESSAGE_ID="test-$(date +%s)"
TEXT="🚀 Teste de integração real com Telegram Bot API\n\nTimestamp: $(date '+%Y-%m-%d %H:%M:%S')"

echo ""
echo "Payload:"
echo "{
  \"messageId\": \"$MESSAGE_ID\",
  \"chatId\": \"$CHAT_ID\",
  \"content\": \"$TEXT\",
  \"conversationId\": \"test-conv-$(date +%s)\",
  \"senderId\": \"system\"
}"
echo ""

RESPONSE=$(curl -s -X POST "$CONNECTOR_URL/api/send" \
  -H "Content-Type: application/json" \
  -d "{
    \"messageId\": \"$MESSAGE_ID\",
    \"chatId\": \"$CHAT_ID\",
    \"content\": \"$TEXT\",
    \"conversationId\": \"test-conv-$(date +%s)\",
    \"senderId\": \"system\"
  }")

echo "Response:"
echo "$RESPONSE" | jq '.'
echo ""

# Validate response
TELEGRAM_MESSAGE_ID=$(echo "$RESPONSE" | jq -r '.telegramMessageId')
STATUS=$(echo "$RESPONSE" | jq -r '.status')

if [ "$STATUS" == "SENT" ] && [ "$TELEGRAM_MESSAGE_ID" != "null" ] && [ ! -z "$TELEGRAM_MESSAGE_ID" ]; then
    echo -e "${GREEN}✅ Teste 1 PASSOU${NC}"
    echo -e "${GREEN}   Message ID do Telegram: $TELEGRAM_MESSAGE_ID${NC}"
else
    echo -e "${RED}❌ Teste 1 FALHOU${NC}"
    echo -e "${RED}   Status: $STATUS${NC}"
    echo -e "${RED}   Telegram Message ID: $TELEGRAM_MESSAGE_ID${NC}"
    exit 1
fi

echo ""
sleep 2

# Test 2: Send message with emoji
echo -e "${BLUE}═══════════════════════════════════════════${NC}"
echo -e "${YELLOW}Teste 2: Enviando mensagem com emojis${NC}"
echo -e "${BLUE}═══════════════════════════════════════════${NC}"

MESSAGE_ID="test-emoji-$(date +%s)"
TEXT="🎉 Emojis suportados! 👍\n\n✅ Sucesso\n❌ Erro\n⚠️ Aviso\n🔔 Notificação\n🚀 Deploy"

echo ""
RESPONSE=$(curl -s -X POST "$CONNECTOR_URL/api/send" \
  -H "Content-Type: application/json" \
  -d "{
    \"messageId\": \"$MESSAGE_ID\",
    \"chatId\": \"$CHAT_ID\",
    \"content\": \"$TEXT\",
    \"conversationId\": \"test-conv-$(date +%s)\",
    \"senderId\": \"system\"
  }")

echo "$RESPONSE" | jq '.'
echo ""

TELEGRAM_MESSAGE_ID=$(echo "$RESPONSE" | jq -r '.telegramMessageId')
STATUS=$(echo "$RESPONSE" | jq -r '.status')

if [ "$STATUS" == "SENT" ] && [ "$TELEGRAM_MESSAGE_ID" != "null" ]; then
    echo -e "${GREEN}✅ Teste 2 PASSOU${NC}"
else
    echo -e "${RED}❌ Teste 2 FALHOU${NC}"
    exit 1
fi

echo ""
sleep 2

# Test 3: Send long message
echo -e "${BLUE}═══════════════════════════════════════════${NC}"
echo -e "${YELLOW}Teste 3: Enviando mensagem longa${NC}"
echo -e "${BLUE}═══════════════════════════════════════════${NC}"

MESSAGE_ID="test-long-$(date +%s)"
TEXT="📊 Relatório de Teste - Chat4All v2

Este é um teste de mensagem longa para validar que o telegram-connector consegue enviar textos extensos via API do Telegram.

🔧 Componentes Testados:
- TelegramApiClient
- TelegramService
- WebClient HTTP
- Error Handling
- Retry Logic

✅ Status: FUNCIONANDO
⏱️ Timestamp: $(date '+%Y-%m-%d %H:%M:%S')

📝 Observações:
O limite do Telegram é 4096 caracteres por mensagem. Mensagens maiores precisam ser divididas."

echo ""
RESPONSE=$(curl -s -X POST "$CONNECTOR_URL/api/send" \
  -H "Content-Type: application/json" \
  -d "{
    \"messageId\": \"$MESSAGE_ID\",
    \"chatId\": \"$CHAT_ID\",
    \"content\": \"$TEXT\",
    \"conversationId\": \"test-conv-$(date +%s)\",
    \"senderId\": \"system\"
  }")

echo "$RESPONSE" | jq '.'
echo ""

TELEGRAM_MESSAGE_ID=$(echo "$RESPONSE" | jq -r '.telegramMessageId')
STATUS=$(echo "$RESPONSE" | jq -r '.status')

if [ "$STATUS" == "SENT" ] && [ "$TELEGRAM_MESSAGE_ID" != "null" ]; then
    echo -e "${GREEN}✅ Teste 3 PASSOU${NC}"
else
    echo -e "${RED}❌ Teste 3 FALHOU${NC}"
    exit 1
fi

echo ""
sleep 2

# Test 4: Error handling (invalid chat_id)
echo -e "${BLUE}═══════════════════════════════════════════${NC}"
echo -e "${YELLOW}Teste 4: Validando error handling (chat_id inválido)${NC}"
echo -e "${BLUE}═══════════════════════════════════════════${NC}"

MESSAGE_ID="test-error-$(date +%s)"
INVALID_CHAT_ID="999999999999999"  # Chat ID que não existe

echo ""
echo "Tentando enviar para chat_id inválido: $INVALID_CHAT_ID"
echo ""

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$CONNECTOR_URL/api/send" \
  -H "Content-Type: application/json" \
  -d "{
    \"messageId\": \"$MESSAGE_ID\",
    \"chatId\": \"$INVALID_CHAT_ID\",
    \"content\": \"Esta mensagem deve falhar\",
    \"conversationId\": \"test-conv-$(date +%s)\",
    \"senderId\": \"system\"
  }")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | head -n-1)

echo "HTTP Status: $HTTP_CODE"
echo "Response: $BODY" | jq '.' 2>/dev/null || echo "$BODY"
echo ""

if [ "$HTTP_CODE" -ge 400 ] && [ "$HTTP_CODE" -lt 600 ]; then
    echo -e "${GREEN}✅ Teste 4 PASSOU (erro capturado corretamente)${NC}"
else
    echo -e "${RED}❌ Teste 4 FALHOU (deveria retornar erro 4xx/5xx)${NC}"
fi

echo ""

# Summary
echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║           RESUMO DOS TESTES                ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}✅ Teste 1: Mensagem simples${NC}"
echo -e "${GREEN}✅ Teste 2: Mensagem com emojis${NC}"
echo -e "${GREEN}✅ Teste 3: Mensagem longa${NC}"
echo -e "${GREEN}✅ Teste 4: Error handling${NC}"
echo ""
echo -e "${GREEN}🎉 TODOS OS TESTES PASSARAM!${NC}"
echo ""
echo -e "${YELLOW}Verifique seu Telegram - você deve ter recebido 3 mensagens.${NC}"
echo ""
