package com.waqiti.transaction.service;

import com.waqiti.transaction.dto.ReceiptAuditLog;
import com.waqiti.transaction.enums.ReceiptAuditAction;

import java.util.List;
import java.util.UUID;

/**
 * Service for auditing receipt-related operations
 * Provides comprehensive logging, monitoring, and compliance tracking
 */
public interface ReceiptAuditService {

    /**
     * Log receipt generation event
     */
    void logReceiptGenerated(UUID transactionId, UUID receiptId, String userId, 
                           String format, String clientIp, String userAgent);

    /**
     * Log receipt download event
     */
    void logReceiptDownloaded(UUID transactionId, UUID receiptId, String userId, 
                            String clientIp, String userAgent);

    /**
     * Log receipt email event
     */
    void logReceiptEmailed(UUID transactionId, UUID receiptId, String userId, 
                         String recipientEmail, boolean success, String clientIp);

    /**
     * Log receipt verification event
     */
    void logReceiptVerified(UUID transactionId, UUID receiptId, String userId, 
                          boolean verificationPassed, int securityScore, String clientIp);

    /**
     * Log suspicious receipt activity
     */
    void logSuspiciousActivity(UUID transactionId, String userId, String activityType, 
                             String details, String clientIp, String userAgent);

    /**
     * Log receipt access with security token
     */
    void logTokenAccess(UUID transactionId, String token, String userId, 
                       boolean success, String clientIp);

    /**
     * Log receipt deletion/cleanup
     */
    void logReceiptDeleted(UUID transactionId, UUID receiptId, String userId, 
                         String reason, String clientIp);

    /**
     * Get audit trail for a transaction
     */
    List<ReceiptAuditLog> getAuditTrail(UUID transactionId);

    /**
     * Get audit logs for a user
     */
    List<ReceiptAuditLog> getUserAuditLogs(String userId, int limit);

    /**
     * Get recent suspicious activities
     */
    List<ReceiptAuditLog> getRecentSuspiciousActivities(int hours);

    /**
     * Generate compliance report
     */
    byte[] generateComplianceReport(java.time.LocalDateTime startDate, 
                                   java.time.LocalDateTime endDate, 
                                   String reportFormat);

    /**
     * Check for compliance violations
     */
    List<ComplianceViolation> detectComplianceViolations();

    /**
     * Archive old audit logs
     */
    void archiveOldLogs(int retentionDays);

    interface ComplianceViolation {
        String getViolationType();
        String getDescription();
        UUID getTransactionId();
        String getUserId();
        java.time.LocalDateTime getDetectedAt();
        String getSeverity();
    }
}