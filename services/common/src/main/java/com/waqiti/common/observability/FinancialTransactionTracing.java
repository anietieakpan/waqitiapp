package com.waqiti.common.observability;

import com.waqiti.common.correlation.CorrelationIdService;
import com.waqiti.common.tracing.TracingUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CRITICAL FINANCIAL: End-to-End Transaction Tracing Service
 * 
 * Provides comprehensive tracing and correlation for financial transactions
 * across all Waqiti services with compliance and audit requirements.
 * 
 * Features:
 * - Financial transaction correlation across microservices
 * - Regulatory compliance tracking (PCI DSS, SOC 2)
 * - Real-time transaction monitoring
 * - Cross-service transaction state management
 * - Audit trail generation for financial operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialTransactionTracing {

    private final Tracer tracer;
    private final CorrelationIdService correlationIdService;
    private final BusinessMetricsRegistry businessMetrics;
    
    // Active financial transaction tracking
    private final Map<String, FinancialTransactionContext> activeTransactions = new ConcurrentHashMap<>();
    
    /**
     * Start comprehensive tracing for a financial transaction
     * Creates correlation context that spans across all services
     */
    public FinancialTransactionContext startFinancialTransaction(
            String transactionType, 
            String userId, 
            BigDecimal amount, 
            String currency,
            String paymentMethod) {
        
        // Generate unique financial transaction correlation ID
        String financialCorrelationId = "FIN-" + UUID.randomUUID().toString().toUpperCase();
        String traceId = correlationIdService.generateTraceId();
        
        // Set correlation context
        correlationIdService.setCorrelationId(financialCorrelationId);
        correlationIdService.setTraceContext(traceId, null);
        
        // Create comprehensive financial transaction span
        Span financialSpan = tracer.nextSpan()
            .name("financial.transaction." + transactionType)
            .tag("waqiti.transaction.id", financialCorrelationId)
            .tag("waqiti.transaction.type", transactionType)
            .tag("waqiti.user.id", userId)
            .tag("waqiti.amount", amount.toString())
            .tag("waqiti.currency", currency)
            .tag("waqiti.payment.method", paymentMethod)
            .tag("waqiti.financial.context", "true")
            .tag("waqiti.compliance.level", "PCI_DSS")
            .tag("waqiti.audit.required", "true")
            .start();
        
        // Create financial transaction context
        FinancialTransactionContext context = FinancialTransactionContext.builder()
            .financialCorrelationId(financialCorrelationId)
            .traceId(traceId)
            .spanId(financialSpan.context().spanId())
            .transactionType(transactionType)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .paymentMethod(paymentMethod)
            .startTime(LocalDateTime.now())
            .currentSpan(financialSpan)
            .transactionState(TransactionState.INITIATED)
            .build();
        
        // Track active transaction
        activeTransactions.put(financialCorrelationId, context);
        
        // Record business metrics
        businessMetrics.incrementPendingTransactions();
        
        // Add detailed financial context
        addFinancialSecurityContext(userId, amount, currency);
        
        log.info("FINANCIAL_TRANSACTION_STARTED: id={}, type={}, user={}, amount={} {}", 
                financialCorrelationId, transactionType, userId, amount, currency);
        
        return context;
    }
    
    /**
     * Create child span for service operations within financial transaction
     */
    public Span createFinancialServiceSpan(String serviceName, String operation) {
        String correlationId = correlationIdService.getOrCreateCorrelationId();
        FinancialTransactionContext context = activeTransactions.get(extractFinancialId(correlationId));
        
        Span serviceSpan = tracer.nextSpan()
            .name("financial." + serviceName + "." + operation)
            .tag("waqiti.service.name", serviceName)
            .tag("waqiti.service.operation", operation)
            .tag("waqiti.financial.correlation.id", correlationId)
            .tag("waqiti.parent.transaction.type", context != null ? context.getTransactionType() : "unknown")
            .start();
        
        if (context != null) {
            serviceSpan.tag("waqiti.transaction.state", context.getTransactionState().name())
                    .tag("waqiti.user.id", context.getUserId())
                    .tag("waqiti.currency", context.getCurrency());
        }
        
        log.debug("FINANCIAL_SERVICE_SPAN_CREATED: service={}, operation={}, correlation={}", 
                serviceName, operation, correlationId);
        
        return serviceSpan;
    }
    
    /**
     * Record service state change within financial transaction
     */
    public void recordFinancialServiceState(String serviceName, String state, Map<String, String> metadata) {
        String correlationId = correlationIdService.getOrCreateCorrelationId();
        String financialId = extractFinancialId(correlationId);
        FinancialTransactionContext context = activeTransactions.get(financialId);
        
        if (context != null) {
            context.addServiceState(serviceName, state, metadata);
            
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                currentSpan.tag("waqiti.service.state." + serviceName, state)
                        .tag("waqiti.state.timestamp", LocalDateTime.now().toString());
                
                if (metadata != null) {
                    metadata.forEach((key, value) -> 
                        currentSpan.tag("waqiti.service.metadata." + key, value));
                }
            }
            
            log.debug("FINANCIAL_SERVICE_STATE: service={}, state={}, correlation={}", 
                    serviceName, state, financialId);
        }
    }
    
    /**
     * Update transaction state and propagate across services
     */
    public void updateTransactionState(TransactionState newState, String reason) {
        String correlationId = correlationIdService.getOrCreateCorrelationId();
        String financialId = extractFinancialId(correlationId);
        FinancialTransactionContext context = activeTransactions.get(financialId);
        
        if (context != null) {
            TransactionState previousState = context.getTransactionState();
            context.setTransactionState(newState);
            context.addStateTransition(previousState, newState, reason);
            
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                currentSpan.tag("waqiti.transaction.state", newState.name())
                        .tag("waqiti.state.transition", previousState.name() + " -> " + newState.name())
                        .tag("waqiti.state.reason", reason != null ? reason : "unknown");
            }
            
            // Update business metrics based on state
            updateMetricsForState(newState, context);
            
            log.info("FINANCIAL_TRANSACTION_STATE_CHANGE: id={}, {} -> {}, reason={}", 
                    financialId, previousState, newState, reason);
        }
    }
    
    /**
     * Record fraud detection results in transaction context
     */
    public void recordFraudAnalysis(double riskScore, boolean fraudDetected, String riskLevel, String details) {
        String correlationId = correlationIdService.getOrCreateCorrelationId();
        String financialId = extractFinancialId(correlationId);
        FinancialTransactionContext context = activeTransactions.get(financialId);
        
        if (context != null) {
            context.setFraudRiskScore(riskScore);
            context.setFraudDetected(fraudDetected);
            context.setFraudRiskLevel(riskLevel);
            
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                TracingUtils.addFraudDetectionResults(riskScore, fraudDetected, riskLevel);
                currentSpan.tag("waqiti.fraud.details", details != null ? details : "none");
            }
            
            // Record fraud metrics
            if (fraudDetected) {
                businessMetrics.recordFraudDetection(context.getFinancialCorrelationId(), riskScore);
                
                log.warn("FINANCIAL_FRAUD_DETECTED: id={}, score={}, level={}, details={}", 
                        financialId, riskScore, riskLevel, details);
            } else {
                log.debug("FINANCIAL_FRAUD_CLEAR: id={}, score={}, level={}", 
                        financialId, riskScore, riskLevel);
            }
        }
    }
    
    /**
     * Record compliance check results
     */
    public void recordComplianceCheck(String checkType, boolean passed, String details) {
        String correlationId = correlationIdService.getOrCreateCorrelationId();
        String financialId = extractFinancialId(correlationId);
        FinancialTransactionContext context = activeTransactions.get(financialId);
        
        if (context != null) {
            context.addComplianceCheck(checkType, passed, details);
            
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                currentSpan.tag("waqiti.compliance." + checkType, passed ? "PASS" : "FAIL")
                        .tag("waqiti.compliance.details." + checkType, details != null ? details : "none");
            }
            
            // Record compliance metrics
            businessMetrics.recordComplianceCheck(checkType, passed);
            
            log.info("FINANCIAL_COMPLIANCE_CHECK: id={}, type={}, result={}, details={}", 
                    financialId, checkType, passed ? "PASS" : "FAIL", details);
        }
    }
    
    /**
     * Complete financial transaction and cleanup
     */
    public void completeFinancialTransaction(boolean successful, String resultCode, String errorMessage) {
        String correlationId = correlationIdService.getOrCreateCorrelationId();
        String financialId = extractFinancialId(correlationId);
        FinancialTransactionContext context = activeTransactions.get(financialId);
        
        if (context != null) {
            context.setEndTime(LocalDateTime.now());
            context.setSuccessful(successful);
            context.setResultCode(resultCode);
            context.setErrorMessage(errorMessage);
            
            // Complete the main financial span
            Span financialSpan = context.getCurrentSpan();
            if (financialSpan != null) {
                financialSpan.tag("waqiti.transaction.successful", String.valueOf(successful))
                        .tag("waqiti.transaction.result.code", resultCode != null ? resultCode : "unknown")
                        .tag("waqiti.transaction.duration.ms", 
                             String.valueOf(context.getDurationMillis()));
                
                if (!successful && errorMessage != null) {
                    TracingUtils.addError(errorMessage, resultCode);
                }
                
                financialSpan.end();
            }
            
            // Update business metrics
            businessMetrics.decrementPendingTransactions();
            
            if (successful) {
                businessMetrics.recordPaymentTransaction(
                    context.getTransactionType(), 
                    "successful", 
                    context.getCurrency(), 
                    context.getAmount(), 
                    context.getDuration()
                );
            } else {
                businessMetrics.recordPaymentFailure(
                    context.getTransactionType(), 
                    "Transaction failed", 
                    resultCode, 
                    context.getAmount(), 
                    context.getCurrency()
                );
            }
            
            // Generate audit trail entry
            generateAuditTrail(context);
            
            // Remove from active tracking
            activeTransactions.remove(financialId);
            
            log.info("FINANCIAL_TRANSACTION_COMPLETED: id={}, successful={}, duration={}ms, result={}", 
                    financialId, successful, context.getDurationMillis(), resultCode);
        }
        
        // Clear correlation context
        correlationIdService.clearContext();
    }
    
    /**
     * Get current financial transaction context
     */
    public FinancialTransactionContext getCurrentFinancialContext() {
        String correlationId = correlationIdService.getCorrelationId().orElse(null);
        if (correlationId != null) {
            String financialId = extractFinancialId(correlationId);
            return activeTransactions.get(financialId);
        }
        return null;
    }
    
    /**
     * Get all active financial transactions (for monitoring)
     */
    public Map<String, FinancialTransactionContext> getActiveTransactions() {
        return Map.copyOf(activeTransactions);
    }
    
    // Private helper methods
    
    private void addFinancialSecurityContext(String userId, BigDecimal amount, String currency) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            TracingUtils.addUserContext(userId, "default");
            
            // Add security-relevant attributes
            currentSpan.tag("waqiti.security.risk.level", calculateRiskLevel(amount))
                    .tag("waqiti.security.pci.scope", "true")
                    .tag("waqiti.security.audit.required", "true")
                    .tag("waqiti.compliance.region", "US") // Would be dynamic in real implementation
                    .tag("waqiti.data.classification", "FINANCIAL");
        }
    }
    
    private String calculateRiskLevel(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("10000")) > 0) return "HIGH";
        if (amount.compareTo(new BigDecimal("1000")) > 0) return "MEDIUM";
        return "LOW";
    }
    
    private String extractFinancialId(String correlationId) {
        if (correlationId != null && correlationId.startsWith("FIN-")) {
            return correlationId;
        }
        // If not a financial correlation ID, try to find from context
        return correlationId;
    }
    
    private void updateMetricsForState(TransactionState state, FinancialTransactionContext context) {
        switch (state) {
            case PROCESSING -> businessMetrics.updateActiveUsers(businessMetrics.getActiveUsers() + 1);
            case FAILED, COMPLETED -> businessMetrics.updateActiveUsers(Math.max(0, businessMetrics.getActiveUsers() - 1));
        }
    }
    
    private void generateAuditTrail(FinancialTransactionContext context) {
        // This would typically write to an audit log system
        log.info("FINANCIAL_AUDIT_TRAIL: id={}, type={}, user={}, amount={} {}, " +
                "duration={}ms, states={}, compliance_checks={}, fraud_score={}", 
                context.getFinancialCorrelationId(),
                context.getTransactionType(),
                context.getUserId(),
                context.getAmount(),
                context.getCurrency(),
                context.getDurationMillis(),
                context.getStateTransitions().size(),
                context.getComplianceChecks().size(),
                context.getFraudRiskScore());
    }
    
    /**
     * Transaction states for financial operations
     */
    public enum TransactionState {
        INITIATED,
        VALIDATING,
        PROCESSING,
        FRAUD_CHECK,
        COMPLIANCE_CHECK,
        AUTHORIZING,
        SETTLING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED
    }
}