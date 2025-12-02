package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.Account;
import com.waqiti.ledger.domain.BankReconciliation;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import com.waqiti.ledger.repository.BankReconciliationRepository;
import com.waqiti.ledger.exception.ReconciliationException;
import com.waqiti.ledger.service.BankReconciliationService.MatchType;
import com.waqiti.ledger.service.BankReconciliationService.OutstandingItemType;
import com.waqiti.ledger.service.BankReconciliationService.OutstandingItemStatus;
import com.waqiti.ledger.service.BankReconciliationService.RecommendationPriority;
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
 * Bank Reconciliation Service
 * 
 * Provides comprehensive bank reconciliation functionality including:
 * - Automated matching of bank statements with ledger entries
 * - Outstanding items identification (checks, deposits in transit)
 * - Reconciliation report generation with variance analysis
 * - Support for multiple bank accounts and currencies
 * - Historical reconciliation tracking and audit trail
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BankReconciliationService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BalanceCalculationService balanceCalculationService;
    private final BankReconciliationRepository bankReconciliationRepository;

    /**
     * Performs comprehensive bank reconciliation with enhanced validation and analysis
     */
    @Transactional
    public BankReconciliationResponse performBankReconciliation(BankReconciliationRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("Performing bank reconciliation for account: {} as of {}", 
                request.getBankAccountId(), request.getReconciliationDate());
            
            // Enhanced request validation
            validateReconciliationRequest(request);
            
            // Additional business rule validations
            performBusinessRuleValidation(request);
            
            // Get bank account details
            Account bankAccount = accountRepository.findById(request.getBankAccountId())
                .orElseThrow(() -> new ReconciliationException("Bank account not found: " + request.getBankAccountId()));
            
            // Calculate book balance as of reconciliation date
            BigDecimal bookBalance = calculateBookBalance(request.getBankAccountId(), request.getReconciliationDate());
            
            // Get all ledger entries for the reconciliation period
            List<LedgerEntry> ledgerEntries = getLedgerEntriesForPeriod(
                request.getBankAccountId(), request.getStartDate(), request.getReconciliationDate());
            
            // Match bank statement items with ledger entries
            ReconciliationMatching matching = performStatementMatching(
                request.getBankStatementItems(), ledgerEntries);
            
            // Identify outstanding items
            OutstandingItems outstandingItems = identifyOutstandingItems(
                matching, request.getReconciliationDate());
            
            // Calculate reconciled balance
            BigDecimal reconciledBalance = calculateReconciledBalance(
                request.getEndingBankBalance(), outstandingItems);
            
            // Generate variance analysis
            ReconciliationVariance variance = analyzeVariances(
                bookBalance, reconciledBalance, outstandingItems);
            
            // Create reconciliation summary
            ReconciliationSummary summary = createReconciliationSummary(
                bankAccount, bookBalance, request.getEndingBankBalance(), 
                reconciledBalance, variance, outstandingItems);
            
            // Generate recommendations
            List<ReconciliationRecommendation> recommendations = generateRecommendations(
                variance, outstandingItems, matching);
            
            // Calculate processing time
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Generate audit trail
            generateReconciliationAuditTrail(request, summary, processingTime);
            
            // PRODUCTION: Save reconciliation to database for history tracking
            saveReconciliationToHistory(request, summary, variance, outstandingItems, matching, processingTime);
            
            return BankReconciliationResponse.builder()
                .reconciliationId(UUID.randomUUID())
                .bankAccountId(request.getBankAccountId())
                .bankAccountName(bankAccount.getAccountName())
                .reconciliationDate(request.getReconciliationDate())
                .startDate(request.getStartDate())
                .currency(bankAccount.getCurrency())
                // Balances
                .bookBalance(bookBalance)
                .bankBalance(request.getEndingBankBalance())
                .reconciledBalance(reconciledBalance)
                .variance(variance.getTotalVariance())
                // Analysis
                .summary(summary)
                .outstandingItems(outstandingItems)
                .matching(matching)
                .varianceAnalysis(variance)
                .recommendations(recommendations)
                // Status and metrics
                .reconciled(variance.getTotalVariance().abs().compareTo(new BigDecimal("0.01")) <= 0)
                .reconciledBy(request.getReconciledBy())
                .reconciledAt(LocalDateTime.now())
                .processingTimeMs(processingTime)
                .build();
                
        } catch (ReconciliationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to perform bank reconciliation", e);
            throw new ReconciliationException("Failed to perform bank reconciliation", e);
        }
    }

    /**
     * Gets reconciliation history for a bank account - PRODUCTION IMPLEMENTATION
     */
    @Cacheable(value = "reconciliationHistory", key = "#bankAccountId + '_' + #startDate + '_' + #endDate")
    public ReconciliationHistoryResponse getReconciliationHistory(UUID bankAccountId, 
                                                                LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Getting reconciliation history for account: {} from {} to {}", 
                bankAccountId, startDate, endDate);
            
            // PRODUCTION: Query actual reconciliation history from database
            List<BankReconciliation> reconciliations = bankReconciliationRepository
                .findByBankAccountIdAndReconciliationDateBetweenOrderByReconciliationDateDesc(
                    bankAccountId, startDate, endDate);
            
            // Convert to response DTOs
            List<ReconciliationHistoryItem> historyItems = reconciliations.stream()
                .map(this::convertToHistoryItem)
                .collect(Collectors.toList());
            
            // Calculate statistics
            BigDecimal totalVariance = reconciliations.stream()
                .map(BankReconciliation::getVariance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal averageVariance = reconciliations.isEmpty() ? BigDecimal.ZERO :
                totalVariance.divide(BigDecimal.valueOf(reconciliations.size()), 2, RoundingMode.HALF_UP);
            
            LocalDate lastReconciliationDate = reconciliations.isEmpty() ? null :
                reconciliations.get(0).getReconciliationDate();
            
            // Get statistics from database
            Object[] stats = bankReconciliationRepository.getReconciliationStatistics(
                bankAccountId, startDate, endDate);
            
            return ReconciliationHistoryResponse.builder()
                .bankAccountId(bankAccountId)
                .startDate(startDate)
                .endDate(endDate)
                .reconciliations(historyItems)
                .totalReconciliations(reconciliations.size())
                .averageVariance(averageVariance)
                .lastReconciliationDate(lastReconciliationDate)
                .successfulReconciliations(getSuccessfulCount(reconciliations))
                .failedReconciliations(getFailedCount(reconciliations))
                .averageMatchingRate(getAverageMatchingRate(reconciliations))
                .totalOutstandingAmount(getTotalOutstandingAmount(reconciliations))
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get reconciliation history for account: {}", bankAccountId, e);
            throw new ReconciliationException("Failed to get reconciliation history", e);
        }
    }

    /**
     * Validates outstanding items from previous reconciliation
     */
    public OutstandingItemsValidationResponse validateOutstandingItems(UUID bankAccountId, 
                                                                     List<OutstandingItem> previousOutstandingItems) {
        try {
            log.debug("Validating {} outstanding items for account: {}", 
                previousOutstandingItems.size(), bankAccountId);
            
            List<OutstandingItemValidation> validations = new ArrayList<>();
            
            for (OutstandingItem item : previousOutstandingItems) {
                OutstandingItemValidation validation = validateOutstandingItem(bankAccountId, item);
                validations.add(validation);
            }
            
            // Calculate summary statistics
            long clearedItems = validations.stream()
                .filter(v -> v.getStatus() == OutstandingItemStatus.CLEARED)
                .count();
            
            long stalledItems = validations.stream()
                .filter(v -> v.getStatus() == OutstandingItemStatus.STALE)
                .count();
            
            BigDecimal totalOutstandingAmount = validations.stream()
                .filter(v -> v.getStatus() == OutstandingItemStatus.OUTSTANDING)
                .map(v -> v.getItem().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return OutstandingItemsValidationResponse.builder()
                .bankAccountId(bankAccountId)
                .validations(validations)
                .totalItems(previousOutstandingItems.size())
                .clearedItems((int) clearedItems)
                .stalledItems((int) stalledItems)
                .stillOutstandingItems(validations.size() - (int) clearedItems - (int) stalledItems)
                .totalOutstandingAmount(totalOutstandingAmount)
                .validatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to validate outstanding items", e);
            throw new ReconciliationException("Failed to validate outstanding items", e);
        }
    }

    /**
     * Generates bank reconciliation report
     */
    public BankReconciliationReportResponse generateReconciliationReport(UUID reconciliationId) {
        try {
            log.debug("Generating reconciliation report for: {}", reconciliationId);
            
            // This would typically retrieve reconciliation data and format it as a report
            // For now, returning placeholder structure
            return BankReconciliationReportResponse.builder()
                .reconciliationId(reconciliationId)
                .reportFormat("PDF")
                .reportContent(new byte[0]) // Placeholder - would contain actual PDF content
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate reconciliation report", e);
            throw new ReconciliationException("Failed to generate reconciliation report", e);
        }
    }

    /**
     * Enhanced business rule validation for reconciliation
     */
    private void performBusinessRuleValidation(BankReconciliationRequest request) {
        // Validate reconciliation frequency (daily, monthly, etc.)
        validateReconciliationFrequency(request.getBankAccountId(), request.getReconciliationDate());
        
        // Validate bank statement completeness
        validateBankStatementCompleteness(request);
        
        // Validate cutoff times and processing delays
        validateCutoffTimes(request);
        
        // Check for duplicate reconciliation attempts
        checkForDuplicateReconciliation(request);
    }
    
    /**
     * Generate comprehensive audit trail for reconciliation
     */
    private void generateReconciliationAuditTrail(BankReconciliationRequest request, 
                                                ReconciliationSummary summary, 
                                                long processingTime) {
        log.info("Bank Reconciliation Audit Trail:");
        log.info("Account: {} ({})", request.getBankAccountId(), summary.getAccountName());
        log.info("Period: {} to {}", request.getStartDate(), request.getReconciliationDate());
        log.info("Bank Balance: {}", summary.getBankBalance());
        log.info("Book Balance: {}", summary.getBookBalance());
        log.info("Variance: {}", summary.getVariance());
        log.info("Reconciled: {}", summary.isReconciled());
        log.info("Outstanding Items: {}", summary.getTotalOutstandingItems());
        log.info("Processing Time: {}ms", processingTime);
        log.info("Reconciled By: {}", request.getReconciledBy());
        
        // In a production environment, this would also:
        // - Store detailed audit records in database
        // - Send notifications for failed reconciliations
        // - Update reconciliation status tracking
        // - Generate compliance reports
    }
    
    /**
     * Validate reconciliation frequency policies
     */
    private void validateReconciliationFrequency(UUID bankAccountId, LocalDate reconciliationDate) {
        // Implementation would check business rules for reconciliation frequency
        // e.g., high-volume accounts must be reconciled daily
        log.debug("Validating reconciliation frequency for account: {}", bankAccountId);
    }
    
    /**
     * Validate bank statement completeness
     */
    private void validateBankStatementCompleteness(BankReconciliationRequest request) {
        // Check if bank statement covers the full period
        if (request.getBankStatementItems().isEmpty()) {
            throw new ReconciliationException("Bank statement cannot be empty");
        }
        
        // Validate statement item dates fall within reconciliation period
        boolean hasItemsOutsidePeriod = request.getBankStatementItems().stream()
            .anyMatch(item -> item.getTransactionDate().isBefore(request.getStartDate()) ||
                             item.getTransactionDate().isAfter(request.getReconciliationDate()));
        
        if (hasItemsOutsidePeriod) {
            log.warn("Bank statement contains items outside reconciliation period");
        }
    }
    
    /**
     * Validate cutoff times and processing delays
     */
    private void validateCutoffTimes(BankReconciliationRequest request) {
        // Implementation would validate cutoff times for deposits, withdrawals, etc.
        log.debug("Validating cutoff times for reconciliation");
    }
    
    /**
     * Check for duplicate reconciliation attempts
     */
    private void checkForDuplicateReconciliation(BankReconciliationRequest request) {
        // Implementation would check if reconciliation already exists for this period
        log.debug("Checking for duplicate reconciliation attempts");
    }
    
    // Private helper methods

    private void validateReconciliationRequest(BankReconciliationRequest request) {
        if (request.getBankAccountId() == null) {
            throw new ReconciliationException("Bank account ID is required");
        }
        if (request.getReconciliationDate() == null) {
            throw new ReconciliationException("Reconciliation date is required");
        }
        if (request.getEndingBankBalance() == null) {
            throw new ReconciliationException("Ending bank balance is required");
        }
        if (request.getBankStatementItems() == null || request.getBankStatementItems().isEmpty()) {
            throw new ReconciliationException("Bank statement items are required");
        }
        if (request.getReconciledBy() == null || request.getReconciledBy().trim().isEmpty()) {
            throw new ReconciliationException("Reconciled by user is required for audit trail");
        }
        
        // Enhanced validations
        if (request.getReconciliationDate().isAfter(LocalDate.now())) {
            throw new ReconciliationException("Cannot reconcile future dates");
        }
        
        if (request.getStartDate() != null && request.getStartDate().isAfter(request.getReconciliationDate())) {
            throw new ReconciliationException("Start date cannot be after reconciliation date");
        }
        
        // Validate bank statement items
        validateBankStatementItems(request.getBankStatementItems());
    }
    
    /**
     * Enhanced validation for bank statement items
     */
    private void validateBankStatementItems(List<BankStatementItem> items) {
        for (int i = 0; i < items.size(); i++) {
            BankStatementItem item = items.get(i);
            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                throw new ReconciliationException(String.format("Invalid amount in bank statement item %d", i + 1));
            }
            if (item.getTransactionDate() == null) {
                throw new ReconciliationException(String.format("Missing transaction date in bank statement item %d", i + 1));
            }
            if (item.getDescription() == null || item.getDescription().trim().isEmpty()) {
                throw new ReconciliationException(String.format("Missing description in bank statement item %d", i + 1));
            }
        }
    }

    private BigDecimal calculateBookBalance(UUID bankAccountId, LocalDate asOfDate) {
        BalanceCalculationResult balance = balanceCalculationService.calculateBalanceAsOf(
            bankAccountId, asOfDate.atTime(23, 59, 59));
        return balance.getCurrentBalance();
    }

    private List<LedgerEntry> getLedgerEntriesForPeriod(UUID bankAccountId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime fromDateTime = startDate.atStartOfDay();
        LocalDateTime toDateTime = endDate.atTime(23, 59, 59);
        
        return ledgerEntryRepository.findByAccountIdAndTransactionDateBetweenOrderByTransactionDateAsc(
            bankAccountId, fromDateTime, toDateTime);
    }

    private ReconciliationMatching performStatementMatching(List<BankStatementItem> statementItems, 
                                                          List<LedgerEntry> ledgerEntries) {
        List<ReconciliationMatch> exactMatches = new ArrayList<>();
        List<ReconciliationMatch> fuzzyMatches = new ArrayList<>();
        List<BankStatementItem> unmatchedStatementItems = new ArrayList<>();
        List<LedgerEntry> unmatchedLedgerEntries = new ArrayList<>(ledgerEntries);
        
        // First pass: Exact matches (amount and date)
        for (BankStatementItem statementItem : statementItems) {
            Optional<LedgerEntry> exactMatch = findExactMatch(statementItem, unmatchedLedgerEntries);
            
            if (exactMatch.isPresent()) {
                exactMatches.add(ReconciliationMatch.builder()
                    .matchId(UUID.randomUUID())
                    .statementItem(statementItem)
                    .ledgerEntry(exactMatch.get())
                    .matchType(MatchType.EXACT)
                    .matchConfidence(100)
                    .varianceAmount(BigDecimal.ZERO)
                    .build());
                    
                unmatchedLedgerEntries.remove(exactMatch.get());
            } else {
                unmatchedStatementItems.add(statementItem);
            }
        }
        
        // Second pass: Fuzzy matches (amount matches, date within tolerance)
        List<BankStatementItem> stillUnmatched = new ArrayList<>();
        for (BankStatementItem statementItem : unmatchedStatementItems) {
            Optional<LedgerEntry> fuzzyMatch = findFuzzyMatch(statementItem, unmatchedLedgerEntries);
            
            if (fuzzyMatch.isPresent()) {
                fuzzyMatches.add(ReconciliationMatch.builder()
                    .matchId(UUID.randomUUID())
                    .statementItem(statementItem)
                    .ledgerEntry(fuzzyMatch.get())
                    .matchType(MatchType.FUZZY)
                    .matchConfidence(85)
                    .varianceAmount(BigDecimal.ZERO)
                    .build());
                    
                unmatchedLedgerEntries.remove(fuzzyMatch.get());
            } else {
                stillUnmatched.add(statementItem);
            }
        }
        
        // Calculate matching statistics
        int totalItems = statementItems.size();
        int matchedItems = exactMatches.size() + fuzzyMatches.size();
        BigDecimal matchingRate = totalItems > 0 ? 
            new BigDecimal(matchedItems).divide(new BigDecimal(totalItems), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
        
        return ReconciliationMatching.builder()
            .exactMatches(exactMatches)
            .fuzzyMatches(fuzzyMatches)
            .unmatchedStatementItems(stillUnmatched)
            .unmatchedLedgerEntries(unmatchedLedgerEntries)
            .totalMatches(matchedItems)
            .matchingRate(matchingRate)
            .build();
    }

    private Optional<LedgerEntry> findExactMatch(BankStatementItem statementItem, List<LedgerEntry> ledgerEntries) {
        return ledgerEntries.stream()
            .filter(entry -> entry.getAmount().compareTo(statementItem.getAmount()) == 0)
            .filter(entry -> entry.getTransactionDate().toLocalDate().equals(statementItem.getTransactionDate()))
            .findFirst();
    }

    private Optional<LedgerEntry> findFuzzyMatch(BankStatementItem statementItem, List<LedgerEntry> ledgerEntries) {
        // Match on amount, but allow date variance of +/- 3 days
        return ledgerEntries.stream()
            .filter(entry -> entry.getAmount().compareTo(statementItem.getAmount()) == 0)
            .filter(entry -> Math.abs(entry.getTransactionDate().toLocalDate().toEpochDay() - 
                statementItem.getTransactionDate().toEpochDay()) <= 3)
            .findFirst();
    }

    private OutstandingItems identifyOutstandingItems(ReconciliationMatching matching, LocalDate reconciliationDate) {
        List<OutstandingItem> outstandingChecks = new ArrayList<>();
        List<OutstandingItem> depositsInTransit = new ArrayList<>();
        List<OutstandingItem> otherItems = new ArrayList<>();
        
        // Unmatched ledger entries become outstanding items
        for (LedgerEntry entry : matching.getUnmatchedLedgerEntries()) {
            OutstandingItemType type = determineOutstandingItemType(entry);
            
            OutstandingItem item = OutstandingItem.builder()
                .itemId(UUID.randomUUID())
                .ledgerEntryId(entry.getLedgerEntryId())
                .type(type)
                .description(entry.getDescription())
                .amount(entry.getAmount())
                .transactionDate(entry.getTransactionDate().toLocalDate())
                .ageInDays(calculateAgeInDays(entry.getTransactionDate().toLocalDate(), reconciliationDate))
                .status(OutstandingItemStatus.OUTSTANDING)
                .build();
                
            switch (type) {
                case OUTSTANDING_CHECK:
                    outstandingChecks.add(item);
                    break;
                case DEPOSIT_IN_TRANSIT:
                    depositsInTransit.add(item);
                    break;
                default:
                    otherItems.add(item);
                    break;
            }
        }
        
        // Calculate totals
        BigDecimal totalOutstandingChecks = outstandingChecks.stream()
            .map(OutstandingItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal totalDepositsInTransit = depositsInTransit.stream()
            .map(OutstandingItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal totalOtherItems = otherItems.stream()
            .map(OutstandingItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return OutstandingItems.builder()
            .outstandingChecks(outstandingChecks)
            .depositsInTransit(depositsInTransit)
            .otherItems(otherItems)
            .totalOutstandingChecks(totalOutstandingChecks)
            .totalDepositsInTransit(totalDepositsInTransit)
            .totalOtherItems(totalOtherItems)
            .totalOutstandingAmount(totalOutstandingChecks.add(totalDepositsInTransit).add(totalOtherItems))
            .build();
    }

    private OutstandingItemType determineOutstandingItemType(LedgerEntry entry) {
        // Logic to determine if it's a check, deposit, or other item
        if (entry.getEntryType() == LedgerEntry.EntryType.CREDIT && entry.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            return OutstandingItemType.OUTSTANDING_CHECK;
        } else if (entry.getEntryType() == LedgerEntry.EntryType.DEBIT && entry.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            return OutstandingItemType.DEPOSIT_IN_TRANSIT;
        } else {
            return OutstandingItemType.OTHER;
        }
    }

    private int calculateAgeInDays(LocalDate transactionDate, LocalDate reconciliationDate) {
        return (int) (reconciliationDate.toEpochDay() - transactionDate.toEpochDay());
    }

    private BigDecimal calculateReconciledBalance(BigDecimal bankBalance, OutstandingItems outstandingItems) {
        // Adjusted Bank Balance = Bank Balance + Deposits in Transit - Outstanding Checks - Other Adjustments
        return bankBalance
            .add(outstandingItems.getTotalDepositsInTransit())
            .subtract(outstandingItems.getTotalOutstandingChecks())
            .subtract(outstandingItems.getTotalOtherItems());
    }

    private ReconciliationVariance analyzeVariances(BigDecimal bookBalance, BigDecimal reconciledBalance, 
                                                  OutstandingItems outstandingItems) {
        BigDecimal totalVariance = bookBalance.subtract(reconciledBalance);
        boolean withinTolerance = totalVariance.abs().compareTo(new BigDecimal("0.01")) <= 0;
        
        // Enhanced variance categorization
        String varianceCategory = categorizeVariance(totalVariance, outstandingItems);
        String recommendedAction = getVarianceRecommendedAction(totalVariance, varianceCategory);
        
        // Calculate component variances
        BigDecimal timingDifferenceVariance = calculateTimingDifferenceVariance(outstandingItems);
        BigDecimal unexplainedVariance = totalVariance.subtract(timingDifferenceVariance);
        
        BigDecimal variancePercentage = reconciledBalance.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
            totalVariance.divide(reconciledBalance, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        
        return ReconciliationVariance.builder()
            .bookBalance(bookBalance)
            .reconciledBalance(reconciledBalance)
            .totalVariance(totalVariance)
            .withinTolerance(withinTolerance)
            .variancePercentage(variancePercentage)
            .explanation(generateVarianceExplanation(totalVariance, outstandingItems))
            .varianceCategory(varianceCategory)
            .recommendedAction(recommendedAction)
            .timingDifferenceVariance(timingDifferenceVariance)
            .unexplainedVariance(unexplainedVariance)
            .build();
    }
    
    /**
     * Categorize the type of variance for better analysis
     */
    private String categorizeVariance(BigDecimal variance, OutstandingItems outstandingItems) {
        BigDecimal absVariance = variance.abs();
        
        if (absVariance.compareTo(new BigDecimal("0.01")) <= 0) {
            return "IMMATERIAL";
        } else if (absVariance.compareTo(new BigDecimal("100.00")) <= 0) {
            return "MINOR";
        } else if (absVariance.compareTo(new BigDecimal("1000.00")) <= 0) {
            return "MODERATE";
        } else {
            return "SIGNIFICANT";
        }
    }
    
    /**
     * Get recommended action based on variance analysis
     */
    private String getVarianceRecommendedAction(BigDecimal variance, String category) {
        switch (category) {
            case "IMMATERIAL":
                return "No action required - variance within acceptable tolerance";
            case "MINOR":
                return "Review outstanding items and verify bank statement completeness";
            case "MODERATE":
                return "Investigate variance - review transactions and contact bank if necessary";
            case "SIGNIFICANT":
                return "Immediate investigation required - escalate to senior management";
            default:
                return "Review and analyze variance";
        }
    }
    
    /**
     * Calculate variance attributed to timing differences
     */
    private BigDecimal calculateTimingDifferenceVariance(OutstandingItems outstandingItems) {
        // Timing differences are typically represented by outstanding items
        return outstandingItems.getTotalOutstandingAmount();
    }

    private String generateVarianceExplanation(BigDecimal variance, OutstandingItems outstandingItems) {
        if (variance.abs().compareTo(new BigDecimal("0.01")) <= 0) {
            return "Books and bank are reconciled within acceptable tolerance.";
        } else if (variance.compareTo(BigDecimal.ZERO) > 0) {
            return String.format("Book balance exceeds reconciled balance by %s. " +
                "Review outstanding items and bank statement for missing transactions.", variance);
        } else {
            return String.format("Reconciled balance exceeds book balance by %s. " +
                "Review for duplicate entries or unrecorded bank charges.", variance.abs());
        }
    }

    private ReconciliationSummary createReconciliationSummary(Account bankAccount, BigDecimal bookBalance,
                                                            BigDecimal bankBalance, BigDecimal reconciledBalance,
                                                            ReconciliationVariance variance, OutstandingItems outstandingItems) {
        return ReconciliationSummary.builder()
            .accountName(bankAccount.getAccountName())
            .accountNumber(bankAccount.getAccountCode())
            .currency(bankAccount.getCurrency())
            .bookBalance(bookBalance)
            .bankBalance(bankBalance)
            .reconciledBalance(reconciledBalance)
            .variance(variance.getTotalVariance())
            .reconciled(variance.isWithinTolerance())
            .totalOutstandingItems(outstandingItems.getOutstandingChecks().size() + 
                outstandingItems.getDepositsInTransit().size() + outstandingItems.getOtherItems().size())
            .totalOutstandingAmount(outstandingItems.getTotalOutstandingAmount())
            .build();
    }

    private List<ReconciliationRecommendation> generateRecommendations(ReconciliationVariance variance,
                                                                     OutstandingItems outstandingItems,
                                                                     ReconciliationMatching matching) {
        List<ReconciliationRecommendation> recommendations = new ArrayList<>();
        
        // Add recommendations based on analysis
        if (!variance.isWithinTolerance()) {
            recommendations.add(ReconciliationRecommendation.builder()
                .priority(RecommendationPriority.HIGH)
                .category("VARIANCE")
                .title("Investigate Reconciliation Variance")
                .description(String.format("Total variance of %s requires investigation and resolution", 
                    variance.getTotalVariance()))
                .action("Review unmatched transactions and outstanding items")
                .build());
        }
        
        if (!matching.getUnmatchedStatementItems().isEmpty()) {
            recommendations.add(ReconciliationRecommendation.builder()
                .priority(RecommendationPriority.MEDIUM)
                .category("UNMATCHED")
                .title("Review Unmatched Bank Statement Items")
                .description(String.format("%d bank statement items could not be matched to ledger entries", 
                    matching.getUnmatchedStatementItems().size()))
                .action("Review and post missing entries or investigate discrepancies")
                .build());
        }
        
        // Check for stale outstanding items
        long staleItems = outstandingItems.getOutstandingChecks().stream()
            .filter(item -> item.getAgeInDays() > 90)
            .count();
            
        if (staleItems > 0) {
            recommendations.add(ReconciliationRecommendation.builder()
                .priority(RecommendationPriority.MEDIUM)
                .category("STALE_ITEMS")
                .title("Review Stale Outstanding Items")
                .description(String.format("%d outstanding items are over 90 days old", staleItems))
                .action("Consider writing off or follow up on stale outstanding checks")
                .build());
        }
        
        return recommendations;
    }

    private OutstandingItemValidation validateOutstandingItem(UUID bankAccountId, OutstandingItem item) {
        OutstandingItemStatus status = OutstandingItemStatus.OUTSTANDING;
        StringBuilder validationNotes = new StringBuilder();
        
        try {
            // Check if item has cleared in subsequent periods
            LocalDate checkDate = item.getTransactionDate().plusDays(1);
            LocalDate currentDate = LocalDate.now();
            
            if (checkDate.isBefore(currentDate)) {
                // Look for matching transactions after the item date
                List<LedgerEntry> subsequentEntries = ledgerEntryRepository.findByAccountIdAndTransactionDateGreaterThanEqualOrderByTransactionDateDesc(
                    bankAccountId, checkDate.atStartOfDay());
                
                boolean cleared = subsequentEntries.stream()
                    .anyMatch(entry -> entry.getAmount().compareTo(item.getAmount()) == 0);
                
                if (cleared) {
                    status = OutstandingItemStatus.CLEARED;
                    validationNotes.append("Item cleared in subsequent period. ");
                }
            }
            
            // Check age of item
            long ageInDays = item.getAgeInDays();
            if (ageInDays > 90 && status == OutstandingItemStatus.OUTSTANDING) {
                status = OutstandingItemStatus.STALE;
                validationNotes.append(String.format("Item is %d days old and may be stale. ", ageInDays));
            }
            
            // Additional business rule checks
            if (item.getAmount().compareTo(new BigDecimal("10000.00")) > 0) {
                validationNotes.append("High-value item requires additional review. ");
            }
            
            validationNotes.append("Validation completed successfully.");
            
        } catch (Exception e) {
            log.error("Error validating outstanding item: {}", item.getItemId(), e);
            validationNotes.append("Validation error: ").append(e.getMessage());
        }
        
        return OutstandingItemValidation.builder()
            .item(item)
            .status(status)
            .validationNotes(validationNotes.toString())
            .validatedAt(LocalDateTime.now())
            .build();
    }

    // Enums for reconciliation types
    public enum MatchType {
        EXACT,      // Perfect match on amount and date
        FUZZY,      // Amount matches, date within tolerance
        MANUAL      // Manually matched by user
    }

    public enum OutstandingItemType {
        OUTSTANDING_CHECK,
        DEPOSIT_IN_TRANSIT,
        BANK_ERROR,
        BOOK_ERROR,
        OTHER
    }

    public enum OutstandingItemStatus {
        OUTSTANDING,
        CLEARED,
        STALE,
        WRITTEN_OFF
    }

    public enum RecommendationPriority {
        HIGH,
        MEDIUM,
        LOW
    }
    
    // PRODUCTION: Helper methods for reconciliation history
    
    /**
     * Save reconciliation to database for history and audit trail
     */
    private void saveReconciliationToHistory(BankReconciliationRequest request,
                                            ReconciliationSummary summary,
                                            ReconciliationVariance variance,
                                            OutstandingItems outstandingItems,
                                            ReconciliationMatching matching,
                                            long processingTime) {
        try {
            BankReconciliation reconciliation = BankReconciliation.builder()
                .reconciliationId(UUID.randomUUID())
                .bankAccountId(request.getBankAccountId())
                .bankAccountName(summary.getAccountName())
                .reconciliationDate(request.getReconciliationDate())
                .periodStartDate(request.getStartDate())
                .periodEndDate(request.getReconciliationDate())
                .currency(summary.getCurrency())
                // Balances
                .bookBalance(summary.getBookBalance())
                .bankBalance(summary.getBankBalance())
                .reconciledBalance(summary.getReconciledBalance())
                .variance(variance.getTotalVariance())
                // Outstanding items
                .outstandingChecksCount(outstandingItems.getOutstandingChecks().size())
                .outstandingChecksAmount(outstandingItems.getTotalOutstandingChecks())
                .depositsInTransitCount(outstandingItems.getDepositsInTransit().size())
                .depositsInTransitAmount(outstandingItems.getTotalDepositsInTransit())
                .otherItemsCount(outstandingItems.getOtherItems().size())
                .otherItemsAmount(outstandingItems.getTotalOtherItems())
                // Matching statistics
                .totalStatementItems(request.getBankStatementItems().size())
                .matchedItems(matching.getTotalMatches())
                .unmatchedItems(matching.getUnmatchedStatementItems().size())
                .matchingRate(matching.getMatchingRate())
                // Status
                .status(BankReconciliation.ReconciliationStatus.COMPLETED)
                .reconciled(variance.isWithinTolerance())
                .reconciledBy(request.getReconciledBy())
                .reconciledAt(LocalDateTime.now())
                .notes(request.getNotes())
                .varianceExplanation(variance.getExplanation())
                // Metrics
                .processingTimeMs(processingTime)
                .autoMatchedPercentage(matching.getMatchingRate())
                .build();
            
            bankReconciliationRepository.save(reconciliation);
            log.info("Saved reconciliation history: {} for account: {}", 
                reconciliation.getReconciliationId(), request.getBankAccountId());
                
        } catch (Exception e) {
            log.error("Failed to save reconciliation history", e);
            // Don't fail the reconciliation if history save fails
        }
    }
    
    /**
     * Convert reconciliation entity to history item DTO
     */
    private ReconciliationHistoryItem convertToHistoryItem(BankReconciliation reconciliation) {
        return ReconciliationHistoryItem.builder()
            .reconciliationId(reconciliation.getReconciliationId())
            .reconciliationDate(reconciliation.getReconciliationDate())
            .bookBalance(reconciliation.getBookBalance())
            .bankBalance(reconciliation.getBankBalance())
            .variance(reconciliation.getVariance())
            .reconciled(reconciliation.getReconciled())
            .reconciledBy(reconciliation.getReconciledBy())
            .reconciledAt(reconciliation.getReconciledAt())
            .outstandingItemsCount(
                reconciliation.getOutstandingChecksCount() +
                reconciliation.getDepositsInTransitCount() +
                reconciliation.getOtherItemsCount()
            )
            .matchingRate(reconciliation.getMatchingRate())
            .status(reconciliation.getStatus().name())
            .build();
    }
    
    /**
     * Get count of successful reconciliations
     */
    private int getSuccessfulCount(List<BankReconciliation> reconciliations) {
        return (int) reconciliations.stream()
            .filter(BankReconciliation::getReconciled)
            .count();
    }
    
    /**
     * Get count of failed reconciliations
     */
    private int getFailedCount(List<BankReconciliation> reconciliations) {
        return (int) reconciliations.stream()
            .filter(r -> !r.getReconciled())
            .count();
    }
    
    /**
     * Calculate average matching rate
     */
    private BigDecimal getAverageMatchingRate(List<BankReconciliation> reconciliations) {
        if (reconciliations.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalRate = reconciliations.stream()
            .map(BankReconciliation::getMatchingRate)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        return totalRate.divide(BigDecimal.valueOf(reconciliations.size()), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate total outstanding amount across all reconciliations
     */
    private BigDecimal getTotalOutstandingAmount(List<BankReconciliation> reconciliations) {
        return reconciliations.stream()
            .map(r -> {
                BigDecimal checks = r.getOutstandingChecksAmount() != null ? r.getOutstandingChecksAmount() : BigDecimal.ZERO;
                BigDecimal deposits = r.getDepositsInTransitAmount() != null ? r.getDepositsInTransitAmount() : BigDecimal.ZERO;
                BigDecimal other = r.getOtherItemsAmount() != null ? r.getOtherItemsAmount() : BigDecimal.ZERO;
                return checks.add(deposits).add(other);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}