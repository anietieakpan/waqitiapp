package com.waqiti.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IP address profile for threat intelligence and monitoring
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpAddressProfile {
    
    private String ipAddress;
    private String country;
    private String region;
    private String city;
    private String organization;
    private String isp;
    private Boolean isProxy;
    private Boolean isVpn;
    private Boolean isTor;
    private Boolean isHosting;
    private String threatLevel;
    private String reputation;
    private Integer riskScore;
    private Set<String> associatedUserIds;
    private Integer totalRequestCount;
    private Integer failedLoginCount;
    private Integer successfulLoginCount;
    private List<String> violationTypes;
    private Map<String, Integer> endpointAccess;
    private Instant firstSeen;
    private Instant lastSeen;
    private Instant lastViolation;
    private Boolean isBlacklisted;
    private Boolean isWhitelisted;
    private String notes;
    private Map<String, Object> threatIntelligence;
    private List<SecurityEvent> recentEvents;

    public List<SecurityEvent> getRecentEvents() {
        return recentEvents;
    }

    public void addEvent(SecurityEvent event) {
        if (recentEvents == null) {
            recentEvents = new java.util.ArrayList<>();
        }
        recentEvents.add(event);
    }

    public void updateThreatScore(SecurityEvent event) {
        if (riskScore == null) riskScore = 0;
        if (event.getSeverity() != null) {
            switch(event.getSeverity().toUpperCase()) {
                case "CRITICAL": riskScore += 10; break;
                case "HIGH": riskScore += 5; break;
                case "MEDIUM": riskScore += 2; break;
                default: riskScore += 1;
            }
        }
    }
    
    /**
     * Threat levels
     */
    public enum ThreatLevel {
        VERY_LOW, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Reputation levels
     */
    public enum Reputation {
        GOOD, NEUTRAL, SUSPICIOUS, MALICIOUS, BLOCKED
    }
    
    /**
     * Create new IP address profile
     */
    public static IpAddressProfile createNew(String ipAddress, String country, String organization) {
        return IpAddressProfile.builder()
            .ipAddress(ipAddress)
            .country(country)
            .organization(organization)
            .threatLevel(ThreatLevel.LOW.name())
            .reputation(Reputation.NEUTRAL.name())
            .riskScore(3) // Medium-low risk by default
            .totalRequestCount(0)
            .failedLoginCount(0)
            .successfulLoginCount(0)
            .firstSeen(Instant.now())
            .lastSeen(Instant.now())
            .isBlacklisted(false)
            .isWhitelisted(false)
            .build();
    }
    
    /**
     * Check if IP is high risk
     */
    public boolean isHighRisk() {
        return riskScore != null && riskScore >= 7;
    }
    
    /**
     * Check if IP is low risk
     */
    public boolean isLowRisk() {
        return riskScore != null && riskScore <= 3;
    }
    
    /**
     * Check if IP is suspicious
     */
    public boolean isSuspicious() {
        return Reputation.SUSPICIOUS.name().equals(reputation) ||
               Reputation.MALICIOUS.name().equals(reputation) ||
               isHighRisk() ||
               (failedLoginCount != null && failedLoginCount > 10) ||
               Boolean.TRUE.equals(isProxy) ||
               Boolean.TRUE.equals(isTor);
    }
    
    /**
     * Check if IP is trusted
     */
    public boolean isTrusted() {
        return Boolean.TRUE.equals(isWhitelisted) ||
               (Reputation.GOOD.name().equals(reputation) && isLowRisk());
    }
    
    /**
     * Check if IP should be blocked
     */
    public boolean shouldBeBlocked() {
        return Boolean.TRUE.equals(isBlacklisted) ||
               Reputation.BLOCKED.name().equals(reputation) ||
               ThreatLevel.CRITICAL.name().equals(threatLevel) ||
               (riskScore != null && riskScore >= 9);
    }
    
    /**
     * Calculate failure rate
     */
    public double getFailureRate() {
        if (totalRequestCount == null || totalRequestCount == 0) {
            return 0.0;
        }
        
        int failedCount = failedLoginCount != null ? failedLoginCount : 0;
        return (double) failedCount / totalRequestCount;
    }
    
    /**
     * Calculate success rate
     */
    public double getSuccessRate() {
        if (totalRequestCount == null || totalRequestCount == 0) {
            return 0.0;
        }
        
        int successCount = successfulLoginCount != null ? successfulLoginCount : 0;
        return (double) successCount / totalRequestCount;
    }
    
    /**
     * Check if IP has recent violations
     */
    public boolean hasRecentViolations() {
        if (lastViolation == null) {
            return false;
        }
        
        // Consider violations within last 24 hours as recent
        return lastViolation.isAfter(Instant.now().minusSeconds(24 * 60 * 60));
    }
    
    /**
     * Check if IP is anonymized (proxy, VPN, Tor)
     */
    public boolean isAnonymized() {
        return Boolean.TRUE.equals(isProxy) ||
               Boolean.TRUE.equals(isVpn) ||
               Boolean.TRUE.equals(isTor);
    }
    
    /**
     * Check if IP is from hosting provider
     */
    public boolean isFromHostingProvider() {
        return Boolean.TRUE.equals(isHosting);
    }
    
    /**
     * Increment request count
     */
    public void incrementRequestCount() {
        if (totalRequestCount == null) {
            totalRequestCount = 0;
        }
        totalRequestCount++;
        lastSeen = Instant.now();
    }
    
    /**
     * Increment failed login count
     */
    public void incrementFailedLoginCount() {
        if (failedLoginCount == null) {
            failedLoginCount = 0;
        }
        failedLoginCount++;
        incrementRequestCount();
    }
    
    /**
     * Increment successful login count
     */
    public void incrementSuccessfulLoginCount() {
        if (successfulLoginCount == null) {
            successfulLoginCount = 0;
        }
        successfulLoginCount++;
        incrementRequestCount();
    }
    
    /**
     * Update risk score based on behavior
     */
    public void updateRiskScore() {
        int newRiskScore = 0;
        
        // Base score from reputation
        switch (Reputation.valueOf(reputation)) {
            case GOOD -> newRiskScore += 1;
            case NEUTRAL -> newRiskScore += 3;
            case SUSPICIOUS -> newRiskScore += 6;
            case MALICIOUS -> newRiskScore += 8;
            case BLOCKED -> newRiskScore += 10;
        }
        
        // Increase based on failure rate
        double failureRate = getFailureRate();
        if (failureRate > 0.5) {
            newRiskScore += 3;
        } else if (failureRate > 0.2) {
            newRiskScore += 1;
        }
        
        // Increase if anonymized
        if (isAnonymized()) {
            newRiskScore += 2;
        }
        
        // Increase if has recent violations
        if (hasRecentViolations()) {
            newRiskScore += 2;
        }
        
        // Increase if from hosting provider
        if (isFromHostingProvider()) {
            newRiskScore += 1;
        }
        
        this.riskScore = Math.min(newRiskScore, 10);
    }
}