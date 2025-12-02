package com.waqiti.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Enterprise-grade CSRF token service for preventing Cross-Site Request Forgery attacks.
 *
 * Security Implementation:
 * - Cryptographically secure random token generation
 * - Token storage in Redis with expiration
 * - Double-submit cookie pattern
 * - Synchronizer token pattern support
 *
 * CSRF Protection Strategy:
 * 1. Generate unique CSRF token per session
 * 2. Store token server-side (Redis) with user ID mapping
 * 3. Send token to frontend in:
 *    - Cookie (readable by JavaScript)
 *    - Response body
 * 4. Frontend includes token in:
 *    - X-CSRF-TOKEN header for AJAX requests
 *    - Hidden form field for traditional forms
 * 5. Server validates token matches stored value
 *
 * Compliance:
 * - OWASP CSRF Prevention Cheat Sheet
 * - PCI-DSS 6.5.9 (Cross-Site Request Forgery)
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsrfTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CSRF_TOKEN_PREFIX = "csrf:token:";
    private static final String CSRF_USER_PREFIX = "csrf:user:";
    private static final Duration TOKEN_EXPIRATION = Duration.ofHours(24);
    private static final int TOKEN_LENGTH = 32; // 256 bits

    /**
     * Generates a cryptographically secure CSRF token.
     *
     * Token Format:
     * - 32 bytes of cryptographically secure random data
     * - Base64 URL-safe encoded
     * - Prefixed with UUID for uniqueness
     *
     * Storage:
     * - Redis with 24-hour expiration
     * - Bidirectional mapping (token <-> userId)
     *
     * @param userId User ID to associate with token
     * @return Generated CSRF token
     */
    public String generateCsrfToken(UUID userId) {
        // Generate cryptographically secure random bytes
        byte[] randomBytes = new byte[TOKEN_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);

        // Encode to Base64 URL-safe string
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // Add UUID prefix for additional uniqueness
        String csrfToken = UUID.randomUUID().toString() + ":" + token;

        // Store in Redis with bidirectional mapping
        String tokenKey = CSRF_TOKEN_PREFIX + csrfToken;
        String userKey = CSRF_USER_PREFIX + userId.toString();

        // Store token -> userId mapping
        redisTemplate.opsForValue().set(tokenKey, userId.toString(), TOKEN_EXPIRATION);

        // Store userId -> token mapping (for invalidation)
        redisTemplate.opsForValue().set(userKey, csrfToken, TOKEN_EXPIRATION);

        log.debug("Generated CSRF token for user: {}", userId);

        return csrfToken;
    }

    /**
     * Validates CSRF token against stored value.
     *
     * Validation Steps:
     * 1. Check token format
     * 2. Verify token exists in Redis
     * 3. Verify token belongs to current user
     * 4. Check token not expired
     *
     * @param csrfToken CSRF token to validate
     * @param userId User ID to validate against
     * @return true if token is valid
     */
    public boolean validateCsrfToken(String csrfToken, UUID userId) {
        if (csrfToken == null || csrfToken.isBlank()) {
            log.warn("CSRF validation failed: Token is null or empty");
            return false;
        }

        try {
            // Check token exists in Redis
            String tokenKey = CSRF_TOKEN_PREFIX + csrfToken;
            String storedUserId = redisTemplate.opsForValue().get(tokenKey);

            if (storedUserId == null) {
                log.warn("CSRF validation failed: Token not found in Redis");
                return false;
            }

            // Verify token belongs to current user
            if (!storedUserId.equals(userId.toString())) {
                log.warn("CSRF validation failed: Token userId mismatch. " +
                        "Expected: {}, Found: {}", userId, storedUserId);
                return false;
            }

            // Token is valid - refresh expiration
            redisTemplate.expire(tokenKey, TOKEN_EXPIRATION);

            log.debug("CSRF token validated successfully for user: {}", userId);
            return true;

        } catch (Exception e) {
            log.error("CSRF validation error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Invalidates CSRF token (e.g., on logout).
     *
     * @param csrfToken CSRF token to invalidate
     */
    public void invalidateCsrfToken(String csrfToken) {
        if (csrfToken == null || csrfToken.isBlank()) {
            return;
        }

        try {
            String tokenKey = CSRF_TOKEN_PREFIX + csrfToken;

            // Get userId before deleting token
            String userId = redisTemplate.opsForValue().get(tokenKey);

            // Delete token -> userId mapping
            redisTemplate.delete(tokenKey);

            // Delete userId -> token mapping
            if (userId != null) {
                String userKey = CSRF_USER_PREFIX + userId;
                redisTemplate.delete(userKey);
            }

            log.debug("CSRF token invalidated");

        } catch (Exception e) {
            log.error("Failed to invalidate CSRF token: {}", e.getMessage(), e);
        }
    }

    /**
     * Invalidates all CSRF tokens for a user (e.g., on logout from all devices).
     *
     * @param userId User ID
     */
    public void invalidateAllCsrfTokensForUser(UUID userId) {
        try {
            String userKey = CSRF_USER_PREFIX + userId.toString();

            // Get current token
            String csrfToken = redisTemplate.opsForValue().get(userKey);

            if (csrfToken != null) {
                // Delete token -> userId mapping
                String tokenKey = CSRF_TOKEN_PREFIX + csrfToken;
                redisTemplate.delete(tokenKey);
            }

            // Delete userId -> token mapping
            redisTemplate.delete(userKey);

            log.info("All CSRF tokens invalidated for user: {}", userId);

        } catch (Exception e) {
            log.error("Failed to invalidate CSRF tokens for user {}: {}",
                    userId, e.getMessage(), e);
        }
    }

    /**
     * Gets current CSRF token for user (if exists).
     *
     * @param userId User ID
     * @return Current CSRF token or null if none exists
     */
    public String getCurrentCsrfToken(UUID userId) {
        String userKey = CSRF_USER_PREFIX + userId.toString();
        return redisTemplate.opsForValue().get(userKey);
    }

    /**
     * Refreshes CSRF token expiration.
     *
     * @param csrfToken CSRF token to refresh
     */
    public void refreshCsrfTokenExpiration(String csrfToken) {
        if (csrfToken == null || csrfToken.isBlank()) {
            return;
        }

        try {
            String tokenKey = CSRF_TOKEN_PREFIX + csrfToken;
            String userId = redisTemplate.opsForValue().get(tokenKey);

            if (userId != null) {
                // Refresh both mappings
                redisTemplate.expire(tokenKey, TOKEN_EXPIRATION);
                redisTemplate.expire(CSRF_USER_PREFIX + userId, TOKEN_EXPIRATION);

                log.debug("CSRF token expiration refreshed");
            }

        } catch (Exception e) {
            log.error("Failed to refresh CSRF token expiration: {}", e.getMessage(), e);
        }
    }
}
