package com.waqiti.accounting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.accounting.service.MerchantSettlementService;
import com.waqiti.accounting.service.MerchantAccountingService;
import com.waqiti.accounting.service.AuditService;
import com.waqiti.accounting.service.IdempotencyService;
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
 * Merchant Settlement Events Consumer
 * 
 * CRITICAL CONSUMER - Processes merchant settlement initiation events
 * 
 * EVENT SOURCE:
 * - payment-service RecurringPaymentDueEventConsumer: Line 393 publishes merchant settlement events
 * 
 * BUSINESS CRITICALITY:
 * - Manages merchant payment settlements
 * - Tracks merchant receivables
 * - Supports merchant payout processing
 * - Enables merchant financial reporting
 * - Critical for merchant cash flow
 * 
 * MERCHANT SETTLEMENT TYPES:
 * - MERCHANT_SETTLEMENT_INITIATED: Settlement process started
 * - MERCHANT_SETTLEMENT_SCHEDULED: Settlement scheduled for payout
 * - MERCHANT_SETTLEMENT_PROCESSING: Payout in progress
 * - MERCHANT_SETTLEMENT_COMPLETED: Funds transferred to merchant
 * - MERCHANT_SETTLEMENT_FAILED: Settlement failed
 * - MERCHANT_SETTLEMENT_REVERSED: Settlement reversed/refunded
 * 
 * PROCESSING ACTIONS:
 * - Record merchant settlement entries
 * - Track merchant receivables balances
 * - Calculate merchant settlement fees
 * - Update merchant payout schedules
 * - Generate merchant settlement reports
 * - Maintain merchant settlement audit trail
 * 
 * BUSINESS VALUE:
 * - Merchant satisfaction: Timely settlement processing
 * - Financial accuracy: Precise merchant accounting
 * - Cash flow management: Predictable merchant payouts
 * - Reporting: Merchant financial statements
 * - Compliance: Complete settlement audit trail
 * 
 * FAILURE IMPACT:
 * - Delayed merchant settlements
 * - Inaccurate merchant balances
 * - Missing merchant payout records
 * - Merchant dissatisfaction
 * - Revenue recognition errors
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Transactional accounting updates
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MerchantSettlementEventsConsumer {
    
    private final MerchantSettlementService merchantSettlementService;
    private final MerchantAccountingService merchantAccountingService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "merchant.settlement.initiated";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter settlementsInitiatedCounter;
    private Counter settlementAmountCounter;
    private Timer processingTimer;
    
    public MerchantSettlementEventsConsumer(
            MerchantSettlementService merchantSettlementService,
            MerchantAccountingService merchantAccountingService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.merchantSettlementService = merchantSettlementService;
        this.merchantAccountingService = merchantAccountingService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("merchant_settlement_events_processed_total")
                .description("Total number of merchant settlement events processed")
                .tag("consumer", "merchant-settlement-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("merchant_settlement_events_failed_total")
                .description("Total number of merchant settlement events that failed processing")
                .tag("consumer", "merchant-settlement-events-consumer")
                .register(meterRegistry);
        
        this.settlementsInitiatedCounter = Counter.builder("merchant_settlements_initiated_total")
                .description("Total number of merchant settlements initiated")
                .register(meterRegistry);
        
        this.settlementAmountCounter = Counter.builder("merchant_settlement_amount_total")
                .description("Total merchant settlement amounts")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("merchant_settlement_event_processing_duration")
                .description("Time taken to process merchant settlement events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.merchant-settlement-initiated:merchant.settlement.initiated}",
        groupId = "${kafka.consumer.group-id:accounting-service-merchant-settlement-group}",
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
    public void handleMerchantSettlementEvent(
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
            log.info("Received merchant settlement event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String paymentId = extractString(eventData, "paymentId");
            String merchantId = extractString(eventData, "merchantId");
            String eventType = extractString(eventData, "eventType");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = paymentId != null ? paymentId : UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = merchantId;
            }
            
            if (paymentId == null || merchantId == null) {
                log.error("Invalid merchant settlement event - missing required fields: paymentId={}, merchantId={}",
                        paymentId, merchantId);
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
                log.warn("Duplicate merchant settlement event detected - eventId: {}, paymentId: {}, merchantId: {}, correlationId: {}",
                        eventId, paymentId, merchantId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processMerchantSettlementEvent(paymentId, merchantId, eventType, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed merchant settlement event - eventId: {}, paymentId: {}, " +
                    "merchantId: {}, eventType: {}, correlationId: {}",
                    eventId, paymentId, merchantId, eventType, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process merchant settlement event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process merchant settlement event", e);
        }
    }
    
    @CircuitBreaker(name = "accounting", fallbackMethod = "processMerchantSettlementEventFallback")
    @Retry(name = "accounting")
    private void processMerchantSettlementEvent(String paymentId, String merchantId, String eventType,
                                               Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing merchant settlement event - paymentId: {}, merchantId: {}, eventType: {}, correlationId: {}",
                paymentId, merchantId, eventType, correlationId);
        
        settlementsInitiatedCounter.increment();
        
        BigDecimal amount = extractBigDecimal(eventData, "amount");
        String currency = extractString(eventData, "currency");
        
        if (amount != null) {
            settlementAmountCounter.increment(amount.doubleValue());
        }
        
        merchantSettlementService.initiateSettlement(
            paymentId,
            merchantId,
            amount,
            currency,
            eventData,
            correlationId
        );
        
        merchantAccountingService.recordMerchantReceivable(
            merchantId,
            paymentId,
            amount,
            currency,
            eventData,
            correlationId
        );
        
        String settlementDate = extractString(eventData, "settlementDate");
        merchantSettlementService.scheduleSettlementPayout(
            merchantId,
            paymentId,
            amount,
            currency,
            settlementDate,
            correlationId
        );
        
        BigDecimal settlementFee = merchantSettlementService.calculateSettlementFee(
            merchantId,
            amount,
            eventData,
            correlationId
        );
        
        merchantAccountingService.recordSettlementFee(
            merchantId,
            paymentId,
            settlementFee,
            currency,
            correlationId
        );
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("paymentId", paymentId);
        auditMetadata.put("merchantId", merchantId);
        auditMetadata.put("eventType", eventType != null ? eventType : "MERCHANT_SETTLEMENT_INITIATED");
        auditMetadata.put("amount", amount);
        auditMetadata.put("currency", currency);
        auditMetadata.put("settlementFee", settlementFee);
        auditMetadata.put("settlementDate", settlementDate);
        auditMetadata.put("subscriptionId", eventData.get("subscriptionId"));
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        
        auditService.logMerchantSettlementEventProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : paymentId,
            paymentId,
            merchantId,
            eventType != null ? eventType : "MERCHANT_SETTLEMENT_INITIATED",
            correlationId,
            auditMetadata
        );
        
        log.info("Merchant settlement event processed successfully - paymentId: {}, merchantId: {}, " +
                "amount: {} {}, correlationId: {}",
                paymentId, merchantId, amount, currency, correlationId);
    }
    
    private void processMerchantSettlementEventFallback(String paymentId, String merchantId, String eventType,
                                                       Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process merchant settlement event - paymentId: {}, " +
                "merchantId: {}, eventType: {}, correlationId: {}, error: {}",
                paymentId, merchantId, eventType, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "MERCHANT_SETTLEMENT_EVENT_PROCESSING",
            paymentId,
            correlationId,
            Map.of(
                "paymentId", paymentId,
                "merchantId", merchantId,
                "eventType", eventType != null ? eventType : "UNKNOWN",
                "amount", eventData.get("amount"),
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
            String merchantId = extractString(eventData, "merchantId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Merchant settlement event moved to DLT - eventId: {}, paymentId: {}, merchantId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, paymentId, merchantId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("paymentId", paymentId);
            dltMetadata.put("merchantId", merchantId);
            dltMetadata.put("eventType", extractString(eventData, "eventType"));
            dltMetadata.put("amount", eventData.get("amount"));
            dltMetadata.put("currency", extractString(eventData, "currency"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "MERCHANT_SETTLEMENT_EVENT_DLT",
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
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}