package com.waqiti.payment.checkdeposit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.HashMap;

/**
 * Result object for Check OCR processing
 * Contains all extracted fields from check image with confidence scores
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckOCRResult {

    // MICR Line Data
    private MICRData micrData;

    // Check Details
    private String checkNumber;
    private BigDecimal amount;
    private BigDecimal writtenAmount;
    private LocalDate date;
    private String payeeName;
    private String bankName;
    private String memo;

    // Endorsement (back of check)
    private String endorsement;
    private boolean restrictiveEndorsement;

    // Overall confidence score (0-100)
    private Integer confidenceScore;

    // Individual field confidence scores
    @Builder.Default
    private Map<String, Double> fieldConfidenceScores = new HashMap<>();

    // OCR Metadata
    private String ocrEngine;
    private String processingMethod;
    private Long processingTimeMs;
    private boolean fallbackUsed;

    // Validation Status
    private boolean validated;
    private String validationMessage;

    /**
     * Set confidence score for a specific field
     */
    public void setFieldConfidence(String fieldName, double confidence) {
        if (fieldConfidenceScores == null) {
            fieldConfidenceScores = new HashMap<>();
        }
        fieldConfidenceScores.put(fieldName, confidence);
    }

    /**
     * Get confidence score for a specific field
     */
    public Double getFieldConfidence(String fieldName) {
        if (fieldConfidenceScores == null) {
            return 0.0;
        }
        return fieldConfidenceScores.getOrDefault(fieldName, 0.0);
    }

    /**
     * Check if result has minimum required data
     */
    public boolean hasMinimumRequiredData() {
        return micrData != null &&
               micrData.getRoutingNumber() != null &&
               amount != null &&
               amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
