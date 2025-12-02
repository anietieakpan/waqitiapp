package com.waqiti.common.resilience;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking and reporting resilience pattern metrics
 * Integrates with monitoring systems for operational visibility
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResilienceMetricsService {

    private final MeterRegistry meterRegistry;
    
    // Metric tracking
    private final Map<String, Counter> stateTransitionCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> callDurationTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    
    /**
     * Record circuit breaker state transition
     */
    public void recordStateTransition(String circuitBreakerName, String fromState, String toState) {
        String metricName = String.format("circuit.breaker.transition.%s.to.%s", 
            fromState.toLowerCase(), toState.toLowerCase());
            
        Counter counter = stateTransitionCounters.computeIfAbsent(
            circuitBreakerName + "." + metricName,
            key -> Counter.builder(metricName)
                .tag("circuit_breaker", circuitBreakerName)
                .tag("from_state", fromState)
                .tag("to_state", toState)
                .description("Circuit breaker state transitions")
                .register(meterRegistry)
        );
        
        counter.increment();
        
        // Log critical transitions
        if (toState.equals("OPEN") || (toState.equals("CLOSED") && fromState.equals("OPEN"))) {
            log.warn("RESILIENCE METRIC: Circuit breaker {} transitioned {} -> {}", 
                circuitBreakerName, fromState, toState);
        }
    }

    /**
     * Record circuit breaker error
     */
    public void recordCircuitBreakerError(String circuitBreakerName, Throwable error) {
        String errorType = error.getClass().getSimpleName();
        
        Counter counter = errorCounters.computeIfAbsent(
            circuitBreakerName + "." + errorType,
            key -> Counter.builder("circuit.breaker.errors")
                .tag("circuit_breaker", circuitBreakerName)
                .tag("error_type", errorType)
                .description("Circuit breaker errors by type")
                .register(meterRegistry)
        );
        
        counter.increment();
        
        // Track error patterns for analysis
        meterRegistry.counter("circuit.breaker.total.errors",
            "circuit_breaker", circuitBreakerName).increment();
    }

    /**
     * Increment rejected calls counter
     */
    public void incrementRejectedCalls(String circuitBreakerName) {
        meterRegistry.counter("circuit.breaker.rejected.calls",
            "circuit_breaker", circuitBreakerName).increment();
            
        // Log high rejection rates
        Counter rejectionCounter = meterRegistry.find("circuit.breaker.rejected.calls")
            .tag("circuit_breaker", circuitBreakerName)
            .counter();
            
        if (rejectionCounter != null && rejectionCounter.count() % 100 == 0) {
            log.warn("Circuit breaker {} has rejected {} calls", 
                circuitBreakerName, rejectionCounter.count());
        }
    }

    /**
     * Record successful call with timing
     */
    public void recordSuccessfulCall(String circuitBreakerName, long durationMs) {
        Timer timer = callDurationTimers.computeIfAbsent(
            circuitBreakerName,
            key -> Timer.builder("circuit.breaker.call.duration")
                .tag("circuit_breaker", circuitBreakerName)
                .tag("result", "success")
                .description("Successful call durations")
                .register(meterRegistry)
        );
        
        timer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        meterRegistry.counter("circuit.breaker.successful.calls",
            "circuit_breaker", circuitBreakerName).increment();
    }

    /**
     * Record slow call rate exceeded
     */
    public void recordSlowCallRateExceeded(String circuitBreakerName, float slowCallRate) {
        meterRegistry.counter("circuit.breaker.slow.call.rate.exceeded",
            "circuit_breaker", circuitBreakerName).increment();
            
        meterRegistry.gauge("circuit.breaker.current.slow.call.rate",
            io.micrometer.core.instrument.Tags.of("circuit_breaker", circuitBreakerName),
            slowCallRate);
            
        log.warn("Circuit breaker {} slow call rate exceeded: {}%", circuitBreakerName, slowCallRate);
    }

    /**
     * Record failure rate exceeded
     */
    public void recordFailureRateExceeded(String circuitBreakerName, float failureRate) {
        meterRegistry.counter("circuit.breaker.failure.rate.exceeded",
            "circuit_breaker", circuitBreakerName).increment();
            
        meterRegistry.gauge("circuit.breaker.current.failure.rate",
            io.micrometer.core.instrument.Tags.of("circuit_breaker", circuitBreakerName),
            failureRate);
            
        log.error("CRITICAL: Circuit breaker {} failure rate exceeded: {}%", circuitBreakerName, failureRate);
    }

    /**
     * Record retry attempt
     */
    public void recordRetryAttempt(String retryName, int attemptNumber) {
        meterRegistry.counter("retry.attempts",
            "retry_policy", retryName,
            "attempt_number", String.valueOf(attemptNumber)).increment();
            
        // Log excessive retry attempts
        if (attemptNumber >= 3) {
            log.warn("Retry policy {} on attempt #{} - service may be degraded", 
                retryName, attemptNumber);
        }
    }

    /**
     * Record retry exhaustion
     */
    public void recordRetryExhaustion(String retryName, int totalAttempts, Throwable lastError) {
        meterRegistry.counter("retry.exhausted",
            "retry_policy", retryName,
            "total_attempts", String.valueOf(totalAttempts),
            "error_type", lastError.getClass().getSimpleName()).increment();
            
        log.error("RETRY EXHAUSTED: {} failed after {} attempts - {}", 
            retryName, totalAttempts, lastError.getMessage());
    }

    /**
     * Record retry success
     */
    public void recordRetrySuccess(String retryName, int totalAttempts) {
        meterRegistry.counter("retry.success",
            "retry_policy", retryName,
            "attempts_needed", String.valueOf(totalAttempts)).increment();
            
        if (totalAttempts > 0) {
            log.info("Retry policy {} succeeded after {} attempts", retryName, totalAttempts);
        }
    }

    /**
     * Send circuit breaker alert
     */
    public void sendCircuitBreakerAlert(String circuitBreakerName, String message) {
        try {
            // Increment alert counter
            meterRegistry.counter("circuit.breaker.alerts",
                "circuit_breaker", circuitBreakerName,
                "timestamp", String.valueOf(System.currentTimeMillis())).increment();
            
            // Create alert payload
            Map<String, Object> alertData = Map.of(
                "service", "resilience-monitoring",
                "alertType", "CIRCUIT_BREAKER",
                "severity", "HIGH",
                "circuitBreaker", circuitBreakerName,
                "message", message,
                "timestamp", LocalDateTime.now().toString(),
                "environment", System.getProperty("spring.profiles.active", "unknown")
            );
            
            // Log structured alert for external monitoring systems
            log.error("RESILIENCE ALERT: {}", alertData);
            
            // Send to monitoring webhook if configured
            sendToMonitoringWebhook(alertData);
            
        } catch (Exception e) {
            log.error("Failed to send circuit breaker alert: {}", e.getMessage());
        }
    }

    /**
     * Get service health summary
     */
    public ServiceHealthSummary getHealthSummary() {
        ServiceHealthSummary summary = new ServiceHealthSummary();
        
        // Calculate total metrics
        double totalRejectedCalls = meterRegistry.find("circuit.breaker.rejected.calls")
            .counters().stream()
            .mapToDouble(Counter::count)
            .sum();
            
        double totalSuccessfulCalls = meterRegistry.find("circuit.breaker.successful.calls")
            .counters().stream()
            .mapToDouble(Counter::count)
            .sum();
            
        double totalErrors = meterRegistry.find("circuit.breaker.total.errors")
            .counters().stream()
            .mapToDouble(Counter::count)
            .sum();
        
        summary.setTotalCalls(totalSuccessfulCalls + totalErrors + totalRejectedCalls);
        summary.setSuccessfulCalls(totalSuccessfulCalls);
        summary.setRejectedCalls(totalRejectedCalls);
        summary.setErrorCalls(totalErrors);
        
        if (summary.getTotalCalls() > 0) {
            summary.setSuccessRate((totalSuccessfulCalls / summary.getTotalCalls()) * 100);
            summary.setErrorRate((totalErrors / summary.getTotalCalls()) * 100);
        }
        
        summary.setTimestamp(LocalDateTime.now());
        return summary;
    }

    /**
     * Reset metrics (for testing)
     */
    public void resetMetrics() {
        stateTransitionCounters.clear();
        callDurationTimers.clear();
        errorCounters.clear();
        log.info("Resilience metrics reset");
    }

    private void sendToMonitoringWebhook(Map<String, Object> alertData) {
        // Implementation would send to external monitoring system
        // For now, just log the structured data
        log.info("MONITORING WEBHOOK: {}", alertData);
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ServiceHealthSummary {
        private double totalCalls;
        private double successfulCalls;
        private double rejectedCalls;
        private double errorCalls;
        private double successRate;
        private double errorRate;
        private LocalDateTime timestamp;
    }
}