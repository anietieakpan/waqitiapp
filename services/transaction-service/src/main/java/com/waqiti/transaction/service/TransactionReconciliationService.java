package com.waqiti.transaction.service;

import com.waqiti.transaction.dto.*;
import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.repository.TransactionRepository;
import com.waqiti.transaction.client.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade Transaction Reconciliation Service
 * 
 * Handles comprehensive transaction reconciliation with external systems:
 * - Real-time transaction matching with payment processors
 * - Daily/weekly/monthly reconciliation cycles
 * - Exception handling for unmatched transactions
 * - Multi-currency reconciliation with FX considerations
 * - Settlement file processing and validation
 * - Dispute identification and management
 * - Automated reconciliation reporting
 * - Regulatory compliance reconciliation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionReconciliationService {

    private final TransactionRepository transactionRepository;
    private final LedgerServiceClient ledgerClient;
    private final PaymentProviderClient paymentProviderClient;
    private final BankingServiceClient bankingClient;
    private final NotificationServiceClient notificationClient;
    private final ReportingServiceClient reportingClient;

    @Value("${reconciliation.tolerance.amount:0.01}")
    private BigDecimal toleranceAmount;

    @Value("${reconciliation.tolerance.percentage:0.001}")
    private BigDecimal tolerancePercentage;

    @Value("${reconciliation.auto-resolve.enabled:true}")
    private boolean autoResolveEnabled;

    /**
     * Perform comprehensive transaction reconciliation
     */
    @Async
    @Transactional
    public CompletableFuture<ReconciliationResult> performReconciliation(ReconciliationRequest request) {
        log.info("Starting reconciliation for period: {} to {}, providers: {}", 
                request.getStartDate(), request.getEndDate(), request.getProviders());

        try {
            ReconciliationContext context = buildReconciliationContext(request);
            
            // Step 1: Fetch internal transactions
            List<Transaction> internalTransactions = fetchInternalTransactions(request);
            
            // Step 2: Fetch external records from all providers
            Map<String, List<ExternalTransactionRecord>> externalRecords = fetchExternalRecords(request);
            
            // Step 3: Perform matching algorithm
            ReconciliationMatchingResult matchingResult = performTransactionMatching(
                internalTransactions, externalRecords, context);
            
            // Step 4: Handle unmatched transactions
            UnmatchedTransactionResult unmatchedResult = processUnmatchedTransactions(
                matchingResult.getUnmatchedInternal(),
                matchingResult.getUnmatchedExternal(),
                context);
            
            // Step 5: Validate amounts and detect discrepancies
            AmountReconciliationResult amountResult = validateAmountsAndDetectDiscrepancies(
                matchingResult.getMatchedTransactions(), context);
            
            // Step 6: Process settlement reconciliation
            SettlementReconciliationResult settlementResult = reconcileSettlements(
                matchingResult.getMatchedTransactions(), request);
            
            // Step 7: Generate comprehensive reconciliation report
            ReconciliationReport report = generateReconciliationReport(
                internalTransactions, externalRecords, matchingResult, 
                unmatchedResult, amountResult, settlementResult, context);
            
            // Step 8: Handle auto-resolution of discrepancies
            AutoResolutionResult autoResolution = performAutoResolution(
                matchingResult, unmatchedResult, amountResult, context);
            
            // Step 9: Create and store reconciliation summary
            ReconciliationSummary summary = createReconciliationSummary(
                report, autoResolution, context);
            
            // Step 10: Send notifications and alerts
            sendReconciliationNotifications(summary, context);

            ReconciliationResult result = ReconciliationResult.builder()
                    .reconciliationId(context.getReconciliationId())
                    .status("COMPLETED")
                    .summary(summary)
                    .report(report)
                    .matchedCount(matchingResult.getMatchedTransactions().size())
                    .unmatchedInternalCount(matchingResult.getUnmatchedInternal().size())
                    .unmatchedExternalCount(matchingResult.getUnmatchedExternal().size())
                    .discrepancyCount(amountResult.getDiscrepancies().size())
                    .autoResolvedCount(autoResolution.getResolvedCount())
                    .totalProcessingTime(context.getTotalProcessingTime())
                    .completedAt(LocalDateTime.now())
                    .build();

            log.info("Reconciliation completed: {} matched, {} unmatched internal, {} unmatched external, {} discrepancies",
                    result.getMatchedCount(), result.getUnmatchedInternalCount(), 
                    result.getUnmatchedExternalCount(), result.getDiscrepancyCount());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Reconciliation failed", e);
            return CompletableFuture.completedFuture(ReconciliationResult.failed(e.getMessage()));
        }
    }

    /**
     * Perform real-time transaction reconciliation
     */
    public RealTimeReconciliationResult reconcileTransactionRealTime(String transactionId) {
        log.info("Performing real-time reconciliation for transaction: {}", transactionId);

        try {
            // Find internal transaction
            Transaction internalTransaction = transactionRepository.findById(UUID.fromString(transactionId))
                    .orElseThrow(() -> new ReconciliationException("Transaction not found: " + transactionId));

            // Fetch corresponding external records
            List<ExternalTransactionRecord> externalRecords = fetchExternalRecordsForTransaction(internalTransaction);

            if (externalRecords.isEmpty()) {
                return RealTimeReconciliationResult.notFound(
                    "No external records found for transaction: " + transactionId);
            }

            // Find best match
            ExternalTransactionRecord bestMatch = findBestMatch(internalTransaction, externalRecords);
            
            if (bestMatch == null) {
                return RealTimeReconciliationResult.noMatch(
                    "No matching external record found", externalRecords);
            }

            // Validate amounts
            AmountValidationResult amountValidation = validateAmounts(internalTransaction, bestMatch);
            
            if (!amountValidation.isValid()) {
                return RealTimeReconciliationResult.discrepancy(
                    internalTransaction, bestMatch, amountValidation.getDiscrepancy());
            }

            // Success - mark as reconciled
            markTransactionAsReconciled(internalTransaction, bestMatch);

            return RealTimeReconciliationResult.success(internalTransaction, bestMatch);

        } catch (Exception e) {
            log.error("Real-time reconciliation failed for transaction: {}", transactionId, e);
            return RealTimeReconciliationResult.error(e.getMessage());
        }
    }

    /**
     * Process settlement files for reconciliation
     */
    @Transactional
    public SettlementFileProcessingResult processSettlementFile(SettlementFileRequest request) {
        log.info("Processing settlement file: {} from provider: {}", 
                request.getFileName(), request.getProviderName());

        try {
            // Parse settlement file
            List<SettlementRecord> settlementRecords = parseSettlementFile(request.getFileContent(), request.getFileFormat());
            
            // Validate file integrity
            FileValidationResult validation = validateSettlementFile(settlementRecords, request);
            if (!validation.isValid()) {
                return SettlementFileProcessingResult.validationFailed(validation.getErrors());
            }

            // Match settlement records with transactions
            SettlementMatchingResult matchingResult = matchSettlementRecords(settlementRecords);

            // Process settlements
            List<SettlementProcessingResult> processingResults = processSettlementRecords(
                matchingResult.getMatchedRecords());

            // Handle unmatched settlement records
            UnmatchedSettlementResult unmatchedResult = processUnmatchedSettlements(
                matchingResult.getUnmatchedRecords());

            // Generate settlement reconciliation report
            SettlementReconciliationReport report = generateSettlementReport(
                settlementRecords, matchingResult, processingResults, unmatchedResult);

            return SettlementFileProcessingResult.builder()
                    .fileName(request.getFileName())
                    .providerName(request.getProviderName())
                    .totalRecords(settlementRecords.size())
                    .matchedRecords(matchingResult.getMatchedRecords().size())
                    .unmatchedRecords(matchingResult.getUnmatchedRecords().size())
                    .processedSuccessfully(processingResults.size())
                    .report(report)
                    .status("COMPLETED")
                    .processedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Settlement file processing failed", e);
            return SettlementFileProcessingResult.failed(e.getMessage());
        }
    }

    /**
     * Perform multi-currency reconciliation with FX considerations
     */
    public MultiCurrencyReconciliationResult performMultiCurrencyReconciliation(
            MultiCurrencyReconciliationRequest request) {
        
        log.info("Starting multi-currency reconciliation for currencies: {}", request.getCurrencies());

        try {
            Map<String, CurrencyReconciliationResult> currencyResults = new HashMap<>();

            for (String currency : request.getCurrencies()) {
                CurrencyReconciliationResult currencyResult = reconcileCurrency(currency, request);
                currencyResults.put(currency, currencyResult);
            }

            // Cross-currency validation
            CrossCurrencyValidationResult crossValidation = performCrossCurrencyValidation(currencyResults);

            // FX rate reconciliation
            FXReconciliationResult fxReconciliation = reconcileFXRates(request);

            return MultiCurrencyReconciliationResult.builder()
                    .currencyResults(currencyResults)
                    .crossValidation(crossValidation)
                    .fxReconciliation(fxReconciliation)
                    .totalDiscrepancies(calculateTotalDiscrepancies(currencyResults))
                    .status("COMPLETED")
                    .completedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Multi-currency reconciliation failed", e);
            throw new ReconciliationException("Multi-currency reconciliation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate comprehensive reconciliation analytics
     */
    public ReconciliationAnalytics generateReconciliationAnalytics(ReconciliationAnalyticsRequest request) {
        try {
            // Fetch reconciliation history
            List<ReconciliationSummary> historicalData = getReconciliationHistory(request);

            // Calculate key metrics
            ReconciliationMetrics metrics = calculateReconciliationMetrics(historicalData);

            // Identify trends and patterns
            ReconciliationTrends trends = identifyReconciliationTrends(historicalData);

            // Generate recommendations
            List<ReconciliationRecommendation> recommendations = generateRecommendations(metrics, trends);

            return ReconciliationAnalytics.builder()
                    .reportPeriod(request.getStartDate() + " to " + request.getEndDate())
                    .metrics(metrics)
                    .trends(trends)
                    .recommendations(recommendations)
                    .topDiscrepancyTypes(identifyTopDiscrepancyTypes(historicalData))
                    .providerPerformance(analyzeProviderPerformance(historicalData))
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate reconciliation analytics", e);
            throw new ReconciliationException("Analytics generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Sophisticated transaction matching algorithm
     */
    private ReconciliationMatchingResult performTransactionMatching(
            List<Transaction> internalTransactions,
            Map<String, List<ExternalTransactionRecord>> externalRecords,
            ReconciliationContext context) {

        List<TransactionMatch> matches = new ArrayList<>();
        List<Transaction> unmatchedInternal = new ArrayList<>(internalTransactions);
        Map<String, List<ExternalTransactionRecord>> unmatchedExternal = new HashMap<>(externalRecords);

        // Phase 1: Exact matching by reference/ID
        performExactMatching(unmatchedInternal, unmatchedExternal, matches);

        // Phase 2: Fuzzy matching by amount, date, and other criteria
        performFuzzyMatching(unmatchedInternal, unmatchedExternal, matches, context);

        // Phase 3: Advanced pattern matching
        performPatternMatching(unmatchedInternal, unmatchedExternal, matches, context);

        return ReconciliationMatchingResult.builder()
                .matchedTransactions(matches)
                .unmatchedInternal(unmatchedInternal)
                .unmatchedExternal(unmatchedExternal.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList()))
                .build();
    }

    /**
     * Exact matching by transaction reference
     */
    private void performExactMatching(List<Transaction> internal,
                                    Map<String, List<ExternalTransactionRecord>> external,
                                    List<TransactionMatch> matches) {
        Iterator<Transaction> internalIterator = internal.iterator();
        
        while (internalIterator.hasNext()) {
            Transaction transaction = internalIterator.next();
            
            for (String provider : external.keySet()) {
                List<ExternalTransactionRecord> providerRecords = external.get(provider);
                Iterator<ExternalTransactionRecord> externalIterator = providerRecords.iterator();
                
                while (externalIterator.hasNext()) {
                    ExternalTransactionRecord record = externalIterator.next();
                    
                    if (isExactMatch(transaction, record)) {
                        matches.add(new TransactionMatch(transaction, record, provider, 100.0, "EXACT_REFERENCE"));
                        internalIterator.remove();
                        externalIterator.remove();
                        break;
                    }
                }
                
                if (matches.stream().anyMatch(m -> m.getInternalTransaction().equals(transaction))) {
                    break;
                }
            }
        }
    }

    /**
     * Fuzzy matching based on amount, date, and other attributes
     */
    private void performFuzzyMatching(List<Transaction> internal,
                                    Map<String, List<ExternalTransactionRecord>> external,
                                    List<TransactionMatch> matches,
                                    ReconciliationContext context) {
        
        for (Transaction transaction : new ArrayList<>(internal)) {
            ExternalTransactionRecord bestMatch = null;
            String bestProvider = null;
            double bestScore = 0.0;
            
            for (String provider : external.keySet()) {
                for (ExternalTransactionRecord record : external.get(provider)) {
                    double score = calculateMatchScore(transaction, record, context);
                    
                    if (score > bestScore && score >= context.getMinimumMatchThreshold()) {
                        bestScore = score;
                        bestMatch = record;
                        bestProvider = provider;
                    }
                }
            }
            
            if (bestMatch != null) {
                matches.add(new TransactionMatch(transaction, bestMatch, bestProvider, bestScore, "FUZZY_MATCH"));
                internal.remove(transaction);
                external.get(bestProvider).remove(bestMatch);
            }
        }
    }

    /**
     * Calculate match score based on multiple criteria
     */
    private double calculateMatchScore(Transaction transaction, ExternalTransactionRecord record, ReconciliationContext context) {
        double score = 0.0;
        
        // Amount matching (40% weight)
        double amountScore = calculateAmountMatchScore(transaction.getAmount(), record.getAmount());
        score += amountScore * 0.4;
        
        // Date matching (30% weight)
        double dateScore = calculateDateMatchScore(transaction.getCreatedAt(), record.getTransactionDate());
        score += dateScore * 0.3;
        
        // Currency matching (20% weight)
        if (transaction.getCurrency().equals(record.getCurrency())) {
            score += 0.2;
        }
        
        // Additional criteria (10% weight)
        double additionalScore = calculateAdditionalMatchScore(transaction, record);
        score += additionalScore * 0.1;
        
        return score;
    }

    /**
     * Calculate amount match score with tolerance
     */
    private double calculateAmountMatchScore(BigDecimal amount1, BigDecimal amount2) {
        if (amount1.equals(amount2)) {
            return 1.0; // Perfect match
        }
        
        BigDecimal difference = amount1.subtract(amount2).abs();
        BigDecimal percentageDiff = difference.divide(amount1.max(amount2), 4, java.math.RoundingMode.HALF_UP);
        
        // Check if within tolerance
        if (difference.compareTo(toleranceAmount) <= 0 || percentageDiff.compareTo(tolerancePercentage) <= 0) {
            return 0.9; // Very close match
        }
        
        // Calculate score based on how close amounts are
        double maxDifference = amount1.max(amount2).multiply(new BigDecimal("0.1")).doubleValue(); // 10% threshold
        double actualDifference = difference.doubleValue();
        
        if (actualDifference > maxDifference) {
            return 0.0; // Too far apart
        }
        
        return 1.0 - (actualDifference / maxDifference);
    }

    /**
     * Auto-resolve discrepancies where possible
     */
    private AutoResolutionResult performAutoResolution(ReconciliationMatchingResult matchingResult,
                                                     UnmatchedTransactionResult unmatchedResult,
                                                     AmountReconciliationResult amountResult,
                                                     ReconciliationContext context) {
        if (!autoResolveEnabled) {
            return AutoResolutionResult.disabled();
        }

        int resolvedCount = 0;
        List<ResolutionAction> actions = new ArrayList<>();

        // Auto-resolve small amount discrepancies
        for (AmountDiscrepancy discrepancy : amountResult.getDiscrepancies()) {
            if (canAutoResolveDiscrepancy(discrepancy)) {
                ResolutionAction action = autoResolveDiscrepancy(discrepancy);
                actions.add(action);
                resolvedCount++;
            }
        }

        // Auto-resolve timing differences
        resolvedCount += autoResolveTimingDifferences(matchingResult, actions);

        return AutoResolutionResult.builder()
                .resolvedCount(resolvedCount)
                .actions(actions)
                .build();
    }

    // Helper methods for matching and validation
    private boolean isExactMatch(Transaction transaction, ExternalTransactionRecord record) {
        return Objects.equals(transaction.getReference(), record.getReference()) ||
               Objects.equals(transaction.getExternalTransactionId(), record.getTransactionId());
    }

    private boolean canAutoResolveDiscrepancy(AmountDiscrepancy discrepancy) {
        return discrepancy.getAmount().abs().compareTo(new BigDecimal("1.00")) <= 0; // Max $1 auto-resolve
    }

    private void markTransactionAsReconciled(Transaction transaction, ExternalTransactionRecord record) {
        transaction.setReconciled(true);
        transaction.setReconciledAt(LocalDateTime.now());
        transaction.setExternalReference(record.getReference());
        transactionRepository.save(transaction);
    }

    // Exception handling
    public static class ReconciliationException extends RuntimeException {
        public ReconciliationException(String message) {
            super(message);
        }
        
        public ReconciliationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}