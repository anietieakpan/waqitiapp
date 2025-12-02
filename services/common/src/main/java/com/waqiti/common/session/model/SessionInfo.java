package com.waqiti.common.session.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Session information summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
    
    private String sessionId;
    private String userId;
    private String username;
    private String deviceType;
    private String ipAddress;
    private Instant createdAt;
    private Instant lastActivityAt;
    private Instant expiresAt;
    private String status;
    private GeoLocation location;
    private long activityCount;
    private List<String> recentActivities;
    private boolean mfaVerified;
    private String riskLevel;
    private long sessionDurationSeconds;
    
    /**
     * Create from UserSession
     */
    public static SessionInfo fromUserSession(UserSession session) {
        return SessionInfo.builder()
            .sessionId(session.getSessionId())
            .userId(session.getUserId())
            .username(session.getUsername())
            .deviceType(session.getDeviceType())
            .ipAddress(session.getIpAddress())
            .createdAt(session.getCreatedAt())
            .lastActivityAt(session.getLastActivityAt())
            .expiresAt(session.getExpiresAt())
            .status(session.getStatus() != null ? session.getStatus().name() : "UNKNOWN")
            .location(session.getLocation())
            .mfaVerified(session.isMfaVerified())
            .sessionDurationSeconds(session.getSessionDurationSeconds())
            .build();
    }
    
    /**
     * Check if session is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(status) && 
               (expiresAt == null || Instant.now().isBefore(expiresAt));
    }
    
    /**
     * Get time since last activity in seconds
     */
    public long getIdleTimeSeconds() {
        if (lastActivityAt == null) return 0;
        return Instant.now().getEpochSecond() - lastActivityAt.getEpochSecond();
    }
}