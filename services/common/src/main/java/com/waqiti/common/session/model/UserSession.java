package com.waqiti.common.session.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * User session model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {
    
    private String sessionId;
    private String userId;
    private String username;
    private String deviceId;
    private String deviceType;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
    private Instant lastActivityAt;
    private Instant expiresAt;
    private SessionStatus status;
    private Set<String> roles;
    private Set<String> permissions;
    private Map<String, Object> attributes;
    private String refreshToken;
    private String accessToken;
    private GeoLocation location;
    private boolean mfaVerified;
    private String mfaMethod;
    private int failedAttempts;
    private Instant lockedUntil;
    
    /**
     * Check if session is active
     */
    public boolean isActive() {
        return status == SessionStatus.ACTIVE && 
               (expiresAt == null || Instant.now().isBefore(expiresAt));
    }
    
    /**
     * Check if session is expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Check if session is locked
     */
    public boolean isLocked() {
        return status == SessionStatus.LOCKED || 
               (lockedUntil != null && Instant.now().isBefore(lockedUntil));
    }
    
    /**
     * Update last activity timestamp
     */
    public void updateActivity() {
        this.lastActivityAt = Instant.now();
    }
    
    /**
     * Get session duration in seconds
     */
    public long getSessionDurationSeconds() {
        if (createdAt == null) return 0;
        Instant endTime = lastActivityAt != null ? lastActivityAt : Instant.now();
        return endTime.getEpochSecond() - createdAt.getEpochSecond();
    }
}

/**
 * Session status enumeration
 */
enum SessionStatus {
    ACTIVE,
    INACTIVE,
    EXPIRED,
    LOCKED,
    TERMINATED,
    SUSPENDED
}