package com.waqiti.payment.fraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fraud Validation Result
 *
 * Result of fraud detection validation including risk assessment
 * and recommended actions.
 *
 * @author Waqiti Fraud Detection Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudValidationResult {

    /**
     * Whether transaction is approved
     * false = blocked, true = approved
     */
    private boolean approved;

    /**
     * Fraud risk score (0.0 to 1.0)
     */
    private Double riskScore;

    /**
     * Risk level categorization
     */
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    /**
     * Human-readable reason for decision
     */
    private String reason;

    /**
     * List of fraud rules that were triggered
     */
    private List<String> triggeredRules;

    /**
     * Whether transaction requires manual review
     */
    private boolean requiresManualReview;

    /**
     * Priority for manual review (0=Critical, 1=High, 2=Medium, 3=Low)
     */
    private Integer reviewPriority;

    /**
     * Transaction amount (for review context)
     */
    private BigDecimal transactionAmount;

    /**
     * Transaction currency
     */
    private String transactionCurrency;

    /**
     * Additional context for fraud review
     */
    private java.util.Map<String, Object> additionalContext;

    /**
     * ML model confidence in prediction
     */
    private Double confidence;

    /**
     * ML model version used
     */
    private String modelVersion;

    /**
     * Time taken for fraud check (milliseconds)
     */
    private Long fraudCheckDurationMs;
}
