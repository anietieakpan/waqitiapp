package com.waqiti.payment.kafka;

import com.waqiti.common.events.TransactionBlockEvent;
import com.waqiti.payment.service.PaymentProcessingService;
import com.waqiti.payment.service.TransactionSecurityService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.common.audit.AuditService;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * CRITICAL EVENT CONSUMER: Transaction Block Events
 * 
 * This consumer handles transaction blocking events from the wallet service that were 
 * previously orphaned, causing blocked transactions to continue processing despite
 * being flagged for blocking due to security, compliance, or risk concerns.
 * 
 * Key Responsibilities:
 * - Immediately block flagged transactions
 * - Update transaction status and prevent further processing
 * - Send notifications to customers and security team
 * - Create audit trails for blocked transactions
 * - Handle compliance reporting for blocked transactions
 * - Coordinate with upstream services to halt processing
 * 
 * Business Impact:
 * - Prevents unauthorized or fraudulent transactions from completing
 * - Maintains regulatory compliance for AML/BSA requirements
 * - Protects against financial losses from high-risk transactions
 * - Ensures customer accounts are protected from suspicious activity
 * 
 * @author Waqiti Security Team
 * @since 2.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionBlockEventConsumer {

    private final PaymentProcessingService paymentProcessingService;
    private final TransactionSecurityService transactionSecurityService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    
    private final Counter blockEventsProcessed;
    private final Counter transactionsBlocked;
    private final Counter blockProcessingErrors;

    public TransactionBlockEventConsumer(PaymentProcessingService paymentProcessingService,
                                       TransactionSecurityService transactionSecurityService,
                                       NotificationService notificationService,
                                       AuditService auditService,
                                       MeterRegistry meterRegistry) {
        this.paymentProcessingService = paymentProcessingService;
        this.transactionSecurityService = transactionSecurityService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.blockEventsProcessed = Counter.builder("transaction_block_events_processed")
            .description("Number of transaction block events processed")
            .register(meterRegistry);
            
        this.transactionsBlocked = Counter.builder("transactions_blocked")
            .description("Number of transactions successfully blocked")
            .register(meterRegistry);
            
        this.blockProcessingErrors = Counter.builder("block_processing_errors")
            .description("Number of transaction block processing errors")
            .register(meterRegistry);
    }

    /**
     * Processes transaction block events from the wallet service.
     * 
     * @param event The transaction block event
     * @param acknowledgment Kafka acknowledgment for manual commit
     * @param partition The Kafka partition
     * @param offset The message offset
     */
    @KafkaListener(
        topics = "transaction-blocks",
        groupId = "payment-block-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @Timed(value = "transaction_block_processing_duration", description = "Time to process transaction block event")
    public void handleTransactionBlockEvent(
            @Payload TransactionBlockEvent event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            log.warn("Processing transaction block event for transaction: {} reason: {} severity: {}", 
                event.getTransactionId(), event.getBlockReason(), event.getSeverity());
            
            // Validate event data
            validateTransactionBlockEvent(event);
            
            // Immediately block the transaction
            blockTransaction(event);
            
            // Update transaction security status
            updateSecurityStatus(event);
            
            // Send notifications based on severity
            sendNotifications(event);
            
            // Handle compliance reporting if required
            handleComplianceReporting(event);
            
            // Coordinate with upstream services
            coordinateUpstreamBlocking(event);
            
            // Update customer risk profile if applicable
            updateCustomerRiskProfile(event);
            
            // Audit the transaction blocking
            auditTransactionBlocking(event);
            
            // Update metrics
            blockEventsProcessed.increment();
            transactionsBlocked.increment();
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.warn("Successfully blocked transaction: {} for reason: {}", 
                event.getTransactionId(), event.getBlockReason());
            
        } catch (Exception e) {
            log.error("Error processing transaction block event for transaction: {}", event.getTransactionId(), e);
            
            // Update error metrics
            blockProcessingErrors.increment();
            
            // Handle processing error
            handleBlockProcessingError(event, e);
            
            // Acknowledge to prevent infinite reprocessing (error handled in DLQ)
            acknowledgment.acknowledge();
        }
    }

    private void validateTransactionBlockEvent(TransactionBlockEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required for transaction block event");
        }
        
        if (event.getBlockReason() == null || event.getBlockReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Block reason is required for transaction block event");
        }
        
        if (event.getSeverity() == null) {
            throw new IllegalArgumentException("Severity level is required for transaction block event");
        }
        
        if (event.getBlockedAt() == null) {
            throw new IllegalArgumentException("Block timestamp is required for transaction block event");
        }
    }

    private void blockTransaction(TransactionBlockEvent event) {
        try {
            // Determine block type based on severity
            String blockType = determineBlockType(event.getSeverity());
            
            // Block the transaction immediately
            boolean blocked = paymentProcessingService.blockTransaction(
                event.getTransactionId(),
                blockType,
                event.getBlockReason(),
                event.getBlockedAt(),
                event.getBlockedBy()
            );
            
            if (!blocked) {
                throw new RuntimeException("Failed to block transaction - transaction may have already been processed");
            }
            
            log.info("Transaction {} blocked with type: {} reason: {}", 
                event.getTransactionId(), blockType, event.getBlockReason());
            
        } catch (Exception e) {
            log.error("Failed to block transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Transaction blocking failed", e);
        }
    }

    private String determineBlockType(String severity) {
        switch (severity.toUpperCase()) {
            case "CRITICAL":
                return "IMMEDIATE_BLOCK";
            case "HIGH":
                return "SECURITY_BLOCK";
            case "MEDIUM":
                return "COMPLIANCE_BLOCK";
            case "LOW":
                return "PRECAUTIONARY_BLOCK";
            default:
                return "GENERAL_BLOCK";
        }
    }

    private void updateSecurityStatus(TransactionBlockEvent event) {
        try {
            transactionSecurityService.updateTransactionSecurityStatus(
                event.getTransactionId(),
                "BLOCKED",
                event.getBlockReason(),
                event.getSeverity(),
                event.getSecurityFlags(),
                event.getBlockedAt()
            );
            
            log.debug("Updated security status for blocked transaction: {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to update security status for transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Security status update failed", e);
        }
    }

    private void sendNotifications(TransactionBlockEvent event) {
        try {
            String severity = event.getSeverity();
            
            // Always notify customer for blocked transactions
            notificationService.sendTransactionBlockNotification(
                event.getCustomerId(),
                event.getTransactionId(),
                event.getBlockReason(),
                determineCustomerMessage(event.getBlockReason())
            );
            
            // Notify security team for medium+ severity
            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                notificationService.sendSecurityAlert(
                    "TRANSACTION_BLOCKED",
                    event.getTransactionId(),
                    event.getCustomerId(),
                    String.format("Transaction blocked - Severity: %s, Reason: %s", severity, event.getBlockReason()),
                    event.getSecurityFlags()
                );
            }
            
            // Notify compliance team for critical blocks
            if ("CRITICAL".equals(severity)) {
                notificationService.sendComplianceAlert(
                    event.getTransactionId(),
                    event.getCustomerId(),
                    event.getBlockReason(),
                    "Critical transaction blocked - immediate investigation required"
                );
            }
            
            log.debug("Sent notifications for blocked transaction: {} severity: {}", 
                event.getTransactionId(), severity);
            
        } catch (Exception e) {
            log.warn("Failed to send notifications for blocked transaction: {}", event.getTransactionId(), e);
            // Don't throw exception for notification failures
        }
    }

    private String determineCustomerMessage(String blockReason) {
        switch (blockReason.toUpperCase()) {
            case "FRAUD_DETECTED":
                return "Your transaction was blocked due to security concerns. Please contact support if this was a legitimate transaction.";
            case "COMPLIANCE_VIOLATION":
                return "Your transaction was blocked for compliance reasons. Please contact support for assistance.";
            case "VELOCITY_LIMIT_EXCEEDED":
                return "Your transaction was blocked due to velocity limits. Please try again later or contact support.";
            case "SUSPICIOUS_ACTIVITY":
                return "Your transaction was blocked due to unusual activity patterns. Please verify your identity and try again.";
            case "ACCOUNT_SECURITY":
                return "Your transaction was blocked for account security reasons. Please contact support immediately.";
            default:
                return "Your transaction was blocked for security reasons. Please contact support for assistance.";
        }
    }

    private void handleComplianceReporting(TransactionBlockEvent event) {
        try {
            String severity = event.getSeverity();
            String blockReason = event.getBlockReason();
            
            // Generate SAR for certain types of blocks
            if (requiresSARFiling(blockReason, severity)) {
                auditService.generateSuspiciousActivityReport(
                    event.getTransactionId(),
                    event.getCustomerId(),
                    "Transaction blocked for: " + blockReason,
                    event.getSecurityFlags(),
                    "Transaction blocked by automated security system"
                );
                
                log.info("Generated SAR for blocked transaction: {} reason: {}", 
                    event.getTransactionId(), blockReason);
            }
            
            // Generate compliance report for high-severity blocks
            if ("HIGH".equals(severity) || "CRITICAL".equals(severity)) {
                auditService.generateComplianceReport(
                    event.getTransactionId(),
                    event.getCustomerId(),
                    "TRANSACTION_BLOCKED",
                    blockReason,
                    LocalDateTime.now()
                );
                
                log.debug("Generated compliance report for blocked transaction: {}", event.getTransactionId());
            }
            
        } catch (Exception e) {
            log.warn("Failed to handle compliance reporting for blocked transaction: {}", event.getTransactionId(), e);
            // Don't throw exception for compliance reporting failures
        }
    }

    private boolean requiresSARFiling(String blockReason, String severity) {
        // SAR filing required for certain types of blocks
        String[] sarTriggers = {
            "FRAUD_DETECTED",
            "MONEY_LAUNDERING_SUSPECTED",
            "STRUCTURING_DETECTED",
            "SANCTIONS_VIOLATION",
            "TERRORIST_FINANCING"
        };
        
        for (String trigger : sarTriggers) {
            if (blockReason.toUpperCase().contains(trigger)) {
                return true;
            }
        }
        
        // Also file SAR for all critical severity blocks
        return "CRITICAL".equals(severity);
    }

    private void coordinateUpstreamBlocking(TransactionBlockEvent event) {
        try {
            // Notify upstream services to halt any processing
            paymentProcessingService.notifyUpstreamServicesOfBlock(
                event.getTransactionId(),
                event.getBlockReason(),
                event.getSeverity()
            );
            
            // If transaction involves external providers, notify them
            if (event.getExternalProviders() != null && !event.getExternalProviders().isEmpty()) {
                paymentProcessingService.notifyExternalProvidersOfBlock(
                    event.getTransactionId(),
                    event.getExternalProviders(),
                    event.getBlockReason()
                );
            }
            
            log.debug("Coordinated upstream blocking for transaction: {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.warn("Failed to coordinate upstream blocking for transaction: {}", event.getTransactionId(), e);
            // Don't throw exception for upstream coordination failures
        }
    }

    private void updateCustomerRiskProfile(TransactionBlockEvent event) {
        try {
            if (event.getCustomerId() != null) {
                transactionSecurityService.updateCustomerRiskProfileForBlock(
                    event.getCustomerId(),
                    event.getTransactionId(),
                    event.getBlockReason(),
                    event.getSeverity(),
                    event.getBlockedAt()
                );
                
                log.debug("Updated customer risk profile for customer: {} due to blocked transaction", 
                    event.getCustomerId());
            }
            
        } catch (Exception e) {
            log.warn("Failed to update customer risk profile for customer: {}", event.getCustomerId(), e);
            // Don't throw exception for risk profile update failures
        }
    }

    private void auditTransactionBlocking(TransactionBlockEvent event) {
        try {
            auditService.auditTransactionBlocking(
                event.getTransactionId(),
                event.getCustomerId(),
                event.getBlockReason(),
                event.getSeverity(),
                event.getBlockedBy(),
                event.getSecurityFlags(),
                event.getBlockedAt()
            );
            
        } catch (Exception e) {
            log.warn("Failed to audit transaction blocking for transaction: {}", event.getTransactionId(), e);
            // Don't throw exception for audit failures
        }
    }

    private void handleBlockProcessingError(TransactionBlockEvent event, Exception error) {
        try {
            log.error("Transaction block processing error - sending to DLQ. Transaction: {}, Error: {}", 
                event.getTransactionId(), error.getMessage());
            
            // Send to dead letter queue for manual investigation
            transactionSecurityService.sendBlockEventToDLQ(event, error.getMessage());
            
            // Send urgent alert to security team
            notificationService.sendUrgentSecurityAlert(
                "TRANSACTION_BLOCK_PROCESSING_FAILED",
                event.getTransactionId(),
                event.getCustomerId(),
                "CRITICAL: Failed to process transaction block - transaction may still be processing. " +
                "Manual intervention required immediately. Error: " + error.getMessage()
            );
            
        } catch (Exception e) {
            log.error("Failed to handle transaction block processing error", e);
        }
    }
}