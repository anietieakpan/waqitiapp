package com.waqiti.frauddetection.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.model.*;
import com.waqiti.frauddetection.repository.FraudDetectionRepository;
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

/**
 * Production-grade Kafka consumer for fraud detection events
 * Handles real-time fraud scoring, pattern detection, and automated response
 * 
 * Critical for: Transaction security, fraud prevention, risk mitigation, regulatory compliance
 * SLA: Must process fraud alerts within 500ms for real-time transaction decisions
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionConsumer {

    private final FraudDetectionRepository fraudRepository;
    private final FraudScoringService scoringService;
    private final PatternDetectionService patternService;
    private final RiskEngineService riskEngineService;
    private final MachineLearningService mlService;
    private final RuleEngineService ruleEngineService;
    private final FraudResponseService responseService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 500; // 500ms for real-time decisions
    private static final int HIGH_RISK_THRESHOLD = 75;
    private static final int CRITICAL_RISK_THRESHOLD = 90;
    
    @KafkaListener(
        topics = {"fraud-detection"},
        groupId = "fraud-detection-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "fraud-detection-processor", fallbackMethod = "handleFraudDetectionFailure")
    @Retry(name = "fraud-detection-processor")
    public void processFraudDetectionEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing fraud detection event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            FraudDetectionRequest fraudRequest = extractFraudRequest(payload);
            
            // Validate fraud request
            validateFraudRequest(fraudRequest);
            
            // Check for duplicate processing
            if (isDuplicateFraudRequest(fraudRequest)) {
                log.warn("Duplicate fraud request detected: {}, skipping", fraudRequest.getRequestId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Real-time fraud scoring
            FraudScore fraudScore = performFraudScoring(fraudRequest);
            
            // Pattern detection analysis
            PatternAnalysisResult patternAnalysis = performPatternAnalysis(fraudRequest);
            
            // Machine learning fraud detection
            MLFraudDetectionResult mlResult = performMLDetection(fraudRequest);
            
            // Rule-based fraud detection
            RuleEngineResult ruleResult = performRuleBasedDetection(fraudRequest);
            
            // Aggregate fraud assessment
            FraudAssessment fraudAssessment = aggregateFraudAssessment(
                fraudRequest, fraudScore, patternAnalysis, mlResult, ruleResult);
            
            // Determine fraud response
            FraudResponse fraudResponse = determineFraudResponse(fraudAssessment);
            
            // Execute fraud response actions
            executeFraudResponse(fraudRequest, fraudAssessment, fraudResponse);
            
            // Update fraud models with feedback
            updateFraudModels(fraudRequest, fraudAssessment);
            
            // Send fraud notifications
            sendFraudNotifications(fraudRequest, fraudAssessment, fraudResponse);
            
            // Update monitoring systems
            updateMonitoringSystems(fraudRequest, fraudAssessment);
            
            // Create audit trail
            auditFraudDetection(fraudRequest, fraudAssessment, event);
            
            // Record metrics
            recordFraudMetrics(fraudRequest, fraudAssessment, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed fraud detection: {} score: {} response: {} in {}ms", 
                    fraudRequest.getRequestId(), fraudAssessment.getFinalScore(), 
                    fraudResponse.getAction(), System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for fraud detection event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalFraudException e) {
            log.error("Critical fraud detection failed: {}", eventId, e);
            handleCriticalFraudError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process fraud detection event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private FraudDetectionRequest extractFraudRequest(Map<String, Object> payload) {
        return FraudDetectionRequest.builder()
            .requestId(extractString(payload, "requestId", UUID.randomUUID().toString()))
            .transactionId(extractString(payload, "transactionId", null))
            .customerId(extractString(payload, "customerId", null))
            .merchantId(extractString(payload, "merchantId", null))
            .accountId(extractString(payload, "accountId", null))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", "USD"))
            .transactionType(extractString(payload, "transactionType", null))
            .paymentMethod(extractString(payload, "paymentMethod", null))
            .merchantCategory(extractString(payload, "merchantCategory", null))
            .deviceId(extractString(payload, "deviceId", null))
            .deviceFingerprint(extractString(payload, "deviceFingerprint", null))
            .ipAddress(extractString(payload, "ipAddress", null))
            .userAgent(extractString(payload, "userAgent", null))
            .location(extractMap(payload, "location"))
            .sessionId(extractString(payload, "sessionId", null))
            .channelType(extractString(payload, "channelType", null))
            .authenticationMethod(extractString(payload, "authenticationMethod", null))
            .previousTransactionId(extractString(payload, "previousTransactionId", null))
            .timeFromLastTransaction(extractLong(payload, "timeFromLastTransaction", null))
            .velocityData(extractMap(payload, "velocityData"))
            .riskFactors(extractStringList(payload, "riskFactors"))
            .contextData(extractMap(payload, "contextData"))
            .metadata(extractMap(payload, "metadata"))
            .timestamp(Instant.now())
            .build();
    }

    private void validateFraudRequest(FraudDetectionRequest request) {
        if (request.getTransactionId() == null || request.getTransactionId().isEmpty()) {
            throw new ValidationException("Transaction ID is required for fraud detection");
        }
        
        if (request.getCustomerId() == null || request.getCustomerId().isEmpty()) {
            throw new ValidationException("Customer ID is required for fraud detection");
        }
        
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Valid transaction amount is required");
        }
        
        if (request.getTransactionType() == null || request.getTransactionType().isEmpty()) {
            throw new ValidationException("Transaction type is required");
        }
        
        // Validate critical fraud detection data
        if (request.getIpAddress() == null || request.getDeviceId() == null) {
            log.warn("Missing critical fraud detection data for transaction: {}", request.getTransactionId());
        }
        
        // Validate location data if provided
        if (request.getLocation() != null && !isValidLocationData(request.getLocation())) {
            log.warn("Invalid location data for transaction: {}", request.getTransactionId());
        }
    }

    private boolean isValidLocationData(Map<String, Object> location) {
        try {
            Double latitude = extractDouble(location, "latitude", null);
            Double longitude = extractDouble(location, "longitude", null);
            
            return latitude != null && longitude != null &&
                   latitude >= -90 && latitude <= 90 &&
                   longitude >= -180 && longitude <= 180;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDuplicateFraudRequest(FraudDetectionRequest request) {
        return fraudRepository.existsByRequestIdAndTimestampAfter(
            request.getRequestId(),
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private FraudScore performFraudScoring(FraudDetectionRequest request) {
        // Multi-dimensional fraud scoring
        FraudScoringContext context = FraudScoringContext.builder()
            .request(request)
            .customerProfile(getCustomerProfile(request.getCustomerId()))
            .merchantProfile(getMerchantProfile(request.getMerchantId()))
            .deviceProfile(getDeviceProfile(request.getDeviceId()))
            .locationProfile(getLocationProfile(request.getLocation()))
            .velocityProfile(getVelocityProfile(request))
            .build();
        
        // Base fraud score calculation
        int baseScore = scoringService.calculateBaseScore(context);
        
        // Amount-based scoring
        int amountScore = scoringService.calculateAmountScore(
            request.getAmount(), request.getCustomerId(), request.getMerchantId());
        
        // Velocity-based scoring
        int velocityScore = scoringService.calculateVelocityScore(request.getVelocityData());
        
        // Device-based scoring
        int deviceScore = scoringService.calculateDeviceScore(
            request.getDeviceId(), request.getDeviceFingerprint(), request.getCustomerId());
        
        // Location-based scoring
        int locationScore = scoringService.calculateLocationScore(
            request.getLocation(), request.getCustomerId(), request.getIpAddress());
        
        // Behavioral scoring
        int behavioralScore = scoringService.calculateBehavioralScore(
            request.getCustomerId(), request.getTransactionType(), request.getContextData());
        
        // Time-based scoring
        int timeScore = scoringService.calculateTimeScore(
            request.getTimestamp(), request.getTimeFromLastTransaction());
        
        // Authentication scoring
        int authScore = scoringService.calculateAuthenticationScore(
            request.getAuthenticationMethod(), request.getSessionId());
        
        // Aggregate final score
        int finalScore = scoringService.aggregateScores(
            baseScore, amountScore, velocityScore, deviceScore, 
            locationScore, behavioralScore, timeScore, authScore);
        
        return FraudScore.builder()
            .requestId(request.getRequestId())
            .baseScore(baseScore)
            .amountScore(amountScore)
            .velocityScore(velocityScore)
            .deviceScore(deviceScore)
            .locationScore(locationScore)
            .behavioralScore(behavioralScore)
            .timeScore(timeScore)
            .authenticationScore(authScore)
            .finalScore(finalScore)
            .riskLevel(determineRiskLevel(finalScore))
            .calculatedAt(Instant.now())
            .build();
    }

    private String determineRiskLevel(int score) {
        if (score >= CRITICAL_RISK_THRESHOLD) return "CRITICAL";
        if (score >= HIGH_RISK_THRESHOLD) return "HIGH";
        if (score >= 50) return "MEDIUM";
        if (score >= 25) return "LOW";
        return "MINIMAL";
    }

    private PatternAnalysisResult performPatternAnalysis(FraudDetectionRequest request) {
        // Analyze fraud patterns
        List<FraudPattern> detectedPatterns = new ArrayList<>();
        
        // Velocity patterns
        VelocityPattern velocityPattern = patternService.analyzeVelocityPattern(
            request.getCustomerId(), request.getAmount(), request.getTimestamp());
        if (velocityPattern.isSuspicious()) {
            detectedPatterns.add(velocityPattern.toFraudPattern());
        }
        
        // Geographic patterns
        GeographicPattern geoPattern = patternService.analyzeGeographicPattern(
            request.getCustomerId(), request.getLocation(), request.getIpAddress());
        if (geoPattern.isSuspicious()) {
            detectedPatterns.add(geoPattern.toFraudPattern());
        }
        
        // Merchant patterns
        MerchantPattern merchantPattern = patternService.analyzeMerchantPattern(
            request.getCustomerId(), request.getMerchantId(), request.getMerchantCategory());
        if (merchantPattern.isSuspicious()) {
            detectedPatterns.add(merchantPattern.toFraudPattern());
        }
        
        // Device patterns
        DevicePattern devicePattern = patternService.analyzeDevicePattern(
            request.getCustomerId(), request.getDeviceId(), request.getDeviceFingerprint());
        if (devicePattern.isSuspicious()) {
            detectedPatterns.add(devicePattern.toFraudPattern());
        }
        
        // Behavioral patterns
        BehavioralPattern behavioralPattern = patternService.analyzeBehavioralPattern(
            request.getCustomerId(), request.getTransactionType(), request.getContextData());
        if (behavioralPattern.isSuspicious()) {
            detectedPatterns.add(behavioralPattern.toFraudPattern());
        }
        
        // Account takeover patterns
        AccountTakeoverPattern atoPattern = patternService.analyzeAccountTakeoverPattern(
            request.getCustomerId(), request.getDeviceId(), request.getAuthenticationMethod());
        if (atoPattern.isSuspicious()) {
            detectedPatterns.add(atoPattern.toFraudPattern());
        }
        
        // Calculate pattern risk score
        int patternRiskScore = patternService.calculatePatternRiskScore(detectedPatterns);
        
        return PatternAnalysisResult.builder()
            .requestId(request.getRequestId())
            .detectedPatterns(detectedPatterns)
            .patternCount(detectedPatterns.size())
            .patternRiskScore(patternRiskScore)
            .highRiskPatterns(detectedPatterns.stream()
                .filter(p -> p.getRiskLevel().equals("HIGH") || p.getRiskLevel().equals("CRITICAL"))
                .count())
            .analysisTimestamp(Instant.now())
            .build();
    }

    private MLFraudDetectionResult performMLDetection(FraudDetectionRequest request) {
        // Prepare ML feature vector
        MLFeatureVector featureVector = mlService.prepareFeatureVector(request);
        
        // Ensemble model prediction
        List<MLModelResult> modelResults = new ArrayList<>();
        
        // Gradient Boosting model
        MLModelResult gbResult = mlService.predictWithGradientBoosting(featureVector);
        modelResults.add(gbResult);
        
        // Neural Network model
        MLModelResult nnResult = mlService.predictWithNeuralNetwork(featureVector);
        modelResults.add(nnResult);
        
        // Random Forest model
        MLModelResult rfResult = mlService.predictWithRandomForest(featureVector);
        modelResults.add(rfResult);
        
        // Isolation Forest (anomaly detection)
        MLModelResult ifResult = mlService.predictWithIsolationForest(featureVector);
        modelResults.add(ifResult);
        
        // LSTM model (sequence analysis)
        MLModelResult lstmResult = mlService.predictWithLSTM(featureVector);
        modelResults.add(lstmResult);
        
        // Ensemble prediction
        EnsemblePrediction ensemblePrediction = mlService.aggregateModelResults(modelResults);
        
        // Calculate confidence intervals
        ConfidenceInterval confidence = mlService.calculateConfidenceInterval(modelResults);
        
        // Feature importance analysis
        FeatureImportance featureImportance = mlService.analyzeFeatureImportance(
            featureVector, modelResults);
        
        return MLFraudDetectionResult.builder()
            .requestId(request.getRequestId())
            .featureVector(featureVector)
            .modelResults(modelResults)
            .ensemblePrediction(ensemblePrediction)
            .fraudProbability(ensemblePrediction.getFraudProbability())
            .mlScore(ensemblePrediction.getScore())
            .confidence(confidence)
            .featureImportance(featureImportance)
            .modelVersion(mlService.getCurrentModelVersion())
            .predictionTimestamp(Instant.now())
            .build();
    }

    private RuleEngineResult performRuleBasedDetection(FraudDetectionRequest request) {
        // Execute fraud detection rules
        List<RuleResult> ruleResults = new ArrayList<>();
        
        // Amount-based rules
        ruleResults.addAll(ruleEngineService.executeAmountRules(request));
        
        // Velocity rules
        ruleResults.addAll(ruleEngineService.executeVelocityRules(request));
        
        // Geographic rules
        ruleResults.addAll(ruleEngineService.executeGeographicRules(request));
        
        // Device rules
        ruleResults.addAll(ruleEngineService.executeDeviceRules(request));
        
        // Merchant rules
        ruleResults.addAll(ruleEngineService.executeMerchantRules(request));
        
        // Time-based rules
        ruleResults.addAll(ruleEngineService.executeTimeRules(request));
        
        // Authentication rules
        ruleResults.addAll(ruleEngineService.executeAuthenticationRules(request));
        
        // Behavioral rules
        ruleResults.addAll(ruleEngineService.executeBehavioralRules(request));
        
        // Custom business rules
        ruleResults.addAll(ruleEngineService.executeCustomRules(request));
        
        // Aggregate rule results
        RuleAggregation aggregation = ruleEngineService.aggregateRuleResults(ruleResults);
        
        return RuleEngineResult.builder()
            .requestId(request.getRequestId())
            .ruleResults(ruleResults)
            .triggeredRules(ruleResults.stream()
                .filter(RuleResult::isTriggered)
                .count())
            .highRiskRules(ruleResults.stream()
                .filter(r -> r.isTriggered() && ("HIGH".equals(r.getRiskLevel()) || "CRITICAL".equals(r.getRiskLevel())))
                .count())
            .ruleScore(aggregation.getAggregatedScore())
            .ruleDecision(aggregation.getDecision())
            .conflictingRules(aggregation.getConflictingRules())
            .executionTimestamp(Instant.now())
            .build();
    }

    private FraudAssessment aggregateFraudAssessment(FraudDetectionRequest request,
                                                    FraudScore fraudScore,
                                                    PatternAnalysisResult patternAnalysis,
                                                    MLFraudDetectionResult mlResult,
                                                    RuleEngineResult ruleResult) {
        
        // Weight different assessment components
        double scoreWeight = 0.3;
        double patternWeight = 0.25;
        double mlWeight = 0.3;
        double ruleWeight = 0.15;
        
        // Calculate weighted final score
        double weightedScore = (fraudScore.getFinalScore() * scoreWeight) +
                              (patternAnalysis.getPatternRiskScore() * patternWeight) +
                              (mlResult.getMlScore() * mlWeight) +
                              (ruleResult.getRuleScore() * ruleWeight);
        
        int finalScore = Math.min((int) Math.round(weightedScore), 100);
        
        // Determine overall fraud decision
        FraudDecision decision = determineFraudDecision(
            finalScore, fraudScore, patternAnalysis, mlResult, ruleResult);
        
        // Extract key fraud indicators
        List<FraudIndicator> indicators = extractFraudIndicators(
            fraudScore, patternAnalysis, mlResult, ruleResult);
        
        // Calculate confidence level
        double confidence = calculateConfidenceLevel(fraudScore, patternAnalysis, mlResult, ruleResult);
        
        return FraudAssessment.builder()
            .requestId(request.getRequestId())
            .transactionId(request.getTransactionId())
            .customerId(request.getCustomerId())
            .fraudScore(fraudScore)
            .patternAnalysis(patternAnalysis)
            .mlResult(mlResult)
            .ruleResult(ruleResult)
            .finalScore(finalScore)
            .riskLevel(determineRiskLevel(finalScore))
            .decision(decision)
            .indicators(indicators)
            .confidence(confidence)
            .processingTime(ChronoUnit.MILLIS.between(request.getTimestamp(), Instant.now()))
            .assessmentTimestamp(Instant.now())
            .build();
    }

    private FraudDecision determineFraudDecision(int finalScore,
                                               FraudScore fraudScore,
                                               PatternAnalysisResult patternAnalysis,
                                               MLFraudDetectionResult mlResult,
                                               RuleEngineResult ruleResult) {
        
        // Critical risk - immediate block
        if (finalScore >= CRITICAL_RISK_THRESHOLD ||
            ruleResult.getHighRiskRules() > 0 ||
            mlResult.getFraudProbability() > 0.9) {
            return FraudDecision.BLOCK;
        }
        
        // High risk - challenge or review
        if (finalScore >= HIGH_RISK_THRESHOLD ||
            patternAnalysis.getHighRiskPatterns() > 2 ||
            mlResult.getFraudProbability() > 0.7) {
            return FraudDecision.CHALLENGE;
        }
        
        // Medium risk - monitor
        if (finalScore >= 50 ||
            patternAnalysis.getPatternCount() > 3 ||
            mlResult.getFraudProbability() > 0.4) {
            return FraudDecision.MONITOR;
        }
        
        // Low risk - allow
        return FraudDecision.ALLOW;
    }

    private List<FraudIndicator> extractFraudIndicators(FraudScore fraudScore,
                                                       PatternAnalysisResult patternAnalysis,
                                                       MLFraudDetectionResult mlResult,
                                                       RuleEngineResult ruleResult) {
        List<FraudIndicator> indicators = new ArrayList<>();
        
        // High scoring components
        if (fraudScore.getVelocityScore() > HIGH_RISK_THRESHOLD) {
            indicators.add(new FraudIndicator("HIGH_VELOCITY", "Unusual transaction velocity detected", "HIGH"));
        }
        
        if (fraudScore.getLocationScore() > HIGH_RISK_THRESHOLD) {
            indicators.add(new FraudIndicator("LOCATION_ANOMALY", "Suspicious location pattern", "HIGH"));
        }
        
        if (fraudScore.getDeviceScore() > HIGH_RISK_THRESHOLD) {
            indicators.add(new FraudIndicator("DEVICE_RISK", "High-risk device detected", "HIGH"));
        }
        
        // Pattern indicators
        for (FraudPattern pattern : patternAnalysis.getDetectedPatterns()) {
            if ("HIGH".equals(pattern.getRiskLevel()) || "CRITICAL".equals(pattern.getRiskLevel())) {
                indicators.add(new FraudIndicator(
                    pattern.getPatternType(), pattern.getDescription(), pattern.getRiskLevel()));
            }
        }
        
        // ML indicators
        if (mlResult.getFraudProbability() > 0.7) {
            indicators.add(new FraudIndicator(
                "ML_HIGH_RISK", "Machine learning models indicate high fraud risk", "HIGH"));
        }
        
        // Rule indicators
        ruleResult.getRuleResults().stream()
            .filter(r -> r.isTriggered() && ("HIGH".equals(r.getRiskLevel()) || "CRITICAL".equals(r.getRiskLevel())))
            .forEach(r -> indicators.add(new FraudIndicator(
                r.getRuleName(), r.getDescription(), r.getRiskLevel())));
        
        return indicators;
    }

    private double calculateConfidenceLevel(FraudScore fraudScore,
                                          PatternAnalysisResult patternAnalysis,
                                          MLFraudDetectionResult mlResult,
                                          RuleEngineResult ruleResult) {
        
        // Base confidence from score consistency
        double scoreConsistency = calculateScoreConsistency(fraudScore);
        
        // ML model confidence
        double mlConfidence = mlResult.getConfidence().getConfidenceLevel();
        
        // Pattern confidence
        double patternConfidence = patternAnalysis.getPatternCount() > 0 ? 0.8 : 0.6;
        
        // Rule confidence
        double ruleConfidence = ruleResult.getTriggeredRules() > 0 ? 0.9 : 0.7;
        
        // Weighted confidence
        return (scoreConsistency * 0.3) + (mlConfidence * 0.4) + 
               (patternConfidence * 0.15) + (ruleConfidence * 0.15);
    }

    private double calculateScoreConsistency(FraudScore fraudScore) {
        int[] scores = {
            fraudScore.getAmountScore(),
            fraudScore.getVelocityScore(),
            fraudScore.getDeviceScore(),
            fraudScore.getLocationScore(),
            fraudScore.getBehavioralScore()
        };
        
        double mean = Arrays.stream(scores).average().orElse(0.0);
        double variance = Arrays.stream(scores)
            .mapToDouble(s -> Math.pow(s - mean, 2))
            .average().orElse(0.0);
        
        double standardDeviation = Math.sqrt(variance);
        
        // Lower standard deviation = higher consistency = higher confidence
        return Math.max(0.1, 1.0 - (standardDeviation / 100.0));
    }

    private FraudResponse determineFraudResponse(FraudAssessment assessment) {
        return responseService.determineFraudResponse(assessment);
    }

    private void executeFraudResponse(FraudDetectionRequest request,
                                    FraudAssessment assessment,
                                    FraudResponse response) {
        
        CompletableFuture.runAsync(() -> {
            try {
                responseService.executeFraudResponse(request, assessment, response);
            } catch (Exception e) {
                log.error("Failed to execute fraud response for transaction: {}", 
                         request.getTransactionId(), e);
            }
        });
    }

    private void updateFraudModels(FraudDetectionRequest request, FraudAssessment assessment) {
        // Update ML models with new data
        CompletableFuture.runAsync(() -> {
            try {
                mlService.updateModelsWithFeedback(request, assessment);
            } catch (Exception e) {
                log.error("Failed to update ML models for transaction: {}", 
                         request.getTransactionId(), e);
            }
        });
        
        // Update pattern detection models
        CompletableFuture.runAsync(() -> {
            try {
                patternService.updatePatternsWithFeedback(request, assessment);
            } catch (Exception e) {
                log.error("Failed to update pattern models for transaction: {}", 
                         request.getTransactionId(), e);
            }
        });
    }

    private void sendFraudNotifications(FraudDetectionRequest request,
                                      FraudAssessment assessment,
                                      FraudResponse response) {
        
        Map<String, Object> notificationData = Map.of(
            "transactionId", request.getTransactionId(),
            "customerId", request.getCustomerId(),
            "fraudScore", assessment.getFinalScore(),
            "riskLevel", assessment.getRiskLevel(),
            "decision", assessment.getDecision().toString(),
            "response", response.getAction().toString(),
            "confidence", assessment.getConfidence(),
            "indicators", assessment.getIndicators().size()
        );
        
        // Critical fraud alerts
        if (assessment.getFinalScore() >= CRITICAL_RISK_THRESHOLD) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalFraudAlert(notificationData);
                notificationService.sendExecutiveAlert("CRITICAL_FRAUD", notificationData);
            });
        }
        
        // High risk fraud alerts
        if (assessment.getFinalScore() >= HIGH_RISK_THRESHOLD) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendHighRiskFraudAlert(notificationData);
            });
        }
        
        // Merchant notifications
        if (request.getMerchantId() != null) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendMerchantFraudNotification(
                    request.getMerchantId(), notificationData);
            });
        }
        
        // Customer notifications (for blocked transactions)
        if (response.getAction() == FraudResponseAction.BLOCK) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCustomerFraudNotification(
                    request.getCustomerId(), notificationData);
            });
        }
    }

    private void updateMonitoringSystems(FraudDetectionRequest request, FraudAssessment assessment) {
        // Update fraud monitoring dashboards
        monitoringService.updateFraudDashboard(request, assessment);
        
        // Update risk monitoring
        riskMonitoringService.updateFraudRiskMetrics(request, assessment);
        
        // Update operational metrics
        operationalMetricsService.updateFraudOperations(request, assessment);
    }

    private void auditFraudDetection(FraudDetectionRequest request,
                                   FraudAssessment assessment,
                                   GenericKafkaEvent event) {
        auditService.auditFraudDetection(
            request.getRequestId(),
            request.getTransactionId(),
            request.getCustomerId(),
            assessment.getFinalScore(),
            assessment.getRiskLevel(),
            assessment.getDecision().toString(),
            assessment.getConfidence(),
            event.getEventId()
        );
    }

    private void recordFraudMetrics(FraudDetectionRequest request,
                                  FraudAssessment assessment,
                                  long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordFraudDetectionMetrics(
            assessment.getFinalScore(),
            assessment.getRiskLevel(),
            assessment.getDecision().toString(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS,
            assessment.getConfidence()
        );
        
        // Record fraud patterns
        metricsService.recordFraudPatterns(
            assessment.getPatternAnalysis().getDetectedPatterns()
        );
        
        // Record ML performance
        metricsService.recordMLPerformance(
            assessment.getMlResult().getFraudProbability(),
            assessment.getMlResult().getConfidence().getConfidenceLevel()
        );
    }

    // Helper methods for profile retrieval
    private CustomerProfile getCustomerProfile(String customerId) {
        return customerProfileService.getProfile(customerId);
    }

    private MerchantProfile getMerchantProfile(String merchantId) {
        return merchantProfileService.getProfile(merchantId);
    }

    private DeviceProfile getDeviceProfile(String deviceId) {
        return deviceProfileService.getProfile(deviceId);
    }

    private LocationProfile getLocationProfile(Map<String, Object> location) {
        return locationProfileService.getProfile(location);
    }

    private VelocityProfile getVelocityProfile(FraudDetectionRequest request) {
        return velocityProfileService.getProfile(request.getCustomerId(), request.getVelocityData());
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("fraud-detection-validation-errors", event);
    }

    private void handleCriticalFraudError(GenericKafkaEvent event, CriticalFraudException e) {
        // Create emergency alert for critical fraud processing failures
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_FRAUD_PROCESSING_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("fraud-detection-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying fraud detection event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("fraud-detection-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for fraud detection event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "fraud-detection");
        
        kafkaTemplate.send("fraud-detection.DLQ", event);
        
        alertingService.createDLQAlert(
            "fraud-detection",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleFraudDetectionFailure(GenericKafkaEvent event, String topic, int partition,
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for fraud detection processing: {}", e.getMessage());
        
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
            "Fraud Detection Circuit Breaker Open",
            "Fraud detection processing is failing. Transaction security compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Extract BigDecimal with null-safe handling
     * 
     * SECURITY FIX: Return BigDecimal.ZERO instead of null to prevent NPE in fraud calculations
     * Critical for ensuring fraud scoring calculations never fail due to null values
     */
    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            log.warn("FRAUD_DATA_WARNING: Missing numeric value for key '{}' in fraud analysis - using zero", key);
            return BigDecimal.ZERO;
        }
        
        try {
            if (value instanceof BigDecimal) return (BigDecimal) value;
            if (value instanceof Number) return new BigDecimal(value.toString());
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to extract BigDecimal for key '{}' - fraud calculation compromised", key, e);
            return BigDecimal.ZERO; // Safe fallback for fraud calculations
        }
    }

    private Long extractLong(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
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
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class CriticalFraudException extends RuntimeException {
        public CriticalFraudException(String message) {
            super(message);
        }
    }
}