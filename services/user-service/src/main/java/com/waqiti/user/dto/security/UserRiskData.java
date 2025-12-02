package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * User Risk Data DTO
 * 
 * Contains comprehensive risk assessment data for a user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRiskData {
    
    // User identification
    private String userId;
    private String userEmail;
    private String accountType;
    
    // Overall risk assessment
    private Double overallRiskScore; // 0.0 to 1.0
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private String riskCategory; // FINANCIAL, IDENTITY, BEHAVIORAL, COMPLIANCE
    
    // Risk factors
    private List<RiskFactor> riskFactors;
    private Map<String, Double> riskComponentScores;
    
    // Identity risk
    private IdentityRisk identityRisk;
    
    // Behavioral risk
    private BehavioralRisk behavioralRisk;
    
    // Financial risk
    private FinancialRisk financialRisk;
    
    // Device and location risk
    private DeviceRisk deviceRisk;
    private LocationRisk locationRisk;
    
    // Compliance and regulatory risk
    private ComplianceRisk complianceRisk;
    
    // Historical risk data
    private HistoricalRisk historicalRisk;
    
    // Risk assessment metadata
    private LocalDateTime assessedAt;
    private String assessmentVersion;
    private String assessmentMethod;
    private Long processingTimeMs;
    
    // Risk triggers and alerts
    private List<RiskAlert> activeAlerts;
    private List<String> triggeredRules;
    
    // Risk mitigation actions
    private List<String> recommendedActions;
    private List<String> requiredVerifications;
    private Boolean requiresManualReview;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorType;
        private String description;
        private Double impact; // 0.0 to 1.0
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private LocalDateTime detectedAt;
        private String source;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdentityRisk {
        private Double identityScore;
        private Boolean identityVerified;
        private String kycStatus;
        private Boolean documentsVerified;
        private Boolean biometricVerified;
        private Boolean addressVerified;
        private List<String> identityFlags;
        private Boolean suspiciousIdentity;
        private Boolean multipleIdentities;
        private Boolean syntheticIdentity;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralRisk {
        private Double behaviorScore;
        private String behaviorPattern;
        private Boolean anomalousBehavior;
        private List<String> behaviorAnomalies;
        private Double velocityRisk;
        private Boolean unusualActivityPattern;
        private Boolean botLikeBehavior;
        private Map<String, Double> behaviorMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialRisk {
        private Double financialScore;
        private String transactionPattern;
        private Boolean highValueTransactions;
        private Boolean frequentTransactions;
        private Boolean crossBorderTransactions;
        private Boolean cashIntensiveActivity;
        private Double averageTransactionAmount;
        private Integer transactionCount;
        private String sourceOfFunds;
        private Boolean suspiciousFinancialActivity;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceRisk {
        private Double deviceScore;
        private Boolean newDevice;
        private Boolean trustedDevice;
        private Boolean sharedDevice;
        private Boolean emulatorDetected;
        private Boolean rootedDevice;
        private Boolean vpnUsage;
        private Boolean proxyUsage;
        private Integer deviceCount;
        private String primaryDevice;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationRisk {
        private Double locationScore;
        private Boolean highRiskCountry;
        private Boolean sanctionedCountry;
        private Boolean frequentTravelPattern;
        private Boolean impossibleTravel;
        private Boolean locationSpoofing;
        private String primaryLocation;
        private Integer locationCount;
        private List<String> recentLocations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceRisk {
        private Double complianceScore;
        private Boolean pepStatus;
        private Boolean sanctionsHit;
        private Boolean adverseMediaHit;
        private Boolean regulatoryAction;
        private String jurisdictionRisk;
        private List<String> complianceFlags;
        private Boolean requiresEnhancedDueDiligence;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalRisk {
        private Double historicalScore;
        private Integer fraudIncidents;
        private Integer chargebacks;
        private Integer suspiciousActivities;
        private Integer accountSuspensions;
        private LocalDateTime lastIncident;
        private String riskTrend; // IMPROVING, STABLE, DETERIORATING
        private Map<String, Integer> incidentCounts;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAlert {
        private String alertId;
        private String alertType;
        private String severity;
        private String description;
        private LocalDateTime triggeredAt;
        private Boolean acknowledged;
        private String acknowledgedBy;
        private LocalDateTime acknowledgedAt;
        private Map<String, Object> alertData;
    }
    
    /**
     * Get baseline risk score (uses overall risk score)
     */
    public Double getBaselineRiskScore() {
        return overallRiskScore != null ? overallRiskScore : 0.5;
    }
    
    /**
     * Check if user is high risk
     */
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }
    
    /**
     * Check if user requires manual review
     */
    public boolean requiresReview() {
        return Boolean.TRUE.equals(requiresManualReview) || isHighRisk();
    }
    
    /**
     * Get combined device and location risk score
     */
    public Double getContextualRiskScore() {
        double deviceScore = deviceRisk != null && deviceRisk.getDeviceScore() != null 
            ? deviceRisk.getDeviceScore() : 0.0;
        double locationScore = locationRisk != null && locationRisk.getLocationScore() != null 
            ? locationRisk.getLocationScore() : 0.0;
        return (deviceScore + locationScore) / 2.0;
    }
    
    /**
     * Get financial risk score
     */
    public Double getFinancialRiskScore() {
        return financialRisk != null && financialRisk.getFinancialScore() != null 
            ? financialRisk.getFinancialScore() : 0.0;
    }
    
    /**
     * Get identity verification status
     */
    public boolean isIdentityVerified() {
        return identityRisk != null && Boolean.TRUE.equals(identityRisk.getIdentityVerified());
    }
    
    /**
     * Get compliance status
     */
    public boolean isCompliant() {
        if (complianceRisk == null) return true;
        return !Boolean.TRUE.equals(complianceRisk.getPepStatus()) &&
               !Boolean.TRUE.equals(complianceRisk.getSanctionsHit()) &&
               !Boolean.TRUE.equals(complianceRisk.getAdverseMediaHit());
    }
    
    /**
     * Static factory method for creating low risk data
     */
    public static UserRiskData lowRisk(String userId) {
        return UserRiskData.builder()
            .userId(userId)
            .overallRiskScore(0.1)
            .riskLevel("LOW")
            .riskCategory("STANDARD")
            .assessedAt(LocalDateTime.now())
            .requiresManualReview(false)
            .build();
    }
    
    /**
     * Static factory method for creating high risk data
     */
    public static UserRiskData highRisk(String userId, String reason) {
        return UserRiskData.builder()
            .userId(userId)
            .overallRiskScore(0.9)
            .riskLevel("HIGH")
            .riskCategory("SUSPICIOUS")
            .assessedAt(LocalDateTime.now())
            .requiresManualReview(true)
            .recommendedActions(java.util.List.of("MANUAL_REVIEW", "ENHANCED_VERIFICATION"))
            .build();
    }

    /**
     * Set baseline risk score
     */
    public void setBaselineRiskScore(Double score) {
        this.overallRiskScore = score;
    }
    
    /**
     * Get typical daily logins
     */
    public Integer getTypicalDailyLogins() {
        return behavioralRisk != null && behavioralRisk.getBehaviorMetrics() != null
            ? behavioralRisk.getBehaviorMetrics().getOrDefault("dailyLogins", 1.0).intValue()
            : 1;
    }
    
    /**
     * Set contextual risk score
     */
    public void setContextualRiskScore(Double score) {
        if (this.riskComponentScores == null) {
            this.riskComponentScores = new java.util.HashMap<>();
        }
        this.riskComponentScores.put("contextual", score);
    }
    
    /**
     * Set financial risk score
     */
    public void setFinancialRiskScore(Double score) {
        if (this.riskComponentScores == null) {
            this.riskComponentScores = new java.util.HashMap<>();
        }
        this.riskComponentScores.put("financial", score);
    }
    
    /**
     * Set identity risk score
     */
    public void setIdentityRiskScore(Double score) {
        if (this.riskComponentScores == null) {
            this.riskComponentScores = new java.util.HashMap<>();
        }
        this.riskComponentScores.put("identity", score);
    }
    
    /**
     * Add a security event/trigger
     */
    public void addSecurityEvent(Object trigger) {
        if (this.activeAlerts == null) {
            this.activeAlerts = new java.util.ArrayList<>();
        }
        
        // Convert trigger to RiskAlert
        RiskAlert alert = RiskAlert.builder()
            .alertId(java.util.UUID.randomUUID().toString())
            .alertType("SECURITY_TRIGGER")
            .severity("HIGH")
            .description(trigger != null ? trigger.toString() : "Security event triggered")
            .triggeredAt(LocalDateTime.now())
            .acknowledged(false)
            .alertData(new java.util.HashMap<>())
            .build();
            
        this.activeAlerts.add(alert);
    }
    
    /**
     * Get security events as risk alerts
     */
    public List<RiskAlert> getSecurityEvents() {
        return this.activeAlerts != null ? this.activeAlerts : new java.util.ArrayList<>();
    }
    
    /**
     * Update last assessment time
     */
    public void setLastUpdated(LocalDateTime timestamp) {
        this.assessedAt = timestamp;
    }
    
    /**
     * Get last updated time
     */
    public LocalDateTime getLastUpdated() {
        return this.assessedAt;
    }
    
    /**
     * Recalculate overall risk score based on components
     */
    public void recalculateOverallRisk() {
        if (this.riskComponentScores != null && !this.riskComponentScores.isEmpty()) {
            double sum = this.riskComponentScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
            this.overallRiskScore = sum / this.riskComponentScores.size();
            
            // Update risk level based on score
            if (this.overallRiskScore >= 0.8) {
                this.riskLevel = "CRITICAL";
            } else if (this.overallRiskScore >= 0.6) {
                this.riskLevel = "HIGH";
            } else if (this.overallRiskScore >= 0.4) {
                this.riskLevel = "MEDIUM";
            } else {
                this.riskLevel = "LOW";
            }
        }
    }
    
    /**
     * Add risk factor
     */
    public void addRiskFactor(String factorType, String description, Double impact) {
        if (this.riskFactors == null) {
            this.riskFactors = new java.util.ArrayList<>();
        }
        
        RiskFactor factor = RiskFactor.builder()
            .factorType(factorType)
            .description(description)
            .impact(impact)
            .severity(impact >= 0.8 ? "CRITICAL" : impact >= 0.6 ? "HIGH" : impact >= 0.4 ? "MEDIUM" : "LOW")
            .detectedAt(LocalDateTime.now())
            .source("SYSTEM")
            .metadata(new java.util.HashMap<>())
            .build();
            
        this.riskFactors.add(factor);
    }
}