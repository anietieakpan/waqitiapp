package com.waqiti.payment.client.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Batch fraud evaluation response DTO
 * Results for multiple transaction fraud evaluations processed together
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class BatchFraudEvaluationResponse {
    
    @NonNull
    private UUID batchId;
    
    private BatchStatus batchStatus;
    
    private LocalDateTime processedAt;
    
    private Long totalProcessingTimeMs;
    
    // Results for individual payments
    @Builder.Default
    private List<PaymentFraudResult> results = List.of();
    
    // Batch-level analytics
    private BatchStatistics statistics;
    
    // Cross-payment patterns detected
    private CrossPaymentAnalysis crossPaymentAnalysis;
    
    // Batch-level recommendations
    private BatchRecommendation batchRecommendation;
    
    // Processing metrics
    private ProcessingMetrics processingMetrics;
    
    // Error handling
    @Builder.Default
    private List<BatchError> errors = List.of();
    
    // Additional context
    private Map<String, Object> batchMetadata;
    
    public enum BatchStatus {
        SUCCESS,           // All payments processed successfully
        PARTIAL_SUCCESS,   // Some payments processed with errors
        FAILED,           // Batch processing failed
        TIMEOUT,          // Batch processing timed out
        CANCELLED         // Batch processing was cancelled
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentFraudResult {
        private UUID paymentId;
        private FraudEvaluationResponse evaluationResponse;
        private PaymentResultStatus status;
        private String errorMessage;
        private Long processingTimeMs;
        
        public enum PaymentResultStatus {
            SUCCESS,
            ERROR,
            SKIPPED,
            TIMEOUT
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchStatistics {
        private Integer totalPayments;
        private Integer successfulEvaluations;
        private Integer failedEvaluations;
        private Integer highRiskPayments;
        private Integer mediumRiskPayments;
        private Integer lowRiskPayments;
        private Integer blockedPayments;
        private Integer flaggedForReview;
        private Double averageRiskScore;
        private Double averageProcessingTime;
        private Integer totalAlertsGenerated;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrossPaymentAnalysis {
        private boolean hasSuspiciousPatterns;
        private boolean hasCoordinatedAttack;
        private boolean hasVelocityAnomalies;
        private boolean hasGeographicClustering;
        private boolean hasDeviceClustering;
        @Builder.Default
        private List<CrossPaymentPattern> patternsDetected = List.of();
        private Map<String, Object> clusteringResults;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class CrossPaymentPattern {
            private String patternType;
            private String description;
            private Double confidence;
            private List<UUID> affectedPaymentIds;
            private Map<String, Object> patternData;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchRecommendation {
        private BatchAction recommendedAction;
        private String reasoning;
        private Integer urgencyLevel; // 1-10
        @Builder.Default
        private List<String> specificActions = List.of();
        private Map<String, Object> actionParameters;
        
        public enum BatchAction {
            PROCEED_ALL,
            REVIEW_HIGH_RISK,
            BLOCK_SUSPICIOUS,
            INVESTIGATE_PATTERNS,
            ESCALATE_TO_ANALYST
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingMetrics {
        private Integer threadsUsed;
        private Long queueWaitTimeMs;
        private Long averagePaymentProcessingMs;
        private Long maxPaymentProcessingMs;
        private Long minPaymentProcessingMs;
        private Double throughputPerSecond;
        private Integer retryCount;
        private String processingMode; // SEQUENTIAL, PARALLEL, etc.
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchError {
        private String errorCode;
        private String errorMessage;
        private String errorType;
        private List<UUID> affectedPaymentIds;
        private LocalDateTime occurredAt;
        private Map<String, Object> errorContext;
    }
    
    // Business logic methods
    public boolean isFullySuccessful() {
        return batchStatus == BatchStatus.SUCCESS;
    }
    
    public boolean hasHighRiskPayments() {
        return statistics != null && 
               statistics.getHighRiskPayments() != null && 
               statistics.getHighRiskPayments() > 0;
    }
    
    public boolean requiresManualReview() {
        return (statistics != null && statistics.getFlaggedForReview() > 0) ||
               (crossPaymentAnalysis != null && crossPaymentAnalysis.hasSuspiciousPatterns()) ||
               (batchRecommendation != null && 
                batchRecommendation.getRecommendedAction() != BatchRecommendation.BatchAction.PROCEED_ALL);
    }
    
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
    
    public Double getSuccessRate() {
        if (statistics == null || statistics.getTotalPayments() == 0) {
            return 0.0;
        }
        return (double) statistics.getSuccessfulEvaluations() / statistics.getTotalPayments() * 100.0;
    }
    
    public boolean hasCrossPaymentRisks() {
        return crossPaymentAnalysis != null && 
               (crossPaymentAnalysis.hasSuspiciousPatterns() || 
                crossPaymentAnalysis.hasCoordinatedAttack() ||
                crossPaymentAnalysis.hasVelocityAnomalies());
    }
}