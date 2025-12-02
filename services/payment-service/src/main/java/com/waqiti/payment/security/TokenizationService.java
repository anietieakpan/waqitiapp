package com.waqiti.payment.security;

import com.waqiti.payment.entity.PaymentToken;
import com.waqiti.payment.repository.PaymentTokenRepository;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.audit.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * PRODUCTION-GRADE Tokenization Service for secure payment method storage.
 *
 * SECURITY FEATURES:
 * - Token ownership verification (prevents IDOR)
 * - PCI-DSS compliant token management
 * - Token expiration handling
 * - Token revocation support
 * - Comprehensive audit logging
 * - Redis caching for performance (5-min TTL)
 * - Token format validation
 *
 * TOKEN FORMAT: tok_live_{UUID}_{checksum} or tok_test_{UUID}_{checksum}
 * EXAMPLE: tok_live_550e8400-e29b-41d4-a716-446655440000_a3c9f1
 *
 * PCI-DSS COMPLIANCE:
 * - Tokens stored separately from card data
 * - No raw card numbers in this service
 * - All access logged for audit trail
 * - Token lifecycle management
 * - Automatic token expiration
 *
 * @author Waqiti Security Team
 * @version 3.0 - Enterprise Production
 * @since 2025-10-11
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenizationService {

    private final PaymentTokenRepository paymentTokenRepository;
    private final CacheService cacheService;
    private final AuditService auditService;

    private static final String CACHE_PREFIX = "payment:token:ownership:";
    private static final int CACHE_TTL_MINUTES = 5;
    private static final String TOKEN_PREFIX_LIVE = "tok_live_";
    private static final String TOKEN_PREFIX_TEST = "tok_test_";

    /**
     * Verifies that a user owns a specific payment token.
     *
     * SECURITY: This is the critical method preventing IDOR attacks on payment methods.
     *
     * @param userId User attempting to access the token
     * @param token Payment token identifier
     * @return true if user owns the token and it's valid, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean verifyTokenOwnership(UUID userId, String token) {
        if (userId == null || token == null || token.trim().isEmpty()) {
            log.warn("SECURITY: Invalid token ownership verification request - userId: {}, token: {}",
                userId, token != null ? "present" : "null");
            return false;
        }

        // SECURITY: Validate token format first (prevents injection attacks)
        if (!isValidTokenFormat(token)) {
            log.error("SECURITY: Invalid token format detected - token: {}, userId: {}",
                maskToken(token), userId);
            auditSecurityViolation(userId, token, "INVALID_TOKEN_FORMAT");
            return false;
        }

        // PERFORMANCE: Check cache first (5-min TTL reduces DB load)
        String cacheKey = CACHE_PREFIX + userId + ":" + token;
        Optional<Boolean> cachedResult = cacheService.get(cacheKey, Boolean.class);

        if (cachedResult.isPresent()) {
            log.debug("Token ownership verification from cache - userId: {}, token: {}, result: {}",
                userId, maskToken(token), cachedResult.get());
            return cachedResult.get();
        }

        // DATABASE: Verify ownership
        Optional<PaymentToken> paymentTokenOpt = paymentTokenRepository
            .findByTokenAndUserId(token, userId);

        if (paymentTokenOpt.isEmpty()) {
            log.warn("SECURITY: IDOR attempt detected - User {} attempted to access token {} (not owned or doesn't exist)",
                userId, maskToken(token));

            // AUDIT: Log potential IDOR attack
            auditSecurityViolation(userId, token, "TOKEN_NOT_OWNED");

            // CACHE: Cache negative result to prevent repeated attacks
            cacheService.put(cacheKey, false, CACHE_TTL_MINUTES);
            return false;
        }

        PaymentToken paymentToken = paymentTokenOpt.get();

        // SECURITY: Check if token is expired
        if (isTokenExpired(paymentToken)) {
            log.warn("SECURITY: User {} attempted to use expired token {}",
                userId, maskToken(token));
            auditSecurityViolation(userId, token, "TOKEN_EXPIRED");
            cacheService.put(cacheKey, false, CACHE_TTL_MINUTES);
            return false;
        }

        // SECURITY: Check if token is revoked
        if (isTokenRevoked(paymentToken)) {
            log.warn("SECURITY: User {} attempted to use revoked token {}",
                userId, maskToken(token));
            auditSecurityViolation(userId, token, "TOKEN_REVOKED");
            cacheService.put(cacheKey, false, CACHE_TTL_MINUTES);
            return false;
        }

        // SUCCESS: Token is valid and owned by user
        log.info("AUDIT: Token ownership verified - userId: {}, token: {}, tokenType: {}",
            userId, maskToken(token), paymentToken.getTokenType());

        // Update last accessed timestamp
        updateTokenLastAccessed(paymentToken);

        // CACHE: Cache positive result
        cacheService.put(cacheKey, true, CACHE_TTL_MINUTES);

        // AUDIT: Log successful access
        auditService.logAction(
            AuditAction.PAYMENT_TOKEN_ACCESSED,
            userId,
            paymentToken.getId(),
            "Token ownership verified and accessed"
        );

        return true;
    }

    /**
     * Validates token format to prevent injection attacks.
     *
     * VALID FORMATS:
     * - tok_live_{UUID}_{checksum}
     * - tok_test_{UUID}_{checksum}
     */
    private boolean isValidTokenFormat(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        // Must start with valid prefix
        if (!token.startsWith(TOKEN_PREFIX_LIVE) && !token.startsWith(TOKEN_PREFIX_TEST)) {
            return false;
        }

        // Must have 3 parts separated by underscores
        String[] parts = token.split("_");
        if (parts.length != 4) { // ["tok", "live/test", "UUID", "checksum"]
            return false;
        }

        // Validate UUID part
        try {
            UUID.fromString(parts[2]);
        } catch (IllegalArgumentException e) {
            return false;
        }

        // Validate checksum part (6 character hex)
        String checksum = parts[3];
        if (checksum.length() != 6 || !checksum.matches("[a-f0-9]{6}")) {
            return false;
        }

        return true;
    }

    /**
     * Checks if token has expired based on expiration timestamp.
     */
    private boolean isTokenExpired(PaymentToken token) {
        if (token.getExpiresAt() == null) {
            return false; // No expiration set
        }
        return LocalDateTime.now().isAfter(token.getExpiresAt());
    }

    /**
     * Checks if token has been revoked.
     */
    private boolean isTokenRevoked(PaymentToken token) {
        return token.getStatus() != null &&
               "REVOKED".equals(token.getStatus().name());
    }

    /**
     * Updates last accessed timestamp for token usage tracking.
     */
    @Transactional
    public void updateTokenLastAccessed(PaymentToken token) {
        try {
            token.setLastAccessedAt(LocalDateTime.now());
            paymentTokenRepository.save(token);
            log.debug("Token last accessed timestamp updated - tokenId: {}", token.getId());
        } catch (Exception e) {
            // Non-critical operation - log but don't fail
            log.error("Failed to update token last accessed timestamp - tokenId: {}",
                token.getId(), e);
        }
    }

    /**
     * Masks token for logging (PCI-DSS compliance - no full tokens in logs).
     *
     * Example: tok_live_550e8400-e29b-41d4-a716-446655440000_a3c9f1
     * Becomes: tok_live_***************0000_***
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 16) {
            return "***";
        }

        String[] parts = token.split("_");
        if (parts.length == 4) {
            String prefix = parts[0] + "_" + parts[1];
            String uuidPart = parts[2];
            // Show only last 4 chars of UUID
            String maskedUuid = "***************" + uuidPart.substring(uuidPart.length() - 4);
            return prefix + "_" + maskedUuid + "_***";
        }

        // Fallback: show first and last 4 characters
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }

    /**
     * Audits security violations for compliance and monitoring.
     */
    private void auditSecurityViolation(UUID userId, String token, String violationType) {
        log.error("SECURITY AUDIT: Token Violation - Type: {}, UserId: {}, Token: {}, Timestamp: {}",
            violationType, userId, maskToken(token), LocalDateTime.now());

        auditService.logSecurityViolation(
            violationType,
            userId,
            null,
            "Token access violation: " + violationType + " for token " + maskToken(token)
        );
    }

    /**
     * Retrieves token metadata (for admin/support use).
     *
     * @param token Token identifier
     * @return PaymentToken entity or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<PaymentToken> getTokenMetadata(String token) {
        if (!isValidTokenFormat(token)) {
            return Optional.empty();
        }
        return paymentTokenRepository.findByToken(token);
    }

    /**
     * Revokes a token (prevents future use).
     *
     * @param userId User revoking the token
     * @param token Token to revoke
     * @return true if revoked successfully, false if not found or not owned
     */
    @Transactional
    public boolean revokeToken(UUID userId, String token) {
        Optional<PaymentToken> tokenOpt = paymentTokenRepository.findByTokenAndUserId(token, userId);

        if (tokenOpt.isEmpty()) {
            log.warn("SECURITY: User {} attempted to revoke token {} (not found or not owned)",
                userId, maskToken(token));
            return false;
        }

        PaymentToken paymentToken = tokenOpt.get();
        paymentToken.setStatus(PaymentToken.TokenStatus.REVOKED);
        paymentToken.setRevokedAt(LocalDateTime.now());
        paymentTokenRepository.save(paymentToken);

        // Invalidate cache
        String cacheKey = CACHE_PREFIX + userId + ":" + token;
        cacheService.evict(cacheKey);

        log.info("AUDIT: Token revoked - userId: {}, token: {}", userId, maskToken(token));

        auditService.logAction(
            AuditAction.PAYMENT_TOKEN_REVOKED,
            userId,
            paymentToken.getId(),
            "User revoked payment token"
        );

        return true;
    }

    /**
     * Lists all active tokens for a user (for UI display).
     *
     * @param userId User ID
     * @return List of active payment tokens (non-revoked, non-expired)
     */
    @Transactional(readOnly = true)
    public java.util.List<PaymentToken> listUserTokens(UUID userId) {
        return paymentTokenRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
            userId,
            PaymentToken.TokenStatus.ACTIVE
        );
    }
}
