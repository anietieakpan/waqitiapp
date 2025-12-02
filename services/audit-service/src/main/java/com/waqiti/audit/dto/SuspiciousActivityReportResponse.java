package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Suspicious Activity Report Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspiciousActivityReportResponse {
    private UUID reportId;
    private String reportNumber;
    private UUID userId;
    private UUID transactionId;
    private String activityType;
    private String description;
    private String severity;
    private String status;
    private Map<String, Object> details;
    private LocalDateTime reportedAt;
    private String reportedBy;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
}
