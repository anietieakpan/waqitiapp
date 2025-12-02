package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.repository.AccountRepository;
import com.waqiti.corebanking.exception.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Account Management Service
 * 
 * Provides comprehensive account lifecycle management:
 * - Account creation and configuration
 * - Balance management
 * - Account status updates
 * - Compliance and security controls
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;

    /**
     * Creates a new user account
     */
    @Transactional
    public Account createUserAccount(CreateAccountRequest request) {
        String accountNumber = generateAccountNumber(request.getAccountType());
        
        Account account = Account.builder()
            .accountNumber(accountNumber)
            .userId(request.getUserId())
            .accountName(request.getAccountName())
            .accountType(request.getAccountType())
            .accountCategory(Account.AccountCategory.LIABILITY) // User accounts are liabilities
            .status(Account.AccountStatus.PENDING)
            .currency(request.getCurrency())
            .currentBalance(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .pendingBalance(BigDecimal.ZERO)
            .reservedBalance(BigDecimal.ZERO)
            .dailyTransactionLimit(request.getDailyLimit())
            .monthlyTransactionLimit(request.getMonthlyLimit())
            .minimumBalance(request.getMinimumBalance())
            .complianceLevel(Account.ComplianceLevel.STANDARD)
            .openedDate(LocalDateTime.now())
            .build();

        account = accountRepository.save(account);
        
        log.info("Created user account: {} for user {}", account.getAccountNumber(), request.getUserId());
        return account;
    }

    /**
     * Activates a pending account
     */
    @Transactional
    public void activateAccount(UUID accountId) {
        Account account = getAccountById(accountId);
        
        if (account.getStatus() != Account.AccountStatus.PENDING) {
            throw new IllegalStateException("Only pending accounts can be activated");
        }
        
        account.setStatus(Account.AccountStatus.ACTIVE);
        accountRepository.save(account);
        
        log.info("Activated account: {}", account.getAccountNumber());
    }

    /**
     * Gets account by ID
     */
    public Account getAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    /**
     * Gets user's accounts
     */
    public List<Account> getUserAccounts(UUID userId) {
        return accountRepository.findByUserIdAndStatus(userId, Account.AccountStatus.ACTIVE);
    }

    /**
     * Gets user's primary wallet
     */
    public Account getUserPrimaryWallet(UUID userId) {
        return accountRepository.findUserPrimaryWallet(userId)
            .orElseThrow(() -> new AccountNotFoundException("Primary wallet not found for user: " + userId));
    }

    private String generateAccountNumber(Account.AccountType accountType) {
        String prefix = switch (accountType) {
            case USER_WALLET -> "WLT";
            case USER_SAVINGS -> "SAV";
            case USER_CREDIT -> "CRD";
            case BUSINESS_OPERATING -> "BOP";
            case MERCHANT -> "MER";
            default -> "ACC";
        };
        
        return prefix + "-" + System.currentTimeMillis() + "-" + 
               String.format("%04d", SECURE_RANDOM.nextInt(10000));
    }

    // Data classes
    public static class CreateAccountRequest {
        private UUID userId;
        private String accountName;
        private Account.AccountType accountType;
        private String currency;
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private BigDecimal minimumBalance;

        // Getters and setters
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public String getAccountName() { return accountName; }
        public void setAccountName(String accountName) { this.accountName = accountName; }
        public Account.AccountType getAccountType() { return accountType; }
        public void setAccountType(Account.AccountType accountType) { this.accountType = accountType; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public BigDecimal getDailyLimit() { return dailyLimit; }
        public void setDailyLimit(BigDecimal dailyLimit) { this.dailyLimit = dailyLimit; }
        public BigDecimal getMonthlyLimit() { return monthlyLimit; }
        public void setMonthlyLimit(BigDecimal monthlyLimit) { this.monthlyLimit = monthlyLimit; }
        public BigDecimal getMinimumBalance() { return minimumBalance; }
        public void setMinimumBalance(BigDecimal minimumBalance) { this.minimumBalance = minimumBalance; }
    }
}