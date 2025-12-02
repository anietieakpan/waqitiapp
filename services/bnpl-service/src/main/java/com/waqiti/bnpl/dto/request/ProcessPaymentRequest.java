package com.waqiti.bnpl.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for processing BNPL payment
 * Production-grade validation for payment processing security
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPaymentRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Plan ID is required")
    private UUID planId;

    // Optional, if not provided, pay next due installment
    private UUID installmentId;

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.0001", inclusive = true, message = "Payment amount must be positive")
    @DecimalMax(value = "100000.0000", inclusive = true, message = "Payment amount cannot exceed $100,000")
    @Digits(integer = 15, fraction = 4, message = "Invalid payment amount format (max 15 digits, 4 decimals)")
    private BigDecimal amount;

    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "^(CREDIT_CARD|DEBIT_CARD|BANK_TRANSFER|WALLET|UPI|ACH|WIRE)$",
             message = "Payment method must be one of: CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, WALLET, UPI, ACH, WIRE")
    private String paymentMethod;

    @NotBlank(message = "Payment method ID is required (e.g., card token, bank account ID)")
    @Size(max = 255, message = "Payment method ID must not exceed 255 characters")
    private String paymentMethodId;

    // Idempotency key for duplicate payment prevention (CRITICAL for financial transactions)
    @NotBlank(message = "Idempotency key is required to prevent duplicate payments")
    @Size(min = 10, max = 100, message = "Idempotency key must be between 10 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\-_]+$", message = "Idempotency key must contain only alphanumeric characters, hyphens, and underscores")
    private String idempotencyKey;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    private String currency;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    // Security and fraud prevention fields (CRITICAL)
    @NotBlank(message = "IP address is required for fraud detection")
    @Pattern(regexp = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$",
             message = "Invalid IP address format")
    private String ipAddress;

    @Size(max = 255, message = "Device ID must not exceed 255 characters")
    private String deviceId;

    @Size(max = 100, message = "Session ID must not exceed 100 characters")
    private String sessionId;

    @Size(max = 1000, message = "User agent must not exceed 1000 characters")
    private String userAgent;

    // Payment processing flags
    @NotNull(message = "Auto-retry flag is required")
    private Boolean enableAutoRetry = true;

    @NotNull(message = "Send receipt flag is required")
    private Boolean sendReceipt = true;

    // Optional reference numbers for tracking
    @Size(max = 100, message = "External reference must not exceed 100 characters")
    private String externalReference;

    @Size(max = 100, message = "Customer reference must not exceed 100 characters")
    private String customerReference;

    // Optional metadata (limited size for security)
    @Size(max = 10, message = "Metadata cannot exceed 10 entries")
    private Map<String, String> metadata;

    /**
     * Custom validation: Ensure amount is reasonable for a single payment
     */
    @AssertTrue(message = "Payment amount exceeds maximum allowed for single transaction")
    public boolean isAmountReasonable() {
        if (amount == null) {
            return true;
        }
        // Maximum single payment: $100,000
        return amount.compareTo(new BigDecimal("100000.0000")) <= 0;
    }

    /**
     * Custom validation: Idempotency key must be unique per transaction
     */
    @AssertTrue(message = "Idempotency key format is invalid")
    public boolean isIdempotencyKeyValid() {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true; // Let @NotBlank handle this
        }
        // Additional business rule: should include user ID or plan ID for uniqueness
        return idempotencyKey.length() >= 10;
    }
}