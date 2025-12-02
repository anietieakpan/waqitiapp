package com.waqiti.payment.qrcode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * CRITICAL P0 FIX: Generate Merchant QR Code Request DTO
 *
 * Request DTO specifically for generating merchant QR codes with
 * merchant-specific features (store ID, terminal ID, bulk generation, etc.)
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for generating a merchant QR code")
public class GenerateMerchantQRCodeRequest {

    @NotBlank(message = "Merchant ID is required")
    @Schema(description = "ID of the merchant generating the QR code",
            required = true,
            example = "mch_789012")
    private String merchantId;

    @Schema(description = "Merchant user ID", example = "usr_123456")
    private String userId;

    @NotBlank(message = "Store ID is required")
    @Size(max = 50, message = "Store ID too long")
    @Schema(description = "Store or branch identifier",
            required = true,
            example = "store_001")
    private String storeId;

    @Size(max = 50, message = "Terminal ID too long")
    @Schema(description = "Terminal or POS identifier", example = "pos_001")
    private String terminalId;

    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "100000.00", message = "Amount exceeds maximum limit")
    @Digits(integer = 10, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Fixed amount for the QR code (optional for dynamic merchant QR)", example = "99.99")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    @Schema(description = "Currency code in ISO 4217 format",
            required = true,
            example = "USD")
    private String currency;

    @NotNull(message = "QR code type is required")
    @Schema(description = "Type of merchant QR code",
            required = true,
            example = "MERCHANT_STATIC",
            allowableValues = {"MERCHANT_STATIC", "MERCHANT_DYNAMIC"})
    @Pattern(regexp = "^(MERCHANT_STATIC|MERCHANT_DYNAMIC)$",
             message = "Type must be MERCHANT_STATIC or MERCHANT_DYNAMIC")
    private String type;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Schema(description = "Payment description or purpose", example = "Store checkout payment")
    private String description;

    @Size(max = 50, message = "Reference cannot exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9-_]*$",
             message = "Reference can only contain alphanumeric characters, hyphens, and underscores")
    @Schema(description = "Merchant reference ID", example = "INVOICE-2024-001")
    private String reference;

    @Min(value = 1, message = "Expiry must be at least 1 minute")
    @Max(value = 43200, message = "Expiry cannot exceed 30 days (43200 minutes)")
    @Schema(description = "QR code expiry time in minutes (for dynamic QR)", example = "1440")
    private Integer expiryMinutes;

    @DecimalMin(value = "0.01", message = "Minimum amount must be positive")
    @Schema(description = "Minimum payment amount for dynamic QR", example = "1.00")
    private BigDecimal minimumAmount;

    @DecimalMax(value = "1000000.00", message = "Maximum amount too high")
    @Schema(description = "Maximum payment amount for dynamic QR", example = "10000.00")
    private BigDecimal maximumAmount;

    @Schema(description = "Allow tips to be added to the amount", example = "true")
    private Boolean allowTips;

    @DecimalMin(value = "0.0", message = "Tip percentage must be non-negative")
    @DecimalMax(value = "100.0", message = "Tip percentage cannot exceed 100%")
    @Schema(description = "Suggested tip percentage", example = "15.0")
    private BigDecimal suggestedTipPercentage;

    @Size(max = 200, message = "Callback URL too long")
    @Pattern(regexp = "^(https?://)?[\\w.-]+(?:\\.[\\w\\.-]+)+[\\w\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=.]+$",
             message = "Invalid callback URL format")
    @Schema(description = "Webhook URL for payment notifications", example = "https://merchant.com/webhook")
    private String callbackUrl;

    @Schema(description = "Include merchant logo in QR code", example = "true")
    private Boolean includeLogo;

    @Size(max = 500, message = "Logo URL too long")
    @Schema(description = "Custom logo URL", example = "https://cdn.merchant.com/logo.png")
    private String logoUrl;

    @Min(value = 200)
    @Max(value = 1000)
    @Schema(description = "QR code image size in pixels", example = "400")
    private Integer imageSize;

    @Size(max = 50)
    @Schema(description = "QR code color theme", example = "brand")
    private String theme;

    @Size(max = 20)
    @Schema(description = "Business category", example = "retail")
    private String category;

    @Size(max = 100)
    @Schema(description = "Cashier or staff ID", example = "cashier_42")
    private String cashierId;

    @Schema(description = "Enable offline payment capability", example = "false")
    private Boolean offlineCapable;

    @Schema(description = "Generate printable QR code (high resolution)", example = "true")
    private Boolean printable;

    @Schema(description = "Additional metadata for the QR code")
    private Map<String, String> metadata;

    @Size(max = 1000)
    @Schema(description = "Terms and conditions text")
    private String termsAndConditions;

    @Schema(description = "Fraud detection level", example = "MEDIUM")
    @Pattern(regexp = "^(LOW|MEDIUM|HIGH|MAXIMUM)$",
             message = "Fraud detection level must be LOW, MEDIUM, HIGH, or MAXIMUM")
    private String fraudDetectionLevel;

    @Schema(description = "Campaign ID for marketing analytics", example = "campaign_2024_q1")
    private String campaignId;

    @Schema(description = "Enable detailed analytics tracking", example = "true")
    private Boolean enableAnalytics;

    // Validation methods
    @AssertTrue(message = "Minimum amount cannot be greater than maximum amount")
    private boolean isAmountRangeValid() {
        if (minimumAmount != null && maximumAmount != null) {
            return minimumAmount.compareTo(maximumAmount) <= 0;
        }
        return true;
    }

    @AssertTrue(message = "Fixed amount cannot be set with amount range")
    private boolean isAmountConfigurationValid() {
        if (amount != null) {
            return minimumAmount == null && maximumAmount == null;
        }
        return true;
    }
}
