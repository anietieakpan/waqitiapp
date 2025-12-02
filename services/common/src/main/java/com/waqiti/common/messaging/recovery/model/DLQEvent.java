package com.waqiti.common.messaging.recovery.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Dead Letter Queue Event Entity
 *
 * Represents a failed event that has been moved to the DLQ for recovery.
 */
@Entity
@Table(
        name = "dlq_events",
        indexes = {
                @Index(name = "idx_dlq_event_id", columnList = "event_id"),
                @Index(name = "idx_dlq_service_name", columnList = "service_name"),
                @Index(name = "idx_dlq_status", columnList = "status"),
                @Index(name = "idx_dlq_retry_count", columnList = "retry_count"),
                @Index(name = "idx_dlq_first_failed_at", columnList = "first_failed_at"),
                @Index(name = "idx_dlq_priority", columnList = "priority")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DLQEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "original_topic", nullable = false, length = 255)
    private String originalTopic;

    @Type(JsonBinaryType.class)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retry_count", nullable = false)
    @Builder.Default
    private Integer maxRetryCount = 5;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DLQStatus status = DLQStatus.PENDING;

    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private String priority = "MEDIUM";

    @Column(name = "first_failed_at", nullable = false)
    private LocalDateTime firstFailedAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Increment retry count and return new instance
     */
    public DLQEvent incrementRetryCount() {
        return DLQEvent.builder()
                .id(this.id)
                .eventId(this.eventId)
                .serviceName(this.serviceName)
                .eventType(this.eventType)
                .originalTopic(this.originalTopic)
                .payload(this.payload)
                .errorMessage(this.errorMessage)
                .stackTrace(this.stackTrace)
                .retryCount(this.retryCount + 1)
                .maxRetryCount(this.maxRetryCount)
                .status(this.status)
                .priority(this.priority)
                .firstFailedAt(this.firstFailedAt)
                .lastRetryAt(LocalDateTime.now())
                .metadata(this.metadata)
                .build();
    }

    /**
     * Check if max retries exceeded
     */
    public boolean isMaxRetriesExceeded() {
        return this.retryCount >= this.maxRetryCount;
    }
}

