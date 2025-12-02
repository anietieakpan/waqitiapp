package com.waqiti.gdpr.domain;

/**
 * Severity levels for data breaches based on impact assessment
 */
public enum BreachSeverity {
    /**
     * Low severity - minimal risk to data subjects
     * - Small number of records affected
     * - Low-sensitivity data
     * - Minimal potential harm
     */
    LOW,

    /**
     * Medium severity - moderate risk to data subjects
     * - Moderate number of records affected
     * - Moderately sensitive data
     * - Some potential for harm
     */
    MEDIUM,

    /**
     * High severity - significant risk to data subjects
     * - Large number of records affected
     * - Sensitive personal data compromised
     * - Significant potential for harm
     */
    HIGH,

    /**
     * Critical severity - severe risk to data subjects
     * - Very large number of records affected
     * - Special category data (Article 9) or criminal convictions data
     * - Severe potential harm (identity theft, financial loss, etc.)
     * - Requires immediate action
     */
    CRITICAL
}
