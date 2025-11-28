package com.chat4all.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * OAuth2 Resource Server Configuration for API Gateway
 * 
 * Configures Spring Security to validate JWT tokens and enforce scope-based authorization.
 * 
 * Scopes defined (FR-011):
 * - messages:read - Read messages and conversations
 * - messages:write - Send messages
 * - conversations:read - Read conversation metadata
 * - conversations:write - Create/update conversations
 * - users:read - Read user profiles
 * - users:write - Create/update users
 * - channels:read - Read channel configurations
 * - channels:write - Configure channels
 * - admin - Full administrative access
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
@EnableWebFluxSecurity
public class OAuth2Config {

    /**
     * Configures the security filter chain for API Gateway.
     * 
     * Security rules:
     * - Public endpoints: /actuator/health, /api/webhooks/**
     * - Message endpoints: require messages:read or messages:write
     * - Conversation endpoints: require conversations:read or conversations:write
     * - User endpoints: require users:read or users:write
     * - Channel endpoints: require channels:read or channels:write or admin
     * - All other endpoints: require authentication
     * 
     * @param http ServerHttpSecurity configuration
     * @return SecurityWebFilterChain configured security filter chain
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints - no authentication required
                .pathMatchers("/actuator/health").permitAll()
                .pathMatchers("/api/webhooks/**").permitAll()
                
                // OpenAPI/Swagger documentation endpoints - public access
                .pathMatchers("/v3/api-docs/**").permitAll()
                .pathMatchers("/swagger-ui.html").permitAll()
                .pathMatchers("/swagger-ui/**").permitAll()
                .pathMatchers("/webjars/**").permitAll()
                .pathMatchers("/api/messages/v3/api-docs/**").permitAll()
                .pathMatchers("/api/files/v3/api-docs/**").permitAll()
                
                // Message endpoints
                .pathMatchers("/api/messages/**").hasAnyAuthority("SCOPE_messages:read", "SCOPE_messages:write", "SCOPE_admin")
                .pathMatchers("/api/messages").hasAnyAuthority("SCOPE_messages:write", "SCOPE_admin")
                
                // Conversation endpoints
                .pathMatchers("/api/conversations/**").hasAnyAuthority("SCOPE_conversations:read", "SCOPE_conversations:write", "SCOPE_admin")
                .pathMatchers("/api/conversations").hasAnyAuthority("SCOPE_conversations:write", "SCOPE_admin")
                
                // User endpoints
                .pathMatchers("/api/users/**").hasAnyAuthority("SCOPE_users:read", "SCOPE_users:write", "SCOPE_admin")
                .pathMatchers("/api/users").hasAnyAuthority("SCOPE_users:write", "SCOPE_admin")
                
                // File endpoints
                .pathMatchers("/api/files/**").hasAnyAuthority("SCOPE_messages:write", "SCOPE_admin")
                
                // Channel configuration endpoints (admin only)
                .pathMatchers("/api/channels/**").hasAnyAuthority("SCOPE_channels:read", "SCOPE_channels:write", "SCOPE_admin")
                
                // All other endpoints require authentication
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .csrf(ServerHttpSecurity.CsrfSpec::disable); // Disable CSRF for stateless API

        return http.build();
    }

    /**
     * Converts JWT claims to Spring Security authorities.
     * 
     * Extracts scopes from both 'scope' (space-separated) and 'authorities' (array) claims,
     * prefixing them with 'SCOPE_' for Spring Security authorization.
     * 
     * @return Converter from JWT to AbstractAuthenticationToken
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new JwtGrantedAuthoritiesConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

    /**
     * Custom converter to extract authorities from JWT claims.
     * 
     * Supports two claim formats:
     * 1. 'scope' claim: space-separated string (e.g., "messages:read messages:write")
     * 2. 'authorities' claim: array of strings (e.g., ["messages:read", "messages:write"])
     * 
     * All scopes are prefixed with 'SCOPE_' to match Spring Security's convention.
     */
    private static class JwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            // Extract scopes from 'scope' claim (space-separated string)
            String scopeClaim = jwt.getClaimAsString("scope");
            List<String> scopes = scopeClaim != null 
                ? List.of(scopeClaim.split("\\s+"))
                : Collections.emptyList();

            // Extract authorities from 'authorities' claim (array)
            List<String> authorities = jwt.getClaimAsStringList("authorities");
            if (authorities == null) {
                authorities = Collections.emptyList();
            }

            // Combine scopes and authorities, prefix with 'SCOPE_'
            return Stream.concat(scopes.stream(), authorities.stream())
                .filter(scope -> !scope.isBlank())
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .collect(Collectors.toList());
        }
    }
}
