package com.waqiti.frauddetection.sanctions.fuzzy;

/**
 * Fuzzy matching algorithms for name matching.
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
public enum FuzzyMatchingAlgorithm {
    /**
     * Levenshtein distance - character-level edit distance
     */
    LEVENSHTEIN,

    /**
     * Soundex - phonetic algorithm for English names
     */
    SOUNDEX,

    /**
     * Metaphone - improved phonetic algorithm
     */
    METAPHONE,

    /**
     * Jaro-Winkler distance - similarity for short strings
     */
    JARO_WINKLER
}
