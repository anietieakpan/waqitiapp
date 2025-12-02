package com.waqiti.payment.client.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User risk score DTO
 * Comprehensive user risk assessment with behavioral analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserRiskScore {
    
    @NonNull
    private UUID userId;
    
    @Builder.Default
    private BigDecimal overallRiskScore = BigDecimal.ZERO; // 0-100
    
    private RiskLevel riskLevel;
    
    private LocalDateTime lastUpdated;
    
    private LocalDateTime lastEvaluated;
    
    // Risk score breakdown
    private RiskScoreBreakdown breakdown;
    
    // Behavioral indicators
    private BehavioralIndicators behavioral;
    
    // Transaction history analysis
    private TransactionHistoryAnalysis transactionHistory;
    
    // Account security analysis
    private AccountSecurityAnalysis accountSecurity;
    
    // Geographic risk factors
    private GeographicRiskFactors geographic;
    
    // Device and network analysis
    private DeviceNetworkAnalysis deviceNetwork;
    
    // External data enrichment
    private ExternalDataAnalysis externalData;
    
    // Risk trend analysis
    private RiskTrendAnalysis trends;
    
    // Compliance and regulatory
    private ComplianceRiskFactors compliance;
    
    // Additional context
    private Map<String, Object> additionalFactors;
    
    private String riskAssessmentReason;
    
    public enum RiskLevel {
        VERY_LOW(0, 20),
        LOW(21, 40),
        MEDIUM(41, 60),
        HIGH(61, 80),
        VERY_HIGH(81, 95),
        CRITICAL(96, 100);
        
        private final int minScore;
        private final int maxScore;
        
        RiskLevel(int minScore, int maxScore) {
            this.minScore = minScore;
            this.maxScore = maxScore;
        }
        
        public static RiskLevel fromScore(BigDecimal score) {
            int scoreInt = score.intValue();
            for (RiskLevel level : values()) {
                if (scoreInt >= level.minScore && scoreInt <= level.maxScore) {
                    return level;
                }
            }
            return CRITICAL;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskScoreBreakdown {
        private BigDecimal behavioralRisk;      // 0-100
        private BigDecimal transactionRisk;     // 0-100
        private BigDecimal accountSecurityRisk; // 0-100
        private BigDecimal geographicRisk;      // 0-100
        private BigDecimal deviceRisk;          // 0-100
        private BigDecimal complianceRisk;      // 0-100
        private BigDecimal velocityRisk;        // 0-100
        private BigDecimal networkRisk;         // 0-100
        private Map<String, BigDecimal> customRiskFactors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehavioralIndicators {
        private boolean hasUnusualPatterns;
        private boolean hasErrationalBehavior;
        private boolean hasAccountTakeoverSigns;
        private boolean hasMultipleFailedAuthentications;
        private Integer loginFrequencyScore; // 1-10
        private Integer sessionDurationScore; // 1-10
        private Integer navigationPatternScore; // 1-10
        private String primaryUsagePattern;
        private List<String> behavioralAnomalies;
        private LocalDateTime lastUnusualActivity;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionHistoryAnalysis {
        private Integer totalTransactions;
        private BigDecimal totalVolume;
        private BigDecimal averageTransactionAmount;
        private BigDecimal largestTransaction;
        private LocalDateTime firstTransaction;
        private LocalDateTime lastTransaction;
        private Integer uniqueRecipients;
        private Integer failedTransactions;
        private BigDecimal failedTransactionRate;
        private Integer chargebacks;
        private Integer disputes;
        private String transactionPatternRating; // STABLE, VOLATILE, SUSPICIOUS
        private List<String> suspiciousPatterns;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountSecurityAnalysis {
        private boolean hasStrongAuthentication;
        private boolean hasTwoFactorEnabled;
        private boolean hasVerifiedEmail;
        private boolean hasVerifiedPhone;
        private boolean hasVerifiedIdentity;
        private Integer passwordStrengthScore; // 1-10
        private LocalDateTime lastPasswordChange;
        private Integer failedLoginAttempts;
        private boolean hasCompromisedCredentials;
        private List<String> securityEvents;
        private String accountVerificationLevel; // BASIC, ENHANCED, FULL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicRiskFactors {
        private String primaryCountry;
        private String currentLocation;
        private boolean isVpnUser;
        private boolean usesTorNetwork;
        private boolean isHighRiskLocation;
        private boolean hasUnusualLocationPatterns;
        private Integer uniqueCountries;
        private Integer uniqueCities;
        private Double locationConsistencyScore; // 0.0-1.0
        private List<String> riskLocations;
        private String locationTrustScore; // HIGH, MEDIUM, LOW
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceNetworkAnalysis {
        private Integer uniqueDevices;
        private Integer uniqueIpAddresses;
        private boolean hasCompromisedDevices;
        private boolean usesPublicNetworks;
        private boolean hasSharedDevices;
        private String deviceTrustScore; // HIGH, MEDIUM, LOW
        private String networkTrustScore; // HIGH, MEDIUM, LOW
        private List<String> suspiciousDevices;
        private List<String> suspiciousIps;
        private LocalDateTime lastNewDevice;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExternalDataAnalysis {
        private Integer creditScore;
        private boolean hasPublicRecords;
        private boolean hasBankruptcy;
        private boolean hasForeclosure;
        private boolean isOnWatchlist;
        private boolean isPoliticallyExposed;
        private String socialMediaRiskScore; // HIGH, MEDIUM, LOW
        private String publicRecordsRiskScore; // HIGH, MEDIUM, LOW
        private List<String> externalRiskFlags;
        private LocalDateTime lastExternalCheck;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskTrendAnalysis {
        private String trendDirection; // INCREASING, STABLE, DECREASING
        private BigDecimal riskChange30Days;
        private BigDecimal riskChange90Days;
        private BigDecimal volatilityScore; // 0-100
        private LocalDateTime trendStartDate;
        private List<RiskEvent> significantEvents;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RiskEvent {
            private LocalDateTime timestamp;
            private String eventType;
            private BigDecimal riskImpact;
            private String description;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceRiskFactors {
        private boolean hasAmlFlags;
        private boolean hasKycIssues;
        private boolean isOnSanctionsList;
        private boolean hasOfacMatch;
        private boolean hasPepExposure;
        private String complianceRating; // HIGH, MEDIUM, LOW
        private List<String> complianceFlags;
        private LocalDateTime lastComplianceCheck;
        private boolean requiresEnhancedDueDiligence;
    }
    
    // Business logic methods
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || 
               riskLevel == RiskLevel.VERY_HIGH || 
               riskLevel == RiskLevel.CRITICAL;
    }
    
    public boolean requiresManualReview() {
        return riskLevel == RiskLevel.VERY_HIGH || 
               riskLevel == RiskLevel.CRITICAL ||
               (compliance != null && compliance.requiresEnhancedDueDiligence);
    }
    
    public boolean hasSecurityConcerns() {
        return (accountSecurity != null && accountSecurity.hasCompromisedCredentials) ||
               (deviceNetwork != null && deviceNetwork.hasCompromisedDevices) ||
               (behavioral != null && behavioral.hasAccountTakeoverSigns);
    }
    
    public boolean hasComplianceConcerns() {
        return compliance != null && 
               (compliance.hasAmlFlags || 
                compliance.isOnSanctionsList || 
                compliance.hasOfacMatch ||
                compliance.hasPepExposure);
    }
    
    public boolean isRiskIncreasing() {
        return trends != null && "INCREASING".equals(trends.getTrendDirection());
    }
    
    public BigDecimal getConfidenceScore() {
        // Calculate confidence based on data completeness and freshness
        double confidence = 0.5; // Base confidence
        
        if (transactionHistory != null && transactionHistory.getTotalTransactions() > 10) {
            confidence += 0.2;
        }
        
        if (lastEvaluated != null && 
            lastEvaluated.isAfter(LocalDateTime.now().minusDays(7))) {
            confidence += 0.2;
        }
        
        if (accountSecurity != null && accountSecurity.hasVerifiedIdentity) {
            confidence += 0.1;
        }
        
        return new BigDecimal(Math.min(1.0, confidence));
    }
}