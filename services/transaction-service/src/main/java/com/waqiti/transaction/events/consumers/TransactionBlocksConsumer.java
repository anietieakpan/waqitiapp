package com.waqiti.transaction.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.transaction.TransactionBlockEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionBlock;
import com.waqiti.transaction.domain.BlockReason;
import com.waqiti.transaction.domain.BlockStatus;
import com.waqiti.transaction.repository.TransactionRepository;
import com.waqiti.transaction.repository.TransactionBlockRepository;
import com.waqiti.transaction.service.TransactionNotificationService;
import com.waqiti.transaction.service.ComplianceIntegrationService;
import com.waqiti.transaction.service.FraudPreventionService;
import com.waqiti.transaction.service.TransactionRecoveryService;
import com.waqiti.common.exceptions.TransactionNotFoundException;
import com.waqiti.common.exceptions.TransactionBlockException;

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
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

/**
 * Production-grade consumer for transaction block events.
 * Handles real-time transaction blocking with:
 * - Immediate transaction suspension
 * - Comprehensive compliance checking
 * - Fraud prevention integration
 * - Customer and merchant notifications
 * - Regulatory compliance reporting
 * - Recovery workflow management
 * 
 * Critical for fraud prevention and regulatory compliance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionBlocksConsumer {

    private final TransactionRepository transactionRepository;
    private final TransactionBlockRepository blockRepository;
    private final TransactionNotificationService notificationService;
    private final ComplianceIntegrationService complianceService;
    private final FraudPreventionService fraudService;
    private final TransactionRecoveryService recoveryService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    @KafkaListener(
        topics = "transaction-blocks",
        groupId = "transaction-service-block-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2.0),
        include = {TransactionBlockException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handleTransactionBlock(
            @Payload TransactionBlockEvent blockEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "block-priority", required = false) String priority,
            Acknowledgment acknowledgment) {

        String eventId = blockEvent.getEventId() != null ? 
            blockEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing transaction block event: {} for transaction: {} with reason: {}", 
                    eventId, blockEvent.getTransactionId(), blockEvent.getBlockReason());

            // Metrics tracking
            metricsService.incrementCounter("transaction.block.processing.started",
                Map.of(
                    "reason", blockEvent.getBlockReason(),
                    "severity", blockEvent.getSeverity() != null ? blockEvent.getSeverity() : "unknown"
                ));

            // Idempotency check
            if (isTransactionBlockProcessed(blockEvent.getTransactionId(), eventId)) {
                log.info("Transaction block {} already processed for transaction {}", 
                        eventId, blockEvent.getTransactionId());
                acknowledgment.acknowledge();
                return;
            }

            // Retrieve and validate transaction
            Transaction transaction = getAndValidateTransaction(blockEvent.getTransactionId());

            // Create transaction block record
            TransactionBlock transactionBlock = createTransactionBlock(blockEvent, transaction, eventId, correlationId);

            // Execute immediate blocking action
            executeTransactionBlocking(transaction, transactionBlock, blockEvent);

            // Perform compliance analysis
            performComplianceAnalysis(transaction, transactionBlock, blockEvent);

            // Fraud prevention assessment
            assessFraudRisk(transaction, transactionBlock, blockEvent);

            // Save block record
            TransactionBlock savedBlock = blockRepository.save(transactionBlock);

            // Send notifications to stakeholders
            sendBlockNotifications(transaction, savedBlock, blockEvent);

            // Initiate recovery workflow if applicable
            initiateRecoveryWorkflow(transaction, savedBlock, blockEvent);

            // Update analytics and monitoring
            updateBlockAnalytics(transaction, savedBlock, blockEvent);

            // Create comprehensive audit trail
            createBlockAuditLog(transaction, savedBlock, blockEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("transaction.block.processing.success",
                Map.of(
                    "reason", blockEvent.getBlockReason(),
                    "amount_range", categorizeAmount(transaction.getAmount())
                ));

            log.info("Successfully processed transaction block: {} for transaction: {} with status: {}", 
                    savedBlock.getId(), transaction.getId(), savedBlock.getStatus());

            acknowledgment.acknowledge();

        } catch (TransactionNotFoundException e) {
            log.error("Transaction not found for block event {}: {}", eventId, e.getMessage());
            metricsService.incrementCounter("transaction.block.transaction_not_found");
            acknowledgment.acknowledge(); // Don't retry for missing transactions
            
        } catch (Exception e) {
            log.error("Error processing transaction block event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("transaction.block.processing.error");
            
            // Create error audit log
            auditLogger.logError("TRANSACTION_BLOCK_PROCESSING_ERROR", 
                "system", eventId, e.getMessage(),
                Map.of(
                    "transactionId", blockEvent.getTransactionId(),
                    "blockReason", blockEvent.getBlockReason(),
                    "correlationId", correlationId
                ));
            
            throw new TransactionBlockException("Failed to process transaction block: " + e.getMessage(), e);
        }
    }

    /**
     * Emergency transaction block processor for critical threats
     */
    @KafkaListener(
        topics = "transaction-blocks-emergency",
        groupId = "transaction-service-emergency-block-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleEmergencyTransactionBlock(
            @Payload TransactionBlockEvent blockEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.warn("EMERGENCY TRANSACTION BLOCK: Processing critical block event: {} for transaction: {}", 
                    blockEvent.getEventId(), blockEvent.getTransactionId());

            // Emergency blocking with bypass of normal validation
            Transaction transaction = transactionRepository.findById(blockEvent.getTransactionId())
                .orElse(null);
            
            if (transaction != null) {
                // Immediate emergency block
                transaction.setStatus(TransactionStatus.SUSPENDED);
                transaction.setBlockedAt(LocalDateTime.now());
                transaction.setBlockReason(blockEvent.getBlockReason());
                transactionRepository.save(transaction);

                // Send emergency alerts
                notificationService.sendEmergencyBlockAlert(transaction, blockEvent);
                
                log.warn("Emergency block applied to transaction: {}", transaction.getId());
            }

            // Process through normal flow
            handleTransactionBlock(blockEvent, "transaction-blocks-emergency", correlationId, "EMERGENCY", acknowledgment);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process emergency transaction block: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent blocking emergency queue
        }
    }

    private boolean isTransactionBlockProcessed(String transactionId, String eventId) {
        return blockRepository.existsByTransactionIdAndEventId(transactionId, eventId);
    }

    private Transaction getAndValidateTransaction(String transactionId) {
        Optional<Transaction> transactionOpt = transactionRepository.findById(UUID.fromString(transactionId));
        if (transactionOpt.isEmpty()) {
            throw new TransactionNotFoundException("Transaction not found: " + transactionId);
        }
        
        Transaction transaction = transactionOpt.get();
        
        // Validate transaction can be blocked
        if (transaction.getStatus() == TransactionStatus.COMPLETED || transaction.getStatus() == TransactionStatus.FAILED) {
            log.warn("Attempting to block transaction {} in terminal state: {}", 
                    transactionId, transaction.getStatus());
        }
        
        return transaction;
    }

    private TransactionBlock createTransactionBlock(TransactionBlockEvent event, Transaction transaction, 
                                                   String eventId, String correlationId) {
        return TransactionBlock.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .transactionId(transaction.getId())
            .blockReason(BlockReason.valueOf(event.getBlockReason().toUpperCase()))
            .status(BlockStatus.ACTIVE)
            .severity(event.getSeverity() != null ? event.getSeverity() : "MEDIUM")
            .description(event.getDescription())
            .blockedBy(event.getBlockedBy())
            .blockedBySystem(event.getBlockedBySystem())
            .blockTimestamp(LocalDateTime.now())
            .expirationTime(calculateBlockExpiration(event))
            .originalTransactionStatus(transaction.getStatus())
            .blockMetadata(event.getMetadata())
            .correlationId(correlationId)
            .requiresManualReview(requiresManualReview(event))
            .complianceFlags(event.getComplianceFlags())
            .fraudIndicators(event.getFraudIndicators())
            .recoveryEligible(isRecoveryEligible(event, transaction))
            .notificationsSent(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private void executeTransactionBlocking(Transaction transaction, TransactionBlock block, TransactionBlockEvent event) {
        try {
            // Update transaction status
            TransactionStatus previousStatus = transaction.getStatus();
            transaction.setStatus(TransactionStatus.SUSPENDED);
            transaction.setBlockedAt(LocalDateTime.now());
            transaction.setBlockReason(event.getBlockReason());
            transaction.setBlockId(block.getId());
            transaction.setUpdatedAt(LocalDateTime.now());
            
            // Save transaction with block
            transactionRepository.save(transaction);
            
            // Log the blocking action
            log.info("Transaction {} blocked: {} -> BLOCKED (reason: {})", 
                    transaction.getId(), previousStatus, event.getBlockReason());
            
            // Update block with action taken
            block.setActionTaken("TRANSACTION_BLOCKED");
            block.setActionTimestamp(LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Failed to execute transaction blocking for {}: {}", transaction.getId(), e.getMessage());
            block.setActionTaken("BLOCK_FAILED");
            block.setErrorMessage(e.getMessage());
            throw new TransactionBlockException("Failed to block transaction", e);
        }
    }

    private void performComplianceAnalysis(Transaction transaction, TransactionBlock block, TransactionBlockEvent event) {
        try {
            // Run compliance checks
            var complianceResult = complianceService.analyzeBlockedTransaction(transaction, block, event);
            
            // Update block with compliance assessment
            block.setComplianceRiskScore(complianceResult.getRiskScore());
            block.setComplianceFlags(complianceResult.getFlags());
            block.setRegulatoryReporting(complianceResult.requiresReporting());
            
            // High compliance risk requires immediate escalation
            if (complianceResult.getRiskScore() > 0.8) {
                block.setRequiresManualReview(true);
                block.setSeverity("HIGH");
                
                log.warn("High compliance risk detected for blocked transaction {}: score {}", 
                        transaction.getId(), complianceResult.getRiskScore());
            }
            
        } catch (Exception e) {
            log.error("Compliance analysis failed for blocked transaction {}: {}", transaction.getId(), e.getMessage());
            block.setRequiresManualReview(true);
        }
    }

    private void assessFraudRisk(Transaction transaction, TransactionBlock block, TransactionBlockEvent event) {
        try {
            // Fraud risk assessment
            var fraudAssessment = fraudService.assessBlockedTransaction(transaction, block, event);
            
            // Update block with fraud assessment
            block.setFraudRiskScore(fraudAssessment.getRiskScore());
            block.setFraudIndicators(fraudAssessment.getIndicators());
            
            // High fraud risk requires additional security measures
            if (fraudAssessment.getRiskScore() > 0.9) {
                // Notify fraud team
                notificationService.sendFraudTeamAlert(transaction, block, fraudAssessment);
                
                // Consider account-level restrictions
                fraudService.evaluateAccountRestrictions(transaction.getUserId(), fraudAssessment);
            }
            
        } catch (Exception e) {
            log.error("Fraud assessment failed for blocked transaction {}: {}", transaction.getId(), e.getMessage());
            // Don't fail the entire process, but flag for review
            block.setRequiresManualReview(true);
        }
    }

    private void sendBlockNotifications(Transaction transaction, TransactionBlock block, TransactionBlockEvent event) {
        try {
            // Send customer notification
            notificationService.sendCustomerBlockNotification(transaction, block);
            
            // Send merchant notification if applicable
            if (transaction.getMerchantId() != null) {
                notificationService.sendMerchantBlockNotification(transaction, block);
            }
            
            // Send internal team notifications based on severity
            if (block.getSeverity().equals("HIGH") || block.getSeverity().equals("CRITICAL")) {
                notificationService.sendInternalBlockAlert(transaction, block);
            }
            
            // Compliance team notification for regulatory blocks
            if (isRegulatoryBlock(block.getBlockReason())) {
                notificationService.sendComplianceTeamNotification(transaction, block);
            }
            
            block.setNotificationsSent(true);
            
        } catch (Exception e) {
            log.error("Failed to send block notifications for transaction {}: {}", transaction.getId(), e.getMessage());
            block.setNotificationsSent(false);
            // Don't fail processing for notification issues
        }
    }

    private void initiateRecoveryWorkflow(Transaction transaction, TransactionBlock block, TransactionBlockEvent event) {
        if (!block.getRecoveryEligible()) {
            return;
        }
        
        try {
            // Create recovery workflow
            recoveryService.initiateRecoveryWorkflow(transaction, block);
            
            // Set up automatic unblock for temporary blocks
            if (isTemporaryBlock(block.getBlockReason()) && block.getExpirationTime() != null) {
                recoveryService.scheduleAutomaticUnblock(transaction, block);
            }
            
            log.info("Recovery workflow initiated for blocked transaction: {}", transaction.getId());
            
        } catch (Exception e) {
            log.error("Failed to initiate recovery workflow for transaction {}: {}", transaction.getId(), e.getMessage());
            // Flag for manual intervention
            block.setRequiresManualReview(true);
        }
    }

    private void updateBlockAnalytics(Transaction transaction, TransactionBlock block, TransactionBlockEvent event) {
        try {
            // Record block metrics
            metricsService.incrementCounter("transaction.blocks.by_reason",
                Map.of(
                    "reason", block.getBlockReason().toString(),
                    "severity", block.getSeverity()
                ));
            
            // Record financial impact
            if (transaction.getAmount() != null) {
                metricsService.recordTimer("transaction.blocked.amount", transaction.getAmount().doubleValue(),
                    Map.of(
                        "reason", block.getBlockReason().toString(),
                        "currency", transaction.getCurrency()
                    ));
            }
            
            // Geographic blocking patterns
            if (transaction.getOriginCountry() != null) {
                metricsService.incrementCounter("transaction.blocks.geographic",
                    Map.of(
                        "country", transaction.getOriginCountry(),
                        "reason", block.getBlockReason().toString()
                    ));
            }
            
        } catch (Exception e) {
            log.error("Failed to update block analytics for transaction {}: {}", transaction.getId(), e.getMessage());
        }
    }

    private void createBlockAuditLog(Transaction transaction, TransactionBlock block, 
                                   TransactionBlockEvent event, String correlationId) {
        auditLogger.logTransactionEvent(
            "TRANSACTION_BLOCKED",
            event.getBlockedBy() != null ? event.getBlockedBy() : "system",
            block.getId(),
            transaction.getAmount() != null ? transaction.getAmount().doubleValue() : 0.0,
            transaction.getCurrency(),
            "transaction_blocker",
            true,
            Map.of(
                "transactionId", transaction.getId(),
                "blockReason", block.getBlockReason().toString(),
                "severity", block.getSeverity(),
                "originalStatus", block.getOriginalTransactionStatus(),
                "complianceRisk", block.getComplianceRiskScore() != null ? block.getComplianceRiskScore().toString() : "N/A",
                "fraudRisk", block.getFraudRiskScore() != null ? block.getFraudRiskScore().toString() : "N/A",
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private LocalDateTime calculateBlockExpiration(TransactionBlockEvent event) {
        if (event.getExpirationTime() != null) {
            return event.getExpirationTime();
        }
        
        // Default expiration based on block reason
        return switch (event.getBlockReason().toUpperCase()) {
            case "FRAUD_SUSPECTED" -> LocalDateTime.now().plusDays(7);
            case "COMPLIANCE_REVIEW" -> LocalDateTime.now().plusDays(3);
            case "MANUAL_REVIEW" -> LocalDateTime.now().plusDays(1);
            case "SYSTEM_MAINTENANCE" -> LocalDateTime.now().plusHours(4);
            default -> LocalDateTime.now().plusDays(1);
        };
    }

    private boolean requiresManualReview(TransactionBlockEvent event) {
        return event.getSeverity() != null && 
               (event.getSeverity().equals("HIGH") || event.getSeverity().equals("CRITICAL")) ||
               event.getBlockReason().equals("FRAUD_SUSPECTED") ||
               event.getBlockReason().equals("REGULATORY_HOLD");
    }

    private boolean isRecoveryEligible(TransactionBlockEvent event, Transaction transaction) {
        // Terminal states are not recovery eligible
        if (transaction.getStatus() == TransactionStatus.COMPLETED || transaction.getStatus() == TransactionStatus.FAILED) {
            return false;
        }
        
        // Permanent blocks are not recovery eligible
        return !event.getBlockReason().equals("PERMANENT_BAN") && 
               !event.getBlockReason().equals("ACCOUNT_CLOSED");
    }

    private boolean isRegulatoryBlock(BlockReason reason) {
        return reason == BlockReason.COMPLIANCE_REVIEW || 
               reason == BlockReason.REGULATORY_HOLD ||
               reason == BlockReason.AML_SCREENING;
    }

    private boolean isTemporaryBlock(BlockReason reason) {
        return reason == BlockReason.TEMPORARY_HOLD || 
               reason == BlockReason.SYSTEM_MAINTENANCE ||
               reason == BlockReason.MANUAL_REVIEW;
    }

    private String categorizeAmount(java.math.BigDecimal amount) {
        if (amount == null) return "unknown";
        
        if (amount.compareTo(new java.math.BigDecimal("10000")) > 0) {
            return "high";
        } else if (amount.compareTo(new java.math.BigDecimal("1000")) > 0) {
            return "medium";
        } else {
            return "low";
        }
    }
}