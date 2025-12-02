package com.waqiti.common.enums;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;

/**
 * Comprehensive enterprise-grade business impact assessment framework
 * for the Waqiti financial platform with multi-dimensional impact analysis
 * across revenue, customers, operations, compliance, and strategic objectives.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessImpact {
    
    /**
     * Primary impact classification
     */
    private BusinessImpactLevel impactLevel;
    private String customImpactDescription;
    private double impactScore;
    private LocalDateTime assessmentTimestamp;
    private String assessmentVersion;
    
    /**
     * Financial impact metrics
     */
    private FinancialImpact financialImpact;
    private BigDecimal revenueImpact;
    private BigDecimal costImpact;
    private BigDecimal potentialLoss;
    private String currency;
    
    /**
     * Customer and user impact
     */
    private CustomerImpact customerImpact;
    private int affectedCustomers;
    private int potentialChurnCustomers;
    private double customerSatisfactionImpact;
    private List<String> affectedUserSegments;
    
    /**
     * Operational impact
     */
    private OperationalImpact operationalImpact;
    private List<String> affectedBusinessProcesses;
    private List<String> affectedServices;
    private Set<String> affectedRegions;
    private Duration expectedDowntime;
    
    /**
     * Compliance and regulatory impact
     */
    private ComplianceImpact complianceImpact;
    private List<String> regulatoryRisks;
    private boolean requiresRegulatoryNotification;
    private List<String> affectedComplianceFrameworks;
    
    /**
     * Strategic and reputational impact
     */
    private StrategicImpact strategicImpact;
    private ReputationalRisk reputationalRisk;
    private double brandImpactScore;
    private boolean mediaAttentionRisk;
    
    /**
     * Recovery and mitigation context
     */
    private RecoveryMetrics recoveryMetrics;
    private Duration estimatedRecoveryTime;
    private List<String> mitigationActions;
    private String recoveryPlan;
    
    /**
     * Stakeholder impact analysis
     */
    private Map<String, StakeholderImpact> stakeholderImpacts;
    private List<String> criticalStakeholders;
    private boolean requiresExecutiveEscalation;
    
    /**
     * Calculate comprehensive business impact score (0-100)
     */
    public double calculateImpactScore() {
        if (impactScore > 0) {
            return impactScore;
        }
        
        double score = 0.0;
        
        // Financial Impact (30% weight)
        score += calculateFinancialScore() * 0.30;
        
        // Customer Impact (25% weight) 
        score += calculateCustomerScore() * 0.25;
        
        // Operational Impact (20% weight)
        score += calculateOperationalScore() * 0.20;
        
        // Compliance Impact (15% weight)
        score += calculateComplianceScore() * 0.15;
        
        // Strategic Impact (10% weight)
        score += calculateStrategicScore() * 0.10;
        
        this.impactScore = Math.max(0.0, Math.min(100.0, score));
        return this.impactScore;
    }
    
    /**
     * Determine overall business impact level
     */
    public BusinessImpactLevel determineImpactLevel() {
        if (impactLevel != null) {
            return impactLevel;
        }
        
        double score = calculateImpactScore();
        
        // Critical overrides
        if (isCriticalSystemsAffected() || hasRegulatoryImplications() || 
            isExecutiveEscalationRequired()) {
            this.impactLevel = BusinessImpactLevel.CRITICAL;
            return impactLevel;
        }
        
        // Score-based classification
        if (score >= 80.0) {
            this.impactLevel = BusinessImpactLevel.CRITICAL;
        } else if (score >= 60.0) {
            this.impactLevel = BusinessImpactLevel.HIGH;
        } else if (score >= 40.0) {
            this.impactLevel = BusinessImpactLevel.MEDIUM;
        } else if (score >= 20.0) {
            this.impactLevel = BusinessImpactLevel.LOW;
        } else {
            this.impactLevel = BusinessImpactLevel.MINIMAL;
        }
        
        return this.impactLevel;
    }
    
    private double calculateFinancialScore() {
        if (financialImpact == null && revenueImpact == null) return 0.0;
        
        double score = 0.0;
        BigDecimal totalFinancialImpact = BigDecimal.ZERO;
        
        if (revenueImpact != null) totalFinancialImpact = totalFinancialImpact.add(revenueImpact);
        if (costImpact != null) totalFinancialImpact = totalFinancialImpact.add(costImpact);
        if (potentialLoss != null) totalFinancialImpact = totalFinancialImpact.add(potentialLoss);
        
        double impact = totalFinancialImpact.doubleValue();
        
        if (impact >= 1000000) score = 100.0;      // $1M+ = Critical
        else if (impact >= 500000) score = 80.0;   // $500K+ = High
        else if (impact >= 100000) score = 60.0;   // $100K+ = Medium-High
        else if (impact >= 50000) score = 40.0;    // $50K+ = Medium
        else if (impact >= 10000) score = 20.0;    // $10K+ = Low-Medium
        else if (impact >= 1000) score = 10.0;     // $1K+ = Low
        
        return score;
    }
    
    private double calculateCustomerScore() {
        if (customerImpact == null && affectedCustomers == 0) return 0.0;
        
        double score = 0.0;
        
        if (affectedCustomers >= 100000) score = 100.0;      // 100K+ customers
        else if (affectedCustomers >= 50000) score = 80.0;   // 50K+ customers
        else if (affectedCustomers >= 10000) score = 60.0;   // 10K+ customers
        else if (affectedCustomers >= 1000) score = 40.0;    // 1K+ customers
        else if (affectedCustomers >= 100) score = 20.0;     // 100+ customers
        else if (affectedCustomers >= 10) score = 10.0;      // 10+ customers
        
        // Add satisfaction impact
        if (customerSatisfactionImpact > 0) {
            score += customerSatisfactionImpact * 20.0; // Max 20 additional points
        }
        
        return Math.min(100.0, score);
    }
    
    private double calculateOperationalScore() {
        if (operationalImpact == null) return 0.0;
        
        double score = 0.0;
        
        // Service count impact
        if (affectedServices != null) {
            score += Math.min(40.0, affectedServices.size() * 5.0);
        }
        
        // Process impact
        if (affectedBusinessProcesses != null) {
            score += Math.min(30.0, affectedBusinessProcesses.size() * 7.5);
        }
        
        // Geographic impact
        if (affectedRegions != null) {
            score += Math.min(20.0, affectedRegions.size() * 10.0);
        }
        
        // Downtime impact
        if (expectedDowntime != null) {
            long hours = expectedDowntime.toHours();
            if (hours >= 24) score += 20.0;
            else if (hours >= 8) score += 15.0;
            else if (hours >= 4) score += 10.0;
            else if (hours >= 1) score += 5.0;
        }
        
        return Math.min(100.0, score);
    }
    
    private double calculateComplianceScore() {
        if (complianceImpact == null) return 0.0;
        
        double score = 0.0;
        
        if (requiresRegulatoryNotification) score += 50.0;
        
        if (regulatoryRisks != null) {
            score += Math.min(30.0, regulatoryRisks.size() * 10.0);
        }
        
        if (affectedComplianceFrameworks != null) {
            score += Math.min(20.0, affectedComplianceFrameworks.size() * 5.0);
        }
        
        return Math.min(100.0, score);
    }
    
    private double calculateStrategicScore() {
        if (strategicImpact == null) return 0.0;
        
        double score = 0.0;
        
        if (mediaAttentionRisk) score += 40.0;
        
        if (brandImpactScore > 0) {
            score += brandImpactScore * 30.0; // Max 30 points
        }
        
        if (requiresExecutiveEscalation) score += 30.0;
        
        return Math.min(100.0, score);
    }
    
    private boolean isCriticalSystemsAffected() {
        if (affectedServices == null) return false;
        
        Set<String> criticalServices = Set.of(
            "payment-service", "transaction-service", "wallet-service", 
            "core-banking-service", "compliance-service"
        );
        
        return affectedServices.stream().anyMatch(criticalServices::contains);
    }
    
    private boolean hasRegulatoryImplications() {
        return requiresRegulatoryNotification || 
               (regulatoryRisks != null && !regulatoryRisks.isEmpty());
    }
    
    private boolean isExecutiveEscalationRequired() {
        return requiresExecutiveEscalation ||
               calculateImpactScore() >= 80.0 ||
               (revenueImpact != null && revenueImpact.compareTo(new BigDecimal("500000")) >= 0) ||
               affectedCustomers >= 50000;
    }
    
    /**
     * Get human-readable impact assessment
     */
    public String getImpactSummary() {
        BusinessImpactLevel level = determineImpactLevel();
        double score = calculateImpactScore();
        
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Business Impact: %s (Score: %.1f/100)", 
            level.getDisplayName(), score));
        
        if (affectedCustomers > 0) {
            summary.append(String.format(" | %,d customers affected", affectedCustomers));
        }
        
        if (revenueImpact != null && revenueImpact.compareTo(BigDecimal.ZERO) > 0) {
            summary.append(String.format(" | Revenue impact: %s %s", 
                currency != null ? currency : "USD", revenueImpact));
        }
        
        if (requiresExecutiveEscalation) {
            summary.append(" | EXECUTIVE ESCALATION REQUIRED");
        }
        
        return summary.toString();
    }
    
    /**
     * Get recommended escalation actions
     */
    public List<String> getEscalationActions() {
        List<String> actions = new java.util.ArrayList<>();
        BusinessImpactLevel level = determineImpactLevel();
        
        switch (level) {
            case CRITICAL:
                actions.add("IMMEDIATE: Activate crisis management protocols");
                actions.add("IMMEDIATE: Notify C-suite executives within 15 minutes");
                actions.add("IMMEDIATE: Establish dedicated war room");
                actions.add("IMMEDIATE: Activate all relevant response teams");
                if (requiresRegulatoryNotification) {
                    actions.add("REGULATORY: Prepare regulatory notifications");
                }
                if (mediaAttentionRisk) {
                    actions.add("PR: Activate crisis communications team");
                }
                break;
                
            case HIGH:
                actions.add("URGENT: Notify senior management within 30 minutes");
                actions.add("URGENT: Activate incident response team");
                actions.add("URGENT: Implement customer communications plan");
                break;
                
            case MEDIUM:
                actions.add("Notify management within 2 hours");
                actions.add("Activate standard incident response procedures");
                actions.add("Monitor customer impact closely");
                break;
                
            case LOW:
                actions.add("Update management via standard channels");
                actions.add("Document lessons learned");
                break;
                
            case MINIMAL:
                actions.add("Log incident for trend analysis");
                break;
        }
        
        return actions;
    }
    
    /**
     * Generate detailed business impact report
     */
    public String generateImpactReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== BUSINESS IMPACT ASSESSMENT REPORT ===\n");
        report.append(String.format("Assessment Date: %s\n", assessmentTimestamp));
        report.append(String.format("Impact Level: %s (Score: %.1f/100)\n\n", 
            determineImpactLevel().getDisplayName(), calculateImpactScore()));
        
        // Executive Summary
        report.append("EXECUTIVE SUMMARY:\n");
        report.append(getImpactSummary()).append("\n\n");
        
        // Financial Impact
        if (revenueImpact != null || costImpact != null || potentialLoss != null) {
            report.append("FINANCIAL IMPACT:\n");
            if (revenueImpact != null) {
                report.append(String.format("- Revenue Impact: %s %s\n", 
                    currency != null ? currency : "USD", revenueImpact));
            }
            if (costImpact != null) {
                report.append(String.format("- Cost Impact: %s %s\n", 
                    currency != null ? currency : "USD", costImpact));
            }
            if (potentialLoss != null) {
                report.append(String.format("- Potential Loss: %s %s\n", 
                    currency != null ? currency : "USD", potentialLoss));
            }
            report.append("\n");
        }
        
        // Customer Impact
        if (affectedCustomers > 0 || customerSatisfactionImpact > 0) {
            report.append("CUSTOMER IMPACT:\n");
            report.append(String.format("- Affected Customers: %,d\n", affectedCustomers));
            if (potentialChurnCustomers > 0) {
                report.append(String.format("- Potential Churn: %,d customers\n", potentialChurnCustomers));
            }
            if (customerSatisfactionImpact > 0) {
                report.append(String.format("- Satisfaction Impact: %.2f\n", customerSatisfactionImpact));
            }
            report.append("\n");
        }
        
        // Operational Impact
        if (affectedServices != null || affectedBusinessProcesses != null) {
            report.append("OPERATIONAL IMPACT:\n");
            if (affectedServices != null && !affectedServices.isEmpty()) {
                report.append(String.format("- Affected Services: %s\n", 
                    String.join(", ", affectedServices)));
            }
            if (affectedBusinessProcesses != null && !affectedBusinessProcesses.isEmpty()) {
                report.append(String.format("- Affected Processes: %s\n", 
                    String.join(", ", affectedBusinessProcesses)));
            }
            if (expectedDowntime != null) {
                report.append(String.format("- Expected Downtime: %d hours\n", 
                    expectedDowntime.toHours()));
            }
            report.append("\n");
        }
        
        // Escalation Actions
        List<String> actions = getEscalationActions();
        if (!actions.isEmpty()) {
            report.append("REQUIRED ACTIONS:\n");
            for (int i = 0; i < actions.size(); i++) {
                report.append(String.format("%d. %s\n", i + 1, actions.get(i)));
            }
        }
        
        return report.toString();
    }
    
    /**
     * Get impact score for external use
     */
    public double getImpactScore() {
        return calculateImpactScore();
    }
    
    /**
     * Get impact level for external use
     */
    public String getImpactLevel() {
        return determineImpactLevel().getDisplayName();
    }
    
    /**
     * Set impact level method for builder pattern
     */
    public BusinessImpact impactLevel(String level) {
        try {
            this.impactLevel = BusinessImpactLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Default to MEDIUM if invalid
            this.impactLevel = BusinessImpactLevel.MEDIUM;
        }
        return this;
    }
    
    // Removed custom builder - using Lombok @Builder instead
    /* Commenting out custom builder to avoid conflicts with Lombok
    public static class BusinessImpactBuilder {
        private BusinessImpactLevel impactLevel;
        private String customImpactDescription;
        private double impactScore;
        private LocalDateTime assessmentTimestamp = LocalDateTime.now();
        private String assessmentVersion;
        private FinancialImpact financialImpact;
        private BigDecimal revenueImpact;
        private BigDecimal costImpact;
        private BigDecimal potentialLoss;
        private String currency;
        private CustomerImpact customerImpact;
        private int affectedCustomers;
        private int potentialChurnCustomers;
        private double customerSatisfactionImpact;
        private List<String> affectedUserSegments;
        private OperationalImpact operationalImpact;
        private List<String> affectedBusinessProcesses;
        private List<String> affectedServices;
        private Set<String> affectedRegions;
        private Duration expectedDowntime;
        private ComplianceImpact complianceImpact;
        private List<String> regulatoryRisks;
        private boolean requiresRegulatoryNotification;
        private List<String> affectedComplianceFrameworks;
        private StrategicImpact strategicImpact;
        private ReputationalRisk reputationalRisk;
        private double brandImpactScore;
        private boolean mediaAttentionRisk;
        private RecoveryMetrics recoveryMetrics;
        private Duration estimatedRecoveryTime;
        private List<String> mitigationActions;
        private String recoveryPlan;
        private Map<String, StakeholderImpact> stakeholderImpacts;
        private List<String> criticalStakeholders;
        private boolean requiresExecutiveEscalation;
        
        public BusinessImpactBuilder impactLevel(BusinessImpactLevel impactLevel) { this.impactLevel = impactLevel; return this; }
        public BusinessImpactBuilder customImpactDescription(String customImpactDescription) { this.customImpactDescription = customImpactDescription; return this; }
        public BusinessImpactBuilder impactScore(double impactScore) { this.impactScore = impactScore; return this; }
        public BusinessImpactBuilder assessmentTimestamp(LocalDateTime assessmentTimestamp) { this.assessmentTimestamp = assessmentTimestamp; return this; }
        public BusinessImpactBuilder assessmentVersion(String assessmentVersion) { this.assessmentVersion = assessmentVersion; return this; }
        public BusinessImpactBuilder financialImpact(FinancialImpact financialImpact) { this.financialImpact = financialImpact; return this; }
        public BusinessImpactBuilder revenueImpact(BigDecimal revenueImpact) { this.revenueImpact = revenueImpact; return this; }
        public BusinessImpactBuilder costImpact(BigDecimal costImpact) { this.costImpact = costImpact; return this; }
        public BusinessImpactBuilder potentialLoss(BigDecimal potentialLoss) { this.potentialLoss = potentialLoss; return this; }
        public BusinessImpactBuilder currency(String currency) { this.currency = currency; return this; }
        public BusinessImpactBuilder customerImpact(CustomerImpact customerImpact) { this.customerImpact = customerImpact; return this; }
        public BusinessImpactBuilder affectedCustomers(int affectedCustomers) { this.affectedCustomers = affectedCustomers; return this; }
        public BusinessImpactBuilder potentialChurnCustomers(int potentialChurnCustomers) { this.potentialChurnCustomers = potentialChurnCustomers; return this; }
        public BusinessImpactBuilder customerSatisfactionImpact(double customerSatisfactionImpact) { this.customerSatisfactionImpact = customerSatisfactionImpact; return this; }
        public BusinessImpactBuilder affectedUserSegments(List<String> affectedUserSegments) { this.affectedUserSegments = affectedUserSegments; return this; }
        public BusinessImpactBuilder operationalImpact(OperationalImpact operationalImpact) { this.operationalImpact = operationalImpact; return this; }
        public BusinessImpactBuilder affectedBusinessProcesses(List<String> affectedBusinessProcesses) { this.affectedBusinessProcesses = affectedBusinessProcesses; return this; }
        public BusinessImpactBuilder affectedServices(List<String> affectedServices) { this.affectedServices = affectedServices; return this; }
        public BusinessImpactBuilder affectedRegions(Set<String> affectedRegions) { this.affectedRegions = affectedRegions; return this; }
        public BusinessImpactBuilder expectedDowntime(Duration expectedDowntime) { this.expectedDowntime = expectedDowntime; return this; }
        public BusinessImpactBuilder complianceImpact(ComplianceImpact complianceImpact) { this.complianceImpact = complianceImpact; return this; }
        public BusinessImpactBuilder regulatoryRisks(List<String> regulatoryRisks) { this.regulatoryRisks = regulatoryRisks; return this; }
        public BusinessImpactBuilder requiresRegulatoryNotification(boolean requiresRegulatoryNotification) { this.requiresRegulatoryNotification = requiresRegulatoryNotification; return this; }
        public BusinessImpactBuilder affectedComplianceFrameworks(List<String> affectedComplianceFrameworks) { this.affectedComplianceFrameworks = affectedComplianceFrameworks; return this; }
        public BusinessImpactBuilder strategicImpact(StrategicImpact strategicImpact) { this.strategicImpact = strategicImpact; return this; }
        public BusinessImpactBuilder reputationalRisk(ReputationalRisk reputationalRisk) { this.reputationalRisk = reputationalRisk; return this; }
        public BusinessImpactBuilder brandImpactScore(double brandImpactScore) { this.brandImpactScore = brandImpactScore; return this; }
        public BusinessImpactBuilder mediaAttentionRisk(boolean mediaAttentionRisk) { this.mediaAttentionRisk = mediaAttentionRisk; return this; }
        public BusinessImpactBuilder recoveryMetrics(RecoveryMetrics recoveryMetrics) { this.recoveryMetrics = recoveryMetrics; return this; }
        public BusinessImpactBuilder estimatedRecoveryTime(Duration estimatedRecoveryTime) { this.estimatedRecoveryTime = estimatedRecoveryTime; return this; }
        public BusinessImpactBuilder mitigationActions(List<String> mitigationActions) { this.mitigationActions = mitigationActions; return this; }
        public BusinessImpactBuilder recoveryPlan(String recoveryPlan) { this.recoveryPlan = recoveryPlan; return this; }
        public BusinessImpactBuilder stakeholderImpacts(Map<String, StakeholderImpact> stakeholderImpacts) { this.stakeholderImpacts = stakeholderImpacts; return this; }
        public BusinessImpactBuilder criticalStakeholders(List<String> criticalStakeholders) { this.criticalStakeholders = criticalStakeholders; return this; }
        public BusinessImpactBuilder requiresExecutiveEscalation(boolean requiresExecutiveEscalation) { this.requiresExecutiveEscalation = requiresExecutiveEscalation; return this; }
        
        public BusinessImpact build() {
            return new BusinessImpact(impactLevel, customImpactDescription, impactScore, assessmentTimestamp, assessmentVersion, financialImpact, revenueImpact, costImpact, potentialLoss, currency, customerImpact, affectedCustomers, potentialChurnCustomers, customerSatisfactionImpact, affectedUserSegments, operationalImpact, affectedBusinessProcesses, affectedServices, affectedRegions, expectedDowntime, complianceImpact, regulatoryRisks, requiresRegulatoryNotification, affectedComplianceFrameworks, strategicImpact, reputationalRisk, brandImpactScore, mediaAttentionRisk, recoveryMetrics, estimatedRecoveryTime, mitigationActions, recoveryPlan, stakeholderImpacts, criticalStakeholders, requiresExecutiveEscalation);
        }
    }
    */
}

/**
 * Supporting classes for comprehensive business impact analysis
 */
@Data
@Builder
@NoArgsConstructor  
@AllArgsConstructor
class FinancialImpact {
    private BigDecimal directRevenueLoss;
    private BigDecimal indirectRevenueLoss;
    private BigDecimal operationalCosts;
    private BigDecimal complianceCosts;
    private BigDecimal recoveryInvestment;
    private String impactCurrency;
    private Duration impactPeriod;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CustomerImpact {
    private int directlyAffectedCustomers;
    private int indirectlyAffectedCustomers;
    private double churnProbability;
    private double satisfactionReduction;
    private List<String> affectedCustomerSegments;
    private Duration customerImpactDuration;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OperationalImpact {
    private List<String> disruptedProcesses;
    private Set<String> affectedSystems;
    private Map<String, String> serviceAvailability;
    private Duration totalDowntime;
    private int affectedEmployees;
    private List<String> operationalConstraints;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ComplianceImpact {
    private List<String> violatedRegulations;
    private boolean breachNotificationRequired;
    private Duration notificationTimeframe;
    private List<String> potentialPenalties;
    private String complianceRiskLevel;
    private List<String> remediationRequirements;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class StrategicImpact {
    private double strategicObjectiveImpact;
    private List<String> affectedInitiatives;
    private boolean competitiveDisadvantage;
    private double marketConfidenceImpact;
    private List<String> stakeholderConcerns;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ReputationalRisk {
    private String riskLevel;
    private boolean publicVisibility;
    private boolean mediaInterest;
    private double brandEquityImpact;
    private List<String> stakeholderPerceptions;
    private Duration reputationRecoveryTime;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RecoveryMetrics {
    private Duration targetRecoveryTime;
    private Duration actualRecoveryTime;
    private BigDecimal recoveryInvestment;
    private double recoveryEffectiveness;
    private List<String> recoveryMilestones;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class StakeholderImpact {
    private String stakeholderGroup;
    private String impactDescription;
    private String impactLevel;
    private List<String> specificConcerns;
    private String communicationPlan;
}