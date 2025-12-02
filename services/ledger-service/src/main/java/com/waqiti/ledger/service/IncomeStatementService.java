package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.Account;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.exception.AccountingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced Income Statement Service
 * 
 * Provides comprehensive income statement generation with:
 * - Complete revenue and expense categorization
 * - Gross profit, operating income, and net income calculations
 * - Financial ratio analysis and key performance metrics
 * - Multi-period comparative analysis
 * - Percentage of sales analysis
 * - Operating leverage and margin analysis
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class IncomeStatementService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    /**
     * Generates comprehensive income statement with complete calculations
     */
    @Cacheable(value = "incomeStatement", key = "#startDate + '_' + #endDate + '_' + #format")
    public IncomeStatementResponse generateIncomeStatement(LocalDate startDate, LocalDate endDate, 
                                                         IncomeStatementFormat format) {
        try {
            log.info("Generating {} income statement from {} to {}", format, startDate, endDate);
            
            LocalDateTime fromDateTime = startDate.atStartOfDay();
            LocalDateTime toDateTime = endDate.atTime(23, 59, 59);
            
            // Calculate all income statement sections
            IncomeStatementSection revenue = calculateRevenueSection(fromDateTime, toDateTime);
            IncomeStatementSection costOfGoodsSold = calculateCostOfGoodsSoldSection(fromDateTime, toDateTime);
            IncomeStatementSection operatingExpenses = calculateOperatingExpensesSection(fromDateTime, toDateTime);
            IncomeStatementSection otherIncome = calculateOtherIncomeSection(fromDateTime, toDateTime);
            IncomeStatementSection otherExpenses = calculateOtherExpensesSection(fromDateTime, toDateTime);
            IncomeStatementSection interestExpense = calculateInterestExpenseSection(fromDateTime, toDateTime);
            IncomeStatementSection taxes = calculateTaxExpenseSection(fromDateTime, toDateTime);
            
            // Calculate key totals
            BigDecimal totalRevenue = revenue.getTotalAmount();
            BigDecimal totalCogs = costOfGoodsSold.getTotalAmount();
            BigDecimal grossProfit = totalRevenue.subtract(totalCogs);
            
            BigDecimal totalOperatingExpenses = operatingExpenses.getTotalAmount();
            BigDecimal operatingIncome = grossProfit.subtract(totalOperatingExpenses);
            
            BigDecimal totalOtherIncome = otherIncome.getTotalAmount();
            BigDecimal totalOtherExpenses = otherExpenses.getTotalAmount();
            BigDecimal totalInterestExpense = interestExpense.getTotalAmount();
            
            BigDecimal incomeBeforeTaxes = operatingIncome
                .add(totalOtherIncome)
                .subtract(totalOtherExpenses)
                .subtract(totalInterestExpense);
            
            BigDecimal totalTaxes = taxes.getTotalAmount();
            BigDecimal netIncome = incomeBeforeTaxes.subtract(totalTaxes);
            
            // Calculate financial ratios and margins
            FinancialRatios ratios = calculateFinancialRatios(totalRevenue, grossProfit, 
                operatingIncome, netIncome, totalOperatingExpenses);
            
            return IncomeStatementResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .format(format.toString())
                .currency("USD")
                // Revenue section
                .revenue(revenue)
                .totalRevenue(totalRevenue)
                // Cost of goods sold
                .costOfGoodsSold(costOfGoodsSold)
                .totalCostOfGoodsSold(totalCogs)
                // Gross profit
                .grossProfit(grossProfit)
                .grossMarginPercentage(calculatePercentageOfSales(grossProfit, totalRevenue))
                // Operating expenses
                .operatingExpenses(operatingExpenses)
                .totalOperatingExpenses(totalOperatingExpenses)
                // Operating income
                .operatingIncome(operatingIncome)
                .operatingMarginPercentage(calculatePercentageOfSales(operatingIncome, totalRevenue))
                // Other income and expenses
                .otherIncome(otherIncome)
                .otherExpenses(otherExpenses)
                .interestExpense(interestExpense)
                // Pre-tax income
                .incomeBeforeTaxes(incomeBeforeTaxes)
                // Taxes
                .taxExpense(taxes)
                .totalTaxExpense(totalTaxes)
                .effectiveTaxRate(calculateEffectiveTaxRate(totalTaxes, incomeBeforeTaxes))
                // Net income
                .netIncome(netIncome)
                .netMarginPercentage(calculatePercentageOfSales(netIncome, totalRevenue))
                // Financial metrics
                .financialRatios(ratios)
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate income statement", e);
            throw new AccountingException("Failed to generate income statement", e);
        }
    }

    /**
     * Generates comparative income statement showing changes over time
     */
    public ComparativeIncomeStatementResponse generateComparativeIncomeStatement(
            LocalDate currentStart, LocalDate currentEnd, 
            LocalDate priorStart, LocalDate priorEnd, 
            IncomeStatementFormat format) {
        
        try {
            log.info("Generating comparative income statement: {}-{} vs {}-{}", 
                currentStart, currentEnd, priorStart, priorEnd);
            
            // Generate income statements for both periods
            IncomeStatementResponse currentPeriod = generateIncomeStatement(currentStart, currentEnd, format);
            IncomeStatementResponse priorPeriod = generateIncomeStatement(priorStart, priorEnd, format);
            
            // Calculate variances
            Map<String, VarianceAnalysis> variances = calculateIncomeStatementVariances(currentPeriod, priorPeriod);
            
            // Generate growth analysis
            GrowthAnalysis growth = calculateGrowthAnalysis(currentPeriod, priorPeriod);
            
            return ComparativeIncomeStatementResponse.builder()
                .currentPeriod(currentPeriod)
                .priorPeriod(priorPeriod)
                .variances(variances)
                .growthAnalysis(growth)
                .analysisCommentary(generateVarianceCommentary(variances, growth))
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate comparative income statement", e);
            throw new AccountingException("Failed to generate comparative income statement", e);
        }
    }

    /**
     * Calculate revenue section with detailed breakdown
     */
    private IncomeStatementSection calculateRevenueSection(LocalDateTime startDate, LocalDateTime endDate) {
        List<IncomeStatementLineItem> revenueItems = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        
        // Get all revenue accounts
        List<Account> revenueAccounts = accountRepository.findByAccountTypeInAndIsActiveTrue(
            Arrays.asList(Account.AccountType.REVENUE, Account.AccountType.OPERATING_REVENUE));
        
        for (Account account : revenueAccounts) {
            BigDecimal accountRevenue = calculateAccountBalance(account, startDate, endDate, true);
            totalRevenue = totalRevenue.add(accountRevenue);
            
            revenueItems.add(IncomeStatementLineItem.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(accountRevenue)
                .percentageOfSales(BigDecimal.ZERO) // Will be calculated later
                .build());
        }
        
        // Calculate percentage of sales for each item
        calculatePercentagesOfSales(revenueItems, totalRevenue);
        
        return IncomeStatementSection.builder()
            .sectionName("Revenue")
            .sectionType("REVENUE")
            .lineItems(revenueItems)
            .totalAmount(totalRevenue)
            .build();
    }

    /**
     * Calculate cost of goods sold section
     */
    private IncomeStatementSection calculateCostOfGoodsSoldSection(LocalDateTime startDate, LocalDateTime endDate) {
        List<IncomeStatementLineItem> cogsItems = new ArrayList<>();
        BigDecimal totalCogs = BigDecimal.ZERO;
        
        // Get cost of goods sold accounts
        List<Account> cogsAccounts = accountRepository.findByIsActiveTrueAndAccountNameContainingIgnoreCase("cost of goods");
        
        // If no specific COGS accounts, look for expense accounts that might be COGS
        if (cogsAccounts.isEmpty()) {
            cogsAccounts = accountRepository.findByIsActiveTrueAndAccountCodeStartingWith("5000");
        }
        
        for (Account account : cogsAccounts) {
            BigDecimal accountCogs = calculateAccountBalance(account, startDate, endDate, false);
            totalCogs = totalCogs.add(accountCogs);
            
            cogsItems.add(IncomeStatementLineItem.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(accountCogs)
                .percentageOfSales(BigDecimal.ZERO)
                .build());
        }
        
        return IncomeStatementSection.builder()
            .sectionName("Cost of Goods Sold")
            .sectionType("EXPENSE")
            .lineItems(cogsItems)
            .totalAmount(totalCogs)
            .build();
    }

    /**
     * Calculate operating expenses section
     */
    private IncomeStatementSection calculateOperatingExpensesSection(LocalDateTime startDate, LocalDateTime endDate) {
        List<IncomeStatementLineItem> expenseItems = new ArrayList<>();
        BigDecimal totalExpenses = BigDecimal.ZERO;
        
        // Get operating expense accounts
        List<Account> expenseAccounts = accountRepository.findByAccountTypeAndIsActiveTrue(Account.AccountType.OPERATING_EXPENSE);
        
        // If no specific operating expense accounts, get general expenses
        if (expenseAccounts.isEmpty()) {
            expenseAccounts = accountRepository.findByAccountTypeAndIsActiveTrue(Account.AccountType.EXPENSE);
        }
        
        for (Account account : expenseAccounts) {
            // Skip COGS accounts if they were already included
            if (!account.getAccountName().toLowerCase().contains("cost of goods")) {
                BigDecimal accountExpense = calculateAccountBalance(account, startDate, endDate, false);
                totalExpenses = totalExpenses.add(accountExpense);
                
                expenseItems.add(IncomeStatementLineItem.builder()
                    .accountId(account.getAccountId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .amount(accountExpense)
                    .percentageOfSales(BigDecimal.ZERO)
                    .build());
            }
        }
        
        return IncomeStatementSection.builder()
            .sectionName("Operating Expenses")
            .sectionType("EXPENSE")
            .lineItems(expenseItems)
            .totalAmount(totalExpenses)
            .build();
    }

    /**
     * Calculate other income section
     */
    private IncomeStatementSection calculateOtherIncomeSection(LocalDateTime startDate, LocalDateTime endDate) {
        List<IncomeStatementLineItem> otherIncomeItems = new ArrayList<>();
        BigDecimal totalOtherIncome = BigDecimal.ZERO;
        
        // Get other revenue accounts
        List<Account> otherIncomeAccounts = accountRepository.findByAccountTypeAndIsActiveTrue(Account.AccountType.OTHER_REVENUE);
        
        for (Account account : otherIncomeAccounts) {
            BigDecimal accountIncome = calculateAccountBalance(account, startDate, endDate, true);
            totalOtherIncome = totalOtherIncome.add(accountIncome);
            
            otherIncomeItems.add(IncomeStatementLineItem.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(accountIncome)
                .percentageOfSales(BigDecimal.ZERO)
                .build());
        }
        
        return IncomeStatementSection.builder()
            .sectionName("Other Income")
            .sectionType("REVENUE")
            .lineItems(otherIncomeItems)
            .totalAmount(totalOtherIncome)
            .build();
    }

    /**
     * Calculate other expenses section
     */
    private IncomeStatementSection calculateOtherExpensesSection(LocalDateTime startDate, LocalDateTime endDate) {
        List<IncomeStatementLineItem> otherExpenseItems = new ArrayList<>();
        BigDecimal totalOtherExpenses = BigDecimal.ZERO;
        
        // Get other expense accounts
        List<Account> otherExpenseAccounts = accountRepository.findByAccountTypeAndIsActiveTrue(Account.AccountType.OTHER_EXPENSE);
        
        for (Account account : otherExpenseAccounts) {
            BigDecimal accountExpense = calculateAccountBalance(account, startDate, endDate, false);
            totalOtherExpenses = totalOtherExpenses.add(accountExpense);
            
            otherExpenseItems.add(IncomeStatementLineItem.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(accountExpense)
                .percentageOfSales(BigDecimal.ZERO)
                .build());
        }
        
        return IncomeStatementSection.builder()
            .sectionName("Other Expenses")
            .sectionType("EXPENSE")
            .lineItems(otherExpenseItems)
            .totalAmount(totalOtherExpenses)
            .build();
    }

    /**
     * Calculate interest expense section
     */
    private IncomeStatementSection calculateInterestExpenseSection(LocalDateTime startDate, LocalDateTime endDate) {
        List<IncomeStatementLineItem> interestItems = new ArrayList<>();
        BigDecimal totalInterest = BigDecimal.ZERO;
        
        // Get interest expense accounts
        List<Account> interestAccounts = accountRepository.findByIsActiveTrueAndAccountNameContainingIgnoreCase("interest");
        
        for (Account account : interestAccounts) {
            if (account.getAccountName().toLowerCase().contains("expense") || 
                account.getAccountType() == Account.AccountType.EXPENSE) {
                BigDecimal accountInterest = calculateAccountBalance(account, startDate, endDate, false);
                totalInterest = totalInterest.add(accountInterest);
                
                interestItems.add(IncomeStatementLineItem.builder()
                    .accountId(account.getAccountId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .amount(accountInterest)
                    .percentageOfSales(BigDecimal.ZERO)
                    .build());
            }
        }
        
        return IncomeStatementSection.builder()
            .sectionName("Interest Expense")
            .sectionType("EXPENSE")
            .lineItems(interestItems)
            .totalAmount(totalInterest)
            .build();
    }

    /**
     * Calculate tax expense section
     */
    private IncomeStatementSection calculateTaxExpenseSection(LocalDateTime startDate, LocalDateTime endDate) {
        List<IncomeStatementLineItem> taxItems = new ArrayList<>();
        BigDecimal totalTaxes = BigDecimal.ZERO;
        
        // Get tax expense accounts
        List<Account> taxAccounts = accountRepository.findByIsActiveTrueAndAccountNameContainingIgnoreCase("tax");
        
        for (Account account : taxAccounts) {
            if (account.getAccountName().toLowerCase().contains("expense") || 
                account.getAccountType() == Account.AccountType.EXPENSE) {
                BigDecimal accountTax = calculateAccountBalance(account, startDate, endDate, false);
                totalTaxes = totalTaxes.add(accountTax);
                
                taxItems.add(IncomeStatementLineItem.builder()
                    .accountId(account.getAccountId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .amount(accountTax)
                    .percentageOfSales(BigDecimal.ZERO)
                    .build());
            }
        }
        
        return IncomeStatementSection.builder()
            .sectionName("Tax Expense")
            .sectionType("EXPENSE")
            .lineItems(taxItems)
            .totalAmount(totalTaxes)
            .build();
    }

    /**
     * Calculate account balance for the specified period
     */
    private BigDecimal calculateAccountBalance(Account account, LocalDateTime startDate, 
                                             LocalDateTime endDate, boolean isRevenue) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndTransactionDateBetween(
            account.getAccountId(), startDate, endDate);
        
        BigDecimal balance = BigDecimal.ZERO;
        for (LedgerEntry entry : entries) {
            if (isRevenue) {
                // Revenue: Credits are positive, Debits are negative
                balance = balance.add(entry.getEntryType() == LedgerEntry.EntryType.CREDIT ? 
                    entry.getAmount() : entry.getAmount().negate());
            } else {
                // Expenses: Debits are positive, Credits are negative
                balance = balance.add(entry.getEntryType() == LedgerEntry.EntryType.DEBIT ? 
                    entry.getAmount() : entry.getAmount().negate());
            }
        }
        
        return balance;
    }

    /**
     * Calculate financial ratios
     */
    private FinancialRatios calculateFinancialRatios(BigDecimal revenue, BigDecimal grossProfit, 
                                                   BigDecimal operatingIncome, BigDecimal netIncome,
                                                   BigDecimal operatingExpenses) {
        return FinancialRatios.builder()
            .grossMargin(calculatePercentageOfSales(grossProfit, revenue))
            .operatingMargin(calculatePercentageOfSales(operatingIncome, revenue))
            .netMargin(calculatePercentageOfSales(netIncome, revenue))
            .operatingRatio(calculatePercentageOfSales(operatingExpenses, revenue))
            .returnOnSales(calculatePercentageOfSales(netIncome, revenue))
            .build();
    }

    /**
     * Calculate percentage of sales
     */
    private BigDecimal calculatePercentageOfSales(BigDecimal amount, BigDecimal sales) {
        if (sales.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return amount.divide(sales, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calculate effective tax rate
     */
    private BigDecimal calculateEffectiveTaxRate(BigDecimal taxes, BigDecimal incomeBeforeTaxes) {
        if (incomeBeforeTaxes.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return taxes.divide(incomeBeforeTaxes, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    /**
     * Calculate percentages of sales for line items
     */
    private void calculatePercentagesOfSales(List<IncomeStatementLineItem> items, BigDecimal totalRevenue) {
        items.forEach(item -> {
            BigDecimal percentage = calculatePercentageOfSales(item.getAmount(), totalRevenue);
            item.setPercentageOfSales(percentage);
        });
    }

    /**
     * Calculate variances between periods
     */
    private Map<String, VarianceAnalysis> calculateIncomeStatementVariances(
            IncomeStatementResponse current, IncomeStatementResponse prior) {
        Map<String, VarianceAnalysis> variances = new HashMap<>();
        
        variances.put("Total Revenue", createVarianceAnalysis(
            current.getTotalRevenue(), prior.getTotalRevenue()));
        variances.put("Gross Profit", createVarianceAnalysis(
            current.getGrossProfit(), prior.getGrossProfit()));
        variances.put("Operating Income", createVarianceAnalysis(
            current.getOperatingIncome(), prior.getOperatingIncome()));
        variances.put("Net Income", createVarianceAnalysis(
            current.getNetIncome(), prior.getNetIncome()));
        
        return variances;
    }

    /**
     * Calculate growth analysis
     */
    private GrowthAnalysis calculateGrowthAnalysis(IncomeStatementResponse current, IncomeStatementResponse prior) {
        return GrowthAnalysis.builder()
            .revenueGrowth(calculateGrowthRate(current.getTotalRevenue(), prior.getTotalRevenue()))
            .grossProfitGrowth(calculateGrowthRate(current.getGrossProfit(), prior.getGrossProfit()))
            .operatingIncomeGrowth(calculateGrowthRate(current.getOperatingIncome(), prior.getOperatingIncome()))
            .netIncomeGrowth(calculateGrowthRate(current.getNetIncome(), prior.getNetIncome()))
            .build();
    }

    private BigDecimal calculateGrowthRate(BigDecimal current, BigDecimal prior) {
        if (prior.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(prior).divide(prior.abs(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private VarianceAnalysis createVarianceAnalysis(BigDecimal current, BigDecimal prior) {
        BigDecimal variance = current.subtract(prior);
        BigDecimal percentageChange = BigDecimal.ZERO;
        
        if (prior.compareTo(BigDecimal.ZERO) != 0) {
            percentageChange = variance.divide(prior.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
        
        return VarianceAnalysis.builder()
            .currentAmount(current)
            .priorAmount(prior)
            .variance(variance)
            .percentageChange(percentageChange)
            .favorable(variance.compareTo(BigDecimal.ZERO) >= 0)
            .significant(percentageChange.abs().compareTo(BigDecimal.valueOf(10)) > 0)
            .build();
    }

    private String generateVarianceCommentary(Map<String, VarianceAnalysis> variances, GrowthAnalysis growth) {
        StringBuilder commentary = new StringBuilder();
        commentary.append("Income Statement Variance Analysis:\n\n");
        
        variances.forEach((item, analysis) -> {
            commentary.append(String.format("%s: %s by %s (%.2f%%) - %s\n",
                item,
                analysis.getFavorable() ? "Increased" : "Decreased",
                analysis.getVariance().abs(),
                analysis.getPercentageChange().abs(),
                analysis.getSignificant() ? "Significant change" : "Normal variation"
            ));
        });
        
        commentary.append(String.format("\nGrowth Rates:\nRevenue: %.2f%%, Operating Income: %.2f%%, Net Income: %.2f%%",
            growth.getRevenueGrowth(), growth.getOperatingIncomeGrowth(), growth.getNetIncomeGrowth()));
        
        return commentary.toString();
    }

    public enum IncomeStatementFormat {
        SINGLE_STEP,    // Revenue - Expenses = Net Income
        MULTI_STEP,     // Detailed step-by-step calculation
        FUNCTIONAL,     // Organized by function (Sales, Admin, etc.)
        COMPARATIVE     // Side-by-side period comparison
    }
}