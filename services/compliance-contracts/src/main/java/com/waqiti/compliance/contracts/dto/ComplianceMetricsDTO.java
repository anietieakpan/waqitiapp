package com.waqiti.compliance.contracts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Compliance metrics and statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceMetricsDTO {

    /**
     * Period start
     */
    private String periodStart;

    /**
     * Period end
     */
    private String periodEnd;

    /**
     * Total validations performed
     */
    private Long totalValidations;

    /**
     * Validations passed
     */
    private Long validationsPassed;

    /**
     * Validations failed
     */
    private Long validationsFailed;

    /**
     * Average compliance score
     */
    private Double averageComplianceScore;

    /**
     * Total findings
     */
    private Long totalFindings;

    /**
     * Findings by severity
     */
    private Map<FindingSeverity, Long> findingsBySeverity;

    /**
     * Open findings count
     */
    private Long openFindingsCount;

    /**
     * Closed findings count
     */
    private Long closedFindingsCount;

    /**
     * Average time to resolution (hours)
     */
    private Double averageTimeToResolutionHours;

    /**
     * AML cases created
     */
    private Long amlCasesCreated;

    /**
     * SAR filings
     */
    private Long sarFilings;

    /**
     * Compliance by framework
     */
    private Map<String, Double> complianceScoreByFramework;

    /**
     * Trend direction (IMPROVING, STABLE, DECLINING)
     */
    private String trend;
}
