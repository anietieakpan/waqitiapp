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
public class BehavioralAnalysisConsumer {

    private static final String TOPIC = "behavioral-analysis";
    private static final String DLQ_TOPIC = "behavioral-analysis.dlq";
    private static final int MAX_RETRIES = 3;
    private static final long PROCESSING_TIMEOUT_MS = 12000;
    private static final double CRITICAL_ANOMALY_THRESHOLD = 0.85;
    private static final double HIGH_ANOMALY_THRESHOLD = 0.70;
    private static final double MEDIUM_ANOMALY_THRESHOLD = 0.50;
    private static final double LOW_ANOMALY_THRESHOLD = 0.30;
    
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BehavioralProfileRepository behavioralProfileRepository;
    private final BehavioralBaselineRepository behavioralBaselineRepository;
    private final BehavioralAnomalyRepository behavioralAnomalyRepository;
    private final SessionAnalysisRepository sessionAnalysisRepository;
    private final BiometricDataRepository biometricDataRepository;
    private final BehavioralPatternService behavioralPatternService;
    private final BiometricAnalysisService biometricAnalysisService;
    private final SessionAnalysisService sessionAnalysisService;
    private final KeystrokeDynamicsService keystrokeDynamicsService;
    private final MouseDynamicsService mouseDynamicsService;
    private final TouchDynamicsService touchDynamicsService;
    private final NavigationAnalysisService navigationAnalysisService;
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
    
    private static final Map<String, Double> BEHAVIORAL_WEIGHTS = new HashMap<>();
    private static final Map<String, Double> ANOMALY_THRESHOLDS = new HashMap<>();
    
    static {
        BEHAVIORAL_WEIGHTS.put("KEYSTROKE_DYNAMICS", 0.25);
        BEHAVIORAL_WEIGHTS.put("MOUSE_DYNAMICS", 0.20);
        BEHAVIORAL_WEIGHTS.put("TOUCH_DYNAMICS", 0.20);
        BEHAVIORAL_WEIGHTS.put("NAVIGATION_PATTERN", 0.15);
        BEHAVIORAL_WEIGHTS.put("TIMING_ANALYSIS", 0.10);
        BEHAVIORAL_WEIGHTS.put("BIOMETRIC_CONSISTENCY", 0.10);
        
        ANOMALY_THRESHOLDS.put("TYPING_SPEED", 0.4);
        ANOMALY_THRESHOLDS.put("DWELL_TIME", 0.3);
        ANOMALY_THRESHOLDS.put("FLIGHT_TIME", 0.3);
        ANOMALY_THRESHOLDS.put("MOUSE_VELOCITY", 0.35);
        ANOMALY_THRESHOLDS.put("CLICK_PRESSURE", 0.25);
        ANOMALY_THRESHOLDS.put("TOUCH_PRESSURE", 0.3);
        ANOMALY_THRESHOLDS.put("SWIPE_VELOCITY", 0.25);
    }
    
    public BehavioralAnalysisConsumer(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            BehavioralProfileRepository behavioralProfileRepository,
            BehavioralBaselineRepository behavioralBaselineRepository,
            BehavioralAnomalyRepository behavioralAnomalyRepository,
            SessionAnalysisRepository sessionAnalysisRepository,
            BiometricDataRepository biometricDataRepository,
            BehavioralPatternService behavioralPatternService,
            BiometricAnalysisService biometricAnalysisService,
            SessionAnalysisService sessionAnalysisService,
            KeystrokeDynamicsService keystrokeDynamicsService,
            MouseDynamicsService mouseDynamicsService,
            TouchDynamicsService touchDynamicsService,
            NavigationAnalysisService navigationAnalysisService,
            MachineLearningModelService mlModelService,
            NotificationService notificationService,
            MetricsService metricsService,
            RedisCache redisCache
    ) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.behavioralProfileRepository = behavioralProfileRepository;
        this.behavioralBaselineRepository = behavioralBaselineRepository;
        this.behavioralAnomalyRepository = behavioralAnomalyRepository;
        this.sessionAnalysisRepository = sessionAnalysisRepository;
        this.biometricDataRepository = biometricDataRepository;
        this.behavioralPatternService = behavioralPatternService;
        this.biometricAnalysisService = biometricAnalysisService;
        this.sessionAnalysisService = sessionAnalysisService;
        this.keystrokeDynamicsService = keystrokeDynamicsService;
        this.mouseDynamicsService = mouseDynamicsService;
        this.touchDynamicsService = touchDynamicsService;
        this.navigationAnalysisService = navigationAnalysisService;
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
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("behavioral-analysis");
        
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(MAX_RETRIES)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
            .build();
            
        this.retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry("behavioral-analysis");
        
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
    public static class BehavioralAnalysisRequest {
        private String requestId;
        private String userId;
        private String sessionId;
        private String deviceId;
        private SessionData sessionData;
        private KeystrokeDynamics keystrokeDynamics;
        private MouseDynamics mouseDynamics;
        private TouchDynamics touchDynamics;
        private NavigationPattern navigationPattern;
        private BiometricData biometricData;
        private Map<String, Object> contextData;
        private LocalDateTime timestamp;
        private boolean realTimeAnalysis;
        private String correlationId;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionData {
        private Long sessionDuration;
        private Integer pageViews;
        private Integer interactions;
        private Double activeTime;
        private Double idleTime;
        private Integer scrollEvents;
        private Integer clickEvents;
        private Integer keyEvents;
        private List<String> visitedPages;
        private Map<String, Long> timeSpentPerPage;
        private String userAgent;
        private String platform;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeystrokeDynamics {
        private List<Long> dwellTimes;
        private List<Long> flightTimes;
        private List<String> keySequences;
        private Double averageTypingSpeed;
        private Double typingRhythm;
        private Integer backspaceCount;
        private Integer deletionsCount;
        private Map<String, Double> keyPairTimings;
        private List<Double> pressureLevels;
        private Boolean copyPasteDetected;
        private String inputMethod;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MouseDynamics {
        private List<Double> velocities;
        private List<Double> accelerations;
        private List<Double> clickPressures;
        private List<Long> clickDurations;
        private List<Point> trajectoryPoints;
        private Double averageVelocity;
        private Double movementSmoothness;
        private Integer rightClicks;
        private Integer doubleClicks;
        private Integer dragEvents;
        private Map<String, Object> clickPatterns;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private Double x;
        private Double y;
        private Long timestamp;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TouchDynamics {
        private List<Double> touchPressures;
        private List<Double> touchSizes;
        private List<Double> swipeVelocities;
        private List<TouchPoint> touchPoints;
        private Integer tapCount;
        private Integer longPressCount;
        private Integer multiTouchCount;
        private Double averagePressure;
        private Map<String, Object> gesturePatterns;
        private Boolean palmRejectionEvents;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TouchPoint {
        private Double x;
        private Double y;
        private Double pressure;
        private Double size;
        private Long timestamp;
        private String gestureType;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NavigationPattern {
        private List<String> navigationPath;
        private Map<String, Long> pageTimings;
        private List<String> searchQueries;
        private Integer backButtonUsage;
        private Integer tabSwitches;
        private Double scrollDepth;
        private String navigationStyle;
        private Map<String, Integer> elementInteractions;
        private Boolean suspiciousNavigation;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BiometricData {
        private Map<String, Object> voiceprint;
        private Map<String, Object> faceprint;
        private Map<String, Object> gaitPattern;
        private Map<String, Object> heartRateVariability;
        private String biometricQuality;
        private Double confidenceScore;
        private List<String> biometricFlags;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralAnalysisResult {
        private String analysisId;
        private String requestId;
        private String userId;
        private String sessionId;
        private BehavioralScore behavioralScore;
        private List<BehavioralAnomaly> detectedAnomalies;
        private List<BehavioralPattern> identifiedPatterns;
        private BiometricAnalysisResult biometricAnalysis;
        private BehavioralBaseline userBaseline;
        private BehavioralDecision decision;
        private List<BehavioralRecommendation> recommendations;
        private String modelVersion;
        private Long processingTimeMs;
        private LocalDateTime analyzedAt;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralScore {
        private Double overallScore;
        private Double authenticityScore;
        private Double consistencyScore;
        private Map<String, Double> componentScores;
        private String riskLevel;
        private String confidence;
        private List<String> contributingFactors;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralAnomaly {
        private String anomalyType;
        private String category;
        private Double severity;
        private String description;
        private Double deviation;
        private Double zscore;
        private String baseline;
        private String observed;
        private LocalDateTime detectedAt;
        private Map<String, Object> evidence;
        private Boolean confirmed;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralPattern {
        private String patternType;
        private String category;
        private String description;
        private Double confidence;
        private Map<String, Object> characteristics;
        private LocalDateTime firstObserved;
        private Integer occurrenceCount;
        private Boolean isNormal;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BiometricAnalysisResult {
        private Boolean biometricMatch;
        private Double matchConfidence;
        private Map<String, Double> modalityScores;
        private List<String> inconsistencies;
        private String qualityAssessment;
        private Boolean spoofingDetected;
        private String analysisMethod;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralBaseline {
        private String userId;
        private Map<String, Object> keystrokeProfile;
        private Map<String, Object> mouseProfile;
        private Map<String, Object> touchProfile;
        private Map<String, Object> navigationProfile;
        private Map<String, Object> biometricProfile;
        private LocalDateTime lastUpdated;
        private Integer dataPointsCount;
        private Double profileConfidence;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralDecision {
        private String decision;
        private String reasoning;
        private Double confidence;
        private List<String> requiredActions;
        private Map<String, Object> parameters;
        private Boolean requiresHumanReview;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralRecommendation {
        private String type;
        private String priority;
        private String description;
        private Map<String, Object> configuration;
        private Double expectedImpact;
        private Boolean automated;
    }
    
    private void initializeBackgroundTasks() {
        scheduledExecutorService.scheduleWithFixedDelay(
            this::updateBehavioralBaselines,
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::cleanupOldAnalyses,
            1, 24, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::retrainBehavioralModels,
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
            log.info("Processing behavioral analysis request: {} with correlation ID: {}", 
                record.key(), correlationId);
            
            BehavioralAnalysisRequest request = deserializeMessage(record.value());
            validateRequest(request);
            
            CompletableFuture<BehavioralAnalysisResult> analysisFuture = CompletableFuture
                .supplyAsync(() -> executeWithResilience(() -> 
                    performBehavioralAnalysis(request, correlationId)), executorService)
                .orTimeout(PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            BehavioralAnalysisResult result = analysisFuture.join();
            
            if (result.getBehavioralScore().getOverallScore() >= CRITICAL_ANOMALY_THRESHOLD) {
                handleCriticalAnomaly(request, result);
            } else if (result.getBehavioralScore().getOverallScore() >= HIGH_ANOMALY_THRESHOLD) {
                handleHighAnomaly(request, result);
            } else if (result.getBehavioralScore().getOverallScore() >= MEDIUM_ANOMALY_THRESHOLD) {
                handleMediumAnomaly(request, result);
            } else if (result.getBehavioralScore().getOverallScore() >= LOW_ANOMALY_THRESHOLD) {
                handleLowAnomaly(request, result);
            }
            
            persistAnalysisResult(request, result);
            publishBehavioralEvents(result);
            updateMetrics(result, System.currentTimeMillis() - startTime);
            
            acknowledgment.acknowledge();
            
        } catch (TimeoutException e) {
            log.error("Timeout processing behavioral analysis for key: {}", record.key(), e);
            handleProcessingTimeout(record, acknowledgment);
        } catch (Exception e) {
            log.error("Error processing behavioral analysis for key: {}", record.key(), e);
            handleProcessingError(record, acknowledgment, e);
        }
    }
    
    private BehavioralAnalysisRequest deserializeMessage(String message) {
        try {
            return objectMapper.readValue(message, BehavioralAnalysisRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize behavioral analysis request", e);
        }
    }
    
    private void validateRequest(BehavioralAnalysisRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            errors.add("User ID is required");
        }
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            errors.add("Session ID is required");
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
    
    private BehavioralAnalysisResult performBehavioralAnalysis(
        BehavioralAnalysisRequest request,
        String correlationId
    ) {
        BehavioralAnalysisResult result = new BehavioralAnalysisResult();
        result.setAnalysisId(UUID.randomUUID().toString());
        result.setRequestId(request.getRequestId());
        result.setUserId(request.getUserId());
        result.setSessionId(request.getSessionId());
        result.setDetectedAnomalies(new ArrayList<>());
        result.setIdentifiedPatterns(new ArrayList<>());
        result.setRecommendations(new ArrayList<>());
        result.setAnalyzedAt(LocalDateTime.now());
        result.setModelVersion(getCurrentModelVersion());
        
        BehavioralBaseline userBaseline = getUserBaseline(request.getUserId());
        result.setUserBaseline(userBaseline);
        
        List<CompletableFuture<Void>> analysisTask = Arrays.asList(
            CompletableFuture.runAsync(() -> 
                analyzeKeystrokeDynamics(request, result, userBaseline), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeMouseDynamics(request, result, userBaseline), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeTouchDynamics(request, result, userBaseline), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeNavigationPattern(request, result, userBaseline), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeSessionBehavior(request, result, userBaseline), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeBiometricData(request, result, userBaseline), executorService),
            CompletableFuture.runAsync(() -> 
                detectBehavioralPatterns(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performMLAnalysis(request, result), executorService)
        );

        try {
            CompletableFuture.allOf(analysisTask.toArray(new CompletableFuture[0]))
                .get(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Behavioral analysis timed out after 20 seconds for session: {}", request.getSessionId(), e);
            analysisTask.forEach(task -> task.cancel(true));
        } catch (Exception e) {
            log.error("Behavioral analysis failed for session: {}", request.getSessionId(), e);
        }

        calculateBehavioralScore(result);
        makeBehavioralDecision(result);
        generateRecommendations(result);
        updateUserBaseline(request, result);
        
        long processingTime = System.currentTimeMillis() - 
            result.getAnalyzedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        result.setProcessingTimeMs(processingTime);
        
        return result;
    }
    
    private BehavioralBaseline getUserBaseline(String userId) {
        String cacheKey = "behavioral:baseline:" + userId;
        BehavioralBaseline cached = redisCache.get(cacheKey, BehavioralBaseline.class);
        if (cached != null) {
            return cached;
        }
        
        return behavioralBaselineRepository.findByUserId(userId)
            .orElse(createDefaultBaseline(userId));
    }
    
    private BehavioralBaseline createDefaultBaseline(String userId) {
        BehavioralBaseline baseline = new BehavioralBaseline();
        baseline.setUserId(userId);
        baseline.setKeystrokeProfile(new HashMap<>());
        baseline.setMouseProfile(new HashMap<>());
        baseline.setTouchProfile(new HashMap<>());
        baseline.setNavigationProfile(new HashMap<>());
        baseline.setBiometricProfile(new HashMap<>());
        baseline.setLastUpdated(LocalDateTime.now());
        baseline.setDataPointsCount(0);
        baseline.setProfileConfidence(0.0);
        return baseline;
    }
    
    private void analyzeKeystrokeDynamics(
        BehavioralAnalysisRequest request,
        BehavioralAnalysisResult result,
        BehavioralBaseline baseline
    ) {
        if (request.getKeystrokeDynamics() == null) return;
        
        KeystrokeDynamics dynamics = request.getKeystrokeDynamics();
        Map<String, Object> keystrokeProfile = baseline.getKeystrokeProfile();
        
        if (dynamics.getDwellTimes() != null && !dynamics.getDwellTimes().isEmpty()) {
            double avgDwellTime = dynamics.getDwellTimes().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
            
            if (keystrokeProfile.containsKey("avgDwellTime")) {
                double baselineDwellTime = (Double) keystrokeProfile.get("avgDwellTime");
                double deviation = Math.abs(avgDwellTime - baselineDwellTime) / baselineDwellTime;
                
                if (deviation > ANOMALY_THRESHOLDS.get("DWELL_TIME")) {
                    BehavioralAnomaly anomaly = new BehavioralAnomaly();
                    anomaly.setAnomalyType("DWELL_TIME_ANOMALY");
                    anomaly.setCategory("KEYSTROKE");
                    anomaly.setSeverity(calculateAnomalySeverity(deviation));
                    anomaly.setDescription("Unusual keystroke dwell time pattern");
                    anomaly.setDeviation(deviation);
                    anomaly.setZscore(calculateZScore(avgDwellTime, keystrokeProfile, "dwellTimeHistory"));
                    anomaly.setBaseline(String.valueOf(baselineDwellTime));
                    anomaly.setObserved(String.valueOf(avgDwellTime));
                    anomaly.setDetectedAt(LocalDateTime.now());
                    anomaly.setEvidence(Map.of(
                        "dwellTimes", dynamics.getDwellTimes(),
                        "deviation", deviation
                    ));
                    result.getDetectedAnomalies().add(anomaly);
                }
            }
        }
        
        if (dynamics.getFlightTimes() != null && !dynamics.getFlightTimes().isEmpty()) {
            double avgFlightTime = dynamics.getFlightTimes().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
            
            if (keystrokeProfile.containsKey("avgFlightTime")) {
                double baselineFlightTime = (Double) keystrokeProfile.get("avgFlightTime");
                double deviation = Math.abs(avgFlightTime - baselineFlightTime) / baselineFlightTime;
                
                if (deviation > ANOMALY_THRESHOLDS.get("FLIGHT_TIME")) {
                    BehavioralAnomaly anomaly = new BehavioralAnomaly();
                    anomaly.setAnomalyType("FLIGHT_TIME_ANOMALY");
                    anomaly.setCategory("KEYSTROKE");
                    anomaly.setSeverity(calculateAnomalySeverity(deviation));
                    anomaly.setDescription("Unusual keystroke flight time pattern");
                    anomaly.setDeviation(deviation);
                    anomaly.setBaseline(String.valueOf(baselineFlightTime));
                    anomaly.setObserved(String.valueOf(avgFlightTime));
                    anomaly.setDetectedAt(LocalDateTime.now());
                    result.getDetectedAnomalies().add(anomaly);
                }
            }
        }
        
        if (dynamics.getAverageTypingSpeed() != null) {
            if (keystrokeProfile.containsKey("avgTypingSpeed")) {
                double baselineSpeed = (Double) keystrokeProfile.get("avgTypingSpeed");
                double deviation = Math.abs(dynamics.getAverageTypingSpeed() - baselineSpeed) / baselineSpeed;
                
                if (deviation > ANOMALY_THRESHOLDS.get("TYPING_SPEED")) {
                    BehavioralAnomaly anomaly = new BehavioralAnomaly();
                    anomaly.setAnomalyType("TYPING_SPEED_ANOMALY");
                    anomaly.setCategory("KEYSTROKE");
                    anomaly.setSeverity(calculateAnomalySeverity(deviation));
                    anomaly.setDescription("Significant typing speed variation");
                    anomaly.setDeviation(deviation);
                    anomaly.setBaseline(String.valueOf(baselineSpeed));
                    anomaly.setObserved(String.valueOf(dynamics.getAverageTypingSpeed()));
                    anomaly.setDetectedAt(LocalDateTime.now());
                    result.getDetectedAnomalies().add(anomaly);
                }
            }
        }
        
        if (Boolean.TRUE.equals(dynamics.getCopyPasteDetected())) {
            BehavioralAnomaly anomaly = new BehavioralAnomaly();
            anomaly.setAnomalyType("COPY_PASTE_DETECTED");
            anomaly.setCategory("KEYSTROKE");
            anomaly.setSeverity(0.6);
            anomaly.setDescription("Copy-paste behavior detected");
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of("copyPaste", true));
            result.getDetectedAnomalies().add(anomaly);
        }
        
        keystrokeDynamicsService.analyzeAdvancedPatterns(dynamics, result);
    }
    
    private void analyzeMouseDynamics(
        BehavioralAnalysisRequest request,
        BehavioralAnalysisResult result,
        BehavioralBaseline baseline
    ) {
        if (request.getMouseDynamics() == null) return;
        
        MouseDynamics dynamics = request.getMouseDynamics();
        Map<String, Object> mouseProfile = baseline.getMouseProfile();
        
        if (dynamics.getAverageVelocity() != null) {
            if (mouseProfile.containsKey("avgVelocity")) {
                double baselineVelocity = (Double) mouseProfile.get("avgVelocity");
                double deviation = Math.abs(dynamics.getAverageVelocity() - baselineVelocity) / baselineVelocity;
                
                if (deviation > ANOMALY_THRESHOLDS.get("MOUSE_VELOCITY")) {
                    BehavioralAnomaly anomaly = new BehavioralAnomaly();
                    anomaly.setAnomalyType("MOUSE_VELOCITY_ANOMALY");
                    anomaly.setCategory("MOUSE");
                    anomaly.setSeverity(calculateAnomalySeverity(deviation));
                    anomaly.setDescription("Unusual mouse movement velocity");
                    anomaly.setDeviation(deviation);
                    anomaly.setBaseline(String.valueOf(baselineVelocity));
                    anomaly.setObserved(String.valueOf(dynamics.getAverageVelocity()));
                    anomaly.setDetectedAt(LocalDateTime.now());
                    result.getDetectedAnomalies().add(anomaly);
                }
            }
        }
        
        if (dynamics.getClickPressures() != null && !dynamics.getClickPressures().isEmpty()) {
            double avgPressure = dynamics.getClickPressures().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            
            if (mouseProfile.containsKey("avgClickPressure")) {
                double baselinePressure = (Double) mouseProfile.get("avgClickPressure");
                double deviation = Math.abs(avgPressure - baselinePressure) / baselinePressure;
                
                if (deviation > ANOMALY_THRESHOLDS.get("CLICK_PRESSURE")) {
                    BehavioralAnomaly anomaly = new BehavioralAnomaly();
                    anomaly.setAnomalyType("CLICK_PRESSURE_ANOMALY");
                    anomaly.setCategory("MOUSE");
                    anomaly.setSeverity(calculateAnomalySeverity(deviation));
                    anomaly.setDescription("Unusual click pressure pattern");
                    anomaly.setDeviation(deviation);
                    anomaly.setBaseline(String.valueOf(baselinePressure));
                    anomaly.setObserved(String.valueOf(avgPressure));
                    anomaly.setDetectedAt(LocalDateTime.now());
                    result.getDetectedAnomalies().add(anomaly);
                }
            }
        }
        
        if (dynamics.getTrajectoryPoints() != null && dynamics.getTrajectoryPoints().size() > 10) {
            double trajectoryLinearity = mouseDynamicsService.calculateTrajectoryLinearity(
                dynamics.getTrajectoryPoints());
            
            if (mouseProfile.containsKey("avgTrajectoryLinearity")) {
                double baselineLinearity = (Double) mouseProfile.get("avgTrajectoryLinearity");
                double deviation = Math.abs(trajectoryLinearity - baselineLinearity);
                
                if (deviation > 0.3) {
                    BehavioralAnomaly anomaly = new BehavioralAnomaly();
                    anomaly.setAnomalyType("TRAJECTORY_ANOMALY");
                    anomaly.setCategory("MOUSE");
                    anomaly.setSeverity(calculateAnomalySeverity(deviation * 2));
                    anomaly.setDescription("Unusual mouse trajectory pattern");
                    anomaly.setDeviation(deviation);
                    anomaly.setDetectedAt(LocalDateTime.now());
                    result.getDetectedAnomalies().add(anomaly);
                }
            }
        }
        
        mouseDynamicsService.analyzeAdvancedPatterns(dynamics, result);
    }
    
    private void analyzeTouchDynamics(
        BehavioralAnalysisRequest request,
        BehavioralAnalysisResult result,
        BehavioralBaseline baseline
    ) {
        if (request.getTouchDynamics() == null) return;
        
        TouchDynamics dynamics = request.getTouchDynamics();
        Map<String, Object> touchProfile = baseline.getTouchProfile();
        
        if (dynamics.getAveragePressure() != null) {
            if (touchProfile.containsKey("avgPressure")) {
                double baselinePressure = (Double) touchProfile.get("avgPressure");
                double deviation = Math.abs(dynamics.getAveragePressure() - baselinePressure) / baselinePressure;
                
                if (deviation > ANOMALY_THRESHOLDS.get("TOUCH_PRESSURE")) {
                    BehavioralAnomaly anomaly = new BehavioralAnomaly();
                    anomaly.setAnomalyType("TOUCH_PRESSURE_ANOMALY");
                    anomaly.setCategory("TOUCH");
                    anomaly.setSeverity(calculateAnomalySeverity(deviation));
                    anomaly.setDescription("Unusual touch pressure pattern");
                    anomaly.setDeviation(deviation);
                    anomaly.setBaseline(String.valueOf(baselinePressure));
                    anomaly.setObserved(String.valueOf(dynamics.getAveragePressure()));
                    anomaly.setDetectedAt(LocalDateTime.now());
                    result.getDetectedAnomalies().add(anomaly);
                }
            }
        }
        
        if (dynamics.getSwipeVelocities() != null && !dynamics.getSwipeVelocities().isEmpty()) {
            double avgSwipeVelocity = dynamics.getSwipeVelocities().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            
            if (touchProfile.containsKey("avgSwipeVelocity")) {
                double baselineVelocity = (Double) touchProfile.get("avgSwipeVelocity");
                double deviation = Math.abs(avgSwipeVelocity - baselineVelocity) / baselineVelocity;
                
                if (deviation > ANOMALY_THRESHOLDS.get("SWIPE_VELOCITY")) {
                    BehavioralAnomaly anomaly = new BehavioralAnomaly();
                    anomaly.setAnomalyType("SWIPE_VELOCITY_ANOMALY");
                    anomaly.setCategory("TOUCH");
                    anomaly.setSeverity(calculateAnomalySeverity(deviation));
                    anomaly.setDescription("Unusual swipe velocity pattern");
                    anomaly.setDeviation(deviation);
                    anomaly.setBaseline(String.valueOf(baselineVelocity));
                    anomaly.setObserved(String.valueOf(avgSwipeVelocity));
                    anomaly.setDetectedAt(LocalDateTime.now());
                    result.getDetectedAnomalies().add(anomaly);
                }
            }
        }
        
        if (Boolean.TRUE.equals(dynamics.getPalmRejectionEvents())) {
            BehavioralAnomaly anomaly = new BehavioralAnomaly();
            anomaly.setAnomalyType("PALM_REJECTION_DETECTED");
            anomaly.setCategory("TOUCH");
            anomaly.setSeverity(0.4);
            anomaly.setDescription("Palm rejection events detected");
            anomaly.setDetectedAt(LocalDateTime.now());
            result.getDetectedAnomalies().add(anomaly);
        }
        
        touchDynamicsService.analyzeAdvancedPatterns(dynamics, result);
    }
    
    private void analyzeNavigationPattern(
        BehavioralAnalysisRequest request,
        BehavioralAnalysisResult result,
        BehavioralBaseline baseline
    ) {
        if (request.getNavigationPattern() == null) return;
        
        NavigationPattern pattern = request.getNavigationPattern();
        Map<String, Object> navigationProfile = baseline.getNavigationProfile();
        
        if (Boolean.TRUE.equals(pattern.getSuspiciousNavigation())) {
            BehavioralAnomaly anomaly = new BehavioralAnomaly();
            anomaly.setAnomalyType("SUSPICIOUS_NAVIGATION");
            anomaly.setCategory("NAVIGATION");
            anomaly.setSeverity(0.8);
            anomaly.setDescription("Suspicious navigation pattern detected");
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of(
                "navigationPath", pattern.getNavigationPath(),
                "backButtonUsage", pattern.getBackButtonUsage()
            ));
            result.getDetectedAnomalies().add(anomaly);
        }
        
        if (pattern.getScrollDepth() != null) {
            if (navigationProfile.containsKey("avgScrollDepth")) {
                double baselineScrollDepth = (Double) navigationProfile.get("avgScrollDepth");
                double deviation = Math.abs(pattern.getScrollDepth() - baselineScrollDepth);
                
                if (deviation > 0.4) {
                    BehavioralAnomaly anomaly = new BehavioralAnomaly();
                    anomaly.setAnomalyType("SCROLL_DEPTH_ANOMALY");
                    anomaly.setCategory("NAVIGATION");
                    anomaly.setSeverity(calculateAnomalySeverity(deviation * 2));
                    anomaly.setDescription("Unusual scroll depth pattern");
                    anomaly.setDeviation(deviation);
                    anomaly.setBaseline(String.valueOf(baselineScrollDepth));
                    anomaly.setObserved(String.valueOf(pattern.getScrollDepth()));
                    anomaly.setDetectedAt(LocalDateTime.now());
                    result.getDetectedAnomalies().add(anomaly);
                }
            }
        }
        
        if (pattern.getBackButtonUsage() != null && pattern.getBackButtonUsage() > 10) {
            BehavioralAnomaly anomaly = new BehavioralAnomaly();
            anomaly.setAnomalyType("EXCESSIVE_BACK_NAVIGATION");
            anomaly.setCategory("NAVIGATION");
            anomaly.setSeverity(0.5);
            anomaly.setDescription("Excessive back button usage");
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of("backButtonUsage", pattern.getBackButtonUsage()));
            result.getDetectedAnomalies().add(anomaly);
        }
        
        navigationAnalysisService.analyzeAdvancedPatterns(pattern, result);
    }
    
    private void analyzeSessionBehavior(
        BehavioralAnalysisRequest request,
        BehavioralAnalysisResult result,
        BehavioralBaseline baseline
    ) {
        if (request.getSessionData() == null) return;
        
        SessionData session = request.getSessionData();
        
        if (session.getSessionDuration() != null && session.getSessionDuration() < 10000) {
            BehavioralAnomaly anomaly = new BehavioralAnomaly();
            anomaly.setAnomalyType("SHORT_SESSION_DURATION");
            anomaly.setCategory("SESSION");
            anomaly.setSeverity(0.6);
            anomaly.setDescription("Unusually short session duration");
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of("duration", session.getSessionDuration()));
            result.getDetectedAnomalies().add(anomaly);
        }
        
        if (session.getActiveTime() != null && session.getIdleTime() != null) {
            double activeRatio = session.getActiveTime() / (session.getActiveTime() + session.getIdleTime());
            
            if (activeRatio > 0.95) {
                BehavioralAnomaly anomaly = new BehavioralAnomaly();
                anomaly.setAnomalyType("CONTINUOUS_ACTIVITY");
                anomaly.setCategory("SESSION");
                anomaly.setSeverity(0.7);
                anomaly.setDescription("Continuous activity without idle time");
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setEvidence(Map.of(
                    "activeRatio", activeRatio,
                    "activeTime", session.getActiveTime(),
                    "idleTime", session.getIdleTime()
                ));
                result.getDetectedAnomalies().add(anomaly);
            }
        }
        
        sessionAnalysisService.analyzeAdvancedPatterns(session, result);
    }
    
    private void analyzeBiometricData(
        BehavioralAnalysisRequest request,
        BehavioralAnalysisResult result,
        BehavioralBaseline baseline
    ) {
        if (request.getBiometricData() == null) return;
        
        BiometricData biometric = request.getBiometricData();
        BiometricAnalysisResult biometricResult = biometricAnalysisService
            .analyzeBiometricData(biometric, baseline.getBiometricProfile());
        
        result.setBiometricAnalysis(biometricResult);
        
        if (!biometricResult.getBiometricMatch()) {
            BehavioralAnomaly anomaly = new BehavioralAnomaly();
            anomaly.setAnomalyType("BIOMETRIC_MISMATCH");
            anomaly.setCategory("BIOMETRIC");
            anomaly.setSeverity(1.0 - biometricResult.getMatchConfidence());
            anomaly.setDescription("Biometric data does not match user profile");
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of(
                "matchConfidence", biometricResult.getMatchConfidence(),
                "modalityScores", biometricResult.getModalityScores()
            ));
            result.getDetectedAnomalies().add(anomaly);
        }
        
        if (biometricResult.getSpoofingDetected()) {
            BehavioralAnomaly anomaly = new BehavioralAnomaly();
            anomaly.setAnomalyType("BIOMETRIC_SPOOFING");
            anomaly.setCategory("BIOMETRIC");
            anomaly.setSeverity(0.9);
            anomaly.setDescription("Biometric spoofing attempt detected");
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setConfirmed(true);
            result.getDetectedAnomalies().add(anomaly);
        }
    }
    
    private void detectBehavioralPatterns(
        BehavioralAnalysisRequest request,
        BehavioralAnalysisResult result
    ) {
        List<BehavioralPattern> patterns = behavioralPatternService
            .detectPatterns(request);
        
        for (BehavioralPattern pattern : patterns) {
            result.getIdentifiedPatterns().add(pattern);
            
            if (!pattern.getIsNormal()) {
                BehavioralAnomaly anomaly = new BehavioralAnomaly();
                anomaly.setAnomalyType("ABNORMAL_PATTERN");
                anomaly.setCategory("PATTERN");
                anomaly.setSeverity(1.0 - pattern.getConfidence());
                anomaly.setDescription("Abnormal behavioral pattern: " + pattern.getDescription());
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setEvidence(pattern.getCharacteristics());
                result.getDetectedAnomalies().add(anomaly);
            }
        }
    }
    
    private void performMLAnalysis(
        BehavioralAnalysisRequest request,
        BehavioralAnalysisResult result
    ) {
        try {
            Map<String, Object> features = extractBehavioralFeatures(request);
            
            Double anomalyScore = mlModelService.predictAnomaly("BEHAVIORAL_ANOMALY_MODEL", features);
            Double authenticityScore = mlModelService.predictAuthenticity("BEHAVIORAL_AUTH_MODEL", features);
            
            if (anomalyScore > 0.7) {
                BehavioralAnomaly anomaly = new BehavioralAnomaly();
                anomaly.setAnomalyType("ML_DETECTED_ANOMALY");
                anomaly.setCategory("ML");
                anomaly.setSeverity(anomalyScore);
                anomaly.setDescription("Machine learning detected behavioral anomaly");
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setEvidence(Map.of(
                    "anomalyScore", anomalyScore,
                    "modelVersion", getCurrentModelVersion()
                ));
                result.getDetectedAnomalies().add(anomaly);
            }
            
            if (result.getMetadata() == null) {
                result.setMetadata(new HashMap<>());
            }
            result.getMetadata().put("mlAnomalyScore", anomalyScore);
            result.getMetadata().put("mlAuthenticityScore", authenticityScore);
            
        } catch (Exception e) {
            log.error("ML analysis failed", e);
        }
    }
    
    private Map<String, Object> extractBehavioralFeatures(BehavioralAnalysisRequest request) {
        Map<String, Object> features = new HashMap<>();
        
        if (request.getKeystrokeDynamics() != null) {
            KeystrokeDynamics kd = request.getKeystrokeDynamics();
            features.put("avgTypingSpeed", kd.getAverageTypingSpeed());
            features.put("typingRhythm", kd.getTypingRhythm());
            features.put("backspaceCount", kd.getBackspaceCount());
            features.put("copyPaste", kd.getCopyPasteDetected());
        }
        
        if (request.getMouseDynamics() != null) {
            MouseDynamics md = request.getMouseDynamics();
            features.put("avgMouseVelocity", md.getAverageVelocity());
            features.put("movementSmoothness", md.getMovementSmoothness());
            features.put("rightClicks", md.getRightClicks());
            features.put("dragEvents", md.getDragEvents());
        }
        
        if (request.getTouchDynamics() != null) {
            TouchDynamics td = request.getTouchDynamics();
            features.put("avgTouchPressure", td.getAveragePressure());
            features.put("tapCount", td.getTapCount());
            features.put("multiTouchCount", td.getMultiTouchCount());
        }
        
        if (request.getSessionData() != null) {
            SessionData sd = request.getSessionData();
            features.put("sessionDuration", sd.getSessionDuration());
            features.put("pageViews", sd.getPageViews());
            features.put("interactions", sd.getInteractions());
        }
        
        return features;
    }
    
    private void calculateBehavioralScore(BehavioralAnalysisResult result) {
        BehavioralScore score = new BehavioralScore();
        score.setComponentScores(new HashMap<>());
        score.setContributingFactors(new ArrayList<>());
        
        double totalAnomalyScore = result.getDetectedAnomalies().stream()
            .mapToDouble(BehavioralAnomaly::getSeverity)
            .max()
            .orElse(0.0);
        
        double patternScore = result.getIdentifiedPatterns().stream()
            .filter(p -> !p.getIsNormal())
            .mapToDouble(p -> 1.0 - p.getConfidence())
            .max()
            .orElse(0.0);
        
        double biometricScore = 0.0;
        if (result.getBiometricAnalysis() != null) {
            biometricScore = 1.0 - result.getBiometricAnalysis().getMatchConfidence();
        }
        
        Double mlScore = 0.0;
        if (result.getMetadata() != null && result.getMetadata().containsKey("mlAnomalyScore")) {
            mlScore = (Double) result.getMetadata().get("mlAnomalyScore");
        }
        
        score.getComponentScores().put("anomalyScore", totalAnomalyScore);
        score.getComponentScores().put("patternScore", patternScore);
        score.getComponentScores().put("biometricScore", biometricScore);
        score.getComponentScores().put("mlScore", mlScore);
        
        double overallScore = 
            totalAnomalyScore * 0.4 +
            patternScore * 0.25 +
            biometricScore * 0.25 +
            mlScore * 0.1;
        
        score.setOverallScore(Math.min(1.0, overallScore));
        score.setAuthenticityScore(1.0 - score.getOverallScore());
        score.setConsistencyScore(calculateConsistencyScore(result));
        score.setRiskLevel(determineRiskLevel(score.getOverallScore()));
        score.setConfidence(calculateConfidenceScore(result));
        score.setContributingFactors(identifyContributingFactors(result));
        
        result.setBehavioralScore(score);
    }
    
    private double calculateConsistencyScore(BehavioralAnalysisResult result) {
        if (result.getUserBaseline().getDataPointsCount() < 5) {
            return 0.5;
        }
        
        double consistencySum = 0.0;
        int consistencyCount = 0;
        
        for (BehavioralAnomaly anomaly : result.getDetectedAnomalies()) {
            if (anomaly.getDeviation() != null) {
                consistencySum += Math.max(0, 1.0 - anomaly.getDeviation());
                consistencyCount++;
            }
        }
        
        return consistencyCount > 0 ? consistencySum / consistencyCount : 0.8;
    }
    
    private String calculateConfidenceScore(BehavioralAnalysisResult result) {
        int dataPoints = result.getDetectedAnomalies().size() + 
                        result.getIdentifiedPatterns().size();
        
        if (result.getUserBaseline().getDataPointsCount() > 50 && dataPoints > 10) {
            return "HIGH";
        } else if (result.getUserBaseline().getDataPointsCount() > 20 && dataPoints > 5) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    private List<String> identifyContributingFactors(BehavioralAnalysisResult result) {
        return result.getDetectedAnomalies().stream()
            .filter(a -> a.getSeverity() > 0.5)
            .map(BehavioralAnomaly::getAnomalyType)
            .collect(Collectors.toList());
    }
    
    private void makeBehavioralDecision(BehavioralAnalysisResult result) {
        BehavioralDecision decision = new BehavioralDecision();
        decision.setRequiredActions(new ArrayList<>());
        decision.setParameters(new HashMap<>());
        
        double score = result.getBehavioralScore().getOverallScore();
        
        if (score >= CRITICAL_ANOMALY_THRESHOLD) {
            decision.setDecision("BLOCK");
            decision.setReasoning("Critical behavioral anomaly detected");
            decision.setConfidence(0.95);
            decision.getRequiredActions().add("BLOCK_SESSION");
            decision.getRequiredActions().add("REQUIRE_REAUTH");
            decision.getRequiredActions().add("SECURITY_REVIEW");
            decision.setRequiresHumanReview(true);
        } else if (score >= HIGH_ANOMALY_THRESHOLD) {
            decision.setDecision("CHALLENGE");
            decision.setReasoning("High behavioral anomaly requires verification");
            decision.setConfidence(0.85);
            decision.getRequiredActions().add("STEP_UP_AUTH");
            decision.getRequiredActions().add("ENHANCED_MONITORING");
            decision.setRequiresHumanReview(true);
        } else if (score >= MEDIUM_ANOMALY_THRESHOLD) {
            decision.setDecision("MONITOR");
            decision.setReasoning("Medium behavioral anomaly detected");
            decision.setConfidence(0.75);
            decision.getRequiredActions().add("INCREASED_MONITORING");
            decision.getRequiredActions().add("UPDATE_BASELINE");
            decision.setRequiresHumanReview(false);
        } else if (score >= LOW_ANOMALY_THRESHOLD) {
            decision.setDecision("ALLOW_WITH_CAUTION");
            decision.setReasoning("Low behavioral anomaly detected");
            decision.setConfidence(0.80);
            decision.getRequiredActions().add("STANDARD_MONITORING");
            decision.setRequiresHumanReview(false);
        } else {
            decision.setDecision("ALLOW");
            decision.setReasoning("Normal behavioral pattern");
            decision.setConfidence(0.90);
            decision.setRequiresHumanReview(false);
        }
        
        result.setDecision(decision);
    }
    
    private void generateRecommendations(BehavioralAnalysisResult result) {
        result.setRecommendations(new ArrayList<>());
        
        if (result.getBehavioralScore().getOverallScore() >= HIGH_ANOMALY_THRESHOLD) {
            BehavioralRecommendation rec = new BehavioralRecommendation();
            rec.setType("ENHANCED_AUTHENTICATION");
            rec.setPriority("HIGH");
            rec.setDescription("Implement enhanced authentication measures");
            rec.setConfiguration(Map.of(
                "methods", Arrays.asList("BIOMETRIC", "OTP", "PUSH"),
                "validityPeriod", "1_HOUR"
            ));
            rec.setExpectedImpact(0.7);
            rec.setAutomated(true);
            result.getRecommendations().add(rec);
        }
        
        if (result.getUserBaseline().getDataPointsCount() < 20) {
            BehavioralRecommendation rec = new BehavioralRecommendation();
            rec.setType("BASELINE_ENRICHMENT");
            rec.setPriority("MEDIUM");
            rec.setDescription("Collect more behavioral data to improve baseline");
            rec.setConfiguration(Map.of(
                "targetDataPoints", 50,
                "collectionPeriod", "30_DAYS"
            ));
            rec.setExpectedImpact(0.4);
            rec.setAutomated(true);
            result.getRecommendations().add(rec);
        }
        
        for (BehavioralAnomaly anomaly : result.getDetectedAnomalies()) {
            if ("BIOMETRIC_SPOOFING".equals(anomaly.getAnomalyType())) {
                BehavioralRecommendation rec = new BehavioralRecommendation();
                rec.setType("ANTI_SPOOFING");
                rec.setPriority("CRITICAL");
                rec.setDescription("Deploy anti-spoofing countermeasures");
                rec.setConfiguration(Map.of(
                    "method", "LIVENESS_DETECTION",
                    "sensitivity", "HIGH"
                ));
                rec.setExpectedImpact(0.9);
                rec.setAutomated(false);
                result.getRecommendations().add(rec);
                break;
            }
        }
    }
    
    private void updateUserBaseline(BehavioralAnalysisRequest request, BehavioralAnalysisResult result) {
        if (result.getBehavioralScore().getOverallScore() < LOW_ANOMALY_THRESHOLD) {
            BehavioralBaseline baseline = result.getUserBaseline();
            
            if (request.getKeystrokeDynamics() != null) {
                updateKeystrokeBaseline(baseline, request.getKeystrokeDynamics());
            }
            
            if (request.getMouseDynamics() != null) {
                updateMouseBaseline(baseline, request.getMouseDynamics());
            }
            
            if (request.getTouchDynamics() != null) {
                updateTouchBaseline(baseline, request.getTouchDynamics());
            }
            
            baseline.setDataPointsCount(baseline.getDataPointsCount() + 1);
            baseline.setLastUpdated(LocalDateTime.now());
            baseline.setProfileConfidence(Math.min(1.0, 
                baseline.getDataPointsCount() / 100.0));
            
            String cacheKey = "behavioral:baseline:" + request.getUserId();
            redisCache.set(cacheKey, baseline, Duration.ofDays(30));
        }
    }
    
    private void updateKeystrokeBaseline(BehavioralBaseline baseline, KeystrokeDynamics dynamics) {
        Map<String, Object> profile = baseline.getKeystrokeProfile();
        
        if (dynamics.getAverageTypingSpeed() != null) {
            updateProfileValue(profile, "avgTypingSpeed", dynamics.getAverageTypingSpeed());
        }
        
        if (dynamics.getDwellTimes() != null && !dynamics.getDwellTimes().isEmpty()) {
            double avgDwellTime = dynamics.getDwellTimes().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
            updateProfileValue(profile, "avgDwellTime", avgDwellTime);
        }
        
        if (dynamics.getFlightTimes() != null && !dynamics.getFlightTimes().isEmpty()) {
            double avgFlightTime = dynamics.getFlightTimes().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
            updateProfileValue(profile, "avgFlightTime", avgFlightTime);
        }
    }
    
    private void updateMouseBaseline(BehavioralBaseline baseline, MouseDynamics dynamics) {
        Map<String, Object> profile = baseline.getMouseProfile();
        
        if (dynamics.getAverageVelocity() != null) {
            updateProfileValue(profile, "avgVelocity", dynamics.getAverageVelocity());
        }
        
        if (dynamics.getMovementSmoothness() != null) {
            updateProfileValue(profile, "movementSmoothness", dynamics.getMovementSmoothness());
        }
        
        if (dynamics.getClickPressures() != null && !dynamics.getClickPressures().isEmpty()) {
            double avgPressure = dynamics.getClickPressures().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            updateProfileValue(profile, "avgClickPressure", avgPressure);
        }
    }
    
    private void updateTouchBaseline(BehavioralBaseline baseline, TouchDynamics dynamics) {
        Map<String, Object> profile = baseline.getTouchProfile();
        
        if (dynamics.getAveragePressure() != null) {
            updateProfileValue(profile, "avgPressure", dynamics.getAveragePressure());
        }
        
        if (dynamics.getSwipeVelocities() != null && !dynamics.getSwipeVelocities().isEmpty()) {
            double avgSwipeVelocity = dynamics.getSwipeVelocities().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            updateProfileValue(profile, "avgSwipeVelocity", avgSwipeVelocity);
        }
    }
    
    private void updateProfileValue(Map<String, Object> profile, String key, Object newValue) {
        if (profile.containsKey(key)) {
            Object existing = profile.get(key);
            if (existing instanceof Number && newValue instanceof Number) {
                double existingVal = ((Number) existing).doubleValue();
                double newVal = ((Number) newValue).doubleValue();
                double updated = (existingVal * 0.8) + (newVal * 0.2);
                profile.put(key, updated);
            }
        } else {
            profile.put(key, newValue);
        }
    }
    
    private double calculateAnomalySeverity(double deviation) {
        return Math.min(1.0, deviation);
    }
    
    private double calculateZScore(double value, Map<String, Object> profile, String historyKey) {
        if (!profile.containsKey(historyKey)) {
            return 0.0;
        }
        
        List<Double> history = (List<Double>) profile.get(historyKey);
        if (history.size() < 3) {
            return 0.0;
        }
        
        double mean = history.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = history.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        
        return stdDev == 0 ? 0 : (value - mean) / stdDev;
    }
    
    private String determineRiskLevel(double score) {
        if (score >= CRITICAL_ANOMALY_THRESHOLD) return "CRITICAL";
        if (score >= HIGH_ANOMALY_THRESHOLD) return "HIGH";
        if (score >= MEDIUM_ANOMALY_THRESHOLD) return "MEDIUM";
        if (score >= LOW_ANOMALY_THRESHOLD) return "LOW";
        return "MINIMAL";
    }
    
    private String getCurrentModelVersion() {
        return "v1.5.2";
    }
    
    private void updateBehavioralBaselines() {
        log.info("Updating behavioral baselines...");
        try {
            List<BehavioralBaseline> baselines = behavioralBaselineRepository.findAll();
            for (BehavioralBaseline baseline : baselines) {
                if (baseline.getDataPointsCount() > 100) {
                    behavioralPatternService.optimizeBaseline(baseline);
                    behavioralBaselineRepository.save(baseline);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update behavioral baselines", e);
        }
    }
    
    private void cleanupOldAnalyses() {
        log.info("Cleaning up old behavioral analyses...");
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
            behavioralAnomalyRepository.deleteByDetectedAtBefore(cutoff);
            sessionAnalysisRepository.deleteByAnalyzedAtBefore(cutoff);
        } catch (Exception e) {
            log.error("Failed to cleanup old analyses", e);
        }
    }
    
    private void retrainBehavioralModels() {
        log.info("Retraining behavioral models...");
        try {
            mlModelService.retrainModel("BEHAVIORAL_ANOMALY_MODEL");
            mlModelService.retrainModel("BEHAVIORAL_AUTH_MODEL");
        } catch (Exception e) {
            log.error("Failed to retrain behavioral models", e);
        }
    }
    
    private void handleCriticalAnomaly(BehavioralAnalysisRequest request, BehavioralAnalysisResult result) {
        log.error("CRITICAL BEHAVIORAL ANOMALY - User: {}, Session: {}, Score: {}", 
            request.getUserId(), request.getSessionId(), result.getBehavioralScore().getOverallScore());
        
        sendCriticalAnomalyAlert(request, result);
    }
    
    private void handleHighAnomaly(BehavioralAnalysisRequest request, BehavioralAnalysisResult result) {
        log.warn("HIGH BEHAVIORAL ANOMALY - User: {}, Session: {}, Score: {}", 
            request.getUserId(), request.getSessionId(), result.getBehavioralScore().getOverallScore());
        
        sendHighAnomalyNotification(request, result);
    }
    
    private void handleMediumAnomaly(BehavioralAnalysisRequest request, BehavioralAnalysisResult result) {
        log.info("MEDIUM BEHAVIORAL ANOMALY - User: {}, Session: {}, Score: {}", 
            request.getUserId(), request.getSessionId(), result.getBehavioralScore().getOverallScore());
    }
    
    private void handleLowAnomaly(BehavioralAnalysisRequest request, BehavioralAnalysisResult result) {
        log.info("LOW BEHAVIORAL ANOMALY - User: {}, Session: {}, Score: {}", 
            request.getUserId(), request.getSessionId(), result.getBehavioralScore().getOverallScore());
    }
    
    private void sendCriticalAnomalyAlert(BehavioralAnalysisRequest request, BehavioralAnalysisResult result) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("CRITICAL_BEHAVIORAL_ANOMALY")
            .priority("URGENT")
            .recipient("security-team")
            .subject("Critical Behavioral Anomaly Detected")
            .templateData(Map.of(
                "userId", request.getUserId(),
                "sessionId", request.getSessionId(),
                "anomalyScore", result.getBehavioralScore().getOverallScore(),
                "anomalies", result.getDetectedAnomalies().stream()
                    .filter(a -> a.getSeverity() > 0.7)
                    .map(a -> a.getDescription())
                    .collect(Collectors.toList()),
                "decision", result.getDecision().getDecision()
            ))
            .channels(Arrays.asList("EMAIL", "SLACK", "PAGERDUTY"))
            .build();
        
        notificationService.send(template);
    }
    
    private void sendHighAnomalyNotification(BehavioralAnalysisRequest request, BehavioralAnalysisResult result) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("HIGH_BEHAVIORAL_ANOMALY")
            .priority("HIGH")
            .recipient("security-team")
            .subject("High Behavioral Anomaly Detected")
            .templateData(Map.of(
                "userId", request.getUserId(),
                "sessionId", request.getSessionId(),
                "anomalyScore", result.getBehavioralScore().getOverallScore(),
                "contributingFactors", result.getBehavioralScore().getContributingFactors()
            ))
            .channels(Arrays.asList("EMAIL", "SLACK"))
            .build();
        
        notificationService.send(template);
    }
    
    private void persistAnalysisResult(BehavioralAnalysisRequest request, BehavioralAnalysisResult result) {
        BehavioralProfile profile = new BehavioralProfile();
        profile.setAnalysisId(result.getAnalysisId());
        profile.setUserId(request.getUserId());
        profile.setSessionId(request.getSessionId());
        profile.setDeviceId(request.getDeviceId());
        profile.setOverallScore(result.getBehavioralScore().getOverallScore());
        profile.setAuthenticityScore(result.getBehavioralScore().getAuthenticityScore());
        profile.setConsistencyScore(result.getBehavioralScore().getConsistencyScore());
        profile.setRiskLevel(result.getBehavioralScore().getRiskLevel());
        profile.setDecision(result.getDecision().getDecision());
        profile.setAnomalies(objectMapper.convertValue(result.getDetectedAnomalies(), List.class));
        profile.setPatterns(objectMapper.convertValue(result.getIdentifiedPatterns(), List.class));
        profile.setRecommendations(objectMapper.convertValue(result.getRecommendations(), List.class));
        profile.setAnalyzedAt(result.getAnalyzedAt());
        profile.setProcessingTimeMs(result.getProcessingTimeMs());
        
        behavioralProfileRepository.save(profile);
        
        for (BehavioralAnomaly anomaly : result.getDetectedAnomalies()) {
            BehavioralAnomalyEntity entity = new BehavioralAnomalyEntity();
            entity.setUserId(request.getUserId());
            entity.setSessionId(request.getSessionId());
            entity.setAnomalyType(anomaly.getAnomalyType());
            entity.setCategory(anomaly.getCategory());
            entity.setSeverity(anomaly.getSeverity());
            entity.setDescription(anomaly.getDescription());
            entity.setDeviation(anomaly.getDeviation());
            entity.setDetectedAt(anomaly.getDetectedAt());
            entity.setConfirmed(anomaly.getConfirmed());
            behavioralAnomalyRepository.save(entity);
        }
        
        String cacheKey = "behavioral:analysis:" + request.getUserId() + ":" + request.getSessionId();
        redisCache.set(cacheKey, result, Duration.ofHours(24));
    }
    
    private void publishBehavioralEvents(BehavioralAnalysisResult result) {
        kafkaTemplate.send("behavioral-analysis-results", result.getUserId(), result);
        
        if (result.getBehavioralScore().getOverallScore() >= HIGH_ANOMALY_THRESHOLD) {
            kafkaTemplate.send("behavioral-alerts", result.getUserId(), result);
        }
        
        if (result.getDecision().getRequiresHumanReview()) {
            kafkaTemplate.send("behavioral-review-queue", result.getUserId(), result);
        }
    }
    
    private void updateMetrics(BehavioralAnalysisResult result, long processingTime) {
        metricsService.recordBehavioralScore(result.getBehavioralScore().getOverallScore());
        metricsService.recordBehavioralRiskLevel(result.getBehavioralScore().getRiskLevel());
        metricsService.recordBehavioralDecision(result.getDecision().getDecision());
        metricsService.recordAnomalyCount(result.getDetectedAnomalies().size());
        metricsService.recordPatternCount(result.getIdentifiedPatterns().size());
        metricsService.recordProcessingTime("behavioral-analysis", processingTime);
        
        result.getBehavioralScore().getComponentScores().forEach((component, score) -> 
            metricsService.recordComponentScore(component, score)
        );
        
        metricsService.incrementCounter("behavioral.analyses.total");
        metricsService.incrementCounter("behavioral.risk." + 
            result.getBehavioralScore().getRiskLevel().toLowerCase());
    }
    
    private void handleProcessingTimeout(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        metricsService.incrementCounter("behavioral.analysis.timeouts");
        sendToDLQ(record, "Processing timeout");
        acknowledgment.acknowledge();
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Acknowledgment acknowledgment, Exception error) {
        metricsService.incrementCounter("behavioral.analysis.errors");
        log.error("Failed to process behavioral analysis: {}", record.key(), error);
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