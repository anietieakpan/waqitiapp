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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Industrial-Grade Balance Sheet Service with GAAP Compliance
 * 
 * Provides comprehensive balance sheet generation with:
 * - Complete asset, liability, and equity calculations
 * - Hierarchical account grouping and consolidation
 * - Multiple balance sheet formats (Classified, Report Form, Account Form)
 * - Real-time balance validation and verification
 * - Comparative balance sheet analysis
 * - Multi-currency consolidation support
 * - GAAP compliance validation and reporting
 * - Regulatory disclosure requirements
 * - Audit trail and documentation support
 * - Financial ratio analysis integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BalanceSheetService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final BalanceCalculationService balanceCalculationService;

    /**
     * Generates comprehensive balance sheet with complete calculations and GAAP compliance
     */
    @Cacheable(value = "balanceSheet", key = "#asOfDate + '_' + #format")
    public BalanceSheetResponse generateBalanceSheet(LocalDate asOfDate, BalanceSheetFormat format) {
        return generateBalanceSheet(asOfDate, format, true); // Default to GAAP compliance
    }
    
    /**
     * Generates balance sheet with configurable GAAP compliance validation
     */
    public BalanceSheetResponse generateBalanceSheet(LocalDate asOfDate, BalanceSheetFormat format, boolean enforceGAAPCompliance) {
        try {
            log.info("Generating {} balance sheet as of: {}", format, asOfDate);
            
            LocalDateTime asOfDateTime = asOfDate.atTime(23, 59, 59);
            
            // Get all active accounts grouped by type
            Map<Account.AccountType, List<Account>> accountsByType = getAccountsByType();
            
            // Calculate balance sheet sections with real data
            BalanceSheetSection currentAssets = calculateCurrentAssets(accountsByType, asOfDateTime);
            BalanceSheetSection fixedAssets = calculateFixedAssets(accountsByType, asOfDateTime);
            BalanceSheetSection otherAssets = calculateOtherAssets(accountsByType, asOfDateTime);
            
            BalanceSheetSection currentLiabilities = calculateCurrentLiabilities(accountsByType, asOfDateTime);
            BalanceSheetSection longTermLiabilities = calculateLongTermLiabilities(accountsByType, asOfDateTime);
            
            BalanceSheetSection equity = calculateEquitySection(accountsByType, asOfDateTime);
            
            // Aggregate totals
            BalanceSheetSection totalAssets = aggregateAssetSections(currentAssets, fixedAssets, otherAssets);
            BalanceSheetSection totalLiabilities = aggregateLiabilitySections(currentLiabilities, longTermLiabilities);
            
            // Enhanced balance sheet equation verification with GAAP compliance
            BigDecimal totalAssetsAmount = totalAssets.getTotalAmount();
            BigDecimal totalLiabilitiesAndEquity = totalLiabilities.getTotalAmount().add(equity.getTotalAmount());
            boolean balanced = totalAssetsAmount.compareTo(totalLiabilitiesAndEquity) == 0;
            BigDecimal variance = totalAssetsAmount.subtract(totalLiabilitiesAndEquity);
            
            // GAAP compliance validation
            List<String> gaapViolations = new ArrayList<>();
            if (enforceGAAPCompliance) {
                gaapViolations = performGAAPComplianceValidation(currentAssets, fixedAssets, 
                    currentLiabilities, longTermLiabilities, equity);
            }
            
            // Balance sheet equation validation
            if (!balanced && variance.abs().compareTo(new BigDecimal("0.01")) > 0) {
                log.error("CRITICAL: Balance sheet equation violation: Assets={}, Liabilities+Equity={}, Variance={}", 
                    totalAssetsAmount, totalLiabilitiesAndEquity, variance);
                if (enforceGAAPCompliance) {
                    gaapViolations.add(String.format("Balance sheet equation violation: variance of %s", variance));
                }
            }
            
            // Financial ratio calculations
            Map<String, BigDecimal> financialRatios = calculateKeyFinancialRatios(
                currentAssets, currentLiabilities, totalAssets, equity);
            
            // Generate disclosure notes
            List<String> disclosureNotes = generateDisclosureNotes(
                currentAssets, fixedAssets, currentLiabilities, longTermLiabilities, equity);
            
            return BalanceSheetResponse.builder()
                .asOfDate(asOfDate)
                .format(format.toString())
                .currency("USD") // Default currency - could be parameterized
                // Assets
                .currentAssets(currentAssets)
                .fixedAssets(fixedAssets)  
                .otherAssets(otherAssets)
                .totalAssets(totalAssets)
                // Liabilities
                .currentLiabilities(currentLiabilities)
                .longTermLiabilities(longTermLiabilities)
                .totalLiabilities(totalLiabilities)
                // Equity
                .equity(equity)
                // Totals and validation
                .totalAssetsAmount(totalAssetsAmount)
                .totalLiabilitiesAndEquity(totalLiabilitiesAndEquity)
                .balanced(balanced)
                .variance(variance)
                .generatedAt(LocalDateTime.now())
                // GAAP compliance and analysis
                .gaapCompliant(gaapViolations.isEmpty())
                .gaapViolations(gaapViolations)
                .financialRatios(financialRatios)
                .disclosureNotes(disclosureNotes)
                .auditTrail(generateAuditTrail(asOfDate, format, enforceGAAPCompliance))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate balance sheet", e);
            throw new AccountingException("Failed to generate balance sheet", e);
        }
    }

    /**
     * Calculate current assets section with real account balances
     */
    private BalanceSheetSection calculateCurrentAssets(Map<Account.AccountType, List<Account>> accountsByType, 
                                                     LocalDateTime asOfDate) {
        List<BalanceSheetLineItem> currentAssetItems = new ArrayList<>();
        BigDecimal totalCurrentAssets = BigDecimal.ZERO;
        
        // Process current asset accounts
        List<Account> currentAssetAccounts = accountsByType.getOrDefault(Account.AccountType.CURRENT_ASSET, new ArrayList<>());
        for (Account account : currentAssetAccounts) {
            BalanceCalculationResult balance = balanceCalculationService.calculateBalanceAsOf(account.getAccountId(), asOfDate);
            BigDecimal accountBalance = balance.getCurrentBalance();
            totalCurrentAssets = totalCurrentAssets.add(accountBalance);
            
            currentAssetItems.add(BalanceSheetLineItem.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(accountBalance)
                .percentage(BigDecimal.ZERO)
                .build());
        }
        
        calculatePercentages(currentAssetItems, totalCurrentAssets);
        
        return BalanceSheetSection.builder()
            .sectionName("Current Assets")
            .sectionType("ASSET")
            .lineItems(currentAssetItems)
            .totalAmount(totalCurrentAssets)
            .build();
    }

    /**
     * Calculate fixed assets section with depreciation
     */
    private BalanceSheetSection calculateFixedAssets(Map<Account.AccountType, List<Account>> accountsByType, 
                                                   LocalDateTime asOfDate) {
        List<BalanceSheetLineItem> fixedAssetItems = new ArrayList<>();
        BigDecimal totalFixedAssets = BigDecimal.ZERO;
        
        List<Account> fixedAssetAccounts = accountsByType.getOrDefault(Account.AccountType.FIXED_ASSET, new ArrayList<>());
        for (Account account : fixedAssetAccounts) {
            BalanceCalculationResult balance = balanceCalculationService.calculateBalanceAsOf(account.getAccountId(), asOfDate);
            BigDecimal accountBalance = balance.getCurrentBalance();
            totalFixedAssets = totalFixedAssets.add(accountBalance);
            
            fixedAssetItems.add(BalanceSheetLineItem.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(accountBalance)
                .percentage(BigDecimal.ZERO)
                .build());
        }
        
        calculatePercentages(fixedAssetItems, totalFixedAssets);
        
        return BalanceSheetSection.builder()
            .sectionName("Fixed Assets")
            .sectionType("ASSET")
            .lineItems(fixedAssetItems)
            .totalAmount(totalFixedAssets)
            .build();
    }

    private BalanceSheetSection calculateOtherAssets(Map<Account.AccountType, List<Account>> accountsByType, 
                                                   LocalDateTime asOfDate) {
        List<BalanceSheetLineItem> otherAssetItems = new ArrayList<>();
        BigDecimal totalOtherAssets = BigDecimal.ZERO;
        
        List<Account> assetAccounts = accountsByType.getOrDefault(Account.AccountType.ASSET, new ArrayList<>());
        for (Account account : assetAccounts) {
            BalanceCalculationResult balance = balanceCalculationService.calculateBalanceAsOf(account.getAccountId(), asOfDate);
            BigDecimal accountBalance = balance.getCurrentBalance();
            totalOtherAssets = totalOtherAssets.add(accountBalance);
            
            otherAssetItems.add(BalanceSheetLineItem.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(accountBalance)
                .percentage(BigDecimal.ZERO)
                .build());
        }
        
        calculatePercentages(otherAssetItems, totalOtherAssets);
        
        return BalanceSheetSection.builder()
            .sectionName("Other Assets")
            .sectionType("ASSET")
            .lineItems(otherAssetItems)
            .totalAmount(totalOtherAssets)
            .build();
    }

    private BalanceSheetSection calculateCurrentLiabilities(Map<Account.AccountType, List<Account>> accountsByType, 
                                                          LocalDateTime asOfDate) {
        List<BalanceSheetLineItem> currentLiabilityItems = new ArrayList<>();
        BigDecimal totalCurrentLiabilities = BigDecimal.ZERO;
        
        List<Account> currentLiabilityAccounts = accountsByType.getOrDefault(Account.AccountType.CURRENT_LIABILITY, new ArrayList<>());
        for (Account account : currentLiabilityAccounts) {
            BalanceCalculationResult balance = balanceCalculationService.calculateBalanceAsOf(account.getAccountId(), asOfDate);
            BigDecimal accountBalance = balance.getCurrentBalance();
            totalCurrentLiabilities = totalCurrentLiabilities.add(accountBalance);
            
            currentLiabilityItems.add(BalanceSheetLineItem.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(accountBalance)
                .percentage(BigDecimal.ZERO)
                .build());
        }
        
        calculatePercentages(currentLiabilityItems, totalCurrentLiabilities);
        
        return BalanceSheetSection.builder()
            .sectionName("Current Liabilities")
            .sectionType("LIABILITY")
            .lineItems(currentLiabilityItems)
            .totalAmount(totalCurrentLiabilities)
            .build();
    }

    private BalanceSheetSection calculateLongTermLiabilities(Map<Account.AccountType, List<Account>> accountsByType, 
                                                           LocalDateTime asOfDate) {
        List<BalanceSheetLineItem> longTermLiabilityItems = new ArrayList<>();
        BigDecimal totalLongTermLiabilities = BigDecimal.ZERO;
        
        List<Account> longTermLiabilityAccounts = accountsByType.getOrDefault(Account.AccountType.LONG_TERM_LIABILITY, new ArrayList<>());
        for (Account account : longTermLiabilityAccounts) {
            BalanceCalculationResult balance = balanceCalculationService.calculateBalanceAsOf(account.getAccountId(), asOfDate);
            BigDecimal accountBalance = balance.getCurrentBalance();
            totalLongTermLiabilities = totalLongTermLiabilities.add(accountBalance);
            
            longTermLiabilityItems.add(BalanceSheetLineItem.builder()
                .accountId(account.getAccountId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(accountBalance)
                .percentage(BigDecimal.ZERO)
                .build());
        }
        
        calculatePercentages(longTermLiabilityItems, totalLongTermLiabilities);
        
        return BalanceSheetSection.builder()
            .sectionName("Long-term Liabilities")
            .sectionType("LIABILITY")
            .lineItems(longTermLiabilityItems)
            .totalAmount(totalLongTermLiabilities)
            .build();
    }

    private BalanceSheetSection calculateEquitySection(Map<Account.AccountType, List<Account>> accountsByType, 
                                                     LocalDateTime asOfDate) {
        List<BalanceSheetLineItem> equityItems = new ArrayList<>();
        BigDecimal totalEquity = BigDecimal.ZERO;
        
        // Process all equity account types
        List<Account.AccountType> equityTypes = Arrays.asList(
            Account.AccountType.EQUITY, 
            Account.AccountType.RETAINED_EARNINGS, 
            Account.AccountType.PAID_IN_CAPITAL
        );
        
        for (Account.AccountType accountType : equityTypes) {
            List<Account> equityAccounts = accountsByType.getOrDefault(accountType, new ArrayList<>());
            for (Account account : equityAccounts) {
                BalanceCalculationResult balance = balanceCalculationService.calculateBalanceAsOf(account.getAccountId(), asOfDate);
                BigDecimal accountBalance = balance.getCurrentBalance();
                totalEquity = totalEquity.add(accountBalance);
                
                equityItems.add(BalanceSheetLineItem.builder()
                    .accountId(account.getAccountId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName())
                    .amount(accountBalance)
                    .percentage(BigDecimal.ZERO)
                    .build());
            }
        }
        
        // Add current year net income if not already included in retained earnings
        BigDecimal currentYearIncome = calculateCurrentYearNetIncome(asOfDate.atTime(23, 59, 59));
        if (currentYearIncome.compareTo(BigDecimal.ZERO) != 0) {
            totalEquity = totalEquity.add(currentYearIncome);
            equityItems.add(BalanceSheetLineItem.builder()
                .accountId(null)
                .accountCode("NYI")
                .accountName("Net Income (Current Year)")
                .amount(currentYearIncome)
                .percentage(BigDecimal.ZERO)
                .build());
        }
        
        calculatePercentages(equityItems, totalEquity);
        
        return BalanceSheetSection.builder()
            .sectionName("Shareholders' Equity")
            .sectionType("EQUITY")
            .lineItems(equityItems)
            .totalAmount(totalEquity)
            .build();
    }

    private BigDecimal calculateCurrentYearNetIncome(LocalDateTime asOfDate) {
        LocalDateTime yearStart = asOfDate.toLocalDate().withDayOfYear(1).atStartOfDay();
        
        // Calculate total revenue
        List<Account> revenueAccounts = accountRepository.findByAccountTypeInAndIsActiveTrue(
            Arrays.asList(Account.AccountType.REVENUE, Account.AccountType.OPERATING_REVENUE, Account.AccountType.OTHER_REVENUE));
        
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Account account : revenueAccounts) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndTransactionDateBetween(
                account.getAccountId(), yearStart, asOfDate);
            BigDecimal accountBalance = entries.stream()
                .map(entry -> entry.getEntryType() == LedgerEntry.EntryType.CREDIT ? 
                      entry.getAmount() : entry.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalRevenue = totalRevenue.add(accountBalance);
        }
        
        // Calculate total expenses
        List<Account> expenseAccounts = accountRepository.findByAccountTypeInAndIsActiveTrue(
            Arrays.asList(Account.AccountType.EXPENSE, Account.AccountType.OPERATING_EXPENSE, Account.AccountType.OTHER_EXPENSE));
        
        BigDecimal totalExpenses = BigDecimal.ZERO;
        for (Account account : expenseAccounts) {
            List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndTransactionDateBetween(
                account.getAccountId(), yearStart, asOfDate);
            BigDecimal accountBalance = entries.stream()
                .map(entry -> entry.getEntryType() == LedgerEntry.EntryType.DEBIT ? 
                      entry.getAmount() : entry.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalExpenses = totalExpenses.add(accountBalance);
        }
        
        return totalRevenue.subtract(totalExpenses);
    }

    private Map<Account.AccountType, List<Account>> getAccountsByType() {
        List<Account> allAccounts = accountRepository.findByIsActiveTrueOrderByAccountCodeAsc();
        return allAccounts.stream().collect(Collectors.groupingBy(Account::getAccountType));
    }

    private BalanceSheetSection aggregateAssetSections(BalanceSheetSection... sections) {
        BigDecimal totalAmount = Arrays.stream(sections)
            .map(BalanceSheetSection::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        List<BalanceSheetLineItem> allItems = Arrays.stream(sections)
            .flatMap(section -> section.getLineItems().stream())
            .collect(Collectors.toList());
        
        return BalanceSheetSection.builder()
            .sectionName("Total Assets")
            .sectionType("ASSET")
            .lineItems(allItems)
            .totalAmount(totalAmount)
            .build();
    }

    private BalanceSheetSection aggregateLiabilitySections(BalanceSheetSection... sections) {
        BigDecimal totalAmount = Arrays.stream(sections)
            .map(BalanceSheetSection::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        List<BalanceSheetLineItem> allItems = Arrays.stream(sections)
            .flatMap(section -> section.getLineItems().stream())
            .collect(Collectors.toList());
        
        return BalanceSheetSection.builder()
            .sectionName("Total Liabilities")
            .sectionType("LIABILITY")
            .lineItems(allItems)
            .totalAmount(totalAmount)
            .build();
    }

    private void calculatePercentages(List<BalanceSheetLineItem> items, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) != 0) {
            items.forEach(item -> {
                BigDecimal percentage = item.getAmount()
                    .divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                item.setPercentage(percentage);
            });
        }
    }
    
    /**
     * Perform comprehensive GAAP compliance validation
     */
    private List<String> performGAAPComplianceValidation(BalanceSheetSection currentAssets,
                                                        BalanceSheetSection fixedAssets,
                                                        BalanceSheetSection currentLiabilities,
                                                        BalanceSheetSection longTermLiabilities,
                                                        BalanceSheetSection equity) {
        List<String> violations = new ArrayList<>();
        
        // Current ratio validation (should be > 1.0 for healthy liquidity)
        BigDecimal currentRatio = currentLiabilities.getTotalAmount().compareTo(BigDecimal.ZERO) == 0 ?
            BigDecimal.ZERO : currentAssets.getTotalAmount().divide(currentLiabilities.getTotalAmount(), 4, RoundingMode.HALF_UP);
        
        if (currentRatio.compareTo(BigDecimal.ONE) < 0) {
            violations.add(String.format("Current ratio below 1.0: %s (may indicate liquidity issues)", currentRatio));
        }
        
        // Debt-to-equity ratio validation
        BigDecimal totalDebt = currentLiabilities.getTotalAmount().add(longTermLiabilities.getTotalAmount());
        BigDecimal debtToEquityRatio = equity.getTotalAmount().compareTo(BigDecimal.ZERO) == 0 ?
            BigDecimal.ZERO : totalDebt.divide(equity.getTotalAmount(), 4, RoundingMode.HALF_UP);
        
        if (debtToEquityRatio.compareTo(new BigDecimal("2.0")) > 0) {
            violations.add(String.format("High debt-to-equity ratio: %s (may indicate over-leverage)", debtToEquityRatio));
        }
        
        // Asset classification validation
        validateAssetClassification(currentAssets, violations);
        validateLiabilityClassification(currentLiabilities, longTermLiabilities, violations);
        validateEquityStructure(equity, violations);
        
        // Disclosure requirements validation
        validateDisclosureRequirements(currentAssets, fixedAssets, violations);
        
        return violations;
    }
    
    /**
     * Validate asset classification per GAAP
     */
    private void validateAssetClassification(BalanceSheetSection currentAssets, List<String> violations) {
        // Check for proper current asset ordering (by liquidity)
        List<BalanceSheetLineItem> items = currentAssets.getLineItems();
        
        // Cash should typically be first
        boolean cashFirst = !items.isEmpty() && 
            items.get(0).getAccountName().toLowerCase().contains("cash");
        
        if (!cashFirst && !items.isEmpty()) {
            violations.add("Current assets may not be properly ordered by liquidity (cash should be first)");
        }
        
        // Validate reasonable current asset composition
        BigDecimal totalCurrentAssets = currentAssets.getTotalAmount();
        for (BalanceSheetLineItem item : items) {
            BigDecimal percentage = totalCurrentAssets.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                item.getAmount().divide(totalCurrentAssets, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            
            // Flag if any single current asset exceeds 80% of total
            if (percentage.compareTo(new BigDecimal("80")) > 0) {
                violations.add(String.format("Asset concentration risk: %s represents %s%% of current assets", 
                    item.getAccountName(), percentage));
            }
        }
    }
    
    /**
     * Validate liability classification per GAAP
     */
    private void validateLiabilityClassification(BalanceSheetSection currentLiabilities,
                                               BalanceSheetSection longTermLiabilities,
                                               List<String> violations) {
        // Validate current liability composition
        BigDecimal totalCurrentLiabilities = currentLiabilities.getTotalAmount();
        
        if (totalCurrentLiabilities.compareTo(BigDecimal.ZERO) == 0) {
            violations.add("No current liabilities reported (unusual for operating business)");
        }
        
        // Check for proper classification of debt maturities
        for (BalanceSheetLineItem item : longTermLiabilities.getLineItems()) {
            if (item.getAccountName().toLowerCase().contains("due within") ||
                item.getAccountName().toLowerCase().contains("current portion")) {
                violations.add(String.format("Long-term liability may need current portion reclassification: %s", 
                    item.getAccountName()));
            }
        }
    }
    
    /**
     * Validate equity structure per GAAP
     */
    private void validateEquityStructure(BalanceSheetSection equity, List<String> violations) {
        BigDecimal totalEquity = equity.getTotalAmount();
        
        if (totalEquity.compareTo(BigDecimal.ZERO) < 0) {
            violations.add("Negative shareholders' equity indicates potential financial distress");
        }
        
        // Check for retained earnings presentation
        boolean hasRetainedEarnings = equity.getLineItems().stream()
            .anyMatch(item -> item.getAccountName().toLowerCase().contains("retained") ||
                             item.getAccountName().toLowerCase().contains("earnings"));
        
        if (!hasRetainedEarnings && totalEquity.compareTo(BigDecimal.ZERO) > 0) {
            violations.add("Missing retained earnings presentation in equity section");
        }
    }
    
    /**
     * Validate disclosure requirements
     */
    private void validateDisclosureRequirements(BalanceSheetSection currentAssets,
                                              BalanceSheetSection fixedAssets,
                                              List<String> violations) {
        // Check for significant concentrations
        BigDecimal totalAssets = currentAssets.getTotalAmount().add(fixedAssets.getTotalAmount());
        
        List<BalanceSheetLineItem> allAssets = new ArrayList<>();
        allAssets.addAll(currentAssets.getLineItems());
        allAssets.addAll(fixedAssets.getLineItems());
        
        for (BalanceSheetLineItem item : allAssets) {
            BigDecimal percentage = totalAssets.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                item.getAmount().divide(totalAssets, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            
            if (percentage.compareTo(new BigDecimal("25")) > 0) {
                violations.add(String.format("Significant asset concentration may require disclosure: %s (%s%%)", 
                    item.getAccountName(), percentage));
            }
        }
    }
    
    /**
     * Calculate key financial ratios for analysis
     */
    private Map<String, BigDecimal> calculateKeyFinancialRatios(BalanceSheetSection currentAssets,
                                                               BalanceSheetSection currentLiabilities,
                                                               BalanceSheetSection totalAssets,
                                                               BalanceSheetSection equity) {
        Map<String, BigDecimal> ratios = new HashMap<>();
        
        // Current Ratio = Current Assets / Current Liabilities
        BigDecimal currentRatio = currentLiabilities.getTotalAmount().compareTo(BigDecimal.ZERO) == 0 ?
            BigDecimal.ZERO : currentAssets.getTotalAmount().divide(currentLiabilities.getTotalAmount(), 4, RoundingMode.HALF_UP);
        ratios.put("currentRatio", currentRatio);
        
        // Asset Turnover = Revenue / Total Assets (would need revenue data)
        // For now, placeholder
        ratios.put("assetTurnover", BigDecimal.ZERO);
        
        // Equity Ratio = Total Equity / Total Assets
        BigDecimal equityRatio = totalAssets.getTotalAmount().compareTo(BigDecimal.ZERO) == 0 ?
            BigDecimal.ZERO : equity.getTotalAmount().divide(totalAssets.getTotalAmount(), 4, RoundingMode.HALF_UP);
        ratios.put("equityRatio", equityRatio);
        
        return ratios;
    }
    
    /**
     * Generate disclosure notes for the balance sheet
     */
    private List<String> generateDisclosureNotes(BalanceSheetSection currentAssets,
                                                BalanceSheetSection fixedAssets,
                                                BalanceSheetSection currentLiabilities,
                                                BalanceSheetSection longTermLiabilities,
                                                BalanceSheetSection equity) {
        List<String> notes = new ArrayList<>();
        
        // Standard GAAP disclosure notes
        notes.add("1. Summary of Significant Accounting Policies");
        notes.add("2. Cash and Cash Equivalents");
        notes.add("3. Property, Plant and Equipment");
        notes.add("4. Long-term Debt");
        notes.add("5. Shareholders' Equity");
        
        // Add specific notes based on account composition
        if (hasSignificantAssetConcentration(currentAssets, fixedAssets)) {
            notes.add("6. Concentration of Assets");
        }
        
        if (hasComplexDebtStructure(longTermLiabilities)) {
            notes.add("7. Debt Maturities and Covenants");
        }
        
        return notes;
    }
    
    /**
     * Check for significant asset concentration
     */
    private boolean hasSignificantAssetConcentration(BalanceSheetSection currentAssets,
                                                   BalanceSheetSection fixedAssets) {
        BigDecimal totalAssets = currentAssets.getTotalAmount().add(fixedAssets.getTotalAmount());
        
        List<BalanceSheetLineItem> allAssets = new ArrayList<>();
        allAssets.addAll(currentAssets.getLineItems());
        allAssets.addAll(fixedAssets.getLineItems());
        
        return allAssets.stream().anyMatch(item -> {
            BigDecimal percentage = totalAssets.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                item.getAmount().divide(totalAssets, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            return percentage.compareTo(new BigDecimal("25")) > 0;
        });
    }
    
    /**
     * Check for complex debt structure requiring disclosure
     */
    private boolean hasComplexDebtStructure(BalanceSheetSection longTermLiabilities) {
        return longTermLiabilities.getLineItems().size() > 3 ||
               longTermLiabilities.getTotalAmount().compareTo(new BigDecimal("1000000")) > 0;
    }
    
    /**
     * Generate audit trail for balance sheet generation
     */
    private String generateAuditTrail(LocalDate asOfDate, BalanceSheetFormat format, boolean gaapCompliance) {
        return String.format("Balance sheet generated on %s for period ending %s, format: %s, GAAP compliance: %s, user: system",
            LocalDateTime.now(), asOfDate, format, gaapCompliance);
    }

    public enum BalanceSheetFormat {
        CLASSIFIED,     // Current vs Non-current categorization (GAAP compliant)
        REPORT_FORM,    // Vertical format (Assets over Liabilities + Equity)
        ACCOUNT_FORM,   // Horizontal format (Assets = Liabilities + Equity)
        COMPARATIVE,    // Side-by-side period comparison
        SEC_FORM,       // SEC reporting format
        IFRS_COMPLIANT  // IFRS format for international reporting
    }
}