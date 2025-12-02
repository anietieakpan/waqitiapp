package com.waqiti.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditEvent;
import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.encryption.EncryptionService;
import com.waqiti.common.messaging.MessageService;
import com.waqiti.common.validation.ValidationService;
import com.waqiti.user.domain.User;
import com.waqiti.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive 2FA service for account settings modifications
 * Provides risk-based authentication with multiple verification methods
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSettingsMfaService {

    private static final String SETTINGS_MFA_PREFIX = "settings_mfa:";
    private static final String SETTINGS_SESSION_PREFIX = "settings_session:";
    private static final String FAILED_ATTEMPTS_PREFIX = "settings_failed:";
    
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 30;
    private static final int OTP_LENGTH = 6;
    private static final int CHALLENGE_EXPIRY_MINUTES = 15;
    private static final int SESSION_EXPIRY_MINUTES = 60;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final ValidationService validationService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Determines MFA requirements for account settings modification
     */
    public AccountSettingsMfaRequirement determineSettingsMfaRequirement(String userId, SettingsChangeContext context) {
        try {
            log.info("Determining MFA requirement for user {} settings change: {}", userId, context.getChangeType());
            
            // Check if user is locked
            if (isUserLocked(userId)) {
                return AccountSettingsMfaRequirement.builder()
                    .required(false)
                    .blocked(true)
                    .reason("Account temporarily locked due to failed authentication attempts")
                    .lockoutExpiresAt(getLockoutExpiry(userId))
                    .build();
            }

            User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Assess risk level
            SettingsRiskLevel riskLevel = assessSettingsRisk(context, user);
            
            Set<MfaMethod> requiredMethods = determineRequiredMethods(riskLevel, context, user);
            
            return AccountSettingsMfaRequirement.builder()
                .required(!requiredMethods.isEmpty())
                .blocked(false)
                .riskLevel(riskLevel)
                .requiredMethods(requiredMethods)
                .estimatedTime(calculateEstimatedTime(requiredMethods))
                .reason(buildRequirementReason(riskLevel, context))
                .build();

        } catch (Exception e) {
            log.error("Error determining settings MFA requirement for user {}: {}", userId, e.getMessage(), e);
            auditSettingsMfaFailure(userId, context, "REQUIREMENT_ERROR", e.getMessage());
            throw new RuntimeException("Failed to assess MFA requirements", e);
        }
    }

    /**
     * Generates MFA challenge for settings modification
     */
    @Transactional
    public AccountSettingsMfaChallenge generateSettingsMfaChallenge(String userId, String changeId, 
                                                                   AccountSettingsMfaRequirement requirement) {
        try {
            log.info("Generating settings MFA challenge for user {} change {}", userId, changeId);
            
            String challengeId = UUID.randomUUID().toString();
            
            Map<MfaMethod, String> challenges = new HashMap<>();
            Map<String, Object> metadata = new HashMap<>();
            
            for (MfaMethod method : requirement.getRequiredMethods()) {
                String challengeData = generateMethodChallenge(method, userId, challengeId);
                challenges.put(method, challengeData);
                
                // Send challenge based on method
                sendChallengeNotification(method, userId, challengeData, changeId);
            }
            
            // Store challenge data
            SettingsMfaChallengeData challengeData = SettingsMfaChallengeData.builder()
                .challengeId(challengeId)
                .userId(userId)
                .changeId(changeId)
                .requiredMethods(requirement.getRequiredMethods())
                .challenges(challenges)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(CHALLENGE_EXPIRY_MINUTES))
                .build();
            
            storeChallenge(challengeId, challengeData);
            
            AccountSettingsMfaChallenge challenge = AccountSettingsMfaChallenge.builder()
                .challengeId(challengeId)
                .requiredMethods(new ArrayList<>(requirement.getRequiredMethods()))
                .expiresAt(challengeData.getExpiresAt())
                .estimatedTime(requirement.getEstimatedTime())
                .message(buildChallengeMessage(requirement.getRequiredMethods()))
                .metadata(metadata)
                .build();
            
            auditSettingsMfaEvent(userId, changeId, "CHALLENGE_GENERATED", 
                Map.of("challengeId", challengeId, "methods", requirement.getRequiredMethods()));
            
            return challenge;

        } catch (Exception e) {
            log.error("Error generating settings MFA challenge for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate MFA challenge", e);
        }
    }

    /**
     * Verifies MFA response for settings modification
     */
    @Transactional
    public AccountSettingsMfaVerificationResult verifySettingsMfa(String challengeId, 
                                                                  Map<MfaMethod, String> responses,
                                                                  Map<String, Object> additionalData) {
        try {
            log.info("Verifying settings MFA for challenge {}", challengeId);
            
            SettingsMfaChallengeData challengeData = retrieveChallenge(challengeId);
            if (challengeData == null) {
                return AccountSettingsMfaVerificationResult.builder()
                    .success(false)
                    .errorMessage("Invalid or expired challenge")
                    .build();
            }

            // Check expiry
            if (challengeData.getExpiresAt().isBefore(LocalDateTime.now())) {
                cleanupChallenge(challengeId);
                return AccountSettingsMfaVerificationResult.builder()
                    .success(false)
                    .errorMessage("Challenge expired")
                    .build();
            }

            // Increment attempt counter
            challengeData.setAttempts(challengeData.getAttempts() + 1);
            
            // Check max attempts
            if (challengeData.getAttempts() > MAX_FAILED_ATTEMPTS) {
                lockUser(challengeData.getUserId(), "Exceeded MFA attempts for settings modification");
                cleanupChallenge(challengeId);
                
                return AccountSettingsMfaVerificationResult.builder()
                    .success(false)
                    .accountLocked(true)
                    .errorMessage("Account locked due to too many failed attempts")
                    .build();
            }

            // Verify each method
            Map<MfaMethod, Boolean> verificationResults = new HashMap<>();
            boolean allVerified = true;
            String failureReason = null;

            for (MfaMethod method : challengeData.getRequiredMethods()) {
                String response = responses.get(method);
                if (response == null) {
                    allVerified = false;
                    failureReason = "Missing response for " + method;
                    break;
                }

                boolean methodVerified = verifyMethodResponse(method, response, 
                    challengeData.getChallenges().get(method), challengeData);
                verificationResults.put(method, methodVerified);
                
                if (!methodVerified) {
                    allVerified = false;
                    failureReason = "Invalid " + method + " response";
                    break;
                }
            }

            if (allVerified) {
                // Create secure session for settings modification
                String sessionToken = createSettingsSession(challengeData.getUserId(), challengeData.getChangeId());
                
                cleanupChallenge(challengeId);
                resetFailedAttempts(challengeData.getUserId());
                
                auditSettingsMfaEvent(challengeData.getUserId(), challengeData.getChangeId(), 
                    "VERIFICATION_SUCCESS", Map.of("challengeId", challengeId, "sessionToken", sessionToken));
                
                return AccountSettingsMfaVerificationResult.builder()
                    .success(true)
                    .sessionToken(sessionToken)
                    .sessionExpiresAt(LocalDateTime.now().plusMinutes(SESSION_EXPIRY_MINUTES))
                    .message("Settings modification authorized")
                    .verifiedMethods(verificationResults.keySet())
                    .build();
            } else {
                // Update stored challenge with attempt count
                storeChallenge(challengeId, challengeData);
                incrementFailedAttempts(challengeData.getUserId());
                
                auditSettingsMfaEvent(challengeData.getUserId(), challengeData.getChangeId(), 
                    "VERIFICATION_FAILED", Map.of("challengeId", challengeId, "reason", failureReason));
                
                return AccountSettingsMfaVerificationResult.builder()
                    .success(false)
                    .errorMessage(failureReason)
                    .attemptsRemaining(MAX_FAILED_ATTEMPTS - challengeData.getAttempts())
                    .build();
            }

        } catch (Exception e) {
            log.error("Error verifying settings MFA for challenge {}: {}", challengeId, e.getMessage(), e);
            throw new RuntimeException("Failed to verify MFA", e);
        }
    }

    /**
     * Validates settings modification session
     */
    public boolean validateSettingsSession(String sessionToken, String userId, String changeType) {
        try {
            String sessionKey = SETTINGS_SESSION_PREFIX + sessionToken;
            String sessionData = redisTemplate.opsForValue().get(sessionKey);
            
            if (sessionData == null) {
                return false;
            }

            Map<String, Object> session = objectMapper.readValue(sessionData, Map.class);
            
            return userId.equals(session.get("userId")) && 
                   changeType.equals(session.get("changeType")) &&
                   LocalDateTime.parse((String) session.get("expiresAt")).isAfter(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error validating settings session {}: {}", sessionToken, e.getMessage());
            return false;
        }
    }

    /**
     * Invalidates settings modification session
     */
    public void invalidateSettingsSession(String sessionToken) {
        String sessionKey = SETTINGS_SESSION_PREFIX + sessionToken;
        redisTemplate.delete(sessionKey);
        log.info("Settings session invalidated: {}", sessionToken);
    }

    private SettingsRiskLevel assessSettingsRisk(SettingsChangeContext context, User user) {
        int riskScore = 0;
        
        // Risk factors
        switch (context.getChangeType()) {
            case EMAIL_CHANGE:
            case PHONE_CHANGE:
            case TWO_FACTOR_SETTINGS:
                riskScore += 30;
                break;
            case PASSWORD_CHANGE:
            case SECURITY_QUESTIONS:
                riskScore += 25;
                break;
            case NOTIFICATION_PREFERENCES:
            case PRIVACY_SETTINGS:
                riskScore += 10;
                break;
            case PROFILE_INFORMATION:
                riskScore += 5;
                break;
        }

        // Device and location factors
        if (!context.getDeviceInfo().isTrusted()) {
            riskScore += 20;
        }
        
        if (!context.getLocationInfo().isTrusted()) {
            riskScore += 15;
        }
        
        // Time-based factors
        if (isOutOfHours(context.getTimestamp())) {
            riskScore += 10;
        }
        
        // User behavior factors
        if (hasRecentFailedAttempts(user.getId().toString())) {
            riskScore += 15;
        }

        // Classify risk level
        if (riskScore >= 60) {
            return SettingsRiskLevel.CRITICAL;
        } else if (riskScore >= 40) {
            return SettingsRiskLevel.HIGH;
        } else if (riskScore >= 20) {
            return SettingsRiskLevel.MEDIUM;
        } else {
            return SettingsRiskLevel.LOW;
        }
    }

    private Set<MfaMethod> determineRequiredMethods(SettingsRiskLevel riskLevel, 
                                                   SettingsChangeContext context, User user) {
        Set<MfaMethod> methods = new HashSet<>();
        
        switch (riskLevel) {
            case CRITICAL:
                methods.add(MfaMethod.SMS_OTP);
                methods.add(MfaMethod.EMAIL_OTP);
                methods.add(MfaMethod.BACKUP_CODES);
                if (context.getChangeType() == SettingsChangeType.EMAIL_CHANGE || 
                    context.getChangeType() == SettingsChangeType.PHONE_CHANGE) {
                    methods.add(MfaMethod.DOCUMENT_VERIFICATION);
                }
                break;
            case HIGH:
                methods.add(MfaMethod.SMS_OTP);
                methods.add(MfaMethod.EMAIL_OTP);
                break;
            case MEDIUM:
                if (user.getPhoneVerified()) {
                    methods.add(MfaMethod.SMS_OTP);
                } else {
                    methods.add(MfaMethod.EMAIL_OTP);
                }
                break;
            case LOW:
                // For low-risk changes, still require basic verification
                methods.add(MfaMethod.EMAIL_OTP);
                break;
        }
        
        return methods;
    }

    private String generateMethodChallenge(MfaMethod method, String userId, String challengeId) {
        switch (method) {
            case SMS_OTP:
            case EMAIL_OTP:
                return generateOtp();
            case BACKUP_CODES:
                return "Please provide one of your backup codes";
            case DOCUMENT_VERIFICATION:
                return "Please upload a government-issued photo ID for verification";
            default:
                throw new IllegalArgumentException("Unsupported MFA method: " + method);
        }
    }

    private void sendChallengeNotification(MfaMethod method, String userId, String challengeData, String changeId) {
        try {
            User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            switch (method) {
                case SMS_OTP:
                    if (user.getPhoneVerified()) {
                        messageService.sendSms(user.getPhoneNumber(),
                            String.format("Your Waqiti security code is: %s. Valid for 15 minutes. Change ID: %s", 
                                challengeData, changeId));
                    }
                    break;
                case EMAIL_OTP:
                    messageService.sendEmail(user.getEmail(),
                        "Account Settings Verification Code",
                        String.format("Your verification code is: %s\n\nThis code is valid for 15 minutes.\nChange ID: %s\n\nIf you didn't request this change, please contact support immediately.", 
                            challengeData, changeId));
                    break;
                case BACKUP_CODES:
                    // No notification needed for backup codes
                    break;
                case DOCUMENT_VERIFICATION:
                    messageService.sendEmail(user.getEmail(),
                        "Document Verification Required",
                        String.format("A document verification is required for your account settings change.\nChange ID: %s\n\nPlease upload a government-issued photo ID through the secure portal.", 
                            changeId));
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to send challenge notification for method {} to user {}: {}", 
                method, userId, e.getMessage());
        }
    }

    private boolean verifyMethodResponse(MfaMethod method, String response, String expectedResponse, 
                                       SettingsMfaChallengeData challengeData) {
        switch (method) {
            case SMS_OTP:
            case EMAIL_OTP:
                return response.equals(expectedResponse);
            case BACKUP_CODES:
                return verifyBackupCode(challengeData.getUserId(), response);
            case DOCUMENT_VERIFICATION:
                return verifyDocumentUpload(challengeData.getUserId(), response);
            default:
                return false;
        }
    }

    private boolean verifyBackupCode(String userId, String code) {
        // This would verify against stored backup codes
        // Implementation would check database for valid backup codes
        return validationService.validateBackupCode(userId, code);
    }

    private boolean verifyDocumentUpload(String userId, String documentReference) {
        // This would verify document upload and perform OCR/verification
        // Implementation would integrate with document verification service
        return validationService.validateDocumentUpload(userId, documentReference);
    }

    private String createSettingsSession(String userId, String changeId) {
        String sessionToken = UUID.randomUUID().toString();
        String sessionKey = SETTINGS_SESSION_PREFIX + sessionToken;
        
        Map<String, Object> sessionData = Map.of(
            "userId", userId,
            "changeId", changeId,
            "createdAt", LocalDateTime.now().toString(),
            "expiresAt", LocalDateTime.now().plusMinutes(SESSION_EXPIRY_MINUTES).toString()
        );
        
        try {
            String sessionJson = objectMapper.writeValueAsString(sessionData);
            redisTemplate.opsForValue().set(sessionKey, sessionJson, SESSION_EXPIRY_MINUTES, TimeUnit.MINUTES);
            return sessionToken;
        } catch (Exception e) {
            log.error("Failed to create settings session for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to create session", e);
        }
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }

    private void storeChallenge(String challengeId, SettingsMfaChallengeData challengeData) {
        try {
            String challengeKey = SETTINGS_MFA_PREFIX + challengeId;
            String challengeJson = objectMapper.writeValueAsString(challengeData);
            redisTemplate.opsForValue().set(challengeKey, challengeJson, 
                CHALLENGE_EXPIRY_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Failed to store challenge {}: {}", challengeId, e.getMessage());
            throw new RuntimeException("Failed to store challenge", e);
        }
    }

    private SettingsMfaChallengeData retrieveChallenge(String challengeId) {
        try {
            String challengeKey = SETTINGS_MFA_PREFIX + challengeId;
            String challengeJson = redisTemplate.opsForValue().get(challengeKey);
            
            if (challengeJson == null) {
                return null;
            }
            
            return objectMapper.readValue(challengeJson, SettingsMfaChallengeData.class);
        } catch (Exception e) {
            log.error("Failed to retrieve challenge {}: {}", challengeId, e.getMessage());
            return null;
        }
    }

    private void cleanupChallenge(String challengeId) {
        String challengeKey = SETTINGS_MFA_PREFIX + challengeId;
        redisTemplate.delete(challengeKey);
    }

    private boolean isUserLocked(String userId) {
        String lockKey = FAILED_ATTEMPTS_PREFIX + userId + ":locked";
        return redisTemplate.hasKey(lockKey);
    }

    private LocalDateTime getLockoutExpiry(String userId) {
        String lockKey = FAILED_ATTEMPTS_PREFIX + userId + ":locked";
        String expiryStr = redisTemplate.opsForValue().get(lockKey);
        if (expiryStr != null) {
            return LocalDateTime.parse(expiryStr);
        }
        return LocalDateTime.now();
    }

    private void lockUser(String userId, String reason) {
        String lockKey = FAILED_ATTEMPTS_PREFIX + userId + ":locked";
        LocalDateTime lockExpiry = LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES);
        redisTemplate.opsForValue().set(lockKey, lockExpiry.toString(), 
            LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
        
        auditSettingsMfaEvent(userId, null, "ACCOUNT_LOCKED", 
            Map.of("reason", reason, "lockoutExpiry", lockExpiry));
        
        log.warn("User {} locked for settings MFA: {}", userId, reason);
    }

    private void incrementFailedAttempts(String userId) {
        String attemptsKey = FAILED_ATTEMPTS_PREFIX + userId + ":count";
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        ops.increment(attemptsKey);
        redisTemplate.expire(attemptsKey, LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
    }

    private void resetFailedAttempts(String userId) {
        String attemptsKey = FAILED_ATTEMPTS_PREFIX + userId + ":count";
        redisTemplate.delete(attemptsKey);
    }

    private boolean hasRecentFailedAttempts(String userId) {
        String attemptsKey = FAILED_ATTEMPTS_PREFIX + userId + ":count";
        String attempts = redisTemplate.opsForValue().get(attemptsKey);
        return attempts != null && Integer.parseInt(attempts) > 0;
    }

    private boolean isOutOfHours(LocalDateTime timestamp) {
        int hour = timestamp.getHour();
        return hour < 6 || hour > 22; // Consider 6 AM to 10 PM as normal hours
    }

    private int calculateEstimatedTime(Set<MfaMethod> methods) {
        return methods.size() * 2; // Estimate 2 minutes per method
    }

    private String buildRequirementReason(SettingsRiskLevel riskLevel, SettingsChangeContext context) {
        return String.format("Settings change '%s' requires %s security verification", 
            context.getChangeType(), riskLevel.toString().toLowerCase());
    }

    private String buildChallengeMessage(Set<MfaMethod> methods) {
        if (methods.size() == 1) {
            MfaMethod method = methods.iterator().next();
            return String.format("Please complete %s verification to proceed with settings change", 
                method.getDisplayName());
        } else {
            return String.format("Please complete %d verification steps to proceed with settings change", 
                methods.size());
        }
    }

    private void auditSettingsMfaEvent(String userId, String changeId, String eventType, 
                                     Map<String, Object> details) {
        try {
            AuditEvent event = AuditEvent.builder()
                .eventType(AuditEventType.AUTHENTICATION)
                .userId(userId)
                .resourceId(changeId)
                .resourceType("ACCOUNT_SETTINGS")
                .action("SETTINGS_MFA_" + eventType)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
            
            auditService.logEvent(event);
        } catch (Exception e) {
            log.error("Failed to audit settings MFA event for user {}: {}", userId, e.getMessage());
        }
    }

    private void auditSettingsMfaFailure(String userId, SettingsChangeContext context, 
                                       String errorType, String errorMessage) {
        auditSettingsMfaEvent(userId, context != null ? context.getChangeId() : null, 
            "FAILURE", Map.of("errorType", errorType, "errorMessage", errorMessage));
    }

    // Data classes for MFA operations
    
    public enum SettingsRiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum SettingsChangeType {
        EMAIL_CHANGE, PHONE_CHANGE, PASSWORD_CHANGE, TWO_FACTOR_SETTINGS,
        NOTIFICATION_PREFERENCES, PRIVACY_SETTINGS, SECURITY_QUESTIONS, PROFILE_INFORMATION
    }

    public enum MfaMethod {
        SMS_OTP("SMS verification code"),
        EMAIL_OTP("Email verification code"),
        BACKUP_CODES("Backup verification code"),
        DOCUMENT_VERIFICATION("Document verification");

        private final String displayName;

        MfaMethod(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class SettingsChangeContext {
        private String changeId;
        private SettingsChangeType changeType;
        private LocalDateTime timestamp;
        private DeviceInfo deviceInfo;
        private LocationInfo locationInfo;
        private Map<String, Object> changeDetails;
    }

    @lombok.Data
    @lombok.Builder
    public static class DeviceInfo {
        private String deviceId;
        private String deviceType;
        private String browserInfo;
        private boolean trusted;
        private LocalDateTime lastSeen;
    }

    @lombok.Data
    @lombok.Builder
    public static class LocationInfo {
        private String ipAddress;
        private String countryCode;
        private String city;
        private boolean trusted;
        private boolean vpnDetected;
    }

    @lombok.Data
    @lombok.Builder
    public static class AccountSettingsMfaRequirement {
        private boolean required;
        private boolean blocked;
        private SettingsRiskLevel riskLevel;
        private Set<MfaMethod> requiredMethods;
        private String reason;
        private int estimatedTime;
        private LocalDateTime lockoutExpiresAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class AccountSettingsMfaChallenge {
        private String challengeId;
        private List<MfaMethod> requiredMethods;
        private LocalDateTime expiresAt;
        private int estimatedTime;
        private String message;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class AccountSettingsMfaVerificationResult {
        private boolean success;
        private boolean accountLocked;
        private String sessionToken;
        private LocalDateTime sessionExpiresAt;
        private String message;
        private String errorMessage;
        private Set<MfaMethod> verifiedMethods;
        private int attemptsRemaining;
    }

    @lombok.Data
    @lombok.Builder
    private static class SettingsMfaChallengeData {
        private String challengeId;
        private String userId;
        private String changeId;
        private Set<MfaMethod> requiredMethods;
        private Map<MfaMethod, String> challenges;
        private int attempts;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }
}