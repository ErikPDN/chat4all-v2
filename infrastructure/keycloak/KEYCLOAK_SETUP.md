# Keycloak Integration Guide

## Overview

Keycloak é um servidor de gerenciamento de identidade e acesso de código aberto integrado ao Chat4All v2. Ele fornece autenticação OAuth2/OIDC, gerenciamento de usuários, e controle de acesso baseado em papéis (RBAC).

## Arquitetura

```
┌──────────────┐
│   Frontend   │
└──────┬───────┘
       │
       ├──────────────────────────┐
       │                          │
       ▼                          ▼
┌──────────────┐          ┌──────────────┐
│ Keycloak     │◄────────►│ PostgreSQL   │
│ (Auth Server)│          │ (User DB)    │
└──────┬───────┘          └──────────────┘
       │
       │ JWT/OIDC
       │
       ▼
┌──────────────┐
│ API Gateway  │
│ (Validates   │
│  JWT)        │
└──────┬───────┘
       │
   ┌───┴─────────────┐
   ▼                 ▼
Services        Microservices
```

## Início Rápido

### 1. Inicie o Keycloak com Docker Compose

```bash
cd /home/erik/java/projects/chat4all-v2

# Inicie apenas o Keycloak (e dependências)
docker-compose up -d postgres keycloak

# Aguarde initialization (30-60 segundos)
sleep 60

# Verifique status
docker-compose ps | grep keycloak
```

### 2. Acesse o Console de Administração

- **URL**: http://localhost:8888
- **Username**: `admin`
- **Password**: `admin`

### 3. Verifique o Realm Pré-configurado

1. Clique em dropdown no canto superior esquerdo (próximo a "Keycloak")
2. Você verá o realm `chat4all` já criado
3. Selecione-o para ver clientes, usuários e papéis

## Usuários Padrão

O arquivo `chat4all-realm.json` cria automaticamente estes usuários:

| Username | Password | Email | Papéis |
|----------|----------|-------|--------|
| admin | admin123 | admin@chat4all.local | admin, user |
| testuser | test123 | testuser@chat4all.local | user |
| moderator | mod123 | moderator@chat4all.local | user, moderator |
| support-agent | support123 | support@chat4all.local | user, support |

## Clientes OAuth2 Configurados

### 1. `chat4all-backend`
- **Tipo**: Service account (máquina-para-máquina)
- **Secret**: `KevwrFRawxHqjOjgLZHXRPvOWVFvDQXW`
- **Flow**: Direct Access Grant (client credentials)
- **Uso**: Autenticação de serviços internos

### 2. `chat4all-frontend`
- **Tipo**: Public client (SPA)
- **Flows**: Authorization Code, Implicit
- **URL**: http://localhost:3000
- **Uso**: Aplicação web frontend

### 3. `api-gateway`
- **Tipo**: Confidential client
- **Secret**: `QmVGVXJyOFd4akc5ekJzTDJHSFRVMzBk`
- **Flow**: Authorization Code
- **Uso**: Spring Cloud Gateway OAuth2 proxy

## Papéis (Roles)

### Papéis de Realm
- `admin`: Acesso total ao sistema
- `user`: Usuário padrão
- `moderator`: Moderação de conteúdo
- `support`: Suporte técnico

### Papéis de Cliente (`chat4all-backend`)
- `message-read`: Ler mensagens
- `message-write`: Enviar mensagens
- `user-manage`: Gerenciar usuários
- `file-upload`: Upload de arquivos
- `conversation-manage`: Gerenciar conversas

## Fluxos de Autenticação

### OAuth2 Authorization Code Flow (Frontend)

```bash
# 1. Redirecionar usuário para login
curl -X GET "http://localhost:8888/realms/chat4all/protocol/openid-connect/auth?
  client_id=chat4all-frontend&
  redirect_uri=http://localhost:3000/callback&
  response_type=code&
  scope=openid+profile+email&
  state=random_state_string"

# 2. User login na interface Keycloak
# 3. Authorization server retorna código na callback URL
# 4. Frontend troca código por token (backend)

# 5. Frontend usa token para acessar APIs
curl -X POST "http://localhost:8888/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&
      client_id=chat4all-frontend&
      code=AUTH_CODE_FROM_STEP_3&
      redirect_uri=http://localhost:3000/callback"
```

### Client Credentials Flow (Backend Service-to-Service)

```bash
# Para autenticar um serviço sem usuário final
curl -X POST "http://localhost:8888/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "chat4all-backend:KevwrFRawxHqjOjgLZHXRPvOWVFvDQXW" \
  -d "grant_type=client_credentials&
      scope=openid"
```

### Resource Owner Password Grant (Testing)

```bash
# ⚠️ Apenas para testes - NÃO use em produção
curl -X POST "http://localhost:8888/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&
      client_id=chat4all-frontend&
      username=testuser&
      password=test123&
      scope=openid+profile+email"
```

## Extrair Token JWT

```bash
# Login e obter token
TOKEN=$(curl -s -X POST "http://localhost:8888/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&
      client_id=chat4all-frontend&
      username=testuser&
      password=test123" | jq -r '.access_token')

echo $TOKEN

# Decodificar token (visualizar claims)
echo $TOKEN | cut -d'.' -f2 | base64 -d | jq .
```

## Validar Token via API

```bash
# Verifique se um token é válido
curl -X POST "http://localhost:8888/realms/chat4all/protocol/openid-connect/token/introspect" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "chat4all-backend:KevwrFRawxHqjOjgLZHXRPvOWVFvDQXW" \
  -d "token=$TOKEN"

# Resposta:
# {
#   "active": true,
#   "scope": "profile email",
#   "client_id": "chat4all-frontend",
#   "username": "testuser",
#   "type": "Bearer",
#   "exp": 1733925600,
#   "iat": 1733925300,
#   "sub": "abc123def456"
# }
```

## Integração com Spring Boot

### 1. Adicione dependências (pom.xml)

```xml
<!-- Spring Security OAuth2 Resource Server -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    <version>3.2.0</version>
</dependency>

<!-- Spring Security OAuth2 Client (para API Gateway) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
    <version>3.2.0</version>
</dependency>

<!-- JWT processing -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

### 2. Configure aplicação.properties

```properties
# Keycloak Configuration
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8888/realms/chat4all
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8888/realms/chat4all/protocol/openid-connect/certs

# Para API Gateway (OAuth2 Client)
spring.security.oauth2.client.registration.keycloak.client-id=api-gateway
spring.security.oauth2.client.registration.keycloak.client-secret=QmVGVXJyOFd4akc5ekJzTDJHSFRVMzBk
spring.security.oauth2.client.registration.keycloak.scope=openid,profile,email
spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.keycloak.redirect-uri=http://localhost:8080/login/oauth2/code/keycloak

spring.security.oauth2.client.provider.keycloak.issuer-uri=http://localhost:8888/realms/chat4all
spring.security.oauth2.client.provider.keycloak.authorization-uri=http://localhost:8888/realms/chat4all/protocol/openid-connect/auth
spring.security.oauth2.client.provider.keycloak.token-uri=http://localhost:8888/realms/chat4all/protocol/openid-connect/token
spring.security.oauth2.client.provider.keycloak.user-info-uri=http://localhost:8888/realms/chat4all/protocol/openid-connect/userinfo
spring.security.oauth2.client.provider.keycloak.jwk-set-uri=http://localhost:8888/realms/chat4all/protocol/openid-connect/certs
spring.security.oauth2.client.provider.keycloak.user-name-attribute=preferred_username
```

### 3. Configurar Security (exemplo para Resource Server)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((authz) -> authz
                .requestMatchers("/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/messages/**").hasAuthority("SCOPE_message-read")
                .requestMatchers("/api/users/**").hasAuthority("SCOPE_user-manage")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer((oauth2) -> oauth2
                .jwt((jwt) -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
        return converter;
    }

    private JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("realm_access");
        converter.setAuthorityPrefix("ROLE_");
        return converter;
    }
}
```

## Gerenciamento de Usuários

### Criar novo usuário (via Admin Console)

1. Vá para `Users` no menu lateral
2. Clique em `Create new user`
3. Preencha username, email, first/last name
4. Clique em `Create`
5. Vá para aba `Credentials`
6. Defina senha temporária ou permanente
7. Atribua papéis (Realm Roles)

### Criar via API

```bash
# Gerar token de admin
ADMIN_TOKEN=$(curl -s -X POST "http://localhost:8888/realms/chat4all/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "chat4all-backend:KevwrFRawxHqjOjgLZHXRPvOWVFvDQXW" \
  -d "grant_type=client_credentials" | jq -r '.access_token')

# Criar usuário
curl -X POST "http://localhost:8888/admin/realms/chat4all/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "email": "newuser@chat4all.local",
    "firstName": "New",
    "lastName": "User",
    "enabled": true,
    "emailVerified": true,
    "credentials": [{
      "type": "password",
      "value": "newuser123",
      "temporary": false
    }],
    "realmRoles": ["user"]
  }'
```

## Troubleshooting

### Keycloak não inicia

```bash
# Verificar logs
docker logs chat4all-keycloak

# Comum: PostgreSQL não está pronto
# Solução: aguarde postgres inicializar (5-10 segundos)
docker-compose up -d postgres
sleep 10
docker-compose up -d keycloak
```

### Erro "Invalid redirect_uri"

- Verifique as `Valid Redirect URIs` do cliente no console Keycloak
- Adicione suas URLs esperadas (development, staging, production)
- Ou use `http://localhost:*` para wildcard em dev

### Token expirado

Padrão: 1 hora de expiração (configurável no arquivo realm JSON)
```json
"accessTokenLifespan": 3600  // segundos
```

### CORS issues

Se o frontend tiver problemas CORS:

```bash
# No console Keycloak:
# 1. Vá para Realm Settings > Security
# 2. Adicione sua domain em "Web Origins"
# Por exemplo: "http://localhost:3000"
```

## Produção

Para ambiente de produção:

1. **Use HTTPS**: Mude `KC_HOSTNAME_PROTOCOL` para `https`
2. **Mudar senhas padrão**: Admin password, client secrets
3. **Banco de dados**: Use PostgreSQL externo (não container)
4. **Replicação**: Configure múltiplas instâncias Keycloak
5. **Backup**: Configure backup regular do banco PostgreSQL
6. **Certificados**: Configure TLS/SSL certificates
7. **Rate limiting**: Configure rate limits no proxy reverso
8. **Auditoria**: Ative event logging do Keycloak

### Exemplo docker-compose para produção

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:latest
  environment:
    KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN}
    KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
    KC_DB: postgres
    KC_DB_URL: ${KC_DB_URL}
    KC_DB_USERNAME: ${KC_DB_USERNAME}
    KC_DB_PASSWORD: ${KC_DB_PASSWORD}
    KC_HOSTNAME: ${KC_HOSTNAME}
    KC_HOSTNAME_PROTOCOL: https
    KC_PROXY: reencrypt
    KC_LOG_LEVEL: WARN
  command: start
  healthcheck:
    test: ["CMD", "curl", "-f", "https://localhost:8443/health"]
    interval: 15s
    timeout: 5s
    retries: 5
```

## Referências

- [Keycloak Official Docs](https://www.keycloak.org/documentation.html)
- [OpenID Connect Standard](https://openid.net/connect/)
- [OAuth 2.0 Security Best Practices](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics)
- [Spring Security OAuth2 Docs](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)

## Próximos Passos

1. **Integrar com API Gateway**: Configurar Spring Cloud Gateway para OAuth2
2. **Integrar com Microserviços**: Adicionar validação JWT em cada serviço
3. **Implementar MFA**: Two-factor authentication (TOTP/OTP)
4. **Social Login**: Integrar login com Google, GitHub, etc.
5. **Customização**: Temas e páginas de login personalizadas
6. **Auditoria**: Logs de acesso e eventos de segurança
