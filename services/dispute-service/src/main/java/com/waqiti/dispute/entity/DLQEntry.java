package com.waqiti.dispute.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for storing Dead Letter Queue events
 * Provides persistent storage and recovery tracking for failed Kafka messages
 */
@Entity
@Table(name = "dlq_entries", indexes = {
    @Index(name = "idx_dlq_status", columnList = "status"),
    @Index(name = "idx_dlq_topic", columnList = "source_topic"),
    @Index(name = "idx_dlq_created", columnList = "created_at"),
    @Index(name = "idx_dlq_event_id", columnList = "event_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "source_topic", nullable = false, length = 255)
    private String sourceTopic;

    @Column(name = "event_json", nullable = false, columnDefinition = "TEXT")
    private String eventJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_stacktrace", columnDefinition = "TEXT")
    private String errorStacktrace;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DLQStatus status = DLQStatus.PENDING_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_strategy", length = 50)
    private RecoveryStrategy recoveryStrategy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "alert_sent", nullable = false)
    private boolean alertSent = false;

    @Column(name = "ticket_id", length = 50)
    private String ticketId;

    @Version
    @Column(name = "version")
    private Long version;
}
