package com.waqiti.common.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;

/**
 * CRITICAL FINANCIAL: Automatic Financial Transaction Tracing Aspect
 * 
 * Automatically injects distributed tracing and correlation IDs into financial operations.
 * Uses AOP to seamlessly integrate tracing without code modification.
 * 
 * Compliance: PCI DSS, SOC 2, Financial Services Audit Requirements
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(1) // Execute before other aspects
@ConditionalOnProperty(name = "waqiti.observability.financial-tracing.enabled", havingValue = "true", matchIfMissing = true)
public class FinancialTransactionTracingAspect {

    private final FinancialTransactionTracing financialTracing;

    /**
     * Pointcut for methods annotated with @FinancialOperation
     */
    @Pointcut("@annotation(com.waqiti.common.observability.FinancialOperation)")
    public void financialOperationMethods() {}

    /**
     * Pointcut for payment service methods
     */
    @Pointcut("execution(* com.waqiti.payment.service.*.*(..)) || " +
              "execution(* com.waqiti.transaction.service.*.*(..)) || " +
              "execution(* com.waqiti.wallet.service.*.*(..))")
    public void financialServiceMethods() {}

    /**
     * Pointcut for methods containing financial keywords
     */
    @Pointcut("execution(* *.*payment*(..)) || " +
              "execution(* *.*transfer*(..)) || " +
              "execution(* *.*transaction*(..)) || " +
              "execution(* *.*deposit*(..)) || " +
              "execution(* *.*withdraw*(..)) || " +
              "execution(* *.*balance*(..))")
    public void financialOperationsByName() {}

    /**
     * Around advice for financial operations with @FinancialOperation annotation
     */
    @Around("financialOperationMethods() && @annotation(financialOp)")
    public Object traceFinancialOperation(ProceedingJoinPoint joinPoint, FinancialOperation financialOp) throws Throwable {
        // Extract financial context from annotation
        String transactionType = financialOp.type().isEmpty() ? 
            extractTransactionType(joinPoint.getSignature().getName()) : financialOp.type();
        
        // Extract parameters for tracing
        FinancialParams params = extractFinancialParams(joinPoint.getArgs(), financialOp);
        
        // Start financial transaction tracing
        FinancialTransactionContext context = financialTracing.startFinancialTransaction(
            transactionType,
            params.getUserId(),
            params.getAmount(),
            params.getCurrency(),
            params.getPaymentMethod()
        );
        
        try {
            // Update state to processing
            financialTracing.updateTransactionState(
                FinancialTransactionTracing.TransactionState.PROCESSING, 
                "Started " + transactionType
            );
            
            // Execute the method
            Object result = joinPoint.proceed();
            
            // Mark as successful
            financialTracing.updateTransactionState(
                FinancialTransactionTracing.TransactionState.COMPLETED,
                "Operation completed successfully"
            );
            
            financialTracing.completeFinancialTransaction(true, "SUCCESS", null);
            
            return result;
            
        } catch (Exception e) {
            // Mark as failed
            financialTracing.updateTransactionState(
                FinancialTransactionTracing.TransactionState.FAILED,
                "Operation failed: " + e.getMessage()
            );
            
            financialTracing.completeFinancialTransaction(false, "ERROR", e.getMessage());
            
            throw e;
        }
    }

    /**
     * Around advice for financial service methods (automatic detection)
     */
    @Around("financialServiceMethods() && !financialOperationMethods()")
    public Object traceFinancialServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String serviceName = extractServiceName(joinPoint.getTarget().getClass().getName());
        String operation = joinPoint.getSignature().getName();
        
        // Create service span if within financial transaction context
        if (isWithinFinancialTransaction()) {
            return executeWithFinancialServiceSpan(joinPoint, serviceName, operation);
        } else {
            // Execute without financial tracing if not in financial context
            return joinPoint.proceed();
        }
    }

    /**
     * Around advice for methods with financial operation names
     */
    @Around("financialOperationsByName() && !financialOperationMethods() && !financialServiceMethods()")
    public Object traceFinancialOperationByName(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        
        // Only trace if method seems to be a financial operation
        if (isLikelyFinancialOperation(methodName, joinPoint.getArgs())) {
            String serviceName = extractServiceName(joinPoint.getTarget().getClass().getName());
            
            if (isWithinFinancialTransaction()) {
                return executeWithFinancialServiceSpan(joinPoint, serviceName, methodName);
            }
        }
        
        return joinPoint.proceed();
    }

    // Private helper methods

    private Object executeWithFinancialServiceSpan(ProceedingJoinPoint joinPoint, String serviceName, String operation) throws Throwable {
        var serviceSpan = financialTracing.createFinancialServiceSpan(serviceName, operation);
        
        try {
            financialTracing.recordFinancialServiceState(serviceName, "STARTED", null);
            
            Object result = joinPoint.proceed();
            
            financialTracing.recordFinancialServiceState(serviceName, "COMPLETED", null);
            serviceSpan.tag("waqiti.service.success", "true");
            
            return result;
            
        } catch (Exception e) {
            financialTracing.recordFinancialServiceState(serviceName, "FAILED", 
                java.util.Map.of("error", e.getMessage()));
            serviceSpan.tag("waqiti.service.success", "false")
                     .tag("waqiti.service.error", e.getMessage());
            throw e;
        } finally {
            serviceSpan.end();
        }
    }

    private boolean isWithinFinancialTransaction() {
        return financialTracing.getCurrentFinancialContext() != null;
    }

    private String extractServiceName(String className) {
        // Extract service name from class name
        if (className.contains(".payment.")) return "payment-service";
        if (className.contains(".transaction.")) return "transaction-service";
        if (className.contains(".wallet.")) return "wallet-service";
        if (className.contains(".fraud.")) return "fraud-detection-service";
        if (className.contains(".compliance.")) return "compliance-service";
        if (className.contains(".user.")) return "user-service";
        if (className.contains(".kyc.")) return "kyc-service";
        
        // Fallback: extract from package name
        String[] parts = className.split("\\.");
        for (String part : parts) {
            if (part.endsWith("service") || part.endsWith("Service")) {
                return part.toLowerCase().replace("service", "") + "-service";
            }
        }
        
        return "unknown-service";
    }

    private String extractTransactionType(String methodName) {
        String method = methodName.toLowerCase();
        if (method.contains("payment")) return "PAYMENT";
        if (method.contains("transfer")) return "TRANSFER";
        if (method.contains("deposit")) return "DEPOSIT";
        if (method.contains("withdraw")) return "WITHDRAWAL";
        if (method.contains("refund")) return "REFUND";
        return "FINANCIAL_OPERATION";
    }

    private FinancialParams extractFinancialParams(Object[] args, FinancialOperation annotation) {
        FinancialParams params = new FinancialParams();
        
        // Try to extract from annotation parameters
        if (!annotation.userIdParam().isEmpty()) {
            params.setUserId(getParameterValue(args, annotation.userIdParam(), String.class));
        }
        if (!annotation.amountParam().isEmpty()) {
            params.setAmount(getParameterValue(args, annotation.amountParam(), BigDecimal.class));
        }
        if (!annotation.currencyParam().isEmpty()) {
            params.setCurrency(getParameterValue(args, annotation.currencyParam(), String.class));
        }
        
        // Fallback: auto-detect from method parameters
        if (params.getUserId() == null) {
            params.setUserId(findParameterByType(args, String.class, "userId", "user"));
        }
        if (params.getAmount() == null) {
            params.setAmount(findParameterByType(args, BigDecimal.class, "amount", "value"));
        }
        if (params.getCurrency() == null) {
            params.setCurrency(findParameterByType(args, String.class, "currency", "curr"));
        }
        
        // Set defaults if not found
        if (params.getUserId() == null) params.setUserId("unknown");
        if (params.getAmount() == null) params.setAmount(BigDecimal.ZERO);
        if (params.getCurrency() == null) params.setCurrency("USD");
        if (params.getPaymentMethod() == null) params.setPaymentMethod("unknown");
        
        return params;
    }

    @SuppressWarnings("unchecked")
    private <T> T getParameterValue(Object[] args, String paramName, Class<T> type) {
        // This is a simplified implementation
        // In a real scenario, you'd need reflection to match parameter names
        for (Object arg : args) {
            if (type.isInstance(arg)) {
                return (T) arg;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T findParameterByType(Object[] args, Class<T> type, String... hints) {
        for (Object arg : args) {
            if (type.isInstance(arg)) {
                return (T) arg;
            }
        }
        return null;
    }

    private boolean isLikelyFinancialOperation(String methodName, Object[] args) {
        // Check if method name suggests financial operation
        String method = methodName.toLowerCase();
        boolean hasFinancialName = method.contains("payment") || method.contains("transfer") || 
                                  method.contains("deposit") || method.contains("withdraw") ||
                                  method.contains("balance") || method.contains("transaction");
        
        // Check if parameters suggest financial operation (has BigDecimal amount)
        boolean hasFinancialParams = Arrays.stream(args)
            .anyMatch(arg -> arg instanceof BigDecimal);
        
        return hasFinancialName || hasFinancialParams;
    }

    /**
     * Internal class to hold extracted financial parameters
     */
    private static class FinancialParams {
        private String userId;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    }
}