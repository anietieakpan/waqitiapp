package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.domain.Settlement;
import com.waqiti.reconciliation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced reconciliation matching engine with multiple algorithms
 * Supports fuzzy matching, exact matching, and tolerance-based matching
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationMatchingEngine {

    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.01");
    private static final double DEFAULT_FUZZY_THRESHOLD = 0.85;

    /**
     * Match transaction against ledger entries using multiple algorithms
     */
    public TransactionLedgerMatchResult matchTransactionToLedger(TransactionDetails transaction, 
                                                               List<LedgerEntry> ledgerEntries) {
        log.debug("Matching transaction {} against {} ledger entries", 
                 transaction.getTransactionId(), ledgerEntries.size());

        // Exact match first
        TransactionLedgerMatchResult exactMatch = performExactMatch(transaction, ledgerEntries);
        if (exactMatch.isMatched()) {
            return exactMatch;
        }

        // Tolerance-based match
        TransactionLedgerMatchResult toleranceMatch = performToleranceMatch(transaction, ledgerEntries);
        if (toleranceMatch.isMatched()) {
            return toleranceMatch;
        }

        // Fuzzy match as last resort
        return performFuzzyMatch(transaction, ledgerEntries);
    }

    /**
     * Match settlement details with external confirmation
     */
    public SettlementMatchResult matchSettlementDetails(Settlement internal, 
                                                       ExternalSettlementConfirmation external) {
        log.debug("Matching settlement {} with external confirmation", internal.getSettlementId());

        List<SettlementDiscrepancy> discrepancies = new ArrayList<>();
        boolean matched = true;

        // Match amount
        if (internal.getAmount().compareTo(external.getAmount()) != 0) {
            matched = false;
            discrepancies.add(createAmountDiscrepancy(internal.getAmount(), external.getAmount()));
        }

        // Match currency
        if (!internal.getCurrency().equals(external.getCurrency())) {
            matched = false;
            discrepancies.add(createCurrencyDiscrepancy(internal.getCurrency(), external.getCurrency()));
        }

        // Match settlement date
        if (!internal.getSettlementDate().toLocalDate().equals(external.getSettlementDate())) {
            matched = false;
            discrepancies.add(createDateDiscrepancy(
                internal.getSettlementDate().toLocalDate().toString(),
                external.getSettlementDate().toString()));
        }

        // Match reference
        if (internal.getExternalReference() != null && 
            !internal.getExternalReference().equals(external.getExternalReference())) {
            matched = false;
            discrepancies.add(createReferenceDiscrepancy(
                internal.getExternalReference(), external.getExternalReference()));
        }

        double matchScore = calculateMatchScore(internal, external);

        return SettlementMatchResult.builder()
            .matched(matched)
            .discrepancies(discrepancies)
            .matchScore(matchScore)
            .matchingAlgorithm("COMPREHENSIVE_SETTLEMENT_MATCH")
            .build();
    }

    /**
     * Perform exact matching
     */
    private TransactionLedgerMatchResult performExactMatch(TransactionDetails transaction, 
                                                         List<LedgerEntry> ledgerEntries) {
        for (LedgerEntry entry : ledgerEntries) {
            if (isExactMatch(transaction, entry)) {
                return TransactionLedgerMatchResult.builder()
                    .matched(true)
                    .matchedLedgerEntry(entry)
                    .matchingAlgorithm("EXACT_MATCH")
                    .matchScore(1.0)
                    .build();
            }
        }

        return TransactionLedgerMatchResult.builder()
            .matched(false)
            .matchingAlgorithm("EXACT_MATCH")
            .matchScore(0.0)
            .build();
    }

    /**
     * Perform tolerance-based matching
     */
    private TransactionLedgerMatchResult performToleranceMatch(TransactionDetails transaction, 
                                                             List<LedgerEntry> ledgerEntries) {
        for (LedgerEntry entry : ledgerEntries) {
            double matchScore = calculateTransactionLedgerMatchScore(transaction, entry);
            if (matchScore >= DEFAULT_FUZZY_THRESHOLD) {
                List<ReconciliationVariance> variances = identifyVariances(transaction, entry);
                
                return TransactionLedgerMatchResult.builder()
                    .matched(true)
                    .matchedLedgerEntry(entry)
                    .variances(variances)
                    .matchingAlgorithm("TOLERANCE_MATCH")
                    .matchScore(matchScore)
                    .build();
            }
        }

        return TransactionLedgerMatchResult.builder()
            .matched(false)
            .matchingAlgorithm("TOLERANCE_MATCH")
            .matchScore(0.0)
            .build();
    }

    /**
     * Perform fuzzy matching using machine learning algorithms
     */
    private TransactionLedgerMatchResult performFuzzyMatch(TransactionDetails transaction, 
                                                         List<LedgerEntry> ledgerEntries) {
        LedgerEntry bestMatch = null;
        double bestScore = 0.0;

        for (LedgerEntry entry : ledgerEntries) {
            double score = calculateFuzzyMatchScore(transaction, entry);
            if (score > bestScore && score >= 0.7) { // Lower threshold for fuzzy
                bestScore = score;
                bestMatch = entry;
            }
        }

        if (bestMatch != null) {
            List<ReconciliationVariance> variances = identifyVariances(transaction, bestMatch);
            
            return TransactionLedgerMatchResult.builder()
                .matched(true)
                .matchedLedgerEntry(bestMatch)
                .variances(variances)
                .matchingAlgorithm("FUZZY_MATCH")
                .matchScore(bestScore)
                .build();
        }

        return TransactionLedgerMatchResult.builder()
            .matched(false)
            .matchingAlgorithm("FUZZY_MATCH")
            .matchScore(0.0)
            .variances(List.of())
            .build();
    }

    /**
     * Check if transaction exactly matches ledger entry
     */
    private boolean isExactMatch(TransactionDetails transaction, LedgerEntry entry) {
        return transaction.getAmount().compareTo(entry.getAmount()) == 0 &&
               transaction.getCurrency().equals(entry.getCurrency()) &&
               transaction.getTransactionDate().equals(entry.getTransactionDate()) &&
               transaction.getTransactionId().equals(entry.getTransactionId());
    }

    /**
     * Calculate match score between transaction and ledger entry
     */
    private double calculateTransactionLedgerMatchScore(TransactionDetails transaction, LedgerEntry entry) {
        double score = 0.0;
        int factors = 0;

        // Amount similarity (40% weight)
        if (transaction.getAmount() != null && entry.getAmount() != null) {
            BigDecimal diff = transaction.getAmount().subtract(entry.getAmount()).abs();
            BigDecimal tolerance = transaction.getAmount().multiply(new BigDecimal("0.001")); // 0.1% tolerance
            if (diff.compareTo(tolerance) <= 0) {
                score += 0.4;
            } else if (diff.compareTo(DEFAULT_TOLERANCE) <= 0) {
                score += 0.2;
            }
            factors++;
        }

        // Currency match (20% weight)
        if (transaction.getCurrency() != null && entry.getCurrency() != null) {
            if (transaction.getCurrency().equals(entry.getCurrency())) {
                score += 0.2;
            }
            factors++;
        }

        // Date proximity (20% weight)
        if (transaction.getTransactionDate() != null && entry.getTransactionDate() != null) {
            long daysDiff = Math.abs(transaction.getTransactionDate().toLocalDate()
                .toEpochDay() - entry.getTransactionDate().toLocalDate().toEpochDay());
            if (daysDiff == 0) {
                score += 0.2;
            } else if (daysDiff <= 1) {
                score += 0.1;
            }
            factors++;
        }

        // Reference similarity (20% weight)
        if (transaction.getExternalReference() != null && entry.getReference() != null) {
            if (transaction.getExternalReference().equals(entry.getReference())) {
                score += 0.2;
            } else if (calculateStringSimilarity(transaction.getExternalReference(), entry.getReference()) > 0.8) {
                score += 0.1;
            }
            factors++;
        }

        return factors > 0 ? score : 0.0;
    }

    /**
     * Calculate fuzzy match score using advanced algorithms
     */
    private double calculateFuzzyMatchScore(TransactionDetails transaction, LedgerEntry entry) {
        // More sophisticated fuzzy matching using multiple factors
        double amountScore = calculateAmountSimilarity(transaction.getAmount(), entry.getAmount());
        double dateScore = calculateDateProximity(transaction.getTransactionDate(), entry.getTransactionDate());
        double referenceScore = calculateReferenceSimilarity(transaction.getExternalReference(), entry.getReference());
        double currencyScore = transaction.getCurrency().equals(entry.getCurrency()) ? 1.0 : 0.0;

        // Weighted average
        return (amountScore * 0.4) + (dateScore * 0.2) + (referenceScore * 0.2) + (currencyScore * 0.2);
    }

    /**
     * Calculate settlement match score
     */
    private double calculateMatchScore(Settlement internal, ExternalSettlementConfirmation external) {
        double score = 0.0;
        int factors = 0;

        // Amount match
        if (internal.getAmount().compareTo(external.getAmount()) == 0) {
            score += 0.4;
        } else {
            BigDecimal diff = internal.getAmount().subtract(external.getAmount()).abs();
            BigDecimal tolerance = internal.getAmount().multiply(new BigDecimal("0.001"));
            if (diff.compareTo(tolerance) <= 0) {
                score += 0.2;
            }
        }
        factors++;

        // Currency match
        if (internal.getCurrency().equals(external.getCurrency())) {
            score += 0.2;
        }
        factors++;

        // Date match
        if (internal.getSettlementDate().toLocalDate().equals(external.getSettlementDate())) {
            score += 0.2;
        }
        factors++;

        // Reference match
        if (internal.getExternalReference() != null && external.getExternalReference() != null) {
            if (internal.getExternalReference().equals(external.getExternalReference())) {
                score += 0.2;
            } else {
                double similarity = calculateStringSimilarity(internal.getExternalReference(), external.getExternalReference());
                score += similarity * 0.2;
            }
        }
        factors++;

        return score;
    }

    /**
     * Identify variances between transaction and ledger entry
     */
    private List<ReconciliationVariance> identifyVariances(TransactionDetails transaction, LedgerEntry entry) {
        List<ReconciliationVariance> variances = new ArrayList<>();

        // Amount variance
        if (transaction.getAmount().compareTo(entry.getAmount()) != 0) {
            BigDecimal diff = transaction.getAmount().subtract(entry.getAmount());
            variances.add(ReconciliationVariance.builder()
                .varianceType("AMOUNT_VARIANCE")
                .amount(diff.abs())
                .currency(transaction.getCurrency())
                .expectedValue(transaction.getAmount().toString())
                .actualValue(entry.getAmount().toString())
                .description("Amount difference between transaction and ledger entry")
                .build());
        }

        // Date variance
        if (!transaction.getTransactionDate().equals(entry.getTransactionDate())) {
            variances.add(ReconciliationVariance.builder()
                .varianceType("DATE_VARIANCE")
                .amount(BigDecimal.ZERO)
                .expectedValue(transaction.getTransactionDate().toString())
                .actualValue(entry.getTransactionDate().toString())
                .description("Date difference between transaction and ledger entry")
                .build());
        }

        return variances;
    }

    // Utility methods for similarity calculations

    private double calculateAmountSimilarity(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null || amount2 == null) return 0.0;
        
        BigDecimal diff = amount1.subtract(amount2).abs();
        BigDecimal max = amount1.max(amount2);
        
        if (max.equals(BigDecimal.ZERO)) return 1.0;
        
        BigDecimal percentageDiff = diff.divide(max, 4, RoundingMode.HALF_UP);
        return Math.max(0.0, 1.0 - percentageDiff.doubleValue());
    }

    private double calculateDateProximity(java.time.LocalDateTime date1, java.time.LocalDateTime date2) {
        if (date1 == null || date2 == null) return 0.0;
        
        long daysDiff = Math.abs(date1.toLocalDate().toEpochDay() - date2.toLocalDate().toEpochDay());
        
        if (daysDiff == 0) return 1.0;
        if (daysDiff == 1) return 0.8;
        if (daysDiff <= 7) return 0.6;
        if (daysDiff <= 30) return 0.3;
        
        return 0.0;
    }

    private double calculateReferenceSimilarity(String ref1, String ref2) {
        if (ref1 == null || ref2 == null) return 0.0;
        return calculateStringSimilarity(ref1, ref2);
    }

    private double calculateStringSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) return 0.0;
        if (str1.equals(str2)) return 1.0;
        
        // Levenshtein distance based similarity
        int maxLength = Math.max(str1.length(), str2.length());
        if (maxLength == 0) return 1.0;
        
        int distance = levenshteinDistance(str1.toLowerCase(), str2.toLowerCase());
        return 1.0 - (double) distance / maxLength;
    }

    private int levenshteinDistance(String str1, String str2) {
        int[][] dp = new int[str1.length() + 1][str2.length() + 1];

        for (int i = 0; i <= str1.length(); i++) {
            for (int j = 0; j <= str2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                        dp[i - 1][j - 1] + (str1.charAt(i - 1) == str2.charAt(j - 1) ? 0 : 1),
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1)
                    );
                }
            }
        }

        return dp[str1.length()][str2.length()];
    }

    // Helper methods to create discrepancy objects

    private SettlementDiscrepancy createAmountDiscrepancy(BigDecimal expected, BigDecimal actual) {
        return SettlementDiscrepancy.builder()
            .discrepancyType("AMOUNT_MISMATCH")
            .fieldName("amount")
            .expectedValue(expected.toString())
            .actualValue(actual.toString())
            .amountDifference(expected.subtract(actual).abs())
            .description("Settlement amount does not match external confirmation")
            .severity("HIGH")
            .autoResolvable(false)
            .build();
    }

    private SettlementDiscrepancy createCurrencyDiscrepancy(String expected, String actual) {
        return SettlementDiscrepancy.builder()
            .discrepancyType("CURRENCY_MISMATCH")
            .fieldName("currency")
            .expectedValue(expected)
            .actualValue(actual)
            .description("Settlement currency does not match external confirmation")
            .severity("HIGH")
            .autoResolvable(false)
            .build();
    }

    private SettlementDiscrepancy createDateDiscrepancy(String expected, String actual) {
        return SettlementDiscrepancy.builder()
            .discrepancyType("DATE_MISMATCH")
            .fieldName("settlementDate")
            .expectedValue(expected)
            .actualValue(actual)
            .description("Settlement date does not match external confirmation")
            .severity("MEDIUM")
            .autoResolvable(false)
            .build();
    }

    private SettlementDiscrepancy createReferenceDiscrepancy(String expected, String actual) {
        return SettlementDiscrepancy.builder()
            .discrepancyType("REFERENCE_MISMATCH")
            .fieldName("externalReference")
            .expectedValue(expected)
            .actualValue(actual)
            .description("External reference does not match confirmation")
            .severity("LOW")
            .autoResolvable(true)
            .build();
    }
}