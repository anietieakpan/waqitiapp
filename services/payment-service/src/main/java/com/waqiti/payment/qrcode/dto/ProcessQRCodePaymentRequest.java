package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for processing a QR code payment after scanning
 * Contains all necessary information for payment execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Request to process a QR code payment")
public class ProcessQRCodePaymentRequest {

    @NotBlank(message = "QR code ID is required")
    @Size(min = 1, max = 50)
    @Schema(description = "QR code identifier", required = true)
    @JsonProperty("qr_code_id")
    private String qrCodeId;

    @NotBlank(message = "Payer ID is required")
    @Size(min = 1, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$")
    @Schema(description = "User ID of the payer", required = true)
    @JsonProperty("payer_id")
    private String payerId;

    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "999999.99", message = "Amount cannot exceed 999999.99")
    @Digits(integer = 6, fraction = 2)
    @Schema(description = "Payment amount")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3)
    @Pattern(regexp = "^[A-Z]{3}$")
    @Schema(description = "Currency code", example = "USD", required = true)
    private String currency;

    @Schema(description = "Tip amount if applicable")
    @JsonProperty("tip_amount")
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "9999.99")
    private BigDecimal tipAmount;

    @NotBlank(message = "Payment method is required")
    @Schema(description = "Payment method identifier", required = true)
    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    @Schema(description = "Payment method type", example = "CARD")
    @JsonProperty("payment_method_type")
    private PaymentMethodType paymentMethodType;

    @Size(max = 255)
    @Schema(description = "Payment note or message")
    @JsonProperty("payment_note")
    private String paymentNote;

    @Schema(description = "Device information for security")
    @JsonProperty("device_info")
    private DeviceInfo deviceInfo;

    @Schema(description = "Location information for fraud detection")
    @JsonProperty("location_info")
    private LocationInfo locationInfo;

    @Schema(description = "Security verification data")
    @JsonProperty("security_verification")
    private SecurityVerification securityVerification;

    @Schema(description = "Split payment details if applicable")
    @JsonProperty("split_payment")
    private SplitPaymentDetails splitPayment;

    @Schema(description = "Installment configuration if applicable")
    @JsonProperty("installment_config")
    private InstallmentConfig installmentConfig;

    @Schema(description = "Loyalty points to redeem")
    @JsonProperty("loyalty_redemption")
    private LoyaltyRedemption loyaltyRedemption;

    @Schema(description = "Promo code to apply")
    @JsonProperty("promo_code")
    @Size(max = 50)
    private String promoCode;

    @Schema(description = "Idempotency key for duplicate prevention")
    @JsonProperty("idempotency_key")
    @NotBlank(message = "Idempotency key is required")
    @Size(min = 16, max = 64)
    private String idempotencyKey;

    @Schema(description = "Client timestamp")
    @JsonProperty("client_timestamp")
    private LocalDateTime clientTimestamp;

    @Schema(description = "Session ID for tracking")
    @JsonProperty("session_id")
    private String sessionId;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Risk assessment override")
    @JsonProperty("risk_override")
    private RiskOverride riskOverride;

    @Schema(description = "Notification preferences")
    @JsonProperty("notification_preferences")
    private NotificationPreferences notificationPreferences;

    @Schema(description = "Terms acceptance confirmation")
    @JsonProperty("terms_accepted")
    private Boolean termsAccepted;

    @Schema(description = "Request signature for integrity")
    private String signature;

    /**
     * Payment method types
     */
    public enum PaymentMethodType {
        CARD, BANK_ACCOUNT, WALLET, CRYPTO, CASH, LOYALTY_POINTS, BNPL
    }

    /**
     * Device information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        @JsonProperty("device_id")
        private String deviceId;
        
        @JsonProperty("device_type")
        private String deviceType;
        
        @JsonProperty("os_name")
        private String osName;
        
        @JsonProperty("os_version")
        private String osVersion;
        
        @JsonProperty("app_version")
        private String appVersion;
        
        @JsonProperty("ip_address")
        private String ipAddress;
        
        @JsonProperty("device_fingerprint")
        private String deviceFingerprint;
        
        @JsonProperty("is_jailbroken")
        private Boolean isJailbroken;
        
        @JsonProperty("network_type")
        private String networkType;
    }

    /**
     * Location information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        private Double latitude;
        private Double longitude;
        private Double accuracy;
        
        @JsonProperty("country_code")
        private String countryCode;
        
        private String city;
        private String state;
        
        @JsonProperty("postal_code")
        private String postalCode;
        
        @JsonProperty("is_vpn")
        private Boolean isVpn;
        
        @JsonProperty("is_proxy")
        private Boolean isProxy;
    }

    /**
     * Security verification
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityVerification {
        @JsonProperty("pin_verified")
        private Boolean pinVerified;
        
        @JsonProperty("biometric_verified")
        private Boolean biometricVerified;
        
        @JsonProperty("two_factor_code")
        @Size(min = 6, max = 6)
        private String twoFactorCode;
        
        @JsonProperty("security_question_answers")
        private Map<String, String> securityQuestionAnswers;
        
        @JsonProperty("device_trust_score")
        @Min(0)
        @Max(100)
        private Integer deviceTrustScore;
        
        @JsonProperty("fraud_check_passed")
        private Boolean fraudCheckPassed;
        
        @JsonProperty("kyc_verified")
        private Boolean kycVerified;
        
        @JsonProperty("challenge_response")
        private String challengeResponse;
    }

    /**
     * Split payment details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitPaymentDetails {
        @JsonProperty("split_type")
        private SplitType splitType;
        
        @JsonProperty("total_participants")
        @Min(2)
        @Max(20)
        private Integer totalParticipants;
        
        @JsonProperty("payer_share")
        private BigDecimal payerShare;
        
        @JsonProperty("split_config")
        private List<SplitConfig> splitConfig;
        
        @JsonProperty("collection_method")
        private String collectionMethod;
        
        @JsonProperty("deadline")
        private LocalDateTime deadline;
        
        public enum SplitType {
            EQUAL, PERCENTAGE, CUSTOM, PROPORTIONAL
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SplitConfig {
            @JsonProperty("user_id")
            private String userId;
            
            private BigDecimal amount;
            
            private BigDecimal percentage;
            
            @JsonProperty("is_paid")
            private Boolean isPaid;
            
            @JsonProperty("payment_method")
            private String paymentMethod;
        }
    }

    /**
     * Installment configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstallmentConfig {
        @JsonProperty("installment_count")
        @Min(2)
        @Max(48)
        private Integer installmentCount;
        
        @JsonProperty("installment_frequency")
        private InstallmentFrequency installmentFrequency;
        
        @JsonProperty("down_payment")
        private BigDecimal downPayment;
        
        @JsonProperty("interest_rate")
        private BigDecimal interestRate;
        
        @JsonProperty("total_amount")
        private BigDecimal totalAmount;
        
        @JsonProperty("monthly_payment")
        private BigDecimal monthlyPayment;
        
        @JsonProperty("first_payment_date")
        private LocalDateTime firstPaymentDate;
        
        @JsonProperty("provider")
        private String provider;
        
        public enum InstallmentFrequency {
            WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY
        }
    }

    /**
     * Loyalty redemption
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoyaltyRedemption {
        @JsonProperty("points_to_redeem")
        @Min(1)
        private Integer pointsToRedeem;
        
        @JsonProperty("points_value")
        private BigDecimal pointsValue;
        
        @JsonProperty("program_id")
        private String programId;
        
        @JsonProperty("member_id")
        private String memberId;
        
        @JsonProperty("redemption_type")
        private RedemptionType redemptionType;
        
        @JsonProperty("remaining_balance")
        private Integer remainingBalance;
        
        public enum RedemptionType {
            FULL, PARTIAL, POINTS_PLUS_CASH
        }
    }

    /**
     * Risk override
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskOverride {
        @JsonProperty("override_reason")
        private String overrideReason;
        
        @JsonProperty("authorized_by")
        private String authorizedBy;
        
        @JsonProperty("risk_acceptance_level")
        private String riskAcceptanceLevel;
        
        @JsonProperty("additional_checks")
        private List<String> additionalChecks;
        
        @JsonProperty("manual_review_completed")
        private Boolean manualReviewCompleted;
    }

    /**
     * Notification preferences
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPreferences {
        @JsonProperty("send_email")
        private Boolean sendEmail;
        
        @JsonProperty("send_sms")
        private Boolean sendSms;
        
        @JsonProperty("send_push")
        private Boolean sendPush;
        
        @JsonProperty("email_address")
        @Email
        private String emailAddress;
        
        @JsonProperty("phone_number")
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$")
        private String phoneNumber;
        
        @JsonProperty("language_code")
        private String languageCode;
        
        @JsonProperty("webhook_url")
        private String webhookUrl;
    }

    /**
     * Validate the request
     */
    public void validate() {
        if (splitPayment != null && splitPayment.getSplitConfig() != null) {
            BigDecimal totalSplit = splitPayment.getSplitConfig().stream()
                .map(SplitPaymentDetails.SplitConfig::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            if (amount != null && totalSplit.compareTo(amount) > 0) {
                throw new IllegalArgumentException("Split amounts exceed total amount");
            }
        }
        
        if (loyaltyRedemption != null && loyaltyRedemption.getPointsValue() != null) {
            if (amount != null && loyaltyRedemption.getPointsValue().compareTo(amount) > 0) {
                throw new IllegalArgumentException("Loyalty redemption value exceeds payment amount");
            }
        }
        
        if (installmentConfig != null && installmentConfig.getDownPayment() != null) {
            if (amount != null && installmentConfig.getDownPayment().compareTo(amount) >= 0) {
                throw new IllegalArgumentException("Down payment must be less than total amount");
            }
        }
    }
}