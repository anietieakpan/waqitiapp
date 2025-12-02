package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatoryReportResponse {
    private UUID reportId;
    private String reportType;
    private String reportPeriod;
    private String status; // GENERATING, COMPLETED, FAILED, SUBMITTED
    private LocalDateTime generatedAt;
    private LocalDateTime submittedAt;
    private String fileName;
    private Long fileSize;
    private String format;
    private String downloadUrl;
    private Integer recordCount;
    private String submissionReference;
}