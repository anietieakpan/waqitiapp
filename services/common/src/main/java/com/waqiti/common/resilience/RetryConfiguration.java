package com.waqiti.common.resilience;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.core.IntervalFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

/**
 * Retry configuration for resilience patterns in Waqiti services
 * Implements intelligent retry strategies with exponential backoff
 */
@Configuration
@Slf4j
public class RetryConfiguration {

    /**
     * Retry registry for managing retry instances
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryRegistry registry = RetryRegistry.ofDefaults();
        
        // Add event listeners for monitoring
        registry.getEventPublisher().onEntryAdded(event ->
            log.info("Retry configuration added: {}", event.getAddedEntry().getName()));
            
        return registry;
    }

    /**
     * Payment system retry configuration
     * Used for: Core banking operations, payment processing
     */
    @Bean
    public Retry paymentRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)  // Maximum 3 attempts for payment operations
            .waitDuration(Duration.ofSeconds(2))  // Initial wait of 2 seconds
            .retryExceptions(
                java.net.SocketTimeoutException.class,
                java.net.ConnectException.class,
                java.io.IOException.class
            )
            .ignoreExceptions(
                IllegalArgumentException.class,
                IllegalStateException.class
            )
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(2), 2.0))
            .failAfterMaxAttempts(true)
            .build();
            
        Retry retry = registry.retry("payment-system", config);
        
        // Add event listeners
        retry.getEventPublisher()
            .onRetry(event ->
                log.warn("Payment operation retry attempt {} of {} for: {}", 
                    event.getNumberOfRetryAttempts(), 
                    config.getMaxAttempts(),
                    event.getLastThrowable().getMessage()));
                    
        retry.getEventPublisher()
            .onError(event ->
                log.error("Payment operation failed after {} attempts: {}", 
                    event.getNumberOfRetryAttempts(),
                    event.getLastThrowable().getMessage()));
                    
        return retry;
    }

    /**
     * External API retry configuration
     * Used for: Bank APIs, payment providers, third-party services
     */
    @Bean
    public Retry externalApiRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(5)  // More attempts for external APIs
            .waitDuration(Duration.ofSeconds(3))  // Longer initial wait
            .retryExceptions(
                org.springframework.web.client.HttpServerErrorException.class,  // 5xx errors
                org.springframework.web.client.ResourceAccessException.class,   // Network issues
                java.net.SocketTimeoutException.class,
                java.io.IOException.class
            )
            .ignoreExceptions(
                org.springframework.web.client.HttpClientErrorException.class,  // 4xx errors (don't retry)
                IllegalArgumentException.class
            )
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                Duration.ofSeconds(3),  // Initial interval
                2.0,                   // Multiplier
                0.1,                   // Randomization factor
                Duration.ofMinutes(2)  // Max interval
            ))
            .build();
            
        Retry retry = registry.retry("external-api", config);
        
        retry.getEventPublisher()
            .onRetry(event ->
                log.info("External API retry attempt {} for endpoint: {}", 
                    event.getNumberOfRetryAttempts(),
                    extractEndpointFromException(event.getLastThrowable())));
                    
        return retry;
    }

    /**
     * Database retry configuration
     * Used for: Database connection and query retries
     */
    @Bean
    public Retry databaseRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(4)  // 4 attempts for database operations
            .waitDuration(Duration.ofMillis(500))  // Shorter initial wait for DB
            .retryExceptions(
                java.sql.SQLException.class,
                org.springframework.dao.TransientDataAccessException.class,
                org.springframework.dao.DataAccessResourceFailureException.class
            )
            .ignoreExceptions(
                org.springframework.dao.DataIntegrityViolationException.class,
                org.springframework.dao.DuplicateKeyException.class
            )
            .intervalFunction(IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(500), 1.5))  // Faster backoff for DB
            .build();
            
        Retry retry = registry.retry("database", config);
        
        retry.getEventPublisher()
            .onError(event ->
                log.error("Database operation failed after {} attempts - CRITICAL: {}", 
                    event.getNumberOfRetryAttempts(),
                    event.getLastThrowable().getMessage()));
                    
        return retry;
    }

    /**
     * Crypto/blockchain retry configuration
     * Used for: Blockchain operations, crypto transactions
     */
    @Bean
    public Retry cryptoRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(6)  // More attempts for blockchain (can be slow)
            .waitDuration(Duration.ofSeconds(5))  // Longer wait for blockchain
            .retryExceptions(
                java.net.ConnectException.class,
                java.net.SocketTimeoutException.class,
                java.io.IOException.class
            )
            .ignoreExceptions(
                IllegalArgumentException.class,
                IllegalStateException.class
            )
            .intervalFunction(IntervalFunction.ofExponentialBackoff(
                Duration.ofSeconds(5), 1.8, Duration.ofMinutes(5)))  // Max 5 min wait
            .build();
            
        Retry retry = registry.retry("crypto-blockchain", config);
        
        retry.getEventPublisher()
            .onRetry(event ->
                log.info("Blockchain operation retry {} - waiting for network sync", 
                    event.getNumberOfRetryAttempts()));
                    
        return retry;
    }

    /**
     * Notification retry configuration
     * Used for: SMS, email, push notifications
     */
    @Bean
    public Retry notificationRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .retryExceptions(
                java.io.IOException.class,
                java.net.SocketTimeoutException.class
            )
            .ignoreExceptions(
                IllegalArgumentException.class
            )
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2.0))
            .build();
            
        return registry.retry("notification", config);
    }

    /**
     * KYC/Compliance retry configuration
     * Used for: Identity verification, compliance checks
     */
    @Bean
    public Retry complianceRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(
                java.net.SocketTimeoutException.class,
                java.io.IOException.class
            )
            .ignoreExceptions(
                IllegalArgumentException.class
            )
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(2), 1.5))
            .build();
            
        Retry retry = registry.retry("compliance", config);
        
        retry.getEventPublisher()
            .onError(event ->
                log.warn("Compliance check failed after retries - manual review required: {}", 
                    event.getLastThrowable().getMessage()));
                    
        return retry;
    }

    /**
     * Analytics retry configuration
     * Used for: Real-time analytics, data processing
     */
    @Bean
    public Retry analyticsRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(2)  // Fewer retries for analytics (non-critical)
            .waitDuration(Duration.ofSeconds(1))
            .retryExceptions(
                java.io.IOException.class
            )
            .intervalFunction(IntervalFunction.of(Duration.ofSeconds(1)))
            .build();
            
        return registry.retry("analytics", config);
    }

    /**
     * File processing retry configuration
     * Used for: Document uploads, image processing
     */
    @Bean
    public Retry fileProcessingRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(
                java.io.IOException.class
            )
            .ignoreExceptions(
                IllegalArgumentException.class
            )
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(2), 1.5))
            .build();
            
        return registry.retry("file-processing", config);
    }

    /**
     * Helper method to extract endpoint information from exceptions
     */
    private String extractEndpointFromException(Throwable throwable) {
        if (throwable instanceof HttpClientErrorException) {
            HttpClientErrorException ex = (HttpClientErrorException) throwable;
            return ex.getClass().getSimpleName();
        }
        return "unknown-endpoint";
    }
}