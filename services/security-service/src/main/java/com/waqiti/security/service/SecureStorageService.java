package com.waqiti.security.service;

import com.waqiti.common.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secure Storage Service
 *
 * Provides backend support for secure storage operations including
 * CSRF token management and audit logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecureStorageService {

    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    // Store CSRF tokens per session
    private final Map<String, String> csrfTokens = new ConcurrentHashMap<>();

    private static final String CSRF_TOKEN_ATTR = "CSRF_TOKEN";
    private static final int CSRF_TOKEN_LENGTH = 32;

    /**
     * Generate CSRF token for session
     */
    public String generateCSRFToken(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String sessionId = session.getId();

        // Check if token already exists for this session
        String existingToken = csrfTokens.get(sessionId);
        if (existingToken != null) {
            return existingToken;
        }

        // Generate new token
        byte[] tokenBytes = new byte[CSRF_TOKEN_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Store in session and map
        session.setAttribute(CSRF_TOKEN_ATTR, token);
        csrfTokens.put(sessionId, token);

        log.debug("Generated CSRF token for session: {}", sessionId);

        return token;
    }

    /**
     * Validate CSRF token
     */
    public boolean validateCSRFToken(String token, HttpServletRequest request) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }

        String sessionId = session.getId();
        String expectedToken = csrfTokens.get(sessionId);

        if (expectedToken == null) {
            expectedToken = (String) session.getAttribute(CSRF_TOKEN_ATTR);
        }

        boolean valid = token.equals(expectedToken);

        if (!valid) {
            log.warn("CSRF token validation failed. Session: {}, IP: {}",
                    sessionId, request.getRemoteAddr());

            // Audit failed validation
            auditStorageAccess("CSRF_VALIDATION_FAILED", "N/A", request);
        }

        return valid;
    }

    /**
     * Audit storage access
     */
    public void auditStorageAccess(String action, String key, HttpServletRequest request) {
        try {
            auditService.logSecurityEvent(
                    action,
                    "SECURE_STORAGE",
                    Map.of(
                            "key", key,
                            "ipAddress", request.getRemoteAddr(),
                            "userAgent", request.getHeader("User-Agent"),
                            "sessionId", request.getSession(false) != null ?
                                    request.getSession(false).getId() : "N/A",
                            "timestamp", System.currentTimeMillis()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit storage access", e);
        }
    }

    /**
     * Invalidate CSRF token for session
     */
    public void invalidateCSRFToken(HttpSession session) {
        if (session != null) {
            String sessionId = session.getId();
            csrfTokens.remove(sessionId);
            session.removeAttribute(CSRF_TOKEN_ATTR);
            log.debug("Invalidated CSRF token for session: {}", sessionId);
        }
    }

    /**
     * Cleanup expired tokens (should be called periodically)
     */
    public void cleanupExpiredTokens() {
        // Remove tokens for sessions that no longer exist
        // This would be called by a scheduled task
        log.debug("Cleaning up expired CSRF tokens. Current count: {}", csrfTokens.size());

        // Implementation would check session validity
        // For now, just log the count
    }
}
