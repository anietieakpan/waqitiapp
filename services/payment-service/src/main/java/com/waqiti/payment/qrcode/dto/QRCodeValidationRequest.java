package com.waqiti.payment.qrcode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.util.Map;

/**
 * CRITICAL P0 FIX: QR Code Validation Request DTO
 *
 * Request DTO for validating a QR code before payment processing.
 * Validates QR code status, expiry, fraud checks, and compliance.
 *
 * @author Waqiti Engineering Team - Production Fix
 * @version 2.0.0
 * @since 2025-01-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for validating a QR code before payment")
public class QRCodeValidationRequest {

    @NotBlank(message = "QR code ID is required")
    @Size(min = 10, max = 100, message = "QR code ID must be between 10 and 100 characters")
    @Schema(description = "Unique identifier for the QR code to validate",
            required = true,
            example = "QR1234567890ABCDEF")
    private String qrCodeId;

    @Schema(description = "Scanned QR code data payload (for offline validation)")
    private String qrDataPayload;

    @NotBlank(message = "User ID is required")
    @Schema(description = "ID of the user validating/scanning the QR code",
            required = true,
            example = "usr_654321")
    private String userId;

    @Schema(description = "Device ID for tracking and security", example = "dev_abc123xyz")
    private String deviceId;

    @Schema(description = "Device fingerprint for fraud detection")
    private String deviceFingerprint;

    @Schema(description = "IP address of the request", example = "192.168.1.1")
    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^(?:[A-F0-9]{1,4}:){7}[A-F0-9]{1,4}$",
             message = "Invalid IP address format")
    private String ipAddress;

    @Schema(description = "User's current latitude", example = "37.7749")
    @DecimalMin(value = "-90.0", message = "Invalid latitude")
    @DecimalMax(value = "90.0", message = "Invalid latitude")
    private Double latitude;

    @Schema(description = "User's current longitude", example = "-122.4194")
    @DecimalMin(value = "-180.0", message = "Invalid longitude")
    @DecimalMax(value = "180.0", message = "Invalid longitude")
    private Double longitude;

    @Schema(description = "Location accuracy in meters", example = "10")
    @Positive(message = "Location accuracy must be positive")
    private Integer locationAccuracy;

    @Schema(description = "User agent string from device/browser")
    @Size(max = 500, message = "User agent too long")
    private String userAgent;

    @Schema(description = "Platform (iOS, Android, Web)", example = "iOS")
    @Pattern(regexp = "^(iOS|Android|Web)$", message = "Platform must be iOS, Android, or Web")
    private String platform;

    @Schema(description = "App version", example = "2.5.1")
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "Invalid version format")
    private String appVersion;

    @Schema(description = "Validate security features", example = "true")
    private Boolean validateSecurity;

    @Schema(description = "Validate compliance rules", example = "true")
    private Boolean validateCompliance;

    @Schema(description = "Check fraud detection rules", example = "true")
    private Boolean checkFraud;

    @Schema(description = "Validate geolocation restrictions", example = "false")
    private Boolean validateGeolocation;

    @Schema(description = "Session ID for tracking validation flow")
    @Size(max = 100, message = "Session ID too long")
    private String sessionId;

    @Schema(description = "Source of validation request", example = "mobile_app")
    @Size(max = 50, message = "Source too long")
    private String source;

    @Schema(description = "Additional metadata for validation")
    private Map<String, String> metadata;

    @Schema(description = "Skip cache and force fresh validation", example = "false")
    private Boolean skipCache;

    @Schema(description = "Include detailed fraud analysis", example = "false")
    private Boolean includeFraudDetails;

    @Schema(description = "Include payment history for this QR code", example = "false")
    private Boolean includePaymentHistory;

    @Schema(description = "Validation timeout in milliseconds", example = "5000")
    @Min(value = 100, message = "Timeout too short")
    @Max(value = 30000, message = "Timeout too long")
    private Integer timeoutMs;

    // Validation helper methods
    @AssertTrue(message = "Location coordinates must both be provided or both be null")
    private boolean isLocationValid() {
        if (latitude != null || longitude != null) {
            return latitude != null && longitude != null;
        }
        return true;
    }
}
