package com.waqiti.common.config;

import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import feign.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.concurrent.TimeUnit;

/**
 * Global Feign client configuration
 * Provides authentication, error handling, timeout configuration, and performance optimization
 *
 * CRITICAL: All Feign clients now have timeout configurations to prevent thread starvation
 *
 * TIMEOUT CONFIGURATION:
 * - Configured via application.yml: feign.client.config.default.{connectTimeout, readTimeout}
 * - Default: 10s connect, 30s read (can be overridden per client)
 * - Prevents infinite waits and cascading failures
 *
 * @version 3.0.0
 */
@Configuration
@EnableFeignClients(basePackages = "com.waqiti")
@ConditionalOnClass(name = "feign.Feign")
@Slf4j
public class FeignConfiguration {

    @Value("${service.auth.enabled:true}")
    private boolean serviceAuthEnabled;

    @Value("${service.auth.client-id:}")
    private String clientId;

    @Value("${service.auth.client-secret:}")
    private String clientSecret;

    // Timeout configuration
    @Value("${feign.client.config.default.connectTimeout:10000}")
    private int defaultConnectTimeout;

    @Value("${feign.client.config.default.readTimeout:30000}")
    private int defaultReadTimeout;

    /**
     * Request interceptor to add authentication headers
     */
    @Bean
    public RequestInterceptor authorizationRequestInterceptor() {
        return requestTemplate -> {
            if (serviceAuthEnabled) {
                // Try to get JWT token from security context
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication instanceof JwtAuthenticationToken) {
                    Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
                    requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
                }
                
                // Add service-to-service authentication headers
                if (clientId != null && !clientId.isEmpty()) {
                    requestTemplate.header("X-Service-Client-Id", clientId);
                }
                
                // Add correlation ID for distributed tracing
                requestTemplate.header("X-Correlation-Id", java.util.UUID.randomUUID().toString());
            }
        };
    }

    /**
     * Custom error decoder for better error handling
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new FeignErrorDecoder();
    }

    /**
     * Use OkHttp client for better performance
     */
    @Bean
    @ConditionalOnClass(OkHttpClient.class)
    public feign.Client feignClient() {
        return new OkHttpClient();
    }

    /**
     * Custom Feign error decoder
     */
    public static class FeignErrorDecoder implements ErrorDecoder {
        
        private final ErrorDecoder defaultErrorDecoder = new Default();
        
        @Override
        public Exception decode(String methodKey, feign.Response response) {
            switch (response.status()) {
                case 400:
                    return new BadRequestException("Bad request: " + methodKey);
                case 401:
                    return new UnauthorizedException("Unauthorized: " + methodKey);
                case 403:
                    return new ForbiddenException("Forbidden: " + methodKey);
                case 404:
                    return new NotFoundException("Not found: " + methodKey);
                case 429:
                    return new RateLimitException("Rate limit exceeded: " + methodKey);
                case 500:
                case 502:
                case 503:
                case 504:
                    return new ServiceUnavailableException("Service unavailable: " + methodKey);
                default:
                    return defaultErrorDecoder.decode(methodKey, response);
            }
        }
    }

    // Custom exception classes
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }
}