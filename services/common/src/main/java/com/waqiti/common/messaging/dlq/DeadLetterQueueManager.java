package com.waqiti.common.messaging.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.messaging.analysis.FailureAnalysisService;
import com.waqiti.common.messaging.retry.RetryPolicyManager;
import com.waqiti.common.messaging.model.DeadLetterMessage;
import com.waqiti.common.messaging.model.RetryMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CRITICAL PRODUCTION FIX - DeadLetterQueueManager
 * Comprehensive Dead Letter Queue implementation for Kafka
 * Prevents message loss and provides recovery mechanisms
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueManager {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RetryPolicyManager retryPolicyManager;
    private final FailureAnalysisService failureAnalysisService;
    private final ObjectMapper objectMapper;
    
    @Value("${dlq.topic.suffix:.dlq}")
    private String dlqTopicSuffix;
    
    @Value("${dlq.retry.topic.suffix:.retry}")
    private String retryTopicSuffix;
    
    @Value("${dlq.max.retention.days:30}")
    private int maxRetentionDays;
    
    @Value("${dlq.monitoring.enabled:true}")
    private boolean monitoringEnabled;
    
    // Metrics tracking
    private final Map<String, AtomicLong> dlqMetrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> retryMetrics = new ConcurrentHashMap<>();
    
    /**
     * Send message to Dead Letter Queue
     */
    public void sendToDeadLetterQueue(String originalTopic, Object originalMessage, 
                                    Exception error, Map<String, Object> headers) {
        try {
            String dlqTopic = generateDLQTopicName(originalTopic);
            String messageId = UUID.randomUUID().toString();
            
            // Create DLQ message wrapper
            DeadLetterMessage dlqMessage = DeadLetterMessage.builder()
                .id(messageId)
                .originalTopic(originalTopic)
                .originalMessage(originalMessage)
                .failureReason(error.getMessage())
                .failureClass(error.getClass().getSimpleName())
                .stackTrace(getStackTrace(error))
                .originalHeaders(convertHeaders(headers))
                .firstFailedAt(LocalDateTime.now())
                .lastFailedAt(LocalDateTime.now())
                .attemptCount(getAttemptCount(headers))
                .dlqTopic(dlqTopic)
                .retentionUntil(LocalDateTime.now().plusDays(maxRetentionDays))
                .recoverable(isRecoverable(error))
                .build();
            
            // Add DLQ headers
            Map<String, Object> dlqHeaders = new HashMap<>(headers != null ? headers : new HashMap<>());
            dlqHeaders.put("dlq-original-topic", originalTopic);
            dlqHeaders.put("dlq-failure-time", System.currentTimeMillis());
            dlqHeaders.put("dlq-message-id", messageId);
            dlqHeaders.put("dlq-failure-class", error.getClass().getName());
            dlqHeaders.put("dlq-recoverable", dlqMessage.isRecoverable());
            
            // Build message
            Message<DeadLetterMessage> message = MessageBuilder
                .withPayload(dlqMessage)
                .setHeader(KafkaHeaders.TOPIC, dlqTopic)
                .setHeader(KafkaHeaders.KEY, messageId)
                .copyHeaders(dlqHeaders)
                .build();
            
            // Send to DLQ
            kafkaTemplate.send(message).whenComplete((result, failure) -> {
                if (failure == null) {
                    log.info("Message sent to DLQ successfully: topic={}, messageId={}", dlqTopic, messageId);
                    updateDLQMetrics(originalTopic, true);
                    
                    // Record failure for analysis
                    if (monitoringEnabled) {
                        failureAnalysisService.recordFailure(originalTopic, error, headers);
                    }
                } else {
                    log.error("CRITICAL: Failed to send message to DLQ: topic={}, messageId={}, error={}", 
                        dlqTopic, messageId, failure.getMessage(), failure);
                    updateDLQMetrics(originalTopic, false);
                }
            });
            
        } catch (Exception e) {
            log.error("CRITICAL: DLQ processing failed for topic {}: {}", originalTopic, e.getMessage(), e);
        }
    }
    
    /**
     * Send message to retry topic with exponential backoff
     */
    public void sendToRetryTopic(String originalTopic, Object originalMessage, 
                               Exception error, Map<String, Object> headers, int attemptNumber) {
        try {
            // Check if should retry
            if (!retryPolicyManager.shouldRetry(originalTopic, attemptNumber, error)) {
                log.warn("Retry limit exceeded for topic {}, sending to DLQ", originalTopic);
                sendToDeadLetterQueue(originalTopic, originalMessage, error, headers);
                return;
            }
            
            String retryTopic = generateRetryTopicName(originalTopic);
            long delay = retryPolicyManager.getNextRetryDelay(originalTopic, attemptNumber);
            
            // Create retry message
            RetryMessage retryMessage = RetryMessage.builder()
                .id(UUID.randomUUID().toString())
                .originalTopic(originalTopic)
                .originalMessage(originalMessage)
                .attemptNumber(attemptNumber)
                .maxAttempts(retryPolicyManager.getRetryPolicy(originalTopic).getMaxAttempts())
                .nextRetryAt(LocalDateTime.now().plusSeconds(delay / 1000))
                .lastError(error.getMessage())
                .originalHeaders(headers)
                .build();
            
            // Add retry headers
            Map<String, Object> retryHeaders = new HashMap<>(headers != null ? headers : new HashMap<>());
            retryHeaders.put("retry-attempt", attemptNumber);
            retryHeaders.put("retry-delay-ms", delay);
            retryHeaders.put("retry-original-topic", originalTopic);
            retryHeaders.put("retry-scheduled-time", System.currentTimeMillis() + delay);
            
            // Build message with delay
            Message<RetryMessage> message = MessageBuilder
                .withPayload(retryMessage)
                .setHeader(KafkaHeaders.TOPIC, retryTopic)
                .setHeader(KafkaHeaders.KEY, retryMessage.getId())
                .setHeader("kafka_delay", delay) // For delayed delivery
                .copyHeaders(retryHeaders)
                .build();
            
            // Send to retry topic
            kafkaTemplate.send(message).whenComplete((result, failure) -> {
                if (failure == null) {
                    log.info("Message scheduled for retry: topic={}, attempt={}, delay={}ms", 
                        retryTopic, attemptNumber, delay);
                    updateRetryMetrics(originalTopic, true);
                } else {
                    log.error("Failed to send message to retry topic: topic={}, attempt={}, error={}", 
                        retryTopic, attemptNumber, failure.getMessage());
                    updateRetryMetrics(originalTopic, false);
                    
                    // If retry scheduling fails, send directly to DLQ
                    sendToDeadLetterQueue(originalTopic, originalMessage, error, headers);
                }
            });
            
        } catch (Exception e) {
            log.error("Retry processing failed for topic {}: {}", originalTopic, e.getMessage(), e);
            sendToDeadLetterQueue(originalTopic, originalMessage, error, headers);
        }
    }
    
    /**
     * Recover message from DLQ back to original topic
     */
    public void recoverFromDeadLetterQueue(DeadLetterMessage dlqMessage) {
        try {
            if (!dlqMessage.isRecoverable()) {
                log.warn("Message {} marked as non-recoverable, skipping recovery", dlqMessage.getId());
                return;
            }
            
            // Validate message hasn't expired
            if (dlqMessage.getRetentionUntil().isBefore(LocalDateTime.now())) {
                log.warn("Message {} expired, cannot recover", dlqMessage.getId());
                return;
            }
            
            // Add recovery headers
            Map<String, Object> recoveryHeaders = new HashMap<>(dlqMessage.getOriginalHeaders());
            recoveryHeaders.put("dlq-recovered", true);
            recoveryHeaders.put("dlq-recovery-time", System.currentTimeMillis());
            recoveryHeaders.put("dlq-original-failure", dlqMessage.getFailureReason());
            recoveryHeaders.put("dlq-attempt-count", dlqMessage.getAttemptCount());
            
            // Build recovery message
            Message<Object> message = MessageBuilder
                .withPayload(dlqMessage.getOriginalMessage())
                .setHeader(KafkaHeaders.TOPIC, dlqMessage.getOriginalTopic())
                .setHeader(KafkaHeaders.KEY, dlqMessage.getId())
                .copyHeaders(recoveryHeaders)
                .build();
            
            // Send back to original topic
            kafkaTemplate.send(message).whenComplete((result, failure) -> {
                if (failure == null) {
                    log.info("Message recovered from DLQ successfully: messageId={}, originalTopic={}", 
                        dlqMessage.getId(), dlqMessage.getOriginalTopic());
                } else {
                    log.error("Failed to recover message from DLQ: messageId={}, error={}", 
                        dlqMessage.getId(), failure.getMessage());
                }
            });
            
        } catch (Exception e) {
            log.error("DLQ recovery failed for message {}: {}", dlqMessage.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Generate DLQ topic name
     */
    private String generateDLQTopicName(String originalTopic) {
        return originalTopic + dlqTopicSuffix;
    }
    
    /**
     * Generate retry topic name
     */
    private String generateRetryTopicName(String originalTopic) {
        return originalTopic + retryTopicSuffix;
    }
    
    /**
     * Get attempt count from headers
     */
    private int getAttemptCount(Map<String, Object> headers) {
        if (headers == null) return 1;
        
        Object attempt = headers.get("retry-attempt");
        if (attempt instanceof Integer) {
            return (Integer) attempt + 1;
        }
        return 1;
    }
    
    /**
     * Check if error is recoverable
     */
    private boolean isRecoverable(Exception error) {
        // Non-recoverable errors (business logic, validation, etc.)
        if (error instanceof IllegalArgumentException ||
            error instanceof IllegalStateException ||
            error instanceof SecurityException ||
            error.getMessage().contains("validation") ||
            error.getMessage().contains("schema")) {
            return false;
        }
        
        // Recoverable errors (network, timeout, temporary failures)
        return error.getMessage().contains("timeout") ||
               error.getMessage().contains("connection") ||
               error.getMessage().contains("unavailable") ||
               error.getClass().getSimpleName().contains("Timeout") ||
               error.getClass().getSimpleName().contains("Connection");
    }
    
    /**
     * Get stack trace as string (PCI DSS compliant)
     */
    private String getStackTrace(Exception error) {
        try {
            // PCI DSS FIX: Build stack trace without printStackTrace()
            StringBuilder sb = new StringBuilder();
            sb.append(error.getClass().getName()).append(": ").append(error.getMessage()).append("\n");

            for (StackTraceElement element : error.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }

            Throwable cause = error.getCause();
            if (cause != null) {
                sb.append("Caused by: ").append(cause.getClass().getName())
                  .append(": ").append(cause.getMessage()).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Stack trace unavailable: " + e.getMessage();
        }
    }
    
    /**
     * Update DLQ metrics
     */
    private void updateDLQMetrics(String topic, boolean success) {
        String key = topic + (success ? ".dlq.success" : ".dlq.failure");
        dlqMetrics.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * Update retry metrics
     */
    private void updateRetryMetrics(String topic, boolean success) {
        String key = topic + (success ? ".retry.success" : ".retry.failure");
        retryMetrics.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * Get DLQ metrics
     */
    public Map<String, Long> getDLQMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        dlqMetrics.forEach((key, value) -> metrics.put(key, value.get()));
        return metrics;
    }
    
    /**
     * Get retry metrics
     */
    public Map<String, Long> getRetryMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        retryMetrics.forEach((key, value) -> metrics.put(key, value.get()));
        return metrics;
    }
    
    /**
     * Convert Map<String, Object> to Map<String, String>
     */
    private Map<String, String> convertHeaders(Map<String, Object> headers) {
        if (headers == null) {
            return new HashMap<>();
        }
        Map<String, String> converted = new HashMap<>();
        headers.forEach((key, value) -> {
            if (value != null) {
                converted.put(key, value.toString());
            }
        });
        return converted;
    }
}