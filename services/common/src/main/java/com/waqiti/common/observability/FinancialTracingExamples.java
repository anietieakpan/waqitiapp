package com.waqiti.common.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * EXAMPLES: Financial Tracing Integration Examples
 * 
 * Demonstrates how to use the enhanced distributed tracing system
 * for financial operations across Waqiti microservices.
 * 
 * This class shows practical usage patterns for:
 * - Automatic tracing with @FinancialOperation annotation
 * - Manual tracing for complex workflows
 * - Cross-service correlation propagation
 * - Fraud detection integration
 * - Compliance tracking
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialTracingExamples {

    private final FinancialTransactionTracing financialTracing;

    /**
     * Example 1: Automatic tracing with annotation
     * The @FinancialOperation annotation automatically:
     * - Creates financial transaction context
     * - Generates correlation IDs
     * - Records business metrics
     * - Handles errors and state transitions
     */
    @FinancialOperation(
        type = "PAYMENT",
        userIdParam = "userId",
        amountParam = "amount", 
        currencyParam = "currency",
        riskLevel = FinancialOperation.RiskLevel.MEDIUM,
        description = "Process customer payment"
    )
    public String processPaymentAutomatic(String userId, BigDecimal amount, String currency, String paymentMethod) {
        log.info("Processing payment for user {} amount {} {}", userId, amount, currency);
        
        // Business logic here - tracing is automatic
        // Fraud detection will be automatically triggered
        // Compliance checks will be recorded
        // Cross-service calls will propagate correlation context
        
        return "payment_123456";
    }

    /**
     * Example 2: Manual tracing for complex workflows
     * For operations requiring custom tracing logic
     */
    public String processComplexPaymentManual(String userId, BigDecimal amount, String currency) {
        // Start financial transaction manually
        FinancialTransactionContext context = financialTracing.startFinancialTransaction(
            "COMPLEX_PAYMENT", userId, amount, currency, "BANK_TRANSFER"
        );
        
        try {
            // Step 1: Validation
            financialTracing.updateTransactionState(
                FinancialTransactionTracing.TransactionState.VALIDATING,
                "Starting payment validation"
            );
            
            validatePaymentRequest(userId, amount, currency);
            
            // Step 2: Fraud Check
            financialTracing.updateTransactionState(
                FinancialTransactionTracing.TransactionState.FRAUD_CHECK,
                "Performing fraud analysis"
            );
            
            performFraudCheck(context);
            
            // Step 3: Compliance Check
            financialTracing.updateTransactionState(
                FinancialTransactionTracing.TransactionState.COMPLIANCE_CHECK,
                "Verifying compliance requirements"
            );
            
            performComplianceCheck(context);
            
            // Step 4: Process Payment
            financialTracing.updateTransactionState(
                FinancialTransactionTracing.TransactionState.PROCESSING,
                "Processing payment through bank"
            );
            
            String paymentId = callBankService(userId, amount, currency);
            
            // Success
            financialTracing.completeFinancialTransaction(true, "SUCCESS", null);
            return paymentId;
            
        } catch (Exception e) {
            log.error("Payment processing failed", e);
            financialTracing.completeFinancialTransaction(false, "ERROR", e.getMessage());
            throw e;
        }
    }

    /**
     * Example 3: Service method with automatic span creation
     * When called within a financial transaction context,
     * automatically creates service spans
     */
    public void validatePaymentRequest(String userId, BigDecimal amount, String currency) {
        // This method will automatically get a service span if called
        // within a financial transaction context
        
        log.info("Validating payment request for user {}", userId);
        
        // Validation logic
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("Invalid currency code");
        }
        
        // Record validation success
        financialTracing.recordFinancialServiceState(
            "validation-service", 
            "VALIDATED", 
            Map.of("amount", amount.toString(), "currency", currency)
        );
    }

    /**
     * Example 4: Fraud detection integration
     */
    private void performFraudCheck(FinancialTransactionContext context) {
        log.info("Performing fraud check for transaction {}", context.getFinancialCorrelationId());
        
        // Simulate fraud detection service call
        double riskScore = calculateRiskScore(context.getAmount(), context.getUserId());
        boolean fraudDetected = riskScore > 0.8;
        String riskLevel = getRiskLevel(riskScore);
        
        // Record fraud analysis results
        financialTracing.recordFraudAnalysis(
            riskScore, 
            fraudDetected, 
            riskLevel,
            "ML-based fraud detection analysis completed"
        );
        
        if (fraudDetected) {
            throw new SecurityException("Transaction flagged as fraudulent");
        }
    }

    /**
     * Example 5: Compliance check integration
     */
    private void performComplianceCheck(FinancialTransactionContext context) {
        log.info("Performing compliance check for transaction {}", context.getFinancialCorrelationId());
        
        // AML check
        boolean amlPassed = context.getAmount().compareTo(new BigDecimal("10000")) < 0;
        financialTracing.recordComplianceCheck(
            "AML", 
            amlPassed, 
            amlPassed ? "Amount below AML threshold" : "AML review required"
        );
        
        // Sanctions screening
        boolean sanctionsClear = !isUserOnSanctionsList(context.getUserId());
        financialTracing.recordComplianceCheck(
            "SANCTIONS", 
            sanctionsClear, 
            sanctionsClear ? "User not on sanctions list" : "User found on sanctions list"
        );
        
        if (!amlPassed || !sanctionsClear) {
            throw new SecurityException("Compliance check failed");
        }
    }

    /**
     * Example 6: External service call with correlation propagation
     */
    private String callBankService(String userId, BigDecimal amount, String currency) {
        // Create service span for external call
        var span = financialTracing.createFinancialServiceSpan("bank-service", "process_payment");
        
        try {
            // Record service state
            financialTracing.recordFinancialServiceState(
                "bank-service", 
                "PROCESSING", 
                Map.of("bank", "partner_bank", "method", "ACH")
            );
            
            // Simulate bank service call
            // The FinancialCorrelationInterceptor will automatically add
            // correlation headers to the outgoing request
            String paymentId = simulateBankServiceCall(userId, amount, currency);
            
            financialTracing.recordFinancialServiceState(
                "bank-service", 
                "COMPLETED", 
                Map.of("payment_id", paymentId)
            );
            
            span.tag("waqiti.bank.payment.id", paymentId);
            return paymentId;
            
        } catch (Exception e) {
            financialTracing.recordFinancialServiceState(
                "bank-service", 
                "FAILED", 
                Map.of("error", e.getMessage())
            );
            throw e;
        } finally {
            span.end();
        }
    }

    // Helper methods for examples

    private double calculateRiskScore(BigDecimal amount, String userId) {
        // Simplified risk calculation
        double amountRisk = Math.min(amount.doubleValue() / 10000.0, 1.0);
        double userRisk = userId.hashCode() % 100 / 100.0;
        return (amountRisk + userRisk) / 2.0;
    }

    private String getRiskLevel(double score) {
        if (score > 0.8) return "HIGH";
        if (score > 0.5) return "MEDIUM";
        return "LOW";
    }

    private boolean isUserOnSanctionsList(String userId) {
        // Simplified sanctions check
        return userId.contains("sanctioned");
    }

    private String simulateBankServiceCall(String userId, BigDecimal amount, String currency) {
        // Simulate external bank service call
        try {
            Thread.sleep(500); // Simulate network latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return "BANK_PMT_" + System.currentTimeMillis();
    }
}