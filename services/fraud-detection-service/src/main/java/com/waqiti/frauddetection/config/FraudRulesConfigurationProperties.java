package com.waqiti.frauddetection.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Externalized configuration for fraud detection rules.
 *
 * All threshold values should be configured via application.yml or environment variables.
 * This ensures sensitive detection parameters are not hardcoded in source code.
 *
 * IMPORTANT: These are example default values for development/demonstration only.
 * Production deployments MUST override these values via external configuration.
 *
 * Configuration can be provided via:
 * - application-production.yml (recommended)
 * - Environment variables (e.g., FRAUD_DETECTION_THRESHOLDS_HIGH_VALUE_AMOUNT)
 * - HashiCorp Vault
 * - Spring Cloud Config Server
 *
 * @author Waqiti Platform Team
 */
@Configuration
@ConfigurationProperties(prefix = "fraud-detection")
@Data
@Validated
public class FraudRulesConfigurationProperties {

    /**
     * Transaction amount thresholds for various fraud detection rules.
     * Configure these values based on your business requirements and regulatory obligations.
     */
    private Thresholds thresholds = new Thresholds();

    /**
     * Velocity-based detection settings.
     * Controls rate limiting and transaction frequency checks.
     */
    private Velocity velocity = new Velocity();

    /**
     * Account-related risk parameters.
     */
    private Account account = new Account();

    /**
     * Device risk parameters.
     */
    private Device device = new Device();

    /**
     * Behavioral analysis parameters.
     */
    private Behavioral behavioral = new Behavioral();

    /**
     * ML model configuration.
     */
    private MachineLearning ml = new MachineLearning();

    @Data
    public static class Thresholds {
        /**
         * Amount threshold for high-value transaction alerts.
         * Transactions above this amount receive additional scrutiny.
         * Default is a placeholder - MUST be configured for production.
         */
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal highValueAmount = new BigDecimal("10000.00");

        /**
         * Amount threshold for new device + high value combination.
         * When a transaction is from a new device AND exceeds this amount.
         */
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal newDeviceHighValueAmount = new BigDecimal("2000.00");

        /**
         * Upper bound for structuring detection.
         * Transactions just below reporting thresholds.
         */
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal structuringUpperBound = new BigDecimal("9500.00");

        /**
         * Lower bound for structuring detection.
         */
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal structuringLowerBound = new BigDecimal("8000.00");

        /**
         * Threshold for dormant account reactivation with significant amount.
         */
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal dormantAccountAmount = new BigDecimal("1000.00");

        /**
         * Threshold for crypto transaction high value alert.
         */
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal cryptoHighValueAmount = new BigDecimal("3000.00");

        /**
         * Threshold for new account high value transaction.
         */
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal newAccountHighValueAmount = new BigDecimal("5000.00");

        /**
         * Round amount threshold for pattern detection.
         */
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal roundAmountThreshold = new BigDecimal("1000.00");
    }

    @Data
    public static class Velocity {
        /**
         * Maximum transactions allowed per hour before velocity alert.
         */
        @Min(1)
        private int maxTransactionsPerHour = 10;

        /**
         * Maximum unique recipients per day before alert.
         */
        @Min(1)
        private int maxUniqueRecipientsPerDay = 5;

        /**
         * Spike multiplier - triggers when transaction exceeds average by this factor.
         */
        @DecimalMin("1.0")
        private BigDecimal spikeMultiplier = new BigDecimal("5.0");

        /**
         * Number of recent transactions to consider for structuring.
         */
        @Min(1)
        private int structuringTransactionCount = 3;

        /**
         * Maximum failed authentication attempts before alert.
         */
        @Min(1)
        private int maxFailedAttempts = 3;
    }

    @Data
    public static class Account {
        /**
         * Days of inactivity to consider account dormant.
         */
        @Min(1)
        private int dormantPeriodDays = 90;

        /**
         * Minimum account age (days) for reduced scrutiny.
         */
        @Min(1)
        private int minAccountAgeDays = 30;
    }

    @Data
    public static class Device {
        /**
         * Device risk score threshold for suspicious device alert.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private BigDecimal suspiciousDeviceScoreThreshold = new BigDecimal("0.7");

        /**
         * Recipient risk score threshold.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private BigDecimal suspiciousRecipientScoreThreshold = new BigDecimal("0.7");
    }

    @Data
    public static class Behavioral {
        /**
         * Behavioral anomaly score threshold.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private BigDecimal anomalyScoreThreshold = new BigDecimal("0.75");

        /**
         * Hours considered unusual for transactions (start hour, 24h format).
         * Transactions outside normal business hours may receive extra scrutiny.
         */
        @Min(0)
        private int unusualHourStart = 23;

        /**
         * Hours considered unusual for transactions (end hour, 24h format).
         */
        @Min(0)
        private int unusualHourEnd = 6;
    }

    @Data
    public static class MachineLearning {
        /**
         * ML model high risk score threshold.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private BigDecimal highRiskThreshold = new BigDecimal("0.8");

        /**
         * ML model medium risk score threshold.
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private BigDecimal mediumRiskThreshold = new BigDecimal("0.5");

        /**
         * Feature analysis window in hours.
         */
        @Min(1)
        private int featureWindowHours = 168; // 7 days
    }
}
