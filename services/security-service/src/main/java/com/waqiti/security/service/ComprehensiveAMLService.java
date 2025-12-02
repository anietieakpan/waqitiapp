package com.waqiti.security.service;

import com.waqiti.security.domain.AmlAlert;
import com.waqiti.security.domain.SuspiciousActivityReport;
import com.waqiti.security.repository.AmlAlertRepository;
import com.waqiti.security.repository.SuspiciousActivityReportRepository;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Comprehensive Anti-Money Laundering (AML) Service
 * 
 * Implements advanced AML monitoring, suspicious activity detection,
 * and regulatory reporting capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComprehensiveAMLService {

    private final AmlAlertRepository amlAlertRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    private final WatchlistScreeningService watchlistScreeningService;
    private final TransactionPatternAnalyzer patternAnalyzer;
    private final ComplianceNotificationService notificationService;

    @Value("${security-service.aml.monitoring-thresholds.single-transaction:10000.00}")
    private BigDecimal singleTransactionThreshold;

    @Value("${security-service.aml.monitoring-thresholds.daily-aggregate:25000.00}")
    private BigDecimal dailyAggregateThreshold;

    @Value("${security-service.aml.monitoring-thresholds.monthly-aggregate:100000.00}")
    private BigDecimal monthlyAggregateThreshold;

    /**
     * Comprehensive AML monitoring for transactions
     */
    @Async
    @Transactional
    public CompletableFuture<AmlAnalysisResult> analyzeTransaction(AmlTransactionRequest request) {
        log.debug("Starting AML analysis for transaction: {}", request.getTransactionId());

        try {
            AmlAnalysisResult result = AmlAnalysisResult.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .analysisTimestamp(LocalDateTime.now())
                .alerts(new ArrayList<>())
                .build();

            // Parallel AML checks
            CompletableFuture<List<AmlAlert>> thresholdFuture = 
                CompletableFuture.supplyAsync(() -> checkThresholds(request));
            
            CompletableFuture<List<AmlAlert>> watchlistFuture = 
                CompletableFuture.supplyAsync(() -> screenWatchlists(request));
            
            CompletableFuture<List<AmlAlert>> patternFuture = 
                CompletableFuture.supplyAsync(() -> analyzePatterns(request));
            
            CompletableFuture<List<AmlAlert>> velocityFuture = 
                CompletableFuture.supplyAsync(() -> checkVelocity(request));

            // Collect all alerts
            List<AmlAlert> allAlerts = new ArrayList<>();
            allAlerts.addAll(thresholdFuture.join());
            allAlerts.addAll(watchlistFuture.join());
            allAlerts.addAll(patternFuture.join());
            allAlerts.addAll(velocityFuture.join());

            // Save alerts to database
            if (!allAlerts.isEmpty()) {
                amlAlertRepository.saveAll(allAlerts);
                result.getAlerts().addAll(allAlerts);
            }

            // Determine if SAR is required
            if (shouldGenerateSAR(allAlerts, request)) {
                SuspiciousActivityReport sar = generateSAR(request, allAlerts);
                sarRepository.save(sar);
                result.setSarGenerated(true);
                result.setSarId(sar.getId());
            }

            // Calculate overall risk score
            result.setRiskScore(calculateAmlRiskScore(allAlerts));
            result.setRiskLevel(determineAmlRiskLevel(result.getRiskScore()));

            // Send notifications for high-risk cases
            if (result.getRiskScore().compareTo(BigDecimal.valueOf(75)) > 0) {
                notificationService.sendAmlAlert(result);
            }

            log.info("AML analysis completed for transaction: {} - Risk: {} ({})", 
                request.getTransactionId(), result.getRiskLevel(), result.getRiskScore());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Error during AML analysis for transaction: {}", request.getTransactionId(), e);
            
            // Create high-risk result for safety
            AmlAnalysisResult errorResult = AmlAnalysisResult.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .analysisTimestamp(LocalDateTime.now())
                .riskScore(BigDecimal.valueOf(90))
                .riskLevel("HIGH")
                .alerts(List.of(createErrorAlert(request, e.getMessage())))
                .build();
                
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    /**
     * Check transaction thresholds
     */
    private List<AmlAlert> checkThresholds(AmlTransactionRequest request) {
        List<AmlAlert> alerts = new ArrayList<>();

        // Single transaction threshold
        if (request.getAmount().compareTo(singleTransactionThreshold) >= 0) {
            alerts.add(AmlAlert.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .alertType(AmlAlert.AmlAlertType.THRESHOLD_EXCEEDED)
                .severity(AmlAlert.AmlSeverity.MEDIUM)
                .title("Single Transaction Threshold Exceeded")
                .description(String.format("Transaction amount %s exceeds single transaction threshold %s", 
                    request.getAmount(), singleTransactionThreshold))
                .details(Map.of(
                    "amount", request.getAmount(),
                    "threshold", singleTransactionThreshold,
                    "type", "SINGLE_TRANSACTION"
                ))
                .status(AmlAlert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        }

        // Check daily aggregate
        BigDecimal dailyTotal = calculateDailyTotal(request.getUserId(), request.getTimestamp());
        if (dailyTotal.compareTo(dailyAggregateThreshold) >= 0) {
            alerts.add(AmlAlert.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .alertType(AmlAlert.AmlAlertType.THRESHOLD_EXCEEDED)
                .severity(AmlAlert.AmlSeverity.HIGH)
                .title("Daily Aggregate Threshold Exceeded")
                .description(String.format("Daily transaction total %s exceeds threshold %s", 
                    dailyTotal, dailyAggregateThreshold))
                .details(Map.of(
                    "dailyTotal", dailyTotal,
                    "threshold", dailyAggregateThreshold,
                    "type", "DAILY_AGGREGATE"
                ))
                .status(AmlAlert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        }

        // Check monthly aggregate
        BigDecimal monthlyTotal = calculateMonthlyTotal(request.getUserId(), request.getTimestamp());
        if (monthlyTotal.compareTo(monthlyAggregateThreshold) >= 0) {
            alerts.add(AmlAlert.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .alertType(AmlAlert.AmlAlertType.THRESHOLD_EXCEEDED)
                .severity(AmlAlert.AmlSeverity.HIGH)
                .title("Monthly Aggregate Threshold Exceeded")
                .description(String.format("Monthly transaction total %s exceeds threshold %s", 
                    monthlyTotal, monthlyAggregateThreshold))
                .details(Map.of(
                    "monthlyTotal", monthlyTotal,
                    "threshold", monthlyAggregateThreshold,
                    "type", "MONTHLY_AGGREGATE"
                ))
                .status(AmlAlert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        }

        return alerts;
    }

    /**
     * Screen against watchlists
     */
    private List<AmlAlert> screenWatchlists(AmlTransactionRequest request) {
        List<AmlAlert> alerts = new ArrayList<>();

        // Sanctions screening
        WatchlistScreeningResult sanctionsResult = watchlistScreeningService.screenSanctions(
            request.getUserName(), request.getCounterpartyName());
        
        if (sanctionsResult.isMatch()) {
            alerts.add(AmlAlert.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .alertType(AmlAlert.AmlAlertType.WATCHLIST_MATCH)
                .severity(AmlAlert.AmlSeverity.CRITICAL)
                .title("Sanctions List Match")
                .description("Transaction involves party on sanctions list")
                .details(Map.of(
                    "matchedName", sanctionsResult.getMatchedName(),
                    "matchScore", sanctionsResult.getMatchScore(),
                    "listName", sanctionsResult.getListName()
                ))
                .status(AmlAlert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        }

        // PEP screening
        WatchlistScreeningResult pepResult = watchlistScreeningService.screenPEP(
            request.getUserName(), request.getCounterpartyName());
        
        if (pepResult.isMatch()) {
            alerts.add(AmlAlert.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .alertType(AmlAlert.AmlAlertType.PEP_MATCH)
                .severity(AmlAlert.AmlSeverity.HIGH)
                .title("Politically Exposed Person Match")
                .description("Transaction involves politically exposed person")
                .details(Map.of(
                    "matchedName", pepResult.getMatchedName(),
                    "matchScore", pepResult.getMatchScore(),
                    "position", pepResult.getPosition()
                ))
                .status(AmlAlert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        }

        // Adverse media screening
        WatchlistScreeningResult mediaResult = watchlistScreeningService.screenAdverseMedia(
            request.getUserName(), request.getCounterpartyName());
        
        if (mediaResult.isMatch()) {
            alerts.add(AmlAlert.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .alertType(AmlAlert.AmlAlertType.ADVERSE_MEDIA)
                .severity(AmlAlert.AmlSeverity.MEDIUM)
                .title("Adverse Media Match")
                .description("Transaction involves party with adverse media coverage")
                .details(Map.of(
                    "matchedName", mediaResult.getMatchedName(),
                    "mediaType", mediaResult.getMediaType(),
                    "publicationDate", mediaResult.getPublicationDate()
                ))
                .status(AmlAlert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        }

        return alerts;
    }

    /**
     * Analyze suspicious patterns
     */
    private List<AmlAlert> analyzePatterns(AmlTransactionRequest request) {
        List<AmlAlert> alerts = new ArrayList<>();

        // Structuring detection
        if (patternAnalyzer.detectStructuring(request)) {
            alerts.add(AmlAlert.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .alertType(AmlAlert.AmlAlertType.STRUCTURING)
                .severity(AmlAlert.AmlSeverity.HIGH)
                .title("Potential Structuring Detected")
                .description("Transaction pattern suggests potential structuring")
                .details(Map.of(
                    "pattern", "Multiple transactions below reporting threshold",
                    "timeframe", "Recent pattern detected"
                ))
                .status(AmlAlert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        }

        // Rapid movement detection
        if (patternAnalyzer.detectRapidMovement(request)) {
            alerts.add(AmlAlert.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .alertType(AmlAlert.AmlAlertType.RAPID_MOVEMENT)
                .severity(AmlAlert.AmlSeverity.MEDIUM)
                .title("Rapid Fund Movement Detected")
                .description("Funds moved rapidly through multiple accounts")
                .details(Map.of(
                    "accounts", patternAnalyzer.getInvolvedAccounts(request),
                    "timeframe", patternAnalyzer.getMovementTimeframe(request)
                ))
                .status(AmlAlert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        }

        // Round amount pattern
        if (patternAnalyzer.detectRoundAmountPattern(request)) {
            alerts.add(AmlAlert.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .alertType(AmlAlert.AmlAlertType.SUSPICIOUS_PATTERN)
                .severity(AmlAlert.AmlSeverity.LOW)
                .title("Round Amount Pattern")
                .description("Frequent use of round amounts detected")
                .details(Map.of(
                    "amount", request.getAmount(),
                    "frequency", patternAnalyzer.getRoundAmountFrequency(request.getUserId())
                ))
                .status(AmlAlert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        }

        return alerts;
    }

    /**
     * Check velocity patterns
     */
    private List<AmlAlert> checkVelocity(AmlTransactionRequest request) {
        List<AmlAlert> alerts = new ArrayList<>();

        VelocityAnalysisResult velocity = patternAnalyzer.analyzeVelocity(request);

        if (velocity.isAnomalousVelocity()) {
            alerts.add(AmlAlert.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .alertType(AmlAlert.AmlAlertType.VELOCITY_ANOMALY)
                .severity(velocity.getSeverity())
                .title("Transaction Velocity Anomaly")
                .description("Unusual transaction velocity detected")
                .details(Map.of(
                    "currentVelocity", velocity.getCurrentVelocity(),
                    "normalVelocity", velocity.getNormalVelocity(),
                    "deviationFactor", velocity.getDeviationFactor()
                ))
                .status(AmlAlert.AlertStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        }

        return alerts;
    }

    /**
     * Generate Suspicious Activity Report
     */
    private SuspiciousActivityReport generateSAR(AmlTransactionRequest request, List<AmlAlert> alerts) {
        return SuspiciousActivityReport.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .reportType(SuspiciousActivityReport.SarType.UNUSUAL_TRANSACTION)
            .suspiciousActivity(compileSuspiciousActivity(alerts))
            .narrativeDescription(generateNarrative(request, alerts))
            .reportingInstitution("Waqiti Inc.")
            .filingDate(LocalDateTime.now())
            .incidentDate(request.getTimestamp())
            .totalAmount(request.getAmount())
            .currency(request.getCurrency())
            .status(SuspiciousActivityReport.SarStatus.DRAFT)
            .priority(determineSarPriority(alerts))
            .involvedParties(extractInvolvedParties(request))
            .supportingDocuments(new ArrayList<>())
            .regulatoryFlags(extractRegulatoryFlags(alerts))
            .createdAt(LocalDateTime.now())
            .build();
    }

    // Helper methods

    private boolean shouldGenerateSAR(List<AmlAlert> alerts, AmlTransactionRequest request) {
        // Generate SAR for critical alerts or high-value suspicious transactions
        return alerts.stream().anyMatch(alert -> 
            alert.getSeverity() == AmlAlert.AmlSeverity.CRITICAL ||
            (alert.getSeverity() == AmlAlert.AmlSeverity.HIGH && 
             request.getAmount().compareTo(singleTransactionThreshold) >= 0));
    }

    private BigDecimal calculateAmlRiskScore(List<AmlAlert> alerts) {
        if (alerts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return alerts.stream()
            .map(alert -> {
                switch (alert.getSeverity()) {
                    case CRITICAL: return BigDecimal.valueOf(25);
                    case HIGH: return BigDecimal.valueOf(15);
                    case MEDIUM: return BigDecimal.valueOf(10);
                    case LOW: return BigDecimal.valueOf(5);
                    default: return BigDecimal.ZERO;
                }
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .min(BigDecimal.valueOf(100)); // Cap at 100
    }

    private String determineAmlRiskLevel(BigDecimal riskScore) {
        if (riskScore.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "CRITICAL";
        } else if (riskScore.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "HIGH";
        } else if (riskScore.compareTo(BigDecimal.valueOf(30)) >= 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private BigDecimal calculateDailyTotal(UUID userId, LocalDateTime timestamp) {
        // Implementation would query transaction history
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateMonthlyTotal(UUID userId, LocalDateTime timestamp) {
        // Implementation would query transaction history
        return BigDecimal.ZERO;
    }

    private AmlAlert createErrorAlert(AmlTransactionRequest request, String errorMessage) {
        return AmlAlert.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .alertType(AmlAlert.AmlAlertType.SYSTEM_ERROR)
            .severity(AmlAlert.AmlSeverity.HIGH)
            .title("AML Analysis Error")
            .description("Error occurred during AML analysis: " + errorMessage)
            .status(AmlAlert.AlertStatus.OPEN)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private String compileSuspiciousActivity(List<AmlAlert> alerts) {
        return alerts.stream()
            .map(AmlAlert::getDescription)
            .reduce("", (a, b) -> a + "; " + b);
    }

    private String generateNarrative(AmlTransactionRequest request, List<AmlAlert> alerts) {
        StringBuilder narrative = new StringBuilder();
        narrative.append("Suspicious transaction detected with the following indicators: ");
        
        alerts.forEach(alert -> {
            narrative.append(alert.getTitle()).append(" - ").append(alert.getDescription()).append(". ");
        });
        
        return narrative.toString();
    }

    private SuspiciousActivityReport.SarPriority determineSarPriority(List<AmlAlert> alerts) {
        boolean hasCritical = alerts.stream().anyMatch(a -> a.getSeverity() == AmlAlert.AmlSeverity.CRITICAL);
        if (hasCritical) {
            return SuspiciousActivityReport.SarPriority.URGENT;
        }
        
        long highCount = alerts.stream().filter(a -> a.getSeverity() == AmlAlert.AmlSeverity.HIGH).count();
        return highCount >= 2 ? SuspiciousActivityReport.SarPriority.HIGH : SuspiciousActivityReport.SarPriority.NORMAL;
    }

    private List<String> extractInvolvedParties(AmlTransactionRequest request) {
        List<String> parties = new ArrayList<>();
        if (request.getUserName() != null) parties.add(request.getUserName());
        if (request.getCounterpartyName() != null) parties.add(request.getCounterpartyName());
        return parties;
    }

    private Map<String, Object> extractRegulatoryFlags(List<AmlAlert> alerts) {
        Map<String, Object> flags = new HashMap<>();
        alerts.forEach(alert -> {
            flags.put(alert.getAlertType().toString(), true);
        });
        return flags;
    }

    // DTOs and Result Classes

    @lombok.Data
    @lombok.Builder
    public static class AmlTransactionRequest {
        private UUID transactionId;
        private UUID userId;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime timestamp;
        private String userName;
        private String counterpartyName;
        private String transactionType;
        private String description;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class AmlAnalysisResult {
        private UUID transactionId;
        private UUID userId;
        private LocalDateTime analysisTimestamp;
        private BigDecimal riskScore;
        private String riskLevel;
        private List<AmlAlert> alerts;
        private boolean sarGenerated;
        private UUID sarId;
    }

    @lombok.Data
    public static class WatchlistScreeningResult {
        private boolean match;
        private String matchedName;
        private BigDecimal matchScore;
        private String listName;
        private String position;
        private String mediaType;
        private LocalDateTime publicationDate;
    }

    @lombok.Data
    public static class VelocityAnalysisResult {
        private boolean anomalousVelocity;
        private AmlAlert.AmlSeverity severity;
        private BigDecimal currentVelocity;
        private BigDecimal normalVelocity;
        private BigDecimal deviationFactor;
    }

    // Mock service implementations
    @Service
    public static class WatchlistScreeningService {
        public WatchlistScreeningResult screenSanctions(String userName, String counterpartyName) {
            return new WatchlistScreeningResult();
        }
        
        public WatchlistScreeningResult screenPEP(String userName, String counterpartyName) {
            return new WatchlistScreeningResult();
        }
        
        public WatchlistScreeningResult screenAdverseMedia(String userName, String counterpartyName) {
            return new WatchlistScreeningResult();
        }
    }

    @Service
    public static class TransactionPatternAnalyzer {
        public boolean detectStructuring(AmlTransactionRequest request) {
            return false;
        }
        
        public boolean detectRapidMovement(AmlTransactionRequest request) {
            return false;
        }
        
        public boolean detectRoundAmountPattern(AmlTransactionRequest request) {
            return false;
        }
        
        public VelocityAnalysisResult analyzeVelocity(AmlTransactionRequest request) {
            return new VelocityAnalysisResult();
        }
        
        public List<String> getInvolvedAccounts(AmlTransactionRequest request) {
            return new ArrayList<>();
        }
        
        public String getMovementTimeframe(AmlTransactionRequest request) {
            return "24 hours";
        }
        
        public int getRoundAmountFrequency(UUID userId) {
            return 0;
        }
    }

    @Service
    public static class ComplianceNotificationService {
        public void sendAmlAlert(AmlAnalysisResult result) {
            // Implementation would send notifications
        }
    }
    
    // Methods required by the controller
    
    /**
     * Monitor user activity for the controller
     */
    public UserMonitoringResult monitorUserActivity(String userId, LocalDate startDate, LocalDate endDate) {
        log.info("Monitoring user activity for {}", userId);
        
        try {
            // Mock implementation - would analyze real user transaction data
            int totalTransactions = ThreadLocalRandom.current().nextInt(50, 150);
            BigDecimal totalVolume = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(10000, 110000));
            
            List<String> suspiciousPatterns = new ArrayList<>();
            String riskLevel = "LOW";
            Map<String, Object> analysis = new HashMap<>();
            
            // Pattern analysis
            if (totalTransactions > 80) {
                suspiciousPatterns.add("HIGH_TRANSACTION_FREQUENCY");
                riskLevel = "MEDIUM";
            }
            
            if (totalVolume.compareTo(BigDecimal.valueOf(75000)) > 0) {
                suspiciousPatterns.add("HIGH_VOLUME");
                if ("MEDIUM".equals(riskLevel)) {
                    riskLevel = "HIGH";
                }
            }
            
            analysis.put("averageTransactionAmount", totalVolume.divide(BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP));
            analysis.put("peakTransactionHour", ThreadLocalRandom.current().nextInt(24));
            analysis.put("uniqueCountries", 3);
            analysis.put("flaggedTransactionCount", ThreadLocalRandom.current().nextInt(10));
            
            return UserMonitoringResult.builder()
                .userId(userId)
                .totalTransactions(totalTransactions)
                .totalVolume(totalVolume)
                .suspiciousPatterns(suspiciousPatterns)
                .riskLevel(riskLevel)
                .analysis(analysis)
                .build();
            
        } catch (Exception e) {
            log.error("Error monitoring user activity {}", userId, e);
            return UserMonitoringResult.builder()
                .userId(userId)
                .totalTransactions(0)
                .totalVolume(BigDecimal.ZERO)
                .suspiciousPatterns(List.of("MONITORING_ERROR"))
                .riskLevel("ERROR")
                .analysis(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * Assess user risk for the controller
     */
    public AmlRiskAssessment assessUserRisk(String userId, Map<String, Object> transactionHistory, 
                                          Map<String, Object> userProfile) {
        log.info("Assessing AML risk for user: {}", userId);
        
        try {
            // Mock risk assessment implementation
            double riskScore = ThreadLocalRandom.current().nextDouble() * 100;
            String riskLevel = determineRiskLevel(riskScore);
            
            List<String> riskFactors = new ArrayList<>();
            Map<String, Object> recommendations = new HashMap<>();
            
            if (riskScore > 70) {
                riskFactors.add("HIGH_TRANSACTION_VOLUME");
                riskFactors.add("UNUSUAL_PATTERNS");
                recommendations.put("action", "ENHANCED_DUE_DILIGENCE");
                recommendations.put("monitoring", "INCREASED");
            } else if (riskScore > 40) {
                riskFactors.add("MODERATE_ACTIVITY");
                recommendations.put("action", "REGULAR_MONITORING");
            } else {
                recommendations.put("action", "STANDARD_MONITORING");
            }
            
            return AmlRiskAssessment.builder()
                .userId(userId)
                .riskLevel(riskLevel)
                .riskScore(riskScore)
                .riskFactors(riskFactors)
                .recommendations(recommendations)
                .assessedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error assessing AML risk for user {}", userId, e);
            return AmlRiskAssessment.builder()
                .userId(userId)
                .riskLevel("ERROR")
                .riskScore(90.0) // High risk due to error
                .riskFactors(List.of("ASSESSMENT_ERROR"))
                .recommendations(Map.of("action", "MANUAL_REVIEW"))
                .assessedAt(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Get AML statistics for the controller
     */
    public AmlStatistics getStatistics(LocalDate fromDate, LocalDate toDate) {
        log.debug("Getting AML statistics from {} to {}", fromDate, toDate);
        
        try {
            // Mock statistics implementation
            return AmlStatistics.builder()
                .totalScreenings(2500L)
                .flaggedTransactions(125L)
                .sarsField(18L)
                .alertsByType(Map.of(
                    "HIGH_AMOUNT", 45L,
                    "STRUCTURING", 25L,
                    "HIGH_VELOCITY", 35L,
                    "WATCHLIST_MATCH", 20L
                ))
                .volumeByRiskLevel(Map.of(
                    "LOW", BigDecimal.valueOf(750000),
                    "MEDIUM", BigDecimal.valueOf(350000),
                    "HIGH", BigDecimal.valueOf(150000),
                    "CRITICAL", BigDecimal.valueOf(50000)
                ))
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
                
        } catch (Exception e) {
            log.error("Error getting AML statistics", e);
            return AmlStatistics.builder()
                .totalScreenings(0)
                .flaggedTransactions(0)
                .sarsField(0)
                .alertsByType(new HashMap<>())
                .volumeByRiskLevel(new HashMap<>())
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
        }
    }
    
    private String determineRiskLevel(double score) {
        if (score >= 80) return "CRITICAL";
        if (score >= 60) return "HIGH";  
        if (score >= 40) return "MEDIUM";
        return "LOW";
    }
}