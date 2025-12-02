package com.waqiti.compliance.events;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.ComplianceAlertEvent;
import com.waqiti.common.kafka.KafkaDlqHandler;
import com.waqiti.compliance.domain.AlertSeverity;
import com.waqiti.compliance.domain.ComplianceAlert;
import com.waqiti.compliance.domain.AlertStatus;
import com.waqiti.compliance.service.ComplianceService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.CaseManagementService;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL: Processes compliance alert events to prevent regulatory violations
 * IMPACT: Prevents $10-50M regulatory fines and license revocation
 * COMPLIANCE: Required for AML/KYC, SAR filing, and regulatory reporting
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ComplianceAlertEventConsumer {

    private final ComplianceService complianceService;
    private final RegulatoryReportingService reportingService;
    private final CaseManagementService caseManagementService;
    private final AuditService auditService;
    private final KafkaDlqHandler kafkaDlqHandler;

    // Regulatory thresholds (configurable)
    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000.00"); // Currency Transaction Report
    private static final BigDecimal SAR_THRESHOLD = new BigDecimal("5000.00");  // Suspicious Activity Report
    private static final int VELOCITY_ALERT_COUNT = 10; // Transactions per hour

    @KafkaListener(
        topics = "compliance-alerts",
        groupId = "compliance-service-alerts",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Timed(name = "compliance.alert.processing.time", description = "Time taken to process compliance alert")
    @Counted(name = "compliance.alert.processed", description = "Number of compliance alerts processed")
    @Transactional(rollbackFor = Exception.class)
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000)
    )
    public void processComplianceAlert(
            @Payload ComplianceAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String messageKey,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        String correlationId = UUID.randomUUID().toString();
        
        log.info("Processing compliance alert event - AlertType: {}, UserId: {}, Amount: {}, Severity: {}, CorrelationId: {}",
                event.getAlertType(), event.getUserId(), event.getAmount(), event.getSeverity(), correlationId);

        try {
            // CRITICAL: Idempotency check to prevent duplicate alert processing
            if (complianceService.isAlertProcessed(event.getAlertId())) {
                log.info("Compliance alert already processed - AlertId: {}", event.getAlertId());
                acknowledgment.acknowledge();
                return;
            }

            // AUDIT: Record compliance alert processing attempt
            auditService.logComplianceAlertAttempt(event.getAlertType(), event.getUserId(),
                    event.getAmount(), event.getSeverity(), correlationId, LocalDateTime.now());

            // CREATION: Create compliance alert record
            ComplianceAlert alert = complianceService.createAlert(
                    event.getAlertId(),
                    event.getAlertType(),
                    event.getUserId(),
                    event.getTransactionId(),
                    event.getAmount(),
                    event.getCurrency(),
                    event.getSeverity(),
                    event.getAlertDetails(),
                    event.getTimestamp(),
                    correlationId
            );

            // PROCESSING: Route alert based on type and severity
            switch (event.getAlertType()) {
                case "LARGE_CASH_TRANSACTION":
                    processLargeCashTransactionAlert(alert, event, correlationId);
                    break;
                    
                case "SUSPICIOUS_ACTIVITY":
                    processSuspiciousActivityAlert(alert, event, correlationId);
                    break;
                    
                case "VELOCITY_ALERT":
                    processVelocityAlert(alert, event, correlationId);
                    break;
                    
                case "PEP_MATCH":
                    processPepAlert(alert, event, correlationId);
                    break;
                    
                case "SANCTIONS_SCREENING":
                    processSanctionsAlert(alert, event, correlationId);
                    break;
                    
                case "CROSS_BORDER_ALERT":
                    processCrossBorderAlert(alert, event, correlationId);
                    break;
                    
                case "STRUCTURING_PATTERN":
                    processStructuringAlert(alert, event, correlationId);
                    break;
                    
                default:
                    processGenericAlert(alert, event, correlationId);
                    log.warn("Unknown alert type processed as generic - AlertType: {}", event.getAlertType());
                    break;
            }

            // ESCALATION: Handle high-severity alerts immediately
            if (AlertSeverity.CRITICAL.equals(event.getSeverity()) || 
                AlertSeverity.HIGH.equals(event.getSeverity())) {
                
                escalateHighSeverityAlert(alert, correlationId);
            }

            // AUDIT: Log successful alert processing
            auditService.logComplianceAlertSuccess(event.getAlertId(), event.getAlertType(),
                    alert.getStatus(), correlationId, LocalDateTime.now());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Critical error processing compliance alert - AlertId: {}, Error: {}",
                    event.getAlertId(), e.getMessage(), e);

            // AUDIT: Log the error
            auditService.logComplianceAlertError(event.getAlertId(), event.getAlertType(),
                    correlationId, e.getMessage(), LocalDateTime.now());

            // CRITICAL: Compliance alerts cannot be lost - send to DLQ for manual review
            kafkaDlqHandler.sendToDlq(topic, messageKey, event, e.getMessage(),
                    "CRITICAL: Manual intervention required for compliance alert");

            acknowledgment.acknowledge(); // Acknowledge to prevent infinite retries

            // ALERT: Notify compliance team immediately
            complianceService.sendCriticalAlert("COMPLIANCE_ALERT_PROCESSING_ERROR",
                    event.getAlertId(), event.getAlertType(), e.getMessage());
        }
    }

    /**
     * REGULATORY: Process large cash transaction alerts (CTR requirements)
     */
    private void processLargeCashTransactionAlert(ComplianceAlert alert, ComplianceAlertEvent event, 
                                                String correlationId) {
        log.info("Processing large cash transaction alert - Amount: {} {}", event.getAmount(), event.getCurrency());

        // CTR FILING: Check if CTR filing is required (>$10,000)
        if (event.getAmount().compareTo(CTR_THRESHOLD) >= 0) {
            log.info("CTR filing required for large cash transaction - Amount: {} {}", 
                    event.getAmount(), event.getCurrency());

            // AUTOMATIC CTR: Create CTR filing
            var ctrResult = reportingService.createCtrFiling(
                    event.getUserId(),
                    event.getTransactionId(),
                    event.getAmount(),
                    event.getCurrency(),
                    event.getAlertDetails(),
                    correlationId
            );

            if (ctrResult.isSuccess()) {
                complianceService.updateAlertStatus(alert.getId(), AlertStatus.CTR_FILED);
                auditService.logCtrFiling(event.getUserId(), event.getAmount(), 
                        ctrResult.getCtrId(), correlationId, LocalDateTime.now());
                
                log.info("CTR filed successfully - CTRId: {}", ctrResult.getCtrId());
            } else {
                // CTR FAILURE: Critical - must be resolved
                complianceService.updateAlertStatus(alert.getId(), AlertStatus.CTR_FILING_FAILED);
                complianceService.sendCriticalAlert("CTR_FILING_FAILED", 
                        alert.getId(), event.getAlertType(), ctrResult.getErrorMessage());
                
                log.error("CTR filing failed - AlertId: {}, Error: {}", 
                        alert.getId(), ctrResult.getErrorMessage());
            }
        } else {
            // Below CTR threshold but flagged for other reasons
            complianceService.updateAlertStatus(alert.getId(), AlertStatus.REVIEWED);
            log.info("Large cash transaction below CTR threshold - Amount: {} {}", 
                    event.getAmount(), event.getCurrency());
        }
    }

    /**
     * REGULATORY: Process suspicious activity alerts (SAR requirements)
     */
    private void processSuspiciousActivityAlert(ComplianceAlert alert, ComplianceAlertEvent event, 
                                              String correlationId) {
        log.info("Processing suspicious activity alert - UserId: {}, Pattern: {}", 
                event.getUserId(), event.getAlertDetails().get("suspiciousPattern"));

        // CASE CREATION: Create investigation case
        var investigationCase = caseManagementService.createInvestigationCase(
                alert.getId(),
                event.getUserId(),
                "SUSPICIOUS_ACTIVITY",
                event.getAlertDetails(),
                correlationId
        );

        complianceService.updateAlertStatus(alert.getId(), AlertStatus.UNDER_INVESTIGATION);

        // SAR EVALUATION: Check if SAR filing is warranted
        var sarEvaluation = complianceService.evaluateForSarFiling(
                event.getUserId(),
                event.getTransactionId(),
                event.getAmount(),
                event.getAlertDetails()
        );

        if (sarEvaluation.isSarRequired()) {
            log.info("SAR filing required for suspicious activity - UserId: {}", event.getUserId());

            // AUTOMATIC SAR: Create SAR filing
            var sarResult = reportingService.createSarFiling(
                    event.getUserId(),
                    event.getTransactionId(),
                    event.getAmount(),
                    event.getCurrency(),
                    sarEvaluation.getSuspiciousActivityDescription(),
                    correlationId
            );

            if (sarResult.isSuccess()) {
                complianceService.updateAlertStatus(alert.getId(), AlertStatus.SAR_FILED);
                caseManagementService.updateCaseStatus(investigationCase.getId(), "SAR_FILED");
                
                auditService.logSarFiling(event.getUserId(), sarResult.getSarId(),
                        sarEvaluation.getSuspiciousActivityDescription(), correlationId, LocalDateTime.now());
                
                log.info("SAR filed successfully - SARId: {}", sarResult.getSarId());

                // ACCOUNT ACTION: Consider account restrictions if warranted
                if (sarEvaluation.isAccountRestrictionRecommended()) {
                    complianceService.recommendAccountRestriction(event.getUserId(),
                            "SAR filed - account review required", correlationId);
                }
            } else {
                // SAR FAILURE: Critical regulatory violation
                complianceService.updateAlertStatus(alert.getId(), AlertStatus.SAR_FILING_FAILED);
                complianceService.sendCriticalAlert("SAR_FILING_FAILED",
                        alert.getId(), event.getAlertType(), sarResult.getErrorMessage());
                
                log.error("SAR filing failed - AlertId: {}, Error: {}", 
                        alert.getId(), sarResult.getErrorMessage());
            }
        } else {
            // Continue investigation without immediate SAR
            caseManagementService.assignForReview(investigationCase.getId(), 
                    sarEvaluation.getRecommendedReviewLevel());
            
            log.info("Suspicious activity case created for review - CaseId: {}", investigationCase.getId());
        }
    }

    /**
     * MONITORING: Process transaction velocity alerts
     */
    private void processVelocityAlert(ComplianceAlert alert, ComplianceAlertEvent event, String correlationId) {
        log.info("Processing velocity alert - UserId: {}, TransactionCount: {}", 
                event.getUserId(), event.getAlertDetails().get("transactionCount"));

        // PATTERN ANALYSIS: Analyze velocity patterns
        var velocityAnalysis = complianceService.analyzeVelocityPattern(
                event.getUserId(),
                event.getTimestamp().minusHours(24), // Last 24 hours
                event.getAlertDetails()
        );

        if (velocityAnalysis.isAnomalousPattern()) {
            // SUSPICIOUS VELOCITY: Escalate for investigation
            log.warn("Anomalous velocity pattern detected - UserId: {}, Pattern: {}", 
                    event.getUserId(), velocityAnalysis.getPatternDescription());

            complianceService.updateAlertStatus(alert.getId(), AlertStatus.ESCALATED);
            
            // CREATE CASE: For investigation
            caseManagementService.createInvestigationCase(
                    alert.getId(),
                    event.getUserId(),
                    "VELOCITY_ANOMALY",
                    velocityAnalysis.getAnalysisDetails(),
                    correlationId
            );

            // TEMPORARY RESTRICTION: Consider temporary limits
            if (velocityAnalysis.isTemporaryRestrictionRecommended()) {
                complianceService.recommendTemporaryRestriction(event.getUserId(),
                        "Velocity alert - temporary limits applied", correlationId);
            }
        } else {
            // NORMAL VELOCITY: Log and close
            complianceService.updateAlertStatus(alert.getId(), AlertStatus.FALSE_POSITIVE);
            log.info("Velocity alert determined to be normal activity - UserId: {}", event.getUserId());
        }
    }

    /**
     * SANCTIONS: Process PEP (Politically Exposed Person) alerts
     */
    private void processPepAlert(ComplianceAlert alert, ComplianceAlertEvent event, String correlationId) {
        log.info("Processing PEP alert - UserId: {}, PEP Match: {}", 
                event.getUserId(), event.getAlertDetails().get("pepMatch"));

        // IMMEDIATE ACTION: PEP matches require immediate attention
        complianceService.updateAlertStatus(alert.getId(), AlertStatus.PEP_REVIEW_REQUIRED);

        // ENHANCED DUE DILIGENCE: Required for PEP customers
        var eddCase = caseManagementService.createEnhancedDueDiligenceCase(
                alert.getId(),
                event.getUserId(),
                event.getAlertDetails().get("pepMatch").toString(),
                correlationId
        );

        // ACCOUNT REVIEW: Flag account for enhanced monitoring
        complianceService.enableEnhancedMonitoring(event.getUserId(), "PEP match detected", correlationId);

        // NOTIFICATION: Notify compliance officers immediately
        complianceService.notifyComplianceOfficers("PEP_ALERT", alert.getId(), 
                event.getUserId(), event.getAlertDetails().get("pepMatch").toString());

        log.info("PEP alert processed - Enhanced due diligence case created: {}", eddCase.getId());
    }

    /**
     * SANCTIONS: Process sanctions screening alerts
     */
    private void processSanctionsAlert(ComplianceAlert alert, ComplianceAlertEvent event, String correlationId) {
        log.error("CRITICAL: Sanctions screening alert - UserId: {}, Match: {}", 
                event.getUserId(), event.getAlertDetails().get("sanctionsMatch"));

        // IMMEDIATE FREEZE: Sanctions matches require immediate account freeze
        complianceService.freezeAccountImmediately(event.getUserId(), 
                "Sanctions screening match - account frozen pending review", correlationId);

        complianceService.updateAlertStatus(alert.getId(), AlertStatus.ACCOUNT_FROZEN);

        // INVESTIGATION: Create high-priority investigation
        var sanctionsCase = caseManagementService.createSanctionsInvestigationCase(
                alert.getId(),
                event.getUserId(),
                event.getAlertDetails().get("sanctionsMatch").toString(),
                correlationId
        );

        // REGULATORY NOTIFICATION: May be required depending on jurisdiction
        reportingService.notifyRegulatorsOfSanctionsMatch(event.getUserId(),
                event.getAlertDetails().get("sanctionsMatch").toString(), correlationId);

        // CRITICAL ALERT: Notify senior management immediately
        complianceService.sendExecutiveAlert("SANCTIONS_MATCH", alert.getId(),
                event.getUserId(), event.getAlertDetails().get("sanctionsMatch").toString());

        log.error("Sanctions alert processed - Account frozen, investigation case created: {}", sanctionsCase.getId());
    }

    /**
     * CROSS-BORDER: Process cross-border transaction alerts
     */
    private void processCrossBorderAlert(ComplianceAlert alert, ComplianceAlertEvent event, String correlationId) {
        String sourceCountry = event.getAlertDetails().get("sourceCountry").toString();
        String destinationCountry = event.getAlertDetails().get("destinationCountry").toString();
        
        log.info("Processing cross-border alert - Source: {}, Destination: {}, Amount: {} {}",
                sourceCountry, destinationCountry, event.getAmount(), event.getCurrency());

        // SANCTIONS CHECK: Verify countries are not sanctioned
        var countrySanctionsCheck = complianceService.checkCountrySanctions(sourceCountry, destinationCountry);
        
        if (countrySanctionsCheck.isSanctioned()) {
            // BLOCK TRANSACTION: Sanctioned country detected
            complianceService.blockTransaction(event.getTransactionId(),
                    "Transaction blocked - sanctioned country: " + countrySanctionsCheck.getSanctionedCountry());
            
            complianceService.updateAlertStatus(alert.getId(), AlertStatus.TRANSACTION_BLOCKED);
            
            log.error("Cross-border transaction blocked - sanctioned country detected: {}",
                    countrySanctionsCheck.getSanctionedCountry());
        } else {
            // REGULATORY REPORTING: Check if reporting thresholds are met
            if (event.getAmount().compareTo(new BigDecimal("3000.00")) >= 0) {
                reportingService.createCrossBorderReport(event.getTransactionId(),
                        sourceCountry, destinationCountry, event.getAmount(), 
                        event.getCurrency(), correlationId);
            }
            
            complianceService.updateAlertStatus(alert.getId(), AlertStatus.REVIEWED);
            log.info("Cross-border alert reviewed and approved");
        }
    }

    /**
     * AML: Process structuring pattern alerts
     */
    private void processStructuringAlert(ComplianceAlert alert, ComplianceAlertEvent event, String correlationId) {
        log.warn("Processing structuring alert - UserId: {}, Pattern: {}", 
                event.getUserId(), event.getAlertDetails().get("structuringPattern"));

        // INVESTIGATION: Structuring is a serious AML violation
        var structuringCase = caseManagementService.createStructuringInvestigationCase(
                alert.getId(),
                event.getUserId(),
                event.getAlertDetails(),
                correlationId
        );

        complianceService.updateAlertStatus(alert.getId(), AlertStatus.STRUCTURING_INVESTIGATION);

        // SAR FILING: Structuring typically requires SAR filing
        var sarResult = reportingService.createSarFiling(
                event.getUserId(),
                event.getTransactionId(),
                event.getAmount(),
                event.getCurrency(),
                "Potential structuring activity detected: " + event.getAlertDetails().get("structuringPattern"),
                correlationId
        );

        if (sarResult.isSuccess()) {
            caseManagementService.updateCaseStatus(structuringCase.getId(), "SAR_FILED");
            log.info("Structuring SAR filed - SARId: {}", sarResult.getSarId());
        }

        // ENHANCED MONITORING: Enable enhanced monitoring for this customer
        complianceService.enableEnhancedMonitoring(event.getUserId(), 
                "Structuring pattern detected", correlationId);

        log.info("Structuring alert processed - Investigation case: {}", structuringCase.getId());
    }

    /**
     * GENERIC: Process generic compliance alerts
     */
    private void processGenericAlert(ComplianceAlert alert, ComplianceAlertEvent event, String correlationId) {
        log.info("Processing generic compliance alert - AlertType: {}", event.getAlertType());

        // STANDARD REVIEW: Queue for standard compliance review
        complianceService.updateAlertStatus(alert.getId(), AlertStatus.PENDING_REVIEW);
        
        caseManagementService.queueForReview(alert.getId(), "STANDARD", event.getAlertDetails(), correlationId);
        
        log.info("Generic compliance alert queued for review - AlertId: {}", alert.getId());
    }

    /**
     * ESCALATION: Handle high-severity alerts
     */
    private void escalateHighSeverityAlert(ComplianceAlert alert, String correlationId) {
        log.warn("Escalating high-severity compliance alert - AlertId: {}, Severity: {}", 
                alert.getId(), alert.getSeverity());

        // IMMEDIATE NOTIFICATION: Notify compliance team
        complianceService.notifyComplianceOfficers("HIGH_SEVERITY_ALERT", alert.getId(),
                alert.getUserId(), alert.getAlertType());

        // PRIORITY REVIEW: Mark for priority review
        caseManagementService.markForPriorityReview(alert.getId(), "HIGH_SEVERITY", correlationId);

        // AUDIT: Log escalation
        auditService.logAlertEscalation(alert.getId(), alert.getSeverity(), 
                "High severity alert escalated", correlationId, LocalDateTime.now());
    }
}