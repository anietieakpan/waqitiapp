package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request DTO for initializing NFC merchant payment session
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCMerchantSessionRequest {

    @NotBlank(message = "Merchant ID is required")
    @Size(max = 64, message = "Merchant ID must not exceed 64 characters")
    private String merchantId;

    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "10000.00", message = "Amount cannot exceed $10,000")
    @Digits(integer = 6, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO code")
    private String currency;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    @Size(max = 64, message = "Order ID must not exceed 64 characters")
    private String orderId;

    @NotBlank(message = "Device ID is required")
    @Size(max = 128, message = "Device ID must not exceed 128 characters")
    private String deviceId;

    @Size(max = 32, message = "NFC protocol version must not exceed 32 characters")
    private String nfcProtocolVersion;

    @Min(value = 1, message = "Session timeout must be at least 1 minute")
    @Max(value = 60, message = "Session timeout cannot exceed 60 minutes")
    private Integer sessionTimeoutMinutes;

    // Location data
    private Double latitude;
    private Double longitude;
    private String locationAccuracy;

    // Payment constraints
    private BigDecimal minimumAmount;
    private BigDecimal maximumAmount;
    
    @Size(max = 255, message = "Allowed payment methods must not exceed 255 characters")
    private String allowedPaymentMethods;

    // Security settings
    private boolean requireBiometric;
    private boolean requirePin;
    private String securityLevel; // LOW, MEDIUM, HIGH

    // Additional metadata
    @Size(max = 1000, message = "Metadata must not exceed 1000 characters")
    private String metadata;

    /**
     * Gets the session timeout in seconds
     */
    public long getSessionTimeoutSeconds() {
        return sessionTimeoutMinutes != null ? sessionTimeoutMinutes * 60L : 600L; // Default 10 minutes
    }

    /**
     * Validates if amount constraints are properly set
     */
    public boolean hasValidAmountConstraints() {
        if (minimumAmount != null && maximumAmount != null) {
            return minimumAmount.compareTo(maximumAmount) <= 0;
        }
        return true;
    }

    /**
     * Gets the default currency if not specified
     */
    public String getCurrencyOrDefault() {
        return currency != null ? currency : "USD";
    }
}