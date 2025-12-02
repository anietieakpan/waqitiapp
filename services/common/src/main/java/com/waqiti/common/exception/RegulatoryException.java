package com.waqiti.common.exception;

/**
 * Exception indicating a regulatory compliance violation.
 * Used for AML, KYC, and other regulatory requirement failures.
 */
public class RegulatoryException extends RuntimeException {

    private final String violationType;
    private final String regulatoryBody;
    private final boolean requiresReporting;

    public RegulatoryException(String message) {
        super(message);
        this.violationType = "GENERAL_COMPLIANCE";
        this.regulatoryBody = "UNKNOWN";
        this.requiresReporting = true;
    }

    public RegulatoryException(String message, Throwable cause) {
        super(message, cause);
        this.violationType = "GENERAL_COMPLIANCE";
        this.regulatoryBody = "UNKNOWN";
        this.requiresReporting = true;
    }

    public RegulatoryException(String message, String violationType, String regulatoryBody) {
        super(message);
        this.violationType = violationType;
        this.regulatoryBody = regulatoryBody;
        this.requiresReporting = true;
    }

    public String getViolationType() {
        return violationType;
    }

    public String getRegulatoryBody() {
        return regulatoryBody;
    }

    public boolean requiresReporting() {
        return requiresReporting;
    }
}
