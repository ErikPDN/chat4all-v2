#!/bin/bash
# test-basic-flow.sh

echo "--- 1. Criando Conversa ---"
CONV=$(curl -s -X POST http://localhost:8081/api/v1/conversations \
  -H "Content-Type: application/json" \
  -d '{"type":"ONE_TO_ONE","participants":["erik","ana"],"title":"Chat Basic"}')
echo $CONV
ID=$(echo $CONV | jq -r '.conversationId')

echo -e "\n--- 2. Enviando Mensagem para ID: $ID ---"
curl -s -X POST http://localhost:8081/api/messages \
  -H "Content-Type: application/json" \
  -d "{\"conversationId\":\"$ID\",\"senderId\":\"erik\",\"content\":\"Teste Basico\",\"channel\":\"INTERNAL\",\"contentType\":\"TEXT\"}"

echo -e "\n\n--- 3. Aguardando processamento (2s) ---"
sleep 2

echo -e "\n--- 4. Consultando Histórico (GET) ---"
# Importante: Passar userId para passar no filtro de privacidade
curl -s "http://localhost:8081/api/v1/conversations/$ID/messages?userId=erik" | jq