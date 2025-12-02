package com.waqiti.common.gdpr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GDPR Data Export Result
 * PRODUCTION FIX: Created to support GDPRAuditService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GDPRDataExportResult {
    private String requestId;
    private String userId;
    private ExportStatus status;
    private String exportLocation;
    private Long exportFileSize;
    private String exportFormat;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long processingTimeMs;
    private String errorMessage;

    public enum ExportStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
