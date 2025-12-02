package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.domain.AccountBalance;
import com.waqiti.ledger.dto.BalanceCalculationResult;
import com.waqiti.ledger.dto.ReconciliationResult;
import com.waqiti.ledger.dto.AccountBalanceSummary;
import com.waqiti.ledger.domain.Account;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.repository.AccountBalanceRepository;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.exception.BalanceCalculationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Balance Calculation Service
 * 
 * Provides real-time balance calculations for accounts by aggregating
 * ledger entries and handling different balance types (current, available,
 * pending, reserved).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceCalculationService {

    @Lazy
    private final BalanceCalculationService self;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    /**
     * Calculates current real-time balance for an account
     */
    @Cacheable(value = "calculatedBalance", key = "#accountId")
    public BalanceCalculationResult calculateBalance(UUID accountId) {
        try {
            log.debug("Calculating balance for account: {}", accountId);
            
            // Get all ledger entries for the account
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdOrderByTransactionDateAsc(accountId);
            
            return calculateBalanceFromEntries(accountId, entries, LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Failed to calculate balance for account: {}", accountId, e);
            throw new BalanceCalculationException("Failed to calculate account balance", e);
        }
    }

    /**
     * Calculates balance as of a specific date
     */
    public BalanceCalculationResult calculateBalanceAsOf(UUID accountId, LocalDateTime asOfDate) {
        try {
            log.debug("Calculating balance for account: {} as of {}", accountId, asOfDate);
            
            // Get ledger entries up to the specified date
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndTransactionDateLessThanEqualOrderByTransactionDateAsc(
                accountId, asOfDate);
            
            return calculateBalanceFromEntries(accountId, entries, asOfDate);
            
        } catch (Exception e) {
            log.error("Failed to calculate balance as of date for account: {}", accountId, e);
            throw new BalanceCalculationException("Failed to calculate balance as of date", e);
        }
    }

    /**
     * Calculates balances for multiple accounts efficiently
     */
    public Map<UUID, BalanceCalculationResult> calculateBalances(Set<UUID> accountIds) {
        try {
            log.debug("Calculating balances for {} accounts", accountIds.size());
            
            Map<UUID, BalanceCalculationResult> results = new HashMap<>();
            
            for (UUID accountId : accountIds) {
                results.put(accountId, self.calculateBalance(accountId));
            }
            
            return results;
            
        } catch (Exception e) {
            log.error("Failed to calculate balances for multiple accounts", e);
            throw new BalanceCalculationException("Failed to calculate multiple account balances", e);
        }
    }

    /**
     * Validates if a transaction would result in valid balances
     * Enhanced with comprehensive business rule validation
     */
    public boolean validateTransactionBalance(UUID accountId, BigDecimal transactionAmount, 
                                            LedgerEntry.EntryType entryType) {
        try {
            if (transactionAmount == null || transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Invalid transaction amount: {}", transactionAmount);
                return false;
            }
            
            BalanceCalculationResult currentBalance = self.calculateBalance(accountId);
            
            // Calculate what the balance would be after the transaction
            BigDecimal projectedBalance = currentBalance.getCurrentBalance();
            
            if (entryType == LedgerEntry.EntryType.DEBIT) {
                // For asset accounts, debit increases balance; for liability/equity/revenue, debit decreases
                if (chartOfAccountsService.isAssetAccount(accountId) || chartOfAccountsService.isExpenseAccount(accountId)) {
                    projectedBalance = projectedBalance.add(transactionAmount);
                } else {
                    projectedBalance = projectedBalance.subtract(transactionAmount);
                }
            } else if (entryType == LedgerEntry.EntryType.CREDIT) {
                // For asset accounts, credit decreases balance; for liability/equity/revenue, credit increases
                if (chartOfAccountsService.isAssetAccount(accountId) || chartOfAccountsService.isExpenseAccount(accountId)) {
                    projectedBalance = projectedBalance.subtract(transactionAmount);
                } else {
                    projectedBalance = projectedBalance.add(transactionAmount);
                }
            }
            
            // Enhanced business rule validation
            if (!validateBusinessRules(accountId, projectedBalance, transactionAmount)) {
                return false;
            }
            
            // Validate insufficient funds for asset accounts
            if (chartOfAccountsService.isAssetAccount(accountId) && 
                projectedBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Transaction would result in negative asset balance for account: {}", accountId);
                return false;
            }
            
            // Additional regulatory compliance checks
            if (!performRegulatoryChecks(accountId, transactionAmount, projectedBalance)) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to validate transaction balance for account: {}", accountId, e);
            return false;
        }
    }

    /**
     * Calculates running balances for a series of ledger entries
     */
    public List<LedgerEntry> calculateRunningBalances(List<LedgerEntry> entries) {
        try {
            if (entries.isEmpty()) {
                return entries;
            }
            
            // Sort entries by transaction date
            List<LedgerEntry> sortedEntries = entries.stream()
                .sorted(Comparator.comparing(LedgerEntry::getTransactionDate))
                .collect(Collectors.toList());
            
            BigDecimal runningBalance = BigDecimal.ZERO;
            
            // Get starting balance if entries don't start from account creation
            if (!sortedEntries.isEmpty()) {
                UUID accountId = sortedEntries.get(0).getAccountId();
                LocalDateTime firstEntryDate = sortedEntries.get(0).getTransactionDate();
                
                // Get balance before first entry
                List<LedgerEntry> priorEntries = ledgerEntryRepository.findByAccountIdAndTransactionDateLessThanOrderByTransactionDateAsc(
                    accountId, firstEntryDate);
                
                runningBalance = self.calculateBalanceFromEntries(accountId, priorEntries, firstEntryDate).getCurrentBalance();
            }
            
            // Calculate running balance for each entry
            for (LedgerEntry entry : sortedEntries) {
                if (entry.getEntryType() == LedgerEntry.EntryType.DEBIT) {
                    if (isDebitPositive(entry.getAccountId())) {
                        runningBalance = runningBalance.add(entry.getAmount());
                    } else {
                        runningBalance = runningBalance.subtract(entry.getAmount());
                    }
                } else if (entry.getEntryType() == LedgerEntry.EntryType.CREDIT) {
                    if (isDebitPositive(entry.getAccountId())) {
                        runningBalance = runningBalance.subtract(entry.getAmount());
                    } else {
                        runningBalance = runningBalance.add(entry.getAmount());
                    }
                }
                
                entry.setRunningBalance(runningBalance);
            }
            
            return sortedEntries;
            
        } catch (Exception e) {
            log.error("Failed to calculate running balances", e);
            throw new BalanceCalculationException("Failed to calculate running balances", e);
        }
    }

    /**
     * Reconciles calculated balance with stored balance
     */
    public ReconciliationResult reconcileBalance(UUID accountId) {
        try {
            BalanceCalculationResult calculatedBalance = self.calculateBalance(accountId);
            
            Optional<AccountBalance> storedBalanceOpt = accountBalanceRepository.findByAccountId(accountId);
            
            if (storedBalanceOpt.isEmpty()) {
                return ReconciliationResult.builder()
                    .accountId(accountId)
                    .reconciled(false)
                    .issue("No stored balance found for account")
                    .calculatedBalance(calculatedBalance.getCurrentBalance())
                    .storedBalance(BigDecimal.ZERO)
                    .variance(calculatedBalance.getCurrentBalance())
                    .build();
            }
            
            AccountBalance storedBalance = storedBalanceOpt.get();
            BigDecimal variance = calculatedBalance.getCurrentBalance().subtract(storedBalance.getCurrentBalance());
            boolean reconciled = variance.compareTo(BigDecimal.ZERO) == 0;
            
            return ReconciliationResult.builder()
                .accountId(accountId)
                .reconciled(reconciled)
                .calculatedBalance(calculatedBalance.getCurrentBalance())
                .storedBalance(storedBalance.getCurrentBalance())
                .variance(variance)
                .reconciledAt(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to reconcile balance for account: {}", accountId, e);
            throw new BalanceCalculationException("Failed to reconcile balance", e);
        }
    }

    /**
     * Gets account balance summary with aggregated information
     */
    public AccountBalanceSummary getAccountBalanceSummary(UUID accountId) {
        try {
            BalanceCalculationResult balance = self.calculateBalance(accountId);
            
            // Get transaction counts and amounts for the current month
            LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            
            List<LedgerEntry> monthlyEntries = ledgerEntryRepository.findByAccountIdAndTransactionDateGreaterThanEqual(
                accountId, monthStart);
            
            long debitCount = monthlyEntries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntry.EntryType.DEBIT)
                .count();
                
            long creditCount = monthlyEntries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntry.EntryType.CREDIT)
                .count();
                
            BigDecimal totalDebits = monthlyEntries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntry.EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            BigDecimal totalCredits = monthlyEntries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntry.EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return AccountBalanceSummary.builder()
                .accountId(accountId)
                .currentBalance(balance.getCurrentBalance())
                .availableBalance(balance.getAvailableBalance())
                .pendingBalance(balance.getPendingBalance())
                .reservedBalance(balance.getReservedBalance())
                .monthlyDebitCount(debitCount)
                .monthlyCreditCount(creditCount)
                .monthlyDebitAmount(totalDebits)
                .monthlyCreditAmount(totalCredits)
                .lastCalculated(balance.getLastUpdated())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to get account balance summary for: {}", accountId, e);
            throw new BalanceCalculationException("Failed to get balance summary", e);
        }
    }

    /**
     * Perform bulk balance calculation for multiple accounts with optimizations
     */
    public Map<UUID, BalanceCalculationResult> calculateBulkBalances(Set<UUID> accountIds, LocalDateTime asOfDate) {
        try {
            log.debug("Calculating bulk balances for {} accounts as of {}", accountIds.size(), asOfDate);
            
            Map<UUID, BalanceCalculationResult> results = new HashMap<>();
            
            // Batch fetch all entries for all accounts to minimize database calls
            Map<UUID, List<LedgerEntry>> entriesByAccount = new HashMap<>();
            for (UUID accountId : accountIds) {
                List<LedgerEntry> entries = asOfDate != null ?
                    ledgerEntryRepository.findByAccountIdAndTransactionDateLessThanEqualOrderByTransactionDateAsc(accountId, asOfDate) :
                    ledgerEntryRepository.findByAccountIdOrderByTransactionDateAsc(accountId);
                entriesByAccount.put(accountId, entries);
            }
            
            // Calculate balances in parallel for better performance
            entriesByAccount.entrySet().parallelStream().forEach(entry -> {
                UUID accountId = entry.getKey();
                List<LedgerEntry> entries = entry.getValue();
                LocalDateTime calculationDate = asOfDate != null ? asOfDate : LocalDateTime.now();
                
                BalanceCalculationResult result = self.calculateBalanceFromEntries(accountId, entries, calculationDate);
                synchronized (results) {
                    results.put(accountId, result);
                }
            });
            
            return results;
            
        } catch (Exception e) {
            log.error("Failed to calculate bulk balances", e);
            throw new BalanceCalculationException("Failed to calculate bulk balances", e);
        }
    }
    
    /**
     * Validate comprehensive business rules for balance calculations
     */
    private boolean validateBusinessRules(UUID accountId, BigDecimal projectedBalance, BigDecimal transactionAmount) {
        try {
            // Check for maximum balance limits (regulatory compliance)
            BigDecimal maxBalance = getMaximumAllowedBalance(accountId);
            if (maxBalance != null && projectedBalance.compareTo(maxBalance) > 0) {
                log.warn("Transaction would exceed maximum balance limit for account: {}", accountId);
                return false;
            }
            
            // Check for suspicious transaction patterns
            if (isSuspiciousTransaction(accountId, transactionAmount)) {
                log.warn("Suspicious transaction pattern detected for account: {}", accountId);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error validating business rules for account: {}", accountId, e);
            return false;
        }
    }
    
    /**
     * Perform regulatory compliance checks
     */
    private boolean performRegulatoryChecks(UUID accountId, BigDecimal transactionAmount, BigDecimal projectedBalance) {
        try {
            // AML compliance - Currency Transaction Report (CTR) threshold
            BigDecimal ctrThreshold = new BigDecimal("10000"); // $10,000 USD
            if (transactionAmount.compareTo(ctrThreshold) > 0) {
                log.info("Transaction exceeds CTR reporting threshold for account {}: {}", accountId, transactionAmount);
                // In production, this would trigger CTR filing requirements
            }
            
            // Suspicious Activity Report (SAR) thresholds
            BigDecimal sarThreshold = new BigDecimal("5000"); // $5,000 USD for aggregated suspicious activity
            LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
            List<LedgerEntry> recentEntries = ledgerEntryRepository.findByAccountIdAndCreatedAtAfter(accountId, oneDayAgo);
            
            BigDecimal dailyTransactionTotal = recentEntries.stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            if (dailyTransactionTotal.add(transactionAmount).compareTo(sarThreshold) > 0) {
                // Check for suspicious patterns
                if (isSuspiciousTransaction(accountId, transactionAmount)) {
                    log.warn("Transaction may require SAR filing for account {}: daily total {}", 
                        accountId, dailyTransactionTotal.add(transactionAmount));
                }
            }
            
            // FDIC insurance limits for deposit accounts
            Account account = accountRepository.findById(accountId).orElse(null);
            if (account != null && (account.getAccountType() == Account.AccountType.SAVINGS || 
                                   account.getAccountType() == Account.AccountType.PERSONAL)) {
                BigDecimal fdicLimit = new BigDecimal("250000"); // $250,000 FDIC insurance limit
                if (projectedBalance.compareTo(fdicLimit) > 0) {
                    log.warn("Account balance exceeds FDIC insurance limit for account {}: {}", accountId, projectedBalance);
                    // In production, customer should be notified
                }
            }
            
            // International wire transfer compliance (OFAC sanctions screening)
            if (transactionAmount.compareTo(new BigDecimal("3000")) > 0) {
                // In production, this would trigger OFAC sanctions screening
                log.debug("International wire transfer compliance check required for account {}", accountId);
            }
            
            // Bank Secrecy Act (BSA) record keeping requirements
            if (transactionAmount.compareTo(new BigDecimal("3000")) > 0) {
                log.debug("BSA record keeping requirements apply for transaction amount: {}", transactionAmount);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error performing regulatory compliance checks for account: {}", accountId, e);
            // Err on the side of caution - reject transaction if compliance checks fail
            return false;
        }
    }
    
    /**
     * Get maximum allowed balance for account (regulatory limits)
     */
    private BigDecimal getMaximumAllowedBalance(UUID accountId) {
        // Retrieve regulatory balance limits based on account type and jurisdiction
        Account account = accountRepository.findById(accountId)
            .orElse(null);
            
        if (account == null) {
            return new BigDecimal("1000000"); // Default $1M limit
        }
        
        // Apply limits based on account type and verification level
        switch (account.getAccountType()) {
            case PERSONAL:
                if (account.isVerified()) {
                    return new BigDecimal("250000"); // $250K for verified personal accounts
                } else {
                    return new BigDecimal("10000"); // $10K for unverified personal accounts
                }
            case BUSINESS:
                if (account.isVerified()) {
                    return new BigDecimal("10000000"); // $10M for verified business accounts
                } else {
                    return new BigDecimal("100000"); // $100K for unverified business accounts
                }
            case SAVINGS:
                return new BigDecimal("500000"); // $500K for savings accounts (FDIC insurance consideration)
            case INVESTMENT:
                return new BigDecimal("50000000"); // $50M for investment accounts
            default:
                return new BigDecimal("1000000"); // Default $1M limit
        }
    }
    
    /**
     * Check for suspicious transaction patterns
     */
    private boolean isSuspiciousTransaction(UUID accountId, BigDecimal transactionAmount) {
        // Implementation for suspicious activity detection
        try {
            // Check 1: Large transaction threshold
            BigDecimal largeTransactionThreshold = new BigDecimal("10000"); // $10K reporting threshold
            if (transactionAmount.compareTo(largeTransactionThreshold) > 0) {
                log.warn("Large transaction detected for account {}: {}", accountId, transactionAmount);
            }
            
            // Check 2: Velocity check - too many transactions in short period
            LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
            List<LedgerEntry> recentEntries = ledgerEntryRepository.findByAccountIdAndCreatedAtAfter(
                accountId, oneDayAgo);
                
            if (recentEntries.size() > 50) { // More than 50 transactions in 24 hours
                log.warn("High transaction velocity detected for account {}: {} transactions", 
                    accountId, recentEntries.size());
                return true;
            }
            
            // Check 3: Structuring detection - multiple transactions just under reporting threshold
            BigDecimal structuringThreshold = new BigDecimal("9900");
            long suspiciousCount = recentEntries.stream()
                .filter(e -> e.getAmount().compareTo(structuringThreshold) > 0 && 
                           e.getAmount().compareTo(largeTransactionThreshold) < 0)
                .count();
                
            if (suspiciousCount > 3) { // More than 3 transactions between $9,900 and $10,000
                log.warn("Potential structuring detected for account {}", accountId);
                return true;
            }
            
            // Check 4: Sudden spike in transaction amount
            BigDecimal averageTransaction = calculateAverageTransactionAmount(accountId);
            if (averageTransaction.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal spikeThreshold = averageTransaction.multiply(new BigDecimal("10")); // 10x normal
                if (transactionAmount.compareTo(spikeThreshold) > 0) {
                    log.warn("Transaction spike detected for account {}: {} vs avg {}", 
                        accountId, transactionAmount, averageTransaction);
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking suspicious transaction for account {}", accountId, e);
            // Err on the side of caution
            return true;
        }
    }
    
    // Private helper methods

    public BalanceCalculationResult calculateBalanceFromEntries(UUID accountId, List<LedgerEntry> entries, LocalDateTime asOfDate) {
        BigDecimal currentBalance = BigDecimal.ZERO;
        BigDecimal pendingBalance = BigDecimal.ZERO;
        BigDecimal reservedBalance = BigDecimal.ZERO;
        
        boolean isDebitPositive = isDebitPositive(accountId);
        
        for (LedgerEntry entry : entries) {
            BigDecimal amount = entry.getAmount();
            
            switch (entry.getEntryType()) {
                case DEBIT:
                    if (isDebitPositive) {
                        currentBalance = currentBalance.add(amount);
                    } else {
                        currentBalance = currentBalance.subtract(amount);
                    }
                    break;
                    
                case CREDIT:
                    if (isDebitPositive) {
                        currentBalance = currentBalance.subtract(amount);
                    } else {
                        currentBalance = currentBalance.add(amount);
                    }
                    break;
                    
                case RESERVATION:
                    reservedBalance = reservedBalance.add(amount);
                    break;
                    
                case RELEASE:
                    reservedBalance = reservedBalance.subtract(amount);
                    break;
                    
                case PENDING:
                    pendingBalance = pendingBalance.add(amount);
                    break;
                    
                case AUTHORIZATION_HOLD:
                    // Authorization holds reduce available balance but don't affect current balance
                    break;
                    
                case AUTHORIZATION_RELEASE:
                    // Authorization releases increase available balance
                    break;
            }
        }
        
        // Enhanced available balance calculation with authorization holds
        BigDecimal authorizationHolds = entries.stream()
            .filter(entry -> entry.getEntryType() == LedgerEntry.EntryType.AUTHORIZATION_HOLD)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal authorizationReleases = entries.stream()
            .filter(entry -> entry.getEntryType() == LedgerEntry.EntryType.AUTHORIZATION_RELEASE)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal netAuthorizationHolds = authorizationHolds.subtract(authorizationReleases);
        BigDecimal availableBalance = currentBalance.subtract(reservedBalance).subtract(netAuthorizationHolds);
        
        return BalanceCalculationResult.builder()
            .accountId(accountId)
            .currentBalance(currentBalance)
            .availableBalance(availableBalance)
            .pendingBalance(pendingBalance)
            .reservedBalance(reservedBalance)
            .lastUpdated(asOfDate)
            .entryCount(entries.size())
            .build();
    }

    private boolean isDebitPositive(UUID accountId) {
        // For asset and expense accounts, debits increase the balance (positive)
        // For liability, equity, and revenue accounts, debits decrease the balance (negative)
        try {
            return chartOfAccountsService.isAssetAccount(accountId) || 
                   chartOfAccountsService.isExpenseAccount(accountId);
        } catch (Exception e) {
            log.error("Error determining debit direction for account: {}", accountId, e);
            // Default to asset account behavior for safety
            return true;
        }
    }
    
    /**
     * Calculate average transaction amount for an account
     */
    private BigDecimal calculateAverageTransactionAmount(UUID accountId) {
        try {
            // Get transactions from the last 30 days
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            List<LedgerEntry> recentEntries = ledgerEntryRepository.findByAccountIdAndCreatedAtAfter(
                accountId, thirtyDaysAgo);
            
            if (recentEntries.isEmpty()) {
                return BigDecimal.ZERO;
            }
            
            BigDecimal total = recentEntries.stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return total.divide(new BigDecimal(recentEntries.size()), 2, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Error calculating average transaction amount for account {}", accountId, e);
            return BigDecimal.ZERO;
        }
    }
}