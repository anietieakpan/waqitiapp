package com.waqiti.payment.client;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Wallet Service Client Configuration
 * 
 * Comprehensive configuration for the unified wallet service client including
 * resilience patterns, security, and logging.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@Configuration
public class WalletServiceClientConfiguration {
    
    @Value("${wallet.client.connect-timeout:5000}")
    private int connectTimeout;
    
    @Value("${wallet.client.read-timeout:10000}")
    private int readTimeout;
    
    @Value("${wallet.client.log-level:BASIC}")
    private String logLevel;
    
    /**
     * Feign request options configuration
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            connectTimeout, TimeUnit.MILLISECONDS,
            readTimeout, TimeUnit.MILLISECONDS,
            true // followRedirects
        );
    }
    
    /**
     * Feign logging level configuration
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.valueOf(logLevel.toUpperCase());
    }
    
    /**
     * Request interceptor for authentication and tracing
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // Add authentication token
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken) {
                JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
                requestTemplate.header("Authorization", "Bearer " + jwtAuth.getToken().getTokenValue());
            }
            
            // Add tracing headers
            requestTemplate.header("X-Request-ID", java.util.UUID.randomUUID().toString());
            requestTemplate.header("X-Client-Service", "payment-commons");
        };
    }
    
    /**
     * Custom error decoder for wallet service errors
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new WalletServiceErrorDecoder();
    }
    
    /**
     * Circuit breaker configuration for wallet service
     */
    @Bean
    public CircuitBreaker walletServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(3))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
        
        return registry.circuitBreaker("wallet-service", config);
    }
    
    /**
     * Retry configuration for wallet service
     */
    @Bean
    public Retry walletServiceRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(500, 2))
            .retryOnException(throwable -> {
                // Retry on network errors and 5xx errors
                return throwable instanceof feign.RetryableException ||
                       throwable.getMessage() != null && throwable.getMessage().contains("5");
            })
            .ignoreExceptions(IllegalArgumentException.class)
            .build();
        
        return registry.retry("wallet-service", config);
    }
    
    /**
     * Custom error decoder implementation
     */
    public static class WalletServiceErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultDecoder = new Default();
        
        @Override
        public Exception decode(String methodKey, feign.Response response) {
            switch (response.status()) {
                case 400:
                    return new WalletServiceException("Bad request to wallet service", response.status());
                case 401:
                    return new WalletServiceException("Unauthorized access to wallet service", response.status());
                case 403:
                    return new WalletServiceException("Forbidden access to wallet service", response.status());
                case 404:
                    return new WalletServiceException("Wallet resource not found", response.status());
                case 409:
                    return new WalletServiceException("Wallet operation conflict", response.status());
                case 422:
                    return new WalletServiceException("Wallet validation error", response.status());
                case 500:
                case 502:
                case 503:
                case 504:
                    return new feign.RetryableException(
                        response.status(),
                        "Wallet service temporarily unavailable",
                        response.request().httpMethod(),
                        null,
                        response.request()
                    );
                default:
                    return defaultDecoder.decode(methodKey, response);
            }
        }
    }
    
    /**
     * Custom exception for wallet service errors
     */
    public static class WalletServiceException extends RuntimeException {
        private final int statusCode;
        
        public WalletServiceException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }
        
        public int getStatusCode() {
            return statusCode;
        }
    }
}