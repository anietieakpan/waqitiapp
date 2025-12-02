package com.waqiti.common.compliance;

/**
 * Enumeration of compliance report types supported by the Waqiti platform
 * 
 * Each report type corresponds to specific regulatory requirements and
 * has associated filing procedures, deadlines, and content requirements.
 */
public enum ComplianceReportType {

    /**
     * Suspicious Activity Report - Filed when suspicious transactions are detected
     */
    SAR("Suspicious Activity Report", "SAR", "Reports suspicious financial activities to regulatory authorities", 30, true),

    /**
     * Currency Transaction Report - Filed for cash transactions over threshold
     */
    CTR("Currency Transaction Report", "CTR", "Reports currency transactions exceeding regulatory thresholds", 15, true),

    /**
     * Large Cash Transaction Report - Filed for significant cash transactions
     */
    LCTR("Large Cash Transaction Report", "LCTR", "Reports large cash transactions for AML compliance", 15, true),

    /**
     * Anti-Money Laundering Compliance Report
     */
    AML_COMPLIANCE("AML Compliance Report", "AML", "Comprehensive anti-money laundering compliance assessment", 90, false),

    /**
     * Know Your Customer Compliance Report
     */
    KYC_COMPLIANCE("KYC Compliance Report", "KYC", "Customer due diligence and identity verification compliance", 60, false),

    /**
     * Bank Secrecy Act Compliance Report
     */
    BSA_COMPLIANCE("BSA Compliance Report", "BSA", "Bank Secrecy Act compliance assessment and reporting", 90, false),

    /**
     * OFAC Sanctions Screening Report
     */
    OFAC_SCREENING("OFAC Screening Report", "OFAC", "Office of Foreign Assets Control sanctions compliance report", 30, true),

    /**
     * Patriot Act Compliance Report
     */
    PATRIOT_ACT("USA PATRIOT Act Report", "PATRIOT", "USA PATRIOT Act Section 314(a) compliance report", 14, true),

    /**
     * Cross-Border Transaction Report
     */
    CROSS_BORDER("Cross-Border Transaction Report", "CBT", "International fund transfer reporting", 30, false),

    /**
     * High-Risk Customer Review Report
     */
    HIGH_RISK_CUSTOMER("High-Risk Customer Report", "HRC", "Enhanced due diligence for high-risk customers", 45, false),

    /**
     * Transaction Monitoring Exception Report
     */
    MONITORING_EXCEPTION("Transaction Monitoring Exception Report", "TME", "Exceptions and alerts from transaction monitoring systems", 7, false),

    /**
     * Regulatory Examination Response
     */
    EXAM_RESPONSE("Regulatory Examination Response", "EXAM", "Response to regulatory examination findings", 30, true),

    /**
     * Internal Audit Compliance Report
     */
    INTERNAL_AUDIT("Internal Audit Report", "AUDIT", "Internal compliance audit findings and recommendations", 60, false),

    /**
     * Risk Assessment Report
     */
    RISK_ASSESSMENT("Risk Assessment Report", "RISK", "Comprehensive risk assessment of compliance posture", 90, false),

    /**
     * Regulatory Change Impact Assessment
     */
    REG_CHANGE_IMPACT("Regulatory Change Impact Report", "RCI", "Assessment of regulatory changes on business operations", 45, false),

    /**
     * Third-Party Risk Assessment Report
     */
    THIRD_PARTY_RISK("Third-Party Risk Report", "TPR", "Risk assessment of third-party vendors and partners", 60, false),

    /**
     * Data Privacy Compliance Report (GDPR, CCPA, etc.)
     */
    DATA_PRIVACY("Data Privacy Compliance Report", "DPR", "Data privacy and protection compliance assessment", 30, false),

    /**
     * Cyber Security Incident Report
     */
    CYBERSECURITY_INCIDENT("Cybersecurity Incident Report", "CSI", "Security incident reporting to regulatory authorities", 1, true),

    /**
     * Model Risk Management Report
     */
    MODEL_RISK("Model Risk Management Report", "MRM", "Risk assessment of predictive models and algorithms", 90, false),

    /**
     * Consumer Complaint Analysis Report
     */
    CONSUMER_COMPLAINT("Consumer Complaint Report", "CCR", "Analysis and trending of consumer complaints", 30, false),

    /**
     * Operational Risk Assessment Report
     */
    OPERATIONAL_RISK("Operational Risk Report", "OPR", "Assessment of operational risks and control effectiveness", 90, false),

    /**
     * Fair Lending Compliance Report
     */
    FAIR_LENDING("Fair Lending Compliance Report", "FLR", "Fair lending practices and discrimination testing", 60, false),

    /**
     * Capital Adequacy Report
     */
    CAPITAL_ADEQUACY("Capital Adequacy Report", "CAR", "Capital adequacy assessment and regulatory capital reporting", 30, true),

    /**
     * Stress Testing Report
     */
    STRESS_TESTING("Stress Testing Report", "STR", "Stress testing results and capital planning", 90, true),

    /**
     * Custom compliance report type
     */
    CUSTOM("Custom Compliance Report", "CUSTOM", "Custom report type for specific regulatory requirements", 30, false),

    /**
     * Daily transaction summary report
     */
    DAILY_TRANSACTION_SUMMARY("Daily Transaction Summary", "DTS", "Summary of daily transaction activity", 1, false),

    /**
     * Daily AML screening summary
     */
    AML_SCREENING_SUMMARY("AML Screening Summary", "AML_SCREEN", "Daily AML screening activity summary", 1, false),

    /**
     * Daily fraud detection summary
     */
    FRAUD_DETECTION_SUMMARY("Fraud Detection Summary", "FRAUD_SUM", "Daily fraud detection summary report", 1, false),

    /**
     * Daily audit log summary
     */
    AUDIT_LOG_SUMMARY("Audit Log Summary", "AUDIT_SUM", "Daily audit activity summary", 1, false),

    /**
     * PCI DSS compliance report
     */
    PCI_DSS_COMPLIANCE("PCI DSS Compliance Report", "PCI_DSS", "Payment Card Industry Data Security Standard compliance", 30, false),

    /**
     * SOX compliance report
     */
    SOX_COMPLIANCE("SOX Compliance Report", "SOX", "Sarbanes-Oxley compliance reporting", 30, false),

    /**
     * Monthly AML/BSA report
     */
    AML_BSA_MONTHLY("AML/BSA Monthly Report", "AML_BSA", "Monthly Anti-Money Laundering and Bank Secrecy Act report", 30, false),

    /**
     * SOC 2 Type II compliance report
     */
    SOC2_TYPE_II("SOC 2 Type II Report", "SOC2", "Service Organization Control 2 Type II audit report", 90, false),

    /**
     * GDPR compliance report
     */
    GDPR_COMPLIANCE("GDPR Compliance Report", "GDPR", "General Data Protection Regulation compliance report", 30, false),

    /**
     * SAR Filing - Suspicious Activity Report filing
     */
    SAR_FILING("SAR Filing", "SAR_FILING", "Suspicious Activity Report regulatory filing", 30, true),

    /**
     * CTR Filing - Currency Transaction Report filing
     */
    CTR_FILING("CTR Filing", "CTR_FILING", "Currency Transaction Report regulatory filing", 15, true),

    /**
     * HIPAA compliance report
     */
    HIPAA_COMPLIANCE("HIPAA Compliance Report", "HIPAA", "Health Insurance Portability and Accountability Act compliance", 30, false),

    /**
     * FFIEC compliance report
     */
    FFIEC_COMPLIANCE("FFIEC Compliance Report", "FFIEC", "Federal Financial Institutions Examination Council compliance", 30, false),

    /**
     * Sanctions screening report
     */
    SANCTIONS_SCREENING("Sanctions Screening Report", "SANCTIONS", "Sanctions and watchlist screening compliance report", 30, false),

    /**
     * Transaction monitoring report
     */
    TRANSACTION_MONITORING("Transaction Monitoring Report", "TXN_MON", "Transaction monitoring and surveillance report", 7, false),

    /**
     * Customer due diligence report
     */
    CUSTOMER_DUE_DILIGENCE("Customer Due Diligence Report", "CDD", "Customer due diligence and enhanced due diligence report", 30, false),

    /**
     * PEP screening report
     */
    PEP_SCREENING("PEP Screening Report", "PEP", "Politically Exposed Persons screening report", 30, false),

    /**
     * Adverse media screening report
     */
    ADVERSE_MEDIA_SCREENING("Adverse Media Screening Report", "ADVERSE_MEDIA", "Adverse media and negative news screening report", 30, false),

    /**
     * BSA/AML combined report
     */
    BSA_AML_REPORT("BSA/AML Report", "BSA_AML", "Combined Bank Secrecy Act and Anti-Money Laundering report", 90, false),

    /**
     * Regulatory filing report
     */
    REGULATORY_FILING("Regulatory Filing Report", "REG_FILING", "General regulatory filing and submission report", 30, true),

    /**
     * Audit report
     */
    AUDIT_REPORT("Audit Report", "AUDIT_RPT", "Compliance audit and examination report", 60, false),

    /**
     * Policy review report
     */
    POLICY_REVIEW("Policy Review Report", "POLICY_REV", "Compliance policy review and update report", 90, false),

    /**
     * Training compliance report
     */
    TRAINING_COMPLIANCE("Training Compliance Report", "TRAINING", "Employee training and compliance education report", 30, false),

    /**
     * Incident report
     */
    INCIDENT_REPORT("Incident Report", "INCIDENT", "Compliance incident and breach reporting", 1, true),

    /**
     * Custom compliance report
     */
    CUSTOM_COMPLIANCE_REPORT("Custom Compliance Report", "CUSTOM_RPT", "Custom compliance report type", 30, false);

    private final String displayName;
    private final String code;
    private final String description;
    private final int standardFilingDays;
    private final boolean mandatoryFiling;

    ComplianceReportType(String displayName, String code, String description, 
                        int standardFilingDays, boolean mandatoryFiling) {
        this.displayName = displayName;
        this.code = code;
        this.description = description;
        this.standardFilingDays = standardFilingDays;
        this.mandatoryFiling = mandatoryFiling;
    }

    public String getDisplayName() { return displayName; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public int getStandardFilingDays() { return standardFilingDays; }
    public boolean isMandatoryFiling() { return mandatoryFiling; }

    /**
     * Get report type by code
     */
    public static ComplianceReportType fromCode(String code) {
        if (code == null) return null;
        
        for (ComplianceReportType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this report type is time-sensitive
     */
    public boolean isTimeSensitive() {
        return standardFilingDays <= 7 || mandatoryFiling;
    }

    /**
     * Get priority level based on report type characteristics
     */
    public ComplianceReport.ReportPriority getDefaultPriority() {
        if (this == CYBERSECURITY_INCIDENT) return ComplianceReport.ReportPriority.CRITICAL;
        if (mandatoryFiling && standardFilingDays <= 15) return ComplianceReport.ReportPriority.HIGH;
        if (mandatoryFiling) return ComplianceReport.ReportPriority.MEDIUM;
        return ComplianceReport.ReportPriority.LOW;
    }

    /**
     * Get regulatory authority typically associated with this report type
     */
    public String getTypicalRegulatoryAuthority() {
        switch (this) {
            case SAR:
            case CTR:
            case BSA_COMPLIANCE:
                return "FinCEN";
            case OFAC_SCREENING:
                return "OFAC";
            case PATRIOT_ACT:
                return "Treasury";
            case FAIR_LENDING:
                return "CFPB";
            case CAPITAL_ADEQUACY:
            case STRESS_TESTING:
                return "Federal Reserve";
            case CYBERSECURITY_INCIDENT:
                return "CISA";
            case DATA_PRIVACY:
                return "FTC";
            default:
                return "Multiple";
        }
    }
}