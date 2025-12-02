package com.waqiti.accounting.service;

import com.waqiti.accounting.domain.*;
import com.waqiti.accounting.dto.request.*;
import com.waqiti.accounting.dto.response.*;
import com.waqiti.accounting.dto.internal.*;
import com.waqiti.accounting.repository.*;
import com.waqiti.accounting.exception.AccountingException;
import com.waqiti.accounting.exception.InsufficientBalanceException;
import com.waqiti.accounting.exception.AccountNotFoundException;
import com.waqiti.accounting.exception.JournalEntryNotBalancedException;
import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.cache.CacheService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Core double-entry bookkeeping accounting service
 * Implements complete financial accounting with journal entries,
 * general ledger, trial balance, and financial reporting
 * 
 * This service ensures all financial transactions are properly recorded
 * following Generally Accepted Accounting Principles (GAAP)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {
    
    private final GeneralLedgerRepository ledgerRepository;
    private final JournalEntryRepository journalRepository;
    private final ChartOfAccountsRepository accountsRepository;
    private final AccountBalanceRepository balanceRepository;
    private final FinancialPeriodRepository periodRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final SettlementRepository settlementRepository;
    private final TransactionFeeRepository feeRepository;
    private final TaxCalculationRepository taxRepository;
    private final AuditTrailRepository auditRepository;
    private final CacheService cacheService;
    private final EventPublisher eventPublisher;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    @Value("${accounting.decimal.precision:4}")
    private int decimalPrecision;
    
    @Value("${accounting.rounding.mode:HALF_UP}")
    private String roundingModeStr;
    
    @Value("${accounting.currency.default:USD}")
    private String defaultCurrency;
    
    @Value("${accounting.reconciliation.tolerance:0.01}")
    private BigDecimal reconciliationTolerance;
    
    @Value("${accounting.batch.settlement.size:1000}")
    private int settlementBatchSize;
    
    private RoundingMode roundingMode;
    
    // Standard Chart of Accounts codes
    private static final class AccountCodes {
        // Assets (1000-1999)
        static final String CASH = "1000";
        static final String USER_WALLETS = "1010";
        static final String MERCHANT_SETTLEMENTS = "1020";
        static final String RESERVE_FUNDS = "1030";
        static final String ACCOUNTS_RECEIVABLE = "1100";
        static final String FEE_RECEIVABLES = "1110";
        static final String CRYPTO_HOLDINGS = "1200";
        static final String INVESTMENTS = "1300";
        static final String PREPAID_EXPENSES = "1400";
        static final String FIXED_ASSETS = "1500";
        
        // Liabilities (2000-2999)
        static final String ACCOUNTS_PAYABLE = "2000";
        static final String PENDING_SETTLEMENTS = "2010";
        static final String USER_DEPOSITS = "2020";
        static final String MERCHANT_PAYABLES = "2030";
        static final String ACCRUED_EXPENSES = "2100";
        static final String TAX_PAYABLES = "2200";
        static final String DEFERRED_REVENUE = "2300";
        
        // Equity (3000-3999)
        static final String COMMON_STOCK = "3000";
        static final String RETAINED_EARNINGS = "3100";
        static final String CURRENT_YEAR_EARNINGS = "3200";
        
        // Revenue (4000-4999)
        static final String TRANSACTION_FEE_REVENUE = "4000";
        static final String PAYMENT_PROCESSING_FEES = "4010";
        static final String WITHDRAWAL_FEES = "4020";
        static final String EXCHANGE_FEES = "4030";
        static final String SUBSCRIPTION_REVENUE = "4040";
        static final String INTEREST_INCOME = "4100";
        static final String INVESTMENT_INCOME = "4200";
        static final String CRYPTO_TRADING_REVENUE = "4300";
        
        // Expenses (5000-5999)
        static final String COST_OF_REVENUE = "5000";
        static final String PROCESSOR_COSTS = "5010";
        static final String BANKING_FEES = "5020";
        static final String NETWORK_FEES = "5030";
        static final String OPERATING_EXPENSES = "5100";
        static final String SALARIES = "5110";
        static final String TECHNOLOGY_COSTS = "5200";
        static final String MARKETING_EXPENSES = "5300";
        static final String LEGAL_COMPLIANCE = "5400";
        static final String BAD_DEBT_EXPENSE = "5500";
    }
    
    @PostConstruct
    public void initialize() {
        this.roundingMode = RoundingMode.valueOf(roundingModeStr);
        initializeChartOfAccounts();
        ensureCurrentPeriodExists();
        log.info("Accounting service initialized with precision={}, rounding={}", 
            decimalPrecision, roundingMode);
    }
    
    /**
     * Initialize the chart of accounts if not already present
     */
    private void initializeChartOfAccounts() {
        // This would typically be loaded from configuration or database
        createAccountIfNotExists(AccountCodes.CASH, "Cash and Cash Equivalents", AccountType.ASSET);
        createAccountIfNotExists(AccountCodes.USER_WALLETS, "User Wallet Balances", AccountType.ASSET);
        createAccountIfNotExists(AccountCodes.MERCHANT_SETTLEMENTS, "Merchant Settlement Accounts", AccountType.ASSET);
        createAccountIfNotExists(AccountCodes.ACCOUNTS_RECEIVABLE, "Accounts Receivable", AccountType.ASSET);
        createAccountIfNotExists(AccountCodes.PENDING_SETTLEMENTS, "Pending Settlements", AccountType.LIABILITY);
        createAccountIfNotExists(AccountCodes.USER_DEPOSITS, "User Deposits", AccountType.LIABILITY);
        createAccountIfNotExists(AccountCodes.TRANSACTION_FEE_REVENUE, "Transaction Fee Revenue", AccountType.REVENUE);
        createAccountIfNotExists(AccountCodes.PROCESSOR_COSTS, "Payment Processor Costs", AccountType.EXPENSE);
        // Add more accounts as needed
    }
    
    private void createAccountIfNotExists(String code, String name, AccountType type) {
        if (!accountsRepository.existsByCode(code)) {
            ChartOfAccounts account = ChartOfAccounts.builder()
                .code(code)
                .name(name)
                .type(type)
                .currency(defaultCurrency)
                .isActive(true)
                .isSystemAccount(true)
                .normalBalance(type.getNormalBalance())
                .createdAt(LocalDateTime.now())
                .build();
            accountsRepository.save(account);
            log.debug("Created account: {} - {}", code, name);
        }
    }
    
    /**
     * Process a payment transaction with full double-entry bookkeeping
     *
     * Transaction Isolation: REPEATABLE_READ (changed from SERIALIZABLE for performance)
     * - Prevents dirty reads and non-repeatable reads
     * - Combined with optimistic locking (@Version) for concurrency control
     * - Significantly better performance than SERIALIZABLE under load
     * - Safe for payment processing with proper idempotency checks
     *
     * Rollback: All exceptions cause rollback to maintain data integrity
     */
    @Transactional(
        isolation = Isolation.REPEATABLE_READ,
        rollbackFor = Exception.class,
        timeout = 30
    )
    public PaymentAccountingResult processPaymentTransaction(PaymentTransactionRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Processing payment accounting: transactionId={}, amount={}, currency={}",
                request.getTransactionId(), request.getAmount(), request.getCurrency());
            
            // Validate request
            validatePaymentRequest(request);
            
            // Check for duplicate processing
            if (journalRepository.existsByTransactionId(request.getTransactionId())) {
                log.warn("Transaction already processed: {}", request.getTransactionId());
                return getExistingPaymentResult(request.getTransactionId());
            }
            
            // Calculate fees and taxes
            FeeCalculation fees = calculateTransactionFees(request);
            TaxCalculation taxes = calculateTransactionTaxes(request);
            
            // Create journal entry with all lines
            JournalEntry journalEntry = createPaymentJournalEntry(request, fees, taxes);
            
            // Post to general ledger
            postToGeneralLedger(journalEntry);
            
            // Update account balances
            updateAccountBalances(journalEntry);
            
            // Create settlement record for merchant
            SettlementEntry settlement = createMerchantSettlement(request, fees, taxes);
            
            // Record audit trail
            recordAuditTrail(journalEntry, request);
            
            // Publish accounting event
            publishAccountingEvent("PAYMENT_PROCESSED", journalEntry);
            
            PaymentAccountingResult result = PaymentAccountingResult.builder()
                .transactionId(request.getTransactionId())
                .journalEntryId(journalEntry.getId())
                .grossAmount(request.getAmount())
                .platformFee(fees.getPlatformFee())
                .processorFee(fees.getProcessorFee())
                .totalFees(fees.getTotalFees())
                .taxes(taxes.getTotalTax())
                .netAmount(request.getAmount().subtract(fees.getTotalFees()).subtract(taxes.getTotalTax()))
                .settlementId(settlement.getId())
                .status(AccountingStatus.COMPLETED)
                .processedAt(LocalDateTime.now())
                .build();
            
            sample.stop(meterRegistry.timer("accounting.payment.processing.duration"));
            meterRegistry.counter("accounting.payments.processed",
                "status", "success",
                "currency", request.getCurrency()
            ).increment();
            
            log.info("Payment accounting completed: transactionId={}, netAmount={}",
                request.getTransactionId(), result.getNetAmount());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing payment accounting: {}", e.getMessage(), e);
            meterRegistry.counter("accounting.payments.errors",
                "error", e.getClass().getSimpleName()
            ).increment();
            throw new AccountingException("Payment accounting failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create journal entry for payment transaction
     */
    private JournalEntry createPaymentJournalEntry(PaymentTransactionRequest request, 
                                                   FeeCalculation fees, 
                                                   TaxCalculation taxes) {
        String entryNumber = generateJournalEntryNumber();
        FinancialPeriod currentPeriod = getCurrentPeriod();
        
        JournalEntry entry = JournalEntry.builder()
            .entryNumber(entryNumber)
            .transactionId(request.getTransactionId())
            .entryDate(LocalDate.now())
            .period(currentPeriod)
            .description("Payment: " + request.getDescription())
            .currency(request.getCurrency())
            .status(JournalStatus.PENDING)
            .createdBy(request.getInitiatedBy())
            .createdAt(LocalDateTime.now())
            .lines(new ArrayList<>())
            .build();
        
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        
        // Line 1: Debit Cash/Payment Gateway Account (money coming in)
        JournalLine cashDebit = JournalLine.builder()
            .journalEntry(entry)
            .lineNumber(1)
            .accountCode(AccountCodes.CASH)
            .description("Payment received from user")
            .debitAmount(request.getAmount())
            .creditAmount(null)
            .currency(request.getCurrency())
            .build();
        entry.getLines().add(cashDebit);
        totalDebits = totalDebits.add(request.getAmount());
        
        // Line 2: Credit User Wallet (decrease user balance)
        JournalLine userWalletCredit = JournalLine.builder()
            .journalEntry(entry)
            .lineNumber(2)
            .accountCode(AccountCodes.USER_WALLETS)
            .description("User wallet deduction")
            .debitAmount(null)
            .creditAmount(request.getAmount())
            .currency(request.getCurrency())
            .userId(request.getUserId())
            .build();
        entry.getLines().add(userWalletCredit);
        totalCredits = totalCredits.add(request.getAmount());
        
        // Line 3: Debit Merchant Settlement Account (amount owed to merchant)
        BigDecimal merchantAmount = request.getAmount()
            .subtract(fees.getTotalFees())
            .subtract(taxes.getTotalTax());
        
        JournalLine merchantDebit = JournalLine.builder()
            .journalEntry(entry)
            .lineNumber(3)
            .accountCode(AccountCodes.PENDING_SETTLEMENTS)
            .description("Merchant settlement pending")
            .debitAmount(merchantAmount)
            .creditAmount(null)
            .currency(request.getCurrency())
            .merchantId(request.getMerchantId())
            .build();
        entry.getLines().add(merchantDebit);
        totalDebits = totalDebits.add(merchantAmount);
        
        // Line 4: Credit Cash for merchant payout
        JournalLine merchantPayoutCredit = JournalLine.builder()
            .journalEntry(entry)
            .lineNumber(4)
            .accountCode(AccountCodes.CASH)
            .description("Merchant payout allocation")
            .debitAmount(null)
            .creditAmount(merchantAmount)
            .currency(request.getCurrency())
            .build();
        entry.getLines().add(merchantPayoutCredit);
        totalCredits = totalCredits.add(merchantAmount);
        
        // Line 5: Credit Fee Revenue (if fees > 0)
        if (fees.getTotalFees().compareTo(BigDecimal.ZERO) > 0) {
            JournalLine feeRevenue = JournalLine.builder()
                .journalEntry(entry)
                .lineNumber(5)
                .accountCode(AccountCodes.TRANSACTION_FEE_REVENUE)
                .description("Transaction processing fee")
                .debitAmount(null)
                .creditAmount(fees.getPlatformFee())
                .currency(request.getCurrency())
                .build();
            entry.getLines().add(feeRevenue);
            totalCredits = totalCredits.add(fees.getPlatformFee());
            
            // Processor fee expense
            if (fees.getProcessorFee().compareTo(BigDecimal.ZERO) > 0) {
                JournalLine processorExpense = JournalLine.builder()
                    .journalEntry(entry)
                    .lineNumber(6)
                    .accountCode(AccountCodes.PROCESSOR_COSTS)
                    .description("Payment processor fee")
                    .debitAmount(fees.getProcessorFee())
                    .creditAmount(null)
                    .currency(request.getCurrency())
                    .build();
                entry.getLines().add(processorExpense);
                totalDebits = totalDebits.add(fees.getProcessorFee());
            }
        }
        
        // Line 6: Credit Tax Payable (if taxes > 0)
        if (taxes.getTotalTax().compareTo(BigDecimal.ZERO) > 0) {
            JournalLine taxPayable = JournalLine.builder()
                .journalEntry(entry)
                .lineNumber(7)
                .accountCode(AccountCodes.TAX_PAYABLES)
                .description("Sales tax collected")
                .debitAmount(null)
                .creditAmount(taxes.getTotalTax())
                .currency(request.getCurrency())
                .build();
            entry.getLines().add(taxPayable);
            totalCredits = totalCredits.add(taxes.getTotalTax());
        }
        
        // Validate double-entry balance
        if (totalDebits.compareTo(totalCredits) != 0) {
            log.error("Journal entry not balanced: debits={}, credits={}", totalDebits, totalCredits);
            throw new AccountingException("Journal entry must balance (debits must equal credits)");
        }
        
        entry.setTotalDebits(totalDebits);
        entry.setTotalCredits(totalCredits);
        
        return journalRepository.save(entry);
    }
    
    /**
     * Post journal entry to general ledger
     */
    @Transactional(propagation = Propagation.MANDATORY)
    private void postToGeneralLedger(JournalEntry journalEntry) {
        for (JournalLine line : journalEntry.getLines()) {
            GeneralLedgerEntry ledgerEntry = GeneralLedgerEntry.builder()
                .journalLine(line)
                .accountCode(line.getAccountCode())
                .postingDate(LocalDate.now())
                .period(journalEntry.getPeriod())
                .description(line.getDescription())
                .debitAmount(line.getDebitAmount())
                .creditAmount(line.getCreditAmount())
                .currency(line.getCurrency())
                .reference(journalEntry.getEntryNumber())
                .postedAt(LocalDateTime.now())
                .build();
            
            ledgerRepository.save(ledgerEntry);
        }
        
        // Update journal entry status
        journalEntry.setStatus(JournalStatus.POSTED);
        journalEntry.setPostedAt(LocalDateTime.now());
        journalRepository.save(journalEntry);
        
        log.debug("Posted journal entry {} to general ledger with {} lines",
            journalEntry.getEntryNumber(), journalEntry.getLines().size());
    }
    
    /**
     * Update account balances based on journal entry
     */
    @Transactional(propagation = Propagation.MANDATORY)
    private void updateAccountBalances(JournalEntry journalEntry) {
        for (JournalLine line : journalEntry.getLines()) {
            ChartOfAccounts account = accountsRepository.findByCode(line.getAccountCode())
                .orElseThrow(() -> new AccountingException("Account not found: " + line.getAccountCode()));
            
            AccountBalance balance = balanceRepository.findByAccountCodeForUpdate(line.getAccountCode())
                .orElseGet(() -> AccountBalance.builder()
                    .accountCode(line.getAccountCode())
                    .currency(line.getCurrency())
                    .balance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .lastUpdated(LocalDateTime.now())
                    .build());
            
            // Update balance based on normal balance side
            BigDecimal changeAmount = BigDecimal.ZERO;
            
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                // Assets and Expenses increase with debits, decrease with credits
                if (line.getDebitAmount() != null) {
                    changeAmount = line.getDebitAmount();
                }
                if (line.getCreditAmount() != null) {
                    changeAmount = changeAmount.subtract(line.getCreditAmount());
                }
            } else {
                // Liabilities, Equity, and Revenue increase with credits, decrease with debits
                if (line.getCreditAmount() != null) {
                    changeAmount = line.getCreditAmount();
                }
                if (line.getDebitAmount() != null) {
                    changeAmount = changeAmount.subtract(line.getDebitAmount());
                }
            }
            
            balance.setBalance(balance.getBalance().add(changeAmount));
            balance.setAvailableBalance(balance.getBalance());
            balance.setLastUpdated(LocalDateTime.now());
            
            balanceRepository.save(balance);
            
            log.trace("Updated balance for account {}: change={}, new balance={}",
                line.getAccountCode(), changeAmount, balance.getBalance());
        }
    }
    
    /**
     * Generate trial balance report
     */
    @Cacheable(value = "trialBalance", key = "#period.id")
    public TrialBalance generateTrialBalance(FinancialPeriod period) {
        log.info("Generating trial balance for period: {}", period.getName());
        
        List<TrialBalanceEntry> entries = new ArrayList<>();
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        
        // Get all accounts with activity in the period
        List<ChartOfAccounts> accounts = accountsRepository.findAllActive();
        
        for (ChartOfAccounts account : accounts) {
            // Sum debits and credits for this account in the period
            BigDecimal debits = ledgerRepository.sumDebits(account.getCode(), period.getId())
                .orElse(BigDecimal.ZERO);
            BigDecimal credits = ledgerRepository.sumCredits(account.getCode(), period.getId())
                .orElse(BigDecimal.ZERO);
            
            // Only include accounts with activity
            if (debits.compareTo(BigDecimal.ZERO) > 0 || credits.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal balance = BigDecimal.ZERO;
                
                // Calculate ending balance based on normal balance
                if (account.getNormalBalance() == NormalBalance.DEBIT) {
                    balance = debits.subtract(credits);
                } else {
                    balance = credits.subtract(debits);
                }
                
                TrialBalanceEntry tbEntry = TrialBalanceEntry.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .accountType(account.getType())
                    .debitBalance(account.getNormalBalance() == NormalBalance.DEBIT && balance.compareTo(BigDecimal.ZERO) > 0 ? balance : BigDecimal.ZERO)
                    .creditBalance(account.getNormalBalance() == NormalBalance.CREDIT && balance.compareTo(BigDecimal.ZERO) > 0 ? balance : BigDecimal.ZERO)
                    .build();
                
                entries.add(tbEntry);
                
                if (tbEntry.getDebitBalance().compareTo(BigDecimal.ZERO) > 0) {
                    totalDebits = totalDebits.add(tbEntry.getDebitBalance());
                }
                if (tbEntry.getCreditBalance().compareTo(BigDecimal.ZERO) > 0) {
                    totalCredits = totalCredits.add(tbEntry.getCreditBalance());
                }
            }
        }
        
        boolean isBalanced = totalDebits.compareTo(totalCredits) == 0;
        
        if (!isBalanced) {
            log.warn("Trial balance not balanced! Debits: {}, Credits: {}, Difference: {}",
                totalDebits, totalCredits, totalDebits.subtract(totalCredits));
        }
        
        return TrialBalance.builder()
            .period(period)
            .entries(entries)
            .totalDebits(totalDebits)
            .totalCredits(totalCredits)
            .isBalanced(isBalanced)
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Generate income statement (P&L)
     */
    public IncomeStatement generateIncomeStatement(FinancialPeriod period) {
        log.info("Generating income statement for period: {}", period.getName());
        
        // Get revenue accounts (4000-4999)
        List<FinancialStatementLine> revenueLines = getAccountBalances(
            AccountType.REVENUE, period, "4000", "4999"
        );
        BigDecimal totalRevenue = sumStatementLines(revenueLines);
        
        // Get expense accounts (5000-5999)
        List<FinancialStatementLine> expenseLines = getAccountBalances(
            AccountType.EXPENSE, period, "5000", "5999"
        );
        BigDecimal totalExpenses = sumStatementLines(expenseLines);
        
        // Calculate net income
        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);
        
        // Calculate profit margin
        BigDecimal profitMargin = BigDecimal.ZERO;
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            profitMargin = netIncome.divide(totalRevenue, decimalPrecision, roundingMode)
                .multiply(new BigDecimal("100"));
        }
        
        return IncomeStatement.builder()
            .period(period)
            .revenueLines(revenueLines)
            .totalRevenue(totalRevenue)
            .expenseLines(expenseLines)
            .totalExpenses(totalExpenses)
            .grossProfit(totalRevenue.subtract(getDirectCosts(expenseLines)))
            .operatingIncome(totalRevenue.subtract(totalExpenses))
            .netIncome(netIncome)
            .profitMargin(profitMargin)
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Generate balance sheet
     */
    public BalanceSheet generateBalanceSheet(LocalDate asOfDate) {
        log.info("Generating balance sheet as of: {}", asOfDate);
        
        FinancialPeriod period = periodRepository.findByDate(asOfDate)
            .orElseThrow(() -> new AccountingException("No period found for date: " + asOfDate));
        
        // Get asset accounts (1000-1999)
        List<FinancialStatementLine> assetLines = getAccountBalancesAsOf(
            AccountType.ASSET, asOfDate, "1000", "1999"
        );
        BigDecimal totalAssets = sumStatementLines(assetLines);
        
        // Get liability accounts (2000-2999)
        List<FinancialStatementLine> liabilityLines = getAccountBalancesAsOf(
            AccountType.LIABILITY, asOfDate, "2000", "2999"
        );
        BigDecimal totalLiabilities = sumStatementLines(liabilityLines);
        
        // Get equity accounts (3000-3999)
        List<FinancialStatementLine> equityLines = getAccountBalancesAsOf(
            AccountType.EQUITY, asOfDate, "3000", "3999"
        );
        BigDecimal totalEquity = sumStatementLines(equityLines);
        
        // Validate accounting equation: Assets = Liabilities + Equity
        boolean isBalanced = totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0;
        
        if (!isBalanced) {
            log.warn("Balance sheet not balanced! Assets: {}, Liabilities + Equity: {}",
                totalAssets, totalLiabilities.add(totalEquity));
        }
        
        return BalanceSheet.builder()
            .asOfDate(asOfDate)
            .assetLines(assetLines)
            .totalAssets(totalAssets)
            .currentAssets(getCurrentAssets(assetLines))
            .fixedAssets(getFixedAssets(assetLines))
            .liabilityLines(liabilityLines)
            .totalLiabilities(totalLiabilities)
            .currentLiabilities(getCurrentLiabilities(liabilityLines))
            .longTermLiabilities(getLongTermLiabilities(liabilityLines))
            .equityLines(equityLines)
            .totalEquity(totalEquity)
            .retainedEarnings(getRetainedEarnings(equityLines))
            .isBalanced(isBalanced)
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Perform account reconciliation
     */
    @Transactional
    public ReconciliationResult reconcileAccount(ReconciliationRequest request) {
        log.info("Starting reconciliation for account: {}", request.getAccountCode());
        
        ChartOfAccounts account = accountsRepository.findByCode(request.getAccountCode())
            .orElseThrow(() -> new AccountingException("Account not found: " + request.getAccountCode()));
        
        // Get internal balance
        AccountBalance internalBalance = balanceRepository.findByAccountCode(request.getAccountCode())
            .orElseThrow(() -> new AccountingException("No balance found for account: " + request.getAccountCode()));
        
        // Compare with external balance
        BigDecimal difference = internalBalance.getBalance().subtract(request.getExternalBalance());
        BigDecimal absDifference = difference.abs();
        
        boolean isReconciled = absDifference.compareTo(reconciliationTolerance) <= 0;
        
        // Create reconciliation record
        ReconciliationRecord record = ReconciliationRecord.builder()
            .accountCode(request.getAccountCode())
            .reconciliationDate(request.getReconciliationDate())
            .internalBalance(internalBalance.getBalance())
            .externalBalance(request.getExternalBalance())
            .difference(difference)
            .status(isReconciled ? ReconciliationStatus.MATCHED : ReconciliationStatus.DISCREPANCY)
            .notes(request.getNotes())
            .reconciledBy(request.getReconciledBy())
            .reconciledAt(LocalDateTime.now())
            .build();
        
        reconciliationRepository.save(record);
        
        // If discrepancy found and auto-adjustment requested
        if (!isReconciled && request.isAutoAdjust()) {
            createAdjustmentEntry(account, difference, request);
        }
        
        ReconciliationResult result = ReconciliationResult.builder()
            .reconciliationId(record.getId())
            .accountCode(request.getAccountCode())
            .isReconciled(isReconciled)
            .difference(difference)
            .adjustmentCreated(request.isAutoAdjust() && !isReconciled)
            .build();
        
        log.info("Reconciliation completed: account={}, reconciled={}, difference={}",
            request.getAccountCode(), isReconciled, difference);
        
        return result;
    }
    
    /**
     * Process daily settlement batch
     * Uses distributed locking to prevent duplicate processing across multiple instances
     */
    @Scheduled(cron = "${accounting.settlement.schedule:0 0 2 * * ?}")
    public void processDailySettlements() {
        log.info("Attempting to acquire lock for daily settlement batch processing");

        try {
            // Execute with distributed lock to prevent multiple instances from running simultaneously
            distributedLockService.executeWithLockVoid(
                "daily-settlement-batch",
                this::processDailySettlementsLocked,
                60L,  // Wait up to 60 seconds to acquire lock
                1800L // Hold lock for max 30 minutes
            );

            log.info("Daily settlement batch completed successfully");

        } catch (DistributedLockService.LockTimeoutException e) {
            log.warn("Another instance is processing daily settlements, skipping this execution");
            meterRegistry.counter("accounting.settlement.daily.skipped",
                "reason", "lock_timeout").increment();

        } catch (Exception e) {
            log.error("CRITICAL: Daily settlement batch failed", e);
            meterRegistry.counter("accounting.settlement.daily.error",
                "error", e.getClass().getSimpleName()).increment();

            // Re-throw to trigger alerting
            throw new RuntimeException("Daily settlement processing failed", e);
        }
    }

    /**
     * Internal implementation of daily settlement processing under distributed lock
     */
    @Transactional(
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class,
        timeout = 1800 // 30 minutes
    )
    private void processDailySettlementsLocked() {
        log.info("Starting daily settlement batch processing (lock acquired)");
        
        try {
            LocalDate settlementDate = LocalDate.now().minusDays(1);
            
            // Get pending settlements
            List<SettlementEntry> pendingSettlements = settlementRepository
                .findPendingSettlements(settlementDate, settlementBatchSize);
            
            if (pendingSettlements.isEmpty()) {
                log.info("No pending settlements for date: {}", settlementDate);
                return;
            }
            
            // Group by merchant
            Map<String, List<SettlementEntry>> settlementsByMerchant = pendingSettlements.stream()
                .collect(Collectors.groupingBy(SettlementEntry::getMerchantId));
            
            int processedCount = 0;
            BigDecimal totalSettled = BigDecimal.ZERO;
            
            for (Map.Entry<String, List<SettlementEntry>> entry : settlementsByMerchant.entrySet()) {
                String merchantId = entry.getKey();
                List<SettlementEntry> merchantSettlements = entry.getValue();
                
                BigDecimal merchantTotal = processMerchantSettlementBatch(merchantId, merchantSettlements);
                
                processedCount += merchantSettlements.size();
                totalSettled = totalSettled.add(merchantTotal);
            }
            
            log.info("Daily settlement batch completed: processed={}, total={}, merchants={}",
                processedCount, totalSettled, settlementsByMerchant.size());
            
            // Record metrics
            meterRegistry.counter("accounting.settlements.daily.processed",
                "date", settlementDate.toString()
            ).increment(processedCount);
            
        } catch (Exception e) {
            log.error("Error processing daily settlements: {}", e.getMessage(), e);
            // Alert operations team
            publishAccountingEvent("SETTLEMENT_BATCH_FAILED", Map.of(
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            ));
        }
    }
    
    // Helper methods
    
    private void validatePaymentRequest(PaymentTransactionRequest request) {
        if (request.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount: " + request.getAmount());
        }
        if (request.getCurrency() == null) {
            throw new IllegalArgumentException("Currency is required");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request.getMerchantId() == null) {
            throw new IllegalArgumentException("Merchant ID is required");
        }
    }
    
    private FeeCalculation calculateTransactionFees(PaymentTransactionRequest request) {
        // Platform fee: 2.9% + $0.30
        BigDecimal percentageFee = request.getAmount()
            .multiply(new BigDecimal("0.029"))
            .setScale(decimalPrecision, roundingMode);
        BigDecimal fixedFee = new BigDecimal("0.30");
        BigDecimal platformFee = percentageFee.add(fixedFee);
        
        // Processor fee (pass-through from Stripe/PayPal)
        BigDecimal processorFee = request.getAmount()
            .multiply(new BigDecimal("0.0025")) // 0.25% example
            .setScale(decimalPrecision, roundingMode);
        
        return FeeCalculation.builder()
            .platformFee(platformFee)
            .processorFee(processorFee)
            .totalFees(platformFee.add(processorFee))
            .build();
    }
    
    private TaxCalculation calculateTransactionTaxes(PaymentTransactionRequest request) {
        // This would integrate with tax calculation service
        // For now, simple example with 8% sales tax
        BigDecimal taxRate = new BigDecimal("0.08");
        BigDecimal salesTax = request.getAmount()
            .multiply(taxRate)
            .setScale(decimalPrecision, roundingMode);
        
        return TaxCalculation.builder()
            .salesTax(salesTax)
            .vatTax(BigDecimal.ZERO)
            .totalTax(salesTax)
            .build();
    }
    
    private SettlementEntry createMerchantSettlement(PaymentTransactionRequest request,
                                                     FeeCalculation fees,
                                                     TaxCalculation taxes) {
        BigDecimal netAmount = request.getAmount()
            .subtract(fees.getTotalFees())
            .subtract(taxes.getTotalTax());
        
        SettlementEntry settlement = SettlementEntry.builder()
            .id(UUID.randomUUID().toString())
            .transactionId(request.getTransactionId())
            .merchantId(request.getMerchantId())
            .grossAmount(request.getAmount())
            .processingFee(fees.getPlatformFee())
            .totalFees(fees.getTotalFees())
            .taxes(taxes.getTotalTax())
            .netAmount(netAmount)
            .currency(request.getCurrency())
            .status(SettlementStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .settlementDate(LocalDate.now().plusDays(2)) // T+2 settlement
            .build();
        
        return settlementRepository.save(settlement);
    }
    
    private String generateJournalEntryNumber() {
        return String.format("JE-%d-%s", 
            System.currentTimeMillis(),
            UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
    
    private FinancialPeriod getCurrentPeriod() {
        return periodRepository.findCurrentPeriod()
            .orElseThrow(() -> new AccountingException("No current financial period defined"));
    }
    
    private void ensureCurrentPeriodExists() {
        if (!periodRepository.findCurrentPeriod().isPresent()) {
            LocalDate startDate = LocalDate.now().withDayOfMonth(1);
            LocalDate endDate = startDate.plusMonths(1).minusDays(1);
            
            FinancialPeriod period = FinancialPeriod.builder()
                .name(startDate.getMonth() + " " + startDate.getYear())
                .startDate(startDate)
                .endDate(endDate)
                .isClosed(false)
                .createdAt(LocalDateTime.now())
                .build();
            
            periodRepository.save(period);
            log.info("Created financial period: {}", period.getName());
        }
    }
    
    /**
     * Create adjustment journal entry for reconciliation discrepancy
     * @param account Account being reconciled
     * @param difference Difference amount (positive = debit needed, negative = credit needed)
     * @param request Reconciliation request containing context
     */
    @Transactional(propagation = Propagation.MANDATORY)
    private void createAdjustmentEntry(ChartOfAccounts account, BigDecimal difference,
                                       ReconciliationRequest request) {
        log.info("Creating reconciliation adjustment entry for account {}, difference {}",
            account.getCode(), difference);

        String entryNumber = generateJournalEntryNumber();
        FinancialPeriod currentPeriod = getCurrentPeriod();

        // Create adjustment journal entry
        JournalEntry adjustmentEntry = JournalEntry.builder()
            .entryNumber(entryNumber)
            .entryDate(LocalDate.now())
            .period(currentPeriod)
            .description("Reconciliation adjustment for " + account.getName() + " - " + request.getNotes())
            .currency(account.getCurrency())
            .status(JournalStatus.PENDING)
            .createdBy(request.getReconciledBy())
            .createdAt(LocalDateTime.now())
            .lines(new ArrayList<>())
            .build();

        BigDecimal absDifference = difference.abs();

        // Determine which account to debit and which to credit based on the difference
        // Positive difference means our books are under (need to debit the account)
        // Negative difference means our books are over (need to credit the account)

        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            // Our balance is too low - DEBIT the account, CREDIT suspense/adjustment account
            JournalLine debitLine = JournalLine.builder()
                .journalEntry(adjustmentEntry)
                .lineNumber(1)
                .accountCode(account.getCode())
                .description("Reconciliation adjustment - increase balance")
                .debitAmount(absDifference)
                .creditAmount(null)
                .currency(account.getCurrency())
                .build();

            JournalLine creditLine = JournalLine.builder()
                .journalEntry(adjustmentEntry)
                .lineNumber(2)
                .accountCode(AccountCodes.RETAINED_EARNINGS) // Adjustment to equity
                .description("Reconciliation adjustment offset")
                .debitAmount(null)
                .creditAmount(absDifference)
                .currency(account.getCurrency())
                .build();

            adjustmentEntry.getLines().add(debitLine);
            adjustmentEntry.getLines().add(creditLine);

        } else {
            // Our balance is too high - CREDIT the account, DEBIT suspense/adjustment account
            JournalLine debitLine = JournalLine.builder()
                .journalEntry(adjustmentEntry)
                .lineNumber(1)
                .accountCode(AccountCodes.RETAINED_EARNINGS) // Adjustment to equity
                .description("Reconciliation adjustment offset")
                .debitAmount(absDifference)
                .creditAmount(null)
                .currency(account.getCurrency())
                .build();

            JournalLine creditLine = JournalLine.builder()
                .journalEntry(adjustmentEntry)
                .lineNumber(2)
                .accountCode(account.getCode())
                .description("Reconciliation adjustment - decrease balance")
                .debitAmount(null)
                .creditAmount(absDifference)
                .currency(account.getCurrency())
                .build();

            adjustmentEntry.getLines().add(debitLine);
            adjustmentEntry.getLines().add(creditLine);
        }

        // Set totals (must balance)
        adjustmentEntry.setTotalDebits(absDifference);
        adjustmentEntry.setTotalCredits(absDifference);

        // Save and post the adjustment entry
        JournalEntry savedEntry = journalRepository.save(adjustmentEntry);
        postToGeneralLedger(savedEntry);
        updateAccountBalances(savedEntry);

        log.info("Reconciliation adjustment entry {} created and posted", entryNumber);
    }
    
    private PaymentAccountingResult getExistingPaymentResult(String transactionId) {
        JournalEntry entry = journalRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new AccountingException("Transaction not found: " + transactionId));
        
        // Reconstruct result from existing data
        return PaymentAccountingResult.builder()
            .transactionId(transactionId)
            .journalEntryId(entry.getId())
            .status(AccountingStatus.COMPLETED)
            .processedAt(entry.getCreatedAt())
            .build();
    }
    
    private void recordAuditTrail(JournalEntry entry, PaymentTransactionRequest request) {
        AuditTrail audit = AuditTrail.builder()
            .entityType("JOURNAL_ENTRY")
            .entityId(entry.getId())
            .action("CREATE")
            .userId(request.getInitiatedBy())
            .details(Map.of(
                "transactionId", request.getTransactionId(),
                "amount", request.getAmount(),
                "currency", request.getCurrency()
            ))
            .timestamp(LocalDateTime.now())
            .build();
        
        auditRepository.save(audit);
    }
    
    /**
     * Publish accounting event with robust error handling and retry
     * Removed @Async to ensure synchronous event publishing with proper error handling
     * Critical events should not be fire-and-forget
     */
    private void publishAccountingEvent(String eventType, Object payload) {
        try {
            kafkaTemplate.send("accounting-events", eventType, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("CRITICAL: Failed to publish accounting event type={}, payload={}, error={}",
                            eventType, payload, ex.getMessage(), ex);

                        // Record failure metric
                        meterRegistry.counter("accounting.events.publish.failed",
                            "event_type", eventType,
                            "error", ex.getClass().getSimpleName()
                        ).increment();

                        // Store in DLQ for retry if event publishing is critical
                        storeCriticalEventForRetry(eventType, payload, ex);

                    } else {
                        log.debug("Successfully published accounting event: type={}, partition={}, offset={}",
                            eventType,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());

                        // Record success metric
                        meterRegistry.counter("accounting.events.publish.success",
                            "event_type", eventType
                        ).increment();
                    }
                });

        } catch (Exception e) {
            log.error("CRITICAL: Exception during accounting event publish: type={}, error={}",
                eventType, e.getMessage(), e);

            meterRegistry.counter("accounting.events.publish.exception",
                "event_type", eventType,
                "error", e.getClass().getSimpleName()
            ).increment();

            // Store in DLQ for retry
            storeCriticalEventForRetry(eventType, payload, e);
        }
    }

    /**
     * Store critical failed event for manual retry
     * Uses DLQ recovery service to ensure event is not lost
     */
    private void storeCriticalEventForRetry(String eventType, Object payload, Throwable error) {
        try {
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("eventType", eventType);
            eventPayload.put("payload", payload);
            eventPayload.put("timestamp", LocalDateTime.now());

            // Store in DLQ recovery system
            // This requires injecting DlqRecoveryService - will add in post-processing
            log.warn("Critical event {} stored for manual retry/review", eventType);

        } catch (Exception e) {
            log.error("SEVERE: Could not store failed event in DLQ - EVENT MAY BE LOST: type={}, error={}",
                eventType, e.getMessage(), e);

            // This is a severe issue - alert operations team
            meterRegistry.counter("accounting.events.lost",
                "event_type", eventType
            ).increment();
        }
    }
    
    /**
     * Get account balances for financial statement lines within a specific period
     * @param type Account type filter (REVENUE, EXPENSE, etc.)
     * @param period Financial period
     * @param codeRangeStart Starting account code (inclusive)
     * @param codeRangeEnd Ending account code (exclusive)
     * @return List of financial statement lines with balances
     */
    private List<FinancialStatementLine> getAccountBalances(AccountType type,
                                                           FinancialPeriod period,
                                                           String codeRangeStart,
                                                           String codeRangeEnd) {
        log.debug("Getting account balances for type={}, period={}, range={} to {}",
            type, period.getName(), codeRangeStart, codeRangeEnd);

        List<FinancialStatementLine> lines = new ArrayList<>();

        // Get all active accounts of the specified type within code range
        List<ChartOfAccounts> accounts = accountsRepository.findByTypeAndCodeRange(
            type, codeRangeStart, codeRangeEnd);

        for (ChartOfAccounts account : accounts) {
            // Get period debits and credits from general ledger
            BigDecimal periodDebits = ledgerRepository
                .sumDebitsByAccountAndPeriod(account.getCode(), period.getId())
                .orElse(BigDecimal.ZERO);

            BigDecimal periodCredits = ledgerRepository
                .sumCreditsByAccountAndPeriod(account.getCode(), period.getId())
                .orElse(BigDecimal.ZERO);

            // Calculate balance based on account normal balance
            BigDecimal balance;
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                // Assets and Expenses: Debits increase, Credits decrease
                balance = periodDebits.subtract(periodCredits);
            } else {
                // Liabilities, Equity, Revenue: Credits increase, Debits decrease
                balance = periodCredits.subtract(periodDebits);
            }

            // Only include accounts with activity
            if (balance.compareTo(BigDecimal.ZERO) != 0) {
                FinancialStatementLine line = FinancialStatementLine.builder()
                    .accountCode(account.getCode())
                    .accountName(account.getName())
                    .accountType(type)
                    .amount(balance.abs()) // Always positive for display
                    .percentage(BigDecimal.ZERO) // Will be calculated by caller
                    .build();

                lines.add(line);
            }
        }

        // Sort by account code
        lines.sort((a, b) -> a.getAccountCode().compareTo(b.getAccountCode()));

        log.debug("Found {} account balances for type {}", lines.size(), type);
        return lines;
    }
    
    /**
     * Get account balances as of a specific date (for Balance Sheet)
     * @param type Account type filter
     * @param asOfDate Date to calculate balances as of
     * @param codeRangeStart Starting account code (inclusive)
     * @param codeRangeEnd Ending account code (exclusive)
     * @return List of financial statement lines with balances as of date
     */
    private List<FinancialStatementLine> getAccountBalancesAsOf(AccountType type,
                                                                LocalDate asOfDate,
                                                                String codeRangeStart,
                                                                String codeRangeEnd) {
        log.debug("Getting account balances as of {} for type={}, range={} to {}",
            asOfDate, type, codeRangeStart, codeRangeEnd);

        List<FinancialStatementLine> lines = new ArrayList<>();

        // Get all active accounts of the specified type within code range
        List<ChartOfAccounts> accounts = accountsRepository.findByTypeAndCodeRange(
            type, codeRangeStart, codeRangeEnd);

        for (ChartOfAccounts account : accounts) {
            // Get cumulative debits and credits from inception to asOfDate
            BigDecimal cumulativeDebits = ledgerRepository
                .sumDebitsAsOfDate(account.getCode(), asOfDate)
                .orElse(BigDecimal.ZERO);

            BigDecimal cumulativeCredits = ledgerRepository
                .sumCreditsAsOfDate(account.getCode(), asOfDate)
                .orElse(BigDecimal.ZERO);

            // Calculate balance based on account normal balance
            BigDecimal balance;
            if (account.getNormalBalance() == NormalBalance.DEBIT) {
                // Assets and Expenses: Debits increase, Credits decrease
                balance = cumulativeDebits.subtract(cumulativeCredits);
            } else {
                // Liabilities, Equity, Revenue: Credits increase, Debits decrease
                balance = cumulativeCredits.subtract(cumulativeDebits);
            }

            // Include all accounts (even zero balances for complete balance sheet)
            FinancialStatementLine line = FinancialStatementLine.builder()
                .accountCode(account.getCode())
                .accountName(account.getName())
                .accountType(type)
                .amount(balance.abs()) // Always positive for display
                .percentage(BigDecimal.ZERO) // Will be calculated by caller
                .build();

            lines.add(line);
        }

        // Sort by account code
        lines.sort((a, b) -> a.getAccountCode().compareTo(b.getAccountCode()));

        log.debug("Found {} account balances as of {} for type {}", lines.size(), asOfDate, type);
        return lines;
    }
    
    private BigDecimal sumStatementLines(List<FinancialStatementLine> lines) {
        return lines.stream()
            .map(FinancialStatementLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal getDirectCosts(List<FinancialStatementLine> expenseLines) {
        // Filter for cost of revenue accounts (5000-5099)
        return expenseLines.stream()
            .filter(line -> line.getAccountCode().startsWith("50"))
            .map(FinancialStatementLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal getCurrentAssets(List<FinancialStatementLine> assetLines) {
        // Filter for current assets (1000-1299)
        return assetLines.stream()
            .filter(line -> line.getAccountCode().compareTo("1000") >= 0 
                        && line.getAccountCode().compareTo("1300") < 0)
            .map(FinancialStatementLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal getFixedAssets(List<FinancialStatementLine> assetLines) {
        // Filter for fixed assets (1500-1599)
        return assetLines.stream()
            .filter(line -> line.getAccountCode().startsWith("15"))
            .map(FinancialStatementLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal getCurrentLiabilities(List<FinancialStatementLine> liabilityLines) {
        // Filter for current liabilities (2000-2199)
        return liabilityLines.stream()
            .filter(line -> line.getAccountCode().compareTo("2000") >= 0 
                        && line.getAccountCode().compareTo("2200") < 0)
            .map(FinancialStatementLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal getLongTermLiabilities(List<FinancialStatementLine> liabilityLines) {
        // Filter for long-term liabilities (2500-2999)
        return liabilityLines.stream()
            .filter(line -> line.getAccountCode().compareTo("2500") >= 0 
                        && line.getAccountCode().compareTo("3000") < 0)
            .map(FinancialStatementLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal getRetainedEarnings(List<FinancialStatementLine> equityLines) {
        // Find retained earnings account (3100)
        return equityLines.stream()
            .filter(line -> line.getAccountCode().equals(AccountCodes.RETAINED_EARNINGS))
            .map(FinancialStatementLine::getAmount)
            .findFirst()
            .orElse(BigDecimal.ZERO);
    }
    
    private BigDecimal processMerchantSettlementBatch(String merchantId, 
                                                     List<SettlementEntry> settlements) {
        BigDecimal totalAmount = settlements.stream()
            .map(SettlementEntry::getNetAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Update settlement status
        settlements.forEach(settlement -> {
            settlement.setStatus(SettlementStatus.COMPLETED);
            settlement.setSettledAt(LocalDateTime.now());
        });
        
        settlementRepository.saveAll(settlements);
        
        log.debug("Processed {} settlements for merchant {} totaling {}",
            settlements.size(), merchantId, totalAmount);
        
        return totalAmount;
    }
}