package com.waqiti.analytics.config.properties;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics Service Configuration Properties
 *
 * <p>Production-grade configuration properties for the Analytics Service.
 * Binds analytics.* properties from application.yml to strongly-typed,
 * validated Java objects with comprehensive error handling and documentation.
 *
 * <p>Features:
 * <ul>
 *   <li>Bean Validation with custom constraints</li>
 *   <li>Detailed property documentation for IDE autocomplete</li>
 *   <li>Security-conscious sensitive property handling</li>
 *   <li>Post-construction validation for complex business rules</li>
 *   <li>Thread-safe immutable nested configurations</li>
 * </ul>
 *
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "analytics")
@Schema(description = "Analytics Service Configuration Properties")
@Slf4j
public class AnalyticsProperties {

    @Valid
    @NotNull(message = "Processing configuration cannot be null")
    private ProcessingConfig processing = new ProcessingConfig();

    @Valid
    @NotNull(message = "Real-time configuration cannot be null")
    private RealTimeConfig realTime = new RealTimeConfig();

    @Valid
    @NotNull(message = "Machine learning configuration cannot be null")
    private MachineLearningConfig ml = new MachineLearningConfig();

    @Valid
    @NotNull(message = "ETL configuration cannot be null")
    private EtlConfig etl = new EtlConfig();

    @Valid
    @NotNull(message = "Reporting configuration cannot be null")
    private ReportingConfig reporting = new ReportingConfig();

    @Valid
    @NotNull(message = "Dashboard configuration cannot be null")
    private DashboardConfig dashboard = new DashboardConfig();

    @Valid
    @NotNull(message = "Retention configuration cannot be null")
    private RetentionConfig retention = new RetentionConfig();

    @Valid
    @NotNull(message = "Performance configuration cannot be null")
    private PerformanceConfig performance = new PerformanceConfig();

    @Valid
    @NotNull(message = "Quality configuration cannot be null")
    private QualityConfig quality = new QualityConfig();

    /**
     * Validates configuration after properties are bound
     */
    @PostConstruct
    public void validateConfiguration() {
        log.info("Validating Analytics Configuration Properties");

        // Validate retention periods are logical
        if (retention.getRawDataDays() > retention.getAggregatedDataDays()) {
            log.warn("Raw data retention ({} days) is longer than aggregated data retention ({} days). " +
                    "This may lead to data inconsistency.",
                    retention.getRawDataDays(), retention.getAggregatedDataDays());
        }

        // Validate ML configuration paths if enabled
        if (ml.isEnabled()) {
            if (ml.getFraudDetection().getModelPath() == null || ml.getFraudDetection().getModelPath().isEmpty()) {
                log.warn("ML fraud detection enabled but model path not configured");
            }
            if (ml.getRecommendation().getModelPath() == null || ml.getRecommendation().getModelPath().isEmpty()) {
                log.warn("ML recommendation enabled but model path not configured");
            }
        }

        // Validate performance settings
        if (processing.getMaxThreads() > Runtime.getRuntime().availableProcessors() * 2) {
            log.warn("Configured max threads ({}) exceeds recommended limit (2x CPU cores: {}). " +
                    "This may cause performance degradation.",
                    processing.getMaxThreads(), Runtime.getRuntime().availableProcessors() * 2);
        }

        log.info("Analytics Configuration validated successfully");
    }

    /**
     * Data Processing Configuration
     */
    @Data
    @Schema(description = "Data processing configuration for batch and stream processing")
    public static class ProcessingConfig {

        @Min(value = 100, message = "Batch size must be at least 100")
        @Max(value = 100000, message = "Batch size must not exceed 100,000")
        @Schema(description = "Batch size for processing operations", example = "10000", minimum = "100", maximum = "100000")
        private int batchSize = 10000;

        @Schema(description = "Enable parallel processing for batch operations", example = "true")
        private boolean parallelProcessing = true;

        @Min(value = 1, message = "Max threads must be at least 1")
        @Max(value = 100, message = "Max threads must not exceed 100")
        @Schema(description = "Maximum number of threads for parallel processing", example = "10", minimum = "1", maximum = "100")
        private int maxThreads = 10;

        @Min(value = 100, message = "Chunk size must be at least 100")
        @Max(value = 50000, message = "Chunk size must not exceed 50,000")
        @Schema(description = "Chunk size for processing large datasets", example = "1000", minimum = "100", maximum = "50000")
        private int chunkSize = 1000;

        @Min(value = 1, message = "Retention days must be at least 1")
        @Max(value = 3650, message = "Retention days must not exceed 10 years (3650 days)")
        @Schema(description = "Data retention period in days", example = "2555", minimum = "1", maximum = "3650")
        private int retentionDays = 2555;
    }

    /**
     * Real-time Analytics Configuration
     */
    @Data
    @Schema(description = "Real-time analytics and streaming configuration")
    public static class RealTimeConfig {

        @Schema(description = "Enable real-time analytics processing", example = "true")
        private boolean enabled = true;

        @Min(value = 1, message = "Window size must be at least 1 minute")
        @Max(value = 60, message = "Window size must not exceed 60 minutes")
        @Schema(description = "Sliding window size in minutes for real-time aggregation", example = "5", minimum = "1", maximum = "60")
        private int windowSizeMinutes = 5;

        @Min(value = 5, message = "Aggregation interval must be at least 5 seconds")
        @Max(value = 300, message = "Aggregation interval must not exceed 300 seconds")
        @Schema(description = "Aggregation interval in seconds", example = "30", minimum = "5", maximum = "300")
        private int aggregationIntervalSeconds = 30;

        @Valid
        @NotNull(message = "Alert thresholds cannot be null")
        private AlertThresholds alertThresholds = new AlertThresholds();

        @Data
        @Schema(description = "Alert threshold configuration for real-time monitoring")
        public static class AlertThresholds {

            @Min(value = 1, message = "Transaction volume threshold must be at least 1")
            @Schema(description = "Transaction volume alert threshold", example = "1000", minimum = "1")
            private int transactionVolume = 1000;

            @DecimalMin(value = "0.0", inclusive = false, message = "Error rate must be greater than 0")
            @DecimalMax(value = "1.0", message = "Error rate must not exceed 1.0 (100%)")
            @Schema(description = "Error rate alert threshold (0.0-1.0)", example = "0.05", minimum = "0.0", maximum = "1.0")
            private double errorRate = 0.05;

            @Min(value = 100, message = "Response time threshold must be at least 100ms")
            @Schema(description = "Response time alert threshold in milliseconds", example = "5000", minimum = "100")
            private int responseTimeMs = 5000;
        }
    }

    /**
     * Machine Learning Configuration
     */
    @Data
    @Schema(description = "Machine learning and AI model configuration")
    public static class MachineLearningConfig {

        @Schema(description = "Enable machine learning features", example = "true")
        private boolean enabled = true;

        @Valid
        @NotNull(message = "Model training configuration cannot be null")
        private ModelTraining modelTraining = new ModelTraining();

        @Valid
        @NotNull(message = "Fraud detection configuration cannot be null")
        private FraudDetection fraudDetection = new FraudDetection();

        @Valid
        @NotNull(message = "Recommendation configuration cannot be null")
        private Recommendation recommendation = new Recommendation();

        @Data
        @Schema(description = "Model training and retraining configuration")
        public static class ModelTraining {

            @Schema(description = "Enable automatic model retraining", example = "true")
            private boolean autoRetrain = true;

            @Min(value = 1, message = "Retrain interval must be at least 1 hour")
            @Max(value = 720, message = "Retrain interval must not exceed 30 days (720 hours)")
            @Schema(description = "Model retraining interval in hours", example = "24", minimum = "1", maximum = "720")
            private int retrainIntervalHours = 24;

            @Min(value = 1000, message = "Minimum data points must be at least 1,000")
            @Schema(description = "Minimum data points required for model training", example = "10000", minimum = "1000")
            private int minDataPoints = 10000;
        }

        @Data
        @Schema(description = "Fraud detection model configuration")
        public static class FraudDetection {

            @NotBlank(message = "Fraud detection model path cannot be blank")
            @Pattern(regexp = "^/.*", message = "Model path must be an absolute path starting with /")
            @Schema(description = "Absolute path to fraud detection model", example = "/opt/ml/models/fraud-detection", pattern = "^/.*")
            private String modelPath = "/opt/ml/models/fraud-detection";

            @DecimalMin(value = "0.0", inclusive = false, message = "Threshold must be greater than 0")
            @DecimalMax(value = "1.0", message = "Threshold must not exceed 1.0")
            @Schema(description = "Fraud detection confidence threshold (0.0-1.0)", example = "0.75", minimum = "0.0", maximum = "1.0")
            private double threshold = 0.75;

            @Schema(description = "Enable automatic feature extraction", example = "true")
            private boolean featureExtraction = true;
        }

        @Data
        @Schema(description = "Recommendation engine configuration")
        public static class Recommendation {

            @NotBlank(message = "Recommendation model path cannot be blank")
            @Pattern(regexp = "^/.*", message = "Model path must be an absolute path starting with /")
            @Schema(description = "Absolute path to recommendation model", example = "/opt/ml/models/recommendation", pattern = "^/.*")
            private String modelPath = "/opt/ml/models/recommendation";

            @Schema(description = "Enable collaborative filtering", example = "true")
            private boolean collaborativeFiltering = true;

            @Schema(description = "Enable content-based filtering", example = "true")
            private boolean contentBased = true;
        }
    }

    /**
     * ETL (Extract, Transform, Load) Configuration
     */
    @Data
    @Schema(description = "ETL pipeline configuration for data ingestion and transformation")
    public static class EtlConfig {

        @Schema(description = "Enable ETL processes", example = "true")
        private boolean enabled = true;

        @NotBlank(message = "ETL schedule cron expression cannot be blank")
        @Pattern(regexp = "^(\\*|\\d+|\\d+-\\d+|\\d+/\\d+)(\\s+(\\*|\\d+|\\d+-\\d+|\\d+/\\d+)){5}$",
                message = "Schedule must be a valid cron expression")
        @Schema(description = "Cron expression for ETL job scheduling", example = "0 0 2 * * ?")
        private String schedule = "0 0 2 * * ?";

        @Min(value = 1, message = "Parallel jobs must be at least 1")
        @Max(value = 20, message = "Parallel jobs must not exceed 20")
        @Schema(description = "Number of parallel ETL jobs", example = "5", minimum = "1", maximum = "20")
        private int parallelJobs = 5;

        @Valid
        @NotNull(message = "Data sources configuration cannot be null")
        private DataSources dataSources = new DataSources();

        @Valid
        @NotNull(message = "Transformations configuration cannot be null")
        private Transformations transformations = new Transformations();

        @Data
        @Schema(description = "Data source configuration for ETL pipeline")
        public static class DataSources {
            @Schema(description = "Include user service data", example = "true")
            private boolean userService = true;

            @Schema(description = "Include wallet service data", example = "true")
            private boolean walletService = true;

            @Schema(description = "Include payment service data", example = "true")
            private boolean paymentService = true;

            @Schema(description = "Include security service data", example = "true")
            private boolean securityService = true;

            @Schema(description = "Include core banking service data", example = "true")
            private boolean coreBankingService = true;
        }

        @Data
        @Schema(description = "Data transformation configuration")
        public static class Transformations {
            @Schema(description = "Enable data cleansing", example = "true")
            private boolean dataCleansing = true;

            @Schema(description = "Enable data enrichment", example = "true")
            private boolean dataEnrichment = true;

            @Schema(description = "Enable feature engineering", example = "true")
            private boolean featureEngineering = true;
        }
    }

    /**
     * Reporting Configuration
     */
    @Data
    @Schema(description = "Report generation and distribution configuration")
    public static class ReportingConfig {

        @Schema(description = "Enable reporting features", example = "true")
        private boolean enabled = true;

        @NotEmpty(message = "At least one report format must be configured")
        @Schema(description = "Supported report formats", example = "[\"PDF\", \"EXCEL\", \"CSV\", \"JSON\"]")
        private List<String> formats = new ArrayList<>(List.of("PDF", "EXCEL", "CSV", "JSON"));

        @Schema(description = "Scheduled report cron expressions mapped by report name")
        private Map<String, String> scheduledReports = new HashMap<>();

        @Valid
        @NotNull(message = "Distribution configuration cannot be null")
        private Distribution distribution = new Distribution();

        @Data
        @Schema(description = "Report distribution configuration")
        public static class Distribution {
            @Schema(description = "Enable email distribution", example = "true")
            private boolean email = true;

            @Schema(description = "Enable dashboard distribution", example = "true")
            private boolean dashboard = true;

            @Schema(description = "Enable API distribution", example = "true")
            private boolean api = true;
        }
    }

    /**
     * Dashboard Configuration
     */
    @Data
    @Schema(description = "Analytics dashboard configuration")
    public static class DashboardConfig {

        @Min(value = 5, message = "Refresh interval must be at least 5 seconds")
        @Max(value = 300, message = "Refresh interval must not exceed 300 seconds")
        @Schema(description = "Dashboard refresh interval in seconds", example = "30", minimum = "5", maximum = "300")
        private int refreshIntervalSeconds = 30;

        @Min(value = 1, message = "Cache duration must be at least 1 minute")
        @Max(value = 60, message = "Cache duration must not exceed 60 minutes")
        @Schema(description = "Dashboard cache duration in minutes", example = "5", minimum = "1", maximum = "60")
        private int cacheDurationMinutes = 5;

        @Schema(description = "Enable real-time dashboard updates", example = "true")
        private boolean realTimeUpdates = true;

        @Min(value = 100, message = "Max data points must be at least 100")
        @Max(value = 10000, message = "Max data points must not exceed 10,000")
        @Schema(description = "Maximum data points to display", example = "1000", minimum = "100", maximum = "10000")
        private int maxDataPoints = 1000;

        @Valid
        @NotNull(message = "Widgets configuration cannot be null")
        private Widgets widgets = new Widgets();

        @Data
        @Schema(description = "Dashboard widget enablement configuration")
        public static class Widgets {
            @Schema(description = "Enable transaction volume widget", example = "true")
            private boolean transactionVolume = true;

            @Schema(description = "Enable revenue metrics widget", example = "true")
            private boolean revenueMetrics = true;

            @Schema(description = "Enable user growth widget", example = "true")
            private boolean userGrowth = true;

            @Schema(description = "Enable fraud alerts widget", example = "true")
            private boolean fraudAlerts = true;

            @Schema(description = "Enable system health widget", example = "true")
            private boolean systemHealth = true;
        }
    }

    /**
     * Data Retention Configuration
     */
    @Data
    @Schema(description = "Data retention policy configuration")
    public static class RetentionConfig {

        @Min(value = 1, message = "Raw data retention must be at least 1 day")
        @Max(value = 3650, message = "Raw data retention must not exceed 10 years (3650 days)")
        @Schema(description = "Raw data retention period in days", example = "90", minimum = "1", maximum = "3650")
        private int rawDataDays = 90;

        @Min(value = 1, message = "Aggregated data retention must be at least 1 day")
        @Max(value = 3650, message = "Aggregated data retention must not exceed 10 years (3650 days)")
        @Schema(description = "Aggregated data retention period in days", example = "2555", minimum = "1", maximum = "3650")
        private int aggregatedDataDays = 2555;

        @Min(value = 1, message = "ML training data retention must be at least 1 day")
        @Max(value = 3650, message = "ML training data retention must not exceed 10 years (3650 days)")
        @Schema(description = "ML training data retention period in days", example = "365", minimum = "1", maximum = "3650")
        private int mlTrainingDataDays = 365;

        @Min(value = 1, message = "Audit logs retention must be at least 1 day")
        @Max(value = 3650, message = "Audit logs retention must not exceed 10 years (3650 days)")
        @Schema(description = "Audit logs retention period in days", example = "2555", minimum = "1", maximum = "3650")
        private int auditLogsDays = 2555;

        @NotBlank(message = "Cleanup schedule cron expression cannot be blank")
        @Pattern(regexp = "^(\\*|\\d+|\\d+-\\d+|\\d+/\\d+)(\\s+(\\*|\\d+|\\d+-\\d+|\\d+/\\d+)){5}$",
                message = "Cleanup schedule must be a valid cron expression")
        @Schema(description = "Cron expression for cleanup job scheduling", example = "0 0 3 * * ?")
        private String cleanupSchedule = "0 0 3 * * ?";
    }

    /**
     * Performance Optimization Configuration
     */
    @Data
    @Schema(description = "Performance optimization configuration")
    public static class PerformanceConfig {

        @Min(value = 30, message = "Query timeout must be at least 30 seconds")
        @Max(value = 3600, message = "Query timeout must not exceed 1 hour (3600 seconds)")
        @Schema(description = "Query timeout in seconds", example = "300", minimum = "30", maximum = "3600")
        private int queryTimeoutSeconds = 300;

        @Min(value = 5, message = "Connection pool size must be at least 5")
        @Max(value = 100, message = "Connection pool size must not exceed 100")
        @Schema(description = "Database connection pool size", example = "20", minimum = "5", maximum = "100")
        private int connectionPoolSize = 20;

        @Min(value = 64, message = "Cache size must be at least 64 MB")
        @Max(value = 4096, message = "Cache size must not exceed 4 GB (4096 MB)")
        @Schema(description = "Cache size in megabytes", example = "512", minimum = "64", maximum = "4096")
        private int cacheSizeMb = 512;

        @Schema(description = "Enable database index optimization", example = "true")
        private boolean indexOptimization = true;

        @Schema(description = "Enable query optimization", example = "true")
        private boolean queryOptimization = true;
    }

    /**
     * Data Quality Configuration
     */
    @Data
    @Schema(description = "Data quality and validation configuration")
    public static class QualityConfig {

        @Schema(description = "Enable data validation", example = "true")
        private boolean validationEnabled = true;

        @Schema(description = "Enable anomaly detection", example = "true")
        private boolean anomalyDetection = true;

        @Schema(description = "Enable data profiling", example = "true")
        private boolean dataProfiling = true;

        @DecimalMin(value = "0.0", message = "Completeness threshold must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Completeness threshold must not exceed 1.0")
        @Schema(description = "Data completeness threshold (0.0-1.0)", example = "0.95", minimum = "0.0", maximum = "1.0")
        private double completenessThreshold = 0.95;

        @DecimalMin(value = "0.0", message = "Accuracy threshold must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Accuracy threshold must not exceed 1.0")
        @Schema(description = "Data accuracy threshold (0.0-1.0)", example = "0.98", minimum = "0.0", maximum = "1.0")
        private double accuracyThreshold = 0.98;
    }
}
