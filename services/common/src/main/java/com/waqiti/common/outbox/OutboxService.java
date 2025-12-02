package com.waqiti.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.resilience.CircuitBreakerService;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Outbox Pattern Implementation for reliable event publishing
 * Ensures at-least-once delivery semantics for distributed events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerService circuitBreakerService;
    
    // Configurable parameters
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 100;
    private static final long LOCK_TIMEOUT_MINUTES = 5;
    private static final long CLEANUP_RETENTION_DAYS = 30;
    
    // Metrics tracking
    private final Map<String, OutboxMetrics> metricsMap = new ConcurrentHashMap<>();
    
    // Processing state
    private final Map<String, ProcessingContext> activeProcessing = new ConcurrentHashMap<>();
    private final ExecutorService processingExecutor = Executors.newWorkStealingPool();
    
    /**
     * Save event to outbox within the same transaction
     */
    @Transactional
    public OutboxEvent saveEvent(String aggregateId, String aggregateType, String eventType, 
                                 Object payload, Map<String, String> headers) {
        String eventId = UUID.randomUUID().toString();
        
        log.debug("Saving outbox event: eventId={}, aggregateId={}, eventType={}", 
            eventId, aggregateId, eventType);
        
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String headersJson = headers != null ? objectMapper.writeValueAsString(headers) : "{}";
            
            jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    event_id, aggregate_id, aggregate_type, event_type, 
                    payload, headers, status, created_at, version
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """,
                eventId, aggregateId, aggregateType, eventType,
                payloadJson, headersJson, OutboxStatus.PENDING.name(),
                Instant.now(), 0
            );
            
            OutboxEvent event = OutboxEvent.builder()
                .eventId(eventId)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .payload(payloadJson)
                .headers(headers)
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();
            
            recordMetrics(eventType, "saved");
            
            return event;
            
        } catch (Exception e) {
            log.error("Failed to save outbox event: aggregateId={}, eventType={}", 
                aggregateId, eventType, e);
            throw new OutboxException("Failed to save outbox event", e);
        }
    }
    
    /**
     * Process pending outbox events (scheduled task)
     */
    @Scheduled(fixedDelay = 5000) // Run every 5 seconds
    public void processPendingEvents() {
        String processingId = UUID.randomUUID().toString();
        
        log.debug("Starting outbox processing: processingId={}", processingId);
        
        try {
            // Get pending events with optimistic locking
            List<OutboxEvent> pendingEvents = getPendingEvents(BATCH_SIZE);
            
            if (pendingEvents.isEmpty()) {
                return;
            }
            
            log.info("Processing {} pending outbox events", pendingEvents.size());
            
            ProcessingContext context = new ProcessingContext(processingId, pendingEvents.size());
            activeProcessing.put(processingId, context);
            
            // Process events in parallel
            List<CompletableFuture<ProcessingResult>> futures = pendingEvents.stream()
                .map(event -> processEventAsync(event))
                .collect(Collectors.toList());
            
            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList()))
                .thenAccept(results -> {
                    // Update metrics
                    long successCount = results.stream().filter(ProcessingResult::isSuccess).count();
                    long failureCount = results.size() - successCount;
                    
                    context.complete(successCount, failureCount);
                    
                    log.info("Outbox processing completed: processingId={}, success={}, failure={}", 
                        processingId, successCount, failureCount);
                })
                .exceptionally(throwable -> {
                    log.error("Outbox processing failed: processingId={}", processingId, throwable);
                    context.fail(throwable.getMessage());
                    return null;
                })
                .whenComplete((result, throwable) -> {
                    activeProcessing.remove(processingId);
                });
                
        } catch (Exception e) {
            log.error("Error in outbox processing: processingId={}", processingId, e);
            activeProcessing.remove(processingId);
        }
    }
    
    /**
     * Process single event asynchronously
     */
    private CompletableFuture<ProcessingResult> processEventAsync(OutboxEvent event) {
        return CompletableFuture.supplyAsync(() -> {
            String eventId = event.getEventId();
            
            try {
                // Lock event for processing
                if (!lockEvent(eventId)) {
                    log.debug("Event already locked: eventId={}", eventId);
                    return ProcessingResult.skipped(eventId);
                }
                
                // Publish event with circuit breaker
                boolean published = circuitBreakerService.executeWithFullResilience(
                    "outbox_publish_" + event.getEventType(),
                    () -> publishEvent(event),
                    () -> false // Fallback to retry later
                );
                
                if (published) {
                    // Mark as published
                    markEventPublished(eventId);
                    recordMetrics(event.getEventType(), "published");
                    
                    log.debug("Event published successfully: eventId={}", eventId);
                    return ProcessingResult.success(eventId);
                    
                } else {
                    // Increment retry count
                    incrementRetryCount(eventId);
                    recordMetrics(event.getEventType(), "retry");
                    
                    log.warn("Event publishing failed, will retry: eventId={}, retryCount={}", 
                        eventId, event.getRetryCount() + 1);
                    
                    return ProcessingResult.retry(eventId);
                }
                
            } catch (Exception e) {
                log.error("Error processing outbox event: eventId={}", eventId, e);
                
                // Mark as failed if max retries exceeded
                if (event.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                    markEventFailed(eventId, e.getMessage());
                    recordMetrics(event.getEventType(), "failed");
                    return ProcessingResult.failed(eventId, e.getMessage());
                } else {
                    incrementRetryCount(eventId);
                    return ProcessingResult.retry(eventId);
                }
                
            } finally {
                // Release lock
                unlockEvent(eventId);
            }
        }, processingExecutor);
    }
    
    /**
     * Publish event to Kafka
     */
    private boolean publishEvent(OutboxEvent event) {
        try {
            // Parse payload
            Object payload = objectMapper.readValue(event.getPayload(), Object.class);
            
            // Determine topic based on event type
            String topic = determineTopicForEvent(event);
            
            // Create Kafka message with headers using ProducerRecord
            org.apache.kafka.clients.producer.ProducerRecord<String, Object> record = 
                new org.apache.kafka.clients.producer.ProducerRecord<>(topic, event.getAggregateId(), payload);
            
            if (event.getHeaders() != null) {
                event.getHeaders().forEach((key, value) -> 
                    record.headers().add(key, value.getBytes()));
            }
            
            // Add outbox metadata
            record.headers().add("outbox_event_id", event.getEventId().getBytes());
            record.headers().add("outbox_aggregate_id", event.getAggregateId().getBytes());
            record.headers().add("outbox_event_type", event.getEventType().getBytes());
            record.headers().add("outbox_timestamp", event.getCreatedAt().toString().getBytes());
            
            // Send to Kafka
            kafkaTemplate.send(record)
                .get(5, TimeUnit.SECONDS); // Wait for confirmation
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to publish event to Kafka: eventId={}", event.getEventId(), e);
            return false;
        }
    }
    
    /**
     * Get pending events for processing
     */
    private List<OutboxEvent> getPendingEvents(int limit) {
        String sql = """
            SELECT event_id, aggregate_id, aggregate_type, event_type, 
                   payload, headers, status, created_at, retry_count, 
                   locked_until, error_message, version
            FROM outbox_events
            WHERE status IN (?, ?)
              AND retry_count < ?
              AND (locked_until IS NULL OR locked_until < ?)
            ORDER BY created_at ASC
            LIMIT ?
            """;
        
        return jdbcTemplate.query(sql,
            new OutboxEventRowMapper(),
            OutboxStatus.PENDING.name(),
            OutboxStatus.RETRY.name(),
            MAX_RETRY_ATTEMPTS,
            Instant.now(),
            limit
        );
    }
    
    /**
     * Lock event for processing (optimistic locking)
     */
    private boolean lockEvent(String eventId) {
        String sql = """
            UPDATE outbox_events 
            SET locked_until = ?, 
                version = version + 1
            WHERE event_id = ? 
              AND (locked_until IS NULL OR locked_until < ?)
            """;
        
        int updated = jdbcTemplate.update(sql,
            Instant.now().plusSeconds(LOCK_TIMEOUT_MINUTES * 60),
            eventId,
            Instant.now()
        );
        
        return updated > 0;
    }
    
    /**
     * Release event lock
     */
    private void unlockEvent(String eventId) {
        String sql = "UPDATE outbox_events SET locked_until = NULL WHERE event_id = ?";
        jdbcTemplate.update(sql, eventId);
    }
    
    /**
     * Mark event as published
     */
    @Transactional
    private void markEventPublished(String eventId) {
        String sql = """
            UPDATE outbox_events 
            SET status = ?, 
                published_at = ?,
                version = version + 1
            WHERE event_id = ?
            """;
        
        jdbcTemplate.update(sql,
            OutboxStatus.PUBLISHED.name(),
            Instant.now(),
            eventId
        );
    }
    
    /**
     * Mark event as failed
     */
    @Transactional
    private void markEventFailed(String eventId, String errorMessage) {
        String sql = """
            UPDATE outbox_events 
            SET status = ?, 
                error_message = ?,
                version = version + 1
            WHERE event_id = ?
            """;
        
        jdbcTemplate.update(sql,
            OutboxStatus.FAILED.name(),
            errorMessage,
            eventId
        );
    }
    
    /**
     * Increment retry count
     */
    @Transactional
    private void incrementRetryCount(String eventId) {
        String sql = """
            UPDATE outbox_events 
            SET retry_count = retry_count + 1,
                status = ?,
                last_retry_at = ?,
                version = version + 1
            WHERE event_id = ?
            """;
        
        jdbcTemplate.update(sql,
            OutboxStatus.RETRY.name(),
            Instant.now(),
            eventId
        );
    }
    
    /**
     * Clean up old published events (scheduled task)
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    public void cleanupOldEvents() {
        log.info("Starting outbox cleanup");
        
        try {
            String sql = """
                DELETE FROM outbox_events 
                WHERE status = ? 
                  AND published_at < ?
                """;
            
            Instant cutoffTime = Instant.now().minusSeconds(CLEANUP_RETENTION_DAYS * 24 * 60 * 60);
            
            int deleted = jdbcTemplate.update(sql,
                OutboxStatus.PUBLISHED.name(),
                cutoffTime
            );
            
            log.info("Cleaned up {} old outbox events", deleted);
            recordMetrics("cleanup", "completed");
            
        } catch (Exception e) {
            log.error("Failed to cleanup old outbox events", e);
        }
    }
    
    /**
     * Retry failed events manually
     */
    public CompletableFuture<RetryResult> retryFailedEvents(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = """
                    UPDATE outbox_events 
                    SET status = ?, 
                        retry_count = 0,
                        error_message = NULL,
                        version = version + 1
                    WHERE status = ?
                    LIMIT ?
                    """;
                
                int updated = jdbcTemplate.update(sql,
                    OutboxStatus.PENDING.name(),
                    OutboxStatus.FAILED.name(),
                    limit
                );
                
                log.info("Reset {} failed events for retry", updated);
                
                return RetryResult.success(updated);
                
            } catch (Exception e) {
                log.error("Failed to retry failed events", e);
                return RetryResult.failure(e.getMessage());
            }
        });
    }
    
    /**
     * Get outbox metrics
     */
    public OutboxStatistics getStatistics() {
        try {
            String sql = """
                SELECT 
                    status,
                    COUNT(*) as count,
                    MIN(created_at) as oldest_event,
                    MAX(created_at) as newest_event,
                    AVG(retry_count) as avg_retry_count
                FROM outbox_events
                GROUP BY status
                """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            return OutboxStatistics.fromQueryResults(results);
            
        } catch (Exception e) {
            log.error("Failed to get outbox statistics", e);
            return OutboxStatistics.empty();
        }
    }
    
    /**
     * Get processing status
     */
    public List<ProcessingStatus> getActiveProcessing() {
        return activeProcessing.values().stream()
            .map(ctx -> ProcessingStatus.builder()
                .processingId(ctx.getProcessingId())
                .startTime(ctx.getStartTime())
                .totalEvents(ctx.getTotalEvents())
                .processedEvents(ctx.getProcessedEvents())
                .status(ctx.getStatus())
                .build())
            .collect(Collectors.toList());
    }
    
    // Helper methods
    
    private String determineTopicForEvent(OutboxEvent event) {
        // Map event types to Kafka topics
        return switch (event.getAggregateType()) {
            case "Transaction" -> "transaction-events";
            case "Payment" -> "payment-events";
            case "Account" -> "account-events";
            case "User" -> "user-events";
            default -> "domain-events";
        };
    }
    
    private void recordMetrics(String eventType, String operation) {
        OutboxMetrics metrics = metricsMap.computeIfAbsent(eventType, 
            k -> new OutboxMetrics(eventType));
        
        metrics.recordOperation(operation);
    }
    
    // Data classes
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutboxEvent {
        private String eventId;
        private String aggregateId;
        private String aggregateType;
        private String eventType;
        private String payload;
        private Map<String, String> headers;
        private OutboxStatus status;
        private Instant createdAt;
        private Instant publishedAt;
        private Instant lockedUntil;
        private int retryCount;
        private String errorMessage;
        private int version;
    }
    
    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED,
        RETRY
    }
    
    @Data
    @AllArgsConstructor
    private static class ProcessingResult {
        private String eventId;
        private boolean success;
        private boolean retry;
        private String error;
        
        public static ProcessingResult success(String eventId) {
            return new ProcessingResult(eventId, true, false, null);
        }
        
        public static ProcessingResult retry(String eventId) {
            return new ProcessingResult(eventId, false, true, null);
        }
        
        public static ProcessingResult failed(String eventId, String error) {
            return new ProcessingResult(eventId, false, false, error);
        }
        
        public static ProcessingResult skipped(String eventId) {
            return new ProcessingResult(eventId, false, false, "Skipped");
        }
    }
    
    @Data
    private static class ProcessingContext {
        private final String processingId;
        private final int totalEvents;
        private final Instant startTime;
        private int processedEvents = 0;
        private int successCount = 0;
        private int failureCount = 0;
        private String status = "PROCESSING";
        
        public ProcessingContext(String processingId, int totalEvents) {
            this.processingId = processingId;
            this.totalEvents = totalEvents;
            this.startTime = Instant.now();
        }
        
        public synchronized void complete(long success, long failure) {
            this.successCount = (int) success;
            this.failureCount = (int) failure;
            this.processedEvents = successCount + failureCount;
            this.status = "COMPLETED";
        }
        
        public synchronized void fail(String error) {
            this.status = "FAILED: " + error;
        }
    }
    
    @Data
    @Builder
    public static class ProcessingStatus {
        private String processingId;
        private Instant startTime;
        private int totalEvents;
        private int processedEvents;
        private String status;
    }
    
    @Data
    @Builder
    public static class RetryResult {
        private boolean success;
        private int eventsReset;
        private String error;
        
        public static RetryResult success(int eventsReset) {
            return RetryResult.builder()
                .success(true)
                .eventsReset(eventsReset)
                .build();
        }
        
        public static RetryResult failure(String error) {
            return RetryResult.builder()
                .success(false)
                .error(error)
                .build();
        }
    }
    
    @Data
    @Builder
    public static class OutboxStatistics {
        private long pendingCount;
        private long publishedCount;
        private long failedCount;
        private long retryCount;
        private double averageRetryCount;
        private Instant oldestPendingEvent;
        private Instant newestEvent;
        
        public static OutboxStatistics fromQueryResults(List<Map<String, Object>> results) {
            OutboxStatistics.OutboxStatisticsBuilder builder = OutboxStatistics.builder();
            
            for (Map<String, Object> row : results) {
                String status = (String) row.get("status");
                Long count = (Long) row.get("count");
                
                switch (OutboxStatus.valueOf(status)) {
                    case PENDING -> builder.pendingCount(count);
                    case PUBLISHED -> builder.publishedCount(count);
                    case FAILED -> builder.failedCount(count);
                    case RETRY -> builder.retryCount(count);
                }
            }
            
            return builder.build();
        }
        
        public static OutboxStatistics empty() {
            return OutboxStatistics.builder().build();
        }
    }
    
    private static class OutboxMetrics {
        private final String eventType;
        private final Map<String, Long> operationCounts = new ConcurrentHashMap<>();
        
        public OutboxMetrics(String eventType) {
            this.eventType = eventType;
        }
        
        public void recordOperation(String operation) {
            operationCounts.merge(operation, 1L, Long::sum);
        }
        
        public Map<String, Long> getCounts() {
            return new HashMap<>(operationCounts);
        }
    }
    
    private static class OutboxEventRowMapper implements RowMapper<OutboxEvent> {
        @Override
        public OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
            return OutboxEvent.builder()
                .eventId(rs.getString("event_id"))
                .aggregateId(rs.getString("aggregate_id"))
                .aggregateType(rs.getString("aggregate_type"))
                .eventType(rs.getString("event_type"))
                .payload(rs.getString("payload"))
                .status(OutboxStatus.valueOf(rs.getString("status")))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .retryCount(rs.getInt("retry_count"))
                .errorMessage(rs.getString("error_message"))
                .version(rs.getInt("version"))
                .build();
        }
    }
    
    public static class OutboxException extends RuntimeException {
        public OutboxException(String message) {
            super(message);
        }
        
        public OutboxException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}