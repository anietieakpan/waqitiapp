package com.waqiti.compliance.contracts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of an individual compliance requirement check
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementResultDTO {

    /**
     * Requirement identifier
     */
    private String requirementId;

    /**
     * Requirement description
     */
    private String description;

    /**
     * Whether this requirement passed
     */
    private Boolean passed;

    /**
     * Findings specific to this requirement
     */
    private List<String> findings;

    /**
     * Error message if requirement check failed
     */
    private String error;

    /**
     * Additional metadata
     */
    private String metadata;

    /**
     * Control ID (if applicable)
     */
    private String controlId;

    /**
     * Evidence collected for this requirement
     */
    private List<String> evidence;
}
