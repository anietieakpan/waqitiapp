package com.waqiti.common.security.keycloak;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service-to-Service authentication configuration for Keycloak
 * Provides automatic token management for inter-service communication
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class ServiceToServiceAuthConfig {
    
    @Value("${keycloak.auth-server-url:http://localhost:8180}")
    private String authServerUrl;

    @Value("${keycloak.realm:waqiti-fintech}")
    private String realm;

    @Value("${spring.application.name}")
    private String clientId;

    @Value("${keycloak.credentials.secret:${vault.keycloak.${spring.application.name}.client-secret:}}")
    private String clientSecret;

    // Token cache to avoid unnecessary token requests
    private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    /**
     * Keycloak admin client for token management
     */
    @Bean
    @ConditionalOnProperty(name = "keycloak.service-account.enabled", havingValue = "true", matchIfMissing = true)
    public Keycloak keycloakClient() {
        return KeycloakBuilder.builder()
            .serverUrl(authServerUrl)
            .realm(realm)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .grantType("client_credentials")
            .build();
    }

    /**
     * RestTemplate with automatic service-to-service authentication
     */
    @Bean(name = "serviceRestTemplate")
    public RestTemplate serviceRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new ServiceAuthInterceptor());
        return restTemplate;
    }

    /**
     * WebClient with automatic service-to-service authentication
     */
    @Bean(name = "serviceWebClient")
    public WebClient serviceWebClient() {
        return WebClient.builder()
            .filter(serviceAuthExchangeFilter())
            .build();
    }

    /**
     * Exchange filter for WebClient to add service authentication
     */
    private ExchangeFilterFunction serviceAuthExchangeFilter() {
        return (request, next) -> {
            String token = getServiceToken();
            ClientRequest filtered = ClientRequest.from(request)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
            return next.exchange(filtered);
        };
    }

    /**
     * Interceptor for RestTemplate to add service authentication
     */
    private class ServiceAuthInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                          ClientHttpRequestExecution execution) throws IOException {
            String token = getServiceToken();
            request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return execution.execute(request, body);
        }
    }

    /**
     * Get or refresh service account token
     */
    private String getServiceToken() {
        CachedToken cached = tokenCache.get(clientId);
        
        // Check if we have a valid cached token
        if (cached != null && !cached.isExpired()) {
            return cached.getToken();
        }
        
        // Get new token
        try {
            log.debug("Obtaining new service token for client: {}", clientId);
            Keycloak keycloak = keycloakClient();
            String token = keycloak.tokenManager().getAccessTokenString();
            
            // Cache the token (expires in 5 minutes by default)
            tokenCache.put(clientId, new CachedToken(token, Instant.now().plusSeconds(280)));
            
            return token;
        } catch (Exception e) {
            log.error("Failed to obtain service token for client: {}", clientId, e);
            throw new ServiceAuthenticationException("Failed to obtain service token", e);
        }
    }

    /**
     * Service account configuration properties
     */
    @Bean
    @ConditionalOnProperty(name = "keycloak.service-account.enabled", havingValue = "true", matchIfMissing = true)
    public ServiceAccountConfig serviceAccountConfig() {
        return ServiceAccountConfig.builder()
            .clientId(clientId)
            .realm(realm)
            .authServerUrl(authServerUrl)
            .grantType("client_credentials")
            .scope("openid profile")
            .build();
    }

    /**
     * Cached token with expiration
     */
    private static class CachedToken {
        private final String token;
        private final Instant expiresAt;

        public CachedToken(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Custom exception for service authentication failures
     */
    public static class ServiceAuthenticationException extends RuntimeException {
        public ServiceAuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Service account configuration
     */
    @Builder
    @Data
    public static class ServiceAccountConfig {
        private String clientId;
        private String realm;
        private String authServerUrl;
        private String grantType;
        private String scope;
    }

    /**
     * Health indicator for service authentication
     */
    @Bean
    public ServiceAuthHealthIndicator serviceAuthHealthIndicator() {
        return new ServiceAuthHealthIndicator(this::getServiceToken);
    }

    /**
     * Health indicator implementation
     */
    public static class ServiceAuthHealthIndicator implements org.springframework.boot.actuate.health.HealthIndicator {
        private final java.util.function.Supplier<String> tokenSupplier;

        public ServiceAuthHealthIndicator(java.util.function.Supplier<String> tokenSupplier) {
            this.tokenSupplier = tokenSupplier;
        }

        @Override
        public org.springframework.boot.actuate.health.Health health() {
            try {
                tokenSupplier.get();
                return org.springframework.boot.actuate.health.Health.up()
                    .withDetail("service-auth", "operational")
                    .build();
            } catch (Exception e) {
                return org.springframework.boot.actuate.health.Health.down()
                    .withDetail("service-auth", "failed")
                    .withException(e)
                    .build();
            }
        }
    }
}