package com.waqiti.user.dto.security;

import com.waqiti.user.domain.BiometricType;
import com.waqiti.common.validation.ValidationConstraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;

/**
 * Comprehensive Biometric Registration Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to register a new biometric credential")
public class BiometricRegistrationRequest {
    
    @Schema(description = "User ID", hidden = true)
    private String userId;
    
    @NotNull(message = "Biometric type is required")
    @Schema(description = "Type of biometric being registered", required = true)
    private BiometricType biometricType;
    
    @NotNull(message = "Biometric data is required")
    @Valid
    @Schema(description = "Biometric data to be registered", required = true)
    private BiometricData biometricData;
    
    @NotNull(message = "Device info is required")
    @Valid
    @Schema(description = "Device information", required = true)
    private DeviceInfo deviceInfo;
    
    @Schema(description = "Liveness detection data")
    private LivenessData livenessData;
    
    @Size(max = 100)
    @Schema(description = "Friendly name for this biometric credential")
    private String credentialName;
    
    @Schema(description = "Enable continuous authentication")
    private boolean enableContinuousAuth;
    
    @Schema(description = "Registration metadata")
    private Map<String, Object> metadata;
    
    @Schema(description = "Challenge token for replay attack prevention")
    private String challengeToken;
    
    @NotNull(message = "Timestamp is required")
    @Schema(description = "Request timestamp")
    private Long timestamp;
    
    /**
     * Comprehensive Biometric Data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BiometricData {
        
        @NotNull(message = "Raw data is required")
        @Schema(description = "Base64 encoded biometric data", required = true)
        private String rawData;
        
        @Schema(description = "Data format (e.g., WSQ, ISO-19794, JPEG)")
        private String format;
        
        @Min(1)
        @Max(100)
        @Schema(description = "Quality score (1-100)")
        private Integer qualityScore;
        
        @Schema(description = "Capture device info")
        private String captureDevice;
        
        @Schema(description = "Capture timestamp")
        private Long captureTimestamp;
        
        @Schema(description = "Image resolution (for visual biometrics)")
        private String resolution;
        
        @Schema(description = "Color depth")
        private Integer colorDepth;
        
        @Schema(description = "Compression used")
        private String compression;
        
        // Fingerprint specific
        @Schema(description = "Finger position (1-10 for fingers)")
        private Integer fingerPosition;
        
        @Schema(description = "Minutiae count")
        private Integer minutiaeCount;
        
        @Schema(description = "Ridge count")
        private Integer ridgeCount;
        
        // Face specific
        @Schema(description = "Face detection confidence")
        private Double faceConfidence;
        
        @Schema(description = "Number of faces detected")
        private Integer faceCount;
        
        @Schema(description = "Face pose angles")
        private Map<String, Double> poseAngles;
        
        @Schema(description = "Facial landmarks")
        private Map<String, Object> landmarks;
        
        // Voice specific
        @Schema(description = "Audio sample rate")
        private Integer sampleRate;
        
        @Schema(description = "Audio duration in milliseconds")
        private Integer duration;
        
        @Schema(description = "Audio format")
        private String audioFormat;
        
        @Schema(description = "Spoken phrase/passphrase")
        private String spokenPhrase;
        
        @Schema(description = "Voice features extracted")
        private Map<String, Object> voiceFeatures;
        
        // Iris specific
        @Schema(description = "Eye position (LEFT, RIGHT, BOTH)")
        private String eyePosition;
        
        @Schema(description = "Iris diameter in pixels")
        private Integer irisDiameter;
        
        @Schema(description = "Iris texture quality")
        private Double textureQuality;
        
        // Additional features
        @Schema(description = "Extracted feature vector")
        private double[] featureVector;
        
        @Schema(description = "Template data")
        private String templateData;
        
        @Schema(description = "Additional metadata")
        private Map<String, Object> metadata;
    }
    
    /**
     * Comprehensive Device Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        
        @NotBlank(message = "Device ID is required")
        @Size(min = 16, max = 128)
        @Schema(description = "Unique device identifier", required = true)
        private String deviceId;
        
        @Schema(description = "Device fingerprint hash")
        private String deviceFingerprint;
        
        @Schema(description = "Device name")
        private String deviceName;
        
        @Schema(description = "Device manufacturer")
        private String manufacturer;
        
        @Schema(description = "Device model")
        private String model;
        
        @Schema(description = "Operating system")
        private String operatingSystem;
        
        @Schema(description = "OS version")
        private String osVersion;
        
        @Schema(description = "App version")
        private String appVersion;
        
        @Schema(description = "Browser info")
        private String browserInfo;
        
        @Schema(description = "User agent string")
        private String userAgent;
        
        @Schema(description = "Screen resolution")
        private String screenResolution;
        
        @Schema(description = "Timezone")
        private String timezone;
        
        @Schema(description = "Language/locale")
        private String locale;
        
        @Schema(description = "Is device jailbroken/rooted")
        private Boolean isJailbroken;
        
        @Schema(description = "Is debug mode enabled")
        private Boolean isDebugEnabled;
        
        @Schema(description = "Is emulator/simulator")
        private Boolean isEmulator;
        
        @Schema(description = "Has biometric hardware")
        private Boolean hasBiometricHardware;
        
        @Schema(description = "Supported biometric types")
        private String[] supportedBiometrics;
        
        @Schema(description = "Is trusted device")
        private Boolean isTrusted;
        
        @Schema(description = "Trust score (0.0-1.0)")
        private Double trustScore;
        
        @Schema(description = "Device location")
        private LocationInfo location;
        
        @Schema(description = "Network information")
        private NetworkInfo network;
        
        @Schema(description = "Hardware specifications")
        private Map<String, Object> hardwareSpecs;
    }
    
    /**
     * Location Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        private Double latitude;
        
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        private Double longitude;
        
        @Min(0)
        private Double accuracy;
        
        @Schema(description = "Altitude in meters")
        private Double altitude;
        
        @Schema(description = "Location provider")
        private String provider;
        
        @Schema(description = "Location timestamp")
        private Long timestamp;
        
        private String country;
        private String countryCode;
        private String region;
        private String city;
        private String postalCode;
        
        @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^(?:[A-F0-9]{1,4}:){7}[A-F0-9]{1,4}$")
        private String ipAddress;
        
        private String isp;
        private String organization;
        
        @Schema(description = "Is VPN/proxy detected")
        private Boolean isVpn;
        
        @Schema(description = "Is location spoofed")
        private Boolean isSpoofed;
    }
    
    /**
     * Network Information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkInfo {
        
        @Schema(description = "Connection type (WIFI, CELLULAR, ETHERNET)")
        private String connectionType;
        
        @Schema(description = "Network name/SSID")
        private String networkName;
        
        @Schema(description = "Signal strength")
        private Integer signalStrength;
        
        @Schema(description = "Download speed in Mbps")
        private Double downloadSpeed;
        
        @Schema(description = "Upload speed in Mbps")
        private Double uploadSpeed;
        
        @Schema(description = "Network latency in ms")
        private Integer latency;
        
        @Schema(description = "Mobile carrier")
        private String carrier;
        
        @Schema(description = "Network security type")
        private String securityType;
        
        @Schema(description = "Is public network")
        private Boolean isPublicNetwork;
    }
    
    /**
     * Advanced Liveness Detection Data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LivenessData {
        
        @Schema(description = "Liveness detection type")
        private String detectionType;
        
        @Schema(description = "Challenge type (BLINK, SMILE, NOD, etc.)")
        private String challengeType;
        
        @Schema(description = "Challenge sequence")
        private String[] challengeSequence;
        
        @Schema(description = "Eye blink detection frames")
        private String[] eyeBlinkFrames;
        
        @Schema(description = "Head movement frames")
        private String[] headMovementFrames;
        
        @Schema(description = "Smile detection frames")
        private String[] smileFrames;
        
        @Schema(description = "Texture analysis data")
        private String textureData;
        
        @Schema(description = "3D depth map data")
        private String depthData;
        
        @Schema(description = "IR (infrared) image data")
        private String irData;
        
        @Schema(description = "Challenge response")
        private String challengeResponse;
        
        @Schema(description = "Overall liveness score")
        private Double livenessScore;
        
        @Schema(description = "Individual detection scores")
        private Map<String, Double> detectionScores;
        
        @Schema(description = "Detection confidence")
        private Double confidence;
        
        @Schema(description = "Time taken for challenge completion")
        private Integer responseTime;
        
        @Schema(description = "Environmental conditions")
        private EnvironmentalData environmental;
        
        public boolean hasEyeBlinkData() {
            return eyeBlinkFrames != null && eyeBlinkFrames.length > 0;
        }
        
        public boolean hasHeadMovementData() {
            return headMovementFrames != null && headMovementFrames.length > 0;
        }
        
        public boolean hasTextureData() {
            return textureData != null && !textureData.isEmpty();
        }
        
        public boolean hasDepthData() {
            return depthData != null && !depthData.isEmpty();
        }
        
        public boolean hasIrData() {
            return irData != null && !irData.isEmpty();
        }
    }
    
    /**
     * Environmental Data for Liveness Detection
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvironmentalData {
        
        @Schema(description = "Ambient light level")
        private Double lightLevel;
        
        @Schema(description = "Light source direction")
        private String lightDirection;
        
        @Schema(description = "Background complexity")
        private Double backgroundComplexity;
        
        @Schema(description = "Motion detected in background")
        private Boolean backgroundMotion;
        
        @Schema(description = "Multiple faces detected")
        private Boolean multipleFaces;
        
        @Schema(description = "Device orientation")
        private String deviceOrientation;
        
        @Schema(description = "Camera stability")
        private Double cameraStability;
        
        @Schema(description = "Focus quality")
        private Double focusQuality;
    }
}