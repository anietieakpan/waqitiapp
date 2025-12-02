package com.waqiti.security.domain;

public enum BiometricType {
    FINGERPRINT("Fingerprint", "Fingerprint biometric authentication", 0.95),
    FACE_ID("Face ID", "Facial recognition authentication", 0.98),
    TOUCH_ID("Touch ID", "Apple Touch ID fingerprint", 0.95),
    IRIS_SCAN("Iris Scan", "Iris pattern recognition", 0.99),
    VOICE("Voice", "Voice biometric authentication", 0.90),
    PALM_PRINT("Palm Print", "Palm print recognition", 0.94),
    RETINA_SCAN("Retina Scan", "Retinal pattern scan", 0.99),
    BEHAVIORAL("Behavioral", "Behavioral biometrics", 0.85),
    VEIN_PATTERN("Vein Pattern", "Vein pattern recognition", 0.97),
    GAIT("Gait", "Walking pattern analysis", 0.80);

    private final String displayName;
    private final String description;
    private final double baseAccuracy;

    BiometricType(String displayName, String description, double baseAccuracy) {
        this.displayName = displayName;
        this.description = description;
        this.baseAccuracy = baseAccuracy;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getBaseAccuracy() {
        return baseAccuracy;
    }

    public boolean isContactless() {
        return this == FACE_ID || this == IRIS_SCAN || this == VOICE || 
               this == BEHAVIORAL || this == GAIT || this == RETINA_SCAN;
    }

    public boolean isMobileSupported() {
        return this == FINGERPRINT || this == FACE_ID || this == TOUCH_ID || 
               this == VOICE || this == BEHAVIORAL;
    }

    public boolean requiresSpecialHardware() {
        return this == IRIS_SCAN || this == RETINA_SCAN || this == VEIN_PATTERN || 
               this == PALM_PRINT;
    }

    public int getSecurityLevel() {
        if (baseAccuracy >= 0.98) {
            return 5; // Highest
        } else if (baseAccuracy >= 0.95) {
            return 4;
        } else if (baseAccuracy >= 0.90) {
            return 3;
        } else if (baseAccuracy >= 0.85) {
            return 2;
        } else {
            return 1; // Lowest
        }
    }

    public boolean isAvailableOnPlatform(String platform) {
        switch (platform.toLowerCase()) {
            case "ios":
                return this == FACE_ID || this == TOUCH_ID;
            case "android":
                return this == FINGERPRINT || this == FACE_ID || this == VOICE;
            case "web":
                return this == FACE_ID || this == FINGERPRINT || this == BEHAVIORAL;
            default:
                return false;
        }
    }
}