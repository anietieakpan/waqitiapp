package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.service.PCIComplianceService;
import com.waqiti.compliance.service.ComplianceAlertingService;
import com.waqiti.compliance.service.AuditService;
import com.waqiti.compliance.service.IdempotencyService;
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
 * PCI Compliance Events Consumer
 * 
 * CRITICAL CONSUMER - Processes PCI DSS compliance violation and audit events
 * 
 * EVENT SOURCE:
 * - payment-service PaymentTokenizationEventsConsumer: Line 1029 publishes PCI compliance events
 * 
 * BUSINESS CRITICALITY:
 * - Enforces PCI DSS compliance requirements
 * - Detects and responds to PCI violations
 * - Maintains compliance audit trail
 * - Supports regulatory reporting
 * - Prevents data breach penalties
 * 
 * PCI EVENT TYPES:
 * - PCI_VIOLATION: PCI DSS rule violation detected
 * - TOKENIZATION_NON_COMPLIANT: Token handling violation
 * - CARD_DATA_EXPOSURE: Sensitive card data exposure
 * - ENCRYPTION_FAILURE: Encryption requirement not met
 * - ACCESS_CONTROL_VIOLATION: Unauthorized access attempt
 * - AUDIT_LOG_TAMPERING: Audit log integrity violation
 * 
 * PROCESSING ACTIONS:
 * - Record PCI compliance violations
 * - Generate compliance incident reports
 * - Alert security and compliance teams
 * - Trigger automated remediation workflows
 * - Maintain compliance audit trail
 * - Track compliance metrics and trends
 * 
 * BUSINESS VALUE:
 * - Regulatory compliance: PCI DSS adherence
 * - Risk mitigation: Data breach prevention
 * - Financial protection: Avoid penalties ($5K-$100K/month)
 * - Brand protection: Prevent reputation damage
 * - Audit readiness: Complete compliance trail
 * 
 * FAILURE IMPACT:
 * - Undetected PCI violations
 * - Regulatory penalties and fines
 * - Payment card processing suspension
 * - Data breach liability
 * - Missing compliance audit trail
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Real-time critical alerting
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PCIComplianceEventsConsumer {
    
    private final PCIComplianceService pciComplianceService;
    private final ComplianceAlertingService complianceAlertingService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "pci-compliance-events";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter pciViolationsCounter;
    private Counter criticalViolationsCounter;
    private Counter highSeverityViolationsCounter;
    private Timer processingTimer;
    
    public PCIComplianceEventsConsumer(
            PCIComplianceService pciComplianceService,
            ComplianceAlertingService complianceAlertingService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.pciComplianceService = pciComplianceService;
        this.complianceAlertingService = complianceAlertingService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("pci_compliance_events_processed_total")
                .description("Total number of PCI compliance events processed")
                .tag("consumer", "pci-compliance-events-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("pci_compliance_events_failed_total")
                .description("Total number of PCI compliance events that failed processing")
                .tag("consumer", "pci-compliance-events-consumer")
                .register(meterRegistry);
        
        this.pciViolationsCounter = Counter.builder("pci_violations_total")
                .description("Total number of PCI DSS violations detected")
                .register(meterRegistry);
        
        this.criticalViolationsCounter = Counter.builder("pci_critical_violations_total")
                .description("Total number of critical PCI violations")
                .register(meterRegistry);
        
        this.highSeverityViolationsCounter = Counter.builder("pci_high_severity_violations_total")
                .description("Total number of high severity PCI violations")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("pci_compliance_event_processing_duration")
                .description("Time taken to process PCI compliance events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.pci-compliance-events:pci-compliance-events}",
        groupId = "${kafka.consumer.group-id:compliance-service-pci-group}",
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
    public void handlePCIComplianceEvent(
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
            log.info("Received PCI compliance event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String eventType = extractString(eventData, "eventType");
            String violationType = extractString(eventData, "violationType");
            String severity = extractString(eventData, "severity");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = UUID.randomUUID().toString();
            }
            if (correlationId == null) {
                correlationId = UUID.randomUUID().toString();
            }
            
            if (eventType == null || violationType == null) {
                log.error("Invalid PCI compliance event - missing required fields: eventType={}, violationType={}",
                        eventType, violationType);
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
                log.warn("Duplicate PCI compliance event detected - eventId: {}, violationType: {}, correlationId: {}",
                        eventId, violationType, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processPCIComplianceEvent(eventType, violationType, severity, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed PCI compliance event - eventId: {}, type: {}, " +
                    "violation: {}, severity: {}, correlationId: {}",
                    eventId, eventType, violationType, severity, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process PCI compliance event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process PCI compliance event", e);
        }
    }
    
    @CircuitBreaker(name = "compliance", fallbackMethod = "processPCIComplianceEventFallback")
    @Retry(name = "compliance")
    private void processPCIComplianceEvent(String eventType, String violationType, String severity,
                                          Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing PCI compliance event - type: {}, violation: {}, severity: {}, correlationId: {}",
                eventType, violationType, severity, correlationId);
        
        pciViolationsCounter.increment();
        
        pciComplianceService.recordPCIViolation(
            eventType,
            violationType,
            severity,
            eventData,
            correlationId
        );
        
        if ("CRITICAL".equals(severity)) {
            criticalViolationsCounter.increment();
            handleCriticalViolation(eventType, violationType, eventData, correlationId);
        } else if ("HIGH".equals(severity)) {
            highSeverityViolationsCounter.increment();
            handleHighSeverityViolation(eventType, violationType, eventData, correlationId);
        }
        
        pciComplianceService.createComplianceIncidentReport(
            eventType,
            violationType,
            severity,
            eventData,
            correlationId
        );
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("eventType", eventType);
        auditMetadata.put("violationType", violationType);
        auditMetadata.put("severity", severity);
        auditMetadata.put("description", extractString(eventData, "description"));
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        
        auditService.logPCIComplianceViolation(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : eventType,
            eventType,
            violationType,
            severity,
            correlationId,
            auditMetadata
        );
        
        log.info("PCI compliance event processed successfully - type: {}, violation: {}, correlationId: {}",
                eventType, violationType, correlationId);
    }
    
    private void handleCriticalViolation(String eventType, String violationType,
                                        Map<String, Object> eventData, String correlationId) {
        log.error("CRITICAL PCI VIOLATION DETECTED - type: {}, violation: {}, correlationId: {}",
                eventType, violationType, correlationId);
        
        complianceAlertingService.sendCriticalPCIViolationAlert(
            eventType,
            violationType,
            extractString(eventData, "description"),
            correlationId
        );
        
        pciComplianceService.triggerAutomatedRemediation(
            eventType,
            violationType,
            eventData,
            correlationId
        );
        
        auditService.logCriticalComplianceAlert(
            eventType,
            violationType,
            correlationId,
            eventData
        );
    }
    
    private void handleHighSeverityViolation(String eventType, String violationType,
                                            Map<String, Object> eventData, String correlationId) {
        log.warn("HIGH SEVERITY PCI VIOLATION - type: {}, violation: {}, correlationId: {}",
                eventType, violationType, correlationId);
        
        complianceAlertingService.sendHighSeverityPCIViolationAlert(
            eventType,
            violationType,
            extractString(eventData, "description"),
            correlationId
        );
        
        pciComplianceService.createComplianceReviewTask(
            eventType,
            violationType,
            eventData,
            correlationId
        );
    }
    
    private void processPCIComplianceEventFallback(String eventType, String violationType, String severity,
                                                  Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process PCI compliance event - type: {}, " +
                "violation: {}, severity: {}, correlationId: {}, error: {}",
                eventType, violationType, severity, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "PCI_COMPLIANCE_EVENT_PROCESSING",
            eventType,
            correlationId,
            Map.of(
                "eventType", eventType,
                "violationType", violationType,
                "severity", severity,
                "error", e.getMessage()
            )
        );
        
        complianceAlertingService.sendProcessingFailureAlert(
            "PCI_COMPLIANCE_EVENT_PROCESSING_FAILED",
            eventType,
            violationType,
            e.getMessage(),
            correlationId
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
            String eventType = extractString(eventData, "eventType");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("PCI compliance event moved to DLT - eventId: {}, eventType: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, eventType, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("eventType", eventType);
            dltMetadata.put("violationType", extractString(eventData, "violationType"));
            dltMetadata.put("severity", extractString(eventData, "severity"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("timestamp"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "PCI_COMPLIANCE_EVENT_DLT",
                exceptionMessage,
                correlationId,
                dltMetadata
            );
            
            complianceAlertingService.sendDLTAlert(
                "PCI_COMPLIANCE_EVENT",
                eventType,
                exceptionMessage,
                correlationId
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