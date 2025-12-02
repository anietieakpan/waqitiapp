package com.waqiti.common.resilience;

import com.waqiti.common.exception.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import jakarta.validation.ConstraintViolationException;

/**
 * Circuit breaker configuration for resilience patterns in Waqiti services
 * Implements comprehensive failure detection and recovery strategies
 */
@Configuration
@Slf4j
public class CircuitBreakerConfiguration {

    /**
     * Circuit breaker registry with event monitoring
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        
        // Add event consumer for monitoring
        registry.getEventPublisher().onEntryAdded(entryAddedEvent -> 
            log.info("Circuit breaker added: {}", entryAddedEvent.getAddedEntry().getName()));
        
        registry.getEventPublisher().onEntryRemoved(entryRemovedEvent -> 
            log.info("Circuit breaker removed: {}", entryRemovedEvent.getRemovedEntry().getName()));
            
        return registry;
    }

    /**
     * CRITICAL: High availability circuit breaker for critical payment systems
     * PRODUCTION FIX: Enhanced configuration with comprehensive error handling
     * Used for: Core banking, payment processing, wallet operations
     */
    @Bean
    public CircuitBreaker paymentCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(15.0f)  // CRITICAL: Lower threshold for payment systems
            .slowCallRateThreshold(50.0f)  // Monitor slow calls that may indicate issues
            .slowCallDurationThreshold(Duration.ofSeconds(5))  // Calls >5s are considered slow
            .waitDurationInOpenState(Duration.ofSeconds(20))  // Shorter recovery time for critical systems
            .slidingWindowSize(20)  // Larger window for better accuracy
            .minimumNumberOfCalls(5)  // Minimum 5 calls before evaluation
            .permittedNumberOfCallsInHalfOpenState(3)  // 3 test calls in half-open
            .writableStackTraceEnabled(false)  // Performance optimization
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                // PRODUCTION: Record specific exceptions that indicate service failure
                java.net.SocketTimeoutException.class,
                java.net.ConnectException.class,
                java.net.UnknownHostException.class,
                org.springframework.web.client.ResourceAccessException.class,
                com.waqiti.common.exception.ExternalServiceException.class,
                com.waqiti.common.exception.PaymentNotFoundException.class,
                com.waqiti.common.exception.PaymentProcessingException.class,
                com.waqiti.common.exception.BankConnectionException.class,
                com.waqiti.common.exception.AccountNotFoundException.class
            )
            .ignoreExceptions(
                // PRODUCTION: Ignore business logic exceptions that don't indicate service failure
                com.waqiti.common.exception.ValidationException.class,
                com.waqiti.common.exception.InsufficientFundsException.class,
                com.waqiti.common.exception.BusinessException.class,
                com.waqiti.common.exception.AuthenticationException.class
            )
            .build();
            
        CircuitBreaker circuitBreaker = registry.circuitBreaker("payment-system", config);
        
        // Add event listeners for payment circuit breaker
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Payment circuit breaker state transition: {} -> {}", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
                    
        circuitBreaker.getEventPublisher()
            .onFailureRateExceeded(event ->
                log.error("Payment circuit breaker failure rate exceeded: {}% (threshold: {}%)",
                    event.getFailureRate(), circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold()));
                    
        return circuitBreaker;
    }

    /**
     * External API circuit breaker for third-party integrations
     * Used for: Bank APIs, payment providers, KYC services
     */
    @Bean
    public CircuitBreaker externalApiCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(40.0f)  // 40% failure rate threshold (more lenient for external APIs)
            .waitDurationInOpenState(Duration.ofMinutes(2))  // 2min wait in open state
            .slidingWindowSize(20)  // Evaluate last 20 calls
            .minimumNumberOfCalls(10)  // Minimum 10 calls before evaluation
            .permittedNumberOfCallsInHalfOpenState(5)  // 5 test calls in half-open
            .slowCallRateThreshold(60.0f)  // 60% slow call threshold
            .slowCallDurationThreshold(Duration.ofSeconds(10))  // 10s considered slow for external APIs
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                ExternalApiException.class,
                TimeoutException.class,
                ConnectException.class,
                SocketTimeoutException.class
            )
            .ignoreExceptions(
                BadRequestException.class,
                UnauthorizedException.class,
                NotFoundException.class
            )
            .build();
            
        CircuitBreaker circuitBreaker = registry.circuitBreaker("external-api", config);
        
        // Add event listeners
        circuitBreaker.getEventPublisher()
            .onCallNotPermitted(event ->
                log.warn("External API call not permitted due to circuit breaker: {}", 
                    circuitBreaker.getName()));
                    
        return circuitBreaker;
    }

    /**
     * Database circuit breaker for database operations
     * Used for: All database connections and queries
     */
    @Bean
    public CircuitBreaker databaseCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(30.0f)  // 30% failure rate threshold
            .waitDurationInOpenState(Duration.ofSeconds(45))  // 45s wait in open state
            .slidingWindowSize(15)  // Evaluate last 15 calls
            .minimumNumberOfCalls(8)  // Minimum 8 calls before evaluation
            .permittedNumberOfCallsInHalfOpenState(4)  // 4 test calls in half-open
            .slowCallRateThreshold(70.0f)  // 70% slow call threshold
            .slowCallDurationThreshold(Duration.ofSeconds(5))  // 5s considered slow for DB
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                SQLException.class,
                DataAccessException.class
            )
            .ignoreExceptions(
                DataIntegrityViolationException.class,
                ConstraintViolationException.class
            )
            .build();
            
        CircuitBreaker circuitBreaker = registry.circuitBreaker("database", config);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event ->
                log.error("Database circuit breaker state transition: {} -> {} - CRITICAL ALERT",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
                    
        return circuitBreaker;
    }

    /**
     * Crypto/blockchain circuit breaker for cryptocurrency operations
     * Used for: Crypto transactions, blockchain API calls
     */
    @Bean
    public CircuitBreaker cryptoCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(35.0f)  // 35% failure rate threshold
            .waitDurationInOpenState(Duration.ofMinutes(3))  // 3min wait for blockchain sync
            .slidingWindowSize(25)  // Evaluate last 25 calls
            .minimumNumberOfCalls(10)  // Minimum 10 calls before evaluation
            .permittedNumberOfCallsInHalfOpenState(5)  // 5 test calls in half-open
            .slowCallRateThreshold(50.0f)  // 50% slow call threshold
            .slowCallDurationThreshold(Duration.ofSeconds(15))  // 15s considered slow for blockchain
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                BlockchainConnectionException.class,
                TransactionBroadcastException.class,
                NodeSyncException.class
            )
            .ignoreExceptions(
                InsufficientCryptoFundsException.class,
                InvalidAddressException.class
            )
            .build();
            
        return registry.circuitBreaker("crypto-blockchain", config);
    }

    /**
     * Notification circuit breaker for messaging services
     * Used for: SMS, email, push notifications
     */
    @Bean
    public CircuitBreaker notificationCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f)  // 50% failure rate threshold (less critical)
            .waitDurationInOpenState(Duration.ofMinutes(1))  // 1min wait
            .slidingWindowSize(30)  // Evaluate last 30 calls
            .minimumNumberOfCalls(15)  // Minimum 15 calls before evaluation
            .permittedNumberOfCallsInHalfOpenState(10)  // 10 test calls in half-open
            .slowCallRateThreshold(80.0f)  // 80% slow call threshold
            .slowCallDurationThreshold(Duration.ofSeconds(8))  // 8s considered slow
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                NotificationDeliveryException.class,
                SmsProviderException.class,
                EmailProviderException.class
            )
            .build();
            
        return registry.circuitBreaker("notification", config);
    }

    /**
     * Analytics circuit breaker for real-time analytics
     * Used for: Real-time data processing, analytics queries
     */
    @Bean
    public CircuitBreaker analyticsCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(60.0f)  // 60% failure rate threshold (non-critical)
            .waitDurationInOpenState(Duration.ofSeconds(30))  // 30s wait
            .slidingWindowSize(50)  // Evaluate last 50 calls
            .minimumNumberOfCalls(20)  // Minimum 20 calls before evaluation
            .permittedNumberOfCallsInHalfOpenState(10)  // 10 test calls in half-open
            .slowCallRateThreshold(70.0f)  // 70% slow call threshold
            .slowCallDurationThreshold(Duration.ofSeconds(5))  // 5s considered slow
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                AnalyticsProcessingException.class,
                DataStreamException.class
            )
            .build();
            
        return registry.circuitBreaker("analytics", config);
    }
}