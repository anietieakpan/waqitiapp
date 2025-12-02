package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive account information for fraud assessment with full validation and risk metrics
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class AccountInfo {
    private String accountNumber;
    private String accountType;
    private BigDecimal balance;
    private String bankCode;
    private String branchCode;
    private LocalDate accountOpenDate;
    private String accountStatus;
    private BigDecimal monthlyAverageBalance;
    private int transactionCountLastMonth;
    private boolean isDormant;
    private String riskLevel;
    private BigDecimal creditLimit;
    private BigDecimal availableCredit;
    private LocalDateTime lastTransactionDate;
    private List<String> linkedAccounts;
    private Map<String, Object> accountMetadata;
    private String customerSegment;
    private boolean isJointAccount;
    private List<String> authorizedUsers;
    private String complianceStatus;
    private BigDecimal maximumDailyLimit;
    private BigDecimal maximumMonthlyLimit;
    private int failedLoginAttempts;
    private LocalDateTime lastPasswordChange;
    private boolean twoFactorEnabled;
    private String preferredCurrency;
    private String timeZone;
}