package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * DTO for image quality assessment results including sharpness, contrast, and readability scores.
 * Used to determine if check images are suitable for processing.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageQualityResult {

    /**
     * Unique quality assessment ID.
     */
    @JsonProperty("assessment_id")
    private String assessmentId;

    /**
     * Image identifier being assessed.
     */
    @JsonProperty("image_id")
    private String imageId;

    /**
     * Overall quality score (0-100, higher is better).
     */
    @NotNull(message = "Overall score cannot be null")
    @DecimalMin(value = "0.0", message = "Overall score must be between 0 and 100")
    @DecimalMax(value = "100.0", message = "Overall score must be between 0 and 100")
    @JsonProperty("overall_score")
    private BigDecimal overallScore;

    /**
     * Quality assessment result.
     */
    @NotNull(message = "Quality result cannot be null")
    @JsonProperty("quality_result")
    @Builder.Default
    private QualityResult qualityResult = QualityResult.PENDING;

    /**
     * Detailed quality metrics.
     */
    @JsonProperty("quality_metrics")
    private QualityMetrics qualityMetrics;

    /**
     * Image characteristics analysis.
     */
    @JsonProperty("image_characteristics")
    private ImageCharacteristics imageCharacteristics;

    /**
     * Issues detected with the image.
     */
    @JsonProperty("detected_issues")
    @Builder.Default
    private java.util.List<QualityIssue> detectedIssues = new java.util.ArrayList<>();

    /**
     * Recommendations for image improvement.
     */
    @Builder.Default
    private java.util.List<String> recommendations = new java.util.ArrayList<>();

    /**
     * Processing metadata.
     */
    @JsonProperty("processing_metadata")
    private ProcessingMetadata processingMetadata;

    /**
     * Quality assessment result enumeration.
     */
    public enum QualityResult {
        PENDING("Quality assessment in progress"),
        EXCELLENT("Image quality is excellent"),
        GOOD("Image quality is good"),
        ACCEPTABLE("Image quality is acceptable for processing"),
        POOR("Image quality is poor, may cause processing issues"),
        UNACCEPTABLE("Image quality is too poor for processing"),
        ERROR("Error occurred during quality assessment");

        private final String description;

        QualityResult(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Detailed quality metrics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityMetrics {
        
        /**
         * Sharpness/focus score (0-100).
         */
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        @JsonProperty("sharpness_score")
        private BigDecimal sharpnessScore;
        
        /**
         * Contrast score (0-100).
         */
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        @JsonProperty("contrast_score")
        private BigDecimal contrastScore;
        
        /**
         * Brightness score (0-100).
         */
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        @JsonProperty("brightness_score")
        private BigDecimal brightnessScore;
        
        /**
         * Color balance score (0-100).
         */
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        @JsonProperty("color_balance_score")
        private BigDecimal colorBalanceScore;
        
        /**
         * Noise level score (0-100, higher = less noise).
         */
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        @JsonProperty("noise_score")
        private BigDecimal noiseScore;
        
        /**
         * Text readability score (0-100).
         */
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        @JsonProperty("readability_score")
        private BigDecimal readabilityScore;
        
        /**
         * MICR line quality score (0-100).
         */
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        @JsonProperty("micr_quality_score")
        private BigDecimal micrQualityScore;
        
        /**
         * Geometric distortion score (0-100, higher = less distortion).
         */
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        @JsonProperty("distortion_score")
        private BigDecimal distortionScore;
    }

    /**
     * Image characteristics and properties.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageCharacteristics {
        
        /**
         * Image resolution width.
         */
        private Integer width;
        
        /**
         * Image resolution height.
         */
        private Integer height;
        
        /**
         * Dots per inch (DPI).
         */
        private Integer dpi;
        
        /**
         * Color depth in bits.
         */
        @JsonProperty("color_depth")
        private Integer colorDepth;
        
        /**
         * File size in bytes.
         */
        @JsonProperty("file_size_bytes")
        private Long fileSizeBytes;
        
        /**
         * Image format (JPEG, PNG, etc.).
         */
        @JsonProperty("image_format")
        private String imageFormat;
        
        /**
         * Whether image is color or grayscale.
         */
        @JsonProperty("is_color")
        private Boolean isColor;
        
        /**
         * Compression ratio (if applicable).
         */
        @JsonProperty("compression_ratio")
        private BigDecimal compressionRatio;
        
        /**
         * Aspect ratio of the image.
         */
        @JsonProperty("aspect_ratio")
        private BigDecimal aspectRatio;
        
        /**
         * Whether image has sufficient resolution for OCR.
         */
        @JsonProperty("sufficient_resolution")
        private Boolean sufficientResolution;
    }

    /**
     * Quality issue detected during assessment.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityIssue {
        
        /**
         * Type of issue detected.
         */
        @JsonProperty("issue_type")
        private IssueType issueType;
        
        /**
         * Severity of the issue.
         */
        private IssueSeverity severity;
        
        /**
         * Description of the issue.
         */
        private String description;
        
        /**
         * Location of the issue (if applicable).
         */
        private IssueLocation location;
        
        /**
         * Confidence in issue detection (0-100).
         */
        @JsonProperty("confidence_score")
        private BigDecimal confidenceScore;
        
        /**
         * Suggested remediation.
         */
        private String remediation;
    }

    /**
     * Types of quality issues.
     */
    public enum IssueType {
        BLUR("Image is blurry or out of focus"),
        LOW_CONTRAST("Poor contrast between text and background"),
        OVEREXPOSURE("Image is overexposed (too bright)"),
        UNDEREXPOSURE("Image is underexposed (too dark)"),
        NOISE("Excessive noise or grain in image"),
        DISTORTION("Geometric distortion or skew"),
        MISSING_CORNERS("Check corners are not visible"),
        PARTIAL_CHECK("Check is partially cut off"),
        SHADOWS("Shadows obscuring text"),
        GLARE("Glare or reflections affecting readability"),
        LOW_RESOLUTION("Insufficient resolution for processing"),
        COMPRESSION_ARTIFACTS("Compression artifacts affecting quality"),
        COLOR_ISSUES("Color balance or saturation problems"),
        MICR_ISSUES("MICR line quality problems");

        private final String description;

        IssueType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Severity levels for quality issues.
     */
    public enum IssueSeverity {
        LOW("Minor issue that may not affect processing"),
        MEDIUM("Moderate issue that may cause processing problems"),
        HIGH("Significant issue likely to cause processing failure"),
        CRITICAL("Critical issue that will prevent processing");

        private final String description;

        IssueSeverity(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Location information for issues.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueLocation {
        
        /**
         * X coordinate of issue location.
         */
        private Integer x;
        
        /**
         * Y coordinate of issue location.
         */
        private Integer y;
        
        /**
         * Width of affected area.
         */
        private Integer width;
        
        /**
         * Height of affected area.
         */
        private Integer height;
        
        /**
         * Region description.
         */
        private String region;
    }

    /**
     * Processing metadata.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingMetadata {
        
        /**
         * When assessment started.
         */
        @JsonProperty("started_at")
        private java.time.LocalDateTime startedAt;
        
        /**
         * When assessment completed.
         */
        @JsonProperty("completed_at")
        private java.time.LocalDateTime completedAt;
        
        /**
         * Processing duration in milliseconds.
         */
        @JsonProperty("processing_duration_ms")
        private Long processingDurationMs;
        
        /**
         * Algorithm version used.
         */
        @JsonProperty("algorithm_version")
        private String algorithmVersion;
        
        /**
         * Processing node identifier.
         */
        @JsonProperty("processing_node")
        private String processingNode;
    }

    /**
     * Checks if the image quality is acceptable for processing.
     *
     * @return true if quality is acceptable or better
     */
    public boolean isAcceptableQuality() {
        return qualityResult == QualityResult.EXCELLENT ||
               qualityResult == QualityResult.GOOD ||
               qualityResult == QualityResult.ACCEPTABLE;
    }

    /**
     * Checks if there are any critical quality issues.
     *
     * @return true if critical issues exist
     */
    public boolean hasCriticalIssues() {
        return detectedIssues.stream()
                .anyMatch(issue -> issue.severity == IssueSeverity.CRITICAL);
    }

    /**
     * Gets the highest severity issue detected.
     *
     * @return highest severity level, or null if no issues
     */
    public IssueSeverity getHighestSeverity() {
        return detectedIssues.stream()
                .map(issue -> issue.severity)
                .max(Enum::compareTo)
                .orElse(null);
    }

    /**
     * Gets issues by type.
     *
     * @param issueType the type of issue to filter by
     * @return list of issues of the specified type
     */
    public java.util.List<QualityIssue> getIssuesByType(IssueType issueType) {
        return detectedIssues.stream()
                .filter(issue -> issue.issueType == issueType)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Gets a summary of quality metrics.
     *
     * @return formatted summary string
     */
    public String getQualitySummary() {
        if (qualityMetrics == null) {
            return "Quality metrics not available";
        }
        
        return String.format(
            "Overall: %.1f, Sharpness: %.1f, Contrast: %.1f, Brightness: %.1f, Readability: %.1f",
            overallScore,
            qualityMetrics.sharpnessScore != null ? qualityMetrics.sharpnessScore : BigDecimal.ZERO,
            qualityMetrics.contrastScore != null ? qualityMetrics.contrastScore : BigDecimal.ZERO,
            qualityMetrics.brightnessScore != null ? qualityMetrics.brightnessScore : BigDecimal.ZERO,
            qualityMetrics.readabilityScore != null ? qualityMetrics.readabilityScore : BigDecimal.ZERO
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageQualityResult that = (ImageQualityResult) o;
        return Objects.equals(assessmentId, that.assessmentId) &&
               Objects.equals(imageId, that.imageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assessmentId, imageId);
    }

    @Override
    public String toString() {
        return "ImageQualityResult{" +
               "assessmentId='" + assessmentId + '\'' +
               ", imageId='" + imageId + '\'' +
               ", overallScore=" + overallScore +
               ", qualityResult=" + qualityResult +
               ", issuesCount=" + detectedIssues.size() +
               '}';
    }
}