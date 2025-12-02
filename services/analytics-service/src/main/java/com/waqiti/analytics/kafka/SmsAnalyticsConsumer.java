package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.domain.SmsAnalytics;
import com.waqiti.analytics.repository.SmsAnalyticsRepository;
import com.waqiti.analytics.service.SmsAnalyticsService;
import com.waqiti.analytics.service.AnalyticsNotificationService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class SmsAnalyticsConsumer {
    
    private final SmsAnalyticsRepository smsAnalyticsRepository;
    private final SmsAnalyticsService analyticsService;
    private final AnalyticsNotificationService notificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;
    
    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    
    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("sms_analytics_processed_total")
            .description("Total number of successfully processed SMS analytics events")
            .register(meterRegistry);
        errorCounter = Counter.builder("sms_analytics_errors_total")
            .description("Total number of SMS analytics processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("sms_analytics_processing_duration")
            .description("Time taken to process SMS analytics events")
            .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "sms-analytics",
        groupId = "sms-analytics-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "sms-analytics", fallbackMethod = "handleSmsAnalyticsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleSmsAnalyticsEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("sms-analytics-%d-%d-%d", partition, offset, System.currentTimeMillis());
        String eventKey = String.format("sms-analytics-%d-%d", partition, offset);
        
        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            log.info("Processing SMS analytics event: correlationId={}, partition={}, offset={}", 
                correlationId, partition, offset);
            
            // Clean expired entries periodically
            cleanExpiredEntries();
            
            processSmsAnalytics(eventData, correlationId);
            
            // Mark event as processed
            markEventAsProcessed(eventKey);
            
            auditService.logEvent("SMS_ANALYTICS_EVENT_PROCESSED", 
                (String) eventData.get("userId"),
                Map.of("correlationId", correlationId, "partition", partition, "offset", offset,
                    "timestamp", Instant.now()));
            
            successCounter.increment();
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process SMS analytics event: {}", e.getMessage(), e);
            
            // Send fallback event
            kafkaTemplate.send("sms-analytics-fallback-events", Map.of(
                "originalMessage", message, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));
            
            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    @CircuitBreaker(name = "sms-analytics-processing", fallbackMethod = "processSmsAnalyticsFallback")
    private void processSmsAnalytics(Map<String, Object> eventData, String correlationId) {
        String userId = (String) eventData.get("userId");
        String phoneNumber = (String) eventData.get("phoneNumber");
        String messageType = (String) eventData.get("messageType");
        String status = (String) eventData.get("status");
        String provider = (String) eventData.get("provider");
        Double cost = ((Number) eventData.getOrDefault("cost", 0.0)).doubleValue();
        Integer messageLength = ((Number) eventData.getOrDefault("messageLength", 0)).intValue();
        String errorCode = (String) eventData.get("errorCode");
        Long timestamp = ((Number) eventData.get("timestamp")).longValue();
        
        SmsAnalytics analytics = SmsAnalytics.builder()
            .userId(userId)
            .phoneNumber(phoneNumber)
            .messageType(messageType)
            .status(status)
            .provider(provider)
            .cost(cost)
            .messageLength(messageLength)
            .errorCode(errorCode)
            .sentAt(Instant.ofEpochMilli(timestamp))
            .processedAt(Instant.now())
            .correlationId(correlationId)
            .build();
        
        smsAnalyticsRepository.save(analytics);
        
        // Process analytics
        analyticsService.updateSmsMetrics(analytics);
        analyticsService.checkSmsUsagePatterns(userId, phoneNumber);
        
        // Send downstream events
        kafkaTemplate.send("sms-analytics-processed", Map.of(
            "userId", userId,
            "messageType", messageType,
            "status", status,
            "cost", cost,
            "provider", provider,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        // Check for anomalies
        if ("FAILED".equals(status) || cost > 0.50) {
            kafkaTemplate.send("sms-analytics-anomalies", Map.of(
                "userId", userId,
                "anomalyType", "FAILED".equals(status) ? "DELIVERY_FAILURE" : "HIGH_COST",
                "cost", cost,
                "status", status,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        log.info("SMS analytics processed: userId={}, messageType={}, status={}, correlationId={}", 
            userId, messageType, status, correlationId);
    }
    
    private void processSmsAnalyticsFallback(Map<String, Object> eventData, String correlationId, Exception ex) {
        log.error("Circuit breaker fallback for SMS analytics: correlationId={}, error={}", 
            correlationId, ex.getMessage());
        
        kafkaTemplate.send("sms-analytics-fallback-events", Map.of(
            "eventData", eventData,
            "correlationId", correlationId,
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "error", ex.getMessage(),
            "timestamp", Instant.now()
        ));
    }
    
    public void handleSmsAnalyticsEventFallback(
            String message, 
            int partition, 
            long offset, 
            Acknowledgment acknowledgment, 
            Exception ex) {
        
        String correlationId = String.format("sms-analytics-fallback-%d-%d-%d", partition, offset, System.currentTimeMillis());
        
        log.error("Circuit breaker fallback triggered for SMS analytics: partition={}, offset={}, error={}", 
            partition, offset, ex.getMessage());
        
        kafkaTemplate.send("sms-analytics-dlq", Map.of(
            "originalMessage", message, 
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId, 
            "timestamp", Instant.now()));
        
        try {
            notificationService.sendOperationalAlert(
                "SMS Analytics Circuit Breaker Triggered",
                String.format("SMS analytics processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }
        
        acknowledgment.acknowledge();
    }
    
    @DltHandler
    public void handleDltSmsAnalyticsEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        String correlationId = String.format("dlt-sms-analytics-%d", System.currentTimeMillis());
        
        log.error("Dead letter topic handler - SMS analytics permanently failed: topic={}, error={}", 
            topic, exceptionMessage);
        
        auditService.logEvent("SMS_ANALYTICS_DLT_EVENT", "system",
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "correlationId", correlationId, "requiresManualIntervention", true, 
                "timestamp", Instant.now()));
        
        try {
            notificationService.sendCriticalAlert(
                "SMS Analytics Dead Letter Event",
                String.format("SMS analytics sent to DLT: %s", exceptionMessage),
                Map.of("topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }
    
    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }
        
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }
        
        return true;
    }
    
    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }
    
    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry -> 
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}