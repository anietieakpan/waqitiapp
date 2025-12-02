package com.waqiti.savings.dto;

import com.waqiti.savings.domain.SavingsAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for SavingsAccount entity.
 * Contains complete account information for API responses.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Savings account details")
public class SavingsAccountResponse {

    @Schema(description = "Unique account identifier", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Account owner user ID")
    private UUID userId;

    @Schema(description = "Unique account number", example = "SAV1234567890")
    private String accountNumber;

    @Schema(description = "Account type", example = "HIGH_YIELD_SAVINGS")
    private SavingsAccount.AccountType accountType;

    @Schema(description = "Current balance", example = "1500.50")
    private BigDecimal balance;

    @Schema(description = "Available balance for withdrawal", example = "1500.50")
    private BigDecimal availableBalance;

    @Schema(description = "Pending deposits", example = "100.00")
    private BigDecimal pendingDeposits;

    @Schema(description = "Pending withdrawals", example = "0.00")
    private BigDecimal pendingWithdrawals;

    @Schema(description = "Account currency code", example = "USD")
    private String currency;

    @Schema(description = "Account status", example = "ACTIVE")
    private SavingsAccount.Status status;

    @Schema(description = "Interest rate percentage", example = "4.50")
    private BigDecimal interestRate;

    @Schema(description = "Interest calculation type", example = "COMPOUND_DAILY")
    private SavingsAccount.InterestCalculationType interestCalculationType;

    @Schema(description = "Total interest earned", example = "45.25")
    private BigDecimal totalInterestEarned;

    @Schema(description = "Last interest calculation timestamp")
    private LocalDateTime lastInterestCalculatedAt;

    @Schema(description = "Next interest calculation timestamp")
    private LocalDateTime nextInterestCalculationAt;

    @Schema(description = "Minimum balance requirement", example = "0.00")
    private BigDecimal minimumBalance;

    @Schema(description = "Maximum balance allowed", example = "50000.00")
    private BigDecimal maximumBalance;

    @Schema(description = "Daily deposit limit", example = "10000.00")
    private BigDecimal dailyDepositLimit;

    @Schema(description = "Daily withdrawal limit", example = "5000.00")
    private BigDecimal dailyWithdrawalLimit;

    @Schema(description = "Monthly withdrawal count limit", example = "6")
    private Integer monthlyWithdrawalCountLimit;

    @Schema(description = "Current month withdrawal count", example = "2")
    private Integer currentMonthWithdrawals;

    @Schema(description = "Total deposits made", example = "5000.00")
    private BigDecimal totalDeposits;

    @Schema(description = "Total withdrawals made", example = "500.00")
    private BigDecimal totalWithdrawals;

    @Schema(description = "Number of deposits", example = "10")
    private Integer depositCount;

    @Schema(description = "Number of withdrawals", example = "3")
    private Integer withdrawalCount;

    @Schema(description = "Last deposit timestamp")
    private LocalDateTime lastDepositAt;

    @Schema(description = "Last withdrawal timestamp")
    private LocalDateTime lastWithdrawalAt;

    @Schema(description = "Overdraft protection enabled")
    private Boolean overdraftEnabled;

    @Schema(description = "Overdraft limit", example = "500.00")
    private BigDecimal overdraftLimit;

    @Schema(description = "Auto-sweep enabled")
    private Boolean autoSweepEnabled;

    @Schema(description = "Auto-sweep threshold", example = "10000.00")
    private BigDecimal autoSweepThreshold;

    @Schema(description = "Auto-sweep target account ID")
    private UUID autoSweepTargetAccountId;

    @Schema(description = "Account settings")
    private Map<String, Object> settings;

    @Schema(description = "Account metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Account creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Created by user")
    private String createdBy;

    @Schema(description = "Last modified by user")
    private String modifiedBy;

    @Schema(description = "Version for optimistic locking")
    private Long version;
}
