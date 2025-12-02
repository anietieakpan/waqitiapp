package com.waqiti.common.security.authentication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Multi-Factor Authentication service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    
    private static final String MFA_TOKEN_PREFIX = "mfa:token:";
    private static final String MFA_ATTEMPT_PREFIX = "mfa:attempts:";
    private static final Duration MFA_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration MFA_ATTEMPT_TTL = Duration.ofMinutes(15);
    private static final int MAX_MFA_ATTEMPTS = 3;
    
    /**
     * Generate MFA token for user
     */
    public String generateMfaToken(String userId) {
        try {
            // Generate 6-digit code
            int code = 100000 + secureRandom.nextInt(900000);
            String mfaCode = String.valueOf(code);
            
            // Store in Redis with TTL
            String tokenKey = MFA_TOKEN_PREFIX + userId;
            redisTemplate.opsForValue().set(tokenKey, mfaCode, MFA_TOKEN_TTL);
            
            log.debug("Generated MFA token for user: {}", userId);
            return mfaCode;
            
        } catch (Exception e) {
            log.error("Failed to generate MFA token for user: {}", userId, e);
            throw new RuntimeException("Failed to generate MFA token", e);
        }
    }
    
    /**
     * Verify MFA token
     */
    public boolean verifyMfaToken(String userId, String providedToken) {
        try {
            // Check attempt count
            if (!isWithinAttemptLimit(userId)) {
                log.warn("MFA verification failed - too many attempts for user: {}", userId);
                return false;
            }
            
            // Increment attempt count
            incrementAttemptCount(userId);
            
            // Verify token
            String tokenKey = MFA_TOKEN_PREFIX + userId;
            String storedToken = redisTemplate.opsForValue().get(tokenKey);
            
            if (storedToken == null) {
                log.warn("MFA verification failed - no token found for user: {}", userId);
                return false;
            }
            
            boolean isValid = storedToken.equals(providedToken);
            
            if (isValid) {
                // Clear token and attempts on successful verification
                redisTemplate.delete(tokenKey);
                clearAttemptCount(userId);
                log.info("MFA verification successful for user: {}", userId);
            } else {
                log.warn("MFA verification failed - invalid token for user: {}", userId);
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Error verifying MFA token for user: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Check if user is within MFA attempt limit
     */
    private boolean isWithinAttemptLimit(String userId) {
        try {
            String attemptKey = MFA_ATTEMPT_PREFIX + userId;
            String attempts = redisTemplate.opsForValue().get(attemptKey);
            
            if (attempts == null) {
                return true;
            }
            
            return Integer.parseInt(attempts) < MAX_MFA_ATTEMPTS;
            
        } catch (Exception e) {
            log.error("Error checking MFA attempt limit for user: {}", userId, e);
            return false; // Fail closed
        }
    }
    
    /**
     * Increment MFA attempt count
     */
    private void incrementAttemptCount(String userId) {
        try {
            String attemptKey = MFA_ATTEMPT_PREFIX + userId;
            redisTemplate.opsForValue().increment(attemptKey);
            redisTemplate.expire(attemptKey, MFA_ATTEMPT_TTL);
        } catch (Exception e) {
            log.error("Error incrementing MFA attempt count for user: {}", userId, e);
        }
    }
    
    /**
     * Clear MFA attempt count
     */
    private void clearAttemptCount(String userId) {
        try {
            String attemptKey = MFA_ATTEMPT_PREFIX + userId;
            redisTemplate.delete(attemptKey);
        } catch (Exception e) {
            log.error("Error clearing MFA attempt count for user: {}", userId, e);
        }
    }
    
    /**
     * Check if MFA is required for user
     */
    public boolean isMfaRequired(String userId) {
        // This would typically check user preferences or risk factors
        // For now, return true for all users
        return true;
    }
    
    /**
     * Generate backup codes for user
     */
    public String[] generateBackupCodes(String userId, int count) {
        String[] backupCodes = new String[count];
        
        for (int i = 0; i < count; i++) {
            byte[] randomBytes = new byte[8];
            secureRandom.nextBytes(randomBytes);
            backupCodes[i] = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        }
        
        // Store backup codes (in production, these should be hashed)
        // For now, just log the generation
        log.info("Generated {} backup codes for user: {}", count, userId);
        
        return backupCodes;
    }
}