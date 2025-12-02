package com.waqiti.common.kafka.dlq;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Metrics service for DLQ recovery monitoring.
 * Exports metrics to Prometheus for Grafana dashboards.
 */
@Slf4j
@Service
public class DLQMetricsService {

    private final MeterRegistry meterRegistry;
    private final DLQRecoveryRepository repository;
    private final Map<String, Counter> retrySuccessCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> retryFailureCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> retryAttemptCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> manualInterventionCounters = new ConcurrentHashMap<>();

    public DLQMetricsService(MeterRegistry meterRegistry, DLQRecoveryRepository repository) {
        this.meterRegistry = meterRegistry;
        this.repository = repository;
    }

    public void incrementRetrySuccess(String topic) {
        getRetrySuccessCounter(topic).increment();
    }

    public void incrementRetryFailure(String topic) {
        getRetryFailureCounter(topic).increment();
    }

    public void incrementRetryAttempt(String topic) {
        getRetryAttemptCounter(topic).increment();
    }

    public void incrementRetryError(String topic) {
        getRetryFailureCounter(topic).increment();
    }

    public void incrementRecoverySuccess(String topic) {
        getRetrySuccessCounter(topic).increment();
    }

    public void incrementManualInterventionRequired(String topic) {
        getManualInterventionCounter(topic).increment();
    }

    public DLQRecoveryStats getStats(String topic) {
        long recovered = repository.countRecoveredByTopic(topic);
        long manualIntervention = repository.countManualInterventionByTopic(topic);

        return DLQRecoveryStats.builder()
            .topic(topic)
            .recoveredCount(recovered)
            .manualInterventionCount(manualIntervention)
            .build();
    }

    private Counter getRetrySuccessCounter(String topic) {
        return retrySuccessCounters.computeIfAbsent(topic, t ->
            Counter.builder("dlq.retry.success")
                .tag("topic", t)
                .description("Successful DLQ message recoveries")
                .register(meterRegistry)
        );
    }

    private Counter getRetryFailureCounter(String topic) {
        return retryFailureCounters.computeIfAbsent(topic, t ->
            Counter.builder("dlq.retry.failure")
                .tag("topic", t)
                .description("Failed DLQ recovery attempts")
                .register(meterRegistry)
        );
    }

    private Counter getRetryAttemptCounter(String topic) {
        return retryAttemptCounters.computeIfAbsent(topic, t ->
            Counter.builder("dlq.retry.attempt")
                .tag("topic", t)
                .description("Total DLQ retry attempts")
                .register(meterRegistry)
        );
    }

    private Counter getManualInterventionCounter(String topic) {
        return manualInterventionCounters.computeIfAbsent(topic, t ->
            Counter.builder("dlq.manual.intervention")
                .tag("topic", t)
                .description("Messages requiring manual intervention")
                .register(meterRegistry)
        );
    }
}
