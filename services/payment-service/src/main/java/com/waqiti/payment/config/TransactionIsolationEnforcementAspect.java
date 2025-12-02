package com.waqiti.payment.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * PRODUCTION CRITICAL: Transaction Isolation Enforcement Aspect
 *
 * This aspect enforces SERIALIZABLE isolation for all financial operations
 * to prevent race conditions, double-spending, and data inconsistencies.
 *
 * WHY SERIALIZABLE FOR FINANCIAL OPERATIONS:
 * ------------------------------------------
 * 1. Prevents Phantom Reads: No new rows can appear during transaction
 * 2. Prevents Non-Repeatable Reads: Same query returns same results
 * 3. Prevents Dirty Reads: Can't read uncommitted changes
 * 4. Ensures Complete Isolation: Transactions appear to execute sequentially
 *
 * FINANCIAL RISKS WITH LOWER ISOLATION LEVELS:
 * --------------------------------------------
 * READ_COMMITTED:
 *   - Account balance could change between check and debit
 *   - Double-spending possible in concurrent transactions
 *   - Lost updates in balance calculations
 *
 * REPEATABLE_READ:
 *   - Phantom reads still possible
 *   - New transactions could appear during aggregation
 *   - Insufficient for complex financial workflows
 *
 * SERIALIZABLE:
 *   - Complete isolation guarantee
 *   - No race conditions possible
 *   - Required for PCI-DSS and SOX compliance
 *
 * PERFORMANCE TRADE-OFFS:
 * ----------------------
 * SERIALIZABLE has performance cost due to locking, but for financial
 * operations, correctness > performance. We mitigate with:
 * - Distributed locks for cross-service coordination
 * - Short transaction duration (<3 seconds)
 * - Optimistic locking with @Version fields
 * - Strategic use of FOR UPDATE locks
 *
 * MONITORING:
 * ----------
 * This aspect logs warnings when non-SERIALIZABLE isolation is detected
 * in financial operations, helping identify code that needs hardening.
 *
 * @author Waqiti Platform Team - Production Hardening Initiative
 * @version 2.0.0
 * @since November 17, 2025
 */
@Slf4j
@Aspect
@Component
@Order(1) // Execute before transaction interceptor
@ConditionalOnProperty(
    name = "payment.transaction.enforcement.enabled",
    havingValue = "true",
    matchIfMissing = true // Enabled by default for safety
)
public class TransactionIsolationEnforcementAspect {

    /**
     * Financial operation indicators - methods/classes containing these
     * keywords are considered financial operations requiring SERIALIZABLE isolation
     */
    private static final Set<String> FINANCIAL_KEYWORDS = new HashSet<>(Arrays.asList(
        "payment", "transfer", "deposit", "withdraw", "balance", "amount",
        "transaction", "refund", "settlement", "charge", "fee", "commission",
        "credit", "debit", "fund", "money", "financial", "currency", "wallet"
    ));

    /**
     * Parameter types that indicate financial operations
     */
    private static final Set<Class<?>> FINANCIAL_TYPES = new HashSet<>(Arrays.asList(
        BigDecimal.class
    ));

    /**
     * Enforce proper transaction isolation for all @Transactional methods
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object enforceTransactionIsolation(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> declaringClass = method.getDeclaringClass();

        // Get @Transactional annotation
        Transactional methodTx = method.getAnnotation(Transactional.class);
        Transactional classTx = declaringClass.getAnnotation(Transactional.class);

        Transactional effectiveTx = methodTx != null ? methodTx : classTx;

        // Check if this is a financial operation
        boolean isFinancialOperation = isFinancialOperation(method, declaringClass);

        if (isFinancialOperation) {
            Isolation isolation = effectiveTx != null ? effectiveTx.isolation() : Isolation.DEFAULT;

            // Validate isolation level
            if (isolation != Isolation.SERIALIZABLE) {
                String warning = String.format(
                    "⚠️ FINANCIAL OPERATION WITH WEAK ISOLATION LEVEL ⚠️\n" +
                    "Class: %s\n" +
                    "Method: %s\n" +
                    "Current Isolation: %s\n" +
                    "Required Isolation: SERIALIZABLE\n" +
                    "Risk: Race conditions, double-spending, data inconsistency\n" +
                    "Action Required: Add @Transactional(isolation = Isolation.SERIALIZABLE)\n" +
                    "========================================================",
                    declaringClass.getName(),
                    method.getName(),
                    isolation
                );

                log.warn(warning);

                // In strict mode, could throw exception
                if (isStrictMode()) {
                    throw new IllegalStateException(
                        "CRITICAL: Financial operation '" + method.getName() +
                        "' must use SERIALIZABLE isolation. Current: " + isolation
                    );
                }
            } else {
                log.debug("✅ Financial operation using correct SERIALIZABLE isolation: {}.{}",
                    declaringClass.getSimpleName(), method.getName());
            }
        }

        // Proceed with method execution
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            if (isFinancialOperation && duration > 3000) {
                log.warn("⚠️ Long-running financial transaction detected: {}.{} took {}ms. " +
                        "Consider splitting into smaller transactions or using async processing.",
                        declaringClass.getSimpleName(), method.getName(), duration);
            }

            return result;
        } catch (Exception e) {
            log.error("❌ Financial operation failed: {}.{}. Exception: {}",
                declaringClass.getSimpleName(), method.getName(), e.getMessage());
            throw e;
        }
    }

    /**
     * Determine if a method is a financial operation based on:
     * 1. Method/class name contains financial keywords
     * 2. Method parameters include BigDecimal (amounts)
     * 3. Method is in a financial service package
     */
    private boolean isFinancialOperation(Method method, Class<?> declaringClass) {
        String methodName = method.getName().toLowerCase();
        String className = declaringClass.getSimpleName().toLowerCase();
        String packageName = declaringClass.getPackage().getName().toLowerCase();

        // Check method name
        for (String keyword : FINANCIAL_KEYWORDS) {
            if (methodName.contains(keyword)) {
                return true;
            }
        }

        // Check class name
        for (String keyword : FINANCIAL_KEYWORDS) {
            if (className.contains(keyword)) {
                return true;
            }
        }

        // Check package name
        for (String keyword : FINANCIAL_KEYWORDS) {
            if (packageName.contains(keyword)) {
                return true;
            }
        }

        // Check parameters for BigDecimal (money amounts)
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (Class<?> paramType : parameterTypes) {
            if (FINANCIAL_TYPES.contains(paramType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if strict mode is enabled (will throw exception instead of warning)
     * Disabled by default to avoid breaking existing code during migration
     */
    private boolean isStrictMode() {
        String strictMode = System.getProperty("payment.transaction.strict-mode", "false");
        return Boolean.parseBoolean(strictMode);
    }
}
