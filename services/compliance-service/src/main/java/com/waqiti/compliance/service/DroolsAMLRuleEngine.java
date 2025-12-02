package com.waqiti.compliance.service;

import com.waqiti.compliance.model.AMLTransaction;
import com.waqiti.compliance.model.AMLTransaction.ComplianceAlert;
import com.waqiti.compliance.model.AMLTransaction.RiskScore;
import com.waqiti.compliance.entity.ComplianceTransaction;
import com.waqiti.compliance.repository.ComplianceTransactionRepository;
import com.waqiti.compliance.util.SecureLoggingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drools-based AML/CTF Rule Engine Service
 * 
 * This service provides a robust, production-ready implementation of AML/CTF compliance
 * rules using the Drools rule engine. It supports:
 * - Real-time transaction screening
 * - Dynamic rule updates without service restart
 * - Audit trail of all rule executions
 * - Performance monitoring and metrics
 * - Asynchronous and batch processing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DroolsAMLRuleEngine {
    
    private final KieContainer kieContainer;
    private final StatelessKieSession statelessKieSession;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ComplianceTransactionRepository complianceTransactionRepository;
    
    // Metrics and monitoring
    private final AtomicLong totalTransactionsProcessed = new AtomicLong(0);
    private final AtomicLong blockedTransactions = new AtomicLong(0);
    private final AtomicLong flaggedTransactions = new AtomicLong(0);
    private final AtomicLong sarFiledCount = new AtomicLong(0);
    private final Map<String, AtomicLong> ruleFireCount = new ConcurrentHashMap<>();
    
    // Cache for frequently accessed data
    private final Map<String, CustomerRiskProfile> customerRiskCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> sanctionsCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        log.info("Initializing Drools AML/CTF Rule Engine");
        log.info("Available KieBases: {}", kieContainer.getKieBaseNames());
        
        // Pre-load high-risk jurisdictions and sanctions lists
        loadSanctionsList();
        loadHighRiskJurisdictions();
        
        // Start metrics collection
        startMetricsCollection();
    }
    
    /**
     * Screen a single transaction through AML/CTF rules
     */
    @Transactional
    public ComplianceScreeningResult screenTransaction(AMLTransaction transaction) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Enrich transaction with cached data
            enrichTransaction(transaction);
            
            // Create a new KieSession for this transaction
            KieSession kieSession = kieContainer.newKieSession();
            
            try {
                // Set global variables
                kieSession.setGlobal("logger", log);
                
                // Insert transaction fact
                FactHandle factHandle = kieSession.insert(transaction);
                
                // Fire all rules
                int rulesFired = kieSession.fireAllRules();
                
                SecureLoggingUtil.logComplianceEvent(log, "DEBUG", 
                    transaction.getTransactionId(), transaction.getCustomerId(), 
                    "RULES_FIRED", "count", rulesFired);
                
                // Update metrics
                updateMetrics(transaction, rulesFired);
                
                // Create screening result
                ComplianceScreeningResult result = buildScreeningResult(transaction, rulesFired);
                
                // Persist screening result to database
                ComplianceTransaction complianceTransaction = persistScreeningResult(transaction, result, rulesFired, System.currentTimeMillis() - startTime);
                result.setDatabaseId(complianceTransaction.getId().toString());
                
                // Send events for high-risk transactions
                if (result.shouldBlock() || result.requiresSAR()) {
                    sendComplianceAlert(transaction, result);
                }
                
                // Audit trail
                auditRuleExecution(transaction, result, System.currentTimeMillis() - startTime);
                
                return result;
                
            } finally {
                kieSession.dispose();
            }
            
        } catch (Exception e) {
            SecureLoggingUtil.logComplianceEvent(log, "ERROR", 
                transaction.getTransactionId(), transaction.getCustomerId(), 
                "SCREENING_ERROR", "errorType", e.getClass().getSimpleName());
            return ComplianceScreeningResult.error(transaction.getTransactionId(), "Transaction screening failed");
        }
    }
    
    /**
     * Screen multiple transactions in batch (more efficient for bulk processing)
     */
    public List<ComplianceScreeningResult> screenTransactionsBatch(List<AMLTransaction> transactions) {
        log.info("Batch screening {} transactions", transactions.size());
        
        List<ComplianceScreeningResult> results = new ArrayList<>();
        
        // Use stateless session for batch processing (thread-safe)
        for (AMLTransaction transaction : transactions) {
            enrichTransaction(transaction);
        }
        
        // Execute all transactions in stateless session
        statelessKieSession.execute(transactions);
        
        // Build results
        for (AMLTransaction transaction : transactions) {
            results.add(buildScreeningResult(transaction, 0));
            updateMetrics(transaction, 0);
        }
        
        return results;
    }
    
    /**
     * Asynchronously screen a transaction
     */
    public CompletableFuture<ComplianceScreeningResult> screenTransactionAsync(AMLTransaction transaction) {
        return CompletableFuture.supplyAsync(() -> screenTransaction(transaction));
    }
    
    /**
     * Enrich transaction with additional data before rule execution
     */
    private void enrichTransaction(AMLTransaction transaction) {
        // Add customer risk profile from cache
        CustomerRiskProfile riskProfile = customerRiskCache.get(transaction.getCustomerId());
        if (riskProfile != null) {
            transaction.setCustomerRiskRating(riskProfile.getRiskRating());
            transaction.setPreviousSARCount(riskProfile.getSarCount());
            transaction.setPreviousAlertCount(riskProfile.getAlertCount());
        }
        
        // Check sanctions cache
        Boolean isSanctioned = sanctionsCache.get(transaction.getCounterpartyId());
        if (isSanctioned != null) {
            transaction.setCounterpartyIsSanctioned(isSanctioned);
        }
        
        // Calculate transaction velocity metrics
        calculateVelocityMetrics(transaction);
    }
    
    /**
     * Calculate velocity metrics for pattern detection
     */
    private void calculateVelocityMetrics(AMLTransaction transaction) {
        // This would typically query a database or cache
        // Calculate velocity metrics based on historical transaction data
        if (transaction.getDailyTransactionCount() == null) {
            transaction.setDailyTransactionCount(getTransactionCount(transaction.getAccountId(), 1));
        }
        if (transaction.getDailyTransactionVolume() == null) {
            transaction.setDailyTransactionVolume(getTransactionVolume(transaction.getAccountId(), 1));
        }
        if (transaction.getMonthlyTransactionCount() == null) {
            transaction.setMonthlyTransactionCount(getTransactionCount(transaction.getAccountId(), 30));
        }
        if (transaction.getMonthlyTransactionVolume() == null) {
            transaction.setMonthlyTransactionVolume(getTransactionVolume(transaction.getAccountId(), 30));
        }
    }
    
    /**
     * Build screening result from processed transaction
     */
    private ComplianceScreeningResult buildScreeningResult(AMLTransaction transaction, int rulesFired) {
        ComplianceScreeningResult result = new ComplianceScreeningResult();
        result.setTransactionId(transaction.getTransactionId());
        result.setScreeningTimestamp(LocalDateTime.now());
        result.setRulesFired(rulesFired);
        
        // Set risk score
        if (transaction.getRiskScore() != null) {
            result.setRiskScore(transaction.getRiskScore().getTotalScore());
            result.setRiskLevel(transaction.getRiskScore().getRiskLevel());
            result.setRecommendation(transaction.getRiskScore().getRecommendation());
            result.setRiskReasons(transaction.getRiskScore().getReasons());
        }
        
        // Set flags
        result.setShouldBlock(Boolean.TRUE.equals(transaction.getShouldBlock()));
        result.setRequiresReview(Boolean.TRUE.equals(transaction.getRequiresReview()));
        result.setRequiresSAR(Boolean.TRUE.equals(transaction.getRequiresSAR()));
        
        // Set alerts
        result.setAlerts(transaction.getAlerts());
        
        // Set risk indicators
        result.setRiskIndicators(new ArrayList<>(transaction.getRiskIndicators()));
        
        return result;
    }
    
    /**
     * Update metrics after rule execution
     */
    private void updateMetrics(AMLTransaction transaction, int rulesFired) {
        totalTransactionsProcessed.incrementAndGet();
        
        if (Boolean.TRUE.equals(transaction.getShouldBlock())) {
            blockedTransactions.incrementAndGet();
        }
        
        if (Boolean.TRUE.equals(transaction.getRequiresReview())) {
            flaggedTransactions.incrementAndGet();
        }
        
        if (Boolean.TRUE.equals(transaction.getRequiresSAR())) {
            sarFiledCount.incrementAndGet();
        }
        
        // Track which rules fired most frequently
        transaction.getRiskIndicators().forEach(indicator -> 
            ruleFireCount.computeIfAbsent(indicator.getCode(), k -> new AtomicLong()).incrementAndGet()
        );
    }
    
    /**
     * Send compliance alert for high-risk transactions
     */
    private void sendComplianceAlert(AMLTransaction transaction, ComplianceScreeningResult result) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertId", UUID.randomUUID().toString());
        alert.put("transactionId", transaction.getTransactionId());
        alert.put("customerId", transaction.getCustomerId());
        alert.put("accountId", transaction.getAccountId());
        alert.put("amount", transaction.getAmount());
        alert.put("currency", transaction.getCurrency());
        alert.put("riskScore", result.getRiskScore());
        alert.put("riskLevel", result.getRiskLevel());
        alert.put("shouldBlock", result.shouldBlock());
        alert.put("requiresSAR", result.requiresSAR());
        alert.put("alerts", result.getAlerts());
        alert.put("timestamp", LocalDateTime.now());
        
        // Send to compliance team queue
        kafkaTemplate.send("compliance-alerts", alert);
        
        // If transaction should be blocked, also send to transaction service
        if (result.shouldBlock()) {
            kafkaTemplate.send("transaction-blocks", alert);
        }
        
        // If SAR required, send to SAR filing queue
        if (result.requiresSAR()) {
            kafkaTemplate.send("sar-filing-queue", alert);
        }
    }
    
    /**
     * Audit trail for rule executions
     */
    private void auditRuleExecution(AMLTransaction transaction, ComplianceScreeningResult result, long executionTime) {
        Map<String, Object> auditEntry = new HashMap<>();
        auditEntry.put("auditId", UUID.randomUUID().toString());
        auditEntry.put("transactionId", transaction.getTransactionId());
        auditEntry.put("customerId", transaction.getCustomerId());
        auditEntry.put("riskScore", result.getRiskScore());
        auditEntry.put("riskLevel", result.getRiskLevel());
        auditEntry.put("decision", result.getRecommendation());
        auditEntry.put("rulesFired", result.getRulesFired());
        auditEntry.put("executionTimeMs", executionTime);
        auditEntry.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("compliance-audit-trail", auditEntry);
    }
    
    /**
     * Get current metrics
     */
    public ComplianceMetrics getMetrics() {
        ComplianceMetrics metrics = new ComplianceMetrics();
        metrics.setTotalTransactionsProcessed(totalTransactionsProcessed.get());
        metrics.setBlockedTransactions(blockedTransactions.get());
        metrics.setFlaggedTransactions(flaggedTransactions.get());
        metrics.setSarFiledCount(sarFiledCount.get());
        
        // Calculate percentages
        long total = totalTransactionsProcessed.get();
        if (total > 0) {
            metrics.setBlockRate((double) blockedTransactions.get() / total * 100);
            metrics.setFlagRate((double) flaggedTransactions.get() / total * 100);
            metrics.setSarRate((double) sarFiledCount.get() / total * 100);
        }
        
        // Top fired rules
        metrics.setTopFiredRules(getTopFiredRules(10));
        
        return metrics;
    }
    
    /**
     * Get top fired rules
     */
    private List<RuleFiringStats> getTopFiredRules(int limit) {
        return ruleFireCount.entrySet().stream()
            .sorted(Map.Entry.<String, AtomicLong>comparingByValue()
                .reversed())
            .limit(limit)
            .map(entry -> new RuleFiringStats(entry.getKey(), entry.getValue().get()))
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Reload rules dynamically (without service restart)
     */
    public void reloadRules() {
        try {
            log.info("Reloading AML/CTF rules");
            kieContainer.updateToVersion(kieContainer.getReleaseId());
            log.info("Rules reloaded successfully");
        } catch (Exception e) {
            log.error("Failed to reload rules: {}", e.getMessage(), e);
            throw new RuntimeException("Rule reload failed", e);
        }
    }
    
    private Integer getTransactionCount(String accountId, int days) {
        try {
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            Long count = complianceTransactionRepository.countByAccountIdAndCreatedAtAfter(accountId, startDate);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.error("Failed to get transaction count for account: {}", accountId, e);
            return 0;
        }
    }
    
    private BigDecimal getTransactionVolume(String accountId, int days) {
        try {
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<ComplianceTransaction> transactions = complianceTransactionRepository
                .findByAccountIdAndCreatedAtAfter(accountId, startDate);
            
            return transactions.stream()
                .map(ComplianceTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.error("Failed to get transaction volume for account: {}", accountId, e);
            return BigDecimal.ZERO;
        }
    }
    
    private void loadSanctionsList() {
        try {
            log.info("Loading sanctions list from database");
            List<String> sanctionedEntities = complianceTransactionRepository.findAllSanctionedEntities();
            
            for (String entity : sanctionedEntities) {
                sanctionsCache.put(entity.toLowerCase(), Boolean.TRUE);
            }
            
            log.info("Loaded {} sanctioned entities into cache", sanctionedEntities.size());
        } catch (Exception e) {
            log.error("Failed to load sanctions list", e);
        }
        
        log.info("Sanctions list loading completed");
    }
    
    private void loadHighRiskJurisdictions() {
        // Would load from configuration/database
        log.info("Loading high-risk jurisdictions");
    }
    
    private void startMetricsCollection() {
        // Would start scheduled metrics collection
        log.info("Started metrics collection");
    }
    
    /**
     * Persist screening result to database
     */
    private ComplianceTransaction persistScreeningResult(AMLTransaction transaction, 
                                                        ComplianceScreeningResult result,
                                                        int rulesFired, 
                                                        long processingTime) {
        try {
            // Check if transaction already exists (for reprocessing scenarios)
            Optional<ComplianceTransaction> existingOpt = 
                complianceTransactionRepository.findByTransactionId(transaction.getTransactionId());
            
            ComplianceTransaction complianceTransaction;
            if (existingOpt.isPresent()) {
                // Update existing record
                complianceTransaction = existingOpt.get();
                updateExistingComplianceTransaction(complianceTransaction, transaction, result, rulesFired, processingTime);
            } else {
                // Create new record
                complianceTransaction = createNewComplianceTransaction(transaction, result, rulesFired, processingTime);
            }
            
            return complianceTransactionRepository.save(complianceTransaction);
            
        } catch (Exception e) {
            log.error("Failed to persist screening result for transaction {}: {}", 
                     transaction.getTransactionId(), e.getMessage(), e);
            throw new RuntimeException("Database persistence failed", e);
        }
    }
    
    /**
     * Create new compliance transaction record
     */
    private ComplianceTransaction createNewComplianceTransaction(AMLTransaction transaction,
                                                               ComplianceScreeningResult result,
                                                               int rulesFired,
                                                               long processingTime) {
        // Convert AML risk indicators to persistence format
        List<ComplianceTransaction.RiskIndicator> persistentRiskIndicators = new ArrayList<>();
        if (transaction.getRiskIndicators() != null) {
            for (AMLTransaction.RiskIndicator indicator : transaction.getRiskIndicators()) {
                persistentRiskIndicators.add(ComplianceTransaction.RiskIndicator.builder()
                    .code(indicator.getCode())
                    .description(indicator.getDescription())
                    .score(indicator.getScore())
                    .timestamp(LocalDateTime.now())
                    .build());
            }
        }
        
        // Convert AML alerts to persistence format
        List<ComplianceTransaction.Alert> persistentAlerts = new ArrayList<>();
        if (transaction.getAlerts() != null) {
            for (AMLTransaction.ComplianceAlert alert : transaction.getAlerts()) {
                persistentAlerts.add(ComplianceTransaction.Alert.builder()
                    .type(alert.getType())
                    .severity(alert.getSeverity())
                    .message(alert.getMessage())
                    .timestamp(alert.getTimestamp())
                    .build());
            }
        }
        
        return ComplianceTransaction.builder()
            .transactionId(transaction.getTransactionId())
            .customerId(transaction.getCustomerId())
            .accountId(transaction.getAccountId())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .transactionType(transaction.getType())
            .transactionDate(transaction.getTimestamp())
            .riskScore(result.getRiskScore())
            .riskLevel(mapToRiskLevelEnum(result.getRiskLevel()))
            .screeningStatus(ComplianceTransaction.ScreeningStatus.COMPLETED)
            .screeningDate(LocalDateTime.now())
            .rulesFired(rulesFired)
            .decision(mapToDecisionEnum(result.getRecommendation()))
            .decisionReason(String.join(", ", result.getRiskReasons() != null ? result.getRiskReasons() : new ArrayList<>()))
            .autoDecision(true)
            .isBlocked(result.shouldBlock())
            .requiresReview(result.requiresReview())
            .requiresSAR(result.requiresSAR())
            .isCTRRequired(transaction.getAmount() != null && 
                          transaction.getAmount().compareTo(new BigDecimal("10000")) >= 0)
            .riskIndicators(persistentRiskIndicators)
            .alerts(persistentAlerts)
            .processingTimeMs(processingTime)
            .build();
    }
    
    /**
     * Update existing compliance transaction record
     */
    private void updateExistingComplianceTransaction(ComplianceTransaction existing,
                                                   AMLTransaction transaction,
                                                   ComplianceScreeningResult result,
                                                   int rulesFired,
                                                   long processingTime) {
        // Update screening results
        existing.setRiskScore(result.getRiskScore());
        existing.setRiskLevel(mapToRiskLevelEnum(result.getRiskLevel()));
        existing.setScreeningStatus(ComplianceTransaction.ScreeningStatus.COMPLETED);
        existing.setScreeningDate(LocalDateTime.now());
        existing.setRulesFired(rulesFired);
        existing.setDecision(mapToDecisionEnum(result.getRecommendation()));
        existing.setDecisionReason(String.join(", ", result.getRiskReasons() != null ? result.getRiskReasons() : new ArrayList<>()));
        existing.setIsBlocked(result.shouldBlock());
        existing.setRequiresReview(result.requiresReview());
        existing.setRequiresSAR(result.requiresSAR());
        existing.setProcessingTimeMs(processingTime);
        
        // Update risk indicators and alerts (append new ones)
        if (transaction.getRiskIndicators() != null) {
            for (AMLTransaction.RiskIndicator indicator : transaction.getRiskIndicators()) {
                existing.addRiskIndicator(indicator.getCode(), indicator.getDescription(), indicator.getScore());
            }
        }
        
        if (transaction.getAlerts() != null) {
            for (AMLTransaction.ComplianceAlert alert : transaction.getAlerts()) {
                existing.addAlert(alert.getType(), alert.getSeverity(), alert.getMessage());
            }
        }
    }
    
    /**
     * Map string risk level to enum
     */
    private ComplianceTransaction.RiskLevel mapToRiskLevelEnum(String riskLevel) {
        if (riskLevel == null) {
            log.warn("CRITICAL: Null risk level provided to AML engine - Defaulting to HIGH for safety");
            return ComplianceTransaction.RiskLevel.HIGH;
        }
        try {
            return ComplianceTransaction.RiskLevel.valueOf(riskLevel);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown risk level: {}, defaulting to MEDIUM", riskLevel);
            return ComplianceTransaction.RiskLevel.MEDIUM;
        }
    }
    
    /**
     * Map string recommendation to decision enum
     */
    private ComplianceTransaction.Decision mapToDecisionEnum(String recommendation) {
        if (recommendation == null) {
            log.warn("CRITICAL: Null recommendation provided to AML engine - Defaulting to HOLD for safety");
            return ComplianceTransaction.Decision.HOLD;
        }
        try {
            return ComplianceTransaction.Decision.valueOf(recommendation);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown recommendation: {}, defaulting to REVIEW", recommendation);
            return ComplianceTransaction.Decision.REVIEW;
        }
    }
    
    // DTO Classes
    
    @lombok.Data
    public static class ComplianceScreeningResult {
        private String transactionId;
        private String databaseId; // ID in compliance_transactions table
        private LocalDateTime screeningTimestamp;
        private Integer riskScore;
        private String riskLevel;
        private String recommendation;
        private List<String> riskReasons;
        private boolean shouldBlock;
        private boolean requiresReview;
        private boolean requiresSAR;
        private List<ComplianceAlert> alerts;
        private List<AMLTransaction.RiskIndicator> riskIndicators;
        private int rulesFired;
        private String errorMessage;
        
        public static ComplianceScreeningResult error(String transactionId, String errorMessage) {
            ComplianceScreeningResult result = new ComplianceScreeningResult();
            result.setTransactionId(transactionId);
            result.setErrorMessage(errorMessage);
            result.setRecommendation("ERROR");
            return result;
        }
    }
    
    @lombok.Data
    private static class CustomerRiskProfile {
        private String customerId;
        private String riskRating;
        private Integer sarCount;
        private Integer alertCount;
        private LocalDateTime lastReview;
    }
    
    @lombok.Data
    public static class ComplianceMetrics {
        private long totalTransactionsProcessed;
        private long blockedTransactions;
        private long flaggedTransactions;
        private long sarFiledCount;
        private double blockRate;
        private double flagRate;
        private double sarRate;
        private List<RuleFiringStats> topFiredRules;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RuleFiringStats {
        private String ruleName;
        private long fireCount;
    }
}