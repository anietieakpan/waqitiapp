package com.waqiti.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive Dead Letter Queue (DLQ) service for Kafka
 * Handles failed message routing, enrichment, and tracking for manual processing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaDeadLetterService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaErrorMetrics errorMetrics;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    
    // DLQ statistics tracking
    private final Map<String, AtomicLong> dlqCounts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> firstDlqTimes = new ConcurrentHashMap<>();
    
    // DLQ configuration constants
    private static final String DLQ_SUFFIX = "-dlq";
    private static final String DLQ_HEADER_ORIGINAL_TOPIC = "x-original-topic";
    private static final String DLQ_HEADER_ORIGINAL_PARTITION = "x-original-partition";
    private static final String DLQ_HEADER_ORIGINAL_OFFSET = "x-original-offset";
    private static final String DLQ_HEADER_ERROR_MESSAGE = "x-error-message";
    private static final String DLQ_HEADER_ERROR_CLASS = "x-error-class";
    private static final String DLQ_HEADER_RETRY_COUNT = "x-retry-count";
    private static final String DLQ_HEADER_DLQ_TIMESTAMP = "x-dlq-timestamp";
    private static final String DLQ_HEADER_CONSUMER_GROUP = "x-consumer-group";
    private static final String DLQ_HEADER_ERROR_CONTEXT = "x-error-context";
    
    // Legacy method for backward compatibility
    public void sendToDeadLetterQueue(ConsumerRecord<?, ?> record, Exception exception) {
        String dlqTopic = record.topic() + DLQ_SUFFIX;
        
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalTopic", record.topic());
            dlqMessage.put("originalPartition", record.partition());
            dlqMessage.put("originalOffset", record.offset());
            dlqMessage.put("originalKey", record.key());
            dlqMessage.put("originalValue", record.value());
            dlqMessage.put("errorMessage", exception.getMessage());
            dlqMessage.put("errorClass", exception.getClass().getName());
            dlqMessage.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send(dlqTopic, objectMapper.writeValueAsString(dlqMessage));
            errorMetrics.recordDeadLetterMessage(record.topic());
            
            log.warn("Message sent to DLQ. Topic: {}, Offset: {}, Error: {}", 
                    record.topic(), record.offset(), exception.getMessage());
                    
        } catch (Exception e) {
            log.error("Failed to send message to DLQ. Topic: {}, Offset: {}", 
                    record.topic(), record.offset(), e);
        }
    }
    
    public void processDeadLetterMessage(String dlqTopic, String message) {
        try {
            Map<String, Object> dlqMessage = objectMapper.readValue(message, Map.class);
            
            // Log the dead letter message for investigation
            log.error("Processing DLQ message from topic: {}, Original topic: {}, Error: {}", 
                    dlqTopic, dlqMessage.get("originalTopic"), dlqMessage.get("errorMessage"));
                    
            // Here you could implement logic to:
            // 1. Store in database for later analysis
            // 2. Send alerts to monitoring system
            // 3. Attempt manual reprocessing after fixes
            
        } catch (Exception e) {
            log.error("Failed to process DLQ message from topic: {}", dlqTopic, e);
        }
    }
    
    // New comprehensive methods
    
    /**
     * Send failed message to Dead Letter Queue with comprehensive error context
     */
    public void sendToDeadLetterQueue(KafkaErrorHandler.ErrorContext errorContext, 
                                    Message<?> originalMessage, 
                                    Exception exception) {
        try {
            String dlqTopic = buildDlqTopic(errorContext.getTopic());
            
            // Build enriched DLQ message with error context
            Message<Object> dlqMessage = buildDlqMessage(errorContext, originalMessage, exception);
            
            // Send to DLQ
            kafkaTemplate.send(dlqMessage);
            
            // Update statistics
            updateDlqStatistics(errorContext.getTopic(), errorContext.getGroupId());
            
            // Update metrics
            errorMetrics.incrementDlqCount(
                errorContext.getTopic(), 
                errorContext.getGroupId(), 
                exception.getClass().getSimpleName()
            );
            
            // Audit the DLQ operation
            auditDlqMessage(errorContext, exception, dlqTopic);
            
            log.warn("DLQ_MESSAGE_SENT: Message sent to DLQ - Topic: {}, DLQ: {}, Error: {}", 
                errorContext.getTopic(), dlqTopic, exception.getClass().getSimpleName());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send message to DLQ for topic {}, partition {}, offset {} - Error: {}", 
                errorContext.getTopic(), errorContext.getPartition(), errorContext.getOffset(), e.getMessage(), e);
            
            // Even DLQ sending failed - this is critical
            auditService.auditKafkaEvent(
                "DLQ_SEND_FAILURE",
                errorContext.getGroupId(),
                "Failed to send message to Dead Letter Queue",
                Map.of(
                    "originalTopic", errorContext.getTopic(),
                    "partition", errorContext.getPartition(),
                    "offset", errorContext.getOffset(),
                    "dlqError", e.getMessage(),
                    "originalError", exception.getMessage()
                )
            );
        }
    }
    
    /**
     * Send poison message to dedicated poison message DLQ
     */
    public void sendPoisonMessageToDlq(KafkaErrorHandler.ErrorContext errorContext, 
                                     Message<?> originalMessage, 
                                     Exception exception,
                                     int consecutiveFailures) {
        try {
            String poisonDlqTopic = buildPoisonDlqTopic(errorContext.getTopic());
            
            // Build poison message with additional context
            Message<Object> poisonMessage = buildPoisonMessage(errorContext, originalMessage, exception, consecutiveFailures);
            
            // Send to poison DLQ
            kafkaTemplate.send(poisonMessage);
            
            // Update metrics for poison messages
            errorMetrics.recordPoisonMessage(
                errorContext.getTopic(),
                errorContext.getGroupId(),
                errorContext.getMessageKey(),
                errorContext.getOffset(),
                consecutiveFailures
            );
            
            log.error("POISON_MESSAGE_DLQ: Poison message sent to DLQ - Topic: {}, Poison DLQ: {}, Failures: {}", 
                errorContext.getTopic(), poisonDlqTopic, consecutiveFailures);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send poison message to DLQ", e);
            // Fallback to regular DLQ
            sendToDeadLetterQueue(errorContext, originalMessage, exception);
        }
    }
    
    /**
     * Get DLQ statistics for monitoring
     */
    public DlqStatistics getDlqStatistics() {
        long totalDlqMessages = dlqCounts.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
        
        int topicsWithDlq = dlqCounts.size();
        
        LocalDateTime oldestDlqTime = firstDlqTimes.values().stream()
            .min(LocalDateTime::compareTo)
            .orElse(null);
        
        return DlqStatistics.builder()
            .totalDlqMessages(totalDlqMessages)
            .topicsWithDlqMessages(topicsWithDlq)
            .oldestDlqMessageTime(oldestDlqTime)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Clean up old DLQ statistics
     */
    public void cleanupOldStatistics() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        
        int removedCount = 0;
        for (Map.Entry<String, LocalDateTime> entry : firstDlqTimes.entrySet()) {
            if (entry.getValue().isBefore(cutoff)) {
                String topicGroup = entry.getKey();
                firstDlqTimes.remove(topicGroup);
                dlqCounts.remove(topicGroup);
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.info("Cleaned up {} old DLQ statistics entries", removedCount);
        }
    }
    
    // Private methods
    
    private Message<Object> buildDlqMessage(KafkaErrorHandler.ErrorContext errorContext, 
                                          Message<?> originalMessage, 
                                          Exception exception) {
        
        MessageBuilder<Object> builder = MessageBuilder.withPayload(originalMessage.getPayload())
            .setHeader(KafkaHeaders.TOPIC, buildDlqTopic(errorContext.getTopic()))
            .setHeader(KafkaHeaders.KEY, errorContext.getMessageKey())
            .setHeader(DLQ_HEADER_ORIGINAL_TOPIC, errorContext.getTopic())
            .setHeader(DLQ_HEADER_ORIGINAL_PARTITION, errorContext.getPartition())
            .setHeader(DLQ_HEADER_ORIGINAL_OFFSET, errorContext.getOffset())
            .setHeader(DLQ_HEADER_ERROR_MESSAGE, truncateErrorMessage(exception.getMessage()))
            .setHeader(DLQ_HEADER_ERROR_CLASS, exception.getClass().getSimpleName())
            .setHeader(DLQ_HEADER_CONSUMER_GROUP, errorContext.getGroupId())
            .setHeader(DLQ_HEADER_DLQ_TIMESTAMP, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .setHeader(DLQ_HEADER_ERROR_CONTEXT, buildErrorContext(errorContext, exception));
        
        // Copy original headers if they exist
        if (originalMessage.getHeaders() != null) {
            originalMessage.getHeaders().forEach((key, value) -> {
                if (!key.startsWith("kafka_") && !key.startsWith("x-")) {
                    builder.setHeader("original_" + key, value);
                }
            });
        }
        
        return builder.build();
    }
    
    private Message<Object> buildPoisonMessage(KafkaErrorHandler.ErrorContext errorContext, 
                                             Message<?> originalMessage, 
                                             Exception exception,
                                             int consecutiveFailures) {
        
        Message<Object> baseMessage = buildDlqMessage(errorContext, originalMessage, exception);
        
        return MessageBuilder.fromMessage(baseMessage)
            .setHeader(KafkaHeaders.TOPIC, buildPoisonDlqTopic(errorContext.getTopic()))
            .setHeader("x-poison-message", true)
            .setHeader("x-consecutive-failures", consecutiveFailures)
            .setHeader("x-poison-detected-at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .build();
    }
    
    private void updateDlqStatistics(String topic, String groupId) {
        String statsKey = topic + ":" + groupId;
        
        dlqCounts.computeIfAbsent(statsKey, k -> new AtomicLong(0)).incrementAndGet();
        firstDlqTimes.computeIfAbsent(statsKey, k -> LocalDateTime.now());
    }
    
    private void auditDlqMessage(KafkaErrorHandler.ErrorContext errorContext, Exception exception, String dlqTopic) {
        try {
            auditService.auditKafkaEvent(
                "MESSAGE_SENT_TO_DLQ",
                errorContext.getGroupId(),
                "Message sent to Dead Letter Queue due to processing failure",
                Map.of(
                    "originalTopic", errorContext.getTopic(),
                    "dlqTopic", dlqTopic,
                    "partition", errorContext.getPartition(),
                    "offset", errorContext.getOffset(),
                    "messageKey", errorContext.getMessageKey() != null ? errorContext.getMessageKey() : "null",
                    "errorClass", exception.getClass().getSimpleName(),
                    "errorMessage", truncateErrorMessage(exception.getMessage()),
                    "consumerGroup", errorContext.getGroupId(),
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            );
        } catch (Exception e) {
            log.debug("Failed to audit DLQ message", e);
        }
    }
    
    private String buildDlqTopic(String originalTopic) {
        return originalTopic + DLQ_SUFFIX;
    }
    
    private String buildPoisonDlqTopic(String originalTopic) {
        return originalTopic + "-poison" + DLQ_SUFFIX;
    }
    
    private String buildErrorContext(KafkaErrorHandler.ErrorContext errorContext, Exception exception) {
        return String.format("Topic=%s, Partition=%d, Offset=%d, Group=%s, Error=%s", 
            errorContext.getTopic(),
            errorContext.getPartition(),
            errorContext.getOffset(),
            errorContext.getGroupId(),
            exception.getClass().getSimpleName()
        );
    }
    
    private String truncateErrorMessage(String message) {
        if (message == null) {
            return "null";
        }
        
        // Truncate very long error messages to prevent header size issues
        int maxLength = 1000;
        if (message.length() > maxLength) {
            return message.substring(0, maxLength) + "... (truncated)";
        }
        
        return message;
    }
    
    // Data classes
    
    @lombok.Data
    @lombok.Builder
    public static class DlqStatistics {
        private long totalDlqMessages;
        private int topicsWithDlqMessages;
        private LocalDateTime oldestDlqMessageTime;
        private LocalDateTime timestamp;
        
        public boolean hasRecentDlqActivity() {
            return oldestDlqMessageTime != null && 
                   oldestDlqMessageTime.isAfter(LocalDateTime.now().minusHours(1));
        }
        
        public long getDlqMessageAgeHours() {
            if (oldestDlqMessageTime == null) {
                return 0;
            }
            
            return java.time.Duration.between(oldestDlqMessageTime, LocalDateTime.now()).toHours();
        }
        
        public double getAverageDlqPerTopic() {
            return topicsWithDlqMessages == 0 ? 0.0 : (double) totalDlqMessages / topicsWithDlqMessages;
        }
    }
}