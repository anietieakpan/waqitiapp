package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request DTO for NFC merchant payments
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCMerchantPaymentRequest {

    @NotBlank(message = "Payment ID is required")
    @Size(max = 64, message = "Payment ID must not exceed 64 characters")
    private String paymentId;

    @NotBlank(message = "Merchant ID is required")
    @Size(max = 64, message = "Merchant ID must not exceed 64 characters")
    private String merchantId;

    @NotBlank(message = "Customer ID is required")
    @Size(max = 64, message = "Customer ID must not exceed 64 characters")
    private String customerId;

    @NotNull(message = "Amount is required")
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

    @NotNull(message = "Timestamp is required")
    @PastOrPresent(message = "Timestamp cannot be in the future")
    private Instant timestamp;

    @NotBlank(message = "Signature is required")
    @Size(max = 512, message = "Signature must not exceed 512 characters")
    private String signature;

    @NotBlank(message = "Device ID is required")
    @Size(max = 128, message = "Device ID must not exceed 128 characters")
    private String deviceId;

    @Size(max = 64, message = "NFC session ID must not exceed 64 characters")
    private String nfcSessionId;

    @Size(max = 32, message = "NFC protocol version must not exceed 32 characters")
    private String nfcProtocolVersion;

    // Security and fraud detection fields
    @Size(max = 255, message = "Device fingerprint must not exceed 255 characters")
    private String deviceFingerprint;

    @Size(max = 64, message = "Transaction context must not exceed 64 characters")
    private String transactionContext;

    // Location data for fraud detection
    private Double latitude;
    private Double longitude;
    
    @Size(max = 10, message = "Location accuracy must not exceed 10 characters")
    private String locationAccuracy;

    // Additional metadata
    @Size(max = 1000, message = "Metadata must not exceed 1000 characters")
    private String metadata;

    /**
     * Validates if the payment request has expired based on timestamp
     * @param expirationMinutes Number of minutes after which request expires
     * @return true if expired, false otherwise
     */
    public boolean isExpired(int expirationMinutes) {
        if (timestamp == null) {
            return true;
        }
        
        Instant expirationTime = timestamp.plusSeconds(expirationMinutes * 60L);
        return Instant.now().isAfter(expirationTime);
    }

    /**
     * Gets the transaction amount in cents for internal processing
     * @return amount in cents
     */
    public long getAmountInCents() {
        return amount != null ? amount.multiply(BigDecimal.valueOf(100)).longValue() : 0L;
    }

    /**
     * Validates if the request has all required security fields
     * @return true if all security fields are present
     */
    public boolean hasRequiredSecurityFields() {
        return signature != null && !signature.trim().isEmpty() &&
               deviceId != null && !deviceId.trim().isEmpty() &&
               timestamp != null;
    }
}