package com.waqiti.common.fraud;

import com.waqiti.common.fraud.dto.TransactionBlockEvent;
import com.waqiti.common.fraud.model.*;
import com.waqiti.common.fraud.profiling.UserRiskProfile;
import com.waqiti.common.fraud.rules.*;

import com.waqiti.common.fraud.alert.FraudAlertService;
import com.waqiti.common.fraud.rules.FraudRuleViolation;
import com.waqiti.common.fraud.scoring.FraudScoringEngine;

import com.waqiti.common.fraud.rules.FraudRule;

import com.waqiti.common.kafka.KafkaProducerService;
import com.waqiti.common.monitoring.MetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Real-time fraud monitoring and alerting system
 * Analyzes transactions, user behavior, and system patterns to detect and prevent fraud
 * 
 * Features:
 * - Real-time transaction analysis
 * - ML-based fraud scoring
 * - Pattern detection and anomaly analysis
 * - Multi-level alerting system
 * - Automatic risk-based actions
 * - Compliance reporting integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeFraudMonitoringService {

    private final FraudScoringEngine fraudScoringEngine;
    private final FraudAlertService fraudAlertService;
    private final KafkaProducerService kafkaProducerService;
    private final MetricsService metricsService;
    private final MeterRegistry meterRegistry;
    
    private final List<FraudRule> fraudRules;
    private final Map<String, UserRiskProfile> userRiskProfiles = new ConcurrentHashMap<>();
    private final Map<String, TransactionPattern> recentPatterns = new ConcurrentHashMap<>();
    
    // Metrics
    private final Counter alertGeneratedCounter;
    private final Timer fraudAnalysisTimer;
    private final Counter blockActionCounter;
    
    public RealTimeFraudMonitoringService(
            FraudScoringEngine fraudScoringEngine,
            FraudAlertService fraudAlertService, 
            KafkaProducerService kafkaProducerService,
            MetricsService metricsService,
            MeterRegistry meterRegistry,
            List<FraudRule> fraudRules) {
        this.fraudScoringEngine = fraudScoringEngine;
        this.fraudAlertService = fraudAlertService;
        this.kafkaProducerService = kafkaProducerService;
        this.metricsService = metricsService;
        this.meterRegistry = meterRegistry;
        this.fraudRules = fraudRules;
        
        // Initialize metrics
        this.alertGeneratedCounter = Counter.builder("fraud.alerts.generated")
            .description("Number of fraud alerts generated")
            .register(meterRegistry);
        this.fraudAnalysisTimer = Timer.builder("fraud.analysis.duration")
            .description("Time taken for fraud analysis")
            .register(meterRegistry);
        this.blockActionCounter = Counter.builder("fraud.actions.block")
            .description("Number of blocking actions taken")
            .register(meterRegistry);
    }
    
    /**
     * Real-time transaction fraud analysis
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public CompletableFuture<FraudAnalysisResult> analyzeTransaction(TransactionEvent transactionEvent) {
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            
            try {
                log.debug("Starting fraud analysis for transaction: {}", transactionEvent.getTransactionId());
                
                // Create fraud context
                FraudContext fraudContext = buildFraudContext(transactionEvent);
                
                // Calculate fraud score using ML engine
                FraudScore fraudScore = fraudScoringEngine.calculateFraudScore(fraudContext);
                
                // Apply fraud rules
                List<FraudRuleViolation> ruleViolations = applyFraudRules(fraudContext);
                
                // Detect behavioral anomalies
                List<BehavioralAnomaly> anomalies = detectBehavioralAnomalies(fraudContext);
                
                // Check against known fraud patterns
                List<PatternMatch> patternMatches = checkFraudPatterns(fraudContext);
                
                // Determine overall risk level
                FraudRiskLevel riskLevel = determineRiskLevel(fraudScore, ruleViolations, anomalies, patternMatches);
                
                // Create analysis result
                FraudAnalysisResult result = FraudAnalysisResult.builder()
                    .transactionId(transactionEvent.getTransactionId())
                    .userId(transactionEvent.getUserId())
                    .fraudScore(fraudScore)
                    .riskLevel(riskLevel)
                    .ruleViolations(ruleViolations)
                    .behavioralAnomalies(anomalies)
                    .patternMatches(patternMatches)
                    .analysisTimestamp(LocalDateTime.now())
                    .build();
                
                // Take appropriate actions based on risk level
                CompletableFuture<Void> actionsResult = takeRiskBasedActions(result, fraudContext);
                
                // Update user risk profile
                updateUserRiskProfile(transactionEvent.getUserId(), result);
                
                // Update fraud patterns
                updateFraudPatterns(fraudContext);
                
                // Update metrics
                updateMetrics(result);
                
                log.info("Fraud analysis completed for transaction: {}, risk: {}, score: {}", 
                    transactionEvent.getTransactionId(), riskLevel, fraudScore.getScore());
                
                return result;
                
            } catch (Exception e) {
                log.error("Error during fraud analysis for transaction: {}", 
                    transactionEvent.getTransactionId(), e);
                    
                // Return safe default result
                return FraudAnalysisResult.builder()
                    .transactionId(transactionEvent.getTransactionId())
                    .userId(transactionEvent.getUserId())
                    .fraudScore(FraudScore.builder().score(0.0).confidence(0.0).build())
                    .riskLevel(FraudRiskLevel.UNKNOWN)
                    .ruleViolations(Collections.emptyList())
                    .behavioralAnomalies(Collections.emptyList())
                    .patternMatches(Collections.emptyList())
                    .analysisTimestamp(LocalDateTime.now())
                    .error("Analysis failed: " + e.getMessage())
                    .build();
            } finally {
                sample.stop(fraudAnalysisTimer);
            }
        });
    }
    
    /**
     * Build comprehensive fraud context from transaction event
     */
    private FraudContext buildFraudContext(TransactionEvent transactionEvent) {
        UserRiskProfile userProfile = userRiskProfiles.getOrDefault(
            transactionEvent.getUserId(),
            UserRiskProfile.createDefault(transactionEvent.getUserId())
        );

        // Build TransactionInfo from event
        BigDecimal amount = transactionEvent.getAmount();
        FraudContext.TransactionInfo transactionInfo = FraudContext.TransactionInfo.builder()
            .transactionId(transactionEvent.getTransactionId())
            .amount(amount != null ? amount : BigDecimal.ZERO)
            .currency(transactionEvent.getCurrency())
            .transactionType(transactionEvent.getTransactionType() != null ? transactionEvent.getTransactionType().name() : "UNKNOWN")
            .timestamp(transactionEvent.getTimestamp())
            .channel(transactionEvent.getChannel() != null ? transactionEvent.getChannel().name() : "UNKNOWN")
            .build();

        return FraudContext.builder()
            .transactionId(transactionEvent.getTransactionId())
            .userId(transactionEvent.getUserId())
            .transaction(transactionInfo)
            .build();
    }
    
    /**
     * Apply all fraud rules to the transaction
     */
    private List<FraudRuleViolation> applyFraudRules(FraudContext context) {
        // Convert FraudContext to Map for rule evaluation
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("transactionId", context.getTransactionId());
        contextMap.put("userId", context.getUserId());
        contextMap.put("amount", context.getAmount());
        contextMap.put("currency", context.getCurrency());

        return fraudRules.stream()
            .map(rule -> {
                RuleEvaluationResult result = rule.evaluate(contextMap);
                if (result != null && result.isViolated()) {
                    return FraudRuleViolation.builder()
                        .ruleId(rule.getRuleId())
                        .ruleName(rule.getRuleName())
                        .severity(convertSeverity(result.getSeverity()))
                        .description(result.getDescription())
                        .build();
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Detect behavioral anomalies in user patterns
     */
    private List<BehavioralAnomaly> detectBehavioralAnomalies(FraudContext context) {
        List<BehavioralAnomaly> anomalies = new ArrayList<>();
        UserRiskProfile profile = context.getUserRiskProfile();
        
        // Check for unusual transaction amounts
        if (isUnusualAmount(context.getAmount(), profile)) {
            anomalies.add(BehavioralAnomaly.builder()
                .type(AnomalyType.UNUSUAL_AMOUNT)
                .severity(AnomalySeverity.MEDIUM.name())
                .description("Transaction amount significantly deviates from user's typical pattern")
                .confidence(0.8)
                .build());
        }

        // Check for unusual time patterns
        if (isUnusualTime(context.getTimestamp(), profile)) {
            anomalies.add(BehavioralAnomaly.builder()
                .type(AnomalyType.UNUSUAL_TIME)
                .severity(AnomalySeverity.LOW.name())
                .description("Transaction occurred outside user's typical active hours")
                .confidence(0.6)
                .build());
        }

        // Check for unusual location
        if (isUnusualLocation(context.getLocation(), profile)) {
            anomalies.add(BehavioralAnomaly.builder()
                .type(AnomalyType.UNUSUAL_LOCATION)
                .severity(AnomalySeverity.HIGH.name())
                .description("Transaction from unusual geographic location")
                .confidence(0.9)
                .build());
        }

        // Check for velocity anomalies
        if (hasVelocityAnomaly(context)) {
            anomalies.add(BehavioralAnomaly.builder()
                .type(AnomalyType.HIGH_VELOCITY)
                .severity(AnomalySeverity.HIGH.name())
                .description("Unusually high transaction velocity detected")
                .confidence(0.95)
                .build());
        }

        // Check for device anomalies
        if (isUnusualDevice(context.getDeviceId(), profile)) {
            anomalies.add(BehavioralAnomaly.builder()
                .type(AnomalyType.NEW_DEVICE)
                .severity(AnomalySeverity.MEDIUM.name())
                .description("Transaction from new or unusual device")
                .confidence(0.7)
                .build());
        }
        
        return anomalies;
    }
    
    /**
     * Check transaction against known fraud patterns
     */
    private List<PatternMatch> checkFraudPatterns(FraudContext context) {
        List<PatternMatch> matches = new ArrayList<>();
        
        // Check for card testing patterns
        if (isCardTestingPattern(context)) {
            matches.add(PatternMatch.builder()
                .patternType(FraudPatternType.CARD_TESTING.name())
                .confidenceLevel(new BigDecimal("0.85"))
                .description("Multiple small transactions from same source")
                .build());
        }

        // Check for account takeover patterns
        if (isAccountTakeoverPattern(context)) {
            matches.add(PatternMatch.builder()
                .patternType(FraudPatternType.ACCOUNT_TAKEOVER.name())
                .confidenceLevel(new BigDecimal("0.9"))
                .description("Suspicious login and transaction pattern")
                .build());
        }

        // Check for money laundering patterns
        if (isMoneyLaunderingPattern(context)) {
            matches.add(PatternMatch.builder()
                .patternType(FraudPatternType.MONEY_LAUNDERING.name())
                .confidenceLevel(new BigDecimal("0.75"))
                .description("Potential structuring or layering detected")
                .build());
        }

        // Check for synthetic identity patterns
        if (isSyntheticIdentityPattern(context)) {
            matches.add(PatternMatch.builder()
                .patternType(FraudPatternType.SYNTHETIC_IDENTITY.name())
                .confidenceLevel(new BigDecimal("0.8"))
                .description("Indicators of synthetic identity fraud")
                .build());
        }
        
        return matches;
    }
    
    /**
     * Determine overall fraud risk level
     */
    private FraudRiskLevel determineRiskLevel(
            FraudScore fraudScore, 
            List<com.waqiti.common.fraud.rules.FraudRuleViolation> ruleViolations,
            List<BehavioralAnomaly> anomalies,
            List<PatternMatch> patternMatches) {
        
        double score = fraudScore.getScore();
        boolean hasHighSeverityViolations = ruleViolations.stream()
            .anyMatch(v -> v.getSeverity() != null && v.getSeverity().name().equals("HIGH"));
        boolean hasHighSeverityAnomalies = anomalies.stream()
            .anyMatch(a -> a.getSeverity() != null && a.getSeverity().name().equals("HIGH"));
        boolean hasHighConfidencePatterns = patternMatches.stream()
            .anyMatch(p -> p.getConfidenceLevel() != null && p.getConfidenceLevel().doubleValue() > 0.8);
        
        if (score > 0.8 || hasHighSeverityViolations || hasHighConfidencePatterns) {
            return FraudRiskLevel.HIGH;
        } else if (score > 0.5 || hasHighSeverityAnomalies || !ruleViolations.isEmpty()) {
            return FraudRiskLevel.MEDIUM;
        } else if (score > 0.2 || !anomalies.isEmpty()) {
            return FraudRiskLevel.LOW;
        } else {
            return FraudRiskLevel.MINIMAL;
        }
    }
    
    /**
     * Take risk-based actions
     */
    @Async
    private CompletableFuture<Void> takeRiskBasedActions(
            FraudAnalysisResult result, 
            FraudContext context) {
        
        return CompletableFuture.runAsync(() -> {
            try {
                switch (result.getRiskLevel()) {
                    case HIGH:
                        // Block transaction immediately
                        blockTransaction(result, context);
                        blockActionCounter.increment();
                        
                        // Generate high-priority alert
                        generateAlert(AlertLevel.CRITICAL, result, context);
                        
                        // Notify compliance team
                        notifyCompliance(result, context);
                        break;
                        
                    case MEDIUM:
                        // Hold transaction for review
                        holdTransactionForReview(result, context);
                        
                        // Generate medium-priority alert
                        generateAlert(AlertLevel.HIGH, result, context);
                        
                        // Request additional verification
                        requestAdditionalVerification(result, context);
                        break;
                        
                    case LOW:
                        // Allow but monitor closely
                        flagForMonitoring(result, context);
                        
                        // Generate low-priority alert
                        generateAlert(AlertLevel.MEDIUM, result, context);
                        break;
                        
                    case MINIMAL:
                        // Log for analytics only
                        logForAnalytics(result, context);
                        break;
                        
                    default:
                        log.warn("Unknown risk level: {} for transaction: {}", 
                            result.getRiskLevel(), result.getTransactionId());
                        break;
                }
                
            } catch (Exception e) {
                log.error("Error taking risk-based actions for transaction: {}", 
                    result.getTransactionId(), e);
            }
        });
    }
    
    /**
     * Block transaction immediately
     */
    private void blockTransaction(FraudAnalysisResult result, FraudContext context) {
        log.warn("BLOCKING transaction {} due to high fraud risk (score: {})", 
            result.getTransactionId(), result.getFraudScore().getScore());
            
        // Send blocking event
        TransactionBlockEvent blockEvent = TransactionBlockEvent.builder()
            .transactionId(result.getTransactionId())
            .userId(context.getUserId())
            .reason("High fraud risk detected")
            .fraudScore(result.getFraudScore().getScore())
            .timestamp(LocalDateTime.now())
            .build();
            
        kafkaProducerService.sendEvent("transaction-blocked", blockEvent);
        
        // Update metrics
        Counter.builder("fraud.detected")
            .tag("action", "blocked")
            .description("Number of fraud cases detected")
            .register(meterRegistry)
            .increment();
    }
    
    /**
     * Generate fraud alert
     */
    private void generateAlert(AlertLevel level, FraudAnalysisResult result, FraudContext context) {
        com.waqiti.common.fraud.alert.FraudAlert alert = com.waqiti.common.fraud.alert.FraudAlert.builder()
            .alertId(UUID.randomUUID().toString())
            .level(level)
            .transactionId(result.getTransactionId())
            .userId(context.getUserId())
            .riskScore(result.getFraudScore().getScore())
            .timestamp(LocalDateTime.now())
            .build();

        fraudAlertService.sendAlert(alert);
        // PRODUCTION FIX: Use meterRegistry with Tags for dynamic tag values
        meterRegistry.counter("fraud.alerts.generated", "level", level.name()).increment();
    }
    
    // Helper methods for anomaly detection
    
    private boolean isUnusualAmount(BigDecimal amount, UserRiskProfile profile) {
        if (profile.getTypicalTransactionAmount() == null) return false;
        
        double ratio = amount.divide(profile.getTypicalTransactionAmount(), 2, RoundingMode.HALF_UP)
            .doubleValue();
        return ratio > 5.0 || ratio < 0.1; // More than 5x or less than 10% of typical
    }
    
    private boolean isUnusualTime(LocalDateTime timestamp, UserRiskProfile profile) {
        int hour = timestamp.getHour();
        return profile.getTypicalActiveHours() != null && 
            !profile.getTypicalActiveHours().contains(hour);
    }
    
    private boolean isUnusualLocation(Location location, UserRiskProfile profile) {
        if (location == null || profile.getTypicalLocations() == null) return false;

        String currentLocationString = location.getCity() + ", " + location.getCountry();

        // Check if current location matches any typical locations
        return profile.getTypicalLocations().stream()
            .noneMatch(loc -> loc != null && loc.equalsIgnoreCase(currentLocationString));
    }
    
    private boolean hasVelocityAnomaly(FraudContext context) {
        long recentTransactionCount = context.getRecentTransactions().stream()
            .mapToLong(t -> ChronoUnit.MINUTES.between(t.getTimestamp(), context.getTimestamp()) < 60 ? 1 : 0)
            .sum();
        return recentTransactionCount > 10; // More than 10 transactions in last hour
    }
    
    private boolean isUnusualDevice(String deviceId, UserRiskProfile profile) {
        return deviceId != null && profile.getKnownDevices() != null &&
            !profile.getKnownDevices().contains(deviceId);
    }
    
    // Pattern detection methods
    
    private boolean isCardTestingPattern(FraudContext context) {
        // Logic to detect card testing patterns
        return context.getRecentTransactions().stream()
            .filter(t -> t.getAmount().compareTo(new BigDecimal("10")) < 0)
            .count() > 5; // More than 5 small transactions recently
    }
    
    private boolean isAccountTakeoverPattern(FraudContext context) {
        // Logic to detect account takeover patterns
        return isUnusualLocation(context.getLocation(), context.getUserRiskProfile()) &&
            isUnusualDevice(context.getDeviceId(), context.getUserRiskProfile());
    }
    
    private boolean isMoneyLaunderingPattern(FraudContext context) {
        // Logic to detect money laundering patterns
        BigDecimal recentTotal = context.getRecentTransactions().stream()
            .filter(t -> ChronoUnit.DAYS.between(t.getTimestamp(), context.getTimestamp()) < 7)
            .map(TransactionSummary::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return recentTotal.compareTo(new BigDecimal("10000")) > 0; // More than $10k in a week
    }
    
    private boolean isSyntheticIdentityPattern(FraudContext context) {
        // Logic to detect synthetic identity patterns
        return context.getAccountAge() < 30 && // New account
            context.getAmount().compareTo(new BigDecimal("1000")) > 0; // Large transaction
    }
    
    private void updateUserRiskProfile(String userId, FraudAnalysisResult result) {
        UserRiskProfile profile = userRiskProfiles.computeIfAbsent(
            userId, k -> UserRiskProfile.createDefault(userId));
        profile.updateFromAnalysis(result);
    }
    
    private void updateFraudPatterns(FraudContext context) {
        // Update pattern tracking for future detection
        String locationString = context.getLocationInfo() != null ?
            context.getLocationInfo().getCity() + ", " + context.getLocationInfo().getCountry() : null;

        TransactionPattern pattern = TransactionPattern.builder()
            .userId(context.getUserId())
            .amount(context.getAmount())
            .timestamp(context.getTimestamp())
            .location(locationString)
            .deviceId(context.getDeviceId())
            .build();
            
        recentPatterns.put(context.getTransactionId(), pattern);
        
        // Clean old patterns (keep last 1000)
        if (recentPatterns.size() > 1000) {
            recentPatterns.entrySet().removeIf(entry -> 
                ChronoUnit.HOURS.between(entry.getValue().getTimestamp(), LocalDateTime.now()) > 24);
        }
    }
    
    private void updateMetrics(FraudAnalysisResult result) {
        // Record metrics using meterRegistry instead of metricsService
        meterRegistry.gauge("fraud.score", result.getFraudScore().getScore());

        if (result.getRiskLevel().ordinal() >= FraudRiskLevel.MEDIUM.ordinal()) {
            Counter.builder("fraud.detected")
                .tag("risk_level", result.getRiskLevel().name())
                .description("Number of fraud cases detected")
                .register(meterRegistry)
                .increment();
        }
    }
    
    // Additional helper methods
    
    private long calculateAccountAge(String userId) {
        // Implementation would query user service for account creation date
        return 365; // Default to 1 year for now
    }
    
    private List<TransactionSummary> getRecentTransactions(String userId) {
        // Implementation would query transaction history
        return new ArrayList<>(); // Empty for now
    }
    
    private double calculateDistance(Location loc1, Location loc2) {
        // Simplified distance calculation
        return Math.sqrt(Math.pow(loc1.getLatitude() - loc2.getLatitude(), 2) +
                        Math.pow(loc1.getLongitude() - loc2.getLongitude(), 2)) * 111; // Rough km conversion
    }
    
    private void holdTransactionForReview(FraudAnalysisResult result, FraudContext context) {
        log.info("Holding transaction {} for manual review", result.getTransactionId());
        // Implementation would send to review queue
    }
    
    private void requestAdditionalVerification(FraudAnalysisResult result, FraudContext context) {
        log.info("Requesting additional verification for transaction {}", result.getTransactionId());
        // Implementation would trigger verification workflow
    }
    
    private void flagForMonitoring(FraudAnalysisResult result, FraudContext context) {
        log.debug("Flagging transaction {} for enhanced monitoring", result.getTransactionId());
        // Implementation would add to monitoring watchlist
    }
    
    private void logForAnalytics(FraudAnalysisResult result, FraudContext context) {
        log.debug("Logging transaction {} for analytics", result.getTransactionId());
        // Implementation would send to analytics pipeline
    }
    
    private void notifyCompliance(FraudAnalysisResult result, FraudContext context) {
        log.warn("Notifying compliance team about high-risk transaction {}", result.getTransactionId());
        // Implementation would notify compliance system
    }

    /**
     * Convert FraudRule.RuleSeverity to FraudRuleViolation.ViolationSeverity
     */
    private FraudRuleViolation.ViolationSeverity convertSeverity(FraudRule.RuleSeverity ruleSeverity) {
        if (ruleSeverity == null) {
            return FraudRuleViolation.ViolationSeverity.MEDIUM;
        }
        switch (ruleSeverity) {
            case LOW:
                return FraudRuleViolation.ViolationSeverity.LOW;
            case MEDIUM:
                return FraudRuleViolation.ViolationSeverity.MEDIUM;
            case HIGH:
                return FraudRuleViolation.ViolationSeverity.HIGH;
            case CRITICAL:
                return FraudRuleViolation.ViolationSeverity.CRITICAL;
            default:
                return FraudRuleViolation.ViolationSeverity.MEDIUM;
        }
    }

    /**
     * Get the fraud scoring engine for model feedback and updates
     * @return FraudScoringEngine instance
     */
    public FraudScoringEngine getFraudScoringEngine() {
        return fraudScoringEngine;
    }
}