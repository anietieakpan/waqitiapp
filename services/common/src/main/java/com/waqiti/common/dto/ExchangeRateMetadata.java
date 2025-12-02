package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Exchange rate metadata for tracking and analytics
 * Contains supplementary information about exchange rate operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateMetadata {
    
    private String id;
    private String pairKey;
    private String baseCurrency;
    private String targetCurrency;
    private String provider;
    private String source;
    private LocalDateTime lastUpdated;
    private LocalDateTime nextUpdate;
    private Integer updateCount;
    
    // Provider information
    private String providerStatus;
    private String providerUrl;
    private Integer providerResponseTimeMs;
    private Boolean providerAvailable;
    private String providerErrorMessage;
    
    // Update frequency
    private Long updateFrequencySeconds;
    private Integer failedAttempts;
    private LocalDateTime lastSuccessfulUpdate;
    private LocalDateTime lastFailedUpdate;
    
    // Quality metrics
    private Double accuracy;
    private Double reliability;
    private Integer dataPoints;
    private String qualityGrade; // A, B, C, D, F
    
    // Historical tracking
    private List<HistoricalDataPoint> recentRates;
    private Map<String, Object> statistics;
    
    // Compliance and regulations
    private Boolean isRegulated;
    private String regulatoryBody;
    private String complianceStatus;
    private List<String> restrictions;
    
    // Caching information
    private Long cacheTtlSeconds;
    private Boolean cacheEnabled;
    private String cacheKey;
    private LocalDateTime cacheExpiry;
    
    // Alert thresholds
    private Double volatilityThreshold;
    private Double changeThreshold;
    private Boolean alertsEnabled;
    private List<String> alertChannels;
    
    // Additional metadata
    private Map<String, String> tags;
    private String notes;
    private String version;
    
    /**
     * Historical data point for rate tracking
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalDataPoint {
        private LocalDateTime timestamp;
        private Double rate;
        private Double volume;
        private String source;
    }
    
    /**
     * Check if metadata needs refresh
     */
    public boolean needsRefresh() {
        if (nextUpdate == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(nextUpdate);
    }
    
    /**
     * Check if provider is healthy
     */
    public boolean isProviderHealthy() {
        return Boolean.TRUE.equals(providerAvailable) && 
               (failedAttempts == null || failedAttempts < 3);
    }
    
    /**
     * Get cache remaining time in seconds
     */
    public Long getCacheRemainingSeconds() {
        if (cacheExpiry == null) {
            return 0L;
        }
        long remaining = java.time.Duration.between(LocalDateTime.now(), cacheExpiry).getSeconds();
        return remaining > 0 ? remaining : 0L;
    }
    
    /**
     * Check if high quality data
     */
    public boolean isHighQuality() {
        return "A".equals(qualityGrade) || "B".equals(qualityGrade);
    }
}