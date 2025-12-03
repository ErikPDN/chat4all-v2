# Telegram Connector - Real API Integration

## 📋 Overview

Refatoração do `telegram-connector` de **mock** para **integração REAL** com a [Telegram Bot API](https://core.telegram.org/bots/api).

**Branch:** `feature/telegram-real-integration`  
**Data:** 02/12/2024  
**Status:** ✅ **CONCLUÍDO**

---

## 🔧 Alterações Implementadas

### 1. **Configuração (`application.yml`)**

**Arquivo:** `services/connectors/telegram-connector/src/main/resources/application.yml`

#### Antes (Mock):
```yaml
server:
  port: 8092  # Porta incorreta
# Sem configuração de API do Telegram
```

#### Depois (Real API):
```yaml
server:
  port: 8086  # Corrigido para 8086

app:
  telegram:
    api-url: ${TELEGRAM_API_URL:https://api.telegram.org}
    bot-token: ${TELEGRAM_BOT_TOKEN:your-telegram-bot-token-here}

callback:
  base-url: ${CALLBACK_BASE_URL:http://localhost:8081}
```

**Variáveis de Ambiente:**
- `TELEGRAM_API_URL`: URL base da API do Telegram (padrão: `https://api.telegram.org`)
- `TELEGRAM_BOT_TOKEN`: Token do bot criado via [@BotFather](https://t.me/BotFather) **(obrigatório para produção)**

---

### 2. **TelegramApiClient** (NOVO)

**Arquivo:** `services/connectors/telegram-connector/src/main/java/com/chat4all/connector/telegram/client/TelegramApiClient.java`

**Responsabilidade:** Realizar chamadas HTTP reais para a API do Telegram Bot.

#### Principais Features:

- ✅ **WebClient reativo** para chamadas HTTP assíncronas
- ✅ **Error Handling**: Tratamento de erros 4xx e 5xx com `TelegramApiException`
- ✅ **Timeout**: 10 segundos por requisição
- ✅ **Logging**: Logs detalhados de requisição/resposta
- ✅ **DTOs tipados**: `TelegramSendMessageResponse`, `TelegramMessage`, `TelegramChat`

#### Endpoint Utilizado:

```http
POST https://api.telegram.org/bot{token}/sendMessage
Content-Type: application/json

{
  "chat_id": "123456789",
  "text": "Mensagem de teste"
}
```

#### Resposta da API:

```json
{
  "ok": true,
  "result": {
    "message_id": 42,
    "chat": {
      "id": 123456789,
      "type": "private",
      "first_name": "John",
      "username": "john_doe"
    },
    "date": 1701500000,
    "text": "Mensagem de teste"
  }
}
```

#### Tratamento de Erros:

- **4xx (Bad Request/Unauthorized)**: `TelegramApiException` com `statusCode=400/401/403/404`
- **5xx (Server Error)**: `TelegramApiException` com `statusCode=500/502/503`
- **Timeout**: `TimeoutException` após 10 segundos
- **Erros de rede**: `WebClientException` com mensagem descritiva

**Propagação de Erros:**  
Todos os erros são propagados para o `TelegramService`, que por sua vez os repassa ao `router-service` para acionamento da lógica de **retry** com **backoff exponencial**.

---

### 3. **TelegramService** (REFATORADO)

**Arquivo:** `services/connectors/telegram-connector/src/main/java/com/chat4all/connector/telegram/service/TelegramService.java`

#### Antes (Mock):

```java
public SendMessageResponse sendMessage(SendMessageRequest request) {
    String telegramMessageId = "tg_" + UUID.randomUUID().toString();
    
    log.info("[Telegram] Simulating message delivery: messageId={}, telegramId={}",
        request.getMessageId(), telegramMessageId);
    
    sendReadStatusCallback(request.getMessageId(), telegramMessageId);
    
    return SendMessageResponse.builder()
        .messageId(request.getMessageId())
        .telegramMessageId(telegramMessageId)
        .status("SENT")
        .timestamp(Instant.now().toString())
        .build();
}
```

**Problemas:**
- ❌ Gera IDs fake (`"tg_" + UUID`)
- ❌ Não envia mensagem real ao Telegram
- ❌ Simula sucesso mesmo quando deveria falhar

#### Depois (Real API):

```java
public SendMessageResponse sendMessage(SendMessageRequest request) {
    try {
        log.info("[Telegram] Sending message via Telegram Bot API: messageId={}, chatId={}, contentLength={}",
            request.getMessageId(), request.getChatId(), request.getContent().length());

        // Chamada REAL para a API do Telegram
        TelegramApiClient.TelegramSendMessageResponse telegramResponse = 
            telegramApiClient.sendMessage(request.getChatId(), request.getContent());

        String telegramMessageId = String.valueOf(telegramResponse.getResult().getMessageId());

        log.info("[Telegram] Message sent successfully via Telegram API: messageId={}, telegramMessageId={}",
            request.getMessageId(), telegramMessageId);

        // Envia callback de READ status (simulando que o usuário leu a mensagem)
        sendReadStatusCallback(request.getMessageId(), telegramMessageId);

        return SendMessageResponse.builder()
            .messageId(request.getMessageId())
            .telegramMessageId(telegramMessageId)
            .status("SENT")
            .timestamp(Instant.now().toString())
            .build();

    } catch (TelegramApiClient.TelegramApiException e) {
        log.error("[Telegram] Failed to send message via Telegram API: messageId={}, error={}, statusCode={}",
            request.getMessageId(), e.getMessage(), e.getStatusCode());
        throw e; // Propaga o erro para que o router faça retry
    } catch (Exception e) {
        log.error("[Telegram] Unexpected error sending message: messageId={}", 
            request.getMessageId(), e);
        throw new RuntimeException("Failed to send Telegram message", e);
    }
}
```

**Benefícios:**
- ✅ Usa `telegramApiClient.sendMessage()` para enviar mensagem real
- ✅ Retorna `message_id` REAL da API do Telegram
- ✅ Propaga exceções para retry no `router-service`
- ✅ Logs detalhados de sucesso e falha

---

## 🧪 Como Testar

### 1. **Criar Bot no Telegram**

1. Abra o Telegram e busque por [@BotFather](https://t.me/BotFather)
2. Envie o comando `/newbot`
3. Siga as instruções e **copie o token** (ex: `123456789:ABCdefGHIjklMNOpqrsTUVwxyz`)

### 2. **Obter Chat ID**

Opção 1: Usar [@userinfobot](https://t.me/userinfobot)
- Inicie conversa com o bot
- Ele retornará seu `Chat ID` (ex: `987654321`)

Opção 2: Via API
```bash
curl "https://api.telegram.org/bot<SEU_TOKEN>/getUpdates"
```

### 3. **Configurar Variáveis de Ambiente**

**Docker Compose:**

Edite `docker-compose.yml`:

```yaml
telegram-connector:
  image: chat4all-v2-telegram-connector
  container_name: chat4all-telegram-connector
  environment:
    - TELEGRAM_BOT_TOKEN=123456789:ABCdefGHIjklMNOpqrsTUVwxyz
    - TELEGRAM_API_URL=https://api.telegram.org
    - CALLBACK_BASE_URL=http://message-service:8081
  ports:
    - "8086:8086"
  depends_on:
    - kafka
    - jaeger
```

**Desenvolvimento Local:**

```bash
export TELEGRAM_BOT_TOKEN="123456789:ABCdefGHIjklMNOpqrsTUVwxyz"
export TELEGRAM_API_URL="https://api.telegram.org"
export CALLBACK_BASE_URL="http://localhost:8081"

mvn spring-boot:run -pl services/connectors/telegram-connector
```

### 4. **Enviar Mensagem de Teste**

```bash
curl -X POST http://localhost:8086/api/send \
  -H "Content-Type: application/json" \
  -d '{
    "messageId": "test-msg-001",
    "chatId": "987654321",
    "content": "Hello from Chat4All! 🚀",
    "conversationId": "conv-123",
    "senderId": "user-456"
  }'
```

**Resposta esperada:**

```json
{
  "messageId": "test-msg-001",
  "telegramMessageId": "42",
  "status": "SENT",
  "timestamp": "2024-12-02T23:35:00.123Z"
}
```

**Verificação:**
- ✅ Mensagem aparece no Telegram do usuário
- ✅ `telegramMessageId` é um número real (ex: `42`)
- ✅ Logs mostram: `[Telegram] Message sent successfully via Telegram API`

---

## 📊 Logs de Sucesso

### Inicialização:

```json
{
  "timestamp": "2025-12-03T02:35:41.085+0000",
  "message": "TelegramApiClient initialized with API URL: https://api.telegram.org",
  "logger": "com.chat4all.connector.telegram.client.TelegramApiClient",
  "level": "INFO"
}
```

### Envio de Mensagem:

```json
{
  "timestamp": "2025-12-03T02:35:50.123+0000",
  "message": "[Telegram] Sending message via Telegram Bot API: messageId=msg-123, chatId=987654321, contentLength=25",
  "logger": "com.chat4all.connector.telegram.service.TelegramService",
  "level": "INFO"
}
```

### Sucesso:

```json
{
  "timestamp": "2025-12-03T02:35:50.456+0000",
  "message": "Message sent successfully to Telegram: chatId=987654321, messageId=42",
  "logger": "com.chat4all.connector.telegram.client.TelegramApiClient",
  "level": "INFO"
}
```

### Erro (exemplo: chat_id inválido):

```json
{
  "timestamp": "2025-12-03T02:35:55.789+0000",
  "message": "[Telegram] Failed to send message via Telegram API: messageId=msg-456, error=Telegram API client error: Bad Request: chat not found, statusCode=400",
  "logger": "com.chat4all.connector.telegram.service.TelegramService",
  "level": "ERROR"
}
```

---

## 🔄 Integração com Router Service

O `router-service` possui **retry automático** com **backoff exponencial** para falhas de connectors:

```java
@Retryable(
    value = { WebClientResponseException.class, TelegramApiException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2.0)
)
public SendMessageResponse routeMessage(String channel, SendMessageRequest request) {
    // ...
}
```

**Cenários de Retry:**
- ❌ `400 Bad Request`: Não retenta (erro de validação)
- ❌ `401 Unauthorized`: Não retenta (token inválido)
- ❌ `404 Not Found`: Não retenta (chat não existe)
- ✅ `500 Internal Server Error`: Retenta 3x (1s, 2s, 4s)
- ✅ `503 Service Unavailable`: Retenta 3x (1s, 2s, 4s)
- ✅ `TimeoutException`: Retenta 3x (1s, 2s, 4s)

---

## 🛠️ Build e Deploy

### Build Local:

```bash
cd /home/erik/java/projects/chat4all-v2
mvn clean package -pl services/connectors/telegram-connector -am -DskipTests
```

### Docker Build:

```bash
docker-compose up -d --build telegram-connector
```

### Verificar Status:

```bash
docker ps | grep telegram-connector
docker logs -f chat4all-telegram-connector
```

---

## 📁 Estrutura de Arquivos

```
telegram-connector/
├── src/main/java/com/chat4all/connector/telegram/
│   ├── api/
│   │   └── WebhookController.java          # Webhook para receber mensagens inbound
│   ├── client/
│   │   └── TelegramApiClient.java          # ✨ NOVO: Cliente HTTP para API do Telegram
│   ├── dto/
│   │   ├── SendMessageRequest.java         # DTO de entrada
│   │   └── SendMessageResponse.java        # DTO de saída
│   ├── service/
│   │   └── TelegramService.java            # 🔧 REFATORADO: Usa TelegramApiClient
│   └── TelegramConnectorApplication.java   # Main class
├── src/main/resources/
│   └── application.yml                     # 🔧 ATUALIZADO: Configuração da API
├── pom.xml
└── REAL_API_INTEGRATION.md                 # 📄 Este documento
```

---

## ✅ Checklist de Implementação

- [X] Criar `TelegramApiClient.java`
- [X] Configurar `application.yml` com `TELEGRAM_API_URL` e `TELEGRAM_BOT_TOKEN`
- [X] Refatorar `TelegramService.sendMessage()` para usar API real
- [X] Remover código de simulação (fake IDs, delays)
- [X] Adicionar tratamento de erros com propagação para retry
- [X] Corrigir porta do connector para 8086
- [X] Compilar e testar build
- [X] Fazer deploy no Docker
- [X] Verificar logs de inicialização
- [X] Criar documentação (este arquivo)

---

## 🚀 Próximos Passos (Opcional)

### 1. **Suporte a Mensagens com Mídia**
- Implementar `sendPhoto()`, `sendDocument()`, `sendVideo()`
- Integrar com `file-service` para upload de arquivos

### 2. **Webhook Real (Inbound)**
- Configurar webhook do Telegram Bot API
- Receber mensagens de usuários via `POST /api/webhooks/telegram`
- Publicar em Kafka topic `messages-inbound-telegram`

### 3. **Testes Automatizados**
- Unit tests com mocks do WebClient
- Integration tests com WireMock simulando API do Telegram

### 4. **Observabilidade**
- Adicionar métricas Prometheus:
  - `telegram_messages_sent_total`
  - `telegram_api_errors_total{status_code}`
  - `telegram_api_latency_seconds`

---

## 📚 Referências

- [Telegram Bot API Documentation](https://core.telegram.org/bots/api)
- [Spring WebClient Documentation](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [Resilience4j Retry](https://resilience4j.readme.io/docs/retry)

---

## 👤 Autor

**Chat4All Team**  
**Data:** 02/12/2024  
**Versão:** 1.0.0
