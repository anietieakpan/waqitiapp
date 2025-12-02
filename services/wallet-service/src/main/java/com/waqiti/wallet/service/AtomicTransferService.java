package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.FundReservation;
import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.TransactionType;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.dto.DepositRequest;
import com.waqiti.wallet.dto.TransactionResponse;
import com.waqiti.wallet.dto.TransferRequest;
import com.waqiti.wallet.dto.WithdrawRequest;
import com.waqiti.wallet.exception.InsufficientBalanceException;
import com.waqiti.wallet.exception.TransactionFailedException;
import com.waqiti.wallet.exception.WalletNotFoundException;
import com.waqiti.wallet.lock.DistributedWalletLockService;
import com.waqiti.wallet.repository.TransactionRepository;
import com.waqiti.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * CRITICAL SECURITY SERVICE: Atomic Transfer Operations
 * Ensures all fund transfers are atomic, consistent, isolated, and durable (ACID)
 * Prevents race conditions, double-spending, and partial transfers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtomicTransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final FundReservationService fundReservationService;
    private final WalletBalanceService walletBalanceService;  // ADDED: For proper fund reservation
    private final IntegrationService integrationService;
    private final TransactionLogger transactionLogger;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WalletCompensationService walletCompensationService;
    private final DistributedWalletLockService lockService;  // ADDED: Redis-based distributed locking

    // Local lock for in-process synchronization (kept for backward compatibility)
    private final Lock transferLock = new ReentrantLock();
    
    /**
     * CRITICAL SECURITY FIX: Atomic transfer with proper rollback
     * Uses 2-phase commit pattern: Reserve -> Execute -> Confirm/Rollback
     *
     * ✅ CRITICAL PRODUCTION FIX: Changed back to SERIALIZABLE for atomic consistency
     * Previous comment was INCORRECT - READ_COMMITTED can see partial state
     * For atomic transfers, we MUST use SERIALIZABLE to ensure:
     * 1. Both wallets see consistent state throughout transfer
     * 2. No phantom reads during multi-step operation
     * 3. True ACID guarantees for financial integrity
     * Deadlocks are prevented by sorted lock acquisition (line 80-83)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @Retryable(value = {OptimisticLockException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100))
    public TransactionResponse executeAtomicTransfer(TransferRequest request, UUID authenticatedUserId) {

        UUID transactionId = UUID.randomUUID();
        FundReservation reservation = null;
        Transaction transaction = null;

        // CRITICAL: Acquire distributed locks (sorted order prevents deadlocks)
        List<String> walletIds = Arrays.asList(
            request.getSourceWalletId().toString(),
            request.getTargetWalletId().toString()
        );

        Map<String, String> locks = lockService.acquireMultipleLocks(walletIds);

        if (locks == null) {
            log.error("LOCK TIMEOUT: Failed to acquire locks for atomic transfer: source={}, target={}",
                request.getSourceWalletId(), request.getTargetWalletId());
            throw new TransactionFailedException("Transfer temporarily unavailable due to high load, please retry");
        }

        log.info("SECURITY: Starting atomic transfer {} from wallet {} to wallet {} for amount {} (locks acquired)",
            transactionId, request.getSourceWalletId(), request.getTargetWalletId(), request.getAmount());
        
        try {
            // PHASE 1: PREPARE - Lock wallets and validate
            TransferContext context = prepareTransfer(request, authenticatedUserId, transactionId);
            
            // PHASE 2: RESERVE - Reserve funds atomically
            reservation = reserveFundsAtomic(context);
            
            // PHASE 3: EXECUTE - Perform external transfer
            String externalId = executeExternalTransfer(context);
            
            // PHASE 4: CONFIRM - Confirm reservation and update balances
            transaction = confirmTransferAtomic(context, reservation, externalId);
            
            // PHASE 5: COMPLETE - Final logging and notifications
            completeTransfer(context, transaction);
            
            log.info("SECURITY: Atomic transfer {} completed successfully", transactionId);
            return mapToTransactionResponse(transaction);
            
        } catch (Exception e) {
            log.error("SECURITY: Atomic transfer {} failed, initiating rollback", transactionId, e);

            // CRITICAL: Rollback on any failure
            rollbackTransfer(reservation, transaction, transactionId, e);

            if (e instanceof InsufficientBalanceException) {
                throw (InsufficientBalanceException) e;
            } else {
                throw new TransactionFailedException("Atomic transfer failed: " + e.getMessage(), e);
            }
        } finally {
            // CRITICAL: Always release distributed locks (even on exception)
            lockService.releaseMultipleLocks(locks);
            log.debug("Distributed locks released for atomic transfer: transactionId={}, walletIds={}",
                transactionId, walletIds);
        }
    }
    
    /**
     * PHASE 1: Prepare transfer - Lock wallets and validate
     */
    private TransferContext prepareTransfer(TransferRequest request, UUID authenticatedUserId, UUID transactionId) {
        
        // Lock wallets in consistent order to prevent deadlocks
        UUID sourceId = request.getSourceWalletId();
        UUID targetId = request.getTargetWalletId();
        
        Wallet sourceWallet, targetWallet;
        
        // CRITICAL: Acquire locks in UUID order to prevent deadlocks
        if (sourceId.compareTo(targetId) < 0) {
            sourceWallet = walletRepository.findByIdWithPessimisticLock(sourceId)
                .orElseThrow(() -> new WalletNotFoundException("Source wallet not found: " + sourceId));
            targetWallet = walletRepository.findByIdWithPessimisticLock(targetId)
                .orElseThrow(() -> new WalletNotFoundException("Target wallet not found: " + targetId));
        } else {
            targetWallet = walletRepository.findByIdWithPessimisticLock(targetId)
                .orElseThrow(() -> new WalletNotFoundException("Target wallet not found: " + targetId));
            sourceWallet = walletRepository.findByIdWithPessimisticLock(sourceId)
                .orElseThrow(() -> new WalletNotFoundException("Source wallet not found: " + sourceId));
        }
        
        // Initialize fund reservation repositories
        fundReservationService.initializeWalletReservations(sourceWallet);
        fundReservationService.initializeWalletReservations(targetWallet);
        
        // SECURITY: Verify ownership
        if (!sourceWallet.getUserId().equals(authenticatedUserId)) {
            throw new SecurityException("Unauthorized: User does not own source wallet");
        }
        
        // Validate wallets are active
        if (sourceWallet.getStatus() != Wallet.WalletStatus.ACTIVE) {
            throw new IllegalStateException("Source wallet is not active: " + sourceWallet.getStatus());
        }
        
        if (targetWallet.getStatus() != Wallet.WalletStatus.ACTIVE) {
            throw new IllegalStateException("Target wallet is not active: " + targetWallet.getStatus());
        }
        
        // Validate currencies match
        if (!sourceWallet.getCurrency().equals(targetWallet.getCurrency())) {
            throw new IllegalArgumentException(String.format(
                "Currency mismatch: source=%s, target=%s", 
                sourceWallet.getCurrency(), targetWallet.getCurrency()));
        }
        
        // Validate amount
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        
        return new TransferContext(sourceWallet, targetWallet, request, transactionId, authenticatedUserId);
    }
    
    /**
     * PHASE 2: Reserve funds atomically
     * FIXED: Use WalletBalanceService instead of deprecated wallet.reserveFunds()
     */
    private FundReservation reserveFundsAtomic(TransferContext context) {

        // Generate idempotency key for reservation
        String idempotencyKey = String.format("transfer_%s_%s",
            context.transactionId, context.request.getIdempotencyKey());

        // CRITICAL FIX: Use WalletBalanceService for persistent fund reservation
        // This replaces the deprecated wallet.reserveFunds() method that threw UnsupportedOperationException
        FundReservation reservation = walletBalanceService.reserveFunds(
            context.sourceWallet.getId(),
            context.request.getAmount(),
            context.transactionId,
            idempotencyKey
        );

        // Refresh wallet entity to reflect updated balance after reservation
        context.sourceWallet = walletRepository.findById(context.sourceWallet.getId())
            .orElseThrow(() -> new WalletNotFoundException("Source wallet not found after reservation"));

        log.info("SECURITY: Reserved {} {} for transfer {} with reservation {} using WalletBalanceService",
            context.request.getAmount(), context.sourceWallet.getCurrency(),
            context.transactionId, reservation.getId());

        return reservation;
    }
    
    /**
     * PHASE 3: Execute external transfer
     */
    private String executeExternalTransfer(TransferContext context) {
        try {
            // Call external system with timeout
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                integrationService.transferBetweenWallets(
                    context.sourceWallet,
                    context.targetWallet,
                    context.request.getAmount()
                )
            );
            
            // Wait with timeout to prevent hanging
            return future.get(30, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.error("SECURITY: External transfer failed for transaction {}", context.transactionId, e);
            throw new TransactionFailedException("External transfer failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * PHASE 4: Confirm transfer atomically
     *
     * CRITICAL SECURITY FIX: Uses database-level atomic operations to prevent partial transfers.
     * Previous implementation had two separate save() calls which could result in fund loss
     * if the second save failed after the first committed.
     *
     * New implementation:
     * 1. Uses native SQL queries that execute atomically in a single database transaction
     * 2. Verifies both operations succeeded with row count checks
     * 3. Rolls back entire transaction if either operation fails
     * 4. Prevents race conditions with optimistic locking (version column)
     */
    private Transaction confirmTransferAtomic(TransferContext context, FundReservation reservation, String externalId) {

        // Create transaction record
        Transaction transaction = Transaction.builder()
            .id(context.transactionId)
            .sourceWalletId(context.sourceWallet.getId())
            .targetWalletId(context.targetWallet.getId())
            .amount(context.request.getAmount())
            .currency(context.sourceWallet.getCurrency())
            .type(TransactionType.TRANSFER)
            .description(context.request.getDescription())
            .externalReferenceId(externalId)
            .status(Transaction.Status.PENDING)
            .createdAt(LocalDateTime.now())
            .createdBy(context.authenticatedUserId.toString())
            .build();

        transaction = transactionRepository.save(transaction);

        try {
            // CRITICAL SECURITY FIX: Execute both wallet updates atomically using database-level operations
            // This ensures both updates succeed or both fail - preventing fund loss

            // Step 1: Atomically transfer funds from reserved to deducted on source wallet
            // This decrements balance and releases the reservation in a single database operation
            int sourceUpdated = walletRepository.atomicTransferFromReserved(
                context.sourceWallet.getId(),
                context.request.getAmount()
            );

            if (sourceUpdated == 0) {
                throw new TransactionFailedException(
                    "CRITICAL: Failed to deduct funds from source wallet - insufficient reserved funds or wallet state changed");
            }

            // Step 2: Atomically credit target wallet
            // Uses native SQL UPDATE with WHERE clause to ensure wallet is still active
            int targetUpdated = walletRepository.atomicCreditWallet(
                context.targetWallet.getId(),
                context.request.getAmount()
            );

            if (targetUpdated == 0) {
                // This should trigger a rollback of the entire transaction including sourceUpdated
                throw new TransactionFailedException(
                    "CRITICAL: Failed to credit target wallet - wallet may be frozen or deleted");
            }

            // IMPORTANT: Both operations succeeded at database level
            // Now update in-memory entity states for consistency (these won't be saved again)
            context.sourceWallet.confirmReservation(reservation.getId());
            context.targetWallet.credit(context.request.getAmount());

            // Mark transaction as completed
            transaction.complete(externalId);
            transaction = transactionRepository.save(transaction);

            log.info("SECURITY: Confirmed atomic transfer {} with external ID {} - Database-level atomic operations succeeded",
                context.transactionId, externalId);

            return transaction;

        } catch (Exception e) {
            // Mark transaction as failed
            transaction.fail("Confirmation failed: " + e.getMessage());
            transactionRepository.save(transaction);

            log.error("SECURITY: Atomic transfer confirmation failed for {} - Transaction will be rolled back",
                context.transactionId, e);

            // Re-throw to trigger @Transactional rollback of entire transaction
            // This will undo both atomicTransferFromReserved and atomicCreditWallet
            throw e;
        }
    }
    
    /**
     * PHASE 5: Complete transfer - logging and notifications
     */
    private void completeTransfer(TransferContext context, Transaction transaction) {
        // Log transaction events for audit
        transactionLogger.logTransaction(transaction);
        
        // Log wallet events for notifications
        transactionLogger.logWalletEvent(
            context.sourceWallet.getUserId(),
            context.sourceWallet.getId(),
            "TRANSFER_OUT",
            context.request.getAmount(),
            context.sourceWallet.getCurrency(),
            transaction.getId()
        );
        
        transactionLogger.logWalletEvent(
            context.targetWallet.getUserId(),
            context.targetWallet.getId(),
            "TRANSFER_IN",
            context.request.getAmount(),
            context.targetWallet.getCurrency(),
            transaction.getId()
        );
    }
    
    /**
     * CRITICAL: Rollback transfer on failure with compensation tracking
     */
    private void rollbackTransfer(FundReservation reservation, Transaction transaction,
                                 UUID transactionId, Exception error) {
        try {
            // Step 1: Release reservation if exists
            if (reservation != null) {
                Wallet sourceWallet = walletRepository.findById(reservation.getWalletId())
                    .orElseThrow(() -> new IllegalStateException(
                        "CRITICAL: Source wallet not found during rollback: " + reservation.getWalletId()));

                fundReservationService.initializeWalletReservations(sourceWallet);
                sourceWallet.releaseReservation(reservation.getId(), "Transfer failed: " + error.getMessage());
                walletRepository.save(sourceWallet);
                log.info("SECURITY: Released reservation {} for failed transfer {}",
                    reservation.getId(), transactionId);
            }

            // Step 2: Mark transaction as failed
            if (transaction != null) {
                transaction.fail(error.getMessage());
                transactionRepository.save(transaction);
            }

            // Step 3: Create compensation record for saga tracking
            try {
                com.waqiti.common.eventsourcing.PaymentFailedEvent failedEvent =
                    com.waqiti.common.eventsourcing.PaymentFailedEvent.builder()
                        .paymentId(transactionId.toString())
                        .userId(transaction != null ? transaction.getUserId() : null)
                        .senderWalletId(reservation != null ? reservation.getWalletId() : null)
                        .amount(reservation != null ? reservation.getAmount() : null)
                        .currency(reservation != null ? "USD" : null)
                        .failureReason(error.getMessage())
                        .failureCode(error instanceof InsufficientBalanceException ?
                                    "INSUFFICIENT_FUNDS" : "TRANSFER_FAILED")
                        .failedAt(LocalDateTime.now())
                        .build();

                walletCompensationService.createRecord(failedEvent, error);
                log.info("SECURITY: Created compensation record for failed transfer {}", transactionId);

            } catch (Exception compensationError) {
                log.error("SECURITY CRITICAL: Failed to create compensation record for transfer {}",
                         transactionId, compensationError);
                // Continue with rollback even if compensation record creation fails
            }

            // Step 4: Log failure for audit
            transactionLogger.logTransactionFailure(
                transactionId,
                error.getMessage(),
                error instanceof InsufficientBalanceException ? "INSUFFICIENT_FUNDS" : "TRANSFER_FAILED"
            );

            // Step 5: Publish transfer failed event to Kafka for monitoring
            try {
                Map<String, Object> failedEventPayload = Map.of(
                    "transactionId", transactionId.toString(),
                    "eventType", "TRANSFER_FAILED",
                    "errorMessage", error.getMessage(),
                    "timestamp", LocalDateTime.now().toString(),
                    "reservationId", reservation != null ? reservation.getId().toString() : "null",
                    "compensationInitiated", true
                );
                kafkaTemplate.send("wallet.transfer.failed", transactionId.toString(), failedEventPayload);
            } catch (Exception kafkaError) {
                log.error("Failed to publish transfer failed event to Kafka", kafkaError);
            }

        } catch (Exception rollbackError) {
            log.error("SECURITY CRITICAL: Rollback failed for transfer {}", transactionId, rollbackError);
            // Alert operations team - this needs manual intervention
            alertOperations("CRITICAL: Rollback failed for transfer " + transactionId, rollbackError);
        }
    }
    
    /**
     * Atomic deposit operation
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse executeAtomicDeposit(DepositRequest request) {
        log.info("SECURITY: Executing atomic deposit of {} to wallet {}", 
            request.getAmount(), request.getWalletId());
        
        // Lock wallet
        Wallet wallet = walletRepository.findByIdWithPessimisticLock(request.getWalletId())
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + request.getWalletId()));
        
        // Initialize fund reservations
        fundReservationService.initializeWalletReservations(wallet);
        
        // Validate wallet
        if (wallet.getStatus() != Wallet.WalletStatus.ACTIVE) {
            throw new IllegalStateException("Wallet is not active: " + wallet.getStatus());
        }
        
        // Create transaction
        Transaction transaction = Transaction.builder()
            .id(UUID.randomUUID())
            .targetWalletId(wallet.getId())
            .amount(request.getAmount())
            .currency(wallet.getCurrency())
            .type(TransactionType.DEPOSIT)
            .description(request.getDescription())
            .status(Transaction.Status.PENDING)
            .createdAt(LocalDateTime.now())
            .build();
        
        transaction = transactionRepository.save(transaction);
        
        try {
            // Execute deposit in external system
            String externalId = integrationService.deposit(wallet, request.getAmount());
            
            // Credit wallet
            wallet.credit(request.getAmount());
            walletRepository.save(wallet);
            
            // Complete transaction
            transaction.complete(externalId);
            transaction = transactionRepository.save(transaction);
            
            // Log for audit
            transactionLogger.logTransaction(transaction);
            
            return mapToTransactionResponse(transaction);
            
        } catch (Exception e) {
            transaction.fail(e.getMessage());
            transactionRepository.save(transaction);
            throw new TransactionFailedException("Deposit failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Atomic withdrawal operation
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse executeAtomicWithdrawal(WithdrawRequest request, UUID authenticatedUserId) {
        UUID transactionId = UUID.randomUUID();
        FundReservation reservation = null;
        
        log.info("SECURITY: Executing atomic withdrawal of {} from wallet {}", 
            request.getAmount(), request.getWalletId());
        
        try {
            // Lock wallet
            Wallet wallet = walletRepository.findByIdWithPessimisticLock(request.getWalletId())
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + request.getWalletId()));
            
            // Initialize fund reservations
            fundReservationService.initializeWalletReservations(wallet);
            
            // Verify ownership
            if (!wallet.getUserId().equals(authenticatedUserId)) {
                throw new SecurityException("Unauthorized: User does not own wallet");
            }
            
            // Validate wallet
            if (wallet.getStatus() != Wallet.WalletStatus.ACTIVE) {
                throw new IllegalStateException("Wallet is not active: " + wallet.getStatus());
            }
            
            // ✅ CRITICAL PRODUCTION FIX: Use WalletBalanceService instead of deprecated Wallet methods
            // This prevents UnsupportedOperationException and uses proper persistent fund reservations
            reservation = walletBalanceService.reserveFunds(
                wallet.getId(),
                request.getAmount(),
                transactionId,
                "withdrawal_" + request.getIdempotencyKey()
            );
            // Note: WalletBalanceService handles wallet save internally
            
            // Create transaction
            Transaction transaction = Transaction.builder()
                .id(transactionId)
                .sourceWalletId(wallet.getId())
                .amount(request.getAmount())
                .currency(wallet.getCurrency())
                .type(TransactionType.WITHDRAWAL)
                .description(request.getDescription())
                .status(Transaction.Status.PENDING)
                .createdAt(LocalDateTime.now())
                .createdBy(authenticatedUserId.toString())
                .build();
            
            transaction = transactionRepository.save(transaction);
            
            // Execute withdrawal in external system
            String externalId = integrationService.withdraw(wallet, request.getAmount());

            // ✅ CRITICAL PRODUCTION FIX: Confirm reservation using WalletBalanceService
            walletBalanceService.confirmReservation(reservation.getId());
            
            // Complete transaction
            transaction.complete(externalId);
            transaction = transactionRepository.save(transaction);
            
            // Log for audit
            transactionLogger.logTransaction(transaction);
            
            return mapToTransactionResponse(transaction);
            
        } catch (Exception e) {
            // ✅ CRITICAL PRODUCTION FIX: Rollback using WalletBalanceService
            if (reservation != null) {
                try {
                    log.warn("SECURITY: Rolling back withdrawal reservation {} due to: {}",
                        reservation.getId(), e.getMessage());
                    walletBalanceService.releaseReservation(
                        reservation.getId(),
                        "Withdrawal failed: " + e.getMessage()
                    );
                } catch (Exception rollbackError) {
                    log.error("CRITICAL: Rollback failed for withdrawal {}", transactionId, rollbackError);
                    // Re-throw to ensure the issue is escalated
                    throw new TransactionFailedException(
                        "Rollback failed for withdrawal: " + rollbackError.getMessage(), rollbackError);
                }
            }

            throw new TransactionFailedException("Withdrawal failed: " + e.getMessage(), e);
        }
    }
    
    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
            .transactionId(transaction.getId())
            .sourceWalletId(transaction.getSourceWalletId())
            .targetWalletId(transaction.getTargetWalletId())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .type(transaction.getType())
            .status(transaction.getStatus().toString())
            .description(transaction.getDescription())
            .externalReferenceId(transaction.getExternalReferenceId())
            .createdAt(transaction.getCreatedAt())
            .completedAt(transaction.getCompletedAt())
            .build();
    }
    
    private void alertOperations(String message, Exception error) {
        try {
            log.error("OPERATIONS ALERT: {}", message, error);
            
            // Create alert payload
            Map<String, Object> alertData = Map.of(
                "service", "wallet-service",
                "alertType", "TRANSFER_FAILURE",
                "message", message,
                "error", error != null ? error.getMessage() : "Unknown error",
                "severity", "HIGH",
                "timestamp", System.currentTimeMillis(),
                "environment", getEnvironment()
            );
            
            // Send to operations monitoring topic
            kafkaTemplate.send("operations-alerts", alertData);
            
            // Send Slack notification if configured
            sendSlackAlert(message, error);
            
            // Send PagerDuty alert for critical issues
            if (isCriticalIssue(message, error)) {
                sendPagerDutyAlert(message, error);
            }
            
        } catch (Exception e) {
            log.error("Failed to send operations alert", e);
        }
    }
    
    /**
     * Send Slack notification for operations team
     */
    private void sendSlackAlert(String message, Exception error) {
        try {
            String slackMessage = String.format(
                "🚨 *Wallet Service Alert*\\n" +
                "*Message:* %s\\n" +
                "*Error:* %s\\n" +
                "*Service:* wallet-service\\n" +
                "*Time:* %s",
                message,
                error != null ? error.getMessage() : "None",
                java.time.LocalDateTime.now()
            );
            
            Map<String, Object> slackPayload = Map.of(
                "channel", "#operations-alerts",
                "text", slackMessage,
                "username", "Wallet Service Bot"
            );
            
            kafkaTemplate.send("slack-notifications", slackPayload);
            
        } catch (Exception e) {
            log.debug("Failed to send Slack alert: {}", e.getMessage());
        }
    }
    
    /**
     * Send PagerDuty alert for critical issues
     */
    private void sendPagerDutyAlert(String message, Exception error) {
        try {
            Map<String, Object> pagerDutyEvent = Map.of(
                "routing_key", "wallet-service-critical",
                "event_action", "trigger",
                "dedup_key", "wallet-transfer-failure-" + System.currentTimeMillis(),
                "payload", Map.of(
                    "summary", "Critical wallet transfer failure: " + message,
                    "source", "wallet-service",
                    "severity", "critical",
                    "component", "AtomicTransferService",
                    "custom_details", Map.of(
                        "error", error != null ? error.getMessage() : "Unknown",
                        "service", "wallet-service",
                        "timestamp", System.currentTimeMillis()
                    )
                )
            );
            
            kafkaTemplate.send("pagerduty-events", pagerDutyEvent);
            
        } catch (Exception e) {
            log.debug("Failed to send PagerDuty alert: {}", e.getMessage());
        }
    }
    
    /**
     * Check if issue requires immediate attention
     */
    private boolean isCriticalIssue(String message, Exception error) {
        if (message == null) return false;
        
        String msg = message.toLowerCase();
        return msg.contains("balance inconsistency") ||
               msg.contains("transaction rollback failed") ||
               msg.contains("wallet locked") ||
               msg.contains("critical error") ||
               (error instanceof DataIntegrityViolationException);
    }
    
    /**
     * Get current environment
     */
    private String getEnvironment() {
        return System.getProperty("spring.profiles.active", "unknown");
    }
    
    /**
     * Transfer context for maintaining state across phases
     */
    private static class TransferContext {
        final Wallet sourceWallet;
        final Wallet targetWallet;
        final TransferRequest request;
        final UUID transactionId;
        final UUID authenticatedUserId;
        
        TransferContext(Wallet sourceWallet, Wallet targetWallet, TransferRequest request, 
                       UUID transactionId, UUID authenticatedUserId) {
            this.sourceWallet = sourceWallet;
            this.targetWallet = targetWallet;
            this.request = request;
            this.transactionId = transactionId;
            this.authenticatedUserId = authenticatedUserId;
        }
    }
}