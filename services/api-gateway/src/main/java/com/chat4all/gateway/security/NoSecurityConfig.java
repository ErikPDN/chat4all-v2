package com.chat4all.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Disabled Security Configuration for Performance Testing
 * 
 * ⚠️ WARNING: This configuration disables ALL security controls.
 * Should ONLY be used in local development for performance testing.
 * 
 * To activate: Start gateway with profile 'no-security'
 *   export SPRING_PROFILES_ACTIVE=no-security
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
@EnableWebFluxSecurity
@Profile("no-security")
public class NoSecurityConfig {

    /**
     * Configures security to permit all requests without authentication.
     * 
     * ⚠️ SECURITY DISABLED - FOR TESTING ONLY
     * 
     * @param http ServerHttpSecurity configuration
     * @return SecurityWebFilterChain with all endpoints permitted
     */
    @Bean
    public SecurityWebFilterChain noSecurityFilterChain(ServerHttpSecurity http) {
        http
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()  // Allow ALL requests without authentication
            )
            .csrf(ServerHttpSecurity.CsrfSpec::disable)  // Disable CSRF
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)  // Disable basic auth
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable);  // Disable form login

        return http.build();
    }
}
