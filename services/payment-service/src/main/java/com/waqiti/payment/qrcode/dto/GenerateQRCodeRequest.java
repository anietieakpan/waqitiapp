package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.waqiti.payment.qrcode.domain.QRCodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request DTO for generating a QR code payment
 * Supports both static and dynamic QR codes with comprehensive validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for generating a payment QR code")
public class GenerateQRCodeRequest {
    
    @NotNull(message = "User ID is required")
    @Schema(description = "ID of the user generating the QR code", required = true, example = "usr_123456")
    private String userId;
    
    @Schema(description = "Merchant ID if generating for a merchant", example = "mch_789012")
    private String merchantId;
    
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "10000.00", message = "Amount exceeds maximum limit")
    @Digits(integer = 10, fraction = 4, message = "Invalid amount format")
    @Schema(description = "Fixed amount for the QR code (optional for dynamic QR)", example = "99.99")
    private BigDecimal amount;
    
    @NotNull(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code")
    @Schema(description = "Currency code in ISO 4217 format", required = true, example = "USD")
    private String currency;
    
    @NotNull(message = "QR code type is required")
    @Schema(description = "Type of QR code to generate", required = true)
    private QRCodeType type;
    
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @Schema(description = "Payment description or purpose", example = "Coffee payment")
    private String description;
    
    @Size(max = 50, message = "Reference cannot exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9-_]*$", message = "Reference can only contain alphanumeric characters, hyphens, and underscores")
    @Schema(description = "External reference ID", example = "INV-2024-001")
    private String reference;
    
    @Schema(description = "Additional metadata for the QR code")
    private Map<String, String> metadata;
    
    @Min(value = 1, message = "Expiry must be at least 1 minute")
    @Max(value = 1440, message = "Expiry cannot exceed 24 hours")
    @Schema(description = "QR code expiry time in minutes", example = "5")
    private Integer expiryMinutes;
    
    @Schema(description = "Allow partial payments for this QR code", example = "false")
    private Boolean allowPartialPayment;
    
    @Schema(description = "Allow tips to be added to the amount", example = "true")
    private Boolean allowTips;
    
    @DecimalMin(value = "0.01", message = "Minimum amount must be positive")
    @Schema(description = "Minimum payment amount for dynamic QR", example = "1.00")
    private BigDecimal minimumAmount;
    
    @DecimalMax(value = "100000.00", message = "Maximum amount too high")
    @Schema(description = "Maximum payment amount for dynamic QR", example = "1000.00")
    private BigDecimal maximumAmount;
    
    @Size(max = 100, message = "Callback URL too long")
    @Pattern(regexp = "^(https?://)?[\\w.-]+(?:\\.[\\w\\.-]+)+[\\w\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=.]+$", 
             message = "Invalid callback URL format")
    @Schema(description = "Webhook URL for payment notifications", example = "https://merchant.com/webhook")
    private String callbackUrl;
    
    @Schema(description = "Require payer to enter a PIN", example = "false")
    private Boolean requirePin;
    
    @Schema(description = "Require payer confirmation before processing", example = "true")
    private Boolean requireConfirmation;
    
    @Size(max = 50)
    @Schema(description = "Custom styling theme for QR code", example = "dark")
    private String theme;
    
    @Min(value = 200)
    @Max(value = 1000)
    @Schema(description = "QR code image size in pixels", example = "300")
    private Integer imageSize;
    
    @Schema(description = "Include merchant logo in QR code", example = "true")
    private Boolean includeLogo;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Specific expiry date/time for the QR code")
    private LocalDateTime specificExpiryTime;
    
    @Size(max = 20)
    @Schema(description = "Category for analytics and reporting", example = "retail")
    private String category;
    
    @Size(max = 50)
    @Schema(description = "Store location or branch ID", example = "store_001")
    private String storeId;
    
    @Size(max = 50)
    @Schema(description = "Terminal or POS ID", example = "pos_001")
    private String terminalId;
    
    @Schema(description = "Enable offline payment capability", example = "false")
    private Boolean offlineCapable;
    
    @Size(max = 1000)
    @Schema(description = "Terms and conditions text")
    private String termsAndConditions;
    
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