package com.waqiti.payment.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Production-ready circuit breaker configuration for all payment providers.
 * Implements resilience patterns to prevent cascading failures.
 */
@Configuration
public class PaymentCircuitBreakerConfig {

    /**
     * Stripe payment provider circuit breaker
     */
    @Bean
    public CircuitBreaker stripeCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(20)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .slowCallRateThreshold(50)
            .recordExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class)
            .build();
        
        return registry.circuitBreaker("stripe-payment", config);
    }

    /**
     * PayPal payment provider circuit breaker
     */
    @Bean
    public CircuitBreaker paypalCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(20)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slowCallDurationThreshold(Duration.ofSeconds(7))
            .slowCallRateThreshold(50)
            .recordExceptions(Exception.class)
            .build();
        
        return registry.circuitBreaker("paypal-payment", config);
    }

    /**
     * Square payment provider circuit breaker
     */
    @Bean
    public CircuitBreaker squareCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(60)
            .waitDurationInOpenState(Duration.ofSeconds(20))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(15)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slowCallDurationThreshold(Duration.ofSeconds(4))
            .slowCallRateThreshold(60)
            .build();
        
        return registry.circuitBreaker("square-payment", config);
    }

    /**
     * Razorpay payment provider circuit breaker
     */
    @Bean
    public CircuitBreaker razorpayCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(25))
            .permittedNumberOfCallsInHalfOpenState(4)
            .slidingWindowSize(20)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .minimumNumberOfCalls(10)
            .slowCallDurationThreshold(Duration.ofSeconds(6))
            .slowCallRateThreshold(50)
            .build();
        
        return registry.circuitBreaker("razorpay-payment", config);
    }

    /**
     * Flutterwave payment provider circuit breaker
     */
    @Bean
    public CircuitBreaker flutterwaveCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(55)
            .waitDurationInOpenState(Duration.ofSeconds(35))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(25)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slowCallDurationThreshold(Duration.ofSeconds(8))
            .slowCallRateThreshold(55)
            .build();
        
        return registry.circuitBreaker("flutterwave-payment", config);
    }

    /**
     * Bank transfer circuit breaker
     */
    @Bean
    public CircuitBreaker bankTransferCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(40)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(30)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slowCallDurationThreshold(Duration.ofSeconds(15))
            .slowCallRateThreshold(40)
            .build();
        
        return registry.circuitBreaker("bank-transfer", config);
    }

    /**
     * Crypto payment circuit breaker
     */
    @Bean
    public CircuitBreaker cryptoPaymentCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(45)
            .waitDurationInOpenState(Duration.ofSeconds(40))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(20)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            .slowCallRateThreshold(45)
            .build();
        
        return registry.circuitBreaker("crypto-payment", config);
    }

    /**
     * Mobile money circuit breaker
     */
    @Bean
    public CircuitBreaker mobileMoneyCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(60)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(20)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slowCallDurationThreshold(Duration.ofSeconds(12))
            .slowCallRateThreshold(60)
            .build();
        
        return registry.circuitBreaker("mobile-money", config);
    }

    /**
     * Retry configuration for payment operations
     */
    @Bean
    public Retry paymentRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .intervalFunction(IntervalFunction.ofExponentialBackoff(500, 2))
            .retryExceptions(TimeoutException.class, java.net.ConnectException.class)
            .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
            .build();
        
        return registry.retry("payment-retry", config);
    }

    /**
     * Time limiter for payment operations
     */
    @Bean
    public TimeLimiter paymentTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(10))
            .cancelRunningFuture(true)
            .build();
        
        return registry.timeLimiter("payment-timeout", config);
    }

    /**
     * Global circuit breaker registry
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    /**
     * Global retry registry
     */
    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.ofDefaults();
    }

    /**
     * Global time limiter registry
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.ofDefaults();
    }

    /**
     * Circuit breaker health indicator
     */
    @Bean
    public CircuitBreakerHealthIndicator circuitBreakerHealthIndicator(
            CircuitBreakerRegistry circuitBreakerRegistry) {
        return new CircuitBreakerHealthIndicator(circuitBreakerRegistry);
    }

    /**
     * Health indicator for circuit breakers
     */
    public static class CircuitBreakerHealthIndicator {
        private final CircuitBreakerRegistry registry;

        public CircuitBreakerHealthIndicator(CircuitBreakerRegistry registry) {
            this.registry = registry;
        }

        public Map<String, CircuitBreakerHealth> getHealth() {
            Map<String, CircuitBreakerHealth> health = new HashMap<>();
            
            registry.getAllCircuitBreakers().forEach(cb -> {
                CircuitBreakerHealth cbHealth = new CircuitBreakerHealth();
                cbHealth.setState(cb.getState().name());
                cbHealth.setFailureRate(cb.getMetrics().getFailureRate());
                cbHealth.setSlowCallRate(cb.getMetrics().getSlowCallRate());
                cbHealth.setNumberOfSuccessfulCalls(cb.getMetrics().getNumberOfSuccessfulCalls());
                cbHealth.setNumberOfFailedCalls(cb.getMetrics().getNumberOfFailedCalls());
                
                health.put(cb.getName(), cbHealth);
            });
            
            return health;
        }
    }

    /**
     * Circuit breaker health data
     */
    public static class CircuitBreakerHealth {
        private String state;
        private float failureRate;
        private float slowCallRate;
        private int numberOfSuccessfulCalls;
        private int numberOfFailedCalls;

        // Getters and setters
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public float getFailureRate() { return failureRate; }
        public void setFailureRate(float failureRate) { this.failureRate = failureRate; }
        public float getSlowCallRate() { return slowCallRate; }
        public void setSlowCallRate(float slowCallRate) { this.slowCallRate = slowCallRate; }
        public int getNumberOfSuccessfulCalls() { return numberOfSuccessfulCalls; }
        public void setNumberOfSuccessfulCalls(int numberOfSuccessfulCalls) { 
            this.numberOfSuccessfulCalls = numberOfSuccessfulCalls; 
        }
        public int getNumberOfFailedCalls() { return numberOfFailedCalls; }
        public void setNumberOfFailedCalls(int numberOfFailedCalls) { 
            this.numberOfFailedCalls = numberOfFailedCalls; 
        }
    }

}