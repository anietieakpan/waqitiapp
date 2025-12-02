package com.waqiti.common.distributed;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component
public class FinancialOperationLockManager {
    
    private final RedissonClient redissonClient;
    private static final long DEFAULT_WAIT_TIME = 5;
    private static final long DEFAULT_LEASE_TIME = 10;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;
    
    public FinancialOperationLockManager(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }
    
    public <T> T executeWithLock(String lockKey, Supplier<T> operation) throws InterruptedException {
        return executeWithLock(lockKey, operation, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, DEFAULT_TIME_UNIT);
    }
    
    public <T> T executeWithLock(String lockKey, Supplier<T> operation, 
                                  long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        RLock lock = redissonClient.getFairLock(lockKey);
        
        try {
            if (lock.tryLock(waitTime, leaseTime, timeUnit)) {
                log.debug("Acquired lock for key: {}", lockKey);
                return operation.get();
            } else {
                throw new RuntimeException("Unable to acquire lock for key: " + lockKey);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released lock for key: {}", lockKey);
            }
        }
    }
    
    public void executeWithLock(String lockKey, Runnable operation) throws InterruptedException {
        executeWithLock(lockKey, () -> {
            operation.run();
            return null;
        });
    }
    
    public void executeWithLock(String lockKey, Runnable operation, 
                                long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        executeWithLock(lockKey, () -> {
            operation.run();
            return null;
        }, waitTime, leaseTime, timeUnit);
    }
    
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock lock = redissonClient.getFairLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while trying to acquire lock for key: {}", lockKey, e);
            return false;
        }
    }
    
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getFairLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Manually released lock for key: {}", lockKey);
        }
    }
    
    public boolean isLocked(String lockKey) {
        RLock lock = redissonClient.getFairLock(lockKey);
        return lock.isLocked();
    }
    
    public boolean isHeldByCurrentThread(String lockKey) {
        RLock lock = redissonClient.getFairLock(lockKey);
        return lock.isHeldByCurrentThread();
    }
}