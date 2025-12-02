package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Exchange rate data transfer object
 * Contains comprehensive exchange rate information for currency conversions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateData {

    private String id;
    private String baseCurrency;
    private String targetCurrency;
    private String fromCurrency;  // Alias for baseCurrency
    private String toCurrency;    // Alias for targetCurrency
    private BigDecimal rate;
    private BigDecimal inverseRate;
    private BigDecimal buyRate;
    private BigDecimal sellRate;
    private BigDecimal midRate;
    private String provider;
    private LocalDateTime timestamp;
    private Instant timestampInstant; // Instant version for compatibility
    private LocalDateTime expiresAt;
    private boolean isStale;
    private String source;  // Source system/provider
    private Double confidence;  // Confidence score 0.0-1.0
    
    // Market data
    private BigDecimal dailyChangePercent;
    private BigDecimal weeklyChangePercent;
    private BigDecimal monthlyChangePercent;
    private BigDecimal dailyHigh;
    private BigDecimal dailyLow;
    private BigDecimal volume;
    
    // Spreads and fees
    private BigDecimal spread;
    private BigDecimal spreadPercent;
    private BigDecimal conversionFee;
    private BigDecimal minimumAmount;
    private BigDecimal maximumAmount;
    
    // Additional metadata
    private Map<String, Object> metadata;
    private String rateType; // SPOT, FORWARD, etc.
    private boolean isActive;
    private String region;
    private String marketStatus; // OPEN, CLOSED, HOLIDAY
    
    /**
     * Calculate the converted amount
     */
    public BigDecimal convert(BigDecimal amount) {
        if (amount == null || rate == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(rate);
    }
    
    /**
     * Calculate the converted amount with fees
     */
    public BigDecimal convertWithFees(BigDecimal amount) {
        BigDecimal converted = convert(amount);
        if (conversionFee != null) {
            converted = converted.subtract(conversionFee);
        }
        return converted;
    }
    
    /**
     * Check if the rate is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Get the effective rate (considering buy/sell/mid)
     */
    public BigDecimal getEffectiveRate(String transactionType) {
        if ("BUY".equalsIgnoreCase(transactionType) && buyRate != null) {
            return buyRate;
        } else if ("SELL".equalsIgnoreCase(transactionType) && sellRate != null) {
            return sellRate;
        }
        return midRate != null ? midRate : rate;
    }

    /**
     * Get fromCurrency (maps to baseCurrency)
     */
    public String getFromCurrency() {
        return fromCurrency != null ? fromCurrency : baseCurrency;
    }

    /**
     * Get toCurrency (maps to targetCurrency)
     */
    public String getToCurrency() {
        return toCurrency != null ? toCurrency : targetCurrency;
    }

    /**
     * Get timestamp as Instant
     */
    public Instant getTimestamp() {
        if (timestampInstant != null) {
            return timestampInstant;
        }
        if (timestamp != null) {
            return timestamp.atZone(java.time.ZoneId.systemDefault()).toInstant();
        }
        return null;
    }

    /**
     * Get baseCurrency (maps to fromCurrency)
     */
    public String getBaseCurrency() {
        return baseCurrency != null ? baseCurrency : fromCurrency;
    }

    /**
     * Get targetCurrency (maps to toCurrency)
     */
    public String getTargetCurrency() {
        return targetCurrency != null ? targetCurrency : toCurrency;
    }
}