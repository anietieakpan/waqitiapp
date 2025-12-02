package com.waqiti.common.events.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dead letter queue entry for events that could not be processed
 * Stores failed events for manual review and retry
 */
@Entity
@Table(name = "dead_letter_queue")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterQueueEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "original_event_id")
    private String originalEventId;
    
    @Column(name = "topic", nullable = false)
    private String topic;
    
    @Column(name = "partition_key")
    private String partitionKey;
    
    @Column(name = "event_data", columnDefinition = "TEXT", nullable = false)
    private String eventData;
    
    @Column(name = "event_type")
    private String eventType;
    
    @Column(name = "aggregate_id")
    private String aggregateId;
    
    @Column(name = "aggregate_type")
    private String aggregateType;
    
    @Column(name = "original_created_at")
    private LocalDateTime originalCreatedAt;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
    
    @Column(name = "replayed_at")
    private LocalDateTime replayedAt;
    
    @Column(name = "headers", columnDefinition = "TEXT")
    private String headers;
    
    @Column(name = "error_type")
    private String errorType;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private DLQStatus status;
    
    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;
    
    @Column(name = "max_retries")
    @Builder.Default
    private int maxRetries = 3;
    
    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
    
    @Column(name = "resolved_by")
    private String resolvedBy;
    
    @Column(name = "consumer_group")
    private String consumerGroup;
    
    @Column(name = "processing_host")
    private String processingHost;
    
    @Column(name = "version")
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = DLQStatus.UNPROCESSED;
        }
    }
    
    public enum DLQStatus {
        NEW,
        UNPROCESSED,
        PENDING_RETRY,
        RETRYING,
        RESOLVED,
        DISCARDED,
        MANUAL_REVIEW,
        REPLAYED
    }
    
    /**
     * Check if entry can be retried
     */
    public boolean canRetry() {
        return (status == DLQStatus.UNPROCESSED || status == DLQStatus.PENDING_RETRY) 
                && retryCount < maxRetries;
    }
    
    /**
     * Mark for retry with exponential backoff
     */
    public void markForRetry() {
        this.status = DLQStatus.PENDING_RETRY;
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
        this.nextRetryAt = calculateNextRetryTime();
    }
    
    /**
     * Calculate next retry time with exponential backoff
     */
    private LocalDateTime calculateNextRetryTime() {
        long delayMinutes = (long) Math.pow(2, Math.min(retryCount, 5)) * 10;
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }
    
    /**
     * Mark as resolved
     */
    public void markAsResolved(String notes, String resolvedByUser) {
        this.status = DLQStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.resolutionNotes = notes;
        this.resolvedBy = resolvedByUser;
    }
    
    /**
     * Mark for manual review
     */
    public void markForManualReview() {
        this.status = DLQStatus.MANUAL_REVIEW;
    }
}