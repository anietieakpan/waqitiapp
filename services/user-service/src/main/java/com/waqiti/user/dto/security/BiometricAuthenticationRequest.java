package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request to authenticate using biometric
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BiometricAuthenticationRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Biometric type is required")
    private BiometricType biometricType;

    @NotBlank(message = "Biometric data is required")
    private String biometricData; // Base64 encoded biometric data

    private String credentialId; // Optional: specific credential to use
    private String deviceFingerprint;
    private String sessionId;
    private String challengeToken;

    // Context information
    private String userAgent;
    private String ipAddress;
    private String location; // Geolocation
    private Long timestamp;

    // Additional biometric-specific data
    private Map<String, Object> biometricMetadata;

    // For fingerprint
    private String fingerprintImage;

    // For face recognition
    private String faceImage;
    private Boolean livenessDetected;

    // For voice recognition
    private String voiceSample;
    private String voicePhrase;

    // For iris scan
    private String irisImage;

    // For behavioral biometrics
    private Map<String, Object> typingData;
    private Map<String, Object> mouseData;
    private Map<String, Object> navigationData;

    // Quality and confidence
    private Double qualityScore;
    private Double confidenceLevel;

    // Authentication context
    private String authenticationPurpose; // LOGIN, TRANSACTION, etc.
    private String transactionContext; // For transaction-specific auth
}