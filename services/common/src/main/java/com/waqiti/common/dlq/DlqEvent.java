package com.waqiti.common.dlq;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

/**
 * Persistent DLQ Event Entity
 *
 * Stores all failed events for audit trail, recovery, and manual review.
 * Provides complete visibility into all DLQ events across the platform.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Entity
@Table(name = "dlq_events", indexes = {
    @Index(name = "idx_dlq_status", columnList = "status"),
    @Index(name = "idx_dlq_service", columnList = "service_name"),
    @Index(name = "idx_dlq_created", columnList = "created_at"),
    @Index(name = "idx_dlq_recovery_strategy", columnList = "recovery_strategy"),
    @Index(name = "idx_dlq_retry_count", columnList = "retry_count")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String serviceName;

    @Column(nullable = false, length = 200)
    private String originalTopic;

    @Column(nullable = false, length = 200)
    private String dlqTopic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DlqEventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RecoveryStrategy recoveryStrategy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String eventPayload;

    @Column(length = 200)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(nullable = false)
    private Integer maxRetries = 3;

    @Column
    private Instant lastRetryAt;

    @Column
    private Instant nextRetryAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant processedAt;

    @Column
    private Instant resolvedAt;

    @Column(length = 100)
    private String assignedTo;

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(columnDefinition = "TEXT")
    private String compensationAction;

    @Type(io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Type(io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> headers;

    @Column
    private Double severity; // 0.0 to 1.0

    @Column
    private Long financialImpactCents;

    @Column(length = 100)
    private String correlationId;

    @Column(length = 100)
    private String traceId;

    @Column
    private Boolean alertSent = false;

    @Column
    private Boolean pagerDutyTriggered = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = DlqEventStatus.PENDING;
        }
    }

    public enum DlqEventStatus {
        PENDING,        // Just received, not yet processed
        PROCESSING,     // Currently being processed
        RETRY_SCHEDULED, // Retry scheduled
        RETRYING,       // Currently retrying
        AUTO_RECOVERED, // Automatically recovered
        MANUAL_REVIEW,  // Requires manual review
        COMPENSATED,    // Compensation executed
        RESOLVED,       // Successfully resolved
        DEAD_LETTER,    // Permanent failure - moved to dead letter
        ARCHIVED        // Archived after resolution
    }

    public enum RecoveryStrategy {
        RETRY,          // Retry with exponential backoff
        COMPENSATE,     // Execute compensation logic
        MANUAL_REVIEW,  // Manual intervention required
        AUTO_RESOLVE,   // Can be auto-resolved (e.g., duplicates)
        DEAD_LETTER,    // No recovery possible
        IGNORE          // Safely ignorable
    }
}
