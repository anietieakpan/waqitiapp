package com.waqiti.common.observability;

import io.micrometer.tracing.Span;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CRITICAL FINANCIAL: Financial Transaction Context
 * 
 * Comprehensive context holder for financial transactions across microservices.
 * Maintains state, correlation, audit trail, and compliance information
 * throughout the entire transaction lifecycle.
 */
@Data
@Builder
public class FinancialTransactionContext {
    
    // Core identification
    private final String financialCorrelationId;
    private final String traceId;
    private final String spanId;
    
    // Transaction details
    private final String transactionType;
    private final String userId;
    private final BigDecimal amount;
    private final String currency;
    private final String paymentMethod;
    
    // Timing information
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    
    // State management
    private FinancialTransactionTracing.TransactionState transactionState;
    private boolean successful;
    private String resultCode;
    private String errorMessage;
    
    // Tracing context
    private Span currentSpan;
    
    // Service state tracking
    @Builder.Default
    private final Map<String, ServiceState> serviceStates = new HashMap<>();
    
    // State transition tracking
    @Builder.Default
    private final List<StateTransition> stateTransitions = new ArrayList<>();
    
    // Fraud detection results
    private double fraudRiskScore;
    private boolean fraudDetected;
    private String fraudRiskLevel;
    
    // Compliance tracking
    @Builder.Default
    private final List<ComplianceCheck> complianceChecks = new ArrayList<>();
    
    // Performance metrics
    @Builder.Default
    private final Map<String, Long> performanceMetrics = new HashMap<>();
    
    /**
     * Calculate transaction duration
     */
    public Duration getDuration() {
        if (endTime == null) {
            return Duration.between(startTime, LocalDateTime.now());
        }
        return Duration.between(startTime, endTime);
    }
    
    /**
     * Get duration in milliseconds
     */
    public long getDurationMillis() {
        return getDuration().toMillis();
    }
    
    /**
     * Add service state information
     */
    public void addServiceState(String serviceName, String state, Map<String, String> metadata) {
        ServiceState serviceState = ServiceState.builder()
            .serviceName(serviceName)
            .state(state)
            .timestamp(LocalDateTime.now())
            .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
            .build();
        
        serviceStates.put(serviceName, serviceState);
    }
    
    /**
     * Add state transition
     */
    public void addStateTransition(FinancialTransactionTracing.TransactionState fromState, 
                                  FinancialTransactionTracing.TransactionState toState, 
                                  String reason) {
        StateTransition transition = StateTransition.builder()
            .fromState(fromState)
            .toState(toState)
            .reason(reason)
            .timestamp(LocalDateTime.now())
            .build();
        
        stateTransitions.add(transition);
    }
    
    /**
     * Add compliance check result
     */
    public void addComplianceCheck(String checkType, boolean passed, String details) {
        ComplianceCheck check = ComplianceCheck.builder()
            .checkType(checkType)
            .passed(passed)
            .details(details)
            .timestamp(LocalDateTime.now())
            .build();
        
        complianceChecks.add(check);
    }
    
    /**
     * Add performance metric
     */
    public void addPerformanceMetric(String metricName, long value) {
        performanceMetrics.put(metricName, value);
    }
    
    /**
     * Get service state for specific service
     */
    public ServiceState getServiceState(String serviceName) {
        return serviceStates.get(serviceName);
    }
    
    /**
     * Check if transaction is in terminal state
     */
    public boolean isInTerminalState() {
        return transactionState == FinancialTransactionTracing.TransactionState.COMPLETED ||
               transactionState == FinancialTransactionTracing.TransactionState.FAILED ||
               transactionState == FinancialTransactionTracing.TransactionState.CANCELLED ||
               transactionState == FinancialTransactionTracing.TransactionState.REFUNDED;
    }
    
    /**
     * Check if transaction passed all compliance checks
     */
    public boolean isCompliant() {
        return complianceChecks.stream().allMatch(ComplianceCheck::isPassed);
    }
    
    /**
     * Get highest fraud risk level encountered
     */
    public String getHighestFraudRiskLevel() {
        if (fraudDetected) return "HIGH";
        if (fraudRiskScore > 0.7) return "MEDIUM";
        if (fraudRiskScore > 0.3) return "LOW";
        return "MINIMAL";
    }
    
    /**
     * Generate summary for audit trail
     */
    public String generateAuditSummary() {
        return String.format(
            "FinancialTransaction[id=%s, type=%s, user=%s, amount=%s %s, " +
            "duration=%dms, state=%s, successful=%s, fraud_risk=%.2f, compliance_checks=%d]",
            financialCorrelationId, transactionType, userId, amount, currency,
            getDurationMillis(), transactionState, successful, fraudRiskScore, complianceChecks.size()
        );
    }
    
    /**
     * Service state information
     */
    @Data
    @Builder
    public static class ServiceState {
        private final String serviceName;
        private final String state;
        private final LocalDateTime timestamp;
        @Builder.Default
        private final Map<String, String> metadata = new HashMap<>();
    }
    
    /**
     * State transition tracking
     */
    @Data
    @Builder
    public static class StateTransition {
        private final FinancialTransactionTracing.TransactionState fromState;
        private final FinancialTransactionTracing.TransactionState toState;
        private final String reason;
        private final LocalDateTime timestamp;
    }
    
    /**
     * Compliance check result
     */
    @Data
    @Builder
    public static class ComplianceCheck {
        private final String checkType;
        private final boolean passed;
        private final String details;
        private final LocalDateTime timestamp;
    }
}