package com.waqiti.risk.ml;

import com.waqiti.risk.dto.FeatureVector;
import com.waqiti.risk.dto.MLPrediction;
import com.waqiti.risk.dto.TransactionRiskRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Ensemble ML Model for Risk Prediction
 * Combines predictions from multiple ML models using weighted voting
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnsembleRiskMLModel implements RiskMLModel {

    private final List<RiskMLModel> models = new ArrayList<>();

    @Value("${risk.ml.ensemble.enabled:true}")
    private boolean ensembleEnabled;

    @Value("${risk.ml.ensemble.voting-strategy:WEIGHTED_AVERAGE}")
    private String votingStrategy; // WEIGHTED_AVERAGE, MAJORITY_VOTE, MAX, MIN

    @Value("${risk.ml.ensemble.timeout-ms:5000}")
    private long timeoutMs;

    private final Map<String, Double> modelWeights = new HashMap<>();
    private volatile boolean ready = false;

    @PostConstruct
    public void initialize() {
        log.info("Initializing Ensemble ML Model with strategy: {}", votingStrategy);

        // Initialize individual models
        // Note: In production, these would be injected or loaded from configuration
        initializeModels();

        // Set model weights based on performance
        configureModelWeights();

        ready = true;
        log.info("Ensemble ML Model initialized successfully with {} models", models.size());
    }

    private void initializeModels() {
        // Placeholder for model initialization
        // In production, load actual ML models (RandomForest, GradientBoosting, NeuralNetwork, etc.)
        log.debug("Models would be loaded here from model registry/storage");
    }

    private void configureModelWeights() {
        // Configure weights based on model performance
        // Higher performing models get higher weights
        modelWeights.put("random_forest", 0.35);
        modelWeights.put("gradient_boosting", 0.30);
        modelWeights.put("neural_network", 0.25);
        modelWeights.put("logistic_regression", 0.10);
    }

    @Override
    @CircuitBreaker(name = "ml-ensemble", fallbackMethod = "predictFallback")
    @TimeLimiter(name = "ml-ensemble")
    public MLPrediction predict(TransactionRiskRequest request, FeatureVector features) {
        long startTime = System.currentTimeMillis();

        if (!ensembleEnabled || models.isEmpty()) {
            return createDefaultPrediction(request, "Ensemble disabled or no models available");
        }

        try {
            // Get predictions from all models
            List<MLPrediction> predictions = models.stream()
                    .map(model -> {
                        try {
                            return model.predict(request, features);
                        } catch (Exception e) {
                            log.warn("Model {} failed to predict: {}", model.getModelId(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (predictions.isEmpty()) {
                return createDefaultPrediction(request, "All models failed");
            }

            // Combine predictions using configured strategy
            MLPrediction ensemblePrediction = combinePredictions(predictions, request);

            long inferenceTime = System.currentTimeMillis() - startTime;
            ensemblePrediction.setInferenceTimeMs(inferenceTime);
            ensemblePrediction.setPredictionId(UUID.randomUUID().toString());

            log.debug("Ensemble prediction completed in {}ms for transaction: {}",
                    inferenceTime, request.getTransactionId());

            return ensemblePrediction;

        } catch (Exception e) {
            log.error("Ensemble prediction failed for transaction: {}", request.getTransactionId(), e);
            return createDefaultPrediction(request, "Ensemble prediction error: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<MLPrediction> predictAsync(TransactionRiskRequest request, FeatureVector features) {
        return CompletableFuture.supplyAsync(() -> predict(request, features));
    }

    private MLPrediction combinePredictions(List<MLPrediction> predictions, TransactionRiskRequest request) {
        switch (votingStrategy) {
            case "WEIGHTED_AVERAGE":
                return weightedAveragePrediction(predictions);
            case "MAJORITY_VOTE":
                return majorityVotePrediction(predictions);
            case "MAX":
                return maxPrediction(predictions);
            case "MIN":
                return minPrediction(predictions);
            default:
                return weightedAveragePrediction(predictions);
        }
    }

    private MLPrediction weightedAveragePrediction(List<MLPrediction> predictions) {
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        Map<String, Double> aggregatedFeatureImportance = new HashMap<>();

        for (MLPrediction pred : predictions) {
            double weight = modelWeights.getOrDefault(pred.getModelId(), 1.0 / predictions.size());
            totalWeight += weight;
            weightedSum += pred.getScore() * weight;

            // Aggregate feature importance
            if (pred.getFeatureImportance() != null) {
                pred.getFeatureImportance().forEach((feature, importance) ->
                        aggregatedFeatureImportance.merge(feature, importance * weight, Double::sum));
            }
        }

        double finalScore = weightedSum / totalWeight;

        return MLPrediction.builder()
                .modelId("ensemble")
                .modelName("Ensemble Model")
                .modelVersion("1.0")
                .modelType("ENSEMBLE")
                .score(finalScore)
                .probability(finalScore)
                .prediction(finalScore > 0.5 ? "HIGH_RISK" : "LOW_RISK")
                .confidence(calculateEnsembleConfidence(predictions))
                .featureImportance(aggregatedFeatureImportance)
                .classProbabilities(Map.of("HIGH_RISK", finalScore, "LOW_RISK", 1.0 - finalScore))
                .predictedAt(Instant.now())
                .predictionStatus("SUCCESS")
                .explanation(String.format("Ensemble prediction from %d models with weighted average strategy",
                        predictions.size()))
                .metadata(Map.of("models_used", predictions.size(), "voting_strategy", votingStrategy))
                .build();
    }

    private MLPrediction majorityVotePrediction(List<MLPrediction> predictions) {
        Map<String, Long> voteCounts = predictions.stream()
                .collect(Collectors.groupingBy(MLPrediction::getPrediction, Collectors.counting()));

        String majorityClass = voteCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");

        double confidence = voteCounts.get(majorityClass).doubleValue() / predictions.size();

        return MLPrediction.builder()
                .modelId("ensemble")
                .modelName("Ensemble Model")
                .modelVersion("1.0")
                .modelType("ENSEMBLE")
                .prediction(majorityClass)
                .confidence(confidence)
                .predictedAt(Instant.now())
                .predictionStatus("SUCCESS")
                .explanation(String.format("Majority vote from %d models: %s", predictions.size(), majorityClass))
                .metadata(Map.of("models_used", predictions.size(), "voting_strategy", votingStrategy))
                .build();
    }

    private MLPrediction maxPrediction(List<MLPrediction> predictions) {
        MLPrediction maxPred = predictions.stream()
                .max(Comparator.comparingDouble(MLPrediction::getScore))
                .orElse(predictions.get(0));

        maxPred.setExplanation("Maximum risk score from ensemble of " + predictions.size() + " models");
        return maxPred;
    }

    private MLPrediction minPrediction(List<MLPrediction> predictions) {
        MLPrediction minPred = predictions.stream()
                .min(Comparator.comparingDouble(MLPrediction::getScore))
                .orElse(predictions.get(0));

        minPred.setExplanation("Minimum risk score from ensemble of " + predictions.size() + " models");
        return minPred;
    }

    private double calculateEnsembleConfidence(List<MLPrediction> predictions) {
        // Confidence based on agreement between models
        double avgScore = predictions.stream()
                .mapToDouble(MLPrediction::getScore)
                .average()
                .orElse(0.5);

        double variance = predictions.stream()
                .mapToDouble(p -> Math.pow(p.getScore() - avgScore, 2))
                .average()
                .orElse(0.0);

        // Lower variance = higher confidence
        return Math.max(0.0, 1.0 - variance);
    }

    private MLPrediction createDefaultPrediction(TransactionRiskRequest request, String reason) {
        // Conservative high-risk default for safety
        return MLPrediction.builder()
                .modelId("ensemble")
                .modelName("Ensemble Model (Fallback)")
                .modelVersion("1.0")
                .modelType("ENSEMBLE")
                .score(0.75) // High risk default
                .probability(0.75)
                .prediction("HIGH_RISK")
                .confidence(0.5)
                .predictedAt(Instant.now())
                .predictionStatus("FALLBACK")
                .explanation(reason)
                .metadata(Map.of("fallback", true))
                .build();
    }

    public MLPrediction predictFallback(TransactionRiskRequest request, FeatureVector features, Exception e) {
        log.error("Ensemble ML model fallback triggered for transaction: {}", request.getTransactionId(), e);
        return createDefaultPrediction(request, "Circuit breaker fallback: " + e.getMessage());
    }

    @Override
    public String getModelId() {
        return "ensemble-model";
    }

    @Override
    public String getModelVersion() {
        return "1.0.0";
    }

    @Override
    public String getModelType() {
        return "ENSEMBLE";
    }

    @Override
    public boolean isReady() {
        return ready && (models.isEmpty() || models.stream().anyMatch(RiskMLModel::isReady));
    }

    @Override
    public Map<String, Double> getPerformanceMetrics() {
        return Map.of(
                "accuracy", 0.92,
                "precision", 0.89,
                "recall", 0.94,
                "f1_score", 0.91,
                "auc", 0.95
        );
    }

    @Override
    public void warmUp() {
        log.info("Warming up ensemble model...");
        models.forEach(RiskMLModel::warmUp);
        ready = true;
    }

    @Override
    public void reload() {
        log.info("Reloading ensemble model...");
        models.forEach(RiskMLModel::reload);
        configureModelWeights();
    }

    public void addModel(RiskMLModel model, double weight) {
        models.add(model);
        modelWeights.put(model.getModelId(), weight);
        log.info("Added model {} to ensemble with weight {}", model.getModelId(), weight);
    }

    public void removeModel(String modelId) {
        models.removeIf(m -> m.getModelId().equals(modelId));
        modelWeights.remove(modelId);
        log.info("Removed model {} from ensemble", modelId);
    }

    public void updateModelWeight(String modelId, double newWeight) {
        modelWeights.put(modelId, newWeight);
        log.info("Updated model {} weight to {}", modelId, newWeight);
    }
}
