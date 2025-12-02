package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Inter-company transaction matching result for reconciliation
 * Used in ledger reconciliation to match transactions between company entities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyMatch {

    /**
     * Unique identifier for this match
     */
    private String matchId;

    /**
     * Source company entity ID
     */
    private String sourceCompanyId;

    /**
     * Destination company entity ID
     */
    private String destinationCompanyId;

    /**
     * Source transaction ID
     */
    private String sourceTransactionId;

    /**
     * Destination transaction ID
     */
    private String destinationTransactionId;

    /**
     * Matched amount (should be equal on both sides)
     */
    private BigDecimal matchedAmount;

    /**
     * Currency code (ISO 4217)
     */
    private String currencyCode;

    /**
     * Match status
     */
    private MatchStatus status;

    /**
     * Confidence score (0-100)
     */
    private Integer confidenceScore;

    /**
     * Amount difference (if any discrepancy exists)
     */
    private BigDecimal amountDifference;

    /**
     * Tolerance threshold used for matching
     */
    private BigDecimal toleranceThreshold;

    /**
     * Timestamp when match was identified
     */
    private LocalDateTime matchedAt;

    /**
     * User or system that created the match
     */
    private String matchedBy;

    /**
     * Additional matching criteria met
     */
    private List<String> matchingCriteria;

    /**
     * Notes or comments about the match
     */
    private String notes;

    /**
     * Whether this match requires manual review
     */
    private boolean requiresReview;

    /**
     * Reviewer user ID (if reviewed)
     */
    private String reviewedBy;

    /**
     * Review timestamp
     */
    private LocalDateTime reviewedAt;

    /**
     * Review decision
     */
    private ReviewDecision reviewDecision;

    /**
     * Match status enumeration
     */
    public enum MatchStatus {
        MATCHED,           // Perfect match
        PARTIAL_MATCH,     // Close match within tolerance
        MISMATCH,          // No match
        PENDING_REVIEW,    // Awaiting manual review
        APPROVED,          // Manually approved
        REJECTED           // Manually rejected
    }

    /**
     * Review decision enumeration
     */
    public enum ReviewDecision {
        APPROVE,
        REJECT,
        ESCALATE
    }

    /**
     * Check if this is a perfect match (zero difference)
     */
    public boolean isPerfectMatch() {
        return amountDifference != null &&
               amountDifference.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Check if match is within tolerance
     */
    public boolean isWithinTolerance() {
        if (amountDifference == null || toleranceThreshold == null) {
            return false;
        }
        return amountDifference.abs().compareTo(toleranceThreshold) <= 0;
    }
}
