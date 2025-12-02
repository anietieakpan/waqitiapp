package com.waqiti.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for receipt upload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptUploadResponseDto {

    private String receiptId;
    private String expenseId;
    private String fileUrl;
    private String fileName;
    private Long fileSizeBytes;
    private String mimeType;
    private LocalDateTime uploadedAt;

    // OCR Results (if enabled)
    private Boolean ocrProcessed;
    private OcrData ocrData;

    private Boolean uploadSuccess;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrData {
        private BigDecimal extractedAmount;
        private String extractedCurrency;
        private LocalDate extractedDate;
        private String extractedMerchant;
        private String extractedCategory;
        private Double confidenceScore;
        private Boolean verified;
        private String rawText;
    }
}
