package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.cache.RedisCache;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.NotificationTemplate;
import com.waqiti.security.dto.*;
import com.waqiti.security.model.*;
import com.waqiti.security.service.*;
import com.waqiti.security.repository.*;
import com.waqiti.security.util.SecurityUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuspiciousLoginDetectionConsumer {

    private static final String TOPIC = "suspicious-login-detection";
    private static final String DLQ_TOPIC = "suspicious-login-detection.dlq";
    private static final int MAX_RETRIES = 3;
    private static final long PROCESSING_TIMEOUT_MS = 10000;
    private static final double CRITICAL_RISK_THRESHOLD = 0.85;
    private static final double HIGH_RISK_THRESHOLD = 0.70;
    private static final double MEDIUM_RISK_THRESHOLD = 0.50;
    private static final double LOW_RISK_THRESHOLD = 0.30;
    
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SecurityEventRepository securityEventRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final UserProfileRepository userProfileRepository;
    private final DeviceRepository deviceRepository;
    private final IpReputationService ipReputationService;
    private final GeoLocationService geoLocationService;
    private final DeviceFingerprintingService deviceFingerprintingService;
    private final BehavioralAnalysisService behavioralAnalysisService;
    private final AuthenticationService authenticationService;
    private final SecurityOrchestrationService securityOrchestrationService;
    private final MachineLearningService machineLearningService;
    private final NotificationService notificationService;
    
    private final MetricsService metricsService;
    private final RedisCache redisCache;
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    
    private static final Map<String, Pattern> SUSPICIOUS_PATTERNS = new HashMap<>();
    private static final Map<String, Double> RISK_WEIGHTS = new HashMap<>();
    
    static {
        SUSPICIOUS_PATTERNS.put("SQL_INJECTION", Pattern.compile("('.+--)|(<script)|(%3Cscript)|union.*select|drop.*table", Pattern.CASE_INSENSITIVE));
        SUSPICIOUS_PATTERNS.put("XSS_ATTEMPT", Pattern.compile("<[^>]*(script|iframe|object|embed|applet)[^>]*>", Pattern.CASE_INSENSITIVE));
        SUSPICIOUS_PATTERNS.put("PATH_TRAVERSAL", Pattern.compile("(\\.\\./)|(%2e%2e%2f)|(\\.\\\\)|(%2e%5c)", Pattern.CASE_INSENSITIVE));
        SUSPICIOUS_PATTERNS.put("COMMAND_INJECTION", Pattern.compile("(;|\\||&|\\$|`|\\(|\\)|<|>|\\\\n|\\\\r)", Pattern.CASE_INSENSITIVE));
        SUSPICIOUS_PATTERNS.put("BOT_PATTERN", Pattern.compile("(bot|crawler|spider|scraper|scan)", Pattern.CASE_INSENSITIVE));
        
        RISK_WEIGHTS.put("LOCATION_ANOMALY", 0.25);
        RISK_WEIGHTS.put("TIME_ANOMALY", 0.15);
        RISK_WEIGHTS.put("DEVICE_ANOMALY", 0.20);
        RISK_WEIGHTS.put("BEHAVIORAL_ANOMALY", 0.20);
        RISK_WEIGHTS.put("VELOCITY_ANOMALY", 0.10);
        RISK_WEIGHTS.put("IP_REPUTATION", 0.10);
    }
    
    public SuspiciousLoginDetectionConsumer(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            SecurityEventRepository securityEventRepository,
            LoginAttemptRepository loginAttemptRepository,
            UserProfileRepository userProfileRepository,
            DeviceRepository deviceRepository,
            IpReputationService ipReputationService,
            GeoLocationService geoLocationService,
            DeviceFingerprintingService deviceFingerprintingService,
            BehavioralAnalysisService behavioralAnalysisService,
            AuthenticationService authenticationService,
            SecurityOrchestrationService securityOrchestrationService,
            MachineLearningService machineLearningService,
            NotificationService notificationService,
            MetricsService metricsService,
            RedisCache redisCache) {
        
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.securityEventRepository = securityEventRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.userProfileRepository = userProfileRepository;
        this.deviceRepository = deviceRepository;
        this.ipReputationService = ipReputationService;
        this.geoLocationService = geoLocationService;
        this.deviceFingerprintingService = deviceFingerprintingService;
        this.behavioralAnalysisService = behavioralAnalysisService;
        this.authenticationService = authenticationService;
        this.securityOrchestrationService = securityOrchestrationService;
        this.machineLearningService = machineLearningService;
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
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("suspicious-login-detection");
        
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(MAX_RETRIES)
            .waitDuration(Duration.ofSeconds(2))
            .retryExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class, IllegalStateException.class)
            .build();
            
        this.retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry("suspicious-login-detection");
        
        this.executorService = Executors.newFixedThreadPool(10);
        this.scheduledExecutorService = Executors.newScheduledThreadPool(5);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker state transition: {}", event.getStateTransition()));
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginAttemptRequest {
        private String userId;
        private String sessionId;
        private String username;
        private String email;
        private String ipAddress;
        private String userAgent;
        private String deviceId;
        private String deviceFingerprint;
        private Map<String, Object> deviceInfo;
        private String authMethod;
        private boolean mfaUsed;
        private String mfaType;
        private GeoLocation location;
        private LocalDateTime timestamp;
        private String referrer;
        private String origin;
        private Map<String, String> headers;
        private LoginContext context;
        private List<String> previousIps;
        private List<String> previousDevices;
        private LoginBehavior behavior;
        private String correlationId;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocation {
        private String country;
        private String countryCode;
        private String city;
        private String region;
        private String postalCode;
        private Double latitude;
        private Double longitude;
        private String timezone;
        private String isp;
        private String org;
        private String asn;
        private boolean vpnDetected;
        private boolean proxyDetected;
        private boolean torDetected;
        private boolean hostingDetected;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginContext {
        private String application;
        private String platform;
        private String version;
        private String environment;
        private boolean apiAccess;
        private boolean webAccess;
        private boolean mobileAccess;
        private String clientType;
        private Map<String, String> metadata;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginBehavior {
        private Long typingSpeed;
        private Long mouseMovements;
        private Long touchPatterns;
        private List<Long> keystrokeDynamics;
        private Long timeToComplete;
        private Integer passwordAttempts;
        private boolean copyPaste;
        private boolean autoFill;
        private Map<String, Object> biometrics;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousLoginAnalysis {
        private String analysisId;
        private String userId;
        private String sessionId;
        private LocalDateTime timestamp;
        private LoginRiskScore riskScore;
        private List<SuspiciousIndicator> indicators;
        private List<AnomalyDetection> anomalies;
        private SecurityRecommendation recommendation;
        private AutomatedResponse response;
        private boolean requiresManualReview;
        private String reviewStatus;
        private Map<String, Object> evidence;
        private List<String> mitigationActions;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRiskScore {
        private double overallScore;
        private double locationScore;
        private double timeScore;
        private double deviceScore;
        private double behaviorScore;
        private double velocityScore;
        private double reputationScore;
        private String riskLevel;
        private String confidence;
        private Map<String, Double> factorWeights;
        private List<String> contributingFactors;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousIndicator {
        private String type;
        private String severity;
        private String description;
        private double weight;
        private Map<String, Object> details;
        private LocalDateTime detectedAt;
        private String source;
        private boolean confirmed;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyDetection {
        private String anomalyType;
        private String anomalyClass;
        private double deviation;
        private double zscore;
        private String baseline;
        private String observed;
        private double confidence;
        private String algorithm;
        private Map<String, Object> parameters;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityRecommendation {
        private String recommendationType;
        private String priority;
        private List<String> actions;
        private Map<String, Object> configuration;
        private String reasoning;
        private double expectedRiskReduction;
        private LocalDateTime expiresAt;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutomatedResponse {
        private String responseType;
        private String status;
        private List<String> actionsTaken;
        private LocalDateTime executedAt;
        private boolean requiresConfirmation;
        private Map<String, Object> results;
        private String rollbackPlan;
    }
    
    @KafkaListener(
        topics = TOPIC,
        groupId = "security-service-group",
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
            log.info("Processing suspicious login detection event: {} with correlation ID: {}", 
                record.key(), correlationId);
            
            LoginAttemptRequest request = deserializeMessage(record.value());
            validateRequest(request);
            
            CompletableFuture<SuspiciousLoginAnalysis> analysisFuture = CompletableFuture
                .supplyAsync(() -> executeWithResilience(() -> 
                    analyzeLoginAttempt(request, correlationId)), executorService)
                .orTimeout(PROCESSING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
            SuspiciousLoginAnalysis analysis = analysisFuture.join();
            
            if (analysis.getRiskScore().getOverallScore() >= CRITICAL_RISK_THRESHOLD) {
                handleCriticalRiskLogin(request, analysis);
            } else if (analysis.getRiskScore().getOverallScore() >= HIGH_RISK_THRESHOLD) {
                handleHighRiskLogin(request, analysis);
            } else if (analysis.getRiskScore().getOverallScore() >= MEDIUM_RISK_THRESHOLD) {
                handleMediumRiskLogin(request, analysis);
            } else if (analysis.getRiskScore().getOverallScore() >= LOW_RISK_THRESHOLD) {
                handleLowRiskLogin(request, analysis);
            }
            
            persistAnalysisResults(request, analysis);
            publishAnalysisEvents(analysis);
            updateMetrics(analysis, System.currentTimeMillis() - startTime);
            
            acknowledgment.acknowledge();
            
        } catch (TimeoutException e) {
            log.error("Timeout processing suspicious login detection for key: {}", record.key(), e);
            handleProcessingTimeout(record, acknowledgment);
        } catch (Exception e) {
            log.error("Error processing suspicious login detection for key: {}", record.key(), e);
            handleProcessingError(record, acknowledgment, e);
        }
    }
    
    private LoginAttemptRequest deserializeMessage(String message) {
        try {
            return objectMapper.readValue(message, LoginAttemptRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize login attempt request", e);
        }
    }
    
    private void validateRequest(LoginAttemptRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getUserId() == null && request.getUsername() == null && request.getEmail() == null) {
            errors.add("User identification required (userId, username, or email)");
        }
        if (request.getIpAddress() == null || request.getIpAddress().isEmpty()) {
            errors.add("IP address is required");
        }
        if (request.getTimestamp() == null) {
            errors.add("Timestamp is required");
        }
        if (request.getAuthMethod() == null || request.getAuthMethod().isEmpty()) {
            errors.add("Authentication method is required");
        }
        
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", errors));
        }
    }
    
    private <T> T executeWithResilience(Supplier<T> supplier) {
        return Retry.decorateSupplier(retry,
            CircuitBreaker.decorateSupplier(circuitBreaker, supplier)).get();
    }
    
    private SuspiciousLoginAnalysis analyzeLoginAttempt(
        LoginAttemptRequest request,
        String correlationId
    ) {
        SuspiciousLoginAnalysis analysis = new SuspiciousLoginAnalysis();
        analysis.setAnalysisId(UUID.randomUUID().toString());
        analysis.setUserId(request.getUserId());
        analysis.setSessionId(request.getSessionId());
        analysis.setTimestamp(LocalDateTime.now());
        
        List<CompletableFuture<Void>> analysisTask = Arrays.asList(
            CompletableFuture.runAsync(() -> 
                analyzeLocationAnomaly(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeTimeAnomaly(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeDeviceAnomaly(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeBehavioralAnomaly(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeVelocityAnomaly(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeIpReputation(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                detectInjectionAttempts(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                detectBotPatterns(request, analysis), executorService),
            CompletableFuture.runAsync(() -> 
                analyzeAuthenticationPattern(request, analysis), executorService)
        );

        try {
            CompletableFuture.allOf(analysisTask.toArray(new CompletableFuture[0]))
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Suspicious login analysis timed out after 10 seconds for user: {}", request.getUserId(), e);
            analysisTask.forEach(task -> task.cancel(true));
        } catch (Exception e) {
            log.error("Suspicious login analysis failed for user: {}", request.getUserId(), e);
        }

        calculateOverallRiskScore(analysis);
        generateSecurityRecommendation(analysis);
        determineAutomatedResponse(request, analysis);
        
        return analysis;
    }
    
    private void analyzeLocationAnomaly(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) {
            analysis.setIndicators(new ArrayList<>());
        }
        if (analysis.getAnomalies() == null) {
            analysis.setAnomalies(new ArrayList<>());
        }
        
        UserProfile profile = userProfileRepository.findByUserId(request.getUserId())
            .orElse(new UserProfile());
        
        GeoLocation currentLocation = request.getLocation();
        if (currentLocation == null) {
            currentLocation = geoLocationService.getLocationByIp(request.getIpAddress());
        }
        
        List<LoginAttempt> recentLogins = loginAttemptRepository
            .findRecentByUserId(request.getUserId(), 30);
        
        if (!recentLogins.isEmpty()) {
            LoginAttempt lastLogin = recentLogins.get(0);
            GeoLocation lastLocation = lastLogin.getLocation();
            
            double distance = calculateDistance(
                lastLocation.getLatitude(), lastLocation.getLongitude(),
                currentLocation.getLatitude(), currentLocation.getLongitude()
            );
            
            Duration timeDiff = Duration.between(
                lastLogin.getTimestamp(), 
                request.getTimestamp()
            );
            
            double maxPossibleDistance = timeDiff.toHours() * 900;
            
            if (distance > maxPossibleDistance) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("IMPOSSIBLE_TRAVEL");
                indicator.setSeverity("HIGH");
                indicator.setDescription(String.format(
                    "Impossible travel detected: %.2f km in %.2f hours",
                    distance, timeDiff.toHours()
                ));
                indicator.setWeight(0.9);
                indicator.setDetails(Map.of(
                    "distance", distance,
                    "timeDiff", timeDiff.toHours(),
                    "maxPossible", maxPossibleDistance,
                    "lastLocation", lastLocation,
                    "currentLocation", currentLocation
                ));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("LOCATION_ANALYSIS");
                indicator.setConfirmed(true);
                analysis.getIndicators().add(indicator);
            }
            
            if (!currentLocation.getCountry().equals(profile.getUsualCountry())) {
                AnomalyDetection anomaly = new AnomalyDetection();
                anomaly.setAnomalyType("LOCATION_COUNTRY");
                anomaly.setAnomalyClass("GEOGRAPHIC");
                anomaly.setBaseline(profile.getUsualCountry());
                anomaly.setObserved(currentLocation.getCountry());
                anomaly.setDeviation(1.0);
                anomaly.setConfidence(0.85);
                anomaly.setAlgorithm("GEOGRAPHIC_BASELINE");
                analysis.getAnomalies().add(anomaly);
            }
        }
        
        if (currentLocation.isVpnDetected() || currentLocation.isProxyDetected() || 
            currentLocation.isTorDetected() || currentLocation.isHostingDetected()) {
            SuspiciousIndicator indicator = new SuspiciousIndicator();
            indicator.setType("ANONYMIZATION_DETECTED");
            indicator.setSeverity("MEDIUM");
            indicator.setDescription("Login attempt from anonymized connection");
            indicator.setWeight(0.7);
            indicator.setDetails(Map.of(
                "vpn", currentLocation.isVpnDetected(),
                "proxy", currentLocation.isProxyDetected(),
                "tor", currentLocation.isTorDetected(),
                "hosting", currentLocation.isHostingDetected()
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setSource("NETWORK_ANALYSIS");
            analysis.getIndicators().add(indicator);
        }
    }
    
    private void analyzeTimeAnomaly(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) {
            analysis.setIndicators(new ArrayList<>());
        }
        if (analysis.getAnomalies() == null) {
            analysis.setAnomalies(new ArrayList<>());
        }
        
        UserProfile profile = userProfileRepository.findByUserId(request.getUserId())
            .orElse(new UserProfile());
        
        LocalTime loginTime = request.getTimestamp().toLocalTime();
        DayOfWeek loginDay = request.getTimestamp().getDayOfWeek();
        
        Map<String, Object> usualPattern = profile.getUsualLoginPattern();
        if (usualPattern != null) {
            List<Integer> usualHours = (List<Integer>) usualPattern.get("hours");
            List<String> usualDays = (List<String>) usualPattern.get("days");
            
            if (usualHours != null && !usualHours.contains(loginTime.getHour())) {
                double deviation = calculateTimeDeviation(loginTime.getHour(), usualHours);
                
                if (deviation > 6) {
                    AnomalyDetection anomaly = new AnomalyDetection();
                    anomaly.setAnomalyType("TIME_UNUSUAL_HOUR");
                    anomaly.setAnomalyClass("TEMPORAL");
                    anomaly.setDeviation(deviation);
                    anomaly.setZscore(calculateZScore(loginTime.getHour(), usualHours));
                    anomaly.setBaseline(usualHours.toString());
                    anomaly.setObserved(String.valueOf(loginTime.getHour()));
                    anomaly.setConfidence(0.75);
                    anomaly.setAlgorithm("TIME_PATTERN_ANALYSIS");
                    analysis.getAnomalies().add(anomaly);
                }
            }
            
            if (usualDays != null && !usualDays.contains(loginDay.toString())) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("UNUSUAL_DAY");
                indicator.setSeverity("LOW");
                indicator.setDescription("Login on unusual day: " + loginDay);
                indicator.setWeight(0.3);
                indicator.setDetails(Map.of(
                    "usualDays", usualDays,
                    "loginDay", loginDay.toString()
                ));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("TEMPORAL_ANALYSIS");
                analysis.getIndicators().add(indicator);
            }
        }
        
        if (loginTime.getHour() >= 2 && loginTime.getHour() <= 5) {
            SuspiciousIndicator indicator = new SuspiciousIndicator();
            indicator.setType("LATE_NIGHT_LOGIN");
            indicator.setSeverity("LOW");
            indicator.setDescription("Login during unusual hours (2AM-5AM)");
            indicator.setWeight(0.4);
            indicator.setDetails(Map.of(
                "loginTime", loginTime.toString(),
                "timezone", request.getLocation() != null ? 
                    request.getLocation().getTimezone() : "Unknown"
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setSource("TEMPORAL_ANALYSIS");
            analysis.getIndicators().add(indicator);
        }
    }
    
    private void analyzeDeviceAnomaly(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) {
            analysis.setIndicators(new ArrayList<>());
        }
        if (analysis.getAnomalies() == null) {
            analysis.setAnomalies(new ArrayList<>());
        }
        
        String deviceId = request.getDeviceId();
        if (deviceId == null && request.getDeviceFingerprint() != null) {
            deviceId = generateDeviceId(request.getDeviceFingerprint());
        }
        
        List<Device> knownDevices = deviceRepository.findByUserId(request.getUserId());
        boolean isNewDevice = knownDevices.stream()
            .noneMatch(d -> d.getDeviceId().equals(deviceId));
        
        if (isNewDevice) {
            SuspiciousIndicator indicator = new SuspiciousIndicator();
            indicator.setType("NEW_DEVICE");
            indicator.setSeverity("MEDIUM");
            indicator.setDescription("Login from previously unknown device");
            indicator.setWeight(0.6);
            indicator.setDetails(Map.of(
                "deviceId", deviceId != null ? deviceId : "unknown",
                "userAgent", request.getUserAgent(),
                "knownDeviceCount", knownDevices.size()
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setSource("DEVICE_ANALYSIS");
            indicator.setConfirmed(true);
            analysis.getIndicators().add(indicator);
        }
        
        if (request.getDeviceInfo() != null) {
            Map<String, Object> deviceInfo = request.getDeviceInfo();
            
            if (Boolean.TRUE.equals(deviceInfo.get("isEmulator"))) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("EMULATOR_DETECTED");
                indicator.setSeverity("HIGH");
                indicator.setDescription("Login from emulator device");
                indicator.setWeight(0.8);
                indicator.setDetails(deviceInfo);
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("DEVICE_ANALYSIS");
                analysis.getIndicators().add(indicator);
            }
            
            if (Boolean.TRUE.equals(deviceInfo.get("isRooted")) || 
                Boolean.TRUE.equals(deviceInfo.get("isJailbroken"))) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("COMPROMISED_DEVICE");
                indicator.setSeverity("HIGH");
                indicator.setDescription("Login from rooted/jailbroken device");
                indicator.setWeight(0.7);
                indicator.setDetails(deviceInfo);
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("DEVICE_ANALYSIS");
                analysis.getIndicators().add(indicator);
            }
        }
        
        if (request.getUserAgent() != null) {
            analyzeUserAgent(request.getUserAgent(), analysis);
        }
    }
    
    private void analyzeBehavioralAnomaly(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) {
            analysis.setIndicators(new ArrayList<>());
        }
        if (analysis.getAnomalies() == null) {
            analysis.setAnomalies(new ArrayList<>());
        }
        
        LoginBehavior behavior = request.getBehavior();
        if (behavior == null) {
            return;
        }
        
        UserProfile profile = userProfileRepository.findByUserId(request.getUserId())
            .orElse(new UserProfile());
        
        Map<String, Object> behaviorBaseline = profile.getBehaviorBaseline();
        if (behaviorBaseline != null) {
            if (behavior.getTypingSpeed() != null) {
                Long baselineSpeed = (Long) behaviorBaseline.get("typingSpeed");
                if (baselineSpeed != null) {
                    double deviation = Math.abs(behavior.getTypingSpeed() - baselineSpeed) / 
                        (double) baselineSpeed;
                    
                    if (deviation > 0.5) {
                        AnomalyDetection anomaly = new AnomalyDetection();
                        anomaly.setAnomalyType("TYPING_SPEED");
                        anomaly.setAnomalyClass("BEHAVIORAL");
                        anomaly.setDeviation(deviation);
                        anomaly.setBaseline(String.valueOf(baselineSpeed));
                        anomaly.setObserved(String.valueOf(behavior.getTypingSpeed()));
                        anomaly.setConfidence(0.7);
                        anomaly.setAlgorithm("BEHAVIORAL_BIOMETRICS");
                        analysis.getAnomalies().add(anomaly);
                    }
                }
            }
            
            if (behavior.getKeystrokeDynamics() != null && !behavior.getKeystrokeDynamics().isEmpty()) {
                List<Long> baselineDynamics = (List<Long>) behaviorBaseline.get("keystrokeDynamics");
                if (baselineDynamics != null && !baselineDynamics.isEmpty()) {
                    double similarity = calculateKeystrokeSimilarity(
                        behavior.getKeystrokeDynamics(), 
                        baselineDynamics
                    );
                    
                    if (similarity < 0.6) {
                        AnomalyDetection anomaly = new AnomalyDetection();
                        anomaly.setAnomalyType("KEYSTROKE_DYNAMICS");
                        anomaly.setAnomalyClass("BEHAVIORAL");
                        anomaly.setDeviation(1.0 - similarity);
                        anomaly.setConfidence(0.8);
                        anomaly.setAlgorithm("KEYSTROKE_PATTERN_MATCHING");
                        analysis.getAnomalies().add(anomaly);
                    }
                }
            }
        }
        
        if (behavior.isCopyPaste()) {
            SuspiciousIndicator indicator = new SuspiciousIndicator();
            indicator.setType("COPY_PASTE_DETECTED");
            indicator.setSeverity("LOW");
            indicator.setDescription("Password entered via copy-paste");
            indicator.setWeight(0.3);
            indicator.setDetails(Map.of("method", "copy_paste"));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setSource("BEHAVIORAL_ANALYSIS");
            analysis.getIndicators().add(indicator);
        }
        
        if (behavior.getPasswordAttempts() != null && behavior.getPasswordAttempts() > 3) {
            SuspiciousIndicator indicator = new SuspiciousIndicator();
            indicator.setType("MULTIPLE_PASSWORD_ATTEMPTS");
            indicator.setSeverity("MEDIUM");
            indicator.setDescription("Multiple password attempts: " + behavior.getPasswordAttempts());
            indicator.setWeight(0.6);
            indicator.setDetails(Map.of("attempts", behavior.getPasswordAttempts()));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setSource("BEHAVIORAL_ANALYSIS");
            analysis.getIndicators().add(indicator);
        }
        
        if (behavior.getTimeToComplete() != null) {
            if (behavior.getTimeToComplete() < 2000) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("AUTOMATED_LOGIN");
                indicator.setSeverity("HIGH");
                indicator.setDescription("Login completed too quickly (possible automation)");
                indicator.setWeight(0.8);
                indicator.setDetails(Map.of("timeToComplete", behavior.getTimeToComplete()));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("BEHAVIORAL_ANALYSIS");
                analysis.getIndicators().add(indicator);
            } else if (behavior.getTimeToComplete() > 120000) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("SLOW_LOGIN");
                indicator.setSeverity("LOW");
                indicator.setDescription("Unusually slow login process");
                indicator.setWeight(0.3);
                indicator.setDetails(Map.of("timeToComplete", behavior.getTimeToComplete()));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("BEHAVIORAL_ANALYSIS");
                analysis.getIndicators().add(indicator);
            }
        }
    }
    
    private void analyzeVelocityAnomaly(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) {
            analysis.setIndicators(new ArrayList<>());
        }
        
        List<LoginAttempt> recentAttempts = loginAttemptRepository
            .findByUserIdAndTimestampAfter(
                request.getUserId(),
                request.getTimestamp().minusMinutes(10)
            );
        
        if (recentAttempts.size() > 5) {
            SuspiciousIndicator indicator = new SuspiciousIndicator();
            indicator.setType("HIGH_LOGIN_VELOCITY");
            indicator.setSeverity("MEDIUM");
            indicator.setDescription("High login velocity: " + recentAttempts.size() + " attempts in 10 minutes");
            indicator.setWeight(0.6);
            indicator.setDetails(Map.of(
                "attemptCount", recentAttempts.size(),
                "timeWindow", "10 minutes"
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setSource("VELOCITY_ANALYSIS");
            analysis.getIndicators().add(indicator);
        }
        
        Set<String> uniqueIps = recentAttempts.stream()
            .map(LoginAttempt::getIpAddress)
            .collect(Collectors.toSet());
        
        if (uniqueIps.size() > 3) {
            SuspiciousIndicator indicator = new SuspiciousIndicator();
            indicator.setType("MULTIPLE_IP_ADDRESSES");
            indicator.setSeverity("HIGH");
            indicator.setDescription("Login attempts from " + uniqueIps.size() + " different IPs");
            indicator.setWeight(0.7);
            indicator.setDetails(Map.of(
                "ipAddresses", uniqueIps,
                "ipCount", uniqueIps.size()
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setSource("VELOCITY_ANALYSIS");
            analysis.getIndicators().add(indicator);
        }
        
        List<LoginAttempt> failedAttempts = loginAttemptRepository
            .findFailedByUserIdAndTimestampAfter(
                request.getUserId(),
                request.getTimestamp().minusHours(1)
            );
        
        if (failedAttempts.size() > 10) {
            SuspiciousIndicator indicator = new SuspiciousIndicator();
            indicator.setType("BRUTE_FORCE_PATTERN");
            indicator.setSeverity("CRITICAL");
            indicator.setDescription("Possible brute force attack: " + failedAttempts.size() + " failed attempts");
            indicator.setWeight(0.9);
            indicator.setDetails(Map.of(
                "failedCount", failedAttempts.size(),
                "timeWindow", "1 hour"
            ));
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setSource("VELOCITY_ANALYSIS");
            indicator.setConfirmed(true);
            analysis.getIndicators().add(indicator);
        }
    }
    
    private void analyzeIpReputation(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) {
            analysis.setIndicators(new ArrayList<>());
        }
        
        IpReputationScore reputationScore = ipReputationService
            .getReputationScore(request.getIpAddress());
        
        if (reputationScore != null) {
            if (reputationScore.getThreatScore() > 70) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("MALICIOUS_IP");
                indicator.setSeverity("CRITICAL");
                indicator.setDescription("Login from known malicious IP");
                indicator.setWeight(0.95);
                indicator.setDetails(Map.of(
                    "threatScore", reputationScore.getThreatScore(),
                    "categories", reputationScore.getCategories(),
                    "lastSeen", reputationScore.getLastSeenMalicious()
                ));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("IP_REPUTATION");
                indicator.setConfirmed(true);
                analysis.getIndicators().add(indicator);
            } else if (reputationScore.getThreatScore() > 40) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("SUSPICIOUS_IP");
                indicator.setSeverity("MEDIUM");
                indicator.setDescription("Login from suspicious IP");
                indicator.setWeight(0.6);
                indicator.setDetails(Map.of(
                    "threatScore", reputationScore.getThreatScore(),
                    "categories", reputationScore.getCategories()
                ));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("IP_REPUTATION");
                analysis.getIndicators().add(indicator);
            }
            
            if (reputationScore.isBlacklisted()) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("BLACKLISTED_IP");
                indicator.setSeverity("CRITICAL");
                indicator.setDescription("IP is blacklisted");
                indicator.setWeight(1.0);
                indicator.setDetails(Map.of(
                    "blacklists", reputationScore.getBlacklists(),
                    "reason", reputationScore.getBlacklistReason()
                ));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("IP_REPUTATION");
                indicator.setConfirmed(true);
                analysis.getIndicators().add(indicator);
            }
        }
    }
    
    private void detectInjectionAttempts(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) {
            analysis.setIndicators(new ArrayList<>());
        }
        
        List<String> fieldsToCheck = Arrays.asList(
            request.getUsername(),
            request.getEmail(),
            request.getReferrer(),
            request.getOrigin()
        );
        
        for (String field : fieldsToCheck) {
            if (field == null) continue;
            
            for (Map.Entry<String, Pattern> entry : SUSPICIOUS_PATTERNS.entrySet()) {
                Matcher matcher = entry.getValue().matcher(field);
                if (matcher.find()) {
                    SuspiciousIndicator indicator = new SuspiciousIndicator();
                    indicator.setType(entry.getKey());
                    indicator.setSeverity("HIGH");
                    indicator.setDescription("Potential injection attempt detected");
                    indicator.setWeight(0.85);
                    indicator.setDetails(Map.of(
                        "pattern", entry.getKey(),
                        "field", field,
                        "matched", matcher.group()
                    ));
                    indicator.setDetectedAt(LocalDateTime.now());
                    indicator.setSource("INJECTION_DETECTION");
                    indicator.setConfirmed(true);
                    analysis.getIndicators().add(indicator);
                }
            }
        }
    }
    
    private void detectBotPatterns(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) {
            analysis.setIndicators(new ArrayList<>());
        }
        
        String userAgent = request.getUserAgent();
        if (userAgent != null) {
            Pattern botPattern = SUSPICIOUS_PATTERNS.get("BOT_PATTERN");
            if (botPattern.matcher(userAgent).find()) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("BOT_DETECTED");
                indicator.setSeverity("HIGH");
                indicator.setDescription("Bot user agent detected");
                indicator.setWeight(0.8);
                indicator.setDetails(Map.of("userAgent", userAgent));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("BOT_DETECTION");
                analysis.getIndicators().add(indicator);
            }
            
            if (userAgent.isEmpty() || userAgent.length() < 20) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("SUSPICIOUS_USER_AGENT");
                indicator.setSeverity("MEDIUM");
                indicator.setDescription("Suspicious or missing user agent");
                indicator.setWeight(0.5);
                indicator.setDetails(Map.of("userAgent", userAgent));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("BOT_DETECTION");
                analysis.getIndicators().add(indicator);
            }
        }
        
        if (request.getHeaders() != null) {
            Map<String, String> headers = request.getHeaders();
            
            if (!headers.containsKey("Accept-Language") || 
                !headers.containsKey("Accept-Encoding")) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("MISSING_BROWSER_HEADERS");
                indicator.setSeverity("MEDIUM");
                indicator.setDescription("Missing standard browser headers");
                indicator.setWeight(0.6);
                indicator.setDetails(Map.of("headers", headers.keySet()));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("BOT_DETECTION");
                analysis.getIndicators().add(indicator);
            }
        }
    }
    
    private void analyzeAuthenticationPattern(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) {
            analysis.setIndicators(new ArrayList<>());
        }
        
        if (!request.isMfaUsed()) {
            UserProfile profile = userProfileRepository.findByUserId(request.getUserId())
                .orElse(new UserProfile());
            
            if (profile.isMfaEnabled()) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("MFA_BYPASS");
                indicator.setSeverity("HIGH");
                indicator.setDescription("MFA bypassed for MFA-enabled account");
                indicator.setWeight(0.8);
                indicator.setDetails(Map.of(
                    "mfaEnabled", true,
                    "mfaUsed", false
                ));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("AUTH_PATTERN_ANALYSIS");
                analysis.getIndicators().add(indicator);
            }
        }
        
        if ("API_KEY".equals(request.getAuthMethod())) {
            if (request.getContext() != null && !request.getContext().isApiAccess()) {
                SuspiciousIndicator indicator = new SuspiciousIndicator();
                indicator.setType("UNEXPECTED_AUTH_METHOD");
                indicator.setSeverity("MEDIUM");
                indicator.setDescription("API key used for non-API access");
                indicator.setWeight(0.6);
                indicator.setDetails(Map.of(
                    "authMethod", request.getAuthMethod(),
                    "accessType", request.getContext()
                ));
                indicator.setDetectedAt(LocalDateTime.now());
                indicator.setSource("AUTH_PATTERN_ANALYSIS");
                analysis.getIndicators().add(indicator);
            }
        }
    }
    
    private void analyzeUserAgent(String userAgent, SuspiciousLoginAnalysis analysis) {
        Map<String, String> uaDetails = parseUserAgent(userAgent);
        
        if (uaDetails.get("browser").equals("Unknown") || 
            uaDetails.get("os").equals("Unknown")) {
            SuspiciousIndicator indicator = new SuspiciousIndicator();
            indicator.setType("UNRECOGNIZED_USER_AGENT");
            indicator.setSeverity("LOW");
            indicator.setDescription("Unrecognized user agent");
            indicator.setWeight(0.4);
            indicator.setDetails(uaDetails);
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setSource("USER_AGENT_ANALYSIS");
            analysis.getIndicators().add(indicator);
        }
        
        if (uaDetails.get("browser").contains("Headless") || 
            userAgent.contains("PhantomJS") || 
            userAgent.contains("Selenium")) {
            SuspiciousIndicator indicator = new SuspiciousIndicator();
            indicator.setType("HEADLESS_BROWSER");
            indicator.setSeverity("HIGH");
            indicator.setDescription("Headless browser detected");
            indicator.setWeight(0.8);
            indicator.setDetails(uaDetails);
            indicator.setDetectedAt(LocalDateTime.now());
            indicator.setSource("USER_AGENT_ANALYSIS");
            analysis.getIndicators().add(indicator);
        }
    }
    
    private void calculateOverallRiskScore(SuspiciousLoginAnalysis analysis) {
        LoginRiskScore riskScore = new LoginRiskScore();
        
        double locationScore = calculateLocationScore(analysis);
        double timeScore = calculateTimeScore(analysis);
        double deviceScore = calculateDeviceScore(analysis);
        double behaviorScore = calculateBehaviorScore(analysis);
        double velocityScore = calculateVelocityScore(analysis);
        double reputationScore = calculateReputationScore(analysis);
        
        riskScore.setLocationScore(locationScore);
        riskScore.setTimeScore(timeScore);
        riskScore.setDeviceScore(deviceScore);
        riskScore.setBehaviorScore(behaviorScore);
        riskScore.setVelocityScore(velocityScore);
        riskScore.setReputationScore(reputationScore);
        
        double overallScore = 
            locationScore * RISK_WEIGHTS.get("LOCATION_ANOMALY") +
            timeScore * RISK_WEIGHTS.get("TIME_ANOMALY") +
            deviceScore * RISK_WEIGHTS.get("DEVICE_ANOMALY") +
            behaviorScore * RISK_WEIGHTS.get("BEHAVIORAL_ANOMALY") +
            velocityScore * RISK_WEIGHTS.get("VELOCITY_ANOMALY") +
            reputationScore * RISK_WEIGHTS.get("IP_REPUTATION");
        
        if (analysis.getIndicators() != null) {
            double indicatorBoost = analysis.getIndicators().stream()
                .filter(i -> "CRITICAL".equals(i.getSeverity()))
                .mapToDouble(i -> i.getWeight() * 0.1)
                .sum();
            overallScore = Math.min(1.0, overallScore + indicatorBoost);
        }
        
        riskScore.setOverallScore(overallScore);
        riskScore.setRiskLevel(determineRiskLevel(overallScore));
        riskScore.setConfidence(calculateConfidence(analysis));
        riskScore.setFactorWeights(RISK_WEIGHTS);
        riskScore.setContributingFactors(identifyContributingFactors(analysis));
        
        analysis.setRiskScore(riskScore);
    }
    
    private double calculateLocationScore(SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) return 0.0;
        
        return analysis.getIndicators().stream()
            .filter(i -> i.getType().contains("TRAVEL") || 
                        i.getType().contains("LOCATION") ||
                        i.getType().contains("ANONYMIZATION"))
            .mapToDouble(i -> i.getWeight())
            .average()
            .orElse(0.0);
    }
    
    private double calculateTimeScore(SuspiciousLoginAnalysis analysis) {
        if (analysis.getAnomalies() == null) return 0.0;
        
        return analysis.getAnomalies().stream()
            .filter(a -> "TEMPORAL".equals(a.getAnomalyClass()))
            .mapToDouble(a -> a.getDeviation() / 10.0)
            .average()
            .orElse(0.0);
    }
    
    private double calculateDeviceScore(SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) return 0.0;
        
        return analysis.getIndicators().stream()
            .filter(i -> i.getType().contains("DEVICE") || 
                        i.getType().contains("EMULATOR") ||
                        i.getType().contains("COMPROMISED"))
            .mapToDouble(i -> i.getWeight())
            .average()
            .orElse(0.0);
    }
    
    private double calculateBehaviorScore(SuspiciousLoginAnalysis analysis) {
        if (analysis.getAnomalies() == null) return 0.0;
        
        return analysis.getAnomalies().stream()
            .filter(a -> "BEHAVIORAL".equals(a.getAnomalyClass()))
            .mapToDouble(a -> a.getDeviation())
            .average()
            .orElse(0.0);
    }
    
    private double calculateVelocityScore(SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) return 0.0;
        
        return analysis.getIndicators().stream()
            .filter(i -> i.getType().contains("VELOCITY") || 
                        i.getType().contains("BRUTE_FORCE"))
            .mapToDouble(i -> i.getWeight())
            .max()
            .orElse(0.0);
    }
    
    private double calculateReputationScore(SuspiciousLoginAnalysis analysis) {
        if (analysis.getIndicators() == null) return 0.0;
        
        return analysis.getIndicators().stream()
            .filter(i -> i.getType().contains("IP") || 
                        i.getType().contains("BLACKLIST"))
            .mapToDouble(i -> i.getWeight())
            .max()
            .orElse(0.0);
    }
    
    private String determineRiskLevel(double score) {
        if (score >= CRITICAL_RISK_THRESHOLD) return "CRITICAL";
        if (score >= HIGH_RISK_THRESHOLD) return "HIGH";
        if (score >= MEDIUM_RISK_THRESHOLD) return "MEDIUM";
        if (score >= LOW_RISK_THRESHOLD) return "LOW";
        return "MINIMAL";
    }
    
    private String calculateConfidence(SuspiciousLoginAnalysis analysis) {
        int dataPoints = 0;
        if (analysis.getIndicators() != null) dataPoints += analysis.getIndicators().size();
        if (analysis.getAnomalies() != null) dataPoints += analysis.getAnomalies().size();
        
        if (dataPoints >= 10) return "HIGH";
        if (dataPoints >= 5) return "MEDIUM";
        return "LOW";
    }
    
    private List<String> identifyContributingFactors(SuspiciousLoginAnalysis analysis) {
        List<String> factors = new ArrayList<>();
        
        if (analysis.getIndicators() != null) {
            analysis.getIndicators().stream()
                .filter(i -> i.getWeight() > 0.5)
                .map(i -> i.getType())
                .forEach(factors::add);
        }
        
        if (analysis.getAnomalies() != null) {
            analysis.getAnomalies().stream()
                .filter(a -> a.getDeviation() > 0.5)
                .map(a -> a.getAnomalyType())
                .forEach(factors::add);
        }
        
        return factors;
    }
    
    private void generateSecurityRecommendation(SuspiciousLoginAnalysis analysis) {
        SecurityRecommendation recommendation = new SecurityRecommendation();
        double riskScore = analysis.getRiskScore().getOverallScore();
        
        if (riskScore >= CRITICAL_RISK_THRESHOLD) {
            recommendation.setRecommendationType("IMMEDIATE_ACTION");
            recommendation.setPriority("CRITICAL");
            recommendation.setActions(Arrays.asList(
                "Block login attempt immediately",
                "Lock account temporarily",
                "Require identity verification",
                "Alert security team",
                "Initiate incident response"
            ));
            recommendation.setExpectedRiskReduction(0.9);
        } else if (riskScore >= HIGH_RISK_THRESHOLD) {
            recommendation.setRecommendationType("ENHANCED_VERIFICATION");
            recommendation.setPriority("HIGH");
            recommendation.setActions(Arrays.asList(
                "Require additional authentication",
                "Send verification email/SMS",
                "Enable step-up authentication",
                "Monitor session closely"
            ));
            recommendation.setExpectedRiskReduction(0.7);
        } else if (riskScore >= MEDIUM_RISK_THRESHOLD) {
            recommendation.setRecommendationType("ADDITIONAL_CHECKS");
            recommendation.setPriority("MEDIUM");
            recommendation.setActions(Arrays.asList(
                "Require CAPTCHA",
                "Verify device fingerprint",
                "Check recent activity",
                "Send login notification"
            ));
            recommendation.setExpectedRiskReduction(0.5);
        } else {
            recommendation.setRecommendationType("STANDARD_MONITORING");
            recommendation.setPriority("LOW");
            recommendation.setActions(Arrays.asList(
                "Log login attempt",
                "Update user profile",
                "Continue monitoring"
            ));
            recommendation.setExpectedRiskReduction(0.2);
        }
        
        recommendation.setReasoning("Based on risk score: " + 
            String.format("%.2f", riskScore) + " (" + 
            analysis.getRiskScore().getRiskLevel() + ")");
        recommendation.setExpiresAt(LocalDateTime.now().plusHours(24));
        
        analysis.setRecommendation(recommendation);
    }
    
    private void determineAutomatedResponse(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        AutomatedResponse response = new AutomatedResponse();
        double riskScore = analysis.getRiskScore().getOverallScore();
        
        response.setActionsTaken(new ArrayList<>());
        response.setResults(new HashMap<>());
        
        if (riskScore >= CRITICAL_RISK_THRESHOLD) {
            response.setResponseType("BLOCK_AND_LOCKDOWN");
            response.setStatus("EXECUTED");
            response.getActionsTaken().add("Blocked login attempt");
            response.getActionsTaken().add("Locked user account");
            response.getActionsTaken().add("Blacklisted IP address");
            response.getActionsTaken().add("Triggered security alert");
            response.setRequiresConfirmation(false);
            response.setRollbackPlan("Manual review required for unlock");
        } else if (riskScore >= HIGH_RISK_THRESHOLD) {
            response.setResponseType("CHALLENGE_RESPONSE");
            response.setStatus("PENDING_USER_ACTION");
            response.getActionsTaken().add("Initiated MFA challenge");
            response.getActionsTaken().add("Sent verification code");
            response.getActionsTaken().add("Enabled session monitoring");
            response.setRequiresConfirmation(true);
            response.setRollbackPlan("Allow retry with proper verification");
        } else if (riskScore >= MEDIUM_RISK_THRESHOLD) {
            response.setResponseType("ENHANCED_MONITORING");
            response.setStatus("ACTIVE");
            response.getActionsTaken().add("Enabled detailed logging");
            response.getActionsTaken().add("Set session timeout");
            response.getActionsTaken().add("Activated behavior tracking");
            response.setRequiresConfirmation(false);
            response.setRollbackPlan("Standard monitoring after session end");
        } else {
            response.setResponseType("STANDARD_FLOW");
            response.setStatus("ALLOWED");
            response.getActionsTaken().add("Logged login attempt");
            response.getActionsTaken().add("Updated last login time");
            response.setRequiresConfirmation(false);
        }
        
        response.setExecutedAt(LocalDateTime.now());
        analysis.setResponse(response);
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    
    private double calculateTimeDeviation(int currentHour, List<Integer> usualHours) {
        return usualHours.stream()
            .mapToInt(h -> Math.abs(currentHour - h))
            .min()
            .orElse(12);
    }
    
    private double calculateZScore(int value, List<Integer> baseline) {
        double mean = baseline.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = baseline.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0);
        double stdDev = Math.sqrt(variance);
        return stdDev == 0 ? 0 : (value - mean) / stdDev;
    }
    
    private double calculateKeystrokeSimilarity(List<Long> current, List<Long> baseline) {
        int minSize = Math.min(current.size(), baseline.size());
        if (minSize == 0) return 0;
        
        double totalDiff = 0;
        for (int i = 0; i < minSize; i++) {
            totalDiff += Math.abs(current.get(i) - baseline.get(i));
        }
        
        double avgDiff = totalDiff / minSize;
        double maxDiff = 1000;
        return Math.max(0, 1 - (avgDiff / maxDiff));
    }
    
    private String generateDeviceId(String fingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprint.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            return fingerprint.substring(0, Math.min(32, fingerprint.length()));
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    private Map<String, String> parseUserAgent(String userAgent) {
        Map<String, String> details = new HashMap<>();
        
        String browser = "Unknown";
        String os = "Unknown";
        String version = "Unknown";
        
        if (userAgent.contains("Chrome")) {
            browser = "Chrome";
            Pattern pattern = Pattern.compile("Chrome/(\\S+)");
            Matcher matcher = pattern.matcher(userAgent);
            if (matcher.find()) version = matcher.group(1);
        } else if (userAgent.contains("Firefox")) {
            browser = "Firefox";
            Pattern pattern = Pattern.compile("Firefox/(\\S+)");
            Matcher matcher = pattern.matcher(userAgent);
            if (matcher.find()) version = matcher.group(1);
        } else if (userAgent.contains("Safari")) {
            browser = "Safari";
            Pattern pattern = Pattern.compile("Version/(\\S+)");
            Matcher matcher = pattern.matcher(userAgent);
            if (matcher.find()) version = matcher.group(1);
        }
        
        if (userAgent.contains("Windows")) os = "Windows";
        else if (userAgent.contains("Mac OS")) os = "macOS";
        else if (userAgent.contains("Linux")) os = "Linux";
        else if (userAgent.contains("Android")) os = "Android";
        else if (userAgent.contains("iOS")) os = "iOS";
        
        details.put("browser", browser);
        details.put("browserVersion", version);
        details.put("os", os);
        details.put("rawUserAgent", userAgent);
        
        return details;
    }
    
    private void handleCriticalRiskLogin(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        log.error("CRITICAL RISK LOGIN DETECTED - User: {}, IP: {}, Risk Score: {}", 
            request.getUserId(), request.getIpAddress(), analysis.getRiskScore().getOverallScore());
        
        authenticationService.blockLogin(request.getUserId(), request.getSessionId());
        authenticationService.lockAccount(request.getUserId(), Duration.ofHours(24));
        ipReputationService.blacklistIp(request.getIpAddress(), "Critical risk login attempt");
        
        securityOrchestrationService.initiateIncidentResponse(
            "CRITICAL_LOGIN_RISK",
            request.getUserId(),
            analysis
        );
        
        sendCriticalAlert(request, analysis);
    }
    
    private void handleHighRiskLogin(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        log.warn("HIGH RISK LOGIN DETECTED - User: {}, IP: {}, Risk Score: {}", 
            request.getUserId(), request.getIpAddress(), analysis.getRiskScore().getOverallScore());
        
        authenticationService.requireStepUpAuth(request.getUserId(), request.getSessionId());
        authenticationService.enableEnhancedMonitoring(request.getSessionId());
        
        sendHighRiskNotification(request, analysis);
    }
    
    private void handleMediumRiskLogin(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        log.info("MEDIUM RISK LOGIN DETECTED - User: {}, IP: {}, Risk Score: {}", 
            request.getUserId(), request.getIpAddress(), analysis.getRiskScore().getOverallScore());
        
        authenticationService.requireCaptcha(request.getSessionId());
        authenticationService.setReducedSessionTimeout(request.getSessionId(), Duration.ofMinutes(30));
        
        sendMediumRiskNotification(request, analysis);
    }
    
    private void handleLowRiskLogin(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        log.info("LOW RISK LOGIN - User: {}, IP: {}, Risk Score: {}", 
            request.getUserId(), request.getIpAddress(), analysis.getRiskScore().getOverallScore());
        
        authenticationService.logLoginAttempt(request);
    }
    
    private void sendCriticalAlert(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("CRITICAL_SECURITY_ALERT")
            .priority("URGENT")
            .recipient(request.getUserId())
            .subject("Critical Security Alert - Suspicious Login Blocked")
            .templateData(Map.of(
                "userId", request.getUserId(),
                "ipAddress", request.getIpAddress(),
                "location", request.getLocation() != null ? 
                    request.getLocation().getCity() + ", " + request.getLocation().getCountry() : "Unknown",
                "riskScore", String.format("%.2f", analysis.getRiskScore().getOverallScore()),
                "indicators", analysis.getIndicators().stream()
                    .filter(i -> "CRITICAL".equals(i.getSeverity()))
                    .map(i -> i.getDescription())
                    .collect(Collectors.toList()),
                "timestamp", request.getTimestamp().toString()
            ))
            .channels(Arrays.asList("EMAIL", "SMS", "PUSH", "IN_APP"))
            .build();
        
        notificationService.send(template);
        
        NotificationTemplate adminTemplate = NotificationTemplate.builder()
            .type("ADMIN_SECURITY_ALERT")
            .priority("URGENT")
            .recipient("security-team")
            .subject("Critical Login Risk Detected")
            .templateData(Map.of(
                "analysis", analysis,
                "request", request
            ))
            .channels(Arrays.asList("SLACK", "PAGERDUTY"))
            .build();
        
        notificationService.send(adminTemplate);
    }
    
    private void sendHighRiskNotification(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("HIGH_RISK_LOGIN")
            .priority("HIGH")
            .recipient(request.getUserId())
            .subject("Suspicious Login Attempt Detected")
            .templateData(Map.of(
                "ipAddress", request.getIpAddress(),
                "location", request.getLocation() != null ? 
                    request.getLocation().getCity() + ", " + request.getLocation().getCountry() : "Unknown",
                "device", request.getUserAgent(),
                "action", "Additional verification required"
            ))
            .channels(Arrays.asList("EMAIL", "SMS", "IN_APP"))
            .build();
        
        notificationService.send(template);
    }
    
    private void sendMediumRiskNotification(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        NotificationTemplate template = NotificationTemplate.builder()
            .type("MEDIUM_RISK_LOGIN")
            .priority("MEDIUM")
            .recipient(request.getUserId())
            .subject("New Login from Unfamiliar Location")
            .templateData(Map.of(
                "location", request.getLocation() != null ? 
                    request.getLocation().getCity() + ", " + request.getLocation().getCountry() : "Unknown",
                "device", request.getUserAgent(),
                "timestamp", request.getTimestamp().toString()
            ))
            .channels(Arrays.asList("EMAIL", "IN_APP"))
            .build();
        
        notificationService.send(template);
    }
    
    private void persistAnalysisResults(LoginAttemptRequest request, SuspiciousLoginAnalysis analysis) {
        SecurityEvent event = new SecurityEvent();
        event.setEventId(analysis.getAnalysisId());
        event.setEventType("SUSPICIOUS_LOGIN_ANALYSIS");
        event.setUserId(request.getUserId());
        event.setSessionId(request.getSessionId());
        event.setIpAddress(request.getIpAddress());
        event.setRiskScore(analysis.getRiskScore().getOverallScore());
        event.setRiskLevel(analysis.getRiskScore().getRiskLevel());
        event.setTimestamp(LocalDateTime.now());
        event.setDetails(objectMapper.convertValue(analysis, Map.class));
        
        securityEventRepository.save(event);
        
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUserId(request.getUserId());
        attempt.setSessionId(request.getSessionId());
        attempt.setIpAddress(request.getIpAddress());
        attempt.setUserAgent(request.getUserAgent());
        attempt.setLocation(request.getLocation());
        attempt.setAuthMethod(request.getAuthMethod());
        attempt.setMfaUsed(request.isMfaUsed());
        attempt.setRiskScore(analysis.getRiskScore().getOverallScore());
        attempt.setStatus(analysis.getRiskScore().getOverallScore() >= HIGH_RISK_THRESHOLD ? 
            "BLOCKED" : "ALLOWED");
        attempt.setTimestamp(request.getTimestamp());
        attempt.setAnalysisId(analysis.getAnalysisId());
        
        loginAttemptRepository.save(attempt);
        
        String cacheKey = "login:analysis:" + request.getUserId();
        redisCache.set(cacheKey, analysis, Duration.ofHours(24));
    }
    
    private void publishAnalysisEvents(SuspiciousLoginAnalysis analysis) {
        if (analysis.getRiskScore().getOverallScore() >= HIGH_RISK_THRESHOLD) {
            kafkaTemplate.send("security-alerts", analysis.getUserId(), analysis);
        }
        
        if (analysis.isRequiresManualReview()) {
            kafkaTemplate.send("security-review-queue", analysis.getUserId(), analysis);
        }
        
        kafkaTemplate.send("login-analytics", analysis.getUserId(), analysis);
    }
    
    private void updateMetrics(SuspiciousLoginAnalysis analysis, long processingTime) {
        metricsService.recordLoginRiskScore(analysis.getRiskScore().getOverallScore());
        metricsService.recordLoginRiskLevel(analysis.getRiskScore().getRiskLevel());
        metricsService.recordSuspiciousIndicatorCount(
            analysis.getIndicators() != null ? analysis.getIndicators().size() : 0
        );
        metricsService.recordAnomalyCount(
            analysis.getAnomalies() != null ? analysis.getAnomalies().size() : 0
        );
        metricsService.recordProcessingTime("suspicious-login-detection", processingTime);
        
        if (analysis.getRiskScore().getOverallScore() >= CRITICAL_RISK_THRESHOLD) {
            metricsService.incrementCounter("critical.risk.logins");
        } else if (analysis.getRiskScore().getOverallScore() >= HIGH_RISK_THRESHOLD) {
            metricsService.incrementCounter("high.risk.logins");
        } else if (analysis.getRiskScore().getOverallScore() >= MEDIUM_RISK_THRESHOLD) {
            metricsService.incrementCounter("medium.risk.logins");
        } else if (analysis.getRiskScore().getOverallScore() >= LOW_RISK_THRESHOLD) {
            metricsService.incrementCounter("low.risk.logins");
        }
    }
    
    private void handleProcessingTimeout(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        metricsService.incrementCounter("suspicious.login.detection.timeouts");
        sendToDLQ(record, "Processing timeout");
        acknowledgment.acknowledge();
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Acknowledgment acknowledgment, Exception error) {
        metricsService.incrementCounter("suspicious.login.detection.errors");
        log.error("Failed to process suspicious login detection: {}", record.key(), error);
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