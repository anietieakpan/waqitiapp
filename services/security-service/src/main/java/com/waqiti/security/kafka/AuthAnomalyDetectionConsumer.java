package com.waqiti.security.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.security.model.*;
import com.waqiti.security.repository.AuthAnomalyRepository;
import com.waqiti.security.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for authentication anomaly detection
 * Handles behavioral authentication analysis, suspicious login detection, and adaptive security
 * 
 * Critical for: Account security, fraud prevention, adaptive authentication
 * SLA: Must process auth events within 8 seconds for real-time security decisions
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthAnomalyDetectionConsumer {

    private final AuthAnomalyRepository anomalyRepository;
    private final BehavioralAuthService behavioralAuthService;
    private final AnomalyDetectionEngine anomalyEngine;
    private final UserProfileService userProfileService;
    private final DeviceAnalysisService deviceAnalysisService;
    private final GeolocationService geolocationService;
    private final AdaptiveAuthService adaptiveAuthService;
    private final MLModelService mlModelService;
    private final NotificationService notificationService;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ScheduledExecutorService scheduledExecutor;
    private final DashboardService dashboardService;
    private final UserSecurityService userSecurityService;
    private final DeviceMonitoringService deviceMonitoringService;
    private final EmergencyAlertService emergencyAlertService;
    private final AlertingService alertingService;
    private final FailedEventRepository failedEventRepository;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 8000; // 8 seconds
    private static final Set<String> HIGH_RISK_ANOMALIES = Set.of(
        "IMPOSSIBLE_TRAVEL", "COMPROMISED_CREDENTIALS", "DEVICE_SPOOFING", 
        "BEHAVIORAL_DEVIATION", "CREDENTIAL_STUFFING", "BRUTE_FORCE_PATTERN"
    );
    
    @KafkaListener(
        topics = {"auth-anomaly-detection"},
        groupId = "auth-anomaly-detection-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "auth-anomaly-detection-processor", fallbackMethod = "handleAuthAnomalyFailure")
    @Retry(name = "auth-anomaly-detection-processor")
    public void processAuthAnomalyDetection(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing auth anomaly detection: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            AuthenticationEvent authEvent = extractAuthenticationEvent(payload);
            
            // Validate authentication event
            validateAuthEvent(authEvent);
            
            // Check for duplicate event
            if (isDuplicateEvent(authEvent)) {
                log.warn("Duplicate auth event detected: {}, skipping", authEvent.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Enrich auth event with contextual data
            AuthenticationEvent enrichedEvent = enrichAuthEvent(authEvent);
            
            // Perform comprehensive anomaly detection
            AnomalyDetectionResult anomalyResult = performAnomalyDetection(enrichedEvent);
            
            // Process authentication anomalies
            AuthAnomalyProcessingResult result = processAuthAnomalies(enrichedEvent, anomalyResult);
            
            // Apply adaptive authentication controls
            if (anomalyResult.requiresAdaptiveAuth()) {
                applyAdaptiveAuthControls(enrichedEvent, anomalyResult, result);
            }
            
            // Update user behavioral profiles
            if (anomalyResult.enablesProfileUpdate()) {
                updateUserBehavioralProfile(enrichedEvent, anomalyResult);
            }
            
            // Perform device analysis
            if (anomalyResult.requiresDeviceAnalysis()) {
                performDeviceAnalysis(enrichedEvent, result);
            }
            
            // Update ML models with new behavioral data
            if (anomalyResult.enablesMLUpdate()) {
                updateMLModels(enrichedEvent, anomalyResult);
            }
            
            // Trigger security workflows
            if (anomalyResult.hasSecurityWorkflows()) {
                triggerSecurityWorkflows(enrichedEvent, anomalyResult);
            }
            
            // Send anomaly notifications
            sendAnomalyNotifications(enrichedEvent, anomalyResult, result);
            
            // Update security monitoring
            updateSecurityMonitoring(enrichedEvent, result);
            
            // Audit anomaly detection
            auditAnomalyDetection(enrichedEvent, result, event);
            
            // Record metrics
            recordAnomalyMetrics(enrichedEvent, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed auth anomaly: {} user: {} anomalies: {} risk: {} in {}ms", 
                    enrichedEvent.getEventId(), enrichedEvent.getUserId(), 
                    anomalyResult.getDetectedAnomalies().size(), anomalyResult.getRiskScore(), 
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for auth anomaly: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalAnomalyException e) {
            log.error("Critical anomaly processing failed: {}", eventId, e);
            handleCriticalAnomalyError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process auth anomaly: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private AuthenticationEvent extractAuthenticationEvent(Map<String, Object> payload) {
        return AuthenticationEvent.builder()
            .eventId(extractString(payload, "eventId", UUID.randomUUID().toString()))
            .userId(extractString(payload, "userId", null))
            .sessionId(extractString(payload, "sessionId", null))
            .authMethod(AuthMethod.fromString(extractString(payload, "authMethod", null)))
            .authResult(AuthResult.fromString(extractString(payload, "authResult", null)))
            .sourceIp(extractString(payload, "sourceIp", null))
            .userAgent(extractString(payload, "userAgent", null))
            .deviceId(extractString(payload, "deviceId", null))
            .deviceFingerprint(extractString(payload, "deviceFingerprint", null))
            .country(extractString(payload, "country", null))
            .region(extractString(payload, "region", null))
            .city(extractString(payload, "city", null))
            .latitude(extractDouble(payload, "latitude", null))
            .longitude(extractDouble(payload, "longitude", null))
            .isp(extractString(payload, "isp", null))
            .organization(extractString(payload, "organization", null))
            .applicationId(extractString(payload, "applicationId", null))
            .clientType(extractString(payload, "clientType", null))
            .platform(extractString(payload, "platform", null))
            .browserName(extractString(payload, "browserName", null))
            .browserVersion(extractString(payload, "browserVersion", null))
            .osName(extractString(payload, "osName", null))
            .osVersion(extractString(payload, "osVersion", null))
            .screenResolution(extractString(payload, "screenResolution", null))
            .timezone(extractString(payload, "timezone", null))
            .language(extractString(payload, "language", null))
            .cookiesEnabled(extractBoolean(payload, "cookiesEnabled", true))
            .javaScriptEnabled(extractBoolean(payload, "javaScriptEnabled", true))
            .pluginsInstalled(extractStringList(payload, "pluginsInstalled"))
            .authAttempts(extractInteger(payload, "authAttempts", 1))
            .timeToComplete(extractLong(payload, "timeToComplete", null))
            .referrer(extractString(payload, "referrer", null))
            .httpHeaders(extractMap(payload, "httpHeaders"))
            .riskFlags(extractStringList(payload, "riskFlags"))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .metadata(extractMap(payload, "metadata"))
            .timestamp(extractInstant(payload, "timestamp"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateAuthEvent(AuthenticationEvent event) {
        if (event.getUserId() == null || event.getUserId().isEmpty()) {
            throw new ValidationException("User ID is required");
        }
        
        if (event.getAuthMethod() == null) {
            throw new ValidationException("Authentication method is required");
        }
        
        if (event.getAuthResult() == null) {
            throw new ValidationException("Authentication result is required");
        }
        
        if (event.getSourceIp() == null || event.getSourceIp().isEmpty()) {
            throw new ValidationException("Source IP is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new ValidationException("Timestamp is required");
        }
        
        // Validate IP address format
        if (!isValidIpAddress(event.getSourceIp())) {
            throw new ValidationException("Invalid IP address format: " + event.getSourceIp());
        }
        
        // Validate coordinates if provided
        if (event.getLatitude() != null && 
            (event.getLatitude() < -90 || event.getLatitude() > 90)) {
            throw new ValidationException("Invalid latitude: " + event.getLatitude());
        }
        
        if (event.getLongitude() != null && 
            (event.getLongitude() < -180 || event.getLongitude() > 180)) {
            throw new ValidationException("Invalid longitude: " + event.getLongitude());
        }
    }

    private boolean isValidIpAddress(String ip) {
        if (ip == null) return false;
        
        // Simple IPv4 validation
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDuplicateEvent(AuthenticationEvent event) {
        // Check for exact duplicate
        return anomalyRepository.existsByEventIdAndCreatedAtAfter(
            event.getEventId(),
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private AuthenticationEvent enrichAuthEvent(AuthenticationEvent event) {
        // Enrich with user profile data
        UserSecurityProfile userProfile = userProfileService.getUserSecurityProfile(event.getUserId());
        if (userProfile != null) {
            event.setUserRiskLevel(userProfile.getRiskLevel());
            event.setAccountAge(userProfile.getAccountAge());
            event.setLastLoginTime(userProfile.getLastLoginTime());
            event.setLastLoginLocation(userProfile.getLastLoginLocation());
            event.setTypicalLoginTimes(userProfile.getTypicalLoginTimes());
            event.setTypicalDevices(userProfile.getTypicalDevices());
            event.setTypicalLocations(userProfile.getTypicalLocations());
        }
        
        // Enrich with geolocation data if not provided
        if (event.getCountry() == null && event.getSourceIp() != null) {
            GeoLocationData geoData = geolocationService.getLocationData(event.getSourceIp());
            if (geoData != null) {
                event.setCountry(geoData.getCountry());
                event.setRegion(geoData.getRegion());
                event.setCity(geoData.getCity());
                event.setLatitude(geoData.getLatitude());
                event.setLongitude(geoData.getLongitude());
                event.setIsp(geoData.getIsp());
                event.setOrganization(geoData.getOrganization());
            }
        }
        
        // Enrich with device information
        if (event.getDeviceId() != null) {
            DeviceProfile deviceProfile = deviceAnalysisService.getDeviceProfile(event.getDeviceId());
            if (deviceProfile != null) {
                event.setKnownDevice(deviceProfile.isKnownDevice());
                event.setDeviceRiskScore(deviceProfile.getRiskScore());
                event.setDeviceFirstSeen(deviceProfile.getFirstSeen());
                event.setDeviceLastSeen(deviceProfile.getLastSeen());
            }
        }
        
        // Enrich with recent authentication history
        AuthenticationHistory authHistory = anomalyRepository.getRecentAuthHistory(
            event.getUserId(),
            Instant.now().minus(24, ChronoUnit.HOURS)
        );
        
        event.setRecentAuthCount(authHistory.getTotalAttempts());
        event.setRecentFailedAuthCount(authHistory.getFailedAttempts());
        event.setRecentSuccessfulAuthCount(authHistory.getSuccessfulAttempts());
        event.setRecentLocationCount(authHistory.getUniqueLocations());
        event.setRecentDeviceCount(authHistory.getUniqueDevices());
        
        return event;
    }

    private AnomalyDetectionResult performAnomalyDetection(AuthenticationEvent event) {
        AnomalyDetectionResult result = new AnomalyDetectionResult();
        result.setEventId(event.getEventId());
        result.setUserId(event.getUserId());
        result.setDetectionTime(Instant.now());
        
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        
        // Geographic anomaly detection
        anomalies.addAll(detectGeographicAnomalies(event));
        
        // Temporal anomaly detection
        anomalies.addAll(detectTemporalAnomalies(event));
        
        // Device anomaly detection
        anomalies.addAll(detectDeviceAnomalies(event));
        
        // Behavioral anomaly detection
        anomalies.addAll(detectBehavioralAnomalies(event));
        
        // Pattern-based anomaly detection
        anomalies.addAll(detectPatternAnomalies(event));
        
        // ML-based anomaly detection
        anomalies.addAll(detectMLAnomalies(event));
        
        result.setDetectedAnomalies(anomalies);
        
        // Calculate overall risk score
        int riskScore = calculateRiskScore(anomalies, event);
        result.setRiskScore(riskScore);
        
        // Determine risk level
        result.setRiskLevel(determineRiskLevel(riskScore, anomalies));
        
        // Determine required actions
        result.setRequiresAdaptiveAuth(requiresAdaptiveAuth(anomalies, riskScore));
        result.setEnablesProfileUpdate(enablesProfileUpdate(event, riskScore));
        result.setRequiresDeviceAnalysis(requiresDeviceAnalysis(anomalies));
        result.setEnablesMLUpdate(enablesMLUpdate(event, anomalies));
        result.setHasSecurityWorkflows(hasSecurityWorkflows(anomalies, riskScore));
        
        // Generate recommendations
        result.setRecommendations(generateRecommendations(anomalies, riskScore));
        
        return result;
    }

    private List<DetectedAnomaly> detectGeographicAnomalies(AuthenticationEvent event) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        
        // Impossible travel detection
        if (event.getLastLoginLocation() != null && 
            event.getLatitude() != null && event.getLongitude() != null) {
            
            double distance = geolocationService.calculateDistance(
                event.getLastLoginLocation().getLatitude(),
                event.getLastLoginLocation().getLongitude(),
                event.getLatitude(),
                event.getLongitude()
            );
            
            long timeDiff = ChronoUnit.MINUTES.between(
                event.getLastLoginTime(),
                event.getTimestamp()
            );
            
            double maxPossibleSpeed = 1000; // km/h (generous for air travel)
            double requiredSpeed = (distance / 1000.0) / (timeDiff / 60.0); // km/h
            
            if (requiredSpeed > maxPossibleSpeed) {
                anomalies.add(DetectedAnomaly.builder()
                    .anomalyType("IMPOSSIBLE_TRAVEL")
                    .severity(AnomalySeverity.HIGH)
                    .confidence(0.95)
                    .description(String.format("Impossible travel: %.2f km in %d minutes (%.2f km/h required)", 
                                              distance / 1000.0, timeDiff, requiredSpeed))
                    .evidence(Map.of(
                        "distance_km", distance / 1000.0,
                        "time_minutes", timeDiff,
                        "required_speed_kmh", requiredSpeed,
                        "previous_location", event.getLastLoginLocation(),
                        "current_location", Map.of("lat", event.getLatitude(), "lon", event.getLongitude())
                    ))
                    .riskScore(85)
                    .detectedAt(Instant.now())
                    .build());
            }
        }
        
        // High-risk country detection
        if (isHighRiskCountry(event.getCountry())) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("HIGH_RISK_COUNTRY")
                .severity(AnomalySeverity.MEDIUM)
                .confidence(0.8)
                .description("Authentication from high-risk country: " + event.getCountry())
                .evidence(Map.of("country", event.getCountry()))
                .riskScore(60)
                .detectedAt(Instant.now())
                .build());
        }
        
        // New country detection
        if (event.getTypicalLocations() != null && 
            !event.getTypicalLocations().contains(event.getCountry())) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("NEW_COUNTRY")
                .severity(AnomalySeverity.LOW)
                .confidence(0.7)
                .description("Authentication from new country: " + event.getCountry())
                .evidence(Map.of(
                    "new_country", event.getCountry(),
                    "typical_countries", event.getTypicalLocations()
                ))
                .riskScore(40)
                .detectedAt(Instant.now())
                .build());
        }
        
        return anomalies;
    }

    private List<DetectedAnomaly> detectTemporalAnomalies(AuthenticationEvent event) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        
        // Off-hours authentication
        if (event.getTypicalLoginTimes() != null) {
            int currentHour = event.getTimestamp().atZone(java.time.ZoneId.systemDefault()).getHour();
            
            if (!event.getTypicalLoginTimes().contains(currentHour)) {
                anomalies.add(DetectedAnomaly.builder()
                    .anomalyType("OFF_HOURS_LOGIN")
                    .severity(AnomalySeverity.LOW)
                    .confidence(0.6)
                    .description("Authentication outside typical hours")
                    .evidence(Map.of(
                        "current_hour", currentHour,
                        "typical_hours", event.getTypicalLoginTimes()
                    ))
                    .riskScore(30)
                    .detectedAt(Instant.now())
                    .build());
            }
        }
        
        // Rapid authentication attempts
        if (event.getRecentAuthCount() > 10) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("RAPID_AUTH_ATTEMPTS")
                .severity(AnomalySeverity.HIGH)
                .confidence(0.9)
                .description("Unusually high number of authentication attempts")
                .evidence(Map.of("recent_attempts", event.getRecentAuthCount()))
                .riskScore(70)
                .detectedAt(Instant.now())
                .build());
        }
        
        // High failure rate
        if (event.getRecentFailedAuthCount() > 5) {
            double failureRate = (double) event.getRecentFailedAuthCount() / event.getRecentAuthCount();
            if (failureRate > 0.5) {
                anomalies.add(DetectedAnomaly.builder()
                    .anomalyType("HIGH_FAILURE_RATE")
                    .severity(AnomalySeverity.HIGH)
                    .confidence(0.85)
                    .description(String.format("High authentication failure rate: %.2f%%", failureRate * 100))
                    .evidence(Map.of(
                        "failed_attempts", event.getRecentFailedAuthCount(),
                        "total_attempts", event.getRecentAuthCount(),
                        "failure_rate", failureRate
                    ))
                    .riskScore(75)
                    .detectedAt(Instant.now())
                    .build());
            }
        }
        
        return anomalies;
    }

    private List<DetectedAnomaly> detectDeviceAnomalies(AuthenticationEvent event) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        
        // Unknown device
        if (!event.isKnownDevice()) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("UNKNOWN_DEVICE")
                .severity(AnomalySeverity.MEDIUM)
                .confidence(0.8)
                .description("Authentication from unknown device")
                .evidence(Map.of(
                    "device_id", event.getDeviceId(),
                    "device_fingerprint", event.getDeviceFingerprint()
                ))
                .riskScore(50)
                .detectedAt(Instant.now())
                .build());
        }
        
        // High-risk device
        if (event.getDeviceRiskScore() != null && event.getDeviceRiskScore() > 70) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("HIGH_RISK_DEVICE")
                .severity(AnomalySeverity.HIGH)
                .confidence(0.85)
                .description("Authentication from high-risk device")
                .evidence(Map.of("device_risk_score", event.getDeviceRiskScore()))
                .riskScore(event.getDeviceRiskScore())
                .detectedAt(Instant.now())
                .build());
        }
        
        // Multiple devices
        if (event.getRecentDeviceCount() > 5) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("MULTIPLE_DEVICES")
                .severity(AnomalySeverity.MEDIUM)
                .confidence(0.7)
                .description("Authentication from multiple devices recently")
                .evidence(Map.of("unique_devices", event.getRecentDeviceCount()))
                .riskScore(45)
                .detectedAt(Instant.now())
                .build());
        }
        
        // Device fingerprint mismatch
        if (event.getDeviceId() != null && event.getDeviceFingerprint() != null) {
            DeviceProfile deviceProfile = deviceAnalysisService.getDeviceProfile(event.getDeviceId());
            if (deviceProfile != null && 
                !deviceProfile.getFingerprint().equals(event.getDeviceFingerprint())) {
                anomalies.add(DetectedAnomaly.builder()
                    .anomalyType("DEVICE_FINGERPRINT_MISMATCH")
                    .severity(AnomalySeverity.HIGH)
                    .confidence(0.9)
                    .description("Device fingerprint doesn't match known device")
                    .evidence(Map.of(
                        "expected_fingerprint", deviceProfile.getFingerprint(),
                        "actual_fingerprint", event.getDeviceFingerprint()
                    ))
                    .riskScore(80)
                    .detectedAt(Instant.now())
                    .build());
            }
        }
        
        return anomalies;
    }

    private List<DetectedAnomaly> detectBehavioralAnomalies(AuthenticationEvent event) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        
        // User agent anomaly
        if (event.getUserAgent() != null) {
            UserAgentAnalysisResult uaAnalysis = behavioralAuthService.analyzeUserAgent(
                event.getUserId(),
                event.getUserAgent()
            );
            
            if (uaAnalysis.isAnomalous()) {
                anomalies.add(DetectedAnomaly.builder()
                    .anomalyType("USER_AGENT_ANOMALY")
                    .severity(AnomalySeverity.MEDIUM)
                    .confidence(uaAnalysis.getConfidence())
                    .description("Unusual user agent pattern")
                    .evidence(Map.of(
                        "user_agent", event.getUserAgent(),
                        "typical_user_agents", uaAnalysis.getTypicalUserAgents()
                    ))
                    .riskScore(55)
                    .detectedAt(Instant.now())
                    .build());
            }
        }
        
        // Authentication timing anomaly
        if (event.getTimeToComplete() != null) {
            TimingAnalysisResult timingAnalysis = behavioralAuthService.analyzeAuthTiming(
                event.getUserId(),
                event.getTimeToComplete()
            );
            
            if (timingAnalysis.isAnomalous()) {
                anomalies.add(DetectedAnomaly.builder()
                    .anomalyType("AUTH_TIMING_ANOMALY")
                    .severity(AnomalySeverity.LOW)
                    .confidence(timingAnalysis.getConfidence())
                    .description("Unusual authentication completion time")
                    .evidence(Map.of(
                        "completion_time_ms", event.getTimeToComplete(),
                        "typical_range_ms", timingAnalysis.getTypicalRange()
                    ))
                    .riskScore(35)
                    .detectedAt(Instant.now())
                    .build());
            }
        }
        
        // Browser configuration anomaly
        BrowserConfigAnalysisResult browserAnalysis = behavioralAuthService.analyzeBrowserConfig(
            event.getUserId(),
            BrowserConfig.builder()
                .browserName(event.getBrowserName())
                .browserVersion(event.getBrowserVersion())
                .osName(event.getOsName())
                .osVersion(event.getOsVersion())
                .screenResolution(event.getScreenResolution())
                .timezone(event.getTimezone())
                .language(event.getLanguage())
                .cookiesEnabled(event.isCookiesEnabled())
                .javaScriptEnabled(event.isJavaScriptEnabled())
                .pluginsInstalled(event.getPluginsInstalled())
                .build()
        );
        
        if (browserAnalysis.isAnomalous()) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("BROWSER_CONFIG_ANOMALY")
                .severity(AnomalySeverity.MEDIUM)
                .confidence(browserAnalysis.getConfidence())
                .description("Unusual browser configuration")
                .evidence(Map.of(
                    "browser_config", browserAnalysis.getCurrentConfig(),
                    "typical_configs", browserAnalysis.getTypicalConfigs()
                ))
                .riskScore(60)
                .detectedAt(Instant.now())
                .build());
        }
        
        return anomalies;
    }

    private List<DetectedAnomaly> detectPatternAnomalies(AuthenticationEvent event) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        
        // Credential stuffing pattern
        CredentialStuffingResult stuffingResult = behavioralAuthService.detectCredentialStuffing(
            event.getSourceIp(),
            event.getUserAgent(),
            Instant.now().minus(1, ChronoUnit.HOURS)
        );
        
        if (stuffingResult.isDetected()) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("CREDENTIAL_STUFFING")
                .severity(AnomalySeverity.HIGH)
                .confidence(stuffingResult.getConfidence())
                .description("Credential stuffing attack pattern detected")
                .evidence(Map.of(
                    "affected_accounts", stuffingResult.getAffectedAccounts(),
                    "attempt_rate", stuffingResult.getAttemptRate()
                ))
                .riskScore(85)
                .detectedAt(Instant.now())
                .build());
        }
        
        // Brute force pattern
        BruteForceResult bruteForceResult = behavioralAuthService.detectBruteForce(
            event.getUserId(),
            event.getSourceIp(),
            Instant.now().minus(30, ChronoUnit.MINUTES)
        );
        
        if (bruteForceResult.isDetected()) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("BRUTE_FORCE_PATTERN")
                .severity(AnomalySeverity.HIGH)
                .confidence(bruteForceResult.getConfidence())
                .description("Brute force attack pattern detected")
                .evidence(Map.of(
                    "attempt_count", bruteForceResult.getAttemptCount(),
                    "time_window_minutes", 30
                ))
                .riskScore(80)
                .detectedAt(Instant.now())
                .build());
        }
        
        return anomalies;
    }

    private List<DetectedAnomaly> detectMLAnomalies(AuthenticationEvent event) {
        List<DetectedAnomaly> anomalies = new ArrayList<>();
        
        // ML-based behavioral anomaly detection
        MLAnomalyResult mlResult = mlModelService.detectBehavioralAnomaly(
            event.getUserId(),
            MLFeatureVector.fromAuthEvent(event)
        );
        
        if (mlResult.isAnomalous()) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("ML_BEHAVIORAL_ANOMALY")
                .severity(mlResult.getSeverity())
                .confidence(mlResult.getConfidence())
                .description("Machine learning detected behavioral anomaly")
                .evidence(Map.of(
                    "anomaly_score", mlResult.getAnomalyScore(),
                    "contributing_features", mlResult.getContributingFeatures()
                ))
                .riskScore((int) (mlResult.getAnomalyScore() * 100))
                .detectedAt(Instant.now())
                .build());
        }
        
        // Ensemble anomaly detection
        EnsembleAnomalyResult ensembleResult = mlModelService.detectEnsembleAnomaly(
            event.getUserId(),
            event
        );
        
        if (ensembleResult.isAnomalous()) {
            anomalies.add(DetectedAnomaly.builder()
                .anomalyType("ENSEMBLE_ANOMALY")
                .severity(ensembleResult.getSeverity())
                .confidence(ensembleResult.getConfidence())
                .description("Ensemble model detected anomaly")
                .evidence(Map.of(
                    "model_scores", ensembleResult.getModelScores(),
                    "consensus_score", ensembleResult.getConsensusScore()
                ))
                .riskScore((int) (ensembleResult.getConsensusScore() * 100))
                .detectedAt(Instant.now())
                .build());
        }
        
        return anomalies;
    }

    private int calculateRiskScore(List<DetectedAnomaly> anomalies, AuthenticationEvent event) {
        if (anomalies.isEmpty()) {
            return 10; // Minimal risk for no anomalies
        }
        
        // Base score from anomalies
        double totalScore = 0.0;
        double totalWeight = 0.0;
        
        for (DetectedAnomaly anomaly : anomalies) {
            double weight = getAnomalyWeight(anomaly.getAnomalyType());
            totalScore += anomaly.getRiskScore() * weight * anomaly.getConfidence();
            totalWeight += weight;
        }
        
        int averageScore = (int) (totalScore / totalWeight);
        
        // Adjust based on context
        if (event.getAuthResult() == AuthResult.FAILED) {
            averageScore = Math.min(100, averageScore + 15);
        }
        
        if ("HIGH".equals(event.getUserRiskLevel())) {
            averageScore = Math.min(100, averageScore + 10);
        }
        
        // Critical anomaly boost
        boolean hasCriticalAnomaly = anomalies.stream()
            .anyMatch(a -> HIGH_RISK_ANOMALIES.contains(a.getAnomalyType()));
        
        if (hasCriticalAnomaly) {
            averageScore = Math.min(100, averageScore + 20);
        }
        
        return averageScore;
    }

    private double getAnomalyWeight(String anomalyType) {
        Map<String, Double> weights = Map.of(
            "IMPOSSIBLE_TRAVEL", 1.0,
            "CREDENTIAL_STUFFING", 0.9,
            "BRUTE_FORCE_PATTERN", 0.9,
            "DEVICE_FINGERPRINT_MISMATCH", 0.8,
            "HIGH_RISK_DEVICE", 0.7,
            "HIGH_RISK_COUNTRY", 0.6,
            "UNKNOWN_DEVICE", 0.5,
            "OFF_HOURS_LOGIN", 0.3
        );
        
        return weights.getOrDefault(anomalyType, 0.5);
    }

    private String determineRiskLevel(int riskScore, List<DetectedAnomaly> anomalies) {
        // Check for critical anomalies first
        boolean hasCriticalAnomaly = anomalies.stream()
            .anyMatch(a -> HIGH_RISK_ANOMALIES.contains(a.getAnomalyType()));
        
        if (hasCriticalAnomaly || riskScore >= 80) {
            return "CRITICAL";
        }
        
        if (riskScore >= 65) {
            return "HIGH";
        }
        
        if (riskScore >= 45) {
            return "MEDIUM";
        }
        
        if (riskScore >= 25) {
            return "LOW";
        }
        
        return "MINIMAL";
    }

    private boolean requiresAdaptiveAuth(List<DetectedAnomaly> anomalies, int riskScore) {
        return riskScore >= 50 ||
               anomalies.stream().anyMatch(a -> a.getSeverity().ordinal() >= AnomalySeverity.MEDIUM.ordinal());
    }

    private boolean enablesProfileUpdate(AuthenticationEvent event, int riskScore) {
        return event.getAuthResult() == AuthResult.SUCCESS && riskScore < 70;
    }

    private boolean requiresDeviceAnalysis(List<DetectedAnomaly> anomalies) {
        return anomalies.stream()
            .anyMatch(a -> a.getAnomalyType().contains("DEVICE") || a.getAnomalyType().contains("FINGERPRINT"));
    }

    private boolean enablesMLUpdate(AuthenticationEvent event, List<DetectedAnomaly> anomalies) {
        return event.getAuthResult() == AuthResult.SUCCESS || !anomalies.isEmpty();
    }

    private boolean hasSecurityWorkflows(List<DetectedAnomaly> anomalies, int riskScore) {
        return riskScore >= 60 ||
               anomalies.stream().anyMatch(a -> HIGH_RISK_ANOMALIES.contains(a.getAnomalyType()));
    }

    private List<String> generateRecommendations(List<DetectedAnomaly> anomalies, int riskScore) {
        List<String> recommendations = new ArrayList<>();
        
        if (riskScore >= 80) {
            recommendations.add("REQUIRE_ADDITIONAL_AUTHENTICATION");
            recommendations.add("BLOCK_LOGIN_ATTEMPT");
            recommendations.add("NOTIFY_SECURITY_TEAM");
        } else if (riskScore >= 65) {
            recommendations.add("REQUIRE_MFA");
            recommendations.add("ENHANCED_MONITORING");
        } else if (riskScore >= 45) {
            recommendations.add("REQUIRE_EMAIL_VERIFICATION");
            recommendations.add("INCREASE_SESSION_TIMEOUT");
        }
        
        // Specific recommendations based on anomaly types
        for (DetectedAnomaly anomaly : anomalies) {
            switch (anomaly.getAnomalyType()) {
                case "IMPOSSIBLE_TRAVEL":
                    recommendations.add("VERIFY_ACCOUNT_OWNERSHIP");
                    break;
                case "UNKNOWN_DEVICE":
                    recommendations.add("DEVICE_REGISTRATION_REQUIRED");
                    break;
                case "CREDENTIAL_STUFFING":
                case "BRUTE_FORCE_PATTERN":
                    recommendations.add("TEMPORARILY_LOCK_ACCOUNT");
                    break;
                case "HIGH_RISK_COUNTRY":
                    recommendations.add("ADDITIONAL_IDENTITY_VERIFICATION");
                    break;
            }
        }
        
        return recommendations.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    private AuthAnomalyProcessingResult processAuthAnomalies(AuthenticationEvent event, 
                                                           AnomalyDetectionResult anomalyResult) {
        AuthAnomalyProcessingResult result = new AuthAnomalyProcessingResult();
        result.setEventId(event.getEventId());
        result.setUserId(event.getUserId());
        result.setAnomalyResult(anomalyResult);
        result.setProcessingStartTime(Instant.now());
        
        try {
            // Save authentication event
            AuthenticationEvent savedEvent = anomalyRepository.saveAuthEvent(event);
            result.setSavedEvent(savedEvent);
            
            // Save detected anomalies
            List<AuthAnomaly> savedAnomalies = new ArrayList<>();
            for (DetectedAnomaly anomaly : anomalyResult.getDetectedAnomalies()) {
                AuthAnomaly authAnomaly = AuthAnomaly.builder()
                    .anomalyId(UUID.randomUUID().toString())
                    .eventId(event.getEventId())
                    .userId(event.getUserId())
                    .anomalyType(anomaly.getAnomalyType())
                    .severity(anomaly.getSeverity())
                    .confidence(anomaly.getConfidence())
                    .description(anomaly.getDescription())
                    .evidence(anomaly.getEvidence())
                    .riskScore(anomaly.getRiskScore())
                    .status(AnomalyStatus.ACTIVE)
                    .detectedAt(anomaly.getDetectedAt())
                    .createdAt(Instant.now())
                    .build();
                
                savedAnomalies.add(anomalyRepository.saveAnomaly(authAnomaly));
            }
            
            result.setSavedAnomalies(savedAnomalies);
            result.setStatus(ProcessingStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Failed to process auth anomalies: {}", event.getEventId(), e);
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new AnomalyProcessingException("Anomaly processing failed", e);
        }
        
        result.setProcessingEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private void applyAdaptiveAuthControls(AuthenticationEvent event, AnomalyDetectionResult anomalyResult, 
                                         AuthAnomalyProcessingResult result) {
        List<String> appliedControls = new ArrayList<>();
        
        // High-risk controls
        if (anomalyResult.getRiskScore() >= 80) {
            adaptiveAuthService.requireStepUpAuth(event.getUserId(), "HIGH_RISK_ANOMALY");
            appliedControls.add("STEP_UP_AUTH_REQUIRED");
            
            adaptiveAuthService.blockSession(event.getSessionId(), "CRITICAL_ANOMALY_DETECTED");
            appliedControls.add("SESSION_BLOCKED");
        }
        
        // Medium-risk controls
        else if (anomalyResult.getRiskScore() >= 50) {
            adaptiveAuthService.requireMFA(event.getUserId(), "MEDIUM_RISK_ANOMALY");
            appliedControls.add("MFA_REQUIRED");
            
            adaptiveAuthService.reducedSessionTimeout(event.getSessionId(), 30); // 30 minutes
            appliedControls.add("REDUCED_SESSION_TIMEOUT");
        }
        
        // Device-specific controls
        boolean hasDeviceAnomaly = anomalyResult.getDetectedAnomalies().stream()
            .anyMatch(a -> a.getAnomalyType().contains("DEVICE"));
        
        if (hasDeviceAnomaly) {
            adaptiveAuthService.requireDeviceVerification(event.getUserId(), event.getDeviceId());
            appliedControls.add("DEVICE_VERIFICATION_REQUIRED");
        }
        
        // Location-specific controls
        boolean hasLocationAnomaly = anomalyResult.getDetectedAnomalies().stream()
            .anyMatch(a -> a.getAnomalyType().contains("TRAVEL") || a.getAnomalyType().contains("COUNTRY"));
        
        if (hasLocationAnomaly) {
            adaptiveAuthService.requireLocationVerification(event.getUserId(), event.getSourceIp());
            appliedControls.add("LOCATION_VERIFICATION_REQUIRED");
        }
        
        result.setAppliedControls(appliedControls);
    }

    private void updateUserBehavioralProfile(AuthenticationEvent event, AnomalyDetectionResult anomalyResult) {
        // Update only for successful authentications with low-medium risk
        if (event.getAuthResult() == AuthResult.SUCCESS && anomalyResult.getRiskScore() < 70) {
            behavioralAuthService.updateUserProfile(
                event.getUserId(),
                UserBehaviorUpdate.builder()
                    .loginLocation(Map.of(
                        "country", event.getCountry(),
                        "region", event.getRegion(),
                        "city", event.getCity()
                    ))
                    .loginTime(event.getTimestamp())
                    .deviceId(event.getDeviceId())
                    .userAgent(event.getUserAgent())
                    .ipAddress(event.getSourceIp())
                    .build()
            );
        }
    }

    private void performDeviceAnalysis(AuthenticationEvent event, AuthAnomalyProcessingResult result) {
        if (event.getDeviceId() != null) {
            DeviceAnalysisResult deviceAnalysis = deviceAnalysisService.analyzeDevice(
                event.getDeviceId(),
                event.getDeviceFingerprint(),
                event.getUserAgent()
            );
            
            result.setDeviceAnalysis(deviceAnalysis);
            
            // Update device profile
            deviceAnalysisService.updateDeviceProfile(
                event.getDeviceId(),
                event.getUserId(),
                deviceAnalysis
            );
        }
    }

    private void updateMLModels(AuthenticationEvent event, AnomalyDetectionResult anomalyResult) {
        // Update behavioral models
        mlModelService.updateBehavioralModel(
            event.getUserId(),
            MLTrainingData.fromAuthEvent(event),
            anomalyResult.getDetectedAnomalies()
        );
        
        // Update global anomaly detection models
        mlModelService.updateGlobalAnomalyModel(
            MLFeatureVector.fromAuthEvent(event),
            anomalyResult.getRiskScore()
        );
    }

    private void triggerSecurityWorkflows(AuthenticationEvent event, AnomalyDetectionResult anomalyResult) {
        List<String> workflows = getSecurityWorkflows(anomalyResult.getRiskLevel(), anomalyResult.getDetectedAnomalies());
        
        for (String workflowType : workflows) {
            CompletableFuture.runAsync(() -> {
                try {
                    workflowService.triggerWorkflow(workflowType, event, anomalyResult);
                } catch (Exception e) {
                    log.error("Failed to trigger workflow {} for auth anomaly {}", 
                             workflowType, event.getEventId(), e);
                }
            });
        }
    }

    private List<String> getSecurityWorkflows(String riskLevel, List<DetectedAnomaly> anomalies) {
        Map<String, List<String>> workflowMapping = Map.of(
            "CRITICAL", Arrays.asList("CRITICAL_AUTH_INVESTIGATION", "ACCOUNT_SECURITY_REVIEW", "INCIDENT_RESPONSE"),
            "HIGH", Arrays.asList("HIGH_RISK_AUTH_REVIEW", "ENHANCED_MONITORING", "SECURITY_ALERT"),
            "MEDIUM", Arrays.asList("AUTH_ANOMALY_REVIEW", "USER_NOTIFICATION"),
            "LOW", Arrays.asList("BEHAVIORAL_ANALYSIS_UPDATE")
        );
        
        return workflowMapping.getOrDefault(riskLevel, Arrays.asList("BEHAVIORAL_ANALYSIS_UPDATE"));
    }

    private void sendAnomalyNotifications(AuthenticationEvent event, AnomalyDetectionResult anomalyResult, 
                                        AuthAnomalyProcessingResult result) {
        
        Map<String, Object> notificationData = Map.of(
            "eventId", event.getEventId(),
            "userId", event.getUserId(),
            "riskScore", anomalyResult.getRiskScore(),
            "riskLevel", anomalyResult.getRiskLevel(),
            "anomalyCount", anomalyResult.getDetectedAnomalies().size(),
            "sourceIp", event.getSourceIp(),
            "location", event.getCountry() + ", " + event.getCity(),
            "authResult", event.getAuthResult().toString()
        );
        
        // Critical risk notifications
        if ("CRITICAL".equals(anomalyResult.getRiskLevel())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalAuthAnomalyAlert(notificationData);
                notificationService.sendUserSecurityAlert(event.getUserId(), notificationData);
            });
        }
        
        // High risk notifications
        if (anomalyResult.getRiskScore() >= 65) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendHighRiskAuthAlert(notificationData);
            });
        }
        
        // User notifications for successful logins with anomalies
        if (event.getAuthResult() == AuthResult.SUCCESS && anomalyResult.getRiskScore() >= 45) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendUserLoginAlert(event.getUserId(), notificationData);
            });
        }
        
        // Team notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendTeamNotification(
                "SECURITY_TEAM",
                "AUTH_ANOMALY_DETECTED",
                notificationData
            );
        });
    }

    private void updateSecurityMonitoring(AuthenticationEvent event, AuthAnomalyProcessingResult result) {
        // Update authentication monitoring dashboard
        dashboardService.updateAuthDashboard(event, result);
        
        // Update user security profiles
        userSecurityService.updateSecurityProfile(event.getUserId(), result);
        
        // Update device monitoring
        deviceMonitoringService.updateDeviceMetrics(event.getDeviceId(), result);
    }

    private void auditAnomalyDetection(AuthenticationEvent event, AuthAnomalyProcessingResult result, 
                                     GenericKafkaEvent originalEvent) {
        auditService.auditAuthAnomaly(
            event.getEventId(),
            event.getUserId(),
            event.getSourceIp(),
            result.getAnomalyResult().getDetectedAnomalies().size(),
            result.getAnomalyResult().getRiskScore(),
            result.getAnomalyResult().getRiskLevel(),
            originalEvent.getEventId()
        );
    }

    private void recordAnomalyMetrics(AuthenticationEvent event, AuthAnomalyProcessingResult result, 
                                    long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordAuthAnomalyMetrics(
            event.getAuthMethod().toString(),
            event.getAuthResult().toString(),
            result.getAnomalyResult().getDetectedAnomalies().size(),
            result.getAnomalyResult().getRiskScore(),
            result.getAnomalyResult().getRiskLevel(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS
        );
        
        // Record anomaly type metrics
        for (DetectedAnomaly anomaly : result.getAnomalyResult().getDetectedAnomalies()) {
            metricsService.recordAnomalyTypeMetrics(
                anomaly.getAnomalyType(),
                anomaly.getSeverity().toString(),
                anomaly.getConfidence()
            );
        }
    }

    private boolean isHighRiskCountry(String country) {
        Set<String> highRiskCountries = Set.of(
            "CN", "RU", "IR", "KP", "SY", "IQ", "AF", "LY", "SO", "SD"
        );
        return highRiskCountries.contains(country);
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("auth-anomaly-validation-errors", event);
    }

    private void handleCriticalAnomalyError(GenericKafkaEvent event, CriticalAnomalyException e) {
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_AUTH_ANOMALY_PROCESSING_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("auth-anomaly-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying auth anomaly {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("auth-anomaly-detection-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for auth anomaly {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "auth-anomaly-detection");
        
        kafkaTemplate.send("auth-anomaly-detection.DLQ", event);
        
        alertingService.createDLQAlert(
            "auth-anomaly-detection",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleAuthAnomalyFailure(GenericKafkaEvent event, String topic, int partition,
                                       long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for auth anomaly detection: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Auth Anomaly Detection Circuit Breaker Open",
            "Authentication anomaly detection is failing. Account security compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Long extractLong(Map<String, Object> map, String key, Long defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Double extractDouble(Map<String, Object> map, String key, Double defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private Boolean extractBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class CriticalAnomalyException extends RuntimeException {
        public CriticalAnomalyException(String message) {
            super(message);
        }
    }

    public static class AnomalyProcessingException extends RuntimeException {
        public AnomalyProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}