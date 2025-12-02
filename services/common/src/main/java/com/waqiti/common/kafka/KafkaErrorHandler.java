package com.waqiti.common.kafka;

import com.waqiti.common.exception.KafkaProcessingException;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerAwareListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive Kafka error handler for all consumers
 * Provides standardized error handling, logging, and recovery mechanisms
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaErrorHandler implements ConsumerAwareListenerErrorHandler {
    
    private final AuditService auditService;
    private final KafkaErrorMetrics errorMetrics;
    private final KafkaRetryService retryService;
    private final KafkaDeadLetterService deadLetterService;
    
    // Track error patterns and frequencies
    private final Map<String, ErrorStatistics> errorStats = new ConcurrentHashMap<>();
    
    @Override
    public Object handleError(Message<?> message, ListenerExecutionFailedException exception, 
                            Consumer<?, ?> consumer) {
        
        ErrorContext errorContext = extractErrorContext(message, exception, consumer);
        
        try {
            // Log the error with full context
            logError(errorContext, exception);
            
            // Update error metrics
            updateErrorMetrics(errorContext, exception);
            
            // Audit the error
            auditError(errorContext, exception);
            
            // Determine recovery strategy
            RecoveryAction action = determineRecoveryAction(errorContext, exception);
            
            // Execute recovery action
            return executeRecoveryAction(action, errorContext, message, exception, consumer);
            
        } catch (Exception handlingException) {
            log.error("CRITICAL: Error in error handler itself for topic {}, partition {}, offset {}", 
                errorContext.getTopic(), errorContext.getPartition(), errorContext.getOffset(), 
                handlingException);
            
            // Fallback: acknowledge and move on to prevent infinite loops
            acknowledgeMessage(message);
            return null;
        }
    }
    
    private ErrorContext extractErrorContext(Message<?> message, ListenerExecutionFailedException exception, 
                                           Consumer<?, ?> consumer) {
        
        String topic = (String) message.getHeaders().get(KafkaHeaders.RECEIVED_TOPIC);
        Integer partition = (Integer) message.getHeaders().get(KafkaHeaders.RECEIVED_PARTITION);
        Long offset = (Long) message.getHeaders().get(KafkaHeaders.OFFSET);
        String groupId = (String) message.getHeaders().get(KafkaHeaders.GROUP_ID);
        Object messageKey = message.getHeaders().get(KafkaHeaders.RECEIVED_KEY);
        
        return ErrorContext.builder()
            .topic(topic != null ? topic : "unknown")
            .partition(partition != null ? partition : -1)
            .offset(offset != null ? offset : -1L)
            .groupId(groupId != null ? groupId : "unknown")
            .messageKey(messageKey != null ? messageKey.toString() : null)
            .timestamp(LocalDateTime.now())
            .consumerInfo(getConsumerInfo(consumer))
            .payload(message.getPayload())
            .headers(message.getHeaders())
            .build();
    }
    
    private void logError(ErrorContext context, Exception exception) {
        String errorId = generateErrorId(context);
        
        if (isRetryableError(exception)) {
            log.warn("KAFKA_RETRY_ERROR [{}]: Retryable error in topic {}, partition {}, offset {} - {}", 
                errorId, context.getTopic(), context.getPartition(), context.getOffset(), 
                exception.getMessage());
        } else if (isPoisonMessage(context, exception)) {
            log.error("KAFKA_POISON_MESSAGE [{}]: Poison message detected in topic {}, partition {}, offset {} - {}", 
                errorId, context.getTopic(), context.getPartition(), context.getOffset(), 
                exception.getMessage());
        } else {
            log.error("KAFKA_PROCESSING_ERROR [{}]: Error processing message in topic {}, partition {}, offset {} - {}", 
                errorId, context.getTopic(), context.getPartition(), context.getOffset(), 
                exception.getMessage(), exception);
        }
        
        // Log additional context for complex errors
        if (log.isDebugEnabled()) {
            log.debug("Error context [{}]: Topic={}, Partition={}, Offset={}, Key={}, GroupId={}, Consumer={}", 
                errorId, context.getTopic(), context.getPartition(), context.getOffset(), 
                context.getMessageKey(), context.getGroupId(), context.getConsumerInfo());
        }
    }
    
    private void updateErrorMetrics(ErrorContext context, Exception exception) {
        try {
            errorMetrics.incrementErrorCount(context.getTopic(), context.getGroupId(), 
                exception.getClass().getSimpleName());
            
            // Update error statistics
            String statsKey = context.getTopic() + ":" + context.getGroupId();
            ErrorStatistics stats = errorStats.computeIfAbsent(statsKey, k -> new ErrorStatistics());
            stats.recordError(exception);
            
            // Check for error rate thresholds
            if (stats.getErrorRateLastMinute() > 50) { // 50 errors per minute
                log.warn("HIGH_ERROR_RATE: Topic {} group {} has high error rate: {} errors/minute", 
                    context.getTopic(), context.getGroupId(), stats.getErrorRateLastMinute());
                
                errorMetrics.recordHighErrorRate(context.getTopic(), context.getGroupId());
            }
            
        } catch (Exception e) {
            log.debug("Failed to update error metrics", e);
        }
    }
    
    private void auditError(ErrorContext context, Exception exception) {
        try {
            auditService.auditKafkaEvent(
                "KAFKA_MESSAGE_PROCESSING_ERROR",
                context.getGroupId(),
                "Kafka message processing failed",
                Map.of(
                    "topic", context.getTopic(),
                    "partition", context.getPartition(),
                    "offset", context.getOffset(),
                    "messageKey", context.getMessageKey() != null ? context.getMessageKey() : "null",
                    "errorType", exception.getClass().getSimpleName(),
                    "errorMessage", exception.getMessage() != null ? exception.getMessage() : "null",
                    "retryable", isRetryableError(exception),
                    "poisonMessage", isPoisonMessage(context, exception)
                )
            );
        } catch (Exception e) {
            log.debug("Failed to audit Kafka error", e);
        }
    }
    
    private RecoveryAction determineRecoveryAction(ErrorContext context, Exception exception) {
        // Check if it's a poison message (consistent failures)
        if (isPoisonMessage(context, exception)) {
            return RecoveryAction.SEND_TO_DLQ;
        }
        
        // Check if it's a retryable error
        if (isRetryableError(exception)) {
            int retryCount = retryService.getRetryCount(context);
            int maxRetries = getMaxRetries(context.getTopic(), exception);
            
            if (retryCount < maxRetries) {
                return RecoveryAction.RETRY_WITH_BACKOFF;
            } else {
                return RecoveryAction.SEND_TO_DLQ;
            }
        }
        
        // Check if it's a validation error
        if (isValidationError(exception)) {
            return RecoveryAction.LOG_AND_SKIP;
        }
        
        // Check if it's a temporary infrastructure error
        if (isInfrastructureError(exception)) {
            return RecoveryAction.PAUSE_AND_RETRY;
        }
        
        // Default: send to DLQ for manual investigation
        return RecoveryAction.SEND_TO_DLQ;
    }
    
    private Object executeRecoveryAction(RecoveryAction action, ErrorContext context, 
                                       Message<?> message, Exception exception, 
                                       Consumer<?, ?> consumer) {
        
        String errorId = generateErrorId(context);
        
        switch (action) {
            case RETRY_WITH_BACKOFF:
                return executeRetryWithBackoff(context, message, exception, errorId);
                
            case SEND_TO_DLQ:
                return executeSendToDLQ(context, message, exception, errorId);
                
            case LOG_AND_SKIP:
                return executeLogAndSkip(context, message, exception, errorId);
                
            case PAUSE_AND_RETRY:
                return executePauseAndRetry(context, message, exception, consumer, errorId);
                
            case ALERT_AND_STOP:
                return executeAlertAndStop(context, message, exception, errorId);
                
            default:
                log.warn("Unknown recovery action: {}, defaulting to DLQ", action);
                return executeSendToDLQ(context, message, exception, errorId);
        }
    }
    
    private Object executeRetryWithBackoff(ErrorContext context, Message<?> message, 
                                         Exception exception, String errorId) {
        try {
            long delayMs = retryService.calculateBackoffDelay(context);
            
            log.info("KAFKA_RETRY [{}]: Scheduling retry for topic {}, partition {}, offset {} with {}ms delay", 
                errorId, context.getTopic(), context.getPartition(), context.getOffset(), delayMs);
            
            retryService.scheduleRetry(context, message, delayMs);
            
            // Don't acknowledge - let retry mechanism handle it
            return null;
            
        } catch (Exception e) {
            log.error("Failed to schedule retry [{}], sending to DLQ", errorId, e);
            return executeSendToDLQ(context, message, exception, errorId);
        }
    }
    
    private Object executeSendToDLQ(ErrorContext context, Message<?> message, 
                                   Exception exception, String errorId) {
        try {
            log.warn("KAFKA_DLQ [{}]: Sending message to dead letter queue - topic {}, partition {}, offset {}", 
                errorId, context.getTopic(), context.getPartition(), context.getOffset());
            
            deadLetterService.sendToDeadLetterQueue(context, message, exception);
            
            // Acknowledge the original message
            acknowledgeMessage(message);
            
            return null;
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send message to DLQ [{}]", errorId, e);
            // Acknowledge anyway to prevent infinite loops
            acknowledgeMessage(message);
            return null;
        }
    }
    
    private Object executeLogAndSkip(ErrorContext context, Message<?> message, 
                                    Exception exception, String errorId) {
        log.warn("KAFKA_SKIP [{}]: Skipping invalid message - topic {}, partition {}, offset {} - {}", 
            errorId, context.getTopic(), context.getPartition(), context.getOffset(), 
            exception.getMessage());
        
        // Acknowledge to skip the message
        acknowledgeMessage(message);
        
        return null;
    }
    
    private Object executePauseAndRetry(ErrorContext context, Message<?> message, 
                                      Exception exception, Consumer<?, ?> consumer, String errorId) {
        try {
            long pauseMs = calculateInfrastructurePause(exception);
            
            log.warn("KAFKA_PAUSE [{}]: Pausing consumer for {}ms due to infrastructure error - topic {}, partition {}", 
                errorId, pauseMs, context.getTopic(), context.getPartition());
            
            // Pause the specific partition
            TopicPartition topicPartition = new TopicPartition(context.getTopic(), context.getPartition());
            consumer.pause(java.util.Collections.singleton(topicPartition));
            
            // Schedule resume
            retryService.scheduleConsumerResume(consumer, topicPartition, pauseMs);
            
            return null;
            
        } catch (Exception e) {
            log.error("Failed to pause consumer [{}], sending to DLQ", errorId, e);
            return executeSendToDLQ(context, message, exception, errorId);
        }
    }
    
    private Object executeAlertAndStop(ErrorContext context, Message<?> message, 
                                     Exception exception, String errorId) {
        log.error("KAFKA_CRITICAL [{}]: Critical error requires immediate attention - topic {}, partition {}, offset {}", 
            errorId, context.getTopic(), context.getPartition(), context.getOffset(), exception);
        
        // Send alert to operations team
        errorMetrics.recordCriticalError(context.getTopic(), context.getGroupId(), exception);
        
        // Acknowledge to prevent reprocessing during investigation
        acknowledgeMessage(message);
        
        return null;
    }
    
    // Utility methods
    
    private boolean isRetryableError(Exception exception) {
        return exception instanceof org.springframework.dao.DataAccessException ||
               exception instanceof org.springframework.web.client.ResourceAccessException ||
               exception instanceof java.net.SocketTimeoutException ||
               exception instanceof org.springframework.transaction.TransactionException ||
               exception.getMessage() != null && (
                   exception.getMessage().contains("timeout") ||
                   exception.getMessage().contains("connection") ||
                   exception.getMessage().contains("temporary")
               );
    }
    
    private boolean isPoisonMessage(ErrorContext context, Exception exception) {
        String statsKey = context.getTopic() + ":" + context.getPartition() + ":" + context.getOffset();
        ErrorStatistics stats = errorStats.get(statsKey);
        
        return stats != null && stats.getConsecutiveFailures() >= 3;
    }
    
    private boolean isValidationError(Exception exception) {
        return exception instanceof jakarta.validation.ValidationException ||
               exception instanceof IllegalArgumentException ||
               exception instanceof com.fasterxml.jackson.core.JsonProcessingException ||
               exception.getMessage() != null && (
                   exception.getMessage().contains("validation") ||
                   exception.getMessage().contains("invalid") ||
                   exception.getMessage().contains("malformed")
               );
    }
    
    private boolean isInfrastructureError(Exception exception) {
        return exception instanceof java.sql.SQLException ||
               exception instanceof org.springframework.dao.DataAccessResourceFailureException ||
               exception instanceof org.springframework.data.redis.RedisConnectionFailureException ||
               exception.getMessage() != null && (
                   exception.getMessage().contains("database") ||
                   exception.getMessage().contains("redis") ||
                   exception.getMessage().contains("network")
               );
    }
    
    private int getMaxRetries(String topic, Exception exception) {
        // Critical topics get more retries
        if (topic.contains("payment") || topic.contains("transaction") || topic.contains("compliance")) {
            return isRetryableError(exception) ? 5 : 3;
        }
        
        return isRetryableError(exception) ? 3 : 1;
    }
    
    private long calculateInfrastructurePause(Exception exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("database")) {
            return 30000; // 30 seconds for database issues
        }
        return 10000; // 10 seconds default
    }
    
    private void acknowledgeMessage(Message<?> message) {
        try {
            Acknowledgment ack = (Acknowledgment) message.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT);
            if (ack != null) {
                ack.acknowledge();
            }
        } catch (Exception e) {
            log.debug("Failed to acknowledge message", e);
        }
    }
    
    private String generateErrorId(ErrorContext context) {
        return String.format("%s-%d-%d-%s", 
            context.getTopic(), 
            context.getPartition(), 
            context.getOffset(),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
        );
    }
    
    private String getConsumerInfo(Consumer<?, ?> consumer) {
        if (consumer != null) {
            return consumer.getClass().getSimpleName() + "@" + Integer.toHexString(consumer.hashCode());
        }
        return "unknown";
    }
    
    // Enums and data classes
    
    public enum RecoveryAction {
        RETRY_WITH_BACKOFF,
        SEND_TO_DLQ,
        LOG_AND_SKIP,
        PAUSE_AND_RETRY,
        ALERT_AND_STOP
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ErrorContext {
        private String topic;
        private int partition;
        private long offset;
        private String groupId;
        private String messageKey;
        private LocalDateTime timestamp;
        private String consumerInfo;
        private Object payload;
        private org.springframework.messaging.MessageHeaders headers;
    }
    
    @lombok.Data
    public static class ErrorStatistics {
        private int totalErrors = 0;
        private int consecutiveFailures = 0;
        private LocalDateTime lastError;
        private LocalDateTime firstError;
        private final java.util.concurrent.atomic.AtomicInteger errorsLastMinute = new java.util.concurrent.atomic.AtomicInteger(0);
        
        public void recordError(Exception exception) {
            totalErrors++;
            consecutiveFailures++;
            lastError = LocalDateTime.now();
            if (firstError == null) {
                firstError = lastError;
            }
            
            errorsLastMinute.incrementAndGet();
            
            // Reset minute counter periodically
            if (lastError.isAfter(firstError.plusMinutes(1))) {
                errorsLastMinute.set(1);
                firstError = lastError;
            }
        }
        
        public void recordSuccess() {
            consecutiveFailures = 0;
        }
        
        public int getErrorRateLastMinute() {
            return errorsLastMinute.get();
        }
    }
}