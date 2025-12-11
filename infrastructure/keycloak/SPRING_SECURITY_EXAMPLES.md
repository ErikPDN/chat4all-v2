# Exemplo de Configuração Spring Security para Keycloak

## API Gateway (Spring Cloud Gateway)

### pom.xml
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
    <version>3.2.0</version>
</dependency>

<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-config</artifactId>
    <version>6.2.0</version>
</dependency>

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

### application.yml
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: api-gateway
            client-secret: QmVGVXJyOFd4akc5ekJzTDJHSFRVMzBk
            scope: openid,profile,email,roles
            authorization-grant-type: authorization_code
            redirect-uri: http://localhost:8080/login/oauth2/code/keycloak
        provider:
          keycloak:
            issuer-uri: http://keycloak:8080/realms/chat4all
            authorization-uri: http://keycloak:8080/realms/chat4all/protocol/openid-connect/auth
            token-uri: http://keycloak:8080/realms/chat4all/protocol/openid-connect/token
            user-info-uri: http://keycloak:8080/realms/chat4all/protocol/openid-connect/userinfo
            jwk-set-uri: http://keycloak:8080/realms/chat4all/protocol/openid-connect/certs
            user-name-attribute: preferred_username
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://user-service:8083
          predicates:
            - Path=/api/users/**
          filters:
            - TokenRelay=
        - id: message-service
          uri: http://message-service:8081
          predicates:
            - Path=/api/messages/**,/api/conversations/**
          filters:
            - TokenRelay=
        - id: file-service
          uri: http://file-service:8084
          predicates:
            - Path=/api/files/**
          filters:
            - TokenRelay=
```

### SecurityConfig.java
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((authz) -> authz
                .requestMatchers("/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/login", "/logout", "/").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login((oauth2) -> oauth2
                .loginPage("/oauth2/authorization/keycloak")
                .defaultSuccessUrl("/", true)
            )
            .oauth2Client(Customizer.withDefaults())
            .logout((logout) -> logout
                .logoutUrl("/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );
        return http.build();
    }
}
```

---

## Microserviços como Resource Server

### Exemplo: Message Service

#### pom.xml
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    <version>3.2.0</version>
</dependency>

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

#### application.yml
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/chat4all
          jwk-set-uri: http://keycloak:8080/realms/chat4all/protocol/openid-connect/certs
```

#### SecurityConfig.java
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((authz) -> authz
                .requestMatchers("/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/messages/**").hasAuthority("SCOPE_message-read")
                .requestMatchers(HttpMethod.POST, "/api/messages/**").hasAuthority("SCOPE_message-write")
                .requestMatchers("/api/conversations/**").authenticated()
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

#### Controller Example
```java
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_message-write')")
    public ResponseEntity<MessageResponse> createMessage(
            @RequestBody CreateMessageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getClaimAsString("sub");
        String username = jwt.getClaimAsString("preferred_username");
        
        MessageResponse response = messageService.createMessage(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{messageId}")
    @PreAuthorize("hasAuthority('SCOPE_message-read')")
    public ResponseEntity<MessageResponse> getMessageById(
            @PathVariable String messageId,
            @AuthenticationPrincipal Jwt jwt) {
        
        MessageResponse response = messageService.getMessageById(messageId);
        return ResponseEntity.ok(response);
    }
}
```

---

## User Service com Repository Role-Based Access

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    public ResponseEntity<List<UserDto>> listAllUsers(
            @AuthenticationPrincipal Jwt jwt) {
        
        List<UserDto> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> getCurrentUserProfile(
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getClaimAsString("sub");
        UserDto user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> updateProfile(
            @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getClaimAsString("sub");
        UserDto user = userService.updateUser(userId, request);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {
        
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
```

---

## File Service com Roles

```java
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final AuditService auditService;

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('SCOPE_file-upload')")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getClaimAsString("sub");
        String filename = file.getOriginalFilename();
        
        FileUploadResponse response = fileService.uploadFile(file, userId);
        
        auditService.logFileUpload(userId, filename, response.getFileId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{fileId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String fileId,
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getClaimAsString("sub");
        Resource resource = fileService.getFile(fileId, userId);
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasRole('ADMIN') or @fileService.isOwner(#fileId, #jwt.getClaimAsString('sub'))")
    public ResponseEntity<Void> deleteFile(
            @PathVariable String fileId,
            @AuthenticationPrincipal Jwt jwt) {
        
        fileService.deleteFile(fileId);
        return ResponseEntity.noContent().build();
    }
}
```

---

## Testing com Keycloak Tokens

```java
@SpringBootTest
@AutoConfigureMockMvc
public class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String validToken;

    @BeforeEach
    public void setup() {
        validToken = generateValidJwt();
    }

    private String generateValidJwt() {
        // Usar biblioteca jwter ou mockar o token
        // Exemplo simplificado
        return "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...";
    }

    @Test
    public void testCreateMessage_WithValidToken() throws Exception {
        mockMvc.perform(post("/api/messages")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "conversationId": "conv-001",
                        "content": "Test message"
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").exists());
    }

    @Test
    public void testCreateMessage_WithoutToken() throws Exception {
        mockMvc.perform(post("/api/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "conversationId": "conv-001",
                        "content": "Test message"
                    }
                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testGetAllUsers_WithoutAdminRole() throws Exception {
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden());
    }
}
```

---

## Variáveis de Ambiente para Produção

```bash
# .env for production
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=<strong-password>
KC_DB_URL=jdbc:postgresql://postgres.prod:5432/keycloak
KC_DB_USERNAME=keycloak
KC_DB_PASSWORD=<db-password>
KC_HOSTNAME=auth.yourdomain.com
KC_HOSTNAME_PROTOCOL=https
KC_HTTP_ENABLED=false

OAUTH2_CLIENT_SECRET_BACKEND=<random-secret>
OAUTH2_CLIENT_SECRET_GATEWAY=<random-secret>
OAUTH2_CLIENT_SECRET_FRONTEND=<no-secret-for-public-client>
```

---

## Troubleshooting

### Token não é reconhecido
- Verifique se `issuer-uri` está correto no application.yml
- Verifique se Keycloak está acessível do container

### CORS issues
- Certifique-se de configurar `Web Origins` no cliente Keycloak
- Configure CORS em cada microserviço

### Performance
- Cache JWK set localmente
- Use connection pooling para banco de dados
- Implemente token caching em Redis
