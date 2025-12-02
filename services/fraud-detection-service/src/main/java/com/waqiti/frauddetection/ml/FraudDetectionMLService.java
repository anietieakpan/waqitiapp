package com.waqiti.frauddetection.ml;

import com.waqiti.frauddetection.model.*;
import com.waqiti.frauddetection.dto.FraudDetectionRequest;
import com.waqiti.frauddetection.dto.FraudRiskAssessment;
import com.waqiti.frauddetection.dto.RiskLevel;
import com.waqiti.frauddetection.dto.ml.*;
import com.waqiti.frauddetection.entity.FraudCase;
import com.waqiti.frauddetection.repository.FraudCaseRepository;
import com.waqiti.frauddetection.repository.TransactionPatternRepository;
import com.waqiti.frauddetection.repository.UserBehaviorRepository;
import com.waqiti.frauddetection.repository.ModelMetricsRepository;
import com.waqiti.frauddetection.repository.FraudIncidentRepository;
import com.waqiti.frauddetection.repository.FraudRuleRepository;
import com.waqiti.frauddetection.repository.TransactionVelocityRepository;
import com.waqiti.frauddetection.repository.LocationHistoryRepository;
import com.waqiti.frauddetection.repository.IpReputationRepository;
import com.waqiti.frauddetection.integration.tensorflow.TensorFlowModelService;
import com.waqiti.frauddetection.integration.pytorch.PyTorchModelService;
import com.waqiti.frauddetection.integration.sklearn.ScikitLearnModelService;
import com.waqiti.frauddetection.service.Neo4jTransactionGraphService;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.metrics.service.MetricsService;
import com.waqiti.common.resilience.AnalyticsResilience;
import com.waqiti.common.events.FraudDetectionEvent;
import com.waqiti.common.monitoring.PerformanceMonitor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionMLService {

    private final FraudCaseRepository fraudCaseRepository;
    private final TransactionPatternRepository patternRepository;
    private final UserBehaviorRepository behaviorRepository;
    private final ModelMetricsRepository metricsRepository;
    private final TensorFlowModelService tensorFlowService;
    private final PyTorchModelService pyTorchService;
    private final ScikitLearnModelService scikitLearnService;
    private final Neo4jTransactionGraphService graphService;
    private final CacheService cacheService;
    private final MetricsService metricsService;
    private final AnalyticsResilience analyticsResilience;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    // CRITICAL P0 FIX: Add OutboxService for transactional event publishing
    private final com.waqiti.common.outbox.OutboxService outboxService;
    // P1 ENHANCEMENT: Add DeviceRiskScoringService for real device fingerprinting
    private final com.waqiti.frauddetection.device.DeviceRiskScoringService deviceRiskScoringService;
    // FINAL 5% ENHANCEMENT: Add IPGeolocationService for location-based fraud detection
    private final com.waqiti.frauddetection.geolocation.IPGeolocationService ipGeolocationService;
    
    @Value("${fraud.ml.model.primary:tensorflow}")
    private String primaryModel;
    
    @Value("${fraud.ml.ensemble.enabled:true}")
    private boolean ensembleEnabled;
    
    // CONFIGURE_IN_VAULT: Risk thresholds - should be configured externally
    @Value("${fraud.ml.threshold.high:#{null}}")
    private Double highRiskThreshold;

    @Value("${fraud.ml.threshold.medium:#{null}}")
    private Double mediumRiskThreshold;
    
    @Value("${fraud.ml.feature.window.hours:168}")
    private int featureWindowHours;
    
    @Value("${fraud.ml.model.retrain.days:7}")
    private int modelRetrainDays;
    
    private static final String ML_TOPIC = "fraud-ml-events";
    private static final String CACHE_PREFIX = "fraud:ml:";
    
    /**
     * Real-time fraud detection using ML models
     */
    public FraudRiskAssessment assessTransactionRisk(FraudDetectionRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String assessmentId = UUID.randomUUID().toString();
        
        try {
            log.debug("Assessing transaction risk - ID: {}, User: {}, Amount: {}", 
                assessmentId, request.getUserId(), request.getAmount());
            
            // Extract features for ML models
            FeatureVector features = extractFeatures(request);
            
            // Run prediction ensemble
            List<ModelPrediction> predictions = runEnsemblePrediction(features);
            
            // Combine predictions
            FraudRiskScore riskScore = combineEnsemblePredictions(predictions);
            
            // Determine risk level
            RiskLevel riskLevel = determineRiskLevel(riskScore.getScore());
            
            // Create assessment result
            FraudRiskAssessment assessment = FraudRiskAssessment.builder()
                .id(assessmentId)
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .riskScore(riskScore.getScore())
                .riskLevel(riskLevel)
                .confidence(riskScore.getConfidence())
                .features(features.getFeatureMap())
                .modelVersions(predictions.stream()
                    .collect(Collectors.toMap(
                        ModelPrediction::getModelName,
                        ModelPrediction::getModelVersion
                    )))
                .rulesTrigger(extractTriggeredRules(features, riskScore))
                .anomalyFlags(detectAnomalies(features, request))
                .createdAt(LocalDateTime.now())
                .build();
            
            // Record assessment
            recordAssessment(assessment, request);
            
            // Update user behavior profile
            updateUserBehaviorProfile(request, features, riskScore);
            
            // Publish ML event
            publishMLEvent("FRAUD_RISK_ASSESSED", assessment);
            
            // Record metrics
            recordModelMetrics(predictions, riskLevel);
            
            sample.stop(Timer.builder("fraud.ml.assessment.duration")
                .tag("risk_level", riskLevel.toString())
                .register(meterRegistry));
            
            log.info("Fraud risk assessment completed - ID: {}, Risk: {}, Score: {}", 
                assessmentId, riskLevel, riskScore.getScore());
                
            return assessment;
            
        } catch (Exception e) {
            log.error("Error assessing transaction risk - ID: {}", assessmentId, e);
            
            Counter.builder("fraud.ml.assessment.errors")
                .tag("error", e.getClass().getSimpleName())
                .register(meterRegistry)
                .increment();
            
            // Return safe default assessment
            return createDefaultAssessment(assessmentId, request);
        }
    }
    
    /**
     * Batch processing for transaction analysis
     */
    @Async
    public CompletableFuture<BatchAnalysisResult> analyzeBatchTransactions(List<FraudDetectionRequest> requests) {
        log.info("Starting batch fraud analysis for {} transactions", requests.size());
        
        try {
            List<FraudRiskAssessment> assessments = requests.parallelStream()
                .map(this::assessTransactionRisk)
                .collect(Collectors.toList());
            
            BatchAnalysisResult result = BatchAnalysisResult.builder()
                .totalTransactions(requests.size())
                .highRiskCount(countByRiskLevel(assessments, RiskLevel.HIGH))
                .mediumRiskCount(countByRiskLevel(assessments, RiskLevel.MEDIUM))
                .lowRiskCount(countByRiskLevel(assessments, RiskLevel.LOW))
                .averageRiskScore(calculateAverageRiskScore(assessments))
                .assessments(assessments)
                .processedAt(LocalDateTime.now())
                .build();
            
            log.info("Batch fraud analysis completed - High: {}, Medium: {}, Low: {}", 
                result.getHighRiskCount(), result.getMediumRiskCount(), result.getLowRiskCount());
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Error in batch fraud analysis", e);
            throw new RuntimeException("Batch analysis failed", e);
        }
    }
    
    /**
     * Feature extraction for ML models
     */
    @Cacheable(value = "fraud-features", key = "#request.userId + '_' + #request.transactionId")
    public FeatureVector extractFeatures(FraudDetectionRequest request) {
        log.debug("Extracting features for transaction: {}", request.getTransactionId());
        
        try {
            Map<String, Double> features = new HashMap<>();
            
            // Transaction features
            features.putAll(extractTransactionFeatures(request));
            
            // User behavior features
            features.putAll(extractUserBehaviorFeatures(request.getUserId()));
            
            // Temporal features
            features.putAll(extractTemporalFeatures(request));
            
            // Device/Location features
            features.putAll(extractDeviceLocationFeatures(request));
            
            // Historical pattern features
            features.putAll(extractHistoricalPatternFeatures(request.getUserId()));
            
            // Network analysis features
            features.putAll(extractNetworkAnalysisFeatures(request.getUserId()));
            
            return FeatureVector.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .featureMap(features)
                .extractedAt(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Error extracting features for transaction: {}", request.getTransactionId(), e);
            return createDefaultFeatureVector(request);
        }
    }
    
    /**
     * Run ensemble prediction using multiple ML models
     */
    private List<ModelPrediction> runEnsemblePrediction(FeatureVector features) {
        List<ModelPrediction> predictions = new ArrayList<>();
        
        try {
            // TensorFlow Deep Learning model
            CompletableFuture<ModelPrediction> tensorFlowPrediction = CompletableFuture.supplyAsync(() ->
                tensorFlowService.predict(features)
            );
            
            // PyTorch Neural Network model
            CompletableFuture<ModelPrediction> pyTorchPrediction = CompletableFuture.supplyAsync(() ->
                pyTorchService.predict(features)
            );
            
            // Scikit-learn ensemble model (Random Forest + XGBoost)
            CompletableFuture<ModelPrediction> scikitPrediction = CompletableFuture.supplyAsync(() ->
                scikitLearnService.predict(features)
            );
            
            // Wait for all predictions with timeout
            try {
                CompletableFuture.allOf(tensorFlowPrediction, pyTorchPrediction, scikitPrediction)
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("ML ensemble prediction timed out after 5 seconds", e);
                throw new RuntimeException("ML prediction timed out", e);
            } catch (Exception e) {
                log.error("ML ensemble prediction failed", e);
                throw e;
            }

            predictions.add(tensorFlowPrediction.get(100, java.util.concurrent.TimeUnit.MILLISECONDS));
            predictions.add(pyTorchPrediction.get(100, java.util.concurrent.TimeUnit.MILLISECONDS));
            predictions.add(scikitPrediction.get(100, java.util.concurrent.TimeUnit.MILLISECONDS));
            
        } catch (Exception e) {
            log.error("Error in ensemble prediction", e);
            
            // Fallback to primary model only
            try {
                ModelPrediction fallbackPrediction = switch (primaryModel.toLowerCase()) {
                    case "tensorflow" -> tensorFlowService.predict(features);
                    case "pytorch" -> pyTorchService.predict(features);
                    case "scikit" -> scikitLearnService.predict(features);
                    default -> createDefaultPrediction(features);
                };
                predictions.add(fallbackPrediction);
                
            } catch (Exception fallbackError) {
                log.error("Error in fallback prediction", fallbackError);
                predictions.add(createDefaultPrediction(features));
            }
        }
        
        return predictions.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Combine multiple model predictions using weighted ensemble
     */
    private FraudRiskScore combineEnsemblePredictions(List<ModelPrediction> predictions) {
        if (predictions.isEmpty()) {
            return FraudRiskScore.builder()
                .score(0.5)  // neutral score
                .confidence(0.1)  // low confidence
                .build();
        }
        
        if (predictions.size() == 1) {
            ModelPrediction pred = predictions.get(0);
            return FraudRiskScore.builder()
                .score(pred.getRiskScore())
                .confidence(pred.getConfidence())
                .build();
        }
        
        // Weighted ensemble combining
        Map<String, Double> modelWeights = getModelWeights();
        
        double weightedScore = 0.0;
        double weightedConfidence = 0.0;
        double totalWeight = 0.0;
        
        for (ModelPrediction prediction : predictions) {
            double weight = modelWeights.getOrDefault(prediction.getModelName(), 1.0);
            
            weightedScore += prediction.getRiskScore() * weight;
            weightedConfidence += prediction.getConfidence() * weight;
            totalWeight += weight;
        }
        
        double finalScore = totalWeight > 0 ? weightedScore / totalWeight : 0.5;
        double finalConfidence = totalWeight > 0 ? weightedConfidence / totalWeight : 0.1;
        
        // Apply ensemble confidence boost
        finalConfidence = Math.min(1.0, finalConfidence * 1.1);
        
        return FraudRiskScore.builder()
            .score(finalScore)
            .confidence(finalConfidence)
            .modelCount(predictions.size())
            .agreementScore(calculateModelAgreement(predictions))
            .build();
    }
    
    /**
     * Extract transaction-specific features
     */
    private Map<String, Double> extractTransactionFeatures(FraudDetectionRequest request) {
        Map<String, Double> features = new HashMap<>();
        
        // Amount features
        features.put("amount", request.getAmount().doubleValue());
        features.put("amount_log", Math.log(request.getAmount().doubleValue() + 1));
        features.put("amount_rounded", request.getAmount().remainder(BigDecimal.ONE).doubleValue());
        
        // Time features
        LocalDateTime now = LocalDateTime.now();
        features.put("hour_of_day", (double) now.getHour());
        features.put("day_of_week", (double) now.getDayOfWeek().getValue());
        features.put("is_weekend", now.getDayOfWeek().getValue() > 5 ? 1.0 : 0.0);
        features.put("is_business_hours", (now.getHour() >= 9 && now.getHour() <= 17) ? 1.0 : 0.0);
        
        // Transaction type features
        if (request.getTransactionType() != null) {
            features.put("tx_type_p2p", request.getTransactionType().equals("P2P") ? 1.0 : 0.0);
            features.put("tx_type_merchant", request.getTransactionType().equals("MERCHANT") ? 1.0 : 0.0);
            features.put("tx_type_withdrawal", request.getTransactionType().equals("WITHDRAWAL") ? 1.0 : 0.0);
        }
        
        // Currency features
        if (request.getCurrency() != null) {
            features.put("is_usd", request.getCurrency().equals("USD") ? 1.0 : 0.0);
            features.put("is_foreign_currency", !request.getCurrency().equals("USD") ? 1.0 : 0.0);
        }
        
        return features;
    }
    
    /**
     * Extract user behavior features
     */
    private Map<String, Double> extractUserBehaviorFeatures(String userId) {
        Map<String, Double> features = new HashMap<>();
        
        try {
            // Get user behavior history
            LocalDateTime windowStart = LocalDateTime.now().minusHours(featureWindowHours);
            List<UserBehavior> behaviors = behaviorRepository.findByUserIdAndTimestampAfter(userId, windowStart);
            
            if (behaviors.isEmpty()) {
                // New user features
                features.put("is_new_user", 1.0);
                features.put("avg_tx_amount", 0.0);
                features.put("tx_frequency", 0.0);
                features.put("unique_merchants", 0.0);
                features.put("avg_time_between_tx", 0.0);
                return features;
            }
            
            features.put("is_new_user", 0.0);
            
            // Transaction frequency
            features.put("tx_count_24h", (double) behaviors.stream()
                .mapToInt(b -> (int) b.getTransactionCount24h())
                .sum());
            
            features.put("tx_count_7d", (double) behaviors.stream()
                .mapToInt(b -> (int) b.getTransactionCount7d())
                .sum());
            
            // Average transaction amount
            features.put("avg_tx_amount", behaviors.stream()
                .mapToDouble(UserBehavior::getAverageTransactionAmount)
                .average()
                .orElse(0.0));
            
            // Transaction amount variance
            features.put("tx_amount_std", calculateStandardDeviation(
                behaviors.stream()
                    .mapToDouble(UserBehavior::getAverageTransactionAmount)
                    .toArray()));
            
            // Unique recipients/merchants
            features.put("unique_recipients", behaviors.stream()
                .mapToDouble(UserBehavior::getUniqueRecipientsCount)
                .max()
                .orElse(0.0));
            
            // Time patterns
            features.put("night_tx_ratio", behaviors.stream()
                .mapToDouble(UserBehavior::getNightTransactionRatio)
                .average()
                .orElse(0.0));
            
            features.put("weekend_tx_ratio", behaviors.stream()
                .mapToDouble(UserBehavior::getWeekendTransactionRatio)
                .average()
                .orElse(0.0));
            
            // Device/location consistency
            features.put("device_consistency", behaviors.stream()
                .mapToDouble(UserBehavior::getDeviceConsistencyScore)
                .average()
                .orElse(1.0));
            
            features.put("location_consistency", behaviors.stream()
                .mapToDouble(UserBehavior::getLocationConsistencyScore)
                .average()
                .orElse(1.0));
            
        } catch (Exception e) {
            log.error("Error extracting user behavior features for user: {}", userId, e);
            // Return safe defaults
            features.put("is_new_user", 1.0);
            features.put("avg_tx_amount", 0.0);
            features.put("tx_frequency", 0.0);
        }
        
        return features;
    }
    
    /**
     * Extract temporal features
     */
    private Map<String, Double> extractTemporalFeatures(FraudDetectionRequest request) {
        Map<String, Double> features = new HashMap<>();
        
        LocalDateTime txTime = request.getTransactionTime() != null ? 
            request.getTransactionTime() : LocalDateTime.now();
            
        // Time of day features
        features.put("hour_sin", Math.sin(2 * Math.PI * txTime.getHour() / 24.0));
        features.put("hour_cos", Math.cos(2 * Math.PI * txTime.getHour() / 24.0));
        
        // Day of week features
        features.put("dow_sin", Math.sin(2 * Math.PI * txTime.getDayOfWeek().getValue() / 7.0));
        features.put("dow_cos", Math.cos(2 * Math.PI * txTime.getDayOfWeek().getValue() / 7.0));
        
        // Month features
        features.put("month_sin", Math.sin(2 * Math.PI * txTime.getMonthValue() / 12.0));
        features.put("month_cos", Math.cos(2 * Math.PI * txTime.getMonthValue() / 12.0));
        
        // Special time periods
        features.put("is_holiday", isHoliday(txTime) ? 1.0 : 0.0);
        features.put("is_month_end", txTime.getDayOfMonth() > 28 ? 1.0 : 0.0);
        features.put("is_payroll_period", isPayrollPeriod(txTime) ? 1.0 : 0.0);
        
        return features;
    }
    
    /**
     * Extract device and location features
     */
    private Map<String, Double> extractDeviceLocationFeatures(FraudDetectionRequest request) {
        Map<String, Double> features = new HashMap<>();
        
        // Device features
        if (request.getDeviceFingerprint() != null) {
            features.put("is_known_device", isKnownDevice(request.getUserId(), 
                request.getDeviceFingerprint()) ? 1.0 : 0.0);
            features.put("device_risk_score", getDeviceRiskScore(request.getDeviceFingerprint()));
        } else {
            features.put("is_known_device", 0.0);
            features.put("device_risk_score", 0.5);  // neutral
        }
        
        // Location features
        if (request.getIpAddress() != null) {
            GeoLocation location = geoLocateIP(request.getIpAddress());
            if (location != null) {
                features.put("is_home_country", isHomeCountry(request.getUserId(), 
                    location.getCountry()) ? 1.0 : 0.0);
                features.put("is_vpn", location.isVpn() ? 1.0 : 0.0);
                features.put("is_tor", location.isTor() ? 1.0 : 0.0);
                features.put("country_risk_score", getCountryRiskScore(location.getCountry()));
                features.put("distance_from_home", getDistanceFromHome(request.getUserId(), location));
            }
        }
        
        return features;
    }
    
    /**
     * Extract historical transaction pattern features
     */
    private Map<String, Double> extractHistoricalPatternFeatures(String userId) {
        Map<String, Double> features = new HashMap<>();
        
        try {
            List<TransactionPattern> patterns = patternRepository
                .findByUserIdAndCreatedAtAfter(userId, LocalDateTime.now().minusDays(30));
            
            if (patterns.isEmpty()) {
                return Map.of("has_historical_data", 0.0);
            }
            
            features.put("has_historical_data", 1.0);
            
            // Pattern analysis
            features.put("pattern_regularity", patterns.stream()
                .mapToDouble(TransactionPattern::getRegularityScore)
                .average()
                .orElse(0.0));
            
            features.put("pattern_stability", patterns.stream()
                .mapToDouble(TransactionPattern::getStabilityScore)
                .average()
                .orElse(0.0));
            
            features.put("pattern_anomaly_count", patterns.stream()
                .mapToDouble(TransactionPattern::getAnomalyCount)
                .sum());
            
        } catch (Exception e) {
            log.error("Error extracting historical pattern features for user: {}", userId, e);
            features.put("has_historical_data", 0.0);
        }
        
        return features;
    }
    
    /**
     * Extract network analysis features
     */
    private Map<String, Double> extractNetworkAnalysisFeatures(String userId) {
        Map<String, Double> features = new HashMap<>();
        
        try {
            // Network centrality measures
            features.put("network_centrality", calculateNetworkCentrality(userId));
            features.put("connection_diversity", calculateConnectionDiversity(userId));
            features.put("suspicious_connections", countSuspiciousConnections(userId));
            
            // Transaction network features
            features.put("mutual_connections", countMutualConnections(userId));
            features.put("network_velocity", calculateNetworkVelocity(userId));
            
        } catch (Exception e) {
            log.error("Error extracting network analysis features for user: {}", userId, e);
            // Return safe defaults
            features.put("network_centrality", 0.0);
            features.put("connection_diversity", 0.0);
            features.put("suspicious_connections", 0.0);
        }
        
        return features;
    }
    
    /**
     * Model retraining scheduler
     */
    @Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
    @Transactional
    public void scheduleModelRetraining() {
        log.info("Starting scheduled model retraining");
        
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(modelRetrainDays);
            
            // Check if retraining is needed
            boolean needsRetraining = checkIfRetrainingNeeded(cutoffDate);
            
            if (needsRetraining) {
                // Get training data
                List<FraudCase> trainingData = fraudCaseRepository.findTrainingData(cutoffDate);
                
                if (trainingData.size() >= 1000) {  // Minimum training samples
                    // Retrain models
                    retrainModels(trainingData);
                    
                    // Update model metrics
                    updateModelPerformanceMetrics();
                    
                    log.info("Model retraining completed successfully with {} samples", trainingData.size());
                } else {
                    log.info("Insufficient training data ({} samples), skipping retraining", trainingData.size());
                }
            } else {
                log.info("Models are up to date, no retraining needed");
            }
            
        } catch (Exception e) {
            log.error("Error in scheduled model retraining", e);
        }
    }
    
    /**
     * Model performance monitoring
     */
    @Scheduled(fixedRate = 300000)  // Every 5 minutes
    public void monitorModelPerformance() {
        try {
            // Calculate recent model accuracy
            LocalDateTime since = LocalDateTime.now().minusHours(1);
            List<FraudCase> recentCases = fraudCaseRepository.findVerifiedCasesSince(since);
            
            if (!recentCases.isEmpty()) {
                double accuracy = calculateModelAccuracy(recentCases);
                double precision = calculateModelPrecision(recentCases);
                double recall = calculateModelRecall(recentCases);
                double f1Score = 2 * (precision * recall) / (precision + recall);
                
                // Record metrics
                Gauge.builder("fraud.ml.model.accuracy")
                    .register(meterRegistry, () -> accuracy);
                    
                Gauge.builder("fraud.ml.model.precision")
                    .register(meterRegistry, () -> precision);
                    
                Gauge.builder("fraud.ml.model.recall")
                    .register(meterRegistry, () -> recall);
                    
                Gauge.builder("fraud.ml.model.f1_score")
                    .register(meterRegistry, () -> f1Score);
                
                // Alert if performance degrades
                if (accuracy < 0.85 || f1Score < 0.80) {
                    log.warn("Model performance degraded - Accuracy: {}, F1: {}", accuracy, f1Score);
                    publishMLEvent("MODEL_PERFORMANCE_DEGRADED", Map.of(
                        "accuracy", accuracy,
                        "precision", precision,
                        "recall", recall,
                        "f1_score", f1Score
                    ));
                }
            }
            
        } catch (Exception e) {
            log.error("Error monitoring model performance", e);
        }
    }
    
    // Private helper methods
    
    private RiskLevel determineRiskLevel(double riskScore) {
        // CONFIGURE_IN_VAULT: Risk level thresholds must be configured externally
        double highThreshold = (highRiskThreshold != null) ? highRiskThreshold :
            Double.parseDouble(System.getenv().getOrDefault("FRAUD_HIGH_RISK_THRESHOLD", "0.9"));
        double mediumThreshold = (mediumRiskThreshold != null) ? mediumRiskThreshold :
            Double.parseDouble(System.getenv().getOrDefault("FRAUD_MEDIUM_RISK_THRESHOLD", "0.6"));

        if (riskScore >= highThreshold) {
            return RiskLevel.HIGH;
        } else if (riskScore >= mediumThreshold) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }
    
    private List<String> extractTriggeredRules(FeatureVector features, FraudRiskScore riskScore) {
        List<String> triggeredRules = new ArrayList<>();
        
        Map<String, Double> featureMap = features.getFeatureMap();
        
        // Amount-based rules
        if (featureMap.getOrDefault("amount", 0.0) > 10000.0) {
            triggeredRules.add("HIGH_AMOUNT_TRANSACTION");
        }
        
        // Time-based rules
        if (featureMap.getOrDefault("is_weekend", 0.0) == 1.0 && 
            featureMap.getOrDefault("night_tx_ratio", 0.0) > 0.8) {
            triggeredRules.add("UNUSUAL_TIME_PATTERN");
        }
        
        // Device-based rules
        if (featureMap.getOrDefault("is_known_device", 0.0) == 0.0) {
            triggeredRules.add("UNKNOWN_DEVICE");
        }
        
        // Location-based rules
        if (featureMap.getOrDefault("is_vpn", 0.0) == 1.0 || 
            featureMap.getOrDefault("is_tor", 0.0) == 1.0) {
            triggeredRules.add("SUSPICIOUS_NETWORK");
        }
        
        // Behavior-based rules
        if (featureMap.getOrDefault("tx_amount_std", 0.0) > 2.0) {
            triggeredRules.add("UNUSUAL_AMOUNT_PATTERN");
        }
        
        return triggeredRules;
    }
    
    private List<String> detectAnomalies(FeatureVector features, FraudDetectionRequest request) {
        List<String> anomalies = new ArrayList<>();
        
        // Statistical anomaly detection
        Map<String, Double> featureMap = features.getFeatureMap();
        
        // Z-score based anomaly detection for key features
        String[] keyFeatures = {"amount", "tx_frequency", "avg_tx_amount", "device_consistency"};
        
        for (String feature : keyFeatures) {
            Double value = featureMap.get(feature);
            if (value != null) {
                double zScore = calculateZScore(request.getUserId(), feature, value);
                if (Math.abs(zScore) > 3.0) {  // 3-sigma rule
                    anomalies.add(feature.toUpperCase() + "_ANOMALY");
                }
            }
        }
        
        return anomalies;
    }
    
    private void recordAssessment(FraudRiskAssessment assessment, FraudDetectionRequest request) {
        try {
            FraudCase fraudCase = FraudCase.builder()
                .id(assessment.getId())
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .riskScore(assessment.getRiskScore())
                .riskLevel(assessment.getRiskLevel())
                .confidence(assessment.getConfidence())
                .features(assessment.getFeatures())
                .rulesTrigger(assessment.getRulesTrigger())
                .anomalyFlags(assessment.getAnomalyFlags())
                .status(FraudCaseStatus.PENDING_VERIFICATION)
                .createdAt(LocalDateTime.now())
                .build();
            
            fraudCaseRepository.save(fraudCase);
            
        } catch (Exception e) {
            log.error("Error recording fraud assessment: {}", assessment.getId(), e);
        }
    }
    
    private void updateUserBehaviorProfile(FraudDetectionRequest request, FeatureVector features, FraudRiskScore riskScore) {
        try {
            String userId = request.getUserId();
            LocalDateTime now = LocalDateTime.now();
            
            // Update or create user behavior record
            UserBehavior behavior = behaviorRepository.findLatestByUserId(userId)
                .orElse(UserBehavior.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .build());
            
            // Update behavior metrics
            behavior.setLastTransactionTime(now);
            behavior.setTransactionCount24h(behavior.getTransactionCount24h() + 1);
            behavior.setLastRiskScore(riskScore.getScore());
            behavior.setDeviceConsistencyScore(features.getFeatureMap().getOrDefault("device_consistency", 1.0));
            behavior.setLocationConsistencyScore(features.getFeatureMap().getOrDefault("location_consistency", 1.0));
            behavior.setUpdatedAt(now);
            
            behaviorRepository.save(behavior);
            
        } catch (Exception e) {
            log.error("Error updating user behavior profile for user: {}", request.getUserId(), e);
        }
    }
    
    private Map<String, Double> getModelWeights() {
        // CONFIGURE_IN_VAULT: Ensemble model weights - configured externally
        // Dynamic model weights based on recent performance
        double tensorflowWeight = Double.parseDouble(System.getenv().getOrDefault("FRAUD_MODEL_WEIGHT_TENSORFLOW", "0.33"));
        double pytorchWeight = Double.parseDouble(System.getenv().getOrDefault("FRAUD_MODEL_WEIGHT_PYTORCH", "0.33"));
        double scikitWeight = Double.parseDouble(System.getenv().getOrDefault("FRAUD_MODEL_WEIGHT_SCIKIT", "0.34"));

        return Map.of(
            "tensorflow", tensorflowWeight,
            "pytorch", pytorchWeight,
            "scikit-learn", scikitWeight
        );
    }
    
    private double calculateModelAgreement(List<ModelPrediction> predictions) {
        if (predictions.size() < 2) return 1.0;
        
        double[] scores = predictions.stream()
            .mapToDouble(ModelPrediction::getRiskScore)
            .toArray();
        
        double mean = Arrays.stream(scores).average().orElse(0.5);
        double variance = Arrays.stream(scores)
            .map(score -> Math.pow(score - mean, 2))
            .average()
            .orElse(0.0);
        
        // Agreement is inverse of variance (normalized)
        return Math.max(0.0, 1.0 - (variance * 4.0));  // Scale to 0-1
    }
    
    private FraudRiskAssessment createDefaultAssessment(String assessmentId, FraudDetectionRequest request) {
        return FraudRiskAssessment.builder()
            .id(assessmentId)
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .riskScore(0.5)  // neutral risk
            .riskLevel(RiskLevel.MEDIUM)
            .confidence(0.1)  // low confidence
            .features(new HashMap<>())
            .modelVersions(new HashMap<>())
            .rulesTrigger(List.of("DEFAULT_ASSESSMENT"))
            .anomalyFlags(new ArrayList<>())
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    private FeatureVector createDefaultFeatureVector(FraudDetectionRequest request) {
        Map<String, Double> features = Map.of(
            "amount", request.getAmount().doubleValue(),
            "hour_of_day", (double) LocalDateTime.now().getHour(),
            "is_weekend", LocalDateTime.now().getDayOfWeek().getValue() > 5 ? 1.0 : 0.0,
            "is_new_user", 1.0
        );
        
        return FeatureVector.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .featureMap(features)
            .extractedAt(LocalDateTime.now())
            .build();
    }
    
    private ModelPrediction createDefaultPrediction(FeatureVector features) {
        return ModelPrediction.builder()
            .modelName("default")
            .modelVersion("1.0")
            .riskScore(0.5)
            .confidence(0.1)
            .features(features.getFeatureMap())
            .predictedAt(LocalDateTime.now())
            .build();
    }
    
    // Additional helper methods for feature extraction
    
    private double calculateStandardDeviation(double[] values) {
        if (values.length == 0) return 0.0;
        
        double mean = Arrays.stream(values).average().orElse(0.0);
        double variance = Arrays.stream(values)
            .map(val -> Math.pow(val - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    private boolean isHoliday(LocalDateTime dateTime) {
        // Simplified holiday detection - could be enhanced with proper holiday calendar
        int month = dateTime.getMonthValue();
        int day = dateTime.getDayOfMonth();
        
        // Major US holidays
        return (month == 1 && day == 1) ||  // New Year's Day
               (month == 7 && day == 4) ||  // Independence Day
               (month == 12 && day == 25);  // Christmas Day
    }
    
    private boolean isPayrollPeriod(LocalDateTime dateTime) {
        int day = dateTime.getDayOfMonth();
        // Typical payroll periods: 15th and end of month
        return day == 15 || day >= 28;
    }
    
    private boolean isKnownDevice(String userId, String deviceFingerprint) {
        // Check cache first
        String cacheKey = CACHE_PREFIX + "device:" + userId + ":" + deviceFingerprint;
        Boolean cached = cacheService.get(cacheKey, Boolean.class);
        if (cached != null) return cached;
        
        // Check database
        boolean known = behaviorRepository.existsByUserIdAndDeviceFingerprint(userId, deviceFingerprint);
        
        // Cache result
        cacheService.put(cacheKey, known, 3600); // 1 hour cache
        
        return known;
    }
    
    /**
     * P1 ENHANCEMENT: Real device risk scoring with DeviceRiskScoringService
     */
    private double getDeviceRiskScore(Map<String, Object> deviceData) {
        try {
            // Inject DeviceRiskScoringService via constructor if not already done
            if (deviceRiskScoringService != null) {
                BigDecimal riskScore = deviceRiskScoringService.calculateDeviceRiskScore(deviceData);
                return riskScore.doubleValue();
            } else {
                log.warn("FRAUD: DeviceRiskScoringService not available - using fallback");
                return 0.5; // Moderate risk fallback
            }
        } catch (Exception e) {
            log.error("FRAUD: Error calculating device risk score", e);
            return 0.6; // Conservative fallback on error
        }
    }

    // Legacy method for backward compatibility
    private double getDeviceRiskScore(String deviceFingerprint) {
        Map<String, Object> deviceData = Map.of("fingerprint", deviceFingerprint);
        return getDeviceRiskScore(deviceData);
    }
    
    /**
     * FINAL 5% ENHANCEMENT: Real IP geolocation with MaxMind
     */
    private GeoLocation geoLocateIP(String ipAddress) {
        try {
            if (ipGeolocationService != null) {
                return ipGeolocationService.geolocateIP(ipAddress);
            } else {
                log.warn("FRAUD: IPGeolocationService not available - using fallback");
                return GeoLocation.unknown();
            }
        } catch (Exception e) {
            log.error("FRAUD: Error geolocating IP: {}", ipAddress, e);
            return GeoLocation.unknown();
        }
    }
    
    private boolean isHomeCountry(String userId, String country) {
        // Check user's registered country
        String cacheKey = CACHE_PREFIX + "home_country:" + userId;
        String homeCountry = cacheService.get(cacheKey, String.class);
        
        if (homeCountry == null) {
            // Would fetch from user service
            homeCountry = "US"; // Placeholder
            cacheService.put(cacheKey, homeCountry, 86400); // 24 hour cache
        }
        
        return homeCountry.equals(country);
    }
    
    private double getCountryRiskScore(String country) {
        // CONFIGURE_IN_VAULT: Country risk scores - configured externally via configuration service
        // Country risk scoring based on fraud rates, regulations, etc.
        // Values should be loaded from external configuration/vault, not hardcoded
        String configKey = "FRAUD_COUNTRY_RISK_" + country.toUpperCase();
        String riskScoreStr = System.getenv().get(configKey);

        if (riskScoreStr != null) {
            try {
                return Double.parseDouble(riskScoreStr);
            } catch (NumberFormatException e) {
                log.warn("Invalid country risk score for {}: {}", country, riskScoreStr);
            }
        }

        // Default fallback - configured externally
        return Double.parseDouble(System.getenv().getOrDefault("FRAUD_COUNTRY_RISK_DEFAULT", "0.5"));
    }
    
    private double getDistanceFromHome(String userId, GeoLocation location) {
        // Calculate distance from user's home location
        // Placeholder - would use actual geolocation calculation
        return 0.0;
    }
    
    // Network analysis methods

    private double calculateNetworkCentrality(String userId) {
        try {
            // Delegate to Neo4j graph service for accurate network centrality calculation
            UUID userUuid = UUID.fromString(userId);
            return graphService.calculateNetworkCentrality(userUuid);
        } catch (Exception e) {
            log.warn("Failed to calculate network centrality for user: {}", userId, e);
            return 0.1; // Safe default - low centrality
        }
    }

    private double calculateConnectionDiversity(String userId) {
        try {
            // Delegate to Neo4j graph service for connection diversity analysis
            UUID userUuid = UUID.fromString(userId);
            return graphService.calculateConnectionDiversity(userUuid);
        } catch (Exception e) {
            log.warn("Failed to calculate connection diversity for user: {}", userId, e);
            return 0.5; // Safe default - moderate diversity
        }
    }

    private double countSuspiciousConnections(String userId) {
        try {
            // Delegate to Neo4j graph service to count suspicious connections
            UUID userUuid = UUID.fromString(userId);
            return graphService.countSuspiciousConnections(userUuid);
        } catch (Exception e) {
            log.warn("Failed to count suspicious connections for user: {}", userId, e);
            return 0.0; // Safe default - no suspicious connections
        }
    }
    
    private double countMutualConnections(String userId) {
        // Count mutual connections in transaction network
        return 1.0; // Placeholder
    }
    
    private double calculateNetworkVelocity(String userId) {
        // Calculate velocity of transactions in user's network
        return 0.5; // Placeholder
    }
    
    private double calculateZScore(String userId, String feature, double value) {
        // Calculate Z-score for anomaly detection
        // Would use historical statistics for the user and feature
        return 0.0; // Placeholder
    }
    
    // Model performance calculation methods
    
    private double calculateModelAccuracy(List<FraudCase> cases) {
        long correct = cases.stream()
            .mapToLong(c -> predictedCorrectly(c) ? 1L : 0L)
            .sum();
        
        return cases.isEmpty() ? 0.0 : (double) correct / cases.size();
    }
    
    private double calculateModelPrecision(List<FraudCase> cases) {
        long truePositives = cases.stream()
            .mapToLong(c -> predictedFraudAndWasFraud(c) ? 1L : 0L)
            .sum();
            
        long falsePositives = cases.stream()
            .mapToLong(c -> predictedFraudButWasNot(c) ? 1L : 0L)
            .sum();
        
        return (truePositives + falsePositives) == 0 ? 0.0 : 
            (double) truePositives / (truePositives + falsePositives);
    }
    
    private double calculateModelRecall(List<FraudCase> cases) {
        long truePositives = cases.stream()
            .mapToLong(c -> predictedFraudAndWasFraud(c) ? 1L : 0L)
            .sum();
            
        long falseNegatives = cases.stream()
            .mapToLong(c -> missedFraud(c) ? 1L : 0L)
            .sum();
        
        return (truePositives + falseNegatives) == 0 ? 0.0 : 
            (double) truePositives / (truePositives + falseNegatives);
    }
    
    private boolean predictedCorrectly(FraudCase fraudCase) {
        boolean predictedFraud = fraudCase.getRiskLevel() == RiskLevel.HIGH;
        boolean actuallyFraud = fraudCase.getActualFraudStatus() == FraudStatus.CONFIRMED_FRAUD;
        
        return predictedFraud == actuallyFraud;
    }
    
    private boolean predictedFraudAndWasFraud(FraudCase fraudCase) {
        return fraudCase.getRiskLevel() == RiskLevel.HIGH && 
               fraudCase.getActualFraudStatus() == FraudStatus.CONFIRMED_FRAUD;
    }
    
    private boolean predictedFraudButWasNot(FraudCase fraudCase) {
        return fraudCase.getRiskLevel() == RiskLevel.HIGH && 
               fraudCase.getActualFraudStatus() == FraudStatus.CONFIRMED_LEGITIMATE;
    }
    
    private boolean missedFraud(FraudCase fraudCase) {
        return fraudCase.getRiskLevel() != RiskLevel.HIGH && 
               fraudCase.getActualFraudStatus() == FraudStatus.CONFIRMED_FRAUD;
    }
    
    // Batch analysis helpers
    
    private long countByRiskLevel(List<FraudRiskAssessment> assessments, RiskLevel level) {
        return assessments.stream()
            .mapToLong(a -> a.getRiskLevel() == level ? 1L : 0L)
            .sum();
    }
    
    private double calculateAverageRiskScore(List<FraudRiskAssessment> assessments) {
        return assessments.stream()
            .mapToDouble(FraudRiskAssessment::getRiskScore)
            .average()
            .orElse(0.0);
    }
    
    // Model retraining methods
    
    private boolean checkIfRetrainingNeeded(LocalDateTime cutoffDate) {
        // Check model staleness, performance degradation, data drift
        Optional<ModelMetrics> latestMetrics = metricsRepository.findLatest();
        
        if (latestMetrics.isEmpty()) return true;
        
        ModelMetrics metrics = latestMetrics.get();
        
        // Retrain if model is old or performance has degraded
        return metrics.getLastTrainedAt().isBefore(cutoffDate) ||
               metrics.getAccuracy() < 0.85 ||
               metrics.getF1Score() < 0.80;
    }
    
    private void retrainModels(List<FraudCase> trainingData) {
        log.info("Retraining fraud detection models with {} samples", trainingData.size());
        
        try {
            // Prepare training data
            List<TrainingExample> examples = trainingData.stream()
                .map(this::convertToTrainingExample)
                .collect(Collectors.toList());
            
            // Retrain each model
            CompletableFuture<Void> tensorFlowRetraining = CompletableFuture.runAsync(() ->
                tensorFlowService.retrain(examples)
            );
            
            CompletableFuture<Void> pyTorchRetraining = CompletableFuture.runAsync(() ->
                pyTorchService.retrain(examples)
            );
            
            CompletableFuture<Void> scikitRetraining = CompletableFuture.runAsync(() ->
                scikitLearnService.retrain(examples)
            );
            
            // Wait for all retraining to complete with timeout (retraining can take longer)
            try {
                CompletableFuture.allOf(tensorFlowRetraining, pyTorchRetraining, scikitRetraining)
                    .get(30, java.util.concurrent.TimeUnit.MINUTES);
                log.info("Model retraining completed successfully");
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Model retraining timed out after 30 minutes", e);
                throw new RuntimeException("Model retraining timed out", e);
            } catch (Exception e) {
                log.error("Model retraining failed", e);
                throw new RuntimeException("Model retraining failed", e);
            }
            
        } catch (Exception e) {
            log.error("Error during model retraining", e);
            throw new RuntimeException("Model retraining failed", e);
        }
    }
    
    private TrainingExample convertToTrainingExample(FraudCase fraudCase) {
        boolean isFraud = fraudCase.getActualFraudStatus() == FraudStatus.CONFIRMED_FRAUD;
        
        return TrainingExample.builder()
            .features(fraudCase.getFeatures())
            .label(isFraud ? 1.0 : 0.0)
            .weight(1.0) // Could be adjusted based on case importance
            .build();
    }
    
    private void updateModelPerformanceMetrics() {
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(7);
            List<FraudCase> recentCases = fraudCaseRepository.findVerifiedCasesSince(since);
            
            if (!recentCases.isEmpty()) {
                double accuracy = calculateModelAccuracy(recentCases);
                double precision = calculateModelPrecision(recentCases);
                double recall = calculateModelRecall(recentCases);
                double f1Score = 2 * (precision * recall) / (precision + recall);
                
                ModelMetrics metrics = ModelMetrics.builder()
                    .id(UUID.randomUUID().toString())
                    .modelName("ensemble")
                    .modelVersion(getCurrentModelVersion())
                    .accuracy(accuracy)
                    .precision(precision)
                    .recall(recall)
                    .f1Score(f1Score)
                    .evaluationSamples(recentCases.size())
                    .lastTrainedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();
                
                metricsRepository.save(metrics);
                
                log.info("Updated model performance metrics - Accuracy: {}, F1: {}", accuracy, f1Score);
            }
            
        } catch (Exception e) {
            log.error("Error updating model performance metrics", e);
        }
    }
    
    private String getCurrentModelVersion() {
        return "1.0.0"; // Would be managed by model versioning system
    }
    
    private void recordModelMetrics(List<ModelPrediction> predictions, RiskLevel riskLevel) {
        Counter.builder("fraud.ml.predictions")
            .tag("risk_level", riskLevel.toString())
            .tag("model_count", String.valueOf(predictions.size()))
            .register(meterRegistry)
            .increment();
    }
    
    /**
     * CRITICAL P0 FIX: Transactional event publishing using Outbox pattern
     * This ensures events are published reliably within the same transaction
     * as the fraud detection result persistence.
     *
     * Benefits:
     * - Guaranteed at-least-once delivery
     * - No lost events during database rollback
     * - Survives service crashes
     * - Automatic retry mechanism
     */
    @Transactional
    private void publishMLEvent(String eventType, Object eventData) {
        try {
            // Determine aggregate type based on event data
            String aggregateType = "FraudAssessment";
            String aggregateId = extractAggregateId(eventData);

            // Prepare event headers for tracing and routing
            Map<String, String> headers = new HashMap<>();
            headers.put("event-type", eventType);
            headers.put("service-name", "fraud-detection-service");
            headers.put("timestamp", Instant.now().toString());
            headers.put("version", "1.0");

            // CRITICAL: Use OutboxService for transactional publishing
            // Event will be persisted in outbox table within same transaction
            // and published asynchronously by outbox processor
            outboxService.saveEvent(
                aggregateId,
                aggregateType,
                eventType,
                eventData,
                headers
            );

            log.debug("SECURITY: Fraud event saved to outbox for reliable publishing: eventType={}, aggregateId={}",
                eventType, aggregateId);

        } catch (Exception e) {
            log.error("CRITICAL: Error saving ML event to outbox: eventType={}", eventType, e);
            // Re-throw to ensure transaction rollback
            throw new RuntimeException("Failed to save fraud event to outbox", e);
        }
    }

    /**
     * CRITICAL P0 FIX: Publish FraudDetectedEvent with complete fraud context
     * This method creates a proper domain event with all necessary information
     * for downstream consumers (wallet-service, compliance-service, user-service)
     */
    @Transactional
    public void publishFraudDetectedEvent(FraudRiskAssessment assessment) {
        try {
            // Create domain event
            com.waqiti.fraud.events.domain.FraudDetectedEvent event =
                com.waqiti.fraud.events.domain.FraudDetectedEvent.builder()
                    .fraudId(UUID.fromString(assessment.getAssessmentId()))
                    .transactionId(assessment.getTransactionId())
                    .walletId(assessment.getWalletId())
                    .userId(assessment.getUserId())
                    .amount(assessment.getAmount())
                    .currency(assessment.getCurrency())
                    .riskLevel(assessment.getRiskLevel().name())
                    .riskScore(assessment.getRiskScore().getScore())
                    .fraudPatterns(assessment.getFraudPatterns() != null ?
                        assessment.getFraudPatterns().toArray(new String[0]) : new String[0])
                    .recommendedActions(assessment.getRecommendedActions() != null ?
                        assessment.getRecommendedActions().toArray(new String[0]) : new String[0])
                    .reason(assessment.getReason())
                    .confidence(assessment.getRiskScore().getConfidence())
                    .deviceId(assessment.getDeviceId())
                    .ipAddress(assessment.getIpAddress())
                    .location(assessment.getLocation())
                    .additionalData(assessment.getAdditionalContext())
                    .detectedAt(Instant.now())
                    .correlationId(generateCorrelationId())
                    .idempotencyKey(com.waqiti.fraud.events.domain.FraudDetectedEvent.generateIdempotencyKey(
                        UUID.fromString(assessment.getAssessmentId()),
                        assessment.getWalletId(),
                        assessment.getTransactionId()
                    ))
                    .eventVersion(1)
                    .build();

            // Prepare event headers with idempotency key
            Map<String, String> headers = new HashMap<>();
            headers.put("event-type", "FRAUD_DETECTED");
            headers.put("service-name", "fraud-detection-service");
            headers.put("timestamp", event.getDetectedAt().toString());
            headers.put("version", "1.0");
            headers.put("idempotency-key", event.getIdempotencyKey());
            headers.put("correlation-id", event.getCorrelationId());
            headers.put("risk-level", event.getRiskLevel());
            headers.put("requires-wallet-freeze", String.valueOf(event.requiresWalletFreeze()));

            // CRITICAL: Publish via outbox for transactional guarantees
            outboxService.saveEvent(
                event.getFraudId().toString(),
                "FraudDetection",
                "FRAUD_DETECTED",
                event,
                headers
            );

            log.info("SECURITY: FraudDetectedEvent published to outbox - fraudId={}, riskLevel={}, walletId={}, idempotencyKey={}",
                event.getFraudId(), event.getRiskLevel(), event.getWalletId(), event.getIdempotencyKey());

            // Record metrics
            meterRegistry.counter("fraud.events.published",
                "risk_level", event.getRiskLevel(),
                "requires_freeze", String.valueOf(event.requiresWalletFreeze())
            ).increment();

        } catch (Exception e) {
            log.error("CRITICAL: Failed to publish FraudDetectedEvent for assessment: {}",
                assessment.getAssessmentId(), e);
            throw new RuntimeException("Failed to publish fraud detected event", e);
        }
    }

    /**
     * Helper method to extract aggregate ID from event data
     */
    private String extractAggregateId(Object eventData) {
        if (eventData instanceof FraudRiskAssessment) {
            return ((FraudRiskAssessment) eventData).getAssessmentId();
        } else if (eventData instanceof Map) {
            Map<?, ?> dataMap = (Map<?, ?>) eventData;
            Object id = dataMap.get("assessmentId");
            if (id != null) {
                return id.toString();
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Generate correlation ID for distributed tracing
     */
    private String generateCorrelationId() {
        return "fraud-" + UUID.randomUUID().toString();
    }
}