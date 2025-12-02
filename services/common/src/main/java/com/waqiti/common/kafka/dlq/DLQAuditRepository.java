package com.waqiti.common.kafka.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Repository for auditing DLQ recovery operations
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class DLQAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Record successful message recovery
     */
    public void recordSuccess(String messageId, String topic, int attemptNumber) {
        String sql = """
            INSERT INTO dlq_audit_log (
                message_id, topic, status, attempt_number, timestamp
            ) VALUES (?, ?, 'SUCCESS', ?, ?)
            """;

        try {
            jdbcTemplate.update(sql, messageId, topic, attemptNumber, Instant.now());
            log.debug("Recorded DLQ success: messageId={}", messageId);
        } catch (Exception e) {
            log.error("Failed to record DLQ success audit", e);
        }
    }

    /**
     * Record retry attempt
     */
    public void recordRetry(String messageId, String topic, int attemptNumber, long backoffMs) {
        String sql = """
            INSERT INTO dlq_audit_log (
                message_id, topic, status, attempt_number, backoff_ms, timestamp
            ) VALUES (?, ?, 'RETRY', ?, ?, ?)
            """;

        try {
            jdbcTemplate.update(sql, messageId, topic, attemptNumber, backoffMs, Instant.now());
            log.debug("Recorded DLQ retry: messageId={}, attempt={}", messageId, attemptNumber);
        } catch (Exception e) {
            log.error("Failed to record DLQ retry audit", e);
        }
    }

    /**
     * Save permanently failed message to dead letter storage
     */
    public void saveDeadLetter(DLQDeadLetter deadLetter) {
        String sql = """
            INSERT INTO dlq_dead_letters (
                message_id, topic, partition, offset, key_data, value_data,
                headers, failure_reason, stack_trace, total_retry_attempts, timestamp
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            """;

        try {
            jdbcTemplate.update(sql,
                deadLetter.getMessageId(),
                deadLetter.getTopic(),
                deadLetter.getPartition(),
                deadLetter.getOffset(),
                deadLetter.getKey(),
                deadLetter.getValue(),
                convertHeadersToJson(deadLetter.getHeaders()),
                deadLetter.getFailureReason(),
                deadLetter.getStackTrace(),
                deadLetter.getTotalRetryAttempts(),
                deadLetter.getTimestamp()
            );

            log.info("Saved dead letter to storage: messageId={}", deadLetter.getMessageId());
        } catch (Exception e) {
            log.error("Failed to save dead letter", e);
        }
    }

    private String convertHeadersToJson(java.util.Map<String, String> headers) {
        // Simple JSON conversion - in production use Jackson
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        headers.forEach((k, v) ->
            json.append("\"").append(k).append("\":\"").append(v).append("\",")
        );
        json.setLength(json.length() - 1); // Remove trailing comma
        json.append("}");

        return json.toString();
    }
}
