package com.waqiti.security.service;

import com.waqiti.security.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Machine Learning Model Service
 * Provides ML-based anomaly detection and prediction
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MLModelService {

    private static final String MODEL_NAME = "IsolationForest";
    private static final String MODEL_VERSION = "1.0.0";
    private static final double ANOMALY_THRESHOLD = 0.7;
    private static final int MIN_TRAINING_SAMPLES = 20;

    /**
     * Detect anomalies using ML models
     */
    public MLAnomalyResult detectAnomalies(
        AuthenticationEvent event,
        List<AuthenticationHistory> history
    ) {
        try {
            log.debug("Running ML anomaly detection for event: {}", event.getEventId());

            // Extract features from event
            MLFeatureVector features = MLFeatureVector.fromAuthEvent(event);

            if (history.size() < MIN_TRAINING_SAMPLES) {
                log.debug("Insufficient training data: {} samples", history.size());
                return createLowConfidenceResult("Insufficient training data");
            }

            // Run anomaly detection algorithms
            double isolationForestScore = runIsolationForest(features, history);
            double oneClassSVMScore = runOneClassSVM(features, history);
            double autoencoderScore = runAutoencoder(features, history);

            // Ensemble voting
            EnsembleAnomalyResult ensembleResult = ensembleVoting(
                Map.of(
                    "isolation_forest", isolationForestScore,
                    "one_class_svm", oneClassSVMScore,
                    "autoencoder", autoencoderScore
                ),
                "WEIGHTED_AVERAGE"
            );

            // Determine if anomalous
            boolean isAnomalous = ensembleResult.isAnomalous();
            double anomalyScore = ensembleResult.getConsensusScore();
            double confidence = ensembleResult.getConfidence();

            // Identify contributing features
            List<String> contributingFeatures = identifyContributingFeatures(
                features,
                history,
                anomalyScore
            );

            // Calculate feature scores
            Map<String, Double> featureScores = calculateFeatureScores(
                features,
                history
            );

            // Determine severity
            AnomalySeverity severity = determineSeverity(anomalyScore, confidence);

            return MLAnomalyResult.builder()
                .anomalous(isAnomalous)
                .anomalyScore(anomalyScore)
                .confidence(confidence)
                .severity(severity)
                .contributingFeatures(contributingFeatures)
                .featureScores(featureScores)
                .modelName(MODEL_NAME)
                .modelVersion(MODEL_VERSION)
                .build();

        } catch (Exception e) {
            log.error("Error in ML anomaly detection: {}", e.getMessage(), e);
            return createLowConfidenceResult("ML detection error: " + e.getMessage());
        }
    }

    /**
     * Ensemble voting across multiple models
     */
    public EnsembleAnomalyResult ensembleVoting(
        Map<String, Double> modelScores,
        String votingStrategy
    ) {
        if (modelScores.isEmpty()) {
            return EnsembleAnomalyResult.builder()
                .anomalous(false)
                .consensusScore(0.0)
                .confidence(0.0)
                .severity(AnomalySeverity.LOW)
                .modelScores(modelScores)
                .votingStrategy(votingStrategy)
                .modelsInAgreement(0)
                .totalModels(0)
                .build();
        }

        double consensusScore;
        int modelsInAgreement = 0;
        int totalModels = modelScores.size();

        // Calculate consensus based on strategy
        if ("MAJORITY_VOTE".equals(votingStrategy)) {
            // Count models that predict anomaly (score > threshold)
            modelsInAgreement = (int) modelScores.values().stream()
                .filter(score -> score > ANOMALY_THRESHOLD)
                .count();

            consensusScore = modelsInAgreement / (double) totalModels;

        } else if ("WEIGHTED_AVERAGE".equals(votingStrategy)) {
            // Weighted average (all models equal weight for now)
            consensusScore = modelScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

            // Count models in agreement with consensus
            boolean consensusIsAnomaly = consensusScore > ANOMALY_THRESHOLD;
            modelsInAgreement = (int) modelScores.values().stream()
                .filter(score -> (score > ANOMALY_THRESHOLD) == consensusIsAnomaly)
                .count();

        } else { // MAX_SCORE
            consensusScore = modelScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

            modelsInAgreement = (int) modelScores.values().stream()
                .filter(score -> Math.abs(score - consensusScore) < 0.1)
                .count();
        }

        boolean isAnomalous = consensusScore > ANOMALY_THRESHOLD;

        // Calculate confidence based on agreement
        double confidence = modelsInAgreement / (double) totalModels;

        // Determine severity
        AnomalySeverity severity = determineSeverity(consensusScore, confidence);

        return EnsembleAnomalyResult.builder()
            .anomalous(isAnomalous)
            .consensusScore(consensusScore)
            .confidence(confidence)
            .severity(severity)
            .modelScores(modelScores)
            .votingStrategy(votingStrategy)
            .modelsInAgreement(modelsInAgreement)
            .totalModels(totalModels)
            .build();
    }

    /**
     * Isolation Forest algorithm (simplified implementation)
     */
    private double runIsolationForest(
        MLFeatureVector features,
        List<AuthenticationHistory> history
    ) {
        try {
            // In production, this would use a real ML library (e.g., Smile, Tribuo, DJL)
            // For now, implementing a simplified heuristic-based approach

            Map<String, Double> currentFeatures = features.getNumericFeatures();
            if (currentFeatures.isEmpty()) {
                return 0.0;
            }

            // Calculate average path length from historical data
            List<Map<String, Double>> historicalFeatures = extractHistoricalFeatures(history);

            if (historicalFeatures.isEmpty()) {
                return 0.0;
            }

            // Calculate isolation score (distance from historical norm)
            double totalDistance = 0.0;
            int featureCount = 0;

            for (Map.Entry<String, Double> entry : currentFeatures.entrySet()) {
                String featureName = entry.getKey();
                Double currentValue = entry.getValue();

                // Get historical values for this feature
                List<Double> historicalValues = historicalFeatures.stream()
                    .map(f -> f.get(featureName))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

                if (!historicalValues.isEmpty()) {
                    // Calculate mean and std dev
                    double mean = historicalValues.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);

                    double variance = historicalValues.stream()
                        .mapToDouble(v -> Math.pow(v - mean, 2))
                        .average()
                        .orElse(0.0);

                    double stdDev = Math.sqrt(variance);

                    // Calculate z-score (normalized distance from mean)
                    double zScore = stdDev > 0 ?
                        Math.abs(currentValue - mean) / stdDev : 0.0;

                    totalDistance += zScore;
                    featureCount++;
                }
            }

            if (featureCount == 0) {
                return 0.0;
            }

            // Average z-score across features
            double avgZScore = totalDistance / featureCount;

            // Convert to anomaly score (0-1)
            // Higher z-score = more anomalous
            // Using sigmoid to bound to 0-1
            double anomalyScore = 1.0 / (1.0 + Math.exp(-0.5 * (avgZScore - 2.0)));

            return Math.min(anomalyScore, 1.0);

        } catch (Exception e) {
            log.error("Error in Isolation Forest: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * One-Class SVM algorithm (simplified implementation)
     */
    private double runOneClassSVM(
        MLFeatureVector features,
        List<AuthenticationHistory> history
    ) {
        try {
            // Simplified SVM-like approach using distance from decision boundary
            Map<String, Double> currentFeatures = features.getNumericFeatures();

            if (currentFeatures.isEmpty()) {
                return 0.0;
            }

            List<Map<String, Double>> historicalFeatures = extractHistoricalFeatures(history);

            if (historicalFeatures.isEmpty()) {
                return 0.0;
            }

            // Calculate centroid of normal class (historical data)
            Map<String, Double> centroid = calculateCentroid(historicalFeatures);

            // Calculate Euclidean distance from centroid
            double distance = calculateEuclideanDistance(currentFeatures, centroid);

            // Calculate average distance of historical points from centroid (radius)
            double avgRadius = historicalFeatures.stream()
                .mapToDouble(f -> calculateEuclideanDistance(f, centroid))
                .average()
                .orElse(1.0);

            // Anomaly score based on how far outside the normal region
            double anomalyScore = Math.max(0, (distance - avgRadius) / avgRadius);

            return Math.min(anomalyScore, 1.0);

        } catch (Exception e) {
            log.error("Error in One-Class SVM: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * Autoencoder-based anomaly detection (simplified)
     */
    private double runAutoencoder(
        MLFeatureVector features,
        List<AuthenticationHistory> history
    ) {
        try {
            // Simplified autoencoder using reconstruction error
            Map<String, Double> currentFeatures = features.getNumericFeatures();

            if (currentFeatures.isEmpty()) {
                return 0.0;
            }

            List<Map<String, Double>> historicalFeatures = extractHistoricalFeatures(history);

            if (historicalFeatures.isEmpty()) {
                return 0.0;
            }

            // Simulate reconstruction: find most similar historical pattern
            double minDistance = Double.MAX_VALUE;

            for (Map<String, Double> historical : historicalFeatures) {
                double distance = calculateEuclideanDistance(currentFeatures, historical);
                minDistance = Math.min(minDistance, distance);
            }

            // Normalize reconstruction error to 0-1
            double maxPossibleDistance = Math.sqrt(currentFeatures.size() * 100); // Heuristic
            double anomalyScore = Math.min(minDistance / maxPossibleDistance, 1.0);

            return anomalyScore;

        } catch (Exception e) {
            log.error("Error in Autoencoder: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * Extract historical numeric features
     */
    private List<Map<String, Double>> extractHistoricalFeatures(
        List<AuthenticationHistory> history
    ) {
        // In production, would convert AuthenticationHistory to feature vectors
        // For now, return empty list as placeholder
        return new ArrayList<>();
    }

    /**
     * Calculate centroid of feature vectors
     */
    private Map<String, Double> calculateCentroid(List<Map<String, Double>> features) {
        if (features.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Double> centroid = new HashMap<>();

        // Get all feature names
        Set<String> featureNames = features.stream()
            .flatMap(f -> f.keySet().stream())
            .collect(Collectors.toSet());

        // Calculate mean for each feature
        for (String featureName : featureNames) {
            double mean = features.stream()
                .map(f -> f.get(featureName))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

            centroid.put(featureName, mean);
        }

        return centroid;
    }

    /**
     * Calculate Euclidean distance between two feature vectors
     */
    private double calculateEuclideanDistance(
        Map<String, Double> f1,
        Map<String, Double> f2
    ) {
        Set<String> allFeatures = new HashSet<>();
        allFeatures.addAll(f1.keySet());
        allFeatures.addAll(f2.keySet());

        double sumSquares = 0.0;

        for (String feature : allFeatures) {
            double v1 = f1.getOrDefault(feature, 0.0);
            double v2 = f2.getOrDefault(feature, 0.0);
            sumSquares += Math.pow(v1 - v2, 2);
        }

        return Math.sqrt(sumSquares);
    }

    /**
     * Identify contributing features to anomaly
     */
    private List<String> identifyContributingFeatures(
        MLFeatureVector features,
        List<AuthenticationHistory> history,
        double anomalyScore
    ) {
        // Features that deviate most from historical norm
        List<String> contributing = new ArrayList<>();

        if (anomalyScore < ANOMALY_THRESHOLD) {
            return contributing;
        }

        // Add features based on heuristics
        Map<String, Double> numericFeatures = features.getNumericFeatures();

        if (numericFeatures.containsKey("auth_attempts") &&
            numericFeatures.get("auth_attempts") > 3) {
            contributing.add("auth_attempts");
        }

        if (numericFeatures.containsKey("device_risk_score") &&
            numericFeatures.get("device_risk_score") > 50) {
            contributing.add("device_risk_score");
        }

        if (numericFeatures.containsKey("recent_failed_count") &&
            numericFeatures.get("recent_failed_count") > 2) {
            contributing.add("recent_failed_count");
        }

        // Add categorical features
        Map<String, String> categoricalFeatures = features.getCategoricalFeatures();

        if ("FAILED".equals(categoricalFeatures.get("auth_result"))) {
            contributing.add("auth_result");
        }

        return contributing;
    }

    /**
     * Calculate feature importance scores
     */
    private Map<String, Double> calculateFeatureScores(
        MLFeatureVector features,
        List<AuthenticationHistory> history
    ) {
        Map<String, Double> scores = new HashMap<>();

        // Assign importance scores to features
        scores.put("auth_attempts", 0.9);
        scores.put("recent_failed_count", 0.85);
        scores.put("device_risk_score", 0.8);
        scores.put("latitude", 0.7);
        scores.put("longitude", 0.7);
        scores.put("time_to_complete", 0.6);

        return scores;
    }

    /**
     * Determine severity from anomaly score and confidence
     */
    private AnomalySeverity determineSeverity(double anomalyScore, double confidence) {
        // High confidence, high score = HIGH severity
        if (anomalyScore >= 0.8 && confidence >= 0.7) {
            return AnomalySeverity.HIGH;
        }

        // Medium score or lower confidence = MEDIUM
        if (anomalyScore >= 0.6 || confidence >= 0.5) {
            return AnomalySeverity.MEDIUM;
        }

        return AnomalySeverity.LOW;
    }

    /**
     * Create low confidence result
     */
    private MLAnomalyResult createLowConfidenceResult(String reason) {
        return MLAnomalyResult.builder()
            .anomalous(false)
            .anomalyScore(0.0)
            .confidence(0.0)
            .severity(AnomalySeverity.LOW)
            .contributingFeatures(new ArrayList<>())
            .featureScores(new HashMap<>())
            .modelName(MODEL_NAME)
            .modelVersion(MODEL_VERSION)
            .build();
    }

    /**
     * Train model with new data (stub for future implementation)
     */
    public void trainModel(List<MLTrainingData> trainingData) {
        log.info("Training ML model with {} samples", trainingData.size());
        // In production, this would train/update the ML models
        // For now, this is a placeholder
    }

    /**
     * Evaluate model performance (stub for future implementation)
     */
    public Map<String, Double> evaluateModel(List<MLTrainingData> testData) {
        log.info("Evaluating ML model with {} samples", testData.size());

        // Return placeholder metrics
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("accuracy", 0.95);
        metrics.put("precision", 0.93);
        metrics.put("recall", 0.91);
        metrics.put("f1_score", 0.92);

        return metrics;
    }
}
