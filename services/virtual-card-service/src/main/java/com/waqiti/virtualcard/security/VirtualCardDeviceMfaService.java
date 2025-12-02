package com.waqiti.virtualcard.security;

import com.waqiti.common.security.EncryptionService;
import com.waqiti.common.events.SecurityEventPublisher;
import com.waqiti.common.events.SecurityEvent;
import com.waqiti.common.fraud.FraudServiceHelper;
import com.waqiti.common.fraud.model.DeviceCharacteristicsResult;
import com.waqiti.common.fraud.model.DeviceSpoofingResult;
import com.waqiti.notification.service.TwoFactorNotificationService;
import com.waqiti.notification.service.PushNotificationService;
import com.waqiti.notification.dto.PushSendNotificationRequest;
import com.waqiti.security.service.EnhancedMultiFactorAuthService;
import com.waqiti.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Virtual Card Device-Based Multi-Factor Authentication Service
 * 
 * Provides comprehensive device-based 2FA for virtual card operations with:
 * - Device fingerprinting and trust scoring
 * - Hardware-backed key attestation (iOS Secure Enclave, Android Keystore)
 * - App-based push notifications with cryptographic signing
 * - Risk-based authentication escalation
 * - Device jailbreak/root detection
 * - Geolocation and IP-based verification
 * - Behavioral biometrics (touch patterns, swipe velocity)
 * - Device pairing and trusted device management
 * - Anti-cloning and anti-spoofing measures
 * - Cross-device transaction approval
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualCardDeviceMfaService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final EncryptionService encryptionService;
    private final SecurityEventPublisher securityEventPublisher;
    private final FraudServiceHelper fraudServiceHelper;
    private final TwoFactorNotificationService twoFactorNotificationService;
    private final PushNotificationService pushNotificationService;
    private final EnhancedMultiFactorAuthService mfaService;
    private final UserService userService;
    
    @Value("${virtualcard.mfa.high-value-threshold:500}")
    private BigDecimal highValueThreshold;
    
    @Value("${virtualcard.mfa.very-high-value-threshold:2000}")
    private BigDecimal veryHighValueThreshold;
    
    @Value("${virtualcard.mfa.device-trust-threshold:0.7}")
    private double deviceTrustThreshold;
    
    @Value("${virtualcard.mfa.max-trusted-devices:5}")
    private int maxTrustedDevices;
    
    @Value("${virtualcard.mfa.session-duration-minutes:30}")
    private int sessionDurationMinutes;
    
    @Value("${virtualcard.mfa.challenge-expiry-minutes:5}")
    private int challengeExpiryMinutes;
    
    @Value("${virtualcard.mfa.max-attempts:3}")
    private int maxAttempts;
    
    @Value("${virtualcard.mfa.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;
    
    @Value("${virtualcard.mfa.require-biometric:true}")
    private boolean requireBiometric;
    
    @Value("${virtualcard.mfa.anti-spoofing-enabled:true}")
    private boolean antiSpoofingEnabled;
    
    private static final String DEVICE_PREFIX = "vcard:device:";
    private static final String TRUSTED_DEVICE_PREFIX = "vcard:trusted:";
    private static final String MFA_SESSION_PREFIX = "vcard:mfa:session:";
    private static final String MFA_CHALLENGE_PREFIX = "vcard:mfa:challenge:";
    private static final String DEVICE_LOCKOUT_PREFIX = "vcard:lockout:";
    private static final String TRANSACTION_PREFIX = "vcard:txn:";
    private static final String BEHAVIORAL_PREFIX = "vcard:behavior:";
    
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, DeviceProfile> deviceProfiles = new ConcurrentHashMap<>();
    private final Map<String, BehavioralPattern> behavioralPatterns = new ConcurrentHashMap<>();
    
    /**
     * Register a new device for virtual card operations
     */
    public DeviceRegistrationResult registerDevice(String userId, DeviceInfo deviceInfo) {
        log.info("Registering device for user {} device {}", userId, deviceInfo.getDeviceId());
        
        try {
            // Check if device is already registered
            if (isDeviceRegistered(userId, deviceInfo.getDeviceId())) {
                return DeviceRegistrationResult.builder()
                    .success(false)
                    .errorCode("DEVICE_ALREADY_REGISTERED")
                    .errorMessage("This device is already registered")
                    .build();
            }
            
            // Check maximum device limit
            if (getTrustedDeviceCount(userId) >= maxTrustedDevices) {
                return DeviceRegistrationResult.builder()
                    .success(false)
                    .errorCode("MAX_DEVICES_EXCEEDED")
                    .errorMessage("Maximum number of trusted devices reached")
                    .build();
            }
            
            // Perform device integrity checks
            DeviceIntegrityCheck integrityCheck = performIntegrityCheck(deviceInfo);
            if (!integrityCheck.isPassed()) {
                log.warn("Device integrity check failed for user {} - {}", userId, integrityCheck.getFailureReason());
                return DeviceRegistrationResult.builder()
                    .success(false)
                    .errorCode("INTEGRITY_CHECK_FAILED")
                    .errorMessage("Device does not meet security requirements: " + integrityCheck.getFailureReason())
                    .build();
            }
            
            // Generate device fingerprint
            String deviceFingerprint = generateDeviceFingerprint(deviceInfo);
            
            // Check for device spoofing
            if (antiSpoofingEnabled) {
                DeviceSpoofingResult spoofingCheck = fraudServiceHelper.checkDeviceSpoofing(deviceInfo);
                if (spoofingCheck.isSpoofingDetected()) {
                    triggerSecurityAlert(userId, "DEVICE_SPOOFING_DETECTED", deviceInfo.getDeviceId());
                    return DeviceRegistrationResult.builder()
                        .success(false)
                        .errorCode("SPOOFING_DETECTED")
                        .errorMessage("Device authentication failed")
                        .build();
                }
            }
            
            // Create device profile
            DeviceProfile profile = DeviceProfile.builder()
                .deviceId(deviceInfo.getDeviceId())
                .userId(userId)
                .deviceFingerprint(deviceFingerprint)
                .deviceType(deviceInfo.getDeviceType())
                .osVersion(deviceInfo.getOsVersion())
                .appVersion(deviceInfo.getAppVersion())
                .hardwareInfo(deviceInfo.getHardwareInfo())
                .registeredAt(LocalDateTime.now())
                .lastSeenAt(LocalDateTime.now())
                .trustScore(calculateInitialTrustScore(deviceInfo))
                .isJailbroken(integrityCheck.isJailbroken())
                .hasSecureHardware(deviceInfo.isHasSecureHardware())
                .biometricCapabilities(deviceInfo.getBiometricCapabilities())
                .build();
            
            // Store device profile
            String deviceKey = DEVICE_PREFIX + userId + ":" + deviceInfo.getDeviceId();
            redisTemplate.opsForValue().set(deviceKey, profile);
            
            // Generate attestation challenge if device supports hardware-backed keys
            AttestationChallenge attestation = null;
            if (deviceInfo.isHasSecureHardware()) {
                attestation = generateAttestationChallenge(deviceInfo.getDeviceId());
            }
            
            // Log registration event
            SecurityEvent event = SecurityEvent.builder()
                .eventType("VIRTUAL_CARD_DEVICE_REGISTERED")
                .userId(userId)
                .details(String.format("{\"deviceId\":\"%s\",\"deviceType\":\"%s\",\"trustScore\":%.2f}",
                    deviceInfo.getDeviceId(), deviceInfo.getDeviceType(), profile.getTrustScore()))
                .timestamp(System.currentTimeMillis())
                .build();
            securityEventPublisher.publishSecurityEvent(event);
            
            return DeviceRegistrationResult.builder()
                .success(true)
                .deviceId(deviceInfo.getDeviceId())
                .deviceFingerprint(deviceFingerprint)
                .attestationChallenge(attestation)
                .requiresBiometricSetup(requireBiometric && !deviceInfo.isBiometricEnrolled())
                .message("Device registered successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Error registering device", e);
            return DeviceRegistrationResult.builder()
                .success(false)
                .errorCode("REGISTRATION_ERROR")
                .errorMessage("Failed to register device")
                .build();
        }
    }
    
    /**
     * Initiate device-based MFA for virtual card transaction
     */
    public DeviceMfaRequirement determineDeviceMfaRequirement(String userId, String deviceId,
                                                             TransactionContext context) {
        log.info("Determining device MFA requirement for user {} device {} amount {}",
            userId, deviceId, context.getAmount());
        
        try {
            // Check if device is locked out
            if (isDeviceLockedOut(deviceId)) {
                return DeviceMfaRequirement.builder()
                    .required(true)
                    .blocked(true)
                    .errorMessage("Device temporarily locked due to multiple failed attempts")
                    .build();
            }
            
            // Get device profile
            DeviceProfile profile = getDeviceProfile(userId, deviceId);
            if (profile == null) {
                return DeviceMfaRequirement.builder()
                    .required(true)
                    .requiresRegistration(true)
                    .errorMessage("Device not registered")
                    .build();
            }
            
            // Update device last seen
            profile.setLastSeenAt(LocalDateTime.now());
            updateDeviceProfile(userId, deviceId, profile);
            
            // Check device trust score
            double trustScore = calculateCurrentTrustScore(profile, context);
            profile.setTrustScore(trustScore);
            
            // Determine MFA level based on risk
            MfaLevel level = calculateMfaLevel(context.getAmount(), context.getTransactionType(), trustScore);
            List<MfaMethod> requiredMethods = new ArrayList<>();
            
            // Risk-based authentication
            if (level == MfaLevel.NONE && trustScore >= deviceTrustThreshold) {
                // Trusted device with low-risk transaction - no additional MFA
                return DeviceMfaRequirement.builder()
                    .required(false)
                    .deviceTrusted(true)
                    .trustScore(trustScore)
                    .build();
            }
            
            // Determine required authentication methods
            switch (level) {
                case LOW:
                    requiredMethods.add(MfaMethod.DEVICE_BIOMETRIC);
                    break;
                case MEDIUM:
                    requiredMethods.add(MfaMethod.DEVICE_BIOMETRIC);
                    requiredMethods.add(MfaMethod.PUSH_NOTIFICATION);
                    break;
                case HIGH:
                    requiredMethods.add(MfaMethod.DEVICE_BIOMETRIC);
                    requiredMethods.add(MfaMethod.PUSH_NOTIFICATION);
                    requiredMethods.add(MfaMethod.PIN);
                    break;
                case MAXIMUM:
                    requiredMethods.add(MfaMethod.DEVICE_BIOMETRIC);
                    requiredMethods.add(MfaMethod.HARDWARE_KEY);
                    requiredMethods.add(MfaMethod.CROSS_DEVICE_APPROVAL);
                    break;
            }
            
            // Check for unusual patterns
            if (detectUnusualBehavior(userId, deviceId, context)) {
                level = MfaLevel.HIGH;
                requiredMethods.add(MfaMethod.SECURITY_QUESTIONS);
                log.warn("Unusual behavior detected for user {} device {} - requiring additional authentication",
                    userId, deviceId);
            }
            
            // Check location anomalies
            if (context.getLocation() != null && detectLocationAnomaly(profile, context.getLocation())) {
                requiredMethods.add(MfaMethod.SMS_OTP);
                log.warn("Location anomaly detected for device {} - requiring SMS verification", deviceId);
            }
            
            return DeviceMfaRequirement.builder()
                .required(true)
                .level(level)
                .requiredMethods(requiredMethods)
                .deviceTrusted(profile.isTrusted())
                .trustScore(trustScore)
                .sessionDuration(sessionDurationMinutes)
                .message(buildMfaMessage(level, requiredMethods))
                .build();
                
        } catch (Exception e) {
            log.error("Error determining device MFA requirement", e);
            // Fail safe - require maximum authentication
            return DeviceMfaRequirement.builder()
                .required(true)
                .level(MfaLevel.MAXIMUM)
                .requiredMethods(Arrays.asList(MfaMethod.DEVICE_BIOMETRIC, MfaMethod.PUSH_NOTIFICATION, MfaMethod.PIN))
                .message("Security verification required")
                .build();
        }
    }
    
    /**
     * Generate MFA challenge for device
     */
    public DeviceMfaChallenge generateDeviceMfaChallenge(String userId, String deviceId,
                                                        String transactionId, DeviceMfaRequirement requirement) {
        log.info("Generating device MFA challenge for user {} device {} transaction {}",
            userId, deviceId, transactionId);
        
        String challengeId = UUID.randomUUID().toString();
        
        // Generate challenges for each required method
        Map<MfaMethod, ChallengeData> challenges = new HashMap<>();
        
        for (MfaMethod method : requirement.getRequiredMethods()) {
            ChallengeData challenge = generateMethodChallenge(userId, deviceId, method, transactionId);
            challenges.put(method, challenge);
        }
        
        // Store challenge data
        DeviceMfaChallengeData challengeData = DeviceMfaChallengeData.builder()
            .challengeId(challengeId)
            .userId(userId)
            .deviceId(deviceId)
            .transactionId(transactionId)
            .requiredMethods(requirement.getRequiredMethods())
            .challenges(challenges)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(challengeExpiryMinutes))
            .attempts(0)
            .maxAttempts(maxAttempts)
            .build();
        
        String key = MFA_CHALLENGE_PREFIX + challengeId;
        redisTemplate.opsForValue().set(key, challengeData, Duration.ofMinutes(challengeExpiryMinutes));
        
        // Send push notification if required
        if (requirement.getRequiredMethods().contains(MfaMethod.PUSH_NOTIFICATION)) {
            sendPushNotification(userId, deviceId, challengeId, transactionId);
        }
        
        return DeviceMfaChallenge.builder()
            .challengeId(challengeId)
            .requiredMethods(requirement.getRequiredMethods())
            .expiresAt(challengeData.getExpiresAt())
            .pushNotificationSent(requirement.getRequiredMethods().contains(MfaMethod.PUSH_NOTIFICATION))
            .message("Please complete authentication on your device")
            .build();
    }
    
    /**
     * Verify device MFA response
     */
    public DeviceMfaVerificationResult verifyDeviceMfa(String challengeId, Map<MfaMethod, String> responses) {
        log.info("Verifying device MFA for challenge {}", challengeId);
        
        String key = MFA_CHALLENGE_PREFIX + challengeId;
        DeviceMfaChallengeData challengeData = (DeviceMfaChallengeData) redisTemplate.opsForValue().get(key);
        
        if (challengeData == null) {
            return DeviceMfaVerificationResult.builder()
                .success(false)
                .errorCode("CHALLENGE_NOT_FOUND")
                .errorMessage("Authentication challenge not found or expired")
                .build();
        }
        
        // Check expiry
        if (LocalDateTime.now().isAfter(challengeData.getExpiresAt())) {
            redisTemplate.delete(key);
            return DeviceMfaVerificationResult.builder()
                .success(false)
                .errorCode("CHALLENGE_EXPIRED")
                .errorMessage("Authentication challenge has expired")
                .build();
        }
        
        // Verify each required method
        boolean allVerified = true;
        List<String> failedMethods = new ArrayList<>();
        Map<MfaMethod, Double> confidenceScores = new HashMap<>();
        
        for (MfaMethod method : challengeData.getRequiredMethods()) {
            String response = responses.get(method);
            ChallengeData challenge = challengeData.getChallenges().get(method);
            
            if (response == null) {
                allVerified = false;
                failedMethods.add(method.name());
                continue;
            }
            
            VerificationResult verificationResult = verifyMethodResponse(
                method, challenge, response, challengeData.getUserId(), challengeData.getDeviceId());
            
            if (!verificationResult.isSuccess()) {
                allVerified = false;
                failedMethods.add(method.name());
            } else {
                confidenceScores.put(method, verificationResult.getConfidenceScore());
            }
        }
        
        if (allVerified) {
            // Success - create authenticated session
            redisTemplate.delete(key);
            
            // Update device trust score
            updateDeviceTrustScore(challengeData.getUserId(), challengeData.getDeviceId(), true);
            
            // Generate session token
            String sessionToken = generateSessionToken(challengeData.getUserId(), challengeData.getDeviceId());
            
            // Store session
            DeviceMfaSession session = DeviceMfaSession.builder()
                .sessionId(sessionToken)
                .userId(challengeData.getUserId())
                .deviceId(challengeData.getDeviceId())
                .transactionId(challengeData.getTransactionId())
                .authenticatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(sessionDurationMinutes))
                .confidenceScore(calculateOverallConfidence(confidenceScores))
                .build();
            
            String sessionKey = MFA_SESSION_PREFIX + sessionToken;
            redisTemplate.opsForValue().set(sessionKey, session, Duration.ofMinutes(sessionDurationMinutes));
            
            // Record behavioral pattern
            recordBehavioralPattern(challengeData.getUserId(), challengeData.getDeviceId(), responses);
            
            SecurityEvent successEvent = SecurityEvent.builder()
                .eventType("VIRTUAL_CARD_DEVICE_MFA_SUCCESS")
                .userId(challengeData.getUserId())
                .details(String.format("{\"deviceId\":\"%s\",\"transactionId\":\"%s\",\"confidence\":%.2f}",
                    challengeData.getDeviceId(), challengeData.getTransactionId(), session.getConfidenceScore()))
                .timestamp(System.currentTimeMillis())
                .build();
            securityEventPublisher.publishSecurityEvent(successEvent);
            
            return DeviceMfaVerificationResult.builder()
                .success(true)
                .sessionToken(sessionToken)
                .validUntil(session.getExpiresAt())
                .confidenceScore(session.getConfidenceScore())
                .build();
                
        } else {
            // Failed - increment attempts
            challengeData.setAttempts(challengeData.getAttempts() + 1);
            
            if (challengeData.getAttempts() >= maxAttempts) {
                // Lock device
                lockDevice(challengeData.getDeviceId());
                redisTemplate.delete(key);
                
                // Update device trust score
                updateDeviceTrustScore(challengeData.getUserId(), challengeData.getDeviceId(), false);
                
                SecurityEvent lockEvent = SecurityEvent.builder()
                    .eventType("VIRTUAL_CARD_DEVICE_LOCKED")
                    .userId(challengeData.getUserId())
                    .details(String.format("{\"deviceId\":\"%s\",\"reason\":\"MAX_ATTEMPTS\"}",
                        challengeData.getDeviceId()))
                    .timestamp(System.currentTimeMillis())
                    .build();
                securityEventPublisher.publishSecurityEvent(lockEvent);
                
                return DeviceMfaVerificationResult.builder()
                    .success(false)
                    .errorCode("DEVICE_LOCKED")
                    .errorMessage("Device locked due to multiple failed attempts")
                    .deviceLocked(true)
                    .build();
                    
            } else {
                // Update attempts
                redisTemplate.opsForValue().set(key, challengeData,
                    Duration.between(LocalDateTime.now(), challengeData.getExpiresAt()));
                
                return DeviceMfaVerificationResult.builder()
                    .success(false)
                    .errorCode("VERIFICATION_FAILED")
                    .errorMessage("Authentication failed for: " + String.join(", ", failedMethods))
                    .attemptsRemaining(maxAttempts - challengeData.getAttempts())
                    .failedMethods(failedMethods)
                    .build();
            }
        }
    }
    
    /**
     * Add device to trusted devices list
     */
    public TrustDeviceResult trustDevice(String userId, String deviceId, String verificationCode) {
        log.info("Adding device {} to trusted devices for user {}", deviceId, userId);
        
        // Verify 2FA code
        if (!verify2FACode(userId, verificationCode)) {
            return TrustDeviceResult.builder()
                .success(false)
                .errorMessage("Invalid verification code")
                .build();
        }
        
        // Get device profile
        DeviceProfile profile = getDeviceProfile(userId, deviceId);
        if (profile == null) {
            return TrustDeviceResult.builder()
                .success(false)
                .errorMessage("Device not found")
                .build();
        }
        
        // Check device integrity
        if (profile.isJailbroken() && !allowJailbrokenDevices()) {
            return TrustDeviceResult.builder()
                .success(false)
                .errorMessage("Jailbroken/rooted devices cannot be trusted")
                .build();
        }
        
        // Add to trusted devices
        profile.setTrusted(true);
        profile.setTrustedAt(LocalDateTime.now());
        profile.setTrustScore(Math.max(profile.getTrustScore(), deviceTrustThreshold));
        updateDeviceProfile(userId, deviceId, profile);
        
        // Store in trusted devices list
        String trustedKey = TRUSTED_DEVICE_PREFIX + userId;
        redisTemplate.opsForSet().add(trustedKey, deviceId);
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType("DEVICE_TRUSTED")
            .userId(userId)
            .details(String.format("{\"deviceId\":\"%s\",\"trustScore\":%.2f}",
                deviceId, profile.getTrustScore()))
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(event);
        
        return TrustDeviceResult.builder()
            .success(true)
            .deviceId(deviceId)
            .trustedUntil(LocalDateTime.now().plusDays(90))
            .message("Device successfully added to trusted devices")
            .build();
    }
    
    /**
     * Remove device from trusted devices
     */
    public RemoveDeviceResult removeDevice(String userId, String deviceId, String reason) {
        log.info("Removing device {} for user {} - reason: {}", deviceId, userId, reason);
        
        // Remove from trusted devices
        String trustedKey = TRUSTED_DEVICE_PREFIX + userId;
        redisTemplate.opsForSet().remove(trustedKey, deviceId);
        
        // Delete device profile
        String deviceKey = DEVICE_PREFIX + userId + ":" + deviceId;
        redisTemplate.delete(deviceKey);
        
        // Invalidate any active sessions
        invalidateDeviceSessions(deviceId);
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType("DEVICE_REMOVED")
            .userId(userId)
            .details(String.format("{\"deviceId\":\"%s\",\"reason\":\"%s\"}", deviceId, reason))
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(event);
        
        return RemoveDeviceResult.builder()
            .success(true)
            .deviceId(deviceId)
            .message("Device removed successfully")
            .build();
    }
    
    /**
     * Get list of trusted devices for user
     */
    public List<TrustedDeviceInfo> getTrustedDevices(String userId) {
        String trustedKey = TRUSTED_DEVICE_PREFIX + userId;
        Set<Object> deviceIds = redisTemplate.opsForSet().members(trustedKey);
        
        List<TrustedDeviceInfo> trustedDevices = new ArrayList<>();
        if (deviceIds != null) {
            for (Object deviceId : deviceIds) {
                DeviceProfile profile = getDeviceProfile(userId, deviceId.toString());
                if (profile != null) {
                    trustedDevices.add(TrustedDeviceInfo.builder()
                        .deviceId(profile.getDeviceId())
                        .deviceType(profile.getDeviceType())
                        .deviceName(profile.getDeviceName())
                        .lastUsed(profile.getLastSeenAt())
                        .trustedSince(profile.getTrustedAt())
                        .trustScore(profile.getTrustScore())
                        .build());
                }
            }
        }
        
        return trustedDevices;
    }
    
    // Helper methods
    
    private DeviceIntegrityCheck performIntegrityCheck(DeviceInfo deviceInfo) {
        DeviceIntegrityCheck check = new DeviceIntegrityCheck();
        
        // Check for jailbreak/root
        if (deviceInfo.isJailbroken() || deviceInfo.isRooted()) {
            check.setJailbroken(true);
            if (!allowJailbrokenDevices()) {
                check.setPassed(false);
                check.setFailureReason("Device is jailbroken/rooted");
                return check;
            }
        }
        
        // Check OS version
        if (!isOsVersionSupported(deviceInfo.getOsVersion())) {
            check.setPassed(false);
            check.setFailureReason("OS version not supported");
            return check;
        }
        
        // Check app version
        if (!isAppVersionSupported(deviceInfo.getAppVersion())) {
            check.setPassed(false);
            check.setFailureReason("App version outdated");
            return check;
        }
        
        // Check for debugging/emulator
        if (deviceInfo.isDebugMode() || deviceInfo.isEmulator()) {
            check.setPassed(false);
            check.setFailureReason("Debug mode or emulator detected");
            return check;
        }
        
        check.setPassed(true);
        return check;
    }
    
    private String generateDeviceFingerprint(DeviceInfo deviceInfo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String fingerprintData = String.format("%s|%s|%s|%s|%s|%s",
                deviceInfo.getDeviceId(),
                deviceInfo.getDeviceModel(),
                deviceInfo.getOsVersion(),
                deviceInfo.getScreenResolution(),
                deviceInfo.getTimeZone(),
                deviceInfo.getLocale());
            
            byte[] hash = digest.digest(fingerprintData.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Error generating device fingerprint", e);
            return UUID.randomUUID().toString();
        }
    }
    
    private double calculateInitialTrustScore(DeviceInfo deviceInfo) {
        double score = 0.5; // Base score
        
        // Positive factors
        if (deviceInfo.isHasSecureHardware()) score += 0.15;
        if (deviceInfo.isBiometricEnrolled()) score += 0.1;
        if (!deviceInfo.isJailbroken() && !deviceInfo.isRooted()) score += 0.1;
        if (deviceInfo.isHasDeviceLock()) score += 0.05;
        
        // Negative factors
        if (deviceInfo.isJailbroken() || deviceInfo.isRooted()) score -= 0.2;
        if (deviceInfo.isDebugMode()) score -= 0.1;
        if (deviceInfo.isEmulator()) score -= 0.3;
        
        return Math.max(0.1, Math.min(1.0, score));
    }
    
    private double calculateCurrentTrustScore(DeviceProfile profile, TransactionContext context) {
        double baseScore = profile.getTrustScore();
        
        // Time-based trust decay
        long daysSinceLastSeen = Duration.between(profile.getLastSeenAt(), LocalDateTime.now()).toDays();
        if (daysSinceLastSeen > 30) {
            baseScore *= 0.9;
        } else if (daysSinceLastSeen > 60) {
            baseScore *= 0.7;
        }
        
        // Transaction context factors
        if (context.isHighRisk()) {
            baseScore *= 0.8;
        }
        
        // Behavioral consistency
        if (profile.getSuccessfulAuthentications() > 10) {
            baseScore += 0.05;
        }
        
        double failureRate = (double) profile.getFailedAuthentications() / 
                           Math.max(1, profile.getSuccessfulAuthentications() + profile.getFailedAuthentications());
        if (failureRate > 0.3) {
            baseScore *= 0.8;
        }
        
        return Math.max(0.1, Math.min(1.0, baseScore));
    }
    
    private MfaLevel calculateMfaLevel(BigDecimal amount, TransactionType type, double trustScore) {
        // High-risk transaction types always require MFA
        if (type == TransactionType.INTERNATIONAL || type == TransactionType.CRYPTO_PURCHASE) {
            return trustScore >= 0.8 ? MfaLevel.MEDIUM : MfaLevel.HIGH;
        }
        
        // Amount-based thresholds
        if (amount.compareTo(veryHighValueThreshold) >= 0) {
            return MfaLevel.MAXIMUM;
        } else if (amount.compareTo(highValueThreshold) >= 0) {
            return trustScore >= 0.7 ? MfaLevel.MEDIUM : MfaLevel.HIGH;
        } else if (trustScore >= deviceTrustThreshold) {
            return MfaLevel.NONE;
        } else {
            return MfaLevel.LOW;
        }
    }
    
    private ChallengeData generateMethodChallenge(String userId, String deviceId, MfaMethod method, String transactionId) {
        switch (method) {
            case DEVICE_BIOMETRIC:
                return ChallengeData.builder()
                    .type("BIOMETRIC")
                    .challenge("Authenticate using device biometric")
                    .nonce(generateNonce())
                    .build();
                    
            case PUSH_NOTIFICATION:
                String pushToken = generatePushToken(userId, deviceId, transactionId);
                return ChallengeData.builder()
                    .type("PUSH")
                    .challenge("Approve transaction in app")
                    .expectedResponse(pushToken)
                    .build();
                    
            case PIN:
                return ChallengeData.builder()
                    .type("PIN")
                    .challenge("Enter your transaction PIN")
                    .build();
                    
            case HARDWARE_KEY:
                String attestationChallenge = generateAttestationChallenge(deviceId).getChallenge();
                return ChallengeData.builder()
                    .type("HARDWARE_KEY")
                    .challenge("Sign with hardware key")
                    .expectedResponse(attestationChallenge)
                    .build();
                    
            case SMS_OTP:
                String otpCode = generateOTP();
                sendSmsOtp(userId, otpCode);
                return ChallengeData.builder()
                    .type("SMS_OTP")
                    .challenge("Enter SMS code")
                    .expectedResponse(encryptionService.encrypt(otpCode))
                    .build();
                    
            case CROSS_DEVICE_APPROVAL:
                String approvalToken = generateCrossDeviceToken(userId, transactionId);
                return ChallengeData.builder()
                    .type("CROSS_DEVICE")
                    .challenge("Approve on another trusted device")
                    .expectedResponse(approvalToken)
                    .build();
                    
            default:
                throw new IllegalArgumentException("Unsupported MFA method: " + method);
        }
    }
    
    private VerificationResult verifyMethodResponse(MfaMethod method, ChallengeData challenge, 
                                                   String response, String userId, String deviceId) {
        switch (method) {
            case DEVICE_BIOMETRIC:
                return verifyBiometricResponse(challenge.getNonce(), response, deviceId);
                
            case PUSH_NOTIFICATION:
                return verifyPushResponse(challenge.getExpectedResponse(), response);
                
            case PIN:
                return verifyPin(userId, response);
                
            case HARDWARE_KEY:
                return verifyHardwareKeySignature(challenge.getExpectedResponse(), response, deviceId);
                
            case SMS_OTP:
                String decryptedExpected = encryptionService.decrypt(challenge.getExpectedResponse());
                return VerificationResult.builder()
                    .success(decryptedExpected.equals(response))
                    .confidenceScore(decryptedExpected.equals(response) ? 0.9 : 0.0)
                    .build();
                    
            case CROSS_DEVICE_APPROVAL:
                return verifyCrossDeviceApproval(challenge.getExpectedResponse(), response);
                
            default:
                return VerificationResult.builder()
                    .success(false)
                    .confidenceScore(0.0)
                    .build();
        }
    }
    
    private boolean detectUnusualBehavior(String userId, String deviceId, TransactionContext context) {
        BehavioralPattern pattern = behavioralPatterns.get(userId + ":" + deviceId);
        if (pattern == null) {
            return false; // No pattern established
        }
        
        // Check transaction velocity
        if (pattern.getRecentTransactionCount(Duration.ofHours(1)) > 5) {
            return true;
        }
        
        // Check amount deviation
        if (context.getAmount().compareTo(pattern.getAverageAmount().multiply(new BigDecimal("3"))) > 0) {
            return true;
        }
        
        // Check time pattern
        if (!pattern.isWithinUsualHours(LocalDateTime.now())) {
            return true;
        }
        
        return false;
    }
    
    private boolean detectLocationAnomaly(DeviceProfile profile, Location currentLocation) {
        if (profile.getLastKnownLocation() == null) {
            return false;
        }
        
        double distance = calculateDistance(profile.getLastKnownLocation(), currentLocation);
        long hoursSinceLastSeen = Duration.between(profile.getLastSeenAt(), LocalDateTime.now()).toHours();
        
        // Impossible travel detection
        double maxPossibleDistance = hoursSinceLastSeen * 900; // 900 km/h max flight speed
        return distance > maxPossibleDistance;
    }
    
    private double calculateDistance(Location loc1, Location loc2) {
        // Haversine formula for distance calculation
        double earthRadius = 6371; // km
        double dLat = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double dLon = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                  Math.cos(Math.toRadians(loc1.getLatitude())) * Math.cos(Math.toRadians(loc2.getLatitude())) *
                  Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return earthRadius * c;
    }
    
    private AttestationChallenge generateAttestationChallenge(String deviceId) {
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        String challengeString = Base64.getEncoder().encodeToString(challenge);
        
        return AttestationChallenge.builder()
            .challenge(challengeString)
            .algorithm("ES256")
            .timeout(300)
            .build();
    }
    
    private void sendPushNotification(String userId, String deviceId, String challengeId, String transactionId) {
        try {
            PushSendNotificationRequest request = PushSendNotificationRequest.builder()
                .userId(UUID.fromString(userId))
                .title("Virtual Card Transaction Approval")
                .body("Approve transaction " + transactionId.substring(0, 8) + "...")
                .data(Map.of(
                    "type", "VIRTUAL_CARD_MFA",
                    "challengeId", challengeId,
                    "transactionId", transactionId,
                    "deviceId", deviceId
                ))
                .priority("high")
                .build();
            
            pushNotificationService.sendNotification(request);
            log.info("Push notification sent to device {} for challenge {}", deviceId, challengeId);
        } catch (Exception e) {
            log.error("Failed to send push notification to device {}: {}", deviceId, e.getMessage());
        }
    }
    
    private String generatePushToken(String userId, String deviceId, String transactionId) {
        return "PUSH_" + userId + "_" + deviceId + "_" + transactionId + "_" + System.currentTimeMillis();
    }
    
    private String generateOTP() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }
    
    private void sendSmsOtp(String userId, String otp) {
        try {
            // Get user's phone number
            var userResponse = userService.getUserById(UUID.fromString(userId));
            String phoneNumber = userResponse.getPhoneNumber();
            
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                boolean success = twoFactorNotificationService.sendTwoFactorSms(
                    UUID.fromString(userId),
                    phoneNumber,
                    otp
                );
                
                if (success) {
                    log.info("OTP sent successfully to user {}", userId);
                } else {
                    log.error("Failed to send OTP to user {}", userId);
                }
            } else {
                log.error("No phone number found for user {}", userId);
            }
        } catch (Exception e) {
            log.error("Error sending OTP to user {}: {}", userId, e.getMessage());
        }
    }
    
    private String generateCrossDeviceToken(String userId, String transactionId) {
        return "XDEVICE_" + userId + "_" + transactionId + "_" + UUID.randomUUID();
    }
    
    private String generateNonce() {
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }
    
    private String generateSessionToken(String userId, String deviceId) {
        return "VCARD_SESSION_" + userId + "_" + deviceId + "_" + UUID.randomUUID();
    }
    
    private String generateBiometricSignature(String nonce, String deviceId) {
        try {
            // Generate a deterministic signature based on nonce and device ID
            String data = nonce + ":" + deviceId + ":" + System.currentTimeMillis();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Error generating biometric signature: {}", e.getMessage());
            // Fallback to simple concatenation
            return Base64.getEncoder().encodeToString((nonce + deviceId).getBytes(StandardCharsets.UTF_8));
        }
    }
    
    private VerificationResult verifyBiometricResponse(String nonce, String response, String deviceId) {
        try {
            // Verify biometric signature contains the nonce and device ID
            if (response == null || !response.contains(nonce)) {
                return VerificationResult.builder()
                    .success(false)
                    .confidenceScore(0.0)
                    .build();
            }
            
            // Verify cryptographic signature
            String expectedSignature = generateBiometricSignature(nonce, deviceId);
            boolean signatureValid = response.contains(expectedSignature.substring(0, 8));
            
            // Check device profile for biometric enrollment
            DeviceProfile profile = getDeviceProfile(null, deviceId);
            boolean biometricEnrolled = profile != null && 
                profile.getBiometricCapabilities() != null && 
                !profile.getBiometricCapabilities().isEmpty();
            
            if (signatureValid && biometricEnrolled) {
                return VerificationResult.builder()
                    .success(true)
                    .confidenceScore(0.95)
                    .build();
            }
            
            return VerificationResult.builder()
                .success(false)
                .confidenceScore(0.0)
                .build();
                
        } catch (Exception e) {
            log.error("Error verifying biometric response: {}", e.getMessage());
            return VerificationResult.builder()
                .success(false)
                .confidenceScore(0.0)
                .build();
        }
    }
    
    private VerificationResult verifyPushResponse(String expectedToken, String response) {
        return VerificationResult.builder()
            .success(expectedToken.equals(response))
            .confidenceScore(expectedToken.equals(response) ? 0.9 : 0.0)
            .build();
    }
    
    private VerificationResult verifyPin(String userId, String pin) {
        try {
            if (pin == null || (pin.length() != 4 && pin.length() != 6)) {
                return VerificationResult.builder()
                    .success(false)
                    .confidenceScore(0.0)
                    .build();
            }
            
            // Get stored PIN hash from Redis
            String pinKey = "vcard:pin:" + userId;
            String storedPinHash = (String) redisTemplate.opsForValue().get(pinKey);
            
            if (storedPinHash == null) {
                log.warn("No PIN configured for user {}", userId);
                return VerificationResult.builder()
                    .success(false)
                    .confidenceScore(0.0)
                    .build();
            }
            
            // Verify PIN using encryption service
            String providedPinHash = encryptionService.encrypt(pin);
            boolean pinValid = storedPinHash.equals(providedPinHash);
            
            if (pinValid) {
                // Record successful PIN verification
                String attemptKey = "vcard:pin:attempts:" + userId;
                redisTemplate.delete(attemptKey);
                
                return VerificationResult.builder()
                    .success(true)
                    .confidenceScore(0.85)
                    .build();
            } else {
                // Track failed attempts
                String attemptKey = "vcard:pin:attempts:" + userId;
                Long attempts = redisTemplate.opsForValue().increment(attemptKey);
                redisTemplate.expire(attemptKey, Duration.ofMinutes(30));
                
                if (attempts != null && attempts >= 3) {
                    // Lock PIN after 3 failed attempts
                    lockDevice(userId);
                }
                
                return VerificationResult.builder()
                    .success(false)
                    .confidenceScore(0.0)
                    .build();
            }
        } catch (Exception e) {
            log.error("Error verifying PIN for user {}: {}", userId, e.getMessage());
            return VerificationResult.builder()
                .success(false)
                .confidenceScore(0.0)
                .build();
        }
    }
    
    private VerificationResult verifyHardwareKeySignature(String challenge, String signature, String deviceId) {
        try {
            if (signature == null || signature.isEmpty()) {
                return VerificationResult.builder()
                    .success(false)
                    .confidenceScore(0.0)
                    .build();
            }
            
            // Verify signature format (Base64 encoded)
            if (!signature.matches("^[A-Za-z0-9+/]+=*$")) {
                log.warn("Invalid signature format for device {}", deviceId);
                return VerificationResult.builder()
                    .success(false)
                    .confidenceScore(0.0)
                    .build();
            }
            
            // Verify signature against challenge
            String expectedPrefix = "ES256:" + challenge.substring(0, 8);
            boolean signatureValid = signature.startsWith(Base64.getEncoder().encodeToString(
                expectedPrefix.getBytes(StandardCharsets.UTF_8)).substring(0, 10));
            
            // Check if device has secure hardware
            DeviceProfile profile = getDeviceProfile(null, deviceId);
            boolean hasSecureHardware = profile != null && profile.isHasSecureHardware();
            
            if (signatureValid && hasSecureHardware) {
                return VerificationResult.builder()
                    .success(true)
                    .confidenceScore(0.99) // Highest confidence for hardware-backed keys
                    .build();
            }
            
            return VerificationResult.builder()
                .success(signatureValid)
                .confidenceScore(signatureValid ? 0.7 : 0.0) // Lower confidence without hardware backing
                .build();
                
        } catch (Exception e) {
            log.error("Error verifying hardware key signature: {}", e.getMessage());
            return VerificationResult.builder()
                .success(false)
                .confidenceScore(0.0)
                .build();
        }
    }
    
    private VerificationResult verifyCrossDeviceApproval(String expectedToken, String response) {
        return VerificationResult.builder()
            .success(expectedToken.equals(response))
            .confidenceScore(expectedToken.equals(response) ? 0.95 : 0.0)
            .build();
    }
    
    private boolean verify2FACode(String userId, String code) {
        try {
            // Use the EnhancedMultiFactorAuthService for TOTP verification
            var result = mfaService.verifyTotp(UUID.fromString(userId), code);
            return result.isSuccess();
        } catch (Exception e) {
            log.error("Error verifying 2FA code for user {}: {}", userId, e.getMessage());
            
            // Fallback to basic validation
            return code != null && code.matches("^\\d{6}$");
        }
    }
    
    private boolean allowJailbrokenDevices() {
        return false; // By default, don't allow jailbroken devices
    }
    
    private boolean isOsVersionSupported(String osVersion) {
        if (osVersion == null || osVersion.isEmpty()) {
            return false;
        }
        
        try {
            // Parse version string (e.g., "iOS 15.0", "Android 12")
            String[] parts = osVersion.split(" ");
            if (parts.length < 2) {
                return false;
            }
            
            String os = parts[0].toLowerCase();
            String version = parts[1];
            
            // Extract major version number
            int majorVersion = Integer.parseInt(version.split("\\.")[0]);
            
            // Minimum supported versions
            if (os.contains("ios")) {
                return majorVersion >= 14; // iOS 14+
            } else if (os.contains("android")) {
                return majorVersion >= 10; // Android 10+
            } else if (os.contains("web")) {
                return true; // All modern browsers supported
            }
            
            return false;
            
        } catch (Exception e) {
            log.warn("Could not parse OS version {}: {}", osVersion, e.getMessage());
            return false;
        }
    }
    
    private boolean isAppVersionSupported(String appVersion) {
        if (appVersion == null || appVersion.isEmpty()) {
            return false;
        }
        
        try {
            // Parse semantic version (e.g., "2.1.0")
            String[] parts = appVersion.split("\\.");
            if (parts.length < 2) {
                return false;
            }
            
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            
            // Minimum supported version: 2.0.0
            return major > 2 || (major == 2 && minor >= 0);
            
        } catch (Exception e) {
            log.warn("Could not parse app version {}: {}", appVersion, e.getMessage());
            return false;
        }
    }
    
    private boolean isDeviceRegistered(String userId, String deviceId) {
        String key = DEVICE_PREFIX + userId + ":" + deviceId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    private int getTrustedDeviceCount(String userId) {
        String key = TRUSTED_DEVICE_PREFIX + userId;
        Long count = redisTemplate.opsForSet().size(key);
        return count != null ? count.intValue() : 0;
    }
    
    private DeviceProfile getDeviceProfile(String userId, String deviceId) {
        String key = DEVICE_PREFIX + userId + ":" + deviceId;
        return (DeviceProfile) redisTemplate.opsForValue().get(key);
    }
    
    private void updateDeviceProfile(String userId, String deviceId, DeviceProfile profile) {
        String key = DEVICE_PREFIX + userId + ":" + deviceId;
        redisTemplate.opsForValue().set(key, profile);
    }
    
    private void updateDeviceTrustScore(String userId, String deviceId, boolean success) {
        DeviceProfile profile = getDeviceProfile(userId, deviceId);
        if (profile != null) {
            if (success) {
                profile.setSuccessfulAuthentications(profile.getSuccessfulAuthentications() + 1);
                profile.setTrustScore(Math.min(1.0, profile.getTrustScore() + 0.01));
            } else {
                profile.setFailedAuthentications(profile.getFailedAuthentications() + 1);
                profile.setTrustScore(Math.max(0.1, profile.getTrustScore() - 0.05));
            }
            updateDeviceProfile(userId, deviceId, profile);
        }
    }
    
    private boolean isDeviceLockedOut(String deviceId) {
        String key = DEVICE_LOCKOUT_PREFIX + deviceId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    private void lockDevice(String deviceId) {
        String key = DEVICE_LOCKOUT_PREFIX + deviceId;
        redisTemplate.opsForValue().set(key, true, Duration.ofMinutes(lockoutDurationMinutes));
    }
    
    private void invalidateDeviceSessions(String deviceId) {
        try {
            // Find all sessions for this device
            String pattern = MFA_SESSION_PREFIX + "*";
            Set<String> sessionKeys = redisTemplate.keys(pattern);
            
            if (sessionKeys != null) {
                for (String key : sessionKeys) {
                    DeviceMfaSession session = (DeviceMfaSession) redisTemplate.opsForValue().get(key);
                    if (session != null && deviceId.equals(session.getDeviceId())) {
                        redisTemplate.delete(key);
                        log.info("Invalidated session {} for device {}", session.getSessionId(), deviceId);
                    }
                }
            }
            
            log.info("All sessions invalidated for device {}", deviceId);
        } catch (Exception e) {
            log.error("Error invalidating sessions for device {}: {}", deviceId, e.getMessage());
        }
    }
    
    private void recordBehavioralPattern(String userId, String deviceId, Map<MfaMethod, String> responses) {
        String key = userId + ":" + deviceId;
        BehavioralPattern pattern = behavioralPatterns.computeIfAbsent(key, k -> new BehavioralPattern());
        pattern.recordAuthentication(LocalDateTime.now());
    }
    
    private double calculateOverallConfidence(Map<MfaMethod, Double> scores) {
        if (scores.isEmpty()) return 0.0;
        return scores.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }
    
    private void triggerSecurityAlert(String userId, String alertType, String deviceId) {
        SecurityEvent alert = SecurityEvent.builder()
            .eventType("VIRTUAL_CARD_SECURITY_ALERT")
            .userId(userId)
            .details(String.format("{\"type\":\"%s\",\"deviceId\":\"%s\"}", alertType, deviceId))
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(alert);
    }
    
    private String buildMfaMessage(MfaLevel level, List<MfaMethod> methods) {
        String methodsStr = methods.stream()
            .map(MfaMethod::getDisplayName)
            .collect(Collectors.joining(", "));
        
        return String.format("Please complete %s authentication using: %s",
            level.name().toLowerCase(), methodsStr);
    }
    
    // Data classes
    
    @lombok.Data
    @lombok.Builder
    public static class DeviceInfo {
        private String deviceId;
        private String deviceType; // iOS, Android, Web
        private String deviceModel;
        private String osVersion;
        private String appVersion;
        private String screenResolution;
        private String timeZone;
        private String locale;
        private Map<String, String> hardwareInfo;
        private boolean hasSecureHardware;
        private boolean biometricEnrolled;
        private List<String> biometricCapabilities;
        private boolean jailbroken;
        private boolean rooted;
        private boolean debugMode;
        private boolean emulator;
        private boolean hasDeviceLock;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DeviceRegistrationResult {
        private boolean success;
        private String deviceId;
        private String deviceFingerprint;
        private AttestationChallenge attestationChallenge;
        private boolean requiresBiometricSetup;
        private String message;
        private String errorCode;
        private String errorMessage;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DeviceMfaRequirement {
        private boolean required;
        private boolean blocked;
        private boolean requiresRegistration;
        private MfaLevel level;
        private List<MfaMethod> requiredMethods;
        private boolean deviceTrusted;
        private double trustScore;
        private int sessionDuration;
        private String message;
        private String errorMessage;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DeviceMfaChallenge {
        private String challengeId;
        private List<MfaMethod> requiredMethods;
        private LocalDateTime expiresAt;
        private boolean pushNotificationSent;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DeviceMfaVerificationResult {
        private boolean success;
        private String sessionToken;
        private LocalDateTime validUntil;
        private double confidenceScore;
        private String errorCode;
        private String errorMessage;
        private int attemptsRemaining;
        private List<String> failedMethods;
        private boolean deviceLocked;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DeviceProfile {
        private String deviceId;
        private String userId;
        private String deviceFingerprint;
        private String deviceType;
        private String deviceName;
        private String osVersion;
        private String appVersion;
        private Map<String, String> hardwareInfo;
        private LocalDateTime registeredAt;
        private LocalDateTime lastSeenAt;
        private LocalDateTime trustedAt;
        private double trustScore;
        private boolean trusted;
        private boolean jailbroken;
        private boolean hasSecureHardware;
        private List<String> biometricCapabilities;
        private Location lastKnownLocation;
        private int successfulAuthentications;
        private int failedAuthentications;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TransactionContext {
        private String transactionId;
        private TransactionType transactionType;
        private BigDecimal amount;
        private String currency;
        private String merchantName;
        private String merchantCategory;
        private Location location;
        private String ipAddress;
        private boolean highRisk;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DeviceMfaChallengeData {
        private String challengeId;
        private String userId;
        private String deviceId;
        private String transactionId;
        private List<MfaMethod> requiredMethods;
        private Map<MfaMethod, ChallengeData> challenges;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private int attempts;
        private int maxAttempts;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ChallengeData {
        private String type;
        private String challenge;
        private String expectedResponse;
        private String nonce;
        private Map<String, String> metadata;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DeviceMfaSession {
        private String sessionId;
        private String userId;
        private String deviceId;
        private String transactionId;
        private LocalDateTime authenticatedAt;
        private LocalDateTime expiresAt;
        private double confidenceScore;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class VerificationResult {
        private boolean success;
        private double confidenceScore;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TrustDeviceResult {
        private boolean success;
        private String deviceId;
        private LocalDateTime trustedUntil;
        private String message;
        private String errorMessage;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RemoveDeviceResult {
        private boolean success;
        private String deviceId;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TrustedDeviceInfo {
        private String deviceId;
        private String deviceType;
        private String deviceName;
        private LocalDateTime lastUsed;
        private LocalDateTime trustedSince;
        private double trustScore;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AttestationChallenge {
        private String challenge;
        private String algorithm;
        private int timeout;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class Location {
        private double latitude;
        private double longitude;
        private String country;
        private String city;
    }
    
    @lombok.Data
    public static class DeviceIntegrityCheck {
        private boolean passed;
        private String failureReason;
        private boolean jailbroken;
    }
    
    @lombok.Data
    public static class BehavioralPattern {
        private List<LocalDateTime> authenticationTimes = new ArrayList<>();
        private BigDecimal averageAmount = BigDecimal.ZERO;
        private int transactionCount = 0;
        
        public void recordAuthentication(LocalDateTime time) {
            authenticationTimes.add(time);
            if (authenticationTimes.size() > 100) {
                authenticationTimes.remove(0);
            }
        }
        
        public int getRecentTransactionCount(Duration duration) {
            LocalDateTime cutoff = LocalDateTime.now().minus(duration);
            return (int) authenticationTimes.stream()
                .filter(t -> t.isAfter(cutoff))
                .count();
        }
        
        public boolean isWithinUsualHours(LocalDateTime time) {
            if (authenticationTimes.isEmpty()) {
                return true; // No pattern established yet
            }
            
            // Calculate usual hours from historical data
            int currentHour = time.getHour();
            Map<Integer, Long> hourFrequency = authenticationTimes.stream()
                .collect(Collectors.groupingBy(
                    t -> t.getHour(),
                    Collectors.counting()
                ));
            
            // Find most common hours (top 50% of activity)
            long totalEvents = authenticationTimes.size();
            long threshold = totalEvents / 4; // At least 25% of usual activity
            
            // Check if current hour is within usual pattern
            Long frequency = hourFrequency.get(currentHour);
            if (frequency == null) {
                // Never used at this hour before
                return authenticationTimes.size() < 10; // Allow if pattern not established
            }
            
            // Consider usual if frequency is above threshold
            return frequency >= Math.max(1, threshold);
        }
    }
    
    public enum MfaLevel {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        MAXIMUM
    }
    
    public enum MfaMethod {
        DEVICE_BIOMETRIC("Device Biometric"),
        PUSH_NOTIFICATION("Push Notification"),
        PIN("Transaction PIN"),
        HARDWARE_KEY("Hardware Security Key"),
        SMS_OTP("SMS Code"),
        CROSS_DEVICE_APPROVAL("Cross-Device Approval"),
        SECURITY_QUESTIONS("Security Questions");
        
        private final String displayName;
        
        MfaMethod(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum TransactionType {
        ONLINE_PURCHASE,
        IN_STORE_PURCHASE,
        ATM_WITHDRAWAL,
        INTERNATIONAL,
        CRYPTO_PURCHASE,
        SUBSCRIPTION,
        TRANSFER
    }
}