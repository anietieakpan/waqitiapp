package com.waqiti.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for tracking Kafka event processing and consumer performance
 * Provides comprehensive monitoring and audit capabilities
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaEventTrackingService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;
    
    // Performance counters
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong totalEventsFailed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);

    /**
     * Log successfully processed Kafka event
     */
    @Transactional
    public void logEventProcessed(ConsumerRecord<String, Object> record, 
                                 String consumerName,
                                 long processingTimeMs) {
        try {
            String eventId = generateEventId(record);
            
            Map<String, Object> params = new HashMap<>();
            params.put("eventId", eventId);
            params.put("eventType", extractEventType(record));
            params.put("topicName", record.topic());
            params.put("partitionId", record.partition());
            params.put("offsetValue", record.offset());
            params.put("consumerGroup", extractConsumerGroup(record));
            params.put("payload", convertToJson(record.value()));
            params.put("headers", convertHeadersToJson(record));
            params.put("processingDurationMs", processingTimeMs);
            params.put("status", "PROCESSED");
            params.put("retryCount", 0);
            
            String sql = """
                INSERT INTO event_tracking.kafka_event_log 
                (event_id, event_type, topic_name, partition_id, offset_value, consumer_group, 
                 payload, headers, processing_duration_ms, status, retry_count)
                VALUES (:eventId, :eventType, :topicName, :partitionId, :offsetValue, :consumerGroup,
                        :payload::jsonb, :headers::jsonb, :processingDurationMs, :status, :retryCount)
                """;
            
            namedParameterJdbcTemplate.update(sql, params);
            
            // Update consumer metrics asynchronously
            CompletableFuture.runAsync(() -> updateConsumerMetrics(consumerName, record.topic(), 
                                                                 processingTimeMs, true));
            
            // Update performance counters
            totalEventsProcessed.incrementAndGet();
            totalProcessingTimeMs.addAndGet(processingTimeMs);
            
            log.debug("Event logged successfully: {} from topic: {}", eventId, record.topic());
            
        } catch (DataAccessException e) {
            log.error("Failed to log processed event from topic: {}", record.topic(), e);
            // Don't rethrow - tracking failure shouldn't break event processing
        }
    }

    /**
     * Log failed event processing
     */
    @Transactional
    public void logEventFailed(ConsumerRecord<String, Object> record, 
                              String consumerName,
                              Exception exception,
                              int retryCount) {
        try {
            String eventId = generateEventId(record);
            
            Map<String, Object> params = new HashMap<>();
            params.put("eventId", eventId);
            params.put("eventType", extractEventType(record));
            params.put("topicName", record.topic());
            params.put("partitionId", record.partition());
            params.put("offsetValue", record.offset());
            params.put("consumerGroup", extractConsumerGroup(record));
            params.put("payload", convertToJson(record.value()));
            params.put("headers", convertHeadersToJson(record));
            params.put("status", "FAILED");
            params.put("errorMessage", exception.getMessage());
            params.put("retryCount", retryCount);
            
            String sql = """
                INSERT INTO event_tracking.kafka_event_log 
                (event_id, event_type, topic_name, partition_id, offset_value, consumer_group, 
                 payload, headers, status, error_message, retry_count)
                VALUES (:eventId, :eventType, :topicName, :partitionId, :offsetValue, :consumerGroup,
                        :payload::jsonb, :headers::jsonb, :status, :errorMessage, :retryCount)
                """;
            
            namedParameterJdbcTemplate.update(sql, params);
            
            // Log processing error
            logProcessingError(record, exception);
            
            // Update consumer metrics asynchronously
            CompletableFuture.runAsync(() -> updateConsumerMetrics(consumerName, record.topic(), 
                                                                 0, false));
            
            // Update performance counters
            totalEventsFailed.incrementAndGet();
            
            log.debug("Failed event logged: {} from topic: {}", eventId, record.topic());
            
        } catch (DataAccessException e) {
            log.error("Failed to log failed event from topic: {}", record.topic(), e);
        }
    }

    /**
     * Log DLQ event
     */
    @Transactional
    public void logDlqEvent(DlqEvent dlqEvent) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("originalEventId", dlqEvent.getOriginalEventId());
            params.put("dlqEventId", dlqEvent.getDlqEventId());
            params.put("sourceTopic", dlqEvent.getOriginalTopic());
            params.put("dlqTopic", dlqEvent.getDlqTopic());
            params.put("consumerGroup", dlqEvent.getConsumerGroup());
            params.put("dlqReason", dlqEvent.getErrorMessage());
            params.put("originalPayload", convertToJson(dlqEvent.getValue()));
            params.put("errorDetails", buildErrorDetails(dlqEvent));
            params.put("retryCount", dlqEvent.getRetryCount());
            params.put("maxRetries", 3);
            params.put("dlqStatus", "PENDING");
            
            String sql = """
                INSERT INTO event_tracking.dlq_events 
                (original_event_id, dlq_event_id, source_topic, dlq_topic, consumer_group, 
                 dlq_reason, original_payload, error_details, retry_count, max_retries, dlq_status)
                VALUES (:originalEventId, :dlqEventId, :sourceTopic, :dlqTopic, :consumerGroup,
                        :dlqReason, :originalPayload::jsonb, :errorDetails::jsonb, :retryCount, 
                        :maxRetries, :dlqStatus)
                """;
            
            namedParameterJdbcTemplate.update(sql, params);
            
            log.info("DLQ event logged: {} for original topic: {}", 
                    dlqEvent.getDlqEventId(), dlqEvent.getOriginalTopic());
            
        } catch (DataAccessException e) {
            log.error("Failed to log DLQ event for topic: {}", dlqEvent.getOriginalTopic(), e);
        }
    }

    /**
     * Log DLQ processing error
     */
    public void logDlqProcessingError(ConsumerRecord<String, Object> record, Exception exception) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("eventId", generateEventId(record));
            params.put("consumerName", "DlqProcessor");
            params.put("topicName", record.topic());
            params.put("errorType", exception.getClass().getSimpleName());
            params.put("errorMessage", exception.getMessage());
            params.put("stackTrace", getStackTrace(exception));
            params.put("eventPayload", convertToJson(record.value()));
            params.put("processingContext", buildProcessingContext(record));
            
            String sql = """
                INSERT INTO event_tracking.processing_errors 
                (event_id, consumer_name, topic_name, error_type, error_message, stack_trace, 
                 event_payload, processing_context)
                VALUES (:eventId, :consumerName, :topicName, :errorType, :errorMessage, :stackTrace,
                        :eventPayload::jsonb, :processingContext::jsonb)
                ON CONFLICT (event_id, consumer_name, error_type) 
                DO UPDATE SET 
                    error_count = processing_errors.error_count + 1,
                    last_occurrence = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                """;
            
            namedParameterJdbcTemplate.update(sql, params);
            
        } catch (DataAccessException e) {
            log.error("Failed to log DLQ processing error", e);
        }
    }

    /**
     * Log general processing error
     */
    @Transactional
    public void logProcessingError(ConsumerRecord<String, Object> record, Exception exception) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("eventId", generateEventId(record));
            params.put("consumerName", "UnknownConsumer");
            params.put("topicName", record.topic());
            params.put("errorType", exception.getClass().getSimpleName());
            params.put("errorMessage", exception.getMessage());
            params.put("stackTrace", getStackTrace(exception));
            params.put("eventPayload", convertToJson(record.value()));
            params.put("processingContext", buildProcessingContext(record));
            
            String sql = """
                INSERT INTO event_tracking.processing_errors 
                (event_id, consumer_name, topic_name, error_type, error_message, stack_trace, 
                 event_payload, processing_context)
                VALUES (:eventId, :consumerName, :topicName, :errorType, :errorMessage, :stackTrace,
                        :eventPayload::jsonb, :processingContext::jsonb)
                ON CONFLICT (event_id, consumer_name, error_type) 
                DO UPDATE SET 
                    error_count = processing_errors.error_count + 1,
                    last_occurrence = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                """;
            
            namedParameterJdbcTemplate.update(sql, params);
            
        } catch (DataAccessException e) {
            log.error("Failed to log processing error", e);
        }
    }

    /**
     * Update consumer performance metrics
     */
    private void updateConsumerMetrics(String consumerName, String topicName, 
                                     long processingTimeMs, boolean success) {
        try {
            String consumerGroup = consumerName + "-group"; // Default group naming
            
            Map<String, Object> params = new HashMap<>();
            params.put("consumerName", consumerName);
            params.put("topicName", topicName);
            params.put("consumerGroup", consumerGroup);
            params.put("processingTimeMs", processingTimeMs);
            
            String sql;
            if (success) {
                sql = """
                    INSERT INTO event_tracking.consumer_metrics 
                    (consumer_name, topic_name, consumer_group, events_processed_count, 
                     total_processing_time_ms, last_processed_at)
                    VALUES (:consumerName, :topicName, :consumerGroup, 1, :processingTimeMs, CURRENT_TIMESTAMP)
                    ON CONFLICT (consumer_name, topic_name, consumer_group) 
                    DO UPDATE SET 
                        events_processed_count = consumer_metrics.events_processed_count + 1,
                        total_processing_time_ms = consumer_metrics.total_processing_time_ms + :processingTimeMs,
                        avg_processing_time_ms = (consumer_metrics.total_processing_time_ms + :processingTimeMs) / 
                                               (consumer_metrics.events_processed_count + 1),
                        min_processing_time_ms = LEAST(consumer_metrics.min_processing_time_ms, :processingTimeMs),
                        max_processing_time_ms = GREATEST(consumer_metrics.max_processing_time_ms, :processingTimeMs),
                        last_processed_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    """;
            } else {
                sql = """
                    INSERT INTO event_tracking.consumer_metrics 
                    (consumer_name, topic_name, consumer_group, events_failed_count)
                    VALUES (:consumerName, :topicName, :consumerGroup, 1)
                    ON CONFLICT (consumer_name, topic_name, consumer_group) 
                    DO UPDATE SET 
                        events_failed_count = consumer_metrics.events_failed_count + 1,
                        updated_at = CURRENT_TIMESTAMP
                    """;
            }
            
            namedParameterJdbcTemplate.update(sql, params);
            
        } catch (DataAccessException e) {
            log.error("Failed to update consumer metrics for {}/{}", consumerName, topicName, e);
        }
    }

    /**
     * Update consumer health status
     */
    @Transactional
    public void updateConsumerHealth(String consumerName, String instanceId, 
                                   ConsumerHealth health) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("consumerName", consumerName);
            params.put("instanceId", instanceId);
            params.put("healthStatus", health.getStatus().toString());
            params.put("lagMs", health.getLagMilliseconds());
            params.put("processingRate", health.getProcessingRate());
            params.put("errorRate", health.getErrorRate());
            params.put("memoryUsageMb", health.getMemoryUsageMb());
            params.put("cpuUsage", health.getCpuUsagePercent());
            params.put("activeThreads", health.getActiveThreads());
            params.put("healthDetails", convertToJson(health.getDetails()));
            params.put("alertBreached", health.isAlertThresholdBreached());
            
            String sql = """
                INSERT INTO event_tracking.consumer_health 
                (consumer_name, consumer_instance_id, health_status, lag_milliseconds, 
                 processing_rate, error_rate, memory_usage_mb, cpu_usage_percent, 
                 active_threads, health_details, alert_threshold_breached, last_heartbeat)
                VALUES (:consumerName, :instanceId, :healthStatus, :lagMs, :processingRate, 
                        :errorRate, :memoryUsageMb, :cpuUsage, :activeThreads, :healthDetails::jsonb, 
                        :alertBreached, CURRENT_TIMESTAMP)
                ON CONFLICT (consumer_name, consumer_instance_id) 
                DO UPDATE SET 
                    health_status = :healthStatus,
                    lag_milliseconds = :lagMs,
                    processing_rate = :processingRate,
                    error_rate = :errorRate,
                    memory_usage_mb = :memoryUsageMb,
                    cpu_usage_percent = :cpuUsage,
                    active_threads = :activeThreads,
                    health_details = :healthDetails::jsonb,
                    alert_threshold_breached = :alertBreached,
                    last_heartbeat = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                """;
            
            namedParameterJdbcTemplate.update(sql, params);
            
        } catch (DataAccessException e) {
            log.error("Failed to update consumer health for {}", consumerName, e);
        }
    }

    /**
     * Get consumer performance summary
     */
    public Map<String, Object> getConsumerPerformanceSummary() {
        try {
            String sql = """
                SELECT 
                    COUNT(DISTINCT consumer_name) as active_consumers,
                    SUM(events_processed_count) as total_events_processed,
                    SUM(events_failed_count) as total_events_failed,
                    ROUND(AVG(avg_processing_time_ms), 2) as overall_avg_processing_time,
                    COUNT(DISTINCT topic_name) as topics_covered
                FROM event_tracking.consumer_metrics
                """;
            
            Map<String, Object> summary = jdbcTemplate.queryForMap(sql);
            
            // Add real-time performance counters
            summary.put("realtime_events_processed", totalEventsProcessed.get());
            summary.put("realtime_events_failed", totalEventsFailed.get());
            summary.put("realtime_avg_processing_time", 
                       totalEventsProcessed.get() > 0 ? 
                       totalProcessingTimeMs.get() / totalEventsProcessed.get() : 0);
            
            return summary;
            
        } catch (DataAccessException e) {
            log.error("Failed to get consumer performance summary", e);
            return new HashMap<>();
        }
    }

    // Helper methods
    
    private String generateEventId(ConsumerRecord<String, Object> record) {
        return String.format("%s-%d-%d-%s", 
                           record.topic(), 
                           record.partition(), 
                           record.offset(), 
                           UUID.randomUUID().toString().substring(0, 8));
    }
    
    private String extractEventType(ConsumerRecord<String, Object> record) {
        // Try to extract from headers first
        org.apache.kafka.common.header.Header typeHeader = record.headers().lastHeader("event-type");
        if (typeHeader != null) {
            return new String(typeHeader.value());
        }
        
        // Fallback to topic name
        return record.topic().replaceAll("-", "_").toUpperCase() + "_EVENT";
    }
    
    private String extractConsumerGroup(ConsumerRecord<String, Object> record) {
        // Try to extract from headers
        org.apache.kafka.common.header.Header groupHeader = record.headers().lastHeader("consumer-group");
        if (groupHeader != null) {
            return new String(groupHeader.value());
        }
        
        // Default consumer group
        return "default-consumer-group";
    }
    
    private String convertToJson(Object obj) {
        try {
            if (obj == null) return "{}";
            if (obj instanceof String) return (String) obj;
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            log.debug("Failed to convert object to JSON", e);
            return "{}";
        }
    }
    
    private String convertHeadersToJson(ConsumerRecord<String, Object> record) {
        try {
            Map<String, String> headers = new HashMap<>();
            record.headers().forEach(header -> 
                headers.put(header.key(), new String(header.value())));
            return convertToJson(headers);
        } catch (Exception e) {
            log.debug("Failed to convert headers to JSON", e);
            return "{}";
        }
    }
    
    private String buildErrorDetails(DlqEvent dlqEvent) {
        Map<String, Object> details = new HashMap<>();
        details.put("errorType", dlqEvent.getErrorType());
        details.put("errorMessage", dlqEvent.getErrorMessage());
        details.put("timestamp", dlqEvent.getTimestamp());
        details.put("partition", dlqEvent.getPartition());
        details.put("offset", dlqEvent.getOffset());
        return convertToJson(details);
    }
    
    private String buildProcessingContext(ConsumerRecord<String, Object> record) {
        Map<String, Object> context = new HashMap<>();
        context.put("topic", record.topic());
        context.put("partition", record.partition());
        context.put("offset", record.offset());
        context.put("timestamp", record.timestamp());
        context.put("timestampType", record.timestampType().toString());
        context.put("keySize", record.serializedKeySize());
        context.put("valueSize", record.serializedValueSize());
        return convertToJson(context);
    }
    
    private String getStackTrace(Exception e) {
        try {
            // PCI DSS FIX: Build stack trace without printStackTrace()
            StringBuilder sb = new StringBuilder();
            sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");

            for (StackTraceElement element : e.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }

            Throwable cause = e.getCause();
            if (cause != null) {
                sb.append("Caused by: ").append(cause.getClass().getName())
                  .append(": ").append(cause.getMessage()).append("\n");
            }

            return sb.toString();
        } catch (Exception ex) {
            return "Failed to get stack trace: " + ex.getMessage();
        }
    }
}