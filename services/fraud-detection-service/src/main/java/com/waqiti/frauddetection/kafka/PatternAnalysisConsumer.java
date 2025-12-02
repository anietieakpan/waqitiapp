package com.waqiti.frauddetection.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.model.*;
import com.waqiti.frauddetection.repository.PatternAnalysisRepository;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.math.MoneyMath;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for pattern analysis
 * Handles behavioral pattern detection, anomaly identification, and fraud pattern recognition
 * 
 * Critical for: Advanced fraud detection, behavioral analysis, ML model training
 * SLA: Must process pattern analysis within 20 seconds for real-time insights
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PatternAnalysisConsumer {

    private final PatternAnalysisRepository patternRepository;
    private final BehavioralPatternService behavioralService;
    private final TransactionPatternService transactionPatternService;
    private final AnomalyDetectionService anomalyService;
    private final MLModelService mlModelService;
    private final GraphAnalysisService graphAnalysisService;
    private final TimeSeriesAnalysisService timeSeriesService;
    private final NotificationService notificationService;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ScheduledExecutorService scheduledExecutor;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 20000; // 20 seconds
    private static final Set<String> CRITICAL_PATTERNS = Set.of(
        "COORDINATED_ATTACK", "MONEY_LAUNDERING_RING", "ACCOUNT_TAKEOVER_PATTERN", 
        "SYNTHETIC_IDENTITY_FRAUD", "PAYMENT_CARD_TESTING"
    );
    
    @KafkaListener(
        topics = {"pattern-analysis"},
        groupId = "pattern-analysis-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "pattern-analysis-processor", fallbackMethod = "handlePatternAnalysisFailure")
    @Retry(name = "pattern-analysis-processor")
    public void processPatternAnalysis(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing pattern analysis: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            PatternAnalysisRequest request = extractPatternAnalysisRequest(payload);
            
            // Validate request
            validateRequest(request);
            
            // Check for duplicate request
            if (isDuplicateRequest(request)) {
                log.warn("Duplicate pattern analysis request: {}, skipping", request.getRequestId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Enrich request with additional context
            PatternAnalysisRequest enrichedRequest = enrichRequest(request);
            
            // Determine analysis scope and methods
            AnalysisScope analysisScope = determineAnalysisScope(enrichedRequest);
            
            // Perform comprehensive pattern analysis
            PatternAnalysisResult analysisResult = performPatternAnalysis(enrichedRequest, analysisScope);
            
            // Detect anomalies using multiple algorithms
            if (analysisScope.enablesAnomalyDetection()) {
                performAnomalyDetection(enrichedRequest, analysisResult);
            }
            
            // Perform graph analysis for network patterns
            if (analysisScope.enablesGraphAnalysis()) {
                performGraphAnalysis(enrichedRequest, analysisResult);
            }
            
            // Execute time series analysis
            if (analysisScope.enablesTimeSeriesAnalysis()) {
                performTimeSeriesAnalysis(enrichedRequest, analysisResult);
            }
            
            // Update ML models with new patterns
            if (analysisScope.enablesMLModelUpdate()) {
                updateMLModels(enrichedRequest, analysisResult);
            }
            
            // Trigger automated responses
            if (analysisResult.requiresImmediateAction()) {
                triggerAutomatedResponses(enrichedRequest, analysisResult);
            }
            
            // Send pattern notifications
            sendPatternNotifications(enrichedRequest, analysisResult);
            
            // Update pattern knowledge base
            updatePatternKnowledgeBase(enrichedRequest, analysisResult);
            
            // Audit pattern analysis
            auditPatternAnalysis(enrichedRequest, analysisResult, event);
            
            // Record metrics
            recordPatternMetrics(enrichedRequest, analysisResult, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed pattern analysis: {} patterns: {} risk: {} in {}ms", 
                    enrichedRequest.getRequestId(), analysisResult.getDetectedPatterns().size(), 
                    analysisResult.getOverallRiskScore(), System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for pattern analysis: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalPatternException e) {
            log.error("Critical pattern analysis failed: {}", eventId, e);
            handleCriticalPatternError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process pattern analysis: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private PatternAnalysisRequest extractPatternAnalysisRequest(Map<String, Object> payload) {
        return PatternAnalysisRequest.builder()
            .requestId(extractString(payload, "requestId", UUID.randomUUID().toString()))
            .analysisType(PatternAnalysisType.fromString(extractString(payload, "analysisType", null)))
            .entityId(extractString(payload, "entityId", null))
            .entityType(extractString(payload, "entityType", null))
            .customerId(extractString(payload, "customerId", null))
            .merchantId(extractString(payload, "merchantId", null))
            .transactionId(extractString(payload, "transactionId", null))
            .timeWindow(extractString(payload, "timeWindow", "24_HOURS"))
            .analysisDepth(AnalysisDepth.fromString(extractString(payload, "analysisDepth", "STANDARD")))
            .urgency(PatternUrgency.fromString(extractString(payload, "urgency", "NORMAL")))
            .triggerEvent(extractString(payload, "triggerEvent", null))
            .includedPatternTypes(extractStringList(payload, "includedPatternTypes"))
            .excludedPatternTypes(extractStringList(payload, "excludedPatternTypes"))
            .confidenceThreshold(extractDouble(payload, "confidenceThreshold", 0.7))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .requestedBy(extractString(payload, "requestedBy", "SYSTEM"))
            .metadata(extractMap(payload, "metadata"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateRequest(PatternAnalysisRequest request) {
        if (request.getAnalysisType() == null) {
            throw new ValidationException("Analysis type is required");
        }
        
        if (request.getEntityId() == null || request.getEntityId().isEmpty()) {
            throw new ValidationException("Entity ID is required");
        }
        
        if (request.getEntityType() == null || request.getEntityType().isEmpty()) {
            throw new ValidationException("Entity type is required");
        }
        
        if (request.getAnalysisDepth() == null) {
            throw new ValidationException("Analysis depth is required");
        }
        
        if (request.getConfidenceThreshold() < 0.0 || request.getConfidenceThreshold() > 1.0) {
            throw new ValidationException("Confidence threshold must be between 0.0 and 1.0");
        }
        
        // Validate time window
        if (!isValidTimeWindow(request.getTimeWindow())) {
            throw new ValidationException("Invalid time window: " + request.getTimeWindow());
        }
    }

    private boolean isValidTimeWindow(String timeWindow) {
        return Arrays.asList("1_HOUR", "6_HOURS", "24_HOURS", "7_DAYS", "30_DAYS", "90_DAYS")
               .contains(timeWindow);
    }

    private boolean isDuplicateRequest(PatternAnalysisRequest request) {
        // Check for recent similar requests
        return patternRepository.existsSimilarRequest(
            request.getEntityId(),
            request.getAnalysisType(),
            request.getTimeWindow(),
            Instant.now().minus(30, ChronoUnit.MINUTES)
        );
    }

    private PatternAnalysisRequest enrichRequest(PatternAnalysisRequest request) {
        // Enrich with entity profile data
        EntityProfile profile = entityService.getEntityProfile(
            request.getEntityId(), 
            request.getEntityType()
        );
        
        if (profile != null) {
            request.setEntityProfile(profile);
            request.setEntityRiskLevel(profile.getRiskLevel());
            request.setEntityCreationDate(profile.getCreationDate());
        }
        
        // Enrich with historical pattern data
        HistoricalPatternData historicalData = patternRepository.getHistoricalPatterns(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        request.setHistoricalPatternData(historicalData);
        
        // Enrich with related entities
        List<String> relatedEntities = graphAnalysisService.findRelatedEntities(
            request.getEntityId(),
            request.getEntityType(),
            3 // 3 degrees of separation
        );
        request.setRelatedEntities(relatedEntities);
        
        // Enrich with contextual data
        ContextualData contextData = contextService.getContextualData(
            request.getEntityId(),
            request.getTriggerEvent()
        );
        request.setContextualData(contextData);
        
        return request;
    }

    private int parseTimeWindow(String timeWindow) {
        switch (timeWindow) {
            case "1_HOUR": return 1;
            case "6_HOURS": return 6;
            case "24_HOURS": return 24;
            case "7_DAYS": return 168; // 7 * 24
            case "30_DAYS": return 720; // 30 * 24
            case "90_DAYS": return 2160; // 90 * 24
            default: return 24;
        }
    }

    private AnalysisScope determineAnalysisScope(PatternAnalysisRequest request) {
        AnalysisScope scope = new AnalysisScope();
        scope.setAnalysisType(request.getAnalysisType());
        scope.setAnalysisDepth(request.getAnalysisDepth());
        
        // Determine analysis capabilities based on type and depth
        switch (request.getAnalysisType()) {
            case BEHAVIORAL_ANALYSIS:
                scope.setEnablesAnomalyDetection(true);
                scope.setEnablesTimeSeriesAnalysis(true);
                scope.setEnablesMLModelUpdate(true);
                break;
                
            case TRANSACTION_PATTERN:
                scope.setEnablesAnomalyDetection(true);
                scope.setEnablesGraphAnalysis(true);
                scope.setEnablesTimeSeriesAnalysis(true);
                break;
                
            case NETWORK_ANALYSIS:
                scope.setEnablesGraphAnalysis(true);
                scope.setEnablesMLModelUpdate(true);
                break;
                
            case FRAUD_RING_DETECTION:
                scope.setEnablesGraphAnalysis(true);
                scope.setEnablesAnomalyDetection(true);
                scope.setEnablesMLModelUpdate(true);
                break;
                
            case VELOCITY_PATTERN:
                scope.setEnablesTimeSeriesAnalysis(true);
                scope.setEnablesAnomalyDetection(true);
                break;
                
            default:
                scope.setEnablesAnomalyDetection(true);
        }
        
        // Adjust based on analysis depth
        if (request.getAnalysisDepth() == AnalysisDepth.DEEP) {
            scope.setEnablesGraphAnalysis(true);
            scope.setEnablesTimeSeriesAnalysis(true);
            scope.setEnablesMLModelUpdate(true);
        }
        
        return scope;
    }

    private PatternAnalysisResult performPatternAnalysis(PatternAnalysisRequest request, AnalysisScope scope) {
        PatternAnalysisResult result = new PatternAnalysisResult();
        result.setRequestId(request.getRequestId());
        result.setAnalysisStartTime(Instant.now());
        
        List<DetectedPattern> detectedPatterns = new ArrayList<>();
        
        // Behavioral pattern analysis
        if (scope.getAnalysisType() == PatternAnalysisType.BEHAVIORAL_ANALYSIS) {
            detectedPatterns.addAll(performBehavioralAnalysis(request));
        }
        
        // Transaction pattern analysis
        if (scope.getAnalysisType() == PatternAnalysisType.TRANSACTION_PATTERN ||
            scope.getAnalysisType() == PatternAnalysisType.FRAUD_RING_DETECTION) {
            detectedPatterns.addAll(performTransactionPatternAnalysis(request));
        }
        
        // Velocity pattern analysis
        if (scope.getAnalysisType() == PatternAnalysisType.VELOCITY_PATTERN) {
            detectedPatterns.addAll(performVelocityPatternAnalysis(request));
        }
        
        // Account takeover pattern analysis
        if (scope.getAnalysisType() == PatternAnalysisType.ACCOUNT_TAKEOVER) {
            detectedPatterns.addAll(performAccountTakeoverAnalysis(request));
        }
        
        // Money laundering pattern analysis
        if (scope.getAnalysisType() == PatternAnalysisType.MONEY_LAUNDERING) {
            detectedPatterns.addAll(performMoneyLaunderingAnalysis(request));
        }
        
        // Filter patterns by confidence threshold
        detectedPatterns = detectedPatterns.stream()
            .filter(pattern -> pattern.getConfidence() >= request.getConfidenceThreshold())
            .sorted((p1, p2) -> Double.compare(p2.getConfidence(), p1.getConfidence()))
            .collect(java.util.stream.Collectors.toList());
        
        result.setDetectedPatterns(detectedPatterns);
        
        // Calculate overall risk score
        int overallRiskScore = calculateOverallRiskScore(detectedPatterns, request);
        result.setOverallRiskScore(overallRiskScore);
        
        // Determine risk level
        result.setRiskLevel(determineRiskLevel(overallRiskScore, detectedPatterns));
        
        // Generate insights and recommendations
        result.setInsights(generateInsights(detectedPatterns, request));
        result.setRecommendations(generateRecommendations(detectedPatterns, overallRiskScore));
        
        // Determine if immediate action is required
        result.setRequiresImmediateAction(requiresImmediateAction(detectedPatterns, overallRiskScore));
        
        result.setAnalysisEndTime(Instant.now());
        result.setAnalysisTimeMs(
            ChronoUnit.MILLIS.between(result.getAnalysisStartTime(), result.getAnalysisEndTime())
        );
        
        return result;
    }

    private List<DetectedPattern> performBehavioralAnalysis(PatternAnalysisRequest request) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Login behavior patterns
        BehaviorPattern loginPattern = behavioralService.analyzeLoginBehavior(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (loginPattern.isAnomalous()) {
            patterns.add(createDetectedPattern(
                "ANOMALOUS_LOGIN_BEHAVIOR",
                loginPattern.getConfidence(),
                loginPattern.getDescription(),
                loginPattern.getEvidence()
            ));
        }
        
        // Transaction timing patterns
        BehaviorPattern timingPattern = behavioralService.analyzeTransactionTiming(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (timingPattern.isAnomalous()) {
            patterns.add(createDetectedPattern(
                "ANOMALOUS_TRANSACTION_TIMING",
                timingPattern.getConfidence(),
                timingPattern.getDescription(),
                timingPattern.getEvidence()
            ));
        }
        
        // Amount pattern analysis
        BehaviorPattern amountPattern = behavioralService.analyzeAmountPatterns(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (amountPattern.isAnomalous()) {
            patterns.add(createDetectedPattern(
                "ANOMALOUS_AMOUNT_PATTERN",
                amountPattern.getConfidence(),
                amountPattern.getDescription(),
                amountPattern.getEvidence()
            ));
        }
        
        // Geographic behavior patterns
        BehaviorPattern geoPattern = behavioralService.analyzeGeographicBehavior(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (geoPattern.isAnomalous()) {
            patterns.add(createDetectedPattern(
                "ANOMALOUS_GEOGRAPHIC_BEHAVIOR",
                geoPattern.getConfidence(),
                geoPattern.getDescription(),
                geoPattern.getEvidence()
            ));
        }
        
        // Device usage patterns
        BehaviorPattern devicePattern = behavioralService.analyzeDeviceUsage(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (devicePattern.isAnomalous()) {
            patterns.add(createDetectedPattern(
                "ANOMALOUS_DEVICE_USAGE",
                devicePattern.getConfidence(),
                devicePattern.getDescription(),
                devicePattern.getEvidence()
            ));
        }
        
        return patterns;
    }

    private List<DetectedPattern> performTransactionPatternAnalysis(PatternAnalysisRequest request) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Round dollar amounts (possible money laundering)
        TransactionPattern roundAmountPattern = transactionPatternService.analyzeRoundAmounts(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (roundAmountPattern.isSignificant()) {
            patterns.add(createDetectedPattern(
                "ROUND_AMOUNT_PATTERN",
                roundAmountPattern.getConfidence(),
                "Frequent use of round dollar amounts",
                roundAmountPattern.getEvidence()
            ));
        }
        
        // Structuring patterns (amounts just below reporting thresholds)
        TransactionPattern structuringPattern = transactionPatternService.analyzeStructuring(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (structuringPattern.isSignificant()) {
            patterns.add(createDetectedPattern(
                "STRUCTURING_PATTERN",
                structuringPattern.getConfidence(),
                "Transactions structured to avoid reporting thresholds",
                structuringPattern.getEvidence()
            ));
        }
        
        // Rapid fire transactions
        TransactionPattern rapidFirePattern = transactionPatternService.analyzeRapidFireTransactions(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (rapidFirePattern.isSignificant()) {
            patterns.add(createDetectedPattern(
                "RAPID_FIRE_TRANSACTIONS",
                rapidFirePattern.getConfidence(),
                "Unusually rapid sequence of transactions",
                rapidFirePattern.getEvidence()
            ));
        }
        
        // Payment method switching patterns
        TransactionPattern paymentSwitchPattern = transactionPatternService.analyzePaymentMethodSwitching(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (paymentSwitchPattern.isSignificant()) {
            patterns.add(createDetectedPattern(
                "PAYMENT_METHOD_SWITCHING",
                paymentSwitchPattern.getConfidence(),
                "Frequent switching between payment methods",
                paymentSwitchPattern.getEvidence()
            ));
        }
        
        // Merchant category hopping
        TransactionPattern categoryHoppingPattern = transactionPatternService.analyzeMerchantCategoryHopping(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (categoryHoppingPattern.isSignificant()) {
            patterns.add(createDetectedPattern(
                "MERCHANT_CATEGORY_HOPPING",
                categoryHoppingPattern.getConfidence(),
                "Transactions across diverse merchant categories",
                categoryHoppingPattern.getEvidence()
            ));
        }
        
        return patterns;
    }

    private List<DetectedPattern> performVelocityPatternAnalysis(PatternAnalysisRequest request) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Burst patterns
        VelocityPattern burstPattern = velocityPatternService.analyzeBurstPatterns(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (burstPattern.isAnomalous()) {
            patterns.add(createDetectedPattern(
                "VELOCITY_BURST_PATTERN",
                burstPattern.getConfidence(),
                "Sudden burst in transaction velocity",
                burstPattern.getEvidence()
            ));
        }
        
        // Cyclical patterns
        VelocityPattern cyclicalPattern = velocityPatternService.analyzeCyclicalPatterns(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (cyclicalPattern.isSignificant()) {
            patterns.add(createDetectedPattern(
                "CYCLICAL_VELOCITY_PATTERN",
                cyclicalPattern.getConfidence(),
                "Unusual cyclical transaction patterns",
                cyclicalPattern.getEvidence()
            ));
        }
        
        // Weekend/holiday patterns
        VelocityPattern timePattern = velocityPatternService.analyzeTimeBasedPatterns(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (timePattern.isAnomalous()) {
            patterns.add(createDetectedPattern(
                "TIME_BASED_VELOCITY_PATTERN",
                timePattern.getConfidence(),
                "Unusual timing patterns in transaction velocity",
                timePattern.getEvidence()
            ));
        }
        
        return patterns;
    }

    private List<DetectedPattern> performAccountTakeoverAnalysis(PatternAnalysisRequest request) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Sudden behavior change
        AccountTakeoverPattern behaviorChangePattern = accountTakeoverService.analyzeBehaviorChange(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (behaviorChangePattern.isIndicative()) {
            patterns.add(createDetectedPattern(
                "SUDDEN_BEHAVIOR_CHANGE",
                behaviorChangePattern.getConfidence(),
                "Sudden change in account usage patterns",
                behaviorChangePattern.getEvidence()
            ));
        }
        
        // New device usage
        AccountTakeoverPattern newDevicePattern = accountTakeoverService.analyzeNewDeviceUsage(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (newDevicePattern.isIndicative()) {
            patterns.add(createDetectedPattern(
                "NEW_DEVICE_USAGE",
                newDevicePattern.getConfidence(),
                "Account accessed from new devices",
                newDevicePattern.getEvidence()
            ));
        }
        
        // Geographic impossibility
        AccountTakeoverPattern geoImpossibilityPattern = accountTakeoverService.analyzeGeographicImpossibility(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (geoImpossibilityPattern.isIndicative()) {
            patterns.add(createDetectedPattern(
                "GEOGRAPHIC_IMPOSSIBILITY",
                geoImpossibilityPattern.getConfidence(),
                "Geographically impossible account access patterns",
                geoImpossibilityPattern.getEvidence()
            ));
        }
        
        return patterns;
    }

    private List<DetectedPattern> performMoneyLaunderingAnalysis(PatternAnalysisRequest request) {
        List<DetectedPattern> patterns = new ArrayList<>();
        
        // Layering patterns
        MoneyLaunderingPattern layeringPattern = moneyLaunderingService.analyzeLayeringPatterns(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (layeringPattern.isSuspicious()) {
            patterns.add(createDetectedPattern(
                "LAYERING_PATTERN",
                layeringPattern.getConfidence(),
                "Complex layering of transactions to obscure money trail",
                layeringPattern.getEvidence()
            ));
        }
        
        // Smurfing patterns
        MoneyLaunderingPattern smurfingPattern = moneyLaunderingService.analyzeSmurfingPatterns(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (smurfingPattern.isSuspicious()) {
            patterns.add(createDetectedPattern(
                "SMURFING_PATTERN",
                smurfingPattern.getConfidence(),
                "Multiple small transactions to avoid detection",
                smurfingPattern.getEvidence()
            ));
        }
        
        // Integration patterns
        MoneyLaunderingPattern integrationPattern = moneyLaunderingService.analyzeIntegrationPatterns(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        if (integrationPattern.isSuspicious()) {
            patterns.add(createDetectedPattern(
                "INTEGRATION_PATTERN",
                integrationPattern.getConfidence(),
                "Suspicious integration of funds into legitimate financial system",
                integrationPattern.getEvidence()
            ));
        }
        
        return patterns;
    }

    private DetectedPattern createDetectedPattern(String patternType, double confidence, 
                                                String description, Map<String, Object> evidence) {
        return DetectedPattern.builder()
            .patternType(patternType)
            .confidence(confidence)
            .description(description)
            .evidence(evidence)
            .detectedAt(Instant.now())
            .riskScore(calculatePatternRiskScore(patternType, confidence))
            .build();
    }

    private int calculatePatternRiskScore(String patternType, double confidence) {
        int baseScore = (int) (confidence * 100);
        
        // Adjust based on pattern type criticality
        if (CRITICAL_PATTERNS.contains(patternType)) {
            baseScore = Math.min(100, baseScore + 20);
        }
        
        return baseScore;
    }

    private int calculateOverallRiskScore(List<DetectedPattern> patterns, PatternAnalysisRequest request) {
        if (patterns.isEmpty()) {
            return 10; // Minimal risk for no patterns
        }
        
        // Calculate weighted score based on pattern types and confidence
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        
        for (DetectedPattern pattern : patterns) {
            double weight = getPatternWeight(pattern.getPatternType());
            weightedSum += pattern.getRiskScore() * weight;
            totalWeight += weight;
        }
        
        int averageScore = (int) (weightedSum / totalWeight);
        
        // Boost score if multiple high-confidence patterns
        long highConfidencePatterns = patterns.stream()
            .filter(p -> p.getConfidence() > 0.8)
            .count();
        
        if (highConfidencePatterns > 2) {
            averageScore = Math.min(100, averageScore + 15);
        }
        
        // Boost score for critical patterns
        boolean hasCriticalPattern = patterns.stream()
            .anyMatch(p -> CRITICAL_PATTERNS.contains(p.getPatternType()));
        
        if (hasCriticalPattern) {
            averageScore = Math.min(100, averageScore + 20);
        }
        
        return averageScore;
    }

    private double getPatternWeight(String patternType) {
        // Higher weights for more serious patterns
        Map<String, Double> weights = Map.of(
            "COORDINATED_ATTACK", 1.0,
            "MONEY_LAUNDERING_RING", 1.0,
            "ACCOUNT_TAKEOVER_PATTERN", 0.9,
            "LAYERING_PATTERN", 0.9,
            "STRUCTURING_PATTERN", 0.8,
            "SMURFING_PATTERN", 0.8,
            "GEOGRAPHIC_IMPOSSIBILITY", 0.7,
            "RAPID_FIRE_TRANSACTIONS", 0.6
        );
        
        return weights.getOrDefault(patternType, 0.5);
    }

    private String determineRiskLevel(int overallRiskScore, List<DetectedPattern> patterns) {
        // Check for critical patterns first
        boolean hasCriticalPattern = patterns.stream()
            .anyMatch(p -> CRITICAL_PATTERNS.contains(p.getPatternType()));
        
        if (hasCriticalPattern || overallRiskScore >= 85) {
            return "CRITICAL";
        }
        
        if (overallRiskScore >= 70) {
            return "HIGH";
        }
        
        if (overallRiskScore >= 50) {
            return "MEDIUM";
        }
        
        if (overallRiskScore >= 25) {
            return "LOW";
        }
        
        return "MINIMAL";
    }

    private List<String> generateInsights(List<DetectedPattern> patterns, PatternAnalysisRequest request) {
        List<String> insights = new ArrayList<>();
        
        // Pattern frequency insights
        if (patterns.size() > 5) {
            insights.add("Multiple suspicious patterns detected simultaneously");
        }
        
        // High confidence insights
        long highConfidenceCount = patterns.stream()
            .filter(p -> p.getConfidence() > 0.9)
            .count();
        
        if (highConfidenceCount > 0) {
            insights.add(String.format("%d high-confidence patterns detected", highConfidenceCount));
        }
        
        // Pattern type insights
        boolean hasMoneyLaunderingIndicators = patterns.stream()
            .anyMatch(p -> p.getPatternType().contains("LAUNDERING") || 
                          p.getPatternType().contains("LAYERING") ||
                          p.getPatternType().contains("STRUCTURING"));
        
        if (hasMoneyLaunderingIndicators) {
            insights.add("Money laundering indicators present");
        }
        
        // Account takeover insights
        boolean hasAccountTakeoverIndicators = patterns.stream()
            .anyMatch(p -> p.getPatternType().contains("TAKEOVER") || 
                          p.getPatternType().contains("DEVICE") ||
                          p.getPatternType().contains("GEOGRAPHIC"));
        
        if (hasAccountTakeoverIndicators) {
            insights.add("Account takeover indicators detected");
        }
        
        return insights;
    }

    private List<String> generateRecommendations(List<DetectedPattern> patterns, int overallRiskScore) {
        List<String> recommendations = new ArrayList<>();
        
        if (overallRiskScore >= 85) {
            recommendations.add("IMMEDIATE_INVESTIGATION_REQUIRED");
            recommendations.add("BLOCK_ACCOUNT_TRANSACTIONS");
            recommendations.add("ESCALATE_TO_FRAUD_TEAM");
        } else if (overallRiskScore >= 70) {
            recommendations.add("ENHANCED_MONITORING");
            recommendations.add("MANUAL_REVIEW_REQUIRED");
            recommendations.add("APPLY_TRANSACTION_LIMITS");
        } else if (overallRiskScore >= 50) {
            recommendations.add("INCREASE_MONITORING_FREQUENCY");
            recommendations.add("REVIEW_ACCOUNT_ACTIVITY");
        }
        
        // Pattern-specific recommendations
        for (DetectedPattern pattern : patterns) {
            if (pattern.getPatternType().contains("LAUNDERING")) {
                recommendations.add("FILE_SUSPICIOUS_ACTIVITY_REPORT");
            }
            
            if (pattern.getPatternType().contains("TAKEOVER")) {
                recommendations.add("FORCE_AUTHENTICATION_RESET");
            }
            
            if (pattern.getPatternType().contains("VELOCITY")) {
                recommendations.add("APPLY_VELOCITY_CONTROLS");
            }
        }
        
        return recommendations.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    private boolean requiresImmediateAction(List<DetectedPattern> patterns, int overallRiskScore) {
        return overallRiskScore >= 85 ||
               patterns.stream().anyMatch(p -> CRITICAL_PATTERNS.contains(p.getPatternType())) ||
               patterns.stream().anyMatch(p -> p.getConfidence() > 0.95);
    }

    private void performAnomalyDetection(PatternAnalysisRequest request, PatternAnalysisResult result) {
        // Statistical anomaly detection
        AnomalyDetectionResult statisticalAnomalies = anomalyService.detectStatisticalAnomalies(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        result.setStatisticalAnomalies(statisticalAnomalies);
        
        // ML-based anomaly detection
        AnomalyDetectionResult mlAnomalies = anomalyService.detectMLAnomalies(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        result.setMlAnomalies(mlAnomalies);
        
        // Time series anomaly detection
        AnomalyDetectionResult timeSeriesAnomalies = anomalyService.detectTimeSeriesAnomalies(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        result.setTimeSeriesAnomalies(timeSeriesAnomalies);
    }

    private void performGraphAnalysis(PatternAnalysisRequest request, PatternAnalysisResult result) {
        // Build entity relationship graph
        EntityGraph graph = graphAnalysisService.buildEntityGraph(
            request.getEntityId(),
            request.getRelatedEntities(),
            parseTimeWindow(request.getTimeWindow())
        );
        
        // Detect fraud rings
        List<FraudRing> fraudRings = graphAnalysisService.detectFraudRings(graph);
        result.setDetectedFraudRings(fraudRings);
        
        // Calculate centrality measures
        CentralityMeasures centrality = graphAnalysisService.calculateCentrality(
            graph, 
            request.getEntityId()
        );
        result.setCentralityMeasures(centrality);
        
        // Community detection
        List<EntityCommunity> communities = graphAnalysisService.detectCommunities(graph);
        result.setDetectedCommunities(communities);
    }

    private void performTimeSeriesAnalysis(PatternAnalysisRequest request, PatternAnalysisResult result) {
        // Transaction volume time series analysis
        TimeSeriesAnalysisResult volumeAnalysis = timeSeriesService.analyzeTransactionVolume(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        result.setVolumeTimeSeriesAnalysis(volumeAnalysis);
        
        // Amount time series analysis
        TimeSeriesAnalysisResult amountAnalysis = timeSeriesService.analyzeTransactionAmounts(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        result.setAmountTimeSeriesAnalysis(amountAnalysis);
        
        // Frequency analysis
        TimeSeriesAnalysisResult frequencyAnalysis = timeSeriesService.analyzeTransactionFrequency(
            request.getEntityId(),
            parseTimeWindow(request.getTimeWindow())
        );
        result.setFrequencyTimeSeriesAnalysis(frequencyAnalysis);
    }

    private void updateMLModels(PatternAnalysisRequest request, PatternAnalysisResult result) {
        // Update fraud detection models
        mlModelService.updateFraudDetectionModel(
            request.getEntityId(),
            result.getDetectedPatterns()
        );
        
        // Update anomaly detection models
        if (result.getStatisticalAnomalies() != null) {
            mlModelService.updateAnomalyDetectionModel(
                request.getEntityId(),
                result.getStatisticalAnomalies()
            );
        }
        
        // Update behavioral models
        mlModelService.updateBehavioralModel(
            request.getEntityId(),
            request.getEntityType(),
            result.getDetectedPatterns()
        );
    }

    private void triggerAutomatedResponses(PatternAnalysisRequest request, PatternAnalysisResult result) {
        List<String> actionsTriggered = new ArrayList<>();
        
        // Block account for critical patterns
        if ("CRITICAL".equals(result.getRiskLevel())) {
            accountControlService.blockAccount(
                request.getEntityId(),
                "CRITICAL_PATTERN_DETECTED",
                request.getRequestId()
            );
            actionsTriggered.add("ACCOUNT_BLOCKED");
        }
        
        // Apply transaction limits for high risk
        if (result.getOverallRiskScore() > 70) {
            transactionControlService.applyEmergencyLimits(
                request.getEntityId(),
                "PATTERN_ANALYSIS_HIGH_RISK"
            );
            actionsTriggered.add("EMERGENCY_LIMITS_APPLIED");
        }
        
        // File suspicious activity report for money laundering patterns
        boolean hasMLPatterns = result.getDetectedPatterns().stream()
            .anyMatch(p -> p.getPatternType().contains("LAUNDERING"));
        
        if (hasMLPatterns) {
            complianceService.fileAutomaticSAR(
                request.getEntityId(),
                result.getDetectedPatterns(),
                "PATTERN_ANALYSIS_ML_INDICATORS"
            );
            actionsTriggered.add("SAR_FILED");
        }
        
        result.setAutomatedActionsTriggered(actionsTriggered);
    }

    private void sendPatternNotifications(PatternAnalysisRequest request, PatternAnalysisResult result) {
        Map<String, Object> notificationData = Map.of(
            "requestId", request.getRequestId(),
            "entityId", request.getEntityId(),
            "analysisType", request.getAnalysisType().toString(),
            "overallRiskScore", result.getOverallRiskScore(),
            "riskLevel", result.getRiskLevel(),
            "patternCount", result.getDetectedPatterns().size(),
            "criticalPatterns", result.getDetectedPatterns().stream()
                .filter(p -> CRITICAL_PATTERNS.contains(p.getPatternType()))
                .count()
        );
        
        // Critical notifications
        if ("CRITICAL".equals(result.getRiskLevel())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalPatternAlert(notificationData);
                notificationService.sendExecutiveAlert("CRITICAL_PATTERN_DETECTED", notificationData);
            });
        }
        
        // High risk notifications
        if (result.getOverallRiskScore() > 70) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendHighRiskPatternAlert(notificationData);
            });
        }
        
        // Team notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendTeamNotification(
                "FRAUD_TEAM",
                "PATTERN_ANALYSIS_COMPLETE",
                notificationData
            );
        });
    }

    private void updatePatternKnowledgeBase(PatternAnalysisRequest request, PatternAnalysisResult result) {
        // Store detected patterns in knowledge base
        for (DetectedPattern pattern : result.getDetectedPatterns()) {
            patternKnowledgeService.storePattern(
                request.getEntityId(),
                request.getEntityType(),
                pattern
            );
        }
        
        // Update pattern statistics
        patternStatsService.updatePatternStatistics(
            request.getAnalysisType(),
            result.getDetectedPatterns()
        );
        
        // Update global pattern trends
        patternTrendService.updateGlobalTrends(result.getDetectedPatterns());
    }

    private void auditPatternAnalysis(PatternAnalysisRequest request, PatternAnalysisResult result, 
                                    GenericKafkaEvent originalEvent) {
        auditService.auditPatternAnalysis(
            request.getRequestId(),
            request.getEntityId(),
            request.getAnalysisType().toString(),
            result.getDetectedPatterns().size(),
            result.getOverallRiskScore(),
            result.getRiskLevel(),
            originalEvent.getEventId()
        );
    }

    private void recordPatternMetrics(PatternAnalysisRequest request, PatternAnalysisResult result, 
                                    long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordPatternAnalysisMetrics(
            request.getAnalysisType().toString(),
            request.getAnalysisDepth().toString(),
            result.getDetectedPatterns().size(),
            result.getOverallRiskScore(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS
        );
        
        // Record pattern type metrics
        for (DetectedPattern pattern : result.getDetectedPatterns()) {
            metricsService.recordPatternTypeMetrics(
                pattern.getPatternType(),
                pattern.getConfidence(),
                pattern.getRiskScore()
            );
        }
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("pattern-analysis-validation-errors", event);
    }

    private void handleCriticalPatternError(GenericKafkaEvent event, CriticalPatternException e) {
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_PATTERN_ANALYSIS_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("pattern-analysis-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying pattern analysis {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("pattern-analysis-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for pattern analysis {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "pattern-analysis");
        
        kafkaTemplate.send("pattern-analysis.DLQ", event);
        
        alertingService.createDLQAlert(
            "pattern-analysis",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handlePatternAnalysisFailure(GenericKafkaEvent event, String topic, int partition,
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for pattern analysis: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Pattern Analysis Circuit Breaker Open",
            "Pattern analysis is failing. Advanced fraud detection compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Double extractDouble(Map<String, Object> map, String key, Double defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Double) return (Double) value;

        // Handle BigDecimal with MoneyMath for precision
        if (value instanceof BigDecimal) {
            return (double) MoneyMath.toMLFeature((BigDecimal) value);
        }

        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class CriticalPatternException extends RuntimeException {
        public CriticalPatternException(String message) {
            super(message);
        }
    }
}