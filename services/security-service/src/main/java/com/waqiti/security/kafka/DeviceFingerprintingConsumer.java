package com.waqiti.security.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.security.model.*;
import com.waqiti.security.repository.DeviceFingerprintRepository;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Production-grade Kafka consumer for device fingerprinting
 * Handles device identification, tracking, risk assessment, and fraud detection
 * 
 * Critical for: Device-based security, fraud prevention, identity verification
 * SLA: Must process device fingerprints within 3 seconds for real-time authentication
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceFingerprintingConsumer {

    private final DeviceFingerprintRepository fingerprintRepository;
    private final DeviceAnalysisService deviceAnalysisService;
    private final DeviceRiskScoringService riskScoringService;
    private final DeviceTrustService trustService;
    private final FingerprintMatchingService matchingService;
    private final DeviceReputationService reputationService;
    private final MLDeviceAnalysisService mlAnalysisService;
    private final NotificationService notificationService;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ScheduledExecutorService scheduledExecutor;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 3000; // 3 seconds
    private static final Set<String> HIGH_RISK_DEVICE_SIGNALS = Set.of(
        "EMULATOR_DETECTED", "ROOTED_DEVICE", "JAILBROKEN_DEVICE", 
        "DEBUGGER_ATTACHED", "TAMPERING_DETECTED", "VPN_DETECTED",
        "PROXY_DETECTED", "TOR_DETECTED", "SPOOFED_FINGERPRINT"
    );
    
    @KafkaListener(
        topics = {"device-fingerprinting"},
        groupId = "device-fingerprinting-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "device-fingerprinting-processor", fallbackMethod = "handleDeviceFingerprintingFailure")
    @Retry(name = "device-fingerprinting-processor")
    public void processDeviceFingerprinting(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing device fingerprinting: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            DeviceFingerprintRequest request = extractDeviceFingerprintRequest(payload);
            
            // Validate fingerprint request
            validateFingerprintRequest(request);
            
            // Check for duplicate request
            if (isDuplicateRequest(request)) {
                log.warn("Duplicate fingerprint request: {}, skipping", request.getRequestId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Generate stable device fingerprint
            DeviceFingerprint fingerprint = generateDeviceFingerprint(request);
            
            // Enrich fingerprint with additional context
            DeviceFingerprint enrichedFingerprint = enrichDeviceFingerprint(fingerprint, request);
            
            // Perform device analysis
            DeviceAnalysisResult analysisResult = performDeviceAnalysis(enrichedFingerprint, request);
            
            // Process device fingerprint
            DeviceFingerprintProcessingResult result = processDeviceFingerprint(
                enrichedFingerprint, 
                analysisResult, 
                request
            );
            
            // Perform device matching
            if (analysisResult.enablesMatching()) {
                performDeviceMatching(enrichedFingerprint, result);
            }
            
            // Calculate device risk score
            if (analysisResult.enablesRiskScoring()) {
                calculateDeviceRisk(enrichedFingerprint, analysisResult, result);
            }
            
            // Update device trust levels
            updateDeviceTrust(enrichedFingerprint, analysisResult, result);
            
            // Detect device anomalies
            if (analysisResult.enablesAnomalyDetection()) {
                detectDeviceAnomalies(enrichedFingerprint, result);
            }
            
            // Update ML models
            if (analysisResult.enablesMLUpdate()) {
                updateMLModels(enrichedFingerprint, analysisResult);
            }
            
            // Trigger security workflows
            if (analysisResult.hasSecurityWorkflows()) {
                triggerSecurityWorkflows(enrichedFingerprint, analysisResult);
            }
            
            // Send device notifications
            sendDeviceNotifications(enrichedFingerprint, analysisResult, result);
            
            // Update monitoring systems
            updateMonitoringSystems(enrichedFingerprint, result);
            
            // Audit device fingerprinting
            auditDeviceFingerprinting(enrichedFingerprint, result, event);
            
            // Record metrics
            recordFingerprintMetrics(enrichedFingerprint, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed device fingerprint: {} device: {} risk: {} trust: {} in {}ms", 
                    request.getRequestId(), enrichedFingerprint.getDeviceId(), 
                    result.getRiskScore(), result.getTrustScore(), 
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for device fingerprinting: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalDeviceException e) {
            log.error("Critical device processing failed: {}", eventId, e);
            handleCriticalDeviceError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process device fingerprinting: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private DeviceFingerprintRequest extractDeviceFingerprintRequest(Map<String, Object> payload) {
        return DeviceFingerprintRequest.builder()
            .requestId(extractString(payload, "requestId", UUID.randomUUID().toString()))
            .userId(extractString(payload, "userId", null))
            .sessionId(extractString(payload, "sessionId", null))
            .deviceId(extractString(payload, "deviceId", null))
            .userAgent(extractString(payload, "userAgent", null))
            .platform(extractString(payload, "platform", null))
            .osName(extractString(payload, "osName", null))
            .osVersion(extractString(payload, "osVersion", null))
            .browserName(extractString(payload, "browserName", null))
            .browserVersion(extractString(payload, "browserVersion", null))
            .screenResolution(extractString(payload, "screenResolution", null))
            .screenColorDepth(extractInteger(payload, "screenColorDepth", null))
            .timezone(extractString(payload, "timezone", null))
            .timezoneOffset(extractInteger(payload, "timezoneOffset", null))
            .language(extractString(payload, "language", null))
            .languages(extractStringList(payload, "languages"))
            .plugins(extractStringList(payload, "plugins"))
            .fonts(extractStringList(payload, "fonts"))
            .canvas(extractString(payload, "canvas", null))
            .webgl(extractString(payload, "webgl", null))
            .audioContext(extractString(payload, "audioContext", null))
            .cpuClass(extractString(payload, "cpuClass", null))
            .hardwareConcurrency(extractInteger(payload, "hardwareConcurrency", null))
            .deviceMemory(extractDouble(payload, "deviceMemory", null))
            .touchSupport(extractBoolean(payload, "touchSupport", false))
            .maxTouchPoints(extractInteger(payload, "maxTouchPoints", 0))
            .cookiesEnabled(extractBoolean(payload, "cookiesEnabled", true))
            .localStorageEnabled(extractBoolean(payload, "localStorageEnabled", true))
            .sessionStorageEnabled(extractBoolean(payload, "sessionStorageEnabled", true))
            .indexedDBEnabled(extractBoolean(payload, "indexedDBEnabled", true))
            .doNotTrack(extractString(payload, "doNotTrack", null))
            .adBlockEnabled(extractBoolean(payload, "adBlockEnabled", false))
            .sourceIp(extractString(payload, "sourceIp", null))
            .httpHeaders(extractMap(payload, "httpHeaders"))
            .accelerometer(extractBoolean(payload, "accelerometer", false))
            .gyroscope(extractBoolean(payload, "gyroscope", false))
            .battery(extractMap(payload, "battery"))
            .networkInfo(extractMap(payload, "networkInfo"))
            .mediaDevices(extractStringList(payload, "mediaDevices"))
            .permissions(extractMap(payload, "permissions"))
            .mobileInfo(extractMap(payload, "mobileInfo"))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .metadata(extractMap(payload, "metadata"))
            .timestamp(extractInstant(payload, "timestamp"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateFingerprintRequest(DeviceFingerprintRequest request) {
        if (request.getUserAgent() == null || request.getUserAgent().isEmpty()) {
            throw new ValidationException("User agent is required");
        }
        
        if (request.getSourceIp() == null || request.getSourceIp().isEmpty()) {
            throw new ValidationException("Source IP is required");
        }
        
        if (request.getTimestamp() == null) {
            throw new ValidationException("Timestamp is required");
        }
        
        // Validate essential fingerprint components
        if (request.getScreenResolution() == null && 
            request.getCanvas() == null && 
            request.getWebgl() == null) {
            throw new ValidationException("At least one fingerprint component is required");
        }
        
        // Validate user context if provided
        if (request.getUserId() != null && !userService.userExists(request.getUserId())) {
            log.warn("User not found for device fingerprinting: {}", request.getUserId());
        }
    }

    private boolean isDuplicateRequest(DeviceFingerprintRequest request) {
        // Check for recent identical fingerprint submission
        return fingerprintRepository.existsSimilarFingerprint(
            request.getUserAgent(),
            request.getSourceIp(),
            request.getCanvas(),
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private DeviceFingerprint generateDeviceFingerprint(DeviceFingerprintRequest request) {
        DeviceFingerprint fingerprint = new DeviceFingerprint();
        fingerprint.setFingerprintId(UUID.randomUUID().toString());
        fingerprint.setTimestamp(request.getTimestamp());
        
        // Generate stable device ID
        String deviceId = generateStableDeviceId(request);
        fingerprint.setDeviceId(deviceId);
        
        // Generate fingerprint hash
        String fingerprintHash = generateFingerprintHash(request);
        fingerprint.setFingerprintHash(fingerprintHash);
        
        // Extract device characteristics
        fingerprint.setDeviceType(determineDeviceType(request));
        fingerprint.setPlatform(request.getPlatform());
        fingerprint.setOperatingSystem(request.getOsName());
        fingerprint.setOsVersion(request.getOsVersion());
        fingerprint.setBrowser(request.getBrowserName());
        fingerprint.setBrowserVersion(request.getBrowserVersion());
        
        // Screen characteristics
        fingerprint.setScreenResolution(request.getScreenResolution());
        fingerprint.setScreenColorDepth(request.getScreenColorDepth());
        
        // Browser characteristics
        fingerprint.setUserAgent(request.getUserAgent());
        fingerprint.setTimezone(request.getTimezone());
        fingerprint.setTimezoneOffset(request.getTimezoneOffset());
        fingerprint.setLanguage(request.getLanguage());
        fingerprint.setLanguages(request.getLanguages());
        
        // Hardware characteristics
        fingerprint.setHardwareConcurrency(request.getHardwareConcurrency());
        fingerprint.setDeviceMemory(request.getDeviceMemory());
        fingerprint.setTouchSupport(request.isTouchSupport());
        fingerprint.setMaxTouchPoints(request.getMaxTouchPoints());
        
        // Canvas fingerprint
        fingerprint.setCanvasFingerprint(request.getCanvas());
        fingerprint.setWebglFingerprint(request.getWebgl());
        fingerprint.setAudioContextFingerprint(request.getAudioContext());
        
        // Browser capabilities
        fingerprint.setCookiesEnabled(request.isCookiesEnabled());
        fingerprint.setLocalStorageEnabled(request.isLocalStorageEnabled());
        fingerprint.setSessionStorageEnabled(request.isSessionStorageEnabled());
        fingerprint.setIndexedDBEnabled(request.isIndexedDBEnabled());
        
        // Network information
        fingerprint.setSourceIp(request.getSourceIp());
        fingerprint.setHttpHeaders(request.getHttpHeaders());
        
        // Mobile-specific information
        if (request.getMobileInfo() != null) {
            fingerprint.setMobileDevice(true);
            fingerprint.setMobileInfo(request.getMobileInfo());
        }
        
        // User association
        fingerprint.setUserId(request.getUserId());
        fingerprint.setSessionId(request.getSessionId());
        
        return fingerprint;
    }

    private String generateStableDeviceId(DeviceFingerprintRequest request) {
        // Create stable device ID from consistent device characteristics
        StringBuilder deviceIdBuilder = new StringBuilder();
        
        // Primary characteristics (most stable)
        if (request.getCanvas() != null) {
            deviceIdBuilder.append(request.getCanvas());
        }
        if (request.getWebgl() != null) {
            deviceIdBuilder.append(request.getWebgl());
        }
        
        // Secondary characteristics
        deviceIdBuilder.append(request.getUserAgent());
        deviceIdBuilder.append(request.getScreenResolution());
        deviceIdBuilder.append(request.getTimezone());
        
        // Hardware characteristics
        if (request.getHardwareConcurrency() != null) {
            deviceIdBuilder.append(request.getHardwareConcurrency());
        }
        
        // Generate SHA-256 hash
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(deviceIdBuilder.toString().getBytes());
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate device ID hash", e);
            return UUID.randomUUID().toString();
        }
    }

    private String generateFingerprintHash(DeviceFingerprintRequest request) {
        // Create comprehensive fingerprint hash including all available attributes
        StringBuilder fingerprintBuilder = new StringBuilder();
        
        // Include all fingerprint components
        fingerprintBuilder.append(request.getUserAgent());
        fingerprintBuilder.append(request.getPlatform());
        fingerprintBuilder.append(request.getOsName()).append(request.getOsVersion());
        fingerprintBuilder.append(request.getBrowserName()).append(request.getBrowserVersion());
        fingerprintBuilder.append(request.getScreenResolution());
        fingerprintBuilder.append(request.getScreenColorDepth());
        fingerprintBuilder.append(request.getTimezone());
        fingerprintBuilder.append(request.getLanguage());
        
        if (request.getCanvas() != null) {
            fingerprintBuilder.append(request.getCanvas());
        }
        if (request.getWebgl() != null) {
            fingerprintBuilder.append(request.getWebgl());
        }
        if (request.getAudioContext() != null) {
            fingerprintBuilder.append(request.getAudioContext());
        }
        
        // Include plugins and fonts
        if (request.getPlugins() != null) {
            fingerprintBuilder.append(String.join(",", request.getPlugins()));
        }
        if (request.getFonts() != null) {
            fingerprintBuilder.append(String.join(",", request.getFonts()));
        }
        
        // Generate SHA-256 hash
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(fingerprintBuilder.toString().getBytes());
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate fingerprint hash", e);
            return UUID.randomUUID().toString();
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private DeviceType determineDeviceType(DeviceFingerprintRequest request) {
        if (request.getMobileInfo() != null) {
            String userAgent = request.getUserAgent().toLowerCase();
            if (userAgent.contains("iphone") || userAgent.contains("ipad")) {
                return DeviceType.IOS_MOBILE;
            } else if (userAgent.contains("android")) {
                return DeviceType.ANDROID_MOBILE;
            }
            return DeviceType.MOBILE;
        }
        
        if (request.isTouchSupport() && request.getMaxTouchPoints() > 0) {
            return DeviceType.TABLET;
        }
        
        return DeviceType.DESKTOP;
    }

    private DeviceFingerprint enrichDeviceFingerprint(DeviceFingerprint fingerprint, 
                                                    DeviceFingerprintRequest request) {
        // Enrich with historical device data
        DeviceHistory history = fingerprintRepository.getDeviceHistory(
            fingerprint.getDeviceId(),
            Instant.now().minus(90, ChronoUnit.DAYS)
        );
        
        fingerprint.setFirstSeen(history.getFirstSeen() != null ? history.getFirstSeen() : Instant.now());
        fingerprint.setLastSeen(Instant.now());
        fingerprint.setSeenCount(history.getSeenCount() + 1);
        fingerprint.setAssociatedUsers(history.getAssociatedUsers());
        
        // Enrich with geolocation data
        if (request.getSourceIp() != null) {
            GeoLocationData geoData = geoLocationService.getLocationData(request.getSourceIp());
            if (geoData != null) {
                fingerprint.setCountry(geoData.getCountry());
                fingerprint.setRegion(geoData.getRegion());
                fingerprint.setCity(geoData.getCity());
                fingerprint.setIsp(geoData.getIsp());
                fingerprint.setOrganization(geoData.getOrganization());
            }
        }
        
        // Enrich with device reputation
        DeviceReputation reputation = reputationService.getDeviceReputation(fingerprint.getDeviceId());
        if (reputation != null) {
            fingerprint.setReputationScore(reputation.getScore());
            fingerprint.setReputationFactors(reputation.getFactors());
            fingerprint.setKnownMalicious(reputation.isMalicious());
        }
        
        // Enrich with browser security features
        BrowserSecurityFeatures securityFeatures = analyzeBrowserSecurity(request);
        fingerprint.setSecurityFeatures(securityFeatures);
        
        return fingerprint;
    }

    private BrowserSecurityFeatures analyzeBrowserSecurity(DeviceFingerprintRequest request) {
        BrowserSecurityFeatures features = new BrowserSecurityFeatures();
        
        // Privacy features
        features.setDoNotTrack("1".equals(request.getDoNotTrack()));
        features.setAdBlockEnabled(request.isAdBlockEnabled());
        
        // Storage capabilities
        features.setCookiesEnabled(request.isCookiesEnabled());
        features.setLocalStorageEnabled(request.isLocalStorageEnabled());
        features.setSessionStorageEnabled(request.isSessionStorageEnabled());
        features.setIndexedDBEnabled(request.isIndexedDBEnabled());
        
        // Advanced features
        features.setWebRTCEnabled(detectWebRTC(request));
        features.setWebSocketsEnabled(detectWebSockets(request));
        features.setServiceWorkersEnabled(detectServiceWorkers(request));
        
        return features;
    }

    private boolean detectWebRTC(DeviceFingerprintRequest request) {
        return request.getMediaDevices() != null && !request.getMediaDevices().isEmpty();
    }

    private boolean detectWebSockets(DeviceFingerprintRequest request) {
        // Check for WebSocket support indicators
        return request.getMetadata() != null && 
               request.getMetadata().containsKey("webSocketSupport");
    }

    private boolean detectServiceWorkers(DeviceFingerprintRequest request) {
        // Check for Service Worker support indicators
        return request.getMetadata() != null && 
               request.getMetadata().containsKey("serviceWorkerSupport");
    }

    private DeviceAnalysisResult performDeviceAnalysis(DeviceFingerprint fingerprint, 
                                                      DeviceFingerprintRequest request) {
        DeviceAnalysisResult result = new DeviceAnalysisResult();
        result.setDeviceId(fingerprint.getDeviceId());
        result.setAnalysisTime(Instant.now());
        
        // Detect device anomalies
        List<DeviceAnomaly> anomalies = new ArrayList<>();
        
        // Emulator/simulator detection
        anomalies.addAll(detectEmulator(fingerprint, request));
        
        // Root/jailbreak detection
        anomalies.addAll(detectRootedDevice(fingerprint, request));
        
        // Tampering detection
        anomalies.addAll(detectTampering(fingerprint, request));
        
        // Privacy tools detection
        anomalies.addAll(detectPrivacyTools(fingerprint, request));
        
        // Fingerprint spoofing detection
        anomalies.addAll(detectSpoofing(fingerprint, request));
        
        result.setAnomalies(anomalies);
        
        // Calculate device confidence
        double confidence = calculateDeviceConfidence(fingerprint, anomalies);
        result.setConfidence(confidence);
        
        // Determine device legitimacy
        DeviceLegitimacy legitimacy = determineDeviceLegitimacy(confidence, anomalies);
        result.setLegitimacy(legitimacy);
        
        // Identify device threats
        List<DeviceThreat> threats = identifyDeviceThreats(fingerprint, anomalies);
        result.setThreats(threats);
        
        // Determine required capabilities
        result.setEnablesMatching(confidence > 0.3);
        result.setEnablesRiskScoring(true);
        result.setEnablesAnomalyDetection(anomalies.size() > 0);
        result.setEnablesMLUpdate(true);
        result.setHasSecurityWorkflows(threats.size() > 0 || legitimacy != DeviceLegitimacy.LEGITIMATE);
        
        return result;
    }

    private List<DeviceAnomaly> detectEmulator(DeviceFingerprint fingerprint, 
                                              DeviceFingerprintRequest request) {
        List<DeviceAnomaly> anomalies = new ArrayList<>();
        
        // Check for emulator indicators
        EmulatorDetectionResult emulatorResult = deviceAnalysisService.detectEmulator(
            fingerprint.getUserAgent(),
            fingerprint.getHardwareConcurrency(),
            fingerprint.getDeviceMemory(),
            fingerprint.getPlatform()
        );
        
        if (emulatorResult.isEmulatorDetected()) {
            anomalies.add(DeviceAnomaly.builder()
                .anomalyType("EMULATOR_DETECTED")
                .severity(AnomalySeverity.HIGH)
                .confidence(emulatorResult.getConfidence())
                .description("Device appears to be an emulator/simulator")
                .evidence(emulatorResult.getIndicators())
                .build());
        }
        
        return anomalies;
    }

    private List<DeviceAnomaly> detectRootedDevice(DeviceFingerprint fingerprint, 
                                                  DeviceFingerprintRequest request) {
        List<DeviceAnomaly> anomalies = new ArrayList<>();
        
        if (request.getMobileInfo() != null) {
            RootDetectionResult rootResult = deviceAnalysisService.detectRoot(
                request.getMobileInfo(),
                fingerprint.getPlatform()
            );
            
            if (rootResult.isRooted()) {
                String anomalyType = fingerprint.getPlatform().toLowerCase().contains("ios") ? 
                                    "JAILBROKEN_DEVICE" : "ROOTED_DEVICE";
                
                anomalies.add(DeviceAnomaly.builder()
                    .anomalyType(anomalyType)
                    .severity(AnomalySeverity.HIGH)
                    .confidence(rootResult.getConfidence())
                    .description("Device has been rooted/jailbroken")
                    .evidence(rootResult.getIndicators())
                    .build());
            }
        }
        
        return anomalies;
    }

    private List<DeviceAnomaly> detectTampering(DeviceFingerprint fingerprint, 
                                               DeviceFingerprintRequest request) {
        List<DeviceAnomaly> anomalies = new ArrayList<>();
        
        // Check for fingerprint inconsistencies
        TamperingDetectionResult tamperingResult = deviceAnalysisService.detectTampering(
            fingerprint,
            request
        );
        
        if (tamperingResult.isTamperingDetected()) {
            anomalies.add(DeviceAnomaly.builder()
                .anomalyType("TAMPERING_DETECTED")
                .severity(AnomalySeverity.HIGH)
                .confidence(tamperingResult.getConfidence())
                .description("Device fingerprint shows signs of tampering")
                .evidence(tamperingResult.getInconsistencies())
                .build());
        }
        
        // Check for debugger
        if (request.getMetadata() != null && 
            Boolean.TRUE.equals(request.getMetadata().get("debuggerAttached"))) {
            anomalies.add(DeviceAnomaly.builder()
                .anomalyType("DEBUGGER_ATTACHED")
                .severity(AnomalySeverity.MEDIUM)
                .confidence(0.9)
                .description("Debugger is attached to the browser/app")
                .evidence(Map.of("debugger", true))
                .build());
        }
        
        return anomalies;
    }

    private List<DeviceAnomaly> detectPrivacyTools(DeviceFingerprint fingerprint, 
                                                  DeviceFingerprintRequest request) {
        List<DeviceAnomaly> anomalies = new ArrayList<>();
        
        // VPN detection
        VPNDetectionResult vpnResult = deviceAnalysisService.detectVPN(
            request.getSourceIp(),
            request.getNetworkInfo()
        );
        
        if (vpnResult.isVPNDetected()) {
            anomalies.add(DeviceAnomaly.builder()
                .anomalyType("VPN_DETECTED")
                .severity(AnomalySeverity.MEDIUM)
                .confidence(vpnResult.getConfidence())
                .description("Device is using a VPN")
                .evidence(vpnResult.getIndicators())
                .build());
        }
        
        // Proxy detection
        ProxyDetectionResult proxyResult = deviceAnalysisService.detectProxy(
            request.getSourceIp(),
            request.getHttpHeaders()
        );
        
        if (proxyResult.isProxyDetected()) {
            anomalies.add(DeviceAnomaly.builder()
                .anomalyType("PROXY_DETECTED")
                .severity(AnomalySeverity.MEDIUM)
                .confidence(proxyResult.getConfidence())
                .description("Device is using a proxy")
                .evidence(proxyResult.getIndicators())
                .build());
        }
        
        // Tor detection
        if (torExitNodeService.isTorExitNode(request.getSourceIp())) {
            anomalies.add(DeviceAnomaly.builder()
                .anomalyType("TOR_DETECTED")
                .severity(AnomalySeverity.HIGH)
                .confidence(0.95)
                .description("Device is using Tor network")
                .evidence(Map.of("tor_exit_node", true))
                .build());
        }
        
        return anomalies;
    }

    private List<DeviceAnomaly> detectSpoofing(DeviceFingerprint fingerprint, 
                                              DeviceFingerprintRequest request) {
        List<DeviceAnomaly> anomalies = new ArrayList<>();
        
        // Canvas spoofing detection
        if (fingerprint.getCanvasFingerprint() != null) {
            CanvasSpoofingResult canvasResult = deviceAnalysisService.detectCanvasSpoofing(
                fingerprint.getCanvasFingerprint()
            );
            
            if (canvasResult.isSpoofingDetected()) {
                anomalies.add(DeviceAnomaly.builder()
                    .anomalyType("CANVAS_SPOOFING")
                    .severity(AnomalySeverity.HIGH)
                    .confidence(canvasResult.getConfidence())
                    .description("Canvas fingerprint appears to be spoofed")
                    .evidence(canvasResult.getIndicators())
                    .build());
            }
        }
        
        // WebGL spoofing detection
        if (fingerprint.getWebglFingerprint() != null) {
            WebGLSpoofingResult webglResult = deviceAnalysisService.detectWebGLSpoofing(
                fingerprint.getWebglFingerprint()
            );
            
            if (webglResult.isSpoofingDetected()) {
                anomalies.add(DeviceAnomaly.builder()
                    .anomalyType("WEBGL_SPOOFING")
                    .severity(AnomalySeverity.HIGH)
                    .confidence(webglResult.getConfidence())
                    .description("WebGL fingerprint appears to be spoofed")
                    .evidence(webglResult.getIndicators())
                    .build());
            }
        }
        
        // Overall fingerprint spoofing
        FingerprintSpoofingResult spoofingResult = deviceAnalysisService.detectFingerprintSpoofing(
            fingerprint,
            request
        );
        
        if (spoofingResult.isSpoofingDetected()) {
            anomalies.add(DeviceAnomaly.builder()
                .anomalyType("SPOOFED_FINGERPRINT")
                .severity(AnomalySeverity.CRITICAL)
                .confidence(spoofingResult.getConfidence())
                .description("Device fingerprint appears to be artificially generated")
                .evidence(spoofingResult.getIndicators())
                .build());
        }
        
        return anomalies;
    }

    private double calculateDeviceConfidence(DeviceFingerprint fingerprint, List<DeviceAnomaly> anomalies) {
        double confidence = 1.0; // Start with full confidence
        
        // Reduce confidence based on anomalies
        for (DeviceAnomaly anomaly : anomalies) {
            double reduction = anomaly.getConfidence() * getSeverityMultiplier(anomaly.getSeverity());
            confidence -= reduction * 0.2;
        }
        
        // Increase confidence for known good devices
        if (fingerprint.getSeenCount() > 10 && !fingerprint.isKnownMalicious()) {
            confidence += 0.1;
        }
        
        // Adjust based on reputation
        if (fingerprint.getReputationScore() != null) {
            confidence = confidence * 0.7 + (fingerprint.getReputationScore() / 100.0) * 0.3;
        }
        
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private double getSeverityMultiplier(AnomalySeverity severity) {
        switch (severity) {
            case CRITICAL: return 1.0;
            case HIGH: return 0.8;
            case MEDIUM: return 0.6;
            case LOW: return 0.4;
            default: return 0.5;
        }
    }

    private DeviceLegitimacy determineDeviceLegitimacy(double confidence, List<DeviceAnomaly> anomalies) {
        // Check for critical anomalies
        boolean hasCriticalAnomaly = anomalies.stream()
            .anyMatch(a -> HIGH_RISK_DEVICE_SIGNALS.contains(a.getAnomalyType()));
        
        if (hasCriticalAnomaly || confidence < 0.3) {
            return DeviceLegitimacy.MALICIOUS;
        }
        
        if (confidence < 0.5) {
            return DeviceLegitimacy.SUSPICIOUS;
        }
        
        if (confidence < 0.7) {
            return DeviceLegitimacy.UNCERTAIN;
        }
        
        return DeviceLegitimacy.LEGITIMATE;
    }

    private List<DeviceThreat> identifyDeviceThreats(DeviceFingerprint fingerprint, 
                                                   List<DeviceAnomaly> anomalies) {
        List<DeviceThreat> threats = new ArrayList<>();
        
        for (DeviceAnomaly anomaly : anomalies) {
            DeviceThreat threat = mapAnomalyToThreat(anomaly);
            if (threat != null) {
                threats.add(threat);
            }
        }
        
        // Check against threat intelligence
        List<DeviceThreat> intelligenceThreats = threatIntelligenceService.getDeviceThreats(
            fingerprint.getDeviceId(),
            fingerprint.getSourceIp()
        );
        threats.addAll(intelligenceThreats);
        
        return threats;
    }

    private DeviceThreat mapAnomalyToThreat(DeviceAnomaly anomaly) {
        switch (anomaly.getAnomalyType()) {
            case "EMULATOR_DETECTED":
                return DeviceThreat.builder()
                    .threatType("EMULATION_THREAT")
                    .severity(ThreatSeverity.HIGH)
                    .description("Emulator use may indicate automated attack")
                    .build();
                
            case "ROOTED_DEVICE":
            case "JAILBROKEN_DEVICE":
                return DeviceThreat.builder()
                    .threatType("COMPROMISED_DEVICE")
                    .severity(ThreatSeverity.HIGH)
                    .description("Rooted/jailbroken device poses security risk")
                    .build();
                
            case "SPOOFED_FINGERPRINT":
                return DeviceThreat.builder()
                    .threatType("IDENTITY_SPOOFING")
                    .severity(ThreatSeverity.CRITICAL)
                    .description("Device identity is being spoofed")
                    .build();
                
            case "TOR_DETECTED":
                return DeviceThreat.builder()
                    .threatType("ANONYMIZATION_THREAT")
                    .severity(ThreatSeverity.HIGH)
                    .description("Tor usage may indicate malicious intent")
                    .build();
                
            default:
                return null;
        }
    }

    private DeviceFingerprintProcessingResult processDeviceFingerprint(DeviceFingerprint fingerprint, 
                                                                     DeviceAnalysisResult analysisResult,
                                                                     DeviceFingerprintRequest request) {
        DeviceFingerprintProcessingResult result = new DeviceFingerprintProcessingResult();
        result.setFingerprintId(fingerprint.getFingerprintId());
        result.setDeviceId(fingerprint.getDeviceId());
        result.setAnalysisResult(analysisResult);
        result.setProcessingStartTime(Instant.now());
        
        try {
            // Save device fingerprint
            DeviceFingerprint savedFingerprint = fingerprintRepository.saveFingerprint(fingerprint);
            result.setSavedFingerprint(savedFingerprint);
            
            // Update device profile
            DeviceProfile deviceProfile = DeviceProfile.builder()
                .deviceId(fingerprint.getDeviceId())
                .lastFingerprint(savedFingerprint)
                .lastSeen(Instant.now())
                .legitimacy(analysisResult.getLegitimacy())
                .confidence(analysisResult.getConfidence())
                .threats(analysisResult.getThreats())
                .build();
            
            DeviceProfile savedProfile = fingerprintRepository.saveDeviceProfile(deviceProfile);
            result.setSavedProfile(savedProfile);
            
            result.setStatus(ProcessingStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Failed to process device fingerprint: {}", fingerprint.getFingerprintId(), e);
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new DeviceProcessingException("Device fingerprint processing failed", e);
        }
        
        result.setProcessingEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private void performDeviceMatching(DeviceFingerprint fingerprint, 
                                      DeviceFingerprintProcessingResult result) {
        // Find similar devices
        List<DeviceMatch> matches = matchingService.findSimilarDevices(
            fingerprint,
            0.85 // 85% similarity threshold
        );
        
        result.setDeviceMatches(matches);
        
        // Check for device sharing
        if (matches.size() > 0) {
            DeviceSharingAnalysis sharingAnalysis = matchingService.analyzeDeviceSharing(
                fingerprint,
                matches
            );
            result.setDeviceSharingAnalysis(sharingAnalysis);
        }
    }

    private void calculateDeviceRisk(DeviceFingerprint fingerprint, 
                                    DeviceAnalysisResult analysisResult,
                                    DeviceFingerprintProcessingResult result) {
        DeviceRiskScore riskScore = riskScoringService.calculateRiskScore(
            fingerprint,
            analysisResult
        );
        
        result.setRiskScore(riskScore.getScore());
        result.setRiskFactors(riskScore.getFactors());
        result.setRiskLevel(riskScore.getLevel());
    }

    private void updateDeviceTrust(DeviceFingerprint fingerprint, 
                                  DeviceAnalysisResult analysisResult,
                                  DeviceFingerprintProcessingResult result) {
        DeviceTrustScore trustScore = trustService.calculateTrustScore(
            fingerprint,
            analysisResult
        );
        
        result.setTrustScore(trustScore.getScore());
        result.setTrustFactors(trustScore.getFactors());
        
        // Update trust level in device profile
        trustService.updateDeviceTrust(fingerprint.getDeviceId(), trustScore);
    }

    private void detectDeviceAnomalies(DeviceFingerprint fingerprint, 
                                      DeviceFingerprintProcessingResult result) {
        // ML-based anomaly detection
        MLDeviceAnomalyResult mlResult = mlAnalysisService.detectAnomalies(fingerprint);
        
        if (mlResult.hasAnomalies()) {
            result.setMlAnomalies(mlResult.getAnomalies());
            result.setAnomalyScore(mlResult.getAnomalyScore());
        }
    }

    private void updateMLModels(DeviceFingerprint fingerprint, DeviceAnalysisResult analysisResult) {
        // Update device clustering model
        mlAnalysisService.updateDeviceClusteringModel(fingerprint);
        
        // Update anomaly detection model
        mlAnalysisService.updateAnomalyDetectionModel(
            fingerprint,
            analysisResult.getAnomalies()
        );
        
        // Update device risk model
        mlAnalysisService.updateRiskPredictionModel(
            fingerprint,
            analysisResult.getLegitimacy()
        );
    }

    private void triggerSecurityWorkflows(DeviceFingerprint fingerprint, 
                                        DeviceAnalysisResult analysisResult) {
        List<String> workflows = getSecurityWorkflows(
            analysisResult.getLegitimacy(),
            analysisResult.getThreats()
        );
        
        for (String workflowType : workflows) {
            CompletableFuture.runAsync(() -> {
                try {
                    workflowService.triggerWorkflow(workflowType, fingerprint, analysisResult);
                } catch (Exception e) {
                    log.error("Failed to trigger security workflow {} for device {}", 
                             workflowType, fingerprint.getDeviceId(), e);
                }
            });
        }
    }

    private List<String> getSecurityWorkflows(DeviceLegitimacy legitimacy, List<DeviceThreat> threats) {
        if (legitimacy == DeviceLegitimacy.MALICIOUS) {
            return Arrays.asList("MALICIOUS_DEVICE_RESPONSE", "DEVICE_BLOCKING", "THREAT_INVESTIGATION");
        }
        
        if (legitimacy == DeviceLegitimacy.SUSPICIOUS) {
            return Arrays.asList("DEVICE_INVESTIGATION", "ENHANCED_MONITORING");
        }
        
        if (!threats.isEmpty()) {
            return Arrays.asList("THREAT_ASSESSMENT", "RISK_MITIGATION");
        }
        
        return Arrays.asList("STANDARD_DEVICE_MONITORING");
    }

    private void sendDeviceNotifications(DeviceFingerprint fingerprint, 
                                       DeviceAnalysisResult analysisResult,
                                       DeviceFingerprintProcessingResult result) {
        
        Map<String, Object> notificationData = Map.of(
            "deviceId", fingerprint.getDeviceId(),
            "userId", fingerprint.getUserId() != null ? fingerprint.getUserId() : "N/A",
            "legitimacy", analysisResult.getLegitimacy().toString(),
            "confidence", String.format("%.2f%%", analysisResult.getConfidence() * 100),
            "anomalyCount", analysisResult.getAnomalies().size(),
            "threatCount", analysisResult.getThreats().size(),
            "riskScore", result.getRiskScore(),
            "trustScore", result.getTrustScore()
        );
        
        // Critical notifications for malicious devices
        if (analysisResult.getLegitimacy() == DeviceLegitimacy.MALICIOUS) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalDeviceAlert(notificationData);
                notificationService.sendSecurityTeamAlert("MALICIOUS_DEVICE_DETECTED", notificationData);
            });
        }
        
        // High risk notifications
        if (result.getRiskScore() > 75) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendHighRiskDeviceAlert(notificationData);
            });
        }
        
        // User notifications for suspicious devices
        if (fingerprint.getUserId() != null && 
            analysisResult.getLegitimacy() == DeviceLegitimacy.SUSPICIOUS) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendUserDeviceAlert(fingerprint.getUserId(), notificationData);
            });
        }
    }

    private void updateMonitoringSystems(DeviceFingerprint fingerprint, 
                                       DeviceFingerprintProcessingResult result) {
        // Update device monitoring dashboard
        dashboardService.updateDeviceDashboard(fingerprint, result);
        
        // Update device reputation systems
        reputationService.updateDeviceReputation(fingerprint.getDeviceId(), result);
        
        // Update threat intelligence
        threatIntelligenceService.updateDeviceIntelligence(fingerprint, result);
    }

    private void auditDeviceFingerprinting(DeviceFingerprint fingerprint, 
                                         DeviceFingerprintProcessingResult result,
                                         GenericKafkaEvent originalEvent) {
        auditService.auditDeviceFingerprinting(
            fingerprint.getFingerprintId(),
            fingerprint.getDeviceId(),
            fingerprint.getUserId(),
            result.getAnalysisResult().getLegitimacy().toString(),
            result.getRiskScore(),
            result.getTrustScore(),
            originalEvent.getEventId()
        );
    }

    private void recordFingerprintMetrics(DeviceFingerprint fingerprint, 
                                        DeviceFingerprintProcessingResult result,
                                        long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordDeviceFingerprintMetrics(
            fingerprint.getDeviceType().toString(),
            result.getAnalysisResult().getLegitimacy().toString(),
            result.getRiskScore(),
            result.getTrustScore(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS
        );
        
        // Record anomaly metrics
        for (DeviceAnomaly anomaly : result.getAnalysisResult().getAnomalies()) {
            metricsService.recordDeviceAnomalyMetrics(
                anomaly.getAnomalyType(),
                anomaly.getSeverity().toString(),
                anomaly.getConfidence()
            );
        }
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("device-fingerprinting-validation-errors", event);
    }

    private void handleCriticalDeviceError(GenericKafkaEvent event, CriticalDeviceException e) {
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_DEVICE_PROCESSING_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("device-fingerprinting-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying device fingerprinting {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("device-fingerprinting-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for device fingerprinting {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "device-fingerprinting");
        
        kafkaTemplate.send("device-fingerprinting.DLQ", event);
        
        alertingService.createDLQAlert(
            "device-fingerprinting",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleDeviceFingerprintingFailure(GenericKafkaEvent event, String topic, int partition,
                                                long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for device fingerprinting: {}", e.getMessage());
        
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
            "Device Fingerprinting Circuit Breaker Open",
            "Device fingerprinting is failing. Device security compromised."
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

    public static class CriticalDeviceException extends RuntimeException {
        public CriticalDeviceException(String message) {
            super(message);
        }
    }

    public static class DeviceProcessingException extends RuntimeException {
        public DeviceProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}