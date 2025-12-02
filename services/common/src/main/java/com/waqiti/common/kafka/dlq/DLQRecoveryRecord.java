package com.waqiti.common.kafka.dlq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persistent record of DLQ recovery attempts.
 */
@Entity
@Table(name = "dlq_recovery_records", indexes = {
    @Index(name = "idx_message_key", columnList = "message_key"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_original_topic", columnList = "original_topic")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQRecoveryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "message_key", nullable = false, unique = true)
    private String messageKey;

    @Column(name = "original_topic", nullable = false)
    private String originalTopic;

    @Column(name = "dlq_topic")
    private String dlqTopic;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecoveryStatus status;

    @Column(name = "retry_attempts")
    private int retryAttempts;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "recovered_at")
    private LocalDateTime recoveredAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "manual_retry_by")
    private String manualRetryBy;

    @Column(name = "manual_retry_at")
    private LocalDateTime manualRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
