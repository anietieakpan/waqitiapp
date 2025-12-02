package com.waqiti.payment.qrcode.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CRITICAL P0 FIX: QR Code Summary Response DTO
 *
 * Lightweight summary of QR code for list views and dashboards.
 * Contains essential fields without heavy nested objects.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightweight QR code summary for list views")
public class QRCodeSummaryResponse {

    @Schema(description = "Unique identifier for the QR code", example = "QR1234567890ABCDEF", required = true)
    private String qrCodeId;

    @Schema(description = "QR code type", example = "USER_DYNAMIC")
    private String type;

    @Schema(description = "Payment amount (if fixed)", example = "99.99")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Payment description", example = "Coffee payment")
    private String description;

    @Schema(description = "QR code status", example = "ACTIVE")
    private String status;

    @Schema(description = "Owner user ID", example = "usr_123456")
    private String userId;

    @Schema(description = "Owner display name", example = "John Doe")
    private String userName;

    @Schema(description = "Merchant ID (if applicable)", example = "mch_789012")
    private String merchantId;

    @Schema(description = "Merchant name (if applicable)", example = "Coffee Shop")
    private String merchantName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "QR code creation timestamp")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "QR code expiry timestamp")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Last used timestamp")
    private LocalDateTime lastUsedAt;

    @Schema(description = "Indicates if QR code has been used", example = "false")
    private Boolean isUsed;

    @Schema(description = "Indicates if QR code is static (reusable)", example = "true")
    private Boolean isStatic;

    @Schema(description = "Indicates if QR code is expired", example = "false")
    private Boolean isExpired;

    @Schema(description = "Total number of scans", example = "12")
    private Integer scanCount;

    @Schema(description = "Total number of successful payments", example = "8")
    private Integer paymentCount;

    @Schema(description = "Total payment volume", example = "999.99")
    private BigDecimal totalPaymentVolume;

    @Schema(description = "External reference ID", example = "INV-2024-001")
    private String reference;

    @Schema(description = "QR code image URL", example = "https://api.example.com/qr/QR1234567890ABCDEF.png")
    private String qrCodeImageUrl;

    @Schema(description = "Short shareable link", example = "https://waq.it/p/ABC123")
    private String shortUrl;

    // Helper methods
    public boolean isActive() {
        return "ACTIVE".equals(status) && !isExpired && !isUsed;
    }
}
