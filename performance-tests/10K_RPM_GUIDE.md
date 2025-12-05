# Atingindo 10.000 Requisições por Minuto

## Meta de Performance

**Requisito**: FR-012 - Tempo de resposta da API < 500ms (P95)  
**Meta de Throughput**: 10.000 req/min = **167 req/s**

## Como Funciona o Teste

### Executor: Constant Arrival Rate

O teste usa o executor `constant-arrival-rate` do K6, que:

1. **Mantém RPS exato**: Independente do tempo de resposta
2. **Ajusta VUs automaticamente**: Adiciona VUs se necessário para manter a taxa
3. **Previne backpressure**: Não deixa requisições acumularem

### Configuração

```javascript
{
  executor: 'constant-arrival-rate',
  rate: 167,              // 167 requisições por segundo
  timeUnit: '1s',         // por segundo
  duration: '5m',         // sustenta por 5 minutos
  preAllocatedVUs: 200,   // VUs pré-alocados
  maxVUs: 500,            // limite máximo de VUs
}
```

### Distribuição de Requisições

| Operação | % | Endpoint | Método |
|----------|---|----------|--------|
| Enviar mensagem | 50% | `/api/messages` | POST |
| Buscar histórico | 30% | `/api/v1/conversations/{id}/messages` | GET |
| Health check | 15% | `/actuator/health` | GET |
| Webhook (inbound) | 5% | `/api/connectors/whatsapp/webhook` | POST |

**Razão**: Simula carga realista onde envio de mensagens é mais comum que consultas.

## Executando o Teste

### Opção 1: Script Simplificado (Recomendado)

```bash
# Do diretório raiz do projeto
./test-10k-rpm.sh

# Teste mais longo (10 minutos)
./test-10k-rpm.sh 10m

# Meta maior (15K req/min)
./test-10k-rpm.sh 5m 15000
```

### Opção 2: K6 Direto

```bash
cd performance-tests

k6 run scenarios/target-10k-rpm.js \
  -e BASE_URL=http://localhost:8080 \
  -e TARGET_RPM=10000 \
  -e DURATION=5m
```

### Opção 3: Com Output para Análise

```bash
cd performance-tests

k6 run scenarios/target-10k-rpm.js \
  -e TARGET_RPM=10000 \
  --out json=results/test-$(date +%Y%m%d-%H%M%S).json
```

## Interpretando Resultados

### ✅ Teste PASSOU

```
✓ http_req_duration.............: avg=145ms  p(95)=298ms  p(99)=450ms
✓ http_req_failed...............: 0.50%      ✓ 250        ✗ 49750
✓ http_reqs.....................: 50000      166.67/s
```

**Significado**:
- ✅ P95 < 500ms (target: <500ms)
- ✅ P99 < 1000ms (target: <1000ms)
- ✅ Error rate < 1% (target: <1%)
- ✅ Throughput >= 167 req/s (95% da meta)

### ❌ Teste FALHOU

```
✗ http_req_duration.............: avg=850ms  p(95)=1200ms  p(99)=2500ms
✗ http_req_failed...............: 5.00%      ✓ 2500       ✗ 47500
✗ http_reqs.....................: 50000      140/s
```

**Problemas identificados**:
- ❌ P95 > 500ms → Sistema lento
- ❌ Error rate > 1% → Erros frequentes
- ❌ Throughput < 167 req/s → Não sustenta a carga

## Ajustando para Passar

### 1. Escalar Serviços

**docker-compose.yml**:
```yaml
services:
  message-service:
    deploy:
      replicas: 3  # Aumente de 1 para 3
    environment:
      SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 50
```

### 2. Otimizar Banco de Dados

**PostgreSQL**:
```sql
-- Verificar conexões
SELECT count(*) FROM pg_stat_activity;

-- Ajustar max_connections
ALTER SYSTEM SET max_connections = 200;
SELECT pg_reload_conf();
```

**MongoDB**:
```javascript
// Verificar operações lentas
db.currentOp({ "secs_running": { "$gt": 1 } })

// Criar índices faltantes
db.messages.createIndex({ conversationId: 1, timestamp: -1 })
```

### 3. Ajustar Connection Pools

**application.yml**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50      # Era: 20
      minimum-idle: 10           # Era: 5
      connection-timeout: 30000  # 30s
```

### 4. Kafka Partitions

```bash
# Aumentar partições para paralelização
kafka-topics --alter --topic chat.messages.outbound --partitions 12
```

### 5. Aumentar VUs Pré-alocados

Se você ver `VUs spawning` no output, significa que K6 está criando VUs dinamicamente. Aumente `preAllocatedVUs`:

**target-10k-rpm.js**:
```javascript
preAllocatedVUs: 300,  // Aumentar de 200
maxVUs: 800,           // Aumentar de 500
```

## Métricas Importantes

### http_req_duration
- **Média (avg)**: Tempo médio de resposta
- **P95**: 95% das requisições abaixo desse valor ⭐
- **P99**: 99% das requisições abaixo desse valor

**Target**: P95 < 500ms

### http_req_failed
- Taxa de requisições que falharam (status >= 400 ou timeout)

**Target**: < 1%

### http_reqs
- Total de requisições feitas
- **req/s**: Throughput real

**Target**: >= 167 req/s (10.000 req/min)

### VUs
- Número de usuários virtuais ativos
- Se VUs > maxVUs → Sistema não aguenta a carga

## Troubleshooting

### Erro: "executor: not enough VUs, consider raising..."

**Solução**: Aumentar `preAllocatedVUs` ou `maxVUs`

```javascript
preAllocatedVUs: 500,
maxVUs: 1000,
```

### Erro: Connection timeout

**Solução**: Aumentar timeout do K6

```bash
k6 run scenarios/target-10k-rpm.js \
  --http-debug="full" \
  --no-connection-reuse=false
```

### Alta latência (P95 > 500ms)

**Checklist**:
1. ✅ Serviços escalados (3+ réplicas)?
2. ✅ Connection pools dimensionados?
3. ✅ Índices de banco criados?
4. ✅ Kafka com partições suficientes?
5. ✅ Recursos (CPU/RAM) disponíveis?

### Baixo throughput (< 167 req/s)

**Possíveis causas**:
- Backpressure no Kafka
- Connection pool esgotado
- Database locks
- Network bottleneck

**Debug**:
```bash
# Verificar logs
docker logs chat4all-message-service | tail -100

# Verificar métricas Prometheus
curl http://localhost:8081/actuator/metrics/http.server.requests

# Verificar Kafka lag
docker exec chat4all-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group message-consumer
```

## Próximos Passos

1. **Smoke test** primeiro: `k6 run scenarios/smoke-test.js`
2. **10K req/min test**: `./test-10k-rpm.sh`
3. **Analisar resultados** e ajustar conforme necessário
4. **Load test completo**: `k6 run scenarios/concurrent-conversations.js`
5. **Spike test**: `k6 run scenarios/spike-test.js`

## Recursos Adicionais

- [K6 Documentation](https://k6.io/docs/)
- [Constant Arrival Rate Executor](https://k6.io/docs/using-k6/scenarios/executors/constant-arrival-rate/)
- [K6 Thresholds](https://k6.io/docs/using-k6/thresholds/)
- [Performance Testing Best Practices](https://k6.io/docs/testing-guides/api-load-testing/)
