package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.waqiti.user.event.AccountStatusChangeEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.AccountManagementService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.ComplianceReportingService;
import com.waqiti.user.service.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-grade Kafka consumer for account status change events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountStatusChangeConsumer {

    private final UserService userService;
    private final AccountManagementService accountService;
    private final NotificationService notificationService;
    private final ComplianceReportingService complianceService;
    private final SecurityAuditService securityAuditService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "account-status-changes", groupId = "account-status-processor")
    public void processAccountStatusChange(@Payload AccountStatusChangeEvent event,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment) {
        try {
            log.info("Processing account status change for user: {} from {} to {} reason: {}", 
                    event.getUserId(), event.getPreviousStatus(), 
                    event.getNewStatus(), event.getChangeReason());
            
            // Validate event
            validateAccountStatusChangeEvent(event);
            
            // Process status change
            processStatusTransition(event);
            
            // Apply status-specific restrictions or permissions
            applyStatusBasedPermissions(event);
            
            // Handle financial implications
            if (hasFinancialImpact(event)) {
                handleFinancialChanges(event);
            }
            
            // Update compliance records
            updateComplianceRecords(event);
            
            // Send notifications
            sendStatusChangeNotifications(event);
            
            // Schedule follow-up actions
            scheduleFollowUpActions(event);
            
            // Log status change for audit
            securityAuditService.logAccountStatusChange(
                event.getUserId(),
                event.getAccountId(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getChangeReason(),
                event.getChangedBy(),
                event.getChangedAt(),
                event.getMetadata()
            );
            
            // Track metrics
            accountService.trackStatusChangeMetrics(
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getChangeReason()
            );
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed account status change for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process account status change for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, event),
                e
            ).exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Account status change processing failed", e);
        }
    }

    private void validateAccountStatusChangeEvent(AccountStatusChangeEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for account status change");
        }
        
        if (event.getAccountId() == null || event.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }
        
        if (event.getNewStatus() == null || event.getNewStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("New status is required");
        }
        
        if (event.getChangeReason() == null || event.getChangeReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Change reason is required");
        }
    }

    private void processStatusTransition(AccountStatusChangeEvent event) {
        String previousStatus = event.getPreviousStatus();
        String newStatus = event.getNewStatus();
        
        // Update account status
        accountService.updateAccountStatus(
            event.getAccountId(),
            event.getUserId(),
            newStatus,
            event.getChangeReason(),
            event.getChangedBy(),
            event.getEffectiveDate()
        );
        
        // Handle specific transitions
        String transition = previousStatus + "_TO_" + newStatus;
        switch (transition) {
            case "PENDING_TO_ACTIVE" -> handleAccountActivation(event);
            case "ACTIVE_TO_SUSPENDED" -> handleAccountSuspension(event);
            case "SUSPENDED_TO_ACTIVE" -> handleAccountReactivation(event);
            case "ACTIVE_TO_CLOSED" -> handleAccountClosure(event);
            case "SUSPENDED_TO_CLOSED" -> handleAccountClosure(event);
            case "ACTIVE_TO_FROZEN" -> handleAccountFreeze(event);
            case "FROZEN_TO_ACTIVE" -> handleAccountUnfreeze(event);
            case "ACTIVE_TO_RESTRICTED" -> handleAccountRestriction(event);
            case "RESTRICTED_TO_ACTIVE" -> handleRestrictionRemoval(event);
            case "PENDING_TO_REJECTED" -> handleAccountRejection(event);
            default -> log.debug("Standard transition: {}", transition);
        }
    }

    private void handleAccountActivation(AccountStatusChangeEvent event) {
        // Enable all account features
        accountService.enableAllFeatures(event.getAccountId());
        
        // Set initial limits
        accountService.setInitialLimits(
            event.getAccountId(),
            event.getAccountType(),
            event.getAccountTier()
        );
        
        // Grant initial permissions
        userService.grantInitialPermissions(
            event.getUserId(),
            event.getAccountType()
        );
        
        // Send welcome package
        notificationService.sendAccountActivationWelcome(
            event.getUserId(),
            event.getAccountId(),
            event.getAccountType()
        );
        
        log.info("Account activated: {} for user: {}", 
                event.getAccountId(), event.getUserId());
    }

    private void handleAccountSuspension(AccountStatusChangeEvent event) {
        // Disable transactional features
        accountService.disableTransactionalFeatures(event.getAccountId());
        
        // Freeze pending transactions
        accountService.freezePendingTransactions(event.getAccountId());
        
        // Revoke active sessions
        userService.revokeAccountSessions(
            event.getUserId(),
            event.getAccountId()
        );
        
        // Apply suspension restrictions
        Map<String, Object> restrictions = event.getRestrictions();
        if (restrictions != null) {
            accountService.applyRestrictions(
                event.getAccountId(),
                restrictions
            );
        }
        
        // Schedule review date
        if (event.getReviewDate() != null) {
            accountService.scheduleAccountReview(
                event.getAccountId(),
                event.getReviewDate(),
                "SUSPENSION_REVIEW"
            );
        }
        
        log.info("Account suspended: {} reason: {}", 
                event.getAccountId(), event.getChangeReason());
    }

    private void handleAccountReactivation(AccountStatusChangeEvent event) {
        // Re-enable features
        accountService.reEnableFeatures(event.getAccountId());
        
        // Process pending transactions
        accountService.processPendingTransactions(event.getAccountId());
        
        // Restore permissions
        userService.restoreAccountPermissions(
            event.getUserId(),
            event.getAccountId()
        );
        
        // Clear restrictions
        accountService.clearRestrictions(event.getAccountId());
        
        // Send reactivation notification
        notificationService.sendAccountReactivationNotice(
            event.getUserId(),
            event.getAccountId(),
            event.getEffectiveDate()
        );
        
        log.info("Account reactivated: {} for user: {}", 
                event.getAccountId(), event.getUserId());
    }

    private void handleAccountClosure(AccountStatusChangeEvent event) {
        // Process final settlements
        accountService.processFinalSettlement(
            event.getAccountId(),
            event.getFinalBalance(),
            event.getSettlementMethod()
        );
        
        // Archive account data
        accountService.archiveAccountData(
            event.getAccountId(),
            event.getArchiveRetentionPeriod()
        );
        
        // Revoke all access
        userService.revokeAllAccountAccess(
            event.getUserId(),
            event.getAccountId()
        );
        
        // Handle refunds if applicable
        if (event.getRefundAmount() != null && 
            event.getRefundAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            accountService.processRefund(
                event.getAccountId(),
                event.getRefundAmount(),
                event.getRefundMethod()
            );
        }
        
        // Generate closure certificate
        accountService.generateClosureCertificate(
            event.getAccountId(),
            event.getClosureReason(),
            event.getClosedAt()
        );
        
        log.info("Account closed: {} reason: {}", 
                event.getAccountId(), event.getClosureReason());
    }

    private void handleAccountFreeze(AccountStatusChangeEvent event) {
        // Immediately freeze all transactions
        accountService.freezeAllTransactions(event.getAccountId());
        
        // Block all debits
        accountService.blockDebits(event.getAccountId());
        
        // Apply legal hold if required
        if ("LEGAL_HOLD".equals(event.getFreezeType())) {
            accountService.applyLegalHold(
                event.getAccountId(),
                event.getLegalHoldReference(),
                event.getLegalHoldExpiry()
            );
        }
        
        // Notify compliance
        complianceService.reportAccountFreeze(
            event.getAccountId(),
            event.getUserId(),
            event.getFreezeReason(),
            event.getFreezeType()
        );
        
        log.info("Account frozen: {} type: {} reason: {}", 
                event.getAccountId(), event.getFreezeType(), event.getFreezeReason());
    }

    private void handleAccountUnfreeze(AccountStatusChangeEvent event) {
        // Remove freeze
        accountService.removeFreeze(event.getAccountId());
        
        // Process queued transactions
        accountService.processQueuedTransactions(event.getAccountId());
        
        // Remove legal hold if applicable
        if (event.getLegalHoldReference() != null) {
            accountService.removeLegalHold(
                event.getAccountId(),
                event.getLegalHoldReference()
            );
        }
        
        // Restore normal operations
        accountService.restoreNormalOperations(event.getAccountId());
        
        log.info("Account unfrozen: {} for user: {}", 
                event.getAccountId(), event.getUserId());
    }

    private void handleAccountRestriction(AccountStatusChangeEvent event) {
        // Apply specific restrictions
        Map<String, Object> restrictions = event.getRestrictions();
        if (restrictions != null) {
            // Apply transaction limits
            if (restrictions.containsKey("transactionLimit")) {
                accountService.setTransactionLimit(
                    event.getAccountId(),
                    (String) restrictions.get("transactionLimit")
                );
            }
            
            // Apply withdrawal limits
            if (restrictions.containsKey("withdrawalLimit")) {
                accountService.setWithdrawalLimit(
                    event.getAccountId(),
                    (String) restrictions.get("withdrawalLimit")
                );
            }
            
            // Block specific features
            if (restrictions.containsKey("blockedFeatures")) {
                accountService.blockFeatures(
                    event.getAccountId(),
                    (java.util.List<String>) restrictions.get("blockedFeatures")
                );
            }
        }
        
        log.info("Account restricted: {} restrictions: {}", 
                event.getAccountId(), restrictions);
    }

    private void handleRestrictionRemoval(AccountStatusChangeEvent event) {
        // Remove all restrictions
        accountService.removeAllRestrictions(event.getAccountId());
        
        // Restore full limits
        accountService.restoreFullLimits(
            event.getAccountId(),
            event.getAccountType(),
            event.getAccountTier()
        );
        
        // Re-enable blocked features
        accountService.enableAllFeatures(event.getAccountId());
        
        log.info("Account restrictions removed: {} for user: {}", 
                event.getAccountId(), event.getUserId());
    }

    private void handleAccountRejection(AccountStatusChangeEvent event) {
        // Mark account as rejected
        accountService.markAccountRejected(
            event.getAccountId(),
            event.getRejectionReason(),
            event.getRejectionDetails()
        );
        
        // Return any deposits
        if (event.getInitialDeposit() != null) {
            accountService.returnInitialDeposit(
                event.getAccountId(),
                event.getInitialDeposit(),
                event.getRefundMethod()
            );
        }
        
        // Clean up pending data
        accountService.cleanupPendingAccountData(event.getAccountId());
        
        // Send rejection notification
        notificationService.sendAccountRejectionNotice(
            event.getUserId(),
            event.getAccountId(),
            event.getRejectionReason(),
            event.getAppealProcess()
        );
        
        log.info("Account rejected: {} reason: {}", 
                event.getAccountId(), event.getRejectionReason());
    }

    private void applyStatusBasedPermissions(AccountStatusChangeEvent event) {
        // Get permissions for new status
        Map<String, Boolean> permissions = accountService.getStatusPermissions(
            event.getNewStatus(),
            event.getAccountType()
        );
        
        // Apply permissions
        userService.updateAccountPermissions(
            event.getUserId(),
            event.getAccountId(),
            permissions
        );
        
        // Update feature access
        accountService.updateFeatureAccess(
            event.getAccountId(),
            event.getNewStatus()
        );
    }

    private boolean hasFinancialImpact(AccountStatusChangeEvent event) {
        return event.getFinalBalance() != null ||
               event.getRefundAmount() != null ||
               event.getInitialDeposit() != null ||
               "CLOSED".equals(event.getNewStatus()) ||
               "FROZEN".equals(event.getNewStatus());
    }

    private void handleFinancialChanges(AccountStatusChangeEvent event) {
        // Handle balance adjustments
        if (event.getBalanceAdjustment() != null) {
            accountService.adjustBalance(
                event.getAccountId(),
                event.getBalanceAdjustment(),
                event.getAdjustmentReason()
            );
        }
        
        // Process pending fees
        if (event.getPendingFees() != null && 
            event.getPendingFees().compareTo(java.math.BigDecimal.ZERO) > 0) {
            accountService.processPendingFees(
                event.getAccountId(),
                event.getPendingFees()
            );
        }
        
        // Handle interest calculations
        if ("CLOSED".equals(event.getNewStatus())) {
            accountService.calculateFinalInterest(
                event.getAccountId(),
                event.getClosedAt()
            );
        }
    }

    private void updateComplianceRecords(AccountStatusChangeEvent event) {
        // Report status change to compliance
        complianceService.reportAccountStatusChange(
            event.getAccountId(),
            event.getUserId(),
            event.getPreviousStatus(),
            event.getNewStatus(),
            event.getChangeReason(),
            event.getChangedBy()
        );
        
        // Update risk assessment if needed
        if (isRiskRelated(event)) {
            complianceService.updateRiskAssessment(
                event.getUserId(),
                event.getAccountId(),
                event.getNewStatus(),
                event.getRiskFactors()
            );
        }
        
        // File regulatory reports if required
        if (requiresRegulatoryReporting(event)) {
            complianceService.fileRegulatoryReport(
                event.getAccountId(),
                event.getNewStatus(),
                event.getChangeReason(),
                event.getRegulatoryReference()
            );
        }
    }

    private boolean isRiskRelated(AccountStatusChangeEvent event) {
        return "FRAUD".equals(event.getChangeReason()) ||
               "COMPLIANCE".equals(event.getChangeReason()) ||
               "SECURITY".equals(event.getChangeReason()) ||
               "AML".equals(event.getChangeReason()) ||
               event.getRiskFactors() != null;
    }

    private boolean requiresRegulatoryReporting(AccountStatusChangeEvent event) {
        return "FROZEN".equals(event.getNewStatus()) ||
               "LEGAL_HOLD".equals(event.getFreezeType()) ||
               "AML".equals(event.getChangeReason()) ||
               "REGULATORY_ACTION".equals(event.getChangeReason());
    }

    private void sendStatusChangeNotifications(AccountStatusChangeEvent event) {
        // Send primary notification
        notificationService.sendAccountStatusChangeNotification(
            event.getUserId(),
            event.getAccountId(),
            event.getPreviousStatus(),
            event.getNewStatus(),
            event.getChangeReason(),
            event.getEffectiveDate()
        );
        
        // Send additional notifications based on status
        if ("SUSPENDED".equals(event.getNewStatus()) || "FROZEN".equals(event.getNewStatus())) {
            notificationService.sendUrgentAccountAlert(
                event.getUserId(),
                event.getAccountId(),
                event.getNewStatus(),
                event.getChangeReason(),
                event.getActionRequired()
            );
        }
        
        // Notify authorized users
        if (event.getAuthorizedUsers() != null) {
            for (String authorizedUser : event.getAuthorizedUsers()) {
                notificationService.notifyAuthorizedUser(
                    authorizedUser,
                    event.getAccountId(),
                    event.getNewStatus()
                );
            }
        }
    }

    private void scheduleFollowUpActions(AccountStatusChangeEvent event) {
        // Schedule review if needed
        if (event.getReviewDate() != null) {
            accountService.scheduleStatusReview(
                event.getAccountId(),
                event.getReviewDate(),
                event.getNewStatus()
            );
        }
        
        // Schedule automatic reactivation
        if (event.getAutoReactivateDate() != null) {
            accountService.scheduleAutoReactivation(
                event.getAccountId(),
                event.getAutoReactivateDate()
            );
        }
        
        // Schedule expiry
        if (event.getStatusExpiryDate() != null) {
            accountService.scheduleStatusExpiry(
                event.getAccountId(),
                event.getStatusExpiryDate(),
                event.getExpiryAction()
            );
        }
    }
}