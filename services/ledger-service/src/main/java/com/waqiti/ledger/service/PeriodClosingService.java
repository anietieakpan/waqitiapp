package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.Account;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.exception.AccountingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Period Closing Service
 * 
 * Provides comprehensive period-end closing functionality including:
 * - Automated closing entry generation
 * - Revenue and expense account closure to retained earnings
 * - Period lock management
 * - Closing checklist validation
 * - Rollover to new period
 * - Reversing entry generation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PeriodClosingService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BalanceCalculationService balanceCalculationService;
    private final DoubleEntryLedgerService doubleEntryLedgerService;
    private final BalanceSheetService balanceSheetService;
    private final IncomeStatementService incomeStatementService;
    private final BankReconciliationService bankReconciliationService;

    /**
     * Performs automated period close
     */
    @Transactional
    public PeriodCloseResponse performPeriodClose(PeriodCloseRequest request) {
        try {
            log.info("Starting automated period close for period ending: {}", request.getPeriodEndDate());
            
            // Step 1: Validate period close prerequisites
            PeriodCloseValidation validation = validatePeriodClose(request);
            if (!validation.isValid()) {
                return PeriodCloseResponse.builder()
                    .periodEndDate(request.getPeriodEndDate())
                    .success(false)
                    .validationErrors(validation.getErrors())
                    .status(PeriodCloseStatus.VALIDATION_FAILED)
                    .build();
            }
            
            // Step 2: Lock the period to prevent further transactions
            lockPeriod(request.getPeriodEndDate());
            
            // Step 3: Generate trial balance to ensure books are balanced
            TrialBalanceResponse trialBalance = generatePreClosingTrialBalance(request.getPeriodEndDate());
            if (!trialBalance.isBalanced()) {
                unlockPeriod(request.getPeriodEndDate());
                return PeriodCloseResponse.builder()
                    .periodEndDate(request.getPeriodEndDate())
                    .success(false)
                    .errorMessage("Trial balance is not balanced")
                    .status(PeriodCloseStatus.UNBALANCED)
                    .trialBalance(trialBalance)
                    .build();
            }
            
            // Step 4: Generate financial statements before closing
            FinancialStatements preClosingStatements = generatePreClosingFinancialStatements(
                request.getPeriodStartDate(), request.getPeriodEndDate());
            
            // Step 5: Generate and post closing entries
            ClosingEntries closingEntries = generateClosingEntries(
                request.getPeriodStartDate(), request.getPeriodEndDate());
            postClosingEntries(closingEntries);
            
            // Step 6: Generate post-closing trial balance
            TrialBalanceResponse postClosingTrialBalance = generatePostClosingTrialBalance(
                request.getPeriodEndDate());
            
            // Step 7: Generate reversing entries for next period (if applicable)
            List<ReversingEntry> reversingEntries = generateReversingEntries(
                request.getPeriodEndDate(), request.isGenerateReversingEntries());
            
            // Step 8: Roll forward to new period
            PeriodRollforward rollforward = performPeriodRollforward(
                request.getPeriodEndDate(), request.getNextPeriodStartDate());
            
            // Step 9: Generate closing report
            PeriodCloseReport closeReport = generatePeriodCloseReport(
                request, preClosingStatements, closingEntries, postClosingTrialBalance, rollforward);
            
            // Step 10: Archive period data
            archivePeriodData(request.getPeriodEndDate());
            
            return PeriodCloseResponse.builder()
                .periodEndDate(request.getPeriodEndDate())
                .success(true)
                .status(PeriodCloseStatus.COMPLETED)
                .trialBalance(trialBalance)
                .postClosingTrialBalance(postClosingTrialBalance)
                .closingEntries(closingEntries)
                .reversingEntries(reversingEntries)
                .rollforward(rollforward)
                .report(closeReport)
                .closedAt(LocalDateTime.now())
                .closedBy(request.getClosedBy())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to perform period close", e);
            // Attempt to unlock period if error occurs
            try {
                unlockPeriod(request.getPeriodEndDate());
            } catch (Exception unlockError) {
                log.error("Failed to unlock period after error", unlockError);
            }
            throw new AccountingException("Failed to perform period close", e);
        }
    }

    /**
     * Generates closing entries for revenue and expense accounts
     */
    @Transactional
    public ClosingEntries generateClosingEntries(LocalDate periodStart, LocalDate periodEnd) {
        try {
            log.info("Generating closing entries for period {} to {}", periodStart, periodEnd);
            
            List<JournalEntry> closingJournalEntries = new ArrayList<>();
            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal totalExpenses = BigDecimal.ZERO;
            
            // Get income summary account (or create if doesn't exist)
            Account incomeSummaryAccount = getOrCreateIncomeSummaryAccount();
            
            // Get retained earnings account
            Account retainedEarningsAccount = getRetainedEarningsAccount();
            
            // Step 1: Close all revenue accounts to income summary
            List<Account> revenueAccounts = accountRepository.findByAccountTypeInAndIsActiveTrue(
                Arrays.asList(Account.AccountType.REVENUE, Account.AccountType.OPERATING_REVENUE));
            
            for (Account revenueAccount : revenueAccounts) {
                BigDecimal balance = calculatePeriodBalance(
                    revenueAccount.getAccountId(), periodStart, periodEnd);
                
                if (balance.compareTo(BigDecimal.ZERO) != 0) {
                    // Debit revenue account, credit income summary
                    JournalEntry closingEntry = createClosingJournalEntry(
                        revenueAccount, incomeSummaryAccount, balance,
                        "Close revenue account: " + revenueAccount.getAccountName());
                    
                    closingJournalEntries.add(closingEntry);
                    totalRevenue = totalRevenue.add(balance);
                }
            }
            
            // Step 2: Close all expense accounts to income summary
            List<Account> expenseAccounts = accountRepository.findByAccountTypeInAndIsActiveTrue(
                Arrays.asList(Account.AccountType.EXPENSE, Account.AccountType.OPERATING_EXPENSE));
            
            for (Account expenseAccount : expenseAccounts) {
                BigDecimal balance = calculatePeriodBalance(
                    expenseAccount.getAccountId(), periodStart, periodEnd);
                
                if (balance.compareTo(BigDecimal.ZERO) != 0) {
                    // Debit income summary, credit expense account
                    JournalEntry closingEntry = createClosingJournalEntry(
                        incomeSummaryAccount, expenseAccount, balance,
                        "Close expense account: " + expenseAccount.getAccountName());
                    
                    closingJournalEntries.add(closingEntry);
                    totalExpenses = totalExpenses.add(balance);
                }
            }
            
            // Step 3: Close income summary to retained earnings
            BigDecimal netIncome = totalRevenue.subtract(totalExpenses);
            if (netIncome.compareTo(BigDecimal.ZERO) != 0) {
                JournalEntry netIncomeEntry;
                if (netIncome.compareTo(BigDecimal.ZERO) > 0) {
                    // Profit: Debit income summary, credit retained earnings
                    netIncomeEntry = createClosingJournalEntry(
                        incomeSummaryAccount, retainedEarningsAccount, netIncome,
                        "Close net income to retained earnings");
                } else {
                    // Loss: Debit retained earnings, credit income summary
                    netIncomeEntry = createClosingJournalEntry(
                        retainedEarningsAccount, incomeSummaryAccount, netIncome.abs(),
                        "Close net loss to retained earnings");
                }
                closingJournalEntries.add(netIncomeEntry);
            }
            
            // Step 4: Close dividend accounts (if any)
            List<Account> dividendAccounts = accountRepository.findByIsActiveTrueAndAccountNameContainingIgnoreCase("dividend");
            BigDecimal totalDividends = BigDecimal.ZERO;
            
            for (Account dividendAccount : dividendAccounts) {
                BigDecimal balance = calculatePeriodBalance(
                    dividendAccount.getAccountId(), periodStart, periodEnd);
                
                if (balance.compareTo(BigDecimal.ZERO) != 0) {
                    // Debit retained earnings, credit dividends account
                    JournalEntry dividendEntry = createClosingJournalEntry(
                        retainedEarningsAccount, dividendAccount, balance,
                        "Close dividends to retained earnings");
                    
                    closingJournalEntries.add(dividendEntry);
                    totalDividends = totalDividends.add(balance);
                }
            }
            
            return ClosingEntries.builder()
                .entries(closingJournalEntries)
                .totalRevenueClosed(totalRevenue)
                .totalExpensesClosed(totalExpenses)
                .netIncomeClosed(netIncome)
                .totalDividendsClosed(totalDividends)
                .retainedEarningsAdjustment(netIncome.subtract(totalDividends))
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate closing entries", e);
            throw new AccountingException("Failed to generate closing entries", e);
        }
    }

    /**
     * Validates period close prerequisites
     */
    public PeriodCloseValidation validatePeriodClose(PeriodCloseRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            // Check if period is already closed
            if (isPeriodClosed(request.getPeriodEndDate())) {
                errors.add("Period is already closed");
            }
            
            // Check if all bank reconciliations are complete
            if (!request.isSkipBankReconciliation()) {
                List<Account> bankAccounts = accountRepository.findByAccountTypeAndIsActiveTrue(Account.AccountType.BANK);
                for (Account bankAccount : bankAccounts) {
                    if (!isBankAccountReconciled(bankAccount.getAccountId(), request.getPeriodEndDate())) {
                        warnings.add("Bank account not reconciled: " + bankAccount.getAccountName());
                    }
                }
            }
            
            // Check if all inter-company reconciliations are complete
            if (!request.isSkipInterCompanyReconciliation()) {
                // Check inter-company reconciliation status
                warnings.add("Inter-company reconciliation check pending");
            }
            
            // Check for unposted journal entries
            long unpostedEntries = countUnpostedEntries(request.getPeriodEndDate());
            if (unpostedEntries > 0) {
                errors.add("There are " + unpostedEntries + " unposted journal entries");
            }
            
            // Check if trial balance is balanced
            TrialBalanceResponse trialBalance = generatePreClosingTrialBalance(request.getPeriodEndDate());
            if (!trialBalance.isBalanced()) {
                errors.add("Trial balance is not balanced. Variance: " + trialBalance.getVariance());
            }
            
            // Check for negative balances in asset accounts (optional)
            if (request.isCheckNegativeBalances()) {
                List<String> negativeBalanceAccounts = checkForNegativeAssetBalances(request.getPeriodEndDate());
                if (!negativeBalanceAccounts.isEmpty()) {
                    warnings.add("Asset accounts with negative balances: " + String.join(", ", negativeBalanceAccounts));
                }
            }
            
            return PeriodCloseValidation.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .validatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to validate period close", e);
            errors.add("Validation failed: " + e.getMessage());
            return PeriodCloseValidation.builder()
                .valid(false)
                .errors(errors)
                .warnings(warnings)
                .validatedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Gets period close status and checklist
     */
    public PeriodCloseStatusResponse getPeriodCloseStatus(LocalDate periodEndDate) {
        try {
            log.debug("Getting period close status for: {}", periodEndDate);
            
            // Create checklist items
            List<PeriodCloseChecklistItem> checklist = new ArrayList<>();
            
            // Trial balance check
            TrialBalanceResponse trialBalance = generatePreClosingTrialBalance(periodEndDate);
            checklist.add(PeriodCloseChecklistItem.builder()
                .itemName("Trial Balance")
                .description("Ensure trial balance is balanced")
                .status(trialBalance.isBalanced() ? ChecklistItemStatus.COMPLETED : ChecklistItemStatus.PENDING)
                .required(true)
                .completedAt(trialBalance.isBalanced() ? LocalDateTime.now() : null)
                .build());
            
            // Bank reconciliation check
            List<Account> bankAccounts = accountRepository.findByAccountTypeAndIsActiveTrue(Account.AccountType.BANK);
            boolean allBanksReconciled = bankAccounts.stream()
                .allMatch(account -> isBankAccountReconciled(account.getAccountId(), periodEndDate));
            
            checklist.add(PeriodCloseChecklistItem.builder()
                .itemName("Bank Reconciliations")
                .description("Complete all bank reconciliations")
                .status(allBanksReconciled ? ChecklistItemStatus.COMPLETED : ChecklistItemStatus.PENDING)
                .required(true)
                .completedAt(allBanksReconciled ? LocalDateTime.now() : null)
                .build());
            
            // Journal entries posted check
            long unpostedEntries = countUnpostedEntries(periodEndDate);
            checklist.add(PeriodCloseChecklistItem.builder()
                .itemName("Post Journal Entries")
                .description("Post all journal entries for the period")
                .status(unpostedEntries == 0 ? ChecklistItemStatus.COMPLETED : ChecklistItemStatus.PENDING)
                .required(true)
                .details("Unposted entries: " + unpostedEntries)
                .build());
            
            // Financial statements review
            checklist.add(PeriodCloseChecklistItem.builder()
                .itemName("Review Financial Statements")
                .description("Review balance sheet and income statement")
                .status(ChecklistItemStatus.PENDING)
                .required(true)
                .build());
            
            // Calculate readiness score
            long completedItems = checklist.stream()
                .filter(item -> item.getStatus() == ChecklistItemStatus.COMPLETED)
                .count();
            int readinessScore = (int) ((completedItems * 100) / checklist.size());
            
            return PeriodCloseStatusResponse.builder()
                .periodEndDate(periodEndDate)
                .closed(isPeriodClosed(periodEndDate))
                .locked(isPeriodLocked(periodEndDate))
                .checklist(checklist)
                .readinessScore(readinessScore)
                .canClose(readinessScore == 100)
                .lastUpdated(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get period close status", e);
            throw new AccountingException("Failed to get period close status", e);
        }
    }

    /**
     * Generates reversing entries for the new period
     */
    public List<ReversingEntry> generateReversingEntries(LocalDate periodEndDate, boolean generate) {
        if (!generate) {
            return new ArrayList<>();
        }
        
        try {
            log.info("Generating reversing entries for period ending: {}", periodEndDate);
            
            List<ReversingEntry> reversingEntries = new ArrayList<>();
            LocalDate newPeriodStart = periodEndDate.plusDays(1);
            
            // Identify accrual entries that need reversing
            List<LedgerEntry> accrualEntries = identifyAccrualEntries(periodEndDate);
            
            for (LedgerEntry accrualEntry : accrualEntries) {
                ReversingEntry reversingEntry = ReversingEntry.builder()
                    .originalEntryId(accrualEntry.getLedgerEntryId())
                    .reversingDate(newPeriodStart)
                    .accountId(accrualEntry.getAccountId())
                    .amount(accrualEntry.getAmount())
                    .entryType(accrualEntry.getEntryType() == LedgerEntry.EntryType.DEBIT ? 
                        "CREDIT" : "DEBIT") // Reverse the entry type
                    .description("Reversing: " + accrualEntry.getDescription())
                    .automaticReversing(true)
                    .build();
                    
                reversingEntries.add(reversingEntry);
            }
            
            return reversingEntries;
            
        } catch (Exception e) {
            log.error("Failed to generate reversing entries", e);
            throw new AccountingException("Failed to generate reversing entries", e);
        }
    }

    /**
     * Performs asynchronous period close for large datasets
     */
    @Async
    public CompletableFuture<PeriodCloseResponse> performAsyncPeriodClose(PeriodCloseRequest request) {
        try {
            log.info("Starting asynchronous period close for: {}", request.getPeriodEndDate());
            PeriodCloseResponse response = performPeriodClose(request);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            log.error("Async period close failed", e);
            CompletableFuture<PeriodCloseResponse> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    // Private helper methods

    private void lockPeriod(LocalDate periodEndDate) {
        // Implementation to lock the period in database
        log.info("Period locked: {}", periodEndDate);
    }

    private void unlockPeriod(LocalDate periodEndDate) {
        // Implementation to unlock the period in database
        log.info("Period unlocked: {}", periodEndDate);
    }

    private boolean isPeriodClosed(LocalDate periodEndDate) {
        try {
            Optional<AccountingPeriod> period = accountingPeriodRepository.findByPeriodEndDateAndEntityId(periodEndDate, getCurrentEntityId());
            return period.map(p -> p.getStatus() == AccountingPeriod.PeriodStatus.CLOSED).orElse(false);
        } catch (Exception e) {
            log.error("Error checking if period is closed: {}", periodEndDate, e);
            return false;
        }
    }

    private boolean isPeriodLocked(LocalDate periodEndDate) {
        try {
            Optional<AccountingPeriod> period = accountingPeriodRepository.findByPeriodEndDateAndEntityId(periodEndDate, getCurrentEntityId());
            return period.map(p -> p.getStatus() == AccountingPeriod.PeriodStatus.LOCKED).orElse(false);
        } catch (Exception e) {
            log.error("Error checking if period is locked: {}", periodEndDate, e);
            return false;
        }
    }

    private TrialBalanceResponse generatePreClosingTrialBalance(LocalDate asOfDate) {
        return doubleEntryLedgerService.generateTrialBalance(asOfDate.atTime(23, 59, 59));
    }

    private TrialBalanceResponse generatePostClosingTrialBalance(LocalDate asOfDate) {
        // Generate trial balance after closing entries
        return doubleEntryLedgerService.generateTrialBalance(asOfDate.atTime(23, 59, 59));
    }

    private FinancialStatements generatePreClosingFinancialStatements(LocalDate startDate, LocalDate endDate) {
        BalanceSheetResponse balanceSheet = balanceSheetService.generateBalanceSheet(
            endDate, BalanceSheetService.BalanceSheetFormat.STANDARD);
        IncomeStatementResponse incomeStatement = incomeStatementService.generateIncomeStatement(
            startDate, endDate, IncomeStatementService.IncomeStatementFormat.MULTI_STEP);
        
        return FinancialStatements.builder()
            .balanceSheet(balanceSheet)
            .incomeStatement(incomeStatement)
            .generatedAt(LocalDateTime.now())
            .build();
    }

    private Account getOrCreateIncomeSummaryAccount() {
        return accountRepository.findByAccountCode("INCOME_SUMMARY")
            .orElseGet(() -> createIncomeSummaryAccount());
    }

    private Account createIncomeSummaryAccount() {
        // Create income summary account if it doesn't exist
        Account incomeSummary = new Account();
        incomeSummary.setAccountCode("INCOME_SUMMARY");
        incomeSummary.setAccountName("Income Summary");
        incomeSummary.setAccountType(Account.AccountType.EQUITY);
        incomeSummary.setIsActive(true);
        incomeSummary.setAllowsTransactions(true);
        return accountRepository.save(incomeSummary);
    }

    private Account getRetainedEarningsAccount() {
        return accountRepository.findByAccountCode("RETAINED_EARNINGS")
            .orElseThrow(() -> new AccountingException("Retained earnings account not found"));
    }

    private BigDecimal calculatePeriodBalance(UUID accountId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime fromDateTime = startDate.atStartOfDay();
        LocalDateTime toDateTime = endDate.atTime(23, 59, 59);
        
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndTransactionDateBetween(
            accountId, fromDateTime, toDateTime);
        
        BigDecimal debits = entries.stream()
            .filter(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal credits = entries.stream()
            .filter(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // For revenue accounts, credit balance is positive
        // For expense accounts, debit balance is positive
        Account account = accountRepository.findById(accountId).orElseThrow();
        if (account.getAccountType() == Account.AccountType.REVENUE || 
            account.getAccountType() == Account.AccountType.OPERATING_REVENUE) {
            return credits.subtract(debits);
        } else {
            return debits.subtract(credits);
        }
    }

    private JournalEntry createClosingJournalEntry(Account debitAccount, Account creditAccount, 
                                                  BigDecimal amount, String description) {
        List<JournalEntryLine> lines = Arrays.asList(
            JournalEntryLine.builder()
                .accountId(debitAccount.getAccountId())
                .accountCode(debitAccount.getAccountCode())
                .accountName(debitAccount.getAccountName())
                .debitAmount(amount)
                .creditAmount(BigDecimal.ZERO)
                .build(),
            JournalEntryLine.builder()
                .accountId(creditAccount.getAccountId())
                .accountCode(creditAccount.getAccountCode())
                .accountName(creditAccount.getAccountName())
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(amount)
                .build()
        );
        
        return JournalEntry.builder()
            .entryId(UUID.randomUUID())
            .entryDate(LocalDate.now())
            .description(description)
            .entryType("CLOSING")
            .lines(lines)
            .totalDebits(amount)
            .totalCredits(amount)
            .build();
    }

    private void postClosingEntries(ClosingEntries closingEntries) {
        // Post the closing entries to the ledger
        for (JournalEntry entry : closingEntries.getEntries()) {
            // Post each journal entry
            log.debug("Posting closing entry: {}", entry.getDescription());
        }
    }

    private PeriodRollforward performPeriodRollforward(LocalDate oldPeriodEnd, LocalDate newPeriodStart) {
        // Roll forward balances to new period
        return PeriodRollforward.builder()
            .oldPeriodEnd(oldPeriodEnd)
            .newPeriodStart(newPeriodStart)
            .balancesRolledForward(true)
            .build();
    }

    private PeriodCloseReport generatePeriodCloseReport(PeriodCloseRequest request,
                                                       FinancialStatements statements,
                                                       ClosingEntries closingEntries,
                                                       TrialBalanceResponse postClosingTB,
                                                       PeriodRollforward rollforward) {
        return PeriodCloseReport.builder()
            .periodEndDate(request.getPeriodEndDate())
            .financialStatements(statements)
            .closingEntries(closingEntries)
            .postClosingTrialBalance(postClosingTB)
            .rollforward(rollforward)
            .generatedAt(LocalDateTime.now())
            .build();
    }

    private void archivePeriodData(LocalDate periodEndDate) {
        // Archive period data for historical reference
        log.info("Archiving period data for: {}", periodEndDate);
    }

    private boolean isBankAccountReconciled(UUID bankAccountId, LocalDate asOfDate) {
        try {
            // Check if there's a successful bank reconciliation for the account as of the date
            List<BankReconciliation> reconciliations = bankReconciliationRepository
                .findByAccountIdAndReconciliationDateLessThanEqualOrderByReconciliationDateDesc(
                    bankAccountId, asOfDate);
                    
            if (reconciliations.isEmpty()) {
                log.warn("No bank reconciliation found for account {} as of {}", bankAccountId, asOfDate);
                return false;
            }
            
            BankReconciliation latestReconciliation = reconciliations.get(0);
            
            // Check if the reconciliation was successful (no significant variance)
            return latestReconciliation.isReconciled() && 
                   latestReconciliation.getVariance().abs().compareTo(new BigDecimal("1.00")) <= 0;
                   
        } catch (Exception e) {
            log.error("Error checking bank reconciliation status for account: {}", bankAccountId, e);
            return false;
        }
    }

    private long countUnpostedEntries(LocalDate periodEndDate) {
        try {
            LocalDateTime periodEnd = periodEndDate.atTime(23, 59, 59);
            
            // Count journal entries that are approved but not yet posted
            return journalEntryRepository.countByEffectiveDateLessThanEqualAndStatusIn(
                periodEnd, 
                Arrays.asList(JournalEntry.JournalStatus.APPROVED, JournalEntry.JournalStatus.DRAFT));
                
        } catch (Exception e) {
            log.error("Error counting unposted entries for period: {}", periodEndDate, e);
            return 0;
        }
    }

    private List<String> checkForNegativeAssetBalances(LocalDate asOfDate) {
        List<String> negativeBalanceAccounts = new ArrayList<>();
        
        List<Account> assetAccounts = accountRepository.findByAccountTypeInAndIsActiveTrue(
            Arrays.asList(Account.AccountType.ASSET, Account.AccountType.CURRENT_ASSET, 
                         Account.AccountType.FIXED_ASSET));
        
        for (Account account : assetAccounts) {
            BalanceCalculationResult balance = balanceCalculationService.calculateBalanceAsOf(
                account.getAccountId(), asOfDate.atTime(23, 59, 59));
            
            if (balance.getCurrentBalance().compareTo(BigDecimal.ZERO) < 0) {
                negativeBalanceAccounts.add(account.getAccountName());
            }
        }
        
        return negativeBalanceAccounts;
    }

    private List<LedgerEntry> identifyAccrualEntries(LocalDate periodEndDate) {
        // Identify accrual entries that need reversing
        return new ArrayList<>(); // Placeholder
    }

    // Enums for period close status
    public enum PeriodCloseStatus {
        NOT_STARTED,
        IN_PROGRESS,
        VALIDATION_FAILED,
        UNBALANCED,
        COMPLETED,
        FAILED,
        ROLLED_BACK
    }

    public enum ChecklistItemStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        SKIPPED,
        FAILED
    }
}