package com.waqiti.user.service.impl;

import com.waqiti.user.service.ComplianceReportingService;
import com.waqiti.common.events.AccountFreezeRequestEvent;
import com.waqiti.notification.client.NotificationServiceClient;
import com.waqiti.compliance.client.ComplianceServiceClient;
import com.waqiti.common.audit.ComprehensiveAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Compliance Reporting Service Implementation
 * 
 * CRITICAL REGULATORY IMPACT: Ensures timely regulatory notifications
 * 
 * This service handles critical regulatory reporting:
 * - Law enforcement notifications
 * - Regulatory body notifications
 * - SAR filing
 * - Compliance team alerts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceReportingServiceImpl implements ComplianceReportingService {
    
    private final NotificationServiceClient notificationServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final ComprehensiveAuditService auditService;
    
    @Override
    public void notifyLawEnforcement(AccountFreezeRequestEvent event, String freezeId) {
        log.error("LAW ENFORCEMENT: Sending notification for account freeze - User: {}, Reason: {}", 
            event.getUserId(), event.getFreezeReason());
        
        try {
            String notificationMessage = String.format(
                "LAW ENFORCEMENT NOTIFICATION\n" +
                "Account Freeze Alert\n\n" +
                "User ID: %s\n" +
                "Account ID: %s\n" +
                "Freeze Reason: %s\n" +
                "Freeze ID: %s\n" +
                "Case ID: %s\n" +
                "Investigation ID: %s\n" +
                "Total Account Balance: %s\n" +
                "Pending Transactions: %d\n" +
                "Risk Score: %s\n" +
                "Suspicious Activity: %s\n" +
                "Requested At: %s\n" +
                "Requesting System: %s",
                event.getUserId(),
                event.getAccountId(),
                event.getFreezeReason(),
                freezeId,
                event.getCaseId(),
                event.getInvestigationId(),
                event.getTotalAccountBalance(),
                event.getPendingTransactionCount(),
                event.getRiskScore(),
                event.getSuspiciousActivityPattern(),
                event.getRequestedAt(),
                event.getRequestingSystem()
            );
            
            // Send to law enforcement API
            notificationServiceClient.sendLawEnforcementNotification(
                "ACCOUNT_FREEZE",
                "Account Freeze - Potential Criminal Activity",
                notificationMessage,
                event.getCaseId()
            );
            
            // Audit the law enforcement notification
            auditService.auditCriticalSecurityEvent(
                "LAW_ENFORCEMENT_NOTIFIED",
                event.getUserId().toString(),
                "Law enforcement notified of account freeze",
                Map.of(
                    "freezeId", freezeId,
                    "freezeReason", event.getFreezeReason().toString(),
                    "caseId", event.getCaseId(),
                    "notifiedAt", LocalDateTime.now()
                )
            );
            
            log.error("LAW ENFORCEMENT: Notification sent for freeze ID: {}", freezeId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to notify law enforcement for freeze {}", freezeId, e);
            
            // Audit the failure - this is critical
            auditService.auditCriticalSecurityEvent(
                "LAW_ENFORCEMENT_NOTIFICATION_FAILED",
                event.getUserId().toString(),
                "CRITICAL: Failed to notify law enforcement: " + e.getMessage(),
                Map.of(
                    "freezeId", freezeId,
                    "error", e.getMessage()
                )
            );
        }
    }
    
    @Override
    public void notifyRegulators(AccountFreezeRequestEvent event, String freezeId) {
        log.error("REGULATORY: Sending regulatory notification for account freeze - User: {}", event.getUserId());
        
        try {
            String regulatoryMessage = String.format(
                "REGULATORY COMPLIANCE NOTIFICATION\n" +
                "Account Freeze Report\n\n" +
                "Financial Institution: Waqiti\n" +
                "Report Type: Account Freeze\n" +
                "User ID: %s\n" +
                "Account ID: %s\n" +
                "Customer Number: %s\n" +
                "Freeze Reason: %s\n" +
                "Severity: %s\n" +
                "Compliance Violation: %s\n" +
                "Sanctions Match: %s\n" +
                "AML Rule Violated: %s\n" +
                "Total Balance: %s\n" +
                "Risk Score: %s\n" +
                "Case ID: %s\n" +
                "Freeze ID: %s\n" +
                "Effective From: %s",
                event.getUserId(),
                event.getAccountId(),
                event.getCustomerNumber(),
                event.getFreezeReason(),
                event.getSeverity(),
                event.getComplianceViolation(),
                event.getSanctionsListMatch(),
                event.getAmlRuleViolated(),
                event.getTotalAccountBalance(),
                event.getRiskScore(),
                event.getCaseId(),
                freezeId,
                event.getEffectiveFrom()
            );
            
            // Send to regulatory API
            notificationServiceClient.sendRegulatoryNotification(
                "ACCOUNT_FREEZE_REPORT",
                "Regulatory Compliance - Account Freeze",
                regulatoryMessage,
                event.getCaseId()
            );
            
            // Audit the regulatory notification
            auditService.auditCriticalSecurityEvent(
                "REGULATORS_NOTIFIED",
                event.getUserId().toString(),
                "Regulators notified of account freeze",
                Map.of(
                    "freezeId", freezeId,
                    "freezeReason", event.getFreezeReason().toString(),
                    "severity", event.getSeverity().toString(),
                    "notifiedAt", LocalDateTime.now()
                )
            );
            
            log.error("REGULATORY: Notification sent for freeze ID: {}", freezeId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to notify regulators for freeze {}", freezeId, e);
            
            // Audit the failure - regulatory notification failure is critical
            auditService.auditCriticalSecurityEvent(
                "REGULATORY_NOTIFICATION_FAILED",
                event.getUserId().toString(),
                "CRITICAL: Failed to notify regulators: " + e.getMessage(),
                Map.of(
                    "freezeId", freezeId,
                    "error", e.getMessage()
                )
            );
        }
    }
    
    @Override
    public void notifyComplianceTeam(AccountFreezeRequestEvent event, String freezeId) {
        log.warn("COMPLIANCE: Notifying compliance team about account freeze - User: {}", event.getUserId());
        
        try {
            String complianceMessage = String.format(
                "COMPLIANCE TEAM ALERT\n" +
                "Account Freeze Notification\n\n" +
                "User ID: %s\n" +
                "Freeze Reason: %s\n" +
                "Severity: %s\n" +
                "Freeze Scope: %s\n" +
                "Case ID: %s\n" +
                "Freeze ID: %s\n" +
                "Investigation ID: %s\n" +
                "Review Date: %s\n" +
                "Requesting Officer: %s\n" +
                "Requires Manual Review: %s",
                event.getUserId(),
                event.getFreezeReason(),
                event.getSeverity(),
                event.getFreezeScope(),
                event.getCaseId(),
                freezeId,
                event.getInvestigationId(),
                event.getReviewDate(),
                event.getRequestingOfficerId(),
                event.getFreezeScope() != AccountFreezeRequestEvent.FreezeScope.FULL_FREEZE
            );
            
            notificationServiceClient.sendComplianceTeamAlert(
                "ACCOUNT_FREEZE",
                "Account Freeze - Compliance Review Required",
                complianceMessage,
                event.getCaseId()
            );
            
            log.warn("COMPLIANCE: Team notified about freeze ID: {}", freezeId);
            
        } catch (Exception e) {
            log.error("Failed to notify compliance team for freeze {}", freezeId, e);
        }
    }
    
    @Override
    public void notifyRegulatoryBody(String regulatoryBody, UUID userId, 
                                    AccountFreezeRequestEvent.FreezeReason reason, String caseId) {
        log.warn("REGULATORY: Notifying {} about account freeze for user {}", regulatoryBody, userId);
        
        try {
            String message = String.format(
                "REGULATORY NOTIFICATION TO: %s\n" +
                "User ID: %s\n" +
                "Freeze Reason: %s\n" +
                "Case ID: %s\n" +
                "Notification Date: %s",
                regulatoryBody,
                userId,
                reason,
                caseId,
                LocalDateTime.now()
            );
            
            // Send to specific regulatory body
            complianceServiceClient.notifyRegulatoryBody(regulatoryBody, message, caseId);
            
            // Audit the notification
            auditService.auditCriticalSecurityEvent(
                "REGULATORY_BODY_NOTIFIED",
                userId.toString(),
                String.format("Notified %s about account freeze", regulatoryBody),
                Map.of(
                    "regulatoryBody", regulatoryBody,
                    "reason", reason.toString(),
                    "caseId", caseId,
                    "notifiedAt", LocalDateTime.now()
                )
            );
            
            log.warn("REGULATORY: {} notified about case {}", regulatoryBody, caseId);
            
        } catch (Exception e) {
            log.error("Failed to notify {} for user {}", regulatoryBody, userId, e);
        }
    }
    
    @Override
    public void fileSuspiciousActivityReport(UUID userId, String reason, String activityPattern,
                                            BigDecimal accountBalance, String caseId) {
        log.error("SAR: Filing Suspicious Activity Report for user {} - {}", userId, reason);
        
        try {
            String sarReport = String.format(
                "SUSPICIOUS ACTIVITY REPORT (SAR)\n" +
                "==========================================\n" +
                "Filing Institution: Waqiti Financial Services\n" +
                "Filing Date: %s\n\n" +
                "SUBJECT INFORMATION:\n" +
                "User ID: %s\n" +
                "Account Balance: %s\n\n" +
                "SUSPICIOUS ACTIVITY:\n" +
                "Reason: %s\n" +
                "Pattern Detected: %s\n" +
                "Case ID: %s\n\n" +
                "NARRATIVE:\n" +
                "The subject's account has been frozen due to suspicious activity patterns " +
                "that may indicate money laundering, terrorist financing, or other financial crimes. " +
                "The activity pattern '%s' was detected through our automated monitoring systems.\n\n" +
                "ACTION TAKEN:\n" +
                "- Account frozen immediately\n" +
                "- All pending transactions cancelled\n" +
                "- Enhanced monitoring activated\n" +
                "- Law enforcement notified where required\n\n" +
                "This report is filed in accordance with regulatory requirements.",
                LocalDateTime.now(),
                userId,
                accountBalance,
                reason,
                activityPattern != null ? activityPattern : "Multiple suspicious indicators",
                caseId,
                activityPattern != null ? activityPattern : "Various suspicious patterns"
            );
            
            // File SAR through compliance service
            String sarId = complianceServiceClient.fileSuspiciousActivityReport(
                userId,
                sarReport,
                caseId,
                reason
            );
            
            // Audit the SAR filing
            auditService.auditCriticalSecurityEvent(
                "SAR_FILED",
                userId.toString(),
                "Suspicious Activity Report filed",
                Map.of(
                    "sarId", sarId,
                    "reason", reason,
                    "caseId", caseId,
                    "accountBalance", accountBalance,
                    "filedAt", LocalDateTime.now()
                )
            );
            
            log.error("SAR: Report filed successfully. SAR ID: {}", sarId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to file SAR for user {}", userId, e);
            
            // SAR filing failure is extremely critical
            auditService.auditCriticalSecurityEvent(
                "SAR_FILING_FAILED",
                userId.toString(),
                "CRITICAL: Failed to file Suspicious Activity Report: " + e.getMessage(),
                Map.of(
                    "reason", reason,
                    "caseId", caseId,
                    "error", e.getMessage()
                )
            );
        }
    }
}