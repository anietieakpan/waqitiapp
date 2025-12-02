package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Request DTO for initializing NFC P2P session
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCP2PSessionRequest {

    @NotBlank(message = "User ID is required")
    @Size(max = 64, message = "User ID must not exceed 64 characters")
    private String userId;

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @Size(max = 255, message = "Avatar URL must not exceed 255 characters")
    private String avatarUrl;

    @NotBlank(message = "Device ID is required")
    @Size(max = 128, message = "Device ID must not exceed 128 characters")
    private String deviceId;

    @Size(max = 32, message = "NFC protocol version must not exceed 32 characters")
    private String nfcProtocolVersion;

    @Min(value = 1, message = "Session timeout must be at least 1 minute")
    @Max(value = 30, message = "Session timeout cannot exceed 30 minutes")
    private Integer sessionTimeoutMinutes;

    // Transfer limits
    @DecimalMin(value = "0.01", message = "Minimum transfer amount must be greater than 0")
    @DecimalMax(value = "5000.00", message = "Maximum transfer amount cannot exceed $5,000")
    private BigDecimal maxTransferAmount;

    @Min(value = 1, message = "Max transfer count must be at least 1")
    @Max(value = 10, message = "Max transfer count cannot exceed 10")
    private Integer maxTransferCount;

    // Location data
    private Double latitude;
    private Double longitude;
    private String locationAccuracy;

    // Security settings
    private boolean requireBiometric;
    private boolean requirePin;
    private String securityLevel; // LOW, MEDIUM, HIGH

    // Contact sharing preferences
    private boolean allowContactSharing;
    private boolean autoAcceptFromContacts;

    // Additional metadata
    @Size(max = 1000, message = "Metadata must not exceed 1000 characters")
    private String metadata;

    /**
     * Gets the session timeout in seconds
     */
    public long getSessionTimeoutSeconds() {
        return sessionTimeoutMinutes != null ? sessionTimeoutMinutes * 60L : 900L; // Default 15 minutes
    }

    /**
     * Gets the default max transfer amount if not specified
     */
    public BigDecimal getMaxTransferAmountOrDefault() {
        return maxTransferAmount != null ? maxTransferAmount : new BigDecimal("1000.00");
    }

    /**
     * Gets the default max transfer count if not specified
     */
    public Integer getMaxTransferCountOrDefault() {
        return maxTransferCount != null ? maxTransferCount : 5;
    }
}