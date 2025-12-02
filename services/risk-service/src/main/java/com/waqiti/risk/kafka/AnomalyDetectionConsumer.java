package com.waqiti.risk.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.cache.RedisCache;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.NotificationTemplate;
import com.waqiti.common.fraud.ml.MachineLearningModelService;
import com.waqiti.risk.dto.*;
import com.waqiti.risk.model.*;
import com.waqiti.risk.service.*;
import com.waqiti.risk.repository.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AnomalyDetectionConsumer {

    private static final String TOPIC = "anomaly-detection";
    private static final String DLQ_TOPIC = "anomaly-detection.dlq";
    private static final int MAX_RETRIES = 3;
    private static final long PROCESSING_TIMEOUT_MS = 12000;
    private static final double CRITICAL_ANOMALY_THRESHOLD = 0.90;
    private static final double HIGH_ANOMALY_THRESHOLD = 0.75;
    private static final double MEDIUM_ANOMALY_THRESHOLD = 0.55;
    private static final double LOW_ANOMALY_THRESHOLD = 0.35;
    
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AnomalyRepository anomalyRepository;
    private final BaselineRepository baselineRepository;
    private final AnomalyModelRepository anomalyModelRepository;
    private final TimeSeriesRepository timeSeriesRepository;
    private final StatisticalAnomalyService statisticalAnomalyService;
    private final MLAnomalyService mlAnomalyService;
    private final TimeSeriesAnomalyService timeSeriesAnomalyService;
    private final ContextualAnomalyService contextualAnomalyService;
    private final CollectiveAnomalyService collectiveAnomalyService;
    private final BaselineService baselineService;
    private final OutlierDetectionService outlierDetectionService;
    private final MachineLearningModelService mlModelService;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final RedisCache redisCache;
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    
    private static final Map<String, Double> ANOMALY_WEIGHTS = new HashMap<>();
    private static final Map<String, Double> DETECTION_THRESHOLDS = new HashMap<>();
    
    static {
        ANOMALY_WEIGHTS.put("STATISTICAL", 0.25);
        ANOMALY_WEIGHTS.put("ML_BASED", 0.30);
        ANOMALY_WEIGHTS.put("TIME_SERIES", 0.20);
        ANOMALY_WEIGHTS.put("CONTEXTUAL", 0.15);
        ANOMALY_WEIGHTS.put("COLLECTIVE", 0.10);
        
        DETECTION_THRESHOLDS.put("Z_SCORE", 3.0);
        DETECTION_THRESHOLDS.put("IQR", 1.5);
        DETECTION_THRESHOLDS.put("ISOLATION_FOREST", 0.1);
        DETECTION_THRESHOLDS.put("LSTM_ERROR", 0.05);
        DETECTION_THRESHOLDS.put("DBSCAN_OUTLIER", -1.0);
    }
    
    public AnomalyDetectionConsumer(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            AnomalyRepository anomalyRepository,
            BaselineRepository baselineRepository,
            AnomalyModelRepository anomalyModelRepository,
            TimeSeriesRepository timeSeriesRepository,
            StatisticalAnomalyService statisticalAnomalyService,
            MLAnomalyService mlAnomalyService,
            TimeSeriesAnomalyService timeSeriesAnomalyService,
            ContextualAnomalyService contextualAnomalyService,
            CollectiveAnomalyService collectiveAnomalyService,
            BaselineService baselineService,
            OutlierDetectionService outlierDetectionService,
            MachineLearningModelService mlModelService,
            NotificationService notificationService,
            MetricsService metricsService,
            RedisCache redisCache
    ) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.anomalyRepository = anomalyRepository;
        this.baselineRepository = baselineRepository;
        this.anomalyModelRepository = anomalyModelRepository;
        this.timeSeriesRepository = timeSeriesRepository;
        this.statisticalAnomalyService = statisticalAnomalyService;
        this.mlAnomalyService = mlAnomalyService;
        this.timeSeriesAnomalyService = timeSeriesAnomalyService;
        this.contextualAnomalyService = contextualAnomalyService;
        this.collectiveAnomalyService = collectiveAnomalyService;
        this.baselineService = baselineService;
        this.outlierDetectionService = outlierDetectionService;
        this.mlModelService = mlModelService;
        this.notificationService = notificationService;
        this.metricsService = metricsService;
        this.redisCache = redisCache;
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .failureRateThreshold(50)
            .build();
            
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("anomaly-detection");
        
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(MAX_RETRIES)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
            .build();
            
        this.retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry("anomaly-detection");
        
        this.executorService = Executors.newFixedThreadPool(10);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(4);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker state transition: {}", event.getStateTransition()));
        
        initializeBackgroundTasks();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyDetectionRequest {
        private String requestId;
        private String entityType;
        private String entityId;
        private String userId;
        private String sessionId;
        private DataPoint dataPoint;
        private List<DataPoint> historicalData;
        private DetectionContext context;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        private boolean realTimeDetection;
        private String correlationId;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private String dataPointId;
        private String metricName;
        private Object value;
        private String dataType;
        private Map<String, Object> attributes;
        private Map<String, Object> contextualFeatures;
        private LocalDateTime timestamp;
        private String source;
        private Double confidence;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectionContext {
        private String domain;
        private String subdomain;
        private List<String> focusMetrics;
        private Map<String, Object> businessContext;
        private Duration timeWindow;
        private String sensitivity;
        private List<String> algorithms;
        private Map<String, Object> parameters;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyDetectionResult {
        private String analysisId;
        private String requestId;
        private String entityId;
        private List<DetectedAnomaly> anomalies;
        private List<StatisticalTest> statisticalTests;
        private List<MLDetection> mlDetections;
        private TimeSeriesAnalysis timeSeriesAnalysis;
        private ContextualAnalysis contextualAnalysis;
        private CollectiveAnalysis collectiveAnalysis;
        private AnomalyAssessment assessment;
        private List<AnomalyRecommendation> recommendations;
        private AnomalyDecision decision;
        private String modelVersion;
        private Long processingTimeMs;
        private LocalDateTime analyzedAt;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectedAnomaly {
        private String anomalyId;
        private String anomalyType;
        private String metricName;
        private Object expectedValue;
        private Object observedValue;
        private Double anomalyScore;
        private Double confidence;
        private String severity;
        private String description;
        private LocalDateTime detectedAt;
        private Map<String, Object> evidence;
        private List<String> contributingFactors;
        private String detectionMethod;
        private boolean validated;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticalTest {
        private String testName;
        private String testType;
        private Double testStatistic;
        private Double pValue;
        private Double criticalValue;
        private boolean significantResult;
        private String interpretation;
        private Map<String, Object> testParameters;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MLDetection {
        private String modelName;
        private String algorithm;
        private Double anomalyScore;
        private Double threshold;
        private boolean isAnomaly;
        private List<String> featureImportance;
        private Map<String, Object> modelMetrics;
        private String explanation;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesAnalysis {
        private String seriesName;
        private List<TimeSeriesAnomaly> detectedAnomalies;
        private SeasonalityAnalysis seasonality;
        private TrendAnalysis trend;
        private Map<String, Double> forecastMetrics;
        private List<ChangePoint> changePoints;
        private Map<String, Object> decomposition;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesAnomaly {
        private LocalDateTime timestamp;
        private Double value;
        private Double expectedValue;
        private Double anomalyScore;
        private String anomalyType;
        private String description;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeasonalityAnalysis {
        private boolean seasonalityDetected;
        private Integer seasonalPeriod;
        private Double seasonalStrength;
        private Map<String, Object> seasonalComponents;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendAnalysis {
        private boolean trendDetected;
        private String trendDirection;
        private Double trendStrength;
        private Double slope;
        private Map<String, Object> trendParameters;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePoint {
        private LocalDateTime timestamp;
        private String changeType;
        private Double confidence;
        private String description;
        private Map<String, Object> changeMetrics;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextualAnalysis {
        private List<ContextualAnomaly> contextualAnomalies;
        private Map<String, Object> contextualFeatures;
        private List<String> contextualRules;
        private Double contextualScore;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextualAnomaly {
        private String anomalyType;
        private Map<String, Object> context;
        private Double anomalyScore;
        private String description;
        private List<String> relatedContexts;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectiveAnalysis {
        private List<CollectiveAnomaly> collectiveAnomalies;
        private List<AnomalousGroup> anomalousGroups;
        private Double collectiveScore;
        private Map<String, Object> groupingCriteria;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectiveAnomaly {
        private String groupId;
        private List<String> members;
        private String anomalyPattern;
        private Double groupScore;
        private String description;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalousGroup {
        private String groupId;
        private String groupType;
        private Integer memberCount;
        private Double cohesionScore;
        private Map<String, Object> groupCharacteristics;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyAssessment {
        private Double overallAnomalyScore;
        private String severityLevel;
        private String riskLevel;
        private Map<String, Double> componentScores;
        private List<String> primaryConcerns;
        private Double confidence;
        private boolean requiresInvestigation;
        private Map<String, Object> assessmentDetails;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyRecommendation {
        private String recommendationType;
        private String priority;
        private String action;
        private String reasoning;
        private Map<String, Object> parameters;
        private Double expectedImpact;
        private boolean automated;
        private LocalDateTime validUntil;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyDecision {
        private String decision;
        private String reasoning;
        private Double confidence;
        private List<String> requiredActions;
        private Map<String, Object> decisionParameters;
        private boolean requiresHumanReview;
        private String escalationLevel;
    }
    
    private void initializeBackgroundTasks() {
        scheduledExecutorService.scheduleWithFixedDelay(
            this::updateAnomalyModels,
            0, 4, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::updateBaselines,
            0, 2, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::cleanupOldAnomalies,
            1, 24, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::recalibrateThresholds,
            0, 12, TimeUnit.HOURS
        );
    }
    
    @KafkaListener(
        topics = TOPIC,
        groupId = "risk-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(
        ConsumerRecord<String, String> record,
        Acknowledgment acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp
    ) {
        long startTime = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();
        
        try {
            log.info("Processing anomaly detection request: {} with correlation ID: {}", 
                record.key(), correlationId);
            
            AnomalyDetectionRequest request = deserializeMessage(record.value());
            validateRequest(request);
            
            CompletableFuture<AnomalyDetectionResult> detectionFuture = CompletableFuture
                .supplyAsync(() -> executeWithResilience(() -> 
                    performAnomalyDetection(request, correlationId)), executorService)
                .orTimeout(PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            AnomalyDetectionResult result = detectionFuture.join();
            
            if (result.getAssessment().getOverallAnomalyScore() >= CRITICAL_ANOMALY_THRESHOLD) {
                handleCriticalAnomaly(request, result);
            } else if (result.getAssessment().getOverallAnomalyScore() >= HIGH_ANOMALY_THRESHOLD) {
                handleHighAnomaly(request, result);
            } else if (result.getAssessment().getOverallAnomalyScore() >= MEDIUM_ANOMALY_THRESHOLD) {
                handleMediumAnomaly(request, result);
            } else if (result.getAssessment().getOverallAnomalyScore() >= LOW_ANOMALY_THRESHOLD) {
                handleLowAnomaly(request, result);
            }
            
            persistAnomalyResult(request, result);
            publishAnomalyEvents(result);
            updateMetrics(result, System.currentTimeMillis() - startTime);
            
            acknowledgment.acknowledge();
            
        } catch (TimeoutException e) {
            log.error("Timeout processing anomaly detection for key: {}", record.key(), e);
            handleProcessingTimeout(record, acknowledgment);
        } catch (Exception e) {
            log.error("Error processing anomaly detection for key: {}", record.key(), e);
            handleProcessingError(record, acknowledgment, e);
        }
    }
    
    private AnomalyDetectionRequest deserializeMessage(String message) {
        try {
            return objectMapper.readValue(message, AnomalyDetectionRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize anomaly detection request", e);
        }
    }
    
    private void validateRequest(AnomalyDetectionRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getEntityId() == null || request.getEntityId().isEmpty()) {
            errors.add("Entity ID is required");
        }
        if (request.getDataPoint() == null) {
            errors.add("Data point is required");
        }
        if (request.getTimestamp() == null) {
            errors.add("Timestamp is required");
        }
        
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", errors));
        }
    }
    
    private <T> T executeWithResilience(Supplier<T> supplier) {
        return Retry.decorateSupplier(retry,
            CircuitBreaker.decorateSupplier(circuitBreaker, supplier)).get();
    }
    
    private AnomalyDetectionResult performAnomalyDetection(
        AnomalyDetectionRequest request,
        String correlationId
    ) {
        AnomalyDetectionResult result = new AnomalyDetectionResult();
        result.setAnalysisId(UUID.randomUUID().toString());
        result.setRequestId(request.getRequestId());
        result.setEntityId(request.getEntityId());
        result.setAnomalies(new ArrayList<>());
        result.setStatisticalTests(new ArrayList<>());
        result.setMlDetections(new ArrayList<>());
        result.setRecommendations(new ArrayList<>());
        result.setAnalyzedAt(LocalDateTime.now());
        result.setModelVersion(getCurrentModelVersion());
        
        List<CompletableFuture<Void>> detectionTasks = Arrays.asList(
            CompletableFuture.runAsync(() -> 
                performStatisticalAnomalyDetection(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performMLAnomalyDetection(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performTimeSeriesAnomalyDetection(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performContextualAnomalyDetection(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performCollectiveAnomalyDetection(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performOutlierDetection(request, result), executorService)
        );

        try {
            CompletableFuture.allOf(detectionTasks.toArray(new CompletableFuture[0]))
                .get(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Anomaly detection timed out after 20 seconds for transaction: {}", request.getTransactionId(), e);
            detectionTasks.forEach(task -> task.cancel(true));
        } catch (Exception e) {
            log.error("Anomaly detection failed for transaction: {}", request.getTransactionId(), e);
        }

        assessOverallAnomaly(result);
        makeAnomalyDecision(result);
        generateRecommendations(result);
        updateBaselinesIfNeeded(request, result);
        
        long processingTime = System.currentTimeMillis() - 
            result.getAnalyzedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        result.setProcessingTimeMs(processingTime);
        
        return result;
    }
    
    private void performStatisticalAnomalyDetection(
        AnomalyDetectionRequest request,
        AnomalyDetectionResult result
    ) {
        DataPoint currentPoint = request.getDataPoint();
        List<DataPoint> historicalData = request.getHistoricalData();
        
        if (historicalData == null || historicalData.size() < 10) {
            return;
        }
        
        List<Double> values = extractNumericValues(historicalData, currentPoint.getMetricName());
        if (values.isEmpty()) return;
        
        double currentValue = extractNumericValue(currentPoint.getValue());
        
        StatisticalTest zScoreTest = performZScoreTest(currentValue, values);
        result.getStatisticalTests().add(zScoreTest);
        
        if (zScoreTest.isSignificantResult()) {
            DetectedAnomaly anomaly = new DetectedAnomaly();
            anomaly.setAnomalyId(UUID.randomUUID().toString());
            anomaly.setAnomalyType("STATISTICAL_OUTLIER");
            anomaly.setMetricName(currentPoint.getMetricName());
            anomaly.setExpectedValue(calculateMean(values));
            anomaly.setObservedValue(currentValue);
            anomaly.setAnomalyScore(Math.abs(zScoreTest.getTestStatistic()) / 10.0);
            anomaly.setConfidence(1.0 - zScoreTest.getPValue());
            anomaly.setSeverity(determineStatisticalSeverity(Math.abs(zScoreTest.getTestStatistic())));
            anomaly.setDescription("Statistical outlier detected using Z-score test");
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setDetectionMethod("Z_SCORE");
            anomaly.setEvidence(Map.of(
                "zScore", zScoreTest.getTestStatistic(),
                "pValue", zScoreTest.getPValue(),
                "threshold", DETECTION_THRESHOLDS.get("Z_SCORE")
            ));
            result.getAnomalies().add(anomaly);
        }
        
        StatisticalTest iqrTest = performIQRTest(currentValue, values);
        result.getStatisticalTests().add(iqrTest);
        
        if (iqrTest.isSignificantResult()) {
            DetectedAnomaly anomaly = new DetectedAnomaly();
            anomaly.setAnomalyId(UUID.randomUUID().toString());
            anomaly.setAnomalyType("IQR_OUTLIER");
            anomaly.setMetricName(currentPoint.getMetricName());
            anomaly.setExpectedValue(calculateMedian(values));
            anomaly.setObservedValue(currentValue);
            anomaly.setAnomalyScore(0.7);
            anomaly.setConfidence(0.8);
            anomaly.setSeverity("MEDIUM");
            anomaly.setDescription("Outlier detected using IQR method");
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setDetectionMethod("IQR");
            result.getAnomalies().add(anomaly);
        }
        
        StatisticalTest grubbsTest = performGrubbsTest(currentValue, values);
        result.getStatisticalTests().add(grubbsTest);
        
        if (grubbsTest.isSignificantResult()) {
            DetectedAnomaly anomaly = new DetectedAnomaly();
            anomaly.setAnomalyId(UUID.randomUUID().toString());
            anomaly.setAnomalyType("GRUBBS_OUTLIER");
            anomaly.setMetricName(currentPoint.getMetricName());
            anomaly.setObservedValue(currentValue);
            anomaly.setAnomalyScore(0.8);
            anomaly.setConfidence(1.0 - grubbsTest.getPValue());
            anomaly.setSeverity("HIGH");
            anomaly.setDescription("Outlier detected using Grubbs test");
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setDetectionMethod("GRUBBS");
            result.getAnomalies().add(anomaly);
        }
    }
    
    private void performMLAnomalyDetection(
        AnomalyDetectionRequest request,
        AnomalyDetectionResult result
    ) {
        DataPoint currentPoint = request.getDataPoint();
        
        try {
            Map<String, Object> features = extractMLFeatures(request);
            
            MLDetection isolationForest = performIsolationForest(features);
            result.getMlDetections().add(isolationForest);
            
            if (isolationForest.isAnomaly()) {
                DetectedAnomaly anomaly = new DetectedAnomaly();
                anomaly.setAnomalyId(UUID.randomUUID().toString());
                anomaly.setAnomalyType("ML_ISOLATION_FOREST");
                anomaly.setMetricName(currentPoint.getMetricName());
                anomaly.setObservedValue(currentPoint.getValue());
                anomaly.setAnomalyScore(isolationForest.getAnomalyScore());
                anomaly.setConfidence(0.85);
                anomaly.setSeverity(isolationForest.getAnomalyScore() > 0.8 ? "HIGH" : "MEDIUM");
                anomaly.setDescription("Anomaly detected using Isolation Forest");
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setDetectionMethod("ISOLATION_FOREST");
                anomaly.setEvidence(Map.of(
                    "anomalyScore", isolationForest.getAnomalyScore(),
                    "featureImportance", isolationForest.getFeatureImportance()
                ));
                result.getAnomalies().add(anomaly);
            }
            
            MLDetection oneClassSVM = performOneClassSVM(features);
            result.getMlDetections().add(oneClassSVM);
            
            if (oneClassSVM.isAnomaly()) {
                DetectedAnomaly anomaly = new DetectedAnomaly();
                anomaly.setAnomalyId(UUID.randomUUID().toString());
                anomaly.setAnomalyType("ML_ONE_CLASS_SVM");
                anomaly.setMetricName(currentPoint.getMetricName());
                anomaly.setObservedValue(currentPoint.getValue());
                anomaly.setAnomalyScore(oneClassSVM.getAnomalyScore());
                anomaly.setConfidence(0.8);
                anomaly.setSeverity("MEDIUM");
                anomaly.setDescription("Anomaly detected using One-Class SVM");
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setDetectionMethod("ONE_CLASS_SVM");
                result.getAnomalies().add(anomaly);
            }
            
            MLDetection autoencoderDetection = performAutoencoderDetection(features);
            result.getMlDetections().add(autoencoderDetection);
            
            if (autoencoderDetection.isAnomaly()) {
                DetectedAnomaly anomaly = new DetectedAnomaly();
                anomaly.setAnomalyId(UUID.randomUUID().toString());
                anomaly.setAnomalyType("ML_AUTOENCODER");
                anomaly.setMetricName(currentPoint.getMetricName());
                anomaly.setObservedValue(currentPoint.getValue());
                anomaly.setAnomalyScore(autoencoderDetection.getAnomalyScore());
                anomaly.setConfidence(0.9);
                anomaly.setSeverity(autoencoderDetection.getAnomalyScore() > 0.7 ? "HIGH" : "MEDIUM");
                anomaly.setDescription("Anomaly detected using Autoencoder");
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setDetectionMethod("AUTOENCODER");
                result.getAnomalies().add(anomaly);
            }
            
        } catch (Exception e) {
            log.error("ML anomaly detection failed", e);
        }
    }
    
    private void performTimeSeriesAnomalyDetection(
        AnomalyDetectionRequest request,
        AnomalyDetectionResult result
    ) {
        if (request.getHistoricalData() == null || request.getHistoricalData().size() < 20) {
            return;
        }
        
        TimeSeriesAnalysis analysis = timeSeriesAnomalyService
            .analyzeTimeSeries(request.getHistoricalData(), request.getDataPoint());
        
        result.setTimeSeriesAnalysis(analysis);
        
        for (TimeSeriesAnomaly tsAnomaly : analysis.getDetectedAnomalies()) {
            DetectedAnomaly anomaly = new DetectedAnomaly();
            anomaly.setAnomalyId(UUID.randomUUID().toString());
            anomaly.setAnomalyType("TIME_SERIES_" + tsAnomaly.getAnomalyType());
            anomaly.setMetricName(request.getDataPoint().getMetricName());
            anomaly.setExpectedValue(tsAnomaly.getExpectedValue());
            anomaly.setObservedValue(tsAnomaly.getValue());
            anomaly.setAnomalyScore(tsAnomaly.getAnomalyScore());
            anomaly.setConfidence(0.8);
            anomaly.setSeverity(tsAnomaly.getAnomalyScore() > 0.8 ? "HIGH" : "MEDIUM");
            anomaly.setDescription(tsAnomaly.getDescription());
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setDetectionMethod("TIME_SERIES");
            result.getAnomalies().add(anomaly);
        }
        
        for (ChangePoint changePoint : analysis.getChangePoints()) {
            if (changePoint.getConfidence() > 0.7) {
                DetectedAnomaly anomaly = new DetectedAnomaly();
                anomaly.setAnomalyId(UUID.randomUUID().toString());
                anomaly.setAnomalyType("CHANGE_POINT");
                anomaly.setMetricName(request.getDataPoint().getMetricName());
                anomaly.setAnomalyScore(changePoint.getConfidence());
                anomaly.setConfidence(changePoint.getConfidence());
                anomaly.setSeverity("MEDIUM");
                anomaly.setDescription("Change point detected: " + changePoint.getDescription());
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setDetectionMethod("CHANGE_POINT_DETECTION");
                anomaly.setEvidence(changePoint.getChangeMetrics());
                result.getAnomalies().add(anomaly);
            }
        }
    }
    
    private void performContextualAnomalyDetection(
        AnomalyDetectionRequest request,
        AnomalyDetectionResult result
    ) {
        DataPoint currentPoint = request.getDataPoint();
        
        ContextualAnalysis analysis = contextualAnomalyService
            .analyzeContextualAnomalies(currentPoint, request.getContext());
        
        result.setContextualAnalysis(analysis);
        
        for (ContextualAnomaly contextAnomaly : analysis.getContextualAnomalies()) {
            DetectedAnomaly anomaly = new DetectedAnomaly();
            anomaly.setAnomalyId(UUID.randomUUID().toString());
            anomaly.setAnomalyType("CONTEXTUAL_" + contextAnomaly.getAnomalyType());
            anomaly.setMetricName(currentPoint.getMetricName());
            anomaly.setObservedValue(currentPoint.getValue());
            anomaly.setAnomalyScore(contextAnomaly.getAnomalyScore());
            anomaly.setConfidence(0.75);
            anomaly.setSeverity(contextAnomaly.getAnomalyScore() > 0.7 ? "HIGH" : "MEDIUM");
            anomaly.setDescription(contextAnomaly.getDescription());
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setDetectionMethod("CONTEXTUAL");
            anomaly.setEvidence(contextAnomaly.getContext());
            anomaly.setContributingFactors(contextAnomaly.getRelatedContexts());
            result.getAnomalies().add(anomaly);
        }
    }
    
    private void performCollectiveAnomalyDetection(
        AnomalyDetectionRequest request,
        AnomalyDetectionResult result
    ) {
        if (request.getHistoricalData() == null || request.getHistoricalData().size() < 10) {
            return;
        }
        
        List<DataPoint> allPoints = new ArrayList<>(request.getHistoricalData());
        allPoints.add(request.getDataPoint());
        
        CollectiveAnalysis analysis = collectiveAnomalyService
            .analyzeCollectiveAnomalies(allPoints);
        
        result.setCollectiveAnalysis(analysis);
        
        for (CollectiveAnomaly collectiveAnomaly : analysis.getCollectiveAnomalies()) {
            DetectedAnomaly anomaly = new DetectedAnomaly();
            anomaly.setAnomalyId(UUID.randomUUID().toString());
            anomaly.setAnomalyType("COLLECTIVE_" + collectiveAnomaly.getAnomalyPattern());
            anomaly.setMetricName(request.getDataPoint().getMetricName());
            anomaly.setAnomalyScore(collectiveAnomaly.getGroupScore());
            anomaly.setConfidence(0.8);
            anomaly.setSeverity(collectiveAnomaly.getGroupScore() > 0.8 ? "HIGH" : "MEDIUM");
            anomaly.setDescription(collectiveAnomaly.getDescription());
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setDetectionMethod("COLLECTIVE");
            anomaly.setEvidence(Map.of(
                "groupId", collectiveAnomaly.getGroupId(),
                "memberCount", collectiveAnomaly.getMembers().size()
            ));
            result.getAnomalies().add(anomaly);
        }
    }
    
    private void performOutlierDetection(
        AnomalyDetectionRequest request,
        AnomalyDetectionResult result
    ) {
        if (request.getHistoricalData() == null || request.getHistoricalData().size() < 5) {
            return;
        }
        
        List<DataPoint> allPoints = new ArrayList<>(request.getHistoricalData());
        allPoints.add(request.getDataPoint());
        
        List<DataPoint> outliers = outlierDetectionService.detectOutliers(allPoints);
        
        for (DataPoint outlier : outliers) {
            if (outlier.getDataPointId().equals(request.getDataPoint().getDataPointId())) {
                DetectedAnomaly anomaly = new DetectedAnomaly();
                anomaly.setAnomalyId(UUID.randomUUID().toString());
                anomaly.setAnomalyType("OUTLIER");
                anomaly.setMetricName(outlier.getMetricName());
                anomaly.setObservedValue(outlier.getValue());
                anomaly.setAnomalyScore(0.7);
                anomaly.setConfidence(0.8);
                anomaly.setSeverity("MEDIUM");
                anomaly.setDescription("Data point identified as outlier");
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setDetectionMethod("OUTLIER_DETECTION");
                result.getAnomalies().add(anomaly);
            }
        }
    }
    
    private void assessOverallAnomaly(AnomalyDetectionResult result) {
        AnomalyAssessment assessment = new AnomalyAssessment();
        assessment.setComponentScores(new HashMap<>());
        assessment.setPrimaryConcerns(new ArrayList<>());
        assessment.setAssessmentDetails(new HashMap<>());
        
        double statisticalScore = result.getAnomalies().stream()
            .filter(a -> a.getDetectionMethod().startsWith("STATISTICAL") || 
                        a.getDetectionMethod().equals("Z_SCORE") ||
                        a.getDetectionMethod().equals("IQR") ||
                        a.getDetectionMethod().equals("GRUBBS"))
            .mapToDouble(DetectedAnomaly::getAnomalyScore)
            .max()
            .orElse(0.0);
        
        double mlScore = result.getMlDetections().stream()
            .filter(MLDetection::isAnomaly)
            .mapToDouble(MLDetection::getAnomalyScore)
            .max()
            .orElse(0.0);
        
        double timeSeriesScore = result.getTimeSeriesAnalysis() != null ?
            result.getTimeSeriesAnalysis().getDetectedAnomalies().stream()
                .mapToDouble(TimeSeriesAnomaly::getAnomalyScore)
                .max()
                .orElse(0.0) : 0.0;
        
        double contextualScore = result.getContextualAnalysis() != null ?
            result.getContextualAnalysis().getContextualScore() : 0.0;
        
        double collectiveScore = result.getCollectiveAnalysis() != null ?
            result.getCollectiveAnalysis().getCollectiveScore() : 0.0;
        
        assessment.getComponentScores().put("STATISTICAL", statisticalScore);
        assessment.getComponentScores().put("ML_BASED", mlScore);
        assessment.getComponentScores().put("TIME_SERIES", timeSeriesScore);
        assessment.getComponentScores().put("CONTEXTUAL", contextualScore);
        assessment.getComponentScores().put("COLLECTIVE", collectiveScore);
        
        double overallScore = 
            statisticalScore * ANOMALY_WEIGHTS.get("STATISTICAL") +
            mlScore * ANOMALY_WEIGHTS.get("ML_BASED") +
            timeSeriesScore * ANOMALY_WEIGHTS.get("TIME_SERIES") +
            contextualScore * ANOMALY_WEIGHTS.get("CONTEXTUAL") +
            collectiveScore * ANOMALY_WEIGHTS.get("COLLECTIVE");
        
        assessment.setOverallAnomalyScore(overallScore);
        assessment.setSeverityLevel(determineSeverityLevel(overallScore));
        assessment.setRiskLevel(determineRiskLevel(overallScore));
        assessment.setConfidence(calculateAssessmentConfidence(result));
        assessment.setRequiresInvestigation(overallScore >= MEDIUM_ANOMALY_THRESHOLD);
        
        identifyPrimaryConcerns(assessment, result);
        
        result.setAssessment(assessment);
    }
    
    private void makeAnomalyDecision(AnomalyDetectionResult result) {
        AnomalyDecision decision = new AnomalyDecision();
        decision.setRequiredActions(new ArrayList<>());
        decision.setDecisionParameters(new HashMap<>());
        
        double score = result.getAssessment().getOverallAnomalyScore();
        boolean hasCriticalAnomalies = result.getAnomalies().stream()
            .anyMatch(a -> "CRITICAL".equals(a.getSeverity()));
        
        if (hasCriticalAnomalies || score >= CRITICAL_ANOMALY_THRESHOLD) {
            decision.setDecision("IMMEDIATE_ACTION");
            decision.setReasoning("Critical anomaly detected requiring immediate attention");
            decision.setConfidence(0.95);
            decision.getRequiredActions().add("ALERT_OPERATIONS");
            decision.getRequiredActions().add("TRIGGER_INVESTIGATION");
            decision.getRequiredActions().add("APPLY_SAFEGUARDS");
            decision.setRequiresHumanReview(true);
            decision.setEscalationLevel("URGENT");
        } else if (score >= HIGH_ANOMALY_THRESHOLD) {
            decision.setDecision("INVESTIGATE");
            decision.setReasoning("High anomaly score requires investigation");
            decision.setConfidence(0.85);
            decision.getRequiredActions().add("DETAILED_ANALYSIS");
            decision.getRequiredActions().add("ENHANCED_MONITORING");
            decision.getRequiredActions().add("NOTIFY_STAKEHOLDERS");
            decision.setRequiresHumanReview(true);
            decision.setEscalationLevel("HIGH");
        } else if (score >= MEDIUM_ANOMALY_THRESHOLD) {
            decision.setDecision("MONITOR");
            decision.setReasoning("Medium anomaly detected, monitoring required");
            decision.setConfidence(0.75);
            decision.getRequiredActions().add("CONTINUOUS_MONITORING");
            decision.getRequiredActions().add("TREND_ANALYSIS");
            decision.setRequiresHumanReview(false);
            decision.setEscalationLevel("MEDIUM");
        } else if (score >= LOW_ANOMALY_THRESHOLD) {
            decision.setDecision("LOG_AND_TRACK");
            decision.setReasoning("Low anomaly detected, logging for trend analysis");
            decision.setConfidence(0.80);
            decision.getRequiredActions().add("LOG_ANOMALY");
            decision.getRequiredActions().add("UPDATE_BASELINES");
            decision.setRequiresHumanReview(false);
            decision.setEscalationLevel("LOW");
        } else {
            decision.setDecision("NORMAL");
            decision.setReasoning("No significant anomaly detected");
            decision.setConfidence(0.90);
            decision.getRequiredActions().add("ROUTINE_PROCESSING");
            decision.setRequiresHumanReview(false);
            decision.setEscalationLevel("NONE");
        }
        
        result.setDecision(decision);
    }
    
    private void generateRecommendations(AnomalyDetectionResult result) {
        result.setRecommendations(new ArrayList<>());
        
        if (result.getAssessment().getOverallAnomalyScore() >= HIGH_ANOMALY_THRESHOLD) {
            AnomalyRecommendation rec = new AnomalyRecommendation();
            rec.setRecommendationType("INCIDENT_RESPONSE");
            rec.setPriority("HIGH");
            rec.setAction("Initiate incident response protocol");
            rec.setReasoning("High anomaly score indicates potential system issue");
            rec.setParameters(Map.of(
                "responseType", "TECHNICAL_INVESTIGATION",
                "timeframe", "IMMEDIATE"
            ));
            rec.setExpectedImpact(0.8);
            rec.setAutomated(false);
            rec.setValidUntil(LocalDateTime.now().plusHours(6));
            result.getRecommendations().add(rec);
        }
        
        if (result.getAssessment().getComponentScores().get("TIME_SERIES") > 0.7) {
            AnomalyRecommendation rec = new AnomalyRecommendation();
            rec.setRecommendationType("MODEL_RETRAINING");
            rec.setPriority("MEDIUM");
            rec.setAction("Retrain time series models");
            rec.setReasoning("Time series anomalies suggest model drift");
            rec.setParameters(Map.of(
                "modelType", "TIME_SERIES",
                "trainingData", "RECENT_30_DAYS"
            ));
            rec.setExpectedImpact(0.6);
            rec.setAutomated(true);
            rec.setValidUntil(LocalDateTime.now().plusDays(7));
            result.getRecommendations().add(rec);
        }
        
        if (result.getAnomalies().stream().anyMatch(a -> "CONTEXTUAL".equals(a.getDetectionMethod()))) {
            AnomalyRecommendation rec = new AnomalyRecommendation();
            rec.setRecommendationType("CONTEXT_VALIDATION");
            rec.setPriority("MEDIUM");
            rec.setAction("Validate contextual assumptions");
            rec.setReasoning("Contextual anomalies suggest environmental changes");
            rec.setParameters(Map.of(
                "validationType", "BUSINESS_RULES",
                "scope", "DOMAIN_SPECIFIC"
            ));
            rec.setExpectedImpact(0.5);
            rec.setAutomated(false);
            rec.setValidUntil(LocalDateTime.now().plusDays(3));
            result.getRecommendations().add(rec);
        }
        
        if (result.getAssessment().getRequiresInvestigation()) {
            AnomalyRecommendation rec = new AnomalyRecommendation();
            rec.setRecommendationType("BASELINE_UPDATE");
            rec.setPriority("LOW");
            rec.setAction("Update baseline models");
            rec.setReasoning("Regular baseline updates improve detection accuracy");
            rec.setParameters(Map.of(
                "updateType", "INCREMENTAL",
                "frequency", "WEEKLY"
            ));
            rec.setExpectedImpact(0.3);
            rec.setAutomated(true);
            rec.setValidUntil(LocalDateTime.now().plusDays(30));
            result.getRecommendations().add(rec);
        }
    }
    
    private void updateBaselinesIfNeeded(AnomalyDetectionRequest request, AnomalyDetectionResult result) {
        if (result.getAssessment().getOverallAnomalyScore() < LOW_ANOMALY_THRESHOLD) {
            baselineService.updateBaseline(
                request.getEntityId(),
                request.getDataPoint().getMetricName(),
                request.getDataPoint()
            );
        }
    }
    
    private List<Double> extractNumericValues(List<DataPoint> dataPoints, String metricName) {
        return dataPoints.stream()
            .filter(dp -> metricName.equals(dp.getMetricName()))
            .map(dp -> extractNumericValue(dp.getValue()))
            .filter(v -> !Double.isNaN(v) && !Double.isInfinite(v))
            .collect(Collectors.toList());
    }
    
    private double extractNumericValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
    
    private StatisticalTest performZScoreTest(double value, List<Double> baseline) {
        double mean = calculateMean(baseline);
        double stdDev = calculateStandardDeviation(baseline, mean);
        double zScore = stdDev == 0 ? 0 : (value - mean) / stdDev;
        double pValue = 2 * (1 - normalCDF(Math.abs(zScore)));
        
        StatisticalTest test = new StatisticalTest();
        test.setTestName("Z-Score Test");
        test.setTestType("OUTLIER_DETECTION");
        test.setTestStatistic(zScore);
        test.setPValue(pValue);
        test.setCriticalValue(DETECTION_THRESHOLDS.get("Z_SCORE"));
        test.setSignificantResult(Math.abs(zScore) > test.getCriticalValue());
        test.setInterpretation(Math.abs(zScore) > test.getCriticalValue() ? 
            "Significant outlier detected" : "No significant outlier");
        test.setTestParameters(Map.of(
            "mean", mean,
            "stdDev", stdDev,
            "threshold", test.getCriticalValue()
        ));
        
        return test;
    }
    
    private StatisticalTest performIQRTest(double value, List<Double> baseline) {
        Collections.sort(baseline);
        double q1 = calculatePercentile(baseline, 25);
        double q3 = calculatePercentile(baseline, 75);
        double iqr = q3 - q1;
        double lowerBound = q1 - 1.5 * iqr;
        double upperBound = q3 + 1.5 * iqr;
        
        StatisticalTest test = new StatisticalTest();
        test.setTestName("IQR Test");
        test.setTestType("OUTLIER_DETECTION");
        test.setTestStatistic(Math.min(value - lowerBound, upperBound - value));
        test.setSignificantResult(value < lowerBound || value > upperBound);
        test.setInterpretation(test.isSignificantResult() ? 
            "Outlier detected using IQR method" : "No outlier detected");
        test.setTestParameters(Map.of(
            "q1", q1,
            "q3", q3,
            "iqr", iqr,
            "lowerBound", lowerBound,
            "upperBound", upperBound
        ));
        
        return test;
    }
    
    private StatisticalTest performGrubbsTest(double value, List<Double> baseline) {
        double mean = calculateMean(baseline);
        double stdDev = calculateStandardDeviation(baseline, mean);
        double grubbsStatistic = Math.abs(value - mean) / stdDev;
        
        int n = baseline.size();
        double criticalValue = ((n - 1) / Math.sqrt(n)) * 
            Math.sqrt(Math.pow(2.365, 2) / (n - 2 + Math.pow(2.365, 2)));
        
        StatisticalTest test = new StatisticalTest();
        test.setTestName("Grubbs Test");
        test.setTestType("OUTLIER_DETECTION");
        test.setTestStatistic(grubbsStatistic);
        test.setCriticalValue(criticalValue);
        test.setSignificantResult(grubbsStatistic > criticalValue);
        test.setPValue(test.isSignificantResult() ? 0.01 : 0.5);
        test.setInterpretation(test.isSignificantResult() ? 
            "Significant outlier detected" : "No significant outlier");
        test.setTestParameters(Map.of(
            "n", n,
            "mean", mean,
            "stdDev", stdDev
        ));
        
        return test;
    }
    
    private MLDetection performIsolationForest(Map<String, Object> features) {
        try {
            Double anomalyScore = mlModelService.predictAnomaly("ISOLATION_FOREST", features);
            
            MLDetection detection = new MLDetection();
            detection.setModelName("Isolation Forest");
            detection.setAlgorithm("ISOLATION_FOREST");
            detection.setAnomalyScore(anomalyScore);
            detection.setThreshold(DETECTION_THRESHOLDS.get("ISOLATION_FOREST"));
            detection.setAnomaly(anomalyScore > detection.getThreshold());
            detection.setFeatureImportance(Arrays.asList("feature1", "feature2", "feature3"));
            detection.setExplanation("Isolation Forest anomaly detection");
            
            return detection;
        } catch (Exception e) {
            log.error("Isolation Forest detection failed", e);
            return createDefaultMLDetection("ISOLATION_FOREST");
        }
    }
    
    private MLDetection performOneClassSVM(Map<String, Object> features) {
        try {
            Double anomalyScore = mlModelService.predictAnomaly("ONE_CLASS_SVM", features);
            
            MLDetection detection = new MLDetection();
            detection.setModelName("One-Class SVM");
            detection.setAlgorithm("ONE_CLASS_SVM");
            detection.setAnomalyScore(anomalyScore);
            detection.setThreshold(0.0);
            detection.setAnomaly(anomalyScore < 0);
            detection.setExplanation("One-Class SVM anomaly detection");
            
            return detection;
        } catch (Exception e) {
            log.error("One-Class SVM detection failed", e);
            return createDefaultMLDetection("ONE_CLASS_SVM");
        }
    }
    
    private MLDetection performAutoencoderDetection(Map<String, Object> features) {
        try {
            Double reconstructionError = mlModelService.predictAnomaly("AUTOENCODER", features);
            
            MLDetection detection = new MLDetection();
            detection.setModelName("Autoencoder");
            detection.setAlgorithm("AUTOENCODER");
            detection.setAnomalyScore(reconstructionError);
            detection.setThreshold(DETECTION_THRESHOLDS.get("LSTM_ERROR"));
            detection.setAnomaly(reconstructionError > detection.getThreshold());
            detection.setExplanation("Autoencoder reconstruction error based detection");
            
            return detection;
        } catch (Exception e) {
            log.error("Autoencoder detection failed", e);
            return createDefaultMLDetection("AUTOENCODER");
        }
    }
    
    private MLDetection createDefaultMLDetection(String algorithm) {
        MLDetection detection = new MLDetection();
        detection.setModelName(algorithm);
        detection.setAlgorithm(algorithm);
        detection.setAnomalyScore(0.0);
        detection.setThreshold(0.5);
        detection.setAnomaly(false);
        detection.setExplanation("Model unavailable");
        return detection;
    }
    
    private Map<String, Object> extractMLFeatures(AnomalyDetectionRequest request) {
        Map<String, Object> features = new HashMap<>();
        
        DataPoint current = request.getDataPoint();
        features.put("value", extractNumericValue(current.getValue()));
        features.put("hour", current.getTimestamp().getHour());
        features.put("dayOfWeek", current.getTimestamp().getDayOfWeek().getValue());
        features.put("dayOfMonth", current.getTimestamp().getDayOfMonth());
        features.put("month", current.getTimestamp().getMonthValue());
        
        if (current.getAttributes() != null) {
            current.getAttributes().forEach((key, value) -> {
                if (value instanceof Number) {
                    features.put("attr_" + key, ((Number) value).doubleValue());
                }
            });
        }
        
        if (request.getHistoricalData() != null && !request.getHistoricalData().isEmpty()) {
            List<Double> recentValues = request.getHistoricalData().stream()
                .limit(10)
                .map(dp -> extractNumericValue(dp.getValue()))
                .collect(Collectors.toList());
            
            features.put("recent_mean", calculateMean(recentValues));
            features.put("recent_std", calculateStandardDeviation(recentValues, calculateMean(recentValues)));
            features.put("recent_min", Collections.min(recentValues));
            features.put("recent_max", Collections.max(recentValues));
        }
        
        return features;
    }
    
    private double calculateMean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    
    private double calculateMedian(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 0) {
            return (sorted.get(n/2 - 1) + sorted.get(n/2)) / 2.0;
        } else {
            return sorted.get(n/2);
        }
    }
    
    private double calculateStandardDeviation(List<Double> values, double mean) {
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
    }
    
    private double calculatePercentile(List<Double> sortedValues, double percentile) {
        int index = (int) Math.ceil(sortedValues.size() * percentile / 100.0) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }
    
    private double normalCDF(double x) {
        return 0.5 * (1 + erf(x / Math.sqrt(2)));
    }
    
    private double erf(double x) {
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;
        
        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x);
        
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        
        return sign * y;
    }
    
    private String determineStatisticalSeverity(double zScore) {
        if (Math.abs(zScore) >= 4.0) return "CRITICAL";
        if (Math.abs(zScore) >= 3.0) return "HIGH";
        if (Math.abs(zScore) >= 2.0) return "MEDIUM";
        return "LOW";
    }
    
    private String determineSeverityLevel(double score) {
        if (score >= CRITICAL_ANOMALY_THRESHOLD) return "CRITICAL";
        if (score >= HIGH_ANOMALY_THRESHOLD) return "HIGH";
        if (score >= MEDIUM_ANOMALY_THRESHOLD) return "MEDIUM";
        if (score >= LOW_ANOMALY_THRESHOLD) return "LOW";
        return "MINIMAL";
    }
    
    private String determineRiskLevel(double score) {
        if (score >= 0.8) return "CRITICAL";
        if (score >= 0.6) return "HIGH";
        if (score >= 0.4) return "MEDIUM";
        if (score >= 0.2) return "LOW";
        return "MINIMAL";
    }
    
    private double calculateAssessmentConfidence(AnomalyDetectionResult result) {
        int methodCount = 0;
        if (!result.getStatisticalTests().isEmpty()) methodCount++;
        if (!result.getMlDetections().isEmpty()) methodCount++;
        if (result.getTimeSeriesAnalysis() != null) methodCount++;
        if (result.getContextualAnalysis() != null) methodCount++;
        if (result.getCollectiveAnalysis() != null) methodCount++;
        
        double baseConfidence = methodCount * 0.15 + 0.25;
        
        long validatedAnomalies = result.getAnomalies().stream()
            .filter(DetectedAnomaly::isValidated)
            .count();
        
        double validationBonus = validatedAnomalies * 0.1;
        
        return Math.min(1.0, baseConfidence + validationBonus);
    }
    
    private void identifyPrimaryConcerns(AnomalyAssessment assessment, AnomalyDetectionResult result) {
        assessment.getComponentScores().entrySet().stream()
            .filter(entry -> entry.getValue() > 0.5)
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .forEach(assessment.getPrimaryConcerns()::add);
        
        if (assessment.getPrimaryConcerns().isEmpty()) {
            assessment.getPrimaryConcerns().add("NO_SIGNIFICANT_CONCERNS");
        }
    }
    
    private String getCurrentModelVersion() {
        return "v2.8.1";
    }
    
    private void updateAnomalyModels() {
        log.info("Updating anomaly detection models...");
        try {
            mlModelService.retrainModel("ISOLATION_FOREST");
            mlModelService.retrainModel("ONE_CLASS_SVM");
            mlModelService.retrainModel("AUTOENCODER");
        } catch (Exception e) {
            log.error("Failed to update anomaly models", e);
        }
    }
    
    private void updateBaselines() {
        log.info("Updating detection baselines...");
        try {
            baselineService.updateAllBaselines();
        } catch (Exception e) {
            log.error("Failed to update baselines", e);
        }
    }
    
    private void cleanupOldAnomalies() {
        log.info("Cleaning up old anomaly data...");
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
            anomalyRepository.deleteByDetectedAtBefore(cutoff);
        } catch (Exception e) {
            log.error("Failed to cleanup old anomalies", e);
        }
    }
    
    private void recalibrateThresholds() {
        log.info("Recalibrating detection thresholds...");
        try {
            statisticalAnomalyService.recalibrateThresholds();
        } catch (Exception e) {
            log.error("Failed to recalibrate thresholds", e);
        }
    }
    
    private void handleCriticalAnomaly(AnomalyDetectionRequest request, AnomalyDetectionResult result) {
        log.error("CRITICAL ANOMALY DETECTED - Entity: {}, Score: {}, Anomalies: {}", 
            request.getEntityId(), result.getAssessment().getOverallAnomalyScore(), 
            result.getAnomalies().size());
        
        sendCriticalAnomalyAlert(request, result);
    }
    
    private void handleHighAnomaly(AnomalyDetectionRequest request, AnomalyDetectionResult result) {
        log.warn("HIGH ANOMALY - Entity: {}, Score: {}", 
            request.getEntityId(), result.getAssessment().getOverallAnomalyScore());
        
        sendHighAnomalyNotification(request, result);
    }
    
    private void handleMediumAnomaly(AnomalyDetectionRequest request, AnomalyDetectionResult result) {
        log.info("MEDIUM ANOMALY - Entity: {}, Score: {}", 
            request.getEntityId(), result.getAssessment().getOverallAnomalyScore());
    }
    
    private void handleLowAnomaly(AnomalyDetectionRequest request, AnomalyDetectionResult result) {
        log.info("LOW ANOMALY - Entity: {}, Score: {}", 
            request.getEntityId(), result.getAssessment().getOverallAnomalyScore());
    }
    
    private void sendCriticalAnomalyAlert(AnomalyDetectionRequest request, AnomalyDetectionResult result) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("CRITICAL_ANOMALY_ALERT")
            .priority("URGENT")
            .recipient("operations-team")
            .subject("Critical Anomaly Detected")
            .templateData(Map.of(
                "entityId", request.getEntityId(),
                "metricName", request.getDataPoint().getMetricName(),
                "anomalyScore", result.getAssessment().getOverallAnomalyScore(),
                "severityLevel", result.getAssessment().getSeverityLevel(),
                "primaryConcerns", result.getAssessment().getPrimaryConcerns(),
                "detectedAnomalies", result.getAnomalies().stream()
                    .filter(a -> "CRITICAL".equals(a.getSeverity()) || "HIGH".equals(a.getSeverity()))
                    .map(a -> a.getAnomalyType() + ": " + a.getDescription())
                    .collect(Collectors.toList()),
                "decision", result.getDecision().getDecision()
            ))
            .channels(Arrays.asList("EMAIL", "SLACK", "PAGERDUTY"))
            .build();
        
        notificationService.send(template);
    }
    
    private void sendHighAnomalyNotification(AnomalyDetectionRequest request, AnomalyDetectionResult result) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("HIGH_ANOMALY_ALERT")
            .priority("HIGH")
            .recipient("monitoring-team")
            .subject("High Anomaly Detected")
            .templateData(Map.of(
                "entityId", request.getEntityId(),
                "metricName", request.getDataPoint().getMetricName(),
                "anomalyScore", result.getAssessment().getOverallAnomalyScore(),
                "componentScores", result.getAssessment().getComponentScores()
            ))
            .channels(Arrays.asList("EMAIL", "SLACK"))
            .build();
        
        notificationService.send(template);
    }
    
    private void persistAnomalyResult(AnomalyDetectionRequest request, AnomalyDetectionResult result) {
        AnomalyDetection detection = new AnomalyDetection();
        detection.setAnalysisId(result.getAnalysisId());
        detection.setRequestId(request.getRequestId());
        detection.setEntityType(request.getEntityType());
        detection.setEntityId(request.getEntityId());
        detection.setUserId(request.getUserId());
        detection.setSessionId(request.getSessionId());
        detection.setMetricName(request.getDataPoint().getMetricName());
        detection.setOverallAnomalyScore(result.getAssessment().getOverallAnomalyScore());
        detection.setSeverityLevel(result.getAssessment().getSeverityLevel());
        detection.setRiskLevel(result.getAssessment().getRiskLevel());
        detection.setDecision(result.getDecision().getDecision());
        detection.setAnomalies(objectMapper.convertValue(result.getAnomalies(), List.class));
        detection.setStatisticalTests(objectMapper.convertValue(result.getStatisticalTests(), List.class));
        detection.setMlDetections(objectMapper.convertValue(result.getMlDetections(), List.class));
        detection.setRecommendations(objectMapper.convertValue(result.getRecommendations(), List.class));
        detection.setAnalyzedAt(result.getAnalyzedAt());
        detection.setProcessingTimeMs(result.getProcessingTimeMs());
        detection.setModelVersion(result.getModelVersion());
        
        anomalyRepository.save(detection);
        
        for (DetectedAnomaly anomaly : result.getAnomalies()) {
            AnomalyEntity entity = new AnomalyEntity();
            entity.setAnomalyId(anomaly.getAnomalyId());
            entity.setAnalysisId(result.getAnalysisId());
            entity.setAnomalyType(anomaly.getAnomalyType());
            entity.setMetricName(anomaly.getMetricName());
            entity.setExpectedValue(anomaly.getExpectedValue() != null ? 
                anomaly.getExpectedValue().toString() : null);
            entity.setObservedValue(anomaly.getObservedValue() != null ? 
                anomaly.getObservedValue().toString() : null);
            entity.setAnomalyScore(anomaly.getAnomalyScore());
            entity.setConfidence(anomaly.getConfidence());
            entity.setSeverity(anomaly.getSeverity());
            entity.setDescription(anomaly.getDescription());
            entity.setDetectionMethod(anomaly.getDetectionMethod());
            entity.setValidated(anomaly.isValidated());
            entity.setDetectedAt(anomaly.getDetectedAt());
            anomalyRepository.saveAnomalyEntity(entity);
        }
        
        String cacheKey = "anomaly:detection:" + request.getEntityId() + ":" + request.getDataPoint().getMetricName();
        redisCache.set(cacheKey, result, Duration.ofHours(4));
    }
    
    private void publishAnomalyEvents(AnomalyDetectionResult result) {
        kafkaTemplate.send("anomaly-detection-results", result.getEntityId(), result);
        
        if (result.getAssessment().getOverallAnomalyScore() >= HIGH_ANOMALY_THRESHOLD) {
            kafkaTemplate.send("anomaly-alerts", result.getEntityId(), result);
        }
        
        if (result.getDecision().getRequiresHumanReview()) {
            kafkaTemplate.send("anomaly-review-queue", result.getEntityId(), result);
        }
        
        if ("IMMEDIATE_ACTION".equals(result.getDecision().getDecision())) {
            kafkaTemplate.send("critical-anomaly-alerts", result.getEntityId(), result);
        }
    }
    
    private void updateMetrics(AnomalyDetectionResult result, long processingTime) {
        metricsService.recordAnomalyScore(result.getAssessment().getOverallAnomalyScore());
        metricsService.recordAnomalySeverity(result.getAssessment().getSeverityLevel());
        metricsService.recordAnomalyRiskLevel(result.getAssessment().getRiskLevel());
        metricsService.recordAnomalyDecision(result.getDecision().getDecision());
        metricsService.recordDetectedAnomalyCount(result.getAnomalies().size());
        metricsService.recordProcessingTime("anomaly-detection", processingTime);
        
        result.getAssessment().getComponentScores().forEach((component, score) -> 
            metricsService.recordComponentScore("anomaly." + component.toLowerCase(), score)
        );
        
        metricsService.incrementCounter("anomaly.detections.total");
        metricsService.incrementCounter("anomaly.severity." + 
            result.getAssessment().getSeverityLevel().toLowerCase());
        metricsService.incrementCounter("anomaly.risk." + 
            result.getAssessment().getRiskLevel().toLowerCase());
        
        if (result.getAssessment().getRequiresInvestigation()) {
            metricsService.incrementCounter("anomaly.investigations.required");
        }
    }
    
    private void handleProcessingTimeout(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        metricsService.incrementCounter("anomaly.detection.timeouts");
        sendToDLQ(record, "Processing timeout");
        acknowledgment.acknowledge();
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Acknowledgment acknowledgment, Exception error) {
        metricsService.incrementCounter("anomaly.detection.errors");
        log.error("Failed to process anomaly detection: {}", record.key(), error);
        sendToDLQ(record, error.getMessage());
        acknowledgment.acknowledge();
    }
    
    private void sendToDLQ(ConsumerRecord<String, String> record, String reason) {
        try {
            ProducerRecord<String, Object> dlqRecord = new ProducerRecord<>(
                DLQ_TOPIC,
                record.key(),
                Map.of(
                    "originalTopic", TOPIC,
                    "originalMessage", record.value(),
                    "failureReason", reason,
                    "failureTimestamp", Instant.now().toString(),
                    "retryCount", record.headers().lastHeader("retryCount") != null ?
                        Integer.parseInt(new String(record.headers().lastHeader("retryCount").value())) + 1 : 1
                )
            );
            
            kafkaTemplate.send(dlqRecord);
            log.info("Message sent to DLQ: {}", record.key());
        } catch (Exception e) {
            log.error("Failed to send message to DLQ: {}", record.key(), e);
        }
    }
}