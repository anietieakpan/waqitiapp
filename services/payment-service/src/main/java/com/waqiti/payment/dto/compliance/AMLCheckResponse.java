/**
 * AML Check Response DTO
 * Response for Anti-Money Laundering check operations
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
public class AMLCheckResponse {
    
    /**
     * Whether the check was successful
     */
    private Boolean success;
    
    /**
     * AML check ID
     */
    private String amlCheckId;
    
    /**
     * User ID that was checked
     */
    private String userId;
    
    /**
     * Check type performed
     */
    private String checkType;
    
    /**
     * Check scope used
     */
    private String checkScope;
    
    /**
     * Overall check result (CLEAR, HIT, POTENTIAL_HIT, ERROR)
     */
    private String checkResult;
    
    /**
     * Risk level determined (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String riskLevel;
    
    /**
     * AML risk score (0.0 to 1.0)
     */
    private Double amlRiskScore;
    
    /**
     * Overall match confidence (0.0 to 1.0)
     */
    private Double matchConfidence;
    
    /**
     * Sanctions screening result (CLEAR, HIT, POTENTIAL_HIT)
     */
    private String sanctionsResult;
    
    /**
     * PEP screening result (CLEAR, HIT, POTENTIAL_HIT)
     */
    private String pepResult;
    
    /**
     * Watchlist screening result (CLEAR, HIT, POTENTIAL_HIT)
     */
    private String watchlistResult;
    
    /**
     * Adverse media screening result (CLEAR, HIT, POTENTIAL_HIT)
     */
    private String adverseMediaResult;
    
    /**
     * Screening hits found
     */
    private List<Map<String, Object>> screeningHits;
    
    /**
     * Sanctions hits details
     */
    private List<Map<String, Object>> sanctionsHits;
    
    /**
     * PEP hits details
     */
    private List<Map<String, Object>> pepHits;
    
    /**
     * Watchlist hits details
     */
    private List<Map<String, Object>> watchlistHits;
    
    /**
     * Adverse media hits details
     */
    private List<Map<String, Object>> adverseMediaHits;
    
    /**
     * False positive indicators
     */
    private List<String> falsePositiveIndicators;
    
    /**
     * Risk indicators identified
     */
    private List<String> riskIndicators;
    
    /**
     * Red flags raised
     */
    private List<String> redFlags;
    
    /**
     * Typologies identified
     */
    private List<String> identifiedTypologies;
    
    /**
     * Behavioral analysis results
     */
    private Map<String, Object> behavioralAnalysis;
    
    /**
     * Network analysis results
     */
    private Map<String, Object> networkAnalysis;
    
    /**
     * Transaction monitoring results
     */
    private Map<String, Object> transactionMonitoringResults;
    
    /**
     * Beneficial owners screening results
     */
    private List<Map<String, Object>> beneficialOwnersResults;
    
    /**
     * Screening providers used
     */
    private List<String> providersUsed;
    
    /**
     * Screening lists checked
     */
    private List<String> listsChecked;
    
    /**
     * Recommended actions
     */
    private List<String> recommendedActions;
    
    /**
     * Whether manual review is required
     */
    private Boolean manualReviewRequired;
    
    /**
     * Whether enhanced due diligence is recommended
     */
    private Boolean enhancedDueDiligenceRecommended;
    
    /**
     * Whether account restrictions are recommended
     */
    private Boolean restrictionsRecommended;
    
    /**
     * Recommended restrictions
     */
    private List<String> recommendedRestrictions;
    
    /**
     * Whether SAR filing is recommended
     */
    private Boolean sarFilingRecommended;
    
    /**
     * Whether regulatory notification is required
     */
    private Boolean regulatoryNotificationRequired;
    
    /**
     * Check summary
     */
    private String checkSummary;
    
    /**
     * Check notes
     */
    private String checkNotes;
    
    /**
     * Reviewer comments
     */
    private String reviewerComments;
    
    /**
     * Quality assurance score
     */
    private Double qualityScore;
    
    /**
     * Data sources used
     */
    private List<String> dataSources;
    
    /**
     * Data quality indicators
     */
    private Map<String, Object> dataQuality;
    
    /**
     * Check version/methodology
     */
    private String checkVersion;
    
    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;
    
    /**
     * When check was initiated
     */
    private Instant initiatedAt;
    
    /**
     * When check was completed
     */
    private Instant completedAt;
    
    /**
     * Next scheduled review date
     */
    private Instant nextReviewDate;
    
    /**
     * Check expiry date
     */
    private Instant expiryDate;
    
    /**
     * Compliance alerts created
     */
    private List<String> alertsCreated;
    
    /**
     * Related investigations
     */
    private List<String> relatedInvestigations;
    
    /**
     * Audit trail
     */
    private List<Map<String, Object>> auditTrail;
    
    /**
     * Error message if check failed
     */
    private String errorMessage;
    
    /**
     * Error code if applicable
     */
    private String errorCode;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Response version
     */
    private String version;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
}