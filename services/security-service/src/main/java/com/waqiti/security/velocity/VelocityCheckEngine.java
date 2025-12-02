package com.waqiti.security.velocity;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Velocity Check Engine
 * 
 * Monitors and enforces transaction velocity limits for fraud prevention.
 * Tracks transaction frequency and amounts across multiple time windows.
 * Provides cryptocurrency-specific velocity checking.
 * 
 * @author Waqiti Security Team
 * @version 1.0 - Production Stub Implementation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VelocityCheckEngine {

    // Velocity limits (in production, these would be configurable per user tier)
    private static final int HOURLY_TRANSACTION_LIMIT = 10;
    private static final int DAILY_TRANSACTION_LIMIT = 50;
    private static final BigDecimal HOURLY_AMOUNT_LIMIT = BigDecimal.valueOf(10000);
    private static final BigDecimal DAILY_AMOUNT_LIMIT = BigDecimal.valueOf(50000);
    
    // Crypto-specific limits (more restrictive for high-risk cryptocurrencies)
    private static final int CRYPTO_HOURLY_LIMIT = 5;
    private static final int CRYPTO_DAILY_LIMIT = 20;
    private static final BigDecimal CRYPTO_HOURLY_AMOUNT_LIMIT = BigDecimal.valueOf(5000);
    private static final BigDecimal CRYPTO_DAILY_AMOUNT_LIMIT = BigDecimal.valueOf(25000);

    /**
     * Perform crypto velocity check
     */
    public VelocityCheckResult performCryptoVelocityCheck(
            UUID userId, 
            BigDecimal amount, 
            String currency, 
            TimeWindow timeWindow) {
        
        log.debug("Performing crypto velocity check for user: {}, amount: {}, currency: {}, window: {}", 
            userId, amount, currency, timeWindow);
        
        try {
            // Get user's recent crypto transaction velocity
            CryptoVelocityData velocityData = getCryptoVelocityData(userId, currency);
            
            boolean hourlyLimitExceeded = false;
            boolean dailyLimitExceeded = false;
            boolean transactionCountExceeded = false;
            double velocitySpike = 1.0;
            
            // Check hourly limits
            if (velocityData.getHourlyTransactionCount() >= CRYPTO_HOURLY_LIMIT) {
                hourlyLimitExceeded = true;
                transactionCountExceeded = true;
            }
            
            BigDecimal newHourlyAmount = velocityData.getHourlyAmount().add(amount);
            if (newHourlyAmount.compareTo(CRYPTO_HOURLY_AMOUNT_LIMIT) > 0) {
                hourlyLimitExceeded = true;
            }
            
            // Check daily limits
            if (velocityData.getDailyTransactionCount() >= CRYPTO_DAILY_LIMIT) {
                dailyLimitExceeded = true;
                transactionCountExceeded = true;
            }
            
            BigDecimal newDailyAmount = velocityData.getDailyAmount().add(amount);
            if (newDailyAmount.compareTo(CRYPTO_DAILY_AMOUNT_LIMIT) > 0) {
                dailyLimitExceeded = true;
            }
            
            // Calculate velocity spike
            if (velocityData.getHistoricalHourlyAverage() > 0) {
                velocitySpike = velocityData.getHourlyTransactionCount() / 
                              (double) velocityData.getHistoricalHourlyAverage();
            }
            
            // Calculate risk score
            double riskScore = calculateVelocityRiskScore(
                hourlyLimitExceeded, 
                dailyLimitExceeded, 
                transactionCountExceeded, 
                velocitySpike
            );
            
            return VelocityCheckResult.builder()
                .userId(userId)
                .currency(currency)
                .checkTimestamp(LocalDateTime.now())
                .hourlyLimitExceeded(hourlyLimitExceeded)
                .dailyLimitExceeded(dailyLimitExceeded)
                .transactionCountExceeded(transactionCountExceeded)
                .velocitySpike(velocitySpike)
                .currentHourlyCount(velocityData.getHourlyTransactionCount())
                .currentDailyCount(velocityData.getDailyTransactionCount())
                .currentHourlyAmount(velocityData.getHourlyAmount())
                .currentDailyAmount(velocityData.getDailyAmount())
                .hourlyLimit(CRYPTO_HOURLY_LIMIT)
                .dailyLimit(CRYPTO_DAILY_LIMIT)
                .hourlyAmountLimit(CRYPTO_HOURLY_AMOUNT_LIMIT)
                .dailyAmountLimit(CRYPTO_DAILY_AMOUNT_LIMIT)
                .riskScore(riskScore)
                .recommended(determineRecommendedAction(riskScore))
                .build();
                
        } catch (Exception e) {
            log.error("Error performing crypto velocity check for user: {}", userId, e);
            // Return fail-secure result
            return VelocityCheckResult.builder()
                .userId(userId)
                .currency(currency)
                .checkTimestamp(LocalDateTime.now())
                .hourlyLimitExceeded(true)
                .dailyLimitExceeded(false)
                .transactionCountExceeded(true)
                .velocitySpike(1.0)
                .riskScore(0.5)
                .recommended("REVIEW")
                .build();
        }
    }
    
    /**
     * Perform general velocity check (non-crypto)
     */
    public VelocityCheckResult performVelocityCheck(UUID userId, BigDecimal amount, TimeWindow timeWindow) {
        log.debug("Performing velocity check for user: {}, amount: {}, window: {}", userId, amount, timeWindow);
        
        try {
            VelocityData velocityData = getVelocityData(userId);
            
            boolean hourlyLimitExceeded = velocityData.getHourlyTransactionCount() >= HOURLY_TRANSACTION_LIMIT;
            boolean dailyLimitExceeded = velocityData.getDailyTransactionCount() >= DAILY_TRANSACTION_LIMIT;
            boolean transactionCountExceeded = hourlyLimitExceeded || dailyLimitExceeded;
            
            BigDecimal newHourlyAmount = velocityData.getHourlyAmount().add(amount);
            if (newHourlyAmount.compareTo(HOURLY_AMOUNT_LIMIT) > 0) {
                hourlyLimitExceeded = true;
            }
            
            BigDecimal newDailyAmount = velocityData.getDailyAmount().add(amount);
            if (newDailyAmount.compareTo(DAILY_AMOUNT_LIMIT) > 0) {
                dailyLimitExceeded = true;
            }
            
            double velocitySpike = velocityData.getHistoricalHourlyAverage() > 0 
                ? velocityData.getHourlyTransactionCount() / (double) velocityData.getHistoricalHourlyAverage()
                : 1.0;
            
            double riskScore = calculateVelocityRiskScore(
                hourlyLimitExceeded, 
                dailyLimitExceeded, 
                transactionCountExceeded, 
                velocitySpike
            );
            
            return VelocityCheckResult.builder()
                .userId(userId)
                .checkTimestamp(LocalDateTime.now())
                .hourlyLimitExceeded(hourlyLimitExceeded)
                .dailyLimitExceeded(dailyLimitExceeded)
                .transactionCountExceeded(transactionCountExceeded)
                .velocitySpike(velocitySpike)
                .currentHourlyCount(velocityData.getHourlyTransactionCount())
                .currentDailyCount(velocityData.getDailyTransactionCount())
                .currentHourlyAmount(velocityData.getHourlyAmount())
                .currentDailyAmount(velocityData.getDailyAmount())
                .hourlyLimit(HOURLY_TRANSACTION_LIMIT)
                .dailyLimit(DAILY_TRANSACTION_LIMIT)
                .hourlyAmountLimit(HOURLY_AMOUNT_LIMIT)
                .dailyAmountLimit(DAILY_AMOUNT_LIMIT)
                .riskScore(riskScore)
                .recommended(determineRecommendedAction(riskScore))
                .build();
                
        } catch (Exception e) {
            log.error("Error performing velocity check for user: {}", userId, e);
            return VelocityCheckResult.builder()
                .userId(userId)
                .checkTimestamp(LocalDateTime.now())
                .hourlyLimitExceeded(true)
                .riskScore(0.5)
                .recommended("REVIEW")
                .build();
        }
    }
    
    /**
     * Check if user exceeds velocity limits
     */
    public boolean exceedsVelocityLimits(UUID userId, BigDecimal amount) {
        try {
            VelocityCheckResult result = performVelocityCheck(userId, amount, TimeWindow.HOURLY);
            return result.isHourlyLimitExceeded() || result.isDailyLimitExceeded();
        } catch (Exception e) {
            log.error("Error checking velocity limits for user: {}", userId, e);
            return true; // Fail-secure: assume limit exceeded
        }
    }
    
    private CryptoVelocityData getCryptoVelocityData(UUID userId, String currency) {
        // In production, this would query the transaction database
        // For now, simulate based on user hash
        int hash = Math.abs(userId.hashCode());
        
        int hourlyCount = hash % 5;
        int dailyCount = hash % 15;
        BigDecimal hourlyAmount = BigDecimal.valueOf((hash % 3000) + 100);
        BigDecimal dailyAmount = BigDecimal.valueOf((hash % 15000) + 500);
        int historicalAverage = Math.max(1, hash % 3);
        
        return CryptoVelocityData.builder()
            .userId(userId)
            .currency(currency)
            .hourlyTransactionCount(hourlyCount)
            .dailyTransactionCount(dailyCount)
            .hourlyAmount(hourlyAmount)
            .dailyAmount(dailyAmount)
            .historicalHourlyAverage(historicalAverage)
            .historicalDailyAverage(historicalAverage * 8)
            .lastTransactionTime(LocalDateTime.now().minusMinutes(hash % 60))
            .build();
    }
    
    private VelocityData getVelocityData(UUID userId) {
        // Simulate velocity data based on user hash
        int hash = Math.abs(userId.hashCode());
        
        int hourlyCount = hash % 8;
        int dailyCount = hash % 30;
        BigDecimal hourlyAmount = BigDecimal.valueOf((hash % 5000) + 200);
        BigDecimal dailyAmount = BigDecimal.valueOf((hash % 25000) + 1000);
        int historicalAverage = Math.max(1, hash % 5);
        
        return VelocityData.builder()
            .userId(userId)
            .hourlyTransactionCount(hourlyCount)
            .dailyTransactionCount(dailyCount)
            .hourlyAmount(hourlyAmount)
            .dailyAmount(dailyAmount)
            .historicalHourlyAverage(historicalAverage)
            .historicalDailyAverage(historicalAverage * 10)
            .lastTransactionTime(LocalDateTime.now().minusMinutes(hash % 120))
            .build();
    }
    
    private double calculateVelocityRiskScore(
            boolean hourlyExceeded, 
            boolean dailyExceeded, 
            boolean countExceeded, 
            double spike) {
        
        double riskScore = 0.0;
        
        if (hourlyExceeded) riskScore += 0.4;
        if (dailyExceeded) riskScore += 0.3;
        if (countExceeded) riskScore += 0.2;
        
        // Add risk based on velocity spike
        if (spike > 10.0) {
            riskScore += 0.5;
        } else if (spike > 5.0) {
            riskScore += 0.3;
        } else if (spike > 3.0) {
            riskScore += 0.2;
        }
        
        return Math.min(riskScore, 1.0);
    }
    
    private String determineRecommendedAction(double riskScore) {
        if (riskScore >= 0.8) return "BLOCK";
        if (riskScore >= 0.6) return "REVIEW";
        if (riskScore >= 0.4) return "WARN";
        return "ALLOW";
    }
    
    /**
     * Velocity Check Result
     */
    @Data
    @Builder
    public static class VelocityCheckResult {
        private UUID userId;
        private String currency;
        private LocalDateTime checkTimestamp;
        private boolean hourlyLimitExceeded;
        private boolean dailyLimitExceeded;
        private boolean transactionCountExceeded;
        private double velocitySpike;
        private int currentHourlyCount;
        private int currentDailyCount;
        private BigDecimal currentHourlyAmount;
        private BigDecimal currentDailyAmount;
        private int hourlyLimit;
        private int dailyLimit;
        private BigDecimal hourlyAmountLimit;
        private BigDecimal dailyAmountLimit;
        private double riskScore;
        private String recommended;
    }
    
    /**
     * Crypto Velocity Data
     */
    @Data
    @Builder
    private static class CryptoVelocityData {
        private UUID userId;
        private String currency;
        private int hourlyTransactionCount;
        private int dailyTransactionCount;
        private BigDecimal hourlyAmount;
        private BigDecimal dailyAmount;
        private int historicalHourlyAverage;
        private int historicalDailyAverage;
        private LocalDateTime lastTransactionTime;
    }
    
    /**
     * General Velocity Data
     */
    @Data
    @Builder
    private static class VelocityData {
        private UUID userId;
        private int hourlyTransactionCount;
        private int dailyTransactionCount;
        private BigDecimal hourlyAmount;
        private BigDecimal dailyAmount;
        private int historicalHourlyAverage;
        private int historicalDailyAverage;
        private LocalDateTime lastTransactionTime;
    }
    
    /**
     * Time Window enum
     */
    public enum TimeWindow {
        HOURLY,
        DAILY,
        WEEKLY,
        MONTHLY
    }
}