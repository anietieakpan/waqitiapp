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
public class TransactionClusteringConsumer {

    private static final String TOPIC = "transaction-clustering";
    private static final String DLQ_TOPIC = "transaction-clustering.dlq";
    private static final int MAX_RETRIES = 3;
    private static final long PROCESSING_TIMEOUT_MS = 15000;
    private static final double SUSPICIOUS_CLUSTER_THRESHOLD = 0.75;
    private static final double HIGH_RISK_CLUSTER_THRESHOLD = 0.60;
    private static final double MEDIUM_RISK_CLUSTER_THRESHOLD = 0.40;
    private static final double LOW_RISK_CLUSTER_THRESHOLD = 0.20;
    
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionClusterRepository transactionClusterRepository;
    private final ClusterAnalysisRepository clusterAnalysisRepository;
    private final FraudRingRepository fraudRingRepository;
    private final MoneyLaunderingClusterRepository moneyLaunderingClusterRepository;
    private final TransactionGraphRepository transactionGraphRepository;
    private final ClusteringService clusteringService;
    private final GraphAnalysisService graphAnalysisService;
    private final PatternDetectionService patternDetectionService;
    private final FraudRingDetectionService fraudRingDetectionService;
    private final MoneyLaunderingDetectionService moneyLaunderingDetectionService;
    private final NetworkAnalysisService networkAnalysisService;
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
    
    private static final Map<String, Double> CLUSTERING_WEIGHTS = new HashMap<>();
    private static final Map<String, List<String>> SUSPICIOUS_PATTERNS = new HashMap<>();
    
    static {
        CLUSTERING_WEIGHTS.put("AMOUNT_SIMILARITY", 0.25);
        CLUSTERING_WEIGHTS.put("TIME_PROXIMITY", 0.20);
        CLUSTERING_WEIGHTS.put("MERCHANT_CORRELATION", 0.15);
        CLUSTERING_WEIGHTS.put("GEOGRAPHIC_PROXIMITY", 0.15);
        CLUSTERING_WEIGHTS.put("PARTICIPANT_OVERLAP", 0.15);
        CLUSTERING_WEIGHTS.put("BEHAVIOR_SIMILARITY", 0.10);
        
        SUSPICIOUS_PATTERNS.put("STRUCTURING", Arrays.asList(
            "AMOUNT_JUST_BELOW_THRESHOLD",
            "FREQUENT_SIMILAR_AMOUNTS",
            "SYSTEMATIC_SPLITTING"
        ));
        SUSPICIOUS_PATTERNS.put("SMURFING", Arrays.asList(
            "MULTIPLE_SMALL_TRANSACTIONS",
            "COORDINATED_TIMING",
            "RELATED_ACCOUNTS"
        ));
        SUSPICIOUS_PATTERNS.put("LAYERING", Arrays.asList(
            "RAPID_MOVEMENT",
            "COMPLEX_ROUTING",
            "MULTIPLE_JURISDICTIONS"
        ));
    }
    
    public TransactionClusteringConsumer(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            TransactionClusterRepository transactionClusterRepository,
            ClusterAnalysisRepository clusterAnalysisRepository,
            FraudRingRepository fraudRingRepository,
            MoneyLaunderingClusterRepository moneyLaunderingClusterRepository,
            TransactionGraphRepository transactionGraphRepository,
            ClusteringService clusteringService,
            GraphAnalysisService graphAnalysisService,
            PatternDetectionService patternDetectionService,
            FraudRingDetectionService fraudRingDetectionService,
            MoneyLaunderingDetectionService moneyLaunderingDetectionService,
            NetworkAnalysisService networkAnalysisService,
            MachineLearningModelService mlModelService,
            NotificationService notificationService,
            MetricsService metricsService,
            RedisCache redisCache
    ) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionClusterRepository = transactionClusterRepository;
        this.clusterAnalysisRepository = clusterAnalysisRepository;
        this.fraudRingRepository = fraudRingRepository;
        this.moneyLaunderingClusterRepository = moneyLaunderingClusterRepository;
        this.transactionGraphRepository = transactionGraphRepository;
        this.clusteringService = clusteringService;
        this.graphAnalysisService = graphAnalysisService;
        this.patternDetectionService = patternDetectionService;
        this.fraudRingDetectionService = fraudRingDetectionService;
        this.moneyLaunderingDetectionService = moneyLaunderingDetectionService;
        this.networkAnalysisService = networkAnalysisService;
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
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("transaction-clustering");
        
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(MAX_RETRIES)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
            .build();
            
        this.retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry("transaction-clustering");
        
        this.executorService = Executors.newFixedThreadPool(12);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(4);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker state transition: {}", event.getStateTransition()));
        
        initializeBackgroundTasks();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionClusteringRequest {
        private String requestId;
        private String transactionId;
        private String userId;
        private String sessionId;
        private TransactionData transaction;
        private List<TransactionData> relatedTransactions;
        private ClusteringContext context;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        private boolean realTimeClustering;
        private String correlationId;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionData {
        private String transactionId;
        private String userId;
        private String accountId;
        private BigDecimal amount;
        private String currency;
        private String transactionType;
        private String merchantId;
        private String merchantName;
        private String merchantCategory;
        private String paymentMethod;
        private LocationInfo location;
        private String description;
        private LocalDateTime timestamp;
        private String status;
        private Map<String, Object> additionalData;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        private String country;
        private String city;
        private String region;
        private Double latitude;
        private Double longitude;
        private String ipAddress;
        private String timezone;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusteringContext {
        private String analysisType;
        private Duration timeWindow;
        private BigDecimal amountThreshold;
        private Integer minClusterSize;
        private Double similarityThreshold;
        private List<String> focusAreas;
        private Map<String, Object> parameters;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionClusteringResult {
        private String analysisId;
        private String requestId;
        private List<TransactionCluster> identifiedClusters;
        private List<SuspiciousPattern> detectedPatterns;
        private List<FraudRing> fraudRings;
        private List<MoneyLaunderingChain> launderingChains;
        private GraphAnalysisResult graphAnalysis;
        private ClusterRiskAssessment riskAssessment;
        private List<ClusterRecommendation> recommendations;
        private ClusteringDecision decision;
        private String modelVersion;
        private Long processingTimeMs;
        private LocalDateTime analyzedAt;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionCluster {
        private String clusterId;
        private String clusterType;
        private List<String> transactionIds;
        private List<String> userIds;
        private List<String> accountIds;
        private BigDecimal totalAmount;
        private Integer transactionCount;
        private Duration timeSpan;
        private ClusterCharacteristics characteristics;
        private Double suspiciousScore;
        private String riskLevel;
        private LocalDateTime firstTransaction;
        private LocalDateTime lastTransaction;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterCharacteristics {
        private Double amountVariation;
        private Double timingPattern;
        private Double geographicSpread;
        private Double merchantDiversity;
        private Double participantConnectivity;
        private Double behaviorSimilarity;
        private List<String> commonAttributes;
        private Map<String, Object> statisticalMetrics;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousPattern {
        private String patternId;
        private String patternType;
        private String patternName;
        private String description;
        private Double confidence;
        private List<String> involvedTransactions;
        private List<String> involvedUsers;
        private Map<String, Object> patternMetrics;
        private String severity;
        private LocalDateTime detectedAt;
        private List<String> indicators;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudRing {
        private String ringId;
        private String ringType;
        private List<String> memberIds;
        private List<String> transactionIds;
        private Double riskScore;
        private String operationPattern;
        private BigDecimal totalVolume;
        private Integer transactionCount;
        private Duration activeTimespan;
        private Map<String, Object> ringCharacteristics;
        private List<String> fraudIndicators;
        private String status;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoneyLaunderingChain {
        private String chainId;
        private String launderingType;
        private List<ChainStep> steps;
        private BigDecimal inputAmount;
        private BigDecimal outputAmount;
        private Double obfuscationScore;
        private Integer layeringDepth;
        private List<String> involvedJurisdictions;
        private Duration totalDuration;
        private Map<String, Object> chainMetrics;
        private String status;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChainStep {
        private Integer stepNumber;
        private String stepType;
        private String transactionId;
        private String fromAccount;
        private String toAccount;
        private BigDecimal amount;
        private String mechanism;
        private LocalDateTime timestamp;
        private Map<String, Object> stepDetails;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphAnalysisResult {
        private Integer nodeCount;
        private Integer edgeCount;
        private Double networkDensity;
        private Double clusteringCoefficient;
        private List<CentralityMeasure> centralityMeasures;
        private List<NetworkCommunity> communities;
        private List<AnomalousSubgraph> anomalousSubgraphs;
        private Map<String, Object> graphMetrics;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CentralityMeasure {
        private String nodeId;
        private String nodeType;
        private Double betweennessCentrality;
        private Double closenessCentrality;
        private Double eigenvectorCentrality;
        private Double pageRank;
        private String centralityRank;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkCommunity {
        private String communityId;
        private List<String> members;
        private Double modularity;
        private Double internalDensity;
        private String communityType;
        private Map<String, Object> communityMetrics;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalousSubgraph {
        private String subgraphId;
        private List<String> nodes;
        private List<String> edges;
        private String anomalyType;
        private Double anomalyScore;
        private String description;
        private Map<String, Object> anomalyMetrics;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterRiskAssessment {
        private Double overallRiskScore;
        private String riskLevel;
        private Map<String, Double> riskFactorScores;
        private List<String> riskIndicators;
        private String primaryConcern;
        private Double confidence;
        private Map<String, Object> assessmentDetails;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterRecommendation {
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
    public static class ClusteringDecision {
        private String decision;
        private String reasoning;
        private Double confidence;
        private List<String> requiredActions;
        private Map<String, Object> decisionParameters;
        private boolean requiresManualReview;
        private String escalationLevel;
    }
    
    private void initializeBackgroundTasks() {
        scheduledExecutorService.scheduleWithFixedDelay(
            this::performBatchClustering,
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::updateClusteringModels,
            0, 6, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::cleanupOldClusters,
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
            log.info("Processing transaction clustering request: {} with correlation ID: {}", 
                record.key(), correlationId);
            
            TransactionClusteringRequest request = deserializeMessage(record.value());
            validateRequest(request);
            
            CompletableFuture<TransactionClusteringResult> clusteringFuture = CompletableFuture
                .supplyAsync(() -> executeWithResilience(() -> 
                    performTransactionClustering(request, correlationId)), executorService)
                .orTimeout(PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            TransactionClusteringResult result = clusteringFuture.join();
            
            if (result.getRiskAssessment().getOverallRiskScore() >= SUSPICIOUS_CLUSTER_THRESHOLD) {
                handleSuspiciousClusters(request, result);
            } else if (result.getRiskAssessment().getOverallRiskScore() >= HIGH_RISK_CLUSTER_THRESHOLD) {
                handleHighRiskClusters(request, result);
            } else if (result.getRiskAssessment().getOverallRiskScore() >= MEDIUM_RISK_CLUSTER_THRESHOLD) {
                handleMediumRiskClusters(request, result);
            } else if (result.getRiskAssessment().getOverallRiskScore() >= LOW_RISK_CLUSTER_THRESHOLD) {
                handleLowRiskClusters(request, result);
            }
            
            persistClusteringResult(request, result);
            publishClusteringEvents(result);
            updateMetrics(result, System.currentTimeMillis() - startTime);
            
            acknowledgment.acknowledge();
            
        } catch (TimeoutException e) {
            log.error("Timeout processing transaction clustering for key: {}", record.key(), e);
            handleProcessingTimeout(record, acknowledgment);
        } catch (Exception e) {
            log.error("Error processing transaction clustering for key: {}", record.key(), e);
            handleProcessingError(record, acknowledgment, e);
        }
    }
    
    private TransactionClusteringRequest deserializeMessage(String message) {
        try {
            return objectMapper.readValue(message, TransactionClusteringRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize transaction clustering request", e);
        }
    }
    
    private void validateRequest(TransactionClusteringRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getTransactionId() == null || request.getTransactionId().isEmpty()) {
            errors.add("Transaction ID is required");
        }
        if (request.getTransaction() == null) {
            errors.add("Transaction data is required");
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
    
    private TransactionClusteringResult performTransactionClustering(
        TransactionClusteringRequest request,
        String correlationId
    ) {
        TransactionClusteringResult result = new TransactionClusteringResult();
        result.setAnalysisId(UUID.randomUUID().toString());
        result.setRequestId(request.getRequestId());
        result.setIdentifiedClusters(new ArrayList<>());
        result.setDetectedPatterns(new ArrayList<>());
        result.setFraudRings(new ArrayList<>());
        result.setLaunderingChains(new ArrayList<>());
        result.setRecommendations(new ArrayList<>());
        result.setAnalyzedAt(LocalDateTime.now());
        result.setModelVersion(getCurrentModelVersion());
        
        List<CompletableFuture<Void>> clusteringTasks = Arrays.asList(
            CompletableFuture.runAsync(() -> 
                performAmountBasedClustering(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performTimeBasedClustering(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performGeographicClustering(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performBehavioralClustering(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performNetworkClustering(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                detectFraudRings(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                detectMoneyLaunderingChains(request, result), executorService),
            CompletableFuture.runAsync(() -> 
                performGraphAnalysis(request, result), executorService)
        );

        try {
            CompletableFuture.allOf(clusteringTasks.toArray(new CompletableFuture[0]))
                .get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Transaction clustering timed out after 30 seconds for transaction: {}", request.getTransactionId(), e);
            clusteringTasks.forEach(task -> task.cancel(true));
        } catch (Exception e) {
            log.error("Transaction clustering failed for transaction: {}", request.getTransactionId(), e);
        }

        detectSuspiciousPatterns(result);
        performMLEnhancedClustering(request, result);
        assessClusterRisk(result);
        makeClusteringDecision(result);
        generateRecommendations(result);
        
        long processingTime = System.currentTimeMillis() - 
            result.getAnalyzedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        result.setProcessingTimeMs(processingTime);
        
        return result;
    }
    
    private void performAmountBasedClustering(
        TransactionClusteringRequest request,
        TransactionClusteringResult result
    ) {
        List<TransactionData> transactions = getAllRelevantTransactions(request);
        
        Map<String, List<TransactionData>> amountClusters = clusteringService
            .clusterByAmount(transactions, 0.05);
        
        for (Map.Entry<String, List<TransactionData>> entry : amountClusters.entrySet()) {
            List<TransactionData> clusterTransactions = entry.getValue();
            
            if (clusterTransactions.size() >= 3) {
                TransactionCluster cluster = createCluster(
                    "AMOUNT_BASED",
                    clusterTransactions,
                    "Similar transaction amounts"
                );
                
                ClusterCharacteristics characteristics = new ClusterCharacteristics();
                characteristics.setAmountVariation(calculateAmountVariation(clusterTransactions));
                characteristics.setCommonAttributes(Arrays.asList("SIMILAR_AMOUNTS"));
                cluster.setCharacteristics(characteristics);
                
                if (isStructuringPattern(clusterTransactions)) {
                    cluster.setSuspiciousScore(0.8);
                    cluster.setRiskLevel("HIGH");
                    
                    SuspiciousPattern pattern = new SuspiciousPattern();
                    pattern.setPatternId(UUID.randomUUID().toString());
                    pattern.setPatternType("STRUCTURING");
                    pattern.setPatternName("Amount Structuring");
                    pattern.setDescription("Systematic splitting of amounts to avoid reporting thresholds");
                    pattern.setConfidence(0.85);
                    pattern.setInvolvedTransactions(clusterTransactions.stream()
                        .map(TransactionData::getTransactionId)
                        .collect(Collectors.toList()));
                    pattern.setSeverity("HIGH");
                    pattern.setDetectedAt(LocalDateTime.now());
                    result.getDetectedPatterns().add(pattern);
                } else {
                    cluster.setSuspiciousScore(calculateSuspiciousScore(clusterTransactions));
                    cluster.setRiskLevel(determineRiskLevel(cluster.getSuspiciousScore()));
                }
                
                result.getIdentifiedClusters().add(cluster);
            }
        }
    }
    
    private void performTimeBasedClustering(
        TransactionClusteringRequest request,
        TransactionClusteringResult result
    ) {
        List<TransactionData> transactions = getAllRelevantTransactions(request);
        
        Map<String, List<TransactionData>> timeClusters = clusteringService
            .clusterByTime(transactions, Duration.ofMinutes(30));
        
        for (Map.Entry<String, List<TransactionData>> entry : timeClusters.entrySet()) {
            List<TransactionData> clusterTransactions = entry.getValue();
            
            if (clusterTransactions.size() >= 5) {
                TransactionCluster cluster = createCluster(
                    "TIME_BASED",
                    clusterTransactions,
                    "Transactions clustered by timing"
                );
                
                ClusterCharacteristics characteristics = new ClusterCharacteristics();
                characteristics.setTimingPattern(calculateTimingPattern(clusterTransactions));
                characteristics.setCommonAttributes(Arrays.asList("SYNCHRONIZED_TIMING"));
                cluster.setCharacteristics(characteristics);
                
                if (isCoordinatedAttack(clusterTransactions)) {
                    cluster.setSuspiciousScore(0.9);
                    cluster.setRiskLevel("CRITICAL");
                    
                    SuspiciousPattern pattern = new SuspiciousPattern();
                    pattern.setPatternId(UUID.randomUUID().toString());
                    pattern.setPatternType("COORDINATED_ATTACK");
                    pattern.setPatternName("Coordinated Transaction Attack");
                    pattern.setDescription("Multiple transactions executed in coordinated manner");
                    pattern.setConfidence(0.9);
                    pattern.setInvolvedTransactions(clusterTransactions.stream()
                        .map(TransactionData::getTransactionId)
                        .collect(Collectors.toList()));
                    pattern.setSeverity("CRITICAL");
                    pattern.setDetectedAt(LocalDateTime.now());
                    result.getDetectedPatterns().add(pattern);
                } else {
                    cluster.setSuspiciousScore(calculateSuspiciousScore(clusterTransactions));
                    cluster.setRiskLevel(determineRiskLevel(cluster.getSuspiciousScore()));
                }
                
                result.getIdentifiedClusters().add(cluster);
            }
        }
    }
    
    private void performGeographicClustering(
        TransactionClusteringRequest request,
        TransactionClusteringResult result
    ) {
        List<TransactionData> transactions = getAllRelevantTransactions(request);
        
        Map<String, List<TransactionData>> geoClusters = clusteringService
            .clusterByGeography(transactions, 50.0);
        
        for (Map.Entry<String, List<TransactionData>> entry : geoClusters.entrySet()) {
            List<TransactionData> clusterTransactions = entry.getValue();
            
            if (clusterTransactions.size() >= 3) {
                TransactionCluster cluster = createCluster(
                    "GEOGRAPHIC",
                    clusterTransactions,
                    "Geographically clustered transactions"
                );
                
                ClusterCharacteristics characteristics = new ClusterCharacteristics();
                characteristics.setGeographicSpread(calculateGeographicSpread(clusterTransactions));
                characteristics.setCommonAttributes(Arrays.asList("GEOGRAPHIC_PROXIMITY"));
                cluster.setCharacteristics(characteristics);
                
                cluster.setSuspiciousScore(calculateSuspiciousScore(clusterTransactions));
                cluster.setRiskLevel(determineRiskLevel(cluster.getSuspiciousScore()));
                
                result.getIdentifiedClusters().add(cluster);
            }
        }
    }
    
    private void performBehavioralClustering(
        TransactionClusteringRequest request,
        TransactionClusteringResult result
    ) {
        List<TransactionData> transactions = getAllRelevantTransactions(request);
        
        Map<String, List<TransactionData>> behaviorClusters = clusteringService
            .clusterByBehavior(transactions);
        
        for (Map.Entry<String, List<TransactionData>> entry : behaviorClusters.entrySet()) {
            List<TransactionData> clusterTransactions = entry.getValue();
            
            if (clusterTransactions.size() >= 2) {
                TransactionCluster cluster = createCluster(
                    "BEHAVIORAL",
                    clusterTransactions,
                    "Behavioral pattern clustering"
                );
                
                ClusterCharacteristics characteristics = new ClusterCharacteristics();
                characteristics.setBehaviorSimilarity(calculateBehaviorSimilarity(clusterTransactions));
                characteristics.setCommonAttributes(Arrays.asList("SIMILAR_BEHAVIOR"));
                cluster.setCharacteristics(characteristics);
                
                cluster.setSuspiciousScore(calculateSuspiciousScore(clusterTransactions));
                cluster.setRiskLevel(determineRiskLevel(cluster.getSuspiciousScore()));
                
                result.getIdentifiedClusters().add(cluster);
            }
        }
    }
    
    private void performNetworkClustering(
        TransactionClusteringRequest request,
        TransactionClusteringResult result
    ) {
        List<TransactionData> transactions = getAllRelevantTransactions(request);
        
        TransactionGraph graph = buildTransactionGraph(transactions);
        List<NetworkCommunity> communities = networkAnalysisService.detectCommunities(graph);
        
        for (NetworkCommunity community : communities) {
            if (community.getMembers().size() >= 3) {
                List<TransactionData> communityTransactions = transactions.stream()
                    .filter(t -> community.getMembers().contains(t.getUserId()))
                    .collect(Collectors.toList());
                
                TransactionCluster cluster = createCluster(
                    "NETWORK",
                    communityTransactions,
                    "Network community clustering"
                );
                
                ClusterCharacteristics characteristics = new ClusterCharacteristics();
                characteristics.setParticipantConnectivity(community.getInternalDensity());
                characteristics.setCommonAttributes(Arrays.asList("NETWORK_COMMUNITY"));
                cluster.setCharacteristics(characteristics);
                
                cluster.setSuspiciousScore(calculateNetworkSuspiciousScore(community, communityTransactions));
                cluster.setRiskLevel(determineRiskLevel(cluster.getSuspiciousScore()));
                
                result.getIdentifiedClusters().add(cluster);
            }
        }
    }
    
    private void detectFraudRings(
        TransactionClusteringRequest request,
        TransactionClusteringResult result
    ) {
        List<TransactionData> transactions = getAllRelevantTransactions(request);
        List<FraudRing> detectedRings = fraudRingDetectionService.detectFraudRings(transactions);
        
        for (FraudRing ring : detectedRings) {
            if (ring.getRiskScore() > 0.6) {
                result.getFraudRings().add(ring);
                
                SuspiciousPattern pattern = new SuspiciousPattern();
                pattern.setPatternId(UUID.randomUUID().toString());
                pattern.setPatternType("FRAUD_RING");
                pattern.setPatternName("Organized Fraud Ring");
                pattern.setDescription("Coordinated fraudulent activity by multiple participants");
                pattern.setConfidence(ring.getRiskScore());
                pattern.setInvolvedTransactions(ring.getTransactionIds());
                pattern.setInvolvedUsers(ring.getMemberIds());
                pattern.setSeverity(ring.getRiskScore() > 0.8 ? "CRITICAL" : "HIGH");
                pattern.setDetectedAt(LocalDateTime.now());
                pattern.setIndicators(ring.getFraudIndicators());
                result.getDetectedPatterns().add(pattern);
            }
        }
    }
    
    private void detectMoneyLaunderingChains(
        TransactionClusteringRequest request,
        TransactionClusteringResult result
    ) {
        List<TransactionData> transactions = getAllRelevantTransactions(request);
        List<MoneyLaunderingChain> detectedChains = moneyLaunderingDetectionService
            .detectLaunderingChains(transactions);
        
        for (MoneyLaunderingChain chain : detectedChains) {
            if (chain.getObfuscationScore() > 0.5) {
                result.getLaunderingChains().add(chain);
                
                SuspiciousPattern pattern = new SuspiciousPattern();
                pattern.setPatternId(UUID.randomUUID().toString());
                pattern.setPatternType("MONEY_LAUNDERING");
                pattern.setPatternName("Money Laundering Chain");
                pattern.setDescription("Suspicious money movement pattern indicating laundering");
                pattern.setConfidence(chain.getObfuscationScore());
                pattern.setInvolvedTransactions(chain.getSteps().stream()
                    .map(ChainStep::getTransactionId)
                    .collect(Collectors.toList()));
                pattern.setSeverity(chain.getObfuscationScore() > 0.8 ? "CRITICAL" : "HIGH");
                pattern.setDetectedAt(LocalDateTime.now());
                result.getDetectedPatterns().add(pattern);
            }
        }
    }
    
    private void performGraphAnalysis(
        TransactionClusteringRequest request,
        TransactionClusteringResult result
    ) {
        List<TransactionData> transactions = getAllRelevantTransactions(request);
        TransactionGraph graph = buildTransactionGraph(transactions);
        
        GraphAnalysisResult graphAnalysis = graphAnalysisService.analyzeGraph(graph);
        result.setGraphAnalysis(graphAnalysis);
        
        for (AnomalousSubgraph subgraph : graphAnalysis.getAnomalousSubgraphs()) {
            if (subgraph.getAnomalyScore() > 0.7) {
                SuspiciousPattern pattern = new SuspiciousPattern();
                pattern.setPatternId(UUID.randomUUID().toString());
                pattern.setPatternType("GRAPH_ANOMALY");
                pattern.setPatternName("Anomalous Transaction Subgraph");
                pattern.setDescription(subgraph.getDescription());
                pattern.setConfidence(subgraph.getAnomalyScore());
                pattern.setSeverity(subgraph.getAnomalyScore() > 0.9 ? "CRITICAL" : "HIGH");
                pattern.setDetectedAt(LocalDateTime.now());
                result.getDetectedPatterns().add(pattern);
            }
        }
    }
    
    private void detectSuspiciousPatterns(TransactionClusteringResult result) {
        patternDetectionService.enhancePatternDetection(result);
        
        for (TransactionCluster cluster : result.getIdentifiedClusters()) {
            List<String> patterns = identifyClusterPatterns(cluster);
            
            for (String patternType : patterns) {
                if (SUSPICIOUS_PATTERNS.containsKey(patternType)) {
                    SuspiciousPattern pattern = new SuspiciousPattern();
                    pattern.setPatternId(UUID.randomUUID().toString());
                    pattern.setPatternType(patternType);
                    pattern.setPatternName(getPatternName(patternType));
                    pattern.setDescription(getPatternDescription(patternType));
                    pattern.setConfidence(cluster.getSuspiciousScore());
                    pattern.setInvolvedTransactions(cluster.getTransactionIds());
                    pattern.setInvolvedUsers(cluster.getUserIds());
                    pattern.setSeverity(cluster.getRiskLevel());
                    pattern.setDetectedAt(LocalDateTime.now());
                    pattern.setIndicators(SUSPICIOUS_PATTERNS.get(patternType));
                    result.getDetectedPatterns().add(pattern);
                }
            }
        }
    }
    
    private void performMLEnhancedClustering(
        TransactionClusteringRequest request,
        TransactionClusteringResult result
    ) {
        try {
            Map<String, Object> features = extractClusteringFeatures(request, result);
            
            Double mlRiskScore = mlModelService.predictRisk("CLUSTERING_RISK_MODEL", features);
            List<String> mlPatterns = mlModelService.predictPatterns("PATTERN_DETECTION_MODEL", features);
            
            for (TransactionCluster cluster : result.getIdentifiedClusters()) {
                cluster.setSuspiciousScore(Math.max(cluster.getSuspiciousScore(), mlRiskScore));
                cluster.setRiskLevel(determineRiskLevel(cluster.getSuspiciousScore()));
            }
            
            for (String pattern : mlPatterns) {
                SuspiciousPattern suspiciousPattern = new SuspiciousPattern();
                suspiciousPattern.setPatternId(UUID.randomUUID().toString());
                suspiciousPattern.setPatternType("ML_DETECTED");
                suspiciousPattern.setPatternName("ML Detected Pattern: " + pattern);
                suspiciousPattern.setDescription("Machine learning detected suspicious pattern");
                suspiciousPattern.setConfidence(mlRiskScore);
                suspiciousPattern.setSeverity(mlRiskScore > 0.8 ? "HIGH" : "MEDIUM");
                suspiciousPattern.setDetectedAt(LocalDateTime.now());
                result.getDetectedPatterns().add(suspiciousPattern);
            }
            
        } catch (Exception e) {
            log.error("ML enhanced clustering failed", e);
        }
    }
    
    private void assessClusterRisk(TransactionClusteringResult result) {
        ClusterRiskAssessment assessment = new ClusterRiskAssessment();
        assessment.setRiskFactorScores(new HashMap<>());
        assessment.setRiskIndicators(new ArrayList<>());
        assessment.setAssessmentDetails(new HashMap<>());
        
        double maxClusterRisk = result.getIdentifiedClusters().stream()
            .mapToDouble(TransactionCluster::getSuspiciousScore)
            .max()
            .orElse(0.0);
        
        double patternRisk = result.getDetectedPatterns().stream()
            .mapToDouble(SuspiciousPattern::getConfidence)
            .max()
            .orElse(0.0);
        
        double fraudRingRisk = result.getFraudRings().stream()
            .mapToDouble(FraudRing::getRiskScore)
            .max()
            .orElse(0.0);
        
        double launderingRisk = result.getLaunderingChains().stream()
            .mapToDouble(MoneyLaunderingChain::getObfuscationScore)
            .max()
            .orElse(0.0);
        
        assessment.getRiskFactorScores().put("CLUSTER_RISK", maxClusterRisk);
        assessment.getRiskFactorScores().put("PATTERN_RISK", patternRisk);
        assessment.getRiskFactorScores().put("FRAUD_RING_RISK", fraudRingRisk);
        assessment.getRiskFactorScores().put("LAUNDERING_RISK", launderingRisk);
        
        double overallRisk = Math.max(Math.max(maxClusterRisk, patternRisk), 
                                    Math.max(fraudRingRisk, launderingRisk));
        
        assessment.setOverallRiskScore(overallRisk);
        assessment.setRiskLevel(determineRiskLevel(overallRisk));
        assessment.setConfidence(calculateAssessmentConfidence(result));
        assessment.setPrimaryConcern(identifyPrimaryConcern(assessment.getRiskFactorScores()));
        
        identifyRiskIndicators(result, assessment);
        
        result.setRiskAssessment(assessment);
    }
    
    private void makeClusteringDecision(TransactionClusteringResult result) {
        ClusteringDecision decision = new ClusteringDecision();
        decision.setRequiredActions(new ArrayList<>());
        decision.setDecisionParameters(new HashMap<>());
        
        double riskScore = result.getRiskAssessment().getOverallRiskScore();
        boolean hasCriticalPatterns = result.getDetectedPatterns().stream()
            .anyMatch(p -> "CRITICAL".equals(p.getSeverity()));
        
        if (hasCriticalPatterns || riskScore >= SUSPICIOUS_CLUSTER_THRESHOLD) {
            decision.setDecision("INVESTIGATE");
            decision.setReasoning("Critical suspicious patterns or high risk clusters detected");
            decision.setConfidence(0.95);
            decision.getRequiredActions().add("IMMEDIATE_INVESTIGATION");
            decision.getRequiredActions().add("FREEZE_RELATED_ACCOUNTS");
            decision.getRequiredActions().add("ALERT_COMPLIANCE");
            decision.setRequiresManualReview(true);
            decision.setEscalationLevel("URGENT");
        } else if (riskScore >= HIGH_RISK_CLUSTER_THRESHOLD) {
            decision.setDecision("ENHANCED_MONITORING");
            decision.setReasoning("High risk clusters require enhanced monitoring");
            decision.setConfidence(0.85);
            decision.getRequiredActions().add("ENHANCED_MONITORING");
            decision.getRequiredActions().add("TRANSACTION_LIMITS");
            decision.getRequiredActions().add("MANUAL_REVIEW");
            decision.setRequiresManualReview(true);
            decision.setEscalationLevel("HIGH");
        } else if (riskScore >= MEDIUM_RISK_CLUSTER_THRESHOLD) {
            decision.setDecision("MONITOR");
            decision.setReasoning("Medium risk clusters detected");
            decision.setConfidence(0.75);
            decision.getRequiredActions().add("STANDARD_MONITORING");
            decision.getRequiredActions().add("PATTERN_TRACKING");
            decision.setRequiresManualReview(false);
            decision.setEscalationLevel("MEDIUM");
        } else {
            decision.setDecision("ALLOW");
            decision.setReasoning("Low risk clustering patterns");
            decision.setConfidence(0.80);
            decision.getRequiredActions().add("ROUTINE_LOGGING");
            decision.setRequiresManualReview(false);
            decision.setEscalationLevel("LOW");
        }
        
        result.setDecision(decision);
    }
    
    private void generateRecommendations(TransactionClusteringResult result) {
        result.setRecommendations(new ArrayList<>());
        
        if (result.getRiskAssessment().getOverallRiskScore() >= HIGH_RISK_CLUSTER_THRESHOLD) {
            ClusterRecommendation rec = new ClusterRecommendation();
            rec.setRecommendationType("INVESTIGATION_PROTOCOL");
            rec.setPriority("HIGH");
            rec.setAction("Initiate comprehensive investigation of identified clusters");
            rec.setReasoning("High risk patterns require immediate attention");
            rec.setParameters(Map.of(
                "investigationType", "COMPREHENSIVE",
                "timeframe", "24_HOURS",
                "resources", "SPECIALIZED_TEAM"
            ));
            rec.setExpectedImpact(0.8);
            rec.setAutomated(false);
            rec.setValidUntil(LocalDateTime.now().plusDays(7));
            result.getRecommendations().add(rec);
        }
        
        if (!result.getFraudRings().isEmpty()) {
            ClusterRecommendation rec = new ClusterRecommendation();
            rec.setRecommendationType("FRAUD_RING_DISRUPTION");
            rec.setPriority("CRITICAL");
            rec.setAction("Disrupt identified fraud rings");
            rec.setReasoning("Active fraud rings pose immediate threat");
            rec.setParameters(Map.of(
                "disruptionMethod", "ACCOUNT_FREEZE",
                "coordination", "LAW_ENFORCEMENT"
            ));
            rec.setExpectedImpact(0.9);
            rec.setAutomated(true);
            rec.setValidUntil(LocalDateTime.now().plusHours(6));
            result.getRecommendations().add(rec);
        }
        
        if (!result.getLaunderingChains().isEmpty()) {
            ClusterRecommendation rec = new ClusterRecommendation();
            rec.setRecommendationType("AML_COMPLIANCE");
            rec.setPriority("CRITICAL");
            rec.setAction("File suspicious activity reports");
            rec.setReasoning("Money laundering patterns require regulatory reporting");
            rec.setParameters(Map.of(
                "reportType", "SAR_FILING",
                "urgency", "IMMEDIATE"
            ));
            rec.setExpectedImpact(0.95);
            rec.setAutomated(false);
            rec.setValidUntil(LocalDateTime.now().plusDays(1));
            result.getRecommendations().add(rec);
        }
    }
    
    private List<TransactionData> getAllRelevantTransactions(TransactionClusteringRequest request) {
        List<TransactionData> transactions = new ArrayList<>();
        transactions.add(request.getTransaction());
        
        if (request.getRelatedTransactions() != null) {
            transactions.addAll(request.getRelatedTransactions());
        }
        
        Duration timeWindow = request.getContext() != null && request.getContext().getTimeWindow() != null ?
            request.getContext().getTimeWindow() : Duration.ofHours(24);
        
        List<TransactionData> additionalTransactions = clusteringService
            .findRelatedTransactions(request.getTransaction(), timeWindow);
        transactions.addAll(additionalTransactions);
        
        return transactions.stream().distinct().collect(Collectors.toList());
    }
    
    private TransactionCluster createCluster(
        String clusterType,
        List<TransactionData> transactions,
        String description
    ) {
        TransactionCluster cluster = new TransactionCluster();
        cluster.setClusterId(UUID.randomUUID().toString());
        cluster.setClusterType(clusterType);
        cluster.setTransactionIds(transactions.stream()
            .map(TransactionData::getTransactionId)
            .collect(Collectors.toList()));
        cluster.setUserIds(transactions.stream()
            .map(TransactionData::getUserId)
            .distinct()
            .collect(Collectors.toList()));
        cluster.setAccountIds(transactions.stream()
            .map(TransactionData::getAccountId)
            .distinct()
            .collect(Collectors.toList()));
        cluster.setTotalAmount(transactions.stream()
            .map(TransactionData::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        cluster.setTransactionCount(transactions.size());
        
        LocalDateTime firstTime = transactions.stream()
            .map(TransactionData::getTimestamp)
            .min(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());
        LocalDateTime lastTime = transactions.stream()
            .map(TransactionData::getTimestamp)
            .max(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());
        
        cluster.setFirstTransaction(firstTime);
        cluster.setLastTransaction(lastTime);
        cluster.setTimeSpan(Duration.between(firstTime, lastTime));
        
        return cluster;
    }
    
    private TransactionGraph buildTransactionGraph(List<TransactionData> transactions) {
        return graphAnalysisService.buildGraph(transactions);
    }
    
    private boolean isStructuringPattern(List<TransactionData> transactions) {
        BigDecimal threshold = new BigDecimal("10000");
        
        return transactions.stream()
            .map(TransactionData::getAmount)
            .allMatch(amount -> amount.compareTo(threshold) < 0 && 
                      amount.compareTo(threshold.multiply(new BigDecimal("0.9"))) > 0);
    }
    
    private boolean isCoordinatedAttack(List<TransactionData> transactions) {
        if (transactions.size() < 5) return false;
        
        long timeRange = transactions.stream()
            .map(t -> t.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .max(Long::compareTo)
            .orElse(0L) - 
            transactions.stream()
            .map(t -> t.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .min(Long::compareTo)
            .orElse(0L);
        
        return timeRange < Duration.ofMinutes(10).toMillis();
    }
    
    private double calculateAmountVariation(List<TransactionData> transactions) {
        if (transactions.size() < 2) return 0.0;
        
        List<Double> amounts = transactions.stream()
            .map(t -> t.getAmount().doubleValue())
            .collect(Collectors.toList());
        
        double mean = amounts.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = amounts.stream()
            .mapToDouble(a -> Math.pow(a - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance) / mean;
    }
    
    private double calculateTimingPattern(List<TransactionData> transactions) {
        if (transactions.size() < 3) return 0.0;
        
        List<Long> intervals = new ArrayList<>();
        List<LocalDateTime> times = transactions.stream()
            .map(TransactionData::getTimestamp)
            .sorted()
            .collect(Collectors.toList());
        
        for (int i = 1; i < times.size(); i++) {
            intervals.add(Duration.between(times.get(i-1), times.get(i)).toMillis());
        }
        
        double avgInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = intervals.stream()
            .mapToDouble(i -> Math.pow(i - avgInterval, 2))
            .average()
            .orElse(0.0);
        
        return avgInterval == 0 ? 0 : Math.sqrt(variance) / avgInterval;
    }
    
    private double calculateGeographicSpread(List<TransactionData> transactions) {
        List<LocationInfo> locations = transactions.stream()
            .map(TransactionData::getLocation)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (locations.size() < 2) return 0.0;
        
        double maxDistance = 0.0;
        for (int i = 0; i < locations.size(); i++) {
            for (int j = i + 1; j < locations.size(); j++) {
                double distance = calculateDistance(
                    locations.get(i).getLatitude(), locations.get(i).getLongitude(),
                    locations.get(j).getLatitude(), locations.get(j).getLongitude()
                );
                maxDistance = Math.max(maxDistance, distance);
            }
        }
        
        return maxDistance;
    }
    
    private double calculateBehaviorSimilarity(List<TransactionData> transactions) {
        return 0.8;
    }
    
    private double calculateSuspiciousScore(List<TransactionData> transactions) {
        double score = 0.0;
        
        if (transactions.size() > 10) score += 0.3;
        if (hasUnusualAmountPatterns(transactions)) score += 0.4;
        if (hasUnusualTimingPatterns(transactions)) score += 0.3;
        
        return Math.min(1.0, score);
    }
    
    private double calculateNetworkSuspiciousScore(NetworkCommunity community, List<TransactionData> transactions) {
        double score = 0.0;
        
        if (community.getInternalDensity() > 0.8) score += 0.4;
        if (transactions.size() > community.getMembers().size() * 2) score += 0.3;
        if (community.getModularity() > 0.5) score += 0.3;
        
        return Math.min(1.0, score);
    }
    
    private boolean hasUnusualAmountPatterns(List<TransactionData> transactions) {
        return calculateAmountVariation(transactions) < 0.1;
    }
    
    private boolean hasUnusualTimingPatterns(List<TransactionData> transactions) {
        return calculateTimingPattern(transactions) < 0.2;
    }
    
    private String determineRiskLevel(double score) {
        if (score >= SUSPICIOUS_CLUSTER_THRESHOLD) return "CRITICAL";
        if (score >= HIGH_RISK_CLUSTER_THRESHOLD) return "HIGH";
        if (score >= MEDIUM_RISK_CLUSTER_THRESHOLD) return "MEDIUM";
        if (score >= LOW_RISK_CLUSTER_THRESHOLD) return "LOW";
        return "MINIMAL";
    }
    
    private List<String> identifyClusterPatterns(TransactionCluster cluster) {
        List<String> patterns = new ArrayList<>();
        
        if ("AMOUNT_BASED".equals(cluster.getClusterType()) && 
            cluster.getCharacteristics().getAmountVariation() < 0.1) {
            patterns.add("STRUCTURING");
        }
        
        if ("TIME_BASED".equals(cluster.getClusterType()) && 
            cluster.getTimeSpan().toMinutes() < 30) {
            patterns.add("SMURFING");
        }
        
        return patterns;
    }
    
    private String getPatternName(String patternType) {
        Map<String, String> names = Map.of(
            "STRUCTURING", "Transaction Structuring",
            "SMURFING", "Smurfing Pattern",
            "LAYERING", "Money Layering"
        );
        return names.getOrDefault(patternType, patternType);
    }
    
    private String getPatternDescription(String patternType) {
        Map<String, String> descriptions = Map.of(
            "STRUCTURING", "Systematic breaking down of large amounts to avoid reporting",
            "SMURFING", "Multiple small transactions to avoid detection",
            "LAYERING", "Complex layering of transactions to obscure money trail"
        );
        return descriptions.getOrDefault(patternType, "Suspicious pattern detected");
    }
    
    private Map<String, Object> extractClusteringFeatures(
        TransactionClusteringRequest request,
        TransactionClusteringResult result
    ) {
        Map<String, Object> features = new HashMap<>();
        
        features.put("clusterCount", result.getIdentifiedClusters().size());
        features.put("patternCount", result.getDetectedPatterns().size());
        features.put("fraudRingCount", result.getFraudRings().size());
        features.put("launderingChainCount", result.getLaunderingChains().size());
        
        if (!result.getIdentifiedClusters().isEmpty()) {
            features.put("maxClusterSize", result.getIdentifiedClusters().stream()
                .mapToInt(TransactionCluster::getTransactionCount)
                .max()
                .orElse(0));
            features.put("avgSuspiciousScore", result.getIdentifiedClusters().stream()
                .mapToDouble(TransactionCluster::getSuspiciousScore)
                .average()
                .orElse(0.0));
        }
        
        return features;
    }
    
    private double calculateAssessmentConfidence(TransactionClusteringResult result) {
        int dataPoints = result.getIdentifiedClusters().size() + 
                        result.getDetectedPatterns().size() +
                        result.getFraudRings().size() +
                        result.getLaunderingChains().size();
        
        if (dataPoints >= 10) return 0.9;
        if (dataPoints >= 5) return 0.8;
        if (dataPoints >= 2) return 0.7;
        return 0.6;
    }
    
    private String identifyPrimaryConcern(Map<String, Double> riskFactors) {
        return riskFactors.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");
    }
    
    private void identifyRiskIndicators(TransactionClusteringResult result, ClusterRiskAssessment assessment) {
        if (!result.getFraudRings().isEmpty()) {
            assessment.getRiskIndicators().add("FRAUD_RINGS_DETECTED");
        }
        if (!result.getLaunderingChains().isEmpty()) {
            assessment.getRiskIndicators().add("MONEY_LAUNDERING_CHAINS");
        }
        if (result.getDetectedPatterns().stream().anyMatch(p -> "CRITICAL".equals(p.getSeverity()))) {
            assessment.getRiskIndicators().add("CRITICAL_PATTERNS");
        }
        if (result.getIdentifiedClusters().stream().anyMatch(c -> c.getTransactionCount() > 50)) {
            assessment.getRiskIndicators().add("LARGE_CLUSTERS");
        }
    }
    
    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return 0.0;
        
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    
    private String getCurrentModelVersion() {
        return "v3.2.1";
    }
    
    private void performBatchClustering() {
        log.info("Performing batch clustering analysis...");
        try {
            clusteringService.performBatchAnalysis();
        } catch (Exception e) {
            log.error("Batch clustering failed", e);
        }
    }
    
    private void updateClusteringModels() {
        log.info("Updating clustering models...");
        try {
            mlModelService.retrainModel("CLUSTERING_RISK_MODEL");
            mlModelService.retrainModel("PATTERN_DETECTION_MODEL");
        } catch (Exception e) {
            log.error("Failed to update clustering models", e);
        }
    }
    
    private void cleanupOldClusters() {
        log.info("Cleaning up old clustering data...");
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
            transactionClusterRepository.deleteByAnalyzedAtBefore(cutoff);
            clusterAnalysisRepository.deleteByCreatedAtBefore(cutoff);
        } catch (Exception e) {
            log.error("Failed to cleanup old clustering data", e);
        }
    }
    
    private void handleSuspiciousClusters(TransactionClusteringRequest request, TransactionClusteringResult result) {
        log.error("SUSPICIOUS CLUSTERS DETECTED - Request: {}, Risk Score: {}, Patterns: {}", 
            request.getRequestId(), result.getRiskAssessment().getOverallRiskScore(), 
            result.getDetectedPatterns().size());
        
        sendSuspiciousClusterAlert(request, result);
    }
    
    private void handleHighRiskClusters(TransactionClusteringRequest request, TransactionClusteringResult result) {
        log.warn("HIGH RISK CLUSTERS - Request: {}, Risk Score: {}", 
            request.getRequestId(), result.getRiskAssessment().getOverallRiskScore());
        
        sendHighRiskClusterNotification(request, result);
    }
    
    private void handleMediumRiskClusters(TransactionClusteringRequest request, TransactionClusteringResult result) {
        log.info("MEDIUM RISK CLUSTERS - Request: {}, Risk Score: {}", 
            request.getRequestId(), result.getRiskAssessment().getOverallRiskScore());
    }
    
    private void handleLowRiskClusters(TransactionClusteringRequest request, TransactionClusteringResult result) {
        log.info("LOW RISK CLUSTERS - Request: {}, Risk Score: {}", 
            request.getRequestId(), result.getRiskAssessment().getOverallRiskScore());
    }
    
    private void sendSuspiciousClusterAlert(TransactionClusteringRequest request, TransactionClusteringResult result) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("SUSPICIOUS_CLUSTER_ALERT")
            .priority("URGENT")
            .recipient("fraud-investigation-team")
            .subject("Suspicious Transaction Clusters Detected")
            .templateData(Map.of(
                "requestId", request.getRequestId(),
                "riskScore", result.getRiskAssessment().getOverallRiskScore(),
                "clusterCount", result.getIdentifiedClusters().size(),
                "patternCount", result.getDetectedPatterns().size(),
                "fraudRings", result.getFraudRings().size(),
                "launderingChains", result.getLaunderingChains().size(),
                "primaryConcern", result.getRiskAssessment().getPrimaryConcern(),
                "decision", result.getDecision().getDecision()
            ))
            .channels(Arrays.asList("EMAIL", "SLACK", "PAGERDUTY"))
            .build();
        
        notificationService.send(template);
    }
    
    private void sendHighRiskClusterNotification(TransactionClusteringRequest request, TransactionClusteringResult result) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("HIGH_RISK_CLUSTER")
            .priority("HIGH")
            .recipient("risk-analysis-team")
            .subject("High Risk Transaction Clusters Identified")
            .templateData(Map.of(
                "requestId", request.getRequestId(),
                "riskScore", result.getRiskAssessment().getOverallRiskScore(),
                "clusters", result.getIdentifiedClusters().stream()
                    .filter(c -> "HIGH".equals(c.getRiskLevel()) || "CRITICAL".equals(c.getRiskLevel()))
                    .map(c -> c.getClusterType() + " (" + c.getTransactionCount() + " txns)")
                    .collect(Collectors.toList())
            ))
            .channels(Arrays.asList("EMAIL", "SLACK"))
            .build();
        
        notificationService.send(template);
    }
    
    private void persistClusteringResult(TransactionClusteringRequest request, TransactionClusteringResult result) {
        ClusterAnalysis analysis = new ClusterAnalysis();
        analysis.setAnalysisId(result.getAnalysisId());
        analysis.setRequestId(request.getRequestId());
        analysis.setTransactionId(request.getTransactionId());
        analysis.setUserId(request.getUserId());
        analysis.setSessionId(request.getSessionId());
        analysis.setOverallRiskScore(result.getRiskAssessment().getOverallRiskScore());
        analysis.setRiskLevel(result.getRiskAssessment().getRiskLevel());
        analysis.setDecision(result.getDecision().getDecision());
        analysis.setClusters(objectMapper.convertValue(result.getIdentifiedClusters(), List.class));
        analysis.setPatterns(objectMapper.convertValue(result.getDetectedPatterns(), List.class));
        analysis.setFraudRings(objectMapper.convertValue(result.getFraudRings(), List.class));
        analysis.setLaunderingChains(objectMapper.convertValue(result.getLaunderingChains(), List.class));
        analysis.setRecommendations(objectMapper.convertValue(result.getRecommendations(), List.class));
        analysis.setAnalyzedAt(result.getAnalyzedAt());
        analysis.setProcessingTimeMs(result.getProcessingTimeMs());
        analysis.setModelVersion(result.getModelVersion());
        
        clusterAnalysisRepository.save(analysis);
        
        for (TransactionCluster cluster : result.getIdentifiedClusters()) {
            TransactionClusterEntity entity = new TransactionClusterEntity();
            entity.setClusterId(cluster.getClusterId());
            entity.setClusterType(cluster.getClusterType());
            entity.setTransactionIds(cluster.getTransactionIds());
            entity.setUserIds(cluster.getUserIds());
            entity.setAccountIds(cluster.getAccountIds());
            entity.setTotalAmount(cluster.getTotalAmount());
            entity.setTransactionCount(cluster.getTransactionCount());
            entity.setSuspiciousScore(cluster.getSuspiciousScore());
            entity.setRiskLevel(cluster.getRiskLevel());
            entity.setAnalysisId(result.getAnalysisId());
            entity.setCreatedAt(LocalDateTime.now());
            transactionClusterRepository.save(entity);
        }
        
        String cacheKey = "clustering:analysis:" + request.getRequestId();
        redisCache.set(cacheKey, result, Duration.ofHours(12));
    }
    
    private void publishClusteringEvents(TransactionClusteringResult result) {
        kafkaTemplate.send("clustering-results", result.getAnalysisId(), result);
        
        if (result.getRiskAssessment().getOverallRiskScore() >= HIGH_RISK_CLUSTER_THRESHOLD) {
            kafkaTemplate.send("clustering-alerts", result.getAnalysisId(), result);
        }
        
        if (!result.getFraudRings().isEmpty()) {
            kafkaTemplate.send("fraud-ring-alerts", result.getAnalysisId(), result);
        }
        
        if (!result.getLaunderingChains().isEmpty()) {
            kafkaTemplate.send("aml-alerts", result.getAnalysisId(), result);
        }
        
        if (result.getDecision().getRequiresManualReview()) {
            kafkaTemplate.send("clustering-review-queue", result.getAnalysisId(), result);
        }
    }
    
    private void updateMetrics(TransactionClusteringResult result, long processingTime) {
        metricsService.recordClusteringRiskScore(result.getRiskAssessment().getOverallRiskScore());
        metricsService.recordClusteringRiskLevel(result.getRiskAssessment().getRiskLevel());
        metricsService.recordClusteringDecision(result.getDecision().getDecision());
        metricsService.recordClusterCount(result.getIdentifiedClusters().size());
        metricsService.recordPatternCount(result.getDetectedPatterns().size());
        metricsService.recordFraudRingCount(result.getFraudRings().size());
        metricsService.recordLaunderingChainCount(result.getLaunderingChains().size());
        metricsService.recordProcessingTime("transaction-clustering", processingTime);
        
        result.getRiskAssessment().getRiskFactorScores().forEach((factor, score) -> 
            metricsService.recordRiskFactor("clustering." + factor.toLowerCase(), score)
        );
        
        metricsService.incrementCounter("clustering.analyses.total");
        metricsService.incrementCounter("clustering.risk." + 
            result.getRiskAssessment().getRiskLevel().toLowerCase());
        
        if (!result.getFraudRings().isEmpty()) {
            metricsService.incrementCounter("clustering.fraud.rings.detected");
        }
        
        if (!result.getLaunderingChains().isEmpty()) {
            metricsService.incrementCounter("clustering.laundering.chains.detected");
        }
    }
    
    private void handleProcessingTimeout(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        metricsService.incrementCounter("transaction.clustering.timeouts");
        sendToDLQ(record, "Processing timeout");
        acknowledgment.acknowledge();
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Acknowledgment acknowledgment, Exception error) {
        metricsService.incrementCounter("transaction.clustering.errors");
        log.error("Failed to process transaction clustering: {}", record.key(), error);
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