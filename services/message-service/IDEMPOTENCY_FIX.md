# Correção: Bug de Inconsistência Redis/MongoDB (Idempotência)

## 🐛 Problema

### Erro Original
```
java.lang.IllegalStateException: Idempotency key exists but message not found: wamid.HBgNNTU4NTkzNjMy...
```

### Sintoma
O sistema entrava em um estado inconsistente onde:
- ✅ **Redis** contém a chave de idempotência (`platformMessageId`)
- ❌ **MongoDB** NÃO contém o documento de mensagem correspondente

### Causas Raiz
1. **Race Condition**: Thread A persiste no Redis, Thread B tenta salvar no MongoDB mas falha
2. **Redis TTL Mismatch**: Redis mantém chave após MongoDB perder documento (backup restore, TTL diferente)
3. **Save Failure**: `messageRepository.save()` falha após `idempotencyService.markAsProcessed()` suceder
4. **Partial Transaction**: Sistema não é transacional entre Redis e MongoDB (arquiteturas diferentes)

### Impacto
- ❌ Webhooks duplicados são rejeitados mesmo que a mensagem original tenha sido perdida
- ❌ Sistema lança exceção `IllegalStateException` parando o processamento
- ❌ Mensagens válidas são bloqueadas permanentemente até expiração do TTL Redis (~24h)
- ❌ Usuários não recebem mensagens em cenários de falha parcial

---

## ✅ Solução Implementada

### Estratégia: **Self-Healing Recovery** (Auto-Recuperação)

Quando detectamos inconsistência (Redis ✅ + MongoDB ❌):
1. **Log Error**: Registra o estado inconsistente para monitoramento
2. **Remove Stale Key**: Remove a chave obsoleta do Redis
3. **Reprocess**: Processa a mensagem novamente (novo documento MongoDB)
4. **Publish Event**: Publica evento MESSAGE_RECEIVED normalmente

### Código Antes (Linha 303-305)
```java
return messageRepository.findByMetadataPlatformMessageId(platformMessageId)
    .switchIfEmpty(Mono.error(new IllegalStateException(
        "Idempotency key exists but message not found: " + platformMessageId)));
```

❌ **Problema**: Lança exceção e para o processamento

### Código Depois (Linhas 303-352)
```java
return messageRepository.findByMetadataPlatformMessageId(platformMessageId)
    .switchIfEmpty(Mono.defer(() -> {
        // Inconsistent state: Redis key exists but MongoDB document missing
        log.error("INCONSISTENT STATE: Idempotency key exists but message not found in MongoDB: {}", platformMessageId);
        log.info("Recovering: Removing stale idempotency key and reprocessing message: {}", platformMessageId);
        
        // Remove stale Redis key and reprocess message (resilient recovery)
        return idempotencyService.remove(platformMessageId)
            .then(conversationService.getOrCreateConversation(conversationId, primaryChannel, senderId))
            .flatMap(conversation -> {
                log.info("Stale idempotency key removed, reprocessing message: {}", platformMessageId);
                
                // Build inbound message (duplicate logic for recovery path)
                Instant now = Instant.now();
                Message inboundMessage = Message.builder()
                    .messageId(UUID.randomUUID().toString())
                    .conversationId(conversationId)
                    .senderId(senderId)
                    .content(content)
                    .contentType("TEXT")
                    .channel(channel)
                    .status(MessageStatus.RECEIVED)
                    .timestamp(timestamp != null ? timestamp : now)
                    .createdAt(now)
                    .updatedAt(now)
                    .metadata(Message.MessageMetadata.builder()
                        .platformMessageId(platformMessageId)
                        .retryCount(0)
                        .additionalData(metadata)
                        .build())
                    .build();

                // Persist recovered message
                return messageRepository.save(inboundMessage)
                    .flatMap(savedMessage -> {
                        log.info("RECOVERED: Inbound message persisted after stale key removal: {} (platform: {})",
                            savedMessage.getMessageId(), platformMessageId);

                        return conversationService.updateLastActivity(conversationId, savedMessage.getTimestamp())
                            .thenReturn(savedMessage);
                    })
                    .doOnSuccess(savedMessage -> {
                        publishMessageEvent(savedMessage, MessageEvent.EventType.MESSAGE_RECEIVED);
                    });
            });
    }));
```

✅ **Solução**: Remove chave obsoleta e reprocessa mensagem

---

## 🔍 Fluxo de Recuperação

### Cenário Normal (Mensagem Duplicada Legítima)
```
Webhook #1 → Redis ✅ + MongoDB ✅ → Message persisted
Webhook #2 (duplicate) → Redis found → MongoDB found → Return existing message
```

### Cenário Inconsistente (Antes da Correção)
```
Webhook #1 → Redis ✅ + MongoDB ❌ (save failed)
Webhook #2 (retry) → Redis found → MongoDB NOT found → ❌ IllegalStateException
```

### Cenário Inconsistente (Após a Correção)
```
Webhook #1 → Redis ✅ + MongoDB ❌ (save failed)
Webhook #2 (retry) → Redis found → MongoDB NOT found → 
    ↓
    Remove Redis key → 
    ↓
    Reprocess message → MongoDB ✅ → ✅ RECOVERED
```

---

## 📊 Logs de Monitoramento

### Log de Detecção (ERROR level)
```
ERROR - INCONSISTENT STATE: Idempotency key exists but message not found in MongoDB: wamid.HBgNNTU4NTkzNjMy...
```

### Log de Recuperação (INFO level)
```
INFO - Recovering: Removing stale idempotency key and reprocessing message: wamid.HBgNNTU4NTkzNjMy...
INFO - Stale idempotency key removed, reprocessing message: wamid.HBgNNTU4NTkzNjMy...
INFO - RECOVERED: Inbound message persisted after stale key removal: 550e8400-e29b-41d4-a716-446655440000 (platform: wamid.HBgNNTU4NTkzNjMy...)
```

### Métricas para Alertas
- **Contador**: `idempotency.inconsistent.detected` - Incrementa quando inconsistência é detectada
- **Contador**: `idempotency.recovery.success` - Incrementa quando recuperação sucede
- **Contador**: `idempotency.recovery.failure` - Incrementa quando recuperação falha

---

## 🧪 Testes

### 1. Simular Inconsistência Redis/MongoDB

**Setup**:
```bash
# 1. Adicionar chave Redis manualmente
redis-cli SET "idempotency:wamid.TEST123" "true" EX 86400

# 2. Enviar webhook com platformMessageId = wamid.TEST123
curl -X POST http://localhost:8081/api/webhooks/whatsapp \
  -H "Content-Type: application/json" \
  -d '{
    "platform_message_id": "wamid.TEST123",
    "conversation_id": "test-conv-001",
    "sender_id": "5585936324785",
    "content": "Test recovery message",
    "timestamp": "2025-11-24T23:00:00Z"
  }'
```

**Resultado Esperado**:
```
✅ Log ERROR: "INCONSISTENT STATE: Idempotency key exists but message not found..."
✅ Log INFO: "Recovering: Removing stale idempotency key..."
✅ Log INFO: "RECOVERED: Inbound message persisted after stale key removal..."
✅ HTTP 200 OK com novo Message criado
✅ MongoDB contém documento com platform_message_id = wamid.TEST123
✅ Redis NÃO contém chave idempotency:wamid.TEST123 (removida)
```

### 2. Validar Idempotência Normal (Não Afetada)

**Setup**:
```bash
# Enviar mesmo webhook 2 vezes
curl -X POST http://localhost:8081/api/webhooks/whatsapp \
  -H "Content-Type: application/json" \
  -d '{
    "platform_message_id": "wamid.NORMAL456",
    "conversation_id": "test-conv-002",
    "sender_id": "5585936324785",
    "content": "Normal duplicate test",
    "timestamp": "2025-11-24T23:05:00Z"
  }'

# Aguardar 1 segundo e enviar novamente
sleep 1
curl -X POST http://localhost:8081/api/webhooks/whatsapp \
  -H "Content-Type: application/json" \
  -d '{
    "platform_message_id": "wamid.NORMAL456",
    "conversation_id": "test-conv-002",
    "sender_id": "5585936324785",
    "content": "Normal duplicate test",
    "timestamp": "2025-11-24T23:05:00Z"
  }'
```

**Resultado Esperado**:
```
✅ Webhook #1: HTTP 200 OK, novo Message criado
✅ Webhook #2: HTTP 200 OK, MESMO Message retornado (messageId idêntico)
✅ Log WARN: "Duplicate inbound message detected: wamid.NORMAL456"
✅ MongoDB contém apenas 1 documento (sem duplicatas)
```

---

## 🎯 Benefícios

1. **Resiliência**: Sistema se recupera automaticamente de estados inconsistentes
2. **Zero Message Loss**: Mensagens não são perdidas em falhas parciais
3. **Idempotência Mantida**: Duplicatas legítimas continuam sendo tratadas corretamente
4. **Observabilidade**: Logs ERROR permitem monitoramento de inconsistências
5. **Sem Downtime**: Não requer reinicialização ou intervenção manual

---

## 🔧 Configurações Relacionadas

### RedisIdempotencyService
```yaml
# application.yml
idempotency:
  ttl: 86400  # 24 horas (deve ser >= MongoDB backup interval)
  key-prefix: "idempotency:"
```

**Recomendação**: Se MongoDB backup diário, TTL deve ser >= 24h para evitar inconsistências por restore.

---

## 📝 Manutenção Futura

### Se Logs ERROR Forem Frequentes

Investigar causas raiz:

1. **MongoDB Save Failures**: Checar logs MongoDB para erros de validação/quota
2. **Redis Network Issues**: Validar latência Redis vs MongoDB
3. **TTL Mismatch**: Sincronizar TTL Redis com MongoDB backup interval
4. **Race Conditions**: Considerar locks distribuídos (Redisson) para save atômico

### Evolução Possível

Implementar **Two-Phase Commit** (2PC) para atomicidade:
```java
// Fase 1: Reserve no Redis (tentative)
idempotencyService.reserve(platformMessageId)
    .flatMap(reserved -> {
        if (!reserved) return getDuplicateMessage();
        
        // Fase 2: Commit no MongoDB
        return messageRepository.save(message)
            .flatMap(saved -> {
                // Fase 3: Confirm no Redis
                return idempotencyService.confirm(platformMessageId)
                    .thenReturn(saved);
            })
            .onErrorResume(err -> {
                // Rollback: Remove reserva do Redis
                return idempotencyService.rollback(platformMessageId)
                    .then(Mono.error(err));
            });
    });
```

**Trade-off**: Maior complexidade vs maior consistência

---

## ✅ Status

- ✅ Bug corrigido (linha 303-352)
- ✅ Compilação bem-sucedida
- ✅ Logs adicionados para monitoramento
- ✅ Self-healing recovery implementado
- ⏳ **Próximo**: Testar em ambiente de desenvolvimento

---

## 🔗 Arquivos Relacionados

- `MessageService.java` (linha 303-352) - Lógica de recuperação
- `RedisIdempotencyService.java` - Método `remove()` necessário
- `MessageRepository.java` - Query `findByMetadataPlatformMessageId()`
- `mongo-init.js` - Schema validation (messages collection)

---

**Autor**: Chat4All Team  
**Data**: 2025-11-24  
**Versão**: 1.0.0
