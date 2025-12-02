package com.waqiti.kyc.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Configuration
@Slf4j
public class ResilienceConfiguration {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.ofDefaults();
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.ofDefaults();
    }

    @Bean
    public CircuitBreaker kycProviderCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        CircuitBreaker circuitBreaker = registry.circuitBreaker("kyc-provider", config);
        
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                    log.info("KYC Provider Circuit breaker state transition: {}", event))
                .onFailureRateExceeded(event -> 
                    log.warn("KYC Provider Circuit breaker failure rate exceeded: {}", event))
                .onCallNotPermitted(event -> 
                    log.warn("KYC Provider Circuit breaker call not permitted: {}", event));
        
        return circuitBreaker;
    }

    @Bean
    public CircuitBreaker databaseCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(70)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreaker circuitBreaker = registry.circuitBreaker("database", config);
        
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                    log.info("Database Circuit breaker state transition: {}", event))
                .onFailureRateExceeded(event -> 
                    log.error("Database Circuit breaker failure rate exceeded: {}", event));
        
        return circuitBreaker;
    }

    @Bean
    public CircuitBreaker documentStorageCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(45))
                .slidingWindowSize(15)
                .minimumNumberOfCalls(8)
                .permittedNumberOfCallsInHalfOpenState(4)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreaker circuitBreaker = registry.circuitBreaker("document-storage", config);
        
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                    log.info("Document Storage Circuit breaker state transition: {}", event))
                .onFailureRateExceeded(event -> 
                    log.warn("Document Storage Circuit breaker failure rate exceeded: {}", event));
        
        return circuitBreaker;
    }

    @Bean
    public Retry kycProviderRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .intervalFunction(num -> num * 1000L) // Exponential backoff
                .retryOnException(throwable -> 
                    throwable instanceof TimeoutException ||
                    throwable instanceof RuntimeException)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        Retry retry = registry.retry("kyc-provider", config);
        
        retry.getEventPublisher()
                .onRetry(event -> 
                    log.info("KYC Provider retry attempt: {}", event))
                .onError(event -> 
                    log.error("KYC Provider retry failed: {}", event));
        
        return retry;
    }

    @Bean
    public Retry databaseRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofMillis(500))
                .intervalFunction(num -> (long) Math.pow(2, num) * 500) // Exponential backoff
                .build();

        Retry retry = registry.retry("database", config);
        
        retry.getEventPublisher()
                .onRetry(event -> 
                    log.info("Database retry attempt: {}", event))
                .onError(event -> 
                    log.error("Database retry failed: {}", event));
        
        return retry;
    }

    @Bean
    public TimeLimiter kycProviderTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .cancelRunningFuture(true)
                .build();

        TimeLimiter timeLimiter = registry.timeLimiter("kyc-provider", config);
        
        timeLimiter.getEventPublisher()
                .onTimeout(event -> 
                    log.warn("KYC Provider operation timed out: {}", event));
        
        return timeLimiter;
    }

    @Bean
    public TimeLimiter documentStorageTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(60)) // Longer timeout for file operations
                .cancelRunningFuture(true)
                .build();

        TimeLimiter timeLimiter = registry.timeLimiter("document-storage", config);
        
        timeLimiter.getEventPublisher()
                .onTimeout(event -> 
                    log.warn("Document Storage operation timed out: {}", event));
        
        return timeLimiter;
    }
}