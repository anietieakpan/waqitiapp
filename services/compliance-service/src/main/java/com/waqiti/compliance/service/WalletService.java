package com.waqiti.compliance.service;

import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.compliance.repository.ComplianceTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet Service
 * 
 * CRITICAL: Manages wallet operations for compliance monitoring and transaction limit enforcement.
 * Provides wallet-level compliance checks and transaction monitoring capabilities.
 * 
 * COMPLIANCE IMPACT:
 * - Supports transaction limit enforcement
 * - Enables wallet-level AML monitoring
 * - Tracks high-risk wallet activities
 * - Maintains compliance audit trails
 * 
 * BUSINESS IMPACT:
 * - Enables secure wallet operations
 * - Supports compliance requirements
 * - Reduces fraud and money laundering risks
 * - Maintains regulatory standing
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WalletService {

    private final ComprehensiveAuditService auditService;
    private final TransactionLimitService transactionLimitService;
    private final EnhancedMonitoringService enhancedMonitoringService;
    private final ComplianceTransactionRepository complianceTransactionRepository;

    /**
     * Validate wallet transaction against compliance limits
     */
    public boolean validateWalletTransaction(UUID userId, String walletId, 
                                           String transactionType, BigDecimal amount,
                                           String currency) {
        
        log.debug("WALLET: Validating transaction for user {} wallet {} type: {} amount: {} {}", 
                userId, walletId, transactionType, amount, currency);
        
        try {
            // Check transaction limits
            if (transactionLimitService.exceedsLimits(userId, transactionType, amount)) {
                log.warn("WALLET: Transaction exceeds limits for user {} wallet {} amount: {}", 
                        userId, walletId, amount);
                
                auditService.auditCriticalComplianceEvent(
                    "WALLET_TRANSACTION_LIMIT_EXCEEDED",
                    userId.toString(),
                    "Wallet transaction exceeds compliance limits",
                    Map.of(
                        "userId", userId,
                        "walletId", walletId,
                        "transactionType", transactionType,
                        "amount", amount,
                        "currency", currency,
                        "validationResult", "REJECTED"
                    )
                );
                
                return false;
            }
            
            // Check for enhanced monitoring requirements
            if (enhancedMonitoringService.hasEnhancedMonitoring(userId)) {
                log.warn("WALLET: User {} under enhanced monitoring - additional checks required", userId);
                
                auditService.auditCriticalComplianceEvent(
                    "WALLET_ENHANCED_MONITORING_CHECK",
                    userId.toString(),
                    "Wallet transaction subject to enhanced monitoring",
                    Map.of(
                        "userId", userId,
                        "walletId", walletId,
                        "transactionType", transactionType,
                        "amount", amount,
                        "monitoringLevel", enhancedMonitoringService.getMonitoringLevel(userId)
                    )
                );
            }
            
            // Audit successful validation
            auditService.auditComplianceEvent(
                "WALLET_TRANSACTION_VALIDATED",
                userId.toString(),
                "Wallet transaction validated successfully",
                Map.of(
                    "userId", userId,
                    "walletId", walletId,
                    "transactionType", transactionType,
                    "amount", amount,
                    "currency", currency,
                    "validationResult", "APPROVED"
                )
            );
            
            log.debug("WALLET: Transaction validation successful for user {} wallet {}", userId, walletId);
            
            return true;
            
        } catch (Exception e) {
            log.error("WALLET: Failed to validate transaction for user {} wallet {}", userId, walletId, e);
            return false;
        }
    }

    /**
     * Block wallet for compliance reasons
     */
    public void blockWallet(UUID userId, String walletId, String reason) {
        log.error("WALLET: BLOCKING wallet {} for user {} reason: {}", walletId, userId, reason);
        
        try {
            // Audit wallet blocking
            auditService.auditCriticalComplianceEvent(
                "WALLET_BLOCKED",
                userId.toString(),
                "Wallet blocked for compliance reasons: " + reason,
                Map.of(
                    "userId", userId,
                    "walletId", walletId,
                    "reason", reason,
                    "blockedAt", LocalDateTime.now(),
                    "action", "WALLET_BLOCKED"
                )
            );
            
            log.error("WALLET: Wallet {} blocked for user {} reason: {}", walletId, userId, reason);
            
        } catch (Exception e) {
            log.error("WALLET: Failed to block wallet {} for user {}", walletId, userId, e);
            throw new RuntimeException("Failed to block wallet", e);
        }
    }

    /**
     * Unblock wallet after compliance review
     */
    public void unblockWallet(UUID userId, String walletId, String reason, String approvedBy) {
        log.warn("WALLET: UNBLOCKING wallet {} for user {} reason: {} approved by: {}", 
                walletId, userId, reason, approvedBy);
        
        try {
            // Audit wallet unblocking
            auditService.auditCriticalComplianceEvent(
                "WALLET_UNBLOCKED",
                userId.toString(),
                "Wallet unblocked after compliance review: " + reason,
                Map.of(
                    "userId", userId,
                    "walletId", walletId,
                    "reason", reason,
                    "approvedBy", approvedBy,
                    "unblockedAt", LocalDateTime.now(),
                    "action", "WALLET_UNBLOCKED"
                )
            );
            
            log.warn("WALLET: Wallet {} unblocked for user {} by {}", walletId, userId, approvedBy);
            
        } catch (Exception e) {
            log.error("WALLET: Failed to unblock wallet {} for user {}", walletId, userId, e);
            throw new RuntimeException("Failed to unblock wallet", e);
        }
    }

    /**
     * Check wallet compliance status
     */
    @Cacheable(value = "complianceChecks", key = "#userId + ':' + #walletId")
    public String getWalletComplianceStatus(UUID userId, String walletId) {
        try {
            // Check if user has enhanced monitoring
            if (enhancedMonitoringService.hasEnhancedMonitoring(userId)) {
                return "ENHANCED_MONITORING";
            }
            
            // Check for recent violations
            if (hasRecentComplianceViolations(userId)) {
                return "COMPLIANCE_REVIEW";
            }
            
            return "COMPLIANT";
            
        } catch (Exception e) {
            log.error("WALLET: Failed to get compliance status for wallet {} user {}", walletId, userId, e);
            return "UNKNOWN";
        }
    }

    /**
     * Record wallet transaction for compliance monitoring
     */
    public void recordWalletTransaction(UUID userId, String walletId, String transactionId,
                                      String transactionType, BigDecimal amount, String currency) {
        
        log.debug("WALLET: Recording transaction {} for user {} wallet {} type: {} amount: {} {}", 
                transactionId, userId, walletId, transactionType, amount, currency);
        
        try {
            // Audit wallet transaction
            auditService.auditComplianceEvent(
                "WALLET_TRANSACTION_RECORDED",
                userId.toString(),
                "Wallet transaction recorded for compliance monitoring",
                Map.of(
                    "userId", userId,
                    "walletId", walletId,
                    "transactionId", transactionId,
                    "transactionType", transactionType,
                    "amount", amount,
                    "currency", currency,
                    "recordedAt", LocalDateTime.now()
                )
            );
            
            log.debug("WALLET: Transaction {} recorded for compliance monitoring", transactionId);
            
        } catch (Exception e) {
            log.error("WALLET: Failed to record transaction {} for wallet {}", transactionId, walletId, e);
        }
    }

    /**
     * Flag wallet for suspicious activity
     */
    public void flagWalletSuspiciousActivity(UUID userId, String walletId, String activityType, 
                                           String description, BigDecimal amount) {
        
        log.warn("WALLET: FLAGGING SUSPICIOUS ACTIVITY - User: {} Wallet: {} Activity: {} Amount: {}", 
                userId, walletId, activityType, amount);
        
        try {
            // Audit suspicious activity
            auditService.auditCriticalComplianceEvent(
                "WALLET_SUSPICIOUS_ACTIVITY",
                userId.toString(),
                "Wallet flagged for suspicious activity: " + activityType,
                Map.of(
                    "userId", userId,
                    "walletId", walletId,
                    "activityType", activityType,
                    "description", description,
                    "amount", amount != null ? amount : BigDecimal.ZERO,
                    "flaggedAt", LocalDateTime.now(),
                    "severity", "HIGH"
                )
            );
            
            // Enable enhanced monitoring
            enhancedMonitoringService.enableEnhancedMonitoring(userId, 
                "Wallet suspicious activity: " + activityType);
            
            log.warn("WALLET: Wallet {} flagged for suspicious activity - enhanced monitoring enabled", walletId);
            
        } catch (Exception e) {
            log.error("WALLET: Failed to flag suspicious activity for wallet {}", walletId, e);
            throw new RuntimeException("Failed to flag wallet suspicious activity", e);
        }
    }

    /**
     * Update wallet limits based on KYC tier
     */
    public void updateWalletLimits(UUID userId, String walletId, String kycTier) {
        log.info("WALLET: Updating limits for user {} wallet {} KYC tier: {}", userId, walletId, kycTier);
        
        try {
            // Get tier-appropriate limits
            Map<String, BigDecimal> limitAmounts = getTierLimits(kycTier);
            
            // Update transaction limits
            transactionLimitService.updateUserLimits(userId, kycTier, limitAmounts, LocalDateTime.now());
            
            // Audit limit update
            auditService.auditComplianceEvent(
                "WALLET_LIMITS_UPDATED",
                userId.toString(),
                "Wallet transaction limits updated for KYC tier: " + kycTier,
                Map.of(
                    "userId", userId,
                    "walletId", walletId,
                    "kycTier", kycTier,
                    "limitAmounts", limitAmounts,
                    "updatedAt", LocalDateTime.now()
                )
            );
            
            log.info("WALLET: Updated limits for wallet {} KYC tier: {}", walletId, kycTier);
            
        } catch (Exception e) {
            log.error("WALLET: Failed to update limits for wallet {} KYC tier: {}", walletId, kycTier, e);
            throw new RuntimeException("Failed to update wallet limits", e);
        }
    }

    // Helper methods

    private boolean hasRecentComplianceViolations(UUID userId) {
        try {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            Long violationCount = complianceTransactionRepository
                .countViolationsByUserIdAndCreatedAtAfter(userId.toString(), thirtyDaysAgo);
            return violationCount != null && violationCount > 0;
        } catch (Exception e) {
            log.error("Failed to check compliance violations for user {}", userId, e);
            return false;
        }
    }

    private Map<String, BigDecimal> getTierLimits(String kycTier) {
        // Implementation would return tier-appropriate limits
        switch (kycTier.toUpperCase()) {
            case "BASIC":
                return Map.of(
                    "DAILY_SEND", BigDecimal.valueOf(1000),
                    "DAILY_RECEIVE", BigDecimal.valueOf(1000),
                    "MONTHLY_SEND", BigDecimal.valueOf(5000),
                    "MONTHLY_RECEIVE", BigDecimal.valueOf(5000)
                );
            case "STANDARD":
                return Map.of(
                    "DAILY_SEND", BigDecimal.valueOf(5000),
                    "DAILY_RECEIVE", BigDecimal.valueOf(5000),
                    "MONTHLY_SEND", BigDecimal.valueOf(25000),
                    "MONTHLY_RECEIVE", BigDecimal.valueOf(25000)
                );
            case "PREMIUM":
                return Map.of(
                    "DAILY_SEND", BigDecimal.valueOf(25000),
                    "DAILY_RECEIVE", BigDecimal.valueOf(25000),
                    "MONTHLY_SEND", BigDecimal.valueOf(100000),
                    "MONTHLY_RECEIVE", BigDecimal.valueOf(100000)
                );
            default:
                return Map.of(
                    "DAILY_SEND", BigDecimal.valueOf(500),
                    "DAILY_RECEIVE", BigDecimal.valueOf(500),
                    "MONTHLY_SEND", BigDecimal.valueOf(2000),
                    "MONTHLY_RECEIVE", BigDecimal.valueOf(2000)
                );
        }
    }
}