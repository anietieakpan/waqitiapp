package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Device Fingerprint Data DTO
 * 
 * Contains device-specific information used for device fingerprinting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceFingerprintData {
    
    // Browser information
    private String userAgent;
    private String language;
    private String languages;
    private String platform;
    private String browserName;
    private String browserVersion;
    private String engineName;
    private String engineVersion;
    
    // Screen information
    private Integer screenWidth;
    private Integer screenHeight;
    private Integer colorDepth;
    private Integer pixelRatio;
    
    // Device capabilities
    private Boolean touchSupport;
    private Boolean cookieEnabled;
    private Boolean localStorageEnabled;
    private Boolean sessionStorageEnabled;
    private Boolean indexedDbEnabled;
    private Boolean webGlEnabled;
    
    // Hardware information
    private Integer hardwareConcurrency;
    private Long maxTouchPoints;
    private String deviceMemory;
    
    // Timezone and locale
    private String timezone;
    private Integer timezoneOffset;
    private String locale;
    
    // Network information
    private String connectionType;
    private String effectiveType;
    private Double downlink;
    private Integer rtt;
    
    // Canvas fingerprint
    private String canvasFingerprint;
    
    // WebGL fingerprint
    private String webGlRenderer;
    private String webGlVendor;
    private String webGlVersion;
    
    // Audio fingerprint
    private String audioFingerprint;
    
    // Font detection
    private String availableFonts;
    
    // Plugins and extensions
    private String plugins;
    private String extensions;
    
    // Media device information
    private Integer audioInputs;
    private Integer audioOutputs;
    private Integer videoInputs;
    
    // Battery information (if available)
    private Boolean batteryCharging;
    private Double batteryLevel;
    private Double chargingTime;
    private Double dischargingTime;
    
    // Permissions
    private Map<String, String> permissions;
    
    // Additional fingerprint data
    private Map<String, Object> additionalData;
    
    // Composite fingerprint hash
    private String fingerprintHash;
    
    // Confidence score (0.0 to 1.0)
    private Double confidenceScore;
    
    // Device type classification
    private String deviceType; // MOBILE, DESKTOP, TABLET, OTHER
    
    // Bot detection signals
    private Boolean likelyBot;
    private String botDetectionReason;
    
    // Risk indicators
    private Boolean vpnDetected;
    private Boolean proxyDetected;
    private Boolean torDetected;
    private Boolean emulatorDetected;
    
    // Location information (if available)
    private Double latitude;
    private Double longitude;
    private String country;
    private String city;
    private String isp;
    
    // Device tracking and trust
    private String fingerprint;
    private String userId;
    private Map<String, Object> deviceInfo;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private String firstIpAddress;
    private String lastIpAddress;
    private String trustLevel; // UNTRUSTED, LOW, MEDIUM, HIGH, TRUSTED
    private Boolean isVerified;
    private LocalDateTime verifiedAt;
    private String verificationMethod;
    
    // Security tracking
    private Integer loginCount;
    private Integer failedLoginCount;
    private LocalDateTime lastFailedLogin;
    private Boolean suspicious;
    private String suspiciousReason;
    private LocalDateTime suspiciousDetectedAt;
    
    // Device lifecycle
    private Boolean active;
    private LocalDateTime deactivatedAt;
    private String deactivationReason;
    private LocalDateTime lastActivityAt;
    
    // Risk scoring
    private Double riskScore;
    private Map<String, Double> riskComponents;
    private LocalDateTime riskAssessedAt;
    
    // Behavioral patterns
    private Map<String, Object> behavioralData;
    private String usagePattern;
    private Double consistencyScore;
}