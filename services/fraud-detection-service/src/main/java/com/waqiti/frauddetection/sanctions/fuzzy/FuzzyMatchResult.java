package com.waqiti.frauddetection.sanctions.fuzzy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Result of a fuzzy matching operation.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FuzzyMatchResult {

    /**
     * Algorithm used for matching
     */
    private FuzzyMatchingAlgorithm algorithm;

    /**
     * Confidence score (0-100)
     */
    private BigDecimal confidence;

    /**
     * Levenshtein distance (if applicable)
     */
    private Integer levenshteinDistance;

    /**
     * Soundex code (if applicable)
     */
    private String soundexCode;

    /**
     * Metaphone code (if applicable)
     */
    private String metaphoneCode;

    /**
     * First matched name
     */
    private String matchedName1;

    /**
     * Second matched name
     */
    private String matchedName2;

    /**
     * Additional match metadata
     */
    private java.util.Map<String, Object> metadata;
}
