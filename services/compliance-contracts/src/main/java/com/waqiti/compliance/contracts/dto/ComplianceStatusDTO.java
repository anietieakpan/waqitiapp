package com.waqiti.compliance.contracts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Simple compliance status summary for an entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceStatusDTO {

    /**
     * Entity identifier
     */
    private String entityId;

    /**
     * Entity type
     */
    private String entityType;

    /**
     * Overall compliance status
     */
    private ComplianceStatus status;

    /**
     * Compliance score (0-100)
     */
    private Double score;

    /**
     * Last validation date
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastValidatedAt;

    /**
     * Next scheduled validation
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime nextValidationAt;

    /**
     * Number of open findings
     */
    private Integer openFindingsCount;

    /**
     * Number of critical findings
     */
    private Integer criticalFindingsCount;

    /**
     * Is entity compliant
     */
    private Boolean compliant;

    /**
     * Summary message
     */
    private String message;
}
