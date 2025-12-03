#!/bin/bash

# Get Telegram Chat ID Helper Script

set -e

source .env

echo ""
echo "════════════════════════════════════════════════════════"
echo "  📱 Telegram Chat ID Helper"
echo "════════════════════════════════════════════════════════"
echo ""
echo "🤖 Seu bot: @chat4all_erik_bot"
echo ""
echo "📝 PASSO A PASSO:"
echo ""
echo "  1. Abra o Telegram"
echo "  2. Busque por: @chat4all_erik_bot"
echo "  3. Clique em 'START' ou envie uma mensagem (ex: 'oi')"
echo "  4. Volte aqui e pressione ENTER"
echo ""
read -p "Pressione ENTER depois de enviar a mensagem no Telegram..."
echo ""
echo "🔍 Buscando seu Chat ID..."
echo ""

CHAT_ID=$(curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getUpdates" | jq -r '.result[-1].message.chat.id')

if [ "$CHAT_ID" != "null" ] && [ ! -z "$CHAT_ID" ]; then
    echo "✅ Chat ID encontrado: $CHAT_ID"
    echo ""
    echo "📝 Adicionando ao arquivo .env..."
    
    # Adiciona ou atualiza TELEGRAM_CHAT_ID no .env
    if grep -q "TELEGRAM_CHAT_ID" .env; then
        sed -i "s/TELEGRAM_CHAT_ID=.*/TELEGRAM_CHAT_ID=$CHAT_ID/" .env
        echo "✅ TELEGRAM_CHAT_ID atualizado no .env"
    else
        echo "TELEGRAM_CHAT_ID=$CHAT_ID" >> .env
        echo "✅ TELEGRAM_CHAT_ID adicionado ao .env"
    fi
    
    echo ""
    echo "════════════════════════════════════════════════════════"
    echo "  ✅ Configuração Completa!"
    echo "════════════════════════════════════════════════════════"
    echo ""
    echo "Agora execute o teste:"
    echo "  ./test-telegram-real-api.sh"
    echo ""
else
    echo "❌ Nenhuma mensagem encontrada"
    echo ""
    echo "Certifique-se de:"
    echo "  1. Ter enviado uma mensagem para @chat4all_erik_bot"
    echo "  2. Ter clicado em 'START'"
    echo ""
    exit 1
fi
