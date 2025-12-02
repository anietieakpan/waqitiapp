package com.waqiti.ledger.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.ledger.service.LedgerReconciliationService;
import com.waqiti.ledger.service.AuditService;
import com.waqiti.ledger.service.IdempotencyService;
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
 * Reconciliation Events Consumer
 * 
 * CRITICAL CONSUMER - Processes financial reconciliation lifecycle events
 * 
 * EVENT SOURCE:
 * - reconciliation-service ReconciliationService: Line 518 publishes reconciliation events
 * - payment-service PaymentService: Line 1726 publishes reconciliation events
 * 
 * BUSINESS CRITICALITY:
 * - Ensures financial accuracy through reconciliation
 * - Detects and resolves transaction discrepancies
 * - Maintains compliance with financial regulations
 * - Supports audit trail for financial reporting
 * - Enables real-time reconciliation monitoring
 * 
 * RECONCILIATION EVENT TYPES:
 * - RECONCILIATION_STARTED: Reconciliation process initiated
 * - RECONCILIATION_IN_PROGRESS: Reconciliation executing
 * - RECONCILIATION_COMPLETED: Successfully reconciled
 * - DISCREPANCY_FOUND: Mismatch detected
 * - DISCREPANCY_RESOLVED: Mismatch fixed
 * - RECONCILIATION_FAILED: Process failed
 * 
 * PROCESSING ACTIONS:
 * - Record reconciliation status in ledger
 * - Track reconciliation completion metrics
 * - Alert on discrepancies and failures
 * - Update ledger entries with reconciliation status
 * - Maintain audit trail for compliance
 * - Measure reconciliation SLA performance
 * 
 * BUSINESS VALUE:
 * - Financial integrity: Accurate transaction reconciliation
 * - Compliance: Complete audit trail
 * - Risk management: Early discrepancy detection
 * - Operations: Automated reconciliation monitoring
 * - SLA tracking: Reconciliation performance metrics
 * 
 * FAILURE IMPACT:
 * - Loss of reconciliation visibility
 * - Delayed detection of financial discrepancies
 * - Compliance violations
 * - Missing audit trail
 * - Reduced financial accuracy
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time alerting
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReconciliationEventsConsumer {
    
    private final LedgerReconciliationService ledgerReconciliationService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "reconciliation-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter reconciliationsCompletedCounter;
    private Counter discrepanciesFoundCounter;
    private Counter discrepanciesResolvedCounter;
    private Timer processingTimer;
    
    public ReconciliationEventsConsumer(
            LedgerReconciliationService ledgerReconciliationService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.ledgerReconciliationService = ledgerReconciliationService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("reconciliation_events_processed_total")
                .description("Total number of reconciliation events processed")
                .tag("consumer", "reconciliation-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("reconciliation_events_failed_total")
                .description("Total number of reconciliation events that failed processing")
                .tag("consumer", "reconciliation-events-consumer")
                .register(meterRegistry);
        
        this.reconciliationsCompletedCounter = Counter.builder("reconciliations_completed_total")
                .description("Total number of reconciliations completed successfully")
                .register(meterRegistry);
        
        this.discrepanciesFoundCounter = Counter.builder("reconciliation_discrepancies_found_total")
                .description("Total number of discrepancies found during reconciliation")
                .register(meterRegistry);
        
        this.discrepanciesResolvedCounter = Counter.builder("reconciliation_discrepancies_resolved_total")
                .description("Total number of discrepancies resolved")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("reconciliation_event_processing_duration")
                .description("Time taken to process reconciliation events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.reconciliation-events:reconciliation-events}",
        groupId = "${kafka.consumer.group-id:ledger-service-reconciliation-group}",
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
    public void handleReconciliationEvent(
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
            log.info("Received reconciliation event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String reconciliationId = extractString(eventData, "reconciliationId");
            String eventType = extractString(eventData, "eventType");
            String status = extractString(eventData, "status");
            String type = extractString(eventData, "type");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = reconciliationId != null ? reconciliationId : UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = reconciliationId;
            }
            
            if (reconciliationId == null || eventType == null) {
                log.error("Invalid reconciliation event - missing required fields: reconciliationId={}, eventType={}",
                        reconciliationId, eventType);
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
                log.warn("Duplicate reconciliation event detected - eventId: {}, reconciliationId: {}, correlationId: {}",
                        eventId, reconciliationId, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processReconciliationEvent(reconciliationId, eventType, status, type, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed reconciliation event - eventId: {}, reconciliationId: {}, " +
                    "eventType: {}, status: {}, correlationId: {}",
                    eventId, reconciliationId, eventType, status, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process reconciliation event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process reconciliation event", e);
        }
    }
    
    @CircuitBreaker(name = "ledger", fallbackMethod = "processReconciliationEventFallback")
    @Retry(name = "ledger")
    private void processReconciliationEvent(String reconciliationId, String eventType, String status,
                                           String type, Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing reconciliation event - reconciliationId: {}, eventType: {}, status: {}, " +
                "type: {}, correlationId: {}",
                reconciliationId, eventType, status, type, correlationId);
        
        ledgerReconciliationService.recordReconciliationEvent(
            reconciliationId,
            eventType,
            status,
            type,
            eventData,
            correlationId
        );
        
        switch (eventType) {
            case "RECONCILIATION_COMPLETED":
                reconciliationsCompletedCounter.increment();
                handleReconciliationCompleted(reconciliationId, eventData, correlationId);
                break;
            case "DISCREPANCY_FOUND":
                discrepanciesFoundCounter.increment();
                handleDiscrepancyFound(reconciliationId, eventData, correlationId);
                break;
            case "DISCREPANCY_RESOLVED":
                discrepanciesResolvedCounter.increment();
                handleDiscrepancyResolved(reconciliationId, eventData, correlationId);
                break;
            case "RECONCILIATION_FAILED":
                handleReconciliationFailed(reconciliationId, eventData, correlationId);
                break;
            default:
                log.debug("Reconciliation event type: {} - reconciliationId: {}", eventType, reconciliationId);
        }
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("reconciliationId", reconciliationId);
        auditMetadata.put("eventType", eventType);
        auditMetadata.put("status", status);
        auditMetadata.put("type", type);
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        
        auditService.logReconciliationEventProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : reconciliationId,
            reconciliationId,
            eventType,
            status,
            correlationId,
            auditMetadata
        );
        
        log.info("Reconciliation event processed successfully - reconciliationId: {}, eventType: {}, correlationId: {}",
                reconciliationId, eventType, correlationId);
    }
    
    private void handleReconciliationCompleted(String reconciliationId, Map<String, Object> eventData, String correlationId) {
        log.info("Reconciliation completed - reconciliationId: {}, correlationId: {}", reconciliationId, correlationId);
        ledgerReconciliationService.updateLedgerEntriesAsReconciled(reconciliationId, eventData, correlationId);
    }
    
    private void handleDiscrepancyFound(String reconciliationId, Map<String, Object> eventData, String correlationId) {
        log.warn("Discrepancy found during reconciliation - reconciliationId: {}, correlationId: {}",
                reconciliationId, correlationId);
        
        ledgerReconciliationService.recordDiscrepancy(reconciliationId, eventData, correlationId);
        ledgerReconciliationService.createDiscrepancyResolutionTask(reconciliationId, eventData, correlationId);
    }
    
    private void handleDiscrepancyResolved(String reconciliationId, Map<String, Object> eventData, String correlationId) {
        log.info("Discrepancy resolved - reconciliationId: {}, correlationId: {}", reconciliationId, correlationId);
        ledgerReconciliationService.markDiscrepancyAsResolved(reconciliationId, eventData, correlationId);
    }
    
    private void handleReconciliationFailed(String reconciliationId, Map<String, Object> eventData, String correlationId) {
        log.error("Reconciliation failed - reconciliationId: {}, correlationId: {}", reconciliationId, correlationId);
        ledgerReconciliationService.handleReconciliationFailure(reconciliationId, eventData, correlationId);
    }
    
    private void processReconciliationEventFallback(String reconciliationId, String eventType, String status,
                                                   String type, Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process reconciliation event - reconciliationId: {}, " +
                "eventType: {}, correlationId: {}, error: {}",
                reconciliationId, eventType, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "RECONCILIATION_EVENT_PROCESSING",
            reconciliationId,
            correlationId,
            Map.of(
                "reconciliationId", reconciliationId,
                "eventType", eventType,
                "status", status,
                "type", type,
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
            String reconciliationId = extractString(eventData, "reconciliationId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("Reconciliation event moved to DLT - eventId: {}, reconciliationId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, reconciliationId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("reconciliationId", reconciliationId);
            dltMetadata.put("eventType", extractString(eventData, "eventType"));
            dltMetadata.put("status", extractString(eventData, "status"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "RECONCILIATION_EVENT_DLT",
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