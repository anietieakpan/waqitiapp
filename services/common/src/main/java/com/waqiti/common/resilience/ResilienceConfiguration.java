package com.waqiti.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerProperties;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Comprehensive Resilience4j Configuration for External Service Calls
 * Provides circuit breakers, retries, timeouts, and bulkheads for all external integrations
 */
@Configuration
@EnableConfigurationProperties({CircuitBreakerProperties.class, RetryProperties.class})
@RequiredArgsConstructor
@Slf4j
public class ResilienceConfiguration {

    private final MeterRegistry meterRegistry;
    private final ResilienceMetricsService metricsService;

    /**
     * Circuit Breaker Registry Event Consumer with comprehensive monitoring
     */
    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
                log.info("Circuit breaker {} registered for service integration", circuitBreaker.getName());
                
                // Register comprehensive event listeners
                circuitBreaker.getEventPublisher()
                    .onStateTransition(event -> {
                        String fromState = event.getStateTransition().getFromState().toString();
                        String toState = event.getStateTransition().getToState().toString();
                        
                        log.warn("CIRCUIT BREAKER STATE CHANGE: {} {} -> {}", 
                            circuitBreaker.getName(), fromState, toState);
                        
                        // Update metrics
                        metricsService.recordStateTransition(circuitBreaker.getName(), fromState, toState);
                        
                        // Send alerts for critical state changes
                        if (toState.equals("OPEN")) {
                            metricsService.sendCircuitBreakerAlert(circuitBreaker.getName(), 
                                "Circuit breaker opened - service calls will be rejected");
                        } else if (toState.equals("CLOSED") && fromState.equals("OPEN")) {
                            metricsService.sendCircuitBreakerAlert(circuitBreaker.getName(), 
                                "Circuit breaker recovered - service calls resumed");
                        }
                    })
                    .onError(event -> {
                        log.error("Circuit breaker {} error: {}", 
                            circuitBreaker.getName(), event.getThrowable().getMessage());
                        metricsService.recordCircuitBreakerError(circuitBreaker.getName(), event.getThrowable());
                    })
                    .onCallNotPermitted(event -> {
                        log.warn("Circuit breaker {} rejected call - service unavailable", 
                            circuitBreaker.getName());
                        metricsService.incrementRejectedCalls(circuitBreaker.getName());
                    })
                    .onSuccess(event -> {
                        log.debug("Circuit breaker {} call succeeded in {}ms", 
                            circuitBreaker.getName(), event.getElapsedDuration().toMillis());
                        metricsService.recordSuccessfulCall(circuitBreaker.getName(), 
                            event.getElapsedDuration().toMillis());
                    })
                    .onSlowCallRateExceeded(event -> {
                        log.warn("Circuit breaker {} slow call rate exceeded: {}%", 
                            circuitBreaker.getName(), event.getSlowCallRate());
                        metricsService.recordSlowCallRateExceeded(circuitBreaker.getName(), event.getSlowCallRate());
                    })
                    .onFailureRateExceeded(event -> {
                        log.error("Circuit breaker {} failure rate exceeded: {}%", 
                            circuitBreaker.getName(), event.getFailureRate());
                        metricsService.recordFailureRateExceeded(circuitBreaker.getName(), event.getFailureRate());
                    });
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
                log.info("Circuit breaker {} deregistered", entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                log.info("Circuit breaker {} configuration updated", entryReplacedEvent.getNewEntry().getName());
            }
        };
    }

    /**
     * Retry Registry Event Consumer with detailed logging
     */
    @Bean
    public RegistryEventConsumer<Retry> retryEventConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Retry> entryAddedEvent) {
                Retry retry = entryAddedEvent.getAddedEntry();
                log.info("Retry policy {} registered", retry.getName());
                
                retry.getEventPublisher()
                    .onRetry(event -> {
                        log.debug("Retry {} attempt #{} for {}", 
                            retry.getName(), event.getNumberOfRetryAttempts(), 
                            event.getLastThrowable().getClass().getSimpleName());
                        metricsService.recordRetryAttempt(retry.getName(), event.getNumberOfRetryAttempts());
                    })
                    .onError(event -> {
                        log.error("Retry {} failed after {} attempts: {}", 
                            retry.getName(), event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage());
                        metricsService.recordRetryExhaustion(retry.getName(), 
                            event.getNumberOfRetryAttempts(), event.getLastThrowable());
                    })
                    .onSuccess(event -> {
                        if (event.getNumberOfRetryAttempts() > 0) {
                            log.info("Retry {} succeeded after {} attempts", 
                                retry.getName(), event.getNumberOfRetryAttempts());
                        }
                        metricsService.recordRetrySuccess(retry.getName(), event.getNumberOfRetryAttempts());
                    });
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<Retry> entryRemoveEvent) {
                log.info("Retry policy {} deregistered", entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<Retry> entryReplacedEvent) {
                log.info("Retry policy {} configuration updated", entryReplacedEvent.getNewEntry().getName());
            }
        };
    }

    /**
     * Enhanced Health Indicator for Circuit Breakers with detailed metrics
     */
    @Bean
    @Primary
    public HealthIndicator resilienceHealthIndicator(
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry,
            io.github.resilience4j.retry.RetryRegistry retryRegistry) {
        
        return () -> {
            Health.Builder healthBuilder = Health.up();
            
            // Circuit Breaker Health
            circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
                CircuitBreaker.State state = circuitBreaker.getState();
                CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
                String name = circuitBreaker.getName();
                
                // Circuit breaker status
                healthBuilder.withDetail(name + ".state", state)
                    .withDetail(name + ".failureRate", String.format("%.2f%%", metrics.getFailureRate()))
                    .withDetail(name + ".slowCallRate", String.format("%.2f%%", metrics.getSlowCallRate()))
                    .withDetail(name + ".bufferedCalls", metrics.getNumberOfBufferedCalls())
                    .withDetail(name + ".failedCalls", metrics.getNumberOfFailedCalls())
                    .withDetail(name + ".successfulCalls", metrics.getNumberOfSuccessfulCalls())
                    .withDetail(name + ".slowCalls", metrics.getNumberOfSlowCalls())
                    .withDetail(name + ".notPermittedCalls", metrics.getNumberOfNotPermittedCalls());
                
                // Health assessment
                switch (state) {
                    case OPEN:
                        healthBuilder.down().withDetail(name + ".issue", 
                            String.format("Circuit breaker OPEN - failure rate: %.2f%%", metrics.getFailureRate()));
                        break;
                    case HALF_OPEN:
                        healthBuilder.unknown().withDetail(name + ".status", 
                            "Circuit breaker HALF_OPEN - testing service recovery");
                        break;
                    case CLOSED:
                        if (metrics.getFailureRate() > 25.0f) {
                            healthBuilder.unknown().withDetail(name + ".warning", 
                                String.format("High failure rate: %.2f%%", metrics.getFailureRate()));
                        }
                        break;
                }
                
                // Record detailed metrics
                meterRegistry.gauge("circuit.breaker.state.numeric", 
                    io.micrometer.core.instrument.Tags.of("name", name, "state", state.toString()), 
                    state.ordinal());
                    
                meterRegistry.gauge("circuit.breaker.failure.rate", 
                    io.micrometer.core.instrument.Tags.of("name", name), 
                    metrics.getFailureRate());
                    
                meterRegistry.gauge("circuit.breaker.slow.call.rate", 
                    io.micrometer.core.instrument.Tags.of("name", name), 
                    metrics.getSlowCallRate());
            });
            
            // Retry Health
            retryRegistry.getAllRetries().forEach(retry -> {
                Retry.Metrics metrics = retry.getMetrics();
                String name = retry.getName();
                
                healthBuilder.withDetail(name + ".successfulCallsWithoutRetry", 
                    metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt())
                    .withDetail(name + ".successfulCallsWithRetry", 
                        metrics.getNumberOfSuccessfulCallsWithRetryAttempt())
                    .withDetail(name + ".failedCallsWithoutRetry", 
                        metrics.getNumberOfFailedCallsWithoutRetryAttempt())
                    .withDetail(name + ".failedCallsWithRetry", 
                        metrics.getNumberOfFailedCallsWithRetryAttempt());
            });
            
            return healthBuilder.build();
        };
    }

    /**
     * Custom metrics recorder for resilience patterns
     */
    @Bean
    public ResilienceMetricsService resilienceMetricsService() {
        return new ResilienceMetricsService(meterRegistry);
    }

    /**
     * AOP aspect for automatic circuit breaker application
     */
    @Bean
    public ExternalServiceCallAspect externalServiceCallAspect(
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry,
            org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate) {
        return new ExternalServiceCallAspect(circuitBreakerRegistry, kafkaTemplate);
    }
}