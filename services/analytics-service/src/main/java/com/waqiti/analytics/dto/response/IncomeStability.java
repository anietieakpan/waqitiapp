package com.waqiti.analytics.dto.response;

import lombok.Getter;

/**
 * Income Stability Enum
 *
 * Categorizes income stability based on variance, consistency, and predictability.
 * Used for credit risk assessment and financial health scoring.
 *
 * Stability Metrics:
 * - STABLE: Low variance, consistent amount, predictable timing (CV < 15%)
 * - UNSTABLE: High variance, inconsistent amount, unpredictable timing (CV > 40%)
 * - IMPROVING: Variance decreasing, amounts increasing, regularity improving
 * - DECLINING: Variance increasing, amounts decreasing, regularity worsening
 *
 * Coefficient of Variation (CV) = (Standard Deviation / Mean) × 100%
 *
 * Business Impact:
 * - STABLE income → Higher credit score, better loan terms
 * - UNSTABLE income → Risk mitigation required, lower credit limits
 * - IMPROVING income → Positive trend, monitor for stability
 * - DECLINING income → Warning flag, financial stress indicator
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Getter
public enum IncomeStability {

    /**
     * Income is stable and predictable
     * Criteria: CV < 15%, regular frequency, minimal gaps
     */
    STABLE("Stable",
           "Income shows consistent amounts and timing with low variance",
           85),

    /**
     * Income is unstable and unpredictable
     * Criteria: CV > 40%, irregular frequency, significant gaps
     */
    UNSTABLE("Unstable",
             "Income shows high variance and unpredictable patterns",
             30),

    /**
     * Income stability is improving over time
     * Criteria: Decreasing CV, increasing amounts, improving frequency
     */
    IMPROVING("Improving",
              "Income variance is decreasing and amounts are increasing",
              65),

    /**
     * Income stability is declining over time
     * Criteria: Increasing CV, decreasing amounts, worsening frequency
     */
    DECLINING("Declining",
              "Income variance is increasing or amounts are decreasing",
              45);

    /**
     * Display name for UI
     */
    private final String displayName;

    /**
     * Detailed description
     */
    private final String description;

    /**
     * Credit score impact (0-100, higher = better)
     */
    private final int creditScoreImpact;

    IncomeStability(String displayName, String description, int creditScoreImpact) {
        this.displayName = displayName;
        this.description = description;
        this.creditScoreImpact = creditScoreImpact;
    }

    /**
     * Determines income stability from coefficient of variation
     *
     * @param coefficientOfVariation CV percentage (0-100+)
     * @param growthRate Income growth rate percentage
     * @return Appropriate stability category
     */
    public static IncomeStability fromMetrics(double coefficientOfVariation, double growthRate) {
        if (coefficientOfVariation < 15) {
            return STABLE;
        } else if (coefficientOfVariation > 40) {
            return growthRate > 0 ? IMPROVING : UNSTABLE;
        } else {
            return growthRate < -10 ? DECLINING :
                   growthRate > 10 ? IMPROVING : STABLE;
        }
    }

    /**
     * Check if this stability level is acceptable for credit
     */
    public boolean isAcceptableForCredit() {
        return this == STABLE || this == IMPROVING;
    }
}
