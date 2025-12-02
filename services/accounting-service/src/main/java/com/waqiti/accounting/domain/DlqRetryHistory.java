package com.waqiti.accounting.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DLQ Retry History Entity
 * Audit trail of all retry attempts for DLQ messages
 */
@Entity
@Table(name = "dlq_retry_history", indexes = {
    @Index(name = "idx_dlq_retry_history_message", columnList = "dlq_message_id,retry_timestamp"),
    @Index(name = "idx_dlq_retry_history_status", columnList = "retry_status,retry_timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqRetryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "dlq_message_id", nullable = false)
    private UUID dlqMessageId;

    @NotNull
    @Column(name = "retry_attempt", nullable = false)
    private Integer retryAttempt;

    @NotNull
    @Column(name = "retry_timestamp", nullable = false)
    @Builder.Default
    private LocalDateTime retryTimestamp = LocalDateTime.now();

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "retry_status", nullable = false, length = 50)
    private RetryStatus retryStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    public enum RetryStatus {
        SUCCESS,
        FAILURE,
        TIMEOUT
    }
}
