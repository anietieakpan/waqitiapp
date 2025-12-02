package com.waqiti.common.messaging.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
// Removed: import org.springframework.util.concurrent.ListenableFutureCallback; (deprecated in newer Spring Kafka)

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

// Dead Letter Queue related imports
import com.waqiti.common.messaging.deadletter.DeadLetterEvent;
import com.waqiti.common.messaging.deadletter.DeadLetterQueueMetrics;
import com.waqiti.common.messaging.deadletter.DeadLetterRecord;

/**
 * CRITICAL MESSAGE RECOVERY: Dead Letter Queue Service
 * 
 * Handles failed message processing with automatic retry, poison message detection,
 * and comprehensive error analysis. Essential for maintaining message integrity
 * in the financial transaction processing system.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeadLetterQueueService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityAuditLogger securityAuditLogger;
    private final DeadLetterRepository deadLetterRepository;

    // DLQ topic patterns
    private static final String DLQ_TOPIC_SUFFIX = ".dlq";
    private static final String RETRY_TOPIC_SUFFIX = ".retry";
    
    // Retry configuration
    private static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 30000; // 30 seconds
    private static final long EXPONENTIAL_BACKOFF_MULTIPLIER = 2;
    
    // Metrics tracking
    private final Map<String, AtomicLong> dlqMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<DeadLetterEvent>> recentEvents = new ConcurrentHashMap<>();
    
    /**
     * CRITICAL: Send failed message to dead letter queue with comprehensive error context
     */
    public CompletableFuture<Void> sendToDeadLetterQueue(DeadLetterMessage dlqMessage) {
        try {
            log.error("CRITICAL: Sending message to DLQ - Topic: {}, Reason: {}, MessageId: {}", 
                dlqMessage.getOriginalTopic(), dlqMessage.getFailureReason(), dlqMessage.getMessageId());
            
            // Enrich with DLQ metadata
            dlqMessage.setDlqTimestamp(LocalDateTime.now());
            dlqMessage.setDlqTopic(generateDlqTopicName(dlqMessage.getOriginalTopic()));
            dlqMessage.setProcessingHistory(buildProcessingHistory(dlqMessage));
            
            // Determine if message is poisonous
            boolean isPoisonMessage = isPoisonMessage(dlqMessage);
            dlqMessage.setPoisonMessage(isPoisonMessage);
            
            if (isPoisonMessage) {
                log.error("POISON MESSAGE DETECTED: MessageId: {}, Topic: {}, Attempts: {}", 
                    dlqMessage.getMessageId(), dlqMessage.getOriginalTopic(), dlqMessage.getRetryCount());
            }
            
            // Store in database for tracking
            DeadLetterRecord record = createDeadLetterRecord(dlqMessage);
            deadLetterRepository.save(record);
            
            // Send to Kafka DLQ topic
            String dlqTopicName = dlqMessage.getDlqTopic();
            String messageJson = objectMapper.writeValueAsString(dlqMessage);
            
            return kafkaTemplate.send(dlqTopicName, dlqMessage.getMessageId(), messageJson)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("CRITICAL: Failed to send message to DLQ - MessageId: {}", 
                            dlqMessage.getMessageId(), ex);
                        
                        // Store in fallback storage if DLQ send fails
                        storeFallbackMessage(dlqMessage, ex);
                    } else {
                        log.info("SUCCESS: Message sent to DLQ - Topic: {}, Partition: {}, Offset: {}", 
                            dlqTopicName, result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                        
                        // Update metrics
                        updateDlqMetrics(dlqMessage.getOriginalTopic());
                        
                        // Log security event for financial messages
                        if (isFinancialMessage(dlqMessage)) {
                            logFinancialMessageFailure(dlqMessage);
                        }
                    }
                })
                .thenApply(result -> null); // Convert to CompletableFuture<Void>
            
        } catch (Exception e) {
            log.error("CRITICAL: Error processing DLQ message - MessageId: {}", 
                dlqMessage.getMessageId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Attempt to retry a failed message with exponential backoff
     */
    public CompletableFuture<Void> retryMessage(DeadLetterMessage dlqMessage) {
        try {
            if (dlqMessage.getRetryCount() >= DEFAULT_MAX_RETRY_ATTEMPTS) {
                log.warn("RETRY EXHAUSTED: Message exceeded max retry attempts - MessageId: {}, Attempts: {}", 
                    dlqMessage.getMessageId(), dlqMessage.getRetryCount());
                return sendToDeadLetterQueue(dlqMessage);
            }
            
            // Increment retry count
            dlqMessage.setRetryCount(dlqMessage.getRetryCount() + 1);
            dlqMessage.setLastRetryAt(LocalDateTime.now());
            
            // Calculate retry delay with exponential backoff
            long retryDelay = calculateRetryDelay(dlqMessage.getRetryCount());
            dlqMessage.setNextRetryAt(LocalDateTime.now().plusNanos(retryDelay * 1_000_000));
            
            log.info("RETRY: Scheduling message retry - MessageId: {}, Attempt: {}, Delay: {}ms", 
                dlqMessage.getMessageId(), dlqMessage.getRetryCount(), retryDelay);
            
            // Send to retry topic with delay
            String retryTopic = generateRetryTopicName(dlqMessage.getOriginalTopic());
            String messageJson = objectMapper.writeValueAsString(dlqMessage);
            
            // Schedule retry after delay and send to retry topic
            return CompletableFuture.runAsync(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(retryDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry delay interrupted", e);
                }
            }, CompletableFuture.delayedExecutor(retryDelay, java.util.concurrent.TimeUnit.MILLISECONDS))
                .thenCompose(v -> kafkaTemplate.send(retryTopic, dlqMessage.getMessageId(), messageJson))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("RETRY FAILED: Failed to send to retry topic - MessageId: {}", 
                            dlqMessage.getMessageId(), ex);
                    } else {
                        log.info("RETRY SCHEDULED: Message sent to retry topic - MessageId: {}", 
                            dlqMessage.getMessageId());
                    }
                })
                .thenApply(result -> null); // Convert to CompletableFuture<Void>
            
        } catch (Exception e) {
            log.error("ERROR: Failed to process retry for message - MessageId: {}", 
                dlqMessage.getMessageId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Process messages from dead letter queue for manual intervention
     */
    public List<DeadLetterMessage> getDeadLetterMessages(String topic, int limit) {
        try {
            List<DeadLetterRecord> records = deadLetterRepository.findByOriginalTopicOrderByCreatedAtDesc(topic, limit);
            
            List<DeadLetterMessage> messages = new ArrayList<>();
            for (DeadLetterRecord record : records) {
                try {
                    DeadLetterMessage message = objectMapper.readValue(record.getMessageData(), DeadLetterMessage.class);
                    messages.add(message);
                } catch (Exception e) {
                    log.error("ERROR: Failed to deserialize DLQ message - RecordId: {}", record.getId(), e);
                }
            }
            
            return messages;
            
        } catch (Exception e) {
            log.error("ERROR: Failed to retrieve DLQ messages for topic: {}", topic, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Manual reprocessing of dead letter messages
     */
    public CompletableFuture<Void> reprocessMessage(String messageId, String originalTopic) {
        try {
            Optional<DeadLetterRecord> recordOpt = deadLetterRepository.findByMessageId(messageId);
            if (!recordOpt.isPresent()) {
                throw new IllegalArgumentException("Dead letter message not found: " + messageId);
            }
            
            DeadLetterRecord record = recordOpt.get();
            DeadLetterMessage dlqMessage = objectMapper.readValue(record.getMessageData(), DeadLetterMessage.class);
            
            log.info("MANUAL REPROCESS: Reprocessing dead letter message - MessageId: {}, Topic: {}", 
                messageId, originalTopic);
            
            // Mark as being reprocessed
            record.setStatus(DeadLetterStatus.REPROCESSING);
            record.setReprocessedAt(LocalDateTime.now());
            deadLetterRepository.save(record);
            
            // Send back to original topic
            return kafkaTemplate.send(originalTopic, dlqMessage.getMessageId(), dlqMessage.getOriginalPayload())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("REPROCESS FAILED: Failed to send message back to topic - MessageId: {}", 
                            messageId, ex);
                        
                        // Mark as failed reprocessing
                        record.setStatus(DeadLetterStatus.REPROCESS_FAILED);
                        record.setFailureReason(ex.getMessage());
                        deadLetterRepository.save(record);
                    } else {
                        log.info("REPROCESS SUCCESS: Message sent back to original topic - MessageId: {}", messageId);
                        
                        // Update record status
                        record.setStatus(DeadLetterStatus.REPROCESSED);
                        deadLetterRepository.save(record);
                    }
                })
                .thenApply(result -> null); // Convert to CompletableFuture<Void>
            
        } catch (Exception e) {
            log.error("ERROR: Failed to reprocess dead letter message - MessageId: {}", messageId, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Get DLQ statistics and metrics
     */
    public DeadLetterQueueMetrics getDlqMetrics(String topic) {
        try {
            // Get counts from repository
            long totalMessages = deadLetterRepository.countByOriginalTopic(topic);
            long poisonMessages = deadLetterRepository.countByOriginalTopicAndPoisonMessage(topic, true);
            long pendingMessages = deadLetterRepository.countByOriginalTopicAndStatus(topic, DeadLetterStatus.PENDING);
            long reprocessedMessages = deadLetterRepository.countByOriginalTopicAndStatus(topic, DeadLetterStatus.REPROCESSED);
            
            // Get recent failure reasons
            List<String> recentFailureReasons = deadLetterRepository.findRecentFailureReasons(topic, 10);
            
            // Get hourly statistics
            List<DlqHourlyStats> hourlyStats = deadLetterRepository.getHourlyStatistics(topic, 24);
            
            return DeadLetterQueueMetrics.builder()
                .topic(topic)
                .totalMessages(totalMessages)
                .poisonMessages(poisonMessages)
                .pendingMessages(pendingMessages)
                .reprocessedMessages(reprocessedMessages)
                .successRate(totalMessages > 0 ? (double) reprocessedMessages / totalMessages : 0.0)
                .recentFailureReasons(recentFailureReasons)
                .hourlyStatistics(hourlyStats)
                .lastUpdated(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("ERROR: Failed to get DLQ metrics for topic: {}", topic, e);
            return DeadLetterQueueMetrics.builder()
                .topic(topic)
                .totalMessages(0)
                .lastUpdated(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Purge old dead letter messages (for maintenance)
     */
    public int purgeOldMessages(int daysOld) {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
            
            log.info("MAINTENANCE: Purging DLQ messages older than {} days (before {})", daysOld, cutoffDate);
            
            int purgedCount = deadLetterRepository.deleteByCreatedAtBefore(cutoffDate);
            
            log.info("MAINTENANCE: Purged {} old DLQ messages", purgedCount);
            
            return purgedCount;
            
        } catch (Exception e) {
            log.error("ERROR: Failed to purge old DLQ messages", e);
            return 0;
        }
    }
    
    // Private helper methods
    
    private String generateDlqTopicName(String originalTopic) {
        return originalTopic + DLQ_TOPIC_SUFFIX;
    }
    
    private String generateRetryTopicName(String originalTopic) {
        return originalTopic + RETRY_TOPIC_SUFFIX;
    }
    
    private boolean isPoisonMessage(DeadLetterMessage message) {
        // A message is considered poisonous if:
        // 1. It has exceeded retry attempts
        // 2. It has the same error repeatedly
        // 3. It's causing downstream failures
        
        if (message.getRetryCount() >= DEFAULT_MAX_RETRY_ATTEMPTS) {
            return true;
        }
        
        // Check for repeated identical failures
        List<ProcessingHistory> history = message.getProcessingHistory();
        if (history != null && history.size() >= 3) {
            String lastError = history.get(history.size() - 1).getErrorMessage();
            long sameErrorCount = history.stream()
                .filter(h -> h.getErrorMessage() != null && h.getErrorMessage().equals(lastError))
                .count();
            
            if (sameErrorCount >= 3) {
                return true;
            }
        }
        
        return false;
    }
    
    private List<ProcessingHistory> buildProcessingHistory(DeadLetterMessage message) {
        List<ProcessingHistory> history = message.getProcessingHistory();
        if (history == null) {
            history = new ArrayList<>();
        }
        
        // Add current failure to history
        ProcessingHistory currentFailure = ProcessingHistory.builder()
            .timestamp(LocalDateTime.now())
            .attemptNumber(message.getRetryCount() + 1)
            .errorMessage(message.getFailureReason())
            .errorType(message.getErrorType())
            .stackTrace(message.getStackTrace())
            .processingDurationMs(message.getProcessingDurationMs())
            .build();
        
        history.add(currentFailure);
        
        // Keep only last 10 entries to prevent unbounded growth
        if (history.size() > 10) {
            history = history.subList(history.size() - 10, history.size());
        }
        
        return history;
    }
    
    private DeadLetterRecord createDeadLetterRecord(DeadLetterMessage message) {
        try {
            return DeadLetterRecord.builder()
                .messageId(message.getMessageId())
                .originalTopic(message.getOriginalTopic())
                .dlqTopic(message.getDlqTopic())
                .messageData(objectMapper.writeValueAsString(message))
                .failureReason(message.getFailureReason())
                .errorType(message.getErrorType())
                .retryCount(message.getRetryCount())
                .poisonMessage(message.isPoisonMessage())
                .status(DeadLetterStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("ERROR: Failed to create DLQ record for message: {}", message.getMessageId(), e);
            throw new RuntimeException("Failed to create DLQ record", e);
        }
    }
    
    private long calculateRetryDelay(int retryCount) {
        // Exponential backoff: 30s, 60s, 120s, etc.
        return DEFAULT_RETRY_DELAY_MS * (long) Math.pow(EXPONENTIAL_BACKOFF_MULTIPLIER, retryCount - 1);
    }
    
    private void updateDlqMetrics(String topic) {
        dlqMetrics.computeIfAbsent(topic, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    private boolean isFinancialMessage(DeadLetterMessage message) {
        String topic = message.getOriginalTopic().toLowerCase();
        return topic.contains("payment") || topic.contains("transaction") || 
               topic.contains("transfer") || topic.contains("balance") ||
               topic.contains("financial") || topic.contains("banking");
    }
    
    private void logFinancialMessageFailure(DeadLetterMessage message) {
        try {
            securityAuditLogger.logSecurityEvent("FINANCIAL_MESSAGE_DLQ", "SYSTEM",
                "Financial message sent to dead letter queue",
                Map.of(
                    "messageId", message.getMessageId(),
                    "originalTopic", message.getOriginalTopic(),
                    "failureReason", message.getFailureReason(),
                    "retryCount", message.getRetryCount(),
                    "poisonMessage", message.isPoisonMessage()
                ));
                
        } catch (Exception e) {
            log.error("ERROR: Failed to log financial message DLQ event", e);
        }
    }
    
    private void storeFallbackMessage(DeadLetterMessage message, Throwable error) {
        try {
            // Store in database with failed status
            DeadLetterRecord record = createDeadLetterRecord(message);
            record.setStatus(DeadLetterStatus.DLQ_SEND_FAILED);
            record.setFailureReason("DLQ send failed: " + error.getMessage());
            deadLetterRepository.save(record);
            
            log.error("FALLBACK: Message stored in database due to DLQ send failure - MessageId: {}", 
                message.getMessageId());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to store fallback message - MessageId: {} - DATA MAY BE LOST", 
                message.getMessageId(), e);
        }
    }
}