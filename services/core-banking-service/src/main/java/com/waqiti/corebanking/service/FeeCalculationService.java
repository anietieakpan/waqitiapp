package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.*;
import com.waqiti.corebanking.repository.FeeScheduleRepository;
import com.waqiti.corebanking.repository.TransactionRepository;
import com.waqiti.corebanking.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fee Calculation Service
 * 
 * Handles all fee calculations for accounts and transactions.
 * Supports various fee types including flat fees, percentage fees,
 * tiered fees, and conditional fees with waivers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeeCalculationService {
    
    private final FeeScheduleRepository feeScheduleRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionProcessingService transactionProcessingService;
    
    /**
     * Calculate fee for a transaction
     */
    public BigDecimal calculateTransactionFee(Transaction transaction, Account account) {
        log.debug("Calculating transaction fee for transaction: {}, account: {}", 
            transaction.getId(), account.getAccountNumber());
        
        // Get applicable fee schedule
        Optional<FeeSchedule> feeScheduleOpt = getFeeScheduleForAccount(account);
        if (feeScheduleOpt.isEmpty()) {
            log.debug("No fee schedule found for account: {}", account.getAccountNumber());
            return BigDecimal.ZERO;
        }
        
        FeeSchedule feeSchedule = feeScheduleOpt.get();
        
        // Check if fee schedule applies to this transaction type
        if (!feeSchedule.appliesToTransactionType(transaction.getType())) {
            log.debug("Fee schedule {} does not apply to transaction type: {}", 
                feeSchedule.getName(), transaction.getType());
            return BigDecimal.ZERO;
        }
        
        // Check for fee waivers
        if (isFeeWaived(transaction, account, feeSchedule)) {
            log.debug("Fee waived for transaction: {}", transaction.getId());
            return BigDecimal.ZERO;
        }
        
        // Calculate fee based on method
        BigDecimal fee = calculateFeeByMethod(transaction.getAmount(), feeSchedule, account, transaction);
        
        // Apply minimum and maximum limits
        fee = applyFeeLimits(fee, feeSchedule);
        
        log.info("Calculated fee: {} for transaction: {}, method: {}", 
            fee, transaction.getId(), feeSchedule.getCalculationMethod());
        
        return fee;
    }
    
    /**
     * Calculate periodic maintenance fee for an account
     */
    public BigDecimal calculateMaintenanceFee(Account account, FeeSchedule.PeriodType period) {
        log.debug("Calculating maintenance fee for account: {}, period: {}", 
            account.getAccountNumber(), period);
        
        List<FeeSchedule> maintenanceFees = getActiveMaintenanceFeesForPeriod(period);
        
        for (FeeSchedule feeSchedule : maintenanceFees) {
            if (feeSchedule.appliesToAccountType(account.getAccountType())) {
                // Check if account qualifies for fee waiver
                if (isMaintenanceFeeWaived(account, feeSchedule)) {
                    log.debug("Maintenance fee waived for account: {}", account.getAccountNumber());
                    continue;
                }
                
                BigDecimal fee = feeSchedule.getBaseAmount();
                if (fee == null) {
                    fee = BigDecimal.ZERO;
                }
                
                log.info("Calculated maintenance fee: {} for account: {}", 
                    fee, account.getAccountNumber());
                return fee;
            }
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * Apply calculated fee to account
     */
    @Transactional
    public Transaction applyFee(Account account, BigDecimal feeAmount, FeeSchedule.FeeType feeType, 
                               String description, UUID relatedTransactionId) {
        
        if (feeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("No fee to apply for account: {}", account.getAccountNumber());
            // Return a zero-fee transaction instead of null to maintain audit trail
            return Transaction.builder()
                .id(UUID.randomUUID())
                .accountId(account.getId())
                .amount(BigDecimal.ZERO)
                .type(TransactionType.FEE)
                .status(TransactionStatus.COMPLETED)
                .description("Zero fee - no charges applied")
                .transactionDate(LocalDateTime.now())
                .build();
        }
        
        log.info("Applying fee: {} {} to account: {}", 
            feeAmount, account.getCurrency(), account.getAccountNumber());
        
        // Create fee transaction
        Transaction feeTransaction = Transaction.builder()
            .id(UUID.randomUUID())
            .accountId(account.getId())
            .amount(feeAmount)
            .currency(account.getCurrency())
            .type(mapFeeTypeToTransactionType(feeType))
            .status(TransactionStatus.PENDING)
            .description(description != null ? description : "Fee charge - " + feeType.toString())
            .referenceNumber(generateFeeReference(account, feeType))
            .relatedTransactionId(relatedTransactionId)
            .createdAt(LocalDateTime.now())
            .metadata(java.util.Map.of(
                "feeType", feeType.toString(),
                "originalAmount", feeAmount.toString(),
                "appliedDate", LocalDateTime.now().toString()
            ))
            .build();
        
        // Process the fee transaction
        try {
            return transactionProcessingService.processTransaction(feeTransaction);
        } catch (Exception e) {
            log.error("Failed to apply fee to account: {}", account.getAccountNumber(), e);
            throw new RuntimeException("Fee application failed", e);
        }
    }
    
    /**
     * Get transaction count for period (for free transaction limits)
     */
    public int getTransactionCountForPeriod(Account account, FeeSchedule.PeriodType periodType) {
        LocalDateTime periodStart = getPeriodStart(periodType);
        
        return transactionRepository.countByAccountIdAndCreatedAtBetween(
            account.getId(), periodStart, LocalDateTime.now());
    }
    
    /**
     * Check if account has exceeded free transaction limit
     */
    public boolean hasExceededFreeLimit(Account account, FeeSchedule feeSchedule) {
        if (feeSchedule.getFreeTransactionsPerPeriod() == null) {
            return false; // No limit set
        }
        
        int transactionCount = getTransactionCountForPeriod(account, feeSchedule.getPeriodType());
        return transactionCount >= feeSchedule.getFreeTransactionsPerPeriod();
    }
    
    // Private helper methods
    
    @Cacheable(value = "feeSchedules", key = "#account.accountType + '_' + (#account.feeScheduleId != null ? #account.feeScheduleId : 'default')")
    public Optional<FeeSchedule> getFeeScheduleForAccount(Account account) {
        log.debug("Loading fee schedule for account type: {}, custom schedule ID: {}", 
            account.getAccountType(), account.getFeeScheduleId());
            
        if (account.getFeeScheduleId() != null) {
            return feeScheduleRepository.findById(account.getFeeScheduleId());
        }
        
        // Find default fee schedule for account type
        return feeScheduleRepository.findDefaultForAccountType(account.getAccountType());
    }
    
    private boolean isFeeWaived(Transaction transaction, Account account, FeeSchedule feeSchedule) {
        // Check various waiver conditions
        
        // 1. Check if within free transaction limit
        if (!hasExceededFreeLimit(account, feeSchedule)) {
            return true;
        }
        
        // 2. Check account balance-based waivers
        if (isBalanceBasedWaiver(account, feeSchedule)) {
            return true;
        }
        
        // 3. Check custom waiver conditions (JSON-based)
        return checkCustomWaiverConditions(transaction, account, feeSchedule);
    }
    
    private boolean isMaintenanceFeeWaived(Account account, FeeSchedule feeSchedule) {
        // Common maintenance fee waivers
        
        // 1. Minimum balance waiver
        if (feeSchedule.getWaiverConditions() != null && 
            feeSchedule.getWaiverConditions().contains("minimum_balance")) {
            
            BigDecimal minimumBalance = account.getMinimumBalance();
            if (minimumBalance != null && 
                account.getCurrentBalance().compareTo(minimumBalance) >= 0) {
                return true;
            }
        }
        
        // 2. Account age waiver (new accounts)
        if (feeSchedule.getWaiverConditions() != null && 
            feeSchedule.getWaiverConditions().contains("new_account")) {
            
            long accountAgeMonths = ChronoUnit.MONTHS.between(
                account.getCreatedAt(), LocalDateTime.now());
            
            if (accountAgeMonths < 3) { // Waive for first 3 months
                return true;
            }
        }
        
        return false;
    }
    
    private BigDecimal calculateFeeByMethod(BigDecimal amount, FeeSchedule feeSchedule, 
                                          Account account, Transaction transaction) {
        
        switch (feeSchedule.getCalculationMethod()) {
            case FLAT_FEE:
                return feeSchedule.getBaseAmount() != null ? feeSchedule.getBaseAmount() : BigDecimal.ZERO;
                
            case PERCENTAGE:
                if (feeSchedule.getPercentageRate() != null && amount != null) {
                    return amount.multiply(feeSchedule.getPercentageRate())
                               .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                }
                return BigDecimal.ZERO;
                
            case TIERED:
                return calculateTieredFee(amount, feeSchedule, account);
                
            case FLAT_PLUS_PERCENTAGE:
                BigDecimal flatFee = feeSchedule.getBaseAmount() != null ? 
                    feeSchedule.getBaseAmount() : BigDecimal.ZERO;
                BigDecimal percentageFee = BigDecimal.ZERO;
                
                if (feeSchedule.getPercentageRate() != null && amount != null) {
                    percentageFee = amount.multiply(feeSchedule.getPercentageRate())
                                         .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                }
                
                return flatFee.add(percentageFee);
                
            case MIN_OF_FLAT_OR_PERCENTAGE:
                BigDecimal flatOption = feeSchedule.getBaseAmount() != null ? 
                    feeSchedule.getBaseAmount() : BigDecimal.ZERO;
                BigDecimal percentageOption = BigDecimal.ZERO;
                
                if (feeSchedule.getPercentageRate() != null && amount != null) {
                    percentageOption = amount.multiply(feeSchedule.getPercentageRate())
                                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                }
                
                return flatOption.min(percentageOption);
                
            case MAX_OF_FLAT_OR_PERCENTAGE:
                BigDecimal flatMax = feeSchedule.getBaseAmount() != null ? 
                    feeSchedule.getBaseAmount() : BigDecimal.ZERO;
                BigDecimal percentageMax = BigDecimal.ZERO;
                
                if (feeSchedule.getPercentageRate() != null && amount != null) {
                    percentageMax = amount.multiply(feeSchedule.getPercentageRate())
                                         .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                }
                
                return flatMax.max(percentageMax);
                
            default:
                return BigDecimal.ZERO;
        }
    }
    
    private BigDecimal calculateTieredFee(BigDecimal amount, FeeSchedule feeSchedule, Account account) {
        if (feeSchedule.getFeeTiers() == null || feeSchedule.getFeeTiers().isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        int transactionCount = getTransactionCountForPeriod(account, feeSchedule.getPeriodType());
        
        for (FeeTier tier : feeSchedule.getFeeTiers()) {
            if (tier.isInRange(amount)) {
                return tier.calculateFee(amount, transactionCount);
            }
        }
        
        return BigDecimal.ZERO;
    }
    
    private BigDecimal applyFeeLimits(BigDecimal fee, FeeSchedule feeSchedule) {
        if (feeSchedule.getMinimumFee() != null && 
            fee.compareTo(feeSchedule.getMinimumFee()) < 0) {
            fee = feeSchedule.getMinimumFee();
        }
        
        if (feeSchedule.getMaximumFee() != null && 
            fee.compareTo(feeSchedule.getMaximumFee()) > 0) {
            fee = feeSchedule.getMaximumFee();
        }
        
        return fee;
    }
    
    private boolean isBalanceBasedWaiver(Account account, FeeSchedule feeSchedule) {
        // Example: Waive fees if account balance is above certain threshold
        if (feeSchedule.getWaiverConditions() != null && 
            feeSchedule.getWaiverConditions().contains("high_balance")) {
            
            BigDecimal threshold = new BigDecimal("10000.00"); // $10,000 threshold
            return account.getCurrentBalance().compareTo(threshold) >= 0;
        }
        return false;
    }
    
    private boolean checkCustomWaiverConditions(Transaction transaction, Account account, 
                                              FeeSchedule feeSchedule) {
        // Implement custom JSON-based waiver conditions
        // This would parse the JSON and evaluate conditions dynamically
        return false;
    }
    
    private Transaction.TransactionType mapFeeTypeToTransactionType(FeeSchedule.FeeType feeType) {
        switch (feeType) {
            case TRANSACTION_FEE:
                return Transaction.TransactionType.FEE_CHARGE;
            case MAINTENANCE_FEE:
                return Transaction.TransactionType.MAINTENANCE_FEE;
            case OVERDRAFT_FEE:
                return Transaction.TransactionType.OVERDRAFT_FEE;
            case ATM_FEE:
                return Transaction.TransactionType.ATM_FEE;
            case WIRE_TRANSFER_FEE:
                return Transaction.TransactionType.WIRE_FEE;
            case INTERNATIONAL_FEE:
                return Transaction.TransactionType.INTERNATIONAL_FEE;
            default:
                return Transaction.TransactionType.FEE_CHARGE;
        }
    }
    
    private String generateFeeReference(Account account, FeeSchedule.FeeType feeType) {
        return String.format("FEE-%s-%s-%s", 
            feeType.toString().substring(0, 3),
            account.getAccountNumber(),
            System.currentTimeMillis());
    }
    
    private LocalDateTime getPeriodStart(FeeSchedule.PeriodType periodType) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (periodType) {
            case DAILY:
                return now.toLocalDate().atStartOfDay();
            case WEEKLY:
                return now.minusWeeks(1);
            case MONTHLY:
                return now.withDayOfMonth(1).toLocalDate().atStartOfDay();
            case QUARTERLY:
                return now.minusMonths(3);
            case ANNUALLY:
                return now.withDayOfYear(1).toLocalDate().atStartOfDay();
            default:
                return now.minusDays(30);
        }
    }
    
    /**
     * Cache active maintenance fees by period to avoid repeated database queries
     */
    @Cacheable(value = "maintenanceFees", key = "#period")
    public List<FeeSchedule> getActiveMaintenanceFeesForPeriod(FeeSchedule.PeriodType period) {
        log.debug("Loading active maintenance fees for period: {}", period);
        
        return feeScheduleRepository
            .findByFeeTypeAndStatusAndPeriodType(
                FeeSchedule.FeeType.MAINTENANCE_FEE, 
                FeeSchedule.FeeScheduleStatus.ACTIVE,
                period);
    }
    
    /**
     * Cache transaction counts for accounts to improve performance
     */
    @Cacheable(value = "transactionCounts", key = "#account.id + '_' + #periodType")
    public int getTransactionCountForPeriod(Account account, FeeSchedule.PeriodType periodType) {
        log.debug("Getting transaction count for account: {}, period: {}", 
            account.getAccountNumber(), periodType);
        
        LocalDateTime periodStart = getPeriodStart(periodType);
        
        return transactionRepository.countByAccountIdAndCreatedDateAfter(
            account.getId(), periodStart);
    }
    
    /**
     * Clear fee-related caches when fee schedules are updated
     */
    @CacheEvict(value = {"feeSchedules", "maintenanceFees"}, allEntries = true)
    public void clearFeeScheduleCache() {
        log.info("Cleared fee schedule caches");
    }
    
    /**
     * Clear transaction count cache for a specific account
     */
    @CacheEvict(value = "transactionCounts", key = "#accountId + '_*'")
    public void clearTransactionCountCache(UUID accountId) {
        log.info("Cleared transaction count cache for account: {}", accountId);
    }
}