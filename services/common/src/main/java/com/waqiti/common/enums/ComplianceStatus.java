package com.waqiti.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enterprise-grade compliance status enumeration for regulatory and SLA compliance
 * across the Waqiti financial platform with comprehensive compliance level tracking.
 */
@Getter
@AllArgsConstructor
public enum ComplianceStatus {
    FULLY_COMPLIANT("Fully Compliant", "GREEN", "#28a745", 100, 
        "All compliance requirements met with no violations or risks"),
    
    MOSTLY_COMPLIANT("Mostly Compliant", "BLUE", "#17a2b8", 80, 
        "Minor compliance gaps that do not impact overall regulatory standing"),
    
    PARTIALLY_COMPLIANT("Partially Compliant", "YELLOW", "#ffc107", 60, 
        "Significant compliance gaps requiring immediate attention and remediation"),
    
    NON_COMPLIANT("Non-Compliant", "RED", "#dc3545", 20, 
        "Critical compliance failures requiring immediate escalation and correction"),
    
    COMPLIANCE_UNKNOWN("Compliance Unknown", "GRAY", "#6c757d", 0, 
        "Compliance status cannot be determined - assessment required"),
    
    UNDER_REVIEW("Under Review", "ORANGE", "#fd7e14", 40, 
        "Compliance assessment in progress - awaiting review results"),
    
    EXEMPTED("Exempted", "PURPLE", "#6f42c1", 95, 
        "Formally exempted from specific compliance requirements"),
    
    REMEDIATION_IN_PROGRESS("Remediation in Progress", "TEAL", "#20c997", 70, 
        "Active remediation efforts underway to address compliance gaps");

    private final String displayName;
    private final String statusColor;
    private final String colorCode;
    private final int complianceScore;
    private final String description;

    /**
     * Get compliance status based on compliance percentage
     */
    public static ComplianceStatus fromScore(double compliancePercentage) {
        if (compliancePercentage >= 98.0) {
            return FULLY_COMPLIANT;
        } else if (compliancePercentage >= 90.0) {
            return MOSTLY_COMPLIANT; 
        } else if (compliancePercentage >= 70.0) {
            return PARTIALLY_COMPLIANT;
        } else if (compliancePercentage >= 50.0) {
            return UNDER_REVIEW;
        } else {
            return NON_COMPLIANT;
        }
    }

    /**
     * Check if this status requires immediate escalation
     */
    public boolean requiresEscalation() {
        return this == NON_COMPLIANT || this == PARTIALLY_COMPLIANT;
    }

    /**
     * Check if this status is acceptable for production operations
     */
    public boolean isAcceptableForProduction() {
        return this == FULLY_COMPLIANT || this == MOSTLY_COMPLIANT || this == EXEMPTED;
    }

    /**
     * Check if this status requires regulatory notification
     */
    public boolean requiresRegulatoryNotification() {
        return this == NON_COMPLIANT;
    }

    /**
     * Get risk level associated with this compliance status
     */
    public RiskLevel getRiskLevel() {
        return switch (this) {
            case FULLY_COMPLIANT, EXEMPTED -> RiskLevel.LOW;
            case MOSTLY_COMPLIANT -> RiskLevel.MEDIUM;
            case PARTIALLY_COMPLIANT, UNDER_REVIEW, REMEDIATION_IN_PROGRESS -> RiskLevel.HIGH;
            case NON_COMPLIANT -> RiskLevel.CRITICAL;
            case COMPLIANCE_UNKNOWN -> RiskLevel.UNKNOWN;
        };
    }

    /**
     * Get recommended actions for this compliance status
     */
    public String[] getRecommendedActions() {
        return switch (this) {
            case FULLY_COMPLIANT -> new String[]{
                "Maintain current compliance practices",
                "Continue regular compliance monitoring",
                "Document compliance evidence"
            };
            case MOSTLY_COMPLIANT -> new String[]{
                "Address minor compliance gaps",
                "Enhance compliance monitoring",
                "Review and update policies"
            };
            case PARTIALLY_COMPLIANT -> new String[]{
                "URGENT: Develop remediation plan",
                "Assign compliance team resources",
                "Implement temporary controls",
                "Schedule management review"
            };
            case NON_COMPLIANT -> new String[]{
                "IMMEDIATE: Stop non-compliant activities",
                "Activate crisis management protocols",
                "Notify regulatory authorities if required",
                "Engage legal and compliance teams",
                "Implement emergency controls"
            };
            case UNDER_REVIEW -> new String[]{
                "Expedite compliance assessment",
                "Implement precautionary controls",
                "Prepare contingency plans"
            };
            case REMEDIATION_IN_PROGRESS -> new String[]{
                "Monitor remediation progress",
                "Ensure adequate resources",
                "Track milestone completion"
            };
            case EXEMPTED -> new String[]{
                "Maintain exemption documentation",
                "Monitor exemption validity",
                "Review exemption conditions"
            };
            case COMPLIANCE_UNKNOWN -> new String[]{
                "URGENT: Conduct compliance assessment",
                "Engage compliance experts",
                "Implement conservative controls"
            };
        };
    }
}