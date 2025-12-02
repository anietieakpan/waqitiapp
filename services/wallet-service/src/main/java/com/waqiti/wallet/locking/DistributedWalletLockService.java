package com.waqiti.wallet.locking;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Enterprise-grade Distributed Locking Service for Wallet Operations
 *
 * CRITICAL PRODUCTION SYSTEM:
 * Prevents double-spending and race conditions in concurrent wallet operations
 * using Redis-based distributed locks with Redisson.
 *
 * FEATURES:
 * - Distributed lock acquisition with configurable timeouts
 * - Automatic lock release on exception
 * - Comprehensive metrics and monitoring
 * - Lock contention detection
 * - Deadlock prevention
 *
 * PROTECTION AGAINST:
 * - Double-spending: Ensures only one transaction modifies wallet at a time
 * - Race conditions: Serializes concurrent access to same wallet
 * - Lost updates: Prevents optimistic locking failures
 *
 * COMPLIANCE:
 * - PCI DSS: Financial transaction integrity
 * - SOX: Audit trail for all financial operations
 * - ISO 27001: Access control and concurrency management
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @since 2025-01-16
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedWalletLockService {

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    // Lock configuration constants
    private static final String WALLET_LOCK_PREFIX = "wallet:lock:";
    private static final String FUND_RESERVATION_LOCK_PREFIX = "wallet:reservation:";
    private static final long DEFAULT_WAIT_TIME_SECONDS = 30L;
    private static final long DEFAULT_LEASE_TIME_SECONDS = 60L;
    private static final long FAST_OPERATION_LEASE_TIME_SECONDS = 10L;

    // Metrics
    private final Counter lockAcquisitionSuccessCounter;
    private final Counter lockAcquisitionFailureCounter;
    private final Counter lockTimeoutCounter;
    private final Counter lockContentionCounter;
    private final Timer lockWaitTimer;

    public DistributedWalletLockService(RedissonClient redissonClient, MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.lockAcquisitionSuccessCounter = Counter.builder("wallet.lock.acquisition.success")
            .description("Number of successful wallet lock acquisitions")
            .tag("service", "wallet")
            .register(meterRegistry);

        this.lockAcquisitionFailureCounter = Counter.builder("wallet.lock.acquisition.failure")
            .description("Number of failed wallet lock acquisitions")
            .tag("service", "wallet")
            .register(meterRegistry);

        this.lockTimeoutCounter = Counter.builder("wallet.lock.timeout")
            .description("Number of wallet lock acquisition timeouts")
            .tag("service", "wallet")
            .register(meterRegistry);

        this.lockContentionCounter = Counter.builder("wallet.lock.contention")
            .description("Number of wallet lock contentions detected")
            .tag("service", "wallet")
            .register(meterRegistry);

        this.lockWaitTimer = Timer.builder("wallet.lock.wait.time")
            .description("Time spent waiting to acquire wallet lock")
            .tag("service", "wallet")
            .register(meterRegistry);
    }

    /**
     * Execute an operation with distributed lock on wallet
     *
     * This method ensures that only one thread/process can modify a wallet at a time,
     * preventing double-spending and race conditions.
     *
     * ALGORITHM:
     * 1. Attempt to acquire distributed lock (Redis-based)
     * 2. If acquired, execute operation within lock scope
     * 3. Automatically release lock on completion or exception
     * 4. Record metrics for monitoring
     *
     * @param walletId UUID of the wallet to lock
     * @param operation The operation to execute while holding the lock
     * @param <T> Return type of the operation
     * @return Result of the operation
     * @throws WalletLockException if lock cannot be acquired
     * @throws WalletLockTimeoutException if lock acquisition times out
     */
    public <T> T executeWithLock(UUID walletId, Supplier<T> operation) {
        return executeWithLock(walletId, operation,
            DEFAULT_WAIT_TIME_SECONDS, DEFAULT_LEASE_TIME_SECONDS);
    }

    /**
     * Execute an operation with distributed lock on wallet (custom timeouts)
     *
     * Use this for operations that need different timeout characteristics.
     * For example, quick balance checks can use shorter timeouts.
     *
     * @param walletId UUID of the wallet to lock
     * @param operation The operation to execute while holding the lock
     * @param waitTimeSeconds Maximum time to wait for lock acquisition
     * @param leaseTimeSeconds Maximum time to hold the lock (auto-release)
     * @param <T> Return type of the operation
     * @return Result of the operation
     * @throws WalletLockException if lock cannot be acquired
     */
    public <T> T executeWithLock(UUID walletId, Supplier<T> operation,
                                  long waitTimeSeconds, long leaseTimeSeconds) {
        String lockKey = WALLET_LOCK_PREFIX + walletId;
        RLock lock = redissonClient.getLock(lockKey);

        Timer.Sample sample = Timer.start(meterRegistry);
        boolean lockAcquired = false;

        try {
            // Attempt to acquire lock with timeout
            log.debug("Attempting to acquire lock for wallet: {} (waitTime={}s, leaseTime={}s)",
                walletId, waitTimeSeconds, leaseTimeSeconds);

            lockAcquired = lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS);

            if (!lockAcquired) {
                lockTimeoutCounter.increment();
                lockContentionCounter.increment();

                log.warn("Lock acquisition timeout for wallet: {} after {}s - possible contention",
                    walletId, waitTimeSeconds);

                throw new WalletLockTimeoutException(
                    String.format("Could not acquire lock for wallet %s within %d seconds. " +
                        "Wallet may be under heavy concurrent access.", walletId, waitTimeSeconds)
                );
            }

            // Record successful acquisition
            lockAcquisitionSuccessCounter.increment();
            sample.stop(lockWaitTimer);

            log.debug("Successfully acquired lock for wallet: {}", walletId);

            // Execute operation within lock
            T result = operation.get();

            log.debug("Operation completed successfully for wallet: {}", walletId);
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lockAcquisitionFailureCounter.increment();

            log.error("Lock acquisition interrupted for wallet: {}", walletId, e);
            throw new WalletLockException("Lock acquisition interrupted", e);

        } catch (Exception e) {
            lockAcquisitionFailureCounter.increment();

            log.error("Error executing operation with lock for wallet: {}", walletId, e);
            throw new WalletLockException("Error during locked operation", e);

        } finally {
            // Always release lock if we acquired it
            if (lockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released lock for wallet: {}", walletId);
            }
        }
    }

    /**
     * Execute a fast operation with distributed lock (shorter lease time)
     *
     * Use this for quick operations like balance checks that don't need
     * long lock hold times. This reduces lock contention.
     *
     * @param walletId UUID of the wallet to lock
     * @param operation The operation to execute
     * @param <T> Return type of the operation
     * @return Result of the operation
     */
    public <T> T executeWithFastLock(UUID walletId, Supplier<T> operation) {
        return executeWithLock(walletId, operation,
            DEFAULT_WAIT_TIME_SECONDS, FAST_OPERATION_LEASE_TIME_SECONDS);
    }

    /**
     * Execute operation with fund reservation lock
     *
     * Fund reservations need separate locks to avoid deadlocks with wallet locks.
     * Always acquire reservation lock BEFORE wallet lock to prevent deadlocks.
     *
     * @param reservationId UUID of the reservation
     * @param operation The operation to execute
     * @param <T> Return type of the operation
     * @return Result of the operation
     */
    public <T> T executeWithReservationLock(UUID reservationId, Supplier<T> operation) {
        String lockKey = FUND_RESERVATION_LOCK_PREFIX + reservationId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean lockAcquired = false;

        try {
            lockAcquired = lock.tryLock(DEFAULT_WAIT_TIME_SECONDS,
                DEFAULT_LEASE_TIME_SECONDS, TimeUnit.SECONDS);

            if (!lockAcquired) {
                throw new WalletLockTimeoutException(
                    "Could not acquire reservation lock: " + reservationId
                );
            }

            return operation.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WalletLockException("Reservation lock acquisition interrupted", e);

        } finally {
            if (lockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Check if wallet is currently locked
     *
     * Useful for monitoring and diagnostics. Does NOT attempt to acquire lock.
     *
     * @param walletId UUID of the wallet
     * @return true if wallet is currently locked
     */
    public boolean isLocked(UUID walletId) {
        String lockKey = WALLET_LOCK_PREFIX + walletId;
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isLocked();
    }

    /**
     * Get current lock holder information
     *
     * For debugging and diagnostics. Returns null if not locked.
     *
     * @param walletId UUID of the wallet
     * @return Lock holder thread ID or null
     */
    public String getLockHolder(UUID walletId) {
        String lockKey = WALLET_LOCK_PREFIX + walletId;
        RLock lock = redissonClient.getLock(lockKey);

        if (lock.isLocked()) {
            return "Thread-" + lock.getHoldCount();
        }
        return null;
    }

    /**
     * Force unlock a wallet (ADMIN OPERATION ONLY)
     *
     * WARNING: This should only be used by operations team to recover from
     * stuck locks. Improper use can cause race conditions.
     *
     * @param walletId UUID of the wallet
     * @return true if lock was released
     */
    public boolean forceUnlock(UUID walletId) {
        String lockKey = WALLET_LOCK_PREFIX + walletId;
        RLock lock = redissonClient.getLock(lockKey);

        if (lock.isLocked()) {
            lock.forceUnlock();
            log.warn("ADMIN ACTION: Forcefully unlocked wallet: {}", walletId);
            return true;
        }

        return false;
    }

    /**
     * Get lock metrics for monitoring dashboard
     *
     * @return Lock metrics
     */
    public LockMetrics getMetrics() {
        return LockMetrics.builder()
            .successfulAcquisitions(lockAcquisitionSuccessCounter.count())
            .failedAcquisitions(lockAcquisitionFailureCounter.count())
            .timeouts(lockTimeoutCounter.count())
            .contentions(lockContentionCounter.count())
            .averageWaitTime(lockWaitTimer.mean(TimeUnit.MILLISECONDS))
            .maxWaitTime(lockWaitTimer.max(TimeUnit.MILLISECONDS))
            .totalWaitTime(lockWaitTimer.totalTime(TimeUnit.MILLISECONDS))
            .build();
    }
}
