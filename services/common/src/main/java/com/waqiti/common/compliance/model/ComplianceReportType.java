package com.waqiti.common.compliance.model;

/**
 * Types of compliance reports that can be generated
 */
public enum ComplianceReportType {
    
    // AML Reports
    SUSPICIOUS_ACTIVITY_REPORT("SAR", "Suspicious Activity Report", "AML"),
    CURRENCY_TRANSACTION_REPORT("CTR", "Currency Transaction Report", "AML"),
    LARGE_CASH_TRANSACTION("LCT", "Large Cash Transaction Report", "AML"),
    WIRE_TRANSFER_REPORT("WTR", "Wire Transfer Report", "AML"),
    
    // KYC Reports
    CUSTOMER_DUE_DILIGENCE("CDD", "Customer Due Diligence Report", "KYC"),
    ENHANCED_DUE_DILIGENCE("EDD", "Enhanced Due Diligence Report", "KYC"),
    ONGOING_MONITORING("OM", "Ongoing Monitoring Report", "KYC"),
    RISK_ASSESSMENT("RA", "Risk Assessment Report", "KYC"),
    
    // PCI DSS Reports
    SECURITY_SCANNING("SS", "Security Scanning Report", "PCI_DSS"),
    VULNERABILITY_ASSESSMENT("VA", "Vulnerability Assessment", "PCI_DSS"),
    PENETRATION_TESTING("PT", "Penetration Testing Report", "PCI_DSS"),
    COMPLIANCE_VALIDATION("CV", "Compliance Validation Report", "PCI_DSS"),
    
    // GDPR Reports
    DATA_PROCESSING_ACTIVITY("DPA", "Data Processing Activity Report", "GDPR"),
    PRIVACY_IMPACT_ASSESSMENT("PIA", "Privacy Impact Assessment", "GDPR"),
    DATA_BREACH_NOTIFICATION("DBN", "Data Breach Notification", "GDPR"),
    CONSENT_MANAGEMENT("CM", "Consent Management Report", "GDPR"),
    
    // SOX Reports
    INTERNAL_CONTROLS("IC", "Internal Controls Report", "SOX"),
    FINANCIAL_DISCLOSURE("FD", "Financial Disclosure Report", "SOX"),
    EXECUTIVE_CERTIFICATION("EC", "Executive Certification", "SOX"),
    
    // Basel III Reports
    CAPITAL_ADEQUACY("CA", "Capital Adequacy Report", "BASEL_III"),
    LIQUIDITY_COVERAGE("LC", "Liquidity Coverage Ratio", "BASEL_III"),
    NET_STABLE_FUNDING("NSF", "Net Stable Funding Ratio", "BASEL_III"),
    LEVERAGE_RATIO("LR", "Leverage Ratio Report", "BASEL_III"),
    
    // Custom Reports
    CUSTOM_COMPLIANCE("CC", "Custom Compliance Report", "CUSTOM"),
    REGULATORY_CHANGE("RC", "Regulatory Change Impact", "CUSTOM"),
    AUDIT_TRAIL("AT", "Audit Trail Report", "CUSTOM"),

    // Daily Reports
    DAILY_TRANSACTION_SUMMARY("DTS", "Daily Transaction Summary", "DAILY"),
    AML_SCREENING_SUMMARY("AML_SCREEN", "AML Screening Summary", "DAILY"),
    FRAUD_DETECTION_SUMMARY("FRAUD_SUM", "Fraud Detection Summary", "DAILY"),
    AUDIT_LOG_SUMMARY("AUDIT_SUM", "Audit Log Summary", "DAILY"),

    // Additional Compliance Reports
    PCI_DSS_COMPLIANCE("PCI_DSS", "PCI DSS Compliance Report", "PCI_DSS"),
    SOX_COMPLIANCE("SOX", "SOX Compliance Report", "SOX"),
    AML_BSA_MONTHLY("AML_BSA", "AML/BSA Monthly Report", "AML"),
    KYC_COMPLIANCE("KYC", "KYC Compliance Report", "KYC"),
    SOC2_TYPE_II("SOC2", "SOC 2 Type II Report", "SOX"),
    GDPR_COMPLIANCE("GDPR", "GDPR Compliance Report", "GDPR"),
    SAR_FILING("SAR_FILING", "SAR Filing", "AML"),
    CTR_FILING("CTR_FILING", "CTR Filing", "AML");
    
    private final String code;
    private final String displayName;
    private final String category;
    
    ComplianceReportType(String code, String displayName, String category) {
        this.code = code;
        this.displayName = displayName;
        this.category = category;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getCategory() {
        return category;
    }
    
    /**
     * Get report type by code
     */
    public static ComplianceReportType fromCode(String code) {
        for (ComplianceReportType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown compliance report type code: " + code);
    }
    
    /**
     * Check if report type requires enhanced security
     */
    public boolean requiresEnhancedSecurity() {
        return this == SUSPICIOUS_ACTIVITY_REPORT || 
               this == ENHANCED_DUE_DILIGENCE ||
               this == DATA_BREACH_NOTIFICATION ||
               this == PENETRATION_TESTING;
    }
    
    /**
     * Check if report type is regulatory required
     */
    public boolean isRegulatoryRequired() {
        return !category.equals("CUSTOM");
    }
}