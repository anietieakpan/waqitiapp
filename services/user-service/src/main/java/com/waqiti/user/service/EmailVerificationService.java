package com.waqiti.user.service;

import com.waqiti.common.events.SecurityEventPublisher;
import com.waqiti.common.events.SecurityEvent;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.user.domain.User;
import com.waqiti.user.domain.EmailVerificationToken;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.repository.EmailVerificationTokenRepository;
import com.waqiti.user.exception.InvalidTokenException;
import com.waqiti.user.exception.TokenExpiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final NotificationService notificationService;
    private final SecurityEventPublisher securityEventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${user.email.verification.token-expiry-hours:24}")
    private int tokenExpiryHours;
    
    @Value("${user.email.verification.code-length:6}")
    private int codeLength;
    
    @Value("${user.email.verification.max-attempts:5}")
    private int maxAttempts;
    
    @Value("${user.email.verification.cooldown-minutes:5}")
    private int cooldownMinutes;
    
    @Value("${user.email.verification.base-url}")
    private String baseUrl;
    
    private static final String VERIFICATION_ATTEMPTS_PREFIX = "email:verify:attempts:";
    private static final String VERIFICATION_COOLDOWN_PREFIX = "email:verify:cooldown:";
    private static final SecureRandom secureRandom = new SecureRandom();
    
    @Transactional
    public EmailVerificationResult sendVerificationEmail(String userId) {
        log.info("Sending email verification for user: {}", userId);
        
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        
        if (user.isEmailVerified()) {
            return EmailVerificationResult.builder()
                    .success(false)
                    .reason("Email already verified")
                    .build();
        }
        
        if (isUserInCooldown(userId)) {
            int remainingSeconds = getRemainingCooldownSeconds(userId);
            return EmailVerificationResult.builder()
                    .success(false)
                    .reason(String.format("Please wait %d seconds before requesting another code", remainingSeconds))
                    .cooldownSeconds(remainingSeconds)
                    .build();
        }
        
        invalidateExistingTokens(userId);
        
        String token = generateSecureToken();
        String code = generateNumericCode();
        
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(userId))
                .token(token)
                .verificationCode(code)
                .email(user.getEmail())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(tokenExpiryHours))
                .verified(false)
                .attempts(0)
                .build();
        
        tokenRepository.save(verificationToken);
        
        sendVerificationNotifications(user, token, code);
        
        setCooldown(userId);
        
        SecurityEvent event = SecurityEvent.builder()
                .eventType("EMAIL_VERIFICATION_SENT")
                .userId(userId)
                .details(String.format("{\"email\":\"%s\",\"tokenId\":\"%s\"}", 
                        maskEmail(user.getEmail()), verificationToken.getId()))
                .timestamp(System.currentTimeMillis())
                .build();
        securityEventPublisher.publishSecurityEvent(event);
        
        kafkaTemplate.send("user-events", Map.of(
                "eventType", "EMAIL_VERIFICATION_INITIATED",
                "userId", userId,
                "email", user.getEmail(),
                "timestamp", System.currentTimeMillis()
        ));
        
        return EmailVerificationResult.builder()
                .success(true)
                .tokenId(verificationToken.getId().toString())
                .expiresAt(verificationToken.getExpiresAt())
                .build();
    }
    
    @Transactional
    public EmailVerificationResult verifyEmailWithToken(String token) {
        log.info("Verifying email with token");
        
        EmailVerificationToken verificationToken = tokenRepository.findByTokenAndVerifiedFalse(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid or already used verification token"));
        
        if (LocalDateTime.now().isAfter(verificationToken.getExpiresAt())) {
            throw new TokenExpiredException("Verification token has expired");
        }
        
        return completeVerification(verificationToken);
    }
    
    @Transactional
    public EmailVerificationResult verifyEmailWithCode(String userId, String code) {
        log.info("Verifying email with code for user: {}", userId);
        
        EmailVerificationToken verificationToken = tokenRepository
                .findByUserIdAndVerifiedFalseOrderByCreatedAtDesc(UUID.fromString(userId))
                .stream()
                .findFirst()
                .orElseThrow(() -> new InvalidTokenException("No pending verification found for user"));
        
        if (LocalDateTime.now().isAfter(verificationToken.getExpiresAt())) {
            throw new TokenExpiredException("Verification code has expired");
        }
        
        verificationToken.setAttempts(verificationToken.getAttempts() + 1);
        
        if (verificationToken.getAttempts() > maxAttempts) {
            verificationToken.setExpiredAt(LocalDateTime.now());
            tokenRepository.save(verificationToken);
            
            log.warn("SECURITY: Max verification attempts exceeded for user: {}", userId);
            
            SecurityEvent event = SecurityEvent.builder()
                    .eventType("EMAIL_VERIFICATION_MAX_ATTEMPTS")
                    .userId(userId)
                    .details(String.format("{\"attempts\":%d}", verificationToken.getAttempts()))
                    .timestamp(System.currentTimeMillis())
                    .build();
            securityEventPublisher.publishSecurityEvent(event);
            
            return EmailVerificationResult.builder()
                    .success(false)
                    .reason("Maximum verification attempts exceeded. Please request a new code.")
                    .attemptsExceeded(true)
                    .build();
        }
        
        if (!code.equals(verificationToken.getVerificationCode())) {
            tokenRepository.save(verificationToken);
            
            return EmailVerificationResult.builder()
                    .success(false)
                    .reason("Invalid verification code")
                    .remainingAttempts(maxAttempts - verificationToken.getAttempts())
                    .build();
        }
        
        return completeVerification(verificationToken);
    }
    
    @Transactional
    public boolean resendVerificationEmail(String userId) {
        log.info("Resending verification email for user: {}", userId);
        
        EmailVerificationResult result = sendVerificationEmail(userId);
        return result.isSuccess();
    }
    
    @Transactional(readOnly = true)
    public boolean isEmailVerified(String userId) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return user.isEmailVerified();
    }
    
    @Transactional(readOnly = true)
    public EmailVerificationStatus getVerificationStatus(String userId) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        
        if (user.isEmailVerified()) {
            return EmailVerificationStatus.builder()
                    .verified(true)
                    .email(user.getEmail())
                    .verifiedAt(user.getEmailVerifiedAt())
                    .build();
        }
        
        tokenRepository.findByUserIdAndVerifiedFalseOrderByCreatedAtDesc(UUID.fromString(userId))
                .stream()
                .findFirst()
                .ifPresent(token -> {
                    boolean expired = LocalDateTime.now().isAfter(token.getExpiresAt());
                    EmailVerificationStatus.builder()
                            .verified(false)
                            .email(user.getEmail())
                            .pendingVerification(true)
                            .tokenExpired(expired)
                            .expiresAt(token.getExpiresAt())
                            .attempts(token.getAttempts())
                            .maxAttempts(maxAttempts)
                            .build();
                });
        
        return EmailVerificationStatus.builder()
                .verified(false)
                .email(user.getEmail())
                .pendingVerification(false)
                .build();
    }
    
    private EmailVerificationResult completeVerification(EmailVerificationToken verificationToken) {
        verificationToken.setVerified(true)
;
        verificationToken.setVerifiedAt(LocalDateTime.now());
        tokenRepository.save(verificationToken);
        
        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(user);
        
        clearCooldown(user.getId().toString());
        clearAttempts(user.getId().toString());
        
        SecurityEvent successEvent = SecurityEvent.builder()
                .eventType("EMAIL_VERIFICATION_SUCCESS")
                .userId(user.getId().toString())
                .details(String.format("{\"email\":\"%s\"}", maskEmail(user.getEmail())))
                .timestamp(System.currentTimeMillis())
                .build();
        securityEventPublisher.publishSecurityEvent(successEvent);
        
        kafkaTemplate.send("user-events", Map.of(
                "eventType", "EMAIL_VERIFIED",
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "timestamp", System.currentTimeMillis()
        ));
        
        sendWelcomeEmail(user);
        
        log.info("Email verification completed successfully for user: {}", user.getId());
        
        return EmailVerificationResult.builder()
                .success(true)
                .message("Email verified successfully")
                .build();
    }
    
    private void sendVerificationNotifications(User user, String token, String code) {
        String verificationLink = String.format("%s/verify-email?token=%s", baseUrl, token);
        
        String subject = "Verify Your Email Address - Waqiti";
        String body = buildVerificationEmailBody(user.getFirstName(), verificationLink, code);
        
        notificationService.sendEmail(user.getId().toString(), subject, body);
        
        if (user.getPhoneNumber() != null) {
            String smsMessage = String.format(
                    "Waqiti: Your email verification code is %s. Valid for %d hours. " +
                    "Or click: %s",
                    code, tokenExpiryHours, verificationLink);
            notificationService.sendSms(user.getId().toString(), smsMessage);
        }
    }
    
    private String buildVerificationEmailBody(String firstName, String verificationLink, String code) {
        return String.format("""
                Hi %s,
                
                Welcome to Waqiti! Please verify your email address to complete your account setup.
                
                VERIFICATION CODE: %s
                
                Or click the link below to verify instantly:
                %s
                
                This verification link and code will expire in %d hours.
                
                If you didn't create a Waqiti account, please ignore this email.
                
                Best regards,
                The Waqiti Team
                
                ---
                For security reasons, never share this code with anyone.
                """, firstName, code, verificationLink, tokenExpiryHours);
    }
    
    private void sendWelcomeEmail(User user) {
        String subject = "Welcome to Waqiti!";
        String body = String.format("""
                Hi %s,
                
                Your email has been verified successfully!
                
                You now have full access to all Waqiti features:
                - Send and receive payments
                - International transfers
                - Merchant payments
                - And much more!
                
                Get started: %s/dashboard
                
                Thank you for joining Waqiti!
                
                Best regards,
                The Waqiti Team
                """, user.getFirstName(), baseUrl);
        
        notificationService.sendEmail(user.getId().toString(), subject, body);
    }
    
    private String generateSecureToken() {
        return UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
    }
    
    private String generateNumericCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < codeLength; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
    
    private void invalidateExistingTokens(String userId) {
        tokenRepository.findByUserIdAndVerifiedFalse(UUID.fromString(userId))
                .forEach(token -> {
                    token.setExpiredAt(LocalDateTime.now());
                    tokenRepository.save(token);
                });
    }
    
    private boolean isUserInCooldown(String userId) {
        String key = VERIFICATION_COOLDOWN_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    private void setCooldown(String userId) {
        String key = VERIFICATION_COOLDOWN_PREFIX + userId;
        redisTemplate.opsForValue().set(key, true, Duration.ofMinutes(cooldownMinutes));
    }
    
    private void clearCooldown(String userId) {
        String key = VERIFICATION_COOLDOWN_PREFIX + userId;
        redisTemplate.delete(key);
    }
    
    private int getRemainingCooldownSeconds(String userId) {
        String key = VERIFICATION_COOLDOWN_PREFIX + userId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl.intValue() : 0;
    }
    
    private void clearAttempts(String userId) {
        String key = VERIFICATION_ATTEMPTS_PREFIX + userId;
        redisTemplate.delete(key);
    }
    
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***@***.***";
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];
        
        String maskedLocal = localPart.length() > 2 ? 
                localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) : 
                "***";
        
        return maskedLocal + "@" + domain;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class EmailVerificationResult {
        private boolean success;
        private String tokenId;
        private String reason;
        private String message;
        private LocalDateTime expiresAt;
        private int cooldownSeconds;
        private int remainingAttempts;
        private boolean attemptsExceeded;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class EmailVerificationStatus {
        private boolean verified;
        private String email;
        private LocalDateTime verifiedAt;
        private boolean pendingVerification;
        private boolean tokenExpired;
        private LocalDateTime expiresAt;
        private int attempts;
        private int maxAttempts;
    }
}