package com.waqiti.payment.security;

import com.waqiti.common.security.EncryptionService;
import com.waqiti.common.events.SecurityEventPublisher;
import com.waqiti.common.events.SecurityEvent;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.payment.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class HighValueTransactionMfaService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final EncryptionService encryptionService;
    private final SecurityEventPublisher securityEventPublisher;
    private final NotificationService notificationService;
    private final UserServiceClient userServiceClient;
    
    @Value("${payment.high-value-threshold:5000}")
    private BigDecimal highValueThreshold;
    
    @Value("${payment.critical-value-threshold:25000}")
    private BigDecimal criticalValueThreshold;
    
    @Value("${payment.extreme-value-threshold:100000}")
    private BigDecimal extremeValueThreshold;
    
    @Value("${payment.mfa-session-duration-minutes:10}")
    private int sessionDurationMinutes;
    
    @Value("${payment.mfa-challenge-expiry-minutes:5}")
    private int challengeExpiryMinutes;
    
    @Value("${payment.mfa-max-attempts:3}")
    private int maxAttempts;
    
    private static final String MFA_CHALLENGE_PREFIX = "payment:mfa:challenge:";
    private static final String MFA_SESSION_PREFIX = "payment:mfa:session:";
    private static final String MFA_LOCKOUT_PREFIX = "payment:mfa:lockout:";
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    public boolean requiresMfa(BigDecimal amount, String transactionType, String userId) {
        if (amount == null) {
            return false;
        }
        
        if (amount.compareTo(highValueThreshold) < 0) {
            return false;
        }
        
        if (isUserLockedOut(userId)) {
            log.warn("User {} is locked out from MFA attempts", userId);
            return true;
        }
        
        if ("P2P_TRANSFER".equals(transactionType) && amount.compareTo(new BigDecimal("2000")) >= 0) {
            return true;
        }
        
        if ("MERCHANT_PAYMENT".equals(transactionType) && amount.compareTo(highValueThreshold) >= 0) {
            return true;
        }
        
        if ("NFC_PAYMENT".equals(transactionType) && amount.compareTo(new BigDecimal("1000")) >= 0) {
            return true;
        }
        
        return amount.compareTo(highValueThreshold) >= 0;
    }
    
    public MfaRequirement determineMfaRequirement(String userId, String transactionId, 
                                                  BigDecimal amount, String transactionType) {
        log.info("Determining MFA requirement for user {} transaction {} amount {}", 
                userId, transactionId, amount);
        
        if (isUserLockedOut(userId)) {
            return MfaRequirement.builder()
                    .required(true)
                    .blocked(true)
                    .reason("Account temporarily locked due to failed authentication attempts")
                    .build();
        }
        
        MfaLevel level = calculateMfaLevel(amount);
        List<MfaMethod> methods = determineRequiredMethods(level, transactionType);
        
        return MfaRequirement.builder()
                .required(true)
                .level(level)
                .requiredMethods(methods)
                .amount(amount)
                .transactionType(transactionType)
                .sessionDuration(sessionDurationMinutes)
                .message(buildRequirementMessage(level, amount))
                .build();
    }
    
    public MfaChallenge generateMfaChallenge(String userId, String transactionId, MfaRequirement requirement) {
        log.info("Generating MFA challenge for user {} transaction {} level {}", 
                userId, transactionId, requirement.getLevel());
        
        String challengeId = UUID.randomUUID().toString();
        
        Map<MfaMethod, ChallengeData> challenges = new HashMap<>();
        
        for (MfaMethod method : requirement.getRequiredMethods()) {
            ChallengeData challenge = generateMethodChallenge(userId, method, transactionId, requirement);
            challenges.put(method, challenge);
        }
        
        MfaChallengeData challengeData = MfaChallengeData.builder()
                .challengeId(challengeId)
                .userId(userId)
                .transactionId(transactionId)
                .requirement(requirement)
                .challenges(challenges)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(challengeExpiryMinutes))
                .attempts(0)
                .maxAttempts(maxAttempts)
                .build();
        
        String key = MFA_CHALLENGE_PREFIX + challengeId;
        redisTemplate.opsForValue().set(key, challengeData, Duration.ofMinutes(challengeExpiryMinutes));
        
        sendChallengeNotifications(userId, challenges, requirement);
        
        SecurityEvent event = SecurityEvent.builder()
                .eventType("HIGH_VALUE_TRANSACTION_MFA_CHALLENGE")
                .userId(userId)
                .details(String.format("{\"challengeId\":\"%s\",\"transactionId\":\"%s\",\"level\":\"%s\",\"amount\":%.2f}",
                        challengeId, transactionId, requirement.getLevel(), requirement.getAmount()))
                .timestamp(System.currentTimeMillis())
                .build();
        securityEventPublisher.publishSecurityEvent(event);
        
        return MfaChallenge.builder()
                .challengeId(challengeId)
                .requiredMethods(requirement.getRequiredMethods())
                .expiresAt(challengeData.getExpiresAt())
                .riskLevel(requirement.getLevel())
                .build();
    }
    
    public MfaVerificationResult verifyMfa(String challengeId, Map<MfaMethod, String> responses) {
        log.info("Verifying MFA for challenge {}", challengeId);
        
        String key = MFA_CHALLENGE_PREFIX + challengeId;
        MfaChallengeData challengeData = (MfaChallengeData) redisTemplate.opsForValue().get(key);
        
        if (challengeData == null) {
            return MfaVerificationResult.builder()
                    .success(false)
                    .errorCode("CHALLENGE_NOT_FOUND")
                    .errorMessage("Challenge not found or expired")
                    .build();
        }
        
        if (LocalDateTime.now().isAfter(challengeData.getExpiresAt())) {
            redisTemplate.delete(key);
            return MfaVerificationResult.builder()
                    .success(false)
                    .errorCode("CHALLENGE_EXPIRED")
                    .errorMessage("Challenge has expired")
                    .build();
        }
        
        boolean allVerified = true;
        List<String> failedMethods = new ArrayList<>();
        
        for (MfaMethod method : challengeData.getRequirement().getRequiredMethods()) {
            String response = responses.get(method);
            ChallengeData challenge = challengeData.getChallenges().get(method);
            
            if (response == null) {
                allVerified = false;
                failedMethods.add(method.name());
                continue;
            }
            
            boolean verified = verifyMethodResponse(method, challenge, response);
            if (!verified) {
                allVerified = false;
                failedMethods.add(method.name());
            }
        }
        
        if (allVerified) {
            redisTemplate.delete(key);
            
            MfaSession session = createMfaSession(challengeData);
            
            SecurityEvent successEvent = SecurityEvent.builder()
                    .eventType("HIGH_VALUE_TRANSACTION_MFA_SUCCESS")
                    .userId(challengeData.getUserId())
                    .details(String.format("{\"challengeId\":\"%s\",\"transactionId\":\"%s\",\"level\":\"%s\"}",
                            challengeId, challengeData.getTransactionId(), challengeData.getRequirement().getLevel()))
                    .timestamp(System.currentTimeMillis())
                    .build();
            securityEventPublisher.publishSecurityEvent(successEvent);
            
            return MfaVerificationResult.builder()
                    .success(true)
                    .sessionToken(session.getSessionToken())
                    .validUntil(session.getExpiresAt())
                    .processingTime(Duration.between(challengeData.getCreatedAt(), LocalDateTime.now()))
                    .build();
                    
        } else {
            challengeData.setAttempts(challengeData.getAttempts() + 1);
            
            if (challengeData.getAttempts() >= maxAttempts) {
                lockUser(challengeData.getUserId());
                redisTemplate.delete(key);
                
                SecurityEvent lockEvent = SecurityEvent.builder()
                        .eventType("HIGH_VALUE_TRANSACTION_MFA_LOCKOUT")
                        .userId(challengeData.getUserId())
                        .details(String.format("{\"challengeId\":\"%s\",\"reason\":\"MAX_ATTEMPTS\"}", challengeId))
                        .timestamp(System.currentTimeMillis())
                        .build();
                securityEventPublisher.publishSecurityEvent(lockEvent);
                
                return MfaVerificationResult.builder()
                        .success(false)
                        .errorCode("ACCOUNT_LOCKED")
                        .errorMessage("Maximum attempts exceeded. Account temporarily locked.")
                        .accountLocked(true)
                        .build();
                        
            } else {
                redisTemplate.opsForValue().set(key, challengeData,
                        Duration.between(LocalDateTime.now(), challengeData.getExpiresAt()));
                
                return MfaVerificationResult.builder()
                        .success(false)
                        .errorCode("VERIFICATION_FAILED")
                        .errorMessage("Verification failed for: " + String.join(", ", failedMethods))
                        .attemptsRemaining(maxAttempts - challengeData.getAttempts())
                        .failedMethods(failedMethods)
                        .build();
            }
        }
    }
    
    public boolean validateMfaSession(String sessionToken, String transactionId) {
        String key = MFA_SESSION_PREFIX + sessionToken;
        MfaSession session = (MfaSession) redisTemplate.opsForValue().get(key);
        
        if (session == null) {
            return false;
        }
        
        if (LocalDateTime.now().isAfter(session.getExpiresAt())) {
            redisTemplate.delete(key);
            return false;
        }
        
        if (!session.getTransactionId().equals(transactionId)) {
            log.warn("SECURITY: MFA session token {} used for wrong transaction. Expected: {}, Got: {}", 
                    sessionToken, session.getTransactionId(), transactionId);
            return false;
        }
        
        redisTemplate.delete(key);
        return true;
    }
    
    private MfaLevel calculateMfaLevel(BigDecimal amount) {
        if (amount.compareTo(extremeValueThreshold) >= 0) {
            return MfaLevel.EXTREME;
        } else if (amount.compareTo(criticalValueThreshold) >= 0) {
            return MfaLevel.CRITICAL;
        } else if (amount.compareTo(highValueThreshold) >= 0) {
            return MfaLevel.HIGH;
        } else {
            return MfaLevel.MEDIUM;
        }
    }
    
    private List<MfaMethod> determineRequiredMethods(MfaLevel level, String transactionType) {
        List<MfaMethod> methods = new ArrayList<>();
        
        switch (level) {
            case MEDIUM:
                methods.add(MfaMethod.SMS_OTP);
                break;
            case HIGH:
                methods.add(MfaMethod.SMS_OTP);
                methods.add(MfaMethod.EMAIL_OTP);
                break;
            case CRITICAL:
                methods.add(MfaMethod.SMS_OTP);
                methods.add(MfaMethod.EMAIL_OTP);
                methods.add(MfaMethod.BIOMETRIC);
                break;
            case EXTREME:
                methods.add(MfaMethod.SMS_OTP);
                methods.add(MfaMethod.EMAIL_OTP);
                methods.add(MfaMethod.BIOMETRIC);
                methods.add(MfaMethod.SECURITY_QUESTION);
                break;
        }
        
        return methods;
    }
    
    private ChallengeData generateMethodChallenge(String userId, MfaMethod method, 
                                                  String transactionId, MfaRequirement requirement) {
        switch (method) {
            case SMS_OTP:
                String smsCode = generateNumericCode(6);
                sendSmsOtp(userId, smsCode, requirement.getAmount());
                return ChallengeData.builder()
                        .type("SMS_OTP")
                        .challenge("Enter 6-digit SMS code")
                        .expectedResponse(encryptionService.encrypt(smsCode))
                        .expiryMinutes(5)
                        .build();
                        
            case EMAIL_OTP:
                String emailCode = generateNumericCode(8);
                sendEmailOtp(userId, emailCode, requirement);
                return ChallengeData.builder()
                        .type("EMAIL_OTP")
                        .challenge("Enter 8-digit email code")
                        .expectedResponse(encryptionService.encrypt(emailCode))
                        .expiryMinutes(10)
                        .build();
                        
            case BIOMETRIC:
                String biometricChallenge = generateBiometricChallenge();
                return ChallengeData.builder()
                        .type("BIOMETRIC")
                        .challenge("Verify fingerprint or face ID")
                        .expectedResponse(biometricChallenge)
                        .expiryMinutes(5)
                        .build();
                        
            case SECURITY_QUESTION:
                String securityAnswer = getUserSecurityAnswer(userId);
                return ChallengeData.builder()
                        .type("SECURITY_QUESTION")
                        .challenge("Answer your security question")
                        .expectedResponse(encryptionService.encrypt(securityAnswer))
                        .expiryMinutes(10)
                        .build();
                        
            default:
                throw new IllegalArgumentException("Unsupported MFA method: " + method);
        }
    }
    
    private boolean verifyMethodResponse(MfaMethod method, ChallengeData challenge, String response) {
        switch (method) {
            case SMS_OTP:
            case EMAIL_OTP:
            case SECURITY_QUESTION:
                String expected = encryptionService.decrypt(challenge.getExpectedResponse());
                return expected.equals(response);
                
            case BIOMETRIC:
                return verifyBiometric(challenge.getExpectedResponse(), response);
                
            default:
                return false;
        }
    }
    
    private MfaSession createMfaSession(MfaChallengeData challengeData) {
        String sessionToken = "MFA_" + UUID.randomUUID().toString();
        
        MfaSession session = MfaSession.builder()
                .sessionToken(sessionToken)
                .userId(challengeData.getUserId())
                .transactionId(challengeData.getTransactionId())
                .requirement(challengeData.getRequirement())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(sessionDurationMinutes))
                .build();
        
        String key = MFA_SESSION_PREFIX + sessionToken;
        redisTemplate.opsForValue().set(key, session, Duration.ofMinutes(sessionDurationMinutes));
        
        return session;
    }
    
    private String generateNumericCode(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
    
    private String generateBiometricChallenge() {
        return "BIO_" + UUID.randomUUID().toString();
    }
    
    private String getUserSecurityAnswer(String userId) {
        log.debug("SECURITY: Retrieving security answer for user {}", userId);
        
        try {
            // Call user service to get the user's security question and expected answer
            UserSecurityDetails securityDetails = userServiceClient.getUserSecurityDetails(userId);
            
            if (securityDetails == null || securityDetails.getSecurityAnswer() == null) {
                log.warn("SECURITY: No security answer found for user {}", userId);
                throw new SecurityException("User security answer not configured");
            }
            
            // Return the hashed security answer for comparison
            return securityDetails.getSecurityAnswer();
            
        } catch (Exception e) {
            log.error("SECURITY: Failed to retrieve security answer for user {}", userId, e);
            throw new SecurityException("Unable to retrieve security answer", e);
        }
    }
    
    private boolean verifyBiometric(String expectedChallenge, String response) {
        try {
            log.debug("SECURITY: Verifying biometric challenge");
            
            if (expectedChallenge == null || response == null) {
                log.warn("SECURITY: Null biometric challenge or response");
                return false;
            }
            
            // Parse the biometric response which should contain:
            // 1. Challenge ID
            // 2. Encrypted biometric hash
            // 3. Device signature
            BiometricResponse biometricResponse = parseBiometricResponse(response);
            
            if (!expectedChallenge.equals(biometricResponse.getChallengeId())) {
                log.warn("SECURITY: Biometric challenge ID mismatch");
                return false;
            }
            
            // Verify the device signature to ensure it came from a trusted device
            if (!verifyDeviceSignature(biometricResponse)) {
                log.warn("SECURITY: Invalid device signature for biometric verification");
                return false;
            }
            
            // Verify the biometric hash matches the user's stored biometric template
            return verifyBiometricHash(biometricResponse.getUserId(), biometricResponse.getBiometricHash());
            
        } catch (Exception e) {
            log.error("SECURITY: Error during biometric verification", e);
            return false;
        }
    }
    
    private BiometricResponse parseBiometricResponse(String response) {
        try {
            // Expected format: challengeId:encryptedBiometricHash:deviceSignature:userId
            String[] parts = response.split(":");
            if (parts.length != 4) {
                throw new IllegalArgumentException("Invalid biometric response format");
            }
            
            return BiometricResponse.builder()
                    .challengeId(parts[0])
                    .biometricHash(parts[1])
                    .deviceSignature(parts[2])
                    .userId(parts[3])
                    .build();
                    
        } catch (Exception e) {
            log.error("SECURITY: Failed to parse biometric response", e);
            throw new SecurityException("Invalid biometric response format", e);
        }
    }
    
    private boolean verifyDeviceSignature(BiometricResponse response) {
        try {
            // Verify that the response came from a registered and trusted device
            String expectedSignature = generateDeviceSignature(
                response.getChallengeId(), 
                response.getBiometricHash(), 
                response.getUserId()
            );
            
            return encryptionService.verifySignature(
                response.getDeviceSignature(), 
                expectedSignature
            );
            
        } catch (Exception e) {
            log.error("SECURITY: Failed to verify device signature", e);
            return false;
        }
    }
    
    private boolean verifyBiometricHash(String userId, String providedHash) {
        try {
            // Get the user's stored biometric template
            UserSecurityDetails securityDetails = userServiceClient.getUserSecurityDetails(userId);
            
            if (securityDetails == null || !securityDetails.isBiometricConfigured()) {
                log.warn("SECURITY: No biometric configuration found for user {}", userId);
                return false;
            }
            
            // Decrypt the stored biometric template
            String storedBiometricTemplate = encryptionService.decrypt(securityDetails.getBiometricId());
            
            // Compare the hashes using secure comparison to prevent timing attacks
            return encryptionService.secureEquals(storedBiometricTemplate, providedHash);
            
        } catch (Exception e) {
            log.error("SECURITY: Failed to verify biometric hash for user {}", userId, e);
            return false;
        }
    }
    
    private String generateDeviceSignature(String challengeId, String biometricHash, String userId) {
        String payload = challengeId + ":" + biometricHash + ":" + userId;
        return encryptionService.generateHMAC(payload, "biometric-device-key");
    }
    
    private void sendSmsOtp(String userId, String code, BigDecimal amount) {
        String message = String.format(
                "Waqiti Security: Your verification code for $%.2f transaction is %s. Valid for 5 minutes. Do not share.",
                amount, code);
        notificationService.sendSms(userId, message);
    }
    
    private void sendEmailOtp(String userId, String code, MfaRequirement requirement) {
        String subject = "High-Value Transaction Verification Required";
        String body = String.format(
                "A transaction of $%.2f requires verification.\n\n" +
                "Your verification code is: %s\n\n" +
                "This code expires in 10 minutes.\n\n" +
                "If you did not initiate this transaction, contact us immediately.",
                requirement.getAmount(), code);
        notificationService.sendEmail(userId, subject, body);
    }
    
    private void sendChallengeNotifications(String userId, Map<MfaMethod, ChallengeData> challenges,
                                           MfaRequirement requirement) {
        log.info("Sending MFA challenge notifications to user {}", userId);
    }
    
    private String buildRequirementMessage(MfaLevel level, BigDecimal amount) {
        return String.format("High-value transaction ($%.2f) requires %s level verification",
                amount, level.toString());
    }
    
    private boolean isUserLockedOut(String userId) {
        String key = MFA_LOCKOUT_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    private void lockUser(String userId) {
        String key = MFA_LOCKOUT_PREFIX + userId;
        redisTemplate.opsForValue().set(key, true, Duration.ofHours(1));
        
        notificationService.sendEmail(userId, "Account Temporarily Locked",
                "Your account has been temporarily locked due to multiple failed verification attempts. " +
                "It will be automatically unlocked in 1 hour.");
    }
    
    public enum MfaLevel { MEDIUM, HIGH, CRITICAL, EXTREME }
    public enum MfaMethod { SMS_OTP, EMAIL_OTP, BIOMETRIC, SECURITY_QUESTION }
    
    @lombok.Data
    @lombok.Builder
    public static class MfaRequirement {
        private boolean required;
        private boolean blocked;
        private String reason;
        private MfaLevel level;
        private List<MfaMethod> requiredMethods;
        private BigDecimal amount;
        private String transactionType;
        private int sessionDuration;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MfaChallenge {
        private String challengeId;
        private List<MfaMethod> requiredMethods;
        private LocalDateTime expiresAt;
        private MfaLevel riskLevel;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MfaVerificationResult {
        private boolean success;
        private String sessionToken;
        private LocalDateTime validUntil;
        private Duration processingTime;
        private String errorCode;
        private String errorMessage;
        private int attemptsRemaining;
        private List<String> failedMethods;
        private boolean accountLocked;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ChallengeData {
        private String type;
        private String challenge;
        private String expectedResponse;
        private int expiryMinutes;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MfaChallengeData {
        private String challengeId;
        private String userId;
        private String transactionId;
        private MfaRequirement requirement;
        private Map<MfaMethod, ChallengeData> challenges;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private int attempts;
        private int maxAttempts;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MfaSession {
        private String sessionToken;
        private String userId;
        private String transactionId;
        private MfaRequirement requirement;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BiometricResponse {
        private String challengeId;
        private String biometricHash;
        private String deviceSignature;
        private String userId;
    }
}