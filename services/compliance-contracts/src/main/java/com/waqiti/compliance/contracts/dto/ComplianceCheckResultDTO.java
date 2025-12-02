package com.waqiti.compliance.contracts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of an individual compliance check
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckResultDTO {

    /**
     * Check identifier
     */
    private String checkId;

    /**
     * Check name/description
     */
    private String checkName;

    /**
     * Framework this check belongs to
     */
    private String framework;

    /**
     * Framework version
     */
    private String frameworkVersion;

    /**
     * Type of check performed
     */
    private String checkType;

    /**
     * Whether the check passed
     */
    private Boolean passed;

    /**
     * Check score (0-100)
     */
    private Double score;

    /**
     * Individual requirements checked
     */
    private List<RequirementResultDTO> requirements;

    /**
     * Findings from this check
     */
    private List<ComplianceFindingDTO> findings;

    /**
     * Check started timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startedAt;

    /**
     * Check completed timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime completedAt;

    /**
     * Duration in milliseconds
     */
    private Long durationMs;

    /**
     * Error message if check failed
     */
    private String errorMessage;

    /**
     * Additional metadata
     */
    private String metadata;
}
