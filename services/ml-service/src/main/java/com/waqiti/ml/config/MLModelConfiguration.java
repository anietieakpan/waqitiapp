package com.waqiti.ml.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Production-ready ML Model Configuration.
 * Manages all ML model settings, thresholds, and feature engineering parameters.
 */
@ConfigurationProperties(prefix = "ml.model")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Validated
public class MLModelConfiguration {

    /**
     * Model paths configuration
     */
    private ModelPaths models = new ModelPaths();

    /**
     * Feature engineering configuration
     */
    private FeatureEngineering features = new FeatureEngineering();

    /**
     * Risk scoring thresholds
     */
    private RiskThresholds thresholds = new RiskThresholds();

    /**
     * Model performance settings
     */
    private PerformanceSettings performance = new PerformanceSettings();

    /**
     * Training configuration
     */
    private TrainingConfig training = new TrainingConfig();

    /**
     * Monitoring and alerting configuration
     */
    private MonitoringConfig monitoring = new MonitoringConfig();

    /**
     * Model versioning configuration
     */
    private VersioningConfig versioning = new VersioningConfig();

    /**
     * Model paths and locations
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPaths {
        
        @NotBlank(message = "Fraud detection model path is required")
        private String fraudDetectionPath = "/models/fraud-detection";
        
        @NotBlank(message = "Anomaly detection model path is required")
        private String anomalyDetectionPath = "/models/anomaly-detection";
        
        @NotBlank(message = "Risk scoring model path is required")
        private String riskScoringPath = "/models/risk-scoring";
        
        @NotBlank(message = "Clustering model path is required")
        private String clusteringPath = "/models/clustering";
        
        private String behaviorAnalysisPath = "/models/behavior-analysis";
        private String velocityCheckPath = "/models/velocity-check";
        private String geolocationPath = "/models/geolocation";
        private String deviceFingerprintPath = "/models/device-fingerprint";
        private String networkAnalysisPath = "/models/network-analysis";
        
        private String modelBackupPath = "/models/backup";
        private String modelArchivePath = "/models/archive";
        private String trainingDataPath = "/data/training";
        private String validationDataPath = "/data/validation";
    }

    /**
     * Feature engineering configuration
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureEngineering {
        
        @Min(10)
        @Max(1000)
        private Integer featureVectorSize = 25;
        
        private Boolean scalingEnabled = true;
        private String scalingMethod = "z-score"; // z-score, min-max, robust
        
        private Boolean imputationEnabled = true;
        private String imputationStrategy = "median"; // mean, median, mode, forward-fill
        
        private Boolean outlierRemovalEnabled = false;
        private Double outlierThreshold = 3.0; // Standard deviations
        
        private Map<String, Double> featureMeans = new HashMap<>();
        private Map<String, Double> featureStdDevs = new HashMap<>();
        private Map<String, Double> featureMinValues = new HashMap<>();
        private Map<String, Double> featureMaxValues = new HashMap<>();
        
        private List<String> categoricalFeatures = new ArrayList<>();
        private List<String> numericalFeatures = new ArrayList<>();
        private List<String> temporalFeatures = new ArrayList<>();
        
        private Boolean polynomialFeaturesEnabled = false;
        private Integer polynomialDegree = 2;
        
        private Boolean interactionFeaturesEnabled = false;
        private List<String[]> interactionPairs = new ArrayList<>();
        
        private Boolean dimensionalityReductionEnabled = false;
        private String reductionMethod = "PCA"; // PCA, LDA, t-SNE
        private Integer targetDimensions = 15;
    }

    /**
     * Risk scoring thresholds
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskThresholds {
        
        @Min(0)
        @Max(100)
        private Double lowRiskThreshold = 20.0;
        
        @Min(0)
        @Max(100)
        private Double mediumRiskThreshold = 40.0;
        
        @Min(0)
        @Max(100)
        private Double highRiskThreshold = 60.0;
        
        @Min(0)
        @Max(100)
        private Double criticalRiskThreshold = 80.0;
        
        @Min(0)
        @Max(100)
        private Double autoBlockThreshold = 90.0;
        
        @Min(0)
        @Max(100)
        private Double manualReviewThreshold = 70.0;
        
        @Min(0)
        @Max(100)
        private Double anomalyScoreThreshold = 0.7;
        
        @Min(0)
        @Max(100)
        private Double fraudProbabilityThreshold = 0.8;
        
        private Map<String, Double> transactionTypeThresholds = new HashMap<>();
        private Map<String, Double> countryRiskThresholds = new HashMap<>();
        private Map<String, Double> merchantCategoryThresholds = new HashMap<>();
        
        @Min(0)
        private Double velocityThresholdKmh = 900.0;
        
        @Min(0)
        private Double amountThresholdHigh = 10000.0;
        
        @Min(0)
        private Double amountThresholdMedium = 5000.0;
        
        @Min(0)
        private Integer burstTransactionThreshold = 5;
        
        @Min(0)
        private Integer burstTimeWindowMinutes = 10;
    }

    /**
     * Model performance settings
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceSettings {
        
        @Min(1)
        @Max(32)
        private Integer inferenceThreads = 4;
        
        @Min(1)
        @Max(1000)
        private Integer batchSize = 32;
        
        @Min(100)
        @Max(60000)
        private Integer timeoutMs = 5000;
        
        @Min(1)
        @Max(10)
        private Integer maxRetries = 3;
        
        @Min(100)
        @Max(10000)
        private Integer retryDelayMs = 1000;
        
        private Boolean cachingEnabled = true;
        
        @Min(1)
        @Max(1440)
        private Integer cacheExpirationMinutes = 60;
        
        @Min(100)
        @Max(100000)
        private Integer maxCacheSize = 10000;
        
        private Boolean lazyLoadingEnabled = true;
        private Boolean preloadModelsOnStartup = true;
        private Boolean warmupEnabled = true;
        
        @Min(1)
        @Max(1000)
        private Integer warmupIterations = 10;
        
        private Boolean gpuEnabled = false;
        private String gpuDevice = "0";
        
        @Min(1)
        @Max(100)
        private Integer maxConcurrentInferences = 20;
        
        private Boolean profilingEnabled = false;
        private Boolean metricsCollectionEnabled = true;
    }

    /**
     * Training configuration
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingConfig {
        
        private Boolean autoRetrainingEnabled = false;
        
        @Min(1)
        @Max(365)
        private Integer retrainingIntervalDays = 30;
        
        @Min(0.0)
        @Max(1.0)
        private Double trainTestSplit = 0.8;
        
        @Min(0.0)
        @Max(1.0)
        private Double validationSplit = 0.1;
        
        @Min(1)
        @Max(1000)
        private Integer epochs = 100;
        
        @Min(0.0001)
        @Max(1.0)
        private Double learningRate = 0.001;
        
        @Min(1)
        @Max(1024)
        private Integer trainingBatchSize = 64;
        
        private String optimizer = "adam"; // adam, sgd, rmsprop
        private String lossFunction = "binary_crossentropy";
        
        private List<String> metrics = List.of("accuracy", "precision", "recall", "f1", "auc");
        
        private Boolean earlyStopping = true;
        
        @Min(1)
        @Max(100)
        private Integer patienceEpochs = 10;
        
        @Min(0.0)
        @Max(1.0)
        private Double dropoutRate = 0.2;
        
        private Boolean dataSugmentationEnabled = false;
        private Boolean classWeightBalancing = true;
        
        @Min(1000)
        private Integer minTrainingSamples = 10000;
        
        private String modelSelectionMetric = "f1";
        
        private Boolean crossValidationEnabled = true;
        
        @Min(2)
        @Max(20)
        private Integer crossValidationFolds = 5;
    }

    /**
     * Monitoring and alerting configuration
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonitoringConfig {
        
        private Boolean enabled = true;
        
        @Min(1)
        @Max(60)
        private Integer metricsIntervalMinutes = 5;
        
        private Boolean driftDetectionEnabled = true;
        
        @Min(0.0)
        @Max(1.0)
        private Double driftThreshold = 0.1;
        
        private Boolean performanceMonitoringEnabled = true;
        
        @Min(0.0)
        @Max(1.0)
        private Double performanceDegradationThreshold = 0.05;
        
        private List<String> alertChannels = List.of("email", "slack", "pagerduty");
        
        private Map<String, String> alertRecipients = new HashMap<>();
        
        @Min(1)
        @Max(1440)
        private Integer alertCooldownMinutes = 60;
        
        private Boolean dataQualityMonitoring = true;
        
        @Min(0.0)
        @Max(1.0)
        private Double missingDataThreshold = 0.1;
        
        private Boolean modelComparisonEnabled = true;
        
        private List<String> comparisonMetrics = List.of("accuracy", "latency", "throughput");
        
        @Min(1)
        @Max(100000)
        private Integer samplingRate = 100; // 1 in N requests
        
        private Boolean auditLoggingEnabled = true;
        
        @Min(1)
        @Max(365)
        private Integer auditRetentionDays = 90;
        
        private Boolean explainabilityEnabled = false;
        private String explainabilityMethod = "SHAP"; // SHAP, LIME, IntegratedGradients
    }

    /**
     * Model versioning configuration
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersioningConfig {
        
        private String currentVersion = "2.1.0";
        
        private Boolean versioningEnabled = true;
        
        @Min(1)
        @Max(100)
        private Integer maxVersionsToKeep = 5;
        
        private Boolean canaryDeploymentEnabled = false;
        
        @Min(0.0)
        @Max(1.0)
        private Double canaryTrafficPercentage = 0.1;
        
        private Boolean shadowModeEnabled = false;
        
        private Boolean abTestingEnabled = false;
        
        private Map<String, Double> versionWeights = new HashMap<>();
        
        private Boolean autoRollbackEnabled = true;
        
        @Min(0.0)
        @Max(1.0)
        private Double rollbackThreshold = 0.15; // Performance degradation threshold
        
        private String namingConvention = "v{major}.{minor}.{patch}";
        
        private Boolean gitIntegrationEnabled = false;
        
        private String modelRegistry = "mlflow"; // mlflow, kubeflow, custom
        
        private Map<String, String> versionMetadata = new HashMap<>();
    }

    /**
     * Get risk level based on score
     */
    public String getRiskLevel(double score) {
        if (score >= thresholds.getCriticalRiskThreshold()) return "CRITICAL";
        if (score >= thresholds.getHighRiskThreshold()) return "HIGH";
        if (score >= thresholds.getMediumRiskThreshold()) return "MEDIUM";
        if (score >= thresholds.getLowRiskThreshold()) return "LOW";
        return "MINIMAL";
    }

    /**
     * Check if score requires manual review
     */
    public boolean requiresManualReview(double score) {
        return score >= thresholds.getManualReviewThreshold();
    }

    /**
     * Check if score requires auto-blocking
     */
    public boolean requiresAutoBlock(double score) {
        return score >= thresholds.getAutoBlockThreshold();
    }

    /**
     * Get ensemble weights for different models
     */
    public Map<String, Double> getEnsembleWeights() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("fraud_detection", 0.5);
        weights.put("risk_scoring", 0.3);
        weights.put("anomaly_detection", 0.2);
        return weights;
    }

    /**
     * Get feature importance weights
     */
    public Map<String, Double> getFeatureImportanceWeights() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("amount", 0.25);
        weights.put("velocity", 0.20);
        weights.put("location_risk", 0.15);
        weights.put("device_trust", 0.15);
        weights.put("behavioral_pattern", 0.15);
        weights.put("network_risk", 0.10);
        return weights;
    }

    /**
     * Validate configuration consistency
     */
    public boolean isValid() {
        // Ensure thresholds are in ascending order
        return thresholds.getLowRiskThreshold() < thresholds.getMediumRiskThreshold() &&
               thresholds.getMediumRiskThreshold() < thresholds.getHighRiskThreshold() &&
               thresholds.getHighRiskThreshold() < thresholds.getCriticalRiskThreshold() &&
               thresholds.getCriticalRiskThreshold() <= thresholds.getAutoBlockThreshold();
    }

    /**
     * Get configuration summary for logging
     */
    public String getConfigurationSummary() {
        return String.format(
            "ML Configuration [Version: %s, Models: %d, Features: %d, GPU: %s, Monitoring: %s]",
            versioning.getCurrentVersion(),
            4, // Number of models
            features.getFeatureVectorSize(),
            performance.getGpuEnabled() ? "Enabled" : "Disabled",
            monitoring.getEnabled() ? "Enabled" : "Disabled"
        );
    }
}