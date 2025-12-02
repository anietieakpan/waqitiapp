package com.waqiti.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Device fingerprint data class
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceFingerprint implements Serializable {
    private String hash;
    private String userAgent;
    private String platform;
    private String screenResolution;
    private String timezone;
    private String colorDepth;
    private String canvas;
    private String webgl;
    private List<String> fonts;
    private Instant timestamp;
    private String ipAddress;
    private boolean isKnown;
    private double trustScore;
    private String acceptLanguage;
    private String acceptEncoding;
    private String accept;
    private String platformVersion;
    private String mobile;
    private String pixelRatio;
    private String brands;
    private String touchSupport;
    
    public String getHash() {
        return hash;
    }
    
    public void setHash(String hash) {
        this.hash = hash;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public String getPlatform() {
        return platform;
    }
    
    public String getScreenResolution() {
        return screenResolution;
    }
    
    public String getTimezone() {
        return timezone;
    }
    
    public String getColorDepth() {
        return colorDepth;
    }
    
    public String getCanvas() {
        return canvas;
    }
    
    public String getWebgl() {
        return webgl;
    }
    
    public List<String> getFonts() {
        return fonts;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("hash", hash);
        map.put("userAgent", userAgent);
        map.put("platform", platform);
        map.put("screenResolution", screenResolution);
        map.put("timezone", timezone);
        map.put("colorDepth", colorDepth);
        map.put("canvas", canvas);
        map.put("webgl", webgl);
        map.put("fonts", fonts);
        map.put("timestamp", timestamp);
        map.put("ipAddress", ipAddress);
        map.put("isKnown", isKnown);
        map.put("trustScore", trustScore);
        return map;
    }
}