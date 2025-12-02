package com.waqiti.ml.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * Machine Learning service configuration properties
 */
@Data
@ConfigurationProperties(prefix = "ml")
public class MLProperties {

    private ModelsConfig models = new ModelsConfig();
    private FeatureStoreConfig featureStore = new FeatureStoreConfig();
    private InferenceConfig inference = new InferenceConfig();
    private TrainingConfig training = new TrainingConfig();
    private MonitoringConfig monitoring = new MonitoringConfig();

    @Data
    public static class ModelsConfig {
        private ModelConfig fraudDetection = new ModelConfig();
        private ModelConfig riskScoring = new ModelConfig();
        private ModelConfig behavioralAnalysis = new ModelConfig();
        private ModelConfig patternRecognition = new ModelConfig();
    }

    @Data
    public static class ModelConfig {
        private boolean enabled = true;
        private String modelPath;
        private int batchSize = 100;
        private double confidenceThreshold = 0.7;
        private Duration retrainInterval = Duration.ofHours(24);
        private ModelFeatureStoreConfig featureStore = new ModelFeatureStoreConfig();
    }

    @Data
    public static class ModelFeatureStoreConfig {
        private boolean enabled = true;
        private String feastServer = "localhost:6566";
    }

    @Data
    public static class FeatureStoreConfig {
        private boolean enabled = true;
        private FeastConfig feast = new FeastConfig();
    }

    @Data
    public static class FeastConfig {
        private String server = "localhost:6566";
        private String project = "waqiti-ml";
        private String registry = "/app/feature-store/registry.db";
        private OnlineStoreConfig onlineStore = new OnlineStoreConfig();
        private OfflineStoreConfig offlineStore = new OfflineStoreConfig();
    }

    @Data
    public static class OnlineStoreConfig {
        private String type = "redis";
        private String connectionString;
    }

    @Data
    public static class OfflineStoreConfig {
        private String type = "postgres";
        private String host = "localhost";
        private int port = 5432;
        private String database;
        private String user;
        private String password;
    }

    @Data
    public static class InferenceConfig {
        private Duration timeout = Duration.ofSeconds(5);
        private int maxConcurrentRequests = 100;
        private int queueCapacity = 1000;
        private int threadPoolSize = 20;
        private BatchProcessingConfig batchProcessing = new BatchProcessingConfig();
    }

    @Data
    public static class BatchProcessingConfig {
        private boolean enabled = true;
        private int batchSize = 50;
        private Duration maxWaitTime = Duration.ofSeconds(1);
    }

    @Data
    public static class TrainingConfig {
        private boolean enabled = true;
        private String schedule = "0 2 * * *";
        private int dataRetentionDays = 90;
        private boolean modelBackupEnabled = true;
        private String modelBackupLocation = "/app/backups/models";
    }

    @Data
    public static class MonitoringConfig {
        private boolean modelDriftDetection = true;
        private boolean performanceTracking = true;
        private boolean dataQualityMonitoring = true;
        private AlertThresholdConfig alertThreshold = new AlertThresholdConfig();
    }

    @Data
    public static class AlertThresholdConfig {
        private double accuracyDrop = 0.05;
        private double predictionDrift = 0.1;
        private double dataQuality = 0.8;
    }
}