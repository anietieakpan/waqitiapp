package com.waqiti.compliance.service.impl;

import com.waqiti.compliance.service.AMLTransactionMonitoringService;
import com.waqiti.compliance.model.AMLMonitoringResult;
import com.waqiti.compliance.model.AMLRuleViolation;
import com.waqiti.compliance.model.AMLTransaction;
import com.waqiti.compliance.repository.AMLTransactionRepository;
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.events.TransactionBlockEvent;
import com.waqiti.common.events.SarFilingRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AML Transaction Monitoring Service Implementation
 * 
 * CRITICAL COMPLIANCE COMPONENT: Real-time money laundering detection
 * REGULATORY IMPACT: Prevents AML violations and regulatory penalties
 * 
 * Implements comprehensive AML monitoring rules:
 * - Structuring/Smurfing detection
 * - Velocity checks
 * - Pattern analysis
 * - Geographic risk assessment
 * - Threshold monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AMLTransactionMonitoringServiceImpl implements AMLTransactionMonitoringService {
    
    private final AMLTransactionRepository transactionRepository;
    private final ComprehensiveAuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // AML Thresholds (configurable in production)
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("10000");
    private static final BigDecimal WEEKLY_LIMIT = new BigDecimal("50000");
    private static final BigDecimal MONTHLY_LIMIT = new BigDecimal("100000");
    private static final BigDecimal STRUCTURING_THRESHOLD = new BigDecimal("9500"); // Just under $10k
    private static final int MAX_TRANSACTIONS_PER_DAY = 10;
    private static final int MAX_TRANSACTIONS_PER_HOUR = 5;
    
    // High-risk countries for AML
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "AF", "AL", "BS", "BB", "BW", "KH", "GH", "IS", "MN", "PA", 
        "PK", "LK", "SY", "TT", "UG", "VU", "YE", "ZW"
    );
    
    @Override
    public AMLMonitoringResult monitorTransaction(UUID transactionId, UUID userId, BigDecimal amount,
                                                 String currency, String transactionType,
                                                 String sourceAccount, String destinationAccount) {
        log.info("AML: Monitoring transaction {} for user {} - Amount: {} {}", 
            transactionId, userId, amount, currency);
        
        String monitoringId = UUID.randomUUID().toString();
        List<AMLRuleViolation> violations = new ArrayList<>();
        
        try {
            // Check structuring patterns
            if (detectStructuring(userId, amount, 24)) {
                violations.add(createStructuringViolation(userId, amount));
            }
            
            // Check velocity rules
            violations.addAll(checkVelocityRules(userId, amount));
            
            // Check cumulative thresholds
            if (checkCumulativeThresholds(userId, "DAILY")) {
                violations.add(createThresholdViolation("DAILY", userId, amount));
            }
            
            // Check for rapid movement
            if (detectRapidMovement(userId, 1)) {
                violations.add(createRapidMovementViolation(userId));
            }
            
            // Check round amounts
            if (isRoundAmountSuspicious(amount)) {
                violations.add(createRoundAmountViolation(amount));
            }
            
            // Calculate risk score
            double riskScore = calculateTransactionRiskScore(violations, amount, transactionType);
            
            // Build monitoring result
            AMLMonitoringResult result = buildMonitoringResult(
                monitoringId, transactionId, userId, amount, currency,
                transactionType, sourceAccount, destinationAccount, violations, riskScore
            );
            
            // Audit the monitoring
            auditMonitoring(userId, transactionId, result);
            
            // Take action if violations detected
            if (result.shouldBlockTransaction()) {
                blockTransactionForAML(transactionId, userId, amount, currency, result);
            }
            
            if (result.requiresSARFiling()) {
                initiateSARFiling(userId, transactionId, amount, currency, result);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error monitoring transaction {} for AML", transactionId, e);
            return createErrorResult(monitoringId, transactionId, userId, amount, currency);
        }
    }
    
    @Override
    public boolean detectStructuring(UUID userId, BigDecimal amount, int timeWindow) {
        log.debug("AML: Checking for structuring patterns for user {}", userId);
        
        LocalDateTime startTime = LocalDateTime.now().minusHours(timeWindow);
        
        // Get recent transactions
        List<AMLTransaction> recentTransactions = transactionRepository
            .findByUserIdAndCreatedAtAfter(userId, startTime);
        
        // Check for multiple transactions just under reporting threshold
        long suspiciousCount = recentTransactions.stream()
            .filter(t -> t.getAmount().compareTo(STRUCTURING_THRESHOLD) >= 0 && 
                        t.getAmount().compareTo(DAILY_LIMIT) < 0)
            .count();
        
        // Check if current transaction + recent transactions indicate structuring
        BigDecimal totalAmount = amount;
        for (AMLTransaction transaction : recentTransactions) {
            totalAmount = totalAmount.add(transaction.getAmount());
        }
        
        // Structuring detected if:
        // 1. Multiple transactions just under threshold
        // 2. Total would exceed threshold but individual transactions don't
        boolean structuringDetected = suspiciousCount >= 2 || 
            (totalAmount.compareTo(DAILY_LIMIT) > 0 && 
             amount.compareTo(DAILY_LIMIT) < 0 &&
             recentTransactions.size() >= 2);
        
        if (structuringDetected) {
            log.warn("AML ALERT: Potential structuring detected for user {}", userId);
        }
        
        return structuringDetected;
    }
    
    @Override
    public List<AMLRuleViolation> checkVelocityRules(UUID userId, BigDecimal currentAmount) {
        log.debug("AML: Checking velocity rules for user {}", userId);
        
        List<AMLRuleViolation> violations = new ArrayList<>();
        
        // Check hourly velocity
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long hourlyCount = transactionRepository.countByUserIdAndCreatedAtAfter(userId, oneHourAgo);
        
        if (hourlyCount >= MAX_TRANSACTIONS_PER_HOUR) {
            violations.add(AMLRuleViolation.builder()
                .violationId(UUID.randomUUID().toString())
                .ruleId("VEL_001")
                .ruleName("Hourly Transaction Velocity")
                .ruleType(AMLRuleViolation.RuleType.VELOCITY)
                .severity(AMLRuleViolation.Severity.HIGH)
                .description("Exceeded maximum transactions per hour")
                .violationReason(String.format("%d transactions in last hour (max: %d)", 
                    hourlyCount, MAX_TRANSACTIONS_PER_HOUR))
                .threshold(new BigDecimal(MAX_TRANSACTIONS_PER_HOUR))
                .detectedAt(LocalDateTime.now())
                .requiresImmediateAction(true)
                .build());
        }
        
        // Check daily velocity
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        long dailyCount = transactionRepository.countByUserIdAndCreatedAtAfter(userId, oneDayAgo);
        
        if (dailyCount >= MAX_TRANSACTIONS_PER_DAY) {
            violations.add(AMLRuleViolation.builder()
                .violationId(UUID.randomUUID().toString())
                .ruleId("VEL_002")
                .ruleName("Daily Transaction Velocity")
                .ruleType(AMLRuleViolation.RuleType.VELOCITY)
                .severity(AMLRuleViolation.Severity.MEDIUM)
                .description("Exceeded maximum transactions per day")
                .violationReason(String.format("%d transactions in last 24 hours (max: %d)", 
                    dailyCount, MAX_TRANSACTIONS_PER_DAY))
                .threshold(new BigDecimal(MAX_TRANSACTIONS_PER_DAY))
                .detectedAt(LocalDateTime.now())
                .build());
        }
        
        return violations;
    }
    
    @Override
    public boolean detectRapidMovement(UUID userId, int timeWindow) {
        log.debug("AML: Checking for rapid fund movement for user {}", userId);
        
        LocalDateTime startTime = LocalDateTime.now().minusHours(timeWindow);
        
        // Get deposits and withdrawals in time window
        List<AMLTransaction> transactions = transactionRepository
            .findByUserIdAndCreatedAtAfter(userId, startTime);
        
        // Check for deposit followed by immediate withdrawal pattern
        boolean hasDeposit = transactions.stream()
            .anyMatch(t -> "DEPOSIT".equals(t.getTransactionType()));
        boolean hasWithdrawal = transactions.stream()
            .anyMatch(t -> "WITHDRAWAL".equals(t.getTransactionType()) || 
                          "TRANSFER_OUT".equals(t.getTransactionType()));
        
        if (hasDeposit && hasWithdrawal && transactions.size() >= 2) {
            // Check if withdrawal happened shortly after deposit
            Optional<LocalDateTime> firstDeposit = transactions.stream()
                .filter(t -> "DEPOSIT".equals(t.getTransactionType()))
                .map(AMLTransaction::getCreatedAt)
                .min(LocalDateTime::compareTo);
            
            Optional<LocalDateTime> firstWithdrawal = transactions.stream()
                .filter(t -> "WITHDRAWAL".equals(t.getTransactionType()) || 
                           "TRANSFER_OUT".equals(t.getTransactionType()))
                .map(AMLTransaction::getCreatedAt)
                .min(LocalDateTime::compareTo);
            
            if (firstDeposit.isPresent() && firstWithdrawal.isPresent()) {
                long minutesBetween = ChronoUnit.MINUTES.between(firstDeposit.get(), firstWithdrawal.get());
                if (minutesBetween < 30 && minutesBetween >= 0) {
                    log.warn("AML ALERT: Rapid fund movement detected for user {}", userId);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    @Override
    public boolean isRoundAmountSuspicious(BigDecimal amount) {
        // Check if amount is a suspicious round number
        BigDecimal[] suspiciousAmounts = {
            new BigDecimal("1000"),
            new BigDecimal("2000"),
            new BigDecimal("3000"),
            new BigDecimal("5000"),
            new BigDecimal("9000"),
            new BigDecimal("9500"),
            new BigDecimal("9900"),
            new BigDecimal("10000"),
            new BigDecimal("50000"),
            new BigDecimal("100000")
        };
        
        for (BigDecimal suspiciousAmount : suspiciousAmounts) {
            if (amount.compareTo(suspiciousAmount) == 0) {
                return true;
            }
        }
        
        // Check if amount ends in multiple zeros (e.g., 4500, 7800)
        BigDecimal remainder = amount.remainder(new BigDecimal("100"));
        return remainder.compareTo(BigDecimal.ZERO) == 0 && amount.compareTo(new BigDecimal("1000")) > 0;
    }
    
    @Override
    public boolean detectDormantAccountReactivation(UUID userId, LocalDateTime lastActivityDate) {
        if (lastActivityDate == null) {
            return false;
        }
        
        long daysSinceLastActivity = ChronoUnit.DAYS.between(lastActivityDate, LocalDateTime.now());
        
        // Account dormant if no activity for 90+ days
        if (daysSinceLastActivity > 90) {
            log.warn("AML ALERT: Dormant account reactivation for user {} - {} days since last activity", 
                userId, daysSinceLastActivity);
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean checkCumulativeThresholds(UUID userId, String period) {
        log.debug("AML: Checking {} cumulative thresholds for user {}", period, userId);
        
        LocalDateTime startTime;
        BigDecimal threshold;
        
        switch (period.toUpperCase()) {
            case "DAILY":
                startTime = LocalDateTime.now().minusDays(1);
                threshold = DAILY_LIMIT;
                break;
            case "WEEKLY":
                startTime = LocalDateTime.now().minusWeeks(1);
                threshold = WEEKLY_LIMIT;
                break;
            case "MONTHLY":
                startTime = LocalDateTime.now().minusMonths(1);
                threshold = MONTHLY_LIMIT;
                break;
            default:
                return false;
        }
        
        BigDecimal totalAmount = transactionRepository
            .sumAmountByUserIdAndCreatedAtAfter(userId, startTime);
        
        boolean exceeded = totalAmount != null && totalAmount.compareTo(threshold) > 0;
        
        if (exceeded) {
            log.warn("AML ALERT: {} threshold exceeded for user {} - Total: {}, Threshold: {}", 
                period, userId, totalAmount, threshold);
        }
        
        return exceeded;
    }
    
    @Override
    public double detectUnusualPatterns(UUID userId, String transactionType, BigDecimal amount) {
        // Get user's transaction history
        List<AMLTransaction> history = transactionRepository
            .findByUserIdAndCreatedAtAfter(userId, LocalDateTime.now().minusMonths(3));
        
        if (history.isEmpty()) {
            // New user, moderate risk
            return 0.5;
        }
        
        // Calculate average transaction amount
        BigDecimal avgAmount = history.stream()
            .map(AMLTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal(history.size()), RoundingMode.HALF_UP);
        
        // Check if current amount is significantly different from average
        BigDecimal deviation = amount.subtract(avgAmount).abs();
        BigDecimal deviationPercent = avgAmount.compareTo(BigDecimal.ZERO) > 0 ?
            deviation.divide(avgAmount, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        
        // Calculate pattern score based on deviation
        double patternScore = Math.min(deviationPercent.doubleValue(), 1.0);
        
        // Check for unusual transaction type
        long typeCount = history.stream()
            .filter(t -> transactionType.equals(t.getTransactionType()))
            .count();
        
        if (typeCount == 0 && !history.isEmpty()) {
            // First time using this transaction type
            patternScore = Math.min(patternScore + 0.3, 1.0);
        }
        
        return patternScore;
    }
    
    @Override
    public double assessGeographicRisk(String sourceCountry, String destinationCountry, BigDecimal amount) {
        double riskScore = 0.0;
        
        // Check if either country is high-risk
        if (HIGH_RISK_COUNTRIES.contains(sourceCountry)) {
            riskScore += 0.5;
        }
        if (HIGH_RISK_COUNTRIES.contains(destinationCountry)) {
            riskScore += 0.5;
        }
        
        // Additional risk for large amounts to/from high-risk countries
        if (riskScore > 0 && amount.compareTo(new BigDecimal("5000")) > 0) {
            riskScore = Math.min(riskScore + 0.3, 1.0);
        }
        
        return riskScore;
    }
    
    @Override
    public double calculateUserAMLRiskScore(UUID userId) {
        // Get user's recent transaction history
        List<AMLTransaction> recentTransactions = transactionRepository
            .findByUserIdAndCreatedAtAfter(userId, LocalDateTime.now().minusMonths(1));
        
        double riskScore = 0.0;
        
        // Factor 1: Transaction frequency
        if (recentTransactions.size() > 50) {
            riskScore += 0.2;
        }
        
        // Factor 2: Total volume
        BigDecimal totalVolume = recentTransactions.stream()
            .map(AMLTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalVolume.compareTo(new BigDecimal("100000")) > 0) {
            riskScore += 0.3;
        }
        
        // Factor 3: International transactions
        long internationalCount = recentTransactions.stream()
            .filter(t -> t.isInternational())
            .count();
        
        if (internationalCount > 5) {
            riskScore += 0.2;
        }
        
        // Factor 4: Round amounts
        long roundAmountCount = recentTransactions.stream()
            .filter(t -> isRoundAmountSuspicious(t.getAmount()))
            .count();
        
        if (roundAmountCount > 3) {
            riskScore += 0.3;
        }
        
        return Math.min(riskScore, 1.0);
    }
    
    @Override
    public List<AMLMonitoringResult> getUserTransactionHistory(UUID userId, LocalDateTime startDate, 
                                                              LocalDateTime endDate) {
        List<AMLTransaction> transactions = transactionRepository
            .findByUserIdAndCreatedAtBetween(userId, startDate, endDate);
        
        return transactions.stream()
            .map(t -> monitorTransaction(
                t.getTransactionId(), t.getUserId(), t.getAmount(),
                t.getCurrency(), t.getTransactionType(),
                t.getSourceAccount(), t.getDestinationAccount()
            ))
            .collect(Collectors.toList());
    }
    
    // Private helper methods
    
    private AMLRuleViolation createStructuringViolation(UUID userId, BigDecimal amount) {
        return AMLRuleViolation.builder()
            .violationId(UUID.randomUUID().toString())
            .ruleId("STR_001")
            .ruleName("Potential Structuring/Smurfing")
            .ruleType(AMLRuleViolation.RuleType.STRUCTURING)
            .severity(AMLRuleViolation.Severity.CRITICAL)
            .description("Multiple transactions structured to avoid reporting threshold")
            .violationReason("Pattern consistent with structuring to avoid CTR filing")
            .violationAmount(amount)
            .threshold(DAILY_LIMIT)
            .detectedAt(LocalDateTime.now())
            .requiresImmediateAction(true)
            .requiresSAR(true)
            .regulatoryReference("31 CFR 1020.320")
            .build();
    }
    
    private AMLRuleViolation createThresholdViolation(String period, UUID userId, BigDecimal amount) {
        return AMLRuleViolation.builder()
            .violationId(UUID.randomUUID().toString())
            .ruleId("THR_" + period)
            .ruleName(period + " Threshold Exceeded")
            .ruleType(AMLRuleViolation.RuleType.CUMULATIVE_THRESHOLD)
            .severity(AMLRuleViolation.Severity.HIGH)
            .description(period + " transaction limit exceeded")
            .violationAmount(amount)
            .threshold("DAILY".equals(period) ? DAILY_LIMIT : 
                      "WEEKLY".equals(period) ? WEEKLY_LIMIT : MONTHLY_LIMIT)
            .detectedAt(LocalDateTime.now())
            .requiresSAR("DAILY".equals(period))
            .build();
    }
    
    private AMLRuleViolation createRapidMovementViolation(UUID userId) {
        return AMLRuleViolation.builder()
            .violationId(UUID.randomUUID().toString())
            .ruleId("RPD_001")
            .ruleName("Rapid Fund Movement")
            .ruleType(AMLRuleViolation.RuleType.RAPID_MOVEMENT)
            .severity(AMLRuleViolation.Severity.HIGH)
            .description("Funds deposited and immediately withdrawn")
            .violationReason("Pattern consistent with layering stage of money laundering")
            .detectedAt(LocalDateTime.now())
            .requiresAccountReview(true)
            .build();
    }
    
    private AMLRuleViolation createRoundAmountViolation(BigDecimal amount) {
        return AMLRuleViolation.builder()
            .violationId(UUID.randomUUID().toString())
            .ruleId("RND_001")
            .ruleName("Suspicious Round Amount")
            .ruleType(AMLRuleViolation.RuleType.ROUND_AMOUNT)
            .severity(AMLRuleViolation.Severity.MEDIUM)
            .description("Transaction uses suspicious round amount")
            .violationAmount(amount)
            .detectedAt(LocalDateTime.now())
            .build();
    }
    
    private double calculateTransactionRiskScore(List<AMLRuleViolation> violations, 
                                                BigDecimal amount, String transactionType) {
        if (violations.isEmpty()) {
            return 0.1;
        }
        
        double baseScore = 0.3;
        
        // Add score based on violation severity
        for (AMLRuleViolation violation : violations) {
            switch (violation.getSeverity()) {
                case CRITICAL:
                    baseScore += 0.4;
                    break;
                case HIGH:
                    baseScore += 0.3;
                    break;
                case MEDIUM:
                    baseScore += 0.2;
                    break;
                case LOW:
                    baseScore += 0.1;
                    break;
            }
        }
        
        // Additional factors
        if (amount.compareTo(new BigDecimal("50000")) > 0) {
            baseScore += 0.2;
        }
        
        return Math.min(baseScore, 1.0);
    }
    
    private AMLMonitoringResult buildMonitoringResult(String monitoringId, UUID transactionId, UUID userId,
                                                     BigDecimal amount, String currency, String transactionType,
                                                     String sourceAccount, String destinationAccount,
                                                     List<AMLRuleViolation> violations, double riskScore) {
        
        AMLMonitoringResult.RiskLevel riskLevel;
        if (riskScore > 0.8) {
            riskLevel = AMLMonitoringResult.RiskLevel.CRITICAL;
        } else if (riskScore > 0.6) {
            riskLevel = AMLMonitoringResult.RiskLevel.HIGH;
        } else if (riskScore > 0.3) {
            riskLevel = AMLMonitoringResult.RiskLevel.MEDIUM;
        } else {
            riskLevel = AMLMonitoringResult.RiskLevel.LOW;
        }
        
        boolean hasStructuring = violations.stream()
            .anyMatch(v -> v.getRuleType() == AMLRuleViolation.RuleType.STRUCTURING);
        
        return AMLMonitoringResult.builder()
            .monitoringId(monitoringId)
            .transactionId(transactionId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .transactionType(transactionType)
            .sourceAccount(sourceAccount)
            .destinationAccount(destinationAccount)
            .monitoredAt(LocalDateTime.now())
            .hasViolations(!violations.isEmpty())
            .violations(violations)
            .riskScore(riskScore)
            .riskLevel(riskLevel)
            .structuringDetected(hasStructuring)
            .requiresImmediateAction(riskLevel == AMLMonitoringResult.RiskLevel.CRITICAL)
            .requiresSARFiling(hasStructuring || riskScore > 0.8)
            .recommendedAction(riskLevel == AMLMonitoringResult.RiskLevel.CRITICAL ? "BLOCK_AND_REPORT" :
                             riskLevel == AMLMonitoringResult.RiskLevel.HIGH ? "REVIEW_URGENTLY" : "MONITOR")
            .build();
    }
    
    private AMLMonitoringResult createErrorResult(String monitoringId, UUID transactionId, 
                                                 UUID userId, BigDecimal amount, String currency) {
        return AMLMonitoringResult.builder()
            .monitoringId(monitoringId)
            .transactionId(transactionId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .monitoredAt(LocalDateTime.now())
            .hasViolations(false)
            .riskLevel(AMLMonitoringResult.RiskLevel.MEDIUM)
            .requiresImmediateAction(false)
            .complianceNotes("Error during AML monitoring - manual review required")
            .build();
    }
    
    private void auditMonitoring(UUID userId, UUID transactionId, AMLMonitoringResult result) {
        String eventType = result.isHasViolations() ? "AML_VIOLATION_DETECTED" : "AML_MONITORING_CLEAR";
        
        auditService.auditHighRiskOperation(
            eventType,
            userId.toString(),
            String.format("AML monitoring completed - Risk: %s, Score: %.2f", 
                result.getRiskLevel(), result.getRiskScore()),
            Map.of(
                "transactionId", transactionId,
                "monitoringId", result.getMonitoringId(),
                "hasViolations", result.isHasViolations(),
                "riskScore", result.getRiskScore(),
                "violations", result.getViolations() != null ? result.getViolations().size() : 0
            )
        );
    }
    
    private void blockTransactionForAML(UUID transactionId, UUID userId, BigDecimal amount,
                                       String currency, AMLMonitoringResult result) {
        log.error("AML: Blocking transaction {} due to AML violations", transactionId);
        
        // Create transaction block event
        TransactionBlockEvent blockEvent = TransactionBlockEvent.builder()
            .transactionId(transactionId)
            .userId(userId)
            .blockReason(TransactionBlockEvent.BlockReason.AML_PATTERN_DETECTION)
            .severity(TransactionBlockEvent.BlockSeverity.HIGH)
            .blockDescription("Transaction blocked due to AML rule violations")
            .complianceViolations(result.getViolations().stream()
                .map(AMLRuleViolation::getRuleName)
                .collect(Collectors.toList()))
            .transactionAmount(amount)
            .currency(currency)
            .blockedAt(LocalDateTime.now())
            .amlRuleViolated(result.getViolations().get(0).getRuleName())
            .riskScore(result.getRiskScore())
            .blockingSystem("AML_MONITORING_SERVICE")
            .requiresManualReview(true)
            .notifyRegulators(result.requiresSARFiling())
            .build();
        
        // Publish to Kafka
        kafkaTemplate.send("transaction-blocks", blockEvent);
        
        log.error("AML: Transaction {} blocked for AML violations", transactionId);
    }
    
    private void initiateSARFiling(UUID userId, UUID transactionId, BigDecimal amount,
                                  String currency, AMLMonitoringResult result) {
        log.error("AML: Initiating SAR filing for transaction {} - User: {}", transactionId, userId);
        
        // Get the most severe violation
        AMLRuleViolation primaryViolation = result.getViolations().stream()
            .max(Comparator.comparing(AMLRuleViolation::getSeverity))
            .orElse(result.getViolations().get(0));
        
        // Create SAR filing request
        SarFilingRequestEvent sarEvent = SarFilingRequestEvent.builder()
            .userId(userId)
            .category(mapViolationToSarCategory(primaryViolation.getRuleType()))
            .priority(result.isStructuringDetected() ? 
                     SarFilingRequestEvent.SarPriority.IMMEDIATE : 
                     SarFilingRequestEvent.SarPriority.HIGH)
            .violationType(primaryViolation.getRuleName())
            .suspiciousActivity(primaryViolation.getDescription())
            .totalSuspiciousAmount(amount)
            .currency(currency)
            .transactionCount(1)
            .activityStartDate(LocalDateTime.now().minusDays(30))
            .activityEndDate(LocalDateTime.now())
            .detectionMethod("AML_RULE_ENGINE")
            .detectionRuleId(primaryViolation.getRuleId())
            .riskScore(result.getRiskScore())
            .suspiciousTransactionIds(Arrays.asList(transactionId))
            .caseId(UUID.randomUUID().toString())
            .requestingSystem("AML_MONITORING_SERVICE")
            .requestedAt(LocalDateTime.now())
            .requiresImmediateFiling(result.isStructuringDetected())
            .build();
        
        // Publish to Kafka
        kafkaTemplate.send("sar-filing-requests", sarEvent);
        
        log.error("AML: SAR filing initiated for transaction {}", transactionId);
    }
    
    private SarFilingRequestEvent.SarCategory mapViolationToSarCategory(AMLRuleViolation.RuleType ruleType) {
        switch (ruleType) {
            case STRUCTURING:
                return SarFilingRequestEvent.SarCategory.STRUCTURING;
            case RAPID_MOVEMENT:
            case PATTERN_ANOMALY:
            case ROUND_AMOUNT:
                return SarFilingRequestEvent.SarCategory.MONEY_LAUNDERING;
            default:
                return SarFilingRequestEvent.SarCategory.OTHER_SUSPICIOUS_ACTIVITY;
        }
    }
}