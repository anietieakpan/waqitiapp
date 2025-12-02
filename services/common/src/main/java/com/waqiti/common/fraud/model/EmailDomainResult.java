package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of email domain analysis for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDomainResult {
    
    private String domain;
    private String topLevelDomain;
    private EmailRiskLevel riskLevel;
    private double riskScore;
    private String reputation;
    private double reputationScore;

    // Domain characteristics
    private boolean isDisposable;
    private boolean isEducational;
    private boolean isCorporate;
    private boolean isFreemail;
    private boolean isFree;
    private boolean isNewDomain;
    private boolean isValidFormat;
    private boolean isSuspicious;
    private boolean isMalicious;
    private boolean hasCustomMx;
    
    // Domain age and history
    private LocalDateTime domainCreationDate;
    private Integer domainAgeInDays;
    private LocalDateTime domainAge;
    private boolean hasHistoricalActivity;
    
    // Security indicators
    private boolean hasDmarc;
    private boolean hasSpf;
    private boolean hasDkim;
    private boolean isOnBlocklist;
    private boolean hasRecentSecurityIssues;
    
    // Fraud indicators
    private int fraudIncidentCount;
    private LocalDateTime lastFraudIncident;
    private List<String> fraudCategories;
    private boolean isTyposquatting;
    private boolean hasConfusingCharacters;
    
    // Analysis metadata
    private LocalDateTime analyzedAt;
    private String analysisSource;
    private Map<String, Object> additionalAttributes;
    private String confidence;
    private List<String> warnings;
    
    /**
     * Check if domain is considered safe
     */
    public boolean isSafe() {
        return riskLevel == EmailRiskLevel.SAFE || riskLevel == EmailRiskLevel.LOW;
    }
    
    /**
     * Check if domain requires additional verification
     */
    public boolean requiresVerification() {
        return riskLevel != null && riskLevel.requiresVerification();
    }
    
    /**
     * Check if domain should be blocked
     */
    public boolean shouldBeBlocked() {
        return riskLevel != null && riskLevel.requiresBlocking();
    }
    
    /**
     * Get overall domain trust score
     */
    public double getTrustScore() {
        return 1.0 - riskScore;
    }
    
    /**
     * Get security posture score based on email security settings
     */
    public double getSecurityScore() {
        double score = 0.0;
        if (hasDmarc) score += 0.4;
        if (hasSpf) score += 0.3;
        if (hasDkim) score += 0.3;
        return score;
    }
    
    /**
     * Generate detailed analysis summary
     */
    public String getAnalysisSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Domain: ").append(domain).append("\n");
        summary.append("Risk Level: ").append(riskLevel).append(" (").append(String.format("%.2f", riskScore)).append(")\n");
        
        if (isDisposable) summary.append("- Disposable email domain\n");
        if (isFreemail) summary.append("- Free email provider\n");
        if (isNewDomain) summary.append("- Recently created domain\n");
        if (isOnBlocklist) summary.append("- Found on security blocklist\n");
        if (fraudIncidentCount > 0) summary.append("- Previous fraud incidents: ").append(fraudIncidentCount).append("\n");
        
        return summary.toString();
    }
}