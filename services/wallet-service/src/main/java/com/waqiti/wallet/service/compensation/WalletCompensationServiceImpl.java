package com.waqiti.wallet.service.compensation;

import com.waqiti.common.kafka.dlq.compensation.CompensationService.CompensationResult;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.domain.FundReservation;
import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.TransactionType;
import com.waqiti.wallet.entity.CompensationAudit;
import com.waqiti.wallet.entity.CompensationType;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.TransactionRepository;
import com.waqiti.wallet.repository.FundReservationRepository;
import com.waqiti.wallet.repository.CompensationAuditRepository;
import com.waqiti.wallet.client.LedgerServiceClient;
import com.waqiti.wallet.client.NotificationServiceClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Production-Ready Wallet Compensation Service Implementation.
 *
 * Implements DLQ compensation strategies for wallet operations including:
 * - Balance adjustments for failed transactions
 * - Hold releases for authorization failures
 * - Transaction reversals for processing errors
 * - Fund reservation releases
 *
 * Key Features:
 * - Distributed idempotency tracking via Redis
 * - Financial precision with BigDecimal
 * - Comprehensive audit trail with ledger integration
 * - Transaction management with SERIALIZABLE isolation for financial ops
 * - Metrics tracking for all operations
 * - PagerDuty/Slack alerting for critical failures
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0 - Full Production Implementation
 * @since 2025-11-21
 */
@Service("dlqWalletCompensationService")
@RequiredArgsConstructor
@Slf4j
public class WalletCompensationServiceImpl implements com.waqiti.common.kafka.dlq.compensation.WalletCompensationService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final FundReservationRepository fundReservationRepository;
    private final CompensationAuditRepository compensationAuditRepository;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "wallet:compensation:idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
    private static final String SYSTEM_ACTOR = "SYSTEM_DLQ_COMPENSATION";

    /**
     * Adjust wallet balance for compensation.
     *
     * Use Cases:
     * - Refund for failed debit
     * - Correction for processing errors
     * - Balance restoration after system failures
     *
     * @param walletId Wallet to adjust
     * @param amount Amount to adjust (positive = credit, negative = debit)
     * @param currency Currency code (ISO 4217)
     * @param reason Human-readable compensation reason
     * @return CompensationResult with operation status
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30, rollbackFor = Exception.class)
    public CompensationResult adjustBalance(UUID walletId, BigDecimal amount, String currency, String reason) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        String compensationId = UUID.randomUUID().toString();

        log.info("COMPENSATION: Adjusting wallet balance - compensationId={}, walletId={}, amount={}, currency={}, reason={}",
                compensationId, walletId, amount, currency, reason);

        try {
            // 1. Validate inputs
            CompensationResult validationResult = validateBalanceAdjustmentInputs(walletId, amount, currency, reason);
            if (!validationResult.isSuccess()) {
                return validationResult;
            }

            // 2. Check idempotency (distributed via Redis)
            String idempotencyKey = buildIdempotencyKey("balance-adjustment", walletId, amount, currency);
            if (isAlreadyCompensated(idempotencyKey)) {
                log.info("COMPENSATION: Balance adjustment already processed (idempotent) - key={}", idempotencyKey);
                recordMetric("wallet.compensation.balance_adjustment.idempotent");
                return CompensationResult.success("Balance adjustment already processed");
            }

            // 3. Load wallet with pessimistic lock for atomic update
            Wallet wallet = walletRepository.findByIdWithPessimisticWriteLock(walletId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + walletId));

            // 4. Verify wallet status allows adjustments
            if (wallet.getStatus() == WalletStatus.CLOSED) {
                log.warn("COMPENSATION: Cannot adjust balance for closed wallet - walletId={}", walletId);
                recordMetric("wallet.compensation.balance_adjustment.rejected.closed");
                return CompensationResult.failure("Wallet is CLOSED - cannot adjust balance");
            }

            // 5. Verify currency match
            if (!wallet.getCurrency().equals(currency)) {
                log.error("COMPENSATION: Currency mismatch - wallet={}, requested={}", wallet.getCurrency(), currency);
                recordMetric("wallet.compensation.balance_adjustment.rejected.currency_mismatch");
                return CompensationResult.failure("Currency mismatch: wallet=" + wallet.getCurrency() + ", requested=" + currency);
            }

            // 6. Calculate new balance
            BigDecimal currentBalance = wallet.getBalance();
            BigDecimal currentAvailable = wallet.getAvailableBalance();
            BigDecimal newBalance = currentBalance.add(amount);
            BigDecimal newAvailable = currentAvailable.add(amount);

            // 7. Validate sufficient balance for debits
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                if (newAvailable.compareTo(BigDecimal.ZERO) < 0) {
                    log.warn("COMPENSATION: Insufficient balance for adjustment - current={}, adjustment={}",
                            currentAvailable, amount);
                    recordMetric("wallet.compensation.balance_adjustment.rejected.insufficient_balance");
                    return CompensationResult.failure("Insufficient balance for adjustment");
                }
            }

            // 8. Update wallet balance atomically
            wallet.setBalance(newBalance);
            wallet.setAvailableBalance(newAvailable);
            wallet.setLastModifiedBy(SYSTEM_ACTOR);
            wallet.setLastModifiedAt(LocalDateTime.now());
            walletRepository.saveAndFlush(wallet);

            // 9. Create compensation transaction record
            Transaction compensationTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .sourceWalletId(amount.compareTo(BigDecimal.ZERO) < 0 ? walletId : null)
                .targetWalletId(amount.compareTo(BigDecimal.ZERO) > 0 ? walletId : null)
                .amount(amount.abs())
                .currency(currency)
                .type(amount.compareTo(BigDecimal.ZERO) > 0 ? TransactionType.COMPENSATION_CREDIT : TransactionType.COMPENSATION_DEBIT)
                .status(Transaction.Status.COMPLETED)
                .description("DLQ Compensation: " + reason)
                .externalReferenceId(compensationId)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .createdBy(SYSTEM_ACTOR)
                .build();
            transactionRepository.save(compensationTxn);

            // 10. Create ledger entry for audit trail
            try {
                ledgerServiceClient.createCompensationEntry(
                    compensationId,
                    walletId,
                    amount,
                    currency,
                    "WALLET_BALANCE_ADJUSTMENT",
                    reason
                );
            } catch (Exception e) {
                log.error("COMPENSATION: Failed to create ledger entry - compensationId={}", compensationId, e);
                // Continue - ledger failure should not fail compensation
            }

            // 11. Create compensation audit record
            CompensationAudit audit = CompensationAudit.builder()
                .id(UUID.randomUUID())
                .compensationId(compensationId)
                .idempotencyKey(idempotencyKey)
                .compensationType(CompensationType.WALLET_BALANCE_ADJUSTMENT)
                .entityType("WALLET")
                .entityId(walletId)
                .amount(amount)
                .currency(currency)
                .reason(reason)
                .previousBalance(currentBalance)
                .newBalance(newBalance)
                .transactionId(compensationTxn.getId())
                .performedAt(LocalDateTime.now())
                .performedBy(SYSTEM_ACTOR)
                .status("COMPLETED")
                .build();
            compensationAuditRepository.save(audit);

            // 12. Send notification to wallet owner (async, non-blocking)
            try {
                notificationServiceClient.sendBalanceAdjustmentNotification(
                    wallet.getUserId(),
                    amount,
                    currency,
                    reason,
                    compensationId
                );
            } catch (Exception e) {
                log.warn("COMPENSATION: Failed to send notification - compensationId={}", compensationId, e);
                // Non-critical - continue
            }

            // 13. Record idempotency in Redis
            recordCompensation(idempotencyKey, compensationId);

            // 14. Record success metrics
            recordMetric("wallet.compensation.balance_adjustment.success");
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                recordMetric("wallet.compensation.balance_credit.success");
                meterRegistry.counter("wallet.compensation.balance_credit.amount", "currency", currency)
                    .increment(amount.doubleValue());
            } else {
                recordMetric("wallet.compensation.balance_debit.success");
                meterRegistry.counter("wallet.compensation.balance_debit.amount", "currency", currency)
                    .increment(amount.abs().doubleValue());
            }

            log.info("COMPENSATION: Wallet balance adjusted successfully - compensationId={}, walletId={}, " +
                    "previousBalance={}, newBalance={}, amount={}",
                    compensationId, walletId, currentBalance, newBalance, amount);

            return CompensationResult.success("Balance adjusted: " + amount + " " + currency, compensationId);

        } catch (EntityNotFoundException e) {
            log.error("COMPENSATION: Entity not found during balance adjustment - walletId={}", walletId, e);
            recordMetric("wallet.compensation.balance_adjustment.failure.not_found");
            return CompensationResult.failure("Entity not found: " + e.getMessage());
        } catch (Exception e) {
            log.error("COMPENSATION: Failed to adjust wallet balance - walletId={}", walletId, e);
            recordMetric("wallet.compensation.balance_adjustment.failure");
            alertCriticalFailure("Balance adjustment failed", walletId, e);
            return CompensationResult.failure("Balance adjustment failed: " + e.getMessage());
        } finally {
            timerSample.stop(meterRegistry.timer("wallet.compensation.balance_adjustment.duration"));
        }
    }

    /**
     * Release a fund reservation.
     *
     * Use Cases:
     * - Release authorization hold after payment failure
     * - Expire pending transaction holds
     * - Saga compensation for failed transfers
     *
     * @param reservationId ID of the reservation to release
     * @param reason Human-readable release reason
     * @return CompensationResult with operation status
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30, rollbackFor = Exception.class)
    public CompensationResult releaseHold(UUID reservationId, String reason) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        String compensationId = UUID.randomUUID().toString();

        log.info("COMPENSATION: Releasing fund reservation - compensationId={}, reservationId={}, reason={}",
                compensationId, reservationId, reason);

        try {
            // 1. Validate inputs
            if (reservationId == null) {
                return CompensationResult.failure("Reservation ID cannot be null");
            }
            if (reason == null || reason.isBlank()) {
                return CompensationResult.failure("Reason cannot be blank");
            }

            // 2. Check idempotency
            String idempotencyKey = buildIdempotencyKey("hold-release", reservationId);
            if (isAlreadyCompensated(idempotencyKey)) {
                log.info("COMPENSATION: Hold release already processed (idempotent) - key={}", idempotencyKey);
                recordMetric("wallet.compensation.hold_release.idempotent");
                return CompensationResult.success("Hold already released");
            }

            // 3. Load reservation
            FundReservation reservation = fundReservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found: " + reservationId));

            // 4. Verify reservation status
            if (reservation.getStatus() == FundReservation.Status.RELEASED) {
                log.info("COMPENSATION: Reservation already released - reservationId={}", reservationId);
                recordCompensation(idempotencyKey, "ALREADY_RELEASED");
                return CompensationResult.success("Reservation already released");
            }
            if (reservation.getStatus() == FundReservation.Status.CONFIRMED) {
                log.warn("COMPENSATION: Cannot release confirmed reservation - reservationId={}", reservationId);
                recordMetric("wallet.compensation.hold_release.rejected.confirmed");
                return CompensationResult.failure("Reservation has been confirmed - cannot release");
            }

            // 5. Load associated wallet with pessimistic lock
            Wallet wallet = walletRepository.findByIdWithPessimisticWriteLock(reservation.getWalletId())
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + reservation.getWalletId()));

            // 6. Release reservation amount back to available balance
            BigDecimal reservedAmount = reservation.getAmount();
            BigDecimal currentReserved = wallet.getReservedBalance();
            BigDecimal currentAvailable = wallet.getAvailableBalance();

            wallet.setReservedBalance(currentReserved.subtract(reservedAmount));
            wallet.setAvailableBalance(currentAvailable.add(reservedAmount));
            wallet.setLastModifiedBy(SYSTEM_ACTOR);
            wallet.setLastModifiedAt(LocalDateTime.now());
            walletRepository.saveAndFlush(wallet);

            // 7. Update reservation status
            reservation.setStatus(FundReservation.Status.RELEASED);
            reservation.setReleasedAt(LocalDateTime.now());
            reservation.setReleaseReason(reason);
            fundReservationRepository.save(reservation);

            // 8. Create ledger entry for audit trail
            try {
                ledgerServiceClient.createCompensationEntry(
                    compensationId,
                    wallet.getId(),
                    reservedAmount,
                    wallet.getCurrency(),
                    "WALLET_HOLD_RELEASE",
                    reason
                );
            } catch (Exception e) {
                log.warn("COMPENSATION: Failed to create ledger entry for hold release - compensationId={}", compensationId, e);
            }

            // 9. Create compensation audit record
            CompensationAudit audit = CompensationAudit.builder()
                .id(UUID.randomUUID())
                .compensationId(compensationId)
                .idempotencyKey(idempotencyKey)
                .compensationType(CompensationType.WALLET_HOLD_RELEASE)
                .entityType("RESERVATION")
                .entityId(reservationId)
                .relatedEntityId(wallet.getId())
                .amount(reservedAmount)
                .currency(wallet.getCurrency())
                .reason(reason)
                .performedAt(LocalDateTime.now())
                .performedBy(SYSTEM_ACTOR)
                .status("COMPLETED")
                .build();
            compensationAuditRepository.save(audit);

            // 10. Send notification to wallet owner
            try {
                notificationServiceClient.sendHoldReleaseNotification(
                    wallet.getUserId(),
                    reservedAmount,
                    wallet.getCurrency(),
                    reason,
                    compensationId
                );
            } catch (Exception e) {
                log.warn("COMPENSATION: Failed to send hold release notification - compensationId={}", compensationId, e);
            }

            // 11. Record idempotency
            recordCompensation(idempotencyKey, compensationId);

            // 12. Record metrics
            recordMetric("wallet.compensation.hold_release.success");
            meterRegistry.counter("wallet.compensation.hold_release.amount", "currency", wallet.getCurrency())
                .increment(reservedAmount.doubleValue());

            log.info("COMPENSATION: Fund reservation released successfully - compensationId={}, reservationId={}, amount={}",
                    compensationId, reservationId, reservedAmount);

            return CompensationResult.success("Hold released successfully", compensationId);

        } catch (EntityNotFoundException e) {
            log.error("COMPENSATION: Entity not found during hold release - reservationId={}", reservationId, e);
            recordMetric("wallet.compensation.hold_release.failure.not_found");
            return CompensationResult.failure("Entity not found: " + e.getMessage());
        } catch (Exception e) {
            log.error("COMPENSATION: Failed to release hold - reservationId={}", reservationId, e);
            recordMetric("wallet.compensation.hold_release.failure");
            alertCriticalFailure("Hold release failed", reservationId, e);
            return CompensationResult.failure("Hold release failed: " + e.getMessage());
        } finally {
            timerSample.stop(meterRegistry.timer("wallet.compensation.hold_release.duration"));
        }
    }

    /**
     * Reverse a wallet transaction.
     *
     * Use Cases:
     * - Reverse failed debit transaction
     * - Undo erroneous credit
     * - Correct duplicate transactions
     * - Saga compensation for failed transfers
     *
     * @param transactionId ID of the transaction to reverse
     * @param reason Human-readable reversal reason
     * @return CompensationResult with operation status
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30, rollbackFor = Exception.class)
    public CompensationResult reverseTransaction(UUID transactionId, String reason) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        String compensationId = UUID.randomUUID().toString();

        log.info("COMPENSATION: Reversing wallet transaction - compensationId={}, transactionId={}, reason={}",
                compensationId, transactionId, reason);

        try {
            // 1. Validate inputs
            if (transactionId == null) {
                return CompensationResult.failure("Transaction ID cannot be null");
            }
            if (reason == null || reason.isBlank()) {
                return CompensationResult.failure("Reason cannot be blank");
            }

            // 2. Check idempotency
            String idempotencyKey = buildIdempotencyKey("transaction-reversal", transactionId);
            if (isAlreadyCompensated(idempotencyKey)) {
                log.info("COMPENSATION: Transaction reversal already processed (idempotent) - key={}", idempotencyKey);
                recordMetric("wallet.compensation.transaction_reversal.idempotent");
                return CompensationResult.success("Transaction already reversed");
            }

            // 3. Load original transaction
            Transaction originalTxn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + transactionId));

            // 4. Verify transaction can be reversed
            if (originalTxn.getStatus() == Transaction.Status.REVERSED) {
                log.info("COMPENSATION: Transaction already reversed - transactionId={}", transactionId);
                recordCompensation(idempotencyKey, "ALREADY_REVERSED");
                return CompensationResult.success("Transaction already reversed");
            }
            if (originalTxn.getStatus() != Transaction.Status.COMPLETED) {
                log.warn("COMPENSATION: Cannot reverse transaction in status: {} - transactionId={}",
                        originalTxn.getStatus(), transactionId);
                recordMetric("wallet.compensation.transaction_reversal.rejected.invalid_status");
                return CompensationResult.failure("Transaction status: " + originalTxn.getStatus() + " - cannot reverse");
            }

            // 5. Check if already has a reversal transaction
            Optional<Transaction> existingReversal = transactionRepository.findByOriginalTransactionId(transactionId);
            if (existingReversal.isPresent()) {
                log.info("COMPENSATION: Reversal transaction already exists - transactionId={}, reversalId={}",
                        transactionId, existingReversal.get().getId());
                recordCompensation(idempotencyKey, existingReversal.get().getId().toString());
                return CompensationResult.success("Transaction already reversed");
            }

            // 6. Determine which wallet(s) to adjust
            UUID sourceWalletId = originalTxn.getSourceWalletId();
            UUID targetWalletId = originalTxn.getTargetWalletId();
            BigDecimal amount = originalTxn.getAmount();
            String currency = originalTxn.getCurrency();

            // 7. Reverse based on transaction type
            if (sourceWalletId != null && targetWalletId != null) {
                // Transfer reversal - credit source, debit target
                reverseTransferTransaction(sourceWalletId, targetWalletId, amount, currency, compensationId, reason);
            } else if (sourceWalletId != null) {
                // Debit reversal - credit source wallet
                reverseSingleWalletTransaction(sourceWalletId, amount, currency, compensationId, reason, true);
            } else if (targetWalletId != null) {
                // Credit reversal - debit target wallet
                reverseSingleWalletTransaction(targetWalletId, amount, currency, compensationId, reason, false);
            }

            // 8. Create reversal transaction record
            Transaction reversalTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .sourceWalletId(targetWalletId) // Reversed
                .targetWalletId(sourceWalletId) // Reversed
                .amount(amount)
                .currency(currency)
                .type(TransactionType.REVERSAL)
                .status(Transaction.Status.COMPLETED)
                .description("Reversal of transaction " + transactionId + ": " + reason)
                .originalTransactionId(transactionId)
                .externalReferenceId(compensationId)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .createdBy(SYSTEM_ACTOR)
                .build();
            transactionRepository.save(reversalTxn);

            // 9. Update original transaction status
            originalTxn.setStatus(Transaction.Status.REVERSED);
            originalTxn.setReversedAt(LocalDateTime.now());
            originalTxn.setReversalReason(reason);
            originalTxn.setReversalTransactionId(reversalTxn.getId());
            transactionRepository.save(originalTxn);

            // 10. Create ledger entry
            try {
                ledgerServiceClient.createReversalEntry(
                    compensationId,
                    transactionId,
                    reversalTxn.getId(),
                    sourceWalletId != null ? sourceWalletId : targetWalletId,
                    amount,
                    currency,
                    reason
                );
            } catch (Exception e) {
                log.warn("COMPENSATION: Failed to create ledger reversal entry - compensationId={}", compensationId, e);
            }

            // 11. Create compensation audit record
            CompensationAudit audit = CompensationAudit.builder()
                .id(UUID.randomUUID())
                .compensationId(compensationId)
                .idempotencyKey(idempotencyKey)
                .compensationType(CompensationType.WALLET_TRANSACTION_REVERSAL)
                .entityType("TRANSACTION")
                .entityId(transactionId)
                .relatedEntityId(reversalTxn.getId())
                .amount(amount)
                .currency(currency)
                .reason(reason)
                .performedAt(LocalDateTime.now())
                .performedBy(SYSTEM_ACTOR)
                .status("COMPLETED")
                .build();
            compensationAuditRepository.save(audit);

            // 12. Send notification
            try {
                UUID userId = sourceWalletId != null ?
                    walletRepository.findById(sourceWalletId).map(Wallet::getUserId).orElse(null) :
                    walletRepository.findById(targetWalletId).map(Wallet::getUserId).orElse(null);

                if (userId != null) {
                    notificationServiceClient.sendTransactionReversalNotification(
                        userId,
                        amount,
                        currency,
                        reason,
                        compensationId
                    );
                }
            } catch (Exception e) {
                log.warn("COMPENSATION: Failed to send reversal notification - compensationId={}", compensationId, e);
            }

            // 13. Record idempotency
            recordCompensation(idempotencyKey, compensationId);

            // 14. Record metrics
            recordMetric("wallet.compensation.transaction_reversal.success");
            meterRegistry.counter("wallet.compensation.transaction_reversal.amount", "currency", currency)
                .increment(amount.doubleValue());

            log.info("COMPENSATION: Transaction reversed successfully - compensationId={}, originalTxn={}, reversalTxn={}",
                    compensationId, transactionId, reversalTxn.getId());

            return CompensationResult.success("Transaction reversed successfully", compensationId);

        } catch (EntityNotFoundException e) {
            log.error("COMPENSATION: Entity not found during transaction reversal - transactionId={}", transactionId, e);
            recordMetric("wallet.compensation.transaction_reversal.failure.not_found");
            return CompensationResult.failure("Entity not found: " + e.getMessage());
        } catch (Exception e) {
            log.error("COMPENSATION: Failed to reverse transaction - transactionId={}", transactionId, e);
            recordMetric("wallet.compensation.transaction_reversal.failure");
            alertCriticalFailure("Transaction reversal failed", transactionId, e);
            return CompensationResult.failure("Transaction reversal failed: " + e.getMessage());
        } finally {
            timerSample.stop(meterRegistry.timer("wallet.compensation.transaction_reversal.duration"));
        }
    }

    // ========== Private Helper Methods ==========

    private CompensationResult validateBalanceAdjustmentInputs(UUID walletId, BigDecimal amount,
            String currency, String reason) {
        if (walletId == null) {
            return CompensationResult.failure("Wallet ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return CompensationResult.failure("Amount must be non-zero");
        }
        if (currency == null || currency.isBlank()) {
            return CompensationResult.failure("Currency cannot be blank");
        }
        if (reason == null || reason.isBlank()) {
            return CompensationResult.failure("Reason cannot be blank");
        }
        return CompensationResult.success("Valid");
    }

    private void reverseTransferTransaction(UUID sourceWalletId, UUID targetWalletId,
            BigDecimal amount, String currency, String compensationId, String reason) {

        // Lock wallets in sorted order to prevent deadlocks
        UUID firstId = sourceWalletId.compareTo(targetWalletId) < 0 ? sourceWalletId : targetWalletId;
        UUID secondId = sourceWalletId.compareTo(targetWalletId) < 0 ? targetWalletId : sourceWalletId;

        Wallet firstWallet = walletRepository.findByIdWithPessimisticWriteLock(firstId)
            .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + firstId));
        Wallet secondWallet = walletRepository.findByIdWithPessimisticWriteLock(secondId)
            .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + secondId));

        Wallet sourceWallet = firstId.equals(sourceWalletId) ? firstWallet : secondWallet;
        Wallet targetWallet = firstId.equals(targetWalletId) ? firstWallet : secondWallet;

        // Credit source wallet (was debited)
        sourceWallet.setBalance(sourceWallet.getBalance().add(amount));
        sourceWallet.setAvailableBalance(sourceWallet.getAvailableBalance().add(amount));
        sourceWallet.setLastModifiedBy(SYSTEM_ACTOR);
        sourceWallet.setLastModifiedAt(LocalDateTime.now());

        // Debit target wallet (was credited)
        if (targetWallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Target wallet has insufficient balance for reversal");
        }
        targetWallet.setBalance(targetWallet.getBalance().subtract(amount));
        targetWallet.setAvailableBalance(targetWallet.getAvailableBalance().subtract(amount));
        targetWallet.setLastModifiedBy(SYSTEM_ACTOR);
        targetWallet.setLastModifiedAt(LocalDateTime.now());

        walletRepository.saveAndFlush(sourceWallet);
        walletRepository.saveAndFlush(targetWallet);
    }

    private void reverseSingleWalletTransaction(UUID walletId, BigDecimal amount, String currency,
            String compensationId, String reason, boolean isCredit) {

        Wallet wallet = walletRepository.findByIdWithPessimisticWriteLock(walletId)
            .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + walletId));

        if (isCredit) {
            // Credit the wallet (reverse a debit)
            wallet.setBalance(wallet.getBalance().add(amount));
            wallet.setAvailableBalance(wallet.getAvailableBalance().add(amount));
        } else {
            // Debit the wallet (reverse a credit)
            if (wallet.getAvailableBalance().compareTo(amount) < 0) {
                throw new IllegalStateException("Wallet has insufficient balance for reversal");
            }
            wallet.setBalance(wallet.getBalance().subtract(amount));
            wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(amount));
        }

        wallet.setLastModifiedBy(SYSTEM_ACTOR);
        wallet.setLastModifiedAt(LocalDateTime.now());
        walletRepository.saveAndFlush(wallet);
    }

    private String buildIdempotencyKey(String operation, Object... parts) {
        StringBuilder key = new StringBuilder(IDEMPOTENCY_KEY_PREFIX).append(operation);
        for (Object part : parts) {
            key.append(":").append(part != null ? part.toString() : "null");
        }
        return key.toString();
    }

    private boolean isAlreadyCompensated(String idempotencyKey) {
        try {
            // Check Redis first (distributed cache)
            Boolean exists = redisTemplate.hasKey(idempotencyKey);
            if (Boolean.TRUE.equals(exists)) {
                return true;
            }

            // Check database for persistent idempotency
            return compensationAuditRepository.existsByIdempotencyKey(idempotencyKey);
        } catch (Exception e) {
            log.warn("COMPENSATION: Failed to check idempotency - key={}, proceeding cautiously", idempotencyKey, e);
            // On Redis failure, check database only
            return compensationAuditRepository.existsByIdempotencyKey(idempotencyKey);
        }
    }

    private void recordCompensation(String idempotencyKey, String compensationId) {
        try {
            // Store in Redis with TTL
            redisTemplate.opsForValue().set(idempotencyKey, compensationId, IDEMPOTENCY_TTL);
        } catch (Exception e) {
            log.warn("COMPENSATION: Failed to record idempotency in Redis - key={}", idempotencyKey, e);
            // Database record already created in audit table, so this is non-critical
        }
    }

    private void recordMetric(String metricName) {
        try {
            meterRegistry.counter(metricName).increment();
        } catch (Exception e) {
            log.debug("COMPENSATION: Failed to record metric - name={}", metricName);
        }
    }

    private void alertCriticalFailure(String message, Object entityId, Exception error) {
        try {
            log.error("CRITICAL_ALERT: {} - entityId={}, error={}", message, entityId, error.getMessage());

            // Record alert metric
            meterRegistry.counter("wallet.compensation.critical_failure",
                "type", message.replaceAll(" ", "_").toLowerCase())
                .increment();

            // The notification service client will handle PagerDuty/Slack integration
            notificationServiceClient.sendCriticalAlert(
                "Wallet Compensation Failure",
                String.format("%s - Entity: %s, Error: %s", message, entityId, error.getMessage()),
                "wallet-service",
                "HIGH"
            );
        } catch (Exception e) {
            log.error("COMPENSATION: Failed to send critical alert - original error: {}", message, e);
        }
    }
}
