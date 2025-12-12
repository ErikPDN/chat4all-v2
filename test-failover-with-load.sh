#!/bin/bash

# Teste de Failover com Injeção de Carga
# Simula falha do Router Service enquanto há processamento de mensagens
# Objetivo: Validar Zero Message Loss durante falha

set +e  # Não sair em erros

# Cores ANSI
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Configurações
API_URL="http://localhost:8080"
MONGODB_HOST="localhost"
MONGODB_PORT="27017"
MONGODB_DB="chat4all"
MONGODB_COLLECTION="messages"
MONGODB_SERVICE="mongodb"
MONGODB_USER="chat4all"
MONGODB_PASS="chat4all_dev_password"
ROUTER_CONTAINER="chat4all-v2-router-service-1"

# Contadores
INITIAL_COUNT=0
AFTER_FIRST_LOAD=0
AFTER_RECOVERY=0
TOTAL_SENT=100

LOG_DIR="logs/failover-tests"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
REPORT="$LOG_DIR/FAILOVER_WITH_LOAD_$TIMESTAMP.md"

mkdir -p "$LOG_DIR"

# Funções de log
log() {
    echo -e "${BLUE}[$(date +'%H:%M:%S')]${NC} $1"
    echo "[$(date +'%H:%M:%S')] $1" >> "$REPORT"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
    echo "✓ $1" >> "$REPORT"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
    echo "✗ $1" >> "$REPORT"
}

log_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
    echo "⚠ $1" >> "$REPORT"
}

log_info() {
    echo -e "${CYAN}ℹ${NC} $1"
    echo "ℹ $1" >> "$REPORT"
}

log_phase() {
    echo ""
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${MAGENTA}$1${NC}"
    echo -e "${MAGENTA}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "## $1" >> "$REPORT"
    echo "" >> "$REPORT"
}

# Função para contar mensagens no MongoDB
count_messages_in_mongodb() {
    local count="0"
    
    # Usar docker compose exec com mongosh (database como argumento posicional, service name)
    count=$(docker compose exec -T "$MONGODB_SERVICE" mongosh \
        -u "$MONGODB_USER" \
        -p "$MONGODB_PASS" \
        --authenticationDatabase admin \
        "$MONGODB_DB" \
        --eval "db.$MONGODB_COLLECTION.countDocuments()" 2>/dev/null | tail -1)
    
    # Validar se é um número
    if ! [[ "$count" =~ ^[0-9]+$ ]]; then
        count="0"
    fi
    
    # Fallback para 0 se não conseguir
    echo "${count:-0}"
}

# Função para enviar mensagem via API
send_message() {
    local msg_id=$1
    local timestamp=$(date +%s%N)  # Timestamp com nanosegundos para unicidade
    local text="Test message $msg_id at $timestamp"
    
    # Criar payload JSON com estrutura correta
    local payload="{
  \"senderId\": \"test-user\",
  \"conversationId\": \"test-conv-failover\",
  \"content\": \"$text\",
  \"contentType\": \"TEXT\",
  \"channel\": \"INTERNAL\"
}"
    
    # Enviar via curl com timeout e obter apenas HTTP code
    local http_code=$(curl -s -w "%{http_code}" -o /dev/null \
        --max-time 5 \
        -X POST "$API_URL/api/messages" \
        -H "Content-Type: application/json" \
        -d "$payload" 2>/dev/null)
    
    # Aceitar 200, 201, 202 como sucesso
    case "$http_code" in
        200|201|202|204)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

# Função para verificar saúde do container
check_container_health() {
    local container=$1
    local status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null)
    
    if [ "$status" = "healthy" ]; then
        return 0
    else
        if docker ps --format "{{.Names}}" | grep -q "^${container}$"; then
            return 0
        fi
        return 1
    fi
}

# Função para aguardar container voltar ao saudável
wait_healthy() {
    local container=$1
    local timeout=45
    local elapsed=0
    
    log_info "Aguardando $container recuperar..."
    
    while [ $elapsed -lt $timeout ]; do
        if check_container_health "$container"; then
            log_success "$container RECUPERADO em ${elapsed}s ✅"
            return 0
        fi
        
        sleep 2
        elapsed=$((elapsed + 2))
        echo -n "."
    done
    
    echo ""
    log_error "$container NÃO RECUPEROU em ${timeout}s ❌"
    return 1
}

# Inicializar relatório
cat > "$REPORT" << 'EOF'
# Teste de Failover com Injeção de Carga - Chat4All v2

## Objetivo
Validar que o sistema mantém zero message loss quando o Router Service falha durante processamento de mensagens.

## Metodologia
1. **Fase 1**: Enviar 50 mensagens iniciais e verificar armazenamento
2. **Fase 2**: Derrubar o Router Service (docker kill)
3. **Fase 3**: Enviar 50 mensagens adicionais durante falha
4. **Fase 4**: Recuperar o Router Service (docker start)
5. **Verificação Final**: Validar que todas as 100 mensagens foram processadas (Zero Message Loss)

## Componentes Testados
- **API Gateway**: Aceita requisições durante falha
- **Message Service**: Bufferiza mensagens no Kafka
- **Router Service**: Responsável pelo roteamento para diferentes plataformas
- **Kafka**: Garante entrega confiável de mensagens
- **MongoDB**: Armazena estado final das mensagens

## Critérios de Sucesso
- ✓ Fase 1: 50 mensagens armazenadas no MongoDB
- ✓ Fase 2: Router derribado com sucesso
- ✓ Fase 3: 50 mensagens aceitas com HTTP 202 durante falha
- ✓ Fase 4: Router recuperado automaticamente
- ✓ Verificação Final: Total == Inicial + 100 (Zero Message Loss)

---

EOF

# ============================================================================
# INÍCIO DO TESTE
# ============================================================================

log_phase "FASE 1: CARGA INICIAL (50 mensagens)"

log "Contando mensagens iniciais no MongoDB..."
INITIAL_COUNT=$(count_messages_in_mongodb)

# Validar que é um número
if ! [[ "$INITIAL_COUNT" =~ ^[0-9]+$ ]]; then
    log_warning "Contagem inicial inválida ($INITIAL_COUNT), usando 0"
    INITIAL_COUNT=0
fi

log_success "Contagem inicial: $INITIAL_COUNT mensagens"
echo "**Contagem Inicial**: $INITIAL_COUNT mensagens" >> "$REPORT"
echo "" >> "$REPORT"

log "Enviando 50 mensagens iniciais para $API_URL/api/messages..."

MESSAGES_SENT=0
MESSAGES_FAILED=0

for i in {1..50}; do
    if send_message $i; then
        MESSAGES_SENT=$((MESSAGES_SENT + 1))
        echo -n "."
    else
        MESSAGES_FAILED=$((MESSAGES_FAILED + 1))
        echo -n "x"
    fi
done
echo ""

log_success "Fase 1 completa: $MESSAGES_SENT mensagens enviadas, $MESSAGES_FAILED falhadas"
echo "**Fase 1 Resultado**: $MESSAGES_SENT mensagens enviadas com sucesso" >> "$REPORT"
echo "" >> "$REPORT"

log "Aguardando 5 segundos para processamento..."
sleep 5

log "Contando mensagens no MongoDB após Fase 1..."
AFTER_FIRST_LOAD=$(count_messages_in_mongodb)

# Validar que é um número
if ! [[ "$AFTER_FIRST_LOAD" =~ ^[0-9]+$ ]]; then
    log_warning "Contagem após fase 1 inválida ($AFTER_FIRST_LOAD), usando 0"
    AFTER_FIRST_LOAD=0
fi

log_success "Contagem após Fase 1: $AFTER_FIRST_LOAD mensagens"
echo "**Contagem após Fase 1**: $AFTER_FIRST_LOAD mensagens" >> "$REPORT"
echo "" >> "$REPORT"

# ============================================================================
log_phase "FASE 2: INJETAR FALHA - DERRUBAR ROUTER SERVICE"

log_warning "Derribando container: $ROUTER_CONTAINER"

if docker kill "$ROUTER_CONTAINER" 2>/dev/null; then
    log_success "✅ Router Service DERRIBADO com sucesso!"
    echo "**Ação**: Router Service derribado via 'docker kill'" >> "$REPORT"
else
    log_error "Falha ao derrubar Router Service"
    log_warning "Continuando teste mesmo assim..."
fi

log_warning "⚠️ ROUTER SERVICE FORA DE SERVIÇO - INICIANDO FASE 3"
echo "" >> "$REPORT"

sleep 2

# ============================================================================
log_phase "FASE 3: INJETAR CARGA DURANTE FALHA (50 mensagens)"

log_warning "Router está FORA! Enviando 50 mensagens adicionais..."
log_info "Expectativa: Message Service aceita (HTTP 202) e bufferiza no Kafka"

MESSAGES_SENT_DURING_FAILURE=0
MESSAGES_FAILED_DURING_FAILURE=0

for i in {51..100}; do
    if send_message $i; then
        MESSAGES_SENT_DURING_FAILURE=$((MESSAGES_SENT_DURING_FAILURE + 1))
        echo -n "."
    else
        MESSAGES_FAILED_DURING_FAILURE=$((MESSAGES_FAILED_DURING_FAILURE + 1))
        echo -n "x"
    fi
done
echo ""

log_success "Fase 3 completa: $MESSAGES_SENT_DURING_FAILURE mensagens aceitas durante falha"
echo "**Fase 3 Resultado**: $MESSAGES_SENT_DURING_FAILURE mensagens aceitas pelo Message Service durante falha" >> "$REPORT"
echo "" >> "$REPORT"

log "Contando mensagens NO MONGODB (não deve ter aumentado)..."
DURING_FAILURE=$(count_messages_in_mongodb)

# Validar que é um número
if ! [[ "$DURING_FAILURE" =~ ^[0-9]+$ ]]; then
    log_warning "Contagem durante falha inválida ($DURING_FAILURE), usando 0"
    DURING_FAILURE=0
fi

log_info "Contagem durante falha: $DURING_FAILURE (não deve ter mudado muito)"
echo "**Contagem durante falha**: $DURING_FAILURE (esperado: similar a $AFTER_FIRST_LOAD, pois Router está offline)" >> "$REPORT"
echo "" >> "$REPORT"

# ============================================================================
log_phase "FASE 4: RECUPERAÇÃO - REINICIAR ROUTER SERVICE"

log_warning "Reiniciando Router Service..."

if docker start "$ROUTER_CONTAINER" 2>/dev/null; then
    log_success "✅ docker start executado"
else
    log_error "Falha ao reiniciar Router Service"
fi

if wait_healthy "$ROUTER_CONTAINER"; then
    log_success "✅ Router Service está SAUDÁVEL"
else
    log_error "Router Service não recuperou em tempo"
    log_warning "Continuando verificação mesmo assim..."
fi

log "Aguardando 45 segundos para o Router processar backlog do Kafka..."
sleep 45

# ============================================================================
log_phase "VERIFICAÇÃO FINAL: VALIDAR ZERO MESSAGE LOSS"

log "Contando mensagens FINAIS no MongoDB..."
AFTER_RECOVERY=$(count_messages_in_mongodb)

# Garantir que são números
if ! [[ "$INITIAL_COUNT" =~ ^[0-9]+$ ]]; then
    log_warning "INITIAL_COUNT inválido, usando 0"
    INITIAL_COUNT=0
fi
if ! [[ "$AFTER_RECOVERY" =~ ^[0-9]+$ ]]; then
    log_warning "AFTER_RECOVERY inválido, usando 0"
    AFTER_RECOVERY=0
fi

log_success "Contagem final: $AFTER_RECOVERY mensagens"
echo "**Contagem Final**: $AFTER_RECOVERY mensagens" >> "$REPORT"
echo "" >> "$REPORT"

# Cálculos
EXPECTED_COUNT=$((INITIAL_COUNT + TOTAL_SENT))
MESSAGES_LOST=$((EXPECTED_COUNT - AFTER_RECOVERY))

echo "## Resultados Numéricos" >> "$REPORT"
echo "" >> "$REPORT"
echo "| Métrica | Valor |" >> "$REPORT"
echo "|---------|-------|" >> "$REPORT"
echo "| Contagem Inicial | $INITIAL_COUNT |" >> "$REPORT"
echo "| Mensagens enviadas Fase 1 | 50 |" >> "$REPORT"
echo "| Contagem após Fase 1 | $AFTER_FIRST_LOAD |" >> "$REPORT"
echo "| Mensagens enviadas Fase 3 (durante falha) | 50 |" >> "$REPORT"
echo "| Total esperado (Inicial + 100) | $EXPECTED_COUNT |" >> "$REPORT"
echo "| Contagem final | $AFTER_RECOVERY |" >> "$REPORT"
echo "| Mensagens perdidas | $MESSAGES_LOST |" >> "$REPORT"
echo "" >> "$REPORT"

# Determinar resultado
if [ "$AFTER_RECOVERY" -eq "$EXPECTED_COUNT" ]; then
    echo "" >> "$REPORT"
    echo "## ✅ RESULTADO FINAL: SUCESSO" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "**Zero Message Loss confirmado!**" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "Todas as 100 mensagens foram processadas com sucesso, mesmo com falha do Router Service." >> "$REPORT"
    echo "" >> "$REPORT"
    
    echo ""
    echo -e "${GREEN}═════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}✅ TESTE PASSOU - ZERO MESSAGE LOSS CONFIRMADO!${NC}"
    echo -e "${GREEN}═════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "  Inicial: $INITIAL_COUNT | Total enviado: 100 | Final: $AFTER_RECOVERY"
    echo -e "  Perdidas: $MESSAGES_LOST"
    echo ""
    
    EXIT_CODE=0
else
    echo "" >> "$REPORT"
    echo "## ❌ RESULTADO FINAL: FALHA" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "**Message Loss detectado!**" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "Esperado: $EXPECTED_COUNT mensagens" >> "$REPORT"
    echo "Obtido: $AFTER_RECOVERY mensagens" >> "$REPORT"
    echo "Perdidas: $MESSAGES_LOST mensagens" >> "$REPORT"
    echo "" >> "$REPORT"
    
    echo ""
    echo -e "${RED}═════════════════════════════════════════════════════════════${NC}"
    echo -e "${RED}❌ TESTE FALHOU - MESSAGE LOSS DETECTADO!${NC}"
    echo -e "${RED}═════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "  Esperado: $EXPECTED_COUNT | Obtido: $AFTER_RECOVERY | Perdidas: $MESSAGES_LOST"
    echo ""
    
    EXIT_CODE=1
fi

# Salvar relatório
log ""
log_success "Relatório salvo em: $REPORT"

# Imprimir resumo
echo "" >> "$REPORT"
echo "---" >> "$REPORT"
echo "" >> "$REPORT"
echo "Teste executado em: $(date)" >> "$REPORT"
echo "Sistema: $HOSTNAME" >> "$REPORT"
echo "Docker containers: $(docker ps --quiet | wc -l) rodando" >> "$REPORT"

cat "$REPORT" | tail -40

exit $EXIT_CODE
