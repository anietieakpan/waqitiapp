package com.waqiti.wallet.service;

import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.dto.BalanceUpdateRequest;
import com.waqiti.wallet.exception.*;
import com.waqiti.wallet.repository.WalletRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * PRODUCTION-GRADE DISTRIBUTED WALLET OPERATIONS SERVICE
 *
 * CRITICAL SECURITY: Replaces ALL JVM-local synchronization with distributed Redis locking
 *
 * This service provides thread-safe, cluster-safe balance operations using:
 * 1. Distributed Redis locking (prevents race conditions across instances)
 * 2. Optimistic locking with @Version (database-level concurrency control)
 * 3. Pessimistic locking with SELECT FOR UPDATE (row-level locks)
 * 4. Retry logic with exponential backoff (handles transient failures)
 * 5. Comprehensive audit logging (compliance & forensics)
 *
 * REPLACES: All deprecated synchronized methods in Wallet.java
 *
 * USE THIS SERVICE FOR:
 * - Balance updates (credit/debit)
 * - Fund transfers between wallets
 * - Balance queries (with locking if needed)
 * - Wallet freezing/unfreezing
 * - Any operation modifying wallet state
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-01
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.SERIALIZABLE, timeout = 30, rollbackFor = Exception.class)
public class DistributedWalletOperationsService {

    private final WalletRepository walletRepository;
    private final DistributedLockService distributedLockService;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter balanceUpdateSuccessCounter;
    private Counter balanceUpdateFailureCounter;
    private Counter lockTimeoutCounter;
    private Counter raceConditionPreventedCounter;

    // Configuration
    private static final int MAX_LOCK_WAIT_SECONDS = 10;
    private static final int LOCK_LEASE_SECONDS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    @PostConstruct
    public void initializeMetrics() {
        balanceUpdateSuccessCounter = Counter.builder("wallet.balance_update.success")
                .description("Successful balance updates")
                .register(meterRegistry);

        balanceUpdateFailureCounter = Counter.builder("wallet.balance_update.failure")
                .description("Failed balance updates")
                .register(meterRegistry);

        lockTimeoutCounter = Counter.builder("wallet.lock.timeout")
                .description("Lock acquisition timeouts")
                .register(meterRegistry);

        raceConditionPreventedCounter = Counter.builder("wallet.race_condition.prevented")
                .description("Race conditions prevented by distributed locking")
                .register(meterRegistry);
    }

    /**
     * CRITICAL: Credit wallet with distributed locking
     *
     * Thread-safe, cluster-safe credit operation that works across multiple service instances.
     *
     * @param walletId Wallet to credit
     * @param amount Amount to credit (must be positive)
     * @param reference Transaction reference for audit
     * @param reason Reason for credit
     * @throws WalletNotFoundException if wallet doesn't exist
     * @throws WalletNotActiveException if wallet is frozen/closed
     * @throws WalletLockException if unable to acquire lock
     */
    @Timed(value = "wallet.credit", description = "Time to credit wallet")
    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public void creditWallet(UUID walletId, BigDecimal amount, String reference, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }

        executeWithDistributedLock(walletId, wallet -> {
            validateWalletActive(wallet);

            BigDecimal newBalance = wallet.getBalance().add(amount);
            BigDecimal newAvailableBalance = wallet.getAvailableBalance().add(amount);

            wallet.setBalance(newBalance);
            wallet.setAvailableBalance(newAvailableBalance);
            wallet.setUpdatedAt(LocalDateTime.now());
            wallet.setUpdatedBy(reference);

            walletRepository.save(wallet);

            log.info("Wallet credited - WalletId: {}, Amount: {}, NewBalance: {}, Reference: {}, Reason: {}",
                    walletId, amount, newBalance, reference, reason);

            balanceUpdateSuccessCounter.increment();
        });
    }

    /**
     * CRITICAL: Debit wallet with distributed locking
     *
     * Thread-safe, cluster-safe debit operation with balance validation.
     *
     * @param walletId Wallet to debit
     * @param amount Amount to debit (must be positive)
     * @param reference Transaction reference
     * @param reason Reason for debit
     * @throws InsufficientBalanceException if wallet has insufficient available balance
     * @throws WalletNotFoundException if wallet doesn't exist
     * @throws WalletNotActiveException if wallet is frozen/closed
     */
    @Timed(value = "wallet.debit", description = "Time to debit wallet")
    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public void debitWallet(UUID walletId, BigDecimal amount, String reference, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }

        executeWithDistributedLock(walletId, wallet -> {
            validateWalletActive(wallet);

            if (wallet.getAvailableBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient available balance. Available: " + wallet.getAvailableBalance() +
                        ", Requested: " + amount
                );
            }

            BigDecimal newBalance = wallet.getBalance().subtract(amount);
            BigDecimal newAvailableBalance = wallet.getAvailableBalance().subtract(amount);

            wallet.setBalance(newBalance);
            wallet.setAvailableBalance(newAvailableBalance);
            wallet.setUpdatedAt(LocalDateTime.now());
            wallet.setUpdatedBy(reference);

            walletRepository.save(wallet);

            log.info("Wallet debited - WalletId: {}, Amount: {}, NewBalance: {}, Reference: {}, Reason: {}",
                    walletId, amount, newBalance, reference, reason);

            balanceUpdateSuccessCounter.increment();
        });
    }

    /**
     * CRITICAL: Transfer funds between wallets with distributed locking
     *
     * Atomic transfer operation that locks BOTH wallets to prevent race conditions.
     * Locks are acquired in deterministic order (by wallet ID) to prevent deadlocks.
     *
     * @param sourceWalletId Source wallet
     * @param destinationWalletId Destination wallet
     * @param amount Amount to transfer
     * @param reference Transaction reference
     * @param reason Reason for transfer
     */
    @Timed(value = "wallet.transfer", description = "Time to transfer between wallets")
    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public void transferBetweenWallets(
            UUID sourceWalletId,
            UUID destinationWalletId,
            BigDecimal amount,
            String reference,
            String reason) {

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        if (sourceWalletId.equals(destinationWalletId)) {
            throw new IllegalArgumentException("Cannot transfer to same wallet");
        }

        // Lock in deterministic order to prevent deadlocks
        UUID firstLock = sourceWalletId.compareTo(destinationWalletId) < 0 ? sourceWalletId : destinationWalletId;
        UUID secondLock = sourceWalletId.compareTo(destinationWalletId) < 0 ? destinationWalletId : sourceWalletId;

        executeWithDistributedLock(firstLock, wallet1 -> {
            executeWithDistributedLock(secondLock, wallet2 -> {
                // Load both wallets with pessimistic lock
                Wallet sourceWallet = walletRepository.findByIdWithPessimisticLock(sourceWalletId)
                        .orElseThrow(() -> new WalletNotFoundException("Source wallet not found: " + sourceWalletId));

                Wallet destinationWallet = walletRepository.findByIdWithPessimisticLock(destinationWalletId)
                        .orElseThrow(() -> new WalletNotFoundException("Destination wallet not found: " + destinationWalletId));

                // Validate both wallets
                validateWalletActive(sourceWallet);
                validateWalletActive(destinationWallet);

                // Check sufficient balance
                if (sourceWallet.getAvailableBalance().compareTo(amount) < 0) {
                    throw new InsufficientBalanceException(
                            "Source wallet has insufficient balance. Available: " + sourceWallet.getAvailableBalance() +
                            ", Requested: " + amount
                    );
                }

                // Debit source
                LocalDateTime now = LocalDateTime.now();
                sourceWallet.setBalance(sourceWallet.getBalance().subtract(amount));
                sourceWallet.setAvailableBalance(sourceWallet.getAvailableBalance().subtract(amount));
                sourceWallet.setUpdatedAt(now);
                sourceWallet.setUpdatedBy(reference);

                // Credit destination
                destinationWallet.setBalance(destinationWallet.getBalance().add(amount));
                destinationWallet.setAvailableBalance(destinationWallet.getAvailableBalance().add(amount));
                destinationWallet.setUpdatedAt(now);
                destinationWallet.setUpdatedBy(reference);

                // Save both (atomic within transaction)
                walletRepository.save(sourceWallet);
                walletRepository.save(destinationWallet);

                log.info("Transfer completed - Source: {}, Destination: {}, Amount: {}, Reference: {}, Reason: {}",
                        sourceWalletId, destinationWalletId, amount, reference, reason);

                balanceUpdateSuccessCounter.increment();
            });
        });
    }

    /**
     * CRITICAL: Update wallet balance with distributed locking
     *
     * Generic balance update operation for admin/reconciliation purposes.
     *
     * @param request Balance update request
     */
    @Timed(value = "wallet.update_balance", description = "Time to update balance")
    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public void updateBalance(BalanceUpdateRequest request) {
        executeWithDistributedLock(request.getWalletId(), wallet -> {
            validateWalletActive(wallet);

            BigDecimal difference = request.getNewBalance().subtract(wallet.getBalance());

            wallet.setBalance(request.getNewBalance());
            wallet.setAvailableBalance(wallet.getAvailableBalance().add(difference));
            wallet.setUpdatedAt(LocalDateTime.now());
            wallet.setUpdatedBy(request.getReference());

            walletRepository.save(wallet);

            log.info("Balance updated - WalletId: {}, OldBalance: {}, NewBalance: {}, Difference: {}, Reference: {}",
                    request.getWalletId(), wallet.getBalance(), request.getNewBalance(), difference, request.getReference());

            balanceUpdateSuccessCounter.increment();
        });
    }

    /**
     * CRITICAL: Execute operation with distributed lock
     *
     * Core method that provides distributed locking for all wallet operations.
     * Ensures only ONE operation per wallet across entire cluster at any time.
     *
     * @param walletId Wallet ID to lock
     * @param operation Operation to execute while holding lock
     */
    private void executeWithDistributedLock(UUID walletId, Consumer<Wallet> operation) {
        String lockKey = "wallet:lock:" + walletId;
        RLock lock = distributedLockService.getLock(lockKey);
        boolean lockAcquired = false;

        try {
            // Try to acquire lock with timeout
            lockAcquired = lock.tryLock(MAX_LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);

            if (!lockAcquired) {
                lockTimeoutCounter.increment();
                log.error("Failed to acquire lock for wallet: {} after {} seconds",
                        walletId, MAX_LOCK_WAIT_SECONDS);
                throw new WalletLockException(
                        "Unable to acquire lock for wallet: " + walletId +
                        ". System is under high load. Please retry."
                );
            }

            raceConditionPreventedCounter.increment();
            log.debug("Acquired distributed lock for wallet: {}", walletId);

            // Load wallet with pessimistic lock
            Wallet wallet = walletRepository.findByIdWithPessimisticLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

            // Execute operation
            operation.accept(wallet);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while waiting for lock - WalletId: {}", walletId, e);
            throw new WalletLockException("Thread interrupted while acquiring wallet lock", e);

        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic locking failure for wallet: {}, retrying...", walletId);
            throw e; // Will trigger @Retryable

        } catch (InsufficientBalanceException | WalletNotFoundException | WalletNotActiveException |
                 WalletLockException e) {
            balanceUpdateFailureCounter.increment();
            throw e;

        } catch (Exception e) {
            balanceUpdateFailureCounter.increment();
            log.error("Unexpected error during wallet operation - WalletId: {}", walletId, e);
            throw new WalletOperationException("Failed to execute wallet operation: " + e.getMessage(), e);

        } finally {
            // CRITICAL: Always release lock
            if (lockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock for wallet: {}", walletId);
            }
        }
    }

    /**
     * Validate wallet is in ACTIVE status
     */
    private void validateWalletActive(Wallet wallet) {
        if (wallet.getStatus() != com.waqiti.wallet.domain.WalletStatus.ACTIVE) {
            throw new WalletNotActiveException(
                    "Wallet is not active: " + wallet.getStatus()
            );
        }
    }

    /**
     * Get wallet balance (read-only, no lock needed)
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
        return wallet.getBalance();
    }

    /**
     * Get available balance (read-only, no lock needed)
     */
    @Transactional(readOnly = true)
    public BigDecimal getAvailableBalance(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
        return wallet.getAvailableBalance();
    }
}
