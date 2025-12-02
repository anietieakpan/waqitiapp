package com.waqiti.common.session.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Session statistics and metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatistics {
    
    private long totalActiveSessions;
    private long totalInactiveSessions;
    private long totalExpiredSessions;
    private long totalLockedSessions;
    private long totalSessionsCreatedToday;
    private long totalSessionsTerminatedToday;
    private double averageSessionDurationMinutes;
    private long maxConcurrentSessions;
    private Map<String, Long> sessionsByDeviceType;
    private Map<String, Long> sessionsByCountry;
    private Map<String, Long> sessionsByStatus;
    private long suspiciousSessionCount;
    private long mfaVerifiedSessionCount;
    private Instant statisticsGeneratedAt;
    private Instant periodStart;
    private Instant periodEnd;
    
    /**
     * Get total sessions
     */
    public long getTotalSessions() {
        return totalActiveSessions + totalInactiveSessions + 
               totalExpiredSessions + totalLockedSessions;
    }
    
    /**
     * Get active session percentage
     */
    public double getActiveSessionPercentage() {
        long total = getTotalSessions();
        if (total == 0) return 0.0;
        return (double) totalActiveSessions / total * 100;
    }
    
    /**
     * Get MFA adoption rate
     */
    public double getMfaAdoptionRate() {
        long total = getTotalSessions();
        if (total == 0) return 0.0;
        return (double) mfaVerifiedSessionCount / total * 100;
    }
    
    /**
     * Check if there are security concerns
     */
    public boolean hasSecurityConcerns() {
        return suspiciousSessionCount > 0 || 
               totalLockedSessions > getTotalSessions() * 0.1;
    }
    
    /**
     * Get most common device type
     */
    public String getMostCommonDeviceType() {
        if (sessionsByDeviceType == null || sessionsByDeviceType.isEmpty()) {
            return "UNKNOWN";
        }
        
        return sessionsByDeviceType.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");
    }
}