package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * CRITICAL P0 FIX: QR Code Fraud Alert Response DTO
 *
 * Response DTO for fraud detection alerts related to QR code usage.
 * Contains risk scoring, suspicious patterns, and recommended actions.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing QR code fraud alerts")
public class QRCodeFraudAlertResponse {

    @Schema(description = "Unique alert identifier", example = "alert_xyz789", required = true)
    private String alertId;

    @Schema(description = "QR code ID", example = "QR1234567890ABCDEF", required = true)
    private String qrCodeId;

    @Schema(description = "Alert severity level", example = "HIGH", required = true)
    private AlertSeverity severity;

    @Schema(description = "Alert status", example = "ACTIVE", required = true)
    private AlertStatus status;

    @Schema(description = "Fraud risk score (0-100, higher is riskier)", example = "75", required = true)
    private Integer riskScore;

    @Schema(description = "Previous risk score for comparison", example = "25")
    private Integer previousRiskScore;

    @Schema(description = "Alert title", example = "Suspicious QR code activity detected")
    private String title;

    @Schema(description = "Detailed alert description")
    private String description;

    @Schema(description = "List of detected fraud patterns")
    private List<FraudPattern> detectedPatterns;

    @Schema(description = "List of triggered fraud rules")
    private List<String> triggeredRules;

    @Schema(description = "Recommended actions")
    private List<RecommendedAction> recommendedActions;

    @Schema(description = "Affected user ID", example = "usr_123456")
    private String userId;

    @Schema(description = "Affected merchant ID", example = "mch_789012")
    private String merchantId;

    @Schema(description = "Suspicious IP addresses")
    private List<String> suspiciousIps;

    @Schema(description = "Suspicious device IDs")
    private List<String> suspiciousDevices;

    @Schema(description = "Suspicious locations")
    private List<Location> suspiciousLocations;

    @Schema(description = "Number of suspicious scans", example = "15")
    private Integer suspiciousScanCount;

    @Schema(description = "Number of failed payment attempts", example = "8")
    private Integer failedPaymentAttempts;

    @Schema(description = "Potential loss amount", example = "5000.00")
    private BigDecimal potentialLossAmount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Alert creation timestamp")
    private LocalDateTime alertedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "First suspicious activity timestamp")
    private LocalDateTime firstDetectedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Most recent suspicious activity timestamp")
    private LocalDateTime lastDetectedAt;

    @Schema(description = "Indicates if QR code has been automatically blocked", example = "false")
    private Boolean autoBlocked;

    @Schema(description = "Indicates if manual review is required", example = "true")
    private Boolean requiresManualReview;

    @Schema(description = "Investigation notes")
    private String investigationNotes;

    @Schema(description = "Related transaction IDs")
    private List<String> relatedTransactions;

    @Schema(description = "Machine learning model confidence score", example = "0.92")
    private Double mlConfidence;

    @Schema(description = "Additional fraud indicators")
    private Map<String, Object> fraudIndicators;

    @Schema(description = "Alert metadata")
    private Map<String, Object> metadata;

    /**
     * Alert severity enumeration
     */
    public enum AlertSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Alert status enumeration
     */
    public enum AlertStatus {
        ACTIVE,
        INVESTIGATING,
        RESOLVED,
        FALSE_POSITIVE,
        ESCALATED,
        DISMISSED
    }

    /**
     * Detected fraud pattern
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Detected fraud pattern")
    public static class FraudPattern {

        @Schema(description = "Pattern type", example = "VELOCITY_ABUSE")
        private String patternType;

        @Schema(description = "Pattern description", example = "Unusually high scan frequency from same device")
        private String description;

        @Schema(description = "Confidence score (0-1)", example = "0.85")
        private Double confidence;

        @Schema(description = "Evidence count", example = "12")
        private Integer evidenceCount;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "First occurrence")
        private LocalDateTime firstOccurrence;
    }

    /**
     * Recommended action
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Recommended action to mitigate fraud")
    public static class RecommendedAction {

        @Schema(description = "Action type", example = "BLOCK_QR_CODE")
        private String actionType;

        @Schema(description = "Action description", example = "Block QR code to prevent further abuse")
        private String description;

        @Schema(description = "Action priority", example = "HIGH")
        private String priority;

        @Schema(description = "Indicates if action can be automated", example = "true")
        private Boolean automatable;

        @Schema(description = "Indicates if action has been taken", example = "false")
        private Boolean executed;
    }

    /**
     * Suspicious location
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Suspicious location information")
    public static class Location {

        @Schema(description = "Latitude", example = "37.7749")
        private Double latitude;

        @Schema(description = "Longitude", example = "-122.4194")
        private Double longitude;

        @Schema(description = "City", example = "San Francisco")
        private String city;

        @Schema(description = "Country code", example = "US")
        private String countryCode;

        @Schema(description = "Number of activities from this location", example = "8")
        private Integer activityCount;
    }

    // Helper methods
    public boolean isCritical() {
        return severity == AlertSeverity.CRITICAL || severity == AlertSeverity.HIGH;
    }

    public boolean isActive() {
        return status == AlertStatus.ACTIVE || status == AlertStatus.INVESTIGATING;
    }

    public boolean requiresImmediateAction() {
        return isCritical() && requiresManualReview && !autoBlocked;
    }
}
