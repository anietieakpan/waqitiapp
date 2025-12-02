package com.waqiti.common.session.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Result of session validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionValidationResult {
    
    private boolean valid;
    private String sessionId;
    private String userId;
    private String validationResult;
    private List<String> validationErrors;
    private UserSession session;
    private Instant validatedAt;
    private boolean sessionExtended;
    private Instant newExpiryTime;
    private String riskLevel;
    private boolean requiresReauthentication;
    private String reauthenticationReason;
    
    /**
     * Create successful validation result
     */
    public static SessionValidationResult success(String sessionId, UserSession session) {
        return SessionValidationResult.builder()
            .valid(true)
            .sessionId(sessionId)
            .userId(session.getUserId())
            .session(session)
            .validationResult("Session validated successfully")
            .validatedAt(Instant.now())
            .build();
    }
    
    /**
     * Create failed validation result
     */
    public static SessionValidationResult failure(String sessionId, String reason) {
        return SessionValidationResult.builder()
            .valid(false)
            .sessionId(sessionId)
            .validationResult("Session validation failed")
            .validationErrors(List.of(reason))
            .validatedAt(Instant.now())
            .build();
    }
    
    /**
     * Create expired session result
     */
    public static SessionValidationResult expired(String sessionId) {
        return SessionValidationResult.builder()
            .valid(false)
            .sessionId(sessionId)
            .validationResult("Session expired")
            .validationErrors(List.of("Session has expired"))
            .requiresReauthentication(true)
            .reauthenticationReason("Session expired")
            .validatedAt(Instant.now())
            .build();
    }
    
    /**
     * Create suspicious session result
     */
    public static SessionValidationResult suspicious(String sessionId, String reason) {
        return SessionValidationResult.builder()
            .valid(false)
            .sessionId(sessionId)
            .validationResult("Suspicious session activity detected")
            .validationErrors(List.of(reason))
            .riskLevel("HIGH")
            .requiresReauthentication(true)
            .reauthenticationReason("Security verification required")
            .validatedAt(Instant.now())
            .build();
    }
    
    /**
     * Check if reauthentication is needed
     */
    public boolean needsReauthentication() {
        return !valid || requiresReauthentication;
    }
}