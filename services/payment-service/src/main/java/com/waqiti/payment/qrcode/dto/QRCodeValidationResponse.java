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
 * CRITICAL P0 FIX: QR Code Validation Response DTO
 *
 * Response DTO for QR code validation operations.
 * Indicates whether a QR code is valid, active, and ready for payment processing.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing QR code validation results")
public class QRCodeValidationResponse {

    @Schema(description = "Unique identifier for the QR code", example = "QR1234567890ABCDEF", required = true)
    private String qrCodeId;

    @Schema(description = "Validation status", example = "true", required = true)
    private Boolean valid;

    @Schema(description = "Human-readable validation status", example = "ACTIVE", required = true)
    private ValidationStatus status;

    @Schema(description = "Validation message", example = "QR code is valid and ready for payment")
    private String message;

    @Schema(description = "Error code if validation failed", example = "QR_EXPIRED")
    private String errorCode;

    @Schema(description = "Error details if validation failed")
    private String errorDetails;

    @Schema(description = "List of validation warnings")
    private List<String> warnings;

    @Schema(description = "Payment amount (if fixed)", example = "99.99")
    private BigDecimal amount;

    @Schema(description = "Minimum payment amount (if dynamic)", example = "1.00")
    private BigDecimal minimumAmount;

    @Schema(description = "Maximum payment amount (if dynamic)", example = "1000.00")
    private BigDecimal maximumAmount;

    @Schema(description = "Currency code", example = "USD", required = true)
    private String currency;

    @Schema(description = "QR code type", example = "USER_DYNAMIC")
    private String type;

    @Schema(description = "Payment description", example = "Coffee payment")
    private String description;

    @Schema(description = "External reference ID", example = "INV-2024-001")
    private String reference;

    @Schema(description = "Payee user ID", example = "usr_123456")
    private String payeeUserId;

    @Schema(description = "Payee display name", example = "Coffee Shop")
    private String payeeName;

    @Schema(description = "Merchant ID (if applicable)", example = "mch_789012")
    private String merchantId;

    @Schema(description = "Merchant name (if applicable)", example = "Coffee Shop Inc.")
    private String merchantName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "QR code expiry timestamp")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "QR code creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Time remaining until expiry (seconds)", example = "120")
    private Long timeRemainingSeconds;

    @Schema(description = "Indicates if QR code has been used", example = "false")
    private Boolean isUsed;

    @Schema(description = "Indicates if QR code is static (reusable)", example = "true")
    private Boolean isStatic;

    @Schema(description = "Scan count for this QR code", example = "5")
    private Integer scanCount;

    @Schema(description = "Security features required")
    private SecurityFeatures securityFeatures;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Risk score (0-100, higher is riskier)", example = "15")
    private Integer riskScore;

    @Schema(description = "Fraud detection alerts")
    private List<String> fraudAlerts;

    @Schema(description = "Geolocation verification required", example = "false")
    private Boolean requiresGeolocation;

    @Schema(description = "Device binding enforced", example = "false")
    private Boolean requiresDeviceBinding;

    @Schema(description = "Validation timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validatedAt;

    @Schema(description = "Validation processing time (ms)", example = "45")
    private Long validationDurationMs;

    @Schema(description = "Compliance checks passed", example = "true")
    private Boolean compliancePassed;

    @Schema(description = "Rate limit information")
    private RateLimitInfo rateLimitInfo;

    /**
     * Validation status enumeration
     */
    public enum ValidationStatus {
        ACTIVE,
        EXPIRED,
        USED,
        CANCELLED,
        SUSPENDED,
        INVALID,
        BLOCKED,
        PENDING_APPROVAL
    }

    /**
     * Security features required for payment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Security features required for this QR code payment")
    public static class SecurityFeatures {

        @Schema(description = "PIN required", example = "false")
        private Boolean requirePin;

        @Schema(description = "Biometric authentication required", example = "true")
        private Boolean requireBiometric;

        @Schema(description = "Confirmation required", example = "true")
        private Boolean requireConfirmation;

        @Schema(description = "Maximum allowed scan attempts", example = "3")
        private Integer maxScanAttempts;

        @Schema(description = "Fraud detection level", example = "MEDIUM")
        private String fraudDetectionLevel;
    }

    /**
     * Rate limit information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Rate limit information for validation")
    public static class RateLimitInfo {

        @Schema(description = "Remaining validations allowed", example = "95")
        private Integer remaining;

        @Schema(description = "Total validation limit", example = "100")
        private Integer limit;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Rate limit reset timestamp")
        private LocalDateTime resetAt;
    }

    // Helper methods
    public boolean isExpired() {
        return status == ValidationStatus.EXPIRED ||
               (expiresAt != null && LocalDateTime.now().isAfter(expiresAt));
    }

    public boolean isReadyForPayment() {
        return valid && status == ValidationStatus.ACTIVE && !isExpired() && !isUsed;
    }

    // Builder convenience methods
    public static QRCodeValidationResponse success(String qrCodeId, BigDecimal amount, String currency) {
        return QRCodeValidationResponse.builder()
            .qrCodeId(qrCodeId)
            .valid(true)
            .status(ValidationStatus.ACTIVE)
            .message("QR code is valid and ready for payment")
            .amount(amount)
            .currency(currency)
            .validatedAt(LocalDateTime.now())
            .build();
    }

    public static QRCodeValidationResponse invalid(String qrCodeId, String errorCode, String errorDetails) {
        return QRCodeValidationResponse.builder()
            .qrCodeId(qrCodeId)
            .valid(false)
            .status(ValidationStatus.INVALID)
            .errorCode(errorCode)
            .errorDetails(errorDetails)
            .message("QR code validation failed")
            .validatedAt(LocalDateTime.now())
            .build();
    }
}
