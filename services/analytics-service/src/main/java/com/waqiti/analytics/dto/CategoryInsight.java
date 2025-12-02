package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Category Insight DTO
 *
 * Actionable insight about spending behavior in a category.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryInsight {

    // Insight Details
    private String insightId;
    private String insightType; // "OPPORTUNITY", "ALERT", "TREND", "RECOMMENDATION"
    private String severity; // "HIGH", "MEDIUM", "LOW"

    // Content
    private String title;
    private String description;
    private String actionableAdvice;

    // Impact
    private BigDecimal potentialSavings;
    private BigDecimal impactAmount;
    private String impactLevel; // "HIGH", "MEDIUM", "LOW"

    // Supporting Data
    private String evidenceDescription;
    private BigDecimal comparisonValue;
    private String comparisonBenchmark; // "PEER_AVERAGE", "USER_HISTORY", "INDUSTRY_STANDARD"

    // Category Context
    private String categoryCode;
    private String categoryName;

    // Flags
    private Boolean requiresAction;
    private Boolean isPositive; // true = good news, false = needs attention
    private Boolean isTimeSensitive;

    // Metadata
    private LocalDateTime generatedAt;
    private LocalDateTime expiresAt;
    private Integer confidenceScore; // 0-100

    // Helper Methods
    public boolean isHighPriority() {
        return "HIGH".equals(severity) || (requiresAction != null && requiresAction);
    }

    public boolean isOpportunity() {
        return "OPPORTUNITY".equals(insightType) && potentialSavings != null &&
                potentialSavings.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isAlert() {
        return "ALERT".equals(insightType);
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean hasHighConfidence() {
        return confidenceScore != null && confidenceScore > 80;
    }
}
