package com.waqiti.user.dto.security;

import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Device Information DTO
 * Contains comprehensive device and browser information for fingerprinting
 */
@Data
@Builder
public class DeviceInformation {
    private String userAgent;
    private String screenResolution;
    private String timezone;
    private List<String> languages;
    private String platform;
    private List<String> fonts;
    private String canvasFingerprint;
    private String webglInfo;
    private String ipAddress;
    private Map<String, String> headers;
    private String browserName;
    private String browserVersion;
    private String operatingSystem;
    private String deviceType;
    private Boolean cookiesEnabled;
    private Boolean javaEnabled;
    private String colorDepth;
    private String pixelRatio;
    private String touchSupport;
    private List<String> plugins;
    private String audioFingerprint;
    private Map<String, Object> additionalAttributes;
}