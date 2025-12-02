package com.waqiti.notification.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.notification.event.ComplianceNotificationEvent;
import com.waqiti.notification.service.NotificationService;
import com.waqiti.notification.service.ComplianceAlertService;
import com.waqiti.notification.service.RegulatoryReportingService;
import com.waqiti.notification.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Production-grade Kafka consumer for compliance notifications
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceNotificationConsumer {

    private final NotificationService notificationService;
    private final ComplianceAlertService alertService;
    private final RegulatoryReportingService regulatoryService;
    private final AuditService auditService;
    private final UniversalDLQHandler universalDLQHandler;

    @KafkaListener(topics = "compliance-notifications", groupId = "compliance-notification-processor")
    public void processComplianceNotification(@Payload ComplianceNotificationEvent event,
                                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                            @Header(KafkaHeaders.OFFSET) long offset,
                                            Acknowledgment acknowledgment) {
        try {
            log.info("Processing compliance notification: {} type: {} severity: {} urgent: {}", 
                    event.getNotificationId(), event.getComplianceType(), 
                    event.getSeverity(), event.isUrgent());
            
            // Validate event
            validateComplianceNotification(event);
            
            // Process based on compliance type
            switch (event.getComplianceType()) {
                case "AML_ALERT" -> handleAmlAlert(event);
                case "KYC_ALERT" -> handleKycAlert(event);
                case "SANCTIONS_HIT" -> handleSanctionsHit(event);
                case "SUSPICIOUS_ACTIVITY" -> handleSuspiciousActivity(event);
                case "REGULATORY_BREACH" -> handleRegulatoryBreach(event);
                case "TRANSACTION_MONITORING" -> handleTransactionMonitoring(event);
                case "PEP_ALERT" -> handlePepAlert(event);
                case "RISK_THRESHOLD" -> handleRiskThreshold(event);
                case "COMPLIANCE_REVIEW" -> handleComplianceReview(event);
                case "AUDIT_FINDING" -> handleAuditFinding(event);
                default -> handleGenericCompliance(event);
            }
            
            // Send notifications to appropriate parties
            sendComplianceNotifications(event);
            
            // File regulatory reports if required
            if (event.isRegulatoryReportingRequired()) {
                fileRegulatoryReports(event);
            }
            
            // Create audit trail
            createComplianceAuditTrail(event);
            
            // Escalate if urgent
            if (event.isUrgent()) {
                escalateToManagement(event);
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed compliance notification: {}", event.getNotificationId());
            
        } catch (Exception e) {
            log.error("Failed to process compliance notification {}: {}",
                    event.getNotificationId(), e.getMessage(), e);

            // Use UniversalDLQHandler for enhanced DLQ routing
            universalDLQHandler.sendToDLQ(
                event,
                topic,
                partition,
                offset,
                e,
                Map.of(
                    "consumerGroup", "compliance-notification-processor",
                    "errorType", e.getClass().getSimpleName(),
                    "notificationId", event.getNotificationId() != null ? event.getNotificationId() : "unknown",
                    "complianceType", event.getComplianceType() != null ? event.getComplianceType() : "unknown"
                )
            );

            throw new RuntimeException("Compliance notification processing failed", e);
        }
    }

    private void validateComplianceNotification(ComplianceNotificationEvent event) {
        if (event.getNotificationId() == null || event.getNotificationId().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification ID is required");
        }
        
        if (event.getComplianceType() == null || event.getComplianceType().trim().isEmpty()) {
            throw new IllegalArgumentException("Compliance type is required");
        }
        
        if (event.getSeverity() == null || event.getSeverity().trim().isEmpty()) {
            throw new IllegalArgumentException("Severity is required");
        }
    }

    private void handleAmlAlert(ComplianceNotificationEvent event) {
        // Process AML alert
        alertService.processAmlAlert(
            event.getEntityId(),
            event.getAlertReason(),
            event.getRiskScore(),
            event.getTransactionIds()
        );
        
        // Check if SAR filing is required
        if (event.getRiskScore() > 85 || event.isSarRequired()) {
            regulatoryService.initiateSarFiling(
                event.getEntityId(),
                event.getAlertReason(),
                event.getSuspiciousIndicators(),
                event.getTransactionAmount()
            );
        }
        
        // Apply restrictions
        if (event.isApplyRestrictions()) {
            alertService.applyAmlRestrictions(
                event.getEntityId(),
                event.getRestrictionLevel()
            );
        }
        
        // Notify AML team
        notificationService.notifyAmlTeam(
            event.getNotificationId(),
            event.getEntityId(),
            event.getAlertReason(),
            event.getSeverity()
        );
    }

    private void handleKycAlert(ComplianceNotificationEvent event) {
        // Process KYC alert
        alertService.processKycAlert(
            event.getEntityId(),
            event.getKycIssue(),
            event.getDocumentStatus(),
            event.getVerificationFailures()
        );
        
        // Request additional documentation if needed
        if (event.isAdditionalDocsRequired()) {
            notificationService.requestAdditionalDocuments(
                event.getEntityId(),
                event.getRequiredDocuments(),
                event.getDeadline()
            );
        }
        
        // Update KYC status
        alertService.updateKycStatus(
            event.getEntityId(),
            event.getKycStatus(),
            event.getKycReviewNotes()
        );
    }

    private void handleSanctionsHit(ComplianceNotificationEvent event) {
        // Critical - immediate action required
        log.error("Sanctions hit detected for entity: {} list: {} score: {}", 
                event.getEntityId(), event.getSanctionsList(), event.getMatchScore());
        
        // Freeze account immediately
        alertService.freezeAccount(
            event.getEntityId(),
            "SANCTIONS_HIT",
            event.getSanctionsList()
        );
        
        // Block all transactions
        alertService.blockAllTransactions(event.getEntityId());
        
        // File regulatory report
        regulatoryService.fileSanctionsReport(
            event.getEntityId(),
            event.getSanctionsList(),
            event.getMatchScore(),
            event.getMatchedName(),
            event.getMatchDetails()
        );
        
        // Notify all relevant parties
        notificationService.sendSanctionsAlert(
            event.getEntityId(),
            event.getSanctionsList(),
            List.of("compliance-team", "legal-team", "senior-management")
        );
    }

    private void handleSuspiciousActivity(ComplianceNotificationEvent event) {
        // Log suspicious activity
        alertService.logSuspiciousActivity(
            event.getEntityId(),
            event.getActivityType(),
            event.getActivityDetails(),
            event.getSuspiciousIndicators()
        );
        
        // Determine if SAR is required
        boolean sarRequired = alertService.evaluateSarRequirement(
            event.getEntityId(),
            event.getActivityType(),
            event.getTransactionAmount(),
            event.getSuspiciousIndicators()
        );
        
        if (sarRequired) {
            // File SAR
            String sarId = regulatoryService.fileSuspiciousActivityReport(
                event.getEntityId(),
                event.getActivityType(),
                event.getActivityDetails(),
                event.getTransactionIds(),
                event.getFilingDeadline()
            );
            
            // Track SAR filing
            alertService.trackSarFiling(
                sarId,
                event.getEntityId(),
                event.getFilingDeadline()
            );
        }
        
        // Enhanced monitoring
        alertService.enableEnhancedMonitoring(
            event.getEntityId(),
            event.getMonitoringDuration(),
            event.getMonitoringLevel()
        );
    }

    private void handleRegulatoryBreach(ComplianceNotificationEvent event) {
        // Log regulatory breach
        regulatoryService.logRegulatoryBreach(
            event.getBreachType(),
            event.getRegulation(),
            event.getBreachDetails(),
            event.getImpactAssessment()
        );
        
        // Initiate remediation
        String remediationId = alertService.initiateRemediation(
            event.getBreachType(),
            event.getRemediationSteps(),
            event.getRemediationDeadline()
        );
        
        // File regulatory disclosure
        if (event.isDisclosureRequired()) {
            regulatoryService.fileRegulatoryDisclosure(
                event.getRegulator(),
                event.getBreachType(),
                event.getBreachDetails(),
                remediationId
            );
        }
        
        // Notify board and senior management
        notificationService.notifyBoardOfDirectors(
            event.getBreachType(),
            event.getSeverity(),
            event.getImpactAssessment(),
            event.getRemediationSteps()
        );
    }

    private void handleTransactionMonitoring(ComplianceNotificationEvent event) {
        // Analyze transaction patterns
        Map<String, Object> analysis = alertService.analyzeTransactionPatterns(
            event.getEntityId(),
            event.getTransactionIds(),
            event.getMonitoringPeriod()
        );
        
        // Check for red flags
        List<String> redFlags = alertService.identifyRedFlags(analysis);
        
        if (!redFlags.isEmpty()) {
            // Create monitoring case
            alertService.createMonitoringCase(
                event.getEntityId(),
                redFlags,
                analysis,
                event.getCaseDeadline()
            );
            
            // Apply transaction controls
            alertService.applyTransactionControls(
                event.getEntityId(),
                event.getControlLevel()
            );
        }
        
        // Update risk profile
        alertService.updateRiskProfile(
            event.getEntityId(),
            event.getRiskScore(),
            redFlags
        );
    }

    private void handlePepAlert(ComplianceNotificationEvent event) {
        // Process PEP (Politically Exposed Person) alert
        alertService.processPepAlert(
            event.getEntityId(),
            event.getPepStatus(),
            event.getPepDetails(),
            event.getRelationshipType()
        );
        
        // Apply enhanced due diligence
        alertService.applyEnhancedDueDiligence(
            event.getEntityId(),
            "PEP",
            event.getDueDiligenceLevel()
        );
        
        // Set up ongoing monitoring
        alertService.setupPepMonitoring(
            event.getEntityId(),
            event.getMonitoringFrequency()
        );
        
        // Get senior management approval if required
        if (event.isSeniorApprovalRequired()) {
            notificationService.requestSeniorApproval(
                event.getEntityId(),
                "PEP_RELATIONSHIP",
                event.getPepDetails()
            );
        }
    }

    private void handleRiskThreshold(ComplianceNotificationEvent event) {
        // Process risk threshold breach
        alertService.processRiskThresholdBreach(
            event.getEntityId(),
            event.getRiskType(),
            event.getCurrentRiskScore(),
            event.getThreshold()
        );
        
        // Apply risk-based controls
        alertService.applyRiskBasedControls(
            event.getEntityId(),
            event.getCurrentRiskScore(),
            event.getControlMeasures()
        );
        
        // Schedule risk review
        alertService.scheduleRiskReview(
            event.getEntityId(),
            event.getReviewDeadline(),
            event.getReviewType()
        );
    }

    private void handleComplianceReview(ComplianceNotificationEvent event) {
        // Schedule compliance review
        String reviewId = alertService.scheduleComplianceReview(
            event.getEntityId(),
            event.getReviewType(),
            event.getReviewScope(),
            event.getReviewDeadline()
        );
        
        // Assign to compliance officer
        alertService.assignToComplianceOfficer(
            reviewId,
            event.getAssignedOfficer(),
            event.getPriority()
        );
        
        // Send review notification
        notificationService.sendReviewNotification(
            event.getAssignedOfficer(),
            reviewId,
            event.getReviewType(),
            event.getReviewDeadline()
        );
    }

    private void handleAuditFinding(ComplianceNotificationEvent event) {
        // Process audit finding
        alertService.processAuditFinding(
            event.getFindingId(),
            event.getFindingType(),
            event.getFindingSeverity(),
            event.getFindingDetails()
        );
        
        // Create corrective action plan
        alertService.createCorrectiveActionPlan(
            event.getFindingId(),
            event.getCorrectiveActions(),
            event.getActionDeadlines()
        );
        
        // Track remediation progress
        alertService.trackRemediationProgress(
            event.getFindingId(),
            event.getRemediationStatus()
        );
    }

    private void handleGenericCompliance(ComplianceNotificationEvent event) {
        // Log generic compliance event
        alertService.logComplianceEvent(
            event.getNotificationId(),
            event.getComplianceType(),
            event.getEventDetails()
        );
        
        // Send to appropriate team
        notificationService.routeToComplianceTeam(
            event.getComplianceType(),
            event.getNotificationId(),
            event.getSeverity()
        );
    }

    private void sendComplianceNotifications(ComplianceNotificationEvent event) {
        // Determine recipients based on severity and type
        List<String> recipients = determineRecipients(event);
        
        // Send notifications
        for (String recipient : recipients) {
            notificationService.sendComplianceAlert(
                recipient,
                event.getComplianceType(),
                event.getSeverity(),
                event.getAlertMessage(),
                event.getActionRequired()
            );
        }
        
        // Send to external parties if required
        if (event.isExternalNotificationRequired()) {
            notificationService.sendExternalCompliance(
                event.getExternalRecipients(),
                event.getComplianceType(),
                event.getExternalMessage()
            );
        }
    }

    private List<String> determineRecipients(ComplianceNotificationEvent event) {
        List<String> recipients = new java.util.ArrayList<>();
        
        // Always notify compliance team
        recipients.add("compliance-team");
        
        // Add based on severity
        switch (event.getSeverity()) {
            case "CRITICAL" -> {
                recipients.add("senior-management");
                recipients.add("board-of-directors");
                recipients.add("legal-team");
            }
            case "HIGH" -> {
                recipients.add("senior-management");
                recipients.add("risk-management");
            }
            case "MEDIUM" -> {
                recipients.add("risk-management");
            }
        }
        
        // Add specific teams based on type
        if (event.getComplianceType().contains("AML")) {
            recipients.add("aml-team");
        }
        if (event.getComplianceType().contains("SANCTIONS")) {
            recipients.add("sanctions-team");
        }
        
        return recipients;
    }

    private void fileRegulatoryReports(ComplianceNotificationEvent event) {
        // Determine which reports to file
        List<String> requiredReports = regulatoryService.determineRequiredReports(
            event.getComplianceType(),
            event.getJurisdiction(),
            event.getSeverity()
        );
        
        // File each report
        for (String reportType : requiredReports) {
            String reportId = regulatoryService.fileReport(
                reportType,
                event.getEntityId(),
                event.getReportData(),
                event.getFilingDeadline()
            );
            
            // Track filing
            regulatoryService.trackReportFiling(
                reportId,
                reportType,
                event.getRegulator()
            );
        }
    }

    private void createComplianceAuditTrail(ComplianceNotificationEvent event) {
        auditService.createComplianceAudit(
            event.getNotificationId(),
            event.getComplianceType(),
            event.getEntityId(),
            event.getSeverity(),
            event.getActions(),
            event.getDecisions(),
            event.getTimestamp()
        );
    }

    private void escalateToManagement(ComplianceNotificationEvent event) {
        // Create escalation
        String escalationId = alertService.createEscalation(
            event.getNotificationId(),
            event.getComplianceType(),
            event.getEscalationReason(),
            event.getSeverity()
        );
        
        // Send immediate alerts
        notificationService.sendUrgentAlert(
            "senior-management",
            escalationId,
            event.getComplianceType(),
            event.getEscalationMessage()
        );
        
        // Schedule follow-up
        alertService.scheduleEscalationFollowUp(
            escalationId,
            LocalDateTime.now().plusHours(1)
        );
    }
}