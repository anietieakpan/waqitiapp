package com.waqiti.analytics.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.service.SocialAnalyticsService;
import com.waqiti.analytics.service.AuditService;
import com.waqiti.analytics.service.IdempotencyService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Social Payment Events Consumer
 * 
 * CRITICAL CONSUMER - Processes social payment and peer-to-peer transaction events
 * 
 * EVENT SOURCE:
 * - social-service SocialPaymentIntegrationService: Line 594 publishes social payment events
 * 
 * BUSINESS CRITICALITY:
 * - Tracks social commerce payment transactions
 * - Analyzes peer-to-peer payment patterns
 * - Measures social payment feature adoption
 * - Supports influencer payment analytics
 * - Provides social commerce insights
 * 
 * EVENT TYPES:
 * - PAYMENT_SENT: User sent payment via social features
 * - PAYMENT_RECEIVED: User received payment via social features
 * - SPLIT_BILL_CREATED: Bill split among group
 * - SPLIT_BILL_PAID: User paid their share
 * - SOCIAL_COMMERCE_PURCHASE: Purchase through social platform
 * - INFLUENCER_PAYOUT: Payment to content creator/influencer
 * 
 * PROCESSING ACTIONS:
 * - Record social payment transaction analytics
 * - Track peer-to-peer payment volumes
 * - Analyze social commerce conversion rates
 * - Measure split bill feature engagement
 * - Track influencer earnings and payouts
 * - Generate social payment network graphs
 * 
 * BUSINESS VALUE:
 * - Product intelligence: Social payment feature performance
 * - Customer insights: P2P payment behavior
 * - Revenue analytics: Social commerce transactions
 * - Network effects: Viral payment growth tracking
 * - Influencer metrics: Creator economy analytics
 * 
 * FAILURE IMPACT:
 * - Loss of social commerce analytics
 * - Missing P2P payment insights
 * - Inability to measure feature adoption
 * - Reduced visibility into social network effects
 * - Missing influencer payment tracking
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SocialPaymentEventsConsumer {
    
    private final SocialAnalyticsService socialAnalyticsService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "social-payment-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter socialPaymentsSentCounter;
    private Counter socialPaymentsReceivedCounter;
    private Counter splitBillsCounter;
    private Timer processingTimer;
    
    public SocialPaymentEventsConsumer(
            SocialAnalyticsService socialAnalyticsService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.socialAnalyticsService = socialAnalyticsService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("social_payment_events_processed_total")
                .description("Total number of social payment events processed")
                .tag("consumer", "social-payment-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("social_payment_events_failed_total")
                .description("Total number of social payment events that failed processing")
                .tag("consumer", "social-payment-events-consumer")
                .register(meterRegistry);
        
        this.socialPaymentsSentCounter = Counter.builder("social_payments_sent_total")
                .description("Total number of social payments sent")
                .register(meterRegistry);
        
        this.socialPaymentsReceivedCounter = Counter.builder("social_payments_received_total")
                .description("Total number of social payments received")
                .register(meterRegistry);
        
        this.splitBillsCounter = Counter.builder("split_bills_total")
                .description("Total number of split bills created")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("social_payment_event_processing_duration")
                .description("Time taken to process social payment events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.social-payment-events:social-payment-events}",
        groupId = "${kafka.consumer.group-id:analytics-service-social-payment-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:2}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, timeout = 30)
    public void handleSocialPaymentEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = null;
        String correlationId = null;
        
        try {
            log.info("Received social payment event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String eventType = extractString(eventData, "eventType");
            String paymentId = extractString(eventData, "paymentId");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = paymentId != null ? paymentId : UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = UUID.randomUUID().toString();
            }
            
            if (paymentId == null || eventType == null) {
                log.error("Invalid social payment event - missing required fields: paymentId={}, eventType={}",
                        paymentId, eventType);
                auditService.logEventProcessingFailure(
                    eventId,
                    TOPIC_NAME,
                    "VALIDATION_FAILED",
                    "Missing required fields",
                    correlationId,
                    Map.of("event", eventData)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate social payment event detected - eventId: {}, paymentId: {}, correlationId: {}",
                        eventId, paymentId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processSocialPaymentEvent(eventType, paymentId, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed social payment event - eventId: {}, paymentId: {}, " +
                    "eventType: {}, correlationId: {}",
                    eventId, paymentId, eventType, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process social payment event - eventId: {}, correlationId: {}, error: {}",
                    eventId, correlationId, e.getMessage(), e);
            
            auditService.logEventProcessingFailure(
                eventId,
                TOPIC_NAME,
                "PROCESSING_FAILED",
                e.getMessage(),
                correlationId,
                Map.of(
                    "error", e.getClass().getName(),
                    "errorMessage", e.getMessage()
                )
            );
            
            throw new RuntimeException("Failed to process social payment event", e);
        }
    }
    
    @CircuitBreaker(name = "analytics", fallbackMethod = "processSocialPaymentEventFallback")
    @Retry(name = "analytics")
    private void processSocialPaymentEvent(String eventType, String paymentId,
                                          Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing social payment event - paymentId: {}, eventType: {}, correlationId: {}",
                paymentId, eventType, correlationId);
        
        switch (eventType) {
            case "PAYMENT_SENT":
                handlePaymentSent(paymentId, eventData, correlationId);
                socialPaymentsSentCounter.increment();
                break;
            case "PAYMENT_RECEIVED":
                handlePaymentReceived(paymentId, eventData, correlationId);
                socialPaymentsReceivedCounter.increment();
                break;
            case "SPLIT_BILL_CREATED":
                handleSplitBillCreated(paymentId, eventData, correlationId);
                splitBillsCounter.increment();
                break;
            case "SPLIT_BILL_PAID":
                handleSplitBillPaid(paymentId, eventData, correlationId);
                break;
            case "SOCIAL_COMMERCE_PURCHASE":
                handleSocialCommercePurchase(paymentId, eventData, correlationId);
                break;
            case "INFLUENCER_PAYOUT":
                handleInfluencerPayout(paymentId, eventData, correlationId);
                break;
            default:
                log.warn("Unknown social payment event type: {}", eventType);
        }
        
        socialAnalyticsService.recordSocialPaymentEvent(paymentId, eventType, eventData, correlationId);
        
        Map<String, Object> auditMetadata = new HashMap<>(eventData);
        auditMetadata.put("eventType", eventType);
        
        auditService.logSocialPaymentEventProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : paymentId,
            paymentId,
            eventType,
            correlationId,
            auditMetadata
        );
        
        log.info("Social payment event processed successfully - paymentId: {}, eventType: {}, correlationId: {}",
                paymentId, eventType, correlationId);
    }
    
    private void handlePaymentSent(String paymentId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing payment sent - paymentId: {}, correlationId: {}", paymentId, correlationId);
        
        String senderId = extractString(eventData, "senderId");
        String recipientId = extractString(eventData, "recipientId");
        BigDecimal amount = extractBigDecimal(eventData, "amount");
        
        socialAnalyticsService.trackSocialPaymentSent(
            paymentId,
            senderId,
            recipientId,
            amount,
            eventData,
            correlationId
        );
    }
    
    private void handlePaymentReceived(String paymentId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing payment received - paymentId: {}, correlationId: {}", paymentId, correlationId);
        
        String senderId = extractString(eventData, "senderId");
        String recipientId = extractString(eventData, "recipientId");
        BigDecimal amount = extractBigDecimal(eventData, "amount");
        
        socialAnalyticsService.trackSocialPaymentReceived(
            paymentId,
            senderId,
            recipientId,
            amount,
            eventData,
            correlationId
        );
    }
    
    private void handleSplitBillCreated(String paymentId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing split bill created - paymentId: {}, correlationId: {}", paymentId, correlationId);
        socialAnalyticsService.trackSplitBillCreated(paymentId, eventData, correlationId);
    }
    
    private void handleSplitBillPaid(String paymentId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing split bill paid - paymentId: {}, correlationId: {}", paymentId, correlationId);
        socialAnalyticsService.trackSplitBillPaid(paymentId, eventData, correlationId);
    }
    
    private void handleSocialCommercePurchase(String paymentId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing social commerce purchase - paymentId: {}, correlationId: {}", paymentId, correlationId);
        socialAnalyticsService.trackSocialCommercePurchase(paymentId, eventData, correlationId);
    }
    
    private void handleInfluencerPayout(String paymentId, Map<String, Object> eventData, String correlationId) {
        log.info("Processing influencer payout - paymentId: {}, correlationId: {}", paymentId, correlationId);
        socialAnalyticsService.trackInfluencerPayout(paymentId, eventData, correlationId);
    }
    
    private void processSocialPaymentEventFallback(String eventType, String paymentId,
                                                   Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process social payment event - paymentId: {}, " +
                "eventType: {}, correlationId: {}, error: {}",
                paymentId, eventType, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "SOCIAL_PAYMENT_EVENT_PROCESSING",
            paymentId,
            correlationId,
            Map.of(
                "eventType", eventType,
                "paymentId", paymentId,
                "error", e.getMessage()
            )
        );
    }
    
    @DltHandler
    public void handleDeadLetterTopic(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        try {
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            String eventId = extractString(eventData, "eventId");
            String paymentId = extractString(eventData, "paymentId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Social payment event moved to DLT - eventId: {}, paymentId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, paymentId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("paymentId", paymentId);
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "SOCIAL_PAYMENT_EVENT_DLT",
                exceptionMessage,
                correlationId,
                dltMetadata
            );
        } catch (Exception e) {
            log.error("Failed to process DLT event: {}", e.getMessage(), e);
        }
    }
    
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}