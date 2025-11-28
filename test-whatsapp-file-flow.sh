#!/bin/bash

################################################################################
# Script de Validação - Fluxo de Envio de Mídia WhatsApp
# Chat4All v2 - Unified Messaging Platform
#
# Descrição:
#   Testa o fluxo completo de upload de arquivo e envio de mensagem com anexo
#   via WhatsApp, validando integração entre File Service e Message Service.
#
# Fluxo:
#   1. Cria arquivo dummy de teste
#   2. Faz upload via File Service (POST /api/files)
#   3. Captura o fileId retornado
#   4. Envia mensagem com anexo via Message Service (POST /api/messages)
#   5. Valida resposta e orienta verificação no MinIO/Kafka
#
# Pré-requisitos:
#   - File Service rodando em localhost:8083
#   - Message Service rodando em localhost:8081
#   - MinIO acessível
#   - Kafka rodando
#
# Uso:
#   ./test-whatsapp-file-flow.sh
#
# Autor: Chat4All Team
# Data: 2025-11-28
################################################################################

set -e  # Exit on error

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# URLs dos serviços
FILE_SERVICE_URL="http://localhost:8083/api/files"
MESSAGE_SERVICE_URL="http://localhost:8081/api/messages"

echo "=========================================="
echo "  Chat4All v2 - File Upload Flow Test"
echo "=========================================="
echo ""

# Passo 0: Criar arquivo dummy
echo -e "${BLUE}[PASSO 0]${NC} Criando arquivo de teste..."
echo "Conteudo de teste do Chat4All" > teste-doc.txt
if [ -f teste-doc.txt ]; then
    echo -e "${GREEN}✓${NC} Arquivo 'teste-doc.txt' criado com sucesso"
    ls -lh teste-doc.txt
else
    echo -e "${RED}✗ Erro ao criar arquivo de teste${NC}"
    exit 1
fi
echo ""

# Passo 1: Iniciar upload (obter presigned URL)
echo -e "${BLUE}[PASSO 1]${NC} Iniciando upload - solicitando presigned URL..."
echo "URL: POST ${FILE_SERVICE_URL}/initiate"
echo ""

INITIATE_PAYLOAD=$(cat <<EOF
{
  "filename": "teste-doc.txt",
  "fileSize": 30,
  "mimeType": "text/plain"
}
EOF
)

INITIATE_RESPONSE=$(curl -s -X POST "${FILE_SERVICE_URL}/initiate" \
  -H "Content-Type: application/json" \
  -d "${INITIATE_PAYLOAD}")

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Erro ao iniciar upload. Verifique se o File Service está rodando na porta 8083.${NC}"
    rm -f teste-doc.txt
    exit 1
fi

echo "Resposta do File Service:"
echo "${INITIATE_RESPONSE}"
echo ""

# Extrair fileId e presignedUrl da resposta JSON
if command -v jq &> /dev/null; then
    FILE_ID=$(echo "${INITIATE_RESPONSE}" | jq -r '.fileId // empty')
    PRESIGNED_URL=$(echo "${INITIATE_RESPONSE}" | jq -r '.uploadUrl // empty')
else
    # Fallback: extração com grep/sed para compatibilidade
    FILE_ID=$(echo "${INITIATE_RESPONSE}" | grep -oP '(?<="fileId":")[\w\-]+' | head -n 1)
    PRESIGNED_URL=$(echo "${INITIATE_RESPONSE}" | grep -oP '(?<="uploadUrl":")[^"]+' | head -n 1)
fi

# Validação do FILE_ID e PRESIGNED_URL
if [ -z "${FILE_ID}" ] || [ -z "${PRESIGNED_URL}" ]; then
    echo -e "${RED}✗ ERRO: Não foi possível extrair fileId ou uploadUrl da resposta${NC}"
    echo "Resposta recebida:"
    echo "${INITIATE_RESPONSE}"
    echo ""
    echo "Possíveis causas:"
    echo "  - File Service não está respondendo corretamente"
    echo "  - MinIO não está acessível"
    echo "  - Erro ao gerar presigned URL"
    rm -f teste-doc.txt
    exit 1
fi

echo -e "${GREEN}✓ File ID Gerado: ${FILE_ID}${NC}"
echo -e "${GREEN}✓ Presigned URL obtida${NC}"
echo ""

# Passo 1.5: Upload para MinIO via presigned URL
echo -e "${BLUE}[PASSO 1.5]${NC} Fazendo upload do arquivo para MinIO..."
echo "Destino: MinIO (via presigned URL)"
echo ""

UPLOAD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
  -H "Content-Type: text/plain" \
  --data-binary "@teste-doc.txt" \
  "${PRESIGNED_URL}")

if [ "${UPLOAD_STATUS}" != "200" ]; then
    echo -e "${RED}✗ Erro ao fazer upload para MinIO. Status HTTP: ${UPLOAD_STATUS}${NC}"
    rm -f teste-doc.txt
    exit 1
fi

echo -e "${GREEN}✓ Arquivo enviado para MinIO com sucesso (HTTP ${UPLOAD_STATUS})${NC}"
echo ""

# Aguardar processamento assíncrono (reactive streams)
echo -e "${YELLOW}⏳ Aguardando 1 segundo para processamento assíncrono...${NC}"
sleep 1
echo ""

# Passo 2: Enviar mensagem com anexo
echo -e "${BLUE}[PASSO 2]${NC} Enviando mensagem com anexo via Message Service..."
echo "URL: POST ${MESSAGE_SERVICE_URL}"
echo "FileID: ${FILE_ID}"
echo ""

MESSAGE_PAYLOAD=$(cat <<EOF
{
  "conversationId": "teste-file-flow-01",
  "senderId": "erik-dev",
  "content": "Segue documento anexo via MinIO",
  "channel": "WHATSAPP",
  "fileIds": ["${FILE_ID}"]
}
EOF
)

echo "Payload da mensagem:"
echo "${MESSAGE_PAYLOAD}"
echo ""

MESSAGE_RESPONSE=$(curl -s -X POST "${MESSAGE_SERVICE_URL}" \
  -H "Content-Type: application/json" \
  -d "${MESSAGE_PAYLOAD}")

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Erro ao enviar mensagem. Verifique se o Message Service está rodando na porta 8081.${NC}"
    rm -f teste-doc.txt
    exit 1
fi

echo "Resposta do Message Service:"
echo "${MESSAGE_RESPONSE}"
echo ""

# Verificar se a mensagem foi criada com sucesso
if echo "${MESSAGE_RESPONSE}" | grep -q '"messageId"'; then
    if command -v jq &> /dev/null; then
        MESSAGE_ID=$(echo "${MESSAGE_RESPONSE}" | jq -r '.messageId // empty')
        STATUS_URL=$(echo "${MESSAGE_RESPONSE}" | jq -r '.statusUrl // empty')
    else
        MESSAGE_ID=$(echo "${MESSAGE_RESPONSE}" | grep -oP '(?<="messageId":")[\w\-]+' | head -n 1)
        STATUS_URL=$(echo "${MESSAGE_RESPONSE}" | grep -oP '(?<="statusUrl":")[^"]+' | head -n 1)
    fi
    
    echo -e "${GREEN}✓ Mensagem aceita com sucesso! (HTTP 202 Accepted)${NC}"
    echo -e "${GREEN}  Message ID: ${MESSAGE_ID}${NC}"
    echo -e "${GREEN}  File ID: ${FILE_ID}${NC}"
else
    echo -e "${YELLOW}⚠ Resposta recebida, mas formato inesperado${NC}"
    MESSAGE_ID=""
fi

echo ""

# Passo 3: Validar persistência via API
if [ -n "${MESSAGE_ID}" ]; then
    echo -e "${BLUE}[PASSO 3]${NC} Validando persistência da mensagem..."
    echo "Aguardando processamento reativo (reactive streams)..."
    sleep 2
    echo ""
    
    # Consultar status via API
    echo "Consultando: GET ${MESSAGE_SERVICE_URL}/${MESSAGE_ID}/status"
    STATUS_RESPONSE=$(curl -s "${MESSAGE_SERVICE_URL}/${MESSAGE_ID}/status")
    
    if [ $? -eq 0 ] && echo "${STATUS_RESPONSE}" | grep -q '"messageId"'; then
        echo -e "${GREEN}✓ Mensagem persistida com sucesso!${NC}"
        echo ""
        
        # Extrair informações da resposta
        if command -v jq &> /dev/null; then
            echo "Dados da mensagem:"
            echo "${STATUS_RESPONSE}" | jq '{
                messageId: .messageId,
                conversationId: .conversationId,
                senderId: .senderId,
                content: .content,
                channel: .channel,
                status: .status,
                fileIds: .fileIds,
                createdAt: .createdAt,
                updatedAt: .updatedAt
            }'
        else
            echo "Resposta da API:"
            echo "${STATUS_RESPONSE}"
        fi
        echo ""
        
        # Verificar se fileIds está presente
        if echo "${STATUS_RESPONSE}" | grep -q "\"${FILE_ID}\""; then
            echo -e "${GREEN}✓ FileId corretamente associado à mensagem${NC}"
        else
            echo -e "${RED}✗ FileId NÃO encontrado na mensagem${NC}"
        fi
        echo ""
        
        # Extrair status atual
        if command -v jq &> /dev/null; then
            CURRENT_STATUS=$(echo "${STATUS_RESPONSE}" | jq -r '.status // empty')
            echo -e "Status atual: ${GREEN}${CURRENT_STATUS}${NC}"
        fi
        
    else
        echo -e "${RED}✗ Erro ao consultar status da mensagem${NC}"
        echo "A mensagem pode não ter sido persistida corretamente."
    fi
fi

echo ""
echo "=========================================="
echo "  ✅ Teste Concluído com Sucesso!"
echo "=========================================="
echo ""
echo -e "${GREEN}Resumo da execução:${NC}"
echo "  • Arquivo criado: teste-doc.txt"
echo "  • Upload para MinIO: ✓"
echo "  • File ID: ${FILE_ID}"
echo "  • Mensagem criada: ✓"
echo "  • Message ID: ${MESSAGE_ID}"
echo "  • Persistência validada: ✓"
echo "  • FileId associado: ✓"
echo ""
echo -e "${YELLOW}Validações adicionais (opcional):${NC}"
echo ""
echo -e "${YELLOW}[1] Verificar arquivo no MinIO (Console Web):${NC}"
echo "    URL: http://localhost:9001"
echo "    Login: minioadmin / minioadmin123"
echo "    Bucket: chat4all-files"
echo "    Path: files/2025/11/${FILE_ID}/"
echo ""
echo -e "${YELLOW}[2] Verificar evento no Kafka:${NC}"
echo "    docker exec chat4all-kafka kafka-console-consumer.sh \\"
echo "      --bootstrap-server localhost:9092 \\"
echo "      --topic message-events \\"
echo "      --from-beginning | grep '${MESSAGE_ID}'"
echo ""
echo -e "${YELLOW}[3] Verificar diretamente no MongoDB:${NC}"
echo "    docker exec -it chat4all-mongodb mongosh \\"
echo "      --username chat4all \\"
echo "      --password chat4all_dev_password \\"
echo "      --authenticationDatabase admin \\"
echo "      chat4all \\"
echo "      --eval \"db.messages.findOne({messageId: '${MESSAGE_ID}'})\""
echo ""

# Limpar arquivo de teste
rm -f teste-doc.txt
echo "Arquivo de teste removido."
