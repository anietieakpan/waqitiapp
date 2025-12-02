package com.waqiti.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Generate Compliance Report Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateComplianceReportRequest {
    @NotBlank
    private String reportType;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private String format; // PDF, CSV, JSON
    private Boolean includeDetails;
}
