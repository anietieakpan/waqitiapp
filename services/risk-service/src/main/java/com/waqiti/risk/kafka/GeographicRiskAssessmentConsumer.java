package com.waqiti.risk.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.cache.RedisCache;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.NotificationTemplate;
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
public class GeographicRiskAssessmentConsumer {

    private static final String TOPIC = "geographic-risk-assessment";
    private static final String DLQ_TOPIC = "geographic-risk-assessment.dlq";
    private static final int MAX_RETRIES = 3;
    private static final long PROCESSING_TIMEOUT_MS = 10000;
    private static final double CRITICAL_RISK_THRESHOLD = 0.90;
    private static final double HIGH_RISK_THRESHOLD = 0.70;
    private static final double MEDIUM_RISK_THRESHOLD = 0.50;
    private static final double LOW_RISK_THRESHOLD = 0.30;
    
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final GeographicRiskRepository geographicRiskRepository;
    private final CountryRiskRepository countryRiskRepository;
    private final UserLocationRepository userLocationRepository;
    private final SanctionListRepository sanctionListRepository;
    private final TravelPatternRepository travelPatternRepository;
    private final GeolocationService geolocationService;
    private final CountryRiskService countryRiskService;
    private final SanctionService sanctionService;
    private final TravelAnalysisService travelAnalysisService;
    private final ImpossibleTravelService impossibleTravelService;
    private final LocationSpoofingService locationSpoofingService;
    private final TimeZoneAnalysisService timeZoneAnalysisService;
    private final GeoFencingService geoFencingService;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final RedisCache redisCache;
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    
    private static final Map<String, Double> COUNTRY_RISK_SCORES = new HashMap<>();
    private static final Set<String> SANCTIONED_COUNTRIES = new HashSet<>();
    private static final Set<String> HIGH_RISK_COUNTRIES = new HashSet<>();
    private static final Map<String, Double> REGIONAL_RISK_MULTIPLIERS = new HashMap<>();
    
    static {
        COUNTRY_RISK_SCORES.put("US", 0.1);
        COUNTRY_RISK_SCORES.put("GB", 0.1);
        COUNTRY_RISK_SCORES.put("DE", 0.1);
        COUNTRY_RISK_SCORES.put("FR", 0.1);
        COUNTRY_RISK_SCORES.put("CA", 0.1);
        COUNTRY_RISK_SCORES.put("AU", 0.1);
        COUNTRY_RISK_SCORES.put("CN", 0.6);
        COUNTRY_RISK_SCORES.put("RU", 0.8);
        COUNTRY_RISK_SCORES.put("IR", 0.9);
        COUNTRY_RISK_SCORES.put("KP", 1.0);
        COUNTRY_RISK_SCORES.put("SY", 0.9);
        COUNTRY_RISK_SCORES.put("CU", 0.8);
        
        SANCTIONED_COUNTRIES.addAll(Arrays.asList("IR", "KP", "SY", "CU"));
        HIGH_RISK_COUNTRIES.addAll(Arrays.asList("AF", "IQ", "LY", "SO", "SD", "YE"));
        
        REGIONAL_RISK_MULTIPLIERS.put("NORTH_AMERICA", 1.0);
        REGIONAL_RISK_MULTIPLIERS.put("WESTERN_EUROPE", 1.0);
        REGIONAL_RISK_MULTIPLIERS.put("EASTERN_EUROPE", 1.3);
        REGIONAL_RISK_MULTIPLIERS.put("MIDDLE_EAST", 1.8);
        REGIONAL_RISK_MULTIPLIERS.put("AFRICA", 1.5);
        REGIONAL_RISK_MULTIPLIERS.put("ASIA", 1.2);
        REGIONAL_RISK_MULTIPLIERS.put("SOUTH_AMERICA", 1.4);
    }
    
    public GeographicRiskAssessmentConsumer(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            GeographicRiskRepository geographicRiskRepository,
            CountryRiskRepository countryRiskRepository,
            UserLocationRepository userLocationRepository,
            SanctionListRepository sanctionListRepository,
            TravelPatternRepository travelPatternRepository,
            GeolocationService geolocationService,
            CountryRiskService countryRiskService,
            SanctionService sanctionService,
            TravelAnalysisService travelAnalysisService,
            ImpossibleTravelService impossibleTravelService,
            LocationSpoofingService locationSpoofingService,
            TimeZoneAnalysisService timeZoneAnalysisService,
            GeoFencingService geoFencingService,
            NotificationService notificationService,
            MetricsService metricsService,
            RedisCache redisCache
    ) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.geographicRiskRepository = geographicRiskRepository;
        this.countryRiskRepository = countryRiskRepository;
        this.userLocationRepository = userLocationRepository;
        this.sanctionListRepository = sanctionListRepository;
        this.travelPatternRepository = travelPatternRepository;
        this.geolocationService = geolocationService;
        this.countryRiskService = countryRiskService;
        this.sanctionService = sanctionService;
        this.travelAnalysisService = travelAnalysisService;
        this.impossibleTravelService = impossibleTravelService;
        this.locationSpoofingService = locationSpoofingService;
        this.timeZoneAnalysisService = timeZoneAnalysisService;
        this.geoFencingService = geoFencingService;
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
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("geographic-risk-assessment");
        
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(MAX_RETRIES)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
            .build();
            
        this.retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry("geographic-risk-assessment");
        
        this.executorService = Executors.newFixedThreadPool(8);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(3);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker state transition: {}", event.getStateTransition()));
        
        initializeBackgroundTasks();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicRiskRequest {
        private String requestId;
        private String userId;
        private String sessionId;
        private String ipAddress;
        private LocationData currentLocation;
        private LocationData declaredLocation;
        private String timeZone;
        private LocationContext context;
        private List<LocationData> recentLocations;
        private Map<String, Object> deviceInfo;
        private Map<String, Object> networkInfo;
        private LocalDateTime timestamp;
        private boolean realTimeAssessment;
        private String correlationId;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationData {
        private Double latitude;
        private Double longitude;
        private String country;
        private String countryCode;
        private String region;
        private String city;
        private String postalCode;
        private String timezone;
        private Double accuracy;
        private String source;
        private LocalDateTime timestamp;
        private Boolean vpnDetected;
        private Boolean proxyDetected;
        private String isp;
        private String organization;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationContext {
        private String action;
        private BigDecimal transactionAmount;
        private String merchantCountry;
        private String paymentMethod;
        private boolean highValueTransaction;
        private boolean crossBorderTransaction;
        private Map<String, Object> additionalContext;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicRiskResult {
        private String assessmentId;
        private String requestId;
        private String userId;
        private Double overallRiskScore;
        private String riskLevel;
        private Map<String, RiskFactor> riskFactors;
        private List<GeographicAnomaly> detectedAnomalies;
        private List<ComplianceViolation> complianceViolations;
        private LocationAnalysis locationAnalysis;
        private TravelAnalysis travelAnalysis;
        private GeofencingResult geofencingResult;
        private RiskDecision decision;
        private List<RiskMitigation> mitigations;
        private Long processingTimeMs;
        private LocalDateTime assessedAt;
        private Map<String, Object> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorName;
        private Double score;
        private Double weight;
        private String description;
        private Map<String, Object> details;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicAnomaly {
        private String anomalyType;
        private String severity;
        private String description;
        private Double confidence;
        private LocalDateTime detectedAt;
        private Map<String, Object> evidence;
        private boolean actionRequired;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceViolation {
        private String violationType;
        private String regulation;
        private String description;
        private String severity;
        private List<String> requiredActions;
        private LocalDateTime detectedAt;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationAnalysis {
        private String primaryLocation;
        private Double locationConfidence;
        private boolean locationSpoofingDetected;
        private String spoofingMethod;
        private Double distanceFromUsual;
        private boolean newLocationDetected;
        private String locationRiskCategory;
        private Map<String, Object> locationMetadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TravelAnalysis {
        private boolean impossibleTravelDetected;
        private Double travelDistance;
        private Double travelTime;
        private Double maxPossibleSpeed;
        private Double actualSpeed;
        private String travelPattern;
        private List<String> suspiciousIndicators;
        private Map<String, Object> travelMetadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeofencingResult {
        private boolean withinAllowedZone;
        private boolean inRestrictedZone;
        private List<String> violatedZones;
        private String nearestAllowedZone;
        private Double distanceToNearestZone;
        private Map<String, Object> zoneMetadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskDecision {
        private String decision;
        private String reasoning;
        private Double confidence;
        private List<String> requiredActions;
        private Map<String, Object> parameters;
        private boolean requiresManualReview;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskMitigation {
        private String type;
        private String priority;
        private String description;
        private Map<String, Object> configuration;
        private Double expectedRiskReduction;
        private boolean automated;
    }
    
    private void initializeBackgroundTasks() {
        scheduledExecutorService.scheduleWithFixedDelay(
            this::updateCountryRiskScores,
            0, 6, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::updateSanctionLists,
            0, 24, TimeUnit.HOURS
        );
        
        scheduledExecutorService.scheduleWithFixedDelay(
            this::cleanupOldLocationData,
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
            log.info("Processing geographic risk assessment: {} with correlation ID: {}", 
                record.key(), correlationId);
            
            GeographicRiskRequest request = deserializeMessage(record.value());
            validateRequest(request);
            
            CompletableFuture<GeographicRiskResult> assessmentFuture = CompletableFuture
                .supplyAsync(() -> executeWithResilience(() -> 
                    performGeographicRiskAssessment(request, correlationId)), executorService)
                .orTimeout(PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            GeographicRiskResult result = assessmentFuture.join();
            
            if (result.getOverallRiskScore() >= CRITICAL_RISK_THRESHOLD) {
                handleCriticalRisk(request, result);
            } else if (result.getOverallRiskScore() >= HIGH_RISK_THRESHOLD) {
                handleHighRisk(request, result);
            } else if (result.getOverallRiskScore() >= MEDIUM_RISK_THRESHOLD) {
                handleMediumRisk(request, result);
            } else if (result.getOverallRiskScore() >= LOW_RISK_THRESHOLD) {
                handleLowRisk(request, result);
            }
            
            persistAssessmentResult(request, result);
            publishGeographicEvents(result);
            updateMetrics(result, System.currentTimeMillis() - startTime);
            
            acknowledgment.acknowledge();
            
        } catch (TimeoutException e) {
            log.error("Timeout processing geographic risk assessment for key: {}", record.key(), e);
            handleProcessingTimeout(record, acknowledgment);
        } catch (Exception e) {
            log.error("Error processing geographic risk assessment for key: {}", record.key(), e);
            handleProcessingError(record, acknowledgment, e);
        }
    }
    
    private GeographicRiskRequest deserializeMessage(String message) {
        try {
            return objectMapper.readValue(message, GeographicRiskRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize geographic risk request", e);
        }
    }
    
    private void validateRequest(GeographicRiskRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            errors.add("User ID is required");
        }
        if (request.getCurrentLocation() == null && request.getIpAddress() == null) {
            errors.add("Current location or IP address is required");
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
    
    private GeographicRiskResult performGeographicRiskAssessment(
        GeographicRiskRequest request,
        String correlationId
    ) {
        GeographicRiskResult result = new GeographicRiskResult();
        result.setAssessmentId(UUID.randomUUID().toString());
        result.setRequestId(request.getRequestId());
        result.setUserId(request.getUserId());
        result.setRiskFactors(new HashMap<>());
        result.setDetectedAnomalies(new ArrayList<>());
        result.setComplianceViolations(new ArrayList<>());
        result.setMitigations(new ArrayList<>());
        result.setAssessedAt(LocalDateTime.now());
        
        LocationData currentLocation = enrichLocationData(request);
        
        List<CompletableFuture<Void>> assessmentTasks = Arrays.asList(
            CompletableFuture.runAsync(() -> 
                assessCountryRisk(request, result, currentLocation), executorService),
            CompletableFuture.runAsync(() -> 
                assessSanctionCompliance(request, result, currentLocation), executorService),
            CompletableFuture.runAsync(() -> 
                assessLocationSpoofing(request, result, currentLocation), executorService),
            CompletableFuture.runAsync(() -> 
                assessImpossibleTravel(request, result, currentLocation), executorService),
            CompletableFuture.runAsync(() -> 
                assessTimeZoneConsistency(request, result, currentLocation), executorService),
            CompletableFuture.runAsync(() -> 
                assessGeofencing(request, result, currentLocation), executorService),
            CompletableFuture.runAsync(() -> 
                assessDistanceFromUsual(request, result, currentLocation), executorService),
            CompletableFuture.runAsync(() -> 
                assessVpnProxyUsage(request, result, currentLocation), executorService)
        );

        try {
            CompletableFuture.allOf(assessmentTasks.toArray(new CompletableFuture[0]))
                .get(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Geographic risk assessment timed out after 15 seconds for transaction: {}", request.getTransactionId(), e);
            assessmentTasks.forEach(task -> task.cancel(true));
        } catch (Exception e) {
            log.error("Geographic risk assessment failed for transaction: {}", request.getTransactionId(), e);
        }

        calculateOverallRiskScore(result);
        makeRiskDecision(result);
        generateMitigations(result);
        updateUserLocationHistory(request, currentLocation);
        
        long processingTime = System.currentTimeMillis() - 
            result.getAssessedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        result.setProcessingTimeMs(processingTime);
        
        return result;
    }
    
    private LocationData enrichLocationData(GeographicRiskRequest request) {
        LocationData location = request.getCurrentLocation();
        
        if (location == null && request.getIpAddress() != null) {
            location = geolocationService.getLocationFromIP(request.getIpAddress());
        }
        
        if (location != null) {
            if (location.getCountryCode() == null && location.getCountry() != null) {
                location.setCountryCode(geolocationService.getCountryCode(location.getCountry()));
            }
            
            if (location.getTimezone() == null && location.getLatitude() != null && location.getLongitude() != null) {
                location.setTimezone(geolocationService.getTimezone(location.getLatitude(), location.getLongitude()));
            }
            
            if (request.getNetworkInfo() != null) {
                location.setVpnDetected((Boolean) request.getNetworkInfo().get("vpnDetected"));
                location.setProxyDetected((Boolean) request.getNetworkInfo().get("proxyDetected"));
                location.setIsp((String) request.getNetworkInfo().get("isp"));
                location.setOrganization((String) request.getNetworkInfo().get("organization"));
            }
        }
        
        return location;
    }
    
    private void assessCountryRisk(
        GeographicRiskRequest request,
        GeographicRiskResult result,
        LocationData location
    ) {
        if (location == null || location.getCountryCode() == null) {
            return;
        }
        
        String countryCode = location.getCountryCode();
        Double countryRiskScore = COUNTRY_RISK_SCORES.getOrDefault(countryCode, 0.5);
        
        RiskFactor riskFactor = new RiskFactor();
        riskFactor.setFactorName("COUNTRY_RISK");
        riskFactor.setScore(countryRiskScore);
        riskFactor.setWeight(0.3);
        riskFactor.setDescription("Risk assessment based on country of origin");
        riskFactor.setDetails(Map.of(
            "country", location.getCountry(),
            "countryCode", countryCode,
            "riskScore", countryRiskScore
        ));
        result.getRiskFactors().put("COUNTRY_RISK", riskFactor);
        
        if (HIGH_RISK_COUNTRIES.contains(countryCode)) {
            GeographicAnomaly anomaly = new GeographicAnomaly();
            anomaly.setAnomalyType("HIGH_RISK_COUNTRY");
            anomaly.setSeverity("HIGH");
            anomaly.setDescription("Access from high-risk country: " + location.getCountry());
            anomaly.setConfidence(0.9);
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of(
                "country", location.getCountry(),
                "countryCode", countryCode,
                "riskCategory", "HIGH_RISK"
            ));
            anomaly.setActionRequired(true);
            result.getDetectedAnomalies().add(anomaly);
        }
        
        String region = countryRiskService.getRegion(countryCode);
        Double regionalMultiplier = REGIONAL_RISK_MULTIPLIERS.getOrDefault(region, 1.0);
        
        if (regionalMultiplier > 1.5) {
            GeographicAnomaly anomaly = new GeographicAnomaly();
            anomaly.setAnomalyType("HIGH_RISK_REGION");
            anomaly.setSeverity("MEDIUM");
            anomaly.setDescription("Access from high-risk region: " + region);
            anomaly.setConfidence(0.8);
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of(
                "region", region,
                "multiplier", regionalMultiplier
            ));
            result.getDetectedAnomalies().add(anomaly);
        }
    }
    
    private void assessSanctionCompliance(
        GeographicRiskRequest request,
        GeographicRiskResult result,
        LocationData location
    ) {
        if (location == null || location.getCountryCode() == null) {
            return;
        }
        
        String countryCode = location.getCountryCode();
        
        if (SANCTIONED_COUNTRIES.contains(countryCode)) {
            ComplianceViolation violation = new ComplianceViolation();
            violation.setViolationType("SANCTIONS_VIOLATION");
            violation.setRegulation("OFAC_SANCTIONS");
            violation.setDescription("Access from sanctioned country: " + location.getCountry());
            violation.setSeverity("CRITICAL");
            violation.setRequiredActions(Arrays.asList(
                "BLOCK_TRANSACTION",
                "FILE_SAR_REPORT",
                "NOTIFY_COMPLIANCE_TEAM"
            ));
            violation.setDetectedAt(LocalDateTime.now());
            result.getComplianceViolations().add(violation);
            
            GeographicAnomaly anomaly = new GeographicAnomaly();
            anomaly.setAnomalyType("SANCTIONED_COUNTRY");
            anomaly.setSeverity("CRITICAL");
            anomaly.setDescription("Access from sanctioned country");
            anomaly.setConfidence(1.0);
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of(
                "country", location.getCountry(),
                "countryCode", countryCode,
                "sanctionType", "OFAC"
            ));
            anomaly.setActionRequired(true);
            result.getDetectedAnomalies().add(anomaly);
        }
        
        List<SanctionListEntry> sanctionMatches = sanctionService.checkSanctionLists(
            request.getUserId(), location.getCountry(), location.getCity()
        );
        
        for (SanctionListEntry entry : sanctionMatches) {
            ComplianceViolation violation = new ComplianceViolation();
            violation.setViolationType("SANCTION_LIST_MATCH");
            violation.setRegulation(entry.getListType());
            violation.setDescription("Match found on sanction list: " + entry.getListName());
            violation.setSeverity(entry.getSeverity());
            violation.setRequiredActions(Arrays.asList(
                "INVESTIGATE_MATCH",
                "MANUAL_REVIEW",
                "DOCUMENT_DECISION"
            ));
            violation.setDetectedAt(LocalDateTime.now());
            result.getComplianceViolations().add(violation);
        }
    }
    
    private void assessLocationSpoofing(
        GeographicRiskRequest request,
        GeographicRiskResult result,
        LocationData location
    ) {
        if (location == null) {
            return;
        }
        
        LocationSpoofingAnalysis analysis = locationSpoofingService.analyzeLocation(
            location, request.getDeviceInfo(), request.getNetworkInfo()
        );
        
        LocationAnalysis locationAnalysis = new LocationAnalysis();
        locationAnalysis.setPrimaryLocation(location.getCountry() + ", " + location.getCity());
        locationAnalysis.setLocationConfidence(analysis.getConfidence());
        locationAnalysis.setLocationSpoofingDetected(analysis.isSpoofingDetected());
        locationAnalysis.setSpoofingMethod(analysis.getSpoofingMethod());
        locationAnalysis.setLocationRiskCategory(analysis.getRiskCategory());
        locationAnalysis.setLocationMetadata(analysis.getMetadata());
        result.setLocationAnalysis(locationAnalysis);
        
        if (analysis.isSpoofingDetected()) {
            GeographicAnomaly anomaly = new GeographicAnomaly();
            anomaly.setAnomalyType("LOCATION_SPOOFING");
            anomaly.setSeverity("HIGH");
            anomaly.setDescription("Location spoofing detected: " + analysis.getSpoofingMethod());
            anomaly.setConfidence(analysis.getConfidence());
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of(
                "spoofingMethod", analysis.getSpoofingMethod(),
                "indicators", analysis.getSpoofingIndicators()
            ));
            anomaly.setActionRequired(true);
            result.getDetectedAnomalies().add(anomaly);
            
            RiskFactor riskFactor = new RiskFactor();
            riskFactor.setFactorName("LOCATION_SPOOFING");
            riskFactor.setScore(0.8);
            riskFactor.setWeight(0.25);
            riskFactor.setDescription("Location spoofing detected");
            riskFactor.setDetails(analysis.getMetadata());
            result.getRiskFactors().put("LOCATION_SPOOFING", riskFactor);
        }
        
        if (Boolean.TRUE.equals(location.getVpnDetected()) || Boolean.TRUE.equals(location.getProxyDetected())) {
            GeographicAnomaly anomaly = new GeographicAnomaly();
            anomaly.setAnomalyType("LOCATION_OBFUSCATION");
            anomaly.setSeverity("MEDIUM");
            anomaly.setDescription("Location obfuscation detected (VPN/Proxy)");
            anomaly.setConfidence(0.9);
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of(
                "vpnDetected", location.getVpnDetected(),
                "proxyDetected", location.getProxyDetected(),
                "isp", location.getIsp()
            ));
            result.getDetectedAnomalies().add(anomaly);
        }
    }
    
    private void assessImpossibleTravel(
        GeographicRiskRequest request,
        GeographicRiskResult result,
        LocationData location
    ) {
        if (location == null || request.getUserId() == null) {
            return;
        }
        
        List<UserLocation> recentLocations = userLocationRepository
            .findRecentByUserId(request.getUserId(), 24);
        
        if (!recentLocations.isEmpty()) {
            UserLocation lastLocation = recentLocations.get(0);
            
            ImpossibleTravelAnalysis analysis = impossibleTravelService.analyzeTravel(
                lastLocation.getLatitude(), lastLocation.getLongitude(), lastLocation.getTimestamp(),
                location.getLatitude(), location.getLongitude(), request.getTimestamp()
            );
            
            TravelAnalysis travelAnalysis = new TravelAnalysis();
            travelAnalysis.setImpossibleTravelDetected(analysis.isImpossibleTravel());
            travelAnalysis.setTravelDistance(analysis.getDistance());
            travelAnalysis.setTravelTime(analysis.getTravelTimeHours());
            travelAnalysis.setMaxPossibleSpeed(analysis.getMaxPossibleSpeed());
            travelAnalysis.setActualSpeed(analysis.getActualSpeed());
            travelAnalysis.setTravelPattern(analysis.getTravelPattern());
            travelAnalysis.setSuspiciousIndicators(analysis.getSuspiciousIndicators());
            travelAnalysis.setTravelMetadata(analysis.getMetadata());
            result.setTravelAnalysis(travelAnalysis);
            
            if (analysis.isImpossibleTravel()) {
                GeographicAnomaly anomaly = new GeographicAnomaly();
                anomaly.setAnomalyType("IMPOSSIBLE_TRAVEL");
                anomaly.setSeverity("CRITICAL");
                anomaly.setDescription(String.format(
                    "Impossible travel detected: %.2f km in %.2f hours (%.2f km/h)",
                    analysis.getDistance(), analysis.getTravelTimeHours(), analysis.getActualSpeed()
                ));
                anomaly.setConfidence(0.95);
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setEvidence(Map.of(
                    "distance", analysis.getDistance(),
                    "timeHours", analysis.getTravelTimeHours(),
                    "actualSpeed", analysis.getActualSpeed(),
                    "maxPossibleSpeed", analysis.getMaxPossibleSpeed(),
                    "fromLocation", lastLocation.getCity() + ", " + lastLocation.getCountry(),
                    "toLocation", location.getCity() + ", " + location.getCountry()
                ));
                anomaly.setActionRequired(true);
                result.getDetectedAnomalies().add(anomaly);
                
                RiskFactor riskFactor = new RiskFactor();
                riskFactor.setFactorName("IMPOSSIBLE_TRAVEL");
                riskFactor.setScore(1.0);
                riskFactor.setWeight(0.4);
                riskFactor.setDescription("Impossible travel pattern detected");
                riskFactor.setDetails(analysis.getMetadata());
                result.getRiskFactors().put("IMPOSSIBLE_TRAVEL", riskFactor);
            } else if (analysis.getActualSpeed() > 800) {
                GeographicAnomaly anomaly = new GeographicAnomaly();
                anomaly.setAnomalyType("HIGH_SPEED_TRAVEL");
                anomaly.setSeverity("HIGH");
                anomaly.setDescription("High-speed travel detected (possible flight)");
                anomaly.setConfidence(0.8);
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setEvidence(Map.of(
                    "actualSpeed", analysis.getActualSpeed(),
                    "travelType", "LIKELY_FLIGHT"
                ));
                result.getDetectedAnomalies().add(anomaly);
            }
        }
    }
    
    private void assessTimeZoneConsistency(
        GeographicRiskRequest request,
        GeographicRiskResult result,
        LocationData location
    ) {
        if (location == null || request.getTimeZone() == null) {
            return;
        }
        
        TimeZoneAnalysisResult analysis = timeZoneAnalysisService.analyzeTimeZone(
            location.getLatitude(), location.getLongitude(), 
            location.getTimezone(), request.getTimeZone()
        );
        
        if (!analysis.isConsistent()) {
            GeographicAnomaly anomaly = new GeographicAnomaly();
            anomaly.setAnomalyType("TIMEZONE_MISMATCH");
            anomaly.setSeverity("MEDIUM");
            anomaly.setDescription("Timezone mismatch detected");
            anomaly.setConfidence(analysis.getConfidence());
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of(
                "expectedTimezone", analysis.getExpectedTimezone(),
                "declaredTimezone", request.getTimeZone(),
                "offsetDifference", analysis.getOffsetDifference()
            ));
            result.getDetectedAnomalies().add(anomaly);
            
            RiskFactor riskFactor = new RiskFactor();
            riskFactor.setFactorName("TIMEZONE_INCONSISTENCY");
            riskFactor.setScore(0.6);
            riskFactor.setWeight(0.1);
            riskFactor.setDescription("Timezone inconsistency detected");
            riskFactor.setDetails(analysis.getMetadata());
            result.getRiskFactors().put("TIMEZONE_INCONSISTENCY", riskFactor);
        }
    }
    
    private void assessGeofencing(
        GeographicRiskRequest request,
        GeographicRiskResult result,
        LocationData location
    ) {
        if (location == null) {
            return;
        }
        
        GeofencingAnalysis analysis = geoFencingService.checkGeofences(
            location.getLatitude(), location.getLongitude(), 
            request.getContext()
        );
        
        GeofencingResult geofencingResult = new GeofencingResult();
        geofencingResult.setWithinAllowedZone(analysis.isWithinAllowedZone());
        geofencingResult.setInRestrictedZone(analysis.isInRestrictedZone());
        geofencingResult.setViolatedZones(analysis.getViolatedZones());
        geofencingResult.setNearestAllowedZone(analysis.getNearestAllowedZone());
        geofencingResult.setDistanceToNearestZone(analysis.getDistanceToNearestZone());
        geofencingResult.setZoneMetadata(analysis.getMetadata());
        result.setGeofencingResult(geofencingResult);
        
        if (analysis.isInRestrictedZone()) {
            GeographicAnomaly anomaly = new GeographicAnomaly();
            anomaly.setAnomalyType("RESTRICTED_ZONE_ACCESS");
            anomaly.setSeverity("HIGH");
            anomaly.setDescription("Access from restricted geographical zone");
            anomaly.setConfidence(1.0);
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of(
                "restrictedZones", analysis.getViolatedZones(),
                "zoneTypes", analysis.getZoneTypes()
            ));
            anomaly.setActionRequired(true);
            result.getDetectedAnomalies().add(anomaly);
        }
        
        if (!analysis.isWithinAllowedZone() && analysis.getDistanceToNearestZone() > 100) {
            GeographicAnomaly anomaly = new GeographicAnomaly();
            anomaly.setAnomalyType("OUTSIDE_ALLOWED_ZONE");
            anomaly.setSeverity("MEDIUM");
            anomaly.setDescription("Access from outside allowed geographical zones");
            anomaly.setConfidence(0.9);
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setEvidence(Map.of(
                "distanceToNearestZone", analysis.getDistanceToNearestZone(),
                "nearestZone", analysis.getNearestAllowedZone()
            ));
            result.getDetectedAnomalies().add(anomaly);
        }
    }
    
    private void assessDistanceFromUsual(
        GeographicRiskRequest request,
        GeographicRiskResult result,
        LocationData location
    ) {
        if (location == null || request.getUserId() == null) {
            return;
        }
        
        UserLocation usualLocation = userLocationRepository
            .findUsualLocationByUserId(request.getUserId());
        
        if (usualLocation != null) {
            double distance = travelAnalysisService.calculateDistance(
                usualLocation.getLatitude(), usualLocation.getLongitude(),
                location.getLatitude(), location.getLongitude()
            );
            
            if (result.getLocationAnalysis() == null) {
                result.setLocationAnalysis(new LocationAnalysis());
            }
            result.getLocationAnalysis().setDistanceFromUsual(distance);
            
            if (distance > 1000) {
                GeographicAnomaly anomaly = new GeographicAnomaly();
                anomaly.setAnomalyType("FAR_FROM_USUAL_LOCATION");
                anomaly.setSeverity(distance > 5000 ? "HIGH" : "MEDIUM");
                anomaly.setDescription(String.format("Location is %.2f km from usual location", distance));
                anomaly.setConfidence(0.8);
                anomaly.setDetectedAt(LocalDateTime.now());
                anomaly.setEvidence(Map.of(
                    "distance", distance,
                    "usualLocation", usualLocation.getCity() + ", " + usualLocation.getCountry(),
                    "currentLocation", location.getCity() + ", " + location.getCountry()
                ));
                result.getDetectedAnomalies().add(anomaly);
                
                RiskFactor riskFactor = new RiskFactor();
                riskFactor.setFactorName("DISTANCE_FROM_USUAL");
                riskFactor.setScore(Math.min(1.0, distance / 10000.0));
                riskFactor.setWeight(0.2);
                riskFactor.setDescription("Distance from usual location");
                riskFactor.setDetails(Map.of("distance", distance));
                result.getRiskFactors().put("DISTANCE_FROM_USUAL", riskFactor);
            }
            
            if (distance > 100 && !usualLocation.getCountry().equals(location.getCountry())) {
                result.getLocationAnalysis().setNewLocationDetected(true);
            }
        }
    }
    
    private void assessVpnProxyUsage(
        GeographicRiskRequest request,
        GeographicRiskResult result,
        LocationData location
    ) {
        if (location == null) {
            return;
        }
        
        boolean vpnUsage = Boolean.TRUE.equals(location.getVpnDetected());
        boolean proxyUsage = Boolean.TRUE.equals(location.getProxyDetected());
        
        if (vpnUsage || proxyUsage) {
            RiskFactor riskFactor = new RiskFactor();
            riskFactor.setFactorName("VPN_PROXY_USAGE");
            riskFactor.setScore(vpnUsage ? 0.7 : 0.5);
            riskFactor.setWeight(0.15);
            riskFactor.setDescription("VPN or proxy usage detected");
            riskFactor.setDetails(Map.of(
                "vpnDetected", vpnUsage,
                "proxyDetected", proxyUsage,
                "isp", location.getIsp(),
                "organization", location.getOrganization()
            ));
            result.getRiskFactors().put("VPN_PROXY_USAGE", riskFactor);
        }
    }
    
    private void calculateOverallRiskScore(GeographicRiskResult result) {
        double totalScore = 0.0;
        double totalWeight = 0.0;
        
        for (RiskFactor factor : result.getRiskFactors().values()) {
            totalScore += factor.getScore() * factor.getWeight();
            totalWeight += factor.getWeight();
        }
        
        if (totalWeight > 0) {
            totalScore = totalScore / totalWeight;
        }
        
        for (GeographicAnomaly anomaly : result.getDetectedAnomalies()) {
            if ("CRITICAL".equals(anomaly.getSeverity())) {
                totalScore = Math.max(totalScore, 0.9);
            } else if ("HIGH".equals(anomaly.getSeverity())) {
                totalScore += 0.2;
            } else if ("MEDIUM".equals(anomaly.getSeverity())) {
                totalScore += 0.1;
            }
        }
        
        for (ComplianceViolation violation : result.getComplianceViolations()) {
            if ("CRITICAL".equals(violation.getSeverity())) {
                totalScore = 1.0;
                break;
            }
        }
        
        result.setOverallRiskScore(Math.min(1.0, totalScore));
        result.setRiskLevel(determineRiskLevel(result.getOverallRiskScore()));
    }
    
    private void makeRiskDecision(GeographicRiskResult result) {
        RiskDecision decision = new RiskDecision();
        decision.setRequiredActions(new ArrayList<>());
        decision.setParameters(new HashMap<>());
        
        double score = result.getOverallRiskScore();
        boolean hasCriticalViolations = result.getComplianceViolations().stream()
            .anyMatch(v -> "CRITICAL".equals(v.getSeverity()));
        
        if (hasCriticalViolations || score >= CRITICAL_RISK_THRESHOLD) {
            decision.setDecision("BLOCK");
            decision.setReasoning("Critical geographic risk or compliance violation detected");
            decision.setConfidence(0.95);
            decision.getRequiredActions().add("BLOCK_TRANSACTION");
            decision.getRequiredActions().add("ALERT_COMPLIANCE");
            decision.getRequiredActions().add("FREEZE_ACCOUNT");
            decision.setRequiresManualReview(true);
        } else if (score >= HIGH_RISK_THRESHOLD) {
            decision.setDecision("REQUIRE_VERIFICATION");
            decision.setReasoning("High geographic risk requires additional verification");
            decision.setConfidence(0.85);
            decision.getRequiredActions().add("ENHANCED_KYC");
            decision.getRequiredActions().add("MANUAL_REVIEW");
            decision.getRequiredActions().add("DOCUMENT_LOCATION");
            decision.setRequiresManualReview(true);
        } else if (score >= MEDIUM_RISK_THRESHOLD) {
            decision.setDecision("MONITOR");
            decision.setReasoning("Medium geographic risk detected");
            decision.setConfidence(0.75);
            decision.getRequiredActions().add("ENHANCED_MONITORING");
            decision.getRequiredActions().add("LOCATION_VERIFICATION");
            decision.setRequiresManualReview(false);
        } else if (score >= LOW_RISK_THRESHOLD) {
            decision.setDecision("ALLOW_WITH_MONITORING");
            decision.setReasoning("Low geographic risk detected");
            decision.setConfidence(0.80);
            decision.getRequiredActions().add("STANDARD_MONITORING");
            decision.setRequiresManualReview(false);
        } else {
            decision.setDecision("ALLOW");
            decision.setReasoning("Minimal geographic risk");
            decision.setConfidence(0.90);
            decision.setRequiresManualReview(false);
        }
        
        result.setDecision(decision);
    }
    
    private void generateMitigations(GeographicRiskResult result) {
        result.setMitigations(new ArrayList<>());
        
        if (result.getOverallRiskScore() >= HIGH_RISK_THRESHOLD) {
            RiskMitigation mitigation = new RiskMitigation();
            mitigation.setType("LOCATION_VERIFICATION");
            mitigation.setPriority("HIGH");
            mitigation.setDescription("Verify user location through multiple methods");
            mitigation.setConfiguration(Map.of(
                "methods", Arrays.asList("GPS", "CELL_TOWER", "WIFI", "MANUAL_CONFIRMATION"),
                "requiredConfidence", 0.8
            ));
            mitigation.setExpectedRiskReduction(0.4);
            mitigation.setAutomated(false);
            result.getMitigations().add(mitigation);
        }
        
        for (GeographicAnomaly anomaly : result.getDetectedAnomalies()) {
            if ("LOCATION_SPOOFING".equals(anomaly.getAnomalyType())) {
                RiskMitigation mitigation = new RiskMitigation();
                mitigation.setType("ANTI_SPOOFING");
                mitigation.setPriority("CRITICAL");
                mitigation.setDescription("Deploy anti-spoofing countermeasures");
                mitigation.setConfiguration(Map.of(
                    "method", "MULTI_FACTOR_LOCATION",
                    "verification", "BIOMETRIC_PLUS_LOCATION"
                ));
                mitigation.setExpectedRiskReduction(0.7);
                mitigation.setAutomated(true);
                result.getMitigations().add(mitigation);
                break;
            }
        }
        
        if (!result.getComplianceViolations().isEmpty()) {
            RiskMitigation mitigation = new RiskMitigation();
            mitigation.setType("COMPLIANCE_WORKFLOW");
            mitigation.setPriority("CRITICAL");
            mitigation.setDescription("Initiate compliance investigation workflow");
            mitigation.setConfiguration(Map.of(
                "workflow", "SANCTIONS_INVESTIGATION",
                "escalation", "IMMEDIATE"
            ));
            mitigation.setExpectedRiskReduction(0.9);
            mitigation.setAutomated(true);
            result.getMitigations().add(mitigation);
        }
    }
    
    private void updateUserLocationHistory(GeographicRiskRequest request, LocationData location) {
        if (location != null && request.getUserId() != null) {
            UserLocation userLocation = new UserLocation();
            userLocation.setUserId(request.getUserId());
            userLocation.setSessionId(request.getSessionId());
            userLocation.setLatitude(location.getLatitude());
            userLocation.setLongitude(location.getLongitude());
            userLocation.setCountry(location.getCountry());
            userLocation.setCountryCode(location.getCountryCode());
            userLocation.setCity(location.getCity());
            userLocation.setRegion(location.getRegion());
            userLocation.setTimezone(location.getTimezone());
            userLocation.setIpAddress(request.getIpAddress());
            userLocation.setAccuracy(location.getAccuracy());
            userLocation.setSource(location.getSource());
            userLocation.setVpnDetected(location.getVpnDetected());
            userLocation.setProxyDetected(location.getProxyDetected());
            userLocation.setTimestamp(request.getTimestamp());
            
            userLocationRepository.save(userLocation);
        }
    }
    
    private String determineRiskLevel(double score) {
        if (score >= CRITICAL_RISK_THRESHOLD) return "CRITICAL";
        if (score >= HIGH_RISK_THRESHOLD) return "HIGH";
        if (score >= MEDIUM_RISK_THRESHOLD) return "MEDIUM";
        if (score >= LOW_RISK_THRESHOLD) return "LOW";
        return "MINIMAL";
    }
    
    private void updateCountryRiskScores() {
        log.info("Updating country risk scores...");
        try {
            List<CountryRisk> countryRisks = countryRiskRepository.findAll();
            for (CountryRisk risk : countryRisks) {
                COUNTRY_RISK_SCORES.put(risk.getCountryCode(), risk.getRiskScore());
            }
        } catch (Exception e) {
            log.error("Failed to update country risk scores", e);
        }
    }
    
    private void updateSanctionLists() {
        log.info("Updating sanction lists...");
        try {
            List<SanctionListEntry> entries = sanctionListRepository.findActiveSanctions();
            SANCTIONED_COUNTRIES.clear();
            for (SanctionListEntry entry : entries) {
                if (entry.getCountryCode() != null) {
                    SANCTIONED_COUNTRIES.add(entry.getCountryCode());
                }
            }
        } catch (Exception e) {
            log.error("Failed to update sanction lists", e);
        }
    }
    
    private void cleanupOldLocationData() {
        log.info("Cleaning up old location data...");
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
            userLocationRepository.deleteByTimestampBefore(cutoff);
        } catch (Exception e) {
            log.error("Failed to cleanup old location data", e);
        }
    }
    
    private void handleCriticalRisk(GeographicRiskRequest request, GeographicRiskResult result) {
        log.error("CRITICAL GEOGRAPHIC RISK - User: {}, Score: {}, Violations: {}", 
            request.getUserId(), result.getOverallRiskScore(), result.getComplianceViolations().size());
        
        sendCriticalRiskAlert(request, result);
    }
    
    private void handleHighRisk(GeographicRiskRequest request, GeographicRiskResult result) {
        log.warn("HIGH GEOGRAPHIC RISK - User: {}, Score: {}", 
            request.getUserId(), result.getOverallRiskScore());
        
        sendHighRiskNotification(request, result);
    }
    
    private void handleMediumRisk(GeographicRiskRequest request, GeographicRiskResult result) {
        log.info("MEDIUM GEOGRAPHIC RISK - User: {}, Score: {}", 
            request.getUserId(), result.getOverallRiskScore());
    }
    
    private void handleLowRisk(GeographicRiskRequest request, GeographicRiskResult result) {
        log.info("LOW GEOGRAPHIC RISK - User: {}, Score: {}", 
            request.getUserId(), result.getOverallRiskScore());
    }
    
    private void sendCriticalRiskAlert(GeographicRiskRequest request, GeographicRiskResult result) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("CRITICAL_GEOGRAPHIC_RISK")
            .priority("URGENT")
            .recipient("compliance-team")
            .subject("Critical Geographic Risk Alert")
            .templateData(Map.of(
                "userId", request.getUserId(),
                "riskScore", result.getOverallRiskScore(),
                "location", result.getLocationAnalysis() != null ? 
                    result.getLocationAnalysis().getPrimaryLocation() : "Unknown",
                "anomalies", result.getDetectedAnomalies().stream()
                    .filter(a -> "CRITICAL".equals(a.getSeverity()))
                    .map(a -> a.getDescription())
                    .collect(Collectors.toList()),
                "violations", result.getComplianceViolations().stream()
                    .map(v -> v.getDescription())
                    .collect(Collectors.toList()),
                "decision", result.getDecision().getDecision()
            ))
            .channels(Arrays.asList("EMAIL", "SLACK", "PAGERDUTY"))
            .build();
        
        notificationService.send(template);
    }
    
    private void sendHighRiskNotification(GeographicRiskRequest request, GeographicRiskResult result) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("HIGH_GEOGRAPHIC_RISK")
            .priority("HIGH")
            .recipient("risk-team")
            .subject("High Geographic Risk Detected")
            .templateData(Map.of(
                "userId", request.getUserId(),
                "riskScore", result.getOverallRiskScore(),
                "location", result.getLocationAnalysis() != null ? 
                    result.getLocationAnalysis().getPrimaryLocation() : "Unknown",
                "riskFactors", result.getRiskFactors().keySet()
            ))
            .channels(Arrays.asList("EMAIL", "SLACK"))
            .build();
        
        notificationService.send(template);
    }
    
    private void persistAssessmentResult(GeographicRiskRequest request, GeographicRiskResult result) {
        GeographicRisk risk = new GeographicRisk();
        risk.setAssessmentId(result.getAssessmentId());
        risk.setUserId(request.getUserId());
        risk.setSessionId(request.getSessionId());
        risk.setIpAddress(request.getIpAddress());
        risk.setCountry(request.getCurrentLocation() != null ? 
            request.getCurrentLocation().getCountry() : null);
        risk.setCountryCode(request.getCurrentLocation() != null ? 
            request.getCurrentLocation().getCountryCode() : null);
        risk.setCity(request.getCurrentLocation() != null ? 
            request.getCurrentLocation().getCity() : null);
        risk.setLatitude(request.getCurrentLocation() != null ? 
            request.getCurrentLocation().getLatitude() : null);
        risk.setLongitude(request.getCurrentLocation() != null ? 
            request.getCurrentLocation().getLongitude() : null);
        risk.setOverallRiskScore(result.getOverallRiskScore());
        risk.setRiskLevel(result.getRiskLevel());
        risk.setDecision(result.getDecision().getDecision());
        risk.setRiskFactors(objectMapper.convertValue(result.getRiskFactors(), Map.class));
        risk.setAnomalies(objectMapper.convertValue(result.getDetectedAnomalies(), List.class));
        risk.setViolations(objectMapper.convertValue(result.getComplianceViolations(), List.class));
        risk.setMitigations(objectMapper.convertValue(result.getMitigations(), List.class));
        risk.setAssessedAt(result.getAssessedAt());
        risk.setProcessingTimeMs(result.getProcessingTimeMs());
        
        geographicRiskRepository.save(risk);
        
        String cacheKey = "geographic:risk:" + request.getUserId() + ":" + request.getSessionId();
        redisCache.set(cacheKey, result, Duration.ofHours(6));
    }
    
    private void publishGeographicEvents(GeographicRiskResult result) {
        kafkaTemplate.send("geographic-risk-results", result.getUserId(), result);
        
        if (result.getOverallRiskScore() >= HIGH_RISK_THRESHOLD) {
            kafkaTemplate.send("geographic-alerts", result.getUserId(), result);
        }
        
        if (!result.getComplianceViolations().isEmpty()) {
            kafkaTemplate.send("compliance-violations", result.getUserId(), result);
        }
        
        if (result.getDecision().getRequiresManualReview()) {
            kafkaTemplate.send("geographic-review-queue", result.getUserId(), result);
        }
    }
    
    private void updateMetrics(GeographicRiskResult result, long processingTime) {
        metricsService.recordGeographicRiskScore(result.getOverallRiskScore());
        metricsService.recordGeographicRiskLevel(result.getRiskLevel());
        metricsService.recordGeographicDecision(result.getDecision().getDecision());
        metricsService.recordAnomalyCount(result.getDetectedAnomalies().size());
        metricsService.recordComplianceViolationCount(result.getComplianceViolations().size());
        metricsService.recordProcessingTime("geographic-risk-assessment", processingTime);
        
        result.getRiskFactors().forEach((factor, score) -> 
            metricsService.recordRiskFactor(factor, score.getScore())
        );
        
        metricsService.incrementCounter("geographic.assessments.total");
        metricsService.incrementCounter("geographic.risk." + result.getRiskLevel().toLowerCase());
        
        if (!result.getComplianceViolations().isEmpty()) {
            metricsService.incrementCounter("geographic.compliance.violations");
        }
    }
    
    private void handleProcessingTimeout(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        metricsService.incrementCounter("geographic.risk.assessment.timeouts");
        sendToDLQ(record, "Processing timeout");
        acknowledgment.acknowledge();
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Acknowledgment acknowledgment, Exception error) {
        metricsService.incrementCounter("geographic.risk.assessment.errors");
        log.error("Failed to process geographic risk assessment: {}", record.key(), error);
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