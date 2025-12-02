package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.compliance.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for CTR filing events
 * Handles Currency Transaction Report filing events for BSA compliance
 * with automated threshold monitoring and regulatory reporting
 *
 * Critical for: BSA compliance, CTR reporting, AML monitoring
 * SLA: Must process CTR filing events within 5 seconds for regulatory requirements
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CtrFilingEventsConsumer {

    private final CtrFilingService ctrFilingService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ComplianceWorkflowService complianceWorkflowService;
    private final LegalNotificationService legalNotificationService;
    private final ComplianceAuditService complianceAuditService;
    private final UniversalDLQHandler universalDLQHandler;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // CTR threshold constants
    private static final double CTR_THRESHOLD = 10000.0;

    // Metrics
    private final Counter ctrFilingCounter = Counter.builder("ctr_filing_events_processed_total")
            .description("Total number of CTR filing events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter thresholdExceededCounter = Counter.builder("ctr_threshold_exceeded_total")
            .description("Total number of CTR threshold exceeded events")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("ctr_filing_processing_duration")
            .description("Time taken to process CTR filing events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"ctr-filing-events"},
        groupId = "compliance-service-ctr-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "ctr-filing-processor", fallbackMethod = "handleCtrFilingFailure")
    @Retry(name = "ctr-filing-processor")
    public void processCtrFilingEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();

        log.info("COMPLIANCE: Processing CTR filing event: {} from topic: {} partition: {} offset: {}",
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("CTR filing event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate CTR data
            CtrFilingData ctrData = extractCtrFilingData(event.getPayload());
            validateCtrFilingData(ctrData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process CTR filing event
            processCtrFiling(ctrData, event);

            // Record successful processing metrics
            ctrFilingCounter.increment();

            if (ctrData.getTransactionAmount() >= CTR_THRESHOLD) {
                thresholdExceededCounter.increment();
            }

            // Audit the CTR filing processing
            auditCtrFilingProcessing(ctrData, event, "SUCCESS");

            log.info("COMPLIANCE: Successfully processed CTR filing: {} for account: {} - amount: {} status: {}",
                    eventId, ctrData.getAccountId(), ctrData.getTransactionAmount(), ctrData.getFilingStatus());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("COMPLIANCE: Invalid CTR filing data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process CTR filing: {}", eventId, e);
            auditCtrFilingProcessing(null, event, "FAILED: " + e.getMessage());

            // Send to DLQ for retry/parking
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, GenericKafkaEvent> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        topic, partition, offset, eventId, event);
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send CTR filing to DLQ: {}", eventId, dlqEx);
            }

            throw new RuntimeException("CTR filing processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private CtrFilingData extractCtrFilingData(Map<String, Object> payload) {
        return CtrFilingData.builder()
                .ctrId(extractString(payload, "ctrId"))
                .accountId(extractString(payload, "accountId"))
                .customerId(extractString(payload, "customerId"))
                .transactionId(extractString(payload, "transactionId"))
                .transactionAmount(extractDouble(payload, "transactionAmount"))
                .transactionDate(extractInstant(payload, "transactionDate"))
                .transactionType(extractString(payload, "transactionType"))
                .filingStatus(extractString(payload, "filingStatus"))
                .filingReason(extractString(payload, "filingReason"))
                .reportingInstitution(extractString(payload, "reportingInstitution"))
                .reportingOfficer(extractString(payload, "reportingOfficer"))
                .filingDeadline(extractInstant(payload, "filingDeadline"))
                .complianceNotes(extractString(payload, "complianceNotes"))
                .exemptionStatus(extractString(payload, "exemptionStatus"))
                .exemptionType(extractString(payload, "exemptionType"))
                .build();
    }

    private void validateCtrFilingData(CtrFilingData ctrData) {
        if (ctrData.getCtrId() == null || ctrData.getCtrId().trim().isEmpty()) {
            throw new IllegalArgumentException("CTR ID is required");
        }

        if (ctrData.getAccountId() == null || ctrData.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }

        if (ctrData.getTransactionAmount() == null || ctrData.getTransactionAmount() <= 0) {
            throw new IllegalArgumentException("Valid transaction amount is required");
        }

        if (ctrData.getTransactionDate() == null) {
            throw new IllegalArgumentException("Transaction date is required");
        }

        List<String> validStatuses = List.of("PENDING", "FILED", "REJECTED", "CORRECTED", "EXEMPTED");
        if (!validStatuses.contains(ctrData.getFilingStatus())) {
            throw new IllegalArgumentException("Invalid filing status: " + ctrData.getFilingStatus());
        }
    }

    private void processCtrFiling(CtrFilingData ctrData, GenericKafkaEvent event) {
        log.info("COMPLIANCE: Processing CTR filing - CTR: {}, Account: {}, Amount: {}, Type: {}",
                ctrData.getCtrId(), ctrData.getAccountId(),
                ctrData.getTransactionAmount(), ctrData.getTransactionType());

        try {
            // Process based on filing status
            switch (ctrData.getFilingStatus()) {
                case "PENDING":
                    handlePendingFiling(ctrData);
                    break;
                case "FILED":
                    handleFiledCtr(ctrData);
                    break;
                case "REJECTED":
                    handleRejectedFiling(ctrData);
                    break;
                case "CORRECTED":
                    handleCorrectedFiling(ctrData);
                    break;
                case "EXEMPTED":
                    handleExemptedTransaction(ctrData);
                    break;
                default:
                    log.warn("Unknown CTR filing status: {}", ctrData.getFilingStatus());
            }

            // Perform threshold analysis
            if (ctrData.getTransactionAmount() >= CTR_THRESHOLD) {
                ctrFilingService.flagThresholdExceeded(
                        ctrData.getCtrId(),
                        "SINGLE_TRANSACTION",
                        ctrData.getTransactionAmount()
                );
            }

            // Generate CTR documentation
            regulatoryReportingService.generateFinCenCtrForm(ctrData);

            // Update regulatory monitoring
            regulatoryReportingService.updateBsaComplianceMetrics(
                    ctrData.getFilingStatus(),
                    ctrData.getTransactionAmount(),
                    ctrData.getTransactionType()
            );

            log.info("COMPLIANCE: CTR filing processed - CTR: {}, Status: {}, Amount: {}",
                    ctrData.getCtrId(), ctrData.getFilingStatus(), ctrData.getTransactionAmount());

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to process CTR filing for: {}", ctrData.getCtrId(), e);
            executeEmergencyCtrProcedures(ctrData, e);
            throw new RuntimeException("CTR filing processing failed", e);
        }
    }

    private void handlePendingFiling(CtrFilingData ctrData) {
        ctrFilingService.prepareCtrDocumentation(ctrData);

        if (ctrFilingService.areFilingRequirementsMet(ctrData.getCtrId())) {
            ctrFilingService.scheduleCtrFiling(ctrData.getCtrId(), ctrData.getFilingDeadline());
        }

        legalNotificationService.notifyComplianceTeam(
                "CTR Filing Pending",
                String.format("CTR %s for amount $%.2f requires filing",
                        ctrData.getCtrId(), ctrData.getTransactionAmount())
        );
    }

    private void handleFiledCtr(CtrFilingData ctrData) {
        ctrFilingService.confirmCtrFiling(ctrData.getCtrId());

        complianceAuditService.recordSuccessfulCtrFiling(
                ctrData.getCtrId(),
                ctrData.getTransactionAmount(),
                ctrData.getTransactionDate()
        );

        regulatoryReportingService.generateCtrFilingConfirmation(ctrData);
    }

    private void handleRejectedFiling(CtrFilingData ctrData) {
        ctrFilingService.logFilingRejection(ctrData.getCtrId(), ctrData.getComplianceNotes());

        legalNotificationService.sendUrgentComplianceAlert(
                "CTR Filing Rejected",
                String.format("CTR %s filing was rejected - correction required", ctrData.getCtrId()),
                ctrData.getReportingOfficer()
        );

        complianceWorkflowService.initiateCtrCorrectionWorkflow(
                ctrData.getCtrId(),
                ctrData.getComplianceNotes()
        );
    }

    private void handleCorrectedFiling(CtrFilingData ctrData) {
        ctrFilingService.processCorrectedFiling(ctrData);

        complianceAuditService.recordCtrCorrection(ctrData.getCtrId(), ctrData.getComplianceNotes());

        regulatoryReportingService.resubmitCorrectedCtr(ctrData);
    }

    private void handleExemptedTransaction(CtrFilingData ctrData) {
        ctrFilingService.validateCtrExemption(
                ctrData.getCtrId(),
                ctrData.getExemptionType(),
                ctrData.getCustomerId()
        );

        complianceAuditService.recordCtrExemption(
                ctrData.getCtrId(),
                ctrData.getExemptionType(),
                ctrData.getTransactionAmount()
        );
    }

    private void executeEmergencyCtrProcedures(CtrFilingData ctrData, Exception error) {
        log.error("EMERGENCY: Executing emergency CTR procedures due to processing failure");

        try {
            ctrFilingService.prepareEmergencyFiling(ctrData.getCtrId());

            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: CTR Filing Processing Failed",
                    String.format("Failed to process CTR filing for %s: %s",
                            ctrData.getCtrId(), error.getMessage())
            );

            legalNotificationService.escalateToManualIntervention(
                    ctrData.getCtrId(),
                    "CTR_FILING_PROCESSING_FAILED",
                    error.getMessage()
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Emergency CTR procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("COMPLIANCE: CTR filing validation failed for event: {}", event.getEventId(), e);

        auditService.auditSecurityEvent(
                "CTR_FILING_VALIDATION_ERROR",
                null,
                "CTR filing validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditCtrFilingProcessing(CtrFilingData ctrData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "CTR_FILING_PROCESSED",
                    ctrData != null ? ctrData.getAccountId() : null,
                    String.format("CTR filing processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "ctrId", ctrData != null ? ctrData.getCtrId() : "unknown",
                            "filingStatus", ctrData != null ? ctrData.getFilingStatus() : "unknown",
                            "transactionAmount", ctrData != null ? ctrData.getTransactionAmount() : 0.0,
                            "transactionType", ctrData != null ? ctrData.getTransactionType() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit CTR filing processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("CRITICAL: CTR filing event sent to DLT - EventId: {}", event.getEventId());

        try {
            CtrFilingData ctrData = extractCtrFilingData(event.getPayload());
            ctrFilingService.prepareEmergencyFiling(ctrData.getCtrId());

            legalNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: CTR Filing in DLT",
                    "CTR filing could not be processed - immediate manual intervention required"
            );

        } catch (Exception e) {
            log.error("Failed to handle CTR filing DLT event: {}", event.getEventId(), e);
        }
    }

    public void handleCtrFilingFailure(GenericKafkaEvent event, String topic, int partition,
                                     long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for CTR filing processing - EventId: {}",
                event.getEventId(), e);

        try {
            CtrFilingData ctrData = extractCtrFilingData(event.getPayload());
            ctrFilingService.prepareEmergencyFiling(ctrData.getCtrId());

            legalNotificationService.sendEmergencySystemAlert(
                    "CTR Filing Circuit Breaker Open",
                    "CTR filing processing is failing - BSA compliance at risk"
            );

        } catch (Exception ex) {
            log.error("Failed to handle CTR filing circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    // Data class
    @lombok.Data
    @lombok.Builder
    public static class CtrFilingData {
        private String ctrId;
        private String accountId;
        private String customerId;
        private String transactionId;
        private Double transactionAmount;
        private Instant transactionDate;
        private String transactionType;
        private String filingStatus;
        private String filingReason;
        private String reportingInstitution;
        private String reportingOfficer;
        private Instant filingDeadline;
        private String complianceNotes;
        private String exemptionStatus;
        private String exemptionType;
    }
}