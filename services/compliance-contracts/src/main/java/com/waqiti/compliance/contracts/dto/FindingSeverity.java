package com.waqiti.compliance.contracts.dto;

/**
 * Severity levels for compliance findings
 */
public enum FindingSeverity {
    /**
     * Critical - immediate action required, production-blocking
     */
    CRITICAL(10),

    /**
     * High - significant risk, requires urgent attention
     */
    HIGH(7),

    /**
     * Medium - moderate risk, should be addressed soon
     */
    MEDIUM(5),

    /**
     * Low - minor risk, can be addressed in normal cycle
     */
    LOW(3),

    /**
     * Informational - no immediate risk, awareness only
     */
    INFO(1);

    private final int numericValue;

    FindingSeverity(int numericValue) {
        this.numericValue = numericValue;
    }

    public int getNumericValue() {
        return numericValue;
    }

    /**
     * Convert CVSS score to severity
     */
    public static FindingSeverity fromCvssScore(double cvssScore) {
        if (cvssScore >= 9.0) return CRITICAL;
        if (cvssScore >= 7.0) return HIGH;
        if (cvssScore >= 4.0) return MEDIUM;
        if (cvssScore >= 0.1) return LOW;
        return INFO;
    }
}
