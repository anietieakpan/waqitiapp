package com.waqiti.common.security.awareness.model;

/**
 * Assessment Type Enum
 *
 * PCI DSS REQ 12.6.3 - Types of quarterly security assessments.
 */
public enum AssessmentType {
    KNOWLEDGE_CHECK,        // General security knowledge assessment
    VULNERABILITY_AWARENESS, // Current vulnerability landscape awareness
    THREAT_LANDSCAPE,       // Current threat landscape awareness (PCI DSS REQ 12.6.3.1)
    COMPLIANCE_REVIEW       // Compliance policy and procedure review
}