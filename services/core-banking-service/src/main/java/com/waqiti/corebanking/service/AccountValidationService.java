package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.dto.AccountCreationRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountValidationService {

    private static final List<String> SUPPORTED_CURRENCIES = Arrays.asList("USD", "EUR", "GBP", "CAD", "JPY");
    private static final BigDecimal MAX_INITIAL_BALANCE = new BigDecimal("1000000.00");
    private static final BigDecimal MAX_CREDIT_LIMIT = new BigDecimal("100000.00");
    private static final BigDecimal MAX_DAILY_LIMIT = new BigDecimal("100000.00");

    /**
     * Validates account creation request
     */
    public void validateAccountCreationRequest(AccountCreationRequestDto request) {
        log.debug("Validating account creation request for user: {}", request.getUserId());

        // Validate user ID
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }

        // Validate account type
        validateAccountType(request.getAccountType());

        // Validate currency
        validateCurrency(request.getCurrency());

        // Validate initial balance
        validateInitialBalance(request.getInitialBalance(), request.getAccountType());

        // Validate credit limit
        validateCreditLimit(request.getCreditLimit(), request.getAccountType());

        // Validate transaction limits
        validateTransactionLimits(request.getDailyLimit(), request.getMonthlyLimit(), request.getAccountType());

        // Validate compliance level
        validateComplianceLevel(request.getComplianceLevel());

        // Validate description length
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            throw new IllegalArgumentException("Description cannot exceed 500 characters");
        }

        log.debug("Account creation request validation passed for user: {}", request.getUserId());
    }

    /**
     * Validates account status transition
     */
    public void validateStatusTransition(Account.AccountStatus currentStatus, Account.AccountStatus newStatus) {
        log.debug("Validating status transition: {} -> {}", currentStatus, newStatus);

        if (currentStatus == newStatus) {
            throw new IllegalArgumentException("Account is already in status: " + currentStatus);
        }

        // Define valid transitions
        switch (currentStatus) {
            case PENDING:
                if (newStatus != Account.AccountStatus.ACTIVE && 
                    newStatus != Account.AccountStatus.CLOSED) {
                    throw new IllegalStateException("Invalid transition from PENDING to " + newStatus);
                }
                break;

            case ACTIVE:
                if (newStatus != Account.AccountStatus.SUSPENDED && 
                    newStatus != Account.AccountStatus.FROZEN &&
                    newStatus != Account.AccountStatus.DORMANT &&
                    newStatus != Account.AccountStatus.CLOSED) {
                    throw new IllegalStateException("Invalid transition from ACTIVE to " + newStatus);
                }
                break;

            case SUSPENDED:
                if (newStatus != Account.AccountStatus.ACTIVE && 
                    newStatus != Account.AccountStatus.CLOSED) {
                    throw new IllegalStateException("Invalid transition from SUSPENDED to " + newStatus);
                }
                break;

            case FROZEN:
                if (newStatus != Account.AccountStatus.ACTIVE && 
                    newStatus != Account.AccountStatus.CLOSED) {
                    throw new IllegalStateException("Invalid transition from FROZEN to " + newStatus);
                }
                break;

            case DORMANT:
                if (newStatus != Account.AccountStatus.ACTIVE && 
                    newStatus != Account.AccountStatus.CLOSED) {
                    throw new IllegalStateException("Invalid transition from DORMANT to " + newStatus);
                }
                break;

            case CLOSED:
                throw new IllegalStateException("Cannot transition from CLOSED status");

            default:
                throw new IllegalStateException("Unknown account status: " + currentStatus);
        }

        log.debug("Status transition validation passed: {} -> {}", currentStatus, newStatus);
    }

    /**
     * Validates if account can perform transaction
     */
    public void validateTransactionEligibility(Account account, BigDecimal amount, String operation) {
        log.debug("Validating transaction eligibility for account: {} (operation: {}, amount: {})", 
                 account.getAccountId(), operation, amount);

        // Check account status
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active: " + account.getStatus());
        }

        // Check if account is frozen
        if (account.getIsFrozen()) {
            throw new IllegalStateException("Account is frozen");
        }

        // Check daily limits
        if (amount.compareTo(account.getDailyLimit()) > 0) {
            throw new IllegalArgumentException("Transaction amount exceeds daily limit");
        }

        // Check compliance level restrictions
        validateComplianceRestrictions(account, amount, operation);

        log.debug("Transaction eligibility validation passed for account: {}", account.getAccountId());
    }

    /**
     * Validates account balance operations
     */
    public void validateBalanceOperation(Account account, BigDecimal amount, String operation) {
        log.debug("Validating balance operation for account: {} (operation: {}, amount: {})", 
                 account.getAccountId(), operation, amount);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if ("DEBIT".equals(operation)) {
            // For debit operations, check available balance
            if (!account.hasAvailableBalance(amount) && !account.getAllowOverdraft()) {
                throw new IllegalArgumentException("Insufficient available balance");
            }

            // Check overdraft limits
            if (account.getAllowOverdraft() && account.getCreditLimit() != null) {
                BigDecimal totalDebt = account.getCurrentBalance().subtract(amount);
                if (totalDebt.abs().compareTo(account.getCreditLimit()) > 0) {
                    throw new IllegalArgumentException("Transaction would exceed credit limit");
                }
            }
        }

        log.debug("Balance operation validation passed for account: {}", account.getAccountId());
    }

    private void validateAccountType(String accountType) {
        if (accountType == null || accountType.trim().isEmpty()) {
            throw new IllegalArgumentException("Account type is required");
        }

        try {
            Account.AccountType.valueOf(accountType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid account type: " + accountType);
        }
    }

    private void validateCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }

        if (currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be 3 characters");
        }

        if (!SUPPORTED_CURRENCIES.contains(currency.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
    }

    private void validateInitialBalance(BigDecimal initialBalance, String accountType) {
        if (initialBalance != null) {
            if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Initial balance cannot be negative");
            }

            if (initialBalance.compareTo(MAX_INITIAL_BALANCE) > 0) {
                throw new IllegalArgumentException("Initial balance exceeds maximum allowed");
            }

            // Credit accounts typically start with zero balance
            Account.AccountType type = Account.AccountType.valueOf(accountType);
            if (type == Account.AccountType.USER_CREDIT && 
                initialBalance.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("Credit accounts cannot have positive initial balance");
            }
        }
    }

    private void validateCreditLimit(BigDecimal creditLimit, String accountType) {
        if (creditLimit != null) {
            if (creditLimit.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Credit limit cannot be negative");
            }

            if (creditLimit.compareTo(MAX_CREDIT_LIMIT) > 0) {
                throw new IllegalArgumentException("Credit limit exceeds maximum allowed");
            }

            // Only certain account types can have credit limits
            Account.AccountType type = Account.AccountType.valueOf(accountType);
            if (type != Account.AccountType.USER_CREDIT && 
                type != Account.AccountType.BUSINESS_OPERATING &&
                creditLimit.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("Account type does not support credit limit");
            }
        }
    }

    private void validateTransactionLimits(BigDecimal dailyLimit, BigDecimal monthlyLimit, String accountType) {
        if (dailyLimit != null) {
            if (dailyLimit.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Daily limit must be positive");
            }

            if (dailyLimit.compareTo(MAX_DAILY_LIMIT) > 0) {
                throw new IllegalArgumentException("Daily limit exceeds maximum allowed");
            }
        }

        if (monthlyLimit != null) {
            if (monthlyLimit.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Monthly limit must be positive");
            }

            // Monthly limit should be at least as high as daily limit
            if (dailyLimit != null && monthlyLimit.compareTo(dailyLimit) < 0) {
                throw new IllegalArgumentException("Monthly limit cannot be less than daily limit");
            }
        }
    }

    private void validateComplianceLevel(String complianceLevel) {
        if (complianceLevel != null) {
            try {
                Account.ComplianceLevel.valueOf(complianceLevel);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid compliance level: " + complianceLevel);
            }
        }
    }

    private void validateComplianceRestrictions(Account account, BigDecimal amount, String operation) {
        // Implement compliance-specific restrictions based on account compliance level
        switch (account.getComplianceLevel()) {
            case BASIC:
                if (amount.compareTo(new BigDecimal("1000.00")) > 0) {
                    throw new IllegalArgumentException("BASIC compliance level restricts transactions over $1,000");
                }
                break;

            case STANDARD:
                if (amount.compareTo(new BigDecimal("10000.00")) > 0) {
                    throw new IllegalArgumentException("STANDARD compliance level restricts transactions over $10,000");
                }
                break;

            case ENHANCED:
                if (amount.compareTo(new BigDecimal("50000.00")) > 0) {
                    throw new IllegalArgumentException("ENHANCED compliance level restricts transactions over $50,000");
                }
                break;

            case PREMIUM:
                // No restrictions for premium compliance level
                break;

            case RESTRICTED:
                throw new IllegalArgumentException("Account has RESTRICTED compliance level - transactions not allowed");

            default:
                throw new IllegalArgumentException("Unknown compliance level: " + account.getComplianceLevel());
        }
    }
}