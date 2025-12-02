package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for cash deposit system metrics and statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDepositSystemMetrics {
    
    // Overall system metrics
    private Long totalDeposits;
    private BigDecimal totalDepositAmount;
    private Long activeReferences;
    private Long expiredReferences;
    private Long completedDeposits;
    private Long failedDeposits;
    private BigDecimal averageDepositAmount;
    
    // Provider-specific metrics
    private Map<String, ProviderMetrics> providerMetrics;
    
    // Time-based metrics
    private Long depositsToday;
    private Long depositsThisWeek;
    private Long depositsThisMonth;
    private BigDecimal amountToday;
    private BigDecimal amountThisWeek;
    private BigDecimal amountThisMonth;
    
    // Performance metrics
    private Double averageProcessingTimeMinutes;
    private Double successRate;
    private Double failureRate;
    private Long peakHourDeposits;
    
    // Location metrics
    private Map<String, Long> depositsByState;
    private Map<String, Long> depositsByCity;
    private String topDepositLocation;
    private Long uniqueLocationsUsed;
    
    // User metrics
    private Long uniqueUsers;
    private Long repeatUsers;
    private BigDecimal averageDepositsPerUser;
    
    // System health
    private String systemStatus;
    private LocalDateTime lastUpdateTime;
    private Long activeSessions;
    private Map<String, Boolean> providerAvailability;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderMetrics {
        private String providerName;
        private Long totalDeposits;
        private BigDecimal totalAmount;
        private Double successRate;
        private Double averageProcessingTime;
        private Long activeLocations;
        private Boolean isAvailable;
        private LocalDateTime lastSuccessfulDeposit;
    }
}