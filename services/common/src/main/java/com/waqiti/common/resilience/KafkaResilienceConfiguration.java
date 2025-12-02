package com.waqiti.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Production-grade resilience configuration for Kafka operations.
 * Provides comprehensive fault tolerance patterns including:
 * - Circuit Breakers for Kafka producer failures
 * - Retry mechanisms with exponential backoff
 * - Bulkhead pattern for resource isolation
 * - Rate limiting for producer throttling
 * - Dead letter queue handling
 * 
 * Critical for production stability and fault tolerance.
 */
@Slf4j
@Configuration
public class KafkaResilienceConfiguration {

    /**
     * Circuit breaker configuration for Kafka producers
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f) // Open circuit if 50% of calls fail
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before trying again
            .slidingWindowSize(20) // Consider last 20 calls
            .minimumNumberOfCalls(10) // Need at least 10 calls before calculating failure rate
            .permittedNumberOfCallsInHalfOpenState(5) // Allow 5 calls in half-open state
            .slowCallRateThreshold(80.0f) // Slow call threshold
            .slowCallDurationThreshold(Duration.ofSeconds(5)) // Calls taking >5s are slow
            .recordExceptions(
                org.apache.kafka.common.errors.TimeoutException.class,
                org.apache.kafka.common.errors.NetworkException.class,
                org.apache.kafka.common.errors.BrokerNotAvailableException.class,
                org.springframework.kafka.KafkaException.class
            )
            .ignoreExceptions(
                IllegalArgumentException.class,
                org.apache.kafka.common.errors.SerializationException.class
            )
            .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // Specific configuration for critical financial events
        CircuitBreakerConfig criticalConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(30.0f) // More sensitive for critical events
            .waitDurationInOpenState(Duration.ofSeconds(60)) // Longer wait for critical events
            .slidingWindowSize(50)
            .minimumNumberOfCalls(20)
            .build();

        registry.circuitBreaker("kafka-critical-events", criticalConfig);
        registry.circuitBreaker("kafka-payment-events", criticalConfig);
        registry.circuitBreaker("kafka-fraud-events", criticalConfig);
        registry.circuitBreaker("kafka-compliance-events", criticalConfig);

        // Configure event listeners
        registry.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker state transition: {} from {} to {} for {}",
                    event.getCircuitBreakerName(),
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState(),
                    event.getCreationTime()
                ))
            .onFailureRateExceeded(event ->
                log.error("Circuit breaker failure rate exceeded: {} - {}%",
                    event.getCircuitBreakerName(),
                    event.getFailureRate()
                ))
            .onSlowCallRateExceeded(event ->
                log.warn("Circuit breaker slow call rate exceeded: {} - {}%",
                    event.getCircuitBreakerName(),
                    event.getSlowCallRate()
                ));

        return registry;
    }

    /**
     * Retry configuration for Kafka operations
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryOnException(ex -> 
                ex instanceof org.apache.kafka.common.errors.TimeoutException ||
                ex instanceof org.apache.kafka.common.errors.NetworkException ||
                ex instanceof org.apache.kafka.common.errors.BrokerNotAvailableException ||
                ex instanceof org.apache.kafka.common.errors.RetriableException
            )
            .retryExceptions(
                org.apache.kafka.common.errors.TimeoutException.class,
                org.apache.kafka.common.errors.NetworkException.class,
                org.apache.kafka.common.errors.BrokerNotAvailableException.class
            )
            .ignoreExceptions(
                org.apache.kafka.common.errors.SerializationException.class,
                IllegalArgumentException.class
            )
            .build();

        RetryRegistry registry = RetryRegistry.of(defaultConfig);

        // Critical events get more retries with exponential backoff
        RetryConfig criticalConfig = RetryConfig.custom()
            .maxAttempts(5)
            .waitDuration(Duration.ofMillis(1000))
            .intervalFunction(io.github.resilience4j.retry.IntervalFunction.ofExponentialBackoff(1000, 2))
            .retryOnException(ex -> 
                ex instanceof org.apache.kafka.common.errors.TimeoutException ||
                ex instanceof org.apache.kafka.common.errors.NetworkException ||
                ex instanceof org.apache.kafka.common.errors.BrokerNotAvailableException
            )
            .build();

        registry.retry("kafka-critical-retry", criticalConfig);
        registry.retry("kafka-payment-retry", criticalConfig);
        registry.retry("kafka-fraud-retry", criticalConfig);

        // Configure event listeners on registry level - using onEntryAdded to wire up per-instance listeners
        registry.getEventPublisher()
            .onEntryAdded(event -> {
                Retry retry = event.getAddedEntry();
                retry.getEventPublisher()
                    .onRetry(retryEvent ->
                        log.warn("Kafka operation retry attempt {} for {}: {}",
                            retryEvent.getNumberOfRetryAttempts(),
                            retryEvent.getName(),
                            retryEvent.getLastThrowable().getMessage()
                        ))
                    .onSuccess(successEvent ->
                        log.info("Kafka operation succeeded after {} retries for {}",
                            successEvent.getNumberOfRetryAttempts(),
                            successEvent.getName()
                        ))
                    .onError(errorEvent ->
                        log.error("Kafka operation failed after {} retries for {}: {}",
                            errorEvent.getNumberOfRetryAttempts(),
                            errorEvent.getName(),
                            errorEvent.getLastThrowable().getMessage()
                        ));
            });

        return registry;
    }

    /**
     * Bulkhead configuration for resource isolation
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig defaultConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(25) // Allow max 25 concurrent Kafka operations
            .maxWaitDuration(Duration.ofMillis(2000)) // Wait max 2s for a permit
            .build();

        BulkheadRegistry registry = BulkheadRegistry.of(defaultConfig);

        // Critical events get dedicated bulkhead with higher limits
        BulkheadConfig criticalConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(50)
            .maxWaitDuration(Duration.ofMillis(1000))
            .build();

        registry.bulkhead("kafka-critical-bulkhead", criticalConfig);
        registry.bulkhead("kafka-payment-bulkhead", criticalConfig);
        registry.bulkhead("kafka-fraud-bulkhead", criticalConfig);

        // Event listeners - using onEntryAdded to wire up per-instance listeners
        registry.getEventPublisher()
            .onEntryAdded(event -> {
                Bulkhead bulkhead = event.getAddedEntry();
                bulkhead.getEventPublisher()
                    .onCallPermitted(bulkheadEvent ->
                        log.debug("Bulkhead call permitted for {}", bulkheadEvent.getBulkheadName()))
                    .onCallRejected(bulkheadEvent ->
                        log.warn("Bulkhead call rejected for {}", bulkheadEvent.getBulkheadName()))
                    .onCallFinished(bulkheadEvent ->
                        log.debug("Bulkhead call finished for {}", bulkheadEvent.getBulkheadName()));
            });

        return registry;
    }

    /**
     * Rate limiter configuration for Kafka producers
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1)) // Refresh limit every second
            .limitForPeriod(100) // Allow 100 calls per second
            .timeoutDuration(Duration.ofMillis(500)) // Wait max 500ms for permission
            .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(defaultConfig);

        // Different limits for different event types
        RateLimiterConfig highVolumeConfig = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(500) // Higher limit for high-volume events
            .timeoutDuration(Duration.ofMillis(100))
            .build();

        RateLimiterConfig criticalConfig = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(50) // Lower limit but guaranteed for critical events
            .timeoutDuration(Duration.ofMillis(1000))
            .build();

        registry.rateLimiter("kafka-high-volume", highVolumeConfig);
        registry.rateLimiter("kafka-critical", criticalConfig);
        registry.rateLimiter("kafka-payment", criticalConfig);
        registry.rateLimiter("kafka-fraud", criticalConfig);

        // Event listeners
        registry.getEventPublisher()
            .onSuccess(event ->
                log.debug("Rate limiter permitted call for {}", event.getRateLimiterName()))
            .onFailure(event ->
                log.warn("Rate limiter rejected call for {}: {}",
                    event.getRateLimiterName(),
                    event.getEventType()
                ));

        return registry;
    }

    /**
     * Resilient Kafka producer wrapper
     */
    @Bean
    public ResilientKafkaProducer resilientKafkaProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            BulkheadRegistry bulkheadRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        
        return new ResilientKafkaProducer(
            kafkaTemplate,
            circuitBreakerRegistry,
            retryRegistry,
            bulkheadRegistry,
            rateLimiterRegistry
        );
    }

    /**
     * Resilient Kafka producer implementation with all fault tolerance patterns
     */
    public static class ResilientKafkaProducer {
        
        private final KafkaTemplate<String, Object> kafkaTemplate;
        private final CircuitBreakerRegistry circuitBreakerRegistry;
        private final RetryRegistry retryRegistry;
        private final BulkheadRegistry bulkheadRegistry;
        private final RateLimiterRegistry rateLimiterRegistry;

        public ResilientKafkaProducer(
                KafkaTemplate<String, Object> kafkaTemplate,
                CircuitBreakerRegistry circuitBreakerRegistry,
                RetryRegistry retryRegistry,
                BulkheadRegistry bulkheadRegistry,
                RateLimiterRegistry rateLimiterRegistry) {
            this.kafkaTemplate = kafkaTemplate;
            this.circuitBreakerRegistry = circuitBreakerRegistry;
            this.retryRegistry = retryRegistry;
            this.bulkheadRegistry = bulkheadRegistry;
            this.rateLimiterRegistry = rateLimiterRegistry;
        }

        /**
         * Send message with all resilience patterns applied
         */
        public <T> CompletableFuture<SendResult<String, Object>> sendResilient(
                String topic, String key, T payload, String resilienceProfile) {
            
            // Get resilience components for the profile
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafka-" + resilienceProfile);
            Retry retry = retryRegistry.retry("kafka-" + resilienceProfile + "-retry");
            Bulkhead bulkhead = bulkheadRegistry.bulkhead("kafka-" + resilienceProfile + "-bulkhead");
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("kafka-" + resilienceProfile);

            // Create the sending operation
            Supplier<CompletableFuture<SendResult<String, Object>>> sendOperation = () -> {
                log.debug("Sending message to topic {} with key {} using profile {}", topic, key, resilienceProfile);
                return kafkaTemplate.send(topic, key, payload);
            };

            // Apply all resilience patterns
            Supplier<CompletableFuture<SendResult<String, Object>>> decoratedOperation = 
                Bulkhead.decorateSupplier(bulkhead,
                    RateLimiter.decorateSupplier(rateLimiter,
                        CircuitBreaker.decorateSupplier(circuitBreaker,
                            Retry.decorateSupplier(retry, sendOperation))));

            try {
                return decoratedOperation.get();
            } catch (Exception e) {
                log.error("Failed to send message to topic {} with all resilience patterns: {}", topic, e.getMessage(), e);
                
                // Return failed future
                CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(e);
                return failedFuture;
            }
        }

        /**
         * Send critical event with maximum resilience
         */
        public <T> CompletableFuture<SendResult<String, Object>> sendCritical(String topic, String key, T payload) {
            return sendResilient(topic, key, payload, "critical");
        }

        /**
         * Send payment event with payment-specific resilience
         */
        public <T> CompletableFuture<SendResult<String, Object>> sendPayment(String topic, String key, T payload) {
            return sendResilient(topic, key, payload, "payment");
        }

        /**
         * Send fraud event with fraud-specific resilience
         */
        public <T> CompletableFuture<SendResult<String, Object>> sendFraud(String topic, String key, T payload) {
            return sendResilient(topic, key, payload, "fraud");
        }

        /**
         * Send high-volume event with optimized resilience
         */
        public <T> CompletableFuture<SendResult<String, Object>> sendHighVolume(String topic, String key, T payload) {
            return sendResilient(topic, key, payload, "high-volume");
        }

        /**
         * Get circuit breaker status for monitoring
         */
        public CircuitBreaker.State getCircuitBreakerState(String profile) {
            return circuitBreakerRegistry.circuitBreaker("kafka-" + profile).getState();
        }

        /**
         * Get metrics for monitoring
         */
        public java.util.Map<String, Object> getMetrics(String profile) {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("kafka-" + profile);
            Bulkhead bh = bulkheadRegistry.bulkhead("kafka-" + profile + "-bulkhead");
            
            return java.util.Map.of(
                "circuitBreakerState", cb.getState(),
                "failureRate", cb.getMetrics().getFailureRate(),
                "slowCallRate", cb.getMetrics().getSlowCallRate(),
                "availableConcurrentCalls", bh.getMetrics().getAvailableConcurrentCalls(),
                "maxAllowedConcurrentCalls", bh.getMetrics().getMaxAllowedConcurrentCalls()
            );
        }
    }
}