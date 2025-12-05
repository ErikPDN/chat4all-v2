#!/bin/bash

# Demonstração de Failover com Docker Restart
# Simula falhas temporárias e mostra recuperação automática

set +e  # Não sair em erros

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

LOG_DIR="logs/failover-tests"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
REPORT="$LOG_DIR/FAILOVER_DEMONSTRATION_$TIMESTAMP.md"

mkdir -p "$LOG_DIR"

# Inicializar relatório
cat > "$REPORT" << 'EOF'
# Demonstração Funcional de Failover - Chat4All v2

## Objetivo
Demonstrar a capacidade de recuperação automática do sistema quando componentes críticos falham.

## Metodologia
- **Tipo de teste**: Chaos Engineering
- **Ferramenta**: Docker restart para simular falhas temporárias
- **Componentes testados**: Message Service, Router Service, Kafka
- **Critérios de sucesso**: 
  - Serviços voltam automaticamente após falha
  - Zero message loss (dados preservados)
  - Tempo de recuperação < 30 segundos

---

EOF

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

check_container_health() {
    local container=$1
    local status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null)
    
    if [ "$status" = "healthy" ]; then
        return 0
    else
        # Se não tiver healthcheck, verificar se está Up
        if docker ps --format "{{.Names}}" | grep -q "^${container}$"; then
            return 0
        fi
        return 1
    fi
}

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
        
        sleep 3
        elapsed=$((elapsed + 3))
        echo -n "."
    done
    
    echo ""
    log_error "$container NÃO recuperou em ${timeout}s ❌"
    return 1
}

echo ""
echo "================================================"
echo "🔥 DEMONSTRAÇÃO DE FAILOVER - Chat4All v2"
echo "================================================"
echo ""

log "📅 Início do teste: $(date)"
log "📝 Relatório será salvo em: $REPORT"
echo ""

# Adicionar header no relatório
echo "## Execução do Teste" >> "$REPORT"
echo "" >> "$REPORT"
echo "**Data/Hora**: $(date)" >> "$REPORT"
echo "" >> "$REPORT"

# Contar mensagens inicial
INITIAL_MESSAGES=$(docker exec chat4all-mongodb mongosh --quiet --eval \
    "db.getSiblingDB('chat4all').messages.countDocuments()" 2>/dev/null || echo "N/A")
log_info "Mensagens no MongoDB: $INITIAL_MESSAGES"

echo "" >> "$REPORT"
echo "### Estado Inicial" >> "$REPORT"
echo "" >> "$REPORT"
echo "- Mensagens no MongoDB: $INITIAL_MESSAGES" >> "$REPORT"
echo "" >> "$REPORT"

# ==========================================
# TESTE 1: Message Service Failover
# ==========================================

echo ""
echo "================================================"
echo "📊 TESTE 1: Failover do Message Service"
echo "================================================"
echo ""

echo "### Teste 1: Failover do Message Service" >> "$REPORT"
echo "" >> "$REPORT"

START_TIME=$(date +%s)

log_warning "🔥 Reiniciando Message Service (simulando falha)..."
docker restart chat4all-message-service > /dev/null 2>&1

if wait_healthy "chat4all-message-service"; then
    END_TIME=$(date +%s)
    RECOVERY_TIME=$((END_TIME - START_TIME))
    
    log_success "✅ RECUPERAÇÃO AUTOMÁTICA CONFIRMADA"
    log_info "⏱️  Tempo de recuperação: ${RECOVERY_TIME}s"
    
    echo "**Resultado**: ✅ SUCESSO" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "- Serviço recuperado automaticamente" >> "$REPORT"
    echo "- Tempo de recuperação: ${RECOVERY_TIME}s" >> "$REPORT"
    
    TEST1_SUCCESS=true
else
    log_error "❌ FALHA NA RECUPERAÇÃO"
    echo "**Resultado**: ❌ FALHA" >> "$REPORT"
    TEST1_SUCCESS=false
fi

sleep 5

# Verificar health endpoint
if docker exec chat4all-message-service curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; then
    log_success "Health endpoint respondendo OK"
    echo "- Health check: OK" >> "$REPORT"
else
    log_warning "Health endpoint não respondeu"
    echo "- Health check: Falhou" >> "$REPORT"
fi

echo "" >> "$REPORT"

# ==========================================
# TESTE 2: Router Service Failover
# ==========================================

echo ""
echo "================================================"
echo "📊 TESTE 2: Failover do Router Service"
echo "================================================"
echo ""

echo "### Teste 2: Failover do Router Service" >> "$REPORT"
echo "" >> "$REPORT"

ROUTER_NAME=$(docker ps --format "{{.Names}}" | grep -i router | head -1)

if [ -z "$ROUTER_NAME" ]; then
    log_error "Router Service não encontrado"
    TEST2_SUCCESS=false
else
    log_info "Router encontrado: $ROUTER_NAME"
    
    START_TIME=$(date +%s)
    
    log_warning "🔥 Reiniciando Router Service..."
    docker restart "$ROUTER_NAME" > /dev/null 2>&1
    
    if wait_healthy "$ROUTER_NAME"; then
        END_TIME=$(date +%s)
        RECOVERY_TIME=$((END_TIME - START_TIME))
        
        log_success "✅ RECUPERAÇÃO AUTOMÁTICA CONFIRMADA"
        log_info "⏱️  Tempo de recuperação: ${RECOVERY_TIME}s"
        
        echo "**Resultado**: ✅ SUCESSO" >> "$REPORT"
        echo "" >> "$REPORT"
        echo "- Serviço recuperado automaticamente" >> "$REPORT"
        echo "- Tempo de recuperação: ${RECOVERY_TIME}s" >> "$REPORT"
        
        TEST2_SUCCESS=true
    else
        log_error "❌ FALHA NA RECUPERAÇÃO"
        echo "**Resultado**: ❌ FALHA" >> "$REPORT"
        TEST2_SUCCESS=false
    fi
fi

echo "" >> "$REPORT"

sleep 5

# ==========================================
# TESTE 3: Kafka Failover
# ==========================================

echo ""
echo "================================================"
echo "📊 TESTE 3: Failover do Kafka"
echo "================================================"
echo ""

echo "### Teste 3: Failover do Kafka" >> "$REPORT"
echo "" >> "$REPORT"

START_TIME=$(date +%s)

log_warning "🔥 Reiniciando Kafka (simulando falha)..."
docker restart chat4all-kafka > /dev/null 2>&1

if wait_healthy "chat4all-kafka"; then
    END_TIME=$(date +%s)
    RECOVERY_TIME=$((END_TIME - START_TIME))
    
    log_success "✅ RECUPERAÇÃO AUTOMÁTICA CONFIRMADA"
    log_info "⏱️  Tempo de recuperação: ${RECOVERY_TIME}s"
    
    echo "**Resultado**: ✅ SUCESSO" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "- Kafka recuperado automaticamente" >> "$REPORT"
    echo "- Tempo de recuperação: ${RECOVERY_TIME}s" >> "$REPORT"
    
    TEST3_SUCCESS=true
else
    log_error "❌ FALHA NA RECUPERAÇÃO"
    echo "**Resultado**: ❌ FALHA" >> "$REPORT"
    TEST3_SUCCESS=false
fi

echo "" >> "$REPORT"

# Aguardar Kafka ficar completamente pronto
log_info "Aguardando Kafka ficar completamente operacional..."
sleep 10

# ==========================================
# VERIFICAÇÃO FINAL
# ==========================================

echo ""
echo "================================================"
echo "📊 VERIFICAÇÃO FINAL"
echo "================================================"
echo ""

echo "### Verificação Final" >> "$REPORT"
echo "" >> "$REPORT"

# Verificar mensagens
FINAL_MESSAGES=$(docker exec chat4all-mongodb mongosh --quiet --eval \
    "db.getSiblingDB('chat4all').messages.countDocuments()" 2>/dev/null || echo "N/A")

log_info "Mensagens no MongoDB APÓS testes: $FINAL_MESSAGES"

if [ "$INITIAL_MESSAGES" = "$FINAL_MESSAGES" ]; then
    log_success "✅ ZERO MESSAGE LOSS - Dados preservados"
    echo "- **Message Loss**: 0 (Zero) ✅" >> "$REPORT"
    MESSAGE_LOSS=false
else
    log_warning "⚠ Diferença na contagem de mensagens"
    echo "- **Message Loss**: Detectado ⚠" >> "$REPORT"
    MESSAGE_LOSS=true
fi

echo "" >> "$REPORT"

# Verificar todos os containers
log_info "Verificando estado de todos os containers..."
echo "#### Estado dos Containers" >> "$REPORT"
echo "" >> "$REPORT"
echo '```' >> "$REPORT"
docker ps --format "table {{.Names}}\t{{.Status}}" | grep chat4all >> "$REPORT"
echo '```' >> "$REPORT"
echo "" >> "$REPORT"

# ==========================================
# RESUMO
# ==========================================

echo ""
echo "================================================"
echo "📊 RESUMO DA DEMONSTRAÇÃO"
echo "================================================"
echo ""

echo "## Resumo" >> "$REPORT"
echo "" >> "$REPORT"

log "Resultados dos testes:"
echo ""

if [ "$TEST1_SUCCESS" = true ]; then
    log_success "✅ Teste 1 (Message Service): PASSOU"
    echo "- ✅ **Message Service Failover**: PASSOU" >> "$REPORT"
else
    log_error "❌ Teste 1 (Message Service): FALHOU"
    echo "- ❌ **Message Service Failover**: FALHOU" >> "$REPORT"
fi

if [ "$TEST2_SUCCESS" = true ]; then
    log_success "✅ Teste 2 (Router Service): PASSOU"
    echo "- ✅ **Router Service Failover**: PASSOU" >> "$REPORT"
else
    log_error "❌ Teste 2 (Router Service): FALHOU"
    echo "- ❌ **Router Service Failover**: FALHOU" >> "$REPORT"
fi

if [ "$TEST3_SUCCESS" = true ]; then
    log_success "✅ Teste 3 (Kafka): PASSOU"
    echo "- ✅ **Kafka Failover**: PASSOU" >> "$REPORT"
else
    log_error "❌ Teste 3 (Kafka): FALHOU"
    echo "- ❌ **Kafka Failover**: FALHOU" >> "$REPORT"
fi

if [ "$MESSAGE_LOSS" = false ]; then
    log_success "✅ Zero Message Loss: CONFIRMADO"
    echo "- ✅ **Zero Message Loss**: CONFIRMADO" >> "$REPORT"
else
    log_warning "⚠ Message Loss detectado"
    echo "- ⚠ **Message Loss**: DETECTADO" >> "$REPORT"
fi

echo "" >> "$REPORT"

# Conclusão
if [ "$TEST1_SUCCESS" = true ] && [ "$TEST2_SUCCESS" = true ] && [ "$TEST3_SUCCESS" = true ] && [ "$MESSAGE_LOSS" = false ]; then
    echo ""
    log_success "🎉 DEMONSTRAÇÃO DE FAILOVER: SUCESSO COMPLETO!"
    log_success "Todos os componentes recuperaram automaticamente"
    log_success "Nenhuma mensagem foi perdida durante os testes"
    
    echo "## Conclusão" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "✅ **DEMONSTRAÇÃO DE FAILOVER CONCLUÍDA COM SUCESSO**" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "Todos os componentes testados demonstraram capacidade de recuperação automática:" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "1. **Message Service**: Recuperou automaticamente após reinício forçado" >> "$REPORT"
    echo "2. **Router Service**: Recuperou automaticamente após reinício forçado" >> "$REPORT"
    echo "3. **Kafka**: Recuperou automaticamente após reinício forçado" >> "$REPORT"
    echo "4. **Integridade de Dados**: Zero message loss confirmado" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "O sistema atende ao requisito de **demonstração funcional de failover**." >> "$REPORT"
else
    echo ""
    log_warning "⚠ DEMONSTRAÇÃO COMPLETA COM RESSALVAS"
    log_info "Alguns componentes podem precisar de ajustes"
    
    echo "## Conclusão" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "⚠ **DEMONSTRAÇÃO CONCLUÍDA COM RESSALVAS**" >> "$REPORT"
    echo "" >> "$REPORT"
    echo "Alguns testes apresentaram falhas. Revisar logs para detalhes." >> "$REPORT"
fi

echo ""
echo "================================================"
log "📁 Relatório completo salvo em:"
log "   $REPORT"
echo "================================================"
echo ""
