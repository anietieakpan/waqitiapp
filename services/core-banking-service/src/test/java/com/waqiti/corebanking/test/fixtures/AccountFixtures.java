package com.waqiti.corebanking.test.fixtures;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.Account.AccountCategory;
import com.waqiti.corebanking.domain.Account.AccountStatus;
import com.waqiti.corebanking.domain.Account.AccountType;
import com.waqiti.corebanking.domain.Account.ComplianceLevel;
import com.waqiti.corebanking.domain.Account.KycStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test fixtures for Account entities
 *
 * Provides builder pattern for creating test accounts with sensible defaults.
 * All financial amounts use proper BigDecimal precision.
 */
public class AccountFixtures {

    /**
     * Creates a standard user wallet account with default values
     */
    public static Account createStandardUserWallet() {
        return Account.builder()
                .accountId(UUID.randomUUID())
                .accountNumber(generateAccountNumber())
                .userId(UUID.randomUUID())
                .accountName("Test User Wallet")
                .accountType(AccountType.USER_WALLET)
                .accountCategory(AccountCategory.ASSET)
                .status(AccountStatus.ACTIVE)
                .currency("USD")
                .currentBalance(new BigDecimal("1000.0000"))
                .availableBalance(new BigDecimal("1000.0000"))
                .pendingBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .creditLimit(BigDecimal.ZERO)
                .dailyTransactionLimit(new BigDecimal("5000.0000"))
                .monthlyTransactionLimit(new BigDecimal("50000.0000"))
                .minimumBalance(BigDecimal.ZERO)
                .maximumBalance(new BigDecimal("100000.0000"))
                .complianceLevel(ComplianceLevel.STANDARD)
                .kycStatus(KycStatus.VERIFIED)
                .riskScore(10)
                .isJointAccount(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0)
                .build();
    }

    /**
     * Creates a savings account with interest
     */
    public static Account createSavingsAccount() {
        return Account.builder()
                .accountId(UUID.randomUUID())
                .accountNumber(generateAccountNumber())
                .userId(UUID.randomUUID())
                .accountName("Test Savings Account")
                .accountType(AccountType.SAVINGS)
                .accountCategory(AccountCategory.ASSET)
                .status(AccountStatus.ACTIVE)
                .currency("USD")
                .currentBalance(new BigDecimal("5000.0000"))
                .availableBalance(new BigDecimal("5000.0000"))
                .pendingBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .minimumBalance(new BigDecimal("100.0000"))
                .dailyTransactionLimit(new BigDecimal("2000.0000"))
                .monthlyTransactionLimit(new BigDecimal("20000.0000"))
                .interestRate(new BigDecimal("0.5000")) // 0.5% annual
                .complianceLevel(ComplianceLevel.ENHANCED)
                .kycStatus(KycStatus.VERIFIED)
                .riskScore(5)
                .isJointAccount(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0)
                .build();
    }

    /**
     * Creates a business account
     */
    public static Account createBusinessAccount() {
        return Account.builder()
                .accountId(UUID.randomUUID())
                .accountNumber(generateAccountNumber())
                .userId(UUID.randomUUID())
                .accountName("Test Business Account")
                .accountType(AccountType.BUSINESS_OPERATING)
                .accountCategory(AccountCategory.ASSET)
                .status(AccountStatus.ACTIVE)
                .currency("USD")
                .currentBalance(new BigDecimal("50000.0000"))
                .availableBalance(new BigDecimal("50000.0000"))
                .pendingBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .dailyTransactionLimit(new BigDecimal("100000.0000"))
                .monthlyTransactionLimit(new BigDecimal("1000000.0000"))
                .minimumBalance(new BigDecimal("1000.0000"))
                .complianceLevel(ComplianceLevel.PREMIUM)
                .kycStatus(KycStatus.VERIFIED)
                .riskScore(3)
                .isJointAccount(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0)
                .build();
    }

    /**
     * Creates an account with insufficient funds
     */
    public static Account createLowBalanceAccount() {
        return Account.builder()
                .accountId(UUID.randomUUID())
                .accountNumber(generateAccountNumber())
                .userId(UUID.randomUUID())
                .accountName("Low Balance Account")
                .accountType(AccountType.USER_WALLET)
                .accountCategory(AccountCategory.ASSET)
                .status(AccountStatus.ACTIVE)
                .currency("USD")
                .currentBalance(new BigDecimal("10.0000"))
                .availableBalance(new BigDecimal("10.0000"))
                .pendingBalance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .dailyTransactionLimit(new BigDecimal("500.0000"))
                .monthlyTransactionLimit(new BigDecimal("5000.0000"))
                .complianceLevel(ComplianceLevel.BASIC)
                .kycStatus(KycStatus.VERIFIED)
                .riskScore(15)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0)
                .build();
    }

    /**
     * Creates a suspended account
     */
    public static Account createSuspendedAccount() {
        Account account = createStandardUserWallet();
        account.setStatus(AccountStatus.SUSPENDED);
        account.setComplianceLevel(ComplianceLevel.RESTRICTED);
        account.setRiskScore(75);
        return account;
    }

    /**
     * Creates a frozen account
     */
    public static Account createFrozenAccount() {
        Account account = createStandardUserWallet();
        account.setStatus(AccountStatus.FROZEN);
        account.setComplianceLevel(ComplianceLevel.MONITORED);
        account.setRiskScore(85);
        return account;
    }

    /**
     * Creates an account with reserved funds
     */
    public static Account createAccountWithReservation() {
        Account account = createStandardUserWallet();
        account.setReservedBalance(new BigDecimal("500.0000"));
        account.setAvailableBalance(new BigDecimal("500.0000")); // 1000 - 500
        return account;
    }

    /**
     * Creates an account with pending balance
     */
    public static Account createAccountWithPending() {
        Account account = createStandardUserWallet();
        account.setPendingBalance(new BigDecimal("200.0000"));
        return account;
    }

    /**
     * Builder class for custom account creation
     */
    public static class AccountBuilder {
        private Account account;

        private AccountBuilder() {
            account = createStandardUserWallet();
        }

        public static AccountBuilder builder() {
            return new AccountBuilder();
        }

        public AccountBuilder withAccountId(UUID accountId) {
            account.setAccountId(accountId);
            return this;
        }

        public AccountBuilder withUserId(UUID userId) {
            account.setUserId(userId);
            return this;
        }

        public AccountBuilder withAccountNumber(String accountNumber) {
            account.setAccountNumber(accountNumber);
            return this;
        }

        public AccountBuilder withBalance(BigDecimal balance) {
            account.setCurrentBalance(balance);
            account.setAvailableBalance(balance);
            return this;
        }

        public AccountBuilder withStatus(AccountStatus status) {
            account.setStatus(status);
            return this;
        }

        public AccountBuilder withCurrency(String currency) {
            account.setCurrency(currency);
            return this;
        }

        public AccountBuilder withAccountType(AccountType type) {
            account.setAccountType(type);
            return this;
        }

        public AccountBuilder withComplianceLevel(ComplianceLevel level) {
            account.setComplianceLevel(level);
            return this;
        }

        public AccountBuilder withKycStatus(KycStatus status) {
            account.setKycStatus(status);
            return this;
        }

        public AccountBuilder withDailyLimit(BigDecimal limit) {
            account.setDailyTransactionLimit(limit);
            return this;
        }

        public AccountBuilder withMonthlyLimit(BigDecimal limit) {
            account.setMonthlyTransactionLimit(limit);
            return this;
        }

        public Account build() {
            return account;
        }
    }

    /**
     * Generates a random account number (10 digits)
     */
    private static String generateAccountNumber() {
        long number = (long) (Math.random() * 9000000000L) + 1000000000L;
        return String.valueOf(number);
    }
}
