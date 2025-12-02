package com.waqiti.common.events.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event outbox entry for reliable event publishing
 * Implements the outbox pattern for guaranteed delivery
 */
@Entity
@Table(name = "event_outbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventOutboxEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;
    
    @Column(name = "aggregate_id")
    private String aggregateId;
    
    @Column(name = "aggregate_type")
    private String aggregateType;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "topic", nullable = false)
    private String topic;
    
    @Column(name = "partition_key")
    private String partitionKey;
    
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;
    
    @Column(name = "headers", columnDefinition = "TEXT")
    private String headers;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private EventStatus status;
    
    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;
    
    @Column(name = "max_retries")
    @Builder.Default
    private int maxRetries = 3;
    
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    
    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "version")
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = EventStatus.PENDING;
        }
    }
    
    public enum EventStatus {
        PENDING,          // Initial state - not yet processed
        PROCESSING,       // Currently being processed
        PUBLISHED,        // Successfully published to Kafka
        SENT,             // Successfully sent (synonym for PUBLISHED)
        FAILED,           // Failed, will be retried
        RETRY_SCHEDULED,  // Failed, retry has been scheduled
        DEAD_LETTER       // Max retries exceeded, moved to DLQ
    }
    
    /**
     * Check if event can be retried
     */
    public boolean canRetry() {
        return status == EventStatus.FAILED && retryCount < maxRetries;
    }
    
    /**
     * Increment retry count and update retry timestamp
     */
    public void incrementRetry() {
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
        this.nextRetryAt = calculateNextRetryTime();
    }
    
    /**
     * Calculate next retry time with exponential backoff
     */
    private LocalDateTime calculateNextRetryTime() {
        long delayMinutes = (long) Math.pow(2, Math.min(retryCount, 5)) * 5;
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }
    
    /**
     * Mark as successfully published
     */
    public void markAsPublished() {
        this.status = EventStatus.PUBLISHED;
        this.processedAt = LocalDateTime.now();
    }
    
    /**
     * Mark as failed with error details
     */
    public void markAsFailed(String error) {
        this.status = EventStatus.FAILED;
        this.lastError = error;
        incrementRetry();

        if (retryCount >= maxRetries) {
            this.status = EventStatus.DEAD_LETTER;
        }
    }

    /**
     * Get event data (payload) for publishing
     */
    public String getEventData() {
        return this.payload;
    }
}