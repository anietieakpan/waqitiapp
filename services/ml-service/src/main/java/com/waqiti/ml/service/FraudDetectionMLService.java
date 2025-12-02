package com.waqiti.ml.service;

import com.waqiti.ml.domain.*;
import com.waqiti.ml.dto.*;
import com.waqiti.ml.model.*;
import com.waqiti.ml.repository.*;
import com.waqiti.ml.util.*;
import com.waqiti.common.events.FraudDetectionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.classification.RandomForestClassifier;
import org.apache.spark.ml.classification.GBTClassifier;
import org.apache.spark.ml.classification.LogisticRegression;
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator;
import org.apache.spark.ml.feature.*;
import org.apache.spark.ml.linalg.Vectors;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.FloatBuffer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Advanced ML-based Fraud Detection Service
 * Implements multiple models for comprehensive fraud detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FraudDetectionMLService {

    private final FraudModelRepository fraudModelRepository;
    private final FraudDetectionResultRepository resultRepository;
    private final TransactionFeatureRepository featureRepository;
    private final FraudPatternRepository patternRepository;
    private final ModelPerformanceRepository performanceRepository;
    private final SparkSession sparkSession;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FeatureEngineeringService featureEngineeringService;
    private final ModelTrainingService modelTrainingService;
    private final AlertingService alertingService;
    
    // Model cache
    private final Map<String, PipelineModel> modelCache = new ConcurrentHashMap<>();
    private final Map<String, SavedModelBundle> deepLearningModels = new ConcurrentHashMap<>();
    private final Map<String, ModelMetadata> modelMetadata = new ConcurrentHashMap<>();
    
    // Model configurations
    private static final double FRAUD_THRESHOLD = 0.7;
    private static final double HIGH_RISK_THRESHOLD = 0.85;
    private static final int ENSEMBLE_MODEL_COUNT = 5;
    private static final int FEATURE_VECTOR_SIZE = 200;
    
    @PostConstruct
    public void initializeModels() {
        log.info("Initializing fraud detection ML models");
        loadModels();
        validateModels();
        startModelMonitoring();
    }
    
    /**
     * Perform comprehensive fraud detection on transaction
     */
    @Async
    public CompletableFuture<FraudDetectionResult> detectFraud(TransactionFraudRequest request) {
        log.debug("Starting fraud detection for transaction: {}", request.getTransactionId());
        
        try {
            // Step 1: Feature extraction
            TransactionFeatures features = extractTransactionFeatures(request);
            
            // Step 2: Run ensemble models
            EnsembleResult ensembleResult = runEnsembleModels(features);
            
            // Step 3: Run deep learning model
            DeepLearningResult dlResult = runDeepLearningModel(features);
            
            // Step 4: Behavioral analysis
            BehavioralAnalysisResult behavioralResult = analyzeBehavior(request, features);
            
            // Step 5: Network analysis
            NetworkAnalysisResult networkResult = analyzeTransactionNetwork(request);
            
            // Step 6: Combine results
            FraudDetectionResult finalResult = combineResults(
                request, features, ensembleResult, dlResult, behavioralResult, networkResult
            );
            
            // Step 7: Store result
            finalResult = storeDetectionResult(finalResult);
            
            // Step 8: Handle high-risk cases
            if (finalResult.getFraudScore() >= HIGH_RISK_THRESHOLD) {
                handleHighRiskTransaction(finalResult);
            }
            
            // Step 9: Update patterns
            updateFraudPatterns(finalResult);
            
            // Step 10: Send events
            publishFraudDetectionEvent(finalResult);
            
            log.info("Fraud detection completed for transaction: {}, score: {}, fraudulent: {}",
                request.getTransactionId(), finalResult.getFraudScore(), finalResult.isFraudulent());
            
            return CompletableFuture.completedFuture(finalResult);
            
        } catch (Exception e) {
            log.error("Fraud detection failed for transaction: {}", request.getTransactionId(), e);
            return CompletableFuture.completedFuture(createErrorResult(request, e));
        }
    }
    
    /**
     * Extract comprehensive features from transaction
     */
    private TransactionFeatures extractTransactionFeatures(TransactionFraudRequest request) {
        log.debug("Extracting features for transaction: {}", request.getTransactionId());
        
        TransactionFeatures features = new TransactionFeatures();
        
        // Basic transaction features
        features.setAmount(request.getAmount().doubleValue());
        features.setCurrency(request.getCurrency());
        features.setTransactionType(request.getTransactionType());
        features.setPaymentMethod(request.getPaymentMethod());
        
        // Time-based features
        LocalDateTime now = LocalDateTime.now();
        features.setHourOfDay(now.getHour());
        features.setDayOfWeek(now.getDayOfWeek().getValue());
        features.setIsWeekend(now.getDayOfWeek().getValue() >= 6);
        features.setIsNightTime(now.getHour() < 6 || now.getHour() > 22);
        
        // User behavior features
        UserBehaviorFeatures userFeatures = extractUserBehaviorFeatures(request.getUserId());
        features.setAverageTransactionAmount(userFeatures.getAverageAmount());
        features.setTransactionFrequency(userFeatures.getFrequency());
        features.setDaysSinceLastTransaction(userFeatures.getDaysSinceLastTransaction());
        features.setAccountAge(userFeatures.getAccountAge());
        
        // Velocity features
        VelocityFeatures velocityFeatures = calculateVelocityFeatures(request);
        features.setTransactionsLastHour(velocityFeatures.getLastHour());
        features.setTransactionsLastDay(velocityFeatures.getLastDay());
        features.setAmountLastDay(velocityFeatures.getAmountLastDay());
        features.setUniqueRecipientsLastDay(velocityFeatures.getUniqueRecipients());
        
        // Device and location features
        DeviceFeatures deviceFeatures = extractDeviceFeatures(request);
        features.setDeviceFingerprint(deviceFeatures.getFingerprint());
        features.setIsNewDevice(deviceFeatures.isNewDevice());
        features.setDeviceChanges(deviceFeatures.getRecentChanges());
        
        LocationFeatures locationFeatures = extractLocationFeatures(request);
        features.setDistanceFromHome(locationFeatures.getDistanceFromHome());
        features.setIsHighRiskCountry(locationFeatures.isHighRiskCountry());
        features.setLocationVelocity(locationFeatures.getVelocity());
        
        // Merchant features (if applicable)
        if (request.getMerchantId() != null) {
            MerchantFeatures merchantFeatures = extractMerchantFeatures(request.getMerchantId());
            features.setMerchantRiskScore(merchantFeatures.getRiskScore());
            features.setMerchantCategory(merchantFeatures.getCategory());
            features.setIsNewMerchant(merchantFeatures.isNew());
        }
        
        // Network features
        NetworkFeatures networkFeatures = extractNetworkFeatures(request);
        features.setNetworkRiskScore(networkFeatures.getRiskScore());
        features.setConnectedFraudAccounts(networkFeatures.getFraudConnections());
        features.setNetworkDensity(networkFeatures.getDensity());
        
        // Historical fraud features
        HistoricalFraudFeatures historicalFeatures = extractHistoricalFraudFeatures(request.getUserId());
        features.setPreviousFraudAttempts(historicalFeatures.getAttempts());
        features.setLastFraudScore(historicalFeatures.getLastScore());
        features.setFraudPatternMatches(historicalFeatures.getPatternMatches());
        
        // Create feature vector
        features.setFeatureVector(createFeatureVector(features));
        
        return features;
    }
    
    /**
     * Run ensemble of models for fraud detection
     */
    private EnsembleResult runEnsembleModels(TransactionFeatures features) {
        log.debug("Running ensemble models");
        
        List<ModelPrediction> predictions = new ArrayList<>();
        
        // Random Forest
        ModelPrediction rfPrediction = runRandomForest(features);
        predictions.add(rfPrediction);
        
        // Gradient Boosting
        ModelPrediction gbtPrediction = runGradientBoosting(features);
        predictions.add(gbtPrediction);
        
        // Logistic Regression
        ModelPrediction lrPrediction = runLogisticRegression(features);
        predictions.add(lrPrediction);
        
        // XGBoost
        ModelPrediction xgbPrediction = runXGBoost(features);
        predictions.add(xgbPrediction);
        
        // Isolation Forest (Anomaly Detection)
        ModelPrediction ifPrediction = runIsolationForest(features);
        predictions.add(ifPrediction);
        
        // Combine predictions
        double averageScore = predictions.stream()
            .mapToDouble(ModelPrediction::getScore)
            .average()
            .orElse(0.0);
        
        double weightedScore = calculateWeightedScore(predictions);
        
        // Voting mechanism
        long fraudVotes = predictions.stream()
            .filter(p -> p.getScore() > FRAUD_THRESHOLD)
            .count();
        
        boolean isFraudulent = fraudVotes >= (ENSEMBLE_MODEL_COUNT / 2.0);
        
        return EnsembleResult.builder()
            .predictions(predictions)
            .averageScore(averageScore)
            .weightedScore(weightedScore)
            .fraudVotes((int) fraudVotes)
            .totalVotes(ENSEMBLE_MODEL_COUNT)
            .isFraudulent(isFraudulent)
            .confidence(calculateConfidence(predictions))
            .build();
    }
    
    /**
     * Run deep learning model for advanced pattern detection
     */
    private DeepLearningResult runDeepLearningModel(TransactionFeatures features) {
        log.debug("Running deep learning model");
        
        try {
            SavedModelBundle model = deepLearningModels.get("fraud_detection_dnn");
            if (model == null) {
                log.warn("Deep learning model not available");
                return DeepLearningResult.notAvailable();
            }
            
            // Prepare input tensor
            float[][] inputData = new float[1][FEATURE_VECTOR_SIZE];
            double[] featureVector = features.getFeatureVector();
            for (int i = 0; i < featureVector.length && i < FEATURE_VECTOR_SIZE; i++) {
                inputData[0][i] = (float) featureVector[i];
            }
            
            try (Session session = model.session();
                 Tensor<Float> inputTensor = Tensor.create(inputData)) {
                
                // Run inference
                List<Tensor<?>> outputs = session.runner()
                    .feed("input", inputTensor)
                    .fetch("output")
                    .run();
                
                // Process output
                try (Tensor<?> outputTensor = outputs.get(0)) {
                    float[][] outputData = new float[1][2];
                    outputTensor.copyTo(outputData);
                    
                    float fraudProbability = outputData[0][1];
                    float legitimateProbability = outputData[0][0];
                    
                    // Extract hidden layer representations for pattern analysis
                    List<Tensor<?>> hiddenOutputs = session.runner()
                        .feed("input", inputTensor)
                        .fetch("hidden_layer")
                        .run();
                    
                    float[] hiddenFeatures = new float[128];
                    try (Tensor<?> hiddenTensor = hiddenOutputs.get(0)) {
                        hiddenTensor.copyTo(hiddenFeatures);
                    }
                    
                    // Identify patterns
                    List<String> detectedPatterns = identifyPatterns(hiddenFeatures);
                    
                    return DeepLearningResult.builder()
                        .fraudProbability(fraudProbability)
                        .legitimateProbability(legitimateProbability)
                        .confidence(Math.abs(fraudProbability - legitimateProbability))
                        .detectedPatterns(detectedPatterns)
                        .hiddenFeatures(hiddenFeatures)
                        .modelVersion(modelMetadata.get("fraud_detection_dnn").getVersion())
                        .build();
                }
            }
            
        } catch (Exception e) {
            log.error("Deep learning model execution failed", e);
            return DeepLearningResult.error(e.getMessage());
        }
    }
    
    /**
     * Analyze user behavior for anomalies
     */
    private BehavioralAnalysisResult analyzeBehavior(TransactionFraudRequest request, 
                                                     TransactionFeatures features) {
        log.debug("Analyzing user behavior for userId: {}", request.getUserId());
        
        // Get user's historical behavior
        UserBehaviorProfile profile = getUserBehaviorProfile(request.getUserId());
        
        // Calculate deviations
        double amountDeviation = calculateDeviation(
            request.getAmount().doubleValue(), 
            profile.getAverageAmount(), 
            profile.getStdDevAmount()
        );
        
        double timeDeviation = calculateTimeDeviation(
            LocalDateTime.now(), 
            profile.getTypicalTransactionTimes()
        );
        
        double frequencyDeviation = calculateFrequencyDeviation(
            features.getTransactionFrequency(), 
            profile.getNormalFrequency()
        );
        
        // Check for suspicious patterns
        List<String> suspiciousPatterns = new ArrayList<>();
        
        if (amountDeviation > 3.0) {
            suspiciousPatterns.add("UNUSUAL_AMOUNT");
        }
        
        if (timeDeviation > 2.5) {
            suspiciousPatterns.add("UNUSUAL_TIME");
        }
        
        if (frequencyDeviation > 2.0) {
            suspiciousPatterns.add("UNUSUAL_FREQUENCY");
        }
        
        // Sudden behavior changes
        if (detectSuddenBehaviorChange(request.getUserId(), features)) {
            suspiciousPatterns.add("SUDDEN_BEHAVIOR_CHANGE");
        }
        
        // Sequential pattern analysis
        SequentialPatternAnalysis sequentialAnalysis = analyzeSequentialPatterns(
            request.getUserId(), request
        );
        
        if (sequentialAnalysis.hasAnomalousSequence()) {
            suspiciousPatterns.add("ANOMALOUS_SEQUENCE");
        }
        
        // Calculate behavioral risk score
        double behavioralRiskScore = calculateBehavioralRiskScore(
            amountDeviation, timeDeviation, frequencyDeviation, 
            suspiciousPatterns.size(), sequentialAnalysis
        );
        
        return BehavioralAnalysisResult.builder()
            .userId(request.getUserId())
            .amountDeviation(amountDeviation)
            .timeDeviation(timeDeviation)
            .frequencyDeviation(frequencyDeviation)
            .suspiciousPatterns(suspiciousPatterns)
            .sequentialAnalysis(sequentialAnalysis)
            .behavioralRiskScore(behavioralRiskScore)
            .isAnomalous(behavioralRiskScore > 0.6)
            .build();
    }
    
    /**
     * Analyze transaction network for fraud rings
     */
    private NetworkAnalysisResult analyzeTransactionNetwork(TransactionFraudRequest request) {
        log.debug("Analyzing transaction network");
        
        // Build transaction graph
        TransactionGraph graph = buildTransactionGraph(request.getUserId(), request.getRecipientId());
        
        // Detect fraud rings
        List<FraudRing> fraudRings = detectFraudRings(graph);
        
        // Calculate network metrics
        NetworkMetrics metrics = calculateNetworkMetrics(graph);
        
        // Check for money laundering patterns
        List<MoneyLaunderingPattern> mlPatterns = detectMoneyLaunderingPatterns(graph);
        
        // Identify suspicious connections
        List<SuspiciousConnection> suspiciousConnections = identifySuspiciousConnections(
            graph, request.getUserId()
        );
        
        // Calculate network risk score
        double networkRiskScore = calculateNetworkRiskScore(
            fraudRings, metrics, mlPatterns, suspiciousConnections
        );
        
        return NetworkAnalysisResult.builder()
            .graph(graph)
            .fraudRings(fraudRings)
            .metrics(metrics)
            .moneyLaunderingPatterns(mlPatterns)
            .suspiciousConnections(suspiciousConnections)
            .networkRiskScore(networkRiskScore)
            .isPartOfFraudRing(!fraudRings.isEmpty())
            .hasMoneyLaunderingIndicators(!mlPatterns.isEmpty())
            .build();
    }
    
    /**
     * Combine all results into final fraud detection result
     */
    private FraudDetectionResult combineResults(TransactionFraudRequest request,
                                                TransactionFeatures features,
                                                EnsembleResult ensembleResult,
                                                DeepLearningResult dlResult,
                                                BehavioralAnalysisResult behavioralResult,
                                                NetworkAnalysisResult networkResult) {
        
        // Calculate weighted final score
        double finalScore = calculateFinalFraudScore(
            ensembleResult.getWeightedScore(),
            dlResult.getFraudProbability(),
            behavioralResult.getBehavioralRiskScore(),
            networkResult.getNetworkRiskScore()
        );
        
        // Determine if fraudulent
        boolean isFraudulent = finalScore >= FRAUD_THRESHOLD;
        
        // Collect all risk factors
        List<RiskFactor> riskFactors = collectRiskFactors(
            features, ensembleResult, dlResult, behavioralResult, networkResult
        );
        
        // Determine fraud type if fraudulent
        FraudType fraudType = null;
        if (isFraudulent) {
            fraudType = determineFraudType(riskFactors, behavioralResult, networkResult);
        }
        
        // Generate recommendations
        List<String> recommendations = generateRecommendations(
            finalScore, isFraudulent, riskFactors, fraudType
        );
        
        // Calculate confidence
        double confidence = calculateOverallConfidence(
            ensembleResult.getConfidence(),
            dlResult.getConfidence(),
            riskFactors.size()
        );
        
        return FraudDetectionResult.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .fraudScore(finalScore)
            .isFraudulent(isFraudulent)
            .fraudType(fraudType)
            .confidence(confidence)
            .riskLevel(determineRiskLevel(finalScore))
            .riskFactors(riskFactors)
            .ensembleResult(ensembleResult)
            .deepLearningResult(dlResult)
            .behavioralAnalysis(behavioralResult)
            .networkAnalysis(networkResult)
            .recommendations(recommendations)
            .requiresManualReview(shouldRequireManualReview(finalScore, confidence))
            .detectedAt(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Store fraud detection result
     */
    private FraudDetectionResult storeDetectionResult(FraudDetectionResult result) {
        FraudDetectionEntity entity = FraudDetectionEntity.fromResult(result);
        entity = resultRepository.save(entity);
        result.setId(entity.getId());
        return result;
    }
    
    /**
     * Handle high-risk transaction
     */
    private void handleHighRiskTransaction(FraudDetectionResult result) {
        log.warn("High-risk transaction detected: {}, score: {}", 
            result.getTransactionId(), result.getFraudScore());
        
        // Send immediate alert
        alertingService.sendHighRiskAlert(result);
        
        // Block transaction if score is very high
        if (result.getFraudScore() >= 0.95) {
            blockTransaction(result.getTransactionId());
        }
        
        // Flag for manual review
        flagForManualReview(result);
        
        // Update fraud patterns
        updateFraudPatternsImmediate(result);
        
        // Notify relevant parties
        notifyHighRiskTransaction(result);
    }
    
    /**
     * Update fraud patterns based on detection result
     */
    @Async
    public void updateFraudPatterns(FraudDetectionResult result) {
        if (!result.isFraudulent()) {
            return;
        }
        
        try {
            // Extract patterns from result
            List<FraudPattern> patterns = extractPatternsFromResult(result);
            
            // Update existing patterns or create new ones
            for (FraudPattern pattern : patterns) {
                FraudPattern existing = patternRepository.findByPatternHash(pattern.getPatternHash());
                
                if (existing != null) {
                    existing.incrementOccurrence();
                    existing.updateConfidence(result.getConfidence());
                    patternRepository.save(existing);
                } else {
                    pattern.setDiscoveredAt(LocalDateTime.now());
                    pattern.setOccurrences(1);
                    patternRepository.save(pattern);
                }
            }
            
            // Retrain models if significant new patterns found
            if (shouldRetrainModels(patterns)) {
                triggerModelRetraining();
            }
            
        } catch (Exception e) {
            log.error("Failed to update fraud patterns", e);
        }
    }
    
    /**
     * Real-time fraud scoring for instant decisions
     */
    @Cacheable(value = "fraudScores", key = "#request.transactionId")
    public FraudScore getInstantFraudScore(QuickFraudCheckRequest request) {
        log.debug("Calculating instant fraud score for transaction: {}", request.getTransactionId());
        
        try {
            // Use lightweight features for speed
            LightweightFeatures features = extractLightweightFeatures(request);
            
            // Run fastest model only
            double score = runFastFraudModel(features);
            
            // Apply rules-based checks
            RuleCheckResult ruleResult = applyFraudRules(request);
            
            // Combine scores
            double finalScore = (score * 0.7) + (ruleResult.getRiskScore() * 0.3);
            
            return FraudScore.builder()
                .transactionId(request.getTransactionId())
                .score(finalScore)
                .isFraudulent(finalScore >= FRAUD_THRESHOLD)
                .riskLevel(determineRiskLevel(finalScore))
                .processingTimeMs(System.currentTimeMillis())
                .build();
            
        } catch (Exception e) {
            log.error("Instant fraud scoring failed", e);
            return FraudScore.error(request.getTransactionId());
        }
    }
    
    /**
     * Scheduled model performance monitoring
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void monitorModelPerformance() {
        log.info("Monitoring model performance");
        
        for (Map.Entry<String, PipelineModel> entry : modelCache.entrySet()) {
            String modelName = entry.getKey();
            PipelineModel model = entry.getValue();
            
            try {
                ModelPerformance performance = evaluateModelPerformance(modelName, model);
                performanceRepository.save(performance);
                
                // Alert if performance degradation
                if (performance.getAccuracy() < 0.85) {
                    alertingService.sendModelDegradationAlert(modelName, performance);
                }
                
            } catch (Exception e) {
                log.error("Failed to monitor performance for model: {}", modelName, e);
            }
        }
    }
    
    /**
     * Retrain models with new data
     */
    @Async
    public CompletableFuture<ModelTrainingResult> retrainModels() {
        log.info("Starting model retraining");
        
        try {
            // Prepare training data
            Dataset<Row> trainingData = prepareTrainingData();
            
            // Train ensemble models
            Map<String, PipelineModel> newModels = new HashMap<>();
            
            // Random Forest
            PipelineModel rfModel = trainRandomForestModel(trainingData);
            newModels.put("random_forest", rfModel);
            
            // Gradient Boosting
            PipelineModel gbtModel = trainGradientBoostingModel(trainingData);
            newModels.put("gradient_boosting", gbtModel);
            
            // Logistic Regression
            PipelineModel lrModel = trainLogisticRegressionModel(trainingData);
            newModels.put("logistic_regression", lrModel);
            
            // Evaluate new models
            Map<String, ModelPerformance> performances = evaluateModels(newModels, trainingData);
            
            // Update models if performance improved
            for (Map.Entry<String, PipelineModel> entry : newModels.entrySet()) {
                String modelName = entry.getKey();
                ModelPerformance newPerf = performances.get(modelName);
                ModelPerformance currentPerf = getCurrentModelPerformance(modelName);
                
                if (newPerf.getAccuracy() > currentPerf.getAccuracy()) {
                    modelCache.put(modelName, entry.getValue());
                    saveModel(modelName, entry.getValue());
                    log.info("Updated model: {} with improved accuracy: {}", 
                        modelName, newPerf.getAccuracy());
                }
            }
            
            return CompletableFuture.completedFuture(
                ModelTrainingResult.success(performances)
            );
            
        } catch (Exception e) {
            log.error("Model retraining failed", e);
            return CompletableFuture.completedFuture(
                ModelTrainingResult.failure(e.getMessage())
            );
        }
    }
    
    // Helper methods implementation would continue...
    
    private void loadModels() {
        // Load Spark ML models
        modelCache.put("random_forest", loadSparkModel("random_forest"));
        modelCache.put("gradient_boosting", loadSparkModel("gradient_boosting"));
        modelCache.put("logistic_regression", loadSparkModel("logistic_regression"));
        
        // Load TensorFlow models
        deepLearningModels.put("fraud_detection_dnn", 
            SavedModelBundle.load("models/fraud_detection_dnn", "serve"));
    }
    
    private PipelineModel loadSparkModel(String modelName) {
        return PipelineModel.load("models/spark/" + modelName);
    }
    
    private double calculateFinalFraudScore(double ensembleScore, double dlScore, 
                                           double behavioralScore, double networkScore) {
        // Weighted average with emphasis on ensemble and deep learning
        return (ensembleScore * 0.35) + 
               (dlScore * 0.30) + 
               (behavioralScore * 0.20) + 
               (networkScore * 0.15);
    }
    
    private String determineRiskLevel(double score) {
        if (score >= 0.85) return "CRITICAL";
        if (score >= 0.70) return "HIGH";
        if (score >= 0.50) return "MEDIUM";
        if (score >= 0.30) return "LOW";
        return "MINIMAL";
    }
    
    private void publishFraudDetectionEvent(FraudDetectionResult result) {
        FraudDetectionEvent event = FraudDetectionEvent.fromResult(result);
        kafkaTemplate.send("fraud-detection-events", event);
    }
}