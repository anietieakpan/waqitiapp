package com.waqiti.compliance.contracts.dto;

/**
 * Types of compliance validation
 */
public enum ComplianceValidationType {
    /**
     * Comprehensive validation across all frameworks
     */
    COMPREHENSIVE,

    /**
     * PCI-DSS specific validation
     */
    PCI_DSS,

    /**
     * SOC 2 compliance validation
     */
    SOC2,

    /**
     * GDPR compliance validation
     */
    GDPR,

    /**
     * KYC/AML compliance validation
     */
    KYC_AML,

    /**
     * Security hardening validation
     */
    SECURITY_HARDENING,

    /**
     * Vulnerability scan
     */
    VULNERABILITY_SCAN,

    /**
     * Data encryption validation
     */
    DATA_ENCRYPTION,

    /**
     * Access control validation
     */
    ACCESS_CONTROLS,

    /**
     * Custom compliance check
     */
    CUSTOM
}
