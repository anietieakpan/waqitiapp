package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Compliance Report Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReportResponse {
    private UUID reportId;
    private String reportType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Map<String, Object> summary;
    private Map<String, Object> details;
    private LocalDateTime generatedAt;
    private String generatedBy;
}
