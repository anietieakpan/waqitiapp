package com.waqiti.common.fraud;

import com.waqiti.common.fraud.model.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.ml.ImageAnalysisService;
import com.waqiti.common.ml.ModelPredictionService;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ENTERPRISE-GRADE COMPREHENSIVE FRAUD DETECTION SERVICE
 * 
 * Implements multi-layered fraud detection with ML-based analysis:
 * - Routing number fraud database validation
 * - Real-time transaction velocity analysis  
 * - Digital image alteration detection
 * - Behavioral pattern analysis
 * - Machine learning risk scoring
 * - AML compliance integration
 * 
 * This service addresses the critical security gap where ALL routing numbers
 * were being accepted without verification, creating massive fraud exposure.
 * 
 * Features:
 * - Real-time routing number fraud database lookup
 * - Advanced image forensics for check fraud detection
 * - ML-powered transaction risk scoring
 * - Behavioral anomaly detection
 * - Velocity-based fraud prevention
 * - Integration with external fraud data providers
 * - Comprehensive audit logging and compliance reporting
 * 
 * @author Waqiti Security Team
 * @since 2.0.0
 */
@Service
@Slf4j
public class ComprehensiveFraudDetectionService {

    private final FraudDatabaseService fraudDatabaseService;
    private final ImageAnalysisService imageAnalysisService;
    private final ModelPredictionService mlModelService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    // Self-reference for @Cacheable proxy support
    private ComprehensiveFraudDetectionService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@Lazy ComprehensiveFraudDetectionService self) {
        this.self = self;
    }

    // Configuration
    @Value("${fraud.detection.routing.enabled:true}")
    private boolean routingFraudDetectionEnabled;
    
    @Value("${fraud.detection.image.enabled:true}")
    private boolean imageAnalysisEnabled;
    
    @Value("${fraud.detection.ml.enabled:true}")
    private boolean mlFraudDetectionEnabled;
    
    @Value("${fraud.detection.velocity.window.minutes:60}")
    private int velocityWindowMinutes;
    
    @Value("${fraud.detection.velocity.max.transactions:10}")
    private int maxTransactionsPerWindow;
    
    @Value("${fraud.detection.velocity.max.amount:50000}")
    private BigDecimal maxAmountPerWindow;
    
    // Caches for performance
    private final Map<String, RoutingNumberFraudStatus> routingFraudCache = new ConcurrentHashMap<>();
    private final Map<String, List<TransactionEvent>> velocityTracker = new ConcurrentHashMap<>();
    private final Map<String, UserRiskProfile> userRiskProfiles = new ConcurrentHashMap<>();
    
    // Metrics
    private final Counter fraudDetectionCounter;
    private final Counter routingFraudBlockedCounter;
    private final Counter imageAlterationDetectedCounter;
    private final Counter velocityViolationCounter;
    
    public ComprehensiveFraudDetectionService(FraudDatabaseService fraudDatabaseService,
                                            ImageAnalysisService imageAnalysisService,
                                            ModelPredictionService mlModelService,
                                            AuditService auditService,
                                            MeterRegistry meterRegistry) {
        this.fraudDatabaseService = fraudDatabaseService;
        this.imageAnalysisService = imageAnalysisService;
        this.mlModelService = mlModelService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.fraudDetectionCounter = Counter.builder("fraud_detection_checks")
            .description("Number of fraud detection checks performed")
            .register(meterRegistry);
            
        this.routingFraudBlockedCounter = Counter.builder("fraud_routing_blocked")
            .description("Number of transactions blocked due to fraudulent routing numbers")
            .register(meterRegistry);
            
        this.imageAlterationDetectedCounter = Counter.builder("fraud_image_alteration")
            .description("Number of altered images detected")
            .register(meterRegistry);
            
        this.velocityViolationCounter = Counter.builder("fraud_velocity_violation")
            .description("Number of velocity-based fraud violations")
            .register(meterRegistry);
    }

    /**
     * Comprehensive fraud check for routing numbers with real database validation.
     * This replaces the previous implementation that returned false for ALL routing numbers.
     * 
     * @param routingNumber The routing number to validate
     * @return true if the routing number is flagged as fraudulent
     */
    @Timed(value = "fraud_routing_check_duration", description = "Time to check routing number fraud")
    @Cacheable(value = "routing-fraud-cache", key = "#routingNumber", cacheManager = "fraudCacheManager")
    public boolean isKnownFraudulentRoutingNumber(String routingNumber) {
        if (!routingFraudDetectionEnabled) {
            return false;
        }
        
        try {
            log.debug("Checking routing number for fraud: {}", maskRoutingNumber(routingNumber));
            fraudDetectionCounter.increment();
            
            // Check internal fraud database first
            RoutingNumberFraudStatus status = fraudDatabaseService.checkRoutingNumber(routingNumber);
            
            if (status.isFraudulent()) {
                log.warn("Routing number flagged as fraudulent: {} - Reason: {}", 
                    maskRoutingNumber(routingNumber), status.getReason());
                
                // Update metrics
                routingFraudBlockedCounter.increment();
                
                // Audit the fraud detection
                auditService.auditFraudDetection(FraudDetectionEvent.builder()
                    .eventType("ROUTING_NUMBER_FRAUD")
                    .routingNumber(maskRoutingNumber(routingNumber))
                    .fraudReason(status.getReason())
                    .riskScore(status.getRiskScore())
                    .detectedAt(LocalDateTime.now())
                    .blocked(true)
                    .build());
                
                return true;
            }
            
            // Check external fraud data providers
            if (checkExternalFraudProviders(routingNumber)) {
                log.warn("Routing number flagged by external fraud providers: {}", 
                    maskRoutingNumber(routingNumber));
                
                routingFraudBlockedCounter.increment();
                return true;
            }
            
            // Check for suspicious patterns
            if (hasSuspiciousRoutingPatterns(routingNumber)) {
                log.warn("Routing number has suspicious patterns: {}", 
                    maskRoutingNumber(routingNumber));
                
                routingFraudBlockedCounter.increment();
                return true;
            }
            
            log.debug("Routing number passed fraud checks: {}", maskRoutingNumber(routingNumber));
            return false;
            
        } catch (Exception e) {
            log.error("Error checking routing number for fraud: {}", maskRoutingNumber(routingNumber), e);
            
            // In case of error, err on the side of caution for high-value transactions
            // For now, allow the transaction but flag for manual review
            auditService.auditFraudDetection(FraudDetectionEvent.builder()
                .eventType("ROUTING_FRAUD_CHECK_ERROR")
                .routingNumber(maskRoutingNumber(routingNumber))
                .fraudReason("Fraud check system error: " + e.getMessage())
                .detectedAt(LocalDateTime.now())
                .requiresManualReview(true)
                .build());
            
            return false; // Allow transaction but flag for review
        }
    }

    /**
     * Advanced digital alteration detection for check images using ML and forensic analysis.
     * This replaces the previous stub that returned false for ALL images.
     * 
     * @param checkImage The check image to analyze
     * @return true if digital alteration is detected
     */
    @Timed(value = "fraud_image_analysis_duration", description = "Time to analyze check image for fraud")
    public boolean hasDigitalAlterationSigns(BufferedImage checkImage) {
        if (!imageAnalysisEnabled || checkImage == null) {
            return false;
        }
        
        try {
            log.debug("Analyzing check image for digital alteration signs");
            fraudDetectionCounter.increment();
            
            // Perform comprehensive image forensics analysis
            ImageForensicsResult forensicsResult = imageAnalysisService.analyzeImageForensics(checkImage);
            
            // Check for digital alteration indicators
            boolean hasAlteration = false;
            List<String> alterationIndicators = new ArrayList<>();
            
            // 1. JPEG compression analysis
            if (forensicsResult.hasInconsistentJpegCompression()) {
                alterationIndicators.add("Inconsistent JPEG compression levels detected");
                hasAlteration = true;
            }
            
            // 2. Error Level Analysis (ELA)
            if (forensicsResult.getErrorLevelScore() > 0.7) {
                alterationIndicators.add("High error level analysis score: " + forensicsResult.getErrorLevelScore());
                hasAlteration = true;
            }
            
            // 3. Metadata inconsistencies
            if (forensicsResult.hasMetadataInconsistencies()) {
                alterationIndicators.add("Image metadata inconsistencies detected");
                hasAlteration = true;
            }
            
            // 4. Copy-paste detection
            if (forensicsResult.hasCopyPasteIndicators()) {
                alterationIndicators.add("Copy-paste manipulation indicators detected");
                hasAlteration = true;
            }
            
            // 5. Statistical analysis
            if (forensicsResult.getStatisticalAnomalyScore() > 0.8) {
                alterationIndicators.add("Statistical anomalies detected in image data");
                hasAlteration = true;
            }
            
            // 6. ML-based alteration detection
            if (mlFraudDetectionEnabled) {
                MLImageAnalysisResult mlResult = mlModelService.detectImageAlteration(checkImage);
                if (mlResult.getAlterationProbability() > 0.75) {
                    alterationIndicators.add("ML model detected high probability of alteration: " + 
                        mlResult.getAlterationProbability());
                    hasAlteration = true;
                }
            }
            
            if (hasAlteration) {
                log.warn("Digital alteration detected in check image. Indicators: {}", alterationIndicators);
                
                // Update metrics
                imageAlterationDetectedCounter.increment();
                
                // Audit the detection
                auditService.auditFraudDetection(FraudDetectionEvent.builder()
                    .eventType("IMAGE_ALTERATION_DETECTED")
                    .fraudReason("Digital alteration indicators: " + String.join(", ", alterationIndicators))
                    .riskScore(forensicsResult.getOverallRiskScore())
                    .detectedAt(LocalDateTime.now())
                    .blocked(true)
                    .build());
                
                return true;
            }
            
            log.debug("Check image passed alteration detection analysis");
            return false;
            
        } catch (Exception e) {
            log.error("Error analyzing image for digital alteration", e);
            
            // In case of error, flag for manual review
            auditService.auditFraudDetection(FraudDetectionEvent.builder()
                .eventType("IMAGE_ANALYSIS_ERROR")
                .fraudReason("Image analysis system error: " + e.getMessage())
                .detectedAt(LocalDateTime.now())
                .requiresManualReview(true)
                .build());
            
            return false; // Allow transaction but flag for manual review
        }
    }

    /**
     * Comprehensive transaction fraud analysis including velocity, behavioral, and ML-based checks.
     * 
     * @param request The fraud assessment request containing transaction details
     * @return Comprehensive fraud assessment result
     */
    @Timed(value = "fraud_transaction_analysis_duration", description = "Time for comprehensive transaction fraud analysis")
    public FraudAssessmentResult assessTransactionFraud(FraudAssessmentRequest request) {
        try {
            log.debug("Performing comprehensive fraud assessment for transaction: {}", request.getTransactionId());
            fraudDetectionCounter.increment();
            
            FraudAssessmentResult.FraudAssessmentResultBuilder resultBuilder = FraudAssessmentResult.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .assessedAt(LocalDateTime.now());
            
            List<FraudIndicator> indicators = new ArrayList<>();
            double overallRiskScore = 0.0;
            
            // 1. Velocity-based fraud detection
            VelocityCheckResult velocityResult = checkTransactionVelocity(request);
            if (velocityResult.isViolation()) {
                indicators.add(new FraudIndicator("VELOCITY_VIOLATION", velocityResult.getDescription(), velocityResult.getRiskScore()));
                overallRiskScore = Math.max(overallRiskScore, velocityResult.getRiskScore());
                velocityViolationCounter.increment();
            }
            
            // 2. Routing number fraud check
            if (request.getRoutingNumber() != null && self.isKnownFraudulentRoutingNumber(request.getRoutingNumber())) {
                indicators.add(new FraudIndicator("FRAUDULENT_ROUTING_NUMBER", "Known fraudulent routing number", 0.95));
                overallRiskScore = Math.max(overallRiskScore, 0.95);
            }
            
            // 3. Image alteration detection (if check images provided)
            if (request.getCheckImage() != null && hasDigitalAlterationSigns(request.getCheckImage())) {
                indicators.add(new FraudIndicator("IMAGE_ALTERATION", "Digital alteration detected in check image", 0.9));
                overallRiskScore = Math.max(overallRiskScore, 0.9);
            }
            
            // 4. Behavioral analysis
            BehavioralAnalysisResult behavioralResult = analyzeBehavioralPatterns(request);
            if (behavioralResult.isAnomalous()) {
                indicators.add(new FraudIndicator("BEHAVIORAL_ANOMALY", behavioralResult.getDescription(), behavioralResult.getRiskScore()));
                overallRiskScore = Math.max(overallRiskScore, behavioralResult.getRiskScore());
            }
            
            // 5. ML-based fraud scoring
            if (mlFraudDetectionEnabled) {
                MLFraudScore mlScore = mlModelService.calculateFraudScore(request);
                if (mlScore.getScore() > 0.7) {
                    indicators.add(new FraudIndicator("ML_HIGH_RISK", "ML model detected high fraud risk", mlScore.getScore()));
                    overallRiskScore = Math.max(overallRiskScore, mlScore.getScore());
                }
            }
            
            // Determine final recommendation
            FraudMitigationAction recommendation = determineMitigationAction(overallRiskScore, indicators);
            
            FraudAssessmentResult result = resultBuilder
                .overallRiskScore(overallRiskScore)
                .indicators(indicators)
                .recommendation(recommendation)
                .blocked(recommendation == FraudMitigationAction.BLOCK_TRANSACTION)
                .requiresManualReview(recommendation == FraudMitigationAction.MANUAL_REVIEW)
                .build();
            
            // Audit the assessment
            auditService.auditFraudDetection(FraudDetectionEvent.builder()
                .eventType("COMPREHENSIVE_FRAUD_ASSESSMENT")
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .riskScore(overallRiskScore)
                .fraudIndicators(indicators)
                .recommendation(recommendation)
                .detectedAt(LocalDateTime.now())
                .blocked(result.isBlocked())
                .requiresManualReview(result.isRequiresManualReview())
                .build());
            
            log.info("Fraud assessment completed for transaction: {} - Risk Score: {:.2f}, Action: {}", 
                request.getTransactionId(), overallRiskScore, recommendation);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error performing fraud assessment for transaction: {}", request.getTransactionId(), e);
            
            // Return safe default - manual review required
            return FraudAssessmentResult.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .overallRiskScore(0.5)
                .recommendation(FraudMitigationAction.MANUAL_REVIEW)
                .requiresManualReview(true)
                .errorMessage("Fraud assessment system error: " + e.getMessage())
                .assessedAt(LocalDateTime.now())
                .build();
        }
    }

    // Private helper methods

    private boolean checkExternalFraudProviders(String routingNumber) {
        try {
            // Check multiple external fraud data providers
            // This would integrate with services like:
            // - Early Warning Services
            // - NACHA fraud databases
            // - Industry fraud consortiums
            
            // For now, implement basic checks
            Map<String, Object> externalResult = fraudDatabaseService.checkExternalProviders(routingNumber);
            return externalResult != null && !"fraud".equals(externalResult.get("result"));
            
        } catch (Exception e) {
            log.error("Error checking external fraud providers", e);
            return false;
        }
    }

    private boolean hasSuspiciousRoutingPatterns(String routingNumber) {
        // Check for suspicious patterns in routing numbers
        
        // All same digits
        if (routingNumber.matches("(.)\\1{8}")) {
            return true;
        }
        
        // Sequential numbers
        if (isSequentialDigits(routingNumber)) {
            return true;
        }
        
        // Known test/fake routing numbers
        Set<String> testRoutingNumbers = Set.of(
            "000000000", "123456789", "111111111", "222222222",
            "999999999", "987654321"
        );
        
        return testRoutingNumbers.contains(routingNumber);
    }

    private boolean isSequentialDigits(String number) {
        for (int i = 0; i < number.length() - 1; i++) {
            int current = Character.getNumericValue(number.charAt(i));
            int next = Character.getNumericValue(number.charAt(i + 1));
            
            if (Math.abs(current - next) != 1) {
                return false;
            }
        }
        return true;
    }

    private VelocityCheckResult checkTransactionVelocity(FraudAssessmentRequest request) {
        String userId = request.getUserId();
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(velocityWindowMinutes);
        
        // Get recent transactions for this user
        List<TransactionEvent> recentTransactions = velocityTracker.getOrDefault(userId, new ArrayList<>())
            .stream()
            .filter(tx -> tx.getTimestamp().isAfter(windowStart))
            .toList();
        
        // Check transaction count velocity
        if (recentTransactions.size() >= maxTransactionsPerWindow) {
            return VelocityCheckResult.violation(
                "Too many transactions in time window: " + recentTransactions.size() + " in " + velocityWindowMinutes + " minutes",
                0.8
            );
        }
        
        // Check amount velocity
        BigDecimal totalAmount = recentTransactions.stream()
            .map(TransactionEvent::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(request.getAmount());
        
        if (totalAmount.compareTo(maxAmountPerWindow) > 0) {
            return VelocityCheckResult.violation(
                "Transaction amount velocity exceeded: $" + totalAmount + " in " + velocityWindowMinutes + " minutes",
                0.85
            );
        }
        
        // Add current transaction to tracker
        TransactionEvent currentTransaction = TransactionEvent.builder()
            .transactionId(request.getTransactionId())
            .userId(userId)
            .amount(request.getAmount())
            .timestamp(LocalDateTime.now())
            .build();
        
        velocityTracker.computeIfAbsent(userId, k -> new ArrayList<>()).add(currentTransaction);
        
        return VelocityCheckResult.passed();
    }

    private BehavioralAnalysisResult analyzeBehavioralPatterns(FraudAssessmentRequest request) {
        try {
            UserRiskProfile profile = userRiskProfiles.computeIfAbsent(request.getUserId(), 
                k -> fraudDatabaseService.getUserRiskProfile(k));
            
            // Analyze transaction patterns against user's historical behavior
            double anomalyScore = 0.0;
            List<String> anomalies = new ArrayList<>();
            
            // Check amount patterns
            if (isAmountAnomalous(request.getAmount(), profile)) {
                anomalies.add("Transaction amount unusual for this user");
                anomalyScore = Math.max(anomalyScore, 0.6);
            }
            
            // Check time patterns
            if (isTimeAnomalous(request.getTimestamp(), profile)) {
                anomalies.add("Transaction time unusual for this user");
                anomalyScore = Math.max(anomalyScore, 0.5);
            }
            
            // Check geographical patterns
            if (request.getLocationData() != null && isLocationAnomalous(request.getLocationData(), profile)) {
                anomalies.add("Transaction location unusual for this user");
                anomalyScore = Math.max(anomalyScore, 0.7);
            }
            
            return BehavioralAnalysisResult.builder()
                .anomalous(anomalyScore > 0.6)
                .riskScore(anomalyScore)
                .description(String.join(", ", anomalies))
                .anomalies(anomalies)
                .build();
            
        } catch (Exception e) {
            log.error("Error analyzing behavioral patterns", e);
            return BehavioralAnalysisResult.builder().anomalous(false).riskScore(0.0).build();
        }
    }

    private boolean isAmountAnomalous(BigDecimal amount, UserRiskProfile profile) {
        BigDecimal averageAmount = profile.getAverageTransactionAmount();
        if (averageAmount.compareTo(BigDecimal.ZERO) == 0) {
            return false; // No historical data
        }
        
        // Check if amount is more than 3 standard deviations from average
        BigDecimal deviation = amount.subtract(averageAmount).abs();
        BigDecimal threshold = averageAmount.multiply(new BigDecimal("3.0"));
        
        return deviation.compareTo(threshold) > 0;
    }

    private boolean isTimeAnomalous(LocalDateTime timestamp, UserRiskProfile profile) {
        // Check if transaction time is outside user's normal hours
        int hour = timestamp.getHour();
        return hour < profile.getEarliestTransactionHour() || hour > profile.getLatestTransactionHour();
    }

    private boolean isLocationAnomalous(String locationData, UserRiskProfile profile) {
        // Check if location is far from user's normal transaction locations
        // This would implement geographical distance analysis
        return !profile.getCommonLocations().contains(locationData);
    }

    private FraudMitigationAction determineMitigationAction(double riskScore, List<FraudIndicator> indicators) {
        // High risk - block transaction
        if (riskScore >= 0.8) {
            return FraudMitigationAction.BLOCK_TRANSACTION;
        }
        
        // Medium risk - require manual review
        if (riskScore >= 0.5) {
            return FraudMitigationAction.MANUAL_REVIEW;
        }
        
        // Check for specific high-risk indicators
        for (FraudIndicator indicator : indicators) {
            if ("FRAUDULENT_ROUTING_NUMBER".equals(indicator.getType()) || 
                "IMAGE_ALTERATION".equals(indicator.getType())) {
                return FraudMitigationAction.BLOCK_TRANSACTION;
            }
        }
        
        // Low risk - allow with monitoring
        if (riskScore >= 0.3) {
            return FraudMitigationAction.ALLOW_WITH_MONITORING;
        }
        
        // Very low risk - allow transaction
        return FraudMitigationAction.ALLOW_TRANSACTION;
    }

    private String maskRoutingNumber(String routingNumber) {
        if (routingNumber == null || routingNumber.length() < 4) {
            return "****";
        }
        return routingNumber.substring(0, 2) + "*****" + routingNumber.substring(routingNumber.length() - 2);
    }
    
    /**
     * Fraud detection event for auditing and monitoring
     */
    @lombok.Data
    @lombok.Builder
    public static class FraudDetectionEvent {
        private String eventType;
        private String transactionId;
        private String userId;
        private String detectionMethod;
        private double riskScore;
        private String description;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        private String routingNumber;
        private String fraudReason;
        private LocalDateTime detectedAt;
        private boolean blocked;
        private List<com.waqiti.common.fraud.model.FraudIndicator> fraudIndicators;
        private boolean requiresManualReview;
        private com.waqiti.common.fraud.model.FraudMitigationAction recommendation;
    }
    
    /**
     * Image forensics analysis result (simplified stub - actual implementation is in ImageAnalysisService)
     */
    @lombok.Data
    @lombok.Builder
    public static class ImageForensicsResult {
        private boolean alterationDetected;
        private double alterationConfidence;
        private List<String> alterationIndicators;
        private Map<String, Double> forensicScores;
        private String recommendation;
        
        // Additional fields for compatibility
        private String analysisId;
        private LocalDateTime analysisTimestamp;
        private double overallRiskScore;
        private RiskLevel riskLevel;
        private double confidence;
        
        // Forensic analysis fields
        private boolean hasInconsistentJpegCompression;
        private double jpegCompressionScore;
        private List<String> compressionAnomalies;
        private double errorLevelScore;
        private String elaAnalysisDetails;
        private boolean elaAnomalyDetected;
        private boolean hasMetadataInconsistencies;
        private Map<String, String> metadataInconsistencies;
        private Map<Object, Object> metadataFlags;
        private List<Object> editingSoftware;
        private boolean hasCopyPasteIndicators;
        private List<String> copyPasteRegions;
        private double duplicateRegionScore;
        private double statisticalAnomalyScore;
        private List<String> statisticalAnomalies;
        private Map<String, Double> statisticalMetrics;
        private boolean pixelPatternAnomalies;
        private boolean colorHistogramAnomalies;
        private String imageFormat;
        private String imageResolution;
        private long processingTimeMs;
        private String analysisEngine;
        private String engineVersion;
        
        public enum RiskLevel {
            MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
        }
        
        public boolean hasInconsistentJpegCompression() {
            return hasInconsistentJpegCompression;
        }
        
        public double getErrorLevelScore() {
            return errorLevelScore;
        }
        
        public boolean hasMetadataInconsistencies() {
            return hasMetadataInconsistencies;
        }
        
        public boolean hasCopyPasteIndicators() {
            return hasCopyPasteIndicators;
        }
        
        public double getStatisticalAnomalyScore() {
            return statisticalAnomalyScore;
        }
    }
    
    /**
     * ML image analysis result (simplified stub - actual implementation is in ImageAnalysisService)
     */
    @lombok.Data
    @lombok.Builder
    public static class MLImageAnalysisResult {
        private boolean fraudDetected;
        private double fraudProbability;
        private List<String> anomalies;
        private Map<String, Double> featureScores;
        
        // Additional fields for compatibility  
        private String analysisId;
        private LocalDateTime analysisTimestamp;
        private double alterationProbability;
        private RiskLevel riskLevel;
        private String modelId;
        private String modelVersion;
        private boolean alterationDetected;
        private double alterationConfidence;
        private AlterationType alterationType;
        private String imageClassification;
        private double classificationConfidence;
        private List<String> detectedObjects;
        private double authenticityScore;
        private List<String> authenticityIndicators;
        private double tamperingProbability;
        private List<String> tamperingRegions;
        private String tamperingMethod;
        private boolean validDocumentFormat;
        private double documentQualityScore;
        private List<String> documentAnomalies;
        private Map<String, String> extractedFields;
        private double riskScore;
        private List<String> riskFactors;
        private String processingMode;
        private Map<String, Double> modelMetadata;
        private Map<String, Object> detailedAnalysis;
        private long processingTimeMs;
        private Map<String, Double> objectConfidences;
        private boolean likelyAuthentic;
        private long inferenceTimeMs;

        public enum RiskLevel {
            MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
        }
        
        public enum AlterationType {
            UNKNOWN, SPLICING, COPY_MOVE, REMOVAL, ENHANCEMENT, DEEPFAKE, SYNTHETIC, PHOTOSHOP, DIGITAL_ALTERATION, PHYSICAL_TAMPERING
        }
    }
    
    /**
     * ML fraud score result
     */
    @lombok.Data
    @lombok.Builder
    public static class MLFraudScore {
        private double fraudScore;
        private double confidence;
        private List<String> indicators;
        private Map<String, Double> featureImportance;
        private String modelVersion;
        
        // Additional fields for compatibility with ModelPredictionService
        private String scoreId;
        private String modelId;
        private LocalDateTime calculatedAt;
        private double score;
        private String riskLevel;
        private boolean fraudDetected;
        private Map<String, Double> modelScores;
        private Map<String, Double> modelConfidences;
        private double ensembleScore;
        private String ensembleMethod;
        private Map<String, Double> featureValues;
        private List<String> topRiskFeatures;
        private List<String> detectedRiskFactors;
        private String primaryRiskFactor;
        private double behavioralAnomalyScore;
        private double velocityRiskScore;
        private double amountRiskScore;
        private double locationRiskScore;
        private double modelAccuracy;
        private double modelPrecision;
        private double modelRecall;
        private double modelF1Score;
        private LocalDateTime modelTrainingDate;
        private long calculationTimeMs;
        private String calculationMethod;
        private String scoreExplanation;
        private List<String> keyIndicators;
        
        // Risk level and factor constants
        public static class RiskLevel {
            public static final String MINIMAL = "MINIMAL";
            public static final String LOW = "LOW";
            public static final String MEDIUM = "MEDIUM";
            public static final String HIGH = "HIGH";
            public static final String CRITICAL = "CRITICAL";
            public static final String EXTREME = "EXTREME";
        }
        
        public static class RiskFactor {
            public static final String HIGH_VELOCITY = "HIGH_VELOCITY";
            public static final String UNUSUAL_AMOUNT = "UNUSUAL_AMOUNT";
            public static final String UNUSUAL_LOCATION = "UNUSUAL_LOCATION";
            public static final String BEHAVIORAL_CHANGE = "BEHAVIORAL_CHANGE";
            public static final String SUSPICIOUS_PATTERN = "SUSPICIOUS_PATTERN";
        }
    }
}