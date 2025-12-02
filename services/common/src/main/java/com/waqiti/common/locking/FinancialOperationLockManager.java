package com.waqiti.common.locking;

import com.waqiti.common.exception.ConcurrencyException;
import com.waqiti.common.exception.DeadlockException;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Enhanced Financial Operation Lock Manager that provides comprehensive locking
 * strategies for preventing race conditions and double-spending in financial operations.
 * 
 * Addresses Critical Security Issues:
 * - Race conditions in balance updates
 * - Double-spending vulnerabilities
 * - Concurrent transaction conflicts
 * - Deadlock prevention
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialOperationLockManager {

    private final DistributedLockService distributedLockService;
    private final JdbcTemplate jdbcTemplate;
    
    // Lock configuration constants
    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(30);
    private static final Duration HIGH_VALUE_LOCK_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration HIGH_VALUE_LEASE_TIME = Duration.ofMinutes(1);
    
    // High-value transaction threshold
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000.00");
    
    /**
     * Executes a balance update operation with proper locking to prevent race conditions.
     * Uses both distributed locking (Redis) and database-level locking (SELECT FOR UPDATE).
     */
    @Timed("financial.lock.balance.update")
    public <T> T executeBalanceUpdate(UUID walletId, Supplier<T> operation) {
        String lockKey = DistributedLockService.FinancialLocks.walletBalanceUpdate(walletId);
        
        try (DistributedLock lock = acquireMandatoryLock(lockKey, DEFAULT_LOCK_TIMEOUT, DEFAULT_LEASE_TIME)) {
            // Also acquire database-level lock
            acquireDatabaseLockForWallet(walletId);
            
            log.debug("Executing balance update for wallet: {} with distributed and database locks", walletId);
            T result = operation.get();
            
            log.debug("Balance update completed successfully for wallet: {}", walletId);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to execute balance update for wallet: {}", walletId, e);
            throw new ConcurrencyException("Balance update failed due to locking error", e);
        }
    }
    
    /**
     * Executes a fund transfer with proper locking of both source and target wallets.
     * Prevents deadlocks by acquiring locks in a consistent order.
     */
    @Timed("financial.lock.transfer")
    public <T> T executeTransfer(UUID fromWalletId, UUID toWalletId, BigDecimal amount, Supplier<T> operation) {
        // Determine if this is a high-value transaction
        boolean isHighValue = amount.compareTo(HIGH_VALUE_THRESHOLD) > 0;
        Duration timeout = isHighValue ? HIGH_VALUE_LOCK_TIMEOUT : DEFAULT_LOCK_TIMEOUT;
        Duration leaseTime = isHighValue ? HIGH_VALUE_LEASE_TIME : DEFAULT_LEASE_TIME;
        
        // Order wallet IDs to prevent deadlock
        List<UUID> orderedWalletIds = orderWalletIds(fromWalletId, toWalletId);
        
        List<DistributedLock> locks = new ArrayList<>();
        try {
            // Acquire locks in order
            for (UUID walletId : orderedWalletIds) {
                String lockKey = DistributedLockService.FinancialLocks.walletBalanceUpdate(walletId);
                DistributedLock lock = acquireMandatoryLock(lockKey, timeout, leaseTime);
                locks.add(lock);
            }
            
            // Also acquire database-level locks in the same order
            for (UUID walletId : orderedWalletIds) {
                acquireDatabaseLockForWallet(walletId);
            }
            
            log.info("Executing transfer from {} to {} with amount {} - locks acquired", 
                    fromWalletId, toWalletId, amount);
            
            T result = operation.get();
            
            log.info("Transfer completed successfully from {} to {}", fromWalletId, toWalletId);
            return result;
            
        } finally {
            // Release locks in reverse order
            Collections.reverse(locks);
            for (DistributedLock lock : locks) {
                try {
                    lock.close();
                } catch (Exception e) {
                    log.error("Error releasing lock: {}", lock.getRedisKey(), e);
                }
            }
        }
    }
    
    /**
     * Executes a payment processing operation with comprehensive locking.
     */
    @Timed("financial.lock.payment")
    public <T> T executePaymentProcessing(UUID paymentId, UUID walletId, Supplier<T> operation) {
        String paymentLockKey = DistributedLockService.FinancialLocks.paymentProcessing(paymentId);
        String walletLockKey = DistributedLockService.FinancialLocks.walletBalanceUpdate(walletId);
        
        try (DistributedLock paymentLock = acquireMandatoryLock(paymentLockKey, DEFAULT_LOCK_TIMEOUT, DEFAULT_LEASE_TIME);
             DistributedLock walletLock = acquireMandatoryLock(walletLockKey, DEFAULT_LOCK_TIMEOUT, DEFAULT_LEASE_TIME)) {
            
            // Ensure payment hasn't already been processed
            if (isPaymentAlreadyProcessed(paymentId)) {
                throw new IllegalStateException("Payment already processed: " + paymentId);
            }
            
            log.debug("Executing payment processing for payment: {} with wallet: {}", paymentId, walletId);
            T result = operation.get();
            
            log.debug("Payment processing completed for payment: {}", paymentId);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to execute payment processing for payment: {}", paymentId, e);
            throw new ConcurrencyException("Payment processing failed due to locking error", e);
        }
    }
    
    /**
     * Executes fund reservation with proper locking to prevent double-spending.
     */
    @Timed("financial.lock.reservation")
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean executeFundReservation(UUID walletId, UUID transactionId, BigDecimal amount, 
                                         Duration reservationDuration) {
        String lockKey = DistributedLockService.FinancialLocks.walletBalanceUpdate(walletId);
        
        try (DistributedLock lock = acquireMandatoryLock(lockKey, DEFAULT_LOCK_TIMEOUT, DEFAULT_LEASE_TIME)) {
            
            // Check if reservation already exists
            if (reservationExists(walletId, transactionId)) {
                log.warn("Reservation already exists for wallet: {} and transaction: {}", walletId, transactionId);
                return false;
            }
            
            // Check available balance with database lock
            BigDecimal availableBalance = getAvailableBalanceWithLock(walletId);
            if (availableBalance.compareTo(amount) < 0) {
                log.warn("Insufficient funds for reservation: wallet={}, required={}, available={}", 
                        walletId, amount, availableBalance);
                return false;
            }
            
            // Create reservation record
            createFundReservation(walletId, transactionId, amount, reservationDuration);
            
            // Update available balance
            updateAvailableBalance(walletId, availableBalance.subtract(amount));
            
            log.info("Fund reservation created: wallet={}, transaction={}, amount={}", 
                    walletId, transactionId, amount);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to create fund reservation for wallet: {}", walletId, e);
            throw new ConcurrencyException("Fund reservation failed", e);
        }
    }
    
    /**
     * Executes a ledger entry with proper locking to ensure double-entry consistency.
     */
    @Timed("financial.lock.ledger")
    public <T> T executeLedgerEntry(String debitAccountId, String creditAccountId, Supplier<T> operation) {
        // Order account IDs to prevent deadlock
        List<String> orderedAccountIds = orderAccountIds(debitAccountId, creditAccountId);
        
        List<DistributedLock> locks = new ArrayList<>();
        try {
            // Acquire locks for both accounts
            for (String accountId : orderedAccountIds) {
                String lockKey = DistributedLockService.FinancialLocks.ledgerEntry(accountId);
                DistributedLock lock = acquireMandatoryLock(lockKey, DEFAULT_LOCK_TIMEOUT, DEFAULT_LEASE_TIME);
                locks.add(lock);
            }
            
            log.debug("Executing ledger entry: debit={}, credit={}", debitAccountId, creditAccountId);
            T result = operation.get();
            
            log.debug("Ledger entry completed successfully");
            return result;
            
        } finally {
            // Release locks in reverse order
            Collections.reverse(locks);
            for (DistributedLock lock : locks) {
                try {
                    lock.close();
                } catch (Exception e) {
                    log.error("Error releasing lock: {}", lock.getRedisKey(), e);
                }
            }
        }
    }
    
    /**
     * Executes multiple operations with ordered locking to prevent deadlocks.
     */
    public <T> T executeBatchOperationWithLocks(List<String> lockKeys, Supplier<T> operation) {
        // Sort lock keys to ensure consistent ordering
        List<String> sortedKeys = new ArrayList<>(lockKeys);
        Collections.sort(sortedKeys);
        
        List<DistributedLock> acquiredLocks = new ArrayList<>();
        
        try {
            // Acquire all locks in order
            for (String key : sortedKeys) {
                DistributedLock lock = acquireMandatoryLock(key, DEFAULT_LOCK_TIMEOUT, DEFAULT_LEASE_TIME);
                acquiredLocks.add(lock);
            }
            
            log.debug("Acquired {} locks for batch operation", acquiredLocks.size());
            T result = operation.get();
            
            log.debug("Batch operation completed successfully");
            return result;
            
        } finally {
            // Release all locks in reverse order
            Collections.reverse(acquiredLocks);
            for (DistributedLock lock : acquiredLocks) {
                try {
                    lock.close();
                } catch (Exception e) {
                    log.error("Error releasing lock in batch operation: {}", lock.getRedisKey(), e);
                }
            }
        }
    }
    
    /**
     * Tries to acquire a lock with timeout, throwing exception if failed.
     */
    private DistributedLock acquireMandatoryLock(String lockKey, Duration timeout, Duration leaseTime) {
        DistributedLock lock = distributedLockService.acquireLock(lockKey, timeout, leaseTime);
        
        if (lock == null) {
            throw new ConcurrencyException("Failed to acquire lock for key: " + lockKey + " within timeout: " + timeout);
        }
        
        return lock;
    }
    
    /**
     * Acquires database-level lock for a wallet using SELECT FOR UPDATE.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    private void acquireDatabaseLockForWallet(UUID walletId) {
        String sql = "SELECT id FROM wallets WHERE id = ? FOR UPDATE";
        
        try {
            jdbcTemplate.queryForObject(sql, UUID.class, walletId);
            log.debug("Database lock acquired for wallet: {}", walletId);
        } catch (Exception e) {
            log.error("Failed to acquire database lock for wallet: {}", walletId, e);
            throw new ConcurrencyException("Failed to acquire database lock for wallet: " + walletId, e);
        }
    }
    
    /**
     * Gets available balance with database lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    private BigDecimal getAvailableBalanceWithLock(UUID walletId) {
        String sql = "SELECT available_balance FROM wallets WHERE id = ? FOR UPDATE";
        
        try {
            return jdbcTemplate.queryForObject(sql, BigDecimal.class, walletId);
        } catch (Exception e) {
            log.error("Failed to get available balance for wallet: {}", walletId, e);
            throw new ConcurrencyException("Failed to get available balance", e);
        }
    }
    
    /**
     * Updates available balance.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    private void updateAvailableBalance(UUID walletId, BigDecimal newBalance) {
        String sql = "UPDATE wallets SET available_balance = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = ?";
        
        int updated = jdbcTemplate.update(sql, newBalance, walletId);
        if (updated != 1) {
            throw new ConcurrencyException("Failed to update balance for wallet: " + walletId);
        }
    }
    
    /**
     * Checks if a payment has already been processed.
     */
    private boolean isPaymentAlreadyProcessed(UUID paymentId) {
        String sql = "SELECT COUNT(*) FROM payments WHERE id = ? AND status IN ('COMPLETED', 'PROCESSING')";
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, paymentId);
        return count != null && count > 0;
    }
    
    /**
     * Checks if a fund reservation already exists.
     */
    private boolean reservationExists(UUID walletId, UUID transactionId) {
        String sql = "SELECT COUNT(*) FROM fund_reservations WHERE wallet_id = ? AND transaction_id = ? AND status = 'ACTIVE'";
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, walletId, transactionId);
        return count != null && count > 0;
    }
    
    /**
     * Creates a fund reservation record.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    private void createFundReservation(UUID walletId, UUID transactionId, BigDecimal amount, Duration duration) {
        String sql = """
            INSERT INTO fund_reservations 
            (id, wallet_id, transaction_id, amount, currency, status, created_at, expires_at) 
            VALUES (?, ?, ?, ?, 'USD', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '? seconds')
            """;
        
        jdbcTemplate.update(sql, UUID.randomUUID(), walletId, transactionId, amount, duration.getSeconds());
    }
    
    /**
     * Orders wallet IDs consistently to prevent deadlocks.
     */
    private List<UUID> orderWalletIds(UUID wallet1, UUID wallet2) {
        if (wallet1.compareTo(wallet2) < 0) {
            return Arrays.asList(wallet1, wallet2);
        } else {
            return Arrays.asList(wallet2, wallet1);
        }
    }
    
    /**
     * Orders account IDs consistently to prevent deadlocks.
     */
    private List<String> orderAccountIds(String account1, String account2) {
        if (account1.compareTo(account2) < 0) {
            return Arrays.asList(account1, account2);
        } else {
            return Arrays.asList(account2, account1);
        }
    }
    
    /**
     * Detects potential deadlocks in the current locking state.
     */
    public boolean detectDeadlock(List<String> heldLocks, List<String> waitingFor) {
        // Implementation would check for circular dependencies
        // This is a simplified version
        return heldLocks.stream().anyMatch(waitingFor::contains);
    }
    
    /**
     * Async execution with lock and timeout.
     */
    public <T> CompletableFuture<T> executeAsyncWithLock(String lockKey, Supplier<T> operation, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try (DistributedLock lock = acquireMandatoryLock(lockKey, timeout, DEFAULT_LEASE_TIME)) {
                return operation.get();
            }
        }).orTimeout(timeout.toSeconds(), TimeUnit.SECONDS);
    }
}