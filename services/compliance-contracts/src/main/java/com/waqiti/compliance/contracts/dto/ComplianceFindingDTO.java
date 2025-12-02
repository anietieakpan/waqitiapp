package com.waqiti.compliance.contracts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Individual compliance finding/issue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceFindingDTO {

    /**
     * Finding unique identifier
     */
    private String findingId;

    /**
     * Requirement ID that failed
     */
    private String requirementId;

    /**
     * Framework this finding relates to
     */
    private String framework;

    /**
     * Finding title/summary
     */
    private String title;

    /**
     * Detailed description of the finding
     */
    private String description;

    /**
     * Severity level
     */
    private FindingSeverity severity;

    /**
     * Category of finding
     */
    private FindingCategory category;

    /**
     * Current status
     */
    private FindingStatus status;

    /**
     * Affected resource/component
     */
    private String affectedResource;

    /**
     * Location/path where issue was found
     */
    private String location;

    /**
     * Remediation recommendation
     */
    private String remediation;

    /**
     * Estimated effort to fix
     */
    private String estimatedEffort;

    /**
     * Risk score (0-10)
     */
    private Double riskScore;

    /**
     * CVSS score (if vulnerability)
     */
    private Double cvssScore;

    /**
     * CWE ID (if security vulnerability)
     */
    private String cweId;

    /**
     * CVE ID (if known vulnerability)
     */
    private String cveId;

    /**
     * When this finding was discovered
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime discoveredAt;

    /**
     * Due date for remediation
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dueDate;

    /**
     * When remediation was completed
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime resolvedAt;

    /**
     * Who is assigned to fix this
     */
    private String assignedTo;

    /**
     * Tags for categorization
     */
    private java.util.List<String> tags;

    /**
     * Reference links/documentation
     */
    private java.util.List<String> references;
}
