package com.waqiti.common.resilience;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Global Resilience4j configuration for the Waqiti platform
 * 
 * This configuration provides enterprise-grade resilience patterns including:
 * - Circuit Breakers for fault tolerance
 * - Rate Limiters for API protection
 * - Bulkheads for resource isolation
 * - Retry mechanisms with exponential backoff
 * - Time Limiters for timeout management
 * 
 * Configuration Tiers:
 * 1. CRITICAL - Financial transactions, payments, settlements
 * 2. HIGH - Compliance, fraud detection, KYC/AML
 * 3. MEDIUM - Notifications, reporting, analytics
 * 4. LOW - Non-critical operations, background tasks
 * 
 * @author Waqiti Platform Team
 * @since Phase 2 - P1 Remediation
 */
@Slf4j
@Configuration
public class GlobalResilience4jConfig {

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    @Value("${resilience4j.metrics.enabled:true}")
    private boolean metricsEnabled;
    
    /**
     * CRITICAL Circuit Breaker - For financial transactions
     * Strictest settings to protect financial integrity
     */
    @Bean
    public CircuitBreakerConfig criticalCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(100) // 100 seconds window
                .failureRateThreshold(10) // Open circuit at 10% failure rate
                .slowCallRateThreshold(20) // Open circuit at 20% slow calls
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .waitDurationInOpenState(Duration.ofSeconds(60)) // Wait 1 minute
                .permittedNumberOfCallsInHalfOpenState(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(IOException.class, TimeoutException.class, 
                               RuntimeException.class)
                .ignoreExceptions(IllegalArgumentException.class, 
                                IllegalStateException.class)
                .minimumNumberOfCalls(10) // Minimum calls before evaluation
                .build();
    }
    
    /**
     * HIGH Priority Circuit Breaker - For compliance and fraud detection
     */
    @Bean
    public CircuitBreakerConfig highPriorityCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(50) // 50 calls window
                .failureRateThreshold(25) // Open at 25% failure
                .slowCallRateThreshold(30) // Open at 30% slow calls
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(10)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .minimumNumberOfCalls(5)
                .build();
    }
    
    /**
     * MEDIUM Priority Circuit Breaker - For notifications and reporting
     */
    @Bean
    public CircuitBreakerConfig mediumPriorityCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .failureRateThreshold(50) // More tolerant
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .permittedNumberOfCallsInHalfOpenState(15)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .minimumNumberOfCalls(3)
                .build();
    }
    
    /**
     * Rate Limiter for External APIs
     */
    @Bean
    public RateLimiterConfig externalApiRateLimiterConfig() {
        return RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(100) // 100 requests per second
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
    }
    
    /**
     * Rate Limiter for Internal APIs
     */
    @Bean
    public RateLimiterConfig internalApiRateLimiterConfig() {
        return RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(1000) // 1000 requests per second
                .timeoutDuration(Duration.ofSeconds(2))
                .build();
    }
    
    /**
     * Rate Limiter for Compliance APIs (stricter)
     */
    @Bean
    public RateLimiterConfig complianceApiRateLimiterConfig() {
        return RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(50) // 50 requests per minute
                .timeoutDuration(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * Bulkhead for Database Operations
     */
    @Bean
    public BulkheadConfig databaseBulkheadConfig() {
        return BulkheadConfig.custom()
                .maxConcurrentCalls(50) // Max 50 concurrent DB calls
                .maxWaitDuration(Duration.ofSeconds(5))
                .build();
    }
    
    /**
     * Bulkhead for Kafka Consumers
     */
    @Bean
    public BulkheadConfig kafkaBulkheadConfig() {
        return BulkheadConfig.custom()
                .maxConcurrentCalls(100) // Max 100 concurrent Kafka operations
                .maxWaitDuration(Duration.ofSeconds(2))
                .build();
    }
    
    /**
     * Bulkhead for External Service Calls
     */
    @Bean
    public BulkheadConfig externalServiceBulkheadConfig() {
        return BulkheadConfig.custom()
                .maxConcurrentCalls(25) // Limit external calls
                .maxWaitDuration(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * Retry Configuration for Transient Failures
     */
    @Bean
    public RetryConfig transientFailureRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(Duration.ofMillis(500), 2))
                .retryExceptions(IOException.class, TimeoutException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .failAfterMaxAttempts(true)
                .build();
    }
    
    /**
     * Retry Configuration for Critical Operations
     */
    @Bean
    public RetryConfig criticalOperationRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofSeconds(1))
                .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2))
                .intervalBiFunction((attempts, either) -> {
                    if (either.isRight()) {
                        // Success - no retry needed
                        return -1L;
                    }
                    // Exponential backoff with jitter
                    long interval = (long) Math.pow(2, attempts) * 1000;
                    // SECURITY FIX: Use SecureRandom instead of Math.random()
                    long jitter = (long) (secureRandom.nextDouble() * 1000);
                    return Math.min(interval + jitter, 30000); // Max 30 seconds
                })
                .retryExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class, 
                                IllegalStateException.class)
                .build();
    }
    
    /**
     * Time Limiter for External Calls
     */
    @Bean
    public TimeLimiterConfig externalCallTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .cancelRunningFuture(true)
                .build();
    }
    
    /**
     * Time Limiter for Database Operations
     */
    @Bean
    public TimeLimiterConfig databaseTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build();
    }
    
    /**
     * Circuit Breaker Registry with custom configurations
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.custom()
                .withCircuitBreakerConfig(mediumPriorityCircuitBreakerConfig())
                .build();
        
        // Register specific circuit breakers
        registry.circuitBreaker("payment-processing", criticalCircuitBreakerConfig());
        registry.circuitBreaker("compliance-check", highPriorityCircuitBreakerConfig());
        registry.circuitBreaker("fraud-detection", highPriorityCircuitBreakerConfig());
        registry.circuitBreaker("kyc-verification", highPriorityCircuitBreakerConfig());
        registry.circuitBreaker("notification-service", mediumPriorityCircuitBreakerConfig());
        registry.circuitBreaker("reporting-service", mediumPriorityCircuitBreakerConfig());
        
        // Add metrics if enabled
        if (metricsEnabled) {
            registry.getAllCircuitBreakers().forEach(circuitBreaker -> {
                CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
                Tags tags = Tags.of("name", circuitBreaker.getName());
                
                meterRegistry.gauge("circuit_breaker_failure_rate", tags, metrics, 
                    m -> m.getFailureRate());
                meterRegistry.gauge("circuit_breaker_slow_call_rate", tags, metrics, 
                    m -> m.getSlowCallRate());
                meterRegistry.gauge("circuit_breaker_calls_buffered", tags, metrics, 
                    m -> m.getNumberOfBufferedCalls());
                meterRegistry.gauge("circuit_breaker_calls_failed", tags, metrics, 
                    m -> m.getNumberOfFailedCalls());
            });
        }
        
        log.info("Circuit Breaker Registry initialized with {} circuit breakers", 
                registry.getAllCircuitBreakers().size());
        
        return registry;
    }
    
    /**
     * Rate Limiter Registry with custom configurations
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterRegistry registry = RateLimiterRegistry.custom()
                .withRateLimiterConfig(internalApiRateLimiterConfig())
                .build();
        
        // Register specific rate limiters
        registry.rateLimiter("external-api", externalApiRateLimiterConfig());
        registry.rateLimiter("compliance-api", complianceApiRateLimiterConfig());
        registry.rateLimiter("sanctions-screening", complianceApiRateLimiterConfig());
        registry.rateLimiter("kyc-verification", externalApiRateLimiterConfig());
        registry.rateLimiter("payment-gateway", externalApiRateLimiterConfig());
        
        log.info("Rate Limiter Registry initialized with {} rate limiters", 
                registry.getAllRateLimiters().size());
        
        return registry;
    }
    
    /**
     * Bulkhead Registry with custom configurations
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadRegistry registry = BulkheadRegistry.custom()
                .withBulkheadConfig(kafkaBulkheadConfig())
                .build();
        
        // Register specific bulkheads
        registry.bulkhead("database-operations", databaseBulkheadConfig());
        registry.bulkhead("external-services", externalServiceBulkheadConfig());
        registry.bulkhead("kafka-consumers", kafkaBulkheadConfig());
        
        log.info("Bulkhead Registry initialized with {} bulkheads", 
                registry.getAllBulkheads().size());
        
        return registry;
    }
    
    /**
     * Retry Registry with custom configurations
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryRegistry registry = RetryRegistry.custom()
                .withRetryConfig(transientFailureRetryConfig())
                .build();
        
        // Register specific retry configurations
        registry.retry("critical-operations", criticalOperationRetryConfig());
        registry.retry("payment-processing", criticalOperationRetryConfig());
        registry.retry("settlement-confirmation", criticalOperationRetryConfig());
        
        log.info("Retry Registry initialized with {} retry configurations", 
                registry.getAllRetries().size());
        
        return registry;
    }
    
    /**
     * Time Limiter Registry
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterRegistry registry = TimeLimiterRegistry.ofDefaults();
        
        // Register specific time limiters
        registry.timeLimiter("database-operations", databaseTimeLimiterConfig());
        registry.timeLimiter("external-calls", externalCallTimeLimiterConfig());
        
        log.info("Time Limiter Registry initialized with {} time limiters", 
                registry.getAllTimeLimiters().size());
        
        return registry;
    }
    
    // Helper class for custom interval functions
    private static class IntervalFunction {
        
        static java.util.function.Function<Integer, Long> ofExponentialBackoff(long initialInterval, double multiplier) {
            return attempt -> {
                double interval = initialInterval * Math.pow(multiplier, attempt - 1);
                return (long) Math.min(interval, 60000); // Cap at 60 seconds
            };
        }
    }
}