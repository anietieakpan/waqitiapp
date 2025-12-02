package com.waqiti.compliance.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pattern Analysis Result DTO
 *
 * Results from transaction pattern analysis for AML compliance.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternAnalysisResult {

    // Analysis Details
    private String analysisId;
    private Long transactionId;
    private Long customerId;
    private LocalDateTime analyzedAt;

    // Results
    private Boolean hasSuspiciousPatterns;
    private Integer suspiciousPatternCount;
    private List<SuspiciousPattern> suspiciousPatterns;

    // Detected Anomalies
    private Boolean hasAnomalies;
    private Integer anomalyCount;
    private List<TransactionAnomaly> anomalies;

    // Risk Score
    private Integer riskScore; // 0-100
    private String riskLevel; // "LOW", "MEDIUM", "HIGH", "CRITICAL"

    // Pattern Types Detected
    private Boolean hasStructuringPattern;
    private Boolean hasSmurfingPattern;
    private Boolean hasLayeringPattern;
    private Boolean hasRapidMovementPattern;
    private Boolean hasUnusualVelocityPattern;

    // Recommendation
    private String recommendation; // "APPROVE", "REVIEW", "BLOCK", "ESCALATE"
    private Boolean requiresManualReview;
    private Boolean requiresSAR;

    // Metadata
    private Integer transactionsAnalyzed;
    private Integer daysAnalyzed;
    private String algorithmVersion;

    // Helper Methods
    public boolean requiresAction() {
        return hasSuspiciousPatterns != null && hasSuspiciousPatterns ||
                "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }

    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }

    public boolean shouldBlock() {
        return "BLOCK".equals(recommendation);
    }

    public boolean shouldEscalate() {
        return "ESCALATE".equals(recommendation) || requiresSAR != null && requiresSAR;
    }
}
