package com.waqiti.user.dto.security;

/**
 * Supported biometric authentication types
 */
public enum BiometricType {
    FINGERPRINT("Fingerprint"),
    FACE_ID("Face ID"),
    VOICE("Voice Recognition"),
    IRIS("Iris Scan"),
    PALM("Palm Print"),
    BEHAVIORAL("Behavioral Biometrics"),
    WEBAUTHN("WebAuthn/FIDO2"),
    MULTI_MODAL("Multi-Modal Fusion");

    private final String displayName;

    BiometricType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresPhysicalSensor() {
        return switch (this) {
            case FINGERPRINT, FACE_ID, IRIS, PALM -> true;
            case VOICE, BEHAVIORAL, WEBAUTHN, MULTI_MODAL -> false;
        };
    }

    public boolean supportsContinuousAuth() {
        return switch (this) {
            case BEHAVIORAL -> true;
            case FINGERPRINT, FACE_ID, VOICE, IRIS, PALM, WEBAUTHN, MULTI_MODAL -> false;
        };
    }
}