package com.waqiti.common.compliance;

import java.util.List;
import java.util.ArrayList;

/**
 * Exception thrown when compliance report validation fails
 *
 * This exception indicates that a compliance report does not meet
 * the required validation criteria for regulatory submission.
 */
public class ComplianceValidationException extends RuntimeException {

    private final List<String> validationErrors;
    private final List<String> validationWarnings;
    private final String reportId;
    private final ComplianceReportType reportType;

    public ComplianceValidationException(String message) {
        super(message);
        this.validationErrors = new ArrayList<>();
        this.validationWarnings = new ArrayList<>();
        this.reportId = null;
        this.reportType = null;
    }

    public ComplianceValidationException(String message, Throwable cause) {
        super(message, cause);
        this.validationErrors = new ArrayList<>();
        this.validationWarnings = new ArrayList<>();
        this.reportId = null;
        this.reportType = null;
    }

    public ComplianceValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors != null ? new ArrayList<>(validationErrors) : new ArrayList<>();
        this.validationWarnings = new ArrayList<>();
        this.reportId = null;
        this.reportType = null;
    }

    public ComplianceValidationException(String message, List<String> validationErrors, List<String> validationWarnings) {
        super(message);
        this.validationErrors = validationErrors != null ? new ArrayList<>(validationErrors) : new ArrayList<>();
        this.validationWarnings = validationWarnings != null ? new ArrayList<>(validationWarnings) : new ArrayList<>();
        this.reportId = null;
        this.reportType = null;
    }

    public ComplianceValidationException(String reportId, ComplianceReportType reportType,
                                        List<String> validationErrors, List<String> validationWarnings) {
        super(String.format("Validation failed for %s report %s: %d errors, %d warnings",
            reportType, reportId,
            validationErrors != null ? validationErrors.size() : 0,
            validationWarnings != null ? validationWarnings.size() : 0));
        this.reportId = reportId;
        this.reportType = reportType;
        this.validationErrors = validationErrors != null ? new ArrayList<>(validationErrors) : new ArrayList<>();
        this.validationWarnings = validationWarnings != null ? new ArrayList<>(validationWarnings) : new ArrayList<>();
    }

    public List<String> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }

    public List<String> getValidationWarnings() {
        return new ArrayList<>(validationWarnings);
    }

    public String getReportId() {
        return reportId;
    }

    public ComplianceReportType getReportType() {
        return reportType;
    }

    public boolean hasErrors() {
        return !validationErrors.isEmpty();
    }

    public boolean hasWarnings() {
        return !validationWarnings.isEmpty();
    }

    public int getErrorCount() {
        return validationErrors.size();
    }

    public int getWarningCount() {
        return validationWarnings.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());

        if (!validationErrors.isEmpty()) {
            sb.append("\nValidation Errors:");
            for (int i = 0; i < validationErrors.size(); i++) {
                sb.append("\n  ").append(i + 1).append(". ").append(validationErrors.get(i));
            }
        }

        if (!validationWarnings.isEmpty()) {
            sb.append("\nValidation Warnings:");
            for (int i = 0; i < validationWarnings.size(); i++) {
                sb.append("\n  ").append(i + 1).append(". ").append(validationWarnings.get(i));
            }
        }

        return sb.toString();
    }
}
