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
 * Request DTO for approving a BNPL plan
 * Production-grade validation for plan approval process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovePlanRequest {

    @NotNull(message = "Plan ID is required")
    private UUID planId;

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Approved amount is required")
    @DecimalMin(value = "50.0000", inclusive = true, message = "Minimum approved amount is $50.00")
    @DecimalMax(value = "100000.0000", inclusive = true, message = "Maximum approved amount is $100,000.00")
    @Digits(integer = 15, fraction = 4, message = "Invalid approved amount format")
    private BigDecimal approvedAmount;

    @NotNull(message = "Approved term (months) is required")
    @Min(value = 2, message = "Minimum approved term is 2 months")
    @Max(value = 60, message = "Maximum approved term is 60 months")
    private Integer approvedTermMonths;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.0000", inclusive = true, message = "Interest rate cannot be negative")
    @DecimalMax(value = "36.0000", inclusive = true, message = "Interest rate cannot exceed 36% (usury limit)")
    @Digits(integer = 2, fraction = 4, message = "Invalid interest rate format")
    private BigDecimal interestRate;

    @NotBlank(message = "Payment method is required for down payment")
    @Pattern(regexp = "^(CREDIT_CARD|DEBIT_CARD|BANK_TRANSFER|WALLET|ACH)$",
             message = "Payment method must be one of: CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, WALLET, ACH")
    private String paymentMethod;

    @Size(max = 2000, message = "Approval notes must not exceed 2000 characters")
    private String approvalNotes;

    @Size(max = 500, message = "Terms and conditions must not exceed 500 characters")
    private String termsAndConditions;

    @NotNull(message = "Requires manual review flag is required")
    private Boolean requiresManualReview = false;

    @NotNull(message = "Credit score used for approval is required")
    @Min(value = 300, message = "Credit score must be at least 300")
    @Max(value = 850, message = "Credit score cannot exceed 850")
    private Integer creditScoreUsed;

    @NotBlank(message = "Risk tier is required")
    @Pattern(regexp = "^(LOW|MEDIUM|HIGH|VERY_HIGH)$",
             message = "Risk tier must be one of: LOW, MEDIUM, HIGH, VERY_HIGH")
    private String riskTier;

    // Security and audit fields
    @NotBlank(message = "Approved by user ID is required")
    @Size(max = 100, message = "Approved by field must not exceed 100 characters")
    private String approvedBy;

    @NotBlank(message = "IP address is required for audit trail")
    @Pattern(regexp = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$",
             message = "Invalid IP address format")
    private String ipAddress;

    @Size(max = 255, message = "Device ID must not exceed 255 characters")
    private String deviceId;

    @Size(max = 1000, message = "User agent must not exceed 1000 characters")
    private String userAgent;

    // Optional conditions
    @Size(max = 1000, message = "Special conditions must not exceed 1000 characters")
    private String specialConditions;

    @DecimalMin(value = "0.0000", inclusive = true, message = "Processing fee cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid processing fee format")
    private BigDecimal processingFee;

    @DecimalMin(value = "0.0000", inclusive = true, message = "Late payment fee cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid late payment fee format")
    private BigDecimal latePaymentFee;

    // Optional metadata
    @Size(max = 10, message = "Metadata cannot exceed 10 entries")
    private Map<String, String> metadata;

    /**
     * Custom validation: Interest rate must be appropriate for risk tier
     */
    @AssertTrue(message = "Interest rate does not match risk tier guidelines")
    public boolean isInterestRateAppropriate() {
        if (riskTier == null || interestRate == null) {
            return true;
        }

        switch (riskTier) {
            case "LOW":
                // Low risk: 0-10% interest
                return interestRate.compareTo(new BigDecimal("10.0000")) <= 0;
            case "MEDIUM":
                // Medium risk: 10-20% interest
                return interestRate.compareTo(new BigDecimal("20.0000")) <= 0;
            case "HIGH":
                // High risk: 20-30% interest
                return interestRate.compareTo(new BigDecimal("30.0000")) <= 0;
            case "VERY_HIGH":
                // Very high risk: 30-36% interest (legal limit)
                return interestRate.compareTo(new BigDecimal("36.0000")) <= 0;
            default:
                return true;
        }
    }

    /**
     * Custom validation: Term must be reasonable for approved amount
     */
    @AssertTrue(message = "Approved term is not appropriate for the approved amount")
    public boolean isTermAppropriate() {
        if (approvedAmount == null || approvedTermMonths == null) {
            return true;
        }

        // For amounts under $1000, max 12 months
        if (approvedAmount.compareTo(new BigDecimal("1000.0000")) < 0) {
            return approvedTermMonths <= 12;
        }

        // For amounts $1000-$5000, max 24 months
        if (approvedAmount.compareTo(new BigDecimal("5000.0000")) < 0) {
            return approvedTermMonths <= 24;
        }

        // For amounts $5000-$20000, max 36 months
        if (approvedAmount.compareTo(new BigDecimal("20000.0000")) < 0) {
            return approvedTermMonths <= 36;
        }

        // For amounts over $20000, max 60 months
        return approvedTermMonths <= 60;
    }

    /**
     * Custom validation: Credit score must meet minimum for risk tier
     */
    @AssertTrue(message = "Credit score does not meet minimum requirement for approved risk tier")
    public boolean isCreditScoreSufficient() {
        if (creditScoreUsed == null || riskTier == null) {
            return true;
        }

        switch (riskTier) {
            case "LOW":
                return creditScoreUsed >= 720; // Excellent credit
            case "MEDIUM":
                return creditScoreUsed >= 650; // Good credit
            case "HIGH":
                return creditScoreUsed >= 580; // Fair credit
            case "VERY_HIGH":
                return creditScoreUsed >= 500; // Poor credit (subprime)
            default:
                return true;
        }
    }
}