package com.waqiti.frauddetection.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive Device Fingerprint Domain Object
 * 
 * Contains detailed device characteristics for fraud detection,
 * device tracking, and behavioral analysis.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceFingerprint implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Core fingerprint
    private String fingerprintId;      // Unique fingerprint identifier
    private String fingerprintHash;    // SHA-256 hash of device characteristics
    private String userId;             // Associated user ID
    private String sessionId;         // Current session ID
    
    // Browser/Application information
    private String userAgent;         // Complete user agent string
    private String browserName;       // Browser name (Chrome, Firefox, etc.)
    private String browserVersion;    // Browser version
    private String engineName;        // Rendering engine (Blink, Gecko, etc.)
    private String engineVersion;     // Engine version
    private String platform;          // Platform (Win32, MacIntel, etc.)
    private String language;          // Browser language
    private Set<String> languages;    // All supported languages
    private String cookieEnabled;     // Cookie support status
    private String doNotTrack;        // Do Not Track setting
    
    // Screen and display
    private Integer screenWidth;      // Screen width in pixels
    private Integer screenHeight;     // Screen height in pixels
    private Integer availWidth;       // Available screen width
    private Integer availHeight;      // Available screen height
    private Integer colorDepth;       // Color depth
    private Integer pixelDepth;       // Pixel depth
    private Double devicePixelRatio;  // Device pixel ratio
    private String orientation;       // Screen orientation
    
    // Hardware characteristics
    private Integer hardwareConcurrency; // Number of CPU cores
    private Long deviceMemory;        // Device memory in GB
    private String cpuClass;          // CPU class
    private String oscpu;             // OS CPU architecture
    private List<String> plugins;     // Installed plugins
    private Map<String, String> mimeTypes; // Supported MIME types
    
    // Network and connectivity
    private String connectionType;    // Connection type (wifi, cellular, etc.)
    private String effectiveType;     // Effective connection type (4g, 3g, etc.)
    private Double downlink;          // Downlink speed estimate
    private Integer rtt;              // Round-trip time
    private Boolean saveData;         // Data saver mode
    
    // Location and timezone
    private String timezone;          // Timezone
    private Integer timezoneOffset;   // Timezone offset in minutes
    private Boolean geolocationEnabled; // Geolocation API availability
    
    // Security and capabilities
    private Boolean webglEnabled;     // WebGL support
    private String webglVendor;       // WebGL vendor
    private String webglRenderer;     // WebGL renderer
    private Boolean touchSupport;     // Touch support
    private Integer maxTouchPoints;   // Maximum touch points
    private Boolean accelerometer;    // Accelerometer support
    private Boolean gyroscope;        // Gyroscope support
    private Boolean magnetometer;     // Magnetometer support
    
    // Canvas and WebRTC fingerprinting
    private String canvasFingerprint; // Canvas rendering fingerprint
    private String webrtcFingerprint; // WebRTC fingerprint
    private String audioFingerprint;  // Audio context fingerprint
    private String fontsFingerprint;  // Available fonts fingerprint
    
    // Mobile-specific
    private String deviceModel;       // Mobile device model
    private String deviceBrand;       // Mobile device brand
    private String osName;            // Operating system name
    private String osVersion;         // Operating system version
    private String appVersion;        // Application version
    private String appBuildNumber;    // Application build number
    private Boolean isJailbroken;     // Device jailbreak/root status
    private Boolean isEmulator;       // Running on emulator
    private Boolean isDebugMode;      // Debug mode enabled
    
    // Behavioral characteristics
    private Map<String, Double> typingPatterns;    // Typing rhythm patterns
    private Map<String, Double> mouseMovements;   // Mouse movement patterns
    private Map<String, Double> touchPatterns;    // Touch gesture patterns
    private List<String> clickPatterns;           // Click behavior patterns
    private Map<String, Integer> keyboardLayout;  // Keyboard layout characteristics
    
    // Temporal information
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime firstSeen;   // First time this fingerprint was seen
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSeen;    // Last time this fingerprint was seen
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;   // When fingerprint was created
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;   // When fingerprint was last updated
    
    // Risk and trust metrics
    private Double riskScore;          // Overall risk score (0.0-1.0)
    private Double trustScore;         // Trust score based on history (0.0-1.0)
    private Double uniquenessScore;    // Fingerprint uniqueness (0.0-1.0)
    private Double stabilityScore;     // Fingerprint stability over time (0.0-1.0)
    private Integer sessionCount;      // Number of sessions with this fingerprint
    private Integer fraudAttempts;     // Number of fraud attempts
    private Integer successfulAuth;    // Successful authentications
    private Integer failedAuth;        // Failed authentications
    
    // Device classification
    private String deviceType;         // mobile, tablet, desktop, bot
    private String deviceCategory;     // premium, standard, budget
    private Boolean isBot;             // Automated/bot traffic
    private Boolean isHeadless;        // Headless browser
    private Boolean isSuspicious;      // Flagged as suspicious
    private Boolean isBlocked;         // Device is blocked
    private String blockReason;        // Reason for blocking
    
    // Geolocation history
    private List<String> locationHistory; // Recent location codes
    private String currentLocation;    // Current location code
    private Integer locationChanges;   // Number of location changes
    
    // Compliance and privacy
    private Boolean trackingConsent;   // User tracking consent
    private Boolean dataProcessingConsent; // Data processing consent
    private String privacySettings;    // Privacy configuration
    private List<String> gdprFlags;    // GDPR compliance flags
    
    // Custom metadata
    private Map<String, Object> metadata; // Additional custom data
    private Map<String, String> tags;     // Custom tags for categorization
    private Map<String, LocalDateTime> events; // Important events timeline
    
    /**
     * Calculate fingerprint stability score based on characteristic changes
     */
    public double calculateStabilityScore(DeviceFingerprint previous) {
        if (previous == null) return 1.0;
        
        int totalCharacteristics = 0;
        int changedCharacteristics = 0;
        
        // Compare core characteristics
        totalCharacteristics++;
        if (!equals(this.userAgent, previous.userAgent)) changedCharacteristics++;
        
        totalCharacteristics++;
        if (!equals(this.screenWidth, previous.screenWidth)) changedCharacteristics++;
        
        totalCharacteristics++;
        if (!equals(this.screenHeight, previous.screenHeight)) changedCharacteristics++;
        
        totalCharacteristics++;
        if (!equals(this.timezone, previous.timezone)) changedCharacteristics++;
        
        totalCharacteristics++;
        if (!equals(this.language, previous.language)) changedCharacteristics++;
        
        totalCharacteristics++;
        if (!equals(this.platform, previous.platform)) changedCharacteristics++;
        
        // Calculate stability (fewer changes = higher stability)
        return 1.0 - ((double) changedCharacteristics / totalCharacteristics);
    }
    
    /**
     * Check if this device fingerprint represents a high-risk device
     */
    public boolean isHighRisk() {
        return Boolean.TRUE.equals(isBot) ||
               Boolean.TRUE.equals(isHeadless) ||
               Boolean.TRUE.equals(isSuspicious) ||
               Boolean.TRUE.equals(isJailbroken) ||
               Boolean.TRUE.equals(isEmulator) ||
               (riskScore != null && riskScore > 0.7) ||
               (fraudAttempts != null && fraudAttempts > 2);
    }
    
    /**
     * Check if this device can be trusted based on history
     */
    public boolean isTrusted() {
        return !isHighRisk() &&
               (trustScore != null && trustScore > 0.7) &&
               (sessionCount != null && sessionCount >= 5) &&
               (successfulAuth != null && successfulAuth > failedAuth);
    }
    
    /**
     * Generate a unique fingerprint hash from device characteristics
     */
    public String generateFingerprintHash() {
        StringBuilder sb = new StringBuilder();
        
        // Core identifying characteristics
        sb.append(nullSafe(userAgent));
        sb.append(nullSafe(screenWidth));
        sb.append(nullSafe(screenHeight));
        sb.append(nullSafe(colorDepth));
        sb.append(nullSafe(timezone));
        sb.append(nullSafe(language));
        sb.append(nullSafe(platform));
        sb.append(nullSafe(plugins));
        sb.append(nullSafe(canvasFingerprint));
        sb.append(nullSafe(webglRenderer));
        sb.append(nullSafe(audioFingerprint));
        
        // Generate SHA-256 hash
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // Fallback to simple hash
            return String.valueOf(sb.toString().hashCode());
        }
    }
    
    /**
     * Check if device characteristics suggest automation/bot
     */
    public boolean hasAutomationSignatures() {
        if (userAgent != null) {
            String ua = userAgent.toLowerCase();
            if (ua.contains("selenium") || ua.contains("webdriver") || 
                ua.contains("phantom") || ua.contains("headless") ||
                ua.contains("bot") || ua.contains("crawler")) {
                return true;
            }
        }
        
        // Check for suspicious characteristics
        return Boolean.TRUE.equals(isHeadless) ||
               (webglRenderer != null && webglRenderer.contains("SwiftShader")) ||
               (plugins != null && plugins.isEmpty()) || // No plugins is suspicious
               (languages != null && languages.isEmpty()); // No languages is suspicious
    }
    
    /**
     * Get device risk factors as human-readable list
     */
    public List<String> getRiskFactors() {
        List<String> factors = new java.util.ArrayList<>();
        
        if (Boolean.TRUE.equals(isBot)) factors.add("Automated/Bot Traffic");
        if (Boolean.TRUE.equals(isHeadless)) factors.add("Headless Browser");
        if (Boolean.TRUE.equals(isJailbroken)) factors.add("Jailbroken/Rooted Device");
        if (Boolean.TRUE.equals(isEmulator)) factors.add("Running on Emulator");
        if (hasAutomationSignatures()) factors.add("Automation Signatures Detected");
        if (fraudAttempts != null && fraudAttempts > 0) {
            factors.add("Previous Fraud Attempts: " + fraudAttempts);
        }
        if (locationChanges != null && locationChanges > 5) {
            factors.add("Frequent Location Changes: " + locationChanges);
        }
        
        return factors;
    }
    
    /**
     * Create a masked version for logging (removes sensitive data)
     */
    public DeviceFingerprint createMaskedVersion() {
        return DeviceFingerprint.builder()
            .fingerprintId(this.fingerprintId)
            .fingerprintHash(this.fingerprintHash)
            .deviceType(this.deviceType)
            .browserName(this.browserName)
            .browserVersion(this.browserVersion)
            .platform(this.platform)
            .isBot(this.isBot)
            .isHeadless(this.isHeadless)
            .isSuspicious(this.isSuspicious)
            .riskScore(this.riskScore)
            .trustScore(this.trustScore)
            .sessionCount(this.sessionCount)
            .createdAt(this.createdAt)
            .lastSeen(this.lastSeen)
            .build();
    }
    
    // Helper methods
    private String nullSafe(Object obj) {
        return obj != null ? obj.toString() : "";
    }
    
    private boolean equals(Object a, Object b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }
}