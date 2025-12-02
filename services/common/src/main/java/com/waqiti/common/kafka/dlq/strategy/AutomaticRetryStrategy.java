package com.waqiti.common.kafka.dlq.strategy;

import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Automatic retry strategy with exponential backoff.
 *
 * This strategy retries messages automatically with increasing delays.
 * Suitable for transient errors (network timeouts, temporary service unavailability, etc.).
 *
 * Backoff schedule:
 * - Retry 1: 30 seconds
 * - Retry 2: 2 minutes (120s)
 * - Retry 3: 5 minutes (300s)
 * - Retry 4: 15 minutes (900s)
 * - Retry 5: 1 hour (3600s)
 * - Retry 6+: 4 hours (14400s)
 *
 * PRODUCTION BEHAVIOR:
 * - Max retries: 10 attempts
 * - Idempotent: Safe to call multiple times
 * - Metrics: Emits retry success/failure counters
 * - Logging: Comprehensive debug/info/error logs
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AutomaticRetryStrategy implements RecoveryStrategyHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private static final int[] BACKOFF_SECONDS = {30, 120, 300, 900, 3600, 14400};
    private static final int MAX_RETRIES = 10;

    @Override
    public RecoveryResult recover(DlqRecordEntity dlqRecord) {
        log.info("🔄 Attempting automatic retry for DLQ record: id={}, topic={}, retryCount={}",
                dlqRecord.getId(), dlqRecord.getTopic(), dlqRecord.getRetryCount());

        try {
            // Check if max retries exceeded
            if (dlqRecord.getRetryCount() >= MAX_RETRIES) {
                log.warn("⚠️ Max retries exceeded for DLQ record: id={}, retryCount={}",
                        dlqRecord.getId(), dlqRecord.getRetryCount());

                recordMetric("dlq.retry.max_exceeded", dlqRecord.getTopic());

                return RecoveryResult.permanentFailure(
                    "Max retries (" + MAX_RETRIES + ") exceeded");
            }

            // Retry by republishing to original topic
            kafkaTemplate.send(
                dlqRecord.getTopic(),
                dlqRecord.getMessageKey(),
                dlqRecord.getMessageValue()
            ).get(); // Wait for confirmation

            log.info("✅ Message successfully retried: id={}, topic={}",
                    dlqRecord.getId(), dlqRecord.getTopic());

            recordMetric("dlq.retry.success", dlqRecord.getTopic());

            return RecoveryResult.success("Message successfully republished to original topic");

        } catch (Exception e) {
            log.error("❌ Retry failed for DLQ record: id={}, error={}",
                    dlqRecord.getId(), e.getMessage(), e);

            // Calculate next retry delay
            int retryIndex = Math.min(dlqRecord.getRetryCount(), BACKOFF_SECONDS.length - 1);
            int nextDelay = BACKOFF_SECONDS[retryIndex];

            recordMetric("dlq.retry.failure", dlqRecord.getTopic());

            return RecoveryResult.retryLater(
                "Retry failed: " + e.getMessage(),
                nextDelay
            );
        }
    }

    @Override
    public String getStrategyName() {
        return "AUTOMATIC_RETRY";
    }

    @Override
    public boolean canHandle(DlqRecordEntity dlqRecord) {
        // Can handle any transient error that hasn't exceeded max retries
        if (dlqRecord.getRetryCount() >= MAX_RETRIES) {
            return false;
        }

        // Check if this is a transient error (not validation/schema/permanent errors)
        String failureReason = dlqRecord.getLastFailureReason();
        if (failureReason == null) {
            return true; // Unknown error, allow retry
        }

        // Don't retry validation or schema errors
        if (failureReason.contains("ValidationException") ||
            failureReason.contains("SerializationException") ||
            failureReason.contains("JsonProcessingException") ||
            failureReason.contains("validation failed")) {
            return false;
        }

        return true; // Default to allowing retry for transient errors
    }

    /**
     * Records metrics for monitoring.
     */
    private void recordMetric(String metricName, String topic) {
        Counter.builder(metricName)
            .tag("topic", topic)
            .tag("strategy", "automatic_retry")
            .description("DLQ automatic retry strategy metrics")
            .register(meterRegistry)
            .increment();
    }
}
