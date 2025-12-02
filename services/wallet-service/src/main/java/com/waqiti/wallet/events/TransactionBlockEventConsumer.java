package com.waqiti.wallet.events;

import com.waqiti.common.events.TransactionBlockEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.TransactionBlockService;
import com.waqiti.wallet.service.ComplianceNotificationService;
import com.waqiti.common.audit.AuditService;
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

/**
 * Critical Event Consumer for Transaction Blocks
 * 
 * BUSINESS IMPACT: Prevents regulatory fines by enforcing AML/sanctions compliance
 * COMPLIANCE IMPACT: Ensures immediate blocking of prohibited transactions
 * 
 * This consumer was identified as MISSING in the forensic audit, causing:
 * - Sanctions violations going unblocked
 * - AML compliance failures
 * - Potential regulatory fines
 * - Failed transaction blocking automation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionBlockEventConsumer {
    
    private final TransactionBlockService transactionBlockService;
    private final ComplianceNotificationService complianceNotificationService;
    private final AuditService auditService;
    private final UniversalDLQHandler dlqHandler;
    
    /**
     * CRITICAL: Process transaction block events to maintain compliance
     * 
     * This consumer handles transaction blocking requests from compliance services
     * and ensures immediate transaction prevention
     */
    @KafkaListener(
        topics = "transaction-blocks",
        groupId = "wallet-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleTransactionBlock(
            @Payload TransactionBlockEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.warn("COMPLIANCE: Processing transaction block for transaction {} with severity {} from partition {}, offset {}", 
            event.getTransactionId(), event.getSeverity(), partition, offset);
        
        try {
            // Audit the transaction block event reception
            auditService.auditComplianceEvent(
                "TRANSACTION_BLOCK_RECEIVED",
                event.getUserId(),
                "Transaction block processed for transaction: " + event.getTransactionId(),
                event
            );
            
            // Execute the transaction block based on severity
            executeTransactionBlock(event);
            
            // Send compliance notifications if required
            sendComplianceNotifications(event);
            
            // Update compliance case tracking
            updateComplianceTracking(event);
            
            // Log successful processing
            log.warn("COMPLIANCE: Successfully blocked transaction {} for reason: {}", 
                event.getTransactionId(), event.getBlockReason());
            
            // Acknowledge the message after successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process transaction block for transaction {}", event.getTransactionId(), e);

            // Audit the failure
            auditService.auditComplianceEvent(
                "TRANSACTION_BLOCK_FAILED",
                event.getUserId(),
                "Failed to block transaction: " + e.getMessage(),
                event
            );

            dlqHandler.handleFailedMessage(event, topic, partition, offset, e)
                .thenAccept(result -> log.info("Transaction block event sent to DLQ: transactionId={}, destination={}, category={}",
                        event.getTransactionId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for transaction block event - MESSAGE MAY BE LOST! " +
                            "transactionId={}, partition={}, offset={}, error={}",
                            event.getTransactionId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            // Don't acknowledge - let the message be retried or sent to DLQ
            throw new RuntimeException("Transaction block processing failed", e);
        }
    }
    
    /**
     * Execute transaction block based on severity and reason
     */
    private void executeTransactionBlock(TransactionBlockEvent event) {
        switch (event.getSeverity()) {
            case CRITICAL:
                handleCriticalBlock(event);
                break;
            case HIGH:
                handleHighSeverityBlock(event);
                break;
            case MEDIUM:
                handleMediumSeverityBlock(event);
                break;
            case LOW:
                handleLowSeverityBlock(event);
                break;
            default:
                log.warn("Unknown block severity: {}", event.getSeverity());
                handleMediumSeverityBlock(event); // Default to medium
        }
    }
    
    /**
     * Handle critical blocks - immediate action with regulatory notification
     */
    private void handleCriticalBlock(TransactionBlockEvent event) {
        log.error("CRITICAL COMPLIANCE: Blocking transaction {} immediately - {}", 
            event.getTransactionId(), event.getBlockReason());
        
        try {
            // Immediately block the transaction
            String blockId = transactionBlockService.blockTransactionImmediately(
                event.getTransactionId(),
                event.getBlockReason().toString(),
                event.getBlockDescription(),
                event.getComplianceViolations()
            );
            
            // Freeze related accounts if sanctions match
            if (event.getBlockReason() == TransactionBlockEvent.BlockReason.SANCTIONS_MATCH) {
                transactionBlockService.freezeRelatedAccounts(
                    event.getUserId(),
                    event.getRecipientId(),
                    "SANCTIONS_FREEZE",
                    event.getSanctionsListMatch()
                );
            }
            
            // Block all pending transactions for the user
            transactionBlockService.blockAllPendingTransactions(
                event.getUserId(),
                event.getBlockReason().toString()
            );
            
            // Send immediate regulatory notification
            if (event.isNotifyRegulators()) {
                complianceNotificationService.sendRegulatoryNotification(event, blockId);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle critical transaction block for transaction {}", event.getTransactionId(), e);
            throw e;
        }
    }
    
    /**
     * Handle high severity blocks - enhanced compliance actions
     */
    private void handleHighSeverityBlock(TransactionBlockEvent event) {
        log.warn("HIGH SEVERITY COMPLIANCE: Blocking transaction {} - {}", 
            event.getTransactionId(), event.getBlockReason());
        
        try {
            // Block the transaction with enhanced monitoring
            String blockId = transactionBlockService.blockTransactionWithMonitoring(
                event.getTransactionId(),
                event.getBlockReason().toString(),
                event.getBlockDescription(),
                event.getComplianceViolations()
            );
            
            // Apply temporary account restrictions
            transactionBlockService.applyTemporaryRestrictions(
                event.getUserId(),
                event.getBlockReason().toString(),
                LocalDateTime.now().plusDays(7) // 7 days restriction
            );
            
            // Flag account for compliance review
            transactionBlockService.flagAccountForReview(
                event.getUserId(),
                event.getCaseId(),
                event.getBlockReason().toString()
            );
            
        } catch (Exception e) {
            log.error("Failed to handle high severity transaction block for transaction {}", event.getTransactionId(), e);
            throw e;
        }
    }
    
    /**
     * Handle medium severity blocks - standard compliance actions
     */
    private void handleMediumSeverityBlock(TransactionBlockEvent event) {
        log.warn("MEDIUM SEVERITY COMPLIANCE: Blocking transaction {} - {}", 
            event.getTransactionId(), event.getBlockReason());
        
        try {
            // Block the specific transaction
            String blockId = transactionBlockService.blockTransaction(
                event.getTransactionId(),
                event.getBlockReason().toString(),
                event.getBlockDescription()
            );
            
            // Create compliance case for review
            if (event.isRequiresManualReview()) {
                transactionBlockService.createComplianceCase(
                    event.getUserId(),
                    event.getTransactionId(),
                    event.getBlockReason().toString(),
                    event.getCaseId()
                );
            }
            
        } catch (Exception e) {
            log.error("Failed to handle medium severity transaction block for transaction {}", event.getTransactionId(), e);
            // Don't rethrow for medium severity - log and continue
        }
    }
    
    /**
     * Handle low severity blocks - monitoring with optional blocking
     */
    private void handleLowSeverityBlock(TransactionBlockEvent event) {
        log.info("LOW SEVERITY COMPLIANCE: Monitoring transaction {} - {}", 
            event.getTransactionId(), event.getBlockReason());
        
        try {
            // For temporary blocks, just delay the transaction
            if (event.isTemporaryBlock()) {
                transactionBlockService.delayTransaction(
                    event.getTransactionId(),
                    event.getBlockExpiresAt(),
                    event.getBlockReason().toString()
                );
            } else {
                // Block with possibility of auto-resolution
                transactionBlockService.blockTransactionWithAutoReview(
                    event.getTransactionId(),
                    event.getBlockReason().toString(),
                    event.getBlockDescription()
                );
            }
            
        } catch (Exception e) {
            log.error("Failed to handle low severity transaction block for transaction {}", event.getTransactionId(), e);
            // Don't rethrow for low severity
        }
    }
    
    /**
     * Send appropriate compliance notifications
     */
    private void sendComplianceNotifications(TransactionBlockEvent event) {
        try {
            switch (event.getSeverity()) {
                case CRITICAL:
                    complianceNotificationService.sendExecutiveAlert(event);
                    complianceNotificationService.sendComplianceTeamAlert(event);
                    if (event.isNotifyRegulators()) {
                        complianceNotificationService.sendRegulatoryNotification(event, null);
                    }
                    break;
                case HIGH:
                    complianceNotificationService.sendComplianceTeamAlert(event);
                    break;
                case MEDIUM:
                    if (event.isRequiresManualReview()) {
                        complianceNotificationService.sendComplianceReviewNotification(event);
                    }
                    break;
                case LOW:
                    // No immediate notifications for low-severity blocks
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to send compliance notifications for transaction block {}", event.getTransactionId(), e);
            // Don't rethrow - notification failures shouldn't block transaction blocking
        }
    }
    
    /**
     * Update compliance case tracking systems
     */
    private void updateComplianceTracking(TransactionBlockEvent event) {
        try {
            transactionBlockService.updateComplianceTracking(
                event.getCaseId(),
                event.getTransactionId(),
                event.getBlockReason().toString(),
                event.getSeverity().toString(),
                event.getComplianceOfficerId()
            );
            
            // Log compliance metrics
            log.info("Updated compliance tracking for case {} and transaction {}", 
                event.getCaseId(), event.getTransactionId());
                
        } catch (Exception e) {
            log.error("Failed to update compliance tracking for transaction {}", event.getTransactionId(), e);
            // Don't rethrow - tracking updates are important but shouldn't block immediate actions
        }
    }
}