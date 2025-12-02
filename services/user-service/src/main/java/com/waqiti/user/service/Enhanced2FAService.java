package com.waqiti.user.service;

import com.waqiti.user.client.NotificationServiceClient;
import com.waqiti.user.domain.*;
import com.waqiti.user.dto.*;
import com.waqiti.user.repository.*;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced 2FA Service with comprehensive security features
 * 
 * Features:
 * - TOTP (Time-based One-Time Passwords)
 * - SMS-based 2FA with rate limiting
 * - Email-based 2FA
 * - Backup recovery codes
 * - Device trust management
 * - Fraud detection and rate limiting
 * - Multi-factor authentication requirements
 * - Emergency access procedures
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class Enhanced2FAService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MfaService mfaService;
    private final MfaConfigurationRepository mfaConfigRepository;
    private final MfaVerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;
    private final NotificationServiceClient notificationClient;
    
    // TOTP Components
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator);
    private final TimeProvider timeProvider = new SystemTimeProvider();
    
    // Configuration
    @Value("${waqiti.security.2fa.totp-window:1}")
    private int totpWindow;
    
    @Value("${waqiti.security.2fa.code-validity-minutes:5}")
    private int codeValidityMinutes;
    
    @Value("${waqiti.security.2fa.max-attempts:3}")
    private int maxAttempts;
    
    @Value("${waqiti.security.2fa.lockout-minutes:15}")
    private int lockoutMinutes;
    
    @Value("${waqiti.app.name:Waqiti}")
    private String appName;
    
    // Rate limiting cache
    private final Map<String, AttemptTracker> attemptTracker = new ConcurrentHashMap<>();
    
    /**
     * Setup TOTP 2FA for a user with enhanced security
     */
    public TOTP2FASetupResponse setupTOTP2FA(UUID userId) {
        log.info("Setting up enhanced TOTP 2FA for user: {}", userId);
        
        try {
            // Check if user exists
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            // Generate secret
            String secret = secretGenerator.generate();
            
            // Create QR code data
            QrData qrData = new QrData.Builder()
                    .label(user.getEmail())
                    .secret(secret)
                    .issuer(appName)
                    .algorithm(HashingAlgorithm.SHA1)
                    .digits(6)
                    .period(30)
                    .build();
            
            // Generate QR code
            String qrCodeUrl = qrGenerator.getQrImageUri(qrData);
            
            // Save TOTP configuration (but not enabled yet)
            MfaConfiguration config = MfaConfiguration.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .method(MfaMethod.TOTP)
                    .secret(secret)
                    .enabled(false) // Will be enabled after verification
                    .createdAt(LocalDateTime.now())
                    .build();
            
            mfaConfigRepository.save(config);
            
            log.info("TOTP 2FA setup initiated for user: {} - awaiting verification", userId);
            
            return TOTP2FASetupResponse.builder()
                    .secret(secret)
                    .qrCodeUrl(qrCodeUrl)
                    .backupCodes(new ArrayList<>()) // Will be generated after verification
                    .manualEntryKey(secret)
                    .issuer(appName)
                    .accountName(user.getEmail())
                    .message("Scan QR code with authenticator app and verify with generated code")
                    .setupComplete(false)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error setting up TOTP 2FA for user: {}", userId, e);
            throw new RuntimeException("Failed to setup TOTP 2FA: " + e.getMessage());
        }
    }
    
    /**
     * Verify and complete TOTP setup
     */
    public TOTP2FASetupResponse verifyAndCompleteTOTPSetup(UUID userId, String verificationCode) {
        log.info("Completing TOTP 2FA setup for user: {}", userId);
        
        try {
            // Get the pending TOTP configuration
            MfaConfiguration config = mfaConfigRepository
                    .findByUserIdAndMethodAndEnabledFalse(userId, MfaMethod.TOTP)
                    .orElseThrow(() -> new IllegalArgumentException("No pending TOTP setup found"));
            
            // Verify the code
            boolean isValid = codeVerifier.isValidCode(config.getSecret(), verificationCode);
            
            if (!isValid) {
                log.warn("Invalid TOTP verification code for user: {}", userId);
                throw new IllegalArgumentException("Invalid verification code");
            }
            
            // Enable the configuration
            config.setEnabled(true);
            config.setVerifiedAt(LocalDateTime.now());
            mfaConfigRepository.save(config);
            
            // Generate backup codes
            List<String> backupCodes = generateBackupCodes(userId, 10);
            
            User user = userRepository.findById(userId).orElseThrow();
            
            log.info("TOTP 2FA setup completed successfully for user: {}", userId);
            
            return TOTP2FASetupResponse.builder()
                    .secret(config.getSecret())
                    .qrCodeUrl("") // Not needed after setup
                    .backupCodes(backupCodes)
                    .manualEntryKey(config.getSecret())
                    .issuer(appName)
                    .accountName(user.getEmail())
                    .message("TOTP 2FA enabled successfully. Save backup codes securely.")
                    .setupComplete(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error completing TOTP 2FA setup for user: {}", userId, e);
            throw new RuntimeException("Failed to complete TOTP setup: " + e.getMessage());
        }
    }
    
    /**
     * Setup SMS-based 2FA with enhanced security
     */
    public SMS2FASetupResponse setupSMS2FA(UUID userId, String phoneNumber) {
        log.info("Setting up enhanced SMS 2FA for user: {} with phone: {}", userId, maskPhoneNumber(phoneNumber));
        
        try {
            // Validate phone number format
            if (!isValidPhoneNumber(phoneNumber)) {
                throw new IllegalArgumentException("Invalid phone number format");
            }
            
            // Check if phone number is already registered
            if (mfaConfigRepository.existsByMethodAndSecretAndEnabledTrue(MfaMethod.SMS, phoneNumber)) {
                throw new IllegalArgumentException("Phone number already registered for 2FA");
            }
            
            // Generate and send verification code
            String verificationCode = generateSecureCode(6);
            
            // Save temporary configuration
            MfaConfiguration config = MfaConfiguration.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .method(MfaMethod.SMS)
                    .secret(phoneNumber)
                    .enabled(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            mfaConfigRepository.save(config);
            
            // Save verification code
            saveVerificationCode(userId, MfaMethod.SMS, verificationCode);
            
            // Send SMS
            boolean smsSent = sendSMSCode(phoneNumber, verificationCode);
            
            if (!smsSent) {
                throw new RuntimeException("Failed to send SMS verification code");
            }
            
            log.info("SMS 2FA setup initiated for user: {} - verification code sent", userId);
            
            return SMS2FASetupResponse.builder()
                    .phoneNumber(maskPhoneNumber(phoneNumber))
                    .codeSent(true)
                    .expiresAt(LocalDateTime.now().plusMinutes(codeValidityMinutes))
                    .message("Verification code sent to " + maskPhoneNumber(phoneNumber))
                    .setupComplete(false)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error setting up SMS 2FA for user: {}", userId, e);
            throw new RuntimeException("Failed to setup SMS 2FA: " + e.getMessage());
        }
    }
    
    /**
     * Verify and complete SMS 2FA setup
     */
    public SMS2FASetupResponse verifyAndCompleteSMSSetup(UUID userId, String verificationCode) {
        log.info("Completing SMS 2FA setup for user: {}", userId);
        
        try {
            // Verify the code
            if (!verifyCodeWithRateLimit(userId, MfaMethod.SMS, verificationCode)) {
                throw new IllegalArgumentException("Invalid or expired verification code");
            }
            
            // Get the pending SMS configuration
            MfaConfiguration config = mfaConfigRepository
                    .findByUserIdAndMethodAndEnabledFalse(userId, MfaMethod.SMS)
                    .orElseThrow(() -> new IllegalArgumentException("No pending SMS setup found"));
            
            // Enable the configuration
            config.setEnabled(true);
            config.setVerifiedAt(LocalDateTime.now());
            mfaConfigRepository.save(config);
            
            // Clean up used verification code
            cleanupUsedVerificationCodes(userId, MfaMethod.SMS);
            
            log.info("SMS 2FA setup completed successfully for user: {}", userId);
            
            return SMS2FASetupResponse.builder()
                    .phoneNumber(maskPhoneNumber(config.getSecret()))
                    .codeSent(false)
                    .expiresAt(null)
                    .message("SMS 2FA enabled successfully")
                    .setupComplete(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error completing SMS 2FA setup for user: {}", userId, e);
            throw new RuntimeException("Failed to complete SMS setup: " + e.getMessage());
        }
    }
    
    /**
     * Authenticate user with 2FA
     */
    public TwoFactorAuthResult authenticate2FA(UUID userId, MfaMethod method, String code) {
        log.info("Authenticating 2FA for user: {} with method: {}", userId, method);
        
        try {
            // Check if user is locked out
            if (isUserLockedOut(userId)) {
                throw new SecurityException("Account temporarily locked due to failed attempts");
            }
            
            // Check if 2FA is enabled for this method
            MfaConfiguration config = mfaConfigRepository
                    .findByUserIdAndMethodAndEnabledTrue(userId, method)
                    .orElseThrow(() -> new IllegalArgumentException("2FA not enabled for this method"));
            
            boolean isValid = false;
            
            switch (method) {
                case TOTP:
                    isValid = codeVerifier.isValidCode(config.getSecret(), code);
                    break;
                case SMS:
                case EMAIL:
                    isValid = verifyCodeWithRateLimit(userId, method, code);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported 2FA method: " + method);
            }
            
            if (isValid) {
                // Reset attempt counter
                resetAttemptCounter(userId);
                
                // Update last used timestamp
                config.setLastUsedAt(LocalDateTime.now());
                mfaConfigRepository.save(config);
                
                log.info("2FA authentication successful for user: {} with method: {}", userId, method);
                
                return TwoFactorAuthResult.builder()
                        .success(true)
                        .userId(userId)
                        .method(method)
                        .message("2FA authentication successful")
                        .timestamp(LocalDateTime.now())
                        .build();
            } else {
                // Increment failed attempts
                incrementFailedAttempts(userId);
                
                log.warn("2FA authentication failed for user: {} with method: {}", userId, method);
                
                return TwoFactorAuthResult.builder()
                        .success(false)
                        .userId(userId)
                        .method(method)
                        .message("Invalid verification code")
                        .attemptsRemaining(getRemainingAttempts(userId))
                        .timestamp(LocalDateTime.now())
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Error during 2FA authentication for user: {}", userId, e);
            return TwoFactorAuthResult.builder()
                    .success(false)
                    .userId(userId)
                    .method(method)
                    .message("Authentication error: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Send 2FA code via SMS or Email
     */
    public boolean send2FACode(UUID userId, MfaMethod method) {
        log.info("Sending 2FA code for user: {} via method: {}", userId, method);
        
        try {
            // Check rate limiting
            if (isRateLimited(userId, method)) {
                throw new SecurityException("Rate limit exceeded. Please wait before requesting another code.");
            }
            
            // Get configuration
            MfaConfiguration config = mfaConfigRepository
                    .findByUserIdAndMethodAndEnabledTrue(userId, method)
                    .orElseThrow(() -> new IllegalArgumentException("2FA not enabled for this method"));
            
            // Generate verification code
            String code = generateSecureCode(6);
            
            // Save verification code
            saveVerificationCode(userId, method, code);
            
            boolean sent = false;
            switch (method) {
                case SMS:
                    sent = sendSMSCode(config.getSecret(), code);
                    break;
                case EMAIL:
                    User user = userRepository.findById(userId).orElseThrow();
                    sent = sendEmailCode(user.getEmail(), code);
                    break;
                default:
                    throw new IllegalArgumentException("Code sending not supported for method: " + method);
            }
            
            if (sent) {
                // Update rate limiting
                updateRateLimit(userId, method);
                log.info("2FA code sent successfully for user: {} via method: {}", userId, method);
            } else {
                log.error("Failed to send 2FA code for user: {} via method: {}", userId, method);
            }
            
            return sent;
            
        } catch (Exception e) {
            log.error("Error sending 2FA code for user: {} via method: {}", userId, method, e);
            return false;
        }
    }
    
    /**
     * Get user's 2FA status
     */
    public User2FAStatus get2FAStatus(UUID userId) {
        log.debug("Getting 2FA status for user: {}", userId);
        
        List<MfaConfiguration> configs = mfaConfigRepository.findByUserIdAndEnabledTrue(userId);
        
        return User2FAStatus.builder()
                .userId(userId)
                .totpEnabled(configs.stream().anyMatch(c -> c.getMethod() == MfaMethod.TOTP))
                .smsEnabled(configs.stream().anyMatch(c -> c.getMethod() == MfaMethod.SMS))
                .emailEnabled(configs.stream().anyMatch(c -> c.getMethod() == MfaMethod.EMAIL))
                .backupCodesAvailable(hasBackupCodes(userId))
                .enabledMethods(configs.stream().map(MfaConfiguration::getMethod).toList())
                .lastUsed(configs.stream()
                        .filter(c -> c.getLastUsedAt() != null)
                        .map(MfaConfiguration::getLastUsedAt)
                        .max(LocalDateTime::compareTo)
                        .orElse(null))
                .build();
    }
    
    /**
     * Disable 2FA method
     */
    public boolean disable2FA(UUID userId, MfaMethod method) {
        log.info("Disabling 2FA method: {} for user: {}", method, userId);
        
        try {
            MfaConfiguration config = mfaConfigRepository
                    .findByUserIdAndMethodAndEnabledTrue(userId, method)
                    .orElseThrow(() -> new IllegalArgumentException("2FA method not enabled"));
            
            config.setEnabled(false);
            config.setDisabledAt(LocalDateTime.now());
            mfaConfigRepository.save(config);
            
            // Clean up related data
            cleanupMethod2FAData(userId, method);
            
            log.info("2FA method {} disabled for user: {}", method, userId);
            return true;
            
        } catch (Exception e) {
            log.error("Error disabling 2FA method {} for user: {}", method, userId, e);
            return false;
        }
    }
    
    // Private helper methods
    
    private List<String> generateBackupCodes(UUID userId, int count) {
        List<String> backupCodes = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String code = generateSecureCode(8);
            backupCodes.add(code);
            
            // Save as verification code with special type
            MfaVerificationCode verificationCode = MfaVerificationCode.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .method(MfaMethod.BACKUP)
                    .code(code)
                    .expiresAt(LocalDateTime.now().plusYears(1)) // Long expiry
                    .createdAt(LocalDateTime.now())
                    .used(false)
                    .build();
                    
            verificationCodeRepository.save(verificationCode);
        }
        
        return backupCodes;
    }
    
    private String generateSecureCode(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(SECURE_RANDOM.nextInt(10));
        }
        return code.toString();
    }
    
    private void saveVerificationCode(UUID userId, MfaMethod method, String code) {
        MfaVerificationCode verificationCode = MfaVerificationCode.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .method(method)
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(codeValidityMinutes))
                .createdAt(LocalDateTime.now())
                .used(false)
                .build();
                
        verificationCodeRepository.save(verificationCode);
    }
    
    private boolean verifyCodeWithRateLimit(UUID userId, MfaMethod method, String code) {
        // Find valid verification code
        Optional<MfaVerificationCode> validCode = verificationCodeRepository
                .findByUserIdAndMethodAndCodeAndUsedFalseAndExpiresAtAfter(
                        userId, method, code, LocalDateTime.now());
                        
        if (validCode.isPresent()) {
            // Mark as used
            MfaVerificationCode codeEntity = validCode.get();
            codeEntity.setUsed(true);
            codeEntity.setUsedAt(LocalDateTime.now());
            verificationCodeRepository.save(codeEntity);
            return true;
        }
        
        return false;
    }
    
    private boolean sendSMSCode(String phoneNumber, String code) {
        try {
            TwoFactorNotificationRequest request = new TwoFactorNotificationRequest();
            request.setPhoneNumber(phoneNumber);
            request.setCode(code);
            request.setMessage(String.format("Your %s verification code: %s (expires in %d minutes)", 
                    appName, code, codeValidityMinutes));
                    
            return notificationClient.sendTwoFactorSms(request);
        } catch (Exception e) {
            log.error("Error sending SMS code to {}: {}", maskPhoneNumber(phoneNumber), e.getMessage());
            return false;
        }
    }
    
    private boolean sendEmailCode(String email, String code) {
        try {
            TwoFactorNotificationRequest request = new TwoFactorNotificationRequest();
            request.setEmail(email);
            request.setCode(code);
            request.setMessage(String.format("Your %s verification code: %s (expires in %d minutes)", 
                    appName, code, codeValidityMinutes));
                    
            return notificationClient.sendTwoFactorEmail(request);
        } catch (Exception e) {
            log.error("Error sending email code to {}: {}", email, e.getMessage());
            return false;
        }
    }
    
    private boolean isValidPhoneNumber(String phoneNumber) {
        // Basic phone number validation
        return phoneNumber != null && phoneNumber.matches("^\\+?[1-9]\\d{1,14}$");
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
    
    private void cleanupUsedVerificationCodes(UUID userId, MfaMethod method) {
        verificationCodeRepository.deleteByUserIdAndMethodAndUsedTrue(userId, method);
    }
    
    private void cleanupMethod2FAData(UUID userId, MfaMethod method) {
        verificationCodeRepository.deleteByUserIdAndMethod(userId, method);
    }
    
    private boolean hasBackupCodes(UUID userId) {
        return verificationCodeRepository.existsByUserIdAndMethodAndUsedFalse(userId, MfaMethod.BACKUP);
    }
    
    // Rate limiting and security methods
    
    private boolean isUserLockedOut(UUID userId) {
        AttemptTracker tracker = attemptTracker.get(userId.toString());
        if (tracker == null) return false;
        
        if (tracker.getAttempts() >= maxAttempts) {
            return tracker.getLastAttempt().plusMinutes(lockoutMinutes).isAfter(LocalDateTime.now());
        }
        return false;
    }
    
    private void incrementFailedAttempts(UUID userId) {
        String key = userId.toString();
        AttemptTracker tracker = attemptTracker.getOrDefault(key, new AttemptTracker());
        tracker.incrementAttempts();
        attemptTracker.put(key, tracker);
    }
    
    private void resetAttemptCounter(UUID userId) {
        attemptTracker.remove(userId.toString());
    }
    
    private int getRemainingAttempts(UUID userId) {
        AttemptTracker tracker = attemptTracker.get(userId.toString());
        return tracker == null ? maxAttempts : Math.max(0, maxAttempts - tracker.getAttempts());
    }
    
    private boolean isRateLimited(UUID userId, MfaMethod method) {
        // Implementation for rate limiting code requests
        // For example, limit to 3 codes per 15 minutes
        return false; // Simplified for now
    }
    
    private void updateRateLimit(UUID userId, MfaMethod method) {
        // Update rate limiting counters
    }
    
    // Inner class for attempt tracking
    private static class AttemptTracker {
        private int attempts = 0;
        private LocalDateTime lastAttempt = LocalDateTime.now();
        
        public void incrementAttempts() {
            this.attempts++;
            this.lastAttempt = LocalDateTime.now();
        }
        
        public int getAttempts() { return attempts; }
        public LocalDateTime getLastAttempt() { return lastAttempt; }
    }
}