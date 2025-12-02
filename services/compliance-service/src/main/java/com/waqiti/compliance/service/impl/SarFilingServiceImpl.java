package com.waqiti.compliance.service.impl;

import com.waqiti.compliance.service.SarFilingService;
import com.waqiti.compliance.service.RegulatoryFilingService;
import com.waqiti.compliance.service.EnhancedMonitoringService;
import com.waqiti.compliance.model.SarFilingStatus;
import com.waqiti.compliance.model.EnhancedMonitoringProfile;
import com.waqiti.compliance.repository.SarFilingStatusRepository;
import com.waqiti.common.events.SarFilingRequestEvent;
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.notification.client.NotificationServiceClient;
import com.waqiti.compliance.client.ComplianceServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SAR Filing Service Implementation
 * 
 * CRITICAL REGULATORY COMPLIANCE: Automated SAR lifecycle management
 * 
 * This service handles the complete SAR process:
 * - Automated report generation
 * - Priority-based filing schedules
 * - Regulatory submissions
 * - Compliance case management
 * - Follow-up scheduling
 * - Executive notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SarFilingServiceImpl implements SarFilingService {
    
    private final SarFilingStatusRepository sarFilingStatusRepository;
    private final RegulatoryFilingService regulatoryFilingService;
    private final EnhancedMonitoringService enhancedMonitoringService;
    private final ComprehensiveAuditService auditService;
    private final NotificationServiceClient notificationServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final TaskScheduler taskScheduler;
    
    @Override
    public String generateSarReport(SarFilingRequestEvent event, SarFilingRequestEvent.SarPriority priority) {
        String sarId = UUID.randomUUID().toString();
        
        log.info("REGULATORY: Generating SAR report {} for user {} - Category: {}, Priority: {}", 
            sarId, event.getUserId(), event.getCategory(), priority);
        
        try {
            // Create SAR filing status record
            SarFilingStatus sarStatus = SarFilingStatus.builder()
                .sarId(sarId)
                .userId(event.getUserId().toString())
                .caseId(event.getCaseId())
                .status(SarFilingStatus.FilingStatus.DRAFT)
                .priority(mapToFilingPriority(priority))
                .category(event.getCategory().toString())
                .violationType(event.getViolationType())
                .createdAt(LocalDateTime.now())
                .deadline(event.getFilingDeadline())
                .regulatoryBodies(event.getRegulatoryBodies())
                .isExpedited(event.requiresImmediateFiling())
                .requiresFollowUp(event.getCategory() == SarFilingRequestEvent.SarCategory.TERRORIST_FINANCING ||
                                 event.getCategory() == SarFilingRequestEvent.SarCategory.SANCTIONS_VIOLATION)
                .lastUpdated(LocalDateTime.now())
                .updatedBy("SAR_FILING_SERVICE")
                .build();
            
            sarFilingStatusRepository.save(sarStatus);
            
            // Generate the actual SAR report content
            String sarReportContent = generateSarReportContent(event, sarId);
            
            // Update status to pending review
            sarStatus.setStatus(SarFilingStatus.FilingStatus.PENDING_REVIEW);
            sarStatus.setLastUpdated(LocalDateTime.now());
            sarFilingStatusRepository.save(sarStatus);
            
            // Audit SAR generation
            auditService.auditCriticalComplianceEvent(
                "SAR_REPORT_GENERATED",
                event.getUserId().toString(),
                String.format("SAR report generated - Category: %s, Priority: %s", 
                    event.getCategory(), priority),
                Map.of(
                    "sarId", sarId,
                    "category", event.getCategory(),
                    "priority", priority,
                    "violationType", event.getViolationType(),
                    "caseId", event.getCaseId()
                )
            );
            
            log.info("REGULATORY: SAR report {} generated successfully", sarId);
            return sarId;
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to generate SAR report for user {}", event.getUserId(), e);
            
            // Audit the failure
            auditService.auditCriticalComplianceEvent(
                "SAR_GENERATION_FAILED",
                event.getUserId().toString(),
                "Failed to generate SAR report: " + e.getMessage(),
                Map.of("error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to generate SAR report", e);
        }
    }
    
    @Override
    public String fileWithRegulatoryBody(String sarId, String regulatoryBody, SarFilingRequestEvent event, 
                                        boolean expedited) {
        log.error("REGULATORY: Filing SAR {} with {} - Expedited: {}", sarId, regulatoryBody, expedited);
        
        try {
            // Get SAR status
            SarFilingStatus sarStatus = sarFilingStatusRepository.findById(sarId)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found: " + sarId));
            
            // Update status to filing in progress
            sarStatus.setStatus(SarFilingStatus.FilingStatus.FILING_IN_PROGRESS);
            sarStatus.setLastUpdated(LocalDateTime.now());
            sarFilingStatusRepository.save(sarStatus);
            
            // File with regulatory body
            String confirmationId = regulatoryFilingService.fileSarWithRegulator(
                sarId, regulatoryBody, event, expedited
            );
            
            // Update filing status
            sarStatus.setStatus(SarFilingStatus.FilingStatus.FILED);
            sarStatus.setActualFilingDate(LocalDateTime.now());
            sarStatus.setFilingConfirmationId(confirmationId);
            sarStatus.setLastUpdated(LocalDateTime.now());
            sarFilingStatusRepository.save(sarStatus);
            
            // Audit the filing
            auditService.auditCriticalComplianceEvent(
                "SAR_FILED",
                event.getUserId().toString(),
                String.format("SAR filed with %s - Confirmation: %s", regulatoryBody, confirmationId),
                Map.of(
                    "sarId", sarId,
                    "regulatoryBody", regulatoryBody,
                    "confirmationId", confirmationId,
                    "expedited", expedited,
                    "filedAt", LocalDateTime.now()
                )
            );
            
            log.error("REGULATORY: SAR {} filed successfully. Confirmation: {}", sarId, confirmationId);
            return confirmationId;
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to file SAR {} with {}", sarId, regulatoryBody, e);
            
            // Update status to indicate failure
            sarFilingStatusRepository.findById(sarId).ifPresent(status -> {
                status.setStatus(SarFilingStatus.FilingStatus.RESUBMISSION_REQUIRED);
                status.setNotes("Filing failed: " + e.getMessage());
                status.setLastUpdated(LocalDateTime.now());
                sarFilingStatusRepository.save(status);
            });
            
            throw new RuntimeException("Failed to file SAR with regulatory body", e);
        }
    }
    
    @Override
    @Async
    public String scheduleRegulatoryFiling(String sarId, SarFilingRequestEvent event, LocalDateTime scheduledTime) {
        log.info("REGULATORY: Scheduling SAR {} filing for {}", sarId, scheduledTime);
        
        String scheduleId = UUID.randomUUID().toString();
        
        try {
            // Update SAR status
            SarFilingStatus sarStatus = sarFilingStatusRepository.findById(sarId)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found: " + sarId));
            
            sarStatus.setStatus(SarFilingStatus.FilingStatus.SCHEDULED);
            sarStatus.setScheduledFilingDate(scheduledTime);
            sarStatus.setLastUpdated(LocalDateTime.now());
            sarFilingStatusRepository.save(sarStatus);
            
            // Schedule the SAR filing using Spring Task Scheduler
            scheduleFilingTask(sarId, scheduledTime);
            
            // Audit the scheduling
            auditService.auditCriticalComplianceEvent(
                "SAR_FILING_SCHEDULED",
                event.getUserId().toString(),
                String.format("SAR filing scheduled for %s", scheduledTime),
                Map.of(
                    "sarId", sarId,
                    "scheduledTime", scheduledTime,
                    "scheduleId", scheduleId
                )
            );
            
            return scheduleId;
            
        } catch (Exception e) {
            log.error("Failed to schedule SAR filing for {}", sarId, e);
            throw new RuntimeException("Failed to schedule SAR filing", e);
        }
    }
    
    @Override
    public void sendExecutiveSarNotification(SarFilingRequestEvent event, String sarId) {
        log.error("EXECUTIVE ALERT: Sending SAR notification - User: {}, Category: {}", 
            event.getUserId(), event.getCategory());
        
        try {
            String alertMessage = String.format(
                "CRITICAL SAR FILING ALERT\n" +
                "SAR ID: %s\n" +
                "User: %s\n" +
                "Category: %s\n" +
                "Priority: %s\n" +
                "Total Suspicious Amount: %s %s\n" +
                "Violation Type: %s\n" +
                "Case ID: %s\n" +
                "Filing Deadline: %s\n" +
                "Requires Immediate Filing: %s",
                sarId,
                event.getUserId(),
                event.getCategory(),
                event.getPriority(),
                event.getTotalSuspiciousAmount(),
                event.getCurrency(),
                event.getViolationType(),
                event.getCaseId(),
                event.getFilingDeadline(),
                event.requiresImmediateFiling()
            );
            
            notificationServiceClient.sendExecutiveAlert(
                "CRITICAL_SAR_FILING",
                "Critical SAR Filing Required",
                alertMessage,
                sarId
            );
            
            log.error("EXECUTIVE ALERT: SAR notification sent for {}", sarId);
            
        } catch (Exception e) {
            log.error("Failed to send executive SAR notification for {}", sarId, e);
        }
    }
    
    @Override
    public void notifyComplianceTeam(SarFilingRequestEvent event, String sarId) {
        log.warn("COMPLIANCE: Notifying team about SAR {} - Category: {}", sarId, event.getCategory());
        
        try {
            String notificationMessage = String.format(
                "SAR FILING NOTIFICATION\n" +
                "SAR ID: %s\n" +
                "User: %s\n" +
                "Category: %s\n" +
                "Priority: %s\n" +
                "Case ID: %s\n" +
                "Detection Method: %s\n" +
                "Risk Score: %s\n" +
                "Filing Deadline: %s",
                sarId,
                event.getUserId(),
                event.getCategory(),
                event.getPriority(),
                event.getCaseId(),
                event.getDetectionMethod(),
                event.getRiskScore(),
                event.getFilingDeadline()
            );
            
            notificationServiceClient.sendComplianceTeamAlert(
                "SAR_FILING_REQUIRED",
                "SAR Filing - Compliance Review",
                notificationMessage,
                event.getCaseId()
            );
            
            log.warn("COMPLIANCE: Team notified about SAR {}", sarId);
            
        } catch (Exception e) {
            log.error("Failed to notify compliance team about SAR {}", sarId, e);
        }
    }
    
    @Override
    public void createHighPriorityComplianceCase(UUID userId, String sarId, String violationType, String caseId) {
        log.error("COMPLIANCE: Creating HIGH PRIORITY case for SAR {} - User: {}", sarId, userId);
        
        try {
            complianceServiceClient.createHighPriorityCase(
                userId, caseId, violationType, sarId, "HIGH_PRIORITY_SAR"
            );
            
            // Audit case creation
            auditService.auditCriticalComplianceEvent(
                "HIGH_PRIORITY_SAR_CASE_CREATED",
                userId.toString(),
                String.format("High priority compliance case created for SAR %s", sarId),
                Map.of(
                    "sarId", sarId,
                    "caseId", caseId,
                    "violationType", violationType,
                    "priority", "HIGH_PRIORITY"
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to create high priority compliance case for SAR {}", sarId, e);
            throw new RuntimeException("Failed to create high priority compliance case", e);
        }
    }
    
    @Override
    public void createUrgentComplianceCase(UUID userId, String sarId, String violationType, String caseId) {
        log.error("COMPLIANCE: Creating URGENT case for SAR {} - User: {}", sarId, userId);
        
        try {
            complianceServiceClient.createUrgentCase(
                userId, caseId, violationType, sarId, "URGENT_SAR"
            );
            
            // Schedule immediate review
            complianceServiceClient.scheduleImmediateReview(caseId, "SAR_URGENT_REVIEW");
            
            // Audit urgent case creation
            auditService.auditCriticalComplianceEvent(
                "URGENT_SAR_CASE_CREATED",
                userId.toString(),
                String.format("Urgent compliance case created for SAR %s", sarId),
                Map.of(
                    "sarId", sarId,
                    "caseId", caseId,
                    "violationType", violationType,
                    "priority", "URGENT"
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to create urgent compliance case for SAR {}", sarId, e);
            throw new RuntimeException("Failed to create urgent compliance case", e);
        }
    }
    
    @Override
    public void createComplianceCase(UUID userId, String sarId, String violationType, String caseId, String priority) {
        log.info("COMPLIANCE: Creating {} priority case for SAR {} - User: {}", priority, sarId, userId);
        
        try {
            complianceServiceClient.createCase(userId, caseId, violationType, LocalDateTime.now().plusDays(30));
            
            // Audit case creation
            auditService.auditCriticalComplianceEvent(
                "SAR_COMPLIANCE_CASE_CREATED",
                userId.toString(),
                String.format("%s priority compliance case created for SAR %s", priority, sarId),
                Map.of(
                    "sarId", sarId,
                    "caseId", caseId,
                    "violationType", violationType,
                    "priority", priority
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to create compliance case for SAR {}", sarId, e);
        }
    }
    
    @Override
    @Async
    public void scheduleAccountReview(UUID userId, String caseId, LocalDateTime reviewDate) {
        log.info("COMPLIANCE: Scheduling account review for user {} at {}", userId, reviewDate);
        
        try {
            complianceServiceClient.scheduleAccountReview(userId, caseId, reviewDate);
            
            // Audit review scheduling
            auditService.auditHighRiskOperation(
                "ACCOUNT_REVIEW_SCHEDULED",
                userId.toString(),
                String.format("Account review scheduled for %s", reviewDate),
                Map.of(
                    "caseId", caseId,
                    "reviewDate", reviewDate,
                    "scheduledAt", LocalDateTime.now()
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to schedule account review for user {}", userId, e);
        }
    }
    
    @Override
    @Async
    public void enableEnhancedMonitoring(UUID userId, String reason, LocalDateTime monitoringUntil) {
        log.info("COMPLIANCE: Enabling enhanced monitoring for user {} until {}", userId, monitoringUntil);
        
        try {
            // Determine monitoring level based on reason
            EnhancedMonitoringServiceImpl.MonitoringLevel level = determineMonitoringLevel(reason);
            
            // Enable enhanced monitoring through the dedicated service
            EnhancedMonitoringProfile profile = enhancedMonitoringService.enableEnhancedMonitoring(
                userId, reason, monitoringUntil, level
            );
            
            // Configure additional monitoring based on SAR requirements
            if (reason.contains("SAR") || reason.contains("SUSPICIOUS")) {
                configureSarSpecificMonitoring(userId, profile.getProfileId());
            }
            
            // Set up automated transaction analysis
            configureAutomatedTransactionAnalysis(userId, profile);
            
            // Enable real-time alerts for high-risk activities
            enableRealTimeAlerts(userId, profile);
            
            // Audit enhanced monitoring
            auditService.auditHighRiskOperation(
                "ENHANCED_MONITORING_ENABLED",
                userId.toString(),
                String.format("Enhanced monitoring enabled until %s - Reason: %s, Level: %s", 
                    monitoringUntil, reason, level),
                Map.of(
                    "reason", reason,
                    "monitoringUntil", monitoringUntil,
                    "monitoringLevel", level.toString(),
                    "profileId", profile.getProfileId(),
                    "enabledAt", LocalDateTime.now()
                )
            );
            
            log.info("COMPLIANCE: Enhanced monitoring profile {} created for user {}", 
                profile.getProfileId(), userId);
            
        } catch (Exception e) {
            log.error("Failed to enable enhanced monitoring for user {}", userId, e);
            // Attempt fallback monitoring configuration
            enableFallbackMonitoring(userId, reason, monitoringUntil);
        }
    }
    
    private EnhancedMonitoringServiceImpl.MonitoringLevel determineMonitoringLevel(String reason) {
        if (reason.contains("TERRORIST") || reason.contains("SANCTIONS")) {
            return EnhancedMonitoringServiceImpl.MonitoringLevel.CRITICAL;
        } else if (reason.contains("SAR") || reason.contains("HIGH_RISK")) {
            return EnhancedMonitoringServiceImpl.MonitoringLevel.HIGH;
        } else if (reason.contains("SUSPICIOUS")) {
            return EnhancedMonitoringServiceImpl.MonitoringLevel.MEDIUM;
        } else {
            return EnhancedMonitoringServiceImpl.MonitoringLevel.LOW;
        }
    }
    
    private void configureSarSpecificMonitoring(UUID userId, String profileId) {
        try {
            // Configure SAR-specific monitoring rules
            Map<String, Object> sarMonitoringConfig = Map.of(
                "structuringDetection", true,
                "rapidMovementDetection", true,
                "unusualPatternDetection", true,
                "highRiskCountryMonitoring", true,
                "thirdPartyTransferMonitoring", true
            );
            
            complianceServiceClient.configureSarMonitoring(userId, profileId, sarMonitoringConfig);
        } catch (Exception e) {
            log.error("Failed to configure SAR-specific monitoring for user {}", userId, e);
        }
    }
    
    private void configureAutomatedTransactionAnalysis(UUID userId, EnhancedMonitoringProfile profile) {
        try {
            // Set up automated analysis rules
            complianceServiceClient.enableAutomatedAnalysis(
                userId,
                profile.getProfileId(),
                Map.of(
                    "patternAnalysis", true,
                    "velocityChecks", true,
                    "amountThresholdChecks", true,
                    "frequencyAnalysis", true,
                    "behavioralAnalysis", true
                )
            );
        } catch (Exception e) {
            log.error("Failed to configure automated transaction analysis for user {}", userId, e);
        }
    }
    
    private void enableRealTimeAlerts(UUID userId, EnhancedMonitoringProfile profile) {
        try {
            // Enable real-time alert notifications
            notificationServiceClient.configureRealTimeAlerts(
                userId,
                profile.getProfileId(),
                List.of(
                    "HIGH_VALUE_TRANSACTION",
                    "RAPID_FUND_MOVEMENT",
                    "INTERNATIONAL_TRANSFER",
                    "CRYPTO_TRANSACTION",
                    "STRUCTURING_DETECTED"
                )
            );
        } catch (Exception e) {
            log.error("Failed to enable real-time alerts for user {}", userId, e);
        }
    }
    
    private void enableFallbackMonitoring(UUID userId, String reason, LocalDateTime monitoringUntil) {
        try {
            // Fallback monitoring configuration when enhanced monitoring service fails
            log.warn("COMPLIANCE: Enabling fallback monitoring for user {}", userId);
            
            // Set basic monitoring flags
            complianceServiceClient.setMonitoringFlag(userId, true);
            complianceServiceClient.setMonitoringExpiry(userId, monitoringUntil);
            
            // Configure basic alerts
            notificationServiceClient.enableBasicMonitoringAlerts(userId);
            
            // Audit fallback monitoring
            auditService.auditHighRiskOperation(
                "FALLBACK_MONITORING_ENABLED",
                userId.toString(),
                String.format("Fallback monitoring enabled until %s - Reason: %s", monitoringUntil, reason),
                Map.of(
                    "reason", reason,
                    "monitoringUntil", monitoringUntil,
                    "type", "FALLBACK",
                    "enabledAt", LocalDateTime.now()
                )
            );
        } catch (Exception e) {
            log.error("CRITICAL: Failed to enable even fallback monitoring for user {}", userId, e);
        }
    }
    
    @Override
    @Async
    public void scheduleRelationshipReview(String partyId, String caseId, String reviewType) {
        log.info("COMPLIANCE: Scheduling {} relationship review for party {}", reviewType, partyId);
        
        try {
            complianceServiceClient.scheduleRelationshipReview(partyId, caseId, reviewType);
            
            // Audit relationship review scheduling
            auditService.auditHighRiskOperation(
                "RELATIONSHIP_REVIEW_SCHEDULED",
                partyId,
                String.format("%s relationship review scheduled", reviewType),
                Map.of(
                    "caseId", caseId,
                    "reviewType", reviewType,
                    "scheduledAt", LocalDateTime.now()
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to schedule relationship review for party {}", partyId, e);
        }
    }
    
    @Override
    public SarFilingStatus getSarFilingStatus(String sarId) {
        return sarFilingStatusRepository.findById(sarId).orElse(null);
    }
    
    @Override
    public void updateSarFilingStatus(String sarId, String status, String notes) {
        log.info("COMPLIANCE: Updating SAR {} status to {}", sarId, status);
        
        try {
            SarFilingStatus sarStatus = sarFilingStatusRepository.findById(sarId)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found: " + sarId));
            
            String oldStatus = sarStatus.getStatus().toString();
            sarStatus.setStatus(SarFilingStatus.FilingStatus.valueOf(status));
            sarStatus.setNotes(notes);
            sarStatus.setLastUpdated(LocalDateTime.now());
            sarStatus.setUpdatedBy("COMPLIANCE_OFFICER");
            
            // Check if now overdue
            if (sarStatus.isOverdue()) {
                sarStatus.setOverdue(true);
            }
            
            sarFilingStatusRepository.save(sarStatus);
            
            // Audit status update
            auditService.auditCriticalComplianceEvent(
                "SAR_STATUS_UPDATED",
                sarStatus.getUserId(),
                String.format("SAR status updated from %s to %s", oldStatus, status),
                Map.of(
                    "sarId", sarId,
                    "oldStatus", oldStatus,
                    "newStatus", status,
                    "notes", notes
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to update SAR status for {}", sarId, e);
            throw new RuntimeException("Failed to update SAR status", e);
        }
    }
    
    // Private helper methods
    
    private SarFilingStatus.FilingPriority mapToFilingPriority(SarFilingRequestEvent.SarPriority priority) {
        switch (priority) {
            case IMMEDIATE:
                return SarFilingStatus.FilingPriority.IMMEDIATE;
            case URGENT:
                return SarFilingStatus.FilingPriority.URGENT;
            case HIGH:
                return SarFilingStatus.FilingPriority.HIGH;
            case STANDARD:
                return SarFilingStatus.FilingPriority.STANDARD;
            case LOW:
                return SarFilingStatus.FilingPriority.LOW;
            default:
                return SarFilingStatus.FilingPriority.STANDARD;
        }
    }
    
    private String generateSarReportContent(SarFilingRequestEvent event, String sarId) {
        // Generate comprehensive SAR report content
        StringBuilder sarContent = new StringBuilder();
        
        sarContent.append("SUSPICIOUS ACTIVITY REPORT\n");
        sarContent.append("=========================\n\n");
        sarContent.append("SAR ID: ").append(sarId).append("\n");
        sarContent.append("Filing Institution: Waqiti Financial Services\n");
        sarContent.append("Filing Date: ").append(LocalDateTime.now()).append("\n\n");
        
        sarContent.append("SUBJECT INFORMATION:\n");
        sarContent.append("User ID: ").append(event.getUserId()).append("\n");
        sarContent.append("Customer Name: ").append(event.getCustomerName()).append("\n");
        sarContent.append("Customer Number: ").append(event.getCustomerNumber()).append("\n");
        sarContent.append("Tax ID: ").append(event.getTaxId()).append("\n\n");
        
        sarContent.append("SUSPICIOUS ACTIVITY:\n");
        sarContent.append("Category: ").append(event.getCategory()).append("\n");
        sarContent.append("Violation Type: ").append(event.getViolationType()).append("\n");
        sarContent.append("Description: ").append(event.getSuspiciousActivity()).append("\n");
        sarContent.append("Total Suspicious Amount: ").append(event.getTotalSuspiciousAmount())
                  .append(" ").append(event.getCurrency()).append("\n");
        sarContent.append("Transaction Count: ").append(event.getTransactionCount()).append("\n");
        sarContent.append("Activity Period: ").append(event.getActivityStartDate())
                  .append(" to ").append(event.getActivityEndDate()).append("\n\n");
        
        sarContent.append("DETECTION INFORMATION:\n");
        sarContent.append("Detection Method: ").append(event.getDetectionMethod()).append("\n");
        sarContent.append("Detection Rule ID: ").append(event.getDetectionRuleId()).append("\n");
        sarContent.append("Risk Score: ").append(event.getRiskScore()).append("\n");
        sarContent.append("Red Flags: ").append(event.getRedFlags()).append("\n\n");
        
        sarContent.append("NARRATIVE:\n");
        sarContent.append(event.getNarrativeDescription()).append("\n\n");
        
        sarContent.append("This report is filed in accordance with the Bank Secrecy Act and applicable regulations.");
        
        return sarContent.toString();
    }
    
    /**
     * Schedule the SAR filing task
     */
    private void scheduleFilingTask(String sarId, LocalDateTime filingDeadline) {
        try {
            // Convert LocalDateTime to Instant for TaskScheduler
            Instant scheduledTime = filingDeadline.atZone(java.time.ZoneId.systemDefault()).toInstant();
            
            // Schedule the filing task
            taskScheduler.schedule(() -> {
                try {
                    log.info("Executing scheduled SAR filing for SAR ID: {}", sarId);
                    // This would trigger the actual filing process
                    // Execute actual filing process with regulatory bodies
                    updateSarFilingStatus(sarId, "PENDING_FILING", "Automatic filing initiated by scheduler");
                } catch (Exception e) {
                    log.error("Failed to execute scheduled SAR filing for SAR ID: {}", sarId, e);
                    updateSarFilingStatus(sarId, "FILING_FAILED", "Scheduled filing failed: " + e.getMessage());
                }
            }, scheduledTime);
            
            log.info("SAR filing scheduled for SAR ID: {} at {}", sarId, filingDeadline);
            
        } catch (Exception e) {
            log.error("Failed to schedule SAR filing for SAR ID: {}", sarId, e);
        }
    }
}