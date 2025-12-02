package com.waqiti.dlq.strategy;

import com.waqiti.dlq.model.DLQMessage;
import com.waqiti.dlq.repository.DLQMessageRepository;
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
 * - Obsolete events
 * - Test/debug messages
 * - Schema incompatibility
 *
 * IMPORTANT: Skipped messages are:
 * - Marked as SKIPPED status
 * - Archived for audit purposes
 * - Counted in metrics
 * - Never retried
 * - Logged with reason
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SkipStrategy implements RecoveryStrategyHandler {

    private final DLQMessageRepository dlqMessageRepository;
    private final MeterRegistry meterRegistry;

    @Override
    public RecoveryResult recover(DLQMessage message) {
        log.info("Skipping DLQ message: messageId={}, topic={}, reason={}",
                message.getId(), message.getOriginalTopic(), determineSkipReason(message));

        try {
            // Determine skip reason
            String skipReason = determineSkipReason(message);

            // Update message status
            message.setStatus(DLQMessage.DLQStatus.SKIPPED);
            message.setRecoveryNotes("Message skipped: " + skipReason);
            message.setRecoveredAt(LocalDateTime.now());

            dlqMessageRepository.save(message);

            // Record metrics
            recordSkipMetric(message, skipReason);

            log.info("✅ Message skipped successfully: messageId={}, reason={}",
                    message.getId(), skipReason);

            return RecoveryResult.success("Message skipped: " + skipReason);

        } catch (Exception e) {
            log.error("❌ Failed to skip message: messageId={}, error={}",
                    message.getId(), e.getMessage(), e);
            return RecoveryResult.retryLater("Skip operation failed: " + e.getMessage(), 60);
        }
    }

    /**
     * Determines the reason for skipping the message.
     */
    private String determineSkipReason(DLQMessage message) {
        String errorClass = message.getErrorClass();
        String errorMessage = message.getErrorMessage();

        // Validation errors
        if (errorClass != null && errorClass.contains("ValidationException")) {
            return "Validation error - invalid data format";
        }

        if (errorMessage != null && errorMessage.contains("validation failed")) {
            return "Validation error - " + extractValidationError(errorMessage);
        }

        // Schema errors
        if (errorClass != null && (
            errorClass.contains("SerializationException") ||
            errorClass.contains("DeserializationException") ||
            errorClass.contains("JsonProcessingException"))) {
            return "Schema/serialization error - incompatible message format";
        }

        // Null/missing required fields
        if (errorMessage != null && errorMessage.contains("cannot be null")) {
            return "Missing required field - " + extractFieldName(errorMessage);
        }

        // Duplicate detection
        if (errorMessage != null && errorMessage.contains("duplicate")) {
            return "Duplicate message - already processed";
        }

        // Obsolete events
        if (isObsoleteEvent(message)) {
            return "Obsolete event - too old to process";
        }

        // Test/debug messages
        if (isTestMessage(message)) {
            return "Test/debug message - not for production processing";
        }

        // Default
        return "Unrecoverable error - max retries exceeded";
    }

    /**
     * Checks if event is obsolete (too old).
     */
    private boolean isObsoleteEvent(DLQMessage message) {
        // Events older than 30 days are considered obsolete
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return message.getCreatedAt().isBefore(thirtyDaysAgo);
    }

    /**
     * Checks if message is a test message.
     */
    private boolean isTestMessage(DLQMessage message) {
        String payload = message.getMessagePayload();
        if (payload == null) return false;

        return payload.contains("\"test\":true") ||
               payload.contains("\"environment\":\"test\"") ||
               payload.contains("\"debug\":true") ||
               message.getOriginalTopic().contains("test") ||
               message.getOriginalTopic().contains("debug");
    }

    /**
     * Extracts validation error from error message.
     */
    private String extractValidationError(String errorMessage) {
        // Try to extract the specific validation failure
        if (errorMessage.contains(":")) {
            String[] parts = errorMessage.split(":", 2);
            if (parts.length > 1) {
                return parts[1].trim();
            }
        }
        return errorMessage;
    }

    /**
     * Extracts field name from "cannot be null" error.
     */
    private String extractFieldName(String errorMessage) {
        // Extract field name from messages like "customerId cannot be null"
        String[] parts = errorMessage.split(" ");
        if (parts.length > 0) {
            return parts[0];
        }
        return "unknown field";
    }

    /**
     * Records skip metrics.
     */
    private void recordSkipMetric(DLQMessage message, String reason) {
        Counter.builder("dlq.message.skipped")
            .tag("topic", message.getOriginalTopic())
            .tag("reason", categorizeReason(reason))
            .tag("priority", message.getPriority().toString())
            .description("DLQ messages skipped")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Categorizes skip reason for metrics.
     */
    private String categorizeReason(String reason) {
        if (reason.contains("Validation")) return "validation";
        if (reason.contains("Schema")) return "schema";
        if (reason.contains("Duplicate")) return "duplicate";
        if (reason.contains("Obsolete")) return "obsolete";
        if (reason.contains("Test")) return "test";
        return "other";
    }

    @Override
    public String getStrategyName() {
        return "SKIP";
    }

    @Override
    public boolean canHandle(DLQMessage message) {
        // Can handle validation errors, schema errors, obsolete events
        String errorClass = message.getErrorClass();
        String errorMessage = message.getErrorMessage();

        if (errorClass != null) {
            if (errorClass.contains("ValidationException") ||
                errorClass.contains("IllegalArgumentException") ||
                errorClass.contains("SerializationException") ||
                errorClass.contains("JsonProcessingException")) {
                return true;
            }
        }

        if (errorMessage != null) {
            if (errorMessage.contains("validation failed") ||
                errorMessage.contains("cannot be null") ||
                errorMessage.contains("duplicate")) {
                return true;
            }
        }

        // Skip obsolete events
        if (isObsoleteEvent(message)) {
            return true;
        }

        // Skip test messages
        if (isTestMessage(message)) {
            return true;
        }

        return false;
    }
}
