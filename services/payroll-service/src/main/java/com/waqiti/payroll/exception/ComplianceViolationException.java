package com.waqiti.payroll.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * Compliance Violation Exception
 *
 * Thrown when payroll processing violates regulatory compliance requirements.
 * Non-retryable - requires manual intervention.
 *
 * CRITICAL: Triggers compliance alerts and audit logging
 */
public class ComplianceViolationException extends RuntimeException {

    private final String violationType;
    private final String companyId;
    private final List<String> violations;

    public ComplianceViolationException(String message) {
        super(message);
        this.violationType = "UNKNOWN";
        this.companyId = null;
        this.violations = new ArrayList<>();
        this.violations.add(message);
    }

    public ComplianceViolationException(String message, Throwable cause) {
        super(message, cause);
        this.violationType = "UNKNOWN";
        this.companyId = null;
        this.violations = new ArrayList<>();
        this.violations.add(message);
    }

    public ComplianceViolationException(String violationType, String companyId, String message) {
        super(message);
        this.violationType = violationType;
        this.companyId = companyId;
        this.violations = new ArrayList<>();
        this.violations.add(message);
    }

    public ComplianceViolationException(String violationType, String companyId, List<String> violations) {
        super(String.format("%s compliance violation for company %s: %d violations detected",
            violationType, companyId, violations.size()));
        this.violationType = violationType;
        this.companyId = companyId;
        this.violations = new ArrayList<>(violations);
    }

    public String getViolationType() {
        return violationType;
    }

    public String getCompanyId() {
        return companyId;
    }

    public List<String> getViolations() {
        return new ArrayList<>(violations);
    }

    public int getViolationCount() {
        return violations.size();
    }
}
