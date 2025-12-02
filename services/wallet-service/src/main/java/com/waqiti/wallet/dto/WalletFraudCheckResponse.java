package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Wallet Fraud Check Response DTO
 *
 * Response from fraud detection service for wallet transfers
 *
 * Decision values:
 * - ALLOW: Transfer approved, proceed with transaction
 * - BLOCK: Transfer blocked due to fraud, do not proceed
 * - REVIEW: Transfer requires manual review before proceeding
 *
 * Risk levels:
 * - LOW: 0.0 - 0.3 fraud score
 * - MEDIUM: 0.3 - 0.6 fraud score
 * - HIGH: 0.6 - 0.85 fraud score
 * - CRITICAL: 0.85 - 1.0 fraud score
 *
 * @author Waqiti Security Team - P0 Production Fix
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletFraudCheckResponse {

    /**
     * Fraud detection decision (ALLOW, BLOCK, REVIEW)
     */
    private String decision;

    /**
     * Risk level assessment (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String riskLevel;

    /**
     * ML fraud score from 0.0 (no fraud) to 1.0 (definite fraud)
     */
    private Double fraudScore;

    /**
     * Whether the transaction should be blocked
     */
    private Boolean blocked;

    /**
     * Whether the transaction requires manual review
     */
    private Boolean requiresReview;

    /**
     * Human-readable explanation of the decision
     */
    private String reason;

    /**
     * Specific fraud indicators detected (velocity, pattern anomalies, etc.)
     */
    private Map<String, Object> fraudIndicators;

    /**
     * Timestamp of fraud check
     */
    private LocalDateTime timestamp;

    /**
     * Whether fallback logic was used (fraud service unavailable)
     */
    private Boolean fallbackApplied;

    /**
     * ML model version used for fraud detection
     */
    private String modelVersion;

    /**
     * Confidence level of fraud detection (0.0 - 1.0)
     */
    private Double confidence;
}
