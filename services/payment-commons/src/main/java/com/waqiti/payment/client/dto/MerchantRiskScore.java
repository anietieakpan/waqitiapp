package com.waqiti.payment.client.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Merchant risk score DTO
 * Comprehensive merchant risk assessment for fraud prevention
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MerchantRiskScore {
    
    @NonNull
    private String merchantId;
    
    private String merchantName;
    
    @Builder.Default
    private BigDecimal overallRiskScore = BigDecimal.ZERO; // 0-100
    
    private MerchantRiskLevel riskLevel;
    
    private LocalDateTime lastUpdated;
    
    private LocalDateTime lastEvaluated;
    
    // Risk score breakdown
    private MerchantRiskBreakdown breakdown;
    
    // Business profile analysis
    private BusinessProfileAnalysis businessProfile;
    
    // Transaction analysis
    private MerchantTransactionAnalysis transactionAnalysis;
    
    // Compliance and regulatory
    private MerchantComplianceAnalysis compliance;
    
    // Financial analysis
    private FinancialAnalysis financialAnalysis;
    
    // Reputation analysis
    private ReputationAnalysis reputationAnalysis;
    
    // Industry-specific risks
    private IndustryRiskAnalysis industryRisks;
    
    // Operational analysis
    private OperationalAnalysis operationalAnalysis;
    
    // Additional context
    private Map<String, Object> additionalFactors;
    
    private String riskAssessmentReason;
    
    public enum MerchantRiskLevel {
        VERY_LOW(0, 20),
        LOW(21, 40),
        MEDIUM(41, 60),
        HIGH(61, 80),
        VERY_HIGH(81, 95),
        CRITICAL(96, 100);
        
        private final int minScore;
        private final int maxScore;
        
        MerchantRiskLevel(int minScore, int maxScore) {
            this.minScore = minScore;
            this.maxScore = maxScore;
        }
        
        public static MerchantRiskLevel fromScore(BigDecimal score) {
            int scoreInt = score.intValue();
            for (MerchantRiskLevel level : values()) {
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
    public static class MerchantRiskBreakdown {
        private BigDecimal businessRisk;         // 0-100
        private BigDecimal transactionRisk;      // 0-100
        private BigDecimal complianceRisk;       // 0-100
        private BigDecimal financialRisk;        // 0-100
        private BigDecimal reputationRisk;       // 0-100
        private BigDecimal industryRisk;         // 0-100
        private BigDecimal operationalRisk;      // 0-100
        private BigDecimal geographicRisk;       // 0-100
        private Map<String, BigDecimal> customRiskFactors;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessProfileAnalysis {
        private String businessType;
        private String industryCategory;
        private String businessModel;
        private LocalDateTime businessStartDate;
        private Integer businessAge;
        private String businessSize; // SMALL, MEDIUM, LARGE, ENTERPRISE
        private String legalStructure;
        private String registeredCountry;
        private String operatingCountries;
        private boolean isRegulated;
        private boolean hasBusinessLicense;
        private boolean isPubliclyListed;
        private String businessRegistrationNumber;
        private Map<String, Object> businessAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantTransactionAnalysis {
        private Integer totalTransactions;
        private BigDecimal totalVolume;
        private BigDecimal averageTransactionAmount;
        private BigDecimal medianTransactionAmount;
        private BigDecimal largestTransaction;
        private LocalDateTime firstTransaction;
        private LocalDateTime lastTransaction;
        private Integer uniqueCustomers;
        private Integer failedTransactions;
        private BigDecimal failedTransactionRate;
        private Integer chargebacks;
        private Integer disputes;
        private BigDecimal chargebackRate;
        private BigDecimal disputeRate;
        private String transactionPatternRating; // STABLE, VOLATILE, SUSPICIOUS
        @Builder.Default
        private List<String> suspiciousPatterns = List.of();
        private VelocityMetrics velocityMetrics;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class VelocityMetrics {
            private BigDecimal dailyAverageVolume;
            private BigDecimal weeklyAverageVolume;
            private BigDecimal monthlyAverageVolume;
            private Integer dailyAverageCount;
            private Integer weeklyAverageCount;
            private Integer monthlyAverageCount;
            private boolean hasUnusualVelocity;
            private String velocityTrend; // INCREASING, STABLE, DECREASING
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantComplianceAnalysis {
        private boolean hasKycCompleted;
        private boolean hasAmlCompliance;
        private boolean isOnSanctionsList;
        private boolean hasOfacMatch;
        private boolean hasPepExposure;
        private boolean isLicensedEntity;
        private String complianceRating; // HIGH, MEDIUM, LOW
        @Builder.Default
        private List<String> complianceFlags = List.of();
        private LocalDateTime lastComplianceCheck;
        private boolean requiresEnhancedDueDiligence;
        private Map<String, String> regulatoryLicenses;
        private List<ComplianceViolation> violations;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ComplianceViolation {
            private String violationType;
            private String description;
            private LocalDateTime date;
            private String severity;
            private String status;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialAnalysis {
        private String creditRating;
        private Integer creditScore;
        private BigDecimal annualRevenue;
        private String financialStability; // STABLE, MODERATE, UNSTABLE
        private boolean hasBankruptcy;
        private boolean hasLegalIssues;
        private boolean hasDelinquentPayments;
        private String paymentHistory; // EXCELLENT, GOOD, FAIR, POOR
        private Integer daysPayableOutstanding;
        private BigDecimal debtToAssetRatio;
        private String cashFlowRating;
        private LocalDateTime lastFinancialReview;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReputationAnalysis {
        private String onlineReputation; // EXCELLENT, GOOD, FAIR, POOR
        private Double averageReviewScore; // 0.0-5.0
        private Integer totalReviews;
        private Integer negativeReviews;
        private String socialMediaPresence;
        private boolean hasMediaCoverage;
        private boolean hasNegativeMediaCoverage;
        private boolean hasCustomerComplaints;
        private Integer complaintCount;
        private String brandTrustScore;
        private LocalDateTime lastReputationCheck;
        @Builder.Default
        private List<String> reputationFlags = List.of();
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndustryRiskAnalysis {
        private String industryRiskLevel; // HIGH, MEDIUM, LOW
        private boolean isHighRiskIndustry;
        private boolean isRegulatedIndustry;
        private String industryVolatility;
        private String marketPosition;
        private String competitiveAnalysis;
        private boolean hasSeasonality;
        private String industryTrends;
        @Builder.Default
        private List<String> industrySpecificRisks = List.of();
        private Map<String, Object> industryMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationalAnalysis {
        private String operationalMaturity; // BASIC, INTERMEDIATE, ADVANCED
        private boolean hasRobustControls;
        private boolean hasComplianceProgram;
        private boolean hasFraudPrevention;
        private String technologyInfrastructure;
        private String securityPosture;
        private boolean hasCybersecurityIncidents;
        private String dataProtectionCompliance;
        private String operationalRiskRating;
        private LocalDateTime lastOperationalReview;
        @Builder.Default
        private List<String> operationalConcerns = List.of();
    }
    
    // Business logic methods
    public boolean isHighRisk() {
        return riskLevel == MerchantRiskLevel.HIGH || 
               riskLevel == MerchantRiskLevel.VERY_HIGH || 
               riskLevel == MerchantRiskLevel.CRITICAL;
    }
    
    public boolean requiresManualReview() {
        return riskLevel == MerchantRiskLevel.VERY_HIGH || 
               riskLevel == MerchantRiskLevel.CRITICAL ||
               (compliance != null && compliance.requiresEnhancedDueDiligence);
    }
    
    public boolean hasComplianceConcerns() {
        return compliance != null && 
               (compliance.isOnSanctionsList() || 
                compliance.hasOfacMatch() ||
                !compliance.hasKycCompleted() ||
                !compliance.hasAmlCompliance());
    }
    
    public boolean hasFinancialConcerns() {
        return financialAnalysis != null &&
               (financialAnalysis.hasBankruptcy() ||
                financialAnalysis.hasLegalIssues() ||
                "POOR".equals(financialAnalysis.getPaymentHistory()) ||
                "UNSTABLE".equals(financialAnalysis.getFinancialStability()));
    }
    
    public boolean hasReputationConcerns() {
        return reputationAnalysis != null &&
               (reputationAnalysis.hasNegativeMediaCoverage() ||
                "POOR".equals(reputationAnalysis.getOnlineReputation()) ||
                (reputationAnalysis.getAverageReviewScore() != null && 
                 reputationAnalysis.getAverageReviewScore() < 2.0));
    }
    
    public boolean hasHighChargebackRate() {
        return transactionAnalysis != null &&
               transactionAnalysis.getChargebackRate() != null &&
               transactionAnalysis.getChargebackRate().compareTo(new BigDecimal("1.0")) > 0; // > 1%
    }
    
    public boolean isNewMerchant() {
        return businessProfile != null &&
               businessProfile.getBusinessAge() != null &&
               businessProfile.getBusinessAge() < 365; // Less than 1 year
    }
    
    public BigDecimal getConfidenceScore() {
        // Calculate confidence based on data completeness and freshness
        double confidence = 0.5; // Base confidence
        
        if (transactionAnalysis != null && transactionAnalysis.getTotalTransactions() > 100) {
            confidence += 0.2;
        }
        
        if (lastEvaluated != null && 
            lastEvaluated.isAfter(LocalDateTime.now().minusDays(30))) {
            confidence += 0.2;
        }
        
        if (compliance != null && compliance.hasKycCompleted()) {
            confidence += 0.1;
        }
        
        return new BigDecimal(Math.min(1.0, confidence));
    }
}