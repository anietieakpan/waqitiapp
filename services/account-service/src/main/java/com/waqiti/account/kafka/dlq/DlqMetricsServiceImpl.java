package com.waqiti.account.kafka.dlq;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of DLQ metrics service
 *
 * <p>Provides comprehensive DLQ monitoring metrics for:</p>
 * <ul>
 *   <li>Grafana dashboards</li>
 *   <li>Prometheus scraping</li>
 *   <li>CloudWatch metrics</li>
 *   <li>Alerting thresholds</li>
 * </ul>
 *
 * <h3>Metrics Provided:</h3>
 * <pre>
 * dlq.messages.discarded         - Counter by handler, topic, reason
 * dlq.recovery.success            - Counter by handler, retry attempt
 * dlq.recovery.success.duration   - Timer by handler
 * dlq.recovery.failure            - Counter by handler, retry attempt, reason
 * dlq.sla.breach                  - Counter by handler, priority
 * dlq.sla.breach.duration         - Distribution summary
 * dlq.manual.intervention         - Counter by handler, action
 * dlq.manual.intervention.duration - Timer by handler, action
 * </pre>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class DlqMetricsServiceImpl implements DlqMetricsService {

    @Autowired
    private MeterRegistry meterRegistry;

    @Override
    public void recordDiscardedMessage(String handlerName, String topic, String reason) {
        try {
            Counter.builder("dlq.messages.discarded")
                .tag("handler", handlerName)
                .tag("topic", topic)
                .tag("reason", sanitizeReason(reason))
                .description("Messages discarded from DLQ")
                .register(meterRegistry)
                .increment();

            log.debug("Recorded discarded message metric - handler={}, topic={}, reason={}",
                handlerName, topic, reason);

        } catch (Exception e) {
            log.error("Failed to record discarded message metric", e);
        }
    }

    @Override
    public void recordRecoverySuccess(String handlerName, int retryAttempt, long duration) {
        try {
            // Increment success counter
            Counter.builder("dlq.recovery.success")
                .tag("handler", handlerName)
                .tag("retry_attempt", String.valueOf(retryAttempt))
                .description("Successful DLQ message recoveries")
                .register(meterRegistry)
                .increment();

            // Record recovery duration
            Timer.builder("dlq.recovery.success.duration")
                .tag("handler", handlerName)
                .tag("retry_attempt", String.valueOf(retryAttempt))
                .description("Duration of successful recoveries")
                .register(meterRegistry)
                .record(duration, TimeUnit.MILLISECONDS);

            // Track recovery attempt distribution
            DistributionSummary.builder("dlq.recovery.attempts")
                .tag("handler", handlerName)
                .description("Distribution of recovery attempts")
                .register(meterRegistry)
                .record(retryAttempt);

            log.debug("Recorded recovery success metric - handler={}, attempt={}, duration={}ms",
                handlerName, retryAttempt, duration);

        } catch (Exception e) {
            log.error("Failed to record recovery success metric", e);
        }
    }

    @Override
    public void recordRecoveryFailure(String handlerName, int retryAttempt, String reason) {
        try {
            Counter.builder("dlq.recovery.failure")
                .tag("handler", handlerName)
                .tag("retry_attempt", String.valueOf(retryAttempt))
                .tag("reason", sanitizeReason(reason))
                .description("Failed DLQ message recoveries")
                .register(meterRegistry)
                .increment();

            log.debug("Recorded recovery failure metric - handler={}, attempt={}, reason={}",
                handlerName, retryAttempt, reason);

        } catch (Exception e) {
            log.error("Failed to record recovery failure metric", e);
        }
    }

    @Override
    public void recordSlaBreach(String handlerName, String priority, long breachDuration) {
        try {
            // Increment SLA breach counter
            Counter.builder("dlq.sla.breach")
                .tag("handler", handlerName)
                .tag("priority", priority)
                .description("Manual review SLA breaches")
                .register(meterRegistry)
                .increment();

            // Record breach duration
            DistributionSummary.builder("dlq.sla.breach.duration")
                .tag("handler", handlerName)
                .tag("priority", priority)
                .description("SLA breach duration in milliseconds")
                .baseUnit("milliseconds")
                .register(meterRegistry)
                .record(breachDuration);

            log.warn("Recorded SLA breach metric - handler={}, priority={}, breach={}ms",
                handlerName, priority, breachDuration);

        } catch (Exception e) {
            log.error("Failed to record SLA breach metric", e);
        }
    }

    @Override
    public void recordManualIntervention(String handlerName, String action, long duration) {
        try {
            // Increment manual intervention counter
            Counter.builder("dlq.manual.intervention")
                .tag("handler", handlerName)
                .tag("action", action)
                .description("Manual interventions on DLQ messages")
                .register(meterRegistry)
                .increment();

            // Record intervention duration
            Timer.builder("dlq.manual.intervention.duration")
                .tag("handler", handlerName)
                .tag("action", action)
                .description("Time to manual resolution")
                .register(meterRegistry)
                .record(duration, TimeUnit.MILLISECONDS);

            log.debug("Recorded manual intervention metric - handler={}, action={}, duration={}ms",
                handlerName, action, duration);

        } catch (Exception e) {
            log.error("Failed to record manual intervention metric", e);
        }
    }

    /**
     * Record queue depth metrics (called by scheduled job)
     */
    public void recordQueueDepth(String queueType, long count) {
        try {
            Gauge.builder("dlq.queue.depth", () -> count)
                .tag("queue", queueType)
                .description("Current DLQ queue depth")
                .register(meterRegistry);

            log.debug("Recorded queue depth metric - queue={}, depth={}", queueType, count);

        } catch (Exception e) {
            log.error("Failed to record queue depth metric", e);
        }
    }

    /**
     * Record processing lag metrics
     */
    public void recordProcessingLag(String handlerName, long lagSeconds) {
        try {
            Gauge.builder("dlq.processing.lag", () -> lagSeconds)
                .tag("handler", handlerName)
                .description("Processing lag in seconds")
                .baseUnit("seconds")
                .register(meterRegistry);

            log.debug("Recorded processing lag metric - handler={}, lag={}s",
                handlerName, lagSeconds);

        } catch (Exception e) {
            log.error("Failed to record processing lag metric", e);
        }
    }

    /**
     * Record error rate metrics
     */
    public void recordErrorRate(String handlerName, String errorType, double rate) {
        try {
            Gauge.builder("dlq.error.rate", () -> rate)
                .tag("handler", handlerName)
                .tag("error_type", errorType)
                .description("DLQ error rate per minute")
                .baseUnit("errors/minute")
                .register(meterRegistry);

            log.debug("Recorded error rate metric - handler={}, type={}, rate={}",
                handlerName, errorType, rate);

        } catch (Exception e) {
            log.error("Failed to record error rate metric", e);
        }
    }

    /**
     * Sanitize reason string for metric tag (limit length, remove special chars)
     */
    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }

        // Limit length
        String sanitized = reason.length() > 50 ? reason.substring(0, 50) : reason;

        // Replace special characters
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_-]", "_");

        // Convert to lowercase
        return sanitized.toLowerCase();
    }
}
