package com.waqiti.corebanking.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerProperties;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Resilience4j Configuration
 * Configures circuit breakers, retries, and other resilience patterns
 */
@Configuration
@EnableConfigurationProperties({CircuitBreakerProperties.class, RetryProperties.class})
@Slf4j
public class ResilienceConfig {

    /**
     * Circuit Breaker Registry Event Consumer
     */
    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
                log.info("Circuit breaker {} added", circuitBreaker.getName());
                
                circuitBreaker.getEventPublisher()
                    .onStateTransition(event -> 
                        log.info("Circuit breaker {} state transition: {} -> {}", 
                            circuitBreaker.getName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState()))
                    .onError(event -> 
                        log.error("Circuit breaker {} error: {}", 
                            circuitBreaker.getName(),
                            event.getThrowable().getMessage()))
                    .onCallNotPermitted(event -> 
                        log.warn("Circuit breaker {} call not permitted", 
                            circuitBreaker.getName()))
                    .onSuccess(event -> 
                        log.debug("Circuit breaker {} call succeeded", 
                            circuitBreaker.getName()))
                    .onSlowCallRateExceeded(event -> 
                        log.warn("Circuit breaker {} slow call rate exceeded", 
                            circuitBreaker.getName()))
                    .onFailureRateExceeded(event -> 
                        log.warn("Circuit breaker {} failure rate exceeded", 
                            circuitBreaker.getName()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
                log.info("Circuit breaker {} removed", entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                log.info("Circuit breaker {} replaced", entryReplacedEvent.getNewEntry().getName());
            }
        };
    }

    /**
     * Retry Registry Event Consumer
     */
    @Bean
    public RegistryEventConsumer<Retry> retryEventConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Retry> entryAddedEvent) {
                Retry retry = entryAddedEvent.getAddedEntry();
                log.info("Retry {} added", retry.getName());
                
                retry.getEventPublisher()
                    .onRetry(event -> 
                        log.debug("Retry {} attempt #{}", 
                            retry.getName(),
                            event.getNumberOfRetryAttempts()))
                    .onError(event -> 
                        log.error("Retry {} failed after {} attempts: {}", 
                            retry.getName(),
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage()))
                    .onSuccess(event -> 
                        log.debug("Retry {} succeeded after {} attempts", 
                            retry.getName(),
                            event.getNumberOfRetryAttempts()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<Retry> entryRemoveEvent) {
                log.info("Retry {} removed", entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<Retry> entryReplacedEvent) {
                log.info("Retry {} replaced", entryReplacedEvent.getNewEntry().getName());
            }
        };
    }

    /**
     * Custom Health Indicator for Circuit Breakers
     */
    @Bean
    @Primary
    public HealthIndicator circuitBreakerHealthIndicator(
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry) {
        
        return () -> {
            Health.Builder healthBuilder = Health.up();
            
            circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
                CircuitBreaker.State state = circuitBreaker.getState();
                CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
                
                String name = circuitBreaker.getName();
                
                // Add circuit breaker details
                healthBuilder.withDetail(name + ".state", state)
                    .withDetail(name + ".failureRate", metrics.getFailureRate())
                    .withDetail(name + ".slowCallRate", metrics.getSlowCallRate())
                    .withDetail(name + ".bufferedCalls", metrics.getNumberOfBufferedCalls())
                    .withDetail(name + ".failedCalls", metrics.getNumberOfFailedCalls())
                    .withDetail(name + ".successfulCalls", metrics.getNumberOfSuccessfulCalls());
                
                // Update health status based on circuit breaker state
                if (state == CircuitBreaker.State.OPEN) {
                    healthBuilder.down().withDetail(name + ".reason", "Circuit breaker is OPEN");
                } else if (state == CircuitBreaker.State.HALF_OPEN) {
                    healthBuilder.unknown().withDetail(name + ".reason", "Circuit breaker is HALF_OPEN");
                }
                
                // Record metrics
                meterRegistry.gauge("circuit.breaker.state", 
                    io.micrometer.core.instrument.Tags.of("name", name, "state", state.toString()), 
                    state.ordinal());
            });
            
            return healthBuilder.build();
        };
    }
}