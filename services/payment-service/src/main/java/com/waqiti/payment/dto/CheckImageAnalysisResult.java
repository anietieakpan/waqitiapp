package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * DTO containing OCR results and image analysis for check deposits.
 * Includes extracted text, confidence scores, and quality assessments.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckImageAnalysisResult {

    /**
     * Unique analysis ID.
     */
    @JsonProperty("analysis_id")
    private String analysisId;

    /**
     * Image being analyzed (front or back).
     */
    @NotNull(message = "Image type cannot be null")
    @JsonProperty("image_type")
    private ImageType imageType;

    /**
     * Overall processing status.
     */
    @NotNull(message = "Processing status cannot be null")
    @JsonProperty("processing_status")
    @Builder.Default
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    /**
     * OCR extraction results.
     */
    @JsonProperty("ocr_results")
    private OcrResults ocrResults;

    /**
     * Image quality assessment.
     */
    @JsonProperty("quality_assessment")
    private QualityAssessment qualityAssessment;

    /**
     * MICR line analysis (for front of check).
     */
    @JsonProperty("micr_analysis")
    private MicrAnalysis micrAnalysis;

    /**
     * Fraud detection results.
     */
    @JsonProperty("fraud_detection")
    private FraudDetection fraudDetection;

    /**
     * Processing metadata.
     */
    @JsonProperty("processing_info")
    private ProcessingInfo processingInfo;

    /**
     * Validation results.
     */
    @JsonProperty("validation_results")
    @Builder.Default
    private java.util.List<ValidationResult> validationResults = new java.util.ArrayList<>();

    /**
     * Image types for analysis.
     */
    public enum ImageType {
        FRONT("Front side of check"),
        BACK("Back side of check");

        private final String description;

        ImageType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Processing status enumeration.
     */
    public enum ProcessingStatus {
        PENDING("Analysis in progress"),
        COMPLETED("Analysis completed successfully"),
        FAILED("Analysis failed"),
        RETRY_REQUIRED("Image quality too poor, retry required"),
        MANUAL_REVIEW("Requires manual review");

        private final String description;

        ProcessingStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * OCR extraction results.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrResults {
        
        /**
         * Extracted amount from check.
         */
        @JsonProperty("extracted_amount")
        private BigDecimal extractedAmount;
        
        /**
         * Confidence in amount extraction (0-100).
         */
        @JsonProperty("amount_confidence")
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        private BigDecimal amountConfidence;
        
        /**
         * Extracted payee name.
         */
        @JsonProperty("extracted_payee")
        private String extractedPayee;
        
        /**
         * Confidence in payee extraction.
         */
        @JsonProperty("payee_confidence")
        private BigDecimal payeeConfidence;
        
        /**
         * Extracted date.
         */
        @JsonProperty("extracted_date")
        private String extractedDate;
        
        /**
         * Confidence in date extraction.
         */
        @JsonProperty("date_confidence")
        private BigDecimal dateConfidence;
        
        /**
         * Extracted payor/drawer name.
         */
        @JsonProperty("extracted_payor")
        private String extractedPayor;
        
        /**
         * Extracted memo line.
         */
        @JsonProperty("extracted_memo")
        private String extractedMemo;
        
        /**
         * All extracted text with coordinates.
         */
        @JsonProperty("text_blocks")
        @Builder.Default
        private java.util.List<TextBlock> textBlocks = new java.util.ArrayList<>();
    }

    /**
     * Individual text block with location and confidence.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextBlock {
        
        private String text;
        
        @JsonProperty("confidence_score")
        private BigDecimal confidenceScore;
        
        /**
         * Bounding box coordinates.
         */
        @JsonProperty("bounding_box")
        private BoundingBox boundingBox;
        
        /**
         * Type of text (amount, date, payee, etc.).
         */
        @JsonProperty("text_type")
        private String textType;
    }

    /**
     * Bounding box coordinates for text location.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoundingBox {
        private Integer x;
        private Integer y;
        private Integer width;
        private Integer height;
    }

    /**
     * Image quality assessment results.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityAssessment {
        
        /**
         * Overall quality score (0-100).
         */
        @JsonProperty("overall_score")
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        private BigDecimal overallScore;
        
        /**
         * Sharpness/focus score.
         */
        @JsonProperty("sharpness_score")
        private BigDecimal sharpnessScore;
        
        /**
         * Contrast score.
         */
        @JsonProperty("contrast_score")
        private BigDecimal contrastScore;
        
        /**
         * Brightness score.
         */
        @JsonProperty("brightness_score")
        private BigDecimal brightnessScore;
        
        /**
         * Whether image has sufficient resolution.
         */
        @JsonProperty("sufficient_resolution")
        private Boolean sufficientResolution;
        
        /**
         * Whether check is properly aligned.
         */
        @JsonProperty("proper_alignment")
        private Boolean properAlignment;
        
        /**
         * Whether all four corners are visible.
         */
        @JsonProperty("corners_visible")
        private Boolean cornersVisible;
        
        /**
         * Quality issues detected.
         */
        @JsonProperty("quality_issues")
        @Builder.Default
        private java.util.List<String> qualityIssues = new java.util.ArrayList<>();
    }

    /**
     * MICR line analysis for routing and account numbers.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MicrAnalysis {
        
        /**
         * Whether MICR line was detected.
         */
        @JsonProperty("micr_detected")
        private Boolean micrDetected;
        
        /**
         * Extracted routing number.
         */
        @JsonProperty("routing_number")
        private String routingNumber;
        
        /**
         * Confidence in routing number extraction.
         */
        @JsonProperty("routing_confidence")
        private BigDecimal routingConfidence;
        
        /**
         * Extracted account number.
         */
        @JsonProperty("account_number")
        private String accountNumber;
        
        /**
         * Confidence in account number extraction.
         */
        @JsonProperty("account_confidence")
        private BigDecimal accountConfidence;
        
        /**
         * Extracted check number.
         */
        @JsonProperty("check_number")
        private String checkNumber;
        
        /**
         * MICR line quality score.
         */
        @JsonProperty("micr_quality_score")
        private BigDecimal micrQualityScore;
        
        /**
         * Raw MICR line text.
         */
        @JsonProperty("raw_micr_line")
        private String rawMicrLine;
    }

    /**
     * Fraud detection analysis results.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudDetection {
        
        /**
         * Overall fraud risk score (0-100, higher = more risky).
         */
        @JsonProperty("fraud_score")
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        private BigDecimal fraudScore;
        
        /**
         * Risk level assessment.
         */
        @JsonProperty("risk_level")
        private RiskLevel riskLevel;
        
        /**
         * Detected fraud indicators.
         */
        @JsonProperty("fraud_indicators")
        @Builder.Default
        private java.util.List<String> fraudIndicators = new java.util.ArrayList<>();
        
        /**
         * Whether check appears to be altered.
         */
        @JsonProperty("appears_altered")
        private Boolean appearsAltered;
        
        /**
         * Whether check appears to be counterfeit.
         */
        @JsonProperty("appears_counterfeit")
        private Boolean appearsCounterfeit;
        
        /**
         * Duplicate check detection result.
         */
        @JsonProperty("duplicate_detected")
        private Boolean duplicateDetected;
    }

    /**
     * Risk levels for fraud detection.
     */
    public enum RiskLevel {
        LOW("Low risk"),
        MEDIUM("Medium risk"),
        HIGH("High risk"),
        CRITICAL("Critical risk - manual review required");

        private final String description;

        RiskLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Processing information and metadata.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingInfo {
        
        /**
         * When processing started.
         */
        @JsonProperty("started_at")
        private LocalDateTime startedAt;
        
        /**
         * When processing completed.
         */
        @JsonProperty("completed_at")
        private LocalDateTime completedAt;
        
        /**
         * Processing duration in milliseconds.
         */
        @JsonProperty("processing_duration_ms")
        private Long processingDurationMs;
        
        /**
         * OCR engine used.
         */
        @JsonProperty("ocr_engine")
        private String ocrEngine;
        
        /**
         * OCR engine version.
         */
        @JsonProperty("engine_version")
        private String engineVersion;
        
        /**
         * Processing node/server identifier.
         */
        @JsonProperty("processing_node")
        private String processingNode;
        
        /**
         * Error message (if processing failed).
         */
        @JsonProperty("error_message")
        private String errorMessage;
    }

    /**
     * Validation result for specific checks.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        
        @JsonProperty("validation_type")
        private String validationType;
        
        private Boolean passed;
        
        private String message;
        
        @JsonProperty("confidence_score")
        private BigDecimal confidenceScore;
    }

    /**
     * Checks if the analysis is complete and successful.
     *
     * @return true if analysis completed successfully
     */
    public boolean isSuccessful() {
        return processingStatus == ProcessingStatus.COMPLETED;
    }

    /**
     * Checks if manual review is required.
     *
     * @return true if manual review needed
     */
    public boolean requiresManualReview() {
        return processingStatus == ProcessingStatus.MANUAL_REVIEW ||
               (fraudDetection != null && fraudDetection.riskLevel == RiskLevel.CRITICAL);
    }

    /**
     * Gets the overall confidence score for the analysis.
     *
     * @return average confidence across all extracted fields
     */
    public BigDecimal getOverallConfidence() {
        if (ocrResults == null) {
            return BigDecimal.ZERO;
        }
        
        java.util.List<BigDecimal> confidences = new java.util.ArrayList<>();
        
        if (ocrResults.amountConfidence != null) {
            confidences.add(ocrResults.amountConfidence);
        }
        if (ocrResults.payeeConfidence != null) {
            confidences.add(ocrResults.payeeConfidence);
        }
        if (ocrResults.dateConfidence != null) {
            confidences.add(ocrResults.dateConfidence);
        }
        
        if (confidences.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sum = confidences.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sum.divide(BigDecimal.valueOf(confidences.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckImageAnalysisResult that = (CheckImageAnalysisResult) o;
        return Objects.equals(analysisId, that.analysisId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(analysisId);
    }

    @Override
    public String toString() {
        return "CheckImageAnalysisResult{" +
               "analysisId='" + analysisId + '\'' +
               ", imageType=" + imageType +
               ", processingStatus=" + processingStatus +
               ", overallConfidence=" + getOverallConfidence() +
               '}';
    }
}