package com.waqiti.legal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for creating a compliance assessment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateComplianceAssessmentRequest {

    @NotBlank(message = "Requirement ID is required")
    private String requirementId;

    @NotBlank(message = "Assessment type is required")
    private String assessmentType;

    @NotNull(message = "Assessment date is required")
    private LocalDate assessmentDate;

    @NotBlank(message = "Assessor name is required")
    private String assessorName;

    private String scope;

    private String methodology;

    private String findings;

    private String recommendations;

    private String status;

    private LocalDate dueDate;
}
