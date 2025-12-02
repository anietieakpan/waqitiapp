package com.waqiti.savings.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for tracking processed events (idempotency).
 * Ensures events are processed exactly once.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Find processed event by event ID.
     * Used for idempotency checking.
     *
     * @param eventId the unique event identifier
     * @return Optional containing processed event if exists
     */
    Optional<ProcessedEvent> findByEventId(String eventId);

    /**
     * Check if event has been processed.
     *
     * @param eventId the unique event identifier
     * @return true if event has been processed
     */
    boolean existsByEventId(String eventId);

    /**
     * Find processed event by event ID and type.
     * More specific idempotency check.
     *
     * @param eventId the unique event identifier
     * @param eventType the event type
     * @return Optional containing processed event
     */
    Optional<ProcessedEvent> findByEventIdAndEventType(String eventId, String eventType);

    /**
     * Check if event of specific type has been processed.
     *
     * @param eventId the unique event identifier
     * @param eventType the event type
     * @return true if event has been processed
     */
    boolean existsByEventIdAndEventType(String eventId, String eventType);

    /**
     * Delete old processed events for cleanup.
     * Removes events older than retention period.
     *
     * @param beforeDate cutoff date
     */
    @Query("DELETE FROM ProcessedEvent pe WHERE pe.processedAt < :beforeDate")
    void deleteOldProcessedEvents(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * Count processed events by type.
     *
     * @param eventType the event type
     * @return count of processed events
     */
    Long countByEventType(String eventType);

    /**
     * Find events processed within a date range.
     *
     * @param startDate start of date range
     * @param endDate end of date range
     * @return list of processed events
     */
    @Query("SELECT pe FROM ProcessedEvent pe WHERE pe.processedAt BETWEEN :startDate AND :endDate")
    java.util.List<ProcessedEvent> findProcessedEventsBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get processing statistics by event type.
     *
     * @return statistics grouped by event type
     */
    @Query("SELECT NEW map(" +
           "pe.eventType as eventType, " +
           "COUNT(pe) as count, " +
           "MAX(pe.processedAt) as lastProcessedAt) " +
           "FROM ProcessedEvent pe " +
           "GROUP BY pe.eventType")
    java.util.List<java.util.Map<String, Object>> getStatisticsByEventType();
}

/**
 * Entity class for processed events.
 * Minimal entity for idempotency tracking.
 */
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "processed_events", indexes = {
    @jakarta.persistence.Index(name = "idx_processed_events_event_id", columnList = "event_id", unique = true),
    @jakarta.persistence.Index(name = "idx_processed_events_type", columnList = "event_type"),
    @jakarta.persistence.Index(name = "idx_processed_events_processed_at", columnList = "processed_at")
})
class ProcessedEvent {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue
    private UUID id;

    @jakarta.persistence.Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    @jakarta.persistence.Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @jakarta.persistence.Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @jakarta.persistence.Column(name = "processing_result", length = 50)
    private String processingResult; // SUCCESS, FAILURE, PARTIAL

    @jakarta.persistence.Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @jakarta.persistence.Column(name = "retry_count")
    private Integer retryCount = 0;

    // Constructors
    public ProcessedEvent() {
        this.processedAt = LocalDateTime.now();
    }

    public ProcessedEvent(String eventId, String eventType) {
        this();
        this.eventId = eventId;
        this.eventType = eventType;
        this.processingResult = "SUCCESS";
    }

    public ProcessedEvent(String eventId, String eventType, String result) {
        this();
        this.eventId = eventId;
        this.eventType = eventType;
        this.processingResult = result;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getProcessingResult() { return processingResult; }
    public void setProcessingResult(String processingResult) { this.processingResult = processingResult; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
}
