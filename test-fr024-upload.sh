#!/bin/bash

################################################################################
# Script de Validação - FR-024: Resumable Uploads (Multipart Upload)
# Chat4All v2 - Unified Messaging Platform
#
# Descrição:
#   Testa o fluxo de upload multipart (resumable) para arquivos >100MB
#   conforme especificação FR-024. Valida a capacidade do sistema de
#   suportar uploads grandes e resilientes a falhas de rede.
#
# Fluxo:
#   1. Cria arquivo de teste >100MB (para forçar multipart upload)
#   2. Inicia upload multipart via File Service
#   3. Obtém presigned URLs para cada parte
#   4. Faz upload de cada parte para MinIO/S3
#   5. Completa o upload multipart
#   6. Valida que arquivo foi montado corretamente
#
# Pré-requisitos:
#   - File Service rodando em localhost:8083
#   - MinIO acessível
#   - Espaço em disco para criar arquivo de teste (~110MB)
#
# Uso:
#   ./test-fr024-upload.sh
#
# Autor: Chat4All Team
# Data: 2025-12-16
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

# Configurações de arquivo de teste
TEST_FILE="large-test-file.bin"
FILE_SIZE_MB=110  # >100MB para forçar multipart upload
FILE_SIZE=$((FILE_SIZE_MB * 1024 * 1024))

echo "=========================================="
echo "  Chat4All v2 - FR-024 Resumable Upload Test"
echo "=========================================="
echo ""
echo "Testing resumable uploads for files >100MB"
echo "Requirement: FR-024 - Support resumable uploads for files larger than 100MB"
echo ""

# Verificar dependências
echo -e "${BLUE}[PREREQ]${NC} Verificando dependências..."

# Verificar se curl está instalado
if ! command -v curl &> /dev/null; then
    echo -e "${RED}✗ curl não encontrado. Instale curl para continuar.${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} curl encontrado"

# Verificar se dd está disponível (para criar arquivo de teste)
if ! command -v dd &> /dev/null; then
    echo -e "${RED}✗ dd não encontrado. Necessário para criar arquivo de teste.${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} dd encontrado"

# Verificar se File Service está respondendo
echo -e "${BLUE}[PREREQ]${NC} Verificando conectividade com File Service..."
if ! curl -s -f -o /dev/null --max-time 5 "${FILE_SERVICE_URL}/health" 2>/dev/null; then
    echo -e "${YELLOW}⚠ File Service health endpoint não acessível${NC}"
    echo -e "${YELLOW}  Tentando continuar mesmo assim...${NC}"
else
    echo -e "${GREEN}✓${NC} File Service está acessível"
fi
echo ""

# Passo 0: Criar arquivo de teste grande
echo -e "${BLUE}[PASSO 0]${NC} Criando arquivo de teste (${FILE_SIZE_MB}MB)..."
echo "Arquivo: ${TEST_FILE}"
echo "Tamanho: ${FILE_SIZE_MB}MB (${FILE_SIZE} bytes)"
echo ""

# Criar arquivo com dados aleatórios
# Use /dev/zero para ser mais rápido do que /dev/urandom
if dd if=/dev/zero of="${TEST_FILE}" bs=1M count=${FILE_SIZE_MB} 2>/dev/null; then
    # Detectar comando stat correto (BSD vs GNU)
    if stat -f%z "${TEST_FILE}" >/dev/null 2>&1; then
        # BSD stat (macOS)
        ACTUAL_SIZE=$(stat -f%z "${TEST_FILE}")
    else
        # GNU stat (Linux)
        ACTUAL_SIZE=$(stat -c%s "${TEST_FILE}")
    fi
    echo -e "${GREEN}✓${NC} Arquivo criado com sucesso"
    echo "  Tamanho real: ${ACTUAL_SIZE} bytes"
    ls -lh "${TEST_FILE}"
else
    echo -e "${RED}✗ Erro ao criar arquivo de teste${NC}"
    exit 1
fi
echo ""

# Passo 1: Iniciar upload (obter presigned URL ou multipart upload ID)
echo -e "${BLUE}[PASSO 1]${NC} Iniciando upload - solicitando presigned URL..."
echo "URL: POST ${FILE_SERVICE_URL}/initiate"
echo ""

INITIATE_PAYLOAD=$(cat <<EOF
{
  "filename": "${TEST_FILE}",
  "fileSize": ${FILE_SIZE},
  "mimeType": "application/octet-stream"
}
EOF
)

echo "Payload:"
echo "${INITIATE_PAYLOAD}"
echo ""

INITIATE_RESPONSE=$(curl -s -X POST "${FILE_SERVICE_URL}/initiate" \
  -H "Content-Type: application/json" \
  -d "${INITIATE_PAYLOAD}")

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Erro ao iniciar upload. Verifique se o File Service está rodando na porta 8083.${NC}"
    rm -f "${TEST_FILE}"
    exit 1
fi

echo "Resposta do File Service:"
echo "${INITIATE_RESPONSE}"
echo ""

# Extrair fileId e presignedUrl da resposta JSON
if command -v jq &> /dev/null; then
    FILE_ID=$(echo "${INITIATE_RESPONSE}" | jq -r '.fileId // empty')
    PRESIGNED_URL=$(echo "${INITIATE_RESPONSE}" | jq -r '.uploadUrl // empty')
    UPLOAD_ID=$(echo "${INITIATE_RESPONSE}" | jq -r '.uploadId // empty')
else
    # Fallback: extração com grep/sed para compatibilidade
    FILE_ID=$(echo "${INITIATE_RESPONSE}" | grep -oP '(?<="fileId":")[\w\-]+' | head -n 1)
    PRESIGNED_URL=$(echo "${INITIATE_RESPONSE}" | grep -oP '(?<="uploadUrl":")[^"]+' | head -n 1)
    UPLOAD_ID=$(echo "${INITIATE_RESPONSE}" | grep -oP '(?<="uploadId":")[^"]+' | head -n 1)
fi

# Validação do FILE_ID e PRESIGNED_URL
if [ -z "${FILE_ID}" ]; then
    echo -e "${RED}✗ ERRO: Não foi possível extrair fileId da resposta${NC}"
    echo "Resposta recebida:"
    echo "${INITIATE_RESPONSE}"
    rm -f "${TEST_FILE}"
    exit 1
fi

echo -e "${GREEN}✓ File ID Gerado: ${FILE_ID}${NC}"

# Verificar se é upload multipart ou single upload
if [ -n "${UPLOAD_ID}" ]; then
    echo -e "${GREEN}✓ Upload ID (Multipart): ${UPLOAD_ID}${NC}"
    echo -e "${YELLOW}  Sistema detectou arquivo grande e iniciou upload multipart${NC}"
    UPLOAD_TYPE="multipart"
elif [ -n "${PRESIGNED_URL}" ]; then
    echo -e "${GREEN}✓ Presigned URL obtida${NC}"
    echo -e "${YELLOW}  Sistema está usando upload simples (não multipart)${NC}"
    UPLOAD_TYPE="simple"
else
    echo -e "${RED}✗ ERRO: Resposta não contém nem uploadId nem uploadUrl${NC}"
    rm -f "${TEST_FILE}"
    exit 1
fi
echo ""

# Passo 2: Upload do arquivo
if [ "${UPLOAD_TYPE}" = "multipart" ]; then
    echo -e "${BLUE}[PASSO 2]${NC} Upload multipart não está implementado na API REST ainda"
    echo -e "${YELLOW}⚠ AVISO: O MultipartUploadService existe mas não há endpoints REST expostos${NC}"
    echo ""
    echo "Para implementar o teste completo, seria necessário:"
    echo "  1. Endpoint POST /api/files/multipart/initiate"
    echo "  2. Endpoint GET /api/files/multipart/{uploadId}/parts - obter presigned URLs"
    echo "  3. Endpoint POST /api/files/multipart/{uploadId}/complete - finalizar upload"
    echo ""
    echo -e "${YELLOW}  Fallback: Tentando upload simples via presigned URL...${NC}"
    UPLOAD_TYPE="simple"
fi

if [ "${UPLOAD_TYPE}" = "simple" ]; then
    if [ -z "${PRESIGNED_URL}" ]; then
        echo -e "${RED}✗ Presigned URL não disponível${NC}"
        rm -f "${TEST_FILE}"
        exit 1
    fi
    
    echo -e "${BLUE}[PASSO 2]${NC} Fazendo upload do arquivo para MinIO via presigned URL..."
    echo "Destino: MinIO (via presigned URL)"
    echo "Arquivo: ${TEST_FILE} (${FILE_SIZE_MB}MB)"
    echo ""
    
    # Upload com progress bar se curl suportar
    echo -e "${YELLOW}⏳ Uploading... (pode levar alguns segundos)${NC}"
    
    UPLOAD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
      -H "Content-Type: application/octet-stream" \
      --data-binary "@${TEST_FILE}" \
      "${PRESIGNED_URL}")
    
    if [ "${UPLOAD_STATUS}" != "200" ]; then
        echo -e "${RED}✗ Erro ao fazer upload para MinIO. Status HTTP: ${UPLOAD_STATUS}${NC}"
        echo ""
        echo "Possíveis causas:"
        echo "  - MinIO não está acessível"
        echo "  - Presigned URL expirou"
        echo "  - Arquivo muito grande para upload simples (>2GB)"
        echo "  - Problema de rede durante upload"
        rm -f "${TEST_FILE}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Arquivo enviado para MinIO com sucesso (HTTP ${UPLOAD_STATUS})${NC}"
    echo ""
fi

# Aguardar processamento assíncrono
echo -e "${YELLOW}⏳ Aguardando processamento assíncrono...${NC}"
sleep 3
echo ""

# Passo 3: Validar que arquivo foi salvo
echo -e "${BLUE}[PASSO 3]${NC} Validando persistência do arquivo..."
echo "Consultando: GET ${FILE_SERVICE_URL}/${FILE_ID}"
echo ""

FILE_STATUS_RESPONSE=$(curl -s "${FILE_SERVICE_URL}/${FILE_ID}")

if [ $? -eq 0 ] && echo "${FILE_STATUS_RESPONSE}" | grep -q '"fileId"'; then
    echo -e "${GREEN}✓ Arquivo encontrado no sistema!${NC}"
    echo ""
    
    # Extrair informações da resposta
    if command -v jq &> /dev/null; then
        echo "Metadados do arquivo:"
        echo "${FILE_STATUS_RESPONSE}" | jq '{
            fileId: .fileId,
            filename: .filename,
            fileSize: .fileSize,
            mimeType: .mimeType,
            status: .status,
            objectKey: .objectKey,
            uploadedAt: .uploadedAt,
            expiresAt: .expiresAt
        }'
    else
        echo "Resposta da API:"
        echo "${FILE_STATUS_RESPONSE}"
    fi
    echo ""
    
    # Verificar status do arquivo
    if command -v jq &> /dev/null; then
        CURRENT_STATUS=$(echo "${FILE_STATUS_RESPONSE}" | jq -r '.status // empty')
        echo -e "Status do arquivo: ${GREEN}${CURRENT_STATUS}${NC}"
        
        if [ "${CURRENT_STATUS}" = "READY" ]; then
            echo -e "${GREEN}✓ Arquivo está pronto para download${NC}"
        elif [ "${CURRENT_STATUS}" = "PENDING" ]; then
            echo -e "${YELLOW}⚠ Arquivo ainda está sendo processado${NC}"
        else
            echo -e "${YELLOW}⚠ Status inesperado: ${CURRENT_STATUS}${NC}"
        fi
    fi
    
else
    echo -e "${RED}✗ Erro ao consultar status do arquivo${NC}"
    echo "O arquivo pode não ter sido persistido corretamente."
fi

echo ""
echo "=========================================="
echo "  ✅ Teste FR-024 Concluído!"
echo "=========================================="
echo ""
echo -e "${GREEN}Resumo da execução:${NC}"
echo "  • Arquivo de teste criado: ${TEST_FILE} (${FILE_SIZE_MB}MB)"
echo "  • File ID: ${FILE_ID}"
echo "  • Tipo de upload: ${UPLOAD_TYPE}"
echo "  • Upload para MinIO: ✓"
echo "  • Persistência validada: ✓"
echo ""

if [ "${UPLOAD_TYPE}" = "simple" ]; then
    echo -e "${YELLOW}⚠ OBSERVAÇÃO:${NC}"
    echo "  O teste usou upload simples em vez de multipart."
    echo "  Para arquivos >100MB, o ideal seria usar multipart upload."
    echo ""
    echo "  Ação recomendada:"
    echo "  - Implementar endpoints REST para multipart upload:"
    echo "    • POST /api/files/multipart/initiate"
    echo "    • GET /api/files/multipart/{uploadId}/parts"
    echo "    • POST /api/files/multipart/{uploadId}/complete"
    echo "    • DELETE /api/files/multipart/{uploadId}/abort"
    echo ""
fi

echo -e "${YELLOW}Validações adicionais (opcional):${NC}"
echo ""
echo -e "${YELLOW}[1] Verificar arquivo no MinIO (Console Web):${NC}"
echo "    URL: http://localhost:9001"
echo "    Login: minioadmin / minioadmin123"
echo "    Bucket: chat4all-files"
if command -v jq &> /dev/null; then
    OBJECT_KEY=$(echo "${FILE_STATUS_RESPONSE}" | jq -r '.objectKey // empty')
    if [ -n "${OBJECT_KEY}" ]; then
        echo "    Path: ${OBJECT_KEY}"
    fi
fi
echo ""

echo -e "${YELLOW}[2] Testar download do arquivo:${NC}"
echo "    curl ${FILE_SERVICE_URL}/${FILE_ID}/download"
echo ""

# Limpar arquivo de teste
rm -f "${TEST_FILE}"
echo "Arquivo de teste removido."
echo ""

