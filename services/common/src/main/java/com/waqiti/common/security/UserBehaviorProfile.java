package com.waqiti.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * User behavior profile for anomaly detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorProfile {
    
    private UUID userId;
    private String username;
    private Instant profileCreated;
    private Instant lastUpdated;
    private Set<String> typicalIpAddresses;
    private Set<String> typicalLocations;
    private Set<String> typicalDevices;
    private Set<String> typicalUserAgents;
    private List<String> frequentEndpoints;
    private Map<String, Double> endpointFrequency;
    private LocalTime typicalLoginTimeStart;
    private LocalTime typicalLoginTimeEnd;
    private Set<String> typicalDaysOfWeek;
    private Double averageSessionDuration;
    private Integer averageRequestsPerSession;
    private Double averageTransactionAmount;
    private Set<String> preferredPaymentMethods;
    private Map<String, Object> behaviorMetrics;
    private String riskScore;
    private String trustLevel;
    private Integer anomalyCount;
    private Instant lastAnomalyDetected;
    private List<String> securityFlags;
    private List<SecurityEvent> events;

    public void addEvent(SecurityEvent event) {
        if (events == null) {
            events = new java.util.ArrayList<>();
        }
        events.add(event);
    }

    public void updateLoginPatterns(SecurityEvent event) {
        if (event.getTimestamp() != null) {
            LocalTime loginTime = event.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toLocalTime();
            if (typicalLoginTimeStart == null || loginTime.isBefore(typicalLoginTimeStart)) {
                typicalLoginTimeStart = loginTime;
            }
            if (typicalLoginTimeEnd == null || loginTime.isAfter(typicalLoginTimeEnd)) {
                typicalLoginTimeEnd = loginTime;
            }
        }
    }

    public void updateLocationHistory(SecurityEvent event) {
        if (event.getGeolocation() != null) {
            if (typicalLocations == null) {
                typicalLocations = new java.util.HashSet<>();
            }
            typicalLocations.add(event.getGeolocation());
        }
    }
    
    /**
     * Trust levels
     */
    public enum TrustLevel {
        VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
    }
    
    /**
     * Risk scores
     */
    public enum RiskScore {
        VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH, CRITICAL
    }
    
    /**
     * Create initial user behavior profile
     */
    public static UserBehaviorProfile createInitial(UUID userId, String username) {
        return UserBehaviorProfile.builder()
            .userId(userId)
            .username(username)
            .profileCreated(Instant.now())
            .lastUpdated(Instant.now())
            .riskScore(RiskScore.MEDIUM.name())
            .trustLevel(TrustLevel.MEDIUM.name())
            .anomalyCount(0)
            .build();
    }
    
    /**
     * Check if IP address is typical for user
     */
    public boolean isTypicalIpAddress(String ipAddress) {
        return typicalIpAddresses != null && typicalIpAddresses.contains(ipAddress);
    }
    
    /**
     * Check if location is typical for user
     */
    public boolean isTypicalLocation(String location) {
        return typicalLocations != null && typicalLocations.contains(location);
    }
    
    /**
     * Check if device is typical for user
     */
    public boolean isTypicalDevice(String device) {
        return typicalDevices != null && typicalDevices.contains(device);
    }
    
    /**
     * Check if user agent is typical for user
     */
    public boolean isTypicalUserAgent(String userAgent) {
        return typicalUserAgents != null && typicalUserAgents.contains(userAgent);
    }
    
    /**
     * Check if login time is typical for user
     */
    public boolean isTypicalLoginTime(LocalTime loginTime) {
        if (typicalLoginTimeStart == null || typicalLoginTimeEnd == null) {
            return true; // No pattern established yet
        }
        
        if (typicalLoginTimeStart.isBefore(typicalLoginTimeEnd)) {
            return !loginTime.isBefore(typicalLoginTimeStart) && !loginTime.isAfter(typicalLoginTimeEnd);
        } else {
            // Spans midnight
            return !loginTime.isBefore(typicalLoginTimeStart) || !loginTime.isAfter(typicalLoginTimeEnd);
        }
    }
    
    /**
     * Check if endpoint access is typical for user
     */
    public boolean isTypicalEndpoint(String endpoint) {
        return frequentEndpoints != null && frequentEndpoints.contains(endpoint);
    }
    
    /**
     * Get endpoint access frequency
     */
    public Double getEndpointFrequency(String endpoint) {
        if (endpointFrequency == null) {
            return 0.0;
        }
        return endpointFrequency.getOrDefault(endpoint, 0.0);
    }
    
    /**
     * Check if user has high trust level
     */
    public boolean isHighTrust() {
        return TrustLevel.HIGH.name().equals(trustLevel) || 
               TrustLevel.VERY_HIGH.name().equals(trustLevel);
    }
    
    /**
     * Check if user has high risk score
     */
    public boolean isHighRisk() {
        return RiskScore.HIGH.name().equals(riskScore) || 
               RiskScore.VERY_HIGH.name().equals(riskScore) ||
               RiskScore.CRITICAL.name().equals(riskScore);
    }
    
    /**
     * Check if user has security flags
     */
    public boolean hasSecurityFlags() {
        return securityFlags != null && !securityFlags.isEmpty();
    }
    
    /**
     * Check if user has recent anomalies
     */
    public boolean hasRecentAnomalies() {
        if (lastAnomalyDetected == null) {
            return false;
        }
        
        // Consider anomalies within last 24 hours as recent
        return lastAnomalyDetected.isAfter(Instant.now().minusSeconds(24 * 60 * 60));
    }
    
    /**
     * Increment anomaly count
     */
    public void incrementAnomalyCount() {
        if (anomalyCount == null) {
            anomalyCount = 0;
        }
        anomalyCount++;
        lastAnomalyDetected = Instant.now();
        lastUpdated = Instant.now();
    }
    
    /**
     * Calculate overall risk level based on multiple factors
     */
    public double calculateOverallRisk() {
        double risk = 0.0;
        
        // Base risk from risk score
        switch (RiskScore.valueOf(riskScore)) {
            case VERY_LOW -> risk += 0.1;
            case LOW -> risk += 0.3;
            case MEDIUM -> risk += 0.5;
            case HIGH -> risk += 0.7;
            case VERY_HIGH -> risk += 0.9;
            case CRITICAL -> risk += 1.0;
        }
        
        // Increase risk based on anomaly count
        if (anomalyCount != null && anomalyCount > 0) {
            risk += Math.min(anomalyCount * 0.1, 0.3);
        }
        
        // Increase risk if has security flags
        if (hasSecurityFlags()) {
            risk += 0.2;
        }
        
        // Increase risk if has recent anomalies
        if (hasRecentAnomalies()) {
            risk += 0.2;
        }
        
        return Math.min(risk, 1.0);
    }
}