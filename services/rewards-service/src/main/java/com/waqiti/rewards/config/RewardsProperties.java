package com.waqiti.rewards.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Configuration properties for the Rewards Service
 */
@Data
@ConfigurationProperties(prefix = "rewards")
public class RewardsProperties {

    /**
     * Default cashback rate (as decimal, e.g., 0.01 = 1%)
     */
    private BigDecimal defaultCashbackRate = BigDecimal.valueOf(0.01);

    /**
     * Points earned per dollar spent
     */
    private int pointsPerDollar = 100;

    /**
     * Maximum daily cashback amount per user
     */
    private BigDecimal maxDailyCashback = BigDecimal.valueOf(50.00);

    /**
     * Maximum weekly cashback amount per user
     */
    private BigDecimal maxWeeklyCashback = BigDecimal.valueOf(200.00);

    /**
     * Maximum monthly cashback amount per user
     */
    private BigDecimal maxMonthlyCashback = BigDecimal.valueOf(500.00);

    /**
     * Number of days to refresh tier progress
     */
    private int tierRefreshDays = 90;

    /**
     * Welcome bonus amount for new users
     */
    private BigDecimal welcomeBonus = BigDecimal.valueOf(10.00);

    /**
     * Minimum cashback amount for redemption
     */
    private BigDecimal minCashbackRedemption = BigDecimal.valueOf(5.00);

    /**
     * Minimum points for redemption
     */
    private long minPointsRedemption = 500L;

    /**
     * Points to cashback conversion rate (points per dollar)
     */
    private long pointsToCashbackRate = 100L;

    /**
     * Number of days before points expire
     */
    private int pointsExpirationDays = 365;

    /**
     * Cashback processing delay in minutes
     */
    private int cashbackProcessingDelayMinutes = 15;

    /**
     * Enhanced category rates by merchant category code
     */
    private Map<String, BigDecimal> categoryRates = Map.of(
        "5411", BigDecimal.valueOf(0.02), // Grocery stores
        "5541", BigDecimal.valueOf(0.015), // Gas stations
        "5812", BigDecimal.valueOf(0.02), // Restaurants
        "5311", BigDecimal.valueOf(0.015), // Department stores
        "4511", BigDecimal.valueOf(0.02), // Airlines
        "3000", BigDecimal.valueOf(0.025) // Travel
    );

    /**
     * Tier multipliers for cashback
     */
    private Map<String, BigDecimal> tierMultipliers = Map.of(
        "BRONZE", BigDecimal.valueOf(1.0),
        "SILVER", BigDecimal.valueOf(1.25),
        "GOLD", BigDecimal.valueOf(1.5),
        "PLATINUM", BigDecimal.valueOf(2.0)
    );

    /**
     * Tier upgrade bonuses
     */
    private Map<String, BigDecimal> tierUpgradeBonuses = Map.of(
        "SILVER", BigDecimal.valueOf(5.00),
        "GOLD", BigDecimal.valueOf(15.00),
        "PLATINUM", BigDecimal.valueOf(50.00)
    );

    /**
     * Tier spending thresholds
     */
    private Map<String, BigDecimal> tierThresholds = Map.of(
        "BRONZE", BigDecimal.valueOf(1000),
        "SILVER", BigDecimal.valueOf(5000),
        "GOLD", BigDecimal.valueOf(15000),
        "PLATINUM", BigDecimal.valueOf(50000)
    );

    /**
     * Auto-redemption settings
     */
    private AutoRedemption autoRedemption = new AutoRedemption();

    /**
     * Campaign settings
     */
    private Campaign campaign = new Campaign();

    @Data
    public static class AutoRedemption {
        private boolean enabled = false;
        private BigDecimal threshold = BigDecimal.valueOf(25.00);
        private int scheduleHour = 2; // 2 AM
    }

    @Data
    public static class Campaign {
        private boolean enabled = true;
        private int maxActiveCampaigns = 50;
        private int defaultDurationDays = 30;
    }
}