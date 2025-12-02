package com.waqiti.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Security metrics for monitoring and reporting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityMetrics {
    
    // Authentication metrics
    private Long totalLoginAttempts;
    private Long successfulLogins;
    private Long failedLogins;
    private Long blockedLogins;
    private Double loginSuccessRate;
    private Long averageLoginTimeMs;
    private Long multiFactorAuthUsage;
    
    // Authorization metrics
    private Long totalAuthorizationChecks;
    private Long authorizedRequests;
    private Long deniedRequests;
    private Double authorizationSuccessRate;
    private Long privilegeEscalationAttempts;
    
    // Security events metrics
    private Long totalSecurityEvents;
    private Map<String, Long> eventsByType;
    private Map<String, Long> eventsBySeverity;
    private Long criticalEvents;
    private Long highSeverityEvents;
    private Long suspiciousActivities;
    
    // Attack metrics
    private Long bruteForceAttempts;
    private Long sqlInjectionAttempts;
    private Long xssAttempts;
    private Long csrfAttempts;
    private Long ddosAttempts;
    private Long maliciousRequests;
    private Map<String, Long> threatsByLevel;
    private java.util.List<String> topThreatIndicators;
    private java.time.Duration period;
    
    // User behavior metrics
    private Long uniqueUsers;
    private Long activeUsers;
    private Long suspiciousUsers;
    private Long blockedUsers;
    private Long compromisedAccounts;
    private Double averageSessionDuration;
    
    // Geographic metrics
    private Map<String, Long> requestsByCountry;
    private Long internationalRequests;
    private Long vpnDetections;
    private Long torDetections;
    private Long proxyDetections;
    private Long geoAnomalies;
    
    // Rate limiting metrics
    private Long rateLimitExceeded;
    private Long throttledRequests;
    private Long blockedIpAddresses;
    private Map<String, Long> rateLimitsByEndpoint;
    
    // Compliance metrics
    private Long gdprRequests;
    private Long dataBreaches;
    private Long complianceViolations;
    private Long auditEvents;
    private Double complianceScore;
    
    // System security metrics
    private Long encryptionOperations;
    private Long decryptionOperations;
    private Long keyRotations;
    private Long certificateExpirations;
    private Long securityPatches;
    private Long vulnerabilitiesDetected;
    
    // Response metrics
    private Long incidentsCreated;
    private Long incidentsResolved;
    private Long averageResponseTimeMs;
    private Long averageResolutionTimeMs;
    private Double falsePositiveRate;
    
    // Performance metrics
    private Long securityChecksPerSecond;
    private Long averageCheckLatencyMs;
    private Long peakRequestsPerSecond;
    private Double cpuUsagePercent;
    private Double memoryUsagePercent;
    
    // Time window
    private Instant startTime;
    private Instant endTime;
    private String timeWindow; // HOURLY, DAILY, WEEKLY, MONTHLY
    private LocalDateTime calculatedAt;
    
    /**
     * Calculate overall security score (0-100)
     */
    public Double calculateSecurityScore() {
        double score = 100.0;
        
        // Deduct points for failed logins
        if (failedLogins != null && totalLoginAttempts != null && totalLoginAttempts > 0) {
            double failureRate = (double) failedLogins / totalLoginAttempts;
            score -= failureRate * 20;
        }
        
        // Deduct points for denied requests
        if (deniedRequests != null && totalAuthorizationChecks != null && totalAuthorizationChecks > 0) {
            double denialRate = (double) deniedRequests / totalAuthorizationChecks;
            score -= denialRate * 15;
        }
        
        // Deduct points for critical events
        if (criticalEvents != null && criticalEvents > 0) {
            score -= Math.min(criticalEvents * 5, 25);
        }
        
        // Deduct points for attacks
        long totalAttacks = (bruteForceAttempts != null ? bruteForceAttempts : 0) +
                           (sqlInjectionAttempts != null ? sqlInjectionAttempts : 0) +
                           (xssAttempts != null ? xssAttempts : 0);
        if (totalAttacks > 0) {
            score -= Math.min(totalAttacks * 2, 20);
        }
        
        // Deduct points for compromised accounts
        if (compromisedAccounts != null && compromisedAccounts > 0) {
            score -= Math.min(compromisedAccounts * 10, 30);
        }
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Check if metrics indicate high risk
     */
    public boolean indicatesHighRisk() {
        return (criticalEvents != null && criticalEvents > 0) ||
               (compromisedAccounts != null && compromisedAccounts > 0) ||
               (dataBreaches != null && dataBreaches > 0) ||
               calculateSecurityScore() < 70;
    }
    
    /**
     * Get attack rate per hour
     */
    public Double getAttackRate() {
        if (startTime == null || endTime == null) {
            return 0.0;
        }
        
        long totalAttacks = (bruteForceAttempts != null ? bruteForceAttempts : 0) +
                           (sqlInjectionAttempts != null ? sqlInjectionAttempts : 0) +
                           (xssAttempts != null ? xssAttempts : 0) +
                           (csrfAttempts != null ? csrfAttempts : 0);
        
        long durationHours = (endTime.getEpochSecond() - startTime.getEpochSecond()) / 3600;
        if (durationHours == 0) {
            durationHours = 1;
        }
        
        return (double) totalAttacks / durationHours;
    }
}