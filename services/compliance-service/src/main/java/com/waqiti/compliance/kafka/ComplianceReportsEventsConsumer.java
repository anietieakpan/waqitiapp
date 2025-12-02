package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.service.ComplianceReportingService;
import com.waqiti.compliance.service.RegulatoryReportingService;
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
 * Compliance Reports Events Consumer
 * 
 * CRITICAL CONSUMER - Processes compliance report generation requests
 * 
 * EVENT SOURCES:
 * - payment-service WiseWebhookService: Lines 1278, 1344 publish AML compliance reports
 * - user-service ProductionComplianceSecurityConfiguration: Line 287 publishes compliance reports
 * 
 * BUSINESS CRITICALITY:
 * - Regulatory compliance reporting (AML, KYC, PCI DSS)
 * - Audit trail generation for regulators
 * - Suspicious Activity Report (SAR) filing
 * - Currency Transaction Report (CTR) generation
 * - Compliance case documentation
 * 
 * REPORT TYPES:
 * - AML_ALERT: Anti-Money Laundering alert reports
 * - SAR: Suspicious Activity Reports for FinCEN
 * - CTR: Currency Transaction Reports (>$10K)
 * - KYC_COMPLIANCE: KYC verification reports
 * - PCI_COMPLIANCE: PCI DSS compliance reports
 * - AUDIT_TRAIL: Complete audit trail reports
 * - REGULATORY_SUBMISSION: Reports for regulatory submission
 * 
 * PROCESSING ACTIONS:
 * - Generate compliance report documents
 * - Store reports in secure compliance archive
 * - Prepare regulatory filing submissions
 * - Generate audit trail documentation
 * - Track report generation metrics
 * - Maintain compliance reporting history
 * 
 * BUSINESS VALUE:
 * - Regulatory compliance: Avoid penalties ($100K-$10M+ fines)
 * - Audit readiness: Complete documentation
 * - Risk mitigation: Proactive compliance
 * - Legal protection: Audit trail evidence
 * - Operational efficiency: Automated reporting
 * 
 * FAILURE IMPACT:
 * - Regulatory compliance violations
 * - Missing required regulatory filings
 * - Incomplete audit trails
 * - Potential regulatory fines
 * - Legal liability exposure
 * 
 * RESILIENCE:
 * - Idempotency with 24-hour TTL
 * - Circuit breakers on all external calls
 * - Dead Letter Queue for failed events
 * - Comprehensive audit logging
 * - Secure report storage
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-28
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ComplianceReportsEventsConsumer {
    
    private final ComplianceReportingService complianceReportingService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private static final String TOPIC_NAME = "compliance-reports";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    private Counter eventsProcessedCounter;
    private Counter eventsFailedCounter;
    private Counter amlReportsCounter;
    private Counter sarReportsCounter;
    private Counter ctrReportsCounter;
    private Timer processingTimer;
    
    public ComplianceReportsEventsConsumer(
            ComplianceReportingService complianceReportingService,
            RegulatoryReportingService regulatoryReportingService,
            IdempotencyService idempotencyService,
            AuditService auditService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.complianceReportingService = complianceReportingService;
        this.regulatoryReportingService = regulatoryReportingService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        this.eventsProcessedCounter = Counter.builder("compliance_reports_processed_total")
                .description("Total number of compliance report events processed")
                .tag("consumer", "compliance-reports-consumer")
                .register(meterRegistry);
        
        this.eventsFailedCounter = Counter.builder("compliance_reports_failed_total")
                .description("Total number of compliance report events that failed processing")
                .tag("consumer", "compliance-reports-consumer")
                .register(meterRegistry);
        
        this.amlReportsCounter = Counter.builder("compliance_aml_reports_total")
                .description("Total number of AML compliance reports generated")
                .register(meterRegistry);
        
        this.sarReportsCounter = Counter.builder("compliance_sar_reports_total")
                .description("Total number of SARs filed")
                .register(meterRegistry);
        
        this.ctrReportsCounter = Counter.builder("compliance_ctr_reports_total")
                .description("Total number of CTRs filed")
                .register(meterRegistry);
        
        this.processingTimer = Timer.builder("compliance_report_processing_duration")
                .description("Time taken to process compliance report requests")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "${kafka.topics.compliance-reports:compliance-reports}",
        groupId = "${kafka.consumer.group-id:compliance-service-reports-group}",
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
    public void handleComplianceReportEvent(
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
            log.info("Received compliance report event - topic: {}, partition: {}, offset: {}",
                    topic, partition, offset);
            
            Map<String, Object> eventData = objectMapper.readValue(eventPayload, Map.class);
            
            eventId = extractString(eventData, "eventId");
            String caseId = extractString(eventData, "caseId");
            String reportType = extractString(eventData, "reportType");
            String reportId = extractString(eventData, "reportId");
            correlationId = extractString(eventData, "correlationId");
            
            if (eventId == null) {
                eventId = caseId != null ? caseId : (reportId != null ? reportId : UUID.randomUUID().toString());
            }
            if (correlationId == null) {
                correlationId = caseId != null ? caseId : reportId;
            }
            
            if (reportType == null) {
                log.error("Invalid compliance report event - missing reportType");
                auditService.logEventProcessingFailure(
                    eventId,
                    TOPIC_NAME,
                    "VALIDATION_FAILED",
                    "Missing reportType",
                    correlationId,
                    Map.of("event", eventData)
                );
                acknowledgment.acknowledge();
                return;
            }
            
            if (idempotencyService.isDuplicate(eventId, IDEMPOTENCY_TTL)) {
                log.warn("Duplicate compliance report event detected - eventId: {}, reportType: {}, correlationId: {}",
                        eventId, reportType, correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            processComplianceReportEvent(reportType, caseId, reportId, eventData, correlationId);
            
            idempotencyService.recordProcessedEvent(eventId, IDEMPOTENCY_TTL);
            
            sample.stop(processingTimer);
            eventsProcessedCounter.increment();
            
            log.info("Successfully processed compliance report event - eventId: {}, reportType: {}, " +
                    "caseId: {}, correlationId: {}",
                    eventId, reportType, caseId, correlationId);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            sample.stop(processingTimer);
            eventsFailedCounter.increment();
            
            log.error("Failed to process compliance report event - eventId: {}, correlationId: {}, error: {}",
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
            
            throw new RuntimeException("Failed to process compliance report event", e);
        }
    }
    
    @CircuitBreaker(name = "compliance", fallbackMethod = "processComplianceReportEventFallback")
    @Retry(name = "compliance")
    private void processComplianceReportEvent(String reportType, String caseId, String reportId,
                                             Map<String, Object> eventData, String correlationId) {
        
        log.info("Processing compliance report event - reportType: {}, caseId: {}, reportId: {}, correlationId: {}",
                reportType, caseId, reportId, correlationId);
        
        switch (reportType) {
            case "AML_ALERT":
                amlReportsCounter.increment();
                handleAMLReport(caseId, eventData, correlationId);
                break;
            case "SAR":
                sarReportsCounter.increment();
                handleSARReport(caseId, eventData, correlationId);
                break;
            case "CTR":
                ctrReportsCounter.increment();
                handleCTRReport(caseId, eventData, correlationId);
                break;
            case "KYC_COMPLIANCE":
                handleKYCComplianceReport(caseId, reportId, eventData, correlationId);
                break;
            case "PCI_COMPLIANCE":
                handlePCIComplianceReport(caseId, reportId, eventData, correlationId);
                break;
            case "AUDIT_TRAIL":
                handleAuditTrailReport(caseId, reportId, eventData, correlationId);
                break;
            case "REGULATORY_SUBMISSION":
                handleRegulatorySubmission(reportId, eventData, correlationId);
                break;
            default:
                log.debug("Compliance report type: {} - caseId: {}", reportType, caseId);
                handleGenericReport(reportType, caseId, reportId, eventData, correlationId);
        }
        
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("reportType", reportType);
        auditMetadata.put("caseId", caseId);
        auditMetadata.put("reportId", reportId);
        auditMetadata.put("requestedAt", eventData.get("requestedAt"));
        auditMetadata.put("timestamp", eventData.get("timestamp"));
        
        auditService.logComplianceReportProcessed(
            eventData.get("eventId") != null ? eventData.get("eventId").toString() : (caseId != null ? caseId : reportId),
            reportType,
            caseId,
            reportId,
            correlationId,
            auditMetadata
        );
        
        log.info("Compliance report processed successfully - reportType: {}, caseId: {}, correlationId: {}",
                reportType, caseId, correlationId);
    }
    
    private void handleAMLReport(String caseId, Map<String, Object> eventData, String correlationId) {
        log.info("Generating AML compliance report - caseId: {}, correlationId: {}", caseId, correlationId);
        
        String reportPath = complianceReportingService.generateAMLReport(caseId, eventData, correlationId);
        
        complianceReportingService.storeComplianceReport(
            caseId,
            "AML_ALERT",
            reportPath,
            eventData,
            correlationId
        );
        
        auditService.logComplianceReportGenerated(
            caseId,
            "AML_ALERT",
            reportPath,
            correlationId,
            eventData
        );
    }
    
    private void handleSARReport(String caseId, Map<String, Object> eventData, String correlationId) {
        log.warn("CRITICAL: Filing SAR report - caseId: {}, correlationId: {}", caseId, correlationId);
        
        String sarReportPath = regulatoryReportingService.generateSAR(caseId, eventData, correlationId);
        
        regulatoryReportingService.prepareFinCENSubmission(
            caseId,
            "SAR",
            sarReportPath,
            correlationId
        );
        
        auditService.logCriticalComplianceEvent(
            "SAR_FILED",
            caseId,
            correlationId,
            eventData
        );
    }
    
    private void handleCTRReport(String caseId, Map<String, Object> eventData, String correlationId) {
        log.info("Filing CTR report - caseId: {}, correlationId: {}", caseId, correlationId);
        
        String ctrReportPath = regulatoryReportingService.generateCTR(caseId, eventData, correlationId);
        
        regulatoryReportingService.prepareFinCENSubmission(
            caseId,
            "CTR",
            ctrReportPath,
            correlationId
        );
    }
    
    private void handleKYCComplianceReport(String caseId, String reportId, Map<String, Object> eventData, String correlationId) {
        log.info("Generating KYC compliance report - reportId: {}, correlationId: {}", reportId, correlationId);
        
        String reportPath = complianceReportingService.generateKYCReport(reportId, eventData, correlationId);
        
        complianceReportingService.storeComplianceReport(
            reportId,
            "KYC_COMPLIANCE",
            reportPath,
            eventData,
            correlationId
        );
    }
    
    private void handlePCIComplianceReport(String caseId, String reportId, Map<String, Object> eventData, String correlationId) {
        log.info("Generating PCI DSS compliance report - reportId: {}, correlationId: {}", reportId, correlationId);
        
        String reportPath = complianceReportingService.generatePCIReport(reportId, eventData, correlationId);
        
        complianceReportingService.storeComplianceReport(
            reportId,
            "PCI_COMPLIANCE",
            reportPath,
            eventData,
            correlationId
        );
    }
    
    private void handleAuditTrailReport(String caseId, String reportId, Map<String, Object> eventData, String correlationId) {
        log.info("Generating audit trail report - reportId: {}, correlationId: {}", reportId, correlationId);
        
        String reportPath = complianceReportingService.generateAuditTrailReport(reportId, eventData, correlationId);
        
        complianceReportingService.storeComplianceReport(
            reportId,
            "AUDIT_TRAIL",
            reportPath,
            eventData,
            correlationId
        );
    }
    
    private void handleRegulatorySubmission(String reportId, Map<String, Object> eventData, String correlationId) {
        log.info("Preparing regulatory submission - reportId: {}, correlationId: {}", reportId, correlationId);
        
        String submissionType = extractString(eventData, "submissionType");
        
        regulatoryReportingService.prepareRegulatorySubmission(
            reportId,
            submissionType,
            eventData,
            correlationId
        );
    }
    
    private void handleGenericReport(String reportType, String caseId, String reportId,
                                    Map<String, Object> eventData, String correlationId) {
        log.info("Generating generic compliance report - reportType: {}, id: {}, correlationId: {}",
                reportType, caseId != null ? caseId : reportId, correlationId);
        
        String identifier = caseId != null ? caseId : reportId;
        
        String reportPath = complianceReportingService.generateGenericReport(
            identifier,
            reportType,
            eventData,
            correlationId
        );
        
        complianceReportingService.storeComplianceReport(
            identifier,
            reportType,
            reportPath,
            eventData,
            correlationId
        );
    }
    
    private void processComplianceReportEventFallback(String reportType, String caseId, String reportId,
                                                     Map<String, Object> eventData, String correlationId, Exception e) {
        log.error("Circuit breaker fallback: Failed to process compliance report - reportType: {}, " +
                "caseId: {}, reportId: {}, correlationId: {}, error: {}",
                reportType, caseId, reportId, correlationId, e.getMessage());
        
        auditService.logCircuitBreakerFallback(
            "COMPLIANCE_REPORT_PROCESSING",
            caseId != null ? caseId : reportId,
            correlationId,
            Map.of(
                "reportType", reportType,
                "caseId", caseId != null ? caseId : "N/A",
                "reportId", reportId != null ? reportId : "N/A",
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
            String reportType = extractString(eventData, "reportType");
            String caseId = extractString(eventData, "caseId");
            String correlationId = extractString(eventData, "correlationId");
            
            log.error("CRITICAL: Compliance report event moved to DLT - eventId: {}, reportType: {}, caseId: {}, " +
                    "originalTopic: {}, correlationId: {}, error: {}",
                    eventId, reportType, caseId, topic, correlationId, exceptionMessage);
            
            Map<String, Object> dltMetadata = new HashMap<>();
            dltMetadata.put("originalTopic", topic);
            dltMetadata.put("reportType", reportType);
            dltMetadata.put("caseId", caseId);
            dltMetadata.put("reportId", extractString(eventData, "reportId"));
            dltMetadata.put("exceptionMessage", exceptionMessage);
            dltMetadata.put("eventTimestamp", eventData.get("requestedAt"));
            dltMetadata.put("dltTimestamp", Instant.now());
            
            auditService.logDeadLetterEvent(
                eventId,
                TOPIC_NAME,
                "COMPLIANCE_REPORT_DLT",
                exceptionMessage,
                correlationId,
                dltMetadata
            );
            
            auditService.logCriticalAlert(
                "COMPLIANCE_REPORT_FAILED",
                reportType,
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