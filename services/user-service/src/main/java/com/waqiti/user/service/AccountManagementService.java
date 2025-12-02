package com.waqiti.user.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Account Management Service Interface
 * Handles all account lifecycle operations
 */
public interface AccountManagementService {

    // Status Management
    void updateAccountStatus(String accountId, String userId, String newStatus,
                            String changeReason, String changedBy, LocalDateTime effectiveDate);
    Map<String, Boolean> getStatusPermissions(String status, String accountType);
    void updateFeatureAccess(String accountId, String newStatus);
    void trackStatusChangeMetrics(String previousStatus, String newStatus, String changeReason);

    // Account Activation
    void enableAllFeatures(String accountId);
    void setInitialLimits(String accountId, String accountType, String accountTier);

    // Account Suspension
    void disableTransactionalFeatures(String accountId);
    void freezePendingTransactions(String accountId);
    void applyRestrictions(String accountId, Map<String, Object> restrictions);
    void scheduleAccountReview(String accountId, LocalDateTime reviewDate, String reviewType);

    // Account Reactivation
    void reEnableFeatures(String accountId);
    void processPendingTransactions(String accountId);
    void clearRestrictions(String accountId);

    // Account Closure
    void processFinalSettlement(String accountId, BigDecimal finalBalance, String settlementMethod);
    void archiveAccountData(String accountId, Integer archiveRetentionPeriod);
    void processRefund(String accountId, BigDecimal refundAmount, String refundMethod);
    void generateClosureCertificate(String accountId, String closureReason, LocalDateTime closedAt);

    // Account Freeze
    void freezeAllTransactions(String accountId);
    void blockDebits(String accountId);
    void applyLegalHold(String accountId, String legalHoldReference, LocalDateTime legalHoldExpiry);
    void removeFreeze(String accountId);
    void processQueuedTransactions(String accountId);
    void removeLegalHold(String accountId, String legalHoldReference);
    void restoreNormalOperations(String accountId);

    // Account Restrictions
    void setTransactionLimit(String accountId, String transactionLimit);
    void setWithdrawalLimit(String accountId, String withdrawalLimit);
    void blockFeatures(String accountId, List<String> blockedFeatures);
    void removeAllRestrictions(String accountId);
    void restoreFullLimits(String accountId, String accountType, String accountTier);

    // Account Rejection
    void markAccountRejected(String accountId, String rejectionReason, String rejectionDetails);
    void returnInitialDeposit(String accountId, BigDecimal initialDeposit, String refundMethod);
    void cleanupPendingAccountData(String accountId);

    // Financial Operations
    void adjustBalance(String accountId, BigDecimal balanceAdjustment, String adjustmentReason);
    void processPendingFees(String accountId, BigDecimal pendingFees);
    void calculateFinalInterest(String accountId, LocalDateTime closedAt);

    // Scheduling
    void scheduleStatusReview(String accountId, LocalDateTime reviewDate, String newStatus);
    void scheduleAutoReactivation(String accountId, LocalDateTime autoReactivateDate);
    void scheduleStatusExpiry(String accountId, LocalDateTime statusExpiryDate, String expiryAction);
}
