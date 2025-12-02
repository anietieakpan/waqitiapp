package com.waqiti.user.kafka.dlq.entity;

import com.waqiti.user.kafka.dlq.DlqRecoveryStrategy;
import com.waqiti.user.kafka.dlq.DlqSeverityLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Database entity for DLQ events
 * Provides complete audit trail of failed messages and recovery attempts
 */
@Entity
@Table(name = "dlq_events", indexes = {
        @Index(name = "idx_dlq_business_id", columnList = "business_identifier"),
        @Index(name = "idx_dlq_severity", columnList = "severity"),
        @Index(name = "idx_dlq_status", columnList = "recovery_status"),
        @Index(name = "idx_dlq_requires_manual", columnList = "requires_manual_intervention"),
        @Index(name = "idx_dlq_created_at", columnList = "created_at"),
        @Index(name = "idx_dlq_event_type", columnList = "event_type"),
        @Index(name = "idx_dlq_consumer_group", columnList = "consumer_group")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 500)
    private String eventType;

    @Column(name = "business_identifier", length = 255)
    private String businessIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private DlqSeverityLevel severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_strategy", nullable = false, length = 50)
    private DlqRecoveryStrategy recoveryStrategy;

    @Column(name = "original_topic", length = 255)
    private String originalTopic;

    @Column(name = "partition")
    private Integer partition;

    @Column(name = "offset")
    private Long offset;

    @Column(name = "consumer_group", length = 255)
    private String consumerGroup;

    @Column(name = "retry_attempts", nullable = false)
    @Builder.Default
    private Integer retryAttempts = 0;

    @Column(name = "first_failure_time")
    private LocalDateTime firstFailureTime;

    @Column(name = "dlq_entry_time")
    private LocalDateTime dlqEntryTime;

    @Column(name = "original_event", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.JSON)
    private String originalEvent;

    @Column(name = "headers", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.JSON)
    private String headers;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failure_stack_trace", columnDefinition = "TEXT")
    private String failureStackTrace;

    @Column(name = "metadata", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "recovery_result", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.JSON)
    private String recoveryResult;

    @Column(name = "recovery_status", length = 50)
    private String recoveryStatus;

    @Column(name = "requires_manual_intervention")
    @Builder.Default
    private Boolean requiresManualIntervention = false;

    @Column(name = "ticket_number", length = 100)
    private String ticketNumber;

    @Column(name = "recovery_error_message", columnDefinition = "TEXT")
    private String recoveryErrorMessage;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
