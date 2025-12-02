package com.waqiti.rewards.client;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for Wallet Service Feign Client
 * Handles authentication, timeouts, logging, and error handling for wallet service calls
 */
@Configuration
@Slf4j
public class WalletServiceClientConfig {

    @Value("${services.wallet.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${services.wallet.read-timeout:15000}")
    private int readTimeout;

    @Value("${services.wallet.follow-redirects:false}")
    private boolean followRedirects;

    /**
     * Configure request timeout options for wallet operations
     * Wallet operations may take longer due to financial transaction processing
     */
    @Bean
    public Request.Options walletServiceRequestOptions() {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS,
                readTimeout, TimeUnit.MILLISECONDS,
                followRedirects
        );
    }

    /**
     * Request interceptor to add authentication and tracking headers
     */
    @Bean
    public RequestInterceptor walletServiceRequestInterceptor() {
        return requestTemplate -> {
            // Add authentication from security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() != null) {
                requestTemplate.header("Authorization", "Bearer " + authentication.getCredentials().toString());
            }

            // Add idempotency key for wallet operations
            String idempotencyKey = UUID.randomUUID().toString();
            requestTemplate.header("Idempotency-Key", idempotencyKey);

            // Add service identification headers
            requestTemplate.header("X-Service-Name", "rewards-service");
            requestTemplate.header("X-Service-Version", "1.0");
            requestTemplate.header("X-Operation-Type", "rewards-credit");
            
            // Add content type headers
            requestTemplate.header("Content-Type", "application/json");
            requestTemplate.header("Accept", "application/json");
            
            log.debug("Wallet service request: {} {} with idempotency key: {}", 
                requestTemplate.method(), requestTemplate.path(), idempotencyKey);
        };
    }

    /**
     * Custom error decoder for wallet service errors
     */
    @Bean
    public ErrorDecoder walletServiceErrorDecoder() {
        return new WalletServiceErrorDecoder();
    }

    /**
     * Feign logger level configuration
     */
    @Bean
    public Logger.Level walletServiceFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Custom error decoder implementation for wallet-specific errors
     */
    @Slf4j
    public static class WalletServiceErrorDecoder implements ErrorDecoder {
        
        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            log.error("Wallet service error - Method: {}, Status: {}", methodKey, response.status());

            switch (response.status()) {
                case 400:
                    return new WalletServiceException("Invalid wallet request: " + response.reason());
                case 401:
                    return new WalletServiceException("Authentication failed for wallet service");
                case 403:
                    return new WalletServiceException("Insufficient permissions for wallet operation");
                case 404:
                    return new WalletServiceException("Wallet not found: " + response.reason());
                case 409:
                    return new WalletServiceException("Wallet operation conflict: " + response.reason());
                case 422:
                    return new InsufficientFundsException("Insufficient funds for operation: " + response.reason());
                case 429:
                    return new WalletServiceException("Rate limit exceeded for wallet service");
                case 500:
                case 502:
                case 503:
                case 504:
                    return new WalletServiceException("Wallet service temporarily unavailable: " + response.reason());
                default:
                    return defaultErrorDecoder.decode(methodKey, response);
            }
        }
    }

    /**
     * Custom exception for wallet service errors
     */
    public static class WalletServiceException extends RuntimeException {
        public WalletServiceException(String message) {
            super(message);
        }
    }

    /**
     * Exception for insufficient funds scenarios
     */
    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }
}