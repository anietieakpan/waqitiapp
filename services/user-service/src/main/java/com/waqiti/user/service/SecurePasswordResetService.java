package com.waqiti.user.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.waqiti.security.encryption.SecureEncryptionService;
import com.waqiti.user.dto.PasswordResetRequest;
import com.waqiti.user.dto.PasswordResetResponse;
import com.waqiti.user.exception.PasswordResetException;
import com.waqiti.user.model.User;
import com.waqiti.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Secure Password Reset Service
 * 
 * Implements secure password reset functionality with:
 * - Cryptographically secure token generation
 * - Token expiration and one-time use
 * - Rate limiting per user/IP
 * - Secure token storage with encryption
 * - Audit logging for security events
 * - Protection against timing attacks
 * 
 * SECURITY: Fixes weak UUID-based token generation with proper
 * cryptographically secure random token generation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurePasswordResetService {
    
    private final UserRepository userRepository;
    private final SecureEncryptionService encryptionService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    
    @Value("${password-reset.token-length:32}")
    private int tokenLength;
    
    @Value("${password-reset.token-expiry-minutes:15}")
    private int tokenExpiryMinutes;
    
    @Value("${password-reset.max-attempts-per-hour:3}")
    private int maxAttemptsPerHour;
    
    @Value("${password-reset.cooldown-minutes:5}")
    private int cooldownMinutes;
    
    // Secure random generator
    private SecureRandom secureRandom;
    
    // Cache for rate limiting (user -> last reset times)
    private final Cache<String, java.util.List<Instant>> rateLimitCache;
    
    // Cache for active tokens (encrypted token -> token data)
    private final Cache<String, PasswordResetTokenData> tokenCache;
    
    public SecurePasswordResetService(
            UserRepository userRepository,
            SecureEncryptionService encryptionService,
            AuditService auditService,
            NotificationService notificationService,
            PasswordEncoder passwordEncoder) {
        
        this.userRepository = userRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        
        // Initialize rate limiting cache
        this.rateLimitCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(10000)
            .build();
        
        // Initialize token cache
        this.tokenCache = CacheBuilder.newBuilder()
            .expireAfterWrite(tokenExpiryMinutes, TimeUnit.MINUTES)
            .maximumSize(1000)
            .removalListener(notification -> {
                log.debug("Password reset token expired: {}", notification.getKey());
            })
            .build();
    }
    
    @PostConstruct
    public void init() {
        // Initialize secure random with strong entropy
        this.secureRandom = new SecureRandom();
        
        // Seed the random generator (this may take some time)
        byte[] seed = secureRandom.generateSeed(32);
        secureRandom.setSeed(seed);
        
        log.info("Secure Password Reset Service initialized with token length: {}, expiry: {} minutes", 
                tokenLength, tokenExpiryMinutes);
    }
    
    /**
     * Initiate password reset process
     * 
     * @param email User email address
     * @param clientIp Client IP address for rate limiting
     * @return PasswordResetResponse with result
     */
    @Transactional
    public PasswordResetResponse initiatePasswordReset(String email, String clientIp) {
        log.info("Password reset initiated for email: {}", maskEmail(email));
        
        try {
            // Security: Rate limiting check
            if (!checkRateLimit(email, clientIp)) {
                auditService.auditSecurityEvent("PASSWORD_RESET_RATE_LIMITED", 
                    String.format("Email: %s, IP: %s", maskEmail(email), clientIp));
                
                // Return success response to prevent email enumeration
                return PasswordResetResponse.builder()
                    .success(true)
                    .message("If the email exists, a reset link has been sent")
                    .build();
            }
            
            // Find user by email
            User user = userRepository.findByEmail(email).orElse(null);
            
            if (user == null) {
                // Security: Same response time to prevent timing attacks
                simulateProcessingDelay();
                
                // Security: Audit failed attempt but don't reveal if email exists
                auditService.auditSecurityEvent("PASSWORD_RESET_UNKNOWN_EMAIL", 
                    String.format("Email: %s, IP: %s", maskEmail(email), clientIp));
                
                return PasswordResetResponse.builder()
                    .success(true)
                    .message("If the email exists, a reset link has been sent")
                    .build();
            }
            
            // Check if user account is active
            if (!user.isActive()) {
                auditService.auditSecurityEvent("PASSWORD_RESET_INACTIVE_ACCOUNT", 
                    String.format("Email: %s, IP: %s", maskEmail(email), clientIp));
                
                return PasswordResetResponse.builder()
                    .success(true)
                    .message("If the email exists, a reset link has been sent")
                    .build();
            }
            
            // Generate secure reset token
            String resetToken = generateSecureToken();
            
            // Create token data
            PasswordResetTokenData tokenData = PasswordResetTokenData.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(tokenExpiryMinutes, ChronoUnit.MINUTES))
                .clientIp(clientIp)
                .used(false)
                .build();
            
            // Store encrypted token
            String encryptedTokenKey = encryptToken(resetToken);
            tokenCache.put(encryptedTokenKey, tokenData);
            
            // Send reset email
            sendPasswordResetEmail(user, resetToken);
            
            // Update rate limiting
            updateRateLimit(email, clientIp);
            
            // Audit successful initiation
            auditService.auditPasswordReset(user.getId(), user.getEmail(), clientIp, "INITIATED");
            
            log.info("Password reset token generated for user: {}", user.getId());
            
            return PasswordResetResponse.builder()
                .success(true)
                .message("If the email exists, a reset link has been sent")
                .build();
            
        } catch (Exception e) {
            log.error("Error initiating password reset for email: {}", maskEmail(email), e);
            
            auditService.auditSecurityEvent("PASSWORD_RESET_ERROR", 
                String.format("Email: %s, IP: %s, Error: %s", maskEmail(email), clientIp, e.getMessage()));
            
            throw new PasswordResetException("Failed to initiate password reset", e);
        }
    }
    
    /**
     * Complete password reset process
     * 
     * @param token Reset token from email
     * @param newPassword New password
     * @param clientIp Client IP address
     * @return PasswordResetResponse with result
     */
    @Transactional
    public PasswordResetResponse completePasswordReset(String token, String newPassword, String clientIp) {
        log.info("Password reset completion attempted with token: {}", maskToken(token));
        
        try {
            // Validate token format
            if (token == null || token.length() != tokenLength * 4 / 3) { // Base64 encoding ratio
                auditService.auditSecurityEvent("PASSWORD_RESET_INVALID_TOKEN_FORMAT", 
                    String.format("Token: %s, IP: %s", maskToken(token), clientIp));
                
                return PasswordResetResponse.builder()
                    .success(false)
                    .message("Invalid or expired reset token")
                    .build();
            }
            
            // Find token data
            String encryptedTokenKey = encryptToken(token);
            PasswordResetTokenData tokenData = tokenCache.getIfPresent(encryptedTokenKey);
            
            if (tokenData == null) {
                auditService.auditSecurityEvent("PASSWORD_RESET_TOKEN_NOT_FOUND", 
                    String.format("Token: %s, IP: %s", maskToken(token), clientIp));
                
                return PasswordResetResponse.builder()
                    .success(false)
                    .message("Invalid or expired reset token")
                    .build();
            }
            
            // Check if token is expired
            if (tokenData.getExpiresAt().isBefore(Instant.now())) {
                tokenCache.invalidate(encryptedTokenKey);
                auditService.auditSecurityEvent("PASSWORD_RESET_TOKEN_EXPIRED", 
                    String.format("Token: %s, IP: %s", maskToken(token), clientIp));
                
                return PasswordResetResponse.builder()
                    .success(false)
                    .message("Invalid or expired reset token")
                    .build();
            }
            
            // Check if token already used
            if (tokenData.isUsed()) {
                tokenCache.invalidate(encryptedTokenKey);
                auditService.auditSecurityEvent("PASSWORD_RESET_TOKEN_REUSED", 
                    String.format("Token: %s, IP: %s", maskToken(token), clientIp));
                
                return PasswordResetResponse.builder()
                    .success(false)
                    .message("Invalid or expired reset token")
                    .build();
            }
            
            // Get user
            User user = userRepository.findById(tokenData.getUserId())
                .orElseThrow(() -> new PasswordResetException("User not found"));
            
            // Validate password strength
            if (!isPasswordStrong(newPassword)) {
                auditService.auditSecurityEvent("PASSWORD_RESET_WEAK_PASSWORD", 
                    String.format("User: %s, IP: %s", user.getId(), clientIp));
                
                return PasswordResetResponse.builder()
                    .success(false)
                    .message("Password does not meet security requirements")
                    .build();
            }
            
            // Update user password
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordChangedAt(Instant.now());
            
            // Force logout from all sessions (security measure)
            user.setSessionInvalidationTimestamp(Instant.now());
            
            userRepository.save(user);
            
            // Mark token as used and remove from cache
            tokenData.setUsed(true);
            tokenCache.invalidate(encryptedTokenKey);
            
            // Send confirmation email
            notificationService.sendPasswordResetConfirmation(user);
            
            // Audit successful password reset
            auditService.auditPasswordReset(user.getId(), user.getEmail(), clientIp, "COMPLETED");
            
            log.info("Password reset completed for user: {}", user.getId());
            
            return PasswordResetResponse.builder()
                .success(true)
                .message("Password has been reset successfully")
                .build();
            
        } catch (Exception e) {
            log.error("Error completing password reset", e);
            
            auditService.auditSecurityEvent("PASSWORD_RESET_COMPLETION_ERROR", 
                String.format("Token: %s, IP: %s, Error: %s", maskToken(token), clientIp, e.getMessage()));
            
            throw new PasswordResetException("Failed to complete password reset", e);
        }
    }
    
    /**
     * Generate cryptographically secure token
     * SECURITY FIX: Replaces UUID.randomUUID() with SecureRandom
     */
    private String generateSecureToken() {
        // Generate random bytes
        byte[] randomBytes = new byte[tokenLength];
        secureRandom.nextBytes(randomBytes);
        
        // Encode to Base64 URL-safe
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    
    /**
     * Encrypt token for storage
     */
    private String encryptToken(String token) {
        try {
            // Hash token with salt for storage key
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("password-reset-salt".getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt token", e);
        }
    }
    
    /**
     * Check rate limiting
     */
    private boolean checkRateLimit(String email, String clientIp) {
        String key = email + ":" + clientIp;
        
        java.util.List<Instant> attempts = rateLimitCache.getIfPresent(key);
        if (attempts == null) {
            attempts = new java.util.ArrayList<>();
        }
        
        // Remove old attempts
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        attempts.removeIf(attempt -> attempt.isBefore(cutoff));
        
        // Check if limit exceeded
        if (attempts.size() >= maxAttemptsPerHour) {
            return false;
        }
        
        // Check cooldown period
        if (!attempts.isEmpty()) {
            Instant lastAttempt = attempts.get(attempts.size() - 1);
            if (lastAttempt.isAfter(Instant.now().minus(cooldownMinutes, ChronoUnit.MINUTES))) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Update rate limiting
     */
    private void updateRateLimit(String email, String clientIp) {
        String key = email + ":" + clientIp;
        
        java.util.List<Instant> attempts = rateLimitCache.getIfPresent(key);
        if (attempts == null) {
            attempts = new java.util.ArrayList<>();
        }
        
        attempts.add(Instant.now());
        rateLimitCache.put(key, attempts);
    }
    
    /**
     * Validate password strength
     */
    private boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        // Check for uppercase, lowercase, digit, and special character
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> "!@#$%^&*()_+-=[]{}|;:,.<>?".indexOf(ch) >= 0);
        
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
    
    /**
     * Send password reset email
     */
    private void sendPasswordResetEmail(User user, String token) {
        try {
            String resetUrl = buildResetUrl(token);
            notificationService.sendPasswordResetEmail(user, resetUrl, tokenExpiryMinutes);
        } catch (Exception e) {
            log.error("Failed to send password reset email", e);
            // Don't fail the process for email issues
        }
    }
    
    /**
     * Build password reset URL
     */
    private String buildResetUrl(String token) {
        // This would typically use a configured base URL
        return String.format("https://app.example.com/reset-password?token=%s", token);
    }
    
    /**
     * Simulate processing delay to prevent timing attacks
     */
    private void simulateProcessingDelay() {
        try {
            // Random delay between 100-300ms
            int delay = 100 + secureRandom.nextInt(200);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Mask email for logging
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        
        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        
        if (localPart.length() <= 2) {
            return "*".repeat(localPart.length()) + domain;
        }
        
        return localPart.charAt(0) + "*".repeat(localPart.length() - 2) + 
               localPart.charAt(localPart.length() - 1) + domain;
    }
    
    /**
     * Mask token for logging
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
    
    /**
     * Get password reset statistics
     */
    public PasswordResetStatistics getStatistics() {
        return PasswordResetStatistics.builder()
            .activeTokens(tokenCache.size())
            .rateLimitedIps(rateLimitCache.size())
            .tokenExpiryMinutes(tokenExpiryMinutes)
            .maxAttemptsPerHour(maxAttemptsPerHour)
            .build();
    }
    
    /**
     * Token data structure
     */
    @lombok.Data
    @lombok.Builder
    private static class PasswordResetTokenData {
        private String userId;
        private String email;
        private Instant createdAt;
        private Instant expiresAt;
        private String clientIp;
        private boolean used;
    }
    
    /**
     * Statistics data
     */
    @lombok.Data
    @lombok.Builder
    public static class PasswordResetStatistics {
        private long activeTokens;
        private long rateLimitedIps;
        private int tokenExpiryMinutes;
        private int maxAttemptsPerHour;
    }
}