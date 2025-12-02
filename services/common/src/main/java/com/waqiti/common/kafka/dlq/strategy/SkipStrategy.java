package com.waqiti.common.kafka.dlq.strategy;

import com.waqiti.common.kafka.dlq.DlqStatus;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.kafka.dlq.repository.DlqRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Skip strategy for DLQ recovery.
 *
 * This strategy intentionally skips messages that are determined to be invalid
 * or not worth recovering. Used for:
 * - Validation errors (malformed data)
 * - Duplicate messages
 * - Obsolete events (too old)
 * - Test/debug messages
 * - Schema incompatibility
 * - Missing required fields
 *
 * IMPORTANT: Skipped messages are:
 * - Marked as SKIPPED status
 * - Archived for audit purposes (retained in database)
 * - Counted in metrics
 * - Never retried
 * - Logged with detailed reason
 *
 * COMPLIANCE:
 * Skipped messages are retained for audit trail and regulatory compliance.
 * They can be queried for root cause analysis and trend detection.
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SkipStrategy implements RecoveryStrategyHandler {

    private final DlqRecordRepository dlqRecordRepository;
    private final MeterRegistry meterRegistry;

    private static final int OBSOLETE_DAYS_THRESHOLD = 30;

    @Override
    public RecoveryResult recover(DlqRecordEntity dlqRecord) {
        String skipReason = determineSkipReason(dlqRecord);

        log.info("⏭️ Skipping DLQ message: messageId={}, topic={}, reason={}",
                dlqRecord.getMessageId(), dlqRecord.getTopic(), skipReason);

        try {
            // Update record status to PARKED (skipped)
            dlqRecord.setStatus(DlqStatus.PARKED);
            dlqRecord.setParkedAt(LocalDateTime.now());
            dlqRecord.setParkedReason("Message skipped: " + skipReason);

            dlqRecordRepository.save(dlqRecord);

            // Record metrics
            recordSkipMetric(dlqRecord, skipReason);

            log.info("✅ Message skipped successfully: messageId={}, reason={}",
                    dlqRecord.getMessageId(), skipReason);

            return RecoveryResult.success("Message skipped: " + skipReason);

        } catch (Exception e) {
            log.error("❌ Failed to skip message: messageId={}, error={}",
                    dlqRecord.getMessageId(), e.getMessage(), e);
            return RecoveryResult.retryLater("Skip operation failed: " + e.getMessage(), 60);
        }
    }

    /**
     * Determines the reason for skipping the message.
     */
    private String determineSkipReason(DlqRecordEntity dlqRecord) {
        String failureReason = dlqRecord.getLastFailureReason();
        String stackTrace = dlqRecord.getErrorStackTrace();

        // Validation errors
        if (failureReason != null && failureReason.contains("ValidationException")) {
            return "Validation error - invalid data format";
        }

        if (failureReason != null && failureReason.contains("validation failed")) {
            return "Validation error - " + extractValidationError(failureReason);
        }

        // Schema errors
        if (failureReason != null && (
            failureReason.contains("SerializationException") ||
            failureReason.contains("DeserializationException") ||
            failureReason.contains("JsonProcessingException"))) {
            return "Schema/serialization error - incompatible message format";
        }

        // Null/missing required fields
        if (failureReason != null && failureReason.contains("cannot be null")) {
            return "Missing required field - " + extractFieldName(failureReason);
        }

        // Duplicate detection
        if (failureReason != null && failureReason.contains("duplicate")) {
            return "Duplicate message - already processed";
        }

        // Obsolete events
        if (isObsoleteEvent(dlqRecord)) {
            return "Obsolete event - too old to process (>" + OBSOLETE_DAYS_THRESHOLD + " days)";
        }

        // Test/debug messages
        if (isTestMessage(dlqRecord)) {
            return "Test/debug message - not for production processing";
        }

        // Max retries exceeded
        if (dlqRecord.getRetryCount() >= 10) {
            return "Max retries exceeded - unrecoverable error";
        }

        // Default
        return "Unrecoverable error - permanent failure";
    }

    /**
     * Checks if event is obsolete (too old).
     */
    private boolean isObsoleteEvent(DlqRecordEntity dlqRecord) {
        LocalDateTime thresholdDate = LocalDateTime.now().minusDays(OBSOLETE_DAYS_THRESHOLD);
        return dlqRecord.getCreatedAt().isBefore(thresholdDate);
    }

    /**
     * Checks if message is a test message.
     */
    private boolean isTestMessage(DlqRecordEntity dlqRecord) {
        String payload = dlqRecord.getMessageValue();
        if (payload == null) return false;

        String payloadLower = payload.toLowerCase();
        String topicLower = dlqRecord.getTopic().toLowerCase();

        return payloadLower.contains("\"test\":true") ||
               payloadLower.contains("\"environment\":\"test\"") ||
               payloadLower.contains("\"debug\":true") ||
               topicLower.contains("test") ||
               topicLower.contains("debug") ||
               topicLower.contains("sandbox");
    }

    /**
     * Extracts validation error from error message.
     */
    private String extractValidationError(String failureReason) {
        if (failureReason.contains(":")) {
            String[] parts = failureReason.split(":", 2);
            if (parts.length > 1) {
                return parts[1].trim();
            }
        }
        return failureReason;
    }

    /**
     * Extracts field name from "cannot be null" error.
     */
    private String extractFieldName(String failureReason) {
        String[] parts = failureReason.split(" ");
        if (parts.length > 0) {
            return parts[0];
        }
        return "unknown field";
    }

    /**
     * Records skip metrics for monitoring.
     */
    private void recordSkipMetric(DlqRecordEntity dlqRecord, String reason) {
        Counter.builder("dlq.message.skipped")
            .tag("topic", dlqRecord.getTopic())
            .tag("reason", categorizeReason(reason))
            .tag("service", dlqRecord.getServiceName())
            .description("DLQ messages skipped")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Categorizes skip reason for metrics aggregation.
     */
    private String categorizeReason(String reason) {
        if (reason.contains("Validation")) return "validation";
        if (reason.contains("Schema")) return "schema";
        if (reason.contains("Duplicate")) return "duplicate";
        if (reason.contains("Obsolete")) return "obsolete";
        if (reason.contains("Test")) return "test";
        if (reason.contains("Max retries")) return "max_retries";
        return "other";
    }

    @Override
    public String getStrategyName() {
        return "SKIP";
    }

    @Override
    public boolean canHandle(DlqRecordEntity dlqRecord) {
        String failureReason = dlqRecord.getLastFailureReason();

        // Can handle validation errors
        if (failureReason != null) {
            if (failureReason.contains("ValidationException") ||
                failureReason.contains("IllegalArgumentException") ||
                failureReason.contains("SerializationException") ||
                failureReason.contains("JsonProcessingException") ||
                failureReason.contains("validation failed") ||
                failureReason.contains("cannot be null") ||
                failureReason.contains("duplicate")) {
                return true;
            }
        }

        // Skip obsolete events
        if (isObsoleteEvent(dlqRecord)) {
            return true;
        }

        // Skip test messages
        if (isTestMessage(dlqRecord)) {
            return true;
        }

        // Skip messages that have exceeded max retries
        if (dlqRecord.getRetryCount() >= 10) {
            return true;
        }

        return false;
    }
}
