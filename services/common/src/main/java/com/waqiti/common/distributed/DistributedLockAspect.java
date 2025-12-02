package com.waqiti.common.distributed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AspectJ implementation for @DistributedLocked annotation.
 * Provides declarative distributed locking with SpEL expression support.
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2025-09-16
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {
    
    private final DistributedLockService lockService;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final Pattern parameterPattern = Pattern.compile("\\{(\\d+)}");
    
    @Around("@annotation(distributedLocked)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLocked distributedLocked) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        
        // Generate lock key
        String lockKey = generateLockKey(distributedLocked.key(), method, args);
        
        // Determine lock acquisition strategy
        switch (distributedLocked.scope()) {
            case SINGLE:
                return executeSingleLock(joinPoint, distributedLocked, lockKey);
            case MULTIPLE:
                return executeMultipleLocks(joinPoint, distributedLocked, method, args, false);
            case ORDERED:
                return executeMultipleLocks(joinPoint, distributedLocked, method, args, true);
            default:
                throw new IllegalArgumentException("Unsupported lock scope: " + distributedLocked.scope());
        }
    }
    
    private Object executeSingleLock(ProceedingJoinPoint joinPoint, DistributedLocked distributedLocked, String lockKey) throws Throwable {
        Optional<DistributedLock> lockOpt = lockService.acquireLock(
            lockKey,
            distributedLocked.waitTime(),
            distributedLocked.leaseTime()
        );
        
        if (lockOpt.isEmpty()) {
            if (distributedLocked.failOnTimeout()) {
                throw createTimeoutException(distributedLocked.timeoutException(), lockKey, distributedLocked.waitTime());
            } else {
                log.warn("Proceeding without lock for key: {}", lockKey);
            }
        }
        
        try (DistributedLock lock = lockOpt.orElse(null)) {
            log.debug("Executing method with lock: {}", lockKey);
            return joinPoint.proceed();
        } finally {
            if (lockOpt.isPresent()) {
                lockService.releaseLock(lockOpt.get());
            }
        }
    }
    
    private Object executeMultipleLocks(ProceedingJoinPoint joinPoint, DistributedLocked distributedLocked, 
                                      Method method, Object[] args, boolean ordered) throws Throwable {
        
        List<String> lockKeys = generateMultipleLockKeys(distributedLocked.key(), method, args);
        
        if (lockKeys.isEmpty()) {
            log.warn("No lock keys generated, proceeding without locks");
            return joinPoint.proceed();
        }
        
        // Sort keys if ordered locking is requested
        if (ordered) {
            lockKeys = new ArrayList<>(lockKeys);
            Collections.sort(lockKeys);
        }
        
        Optional<Map<String, DistributedLock>> locksOpt = lockService.acquireMultipleLocks(
            lockKeys,
            distributedLocked.waitTime(),
            distributedLocked.leaseTime()
        );
        
        if (locksOpt.isEmpty()) {
            if (distributedLocked.failOnTimeout()) {
                throw createTimeoutException(distributedLocked.timeoutException(), 
                    String.join(",", lockKeys), distributedLocked.waitTime());
            } else {
                log.warn("Proceeding without locks for keys: {}", lockKeys);
            }
        }
        
        try {
            log.debug("Executing method with {} locks: {}", lockKeys.size(), lockKeys);
            return joinPoint.proceed();
        } finally {
            if (locksOpt.isPresent()) {
                Map<String, DistributedLock> locks = locksOpt.get();
                locks.values().forEach(lockService::releaseLock);
            }
        }
    }
    
    private String generateLockKey(String keyPattern, Method method, Object[] args) {
        try {
            // Handle parameter placeholders like {0}, {1}
            if (keyPattern.contains("{")) {
                return replaceParameterPlaceholders(keyPattern, args);
            }
            
            // Handle SpEL expressions
            if (keyPattern.contains("#{")) {
                return evaluateSpelExpression(keyPattern, method, args);
            }
            
            // Static key
            return keyPattern;
            
        } catch (Exception e) {
            log.error("Failed to generate lock key from pattern: {}", keyPattern, e);
            // Fallback to method-based key
            return method.getDeclaringClass().getSimpleName() + ":" + method.getName();
        }
    }
    
    private String replaceParameterPlaceholders(String keyPattern, Object[] args) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = parameterPattern.matcher(keyPattern);
        
        while (matcher.find()) {
            int paramIndex = Integer.parseInt(matcher.group(1));
            String replacement = "";
            
            if (paramIndex < args.length && args[paramIndex] != null) {
                replacement = args[paramIndex].toString();
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    private String evaluateSpelExpression(String keyPattern, Method method, Object[] args) {
        EvaluationContext context = new StandardEvaluationContext();
        
        // Add method parameters to context
        String[] paramNames = getParameterNames(method);
        for (int i = 0; i < args.length && i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        
        // Add method info to context
        context.setVariable("method", method);
        context.setVariable("class", method.getDeclaringClass());
        
        Expression expression = expressionParser.parseExpression(keyPattern);
        Object result = expression.getValue(context);
        
        return result != null ? result.toString() : "";
    }
    
    private List<String> generateMultipleLockKeys(String keyPattern, Method method, Object[] args) {
        List<String> keys = new ArrayList<>();
        
        // For multiple locks, assume we want to lock on each parameter
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null) {
                String paramKey = keyPattern.replace("{*}", "{" + i + "}");
                keys.add(generateLockKey(paramKey, method, args));
            }
        }
        
        // If no parameter placeholders, create key per non-null argument
        if (keys.isEmpty() && !keyPattern.contains("{") && !keyPattern.contains("#{")) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null) {
                    keys.add(keyPattern + ":" + args[i].toString());
                }
            }
        }
        
        return keys;
    }
    
    private String[] getParameterNames(Method method) {
        // In a real implementation, this would use parameter names from compilation
        // For now, use generic names
        String[] names = new String[method.getParameterCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = "param" + i;
        }
        return names;
    }
    
    private RuntimeException createTimeoutException(Class<? extends RuntimeException> exceptionClass, 
                                                  String lockKey, int waitTime) {
        try {
            // Try constructor with (String, int) parameters
            try {
                return exceptionClass.getConstructor(String.class, int.class)
                    .newInstance(lockKey, waitTime);
            } catch (NoSuchMethodException e) {
                // Fallback to constructor with String parameter
                String message = String.format("Failed to acquire lock '%s' within %d seconds", lockKey, waitTime);
                return exceptionClass.getConstructor(String.class).newInstance(message);
            }
        } catch (Exception e) {
            log.error("Failed to create timeout exception of type: {}", exceptionClass.getName(), e);
            return new LockTimeoutException(lockKey, waitTime);
        }
    }
}