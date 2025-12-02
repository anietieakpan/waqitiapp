package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Device fingerprint and risk profile
 * Comprehensive device identification and risk assessment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceProfile {

    private String deviceId;
    private String deviceFingerprint; // Hash of device attributes
    private Instant firstSeen;
    private Instant lastSeen;

    // Device type
    private String deviceType; // DESKTOP, MOBILE, TABLET, UNKNOWN
    private String operatingSystem;
    private String osVersion;
    private String browser;
    private String browserVersion;

    // Hardware
    private String screenResolution;
    private Integer screenWidth;
    private Integer screenHeight;
    private Integer colorDepth;
    private String timezone;
    private String language;
    private List<String> languages;

    // Software
    private List<String> installedFonts;
    private List<String> plugins;
    private Boolean cookiesEnabled;
    private Boolean doNotTrack;
    private String userAgent;

    // Advanced fingerprinting
    private String canvasFingerprint;
    private String webGLFingerprint;
    private String audioFingerprint;
    private String batteryFingerprint;

    // Network
    private String ipAddress;
    private String ipCountry;
    private String ipCity;
    private Boolean vpnDetected;
    private Boolean proxyDetected;
    private Boolean torDetected;
    private String isp;
    private String connectionType; // WIFI, CELLULAR, ETHERNET

    // Trust indicators
    private Boolean trustedDevice;
    private Integer trustScore; // 0-100
    private Instant trustedSince;
    private Integer transactionCount;
    private Integer successfulTransactions;
    private Integer failedTransactions;

    // Risk indicators
    private Boolean newDevice;
    private Boolean deviceShared; // Multiple users
    private Boolean deviceRooted; // Jailbroken/Rooted
    private Boolean emulatorDetected;
    private Boolean suspiciousActivity;

    // Geo-location
    private String gpsLocation; // From device GPS
    private String ipLocation; // From IP geolocation
    private Boolean locationMismatch;
    private Double travelSpeed; // km/h from last transaction

    // Device changes
    private Boolean osVersionChanged;
    private Boolean browserChanged;
    private Boolean screenResolutionChanged;
    private List<String> recentChanges;

    // Risk score
    private Double deviceRiskScore; // 0.0 to 1.0
    private String riskLevel; // LOW, MEDIUM, HIGH
    private List<String> riskFactors;

    private Map<String, Object> customAttributes;
}
