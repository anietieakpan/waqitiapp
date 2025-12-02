package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.*;
import java.time.Instant;

/**
 * Request DTO for NFC contact exchange
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCContactExchangeRequest {

    @NotBlank(message = "User ID is required")
    @Size(max = 64, message = "User ID must not exceed 64 characters")
    private String userId;

    @NotBlank(message = "Display name is required")
    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @NotBlank(message = "Contact user ID is required")
    @Size(max = 64, message = "Contact user ID must not exceed 64 characters")
    private String contactUserId;

    @NotBlank(message = "Contact display name is required")
    @Size(max = 100, message = "Contact display name must not exceed 100 characters")
    private String contactDisplayName;

    @Size(max = 255, message = "Avatar URL must not exceed 255 characters")
    private String avatarUrl;

    @Size(max = 255, message = "Contact avatar URL must not exceed 255 characters")
    private String contactAvatarUrl;

    @NotBlank(message = "Public key is required")
    @Size(max = 1024, message = "Public key must not exceed 1024 characters")
    private String publicKey;

    @NotBlank(message = "Contact public key is required")
    @Size(max = 1024, message = "Contact public key must not exceed 1024 characters")
    private String contactPublicKey;

    @NotNull(message = "Timestamp is required")
    @PastOrPresent(message = "Timestamp cannot be in the future")
    private Instant timestamp;

    @NotBlank(message = "Signature is required")
    @Size(max = 512, message = "Signature must not exceed 512 characters")
    private String signature;

    @NotBlank(message = "Contact signature is required")
    @Size(max = 512, message = "Contact signature must not exceed 512 characters")
    private String contactSignature;

    @NotBlank(message = "Device ID is required")
    @Size(max = 128, message = "Device ID must not exceed 128 characters")
    private String deviceId;

    @NotBlank(message = "Contact device ID is required")
    @Size(max = 128, message = "Contact device ID must not exceed 128 characters")
    private String contactDeviceId;

    @Size(max = 64, message = "NFC session ID must not exceed 64 characters")
    private String nfcSessionId;

    @Size(max = 32, message = "NFC protocol version must not exceed 32 characters")
    private String nfcProtocolVersion;

    // Contact sharing preferences
    private boolean sharePhoneNumber;
    private boolean shareEmail;
    private boolean shareAddress;
    private boolean shareProfilePicture;
    private boolean allowPaymentRequests;
    private boolean allowDirectPayments;

    // Location data
    private Double latitude;
    private Double longitude;
    private String locationAccuracy;

    // Social platform integration
    @Size(max = 100, message = "Social platform username must not exceed 100 characters")
    private String socialPlatformUsername;

    @Size(max = 32, message = "Social platform type must not exceed 32 characters")
    private String socialPlatformType; // INSTAGRAM, TWITTER, LINKEDIN, etc.

    // Additional contact information
    @Size(max = 100, message = "Company name must not exceed 100 characters")
    private String companyName;

    @Size(max = 100, message = "Job title must not exceed 100 characters")
    private String jobTitle;

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    private String bio;

    // Additional metadata
    @Size(max = 1000, message = "Metadata must not exceed 1000 characters")
    private String metadata;

    /**
     * Validates if the contact exchange request has expired
     */
    public boolean isExpired(int expirationMinutes) {
        if (timestamp == null) {
            return true;
        }
        
        Instant expirationTime = timestamp.plusSeconds(expirationMinutes * 60L);
        return Instant.now().isAfter(expirationTime);
    }

    /**
     * Validates if both users have different IDs
     */
    public boolean hasValidUserIds() {
        return userId != null && contactUserId != null && 
               !userId.equals(contactUserId);
    }

    /**
     * Validates if both devices have different IDs
     */
    public boolean hasValidDeviceIds() {
        return deviceId != null && contactDeviceId != null && 
               !deviceId.equals(contactDeviceId);
    }

    /**
     * Checks if any personal information sharing is enabled
     */
    public boolean hasPersonalInfoSharing() {
        return sharePhoneNumber || shareEmail || shareAddress || shareProfilePicture;
    }

    /**
     * Checks if payment features are enabled
     */
    public boolean hasPaymentFeatures() {
        return allowPaymentRequests || allowDirectPayments;
    }
}