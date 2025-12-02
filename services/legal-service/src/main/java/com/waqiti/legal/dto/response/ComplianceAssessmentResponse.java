package com.waqiti.legal.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for compliance assessment data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceAssessmentResponse {

    private String assessmentId;
    private String requirementId;
    private String requirementName;
    private String assessmentType;
    private LocalDate assessmentDate;
    private String assessorName;
    private String scope;
    private String methodology;
    private String findings;
    private String recommendations;
    private String status;
    private LocalDate dueDate;
    private LocalDate completionDate;
    private String complianceScore;
    private boolean compliant;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
