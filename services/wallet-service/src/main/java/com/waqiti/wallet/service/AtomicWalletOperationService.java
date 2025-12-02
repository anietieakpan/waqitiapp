package com.waqiti.wallet.service;

import com.waqiti.common.locking.DistributedLock;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletTransaction;
import com.waqiti.wallet.domain.WalletTransactionStatus;
import com.waqiti.wallet.domain.WalletTransactionType;
import com.waqiti.wallet.exception.InsufficientBalanceException;
import com.waqiti.wallet.exception.WalletLockException;
import com.waqiti.wallet.exception.WalletNotFoundException;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.WalletTransactionRepository;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade atomic wallet operation service with distributed locking.
 * Ensures thread-safe and race-condition-free balance updates across distributed systems.
 * 
 * Features:
 * - Distributed locking with Redis for concurrent operation safety
 * - Optimistic locking with version control at database level
 * - Automatic retry with exponential backoff
 * - Comprehensive audit logging
 * - Performance monitoring and metrics
 * - Idempotency support for duplicate request handling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtomicWalletOperationService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final DistributedLockService distributedLockService;
    private final WalletAuditService auditService;
    private final WalletEventPublisher eventPublisher;
    
    @Value("${wallet.lock.timeout.seconds:10}")
    private int lockTimeoutSeconds;
    
    @Value("${wallet.lock.lease.seconds:30}")
    private int lockLeaseSeconds;
    
    @Value("${wallet.transaction.max-retries:3}")
    private int maxRetries;
    
    @Value("${wallet.balance.precision:2}")
    private int balancePrecision;

    /**
     * Performs an atomic debit operation on a wallet with distributed locking.
     * 
     * @param walletId The wallet to debit
     * @param amount The amount to debit
     * @param transactionId Unique transaction ID for idempotency
     * @param description Transaction description
     * @return The completed wallet transaction
     */
    @Timed("wallet.operation.debit")
    @Retryable(
        value = {OptimisticLockingFailureException.class, WalletLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2)
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRED)
    public WalletTransaction debitWallet(UUID walletId, BigDecimal amount, UUID transactionId, String description) {
        log.info("Starting atomic debit operation: walletId={}, amount={}, transactionId={}", 
                walletId, amount, transactionId);
        
        // Check for idempotency
        if (isTransactionProcessed(transactionId)) {
            log.warn("Transaction already processed: {}", transactionId);
            return transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new IllegalStateException("Transaction record missing: " + transactionId));
        }
        
        // Acquire distributed lock
        String lockKey = DistributedLockService.FinancialLocks.walletBalanceUpdate(walletId);
        DistributedLock lock = distributedLockService.acquireLock(
            lockKey, 
            Duration.ofSeconds(lockTimeoutSeconds), 
            Duration.ofSeconds(lockLeaseSeconds)
        );
        
        if (lock == null) {
            throw new WalletLockException("Failed to acquire lock for wallet: " + walletId);
        }
        
        try {
            // Fetch wallet with pessimistic lock at database level
            Wallet wallet = walletRepository.findByIdWithLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
            
            // Validate the debit operation
            validateDebitOperation(wallet, amount);
            
            // Calculate new balance with proper precision
            BigDecimal newBalance = wallet.getAvailableBalance()
                    .subtract(amount)
                    .setScale(balancePrecision, RoundingMode.HALF_UP);
            
            // Update wallet balance
            wallet.setAvailableBalance(newBalance);
            wallet.setLastTransactionAt(Instant.now());
            wallet.incrementVersion(); // Optimistic locking version
            
            // Create transaction record
            WalletTransaction transaction = WalletTransaction.builder()
                    .id(transactionId)
                    .walletId(walletId)
                    .type(WalletTransactionType.DEBIT)
                    .amount(amount)
                    .balanceBefore(wallet.getAvailableBalance().add(amount))
                    .balanceAfter(newBalance)
                    .description(description)
                    .status(WalletTransactionStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();
            
            // Persist changes
            wallet = walletRepository.save(wallet);
            transaction = transactionRepository.save(transaction);
            
            // Audit the operation
            auditService.auditDebitOperation(wallet, transaction);
            
            // Publish event for downstream services
            eventPublisher.publishDebitEvent(wallet, transaction);
            
            log.info("Debit operation completed successfully: walletId={}, newBalance={}", 
                    walletId, newBalance);
            
            return transaction;
            
        } finally {
            // Always release the lock
            lock.release();
        }
    }

    /**
     * Performs an atomic credit operation on a wallet with distributed locking.
     * 
     * @param walletId The wallet to credit
     * @param amount The amount to credit
     * @param transactionId Unique transaction ID for idempotency
     * @param description Transaction description
     * @return The completed wallet transaction
     */
    @Timed("wallet.operation.credit")
    @Retryable(
        value = {OptimisticLockingFailureException.class, WalletLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2)
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRED)
    public WalletTransaction creditWallet(UUID walletId, BigDecimal amount, UUID transactionId, String description) {
        log.info("Starting atomic credit operation: walletId={}, amount={}, transactionId={}", 
                walletId, amount, transactionId);
        
        // Check for idempotency
        if (isTransactionProcessed(transactionId)) {
            log.warn("Transaction already processed: {}", transactionId);
            return transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new IllegalStateException("Transaction record missing: " + transactionId));
        }
        
        // Acquire distributed lock
        String lockKey = DistributedLockService.FinancialLocks.walletBalanceUpdate(walletId);
        DistributedLock lock = distributedLockService.acquireLock(
            lockKey, 
            Duration.ofSeconds(lockTimeoutSeconds), 
            Duration.ofSeconds(lockLeaseSeconds)
        );
        
        if (lock == null) {
            throw new WalletLockException("Failed to acquire lock for wallet: " + walletId);
        }
        
        try {
            // Fetch wallet with pessimistic lock at database level
            Wallet wallet = walletRepository.findByIdWithLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
            
            // Validate the credit operation
            validateCreditOperation(wallet, amount);
            
            // Calculate new balance with proper precision
            BigDecimal newBalance = wallet.getAvailableBalance()
                    .add(amount)
                    .setScale(balancePrecision, RoundingMode.HALF_UP);
            
            // Check for overflow
            if (newBalance.compareTo(wallet.getMaxBalance()) > 0) {
                throw new IllegalArgumentException("Credit would exceed maximum wallet balance");
            }
            
            // Update wallet balance
            wallet.setAvailableBalance(newBalance);
            wallet.setLastTransactionAt(Instant.now());
            wallet.incrementVersion(); // Optimistic locking version
            
            // Create transaction record
            WalletTransaction transaction = WalletTransaction.builder()
                    .id(transactionId)
                    .walletId(walletId)
                    .type(WalletTransactionType.CREDIT)
                    .amount(amount)
                    .balanceBefore(wallet.getAvailableBalance().subtract(amount))
                    .balanceAfter(newBalance)
                    .description(description)
                    .status(WalletTransactionStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();
            
            // Persist changes
            wallet = walletRepository.save(wallet);
            transaction = transactionRepository.save(transaction);
            
            // Audit the operation
            auditService.auditCreditOperation(wallet, transaction);
            
            // Publish event for downstream services
            eventPublisher.publishCreditEvent(wallet, transaction);
            
            log.info("Credit operation completed successfully: walletId={}, newBalance={}", 
                    walletId, newBalance);
            
            return transaction;
            
        } finally {
            // Always release the lock
            lock.release();
        }
    }

    /**
     * Performs an atomic transfer between wallets with distributed locking.
     * Uses ordered locking to prevent deadlocks.
     * 
     * @param fromWalletId Source wallet ID
     * @param toWalletId Destination wallet ID
     * @param amount Transfer amount
     * @param transactionId Unique transaction ID
     * @param description Transfer description
     * @return Transfer transaction details
     */
    @Timed("wallet.operation.transfer")
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 30)
    @Retryable(
        value = {OptimisticLockException.class, SerializationException.class, TransactionSystemException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public TransferResult atomicTransfer(
            UUID fromWalletId,
            UUID toWalletId,
            BigDecimal amount,
            UUID transactionId,
            String description) {
        
        log.info("Starting atomic transfer: from={}, to={}, amount={}, transactionId={}", 
                fromWalletId, toWalletId, amount, transactionId);
        
        // Check for idempotency
        if (isTransactionProcessed(transactionId)) {
            log.warn("Transfer already processed: {}", transactionId);
            return getTransferResult(transactionId);
        }
        
        // Order wallet IDs to prevent deadlocks
        List<UUID> orderedWalletIds = orderWalletIds(fromWalletId, toWalletId);
        List<DistributedLock> locks = new ArrayList<>();
        
        try {
            // Acquire locks in order
            for (UUID walletId : orderedWalletIds) {
                String lockKey = DistributedLockService.FinancialLocks.walletBalanceUpdate(walletId);
                DistributedLock lock = distributedLockService.acquireLock(
                    lockKey,
                    Duration.ofSeconds(lockTimeoutSeconds * 2), // Double timeout for transfers
                    Duration.ofSeconds(lockLeaseSeconds)
                );
                
                if (lock == null) {
                    throw new WalletLockException("Failed to acquire lock for wallet: " + walletId);
                }
                locks.add(lock);
            }
            
            // Fetch both wallets with pessimistic locks
            Wallet fromWallet = walletRepository.findByIdWithLock(fromWalletId)
                    .orElseThrow(() -> new WalletNotFoundException("Source wallet not found: " + fromWalletId));
            
            Wallet toWallet = walletRepository.findByIdWithLock(toWalletId)
                    .orElseThrow(() -> new WalletNotFoundException("Destination wallet not found: " + toWalletId));
            
            // Validate the transfer
            validateTransfer(fromWallet, toWallet, amount);
            
            // Store original balances for audit
            BigDecimal fromBalanceBefore = fromWallet.getAvailableBalance();
            BigDecimal toBalanceBefore = toWallet.getAvailableBalance();
            
            // Perform the transfer with proper precision
            BigDecimal fromNewBalance = fromBalanceBefore
                    .subtract(amount)
                    .setScale(balancePrecision, RoundingMode.HALF_UP);
            
            BigDecimal toNewBalance = toBalanceBefore
                    .add(amount)
                    .setScale(balancePrecision, RoundingMode.HALF_UP);
            
            // Update wallet balances
            fromWallet.setAvailableBalance(fromNewBalance);
            fromWallet.setLastTransactionAt(Instant.now());
            fromWallet.incrementVersion();
            
            toWallet.setAvailableBalance(toNewBalance);
            toWallet.setLastTransactionAt(Instant.now());
            toWallet.incrementVersion();
            
            // Create transaction records
            WalletTransaction debitTransaction = WalletTransaction.builder()
                    .id(UUID.randomUUID())
                    .walletId(fromWalletId)
                    .type(WalletTransactionType.TRANSFER_OUT)
                    .amount(amount)
                    .balanceBefore(fromBalanceBefore)
                    .balanceAfter(fromNewBalance)
                    .description(description)
                    .relatedTransactionId(transactionId)
                    .status(WalletTransactionStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();
            
            WalletTransaction creditTransaction = WalletTransaction.builder()
                    .id(UUID.randomUUID())
                    .walletId(toWalletId)
                    .type(WalletTransactionType.TRANSFER_IN)
                    .amount(amount)
                    .balanceBefore(toBalanceBefore)
                    .balanceAfter(toNewBalance)
                    .description(description)
                    .relatedTransactionId(transactionId)
                    .status(WalletTransactionStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();
            
            // Persist all changes atomically
            walletRepository.save(fromWallet);
            walletRepository.save(toWallet);
            transactionRepository.save(debitTransaction);
            transactionRepository.save(creditTransaction);
            
            // Create transfer result
            TransferResult result = TransferResult.builder()
                    .transferId(transactionId)
                    .fromWalletId(fromWalletId)
                    .toWalletId(toWalletId)
                    .amount(amount)
                    .fromBalanceBefore(fromBalanceBefore)
                    .fromBalanceAfter(fromNewBalance)
                    .toBalanceBefore(toBalanceBefore)
                    .toBalanceAfter(toNewBalance)
                    .debitTransactionId(debitTransaction.getId())
                    .creditTransactionId(creditTransaction.getId())
                    .status(TransferStatus.COMPLETED)
                    .completedAt(Instant.now())
                    .build();
            
            // Audit the transfer
            auditService.auditTransfer(result);
            
            // Publish transfer event
            eventPublisher.publishTransferEvent(result);
            
            log.info("Transfer completed successfully: {}", result);
            
            return result;
            
        } finally {
            // Release all locks in reverse order
            for (int i = locks.size() - 1; i >= 0; i--) {
                locks.get(i).release();
            }
        }
    }

    /**
     * Places a hold on wallet funds for authorization.
     * 
     * @param walletId The wallet ID
     * @param amount The amount to hold
     * @param holdId Unique hold identifier
     * @param expiryMinutes Hold expiry time in minutes
     * @return The hold details
     */
    @Timed("wallet.operation.hold")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public WalletHold placeHold(UUID walletId, BigDecimal amount, UUID holdId, int expiryMinutes) {
        log.info("Placing hold on wallet: walletId={}, amount={}, holdId={}", walletId, amount, holdId);
        
        String lockKey = DistributedLockService.FinancialLocks.walletBalanceUpdate(walletId);
        DistributedLock lock = distributedLockService.acquireLock(
            lockKey,
            Duration.ofSeconds(lockTimeoutSeconds),
            Duration.ofSeconds(lockLeaseSeconds)
        );
        
        if (lock == null) {
            throw new WalletLockException("Failed to acquire lock for wallet: " + walletId);
        }
        
        try {
            Wallet wallet = walletRepository.findByIdWithLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
            
            // Check available balance (excluding existing holds)
            BigDecimal availableForHold = wallet.getAvailableBalance().subtract(wallet.getTotalHeldAmount());
            
            if (availableForHold.compareTo(amount) < 0) {
                throw new InsufficientBalanceException(
                    String.format("Insufficient balance for hold. Available: %s, Requested: %s", 
                        availableForHold, amount)
                );
            }
            
            // Update held amount
            wallet.setTotalHeldAmount(wallet.getTotalHeldAmount().add(amount));
            wallet.incrementVersion();
            
            // Create hold record
            WalletHold hold = WalletHold.builder()
                    .id(holdId)
                    .walletId(walletId)
                    .amount(amount)
                    .status(HoldStatus.ACTIVE)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(expiryMinutes * 60))
                    .build();
            
            // Persist changes
            walletRepository.save(wallet);
            holdRepository.save(hold);
            
            // Schedule hold expiry
            scheduleHoldExpiry(hold);
            
            log.info("Hold placed successfully: {}", hold);
            
            return hold;
            
        } finally {
            lock.release();
        }
    }

    /**
     * Releases a hold on wallet funds.
     * 
     * @param walletId The wallet ID
     * @param holdId The hold to release
     * @return true if released successfully
     */
    @Timed("wallet.operation.release-hold")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public boolean releaseHold(UUID walletId, UUID holdId) {
        log.info("Releasing hold: walletId={}, holdId={}", walletId, holdId);
        
        String lockKey = DistributedLockService.FinancialLocks.walletBalanceUpdate(walletId);
        DistributedLock lock = distributedLockService.acquireLock(
            lockKey,
            Duration.ofSeconds(lockTimeoutSeconds),
            Duration.ofSeconds(lockLeaseSeconds)
        );
        
        if (lock == null) {
            throw new WalletLockException("Failed to acquire lock for wallet: " + walletId);
        }
        
        try {
            WalletHold hold = holdRepository.findById(holdId)
                    .orElseThrow(() -> new IllegalArgumentException("Hold not found: " + holdId));
            
            if (!hold.getWalletId().equals(walletId)) {
                throw new IllegalArgumentException("Hold does not belong to wallet");
            }
            
            if (hold.getStatus() != HoldStatus.ACTIVE) {
                log.warn("Hold is not active: {}", hold);
                return false;
            }
            
            Wallet wallet = walletRepository.findByIdWithLock(walletId)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
            
            // Release the held amount
            wallet.setTotalHeldAmount(wallet.getTotalHeldAmount().subtract(hold.getAmount()));
            wallet.incrementVersion();
            
            // Update hold status
            hold.setStatus(HoldStatus.RELEASED);
            hold.setReleasedAt(Instant.now());
            
            // Persist changes
            walletRepository.save(wallet);
            holdRepository.save(hold);
            
            log.info("Hold released successfully: {}", hold);
            
            return true;
            
        } finally {
            lock.release();
        }
    }

    // Validation methods
    
    private void validateDebitOperation(Wallet wallet, BigDecimal amount) {
        if (wallet.isFrozen()) {
            throw new IllegalStateException("Wallet is frozen: " + wallet.getId());
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        
        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                String.format("Insufficient balance. Available: %s, Requested: %s", 
                    wallet.getAvailableBalance(), amount)
            );
        }
        
        // Check daily transaction limits
        if (wallet.getDailyTransactionLimit() != null) {
            BigDecimal dailyTotal = calculateDailyTransactionTotal(wallet.getId());
            if (dailyTotal.add(amount).compareTo(wallet.getDailyTransactionLimit()) > 0) {
                throw new IllegalArgumentException("Daily transaction limit exceeded");
            }
        }
    }
    
    private void validateCreditOperation(Wallet wallet, BigDecimal amount) {
        if (wallet.isFrozen()) {
            throw new IllegalStateException("Wallet is frozen: " + wallet.getId());
        }
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        
        // Check maximum balance limit
        if (wallet.getMaxBalance() != null) {
            BigDecimal newBalance = wallet.getAvailableBalance().add(amount);
            if (newBalance.compareTo(wallet.getMaxBalance()) > 0) {
                throw new IllegalArgumentException("Credit would exceed maximum wallet balance");
            }
        }
    }
    
    private void validateTransfer(Wallet fromWallet, Wallet toWallet, BigDecimal amount) {
        validateDebitOperation(fromWallet, amount);
        validateCreditOperation(toWallet, amount);
        
        // Additional transfer-specific validations
        if (fromWallet.getId().equals(toWallet.getId())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }
        
        // Check if transfer is allowed between wallet types
        if (!isTransferAllowed(fromWallet.getType(), toWallet.getType())) {
            throw new IllegalArgumentException(
                String.format("Transfer not allowed from %s to %s wallet", 
                    fromWallet.getType(), toWallet.getType())
            );
        }
    }
    
    // Helper methods
    
    private boolean isTransactionProcessed(UUID transactionId) {
        return transactionRepository.existsById(transactionId);
    }
    
    private List<UUID> orderWalletIds(UUID walletId1, UUID walletId2) {
        List<UUID> ordered = new ArrayList<>();
        if (walletId1.compareTo(walletId2) < 0) {
            ordered.add(walletId1);
            ordered.add(walletId2);
        } else {
            ordered.add(walletId2);
            ordered.add(walletId1);
        }
        return ordered;
    }
    
    private BigDecimal calculateDailyTransactionTotal(UUID walletId) {
        Instant startOfDay = Instant.now().truncatedTo(TimeUnit.DAYS.toChronoUnit());
        return transactionRepository.sumTransactionsSince(walletId, startOfDay);
    }
    
    private boolean isTransferAllowed(WalletType from, WalletType to) {
        // Define transfer rules based on wallet types
        // This is a simplified example - implement based on business rules
        return true;
    }
    
    private TransferResult getTransferResult(UUID transactionId) {
        // Retrieve existing transfer result for idempotent response
        return transferResultRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer result not found: " + transactionId));
    }
    
    private void scheduleHoldExpiry(WalletHold hold) {
        // Schedule automatic hold release on expiry
        CompletableFuture.delayedExecutor(
            hold.getExpiresAt().toEpochMilli() - Instant.now().toEpochMilli(),
            TimeUnit.MILLISECONDS
        ).execute(() -> {
            try {
                releaseHold(hold.getWalletId(), hold.getId());
            } catch (Exception e) {
                log.error("Failed to auto-release expired hold: {}", hold.getId(), e);
            }
        });
    }
}