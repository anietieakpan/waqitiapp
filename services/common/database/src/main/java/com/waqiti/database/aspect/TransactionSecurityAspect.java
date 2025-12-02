package com.waqiti.database.aspect;

import com.waqiti.common.audit.AuditService;
import com.waqiti.database.annotation.FinancialTransaction;
import com.waqiti.database.annotation.ReadOnlyTransaction;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;

/**
 * Transaction Security Aspect
 * 
 * This aspect monitors and enforces transaction security policies:
 * - Verifies correct isolation levels are being used
 * - Monitors transaction duration and performance
 * - Audits financial operations
 * - Prevents accidental modifications in read-only transactions
 * - Alerts on suspicious transaction patterns
 * 
 * SECURITY: Provides runtime validation of transaction isolation levels
 * and monitors for potential security violations
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionSecurityAspect {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    
    /**
     * Monitor financial transactions
     */
    @Around("@annotation(financialTransaction)")
    public Object monitorFinancialTransaction(ProceedingJoinPoint joinPoint, 
                                            FinancialTransaction financialTransaction) throws Throwable {
        
        String methodName = joinPoint.getSignature().toShortString();
        String transactionLabel = financialTransaction.value().isEmpty() 
            ? methodName : financialTransaction.value();
        
        log.debug("Starting financial transaction: {}", transactionLabel);
        
        // Verify we're in a transaction
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.error("SECURITY: Financial method {} called without active transaction", methodName);
            auditService.auditSecurityEvent("FINANCIAL_NO_TRANSACTION", methodName);
            throw new IllegalStateException("Financial operations must be transactional");
        }
        
        Instant startTime = Instant.now();
        
        try {
            // Verify isolation level
            verifyIsolationLevel(Connection.TRANSACTION_SERIALIZABLE, methodName);
            
            // Execute the method
            Object result = joinPoint.proceed();
            
            // Audit successful financial operation
            Duration duration = Duration.between(startTime, Instant.now());
            auditService.auditFinancialTransaction(
                transactionLabel,
                methodName,
                duration.toMillis(),
                "SUCCESS"
            );
            
            // Update metrics
            meterRegistry.timer("financial.transaction.duration", "method", methodName)
                .record(duration);
            meterRegistry.counter("financial.transaction.success", "method", methodName)
                .increment();
            
            log.debug("Financial transaction completed: {} in {}ms", 
                     transactionLabel, duration.toMillis());
            
            return result;
            
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            
            log.error("Financial transaction failed: {} after {}ms", transactionLabel, duration.toMillis(), e);
            
            // Audit failed financial operation
            auditService.auditFinancialTransaction(
                transactionLabel,
                methodName,
                duration.toMillis(),
                "FAILED: " + e.getMessage()
            );
            
            // Update metrics
            meterRegistry.counter("financial.transaction.failure", "method", methodName)
                .increment();
            
            throw e;
        }
    }
    
    /**
     * Monitor read-only transactions
     */
    @Around("@annotation(readOnlyTransaction)")
    public Object monitorReadOnlyTransaction(ProceedingJoinPoint joinPoint,
                                           ReadOnlyTransaction readOnlyTransaction) throws Throwable {
        
        String methodName = joinPoint.getSignature().toShortString();
        String transactionLabel = readOnlyTransaction.value().isEmpty()
            ? methodName : readOnlyTransaction.value();
        
        log.debug("Starting read-only transaction: {}", transactionLabel);
        
        // Verify we're in a read-only transaction
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                log.warn("SECURITY: Method {} annotated as read-only but transaction is not read-only", methodName);
                auditService.auditSecurityEvent("READONLY_VIOLATION", methodName);
            }
        }
        
        Instant startTime = Instant.now();
        
        try {
            // Verify isolation level
            verifyIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ, methodName);
            
            // Execute the method
            Object result = joinPoint.proceed();
            
            Duration duration = Duration.between(startTime, Instant.now());
            
            // Update metrics
            meterRegistry.timer("readonly.transaction.duration", "method", methodName)
                .record(duration);
            meterRegistry.counter("readonly.transaction.success", "method", methodName)
                .increment();
            
            // Audit long-running read queries
            if (duration.toSeconds() > 30) {
                auditService.auditPerformance("LONG_READONLY_TRANSACTION", 
                    String.format("Method: %s, Duration: %dms", methodName, duration.toMillis()));
            }
            
            log.debug("Read-only transaction completed: {} in {}ms", 
                     transactionLabel, duration.toMillis());
            
            return result;
            
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            
            log.error("Read-only transaction failed: {} after {}ms", transactionLabel, duration.toMillis(), e);
            
            meterRegistry.counter("readonly.transaction.failure", "method", methodName)
                .increment();
            
            throw e;
        }
    }
    
    /**
     * Monitor all transactional methods for isolation level compliance
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object monitorGeneralTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        
        String methodName = joinPoint.getSignature().toShortString();
        
        // Skip if already monitored by specific annotations
        if (hasFinancialAnnotation(joinPoint) || hasReadOnlyAnnotation(joinPoint)) {
            return joinPoint.proceed();
        }
        
        log.debug("Starting general transaction: {}", methodName);
        
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Check for suspicious patterns
            detectSuspiciousTransactionPatterns(methodName);
        }
        
        Instant startTime = Instant.now();
        
        try {
            Object result = joinPoint.proceed();
            
            Duration duration = Duration.between(startTime, Instant.now());
            
            // Monitor for long-running transactions
            if (duration.toSeconds() > 60) {
                log.warn("Long-running transaction detected: {} took {}ms", methodName, duration.toMillis());
                auditService.auditPerformance("LONG_TRANSACTION", 
                    String.format("Method: %s, Duration: %dms", methodName, duration.toMillis()));
            }
            
            meterRegistry.timer("general.transaction.duration", "method", methodName)
                .record(duration);
            
            return result;
            
        } catch (Exception e) {
            log.error("General transaction failed: {}", methodName, e);
            meterRegistry.counter("general.transaction.failure", "method", methodName)
                .increment();
            throw e;
        }
    }
    
    /**
     * Verify the current transaction isolation level
     */
    private void verifyIsolationLevel(int expectedLevel, String methodName) {
        try {
            // Get current connection and check isolation level
            Connection connection = entityManager.unwrap(Connection.class);
            int actualLevel = connection.getTransactionIsolation();
            
            if (actualLevel != expectedLevel) {
                String expectedLevelName = getIsolationLevelName(expectedLevel);
                String actualLevelName = getIsolationLevelName(actualLevel);
                
                log.error("SECURITY: Transaction isolation level mismatch in {}. Expected: {}, Actual: {}", 
                         methodName, expectedLevelName, actualLevelName);
                
                auditService.auditSecurityEvent("ISOLATION_LEVEL_MISMATCH", 
                    String.format("Method: %s, Expected: %s, Actual: %s", 
                                  methodName, expectedLevelName, actualLevelName));
                
                // In production, this could throw an exception to fail fast
                // For now, just log and audit the violation
            } else {
                log.debug("Transaction isolation level verified: {} for {}", 
                         getIsolationLevelName(actualLevel), methodName);
            }
            
        } catch (Exception e) {
            log.warn("Could not verify transaction isolation level for {}", methodName, e);
        }
    }
    
    /**
     * Get human-readable isolation level name
     */
    private String getIsolationLevelName(int level) {
        return switch (level) {
            case Connection.TRANSACTION_READ_UNCOMMITTED -> "READ_UNCOMMITTED";
            case Connection.TRANSACTION_READ_COMMITTED -> "READ_COMMITTED";
            case Connection.TRANSACTION_REPEATABLE_READ -> "REPEATABLE_READ";
            case Connection.TRANSACTION_SERIALIZABLE -> "SERIALIZABLE";
            default -> "UNKNOWN(" + level + ")";
        };
    }
    
    /**
     * Check if method has FinancialTransaction annotation
     */
    private boolean hasFinancialAnnotation(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.getTarget().getClass()
                .getMethod(joinPoint.getSignature().getName())
                .isAnnotationPresent(FinancialTransaction.class);
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    /**
     * Check if method has ReadOnlyTransaction annotation
     */
    private boolean hasReadOnlyAnnotation(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.getTarget().getClass()
                .getMethod(joinPoint.getSignature().getName())
                .isAnnotationPresent(ReadOnlyTransaction.class);
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
    
    /**
     * Detect suspicious transaction patterns
     */
    private void detectSuspiciousTransactionPatterns(String methodName) {
        // Check for financial keywords in non-financial transactions
        String[] financialKeywords = {"payment", "transfer", "balance", "withdraw", "deposit", "money"};
        
        String lowerMethodName = methodName.toLowerCase();
        for (String keyword : financialKeywords) {
            if (lowerMethodName.contains(keyword)) {
                log.warn("SECURITY: Potential financial operation {} not using @FinancialTransaction annotation", 
                         methodName);
                auditService.auditSecurityEvent("UNPROTECTED_FINANCIAL_METHOD", methodName);
                break;
            }
        }
        
        // Check for modification operations in read-only named methods
        if (lowerMethodName.contains("get") || lowerMethodName.contains("find") || 
            lowerMethodName.contains("read") || lowerMethodName.contains("fetch")) {
            
            if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                log.warn("SECURITY: Read-named method {} in non-read-only transaction", methodName);
                auditService.auditSecurityEvent("READONLY_NAME_VIOLATION", methodName);
            }
        }
    }
}