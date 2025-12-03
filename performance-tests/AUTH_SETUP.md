# Performance Testing Setup - Authentication

## Current Status

Os testes de performance encontram **401/403 errors** porque os endpoints de mensagens requerem autenticação OAuth2/JWT.

## Opções para Resolver

### Opção 1: Usar Apenas Endpoints Públicos ✅ (Implementado)

Os seguintes endpoints **não requerem autenticação**:
- `/actuator/health` - Health check
- `/actuator/prometheus` - Métricas
- `/v3/api-docs` - OpenAPI documentation  
- `/api/webhooks/**` - Webhooks (WhatsApp, Telegram, Instagram)

**Smoke test atualizado** para usar apenas endpoints públicos.

**Vantagens:**
- ✅ Funciona imediatamente
- ✅ Testa infraestrutura real (API Gateway, filtros, rate limiting)
- ✅ Sem modificação de código

**Limitações:**
- ❌ Não testa endpoints autenticados (POST `/api/messages`, GET `/api/conversations`)

---

### Opção 2: Habilitar Profile de Teste (Recomendado para Load Tests)

Para testar endpoints autenticados, adicione um **perfil Spring específico para testes**.

#### Passo 1: Criar Configuração de Segurança para Testes

Adicione em `services/api-gateway/src/main/java/com/chat4all/gateway/security/PerformanceTestSecurityConfig.java`:

```java
package com.chat4all.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security Configuration for Performance Testing
 * 
 * WARNING: This configuration disables all security!
 * NEVER use in production - only for load testing in isolated environments.
 * 
 * Enable with: --spring.profiles.active=perftest
 */
@Configuration
@EnableWebFluxSecurity
@Profile("perftest")  // Only active when perftest profile is enabled
public class PerformanceTestSecurityConfig {

    @Bean
    public SecurityWebFilterChain performanceTestSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()  // Allow all requests without authentication
            )
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .build();
    }
}
```

#### Passo 2: Configurar application-perftest.yml

Já criado em `services/api-gateway/src/main/resources/application-perftest.yml`:

```yaml
# Performance Testing Configuration
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OAUTH2_ISSUER_URI:}
          jwk-set-uri: ${OAUTH2_JWK_SET_URI:}

management:
  security:
    enabled: false
```

#### Passo 3: Iniciar API Gateway com Perfil de Teste

**Docker Compose:**
```yaml
# docker-compose.perftest.yml
version: '3.8'
services:
  api-gateway:
    environment:
      - SPRING_PROFILES_ACTIVE=perftest  # Enable performance test profile
      - OAUTH2_ISSUER_URI=
      - OAUTH2_JWK_SET_URI=
```

Ou via variável de ambiente:
```bash
docker-compose stop chat4all-api-gateway
docker run -d --name chat4all-api-gateway \
  --network chat4all-v2_default \
  -e SPRING_PROFILES_ACTIVE=perftest \
  -p 8080:8080 \
  chat4all-api-gateway:latest
```

#### Passo 4: Executar Testes

```bash
# Agora todos os endpoints funcionam sem autenticação
k6 run scenarios/concurrent-conversations.js -e VUS=10000

# Teste de spike
k6 run scenarios/spike-test.js
```

---

### Opção 3: Gerar Tokens JWT Válidos (Mais Realista)

Para simular autenticação real, podemos gerar tokens JWT válidos com Keycloak.

#### Passo 3.1: Subir Keycloak

```bash
docker run -d --name keycloak \
  --network chat4all-v2_default \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -p 8090:8080 \
  quay.io/keycloak/keycloak:latest start-dev
```

#### Passo 3.2: Configurar Realm e Client

1. Acesse http://localhost:8090
2. Login: admin/admin
3. Crie realm "chat4all"
4. Crie client "chat4all-api" com client credentials
5. Adicione scopes: `messages:read`, `messages:write`, etc.

#### Passo 3.3: Obter Token

```bash
# Get access token
TOKEN=$(curl -X POST "http://localhost:8090/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=chat4all-api" \
  -d "client_secret=YOUR_SECRET" \
  -d "grant_type=client_credentials" \
  -d "scope=messages:write messages:read" \
  | jq -r '.access_token')

echo $TOKEN
```

#### Passo 3.4: Usar Token no K6

Modificar `concurrent-conversations.js`:

```javascript
// Load token from environment or file
const TOKEN = __ENV.ACCESS_TOKEN || '';

function sendOutboundMessage(conversationId) {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${TOKEN}`,  // Add JWT token
    },
    tags: { endpoint: 'send_message' },
  };
  
  const res = http.post(`${BASE_URL}/api/messages/v1/outbound`, payload, params);
  // ...
}
```

Executar:
```bash
# Get token and run test
ACCESS_TOKEN=$(curl -X POST ...) k6 run scenarios/concurrent-conversations.js
```

---

## Recomendação para Este Projeto

Para **testes rápidos de carga e validação de infraestrutura**:

✅ **Use Opção 2 (Perfil de Teste)**
- Simples de implementar
- Testa endpoints reais
- Isola riscos de segurança (profile separado)
- Permite load tests completos

Para **produção ou staging**:

✅ **Use Opção 3 (Tokens JWT Reais)**
- Valida autenticação completa
- Testa políticas de acesso
- Realista para performance real

---

## Status Atual

✅ Smoke test atualizado para usar apenas endpoints públicos  
⏳ Profile de teste (perftest) criado, aguardando implementação de SecurityConfig  
⏳ Testes de carga (concurrent-conversations, spike-test) ainda retornam 401/403  

## Próximos Passos

1. **Implementar `PerformanceTestSecurityConfig.java`** (5 min)
2. **Testar com profile perftest** (5 min)
3. **Validar load test de 10K usuários** (10 min)
4. **Documentar resultados** (10 min)

Total: ~30 minutos para ter performance tests funcionais completos.
