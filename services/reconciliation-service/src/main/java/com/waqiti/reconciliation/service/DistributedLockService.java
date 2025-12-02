package com.waqiti.reconciliation.service;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * DistributedLockService - Interface for distributed locking
 *
 * Provides distributed locking capabilities for critical reconciliation operations
 * to ensure consistency across multiple service instances.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
public interface DistributedLockService {

    /**
     * Execute a task with a distributed lock
     *
     * @param lockKey Unique key for the lock
     * @param task Task to execute while holding the lock
     * @param <T> Return type of the task
     * @return Result of the task execution
     * @throws Exception if lock cannot be acquired or task fails
     */
    <T> T executeWithLock(String lockKey, Callable<T> task) throws Exception;

    /**
     * Execute a task with a distributed lock with timeout
     *
     * @param lockKey Unique key for the lock
     * @param task Task to execute while holding the lock
     * @param waitTime Maximum time to wait for the lock
     * @param leaseTime Maximum time to hold the lock
     * @param timeUnit Time unit for waitTime and leaseTime
     * @param <T> Return type of the task
     * @return Result of the task execution
     * @throws Exception if lock cannot be acquired or task fails
     */
    <T> T executeWithLock(String lockKey, Callable<T> task, long waitTime, long leaseTime, TimeUnit timeUnit) throws Exception;

    /**
     * Try to acquire a lock without blocking
     *
     * @param lockKey Unique key for the lock
     * @return true if lock was acquired, false otherwise
     */
    boolean tryLock(String lockKey);

    /**
     * Try to acquire a lock with timeout
     *
     * @param lockKey Unique key for the lock
     * @param waitTime Maximum time to wait
     * @param timeUnit Time unit
     * @return true if lock was acquired, false otherwise
     */
    boolean tryLock(String lockKey, long waitTime, TimeUnit timeUnit);

    /**
     * Release a lock
     *
     * @param lockKey Unique key for the lock
     */
    void unlock(String lockKey);

    /**
     * Check if a lock is currently held
     *
     * @param lockKey Unique key for the lock
     * @return true if lock is held, false otherwise
     */
    boolean isLocked(String lockKey);
}
