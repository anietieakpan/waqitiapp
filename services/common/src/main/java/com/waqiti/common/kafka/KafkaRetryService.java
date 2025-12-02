package com.waqiti.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive Kafka retry service
 * Handles retry logic, backoff calculations, and consumer management for failed message processing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaRetryService {

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TaskScheduler taskScheduler;
    private final KafkaErrorMetrics errorMetrics;

    // Retry tracking
    private final Map<String, RetryContext> retryContexts = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledRetries = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledResumes = new ConcurrentHashMap<>();

    // Legacy configuration constants
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration[] RETRY_DELAYS = {
        Duration.ofSeconds(5),
        Duration.ofSeconds(30),
        Duration.ofMinutes(2)
    };
    
    // New configuration constants
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_DELAY_MS = 1000;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_BACKOFF_DELAY_MS = 300000; // 5 minutes
    
    // Legacy methods for backward compatibility
    
    public CompletableFuture<Boolean> retryMessage(ConsumerRecord<?, ?> record, int attemptNumber) {
        if (attemptNumber >= MAX_RETRY_ATTEMPTS) {
            log.error("Max retry attempts reached for message on topic: {}", record.topic());
            return CompletableFuture.completedFuture(false);
        }
        
        Duration delay = RETRY_DELAYS[Math.min(attemptNumber, RETRY_DELAYS.length - 1)];
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delay.toMillis());
                kafkaTemplate.send(record.topic() + "-retry", record.key() != null ? record.key().toString() : null, record.value());
                errorMetrics.recordRetryAttempt(record.topic(), attemptNumber);
                log.info("Successfully retried message on topic: {}, attempt: {}", record.topic(), attemptNumber);
                return true;
            } catch (Exception e) {
                log.error("Failed to retry message on topic: {}", record.topic(), e);
                return false;
            }
        });
    }
    
    public boolean shouldRetry(Exception e) {
        return !(e instanceof IllegalArgumentException || 
                 e instanceof NullPointerException ||
                 e instanceof ClassCastException);
    }
    
    // New comprehensive methods
    
    /**
     * Get current retry count for a specific error context
     */
    public int getRetryCount(KafkaErrorHandler.ErrorContext errorContext) {
        String retryKey = buildRetryKey(errorContext);
        RetryContext context = retryContexts.get(retryKey);
        return context != null ? context.getRetryCount() : 0;
    }
    
    /**
     * Calculate backoff delay based on retry attempt and error type
     */
    public long calculateBackoffDelay(KafkaErrorHandler.ErrorContext errorContext) {
        String retryKey = buildRetryKey(errorContext);
        RetryContext context = retryContexts.computeIfAbsent(retryKey, k -> new RetryContext());
        
        // Increment retry count
        int currentRetry = context.incrementRetryCount();
        
        // Calculate exponential backoff
        long baseDelay = getBaseDelayForTopic(errorContext.getTopic());
        double multiplier = getBackoffMultiplierForTopic(errorContext.getTopic());
        
        long delay = (long) (baseDelay * Math.pow(multiplier, currentRetry - 1));

        // Apply jitter to prevent thundering herd
        double jitter = 0.1; // 10% jitter
        // SECURITY FIX: Use SecureRandom instead of Math.random()
        long jitterAmount = (long) (delay * jitter * secureRandom.nextDouble());
        delay += jitterAmount;
        
        // Cap at maximum delay
        delay = Math.min(delay, MAX_BACKOFF_DELAY_MS);
        
        context.setLastRetryTime(LocalDateTime.now());
        context.setNextRetryDelay(delay);
        
        log.debug("Calculated backoff delay: {}ms for retry #{} of topic {}", 
            delay, currentRetry, errorContext.getTopic());
        
        return delay;
    }
    
    /**
     * Schedule a retry for failed message processing
     */
    public void scheduleRetry(KafkaErrorHandler.ErrorContext errorContext, Message<?> message, long delayMs) {
        String retryKey = buildRetryKey(errorContext);
        
        try {
            // Cancel any existing retry for this key
            cancelScheduledRetry(retryKey);
            
            // Schedule the retry
            ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeRetry(errorContext, message, retryKey),
                Instant.now().plusMillis(delayMs)
            );
            
            scheduledRetries.put(retryKey, future);
            
            // Update metrics
            errorMetrics.incrementRetryCount(
                errorContext.getTopic(), 
                errorContext.getGroupId(), 
                "scheduled_retry"
            );
            
            log.info("Scheduled retry for {} in {}ms", retryKey, delayMs);
            
        } catch (Exception e) {
            log.error("Failed to schedule retry for {}", retryKey, e);
            throw new RuntimeException("Retry scheduling failed", e);
        }
    }
    
    /**
     * Schedule consumer resume after infrastructure pause
     */
    public void scheduleConsumerResume(Consumer<?, ?> consumer, TopicPartition topicPartition, long pauseMs) {
        String resumeKey = buildResumeKey(topicPartition);
        
        try {
            // Cancel any existing resume for this key
            cancelScheduledResume(resumeKey);
            
            // Schedule the resume
            ScheduledFuture<?> future = taskScheduler.schedule(
                () -> resumeConsumer(consumer, topicPartition, resumeKey),
                Instant.now().plusMillis(pauseMs)
            );
            
            scheduledResumes.put(resumeKey, future);
            
            log.info("Scheduled consumer resume for {} in {}ms", resumeKey, pauseMs);
            
        } catch (Exception e) {
            log.error("Failed to schedule consumer resume for {}", resumeKey, e);
            throw new RuntimeException("Consumer resume scheduling failed", e);
        }
    }
    
    /**
     * Reset retry count for successful processing
     */
    public void resetRetryCount(KafkaErrorHandler.ErrorContext errorContext) {
        String retryKey = buildRetryKey(errorContext);
        retryContexts.remove(retryKey);
        cancelScheduledRetry(retryKey);
        
        log.debug("Reset retry count for {}", retryKey);
    }
    
    /**
     * Get retry statistics for monitoring
     */
    public RetryStatistics getRetryStatistics() {
        int activeRetries = scheduledRetries.size();
        int activeResumes = scheduledResumes.size();
        int totalRetryContexts = retryContexts.size();
        
        long avgRetryCount = retryContexts.values().stream()
            .mapToInt(RetryContext::getRetryCount)
            .sum() / Math.max(1, totalRetryContexts);
        
        return RetryStatistics.builder()
            .activeRetries(activeRetries)
            .activeResumes(activeResumes)
            .totalRetryContexts(totalRetryContexts)
            .averageRetryCount(avgRetryCount)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Clean up expired retry contexts
     */
    public void cleanupExpiredContexts() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        
        int removedCount = 0;
        for (Map.Entry<String, RetryContext> entry : retryContexts.entrySet()) {
            if (entry.getValue().getLastRetryTime().isBefore(cutoff)) {
                retryContexts.remove(entry.getKey());
                cancelScheduledRetry(entry.getKey());
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.info("Cleaned up {} expired retry contexts", removedCount);
        }
    }
    
    // Private methods
    
    private void executeRetry(KafkaErrorHandler.ErrorContext errorContext, Message<?> message, String retryKey) {
        try {
            log.info("Executing retry for {}", retryKey);
            
            // Remove from scheduled retries
            scheduledRetries.remove(retryKey);
            
            // Republish the message to a retry topic
            String retryTopic = errorContext.getTopic() + "-retry";
            kafkaTemplate.send(retryTopic, errorContext.getMessageKey(), message.getPayload());
            
            log.info("RETRY_EXECUTION: Successfully retried message for topic {}, partition {}, offset {}", 
                errorContext.getTopic(), errorContext.getPartition(), errorContext.getOffset());
            
            // Update metrics
            errorMetrics.incrementRetryCount(
                errorContext.getTopic(), 
                errorContext.getGroupId(), 
                "executed_retry"
            );
            
        } catch (Exception e) {
            log.error("Failed to execute retry for {}", retryKey, e);
        }
    }
    
    private void resumeConsumer(Consumer<?, ?> consumer, TopicPartition topicPartition, String resumeKey) {
        try {
            log.info("Resuming consumer for {}", resumeKey);
            
            // Remove from scheduled resumes
            scheduledResumes.remove(resumeKey);
            
            // Resume the consumer
            consumer.resume(Collections.singleton(topicPartition));
            
            log.info("CONSUMER_RESUMED: Successfully resumed consumer for topic {}, partition {}", 
                topicPartition.topic(), topicPartition.partition());
            
        } catch (Exception e) {
            log.error("Failed to resume consumer for {}", resumeKey, e);
        }
    }
    
    private void cancelScheduledRetry(String retryKey) {
        ScheduledFuture<?> future = scheduledRetries.remove(retryKey);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.debug("Cancelled scheduled retry for {}", retryKey);
        }
    }
    
    private void cancelScheduledResume(String resumeKey) {
        ScheduledFuture<?> future = scheduledResumes.remove(resumeKey);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.debug("Cancelled scheduled resume for {}", resumeKey);
        }
    }
    
    private String buildRetryKey(KafkaErrorHandler.ErrorContext errorContext) {
        return String.format("%s:%s:%d:%d", 
            errorContext.getTopic(), 
            errorContext.getGroupId(),
            errorContext.getPartition(), 
            errorContext.getOffset()
        );
    }
    
    private String buildResumeKey(TopicPartition topicPartition) {
        return String.format("%s:%d", 
            topicPartition.topic(), 
            topicPartition.partition()
        );
    }
    
    private long getBaseDelayForTopic(String topic) {
        // Critical topics get faster retries
        if (topic.contains("payment") || topic.contains("transaction") || topic.contains("fraud")) {
            return 500; // 500ms
        }
        
        return DEFAULT_INITIAL_DELAY_MS;
    }
    
    private double getBackoffMultiplierForTopic(String topic) {
        // Audit topics get more aggressive backoff
        if (topic.contains("audit") || topic.contains("compliance")) {
            return 1.5;
        }
        
        return DEFAULT_BACKOFF_MULTIPLIER;
    }
    
    // Inner classes
    
    @lombok.Data
    private static class RetryContext {
        private final AtomicInteger retryCount = new AtomicInteger(0);
        private LocalDateTime firstRetryTime = LocalDateTime.now();
        private LocalDateTime lastRetryTime = LocalDateTime.now();
        private long nextRetryDelay = DEFAULT_INITIAL_DELAY_MS;
        
        public int incrementRetryCount() {
            return retryCount.incrementAndGet();
        }
        
        public int getRetryCount() {
            return retryCount.get();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RetryStatistics {
        private int activeRetries;
        private int activeResumes;
        private int totalRetryContexts;
        private long averageRetryCount;
        private LocalDateTime timestamp;
        
        public boolean hasActiveOperations() {
            return activeRetries > 0 || activeResumes > 0;
        }
        
        public double getRetryEfficiency() {
            return totalRetryContexts == 0 ? 0.0 : (double) activeRetries / totalRetryContexts;
        }
    }
}