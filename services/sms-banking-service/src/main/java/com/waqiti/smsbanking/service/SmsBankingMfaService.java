package com.waqiti.smsbanking.service;

import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.common.security.EncryptionService;
import com.waqiti.common.events.SecurityEventPublisher;
import com.waqiti.common.events.SecurityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * SMS Banking Multi-Factor Authentication Service
 * 
 * Provides enhanced security for SMS/USSD banking operations with:
 * - Mandatory 2FA for all financial transactions
 * - Risk-based step-up authentication
 * - Anti-fraud measures for SMS banking
 * - Session management with timeout
 * - Device fingerprinting for SMS banking
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsBankingMfaService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SecurityEventPublisher securityEventPublisher;
    private final EncryptionService encryptionService;
    
    @Value("${sms-banking.mfa.code-length:6}")
    private int mfaCodeLength;
    
    @Value("${sms-banking.mfa.code-expiry-minutes:5}")
    private int mfaCodeExpiryMinutes;
    
    @Value("${sms-banking.mfa.max-attempts:3}")
    private int maxMfaAttempts;
    
    @Value("${sms-banking.mfa.lockout-minutes:15}")
    private int lockoutMinutes;
    
    @Value("${sms-banking.session.timeout-minutes:10}")
    private int sessionTimeoutMinutes;
    
    private static final String MFA_CODE_PREFIX = "sms-banking:mfa:";
    private static final String SESSION_PREFIX = "sms-banking:session:";
    private static final String ATTEMPTS_PREFIX = "sms-banking:attempts:";
    private static final String HIGH_VALUE_THRESHOLD_PREFIX = "sms-banking:threshold:";
    
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, TransactionRisk> riskCache = new ConcurrentHashMap<>();
    
    // High-value transaction patterns that require mandatory 2FA
    private final Pattern TRANSFER_PATTERN = Pattern.compile("(?i)^(SEND|TRANSFER|PAY)\\s+.*");
    private final Pattern LOAN_PATTERN = Pattern.compile("(?i)^(LOAN\\s+PAY|LOANPAY)\\s+.*");
    private final Pattern HIGH_AMOUNT_PATTERN = Pattern.compile(".*\\b(\\d+\\.?\\d*)\\b.*");
    
    /**
     * Validate SMS banking command and determine if 2FA is required
     */
    public MfaValidationResult validateSmsCommand(String phoneNumber, String command, String verificationCode) {
        log.debug("Validating SMS command from {}: {}", phoneNumber, command);
        
        // Check if user is locked out
        if (isUserLockedOut(phoneNumber)) {
            return MfaValidationResult.builder()
                .valid(false)
                .requiresMfa(true)
                .errorCode("USER_LOCKED")
                .errorMessage("Too many failed attempts. Please try again later.")
                .lockoutTimeRemaining(getRemainingLockoutTime(phoneNumber))
                .build();
        }
        
        // Determine transaction risk level
        TransactionRisk riskLevel = assessTransactionRisk(phoneNumber, command);
        
        // All financial operations require 2FA
        boolean requiresMfa = isFinancialTransaction(command) || riskLevel == TransactionRisk.HIGH;
        
        if (requiresMfa) {
            if (verificationCode == null || verificationCode.trim().isEmpty()) {
                // Generate and send 2FA code
                String codeId = generateMfaCode(phoneNumber, command);
                return MfaValidationResult.builder()
                    .valid(false)
                    .requiresMfa(true)
                    .codeId(codeId)
                    .message("2FA required. Code sent to your registered number.")
                    .expiresInMinutes(mfaCodeExpiryMinutes)
                    .build();
            } else {
                // Verify 2FA code
                return verifyMfaCode(phoneNumber, command, verificationCode);
            }
        } else {
            // Low-risk operations like balance inquiry, help
            return MfaValidationResult.builder()
                .valid(true)
                .requiresMfa(false)
                .sessionToken(createSession(phoneNumber))
                .build();
        }
    }
    
    /**
     * Validate USSD session with risk-based 2FA
     */
    public UssdMfaResult validateUssdSession(String sessionId, String phoneNumber, 
                                           String input, String mfaCode) {
        log.debug("Validating USSD session {} from {}", sessionId, phoneNumber);
        
        // Get or create session state
        UssdSessionState sessionState = getUssdSessionState(sessionId, phoneNumber);
        
        // Check if this input requires step-up authentication
        boolean requiresStepUp = requiresStepUpAuth(sessionState, input);
        
        if (requiresStepUp) {
            if (mfaCode == null || mfaCode.trim().isEmpty()) {
                // Generate and send step-up code
                String codeId = generateUssdMfaCode(sessionId, phoneNumber, input);
                sessionState.setPendingOperation(input);
                saveUssdSessionState(sessionId, sessionState);
                
                return UssdMfaResult.builder()
                    .continueSession(true)
                    .requiresMfa(true)
                    .message("Security verification required. Enter the code sent to your phone.")
                    .menuOptions("Enter 6-digit code or 0 to cancel")
                    .codeId(codeId)
                    .build();
            } else {
                // Verify step-up code
                return verifyUssdMfaCode(sessionId, phoneNumber, mfaCode, sessionState);
            }
        } else {
            // Process regular USSD input
            sessionState.setLastActivity(LocalDateTime.now());
            saveUssdSessionState(sessionId, sessionState);
            
            return UssdMfaResult.builder()
                .continueSession(true)
                .requiresMfa(false)
                .sessionState(sessionState)
                .build();
        }
    }
    
    /**
     * Generate MFA code for SMS banking
     */
    @RateLimited(keyType = RateLimited.KeyType.PHONE, capacity = 3, refillTokens = 3, refillPeriodMinutes = 15)
    private String generateMfaCode(String phoneNumber, String command) {
        String code = generateSecureCode();
        String codeId = "sms_mfa_" + System.currentTimeMillis();
        
        // Store code with metadata
        MfaCodeData codeData = MfaCodeData.builder()
            .code(encryptionService.encrypt(code))
            .phoneNumber(phoneNumber)
            .command(command)
            .generatedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(mfaCodeExpiryMinutes))
            .attempts(0)
            .build();
        
        String key = MFA_CODE_PREFIX + codeId;
        redisTemplate.opsForValue().set(key, codeData, Duration.ofMinutes(mfaCodeExpiryMinutes));
        
        // Send SMS code (integrate with SMS service)
        sendMfaCodeSms(phoneNumber, code, command);
        
        // Log security event
        SecurityEvent event = SecurityEvent.builder()
            .eventType("SMS_BANKING_MFA_CODE_GENERATED")
            .phoneNumber(phoneNumber)
            .details(String.format("{\"codeId\":\"%s\",\"command\":\"%s\"}", codeId, maskSensitiveCommand(command)))
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(event);
        
        log.info("MFA code generated for SMS banking: {}", codeId);
        return codeId;
    }
    
    /**
     * Verify MFA code for SMS banking
     */
    private MfaValidationResult verifyMfaCode(String phoneNumber, String command, String verificationCode) {
        try {
            // Find the most recent code for this phone number
            String codeId = findMostRecentCodeId(phoneNumber);
            if (codeId == null) {
                recordFailedAttempt(phoneNumber, "NO_CODE_FOUND");
                return MfaValidationResult.builder()
                    .valid(false)
                    .errorCode("NO_ACTIVE_CODE")
                    .errorMessage("No active verification code found. Request a new code.")
                    .build();
            }
            
            String key = MFA_CODE_PREFIX + codeId;
            MfaCodeData codeData = (MfaCodeData) redisTemplate.opsForValue().get(key);
            
            if (codeData == null) {
                recordFailedAttempt(phoneNumber, "CODE_EXPIRED");
                return MfaValidationResult.builder()
                    .valid(false)
                    .errorCode("CODE_EXPIRED")
                    .errorMessage("Verification code expired. Request a new code.")
                    .build();
            }
            
            // Decrypt and verify code
            String actualCode = encryptionService.decrypt(codeData.getCode());
            
            if (!actualCode.equals(verificationCode)) {
                codeData.setAttempts(codeData.getAttempts() + 1);
                
                if (codeData.getAttempts() >= maxMfaAttempts) {
                    // Remove code and lock user temporarily
                    redisTemplate.delete(key);
                    lockUser(phoneNumber);
                    
                    SecurityEvent event = SecurityEvent.builder()
                        .eventType("SMS_BANKING_MFA_MAX_ATTEMPTS_EXCEEDED")
                        .phoneNumber(phoneNumber)
                        .details(String.format("{\"codeId\":\"%s\",\"attempts\":%d}", codeId, codeData.getAttempts()))
                        .timestamp(System.currentTimeMillis())
                        .build();
                    securityEventPublisher.publishSecurityEvent(event);
                    
                    return MfaValidationResult.builder()
                        .valid(false)
                        .errorCode("MAX_ATTEMPTS_EXCEEDED")
                        .errorMessage("Too many failed attempts. Account temporarily locked.")
                        .build();
                } else {
                    // Update attempt count
                    redisTemplate.opsForValue().set(key, codeData, Duration.ofMinutes(mfaCodeExpiryMinutes));
                    recordFailedAttempt(phoneNumber, "INVALID_CODE");
                    
                    return MfaValidationResult.builder()
                        .valid(false)
                        .errorCode("INVALID_CODE")
                        .errorMessage(String.format("Invalid code. %d attempts remaining.", 
                            maxMfaAttempts - codeData.getAttempts()))
                        .attemptsRemaining(maxMfaAttempts - codeData.getAttempts())
                        .build();
                }
            }
            
            // Code verified successfully
            redisTemplate.delete(key); // Remove used code
            clearFailedAttempts(phoneNumber);
            
            // Create authenticated session
            String sessionToken = createSession(phoneNumber);
            
            SecurityEvent successEvent = SecurityEvent.builder()
                .eventType("SMS_BANKING_MFA_SUCCESS")
                .phoneNumber(phoneNumber)
                .details(String.format("{\"codeId\":\"%s\",\"command\":\"%s\"}", codeId, maskSensitiveCommand(command)))
                .timestamp(System.currentTimeMillis())
                .build();
            securityEventPublisher.publishSecurityEvent(successEvent);
            
            log.info("SMS banking MFA verification successful for {}", phoneNumber);
            
            return MfaValidationResult.builder()
                .valid(true)
                .requiresMfa(false)
                .sessionToken(sessionToken)
                .message("Verification successful. Transaction authorized.")
                .build();
            
        } catch (Exception e) {
            log.error("Error verifying MFA code for SMS banking", e);
            recordFailedAttempt(phoneNumber, "SYSTEM_ERROR");
            
            return MfaValidationResult.builder()
                .valid(false)
                .errorCode("VERIFICATION_ERROR")
                .errorMessage("Verification failed. Please try again.")
                .build();
        }
    }
    
    /**
     * Generate step-up authentication code for USSD
     */
    private String generateUssdMfaCode(String sessionId, String phoneNumber, String input) {
        String code = generateSecureCode();
        String codeId = "ussd_mfa_" + sessionId + "_" + System.currentTimeMillis();
        
        MfaCodeData codeData = MfaCodeData.builder()
            .code(encryptionService.encrypt(code))
            .phoneNumber(phoneNumber)
            .command(input)
            .sessionId(sessionId)
            .generatedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(mfaCodeExpiryMinutes))
            .attempts(0)
            .build();
        
        String key = MFA_CODE_PREFIX + codeId;
        redisTemplate.opsForValue().set(key, codeData, Duration.ofMinutes(mfaCodeExpiryMinutes));
        
        // Send SMS code
        sendMfaCodeSms(phoneNumber, code, "USSD Step-up Authentication");
        
        log.info("USSD step-up MFA code generated: {}", codeId);
        return codeId;
    }
    
    /**
     * Verify step-up authentication code for USSD
     */
    private UssdMfaResult verifyUssdMfaCode(String sessionId, String phoneNumber, 
                                         String mfaCode, UssdSessionState sessionState) {
        try {
            // Find the most recent USSD code for this session
            String codeId = findMostRecentUssdCodeId(sessionId);
            if (codeId == null) {
                return UssdMfaResult.builder()
                    .continueSession(true)
                    .requiresMfa(true)
                    .message("No active verification code found. Request a new code.")
                    .menuOptions("1. Request new code\n0. Cancel")
                    .build();
            }
            
            String key = MFA_CODE_PREFIX + codeId;
            MfaCodeData codeData = (MfaCodeData) redisTemplate.opsForValue().get(key);
            
            if (codeData == null) {
                return UssdMfaResult.builder()
                    .continueSession(true)
                    .requiresMfa(true)
                    .message("Verification code expired.")
                    .menuOptions("1. Request new code\n0. Cancel")
                    .build();
            }
            
            String actualCode = encryptionService.decrypt(codeData.getCode());
            
            if (!actualCode.equals(mfaCode)) {
                codeData.setAttempts(codeData.getAttempts() + 1);
                redisTemplate.opsForValue().set(key, codeData, Duration.ofMinutes(mfaCodeExpiryMinutes));
                
                return UssdMfaResult.builder()
                    .continueSession(true)
                    .requiresMfa(true)
                    .message(String.format("Invalid code. %d attempts remaining.", 
                        maxMfaAttempts - codeData.getAttempts()))
                    .menuOptions("Enter correct code or 0 to cancel")
                    .attemptsRemaining(maxMfaAttempts - codeData.getAttempts())
                    .build();
            }
            
            // Code verified - process pending operation
            redisTemplate.delete(key);
            sessionState.setAuthenticatedOperation(sessionState.getPendingOperation());
            sessionState.setPendingOperation(null);
            sessionState.setLastActivity(LocalDateTime.now());
            saveUssdSessionState(sessionId, sessionState);
            
            log.info("USSD step-up authentication successful for session {}", sessionId);
            
            return UssdMfaResult.builder()
                .continueSession(true)
                .requiresMfa(false)
                .message("Authentication successful. Processing your request...")
                .sessionState(sessionState)
                .build();
                
        } catch (Exception e) {
            log.error("Error verifying USSD MFA code", e);
            return UssdMfaResult.builder()
                .continueSession(true)
                .requiresMfa(true)
                .message("Verification failed. Please try again.")
                .menuOptions("Enter code or 0 to cancel")
                .build();
        }
    }
    
    // Helper methods
    
    private boolean isFinancialTransaction(String command) {
        return TRANSFER_PATTERN.matcher(command).matches() || 
               LOAN_PATTERN.matcher(command).matches() ||
               command.toUpperCase().contains("PAY") ||
               command.toUpperCase().contains("SEND");
    }
    
    private TransactionRisk assessTransactionRisk(String phoneNumber, String command) {
        try {
            // Check for high-value amounts
            if (HIGH_AMOUNT_PATTERN.matcher(command).find()) {
                String[] parts = command.split("\\s+");
                for (String part : parts) {
                    try {
                        double amount = Double.parseDouble(part);
                        if (amount > 1000) { // High-value threshold
                            return TransactionRisk.HIGH;
                        }
                    } catch (NumberFormatException ignored) {
                        // Not a number, continue
                    }
                }
            }
            
            // Check transaction frequency
            String frequencyKey = "sms-banking:frequency:" + phoneNumber;
            Integer transactionCount = (Integer) redisTemplate.opsForValue().get(frequencyKey);
            if (transactionCount != null && transactionCount > 10) { // More than 10 transactions per hour
                return TransactionRisk.HIGH;
            }
            
            // Check for unusual patterns
            if (command.length() > 200 || command.contains("URGENT") || command.contains("EMERGENCY")) {
                return TransactionRisk.MEDIUM;
            }
            
            return TransactionRisk.LOW;
            
        } catch (Exception e) {
            log.warn("Error assessing transaction risk, defaulting to HIGH", e);
            return TransactionRisk.HIGH; // Fail safe
        }
    }
    
    private boolean requiresStepUpAuth(UssdSessionState sessionState, String input) {
        // Step-up required for financial operations
        return input.matches("(?i).*(transfer|send|pay|loan|withdraw).*") ||
               input.matches(".*[1-9]\\d{2,}.*"); // Amounts over 100
    }
    
    private String generateSecureCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < mfaCodeLength; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
    
    private void sendMfaCodeSms(String phoneNumber, String code, String context) {
        // Integration point with SMS service
        String message = String.format("Waqiti Security Code: %s\nFor: %s\nExpires in %d minutes. Do not share.", 
            code, context, mfaCodeExpiryMinutes);
        
        // Integrate with SMS service through notification service
        try {
            notificationServiceClient.sendSms(phoneNumber, message, "HIGH");
            log.info("MFA code SMS sent successfully to {} for {}", phoneNumber, context);
        } catch (Exception e) {
            log.error("Failed to send MFA code SMS to {} for {}", phoneNumber, context, e);
            throw new RuntimeException("Failed to send MFA code", e);
        }
    }
    
    private String createSession(String phoneNumber) {
        String sessionToken = "sms_session_" + System.currentTimeMillis() + "_" + secureRandom.nextInt(10000);
        String key = SESSION_PREFIX + sessionToken;
        
        SessionData sessionData = SessionData.builder()
            .phoneNumber(phoneNumber)
            .createdAt(LocalDateTime.now())
            .lastActivity(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(sessionTimeoutMinutes))
            .build();
        
        redisTemplate.opsForValue().set(key, sessionData, Duration.ofMinutes(sessionTimeoutMinutes));
        return sessionToken;
    }
    
    private String maskSensitiveCommand(String command) {
        // Mask PIN and amount information in logs
        return command.replaceAll("\\b\\d{4,}\\b", "****")
                     .replaceAll("\\b\\d+\\.\\d{2}\\b", "***.**");
    }
    
    private void recordFailedAttempt(String phoneNumber, String reason) {
        String key = ATTEMPTS_PREFIX + phoneNumber;
        Integer attempts = (Integer) redisTemplate.opsForValue().get(key);
        attempts = (attempts == null) ? 1 : attempts + 1;
        
        redisTemplate.opsForValue().set(key, attempts, Duration.ofMinutes(lockoutMinutes));
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType("SMS_BANKING_FAILED_ATTEMPT")
            .phoneNumber(phoneNumber)
            .details(String.format("{\"reason\":\"%s\",\"attempts\":%d}", reason, attempts))
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(event);
    }
    
    private void clearFailedAttempts(String phoneNumber) {
        String key = ATTEMPTS_PREFIX + phoneNumber;
        redisTemplate.delete(key);
    }
    
    private void lockUser(String phoneNumber) {
        String key = ATTEMPTS_PREFIX + phoneNumber + ":locked";
        redisTemplate.opsForValue().set(key, true, Duration.ofMinutes(lockoutMinutes));
    }
    
    private boolean isUserLockedOut(String phoneNumber) {
        String key = ATTEMPTS_PREFIX + phoneNumber + ":locked";
        return Boolean.TRUE.equals(redisTemplate.opsForValue().get(key));
    }
    
    private Duration getRemainingLockoutTime(String phoneNumber) {
        String key = ATTEMPTS_PREFIX + phoneNumber + ":locked";
        Long ttl = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
        return Duration.ofSeconds(ttl != null ? ttl : 0);
    }
    
    private String findMostRecentCodeId(String phoneNumber) {
        // Simplified implementation - in production, use more sophisticated lookup
        String pattern = MFA_CODE_PREFIX + "sms_mfa_*";
        return findMostRecentCode(pattern, phoneNumber);
    }
    
    private String findMostRecentUssdCodeId(String sessionId) {
        String pattern = MFA_CODE_PREFIX + "ussd_mfa_" + sessionId + "_*";
        return findMostRecentCode(pattern, null);
    }
    
    private String findMostRecentCode(String pattern, String phoneNumber) {
        try {
            // Use Redis SCAN to find matching keys
            Set<String> matchingKeys = redisTemplate.keys(pattern);
            
            if (matchingKeys == null || matchingKeys.isEmpty()) {
                return null;
            }
            
            // Find the most recent by timestamp in key
            return matchingKeys.stream()
                .filter(key -> {
                    Object value = redisTemplate.opsForValue().get(key);
                    return value != null; // Only consider keys with values
                })
                .max(String::compareTo) // Latest timestamp will be max
                .orElse(null);
                
        } catch (Exception e) {
            log.error("Failed to find most recent code for pattern: {}", pattern, e);
            return null;
        }
    }
    
    private UssdSessionState getUssdSessionState(String sessionId, String phoneNumber) {
        String key = "ussd:session:" + sessionId;
        UssdSessionState state = (UssdSessionState) redisTemplate.opsForValue().get(key);
        
        if (state == null) {
            state = UssdSessionState.builder()
                .sessionId(sessionId)
                .phoneNumber(phoneNumber)
                .createdAt(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .build();
        }
        
        return state;
    }
    
    private void saveUssdSessionState(String sessionId, UssdSessionState state) {
        String key = "ussd:session:" + sessionId;
        redisTemplate.opsForValue().set(key, state, Duration.ofMinutes(sessionTimeoutMinutes));
    }
    
    // Data classes
    
    @lombok.Data
    @lombok.Builder
    public static class MfaValidationResult {
        private boolean valid;
        private boolean requiresMfa;
        private String codeId;
        private String sessionToken;
        private String message;
        private String errorCode;
        private String errorMessage;
        private int expiresInMinutes;
        private int attemptsRemaining;
        private Duration lockoutTimeRemaining;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class UssdMfaResult {
        private boolean continueSession;
        private boolean requiresMfa;
        private String message;
        private String menuOptions;
        private String codeId;
        private int attemptsRemaining;
        private UssdSessionState sessionState;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MfaCodeData {
        private String code;
        private String phoneNumber;
        private String command;
        private String sessionId;
        private LocalDateTime generatedAt;
        private LocalDateTime expiresAt;
        private int attempts;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SessionData {
        private String phoneNumber;
        private LocalDateTime createdAt;
        private LocalDateTime lastActivity;
        private LocalDateTime expiresAt;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class UssdSessionState {
        private String sessionId;
        private String phoneNumber;
        private LocalDateTime createdAt;
        private LocalDateTime lastActivity;
        private String currentMenu;
        private String pendingOperation;
        private String authenticatedOperation;
        private Map<String, String> sessionData;
    }
    
    public enum TransactionRisk {
        LOW, MEDIUM, HIGH
    }
}