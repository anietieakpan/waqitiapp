package com.waqiti.compliance.contracts.dto;

/**
 * Compliance frameworks supported by the platform
 */
public enum ComplianceFramework {
    /**
     * Payment Card Industry Data Security Standard
     */
    PCI_DSS("PCI-DSS", "4.0"),

    /**
     * Service Organization Control 2
     */
    SOC2("SOC2", "Type II"),

    /**
     * General Data Protection Regulation
     */
    GDPR("GDPR", "2016/679"),

    /**
     * California Consumer Privacy Act
     */
    CCPA("CCPA", "2018"),

    /**
     * Health Insurance Portability and Accountability Act
     */
    HIPAA("HIPAA", "1996"),

    /**
     * ISO/IEC 27001 Information Security
     */
    ISO_27001("ISO-27001", "2013"),

    /**
     * NIST Cybersecurity Framework
     */
    NIST_CSF("NIST-CSF", "1.1"),

    /**
     * Anti-Money Laundering regulations
     */
    AML("AML", "FinCEN"),

    /**
     * Know Your Customer regulations
     */
    KYC("KYC", "FATF"),

    /**
     * Open Web Application Security Project
     */
    OWASP("OWASP", "Top 10"),

    /**
     * Center for Internet Security Benchmarks
     */
    CIS("CIS", "Benchmarks"),

    /**
     * Custom internal compliance framework
     */
    CUSTOM("CUSTOM", "1.0");

    private final String code;
    private final String version;

    ComplianceFramework(String code, String version) {
        this.code = code;
        this.version = version;
    }

    public String getCode() {
        return code;
    }

    public String getVersion() {
        return version;
    }

    public String getFullName() {
        return code + " " + version;
    }
}
