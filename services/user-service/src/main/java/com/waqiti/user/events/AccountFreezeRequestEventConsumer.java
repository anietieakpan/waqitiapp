package com.waqiti.user.events;

import com.waqiti.common.events.AccountFreezeRequestEvent;
import com.waqiti.user.service.AccountFreezeService;
import com.waqiti.user.service.ComplianceReportingService;
import com.waqiti.common.audit.ComprehensiveAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Critical Event Consumer for Account Freeze Requests
 * 
 * BUSINESS IMPACT: Ensures immediate compliance with regulatory requirements
 * COMPLIANCE IMPACT: Prevents violations by freezing accounts within required timeframes
 * 
 * This consumer was identified as MISSING in the forensic audit, causing:
 * - Delayed account freezing for compliance violations
 * - Risk of continued suspicious activity
 * - Regulatory notification delays
 * - Failed compliance automation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountFreezeRequestEventConsumer {
    
    private final AccountFreezeService accountFreezeService;
    private final ComplianceReportingService complianceReportingService;
    private final ComprehensiveAuditService auditService;
    
    /**
     * CRITICAL: Process account freeze requests to maintain compliance
     * 
     * This consumer handles account freeze requests from compliance and fraud detection systems
     * and ensures immediate account restrictions
     */
    @KafkaListener(
        topics = "account-freeze-requests",
        groupId = "user-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleAccountFreezeRequest(
            @Payload AccountFreezeRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.warn("COMPLIANCE: Processing account freeze request for user {} with severity {} from partition {}, offset {}", 
            event.getUserId(), event.getSeverity(), partition, offset);
        
        try {
            // Audit the freeze request reception
            auditService.auditHighRiskOperation(
                "ACCOUNT_FREEZE_REQUEST_RECEIVED",
                event.getUserId().toString(),
                "Account freeze request processed for user: " + event.getUserId(),
                Map.of(
                    "freezeReason", event.getFreezeReason(),
                    "severity", event.getSeverity(),
                    "caseId", event.getCaseId(),
                    "requestingSystem", event.getRequestingSystem()
                )
            );
            
            // Execute the account freeze based on severity
            executeAccountFreeze(event);
            
            // Handle compliance reporting if required
            handleComplianceReporting(event);
            
            // Process linked accounts if any
            if (event.getLinkedAccountIds() != null && !event.getLinkedAccountIds().isEmpty()) {
                freezeLinkedAccounts(event);
            }
            
            // Trigger investigation for related users
            if (event.getRelatedUserIds() != null && !event.getRelatedUserIds().isEmpty()) {
                triggerRelatedInvestigations(event);
            }
            
            // Log successful processing
            log.warn("COMPLIANCE: Successfully froze account {} for reason: {}", 
                event.getUserId(), event.getFreezeReason());
            
            // Acknowledge the message after successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process account freeze request for user {}", event.getUserId(), e);
            
            // Audit the failure
            auditService.auditHighRiskOperation(
                "ACCOUNT_FREEZE_REQUEST_FAILED",
                event.getUserId().toString(),
                "Failed to freeze account: " + e.getMessage(),
                Map.of(
                    "error", e.getMessage(),
                    "freezeReason", event.getFreezeReason()
                )
            );
            
            // Don't acknowledge - let the message be retried or sent to DLQ
            throw new RuntimeException("Account freeze processing failed", e);
        }
    }
    
    /**
     * Execute account freeze based on severity and scope
     */
    private void executeAccountFreeze(AccountFreezeRequestEvent event) {
        switch (event.getSeverity()) {
            case CRITICAL:
                handleCriticalFreeze(event);
                break;
            case HIGH:
                handleHighSeverityFreeze(event);
                break;
            case MEDIUM:
                handleMediumSeverityFreeze(event);
                break;
            case LOW:
                handleLowSeverityFreeze(event);
                break;
            default:
                log.warn("Unknown freeze severity: {}", event.getSeverity());
                handleMediumSeverityFreeze(event); // Default to medium
        }
    }
    
    /**
     * Handle critical freezes - immediate action with full lockdown
     */
    private void handleCriticalFreeze(AccountFreezeRequestEvent event) {
        log.error("CRITICAL COMPLIANCE: Freezing account {} immediately - {}", 
            event.getUserId(), event.getFreezeReason());
        
        try {
            // Immediately freeze the account with full scope
            String freezeId = accountFreezeService.freezeAccountImmediately(
                event.getUserId(),
                event.getFreezeReason(),
                event.getFreezeDescription(),
                AccountFreezeRequestEvent.FreezeScope.FULL_FREEZE
            );
            
            // Freeze all wallets associated with the account
            accountFreezeService.freezeAllWallets(
                event.getUserId(),
                event.getWalletIds(),
                event.getFreezeReason().toString()
            );
            
            // Cancel all pending transactions
            if (event.getAffectedTransactionIds() != null && !event.getAffectedTransactionIds().isEmpty()) {
                accountFreezeService.cancelPendingTransactions(
                    event.getUserId(),
                    event.getAffectedTransactionIds(),
                    "ACCOUNT_FROZEN_" + event.getFreezeReason()
                );
            }
            
            // Send immediate notifications
            if (event.isNotifyLawEnforcement()) {
                complianceReportingService.notifyLawEnforcement(event, freezeId);
            }
            
            if (event.isNotifyRegulators()) {
                complianceReportingService.notifyRegulators(event, freezeId);
            }
            
            // Send executive alert
            accountFreezeService.sendExecutiveAlert(event, freezeId);
            
        } catch (Exception e) {
            log.error("Failed to handle critical account freeze for user {}", event.getUserId(), e);
            throw e;
        }
    }
    
    /**
     * Handle high severity freezes - rapid response with compliance tracking
     */
    private void handleHighSeverityFreeze(AccountFreezeRequestEvent event) {
        log.warn("HIGH SEVERITY COMPLIANCE: Freezing account {} - {}", 
            event.getUserId(), event.getFreezeReason());
        
        try {
            // Freeze account based on specified scope
            String freezeId = accountFreezeService.freezeAccountWithScope(
                event.getUserId(),
                event.getFreezeReason(),
                event.getFreezeDescription(),
                event.getFreezeScope()
            );
            
            // Apply restrictions to wallets
            accountFreezeService.restrictWalletActivity(
                event.getUserId(),
                event.getWalletIds(),
                event.getFreezeScope(),
                event.getReviewDate()
            );
            
            // Create compliance case for urgent review
            accountFreezeService.createUrgentComplianceCase(
                event.getUserId(),
                event.getCaseId(),
                event.getFreezeReason().toString(),
                event.getInvestigationId()
            );
            
            // Schedule review within 24 hours
            accountFreezeService.scheduleComplianceReview(
                event.getUserId(),
                freezeId,
                LocalDateTime.now().plusHours(24)
            );
            
            // Send compliance team notification
            complianceReportingService.notifyComplianceTeam(event, freezeId);
            
        } catch (Exception e) {
            log.error("Failed to handle high severity account freeze for user {}", event.getUserId(), e);
            throw e;
        }
    }
    
    /**
     * Handle medium severity freezes - standard compliance procedures
     */
    private void handleMediumSeverityFreeze(AccountFreezeRequestEvent event) {
        log.warn("MEDIUM SEVERITY COMPLIANCE: Freezing account {} - {}", 
            event.getUserId(), event.getFreezeReason());
        
        try {
            // Apply freeze with specified scope
            String freezeId = accountFreezeService.freezeAccountWithScope(
                event.getUserId(),
                event.getFreezeReason(),
                event.getFreezeDescription(),
                event.getFreezeScope()
            );
            
            // Apply standard wallet restrictions
            if (event.getFreezeScope() != AccountFreezeRequestEvent.FreezeScope.INVESTIGATION_HOLD) {
                accountFreezeService.applyStandardRestrictions(
                    event.getUserId(),
                    event.getWalletIds(),
                    event.getFreezeScope()
                );
            }
            
            // Create compliance case for review within 48 hours
            accountFreezeService.createComplianceCase(
                event.getUserId(),
                event.getCaseId(),
                event.getFreezeReason().toString(),
                event.getReviewDate()
            );
            
            // Notify customer if required
            if (event.isNotifyCustomer()) {
                accountFreezeService.notifyCustomer(
                    event.getUserId(),
                    event.getFreezeReason().toString(),
                    event.getFreezeDescription()
                );
            }
            
        } catch (Exception e) {
            log.error("Failed to handle medium severity account freeze for user {}", event.getUserId(), e);
            // Don't rethrow for medium severity - log and continue
        }
    }
    
    /**
     * Handle low severity freezes - temporary restrictions with monitoring
     */
    private void handleLowSeverityFreeze(AccountFreezeRequestEvent event) {
        log.info("LOW SEVERITY COMPLIANCE: Applying restrictions to account {} - {}", 
            event.getUserId(), event.getFreezeReason());
        
        try {
            // Apply temporary restrictions
            String freezeId;
            if (event.isTemporaryFreeze()) {
                freezeId = accountFreezeService.applyTemporaryFreeze(
                    event.getUserId(),
                    event.getFreezeReason(),
                    event.getFreezeDescription(),
                    event.getFreezeScope(),
                    event.getExpirationDate()
                );
            } else {
                freezeId = accountFreezeService.applyMonitoringRestrictions(
                    event.getUserId(),
                    event.getFreezeReason(),
                    event.getFreezeScope()
                );
            }
            
            // Enable enhanced monitoring
            accountFreezeService.enableEnhancedMonitoring(
                event.getUserId(),
                event.getFreezeReason().toString(),
                event.getReviewDate()
            );
            
        } catch (Exception e) {
            log.error("Failed to handle low severity account freeze for user {}", event.getUserId(), e);
            // Don't rethrow for low severity
        }
    }
    
    /**
     * Handle compliance reporting requirements
     */
    private void handleComplianceReporting(AccountFreezeRequestEvent event) {
        try {
            // Check if regulatory notification is required
            if (event.requiresRegulatoryNotification()) {
                log.warn("REGULATORY: Sending regulatory notifications for account freeze - user {}", event.getUserId());
                
                // Send notifications to specified regulatory bodies
                if (event.getRegulatoryBodies() != null && !event.getRegulatoryBodies().isEmpty()) {
                    for (String regulatoryBody : event.getRegulatoryBodies()) {
                        complianceReportingService.notifyRegulatoryBody(
                            regulatoryBody,
                            event.getUserId(),
                            event.getFreezeReason(),
                            event.getCaseId()
                        );
                    }
                }
                
                // File SAR if needed for AML/sanctions violations
                if (event.getFreezeReason() == AccountFreezeRequestEvent.FreezeReason.SANCTIONS_MATCH ||
                    event.getFreezeReason() == AccountFreezeRequestEvent.FreezeReason.AML_VIOLATION ||
                    event.getFreezeReason() == AccountFreezeRequestEvent.FreezeReason.TERRORIST_FINANCING) {
                    
                    complianceReportingService.fileSuspiciousActivityReport(
                        event.getUserId(),
                        event.getFreezeReason().toString(),
                        event.getSuspiciousActivityPattern(),
                        event.getTotalAccountBalance(),
                        event.getCaseId()
                    );
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to handle compliance reporting for account freeze - user {}", event.getUserId(), e);
            // Don't rethrow - reporting failures shouldn't block freeze execution
        }
    }
    
    /**
     * Freeze linked accounts
     */
    private void freezeLinkedAccounts(AccountFreezeRequestEvent event) {
        log.warn("COMPLIANCE: Freezing {} linked accounts for user {}", 
            event.getLinkedAccountIds().size(), event.getUserId());
        
        try {
            for (String linkedAccountId : event.getLinkedAccountIds()) {
                accountFreezeService.freezeLinkedAccount(
                    linkedAccountId,
                    event.getUserId(),
                    event.getFreezeReason(),
                    event.getCaseId()
                );
            }
            
            log.warn("COMPLIANCE: Successfully froze {} linked accounts", event.getLinkedAccountIds().size());
            
        } catch (Exception e) {
            log.error("Failed to freeze linked accounts for user {}", event.getUserId(), e);
            // Don't rethrow - continue with primary freeze even if linked freezes fail
        }
    }
    
    /**
     * Trigger investigations for related users
     */
    private void triggerRelatedInvestigations(AccountFreezeRequestEvent event) {
        log.info("COMPLIANCE: Triggering investigations for {} related users", event.getRelatedUserIds().size());
        
        try {
            for (UUID relatedUserId : event.getRelatedUserIds()) {
                accountFreezeService.triggerInvestigation(
                    relatedUserId,
                    event.getUserId(),
                    event.getFreezeReason().toString(),
                    event.getInvestigationId()
                );
            }
            
            log.info("COMPLIANCE: Triggered {} related investigations", event.getRelatedUserIds().size());
            
        } catch (Exception e) {
            log.error("Failed to trigger related investigations for user {}", event.getUserId(), e);
            // Don't rethrow - investigations are secondary to the main freeze
        }
    }
}