package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Comprehensive request DTO for initiating a check deposit with full validation and metadata.
 * Supports mobile remote deposit capture (RDC) with enhanced fraud detection.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckDepositRequest {
    
    /**
     * User ID making the deposit.
     */
    @NotNull(message = "User ID is required")
    @JsonProperty("user_id")
    private UUID userId;
    
    /**
     * Target wallet ID for the deposit.
     */
    @NotNull(message = "Wallet ID is required")
    @JsonProperty("wallet_id")
    private UUID walletId;
    
    /**
     * Check amount (must match OCR extracted amount for validation).
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least $0.01")
    @DecimalMax(value = "100000.00", message = "Amount cannot exceed $100,000")
    private BigDecimal amount;
    
    /**
     * Currency of the check (defaults to USD).
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    @Builder.Default
    private String currency = "USD";
    
    /**
     * Front side of the check (base64 encoded).
     */
    @NotBlank(message = "Front image is required")
    @JsonProperty("front_image_base64")
    private String frontImageBase64;
    
    /**
     * Back side of the check (base64 encoded).
     */
    @NotBlank(message = "Back image is required")
    @JsonProperty("back_image_base64")
    private String backImageBase64;
    
    /**
     * Image metadata for processing.
     */
    @Valid
    @JsonProperty("image_metadata")
    private ImageMetadata imageMetadata;
    
    /**
     * Check details extracted or provided by user.
     */
    @Valid
    @JsonProperty("check_details")
    private CheckDetails checkDetails;
    
    /**
     * Device information for fraud detection.
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
     * Additional deposit options.
     */
    @Valid
    @JsonProperty("deposit_options")
    private DepositOptions depositOptions;
    
    /**
     * Optional memo or description.
     */
    @Size(max = 255, message = "Memo cannot exceed 255 characters")
    private String memo;
    
    /**
     * Idempotency key for preventing duplicate submissions.
     */
    @JsonProperty("idempotency_key")
    private String idempotencyKey;
    
    /**
     * Request timestamp.
     */
    @JsonProperty("request_timestamp")
    private LocalDateTime requestTimestamp;

    /**
     * Image metadata and quality information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageMetadata {
        
        /**
         * Image resolution width.
         */
        @Positive(message = "Image width must be positive")
        private Integer width;
        
        /**
         * Image resolution height.
         */
        @Positive(message = "Image height must be positive")
        private Integer height;
        
        /**
         * Image file size in bytes.
         */
        @JsonProperty("file_size_bytes")
        private Long fileSizeBytes;
        
        /**
         * Image format (JPEG, PNG, etc.).
         */
        @JsonProperty("image_format")
        private String imageFormat;
        
        /**
         * Image quality score (0-100).
         */
        @JsonProperty("quality_score")
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        private BigDecimal qualityScore;
        
        /**
         * Whether flash was used.
         */
        @JsonProperty("flash_used")
        private Boolean flashUsed;
        
        /**
         * Camera orientation when image was taken.
         */
        private String orientation;
        
        /**
         * EXIF data (if available).
         */
        @JsonProperty("exif_data")
        private java.util.Map<String, Object> exifData;
    }

    /**
     * Check details and MICR information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckDetails {
        
        /**
         * Check number from MICR line.
         */
        @JsonProperty("check_number")
        private String checkNumber;
        
        /**
         * Routing number from MICR line.
         */
        @Pattern(regexp = "^\\d{9}$", message = "Routing number must be 9 digits")
        @JsonProperty("routing_number")
        private String routingNumber;
        
        /**
         * Account number from MICR line.
         */
        @JsonProperty("account_number")
        private String accountNumber;
        
        /**
         * Name of the payor (person/entity who wrote the check).
         */
        @JsonProperty("payor_name")
        private String payorName;
        
        /**
         * Payor address (if available).
         */
        @JsonProperty("payor_address")
        private String payorAddress;
        
        /**
         * Date on the check.
         */
        @JsonProperty("check_date")
        private String checkDate;
        
        /**
         * Payee name (should match user account).
         */
        @JsonProperty("payee_name")
        private String payeeName;
        
        /**
         * Memo line from the check.
         */
        @JsonProperty("memo_line")
        private String memoLine;
        
        /**
         * Check serial number or auxiliary field.
         */
        @JsonProperty("serial_number")
        private String serialNumber;
        
        /**
         * Bank name (if determinable).
         */
        @JsonProperty("bank_name")
        private String bankName;
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
        
        @JsonProperty("device_type")
        private String deviceType;
        
        @JsonProperty("device_model")
        private String deviceModel;
        
        @JsonProperty("operating_system")
        private String operatingSystem;
        
        @JsonProperty("os_version")
        private String osVersion;
        
        @JsonProperty("app_version")
        private String appVersion;
        
        @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$",
                message = "Invalid IP address format")
        @JsonProperty("ip_address")
        private String ipAddress;
        
        @JsonProperty("user_agent")
        private String userAgent;
        
        /**\n         * Device fingerprint for fraud detection.\n         */\n        @JsonProperty(\"device_fingerprint\")\n        private String deviceFingerprint;\n        \n        /**\n         * Screen resolution.\n         */\n        @JsonProperty(\"screen_resolution\")\n        private String screenResolution;\n        \n        /**\n         * Timezone of the device.\n         */\n        private String timezone;\n    }\n\n    /**\n     * Location information for the deposit.\n     */\n    @Data\n    @Builder\n    @NoArgsConstructor\n    @AllArgsConstructor\n    public static class LocationInfo {\n        \n        @DecimalMin(value = \"-90.0\", message = \"Invalid latitude\")\n        @DecimalMax(value = \"90.0\", message = \"Invalid latitude\")\n        private BigDecimal latitude;\n        \n        @DecimalMin(value = \"-180.0\", message = \"Invalid longitude\")\n        @DecimalMax(value = \"180.0\", message = \"Invalid longitude\")\n        private BigDecimal longitude;\n        \n        /**\n         * Location accuracy in meters.\n         */\n        private BigDecimal accuracy;\n        \n        /**\n         * Address components (if available).\n         */\n        private String address;\n        \n        private String city;\n        \n        private String state;\n        \n        @JsonProperty(\"zip_code\")\n        private String zipCode;\n        \n        private String country;\n        \n        /**\n         * Timestamp when location was captured.\n         */\n        @JsonProperty(\"location_timestamp\")\n        private LocalDateTime locationTimestamp;\n    }\n\n    /**\n     * Deposit processing options.\n     */\n    @Data\n    @Builder\n    @NoArgsConstructor\n    @AllArgsConstructor\n    public static class DepositOptions {\n        \n        /**\n         * Whether to request expedited processing.\n         */\n        @JsonProperty(\"expedited_processing\")\n        @Builder.Default\n        private Boolean expeditedProcessing = false;\n        \n        /**\n         * Whether user accepts holds policy.\n         */\n        @JsonProperty(\"accept_holds_policy\")\n        @Builder.Default\n        private Boolean acceptHoldsPolicy = true;\n        \n        /**\n         * Notification preferences.\n         */\n        @JsonProperty(\"notification_preferences\")\n        private NotificationPreferences notificationPreferences;\n        \n        /**\n         * Risk tolerance level.\n         */\n        @JsonProperty(\"risk_tolerance\")\n        private String riskTolerance;\n    }\n\n    /**\n     * Notification preferences for deposit updates.\n     */\n    @Data\n    @Builder\n    @NoArgsConstructor\n    @AllArgsConstructor\n    public static class NotificationPreferences {\n        \n        @JsonProperty(\"sms_notifications\")\n        @Builder.Default\n        private Boolean smsNotifications = true;\n        \n        @JsonProperty(\"email_notifications\")\n        @Builder.Default\n        private Boolean emailNotifications = true;\n        \n        @JsonProperty(\"push_notifications\")\n        @Builder.Default\n        private Boolean pushNotifications = true;\n    }\n\n    /**\n     * Validates the request for business logic compliance.\n     *\n     * @return true if valid\n     * @throws IllegalArgumentException if validation fails\n     */\n    public boolean validateBusinessRules() {\n        // Check image size constraints\n        if (frontImageBase64 != null && frontImageBase64.length() > 10_000_000) {\n            throw new IllegalArgumentException(\"Front image too large (max 10MB)\");\n        }\n        \n        if (backImageBase64 != null && backImageBase64.length() > 10_000_000) {\n            throw new IllegalArgumentException(\"Back image too large (max 10MB)\");\n        }\n        \n        // Validate check details if provided\n        if (checkDetails != null && checkDetails.routingNumber != null) {\n            if (!isValidRoutingNumber(checkDetails.routingNumber)) {\n                throw new IllegalArgumentException(\"Invalid routing number checksum\");\n            }\n        }\n        \n        return true;\n    }\n\n    /**\n     * Validates ABA routing number using checksum algorithm.\n     */\n    private boolean isValidRoutingNumber(String routingNumber) {\n        if (routingNumber == null || routingNumber.length() != 9) {\n            return false;\n        }\n        \n        try {\n            int[] digits = routingNumber.chars().map(c -> c - '0').toArray();\n            int checksum = 3 * (digits[0] + digits[3] + digits[6]) +\n                          7 * (digits[1] + digits[4] + digits[7]) +\n                          (digits[2] + digits[5] + digits[8]);\n            return checksum % 10 == 0;\n        } catch (Exception e) {\n            return false;\n        }\n    }\n\n    @Override\n    public boolean equals(Object o) {\n        if (this == o) return true;\n        if (o == null || getClass() != o.getClass()) return false;\n        CheckDepositRequest that = (CheckDepositRequest) o;\n        return Objects.equals(idempotencyKey, that.idempotencyKey) &&\n               Objects.equals(userId, that.userId) &&\n               Objects.equals(amount, that.amount);\n    }\n\n    @Override\n    public int hashCode() {\n        return Objects.hash(idempotencyKey, userId, amount);\n    }\n\n    @Override\n    public String toString() {\n        return \"CheckDepositRequest{\" +\n               \"userId=\" + userId +\n               \", walletId=\" + walletId +\n               \", amount=\" + amount +\n               \", currency='\" + currency + '\\'' +\n               \", checkNumber='\" + (checkDetails != null ? checkDetails.checkNumber : null) + '\\'' +\n               \", idempotencyKey='\" + idempotencyKey + '\\'' +\n               '}';\n    }\n}"}