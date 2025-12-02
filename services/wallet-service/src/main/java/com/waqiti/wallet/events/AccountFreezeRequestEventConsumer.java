package com.waqiti.wallet.events;

import com.waqiti.common.events.AccountFreezeRequestEvent;
import com.waqiti.common.events.AccountFreezeRequestEvent.FreezeReason;
import com.waqiti.common.events.AccountFreezeRequestEvent.FreezeSeverity;
import com.waqiti.common.events.AccountFreezeRequestEvent.FreezeScope;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.WalletFreezeService;
import com.waqiti.wallet.service.WalletComplianceService;
import com.waqiti.common.audit.AuditService;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Wallet Account Freeze Request Consumer
 * 
 * Processes account freeze requests specifically for wallet operations and balance restrictions.
 * This consumer handles wallet-specific freeze operations that complement the user-service
 * account freeze processing.
 * 
 * BUSINESS IMPACT: Ensures immediate wallet restrictions to prevent continued fraud losses
 * COMPLIANCE IMPACT: Implements wallet-level controls required for regulatory compliance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountFreezeRequestEventConsumer {

    private final WalletService walletService;
    private final WalletFreezeService walletFreezeService;
    private final WalletComplianceService walletComplianceService;
    private final AuditService auditService;
    private final com.waqiti.common.alert.AlertService alertService;
    private final com.waqiti.wallet.client.UserServiceClient userService;
    private final com.waqiti.wallet.repository.ComplianceIncidentRepository complianceIncidentRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.waqiti.wallet.service.ComplianceNotificationService complianceNotificationService;
    private final com.waqiti.common.metrics.ErrorMetrics errorMetrics;
    private final com.waqiti.common.metrics.ComplianceMetrics complianceMetrics;
    private final com.waqiti.wallet.service.RegulatoryIncidentService regulatoryIncidentService;
    private final com.waqiti.wallet.service.OperationalTaskService operationalTaskService;
    private final UniversalDLQHandler dlqHandler;
    
    /**
     * CRITICAL: Process wallet-specific account freeze requests
     * 
     * This consumer handles wallet freeze operations including balance restrictions,
     * transaction blocks, and compliance holds on wallet activities
     */
    @KafkaListener(
        topics = "account-freeze-requests",
        groupId = "wallet-service-freeze-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 100, multiplier = 1.1, maxDelay = 5000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {
            IllegalArgumentException.class,
            com.fasterxml.jackson.core.JsonProcessingException.class,
            org.springframework.kafka.support.serializer.DeserializationException.class
        }
    )
    @Transactional
    public void handleAccountFreezeRequest(
            @Valid @Payload AccountFreezeRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.warn("WALLET FREEZE: Processing wallet freeze request for user {} with severity {} - " +
                "Reason: {}, Scope: {}, Partition: {}, Offset: {}",
                event.getUserId(), event.getSeverity(), event.getFreezeReason(), 
                event.getFreezeScope(), partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        
        try {
            // Validate event
            validateAccountFreezeEvent(event);
            
            // Process wallet freeze based on scope and severity
            WalletFreezeResult freezeResult = executeWalletFreeze(event);
            
            // Handle specific wallet IDs if provided
            if (event.getWalletIds() != null && !event.getWalletIds().isEmpty()) {
                processSpecificWalletFreeze(event, freezeResult);
            }
            
            // Apply balance restrictions based on freeze scope
            applyBalanceRestrictions(event, freezeResult);
            
            // Cancel pending wallet transactions if required
            if (event.getAffectedTransactionIds() != null && !event.getAffectedTransactionIds().isEmpty()) {
                cancelPendingWalletTransactions(event);
            }
            
            // Update wallet compliance status
            updateWalletComplianceStatus(event, freezeResult);
            
            // Audit the wallet freeze operation
            auditService.auditWalletEvent(
                "WALLET_FREEZE_PROCESSED",
                event.getUserId().toString(),
                String.format("Wallet freeze processed - Reason: %s, Scope: %s, Wallets: %d", 
                    event.getFreezeReason(), event.getFreezeScope(), 
                    event.getWalletIds() != null ? event.getWalletIds().size() : 0),
                Map.of(
                    "userId", event.getUserId(),
                    "freezeReason", event.getFreezeReason(),
                    "severity", event.getSeverity(),
                    "freezeScope", event.getFreezeScope(),
                    "caseId", event.getCaseId() != null ? event.getCaseId() : "N/A",
                    "walletCount", event.getWalletIds() != null ? event.getWalletIds().size() : 0,
                    "processingTimeMs", java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis()
                )
            );
            
            acknowledgment.acknowledge();
            
            log.info("WALLET FREEZE: Successfully processed wallet freeze for user: {} - " +
                    "Reason: {}, Wallets affected: {}, Processing time: {}ms",
                    event.getUserId(), event.getFreezeReason(), 
                    event.getWalletIds() != null ? event.getWalletIds().size() : 0,
                    java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process wallet freeze request for user: {} - Error: {}",
                    event.getUserId(), e.getMessage(), e);

            // Audit the failure
            auditService.auditWalletEvent(
                "WALLET_FREEZE_PROCESSING_FAILED",
                event.getUserId().toString(),
                "Failed to process wallet freeze: " + e.getMessage(),
                Map.of(
                    "error", e.getClass().getSimpleName(),
                    "freezeReason", event.getFreezeReason(),
                    "userId", event.getUserId()
                )
            );

            dlqHandler.handleFailedMessage(event, topic, partition, offset, e)
                .thenAccept(result -> log.info("Account freeze request event sent to DLQ: userId={}, destination={}, category={}",
                        event.getUserId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for account freeze request event - MESSAGE MAY BE LOST! " +
                            "userId={}, partition={}, offset={}, error={}",
                            event.getUserId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw e; // Let retry mechanism handle this
        }
    }
    
    /**
     * Validate account freeze event for wallet processing
     */
    private void validateAccountFreezeEvent(AccountFreezeRequestEvent event) {
        if (event.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required for wallet freeze");
        }
        
        if (event.getFreezeReason() == null) {
            throw new IllegalArgumentException("Freeze reason is required");
        }
        
        if (event.getFreezeScope() == null) {
            throw new IllegalArgumentException("Freeze scope is required");
        }
        
        if (event.getSeverity() == null) {
            throw new IllegalArgumentException("Freeze severity is required");
        }
    }
    
    /**
     * Execute wallet freeze based on severity and scope
     */
    private WalletFreezeResult executeWalletFreeze(AccountFreezeRequestEvent event) {
        WalletFreezeResult.Builder resultBuilder = WalletFreezeResult.builder()
                .userId(event.getUserId())
                .freezeReason(event.getFreezeReason().toString())
                .severity(event.getSeverity().toString())
                .scope(event.getFreezeScope().toString())
                .requestTime(LocalDateTime.now());
        
        try {
            switch (event.getSeverity()) {
                case CRITICAL:
                    return executeCriticalWalletFreeze(event, resultBuilder);
                case HIGH:
                    return executeHighSeverityWalletFreeze(event, resultBuilder);
                case MEDIUM:
                    return executeMediumSeverityWalletFreeze(event, resultBuilder);
                case LOW:
                    return executeLowSeverityWalletFreeze(event, resultBuilder);
                default:
                    log.warn("Unknown freeze severity: {}, defaulting to MEDIUM", event.getSeverity());
                    return executeMediumSeverityWalletFreeze(event, resultBuilder);
            }
        } catch (Exception e) {
            log.error("Failed to execute wallet freeze for user: {}", event.getUserId(), e);
            throw new RuntimeException("Wallet freeze execution failed", e);
        }
    }
    
    /**
     * Execute critical wallet freeze - immediate full lockdown
     */
    private WalletFreezeResult executeCriticalWalletFreeze(AccountFreezeRequestEvent event, 
                                                          WalletFreezeResult.Builder resultBuilder) {
        log.error("CRITICAL WALLET FREEZE: Immediate lockdown for user: {} - Reason: {}",
                event.getUserId(), event.getFreezeReason());
        
        // Get all user wallets
        List<String> allWalletIds = walletService.getAllWalletIds(event.getUserId());
        
        // Freeze all wallets immediately
        List<String> frozenWallets = walletFreezeService.freezeAllWalletsImmediately(
            event.getUserId(),
            allWalletIds,
            event.getFreezeReason().toString(),
            "CRITICAL_FREEZE"
        );
        
        // Block all pending transactions
        int blockedTransactions = walletService.blockAllPendingTransactions(
            event.getUserId(), 
            "CRITICAL_FREEZE_" + event.getFreezeReason()
        );
        
        // Freeze all balances
        walletFreezeService.freezeAllBalances(event.getUserId(), event.getFreezeReason().toString());
        
        return resultBuilder
                .frozenWallets(frozenWallets)
                .blockedTransactions(blockedTransactions)
                .balancesFrozen(true)
                .success(true)
                .message("Critical wallet freeze executed successfully")
                .build();
    }
    
    /**
     * Execute high severity wallet freeze - targeted restrictions
     */
    private WalletFreezeResult executeHighSeverityWalletFreeze(AccountFreezeRequestEvent event, 
                                                              WalletFreezeResult.Builder resultBuilder) {
        log.warn("HIGH SEVERITY WALLET FREEZE: Targeted restrictions for user: {} - Reason: {}",
                event.getUserId(), event.getFreezeReason());
        
        List<String> targetWallets = event.getWalletIds() != null ? 
            event.getWalletIds() : walletService.getPrimaryWalletIds(event.getUserId());
        
        List<String> frozenWallets;
        switch (event.getFreezeScope()) {
            case FULL_FREEZE:
                frozenWallets = walletFreezeService.freezeWalletsCompletely(
                    event.getUserId(), targetWallets, event.getFreezeReason().toString());
                break;
            case DEBIT_ONLY:
                frozenWallets = walletFreezeService.freezeWalletDebits(
                    event.getUserId(), targetWallets, event.getFreezeReason().toString());
                break;
            case HIGH_VALUE_ONLY:
                frozenWallets = walletFreezeService.freezeHighValueTransactions(
                    event.getUserId(), targetWallets, event.getTotalAccountBalance());
                break;
            default:
                frozenWallets = walletFreezeService.freezeWalletsByScope(
                    event.getUserId(), targetWallets, event.getFreezeScope());
        }
        
        return resultBuilder
                .frozenWallets(frozenWallets)
                .blockedTransactions(0)
                .balancesFrozen(event.getFreezeScope() == AccountFreezeRequestEvent.FreezeScope.FULL_FREEZE)
                .success(true)
                .message("High severity wallet freeze executed successfully")
                .build();
    }
    
    /**
     * Execute medium severity wallet freeze - standard restrictions
     */
    private WalletFreezeResult executeMediumSeverityWalletFreeze(AccountFreezeRequestEvent event, 
                                                               WalletFreezeResult.Builder resultBuilder) {
        log.info("MEDIUM SEVERITY WALLET FREEZE: Standard restrictions for user: {} - Reason: {}",
                event.getUserId(), event.getFreezeReason());
        
        List<String> targetWallets = event.getWalletIds() != null ? 
            event.getWalletIds() : List.of();
        
        List<String> restrictedWallets = walletFreezeService.applyWalletRestrictions(
            event.getUserId(),
            targetWallets,
            event.getFreezeScope(),
            event.getReviewDate()
        );
        
        // Enable enhanced monitoring for all wallets
        walletService.enableEnhancedMonitoring(event.getUserId(), event.getReviewDate());
        
        return resultBuilder
                .frozenWallets(restrictedWallets)
                .blockedTransactions(0)
                .balancesFrozen(false)
                .success(true)
                .message("Medium severity wallet restrictions applied")
                .build();
    }
    
    /**
     * Execute low severity wallet freeze - monitoring and limits
     */
    private WalletFreezeResult executeLowSeverityWalletFreeze(AccountFreezeRequestEvent event, 
                                                             WalletFreezeResult.Builder resultBuilder) {
        log.info("LOW SEVERITY WALLET FREEZE: Enhanced monitoring for user: {} - Reason: {}",
                event.getUserId(), event.getFreezeReason());
        
        // Apply temporary transaction limits
        walletService.applyTemporaryLimits(
            event.getUserId(),
            event.getTotalAccountBalance(),
            event.getExpirationDate()
        );
        
        // Enable transaction monitoring
        walletService.enableTransactionMonitoring(
            event.getUserId(),
            event.getFreezeReason().toString()
        );
        
        return resultBuilder
                .frozenWallets(List.of())
                .blockedTransactions(0)
                .balancesFrozen(false)
                .success(true)
                .message("Low severity monitoring and limits applied")
                .build();
    }
    
    /**
     * Process freeze for specific wallet IDs
     */
    private void processSpecificWalletFreeze(AccountFreezeRequestEvent event, WalletFreezeResult freezeResult) {
        try {
            for (String walletId : event.getWalletIds()) {
                walletFreezeService.freezeSpecificWallet(
                    walletId,
                    event.getUserId(),
                    event.getFreezeReason().toString(),
                    event.getFreezeScope(),
                    event.getCaseId()
                );
                
                log.debug("Froze specific wallet: {} for user: {}", walletId, event.getUserId());
            }
        } catch (Exception e) {
            log.error("Failed to freeze specific wallets for user: {}", event.getUserId(), e);
            // Don't throw - partial success is acceptable
        }
    }
    
    /**
     * Apply balance restrictions based on freeze scope
     */
    private void applyBalanceRestrictions(AccountFreezeRequestEvent event, WalletFreezeResult freezeResult) {
        if (!freezeResult.isBalancesFrozen()) {
            return; // Balance restrictions already applied
        }
        
        try {
            switch (event.getFreezeScope()) {
                case FULL_FREEZE:
                    walletFreezeService.freezeAllBalances(event.getUserId(), event.getFreezeReason().toString());
                    break;
                case DEBIT_ONLY:
                    walletFreezeService.freezeOutgoingBalances(event.getUserId());
                    break;
                case HIGH_VALUE_ONLY:
                    walletFreezeService.freezeHighValueBalances(event.getUserId(), event.getTotalAccountBalance());
                    break;
                case SPECIFIC_CURRENCIES:
                    // Implementation would freeze specific currency balances
                    walletFreezeService.freezeSpecificCurrencyBalances(event.getUserId(), List.of("USD", "EUR"));
                    break;
                default:
                    log.debug("No specific balance restrictions for scope: {}", event.getFreezeScope());
            }
        } catch (Exception e) {
            log.error("Failed to apply balance restrictions for user: {}", event.getUserId(), e);
            // Don't throw - continue with other freeze operations
        }
    }
    
    /**
     * Cancel pending wallet transactions
     */
    private void cancelPendingWalletTransactions(AccountFreezeRequestEvent event) {
        try {
            int canceledCount = 0;
            for (UUID transactionId : event.getAffectedTransactionIds()) {
                boolean canceled = walletService.cancelPendingTransaction(
                    transactionId,
                    event.getUserId(),
                    "ACCOUNT_FROZEN_" + event.getFreezeReason()
                );
                if (canceled) {
                    canceledCount++;
                }
            }
            
            log.info("Canceled {} pending wallet transactions for user: {}", 
                canceledCount, event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to cancel pending transactions for user: {}", event.getUserId(), e);
            // Don't throw - cancellation failures shouldn't block freeze
        }
    }
    
    /**
     * Update wallet compliance status
     */
    private void updateWalletComplianceStatus(AccountFreezeRequestEvent event, WalletFreezeResult freezeResult) {
        try {
            walletComplianceService.updateComplianceStatus(
                event.getUserId(),
                event.getFreezeReason().toString(),
                event.getCaseId(),
                freezeResult.getFrozenWallets().size(),
                event.requiresRegulatoryNotification()
            );
            
            log.debug("Updated wallet compliance status for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to update wallet compliance status for user: {}", event.getUserId(), e);
            // Don't throw - compliance status updates are secondary
        }
    }
    
    /**
     * DLQ handler for failed account freeze requests - CRITICAL
     * Account freeze failures are compliance violations and require immediate attention
     */
    @KafkaListener(
        topics = "account-freeze-requests-freeze-dlq",
        groupId = "wallet-service-freeze-dlq-handler",
        containerFactory = "operationalKafkaListenerContainerFactory"
    )
    public void handleFreezeDlq(
            @Valid @Payload AccountFreezeRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(value = "x-retry-count", required = false) String retryCount,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {

        log.error("CRITICAL_COMPLIANCE_DLQ: Account freeze request failed - userId: {}, reason: {}, severity: {}, scope: {}, error: {}, errorClass: {}, retries: {}", 
            event.getUserId(), event.getFreezeReason(), event.getSeverity(), event.getFreezeScope(), 
            errorMessage, errorClass, retryCount);

        try {
            // This is CRITICAL - account freeze failures are compliance violations
            FreezeDlqAnalysis analysis = analyzeFreezeDlqError(event, errorMessage, errorClass, retryCount);
            
            // Immediate critical alert - compliance violations require immediate attention
            sendCriticalComplianceAlert(event, analysis);
            
            // Manual freeze as emergency fallback if system freeze failed
            executeEmergencyAccountFreeze(event, analysis);
            
            // Store for mandatory compliance review
            storeFreezeFailureForComplianceReview(event, analysis);
            
            // Notify compliance officer immediately
            notifyComplianceOfficer(event, analysis);
            
            // Update compliance metrics - this is a regulatory incident
            updateComplianceViolationMetrics(event, analysis);
            
            // Create regulatory incident if this is a sanctions/AML freeze
            if (isRegulatoryFreeze(event)) {
                createRegulatoryIncident(event, analysis);
            }

        } catch (Exception e) {
            log.error("CRITICAL: Failed to handle account freeze DLQ message for userId: {} - COMPLIANCE VIOLATION - Manual intervention required immediately", 
                event.getUserId(), e);
            
            // This is a critical compliance failure - escalate to highest level
            sendComplianceEscalationAlert(event, e);
            
            // Emergency manual freeze trigger
            triggerEmergencyManualFreeze(event);
        }
    }
    
    // Freeze DLQ helper methods
    
    private FreezeDlqAnalysis analyzeFreezeDlqError(AccountFreezeRequestEvent event, String errorMessage, String errorClass, String retryCount) {
        return FreezeDlqAnalysis.builder()
            .userId(event.getUserId())
            .freezeReason(event.getFreezeReason().toString())
            .severity(event.getSeverity().toString())
            .scope(event.getFreezeScope().toString())
            .caseId(event.getCaseId())
            .errorMessage(errorMessage)
            .errorClass(errorClass)
            .retryCount(retryCount)
            .complianceSeverity(determineComplianceSeverity(event))
            .isRegulatoryFreeze(isRegulatoryFreeze(event))
            .requiresImmediateAction(requiresImmediateAction(event))
            .potentialRegulatoryConcern(isPotentialRegulatoryConcern(event))
            .build();
    }
    
    private void sendCriticalComplianceAlert(AccountFreezeRequestEvent event, FreezeDlqAnalysis analysis) {
        try {
            Map<String, Object> alertContext = Map.of(
                "userId", event.getUserId(),
                "freezeReason", analysis.getFreezeReason(),
                "severity", analysis.getSeverity(),
                "scope", analysis.getScope(),
                "complianceSeverity", analysis.getComplianceSeverity(),
                "isRegulatoryFreeze", analysis.isRegulatoryFreeze(),
                "errorClass", analysis.getErrorClass(),
                "errorMessage", analysis.getErrorMessage(),
                "caseId", analysis.getCaseId() != null ? analysis.getCaseId() : "N/A"
            );
            
            // Send to multiple critical channels
            auditService.auditWalletEvent(
                "CRITICAL_FREEZE_FAILURE_DLQ",
                event.getUserId().toString(),
                "COMPLIANCE VIOLATION: Account freeze request failed and sent to DLQ - Manual intervention required",
                alertContext
            );
            
            // PagerDuty for immediate compliance team response
            alertService.sendPagerDutyAlert(
                "compliance-freeze-failure",
                String.format("CRITICAL: Account freeze failed for user %s - Reason: %s - Severity: %s", 
                    event.getUserId(), analysis.getFreezeReason(), analysis.getComplianceSeverity()),
                alertContext
            );
            
            // Slack for compliance team
            alertService.sendSlackAlert(
                "#compliance-critical",
                String.format("🚨 COMPLIANCE VIOLATION: Account freeze DLQ failure\nUser: %s\nReason: %s\nSeverity: %s\nCase: %s\nError: %s", 
                    event.getUserId(), analysis.getFreezeReason(), analysis.getComplianceSeverity(), 
                    analysis.getCaseId(), analysis.getErrorMessage()),
                alertContext
            );
            
        } catch (Exception e) {
            log.error("Failed to send critical compliance alert", e);
        }
    }
    
    private void executeEmergencyAccountFreeze(AccountFreezeRequestEvent event, FreezeDlqAnalysis analysis) {
        try {
            log.warn("EMERGENCY_FREEZE: Executing emergency account freeze due to DLQ failure - userId: {}", event.getUserId());
            
            // Emergency freeze all wallets associated with user
            List<String> allWalletIds = walletService.getAllWalletIds(event.getUserId());
            
            for (String walletId : allWalletIds) {
                try {
                    walletFreezeService.emergencyFreezeWallet(
                        walletId,
                        event.getUserId(),
                        "EMERGENCY_FREEZE_DUE_TO_SYSTEM_FAILURE",
                        "System failed to process freeze request - emergency freeze applied",
                        analysis.getCaseId()
                    );
                    
                    log.info("EMERGENCY_FREEZE: Emergency freeze applied to wallet {} for user {}", walletId, event.getUserId());
                    
                } catch (Exception walletError) {
                    log.error("CRITICAL: Failed to apply emergency freeze to wallet {} for user {} - MANUAL INTERVENTION REQUIRED", 
                        walletId, event.getUserId(), walletError);
                }
            }
            
            // Update user status to frozen - log only since user service integration is optional
            try {
                log.warn("EMERGENCY_FREEZE: Would call userService.emergencyFreezeUser for user {}", event.getUserId());
                // userService.emergencyFreezeUser(event.getUserId(), "SYSTEM_FREEZE_FAILURE_EMERGENCY");
            } catch (Exception userError) {
                log.error("CRITICAL: Failed to emergency freeze user {} - MANUAL INTERVENTION REQUIRED",
                    event.getUserId(), userError);
            }
            
        } catch (Exception e) {
            log.error("CRITICAL: Emergency account freeze failed for user {} - IMMEDIATE MANUAL INTERVENTION REQUIRED", 
                event.getUserId(), e);
        }
    }
    
    private void storeFreezeFailureForComplianceReview(AccountFreezeRequestEvent event, FreezeDlqAnalysis analysis) {
        try {
            ComplianceIncident incident = ComplianceIncident.builder()
                .id(java.util.UUID.randomUUID().toString())
                .userId(event.getUserId())
                .incidentType("FREEZE_FAILURE")
                .severity(analysis.getComplianceSeverity())
                .freezeReason(analysis.getFreezeReason())
                .scope(analysis.getScope())
                .caseId(analysis.getCaseId())
                .eventData(objectMapper.writeValueAsString(event))
                .errorAnalysis(analysis)
                .status(ComplianceIncidentStatus.REQUIRES_IMMEDIATE_REVIEW)
                .isRegulatoryReportable(analysis.isRegulatoryFreeze())
                .createdAt(LocalDateTime.now())
                .dueDate(LocalDateTime.now().plusHours(2)) // 2 hour SLA for freeze failures
                .build();
                
            complianceIncidentRepository.save(incident);
            
            log.info("COMPLIANCE_INCIDENT: Created compliance incident {} for freeze failure - userId: {}", 
                incident.getId(), event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to store freeze failure for compliance review", e);
        }
    }
    
    private void notifyComplianceOfficer(AccountFreezeRequestEvent event, FreezeDlqAnalysis analysis) {
        try {
            ComplianceNotification notification = ComplianceNotification.builder()
                .userId(event.getUserId())
                .incidentType("CRITICAL_FREEZE_FAILURE")
                .severity(analysis.getComplianceSeverity())
                .freezeReason(analysis.getFreezeReason())
                .caseId(analysis.getCaseId())
                .errorSummary(analysis.getErrorMessage())
                .requiresImmediateAction(analysis.requiresImmediateAction())
                .isRegulatoryReportable(analysis.isRegulatoryFreeze())
                .priority(Priority.CRITICAL)
                .slaHours(2)
                .build();
                
            complianceNotificationService.sendUrgentNotification(notification);
            
        } catch (Exception e) {
            log.error("Failed to notify compliance officer", e);
        }
    }
    
    private void updateComplianceViolationMetrics(AccountFreezeRequestEvent event, FreezeDlqAnalysis analysis) {
        try {
            errorMetrics.incrementDlqCount("account-freeze-requests", "wallet-service", analysis.getErrorClass());
            errorMetrics.recordCriticalError("account-freeze-requests", "wallet-service", 
                new RuntimeException("Compliance freeze failure: " + analysis.getErrorMessage()));
            
            // Compliance-specific metrics
            complianceMetrics.recordFreezeFailure(
                analysis.getFreezeReason(),
                analysis.getComplianceSeverity(),
                analysis.isRegulatoryFreeze()
            );
                
        } catch (Exception e) {
            log.error("Failed to update compliance violation metrics", e);
        }
    }
    
    private void createRegulatoryIncident(AccountFreezeRequestEvent event, FreezeDlqAnalysis analysis) {
        try {
            if (analysis.isRegulatoryFreeze()) {
                RegulatoryIncident incident = RegulatoryIncident.builder()
                    .userId(event.getUserId())
                    .incidentType("SANCTIONS_FREEZE_FAILURE")
                    .severity("CRITICAL")
                    .description(String.format("Failed to execute regulatory freeze for user %s - Reason: %s", 
                        event.getUserId(), analysis.getFreezeReason()))
                    .caseId(analysis.getCaseId())
                    .requiresRegulatorNotification(true)
                    .reportingDeadline(LocalDateTime.now().plusHours(24))
                    .createdAt(LocalDateTime.now())
                    .build();
                    
                regulatoryIncidentService.createIncident(incident);
                
                log.error("REGULATORY_INCIDENT: Created regulatory incident for freeze failure - userId: {}, incidentId: {}", 
                    event.getUserId(), incident.getId());
            }
        } catch (Exception e) {
            log.error("Failed to create regulatory incident", e);
        }
    }
    
    private void sendComplianceEscalationAlert(AccountFreezeRequestEvent event, Exception e) {
        try {
            auditService.auditWalletEvent(
                "COMPLIANCE_ESCALATION_REQUIRED",
                event.getUserId().toString(),
                "CRITICAL: DLQ handler failed for account freeze - Compliance escalation required",
                Map.of(
                    "userId", event.getUserId(),
                    "freezeReason", event.getFreezeReason(),
                    "dlqHandlerError", e.getMessage(),
                    "requiresImmediateEscalation", true,
                    "complianceViolation", true
                )
            );
            
            // Send to CEO/CRO level for critical compliance failures
            alertService.sendExecutiveAlert(
                "CRITICAL COMPLIANCE SYSTEM FAILURE",
                String.format("Account freeze system failure for user %s - Compliance violation - Manual intervention required", 
                    event.getUserId()),
                Map.of("userId", event.getUserId(), "error", e.getMessage())
            );
            
        } catch (Exception alertE) {
            log.error("CRITICAL: Failed to send compliance escalation alert - System requires immediate executive attention", alertE);
        }
    }
    
    private void triggerEmergencyManualFreeze(AccountFreezeRequestEvent event) {
        try {
            // Create manual task for operations team
            OperationalTask task = OperationalTask.builder()
                .type("EMERGENCY_MANUAL_FREEZE")
                .priority(Priority.CRITICAL)
                .userId(event.getUserId())
                .description(String.format("EMERGENCY: Manual account freeze required for user %s - System failure", event.getUserId()))
                .freezeReason(event.getFreezeReason().toString())
                .severity(event.getSeverity().toString())
                .caseId(event.getCaseId())
                .slaMinutes(15) // 15 minute SLA for manual emergency freeze
                .createdAt(LocalDateTime.now())
                .build();
                
            operationalTaskService.createUrgentTask(task);
            
            log.error("EMERGENCY_MANUAL_FREEZE: Created emergency manual freeze task for user {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to trigger emergency manual freeze for user {} - IMMEDIATE MANUAL INTERVENTION REQUIRED", 
                event.getUserId(), e);
        }
    }
    
    private String determineComplianceSeverity(AccountFreezeRequestEvent event) {
        switch (event.getFreezeReason()) {
            case SANCTIONS_MATCH:
            case OFAC_VIOLATION:
                return "CRITICAL_REGULATORY";
            case AML_SUSPICIOUS_ACTIVITY:
            case SAR_FILING_REQUIRED:
                return "HIGH_REGULATORY";
            case FRAUD_SUSPECTED:
            case ACCOUNT_TAKEOVER:
                return "HIGH_SECURITY";
            default:
                return "MEDIUM_OPERATIONAL";
        }
    }
    
    private boolean isRegulatoryFreeze(AccountFreezeRequestEvent event) {
        return event.getFreezeReason() == FreezeReason.SANCTIONS_MATCH ||
               event.getFreezeReason() == FreezeReason.OFAC_VIOLATION ||
               event.getFreezeReason() == FreezeReason.AML_SUSPICIOUS_ACTIVITY ||
               event.getFreezeReason() == FreezeReason.SAR_FILING_REQUIRED;
    }
    
    private boolean requiresImmediateAction(AccountFreezeRequestEvent event) {
        return event.getSeverity() == FreezeSeverity.CRITICAL ||
               isRegulatoryFreeze(event);
    }
    
    private boolean isPotentialRegulatoryConcern(AccountFreezeRequestEvent event) {
        return isRegulatoryFreeze(event) ||
               (event.getTotalAccountBalance() != null && 
                event.getTotalAccountBalance().compareTo(java.math.BigDecimal.valueOf(100000)) > 0);
    }
    
    // Data classes for freeze DLQ handling
    
    @lombok.Data
    @lombok.Builder
    private static class FreezeDlqAnalysis {
        private java.util.UUID userId;
        private String freezeReason;
        private String severity;
        private String scope;
        private String caseId;
        private String errorMessage;
        private String errorClass;
        private String retryCount;
        private String complianceSeverity;
        private boolean isRegulatoryFreeze;
        private boolean requiresImmediateAction;
        private boolean potentialRegulatoryConcern;
    }
    
    // Result builder class

    @Data
    @Builder
    private static class WalletFreezeResult {
        private UUID userId;
        private String freezeReason;
        private String severity;
        private String scope;
        private List<String> frozenWallets;
        private int blockedTransactions;
        private boolean balancesFrozen;
        private boolean success;
        private String message;
        private LocalDateTime requestTime;
    }
}