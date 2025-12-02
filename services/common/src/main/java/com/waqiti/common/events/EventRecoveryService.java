package com.waqiti.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.model.DeadLetterQueueEntry;
import com.waqiti.common.events.model.DeadLetterQueueEntry.DLQStatus;
import com.waqiti.common.events.model.DLQAlert;
import com.waqiti.common.events.model.DLQStatistics;
import com.waqiti.common.events.model.EventOutboxEntry;
import com.waqiti.common.events.model.EventStatus;
import com.waqiti.common.events.repository.DeadLetterQueueRepository;
import com.waqiti.common.events.repository.EventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * CRITICAL EVENT-DRIVEN ARCHITECTURE: Event Recovery and Dead Letter Queue Service
 * PRODUCTION-READY: Handles event failures, retries, and dead letter processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventRecoveryService {
    
    private final EventOutboxRepository eventOutboxRepository;
    private final DeadLetterQueueRepository deadLetterRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MINUTES = 5;
    private static final int DLQ_RETENTION_DAYS = 30;
    
    /**
     * CRITICAL: Process failed events on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void processFailedEventsOnStartup() {
        log.info("EVENT_RECOVERY: Processing failed events on startup");
        
        try {
            List<EventOutboxEntry> failedEvents = eventOutboxRepository.findFailedEvents();
            log.info("EVENT_RECOVERY: Found {} failed events to retry", failedEvents.size());
            
            for (EventOutboxEntry event : failedEvents) {
                processFailedEvent(event);
            }
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error processing failed events on startup", e);
        }
    }
    
    /**
     * CRITICAL: Scheduled retry of failed events
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void retryFailedEvents() {
        try {
            LocalDateTime retryAfter = LocalDateTime.now().minusMinutes(RETRY_DELAY_MINUTES);
            List<EventOutboxEntry> retryableEvents = eventOutboxRepository
                    .findRetryableEvents(MAX_RETRY_ATTEMPTS, retryAfter);
            
            if (!retryableEvents.isEmpty()) {
                log.info("EVENT_RECOVERY: Retrying {} failed events", retryableEvents.size());
                
                for (EventOutboxEntry event : retryableEvents) {
                    processFailedEvent(event);
                }
            }
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error during scheduled retry of failed events", e);
        }
    }
    
    /**
     * CRITICAL: Process individual failed event
     */
    @Transactional
    public void processFailedEvent(EventOutboxEntry event) {
        try {
            log.debug("EVENT_RECOVERY: Processing failed event: {}", event.getEventId());
            
            // Increment retry count
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastRetryAt(LocalDateTime.now());
            
            // Attempt to republish event
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                event.getTopic(),
                event.getPartitionKey(),
                event.getEventData()
            );
            
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    handleEventRetryFailure(event, ex);
                } else {
                    handleEventRetrySuccess(event, result);
                }
            });
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error processing failed event: {}", event.getEventId(), e);
            handleEventRetryFailure(event, e);
        }
    }
    
    /**
     * Handle successful event retry
     */
    private void handleEventRetrySuccess(EventOutboxEntry event, SendResult<String, String> result) {
        try {
            log.info("EVENT_RECOVERY: Successfully retried event: {} to topic: {} partition: {}", 
                    event.getEventId(), result.getRecordMetadata().topic(), 
                    result.getRecordMetadata().partition());
            
            // Mark event as successfully processed
            event.setStatus(EventOutboxEntry.EventStatus.SENT);
            event.setProcessedAt(LocalDateTime.now());
            eventOutboxRepository.save(event);
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error updating successful retry status", e);
        }
    }
    
    /**
     * Handle failed event retry
     */
    private void handleEventRetryFailure(EventOutboxEntry event, Throwable ex) {
        try {
            log.warn("EVENT_RECOVERY: Retry failed for event: {} (attempt {})", 
                    event.getEventId(), event.getRetryCount(), ex);
            
            if (event.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                // Move to dead letter queue
                moveToDeadLetterQueue(event, ex);
            } else {
                // Update retry information
                event.setStatus(EventOutboxEntry.EventStatus.RETRY_SCHEDULED);
                event.setLastError(ex.getMessage());
                event.setNextRetryAt(LocalDateTime.now().plusMinutes(
                    RETRY_DELAY_MINUTES * event.getRetryCount())); // Exponential backoff
            }
            
            eventOutboxRepository.save(event);
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error handling retry failure", e);
        }
    }
    
    /**
     * CRITICAL: Move event to dead letter queue
     */
    @Transactional
    public void moveToDeadLetterQueue(EventOutboxEntry event, Throwable error) {
        try {
            log.error("EVENT_RECOVERY: Moving event to DLQ after {} retries: {}", 
                    event.getRetryCount(), event.getEventId());
            
            DeadLetterQueueEntry dlqEntry = DeadLetterQueueEntry.builder()
                    .id(UUID.randomUUID())
                    .originalEventId(event.getEventId())
                    .topic(event.getTopic())
                    .partitionKey(event.getPartitionKey())
                    .eventData(event.getEventData())
                    .eventType(event.getEventType())
                    .aggregateId(event.getAggregateId())
                    .aggregateType(event.getAggregateType())
                    .originalCreatedAt(event.getCreatedAt())
                    .failureReason(error.getMessage())
                    .retryCount(event.getRetryCount())
                    .createdAt(LocalDateTime.now())
                    .status(DLQStatus.NEW)
                    .build();
            
            deadLetterRepository.save(dlqEntry);

            // Update original event status
            event.setStatus(EventOutboxEntry.EventStatus.DEAD_LETTER);
            event.setProcessedAt(LocalDateTime.now());
            eventOutboxRepository.save(event);
            
            // Alert monitoring system
            publishDLQAlert(dlqEntry);
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error moving event to DLQ", e);
        }
    }
    
    /**
     * CRITICAL: Replay events from dead letter queue
     */
    @Transactional
    public boolean replayFromDeadLetterQueue(UUID dlqEntryId) {
        try {
            DeadLetterQueueEntry dlqEntry = deadLetterRepository.findById(dlqEntryId)
                    .orElseThrow(() -> new IllegalArgumentException("DLQ entry not found: " + dlqEntryId));
            
            log.info("EVENT_RECOVERY: Replaying event from DLQ: {}", dlqEntry.getId());
            
            // Create new outbox entry for replay
            EventOutboxEntry replayEvent = EventOutboxEntry.builder()
                    .eventId(UUID.randomUUID().toString()) // New event ID for replay
                    .topic(dlqEntry.getTopic())
                    .partitionKey(dlqEntry.getPartitionKey())
                    .payload(dlqEntry.getEventData())
                    .eventType(dlqEntry.getEventType())
                    .aggregateId(dlqEntry.getAggregateId())
                    .aggregateType(dlqEntry.getAggregateType())
                    .createdAt(LocalDateTime.now())
                    .status(EventOutboxEntry.EventStatus.PENDING)
                    .retryCount(0)
                    .headers("REPLAYED_FROM_DLQ:" + dlqEntry.getId())
                    .build();
            
            eventOutboxRepository.save(replayEvent);
            
            // Attempt to send immediately
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                replayEvent.getTopic(),
                replayEvent.getPartitionKey(),
                replayEvent.getEventData()
            );
            
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("EVENT_RECOVERY: DLQ replay failed, will retry: {}", replayEvent.getEventId());
                    replayEvent.setStatus(EventOutboxEntry.EventStatus.FAILED);
                    replayEvent.setLastError(ex.getMessage());
                } else {
                    log.info("EVENT_RECOVERY: DLQ replay successful: {}", replayEvent.getEventId());
                    replayEvent.setStatus(EventOutboxEntry.EventStatus.SENT);
                    replayEvent.setProcessedAt(LocalDateTime.now());
                    
                    // Mark DLQ entry as replayed
                    dlqEntry.setStatus(DLQStatus.REPLAYED);
                    dlqEntry.setReplayedAt(LocalDateTime.now());
                    deadLetterRepository.save(dlqEntry);
                }
                eventOutboxRepository.save(replayEvent);
            });
            
            return true;
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error replaying from DLQ: {}", dlqEntryId, e);
            return false;
        }
    }
    
    /**
     * CRITICAL: Bulk replay events from DLQ
     */
    @Transactional
    public int replayAllFromDeadLetterQueue(String eventType, LocalDateTime since) {
        try {
            List<DeadLetterQueueEntry> dlqEntries = deadLetterRepository
                    .findReplayableEvents(eventType, since);
            
            log.info("EVENT_RECOVERY: Bulk replaying {} events from DLQ", dlqEntries.size());
            
            int successCount = 0;
            for (DeadLetterQueueEntry dlqEntry : dlqEntries) {
                if (replayFromDeadLetterQueue(dlqEntry.getId())) {
                    successCount++;
                }
            }
            
            log.info("EVENT_RECOVERY: Successfully queued {} events for replay", successCount);
            return successCount;
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error during bulk DLQ replay", e);
            return 0;
        }
    }
    
    /**
     * CRITICAL: Clean up old DLQ entries
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void cleanupOldDLQEntries() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(DLQ_RETENTION_DAYS);
            int deletedCount = deadLetterRepository.deleteOldEntries(cutoffDate);
            
            if (deletedCount > 0) {
                log.info("EVENT_RECOVERY: Cleaned up {} old DLQ entries", deletedCount);
            }
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error during DLQ cleanup", e);
        }
    }
    
    /**
     * CRITICAL: Event ordering validation
     */
    public boolean validateEventOrdering(String aggregateId, long expectedVersion) {
        try {
            Long latestVersion = eventOutboxRepository.getLatestVersionForAggregate(aggregateId)
                    .orElse(null);

            if (latestVersion == null) {
                return expectedVersion == 1; // First event
            }

            return expectedVersion == latestVersion + 1;
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error validating event ordering", e);
            return false;
        }
    }
    
    /**
     * CRITICAL: Get DLQ statistics for monitoring
     * Industrial-grade implementation with comprehensive metrics collection
     */
    public DLQStatistics getDLQStatistics() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime twentyFourHoursAgo = now.minusHours(24);
            LocalDateTime sevenDaysAgo = now.minusDays(7);
            LocalDateTime thirtyDaysAgo = now.minusDays(30);
            
            // Collect comprehensive statistics
            long totalEntries = deadLetterRepository.count();
            long unprocessedEntries = deadLetterRepository.countByStatus(DLQStatus.UNPROCESSED.name());
            long pendingRetryEntries = deadLetterRepository.countByStatus(DLQStatus.PENDING_RETRY.name());
            long resolvedEntries = deadLetterRepository.countByStatus(DLQStatus.RESOLVED.name());
            long discardedEntries = deadLetterRepository.countByStatus(DLQStatus.DISCARDED.name());
            long manualReviewEntries = deadLetterRepository.countByStatus(DLQStatus.MANUAL_REVIEW.name());
            
            // Calculate time-based statistics
            List<DeadLetterQueueEntry> recentEntries = deadLetterRepository.findByCreatedAtBetween(twentyFourHoursAgo, now);
            List<DeadLetterQueueEntry> weekEntries = deadLetterRepository.findByCreatedAtBetween(sevenDaysAgo, now);
            List<DeadLetterQueueEntry> monthEntries = deadLetterRepository.findByCreatedAtBetween(thirtyDaysAgo, now);
            
            // Calculate resolution rates
            double resolutionRate = totalEntries > 0 
                ? (double) resolvedEntries / totalEntries * 100.0 
                : 0.0;
            
            double successfulRetryRate = pendingRetryEntries + resolvedEntries > 0
                ? (double) resolvedEntries / (pendingRetryEntries + resolvedEntries) * 100.0
                : 0.0;
            
            // Efficiently aggregate metrics using repository queries
            List<Object[]> topicCounts = deadLetterRepository.countEntriesByTopic();
            Map<String, Long> entriesByTopic = topicCounts.stream()
                .collect(Collectors.toMap(
                    row -> (String) row[0], 
                    row -> ((Number) row[1]).longValue(),
                    (existing, replacement) -> existing
                ));
                
            List<Object[]> errorTypeCounts = deadLetterRepository.countEntriesByErrorType();
            Map<String, Long> entriesByErrorType = errorTypeCounts.stream()
                .collect(Collectors.toMap(
                    row -> row[0] != null ? (String) row[0] : "UNKNOWN", 
                    row -> ((Number) row[1]).longValue(),
                    (existing, replacement) -> existing
                ));
            
            // Build status distribution map
            Map<String, Long> entriesByStatus = Map.of(
                DLQStatus.UNPROCESSED.name(), unprocessedEntries,
                DLQStatus.PENDING_RETRY.name(), pendingRetryEntries,
                DLQStatus.RESOLVED.name(), resolvedEntries,
                DLQStatus.DISCARDED.name(), discardedEntries,
                DLQStatus.MANUAL_REVIEW.name(), manualReviewEntries
            );
            
            // Get time boundaries for oldest/newest entries
            LocalDateTime oldestEntryTime = deadLetterRepository.findOldestUnprocessedEntryTime();
            LocalDateTime newestEntryTime = !recentEntries.isEmpty() 
                ? recentEntries.stream()
                    .map(DeadLetterQueueEntry::getCreatedAt)
                    .max(LocalDateTime::compareTo)
                    .orElse(null)
                : null;
            
            // Calculate retry statistics efficiently from recent entries sample
            double avgRetryCount = recentEntries.stream()
                .mapToInt(DeadLetterQueueEntry::getRetryCount)
                .average()
                .orElse(0.0);
                
            long maxRetryCount = recentEntries.stream()
                .mapToInt(DeadLetterQueueEntry::getRetryCount)
                .max()
                .orElse(0);
            
            // Build comprehensive industrial-grade statistics
            return DLQStatistics.builder()
                    .totalEntries(totalEntries)
                    .unprocessedEntries(unprocessedEntries)
                    .pendingRetryEntries(pendingRetryEntries)
                    .resolvedEntries(resolvedEntries)
                    .discardedEntries(discardedEntries)
                    .manualReviewEntries(manualReviewEntries)
                    .entriesByTopic(entriesByTopic)
                    .entriesByErrorType(entriesByErrorType)
                    .entriesByStatus(entriesByStatus)
                    .entriesLast24Hours((long) recentEntries.size())
                    .entriesLast7Days((long) weekEntries.size())
                    .entriesLast30Days((long) monthEntries.size())
                    .oldestEntry(oldestEntryTime)
                    .newestEntry(newestEntryTime)
                    .averageRetryCount(avgRetryCount)
                    .maxRetryCount(maxRetryCount)
                    .resolutionRate(resolutionRate)
                    .successfulRetryRate(successfulRetryRate)
                    .statisticsGeneratedAt(now)
                    .build();
                    
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error generating comprehensive DLQ statistics", e);
            // Return minimal statistics on error to ensure monitoring continues
            return DLQStatistics.builder()
                    .totalEntries(0L)
                    .statisticsGeneratedAt(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Publish alert for DLQ entry
     */
    private void publishDLQAlert(DeadLetterQueueEntry dlqEntry) {
        try {
            DLQAlert alert = DLQAlert.builder()
                    .alertId(UUID.randomUUID())
                    .dlqEntryId(dlqEntry.getId())
                    .eventType(dlqEntry.getEventType())
                    .aggregateType(dlqEntry.getAggregateType())
                    .aggregateId(dlqEntry.getAggregateId())
                    .failureReason(dlqEntry.getFailureReason())
                    .retryCount(dlqEntry.getRetryCount())
                    .originalCreatedAt(dlqEntry.getOriginalCreatedAt())
                    .alertLevel("HIGH")
                    .createdAt(Instant.now())
                    .build();
            
            String alertJson = objectMapper.writeValueAsString(alert);
            kafkaTemplate.send("system.alerts.dlq", alert.getAlertId().toString(), alertJson);
            
            log.warn("EVENT_RECOVERY: DLQ alert published for event: {}", dlqEntry.getOriginalEventId());
            
        } catch (Exception e) {
            log.error("EVENT_RECOVERY: Error publishing DLQ alert", e);
        }
    }
}