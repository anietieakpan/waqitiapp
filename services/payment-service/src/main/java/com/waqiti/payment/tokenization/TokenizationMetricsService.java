package com.waqiti.payment.tokenization;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * Metrics service for tokenization operations
 * Provides real-time monitoring of tokenization performance and health
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenizationMetricsService {

    private final MeterRegistry meterRegistry;

    private Counter tokenizationSuccessCounter;
    private Counter tokenizationFailureCounter;
    private Counter detokenizationSuccessCounter;
    private Counter detokenizationFailureCounter;
    private Counter circuitBreakerActivationCounter;
    private Timer tokenizationTimer;
    private Timer detokenizationTimer;

    @PostConstruct
    public void initMetrics() {
        tokenizationSuccessCounter = Counter.builder("tokenization.success")
            .description("Number of successful tokenization operations")
            .tag("service", "payment")
            .register(meterRegistry);

        tokenizationFailureCounter = Counter.builder("tokenization.failure")
            .description("Number of failed tokenization operations")
            .tag("service", "payment")
            .register(meterRegistry);

        detokenizationSuccessCounter = Counter.builder("detokenization.success")
            .description("Number of successful detokenization operations")
            .tag("service", "payment")
            .register(meterRegistry);

        detokenizationFailureCounter = Counter.builder("detokenization.failure")
            .description("Number of failed detokenization operations")
            .tag("service", "payment")
            .register(meterRegistry);

        circuitBreakerActivationCounter = Counter.builder("tokenization.circuit_breaker.activation")
            .description("Number of circuit breaker activations")
            .tag("service", "payment")
            .register(meterRegistry);

        tokenizationTimer = Timer.builder("tokenization.duration")
            .description("Duration of tokenization operations")
            .tag("service", "payment")
            .register(meterRegistry);

        detokenizationTimer = Timer.builder("detokenization.duration")
            .description("Duration of detokenization operations")
            .tag("service", "payment")
            .register(meterRegistry);

        log.info("Tokenization metrics initialized");
    }

    public void recordTokenization(String dataType, long durationMs, boolean success) {
        if (success) {
            tokenizationSuccessCounter.increment();
        } else {
            tokenizationFailureCounter.increment();
        }

        tokenizationTimer.record(durationMs, TimeUnit.MILLISECONDS);

        meterRegistry.counter("tokenization.by_type",
            "dataType", dataType,
            "success", String.valueOf(success)).increment();

        log.debug("Tokenization metrics recorded: dataType={}, durationMs={}, success={}",
            dataType, durationMs, success);
    }

    public void recordDetokenization(long durationMs, boolean success) {
        if (success) {
            detokenizationSuccessCounter.increment();
        } else {
            detokenizationFailureCounter.increment();
        }

        detokenizationTimer.record(durationMs, TimeUnit.MILLISECONDS);

        log.debug("Detokenization metrics recorded: durationMs={}, success={}", durationMs, success);
    }

    public void recordCircuitBreakerActivation(String operation) {
        circuitBreakerActivationCounter.increment();

        meterRegistry.counter("tokenization.circuit_breaker.by_operation",
            "operation", operation).increment();

        log.warn("Circuit breaker activation recorded: operation={}", operation);
    }
}
