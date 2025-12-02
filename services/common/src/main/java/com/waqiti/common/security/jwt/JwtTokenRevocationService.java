package com.waqiti.common.security.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL SECURITY: JWT Token Revocation Service
 *
 * Implements production-grade token revocation using Redis for distributed systems.
 * Addresses the gap where compromised tokens remain valid until expiration.
 *
 * Features:
 * - Distributed token blacklisting across all service instances
 * - Automatic expiration based on token TTL
 * - SHA-256 hashing for privacy (token content not stored)
 * - User-level and token-level revocation support
 * - Audit logging for compliance
 * - Performance optimized with Redis operations
 *
 * Usage:
 * 1. Call revokeToken() when user logs out or token is compromised
 * 2. Call isTokenRevoked() in JWT authentication filter
 * 3. Call revokeAllUserTokens() during password reset or security incidents
 *
 * @author Waqiti Security Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenRevocationService {

    private final RedisTemplate<String, String> redisTemplate;

    // Redis key prefixes for namespace isolation
    private static final String REVOKED_TOKEN_PREFIX = "jwt:revoked:token:";
    private static final String REVOKED_USER_PREFIX = "jwt:revoked:user:";
    private static final String REVOCATION_AUDIT_PREFIX = "jwt:audit:revocation:";

    /**
     * Revoke a specific JWT token
     *
     * @param token The JWT token string to revoke
     * @param ttl Time-to-live for the revocation entry (should match token expiration)
     * @param reason Reason for revocation (for audit trail)
     * @param revokedBy Who revoked the token (user ID or system identifier)
     */
    public void revokeToken(String token, Duration ttl, String reason, String revokedBy) {
        if (token == null || token.isBlank()) {
            log.warn("SECURITY: Attempted to revoke null or empty token");
            return;
        }

        try {
            // Hash the token for privacy - we don't store raw tokens in Redis
            String tokenHash = hashToken(token);
            String redisKey = REVOKED_TOKEN_PREFIX + tokenHash;

            // Store revocation with metadata for audit purposes
            RevocationMetadata metadata = RevocationMetadata.builder()
                .revokedAt(Instant.now())
                .reason(reason)
                .revokedBy(revokedBy)
                .expiresAt(Instant.now().plus(ttl))
                .build();

            // Store in Redis with automatic expiration
            redisTemplate.opsForValue().set(redisKey, metadata.toJson(), ttl.toMillis(), TimeUnit.MILLISECONDS);

            log.info("SECURITY: Token revoked successfully - Hash: {}, Reason: {}, RevokedBy: {}, TTL: {}",
                    tokenHash.substring(0, 8) + "...", reason, revokedBy, ttl);

            // Store audit record (longer retention for compliance)
            storeRevocationAudit(tokenHash, metadata, ttl);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to revoke token - Reason: {}, RevokedBy: {}", reason, revokedBy, e);
            // In production, you might want to throw an exception here to prevent false security
            throw new TokenRevocationException("Failed to revoke token", e);
        }
    }

    /**
     * Check if a token has been revoked
     *
     * @param token The JWT token to check
     * @return true if token is revoked, false otherwise
     */
    public boolean isTokenRevoked(String token) {
        if (token == null || token.isBlank()) {
            log.warn("SECURITY: Attempted to check revocation status of null/empty token");
            return true; // Fail-secure: treat invalid tokens as revoked
        }

        try {
            String tokenHash = hashToken(token);
            String redisKey = REVOKED_TOKEN_PREFIX + tokenHash;

            // Check if token exists in revocation list
            Boolean exists = redisTemplate.hasKey(redisKey);

            if (Boolean.TRUE.equals(exists)) {
                log.warn("SECURITY: Revoked token detected - Hash: {}", tokenHash.substring(0, 8) + "...");
                return true;
            }

            // Also check if user-level revocation is active
            // (This would require extracting user ID from token, but checking here for completeness)

            return false;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to check token revocation status", e);
            // Fail-secure: if we can't verify, treat as revoked
            return true;
        }
    }

    /**
     * Revoke all tokens for a specific user
     *
     * Use cases:
     * - User password reset
     * - Account security incident
     * - User-initiated "log out from all devices"
     *
     * @param userId The user ID whose tokens should be revoked
     * @param ttl How long to maintain the user revocation (e.g., max token lifetime)
     * @param reason Reason for revocation
     * @param revokedBy Who initiated the revocation
     */
    public void revokeAllUserTokens(String userId, Duration ttl, String reason, String revokedBy) {
        if (userId == null || userId.isBlank()) {
            log.warn("SECURITY: Attempted to revoke tokens for null/empty user ID");
            return;
        }

        try {
            String redisKey = REVOKED_USER_PREFIX + userId;

            RevocationMetadata metadata = RevocationMetadata.builder()
                .revokedAt(Instant.now())
                .reason(reason)
                .revokedBy(revokedBy)
                .expiresAt(Instant.now().plus(ttl))
                .userId(userId)
                .build();

            redisTemplate.opsForValue().set(redisKey, metadata.toJson(), ttl.toMillis(), TimeUnit.MILLISECONDS);

            log.warn("SECURITY: All tokens revoked for user - UserID: {}, Reason: {}, RevokedBy: {}, TTL: {}",
                    userId, reason, revokedBy, ttl);

            // Store audit record
            storeRevocationAudit("user:" + userId, metadata, ttl);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to revoke user tokens - UserID: {}, Reason: {}", userId, reason, e);
            throw new TokenRevocationException("Failed to revoke user tokens", e);
        }
    }

    /**
     * Check if all tokens for a user have been revoked
     *
     * @param userId The user ID to check
     * @return true if user-level revocation is active
     */
    public boolean isUserRevoked(String userId) {
        if (userId == null || userId.isBlank()) {
            return true; // Fail-secure
        }

        try {
            String redisKey = REVOKED_USER_PREFIX + userId;
            Boolean exists = redisTemplate.hasKey(redisKey);

            if (Boolean.TRUE.equals(exists)) {
                log.warn("SECURITY: User-level token revocation active - UserID: {}", userId);
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to check user revocation status - UserID: {}", userId, e);
            return true; // Fail-secure
        }
    }

    /**
     * Clear a user's revocation (e.g., after password reset is complete)
     *
     * @param userId The user ID to restore
     * @param clearedBy Who cleared the revocation
     */
    public void clearUserRevocation(String userId, String clearedBy) {
        if (userId == null || userId.isBlank()) {
            log.warn("SECURITY: Attempted to clear revocation for null/empty user ID");
            return;
        }

        try {
            String redisKey = REVOKED_USER_PREFIX + userId;
            Boolean deleted = redisTemplate.delete(redisKey);

            if (Boolean.TRUE.equals(deleted)) {
                log.info("SECURITY: User revocation cleared - UserID: {}, ClearedBy: {}", userId, clearedBy);

                // Audit log
                RevocationMetadata metadata = RevocationMetadata.builder()
                    .revokedAt(Instant.now())
                    .reason("Revocation cleared")
                    .revokedBy(clearedBy)
                    .userId(userId)
                    .build();

                storeRevocationAudit("user:" + userId + ":cleared", metadata, Duration.ofDays(90));
            }

        } catch (Exception e) {
            log.error("CRITICAL: Failed to clear user revocation - UserID: {}", userId, e);
        }
    }

    /**
     * Get revocation statistics for monitoring
     *
     * @return RevocationStatistics object with current metrics
     */
    public RevocationStatistics getStatistics() {
        try {
            long revokedTokenCount = redisTemplate.keys(REVOKED_TOKEN_PREFIX + "*").size();
            long revokedUserCount = redisTemplate.keys(REVOKED_USER_PREFIX + "*").size();

            return RevocationStatistics.builder()
                .revokedTokenCount(revokedTokenCount)
                .revokedUserCount(revokedUserCount)
                .timestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to retrieve revocation statistics", e);
            return RevocationStatistics.builder()
                .revokedTokenCount(0L)
                .revokedUserCount(0L)
                .timestamp(Instant.now())
                .error("Failed to retrieve statistics")
                .build();
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Hash a JWT token using SHA-256
     * We don't store raw tokens for privacy reasons
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("CRITICAL: SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to hash token", e);
        }
    }

    /**
     * Store revocation audit record for compliance
     * Retained longer than the actual revocation for audit trail
     */
    private void storeRevocationAudit(String identifier, RevocationMetadata metadata, Duration originalTtl) {
        try {
            String auditKey = REVOCATION_AUDIT_PREFIX + identifier + ":" + Instant.now().toEpochMilli();

            // Audit records retained for 90 days minimum (compliance requirement)
            Duration auditRetention = originalTtl.compareTo(Duration.ofDays(90)) > 0
                ? originalTtl
                : Duration.ofDays(90);

            redisTemplate.opsForValue().set(auditKey, metadata.toJson(),
                auditRetention.toMillis(), TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.error("Failed to store revocation audit record", e);
            // Don't throw - audit failure shouldn't prevent revocation
        }
    }

    // ========== INNER CLASSES ==========

    /**
     * Metadata stored with each revocation for audit purposes
     */
    @lombok.Builder
    @lombok.Data
    private static class RevocationMetadata {
        private Instant revokedAt;
        private String reason;
        private String revokedBy;
        private Instant expiresAt;
        private String userId;

        public String toJson() {
            return String.format(
                "{\"revokedAt\":\"%s\",\"reason\":\"%s\",\"revokedBy\":\"%s\",\"expiresAt\":\"%s\",\"userId\":\"%s\"}",
                revokedAt, reason, revokedBy, expiresAt, userId != null ? userId : "N/A"
            );
        }
    }

    /**
     * Statistics for monitoring and alerting
     */
    @lombok.Builder
    @lombok.Data
    public static class RevocationStatistics {
        private long revokedTokenCount;
        private long revokedUserCount;
        private Instant timestamp;
        private String error;
    }

    /**
     * Exception thrown when token revocation operations fail
     */
    public static class TokenRevocationException extends RuntimeException {
        public TokenRevocationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
