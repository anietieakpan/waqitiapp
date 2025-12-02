package com.waqiti.payment.qrcode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

/**
 * CRITICAL P0 FIX: QR Code Refresh Request DTO
 *
 * Request DTO for refreshing/regenerating an existing QR code.
 * Used to extend expiry or regenerate QR image for expired codes.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for refreshing/regenerating a QR code")
public class QRCodeRefreshRequest {

    @NotBlank(message = "QR code ID is required")
    @Size(min = 10, max = 100, message = "QR code ID must be between 10 and 100 characters")
    @Schema(description = "Unique identifier for the QR code to refresh",
            required = true,
            example = "QR1234567890ABCDEF")
    private String qrCodeId;

    @NotBlank(message = "User ID is required")
    @Schema(description = "ID of the user requesting the refresh",
            required = true,
            example = "usr_123456")
    private String userId;

    @Min(value = 1, message = "Expiry extension must be at least 1 minute")
    @Max(value = 1440, message = "Expiry extension cannot exceed 24 hours")
    @Schema(description = "Extend expiry by this many minutes", example = "60")
    private Integer extendExpiryMinutes;

    @Schema(description = "Regenerate QR code image", example = "true")
    private Boolean regenerateImage;

    @Schema(description = "Update QR code theme", example = "dark")
    @Size(max = 50, message = "Theme name too long")
    private String newTheme;

    @Min(value = 200)
    @Max(value = 1000)
    @Schema(description = "New QR code image size in pixels", example = "400")
    private Integer newImageSize;

    @Schema(description = "Include new logo in refreshed QR code", example = "false")
    private Boolean updateLogo;

    @Size(max = 500, message = "Logo URL too long")
    @Schema(description = "New logo URL for refreshed QR code")
    private String newLogoUrl;

    @Schema(description = "Reset scan count statistics", example = "false")
    private Boolean resetStatistics;

    @Schema(description = "Reason for refresh", example = "Expired QR code needs renewal")
    @Size(max = 255, message = "Reason too long")
    private String reason;

    @Schema(description = "Generate printable version", example = "false")
    private Boolean generatePrintable;
}
