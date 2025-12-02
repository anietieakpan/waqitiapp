package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.DeviceSecurityService;
import com.waqiti.security.service.MobileThreatDefenseService;
import com.waqiti.security.service.IncidentResponseService;
import com.waqiti.security.service.UserSecurityService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.resilience.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceCompromiseDetectedConsumer {
    
    private final DeviceSecurityService deviceSecurityService;
    private final MobileThreatDefenseService mobileThreatDefenseService;
    private final IncidentResponseService incidentResponseService;
    private final UserSecurityService userSecurityService;
    private final AuditService auditService;
    private final CircuitBreakerService circuitBreakerService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    // Idempotency tracking with 24-hour TTL
    private final ConcurrentHashMap<String, LocalDateTime> processedEvents = new ConcurrentHashMap<>();
    
    private Counter eventProcessedCounter;
    private Counter eventFailedCounter;
    private Timer processingTimer;
    
    @PostConstruct
    public void initMetrics() {
        eventProcessedCounter = Counter.builder("device_compromise_detected_processed_total")
                .description("Total number of device compromise events processed")
                .register(meterRegistry);
        
        eventFailedCounter = Counter.builder("device_compromise_detected_failed_total")
                .description("Total number of device compromise events that failed processing")
                .register(meterRegistry);
        
        processingTimer = Timer.builder("device_compromise_detected_processing_duration")
                .description("Time taken to process device compromise events")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = {"device-compromise-detected", "mobile-device-threats", "device-security-alerts"},
        groupId = "security-service-device-compromise-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @CircuitBreaker(name = "device-compromise-detected", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "device-compromise-detected")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleDeviceCompromiseDetected(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("SECURITY ALERT: Processing device compromise detection - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        String alertId = null;
        UUID userId = null;
        String deviceId = null;
        String threatType = null;
        String severity = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            alertId = (String) event.get("alertId");
            userId = UUID.fromString((String) event.get("userId"));
            deviceId = (String) event.get("deviceId");
            threatType = (String) event.get("threatType");
            severity = (String) event.get("severity");
            LocalDateTime detectionTime = LocalDateTime.parse((String) event.get("detectionTime"));
            String deviceType = (String) event.get("deviceType");
            String operatingSystem = (String) event.get("operatingSystem");
            String osVersion = (String) event.getOrDefault("osVersion", "");
            String appVersion = (String) event.getOrDefault("appVersion", "");
            String detectionSource = (String) event.get("detectionSource");
            String detectionMethod = (String) event.get("detectionMethod");
            @SuppressWarnings("unchecked")
            List<String> compromiseIndicators = (List<String>) event.get("compromiseIndicators");
            @SuppressWarnings("unchecked")
            List<String> maliciousApps = (List<String>) event.getOrDefault("maliciousApps", List.of());
            Boolean rootedJailbroken = (Boolean) event.getOrDefault("rootedJailbroken", false);
            Boolean debuggerDetected = (Boolean) event.getOrDefault("debuggerDetected", false);
            Boolean emulatorDetected = (Boolean) event.getOrDefault("emulatorDetected", false);
            Boolean screenRecordingDetected = (Boolean) event.getOrDefault("screenRecordingDetected", false);
            String ipAddress = (String) event.getOrDefault("ipAddress", "");
            String geolocation = (String) event.getOrDefault("geolocation", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> deviceFingerprint = (Map<String, Object>) event.getOrDefault("deviceFingerprint", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> threatDetails = (Map<String, Object>) event.getOrDefault("threatDetails", Map.of());
            
            log.info("Device compromise detected - AlertId: {}, UserId: {}, DeviceId: {}, ThreatType: {}, Severity: {}, Indicators: {}", 
                    alertId, userId, deviceId, threatType, severity, compromiseIndicators.size());
            
            // Check idempotency
            String eventKey = alertId + "_" + deviceId + "_" + userId.toString();
            if (isAlreadyProcessed(eventKey)) {
                log.warn("Device compromise already processed, skipping: alertId={}, deviceId={}", 
                        alertId, deviceId);
                acknowledgment.acknowledge();
                return;
            }
            
            validateDeviceCompromiseEvent(alertId, userId, deviceId, threatType, severity, 
                    detectionTime, compromiseIndicators);
            
            // Immediate device security actions
            executeImmediateDeviceSecurity(alertId, userId, deviceId, threatType, severity, 
                    compromiseIndicators, maliciousApps, rootedJailbroken);
            
            // Create security incident
            createDeviceSecurityIncident(alertId, userId, deviceId, threatType, severity, 
                    detectionTime, deviceType, operatingSystem, detectionSource, 
                    compromiseIndicators, threatDetails);
            
            // Analyze threat intelligence
            analyzeThreatIntelligence(alertId, userId, deviceId, threatType, 
                    compromiseIndicators, maliciousApps, deviceFingerprint, threatDetails);
            
            // Execute user security measures
            executeUserSecurityMeasures(alertId, userId, deviceId, threatType, severity, 
                    compromiseIndicators, ipAddress, geolocation);
            
            // Perform device forensics
            performDeviceForensics(alertId, userId, deviceId, threatType, deviceType, 
                    operatingSystem, osVersion, appVersion, deviceFingerprint, threatDetails);
            
            // Update device trust score
            updateDeviceTrustScore(alertId, userId, deviceId, threatType, severity, 
                    compromiseIndicators, rootedJailbroken, debuggerDetected, emulatorDetected);
            
            // Execute mobile threat defense
            executeMobileThreatDefense(alertId, userId, deviceId, threatType, severity, 
                    maliciousApps, compromiseIndicators, screenRecordingDetected);
            
            // Setup enhanced monitoring
            setupEnhancedDeviceMonitoring(alertId, userId, deviceId, threatType, 
                    compromiseIndicators, ipAddress, geolocation);
            
            // Mark as processed
            markEventAsProcessed(eventKey);
            
            auditDeviceCompromiseEvent(alertId, userId, deviceId, threatType, severity, 
                    compromiseIndicators, processingStartTime);
            
            eventProcessedCounter.increment();
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Device compromise processed successfully - AlertId: {}, DeviceId: {}, ThreatType: {}, ProcessingTime: {}ms", 
                    alertId, deviceId, threatType, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            eventFailedCounter.increment();
            log.error("CRITICAL: Device compromise processing failed - AlertId: {}, UserId: {}, DeviceId: {}, ThreatType: {}, Error: {}", 
                    alertId, userId, deviceId, threatType, e.getMessage(), e);
            
            if (alertId != null && userId != null && deviceId != null) {
                handleProcessingFailure(alertId, userId, deviceId, threatType, severity, e);
            }
            
            throw new RuntimeException("Device compromise processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    private void validateDeviceCompromiseEvent(String alertId, UUID userId, String deviceId, 
                                             String threatType, String severity, 
                                             LocalDateTime detectionTime, List<String> compromiseIndicators) {
        if (alertId == null || alertId.trim().isEmpty()) {
            throw new IllegalArgumentException("Alert ID is required");
        }
        
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID is required");
        }
        
        if (threatType == null || threatType.trim().isEmpty()) {
            throw new IllegalArgumentException("Threat type is required");
        }
        
        List<String> validThreatTypes = List.of("MALWARE", "ROOTED_DEVICE", "JAILBROKEN_DEVICE", 
                "DEBUGGING_DETECTED", "EMULATOR_DETECTED", "SCREEN_RECORDING", "MALICIOUS_APP", 
                "NETWORK_INJECTION", "CERTIFICATE_PINNING_BYPASS", "HOOKING_FRAMEWORK");
        if (!validThreatTypes.contains(threatType)) {
            throw new IllegalArgumentException("Invalid threat type: " + threatType);
        }
        
        if (severity == null || severity.trim().isEmpty()) {
            throw new IllegalArgumentException("Severity is required");
        }
        
        List<String> validSeverities = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
        if (!validSeverities.contains(severity)) {
            throw new IllegalArgumentException("Invalid severity: " + severity);
        }
        
        if (detectionTime == null || detectionTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid detection time");
        }
        
        if (compromiseIndicators == null || compromiseIndicators.isEmpty()) {
            throw new IllegalArgumentException("Compromise indicators list cannot be empty");
        }
        
        log.debug("Device compromise event validation passed - AlertId: {}, DeviceId: {}", 
                alertId, deviceId);
    }
    
    private void executeImmediateDeviceSecurity(String alertId, UUID userId, String deviceId, 
                                              String threatType, String severity, 
                                              List<String> compromiseIndicators, 
                                              List<String> maliciousApps, Boolean rootedJailbroken) {
        try {
            log.info("Executing immediate device security measures - AlertId: {}, DeviceId: {}, ThreatType: {}", 
                    alertId, deviceId, threatType);
            
            // Block device access for critical threats
            if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
                deviceSecurityService.blockDeviceAccess(alertId, userId, deviceId, threatType, 
                        severity, compromiseIndicators);
            }
            
            // Revoke device authentication tokens
            deviceSecurityService.revokeDeviceTokens(alertId, userId, deviceId, threatType);
            
            // Force user logout on compromised device
            deviceSecurityService.forceDeviceLogout(alertId, userId, deviceId, threatType);
            
            // Disable sensitive features
            deviceSecurityService.disableSensitiveFeatures(alertId, userId, deviceId, 
                    threatType, severity);
            
            // Block malicious apps if detected
            if (maliciousApps != null && !maliciousApps.isEmpty()) {
                deviceSecurityService.blockMaliciousApps(alertId, userId, deviceId, 
                        maliciousApps, threatType);
            }
            
            // Quarantine device for rooted/jailbroken devices
            if (rootedJailbroken) {
                deviceSecurityService.quarantineDevice(alertId, userId, deviceId, 
                        "ROOTED_JAILBROKEN", severity);
            }
            
            // Enable device tracking
            deviceSecurityService.enableDeviceTracking(alertId, userId, deviceId, threatType);
            
            log.info("Immediate device security measures executed - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId);
            
        } catch (Exception e) {
            log.error("Failed to execute immediate device security - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId, e);
            throw new RuntimeException("Immediate device security failed", e);
        }
    }
    
    private void createDeviceSecurityIncident(String alertId, UUID userId, String deviceId, 
                                            String threatType, String severity, 
                                            LocalDateTime detectionTime, String deviceType, 
                                            String operatingSystem, String detectionSource, 
                                            List<String> compromiseIndicators, 
                                            Map<String, Object> threatDetails) {
        try {
            incidentResponseService.createDeviceSecurityIncident(alertId, userId, deviceId, 
                    threatType, severity, detectionTime, deviceType, operatingSystem, 
                    detectionSource, compromiseIndicators, threatDetails);
            
            log.info("Device security incident created - AlertId: {}, DeviceId: {}, ThreatType: {}", 
                    alertId, deviceId, threatType);
            
        } catch (Exception e) {
            log.error("Failed to create device security incident - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId, e);
            throw new RuntimeException("Device security incident creation failed", e);
        }
    }
    
    private void analyzeThreatIntelligence(String alertId, UUID userId, String deviceId, 
                                         String threatType, List<String> compromiseIndicators, 
                                         List<String> maliciousApps, Map<String, Object> deviceFingerprint, 
                                         Map<String, Object> threatDetails) {
        try {
            mobileThreatDefenseService.analyzeThreatIntelligence(alertId, userId, deviceId, 
                    threatType, compromiseIndicators, maliciousApps, deviceFingerprint, threatDetails);
            
            log.info("Threat intelligence analysis completed - AlertId: {}, DeviceId: {}, ThreatType: {}", 
                    alertId, deviceId, threatType);
            
        } catch (Exception e) {
            log.error("Failed to analyze threat intelligence - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId, e);
            // Don't throw exception as threat intelligence failure shouldn't block processing
        }
    }
    
    private void executeUserSecurityMeasures(String alertId, UUID userId, String deviceId, 
                                           String threatType, String severity, 
                                           List<String> compromiseIndicators, String ipAddress, 
                                           String geolocation) {
        try {
            // Force password reset for critical threats
            if ("CRITICAL".equals(severity)) {
                userSecurityService.forcePasswordReset(alertId, userId, deviceId, threatType);
            }
            
            // Enable MFA for high-risk threats
            if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
                userSecurityService.forceMFAEnrollment(alertId, userId, deviceId, threatType);
            }
            
            // Block user account if severe compromise
            if ("CRITICAL".equals(severity) && 
                    (threatType.equals("MALWARE") || threatType.equals("ROOTED_DEVICE"))) {
                userSecurityService.temporaryAccountBlock(alertId, userId, deviceId, 
                        threatType, severity);
            }
            
            // Monitor user activities
            userSecurityService.enableUserActivityMonitoring(alertId, userId, deviceId, 
                    threatType, compromiseIndicators);
            
            // Verify user identity
            userSecurityService.triggerIdentityVerification(alertId, userId, deviceId, 
                    threatType, ipAddress, geolocation);
            
            log.info("User security measures executed - AlertId: {}, UserId: {}, DeviceId: {}", 
                    alertId, userId, deviceId);
            
        } catch (Exception e) {
            log.error("Failed to execute user security measures - AlertId: {}, UserId: {}", 
                    alertId, userId, e);
            // Don't throw exception as user security failure shouldn't block processing
        }
    }
    
    private void performDeviceForensics(String alertId, UUID userId, String deviceId, 
                                      String threatType, String deviceType, String operatingSystem, 
                                      String osVersion, String appVersion, 
                                      Map<String, Object> deviceFingerprint, 
                                      Map<String, Object> threatDetails) {
        try {
            deviceSecurityService.performDeviceForensics(alertId, userId, deviceId, threatType, 
                    deviceType, operatingSystem, osVersion, appVersion, deviceFingerprint, threatDetails);
            
            log.info("Device forensics initiated - AlertId: {}, DeviceId: {}, ThreatType: {}", 
                    alertId, deviceId, threatType);
            
        } catch (Exception e) {
            log.error("Failed to perform device forensics - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId, e);
            // Don't throw exception as forensics failure shouldn't block processing
        }
    }
    
    private void updateDeviceTrustScore(String alertId, UUID userId, String deviceId, 
                                      String threatType, String severity, 
                                      List<String> compromiseIndicators, Boolean rootedJailbroken, 
                                      Boolean debuggerDetected, Boolean emulatorDetected) {
        try {
            int newTrustScore = deviceSecurityService.calculateDeviceTrustScore(alertId, userId, 
                    deviceId, threatType, severity, compromiseIndicators, rootedJailbroken, 
                    debuggerDetected, emulatorDetected);
            
            deviceSecurityService.updateDeviceTrustScore(alertId, userId, deviceId, 
                    newTrustScore, threatType, severity);
            
            log.info("Device trust score updated - AlertId: {}, DeviceId: {}, NewScore: {}", 
                    alertId, deviceId, newTrustScore);
            
        } catch (Exception e) {
            log.error("Failed to update device trust score - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId, e);
            // Don't throw exception as trust score update failure shouldn't block processing
        }
    }
    
    private void executeMobileThreatDefense(String alertId, UUID userId, String deviceId, 
                                          String threatType, String severity, 
                                          List<String> maliciousApps, 
                                          List<String> compromiseIndicators, 
                                          Boolean screenRecordingDetected) {
        try {
            // Deploy mobile threat defense measures
            mobileThreatDefenseService.deployThreatDefense(alertId, userId, deviceId, 
                    threatType, severity, maliciousApps, compromiseIndicators);
            
            // Block screen recording if detected
            if (screenRecordingDetected) {
                mobileThreatDefenseService.blockScreenRecording(alertId, userId, deviceId, 
                        threatType, severity);
            }
            
            // Enable runtime application self-protection
            mobileThreatDefenseService.enableRASP(alertId, userId, deviceId, threatType, 
                    compromiseIndicators);
            
            // Update threat signatures
            mobileThreatDefenseService.updateThreatSignatures(alertId, deviceId, threatType, 
                    maliciousApps, compromiseIndicators);
            
            log.info("Mobile threat defense executed - AlertId: {}, DeviceId: {}, ThreatType: {}", 
                    alertId, deviceId, threatType);
            
        } catch (Exception e) {
            log.error("Failed to execute mobile threat defense - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId, e);
            // Don't throw exception as threat defense failure shouldn't block processing
        }
    }
    
    private void setupEnhancedDeviceMonitoring(String alertId, UUID userId, String deviceId, 
                                             String threatType, List<String> compromiseIndicators, 
                                             String ipAddress, String geolocation) {
        try {
            deviceSecurityService.setupEnhancedMonitoring(alertId, userId, deviceId, threatType, 
                    compromiseIndicators, ipAddress, geolocation);
            
            log.info("Enhanced device monitoring setup completed - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId);
            
        } catch (Exception e) {
            log.error("Failed to setup enhanced device monitoring - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId, e);
            // Don't throw exception as monitoring setup failure shouldn't block processing
        }
    }
    
    private void auditDeviceCompromiseEvent(String alertId, UUID userId, String deviceId, 
                                          String threatType, String severity, 
                                          List<String> compromiseIndicators, 
                                          LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditSecurityEvent(
                    "DEVICE_COMPROMISE_DETECTED_PROCESSED",
                    userId.toString(),
                    String.format("Device compromise processed - DeviceId: %s, ThreatType: %s, Severity: %s, Indicators: %d", 
                            deviceId, threatType, severity, compromiseIndicators.size()),
                    Map.of(
                            "alertId", alertId,
                            "userId", userId.toString(),
                            "deviceId", deviceId,
                            "threatType", threatType,
                            "severity", severity,
                            "compromiseIndicatorsCount", compromiseIndicators.size(),
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit device compromise event - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId, e);
        }
    }
    
    private void handleProcessingFailure(String alertId, UUID userId, String deviceId, 
                                       String threatType, String severity, Exception error) {
        try {
            deviceSecurityService.recordProcessingFailure(alertId, userId, deviceId, 
                    threatType, severity, error.getMessage());
            
            auditService.auditSecurityEvent(
                    "DEVICE_COMPROMISE_PROCESSING_FAILED",
                    userId.toString(),
                    "Failed to process device compromise: " + error.getMessage(),
                    Map.of(
                            "alertId", alertId,
                            "userId", userId.toString(),
                            "deviceId", deviceId,
                            "threatType", threatType != null ? threatType : "UNKNOWN",
                            "severity", severity != null ? severity : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle processing failure - AlertId: {}, DeviceId: {}", 
                    alertId, deviceId, e);
        }
    }
    
    public void handleCircuitBreakerFallback(String eventJson, String topic, int partition, 
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("CIRCUIT BREAKER: Device compromise processing circuit breaker activated - Topic: {}, Error: {}", 
                topic, e.getMessage());
        
        try {
            circuitBreakerService.handleFallback("device-compromise-detected", eventJson, e);
        } catch (Exception fallbackError) {
            log.error("Fallback handling failed for device compromise", fallbackError);
        }
    }
    
    @DltHandler
    public void handleDlt(String eventJson, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                         @Header(value = "x-original-topic", required = false) String originalTopic,
                         @Header(value = "x-error-message", required = false) String errorMessage) {
        
        log.error("CRITICAL: Device compromise sent to DLT - OriginalTopic: {}, Error: {}, Event: {}", 
                originalTopic, errorMessage, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String alertId = (String) event.get("alertId");
            String userId = (String) event.get("userId");
            String deviceId = (String) event.get("deviceId");
            String threatType = (String) event.get("threatType");
            String severity = (String) event.get("severity");
            
            log.error("DLT: Device compromise failed permanently - AlertId: {}, UserId: {}, DeviceId: {}, ThreatType: {}, Severity: {} - IMMEDIATE MANUAL INTERVENTION REQUIRED", 
                    alertId, userId, deviceId, threatType, severity);
            
            // Critical: Send emergency notification for failed device compromise processing
            deviceSecurityService.sendEmergencyNotification(alertId, userId, deviceId, 
                    threatType, severity, "DLT: " + errorMessage);
            
            deviceSecurityService.markForEmergencyReview(alertId, userId, deviceId, 
                    threatType, severity, "DLT: " + errorMessage);
            
        } catch (Exception e) {
            log.error("Failed to parse device compromise DLT event: {}", eventJson, e);
        }
    }
    
    private boolean isAlreadyProcessed(String eventKey) {
        LocalDateTime processedTime = processedEvents.get(eventKey);
        if (processedTime != null) {
            // Check if processed within last 24 hours
            if (processedTime.isAfter(LocalDateTime.now().minusHours(24))) {
                return true;
            } else {
                // Remove expired entry
                processedEvents.remove(eventKey);
            }
        }
        return false;
    }
    
    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, LocalDateTime.now());
        
        // Clean up old entries periodically
        if (processedEvents.size() > 10000) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        }
    }
}