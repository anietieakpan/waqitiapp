package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.compliance.domain.ComplianceAlert;
import com.waqiti.compliance.domain.KycVerification;
import com.waqiti.compliance.event.KycEvent;
import com.waqiti.compliance.service.ComplianceAlertService;
import com.waqiti.compliance.service.KycService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * DLQ Handler for KnowYourCustomerComplianceConsumer
 *
 * Production-grade recovery logic for failed KYC compliance events
 *
 * Recovery Strategies:
 * 1. Third-party API Failures -> Retry with exponential backoff
 * 2. Data Validation Errors -> Flag for manual review
 * 3. Sanctions List Match -> Immediate escalation to compliance team
 * 4. Expired Documents -> Notify customer, create compliance ticket
 * 5. PEP Detection -> Enhanced due diligence workflow
 * 6. Regulatory Reporting Failures -> Store for batch retry
 *
 * Compliance Note: All DLQ events are logged for regulatory audit.
 * Failures in KYC processing may trigger SAR (Suspicious Activity Report) filing.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Service
@Slf4j
public class KnowYourCustomerComplianceConsumerDlqHandler extends BaseDlqConsumer<Object> {

    @Autowired
    private KycService kycService;

    @Autowired
    private ComplianceAlertService alertService;

    @Autowired
    private RegulatoryReportingService reportingService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public KnowYourCustomerComplianceConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("KnowYourCustomerComplianceConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.KnowYourCustomerComplianceConsumer.dlq:KnowYourCustomerComplianceConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            String failureReason = (String) headers.getOrDefault("kafka_exception-message", "Unknown");
            int failureCount = (int) headers.getOrDefault("kafka_dlt-original-offset", 0);

            log.warn("Processing KYC compliance DLQ event: failureReason={} failureCount={}",
                failureReason, failureCount);

            // Convert to KycEvent
            KycEvent kycEvent = convertToKycEvent(event);

            if (kycEvent == null) {
                log.error("Unable to parse KYC event from DLQ - creating audit record");
                createAuditRecord(event, failureReason);
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Create compliance audit trail for all DLQ events
            auditComplianceFailure(kycEvent, failureReason);

            // Categorize and handle failure
            if (isThirdPartyApiFailure(failureReason)) {
                return handleThirdPartyApiFailure(kycEvent, failureCount);
            }

            if (isDataValidationError(failureReason)) {
                return handleDataValidationError(kycEvent, failureReason);
            }

            if (isSanctionsListMatch(failureReason)) {
                return handleSanctionsListMatch(kycEvent);
            }

            if (isExpiredDocumentError(failureReason)) {
                return handleExpiredDocument(kycEvent);
            }

            if (isPepDetection(failureReason)) {
                return handlePepDetection(kycEvent);
            }

            if (isRegulatoryReportingFailure(failureReason)) {
                return handleRegulatoryReportingFailure(kycEvent);
            }

            if (isHighRiskJurisdiction(failureReason)) {
                return handleHighRiskJurisdiction(kycEvent);
            }

            // Unknown error - requires compliance team review
            log.error("COMPLIANCE: Unknown KYC DLQ error: {} for user: {}",
                failureReason, kycEvent.getUserId());
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

        } catch (Exception e) {
            log.error("CRITICAL: Error handling KYC DLQ event", e);
            // Compliance failures require manual review
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Handle third-party KYC provider API failures
     */
    private DlqProcessingResult handleThirdPartyApiFailure(KycEvent event, int failureCount) {
        if (failureCount >= 3) {
            log.error("Max retry attempts for KYC API: userId={}", event.getUserId());

            // Switch to backup KYC provider if available
            String backupProvider = kycService.getBackupKycProvider(event.getKycProvider());

            if (backupProvider != null) {
                log.info("Switching to backup KYC provider: userId={} from={} to={}",
                    event.getUserId(), event.getKycProvider(), backupProvider);

                kycService.updateKycProvider(event.getUserId(), backupProvider);
                kafkaTemplate.send("kyc-verification-requests", event);

                return DlqProcessingResult.SUCCESS;
            }

            // No backup - escalate to manual review
            createComplianceTicket(event, "KYC_API_FAILURE", failureCount);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        log.info("Retrying KYC API call: userId={} attempt={}", event.getUserId(), failureCount + 1);
        return DlqProcessingResult.RETRY;
    }

    /**
     * Handle KYC data validation errors
     */
    private DlqProcessingResult handleDataValidationError(KycEvent event, String reason) {
        log.warn("KYC data validation error: userId={} reason={}", event.getUserId(), reason);

        // Flag account for manual KYC review
        kycService.flagForManualReview(
            event.getUserId(),
            "DATA_VALIDATION_FAILURE",
            reason
        );

        // Notify user of missing/invalid documents
        kafkaTemplate.send("customer-notifications", Map.of(
            "userId", event.getUserId(),
            "type", "KYC_DOCUMENT_INVALID",
            "message", "Your identity verification documents require review. Please update your documents.",
            "severity", "HIGH"
        ));

        // Successfully handled - no retry needed
        return DlqProcessingResult.SUCCESS;
    }

    /**
     * Handle sanctions list matches - CRITICAL
     */
    private DlqProcessingResult handleSanctionsListMatch(KycEvent event) {
        log.error("CRITICAL: Sanctions list match detected: userId={}", event.getUserId());

        // Immediately freeze account
        kycService.freezeAccount(
            event.getUserId(),
            "SANCTIONS_LIST_MATCH",
            event.getVerificationId()
        );

        // Create high-priority compliance alert
        ComplianceAlert alert = alertService.createCriticalAlert(
            event.getUserId(),
            "SANCTIONS_LIST_MATCH",
            event.getSanctionsDetails(),
            "IMMEDIATE_ACTION_REQUIRED"
        );

        // Notify compliance team immediately
        kafkaTemplate.send("compliance-critical-alerts", Map.of(
            "alertId", alert.getId(),
            "userId", event.getUserId(),
            "alertType", "SANCTIONS_LIST_MATCH",
            "severity", "CRITICAL",
            "requiresImmediateAction", true
        ));

        // File SAR (Suspicious Activity Report) if required
        if (kycService.requiresSarFiling(event)) {
            reportingService.initiateSarFiling(
                event.getUserId(),
                "SANCTIONS_LIST_MATCH",
                event.getVerificationId()
            );
        }

        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    }

    /**
     * Handle expired document errors
     */
    private DlqProcessingResult handleExpiredDocument(KycEvent event) {
        log.warn("Expired KYC document: userId={} docType={}", event.getUserId(), event.getDocumentType());

        // Mark verification as expired
        kycService.markVerificationExpired(
            event.getUserId(),
            event.getVerificationId(),
            event.getDocumentType()
        );

        // Request document renewal
        kafkaTemplate.send("customer-notifications", Map.of(
            "userId", event.getUserId(),
            "type", "KYC_DOCUMENT_EXPIRED",
            "message", "Your " + event.getDocumentType() + " has expired. Please upload a new document.",
            "severity", "HIGH",
            "actionRequired", true
        ));

        // Create compliance ticket for follow-up
        createComplianceTicket(event, "EXPIRED_DOCUMENT", 0);

        return DlqProcessingResult.SUCCESS;
    }

    /**
     * Handle PEP (Politically Exposed Person) detection
     */
    private DlqProcessingResult handlePepDetection(KycEvent event) {
        log.warn("PEP detected: userId={}", event.getUserId());

        // Flag for enhanced due diligence
        kycService.flagForEnhancedDueDiligence(
            event.getUserId(),
            "PEP_DETECTED",
            event.getPepDetails()
        );

        // Create compliance alert
        alertService.createAlert(
            event.getUserId(),
            "PEP_DETECTED",
            event.getPepDetails(),
            "ENHANCED_DUE_DILIGENCE_REQUIRED"
        );

        // Notify compliance team for EDD workflow
        kafkaTemplate.send("compliance-alerts", Map.of(
            "userId", event.getUserId(),
            "alertType", "PEP_DETECTED",
            "severity", "HIGH",
            "workflowType", "ENHANCED_DUE_DILIGENCE"
        ));

        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    }

    /**
     * Handle regulatory reporting failures
     */
    private DlqProcessingResult handleRegulatoryReportingFailure(KycEvent event) {
        try {
            log.error("Regulatory reporting failure: userId={} reportType={}",
                event.getUserId(), event.getReportType());

            // Store for batch retry
            reportingService.storeForBatchRetry(
                event.getUserId(),
                event.getReportType(),
                event
            );

            // Schedule batch retry
            reportingService.scheduleBatchRetry(event.getReportType(), 3600); // 1 hour

            return DlqProcessingResult.SUCCESS;

        } catch (Exception e) {
            log.error("Failed to handle regulatory reporting failure: userId={}", event.getUserId(), e);
            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }
    }

    /**
     * Handle high-risk jurisdiction detection
     */
    private DlqProcessingResult handleHighRiskJurisdiction(KycEvent event) {
        log.warn("High-risk jurisdiction detected: userId={} country={}",
            event.getUserId(), event.getCountry());

        // Apply enhanced monitoring
        kycService.applyEnhancedMonitoring(
            event.getUserId(),
            "HIGH_RISK_JURISDICTION",
            event.getCountry()
        );

        // Create compliance alert
        alertService.createAlert(
            event.getUserId(),
            "HIGH_RISK_JURISDICTION",
            Map.of("country", event.getCountry()),
            "ENHANCED_MONITORING_APPLIED"
        );

        return DlqProcessingResult.SUCCESS;
    }

    // ========== Helper Methods ==========

    private KycEvent convertToKycEvent(Object event) {
        try {
            if (event instanceof KycEvent) {
                return (KycEvent) event;
            }
            return objectMapper.convertValue(event, KycEvent.class);
        } catch (Exception e) {
            log.error("Failed to convert event to KycEvent", e);
            return null;
        }
    }

    private void auditComplianceFailure(KycEvent event, String reason) {
        log.info("COMPLIANCE_AUDIT: userId={} verificationId={} failureReason={} timestamp={}",
            event.getUserId(),
            event.getVerificationId(),
            reason,
            System.currentTimeMillis());

        kycService.createAuditLog(
            event.getUserId(),
            "KYC_DLQ_EVENT",
            reason,
            event
        );
    }

    private void createAuditRecord(Object event, String reason) {
        log.error("COMPLIANCE_AUDIT: Failed to parse event - reason={} event={}", reason, event);
    }

    private void createComplianceTicket(KycEvent event, String ticketType, int attemptCount) {
        kycService.createComplianceTicket(
            event.getUserId(),
            ticketType,
            Map.of(
                "verificationId", event.getVerificationId(),
                "attemptCount", attemptCount,
                "kycProvider", event.getKycProvider()
            )
        );
    }

    private boolean isThirdPartyApiFailure(String reason) {
        return reason != null && (
            reason.contains("API") ||
            reason.contains("timeout") ||
            reason.contains("connection") ||
            reason.contains("503") ||
            reason.contains("502") ||
            reason.contains("unavailable")
        );
    }

    private boolean isDataValidationError(String reason) {
        return reason != null && (
            reason.contains("validation") ||
            reason.contains("invalid") ||
            reason.contains("missing") ||
            reason.contains("required") ||
            reason.contains("format")
        );
    }

    private boolean isSanctionsListMatch(String reason) {
        return reason != null && (
            reason.contains("sanctions") ||
            reason.contains("OFAC") ||
            reason.contains("watchlist") ||
            reason.contains("blocked")
        );
    }

    private boolean isExpiredDocumentError(String reason) {
        return reason != null && (
            reason.contains("expired") ||
            reason.contains("expiration") ||
            reason.contains("no longer valid")
        );
    }

    private boolean isPepDetection(String reason) {
        return reason != null && (
            reason.contains("PEP") ||
            reason.contains("politically exposed") ||
            reason.contains("public official")
        );
    }

    private boolean isRegulatoryReportingFailure(String reason) {
        return reason != null && (
            reason.contains("reporting") ||
            reason.contains("regulatory") ||
            reason.contains("filing") ||
            reason.contains("SAR") ||
            reason.contains("CTR")
        );
    }

    private boolean isHighRiskJurisdiction(String reason) {
        return reason != null && (
            reason.contains("high-risk") ||
            reason.contains("jurisdiction") ||
            reason.contains("FATF") ||
            reason.contains("restricted country")
        );
    }

    @Override
    protected String getServiceName() {
        return "KnowYourCustomerComplianceConsumer";
    }

    @Override
    protected boolean isCriticalEvent(Object event) {
        // All KYC/compliance events are critical for regulatory purposes
        return true;
    }
}
