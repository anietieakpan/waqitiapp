/**
 * User Compliance Status DTO
 * Represents the compliance status and history for a user
 */
package com.waqiti.payment.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserComplianceStatus {
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Overall compliance status (COMPLIANT, NON_COMPLIANT, UNDER_REVIEW, SUSPENDED)
     */
    private String overallStatus;
    
    /**
     * Compliance risk level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String riskLevel;
    
    /**
     * Compliance score (0.0 to 1.0)
     */
    private Double complianceScore;
    
    /**
     * KYC status
     */
    private String kycStatus;
    
    /**
     * KYC completion date
     */
    private Instant kycCompletedAt;
    
    /**
     * KYC expiry date
     */
    private Instant kycExpiresAt;
    
    /**
     * KYC risk level
     */
    private String kycRiskLevel;
    
    /**
     * AML status
     */
    private String amlStatus;
    
    /**
     * AML risk level
     */
    private String amlRiskLevel;
    
    /**
     * Last AML check date
     */
    private Instant lastAmlCheckAt;
    
    /**
     * Next AML review date
     */
    private Instant nextAmlReviewAt;
    
    /**
     * Sanctions screening status
     */
    private String sanctionsStatus;
    
    /**
     * Last sanctions screening date
     */
    private Instant lastSanctionsScreenAt;
    
    /**
     * Whether user is on watchlist
     */
    private Boolean onWatchlist;
    
    /**
     * Watchlist categories
     */
    private List<String> watchlistCategories;
    
    /**
     * Whether enhanced due diligence is required
     */
    private Boolean enhancedDueDiligenceRequired;
    
    /**
     * Enhanced due diligence status
     */
    private String enhancedDueDiligenceStatus;
    
    /**
     * Whether customer is PEP (Politically Exposed Person)
     */
    private Boolean isPep;
    
    /**
     * PEP risk level if applicable
     */
    private String pepRiskLevel;
    
    /**
     * Source of wealth verification status
     */
    private String sourceOfWealthStatus;
    
    /**
     * Source of funds verification status
     */
    private String sourceOfFundsStatus;
    
    /**
     * Active compliance alerts count
     */
    private Integer activeAlertsCount;
    
    /**
     * Total compliance alerts count
     */
    private Integer totalAlertsCount;
    
    /**
     * Recent compliance alerts
     */
    private List<ComplianceAlert> recentAlerts;
    
    /**
     * SARs filed count
     */
    private Integer sarsFiledCount;
    
    /**
     * Recent SARs
     */
    private List<String> recentSarIds;
    
    /**
     * Compliance violations count
     */
    private Integer violationsCount;
    
    /**
     * Recent violations
     */
    private List<String> recentViolations;
    
    /**
     * Account restrictions in place
     */
    private List<String> accountRestrictions;
    
    /**
     * Transaction limits imposed
     */
    private Map<String, Object> transactionLimits;
    
    /**
     * Monitoring level (STANDARD, ENHANCED, INTENSIVE)
     */
    private String monitoringLevel;
    
    /**
     * Monitoring start date
     */
    private Instant monitoringStartDate;
    
    /**
     * Monitoring end date
     */
    private Instant monitoringEndDate;
    
    /**
     * Compliance officer assigned
     */
    private String assignedComplianceOfficer;
    
    /**
     * Last compliance review date
     */
    private Instant lastComplianceReviewAt;
    
    /**
     * Next compliance review date
     */
    private Instant nextComplianceReviewAt;
    
    /**
     * Compliance review frequency (MONTHLY, QUARTERLY, ANNUALLY)
     */
    private String reviewFrequency;
    
    /**
     * Document verification status
     */
    private String documentVerificationStatus;
    
    /**
     * Required documents list
     */
    private List<String> requiredDocuments;
    
    /**
     * Submitted documents list
     */
    private List<String> submittedDocuments;
    
    /**
     * Missing documents list
     */
    private List<String> missingDocuments;
    
    /**
     * Regulatory notifications sent
     */
    private List<String> regulatoryNotifications;
    
    /**
     * Compliance notes
     */
    private String complianceNotes;
    
    /**
     * Risk mitigation measures
     */
    private List<String> riskMitigationMeasures;
    
    /**
     * Compliance exceptions granted
     */
    private List<String> complianceExceptions;
    
    /**
     * Last status update timestamp
     */
    private Instant lastUpdatedAt;
    
    /**
     * Status update reason
     */
    private String updateReason;
    
    /**
     * Updated by (user/system)
     */
    private String updatedBy;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Status version for tracking changes
     */
    private String version;
}