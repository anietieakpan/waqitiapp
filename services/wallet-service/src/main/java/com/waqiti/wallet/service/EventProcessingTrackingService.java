package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.EventProcessingRecord;
import com.waqiti.wallet.domain.EventProcessingStatus;
import com.waqiti.wallet.repository.EventProcessingRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Production-Ready Event Processing Tracking Service
 *
 * Enterprise-grade service for tracking event processing with
 * idempotency, audit trails, and monitoring.
 *
 * Features:
 * - Duplicate event detection (idempotency)
 * - Event processing lifecycle tracking
 * - Retry and DLT tracking
 * - Metrics and monitoring
 * - Audit trail for debugging
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventProcessingTrackingService {

    private final EventProcessingRecordRepository eventProcessingRecordRepository;
    private final MeterRegistry meterRegistry;

    private static final String METRIC_PREFIX = "wallet.event.processing";

    /**
     * Check if event has already been processed (duplicate detection)
     *
     * @param eventId the event ID
     * @param eventType the event type
     * @return true if duplicate (already completed), false otherwise
     */
    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public boolean isDuplicateEvent(String eventId, String eventType) {
        log.debug("Checking for duplicate event: eventId={}, eventType={}", eventId, eventType);

        boolean isDuplicate = eventProcessingRecordRepository.existsCompletedEvent(eventId, eventType);

        if (isDuplicate) {
            incrementEventCounter("duplicate", eventType);
            log.warn("Duplicate event detected: eventId={}, eventType={}", eventId, eventType);
        }

        return isDuplicate;
    }

    /**
     * Track event processing start
     *
     * @param eventId the event ID
     * @param eventType the event type
     * @param correlationId correlation ID
     * @param metadata additional metadata
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void trackEventProcessingStart(String eventId, String eventType,
                                         String correlationId, Map<String, String> metadata) {
        log.info("Tracking event processing start: eventId={}, eventType={}, correlationId={}",
                eventId, eventType, correlationId);

        try {
            // Check if record already exists
            Optional<EventProcessingRecord> existing = eventProcessingRecordRepository
                    .findByEventIdAndEventType(eventId, eventType);

            EventProcessingRecord record;
            if (existing.isPresent()) {
                // Update existing record
                record = existing.get();
                record.setStatus(EventProcessingStatus.PROCESSING);
                record.setRetryCount(record.getRetryCount() + 1);
                log.debug("Updating existing event record (retry): eventId={}, retryCount={}",
                        eventId, record.getRetryCount());
            } else {
                // Create new record
                record = EventProcessingRecord.builder()
                        .eventId(eventId)
                        .eventType(eventType)
                        .correlationId(correlationId)
                        .status(EventProcessingStatus.PROCESSING)
                        .metadata(new HashMap<>(metadata))
                        .retryCount(0)
                        .createdAt(LocalDateTime.now())
                        .build();
            }

            eventProcessingRecordRepository.save(record);
            incrementEventCounter("processing_started", eventType);

        } catch (Exception e) {
            log.error("Failed to track event processing start: eventId={}, eventType={}",
                     eventId, eventType, e);
            // Non-critical - don't fail event processing
        }
    }

    /**
     * Track event processing success
     *
     * @param eventId the event ID
     * @param metadata processing metadata
     */
    @Async
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void trackEventProcessingSuccess(String eventId, Map<String, Object> metadata) {
        log.info("Tracking event processing success: eventId={}", eventId);

        try {
            Optional<EventProcessingRecord> recordOpt = findRecordByEventId(eventId);

            if (recordOpt.isPresent()) {
                EventProcessingRecord record = recordOpt.get();
                record.setStatus(EventProcessingStatus.COMPLETED);
                record.setProcessedAt(LocalDateTime.now());

                // Add processing metadata
                if (metadata != null && !metadata.isEmpty()) {
                    Map<String, String> stringMetadata = convertToStringMap(metadata);
                    if (record.getMetadata() == null) {
                        record.setMetadata(new HashMap<>());
                    }
                    record.getMetadata().putAll(stringMetadata);
                }

                eventProcessingRecordRepository.save(record);
                incrementEventCounter("completed", record.getEventType());

                log.debug("Event processing success tracked: eventId={}, eventType={}",
                        eventId, record.getEventType());
            } else {
                log.warn("Event processing record not found for success tracking: eventId={}", eventId);
            }

        } catch (Exception e) {
            log.error("Failed to track event processing success: eventId={}", eventId, e);
            // Non-critical - don't fail event processing
        }
    }

    /**
     * Track event processing failure
     *
     * @param eventId the event ID
     * @param errorType error type
     * @param errorMessage error message
     * @param metadata failure metadata
     */
    @Async
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void trackEventProcessingFailure(String eventId, String errorType,
                                           String errorMessage, Map<String, Object> metadata) {
        log.error("Tracking event processing failure: eventId={}, errorType={}, error={}",
                 eventId, errorType, errorMessage);

        try {
            Optional<EventProcessingRecord> recordOpt = findRecordByEventId(eventId);

            if (recordOpt.isPresent()) {
                EventProcessingRecord record = recordOpt.get();
                record.setStatus(EventProcessingStatus.FAILED);
                record.setErrorType(errorType);
                record.setErrorMessage(truncate(errorMessage, 2000));
                record.setFailedAt(LocalDateTime.now());

                // Add failure metadata
                if (metadata != null && !metadata.isEmpty()) {
                    Map<String, String> stringMetadata = convertToStringMap(metadata);
                    if (record.getMetadata() == null) {
                        record.setMetadata(new HashMap<>());
                    }
                    record.getMetadata().putAll(stringMetadata);
                }

                eventProcessingRecordRepository.save(record);
                incrementEventCounter("failed", record.getEventType());

                log.debug("Event processing failure tracked: eventId={}, eventType={}, retryCount={}",
                        eventId, record.getEventType(), record.getRetryCount());
            } else {
                log.warn("Event processing record not found for failure tracking: eventId={}", eventId);
            }

        } catch (Exception e) {
            log.error("Failed to track event processing failure: eventId={}", eventId, e);
            // Non-critical - don't fail event processing
        }
    }

    /**
     * Track event sent to DLT (Dead Letter Topic)
     *
     * @param eventId the event ID
     * @param eventType the event type
     * @param errorMessage error message
     * @param metadata DLT metadata
     */
    @Async
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void trackEventDLT(String eventId, String eventType,
                             String errorMessage, Map<String, String> metadata) {
        log.error("Tracking event DLT: eventId={}, eventType={}, error={}",
                 eventId, eventType, errorMessage);

        try {
            Optional<EventProcessingRecord> existing = eventProcessingRecordRepository
                    .findByEventIdAndEventType(eventId, eventType);

            EventProcessingRecord record;
            if (existing.isPresent()) {
                record = existing.get();
                record.setStatus(EventProcessingStatus.DLT);
                record.setErrorMessage(truncate(errorMessage, 2000));
            } else {
                // Create new record if not exists (shouldn't happen, but defensive)
                record = EventProcessingRecord.builder()
                        .eventId(eventId)
                        .eventType(eventType)
                        .status(EventProcessingStatus.DLT)
                        .errorMessage(truncate(errorMessage, 2000))
                        .metadata(new HashMap<>(metadata))
                        .retryCount(0)
                        .createdAt(LocalDateTime.now())
                        .failedAt(LocalDateTime.now())
                        .build();
            }

            // Add DLT metadata
            if (metadata != null && !metadata.isEmpty()) {
                if (record.getMetadata() == null) {
                    record.setMetadata(new HashMap<>());
                }
                record.getMetadata().putAll(metadata);
                record.getMetadata().put("dltTimestamp", LocalDateTime.now().toString());
            }

            eventProcessingRecordRepository.save(record);
            incrementEventCounter("dlt", eventType);

            log.warn("Event DLT tracked: eventId={}, eventType={}", eventId, eventType);

        } catch (Exception e) {
            log.error("Failed to track event DLT: eventId={}, eventType={}", eventId, eventType, e);
            // Non-critical - event already in DLT
        }
    }

    // ========== Private Helper Methods ==========

    /**
     * Find event processing record by event ID (without type for convenience)
     */
    private Optional<EventProcessingRecord> findRecordByEventId(String eventId) {
        // This is a simplified lookup - in production you might need to search by ID pattern
        // or pass event type explicitly
        return eventProcessingRecordRepository.findAll().stream()
                .filter(r -> r.getEventId().equals(eventId))
                .findFirst();
    }

    /**
     * Convert object map to string map for metadata storage
     */
    private Map<String, String> convertToStringMap(Map<String, Object> objectMap) {
        Map<String, String> stringMap = new HashMap<>();
        if (objectMap != null) {
            objectMap.forEach((key, value) ->
                    stringMap.put(key, value != null ? value.toString() : "null"));
        }
        return stringMap;
    }

    /**
     * Truncate string to max length
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * Increment event processing counter metric
     */
    private void incrementEventCounter(String status, String eventType) {
        Counter.builder(METRIC_PREFIX + ".count")
                .tag("status", status)
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();
    }
}
