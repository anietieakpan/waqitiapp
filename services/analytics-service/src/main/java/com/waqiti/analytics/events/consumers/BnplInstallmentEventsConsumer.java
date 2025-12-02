package com.waqiti.analytics.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.service.BnplAnalyticsService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BNPL Installment Events Consumer
 * 
 * CRITICAL CONSUMER - Processes Buy Now Pay Later installment payment analytics
 * 
 * EVENT SOURCE:
 * - bnpl-service PaymentService: Line 309 publishes BNPL installment events
 * 
 * BUSINESS CRITICALITY:
 * - Tracks BNPL installment payment lifecycle
 * - Analyzes BNPL payment patterns and defaults
 * - Monitors BNPL portfolio health and risk
 * - Supports BNPL credit decisioning
 * - Enables BNPL revenue and loss forecasting
 * 
 * INSTALLMENT EVENT TYPES:
 * - BNPL_INSTALLMENT_PROCESSED: Installment payment processed
 * - INSTALLMENT_DUE: Payment due notification
 * - INSTALLMENT_PAID: On-time installment payment
 * - INSTALLMENT_LATE: Late payment detected
 * - INSTALLMENT_MISSED: Payment missed (default risk)
 * - INSTALLMENT_DEFAULTED: Account in default
 * - INSTALLMENT_RESTRUCTURED: Payment plan modified
 * 
 * PROCESSING ACTIONS:
 * - Track installment payment metrics
 * - Calculate BNPL portfolio performance
 * - Analyze default rates and risk profiles
 * - Monitor payment timeliness
 * - Forecast BNPL revenue and losses
 * - Segment BNPL customers by payment behavior
 * 
 * BUSINESS VALUE:
 * - Financial products: BNPL installment tracking
 * - Risk management: Default prediction
 * - Revenue optimization: BNPL performance analysis
 * - Customer insights: Payment behavior patterns
 * - Credit decisioning: BNPL eligibility models
 * 
 * FAILURE IMPACT:
 * - Loss of BNPL payment visibility
 * - Inability to track defaults
 * - Missing BNPL portfolio metrics
 * - Degraded credit decision quality
 * - Reduced BNPL revenue optimization
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time analytics processing
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BnplInstallmentEventsConsumer {
    
    private final BnplAnalyticsService bnplAnalyticsService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "bnpl-installment-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter installmentsPaidCounter;
    private Counter installmentsMissedCounter;
    private Counter installmentsDefaultedCounter;
    private Timer processingTimer;
    
    public BnplInstallmentEventsConsumer(
            BnplAnalyticsService bnplAnalyticsService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.bnplAnalyticsService = bnplAnalyticsService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("bnpl_installment_events_processed_total")
                .description("Total number of BNPL installment events processed")
                .tag("consumer", "bnpl-installment-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("bnpl_installment_events_failed_total")
                .description("Total number of BNPL installment events that failed processing")
                .tag("consumer", "bnpl-installment-events-consumer")
                .register(meterRegistry);
        
        this.installmentsPaidCounter = Counter.builder("bnpl_installments_paid_total")
                .description("Total number of BNPL installments paid on time")
                .register(meterRegistry);
        
        this.installmentsMissedCounter = Counter.builder("bnpl_installments_missed_total")
                .description("Total number of BNPL installments missed")
                .register(meterRegistry);
        
        this.installmentsDefaultedCounter = Counter.builder("bnpl_installments_defaulted_total")
                .description("Total number of BNPL installments defaulted")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("bnpl_installment_event_processing_duration")
                .description("Time taken to process BNPL installment events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.bnpl-installment-events:bnpl-installment-events}",
        groupId = "${kafka.consumer.group-id:analytics-service-bnpl-installment-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:3}"
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
    public void handleBnplInstallmentEvent(
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
            log.info("Received BNPL installment event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String transactionId = extractString(eventData, "transactionId");
            String eventType = extractString(eventData, "eventType");
            String bnplPlanId = extractString(eventData, "bnplPlanId");
            String status = extractString(eventData, "status");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = transactionId != null ? transactionId : UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = bnplPlanId;
            }
            
            if (transactionId == null || eventType == null) {
                log.error("Invalid BNPL installment event - missing required fields: transactionId={}, eventType={}",
                        transactionId, eventType);
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
                log.warn("Duplicate BNPL installment event detected - eventId: {}, transactionId: {}, correlationId: {}",
                        eventId, transactionId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processBnplInstallmentEvent(transactionId, eventType, bnplPlanId, status, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed BNPL installment event - eventId: {}, transactionId: {}, " +
                    "eventType: {}, status: {}, correlationId: {}",
                    eventId, transactionId, eventType, status, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process BNPL installment event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process BNPL installment event", e);
        }
    }
    
    @CircuitBreaker(name = "analytics", fallbackMethod = "processBnplInstallmentEventFallback")
    @Retry(name = "analytics")
    private void processBnplInstallmentEvent(String transactionId, String eventType, String bnplPlanId,
                                            String status, Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing BNPL installment event - transactionId: {}, eventType: {}, bnplPlanId: {}, " +
                "status: {}, correlationId: {}",
                transactionId, eventType, bnplPlanId, status, correlationId);
        
        bnplAnalyticsService.recordInstallmentEvent(
            transactionId,
            eventType,
            bnplPlanId,
            status,
            eventData,
            correlationId
        );
        
        switch (eventType) {
            case "BNPL_INSTALLMENT_PROCESSED":
            case "INSTALLMENT_PAID":
                installmentsPaidCounter.increment();
                handleInstallmentPaid(transactionId, bnplPlanId, eventData, correlationId);
                break;
            case "INSTALLMENT_MISSED":
            case "INSTALLMENT_LATE":
                installmentsMissedCounter.increment();
                handleInstallmentMissed(transactionId, bnplPlanId, eventData, correlationId);
                break;
            case "INSTALLMENT_DEFAULTED":
                installmentsDefaultedCounter.increment();
                handleInstallmentDefaulted(transactionId, bnplPlanId, eventData, correlationId);
                break;
            case "INSTALLMENT_DUE":
                handleInstallmentDue(transactionId, bnplPlanId, eventData, correlationId);
                break;
            case "INSTALLMENT_RESTRUCTURED":
                handleInstallmentRestructured(transactionId, bnplPlanId, eventData, correlationId);
                break;
            default:
                log.debug("BNPL installment event type: {} - transactionId: {}", eventType, transactionId);
        }
        
        bnplAnalyticsService.updateBnplPortfolioMetrics(bnplPlanId, eventType, eventData, correlationId);
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("transactionId", transactionId);
        auditMetadata.put("eventType", eventType);
        auditMetadata.put("bnplPlanId", bnplPlanId);
        auditMetadata.put("status", status);
        auditMetadata.put("installmentNumber", eventData.get("installmentNumber"));
        auditMetadata.put("amount", eventData.get("amount"));
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        
        auditService.logBnplInstallmentEventProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : transactionId,
            transactionId,
            eventType,
            bnplPlanId,
            correlationId,
            auditMetadata
        );
        
        log.info("BNPL installment event processed successfully - transactionId: {}, eventType: {}, correlationId: {}",
                transactionId, eventType, correlationId);
    }
    
    private void handleInstallmentPaid(String transactionId, String bnplPlanId,
                                      Map<String, Object> eventData, String correlationId) {
        log.info("BNPL installment paid on time - transactionId: {}, bnplPlanId: {}, correlationId: {}",
                transactionId, bnplPlanId, correlationId);
        
        bnplAnalyticsService.trackOnTimePayment(transactionId, bnplPlanId, eventData, correlationId);
        bnplAnalyticsService.updateCustomerBnplScore(
            extractString(eventData, "userId"),
            "POSITIVE",
            eventData,
            correlationId
        );
    }
    
    private void handleInstallmentMissed(String transactionId, String bnplPlanId,
                                        Map<String, Object> eventData, String correlationId) {
        log.warn("BNPL installment missed - transactionId: {}, bnplPlanId: {}, correlationId: {}",
                transactionId, bnplPlanId, correlationId);
        
        bnplAnalyticsService.trackMissedPayment(transactionId, bnplPlanId, eventData, correlationId);
        bnplAnalyticsService.updateCustomerBnplScore(
            extractString(eventData, "userId"),
            "NEGATIVE",
            eventData,
            correlationId
        );
        bnplAnalyticsService.assessDefaultRisk(bnplPlanId, eventData, correlationId);
    }
    
    private void handleInstallmentDefaulted(String transactionId, String bnplPlanId,
                                           Map<String, Object> eventData, String correlationId) {
        log.error("BNPL installment defaulted - transactionId: {}, bnplPlanId: {}, correlationId: {}",
                transactionId, bnplPlanId, correlationId);
        
        bnplAnalyticsService.trackDefaultedAccount(transactionId, bnplPlanId, eventData, correlationId);
        bnplAnalyticsService.updateCustomerBnplScore(
            extractString(eventData, "userId"),
            "CRITICAL_NEGATIVE",
            eventData,
            correlationId
        );
        bnplAnalyticsService.updateBnplLossForecasts(eventData, correlationId);
    }
    
    private void handleInstallmentDue(String transactionId, String bnplPlanId,
                                     Map<String, Object> eventData, String correlationId) {
        log.debug("BNPL installment due - transactionId: {}, bnplPlanId: {}, correlationId: {}",
                transactionId, bnplPlanId, correlationId);
        
        bnplAnalyticsService.trackUpcomingPayment(transactionId, bnplPlanId, eventData, correlationId);
    }
    
    private void handleInstallmentRestructured(String transactionId, String bnplPlanId,
                                              Map<String, Object> eventData, String correlationId) {
        log.info("BNPL installment restructured - transactionId: {}, bnplPlanId: {}, correlationId: {}",
                transactionId, bnplPlanId, correlationId);
        
        bnplAnalyticsService.trackPlanRestructuring(transactionId, bnplPlanId, eventData, correlationId);
    }
    
    private void processBnplInstallmentEventFallback(String transactionId, String eventType, String bnplPlanId,
                                                    String status, Map<String, Object> eventData,
                                                    String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process BNPL installment event - transactionId: {}, " +
                "eventType: {}, bnplPlanId: {}, correlationId: {}, error: {}",
                transactionId, eventType, bnplPlanId, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "BNPL_INSTALLMENT_EVENT_PROCESSING",
            transactionId,
            correlationId,
            Map.of(
                "transactionId", transactionId,
                "eventType", eventType,
                "bnplPlanId", bnplPlanId,
                "status", status,
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
            String transactionId = extractString(eventData, "transactionId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("BNPL installment event moved to DLT - eventId: {}, transactionId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, transactionId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("transactionId", transactionId);
            dltMetadata.put("eventType", extractString(eventData, "eventType"));
            dltMetadata.put("bnplPlanId", extractString(eventData, "bnplPlanId"));
            dltMetadata.put("status", extractString(eventData, "status"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "BNPL_INSTALLMENT_EVENT_DLT",
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
}