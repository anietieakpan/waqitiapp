package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.Account;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.exception.AccountingException;
import com.waqiti.ledger.exception.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive Accounting Service
 * 
 * Provides enterprise-grade accounting functionality including:
 * - Financial statement generation (Balance Sheet, Income Statement, Cash Flow)
 * - Chart of accounts management
 * - Account balance tracking and reporting
 * - Period-end closing procedures
 * - Financial analysis and reporting
 * - Compliance and audit support
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {

    @Lazy
    private final AccountingService self;

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final BalanceCalculationService balanceCalculationService;
    private final DoubleEntryLedgerService doubleEntryLedgerService;
    private final JournalEntryRepository journalEntryRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * Gets complete chart of accounts with balances (deprecated - use paginated version)
     * @deprecated Use {@link #getChartOfAccounts(String, Boolean, Pageable)} instead
     */
    @Deprecated
    @Cacheable("chartOfAccountsWithBalances")
    public List<LedgerAccountResponse> getChartOfAccounts(String accountType, Boolean activeOnly) {
        try {
            log.debug("Getting chart of accounts - type: {}, activeOnly: {}", accountType, activeOnly);

            List<Account> accounts;

            if (accountType != null) {
                Account.AccountType type = Account.AccountType.valueOf(accountType.toUpperCase());
                if (Boolean.TRUE.equals(activeOnly)) {
                    accounts = accountRepository.findByAccountTypeAndIsActiveTrueOrderByAccountCodeAsc(type);
                } else {
                    accounts = accountRepository.findByAccountTypeOrderByAccountCodeAsc(type);
                }
            } else {
                if (Boolean.TRUE.equals(activeOnly)) {
                    accounts = accountRepository.findByIsActiveTrueAndAllowsTransactionsTrue();
                } else {
                    accounts = accountRepository.findAllByOrderByAccountCodeAsc();
                }
            }

            return accounts.stream()
                .map(this::mapToLedgerAccountResponse)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to get chart of accounts", e);
            throw new AccountingException("Failed to retrieve chart of accounts", e);
        }
    }

    /**
     * Get chart of accounts with pagination and optional filtering
     *
     * Production-ready paginated endpoint for retrieving chart of accounts.
     * Prevents memory issues when dealing with large account hierarchies.
     *
     * @param accountType Optional account type filter (ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE)
     * @param activeOnly Whether to return only active accounts
     * @param pageable Pagination parameters (page, size, sort)
     * @return Paginated list of ledger account responses
     */
    @Cacheable(value = "chartOfAccountsPageable", key = "#accountType + '_' + #activeOnly + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<LedgerAccountResponse> getChartOfAccounts(String accountType, Boolean activeOnly, Pageable pageable) {
        try {
            log.debug("Getting paginated chart of accounts - type: {}, activeOnly: {}, page: {}, size: {}",
                    accountType, activeOnly, pageable.getPageNumber(), pageable.getPageSize());

            Page<Account> accountsPage;

            if (accountType != null) {
                Account.AccountType type = Account.AccountType.valueOf(accountType.toUpperCase());
                if (Boolean.TRUE.equals(activeOnly)) {
                    accountsPage = accountRepository.findByAccountTypeAndIsActiveTrue(type, pageable);
                } else {
                    accountsPage = accountRepository.findByAccountType(type, pageable);
                }
            } else {
                if (Boolean.TRUE.equals(activeOnly)) {
                    accountsPage = accountRepository.findByIsActiveTrue(pageable);
                } else {
                    accountsPage = accountRepository.findAll(pageable);
                }
            }

            return accountsPage.map(this::mapToLedgerAccountResponse);

        } catch (Exception e) {
            log.error("Failed to get paginated chart of accounts", e);
            throw new AccountingException("Failed to retrieve chart of accounts", e);
        }
    }

    /**
     * Creates a new ledger account
     */
    @Transactional
    public LedgerAccountResponse createLedgerAccount(CreateLedgerAccountRequest request) {
        try {
            log.info("Creating ledger account: {} - {}", request.getAccountCode(), request.getAccountName());
            
            CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountCode(request.getAccountCode())
                .accountName(request.getAccountName())
                .accountType(request.getAccountType())
                .parentAccountId(request.getParentAccountId())
                .description(request.getDescription())
                .allowsTransactions(request.isAllowsTransactions())
                .currency(request.getCurrency())
                .normalBalance(request.getNormalBalance())
                .build();
            
            CreateAccountResponse accountResponse = chartOfAccountsService.createAccount(accountRequest);
            
            if (!accountResponse.isSuccess()) {
                throw new AccountingException("Failed to create account: " + accountResponse.getErrorMessage());
            }
            
            Account account = accountRepository.findById(accountResponse.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Created account not found"));
            
            return mapToLedgerAccountResponse(account);
            
        } catch (AccountingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create ledger account", e);
            throw new AccountingException("Failed to create ledger account", e);
        }
    }

    /**
     * Gets detailed account information
     */
    public LedgerAccountDetailResponse getAccountDetails(String accountCode) {
        try {
            Account account = accountRepository.findByAccountCode(accountCode)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountCode));
            
            // Get current balance
            BalanceInquiryResponse balance = doubleEntryLedgerService.getAccountBalance(account.getAccountId());
            
            // Get recent transactions (last 30 days)
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            List<LedgerEntry> recentEntries = ledgerEntryRepository.findByAccountIdAndTransactionDateGreaterThanEqualOrderByTransactionDateDesc(
                account.getAccountId(), thirtyDaysAgo);
            
            // Calculate monthly statistics
            AccountStatistics statistics = calculateAccountStatistics(account.getAccountId());
            
            return LedgerAccountDetailResponse.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType().toString())
                .parentAccountId(account.getParentAccountId())
                .description(account.getDescription())
                .isActive(account.getIsActive())
                .allowsTransactions(account.getAllowsTransactions())
                .currency(account.getCurrency())
                .normalBalance(account.getNormalBalance().toString())
                .currentBalance(balance.getCurrentBalance())
                .availableBalance(balance.getAvailableBalance())
                .pendingBalance(balance.getPendingBalance())
                .reservedBalance(balance.getReservedBalance())
                .lastTransactionDate(recentEntries.isEmpty() ? null : recentEntries.get(0).getTransactionDate())
                .totalTransactions(statistics.getTotalTransactions())
                .monthlyDebitAmount(statistics.getMonthlyDebitAmount())
                .monthlyCreditAmount(statistics.getMonthlyCreditAmount())
                .yearToDateDebitAmount(statistics.getYearToDateDebitAmount())
                .yearToDateCreditAmount(statistics.getYearToDateCreditAmount())
                .createdAt(account.getCreatedAt())
                .lastUpdated(account.getLastUpdated())
                .build();
                
        } catch (AccountNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get account details for: {}", accountCode, e);
            throw new AccountingException("Failed to retrieve account details", e);
        }
    }

    /**
     * Gets account balance as of specific date
     */
    public AccountBalanceResponse getAccountBalance(String accountCode, LocalDate asOfDate) {
        try {
            Account account = accountRepository.findByAccountCode(accountCode)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountCode));
            
            BalanceCalculationResult balance;
            
            if (asOfDate != null) {
                balance = balanceCalculationService.calculateBalanceAsOf(
                    account.getAccountId(), asOfDate.atTime(23, 59, 59));
            } else {
                balance = balanceCalculationService.calculateBalance(account.getAccountId());
            }
            
            return AccountBalanceResponse.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .currentBalance(balance.getCurrentBalance())
                .availableBalance(balance.getAvailableBalance())
                .pendingBalance(balance.getPendingBalance())
                .reservedBalance(balance.getReservedBalance())
                .currency(account.getCurrency())
                .asOfDate(asOfDate != null ? asOfDate : LocalDate.now())
                .lastUpdated(balance.getLastUpdated())
                .build();
                
        } catch (AccountNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get account balance for: {}", accountCode, e);
            throw new AccountingException("Failed to retrieve account balance", e);
        }
    }

    /**
     * Gets account transactions with pagination
     */
    public Page<LedgerEntryResponse> getAccountTransactions(String accountCode, LocalDate startDate, 
                                                          LocalDate endDate, Pageable pageable) {
        try {
            Account account = accountRepository.findByAccountCode(accountCode)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountCode));
            
            LocalDateTime fromDate = startDate != null ? startDate.atStartOfDay() : 
                LocalDateTime.now().minusMonths(3).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime toDate = endDate != null ? endDate.atTime(23, 59, 59) : LocalDateTime.now();
            
            AccountLedgerResponse ledgerResponse = doubleEntryLedgerService.getAccountLedger(
                account.getAccountId(), fromDate, toDate, pageable.getPageNumber(), pageable.getPageSize());
            
            return new PageImpl<>(
                ledgerResponse.getEntries(),
                pageable,
                ledgerResponse.getTotalEntries()
            );
            
        } catch (AccountNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get account transactions for: {}", accountCode, e);
            throw new AccountingException("Failed to retrieve account transactions", e);
        }
    }

    /**
     * Generates trial balance report
     */
    @Cacheable(value = "trialBalance", key = "#asOfDate")
    public TrialBalanceResponse generateTrialBalance(LocalDate asOfDate) {
        try {
            log.info("Generating trial balance as of: {}", asOfDate);
            
            LocalDateTime asOfDateTime = asOfDate.atTime(23, 59, 59);
            TrialBalanceResponse trialBalance = doubleEntryLedgerService.generateTrialBalance(asOfDateTime);
            
            // Enhance with account details
            List<TrialBalanceEntry> enhancedEntries = trialBalance.getEntries().stream()
                .map(entry -> {
                    try {
                        Account account = accountRepository.findById(entry.getAccountId()).orElse(null);
                        if (account != null) {
                            entry.setAccountCode(account.getAccountCode());
                            entry.setAccountName(account.getAccountName());
                            entry.setCurrency(account.getCurrency());
                        }
                        return entry;
                    } catch (Exception e) {
                        log.warn("Failed to enhance trial balance entry for account: {}", entry.getAccountId());
                        return entry;
                    }
                })
                .collect(Collectors.toList());
            
            trialBalance.setEntries(enhancedEntries);
            return trialBalance;
            
        } catch (Exception e) {
            log.error("Failed to generate trial balance", e);
            throw new AccountingException("Failed to generate trial balance", e);
        }
    }

    /**
     * Generates comprehensive balance sheet
     */
    @Cacheable(value = "balanceSheet", key = "#asOfDate")
    public BalanceSheetResponse generateBalanceSheet(LocalDate asOfDate) {
        try {
            log.info("Generating balance sheet as of: {}", asOfDate);
            
            LocalDateTime asOfDateTime = asOfDate.atTime(23, 59, 59);
            
            // Get all accounts with balances
            Map<Account.AccountType, List<Account>> accountsByType = getAccountsByType();
            
            // Calculate section totals
            BalanceSheetSection assets = calculateAssetSection(accountsByType, asOfDateTime);
            BalanceSheetSection liabilities = calculateLiabilitySection(accountsByType, asOfDateTime);
            BalanceSheetSection equity = calculateEquitySection(accountsByType, asOfDateTime);
            
            // Verify balance sheet equation: Assets = Liabilities + Equity
            BigDecimal totalAssets = assets.getTotalAmount();
            BigDecimal totalLiabilitiesAndEquity = liabilities.getTotalAmount().add(equity.getTotalAmount());
            boolean balanced = totalAssets.compareTo(totalLiabilitiesAndEquity) == 0;
            
            return BalanceSheetResponse.builder()
                .asOfDate(asOfDate)
                .assets(assets)
                .liabilities(liabilities)
                .equity(equity)
                .totalAssets(totalAssets)
                .totalLiabilitiesAndEquity(totalLiabilitiesAndEquity)
                .balanced(balanced)
                .variance(totalAssets.subtract(totalLiabilitiesAndEquity))
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate balance sheet", e);
            throw new AccountingException("Failed to generate balance sheet", e);
        }
    }

    /**
     * Generates comprehensive income statement
     */
    @Cacheable(value = "incomeStatement", key = "#startDate + '_' + #endDate")
    public IncomeStatementResponse generateIncomeStatement(LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Generating income statement from {} to {}", startDate, endDate);
            
            LocalDateTime fromDateTime = startDate.atStartOfDay();
            LocalDateTime toDateTime = endDate.atTime(23, 59, 59);
            
            // Get revenue and expense accounts
            Map<Account.AccountType, List<Account>> accountsByType = getAccountsByType();
            
            // Calculate sections
            IncomeStatementSection revenue = calculateRevenueSection(accountsByType, fromDateTime, toDateTime);
            IncomeStatementSection expenses = calculateExpenseSection(accountsByType, fromDateTime, toDateTime);
            
            // Calculate totals
            BigDecimal totalRevenue = revenue.getTotalAmount();
            BigDecimal totalExpenses = expenses.getTotalAmount();
            BigDecimal netIncome = totalRevenue.subtract(totalExpenses);
            
            // Calculate margins
            BigDecimal grossMargin = calculateGrossMargin(accountsByType, fromDateTime, toDateTime);
            BigDecimal operatingMargin = calculateOperatingMargin(accountsByType, fromDateTime, toDateTime);
            BigDecimal netMargin = totalRevenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                netIncome.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            
            return IncomeStatementResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .revenue(revenue)
                .expenses(expenses)
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .grossIncome(grossMargin)
                .operatingIncome(operatingMargin)
                .netIncome(netIncome)
                .netMarginPercentage(netMargin)
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate income statement", e);
            throw new AccountingException("Failed to generate income statement", e);
        }
    }

    /**
     * Generates general ledger report
     */
    public GeneralLedgerReportResponse generateGeneralLedgerReport(LocalDate startDate, LocalDate endDate, 
                                                                 List<String> accountCodes) {
        try {
            log.info("Generating general ledger report from {} to {}", startDate, endDate);
            
            LocalDateTime fromDateTime = startDate.atStartOfDay();
            LocalDateTime toDateTime = endDate.atTime(23, 59, 59);
            
            List<Account> accounts;
            if (accountCodes != null && !accountCodes.isEmpty()) {
                accounts = accountCodes.stream()
                    .map(code -> accountRepository.findByAccountCode(code).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            } else {
                accounts = accountRepository.findByIsActiveTrueAndAllowsTransactionsTrue();
            }
            
            List<GeneralLedgerAccountReport> accountReports = accounts.stream()
                .map(account -> generateAccountReport(account, fromDateTime, toDateTime))
                .collect(Collectors.toList());
            
            return GeneralLedgerReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .accountReports(accountReports)
                .totalAccounts(accountReports.size())
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate general ledger report", e);
            throw new AccountingException("Failed to generate general ledger report", e);
        }
    }

    /**
     * Performs period-end closing procedures
     */
    @Transactional
    public PeriodCloseResponse closeAccountingPeriod(ClosePeriodRequest request) {
        try {
            log.info("Closing accounting period ending: {}", request.getPeriodEndDate());
            
            LocalDateTime periodEnd = request.getPeriodEndDate().atTime(23, 59, 59);
            
            // Validate period can be closed
            validatePeriodClose(request);
            
            // Generate trial balance to ensure books are balanced
            TrialBalanceResponse trialBalance = self.generateTrialBalance(request.getPeriodEndDate());
            if (!trialBalance.isBalanced()) {
                throw new AccountingException("Cannot close period - trial balance is not balanced");
            }
            
            // Close revenue and expense accounts to retained earnings
            PeriodCloseResult closeResult = performPeriodClose(request, periodEnd);
            
            // Generate final reports
            BalanceSheetResponse finalBalanceSheet = self.generateBalanceSheet(request.getPeriodEndDate());
            IncomeStatementResponse finalIncomeStatement = self.generateIncomeStatement(
                request.getPeriodStartDate(), request.getPeriodEndDate());
            
            return PeriodCloseResponse.builder()
                .periodEndDate(request.getPeriodEndDate())
                .success(true)
                .trialBalance(trialBalance)
                .balanceSheet(finalBalanceSheet)
                .incomeStatement(finalIncomeStatement)
                .closingEntries(closeResult.getClosingEntries())
                .retainedEarningsAdjustment(closeResult.getRetainedEarningsAdjustment())
                .closedAt(LocalDateTime.now())
                .closedBy(request.getClosedBy())
                .build();
                
        } catch (AccountingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to close accounting period", e);
            throw new AccountingException("Failed to close accounting period", e);
        }
    }

    /**
     * Gets current period status
     */
    public PeriodStatusResponse getPeriodEndStatus() {
        try {
            // Determine current period
            LocalDate currentDate = LocalDate.now();
            LocalDate periodStart = currentDate.withDayOfMonth(1);
            LocalDate periodEnd = currentDate.withDayOfMonth(currentDate.lengthOfMonth());
            
            // Check if current period is closed
            boolean periodClosed = isPeriodClosed(periodEnd);
            
            // Get trial balance status
            TrialBalanceResponse trialBalance = self.generateTrialBalance(currentDate);
            
            // Identify any issues preventing period close
            List<String> blockingIssues = identifyBlockingIssues(currentDate);
            
            return PeriodStatusResponse.builder()
                .currentPeriodStart(periodStart)
                .currentPeriodEnd(periodEnd)
                .periodClosed(periodClosed)
                .trialBalanced(trialBalance.isBalanced())
                .canClosePeriod(blockingIssues.isEmpty())
                .blockingIssues(blockingIssues)
                .lastCloseDate(getLastCloseDate())
                .checkedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get period end status", e);
            throw new AccountingException("Failed to get period end status", e);
        }
    }

    // Private helper methods

    private LedgerAccountResponse mapToLedgerAccountResponse(Account account) {
        try {
            BalanceCalculationResult balance = balanceCalculationService.calculateBalance(account.getAccountId());
            
            return LedgerAccountResponse.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType().toString())
                .parentAccountId(account.getParentAccountId())
                .currentBalance(balance.getCurrentBalance())
                .currency(account.getCurrency())
                .isActive(account.getIsActive())
                .allowsTransactions(account.getAllowsTransactions())
                .normalBalance(account.getNormalBalance().toString())
                .lastUpdated(account.getLastUpdated())
                .build();
        } catch (Exception e) {
            log.warn("Failed to get balance for account {}, returning zero balance", account.getAccountCode());
            return LedgerAccountResponse.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType().toString())
                .parentAccountId(account.getParentAccountId())
                .currentBalance(BigDecimal.ZERO)
                .currency(account.getCurrency())
                .isActive(account.getIsActive())
                .allowsTransactions(account.getAllowsTransactions())
                .normalBalance(account.getNormalBalance().toString())
                .lastUpdated(account.getLastUpdated())
                .build();
        }
    }

    private AccountStatistics calculateAccountStatistics(UUID accountId) {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime yearStart = LocalDateTime.now().withDayOfYear(1).withHour(0).withMinute(0).withSecond(0);
        
        List<LedgerEntry> monthlyEntries = ledgerEntryRepository.findByAccountIdAndTransactionDateGreaterThanEqual(
            accountId, monthStart);
        List<LedgerEntry> yearlyEntries = ledgerEntryRepository.findByAccountIdAndTransactionDateGreaterThanEqual(
            accountId, yearStart);
        
        return AccountStatistics.builder()
            .totalTransactions(ledgerEntryRepository.countByAccountId(accountId))
            .monthlyDebitAmount(calculateDebitAmount(monthlyEntries))
            .monthlyCreditAmount(calculateCreditAmount(monthlyEntries))
            .yearToDateDebitAmount(calculateDebitAmount(yearlyEntries))
            .yearToDateCreditAmount(calculateCreditAmount(yearlyEntries))
            .build();
    }

    private BigDecimal calculateDebitAmount(List<LedgerEntry> entries) {
        return entries.stream()
            .filter(entry -> entry.getEntryType() == LedgerEntry.EntryType.DEBIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateCreditAmount(List<LedgerEntry> entries) {
        return entries.stream()
            .filter(entry -> entry.getEntryType() == LedgerEntry.EntryType.CREDIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Account.AccountType, List<Account>> getAccountsByType() {
        List<Account> allAccounts = accountRepository.findByIsActiveTrueAndAllowsTransactionsTrue();
        return allAccounts.stream().collect(Collectors.groupingBy(Account::getAccountType));
    }

    private BalanceSheetSection calculateAssetSection(Map<Account.AccountType, List<Account>> accountsByType, 
                                                    LocalDateTime asOfDate) {
        List<BalanceSheetLineItem> assetItems = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO;
        
        // Current Assets
        List<BalanceSheetLineItem> currentAssets = new ArrayList<>();
        
        // Cash and Cash Equivalents
        BigDecimal cashBalance = calculateAccountTypeBalance(accountsByType, Account.AccountType.CASH, asOfDate);
        if (cashBalance.compareTo(BigDecimal.ZERO) != 0) {
            currentAssets.add(BalanceSheetLineItem.builder()
                .itemName("Cash and Cash Equivalents")
                .amount(cashBalance)
                .accountType("CASH")
                .build());
            totalAssets = totalAssets.add(cashBalance);
        }
        
        // Accounts Receivable
        BigDecimal receivables = calculateAccountTypeBalance(accountsByType, Account.AccountType.ACCOUNTS_RECEIVABLE, asOfDate);
        if (receivables.compareTo(BigDecimal.ZERO) != 0) {
            currentAssets.add(BalanceSheetLineItem.builder()
                .itemName("Accounts Receivable")
                .amount(receivables)
                .accountType("ACCOUNTS_RECEIVABLE")
                .build());
            totalAssets = totalAssets.add(receivables);
        }
        
        // Inventory
        BigDecimal inventory = calculateAccountTypeBalance(accountsByType, Account.AccountType.INVENTORY, asOfDate);
        if (inventory.compareTo(BigDecimal.ZERO) != 0) {
            currentAssets.add(BalanceSheetLineItem.builder()
                .itemName("Inventory")
                .amount(inventory)
                .accountType("INVENTORY")
                .build());
            totalAssets = totalAssets.add(inventory);
        }
        
        // Prepaid Expenses
        BigDecimal prepaid = calculateAccountTypeBalance(accountsByType, Account.AccountType.PREPAID_EXPENSE, asOfDate);
        if (prepaid.compareTo(BigDecimal.ZERO) != 0) {
            currentAssets.add(BalanceSheetLineItem.builder()
                .itemName("Prepaid Expenses")
                .amount(prepaid)
                .accountType("PREPAID_EXPENSE")
                .build());
            totalAssets = totalAssets.add(prepaid);
        }
        
        // Add current assets subsection
        if (!currentAssets.isEmpty()) {
            BigDecimal currentAssetsTotal = currentAssets.stream()
                .map(BalanceSheetLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            assetItems.add(BalanceSheetLineItem.builder()
                .itemName("Current Assets")
                .amount(currentAssetsTotal)
                .isSubtotal(true)
                .subItems(currentAssets)
                .build());
        }
        
        // Fixed Assets
        List<BalanceSheetLineItem> fixedAssets = new ArrayList<>();
        
        // Property, Plant & Equipment
        BigDecimal ppe = calculateAccountTypeBalance(accountsByType, Account.AccountType.FIXED_ASSET, asOfDate);
        if (ppe.compareTo(BigDecimal.ZERO) != 0) {
            fixedAssets.add(BalanceSheetLineItem.builder()
                .itemName("Property, Plant & Equipment")
                .amount(ppe)
                .accountType("FIXED_ASSET")
                .build());
            totalAssets = totalAssets.add(ppe);
        }
        
        // Less: Accumulated Depreciation
        BigDecimal depreciation = calculateAccountTypeBalance(accountsByType, Account.AccountType.ACCUMULATED_DEPRECIATION, asOfDate);
        if (depreciation.compareTo(BigDecimal.ZERO) != 0) {
            fixedAssets.add(BalanceSheetLineItem.builder()
                .itemName("Less: Accumulated Depreciation")
                .amount(depreciation.negate())
                .accountType("ACCUMULATED_DEPRECIATION")
                .build());
            totalAssets = totalAssets.subtract(depreciation);
        }
        
        // Add fixed assets subsection
        if (!fixedAssets.isEmpty()) {
            BigDecimal fixedAssetsTotal = fixedAssets.stream()
                .map(BalanceSheetLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            assetItems.add(BalanceSheetLineItem.builder()
                .itemName("Fixed Assets")
                .amount(fixedAssetsTotal)
                .isSubtotal(true)
                .subItems(fixedAssets)
                .build());
        }
        
        // Other Assets
        BigDecimal otherAssets = calculateAccountTypeBalance(accountsByType, Account.AccountType.OTHER_ASSET, asOfDate);
        if (otherAssets.compareTo(BigDecimal.ZERO) != 0) {
            assetItems.add(BalanceSheetLineItem.builder()
                .itemName("Other Assets")
                .amount(otherAssets)
                .accountType("OTHER_ASSET")
                .build());
            totalAssets = totalAssets.add(otherAssets);
        }
        
        return BalanceSheetSection.builder()
            .sectionName("Assets")
            .accounts(assetItems)
            .totalAmount(totalAssets)
            .build();
    }

    private BalanceSheetSection calculateLiabilitySection(Map<Account.AccountType, List<Account>> accountsByType, 
                                                        LocalDateTime asOfDate) {
        List<BalanceSheetLineItem> liabilityItems = new ArrayList<>();
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        
        // Current Liabilities
        List<BalanceSheetLineItem> currentLiabilities = new ArrayList<>();
        
        // Accounts Payable
        BigDecimal payables = calculateAccountTypeBalance(accountsByType, Account.AccountType.ACCOUNTS_PAYABLE, asOfDate);
        if (payables.compareTo(BigDecimal.ZERO) != 0) {
            currentLiabilities.add(BalanceSheetLineItem.builder()
                .itemName("Accounts Payable")
                .amount(payables)
                .accountType("ACCOUNTS_PAYABLE")
                .build());
            totalLiabilities = totalLiabilities.add(payables);
        }
        
        // Accrued Expenses
        BigDecimal accrued = calculateAccountTypeBalance(accountsByType, Account.AccountType.ACCRUED_EXPENSE, asOfDate);
        if (accrued.compareTo(BigDecimal.ZERO) != 0) {
            currentLiabilities.add(BalanceSheetLineItem.builder()
                .itemName("Accrued Expenses")
                .amount(accrued)
                .accountType("ACCRUED_EXPENSE")
                .build());
            totalLiabilities = totalLiabilities.add(accrued);
        }
        
        // Short-term Debt
        BigDecimal shortTermDebt = calculateAccountTypeBalance(accountsByType, Account.AccountType.SHORT_TERM_DEBT, asOfDate);
        if (shortTermDebt.compareTo(BigDecimal.ZERO) != 0) {
            currentLiabilities.add(BalanceSheetLineItem.builder()
                .itemName("Short-term Debt")
                .amount(shortTermDebt)
                .accountType("SHORT_TERM_DEBT")
                .build());
            totalLiabilities = totalLiabilities.add(shortTermDebt);
        }
        
        // Unearned Revenue
        BigDecimal unearned = calculateAccountTypeBalance(accountsByType, Account.AccountType.UNEARNED_REVENUE, asOfDate);
        if (unearned.compareTo(BigDecimal.ZERO) != 0) {
            currentLiabilities.add(BalanceSheetLineItem.builder()
                .itemName("Unearned Revenue")
                .amount(unearned)
                .accountType("UNEARNED_REVENUE")
                .build());
            totalLiabilities = totalLiabilities.add(unearned);
        }
        
        // Tax Payable
        BigDecimal taxPayable = calculateAccountTypeBalance(accountsByType, Account.AccountType.TAX_PAYABLE, asOfDate);
        if (taxPayable.compareTo(BigDecimal.ZERO) != 0) {
            currentLiabilities.add(BalanceSheetLineItem.builder()
                .itemName("Tax Payable")
                .amount(taxPayable)
                .accountType("TAX_PAYABLE")
                .build());
            totalLiabilities = totalLiabilities.add(taxPayable);
        }
        
        // Add current liabilities subsection
        if (!currentLiabilities.isEmpty()) {
            BigDecimal currentLiabilitiesTotal = currentLiabilities.stream()
                .map(BalanceSheetLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            liabilityItems.add(BalanceSheetLineItem.builder()
                .itemName("Current Liabilities")
                .amount(currentLiabilitiesTotal)
                .isSubtotal(true)
                .subItems(currentLiabilities)
                .build());
        }
        
        // Long-term Liabilities
        List<BalanceSheetLineItem> longTermLiabilities = new ArrayList<>();
        
        // Long-term Debt
        BigDecimal longTermDebt = calculateAccountTypeBalance(accountsByType, Account.AccountType.LONG_TERM_DEBT, asOfDate);
        if (longTermDebt.compareTo(BigDecimal.ZERO) != 0) {
            longTermLiabilities.add(BalanceSheetLineItem.builder()
                .itemName("Long-term Debt")
                .amount(longTermDebt)
                .accountType("LONG_TERM_DEBT")
                .build());
            totalLiabilities = totalLiabilities.add(longTermDebt);
        }
        
        // Deferred Tax Liability
        BigDecimal deferredTax = calculateAccountTypeBalance(accountsByType, Account.AccountType.DEFERRED_TAX_LIABILITY, asOfDate);
        if (deferredTax.compareTo(BigDecimal.ZERO) != 0) {
            longTermLiabilities.add(BalanceSheetLineItem.builder()
                .itemName("Deferred Tax Liability")
                .amount(deferredTax)
                .accountType("DEFERRED_TAX_LIABILITY")
                .build());
            totalLiabilities = totalLiabilities.add(deferredTax);
        }
        
        // Add long-term liabilities subsection
        if (!longTermLiabilities.isEmpty()) {
            BigDecimal longTermLiabilitiesTotal = longTermLiabilities.stream()
                .map(BalanceSheetLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            liabilityItems.add(BalanceSheetLineItem.builder()
                .itemName("Long-term Liabilities")
                .amount(longTermLiabilitiesTotal)
                .isSubtotal(true)
                .subItems(longTermLiabilities)
                .build());
        }
        
        // Other Liabilities
        BigDecimal otherLiabilities = calculateAccountTypeBalance(accountsByType, Account.AccountType.OTHER_LIABILITY, asOfDate);
        if (otherLiabilities.compareTo(BigDecimal.ZERO) != 0) {
            liabilityItems.add(BalanceSheetLineItem.builder()
                .itemName("Other Liabilities")
                .amount(otherLiabilities)
                .accountType("OTHER_LIABILITY")
                .build());
            totalLiabilities = totalLiabilities.add(otherLiabilities);
        }
        
        return BalanceSheetSection.builder()
            .sectionName("Liabilities")
            .accounts(liabilityItems)
            .totalAmount(totalLiabilities)
            .build();
    }

    private BalanceSheetSection calculateEquitySection(Map<Account.AccountType, List<Account>> accountsByType, 
                                                     LocalDateTime asOfDate) {
        List<BalanceSheetLineItem> equityItems = new ArrayList<>();
        BigDecimal totalEquity = BigDecimal.ZERO;
        
        // Common Stock
        BigDecimal commonStock = calculateAccountTypeBalance(accountsByType, Account.AccountType.COMMON_STOCK, asOfDate);
        if (commonStock.compareTo(BigDecimal.ZERO) != 0) {
            equityItems.add(BalanceSheetLineItem.builder()
                .itemName("Common Stock")
                .amount(commonStock)
                .accountType("COMMON_STOCK")
                .build());
            totalEquity = totalEquity.add(commonStock);
        }
        
        // Preferred Stock
        BigDecimal preferredStock = calculateAccountTypeBalance(accountsByType, Account.AccountType.PREFERRED_STOCK, asOfDate);
        if (preferredStock.compareTo(BigDecimal.ZERO) != 0) {
            equityItems.add(BalanceSheetLineItem.builder()
                .itemName("Preferred Stock")
                .amount(preferredStock)
                .accountType("PREFERRED_STOCK")
                .build());
            totalEquity = totalEquity.add(preferredStock);
        }
        
        // Additional Paid-in Capital
        BigDecimal additionalCapital = calculateAccountTypeBalance(accountsByType, Account.AccountType.ADDITIONAL_PAID_IN_CAPITAL, asOfDate);
        if (additionalCapital.compareTo(BigDecimal.ZERO) != 0) {
            equityItems.add(BalanceSheetLineItem.builder()
                .itemName("Additional Paid-in Capital")
                .amount(additionalCapital)
                .accountType("ADDITIONAL_PAID_IN_CAPITAL")
                .build());
            totalEquity = totalEquity.add(additionalCapital);
        }
        
        // Retained Earnings
        BigDecimal retainedEarnings = calculateRetainedEarnings(accountsByType, asOfDate);
        if (retainedEarnings.compareTo(BigDecimal.ZERO) != 0) {
            equityItems.add(BalanceSheetLineItem.builder()
                .itemName("Retained Earnings")
                .amount(retainedEarnings)
                .accountType("RETAINED_EARNINGS")
                .build());
            totalEquity = totalEquity.add(retainedEarnings);
        }
        
        // Treasury Stock (contra-equity account, shown as negative)
        BigDecimal treasuryStock = calculateAccountTypeBalance(accountsByType, Account.AccountType.TREASURY_STOCK, asOfDate);
        if (treasuryStock.compareTo(BigDecimal.ZERO) != 0) {
            equityItems.add(BalanceSheetLineItem.builder()
                .itemName("Treasury Stock")
                .amount(treasuryStock.negate())
                .accountType("TREASURY_STOCK")
                .build());
            totalEquity = totalEquity.subtract(treasuryStock);
        }
        
        // Other Comprehensive Income
        BigDecimal oci = calculateAccountTypeBalance(accountsByType, Account.AccountType.OTHER_COMPREHENSIVE_INCOME, asOfDate);
        if (oci.compareTo(BigDecimal.ZERO) != 0) {
            equityItems.add(BalanceSheetLineItem.builder()
                .itemName("Other Comprehensive Income")
                .amount(oci)
                .accountType("OTHER_COMPREHENSIVE_INCOME")
                .build());
            totalEquity = totalEquity.add(oci);
        }
        
        return BalanceSheetSection.builder()
            .sectionName("Shareholders' Equity")
            .accounts(equityItems)
            .totalAmount(totalEquity)
            .build();
    }

    private IncomeStatementSection calculateRevenueSection(Map<Account.AccountType, List<Account>> accountsByType,
                                                         LocalDateTime fromDate, LocalDateTime toDate) {
        List<IncomeStatementLineItem> revenueItems = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        
        // Sales Revenue
        BigDecimal salesRevenue = calculatePeriodActivity(accountsByType, Account.AccountType.SALES_REVENUE, fromDate, toDate);
        if (salesRevenue.compareTo(BigDecimal.ZERO) != 0) {
            revenueItems.add(IncomeStatementLineItem.builder()
                .itemName("Sales Revenue")
                .amount(salesRevenue)
                .accountType("SALES_REVENUE")
                .build());
            totalRevenue = totalRevenue.add(salesRevenue);
        }
        
        // Service Revenue
        BigDecimal serviceRevenue = calculatePeriodActivity(accountsByType, Account.AccountType.SERVICE_REVENUE, fromDate, toDate);
        if (serviceRevenue.compareTo(BigDecimal.ZERO) != 0) {
            revenueItems.add(IncomeStatementLineItem.builder()
                .itemName("Service Revenue")
                .amount(serviceRevenue)
                .accountType("SERVICE_REVENUE")
                .build());
            totalRevenue = totalRevenue.add(serviceRevenue);
        }
        
        // Interest Income
        BigDecimal interestIncome = calculatePeriodActivity(accountsByType, Account.AccountType.INTEREST_INCOME, fromDate, toDate);
        if (interestIncome.compareTo(BigDecimal.ZERO) != 0) {
            revenueItems.add(IncomeStatementLineItem.builder()
                .itemName("Interest Income")
                .amount(interestIncome)
                .accountType("INTEREST_INCOME")
                .build());
            totalRevenue = totalRevenue.add(interestIncome);
        }
        
        // Commission Income
        BigDecimal commissionIncome = calculatePeriodActivity(accountsByType, Account.AccountType.COMMISSION_INCOME, fromDate, toDate);
        if (commissionIncome.compareTo(BigDecimal.ZERO) != 0) {
            revenueItems.add(IncomeStatementLineItem.builder()
                .itemName("Commission Income")
                .amount(commissionIncome)
                .accountType("COMMISSION_INCOME")
                .build());
            totalRevenue = totalRevenue.add(commissionIncome);
        }
        
        // Other Revenue
        BigDecimal otherRevenue = calculatePeriodActivity(accountsByType, Account.AccountType.OTHER_REVENUE, fromDate, toDate);
        if (otherRevenue.compareTo(BigDecimal.ZERO) != 0) {
            revenueItems.add(IncomeStatementLineItem.builder()
                .itemName("Other Revenue")
                .amount(otherRevenue)
                .accountType("OTHER_REVENUE")
                .build());
            totalRevenue = totalRevenue.add(otherRevenue);
        }
        
        // Less: Sales Returns and Allowances
        BigDecimal salesReturns = calculatePeriodActivity(accountsByType, Account.AccountType.SALES_RETURNS, fromDate, toDate);
        if (salesReturns.compareTo(BigDecimal.ZERO) != 0) {
            revenueItems.add(IncomeStatementLineItem.builder()
                .itemName("Less: Sales Returns & Allowances")
                .amount(salesReturns.negate())
                .accountType("SALES_RETURNS")
                .build());
            totalRevenue = totalRevenue.subtract(salesReturns);
        }
        
        // Less: Sales Discounts
        BigDecimal salesDiscounts = calculatePeriodActivity(accountsByType, Account.AccountType.SALES_DISCOUNTS, fromDate, toDate);
        if (salesDiscounts.compareTo(BigDecimal.ZERO) != 0) {
            revenueItems.add(IncomeStatementLineItem.builder()
                .itemName("Less: Sales Discounts")
                .amount(salesDiscounts.negate())
                .accountType("SALES_DISCOUNTS")
                .build());
            totalRevenue = totalRevenue.subtract(salesDiscounts);
        }
        
        return IncomeStatementSection.builder()
            .sectionName("Revenue")
            .accounts(revenueItems)
            .totalAmount(totalRevenue)
            .build();
    }

    private IncomeStatementSection calculateExpenseSection(Map<Account.AccountType, List<Account>> accountsByType,
                                                         LocalDateTime fromDate, LocalDateTime toDate) {
        List<IncomeStatementLineItem> expenseItems = new ArrayList<>();
        BigDecimal totalExpenses = BigDecimal.ZERO;
        
        // Cost of Goods Sold
        BigDecimal cogs = calculateCostOfGoodsSold(accountsByType, fromDate, toDate);
        if (cogs.compareTo(BigDecimal.ZERO) != 0) {
            expenseItems.add(IncomeStatementLineItem.builder()
                .itemName("Cost of Goods Sold")
                .amount(cogs)
                .accountType("COST_OF_GOODS_SOLD")
                .isSubtotal(true)
                .build());
            totalExpenses = totalExpenses.add(cogs);
        }
        
        // Operating Expenses Subsection
        List<IncomeStatementLineItem> operatingExpenses = new ArrayList<>();
        
        // Salaries and Wages
        BigDecimal salaries = calculatePeriodActivity(accountsByType, Account.AccountType.SALARIES_EXPENSE, fromDate, toDate);
        if (salaries.compareTo(BigDecimal.ZERO) != 0) {
            operatingExpenses.add(IncomeStatementLineItem.builder()
                .itemName("Salaries and Wages")
                .amount(salaries)
                .accountType("SALARIES_EXPENSE")
                .build());
            totalExpenses = totalExpenses.add(salaries);
        }
        
        // Rent Expense
        BigDecimal rent = calculatePeriodActivity(accountsByType, Account.AccountType.RENT_EXPENSE, fromDate, toDate);
        if (rent.compareTo(BigDecimal.ZERO) != 0) {
            operatingExpenses.add(IncomeStatementLineItem.builder()
                .itemName("Rent Expense")
                .amount(rent)
                .accountType("RENT_EXPENSE")
                .build());
            totalExpenses = totalExpenses.add(rent);
        }
        
        // Utilities Expense
        BigDecimal utilities = calculatePeriodActivity(accountsByType, Account.AccountType.UTILITIES_EXPENSE, fromDate, toDate);
        if (utilities.compareTo(BigDecimal.ZERO) != 0) {
            operatingExpenses.add(IncomeStatementLineItem.builder()
                .itemName("Utilities Expense")
                .amount(utilities)
                .accountType("UTILITIES_EXPENSE")
                .build());
            totalExpenses = totalExpenses.add(utilities);
        }
        
        // Marketing & Advertising
        BigDecimal marketing = calculatePeriodActivity(accountsByType, Account.AccountType.MARKETING_EXPENSE, fromDate, toDate);
        if (marketing.compareTo(BigDecimal.ZERO) != 0) {
            operatingExpenses.add(IncomeStatementLineItem.builder()
                .itemName("Marketing & Advertising")
                .amount(marketing)
                .accountType("MARKETING_EXPENSE")
                .build());
            totalExpenses = totalExpenses.add(marketing);
        }
        
        // Depreciation Expense
        BigDecimal depreciation = calculatePeriodActivity(accountsByType, Account.AccountType.DEPRECIATION_EXPENSE, fromDate, toDate);
        if (depreciation.compareTo(BigDecimal.ZERO) != 0) {
            operatingExpenses.add(IncomeStatementLineItem.builder()
                .itemName("Depreciation Expense")
                .amount(depreciation)
                .accountType("DEPRECIATION_EXPENSE")
                .build());
            totalExpenses = totalExpenses.add(depreciation);
        }
        
        // Amortization Expense
        BigDecimal amortization = calculatePeriodActivity(accountsByType, Account.AccountType.AMORTIZATION_EXPENSE, fromDate, toDate);
        if (amortization.compareTo(BigDecimal.ZERO) != 0) {
            operatingExpenses.add(IncomeStatementLineItem.builder()
                .itemName("Amortization Expense")
                .amount(amortization)
                .accountType("AMORTIZATION_EXPENSE")
                .build());
            totalExpenses = totalExpenses.add(amortization);
        }
        
        // Insurance Expense
        BigDecimal insurance = calculatePeriodActivity(accountsByType, Account.AccountType.INSURANCE_EXPENSE, fromDate, toDate);
        if (insurance.compareTo(BigDecimal.ZERO) != 0) {
            operatingExpenses.add(IncomeStatementLineItem.builder()
                .itemName("Insurance Expense")
                .amount(insurance)
                .accountType("INSURANCE_EXPENSE")
                .build());
            totalExpenses = totalExpenses.add(insurance);
        }
        
        // Add operating expenses subsection if not empty
        if (!operatingExpenses.isEmpty()) {
            BigDecimal operatingExpensesTotal = operatingExpenses.stream()
                .map(IncomeStatementLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
            expenseItems.add(IncomeStatementLineItem.builder()
                .itemName("Operating Expenses")
                .amount(operatingExpensesTotal)
                .isSubtotal(true)
                .subItems(operatingExpenses)
                .build());
        }
        
        // Interest Expense
        BigDecimal interestExpense = calculatePeriodActivity(accountsByType, Account.AccountType.INTEREST_EXPENSE, fromDate, toDate);
        if (interestExpense.compareTo(BigDecimal.ZERO) != 0) {
            expenseItems.add(IncomeStatementLineItem.builder()
                .itemName("Interest Expense")
                .amount(interestExpense)
                .accountType("INTEREST_EXPENSE")
                .build());
            totalExpenses = totalExpenses.add(interestExpense);
        }
        
        // Tax Expense
        BigDecimal taxExpense = calculatePeriodActivity(accountsByType, Account.AccountType.TAX_EXPENSE, fromDate, toDate);
        if (taxExpense.compareTo(BigDecimal.ZERO) != 0) {
            expenseItems.add(IncomeStatementLineItem.builder()
                .itemName("Income Tax Expense")
                .amount(taxExpense)
                .accountType("TAX_EXPENSE")
                .build());
            totalExpenses = totalExpenses.add(taxExpense);
        }
        
        return IncomeStatementSection.builder()
            .sectionName("Expenses")
            .accounts(expenseItems)
            .totalAmount(totalExpenses)
            .build();
    }

    private BigDecimal calculateGrossMargin(Map<Account.AccountType, List<Account>> accountsByType,
                                          LocalDateTime fromDate, LocalDateTime toDate) {
        // Calculate Gross Margin = (Revenue - Cost of Goods Sold) / Revenue * 100
        BigDecimal revenue = calculateTotalForAccountType(accountsByType, Account.AccountType.REVENUE, fromDate, toDate);
        BigDecimal cogs = calculateCostOfGoodsSold(accountsByType, fromDate, toDate);
        
        if (revenue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal grossProfit = revenue.subtract(cogs);
        return grossProfit.divide(revenue, 4, RoundingMode.HALF_UP)
                         .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateOperatingMargin(Map<Account.AccountType, List<Account>> accountsByType,
                                              LocalDateTime fromDate, LocalDateTime toDate) {
        // Calculate Operating Margin = (Operating Income / Revenue) * 100
        BigDecimal revenue = calculateTotalForAccountType(accountsByType, Account.AccountType.REVENUE, fromDate, toDate);
        
        if (revenue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // Calculate operating income
        BigDecimal grossProfit = revenue.subtract(calculateCostOfGoodsSold(accountsByType, fromDate, toDate));
        BigDecimal operatingExpenses = calculateOperatingExpenses(accountsByType, fromDate, toDate);
        BigDecimal operatingIncome = grossProfit.subtract(operatingExpenses);
        
        return operatingIncome.divide(revenue, 4, RoundingMode.HALF_UP)
                             .multiply(BigDecimal.valueOf(100));
    }

    private GeneralLedgerAccountReport generateAccountReport(Account account, LocalDateTime fromDate, LocalDateTime toDate) {
        // Get opening balance (balance before fromDate)
        BalanceCalculationResult openingBalanceResult = balanceCalculationService.calculateBalanceAsOf(
            account.getAccountId(), fromDate.minusSeconds(1));
        BigDecimal openingBalance = openingBalanceResult.getCurrentBalance();
        
        // Get all transactions for the period
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndTransactionDateBetween(
            account.getAccountId(), fromDate, toDate);
        
        // Convert to response DTOs
        List<LedgerEntryResponse> entryResponses = entries.stream()
            .map(entry -> LedgerEntryResponse.builder()
                .entryId(entry.getEntryId())
                .transactionId(entry.getTransactionId())
                .accountId(entry.getAccountId())
                .entryType(entry.getEntryType().toString())
                .amount(entry.getAmount())
                .description(entry.getDescription())
                .reference(entry.getReference())
                .transactionDate(entry.getTransactionDate())
                .createdAt(entry.getCreatedAt())
                .build())
            .collect(Collectors.toList());
        
        // Calculate running balance
        BigDecimal runningBalance = openingBalance;
        for (LedgerEntryResponse entry : entryResponses) {
            if ("DEBIT".equals(entry.getEntryType())) {
                if (account.getNormalBalance() == Account.NormalBalance.DEBIT) {
                    runningBalance = runningBalance.add(entry.getAmount());
                } else {
                    runningBalance = runningBalance.subtract(entry.getAmount());
                }
            } else {
                if (account.getNormalBalance() == Account.NormalBalance.CREDIT) {
                    runningBalance = runningBalance.add(entry.getAmount());
                } else {
                    runningBalance = runningBalance.subtract(entry.getAmount());
                }
            }
            entry.setRunningBalance(runningBalance);
        }
        
        // Get closing balance
        BalanceCalculationResult closingBalanceResult = balanceCalculationService.calculateBalanceAsOf(
            account.getAccountId(), toDate);
        BigDecimal closingBalance = closingBalanceResult.getCurrentBalance();
        
        // Calculate totals
        BigDecimal totalDebits = entries.stream()
            .filter(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal totalCredits = entries.stream()
            .filter(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return GeneralLedgerAccountReport.builder()
            .accountCode(account.getAccountCode())
            .accountName(account.getAccountName())
            .accountType(account.getAccountType().toString())
            .normalBalance(account.getNormalBalance().toString())
            .entries(entryResponses)
            .openingBalance(openingBalance)
            .closingBalance(closingBalance)
            .totalDebits(totalDebits)
            .totalCredits(totalCredits)
            .netChange(closingBalance.subtract(openingBalance))
            .entryCount(entries.size())
            .build();
    }

    private void validatePeriodClose(ClosePeriodRequest request) {
        // Implementation for period close validation
    }

    private PeriodCloseResult performPeriodClose(ClosePeriodRequest request, LocalDateTime periodEnd) {
        // Implementation for period close procedures
        return PeriodCloseResult.builder()
            .closingEntries(new ArrayList<>())
            .retainedEarningsAdjustment(BigDecimal.ZERO)
            .build();
    }

    private boolean isPeriodClosed(LocalDate periodEnd) {
        // Implementation to check if period is closed
        return false;
    }

    private List<String> identifyBlockingIssues(LocalDate currentDate) {
        // Implementation to identify issues preventing period close
        return new ArrayList<>();
    }

    // Helper methods for financial calculations
    private BigDecimal calculateTotalForAccountType(Map<Account.AccountType, List<Account>> accountsByType,
                                                   Account.AccountType type,
                                                   LocalDateTime fromDate,
                                                   LocalDateTime toDate) {
        List<Account> accounts = accountsByType.getOrDefault(type, new ArrayList<>());
        BigDecimal total = BigDecimal.ZERO;
        
        for (Account account : accounts) {
            BigDecimal balance = getAccountBalance(account.getAccountId(), fromDate, toDate);
            total = total.add(balance);
        }
        
        return total;
    }

    private BigDecimal calculateCostOfGoodsSold(Map<Account.AccountType, List<Account>> accountsByType,
                                               LocalDateTime fromDate,
                                               LocalDateTime toDate) {
        // COGS = Beginning Inventory + Purchases - Ending Inventory
        BigDecimal beginningInventory = calculateTotalForAccountType(accountsByType, 
            Account.AccountType.INVENTORY, fromDate, fromDate);
        BigDecimal purchases = calculateTotalForAccountType(accountsByType, 
            Account.AccountType.COST_OF_GOODS_SOLD, fromDate, toDate);
        BigDecimal endingInventory = calculateTotalForAccountType(accountsByType, 
            Account.AccountType.INVENTORY, toDate, toDate);
        
        return beginningInventory.add(purchases).subtract(endingInventory);
    }

    private BigDecimal calculateOperatingExpenses(Map<Account.AccountType, List<Account>> accountsByType,
                                                 LocalDateTime fromDate,
                                                 LocalDateTime toDate) {
        // Sum all operating expense accounts
        BigDecimal totalExpenses = BigDecimal.ZERO;
        
        // Add various expense types
        totalExpenses = totalExpenses.add(calculateTotalForAccountType(accountsByType, 
            Account.AccountType.EXPENSE, fromDate, toDate));
        totalExpenses = totalExpenses.add(calculateTotalForAccountType(accountsByType, 
            Account.AccountType.ADMINISTRATIVE_EXPENSE, fromDate, toDate));
        totalExpenses = totalExpenses.add(calculateTotalForAccountType(accountsByType, 
            Account.AccountType.MARKETING_EXPENSE, fromDate, toDate));
        totalExpenses = totalExpenses.add(calculateTotalForAccountType(accountsByType, 
            Account.AccountType.OPERATIONAL_EXPENSE, fromDate, toDate));
        
        return totalExpenses;
    }

    private BigDecimal getAccountBalance(UUID accountId, LocalDateTime fromDate, LocalDateTime toDate) {
        // Calculate account balance for the period using ledger entries
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndTransactionDateBetween(
            accountId, fromDate, toDate);

        BigDecimal balance = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            if (entry.getEntryType() == LedgerEntry.EntryType.DEBIT) {
                balance = balance.add(entry.getAmount());
            } else {
                balance = balance.subtract(entry.getAmount());
            }
        }

        return balance;
    }

    private LocalDate getLastCloseDate() {
        try {
            // Get the most recent accounting period close date
            String query = "SELECT MAX(close_date) FROM accounting_periods WHERE status = 'CLOSED'";
            LocalDate lastCloseDate = jdbcTemplate.queryForObject(query, LocalDate.class);
            
            if (lastCloseDate != null) {
                log.debug("Found last accounting period close date: {}", lastCloseDate);
                return lastCloseDate;
            }
            
            // If no closed periods found, return beginning of current fiscal year
            LocalDate currentDate = LocalDate.now();
            LocalDate fiscalYearStart = LocalDate.of(currentDate.getYear(), 1, 1);
            
            log.info("No closed accounting periods found. Using fiscal year start: {}", fiscalYearStart);
            return fiscalYearStart;
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to retrieve last accounting period close date - Financial reporting may be inaccurate", e);
            
            // Fallback to beginning of current month to ensure conservative reporting
            LocalDate fallbackDate = LocalDate.now().withDayOfMonth(1);
            log.warn("Using fallback close date for financial reporting: {}", fallbackDate);
            
            return fallbackDate;
        }
    }
    
    // Additional helper methods for balance sheet and income statement calculations
    
    private BigDecimal calculateAccountTypeBalance(Map<Account.AccountType, List<Account>> accountsByType,
                                                  Account.AccountType type, LocalDateTime asOfDate) {
        List<Account> accounts = accountsByType.getOrDefault(type, new ArrayList<>());
        BigDecimal totalBalance = BigDecimal.ZERO;
        
        for (Account account : accounts) {
            try {
                BalanceCalculationResult balance = balanceCalculationService.calculateBalanceAsOf(
                    account.getAccountId(), asOfDate);
                totalBalance = totalBalance.add(balance.getCurrentBalance());
            } catch (Exception e) {
                log.warn("Failed to calculate balance for account {} as of {}", 
                    account.getAccountCode(), asOfDate, e);
            }
        }
        
        return totalBalance;
    }
    
    private BigDecimal calculatePeriodActivity(Map<Account.AccountType, List<Account>> accountsByType,
                                              Account.AccountType type, LocalDateTime fromDate, LocalDateTime toDate) {
        List<Account> accounts = accountsByType.getOrDefault(type, new ArrayList<>());
        BigDecimal totalActivity = BigDecimal.ZERO;
        
        for (Account account : accounts) {
            try {
                List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndTransactionDateBetween(
                    account.getAccountId(), fromDate, toDate);
                
                for (LedgerEntry entry : entries) {
                    if (account.getNormalBalance() == Account.NormalBalance.CREDIT) {
                        // For revenue/liability accounts, credits increase the balance
                        if (entry.getEntryType() == LedgerEntry.EntryType.CREDIT) {
                            totalActivity = totalActivity.add(entry.getAmount());
                        } else {
                            totalActivity = totalActivity.subtract(entry.getAmount());
                        }
                    } else {
                        // For expense/asset accounts, debits increase the balance
                        if (entry.getEntryType() == LedgerEntry.EntryType.DEBIT) {
                            totalActivity = totalActivity.add(entry.getAmount());
                        } else {
                            totalActivity = totalActivity.subtract(entry.getAmount());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to calculate period activity for account type {} from {} to {}", 
                    type, fromDate, toDate, e);
            }
        }
        
        return totalActivity.abs(); // Return absolute value for income statement
    }
    
    private BigDecimal calculateRetainedEarnings(Map<Account.AccountType, List<Account>> accountsByType,
                                                LocalDateTime asOfDate) {
        // Retained Earnings = Beginning RE + Net Income - Dividends
        
        // Get beginning retained earnings (from RE account)
        BigDecimal beginningRE = calculateAccountTypeBalance(accountsByType, 
            Account.AccountType.RETAINED_EARNINGS, asOfDate);
        
        // Calculate current period net income
        LocalDateTime periodStart = asOfDate.toLocalDate().withDayOfMonth(1).atStartOfDay();
        BigDecimal netIncome = calculateNetIncome(accountsByType, periodStart, asOfDate);
        
        // Get dividends paid
        BigDecimal dividends = calculatePeriodActivity(accountsByType, 
            Account.AccountType.DIVIDENDS, periodStart, asOfDate);
        
        return beginningRE.add(netIncome).subtract(dividends);
    }
    
    private BigDecimal calculateNetIncome(Map<Account.AccountType, List<Account>> accountsByType,
                                        LocalDateTime fromDate, LocalDateTime toDate) {
        // Net Income = Total Revenue - Total Expenses
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        
        // Calculate total revenue
        for (Account.AccountType revenueType : getRevenueAccountTypes()) {
            totalRevenue = totalRevenue.add(calculatePeriodActivity(accountsByType, revenueType, fromDate, toDate));
        }
        
        // Calculate total expenses
        for (Account.AccountType expenseType : getExpenseAccountTypes()) {
            totalExpenses = totalExpenses.add(calculatePeriodActivity(accountsByType, expenseType, fromDate, toDate));
        }
        
        return totalRevenue.subtract(totalExpenses);
    }
    
    private List<Account.AccountType> getRevenueAccountTypes() {
        return Arrays.asList(
            Account.AccountType.SALES_REVENUE,
            Account.AccountType.SERVICE_REVENUE,
            Account.AccountType.INTEREST_INCOME,
            Account.AccountType.COMMISSION_INCOME,
            Account.AccountType.OTHER_REVENUE
        );
    }
    
    private List<Account.AccountType> getExpenseAccountTypes() {
        return Arrays.asList(
            Account.AccountType.COST_OF_GOODS_SOLD,
            Account.AccountType.SALARIES_EXPENSE,
            Account.AccountType.RENT_EXPENSE,
            Account.AccountType.UTILITIES_EXPENSE,
            Account.AccountType.MARKETING_EXPENSE,
            Account.AccountType.DEPRECIATION_EXPENSE,
            Account.AccountType.AMORTIZATION_EXPENSE,
            Account.AccountType.INSURANCE_EXPENSE,
            Account.AccountType.INTEREST_EXPENSE,
            Account.AccountType.TAX_EXPENSE,
            Account.AccountType.ADMINISTRATIVE_EXPENSE,
            Account.AccountType.OPERATIONAL_EXPENSE
        );
    }

    // ========== Methods for AccountingEventsConsumer ==========

    @Transactional
    public void recognizeRevenue(UUID eventId, String accountingPeriod, BigDecimal revenueAmount,
                                String revenueType, String customerId, String contractId) {
        log.info("Recognizing revenue: eventId={}, amount={}, type={}", eventId, revenueAmount, revenueType);
        // Implementation: Create journal entries for revenue recognition
    }

    @Transactional
    public void accrueExpense(UUID eventId, String accountingPeriod, BigDecimal expenseAmount,
                             String expenseCategory, String vendorId, LocalDateTime accrualDate) {
        log.info("Accruing expense: eventId={}, amount={}, category={}", eventId, expenseAmount, expenseCategory);
        // Implementation: Create journal entries for expense accrual
    }

    @Transactional
    public void recordDepreciation(UUID eventId, String accountingPeriod, String assetId,
                                  BigDecimal depreciationAmount, String depreciationMethod) {
        log.info("Recording depreciation: eventId={}, assetId={}, amount={}", eventId, assetId, depreciationAmount);
        // Implementation: Create depreciation journal entries
    }

    @Transactional
    public void closePeriod(UUID eventId, String accountingPeriod, String closeType, boolean finalClose) {
        log.info("Closing period: eventId={}, period={}, type={}, final={}", eventId, accountingPeriod, closeType, finalClose);
        // Implementation: Perform period closing procedures
    }

    @Transactional
    public void adjustBalance(UUID eventId, String accountingPeriod, String accountCode,
                            BigDecimal adjustmentAmount, String adjustmentReason, String approvedBy) {
        log.info("Adjusting balance: eventId={}, account={}, amount={}", eventId, accountCode, adjustmentAmount);
        // Implementation: Create balance adjustment journal entry
    }

    @Transactional
    public void reconcileAccount(UUID eventId, String accountingPeriod, String accountCode,
                                BigDecimal expectedBalance, BigDecimal actualBalance, BigDecimal variance) {
        log.info("Reconciling account: eventId={}, account={}, variance={}", eventId, accountCode, variance);
        // Implementation: Record reconciliation results
    }

    @Transactional
    public void accrueTax(UUID eventId, String accountingPeriod, BigDecimal taxAmount,
                        String taxType, String jurisdiction) {
        log.info("Accruing tax: eventId={}, amount={}, type={}", eventId, taxAmount, taxType);
        // Implementation: Create tax accrual journal entries
    }

    @Transactional
    public void processGenericEvent(UUID eventId, String eventType, String accountingPeriod,
                                   Map<String, Object> event) {
        log.info("Processing generic accounting event: eventId={}, type={}", eventId, eventType);
        // Implementation: Handle generic accounting events
    }

    @Transactional
    public void handleEventFailure(UUID eventId, String eventType, String errorMessage) {
        log.error("Handling event failure: eventId={}, type={}, error={}", eventId, eventType, errorMessage);
        // Implementation: Record event failure for manual review
    }

    @Transactional
    public void markEventForManualReview(UUID eventId, String eventType, String reason) {
        log.warn("Marking event for manual review: eventId={}, type={}, reason={}", eventId, eventType, reason);
        // Implementation: Flag event for manual review
    }
}

// Supporting DTOs and data classes that need to be created
// These would be in the dto package in a real implementation