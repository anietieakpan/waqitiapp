package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * CRITICAL P0 FIX: Bulk QR Code Generation Response DTO
 *
 * Response DTO for bulk QR code generation operations.
 * Contains batch job status, generated QR codes, and download links.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for bulk QR code generation")
public class BulkQRCodeGenerationResponse {

    @Schema(description = "Unique identifier for the bulk generation batch", example = "batch_abc123xyz", required = true)
    private String batchId;

    @Schema(description = "Batch processing status", example = "COMPLETED", required = true)
    private BatchStatus status;

    @Schema(description = "Status message", example = "All QR codes generated successfully")
    private String message;

    @Schema(description = "Merchant ID", example = "mch_789012")
    private String merchantId;

    @Schema(description = "User ID who initiated the batch", example = "usr_123456")
    private String userId;

    @Schema(description = "Requested quantity", example = "50")
    private Integer requestedQuantity;

    @Schema(description = "Successfully generated count", example = "50")
    private Integer successCount;

    @Schema(description = "Failed generation count", example = "0")
    private Integer failureCount;

    @Schema(description = "List of successfully generated QR codes")
    private List<QRCodeSummary> qrCodes;

    @Schema(description = "List of failed generation attempts")
    private List<FailedGeneration> failures;

    @Schema(description = "Download URL for bulk QR codes (ZIP)", example = "https://cdn.example.com/bulk/batch_abc123xyz.zip")
    private String downloadUrl;

    @Schema(description = "PDF export URL", example = "https://cdn.example.com/bulk/batch_abc123xyz.pdf")
    private String pdfUrl;

    @Schema(description = "CSV manifest URL", example = "https://cdn.example.com/bulk/batch_abc123xyz.csv")
    private String csvUrl;

    @Schema(description = "JSON manifest URL", example = "https://cdn.example.com/bulk/batch_abc123xyz.json")
    private String jsonUrl;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Batch creation timestamp")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Batch started processing timestamp")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Batch completion timestamp")
    private LocalDateTime completedAt;

    @Schema(description = "Total processing time (ms)", example = "5420")
    private Long processingTimeMs;

    @Schema(description = "Processing progress percentage (0-100)", example = "100")
    private Integer progressPercentage;

    @Schema(description = "Estimated time remaining (seconds)", example = "0")
    private Long estimatedTimeRemainingSeconds;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Error details if batch failed")
    private String errorDetails;

    @Schema(description = "Download links expiry timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime downloadExpiresAt;

    /**
     * Batch status enumeration
     */
    public enum BatchStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        PARTIAL_SUCCESS,
        FAILED,
        CANCELLED
    }

    /**
     * Summary of generated QR code
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Summary of generated QR code")
    public static class QRCodeSummary {

        @Schema(description = "QR code ID", example = "QR1234567890ABCDEF")
        private String qrCodeId;

        @Schema(description = "QR code reference", example = "TERM-001")
        private String reference;

        @Schema(description = "QR code image URL")
        private String imageUrl;

        @Schema(description = "Short URL", example = "https://waq.it/m/ABC123")
        private String shortUrl;

        @Schema(description = "Status", example = "ACTIVE")
        private String status;
    }

    /**
     * Failed generation record
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Failed generation record")
    public static class FailedGeneration {

        @Schema(description = "Index in the batch", example = "25")
        private Integer index;

        @Schema(description = "Reference (if provided)", example = "TERM-025")
        private String reference;

        @Schema(description = "Error code", example = "DUPLICATE_REFERENCE")
        private String errorCode;

        @Schema(description = "Error message", example = "Reference already exists")
        private String errorMessage;
    }

    // Helper methods
    public boolean isCompleted() {
        return status == BatchStatus.COMPLETED || status == BatchStatus.PARTIAL_SUCCESS;
    }

    public boolean hasFailures() {
        return failureCount != null && failureCount > 0;
    }

    public double getSuccessRate() {
        if (requestedQuantity == null || requestedQuantity == 0) {
            return 0.0;
        }
        return (successCount * 100.0) / requestedQuantity;
    }
}
