package com.waqiti.wallet.kafka;

import com.waqiti.common.events.TransactionReversedEvent;
import com.waqiti.common.events.WalletCreditedEvent;
import com.waqiti.common.distributed.DistributedLockService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletTransaction;
import com.waqiti.wallet.domain.TransactionType;
import com.waqiti.wallet.exception.WalletNotFoundException;
import com.waqiti.wallet.exception.ReversalException;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.WalletTransactionRepository;
import com.waqiti.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL FIX: Consumer for TransactionReversedEvent
 * This was missing and causing wallets not to be credited on transaction reversals
 * 
 * Responsibilities:
 * - Credit wallets when transactions are reversed
 * - Ensure atomic reversal operations
 * - Prevent double reversals
 * - Maintain audit trail
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionReversedEventConsumer {
    
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final DistributedLockService lockService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    
    private static final String WALLET_CREDITED_TOPIC = "wallet-credited-events";
    private static final String DLQ_TOPIC = "transaction-reversed-events-dlq";
    private static final int MAX_REVERSAL_ATTEMPTS = 3;
    
    /**
     * Processes transaction reversal events and credits the affected wallet
     * 
     * CRITICAL: This ensures users get their money back when transactions fail
     * Uses distributed locking to prevent race conditions during reversal
     * 
     * @param event The transaction reversed event
     * @param partition Kafka partition
     * @param offset Message offset
     * @param acknowledgment Manual acknowledgment
     */
    @KafkaListener(
        topics = "transaction-reversed-events",
        groupId = "wallet-service-transaction-reversed-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 5,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void handleTransactionReversed(
            @Payload TransactionReversedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("reversal-%s-p%d-o%d",
            event.getTransactionId(), partition, offset);
        
        log.info("Processing transaction reversal: transactionId={}, walletId={}, amount={}, correlation={}",
            event.getTransactionId(), event.getWalletId(), event.getAmount(), correlationId);
        
        // Acquire distributed lock to prevent concurrent modifications
        String lockKey = String.format("wallet:reversal:%s", event.getWalletId());
        
        try {
            boolean lockAcquired = lockService.tryLock(lockKey, 30, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.warn("Could not acquire lock for wallet reversal: {}", event.getWalletId());
                // Requeue for retry
                throw new ReversalException("Could not acquire lock for reversal processing");
            }
            
            try {
                processReversal(event, correlationId);
                acknowledgment.acknowledge();
                
            } finally {
                lockService.unlock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Failed to process transaction reversal: transactionId={}, walletId={}, partition={}, offset={}, error={}",
                event.getTransactionId(), event.getWalletId(), partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(event, "transaction-reversed-events", partition, offset, e)
                .thenAccept(result -> log.info("Transaction reversal event sent to DLQ: reversalId={}, destination={}, category={}",
                        event.getReversalId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for transaction reversal - MESSAGE MAY BE LOST! " +
                            "reversalId={}, partition={}, offset={}, error={}",
                            event.getReversalId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            // Send to DLQ after max attempts
            if (shouldSendToDlq(event)) {
                acknowledgment.acknowledge(); // Acknowledge to prevent infinite retry
            } else {
                // Let it retry
                throw new ReversalException("Reversal processing failed, will retry", e);
            }
        }
    }
    
    /**
     * Process the actual reversal with transaction management
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    protected void processReversal(TransactionReversedEvent event, String correlationId) {
        
        // Validate event
        validateEvent(event);
        
        // Check if already processed (idempotency)
        if (isReversalProcessed(event.getTransactionId(), event.getReversalId())) {
            log.info("Reversal already processed: reversalId={}, transactionId={}",
                event.getReversalId(), event.getTransactionId());
            return;
        }
        
        // Find the wallet
        Wallet wallet = walletRepository.findById(event.getWalletId())
            .orElseThrow(() -> new WalletNotFoundException(
                "Wallet not found for reversal: " + event.getWalletId()));
        
        // Validate wallet state
        if (!wallet.isActive()) {
            throw new ReversalException(String.format(
                "Cannot process reversal for inactive wallet: %s (status=%s)",
                wallet.getId(), wallet.getStatus()
            ));
        }
        
        // Record the reversal transaction
        WalletTransaction reversalTransaction = WalletTransaction.builder()
            .id(UUID.randomUUID())
            .walletId(wallet.getId())
            .transactionId(event.getTransactionId())
            .reversalId(event.getReversalId())
            .type(TransactionType.REVERSAL_CREDIT)
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .balanceBefore(wallet.getBalance())
            .description(String.format("Reversal: %s", event.getReason()))
            .metadata(buildMetadata(event, correlationId))
            .createdAt(LocalDateTime.now())
            .build();
        
        // Credit the wallet
        BigDecimal previousBalance = wallet.getBalance();
        wallet.credit(event.getAmount());
        
        // Update reversal transaction with new balance
        reversalTransaction.setBalanceAfter(wallet.getBalance());
        
        // Save wallet with optimistic locking
        try {
            wallet = walletRepository.save(wallet);
            transactionRepository.save(reversalTransaction);
            
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure during reversal, will retry: walletId={}",
                wallet.getId());
            throw e; // Will trigger retry
        }
        
        log.info("Successfully credited wallet for reversal: walletId={}, amount={}, " +
            "previousBalance={}, newBalance={}, reversalId={}",
            wallet.getId(), event.getAmount(), previousBalance, 
            wallet.getBalance(), event.getReversalId());
        
        // Publish wallet credited event
        WalletCreditedEvent creditedEvent = WalletCreditedEvent.builder()
            .eventId(UUID.randomUUID())
            .walletId(wallet.getId())
            .transactionId(event.getTransactionId())
            .reversalId(event.getReversalId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .balanceBefore(previousBalance)
            .balanceAfter(wallet.getBalance())
            .creditType("REVERSAL")
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send(WALLET_CREDITED_TOPIC, creditedEvent);
        
        // Send notification to user
        notifyUserOfReversal(wallet.getUserId(), event, wallet.getBalance());
        
        // Audit the reversal
        auditReversal(event, wallet, reversalTransaction);
    }
    
    /**
     * Check if reversal has already been processed
     */
    private boolean isReversalProcessed(UUID transactionId, UUID reversalId) {
        return transactionRepository.existsByTransactionIdAndReversalId(transactionId, reversalId);
    }
    
    /**
     * Validate the reversal event
     */
    private void validateEvent(TransactionReversedEvent event) {
        if (event.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID is required for reversal");
        }
        if (event.getReversalId() == null) {
            throw new IllegalArgumentException("Reversal ID is required");
        }
        if (event.getWalletId() == null) {
            throw new IllegalArgumentException("Wallet ID is required for reversal");
        }
        if (event.getAmount() == null || event.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Invalid reversal amount: " + event.getAmount());
        }
        if (event.getCurrency() == null || event.getCurrency().isEmpty()) {
            throw new IllegalArgumentException("Currency is required for reversal");
        }
    }
    
    /**
     * Build metadata for the reversal transaction
     */
    private Map<String, Object> buildMetadata(TransactionReversedEvent event, String correlationId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("originalTransactionId", event.getOriginalTransactionId());
        metadata.put("reversalReason", event.getReason());
        metadata.put("reversalType", event.getReversalType());
        metadata.put("initiatedBy", event.getInitiatedBy());
        metadata.put("initiatedAt", event.getInitiatedAt());
        metadata.put("correlationId", correlationId);
        
        if (event.getAdditionalInfo() != null) {
            metadata.putAll(event.getAdditionalInfo());
        }
        
        return metadata;
    }
    
    /**
     * Notify user about the reversal
     */
    private void notifyUserOfReversal(UUID userId, TransactionReversedEvent event, BigDecimal newBalance) {
        try {
            NotificationEvent notification = NotificationEvent.builder()
                .userId(userId)
                .type("TRANSACTION_REVERSED")
                .title("Transaction Reversed")
                .message(String.format("Your transaction has been reversed. %s %s has been credited back to your wallet. New balance: %s %s",
                    event.getAmount(), event.getCurrency(), newBalance, event.getCurrency()))
                .priority("HIGH")
                .data(Map.of(
                    "transactionId", event.getTransactionId(),
                    "reversalId", event.getReversalId(),
                    "amount", event.getAmount(),
                    "reason", event.getReason()
                ))
                .build();
            
            kafkaTemplate.send("notification-events", notification);
            
        } catch (Exception e) {
            log.error("Failed to send reversal notification to user: {}", userId, e);
            // Don't fail the reversal if notification fails
        }
    }
    
    /**
     * Audit the reversal for compliance
     */
    private void auditReversal(TransactionReversedEvent event, Wallet wallet, WalletTransaction transaction) {
        try {
            AuditEvent auditEvent = AuditEvent.builder()
                .eventType("TRANSACTION_REVERSAL")
                .entityType("WALLET")
                .entityId(wallet.getId().toString())
                .userId(wallet.getUserId())
                .action("CREDIT")
                .details(Map.of(
                    "transactionId", event.getTransactionId(),
                    "reversalId", event.getReversalId(),
                    "amount", event.getAmount(),
                    "currency", event.getCurrency(),
                    "reason", event.getReason(),
                    "balanceBefore", transaction.getBalanceBefore(),
                    "balanceAfter", transaction.getBalanceAfter()
                ))
                .timestamp(Instant.now())
                .build();
            
            kafkaTemplate.send("audit-events", auditEvent);
            
        } catch (Exception e) {
            log.error("Failed to audit reversal", e);
            // Don't fail the reversal if audit fails
        }
    }
    
    /**
     * Determine if event should be sent to DLQ
     */
    private boolean shouldSendToDlq(TransactionReversedEvent event) {
        // Check attempt count from event metadata or use a cache
        Integer attempts = event.getProcessingAttempts();
        return attempts != null && attempts >= MAX_REVERSAL_ATTEMPTS;
    }
    
    /**
     * Send failed events to dead letter queue
     */
    private void sendToDeadLetterQueue(TransactionReversedEvent event, Exception error) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalEvent", event);
            dlqMessage.put("errorMessage", error.getMessage());
            dlqMessage.put("errorClass", error.getClass().getName());
            dlqMessage.put("failedAt", Instant.now());
            dlqMessage.put("service", "wallet-service");
            dlqMessage.put("attempts", event.getProcessingAttempts());
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            
            log.warn("Sent failed reversal event to DLQ: transactionId={}, reversalId={}",
                event.getTransactionId(), event.getReversalId());
            
            // Alert operations team
            alertService.sendAlert(
                "REVERSAL_FAILED",
                String.format("Failed to process reversal %s for transaction %s after %d attempts",
                    event.getReversalId(), event.getTransactionId(), MAX_REVERSAL_ATTEMPTS),
                AlertService.Priority.HIGH
            );
            
        } catch (Exception dlqError) {
            log.error("Failed to send reversal event to DLQ", dlqError);
        }
    }
}