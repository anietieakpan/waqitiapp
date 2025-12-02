package com.waqiti.account.service;

/**
 * Compliance check result
 */
public record ComplianceCheckResult(
    boolean passed,
    String reason,
    boolean requiresAdditionalVerification
) {
    public boolean isPassed() {
        return passed;
    }
    
    public String getReason() {
        return reason;
    }
    
    public boolean requiresAdditionalVerification() {
        return requiresAdditionalVerification;
    }
}
