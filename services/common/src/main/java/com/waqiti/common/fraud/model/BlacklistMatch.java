package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a match against fraud blacklist with detailed analysis
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistMatch {
    
    private String matchId;
    private String blacklistEntryId;
    private String blacklistName;
    private String blacklistType;
    private String source;  // Added source field
    
    // Match details
    private String matchedValue;
    private String originalValue;
    private MatchType matchType;
    private Double confidence;
    private Double similarity;
    
    // Blacklist entry details
    private String entrySource;
    private LocalDateTime entryCreatedAt;
    private LocalDateTime entryUpdatedAt;
    private String entryCreatedBy;
    private String entryReason;
    private RiskLevel riskLevel;
    private String category;
    
    // Match context
    private String transactionId;
    private String userId;
    private String ipAddress;
    private String deviceId;
    private String sessionId;
    private LocalDateTime matchedAt;
    
    // Risk assessment
    private Double riskScore;
    private String riskReason;
    private Boolean isHighRisk;
    private Boolean critical;
    private String recommendedAction;
    private ActionUrgency actionUrgency;
    
    // Verification and validation
    private VerificationStatus verificationStatus;
    private Boolean isFalsePositive;
    private String falsePositiveReason;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    
    // Additional attributes
    private Map<String, String> additionalData;
    private String notes;
    private Integer occurrenceCount;
    private LocalDateTime lastOccurrence;
    
    /**
     * Types of blacklist matches
     */
    public enum MatchType {
        EXACT,           // Exact string match
        PARTIAL,         // Partial match
        FUZZY,           // Fuzzy/approximate match
        SUBSTRING,       // Substring match
        REGEX,           // Regular expression match
        PHONETIC,        // Phonetic similarity match
        SEMANTIC,        // Semantic similarity match
        HASH,            // Hash-based match
        PATTERN,         // Pattern-based match
        BEHAVIORAL       // Behavioral pattern match
    }
    
    /**
     * Risk levels for blacklist entries
     */
    public enum RiskLevel {
        LOW(0.3),
        MEDIUM(0.5),
        HIGH(0.7),
        CRITICAL(0.9),
        EXTREME(1.0);
        
        private final double score;
        
        RiskLevel(double score) {
            this.score = score;
        }
        
        public double getScore() {
            return score;
        }
    }
    
    /**
     * Action urgency levels
     */
    public enum ActionUrgency {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        IMMEDIATE
    }
    
    /**
     * Verification status for matches
     */
    public enum VerificationStatus {
        PENDING,
        VERIFIED,
        FALSE_POSITIVE,
        INCONCLUSIVE,
        EXPIRED
    }
    
    /**
     * Check if this match is critical based on risk level and other factors
     */
    public boolean isCritical() {
        return Boolean.TRUE.equals(critical) || 
               (riskLevel != null && riskLevel.ordinal() >= RiskLevel.HIGH.ordinal()) ||
               (actionUrgency != null && actionUrgency.ordinal() >= ActionUrgency.HIGH.ordinal());
    }
    
    /**
     * Calculate overall match score considering confidence, similarity, and risk level
     */
    public double calculateOverallScore() {
        double baseScore = 0.0;
        
        // Base score from confidence
        if (confidence != null) {
            baseScore += confidence * 0.4;
        }
        
        // Add similarity score
        if (similarity != null) {
            baseScore += similarity * 0.3;
        }
        
        // Add risk level score
        if (riskLevel != null) {
            baseScore += riskLevel.getScore() * 0.3;
        }
        
        // Adjust for match type
        switch (matchType) {
            case EXACT:
                baseScore *= 1.0;
                break;
            case HASH:
                baseScore *= 0.95;
                break;
            case REGEX:
            case PATTERN:
                baseScore *= 0.9;
                break;
            case SUBSTRING:
                baseScore *= 0.8;
                break;
            case FUZZY:
            case PHONETIC:
                baseScore *= 0.7;
                break;
            case SEMANTIC:
            case BEHAVIORAL:
                baseScore *= 0.6;
                break;
        }
        
        return Math.min(1.0, baseScore);
    }
    
    /**
     * Check if this match requires immediate action
     */
    public boolean requiresImmediateAction() {
        return actionUrgency == ActionUrgency.IMMEDIATE ||
               (riskLevel == RiskLevel.CRITICAL || riskLevel == RiskLevel.EXTREME) ||
               (confidence != null && confidence > 0.9 && matchType == MatchType.EXACT);
    }
    
    /**
     * Check if this is a high-confidence match
     */
    public boolean isHighConfidenceMatch() {
        return confidence != null && confidence > 0.8 && 
               (matchType == MatchType.EXACT || matchType == MatchType.HASH);
    }
    
    /**
     * Get recommended mitigation actions based on match characteristics
     */
    public String[] getRecommendedActions() {
        if (isFalsePositive != null && isFalsePositive) {
            return new String[]{"WHITELIST_ADD", "UPDATE_BLACKLIST"};
        }
        
        if (requiresImmediateAction()) {
            return new String[]{"BLOCK_TRANSACTION", "FREEZE_ACCOUNT", "ESCALATE_TO_ANALYST"};
        }
        
        if (riskLevel == RiskLevel.HIGH) {
            return new String[]{"REQUIRE_ADDITIONAL_AUTH", "ENABLE_ENHANCED_MONITORING"};
        }
        
        if (riskLevel == RiskLevel.MEDIUM) {
            return new String[]{"LOG_SECURITY_EVENT", "ENABLE_ENHANCED_MONITORING"};
        }
        
        return new String[]{"LOG_SECURITY_EVENT"};
    }
    
    /**
     * Generate detailed match description for logging and reporting
     */
    public String getDetailedDescription() {
        StringBuilder desc = new StringBuilder();
        
        desc.append("Blacklist match found: ");
        desc.append("Type=").append(matchType);
        desc.append(", List=").append(blacklistName);
        desc.append(", Value='").append(matchedValue).append("'");
        
        if (confidence != null) {
            desc.append(", Confidence=").append(String.format("%.2f", confidence));
        }
        
        if (riskLevel != null) {
            desc.append(", Risk=").append(riskLevel);
        }
        
        if (entryReason != null) {
            desc.append(", Reason=").append(entryReason);
        }
        
        return desc.toString();
    }
    
    /**
     * Check if match is still valid (not expired or verified as false positive)
     */
    public boolean isValidMatch() {
        return verificationStatus != VerificationStatus.FALSE_POSITIVE &&
               verificationStatus != VerificationStatus.EXPIRED &&
               (isFalsePositive == null || !isFalsePositive);
    }
}