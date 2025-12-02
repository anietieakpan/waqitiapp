package com.waqiti.common.kafka;

import com.waqiti.common.kafka.dlq.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * PRODUCTION-READY DLQ HANDLER BASE CLASS
 *
 * PURPOSE:
 * Provides complete, production-ready DLQ handling implementation.
 * Eliminates all "// TODO: Implement custom recovery logic" placeholders.
 *
 * USAGE:
 * All DLQ handler classes should extend this and call handleDLQMessage():
 *
 * @Component
 * public class SomeDlqHandler extends AbstractDLQHandler {
 *     public SomeDlqHandler(JdbcTemplate jdbcTemplate) {
 *         super(jdbcTemplate, "some-service");
 *     }
 *
 *     @KafkaListener(topics = "some-topic-dlq")
 *     public void handleDLQ(ConsumerRecord<String, String> record, Acknowledgment ack) {
 *         handleDLQMessage(record, ack, this::processCustomRecovery);
 *     }
 *
 *     private void processCustomRecovery(ConsumerRecord<String, String> record) {
 *         // Optional: Add service-specific recovery logic
 *     }
 * }
 *
 * @author Waqiti Event Architecture Team
 * @since 2025-10-31
 * @version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractDLQHandler {

    protected final JdbcTemplate jdbcTemplate;
    protected final String serviceName;

    /**
     * COMPLETE DLQ MESSAGE HANDLING - NO TODOS!
     *
     * This method provides full production-ready DLQ handling:
     * 1. Stores message in dlq_records table
     * 2. Updates statistics
     * 3. Calls custom recovery logic (if provided)
     * 4. Acknowledges message
     * 5. Creates alerts if needed
     *
     * @param record failed Kafka message
     * @param acknowledgment Kafka acknowledgment
     * @param customRecoveryLogic optional service-specific recovery (can be null)
     */
    protected void handleDLQMessage(
        org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record,
        Acknowledgment acknowledgment,
        RecoveryLogic customRecoveryLogic
    ) {
        UUID dlqId = UUID.randomUUID();
        String topicName = record.topic();
        String messageKey = record.key();
        String messageValue = record.value();

        try {
            log.warn("DLQ message received: topic={}, partition={}, offset={}, service={}",
                topicName, record.partition(), record.offset(), serviceName);

            // Store in DLQ database
            storeDLQRecord(dlqId, record);

            // Update statistics
            updateDLQStatistics(topicName);

            // Execute custom recovery logic if provided
            if (customRecoveryLogic != null) {
                try {
                    customRecoveryLogic.recover(record);
                    markDLQRecordAsRecovered(dlqId, "Custom recovery logic executed successfully");

                } catch (Exception recoveryEx) {
                    log.error("Custom recovery logic failed for DLQ record: {}", dlqId, recoveryEx);
                    updateDLQRecordError(dlqId, "Custom recovery failed: " + recoveryEx.getMessage());
                }
            } else {
                // No custom recovery - message stored for manual review
                updateDLQRecordError(dlqId, "No custom recovery logic - requires manual review");
            }

            // Check if alert needed
            createAlertIfNeeded(dlqId, topicName);

            // Acknowledge message to remove from DLQ topic
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }

            log.info("DLQ message processed successfully: dlqId={}, topic={}", dlqId, topicName);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process DLQ message. Topic: {}, Offset: {}, Error: {}",
                topicName, record.offset(), e.getMessage(), e);

            // Don't acknowledge - message will be reprocessed
            // This is the last safety net - if DLQ handling fails, we need manual intervention
        }
    }

    /**
     * Stores DLQ record in database.
     */
    private void storeDLQRecord(UUID dlqId, org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        String sql = """
            INSERT INTO dlq_records (
                id, topic_name, partition_number, offset_number,
                message_key, message_value, headers,
                consumer_service, failure_reason, error_message,
                retry_count, max_retries, next_retry_at,
                first_failed_at, last_failed_at,
                status, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        LocalDateTime nextRetryAt = calculateNextRetryTime(0);
        String headers = extractHeaders(record);
        String failureReason = determineFailureReason(record);

        jdbcTemplate.update(sql,
            dlqId,
            record.topic(),
            record.partition(),
            record.offset(),
            record.key(),
            record.value(),
            headers,
            serviceName,
            failureReason,
            "Message sent to DLQ - see logs for details",
            0, // retry_count
            getMaxRetries(failureReason),
            java.sql.Timestamp.valueOf(nextRetryAt),
            java.sql.Timestamp.from(Instant.now()),
            java.sql.Timestamp.from(Instant.now()),
            "PENDING_RETRY",
            java.sql.Timestamp.from(Instant.now())
        );

        log.debug("DLQ record stored in database: {}", dlqId);
    }

    /**
     * Updates DLQ statistics for monitoring.
     */
    private void updateDLQStatistics(String topicName) {
        String sql = """
            INSERT INTO dlq_statistics (
                topic_name, consumer_service, failure_reason,
                total_failed, last_failure_at
            ) VALUES (?, ?, ?, 1, ?)
            ON CONFLICT (topic_name, consumer_service, failure_reason)
            DO UPDATE SET
                total_failed = dlq_statistics.total_failed + 1,
                last_failure_at = EXCLUDED.last_failure_at
            """;

        jdbcTemplate.update(sql,
            topicName,
            serviceName,
            "DLQ_MESSAGE",
            java.sql.Timestamp.from(Instant.now())
        );
    }

    /**
     * Marks DLQ record as recovered.
     */
    private void markDLQRecordAsRecovered(UUID dlqId, String resolutionNotes) {
        String sql = """
            UPDATE dlq_records
            SET status = 'RESOLVED',
                resolved_at = CURRENT_TIMESTAMP,
                resolution_notes = ?
            WHERE id = ?
            """;

        jdbcTemplate.update(sql, resolutionNotes, dlqId);
    }

    /**
     * Updates DLQ record with error details.
     */
    private void updateDLQRecordError(UUID dlqId, String errorMessage) {
        String sql = """
            UPDATE dlq_records
            SET error_message = ?,
                status = 'REQUIRES_MANUAL_REVIEW'
            WHERE id = ?
            """;

        jdbcTemplate.update(sql, errorMessage, dlqId);
    }

    /**
     * Creates alert if DLQ threshold exceeded.
     */
    private void createAlertIfNeeded(UUID dlqId, String topicName) {
        // Check if we have too many DLQ messages for this topic
        String countSql = """
            SELECT COUNT(*) FROM dlq_records
            WHERE topic_name = ?
                AND consumer_service = ?
                AND created_at > NOW() - INTERVAL '1 hour'
                AND status != 'RESOLVED'
            """;

        Integer recentDLQCount = jdbcTemplate.queryForObject(countSql, Integer.class, topicName, serviceName);

        if (recentDLQCount != null && recentDLQCount > 10) {
            // Create high-severity alert
            String alertSql = """
                INSERT INTO dlq_alerts (
                    id, dlq_record_id, topic_name, consumer_service,
                    failure_reason, severity, alert_message,
                    created_at, acknowledged
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            UUID alertId = UUID.randomUUID();
            String alertMessage = String.format(
                "HIGH DLQ RATE: %d messages in last hour for topic %s in service %s",
                recentDLQCount, topicName, serviceName
            );

            jdbcTemplate.update(alertSql,
                alertId,
                dlqId,
                topicName,
                serviceName,
                "HIGH_DLQ_RATE",
                "HIGH",
                alertMessage,
                java.sql.Timestamp.from(Instant.now()),
                false
            );

            log.error("DLQ ALERT CREATED: {}", alertMessage);
        }
    }

    /**
     * Calculates next retry time with exponential backoff.
     */
    private LocalDateTime calculateNextRetryTime(int attemptNumber) {
        long delayMinutes = (long) Math.pow(2, attemptNumber);
        if (delayMinutes > 60) delayMinutes = 60;

        return LocalDateTime.now(ZoneOffset.UTC).plusMinutes(delayMinutes);
    }

    /**
     * Extracts Kafka headers as JSON string.
     */
    private String extractHeaders(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        StringBuilder headers = new StringBuilder("{");
        record.headers().forEach(header -> {
            if (headers.length() > 1) headers.append(",");
            headers.append("\"").append(header.key()).append("\":\"")
                .append(new String(header.value())).append("\"");
        });
        headers.append("}");
        return headers.toString();
    }

    /**
     * Determines failure reason from topic name.
     */
    private String determineFailureReason(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        String topic = record.topic();
        if (topic.contains("payment")) return "PAYMENT_PROCESSING_ERROR";
        if (topic.contains("user")) return "USER_EVENT_ERROR";
        if (topic.contains("transaction")) return "TRANSACTION_ERROR";
        if (topic.contains("notification")) return "NOTIFICATION_ERROR";
        return "UNKNOWN_ERROR";
    }

    /**
     * Gets max retry attempts based on failure reason.
     */
    private int getMaxRetries(String failureReason) {
        return switch (failureReason) {
            case "PAYMENT_PROCESSING_ERROR", "TRANSACTION_ERROR" -> 5;
            case "USER_EVENT_ERROR" -> 3;
            case "NOTIFICATION_ERROR" -> 2;
            default -> 3;
        };
    }

    /**
     * Functional interface for custom recovery logic.
     */
    @FunctionalInterface
    public interface RecoveryLogic {
        void recover(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) throws Exception;
    }
}
