package com.waqiti.wallet.service.impl;

import com.waqiti.wallet.service.ComplianceNotificationService;
import com.waqiti.common.events.TransactionBlockEvent;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Compliance Notification Service Implementation
 * 
 * CRITICAL COMPLIANCE IMPACT: Ensures proper notification for regulatory compliance
 * 
 * This service handles critical compliance notifications:
 * - Executive alerts for critical violations
 * - Compliance team notifications
 * - Regulatory authority notifications
 * - Manual review notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceNotificationServiceImpl implements ComplianceNotificationService {
    
    private final NotificationServiceClient notificationServiceClient;
    private final AuditService auditService;
    
    @Override
    public void sendExecutiveAlert(TransactionBlockEvent event) {
        log.error("CRITICAL COMPLIANCE: Sending executive alert for transaction block - {} for transaction {}", 
            event.getBlockReason(), event.getTransactionId());
        
        try {
            String alertMessage = String.format(
                "CRITICAL COMPLIANCE ALERT: Transaction %s blocked due to %s\n" +
                "User: %s\n" +
                "Amount: %s %s\n" +
                "Severity: %s\n" +
                "Violations: %s\n" +
                "Sanctions Match: %s\n" +
                "Requires Regulatory Notification: %s",
                event.getTransactionId(),
                event.getBlockReason(),
                event.getUserId(),
                event.getTransactionAmount(),
                event.getCurrency(),
                event.getSeverity(),
                event.getComplianceViolations(),
                event.getSanctionsListMatch(),
                event.isNotifyRegulators()
            );
            
            // Send to executive team
            notificationServiceClient.sendExecutiveAlert(
                "CRITICAL_COMPLIANCE_VIOLATION",
                "Critical Compliance Alert - Transaction Blocked",
                alertMessage,
                event.getTransactionId().toString()
            );
            
            // Audit the executive alert
            auditService.auditComplianceEvent(
                "EXECUTIVE_ALERT_SENT",
                event.getTransactionId().toString(),
                "Executive alert sent for critical compliance violation",
                Map.of(
                    "blockReason", event.getBlockReason(),
                    "severity", event.getSeverity(),
                    "notifiedAt", LocalDateTime.now()
                )
            );
            
            log.error("COMPLIANCE: Executive alert sent for transaction {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send executive alert for transaction {}", event.getTransactionId(), e);
        }
    }
    
    @Override
    public void sendComplianceTeamAlert(TransactionBlockEvent event) {
        log.warn("COMPLIANCE: Sending compliance team alert for transaction block - {} for transaction {}", 
            event.getBlockReason(), event.getTransactionId());
        
        try {
            String alertMessage = String.format(
                "COMPLIANCE ALERT: Transaction %s blocked\n" +
                "Reason: %s\n" +
                "User: %s\n" +
                "Amount: %s %s\n" +
                "Severity: %s\n" +
                "Case ID: %s\n" +
                "Compliance Officer: %s\n" +
                "Requires Manual Review: %s",
                event.getTransactionId(),
                event.getBlockReason(),
                event.getUserId(),
                event.getTransactionAmount(),
                event.getCurrency(),
                event.getSeverity(),
                event.getCaseId(),
                event.getComplianceOfficerId(),
                event.isRequiresManualReview()
            );
            
            // Send to compliance team
            notificationServiceClient.sendComplianceTeamAlert(
                "COMPLIANCE_VIOLATION",
                "Compliance Alert - Transaction Blocked",
                alertMessage,
                event.getCaseId()
            );
            
            // Audit the compliance alert
            auditService.auditComplianceEvent(
                "COMPLIANCE_TEAM_ALERT_SENT",
                event.getTransactionId().toString(),
                "Compliance team alert sent for transaction block",
                Map.of(
                    "blockReason", event.getBlockReason(),
                    "caseId", event.getCaseId(),
                    "notifiedAt", LocalDateTime.now()
                )
            );
            
            log.warn("COMPLIANCE: Compliance team alert sent for transaction {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to send compliance team alert for transaction {}", event.getTransactionId(), e);
        }
    }
    
    @Override
    public void sendComplianceReviewNotification(TransactionBlockEvent event) {
        log.info("COMPLIANCE: Sending compliance review notification for transaction {}", event.getTransactionId());
        
        try {
            String reviewMessage = String.format(
                "COMPLIANCE REVIEW REQUIRED: Transaction %s requires manual review\n" +
                "Reason: %s\n" +
                "Description: %s\n" +
                "User: %s\n" +
                "Amount: %s %s\n" +
                "Case ID: %s\n" +
                "Assigned Officer: %s\n" +
                "Risk Score: %s",
                event.getTransactionId(),
                event.getBlockReason(),
                event.getBlockDescription(),
                event.getUserId(),
                event.getTransactionAmount(),
                event.getCurrency(),
                event.getCaseId(),
                event.getComplianceOfficerId(),
                event.getRiskScore()
            );
            
            // Send review notification
            notificationServiceClient.sendComplianceReviewNotification(
                event.getComplianceOfficerId(),
                "Compliance Review Required",
                reviewMessage,
                event.getCaseId()
            );
            
            // Audit the review notification
            auditService.auditComplianceEvent(
                "COMPLIANCE_REVIEW_NOTIFICATION_SENT",
                event.getTransactionId().toString(),
                "Compliance review notification sent",
                Map.of(
                    "caseId", event.getCaseId(),
                    "complianceOfficerId", event.getComplianceOfficerId(),
                    "notifiedAt", LocalDateTime.now()
                )
            );
            
            log.info("COMPLIANCE: Review notification sent for transaction {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to send compliance review notification for transaction {}", event.getTransactionId(), e);
        }
    }
    
    @Override
    public void sendRegulatoryNotification(TransactionBlockEvent event, String blockId) {
        log.error("REGULATORY: Sending regulatory notification for transaction {} - {}", 
            event.getTransactionId(), event.getBlockReason());
        
        try {
            String regulatoryMessage = String.format(
                "REGULATORY NOTIFICATION: Suspicious Activity Report\n" +
                "Transaction ID: %s\n" +
                "Block ID: %s\n" +
                "Block Reason: %s\n" +
                "User ID: %s\n" +
                "Transaction Amount: %s %s\n" +
                "Transaction Date: %s\n" +
                "Block Date: %s\n" +
                "Sanctions Match: %s\n" +
                "AML Rule Violated: %s\n" +
                "Risk Score: %s\n" +
                "Case ID: %s\n" +
                "Blocking System: %s",
                event.getTransactionId(),
                blockId,
                event.getBlockReason(),
                event.getUserId(),
                event.getTransactionAmount(),
                event.getCurrency(),
                event.getTransactionInitiatedAt(),
                event.getBlockedAt(),
                event.getSanctionsListMatch(),
                event.getAmlRuleViolated(),
                event.getRiskScore(),
                event.getCaseId(),
                event.getBlockingSystem()
            );
            
            // Send regulatory notification
            notificationServiceClient.sendRegulatoryNotification(
                "SUSPICIOUS_ACTIVITY_REPORT",
                "SAR - Transaction Blocked for Compliance Violation",
                regulatoryMessage,
                event.getCaseId()
            );
            
            // Audit the regulatory notification
            auditService.auditComplianceEvent(
                "REGULATORY_NOTIFICATION_SENT",
                event.getTransactionId().toString(),
                "Regulatory notification sent for suspicious activity",
                Map.of(
                    "blockId", blockId,
                    "blockReason", event.getBlockReason(),
                    "caseId", event.getCaseId(),
                    "sanctionsMatch", event.getSanctionsListMatch(),
                    "notifiedAt", LocalDateTime.now()
                )
            );
            
            log.error("REGULATORY: Regulatory notification sent for transaction {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to send regulatory notification for transaction {}", event.getTransactionId(), e);
            
            // Regulatory notification failure is critical - audit it
            auditService.auditComplianceEvent(
                "REGULATORY_NOTIFICATION_FAILED",
                event.getTransactionId().toString(),
                "CRITICAL: Failed to send regulatory notification: " + e.getMessage(),
                Map.of(
                    "blockId", blockId,
                    "error", e.getMessage(),
                    "failedAt", LocalDateTime.now()
                )
            );
        }
    }
}