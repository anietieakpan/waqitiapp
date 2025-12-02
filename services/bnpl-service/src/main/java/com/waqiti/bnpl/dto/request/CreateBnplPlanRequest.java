package com.waqiti.bnpl.dto.request;

import com.waqiti.bnpl.domain.enums.PaymentFrequency;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating a BNPL plan
 * Production-grade validation for all plan creation parameters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBnplPlanRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotBlank(message = "Merchant name is required")
    @Size(min = 2, max = 255, message = "Merchant name must be between 2 and 255 characters")
    private String merchantName;

    @NotBlank(message = "Order reference is required")
    @Size(min = 5, max = 100, message = "Order reference must be between 5 and 100 characters")
    @Pattern(regexp = "^[A-Z0-9\\-_]+$", message = "Order reference must contain only uppercase letters, numbers, hyphens, and underscores")
    private String orderReference;

    @NotNull(message = "Purchase amount is required")
    @DecimalMin(value = "50.0000", inclusive = true, message = "Minimum purchase amount is $50.00")
    @DecimalMax(value = "50000.0000", inclusive = true, message = "Maximum purchase amount is $50,000.00")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format (max 15 digits, 4 decimals)")
    private BigDecimal purchaseAmount;

    @NotNull(message = "Down payment is required")
    @DecimalMin(value = "0.0000", inclusive = true, message = "Down payment cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid down payment format")
    private BigDecimal downPayment;

    @NotNull(message = "Number of installments is required")
    @Min(value = 2, message = "Minimum 2 installments required")
    @Max(value = 24, message = "Maximum 24 installments allowed")
    private Integer numberOfInstallments;

    @NotNull(message = "Payment frequency is required")
    private PaymentFrequency paymentFrequency;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotNull(message = "Terms acceptance is required")
    @AssertTrue(message = "Terms and conditions must be accepted to proceed")
    private Boolean termsAccepted;

    // Optional metadata with validation
    @Pattern(regexp = "^[A-Z0-9]{2,10}$", message = "Category code must be 2-10 uppercase alphanumeric characters")
    private String categoryCode;

    @Size(max = 100, message = "Product type must not exceed 100 characters")
    private String productType;

    @Pattern(regexp = "^(PREMIUM|STANDARD|BASIC|VIP)$", message = "Customer segment must be one of: PREMIUM, STANDARD, BASIC, VIP")
    private String customerSegment;

    /**
     * Calculate finance amount (purchase amount - down payment)
     */
    public BigDecimal getFinanceAmount() {
        if (purchaseAmount == null || downPayment == null) {
            return BigDecimal.ZERO;
        }
        return purchaseAmount.subtract(downPayment);
    }

    /**
     * Custom validation: Down payment cannot exceed purchase amount
     */
    @AssertTrue(message = "Down payment cannot exceed purchase amount")
    public boolean isDownPaymentValid() {
        if (purchaseAmount == null || downPayment == null) {
            return true; // Let @NotNull handle null validation
        }
        return downPayment.compareTo(purchaseAmount) <= 0;
    }

    /**
     * Custom validation: Down payment must be at least 10% for high-value purchases
     */
    @AssertTrue(message = "Down payment must be at least 10% for purchases over $10,000")
    public boolean isMinimumDownPaymentValid() {
        if (purchaseAmount == null || downPayment == null) {
            return true;
        }

        // For purchases over $10,000, require at least 10% down payment
        BigDecimal threshold = new BigDecimal("10000.0000");
        if (purchaseAmount.compareTo(threshold) > 0) {
            BigDecimal minimumDownPayment = purchaseAmount.multiply(new BigDecimal("0.10"));
            return downPayment.compareTo(minimumDownPayment) >= 0;
        }

        return true;
    }

    /**
     * Custom validation: Finance amount must be positive
     */
    @AssertTrue(message = "Financed amount must be greater than zero")
    public boolean isFinanceAmountPositive() {
        if (purchaseAmount == null || downPayment == null) {
            return true;
        }
        BigDecimal financeAmount = getFinanceAmount();
        return financeAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Custom validation: Payment frequency must match installment count
     */
    @AssertTrue(message = "Payment frequency must be compatible with installment count")
    public boolean isPaymentFrequencyValid() {
        if (paymentFrequency == null || numberOfInstallments == null) {
            return true;
        }

        // Weekly: max 52 installments (1 year)
        if (paymentFrequency == PaymentFrequency.WEEKLY && numberOfInstallments > 52) {
            return false;
        }

        // Bi-weekly: max 26 installments (1 year)
        if (paymentFrequency == PaymentFrequency.BIWEEKLY && numberOfInstallments > 26) {
            return false;
        }

        // Monthly: max 24 installments (2 years)
        if (paymentFrequency == PaymentFrequency.MONTHLY && numberOfInstallments > 24) {
            return false;
        }

        return true;
    }
}