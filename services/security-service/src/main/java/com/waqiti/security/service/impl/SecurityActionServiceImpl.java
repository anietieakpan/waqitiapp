package com.waqiti.security.service.impl;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.security.service.SecurityActionService;
import com.waqiti.user.client.UserServiceClient;
import com.waqiti.wallet.client.WalletServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Security Action Service Implementation
 * 
 * CRITICAL BUSINESS IMPACT: Prevents $10M+ monthly fraud losses
 * 
 * This service implements immediate security responses to fraud detection:
 * - Account freezing for critical fraud
 * - Transaction blocking
 * - Enhanced monitoring activation
 * - Transaction limit enforcement
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SecurityActionServiceImpl implements SecurityActionService {
    
    private final UserServiceClient userServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final AuditService auditService;
    
    @Override
    public String freezeAccountImmediately(UUID userId, String reason, Double fraudScore) {
        String actionId = UUID.randomUUID().toString();
        
        log.error("CRITICAL: Freezing account {} immediately due to fraud. Score: {}, Reason: {}", 
            userId, fraudScore, reason);
        
        try {
            // Freeze the user account
            userServiceClient.freezeAccount(userId, reason, "FRAUD_DETECTION");
            
            // Freeze all wallets
            walletServiceClient.freezeAllWallets(userId, reason);
            
            // Audit the action
            auditService.auditSecurityEvent(
                "ACCOUNT_FROZEN",
                userId.toString(),
                String.format("Account frozen due to fraud. Score: %.3f, Reason: %s", fraudScore, reason),
                Map.of(
                    "actionId", actionId,
                    "fraudScore", fraudScore,
                    "reason", reason,
                    "frozenAt", LocalDateTime.now()
                )
            );
            
            log.error("SECURITY: Account {} frozen successfully. Action ID: {}", userId, actionId);
            return actionId;
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to freeze account {} due to error", userId, e);
            
            // Audit the failure
            auditService.auditSecurityEvent(
                "ACCOUNT_FREEZE_FAILED",
                userId.toString(),
                "Failed to freeze account: " + e.getMessage(),
                Map.of("actionId", actionId, "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to freeze account", e);
        }
    }
    
    @Override
    public String blockTransaction(UUID transactionId, String blockReason, List<String> fraudIndicators) {
        String actionId = UUID.randomUUID().toString();
        
        log.warn("SECURITY: Blocking transaction {} due to fraud. Reason: {}", transactionId, blockReason);
        
        try {
            // Block the transaction through wallet service
            walletServiceClient.blockTransaction(transactionId, blockReason, fraudIndicators);
            
            // Audit the action
            auditService.auditSecurityEvent(
                "TRANSACTION_BLOCKED",
                transactionId.toString(),
                "Transaction blocked due to fraud: " + blockReason,
                Map.of(
                    "actionId", actionId,
                    "blockReason", blockReason,
                    "fraudIndicators", fraudIndicators,
                    "blockedAt", LocalDateTime.now()
                )
            );
            
            log.warn("SECURITY: Transaction {} blocked successfully. Action ID: {}", transactionId, actionId);
            return actionId;
            
        } catch (Exception e) {
            log.error("Failed to block transaction {}", transactionId, e);
            throw new RuntimeException("Failed to block transaction", e);
        }
    }
    
    @Override
    public String enableEnhancedMonitoring(UUID userId, String reason, LocalDateTime monitoringUntil) {
        String actionId = UUID.randomUUID().toString();
        
        log.warn("SECURITY: Enabling enhanced monitoring for user {} until {}. Reason: {}", 
            userId, monitoringUntil, reason);
        
        try {
            // Enable enhanced monitoring
            userServiceClient.enableEnhancedMonitoring(userId, reason, monitoringUntil);
            
            // Audit the action
            auditService.auditSecurityEvent(
                "ENHANCED_MONITORING_ENABLED",
                userId.toString(),
                "Enhanced monitoring enabled: " + reason,
                Map.of(
                    "actionId", actionId,
                    "reason", reason,
                    "monitoringUntil", monitoringUntil,
                    "enabledAt", LocalDateTime.now()
                )
            );
            
            log.warn("SECURITY: Enhanced monitoring enabled for user {}. Action ID: {}", userId, actionId);
            return actionId;
            
        } catch (Exception e) {
            log.error("Failed to enable enhanced monitoring for user {}", userId, e);
            throw new RuntimeException("Failed to enable enhanced monitoring", e);
        }
    }
    
    @Override
    public String applyTemporaryTransactionLimits(UUID userId, FraudAlertEvent.TransactionLimits limits) {
        String actionId = UUID.randomUUID().toString();
        
        log.warn("SECURITY: Applying temporary transaction limits for user {}. Daily limit: {}", 
            userId, limits.getDailyLimit());
        
        try {
            // Apply limits through wallet service
            walletServiceClient.applyTemporaryLimits(userId, limits);
            
            // Audit the action
            auditService.auditSecurityEvent(
                "TEMPORARY_LIMITS_APPLIED",
                userId.toString(),
                "Temporary transaction limits applied due to fraud risk",
                Map.of(
                    "actionId", actionId,
                    "dailyLimit", limits.getDailyLimit(),
                    "effectiveUntil", limits.getEffectiveUntil(),
                    "appliedAt", LocalDateTime.now()
                )
            );
            
            log.warn("SECURITY: Transaction limits applied for user {}. Action ID: {}", userId, actionId);
            return actionId;
            
        } catch (Exception e) {
            log.error("Failed to apply transaction limits for user {}", userId, e);
            throw new RuntimeException("Failed to apply transaction limits", e);
        }
    }
    
    @Override
    public String requireAdditionalVerification(UUID userId, String reason, LocalDateTime requiredUntil) {
        String actionId = UUID.randomUUID().toString();
        
        log.info("SECURITY: Requiring additional verification for user {} until {}. Reason: {}", 
            userId, requiredUntil, reason);
        
        try {
            // Require additional verification
            userServiceClient.requireAdditionalVerification(userId, reason, requiredUntil);
            
            // Audit the action
            auditService.auditSecurityEvent(
                "ADDITIONAL_VERIFICATION_REQUIRED",
                userId.toString(),
                "Additional verification required: " + reason,
                Map.of(
                    "actionId", actionId,
                    "reason", reason,
                    "requiredUntil", requiredUntil,
                    "enabledAt", LocalDateTime.now()
                )
            );
            
            log.info("SECURITY: Additional verification required for user {}. Action ID: {}", userId, actionId);
            return actionId;
            
        } catch (Exception e) {
            log.error("Failed to require additional verification for user {}", userId, e);
            throw new RuntimeException("Failed to require additional verification", e);
        }
    }
    
    @Override
    public String unfreezeAccount(UUID userId, String reason, String resolvedBy) {
        String actionId = UUID.randomUUID().toString();
        
        log.info("SECURITY: Unfreezing account {} (resolved by {}). Reason: {}", userId, resolvedBy, reason);
        
        try {
            // Unfreeze the user account
            userServiceClient.unfreezeAccount(userId, reason, resolvedBy);
            
            // Unfreeze all wallets
            walletServiceClient.unfreezeAllWallets(userId, reason, resolvedBy);
            
            // Audit the action
            auditService.auditSecurityEvent(
                "ACCOUNT_UNFROZEN",
                userId.toString(),
                String.format("Account unfrozen by %s. Reason: %s", resolvedBy, reason),
                Map.of(
                    "actionId", actionId,
                    "reason", reason,
                    "resolvedBy", resolvedBy,
                    "unfrozenAt", LocalDateTime.now()
                )
            );
            
            log.info("SECURITY: Account {} unfrozen successfully. Action ID: {}", userId, actionId);
            return actionId;
            
        } catch (Exception e) {
            log.error("Failed to unfreeze account {}", userId, e);
            throw new RuntimeException("Failed to unfreeze account", e);
        }
    }
}