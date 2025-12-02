package com.waqiti.arpayment.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Biometric verification request for security service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiometricVerificationRequest {

    private UUID userId;
    private String biometricType; // FACE, IRIS, VOICE, FINGERPRINT
    private byte[] biometricData; // Encrypted biometric sample
    private String deviceId;
    private String sessionToken;
    private Map<String, Object> contextData;
    private Float confidenceThreshold;
}
