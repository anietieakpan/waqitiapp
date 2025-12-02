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
public class RiskScoringConsumer {

    private static final String TOPIC = "risk-scoring";
    private static final String DLQ_TOPIC = "risk-scoring.dlq";
    private static final int MAX_RETRIES = 3;
    private static final long PROCESSING_TIMEOUT_MS = 8000;
    private static final BigDecimal CRITICAL_RISK_THRESHOLD = new BigDecimal("0.90");
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("0.70");
    private static final BigDecimal MEDIUM_RISK_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal LOW_RISK_THRESHOLD = new BigDecimal("0.30");
    
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RiskScoreRepository riskScoreRepository;
    private final RiskProfileRepository riskProfileRepository;
    private final RiskFactorRepository riskFactorRepository;
    private final RiskModelRepository riskModelRepository;
    private final UserRiskService userRiskService;
    private final TransactionRiskService transactionRiskService;
    private final BehavioralRiskService behavioralRiskService;
    private final NetworkRiskService networkRiskService;
    private final DeviceRiskService deviceRiskService;
    private final GeographicRiskService geographicRiskService;
    private final TemporalRiskService temporalRiskService;
    private final MachineLearningModelService mlModelService;
    private final RiskOrchestrationService riskOrchestrationService;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final RedisCache redisCache;
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    
    private static final Map<String, Double> RISK_FACTOR_WEIGHTS = new HashMap<>();
    private static final Map<String, Double> CONTEXT_MULTIPLIERS = new HashMap<>();
    
    static {
        RISK_FACTOR_WEIGHTS.put("USER_HISTORY", 0.15);
        RISK_FACTOR_WEIGHTS.put("TRANSACTION_PATTERN", 0.20);
        RISK_FACTOR_WEIGHTS.put("BEHAVIORAL_ANOMALY", 0.15);
        RISK_FACTOR_WEIGHTS.put("DEVICE_TRUST", 0.10);
        RISK_FACTOR_WEIGHTS.put("NETWORK_REPUTATION", 0.10);
        RISK_FACTOR_WEIGHTS.put("GEOGRAPHIC_RISK", 0.10);
        RISK_FACTOR_WEIGHTS.put("TEMPORAL_PATTERN", 0.05);
        RISK_FACTOR_WEIGHTS.put("VELOCITY_CHECK", 0.10);
        RISK_FACTOR_WEIGHTS.put("ML_PREDICTION", 0.05);
        
        CONTEXT_MULTIPLIERS.put("FIRST_TIME_USER", 1.3);
        CONTEXT_MULTIPLIERS.put("HIGH_VALUE_TRANSACTION", 1.5);
        CONTEXT_MULTIPLIERS.put("CROSS_BORDER", 1.4);
        CONTEXT_MULTIPLIERS.put("UNUSUAL_MERCHANT", 1.2);
        CONTEXT_MULTIPLIERS.put("PEAK_FRAUD_HOUR", 1.1);
    }
    
    public RiskScoringConsumer(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            RiskScoreRepository riskScoreRepository,
            RiskProfileRepository riskProfileRepository,
            RiskFactorRepository riskFactorRepository,
            RiskModelRepository riskModelRepository,
            UserRiskService userRiskService,
            TransactionRiskService transactionRiskService,
            BehavioralRiskService behavioralRiskService,
            NetworkRiskService networkRiskService,
            DeviceRiskService deviceRiskService,
            GeographicRiskService geographicRiskService,
            TemporalRiskService temporalRiskService,
            MachineLearningModelService mlModelService,
            RiskOrchestrationService riskOrchestrationService,
            NotificationService notificationService,
            MetricsService metricsService,
            RedisCache redisCache) {
        
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.riskScoreRepository = riskScoreRepository;
        this.riskProfileRepository = riskProfileRepository;
        this.riskFactorRepository = riskFactorRepository;
        this.riskModelRepository = riskModelRepository;
        this.userRiskService = userRiskService;
        this.transactionRiskService = transactionRiskService;
        this.behavioralRiskService = behavioralRiskService;
        this.networkRiskService = networkRiskService;
        this.deviceRiskService = deviceRiskService;
        this.geographicRiskService = geographicRiskService;
        this.temporalRiskService = temporalRiskService;
        this.mlModelService = mlModelService;
        this.riskOrchestrationService = riskOrchestrationService;
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
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("risk-scoring");
        
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(MAX_RETRIES)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
            .build();
            
        this.retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry("risk-scoring");
        
        this.executorService = Executors.newFixedThreadPool(12);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(4);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker state transition: {}", event.getStateTransition()));
        
        initializeModelUpdates();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskScoringRequest {
        private String requestId;
        private String entityType;
        private String entityId;
        private String userId;
        private String transactionId;
        private BigDecimal transactionAmount;
        private String transactionType;
        private String merchantId;
        private String merchantCategory;
        private DeviceInfo deviceInfo;
        private NetworkInfo networkInfo;
        private GeoLocation location;
        private BehavioralData behavioralData;
        private Map<String, Object> contextData;
        private List<String> riskIndicators;
        private LocalDateTime timestamp;
        private boolean realTimeScoring;
        private String correlationId;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        private String deviceId;
        private String deviceFingerprint;
        private String deviceType;
        private String osVersion;
        private String appVersion;
        private boolean jailbroken;
        private boolean emulator;
        private Integer trustScore;
        private LocalDateTime firstSeen;
        private LocalDateTime lastSeen;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkInfo {
        private String ipAddress;
        private String isp;
        private String asn;
        private boolean vpn;
        private boolean proxy;
        private boolean tor;
        private boolean hosting;
        private Integer reputationScore;
        private String networkType;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocation {
        private String country;
        private String countryCode;
        private String city;
        private String region;
        private Double latitude;
        private Double longitude;
        private String timezone;
        private Double distanceFromUsual;
        private boolean highRiskCountry;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralData {
        private Long sessionDuration;
        private Integer pageViews;
        private Integer clickCount;
        private Double scrollDepth;
        private Long timeToAction;
        private boolean copyPaste;
        private Map<String, Object> biometrics;
        private List<String> navigationPath;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskScoringResult {
        private String scoringId;
        private String requestId;
        private BigDecimal overallRiskScore;
        private String riskLevel;
        private Map<String, RiskFactorScore> factorScores;
        private List<RiskIndicator> detectedIndicators;
        private List<RiskRule> triggeredRules;
        private RiskDecision decision;
        private List<RiskMitigation> mitigations;
        private String modelVersion;
        private Long processingTimeMs;
        private LocalDateTime scoredAt;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactorScore {
        private String factorName;
        private String category;
        private BigDecimal score;
        private BigDecimal weight;
        private BigDecimal contribution;
        private String reasoning;
        private Map<String, Object> details;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskIndicator {
        private String indicatorType;
        private String severity;
        private String description;
        private BigDecimal impact;
        private LocalDateTime detectedAt;
        private Map<String, Object> evidence;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskRule {
        private String ruleId;
        private String ruleName;
        private String ruleType;
        private String condition;
        private String action;
        private BigDecimal threshold;
        private boolean triggered;
        private String reason;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskDecision {
        private String decision;
        private String reason;
        private BigDecimal confidence;
        private List<String> requiredActions;
        private Map<String, Object> parameters;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskMitigation {
        private String mitigationType;
        private String priority;
        private String description;
        private Map<String, Object> configuration;
        private BigDecimal expectedRiskReduction;
        private boolean automated;
    }
    
    private void initializeModelUpdates() {
        scheduledExecutorService.scheduleWithFixedDelay(
            this::updateRiskModels,
            0, 6, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::recalibrateWeights,
            1, 24, TimeUnit.HOURS
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
            log.info("Processing risk scoring request: {} with correlation ID: {}", 
                record.key(), correlationId);
            
            RiskScoringRequest request = deserializeMessage(record.value());
            validateRequest(request);
            
            CompletableFuture<RiskScoringResult> scoringFuture = CompletableFuture
                .supplyAsync(() -> executeWithResilience(() -> 
                    performRiskScoring(request, correlationId)), executorService)
                .orTimeout(PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            RiskScoringResult result = scoringFuture.join();
            
            if (result.getOverallRiskScore().compareTo(CRITICAL_RISK_THRESHOLD) >= 0) {
                handleCriticalRisk(request, result);
            } else if (result.getOverallRiskScore().compareTo(HIGH_RISK_THRESHOLD) >= 0) {
                handleHighRisk(request, result);
            } else if (result.getOverallRiskScore().compareTo(MEDIUM_RISK_THRESHOLD) >= 0) {
                handleMediumRisk(request, result);
            } else if (result.getOverallRiskScore().compareTo(LOW_RISK_THRESHOLD) >= 0) {
                handleLowRisk(request, result);
            }
            
            persistScoringResult(request, result);
            publishRiskEvents(result);
            updateMetrics(result, System.currentTimeMillis() - startTime);
            
            acknowledgment.acknowledge();
            
        } catch (TimeoutException e) {
            log.error("Timeout processing risk scoring for key: {}", record.key(), e);
            handleProcessingTimeout(record, acknowledgment);
        } catch (Exception e) {
            log.error("Error processing risk scoring for key: {}", record.key(), e);
            handleProcessingError(record, acknowledgment, e);
        }
    }
    
    private RiskScoringRequest deserializeMessage(String message) {
        try {
            return objectMapper.readValue(message, RiskScoringRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize risk scoring request", e);
        }
    }
    
    private void validateRequest(RiskScoringRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getEntityType() == null || request.getEntityType().isEmpty()) {
            errors.add("Entity type is required");
        }
        if (request.getEntityId() == null || request.getEntityId().isEmpty()) {
            errors.add("Entity ID is required");
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
    
    private RiskScoringResult performRiskScoring(
        RiskScoringRequest request,
        String correlationId
    ) {
        RiskScoringResult result = new RiskScoringResult();
        result.setScoringId(UUID.randomUUID().toString());
        result.setRequestId(request.getRequestId());
        result.setFactorScores(new HashMap<>());
        result.setDetectedIndicators(new ArrayList<>());
        result.setTriggeredRules(new ArrayList<>());
        result.setScoredAt(LocalDateTime.now());
        result.setModelVersion(getCurrentModelVersion());
        
        List<CompletableFuture<RiskFactorScore>> scoringTasks = Arrays.asList(
            CompletableFuture.supplyAsync(() -> 
                scoreUserHistory(request), executorService),
            CompletableFuture.supplyAsync(() -> 
                scoreTransactionPattern(request), executorService),
            CompletableFuture.supplyAsync(() -> 
                scoreBehavioralAnomaly(request), executorService),
            CompletableFuture.supplyAsync(() -> 
                scoreDeviceTrust(request), executorService),
            CompletableFuture.supplyAsync(() -> 
                scoreNetworkReputation(request), executorService),
            CompletableFuture.supplyAsync(() -> 
                scoreGeographicRisk(request), executorService),
            CompletableFuture.supplyAsync(() -> 
                scoreTemporalPattern(request), executorService),
            CompletableFuture.supplyAsync(() -> 
                scoreVelocityCheck(request), executorService),
            CompletableFuture.supplyAsync(() -> 
                scoreMachineLearning(request), executorService)
        );
        
        try {
            CompletableFuture.allOf(scoringTasks.toArray(new CompletableFuture[0]))
                .get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Risk scoring timed out after 15 seconds for transaction: {}", request.getTransactionId(), e);
            scoringTasks.forEach(task -> task.cancel(true));
        } catch (Exception e) {
            log.error("Risk scoring failed for transaction: {}", request.getTransactionId(), e);
        }

        scoringTasks.forEach(task -> {
            try {
                RiskFactorScore factorScore = task.get(1, java.util.concurrent.TimeUnit.SECONDS);
                result.getFactorScores().put(factorScore.getFactorName(), factorScore);
            } catch (Exception e) {
                log.error("Error calculating risk factor", e);
            }
        });
        
        detectRiskIndicators(request, result);
        evaluateRiskRules(request, result);
        calculateOverallRiskScore(result);
        applyContextMultipliers(request, result);
        makeRiskDecision(result);
        generateMitigations(result);
        
        long processingTime = System.currentTimeMillis() - result.getScoredAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        result.setProcessingTimeMs(processingTime);
        
        return result;
    }
    
    private RiskFactorScore scoreUserHistory(RiskScoringRequest request) {
        RiskFactorScore score = new RiskFactorScore();
        score.setFactorName("USER_HISTORY");
        score.setCategory("USER");
        score.setWeight(BigDecimal.valueOf(RISK_FACTOR_WEIGHTS.get("USER_HISTORY")));
        
        if (request.getUserId() != null) {
            RiskProfile userProfile = riskProfileRepository.findByUserId(request.getUserId())
                .orElse(new RiskProfile());
            
            BigDecimal historicalRisk = userRiskService.calculateHistoricalRisk(request.getUserId());
            Integer accountAge = userRiskService.getAccountAgeInDays(request.getUserId());
            Integer fraudCount = userRiskService.getFraudCount(request.getUserId());
            BigDecimal avgTransactionRisk = userRiskService.getAverageTransactionRisk(request.getUserId());
            
            BigDecimal factorScore = BigDecimal.ZERO;
            Map<String, Object> details = new HashMap<>();
            
            if (accountAge < 30) {
                factorScore = factorScore.add(new BigDecimal("0.3"));
                details.put("newAccount", true);
            }
            
            if (fraudCount > 0) {
                factorScore = factorScore.add(new BigDecimal("0.5").multiply(
                    BigDecimal.valueOf(Math.min(fraudCount, 3))));
                details.put("fraudHistory", fraudCount);
            }
            
            factorScore = factorScore.add(historicalRisk.multiply(new BigDecimal("0.4")));
            factorScore = factorScore.add(avgTransactionRisk.multiply(new BigDecimal("0.3")));
            
            score.setScore(factorScore.min(BigDecimal.ONE));
            score.setDetails(details);
            score.setReasoning(String.format(
                "Account age: %d days, Fraud count: %d, Historical risk: %.2f",
                accountAge, fraudCount, historicalRisk
            ));
        } else {
            score.setScore(new BigDecimal("0.7"));
            score.setReasoning("No user history available");
        }
        
        score.setContribution(score.getScore().multiply(score.getWeight()));
        return score;
    }
    
    private RiskFactorScore scoreTransactionPattern(RiskScoringRequest request) {
        RiskFactorScore score = new RiskFactorScore();
        score.setFactorName("TRANSACTION_PATTERN");
        score.setCategory("TRANSACTION");
        score.setWeight(BigDecimal.valueOf(RISK_FACTOR_WEIGHTS.get("TRANSACTION_PATTERN")));
        
        BigDecimal factorScore = BigDecimal.ZERO;
        Map<String, Object> details = new HashMap<>();
        
        if (request.getTransactionAmount() != null && request.getUserId() != null) {
            BigDecimal avgAmount = transactionRiskService.getAverageTransactionAmount(request.getUserId());
            BigDecimal deviation = request.getTransactionAmount().subtract(avgAmount)
                .abs().divide(avgAmount.max(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
            
            if (deviation.compareTo(new BigDecimal("3")) > 0) {
                factorScore = factorScore.add(new BigDecimal("0.6"));
                details.put("highDeviation", deviation);
            } else if (deviation.compareTo(new BigDecimal("2")) > 0) {
                factorScore = factorScore.add(new BigDecimal("0.4"));
                details.put("mediumDeviation", deviation);
            }
            
            boolean unusualMerchant = transactionRiskService.isUnusualMerchant(
                request.getUserId(), request.getMerchantCategory());
            if (unusualMerchant) {
                factorScore = factorScore.add(new BigDecimal("0.3"));
                details.put("unusualMerchant", true);
            }
            
            boolean unusualTime = transactionRiskService.isUnusualTransactionTime(
                request.getUserId(), request.getTimestamp());
            if (unusualTime) {
                factorScore = factorScore.add(new BigDecimal("0.2"));
                details.put("unusualTime", true);
            }
        }
        
        if (request.getTransactionType() != null) {
            BigDecimal typeRisk = transactionRiskService.getTransactionTypeRisk(request.getTransactionType());
            factorScore = factorScore.add(typeRisk.multiply(new BigDecimal("0.3")));
            details.put("transactionType", request.getTransactionType());
        }
        
        score.setScore(factorScore.min(BigDecimal.ONE));
        score.setDetails(details);
        score.setReasoning("Transaction pattern analysis completed");
        score.setContribution(score.getScore().multiply(score.getWeight()));
        return score;
    }
    
    private RiskFactorScore scoreBehavioralAnomaly(RiskScoringRequest request) {
        RiskFactorScore score = new RiskFactorScore();
        score.setFactorName("BEHAVIORAL_ANOMALY");
        score.setCategory("BEHAVIORAL");
        score.setWeight(BigDecimal.valueOf(RISK_FACTOR_WEIGHTS.get("BEHAVIORAL_ANOMALY")));
        
        BigDecimal factorScore = BigDecimal.ZERO;
        Map<String, Object> details = new HashMap<>();
        
        if (request.getBehavioralData() != null) {
            BehavioralData behavior = request.getBehavioralData();
            
            BigDecimal anomalyScore = behavioralRiskService.calculateAnomalyScore(behavior);
            factorScore = factorScore.add(anomalyScore.multiply(new BigDecimal("0.5")));
            
            if (behavior.getTimeToAction() != null && behavior.getTimeToAction() < 1000) {
                factorScore = factorScore.add(new BigDecimal("0.3"));
                details.put("automatedBehavior", true);
            }
            
            if (behavior.isCopyPaste()) {
                factorScore = factorScore.add(new BigDecimal("0.1"));
                details.put("copyPaste", true);
            }
            
            if (behavior.getSessionDuration() != null && behavior.getSessionDuration() < 10000) {
                factorScore = factorScore.add(new BigDecimal("0.2"));
                details.put("shortSession", behavior.getSessionDuration());
            }
            
            if (behavior.getBiometrics() != null) {
                BigDecimal biometricRisk = behavioralRiskService.analyzeBiometrics(behavior.getBiometrics());
                factorScore = factorScore.add(biometricRisk.multiply(new BigDecimal("0.3")));
                details.put("biometricAnalysis", biometricRisk);
            }
        }
        
        score.setScore(factorScore.min(BigDecimal.ONE));
        score.setDetails(details);
        score.setReasoning("Behavioral anomaly analysis completed");
        score.setContribution(score.getScore().multiply(score.getWeight()));
        return score;
    }
    
    private RiskFactorScore scoreDeviceTrust(RiskScoringRequest request) {
        RiskFactorScore score = new RiskFactorScore();
        score.setFactorName("DEVICE_TRUST");
        score.setCategory("DEVICE");
        score.setWeight(BigDecimal.valueOf(RISK_FACTOR_WEIGHTS.get("DEVICE_TRUST")));
        
        BigDecimal factorScore = BigDecimal.ZERO;
        Map<String, Object> details = new HashMap<>();
        
        if (request.getDeviceInfo() != null) {
            DeviceInfo device = request.getDeviceInfo();
            
            if (device.isJailbroken() || device.isEmulator()) {
                factorScore = factorScore.add(new BigDecimal("0.8"));
                details.put("compromised", true);
            }
            
            if (device.getTrustScore() != null) {
                BigDecimal trustDeficit = BigDecimal.ONE.subtract(
                    BigDecimal.valueOf(device.getTrustScore()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                );
                factorScore = factorScore.add(trustDeficit.multiply(new BigDecimal("0.5")));
                details.put("trustScore", device.getTrustScore());
            }
            
            if (device.getFirstSeen() != null) {
                long daysSinceFirstSeen = ChronoUnit.DAYS.between(device.getFirstSeen(), LocalDateTime.now());
                if (daysSinceFirstSeen < 1) {
                    factorScore = factorScore.add(new BigDecimal("0.4"));
                    details.put("newDevice", true);
                } else if (daysSinceFirstSeen < 7) {
                    factorScore = factorScore.add(new BigDecimal("0.2"));
                    details.put("recentDevice", true);
                }
            }
            
            BigDecimal deviceRisk = deviceRiskService.calculateDeviceRisk(device.getDeviceFingerprint());
            factorScore = factorScore.add(deviceRisk.multiply(new BigDecimal("0.3")));
        }
        
        score.setScore(factorScore.min(BigDecimal.ONE));
        score.setDetails(details);
        score.setReasoning("Device trust assessment completed");
        score.setContribution(score.getScore().multiply(score.getWeight()));
        return score;
    }
    
    private RiskFactorScore scoreNetworkReputation(RiskScoringRequest request) {
        RiskFactorScore score = new RiskFactorScore();
        score.setFactorName("NETWORK_REPUTATION");
        score.setCategory("NETWORK");
        score.setWeight(BigDecimal.valueOf(RISK_FACTOR_WEIGHTS.get("NETWORK_REPUTATION")));
        
        BigDecimal factorScore = BigDecimal.ZERO;
        Map<String, Object> details = new HashMap<>();
        
        if (request.getNetworkInfo() != null) {
            NetworkInfo network = request.getNetworkInfo();
            
            if (network.isVpn() || network.isProxy() || network.isTor()) {
                factorScore = factorScore.add(new BigDecimal("0.6"));
                details.put("anonymization", true);
                details.put("vpn", network.isVpn());
                details.put("proxy", network.isProxy());
                details.put("tor", network.isTor());
            }
            
            if (network.isHosting()) {
                factorScore = factorScore.add(new BigDecimal("0.4"));
                details.put("hostingProvider", true);
            }
            
            if (network.getReputationScore() != null) {
                BigDecimal reputationRisk = BigDecimal.ONE.subtract(
                    BigDecimal.valueOf(network.getReputationScore()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                );
                factorScore = factorScore.add(reputationRisk.multiply(new BigDecimal("0.7")));
                details.put("reputationScore", network.getReputationScore());
            }
            
            BigDecimal ipRisk = networkRiskService.calculateIpRisk(network.getIpAddress());
            factorScore = factorScore.add(ipRisk.multiply(new BigDecimal("0.3")));
            details.put("ipRisk", ipRisk);
        }
        
        score.setScore(factorScore.min(BigDecimal.ONE));
        score.setDetails(details);
        score.setReasoning("Network reputation assessment completed");
        score.setContribution(score.getScore().multiply(score.getWeight()));
        return score;
    }
    
    private RiskFactorScore scoreGeographicRisk(RiskScoringRequest request) {
        RiskFactorScore score = new RiskFactorScore();
        score.setFactorName("GEOGRAPHIC_RISK");
        score.setCategory("GEOGRAPHIC");
        score.setWeight(BigDecimal.valueOf(RISK_FACTOR_WEIGHTS.get("GEOGRAPHIC_RISK")));
        
        BigDecimal factorScore = BigDecimal.ZERO;
        Map<String, Object> details = new HashMap<>();
        
        if (request.getLocation() != null) {
            GeoLocation location = request.getLocation();
            
            if (location.isHighRiskCountry()) {
                factorScore = factorScore.add(new BigDecimal("0.7"));
                details.put("highRiskCountry", true);
                details.put("country", location.getCountry());
            }
            
            BigDecimal countryRisk = geographicRiskService.getCountryRiskScore(location.getCountryCode());
            factorScore = factorScore.add(countryRisk.multiply(new BigDecimal("0.5")));
            details.put("countryRiskScore", countryRisk);
            
            if (location.getDistanceFromUsual() != null && location.getDistanceFromUsual() > 1000) {
                factorScore = factorScore.add(new BigDecimal("0.4"));
                details.put("farFromUsualLocation", location.getDistanceFromUsual());
            }
            
            if (request.getUserId() != null) {
                boolean impossibleTravel = geographicRiskService.checkImpossibleTravel(
                    request.getUserId(), location, request.getTimestamp()
                );
                if (impossibleTravel) {
                    factorScore = factorScore.add(new BigDecimal("0.8"));
                    details.put("impossibleTravel", true);
                }
            }
        }
        
        score.setScore(factorScore.min(BigDecimal.ONE));
        score.setDetails(details);
        score.setReasoning("Geographic risk assessment completed");
        score.setContribution(score.getScore().multiply(score.getWeight()));
        return score;
    }
    
    private RiskFactorScore scoreTemporalPattern(RiskScoringRequest request) {
        RiskFactorScore score = new RiskFactorScore();
        score.setFactorName("TEMPORAL_PATTERN");
        score.setCategory("TEMPORAL");
        score.setWeight(BigDecimal.valueOf(RISK_FACTOR_WEIGHTS.get("TEMPORAL_PATTERN")));
        
        BigDecimal factorScore = BigDecimal.ZERO;
        Map<String, Object> details = new HashMap<>();
        
        LocalDateTime timestamp = request.getTimestamp();
        int hour = timestamp.getHour();
        DayOfWeek dayOfWeek = timestamp.getDayOfWeek();
        
        if (hour >= 2 && hour <= 5) {
            factorScore = factorScore.add(new BigDecimal("0.4"));
            details.put("nightTimeActivity", true);
        }
        
        BigDecimal hourlyRisk = temporalRiskService.getHourlyRiskScore(hour);
        factorScore = factorScore.add(hourlyRisk.multiply(new BigDecimal("0.3")));
        details.put("hourlyRisk", hourlyRisk);
        
        BigDecimal dayRisk = temporalRiskService.getDayOfWeekRisk(dayOfWeek);
        factorScore = factorScore.add(dayRisk.multiply(new BigDecimal("0.2")));
        details.put("dayRisk", dayRisk);
        
        if (request.getUserId() != null) {
            boolean unusualTime = temporalRiskService.isUnusualTimeForUser(
                request.getUserId(), timestamp
            );
            if (unusualTime) {
                factorScore = factorScore.add(new BigDecimal("0.3"));
                details.put("unusualTimeForUser", true);
            }
        }
        
        score.setScore(factorScore.min(BigDecimal.ONE));
        score.setDetails(details);
        score.setReasoning("Temporal pattern analysis completed");
        score.setContribution(score.getScore().multiply(score.getWeight()));
        return score;
    }
    
    private RiskFactorScore scoreVelocityCheck(RiskScoringRequest request) {
        RiskFactorScore score = new RiskFactorScore();
        score.setFactorName("VELOCITY_CHECK");
        score.setCategory("VELOCITY");
        score.setWeight(BigDecimal.valueOf(RISK_FACTOR_WEIGHTS.get("VELOCITY_CHECK")));
        
        BigDecimal factorScore = BigDecimal.ZERO;
        Map<String, Object> details = new HashMap<>();
        
        if (request.getUserId() != null) {
            Map<String, Integer> velocityMetrics = transactionRiskService.getVelocityMetrics(
                request.getUserId(), request.getTimestamp()
            );
            
            Integer hourlyCount = velocityMetrics.get("hourly");
            Integer dailyCount = velocityMetrics.get("daily");
            Integer weeklyCount = velocityMetrics.get("weekly");
            
            if (hourlyCount > 10) {
                factorScore = factorScore.add(new BigDecimal("0.6"));
                details.put("highHourlyVelocity", hourlyCount);
            } else if (hourlyCount > 5) {
                factorScore = factorScore.add(new BigDecimal("0.3"));
                details.put("mediumHourlyVelocity", hourlyCount);
            }
            
            if (dailyCount > 50) {
                factorScore = factorScore.add(new BigDecimal("0.5"));
                details.put("highDailyVelocity", dailyCount);
            } else if (dailyCount > 20) {
                factorScore = factorScore.add(new BigDecimal("0.25"));
                details.put("mediumDailyVelocity", dailyCount);
            }
            
            BigDecimal velocityScore = transactionRiskService.calculateVelocityRisk(velocityMetrics);
            factorScore = factorScore.add(velocityScore.multiply(new BigDecimal("0.4")));
        }
        
        score.setScore(factorScore.min(BigDecimal.ONE));
        score.setDetails(details);
        score.setReasoning("Velocity check completed");
        score.setContribution(score.getScore().multiply(score.getWeight()));
        return score;
    }
    
    private RiskFactorScore scoreMachineLearning(RiskScoringRequest request) {
        RiskFactorScore score = new RiskFactorScore();
        score.setFactorName("ML_PREDICTION");
        score.setCategory("ML");
        score.setWeight(BigDecimal.valueOf(RISK_FACTOR_WEIGHTS.get("ML_PREDICTION")));
        
        try {
            Map<String, Object> features = extractMLFeatures(request);
            BigDecimal mlScore = mlModelService.predictRisk("RISK_SCORING_MODEL", features);
            
            score.setScore(mlScore);
            score.setDetails(Map.of(
                "modelType", "ENSEMBLE",
                "confidence", calculateMLConfidence(features),
                "modelVersion", getCurrentModelVersion()
            ));
            score.setReasoning("ML model prediction");
        } catch (Exception e) {
            log.error("ML scoring failed", e);
            score.setScore(new BigDecimal("0.5"));
            score.setReasoning("ML scoring unavailable, using default");
        }
        
        score.setContribution(score.getScore().multiply(score.getWeight()));
        return score;
    }
    
    private Map<String, Object> extractMLFeatures(RiskScoringRequest request) {
        Map<String, Object> features = new HashMap<>();
        
        if (request.getTransactionAmount() != null) {
            features.put("amount", request.getTransactionAmount().doubleValue());
        }
        
        if (request.getDeviceInfo() != null) {
            features.put("deviceTrust", request.getDeviceInfo().getTrustScore());
            features.put("isCompromised", request.getDeviceInfo().isJailbroken() || 
                request.getDeviceInfo().isEmulator());
        }
        
        if (request.getNetworkInfo() != null) {
            features.put("networkReputation", request.getNetworkInfo().getReputationScore());
            features.put("isAnonymized", request.getNetworkInfo().isVpn() || 
                request.getNetworkInfo().isProxy() || request.getNetworkInfo().isTor());
        }
        
        if (request.getLocation() != null) {
            features.put("highRiskCountry", request.getLocation().isHighRiskCountry());
            features.put("distanceFromUsual", request.getLocation().getDistanceFromUsual());
        }
        
        if (request.getBehavioralData() != null) {
            features.put("sessionDuration", request.getBehavioralData().getSessionDuration());
            features.put("timeToAction", request.getBehavioralData().getTimeToAction());
        }
        
        features.put("hour", request.getTimestamp().getHour());
        features.put("dayOfWeek", request.getTimestamp().getDayOfWeek().getValue());
        
        return features;
    }
    
    private BigDecimal calculateMLConfidence(Map<String, Object> features) {
        int availableFeatures = (int) features.values().stream()
            .filter(Objects::nonNull)
            .count();
        int totalFeatures = 15;
        
        return BigDecimal.valueOf((double) availableFeatures / totalFeatures);
    }
    
    private void detectRiskIndicators(RiskScoringRequest request, RiskScoringResult result) {
        if (request.getRiskIndicators() != null) {
            for (String indicator : request.getRiskIndicators()) {
                RiskIndicator riskIndicator = new RiskIndicator();
                riskIndicator.setIndicatorType(indicator);
                riskIndicator.setSeverity(determineSeverity(indicator));
                riskIndicator.setDescription(getIndicatorDescription(indicator));
                riskIndicator.setImpact(getIndicatorImpact(indicator));
                riskIndicator.setDetectedAt(LocalDateTime.now());
                result.getDetectedIndicators().add(riskIndicator);
            }
        }
        
        result.getFactorScores().forEach((name, score) -> {
            if (score.getScore().compareTo(new BigDecimal("0.7")) > 0) {
                RiskIndicator indicator = new RiskIndicator();
                indicator.setIndicatorType("HIGH_" + name);
                indicator.setSeverity("HIGH");
                indicator.setDescription("High risk detected in " + name.toLowerCase().replace("_", " "));
                indicator.setImpact(score.getContribution());
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setEvidence(score.getDetails());
                result.getDetectedIndicators().add(indicator);
            }
        });
    }
    
    private void evaluateRiskRules(RiskScoringRequest request, RiskScoringResult result) {
        List<RiskRule> rules = riskModelRepository.findActiveRules();
        
        for (RiskRule rule : rules) {
            if (evaluateRule(rule, request, result)) {
                rule.setTriggered(true);
                rule.setReason("Rule condition met");
                result.getTriggeredRules().add(rule);
            }
        }
    }
    
    private boolean evaluateRule(RiskRule rule, RiskScoringRequest request, RiskScoringResult result) {
        switch (rule.getRuleType()) {
            case "AMOUNT_THRESHOLD":
                return request.getTransactionAmount() != null && 
                    request.getTransactionAmount().compareTo(new BigDecimal(rule.getThreshold().toString())) > 0;
            
            case "COUNTRY_BLOCK":
                return request.getLocation() != null && 
                    rule.getCondition().contains(request.getLocation().getCountryCode());
            
            case "DEVICE_BLOCK":
                return request.getDeviceInfo() != null && 
                    (request.getDeviceInfo().isJailbroken() || request.getDeviceInfo().isEmulator());
            
            case "VELOCITY_LIMIT":
                return result.getFactorScores().containsKey("VELOCITY_CHECK") &&
                    result.getFactorScores().get("VELOCITY_CHECK").getScore()
                        .compareTo(rule.getThreshold()) > 0;
            
            default:
                return false;
        }
    }
    
    private void calculateOverallRiskScore(RiskScoringResult result) {
        BigDecimal totalScore = BigDecimal.ZERO;
        
        for (RiskFactorScore factorScore : result.getFactorScores().values()) {
            totalScore = totalScore.add(factorScore.getContribution());
        }
        
        for (RiskIndicator indicator : result.getDetectedIndicators()) {
            if ("CRITICAL".equals(indicator.getSeverity())) {
                totalScore = totalScore.add(new BigDecimal("0.2"));
            } else if ("HIGH".equals(indicator.getSeverity())) {
                totalScore = totalScore.add(new BigDecimal("0.1"));
            }
        }
        
        for (RiskRule rule : result.getTriggeredRules()) {
            if (rule.isTriggered() && "BLOCK".equals(rule.getAction())) {
                totalScore = BigDecimal.ONE;
                break;
            }
        }
        
        result.setOverallRiskScore(totalScore.min(BigDecimal.ONE));
        result.setRiskLevel(determineRiskLevel(result.getOverallRiskScore()));
    }
    
    private void applyContextMultipliers(RiskScoringRequest request, RiskScoringResult result) {
        BigDecimal multiplier = BigDecimal.ONE;
        
        if (request.getContextData() != null) {
            for (Map.Entry<String, Double> entry : CONTEXT_MULTIPLIERS.entrySet()) {
                if (Boolean.TRUE.equals(request.getContextData().get(entry.getKey()))) {
                    multiplier = multiplier.multiply(BigDecimal.valueOf(entry.getValue()));
                }
            }
        }
        
        if (request.getUserId() == null) {
            multiplier = multiplier.multiply(BigDecimal.valueOf(CONTEXT_MULTIPLIERS.get("FIRST_TIME_USER")));
        }
        
        if (request.getTransactionAmount() != null && 
            request.getTransactionAmount().compareTo(new BigDecimal("10000")) > 0) {
            multiplier = multiplier.multiply(BigDecimal.valueOf(CONTEXT_MULTIPLIERS.get("HIGH_VALUE_TRANSACTION")));
        }
        
        BigDecimal adjustedScore = result.getOverallRiskScore().multiply(multiplier);
        result.setOverallRiskScore(adjustedScore.min(BigDecimal.ONE));
        result.setRiskLevel(determineRiskLevel(result.getOverallRiskScore()));
        
        if (!multiplier.equals(BigDecimal.ONE)) {
            if (result.getMetadata() == null) {
                result.setMetadata(new HashMap<>());
            }
            result.getMetadata().put("contextMultiplier", multiplier);
        }
    }
    
    private void makeRiskDecision(RiskScoringResult result) {
        RiskDecision decision = new RiskDecision();
        decision.setRequiredActions(new ArrayList<>());
        decision.setParameters(new HashMap<>());
        
        if (result.getOverallRiskScore().compareTo(CRITICAL_RISK_THRESHOLD) >= 0) {
            decision.setDecision("BLOCK");
            decision.setReason("Critical risk detected");
            decision.setConfidence(new BigDecimal("0.95"));
            decision.getRequiredActions().add("BLOCK_TRANSACTION");
            decision.getRequiredActions().add("ALERT_SECURITY");
            decision.getRequiredActions().add("FREEZE_ACCOUNT");
        } else if (result.getOverallRiskScore().compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            decision.setDecision("CHALLENGE");
            decision.setReason("High risk requires additional verification");
            decision.setConfidence(new BigDecimal("0.85"));
            decision.getRequiredActions().add("REQUIRE_MFA");
            decision.getRequiredActions().add("MANUAL_REVIEW");
            decision.getParameters().put("mfaType", "SMS_AND_EMAIL");
        } else if (result.getOverallRiskScore().compareTo(MEDIUM_RISK_THRESHOLD) >= 0) {
            decision.setDecision("MONITOR");
            decision.setReason("Medium risk requires monitoring");
            decision.setConfidence(new BigDecimal("0.75"));
            decision.getRequiredActions().add("ENHANCED_MONITORING");
            decision.getRequiredActions().add("VELOCITY_CHECK");
            decision.getParameters().put("monitoringDuration", "24_HOURS");
        } else if (result.getOverallRiskScore().compareTo(LOW_RISK_THRESHOLD) >= 0) {
            decision.setDecision("ALLOW_WITH_MONITORING");
            decision.setReason("Low risk, proceed with standard monitoring");
            decision.setConfidence(new BigDecimal("0.85"));
            decision.getRequiredActions().add("STANDARD_MONITORING");
        } else {
            decision.setDecision("ALLOW");
            decision.setReason("Minimal risk detected");
            decision.setConfidence(new BigDecimal("0.95"));
        }
        
        result.setDecision(decision);
    }
    
    private void generateMitigations(RiskScoringResult result) {
        result.setMitigations(new ArrayList<>());
        
        if (result.getOverallRiskScore().compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            RiskMitigation mitigation = new RiskMitigation();
            mitigation.setMitigationType("STEP_UP_AUTH");
            mitigation.setPriority("HIGH");
            mitigation.setDescription("Require additional authentication");
            mitigation.setConfiguration(Map.of(
                "methods", Arrays.asList("SMS_OTP", "EMAIL_VERIFICATION", "BIOMETRIC"),
                "timeout", "300_SECONDS"
            ));
            mitigation.setExpectedRiskReduction(new BigDecimal("0.3"));
            mitigation.setAutomated(true);
            result.getMitigations().add(mitigation);
        }
        
        if (result.getFactorScores().containsKey("DEVICE_TRUST") &&
            result.getFactorScores().get("DEVICE_TRUST").getScore().compareTo(new BigDecimal("0.6")) > 0) {
            RiskMitigation mitigation = new RiskMitigation();
            mitigation.setMitigationType("DEVICE_VERIFICATION");
            mitigation.setPriority("MEDIUM");
            mitigation.setDescription("Verify device integrity");
            mitigation.setConfiguration(Map.of(
                "checks", Arrays.asList("FINGERPRINT", "JAILBREAK", "EMULATOR")
            ));
            mitigation.setExpectedRiskReduction(new BigDecimal("0.2"));
            mitigation.setAutomated(true);
            result.getMitigations().add(mitigation);
        }
        
        if (result.getFactorScores().containsKey("GEOGRAPHIC_RISK") &&
            result.getFactorScores().get("GEOGRAPHIC_RISK").getScore().compareTo(new BigDecimal("0.7")) > 0) {
            RiskMitigation mitigation = new RiskMitigation();
            mitigation.setMitigationType("LOCATION_VERIFICATION");
            mitigation.setPriority("HIGH");
            mitigation.setDescription("Verify user location");
            mitigation.setConfiguration(Map.of(
                "method", "USER_CONFIRMATION",
                "message", "Confirm transaction from new location"
            ));
            mitigation.setExpectedRiskReduction(new BigDecimal("0.25"));
            mitigation.setAutomated(false);
            result.getMitigations().add(mitigation);
        }
    }
    
    private String determineRiskLevel(BigDecimal score) {
        if (score.compareTo(CRITICAL_RISK_THRESHOLD) >= 0) return "CRITICAL";
        if (score.compareTo(HIGH_RISK_THRESHOLD) >= 0) return "HIGH";
        if (score.compareTo(MEDIUM_RISK_THRESHOLD) >= 0) return "MEDIUM";
        if (score.compareTo(LOW_RISK_THRESHOLD) >= 0) return "LOW";
        return "MINIMAL";
    }
    
    private String determineSeverity(String indicator) {
        if (indicator.contains("CRITICAL") || indicator.contains("BLOCK")) return "CRITICAL";
        if (indicator.contains("HIGH") || indicator.contains("ALERT")) return "HIGH";
        if (indicator.contains("MEDIUM") || indicator.contains("WARNING")) return "MEDIUM";
        return "LOW";
    }
    
    private String getIndicatorDescription(String indicator) {
        Map<String, String> descriptions = Map.of(
            "FRAUD_PATTERN", "Known fraud pattern detected",
            "BLACKLIST_MATCH", "Entity found in blacklist",
            "VELOCITY_VIOLATION", "Transaction velocity limit exceeded",
            "GEO_MISMATCH", "Geographic location mismatch",
            "DEVICE_COMPROMISE", "Compromised device detected"
        );
        return descriptions.getOrDefault(indicator, "Risk indicator: " + indicator);
    }
    
    private BigDecimal getIndicatorImpact(String indicator) {
        Map<String, BigDecimal> impacts = Map.of(
            "FRAUD_PATTERN", new BigDecimal("0.8"),
            "BLACKLIST_MATCH", new BigDecimal("1.0"),
            "VELOCITY_VIOLATION", new BigDecimal("0.6"),
            "GEO_MISMATCH", new BigDecimal("0.5"),
            "DEVICE_COMPROMISE", new BigDecimal("0.7")
        );
        return impacts.getOrDefault(indicator, new BigDecimal("0.3"));
    }
    
    private String getCurrentModelVersion() {
        return "v2.1.0";
    }
    
    private void updateRiskModels() {
        log.info("Updating risk models...");
        try {
            mlModelService.retrainModel("RISK_SCORING_MODEL");
            recalibrateWeights();
        } catch (Exception e) {
            log.error("Failed to update risk models", e);
        }
    }
    
    private void recalibrateWeights() {
        log.info("Recalibrating risk factor weights...");
    }
    
    private void handleCriticalRisk(RiskScoringRequest request, RiskScoringResult result) {
        log.error("CRITICAL RISK DETECTED - Entity: {} {}, Score: {}", 
            request.getEntityType(), request.getEntityId(), result.getOverallRiskScore());
        
        riskOrchestrationService.initiateEmergencyResponse(request, result);
        
        sendCriticalRiskAlert(request, result);
    }
    
    private void handleHighRisk(RiskScoringRequest request, RiskScoringResult result) {
        log.warn("HIGH RISK DETECTED - Entity: {} {}, Score: {}", 
            request.getEntityType(), request.getEntityId(), result.getOverallRiskScore());
        
        riskOrchestrationService.escalateForReview(request, result);
        
        sendHighRiskNotification(request, result);
    }
    
    private void handleMediumRisk(RiskScoringRequest request, RiskScoringResult result) {
        log.info("MEDIUM RISK - Entity: {} {}, Score: {}", 
            request.getEntityType(), request.getEntityId(), result.getOverallRiskScore());
        
        riskOrchestrationService.enableEnhancedMonitoring(request, result);
    }
    
    private void handleLowRisk(RiskScoringRequest request, RiskScoringResult result) {
        log.info("LOW RISK - Entity: {} {}, Score: {}", 
            request.getEntityType(), request.getEntityId(), result.getOverallRiskScore());
    }
    
    private void sendCriticalRiskAlert(RiskScoringRequest request, RiskScoringResult result) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("CRITICAL_RISK_ALERT")
            .priority("URGENT")
            .recipient("risk-team")
            .subject("Critical Risk Detected")
            .templateData(Map.of(
                "entityType", request.getEntityType(),
                "entityId", request.getEntityId(),
                "riskScore", result.getOverallRiskScore(),
                "decision", result.getDecision().getDecision(),
                "indicators", result.getDetectedIndicators()
            ))
            .channels(Arrays.asList("EMAIL", "SMS", "SLACK", "PAGERDUTY"))
            .build();
        
        notificationService.send(template);
    }
    
    private void sendHighRiskNotification(RiskScoringRequest request, RiskScoringResult result) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("HIGH_RISK_ALERT")
            .priority("HIGH")
            .recipient("risk-team")
            .subject("High Risk Transaction Requires Review")
            .templateData(Map.of(
                "entityType", request.getEntityType(),
                "entityId", request.getEntityId(),
                "riskScore", result.getOverallRiskScore(),
                "factorScores", result.getFactorScores()
            ))
            .channels(Arrays.asList("EMAIL", "SLACK"))
            .build();
        
        notificationService.send(template);
    }
    
    private void persistScoringResult(RiskScoringRequest request, RiskScoringResult result) {
        RiskScore score = new RiskScore();
        score.setScoringId(result.getScoringId());
        score.setEntityType(request.getEntityType());
        score.setEntityId(request.getEntityId());
        score.setUserId(request.getUserId());
        score.setTransactionId(request.getTransactionId());
        score.setOverallScore(result.getOverallRiskScore());
        score.setRiskLevel(result.getRiskLevel());
        score.setDecision(result.getDecision().getDecision());
        score.setFactorScores(objectMapper.convertValue(result.getFactorScores(), Map.class));
        score.setIndicators(objectMapper.convertValue(result.getDetectedIndicators(), List.class));
        score.setRules(objectMapper.convertValue(result.getTriggeredRules(), List.class));
        score.setMitigations(objectMapper.convertValue(result.getMitigations(), List.class));
        score.setModelVersion(result.getModelVersion());
        score.setScoredAt(result.getScoredAt());
        score.setProcessingTimeMs(result.getProcessingTimeMs());
        
        riskScoreRepository.save(score);
        
        String cacheKey = "risk:score:" + request.getEntityType() + ":" + request.getEntityId();
        redisCache.set(cacheKey, result, Duration.ofHours(1));
    }
    
    private void publishRiskEvents(RiskScoringResult result) {
        kafkaTemplate.send("risk-scores", result.getScoringId(), result);
        
        if (result.getOverallRiskScore().compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            kafkaTemplate.send("high-risk-alerts", result.getScoringId(), result);
        }
        
        if ("BLOCK".equals(result.getDecision().getDecision())) {
            kafkaTemplate.send("blocked-transactions", result.getScoringId(), result);
        }
    }
    
    private void updateMetrics(RiskScoringResult result, long processingTime) {
        metricsService.recordRiskScore(result.getOverallRiskScore().doubleValue());
        metricsService.recordRiskLevel(result.getRiskLevel());
        metricsService.recordRiskDecision(result.getDecision().getDecision());
        metricsService.recordProcessingTime("risk-scoring", processingTime);
        
        result.getFactorScores().forEach((factor, score) -> 
            metricsService.recordFactorScore(factor, score.getScore().doubleValue())
        );
        
        metricsService.incrementCounter("risk.scores.total");
        metricsService.incrementCounter("risk.level." + result.getRiskLevel().toLowerCase());
        metricsService.incrementCounter("risk.decision." + result.getDecision().getDecision().toLowerCase());
    }
    
    private void handleProcessingTimeout(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        metricsService.incrementCounter("risk.scoring.timeouts");
        sendToDLQ(record, "Processing timeout");
        acknowledgment.acknowledge();
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Acknowledgment acknowledgment, Exception error) {
        metricsService.incrementCounter("risk.scoring.errors");
        log.error("Failed to process risk scoring: {}", record.key(), error);
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