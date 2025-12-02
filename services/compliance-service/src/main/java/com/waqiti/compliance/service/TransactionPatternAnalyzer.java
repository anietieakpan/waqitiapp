package com.waqiti.compliance.service;

import com.waqiti.compliance.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionPatternAnalyzer {
    
    private static final BigDecimal STRUCTURING_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal STRUCTURING_LOWER_BOUND = new BigDecimal("9000");
    private static final int RAPID_TRANSACTION_THRESHOLD_MINUTES = 10;
    private static final int HIGH_FREQUENCY_THRESHOLD = 10;
    
    /**
     * Analyzes transaction patterns for suspicious activity
     */
    public TransactionPatternAnalysis analyzePattern(List<TransactionSummary> transactions) {
        log.debug("Analyzing patterns for {} transactions", transactions.size());
        
        List<TransactionAnomaly> anomalies = new ArrayList<>();
        
        // Check for structuring patterns
        detectStructuringPatterns(transactions, anomalies);
        
        // Check for rapid succession transactions
        detectRapidSuccessionTransactions(transactions, anomalies);
        
        // Check for unusual time patterns
        detectUnusualTimePatterns(transactions, anomalies);
        
        // Check for round amount patterns
        detectRoundAmountPatterns(transactions, anomalies);
        
        // Check for velocity changes
        detectVelocityChanges(transactions, anomalies);
        
        return TransactionPatternAnalysis.builder()
            .anomalies(anomalies)
            .hasAnomalies(!anomalies.isEmpty())
            .analysisTimestamp(LocalDateTime.now())
            .transactionCount(transactions.size())
            .build();
    }
    
    /**
     * Analyzes patterns for specific compliance check request
     */
    public PatternAnalysisResult analyzeForSuspiciousPatterns(
            List<TransactionSummary> recentTransactions, 
            ComplianceCheckRequest currentRequest) {
        
        List<SuspiciousPattern> suspiciousPatterns = new ArrayList<>();
        
        // Add current transaction to the list for complete analysis
        TransactionSummary currentTransaction = TransactionSummary.builder()
            .transactionId(currentRequest.getTransactionId())
            .amount(currentRequest.getAmount())
            .currency(currentRequest.getCurrency())
            .timestamp(LocalDateTime.now())
            .fromAccountId(currentRequest.getFromAccountId())
            .toAccountId(currentRequest.getToAccountId())
            .transactionType(currentRequest.getTransactionType())
            .build();
        
        List<TransactionSummary> allTransactions = new ArrayList<>(recentTransactions);
        allTransactions.add(currentTransaction);
        
        // Sort by timestamp
        allTransactions.sort(Comparator.comparing(TransactionSummary::getTimestamp));
        
        // Check for structuring
        if (detectPotentialStructuring(allTransactions)) {
            suspiciousPatterns.add(SuspiciousPattern.builder()
                .patternType("STRUCTURING")
                .description("Multiple transactions just below reporting threshold")
                .confidenceScore(0.85)
                .build());
        }
        
        // Check for smurfing
        if (detectPotentialSmurfing(allTransactions)) {
            suspiciousPatterns.add(SuspiciousPattern.builder()
                .patternType("SMURFING")
                .description("Pattern consistent with smurfing activity")
                .confidenceScore(0.75)
                .build());
        }
        
        // Check for layering
        if (detectPotentialLayering(allTransactions)) {
            suspiciousPatterns.add(SuspiciousPattern.builder()
                .patternType("LAYERING")
                .description("Complex transaction patterns suggesting layering")
                .confidenceScore(0.70)
                .build());
        }
        
        return PatternAnalysisResult.builder()
            .suspiciousPatterns(suspiciousPatterns)
            .hasSuspiciousPatterns(!suspiciousPatterns.isEmpty())
            .analyzedAt(LocalDateTime.now())
            .build();
    }
    
    private void detectStructuringPatterns(List<TransactionSummary> transactions, 
                                          List<TransactionAnomaly> anomalies) {
        // Group transactions by day
        Map<LocalDateTime, List<TransactionSummary>> dailyTransactions = transactions.stream()
            .collect(Collectors.groupingBy(t -> t.getTimestamp().toLocalDate().atStartOfDay()));
        
        for (Map.Entry<LocalDateTime, List<TransactionSummary>> entry : dailyTransactions.entrySet()) {
            List<TransactionSummary> dayTransactions = entry.getValue();
            
            // Check for multiple transactions just below threshold
            long suspiciousCount = dayTransactions.stream()
                .filter(t -> t.getAmount().compareTo(STRUCTURING_LOWER_BOUND) >= 0 
                        && t.getAmount().compareTo(STRUCTURING_THRESHOLD) < 0)
                .count();
            
            if (suspiciousCount >= 3) {
                anomalies.add(TransactionAnomaly.builder()
                    .anomalyType("POTENTIAL_STRUCTURING")
                    .description("Multiple transactions just below $10,000 threshold on " + entry.getKey().toLocalDate())
                    .severity("HIGH")
                    .detectedAt(LocalDateTime.now())
                    .build());
            }
        }
    }
    
    private void detectRapidSuccessionTransactions(List<TransactionSummary> transactions,
                                                  List<TransactionAnomaly> anomalies) {
        if (transactions.size() < 2) return;
        
        for (int i = 1; i < transactions.size(); i++) {
            long minutesBetween = ChronoUnit.MINUTES.between(
                transactions.get(i-1).getTimestamp(),
                transactions.get(i).getTimestamp()
            );
            
            if (minutesBetween <= RAPID_TRANSACTION_THRESHOLD_MINUTES) {
                anomalies.add(TransactionAnomaly.builder()
                    .anomalyType("RAPID_SUCCESSION")
                    .description("Transactions occurring within " + minutesBetween + " minutes")
                    .severity("MEDIUM")
                    .detectedAt(LocalDateTime.now())
                    .build());
            }
        }
    }
    
    private void detectUnusualTimePatterns(List<TransactionSummary> transactions,
                                          List<TransactionAnomaly> anomalies) {
        // Check for transactions at unusual hours (e.g., 2-5 AM)
        long unusualHourCount = transactions.stream()
            .filter(t -> {
                int hour = t.getTimestamp().getHour();
                return hour >= 2 && hour <= 5;
            })
            .count();
        
        if (unusualHourCount > transactions.size() * 0.3) {
            anomalies.add(TransactionAnomaly.builder()
                .anomalyType("UNUSUAL_TIME_PATTERN")
                .description("High percentage of transactions during unusual hours")
                .severity("LOW")
                .detectedAt(LocalDateTime.now())
                .build());
        }
    }
    
    private void detectRoundAmountPatterns(List<TransactionSummary> transactions,
                                          List<TransactionAnomaly> anomalies) {
        long roundAmountCount = transactions.stream()
            .filter(t -> t.getAmount().remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) == 0)
            .count();
        
        if (roundAmountCount > transactions.size() * 0.5) {
            anomalies.add(TransactionAnomaly.builder()
                .anomalyType("ROUND_AMOUNT_PATTERN")
                .description("High percentage of round amount transactions")
                .severity("MEDIUM")
                .detectedAt(LocalDateTime.now())
                .build());
        }
    }
    
    private void detectVelocityChanges(List<TransactionSummary> transactions,
                                      List<TransactionAnomaly> anomalies) {
        if (transactions.size() < HIGH_FREQUENCY_THRESHOLD) return;
        
        // Calculate average time between transactions
        double avgMinutesBetween = 0;
        for (int i = 1; i < transactions.size(); i++) {
            avgMinutesBetween += ChronoUnit.MINUTES.between(
                transactions.get(i-1).getTimestamp(),
                transactions.get(i).getTimestamp()
            );
        }
        avgMinutesBetween /= (transactions.size() - 1);
        
        if (avgMinutesBetween < 60) { // Less than 1 hour average
            anomalies.add(TransactionAnomaly.builder()
                .anomalyType("HIGH_VELOCITY")
                .description("High transaction frequency detected")
                .severity("HIGH")
                .detectedAt(LocalDateTime.now())
                .build());
        }
    }
    
    private boolean detectPotentialStructuring(List<TransactionSummary> transactions) {
        // Count transactions just below reporting threshold
        return transactions.stream()
            .filter(t -> t.getAmount().compareTo(STRUCTURING_LOWER_BOUND) >= 0 
                    && t.getAmount().compareTo(STRUCTURING_THRESHOLD) < 0)
            .count() >= 3;
    }
    
    private boolean detectPotentialSmurfing(List<TransactionSummary> transactions) {
        // Check for multiple small transactions to different accounts
        Set<UUID> uniqueAccounts = transactions.stream()
            .map(TransactionSummary::getToAccountId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        return uniqueAccounts.size() > 5 && transactions.size() > 10;
    }
    
    private boolean detectPotentialLayering(List<TransactionSummary> transactions) {
        // Check for complex patterns suggesting layering
        // Simplified check: many transactions with varying amounts
        if (transactions.size() < 5) return false;
        
        Set<BigDecimal> uniqueAmounts = transactions.stream()
            .map(TransactionSummary::getAmount)
            .collect(Collectors.toSet());
        
        return uniqueAmounts.size() > transactions.size() * 0.8;
    }
}