package com.waqiti.compliance.contracts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for compliance validation results
 * Shared contract between security-service and compliance-service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceValidationResponse {

    /**
     * Unique identifier for this validation result
     */
    private String validationId;

    /**
     * Original request ID (for correlation)
     */
    private String requestId;

    /**
     * Overall compliance status
     */
    private ComplianceStatus overallStatus;

    /**
     * Compliance score (0-100)
     */
    private Double complianceScore;

    /**
     * Individual check results
     */
    private List<ComplianceCheckResultDTO> checkResults;

    /**
     * Findings from validation
     */
    private List<ComplianceFindingDTO> findings;

    /**
     * Validation started timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startedAt;

    /**
     * Validation completed timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime completedAt;

    /**
     * Duration in milliseconds
     */
    private Long durationMs;

    /**
     * Report ID (if report was generated)
     */
    private String reportId;

    /**
     * Whether all required checks passed
     */
    private Boolean passed;

    /**
     * Critical issues that require immediate attention
     */
    private List<String> criticalIssues;

    /**
     * Recommendations for remediation
     */
    private List<String> recommendations;

    /**
     * Metadata about the validation execution
     */
    private Map<String, Object> metadata;

    /**
     * Next scheduled validation date (if recurring)
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime nextValidationDate;

    /**
     * Validation performed by (service/user)
     */
    private String performedBy;

    /**
     * Error message (if validation failed)
     */
    private String errorMessage;

    /**
     * Stack trace (if validation failed - only in non-prod)
     */
    private String errorDetails;
}
