package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Comprehensive enumeration of enforcement action types with detailed metadata
 */
@Getter
@AllArgsConstructor
public enum EnforcementActionType {
    
    // === ACCOUNT-LEVEL ACTIONS ===
    ACCOUNT_SUSPENSION(
        "Account Suspension",
        "Temporarily suspend account access and transactions",
        ActionCategory.ACCOUNT_CONTROL,
        ActionSeverity.MEDIUM,
        List.of("CUSTOMER_NOTIFICATION", "APPEAL_RIGHTS"),
        Map.of("reversible", true, "maxDurationDays", 90)
    ),
    
    ACCOUNT_CLOSURE(
        "Account Closure",
        "Permanently close account and terminate relationship",
        ActionCategory.ACCOUNT_CONTROL,
        ActionSeverity.HIGH,
        List.of("CUSTOMER_NOTIFICATION", "APPEAL_RIGHTS", "LEGAL_REVIEW"),
        Map.of("reversible", false, "fundsReturnPeriod", 30)
    ),
    
    ACCOUNT_RESTRICTION(
        "Account Restriction",
        "Impose specific limitations on account usage",
        ActionCategory.ACCOUNT_CONTROL,
        ActionSeverity.LOW,
        List.of("CUSTOMER_NOTIFICATION"),
        Map.of("reversible", true, "allowsPartialAccess", true)
    ),
    
    ENHANCED_MONITORING(
        "Enhanced Account Monitoring",
        "Increase monitoring and reporting requirements",
        ActionCategory.MONITORING,
        ActionSeverity.LOW,
        List.of("INTERNAL_APPROVAL"),
        Map.of("transparent", true, "duration", "ongoing")
    ),
    
    // === TRANSACTION-LEVEL ACTIONS ===
    ALLOW(
        "Allow Transaction",
        "Allow the transaction to proceed without restriction",
        ActionCategory.TRANSACTION_CONTROL,
        ActionSeverity.LOW,
        List.of(),
        Map.of("granular", true, "immediateEffect", true, "noAction", true)
    ),

    REVIEW(
        "Manual Review",
        "Send transaction for manual review before processing",
        ActionCategory.MONITORING,
        ActionSeverity.MEDIUM,
        List.of("ANALYST_REVIEW"),
        Map.of("requiresReview", true, "allowsCompletion", false, "timebound", true)
    ),

    BLOCK(
        "Block Transaction",
        "Block the current transaction",
        ActionCategory.TRANSACTION_CONTROL,
        ActionSeverity.HIGH,
        List.of("IMMEDIATE_EFFECT"),
        Map.of("granular", true, "immediateEffect", true)
    ),

    FLAG(
        "Flag for Review",
        "Flag transaction for manual review",
        ActionCategory.MONITORING,
        ActionSeverity.MEDIUM,
        List.of("ANALYST_REVIEW"),
        Map.of("requiresReview", true, "allowsCompletion", false)
    ),
    
    TRANSACTION_BLOCKING(
        "Transaction Blocking",
        "Block specific transactions or transaction types",
        ActionCategory.TRANSACTION_CONTROL,
        ActionSeverity.MEDIUM,
        List.of("CUSTOMER_NOTIFICATION", "JUSTIFICATION_REQUIRED"),
        Map.of("granular", true, "immediateEffect", true)
    ),
    
    PAYMENT_REVERSAL(
        "Payment Reversal",
        "Reverse completed payments and transactions",
        ActionCategory.TRANSACTION_CONTROL,
        ActionSeverity.HIGH,
        List.of("LEGAL_REVIEW", "CUSTOMER_NOTIFICATION"),
        Map.of("timeLimit", 180, "requiresEvidence", true)
    ),
    
    TRANSACTION_LIMITS(
        "Transaction Limits",
        "Impose daily, monthly, or per-transaction limits",
        ActionCategory.TRANSACTION_CONTROL,
        ActionSeverity.LOW,
        List.of("CUSTOMER_NOTIFICATION"),
        Map.of("configurable", true, "graduated", true)
    ),
    
    // === FINANCIAL ACTIONS ===
    ASSET_FREEZING(
        "Asset Freezing",
        "Freeze account funds and assets",
        ActionCategory.FINANCIAL,
        ActionSeverity.HIGH,
        List.of("LEGAL_AUTHORITY", "COURT_ORDER", "CUSTOMER_NOTIFICATION"),
        Map.of("requiresCourtOrder", true, "emergencyPowers", true)
    ),
    
    FUND_SEIZURE(
        "Fund Seizure",
        "Seize funds for investigation or forfeiture",
        ActionCategory.FINANCIAL,
        ActionSeverity.SEVERE,
        List.of("LEGAL_AUTHORITY", "COURT_ORDER", "DUE_PROCESS"),
        Map.of("requiresCourtOrder", true, "criminalProceeding", true)
    ),
    
    PENALTY_ASSESSMENT(
        "Penalty Assessment",
        "Assess financial penalties for violations",
        ActionCategory.FINANCIAL,
        ActionSeverity.MEDIUM,
        List.of("LEGAL_REVIEW", "CUSTOMER_NOTIFICATION", "APPEAL_RIGHTS"),
        Map.of("calculationMethod", "regulatory", "appealable", true)
    ),
    
    // === REGULATORY ACTIONS ===
    SUSPICIOUS_ACTIVITY_REPORT(
        "Suspicious Activity Report",
        "File SAR with regulatory authorities",
        ActionCategory.REGULATORY,
        ActionSeverity.MEDIUM,
        List.of("REGULATORY_FILING", "CONFIDENTIALITY"),
        Map.of("mandatoryFiling", true, "confidential", true, "timeLimit", 30)
    ),
    
    CURRENCY_TRANSACTION_REPORT(
        "Currency Transaction Report",
        "File CTR for large currency transactions",
        ActionCategory.REGULATORY,
        ActionSeverity.LOW,
        List.of("REGULATORY_FILING"),
        Map.of("automaticTrigger", true, "threshold", 10000)
    ),
    
    LAW_ENFORCEMENT_REFERRAL(
        "Law Enforcement Referral",
        "Refer case to law enforcement agencies",
        ActionCategory.REGULATORY,
        ActionSeverity.SEVERE,
        List.of("LEGAL_REVIEW", "EVIDENCE_PACKAGE", "MANAGEMENT_APPROVAL"),
        Map.of("criminalSuspicion", true, "evidenceRequired", true)
    ),
    
    REGULATORY_NOTIFICATION(
        "Regulatory Notification",
        "Notify regulatory bodies of violations",
        ActionCategory.REGULATORY,
        ActionSeverity.MEDIUM,
        List.of("REGULATORY_FILING", "DOCUMENTATION"),
        Map.of("mandatoryNotification", true, "timebound", true)
    ),
    
    // === ADMINISTRATIVE ACTIONS ===
    WARNING_NOTICE(
        "Warning Notice",
        "Issue formal warning to customer",
        ActionCategory.ADMINISTRATIVE,
        ActionSeverity.LOW,
        List.of("CUSTOMER_NOTIFICATION", "DOCUMENTATION"),
        Map.of("educational", true, "noImmediateImpact", true)
    ),
    
    COMPLIANCE_ORDER(
        "Compliance Order",
        "Order customer to take corrective action",
        ActionCategory.ADMINISTRATIVE,
        ActionSeverity.MEDIUM,
        List.of("LEGAL_REVIEW", "CUSTOMER_NOTIFICATION", "DEADLINE"),
        Map.of("enforceable", true, "timebound", true)
    ),
    
    CEASE_AND_DESIST(
        "Cease and Desist Order",
        "Order immediate cessation of specific activities",
        ActionCategory.ADMINISTRATIVE,
        ActionSeverity.HIGH,
        List.of("LEGAL_AUTHORITY", "IMMEDIATE_EFFECT", "APPEAL_RIGHTS"),
        Map.of("immediateCompliance", true, "specificActivities", true)
    ),
    
    // === OPERATIONAL ACTIONS ===
    SYSTEM_ACCESS_REVOCATION(
        "System Access Revocation",
        "Revoke access to systems and platforms",
        ActionCategory.OPERATIONAL,
        ActionSeverity.MEDIUM,
        List.of("IT_COORDINATION", "CUSTOMER_NOTIFICATION"),
        Map.of("immediateEffect", true, "systemWide", true)
    ),
    
    SERVICE_TERMINATION(
        "Service Termination",
        "Terminate specific services or all services",
        ActionCategory.OPERATIONAL,
        ActionSeverity.HIGH,
        List.of("LEGAL_REVIEW", "CUSTOMER_NOTIFICATION", "APPEAL_RIGHTS"),
        Map.of("permanent", true, "serviceSpecific", true)
    ),
    
    IP_BLOCKING(
        "IP Address Blocking",
        "Block access from specific IP addresses",
        ActionCategory.OPERATIONAL,
        ActionSeverity.LOW,
        List.of("TECHNICAL_IMPLEMENTATION"),
        Map.of("technical", true, "bypassable", true)
    ),
    
    DEVICE_BLOCKING(
        "Device Blocking",
        "Block access from specific devices",
        ActionCategory.OPERATIONAL,
        ActionSeverity.MEDIUM,
        List.of("TECHNICAL_IMPLEMENTATION", "CUSTOMER_NOTIFICATION"),
        Map.of("deviceFingerprinting", true, "bypassable", false)
    ),
    
    // === MERCHANT-SPECIFIC ACTIONS ===
    MERCHANT_SUSPENSION(
        "Merchant Account Suspension",
        "Suspend merchant processing capabilities",
        ActionCategory.MERCHANT,
        ActionSeverity.MEDIUM,
        List.of("MERCHANT_NOTIFICATION", "APPEAL_RIGHTS", "FUND_HOLD"),
        Map.of("affectsRevenue", true, "temporaryMeasure", true)
    ),
    
    MERCHANT_TERMINATION(
        "Merchant Account Termination",
        "Permanently terminate merchant relationship",
        ActionCategory.MERCHANT,
        ActionSeverity.HIGH,
        List.of("LEGAL_REVIEW", "MERCHANT_NOTIFICATION", "FUND_SETTLEMENT"),
        Map.of("permanent", true, "industryBlacklist", true)
    ),
    
    CHARGEBACK_LIABILITY(
        "Chargeback Liability Assignment",
        "Assign chargeback liability to merchant",
        ActionCategory.MERCHANT,
        ActionSeverity.MEDIUM,
        List.of("EVIDENCE_REVIEW", "MERCHANT_NOTIFICATION"),
        Map.of("financialImpact", true, "disputeable", true)
    ),
    
    // === GEOGRAPHIC AND TEMPORAL RESTRICTIONS ===
    GEOGRAPHIC_RESTRICTION(
        "Geographic Access Restriction",
        "Restrict access from specific locations",
        ActionCategory.ACCESS_CONTROL,
        ActionSeverity.MEDIUM,
        List.of("CUSTOMER_NOTIFICATION", "JUSTIFICATION"),
        Map.of("locationBased", true, "vpnDetection", true)
    ),
    
    TIME_BASED_RESTRICTION(
        "Time-Based Access Restriction",
        "Restrict access during specific time periods",
        ActionCategory.ACCESS_CONTROL,
        ActionSeverity.LOW,
        List.of("CUSTOMER_NOTIFICATION"),
        Map.of("scheduled", true, "temporary", true)
    ),
    
    // === INVESTIGATION ACTIONS ===
    EVIDENCE_PRESERVATION(
        "Evidence Preservation Order",
        "Preserve digital evidence and records",
        ActionCategory.INVESTIGATION,
        ActionSeverity.MEDIUM,
        List.of("LEGAL_AUTHORITY", "TECHNICAL_COORDINATION"),
        Map.of("forensicImaging", true, "chainOfCustody", true)
    ),
    
    SUBPOENA_COMPLIANCE(
        "Subpoena Compliance",
        "Comply with legal subpoenas for information",
        ActionCategory.INVESTIGATION,
        ActionSeverity.HIGH,
        List.of("LEGAL_REVIEW", "COURT_ORDER", "CONFIDENTIALITY"),
        Map.of("legallyMandated", true, "timebound", true)
    );
    
    private final String displayName;
    private final String description;
    private final ActionCategory category;
    private final ActionSeverity severity;
    private final List<String> requiredApprovals;
    private final Map<String, Object> properties;
    
    /**
     * Categories for organizing enforcement actions
     */
    public enum ActionCategory {
        ACCOUNT_CONTROL,
        TRANSACTION_CONTROL,
        FINANCIAL,
        REGULATORY,
        ADMINISTRATIVE,
        OPERATIONAL,
        MERCHANT,
        ACCESS_CONTROL,
        INVESTIGATION,
        MONITORING
    }
    
    /**
     * Severity levels for enforcement actions
     */
    public enum ActionSeverity {
        LOW(0.2),
        MEDIUM(0.5),
        HIGH(0.8),
        SEVERE(1.0);
        
        private final double severityScore;
        
        ActionSeverity(double severityScore) {
            this.severityScore = severityScore;
        }
        
        public double getSeverityScore() {
            return severityScore;
        }
    }
    
    /**
     * Check if action requires immediate implementation
     */
    public boolean requiresImmediateAction() {
        return severity == ActionSeverity.SEVERE ||
               this == ASSET_FREEZING ||
               this == FUND_SEIZURE ||
               this == LAW_ENFORCEMENT_REFERRAL ||
               this == CEASE_AND_DESIST;
    }
    
    /**
     * Check if action is reversible
     */
    public boolean isReversible() {
        Object reversible = properties.get("reversible");
        return reversible instanceof Boolean ? (Boolean) reversible : true;
    }
    
    /**
     * Check if action requires customer notification
     */
    public boolean requiresCustomerNotification() {
        return requiredApprovals.contains("CUSTOMER_NOTIFICATION") ||
               requiredApprovals.contains("MERCHANT_NOTIFICATION");
    }
    
    /**
     * Check if action has immediate effect
     */
    public boolean hasImmediateEffect() {
        Object immediate = properties.get("immediateEffect");
        return immediate instanceof Boolean ? (Boolean) immediate : false;
    }
    
    /**
     * Get estimated implementation time in minutes
     */
    public int getEstimatedImplementationTimeMinutes() {
        switch (category) {
            case REGULATORY:
                return 60; // Regulatory filings take time
            case FINANCIAL:
                return 30; // Financial actions need approval
            case OPERATIONAL:
                return 15; // Technical implementations
            case ADMINISTRATIVE:
                return 45; // Documentation and review
            default:
                return 20; // General implementation time
        }
    }
}