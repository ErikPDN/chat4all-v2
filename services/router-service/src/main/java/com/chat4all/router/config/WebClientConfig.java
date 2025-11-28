package com.chat4all.router.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient Configuration for Router Service.
 * 
 * Provides WebClient builder for making HTTP calls to connector services.
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
public class WebClientConfig {

    /**
     * Creates WebClient builder bean.
     * 
     * @return Configured WebClient.Builder
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * Creates WebClient instance for connector communication.
     * 
     * @param builder WebClient builder
     * @return Configured WebClient
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
