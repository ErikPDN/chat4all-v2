#!/bin/bash

echo "=== Teste 1: Timestamp como String (epoch seconds) ==="
curl -X POST http://localhost:8081/api/webhooks/whatsapp/message \
  -H "Content-Type: application/json" \
  -d '{
    "platformMessageId": "wamid.test_string_epoch",
    "senderId": "5551234567890",
    "content": "Teste com timestamp String epoch",
    "channel": "WHATSAPP",
    "timestamp": "1732491600"
  }' | jq

echo -e "\n=== Teste 2: Timestamp como String (ISO-8601) ==="
curl -X POST http://localhost:8081/api/webhooks/whatsapp/message \
  -H "Content-Type: application/json" \
  -d '{
    "platformMessageId": "wamid.test_string_iso",
    "senderId": "5551234567890",
    "content": "Teste com timestamp String ISO-8601",
    "channel": "WHATSAPP",
    "timestamp": "2025-11-24T23:00:00Z"
  }' | jq

echo -e "\n=== Teste 3: Timestamp como Number ==="
curl -X POST http://localhost:8081/api/webhooks/whatsapp/message \
  -H "Content-Type: application/json" \
  -d '{
    "platformMessageId": "wamid.test_number",
    "senderId": "5551234567890",
    "content": "Teste com timestamp Number",
    "channel": "WHATSAPP",
    "timestamp": 1732491600
  }' | jq

echo -e "\n=== Teste 4: Sem timestamp (usa Instant.now()) ==="
curl -X POST http://localhost:8081/api/webhooks/whatsapp/message \
  -H "Content-Type: application/json" \
  -d '{
    "platformMessageId": "wamid.test_no_timestamp",
    "senderId": "5551234567890",
    "content": "Teste sem timestamp",
    "channel": "WHATSAPP"
  }' | jq

echo -e "\n=== Testes concluídos ==="
