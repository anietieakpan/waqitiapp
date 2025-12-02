package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.repository.AccountRepository;
import com.waqiti.corebanking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for calculating and applying interest to accounts.
 * Supports various interest calculation methods and frequencies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterestCalculationService {
    
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionProcessingService transactionProcessingService;
    private final DoubleEntryBookkeepingService bookkeepingService;
    
    @Value("${core-banking.interest.enabled:true}")
    private boolean interestEnabled;
    
    @Value("${core-banking.interest.batch-size:100}")
    private int batchSize;
    
    @Value("${core-banking.interest.calculation-days:365}")
    private int calculationDays;
    
    /**
     * Scheduled job to calculate and apply interest daily at 2 AM
     */
    @Scheduled(cron = "${core-banking.interest.cron:0 0 2 * * ?}")
    @Transactional
    public void calculateDailyInterest() {
        if (!interestEnabled) {
            log.info("Interest calculation is disabled");
            return;
        }
        
        log.info("Starting daily interest calculation");
        long startTime = System.currentTimeMillis();
        int processedAccounts = 0;
        int interestApplied = 0;
        
        try {
            // Process accounts in batches to avoid memory issues
            List<Account> eligibleAccounts;
            int offset = 0;
            
            do {
                eligibleAccounts = accountRepository.findEligibleForInterest(
                    LocalDate.now(), PageRequest.of(offset / batchSize, batchSize));
                
                for (Account account : eligibleAccounts) {
                    if (shouldCalculateInterest(account)) {
                        BigDecimal interestAmount = calculateInterest(account);
                        if (interestAmount.compareTo(BigDecimal.ZERO) > 0) {
                            applyInterest(account, interestAmount);
                            interestApplied++;
                        }
                    }
                    processedAccounts++;
                }
                
                offset += batchSize;
            } while (!eligibleAccounts.isEmpty());
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed interest calculation. Processed: {} accounts, Applied interest to: {} accounts, Duration: {} ms", 
                processedAccounts, interestApplied, duration);
            
        } catch (Exception e) {
            log.error("Error during interest calculation", e);
            throw new RuntimeException("Interest calculation failed", e);
        }
    }
    
    /**
     * Calculate interest for a specific account
     */
    public BigDecimal calculateInterest(Account account) {
        if (account.getInterestRate() == null || account.getInterestRate().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // Get average daily balance for the period
        BigDecimal averageDailyBalance = calculateAverageDailyBalance(account);
        if (averageDailyBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // Calculate interest based on the configured method
        Account.InterestCalculationMethod method = account.getInterestCalculationMethod();
        if (method == null) {
            method = Account.InterestCalculationMethod.DAILY_BALANCE;
        }
        
        BigDecimal interestAmount = BigDecimal.ZERO;
        
        switch (method) {
            case DAILY_BALANCE:
                // Daily interest = (Principal × Rate × Days) / 365
                interestAmount = averageDailyBalance
                    .multiply(account.getInterestRate())
                    .multiply(BigDecimal.ONE) // 1 day
                    .divide(BigDecimal.valueOf(calculationDays), 6, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                break;
                
            case MONTHLY_COMPOUND:
                // Monthly compound interest calculation
                if (account.getLastInterestCalculationDate() != null) {
                    long daysSinceLastCalculation = java.time.temporal.ChronoUnit.DAYS.between(
                        account.getLastInterestCalculationDate(), LocalDate.now());
                    
                    if (daysSinceLastCalculation >= 30) {
                        BigDecimal monthlyRate = account.getInterestRate()
                            .divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP)
                            .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                        
                        interestAmount = averageDailyBalance.multiply(monthlyRate)
                            .setScale(2, RoundingMode.HALF_UP);
                    }
                }
                break;
                
            case QUARTERLY_COMPOUND:
                // Quarterly compound interest calculation
                if (account.getLastInterestCalculationDate() != null) {
                    long daysSinceLastCalculation = java.time.temporal.ChronoUnit.DAYS.between(
                        account.getLastInterestCalculationDate(), LocalDate.now());
                    
                    if (daysSinceLastCalculation >= 90) {
                        BigDecimal quarterlyRate = account.getInterestRate()
                            .divide(BigDecimal.valueOf(4), 6, RoundingMode.HALF_UP)
                            .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
                        
                        interestAmount = averageDailyBalance.multiply(quarterlyRate)
                            .setScale(2, RoundingMode.HALF_UP);
                    }
                }
                break;
        }
        
        // Apply minimum and maximum interest limits if configured
        if (account.getMinimumInterestAmount() != null && 
            interestAmount.compareTo(account.getMinimumInterestAmount()) < 0) {
            return BigDecimal.ZERO; // Don't apply interest below minimum
        }
        
        if (account.getMaximumInterestAmount() != null && 
            interestAmount.compareTo(account.getMaximumInterestAmount()) > 0) {
            interestAmount = account.getMaximumInterestAmount();
        }
        
        return interestAmount;
    }
    
    /**
     * Apply calculated interest to an account
     */
    @Transactional
    public void applyInterest(Account account, BigDecimal interestAmount) {
        try {
            // Create interest credit transaction
            Transaction interestTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .accountId(account.getId())
                .amount(interestAmount)
                .currency(account.getCurrency())
                .type(Transaction.TransactionType.INTEREST_CREDIT)
                .status(Transaction.TransactionStatus.PENDING)
                .description("Interest credit for period ending " + LocalDate.now())
                .referenceNumber(generateInterestReference(account))
                .createdAt(LocalDateTime.now())
                .metadata(java.util.Map.of(
                    "interestRate", account.getInterestRate().toString(),
                    "calculationMethod", account.getInterestCalculationMethod().toString(),
                    "periodEnd", LocalDate.now().toString()
                ))
                .build();
            
            // Process the transaction
            transactionProcessingService.processTransaction(interestTransaction);
            
            // Update account's last interest calculation date
            account.setLastInterestCalculationDate(LocalDate.now());
            account.setLastInterestCreditedAmount(interestAmount);
            accountRepository.save(account);
            
            log.info("Applied interest of {} {} to account {}", 
                interestAmount, account.getCurrency(), account.getAccountNumber());
            
        } catch (Exception e) {
            log.error("Failed to apply interest to account {}", account.getAccountNumber(), e);
            throw new RuntimeException("Interest application failed", e);
        }
    }
    
    /**
     * Calculate average daily balance for an account
     */
    private BigDecimal calculateAverageDailyBalance(Account account) {
        LocalDate startDate = account.getLastInterestCalculationDate();
        if (startDate == null) {
            startDate = account.getCreatedAt().toLocalDate();
        }
        
        LocalDate endDate = LocalDate.now();
        
        // Get all transactions for the period
        List<Transaction> transactions = transactionRepository.findByAccountIdAndDateRange(
            account.getId(), startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        // Calculate daily balances and average
        BigDecimal totalBalance = BigDecimal.ZERO;
        BigDecimal currentBalance = account.getCurrentBalance();
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        
        if (days == 0) {
            return currentBalance;
        }
        
        // Simple average calculation - in production, this would track daily balances
        return currentBalance;
    }
    
    /**
     * Check if interest should be calculated for an account
     */
    private boolean shouldCalculateInterest(Account account) {
        // Check if account is eligible for interest
        if (!account.isActive() || account.isBlocked()) {
            return false;
        }
        
        // Check account type eligibility
        if (!isAccountTypeEligible(account.getType())) {
            return false;
        }
        
        // Check minimum balance requirement
        if (account.getMinimumBalanceForInterest() != null && 
            account.getCurrentBalance().compareTo(account.getMinimumBalanceForInterest()) < 0) {
            return false;
        }
        
        // Check if interest calculation is due
        if (account.getLastInterestCalculationDate() != null) {
            LocalDate nextCalculationDate = getNextInterestCalculationDate(account);
            if (LocalDate.now().isBefore(nextCalculationDate)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if account type is eligible for interest
     */
    private boolean isAccountTypeEligible(Account.AccountType type) {
        return type == Account.AccountType.SAVINGS || 
               type == Account.AccountType.FIXED_DEPOSIT ||
               type == Account.AccountType.MONEY_MARKET;
    }
    
    /**
     * Get next interest calculation date based on frequency
     */
    private LocalDate getNextInterestCalculationDate(Account account) {
        LocalDate lastCalculation = account.getLastInterestCalculationDate();
        if (lastCalculation == null) {
            return LocalDate.now();
        }
        
        Account.InterestCalculationMethod method = account.getInterestCalculationMethod();
        if (method == null) {
            method = Account.InterestCalculationMethod.DAILY_BALANCE;
        }
        
        switch (method) {
            case DAILY_BALANCE:
                return lastCalculation.plusDays(1);
            case MONTHLY_COMPOUND:
                return lastCalculation.plusMonths(1);
            case QUARTERLY_COMPOUND:
                return lastCalculation.plusMonths(3);
            default:
                return lastCalculation.plusDays(1);
        }
    }
    
    /**
     * Generate unique reference for interest transaction
     */
    private String generateInterestReference(Account account) {
        return String.format("INT-%s-%s-%s", 
            account.getAccountNumber(), 
            LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
            UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}