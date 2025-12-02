package com.waqiti.common.exception;

/**
 * Exception indicating an insurance regulatory compliance violation.
 * Used for insurance-specific regulatory requirement failures.
 */
public class InsuranceComplianceException extends RuntimeException {

    private final String violationType;
    private final boolean requiresRegulatoryFiling;

    public InsuranceComplianceException(String message) {
        super(message);
        this.violationType = "INSURANCE_COMPLIANCE_VIOLATION";
        this.requiresRegulatoryFiling = true;
    }

    public InsuranceComplianceException(String message, Throwable cause) {
        super(message, cause);
        this.violationType = "INSURANCE_COMPLIANCE_VIOLATION";
        this.requiresRegulatoryFiling = true;
    }

    public InsuranceComplianceException(String message, String violationType) {
        super(message);
        this.violationType = violationType;
        this.requiresRegulatoryFiling = true;
    }

    public String getViolationType() {
        return violationType;
    }

    public boolean requiresRegulatoryFiling() {
        return requiresRegulatoryFiling;
    }
}
