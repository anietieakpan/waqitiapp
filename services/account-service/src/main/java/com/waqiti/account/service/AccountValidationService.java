package com.waqiti.account.service;

import com.waqiti.account.dto.request.CreateAccountRequestDTO;
import com.waqiti.account.entity.Account;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for comprehensive account validation logic
 * 
 * Provides centralized validation for account operations including
 * business rules, regulatory compliance, and security checks.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountValidationService {
    
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern ACCOUNT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-_.,&()]+$");
    
    private static final BigDecimal MIN_INITIAL_DEPOSIT = new BigDecimal("10.00");
    private static final BigDecimal MAX_INITIAL_DEPOSIT = new BigDecimal("1000000.00");
    private static final BigDecimal VIP_MIN_DEPOSIT = new BigDecimal("10000.00");
    private static final BigDecimal PLATINUM_MIN_DEPOSIT = new BigDecimal("50000.00");
    
    private static final int MAX_ACCOUNTS_PER_USER = 10;
    private static final int MAX_ACCOUNTS_PER_TYPE = 3;
    
    private final AccountRepository accountRepository;
    
    /**
     * Validate account entity
     */
    public void validateAccount(Account account) {
        List<String> errors = new ArrayList<>();
        
        // Validate required fields
        if (account.getUserId() == null) {
            errors.add("User ID is required");
        }
        
        if (account.getAccountType() == null) {
            errors.add("Account type is required");
        }
        
        if (account.getCurrency() == null || !CURRENCY_PATTERN.matcher(account.getCurrency()).matches()) {
            errors.add("Valid currency code is required");
        }
        
        if (account.getAccountName() == null || account.getAccountName().trim().isEmpty()) {
            errors.add("Account name is required");
        }
        
        // Validate account name format
        if (account.getAccountName() != null && !ACCOUNT_NAME_PATTERN.matcher(account.getAccountName()).matches()) {
            errors.add("Account name contains invalid characters");
        }
        
        // Validate balances
        if (account.getBalance() == null) {
            account.setBalance(BigDecimal.ZERO);
        }
        
        if (account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Account balance cannot be negative");
        }
        
        if (account.getAvailableBalance() == null) {
            account.setAvailableBalance(account.getBalance());
        }
        
        if (account.getAvailableBalance().compareTo(account.getBalance()) > 0) {
            errors.add("Available balance cannot exceed total balance");
        }
        
        // Validate limits
        validateTransactionLimits(account, errors);
        
        // Validate tier requirements
        validateTierRequirements(account, errors);
        
        // Validate KYC requirements
        validateKycRequirements(account, errors);
        
        if (!errors.isEmpty()) {
            throw new ValidationException("Account validation failed", errors);
        }
    }
    
    /**
     * Validate create account request
     */
    public void validateCreateAccountRequest(CreateAccountRequestDTO request) {
        List<String> errors = new ArrayList<>();
        
        // Check user account limits
        validateUserAccountLimits(request.getUserId(), request.getAccountType(), errors);
        
        // Validate initial deposit
        if (request.getInitialDeposit() != null) {
            validateInitialDeposit(request.getInitialDeposit(), request.getTierLevel(), errors);
        }
        
        // Validate parent account
        if (request.getParentAccountId() != null) {
            validateParentAccount(request.getParentAccountId(), request.getUserId(), errors);
        }
        
        // Validate currency
        if (!isValidCurrency(request.getCurrency())) {
            errors.add("Unsupported currency: " + request.getCurrency());
        }
        
        // Validate international features
        if (Boolean.TRUE.equals(request.getInternationalEnabled())) {
            validateInternationalFeatures(request, errors);
        }
        
        // Validate business account requirements
        if ("BUSINESS".equals(request.getAccountCategory())) {
            validateBusinessAccountRequirements(request, errors);
        }
        
        if (!errors.isEmpty()) {
            throw new ValidationException("Account creation validation failed", errors);
        }
    }
    
    /**
     * Validate transaction limits
     */
    private void validateTransactionLimits(Account account, List<String> errors) {
        if (account.getDailyTransactionLimit() != null && account.getDailyTransactionLimit().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Daily transaction limit cannot be negative");
        }
        
        if (account.getMonthlyTransactionLimit() != null && account.getMonthlyTransactionLimit().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Monthly transaction limit cannot be negative");
        }
        
        if (account.getDailyTransactionLimit() != null && account.getMonthlyTransactionLimit() != null) {
            if (account.getDailyTransactionLimit().compareTo(account.getMonthlyTransactionLimit()) > 0) {
                errors.add("Daily transaction limit cannot exceed monthly limit");
            }
        }
        
        if (account.getOverdraftLimit() != null && account.getOverdraftLimit().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Overdraft limit cannot be negative");
        }
    }
    
    /**
     * Validate tier requirements
     */
    private void validateTierRequirements(Account account, List<String> errors) {
        if (account.getTierLevel() == null) {
            return;
        }
        
        switch (account.getTierLevel()) {
            case VIP:
                if (account.getBalance().compareTo(VIP_MIN_DEPOSIT) < 0) {
                    errors.add("VIP tier requires minimum balance of " + VIP_MIN_DEPOSIT);
                }
                if (account.getKycLevel() == null || account.getKycLevel().ordinal() < Account.KycLevel.LEVEL_2.ordinal()) {
                    errors.add("VIP tier requires KYC Level 2 or higher");
                }
                break;
                
            case PLATINUM:
                if (account.getBalance().compareTo(PLATINUM_MIN_DEPOSIT) < 0) {
                    errors.add("Platinum tier requires minimum balance of " + PLATINUM_MIN_DEPOSIT);
                }
                if (account.getKycLevel() != Account.KycLevel.LEVEL_3) {
                    errors.add("Platinum tier requires KYC Level 3");
                }
                break;
                
            case PREMIUM:
                if (account.getKycLevel() == null || account.getKycLevel() == Account.KycLevel.LEVEL_0) {
                    errors.add("Premium tier requires KYC verification");
                }
                break;
        }
    }
    
    /**
     * Validate KYC requirements
     */
    private void validateKycRequirements(Account account, List<String> errors) {
        if (Boolean.TRUE.equals(account.getInternationalEnabled())) {
            if (account.getKycLevel() != Account.KycLevel.LEVEL_3) {
                errors.add("International transactions require KYC Level 3");
            }
        }
        
        if (account.getDailyTransactionLimit() != null && 
            account.getDailyTransactionLimit().compareTo(new BigDecimal("10000")) > 0) {
            if (account.getKycLevel() == null || account.getKycLevel().ordinal() < Account.KycLevel.LEVEL_2.ordinal()) {
                errors.add("High transaction limits require KYC Level 2 or higher");
            }
        }
    }
    
    /**
     * Validate user account limits
     */
    private void validateUserAccountLimits(UUID userId, String accountType, List<String> errors) {
        // Check total accounts per user
        List<Account> userAccounts = accountRepository.findByUserId(userId);
        if (userAccounts.size() >= MAX_ACCOUNTS_PER_USER) {
            errors.add("Maximum account limit reached for user");
        }
        
        // Check accounts per type
        long typeCount = userAccounts.stream()
            .filter(a -> a.getAccountType().toString().equals(accountType))
            .count();
        
        if (typeCount >= MAX_ACCOUNTS_PER_TYPE) {
            errors.add("Maximum " + accountType + " accounts limit reached");
        }
    }
    
    /**
     * Validate initial deposit
     */
    private void validateInitialDeposit(BigDecimal amount, String tierLevel, List<String> errors) {
        if (amount.compareTo(MIN_INITIAL_DEPOSIT) < 0) {
            errors.add("Initial deposit must be at least " + MIN_INITIAL_DEPOSIT);
        }
        
        if (amount.compareTo(MAX_INITIAL_DEPOSIT) > 0) {
            errors.add("Initial deposit exceeds maximum allowed amount");
        }
        
        if ("VIP".equals(tierLevel) && amount.compareTo(VIP_MIN_DEPOSIT) < 0) {
            errors.add("VIP tier requires minimum initial deposit of " + VIP_MIN_DEPOSIT);
        }
        
        if ("PLATINUM".equals(tierLevel) && amount.compareTo(PLATINUM_MIN_DEPOSIT) < 0) {
            errors.add("Platinum tier requires minimum initial deposit of " + PLATINUM_MIN_DEPOSIT);
        }
    }
    
    /**
     * Validate parent account
     */
    private void validateParentAccount(UUID parentAccountId, UUID userId, List<String> errors) {
        Account parentAccount = accountRepository.findById(parentAccountId).orElse(null);
        
        if (parentAccount == null) {
            errors.add("Parent account not found");
            return;
        }
        
        if (!parentAccount.getUserId().equals(userId)) {
            errors.add("Parent account must belong to the same user");
        }
        
        if (!parentAccount.isAccountActive()) {
            errors.add("Parent account must be active");
        }
        
        if (parentAccount.getParentAccount() != null) {
            errors.add("Cannot create sub-account under another sub-account");
        }
    }
    
    /**
     * Check if currency is valid
     */
    private boolean isValidCurrency(String currency) {
        // List of supported currencies
        return List.of("USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NGN").contains(currency);
    }
    
    /**
     * Validate international features
     */
    private void validateInternationalFeatures(CreateAccountRequestDTO request, List<String> errors) {
        if (!"LEVEL_3".equals(request.getKycLevel())) {
            errors.add("International transactions require KYC Level 3 verification");
        }
        
        if (!List.of("PREMIUM", "VIP", "PLATINUM").contains(request.getTierLevel())) {
            errors.add("International transactions require Premium tier or higher");
        }
    }
    
    /**
     * Validate business account requirements
     */
    private void validateBusinessAccountRequirements(CreateAccountRequestDTO request, List<String> errors) {
        if (!"LEVEL_3".equals(request.getKycLevel())) {
            errors.add("Business accounts require KYC Level 3 verification");
        }
        
        if (request.getInitialDeposit() == null || 
            request.getInitialDeposit().compareTo(new BigDecimal("1000")) < 0) {
            errors.add("Business accounts require minimum initial deposit of 1000");
        }
    }
    
    /**
     * Validate account for transaction
     */
    public void validateAccountForTransaction(Account account, BigDecimal amount, String transactionType) {
        if (!account.isAccountActive()) {
            throw new BusinessException("Account is not active for transactions");
        }
        
        if (account.getFrozen()) {
            throw new BusinessException("Account is frozen");
        }
        
        if ("DEBIT".equals(transactionType)) {
            if (!account.canTransact(amount)) {
                throw new BusinessException("Insufficient funds");
            }
            
            if (!account.isWithinDailyLimit(amount)) {
                throw new BusinessException("Transaction exceeds daily limit");
            }
            
            if (!account.isWithinMonthlyLimit(amount)) {
                throw new BusinessException("Transaction exceeds monthly limit");
            }
        }
    }
}