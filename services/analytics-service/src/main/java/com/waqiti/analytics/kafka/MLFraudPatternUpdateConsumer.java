package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.service.MachineLearningModelService;
import com.waqiti.analytics.service.FraudMLTrainingService;
import com.waqiti.analytics.service.ModelVersioningService;
import com.waqiti.analytics.service.MLMetricsService;
import com.waqiti.analytics.domain.FraudModel;
import com.waqiti.analytics.domain.ModelTrainingJob;
import com.waqiti.analytics.domain.FeatureVector;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * CRITICAL ML: Consumes ML fraud pattern update events
 * 
 * This consumer processes new fraud patterns discovered during fraud investigations
 * and updates machine learning models with this fresh data to improve detection
 * accuracy and adapt to evolving fraud techniques.
 * 
 * Events processed:
 * - ml-fraud-pattern-update: New fraud patterns for model training
 * 
 * ML Impact: CRITICAL - Model accuracy and fraud detection effectiveness
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MLFraudPatternUpdateConsumer {

    private final MachineLearningModelService mlModelService;
    private final FraudMLTrainingService fraudTrainingService;
    private final ModelVersioningService modelVersioningService;
    private final MLMetricsService mlMetricsService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler universalDLQHandler;

    /**
     * Process ML fraud pattern updates
     */
    @KafkaListener(topics = "ml-fraud-pattern-update", groupId = "analytics-ml-training-group")
    public void handleMLFraudPatternUpdate(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            log.info("ML_TRAINING: Processing fraud pattern update - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
            
            Map<String, Object> fraudFeatures = objectMapper.readValue(message, Map.class);
            
            if (fraudFeatures == null || fraudFeatures.isEmpty()) {
                log.error("ML_TRAINING: Invalid fraud pattern update - empty features");
                return;
            }
            
            // Extract fraud pattern information
            FeatureVector featureVector = extractFeatureVector(fraudFeatures);
            
            // Validate feature quality
            if (!validateFeatureQuality(featureVector)) {
                log.warn("ML_TRAINING: Feature vector failed quality validation, skipping update");
                return;
            }
            
            // Store new training data
            storeTrainingData(featureVector);
            
            // Check if model retraining is needed
            boolean retrainingNeeded = assessRetrainingNeed(featureVector);
            
            if (retrainingNeeded) {
                initiateModelRetraining(featureVector);
            } else {
                // Update model incrementally if possible
                updateModelIncremental(featureVector);
            }
            
            // Update ML metrics
            updateMLMetrics(featureVector);
            
            // Update feature importance analysis
            updateFeatureImportance(featureVector);
            
            log.info("ML_TRAINING: Successfully processed fraud pattern update with {} features", 
                fraudFeatures.size());
            
        } catch (Exception e) {
            log.error("ML_TRAINING: CRITICAL - Failed to process ML fraud pattern update", e);

            // Track ML training failures
            mlMetricsService.recordTrainingFailure("FRAUD_PATTERN_UPDATE", e.getMessage());

            // Send to DLQ via UniversalDLQHandler
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, String> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        topic, partition, offset, null, message
                    );
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ: {}", dlqException.getMessage());
            }

            // Re-throw to trigger Kafka retry
            throw new RuntimeException("ML Training failure: Fraud pattern update failed", e);
        }
    }
    
    /**
     * Extract feature vector from fraud pattern data
     */
    private FeatureVector extractFeatureVector(Map<String, Object> fraudFeatures) {
        return FeatureVector.builder()
            .featureId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .label("FRAUD") // This is confirmed fraud from investigation
            .confidence(1.0) // High confidence since it's from actual fraud case
            .features(fraudFeatures)
            .source("FRAUD_INVESTIGATION")
            .priority("HIGH")
            .build();
    }
    
    /**
     * Validate feature quality before using for training
     */
    private boolean validateFeatureQuality(FeatureVector featureVector) {
        try {
            // Check for minimum required features
            Map<String, Object> features = featureVector.getFeatures();
            
            // Required fraud detection features
            String[] requiredFeatures = {
                "transactionAmount", "merchantCategory", "timeOfDay",
                "geographicLocation", "userBehaviorScore"
            };
            
            for (String required : requiredFeatures) {
                if (!features.containsKey(required) || features.get(required) == null) {
                    log.warn("ML_TRAINING: Missing required feature: {}", required);
                    return false;
                }
            }
            
            // Check for data quality issues
            if (hasDataQualityIssues(features)) {
                log.warn("ML_TRAINING: Feature vector has data quality issues");
                return false;
            }
            
            // Check for feature value ranges
            if (!validateFeatureRanges(features)) {
                log.warn("ML_TRAINING: Feature values outside expected ranges");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("ML_TRAINING: Error validating feature quality", e);
            return false;
        }
    }
    
    /**
     * Store new training data for future model updates
     */
    private void storeTrainingData(FeatureVector featureVector) {
        try {
            fraudTrainingService.storeTrainingExample(featureVector);
            
            // Update training data statistics
            mlMetricsService.updateTrainingDataStats("FRAUD_PATTERN", 1);
            
            log.debug("ML_TRAINING: Stored new fraud training example: {}", 
                featureVector.getFeatureId());
            
        } catch (Exception e) {
            log.error("ML_TRAINING: Failed to store training data", e);
            throw e;
        }
    }
    
    /**
     * Assess if full model retraining is needed
     */
    private boolean assessRetrainingNeed(FeatureVector featureVector) {
        try {
            // Check recent training data volume
            long recentExamples = fraudTrainingService.getRecentTrainingExampleCount(7); // Last 7 days
            
            // Check model performance drift
            double currentAccuracy = mlModelService.getCurrentModelAccuracy("fraud_detection");
            double baseline = 0.92; // Minimum acceptable accuracy
            
            // Check feature distribution drift
            boolean featureDrift = fraudTrainingService.detectFeatureDrift(featureVector);
            
            // Retrain if:
            // 1. Accumulated enough new examples (>100)
            // 2. Model accuracy dropped below baseline
            // 3. Significant feature drift detected
            // 4. Last training was more than 7 days ago
            
            boolean needsRetraining = recentExamples > 100 || 
                                     currentAccuracy < baseline ||
                                     featureDrift ||
                                     fraudTrainingService.getLastTrainingAge() > 7;
            
            if (needsRetraining) {
                log.info("ML_TRAINING: Model retraining needed - Examples: {}, Accuracy: {}, Drift: {}", 
                    recentExamples, currentAccuracy, featureDrift);
            }
            
            return needsRetraining;
            
        } catch (Exception e) {
            log.error("ML_TRAINING: Error assessing retraining need", e);
            return false;
        }
    }
    
    /**
     * Initiate full model retraining
     */
    private void initiateModelRetraining(FeatureVector featureVector) {
        try {
            log.info("ML_TRAINING: Initiating fraud model retraining");
            
            // Create training job
            ModelTrainingJob trainingJob = ModelTrainingJob.builder()
                .jobId(UUID.randomUUID().toString())
                .modelType("FRAUD_DETECTION")
                .trainingType("FULL_RETRAIN")
                .triggeredBy("FRAUD_PATTERN_UPDATE")
                .startTime(LocalDateTime.now())
                .status("INITIATED")
                .priority("HIGH")
                .build();
            
            // Submit training job
            String jobId = fraudTrainingService.submitTrainingJob(trainingJob);
            
            // Track training initiation
            mlMetricsService.recordTrainingInitiation("FRAUD_DETECTION", jobId);
            
            log.info("ML_TRAINING: Fraud model retraining job submitted: {}", jobId);
            
        } catch (Exception e) {
            log.error("ML_TRAINING: Failed to initiate model retraining", e);
            throw e;
        }
    }
    
    /**
     * Update model incrementally with new data
     */
    private void updateModelIncremental(FeatureVector featureVector) {
        try {
            log.debug("ML_TRAINING: Performing incremental model update");
            
            // Apply incremental learning if supported by model
            if (mlModelService.supportsIncrementalLearning("fraud_detection")) {
                mlModelService.updateModelIncremental("fraud_detection", featureVector);
                
                // Track incremental update
                mlMetricsService.recordIncrementalUpdate("FRAUD_DETECTION");
                
                log.debug("ML_TRAINING: Incremental model update completed");
            } else {
                log.debug("ML_TRAINING: Model does not support incremental learning, data stored for next training");
            }
            
        } catch (Exception e) {
            log.error("ML_TRAINING: Failed to perform incremental update", e);
            // Don't throw - this is not critical failure
        }
    }
    
    /**
     * Update ML metrics with new training data
     */
    private void updateMLMetrics(FeatureVector featureVector) {
        try {
            mlMetricsService.recordNewTrainingExample("FRAUD_DETECTION", featureVector);
            mlMetricsService.updateFeatureDistribution(featureVector.getFeatures());
            mlMetricsService.trackModelPerformance("FRAUD_DETECTION");
            
        } catch (Exception e) {
            log.error("ML_TRAINING: Failed to update ML metrics", e);
        }
    }
    
    /**
     * Update feature importance analysis
     */
    private void updateFeatureImportance(FeatureVector featureVector) {
        try {
            fraudTrainingService.updateFeatureImportance(featureVector);
            
            // Check for new important features
            List<String> newImportantFeatures = fraudTrainingService.detectNewImportantFeatures();
            
            if (!newImportantFeatures.isEmpty()) {
                log.info("ML_TRAINING: Detected new important features: {}", newImportantFeatures);
                mlMetricsService.recordNewImportantFeatures(newImportantFeatures);
            }
            
        } catch (Exception e) {
            log.error("ML_TRAINING: Failed to update feature importance", e);
        }
    }
    
    /**
     * Check for data quality issues
     */
    private boolean hasDataQualityIssues(Map<String, Object> features) {
        try {
            for (Map.Entry<String, Object> entry : features.entrySet()) {
                Object value = entry.getValue();
                
                // Check for null values
                if (value == null) {
                    return true;
                }
                
                // Check for invalid numeric values
                if (value instanceof Number) {
                    double numValue = ((Number) value).doubleValue();
                    if (Double.isNaN(numValue) || Double.isInfinite(numValue)) {
                        return true;
                    }
                }
                
                // Check for empty strings
                if (value instanceof String && ((String) value).trim().isEmpty()) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("ML_TRAINING: Error checking data quality", e);
            return true;
        }
    }
    
    /**
     * Validate feature value ranges
     */
    private boolean validateFeatureRanges(Map<String, Object> features) {
        try {
            // Validate transaction amount
            if (features.containsKey("transactionAmount")) {
                double amount = ((Number) features.get("transactionAmount")).doubleValue();
                if (amount < 0 || amount > 1000000) { // $1M limit
                    return false;
                }
            }
            
            // Validate user behavior score
            if (features.containsKey("userBehaviorScore")) {
                double score = ((Number) features.get("userBehaviorScore")).doubleValue();
                if (score < 0 || score > 1) { // 0-1 range
                    return false;
                }
            }
            
            // Add more validation as needed
            return true;
            
        } catch (Exception e) {
            log.error("ML_TRAINING: Error validating feature ranges", e);
            return false;
        }
    }
}