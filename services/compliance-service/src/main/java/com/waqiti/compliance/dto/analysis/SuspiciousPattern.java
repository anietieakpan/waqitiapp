package com.waqiti.compliance.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Suspicious Pattern DTO
 *
 * Represents a detected suspicious transaction pattern.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspiciousPattern {

    // Pattern Identification
    private String patternId;
    private String patternType; // "STRUCTURING", "SMURFING", "LAYERING", "RAPID_MOVEMENT", "UNUSUAL_VELOCITY"
    private String patternCategory; // "AML", "FRAUD", "SANCTIONS"

    // Description
    private String description;
    private String detailedExplanation;

    // Severity
    private String severity; // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    private Integer severityScore; // 0-100
    private String riskLevel; // "LOW", "MEDIUM", "HIGH", "CRITICAL"

    // Pattern Details
    private Integer transactionCount;
    private BigDecimal totalAmount;
    private LocalDateTime firstOccurrence;
    private LocalDateTime lastOccurrence;
    private Integer durationDays;

    // Involved Parties
    private Long customerId;
    private List<Long> relatedTransactionIds;
    private List<String> involvedAccounts;

    // Indicators
    private Boolean isStructuringIndicator;
    private Boolean isSmurfingIndicator;
    private Boolean isLayeringIndicator;
    private Boolean isIntegrationIndicator;

    // Thresholds Exceeded
    private List<String> exceededThresholds;
    private Boolean exceedsCTRThreshold;
    private Boolean exceedsSARThreshold;

    // Recommendations
    private String recommendation; // "MONITOR", "REVIEW", "REPORT", "BLOCK"
    private Boolean requiresSAR;
    private Boolean requiresCTR;
    private Boolean requiresManualReview;

    // Confidence
    private BigDecimal confidenceScore; // 0.0-1.0
    private String confidence; // "LOW", "MEDIUM", "HIGH"

    // Metadata
    private LocalDateTime detectedAt;
    private String detectionMethod;
    private String algorithmVersion;
    private String notes;

    // Helper Methods
    public boolean isCritical() {
        return "CRITICAL".equals(severity) || severityScore != null && severityScore >= 80;
    }

    public boolean requiresImmediateAction() {
        return isCritical() || requiresSAR != null && requiresSAR;
    }

    public boolean isHighConfidence() {
        return "HIGH".equals(confidence) ||
                (confidenceScore != null && confidenceScore.compareTo(BigDecimal.valueOf(0.8)) >= 0);
    }

    public boolean shouldReport() {
        return "REPORT".equals(recommendation) || requiresSAR != null && requiresSAR;
    }

    public boolean isStructuring() {
        return "STRUCTURING".equals(patternType);
    }

    public boolean isSmurfing() {
        return "SMURFING".equals(patternType);
    }

    public boolean isLayering() {
        return "LAYERING".equals(patternType);
    }
}
