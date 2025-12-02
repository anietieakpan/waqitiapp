package com.waqiti.common.domain.exceptions;

import com.waqiti.common.domain.valueobjects.AccountNumber;
import com.waqiti.common.domain.Money;
import com.waqiti.common.domain.valueobjects.UserId;

/**
 * Account Domain Exceptions
 * Domain-specific exceptions for account management
 */
public class AccountDomainExceptions {
    
    private static final String ACCOUNT_DOMAIN = "ACCOUNT";
    
    /**
     * Account Not Found Exception
     */
    public static class AccountNotFoundException extends DomainException {
        
        private final String accountId;
        
        public AccountNotFoundException(String accountId) {
            super(String.format("Account not found: %s", accountId),
                    "ACCOUNT_NOT_FOUND",
                    ACCOUNT_DOMAIN);
            this.accountId = accountId;
        }
        
        public AccountNotFoundException(AccountNumber accountNumber) {
            this(accountNumber.getValue());
        }
        
        public String getAccountId() {
            return accountId;
        }
    }
    
    /**
     * Account Already Exists Exception
     */
    public static class AccountAlreadyExistsException extends DomainException {
        
        private final AccountNumber accountNumber;
        private final UserId userId;
        
        public AccountAlreadyExistsException(AccountNumber accountNumber, UserId userId) {
            super(String.format("Account %s already exists for user %s", accountNumber, userId),
                    "ACCOUNT_EXISTS",
                    ACCOUNT_DOMAIN);
            this.accountNumber = accountNumber;
            this.userId = userId;
        }
        
        public AccountNumber getAccountNumber() {
            return accountNumber;
        }
        
        public UserId getUserId() {
            return userId;
        }
    }
    
    /**
     * Account Locked Exception
     */
    public static class AccountLockedException extends DomainException {
        
        private final String accountId;
        private final String reason;
        private final java.time.Instant lockedUntil;
        
        public AccountLockedException(String accountId, String reason, java.time.Instant lockedUntil) {
            super(String.format("Account %s is locked: %s", accountId, reason),
                    "ACCOUNT_LOCKED",
                    ACCOUNT_DOMAIN);
            this.accountId = accountId;
            this.reason = reason;
            this.lockedUntil = lockedUntil;
        }
        
        public String getAccountId() {
            return accountId;
        }
        
        public String getReason() {
            return reason;
        }
        
        public java.time.Instant getLockedUntil() {
            return lockedUntil;
        }
    }
    
    /**
     * Account Suspended Exception
     */
    public static class AccountSuspendedException extends DomainException {
        
        private final String accountId;
        private final String reason;
        
        public AccountSuspendedException(String accountId, String reason) {
            super(String.format("Account %s is suspended: %s", accountId, reason),
                    "ACCOUNT_SUSPENDED",
                    ACCOUNT_DOMAIN);
            this.accountId = accountId;
            this.reason = reason;
        }
        
        public String getAccountId() {
            return accountId;
        }
        
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * Account Closed Exception
     */
    public static class AccountClosedException extends DomainException {
        
        private final String accountId;
        private final java.time.Instant closedAt;
        
        public AccountClosedException(String accountId, java.time.Instant closedAt) {
            super(String.format("Account %s was closed at %s", accountId, closedAt),
                    "ACCOUNT_CLOSED",
                    ACCOUNT_DOMAIN);
            this.accountId = accountId;
            this.closedAt = closedAt;
        }
        
        public String getAccountId() {
            return accountId;
        }
        
        public java.time.Instant getClosedAt() {
            return closedAt;
        }
    }
    
    /**
     * Invalid Account State Exception
     */
    public static class InvalidAccountStateException extends DomainException {
        
        private final String accountId;
        private final String currentState;
        private final String requiredState;
        
        public InvalidAccountStateException(String accountId, String currentState, String requiredState) {
            super(String.format("Invalid account state: account %s is in state %s, required state: %s", 
                    accountId, currentState, requiredState),
                    "INVALID_ACCOUNT_STATE",
                    ACCOUNT_DOMAIN);
            this.accountId = accountId;
            this.currentState = currentState;
            this.requiredState = requiredState;
        }
        
        public String getAccountId() {
            return accountId;
        }
        
        public String getCurrentState() {
            return currentState;
        }
        
        public String getRequiredState() {
            return requiredState;
        }
    }
    
    /**
     * Minimum Balance Exception
     */
    public static class MinimumBalanceException extends DomainException {
        
        private final String accountId;
        private final Money currentBalance;
        private final Money minimumBalance;
        
        public MinimumBalanceException(String accountId, Money currentBalance, Money minimumBalance) {
            super(String.format("Account %s balance %s is below minimum required %s", 
                    accountId, currentBalance, minimumBalance),
                    "MINIMUM_BALANCE_VIOLATION",
                    ACCOUNT_DOMAIN);
            this.accountId = accountId;
            this.currentBalance = currentBalance;
            this.minimumBalance = minimumBalance;
        }
        
        public String getAccountId() {
            return accountId;
        }
        
        public Money getCurrentBalance() {
            return currentBalance;
        }
        
        public Money getMinimumBalance() {
            return minimumBalance;
        }
    }
    
    /**
     * Account Limit Exceeded Exception
     */
    public static class AccountLimitExceededException extends DomainException {
        
        private final String accountId;
        private final String limitType;
        private final Money amount;
        private final Money limit;
        
        public AccountLimitExceededException(String accountId, String limitType, Money amount, Money limit) {
            super(String.format("Account %s %s limit exceeded: amount %s exceeds limit %s", 
                    accountId, limitType, amount, limit),
                    "ACCOUNT_LIMIT_EXCEEDED",
                    ACCOUNT_DOMAIN);
            this.accountId = accountId;
            this.limitType = limitType;
            this.amount = amount;
            this.limit = limit;
        }
        
        public String getAccountId() {
            return accountId;
        }
        
        public String getLimitType() {
            return limitType;
        }
        
        public Money getAmount() {
            return amount;
        }
        
        public Money getLimit() {
            return limit;
        }
    }
    
    /**
     * Overdraft Not Allowed Exception
     */
    public static class OverdraftNotAllowedException extends DomainException {
        
        private final String accountId;
        private final Money requestedAmount;
        private final Money availableBalance;
        
        public OverdraftNotAllowedException(String accountId, Money requestedAmount, Money availableBalance) {
            super(String.format("Overdraft not allowed for account %s: requested %s, available %s", 
                    accountId, requestedAmount, availableBalance),
                    "OVERDRAFT_NOT_ALLOWED",
                    ACCOUNT_DOMAIN);
            this.accountId = accountId;
            this.requestedAmount = requestedAmount;
            this.availableBalance = availableBalance;
        }
        
        public String getAccountId() {
            return accountId;
        }
        
        public Money getRequestedAmount() {
            return requestedAmount;
        }
        
        public Money getAvailableBalance() {
            return availableBalance;
        }
    }
    
    /**
     * Account Type Mismatch Exception
     */
    public static class AccountTypeMismatchException extends DomainException {
        
        private final String accountId;
        private final String expectedType;
        private final String actualType;
        
        public AccountTypeMismatchException(String accountId, String expectedType, String actualType) {
            super(String.format("Account type mismatch for account %s: expected %s, actual %s", 
                    accountId, expectedType, actualType),
                    "ACCOUNT_TYPE_MISMATCH",
                    ACCOUNT_DOMAIN);
            this.accountId = accountId;
            this.expectedType = expectedType;
            this.actualType = actualType;
        }
        
        public String getAccountId() {
            return accountId;
        }
        
        public String getExpectedType() {
            return expectedType;
        }
        
        public String getActualType() {
            return actualType;
        }
    }
}