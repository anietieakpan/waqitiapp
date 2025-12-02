package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Export Response DTO
 *
 * Contains results of payment data export operations including file details,
 * record counts, and download information.
 *
 * COMPLIANCE RELEVANCE:
 * - GDPR: Data portability and export requirements
 * - SOX: Financial data export audit trail
 * - PCI DSS: Secure data export handling
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataExportResponse {

    /**
     * Unique export identifier
     */
    @NotNull
    private UUID exportId;

    /**
     * Export status
     * Values: INITIATED, PROCESSING, COMPLETED, FAILED, EXPIRED
     */
    @NotNull
    private String status;

    /**
     * Export type
     * Values: TRANSACTIONS, PAYMENTS, SETTLEMENTS, REFUNDS, CHARGEBACKS, FULL_ACCOUNT
     */
    @NotNull
    private String exportType;

    /**
     * Export format
     * Values: CSV, JSON, XML, XLSX, PDF
     */
    @NotNull
    private String exportFormat;

    /**
     * Date range start
     */
    private String startDate;

    /**
     * Date range end
     */
    private String endDate;

    /**
     * Total record count
     */
    @Positive
    private int recordCount;

    /**
     * File size in bytes
     */
    private long fileSizeBytes;

    /**
     * File size (human readable)
     */
    private String fileSizeFormatted;

    /**
     * File name
     */
    @NotNull
    private String fileName;

    /**
     * File URL for download
     */
    private String fileUrl;

    /**
     * Secure download token
     */
    private String downloadToken;

    /**
     * Download URL expiration
     */
    private LocalDateTime urlExpiresAt;

    /**
     * File available until
     */
    private LocalDateTime fileExpiresAt;

    /**
     * Checksum for file verification
     */
    private String checksum;

    /**
     * Checksum algorithm
     * Values: MD5, SHA256, SHA512
     */
    private String checksumAlgorithm;

    /**
     * Compression applied
     */
    private boolean compressed;

    /**
     * Compression format
     * Values: ZIP, GZIP, NONE
     */
    private String compressionFormat;

    /**
     * Encryption applied
     */
    private boolean encrypted;

    /**
     * Encryption method
     */
    private String encryptionMethod;

    /**
     * Password protected flag
     */
    private boolean passwordProtected;

    /**
     * Export requested by
     */
    @NotNull
    private UUID requestedBy;

    /**
     * Export requested timestamp
     */
    @NotNull
    private LocalDateTime requestedAt;

    /**
     * Export processing started timestamp
     */
    private LocalDateTime processingStartedAt;

    /**
     * Export completed timestamp
     */
    private LocalDateTime completedAt;

    /**
     * Processing duration (ms)
     */
    private long processingDurationMs;

    /**
     * Filters applied
     */
    private List<ExportFilter> filtersApplied;

    /**
     * Columns included
     */
    private List<String> columnsIncluded;

    /**
     * Data anonymized flag
     */
    private boolean dataAnonymized;

    /**
     * PII redacted flag
     */
    private boolean piiRedacted;

    /**
     * Sensitive data masked flag
     */
    private boolean sensitiveDataMasked;

    /**
     * Download count
     */
    private int downloadCount;

    /**
     * Last downloaded timestamp
     */
    private LocalDateTime lastDownloadedAt;

    /**
     * Maximum downloads allowed
     */
    private int maxDownloadsAllowed;

    /**
     * Error code if export failed
     */
    private String errorCode;

    /**
     * Error message if export failed
     */
    private String errorMessage;

    /**
     * Notification sent flag
     */
    private boolean notificationSent;

    /**
     * Compliance check performed
     */
    private boolean complianceCheckPerformed;

    /**
     * Audit logged flag
     */
    private boolean auditLogged;

    /**
     * Audit trail reference
     */
    private UUID auditTrailId;

    /**
     * Notes or additional information
     */
    private String notes;

    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Export Filter nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportFilter {
        private String fieldName;
        private String operator;
        private String value;
    }
}
