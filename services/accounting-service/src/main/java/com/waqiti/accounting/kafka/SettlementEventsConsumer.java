package com.waqiti.accounting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.accounting.service.SettlementAccountingService;
import com.waqiti.accounting.service.GeneralLedgerService;
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
 * Settlement Events Consumer
 * 
 * CRITICAL CONSUMER - Processes batch settlement events for accounting records
 * 
 * EVENT SOURCE:
 * - batch-service BatchService: Line 792 publishes settlement batch events
 * 
 * BUSINESS CRITICALITY:
 * - Records settlement transactions in general ledger
 * - Maintains accurate financial accounting
 * - Supports financial reporting and audits
 * - Tracks settlement batch completion
 * - Enables settlement reconciliation
 * 
 * SETTLEMENT EVENT TYPES:
 * - SETTLEMENT_BATCH_INITIATED: Settlement batch started
 * - SETTLEMENT_BATCH_PROCESSING: Settlement in progress
 * - SETTLEMENT_BATCH_COMPLETED: Batch successfully settled
 * - SETTLEMENT_BATCH_FAILED: Settlement batch failed
 * - SETTLEMENT_ITEM_SETTLED: Individual settlement item processed
 * - SETTLEMENT_RECONCILED: Settlement reconciliation complete
 * 
 * PROCESSING ACTIONS:
 * - Create general ledger entries for settlements
 * - Update settlement accounting balances
 * - Track settlement batch totals
 * - Record settlement fees and commissions
 * - Generate settlement accounting reports
 * - Maintain settlement audit trail
 * 
 * BUSINESS VALUE:
 * - Financial accuracy: Precise settlement accounting
 * - Audit compliance: Complete settlement trail
 * - Reporting: Settlement financial statements
 * - Reconciliation: Settlement matching capability
 * - Revenue tracking: Settlement fee accounting
 * 
 * FAILURE IMPACT:
 * - Missing settlement accounting records
 * - Inaccurate financial statements
 * - Failed financial reconciliation
 * - Audit compliance violations
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
public class SettlementEventsConsumer {
    
    private final SettlementAccountingService settlementAccountingService;
    private final GeneralLedgerService generalLedgerService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "settlement-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter settlementsCompletedCounter;
    private Counter settlementsBatchTotalCounter;
    private Timer processingTimer;
    
    public SettlementEventsConsumer(
            SettlementAccountingService settlementAccountingService,
            GeneralLedgerService generalLedgerService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.settlementAccountingService = settlementAccountingService;
        this.generalLedgerService = generalLedgerService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("settlement_events_processed_total")
                .description("Total number of settlement events processed")
                .tag("consumer", "settlement-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("settlement_events_failed_total")
                .description("Total number of settlement events that failed processing")
                .tag("consumer", "settlement-events-consumer")
                .register(meterRegistry);
        
        this.settlementsCompletedCounter = Counter.builder("settlements_completed_total")
                .description("Total number of settlements completed")
                .register(meterRegistry);
        
        this.settlementsBatchTotalCounter = Counter.builder("settlement_batch_amount_total")
                .description("Total settlement batch amounts processed")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("settlement_event_processing_duration")
                .description("Time taken to process settlement events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.settlement-events:settlement-events}",
        groupId = "${kafka.consumer.group-id:accounting-service-settlement-group}",
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
    public void handleSettlementEvent(
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
            log.info("Received settlement event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String batchId = extractString(eventData, "batchId");
            String id = extractString(eventData, "id");
            String eventType = extractString(eventData, "eventType");
            String status = extractString(eventData, "status");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = batchId != null ? batchId : (id != null ? id : UUID.randomUUID().toString());
            }
            if (correlationId == null) {
                correlationId = batchId != null ? batchId : id;
            }
            
            String settlementId = batchId != null ? batchId : id;
            
            if (settlementId == null) {
                log.error("Invalid settlement event - missing batchId and id");
                auditService.logEventProcessingFailure(
                    eventId,
                    TOPIC_NAME,
                    "VALIDATION_FAILED",
                    "Missing settlement identifier",
                    correlationId,
                    Map.of("event", eventData)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate settlement event detected - eventId: {}, settlementId: {}, correlationId: {}",
                        eventId, settlementId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processSettlementEvent(settlementId, eventType, status, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed settlement event - eventId: {}, settlementId: {}, " +
                    "eventType: {}, status: {}, correlationId: {}",
                    eventId, settlementId, eventType, status, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process settlement event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process settlement event", e);
        }
    }
    
    @CircuitBreaker(name = "accounting", fallbackMethod = "processSettlementEventFallback")
    @Retry(name = "accounting")
    private void processSettlementEvent(String settlementId, String eventType, String status,
                                       Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing settlement event - settlementId: {}, eventType: {}, status: {}, correlationId: {}",
                settlementId, eventType, status, correlationId);
        
        settlementAccountingService.recordSettlementEvent(
            settlementId,
            eventType,
            status,
            eventData,
            correlationId
        );
        
        String actualEventType = eventType != null ? eventType : determineEventType(status);
        
        switch (actualEventType) {
            case "SETTLEMENT_BATCH_COMPLETED":
            case "COMPLETED":
                settlementsCompletedCounter.increment();
                handleSettlementCompleted(settlementId, eventData, correlationId);
                break;
            case "SETTLEMENT_BATCH_INITIATED":
            case "INITIATED":
                handleSettlementInitiated(settlementId, eventData, correlationId);
                break;
            case "SETTLEMENT_BATCH_PROCESSING":
            case "PROCESSING":
                handleSettlementProcessing(settlementId, eventData, correlationId);
                break;
            case "SETTLEMENT_BATCH_FAILED":
            case "FAILED":
                handleSettlementFailed(settlementId, eventData, correlationId);
                break;
            case "SETTLEMENT_ITEM_SETTLED":
                handleSettlementItemSettled(settlementId, eventData, correlationId);
                break;
            case "SETTLEMENT_RECONCILED":
                handleSettlementReconciled(settlementId, eventData, correlationId);
                break;
            default:
                log.debug("Settlement event type: {} - settlementId: {}", actualEventType, settlementId);
                handleSettlementCompleted(settlementId, eventData, correlationId);
        }
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("settlementId", settlementId);
        auditMetadata.put("eventType", actualEventType);
        auditMetadata.put("status", status);
        auditMetadata.put("totalAmount", eventData.get("totalAmount"));
        auditMetadata.put("currency", eventData.get("currency"));
        auditMetadata.put("itemCount", eventData.get("itemCount"));
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        
        auditService.logSettlementEventProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : settlementId,
            settlementId,
            actualEventType,
            status,
            correlationId,
            auditMetadata
        );
        
        log.info("Settlement event processed successfully - settlementId: {}, eventType: {}, correlationId: {}",
                settlementId, actualEventType, correlationId);
    }
    
    private void handleSettlementCompleted(String settlementId, Map<String, Object> eventData, String correlationId) {
        log.info("Settlement completed - settlementId: {}, correlationId: {}", settlementId, correlationId);
        
        BigDecimal totalAmount = extractBigDecimal(eventData, "totalAmount");
        String currency = extractString(eventData, "currency");
        
        if (totalAmount != null) {
            settlementsBatchTotalCounter.increment(totalAmount.doubleValue());
        }
        
        generalLedgerService.createSettlementLedgerEntry(
            settlementId,
            totalAmount,
            currency,
            eventData,
            correlationId
        );
        
        settlementAccountingService.updateSettlementBalances(
            settlementId,
            totalAmount,
            currency,
            eventData,
            correlationId
        );
        
        settlementAccountingService.recordSettlementFees(settlementId, eventData, correlationId);
    }
    
    private void handleSettlementInitiated(String settlementId, Map<String, Object> eventData, String correlationId) {
        log.info("Settlement initiated - settlementId: {}, correlationId: {}", settlementId, correlationId);
        
        settlementAccountingService.createSettlementRecord(settlementId, eventData, correlationId);
    }
    
    private void handleSettlementProcessing(String settlementId, Map<String, Object> eventData, String correlationId) {
        log.debug("Settlement processing - settlementId: {}, correlationId: {}", settlementId, correlationId);
        
        settlementAccountingService.updateSettlementStatus(settlementId, "PROCESSING", eventData, correlationId);
    }
    
    private void handleSettlementFailed(String settlementId, Map<String, Object> eventData, String correlationId) {
        log.error("Settlement failed - settlementId: {}, correlationId: {}", settlementId, correlationId);
        
        settlementAccountingService.handleSettlementFailure(settlementId, eventData, correlationId);
        
        generalLedgerService.reverseSettlementLedgerEntry(settlementId, eventData, correlationId);
    }
    
    private void handleSettlementItemSettled(String settlementId, Map<String, Object> eventData, String correlationId) {
        log.debug("Settlement item settled - settlementId: {}, correlationId: {}", settlementId, correlationId);
        
        String itemId = extractString(eventData, "itemId");
        BigDecimal itemAmount = extractBigDecimal(eventData, "amount");
        
        settlementAccountingService.recordSettlementItem(
            settlementId,
            itemId,
            itemAmount,
            eventData,
            correlationId
        );
    }
    
    private void handleSettlementReconciled(String settlementId, Map<String, Object> eventData, String correlationId) {
        log.info("Settlement reconciled - settlementId: {}, correlationId: {}", settlementId, correlationId);
        
        settlementAccountingService.markSettlementAsReconciled(settlementId, eventData, correlationId);
    }
    
    private String determineEventType(String status) {
        if (status == null) return "SETTLEMENT_BATCH_COMPLETED";
        
        return switch (status.toUpperCase()) {
            case "COMPLETED" -> "SETTLEMENT_BATCH_COMPLETED";
            case "INITIATED" -> "SETTLEMENT_BATCH_INITIATED";
            case "PROCESSING" -> "SETTLEMENT_BATCH_PROCESSING";
            case "FAILED" -> "SETTLEMENT_BATCH_FAILED";
            default -> "SETTLEMENT_BATCH_COMPLETED";
        };
    }
    
    private void processSettlementEventFallback(String settlementId, String eventType, String status,
                                               Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process settlement event - settlementId: {}, " +
                "eventType: {}, status: {}, correlationId: {}, error: {}",
                settlementId, eventType, status, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "SETTLEMENT_EVENT_PROCESSING",
            settlementId,
            correlationId,
            Map.of(
                "settlementId", settlementId,
                "eventType", eventType != null ? eventType : "UNKNOWN",
                "status", status != null ? status : "UNKNOWN",
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
            String settlementId = extractString(eventData, "batchId");
            if (settlementId == null) settlementId = extractString(eventData, "id");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Settlement event moved to DLT - eventId: {}, settlementId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, settlementId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("settlementId", settlementId);
            dltMetadata.put("eventType", extractString(eventData, "eventType"));
            dltMetadata.put("status", extractString(eventData, "status"));
            dltMetadata.put("totalAmount", eventData.get("totalAmount"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "SETTLEMENT_EVENT_DLT",
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