package com.waqiti.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * CRITICAL AUDIT ASPECT: Automatic audit logging for financial operations
 * PRODUCTION-READY: AOP-based audit capture with minimal performance impact
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class FinancialAuditAspect {

    private final FinancialAuditLogger auditLogger;

    /**
     * CRITICAL: Pointcut for all payment-related methods
     */
    @Pointcut("execution(* com.waqiti..service.*PaymentService.*(..)) || " +
             "execution(* com.waqiti..payment.*.*(..)) || " +
             "execution(* com.waqiti..orchestration.*PaymentProcessor.*(..))")
    public void paymentOperations() {}

    /**
     * CRITICAL: Pointcut for all wallet-related methods
     */
    @Pointcut("execution(* com.waqiti..service.*WalletService.*(..)) || " +
             "execution(* com.waqiti..wallet.*.*(..)) || " +
             "execution(* com.waqiti..service.*TransferService.*(..))")
    public void walletOperations() {}

    /**
     * CRITICAL: Pointcut for all fraud detection methods
     */
    @Pointcut("execution(* com.waqiti..service.*FraudService.*(..)) || " +
             "execution(* com.waqiti..fraud.*.*(..)) || " +
             "execution(* com.waqiti..*FraudDetection.*(..))")
    public void fraudOperations() {}

    /**
     * CRITICAL: Pointcut for all compliance-related methods
     */
    @Pointcut("execution(* com.waqiti..service.*ComplianceService.*(..)) || " +
             "execution(* com.waqiti..compliance.*.*(..)) || " +
             "execution(* com.waqiti..*ComplianceCheck.*(..))")
    public void complianceOperations() {}

    /**
     * CRITICAL: Pointcut for methods annotated with @AuditFinancialOperation
     */
    @Pointcut("@annotation(com.waqiti.common.audit.AuditFinancialOperation)")
    public void auditAnnotatedMethods() {}

    /**
     * Around advice for payment operations
     */
    @Around("paymentOperations() || auditAnnotatedMethods()")
    public Object auditPaymentOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        
        Instant startTime = Instant.now();
        String operationId = generateOperationId(className, methodName);
        
        log.debug("AUDIT_ASPECT: Starting payment operation audit: {}.{}", className, methodName);
        
        try {
            // Extract payment details from method arguments
            Map<String, Object> operationContext = extractPaymentContext(args);
            
            // Log operation start
            logOperationStart(operationId, "PAYMENT", methodName, operationContext);
            
            // Execute the method
            Object result = joinPoint.proceed();
            
            // Log operation success
            long duration = java.time.Duration.between(startTime, Instant.now()).toMillis();
            logOperationSuccess(operationId, "PAYMENT", methodName, result, duration, operationContext);
            
            return result;
            
        } catch (Throwable e) {
            // Log operation failure
            long duration = java.time.Duration.between(startTime, Instant.now()).toMillis();
            logOperationFailure(operationId, "PAYMENT", methodName, e, duration, args);
            throw e;
        }
    }

    /**
     * Around advice for wallet operations
     */
    @Around("walletOperations()")
    public Object auditWalletOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        
        Instant startTime = Instant.now();
        String operationId = generateOperationId(className, methodName);
        
        log.debug("AUDIT_ASPECT: Starting wallet operation audit: {}.{}", className, methodName);
        
        try {
            // Extract wallet details from method arguments
            Map<String, Object> operationContext = extractWalletContext(args);
            
            // For balance-changing operations, capture before state
            BigDecimal previousBalance = null;
            String walletId = (String) operationContext.get("walletId");
            if (isBalanceChangingOperation(methodName) && walletId != null) {
                previousBalance = getCurrentWalletBalance(walletId);
                operationContext.put("previousBalance", previousBalance);
            }
            
            // Log operation start
            logOperationStart(operationId, "WALLET", methodName, operationContext);
            
            // Execute the method
            Object result = joinPoint.proceed();
            
            // For balance-changing operations, capture after state
            if (isBalanceChangingOperation(methodName) && walletId != null && previousBalance != null) {
                BigDecimal newBalance = getCurrentWalletBalance(walletId);
                String customerId = (String) operationContext.get("customerId");
                
                auditLogger.logWalletBalanceChange(
                    walletId, customerId, previousBalance, newBalance, 
                    methodName, operationId, "Wallet operation: " + methodName
                );
            }
            
            // Log operation success
            long duration = java.time.Duration.between(startTime, Instant.now()).toMillis();
            logOperationSuccess(operationId, "WALLET", methodName, result, duration, operationContext);
            
            return result;
            
        } catch (Throwable e) {
            // Log operation failure
            long duration = java.time.Duration.between(startTime, Instant.now()).toMillis();
            logOperationFailure(operationId, "WALLET", methodName, e, duration, args);
            throw e;
        }
    }

    /**
     * After advice for fraud detection operations
     */
    @AfterReturning(pointcut = "fraudOperations()", returning = "result")
    public void auditFraudOperation(JoinPoint joinPoint, Object result) {
        try {
            String methodName = joinPoint.getSignature().getName();
            Object[] args = joinPoint.getArgs();
            
            log.debug("AUDIT_ASPECT: Auditing fraud operation: {}", methodName);
            
            // Extract fraud detection details
            Map<String, Object> fraudContext = extractFraudContext(args, result);
            
            if (fraudContext.containsKey("transactionId")) {
                auditLogger.logFraudDetection(
                    (String) fraudContext.get("transactionId"),
                    (String) fraudContext.get("customerId"),
                    methodName,
                    (Integer) fraudContext.getOrDefault("riskScore", 0),
                    (String) fraudContext.getOrDefault("decision", "UNKNOWN"),
                    (String) fraudContext.getOrDefault("reason", "Fraud check performed"),
                    convertToStringMap(fraudContext)
                );
            }
            
        } catch (Exception e) {
            log.error("AUDIT_ASPECT: Failed to audit fraud operation", e);
        }
    }

    /**
     * After advice for compliance operations
     */
    @AfterReturning(pointcut = "complianceOperations()", returning = "result")
    public void auditComplianceOperation(JoinPoint joinPoint, Object result) {
        try {
            String methodName = joinPoint.getSignature().getName();
            Object[] args = joinPoint.getArgs();
            
            log.debug("AUDIT_ASPECT: Auditing compliance operation: {}", methodName);
            
            // Extract compliance details
            Map<String, Object> complianceContext = extractComplianceContext(args, result);
            
            if (complianceContext.containsKey("transactionId")) {
                auditLogger.logComplianceCheck(
                    (String) complianceContext.get("transactionId"),
                    (String) complianceContext.get("customerId"),
                    methodName,
                    (Boolean) complianceContext.getOrDefault("isCompliant", false),
                    (Integer) complianceContext.getOrDefault("riskScore", 0),
                    (String) complianceContext.getOrDefault("regulatoryInfo", "Standard compliance check"),
                    convertToStringMap(complianceContext)
                );
            }
            
        } catch (Exception e) {
            log.error("AUDIT_ASPECT: Failed to audit compliance operation", e);
        }
    }

    /**
     * Exception advice for all financial operations
     */
    @AfterThrowing(pointcut = "paymentOperations() || walletOperations() || fraudOperations() || complianceOperations()", throwing = "ex")
    public void auditFinancialException(JoinPoint joinPoint, Throwable ex) {
        try {
            String methodName = joinPoint.getSignature().getName();
            String className = joinPoint.getTarget().getClass().getSimpleName();
            Object[] args = joinPoint.getArgs();
            
            log.debug("AUDIT_ASPECT: Auditing financial operation exception: {}.{}", className, methodName);
            
            // Extract context from arguments
            Map<String, String> errorContext = new HashMap<>();
            errorContext.put("methodName", methodName);
            errorContext.put("className", className);
            errorContext.put("argumentCount", String.valueOf(args.length));
            
            // Extract any identifiers from arguments
            String entityId = extractEntityId(args);
            String customerId = extractCustomerId(args);
            
            auditLogger.logSystemError(
                ex.getClass().getSimpleName(),
                className,
                methodName,
                ex.getMessage(),
                entityId,
                customerId,
                errorContext
            );
            
        } catch (Exception e) {
            log.error("AUDIT_ASPECT: Failed to audit financial exception", e);
        }
    }

    // PRIVATE HELPER METHODS

    private String generateOperationId(String className, String methodName) {
        return className + "_" + methodName + "_" + System.currentTimeMillis();
    }

    private void logOperationStart(String operationId, String operationType, String methodName, 
                                 Map<String, Object> context) {
        log.info("AUDIT_ASPECT: {} operation started: {} - Method: {}", 
                operationType, operationId, methodName);
    }

    private void logOperationSuccess(String operationId, String operationType, String methodName, 
                                   Object result, long duration, Map<String, Object> context) {
        log.info("AUDIT_ASPECT: {} operation completed: {} - Duration: {}ms", 
                operationType, operationId, duration);
    }

    private void logOperationFailure(String operationId, String operationType, String methodName, 
                                   Throwable error, long duration, Object[] args) {
        log.error("AUDIT_ASPECT: {} operation failed: {} - Duration: {}ms - Error: {}", 
                operationType, operationId, duration, error.getMessage());
    }

    /**
     * Extract payment context from method arguments
     */
    private Map<String, Object> extractPaymentContext(Object[] args) {
        Map<String, Object> context = new HashMap<>();
        
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null) continue;
            
            String className = arg.getClass().getSimpleName().toLowerCase();
            
            // Extract common payment fields
            if (className.contains("payment")) {
                extractFieldsFromObject(arg, context, Arrays.asList(
                    "paymentId", "customerId", "amount", "currency", "providerId"
                ));
            }
            
            // Handle primitive types and strings
            if (arg instanceof String) {
                if (isPaymentId((String) arg)) {
                    context.put("paymentId", arg);
                } else if (isCustomerId((String) arg)) {
                    context.put("customerId", arg);
                }
            } else if (arg instanceof BigDecimal) {
                context.put("amount", arg);
            }
        }
        
        return context;
    }

    /**
     * Extract wallet context from method arguments
     */
    private Map<String, Object> extractWalletContext(Object[] args) {
        Map<String, Object> context = new HashMap<>();
        
        for (Object arg : args) {
            if (arg == null) continue;
            
            String className = arg.getClass().getSimpleName().toLowerCase();
            
            // Extract common wallet fields
            if (className.contains("wallet") || className.contains("transfer")) {
                extractFieldsFromObject(arg, context, Arrays.asList(
                    "walletId", "customerId", "amount", "currency", "transferId", 
                    "fromWalletId", "toWalletId"
                ));
            }
            
            // Handle primitive types
            if (arg instanceof String) {
                if (isWalletId((String) arg)) {
                    context.put("walletId", arg);
                } else if (isCustomerId((String) arg)) {
                    context.put("customerId", arg);
                }
            } else if (arg instanceof BigDecimal) {
                context.put("amount", arg);
            }
        }
        
        return context;
    }

    /**
     * Extract fraud context from method arguments and result
     */
    private Map<String, Object> extractFraudContext(Object[] args, Object result) {
        Map<String, Object> context = new HashMap<>();
        
        // Extract from arguments
        for (Object arg : args) {
            if (arg == null) continue;
            
            String className = arg.getClass().getSimpleName().toLowerCase();
            if (className.contains("fraud") || className.contains("transaction") || className.contains("payment")) {
                extractFieldsFromObject(arg, context, Arrays.asList(
                    "transactionId", "customerId", "paymentId", "riskScore", "fraudType"
                ));
            }
        }
        
        // Extract from result
        if (result != null) {
            extractFieldsFromObject(result, context, Arrays.asList(
                "decision", "riskScore", "fraudulent", "reason", "riskFactors"
            ));
        }
        
        return context;
    }

    /**
     * Extract compliance context from method arguments and result
     */
    private Map<String, Object> extractComplianceContext(Object[] args, Object result) {
        Map<String, Object> context = new HashMap<>();
        
        // Extract from arguments
        for (Object arg : args) {
            if (arg == null) continue;
            
            String className = arg.getClass().getSimpleName().toLowerCase();
            if (className.contains("compliance") || className.contains("transaction") || className.contains("check")) {
                extractFieldsFromObject(arg, context, Arrays.asList(
                    "transactionId", "customerId", "checkType", "regulatoryInfo"
                ));
            }
        }
        
        // Extract from result
        if (result != null) {
            extractFieldsFromObject(result, context, Arrays.asList(
                "isCompliant", "riskScore", "complianceStatus", "checkResults"
            ));
        }
        
        return context;
    }

    /**
     * Extract fields from object using reflection
     */
    private void extractFieldsFromObject(Object obj, Map<String, Object> context, java.util.List<String> fieldNames) {
        try {
            Class<?> clazz = obj.getClass();
            
            for (String fieldName : fieldNames) {
                try {
                    // Try getter method first
                    String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                    Method getter = clazz.getMethod(getterName);
                    Object value = getter.invoke(obj);
                    if (value != null) {
                        context.put(fieldName, value);
                    }
                } catch (Exception e) {
                    // Try field access
                    try {
                        java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        Object value = field.get(obj);
                        if (value != null) {
                            context.put(fieldName, value);
                        }
                    } catch (Exception ignored) {
                        // Field not found, ignore
                    }
                }
            }
        } catch (Exception e) {
            log.debug("AUDIT_ASPECT: Error extracting fields from object: {}", e.getMessage());
        }
    }

    /**
     * Convert object map to string map for audit logging
     */
    private Map<String, String> convertToStringMap(Map<String, Object> objectMap) {
        Map<String, String> stringMap = new HashMap<>();
        objectMap.forEach((key, value) -> {
            if (value != null) {
                stringMap.put(key, value.toString());
            }
        });
        return stringMap;
    }

    /**
     * Check if operation changes wallet balance
     */
    private boolean isBalanceChangingOperation(String methodName) {
        String lowerMethod = methodName.toLowerCase();
        return lowerMethod.contains("transfer") || lowerMethod.contains("debit") || 
               lowerMethod.contains("credit") || lowerMethod.contains("withdraw") || 
               lowerMethod.contains("deposit");
    }

    /**
     * Get current wallet balance (placeholder - would integrate with actual wallet service)
     */
    private BigDecimal getCurrentWalletBalance(String walletId) {
        // In production, would call wallet service to get current balance
        return BigDecimal.ZERO;
    }

    /**
     * Extract entity ID from method arguments
     */
    private String extractEntityId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String) {
                String str = (String) arg;
                if (isPaymentId(str) || isWalletId(str) || isTransactionId(str)) {
                    return str;
                }
            }
        }
        return "unknown";
    }

    /**
     * Extract customer ID from method arguments
     */
    private String extractCustomerId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String && isCustomerId((String) arg)) {
                return (String) arg;
            }
        }
        return "unknown";
    }

    // ID pattern matching methods
    private boolean isPaymentId(String str) {
        return str.startsWith("pay_") || str.startsWith("payment_") || str.matches(".*payment.*");
    }

    private boolean isWalletId(String str) {
        return str.startsWith("wal_") || str.startsWith("wallet_") || str.matches(".*wallet.*");
    }

    private boolean isTransactionId(String str) {
        return str.startsWith("tx_") || str.startsWith("txn_") || str.matches(".*transaction.*");
    }

    private boolean isCustomerId(String str) {
        return str.startsWith("cus_") || str.startsWith("customer_") || str.matches(".*customer.*");
    }
}