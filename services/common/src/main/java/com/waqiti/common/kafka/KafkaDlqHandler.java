package com.waqiti.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class KafkaDlqHandler {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    
    private final Map<String, DlqMessageMetadata> dlqMessageCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> topicRetryCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorTypeCounters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(5);
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 5000;
    private static final int MAX_RETRY_DELAY_MS = 60000;
    private static final String DLQ_SUFFIX = ".dlq";
    private static final String RETRY_SUFFIX = ".retry";
    private static final String PERMANENT_FAILURE_SUFFIX = ".permanent-failure";
    private static final String DLQ_REDIS_PREFIX = "dlq:message:";
    private static final String DLQ_STATS_PREFIX = "dlq:stats:";
    
    public KafkaDlqHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }
    
    @CircuitBreaker(name = "dlq-handler", fallbackMethod = "sendToDlqFallback")
    @Retry(name = "dlq-handler")
    public void sendToDlq(ConsumerRecord<String, ?> record, Exception exception, String originalTopic) {
        Instant startTime = Instant.now();
        
        log.warn("Sending message to DLQ: topic={} partition={} offset={} error={}", 
                originalTopic, record.partition(), record.offset(), exception.getMessage());
        
        String messageId = generateMessageId(originalTopic, record.partition(), record.offset());
        ErrorClassification classification = classifyError(exception);
        
        DlqMessage dlqMessage = DlqMessage.builder()
                .messageId(messageId)
                .originalTopic(originalTopic)
                .originalPartition(record.partition())
                .originalOffset(record.offset())
                .originalKey(record.key() != null ? record.key().toString() : null)
                .originalValue(record.value())
                .originalHeaders(extractHeaders(record.headers()))
                .errorMessage(exception.getMessage())
                .errorClass(exception.getClass().getName())
                .errorStackTrace(getStackTraceAsString(exception))
                .errorType(classification.getErrorType())
                .severity(classification.getSeverity())
                .retryable(classification.isRetryable())
                .retryCount(0)
                .maxRetries(MAX_RETRY_ATTEMPTS)
                .timestamp(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .build();
        
        String dlqTopic = determineDlqTopic(originalTopic, classification);
        
        sendToDlqTopic(dlqTopic, record.key() != null ? record.key().toString() : messageId, dlqMessage);
        
        storeDlqMetadata(messageId, dlqMessage);
        
        if (classification.isRetryable()) {
            scheduleRetry(dlqMessage, INITIAL_RETRY_DELAY_MS);
        }
        
        recordDlqMetrics(originalTopic, classification.getErrorType(), startTime);
        
        log.info("Message sent to DLQ topic: {} messageId={} errorType={} retryable={}", 
                dlqTopic, messageId, classification.getErrorType(), classification.isRetryable());
    }
    
    @CircuitBreaker(name = "dlq-handler", fallbackMethod = "sendToDlqWithMetadataFallback")
    @Retry(name = "dlq-handler")
    public void sendToDlq(String topic, String key, Object value, String errorMessage, String context) {
        Instant startTime = Instant.now();
        
        log.warn("Sending message to DLQ: topic={} key={} error={} context={}", 
                topic, key, errorMessage, context);
        
        String messageId = generateMessageId(topic, key);
        ErrorClassification classification = classifyErrorByMessage(errorMessage);
        
        DlqMessage dlqMessage = DlqMessage.builder()
                .messageId(messageId)
                .originalTopic(topic)
                .originalKey(key)
                .originalValue(value)
                .errorMessage(errorMessage)
                .errorType(classification.getErrorType())
                .severity(classification.getSeverity())
                .retryable(classification.isRetryable())
                .retryCount(0)
                .maxRetries(MAX_RETRY_ATTEMPTS)
                .context(context)
                .timestamp(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .build();
        
        String dlqTopic = determineDlqTopic(topic, classification);
        
        sendToDlqTopic(dlqTopic, key != null ? key : messageId, dlqMessage);
        
        storeDlqMetadata(messageId, dlqMessage);
        
        if (classification.isRetryable()) {
            scheduleRetry(dlqMessage, INITIAL_RETRY_DELAY_MS);
        }
        
        recordDlqMetrics(topic, classification.getErrorType(), startTime);
        
        log.info("Message sent to DLQ topic: {} messageId={} errorType={} retryable={}", 
                dlqTopic, messageId, classification.getErrorType(), classification.isRetryable());
    }
    
    @CircuitBreaker(name = "dlq-handler", fallbackMethod = "sendToDlqWithMetadataFallback")
    @Retry(name = "dlq-handler")
    public void sendToDlqWithMetadata(String topic, String key, Object value, Exception exception, 
                                     Map<String, Object> metadata) {
        Instant startTime = Instant.now();
        
        log.warn("Sending message with metadata to DLQ: topic={} error={}", topic, exception.getMessage());
        
        String messageId = generateMessageId(topic, key);
        ErrorClassification classification = classifyError(exception);
        
        DlqMessage dlqMessage = DlqMessage.builder()
                .messageId(messageId)
                .originalTopic(topic)
                .originalKey(key)
                .originalValue(value)
                .errorMessage(exception.getMessage())
                .errorClass(exception.getClass().getName())
                .errorStackTrace(getStackTraceAsString(exception))
                .errorType(classification.getErrorType())
                .severity(classification.getSeverity())
                .retryable(classification.isRetryable())
                .retryCount(0)
                .maxRetries(MAX_RETRY_ATTEMPTS)
                .metadata(metadata)
                .timestamp(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .build();
        
        String dlqTopic = determineDlqTopic(topic, classification);
        
        sendToDlqTopic(dlqTopic, key != null ? key : messageId, dlqMessage);
        
        storeDlqMetadata(messageId, dlqMessage);
        
        if (classification.isRetryable()) {
            scheduleRetry(dlqMessage, INITIAL_RETRY_DELAY_MS);
        }
        
        recordDlqMetrics(topic, classification.getErrorType(), startTime);
        
        log.info("Message with metadata sent to DLQ topic: {} messageId={} errorType={} retryable={}", 
                dlqTopic, messageId, classification.getErrorType(), classification.isRetryable());
    }
    
    public void retryDlqMessage(String messageId) {
        DlqMessage dlqMessage = retrieveDlqMessage(messageId);
        
        if (dlqMessage == null) {
            log.error("Cannot retry DLQ message - message not found: messageId={}", messageId);
            return;
        }
        
        if (dlqMessage.getRetryCount() >= dlqMessage.getMaxRetries()) {
            log.warn("Max retries exceeded for DLQ message - moving to permanent failure: messageId={}", messageId);
            moveToPermanentFailure(dlqMessage);
            return;
        }
        
        log.info("Retrying DLQ message: messageId={} retryCount={}/{}", 
                messageId, dlqMessage.getRetryCount() + 1, dlqMessage.getMaxRetries());
        
        try {
            String retryTopic = dlqMessage.getOriginalTopic() + RETRY_SUFFIX;
            
            Message<Object> message = MessageBuilder
                    .withPayload(dlqMessage.getOriginalValue())
                    .setHeader(KafkaHeaders.TOPIC, retryTopic)
                    .setHeader(KafkaHeaders.KEY, dlqMessage.getOriginalKey())
                    .setHeader("dlq-message-id", messageId)
                    .setHeader("dlq-retry-count", dlqMessage.getRetryCount() + 1)
                    .setHeader("dlq-original-topic", dlqMessage.getOriginalTopic())
                    .build();
            
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(message);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    dlqMessage.setRetryCount(dlqMessage.getRetryCount() + 1);
                    dlqMessage.setLastRetryAt(Instant.now());
                    storeDlqMetadata(messageId, dlqMessage);
                    
                    recordRetryMetrics(dlqMessage.getOriginalTopic(), true);
                    
                    log.info("DLQ message retry sent successfully: messageId={} retryCount={}", 
                            messageId, dlqMessage.getRetryCount());
                } else {
                    log.error("Failed to send DLQ message retry: messageId={} error={}", messageId, ex.getMessage());
                    recordRetryMetrics(dlqMessage.getOriginalTopic(), false);
                    
                    int nextRetryDelay = calculateExponentialBackoff(dlqMessage.getRetryCount());
                    scheduleRetry(dlqMessage, nextRetryDelay);
                }
            });
            
        } catch (Exception e) {
            log.error("Error retrying DLQ message: messageId={} error={}", messageId, e.getMessage(), e);
            recordRetryMetrics(dlqMessage.getOriginalTopic(), false);
        }
    }
    
    private void scheduleRetry(DlqMessage dlqMessage, int delayMs) {
        if (dlqMessage.getRetryCount() >= dlqMessage.getMaxRetries()) {
            log.warn("Max retries reached - not scheduling retry: messageId={}", dlqMessage.getMessageId());
            moveToPermanentFailure(dlqMessage);
            return;
        }
        
        log.info("Scheduling retry for DLQ message: messageId={} delayMs={} retryCount={}/{}", 
                dlqMessage.getMessageId(), delayMs, dlqMessage.getRetryCount() + 1, dlqMessage.getMaxRetries());
        
        retryScheduler.schedule(
                () -> retryDlqMessage(dlqMessage.getMessageId()),
                delayMs,
                TimeUnit.MILLISECONDS
        );
    }
    
    private void moveToPermanentFailure(DlqMessage dlqMessage) {
        log.error("Moving DLQ message to permanent failure: messageId={} originalTopic={} retryCount={}", 
                dlqMessage.getMessageId(), dlqMessage.getOriginalTopic(), dlqMessage.getRetryCount());
        
        String permanentFailureTopic = dlqMessage.getOriginalTopic() + PERMANENT_FAILURE_SUFFIX;
        
        dlqMessage.setPermanentFailure(true);
        dlqMessage.setPermanentFailureAt(Instant.now());
        
        sendToDlqTopic(permanentFailureTopic, dlqMessage.getOriginalKey(), dlqMessage);
        
        storeDlqMetadata(dlqMessage.getMessageId(), dlqMessage);
        
        sendPermanentFailureAlert(dlqMessage);
        
        recordPermanentFailureMetrics(dlqMessage.getOriginalTopic());
    }
    
    private void sendToDlqTopic(String dlqTopic, String key, DlqMessage dlqMessage) {
        try {
            Message<DlqMessage> message = MessageBuilder
                    .withPayload(dlqMessage)
                    .setHeader(KafkaHeaders.TOPIC, dlqTopic)
                    .setHeader(KafkaHeaders.KEY, key)
                    .setHeader("dlq-message-id", dlqMessage.getMessageId())
                    .setHeader("dlq-error-type", dlqMessage.getErrorType())
                    .setHeader("dlq-severity", dlqMessage.getSeverity())
                    .setHeader("dlq-retryable", dlqMessage.isRetryable())
                    .build();
            
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(message);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("DLQ message sent successfully: topic={} messageId={}", dlqTopic, dlqMessage.getMessageId());
                } else {
                    log.error("Failed to send DLQ message: topic={} messageId={} error={}", 
                            dlqTopic, dlqMessage.getMessageId(), ex.getMessage());
                }
            });
            
        } catch (Exception e) {
            log.error("Error sending message to DLQ topic: topic={} error={}", dlqTopic, e.getMessage(), e);
            throw new RuntimeException("Failed to send message to DLQ", e);
        }
    }
    
    private ErrorClassification classifyError(Exception exception) {
        String errorClass = exception.getClass().getSimpleName();
        String errorMessage = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
        
        if (errorMessage.contains("timeout") || errorClass.contains("Timeout")) {
            return new ErrorClassification("TIMEOUT", "HIGH", true);
        } else if (errorMessage.contains("connection") || errorClass.contains("Connection")) {
            return new ErrorClassification("CONNECTION_ERROR", "HIGH", true);
        } else if (errorMessage.contains("unavailable") || errorClass.contains("Unavailable")) {
            return new ErrorClassification("SERVICE_UNAVAILABLE", "MEDIUM", true);
        } else if (errorMessage.contains("duplicate") || errorClass.contains("Duplicate")) {
            return new ErrorClassification("DUPLICATE_MESSAGE", "LOW", false);
        } else if (errorMessage.contains("validation") || errorClass.contains("Validation")) {
            return new ErrorClassification("VALIDATION_ERROR", "MEDIUM", false);
        } else if (errorMessage.contains("authorization") || errorClass.contains("Authorization")) {
            return new ErrorClassification("AUTHORIZATION_ERROR", "CRITICAL", false);
        } else if (errorMessage.contains("serialization") || errorClass.contains("Serialization")) {
            return new ErrorClassification("SERIALIZATION_ERROR", "MEDIUM", false);
        } else if (errorMessage.contains("database") || errorClass.contains("Data")) {
            return new ErrorClassification("DATABASE_ERROR", "HIGH", true);
        } else if (errorMessage.contains("rate limit") || errorClass.contains("RateLimit")) {
            return new ErrorClassification("RATE_LIMIT_EXCEEDED", "MEDIUM", true);
        } else {
            return new ErrorClassification("UNKNOWN_ERROR", "HIGH", true);
        }
    }
    
    private ErrorClassification classifyErrorByMessage(String errorMessage) {
        if (errorMessage == null) {
            return new ErrorClassification("UNKNOWN_ERROR", "HIGH", true);
        }
        
        String lowerError = errorMessage.toLowerCase();
        
        if (lowerError.contains("critical") || lowerError.contains("manual intervention")) {
            return new ErrorClassification("CRITICAL_ERROR", "CRITICAL", false);
        } else if (lowerError.contains("timeout")) {
            return new ErrorClassification("TIMEOUT", "HIGH", true);
        } else if (lowerError.contains("connection")) {
            return new ErrorClassification("CONNECTION_ERROR", "HIGH", true);
        } else if (lowerError.contains("unavailable")) {
            return new ErrorClassification("SERVICE_UNAVAILABLE", "MEDIUM", true);
        } else if (lowerError.contains("duplicate")) {
            return new ErrorClassification("DUPLICATE_MESSAGE", "LOW", false);
        } else if (lowerError.contains("validation")) {
            return new ErrorClassification("VALIDATION_ERROR", "MEDIUM", false);
        } else if (lowerError.contains("rate limit")) {
            return new ErrorClassification("RATE_LIMIT_EXCEEDED", "MEDIUM", true);
        } else {
            return new ErrorClassification("UNKNOWN_ERROR", "HIGH", true);
        }
    }
    
    private String determineDlqTopic(String originalTopic, ErrorClassification classification) {
        if (classification.getSeverity().equals("CRITICAL")) {
            return originalTopic + ".critical" + DLQ_SUFFIX;
        } else if (!classification.isRetryable()) {
            return originalTopic + ".non-retryable" + DLQ_SUFFIX;
        } else {
            return originalTopic + DLQ_SUFFIX;
        }
    }
    
    private void storeDlqMetadata(String messageId, DlqMessage dlqMessage) {
        try {
            String redisKey = DLQ_REDIS_PREFIX + messageId;
            redisTemplate.opsForValue().set(redisKey, dlqMessage, Duration.ofDays(7));
            
            dlqMessageCache.put(messageId, new DlqMessageMetadata(dlqMessage.getOriginalTopic(), 
                    dlqMessage.getErrorType(), dlqMessage.getRetryCount(), Instant.now()));
            
            String statsKey = DLQ_STATS_PREFIX + dlqMessage.getOriginalTopic() + ":" + dlqMessage.getErrorType();
            redisTemplate.opsForValue().increment(statsKey);
            
            topicRetryCounters.computeIfAbsent(dlqMessage.getOriginalTopic(), k -> new AtomicInteger(0))
                    .incrementAndGet();
            
            errorTypeCounters.computeIfAbsent(dlqMessage.getErrorType(), k -> new AtomicLong(0))
                    .incrementAndGet();
            
        } catch (Exception e) {
            log.error("Error storing DLQ metadata: messageId={} error={}", messageId, e.getMessage(), e);
        }
    }
    
    private DlqMessage retrieveDlqMessage(String messageId) {
        try {
            String redisKey = DLQ_REDIS_PREFIX + messageId;
            Object cachedMessage = redisTemplate.opsForValue().get(redisKey);
            
            if (cachedMessage instanceof DlqMessage) {
                return (DlqMessage) cachedMessage;
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error retrieving DLQ message: messageId={} error={}", messageId, e.getMessage(), e);
            return null;
        }
    }
    
    private Map<String, String> extractHeaders(Headers headers) {
        Map<String, String> headerMap = new HashMap<>();
        headers.forEach(header -> {
            String value = new String(header.value(), StandardCharsets.UTF_8);
            headerMap.put(header.key(), value);
        });
        return headerMap;
    }
    
    private String generateMessageId(String topic, int partition, long offset) {
        return String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
    }
    
    private String generateMessageId(String topic, String key) {
        return String.format("%s-%s-%d", topic, key != null ? key : "null", System.currentTimeMillis());
    }
    
    /**
     * Get stack trace as string for internal debugging/storage only.
     *
     * SECURITY FIX: Uses StringWriter instead of printStackTrace() to avoid
     * PCI DSS 6.5.5 violation. Stack trace stored internally only, never exposed to clients.
     */
    private String getStackTraceAsString(Exception exception) {
        if (exception == null) {
            return "No exception provided";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName());

        String message = exception.getMessage();
        if (message != null) {
            sb.append(": ").append(message);
        }
        sb.append("\n");

        // Get stack trace elements
        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        // Include cause chain
        Throwable cause = exception.getCause();
        while (cause != null) {
            sb.append("Caused by: ").append(cause.getClass().getName());
            String causeMessage = cause.getMessage();
            if (causeMessage != null) {
                sb.append(": ").append(causeMessage);
            }
            sb.append("\n");

            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }

            cause = cause.getCause();
        }

        return sb.toString();
    }
    
    private int calculateExponentialBackoff(int retryCount) {
        int delay = INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, retryCount);
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }
    
    private void sendPermanentFailureAlert(DlqMessage dlqMessage) {
        log.error("ALERT: Permanent DLQ failure - messageId={} originalTopic={} errorType={} retries={}", 
                dlqMessage.getMessageId(), dlqMessage.getOriginalTopic(), 
                dlqMessage.getErrorType(), dlqMessage.getRetryCount());
    }
    
    private void recordDlqMetrics(String topic, String errorType, Instant startTime) {
        Counter.builder("dlq.messages.sent")
                .tag("topic", topic)
                .tag("error_type", errorType)
                .description("Total DLQ messages sent")
                .register(meterRegistry)
                .increment();
        
        Timer.builder("dlq.send.latency")
                .tag("topic", topic)
                .description("DLQ message send latency")
                .register(meterRegistry)
                .record(Duration.between(startTime, Instant.now()));
    }
    
    private void recordRetryMetrics(String topic, boolean success) {
        Counter.builder("dlq.retries")
                .tag("topic", topic)
                .tag("status", success ? "success" : "failure")
                .description("DLQ message retry attempts")
                .register(meterRegistry)
                .increment();
    }
    
    private void recordPermanentFailureMetrics(String topic) {
        Counter.builder("dlq.permanent.failures")
                .tag("topic", topic)
                .description("Permanent DLQ failures")
                .register(meterRegistry)
                .increment();
    }
    
    @Scheduled(fixedRate = 300000)
    public void publishDlqStatistics() {
        log.info("DLQ Statistics - Topics: {} Total Errors: {} Cached Messages: {}", 
                topicRetryCounters.size(), 
                errorTypeCounters.values().stream().mapToLong(AtomicLong::get).sum(),
                dlqMessageCache.size());
        
        errorTypeCounters.forEach((errorType, count) -> {
            log.info("Error Type: {} Count: {}", errorType, count.get());
        });
    }
    
    @Scheduled(fixedRate = 600000)
    public void cleanupExpiredMessages() {
        Instant now = Instant.now();
        List<String> expiredMessageIds = new ArrayList<>();
        
        dlqMessageCache.forEach((messageId, metadata) -> {
            if (Duration.between(metadata.getTimestamp(), now).toDays() >= 7) {
                expiredMessageIds.add(messageId);
            }
        });
        
        expiredMessageIds.forEach(messageId -> {
            dlqMessageCache.remove(messageId);
            String redisKey = DLQ_REDIS_PREFIX + messageId;
            redisTemplate.delete(redisKey);
        });
        
        if (!expiredMessageIds.isEmpty()) {
            log.info("Cleaned up {} expired DLQ messages", expiredMessageIds.size());
        }
    }
    
    public DlqStatistics getDlqStatistics(String topic) {
        int totalMessages = topicRetryCounters.getOrDefault(topic, new AtomicInteger(0)).get();
        
        Map<String, Long> errorTypeCounts = new HashMap<>();
        errorTypeCounters.forEach((errorType, count) -> {
            errorTypeCounts.put(errorType, count.get());
        });
        
        long cachedMessages = dlqMessageCache.values().stream()
                .filter(metadata -> metadata.getTopic().equals(topic))
                .count();
        
        return DlqStatistics.builder()
                .topic(topic)
                .totalMessages(totalMessages)
                .cachedMessages(cachedMessages)
                .errorTypeCounts(errorTypeCounts)
                .timestamp(Instant.now())
                .build();
    }
    
    private void sendToDlqFallback(ConsumerRecord<String, ?> record, Exception exception, 
                                  String originalTopic, Exception fallbackException) {
        log.error("DLQ handler unavailable - message not sent to DLQ (fallback): topic={} error={}", 
                originalTopic, fallbackException.getMessage());
    }
    
    private void sendToDlqFallback(String topic, String key, Object value, String errorMessage, 
                                  String context, Exception fallbackException) {
        log.error("DLQ handler unavailable - message not sent to DLQ (fallback): topic={} error={}", 
                topic, fallbackException.getMessage());
    }
    
    private void sendToDlqWithMetadataFallback(String topic, String key, Object value, Exception exception, 
                                              Map<String, Object> metadata, Exception fallbackException) {
        log.error("DLQ handler unavailable - message with metadata not sent to DLQ (fallback): topic={} error={}", 
                topic, fallbackException.getMessage());
    }
    
    private static class ErrorClassification {
        private final String errorType;
        private final String severity;
        private final boolean retryable;
        
        public ErrorClassification(String errorType, String severity, boolean retryable) {
            this.errorType = errorType;
            this.severity = severity;
            this.retryable = retryable;
        }
        
        public String getErrorType() { return errorType; }
        public String getSeverity() { return severity; }
        public boolean isRetryable() { return retryable; }
    }
    
    private static class DlqMessageMetadata {
        private final String topic;
        private final String errorType;
        private final int retryCount;
        private final Instant timestamp;
        
        public DlqMessageMetadata(String topic, String errorType, int retryCount, Instant timestamp) {
            this.topic = topic;
            this.errorType = errorType;
            this.retryCount = retryCount;
            this.timestamp = timestamp;
        }
        
        public String getTopic() { return topic; }
        public String getErrorType() { return errorType; }
        public int getRetryCount() { return retryCount; }
        public Instant getTimestamp() { return timestamp; }
    }
}