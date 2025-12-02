package com.waqiti.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Comprehensive Circuit Breaker Configuration for all external service calls
 * Implements the Circuit Breaker pattern for fault tolerance and resilience
 */
@Configuration
public class CircuitBreakerConfig {

    @Value("${resilience4j.circuitbreaker.configs.default.slidingWindowSize:100}")
    private int slidingWindowSize;

    @Value("${resilience4j.circuitbreaker.configs.default.failureRateThreshold:50}")
    private float failureRateThreshold;

    @Value("${resilience4j.circuitbreaker.configs.default.waitDurationInOpenState:60000}")
    private long waitDurationInOpenState;

    @Value("${resilience4j.circuitbreaker.configs.default.permittedNumberOfCallsInHalfOpenState:10}")
    private int permittedNumberOfCallsInHalfOpenState;

    @Value("${resilience4j.retry.configs.default.maxAttempts:3}")
    private int maxRetryAttempts;

    @Value("${resilience4j.retry.configs.default.waitDuration:1000}")
    private long retryWaitDuration;

    /**
     * Default circuit breaker configuration
     */
    @Bean
    @Primary
    public io.github.resilience4j.circuitbreaker.CircuitBreakerConfig defaultCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenState))
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(IOException.class, TimeoutException.class, 
                               ConnectException.class, SocketTimeoutException.class)
                .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
                .build();
    }

    /**
     * Circuit breaker configuration for payment services (more conservative)
     */
    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreakerConfig paymentCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(50)
                .failureRateThreshold(30) // Lower threshold for payment services
                .waitDurationInOpenState(Duration.ofSeconds(120)) // Longer wait time
                .permittedNumberOfCallsInHalfOpenState(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .recordExceptions(IOException.class, TimeoutException.class)
                .build();
    }

    /**
     * Circuit breaker configuration for KYC services
     */
    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreakerConfig kycCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .slidingWindowSize(60) // 60 seconds
                .failureRateThreshold(40)
                .waitDurationInOpenState(Duration.ofSeconds(90))
                .permittedNumberOfCallsInHalfOpenState(8)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .minimumNumberOfCalls(10)
                .build();
    }

    /**
     * Circuit breaker configuration for notification services (more lenient)
     */
    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreakerConfig notificationCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(200)
                .failureRateThreshold(70) // Higher threshold for non-critical services
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(20)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
    }

    /**
     * Circuit breaker configuration for ML services (optimized for batch processing)
     */
    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreakerConfig mlServiceCircuitBreakerConfig() {
        return io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .slidingWindowSize(120) // 2 minutes window
                .failureRateThreshold(35) // Lower threshold for ML services
                .waitDurationInOpenState(Duration.ofSeconds(180)) // 3 minutes wait
                .permittedNumberOfCallsInHalfOpenState(3) // Few test calls
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .slowCallRateThreshold(50) // ML calls can be slower
                .slowCallDurationThreshold(Duration.ofSeconds(10)) // 10 seconds for ML inference
                .minimumNumberOfCalls(5)
                .recordExceptions(IOException.class, TimeoutException.class)
                .build();
    }

    /**
     * Circuit breaker registry
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultCircuitBreakerConfig());
        
        // Register specific circuit breakers
        registry.circuitBreaker("payment-gateway", paymentCircuitBreakerConfig());
        registry.circuitBreaker("kyc-service", kycCircuitBreakerConfig());
        registry.circuitBreaker("notification-service", notificationCircuitBreakerConfig());
        registry.circuitBreaker("fraud-detection", defaultCircuitBreakerConfig());
        registry.circuitBreaker("currency-exchange", defaultCircuitBreakerConfig());
        registry.circuitBreaker("bank-integration", paymentCircuitBreakerConfig());
        
        // New circuit breakers for external integrations
        registry.circuitBreaker("wise-api", paymentCircuitBreakerConfig());
        registry.circuitBreaker("onfido-kyc", kycCircuitBreakerConfig());
        registry.circuitBreaker("tensorflow-serving", mlServiceCircuitBreakerConfig());
        registry.circuitBreaker("currencylayer", defaultCircuitBreakerConfig());
        registry.circuitBreaker("core-banking-service", paymentCircuitBreakerConfig());
        
        return registry;
    }

    /**
     * Default retry configuration with exponential backoff
     */
    @Bean
    @Primary
    public RetryConfig defaultRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(maxRetryAttempts)
                .waitDuration(Duration.ofMillis(retryWaitDuration))
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(retryWaitDuration, 2))
                .retryExceptions(IOException.class, TimeoutException.class, 
                               ConnectException.class, SocketTimeoutException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();
    }

    /**
     * Retry configuration for payment operations (more attempts)
     */
    @Bean
    public RetryConfig paymentRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofSeconds(2))
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(2000, 1.5, 30000))
                .retryExceptions(IOException.class, TimeoutException.class)
                .retryOnResult(response -> response == null)
                .build();
    }

    /**
     * Retry configuration for ML services (fewer attempts, longer delays)
     */
    @Bean
    public RetryConfig mlRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(2) // Fewer attempts for ML services
                .waitDuration(Duration.ofSeconds(5)) // Longer initial wait
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(5000, 2, 60000)) // Max 1 minute between retries
                .retryExceptions(IOException.class, TimeoutException.class, ConnectException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();
    }

    /**
     * Retry registry
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryRegistry registry = RetryRegistry.of(defaultRetryConfig());
        
        registry.retry("payment-retry", paymentRetryConfig());
        registry.retry("kyc-retry", defaultRetryConfig());
        registry.retry("notification-retry", defaultRetryConfig());
        
        // New retry configurations
        registry.retry("wise-api", paymentRetryConfig());
        registry.retry("onfido-kyc", defaultRetryConfig());
        registry.retry("tensorflow-serving", mlRetryConfig());
        registry.retry("currencylayer", defaultRetryConfig());
        registry.retry("core-banking-service", paymentRetryConfig());
        
        return registry;
    }

    /**
     * Bulkhead configuration for resource isolation
     */
    @Bean
    @Primary
    public BulkheadConfig defaultBulkheadConfig() {
        return BulkheadConfig.custom()
                .maxConcurrentCalls(25)
                .maxWaitDuration(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Bulkhead configuration for payment operations (strict isolation)
     */
    @Bean
    public BulkheadConfig paymentBulkheadConfig() {
        return BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .maxWaitDuration(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Bulkhead registry
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadRegistry registry = BulkheadRegistry.of(defaultBulkheadConfig());
        
        registry.bulkhead("payment-bulkhead", paymentBulkheadConfig());
        registry.bulkhead("kyc-bulkhead", defaultBulkheadConfig());
        registry.bulkhead("notification-bulkhead", defaultBulkheadConfig());
        
        return registry;
    }

    /**
     * Rate limiter configuration
     */
    @Bean
    @Primary
    public RateLimiterConfig defaultRateLimiterConfig() {
        return RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(100)
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Rate limiter configuration for public APIs
     */
    @Bean
    public RateLimiterConfig publicApiRateLimiterConfig() {
        return RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(60)
                .timeoutDuration(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Rate limiter configuration for external API integrations
     */
    @Bean
    public RateLimiterConfig externalApiRateLimiterConfig() {
        return RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(10) // Conservative limit for external APIs
                .timeoutDuration(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Rate limiter configuration for ML services
     */
    @Bean
    public RateLimiterConfig mlServiceRateLimiterConfig() {
        return RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(5) // Lower limit for resource-intensive ML calls
                .timeoutDuration(Duration.ofSeconds(60)) // Longer timeout for ML inference
                .build();
    }

    /**
     * Rate limiter registry
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterRegistry registry = RateLimiterRegistry.of(defaultRateLimiterConfig());
        
        registry.rateLimiter("public-api", publicApiRateLimiterConfig());
        registry.rateLimiter("payment-api", defaultRateLimiterConfig());
        registry.rateLimiter("kyc-api", defaultRateLimiterConfig());
        
        // New rate limiters for external services
        registry.rateLimiter("wise-api", externalApiRateLimiterConfig());
        registry.rateLimiter("onfido-kyc", externalApiRateLimiterConfig());
        registry.rateLimiter("tensorflow-serving", mlServiceRateLimiterConfig());
        registry.rateLimiter("currencylayer", externalApiRateLimiterConfig());
        
        return registry;
    }

    /**
     * Time limiter configuration
     */
    @Bean
    @Primary
    public TimeLimiterConfig defaultTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build();
    }

    /**
     * Time limiter configuration for payment operations
     */
    @Bean
    public TimeLimiterConfig paymentTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .cancelRunningFuture(false) // Don't cancel payment operations
                .build();
    }

    /**
     * Time limiter registry
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterRegistry registry = TimeLimiterRegistry.of(defaultTimeLimiterConfig());
        
        registry.timeLimiter("payment-timeout", paymentTimeLimiterConfig());
        registry.timeLimiter("kyc-timeout", defaultTimeLimiterConfig());
        registry.timeLimiter("notification-timeout", defaultTimeLimiterConfig());
        
        return registry;
    }
}