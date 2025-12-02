package com.waqiti.common.locking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Aspect to handle distributed locking using annotations
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {
    
    private final DistributedLockService distributedLockService;
    
    @Around("@annotation(distributedLocked)")
    public Object handleDistributedLock(ProceedingJoinPoint joinPoint, DistributedLocked distributedLocked) throws Throwable {
        String lockKey = distributedLocked.key();
        Duration waitTime = Duration.ofSeconds(distributedLocked.waitTimeSeconds());
        Duration leaseTime = Duration.ofSeconds(distributedLocked.leaseTimeSeconds());
        
        // Replace method parameters in lock key if needed
        String resolvedLockKey = resolveLockKey(lockKey, joinPoint);
        
        log.debug("Attempting to acquire distributed lock: {}", resolvedLockKey);
        
        try (DistributedLock lock = distributedLockService.acquireLock(resolvedLockKey, waitTime, leaseTime)) {
            if (lock == null) {
                String message = "Failed to acquire distributed lock: " + resolvedLockKey;
                if (distributedLocked.throwExceptionOnFailure()) {
                    throw new DistributedLockException(message);
                } else {
                    log.warn(message);
                    return null;
                }
            }
            
            log.debug("Successfully acquired distributed lock: {}", resolvedLockKey);
            return joinPoint.proceed();
        }
    }
    
    /**
     * Resolves placeholders in the lock key using method parameters
     */
    private String resolveLockKey(String lockKey, ProceedingJoinPoint joinPoint) {
        String resolvedKey = lockKey;
        
        // Simple parameter substitution - could be enhanced with SpEL
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            if (resolvedKey.contains(placeholder) && args[i] != null) {
                resolvedKey = resolvedKey.replace(placeholder, args[i].toString());
            }
        }
        
        return resolvedKey;
    }
}