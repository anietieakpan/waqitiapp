package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive IP fraud analysis with geolocation, reputation, and behavioral patterns
 */
@Data
@Builder
@Jacksonized
public class IpFraudAnalysis {
    
    private String ipAddress;
    private double riskScore;
    private IpRiskLevel riskLevel;
    private String riskLevelString;
    private double confidence;
    private boolean inFraudNetwork;
    private String analysisError;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant analysisTimestamp;

    // IP Reputation Analysis
    private IpReputationResult reputation;
    private IpReputationResult reputationResult;
    
    // Proxy/VPN Detection
    private ProxyDetectionResult proxyDetection;
    private ProxyDetectionResult proxyResult;

    // Geolocation Analysis
    private IpGeolocationResult geolocation;
    private IpGeolocationResult geolocationResult;

    // Velocity Analysis
    private IpVelocityResult velocity;
    private IpVelocityResult velocityResult;
    
    // Behavioral Patterns
    private IpBehavioralAnalysis behavioralAnalysis;
    
    // Threat Intelligence
    private ThreatIntelligenceResult threatIntelligence;
    
    // Historical Analysis
    private IpHistoricalAnalysis historicalAnalysis;
    
    /**
     * IP reputation analysis from multiple threat intelligence sources
     */
    @Data
    @Builder
    @Jacksonized
    public static class IpReputationResult {
        private boolean isBlacklisted;
        private boolean isMalicious;
        private boolean isCompromised;
        private double reputationScore;
        private List<String> blacklistSources;
        private List<String> categories;
        private Map<String, Object> threatDetails;
        private String lastSeenThreat;
        private int threatConfidence;
        private Set<String> associatedMalware;
        private List<String> reportingSources;
    }

    /**
     * PRODUCTION FIX: Removed redundant inner class ProxyDetectionResult
     * Using standalone com.waqiti.common.fraud.model.ProxyDetectionResult instead
     * which is more comprehensive (19 fields vs 15 fields)
     */

    /**
     * Comprehensive geolocation analysis
     */
    @Data
    @Builder
    @Jacksonized
    public static class IpGeolocationResult {
        private String ipAddress;
        private String country;
        private String countryCode;
        private String region;
        private String city;
        private String postalCode;
        private double latitude;
        private double longitude;
        private String timezone;
        private String ispName;
        private String isp; // Alias for ispName
        private String organizationName;
        private String asn;
        private int accuracyRadius;
        private boolean isHighRiskCountry;
        private boolean isHighRiskIsp;
        private boolean isSanctionedCountry;
        private String riskReason;
        private double distanceFromUser;
        private boolean isPossibleVpnExit;
        private List<String> riskFactors;
        private double locationRisk;
    }
    
    /**
     * IP velocity and frequency analysis
     */
    @Data
    @Builder
    @Jacksonized
    public static class IpVelocityResult {
        private int transactionsLast1Hour;
        private int transactionsLast24Hours;
        private int transactionsLast7Days;
        private int uniqueAccountsLast24Hours;
        private int uniqueUsersLast24Hours;
        private double averageTransactionAmount;
        private boolean velocityExceeded;
        private String velocityThresholdBreached;
        private double velocityRiskScore;
        private List<String> suspiciousPatterns;
        private boolean rapidTransactionPattern;
        private boolean multipleAccountAccess;
        private int failedAttemptsLast1Hour;
    }
    
    /**
     * IP behavioral pattern analysis
     */
    @Data
    @Builder
    @Jacksonized
    public static class IpBehavioralAnalysis {
        private List<String> typicalUserAgents;
        private List<String> currentUserAgents;
        private boolean userAgentAnomalous;
        private Map<String, Integer> accessPatterns;
        private List<String> timezoneMismatches;
        private boolean suspiciousTimingPattern;
        private double behavioralDeviationScore;
        private List<String> anomalousActivities;
        private boolean automatedBehaviorDetected;
        private boolean botLikeActivity;
        private int diversityScore;
        private boolean consistentFingerprint;
    }
    
    /**
     * Threat intelligence from external sources
     */
    @Data
    @Builder
    @Jacksonized
    public static class ThreatIntelligenceResult {
        private boolean isThreatActor;
        private List<String> threatCampaigns;
        private List<String> malwareAssociations;
        private String threatLevel;
        private Map<String, Object> intelligenceData;
        private List<String> iocs; // Indicators of Compromise
        private String attribution;
        private boolean isC2Server;
        private boolean isMalwareHost;
        private List<String> tactics;
        private List<String> techniques;
        private List<String> procedures;
    }
    
    /**
     * Historical analysis of IP behavior
     */
    @Data
    @Builder
    @Jacksonized
    public static class IpHistoricalAnalysis {
        private Instant firstSeen;
        private Instant lastSeen;
        private int totalTransactions;
        private double totalTransactionVolume;
        private int uniqueAccounts;
        private List<String> historicalCountries;
        private boolean locationConsistent;
        private List<String> previouslyFlaggedReasons;
        private boolean hasHistoricalFraud;
        private double historicalRiskScore;
        private Map<String, Integer> monthlyActivityTrends;
        private boolean dormancyPattern;
        private boolean reactivationAlert;
    }
    
    /**
     * Calculate overall IP risk score based on all analysis components
     */
    public double calculateOverallRiskScore() {
        double score = 0.0;
        
        // Reputation risk (0-30 points)
        if (reputationResult != null) {
            score += (100 - reputationResult.getReputationScore()) * 0.3;
            if (reputationResult.isBlacklisted()) score += 20;
            if (reputationResult.isMalicious()) score += 25;
        }
        
        // Proxy/VPN risk (0-20 points)
        if (proxyResult != null) {
            if (proxyResult.isVpn()) score += 15;
            if (proxyResult.isTor()) score += 20;
            if (proxyResult.isAnonymous()) score += 10;
        }
        
        // Geolocation risk (0-15 points)
        if (geolocationResult != null) {
            if (geolocationResult.isHighRiskCountry()) score += 10;
            if (geolocationResult.isSanctionedCountry()) score += 15;
        }
        
        // Velocity risk (0-20 points)
        if (velocityResult != null) {
            if (velocityResult.isVelocityExceeded()) score += 15;
            if (velocityResult.isRapidTransactionPattern()) score += 10;
            if (velocityResult.isMultipleAccountAccess()) score += 12;
        }
        
        // Threat intelligence risk (0-15 points)
        if (threatIntelligence != null && threatIntelligence.isThreatActor()) {
            score += 15;
        }
        
        return Math.min(score, 100.0);
    }
    
    /**
     * Determine if IP should be blocked immediately
     */
    public boolean shouldBlockImmediately() {
        return (reputationResult != null && reputationResult.isMalicious()) ||
               (threatIntelligence != null && threatIntelligence.isThreatActor()) ||
               (geolocationResult != null && geolocationResult.isSanctionedCountry()) ||
               riskScore >= 90;
    }
    
    /**
     * Get primary risk factors for this IP
     */
    public List<String> getPrimaryRiskFactors() {
        List<String> factors = new java.util.ArrayList<>();
        
        if (reputationResult != null && reputationResult.isBlacklisted()) {
            factors.add("IP_BLACKLISTED");
        }
        if (proxyResult != null && proxyResult.isVpn()) {
            factors.add("VPN_DETECTED");
        }
        if (geolocationResult != null && geolocationResult.isHighRiskCountry()) {
            factors.add("HIGH_RISK_COUNTRY");
        }
        if (velocityResult != null && velocityResult.isVelocityExceeded()) {
            factors.add("VELOCITY_EXCEEDED");
        }
        if (behavioralAnalysis != null && behavioralAnalysis.isBotLikeActivity()) {
            factors.add("BOT_BEHAVIOR");
        }
        
        return factors;
    }
    
    /**
     * Get recommended monitoring duration in days
     */
    public int getRecommendedMonitoringDays() {
        if (shouldBlockImmediately()) return 90;
        if (riskScore >= 70) return 30;
        if (riskScore >= 50) return 14;
        if (riskScore >= 30) return 7;
        return 3;
    }

    /**
     * Get IP reputation result
     */
    public IpReputationResult reputation() {
        return reputation != null ? reputation : reputationResult;
    }
}