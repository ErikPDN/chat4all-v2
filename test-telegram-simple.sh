#!/bin/bash

#######################################
# Telegram Integration - Direct Connector Test
# Tests telegram-connector directly (bypasses message-service and router)
#######################################

set -e

source .env

echo ""
echo "═══════════════════════════════════════════════════════"
echo "  🚀 Chat4All - Teste Direto do Telegram Connector"
echo "═══════════════════════════════════════════════════════"
echo ""
echo "Endpoint: POST http://localhost:8086/v1/messages"
echo "Serviço: telegram-connector (porta 8086)"
echo "Bot: @chat4all_erik_bot"
echo "Chat ID: $TELEGRAM_CHAT_ID"
echo ""
echo "───────────────────────────────────────────────────────"
echo ""

# Teste 1: Mensagem simples
echo "📨 Teste 1: Mensagem simples"
echo ""
curl -X POST http://localhost:8086/v1/messages \
  -H "Content-Type: application/json" \
  -d "{
    \"messageId\": \"test-$(date +%s)\",
    \"chatId\": \"$TELEGRAM_CHAT_ID\",
    \"content\": \"Teste de integração real com Telegram Bot API!\",
    \"conversationId\": \"conv-test\",
    \"senderId\": \"system\"
  }" | jq '.'

echo ""
echo "───────────────────────────────────────────────────────"
echo ""
sleep 2

# Teste 2: Mensagem com emoji
echo "📨 Teste 2: Mensagem com emojis"
echo ""
curl -X POST http://localhost:8086/v1/messages \
  -H "Content-Type: application/json" \
  -d "{
    \"messageId\": \"test-$(date +%s)\",
    \"chatId\": \"$TELEGRAM_CHAT_ID\",
    \"content\": \"🎉 Emojis funcionando! ✅ Sucesso ❌ Erro 🚀 Deploy\",
    \"conversationId\": \"conv-test\",
    \"senderId\": \"system\"
  }" | jq '.'

echo ""
echo "───────────────────────────────────────────────────────"
echo ""
sleep 2

# Teste 3: Mensagem técnica
echo "📨 Teste 3: Mensagem técnica"
echo ""
curl -X POST http://localhost:8086/v1/messages \
  -H "Content-Type: application/json" \
  -d "{
    \"messageId\": \"test-$(date +%s)\",
    \"chatId\": \"$TELEGRAM_CHAT_ID\",
    \"content\": \"Chat4All v2 - Teste de integração com Telegram Bot API. Timestamp: $(date '+%Y-%m-%d %H:%M:%S')\",
    \"conversationId\": \"conv-test\",
    \"senderId\": \"system\"
  }" | jq '.'

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ TESTES CONCLUÍDOS!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📱 Verifique seu Telegram - você deve ter recebido 3 mensagens!"
echo ""
echo "Resumo:"
echo "  ✅ Teste 1: Mensagem simples enviada"
echo "  ✅ Teste 2: Mensagem com emojis enviada"
echo "  ✅ Teste 3: Mensagem técnica com timestamp enviada"
echo ""
