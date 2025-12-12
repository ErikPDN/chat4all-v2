# Test Failover With Load - Documentação

## Visão Geral

O script `test-failover-with-load.sh` simula um cenário real de failover onde o sistema continua recebendo requisições mesmo enquanto um serviço crítico (Router Service) está indisponível.

**Objetivo Principal**: Validar **Zero Message Loss** - garantir que nenhuma mensagem é perdida mesmo durante falhas de componentes.

## O que o Script Faz

### Fases de Execução

```
┌─────────────────────────────────────────────────────────────────┐
│                    FLUXO DO TESTE                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  FASE 1: CARGA INICIAL                                          │
│  └─ Enviar 50 mensagens                                         │
│  └─ Verificar armazenamento no MongoDB                          │
│     (Timeout: 5 segundos)                                       │
│                                                                 │
│  FASE 2: INJETAR FALHA                                          │
│  └─ docker kill chat4all-v2-router-service-1                    │
│  └─ Router Service fica OFFLINE                                 │
│                                                                 │
│  FASE 3: CARGA DURANTE FALHA (O TESTE CRÍTICO)                  │
│  └─ Enviar 50 mensagens ADICIONAIS                              │
│  └─ Message Service aceita (HTTP 202) e bufferiza no Kafka      │
│  └─ MongoDB ainda tem só ~50 (Router está offline)              │
│                                                                 │
│  FASE 4: RECUPERAÇÃO                                            │
│  └─ docker start chat4all-v2-router-service-1                   │
│  └─ Aguardar container voltar ao estado "healthy"               │
│  └─ Aguardar 10s para processar backlog do Kafka                │
│                                                                 │
│  VERIFICAÇÃO FINAL                                              │
│  └─ Contar total de mensagens no MongoDB                        │
│  └─ Se Total == Inicial + 100 → ✅ SUCESSO (Zero Message Loss)  │
│  └─ Se Total < Inicial + 100 → ❌ FALHA (Message Loss)          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Pré-requisitos

### 1. Sistema Rodando
```bash
docker-compose up -d
docker-compose ps  # Verificar todos os containers estão "Up"
```

### 2. Ferramentas Necessárias
- `curl` - Para enviar requisições HTTP
- `docker` - Para kill/restart de containers
- `mongosh` ou `mongo` - Para contar mensagens (opcional, script tenta via docker exec)

### 3. Conectividade
- API Gateway acessível em `http://localhost:8080`
- MongoDB acessível em `localhost:27017`
- Router Service container existente: `chat4all-v2-router-service-1`

## Uso

### Execução Simples
```bash
./test-failover-with-load.sh
```

### Com Acompanhamento de Log em Tempo Real
```bash
./test-failover-with-load.sh 2>&1 | tee test-output.log
```

### Apenas Gerar o Script (sem executar)
```bash
bash -n test-failover-with-load.sh  # Verifica sintaxe
```

## Saída Esperada

### Caso de Sucesso (Zero Message Loss)
```
═════════════════════════════════════════════════════════════
✅ TESTE PASSOU - ZERO MESSAGE LOSS CONFIRMADO!
═════════════════════════════════════════════════════════════

  Inicial: 0 | Total enviado: 100 | Final: 100
  Perdidas: 0

Relatório salvo em: logs/failover-tests/FAILOVER_WITH_LOAD_20251212-140900.md
```

### Caso de Falha (Message Loss)
```
═════════════════════════════════════════════════════════════
❌ TESTE FALHOU - MESSAGE LOSS DETECTADO!
═════════════════════════════════════════════════════════════

  Esperado: 100 | Obtido: 87 | Perdidas: 13

Relatório salvo em: logs/failover-tests/FAILOVER_WITH_LOAD_20251212-140900.md
```

## Relatório de Execução

Cada execução gera um relatório em Markdown:
```
logs/failover-tests/FAILOVER_WITH_LOAD_<TIMESTAMP>.md
```

Exemplo de conteúdo:
```markdown
# Teste de Failover com Injeção de Carga - Chat4All v2

## Objetivo
Validar que o sistema mantém zero message loss quando o Router Service falha...

## Resultados Numéricos

| Métrica | Valor |
|---------|-------|
| Contagem Inicial | 0 |
| Mensagens enviadas Fase 1 | 50 |
| Contagem após Fase 1 | 50 |
| Mensagens enviadas Fase 3 (durante falha) | 50 |
| Total esperado (Inicial + 100) | 100 |
| Contagem final | 100 |
| Mensagens perdidas | 0 |

## ✅ RESULTADO FINAL: SUCESSO
```

## Troubleshooting

### "Router Service NÃO RECUPEROU"
**Problema**: O container não volta ao estado "healthy" após `docker start`

**Soluções**:
```bash
# 1. Verificar logs do router
docker logs chat4all-v2-router-service-1 -f

# 2. Verificar saúde manualmente
docker inspect chat4all-v2-router-service-1 | grep -A 5 Health

# 3. Aumentar timeout (editar script, mudar timeout de 45s para 60s)

# 4. Reiniciar manualmente
docker restart chat4all-v2-router-service-1
```

### "Nenhum cliente MongoDB disponível"
**Problema**: Script não consegue contar mensagens no MongoDB

**Soluções**:
```bash
# 1. Instalar mongosh (recomendado)
sudo apt install -y mongosh

# 2. Ou instalar mongo-tools antigos
sudo apt install -y mongodb-tools

# 3. O script vai tentar usar docker exec como fallback
docker exec chat4all-v2-mongodb-1 mongosh --eval "db.messages.countDocuments()"
```

### "HTTP 403 - SignatureDoesNotMatch"
**Problema**: Erro em requisições (não afeta este teste)

**Contexto**: Este erro seria em uploads de arquivo, não em requisições de mensagem

### "curl: (7) Failed to connect"
**Problema**: API Gateway não está acessível

**Soluções**:
```bash
# Verificar se API Gateway está rodando
docker ps | grep api-gateway

# Iniciar toda a stack
docker-compose up -d

# Testar conectividade
curl -v http://localhost:8080/health
```

## Casos de Uso

### 1. Validação Pós-Deploy
```bash
# Após deployar nova versão, validar que failover funciona
./test-failover-with-load.sh

# Verificar relatório
cat logs/failover-tests/FAILOVER_WITH_LOAD_*.md | tail -30
```

### 2. Teste de Regressão
```bash
# Executar periodicamente (ex: antes de commits importantes)
for i in {1..3}; do
  echo "Run $i"
  ./test-failover-with-load.sh || break
  sleep 30
done
```

### 3. Demonstração de Resiliência
```bash
# Mostrar para stakeholders que o sistema é resiliente
./test-failover-with-load.sh

# Explicar as 4 fases:
# - Fase 1: Operação normal
# - Fase 2: Componente falha
# - Fase 3: Sistema continua aceitando requisições
# - Fase 4: Recuperação automática e processamento de backlog
```

## Componentes Envolvidos

| Componente | Papel | Status Esperado |
|-----------|-------|-----------------|
| **API Gateway** | Recebe requisições de clientes | Online (Fase 1-3) / Online (Fase 4) |
| **Message Service** | Bufferiza mensagens no Kafka | Online (sempre) |
| **Router Service** | Roteia para plataformas (WhatsApp, etc) | Online (Fase 1) / **OFFLINE** (Fase 2-3) / Online (Fase 4) |
| **Kafka** | Broker de mensagens confiável | Online (sempre) |
| **MongoDB** | Armazena mensagens processadas | Online (sempre) |

## Fluxo de Mensagem

### Fase 1 (Normal)
```
curl → API Gateway → Message Service → Kafka → Router Service → MongoDB
                              (ACK 202)
```

### Fase 2-3 (Router offline)
```
curl → API Gateway → Message Service → Kafka (BUFFERIZADO)
                              (ACK 202)
      Router Service está MORTO! MongoDB não aumenta
```

### Fase 4 (Recuperação)
```
Kafka backlog → Router Service (RECUPERADO) → MongoDB
      Após processamento: contagem == inicial + 100
```

## Métricas Capturadas

O script coleta automaticamente:
- ⏱️ Tempo de execução de cada fase
- 📊 Contagens de mensagens (inicial, fase 1, fase 3, final)
- ✅ Taxa de sucesso de requisições HTTP
- 🔄 Tempo de recuperação do Router Service
- 📉 Mensagens perdidas (objetivo: zero)

## Interpretação de Resultados

### ✅ Zero Message Loss (SUCESSO)
- **O que significa**: Sistema é resiliente a falhas de componentes
- **Implicação**: Arquitetura com Kafka está funcionando
- **Próximo passo**: Pode considerar para produção

### ❌ Message Loss (FALHA)
- **O que significa**: Algumas mensagens foram perdidas durante failover
- **Causas possíveis**:
  - Kafka não persistiu mensagens adequadamente
  - Router Service não processou backlog
  - MongoDB inacessível
  - Problema de rede
- **Ações**:
  - Revisar logs dos serviços
  - Verificar configuração de persistência do Kafka
  - Aumentar timeout de recuperação

## Próximas Execuções

### Agendar Testes Regulares
```bash
# Adicionar ao crontab (executa diariamente às 2 AM)
0 2 * * * cd /home/erik/java/projects/chat4all-v2 && \
  ./test-failover-with-load.sh >> logs/daily-tests.log 2>&1
```

### Monitorar Histórico
```bash
# Ver todos os testes executados
ls -lh logs/failover-tests/FAILOVER_WITH_LOAD_*.md

# Comparar resultados
grep "Mensagens perdidas" logs/failover-tests/*.md
```

## Perguntas Frequentes

**P: Por que 50 mensagens em cada fase?**
R: Número pequeno o suficiente para executar rápido, grande o suficiente para ser representativo.

**P: Por que 5 segundos após Fase 1?**
R: Tempo suficiente para Kafka processar e Router entregar as mensagens no MongoDB.

**P: Por que 10 segundos após Fase 4?**
R: Tempo para Router recuperado processar todo o backlog do Kafka acumulado.

**P: Posso aumentar as mensagens (ex: 500 em cada fase)?**
R: Sim! Edite as linhas `for i in {1..50}` para `{1..500}`. Leve em conta que levará mais tempo.

**P: O script modifica dados?**
R: Sim, insere 100 novas mensagens no MongoDB. Se não quiser, remova as linhas de envio.

## Contato e Suporte

Para problemas ou melhorias no script:
1. Verifique os logs em `logs/failover-tests/`
2. Veja a saída completa com `2>&1 | tee output.log`
3. Consulte os logs dos containers individuais com `docker logs <container>`

---

**Versão do Script**: 1.0
**Última Atualização**: Dezembro 2025
**Status**: Pronto para uso em produção
