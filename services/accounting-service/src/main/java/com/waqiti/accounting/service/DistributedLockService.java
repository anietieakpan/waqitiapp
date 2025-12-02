package com.waqiti.accounting.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed locking service using Redisson
 * Prevents race conditions in accounting operations across multiple instances
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    private static final String LOCK_PREFIX = "accounting:lock:";
    private static final long DEFAULT_WAIT_TIME = 30L;
    private static final long DEFAULT_LEASE_TIME = 60L;

    /**
     * Execute operation with distributed lock
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> operation) {
        return executeWithLock(lockKey, operation, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);
    }

    /**
     * Execute operation with distributed lock and custom timeouts
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> operation,
                                long waitTimeSeconds, long leaseTimeSeconds) {
        String fullKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);

        boolean lockAcquired = false;
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.debug("Attempting to acquire distributed lock: {}", fullKey);

            lockAcquired = lock.tryLock(waitTimeSeconds, leaseTimeSeconds, TimeUnit.SECONDS);

            if (!lockAcquired) {
                meterRegistry.counter("accounting.lock.timeout",
                    "key", lockKey).increment();

                log.warn("Failed to acquire lock: {} within {} seconds", fullKey, waitTimeSeconds);

                throw new LockTimeoutException(
                    String.format("Could not acquire lock for key: %s within %d seconds",
                        lockKey, waitTimeSeconds));
            }

            meterRegistry.counter("accounting.lock.acquired",
                "key", lockKey).increment();

            log.debug("Lock acquired successfully: {}", fullKey);

            return operation.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            meterRegistry.counter("accounting.lock.interrupted",
                "key", lockKey).increment();

            throw new LockException("Lock acquisition interrupted for key: " + lockKey, e);

        } catch (Exception e) {
            meterRegistry.counter("accounting.lock.error",
                "key", lockKey, "error", e.getClass().getSimpleName()).increment();

            log.error("Error during locked operation: {}", fullKey, e);
            throw e;

        } finally {
            if (lockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                sample.stop(meterRegistry.timer("accounting.lock.duration",
                    "key", lockKey));

                log.debug("Lock released: {}", fullKey);
            }
        }
    }

    /**
     * Execute void operation with distributed lock
     */
    public void executeWithLockVoid(String lockKey, Runnable operation) {
        executeWithLock(lockKey, () -> {
            operation.run();
            return null;
        });
    }

    /**
     * Lock timeout exception
     */
    public static class LockTimeoutException extends RuntimeException {
        public LockTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * Lock exception
     */
    public static class LockException extends RuntimeException {
        public LockException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
