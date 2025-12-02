package com.waqiti.security.dto;

import com.waqiti.security.domain.BiometricType;
import com.waqiti.security.domain.SigningMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignTransactionRequest {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Signing method is required")
    private SigningMethod signingMethod;

    @NotBlank(message = "Transaction data is required")
    private String transactionData;

    // Transaction details
    private Double transactionAmount;
    private String transactionCurrency;
    private String transactionType;
    private String recipientId;
    private String recipientName;
    private String description;

    // Signing key selection
    private String signingKeyId;
    private boolean useDefaultKey;

    // Hardware key specific
    private String hardwareDeviceId;
    private String pinCode;
    private boolean requireUserPresence;
    private Map<String, String> hardwareOptions;

    // Biometric specific
    private BiometricData biometricData;
    private BiometricType biometricType;
    private boolean fallbackToPin;

    // Multi-signature specific
    private String multiSigConfigId;
    private Integer requiredSignatures;
    private List<String> signerIds;
    private String signerMessage;

    // Security context
    private String deviceInfo;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private Map<String, String> deviceFingerprint;

    // Risk and compliance
    private Map<String, Object> riskFactors;
    private boolean highValueTransaction;
    private boolean internationalTransaction;
    private String complianceNotes;

    // Timestamp and nonce for replay protection
    @NotNull(message = "Timestamp is required")
    private Long timestamp;

    @NotBlank(message = "Nonce is required")
    private String nonce;

    // Additional security options
    private boolean requireStepUp;
    private String stepUpMethod;
    private boolean requireLocationVerification;
    private GeoLocation currentLocation;

    // Notification preferences
    private boolean notifyOnSign;
    private List<String> notificationChannels;

    // Session binding
    private String authToken;
    private String refreshToken;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BiometricData {
        private String template;
        private String encryptedData;
        private String captureDevice;
        private Double qualityScore;
        private Long captureTimestamp;
        private Map<String, String> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocation {
        private Double latitude;
        private Double longitude;
        private Double accuracy;
        private String provider;
        private Long timestamp;
    }

    /**
     * Validate request based on signing method
     */
    public boolean isValid() {
        switch (signingMethod) {
            case HARDWARE_KEY:
                return hardwareDeviceId != null && !hardwareDeviceId.isEmpty();
            case BIOMETRIC:
                return biometricData != null && biometricType != null;
            case MULTI_SIGNATURE:
                return multiSigConfigId != null || 
                       (requiredSignatures != null && signerIds != null);
            case SOFTWARE_KEY:
                return true; // Software key always available
            default:
                return false;
        }
    }

    /**
     * Check if this is a high-risk transaction
     */
    public boolean isHighRisk() {
        if (highValueTransaction) {
            return true;
        }

        if (transactionAmount != null && transactionAmount > 10000) {
            return true;
        }

        if (internationalTransaction) {
            return true;
        }

        if (riskFactors != null && riskFactors.containsKey("suspicious")) {
            return true;
        }

        return false;
    }

    /**
     * Get required authentication level
     */
    public AuthenticationLevel getRequiredAuthLevel() {
        if (isHighRisk()) {
            return AuthenticationLevel.HIGH;
        }

        if (signingMethod == SigningMethod.HARDWARE_KEY || 
            signingMethod == SigningMethod.MULTI_SIGNATURE) {
            return AuthenticationLevel.HIGH;
        }

        if (signingMethod == SigningMethod.BIOMETRIC) {
            return AuthenticationLevel.MEDIUM;
        }

        return AuthenticationLevel.STANDARD;
    }

    public enum AuthenticationLevel {
        STANDARD,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}