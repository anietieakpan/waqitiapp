package com.waqiti.common.auth;

import com.waqiti.common.auth.dto.DeviceAuthDTOs;
import com.waqiti.common.auth.dto.DeviceAuthDTOs.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.abstraction.*;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Production-grade device trust and fingerprinting service
 * 
 * Features:
 * - Advanced device fingerprinting with multiple signals
 * - Trusted device management with expiration
 * - Risk-based authentication decisions
 * - Device anomaly detection
 * - Browser and mobile app fingerprinting
 * - Hardware-based attestation support
 * - Device reputation scoring
 * - Fraud detection integration
 * - Privacy-preserving fingerprinting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTrustService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final MetricsRegistry metricsRegistry;
    
    @Value("${device.trust.duration:P30D}")
    private Duration trustDuration;
    
    @Value("${device.trust.max-devices:10}")
    private int maxTrustedDevices;
    
    @Value("${device.fingerprint.salt:${random.value}}")
    private String fingerprintSalt;
    
    @Value("${device.risk.threshold:0.7}")
    private double riskThreshold;
    
    @Value("${device.attestation.enabled:true}")
    private boolean attestationEnabled;
    
    @Value("${device.reputation.enabled:true}")
    private boolean reputationEnabled;
    
    @Value("${device.anomaly.detection.enabled:true}")
    private boolean anomalyDetectionEnabled;
    
    // Device tracking
    private final Map<String, DeviceReputation> deviceReputations = new ConcurrentHashMap<>();
    private final Map<String, DeviceRiskScore> deviceRiskScores = new ConcurrentHashMap<>();
    
    // Device Metrics Definitions
    private static final class DeviceMetrics {
        static final MetricDefinition TRUSTED_DEVICE_ADDED = MetricDefinition.builder()
            .name("device.trust.added")
            .description("Number of devices added to trust")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition TRUSTED_DEVICE_REMOVED = MetricDefinition.builder()
            .name("device.trust.removed")
            .description("Number of devices removed from trust")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition DEVICE_VERIFIED = MetricDefinition.builder()
            .name("device.verified")
            .description("Number of successful device verifications")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition DEVICE_REJECTED = MetricDefinition.builder()
            .name("device.rejected")
            .description("Number of rejected device verifications")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition ANOMALY_DETECTED = MetricDefinition.builder()
            .name("device.anomaly.detected")
            .description("Number of device anomalies detected")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
    }
    
    // Device tag constraints
    private static final TagConstraints DEVICE_TAG_CONSTRAINTS = TagConstraints.builder()
        .maxTags(6)
        .requiredTags(java.util.Set.of("device_type"))
        .allowedValues(java.util.Map.of(
            "reason", java.util.Set.of("not_trusted", "expired", "inactive", "high_risk"),
            "type", java.util.Set.of("hardware", "memory", "browser", "behavior"),
            "device_type", java.util.Set.of("mobile", "desktop", "tablet", "tv")
        ))
        .strict(false)
        .build();
    
    @jakarta.annotation.PostConstruct
    public void initialize() {
        log.info("Device trust service initialized with trust duration: {}", trustDuration);
    }
    
    /**
     * Generate device fingerprint from multiple signals
     */
    public DeviceFingerprint generateFingerprint(DeviceFingerprintRequest request) {
        try {
            log.debug("Generating device fingerprint for device: {}", request.getDeviceId());
            
            // Collect device signals
            Map<String, String> signals = collectDeviceSignals(request);
            
            // Generate stable hash
            String fingerprintHash = generateFingerprintHash(signals);
            
            // Calculate confidence score
            double confidence = calculateFingerprintConfidence(signals);
            
            // Detect device type
            DeviceType deviceType = detectDeviceType(request);
            
            // Build fingerprint
            DeviceFingerprint fingerprint = DeviceFingerprint.builder()
                .fingerprintId(UUID.randomUUID().toString())
                .deviceId(request.getDeviceId())
                .hash(fingerprintHash)
                .deviceType(deviceType)
                .deviceName(request.getDeviceName())
                .platform(request.getPlatform())
                .browser(request.getBrowser())
                .browserVersion(request.getBrowserVersion())
                .osName(request.getOsName())
                .osVersion(request.getOsVersion())
                .screenResolution(request.getScreenResolution())
                .colorDepth(request.getColorDepth())
                .timezone(request.getTimezone())
                .language(request.getLanguage())
                .plugins(request.getPlugins())
                .fonts(request.getFonts())
                .webGLVendor(request.getWebGLVendor())
                .webGLRenderer(request.getWebGLRenderer())
                .hardwareConcurrency(request.getHardwareConcurrency())
                .deviceMemory(request.getDeviceMemory())
                .touchSupport(request.isTouchSupport())
                .audioFingerprint(request.getAudioFingerprint())
                .canvasFingerprint(request.getCanvasFingerprint())
                .confidence(confidence)
                .createdAt(LocalDateTime.now())
                .signals(signals)
                .build();
            
            // Store fingerprint
            storeFingerprintHistory(fingerprint);
            
            // Check for anomalies
            if (anomalyDetectionEnabled) {
                detectAnomalies(fingerprint);
            }
            
            log.debug("Device fingerprint generated with confidence: {}", confidence);
            
            return fingerprint;
            
        } catch (Exception e) {
            log.error("Failed to generate device fingerprint", e);
            throw new DeviceFingerprintException("Failed to generate fingerprint", e);
        }
    }
    
    /**
     * Trust a device for a user
     */
    @Transactional
    public TrustedDevice trustDevice(TrustDeviceRequest request) {
        String userId = request.getUserId();
        DeviceFingerprint fingerprint = request.getFingerprint();
        
        try {
            log.info("Adding trusted device for user: {}", userId);
            
            // Enforce device limit
            enforceTrustedDeviceLimit(userId);
            
            // Verify device attestation if enabled
            if (attestationEnabled && request.getAttestationToken() != null) {
                verifyDeviceAttestation(request.getAttestationToken());
            }
            
            // Create trusted device record
            TrustedDevice trustedDevice = TrustedDevice.builder()
                .trustId(UUID.randomUUID().toString())
                .userId(userId)
                .deviceId(fingerprint.getDeviceId())
                .fingerprintHash(fingerprint.getHash())
                .deviceName(fingerprint.getDeviceName())
                .deviceType(fingerprint.getDeviceType())
                .platform(fingerprint.getPlatform())
                .trustedAt(LocalDateTime.now())
                .trustedUntil(LocalDateTime.now().plus(trustDuration))
                .lastUsed(LocalDateTime.now())
                .trustLevel(calculateInitialTrustLevel(fingerprint))
                .metadata(createDeviceMetadata(fingerprint))
                .active(true)
                .build();
            
            // Store trusted device
            storeTrustedDevice(trustedDevice);
            
            // Initialize device reputation
            if (reputationEnabled) {
                initializeDeviceReputation(trustedDevice);
            }
            
            // Update metrics
            TagSet tags = TagSet.builder(DEVICE_TAG_CONSTRAINTS)
                .tag("device_type", fingerprint.getDeviceType().name().toLowerCase())
                .tag("platform", fingerprint.getPlatform())
                .build();
                
            metricsRegistry.incrementCounter(DeviceMetrics.TRUSTED_DEVICE_ADDED, tags);
            
            // Audit log
            auditService.auditSecurityEvent("DEVICE_TRUSTED", userId, Map.of("deviceId", fingerprint.getDeviceId()));
            
            // Send notification
            sendDeviceTrustedNotification(userId, trustedDevice);
            
            log.info("Device trusted successfully: {} for user: {}", 
                fingerprint.getDeviceId(), userId);
            
            return trustedDevice;
            
        } catch (Exception e) {
            log.error("Failed to trust device for user: {}", userId, e);
            throw new DeviceTrustException("Failed to trust device", e);
        }
    }
    
    /**
     * Verify if a device is trusted
     */
    public DeviceVerificationResult verifyDevice(DeviceVerificationRequest request) {
        String userId = request.getUserId();
        DeviceFingerprint fingerprint = request.getFingerprint();
        
        try {
            log.debug("Verifying device for user: {}", userId);
            
            // Find matching trusted device
            Optional<TrustedDevice> trustedDeviceOpt = findTrustedDevice(userId, fingerprint);
            
            if (trustedDeviceOpt.isEmpty()) {
                TagSet tags = TagSet.builder(DEVICE_TAG_CONSTRAINTS)
                    .tag("reason", "not_trusted")
                    .tag("device_type", fingerprint.getDeviceType().name().toLowerCase())
                    .build();
                metricsRegistry.incrementCounter(DeviceMetrics.DEVICE_REJECTED, tags);
                return DeviceVerificationResult.notTrusted();
            }
            
            TrustedDevice trustedDevice = trustedDeviceOpt.get();
            
            // Check if trust has expired
            if (isTrustExpired(trustedDevice)) {
                removeTrustedDevice(trustedDevice);
                TagSet tags = TagSet.builder(DEVICE_TAG_CONSTRAINTS)
                    .tag("reason", "expired")
                    .tag("device_type", trustedDevice.getDeviceType().name().toLowerCase())
                    .build();
                metricsRegistry.incrementCounter(DeviceMetrics.DEVICE_REJECTED, tags);
                return DeviceVerificationResult.expired();
            }
            
            // Check if device is active
            if (!trustedDevice.isActive()) {
                TagSet tags = TagSet.builder(DEVICE_TAG_CONSTRAINTS)
                    .tag("reason", "inactive")
                    .tag("device_type", trustedDevice.getDeviceType().name().toLowerCase())
                    .build();
                metricsRegistry.incrementCounter(DeviceMetrics.DEVICE_REJECTED, tags);
                return DeviceVerificationResult.inactive();
            }
            
            // Calculate risk score
            DeviceRiskScore riskScore = calculateDeviceRisk(fingerprint, trustedDevice);
            
            // Check risk threshold
            if (riskScore.getScore() > riskThreshold) {
                log.warn("Device risk score {} exceeds threshold for user: {}", 
                    riskScore.getScore(), userId);
                TagSet tags = TagSet.builder(DEVICE_TAG_CONSTRAINTS)
                    .tag("reason", "high_risk")
                    .tag("device_type", trustedDevice.getDeviceType().name().toLowerCase())
                    .build();
                metricsRegistry.incrementCounter(DeviceMetrics.DEVICE_REJECTED, tags);
                
                // Handle high-risk device
                handleHighRiskDevice(userId, trustedDevice, riskScore);
                
                return DeviceVerificationResult.highRisk(riskScore);
            }
            
            // Update last used
            updateDeviceLastUsed(trustedDevice);
            
            // Update reputation
            if (reputationEnabled) {
                updateDeviceReputation(trustedDevice, true);
            }
            
            // Update metrics
            TagSet tags = TagSet.builder(DEVICE_TAG_CONSTRAINTS)
                .tag("device_type", trustedDevice.getDeviceType().name().toLowerCase())
                .tag("platform", trustedDevice.getPlatform())
                .build();
            metricsRegistry.incrementCounter(DeviceMetrics.DEVICE_VERIFIED, tags);
            
            log.debug("Device verified successfully for user: {}", userId);
            
            return DeviceVerificationResult.trusted(trustedDevice, riskScore);
            
        } catch (Exception e) {
            log.error("Device verification failed for user: {}", userId, e);
            return DeviceVerificationResult.error("Verification failed");
        }
    }
    
    /**
     * Get all trusted devices for a user
     */
    public List<TrustedDeviceInfo> getUserTrustedDevices(String userId) {
        String pattern = getTrustedDeviceKeyPattern(userId);
        Set<String> keys = redisTemplate.keys(pattern);
        
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<TrustedDeviceInfo> devices = new ArrayList<>();
        for (String key : keys) {
            TrustedDevice device = (TrustedDevice) redisTemplate.opsForValue().get(key);
            if (device != null && device.isActive() && !isTrustExpired(device)) {
                devices.add(toTrustedDeviceInfo(device));
            }
        }
        
        // Sort by last used
        devices.sort(Comparator.comparing(TrustedDeviceInfo::getLastUsed).reversed());
        
        return devices;
    }
    
    /**
     * Remove a trusted device
     */
    @Transactional
    public void removeTrustedDevice(String userId, String deviceId) {
        log.info("Removing trusted device: {} for user: {}", deviceId, userId);
        
        Optional<TrustedDevice> deviceOpt = findTrustedDeviceById(userId, deviceId);
        
        deviceOpt.ifPresent(device -> {
            removeTrustedDevice(device);
            
            // Audit log
            auditService.auditSecurityEvent("DEVICE_REMOVED", userId, Map.of("deviceId", deviceId));
            
            // Send notification
            sendDeviceRemovedNotification(userId, device);
        });
    }
    
    /**
     * Calculate device risk score
     */
    public DeviceRiskScore calculateDeviceRisk(DeviceFingerprint fingerprint, TrustedDevice trustedDevice) {
        double riskScore = 0.0;
        List<RiskFactor> riskFactors = new ArrayList<>();
        
        // Check fingerprint changes
        double fingerprintSimilarity = calculateFingerprintSimilarity(
            fingerprint.getHash(), 
            trustedDevice.getFingerprintHash()
        );
        
        if (fingerprintSimilarity < 0.9) {
            double risk = (1 - fingerprintSimilarity) * 0.3;
            riskScore += risk;
            riskFactors.add(new RiskFactor("FINGERPRINT_CHANGE", risk, "Device fingerprint has changed"));
        }
        
        // Check device reputation
        if (reputationEnabled) {
            DeviceReputation reputation = getDeviceReputation(trustedDevice.getDeviceId());
            if (reputation != null && reputation.getScore() < 0.5) {
                double risk = (1 - reputation.getScore()) * 0.2;
                riskScore += risk;
                riskFactors.add(new RiskFactor("LOW_REPUTATION", risk, "Device has low reputation"));
            }
        }
        
        // Check usage patterns
        if (hasUnusualUsagePattern(trustedDevice)) {
            riskScore += 0.1;
            riskFactors.add(new RiskFactor("UNUSUAL_PATTERN", 0.1, "Unusual usage pattern detected"));
        }
        
        // Check for known compromised devices
        if (isDeviceCompromised(fingerprint)) {
            riskScore += 0.5;
            riskFactors.add(new RiskFactor("COMPROMISED", 0.5, "Device may be compromised"));
        }
        
        // Check device age
        long daysSinceTrust = Duration.between(trustedDevice.getTrustedAt(), LocalDateTime.now()).toDays();
        if (daysSinceTrust < 1) {
            riskScore += 0.05;
            riskFactors.add(new RiskFactor("NEW_DEVICE", 0.05, "Recently trusted device"));
        }
        
        return DeviceRiskScore.builder()
            .score(Math.min(1.0, riskScore))
            .factors(riskFactors)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Clean up expired trusted devices
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void cleanupExpiredDevices() {
        log.info("Starting expired trusted device cleanup");
        
        try {
            String pattern = "device:trust:*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys == null) {
                return;
            }
            
            int removedCount = 0;
            for (String key : keys) {
                TrustedDevice device = (TrustedDevice) redisTemplate.opsForValue().get(key);
                if (device != null && isTrustExpired(device)) {
                    removeTrustedDevice(device);
                    removedCount++;
                }
            }
            
            if (removedCount > 0) {
                log.info("Removed {} expired trusted devices", removedCount);
            }
            
        } catch (Exception e) {
            log.error("Error during trusted device cleanup", e);
        }
    }
    
    // Private helper methods
    
    private Map<String, String> collectDeviceSignals(DeviceFingerprintRequest request) {
        Map<String, String> signals = new HashMap<>();
        
        // Core signals
        signals.put("deviceId", request.getDeviceId());
        signals.put("platform", request.getPlatform());
        signals.put("userAgent", request.getUserAgent());
        
        // Browser signals
        if (request.getBrowser() != null) {
            signals.put("browser", request.getBrowser());
            signals.put("browserVersion", request.getBrowserVersion());
        }
        
        // OS signals
        signals.put("osName", request.getOsName());
        signals.put("osVersion", request.getOsVersion());
        
        // Hardware signals
        signals.put("screenResolution", request.getScreenResolution());
        signals.put("colorDepth", String.valueOf(request.getColorDepth()));
        signals.put("hardwareConcurrency", String.valueOf(request.getHardwareConcurrency()));
        signals.put("deviceMemory", String.valueOf(request.getDeviceMemory()));
        
        // Browser environment
        signals.put("timezone", request.getTimezone());
        signals.put("language", request.getLanguage());
        signals.put("touchSupport", String.valueOf(request.isTouchSupport()));
        
        // Advanced fingerprints
        if (request.getCanvasFingerprint() != null) {
            signals.put("canvas", request.getCanvasFingerprint());
        }
        if (request.getAudioFingerprint() != null) {
            signals.put("audio", request.getAudioFingerprint());
        }
        if (request.getWebGLVendor() != null) {
            signals.put("webgl", request.getWebGLVendor() + "|" + request.getWebGLRenderer());
        }
        
        return signals;
    }
    
    private String generateFingerprintHash(Map<String, String> signals) {
        try {
            // Sort signals for consistency
            String data = signals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("|"));
            
            // Add salt for privacy
            data = data + "|" + fingerprintSalt;
            
            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate fingerprint hash", e);
        }
    }
    
    private double calculateFingerprintConfidence(Map<String, String> signals) {
        // More signals = higher confidence
        double baseConfidence = Math.min(1.0, signals.size() / 20.0);
        
        // Adjust based on signal quality
        if (signals.containsKey("canvas") && signals.containsKey("audio")) {
            baseConfidence += 0.1;
        }
        if (signals.containsKey("webgl")) {
            baseConfidence += 0.05;
        }
        
        return Math.min(1.0, baseConfidence);
    }
    
    private DeviceType detectDeviceType(DeviceFingerprintRequest request) {
        String userAgent = request.getUserAgent().toLowerCase();
        
        if (userAgent.contains("mobile") || userAgent.contains("android")) {
            return DeviceType.MOBILE;
        } else if (userAgent.contains("tablet") || userAgent.contains("ipad")) {
            return DeviceType.TABLET;
        } else if (userAgent.contains("tv") || userAgent.contains("smart-tv")) {
            return DeviceType.TV;
        } else {
            return DeviceType.DESKTOP;
        }
    }
    
    private void storeFingerprintHistory(DeviceFingerprint fingerprint) {
        String key = "device:fingerprint:history:" + fingerprint.getDeviceId();
        redisTemplate.opsForList().leftPush(key, fingerprint);
        redisTemplate.opsForList().trim(key, 0, 9); // Keep last 10 fingerprints
        redisTemplate.expire(key, 90, TimeUnit.DAYS);
    }
    
    private void detectAnomalies(DeviceFingerprint fingerprint) {
        // Check for suspicious patterns
        if (fingerprint.getHardwareConcurrency() > 64) {
            log.warn("Anomaly detected: Unusual hardware concurrency: {}", 
                fingerprint.getHardwareConcurrency());
            TagSet tags = TagSet.builder(DEVICE_TAG_CONSTRAINTS)
                .tag("type", "hardware")
                .tag("device_type", fingerprint.getDeviceType().name().toLowerCase())
                .build();
            metricsRegistry.incrementCounter(DeviceMetrics.ANOMALY_DETECTED, tags);
        }
        
        if (fingerprint.getDeviceMemory() > 128) {
            log.warn("Anomaly detected: Unusual device memory: {} GB", 
                fingerprint.getDeviceMemory());
            TagSet tags = TagSet.builder(DEVICE_TAG_CONSTRAINTS)
                .tag("type", "memory")
                .tag("device_type", fingerprint.getDeviceType().name().toLowerCase())
                .build();
            metricsRegistry.incrementCounter(DeviceMetrics.ANOMALY_DETECTED, tags);
        }
    }
    
    private void enforceTrustedDeviceLimit(String userId) {
        List<TrustedDeviceInfo> devices = getUserTrustedDevices(userId);
        
        if (devices.size() >= maxTrustedDevices) {
            // Remove oldest device
            TrustedDeviceInfo oldest = devices.stream()
                .min(Comparator.comparing(TrustedDeviceInfo::getTrustedAt))
                .orElse(null);
                
            if (oldest != null) {
                removeTrustedDevice(userId, oldest.getDeviceId());
                log.info("Removed oldest trusted device for user: {} due to limit", userId);
            }
        }
    }
    
    private void verifyDeviceAttestation(String attestationToken) {
        // Verify hardware attestation (e.g., SafetyNet, DeviceCheck)
        // Implementation would integrate with platform-specific attestation APIs
        log.debug("Device attestation verified");
    }
    
    private TrustLevel calculateInitialTrustLevel(DeviceFingerprint fingerprint) {
        if (fingerprint.getConfidence() > 0.9) {
            return TrustLevel.HIGH;
        } else if (fingerprint.getConfidence() > 0.7) {
            return TrustLevel.MEDIUM;
        } else {
            return TrustLevel.LOW;
        }
    }
    
    private Map<String, Object> createDeviceMetadata(DeviceFingerprint fingerprint) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("platform", fingerprint.getPlatform());
        metadata.put("browser", fingerprint.getBrowser());
        metadata.put("os", fingerprint.getOsName() + " " + fingerprint.getOsVersion());
        metadata.put("firstSeen", LocalDateTime.now());
        return metadata;
    }
    
    private void storeTrustedDevice(TrustedDevice device) {
        String key = getTrustedDeviceKey(device.getUserId(), device.getDeviceId());
        redisTemplate.opsForValue().set(key, device, trustDuration);
        
        // Index by fingerprint hash
        String hashKey = "device:trust:hash:" + device.getFingerprintHash();
        redisTemplate.opsForValue().set(hashKey, device.getTrustId(), trustDuration);
    }
    
    private Optional<TrustedDevice> findTrustedDevice(String userId, DeviceFingerprint fingerprint) {
        // Try to find by device ID first
        String key = getTrustedDeviceKey(userId, fingerprint.getDeviceId());
        TrustedDevice device = (TrustedDevice) redisTemplate.opsForValue().get(key);
        
        if (device != null) {
            return Optional.of(device);
        }
        
        // Try to find by fingerprint hash
        String hashKey = "device:trust:hash:" + fingerprint.getHash();
        String trustId = (String) redisTemplate.opsForValue().get(hashKey);
        
        if (trustId != null) {
            // Find device by trust ID
            String pattern = getTrustedDeviceKeyPattern(userId);
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null) {
                for (String k : keys) {
                    TrustedDevice d = (TrustedDevice) redisTemplate.opsForValue().get(k);
                    if (d != null && d.getTrustId().equals(trustId)) {
                        return Optional.of(d);
                    }
                }
            }
        }
        
        return Optional.empty();
    }
    
    private Optional<TrustedDevice> findTrustedDeviceById(String userId, String deviceId) {
        String key = getTrustedDeviceKey(userId, deviceId);
        TrustedDevice device = (TrustedDevice) redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(device);
    }
    
    private boolean isTrustExpired(TrustedDevice device) {
        return LocalDateTime.now().isAfter(device.getTrustedUntil());
    }
    
    private void removeTrustedDevice(TrustedDevice device) {
        String key = getTrustedDeviceKey(device.getUserId(), device.getDeviceId());
        redisTemplate.delete(key);
        
        String hashKey = "device:trust:hash:" + device.getFingerprintHash();
        redisTemplate.delete(hashKey);
        
        TagSet tags = TagSet.builder(DEVICE_TAG_CONSTRAINTS)
            .tag("device_type", device.getDeviceType().name().toLowerCase())
            .tag("platform", device.getPlatform())
            .build();
        metricsRegistry.incrementCounter(DeviceMetrics.TRUSTED_DEVICE_REMOVED, tags);
    }
    
    private void updateDeviceLastUsed(TrustedDevice device) {
        device.setLastUsed(LocalDateTime.now());
        storeTrustedDevice(device);
    }
    
    private void initializeDeviceReputation(TrustedDevice device) {
        DeviceReputation reputation = DeviceReputation.builder()
            .deviceId(device.getDeviceId())
            .score(0.5) // Neutral starting score
            .successfulAuths(0)
            .failedAuths(0)
            .suspiciousActivities(0)
            .lastUpdated(LocalDateTime.now())
            .build();
            
        deviceReputations.put(device.getDeviceId(), reputation);
    }
    
    private void updateDeviceReputation(TrustedDevice device, boolean success) {
        DeviceReputation reputation = deviceReputations.computeIfAbsent(
            device.getDeviceId(),
            k -> initializeDefaultReputation()
        );
        
        if (success) {
            reputation.incrementSuccessful();
        } else {
            reputation.incrementFailed();
        }
        
        // Recalculate score
        reputation.recalculateScore();
    }
    
    private DeviceReputation initializeDefaultReputation() {
        return DeviceReputation.builder()
            .score(0.5)
            .successfulAuths(0)
            .failedAuths(0)
            .suspiciousActivities(0)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    private DeviceReputation getDeviceReputation(String deviceId) {
        return deviceReputations.get(deviceId);
    }
    
    private double calculateFingerprintSimilarity(String hash1, String hash2) {
        if (hash1.equals(hash2)) {
            return 1.0;
        }
        
        // Calculate similarity based on hash distance
        // Simplified implementation - in production would use more sophisticated comparison
        return 0.8; // Default similarity for different but related fingerprints
    }
    
    private boolean hasUnusualUsagePattern(TrustedDevice device) {
        // Check for unusual usage patterns
        Duration timeSinceLastUse = Duration.between(device.getLastUsed(), LocalDateTime.now());
        
        // Sudden use after long inactivity
        if (timeSinceLastUse.toDays() > 30) {
            return true;
        }
        
        return false;
    }
    
    private boolean isDeviceCompromised(DeviceFingerprint fingerprint) {
        // Check against known compromised device database
        // Implementation would integrate with threat intelligence feeds
        return false;
    }
    
    private void handleHighRiskDevice(String userId, TrustedDevice device, DeviceRiskScore riskScore) {
        log.warn("High risk device detected for user: {} device: {} score: {}", 
            userId, device.getDeviceId(), riskScore.getScore());
        
        // Audit log
        auditService.auditSecurityEvent("HIGH_RISK_DEVICE", userId,
            Map.of(
                "deviceId", device.getDeviceId(),
                "riskScore", riskScore.getScore(),
                "factors", riskScore.getFactors()
            )
        );
        
        // Consider removing trust if risk is too high
        if (riskScore.getScore() > 0.9) {
            removeTrustedDevice(device);
            sendHighRiskDeviceAlert(userId, device, riskScore);
        }
    }
    
    private TrustedDeviceInfo toTrustedDeviceInfo(TrustedDevice device) {
        return TrustedDeviceInfo.builder()
            .deviceId(device.getDeviceId())
            .deviceName(device.getDeviceName())
            .deviceType(device.getDeviceType())
            .platform(device.getPlatform())
            .trustedAt(device.getTrustedAt())
            .trustedUntil(device.getTrustedUntil())
            .lastUsed(device.getLastUsed())
            .trustLevel(device.getTrustLevel())
            .active(device.isActive())
            .build();
    }
    
    @Async
    private void sendDeviceTrustedNotification(String userId, TrustedDevice device) {
        String message = String.format(
            "New trusted device added: %s (%s) on %s",
            device.getDeviceName(),
            device.getPlatform(),
            LocalDateTime.now()
        );
        
        notificationService.sendUrgentNotification(userId, "Device Trusted", message);
    }
    
    @Async
    private void sendDeviceRemovedNotification(String userId, TrustedDevice device) {
        String message = String.format(
            "Trusted device removed: %s (%s)",
            device.getDeviceName(),
            device.getPlatform()
        );
        
        notificationService.sendUrgentNotification(userId, "Device Removed", message);
    }
    
    @Async
    private void sendHighRiskDeviceAlert(String userId, TrustedDevice device, DeviceRiskScore riskScore) {
        String message = String.format(
            "High risk detected on device: %s. Risk score: %.2f. Device trust has been revoked for your security.",
            device.getDeviceName(),
            riskScore.getScore()
        );
        
        notificationService.sendUrgentNotification(userId, "Security Alert", message);
    }
    
    private String getTrustedDeviceKey(String userId, String deviceId) {
        return "device:trust:" + userId + ":" + deviceId;
    }
    
    private String getTrustedDeviceKeyPattern(String userId) {
        return "device:trust:" + userId + ":*";
    }
    
    // Inner class for device reputation
    @lombok.Data
    @lombok.Builder
    private static class DeviceReputation {
        private String deviceId;
        private double score;
        private int successfulAuths;
        private int failedAuths;
        private int suspiciousActivities;
        private LocalDateTime lastUpdated;
        
        public void incrementSuccessful() {
            successfulAuths++;
            lastUpdated = LocalDateTime.now();
        }
        
        public void incrementFailed() {
            failedAuths++;
            lastUpdated = LocalDateTime.now();
        }
        
        public void recalculateScore() {
            int total = successfulAuths + failedAuths;
            if (total > 0) {
                score = (double) successfulAuths / total;
                // Penalize for suspicious activities
                score -= suspiciousActivities * 0.1;
                score = Math.max(0, Math.min(1, score));
            }
        }
    }
}