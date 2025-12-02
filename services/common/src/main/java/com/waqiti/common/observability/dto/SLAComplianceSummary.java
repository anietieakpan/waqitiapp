package com.waqiti.common.observability.dto;

import com.waqiti.common.enums.AvailabilitySLA;
import com.waqiti.common.enums.PerformanceSLA;
import com.waqiti.common.enums.ResponseTimeSLA;
import com.waqiti.common.enums.ThroughputSLA;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive SLA compliance tracking and reporting
 * Monitors service level agreements and provides detailed compliance metrics
 */
@Data
@Builder
public class SLAComplianceSummary {
    
    private LocalDateTime reportTimestamp;
    private String reportingPeriod;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    
    // Overall compliance status
    private ComplianceStatus overallStatus;
    private double overallComplianceScore;
    private String complianceLevel;
    private boolean isInCompliance;
    
    // SLA definitions and targets
    private List<SLADefinition> slaDefinitions;
    private Map<String, SLATarget> slaTargets;
    private Map<String, SLAResult> slaResults;
    
    // Performance SLAs
    private PerformanceSLA performanceSLA;
    private AvailabilitySLA availabilitySLA;
    private ResponseTimeSLA responseTimeSLA;
    private ThroughputSLA throughputSLA;
    
    // Business SLAs
    private BusinessSLA transactionProcessingSLA;
    private BusinessSLA paymentSLA;
    private BusinessSLA userExperienceSLA;
    
    // Security and Compliance SLAs
    private SecuritySLA securitySLA;
    private ComplianceSLA regulatorySLA;
    private DataProtectionSLA dataProtectionSLA;
    
    // Violations and incidents
    private List<SLAViolation> activeViolations;
    private List<SLAViolation> recentViolations;
    private int criticalViolationCount;
    private int totalViolationCount;
    
    // Trends and analysis
    private ComplianceTrend complianceTrend;
    private List<ComplianceMetric> historicalMetrics;
    private RiskAnalysis riskAnalysis;
    
    // Reporting and notifications
    private List<ComplianceAlert> complianceAlerts;
    private List<String> stakeholderNotifications;
    private EscalationStatus escalationStatus;
    
    // Financial impact
    private FinancialImpact financialImpact;
    private List<PenaltyCalculation> penalties;
    private CreditCalculation credits;
    
    /**
     * Calculate overall compliance score based on all SLA metrics
     */
    public double calculateOverallComplianceScore() {
        if (overallComplianceScore > 0) {
            return overallComplianceScore;
        }
        
        try {
            double totalWeight = 0.0;
            double weightedScore = 0.0;
            
            // Performance SLAs (40% weight)
            if (performanceSLA != null) {
                weightedScore += performanceSLA.getCompliancePercentage() * 0.4;
                totalWeight += 0.4;
            }
            
            // Availability SLAs (30% weight)  
            if (availabilitySLA != null) {
                weightedScore += availabilitySLA.getCompliancePercentage() * 0.3;
                totalWeight += 0.3;
            }
            
            // Business SLAs (20% weight)
            if (transactionProcessingSLA != null) {
                weightedScore += transactionProcessingSLA.getCompliancePercentage() * 0.2;
                totalWeight += 0.2;
            }
            
            // Security/Compliance SLAs (10% weight)
            if (securitySLA != null) {
                weightedScore += securitySLA.getCompliancePercentage() * 0.1;
                totalWeight += 0.1;
            }
            
            this.overallComplianceScore = totalWeight > 0 ? (weightedScore / totalWeight) * 100 : 0.0;
            return overallComplianceScore;
            
        } catch (Exception e) {
            this.overallComplianceScore = 0.0;
            return 0.0;
        }
    }
    
    /**
     * Determine overall compliance status based on score and violations
     */
    public ComplianceStatus determineOverallStatus() {
        if (overallStatus != null) {
            return overallStatus;
        }
        
        double score = calculateOverallComplianceScore();
        
        // Critical violations override score-based determination
        if (criticalViolationCount > 0) {
            this.overallStatus = ComplianceStatus.NON_COMPLIANT;
            this.isInCompliance = false;
            return overallStatus;
        }
        
        // Score-based determination
        if (score >= 99.0) {
            this.overallStatus = ComplianceStatus.EXCELLENT;
            this.complianceLevel = "Excellent";
            this.isInCompliance = true;
        } else if (score >= 95.0) {
            this.overallStatus = ComplianceStatus.COMPLIANT;
            this.complianceLevel = "Compliant";
            this.isInCompliance = true;
        } else if (score >= 90.0) {
            this.overallStatus = ComplianceStatus.AT_RISK;
            this.complianceLevel = "At Risk";
            this.isInCompliance = true;
        } else {
            this.overallStatus = ComplianceStatus.NON_COMPLIANT;
            this.complianceLevel = "Non-Compliant";
            this.isInCompliance = false;
        }
        
        return overallStatus;
    }
    
    /**
     * Check if any SLA is in breach
     */
    public boolean hasActiveBreach() {
        return !activeViolations.isEmpty() || criticalViolationCount > 0;
    }
    
    /**
     * Get list of SLAs that are currently in breach
     */
    public List<String> getBreachedSLAs() {
        if (activeViolations == null) {
            return List.of();
        }
        
        return activeViolations.stream()
            .map(SLAViolation::getSlaName)
            .distinct()
            .toList();
    }
    
    /**
     * Get SLAs that are at risk of breach
     */
    public List<String> getAtRiskSLAs() {
        List<String> atRisk = List.of();
        
        // Check each SLA against warning thresholds
        if (performanceSLA != null && performanceSLA.getCompliancePercentage() < 95.0) {
            atRisk.add("Performance SLA");
        }
        
        if (availabilitySLA != null && availabilitySLA.getCompliancePercentage() < 99.5) {
            atRisk.add("Availability SLA");
        }
        
        if (responseTimeSLA != null && responseTimeSLA.getCompliancePercentage() < 95.0) {
            atRisk.add("Response Time SLA");
        }
        
        return atRisk;
    }
    
    /**
     * Get comprehensive compliance summary for reporting
     */
    public String getComplianceSummary() {
        StringBuilder summary = new StringBuilder();
        
        ComplianceStatus status = determineOverallStatus();
        double score = calculateOverallComplianceScore();
        
        summary.append(String.format("SLA Compliance Report - %s\n", reportingPeriod));
        summary.append(String.format("Overall Status: %s (%.2f%%)\n", status.getDisplayName(), score));
        summary.append(String.format("Compliance Level: %s\n", complianceLevel));
        
        if (totalViolationCount > 0) {
            summary.append(String.format("Violations: %d total (%d critical)\n", 
                totalViolationCount, criticalViolationCount));
        }
        
        // Key SLA performance
        if (availabilitySLA != null) {
            summary.append(String.format("Availability: %.3f%% (Target: %.3f%%)\n",
                availabilitySLA.getActualUptime(), availabilitySLA.getTargetUptime()));
        }

        if (responseTimeSLA != null) {
            summary.append(String.format("Response Time: %.0fms (Target: <%.0fms)\n",
                responseTimeSLA.getActualMs(), responseTimeSLA.getTargetMs()));
        }
        
        if (financialImpact != null && financialImpact.getTotalImpact() > 0) {
            summary.append(String.format("Financial Impact: $%.2f\n", financialImpact.getTotalImpact()));
        }
        
        return summary.toString().trim();
    }
    
    /**
     * Get recommended actions based on compliance status
     */
    public List<String> getRecommendedActions() {
        List<String> actions = List.of();
        
        ComplianceStatus status = determineOverallStatus();
        
        if (status == ComplianceStatus.NON_COMPLIANT) {
            actions.add("URGENT: Immediate action required to restore SLA compliance");
            actions.add("Investigate root causes of SLA violations");
            actions.add("Implement corrective measures and monitor closely");
            actions.add("Notify stakeholders and prepare breach reports");
        } else if (status == ComplianceStatus.AT_RISK) {
            actions.add("Monitor at-risk SLAs closely for potential violations");
            actions.add("Implement preventive measures to maintain compliance");
            actions.add("Review capacity and performance optimization opportunities");
        } else if (status == ComplianceStatus.COMPLIANT) {
            actions.add("Continue monitoring to maintain current compliance levels");
            actions.add("Review and optimize SLA targets for better performance");
        }
        
        // Specific recommendations based on violations
        List<String> breachedSLAs = getBreachedSLAs();
        if (breachedSLAs.contains("Availability SLA")) {
            actions.add("Focus on infrastructure reliability and redundancy");
        }
        if (breachedSLAs.contains("Performance SLA")) {
            actions.add("Optimize application performance and resource allocation");
        }
        if (breachedSLAs.contains("Security SLA")) {
            actions.add("Strengthen security measures and incident response");
        }
        
        return actions;
    }
    
    /**
     * Calculate financial impact of SLA violations
     */
    public double getTotalFinancialImpact() {
        if (financialImpact != null) {
            return financialImpact.getTotalImpact();
        }
        
        double totalImpact = 0.0;
        
        if (penalties != null) {
            totalImpact += penalties.stream()
                .mapToDouble(PenaltyCalculation::getAmount)
                .sum();
        }
        
        if (credits != null) {
            totalImpact += credits.getTotalCredits();
        }
        
        return totalImpact;
    }
    
    /**
     * Get worst performing SLA
     */
    public String getWorstPerformingSLA() {
        double worstScore = 100.0;
        String worstSLA = "None";
        
        if (performanceSLA != null && performanceSLA.getCompliancePercentage() < worstScore) {
            worstScore = performanceSLA.getCompliancePercentage();
            worstSLA = "Performance SLA";
        }
        
        if (availabilitySLA != null && availabilitySLA.getCompliancePercentage() < worstScore) {
            worstScore = availabilitySLA.getCompliancePercentage();
            worstSLA = "Availability SLA";
        }
        
        if (responseTimeSLA != null && responseTimeSLA.getCompliancePercentage() < worstScore) {
            worstScore = responseTimeSLA.getCompliancePercentage();
            worstSLA = "Response Time SLA";
        }
        
        return String.format("%s (%.2f%%)", worstSLA, worstScore);
    }
    
    /**
     * Check if escalation is required based on violations
     */
    public boolean requiresEscalation() {
        if (escalationStatus != null) {
            return escalationStatus.isEscalationRequired();
        }
        
        // Escalate for critical violations or multiple breaches
        return criticalViolationCount > 0 || 
               totalViolationCount > 3 ||
               overallComplianceScore < 90.0;
    }
    
    /**
     * Create a compliant SLA summary
     */
    public static SLAComplianceSummary compliant() {
        return SLAComplianceSummary.builder()
            .reportTimestamp(LocalDateTime.now())
            .reportingPeriod("Current Month")
            .periodStart(LocalDateTime.now().minusMonths(1))
            .periodEnd(LocalDateTime.now())
            .overallStatus(ComplianceStatus.COMPLIANT)
            .overallComplianceScore(98.5)
            .complianceLevel("Compliant")
            .isInCompliance(true)
            .criticalViolationCount(0)
            .totalViolationCount(0)
            .activeViolations(List.of())
            .recentViolations(List.of())
            .complianceAlerts(List.of())
            .build();
    }
}

enum ComplianceStatus {
    EXCELLENT("Excellent", "#28a745"),
    COMPLIANT("Compliant", "#20c997"),
    AT_RISK("At Risk", "#ffc107"),
    NON_COMPLIANT("Non-Compliant", "#dc3545");
    
    private final String displayName;
    private final String colorCode;
    
    ComplianceStatus(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getColorCode() {
        return colorCode;
    }
}