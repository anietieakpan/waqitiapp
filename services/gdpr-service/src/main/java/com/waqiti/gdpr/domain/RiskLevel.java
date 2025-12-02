package com.waqiti.gdpr.domain;

/**
 * Risk level classification for privacy impact assessments
 */
public enum RiskLevel {
    /**
     * Low risk - unlikely to result in risk to rights and freedoms
     */
    LOW,

    /**
     * Medium risk - some risk to rights and freedoms
     */
    MEDIUM,

    /**
     * High risk - likely to result in risk to rights and freedoms
     * Requires user notification under Article 34
     */
    HIGH,

    /**
     * Critical risk - likely to result in high risk to rights and freedoms
     * Requires immediate action and comprehensive notification
     * May require DPIA under Article 35
     */
    CRITICAL
}
