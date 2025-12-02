package com.waqiti.frauddetection.sanctions.fuzzy;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.language.Metaphone;
import org.apache.commons.codec.language.Soundex;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Fuzzy matching service for name matching in sanctions screening.
 *
 * Implements multiple algorithms:
 * - Levenshtein Distance: Character-level edit distance
 * - Soundex: Phonetic algorithm for English names
 * - Metaphone: Improved phonetic algorithm
 * - Jaro-Winkler: String similarity for short strings
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Slf4j
@Service
public class FuzzyMatchingService {

    private final LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
    private final Soundex soundex = new Soundex();
    private final Metaphone metaphone = new Metaphone();
    private final JaroWinklerDistance jaroWinkler = new JaroWinklerDistance();

    /**
     * Match two names using all available algorithms.
     *
     * @param name1 First name
     * @param name2 Second name
     * @param algorithms List of algorithms to use
     * @return List of match results, one per algorithm
     */
    public List<FuzzyMatchResult> matchName(String name1, String name2,
                                            List<FuzzyMatchingAlgorithm> algorithms) {
        if (name1 == null || name2 == null) {
            throw new IllegalArgumentException("Names cannot be null");
        }

        // Normalize names
        String normalizedName1 = normalizeName(name1);
        String normalizedName2 = normalizeName(name2);

        List<FuzzyMatchResult> results = new ArrayList<>();

        for (FuzzyMatchingAlgorithm algorithm : algorithms) {
            FuzzyMatchResult result = switch (algorithm) {
                case LEVENSHTEIN -> matchWithLevenshtein(normalizedName1, normalizedName2);
                case SOUNDEX -> matchWithSoundex(normalizedName1, normalizedName2);
                case METAPHONE -> matchWithMetaphone(normalizedName1, normalizedName2);
                case JARO_WINKLER -> matchWithJaroWinkler(normalizedName1, normalizedName2);
            };
            results.add(result);
        }

        return results;
    }

    /**
     * Normalize name for matching (uppercase, remove extra spaces).
     */
    private String normalizeName(String name) {
        return name.trim()
                   .toUpperCase()
                   .replaceAll("\\s+", " ")
                   .replaceAll("[^A-Z0-9\\s]", "");
    }

    /**
     * Levenshtein distance matching.
     *
     * Calculates minimum number of single-character edits needed to change
     * one word into another. Converts to confidence score (0-100).
     */
    private FuzzyMatchResult matchWithLevenshtein(String name1, String name2) {
        int distance = levenshteinDistance.apply(name1, name2);
        int maxLength = Math.max(name1.length(), name2.length());

        // Convert distance to confidence: 0 distance = 100% confidence
        BigDecimal confidence = maxLength > 0
            ? BigDecimal.valueOf(100.0 - (distance * 100.0 / maxLength))
                .setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Cap at 0-100 range
        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            confidence = BigDecimal.ZERO;
        }

        return FuzzyMatchResult.builder()
                .algorithm(FuzzyMatchingAlgorithm.LEVENSHTEIN)
                .confidence(confidence)
                .levenshteinDistance(distance)
                .matchedName1(name1)
                .matchedName2(name2)
                .build();
    }

    /**
     * Soundex phonetic matching.
     *
     * Encodes names to phonetic code, useful for names that sound alike
     * but are spelled differently.
     */
    private FuzzyMatchResult matchWithSoundex(String name1, String name2) {
        try {
            String code1 = soundex.encode(name1);
            String code2 = soundex.encode(name2);

            // Exact soundex match = high confidence (90%)
            // Different soundex = low confidence (based on code similarity)
            BigDecimal confidence;
            if (code1.equals(code2)) {
                confidence = new BigDecimal("90.00");
            } else {
                // Compare codes character by character
                int matches = 0;
                int length = Math.min(code1.length(), code2.length());
                for (int i = 0; i < length; i++) {
                    if (code1.charAt(i) == code2.charAt(i)) {
                        matches++;
                    }
                }
                confidence = BigDecimal.valueOf(matches * 100.0 / Math.max(code1.length(), code2.length()))
                    .setScale(2, RoundingMode.HALF_UP);
            }

            return FuzzyMatchResult.builder()
                    .algorithm(FuzzyMatchingAlgorithm.SOUNDEX)
                    .confidence(confidence)
                    .soundexCode(code1 + " vs " + code2)
                    .matchedName1(name1)
                    .matchedName2(name2)
                    .build();

        } catch (Exception e) {
            log.warn("Soundex encoding failed for names: {} and {}", name1, name2, e);
            return FuzzyMatchResult.builder()
                    .algorithm(FuzzyMatchingAlgorithm.SOUNDEX)
                    .confidence(BigDecimal.ZERO)
                    .matchedName1(name1)
                    .matchedName2(name2)
                    .build();
        }
    }

    /**
     * Metaphone phonetic matching.
     *
     * More accurate than Soundex for English names, handles variations better.
     */
    private FuzzyMatchResult matchWithMetaphone(String name1, String name2) {
        try {
            String code1 = metaphone.encode(name1);
            String code2 = metaphone.encode(name2);

            // Exact metaphone match = very high confidence (95%)
            BigDecimal confidence;
            if (code1.equals(code2)) {
                confidence = new BigDecimal("95.00");
            } else {
                // Use Levenshtein on metaphone codes
                int distance = levenshteinDistance.apply(code1, code2);
                int maxLength = Math.max(code1.length(), code2.length());
                confidence = maxLength > 0
                    ? BigDecimal.valueOf(100.0 - (distance * 100.0 / maxLength))
                        .setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            }

            return FuzzyMatchResult.builder()
                    .algorithm(FuzzyMatchingAlgorithm.METAPHONE)
                    .confidence(confidence)
                    .metaphoneCode(code1 + " vs " + code2)
                    .matchedName1(name1)
                    .matchedName2(name2)
                    .build();

        } catch (Exception e) {
            log.warn("Metaphone encoding failed for names: {} and {}", name1, name2, e);
            return FuzzyMatchResult.builder()
                    .algorithm(FuzzyMatchingAlgorithm.METAPHONE)
                    .confidence(BigDecimal.ZERO)
                    .matchedName1(name1)
                    .matchedName2(name2)
                    .build();
        }
    }

    /**
     * Jaro-Winkler distance matching.
     *
     * Gives higher scores to strings that match from the beginning,
     * useful for names with common prefixes.
     */
    private FuzzyMatchResult matchWithJaroWinkler(String name1, String name2) {
        double similarity = jaroWinkler.apply(name1, name2);

        // Convert 0-1 similarity to 0-100 confidence
        BigDecimal confidence = BigDecimal.valueOf(similarity * 100.0)
            .setScale(2, RoundingMode.HALF_UP);

        return FuzzyMatchResult.builder()
                .algorithm(FuzzyMatchingAlgorithm.JARO_WINKLER)
                .confidence(confidence)
                .matchedName1(name1)
                .matchedName2(name2)
                .build();
    }

    /**
     * Quick check if names are an exact match (case-insensitive).
     */
    public boolean isExactMatch(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return false;
        }
        return normalizeName(name1).equals(normalizeName(name2));
    }

    /**
     * Check if names are likely a match using combined algorithm threshold.
     *
     * @param name1 First name
     * @param name2 Second name
     * @param threshold Minimum confidence threshold (0-100)
     * @return True if any algorithm exceeds threshold
     */
    public boolean isLikelyMatch(String name1, String name2, BigDecimal threshold) {
        List<FuzzyMatchResult> results = matchName(
            name1,
            name2,
            List.of(FuzzyMatchingAlgorithm.values())
        );

        return results.stream()
            .anyMatch(r -> r.getConfidence().compareTo(threshold) >= 0);
    }

    /**
     * Get best match result from all algorithms.
     */
    public FuzzyMatchResult getBestMatch(String name1, String name2) {
        List<FuzzyMatchResult> results = matchName(
            name1,
            name2,
            List.of(FuzzyMatchingAlgorithm.values())
        );

        return results.stream()
            .max((r1, r2) -> r1.getConfidence().compareTo(r2.getConfidence()))
            .orElse(FuzzyMatchResult.builder()
                .algorithm(FuzzyMatchingAlgorithm.LEVENSHTEIN)
                .confidence(BigDecimal.ZERO)
                .matchedName1(name1)
                .matchedName2(name2)
                .build());
    }

    /**
     * Get weighted average confidence across all algorithms.
     *
     * Weights:
     * - Jaro-Winkler: 35% (best for names)
     * - Metaphone: 30% (good phonetic)
     * - Levenshtein: 25% (character accuracy)
     * - Soundex: 10% (basic phonetic)
     */
    public BigDecimal getWeightedConfidence(String name1, String name2) {
        List<FuzzyMatchResult> results = matchName(
            name1,
            name2,
            List.of(FuzzyMatchingAlgorithm.values())
        );

        BigDecimal weightedSum = BigDecimal.ZERO;

        for (FuzzyMatchResult result : results) {
            BigDecimal weight = switch (result.getAlgorithm()) {
                case JARO_WINKLER -> new BigDecimal("0.35");
                case METAPHONE -> new BigDecimal("0.30");
                case LEVENSHTEIN -> new BigDecimal("0.25");
                case SOUNDEX -> new BigDecimal("0.10");
            };

            weightedSum = weightedSum.add(result.getConfidence().multiply(weight));
        }

        return weightedSum.setScale(2, RoundingMode.HALF_UP);
    }
}
