package com.waqiti.user.service;

import com.waqiti.common.events.AccountFreezeRequestEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Account Freeze Service Interface
 * 
 * Handles freezing and restriction of user accounts for compliance and security.
 * This service is critical for regulatory compliance and fraud prevention.
 */
public interface AccountFreezeService {
    
    /**
     * Immediately freeze an account with full lockdown
     */
    String freezeAccountImmediately(UUID userId, AccountFreezeRequestEvent.FreezeReason reason, 
                                   String description, AccountFreezeRequestEvent.FreezeScope scope);
    
    /**
     * Freeze account with specified scope
     */
    String freezeAccountWithScope(UUID userId, AccountFreezeRequestEvent.FreezeReason reason,
                                 String description, AccountFreezeRequestEvent.FreezeScope scope);
    
    /**
     * Apply temporary freeze with expiration
     */
    String applyTemporaryFreeze(UUID userId, AccountFreezeRequestEvent.FreezeReason reason,
                               String description, AccountFreezeRequestEvent.FreezeScope scope,
                               LocalDateTime expirationDate);
    
    /**
     * Apply monitoring restrictions only
     */
    String applyMonitoringRestrictions(UUID userId, AccountFreezeRequestEvent.FreezeReason reason,
                                      AccountFreezeRequestEvent.FreezeScope scope);
    
    /**
     * Freeze all wallets for a user
     */
    void freezeAllWallets(UUID userId, List<String> walletIds, String reason);
    
    /**
     * Restrict wallet activity based on scope
     */
    void restrictWalletActivity(UUID userId, List<String> walletIds, 
                               AccountFreezeRequestEvent.FreezeScope scope, LocalDateTime reviewDate);
    
    /**
     * Apply standard restrictions to wallets
     */
    void applyStandardRestrictions(UUID userId, List<String> walletIds,
                                  AccountFreezeRequestEvent.FreezeScope scope);
    
    /**
     * Cancel pending transactions
     */
    void cancelPendingTransactions(UUID userId, List<UUID> transactionIds, String reason);
    
    /**
     * Create urgent compliance case
     */
    void createUrgentComplianceCase(UUID userId, String caseId, String violationType, String investigationId);
    
    /**
     * Create standard compliance case
     */
    void createComplianceCase(UUID userId, String caseId, String violationType, LocalDateTime reviewDate);
    
    /**
     * Schedule compliance review
     */
    void scheduleComplianceReview(UUID userId, String freezeId, LocalDateTime reviewTime);
    
    /**
     * Enable enhanced monitoring
     */
    void enableEnhancedMonitoring(UUID userId, String reason, LocalDateTime untilDate);
    
    /**
     * Send executive alert
     */
    void sendExecutiveAlert(AccountFreezeRequestEvent event, String freezeId);
    
    /**
     * Notify customer about freeze
     */
    void notifyCustomer(UUID userId, String reason, String description);
    
    /**
     * Freeze linked account
     */
    void freezeLinkedAccount(String linkedAccountId, UUID primaryUserId, 
                            AccountFreezeRequestEvent.FreezeReason reason, String caseId);
    
    /**
     * Trigger investigation for related user
     */
    void triggerInvestigation(UUID relatedUserId, UUID primaryUserId, String reason, String investigationId);
}