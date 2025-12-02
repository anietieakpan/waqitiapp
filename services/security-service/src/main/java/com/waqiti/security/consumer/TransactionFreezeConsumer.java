package com.waqiti.security.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.AccountSecurityService;
import com.waqiti.security.service.TransactionSecurityService;
import com.waqiti.security.service.FraudDetectionService;
import com.waqiti.security.entity.FreezeRequest;
import com.waqiti.security.entity.FreezeReason;
import com.waqiti.security.entity.FreezeStatus;
import com.waqiti.security.entity.SecurityAlert;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Critical Kafka consumer for processing transaction freeze requests
 * Handles security-driven account and transaction freezing for fraud prevention
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionFreezeConsumer {

    private final AccountSecurityService accountSecurityService;
    private final TransactionSecurityService transactionSecurityService;
    private final FraudDetectionService fraudDetectionService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 500L, multiplier = 2.0), // Faster retry for security events
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = "transaction-freeze-requests", groupId = "security-service-freeze-group")
    public void processTransactionFreezeRequest(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Processing transaction freeze request from topic: {}, partition: {}", topic, partition);
            
            // Parse freeze request event
            TransactionFreezeEvent event = objectMapper.readValue(payload, TransactionFreezeEvent.class);
            
            // Validate event
            validateFreezeEvent(event);
            
            // Process based on freeze action
            switch (event.getAction()) {
                case "IMMEDIATE_FREEZE":
                    handleImmediateFreeze(event);
                    break;
                case "ACCOUNT_FREEZE":
                    handleAccountFreeze(event);
                    break;
                case "TRANSACTION_TYPE_FREEZE":
                    handleTransactionTypeFreeze(event);
                    break;
                case "TEMPORARY_FREEZE":
                    handleTemporaryFreeze(event);
                    break;
                case "UNFREEZE":
                    handleUnfreeze(event);
                    break;
                case "EMERGENCY_FREEZE":
                    handleEmergencyFreeze(event);
                    break;
                default:
                    log.warn("Unknown freeze action: {}", event.getAction());
            }
            
            log.info("Successfully processed freeze request for user: {}, action: {}", 
                event.getUserId(), event.getAction());
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing transaction freeze request: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process transaction freeze request", e);
        }
    }

    private void validateFreezeEvent(TransactionFreezeEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getAction() == null || event.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Freeze action is required");
        }
        
        if (event.getReason() == null || event.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Freeze reason is required");
        }
        
        if (event.getInitiatedBy() == null || event.getInitiatedBy().trim().isEmpty()) {
            throw new IllegalArgumentException("Initiator is required");
        }
    }

    private void handleImmediateFreeze(TransactionFreezeEvent event) {
        log.warn("Processing IMMEDIATE FREEZE for user: {}, reason: {}", event.getUserId(), event.getReason());
        
        try {
            // Create freeze request record
            FreezeRequest freezeRequest = createFreezeRequest(event, FreezeStatus.ACTIVE);
            
            // Immediately freeze all transaction types for the user
            transactionSecurityService.freezeAllTransactions(UUID.fromString(event.getUserId()), 
                event.getReason(), event.getInitiatedBy());
            
            // Freeze account operations
            accountSecurityService.freezeAccountOperations(UUID.fromString(event.getUserId()), 
                event.getReason(), event.getInitiatedBy());
            
            // Block new sessions
            accountSecurityService.blockNewSessions(UUID.fromString(event.getUserId()), 
                "Immediate freeze: " + event.getReason());
            
            // Send high-priority security alerts
            sendSecurityAlert(event, "IMMEDIATE_FREEZE_APPLIED", "HIGH");
            
            // Notify security team immediately
            notifySecurityTeam(event, "IMMEDIATE_FREEZE", freezeRequest);
            
            // Log security event
            logSecurityEvent(event, "IMMEDIATE_FREEZE_SUCCESS");
            
            log.warn("Immediate freeze applied successfully for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Error applying immediate freeze for user {}: {}", 
                event.getUserId(), e.getMessage(), e);
            
            // Send failure alert
            sendSecurityAlert(event, "IMMEDIATE_FREEZE_FAILED", "CRITICAL");
            throw new RuntimeException("Failed to apply immediate freeze", e);
        }
    }

    private void handleAccountFreeze(TransactionFreezeEvent event) {
        log.info("Processing account freeze for user: {}, reason: {}", event.getUserId(), event.getReason());
        
        try {
            // Create freeze request record
            FreezeRequest freezeRequest = createFreezeRequest(event, FreezeStatus.ACTIVE);
            
            // Freeze account based on specific criteria
            if (event.getAccountIds() != null && !event.getAccountIds().isEmpty()) {
                // Freeze specific accounts
                for (String accountId : event.getAccountIds()) {
                    accountSecurityService.freezeSpecificAccount(UUID.fromString(accountId), 
                        event.getReason(), event.getInitiatedBy());
                }
            } else {
                // Freeze all accounts for the user
                accountSecurityService.freezeUserAccounts(UUID.fromString(event.getUserId()), 
                    event.getReason(), event.getInitiatedBy());
            }
            
            // Apply transaction restrictions
            transactionSecurityService.applyTransactionRestrictions(UUID.fromString(event.getUserId()), 
                event.getTransactionTypes(), event.getReason());
            
            // Send notifications
            sendSecurityAlert(event, "ACCOUNT_FREEZE_APPLIED", "MEDIUM");
            
            // Update fraud detection models with freeze event
            fraudDetectionService.recordSecurityEvent(UUID.fromString(event.getUserId()), 
                "ACCOUNT_FREEZE", event.getReason(), event.getRiskScore());
            
            log.info("Account freeze applied successfully for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Error applying account freeze for user {}: {}", 
                event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to apply account freeze", e);
        }
    }

    private void handleTransactionTypeFreeze(TransactionFreezeEvent event) {
        log.info("Processing transaction type freeze for user: {}, types: {}", 
            event.getUserId(), event.getTransactionTypes());
        
        try {
            // Create freeze request record
            FreezeRequest freezeRequest = createFreezeRequest(event, FreezeStatus.ACTIVE);
            
            // Freeze specific transaction types
            if (event.getTransactionTypes() != null && !event.getTransactionTypes().isEmpty()) {
                for (String transactionType : event.getTransactionTypes()) {
                    transactionSecurityService.freezeTransactionType(UUID.fromString(event.getUserId()), 
                        transactionType, event.getReason(), event.getInitiatedBy());
                }
            }
            
            // Apply amount limits if specified
            if (event.getMaxAmount() != null) {
                transactionSecurityService.applyAmountLimits(UUID.fromString(event.getUserId()), 
                    event.getMaxAmount(), event.getReason());
            }
            
            // Send notifications
            sendSecurityAlert(event, "TRANSACTION_TYPE_FREEZE_APPLIED", "LOW");
            
            log.info("Transaction type freeze applied successfully for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Error applying transaction type freeze for user {}: {}", 
                event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to apply transaction type freeze", e);
        }
    }

    private void handleTemporaryFreeze(TransactionFreezeEvent event) {
        log.info("Processing temporary freeze for user: {}, duration: {} minutes", 
            event.getUserId(), event.getDurationMinutes());
        
        try {
            // Create temporary freeze request record
            FreezeRequest freezeRequest = createFreezeRequest(event, FreezeStatus.TEMPORARY);
            
            // Apply temporary freeze with automatic expiration
            LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(event.getDurationMinutes());
            
            transactionSecurityService.applyTemporaryFreeze(UUID.fromString(event.getUserId()), 
                event.getReason(), expirationTime, event.getInitiatedBy());
            
            // Schedule automatic unfreeze
            transactionSecurityService.scheduleAutomaticUnfreeze(UUID.fromString(event.getUserId()), 
                expirationTime, freezeRequest.getId());
            
            // Send notifications
            sendSecurityAlert(event, "TEMPORARY_FREEZE_APPLIED", "LOW");
            
            log.info("Temporary freeze applied successfully for user: {}, expires at: {}", 
                event.getUserId(), expirationTime);
            
        } catch (Exception e) {
            log.error("Error applying temporary freeze for user {}: {}", 
                event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to apply temporary freeze", e);
        }
    }

    private void handleUnfreeze(TransactionFreezeEvent event) {
        log.info("Processing unfreeze request for user: {}, reason: {}", 
            event.getUserId(), event.getReason());
        
        try {
            // Find active freeze requests to resolve
            List<FreezeRequest> activeFreezes = accountSecurityService.getActiveFreezeRequests(
                UUID.fromString(event.getUserId()));
            
            // Remove transaction freezes
            if (event.getFreezeId() != null) {
                // Specific freeze removal
                transactionSecurityService.removeSpecificFreeze(UUID.fromString(event.getFreezeId()), 
                    event.getReason(), event.getInitiatedBy());
            } else {
                // Remove all freezes
                transactionSecurityService.removeAllFreezes(UUID.fromString(event.getUserId()), 
                    event.getReason(), event.getInitiatedBy());
            }
            
            // Restore account operations
            accountSecurityService.restoreAccountOperations(UUID.fromString(event.getUserId()), 
                event.getReason(), event.getInitiatedBy());
            
            // Restore session access
            accountSecurityService.restoreSessionAccess(UUID.fromString(event.getUserId()));
            
            // Update freeze request records
            for (FreezeRequest freeze : activeFreezes) {
                if (event.getFreezeId() == null || freeze.getId().toString().equals(event.getFreezeId())) {
                    freeze.setStatus(FreezeStatus.RESOLVED);
                    freeze.setResolvedAt(LocalDateTime.now());
                    freeze.setResolvedBy(event.getInitiatedBy());
                    freeze.setResolutionReason(event.getReason());
                    accountSecurityService.updateFreezeRequest(freeze);
                }
            }
            
            // Send notifications
            sendSecurityAlert(event, "FREEZE_REMOVED", "LOW");
            
            log.info("Unfreeze completed successfully for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Error processing unfreeze for user {}: {}", 
                event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process unfreeze", e);
        }
    }

    private void handleEmergencyFreeze(TransactionFreezeEvent event) {
        log.error("Processing EMERGENCY FREEZE for user: {}, reason: {}", 
            event.getUserId(), event.getReason());
        
        try {
            // Create emergency freeze request record
            FreezeRequest freezeRequest = createFreezeRequest(event, FreezeStatus.EMERGENCY);
            
            // Apply immediate comprehensive freeze
            transactionSecurityService.applyEmergencyFreeze(UUID.fromString(event.getUserId()), 
                event.getReason(), event.getInitiatedBy());
            
            // Block all account access
            accountSecurityService.emergencyAccountBlock(UUID.fromString(event.getUserId()), 
                event.getReason(), event.getInitiatedBy());
            
            // Revoke all active sessions
            accountSecurityService.revokeAllActiveSessions(UUID.fromString(event.getUserId()));
            
            // Send critical security alerts
            sendSecurityAlert(event, "EMERGENCY_FREEZE_APPLIED", "CRITICAL");
            
            // Immediately notify security team and management
            notifySecurityTeam(event, "EMERGENCY_FREEZE", freezeRequest);
            notifyManagement(event, "EMERGENCY_FREEZE", freezeRequest);
            
            // Log critical security event
            logSecurityEvent(event, "EMERGENCY_FREEZE_SUCCESS");
            
            log.error("Emergency freeze applied successfully for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Error applying emergency freeze for user {}: {}", 
                event.getUserId(), e.getMessage(), e);
            
            // Send failure alert to security team immediately
            sendSecurityAlert(event, "EMERGENCY_FREEZE_FAILED", "CRITICAL");
            throw new RuntimeException("Failed to apply emergency freeze", e);
        }
    }

    private FreezeRequest createFreezeRequest(TransactionFreezeEvent event, FreezeStatus status) {
        FreezeRequest request = new FreezeRequest();
        request.setId(UUID.randomUUID());
        request.setUserId(UUID.fromString(event.getUserId()));
        request.setReason(FreezeReason.fromString(event.getReason()));
        request.setStatus(status);
        request.setInitiatedBy(event.getInitiatedBy());
        request.setInitiatedAt(LocalDateTime.now());
        request.setDescription(event.getDescription());
        request.setRiskScore(event.getRiskScore());
        
        if (event.getDurationMinutes() != null && event.getDurationMinutes() > 0) {
            request.setExpirationTime(LocalDateTime.now().plusMinutes(event.getDurationMinutes()));
        }
        
        return accountSecurityService.createFreezeRequest(request);
    }

    private void sendSecurityAlert(TransactionFreezeEvent event, String alertType, String severity) {
        try {
            SecurityAlert alert = new SecurityAlert();
            alert.setId(UUID.randomUUID());
            alert.setUserId(UUID.fromString(event.getUserId()));
            alert.setAlertType(alertType);
            alert.setSeverity(severity);
            alert.setDescription(String.format("Freeze action: %s, Reason: %s", event.getAction(), event.getReason()));
            alert.setInitiatedBy(event.getInitiatedBy());
            alert.setCreatedAt(LocalDateTime.now());
            
            accountSecurityService.createSecurityAlert(alert);
            
        } catch (Exception e) {
            log.error("Error sending security alert for user {}: {}", event.getUserId(), e.getMessage(), e);
        }
    }

    private void notifySecurityTeam(TransactionFreezeEvent event, String action, FreezeRequest freezeRequest) {
        // Implementation would send notifications to security team
        log.info("Security team notification sent for {} action on user: {}", action, event.getUserId());
    }

    private void notifyManagement(TransactionFreezeEvent event, String action, FreezeRequest freezeRequest) {
        // Implementation would send notifications to management for critical events
        log.info("Management notification sent for {} action on user: {}", action, event.getUserId());
    }

    private void logSecurityEvent(TransactionFreezeEvent event, String eventType) {
        // Implementation would log to security audit trail
        log.info("Security event logged: {} for user: {}", eventType, event.getUserId());
    }

    // Transaction freeze event data structure
    public static class TransactionFreezeEvent {
        private String userId;
        private String action;
        private String reason;
        private String description;
        private String initiatedBy;
        private String[] accountIds;
        private String[] transactionTypes;
        private String freezeId;
        private Integer durationMinutes;
        private Double maxAmount;
        private Double riskScore;
        private LocalDateTime timestamp;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getInitiatedBy() { return initiatedBy; }
        public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
        
        public String[] getAccountIds() { return accountIds; }
        public void setAccountIds(String[] accountIds) { this.accountIds = accountIds; }
        
        public String[] getTransactionTypes() { return transactionTypes; }
        public void setTransactionTypes(String[] transactionTypes) { this.transactionTypes = transactionTypes; }
        
        public String getFreezeId() { return freezeId; }
        public void setFreezeId(String freezeId) { this.freezeId = freezeId; }
        
        public Integer getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
        
        public Double getMaxAmount() { return maxAmount; }
        public void setMaxAmount(Double maxAmount) { this.maxAmount = maxAmount; }
        
        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}