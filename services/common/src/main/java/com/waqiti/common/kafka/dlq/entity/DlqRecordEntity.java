package com.waqiti.common.kafka.dlq.entity;

import com.waqiti.common.kafka.dlq.DlqEventType;
import com.waqiti.common.kafka.dlq.DlqStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DLQ Record Entity - Production Database Schema
 *
 * CRITICAL COMPLIANCE:
 * - Immutable audit trail of all Kafka message failures
 * - Supports regulatory reporting and root cause analysis
 * - Enables replay and recovery of failed events
 *
 * DATABASE: PostgreSQL with JSONB support
 * TABLE: dlq_records
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Entity
@Table(name = "dlq_records", indexes = {
        @Index(name = "idx_dlq_message_id", columnList = "message_id", unique = true),
        @Index(name = "idx_dlq_status_event_type", columnList = "status, event_type"),
        @Index(name = "idx_dlq_next_retry_time", columnList = "next_retry_time"),
        @Index(name = "idx_dlq_created_at", columnList = "created_at"),
        @Index(name = "idx_dlq_service_name", columnList = "service_name"),
        @Index(name = "idx_dlq_topic_partition_offset", columnList = "topic, partition, offset")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DlqRecordEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "message_id", nullable = false, unique = true, length = 255)
    private String messageId;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "partition", nullable = false)
    private Integer partition;

    @Column(name = "offset", nullable = false)
    private Long offset;

    @Column(name = "message_key", length = 500)
    private String messageKey;

    @Column(name = "message_value", nullable = false, columnDefinition = "TEXT")
    private String messageValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private DlqEventType eventType;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DlqStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "first_failure_time", nullable = false)
    private Instant firstFailureTime;

    @Column(name = "last_failure_time")
    private Instant lastFailureTime;

    @Column(name = "last_failure_reason", columnDefinition = "TEXT")
    private String lastFailureReason;

    @Column(name = "next_retry_time")
    private Instant nextRetryTime;

    @Column(name = "reprocessed_at")
    private LocalDateTime reprocessedAt;

    @Column(name = "reprocessing_result", columnDefinition = "TEXT")
    private String reprocessingResult;

    @Column(name = "parked_at")
    private LocalDateTime parkedAt;

    @Column(name = "parked_reason", columnDefinition = "TEXT")
    private String parkedReason;

    @Type(JsonType.class)
    @Column(name = "headers", columnDefinition = "jsonb")
    private Map<String, String> headers;

    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (firstFailureTime == null) {
            firstFailureTime = Instant.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
