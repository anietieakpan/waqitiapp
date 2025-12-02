package com.waqiti.compliance.contracts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Compliance report generation response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReportDTO {

    /**
     * Report identifier
     */
    private String reportId;

    /**
     * Validation ID this report is for
     */
    private String validationId;

    /**
     * Report type
     */
    private String reportType;

    /**
     * Report format (PDF, JSON, HTML, etc.)
     */
    private String format;

    /**
     * Report status
     */
    private ReportStatus status;

    /**
     * Download URL for the report
     */
    private String downloadUrl;

    /**
     * Report file size in bytes
     */
    private Long fileSizeBytes;

    /**
     * Generated timestamp
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime generatedAt;

    /**
     * Expiry timestamp for download URL
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    /**
     * Success indicator
     */
    private Boolean success;

    /**
     * Message
     */
    private String message;

    /**
     * Error details (if failed)
     */
    private String errorDetails;
}
