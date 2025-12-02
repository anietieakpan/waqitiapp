package com.waqiti.user.domain;

/**
 * Comprehensive Biometric Type Enumeration
 * Defines all supported biometric authentication methods
 */
public enum BiometricType {
    
    /**
     * Fingerprint recognition
     */
    FINGERPRINT("Fingerprint", "Fingerprint recognition using minutiae patterns", true, true),
    
    /**
     * Face recognition and Face ID
     */
    FACE_ID("Face ID", "Facial recognition using 3D depth mapping or 2D features", true, true),
    
    /**
     * Iris recognition
     */
    IRIS("Iris", "Iris pattern recognition using infrared imaging", true, false),
    
    /**
     * Voice recognition and authentication
     */
    VOICE("Voice", "Voice pattern recognition using vocal characteristics", false, true),
    
    /**
     * Retina scanning
     */
    RETINA("Retina", "Retinal blood vessel pattern recognition", true, false),
    
    /**
     * Hand geometry recognition
     */
    HAND_GEOMETRY("Hand Geometry", "Hand shape and geometry measurements", false, false),
    
    /**
     * Palm print recognition
     */
    PALM_PRINT("Palm Print", "Palm print pattern recognition", true, false),
    
    /**
     * Signature dynamics
     */
    SIGNATURE("Signature", "Dynamic signature analysis including pressure and timing", false, true),
    
    /**
     * Behavioral biometrics - typing patterns
     */
    BEHAVIORAL("Behavioral", "Continuous authentication using behavioral patterns", false, true),
    
    /**
     * Gait recognition
     */
    GAIT("Gait", "Walking pattern and gait analysis", false, true),
    
    /**
     * DNA analysis (for high-security scenarios)
     */
    DNA("DNA", "DNA sequence analysis for identity verification", true, false),
    
    /**
     * Heart rhythm patterns
     */
    ECG("ECG", "Electrocardiogram pattern recognition", false, true),
    
    /**
     * Vein pattern recognition
     */
    VEIN("Vein", "Finger or palm vein pattern recognition using NIR", true, false),
    
    /**
     * WebAuthn/FIDO2 authentication
     */
    WEBAUTHN("WebAuthn", "FIDO2/WebAuthn authentication using platform authenticators", false, true),
    
    /**
     * Multi-modal biometric fusion
     */
    MULTIMODAL("Multimodal", "Combination of multiple biometric modalities", true, true);
    
    private final String displayName;
    private final String description;
    private final boolean requiresSpecializedHardware;
    private final boolean supportsMobileDevices;
    
    BiometricType(String displayName, String description, 
                  boolean requiresSpecializedHardware, boolean supportsMobileDevices) {
        this.displayName = displayName;
        this.description = description;
        this.requiresSpecializedHardware = requiresSpecializedHardware;
        this.supportsMobileDevices = supportsMobileDevices;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean requiresSpecializedHardware() {
        return requiresSpecializedHardware;
    }
    
    public boolean supportsMobileDevices() {
        return supportsMobileDevices;
    }
    
    /**
     * Get security level of the biometric type
     */
    public SecurityLevel getSecurityLevel() {
        return switch (this) {
            case DNA -> SecurityLevel.ULTRA_HIGH;
            case IRIS, RETINA -> SecurityLevel.VERY_HIGH;
            case FINGERPRINT, PALM_PRINT, VEIN -> SecurityLevel.HIGH;
            case FACE_ID, VOICE, ECG -> SecurityLevel.MEDIUM_HIGH;
            case HAND_GEOMETRY, SIGNATURE, BEHAVIORAL -> SecurityLevel.MEDIUM;
            case GAIT -> SecurityLevel.LOW;
            case WEBAUTHN -> SecurityLevel.HIGH; // Depends on implementation
            case MULTIMODAL -> SecurityLevel.VERY_HIGH;
        };
    }
    
    /**
     * Get accuracy level of the biometric type
     */
    public AccuracyLevel getAccuracyLevel() {
        return switch (this) {
            case DNA -> AccuracyLevel.ULTRA_HIGH;
            case IRIS, RETINA -> AccuracyLevel.VERY_HIGH;
            case FINGERPRINT, PALM_PRINT, VEIN -> AccuracyLevel.HIGH;
            case FACE_ID, VOICE -> AccuracyLevel.MEDIUM_HIGH;
            case HAND_GEOMETRY, ECG -> AccuracyLevel.MEDIUM;
            case SIGNATURE, BEHAVIORAL, GAIT -> AccuracyLevel.MEDIUM_LOW;
            case WEBAUTHN -> AccuracyLevel.HIGH;
            case MULTIMODAL -> AccuracyLevel.VERY_HIGH;
        };
    }
    
    /**
     * Get convenience level of the biometric type
     */
    public ConvenienceLevel getConvenienceLevel() {
        return switch (this) {
            case BEHAVIORAL, GAIT -> ConvenienceLevel.VERY_HIGH;
            case FACE_ID, VOICE, WEBAUTHN -> ConvenienceLevel.HIGH;
            case FINGERPRINT -> ConvenienceLevel.MEDIUM_HIGH;
            case SIGNATURE, ECG -> ConvenienceLevel.MEDIUM;
            case HAND_GEOMETRY, PALM_PRINT, VEIN -> ConvenienceLevel.LOW;
            case IRIS, RETINA -> ConvenienceLevel.VERY_LOW;
            case DNA -> ConvenienceLevel.EXTREMELY_LOW;
            case MULTIMODAL -> ConvenienceLevel.MEDIUM;
        };
    }
    
    /**
     * Check if biometric type is suitable for continuous authentication
     */
    public boolean supportsContinuousAuth() {
        return this == BEHAVIORAL || this == GAIT || this == ECG || this == VOICE;
    }
    
    /**
     * Check if biometric type is contactless
     */
    public boolean isContactless() {
        return this == FACE_ID || this == IRIS || this == RETINA || 
               this == VOICE || this == GAIT || this == BEHAVIORAL ||
               this == ECG || this == VEIN || this == WEBAUTHN;
    }
    
    /**
     * Check if biometric type can be spoofed easily
     */
    public boolean isHighSpoofRisk() {
        return this == FACE_ID || this == VOICE || this == SIGNATURE || 
               this == BEHAVIORAL || this == GAIT;
    }
    
    /**
     * Get recommended matching threshold
     */
    public double getRecommendedThreshold() {
        return switch (this) {
            case DNA -> 0.999;
            case IRIS, RETINA -> 0.95;
            case FINGERPRINT, PALM_PRINT, VEIN -> 0.85;
            case FACE_ID -> 0.8;
            case VOICE -> 0.75;
            case HAND_GEOMETRY -> 0.7;
            case ECG -> 0.8;
            case SIGNATURE -> 0.7;
            case BEHAVIORAL -> 0.6;
            case GAIT -> 0.65;
            case WEBAUTHN -> 0.9;
            case MULTIMODAL -> 0.9;
        };
    }
    
    /**
     * Security Level Enumeration
     */
    public enum SecurityLevel {
        EXTREMELY_LOW, VERY_LOW, LOW, MEDIUM_LOW, MEDIUM, 
        MEDIUM_HIGH, HIGH, VERY_HIGH, ULTRA_HIGH
    }
    
    /**
     * Accuracy Level Enumeration
     */
    public enum AccuracyLevel {
        VERY_LOW, LOW, MEDIUM_LOW, MEDIUM, MEDIUM_HIGH, 
        HIGH, VERY_HIGH, ULTRA_HIGH
    }
    
    /**
     * Convenience Level Enumeration
     */
    public enum ConvenienceLevel {
        EXTREMELY_LOW, VERY_LOW, LOW, MEDIUM_LOW, MEDIUM, 
        MEDIUM_HIGH, HIGH, VERY_HIGH, EXTREMELY_HIGH
    }
    
    /**
     * Get all biometric types suitable for mobile devices
     */
    public static BiometricType[] getMobileCompatible() {
        return java.util.Arrays.stream(values())
            .filter(BiometricType::supportsMobileDevices)
            .toArray(BiometricType[]::new);
    }
    
    /**
     * Get all contactless biometric types
     */
    public static BiometricType[] getContactless() {
        return java.util.Arrays.stream(values())
            .filter(BiometricType::isContactless)
            .toArray(BiometricType[]::new);
    }
    
    /**
     * Get all biometric types suitable for continuous authentication
     */
    public static BiometricType[] getContinuousAuthCapable() {
        return java.util.Arrays.stream(values())
            .filter(BiometricType::supportsContinuousAuth)
            .toArray(BiometricType[]::new);
    }
    
    /**
     * Get biometric types by security level
     */
    public static BiometricType[] getBySecurityLevel(SecurityLevel level) {
        return java.util.Arrays.stream(values())
            .filter(type -> type.getSecurityLevel() == level)
            .toArray(BiometricType[]::new);
    }
}