package com.waqiti.wallet.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transaction Block Service Interface
 * 
 * Handles blocking and restriction of transactions for compliance and fraud prevention.
 * This service is critical for maintaining regulatory compliance.
 */
public interface TransactionBlockService {
    
    /**
     * Immediately block a transaction with full audit trail
     * 
     * @param transactionId Transaction to block
     * @param reason Block reason code
     * @param description Detailed description
     * @param violations List of compliance violations
     * @return Block ID for tracking
     */
    String blockTransactionImmediately(UUID transactionId, String reason, String description, List<String> violations);
    
    /**
     * Block transaction with enhanced monitoring
     * 
     * @param transactionId Transaction to block
     * @param reason Block reason
     * @param description Description
     * @param violations Compliance violations
     * @return Block ID
     */
    String blockTransactionWithMonitoring(UUID transactionId, String reason, String description, List<String> violations);
    
    /**
     * Block transaction with standard compliance tracking
     * 
     * @param transactionId Transaction to block
     * @param reason Block reason
     * @param description Description
     * @return Block ID
     */
    String blockTransaction(UUID transactionId, String reason, String description);
    
    /**
     * Block transaction with automatic review capability
     * 
     * @param transactionId Transaction to block
     * @param reason Block reason
     * @param description Description
     * @return Block ID
     */
    String blockTransactionWithAutoReview(UUID transactionId, String reason, String description);
    
    /**
     * Delay transaction execution until specified time
     * 
     * @param transactionId Transaction to delay
     * @param delayUntil When to allow execution
     * @param reason Delay reason
     * @return Delay ID
     */
    String delayTransaction(UUID transactionId, LocalDateTime delayUntil, String reason);
    
    /**
     * Freeze accounts related to sanctions matches
     * 
     * @param userId Primary user ID
     * @param recipientId Recipient user ID (optional)
     * @param freezeType Type of freeze
     * @param sanctionsMatch Sanctions list match details
     * @return Freeze action ID
     */
    String freezeRelatedAccounts(UUID userId, UUID recipientId, String freezeType, String sanctionsMatch);
    
    /**
     * Block all pending transactions for a user
     * 
     * @param userId User ID
     * @param reason Block reason
     * @return Number of transactions blocked
     */
    int blockAllPendingTransactions(UUID userId, String reason);
    
    /**
     * Apply temporary restrictions to an account
     * 
     * @param userId User ID
     * @param reason Restriction reason
     * @param restrictionUntil When restrictions expire
     * @return Restriction ID
     */
    String applyTemporaryRestrictions(UUID userId, String reason, LocalDateTime restrictionUntil);
    
    /**
     * Flag account for compliance review
     * 
     * @param userId User ID
     * @param caseId Compliance case ID
     * @param reason Review reason
     * @return Review flag ID
     */
    String flagAccountForReview(UUID userId, String caseId, String reason);
    
    /**
     * Create compliance case for transaction
     * 
     * @param userId User ID
     * @param transactionId Transaction ID
     * @param violationType Type of violation
     * @param caseId Case ID
     * @return Case creation ID
     */
    String createComplianceCase(UUID userId, UUID transactionId, String violationType, String caseId);
    
    /**
     * Update compliance tracking systems
     * 
     * @param caseId Case ID
     * @param transactionId Transaction ID
     * @param blockReason Block reason
     * @param severity Severity level
     * @param complianceOfficerId Officer ID
     */
    void updateComplianceTracking(String caseId, UUID transactionId, String blockReason, String severity, String complianceOfficerId);
}