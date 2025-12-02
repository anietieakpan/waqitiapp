package com.waqiti.ml.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Device analysis service for fraud detection
 * Provides device fingerprinting and behavioral analysis
 */
@Service
@Slf4j
public class DeviceAnalysisService {
    
    private final Map<String, Set<String>> userDevices = new ConcurrentHashMap<>();
    private final Map<String, DeviceInfo> deviceInfoCache = new ConcurrentHashMap<>();
    
    public DeviceInfo analyzeDevice(String deviceId, String userId) {
        DeviceInfo info = deviceInfoCache.computeIfAbsent(deviceId, this::createDeviceInfo);
        
        // Check if this is a new device for the user
        Set<String> knownDevices = userDevices.computeIfAbsent(userId, k -> new HashSet<>());
        boolean isNewDevice = !knownDevices.contains(deviceId);
        
        if (isNewDevice) {
            knownDevices.add(deviceId);
            log.info("New device detected for user {}: {}", userId, deviceId);
        }
        
        info.setNewDevice(isNewDevice);
        info.setLastSeen(LocalDateTime.now());
        
        return info;
    }
    
    private DeviceInfo createDeviceInfo(String deviceId) {
        DeviceInfo info = new DeviceInfo();
        info.setDeviceId(deviceId);
        info.setFirstSeen(LocalDateTime.now());
        info.setBrowserFingerprint(generateBrowserFingerprint(deviceId));
        info.setDeviceType(determineDeviceType(deviceId));
        info.setOperatingSystem(determineOS(deviceId));
        info.setTrusted(false); // New devices start as untrusted
        info.setRiskScore(calculateInitialRiskScore(deviceId));
        
        return info;
    }
    
    private String generateBrowserFingerprint(String deviceId) {
        // Simulate browser fingerprinting
        return "fp_" + Integer.toHexString(deviceId.hashCode());
    }
    
    private String determineDeviceType(String deviceId) {
        // Simulate device type detection
        int hash = Math.abs(deviceId.hashCode());
        String[] types = {"DESKTOP", "MOBILE", "TABLET", "TV", "WATCH"};
        return types[hash % types.length];
    }
    
    private String determineOS(String deviceId) {
        // Simulate OS detection
        int hash = Math.abs(deviceId.hashCode());
        String[] oses = {"Windows", "macOS", "Linux", "iOS", "Android", "Other"};
        return oses[hash % oses.length];
    }
    
    private double calculateInitialRiskScore(String deviceId) {
        // Calculate initial risk based on device characteristics
        double risk = 0.0;
        
        String deviceType = determineDeviceType(deviceId);
        String os = determineOS(deviceId);
        
        // Mobile devices are generally lower risk
        if ("MOBILE".equals(deviceType)) {
            risk += 0.1;
        } else if ("DESKTOP".equals(deviceType)) {
            risk += 0.2;
        } else {
            risk += 0.3; // Unusual device types are higher risk
        }
        
        // Some OS are higher risk
        if ("Linux".equals(os) || "Other".equals(os)) {
            risk += 0.2;
        }
        
        return Math.min(risk, 1.0);
    }
    
    public void updateDeviceReputation(String deviceId, boolean fraudulent) {
        DeviceInfo info = deviceInfoCache.get(deviceId);
        if (info != null) {
            if (fraudulent) {
                info.setRiskScore(Math.min(info.getRiskScore() + 0.3, 1.0));
                info.setTrusted(false);
            } else {
                info.setRiskScore(Math.max(info.getRiskScore() - 0.1, 0.0));
                if (info.getRiskScore() < 0.2) {
                    info.setTrusted(true);
                }
            }
            log.debug("Updated device reputation for {}: risk={}, trusted={}", 
                deviceId, info.getRiskScore(), info.isTrusted());
        }
    }
    
    // Data class
    public static class DeviceInfo {
        private String deviceId;
        private boolean isNewDevice;
        private String browserFingerprint;
        private String deviceType;
        private String operatingSystem;
        private boolean trusted;
        private double riskScore;
        private LocalDateTime firstSeen;
        private LocalDateTime lastSeen;
        
        // Getters and setters
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        
        public boolean isNewDevice() { return isNewDevice; }
        public void setNewDevice(boolean newDevice) { isNewDevice = newDevice; }
        
        public String getBrowserFingerprint() { return browserFingerprint; }
        public void setBrowserFingerprint(String browserFingerprint) { this.browserFingerprint = browserFingerprint; }
        
        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
        
        public String getOperatingSystem() { return operatingSystem; }
        public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
        
        public boolean isTrusted() { return trusted; }
        public void setTrusted(boolean trusted) { this.trusted = trusted; }
        
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        
        public LocalDateTime getFirstSeen() { return firstSeen; }
        public void setFirstSeen(LocalDateTime firstSeen) { this.firstSeen = firstSeen; }
        
        public LocalDateTime getLastSeen() { return lastSeen; }
        public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }
    }
}