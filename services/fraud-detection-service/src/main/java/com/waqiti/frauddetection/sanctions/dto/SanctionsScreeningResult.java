package com.waqiti.frauddetection.sanctions.dto;

import com.waqiti.frauddetection.sanctions.entity.SanctionsCheckRecord.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for sanctions screening results.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanctionsScreeningResult {

    /**
     * Check record ID
     */
    private UUID checkId;

    /**
     * Whether any match was found
     */
    private Boolean matchFound;

    /**
     * Number of matches found
     */
    private Integer matchCount;

    /**
     * Overall match score (0-100)
     */
    private BigDecimal matchScore;

    /**
     * Risk level assessment
     */
    private RiskLevel riskLevel;

    /**
     * Check status
     */
    private CheckStatus checkStatus;

    /**
     * Resolution decision
     */
    private Resolution resolution;

    /**
     * Detailed match information
     */
    private List<MatchDetail> matchDetails;

    /**
     * Check duration in milliseconds
     */
    private Long checkDurationMs;

    /**
     * Requires manual review
     */
    private Boolean requiresManualReview;

    /**
     * Entity is blocked
     */
    private Boolean blocked;

    /**
     * Entity is cleared
     */
    private Boolean cleared;

    /**
     * Sanctions list version used
     */
    private String sanctionsListVersion;

    /**
     * Matched list names
     */
    private List<String> matchedLists;

    /**
     * Next review date (if applicable)
     */
    private java.time.LocalDate nextReviewDate;
}
