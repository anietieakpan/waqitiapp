package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive Transaction Risk Assessment model for ML-based risk scoring.
 * 
 * Implements advanced risk assessment featuring:
 * - Multi-factor risk scoring engine
 * - Machine learning model predictions
 * - Explainable AI features
 * - Real-time risk calculation
 * - Regulatory compliance scoring
 * - Pattern recognition results
 * 
 * Risk Assessment Standards:
 * - Basel III operational risk framework
 * - FATF risk-based approach
 * - ISO 31000 risk management
 * - COSO Enterprise Risk Management
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRiskAssessment {
    
    // Core Identification
    @NotNull(message = "Assessment ID is required")
    private UUID assessmentId;
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotNull(message = "Assessment timestamp is required")
    private LocalDateTime assessedAt;
    
    // Overall Risk Metrics
    @NotNull(message = "Overall risk score is required")
    @DecimalMin(value = "0.0", message = "Risk score cannot be negative")
    @DecimalMax(value = "100.0", message = "Risk score cannot exceed 100")
    private BigDecimal overallRiskScore;
    
    @NotBlank(message = "Risk level is required")
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    @NotBlank(message = "Risk category is required")
    private String riskCategory; // COMPLIANCE, FRAUD, AML, OPERATIONAL
    
    private String riskDecision; // APPROVE, REVIEW, REJECT, ESCALATE
    private String confidenceLevel; // LOW, MEDIUM, HIGH
    
    // ML Model Information
    private String modelName;
    private String modelVersion;
    private BigDecimal modelConfidence; // 0-1 scale
    private String modelType; // ENSEMBLE, NEURAL_NETWORK, GRADIENT_BOOSTING, etc.
    private LocalDateTime modelTrainingDate;
    
    // Component Risk Scores
    @Builder.Default
    private Map<String, BigDecimal> componentScores = new HashMap<>();
    
    // Individual Risk Factors
    @NotNull(message = "AML risk score is required")
    private BigDecimal amlRiskScore;
    
    @NotNull(message = "Fraud risk score is required")
    private BigDecimal fraudRiskScore;
    
    @NotNull(message = "Compliance risk score is required")
    private BigDecimal complianceRiskScore;
    
    @NotNull(message = "Operational risk score is required")
    private BigDecimal operationalRiskScore;
    
    private BigDecimal creditRiskScore;
    private BigDecimal marketRiskScore;
    private BigDecimal liquidityRiskScore;
    private BigDecimal cybersecurityRiskScore;
    
    // Transaction-Specific Risk Factors
    private BigDecimal velocityRisk;
    private BigDecimal geographicRisk;
    private BigDecimal behaviorRisk;
    private BigDecimal networkRisk;
    private BigDecimal amountRisk;
    private BigDecimal timeRisk;
    private BigDecimal channelRisk;
    
    // Customer and Counterparty Risk
    private BigDecimal customerRiskScore;
    private String customerRiskLevel;
    private BigDecimal counterpartyRiskScore;
    private String counterpartyRiskLevel;
    
    // Risk Indicators and Flags
    @Builder.Default
    private List<String> riskIndicators = new ArrayList<>();
    
    @Builder.Default
    private List<String> riskFlags = new ArrayList<>();
    
    @Builder.Default
    private List<RiskFactor> detailedRiskFactors = new ArrayList<>();
    
    // Explainable AI Features
    @Builder.Default
    private Map<String, BigDecimal> featureImportance = new HashMap<>(); // Feature -> Importance Score
    
    @Builder.Default
    private Map<String, Object> featureValues = new HashMap<>(); // Feature -> Value
    
    @Builder.Default
    private List<String> explanations = new ArrayList<>(); // Human-readable explanations
    
    private String primaryRiskDriver; // Most significant risk factor
    private String riskExplanation; // Overall explanation
    
    // Threshold and Limit Analysis
    @Builder.Default
    private Map<String, ThresholdAnalysis> thresholdAnalysis = new HashMap<>();
    
    private boolean exceedsRiskThreshold;
    private String exceedanceReason;
    
    // Historical Context
    private BigDecimal historicalAverageRisk;
    private BigDecimal riskTrend; // Positive = increasing risk
    private int riskTrendPeriodDays;
    
    // Regulatory and Compliance
    @Builder.Default
    private Map<String, Boolean> regulatoryChecks = new HashMap<>();
    
    private boolean requiresEnhancedDueDiligence;
    private boolean requiresManualReview;
    private boolean requiresExecutiveApproval;
    private String complianceReason;
    
    // Actions and Recommendations
    @Builder.Default
    private List<String> recommendedActions = new ArrayList<>();
    
    @Builder.Default
    private List<String> mitigationSuggestions = new ArrayList<>();
    
    private String automatedAction; // AUTO_APPROVE, AUTO_REJECT, MANUAL_REVIEW
    private String escalationLevel; // L1, L2, L3, EXECUTIVE
    
    // Performance and Processing
    private Long processingTimeMs;
    private String processingNode; // Which processing node handled this
    private String correlationId; // For distributed tracing
    
    // Quality and Validation
    private boolean validationPassed;
    private List<String> validationErrors;
    private BigDecimal dataQualityScore; // Quality of input data
    
    // Audit and Governance
    private String auditTrailId;
    private Map<String, Object> auditMetadata;
    private LocalDateTime expiresAt;
    private boolean archived;
    
    /**
     * Detailed risk factor with explanation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String factorName;
        private String category;
        private BigDecimal weight;
        private BigDecimal value;
        private BigDecimal contribution; // Contribution to overall score
        private String description;
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private Map<String, Object> metadata;
    }
    
    /**
     * Threshold analysis for specific metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThresholdAnalysis {
        private String metricName;
        private BigDecimal currentValue;
        private BigDecimal threshold;
        private boolean exceeded;
        private BigDecimal exceedanceAmount;
        private String exceedancePercentage;
        private String thresholdType; // HARD, SOFT, DYNAMIC
    }
    
    /**
     * Factory method for creating new assessment
     */
    public static TransactionRiskAssessment createNew(String transactionId, String userId) {
        return TransactionRiskAssessment.builder()
                .assessmentId(UUID.randomUUID())
                .transactionId(transactionId)
                .userId(userId)
                .assessedAt(LocalDateTime.now())
                .overallRiskScore(BigDecimal.ZERO)
                .riskLevel("UNKNOWN")
                .riskCategory("PENDING")
                .amlRiskScore(BigDecimal.ZERO)
                .fraudRiskScore(BigDecimal.ZERO)
                .complianceRiskScore(BigDecimal.ZERO)
                .operationalRiskScore(BigDecimal.ZERO)
                .componentScores(new HashMap<>())
                .riskIndicators(new ArrayList<>())
                .riskFlags(new ArrayList<>())
                .detailedRiskFactors(new ArrayList<>())
                .featureImportance(new HashMap<>())
                .featureValues(new HashMap<>())
                .explanations(new ArrayList<>())
                .thresholdAnalysis(new HashMap<>())
                .regulatoryChecks(new HashMap<>())
                .recommendedActions(new ArrayList<>())
                .mitigationSuggestions(new ArrayList<>())
                .build();
    }
    
    /**
     * Calculate overall risk score from components
     */
    public void calculateOverallRiskScore() {
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        // AML Risk (30% weight)
        if (amlRiskScore != null) {
            totalScore = totalScore.add(amlRiskScore.multiply(BigDecimal.valueOf(0.30)));
            totalWeight = totalWeight.add(BigDecimal.valueOf(0.30));
        }
        
        // Fraud Risk (25% weight)
        if (fraudRiskScore != null) {
            totalScore = totalScore.add(fraudRiskScore.multiply(BigDecimal.valueOf(0.25)));
            totalWeight = totalWeight.add(BigDecimal.valueOf(0.25));
        }
        
        // Compliance Risk (20% weight)
        if (complianceRiskScore != null) {
            totalScore = totalScore.add(complianceRiskScore.multiply(BigDecimal.valueOf(0.20)));
            totalWeight = totalWeight.add(BigDecimal.valueOf(0.20));
        }
        
        // Operational Risk (15% weight)
        if (operationalRiskScore != null) {
            totalScore = totalScore.add(operationalRiskScore.multiply(BigDecimal.valueOf(0.15)));
            totalWeight = totalWeight.add(BigDecimal.valueOf(0.15));
        }
        
        // Other risks (10% weight combined)
        BigDecimal otherRisks = BigDecimal.ZERO;
        int otherRiskCount = 0;
        
        if (creditRiskScore != null) {
            otherRisks = otherRisks.add(creditRiskScore);
            otherRiskCount++;
        }
        if (marketRiskScore != null) {
            otherRisks = otherRisks.add(marketRiskScore);
            otherRiskCount++;
        }
        if (cybersecurityRiskScore != null) {
            otherRisks = otherRisks.add(cybersecurityRiskScore);
            otherRiskCount++;
        }
        
        if (otherRiskCount > 0) {
            BigDecimal avgOtherRisk = otherRisks.divide(BigDecimal.valueOf(otherRiskCount), 2, RoundingMode.HALF_UP);
            totalScore = totalScore.add(avgOtherRisk.multiply(BigDecimal.valueOf(0.10)));
            totalWeight = totalWeight.add(BigDecimal.valueOf(0.10));
        }
        
        // Calculate weighted average
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            this.overallRiskScore = totalScore.divide(totalWeight, 2, RoundingMode.HALF_UP);
        } else {
            this.overallRiskScore = BigDecimal.ZERO;
        }
        
        // Determine risk level based on score
        determineRiskLevel();
    }
    
    /**
     * Determine risk level based on overall score
     */
    private void determineRiskLevel() {
        if (overallRiskScore.compareTo(BigDecimal.valueOf(80)) >= 0) {
            this.riskLevel = "CRITICAL";
            this.requiresExecutiveApproval = true;
            this.requiresManualReview = true;
            this.escalationLevel = "EXECUTIVE";
        } else if (overallRiskScore.compareTo(BigDecimal.valueOf(60)) >= 0) {
            this.riskLevel = "HIGH";
            this.requiresManualReview = true;
            this.escalationLevel = "L3";
        } else if (overallRiskScore.compareTo(BigDecimal.valueOf(30)) >= 0) {
            this.riskLevel = "MEDIUM";
            this.escalationLevel = "L2";
        } else {
            this.riskLevel = "LOW";
            this.escalationLevel = "L1";
        }
    }
    
    /**
     * Add a risk factor
     */
    public void addRiskFactor(String name, String category, BigDecimal weight, 
                             BigDecimal value, String description) {
        if (detailedRiskFactors == null) {
            detailedRiskFactors = new ArrayList<>();
        }
        
        BigDecimal contribution = weight.multiply(value);
        
        RiskFactor factor = RiskFactor.builder()
                .factorName(name)
                .category(category)
                .weight(weight)
                .value(value)
                .contribution(contribution)
                .description(description)
                .severity(determineSeverity(value))
                .metadata(new HashMap<>())
                .build();
        
        detailedRiskFactors.add(factor);
        
        // Add to component scores
        componentScores.put(name, contribution);
    }
    
    /**
     * Add feature importance for explainability
     */
    public void addFeatureImportance(String feature, BigDecimal importance, Object value) {
        if (featureImportance == null) {
            featureImportance = new HashMap<>();
        }
        if (featureValues == null) {
            featureValues = new HashMap<>();
        }
        
        featureImportance.put(feature, importance);
        featureValues.put(feature, value);
    }
    
    /**
     * Add human-readable explanation
     */
    public void addExplanation(String explanation) {
        if (explanations == null) {
            explanations = new ArrayList<>();
        }
        explanations.add(explanation);
    }
    
    /**
     * Add risk indicator
     */
    public void addRiskIndicator(String indicator) {
        if (riskIndicators == null) {
            riskIndicators = new ArrayList<>();
        }
        if (!riskIndicators.contains(indicator)) {
            riskIndicators.add(indicator);
        }
    }
    
    /**
     * Add risk flag
     */
    public void addRiskFlag(String flag) {
        if (riskFlags == null) {
            riskFlags = new ArrayList<>();
        }
        if (!riskFlags.contains(flag)) {
            riskFlags.add(flag);
        }
    }
    
    /**
     * Add recommended action
     */
    public void addRecommendedAction(String action) {
        if (recommendedActions == null) {
            recommendedActions = new ArrayList<>();
        }
        recommendedActions.add(action);
    }
    
    /**
     * Add mitigation suggestion
     */
    public void addMitigationSuggestion(String suggestion) {
        if (mitigationSuggestions == null) {
            mitigationSuggestions = new ArrayList<>();
        }
        mitigationSuggestions.add(suggestion);
    }
    
    /**
     * Check if assessment indicates high risk
     */
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }
    
    /**
     * Check if manual review is required
     */
    public boolean requiresReview() {
        return requiresManualReview || isHighRisk() || 
               (riskFlags != null && !riskFlags.isEmpty());
    }
    
    /**
     * Get the most significant risk factor
     */
    public RiskFactor getMostSignificantRiskFactor() {
        if (detailedRiskFactors == null || detailedRiskFactors.isEmpty()) {
            return null;
        }
        
        return detailedRiskFactors.stream()
                .max((f1, f2) -> f1.getContribution().compareTo(f2.getContribution()))
                .orElse(null);
    }
    
    /**
     * Generate risk summary
     */
    public String generateRiskSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Transaction Risk Assessment Summary\n");
        summary.append("=====================================\n");
        summary.append("Overall Risk Score: ").append(overallRiskScore).append("/100\n");
        summary.append("Risk Level: ").append(riskLevel).append("\n");
        summary.append("Risk Category: ").append(riskCategory).append("\n");
        
        if (primaryRiskDriver != null) {
            summary.append("Primary Risk Driver: ").append(primaryRiskDriver).append("\n");
        }
        
        if (riskFlags != null && !riskFlags.isEmpty()) {
            summary.append("Risk Flags: ").append(String.join(", ", riskFlags)).append("\n");
        }
        
        if (requiresManualReview) {
            summary.append("Manual Review Required: YES\n");
        }
        
        if (recommendedActions != null && !recommendedActions.isEmpty()) {
            summary.append("Recommended Actions:\n");
            recommendedActions.forEach(action -> summary.append("- ").append(action).append("\n"));
        }
        
        return summary.toString();
    }
    
    /**
     * Validate assessment completeness
     */
    public void validate() {
        List<String> errors = new ArrayList<>();
        
        if (assessmentId == null) {
            errors.add("Assessment ID is required");
        }
        
        if (transactionId == null || transactionId.trim().isEmpty()) {
            errors.add("Transaction ID is required");
        }
        
        if (userId == null || userId.trim().isEmpty()) {
            errors.add("User ID is required");
        }
        
        if (overallRiskScore == null) {
            errors.add("Overall risk score is required");
        } else if (overallRiskScore.compareTo(BigDecimal.ZERO) < 0 || 
                   overallRiskScore.compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.add("Overall risk score must be between 0 and 100");
        }
        
        if (riskLevel == null || riskLevel.trim().isEmpty()) {
            errors.add("Risk level is required");
        }
        
        if (amlRiskScore == null || fraudRiskScore == null || 
            complianceRiskScore == null || operationalRiskScore == null) {
            errors.add("All component risk scores are required");
        }
        
        if (!errors.isEmpty()) {
            this.validationPassed = false;
            this.validationErrors = errors;
            throw new IllegalStateException("Risk assessment validation failed: " + 
                                          String.join(", ", errors));
        } else {
            this.validationPassed = true;
            this.validationErrors = null;
        }
    }
    
    /**
     * Create audit-safe copy (without sensitive data)
     */
    public TransactionRiskAssessment createAuditCopy() {
        return TransactionRiskAssessment.builder()
                .assessmentId(assessmentId)
                .transactionId(transactionId)
                .userId("***") // Masked
                .assessedAt(assessedAt)
                .overallRiskScore(overallRiskScore)
                .riskLevel(riskLevel)
                .riskCategory(riskCategory)
                .riskDecision(riskDecision)
                .modelName(modelName)
                .modelVersion(modelVersion)
                .amlRiskScore(amlRiskScore)
                .fraudRiskScore(fraudRiskScore)
                .complianceRiskScore(complianceRiskScore)
                .operationalRiskScore(operationalRiskScore)
                .riskFlags(riskFlags != null ? new ArrayList<>(riskFlags) : null)
                .requiresManualReview(requiresManualReview)
                .escalationLevel(escalationLevel)
                .auditTrailId(auditTrailId)
                .build();
    }
    
    /**
     * Determine severity based on risk value
     */
    private String determineSeverity(BigDecimal value) {
        if (value.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "CRITICAL";
        } else if (value.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "HIGH";
        } else if (value.compareTo(BigDecimal.valueOf(30)) >= 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}