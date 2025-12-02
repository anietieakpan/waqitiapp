package com.waqiti.bnpl.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for credit check
 * Production-grade validation for credit assessment requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCheckRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Requested amount is required")
    @DecimalMin(value = "50.0000", inclusive = true, message = "Minimum credit check amount is $50.00")
    @DecimalMax(value = "100000.0000", inclusive = true, message = "Maximum credit check amount is $100,000.00")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format (max 15 digits, 4 decimals)")
    private BigDecimal requestedAmount;

    @NotBlank(message = "Purpose is required")
    @Pattern(regexp = "^(PURCHASE|BILL_PAYMENT|DEBT_CONSOLIDATION|EMERGENCY|HOME_IMPROVEMENT|EDUCATION|MEDICAL|TRAVEL|OTHER)$",
             message = "Purpose must be one of: PURCHASE, BILL_PAYMENT, DEBT_CONSOLIDATION, EMERGENCY, HOME_IMPROVEMENT, EDUCATION, MEDICAL, TRAVEL, OTHER")
    private String purpose;

    private UUID merchantId;

    @Min(value = 2, message = "Minimum 2 installments required")
    @Max(value = 60, message = "Maximum 60 installments allowed for credit check")
    private Integer requestedInstallments;

    // Additional data for enhanced credit scoring
    @DecimalMin(value = "0.0000", inclusive = true, message = "Monthly income cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid monthly income format")
    private BigDecimal monthlyIncome;

    @DecimalMin(value = "0.0000", inclusive = true, message = "Monthly expenses cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid monthly expenses format")
    private BigDecimal monthlyExpenses;

    @Pattern(regexp = "^(EMPLOYED_FULL_TIME|EMPLOYED_PART_TIME|SELF_EMPLOYED|UNEMPLOYED|RETIRED|STUDENT)$",
             message = "Employment status must be one of: EMPLOYED_FULL_TIME, EMPLOYED_PART_TIME, SELF_EMPLOYED, UNEMPLOYED, RETIRED, STUDENT")
    private String employmentStatus;

    @Min(value = 0, message = "Employment months cannot be negative")
    @Max(value = 600, message = "Employment months cannot exceed 600 (50 years)")
    private Integer employmentMonths;

    @DecimalMin(value = "0.0000", inclusive = true, message = "Existing debt cannot be negative")
    @Digits(integer = 15, fraction = 4, message = "Invalid existing debt format")
    private BigDecimal existingDebt;

    @Size(max = 500, message = "Additional notes must not exceed 500 characters")
    private String notes;

    /**
     * Custom validation: Monthly expenses should not exceed monthly income
     */
    @AssertTrue(message = "Monthly expenses cannot exceed monthly income significantly")
    public boolean isIncomeExpenseRatioValid() {
        if (monthlyIncome == null || monthlyExpenses == null) {
            return true; // Optional fields
        }

        // Allow expenses up to 120% of income (some buffer for credit)
        BigDecimal maxExpenses = monthlyIncome.multiply(new BigDecimal("1.20"));
        return monthlyExpenses.compareTo(maxExpenses) <= 0;
    }

    /**
     * Custom validation: Debt-to-income ratio should be reasonable
     */
    @AssertTrue(message = "Total debt burden (existing + requested) exceeds safe lending limits")
    public boolean isDebtToIncomeValid() {
        if (monthlyIncome == null || requestedAmount == null) {
            return true;
        }

        // Calculate monthly payment for requested amount (assume 12 months)
        BigDecimal installments = requestedInstallments != null ?
                new BigDecimal(requestedInstallments) : new BigDecimal("12");
        BigDecimal estimatedMonthlyPayment = requestedAmount.divide(installments, 4, java.math.RoundingMode.HALF_UP);

        // Add existing expenses
        BigDecimal totalMonthlyObligation = monthlyExpenses != null ?
                monthlyExpenses.add(estimatedMonthlyPayment) : estimatedMonthlyPayment;

        // Debt-to-income should not exceed 50%
        BigDecimal maxDebt = monthlyIncome.multiply(new BigDecimal("0.50"));
        return totalMonthlyObligation.compareTo(maxDebt) <= 0;
    }
}