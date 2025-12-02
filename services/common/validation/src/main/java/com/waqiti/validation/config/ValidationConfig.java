package com.waqiti.validation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for financial validation rules
 * 
 * Centralizes all validation limits and rules for consistent
 * application across all financial services
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "waqiti.validation")
@Validated
public class ValidationConfig {
    
    /**
     * Currency-specific configuration
     */
    private Map<String, CurrencyConfig> currencies = new HashMap<>();
    
    /**
     * Transaction type limits
     */
    private Map<String, TransactionLimits> transactionLimits = new HashMap<>();
    
    /**
     * User tier limits
     */
    private Map<String, UserTierLimits> userTierLimits = new HashMap<>();
    
    /**
     * Fraud detection thresholds
     */
    private FraudDetectionConfig fraudDetection = new FraudDetectionConfig();
    
    /**
     * General validation settings
     */
    private GeneralConfig general = new GeneralConfig();
    
    @Data
    public static class CurrencyConfig {
        @NotNull
        private String code;
        
        @NotNull
        private String name;
        
        @Positive
        private int scale = 2;
        
        @NotNull
        private BigDecimal minAmount = BigDecimal.valueOf(0.01);
        
        @NotNull
        private BigDecimal maxAmount = BigDecimal.valueOf(1_000_000);
        
        private boolean supported = true;
        private boolean active = true;
        private boolean crypto;
        private boolean stablecoin;
        
        private List<String> allowedRegions;
        private List<String> restrictedRegions;
        private List<String> allowedTransactionTypes;
    }
    
    @Data
    public static class TransactionLimits {
        @NotNull
        private String transactionType;
        
        @NotNull
        private BigDecimal minAmount = BigDecimal.valueOf(0.01);
        
        @NotNull
        private BigDecimal maxAmount = BigDecimal.valueOf(1_000_000);
        
        @NotNull
        private BigDecimal dailyLimit = BigDecimal.valueOf(10_000);
        
        @NotNull
        private BigDecimal monthlyLimit = BigDecimal.valueOf(100_000);
        
        private boolean requiresKyc;
        private boolean requiresTwoFactor;
        private boolean highValueThreshold;
        
        private List<String> allowedCurrencies;
        private List<String> allowedPaymentMethods;
    }
    
    @Data
    public static class UserTierLimits {
        @NotNull
        private String tierName;
        
        @NotNull
        private BigDecimal dailyLimit;
        
        @NotNull
        private BigDecimal monthlyLimit;
        
        @NotNull
        private BigDecimal yearlyLimit;
        
        @NotNull
        private BigDecimal perTransactionLimit;
        
        private Map<String, BigDecimal> currencySpecificLimits = new HashMap<>();
        private Map<String, BigDecimal> transactionTypeLimit = new HashMap<>();
        
        private boolean requiresKyc = true;
        private boolean requiresEnhancedDueDiligence;
        private int maxTransactionsPerDay = 100;
        private int maxTransactionsPerMonth = 1000;
    }
    
    @Data
    public static class FraudDetectionConfig {
        @NotNull
        private BigDecimal suspiciousAmountThreshold = BigDecimal.valueOf(10_000);
        
        @NotNull
        private BigDecimal highRiskAmountThreshold = BigDecimal.valueOf(50_000);
        
        @NotNull
        private BigDecimal velocityCheckThreshold = BigDecimal.valueOf(25_000);
        
        private int maxTransactionsPerHour = 10;
        private int maxTransactionsPerDay = 100;
        
        private boolean enableRealTimeScreening = true;
        private boolean enableVelocityChecks = true;
        private boolean enableAmountPatternAnalysis = true;
        
        private List<String> highRiskCountries;
        private List<String> blockedCountries;
    }
    
    @Data
    public static class GeneralConfig {
        private boolean enableStrictValidation = true;
        private boolean enableCrossCurrencyValidation = true;
        private boolean enableBusinessRuleValidation = true;
        
        private int maxDescriptionLength = 500;
        private int maxReferenceLength = 100;
        
        private boolean logValidationFailures = true;
        private boolean logSuspiciousActivity = true;
        
        @NotNull
        private BigDecimal absoluteMaxAmount = BigDecimal.valueOf(999_999_999_999L);
        
        @NotNull
        private BigDecimal absoluteMinAmount = BigDecimal.valueOf(0.00000001);
        
        private int maxPrecision = 20;
        private int defaultScale = 2;
        private int cryptoScale = 8;
    }
    
    /**
     * Get currency configuration by code
     */
    public CurrencyConfig getCurrencyConfig(String currencyCode) {
        return currencies.get(currencyCode.toUpperCase());
    }
    
    /**
     * Get transaction limits by type
     */
    public TransactionLimits getTransactionLimits(String transactionType) {
        return transactionLimits.get(transactionType.toUpperCase());
    }
    
    /**
     * Get user tier limits by tier name
     */
    public UserTierLimits getUserTierLimits(String tierName) {
        return userTierLimits.get(tierName.toUpperCase());
    }
    
    /**
     * Check if currency is supported
     */
    public boolean isCurrencySupported(String currencyCode) {
        CurrencyConfig config = getCurrencyConfig(currencyCode);
        return config != null && config.isSupported() && config.isActive();
    }
    
    /**
     * Check if currency is cryptocurrency
     */
    public boolean isCryptocurrency(String currencyCode) {
        CurrencyConfig config = getCurrencyConfig(currencyCode);
        return config != null && config.isCrypto();
    }
    
    /**
     * Check if currency is stablecoin
     */
    public boolean isStablecoin(String currencyCode) {
        CurrencyConfig config = getCurrencyConfig(currencyCode);
        return config != null && config.isStablecoin();
    }
    
    /**
     * Get scale for currency
     */
    public int getScaleForCurrency(String currencyCode) {
        CurrencyConfig config = getCurrencyConfig(currencyCode);
        if (config != null) {
            return config.getScale();
        }
        return general.getDefaultScale();
    }
    
    /**
     * Get minimum amount for currency
     */
    public BigDecimal getMinAmountForCurrency(String currencyCode) {
        CurrencyConfig config = getCurrencyConfig(currencyCode);
        if (config != null) {
            return config.getMinAmount();
        }
        return general.getAbsoluteMinAmount();
    }
    
    /**
     * Get maximum amount for currency
     */
    public BigDecimal getMaxAmountForCurrency(String currencyCode) {
        CurrencyConfig config = getCurrencyConfig(currencyCode);
        if (config != null) {
            return config.getMaxAmount();
        }
        return general.getAbsoluteMaxAmount();
    }
    
    /**
     * Check if amount exceeds fraud detection threshold
     */
    public boolean isSuspiciousAmount(BigDecimal amount) {
        return amount != null && 
               amount.compareTo(fraudDetection.getSuspiciousAmountThreshold()) > 0;
    }
    
    /**
     * Check if amount is high risk
     */
    public boolean isHighRiskAmount(BigDecimal amount) {
        return amount != null && 
               amount.compareTo(fraudDetection.getHighRiskAmountThreshold()) > 0;
    }
}