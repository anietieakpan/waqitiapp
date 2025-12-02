package com.waqiti.security.behavioral;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Behavioral Analysis Engine
 * 
 * Analyzes user behavioral patterns for fraud detection and risk assessment.
 * Provides cryptocurrency-specific behavioral analysis including transaction patterns,
 * timing analysis, and deviation detection.
 * 
 * @author Waqiti Security Team
 * @version 1.0 - Production Stub Implementation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BehavioralAnalysisEngine {

    /**
     * Get crypto behavior profile for user
     */
    public CryptoBehaviorProfile getCryptoBehaviorProfile(UUID userId) {
        log.debug("Getting crypto behavior profile for user: {}", userId);
        
        try {
            // In production, this would query historical crypto transaction data
            // For now, return simulated profile based on user hash
            
            int hash = Math.abs(userId.hashCode());
            boolean isFrequentTrader = (hash % 10) < 3; // 30% are frequent traders
            boolean hasRapidTransactions = (hash % 20) < 2; // 10% have rapid transaction patterns
            
            BigDecimal avgAmount = BigDecimal.valueOf(500 + (hash % 5000));
            
            return CryptoBehaviorProfile.builder()
                .userId(userId)
                .averageTransactionAmount(avgAmount)
                .transactionCountLast24Hours(hash % 20)
                .transactionCountLast7Days(hash % 100)
                .hasRapidSuccessiveTransactions(hasRapidTransactions)
                .preferredCryptocurrencies(getPreferredCurrencies(hash))
                .averageHourOfDay(9 + (hash % 12)) // 9am - 9pm
                .typicalTransactionSize(avgAmount)
                .riskScore(calculateBehavioralRiskScore(isFrequentTrader, hasRapidTransactions))
                .profileCreatedDate(LocalDateTime.now().minusDays(hash % 365))
                .lastTransactionDate(LocalDateTime.now().minusHours(hash % 48))
                .build();
                
        } catch (Exception e) {
            log.error("Error getting crypto behavior profile for user: {}", userId, e);
            // Return safe default profile
            return CryptoBehaviorProfile.builder()
                .userId(userId)
                .averageTransactionAmount(BigDecimal.valueOf(100))
                .transactionCountLast24Hours(0)
                .transactionCountLast7Days(0)
                .hasRapidSuccessiveTransactions(false)
                .preferredCryptocurrencies(List.of("BTC"))
                .averageHourOfDay(12)
                .typicalTransactionSize(BigDecimal.valueOf(100))
                .riskScore(0.2)
                .profileCreatedDate(LocalDateTime.now())
                .lastTransactionDate(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Check if user has used specific currency before
     */
    public boolean hasUsedCurrency(UUID userId, String currency) {
        try {
            CryptoBehaviorProfile profile = getCryptoBehaviorProfile(userId);
            return profile.getPreferredCryptocurrencies().contains(currency);
        } catch (Exception e) {
            log.error("Error checking currency usage for user: {}", userId, e);
            return false; // Assume new currency for safety
        }
    }
    
    /**
     * Analyze behavioral risk score
     */
    public double analyzeBehavioralRisk(UUID userId, BigDecimal currentAmount, String currency) {
        try {
            log.debug("Analyzing behavioral risk for user: {}, amount: {}, currency: {}", 
                userId, currentAmount, currency);
            
            CryptoBehaviorProfile profile = getCryptoBehaviorProfile(userId);
            
            double riskScore = 0.0;
            
            // Check amount deviation
            if (currentAmount.compareTo(profile.getAverageTransactionAmount().multiply(BigDecimal.valueOf(5))) > 0) {
                riskScore += 0.3; // 5x normal amount
            } else if (currentAmount.compareTo(profile.getAverageTransactionAmount().multiply(BigDecimal.valueOf(3))) > 0) {
                riskScore += 0.2; // 3x normal amount
            }
            
            // Check new currency usage
            if (!profile.getPreferredCryptocurrencies().contains(currency)) {
                riskScore += 0.2;
            }
            
            // Check rapid transaction pattern
            if (profile.isHasRapidSuccessiveTransactions()) {
                riskScore += 0.25;
            }
            
            // Check high frequency
            if (profile.getTransactionCountLast24Hours() > 20) {
                riskScore += 0.3;
            } else if (profile.getTransactionCountLast24Hours() > 10) {
                riskScore += 0.15;
            }
            
            return Math.min(riskScore, 1.0);
            
        } catch (Exception e) {
            log.error("Error analyzing behavioral risk for user: {}", userId, e);
            return 0.3; // Default moderate risk
        }
    }
    
    /**
     * Get general behavioral profile (non-crypto)
     */
    public BehavioralProfile getBehavioralProfile(UUID userId) {
        log.debug("Getting general behavioral profile for user: {}", userId);
        
        try {
            int hash = Math.abs(userId.hashCode());
            
            return BehavioralProfile.builder()
                .userId(userId)
                .averageTransactionAmount(BigDecimal.valueOf(150 + (hash % 500)))
                .transactionFrequency(1.5 + ((hash % 100) / 100.0))
                .preferredTransactionTimes(List.of(9, 12, 14, 18, 20))
                .preferredMerchantCategories(List.of("FOOD", "TRANSPORT", "RETAIL"))
                .riskScore(0.1 + ((hash % 30) / 100.0))
                .accountAge(LocalDateTime.now().minusDays(hash % 730))
                .lastActivityDate(LocalDateTime.now().minusHours(hash % 72))
                .build();
                
        } catch (Exception e) {
            log.error("Error getting behavioral profile for user: {}", userId, e);
            return BehavioralProfile.builder()
                .userId(userId)
                .averageTransactionAmount(BigDecimal.valueOf(100))
                .transactionFrequency(1.0)
                .preferredTransactionTimes(List.of(12))
                .preferredMerchantCategories(List.of())
                .riskScore(0.2)
                .accountAge(LocalDateTime.now())
                .lastActivityDate(LocalDateTime.now())
                .build();
        }
    }
    
    private List<String> getPreferredCurrencies(int hash) {
        List<String> allCurrencies = List.of("BTC", "ETH", "USDT", "USDC", "XRP", "ADA", "SOL", "DOT", "MATIC", "AVAX");
        List<String> preferred = new ArrayList<>();
        
        // Select 1-3 preferred currencies based on hash
        int count = 1 + (hash % 3);
        for (int i = 0; i < count && i < allCurrencies.size(); i++) {
            preferred.add(allCurrencies.get((hash + i) % allCurrencies.size()));
        }
        
        return preferred;
    }
    
    private double calculateBehavioralRiskScore(boolean isFrequentTrader, boolean hasRapidTransactions) {
        double riskScore = 0.1; // Base risk
        
        if (isFrequentTrader) {
            riskScore += 0.1; // Slightly higher risk for frequent traders
        }
        
        if (hasRapidTransactions) {
            riskScore += 0.2; // Higher risk for rapid succession
        }
        
        return Math.min(riskScore, 1.0);
    }
    
    /**
     * Crypto Behavior Profile
     */
    @Data
    @Builder
    public static class CryptoBehaviorProfile {
        private UUID userId;
        private BigDecimal averageTransactionAmount;
        private int transactionCountLast24Hours;
        private int transactionCountLast7Days;
        private boolean hasRapidSuccessiveTransactions;
        private List<String> preferredCryptocurrencies;
        private int averageHourOfDay;
        private BigDecimal typicalTransactionSize;
        private double riskScore;
        private LocalDateTime profileCreatedDate;
        private LocalDateTime lastTransactionDate;
        
        public boolean isNewCurrency(String currency) {
            return !preferredCryptocurrencies.contains(currency);
        }
    }
    
    /**
     * General Behavioral Profile
     */
    @Data
    @Builder
    public static class BehavioralProfile {
        private UUID userId;
        private BigDecimal averageTransactionAmount;
        private double transactionFrequency;
        private List<Integer> preferredTransactionTimes;
        private List<String> preferredMerchantCategories;
        private double riskScore;
        private LocalDateTime accountAge;
        private LocalDateTime lastActivityDate;
    }
}