package com.waqiti.atm.security;

import com.waqiti.common.security.EncryptionService;
import com.waqiti.common.events.SecurityEventPublisher;
import com.waqiti.common.events.SecurityEvent;
import com.waqiti.common.fraud.FraudServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ATM Biometric Authentication Service
 * 
 * Provides comprehensive biometric authentication for ATM operations with:
 * - Fingerprint, facial recognition, iris scan, and palm vein authentication
 * - Multi-modal biometric fusion for high-value transactions
 * - Liveness detection to prevent spoofing attacks
 * - Behavioral biometrics (typing patterns, touch pressure)
 * - Location-based authentication requirements
 * - Time-based authentication escalation
 * - Anti-tampering and anti-skimming detection
 * - Emergency duress detection with silent alarms
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtmBiometricAuthService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final EncryptionService encryptionService;
    private final SecurityEventPublisher securityEventPublisher;
    private final FraudServiceHelper fraudServiceHelper;
    
    @Value("${atm.biometric.high-value-threshold:500}")
    private BigDecimal highValueThreshold;
    
    @Value("${atm.biometric.very-high-value-threshold:2000}")
    private BigDecimal veryHighValueThreshold;
    
    @Value("${atm.biometric.max-value-threshold:5000}")
    private BigDecimal maxValueThreshold;
    
    @Value("${atm.biometric.session-duration-minutes:5}")
    private int sessionDurationMinutes;
    
    @Value("${atm.biometric.max-attempts:3}")
    private int maxAttempts;
    
    @Value("${atm.biometric.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;
    
    @Value("${atm.biometric.liveness-check-enabled:true}")
    private boolean livenessCheckEnabled;
    
    @Value("${atm.biometric.match-threshold:0.95}")
    private double matchThreshold;
    
    @Value("${atm.biometric.fusion-threshold:0.98}")
    private double fusionThreshold;
    
    @Value("${atm.biometric.duress-detection-enabled:true}")
    private boolean duressDetectionEnabled;
    
    private static final String BIOMETRIC_SESSION_PREFIX = "atm:bio:session:";
    private static final String BIOMETRIC_TEMPLATE_PREFIX = "atm:bio:template:";
    private static final String BIOMETRIC_LOCKOUT_PREFIX = "atm:bio:lockout:";
    private static final String ATM_LOCATION_PREFIX = "atm:location:";
    private static final String DURESS_SIGNAL_PREFIX = "atm:duress:";
    private static final String BEHAVIORAL_PROFILE_PREFIX = "atm:behavior:";
    private static final String ANTI_TAMPER_PREFIX = "atm:tamper:";
    
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, AtmUsagePattern> usagePatterns = new ConcurrentHashMap<>();
    
    /**
     * Initiate biometric authentication for ATM transaction
     */
    public BiometricAuthRequirement determineBiometricRequirement(String atmId, String cardNumber, 
                                                                 BigDecimal amount, TransactionType type) {
        log.info("Determining biometric requirement for ATM {} card {} amount {} type {}", 
            atmId, maskCardNumber(cardNumber), amount, type);
        
        try {
            // Check if user is locked out
            if (isUserLockedOut(cardNumber)) {
                return BiometricAuthRequirement.builder()
                    .required(true)
                    .locked(true)
                    .message("Account temporarily locked due to multiple failed attempts")
                    .build();
            }
            
            // Check ATM tampering status
            if (isAtmTampered(atmId)) {
                log.error("ATM {} flagged as tampered - blocking all transactions", atmId);
                triggerSecurityAlert(atmId, "ATM_TAMPERED", "High");
                return BiometricAuthRequirement.builder()
                    .required(true)
                    .blocked(true)
                    .message("ATM temporarily unavailable for security reasons")
                    .build();
            }
            
            // Determine required biometric methods based on risk
            BiometricLevel level = calculateBiometricLevel(amount, type);
            List<BiometricMethod> requiredMethods = determineRequiredMethods(level, atmId, cardNumber);
            
            // Check for unusual patterns
            if (detectUnusualUsage(cardNumber, atmId, amount, type)) {
                level = BiometricLevel.MAXIMUM;
                requiredMethods = Arrays.asList(
                    BiometricMethod.FINGERPRINT, 
                    BiometricMethod.FACIAL_RECOGNITION,
                    BiometricMethod.BEHAVIORAL
                );
                log.warn("Unusual usage pattern detected - requiring maximum authentication");
            }
            
            // Check time-based restrictions
            TimeRestriction timeRestriction = checkTimeRestrictions(type, amount);
            if (timeRestriction == TimeRestriction.BLOCKED) {
                return BiometricAuthRequirement.builder()
                    .required(true)
                    .blocked(true)
                    .message("Transaction not allowed at this time")
                    .build();
            } else if (timeRestriction == TimeRestriction.ELEVATED) {
                requiredMethods.add(BiometricMethod.IRIS_SCAN);
            }
            
            return BiometricAuthRequirement.builder()
                .required(!requiredMethods.isEmpty())
                .level(level)
                .requiredMethods(requiredMethods)
                .livenessCheckRequired(livenessCheckEnabled && level.ordinal() >= BiometricLevel.HIGH.ordinal())
                .sessionDuration(sessionDurationMinutes)
                .message(buildAuthMessage(level, requiredMethods))
                .build();
                
        } catch (Exception e) {
            log.error("Error determining biometric requirement", e);
            // Fail safe - require maximum security
            return BiometricAuthRequirement.builder()
                .required(true)
                .level(BiometricLevel.MAXIMUM)
                .requiredMethods(Arrays.asList(
                    BiometricMethod.FINGERPRINT,
                    BiometricMethod.FACIAL_RECOGNITION,
                    BiometricMethod.IRIS_SCAN
                ))
                .livenessCheckRequired(true)
                .message("Security verification required")
                .build();
        }
    }
    
    /**
     * Capture and verify biometric data
     */
    public BiometricVerificationResult verifyBiometric(String sessionId, String cardNumber,
                                                      Map<BiometricMethod, BiometricData> biometricData) {
        log.info("Verifying biometric data for session {} card {}", sessionId, maskCardNumber(cardNumber));
        
        try {
            // Retrieve session data
            BiometricSession session = getSession(sessionId);
            if (session == null) {
                return BiometricVerificationResult.builder()
                    .success(false)
                    .errorCode("SESSION_NOT_FOUND")
                    .errorMessage("Authentication session not found or expired")
                    .build();
            }
            
            // Check session expiry
            if (session.isExpired()) {
                deleteSession(sessionId);
                return BiometricVerificationResult.builder()
                    .success(false)
                    .errorCode("SESSION_EXPIRED")
                    .errorMessage("Authentication session has expired")
                    .build();
            }
            
            // Check for duress signals
            if (duressDetectionEnabled && detectDuressSignal(biometricData)) {
                triggerSilentAlarm(session.getAtmId(), cardNumber, "DURESS_DETECTED");
                // Still allow transaction but alert authorities
                log.error("DURESS SIGNAL DETECTED - Silent alarm triggered for ATM {} card {}", 
                    session.getAtmId(), maskCardNumber(cardNumber));
            }
            
            // Verify each biometric method
            Map<BiometricMethod, VerificationScore> scores = new HashMap<>();
            boolean allVerified = true;
            List<String> failedMethods = new ArrayList<>();
            
            for (BiometricMethod method : session.getRequiredMethods()) {
                BiometricData data = biometricData.get(method);
                if (data == null) {
                    allVerified = false;
                    failedMethods.add(method.name());
                    continue;
                }
                
                // Perform liveness check if required
                if (session.isLivenessCheckRequired() && !performLivenessCheck(method, data)) {
                    log.warn("Liveness check failed for method {}", method);
                    allVerified = false;
                    failedMethods.add(method.name() + "_LIVENESS");
                    continue;
                }
                
                // Verify biometric
                VerificationScore score = verifyBiometricMethod(cardNumber, method, data);
                scores.put(method, score);
                
                if (!score.isMatch()) {
                    allVerified = false;
                    failedMethods.add(method.name());
                }
            }
            
            // Multi-modal fusion for high-security transactions
            if (session.getLevel() == BiometricLevel.MAXIMUM && scores.size() > 1) {
                double fusionScore = calculateFusionScore(scores);
                if (fusionScore < fusionThreshold) {
                    allVerified = false;
                    failedMethods.add("FUSION_SCORE_LOW");
                }
            }
            
            if (allVerified) {
                // Success - create authenticated session
                session.setAuthenticated(true);
                session.setAuthenticatedAt(LocalDateTime.now());
                updateSession(sessionId, session);
                
                // Record successful authentication
                recordUsagePattern(cardNumber, session.getAtmId(), session.getTransactionType());
                
                // Generate transaction token
                String transactionToken = generateTransactionToken(sessionId, cardNumber);
                
                SecurityEvent successEvent = SecurityEvent.builder()
                    .eventType("ATM_BIOMETRIC_AUTH_SUCCESS")
                    .userId(maskCardNumber(cardNumber))
                    .details(String.format("{\"atmId\":\"%s\",\"level\":\"%s\",\"methods\":%s}",
                        session.getAtmId(), session.getLevel(), session.getRequiredMethods()))
                    .timestamp(System.currentTimeMillis())
                    .build();
                securityEventPublisher.publishSecurityEvent(successEvent);
                
                return BiometricVerificationResult.builder()
                    .success(true)
                    .sessionToken(transactionToken)
                    .validUntil(LocalDateTime.now().plusMinutes(sessionDurationMinutes))
                    .confidenceScore(calculateOverallConfidence(scores))
                    .build();
                    
            } else {
                // Failed - increment attempts
                session.incrementAttempts();
                
                if (session.getAttempts() >= maxAttempts) {
                    // Lock account
                    lockUser(cardNumber);
                    deleteSession(sessionId);
                    
                    SecurityEvent lockEvent = SecurityEvent.builder()
                        .eventType("ATM_BIOMETRIC_LOCKOUT")
                        .userId(maskCardNumber(cardNumber))
                        .details(String.format("{\"atmId\":\"%s\",\"reason\":\"MAX_ATTEMPTS\"}",
                            session.getAtmId()))
                        .timestamp(System.currentTimeMillis())
                        .build();
                    securityEventPublisher.publishSecurityEvent(lockEvent);
                    
                    return BiometricVerificationResult.builder()
                        .success(false)
                        .errorCode("ACCOUNT_LOCKED")
                        .errorMessage("Maximum attempts exceeded. Account locked.")
                        .accountLocked(true)
                        .build();
                        
                } else {
                    updateSession(sessionId, session);
                    
                    return BiometricVerificationResult.builder()
                        .success(false)
                        .errorCode("VERIFICATION_FAILED")
                        .errorMessage("Biometric verification failed: " + String.join(", ", failedMethods))
                        .attemptsRemaining(maxAttempts - session.getAttempts())
                        .failedMethods(failedMethods)
                        .build();
                }
            }
            
        } catch (Exception e) {
            log.error("Error verifying biometric", e);
            return BiometricVerificationResult.builder()
                .success(false)
                .errorCode("VERIFICATION_ERROR")
                .errorMessage("An error occurred during verification")
                .build();
        }
    }
    
    /**
     * Create biometric authentication session
     */
    public BiometricSession createSession(String atmId, String cardNumber, BigDecimal amount,
                                         TransactionType type, BiometricAuthRequirement requirement) {
        log.info("Creating biometric session for ATM {} card {}", atmId, maskCardNumber(cardNumber));
        
        String sessionId = generateSessionId();
        
        BiometricSession session = BiometricSession.builder()
            .sessionId(sessionId)
            .atmId(atmId)
            .cardNumber(encryptionService.encrypt(cardNumber))
            .amount(amount)
            .transactionType(type)
            .level(requirement.getLevel())
            .requiredMethods(requirement.getRequiredMethods())
            .livenessCheckRequired(requirement.isLivenessCheckRequired())
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(sessionDurationMinutes))
            .attempts(0)
            .authenticated(false)
            .build();
        
        String key = BIOMETRIC_SESSION_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, session, Duration.ofMinutes(sessionDurationMinutes));
        
        return session;
    }
    
    /**
     * Enroll biometric template for user
     */
    public BiometricEnrollmentResult enrollBiometric(String cardNumber, BiometricMethod method,
                                                    BiometricData biometricData, String enrollmentLocation) {
        log.info("Enrolling biometric {} for card {}", method, maskCardNumber(cardNumber));
        
        try {
            // Verify biometric quality
            BiometricQuality quality = assessBiometricQuality(method, biometricData);
            if (quality.getScore() < 0.8) {
                return BiometricEnrollmentResult.builder()
                    .success(false)
                    .errorMessage("Biometric quality insufficient. Please try again.")
                    .qualityScore(quality.getScore())
                    .qualityIssues(quality.getIssues())
                    .build();
            }
            
            // Generate and store template
            BiometricTemplate template = generateTemplate(method, biometricData);
            template.setEnrolledAt(LocalDateTime.now());
            template.setEnrollmentLocation(enrollmentLocation);
            template.setCardNumber(encryptionService.encrypt(cardNumber));
            
            String key = BIOMETRIC_TEMPLATE_PREFIX + cardNumber + ":" + method;
            redisTemplate.opsForValue().set(key, template);
            
            // Log enrollment event
            SecurityEvent event = SecurityEvent.builder()
                .eventType("BIOMETRIC_ENROLLED")
                .userId(maskCardNumber(cardNumber))
                .details(String.format("{\"method\":\"%s\",\"location\":\"%s\",\"quality\":%.2f}",
                    method, enrollmentLocation, quality.getScore()))
                .timestamp(System.currentTimeMillis())
                .build();
            securityEventPublisher.publishSecurityEvent(event);
            
            return BiometricEnrollmentResult.builder()
                .success(true)
                .templateId(template.getTemplateId())
                .method(method)
                .enrolledAt(template.getEnrolledAt())
                .message("Biometric successfully enrolled")
                .build();
                
        } catch (Exception e) {
            log.error("Error enrolling biometric", e);
            return BiometricEnrollmentResult.builder()
                .success(false)
                .errorMessage("Failed to enroll biometric")
                .build();
        }
    }
    
    // Helper methods
    
    private BiometricLevel calculateBiometricLevel(BigDecimal amount, TransactionType type) {
        // High-risk transaction types always require biometric
        if (type == TransactionType.CASH_ADVANCE || type == TransactionType.INTERNATIONAL) {
            return BiometricLevel.HIGH;
        }
        
        if (amount.compareTo(maxValueThreshold) >= 0) {
            return BiometricLevel.MAXIMUM;
        } else if (amount.compareTo(veryHighValueThreshold) >= 0) {
            return BiometricLevel.HIGH;
        } else if (amount.compareTo(highValueThreshold) >= 0) {
            return BiometricLevel.STANDARD;
        } else {
            return BiometricLevel.BASIC;
        }
    }
    
    private List<BiometricMethod> determineRequiredMethods(BiometricLevel level, String atmId, String cardNumber) {
        List<BiometricMethod> methods = new ArrayList<>();
        
        switch (level) {
            case BASIC:
                methods.add(BiometricMethod.FINGERPRINT);
                break;
            case STANDARD:
                methods.add(BiometricMethod.FINGERPRINT);
                methods.add(BiometricMethod.PIN_PLUS_FINGERPRINT);
                break;
            case HIGH:
                methods.add(BiometricMethod.FINGERPRINT);
                methods.add(BiometricMethod.FACIAL_RECOGNITION);
                break;
            case MAXIMUM:
                methods.add(BiometricMethod.FINGERPRINT);
                methods.add(BiometricMethod.FACIAL_RECOGNITION);
                methods.add(BiometricMethod.IRIS_SCAN);
                break;
        }
        
        // Add behavioral biometrics for repeat users
        if (hasEstablishedPattern(cardNumber)) {
            methods.add(BiometricMethod.BEHAVIORAL);
        }
        
        return methods;
    }
    
    private boolean performLivenessCheck(BiometricMethod method, BiometricData data) {
        switch (method) {
            case FACIAL_RECOGNITION:
                return performFacialLivenessCheck(data);
            case FINGERPRINT:
                return performFingerprintLivenessCheck(data);
            case IRIS_SCAN:
                return performIrisLivenessCheck(data);
            default:
                return true;
        }
    }
    
    private boolean performFacialLivenessCheck(BiometricData data) {
        // Check for 3D depth, eye movement, micro-expressions
        if (data.getMetadata() == null) return false;
        
        boolean hasDepth = Boolean.TRUE.equals(data.getMetadata().get("has3DDepth"));
        boolean hasEyeMovement = Boolean.TRUE.equals(data.getMetadata().get("eyeMovementDetected"));
        boolean hasMicroExpressions = Boolean.TRUE.equals(data.getMetadata().get("microExpressionsDetected"));
        
        return hasDepth && (hasEyeMovement || hasMicroExpressions);
    }
    
    private boolean performFingerprintLivenessCheck(BiometricData data) {
        // Check for pulse, temperature, perspiration
        if (data.getMetadata() == null) return false;
        
        boolean hasPulse = Boolean.TRUE.equals(data.getMetadata().get("pulseDetected"));
        boolean hasTemperature = Boolean.TRUE.equals(data.getMetadata().get("temperatureNormal"));
        
        return hasPulse && hasTemperature;
    }
    
    private boolean performIrisLivenessCheck(BiometricData data) {
        // Check for pupil dilation, eye movement
        if (data.getMetadata() == null) return false;
        
        boolean hasPupilResponse = Boolean.TRUE.equals(data.getMetadata().get("pupilResponseDetected"));
        boolean hasIrisTexture = Boolean.TRUE.equals(data.getMetadata().get("irisTextureValid"));
        
        return hasPupilResponse && hasIrisTexture;
    }
    
    private VerificationScore verifyBiometricMethod(String cardNumber, BiometricMethod method, 
                                                   BiometricData data) {
        // Retrieve stored template
        String key = BIOMETRIC_TEMPLATE_PREFIX + cardNumber + ":" + method;
        BiometricTemplate template = (BiometricTemplate) redisTemplate.opsForValue().get(key);
        
        if (template == null) {
            return VerificationScore.builder()
                .match(false)
                .score(0.0)
                .reason("No enrolled template")
                .build();
        }
        
        // Perform matching based on method
        double score = calculateMatchScore(method, template, data);
        
        return VerificationScore.builder()
            .match(score >= matchThreshold)
            .score(score)
            .confidence(calculateConfidence(score, method))
            .build();
    }
    
    private double calculateMatchScore(BiometricMethod method, BiometricTemplate template, 
                                      BiometricData data) {
        // Simulate biometric matching algorithm
        // In production, this would use actual biometric matching libraries
        
        switch (method) {
            case FINGERPRINT:
                return matchFingerprint(template.getData(), data.getData());
            case FACIAL_RECOGNITION:
                return matchFacial(template.getData(), data.getData());
            case IRIS_SCAN:
                return matchIris(template.getData(), data.getData());
            case PALM_VEIN:
                return matchPalmVein(template.getData(), data.getData());
            case BEHAVIORAL:
                return matchBehavioral(template.getData(), data.getData());
            default:
                return 0.0;
        }
    }
    
    private double matchFingerprint(byte[] template, byte[] sample) {
        // Simulate minutiae-based fingerprint matching
        return 0.96 + (secureRandom.nextDouble() * 0.04);
    }
    
    private double matchFacial(byte[] template, byte[] sample) {
        // Simulate facial recognition matching
        return 0.94 + (secureRandom.nextDouble() * 0.06);
    }
    
    private double matchIris(byte[] template, byte[] sample) {
        // Simulate iris matching (typically very accurate)
        return 0.98 + (secureRandom.nextDouble() * 0.02);
    }
    
    private double matchPalmVein(byte[] template, byte[] sample) {
        // Simulate palm vein pattern matching
        return 0.97 + (secureRandom.nextDouble() * 0.03);
    }
    
    private double matchBehavioral(byte[] template, byte[] sample) {
        // Simulate behavioral biometric matching
        return 0.92 + (secureRandom.nextDouble() * 0.08);
    }
    
    private double calculateFusionScore(Map<BiometricMethod, VerificationScore> scores) {
        // Weighted fusion of multiple biometric scores
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        
        Map<BiometricMethod, Double> weights = Map.of(
            BiometricMethod.IRIS_SCAN, 0.35,
            BiometricMethod.FINGERPRINT, 0.30,
            BiometricMethod.FACIAL_RECOGNITION, 0.25,
            BiometricMethod.PALM_VEIN, 0.10
        );
        
        for (Map.Entry<BiometricMethod, VerificationScore> entry : scores.entrySet()) {
            double weight = weights.getOrDefault(entry.getKey(), 0.1);
            weightedSum += entry.getValue().getScore() * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }
    
    private boolean detectDuressSignal(Map<BiometricMethod, BiometricData> biometricData) {
        // Check for duress patterns in biometric data
        for (Map.Entry<BiometricMethod, BiometricData> entry : biometricData.entrySet()) {
            BiometricData data = entry.getValue();
            if (data.getMetadata() != null) {
                // Check for duress finger (specific finger used for duress)
                if (Boolean.TRUE.equals(data.getMetadata().get("duressFingerUsed"))) {
                    return true;
                }
                
                // Check for stress indicators in facial/voice
                if (Boolean.TRUE.equals(data.getMetadata().get("highStressDetected"))) {
                    return true;
                }
                
                // Check for coded blink pattern
                if (Boolean.TRUE.equals(data.getMetadata().get("duressBlinkPattern"))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean detectUnusualUsage(String cardNumber, String atmId, BigDecimal amount, 
                                      TransactionType type) {
        AtmUsagePattern pattern = usagePatterns.get(cardNumber);
        if (pattern == null) {
            return false; // No pattern established yet
        }
        
        // Check for unusual location
        if (!pattern.getCommonAtms().contains(atmId) && pattern.getTransactionCount() > 10) {
            return true;
        }
        
        // Check for unusual amount
        if (amount.compareTo(pattern.getAverageAmount().multiply(new BigDecimal("3"))) > 0) {
            return true;
        }
        
        // Check for unusual time
        LocalTime now = LocalTime.now();
        if (!pattern.isWithinUsualHours(now)) {
            return true;
        }
        
        // Check for velocity (too many transactions in short time)
        if (pattern.getRecentTransactionCount(Duration.ofHours(1)) > 3) {
            return true;
        }
        
        return false;
    }
    
    private TimeRestriction checkTimeRestrictions(TransactionType type, BigDecimal amount) {
        LocalTime now = LocalTime.now();
        
        // Block high-value transactions during night hours
        if (now.isAfter(LocalTime.of(23, 0)) || now.isBefore(LocalTime.of(6, 0))) {
            if (amount.compareTo(veryHighValueThreshold) > 0) {
                return TimeRestriction.BLOCKED;
            } else if (amount.compareTo(highValueThreshold) > 0) {
                return TimeRestriction.ELEVATED;
            }
        }
        
        // Elevate security during high-risk hours (late night)
        if (now.isAfter(LocalTime.of(22, 0)) || now.isBefore(LocalTime.of(7, 0))) {
            return TimeRestriction.ELEVATED;
        }
        
        return TimeRestriction.NORMAL;
    }
    
    private BiometricQuality assessBiometricQuality(BiometricMethod method, BiometricData data) {
        List<String> issues = new ArrayList<>();
        double score = 1.0;
        
        // Check data size
        if (data.getData() == null || data.getData().length < 1024) {
            issues.add("Insufficient data");
            score -= 0.3;
        }
        
        // Check metadata quality indicators
        if (data.getMetadata() != null) {
            Integer quality = (Integer) data.getMetadata().get("quality");
            if (quality != null && quality < 80) {
                issues.add("Low quality capture");
                score -= 0.2;
            }
            
            Boolean blurred = (Boolean) data.getMetadata().get("blurred");
            if (Boolean.TRUE.equals(blurred)) {
                issues.add("Image blurred");
                score -= 0.2;
            }
        }
        
        return BiometricQuality.builder()
            .score(Math.max(0, score))
            .issues(issues)
            .acceptable(score >= 0.8)
            .build();
    }
    
    private BiometricTemplate generateTemplate(BiometricMethod method, BiometricData data) {
        // Generate unique template ID
        String templateId = UUID.randomUUID().toString();
        
        // Process biometric data into template
        byte[] processedTemplate = processRawBiometric(method, data.getData());
        
        return BiometricTemplate.builder()
            .templateId(templateId)
            .method(method)
            .data(processedTemplate)
            .version("1.0")
            .algorithm(getAlgorithmForMethod(method))
            .build();
    }
    
    private byte[] processRawBiometric(BiometricMethod method, byte[] rawData) {
        // In production, this would use actual biometric processing libraries
        // For now, simulate processing
        return encryptionService.encrypt(Base64.getEncoder().encodeToString(rawData)).getBytes();
    }
    
    private String getAlgorithmForMethod(BiometricMethod method) {
        switch (method) {
            case FINGERPRINT:
                return "MINUTIAE_BASED_V2";
            case FACIAL_RECOGNITION:
                return "DEEPFACE_CNN_V3";
            case IRIS_SCAN:
                return "IRIS_CODE_2048";
            case PALM_VEIN:
                return "VEIN_PATTERN_V1";
            default:
                return "GENERIC_V1";
        }
    }
    
    private void triggerSecurityAlert(String atmId, String alertType, String severity) {
        SecurityEvent alert = SecurityEvent.builder()
            .eventType("ATM_SECURITY_ALERT")
            .userId("ATM_" + atmId)
            .details(String.format("{\"type\":\"%s\",\"severity\":\"%s\",\"atmId\":\"%s\"}",
                alertType, severity, atmId))
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(alert);
    }
    
    private void triggerSilentAlarm(String atmId, String cardNumber, String reason) {
        // Store duress signal
        String key = DURESS_SIGNAL_PREFIX + atmId;
        DuressSignal signal = DuressSignal.builder()
            .atmId(atmId)
            .cardNumber(encryptionService.encrypt(cardNumber))
            .reason(reason)
            .triggeredAt(LocalDateTime.now())
            .build();
        
        redisTemplate.opsForValue().set(key, signal, Duration.ofHours(24));
        
        // Trigger security event
        SecurityEvent alert = SecurityEvent.builder()
            .eventType("ATM_DURESS_ALARM")
            .userId(maskCardNumber(cardNumber))
            .details(String.format("{\"atmId\":\"%s\",\"reason\":\"%s\",\"priority\":\"CRITICAL\"}",
                atmId, reason))
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(alert);
    }
    
    private boolean isAtmTampered(String atmId) {
        String key = ANTI_TAMPER_PREFIX + atmId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    private boolean isUserLockedOut(String cardNumber) {
        String key = BIOMETRIC_LOCKOUT_PREFIX + cardNumber;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    private void lockUser(String cardNumber) {
        String key = BIOMETRIC_LOCKOUT_PREFIX + cardNumber;
        redisTemplate.opsForValue().set(key, true, Duration.ofMinutes(lockoutDurationMinutes));
    }
    
    private BiometricSession getSession(String sessionId) {
        String key = BIOMETRIC_SESSION_PREFIX + sessionId;
        return (BiometricSession) redisTemplate.opsForValue().get(key);
    }
    
    private void updateSession(String sessionId, BiometricSession session) {
        String key = BIOMETRIC_SESSION_PREFIX + sessionId;
        Duration ttl = Duration.between(LocalDateTime.now(), session.getExpiresAt());
        if (ttl.isPositive()) {
            redisTemplate.opsForValue().set(key, session, ttl);
        }
    }
    
    private void deleteSession(String sessionId) {
        String key = BIOMETRIC_SESSION_PREFIX + sessionId;
        redisTemplate.delete(key);
    }
    
    private String generateSessionId() {
        return "ATM_BIO_" + UUID.randomUUID().toString();
    }
    
    private String generateTransactionToken(String sessionId, String cardNumber) {
        return "TXN_" + sessionId + "_" + System.currentTimeMillis();
    }
    
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
    
    private double calculateConfidence(double score, BiometricMethod method) {
        // Different methods have different confidence levels
        Map<BiometricMethod, Double> baseConfidence = Map.of(
            BiometricMethod.IRIS_SCAN, 0.99,
            BiometricMethod.PALM_VEIN, 0.98,
            BiometricMethod.FINGERPRINT, 0.96,
            BiometricMethod.FACIAL_RECOGNITION, 0.94,
            BiometricMethod.BEHAVIORAL, 0.90
        );
        
        double base = baseConfidence.getOrDefault(method, 0.95);
        return Math.min(1.0, score * base);
    }
    
    private double calculateOverallConfidence(Map<BiometricMethod, VerificationScore> scores) {
        if (scores.isEmpty()) return 0.0;
        
        double totalConfidence = scores.values().stream()
            .mapToDouble(VerificationScore::getConfidence)
            .sum();
        
        return totalConfidence / scores.size();
    }
    
    private void recordUsagePattern(String cardNumber, String atmId, TransactionType type) {
        AtmUsagePattern pattern = usagePatterns.computeIfAbsent(cardNumber, 
            k -> new AtmUsagePattern());
        pattern.recordTransaction(atmId, type);
    }
    
    private boolean hasEstablishedPattern(String cardNumber) {
        AtmUsagePattern pattern = usagePatterns.get(cardNumber);
        return pattern != null && pattern.getTransactionCount() >= 5;
    }
    
    private String buildAuthMessage(BiometricLevel level, List<BiometricMethod> methods) {
        String methodsStr = methods.stream()
            .map(m -> m.getDisplayName())
            .collect(Collectors.joining(", "));
        
        return String.format("Please provide %s biometric authentication using: %s", 
            level.name().toLowerCase(), methodsStr);
    }
    
    // Data classes
    
    @lombok.Data
    @lombok.Builder
    public static class BiometricAuthRequirement {
        private boolean required;
        private boolean locked;
        private boolean blocked;
        private BiometricLevel level;
        private List<BiometricMethod> requiredMethods;
        private boolean livenessCheckRequired;
        private int sessionDuration;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BiometricVerificationResult {
        private boolean success;
        private String sessionToken;
        private LocalDateTime validUntil;
        private double confidenceScore;
        private String errorCode;
        private String errorMessage;
        private int attemptsRemaining;
        private List<String> failedMethods;
        private boolean accountLocked;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BiometricSession {
        private String sessionId;
        private String atmId;
        private String cardNumber; // encrypted
        private BigDecimal amount;
        private TransactionType transactionType;
        private BiometricLevel level;
        private List<BiometricMethod> requiredMethods;
        private boolean livenessCheckRequired;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private LocalDateTime authenticatedAt;
        private int attempts;
        private boolean authenticated;
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
        
        public void incrementAttempts() {
            this.attempts++;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BiometricData {
        private BiometricMethod method;
        private byte[] data;
        private Map<String, Object> metadata;
        private LocalDateTime capturedAt;
        private String deviceId;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BiometricTemplate {
        private String templateId;
        private BiometricMethod method;
        private byte[] data;
        private String version;
        private String algorithm;
        private LocalDateTime enrolledAt;
        private String enrollmentLocation;
        private String cardNumber; // encrypted
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BiometricEnrollmentResult {
        private boolean success;
        private String templateId;
        private BiometricMethod method;
        private LocalDateTime enrolledAt;
        private String message;
        private String errorMessage;
        private double qualityScore;
        private List<String> qualityIssues;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class VerificationScore {
        private boolean match;
        private double score;
        private double confidence;
        private String reason;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BiometricQuality {
        private double score;
        private List<String> issues;
        private boolean acceptable;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DuressSignal {
        private String atmId;
        private String cardNumber; // encrypted
        private String reason;
        private LocalDateTime triggeredAt;
    }
    
    @lombok.Data
    public static class AtmUsagePattern {
        private Set<String> commonAtms = new HashSet<>();
        private BigDecimal averageAmount = BigDecimal.ZERO;
        private LocalTime earliestUsage = LocalTime.MAX;
        private LocalTime latestUsage = LocalTime.MIN;
        private int transactionCount = 0;
        private List<LocalDateTime> recentTransactions = new ArrayList<>();
        
        public void recordTransaction(String atmId, TransactionType type) {
            commonAtms.add(atmId);
            transactionCount++;
            recentTransactions.add(LocalDateTime.now());
            
            // Keep only last 100 transactions
            if (recentTransactions.size() > 100) {
                recentTransactions.remove(0);
            }
            
            LocalTime now = LocalTime.now();
            if (now.isBefore(earliestUsage)) {
                earliestUsage = now;
            }
            if (now.isAfter(latestUsage)) {
                latestUsage = now;
            }
        }
        
        public boolean isWithinUsualHours(LocalTime time) {
            if (transactionCount < 5) {
                return true; // Not enough data
            }
            
            // Allow 2 hours buffer
            LocalTime bufferStart = earliestUsage.minusHours(2);
            LocalTime bufferEnd = latestUsage.plusHours(2);
            
            return time.isAfter(bufferStart) && time.isBefore(bufferEnd);
        }
        
        public int getRecentTransactionCount(Duration duration) {
            LocalDateTime cutoff = LocalDateTime.now().minus(duration);
            return (int) recentTransactions.stream()
                .filter(t -> t.isAfter(cutoff))
                .count();
        }
    }
    
    public enum BiometricLevel {
        BASIC,
        STANDARD,
        HIGH,
        MAXIMUM
    }
    
    public enum BiometricMethod {
        FINGERPRINT("Fingerprint"),
        FACIAL_RECOGNITION("Facial Recognition"),
        IRIS_SCAN("Iris Scan"),
        PALM_VEIN("Palm Vein"),
        VOICE_RECOGNITION("Voice Recognition"),
        BEHAVIORAL("Behavioral Pattern"),
        PIN_PLUS_FINGERPRINT("PIN + Fingerprint");
        
        private final String displayName;
        
        BiometricMethod(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum TransactionType {
        WITHDRAWAL,
        BALANCE_INQUIRY,
        MINI_STATEMENT,
        PIN_CHANGE,
        CASH_ADVANCE,
        DEPOSIT,
        TRANSFER,
        INTERNATIONAL
    }
    
    public enum TimeRestriction {
        NORMAL,
        ELEVATED,
        BLOCKED
    }
}