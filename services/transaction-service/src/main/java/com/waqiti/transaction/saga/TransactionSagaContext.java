package com.waqiti.transaction.saga;

import com.waqiti.transaction.domain.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PRODUCTION-READY Transaction Saga Context
 *
 * Comprehensive context object that carries all transaction data through the saga execution
 *
 * Contains:
 * - Transaction identification
 * - Wallet and user information
 * - Fraud detection results
 * - Compliance screening results
 * - Ledger recording results
 * - Transfer results
 * - Error tracking
 * - Audit trail
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSagaContext {

    // ===== TRANSACTION IDENTIFICATION =====
    private String transactionId;
    private TransactionType transactionType;
    private String description;
    private LocalDateTime startTime;

    // ===== WALLET AND USER INFORMATION =====
    private String userId;
    private String sourceWalletId;
    private String destinationWalletId;
    private BigDecimal amount;
    private String currency;

    // ===== SECURITY CONTEXT =====
    private String ipAddress;
    private String deviceFingerprint;
    private String userAgent;
    private String geolocation;

    // ===== FRAUD DETECTION RESULTS =====
    private Double fraudScore;
    private List<String> fraudReasons;
    private boolean fraudBlocked;
    private boolean fraudCheckBypassed;
    private boolean requiresReview;

    // ===== COMPLIANCE SCREENING RESULTS =====
    private String complianceStatus;
    private String complianceScreeningId;
    private List<String> complianceViolations;
    private boolean complianceBlocked;
    private boolean complianceCheckBypassed;
    private boolean sanctionsHit;
    private boolean pepMatch;
    private boolean enhancedMonitoring;
    private boolean travelRuleApplicable;
    private boolean requiresAdditionalInfo;
    private boolean requiresPostScreening;

    // ===== FUND RESERVATION RESULTS =====
    private String reservationId;
    private LocalDateTime reservationExpiry;

    // ===== TRANSFER RESULTS =====
    private String transferReference;
    private BigDecimal sourceBalanceAfter;
    private BigDecimal destinationBalanceAfter;

    // ===== LEDGER RECORDING RESULTS =====
    private String ledgerEntryId;
    private String debitEntryId;
    private String creditEntryId;
    private boolean ledgerRecordingPending;

    // ===== NOTIFICATION STATUS =====
    private boolean notificationPending;

    // ===== ERROR TRACKING =====
    private String errorMessage;
    private String failureReason;
    private LocalDateTime failedAt;

    // ===== METADATA AND AUDIT =====
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    private String sagaId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Add metadata entry
     */
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Get metadata entry
     */
    public Object getMetadata(String key) {
        if (this.metadata == null) {
            return null;
        }
        return this.metadata.get(key);
    }

    /**
     * Check if transaction requires manual review
     */
    public boolean requiresManualReview() {
        return requiresReview ||
               (fraudScore != null && fraudScore >= 0.75) ||
               pepMatch ||
               enhancedMonitoring;
    }

    /**
     * Check if transaction is high-value
     */
    public boolean isHighValue() {
        if (amount == null) {
            return false;
        }
        return amount.compareTo(new BigDecimal("10000")) >= 0;
    }

    /**
     * Check if compliance screening passed
     */
    public boolean isComplianceApproved() {
        return !complianceBlocked &&
               !sanctionsHit &&
               (complianceStatus == null ||
                complianceStatus.equals("APPROVED") ||
                complianceStatus.equals("CLEARED"));
    }

    /**
     * Check if fraud check passed
     */
    public boolean isFraudApproved() {
        return !fraudBlocked &&
               (fraudScore == null || fraudScore < 0.90);
    }

    /**
     * Get total saga execution time in milliseconds
     */
    public long getExecutionTimeMs() {
        if (startTime == null) {
            return 0;
        }
        LocalDateTime endTime = updatedAt != null ? updatedAt : LocalDateTime.now();
        return java.time.Duration.between(startTime, endTime).toMillis();
    }

    /**
     * Initialize context with current timestamp
     */
    public void initialize() {
        this.createdAt = LocalDateTime.now();
        this.startTime = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update modification timestamp
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark as failed with error message
     */
    public void markFailed(String errorMessage) {
        this.errorMessage = errorMessage;
        this.failureReason = errorMessage;
        this.failedAt = LocalDateTime.now();
        touch();
    }

    /**
     * Check if transaction has any blocking issues
     */
    public boolean hasBlockingIssues() {
        return fraudBlocked || complianceBlocked || sanctionsHit;
    }

    /**
     * Get comprehensive status summary
     */
    public String getStatusSummary() {
        if (hasBlockingIssues()) {
            return "BLOCKED: " +
                   (fraudBlocked ? "Fraud " : "") +
                   (complianceBlocked ? "Compliance " : "") +
                   (sanctionsHit ? "Sanctions " : "");
        }

        if (requiresManualReview()) {
            return "FLAGGED FOR REVIEW";
        }

        return "APPROVED";
    }

    /**
     * Convert to audit log entry
     */
    public Map<String, Object> toAuditLog() {
        Map<String, Object> auditLog = new HashMap<>();
        auditLog.put("transactionId", transactionId);
        auditLog.put("userId", userId);
        auditLog.put("amount", amount != null ? amount.toString() : null);
        auditLog.put("currency", currency);
        auditLog.put("transactionType", transactionType != null ? transactionType.name() : null);
        auditLog.put("fraudScore", fraudScore);
        auditLog.put("complianceStatus", complianceStatus);
        auditLog.put("transferReference", transferReference);
        auditLog.put("ledgerEntryId", ledgerEntryId);
        auditLog.put("statusSummary", getStatusSummary());
        auditLog.put("executionTimeMs", getExecutionTimeMs());
        auditLog.put("timestamp", LocalDateTime.now().toString());
        return auditLog;
    }
}
