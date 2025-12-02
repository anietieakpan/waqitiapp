package com.waqiti.ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.domain.AccountBalance;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.repository.AccountBalanceRepository;
import com.waqiti.ledger.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Double-Entry Ledger Service
 * 
 * Provides comprehensive double-entry bookkeeping functionality:
 * - Enforces double-entry accounting principles (Debits = Credits)
 * - Real-time balance calculations and validations
 * - Multi-currency transaction support
 * - Atomic ledger entry creation with ACID compliance
 * - Balance reconciliation and audit trails
 * - Chart of accounts integration
 * - Fund reservation and release mechanisms
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DoubleEntryLedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final BalanceCalculationService balanceCalculationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String BALANCE_LOCK_PREFIX = "balance:lock:";
    private static final String BALANCE_CACHE_PREFIX = "balance:";
    private static final int LOCK_TIMEOUT_SECONDS = 30;

    /**
     * Posts a complete double-entry transaction with multiple ledger entries
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public PostTransactionResult postTransaction(PostTransactionRequest request) {
        try {
            log.info("Posting transaction: {} with {} entries", 
                    request.getTransactionId(), request.getLedgerEntries().size());
            
            // Validate double-entry balance
            validateDoubleEntryBalance(request.getLedgerEntries());
            
            // Validate all account references
            validateAccountReferences(request.getLedgerEntries());
            
            // Acquire locks for all affected accounts
            Set<UUID> accountIds = extractAccountIds(request.getLedgerEntries());
            Map<UUID, String> accountLocks = acquireAccountLocks(accountIds);
            
            try {
                // Create ledger entries
                List<LedgerEntry> savedEntries = new ArrayList<>();
                
                for (LedgerEntryRequest entryRequest : request.getLedgerEntries()) {
                    LedgerEntry entry = createLedgerEntry(entryRequest, request.getTransactionId());
                    savedEntries.add(ledgerEntryRepository.save(entry));
                }
                
                // Update account balances atomically
                updateAccountBalances(savedEntries);
                
                // Validate post-transaction balances
                validatePostTransactionBalances(savedEntries);
                
                log.info("Successfully posted transaction: {} with {} entries", 
                        request.getTransactionId(), savedEntries.size());
                
                return PostTransactionResult.builder()
                    .transactionId(request.getTransactionId())
                    .success(true)
                    .ledgerEntries(mapToLedgerEntryResponses(savedEntries))
                    .build();
                
            } finally {
                releaseAccountLocks(accountLocks);
            }
            
        } catch (DoubleEntryValidationException e) {
            log.error("Double-entry validation failed for transaction: {}", request.getTransactionId(), e);
            return PostTransactionResult.failure(request.getTransactionId(), e.getMessage());
        } catch (InsufficientFundsException e) {
            log.error("Insufficient funds for transaction: {}", request.getTransactionId(), e);
            return PostTransactionResult.failure(request.getTransactionId(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to post transaction: {}", request.getTransactionId(), e);
            return PostTransactionResult.failure(request.getTransactionId(), "Internal error: " + e.getMessage());
        }
    }

    /**
     * Gets real-time account balance with caching
     */
    @Cacheable(value = "accountBalance", key = "#accountId")
    public BalanceInquiryResponse getAccountBalance(UUID accountId) {
        try {
            AccountBalance accountBalance = getOrCreateAccountBalance(accountId);
            
            // Calculate real-time balance from ledger entries
            BalanceCalculationResult calculatedBalance = balanceCalculationService.calculateBalance(accountId);
            
            // Update cached balance if needed
            if (balanceNeedsUpdate(accountBalance, calculatedBalance)) {
                updateAccountBalance(accountBalance, calculatedBalance);
            }
            
            return BalanceInquiryResponse.builder()
                .accountId(accountId)
                .currentBalance(calculatedBalance.getCurrentBalance())
                .availableBalance(calculatedBalance.getAvailableBalance())
                .pendingBalance(calculatedBalance.getPendingBalance())
                .reservedBalance(calculatedBalance.getReservedBalance())
                .lastUpdated(calculatedBalance.getLastUpdated())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get account balance for: {}", accountId, e);
            throw new LedgerServiceException("Failed to retrieve account balance", e);
        }
    }

    /**
     * Reserves funds for pending transactions
     */
    @Transactional
    @CacheEvict(value = "accountBalance", key = "#accountId")
    public ReserveFundsResult reserveFunds(UUID accountId, BigDecimal amount, 
                                         UUID reservationId, String reason) {
        try {
            log.info("Reserving funds: {} for account: {} reservation: {}", 
                    amount, accountId, reservationId);
            
            String lockKey = acquireAccountLock(accountId);
            
            try {
                AccountBalance accountBalance = getOrCreateAccountBalance(accountId);
                
                // Check available balance
                if (accountBalance.getAvailableBalance().compareTo(amount) < 0) {
                    return ReserveFundsResult.failure("Insufficient available balance");
                }
                
                // Create reservation ledger entry
                LedgerEntry reservationEntry = LedgerEntry.builder()
                    .accountId(accountId)
                    .entryType(LedgerEntry.EntryType.RESERVATION)
                    .amount(amount)
                    .referenceNumber(\"RESV-\" + reservationId.toString())
                    .description(\"Fund reservation: \" + reason)
                    .status(LedgerEntry.LedgerStatus.POSTED)
                    .currency(accountBalance.getCurrency())
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .metadata(createSecureMetadata("reservationId", reservationId.toString()))
                    .build();
                
                ledgerEntryRepository.save(reservationEntry);
                
                // Update account balance
                accountBalance.setAvailableBalance(accountBalance.getAvailableBalance().subtract(amount));
                accountBalance.setReservedBalance(accountBalance.getReservedBalance().add(amount));
                accountBalance.setLastUpdated(LocalDateTime.now());
                
                accountBalanceRepository.save(accountBalance);
                
                log.info("Successfully reserved funds: {} for account: {}", amount, accountId);
                
                return ReserveFundsResult.success(reservationId, amount);
                
            } finally {
                releaseAccountLock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Failed to reserve funds for account: {}", accountId, e);
            return ReserveFundsResult.failure("Failed to reserve funds: " + e.getMessage());
        }
    }

    /**
     * Releases previously reserved funds
     */
    @Transactional
    @CacheEvict(value = "accountBalance", key = "#accountId")
    public ReleaseReservedFundsResult releaseReservedFunds(UUID accountId, UUID reservationId, BigDecimal amount) {
        try {
            log.info("Releasing reserved funds: {} for account: {} reservation: {}", 
                    amount, accountId, reservationId);
            
            String lockKey = acquireAccountLock(accountId);
            
            try {
                AccountBalance accountBalance = getOrCreateAccountBalance(accountId);
                
                // Verify reservation exists and amount is valid
                if (accountBalance.getReservedBalance().compareTo(amount) < 0) {
                    return ReleaseReservedFundsResult.failure("Insufficient reserved balance");
                }
                
                // Create release ledger entry
                LedgerEntry releaseEntry = LedgerEntry.builder()
                    .accountId(accountId)
                    .entryType(LedgerEntry.EntryType.RELEASE)
                    .amount(amount)
                    .referenceNumber("REL-" + reservationId.toString())
                    .description("Fund release: " + reservationId)
                    .status(LedgerEntry.LedgerStatus.POSTED)
                    .currency(accountBalance.getCurrency())
                    .transactionDate(LocalDateTime.now())
                    .valueDate(LocalDateTime.now())
                    .metadata(createSecureMetadata("reservationId", reservationId.toString()))
                    .build();
                
                ledgerEntryRepository.save(releaseEntry);
                
                // Update account balance
                accountBalance.setReservedBalance(accountBalance.getReservedBalance().subtract(amount));
                accountBalance.setAvailableBalance(accountBalance.getAvailableBalance().add(amount));
                accountBalance.setLastUpdated(LocalDateTime.now());
                
                accountBalanceRepository.save(accountBalance);
                
                log.info("Successfully released reserved funds: {} for account: {}", amount, accountId);
                
                return ReleaseReservedFundsResult.success(reservationId, amount);
                
            } finally {
                releaseAccountLock(lockKey);
            }
            
        } catch (Exception e) {
            log.error("Failed to release reserved funds for account: {}", accountId, e);
            return ReleaseReservedFundsResult.failure("Failed to release funds: " + e.getMessage());
        }
    }

    /**
     * Gets ledger entries for an account with pagination
     */
    public AccountLedgerResponse getAccountLedger(UUID accountId, LocalDateTime fromDate, 
                                                LocalDateTime toDate, int page, int size) {
        try {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndDateRange(
                accountId, fromDate, toDate, page * size, size);
            
            long totalEntries = ledgerEntryRepository.countByAccountIdAndDateRange(
                accountId, fromDate, toDate);
            
            return AccountLedgerResponse.builder()
                .accountId(accountId)
                .entries(mapToLedgerEntryResponses(entries))
                .totalEntries(totalEntries)
                .page(page)
                .size(size)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get account ledger for: {}", accountId, e);
            throw new LedgerServiceException("Failed to retrieve account ledger", e);
        }
    }

    /**
     * Generates trial balance for all accounts
     */
    public TrialBalanceResponse generateTrialBalance(LocalDateTime asOfDate) {
        try {
            List<Object[]> trialBalanceData = ledgerEntryRepository.getTrialBalance(asOfDate);
            
            BigDecimal totalDebits = BigDecimal.ZERO;
            BigDecimal totalCredits = BigDecimal.ZERO;
            List<TrialBalanceEntry> entries = new ArrayList<>();
            
            for (Object[] row : trialBalanceData) {
                UUID accountId = (UUID) row[0];
                BigDecimal debits = (BigDecimal) row[1];
                BigDecimal credits = (BigDecimal) row[2];
                
                totalDebits = totalDebits.add(debits);
                totalCredits = totalCredits.add(credits);
                
                entries.add(TrialBalanceEntry.builder()
                    .accountId(accountId)
                    .debitBalance(debits)
                    .creditBalance(credits)
                    .netBalance(debits.subtract(credits))
                    .build());
            }
            
            boolean balanced = totalDebits.compareTo(totalCredits) == 0;
            
            return TrialBalanceResponse.builder()
                .asOfDate(asOfDate)
                .entries(entries)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .balanced(balanced)
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate trial balance", e);
            throw new LedgerServiceException("Failed to generate trial balance", e);
        }
    }

    /**
     * Performs account reconciliation
     */
    @Transactional
    public ReconciliationResult reconcileAccount(UUID accountId, LocalDateTime reconciliationDate) {
        try {
            log.info("Starting account reconciliation for: {} as of {}", accountId, reconciliationDate);
            
            // Calculate balance from ledger entries
            BalanceCalculationResult calculatedBalance = balanceCalculationService.calculateBalanceAsOf(
                accountId, reconciliationDate);
            
            // Get current stored balance
            AccountBalance storedBalance = getOrCreateAccountBalance(accountId);
            
            // Compare balances
            BigDecimal variance = calculatedBalance.getCurrentBalance().subtract(storedBalance.getCurrentBalance());
            boolean reconciled = variance.compareTo(BigDecimal.ZERO) == 0;
            
            if (!reconciled) {
                log.warn("Reconciliation variance detected for account {}: {}", accountId, variance);
                
                // Update stored balance to match calculated balance
                updateAccountBalance(storedBalance, calculatedBalance);
            }
            
            return ReconciliationResult.builder()
                .accountId(accountId)
                .reconciliationDate(reconciliationDate)
                .calculatedBalance(calculatedBalance.getCurrentBalance())
                .storedBalance(storedBalance.getCurrentBalance())
                .variance(variance)
                .reconciled(reconciled)
                .reconciledAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to reconcile account: {}", accountId, e);
            throw new LedgerServiceException("Failed to reconcile account", e);
        }
    }

    // Private helper methods

    private void validateDoubleEntryBalance(List<LedgerEntryRequest> entries) {
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        
        for (LedgerEntryRequest entry : entries) {
            if (entry.getEntryType().equals("DEBIT")) {
                totalDebits = totalDebits.add(entry.getAmount());
            } else if (entry.getEntryType().equals("CREDIT")) {
                totalCredits = totalCredits.add(entry.getAmount());
            }
        }
        
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new DoubleEntryValidationException(
                String.format("Double-entry validation failed: Debits=%.2f, Credits=%.2f", 
                             totalDebits, totalCredits));
        }
    }

    private void validateAccountReferences(List<LedgerEntryRequest> entries) {
        for (LedgerEntryRequest entry : entries) {
            if (!accountBalanceRepository.existsByAccountId(entry.getAccountId())) {
                throw new AccountNotFoundException("Account not found: " + entry.getAccountId());
            }
        }
    }

    private Set<UUID> extractAccountIds(List<LedgerEntryRequest> entries) {
        return entries.stream()
            .map(LedgerEntryRequest::getAccountId)
            .collect(Collectors.toSet());
    }

    private Map<UUID, String> acquireAccountLocks(Set<UUID> accountIds) {
        Map<UUID, String> locks = new HashMap<>();
        
        for (UUID accountId : accountIds) {
            String lockKey = acquireAccountLock(accountId);
            locks.put(accountId, lockKey);
        }
        
        return locks;
    }

    private String acquireAccountLock(UUID accountId) {
        String lockKey = BALANCE_LOCK_PREFIX + accountId.toString();
        
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
            lockKey, "locked", LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        
        if (!Boolean.TRUE.equals(acquired)) {
            throw new LedgerLockException("Unable to acquire balance lock for account: " + accountId);
        }
        
        return lockKey;
    }

    private void releaseAccountLocks(Map<UUID, String> locks) {
        for (String lockKey : locks.values()) {
            releaseAccountLock(lockKey);
        }
    }

    private void releaseAccountLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("Failed to release account lock: {}", lockKey, e);
        }
    }

    private LedgerEntry createLedgerEntry(LedgerEntryRequest request, UUID transactionId) {
        return LedgerEntry.builder()
            .transactionId(transactionId)
            .accountId(request.getAccountId())
            .entryType(LedgerEntry.EntryType.valueOf(request.getEntryType()))
            .amount(request.getAmount())
            .referenceNumber(request.getReferenceNumber())
            .description(request.getDescription())
            .narrative(request.getNarrative())
            .status(LedgerEntry.LedgerStatus.POSTED)
            .currency(request.getCurrency())
            .transactionDate(request.getTransactionDate())
            .valueDate(request.getValueDate())
            .contraAccountId(request.getContraAccountId())
            .metadata(request.getMetadata())
            .build();
    }

    private void updateAccountBalances(List<LedgerEntry> entries) {
        Map<UUID, BigDecimal> balanceChanges = new HashMap<>();
        
        // Calculate net balance changes per account
        for (LedgerEntry entry : entries) {
            BigDecimal change = entry.getEntryType() == LedgerEntry.EntryType.DEBIT ?
                entry.getAmount().negate() : entry.getAmount();
            
            balanceChanges.merge(entry.getAccountId(), change, BigDecimal::add);
        }
        
        // Apply balance changes
        for (Map.Entry<UUID, BigDecimal> balanceChange : balanceChanges.entrySet()) {
            UUID accountId = balanceChange.getKey();
            BigDecimal change = balanceChange.getValue();
            
            AccountBalance accountBalance = getOrCreateAccountBalance(accountId);
            
            accountBalance.setCurrentBalance(accountBalance.getCurrentBalance().add(change));
            accountBalance.setAvailableBalance(accountBalance.getAvailableBalance().add(change));
            accountBalance.setLastUpdated(LocalDateTime.now());
            
            accountBalanceRepository.save(accountBalance);
            
            // Invalidate cache
            redisTemplate.delete(BALANCE_CACHE_PREFIX + accountId);
        }
    }

    private void validatePostTransactionBalances(List<LedgerEntry> entries) {
        for (LedgerEntry entry : entries) {
            AccountBalance balance = getOrCreateAccountBalance(entry.getAccountId());
            
            // Verify balance is not negative for asset accounts
            if (isAssetAccount(entry.getAccountId()) && 
                balance.getCurrentBalance().compareTo(BigDecimal.ZERO) < 0) {
                throw new NegativeBalanceException("Negative balance detected for asset account: " + 
                    entry.getAccountId());
            }
        }
    }

    private AccountBalance getOrCreateAccountBalance(UUID accountId) {
        return accountBalanceRepository.findByAccountId(accountId)
            .orElseGet(() -> {
                AccountBalance newBalance = AccountBalance.builder()
                    .accountId(accountId)
                    .currentBalance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .pendingBalance(BigDecimal.ZERO)
                    .reservedBalance(BigDecimal.ZERO)
                    .currency("USD") // Default currency
                    .lastUpdated(LocalDateTime.now())
                    .build();
                
                return accountBalanceRepository.save(newBalance);
            });
    }

    private boolean balanceNeedsUpdate(AccountBalance stored, BalanceCalculationResult calculated) {
        return !stored.getCurrentBalance().equals(calculated.getCurrentBalance());
    }

    private void updateAccountBalance(AccountBalance accountBalance, BalanceCalculationResult calculatedBalance) {
        accountBalance.setCurrentBalance(calculatedBalance.getCurrentBalance());
        accountBalance.setAvailableBalance(calculatedBalance.getAvailableBalance());
        accountBalance.setPendingBalance(calculatedBalance.getPendingBalance());
        accountBalance.setReservedBalance(calculatedBalance.getReservedBalance());
        accountBalance.setLastUpdated(calculatedBalance.getLastUpdated());
        
        accountBalanceRepository.save(accountBalance);
    }

    private boolean isAssetAccount(UUID accountId) {
        // Implementation would check account type from chart of accounts
        return chartOfAccountsService.isAssetAccount(accountId);
    }

    private List<LedgerEntryResponse> mapToLedgerEntryResponses(List<LedgerEntry> entries) {
        return entries.stream()
            .map(this::mapToLedgerEntryResponse)
            .collect(Collectors.toList());
    }

    private LedgerEntryResponse mapToLedgerEntryResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
            .ledgerId(entry.getLedgerId())
            .transactionId(entry.getTransactionId())
            .accountId(entry.getAccountId())
            .entryType(entry.getEntryType().toString())
            .amount(entry.getAmount())
            .runningBalance(entry.getRunningBalance())
            .referenceNumber(entry.getReferenceNumber())
            .description(entry.getDescription())
            .transactionDate(entry.getTransactionDate())
            .valueDate(entry.getValueDate())
            .currency(entry.getCurrency())
            .status(entry.getStatus().toString())
            .build();
    }
    
    /**
     * Creates secure JSON metadata to prevent injection attacks
     * @param key The metadata key
     * @param value The metadata value
     * @return Properly escaped JSON string
     */
    private String createSecureMetadata(String key, String value) {
        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put(key, value);
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to create secure metadata", e);
            // Return empty JSON object on failure to prevent null issues
            return "{}";
        }
    }
    
    /**
     * Creates secure JSON metadata with multiple key-value pairs
     * @param metadata Map of metadata key-value pairs
     * @return Properly escaped JSON string
     */
    private String createSecureMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to create secure metadata", e);
            return "{}";
        }
    }
}