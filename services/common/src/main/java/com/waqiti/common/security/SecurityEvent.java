package com.waqiti.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Security event for monitoring and threat detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEvent {
    
    private UUID eventId;
    private UUID userId;
    private String eventType;
    private String severity;
    private String category;
    private String description;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private String endpoint;
    private String method;
    private Integer statusCode;
    private String sourceSystem;
    private Instant timestamp;
    private Map<String, Object> context;
    private Map<String, Object> metadata;
    private UUID correlationId;
    private String threatLevel;
    private String action;
    private String outcome;
    private Long responseTime;
    private String geolocation;
    private String deviceFingerprint;
    
    /**
     * Security event types
     */
    public enum EventType {
        AUTHENTICATION_SUCCESS,
        AUTHENTICATION_FAILURE,
        AUTHORIZATION_FAILURE,
        BRUTE_FORCE_ATTEMPT,
        SUSPICIOUS_LOGIN,
        PRIVILEGE_ESCALATION,
        DATA_ACCESS_VIOLATION,
        RATE_LIMIT_EXCEEDED,
        MALICIOUS_REQUEST,
        SQL_INJECTION_ATTEMPT,
        XSS_ATTEMPT,
        CSRF_ATTEMPT,
        SUSPICIOUS_BEHAVIOR,
        ACCOUNT_LOCKOUT,
        PASSWORD_POLICY_VIOLATION,
        SESSION_HIJACK,
        TOKEN_THEFT,
        UNUSUAL_LOCATION,
        UNUSUAL_DEVICE,
        MULTIPLE_FAILED_LOGINS,
        ADMIN_ACTION,
        SENSITIVE_DATA_ACCESS,
        EXPORT_ATTEMPT,
        CONFIGURATION_CHANGE,
        SECURITY_POLICY_VIOLATION
    }
    
    /**
     * Severity levels
     */
    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Create authentication failure event
     */
    public static SecurityEvent authenticationFailure(UUID userId, String ipAddress, String userAgent, String reason) {
        return SecurityEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.AUTHENTICATION_FAILURE.name())
            .severity(Severity.MEDIUM.name())
            .category("AUTHENTICATION")
            .description("Authentication failed: " + reason)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create brute force attempt event
     */
    public static SecurityEvent bruteForceAttempt(String ipAddress, String endpoint, int attemptCount) {
        return SecurityEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(EventType.BRUTE_FORCE_ATTEMPT.name())
            .severity(Severity.HIGH.name())
            .category("ATTACK")
            .description("Brute force attempt detected: " + attemptCount + " attempts")
            .ipAddress(ipAddress)
            .endpoint(endpoint)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create authorization failure event
     */
    public static SecurityEvent authorizationFailure(UUID userId, String endpoint, String method, String reason) {
        return SecurityEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.AUTHORIZATION_FAILURE.name())
            .severity(Severity.MEDIUM.name())
            .category("AUTHORIZATION")
            .description("Authorization failed: " + reason)
            .endpoint(endpoint)
            .method(method)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create suspicious behavior event
     */
    public static SecurityEvent suspiciousBehavior(UUID userId, String behavior, String ipAddress) {
        return SecurityEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.SUSPICIOUS_BEHAVIOR.name())
            .severity(Severity.HIGH.name())
            .category("BEHAVIOR")
            .description("Suspicious behavior detected: " + behavior)
            .ipAddress(ipAddress)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create rate limit exceeded event
     */
    public static SecurityEvent rateLimitExceeded(String ipAddress, String endpoint, int requestCount) {
        return SecurityEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(EventType.RATE_LIMIT_EXCEEDED.name())
            .severity(Severity.MEDIUM.name())
            .category("RATE_LIMITING")
            .description("Rate limit exceeded: " + requestCount + " requests")
            .ipAddress(ipAddress)
            .endpoint(endpoint)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Check if event is critical
     */
    public boolean isCritical() {
        return Severity.CRITICAL.name().equals(severity);
    }
    
    /**
     * Check if event is high severity
     */
    public boolean isHighSeverity() {
        return Severity.HIGH.name().equals(severity) || isCritical();
    }
    
    /**
     * Check if event indicates an attack
     */
    public boolean isAttack() {
        return "ATTACK".equals(category) || 
               eventType != null && (
                   eventType.contains("INJECTION") ||
                   eventType.contains("XSS") ||
                   eventType.contains("CSRF") ||
                   eventType.contains("BRUTE_FORCE")
               );
    }
    
    /**
     * Check if event is authentication related
     */
    public boolean isAuthenticationEvent() {
        return "AUTHENTICATION".equals(category) || 
               (eventType != null && eventType.contains("AUTHENTICATION"));
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
}