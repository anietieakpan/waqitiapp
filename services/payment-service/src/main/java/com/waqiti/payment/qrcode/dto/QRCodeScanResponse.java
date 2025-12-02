package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for QR code scanning with comprehensive payment details
 * Provides all information needed for payment confirmation and processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response after scanning a QR code")
public class QRCodeScanResponse {

    @Schema(description = "QR code identifier", example = "QR202412150001", required = true)
    @JsonProperty("qr_code_id")
    private String qrCodeId;

    @Schema(description = "Status of the scanned QR code", example = "ACTIVE")
    private String status;

    @Schema(description = "QR code type", example = "DYNAMIC_AMOUNT")
    private String type;

    @Schema(description = "Recipient user ID")
    @JsonProperty("recipient_id")
    private String recipientId;

    @Schema(description = "Recipient display name")
    @JsonProperty("recipient_name")
    private String recipientName;

    @Schema(description = "Recipient profile picture URL")
    @JsonProperty("recipient_avatar")
    private String recipientAvatar;

    @Schema(description = "Merchant information if applicable")
    @JsonProperty("merchant_info")
    private MerchantInfo merchantInfo;

    @Schema(description = "Fixed amount if specified")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Payment description")
    private String description;

    @Schema(description = "Reference ID")
    @JsonProperty("reference_id")
    private String referenceId;

    @Schema(description = "Whether payment requires confirmation")
    @JsonProperty("requires_confirmation")
    private Boolean requiresConfirmation;

    @Schema(description = "Whether amount can be modified")
    @JsonProperty("amount_modifiable")
    private Boolean amountModifiable;

    @Schema(description = "Minimum payment amount")
    @JsonProperty("min_amount")
    private BigDecimal minAmount;

    @Schema(description = "Maximum payment amount")
    @JsonProperty("max_amount")
    private BigDecimal maxAmount;

    @Schema(description = "Whether tips are allowed")
    @JsonProperty("tips_allowed")
    private Boolean tipsAllowed;

    @Schema(description = "Suggested tip percentages")
    @JsonProperty("tip_suggestions")
    private List<Integer> tipSuggestions;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "QR code expiry time")
    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @Schema(description = "Time remaining in seconds")
    @JsonProperty("ttl_seconds")
    private Long ttlSeconds;

    @Schema(description = "Security features required")
    @JsonProperty("security_features")
    private SecurityFeatures securityFeatures;

    @Schema(description = "Payment options available")
    @JsonProperty("payment_options")
    private PaymentOptions paymentOptions;

    @Schema(description = "Promotion or discount information")
    @JsonProperty("promotion_info")
    private PromotionInfo promotionInfo;

    @Schema(description = "Split payment configuration if applicable")
    @JsonProperty("split_payment")
    private SplitPaymentInfo splitPayment;

    @Schema(description = "Loyalty program information")
    @JsonProperty("loyalty_info")
    private LoyaltyInfo loyaltyInfo;

    @Schema(description = "Recurring payment configuration")
    @JsonProperty("recurring_payment")
    private RecurringPaymentInfo recurringPayment;

    @Schema(description = "Terms and conditions")
    @JsonProperty("terms_conditions")
    private TermsConditions termsConditions;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Validation result")
    @JsonProperty("validation_result")
    private ValidationResult validationResult;

    @Schema(description = "Available actions for this QR code")
    @JsonProperty("available_actions")
    private List<String> availableActions;

    @Schema(description = "UI display configuration")
    @JsonProperty("display_config")
    private DisplayConfig displayConfig;

    @Schema(description = "Whether QR code is valid for scanning")
    @JsonProperty("is_valid")
    private Boolean isValid;

    @Schema(description = "Error message if invalid")
    @JsonProperty("error_message")
    private String errorMessage;

    /**
     * Merchant information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantInfo {
        @JsonProperty("merchant_id")
        private String merchantId;
        
        @JsonProperty("merchant_name")
        private String merchantName;
        
        @JsonProperty("business_name")
        private String businessName;
        
        private String category;
        
        @JsonProperty("logo_url")
        private String logoUrl;
        
        private String address;
        
        @JsonProperty("contact_phone")
        private String contactPhone;
        
        @JsonProperty("contact_email")
        private String contactEmail;
        
        private Double rating;
        
        @JsonProperty("verified_merchant")
        private Boolean verifiedMerchant;
        
        @JsonProperty("store_id")
        private String storeId;
        
        @JsonProperty("terminal_id")
        private String terminalId;
    }

    /**
     * Security features configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityFeatures {
        @JsonProperty("require_pin")
        private Boolean requirePin;
        
        @JsonProperty("require_biometric")
        private Boolean requireBiometric;
        
        @JsonProperty("require_2fa")
        private Boolean require2FA;
        
        @JsonProperty("require_location")
        private Boolean requireLocation;
        
        @JsonProperty("device_binding")
        private Boolean deviceBinding;
        
        @JsonProperty("fraud_check_level")
        private String fraudCheckLevel;
        
        @JsonProperty("kyc_required")
        private Boolean kycRequired;
        
        @JsonProperty("max_attempts")
        private Integer maxAttempts;
        
        @JsonProperty("security_questions")
        private List<String> securityQuestions;
    }

    /**
     * Payment options
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentOptions {
        @JsonProperty("payment_methods")
        private List<PaymentMethod> paymentMethods;
        
        @JsonProperty("installment_available")
        private Boolean installmentAvailable;
        
        @JsonProperty("max_installments")
        private Integer maxInstallments;
        
        @JsonProperty("partial_payment_allowed")
        private Boolean partialPaymentAllowed;
        
        @JsonProperty("currencies_supported")
        private List<String> currenciesSupported;
        
        @JsonProperty("exchange_rates")
        private Map<String, BigDecimal> exchangeRates;
        
        @JsonProperty("payment_schedule")
        private PaymentSchedule paymentSchedule;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PaymentMethod {
            private String type;
            private String name;
            private String icon;
            private Boolean available;
            private BigDecimal fee;
            private String feeType;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PaymentSchedule {
            private String frequency;
            private Integer occurrences;
            private LocalDateTime startDate;
            private LocalDateTime endDate;
        }
    }

    /**
     * Promotion information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionInfo {
        @JsonProperty("promo_code")
        private String promoCode;
        
        @JsonProperty("discount_type")
        private String discountType;
        
        @JsonProperty("discount_value")
        private BigDecimal discountValue;
        
        @JsonProperty("discount_percentage")
        private BigDecimal discountPercentage;
        
        @JsonProperty("max_discount")
        private BigDecimal maxDiscount;
        
        @JsonProperty("min_purchase")
        private BigDecimal minPurchase;
        
        @JsonProperty("valid_until")
        private LocalDateTime validUntil;
        
        private String description;
        
        @JsonProperty("terms_conditions")
        private String termsConditions;
        
        @JsonProperty("auto_applied")
        private Boolean autoApplied;
    }

    /**
     * Split payment information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitPaymentInfo {
        @JsonProperty("split_enabled")
        private Boolean splitEnabled;
        
        @JsonProperty("split_type")
        private String splitType;
        
        @JsonProperty("total_participants")
        private Integer totalParticipants;
        
        @JsonProperty("participants")
        private List<Participant> participants;
        
        @JsonProperty("your_share")
        private BigDecimal yourShare;
        
        @JsonProperty("allow_unequal_split")
        private Boolean allowUnequalSplit;
        
        @JsonProperty("deadline")
        private LocalDateTime deadline;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Participant {
            @JsonProperty("user_id")
            private String userId;
            
            private String name;
            
            private String email;
            
            private String phone;
            
            private BigDecimal amount;
            
            private BigDecimal percentage;
            
            private String status;
            
            @JsonProperty("paid_at")
            private LocalDateTime paidAt;
        }
    }

    /**
     * Loyalty program information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoyaltyInfo {
        @JsonProperty("program_id")
        private String programId;
        
        @JsonProperty("program_name")
        private String programName;
        
        @JsonProperty("points_earned")
        private Integer pointsEarned;
        
        @JsonProperty("points_multiplier")
        private BigDecimal pointsMultiplier;
        
        @JsonProperty("current_balance")
        private Integer currentBalance;
        
        @JsonProperty("tier_name")
        private String tierName;
        
        @JsonProperty("tier_benefits")
        private List<String> tierBenefits;
        
        @JsonProperty("redeemable_points")
        private Integer redeemablePoints;
        
        @JsonProperty("points_value")
        private BigDecimal pointsValue;
        
        @JsonProperty("expiry_date")
        private LocalDateTime expiryDate;
    }

    /**
     * Recurring payment information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecurringPaymentInfo {
        @JsonProperty("is_recurring")
        private Boolean isRecurring;
        
        private String frequency;
        
        private Integer interval;
        
        @JsonProperty("start_date")
        private LocalDateTime startDate;
        
        @JsonProperty("end_date")
        private LocalDateTime endDate;
        
        @JsonProperty("total_occurrences")
        private Integer totalOccurrences;
        
        @JsonProperty("completed_occurrences")
        private Integer completedOccurrences;
        
        @JsonProperty("next_payment_date")
        private LocalDateTime nextPaymentDate;
        
        @JsonProperty("recurring_amount")
        private BigDecimal recurringAmount;
        
        @JsonProperty("auto_renew")
        private Boolean autoRenew;
        
        @JsonProperty("cancellation_allowed")
        private Boolean cancellationAllowed;
    }

    /**
     * Terms and conditions
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TermsConditions {
        private String text;
        
        private String url;
        
        @JsonProperty("must_accept")
        private Boolean mustAccept;
        
        private String version;
        
        @JsonProperty("last_updated")
        private LocalDateTime lastUpdated;
    }

    /**
     * Validation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private Boolean valid;
        
        @JsonProperty("validation_errors")
        private List<String> validationErrors;
        
        @JsonProperty("warning_messages")
        private List<String> warningMessages;
        
        @JsonProperty("risk_score")
        private Integer riskScore;
        
        @JsonProperty("risk_factors")
        private List<String> riskFactors;
        
        @JsonProperty("compliance_check")
        private ComplianceCheck complianceCheck;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ComplianceCheck {
            @JsonProperty("aml_check")
            private String amlCheck;
            
            @JsonProperty("sanctions_check")
            private String sanctionsCheck;
            
            @JsonProperty("pep_check")
            private String pepCheck;
            
            @JsonProperty("kyc_status")
            private String kycStatus;
        }
    }

    /**
     * Display configuration
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisplayConfig {
        @JsonProperty("theme_color")
        private String themeColor;
        
        @JsonProperty("logo_position")
        private String logoPosition;
        
        @JsonProperty("show_recipient_info")
        private Boolean showRecipientInfo;
        
        @JsonProperty("show_amount")
        private Boolean showAmount;
        
        @JsonProperty("custom_message")
        private String customMessage;
        
        @JsonProperty("button_text")
        private String buttonText;
        
        @JsonProperty("success_message")
        private String successMessage;
        
        @JsonProperty("ui_template")
        private String uiTemplate;
    }
}