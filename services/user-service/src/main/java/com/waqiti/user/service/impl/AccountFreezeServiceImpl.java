package com.waqiti.user.service.impl;

import com.waqiti.user.service.AccountFreezeService;
import com.waqiti.user.domain.User;
import com.waqiti.user.domain.UserStatus;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.common.events.AccountFreezeRequestEvent;
import com.waqiti.common.events.AccountFreezeEvent;
import com.waqiti.common.events.AccountMonitoringEvent;
import com.waqiti.common.events.TokenRevocationEvent;
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.client.WalletServiceClient;
import com.waqiti.common.client.NotificationServiceClient;
import com.waqiti.common.client.ComplianceServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Account Freeze Service Implementation
 * 
 * CRITICAL COMPLIANCE IMPACT: Ensures immediate account restrictions for regulatory compliance
 * 
 * This service implements account freezing for compliance:
 * - Sanctions compliance enforcement
 * - AML violation response
 * - Fraud prevention measures
 * - Court order compliance
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountFreezeServiceImpl implements AccountFreezeService {
    
    private final UserRepository userRepository;
    private final ComprehensiveAuditService auditService;
    private final WalletServiceClient walletServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TaskScheduler taskScheduler;
    private final JwtEncoder jwtEncoder;
    
    @Override
    public String freezeAccountImmediately(UUID userId, AccountFreezeRequestEvent.FreezeReason reason, 
                                          String description, AccountFreezeRequestEvent.FreezeScope scope) {
        String freezeId = UUID.randomUUID().toString();
        
        log.error("CRITICAL COMPLIANCE: Immediately freezing account {} - Reason: {}, Scope: {}", 
            userId, reason, scope);
        
        try {
            // Update account status to FROZEN in database
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            
            user.setStatus(UserStatus.FROZEN);
            user.setFreezeReason(reason.toString());
            user.setFreezeDescription(description);
            user.setFrozenAt(LocalDateTime.now());
            user.setFreezeScope(scope.toString());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            
            // Disable all authentication tokens by revoking refresh tokens
            revokeAllUserTokens(userId);
            
            // Publish account freeze event
            AccountFreezeEvent freezeEvent = AccountFreezeEvent.builder()
                .userId(userId)
                .freezeId(freezeId)
                .freezeReason(reason)
                .freezeScope(scope)
                .severity(AccountFreezeEvent.FreezeSeverity.CRITICAL)
                .description(description)
                .frozenAt(LocalDateTime.now())
                .freezingSystem("ACCOUNT_FREEZE_SERVICE")
                .requiresManualReview(true)
                .build();
            
            kafkaTemplate.send("account-freezes", freezeEvent);
            
            // Audit the critical freeze
            auditService.auditHighRiskOperation(
                "ACCOUNT_FROZEN_IMMEDIATE",
                userId.toString(),
                String.format("Account frozen immediately - Reason: %s, Description: %s", reason, description),
                Map.of(
                    "freezeId", freezeId,
                    "reason", reason.toString(),
                    "scope", scope.toString(),
                    "description", description,
                    "frozenAt", LocalDateTime.now()
                )
            );
            
            log.error("COMPLIANCE: Account {} frozen immediately. Freeze ID: {}", userId, freezeId);
            return freezeId;
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to freeze account {} immediately", userId, e);
            
            // Audit the failure
            auditService.auditHighRiskOperation(
                "ACCOUNT_FREEZE_FAILED",
                userId.toString(),
                "Failed to freeze account immediately: " + e.getMessage(),
                Map.of("freezeId", freezeId, "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to freeze account immediately", e);
        }
    }
    
    @Override
    public String freezeAccountWithScope(UUID userId, AccountFreezeRequestEvent.FreezeReason reason,
                                        String description, AccountFreezeRequestEvent.FreezeScope scope) {
        String freezeId = UUID.randomUUID().toString();
        
        log.warn("COMPLIANCE: Freezing account {} with scope {} - {}", userId, scope, reason);
        
        try {
            // Apply freeze based on scope
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            
            // Set status based on scope
            switch (scope) {
                case FULL_FREEZE:
                    user.setStatus(UserStatus.FROZEN);
                    user.setCanTransact(false);
                    user.setCanWithdraw(false);
                    user.setCanDeposit(false);
                    break;
                case WITHDRAWAL_ONLY:
                    user.setStatus(UserStatus.RESTRICTED);
                    user.setCanTransact(true);
                    user.setCanWithdraw(false);
                    user.setCanDeposit(true);
                    break;
                case TRANSACTION_ONLY:
                    user.setStatus(UserStatus.RESTRICTED);
                    user.setCanTransact(false);
                    user.setCanWithdraw(false);
                    user.setCanDeposit(true);
                    break;
                case MONITORING_ONLY:
                    user.setStatus(UserStatus.MONITORED);
                    user.setMonitoringEnabled(true);
                    user.setMonitoringLevel("ENHANCED");
                    break;
            }
            
            user.setFreezeReason(reason.toString());
            user.setFreezeScope(scope.toString());
            user.setFrozenAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            
            // Audit the freeze
            auditService.auditHighRiskOperation(
                "ACCOUNT_FROZEN_WITH_SCOPE",
                userId.toString(),
                String.format("Account frozen with scope %s - %s", scope, reason),
                Map.of(
                    "freezeId", freezeId,
                    "reason", reason.toString(),
                    "scope", scope.toString(),
                    "description", description,
                    "frozenAt", LocalDateTime.now()
                )
            );
            
            log.warn("COMPLIANCE: Account {} frozen with scope {}. Freeze ID: {}", userId, scope, freezeId);
            return freezeId;
            
        } catch (Exception e) {
            log.error("Failed to freeze account {} with scope", userId, e);
            throw new RuntimeException("Failed to freeze account with scope", e);
        }
    }
    
    @Override
    public String applyTemporaryFreeze(UUID userId, AccountFreezeRequestEvent.FreezeReason reason,
                                      String description, AccountFreezeRequestEvent.FreezeScope scope,
                                      LocalDateTime expirationDate) {
        String freezeId = UUID.randomUUID().toString();
        
        log.warn("COMPLIANCE: Applying temporary freeze to account {} until {}", userId, expirationDate);
        
        try {
            // Apply temporary freeze with expiration
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            
            // Apply freeze based on scope
            applyFreezeByScope(user, scope);
            
            user.setFreezeReason(reason.toString());
            user.setFreezeDescription(description);
            user.setFreezeScope(scope.toString());
            user.setFrozenAt(LocalDateTime.now());
            user.setFreezeExpirationDate(expirationDate);
            user.setIsTemporaryFreeze(true);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            
            // Schedule automatic unfreeze
            scheduleAutomaticUnfreeze(userId, freezeId, expirationDate);
            
            // Audit the temporary freeze
            auditService.auditHighRiskOperation(
                "ACCOUNT_FROZEN_TEMPORARY",
                userId.toString(),
                String.format("Temporary freeze applied until %s - %s", expirationDate, reason),
                Map.of(
                    "freezeId", freezeId,
                    "reason", reason.toString(),
                    "scope", scope.toString(),
                    "expirationDate", expirationDate,
                    "appliedAt", LocalDateTime.now()
                )
            );
            
            log.warn("COMPLIANCE: Temporary freeze applied to account {}. Freeze ID: {}", userId, freezeId);
            return freezeId;
            
        } catch (Exception e) {
            log.error("Failed to apply temporary freeze to account {}", userId, e);
            throw new RuntimeException("Failed to apply temporary freeze", e);
        }
    }
    
    @Override
    public String applyMonitoringRestrictions(UUID userId, AccountFreezeRequestEvent.FreezeReason reason,
                                             AccountFreezeRequestEvent.FreezeScope scope) {
        String restrictionId = UUID.randomUUID().toString();
        
        log.info("COMPLIANCE: Applying monitoring restrictions to account {} - {}", userId, reason);
        
        try {
            // Apply monitoring-only restrictions
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            
            user.setStatus(UserStatus.MONITORED);
            user.setMonitoringEnabled(true);
            user.setMonitoringLevel("ENHANCED");
            user.setMonitoringReason(reason.toString());
            user.setMonitoringStartDate(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            
            // Publish monitoring event
            AccountMonitoringEvent monitoringEvent = AccountMonitoringEvent.builder()
                .userId(userId)
                .restrictionId(restrictionId)
                .monitoringLevel("ENHANCED")
                .reason(reason.toString())
                .startedAt(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send("account-monitoring", monitoringEvent);
            
            // Audit the monitoring restrictions
            auditService.auditHighRiskOperation(
                "MONITORING_RESTRICTIONS_APPLIED",
                userId.toString(),
                String.format("Monitoring restrictions applied - %s", reason),
                Map.of(
                    "restrictionId", restrictionId,
                    "reason", reason.toString(),
                    "scope", scope.toString(),
                    "appliedAt", LocalDateTime.now()
                )
            );
            
            log.info("COMPLIANCE: Monitoring restrictions applied to account {}. Restriction ID: {}", 
                userId, restrictionId);
            return restrictionId;
            
        } catch (Exception e) {
            log.error("Failed to apply monitoring restrictions to account {}", userId, e);
            // Don't throw for monitoring restrictions
            return restrictionId;
        }
    }
    
    @Override
    public void freezeAllWallets(UUID userId, List<String> walletIds, String reason) {
        log.warn("COMPLIANCE: Freezing all {} wallets for user {} - {}", 
            walletIds != null ? walletIds.size() : 0, userId, reason);
        
        try {
            // Freeze all wallets through wallet service
            walletServiceClient.freezeAllWallets(userId, reason);
            
            // If specific wallet IDs provided, ensure they're frozen
            if (walletIds != null && !walletIds.isEmpty()) {
                for (String walletId : walletIds) {
                    walletServiceClient.freezeWallet(walletId, reason);
                }
            }
            
            // Audit wallet freeze
            auditService.auditHighRiskOperation(
                "ALL_WALLETS_FROZEN",
                userId.toString(),
                String.format("All wallets frozen for user - %s", reason),
                Map.of(
                    "walletCount", walletIds != null ? walletIds.size() : 0,
                    "reason", reason,
                    "frozenAt", LocalDateTime.now()
                )
            );
            
            log.warn("COMPLIANCE: All wallets frozen for user {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to freeze wallets for user {}", userId, e);
            throw new RuntimeException("Failed to freeze wallets", e);
        }
    }
    
    @Override
    public void restrictWalletActivity(UUID userId, List<String> walletIds, 
                                      AccountFreezeRequestEvent.FreezeScope scope, LocalDateTime reviewDate) {
        log.warn("COMPLIANCE: Restricting wallet activity for user {} based on scope {}", userId, scope);
        
        try {
            // Apply wallet restrictions based on scope
            walletServiceClient.applyWalletRestrictions(userId, walletIds, scope.toString(), reviewDate);
            
            // Audit wallet restrictions
            auditService.auditHighRiskOperation(
                "WALLET_ACTIVITY_RESTRICTED",
                userId.toString(),
                String.format("Wallet activity restricted with scope %s", scope),
                Map.of(
                    "walletCount", walletIds != null ? walletIds.size() : 0,
                    "scope", scope.toString(),
                    "reviewDate", reviewDate,
                    "restrictedAt", LocalDateTime.now()
                )
            );
            
            log.warn("COMPLIANCE: Wallet activity restricted for user {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to restrict wallet activity for user {}", userId, e);
            throw new RuntimeException("Failed to restrict wallet activity", e);
        }
    }
    
    @Override
    public void applyStandardRestrictions(UUID userId, List<String> walletIds,
                                         AccountFreezeRequestEvent.FreezeScope scope) {
        log.info("COMPLIANCE: Applying standard restrictions to user {} wallets", userId);
        
        try {
            // Apply standard wallet restrictions
            walletServiceClient.applyStandardRestrictions(userId, walletIds, scope.toString());
            
            log.info("COMPLIANCE: Standard restrictions applied to user {} wallets", userId);
            
        } catch (Exception e) {
            log.error("Failed to apply standard restrictions to user {}", userId, e);
            // Don't throw for standard restrictions
        }
    }
    
    @Override
    public void cancelPendingTransactions(UUID userId, List<UUID> transactionIds, String reason) {
        log.warn("COMPLIANCE: Cancelling {} pending transactions for user {} - {}", 
            transactionIds.size(), userId, reason);
        
        try {
            // Cancel all specified transactions
            for (UUID transactionId : transactionIds) {
                walletServiceClient.cancelTransaction(transactionId, reason);
            }
            
            // Audit transaction cancellations
            auditService.auditHighRiskOperation(
                "PENDING_TRANSACTIONS_CANCELLED",
                userId.toString(),
                String.format("Cancelled %d pending transactions - %s", transactionIds.size(), reason),
                Map.of(
                    "transactionCount", transactionIds.size(),
                    "reason", reason,
                    "cancelledAt", LocalDateTime.now()
                )
            );
            
            log.warn("COMPLIANCE: Cancelled {} pending transactions for user {}", transactionIds.size(), userId);
            
        } catch (Exception e) {
            log.error("Failed to cancel pending transactions for user {}", userId, e);
            // Don't throw - continue with freeze even if transaction cancellation fails
        }
    }
    
    @Override
    public void createUrgentComplianceCase(UUID userId, String caseId, String violationType, String investigationId) {
        log.error("COMPLIANCE: Creating URGENT compliance case {} for user {} - {}", caseId, userId, violationType);
        
        try {
            // Create urgent compliance case
            complianceServiceClient.createUrgentCase(userId, caseId, violationType, investigationId, "URGENT");
            
            // Audit case creation
            auditService.auditHighRiskOperation(
                "URGENT_COMPLIANCE_CASE_CREATED",
                userId.toString(),
                String.format("Urgent compliance case created - Type: %s, Case: %s", violationType, caseId),
                Map.of(
                    "caseId", caseId,
                    "violationType", violationType,
                    "investigationId", investigationId,
                    "priority", "URGENT",
                    "createdAt", LocalDateTime.now()
                )
            );
            
            log.error("COMPLIANCE: Urgent compliance case {} created for user {}", caseId, userId);
            
        } catch (Exception e) {
            log.error("Failed to create urgent compliance case for user {}", userId, e);
            throw new RuntimeException("Failed to create urgent compliance case", e);
        }
    }
    
    @Override
    public void createComplianceCase(UUID userId, String caseId, String violationType, LocalDateTime reviewDate) {
        log.warn("COMPLIANCE: Creating compliance case {} for user {} - {}", caseId, userId, violationType);
        
        try {
            // Create standard compliance case
            complianceServiceClient.createCase(userId, caseId, violationType, reviewDate);
            
            // Audit case creation
            auditService.auditHighRiskOperation(
                "COMPLIANCE_CASE_CREATED",
                userId.toString(),
                String.format("Compliance case created - Type: %s, Case: %s", violationType, caseId),
                Map.of(
                    "caseId", caseId,
                    "violationType", violationType,
                    "reviewDate", reviewDate,
                    "createdAt", LocalDateTime.now()
                )
            );
            
            log.warn("COMPLIANCE: Compliance case {} created for user {}", caseId, userId);
            
        } catch (Exception e) {
            log.error("Failed to create compliance case for user {}", userId, e);
            // Don't throw for standard case creation
        }
    }
    
    @Override
    public void scheduleComplianceReview(UUID userId, String freezeId, LocalDateTime reviewTime) {
        log.info("COMPLIANCE: Scheduling compliance review for user {} at {}", userId, reviewTime);
        
        try {
            // Schedule compliance review
            complianceServiceClient.scheduleReview(userId, freezeId, reviewTime);
            
            log.info("COMPLIANCE: Review scheduled for user {} at {}", userId, reviewTime);
            
        } catch (Exception e) {
            log.error("Failed to schedule compliance review for user {}", userId, e);
            // Don't throw for scheduling
        }
    }
    
    @Override
    public void enableEnhancedMonitoring(UUID userId, String reason, LocalDateTime untilDate) {
        log.info("COMPLIANCE: Enabling enhanced monitoring for user {} until {}", userId, untilDate);
        
        try {
            // Enable enhanced monitoring
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            
            user.setMonitoringEnabled(true);
            user.setMonitoringLevel("ENHANCED");
            user.setMonitoringReason(reason);
            user.setMonitoringStartDate(LocalDateTime.now());
            user.setMonitoringEndDate(untilDate);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            
            // Publish enhanced monitoring event using AccountMonitoringEvent
            AccountMonitoringEvent monitoringEvent = AccountMonitoringEvent.builder()
                .userId(userId)
                .restrictionId(UUID.randomUUID().toString())
                .monitoringLevel("ENHANCED")
                .reason(reason)
                .startedAt(LocalDateTime.now())
                .endDate(untilDate)
                .notifyCompliance(true)
                .build();
            
            kafkaTemplate.send("account-monitoring", monitoringEvent);
            
            log.info("COMPLIANCE: Enhanced monitoring enabled for user {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to enable enhanced monitoring for user {}", userId, e);
            // Don't throw for monitoring
        }
    }
    
    @Override
    public void sendExecutiveAlert(AccountFreezeRequestEvent event, String freezeId) {
        log.error("EXECUTIVE ALERT: Account freeze for user {} - {}", event.getUserId(), event.getFreezeReason());
        
        try {
            String alertMessage = String.format(
                "CRITICAL ACCOUNT FREEZE\n" +
                "User: %s\n" +
                "Reason: %s\n" +
                "Severity: %s\n" +
                "Total Balance: %s\n" +
                "Pending Transactions: %d\n" +
                "Case ID: %s\n" +
                "Freeze ID: %s",
                event.getUserId(),
                event.getFreezeReason(),
                event.getSeverity(),
                event.getTotalAccountBalance(),
                event.getPendingTransactionCount(),
                event.getCaseId(),
                freezeId
            );
            
            notificationServiceClient.sendExecutiveAlert(
                "CRITICAL_ACCOUNT_FREEZE",
                "Critical Account Freeze Alert",
                alertMessage,
                freezeId
            );
            
            log.error("EXECUTIVE ALERT: Sent for account freeze {}", freezeId);
            
        } catch (Exception e) {
            log.error("Failed to send executive alert for freeze {}", freezeId, e);
        }
    }
    
    @Override
    public void notifyCustomer(UUID userId, String reason, String description) {
        log.info("COMPLIANCE: Notifying customer {} about account restrictions", userId);
        
        try {
            notificationServiceClient.sendCustomerNotification(
                userId,
                "Account Security Notice",
                String.format("Your account has been restricted for review. Reason: %s\n%s", reason, description),
                "ACCOUNT_RESTRICTION"
            );
            
            log.info("COMPLIANCE: Customer {} notified about account restrictions", userId);
            
        } catch (Exception e) {
            log.error("Failed to notify customer {} about restrictions", userId, e);
            // Don't throw for customer notification
        }
    }
    
    @Override
    public void freezeLinkedAccount(String linkedAccountId, UUID primaryUserId, 
                                   AccountFreezeRequestEvent.FreezeReason reason, String caseId) {
        log.warn("COMPLIANCE: Freezing linked account {} associated with user {}", linkedAccountId, primaryUserId);
        
        try {
            // Find and freeze linked account
            User linkedUser = userRepository.findByLinkedAccountId(linkedAccountId)
                .orElse(null);
            
            if (linkedUser != null) {
                linkedUser.setStatus(UserStatus.FROZEN);
                linkedUser.setFreezeReason(reason.toString());
                linkedUser.setLinkedFreezeReason("Linked to primary account freeze");
                linkedUser.setFrozenAt(LocalDateTime.now());
                linkedUser.setUpdatedAt(LocalDateTime.now());
                userRepository.save(linkedUser);
                
                // Freeze linked account wallets
                walletServiceClient.freezeAllWallets(linkedUser.getId(), "LINKED_ACCOUNT_FREEZE");
            }
            
            log.warn("COMPLIANCE: Linked account {} frozen", linkedAccountId);
            
        } catch (Exception e) {
            log.error("Failed to freeze linked account {}", linkedAccountId, e);
            // Don't throw for linked account freezing
        }
    }
    
    @Override
    public void triggerInvestigation(UUID relatedUserId, UUID primaryUserId, String reason, String investigationId) {
        log.info("COMPLIANCE: Triggering investigation for related user {} (primary: {})", relatedUserId, primaryUserId);
        
        try {
            complianceServiceClient.triggerInvestigation(relatedUserId, primaryUserId, reason, investigationId);
            
            log.info("COMPLIANCE: Investigation triggered for related user {}", relatedUserId);
            
        } catch (Exception e) {
            log.error("Failed to trigger investigation for related user {}", relatedUserId, e);
            // Don't throw for investigation triggers
        }
    }
    
    // Private helper methods
    
    private void revokeAllUserTokens(UUID userId) {
        try {
            // Revoke all refresh tokens for the user
            // This would typically involve invalidating tokens in a token store
            log.info("Revoking all tokens for user {}", userId);
            
            // Publish token revocation event
            TokenRevocationEvent revocationEvent = TokenRevocationEvent.builder()
                .userId(userId)
                .reason("ACCOUNT_FROZEN")
                .revokedAt(LocalDateTime.now())
                .revokeAllTokens(true)
                .build();
            
            kafkaTemplate.send("token-revocations", revocationEvent);
            
        } catch (Exception e) {
            log.error("Failed to revoke tokens for user {}", userId, e);
        }
    }
    
    private void applyFreezeByScope(User user, AccountFreezeRequestEvent.FreezeScope scope) {
        switch (scope) {
            case FULL_FREEZE:
                user.setStatus(UserStatus.FROZEN);
                user.setCanTransact(false);
                user.setCanWithdraw(false);
                user.setCanDeposit(false);
                break;
            case WITHDRAWAL_ONLY:
                user.setStatus(UserStatus.RESTRICTED);
                user.setCanTransact(true);
                user.setCanWithdraw(false);
                user.setCanDeposit(true);
                break;
            case TRANSACTION_ONLY:
                user.setStatus(UserStatus.RESTRICTED);
                user.setCanTransact(false);
                user.setCanWithdraw(false);
                user.setCanDeposit(true);
                break;
            case MONITORING_ONLY:
                user.setStatus(UserStatus.MONITORED);
                user.setMonitoringEnabled(true);
                user.setMonitoringLevel("ENHANCED");
                user.setCanTransact(true);
                user.setCanWithdraw(true);
                user.setCanDeposit(true);
                break;
        }
    }
    
    private void scheduleAutomaticUnfreeze(UUID userId, String freezeId, LocalDateTime unfreezeTime) {
        try {
            // Schedule automatic unfreeze task
            taskScheduler.schedule(() -> {
                performAutomaticUnfreeze(userId, freezeId);
            }, java.sql.Timestamp.valueOf(unfreezeTime).toInstant());
            
            log.info("Automatic unfreeze scheduled for user {} at {}", userId, unfreezeTime);
            
        } catch (Exception e) {
            log.error("Failed to schedule automatic unfreeze for user {}", userId, e);
        }
    }
    
    private void performAutomaticUnfreeze(UUID userId, String freezeId) {
        try {
            log.info("Performing automatic unfreeze for user {} (freeze: {})", userId, freezeId);
            
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("User {} not found for automatic unfreeze", userId);
                return;
            }
            
            // Only unfreeze if this is still a temporary freeze
            if (user.getIsTemporaryFreeze() && user.getFreezeExpirationDate() != null && 
                LocalDateTime.now().isAfter(user.getFreezeExpirationDate())) {
                
                // Unfreeze the account
                user.setStatus(UserStatus.ACTIVE);
                user.setFreezeReason(null);
                user.setFreezeDescription(null);
                user.setFreezeScope(null);
                user.setFrozenAt(null);
                user.setFreezeExpirationDate(null);
                user.setIsTemporaryFreeze(false);
                user.setCanTransact(true);
                user.setCanWithdraw(true);
                user.setCanDeposit(true);
                user.setUpdatedAt(LocalDateTime.now());
                userRepository.save(user);
                
                // Unfreeze wallets
                walletServiceClient.unfreezeAllWallets(userId, "TEMPORARY_FREEZE_EXPIRED");
                
                // Audit the automatic unfreeze
                auditService.auditHighRiskOperation(
                    "ACCOUNT_UNFROZEN_AUTOMATIC",
                    userId.toString(),
                    "Account automatically unfrozen after temporary freeze expiration",
                    Map.of(
                        "freezeId", freezeId,
                        "unfrozenAt", LocalDateTime.now()
                    )
                );
                
                // Notify user
                notificationServiceClient.sendCustomerNotification(
                    userId,
                    "Account Restriction Lifted",
                    "Your account restrictions have been automatically lifted. You can now use all account features.",
                    "ACCOUNT_UNFROZEN"
                );
                
                log.info("User {} automatically unfrozen", userId);
            }
            
        } catch (Exception e) {
            log.error("Error during automatic unfreeze of user {}", userId, e);
        }
    }
}