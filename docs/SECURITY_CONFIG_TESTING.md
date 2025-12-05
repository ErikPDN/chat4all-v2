# Performance Testing - Security Configuration

## ⚠️ IMPORTANT: Security AND Rate Limiting Temporarily Disabled

Para permitir testes de performance sem a complexidade de OAuth2 e sem limitações de rate limit, **duas proteções foram temporariamente desabilitadas** no API Gateway:

1. **OAuth2 Authentication** - Permite requisições sem tokens JWT
2. **Rate Limiting** - Remove limites de 100 req/min por usuário e 1000 req/min global

## Configuração Atual (Testing)

- **Profile ativo**: `dev,no-security`
- **Arquivo**: `docker-compose.yml` → `api-gateway.environment.SPRING_PROFILES_ACTIVE`
- **Classe de segurança**: `NoSecurityConfig.java` (permite TODAS as requisições)
- **Rate limiting**: `RateLimitFilter.java` desabilita automaticamente quando profile `no-security` está ativo

## Limites Removidos Temporariamente

### Rate Limiting (RateLimitFilter)
- **User rate limit**: 100 req/min por usuário → **DESABILITADO**
- **Global rate limit**: 1000 req/min → **DESABILITADO**
- **Burst capacity**: 200 req → **DESABILITADO**

### OAuth2 Authentication
- **JWT validation**: Requerido → **DESABILITADO**
- **Scope enforcement**: Requerido → **DESABILITADO**
- **Public endpoints**: Limitados → **TODOS públicos**

## Como Restaurar a Segurança OAuth2

### Passo 1: Remover o profile `no-security`

Edite `docker-compose.yml`:

```yaml
api-gateway:
  environment:
    SPRING_PROFILES_ACTIVE: dev  # Remover ",no-security"
```

### Passo 2: Rebuild do API Gateway

```bash
docker-compose up -d --build api-gateway
```

### Passo 3: Verificar que OAuth2 está ativo

```bash
# Deve retornar 401 Unauthorized
curl -X POST http://localhost:8080/api/messages \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"test","senderId":"user","content":"test","channel":"WHATSAPP"}'
```

**Resposta esperada** (segurança ativa):
```
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer
```

## Configuração OAuth2

### Scopes Definidos (FR-011)

- `messages:read` - Ler mensagens e conversações
- `messages:write` - Enviar mensagens
- `conversations:read` - Ler metadados de conversações
- `conversations:write` - Criar/atualizar conversações
- `users:read` - Ler perfis de usuários
- `users:write` - Criar/atualizar usuários
- `channels:read` - Ler configurações de canais
- `channels:write` - Configurar canais
- `admin` - Acesso administrativo completo

### Endpoints Públicos (sempre acessíveis)

- `/actuator/**` - Métricas Prometheus e health checks
- `/api/webhooks/**` - Webhooks de conectores (WhatsApp, Telegram, Instagram)
- `/v3/api-docs/**` - Documentação OpenAPI
- `/swagger-ui/**` - Interface Swagger UI

### Endpoints Protegidos

| Endpoint | Scopes Requeridos |
|----------|-------------------|
| `POST /api/messages` | `messages:write`, `admin` |
| `GET /api/messages/**` | `messages:read`, `messages:write`, `admin` |
| `GET /api/conversations/**` | `conversations:read`, `conversations:write`, `admin` |
| `POST /api/conversations` | `conversations:write`, `admin` |
| `GET /api/users/**` | `users:read`, `users:write`, `admin` |
| `POST /api/users` | `users:write`, `admin` |
| `POST /api/files/**` | `messages:write`, `admin` |
| `/api/channels/**` | `channels:read`, `channels:write`, `admin` |

## Configuração do OAuth2 Provider

Edite `services/api-gateway/src/main/resources/application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OAUTH2_ISSUER_URI:http://localhost:8090/realms/chat4all}
          jwk-set-uri: ${OAUTH2_JWK_SET_URI:http://localhost:8090/realms/chat4all/protocol/openid-connect/certs}
```

**Variáveis de ambiente** (produção):
- `OAUTH2_ISSUER_URI` - URI do OAuth2 provider (ex: Keycloak, Auth0)
- `OAUTH2_JWK_SET_URI` - URI do JWK Set para validação de tokens

## Testando com Token OAuth2

### 1. Obter token de acesso

```bash
curl -X POST http://localhost:8090/realms/chat4all/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=chat4all-api" \
  -d "username=test-user" \
  -d "password=test-password" \
  -d "scope=messages:read messages:write"
```

### 2. Usar token nas requisições

```bash
TOKEN="eyJhbGciOiJSUzI1NiIs..."

curl -X POST http://localhost:8080/api/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"test","senderId":"user","content":"test","channel":"WHATSAPP"}'
```

## Arquivos de Configuração

| Arquivo | Descrição |
|---------|-----------|
| `OAuth2Config.java` | Configuração principal OAuth2 (ativa sem `no-security` profile) |
| `NoSecurityConfig.java` | Desabilita segurança (ativa COM `no-security` profile) |
| `application.yml` | Configuração do resource server JWT |

## Checklist de Restauração

- [ ] Remover `no-security` do `SPRING_PROFILES_ACTIVE` em `docker-compose.yml`
- [ ] Rebuild do container `api-gateway`
- [ ] Verificar que endpoints retornam `401 Unauthorized` sem token
- [ ] Verificar que rate limiting retorna `429 Too Many Requests` após 100 req/min
- [ ] Configurar OAuth2 provider (Keycloak, Auth0, etc.)
- [ ] Atualizar `OAUTH2_ISSUER_URI` e `OAUTH2_JWK_SET_URI`
- [ ] Testar autenticação com token válido
- [ ] Validar scopes funcionando corretamente
- [ ] Atualizar documentação da API com instruções de autenticação

## Notas de Segurança

⚠️ **NUNCA** use o profile `no-security` em produção!

✅ **Usar em**:
- Testes de performance locais
- Desenvolvimento local (opcional)
- Testes de carga

❌ **NUNCA usar em**:
- Produção
- Staging
- Ambientes públicos
- Qualquer ambiente com dados reais

## Performance com OAuth2

A validação de tokens JWT adiciona overhead (~5-20ms por requisição), dependendo de:
- Tamanho do token
- Número de scopes
- Latência da rede para buscar JWK Set (cache automático)
- Configuração de cache do Spring Security

**Recomendação**: Após testes de performance sem segurança, execute novamente COM OAuth2 para medir o impacto real.
