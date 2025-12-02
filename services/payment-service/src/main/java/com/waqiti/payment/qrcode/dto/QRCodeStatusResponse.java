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

/**
 * CRITICAL P0 FIX: QR Code Status Response DTO
 *
 * Response DTO for checking QR code status.
 * Lightweight response for status polling and real-time updates.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing QR code status")
public class QRCodeStatusResponse {

    @Schema(description = "Unique identifier for the QR code", example = "QR1234567890ABCDEF", required = true)
    private String qrCodeId;

    @Schema(description = "QR code status", example = "ACTIVE", required = true)
    private QRCodeStatus status;

    @Schema(description = "Human-readable status message", example = "QR code is active and ready for payments")
    private String statusMessage;

    @Schema(description = "QR code type", example = "MERCHANT_STATIC")
    private String type;

    @Schema(description = "Indicates if QR code is currently usable", example = "true")
    private Boolean isUsable;

    @Schema(description = "Indicates if QR code has been used", example = "false")
    private Boolean isUsed;

    @Schema(description = "Indicates if QR code is expired", example = "false")
    private Boolean isExpired;

    @Schema(description = "Indicates if QR code is static (reusable)", example = "true")
    private Boolean isStatic;

    @Schema(description = "Payment amount (if fixed)", example = "99.99")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "QR code creation timestamp")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "QR code expiry timestamp")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Last used/scanned timestamp")
    private LocalDateTime lastUsedAt;

    @Schema(description = "Time remaining until expiry (seconds)", example = "3600")
    private Long timeRemainingSeconds;

    @Schema(description = "Total scan count", example = "42")
    private Integer scanCount;

    @Schema(description = "Successful payment count", example = "38")
    private Integer successfulPayments;

    @Schema(description = "Total payment volume", example = "3762.50")
    private BigDecimal totalVolume;

    @Schema(description = "Owner user ID", example = "usr_123456")
    private String userId;

    @Schema(description = "Merchant ID (if applicable)", example = "mch_789012")
    private String merchantId;

    @Schema(description = "External reference", example = "INV-2024-001")
    private String reference;

    @Schema(description = "Current fraud risk level", example = "LOW")
    private String fraudRiskLevel;

    @Schema(description = "Active fraud alerts")
    private List<String> fraudAlerts;

    @Schema(description = "Health status", example = "HEALTHY")
    private HealthStatus health;

    @Schema(description = "Health check details")
    private List<String> healthIssues;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Last status check timestamp")
    private LocalDateTime checkedAt;

    @Schema(description = "Status check duration (ms)", example = "25")
    private Long checkDurationMs;

    /**
     * QR code status enumeration
     */
    public enum QRCodeStatus {
        ACTIVE,
        EXPIRED,
        USED,
        CANCELLED,
        SUSPENDED,
        BLOCKED,
        PENDING,
        INACTIVE
    }

    /**
     * Health status enumeration
     */
    public enum HealthStatus {
        HEALTHY,
        WARNING,
        CRITICAL,
        UNKNOWN
    }

    // Helper methods
    public boolean canAcceptPayments() {
        return isUsable && status == QRCodeStatus.ACTIVE && !isExpired;
    }

    public boolean needsRefresh() {
        return isExpired || status == QRCodeStatus.EXPIRED;
    }

    public boolean hasSecurityIssues() {
        return fraudAlerts != null && !fraudAlerts.isEmpty();
    }

    // Builder convenience method
    public static QRCodeStatusResponse active(String qrCodeId) {
        return QRCodeStatusResponse.builder()
            .qrCodeId(qrCodeId)
            .status(QRCodeStatus.ACTIVE)
            .statusMessage("QR code is active")
            .isUsable(true)
            .isExpired(false)
            .health(HealthStatus.HEALTHY)
            .checkedAt(LocalDateTime.now())
            .build();
    }
}
