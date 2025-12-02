package com.waqiti.common.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive Kafka error metrics collector
 * Provides detailed metrics for monitoring Kafka consumer health and error patterns
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaErrorMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // Error counters by topic and consumer group
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> retryCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> dlqCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> processingTimers = new ConcurrentHashMap<>();
    
    // High error rate tracking
    private final Map<String, AtomicLong> highErrorRateAlerts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> criticalErrorCounts = new ConcurrentHashMap<>();
    
    // Error pattern tracking
    private final Map<String, AtomicLong> errorPatterns = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastErrorTimes = new ConcurrentHashMap<>();
    
    /**
     * Increment error count for specific topic/group/error type combination
     */
    public void incrementErrorCount(String topic, String groupId, String errorType) {
        try {
            String key = buildMetricKey(topic, groupId, errorType);
            
            Counter counter = errorCounters.computeIfAbsent(key, k -> 
                Counter.builder("kafka.consumer.errors")
                    .description("Total number of Kafka consumer errors")
                    .tag("topic", topic)
                    .tag("group", groupId)
                    .tag("error_type", errorType)
                    .register(meterRegistry)
            );
            
            counter.increment();
            
            // Update error patterns
            String patternKey = topic + ":" + errorType;
            errorPatterns.computeIfAbsent(patternKey, k -> new AtomicLong(0)).incrementAndGet();
            lastErrorTimes.put(patternKey, LocalDateTime.now());
            
            log.debug("Incremented error count for {}", key);
            
        } catch (Exception e) {
            log.warn("Failed to increment error count for topic: {}, group: {}, error: {}", 
                topic, groupId, errorType, e);
        }
    }
    
    /**
     * Increment retry count for specific topic/group combination
     */
    public void incrementRetryCount(String topic, String groupId, String retryReason) {
        try {
            String key = buildMetricKey(topic, groupId, "retry");
            
            Counter counter = retryCounters.computeIfAbsent(key, k -> 
                Counter.builder("kafka.consumer.retries")
                    .description("Total number of Kafka consumer retries")
                    .tag("topic", topic)
                    .tag("group", groupId)
                    .tag("retry_reason", retryReason)
                    .register(meterRegistry)
            );
            
            counter.increment();
            
        } catch (Exception e) {
            log.warn("Failed to increment retry count for topic: {}, group: {}", 
                topic, groupId, e);
        }
    }
    
    /**
     * Increment DLQ count for specific topic/group combination
     */
    public void incrementDlqCount(String topic, String groupId, String dlqReason) {
        try {
            String key = buildMetricKey(topic, groupId, "dlq");
            
            Counter counter = dlqCounters.computeIfAbsent(key, k -> 
                Counter.builder("kafka.consumer.dlq.messages")
                    .description("Total number of messages sent to DLQ")
                    .tag("topic", topic)
                    .tag("group", groupId)
                    .tag("dlq_reason", dlqReason)
                    .register(meterRegistry)
            );
            
            counter.increment();
            
        } catch (Exception e) {
            log.warn("Failed to increment DLQ count for topic: {}, group: {}", 
                topic, groupId, e);
        }
    }
    
    /**
     * Record processing time for successful message processing
     */
    public void recordProcessingTime(String topic, String groupId, long processingTimeMs) {
        try {
            String key = buildMetricKey(topic, groupId, "processing");
            
            Timer timer = processingTimers.computeIfAbsent(key, k -> 
                Timer.builder("kafka.consumer.processing.time")
                    .description("Kafka consumer message processing time")
                    .tag("topic", topic)
                    .tag("group", groupId)
                    .register(meterRegistry)
            );
            
            timer.record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            
        } catch (Exception e) {
            log.warn("Failed to record processing time for topic: {}, group: {}", 
                topic, groupId, e);
        }
    }
    
    /**
     * Record high error rate alert
     */
    public void recordHighErrorRate(String topic, String groupId) {
        try {
            String key = buildMetricKey(topic, groupId, "high_error_rate");
            
            AtomicLong alertCount = highErrorRateAlerts.computeIfAbsent(key, k -> {
                // Register gauge for high error rate alerts
                Gauge.builder("kafka.consumer.high.error.rate.alerts", k, obj -> highErrorRateAlerts.getOrDefault(obj, new AtomicLong(0)).get())
                    .description("Number of high error rate alerts")
                    .tag("topic", topic)
                    .tag("group", groupId)
                    .register(meterRegistry);
                    
                return new AtomicLong(0);
            });
            
            alertCount.incrementAndGet();
            
            // Also create a counter for alerting systems
            Counter.builder("kafka.consumer.alerts.high.error.rate")
                .description("High error rate alerts triggered")
                .tag("topic", topic)
                .tag("group", groupId)
                .tag("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")))
                .register(meterRegistry)
                .increment();
                
            log.warn("HIGH_ERROR_RATE_ALERT: Topic {}, Group {} has exceeded error rate threshold", 
                topic, groupId);
            
        } catch (Exception e) {
            log.warn("Failed to record high error rate for topic: {}, group: {}", 
                topic, groupId, e);
        }
    }
    
    /**
     * Record critical error that requires immediate attention
     */
    public void recordCriticalError(String topic, String groupId, Exception exception) {
        try {
            String key = buildMetricKey(topic, groupId, "critical");
            
            AtomicLong criticalCount = criticalErrorCounts.computeIfAbsent(key, k -> {
                // Register gauge for critical error count
                Gauge.builder("kafka.consumer.critical.errors", k, obj -> criticalErrorCounts.getOrDefault(obj, new AtomicLong(0)).get())
                    .description("Number of critical errors requiring attention")
                    .tag("topic", topic)
                    .tag("group", groupId)
                    .register(meterRegistry);
                    
                return new AtomicLong(0);
            });
            
            criticalCount.incrementAndGet();
            
            // Create counter for alerting systems
            Counter.builder("kafka.consumer.alerts.critical.error")
                .description("Critical errors requiring immediate attention")
                .tag("topic", topic)
                .tag("group", groupId)
                .tag("error_type", exception.getClass().getSimpleName())
                .tag("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")))
                .register(meterRegistry)
                .increment();
                
            log.error("CRITICAL_ERROR_ALERT: Topic {}, Group {} has critical error: {}", 
                topic, groupId, exception.getMessage());
            
        } catch (Exception e) {
            log.warn("Failed to record critical error for topic: {}, group: {}", 
                topic, groupId, e);
        }
    }
    
    /**
     * Record consumer lag metrics
     */
    public void recordConsumerLag(String topic, String groupId, int partition, long lag) {
        try {
            Gauge.builder("kafka.consumer.lag", lag, l -> l)
                .description("Kafka consumer lag by topic and partition")
                .tag("topic", topic)
                .tag("group", groupId)
                .tag("partition", String.valueOf(partition))
                .register(meterRegistry);
                
        } catch (Exception e) {
            log.warn("Failed to record consumer lag for topic: {}, group: {}, partition: {}", 
                topic, groupId, partition, e);
        }
    }
    
    /**
     * Record poison message detection
     */
    public void recordPoisonMessage(String topic, String groupId, String messageKey, 
                                  long offset, int consecutiveFailures) {
        try {
            Counter.builder("kafka.consumer.poison.messages")
                .description("Poison messages detected and handled")
                .tag("topic", topic)
                .tag("group", groupId)
                .tag("consecutive_failures", String.valueOf(consecutiveFailures))
                .register(meterRegistry)
                .increment();
                
            log.warn("POISON_MESSAGE_DETECTED: Topic {}, Group {}, Offset {}, Key {}, Failures: {}", 
                topic, groupId, offset, messageKey, consecutiveFailures);
                
        } catch (Exception e) {
            log.warn("Failed to record poison message for topic: {}, group: {}", 
                topic, groupId, e);
        }
    }
    
    /**
     * Record successful message processing
     */
    public void recordSuccessfulProcessing(String topic, String groupId) {
        try {
            Counter.builder("kafka.consumer.successful.processing")
                .description("Successfully processed messages")
                .tag("topic", topic)
                .tag("group", groupId)
                .register(meterRegistry)
                .increment();
                
        } catch (Exception e) {
            log.warn("Failed to record successful processing for topic: {}, group: {}", 
                topic, groupId, e);
        }
    }
    
    // Legacy methods for backward compatibility
    
    public void recordError(String topic, String errorType, String consumerGroup) {
        incrementErrorCount(topic, consumerGroup, errorType);
    }
    
    public void recordRetryAttempt(String topic, int attemptNumber) {
        incrementRetryCount(topic, "default-group", "attempt_" + attemptNumber);
    }
    
    public void recordDeadLetterMessage(String topic) {
        incrementDlqCount(topic, "default-group", "processing_failure");
    }
    
    // Helper methods
    
    private String buildMetricKey(String topic, String groupId, String suffix) {
        String base = topic + ":" + groupId;
        return suffix.isEmpty() ? base : base + ":" + suffix;
    }
    
    private long getTotalErrors(String topic, String groupId) {
        return errorCounters.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(topic + ":" + groupId))
            .mapToLong(entry -> (long) entry.getValue().count())
            .sum();
    }
    
    private long getTotalRetries(String topic, String groupId) {
        return retryCounters.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(topic + ":" + groupId))
            .mapToLong(entry -> (long) entry.getValue().count())
            .sum();
    }
    
    private long getTotalDlqMessages(String topic, String groupId) {
        return dlqCounters.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(topic + ":" + groupId))
            .mapToLong(entry -> (long) entry.getValue().count())
            .sum();
    }
    
    private long getCriticalErrorCount(String topic, String groupId) {
        String key = buildMetricKey(topic, groupId, "critical");
        return criticalErrorCounts.getOrDefault(key, new AtomicLong(0)).get();
    }
    
    private long getHighErrorRateAlerts(String topic, String groupId) {
        String key = buildMetricKey(topic, groupId, "high_error_rate");
        return highErrorRateAlerts.getOrDefault(key, new AtomicLong(0)).get();
    }
    
    // Data class for error statistics
    @lombok.Data
    @lombok.Builder
    public static class ErrorStatistics {
        private String topic;
        private String groupId;
        private long totalErrors;
        private long totalRetries;
        private long totalDlqMessages;
        private long criticalErrorCount;
        private long highErrorRateAlerts;
        private LocalDateTime lastErrorTime;
        
        public double getErrorRate() {
            long total = totalErrors + totalRetries + totalDlqMessages;
            return total == 0 ? 0.0 : (double) totalErrors / total;
        }
        
        public boolean hasRecentErrors() {
            return lastErrorTime != null && 
                   lastErrorTime.isAfter(LocalDateTime.now().minusMinutes(5));
        }
        
        public boolean isCriticalState() {
            return criticalErrorCount > 0 || highErrorRateAlerts > 0;
        }
    }
}