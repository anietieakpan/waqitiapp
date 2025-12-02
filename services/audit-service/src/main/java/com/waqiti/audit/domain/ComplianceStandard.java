package com.waqiti.audit.domain;

/**
 * Enumeration of compliance standards supported by the audit system
 */
public enum ComplianceStandard {
    /**
     * Sarbanes-Oxley Act - Financial reporting and corporate governance
     */
    SOX_COMPLIANCE("SOX", "Sarbanes-Oxley Act Compliance", "Financial reporting and internal controls"),

    /**
     * Payment Card Industry Data Security Standard
     */
    PCI_DSS_COMPLIANCE("PCI DSS", "Payment Card Industry Data Security Standard", "Cardholder data protection"),

    /**
     * General Data Protection Regulation
     */
    GDPR_COMPLIANCE("GDPR", "General Data Protection Regulation", "EU data privacy and protection"),

    /**
     * Bank Secrecy Act / Anti-Money Laundering
     */
    BSA_COMPLIANCE("BSA/AML", "Bank Secrecy Act Compliance", "Anti-money laundering and financial crimes prevention"),

    /**
     * Federal Financial Institutions Examination Council
     */
    FFIEC_COMPLIANCE("FFIEC", "FFIEC Compliance", "IT examination and cybersecurity requirements"),

    /**
     * ISO/IEC 27001 Information Security Management
     */
    ISO_27001_COMPLIANCE("ISO 27001", "ISO/IEC 27001 Compliance", "Information security management system"),

    /**
     * NIST Cybersecurity Framework
     */
    NIST_CYBERSECURITY("NIST CSF", "NIST Cybersecurity Framework", "Risk-based cybersecurity guidance"),

    /**
     * SOC 2 Type II - Service Organization Controls
     */
    SOC2_TYPE_II("SOC 2", "SOC 2 Type II Compliance", "Security, availability, and confidentiality"),

    /**
     * HIPAA - Health Insurance Portability and Accountability Act
     */
    HIPAA_COMPLIANCE("HIPAA", "HIPAA Compliance", "Healthcare data privacy and security"),

    /**
     * California Consumer Privacy Act
     */
    CCPA_COMPLIANCE("CCPA", "California Consumer Privacy Act", "California data privacy requirements"),

    /**
     * GLBA - Gramm-Leach-Bliley Act
     */
    GLBA_COMPLIANCE("GLBA", "Gramm-Leach-Bliley Act", "Financial institution data privacy");

    private final String code;
    private final String displayName;
    private final String description;

    ComplianceStandard(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get compliance standard by code
     */
    public static ComplianceStandard fromCode(String code) {
        for (ComplianceStandard standard : values()) {
            if (standard.code.equalsIgnoreCase(code)) {
                return standard;
            }
        }
        return null;
    }
}
