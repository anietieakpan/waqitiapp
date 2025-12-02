package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compliance Report Detail Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReportDetailResponse {
    private UUID reportId;
    private String reportType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Map<String, Object> summary;
    private List<Map<String, Object>> events;
    private Map<String, Object> statistics;
    private List<String> recommendations;
    private LocalDateTime generatedAt;
    private String generatedBy;
    private String downloadUrl;
}
