package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive device fingerprint for fraud detection
 * Contains hardware, software, and behavioral characteristics
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class DeviceFingerprint {
    
    // Core device identification
    private String fingerprint;
    private String deviceId;
    private String sessionId;
    
    // Hardware characteristics
    private String userAgent;
    private String screenResolution;
    private String colorDepth;
    private String timezone;
    private String language;
    private List<String> languages;
    private String platform;
    private String hardwareConcurrency;
    private String maxTouchPoints;
    
    // Browser/Software characteristics
    private String browserName;
    private String browserVersion;
    private String engineName;
    private String engineVersion;
    private String osName;
    private String osVersion;
    private String deviceType;
    private String deviceModel;
    private String deviceVendor;
    
    // Network characteristics
    private String ipAddress;
    private String connectionType;
    private String effectiveConnectionType;
    private Double downlink;
    private String userAgentData;
    
    // Canvas and WebGL fingerprinting
    private String canvasFingerprint;
    private String webglFingerprint;
    private String webglVendor;
    private String webglRenderer;
    
    // Audio fingerprinting
    private String audioFingerprint;
    private String audioContext;
    
    // Font detection
    private List<String> installedFonts;
    private String fontFingerprint;
    
    // Plugin detection
    private List<String> plugins;
    private List<String> mimeTypes;
    
    // Storage and capabilities
    private Boolean cookiesEnabled;
    private Boolean localStorageEnabled;
    private Boolean sessionStorageEnabled;
    private Boolean indexedDBEnabled;
    private Boolean webSQLEnabled;
    
    // Battery API (if available)
    private Double batteryLevel;
    private Boolean batteryCharging;
    private Double chargingTime;
    private Double dischargingTime;
    
    // Geolocation (if permitted)
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    
    // Touch and interaction patterns
    private String touchSupport;
    private String pointerType;
    private String inputDeviceCapabilities;
    
    // Performance characteristics
    private String performanceFingerprint;
    private Long memorySize;
    private Integer cpuClass;
    
    // Advanced fingerprinting
    private String accelerometer;
    private String gyroscope;
    private String magnetometer;
    private String proximitySupport;
    private String ambientLightSupport;
    
    // Behavioral characteristics
    private Map<String, Object> mouseBehavior;
    private Map<String, Object> keyboardBehavior;
    private Map<String, Object> touchBehavior;
    private Map<String, Object> scrollBehavior;
    
    // Risk indicators
    private Boolean isIncognito;
    private Boolean isHeadless;
    private Boolean isSuspiciousEnvironment;
    private Boolean hasVirtualMachine;
    private Boolean hasAutomationTools;
    private Boolean hasProxyIndicators;
    
    // Metadata
    private LocalDateTime capturedAt;
    private String captureMethod;
    private String fingerprintVersion;
    private Double confidence;
    private Map<String, String> additionalAttributes;
    
    /**
     * Calculate fingerprint uniqueness score
     */
    public double calculateUniqueness() {
        double score = 0.0;
        
        // Weight different fingerprint components
        if (canvasFingerprint != null) score += 0.3;
        if (webglFingerprint != null) score += 0.25;
        if (audioFingerprint != null) score += 0.2;
        if (fontFingerprint != null) score += 0.15;
        if (installedFonts != null && !installedFonts.isEmpty()) score += 0.1;
        
        return Math.min(1.0, score);
    }
    
    /**
     * Detect if device shows signs of automation/bot activity
     */
    public boolean isLikelyBot() {
        return hasAutomationTools != null && hasAutomationTools ||
               isHeadless != null && isHeadless ||
               isSuspiciousEnvironment != null && isSuspiciousEnvironment ||
               (userAgent != null && userAgent.toLowerCase().contains("bot"));
    }
    
    /**
     * Check if fingerprint indicates high-risk environment
     */
    public boolean isHighRisk() {
        return isLikelyBot() ||
               (hasVirtualMachine != null && hasVirtualMachine) ||
               (hasProxyIndicators != null && hasProxyIndicators) ||
               (confidence != null && confidence < 0.5);
    }
}