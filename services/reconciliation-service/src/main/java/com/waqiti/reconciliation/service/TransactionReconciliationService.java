package com.waqiti.reconciliation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * CRITICAL FINANCIAL SERVICE: Transaction Reconciliation Service
 * Ensures financial integrity through comprehensive transaction matching and reconciliation
 * 
 * Features:
 * - Multi-provider transaction matching
 * - Automated discrepancy detection
 * - Real-time balance verification
 * - Settlement file processing
 * - Dispute identification and flagging
 * - Regulatory compliance reporting
 * - Audit trail maintenance
 * - Automated reconciliation workflows
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionReconciliationService {
    
    private final TransactionRepository transactionRepository;
    private final ProviderTransactionRepository providerTransactionRepository;
    private final ReconciliationReportRepository reconciliationReportRepository;
    private final DiscrepancyRepository discrepancyRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    
    // Reconciliation tolerances
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01"); // 1 cent
    private static final Duration TIME_TOLERANCE = Duration.ofMinutes(5);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    // Cache keys
    private static final String RECONCILIATION_LOCK_PREFIX = "recon:lock:";
    private static final String RECONCILIATION_CACHE_PREFIX = "recon:cache:";
    private static final String BALANCE_CACHE_PREFIX = "balance:cache:";
    
    // Processing status tracking
    private final Map<String, ReconciliationStatus> activeReconciliations = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        log.info("Transaction Reconciliation Service initialized");
        // Schedule initial reconciliation for startup
        performStartupReconciliation();
    }
    
    /**
     * Perform comprehensive transaction reconciliation
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void performScheduledReconciliation() {
        log.info("Starting scheduled transaction reconciliation");
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(10);
            ReconciliationResult result = reconcileTransactions(cutoffTime);
            
            log.info("Scheduled reconciliation completed - Matched: {}, Discrepancies: {}, Pending: {}",
                result.getMatchedCount(), result.getDiscrepancyCount(), result.getPendingCount());
            
            // Alert on significant discrepancies
            if (result.getDiscrepancyCount() > 10 || result.getTotalDiscrepancyAmount().compareTo(new BigDecimal("1000")) > 0) {
                sendReconciliationAlert(result);
            }
            
        } catch (Exception e) {
            log.error("Error in scheduled reconciliation", e);
            notificationService.sendAlert("Reconciliation Failed", 
                "Scheduled reconciliation failed: " + e.getMessage());
        }
    }
    
    /**
     * Main reconciliation logic
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ReconciliationResult reconcileTransactions(LocalDateTime cutoffTime) {
        String reconciliationId = UUID.randomUUID().toString();
        ReconciliationStatus status = new ReconciliationStatus(reconciliationId);
        activeReconciliations.put(reconciliationId, status);
        
        try {
            log.info("Starting reconciliation {} for transactions before {}", reconciliationId, cutoffTime);
            
            ReconciliationResult result = new ReconciliationResult(reconciliationId);
            
            // Step 1: Get unreconciled transactions
            List<Transaction> unreconciledTransactions = 
                transactionRepository.findUnreconciledBefore(cutoffTime);
            
            log.info("Found {} unreconciled transactions", unreconciledTransactions.size());
            status.setTotalTransactions(unreconciledTransactions.size());
            
            // Step 2: Match with provider transactions
            for (Transaction transaction : unreconciledTransactions) {
                try {
                    ReconciliationMatch match = findProviderMatch(transaction);
                    
                    if (match.isMatched()) {
                        processMatchedTransaction(transaction, match, result);
                        status.incrementProcessed();
                    } else if (match.hasPartialMatch()) {
                        processPartialMatch(transaction, match, result);
                        status.incrementProcessed();
                    } else {
                        processPendingTransaction(transaction, result);
                        status.incrementProcessed();
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing transaction {}", transaction.getId(), e);
                    result.addError(transaction.getId(), e.getMessage());
                }
            }
            
            // Step 3: Check for orphaned provider transactions
            checkOrphanedProviderTransactions(cutoffTime, result);
            
            // Step 4: Validate balances
            validateAccountBalances(result);
            
            // Step 5: Generate reconciliation report
            ReconciliationReport report = generateReconciliationReport(result);
            reconciliationReportRepository.save(report);
            
            status.setCompleted(true);
            log.info("Reconciliation {} completed successfully", reconciliationId);
            
            return result;
            
        } catch (Exception e) {
            status.setError(e.getMessage());
            log.error("Reconciliation {} failed", reconciliationId, e);
            throw e;
        } finally {
            activeReconciliations.remove(reconciliationId);
        }
    }
    
    /**
     * Find matching provider transaction
     */
    private ReconciliationMatch findProviderMatch(Transaction transaction) {
        // Try exact match first
        List<ProviderTransaction> exactMatches = providerTransactionRepository
            .findByProviderTransactionIdAndAmount(
                transaction.getProviderTransactionId(),
                transaction.getAmount()
            );
        
        if (!exactMatches.isEmpty()) {
            ProviderTransaction bestMatch = findBestMatch(transaction, exactMatches);
            return ReconciliationMatch.exactMatch(bestMatch);
        }
        
        // Try amount and time window match
        LocalDateTime startTime = transaction.getCreatedAt().minus(TIME_TOLERANCE);
        LocalDateTime endTime = transaction.getCreatedAt().plus(TIME_TOLERANCE);
        
        List<ProviderTransaction> amountMatches = providerTransactionRepository
            .findByAmountAndTimestampBetween(
                transaction.getAmount(),
                startTime,
                endTime
            );
        
        if (!amountMatches.isEmpty()) {
            ProviderTransaction bestMatch = findBestMatch(transaction, amountMatches);
            if (isAcceptableMatch(transaction, bestMatch)) {
                return ReconciliationMatch.fuzzyMatch(bestMatch);
            }
        }
        
        // Try partial amount matches (within tolerance)
        BigDecimal minAmount = transaction.getAmount().subtract(AMOUNT_TOLERANCE);
        BigDecimal maxAmount = transaction.getAmount().add(AMOUNT_TOLERANCE);
        
        List<ProviderTransaction> toleranceMatches = providerTransactionRepository
            .findByAmountBetweenAndTimestampBetween(
                minAmount, maxAmount, startTime, endTime
            );
        
        if (!toleranceMatches.isEmpty()) {
            ProviderTransaction bestMatch = findBestMatch(transaction, toleranceMatches);
            return ReconciliationMatch.partialMatch(bestMatch, 
                calculateMatchConfidence(transaction, bestMatch));
        }
        
        return ReconciliationMatch.noMatch();
    }
    
    /**
     * Find best match from candidates
     */
    private ProviderTransaction findBestMatch(Transaction transaction, List<ProviderTransaction> candidates) {
        return candidates.stream()
            .max(Comparator.comparingDouble(provider -> calculateMatchScore(transaction, provider)))
            .orElse(null);
    }
    
    /**
     * Calculate match score for ranking
     */
    private double calculateMatchScore(Transaction transaction, ProviderTransaction provider) {
        double score = 0.0;
        
        // Amount match (40% weight)
        BigDecimal amountDiff = transaction.getAmount().subtract(provider.getAmount()).abs();
        double amountScore = Math.max(0, 1.0 - amountDiff.doubleValue() / transaction.getAmount().doubleValue());
        score += amountScore * 0.4;
        
        // Time match (30% weight)
        Duration timeDiff = Duration.between(transaction.getCreatedAt(), provider.getTimestamp()).abs();
        double timeScore = Math.max(0, 1.0 - timeDiff.toMinutes() / 60.0); // 1 hour max
        score += timeScore * 0.3;
        
        // Reference match (20% weight)
        if (Objects.equals(transaction.getProviderTransactionId(), provider.getProviderTransactionId())) {
            score += 0.2;
        }
        
        // Status match (10% weight)
        if (transaction.getStatus().name().equals(provider.getStatus())) {
            score += 0.1;
        }
        
        return score;
    }
    
    /**
     * Check if match is acceptable
     */
    private boolean isAcceptableMatch(Transaction transaction, ProviderTransaction provider) {
        return calculateMatchScore(transaction, provider) > 0.7; // 70% threshold
    }
    
    /**
     * Calculate match confidence
     */
    private double calculateMatchConfidence(Transaction transaction, ProviderTransaction provider) {
        return calculateMatchScore(transaction, provider);
    }
    
    /**
     * Process matched transaction
     */
    private void processMatchedTransaction(Transaction transaction, ReconciliationMatch match, 
                                        ReconciliationResult result) {
        try {
            ProviderTransaction providerTx = match.getProviderTransaction();
            
            // Update reconciliation status
            transaction.setReconciled(true);
            transaction.setReconciledAt(LocalDateTime.now());
            transaction.setProviderTransactionId(providerTx.getProviderTransactionId());
            transactionRepository.save(transaction);
            
            // Mark provider transaction as matched
            providerTx.setMatched(true);
            providerTx.setMatchedTransactionId(transaction.getId());
            providerTransactionRepository.save(providerTx);
            
            result.addMatchedTransaction(transaction.getId(), providerTx.getId());
            
            // Check for any discrepancies even in matched transactions
            validateMatchedTransaction(transaction, providerTx, result);
            
            log.debug("Transaction {} successfully reconciled with provider transaction {}", 
                transaction.getId(), providerTx.getId());
                
        } catch (Exception e) {
            log.error("Error processing matched transaction {}", transaction.getId(), e);
            result.addError(transaction.getId(), "Failed to process match: " + e.getMessage());
        }
    }
    
    /**
     * Process partial match
     */
    private void processPartialMatch(Transaction transaction, ReconciliationMatch match, 
                                   ReconciliationResult result) {
        ProviderTransaction providerTx = match.getProviderTransaction();
        
        // Create discrepancy record
        Discrepancy discrepancy = Discrepancy.builder()
            .id(UUID.randomUUID().toString())
            .transactionId(transaction.getId())
            .providerTransactionId(providerTx.getId())
            .discrepancyType(DiscrepancyType.PARTIAL_MATCH)
            .expectedAmount(transaction.getAmount())
            .actualAmount(providerTx.getAmount())
            .amountDifference(transaction.getAmount().subtract(providerTx.getAmount()))
            .confidence(match.getConfidence())
            .status(DiscrepancyStatus.PENDING_REVIEW)
            .createdAt(LocalDateTime.now())
            .description(String.format("Partial match with confidence %.2f", match.getConfidence()))
            .build();
        
        discrepancyRepository.save(discrepancy);
        result.addDiscrepancy(discrepancy);
        
        log.warn("Partial match found for transaction {} with confidence {}", 
            transaction.getId(), match.getConfidence());
    }
    
    /**
     * Process pending transaction (no match found)
     */
    private void processPendingTransaction(Transaction transaction, ReconciliationResult result) {
        // Check if transaction is old enough to be considered orphaned
        Duration age = Duration.between(transaction.getCreatedAt(), LocalDateTime.now());
        
        if (age.toHours() > 24) { // 24 hours threshold
            // Create orphaned transaction discrepancy
            Discrepancy discrepancy = Discrepancy.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(transaction.getId())
                .discrepancyType(DiscrepancyType.ORPHANED_TRANSACTION)
                .expectedAmount(transaction.getAmount())
                .actualAmount(BigDecimal.ZERO)
                .amountDifference(transaction.getAmount())
                .status(DiscrepancyStatus.PENDING_REVIEW)
                .createdAt(LocalDateTime.now())
                .description("No matching provider transaction found after 24 hours")
                .build();
            
            discrepancyRepository.save(discrepancy);
            result.addDiscrepancy(discrepancy);
            
            log.warn("Orphaned transaction detected: {} (age: {} hours)", 
                transaction.getId(), age.toHours());
        } else {
            result.addPendingTransaction(transaction.getId());
        }
    }
    
    /**
     * Check for orphaned provider transactions
     */
    private void checkOrphanedProviderTransactions(LocalDateTime cutoffTime, ReconciliationResult result) {
        List<ProviderTransaction> orphanedProvider = providerTransactionRepository
            .findUnmatchedBefore(cutoffTime);
        
        for (ProviderTransaction providerTx : orphanedProvider) {
            Discrepancy discrepancy = Discrepancy.builder()
                .id(UUID.randomUUID().toString())
                .providerTransactionId(providerTx.getId())
                .discrepancyType(DiscrepancyType.ORPHANED_PROVIDER_TRANSACTION)
                .expectedAmount(BigDecimal.ZERO)
                .actualAmount(providerTx.getAmount())
                .amountDifference(providerTx.getAmount().negate())
                .status(DiscrepancyStatus.PENDING_REVIEW)
                .createdAt(LocalDateTime.now())
                .description("Provider transaction without matching internal transaction")
                .build();
            
            discrepancyRepository.save(discrepancy);
            result.addDiscrepancy(discrepancy);
            
            log.warn("Orphaned provider transaction detected: {}", providerTx.getId());
        }
    }
    
    /**
     * Validate matched transaction for discrepancies
     */
    private void validateMatchedTransaction(Transaction transaction, ProviderTransaction providerTx, 
                                         ReconciliationResult result) {
        List<String> issues = new ArrayList<>();
        
        // Amount validation
        if (transaction.getAmount().compareTo(providerTx.getAmount()) != 0) {
            BigDecimal difference = transaction.getAmount().subtract(providerTx.getAmount()).abs();
            if (difference.compareTo(AMOUNT_TOLERANCE) > 0) {
                issues.add(String.format("Amount mismatch: Expected %s, Actual %s", 
                    transaction.getAmount(), providerTx.getAmount()));
            }
        }
        
        // Status validation
        if (!isStatusCompatible(transaction.getStatus(), providerTx.getStatus())) {
            issues.add(String.format("Status mismatch: Expected %s, Actual %s", 
                transaction.getStatus(), providerTx.getStatus()));
        }
        
        // Currency validation
        if (!Objects.equals(transaction.getCurrency(), providerTx.getCurrency())) {
            issues.add(String.format("Currency mismatch: Expected %s, Actual %s", 
                transaction.getCurrency(), providerTx.getCurrency()));
        }
        
        // Create discrepancy for issues found
        if (!issues.isEmpty()) {
            Discrepancy discrepancy = Discrepancy.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(transaction.getId())
                .providerTransactionId(providerTx.getId())
                .discrepancyType(DiscrepancyType.DATA_MISMATCH)
                .expectedAmount(transaction.getAmount())
                .actualAmount(providerTx.getAmount())
                .amountDifference(transaction.getAmount().subtract(providerTx.getAmount()))
                .status(DiscrepancyStatus.PENDING_REVIEW)
                .createdAt(LocalDateTime.now())
                .description(String.join("; ", issues))
                .build();
            
            discrepancyRepository.save(discrepancy);
            result.addDiscrepancy(discrepancy);
        }
    }
    
    /**
     * Check if transaction status is compatible with provider status
     */
    private boolean isStatusCompatible(TransactionStatus transactionStatus, String providerStatus) {
        Map<TransactionStatus, Set<String>> compatibilityMap = Map.of(
            TransactionStatus.COMPLETED, Set.of("SUCCESS", "SETTLED", "COMPLETED"),
            TransactionStatus.PENDING, Set.of("PENDING", "PROCESSING", "SUBMITTED"),
            TransactionStatus.FAILED, Set.of("FAILED", "REJECTED", "CANCELLED"),
            TransactionStatus.CANCELLED, Set.of("CANCELLED", "VOIDED", "REVERSED")
        );
        
        return compatibilityMap.getOrDefault(transactionStatus, Set.of())
            .contains(providerStatus.toUpperCase());
    }
    
    /**
     * Validate account balances
     */
    private void validateAccountBalances(ReconciliationResult result) {
        try {
            Map<String, BigDecimal> internalBalances = calculateInternalBalances();
            Map<String, BigDecimal> providerBalances = fetchProviderBalances();
            
            for (Map.Entry<String, BigDecimal> entry : internalBalances.entrySet()) {
                String account = entry.getKey();
                BigDecimal internalBalance = entry.getValue();
                BigDecimal providerBalance = providerBalances.get(account);
                
                if (providerBalance != null) {
                    BigDecimal difference = internalBalance.subtract(providerBalance).abs();
                    
                    if (difference.compareTo(AMOUNT_TOLERANCE) > 0) {
                        Discrepancy discrepancy = Discrepancy.builder()
                            .id(UUID.randomUUID().toString())
                            .discrepancyType(DiscrepancyType.BALANCE_MISMATCH)
                            .expectedAmount(internalBalance)
                            .actualAmount(providerBalance)
                            .amountDifference(internalBalance.subtract(providerBalance))
                            .status(DiscrepancyStatus.PENDING_REVIEW)
                            .createdAt(LocalDateTime.now())
                            .description(String.format("Balance mismatch for account %s", account))
                            .build();
                        
                        discrepancyRepository.save(discrepancy);
                        result.addDiscrepancy(discrepancy);
                        
                        log.warn("Balance mismatch detected for account {}: Internal={}, Provider={}", 
                            account, internalBalance, providerBalance);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error validating account balances", e);
            result.addError("BALANCE_VALIDATION", e.getMessage());
        }
    }
    
    /**
     * Calculate internal account balances
     */
    private Map<String, BigDecimal> calculateInternalBalances() {
        // Implementation would calculate balances from internal transactions
        return transactionRepository.calculateAccountBalances();
    }
    
    /**
     * Fetch provider balances
     */
    private Map<String, BigDecimal> fetchProviderBalances() {
        // Implementation would fetch balances from payment providers
        Map<String, BigDecimal> balances = new HashMap<>();
        
        try {
            // Example: Fetch from Stripe, PayPal, etc.
            balances.putAll(fetchStripeBalances());
            balances.putAll(fetchPayPalBalances());
            
        } catch (Exception e) {
            log.error("Error fetching provider balances", e);
        }
        
        return balances;
    }
    
    /**
     * Generate reconciliation report
     */
    private ReconciliationReport generateReconciliationReport(ReconciliationResult result) {
        return ReconciliationReport.builder()
            .id(result.getReconciliationId())
            .startTime(LocalDateTime.now().minusMinutes(10)) // Approximate
            .endTime(LocalDateTime.now())
            .totalTransactionsProcessed(result.getMatchedCount() + result.getDiscrepancyCount() + result.getPendingCount())
            .matchedTransactions(result.getMatchedCount())
            .discrepancyCount(result.getDiscrepancyCount())
            .pendingCount(result.getPendingCount())
            .totalDiscrepancyAmount(result.getTotalDiscrepancyAmount())
            .status(result.hasErrors() ? ReportStatus.COMPLETED_WITH_ERRORS : ReportStatus.COMPLETED)
            .createdAt(LocalDateTime.now())
            .summary(generateReportSummary(result))
            .build();
    }
    
    /**
     * Generate report summary
     */
    private String generateReportSummary(ReconciliationResult result) {
        return String.format(
            "Reconciliation completed. Processed: %d transactions, Matched: %d, " +
            "Discrepancies: %d (Total Amount: %s), Pending: %d, Errors: %d",
            result.getMatchedCount() + result.getDiscrepancyCount() + result.getPendingCount(),
            result.getMatchedCount(),
            result.getDiscrepancyCount(),
            result.getTotalDiscrepancyAmount(),
            result.getPendingCount(),
            result.getErrors().size()
        );
    }
    
    /**
     * Send reconciliation alert
     */
    private void sendReconciliationAlert(ReconciliationResult result) {
        String message = String.format(
            "ALERT: Significant discrepancies found in reconciliation. " +
            "Discrepancy Count: %d, Total Amount: %s",
            result.getDiscrepancyCount(),
            result.getTotalDiscrepancyAmount()
        );
        
        notificationService.sendAlert("Reconciliation Alert", message);
    }
    
    /**
     * Startup reconciliation check - DEPRECATED
     * This method has been replaced by ReconciliationStartupService for better security and architecture
     */
    @Deprecated
    public void performStartupReconciliation() {
        log.warn("Legacy startup reconciliation method called - this should use ReconciliationStartupService instead");
        // Method body removed to prevent usage - use ReconciliationStartupService instead
    }
    
    /**
     * Get reconciliation status
     */
    public Map<String, ReconciliationStatus> getActiveReconciliations() {
        return new HashMap<>(activeReconciliations);
    }
    
    // Placeholder methods for provider integration
    private Map<String, BigDecimal> fetchStripeBalances() {
        // Implementation would call Stripe API
        return new HashMap<>();
    }
    
    private Map<String, BigDecimal> fetchPayPalBalances() {
        // Implementation would call PayPal API  
        return new HashMap<>();
    }
    
    // Supporting classes would be in separate files
    // ReconciliationMatch, ReconciliationResult, ReconciliationStatus, etc.
}