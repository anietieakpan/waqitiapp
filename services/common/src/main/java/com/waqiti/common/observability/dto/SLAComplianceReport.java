package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.waqiti.common.enums.ViolationSeverity;
import com.waqiti.common.enums.ComplianceStatus;
import com.waqiti.common.enums.TrendDirection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-grade SLA compliance reporting providing comprehensive analysis
 * of service level agreements across the Waqiti platform with detailed
 * violation tracking and performance metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SLAComplianceReport {
    
    /**
     * Report metadata
     */
    private LocalDateTime generatedAt;
    private LocalDateTime timestamp; // Alias for generatedAt for compatibility
    private LocalDateTime reportPeriodStart;
    private LocalDateTime reportPeriodEnd;
    private String reportVersion;
    private String generatedBy;
    
    /**
     * Overall compliance metrics
     */
    private double overallComplianceScore;
    private ComplianceStatus overallStatus;
    private int totalSLAs;
    private int compliantSLAs;
    private int violatedSLAs;
    private int atRiskSLAs;
    
    /**
     * Service-specific SLA compliance
     */
    private Map<String, ServiceSLACompliance> serviceCompliance;
    private List<String> servicesInViolation;
    private List<String> servicesAtRisk;
    private List<String> topPerformingServices;
    
    /**
     * SLA violations and incidents
     */
    private List<SLAViolation> activeSLAViolations;
    private List<SLAViolation> recentViolations;
    private int criticalViolations;
    private int majorViolations;
    private int minorViolations;
    
    /**
     * Performance against SLA targets
     */
    private AvailabilitySLA availabilitySLA;
    private SLAMetric systemAvailabilitySLA; // System availability SLA metrics (alias for availabilitySLA)
    private ResponseTimeSLA responseTimeSLA;
    private SLAMetric apiResponseTimeSLA; // API response time SLA
    private ErrorRateSLA errorRateSLA;
    private ThroughputSLA throughputSLA;
    private SLAMetric paymentProcessingSLA; // Payment processing SLA metrics
    private SLAMetric databasePerformanceSLA; // Database performance SLA metrics
    
    /**
     * Business impact assessment
     */
    private BusinessImpactAssessment businessImpact;
    private double estimatedRevenueImpact;
    private int affectedCustomers;
    private List<String> affectedBusinessFeatures;
    
    /**
     * Trending and historical analysis
     */
    private SLATrends trends;
    private Map<String, Double> monthlyComplianceScores;
    private List<ComplianceImprovement> improvementOpportunities;
    
    /**
     * Financial and contractual implications
     */
    private double potentialPenalties;
    private double serviceLevelCredits;
    private List<ContractualObligation> contractualObligations;
    private List<String> customerNotifications;
    
    /**
     * Calculate overall compliance score based on all SLAs
     */
    public double calculateOverallComplianceScore() {
        if (overallComplianceScore > 0) {
            return overallComplianceScore;
        }
        
        if (totalSLAs == 0) {
            this.overallComplianceScore = 100.0;
            return overallComplianceScore;
        }
        
        double score = 100.0;
        
        // Weighted scoring based on violation severity
        score -= (criticalViolations * 25.0);  // 25 points per critical violation
        score -= (majorViolations * 15.0);     // 15 points per major violation
        score -= (minorViolations * 5.0);      // 5 points per minor violation
        
        // Factor in services at risk
        score -= (servicesAtRisk.size() * 2.0); // 2 points per service at risk
        
        // Performance factor
        double complianceRatio = (double) compliantSLAs / totalSLAs;
        score = score * complianceRatio;
        
        this.overallComplianceScore = Math.max(0.0, Math.min(100.0, score));
        return overallComplianceScore;
    }
    
    /**
     * Determine overall compliance status
     */
    public ComplianceStatus determineComplianceStatus() {
        if (overallStatus != null) {
            return overallStatus;
        }
        
        double score = calculateOverallComplianceScore();
        
        // Critical violations always result in non-compliant status
        if (criticalViolations > 0) {
            this.overallStatus = ComplianceStatus.NON_COMPLIANT;
            return overallStatus;
        }
        
        if (score >= 98.0) {
            this.overallStatus = ComplianceStatus.FULLY_COMPLIANT;
        } else if (score >= 95.0) {
            this.overallStatus = ComplianceStatus.MOSTLY_COMPLIANT;
        } else if (score >= 90.0) {
            this.overallStatus = ComplianceStatus.PARTIALLY_COMPLIANT;
        } else {
            this.overallStatus = ComplianceStatus.NON_COMPLIANT;
        }
        
        return overallStatus;
    }
    
    /**
     * Get critical SLA violations requiring immediate attention
     */
    public List<SLAViolation> getCriticalViolations() {
        if (activeSLAViolations == null) {
            return List.of();
        }
        
        return activeSLAViolations.stream()
            .filter(violation -> violation.getSeverity() == ViolationSeverity.CRITICAL)
            .sorted((v1, v2) -> v2.getDetectedAt().compareTo(v1.getDetectedAt()))
            .toList();
    }
    
    /**
     * Calculate financial impact of SLA violations
     */
    public double calculateFinancialImpact() {
        return potentialPenalties + serviceLevelCredits + estimatedRevenueImpact;
    }
    
    /**
     * Generate executive summary of SLA compliance
     */
    public String generateExecutiveSummary() {
        StringBuilder summary = new StringBuilder();
        
        ComplianceStatus status = determineComplianceStatus();
        double score = calculateOverallComplianceScore();
        
        summary.append(String.format("SLA Compliance Status: %s (Score: %.1f/100)\n", 
            status.getDisplayName(), score));
        
        summary.append(String.format("Compliance Rate: %d/%d SLAs (%.1f%%)\n", 
            compliantSLAs, totalSLAs, ((double) compliantSLAs / totalSLAs) * 100));
        
        if (criticalViolations > 0) {
            summary.append(String.format("CRITICAL: %d critical violations requiring immediate action\n", 
                criticalViolations));
        }
        
        if (majorViolations > 0) {
            summary.append(String.format("WARNING: %d major violations detected\n", majorViolations));
        }
        
        if (affectedCustomers > 0) {
            summary.append(String.format("Customer Impact: %d customers affected\n", affectedCustomers));
        }
        
        double financialImpact = calculateFinancialImpact();
        if (financialImpact > 0) {
            summary.append(String.format("Financial Impact: $%.2f\n", financialImpact));
        }
        
        return summary.toString().trim();
    }
    
    /**
     * Get actionable recommendations based on current compliance status
     */
    public List<String> getComplianceRecommendations() {
        List<String> recommendations = new java.util.ArrayList<>();
        
        if (overallStatus == ComplianceStatus.NON_COMPLIANT) {
            recommendations.add("URGENT: Implement immediate corrective actions for critical SLA violations");
            recommendations.add("Activate incident response procedures for affected services");
            recommendations.add("Review resource allocation and capacity planning");
            recommendations.add("Consider temporary service level adjustments if contractually permitted");
        }
        
        if (criticalViolations > 0) {
            recommendations.add("Prioritize resolution of critical SLA violations to avoid penalties");
            recommendations.add("Implement enhanced monitoring for services in violation");
        }
        
        if (servicesAtRisk.size() > 0) {
            recommendations.add("Proactively address services showing signs of SLA risk");
            recommendations.add("Review and optimize performance of at-risk services");
        }
        
        if (trends != null && trends.getComplianceTrend() == TrendDirection.DECLINING) {
            recommendations.add("Investigate root causes of declining SLA compliance trend");
            recommendations.add("Implement process improvements to reverse negative trends");
        }
        
        if (potentialPenalties > 0) {
            recommendations.add("Review contractual obligations to minimize financial penalties");
            recommendations.add("Implement customer communication plan for SLA violations");
        }
        
        return recommendations;
    }
    
    /**
     * Generate detailed compliance report
     */
    public String generateDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== SLA COMPLIANCE REPORT ===\n");
        report.append(String.format("Generated: %s\n", generatedAt));
        report.append(String.format("Period: %s to %s\n\n", reportPeriodStart, reportPeriodEnd));
        
        // Executive summary
        report.append("EXECUTIVE SUMMARY:\n");
        report.append(generateExecutiveSummary()).append("\n\n");
        
        // Compliance breakdown
        report.append("COMPLIANCE BREAKDOWN:\n");
        report.append(String.format("- Total SLAs: %d\n", totalSLAs));
        report.append(String.format("- Compliant: %d\n", compliantSLAs));
        report.append(String.format("- Violated: %d\n", violatedSLAs));
        report.append(String.format("- At Risk: %d\n\n", atRiskSLAs));
        
        // Violation severity
        report.append("VIOLATION SEVERITY:\n");
        report.append(String.format("- Critical: %d\n", criticalViolations));
        report.append(String.format("- Major: %d\n", majorViolations));
        report.append(String.format("- Minor: %d\n\n", minorViolations));
        
        // Financial impact
        double financialImpact = calculateFinancialImpact();
        if (financialImpact > 0) {
            report.append("FINANCIAL IMPACT:\n");
            report.append(String.format("- Potential Penalties: $%.2f\n", potentialPenalties));
            report.append(String.format("- Service Level Credits: $%.2f\n", serviceLevelCredits));
            report.append(String.format("- Estimated Revenue Impact: $%.2f\n", estimatedRevenueImpact));
            report.append(String.format("- Total Impact: $%.2f\n\n", financialImpact));
        }
        
        // Recommendations
        List<String> recommendations = getComplianceRecommendations();
        if (!recommendations.isEmpty()) {
            report.append("RECOMMENDATIONS:\n");
            for (int i = 0; i < recommendations.size(); i++) {
                report.append(String.format("%d. %s\n", i + 1, recommendations.get(i)));
            }
        }
        
        return report.toString();
    }
    
    /**
     * Check if immediate action is required
     */
    public boolean requiresImmediateAction() {
        return criticalViolations > 0 || 
               overallStatus == ComplianceStatus.NON_COMPLIANT ||
               potentialPenalties > 10000; // Threshold for significant penalties
    }
}

/**
 * Supporting classes for comprehensive SLA compliance management
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ServiceSLACompliance {
    private String serviceName;
    private double complianceScore;
    private ComplianceStatus status;
    private int totalSLAs;
    private int compliantSLAs;
    private List<SLAViolation> violations;
    private PerformanceMetrics performanceMetrics;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AvailabilitySLA {
    private double targetAvailability;
    private double actualAvailability;
    private boolean inCompliance;
    private double downtimeMinutes;
    private List<String> downtimeIncidents;
    
    public double getCompliancePercentage() { 
        return targetAvailability > 0 ? (actualAvailability / targetAvailability) * 100 : 0;
    }
    public double getActualValue() { return actualAvailability; }
    public double getTargetValue() { return targetAvailability; }
}


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ResponseTimeSLA {
    private double targetResponseTime;
    private double actualResponseTime;
    private boolean inCompliance;
    private double p95ResponseTime;
    private double p99ResponseTime;

    public double getActualValue() { return actualResponseTime; }
    public double getTargetValue() { return targetResponseTime; }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorRateSLA {
    private double targetErrorRate;
    private double actualErrorRate;
    private boolean inCompliance;
    private long totalErrors;
    private long totalRequests;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ThroughputSLA {
    private double targetThroughput;
    private double actualThroughput;
    private boolean inCompliance;
    private long totalRequests;
    private String unit;

    public double getActualValue() { return actualThroughput; }
    public double getTargetValue() { return targetThroughput; }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BusinessImpactAssessment {
    private String impactLevel;
    private List<String> affectedBusinessProcesses;
    private double customerSatisfactionImpact;
    private String reputationalRisk;
    private List<String> mitigationActions;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SLATrends {
    private TrendDirection complianceTrend;
    private Map<String, Double> weeklyComplianceScores;
    private List<String> improvingServices;
    private List<String> decliningServices;
    private double trendVelocity;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ComplianceImprovement {
    private String area;
    private String recommendation;
    private double potentialImpact;
    private String priority;
    private String timeline;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ContractualObligation {
    private String obligationType;
    private String description;
    private LocalDateTime deadline;
    private String status;
    private double penaltyAmount;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PerformanceMetrics {
    private double avgResponseTime;
    private double availability;
    private double errorRate;
    private double throughput;
    private LocalDateTime lastMeasured;
}

