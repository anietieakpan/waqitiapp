package com.waqiti.payment.core.integration;

import com.waqiti.payment.core.model.PaymentStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Payment processing result from orchestration pipeline
 * Complete result with all processing details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"processingDetails", "auditTrail"})
public class PaymentProcessingResult {
    
    private UUID resultId;
    private UUID requestId;
    private PaymentStatus status;
    private ProcessingStatus processingStatus;
    private String transactionId;
    private String providerTransactionId;
    private String provider;
    private BigDecimal processedAmount;
    private String currency;
    private BigDecimal totalFees;
    private BigDecimal netAmount;
    private LocalDateTime processedAt;
    private Long processingTimeMs;
    private String confirmationNumber;
    private PaymentProcessingError error;
    
    @Builder.Default
    private List<ProcessingStep> processingSteps = new ArrayList<>();
    
    private FraudCheckResult fraudCheckResult;
    private ComplianceCheckResult complianceCheckResult;
    private SettlementInfo settlementInfo;
    private Map<String, Object> providerResponse;
    private Map<String, Object> processingDetails;
    
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    
    @Builder.Default
    private List<AuditEntry> auditTrail = new ArrayList<>();
    
    private Map<String, Object> metadata;
    
    public enum ProcessingStatus {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED,
        PENDING,
        CANCELLED,
        TIMEOUT,
        RETRY_REQUIRED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingStep {
        private String stepName;
        private StepStatus status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long durationMs;
        private String message;
        private Map<String, Object> details;
    }
    
    public enum StepStatus {
        STARTED,
        COMPLETED,
        FAILED,
        SKIPPED,
        WARNING
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentProcessingError {
        private String errorCode;
        private String errorMessage;
        private ErrorCategory category;
        private String provider;
        private Map<String, Object> errorDetails;
        private boolean retryable;
        private LocalDateTime occurredAt;
    }
    
    public enum ErrorCategory {
        VALIDATION,
        FRAUD,
        COMPLIANCE,
        PROVIDER,
        NETWORK,
        TIMEOUT,
        INSUFFICIENT_FUNDS,
        CONFIGURATION,
        SYSTEM,
        UNKNOWN
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudCheckResult {
        private boolean passed;
        private BigDecimal riskScore;
        private String riskLevel;
        private List<String> flaggedRules;
        private String recommendation;
        private Map<String, Object> details;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceCheckResult {
        private boolean passed;
        private List<String> violations;
        private String complianceLevel;
        private Map<String, Object> checkResults;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementInfo {
        private String settlementId;
        private LocalDateTime expectedSettlementDate;
        private String settlementStatus;
        private String batchId;
        private Map<String, Object> settlementDetails;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditEntry {
        private LocalDateTime timestamp;
        private String action;
        private String actor;
        private String description;
        private Map<String, Object> data;
    }
    
    // Business logic methods
    public boolean isSuccessful() {
        return processingStatus == ProcessingStatus.SUCCESS;
    }
    
    public boolean requiresRetry() {
        return processingStatus == ProcessingStatus.RETRY_REQUIRED ||
               (error != null && error.isRetryable());
    }
    
    public boolean hasFraudIssues() {
        return fraudCheckResult != null && !fraudCheckResult.isPassed();
    }
    
    public boolean hasComplianceIssues() {
        return complianceCheckResult != null && !complianceCheckResult.isPassed();
    }
    
    public void addProcessingStep(ProcessingStep step) {
        if (processingSteps == null) {
            processingSteps = new ArrayList<>();
        }
        processingSteps.add(step);
    }
    
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }
    
    public void addAuditEntry(AuditEntry entry) {
        if (auditTrail == null) {
            auditTrail = new ArrayList<>();
        }
        auditTrail.add(entry);
    }
}