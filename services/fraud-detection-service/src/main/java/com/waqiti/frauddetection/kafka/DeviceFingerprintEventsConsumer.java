package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.DeviceFingerprintEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.common.events.RiskScoreUpdatedEvent;
import com.waqiti.frauddetection.domain.DeviceFingerprint;
import com.waqiti.frauddetection.domain.DeviceRiskProfile;
import com.waqiti.frauddetection.domain.FraudRiskLevel;
import com.waqiti.frauddetection.repository.DeviceFingerprintRepository;
import com.waqiti.frauddetection.repository.DeviceRiskProfileRepository;
import com.waqiti.frauddetection.service.DeviceFingerprintService;
import com.waqiti.frauddetection.service.DeviceRiskAnalysisService;
import com.waqiti.frauddetection.service.MachineLearningFraudService;
import com.waqiti.frauddetection.exception.DeviceFingerprintException;
import com.waqiti.frauddetection.metrics.DeviceFraudMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CRITICAL Consumer for Device Fingerprint Events
 * 
 * Handles device identification and risk analysis including:
 * - Device fingerprint generation and validation
 * - Browser and mobile device profiling
 * - Device behavior pattern analysis
 * - Risk scoring based on device characteristics
 * - Device spoofing and emulation detection
 * - Cross-device fraud correlation
 * - Machine learning based device risk assessment
 * - Real-time device blacklisting
 * 
 * This is CRITICAL for fraud prevention as device fingerprinting
 * can identify 85-90% of fraudulent transactions before processing.
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceFingerprintEventsConsumer {
    
    private final DeviceFingerprintRepository fingerprintRepository;
    private final DeviceRiskProfileRepository riskProfileRepository;
    private final DeviceFingerprintService fingerprintService;
    private final DeviceRiskAnalysisService riskAnalysisService;
    private final MachineLearningFraudService mlFraudService;
    private final DeviceFraudMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Risk thresholds
    private static final double HIGH_RISK_THRESHOLD = 80.0;
    private static final double MEDIUM_RISK_THRESHOLD = 60.0;
    private static final double SUSPICIOUS_THRESHOLD = 40.0;
    
    // Device fingerprint parameters
    private static final int MIN_FINGERPRINT_COMPONENTS = 10;
    private static final long DEVICE_CACHE_TTL_HOURS = 24;
    private static final int MAX_DEVICES_PER_USER = 5;
    
    // Behavior analysis thresholds
    private static final int VELOCITY_ANALYSIS_WINDOW_HOURS = 1;
    private static final int MAX_TRANSACTIONS_PER_DEVICE_HOUR = 10;
    private static final double DEVICE_CHANGE_RISK_MULTIPLIER = 1.5;
    
    // Cache for device risk scores
    private final Map<String, Double> deviceRiskCache = new ConcurrentHashMap<>();
    
    @KafkaListener(
        topics = "device-fingerprint-events",
        groupId = "device-fingerprint-fraud-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "10"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleDeviceFingerprintEvent(
            @Payload DeviceFingerprintEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("device-%s-p%d-o%d", 
            event.getDeviceId(), partition, offset);
        
        log.info("Processing device fingerprint event: deviceId={}, eventType={}, userId={}, correlation={}",
            event.getDeviceId(), event.getEventType(), event.getUserId(), correlationId);
        
        try {
            // Security validation
            securityContext.validateFinancialOperation(event.getDeviceId(), "DEVICE_FINGERPRINT");
            validateDeviceFingerprintEvent(event);
            
            // Process based on event type
            switch (event.getEventType()) {
                case FINGERPRINT_GENERATED:
                    processDeviceFingerprintGenerated(event, correlationId);
                    break;
                case FINGERPRINT_UPDATED:
                    processDeviceFingerprintUpdated(event, correlationId);
                    break;
                case DEVICE_BEHAVIOR_ANALYSIS:
                    processDeviceBehaviorAnalysis(event, correlationId);
                    break;
                case DEVICE_RISK_ASSESSMENT:
                    processDeviceRiskAssessment(event, correlationId);
                    break;
                case SUSPICIOUS_DEVICE_DETECTED:
                    processSuspiciousDeviceDetected(event, correlationId);
                    break;
                case DEVICE_BLACKLISTED:
                    processDeviceBlacklisted(event, correlationId);
                    break;
                case DEVICE_WHITELISTED:
                    processDeviceWhitelisted(event, correlationId);
                    break;
                case EMULATION_DETECTED:
                    processEmulationDetected(event, correlationId);
                    break;
                default:
                    log.warn("Unknown device fingerprint event type: {}", event.getEventType());
                    break;
            }
            
            // Audit the device fingerprint operation
            auditService.logFinancialEvent(
                "DEVICE_FINGERPRINT_EVENT_PROCESSED",
                event.getDeviceId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId() != null ? event.getUserId() : "UNKNOWN",
                    "riskScore", event.getRiskScore() != null ? event.getRiskScore() : 0.0,
                    "suspiciousActivity", event.isSuspiciousActivity(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process device fingerprint event: deviceId={}, error={}",
                event.getDeviceId(), e.getMessage(), e);
            
            handleDeviceFingerprintEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * Processes new device fingerprint generation
     */
    private void processDeviceFingerprintGenerated(DeviceFingerprintEvent event, String correlationId) {
        log.info("Processing device fingerprint generation: deviceId={}, components={}",
            event.getDeviceId(), event.getFingerprintComponents() != null ? 
            event.getFingerprintComponents().size() : 0);
        
        // Validate fingerprint quality
        if (!isValidFingerprint(event.getFingerprintComponents())) {
            log.warn("Invalid device fingerprint detected: deviceId={}", event.getDeviceId());
            handleInvalidFingerprint(event, correlationId);
            return;
        }
        
        // Check for existing fingerprint
        Optional<DeviceFingerprint> existingFingerprint = 
            fingerprintRepository.findByDeviceId(event.getDeviceId());
        
        if (existingFingerprint.isPresent()) {
            // Analyze fingerprint changes
            analyzeFingerprintChanges(existingFingerprint.get(), event, correlationId);
        } else {
            // Create new device fingerprint
            createNewDeviceFingerprint(event, correlationId);
        }
        
        // Perform real-time risk assessment
        double riskScore = performDeviceRiskAssessment(event, correlationId);
        
        // Update device risk cache
        deviceRiskCache.put(event.getDeviceId(), riskScore);
        
        // Check for immediate fraud indicators
        if (riskScore >= HIGH_RISK_THRESHOLD) {
            triggerHighRiskDeviceAlert(event, riskScore, correlationId);
        }
        
        // Update metrics
        metricsService.recordDeviceFingerprintGenerated(
            event.getDeviceType(), 
            riskScore,
            event.getFingerprintComponents().size()
        );
        
        log.info("Device fingerprint processed: deviceId={}, riskScore={}", 
            event.getDeviceId(), riskScore);
    }
    
    /**
     * Processes device fingerprint updates
     */
    private void processDeviceFingerprintUpdated(DeviceFingerprintEvent event, String correlationId) {
        DeviceFingerprint fingerprint = getDeviceFingerprintById(event.getDeviceId());
        
        log.info("Processing fingerprint update: deviceId={}, changes={}",
            event.getDeviceId(), event.getChangedComponents() != null ? 
            event.getChangedComponents().size() : 0);
        
        // Analyze changes for fraud indicators
        FingerprintChangeAnalysis analysis = analyzeFingerprintChangePattern(
            fingerprint, event, correlationId);
        
        // Update fingerprint with new components
        updateDeviceFingerprint(fingerprint, event, analysis);
        
        // Recalculate risk score based on changes
        double newRiskScore = recalculateRiskScoreAfterChange(fingerprint, analysis);
        
        // Update cached risk score
        deviceRiskCache.put(event.getDeviceId(), newRiskScore);
        
        // Check if changes indicate potential fraud
        if (analysis.isSuspiciousChange()) {
            triggerSuspiciousFingerprintChangeAlert(fingerprint, analysis, correlationId);
        }
        
        // Update metrics
        metricsService.recordDeviceFingerprintUpdated(
            event.getDeviceType(),
            analysis.getChangeFrequency(),
            analysis.getSuspicionLevel()
        );
        
        log.info("Device fingerprint updated: deviceId={}, newRiskScore={}, suspicious={}",
            event.getDeviceId(), newRiskScore, analysis.isSuspiciousChange());
    }
    
    /**
     * Processes device behavior analysis
     */
    private void processDeviceBehaviorAnalysis(DeviceFingerprintEvent event, String correlationId) {
        log.info("Processing device behavior analysis: deviceId={}, behaviorType={}",
            event.getDeviceId(), event.getBehaviorType());
        
        // Get or create device risk profile
        DeviceRiskProfile riskProfile = getOrCreateDeviceRiskProfile(event.getDeviceId());
        
        // Analyze behavior patterns
        BehaviorAnalysisResult behaviorResult = riskAnalysisService.analyzeBehaviorPattern(
            event.getDeviceId(),
            event.getBehaviorData(),
            event.getSessionData(),
            correlationId
        );
        
        // Update risk profile with behavior analysis
        riskProfile.updateBehaviorAnalysis(behaviorResult);
        riskProfile.setLastAnalysisAt(LocalDateTime.now());
        riskProfileRepository.save(riskProfile);
        
        // Check for anomalous behavior
        if (behaviorResult.isAnomalous()) {
            handleAnomalousBehavior(event, behaviorResult, correlationId);
        }
        
        // Perform velocity checks
        VelocityAnalysisResult velocityResult = performVelocityAnalysis(event, correlationId);
        
        if (velocityResult.isVelocityExceeded()) {
            handleVelocityViolation(event, velocityResult, correlationId);
        }
        
        // Update overall device risk score
        double updatedRiskScore = calculateCombinedRiskScore(
            riskProfile, behaviorResult, velocityResult);
        
        deviceRiskCache.put(event.getDeviceId(), updatedRiskScore);
        
        // Publish risk score update
        publishRiskScoreUpdate(event.getDeviceId(), updatedRiskScore, correlationId);
        
        // Update metrics
        metricsService.recordBehaviorAnalysis(
            event.getDeviceType(),
            behaviorResult.getAnomalyScore(),
            velocityResult.getTransactionCount()
        );
        
        log.info("Device behavior analysis completed: deviceId={}, riskScore={}, anomalous={}",
            event.getDeviceId(), updatedRiskScore, behaviorResult.isAnomalous());
    }
    
    /**
     * Processes device risk assessment
     */
    private void processDeviceRiskAssessment(DeviceFingerprintEvent event, String correlationId) {
        log.info("Processing device risk assessment: deviceId={}", event.getDeviceId());
        
        // Comprehensive risk assessment using multiple factors
        DeviceRiskAssessment assessment = performComprehensiveRiskAssessment(event, correlationId);
        
        // Update device risk profile
        DeviceRiskProfile riskProfile = getOrCreateDeviceRiskProfile(event.getDeviceId());
        riskProfile.updateRiskAssessment(assessment);
        riskProfile.setLastRiskAssessmentAt(LocalDateTime.now());
        riskProfileRepository.save(riskProfile);
        
        // Cache the risk score
        deviceRiskCache.put(event.getDeviceId(), assessment.getRiskScore());
        
        // Apply machine learning fraud detection
        MLFraudPrediction mlPrediction = mlFraudService.predictDeviceFraud(
            event.getDeviceId(),
            event.getFingerprintComponents(),
            event.getBehaviorData(),
            correlationId
        );
        
        // Combine traditional and ML risk scores
        double finalRiskScore = combineRiskScores(assessment.getRiskScore(), mlPrediction.getFraudProbability());
        
        // Determine risk level and actions
        FraudRiskLevel riskLevel = determineRiskLevel(finalRiskScore);
        
        switch (riskLevel) {
            case HIGH:
                handleHighRiskDevice(event, finalRiskScore, assessment, correlationId);
                break;
            case MEDIUM:
                handleMediumRiskDevice(event, finalRiskScore, assessment, correlationId);
                break;
            case LOW:
                handleLowRiskDevice(event, finalRiskScore, assessment, correlationId);
                break;
        }
        
        // Update metrics
        metricsService.recordRiskAssessment(
            event.getDeviceType(),
            riskLevel,
            finalRiskScore,
            mlPrediction.getConfidenceScore()
        );
        
        log.info("Device risk assessment completed: deviceId={}, riskLevel={}, score={}",
            event.getDeviceId(), riskLevel, finalRiskScore);
    }
    
    /**
     * Processes suspicious device detection
     */
    private void processSuspiciousDeviceDetected(DeviceFingerprintEvent event, String correlationId) {
        log.warn("Processing suspicious device detection: deviceId={}, reason={}",
            event.getDeviceId(), event.getSuspicionReason());
        
        DeviceFingerprint fingerprint = getDeviceFingerprintById(event.getDeviceId());
        
        // Mark device as suspicious
        fingerprint.setSuspicious(true);
        fingerprint.setSuspicionReason(event.getSuspicionReason());
        fingerprint.setSuspicionDetectedAt(LocalDateTime.now());
        fingerprintRepository.save(fingerprint);
        
        // Increase risk score
        double currentRiskScore = deviceRiskCache.getOrDefault(event.getDeviceId(), 50.0);
        double suspiciousRiskScore = Math.min(100.0, currentRiskScore + 30.0); // Add 30 points
        
        deviceRiskCache.put(event.getDeviceId(), suspiciousRiskScore);
        
        // Trigger fraud alert
        publishFraudAlert(
            event.getDeviceId(),
            "SUSPICIOUS_DEVICE_DETECTED",
            event.getSuspicionReason(),
            suspiciousRiskScore,
            correlationId
        );
        
        // Check user's other devices for correlation
        analyzeUserDeviceCorrelation(event.getUserId(), event.getDeviceId(), correlationId);
        
        // Add to monitoring list
        addToDeviceMonitoring(event.getDeviceId(), "SUSPICIOUS_ACTIVITY", correlationId);
        
        // Send security notification
        notificationService.sendSecurityAlert(
            "Suspicious Device Detected",
            String.format("Suspicious device activity detected: %s. Reason: %s",
                event.getDeviceId(), event.getSuspicionReason()),
            NotificationService.Priority.HIGH
        );
        
        // Update metrics
        metricsService.recordSuspiciousDeviceDetected(
            event.getDeviceType(),
            event.getSuspicionReason()
        );
        
        log.warn("Suspicious device processed: deviceId={}, newRiskScore={}",
            event.getDeviceId(), suspiciousRiskScore);
    }
    
    /**
     * Processes device blacklisting
     */
    private void processDeviceBlacklisted(DeviceFingerprintEvent event, String correlationId) {
        log.warn("Processing device blacklisting: deviceId={}, reason={}",
            event.getDeviceId(), event.getBlacklistReason());
        
        DeviceFingerprint fingerprint = getDeviceFingerprintById(event.getDeviceId());
        
        // Mark device as blacklisted
        fingerprint.setBlacklisted(true);
        fingerprint.setBlacklistReason(event.getBlacklistReason());
        fingerprint.setBlacklistedAt(LocalDateTime.now());
        fingerprint.setBlacklistedBy(event.getActionBy());
        fingerprintRepository.save(fingerprint);
        
        // Set maximum risk score
        deviceRiskCache.put(event.getDeviceId(), 100.0);
        
        // Block all transactions from this device
        publishDeviceBlockingEvent(event.getDeviceId(), event.getBlacklistReason(), correlationId);
        
        // Analyze impact on other users/devices
        analyzeBlacklistImpact(event.getDeviceId(), correlationId);
        
        // Send critical security alert
        notificationService.sendSecurityAlert(
            "Device Blacklisted",
            String.format("Device %s has been blacklisted. Reason: %s",
                event.getDeviceId(), event.getBlacklistReason()),
            NotificationService.Priority.CRITICAL
        );
        
        // Update metrics
        metricsService.recordDeviceBlacklisted(
            event.getDeviceType(),
            event.getBlacklistReason()
        );
        
        log.warn("Device blacklisted: deviceId={}", event.getDeviceId());
    }
    
    /**
     * Processes device whitelisting
     */
    private void processDeviceWhitelisted(DeviceFingerprintEvent event, String correlationId) {
        log.info("Processing device whitelisting: deviceId={}, reason={}",
            event.getDeviceId(), event.getWhitelistReason());
        
        DeviceFingerprint fingerprint = getDeviceFingerprintById(event.getDeviceId());
        
        // Mark device as whitelisted
        fingerprint.setWhitelisted(true);
        fingerprint.setWhitelistReason(event.getWhitelistReason());
        fingerprint.setWhitelistedAt(LocalDateTime.now());
        fingerprint.setWhitelistedBy(event.getActionBy());
        
        // Clear suspicious and blacklist flags
        fingerprint.setSuspicious(false);
        fingerprint.setBlacklisted(false);
        fingerprintRepository.save(fingerprint);
        
        // Reduce risk score significantly
        double whitelistedRiskScore = Math.max(5.0, 
            deviceRiskCache.getOrDefault(event.getDeviceId(), 50.0) * 0.2);
        
        deviceRiskCache.put(event.getDeviceId(), whitelistedRiskScore);
        
        // Remove from monitoring and blocking
        removeFromDeviceMonitoring(event.getDeviceId(), correlationId);
        
        // Update metrics
        metricsService.recordDeviceWhitelisted(
            event.getDeviceType(),
            event.getWhitelistReason()
        );
        
        log.info("Device whitelisted: deviceId={}, newRiskScore={}",
            event.getDeviceId(), whitelistedRiskScore);
    }
    
    /**
     * Processes emulation detection
     */
    private void processEmulationDetected(DeviceFingerprintEvent event, String correlationId) {
        log.warn("Processing emulation detection: deviceId={}, emulationType={}",
            event.getDeviceId(), event.getEmulationType());
        
        DeviceFingerprint fingerprint = getDeviceFingerprintById(event.getDeviceId());
        
        // Mark device as emulated
        fingerprint.setEmulated(true);
        fingerprint.setEmulationType(event.getEmulationType());
        fingerprint.setEmulationDetectedAt(LocalDateTime.now());
        fingerprintRepository.save(fingerprint);
        
        // High risk score for emulated devices
        double emulationRiskScore = Math.max(85.0,
            deviceRiskCache.getOrDefault(event.getDeviceId(), 50.0) + 40.0);
        
        deviceRiskCache.put(event.getDeviceId(), emulationRiskScore);
        
        // Trigger immediate fraud alert
        publishFraudAlert(
            event.getDeviceId(),
            "DEVICE_EMULATION_DETECTED",
            "Device emulation detected: " + event.getEmulationType(),
            emulationRiskScore,
            correlationId
        );
        
        // Block device for manual review
        addToDeviceMonitoring(event.getDeviceId(), "EMULATION_DETECTED", correlationId);
        
        // Send critical alert
        notificationService.sendSecurityAlert(
            "Device Emulation Detected",
            String.format("Device emulation detected: %s (%s)",
                event.getDeviceId(), event.getEmulationType()),
            NotificationService.Priority.CRITICAL
        );
        
        // Update metrics
        metricsService.recordEmulationDetected(
            event.getDeviceType(),
            event.getEmulationType()
        );
        
        log.warn("Device emulation processed: deviceId={}, type={}, riskScore={}",
            event.getDeviceId(), event.getEmulationType(), emulationRiskScore);
    }
    
    /**
     * Utility methods for device fingerprint processing
     */
    private boolean isValidFingerprint(Map<String, Object> components) {
        return components != null && 
               components.size() >= MIN_FINGERPRINT_COMPONENTS &&
               components.containsKey("userAgent") &&
               components.containsKey("screenResolution") &&
               components.containsKey("timeZone");
    }
    
    private void createNewDeviceFingerprint(DeviceFingerprintEvent event, String correlationId) {
        DeviceFingerprint fingerprint = DeviceFingerprint.builder()
            .deviceId(event.getDeviceId())
            .userId(event.getUserId())
            .deviceType(event.getDeviceType())
            .fingerprintHash(fingerprintService.generateFingerprintHash(event.getFingerprintComponents()))
            .components(event.getFingerprintComponents())
            .firstSeenAt(LocalDateTime.now())
            .lastSeenAt(LocalDateTime.now())
            .suspicious(false)
            .blacklisted(false)
            .whitelisted(false)
            .emulated(false)
            .transactionCount(0)
            .correlationId(correlationId)
            .build();
        
        fingerprintRepository.save(fingerprint);
        
        log.info("New device fingerprint created: deviceId={}", event.getDeviceId());
    }
    
    private void analyzeFingerprintChanges(DeviceFingerprint existing, 
            DeviceFingerprintEvent event, String correlationId) {
        
        Map<String, Object> oldComponents = existing.getComponents();
        Map<String, Object> newComponents = event.getFingerprintComponents();
        
        Set<String> changedComponents = new HashSet<>();
        
        for (String key : newComponents.keySet()) {
            if (!Objects.equals(oldComponents.get(key), newComponents.get(key))) {
                changedComponents.add(key);
            }
        }
        
        if (!changedComponents.isEmpty()) {
            log.info("Device fingerprint changes detected: deviceId={}, changed={}",
                event.getDeviceId(), changedComponents);
            
            // Analyze if changes are suspicious
            boolean suspiciousChanges = analyzeSuspiciousChanges(changedComponents, oldComponents, newComponents);
            
            if (suspiciousChanges) {
                triggerSuspiciousFingerprintChangeAlert(existing, null, correlationId);
            }
        }
        
        // Update existing fingerprint
        existing.setComponents(newComponents);
        existing.setLastSeenAt(LocalDateTime.now());
        existing.setFingerprintHash(fingerprintService.generateFingerprintHash(newComponents));
        fingerprintRepository.save(existing);
    }
    
    private boolean analyzeSuspiciousChanges(Set<String> changedComponents,
            Map<String, Object> oldComponents, Map<String, Object> newComponents) {
        
        // Critical components that rarely change
        Set<String> criticalComponents = Set.of("platform", "cpuClass", "hardwareConcurrency");
        
        // Check if critical components changed
        for (String critical : criticalComponents) {
            if (changedComponents.contains(critical)) {
                log.warn("Critical component changed: {}", critical);
                return true;
            }
        }
        
        // Check for too many changes at once
        if (changedComponents.size() >= 5) {
            log.warn("Too many components changed simultaneously: {}", changedComponents.size());
            return true;
        }
        
        return false;
    }
    
    private double performDeviceRiskAssessment(DeviceFingerprintEvent event, String correlationId) {
        DeviceRiskFactors riskFactors = new DeviceRiskFactors();
        
        // Base risk assessment
        double baseRisk = 20.0; // Start with base risk
        
        // Check device type risk
        baseRisk += calculateDeviceTypeRisk(event.getDeviceType());
        
        // Check fingerprint uniqueness
        baseRisk += calculateFingerprintUniquenessRisk(event.getFingerprintComponents());
        
        // Check historical behavior
        baseRisk += calculateHistoricalBehaviorRisk(event.getDeviceId());
        
        // Check geolocation consistency
        if (event.getGeoLocation() != null) {
            baseRisk += calculateGeolocationRisk(event.getDeviceId(), event.getGeoLocation());
        }
        
        // Check velocity patterns
        baseRisk += calculateVelocityRisk(event.getDeviceId());
        
        return Math.min(100.0, Math.max(0.0, baseRisk));
    }
    
    private double calculateDeviceTypeRisk(String deviceType) {
        switch (deviceType.toLowerCase()) {
            case "mobile": return 5.0;
            case "desktop": return 8.0;
            case "tablet": return 6.0;
            case "unknown": return 25.0;
            default: return 15.0;
        }
    }
    
    private double calculateFingerprintUniquenessRisk(Map<String, Object> components) {
        // More unique fingerprints are lower risk
        int uniquenessScore = fingerprintService.calculateUniquenessScore(components);
        
        if (uniquenessScore >= 90) return 5.0;  // Very unique, low risk
        if (uniquenessScore >= 70) return 10.0; // Moderately unique
        if (uniquenessScore >= 50) return 20.0; // Common fingerprint
        return 35.0; // Very common, high risk
    }
    
    private double calculateHistoricalBehaviorRisk(String deviceId) {
        Optional<DeviceRiskProfile> profile = riskProfileRepository.findByDeviceId(deviceId);
        
        if (profile.isEmpty()) {
            return 15.0; // New device, moderate risk
        }
        
        DeviceRiskProfile riskProfile = profile.get();
        
        // Factor in historical fraud attempts
        if (riskProfile.getFraudAttempts() > 0) {
            return Math.min(40.0, riskProfile.getFraudAttempts() * 10.0);
        }
        
        // Factor in successful transaction history
        if (riskProfile.getSuccessfulTransactions() > 10) {
            return Math.max(2.0, 15.0 - (riskProfile.getSuccessfulTransactions() * 0.5));
        }
        
        return 10.0; // Default moderate risk
    }
    
    private double calculateGeolocationRisk(String deviceId, Map<String, Object> geoLocation) {
        // Implementation for geolocation risk calculation
        return 5.0; // Placeholder
    }
    
    private double calculateVelocityRisk(String deviceId) {
        // Implementation for velocity risk calculation
        return 5.0; // Placeholder
    }
    
    /**
     * Additional helper methods and classes
     */
    private DeviceFingerprint getDeviceFingerprintById(String deviceId) {
        return fingerprintRepository.findByDeviceId(deviceId)
            .orElseThrow(() -> new DeviceFingerprintException(
                "Device fingerprint not found: " + deviceId));
    }
    
    private DeviceRiskProfile getOrCreateDeviceRiskProfile(String deviceId) {
        return riskProfileRepository.findByDeviceId(deviceId)
            .orElse(createNewDeviceRiskProfile(deviceId));
    }
    
    private DeviceRiskProfile createNewDeviceRiskProfile(String deviceId) {
        DeviceRiskProfile profile = DeviceRiskProfile.builder()
            .deviceId(deviceId)
            .riskScore(50.0) // Default risk score
            .successfulTransactions(0)
            .fraudAttempts(0)
            .lastTransactionAt(null)
            .createdAt(LocalDateTime.now())
            .build();
        
        return riskProfileRepository.save(profile);
    }
    
    private void validateDeviceFingerprintEvent(DeviceFingerprintEvent event) {
        if (event.getDeviceId() == null || event.getDeviceId().trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    // Placeholder implementations for referenced methods
    private void handleInvalidFingerprint(DeviceFingerprintEvent event, String correlationId) {
        // Implementation for handling invalid fingerprints
    }
    
    private void triggerHighRiskDeviceAlert(DeviceFingerprintEvent event, double riskScore, String correlationId) {
        // Implementation for high risk device alerts
    }
    
    private FingerprintChangeAnalysis analyzeFingerprintChangePattern(DeviceFingerprint fingerprint, 
            DeviceFingerprintEvent event, String correlationId) {
        // Implementation for fingerprint change analysis
        return new FingerprintChangeAnalysis();
    }
    
    private void updateDeviceFingerprint(DeviceFingerprint fingerprint, DeviceFingerprintEvent event, 
            FingerprintChangeAnalysis analysis) {
        // Implementation for updating device fingerprint
    }
    
    private double recalculateRiskScoreAfterChange(DeviceFingerprint fingerprint, 
            FingerprintChangeAnalysis analysis) {
        // Implementation for recalculating risk score
        return 50.0;
    }
    
    private void triggerSuspiciousFingerprintChangeAlert(DeviceFingerprint fingerprint, 
            FingerprintChangeAnalysis analysis, String correlationId) {
        // Implementation for suspicious change alerts
    }
    
    private void handleAnomalousBehavior(DeviceFingerprintEvent event, BehaviorAnalysisResult result, 
            String correlationId) {
        // Implementation for handling anomalous behavior
    }
    
    private VelocityAnalysisResult performVelocityAnalysis(DeviceFingerprintEvent event, String correlationId) {
        // Implementation for velocity analysis
        return new VelocityAnalysisResult();
    }
    
    private void handleVelocityViolation(DeviceFingerprintEvent event, VelocityAnalysisResult result, 
            String correlationId) {
        // Implementation for velocity violation handling
    }
    
    private double calculateCombinedRiskScore(DeviceRiskProfile profile, BehaviorAnalysisResult behavior, 
            VelocityAnalysisResult velocity) {
        // Implementation for combined risk score calculation
        return 50.0;
    }
    
    private void publishRiskScoreUpdate(String deviceId, double riskScore, String correlationId) {
        RiskScoreUpdatedEvent updateEvent = RiskScoreUpdatedEvent.builder()
            .entityId(deviceId)
            .entityType("DEVICE")
            .newRiskScore(riskScore)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("risk-score-updated-events", updateEvent);
    }
    
    private DeviceRiskAssessment performComprehensiveRiskAssessment(DeviceFingerprintEvent event, 
            String correlationId) {
        // Implementation for comprehensive risk assessment
        return new DeviceRiskAssessment();
    }
    
    private double combineRiskScores(double traditionalScore, double mlScore) {
        // Weighted combination of traditional and ML scores
        return (traditionalScore * 0.6) + (mlScore * 100 * 0.4);
    }
    
    private FraudRiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= HIGH_RISK_THRESHOLD) return FraudRiskLevel.HIGH;
        if (riskScore >= MEDIUM_RISK_THRESHOLD) return FraudRiskLevel.MEDIUM;
        return FraudRiskLevel.LOW;
    }
    
    private void handleHighRiskDevice(DeviceFingerprintEvent event, double riskScore, 
            DeviceRiskAssessment assessment, String correlationId) {
        // Implementation for high risk device handling
    }
    
    private void handleMediumRiskDevice(DeviceFingerprintEvent event, double riskScore, 
            DeviceRiskAssessment assessment, String correlationId) {
        // Implementation for medium risk device handling
    }
    
    private void handleLowRiskDevice(DeviceFingerprintEvent event, double riskScore, 
            DeviceRiskAssessment assessment, String correlationId) {
        // Implementation for low risk device handling
    }
    
    private void analyzeUserDeviceCorrelation(String userId, String deviceId, String correlationId) {
        // Implementation for user device correlation analysis
    }
    
    private void addToDeviceMonitoring(String deviceId, String reason, String correlationId) {
        // Implementation for adding device to monitoring list
    }
    
    private void publishDeviceBlockingEvent(String deviceId, String reason, String correlationId) {
        // Implementation for publishing device blocking event
    }
    
    private void analyzeBlacklistImpact(String deviceId, String correlationId) {
        // Implementation for analyzing blacklist impact
    }
    
    private void removeFromDeviceMonitoring(String deviceId, String correlationId) {
        // Implementation for removing device from monitoring
    }
    
    private void publishFraudAlert(String deviceId, String alertType, String reason, 
            double riskScore, String correlationId) {
        
        FraudAlertEvent alertEvent = FraudAlertEvent.builder()
            .entityId(deviceId)
            .entityType("DEVICE")
            .alertType(alertType)
            .reason(reason)
            .riskScore(riskScore)
            .severity(riskScore >= HIGH_RISK_THRESHOLD ? "HIGH" : 
                     riskScore >= MEDIUM_RISK_THRESHOLD ? "MEDIUM" : "LOW")
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("fraud-alert-events", alertEvent);
    }
    
    private void handleDeviceFingerprintEventError(DeviceFingerprintEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("device-fingerprint-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Device Fingerprint Event Processing Failed",
            String.format("Failed to process device fingerprint event: %s", error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementDeviceFingerprintEventError(event.getEventType());
    }
    
    // Inner classes for analysis results
    public static class FingerprintChangeAnalysis {
        private boolean suspiciousChange = false;
        private int changeFrequency = 0;
        private String suspicionLevel = "LOW";
        
        public boolean isSuspiciousChange() { return suspiciousChange; }
        public int getChangeFrequency() { return changeFrequency; }
        public String getSuspicionLevel() { return suspicionLevel; }
    }
    
    public static class BehaviorAnalysisResult {
        private boolean anomalous = false;
        private double anomalyScore = 0.0;
        
        public boolean isAnomalous() { return anomalous; }
        public double getAnomalyScore() { return anomalyScore; }
    }
    
    public static class VelocityAnalysisResult {
        private boolean velocityExceeded = false;
        private int transactionCount = 0;
        
        public boolean isVelocityExceeded() { return velocityExceeded; }
        public int getTransactionCount() { return transactionCount; }
    }
    
    public static class DeviceRiskAssessment {
        private double riskScore = 0.0;
        
        public double getRiskScore() { return riskScore; }
    }
    
    public static class MLFraudPrediction {
        private double fraudProbability = 0.0;
        private double confidenceScore = 0.0;
        
        public double getFraudProbability() { return fraudProbability; }
        public double getConfidenceScore() { return confidenceScore; }
    }
    
    public static class DeviceRiskFactors {
        // Implementation for device risk factors
    }
}