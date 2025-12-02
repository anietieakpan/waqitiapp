package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Comprehensive request DTO for check fraud detection and analysis.
 * Contains all necessary information for fraud scoring and risk assessment.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckFraudRequest {

    /**
     * Unique deposit ID for this fraud analysis.
     */
    @NotNull(message = "Deposit ID cannot be null")
    @JsonProperty("deposit_id")
    private UUID depositId;

    /**
     * User ID making the deposit.
     */
    @NotNull(message = "User ID cannot be null")
    @JsonProperty("user_id")
    private UUID userId;

    /**
     * Check amount for analysis.
     */
    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    /**
     * Currency of the check.
     */
    @Builder.Default
    private String currency = "USD";

    /**
     * Front image of the check for analysis.
     */
    @NotBlank(message = "Front check image is required")
    @JsonProperty("check_image_front")
    private String checkImageFront;

    /**
     * Back image of the check for analysis.
     */
    @NotBlank(message = "Back check image is required")
    @JsonProperty("check_image_back")
    private String checkImageBack;

    /**
     * MICR routing number extracted from check.
     */
    @JsonProperty("micr_routing_number")
    private String micrRoutingNumber;

    /**
     * MICR account number extracted from check.
     */
    @JsonProperty("micr_account_number")
    private String micrAccountNumber;

    /**
     * Check number from MICR line.
     */
    @JsonProperty("check_number")
    private String checkNumber;

    /**
     * Payor/drawer information.
     */
    @JsonProperty("payor_info")
    private PayorInfo payorInfo;

    /**
     * Device information for fraud analysis.
     */
    @Valid
    @JsonProperty("device_info")
    private DeviceInfo deviceInfo;

    /**
     * Location information.
     */
    @Valid
    @JsonProperty("location_info")
    private LocationInfo locationInfo;

    /**
     * User's deposit history for pattern analysis.
     */
    @Valid
    @JsonProperty("user_deposit_history")
    private UserDepositHistory userDepositHistory;

    /**
     * Account behavior analysis.
     */
    @Valid
    @JsonProperty("account_behavior")
    private AccountBehavior accountBehavior;

    /**
     * Transaction context information.
     */
    @Valid
    @JsonProperty("transaction_context")
    private TransactionContext transactionContext;

    /**
     * Request timestamp.
     */
    @JsonProperty("request_timestamp")
    private LocalDateTime requestTimestamp;

    /**
     * Payor/drawer information for fraud analysis.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayorInfo {
        
        @JsonProperty("payor_name")
        private String payorName;
        
        @JsonProperty("payor_address")
        private String payorAddress;
        
        @JsonProperty("bank_name")
        private String bankName;
        
        @JsonProperty("account_type")
        private String accountType;
        
        /**
         * Whether this payor has been seen before.
         */
        @JsonProperty("known_payor")
        private Boolean knownPayor;
        
        /**
         * Risk score for this payor (0-100).
         */
        @JsonProperty("payor_risk_score")
        private BigDecimal payorRiskScore;
    }

    /**
     * Device information for fraud detection.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        
        @NotBlank(message = "Device ID is required")
        @JsonProperty("device_id")
        private String deviceId;
        
        @JsonProperty("device_fingerprint")
        private String deviceFingerprint;
        
        @JsonProperty("device_type")
        private String deviceType;
        
        @JsonProperty("operating_system")
        private String operatingSystem;
        
        @JsonProperty("app_version")
        private String appVersion;
        
        @JsonProperty("ip_address")
        private String ipAddress;
        
        @JsonProperty("user_agent")
        private String userAgent;
        
        /**
         * Whether this device has been used for fraud before.
         */
        @JsonProperty("device_risk_flag")
        private Boolean deviceRiskFlag;
        
        /**
         * Number of deposits from this device in last 24 hours.
         */
        @JsonProperty("device_deposit_count_24h")
        private Integer deviceDepositCount24h;
        
        /**
         * Whether device location matches typical user patterns.
         */
        @JsonProperty("location_anomaly")
        private Boolean locationAnomaly;
    }

    /**
     * Location information for fraud analysis.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        
        @DecimalMin(value = "-90.0", message = "Invalid latitude")
        @DecimalMax(value = "90.0", message = "Invalid latitude")
        private BigDecimal latitude;
        
        @DecimalMin(value = "-180.0", message = "Invalid longitude")
        @DecimalMax(value = "180.0", message = "Invalid longitude")
        private BigDecimal longitude;
        
        /**
         * Location accuracy in meters.
         */
        private BigDecimal accuracy;
        
        @JsonProperty("city")
        private String city;
        
        @JsonProperty("state")
        private String state;
        
        @JsonProperty("country")
        private String country;
        
        /**
         * Distance from user's typical locations (in miles).
         */
        @JsonProperty("distance_from_home")
        private BigDecimal distanceFromHome;
        
        /**
         * Whether this is a high-risk location.
         */
        @JsonProperty("high_risk_location")
        private Boolean highRiskLocation;
        
        /**
         * Location risk score (0-100).
         */
        @JsonProperty("location_risk_score")
        private BigDecimal locationRiskScore;
    }

    /**
     * User's deposit history for pattern analysis.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDepositHistory {
        
        /**
         * Total number of successful deposits.
         */
        @JsonProperty("total_deposits")
        private Integer totalDeposits;
        
        /**
         * Number of deposits in last 30 days.
         */
        @JsonProperty("deposits_last_30_days")
        private Integer depositsLast30Days;
        
        /**
         * Average deposit amount.
         */
        @JsonProperty("average_deposit_amount")
        private BigDecimal averageDepositAmount;
        
        /**
         * Largest previous deposit amount.
         */
        @JsonProperty("max_deposit_amount")
        private BigDecimal maxDepositAmount;
        
        /**
         * Number of failed deposits.
         */
        @JsonProperty("failed_deposits")
        private Integer failedDeposits;
        
        /**
         * Number of chargebacks or returned checks.
         */
        @JsonProperty("chargeback_count")
        private Integer chargebackCount;
        
        /**
         * Time since last deposit (in hours).
         */
        @JsonProperty("hours_since_last_deposit")
        private Integer hoursSinceLastDeposit;
        
        /**
         * User's deposit velocity score.
         */
        @JsonProperty("velocity_score")
        private BigDecimal velocityScore;
    }

    /**
     * Account behavior analysis.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBehavior {
        
        /**
         * Account age in days.
         */
        @JsonProperty("account_age_days")
        private Integer accountAgeDays;
        
        /**
         * User's KYC verification status.
         */
        @JsonProperty("kyc_status")
        private String kycStatus;
        
        /**
         * User's risk tier/level.
         */
        @JsonProperty("user_risk_tier")
        private String userRiskTier;
        
        /**
         * Number of suspicious activities flagged.
         */
        @JsonProperty("suspicious_activity_count")
        private Integer suspiciousActivityCount;
        
        /**
         * Whether user has been flagged for fraud before.
         */
        @JsonProperty("previous_fraud_flag")
        private Boolean previousFraudFlag;
        
        /**
         * User's overall trust score (0-100).
         */
        @JsonProperty("trust_score")
        private BigDecimal trustScore;
        
        /**
         * Typical deposit patterns for this user.
         */
        @JsonProperty("typical_patterns")
        private java.util.Map<String, Object> typicalPatterns;
    }

    /**
     * Transaction context information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionContext {
        
        /**
         * Time of day for the transaction.
         */
        @JsonProperty("time_of_day")
        private String timeOfDay;
        
        /**
         * Day of week.
         */
        @JsonProperty("day_of_week")
        private String dayOfWeek;
        
        /**
         * Whether this is a holiday.
         */
        @JsonProperty("is_holiday")
        private Boolean isHoliday;
        
        /**
         * Session ID for this user session.
         */
        @JsonProperty("session_id")
        private String sessionId;
        
        /**
         * Session duration before this deposit (in minutes).
         */
        @JsonProperty("session_duration_minutes")
        private Integer sessionDurationMinutes;
        
        /**
         * Other actions performed in this session.
         */
        @JsonProperty("session_actions")
        private java.util.List<String> sessionActions;
        
        /**
         * Whether user behavior seems rushed or unusual.
         */
        @JsonProperty("behavioral_anomaly")
        private Boolean behavioralAnomaly;
        
        /**
         * Risk factors identified during the session.
         */
        @JsonProperty("session_risk_factors")
        @Builder.Default
        private java.util.List<String> sessionRiskFactors = new java.util.ArrayList<>();
    }

    /**
     * Validates the fraud request for completeness.
     *
     * @return true if valid
     * @throws IllegalArgumentException if validation fails
     */
    public boolean validateRequest() {
        if (depositId == null || userId == null) {
            throw new IllegalArgumentException("Deposit ID and User ID are required");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid amount is required");
        }
        
        if (checkImageFront == null || checkImageFront.trim().isEmpty()) {
            throw new IllegalArgumentException("Front check image is required");
        }
        
        if (checkImageBack == null || checkImageBack.trim().isEmpty()) {
            throw new IllegalArgumentException("Back check image is required");
        }
        
        return true;
    }

    /**
     * Gets a risk context summary for logging/monitoring.
     *
     * @return risk context summary
     */
    public String getRiskContextSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Amount: ").append(amount);
        
        if (userDepositHistory != null) {
            summary.append(", TotalDeposits: ").append(userDepositHistory.totalDeposits);
            summary.append(", Recent: ").append(userDepositHistory.depositsLast30Days);
        }
        
        if (accountBehavior != null) {
            summary.append(", AccountAge: ").append(accountBehavior.accountAgeDays);
            summary.append(", RiskTier: ").append(accountBehavior.userRiskTier);
        }
        
        if (deviceInfo != null && deviceInfo.deviceRiskFlag != null) {
            summary.append(", DeviceRisk: ").append(deviceInfo.deviceRiskFlag);
        }
        
        return summary.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckFraudRequest that = (CheckFraudRequest) o;
        return Objects.equals(depositId, that.depositId) &&
               Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(depositId, userId);
    }

    @Override
    public String toString() {
        return "CheckFraudRequest{" +
               "depositId=" + depositId +
               ", userId=" + userId +
               ", amount=" + amount +
               ", currency='" + currency + '\'' +
               ", checkNumber='" + checkNumber + '\'' +
               ", requestTimestamp=" + requestTimestamp +
               '}';
    }
}