package com.waqiti.wallet.service;

import com.waqiti.common.audit.annotation.AuditLogged;
import com.waqiti.common.audit.domain.AuditLog.EventCategory;
import com.waqiti.common.audit.domain.AuditLog.Severity;
import com.waqiti.wallet.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Audited Wallet Service with comprehensive audit logging
 * 
 * This service wraps wallet operations with comprehensive audit logging
 * for compliance with SOX, PCI DSS, GDPR, and SOC 2 requirements.
 * 
 * AUDIT COVERAGE:
 * - Wallet creation and management
 * - Balance updates and transfers
 * - Wallet limits and restrictions
 * - Wallet freezing and blocking
 * - Multi-currency operations
 * - Cross-border transactions
 * - Suspicious activity detection
 * 
 * COMPLIANCE MAPPING:
 * - PCI DSS: Wallet transaction security
 * - SOX: Financial balance audit trails
 * - GDPR: Customer wallet data access
 * - SOC 2: Operational wallet controls
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditedWalletService {
    
    private final WalletService walletService;
    
    /**
     * Create wallet with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_CREATED",
        category = EventCategory.FINANCIAL,
        severity = Severity.MEDIUM,
        description = "Wallet created for user #{userId} with currency #{currency}",
        entityType = "Wallet",
        entityIdExpression = "#result.walletId",
        soxRelevant = true,
        gdprRelevant = true,
        metadata = {
            "userId: #userId",
            "currency: #currency",
            "walletType: #walletType",
            "initialBalance: #result.balance"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = false
    )
    @Transactional
    public WalletResponse createWallet(UUID userId, String currency, String walletType) {
        log.info("AUDIT: Creating wallet for user: {} currency: {} type: {}", userId, currency, walletType);
        
        return walletService.createWallet(userId, currency, walletType);
    }
    
    /**
     * Credit wallet with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_CREDITED",
        category = EventCategory.FINANCIAL,
        severity = Severity.HIGH,
        description = "Wallet credited for user #{userId} amount #{amount} #{currency}",
        entityType = "WalletTransaction",
        entityIdExpression = "#result.transactionId",
        pciRelevant = true,
        soxRelevant = true,
        metadata = {
            "userId: #userId",
            "walletId: #walletId",
            "amount: #amount",
            "currency: #currency",
            "transactionType: CREDIT",
            "newBalance: #result.newBalance",
            "reference: #reference"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public WalletTransactionResponse creditWallet(UUID userId, UUID walletId, BigDecimal amount, 
                                                 String currency, String reference) {
        log.info("AUDIT: Crediting wallet: {} for user: {} amount: {} {} ref: {}", 
                walletId, userId, amount, currency, reference);
        
        return walletService.creditWallet(userId, walletId, amount, currency, reference);
    }
    
    /**
     * Debit wallet with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_DEBITED",
        category = EventCategory.FINANCIAL,
        severity = Severity.HIGH,
        description = "Wallet debited for user #{userId} amount #{amount} #{currency}",
        entityType = "WalletTransaction",
        entityIdExpression = "#result.transactionId",
        pciRelevant = true,
        soxRelevant = true,
        metadata = {
            "userId: #userId",
            "walletId: #walletId",
            "amount: #amount",
            "currency: #currency",
            "transactionType: DEBIT",
            "newBalance: #result.newBalance",
            "reference: #reference"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public WalletTransactionResponse debitWallet(UUID userId, UUID walletId, BigDecimal amount, 
                                                String currency, String reference) {
        log.info("AUDIT: Debiting wallet: {} for user: {} amount: {} {} ref: {}", 
                walletId, userId, amount, currency, reference);
        
        return walletService.debitWallet(userId, walletId, amount, currency, reference);
    }
    
    /**
     * Transfer between wallets with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_TRANSFER",
        category = EventCategory.FINANCIAL,
        severity = Severity.HIGH,
        description = "Wallet transfer from #{fromWalletId} to #{toWalletId} amount #{amount} #{currency}",
        entityType = "WalletTransfer",
        entityIdExpression = "#result.transferId",
        pciRelevant = true,
        soxRelevant = true,
        riskScore = 40,
        metadata = {
            "fromUserId: #fromUserId",
            "toUserId: #toUserId",
            "fromWalletId: #fromWalletId",
            "toWalletId: #toWalletId",
            "amount: #amount",
            "currency: #currency",
            "reference: #reference"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    @Transactional
    public WalletTransferResponse transferBetweenWallets(UUID fromUserId, UUID toUserId, 
                                                        UUID fromWalletId, UUID toWalletId,
                                                        BigDecimal amount, String currency, String reference) {
        log.info("AUDIT: Wallet transfer from: {} to: {} amount: {} {} ref: {}", 
                fromWalletId, toWalletId, amount, currency, reference);
        
        return walletService.transferBetweenWallets(fromUserId, toUserId, fromWalletId, toWalletId, 
                                                   amount, currency, reference);
    }
    
    /**
     * Freeze wallet with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_FROZEN",
        category = EventCategory.SECURITY,
        severity = Severity.CRITICAL,
        description = "Wallet frozen for user #{userId} - reason: #{reason}",
        entityType = "WalletSecurity",
        entityIdExpression = "#walletId",
        pciRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 90,
        metadata = {
            "userId: #userId",
            "walletId: #walletId",
            "reason: #reason",
            "freezeType: #freezeType",
            "adminUserId: #currentUser"
        },
        captureParameters = true,
        sendToSiem = true
    )
    @Transactional
    public void freezeWallet(UUID userId, UUID walletId, String reason, String freezeType) {
        log.warn("AUDIT: Freezing wallet: {} for user: {} reason: {} type: {}", 
                walletId, userId, reason, freezeType);
        
        walletService.freezeWallet(userId, walletId, reason, freezeType);
    }
    
    /**
     * Unfreeze wallet with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_UNFROZEN",
        category = EventCategory.SECURITY,
        severity = Severity.HIGH,
        description = "Wallet unfrozen for user #{userId} - reason: #{reason}",
        entityType = "WalletSecurity",
        entityIdExpression = "#walletId",
        pciRelevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "walletId: #walletId",
            "reason: #reason",
            "adminUserId: #currentUser",
            "approvalRequired: true"
        },
        captureParameters = true,
        sendToSiem = true
    )
    @Transactional
    public void unfreezeWallet(UUID userId, UUID walletId, String reason) {
        log.info("AUDIT: Unfreezing wallet: {} for user: {} reason: {}", walletId, userId, reason);
        
        walletService.unfreezeWallet(userId, walletId, reason);
    }
    
    /**
     * Update wallet limits with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_LIMITS_UPDATED",
        category = EventCategory.CONFIGURATION,
        severity = Severity.MEDIUM,
        description = "Wallet limits updated for user #{userId}",
        entityType = "WalletLimits",
        entityIdExpression = "#walletId",
        soxRelevant = true,
        metadata = {
            "userId: #userId",
            "walletId: #walletId",
            "oldDailyLimit: #oldLimits.dailyLimit",
            "newDailyLimit: #newLimits.dailyLimit",
            "oldMonthlyLimit: #oldLimits.monthlyLimit",
            "newMonthlyLimit: #newLimits.monthlyLimit",
            "adminUserId: #currentUser"
        },
        captureParameters = true,
        sendToSiem = false
    )
    @Transactional
    public void updateWalletLimits(UUID userId, UUID walletId, WalletLimits oldLimits, WalletLimits newLimits) {
        log.info("AUDIT: Updating wallet limits for user: {} wallet: {}", userId, walletId);
        
        walletService.updateWalletLimits(userId, walletId, oldLimits, newLimits);
    }
    
    /**
     * Access wallet balance with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_BALANCE_ACCESSED",
        category = EventCategory.DATA_ACCESS,
        severity = Severity.MEDIUM,
        description = "Wallet balance accessed for user #{userId}",
        entityType = "WalletBalance",
        entityIdExpression = "#walletId",
        gdprRelevant = true,
        metadata = {
            "userId: #userId",
            "walletId: #walletId",
            "accessReason: #accessReason",
            "balanceAmount: #result.balance",
            "currency: #result.currency"
        },
        captureParameters = true,
        captureReturnValue = true,
        excludeFields = {"sensitiveBalance"},
        sendToSiem = false
    )
    public WalletBalanceResponse getWalletBalance(UUID userId, UUID walletId, String accessReason) {
        log.debug("AUDIT: Accessing wallet balance for user: {} wallet: {} reason: {}", 
                 userId, walletId, accessReason);
        
        return walletService.getWalletBalance(userId, walletId, accessReason);
    }
    
    /**
     * Process large transaction with enhanced audit logging
     */
    @AuditLogged(
        eventType = "LARGE_WALLET_TRANSACTION",
        category = EventCategory.FINANCIAL,
        severity = Severity.CRITICAL,
        description = "Large wallet transaction processed - amount #{amount} #{currency} for user #{userId}",
        entityType = "LargeWalletTransaction",
        entityIdExpression = "#result.transactionId",
        pciRelevant = true,
        soxRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 85,
        metadata = {
            "userId: #userId",
            "walletId: #walletId",
            "amount: #amount",
            "currency: #currency",
            "transactionType: #transactionType",
            "thresholdExceeded: true",
            "complianceReview: true"
        },
        captureParameters = true,
        captureReturnValue = true,
        captureExecutionTime = true,
        sendToSiem = true
    )
    @Transactional
    public WalletTransactionResponse processLargeTransaction(UUID userId, UUID walletId, BigDecimal amount, 
                                                           String currency, String transactionType) {
        log.warn("AUDIT: Processing large wallet transaction - User: {} Wallet: {} Amount: {} {}", 
                userId, walletId, amount, currency);
        
        return walletService.processLargeTransaction(userId, walletId, amount, currency, transactionType);
    }
    
    /**
     * Flag suspicious wallet activity
     */
    @AuditLogged(
        eventType = "SUSPICIOUS_WALLET_ACTIVITY",
        category = EventCategory.FRAUD,
        severity = Severity.CRITICAL,
        description = "Suspicious wallet activity detected for user #{userId} - type: #{activityType}",
        entityType = "SuspiciousActivity",
        entityIdExpression = "#walletId",
        pciRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 95,
        metadata = {
            "userId: #userId",
            "walletId: #walletId",
            "activityType: #activityType",
            "confidence: #confidence",
            "indicators: #indicators",
            "immediateAction: #immediateAction"
        },
        captureParameters = true,
        sendToSiem = true
    )
    public void flagSuspiciousWalletActivity(UUID userId, UUID walletId, String activityType, 
                                           double confidence, String indicators, String immediateAction) {
        log.error("AUDIT: SUSPICIOUS WALLET ACTIVITY - User: {} Wallet: {} Type: {} Confidence: {}", 
                 userId, walletId, activityType, confidence);
        
        walletService.flagSuspiciousWalletActivity(userId, walletId, activityType, confidence, indicators, immediateAction);
    }
    
    /**
     * Export wallet data with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_DATA_EXPORTED",
        category = EventCategory.DATA_ACCESS,
        severity = Severity.HIGH,
        description = "Wallet data exported for user #{userId}",
        entityType = "WalletDataExport",
        gdprRelevant = true,
        soxRelevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "exportFormat: #exportFormat",
            "dateRange: #dateRange",
            "recordCount: #result.recordCount",
            "exportReason: #exportReason"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    public WalletDataExportResponse exportWalletData(UUID userId, String exportFormat, String dateRange, String exportReason) {
        log.info("AUDIT: Exporting wallet data for user: {} format: {} range: {}", userId, exportFormat, dateRange);
        
        return walletService.exportWalletData(userId, exportFormat, dateRange, exportReason);
    }
    
    /**
     * Process cross-border wallet transfer
     */
    @AuditLogged(
        eventType = "CROSS_BORDER_WALLET_TRANSFER",
        category = EventCategory.FINANCIAL,
        severity = Severity.CRITICAL,
        description = "Cross-border wallet transfer from #{fromCountry} to #{toCountry} amount #{amount} #{currency}",
        entityType = "CrossBorderTransfer",
        entityIdExpression = "#result.transferId",
        pciRelevant = true,
        soxRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 90,
        metadata = {
            "fromUserId: #fromUserId",
            "toUserId: #toUserId",
            "fromCountry: #fromCountry",
            "toCountry: #toCountry",
            "amount: #amount",
            "currency: #currency",
            "complianceCheck: #result.complianceStatus"
        },
        captureParameters = true,
        captureReturnValue = true,
        captureExecutionTime = true,
        sendToSiem = true
    )
    @Transactional
    public CrossBorderTransferResponse processCrossBorderTransfer(UUID fromUserId, UUID toUserId, 
                                                                 String fromCountry, String toCountry,
                                                                 BigDecimal amount, String currency) {
        log.warn("AUDIT: Processing cross-border wallet transfer - From: {} To: {} Amount: {} {}", 
                fromCountry, toCountry, amount, currency);
        
        return walletService.processCrossBorderTransfer(fromUserId, toUserId, fromCountry, toCountry, amount, currency);
    }
    
    /**
     * Close wallet with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_CLOSED",
        category = EventCategory.FINANCIAL,
        severity = Severity.HIGH,
        description = "Wallet closed for user #{userId} - reason: #{reason}",
        entityType = "WalletClosure",
        entityIdExpression = "#walletId",
        gdprRelevant = true,
        soxRelevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "walletId: #walletId",
            "reason: #reason",
            "finalBalance: #finalBalance",
            "currency: #currency",
            "disposalMethod: #disposalMethod"
        },
        captureParameters = true,
        sendToSiem = true
    )
    @Transactional
    public void closeWallet(UUID userId, UUID walletId, String reason, BigDecimal finalBalance, 
                           String currency, String disposalMethod) {
        log.info("AUDIT: Closing wallet: {} for user: {} reason: {} final balance: {}", 
                walletId, userId, reason, finalBalance);
        
        walletService.closeWallet(userId, walletId, reason, finalBalance, currency, disposalMethod);
    }
    
    /**
     * Recover wallet with audit logging
     */
    @AuditLogged(
        eventType = "WALLET_RECOVERED",
        category = EventCategory.SECURITY,
        severity = Severity.HIGH,
        description = "Wallet recovery initiated for user #{userId}",
        entityType = "WalletRecovery",
        entityIdExpression = "#walletId",
        pciRelevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 75,
        metadata = {
            "userId: #userId",
            "walletId: #walletId",
            "recoveryMethod: #recoveryMethod",
            "verificationLevel: #verificationLevel",
            "adminApproval: #adminApproval"
        },
        captureParameters = true,
        sendToSiem = true
    )
    @Transactional
    public WalletRecoveryResponse recoverWallet(UUID userId, UUID walletId, String recoveryMethod, 
                                              String verificationLevel, boolean adminApproval) {
        log.warn("AUDIT: Wallet recovery initiated - User: {} Wallet: {} Method: {}", 
                userId, walletId, recoveryMethod);
        
        return walletService.recoverWallet(userId, walletId, recoveryMethod, verificationLevel, adminApproval);
    }
}