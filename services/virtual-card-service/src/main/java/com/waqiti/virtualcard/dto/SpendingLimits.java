package com.waqiti.virtualcard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for card spending limits
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingLimits {
    
    @DecimalMin(value = "0.0", inclusive = false, message = "Daily limit must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Daily limit must be a valid amount")
    private BigDecimal dailyLimit;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "Weekly limit must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Weekly limit must be a valid amount")
    private BigDecimal weeklyLimit;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "Monthly limit must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Monthly limit must be a valid amount")
    private BigDecimal monthlyLimit;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "Yearly limit must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Yearly limit must be a valid amount")
    private BigDecimal yearlyLimit;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "Transaction limit must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Transaction limit must be a valid amount")
    private BigDecimal transactionLimit;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "ATM withdrawal limit must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "ATM withdrawal limit must be a valid amount")
    private BigDecimal atmWithdrawalLimit;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "Online limit must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Online limit must be a valid amount")
    private BigDecimal onlineLimit;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "International limit must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "International limit must be a valid amount")
    private BigDecimal internationalLimit;
    
    @DecimalMin(value = "0.0", inclusive = false, message = "Contactless limit must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Contactless limit must be a valid amount")
    private BigDecimal contactlessLimit;
    
    // Usage tracking
    private BigDecimal dailySpent;
    
    private BigDecimal weeklySpent;
    
    private BigDecimal monthlySpent;
    
    private BigDecimal yearlySpent;
    
    // Transaction count limits
    private Integer maxDailyTransactions;
    
    private Integer maxWeeklyTransactions;
    
    private Integer maxMonthlyTransactions;
    
    // Current usage counts
    private Integer dailyTransactionCount;
    
    private Integer weeklyTransactionCount;
    
    private Integer monthlyTransactionCount;
}