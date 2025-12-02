package com.waqiti.compliance.kafka;

import com.waqiti.compliance.event.ComplianceScreeningEvent;
import com.waqiti.compliance.service.ScreeningService;
import com.waqiti.compliance.service.SanctionsService;
import com.waqiti.compliance.service.AmlService;
import com.waqiti.compliance.service.RegulatoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-grade Kafka consumer for compliance screening events
 * Handles: compliance-screening-errors, sanctions-clearance-notifications, compliance-screening-completed,
 * aml-alerts, regulatory-notifications, pci-audit-events, compliance-warnings, compliance-incidents
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceScreeningConsumer {

    private final ScreeningService screeningService;
    private final SanctionsService sanctionsService;
    private final AmlService amlService;
    private final RegulatoryService regulatoryService;

    @KafkaListener(topics = {"compliance-screening-errors", "sanctions-clearance-notifications",
                             "compliance-screening-completed", "aml-alerts", "regulatory-notifications",
                             "pci-audit-events", "compliance-warnings", "compliance-incidents"}, 
                   groupId = "compliance-screening-processor")
    @Transactional
    public void processComplianceScreening(@Payload ComplianceScreeningEvent event,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment) {
        try {
            log.info("Processing compliance screening: {} - Type: {} - Entity: {} - Risk: {}", 
                    event.getScreeningId(), event.getScreeningType(), event.getEntityId(), event.getRiskLevel());
            
            // Process based on topic/type
            switch (topic) {
                case "compliance-screening-errors" -> handleScreeningError(event);
                case "sanctions-clearance-notifications" -> handleSanctionsClearance(event);
                case "compliance-screening-completed" -> handleScreeningCompleted(event);
                case "aml-alerts" -> handleAmlAlert(event);
                case "regulatory-notifications" -> handleRegulatoryNotification(event);
                case "pci-audit-events" -> handlePciAudit(event);
                case "compliance-warnings" -> handleComplianceWarning(event);
                case "compliance-incidents" -> handleComplianceIncident(event);
            }
            
            // Update compliance dashboard
            updateComplianceDashboard(event);
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed compliance screening: {}", event.getScreeningId());
            
        } catch (Exception e) {
            log.error("Failed to process compliance screening {}: {}", 
                    event.getScreeningId(), e.getMessage(), e);
            throw new RuntimeException("Compliance screening processing failed", e);
        }
    }

    private void handleScreeningError(ComplianceScreeningEvent event) {
        // Log screening error
        screeningService.logScreeningError(
            event.getScreeningId(),
            event.getErrorType(),
            event.getErrorMessage(),
            event.getEntityId()
        );
        
        // Attempt retry if recoverable
        if (event.isRecoverableError()) {
            screeningService.scheduleRetry(
                event.getScreeningId(),
                event.getRetryCount() + 1,
                LocalDateTime.now().plusMinutes(5)
            );
        } else {
            // Escalate to manual review
            screeningService.escalateToManualReview(
                event.getScreeningId(),
                "SCREENING_ERROR",
                event.getErrorDetails()
            );
        }
        
        // Apply precautionary block
        if (event.isApplyPrecautionaryBlock()) {
            screeningService.applyPrecautionaryBlock(
                event.getEntityId(),
                "SCREENING_ERROR"
            );
        }
    }

    private void handleSanctionsClearance(ComplianceScreeningEvent event) {
        // Process sanctions clearance
        String clearanceStatus = event.getClearanceStatus();
        
        if ("CLEARED".equals(clearanceStatus)) {
            // Entity cleared
            sanctionsService.recordClearance(
                event.getEntityId(),
                event.getScreeningId(),
                event.getClearanceLevel(),
                event.getClearedBy()
            );
            
            // Remove restrictions
            sanctionsService.removeRestrictions(event.getEntityId());
            
        } else if ("HIT".equals(clearanceStatus)) {
            // Sanctions hit detected
            sanctionsService.handleSanctionsHit(
                event.getEntityId(),
                event.getSanctionsList(),
                event.getMatchScore(),
                event.getMatchedName()
            );
            
            // Apply immediate freeze
            sanctionsService.freezeAllActivity(
                event.getEntityId(),
                "SANCTIONS_HIT"
            );
            
            // File regulatory report
            regulatoryService.fileSanctionsReport(
                event.getEntityId(),
                event.getSanctionsList(),
                event.getMatchDetails()
            );
        } else if ("PENDING".equals(clearanceStatus)) {
            // Requires manual review
            sanctionsService.escalateForReview(
                event.getEntityId(),
                event.getScreeningId(),
                event.getPendingReason()
            );
        }
    }

    private void handleScreeningCompleted(ComplianceScreeningEvent event) {
        // Record screening completion
        screeningService.recordCompletion(
            event.getScreeningId(),
            event.getScreeningResult(),
            event.getRiskScore(),
            event.getCompletedAt()
        );
        
        // Process based on result
        String result = event.getScreeningResult();
        
        if ("PASS".equals(result)) {
            // Clear for processing
            screeningService.clearEntity(
                event.getEntityId(),
                event.getClearanceExpiry()
            );
        } else if ("FAIL".equals(result)) {
            // Block entity
            screeningService.blockEntity(
                event.getEntityId(),
                event.getFailureReasons()
            );
        } else if ("REVIEW".equals(result)) {
            // Manual review required
            screeningService.requireManualReview(
                event.getEntityId(),
                event.getReviewReasons()
            );
        }
        
        // Update entity risk profile
        screeningService.updateRiskProfile(
            event.getEntityId(),
            event.getRiskScore(),
            event.getRiskFactors()
        );
    }

    private void handleAmlAlert(ComplianceScreeningEvent event) {
        // Process AML alert
        String alertId = amlService.createAlert(
            event.getEntityId(),
            event.getAlertType(),
            event.getSuspiciousIndicators(),
            event.getTransactionPattern()
        );
        
        // Determine if SAR filing required
        if (amlService.requiresSarFiling(event.getSuspiciousIndicators(), event.getAmount())) {
            // Initiate SAR filing
            String sarId = amlService.initiateSarFiling(
                event.getEntityId(),
                alertId,
                event.getSuspiciousIndicators(),
                event.getAmount()
            );
            
            // Set filing deadline
            amlService.setSarDeadline(
                sarId,
                LocalDateTime.now().plusDays(30)
            );
        }
        
        // Apply enhanced monitoring
        amlService.applyEnhancedMonitoring(
            event.getEntityId(),
            event.getMonitoringLevel(),
            event.getMonitoringDuration()
        );
        
        // Notify compliance team
        amlService.notifyComplianceTeam(
            alertId,
            event.getAlertSeverity(),
            event.getRequiredActions()
        );
    }

    private void handleRegulatoryNotification(ComplianceScreeningEvent event) {
        // Process regulatory notification
        regulatoryService.processNotification(
            event.getNotificationId(),
            event.getRegulator(),
            event.getNotificationType(),
            event.getRequiredAction()
        );
        
        // Create compliance task
        if (event.getRequiredAction() != null) {
            regulatoryService.createComplianceTask(
                event.getNotificationId(),
                event.getRequiredAction(),
                event.getDeadline(),
                event.getAssignedTo()
            );
        }
        
        // File required reports
        if (event.getRequiredReports() != null) {
            for (String reportType : event.getRequiredReports()) {
                regulatoryService.scheduleReport(
                    reportType,
                    event.getRegulator(),
                    event.getReportDeadline()
                );
            }
        }
        
        // Update compliance calendar
        regulatoryService.updateComplianceCalendar(
            event.getNotificationId(),
            event.getDeadline(),
            event.getNotificationType()
        );
    }

    private void handlePciAudit(ComplianceScreeningEvent event) {
        // Process PCI audit event
        regulatoryService.recordPciAuditEvent(
            event.getAuditId(),
            event.getAuditType(),
            event.getAuditScope(),
            event.getFindings()
        );
        
        // Process findings
        if (event.getFindings() != null && !event.getFindings().isEmpty()) {
            for (Map<String, Object> finding : event.getFindings()) {
                String severity = (String) finding.get("severity");
                
                if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
                    // Create remediation task
                    regulatoryService.createRemediationTask(
                        event.getAuditId(),
                        finding,
                        event.getRemediationDeadline()
                    );
                }
            }
        }
        
        // Update PCI compliance status
        regulatoryService.updatePciStatus(
            event.getEntityId(),
            event.getComplianceLevel(),
            event.getNextAuditDate()
        );
    }

    private void handleComplianceWarning(ComplianceScreeningEvent event) {
        // Log compliance warning
        screeningService.logComplianceWarning(
            event.getWarningId(),
            event.getWarningType(),
            event.getWarningMessage(),
            event.getEntityId()
        );
        
        // Determine escalation
        if (event.getWarningCount() >= 3) {
            // Escalate repeated warnings
            screeningService.escalateWarnings(
                event.getEntityId(),
                event.getWarningType(),
                event.getWarningCount()
            );
        }
        
        // Apply preventive measures
        if (event.getPreventiveMeasures() != null) {
            screeningService.applyPreventiveMeasures(
                event.getEntityId(),
                event.getPreventiveMeasures()
            );
        }
        
        // Schedule follow-up
        screeningService.scheduleFollowUp(
            event.getWarningId(),
            event.getFollowUpDate(),
            event.getFollowUpActions()
        );
    }

    private void handleComplianceIncident(ComplianceScreeningEvent event) {
        // Create compliance incident
        String incidentId = screeningService.createIncident(
            event.getIncidentType(),
            event.getSeverity(),
            event.getDescription(),
            event.getAffectedEntities()
        );
        
        // Initiate incident response
        screeningService.initiateIncidentResponse(
            incidentId,
            event.getResponsePlan(),
            event.getResponseTeam()
        );
        
        // Apply containment measures
        if ("CRITICAL".equals(event.getSeverity())) {
            screeningService.applyContainmentMeasures(
                event.getAffectedEntities(),
                event.getContainmentActions()
            );
        }
        
        // File regulatory notifications
        if (event.isRegulatoryNotificationRequired()) {
            regulatoryService.fileIncidentNotification(
                incidentId,
                event.getRegulators(),
                event.getNotificationDeadline()
            );
        }
        
        // Create post-incident review
        screeningService.schedulePostIncidentReview(
            incidentId,
            LocalDateTime.now().plusDays(7)
        );
    }

    private void updateComplianceDashboard(ComplianceScreeningEvent event) {
        // Update real-time compliance metrics
        screeningService.updateDashboard(
            event.getScreeningType(),
            event.getScreeningResult(),
            event.getRiskLevel(),
            event.getTimestamp()
        );
        
        // Update compliance scores
        if (event.getComplianceScore() != null) {
            screeningService.updateComplianceScore(
                event.getEntityId(),
                event.getComplianceScore()
            );
        }
    }
}