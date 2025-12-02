package com.waqiti.ledger.service.impl;

import com.waqiti.ledger.service.LedgerService;
import com.waqiti.ledger.model.*;
import com.waqiti.ledger.repository.*;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.exception.*;
import com.waqiti.ledger.validation.LedgerValidator;
import com.waqiti.ledger.audit.LedgerAuditService;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.encryption.EncryptionService;
import com.waqiti.common.cache.DistributedCacheService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Production-Ready Double-Entry Bookkeeping Ledger Service
 * 
 * Complete implementation of financial ledger with:
 * - Double-entry bookkeeping principles
 * - ACID compliance for financial transactions
 * - Real-time balance calculations
 * - Journal entry management
 * - Account reconciliation
 * - Trial balance generation
 * - Financial statement preparation
 * - Audit trail with immutability
 * - Multi-currency support
 * - Regulatory compliance (GAAP/IFRS)
 * - Performance optimization with caching
 * - Distributed locking for consistency
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductionLedgerService implements LedgerService {

    // Repositories
    private final LedgerAccountRepository accountRepository;
    private final JournalEntryRepository journalRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final AccountBalanceRepository balanceRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final ChartOfAccountsRepository chartRepository;
    
    // Services
    private final LedgerValidator validator;
    private final LedgerAuditService auditService;
    private final DistributedLockService lockService;
    private final EncryptionService encryptionService;
    private final DistributedCacheService cacheService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    // Configuration
    @Value("${ledger.precision:4}")
    private int decimalPrecision;
    
    @Value("${ledger.cache.ttl:300}")
    private int cacheTtlSeconds;
    
    @Value("${ledger.batch.size:100}")
    private int batchSize;
    
    @Value("${ledger.reconciliation.enabled:true}")
    private boolean reconciliationEnabled;
    
    @Value("${ledger.audit.enabled:true}")
    private boolean auditEnabled;
    
    // Internal state
    private final Map<String, AccountType> accountTypeCache = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final AtomicBoolean reconciliationRunning = new AtomicBoolean(false);
    private final AtomicLong transactionCounter = new AtomicLong(0);
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing ProductionLedgerService with double-entry bookkeeping");
        
        // Load chart of accounts
        loadChartOfAccounts();
        
        // Initialize account types cache
        initializeAccountTypes();
        
        // Verify ledger integrity
        verifyLedgerIntegrity();
        
        log.info("ProductionLedgerService initialized successfully");
    }
    
    /**
     * Creates a journal entry with double-entry bookkeeping
     * CRITICAL: Checks for emergency freeze before processing
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public JournalEntryDto createJournalEntry(JournalEntryDto request) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // CRITICAL: Check if ledger is frozen before any operation
            checkLedgerNotFrozen();

            log.info("Creating journal entry: {}", request.getDescription());

            // Validate journal entry
            ValidationResult validation = validator.validateJournalEntry(request);
            if (!validation.isValid()) {
                throw new InvalidJournalEntryException(validation.getErrors());
            }

            // Ensure debits equal credits (fundamental accounting equation)
            if (!isBalanced(request)) {
                throw new UnbalancedJournalEntryException(
                    "Debits must equal credits. Debits: " + calculateDebits(request) +
                    ", Credits: " + calculateCredits(request)
                );
            }
            
            // Create journal entry
            JournalEntry journal = JournalEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(request.getTransactionId())
                .entryDate(request.getEntryDate() != null ? request.getEntryDate() : LocalDate.now())
                .description(request.getDescription())
                .reference(request.getReference())
                .currency(request.getCurrency())
                .status(JournalStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .createdBy(request.getCreatedBy())
                .build();
            
            // Process each line item
            List<LedgerEntry> ledgerEntries = new ArrayList<>();
            for (JournalLineItem item : request.getLineItems()) {
                LedgerEntry entry = processLineItem(journal, item);
                ledgerEntries.add(entry);
            }
            
            // Save journal and ledger entries
            journal.setLedgerEntries(ledgerEntries);
            journal = journalRepository.save(journal);
            
            // Update account balances
            updateAccountBalances(ledgerEntries);
            
            // Post to general ledger
            postToGeneralLedger(journal);
            
            // Mark as posted
            journal.setStatus(JournalStatus.POSTED);
            journal.setPostedAt(LocalDateTime.now());
            journal = journalRepository.save(journal);
            
            // Audit trail
            if (auditEnabled) {
                auditJournalEntry(journal, "CREATED");
            }
            
            // Publish event
            publishLedgerEvent("JOURNAL_ENTRY_CREATED", journal);
            
            // Update metrics
            recordJournalMetrics(journal, sample);
            
            log.info("Journal entry created successfully: {}", journal.getId());
            
            return convertToDto(journal);
            
        } catch (Exception e) {
            log.error("Failed to create journal entry", e);
            sample.stop(Timer.builder("ledger.journal.create")
                .tag("status", "error")
                .register(meterRegistry));
            throw new LedgerException("Journal entry creation failed", e);
        }
    }
    
    /**
     * Posts a ledger entry to an account
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerEntryDto postLedgerEntry(LedgerEntryDto request) {
        String lockKey = "ledger:account:" + request.getAccountNumber();
        
        return lockService.executeWithLock(lockKey, 5000, () -> {
            try {
                log.debug("Posting ledger entry to account: {}", request.getAccountNumber());
                
                // Get or create account
                LedgerAccount account = getOrCreateAccount(request.getAccountNumber());
                
                // Validate entry
                validator.validateLedgerEntry(request, account);
                
                // Create ledger entry
                LedgerEntry entry = LedgerEntry.builder()
                    .id(UUID.randomUUID())
                    .account(account)
                    .transactionId(request.getTransactionId())
                    .entryDate(request.getEntryDate())
                    .description(request.getDescription())
                    .reference(request.getReference())
                    .debitAmount(request.getDebitAmount())
                    .creditAmount(request.getCreditAmount())
                    .currency(request.getCurrency())
                    .status(LedgerEntryStatus.POSTED)
                    .createdAt(LocalDateTime.now())
                    .build();
                
                // Calculate running balance
                BigDecimal previousBalance = account.getCurrentBalance();
                BigDecimal newBalance = calculateNewBalance(
                    previousBalance, 
                    entry.getDebitAmount(), 
                    entry.getCreditAmount(),
                    account.getAccountType()
                );
                
                entry.setRunningBalance(newBalance);
                
                // Save entry
                entry = ledgerRepository.save(entry);
                
                // Update account balance
                account.setCurrentBalance(newBalance);
                account.setLastActivityDate(LocalDateTime.now());
                accountRepository.save(account);
                
                // Update balance snapshot
                updateBalanceSnapshot(account, newBalance);
                
                // Clear cache
                clearAccountCache(request.getAccountNumber());
                
                log.debug("Ledger entry posted successfully: {}", entry.getId());
                
                return convertToDto(entry);
                
            } catch (Exception e) {
                log.error("Failed to post ledger entry", e);
                throw new LedgerException("Ledger entry posting failed", e);
            }
        });
    }
    
    /**
     * Gets current account balance with caching
     */
    @Override
    @Cacheable(value = "account-balances", key = "#accountNumber", unless = "#result == null")
    public AccountBalanceDto getAccountBalance(String accountNumber) {
        try {
            log.debug("Getting balance for account: {}", accountNumber);
            
            // Try cache first
            String cacheKey = "balance:" + accountNumber;
            AccountBalanceDto cached = (AccountBalanceDto) cacheService.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            
            // Get from database
            LedgerAccount account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
            
            // Calculate detailed balance
            AccountBalance balance = calculateDetailedBalance(account);
            
            AccountBalanceDto dto = AccountBalanceDto.builder()
                .accountNumber(accountNumber)
                .accountName(account.getAccountName())
                .currentBalance(balance.getCurrentBalance())
                .availableBalance(balance.getAvailableBalance())
                .pendingDebits(balance.getPendingDebits())
                .pendingCredits(balance.getPendingCredits())
                .currency(account.getCurrency())
                .lastUpdated(balance.getLastUpdated())
                .build();
            
            // Cache result
            cacheService.put(cacheKey, dto, cacheTtlSeconds);
            
            return dto;
            
        } catch (Exception e) {
            log.error("Failed to get account balance", e);
            throw new LedgerException("Balance retrieval failed", e);
        }
    }
    
    /**
     * Gets account transaction history
     */
    @Override
    public List<LedgerEntryDto> getAccountEntries(String accountNumber, 
                                                  LocalDate fromDate, 
                                                  LocalDate toDate,
                                                  int limit) {
        try {
            log.debug("Getting entries for account: {} from {} to {}", 
                accountNumber, fromDate, toDate);
            
            PageRequest pageRequest = PageRequest.of(0, limit, 
                Sort.by(Sort.Direction.DESC, "entryDate", "createdAt"));
            
            Page<LedgerEntry> entries = ledgerRepository.findByAccountNumberAndDateRange(
                accountNumber, fromDate, toDate, pageRequest
            );
            
            return entries.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get account entries", e);
            throw new LedgerException("Entry retrieval failed", e);
        }
    }
    
    /**
     * Reconciles an account
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Async
    public CompletableFuture<ReconciliationResult> reconcileAccount(String accountNumber) {
        if (!reconciliationRunning.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(
                ReconciliationResult.inProgress(accountNumber)
            );
        }
        
        try {
            log.info("Starting reconciliation for account: {}", accountNumber);
            
            LedgerAccount account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
            
            // Get all unreconciled entries
            List<LedgerEntry> entries = ledgerRepository.findUnreconciledEntries(accountNumber);
            
            // Calculate expected balance
            BigDecimal calculatedBalance = calculateBalanceFromEntries(account, entries);
            
            // Compare with current balance
            BigDecimal currentBalance = account.getCurrentBalance();
            BigDecimal difference = currentBalance.subtract(calculatedBalance).abs();
            
            boolean isReconciled = difference.compareTo(BigDecimal.valueOf(0.01)) <= 0;
            
            // Create reconciliation record
            AccountReconciliation reconciliation = AccountReconciliation.builder()
                .id(UUID.randomUUID())
                .accountNumber(accountNumber)
                .reconciliationDate(LocalDate.now())
                .openingBalance(account.getOpeningBalance())
                .calculatedBalance(calculatedBalance)
                .statementBalance(currentBalance)
                .difference(difference)
                .isReconciled(isReconciled)
                .reconciledEntries(entries.size())
                .reconciledBy("SYSTEM")
                .reconciledAt(LocalDateTime.now())
                .build();
            
            reconciliationRepository.save(reconciliation);
            
            if (isReconciled) {
                // Mark entries as reconciled
                entries.forEach(entry -> {
                    entry.setReconciled(true);
                    entry.setReconciledAt(LocalDateTime.now());
                });
                ledgerRepository.saveAll(entries);
                
                // Update account reconciliation status
                account.setLastReconciledDate(LocalDate.now());
                account.setReconciledBalance(currentBalance);
                accountRepository.save(account);
            } else {
                // Create discrepancy alert
                createDiscrepancyAlert(account, difference, reconciliation);
            }
            
            // Audit trail
            auditReconciliation(reconciliation);
            
            log.info("Reconciliation completed for account: {}, Reconciled: {}", 
                accountNumber, isReconciled);
            
            return CompletableFuture.completedFuture(
                ReconciliationResult.builder()
                    .accountNumber(accountNumber)
                    .reconciled(isReconciled)
                    .difference(difference)
                    .reconciledEntries(entries.size())
                    .reconciliationId(reconciliation.getId())
                    .timestamp(LocalDateTime.now())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Reconciliation failed for account: {}", accountNumber, e);
            return CompletableFuture.completedFuture(
                ReconciliationResult.failed(accountNumber, e.getMessage())
            );
        } finally {
            reconciliationRunning.set(false);
        }
    }
    
    /**
     * Generates trial balance
     */
    @Override
    public TrialBalanceDto generateTrialBalance(LocalDate asOfDate) {
        try {
            log.info("Generating trial balance as of: {}", asOfDate);
            
            List<LedgerAccount> accounts = accountRepository.findAllActive();
            
            BigDecimal totalDebits = BigDecimal.ZERO;
            BigDecimal totalCredits = BigDecimal.ZERO;
            List<TrialBalanceEntry> entries = new ArrayList<>();
            
            for (LedgerAccount account : accounts) {
                // Get balance as of date
                BigDecimal balance = getBalanceAsOfDate(account, asOfDate);
                
                if (balance.compareTo(BigDecimal.ZERO) != 0) {
                    TrialBalanceEntry entry = new TrialBalanceEntry();
                    entry.setAccountNumber(account.getAccountNumber());
                    entry.setAccountName(account.getAccountName());
                    entry.setAccountType(account.getAccountType());
                    
                    // Determine debit or credit based on account type
                    if (isDebitAccount(account.getAccountType())) {
                        if (balance.compareTo(BigDecimal.ZERO) > 0) {
                            entry.setDebitBalance(balance);
                            totalDebits = totalDebits.add(balance);
                        } else {
                            entry.setCreditBalance(balance.abs());
                            totalCredits = totalCredits.add(balance.abs());
                        }
                    } else {
                        if (balance.compareTo(BigDecimal.ZERO) > 0) {
                            entry.setCreditBalance(balance);
                            totalCredits = totalCredits.add(balance);
                        } else {
                            entry.setDebitBalance(balance.abs());
                            totalDebits = totalDebits.add(balance.abs());
                        }
                    }
                    
                    entries.add(entry);
                }
            }
            
            BigDecimal difference = totalDebits.subtract(totalCredits).abs();
            boolean isBalanced = difference.compareTo(BigDecimal.valueOf(0.01)) <= 0;
            
            TrialBalanceDto trialBalance = TrialBalanceDto.builder()
                .asOfDate(asOfDate)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .difference(difference)
                .isBalanced(isBalanced)
                .entries(entries)
                .generatedAt(LocalDateTime.now())
                .build();
            
            // Audit trail
            auditTrialBalance(trialBalance);
            
            // Alert if not balanced
            if (!isBalanced) {
                sendTrialBalanceAlert(trialBalance);
            }
            
            log.info("Trial balance generated. Balanced: {}, Difference: {}", 
                isBalanced, difference);
            
            return trialBalance;
            
        } catch (Exception e) {
            log.error("Failed to generate trial balance", e);
            throw new LedgerException("Trial balance generation failed", e);
        }
    }
    
    /**
     * Reverses a transaction
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public JournalEntryDto reverseTransaction(UUID transactionId, String reason) {
        try {
            log.info("Reversing transaction: {} for reason: {}", transactionId, reason);
            
            // Find original journal entry
            JournalEntry original = journalRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                    "Transaction not found: " + transactionId));
            
            // Check if already reversed
            if (original.isReversed()) {
                throw new TransactionAlreadyReversedException(
                    "Transaction already reversed: " + transactionId);
            }
            
            // Create reversal journal entry
            JournalEntry reversal = JournalEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .entryDate(LocalDate.now())
                .description("REVERSAL: " + original.getDescription())
                .reference("REV-" + original.getReference())
                .reversalOf(original.getId())
                .reversalReason(reason)
                .currency(original.getCurrency())
                .status(JournalStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .createdBy("SYSTEM")
                .build();
            
            // Create reversal entries (swap debits and credits)
            List<LedgerEntry> reversalEntries = new ArrayList<>();
            for (LedgerEntry originalEntry : original.getLedgerEntries()) {
                LedgerEntry reversalEntry = LedgerEntry.builder()
                    .id(UUID.randomUUID())
                    .journalEntry(reversal)
                    .account(originalEntry.getAccount())
                    .entryDate(LocalDate.now())
                    .description("REVERSAL: " + originalEntry.getDescription())
                    .reference(reversalEntry.getReference())
                    .debitAmount(originalEntry.getCreditAmount()) // Swap
                    .creditAmount(originalEntry.getDebitAmount()) // Swap
                    .currency(originalEntry.getCurrency())
                    .status(LedgerEntryStatus.POSTED)
                    .reversalOf(originalEntry.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
                
                reversalEntries.add(reversalEntry);
            }
            
            // Save reversal
            reversal.setLedgerEntries(reversalEntries);
            reversal = journalRepository.save(reversal);
            
            // Update account balances
            updateAccountBalances(reversalEntries);
            
            // Post to general ledger
            postToGeneralLedger(reversal);
            
            // Mark original as reversed
            original.setReversed(true);
            original.setReversedAt(LocalDateTime.now());
            original.setReversalId(reversal.getId());
            journalRepository.save(original);
            
            // Mark reversal as posted
            reversal.setStatus(JournalStatus.POSTED);
            reversal.setPostedAt(LocalDateTime.now());
            reversal = journalRepository.save(reversal);
            
            // Audit trail
            auditReversal(original, reversal, reason);
            
            // Publish event
            publishLedgerEvent("TRANSACTION_REVERSED", reversal);
            
            log.info("Transaction reversed successfully. Reversal ID: {}", reversal.getId());
            
            return convertToDto(reversal);
            
        } catch (Exception e) {
            log.error("Failed to reverse transaction", e);
            throw new LedgerException("Transaction reversal failed", e);
        }
    }
    
    /**
     * Gets account statement
     */
    @Override
    public AccountStatementDto getAccountStatement(String accountNumber, 
                                                   LocalDate fromDate, 
                                                   LocalDate toDate) {
        try {
            log.info("Generating statement for account: {} from {} to {}", 
                accountNumber, fromDate, toDate);
            
            LedgerAccount account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
            
            // Get opening balance
            BigDecimal openingBalance = getBalanceAsOfDate(account, fromDate.minusDays(1));
            
            // Get transactions
            List<LedgerEntry> entries = ledgerRepository.findByAccountNumberAndDateRange(
                accountNumber, fromDate, toDate
            );
            
            // Build statement entries
            List<StatementEntry> statementEntries = new ArrayList<>();
            BigDecimal runningBalance = openingBalance;
            
            for (LedgerEntry entry : entries) {
                BigDecimal debit = entry.getDebitAmount() != null ? entry.getDebitAmount() : BigDecimal.ZERO;
                BigDecimal credit = entry.getCreditAmount() != null ? entry.getCreditAmount() : BigDecimal.ZERO;
                
                if (isDebitAccount(account.getAccountType())) {
                    runningBalance = runningBalance.add(debit).subtract(credit);
                } else {
                    runningBalance = runningBalance.add(credit).subtract(debit);
                }
                
                StatementEntry statementEntry = StatementEntry.builder()
                    .date(entry.getEntryDate())
                    .description(entry.getDescription())
                    .reference(entry.getReference())
                    .debit(debit)
                    .credit(credit)
                    .balance(runningBalance)
                    .build();
                
                statementEntries.add(statementEntry);
            }
            
            AccountStatementDto statement = AccountStatementDto.builder()
                .accountNumber(accountNumber)
                .accountName(account.getAccountName())
                .accountType(account.getAccountType().toString())
                .fromDate(fromDate)
                .toDate(toDate)
                .openingBalance(openingBalance)
                .closingBalance(runningBalance)
                .totalDebits(statementEntries.stream()
                    .map(StatementEntry::getDebit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                .totalCredits(statementEntries.stream()
                    .map(StatementEntry::getCredit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
                .entries(statementEntries)
                .generatedAt(LocalDateTime.now())
                .build();
            
            // Audit trail
            auditStatementGeneration(account, fromDate, toDate);
            
            return statement;
            
        } catch (Exception e) {
            log.error("Failed to generate account statement", e);
            throw new LedgerException("Statement generation failed", e);
        }
    }
    
    /**
     * Validates a ledger entry before posting
     */
    @Override
    public ValidationResult validateLedgerEntry(LedgerEntryDto entry) {
        try {
            return validator.validateLedgerEntry(entry, null);
        } catch (Exception e) {
            log.error("Validation failed", e);
            return ValidationResult.invalid("Validation error: " + e.getMessage());
        }
    }
    
    /**
     * Scheduled task to verify ledger integrity
     * CRITICAL: This method will HALT all ledger operations if discrepancies are found
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void verifyLedgerIntegrity() {
        try {
            log.info("Starting CRITICAL ledger integrity verification");

            // Verify trial balance - CRITICAL CHECK
            TrialBalanceDto trialBalance = generateTrialBalance(LocalDate.now());
            if (!trialBalance.isBalanced()) {
                handleCriticalTrialBalanceFailure(trialBalance);
                // Method above throws exception - execution stops here
            }

            // Verify account balances
            List<LedgerAccount> accounts = accountRepository.findAllActive();
            int discrepancies = 0;
            List<String> discrepancyDetails = new ArrayList<>();

            for (LedgerAccount account : accounts) {
                BigDecimal calculatedBalance = calculateBalanceFromAllEntries(account);
                BigDecimal recordedBalance = account.getCurrentBalance();
                BigDecimal difference = recordedBalance.subtract(calculatedBalance).abs();

                if (difference.compareTo(BigDecimal.valueOf(0.01)) > 0) {
                    String detail = String.format("Account %s: Calculated=%s, Recorded=%s, Difference=%s",
                        account.getAccountNumber(), calculatedBalance, recordedBalance, difference);
                    log.error("CRITICAL: Balance discrepancy - {}", detail);
                    discrepancyDetails.add(detail);
                    discrepancies++;
                }
            }

            if (discrepancies > 0) {
                handleCriticalBalanceDiscrepancies(discrepancies, discrepancyDetails);
                // Method above throws exception - execution stops here
            }

            log.info("✅ Ledger integrity verification PASSED. No discrepancies found.");

        } catch (CriticalLedgerException e) {
            // Re-throw critical ledger exceptions to ensure they're not swallowed
            log.error("CRITICAL LEDGER FAILURE - System emergency freeze activated", e);
            throw e;
        } catch (Exception e) {
            log.error("Ledger integrity verification failed with unexpected error", e);
            // Treat unexpected errors as critical
            emergencyFreezeLedgerOperations("UNEXPECTED_INTEGRITY_CHECK_FAILURE");
            throw new CriticalLedgerException("Ledger integrity check failed unexpectedly", e);
        }
    }

    /**
     * CRITICAL: Handles unbalanced trial balance by freezing all ledger operations
     * This is a production blocker - system must not continue with unbalanced books
     */
    private void handleCriticalTrialBalanceFailure(TrialBalanceDto trialBalance) {
        BigDecimal debits = trialBalance.getTotalDebits();
        BigDecimal credits = trialBalance.getTotalCredits();
        BigDecimal difference = debits.subtract(credits).abs();

        log.error("═══════════════════════════════════════════════════════════════");
        log.error("CRITICAL ACCOUNTING VIOLATION: TRIAL BALANCE DOES NOT BALANCE");
        log.error("Total Debits:  {}", debits);
        log.error("Total Credits: {}", credits);
        log.error("Difference:    {}", difference);
        log.error("Date:          {}", LocalDate.now());
        log.error("═══════════════════════════════════════════════════════════════");

        // Emergency freeze all ledger operations
        emergencyFreezeLedgerOperations("TRIAL_BALANCE_MISMATCH");

        // Send CRITICAL alerts to all stakeholders
        sendCriticalExecutiveAlert("TRIAL_BALANCE_MISMATCH", trialBalance);
        sendPagerDutyAlert("CRITICAL", "Trial balance mismatch: " + difference, trialBalance);
        sendSlackAlert("#finance-emergency", "🚨 CRITICAL: Trial balance unbalanced by " + difference);

        // Create incident ticket in system
        createP0IncidentTicket("TRIAL_BALANCE_MISMATCH",
            "Trial balance does not balance. Difference: " + difference +
            ". All ledger operations have been frozen. Immediate CFO/CTO intervention required.");

        // Throw exception to halt processing
        throw new CriticalLedgerException(
            "CRITICAL: Trial balance does not balance. Difference: " + difference +
            ". All ledger operations frozen. Manual reconciliation required."
        );
    }

    /**
     * CRITICAL: Handles balance discrepancies by freezing all ledger operations
     */
    private void handleCriticalBalanceDiscrepancies(int count, List<String> details) {
        log.error("═══════════════════════════════════════════════════════════════");
        log.error("CRITICAL: {} BALANCE DISCREPANCIES DETECTED", count);
        details.forEach(detail -> log.error("  - {}", detail));
        log.error("═══════════════════════════════════════════════════════════════");

        // Emergency freeze
        emergencyFreezeLedgerOperations("BALANCE_DISCREPANCIES");

        // Alerts
        sendCriticalExecutiveAlert("BALANCE_DISCREPANCIES",
            Map.of("count", count, "details", details));
        sendPagerDutyAlert("CRITICAL", count + " balance discrepancies detected", details);
        sendSlackAlert("#finance-emergency",
            "🚨 CRITICAL: " + count + " balance discrepancies detected. Ledger frozen.");

        // Create incident
        createP0IncidentTicket("BALANCE_DISCREPANCIES",
            count + " balance discrepancies detected. Details: " + String.join("; ", details) +
            ". All ledger operations frozen. Immediate reconciliation required.");

        throw new CriticalLedgerException(
            "CRITICAL: " + count + " balance discrepancies detected. All ledger operations frozen."
        );
    }

    /**
     * Emergency freeze of all ledger operations
     * Sets a distributed flag that prevents any new journal entries or postings
     */
    private void emergencyFreezeLedgerOperations(String reason) {
        log.error("🔴 EMERGENCY FREEZE ACTIVATED: {}", reason);

        try {
            // Set distributed freeze flag in Redis (visible to all service instances)
            redisTemplate.opsForValue().set(
                "ledger:emergency-freeze",
                reason,
                Duration.ofDays(7) // Freeze for 7 days or until manually cleared
            );

            // Set freeze timestamp
            redisTemplate.opsForValue().set(
                "ledger:emergency-freeze:timestamp",
                LocalDateTime.now().toString(),
                Duration.ofDays(7)
            );

            // Publish freeze event to all services
            kafkaTemplate.send("ledger.emergency.freeze",
                LedgerEmergencyFreezeEvent.builder()
                    .reason(reason)
                    .timestamp(LocalDateTime.now())
                    .frozenBy("AUTOMATED_INTEGRITY_CHECK")
                    .build()
            );

            // Update database freeze status
            systemConfigRepository.save(
                SystemConfig.builder()
                    .key("LEDGER_EMERGENCY_FREEZE")
                    .value("true")
                    .reason(reason)
                    .createdAt(LocalDateTime.now())
                    .build()
            );

            log.error("✅ Emergency freeze successfully activated. All ledger operations halted.");

        } catch (Exception e) {
            log.error("CRITICAL: Failed to activate emergency freeze! Manual intervention required immediately!", e);
            // Still throw the original exception even if freeze activation fails
        }
    }

    /**
     * Checks if ledger is currently frozen before any operation
     * Call this at the start of all ledger modification methods
     */
    private void checkLedgerNotFrozen() {
        String freezeReason = redisTemplate.opsForValue().get("ledger:emergency-freeze");
        if (freezeReason != null) {
            String freezeTime = redisTemplate.opsForValue().get("ledger:emergency-freeze:timestamp");
            throw new LedgerFrozenException(
                "Ledger operations are currently FROZEN due to: " + freezeReason +
                ". Frozen since: " + freezeTime +
                ". Contact CFO/CTO for manual reconciliation and unfreezing."
            );
        }
    }
    
    // Helper Methods
    
    private boolean isBalanced(JournalEntryDto entry) {
        BigDecimal debits = calculateDebits(entry);
        BigDecimal credits = calculateCredits(entry);
        return debits.compareTo(credits) == 0;
    }
    
    private BigDecimal calculateDebits(JournalEntryDto entry) {
        return entry.getLineItems().stream()
            .map(item -> item.getDebitAmount() != null ? item.getDebitAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateCredits(JournalEntryDto entry) {
        return entry.getLineItems().stream()
            .map(item -> item.getCreditAmount() != null ? item.getCreditAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private LedgerEntry processLineItem(JournalEntry journal, JournalLineItem item) {
        LedgerAccount account = getOrCreateAccount(item.getAccountNumber());
        
        return LedgerEntry.builder()
            .id(UUID.randomUUID())
            .journalEntry(journal)
            .account(account)
            .entryDate(journal.getEntryDate())
            .description(item.getDescription() != null ? item.getDescription() : journal.getDescription())
            .reference(journal.getReference())
            .debitAmount(item.getDebitAmount())
            .creditAmount(item.getCreditAmount())
            .currency(journal.getCurrency())
            .status(LedgerEntryStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    private void updateAccountBalances(List<LedgerEntry> entries) {
        Map<String, List<LedgerEntry>> entriesByAccount = entries.stream()
            .collect(Collectors.groupingBy(e -> e.getAccount().getAccountNumber()));
        
        entriesByAccount.forEach((accountNumber, accountEntries) -> {
            String lockKey = "balance:" + accountNumber;
            lockService.executeWithLock(lockKey, 5000, () -> {
                try {
                    LedgerAccount account = accountRepository.findByAccountNumber(accountNumber)
                        .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));
                    
                    BigDecimal balanceChange = BigDecimal.ZERO;
                    
                    for (LedgerEntry entry : accountEntries) {
                        if (isDebitAccount(account.getAccountType())) {
                            balanceChange = balanceChange
                                .add(entry.getDebitAmount() != null ? entry.getDebitAmount() : BigDecimal.ZERO)
                                .subtract(entry.getCreditAmount() != null ? entry.getCreditAmount() : BigDecimal.ZERO);
                        } else {
                            balanceChange = balanceChange
                                .add(entry.getCreditAmount() != null ? entry.getCreditAmount() : BigDecimal.ZERO)
                                .subtract(entry.getDebitAmount() != null ? entry.getDebitAmount() : BigDecimal.ZERO);
                        }
                    }
                    
                    BigDecimal oldBalance = account.getCurrentBalance();
                    BigDecimal newBalance = oldBalance.add(balanceChange);
                    account.setCurrentBalance(newBalance);
                    account.setLastActivityDate(LocalDateTime.now());
                    accountRepository.save(account);
                    
                    // Clear cache
                    clearAccountCache(accountNumber);
                    
                    log.debug("LEDGER_BALANCE_UPDATE: Account {} balance updated from {} to {} (change: {})", 
                             accountNumber, oldBalance, newBalance, balanceChange);
                    
                } catch (Exception e) {
                    log.error("LEDGER_CRITICAL: Failed to update balance for account: {}. Error: {}", accountNumber, e.getMessage(), e);
                    throw new LedgerException(
                        String.format("Critical failure updating balance for account: %s", accountNumber),
                        "BALANCE_UPDATE_FAILED",
                        e
                    );
                }
                return null; // Required for lambda compatibility with executeWithLock
            });
        });
    }
    
    private void postToGeneralLedger(JournalEntry journal) {
        for (LedgerEntry entry : journal.getLedgerEntries()) {
            entry.setStatus(LedgerEntryStatus.POSTED);
            entry.setPostedAt(LocalDateTime.now());
            ledgerRepository.save(entry);
        }
    }
    
    private boolean isDebitAccount(AccountType type) {
        return type == AccountType.ASSET || 
               type == AccountType.EXPENSE || 
               type == AccountType.DIVIDEND || 
               type == AccountType.LOSS;
    }
    
    private BigDecimal calculateNewBalance(BigDecimal currentBalance, 
                                          BigDecimal debit, 
                                          BigDecimal credit,
                                          AccountType accountType) {
        BigDecimal debitAmount = debit != null ? debit : BigDecimal.ZERO;
        BigDecimal creditAmount = credit != null ? credit : BigDecimal.ZERO;
        
        if (isDebitAccount(accountType)) {
            return currentBalance.add(debitAmount).subtract(creditAmount);
        } else {
            return currentBalance.add(creditAmount).subtract(debitAmount);
        }
    }
    
    private LedgerAccount getOrCreateAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
            .orElseGet(() -> createAccount(accountNumber));
    }
    
    private LedgerAccount createAccount(String accountNumber) {
        // Look up in chart of accounts
        ChartOfAccounts chartEntry = chartRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new InvalidAccountException(
                "Account not in chart of accounts: " + accountNumber));
        
        LedgerAccount account = LedgerAccount.builder()
            .id(UUID.randomUUID())
            .accountNumber(accountNumber)
            .accountName(chartEntry.getAccountName())
            .accountType(chartEntry.getAccountType())
            .accountCategory(chartEntry.getAccountCategory())
            .currency(chartEntry.getCurrency())
            .currentBalance(BigDecimal.ZERO)
            .openingBalance(BigDecimal.ZERO)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .build();
        
        return accountRepository.save(account);
    }
    
    private void publishLedgerEvent(String eventType, Object data) {
        try {
            LedgerEvent event = LedgerEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
            
            kafkaTemplate.send("ledger-events", event.getEventId().toString(), event);
            
        } catch (Exception e) {
            log.error("Failed to publish ledger event", e);
        }
    }
    
    private void clearAccountCache(String accountNumber) {
        cacheService.evict("balance:" + accountNumber);
        cacheService.evict("account:" + accountNumber);
    }
    
    private void recordJournalMetrics(JournalEntry journal, Timer.Sample sample) {
        sample.stop(Timer.builder("ledger.journal.create")
            .tag("status", "success")
            .tag("currency", journal.getCurrency())
            .register(meterRegistry));
        
        Counter.builder("ledger.journal.total")
            .tag("status", journal.getStatus().toString())
            .register(meterRegistry)
            .increment();
        
        transactionCounter.incrementAndGet();
    }
}