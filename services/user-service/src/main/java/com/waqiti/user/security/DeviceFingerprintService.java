package com.waqiti.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.user.domain.UserDevice;
import com.waqiti.user.dto.security.DeviceFingerprintData;
import com.waqiti.user.dto.security.DeviceRegistrationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Device Fingerprinting Service
 * 
 * Provides device identification and validation capabilities for enhanced security:
 * - Browser/device fingerprinting
 * - Device trust scoring
 * - Anomaly detection for new devices
 * - Device history tracking
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceFingerprintService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String DEVICE_FINGERPRINT_PREFIX = "device:fingerprint:";
    private static final String USER_DEVICES_PREFIX = "user:devices:";
    private static final int DEVICE_TRUST_DAYS = 30;
    private static final double FINGERPRINT_SIMILARITY_THRESHOLD = 0.8;

    /**
     * Generates a device fingerprint from client information
     */
    public String generateDeviceFingerprint(DeviceInformation deviceInfo) {
        try {
            // Create a composite fingerprint from various device attributes
            StringBuilder fingerprintData = new StringBuilder();
            
            // Browser information
            if (deviceInfo.getUserAgent() != null) {
                fingerprintData.append(normalizeUserAgent(deviceInfo.getUserAgent()));
            }
            
            // Screen information
            if (deviceInfo.getScreenResolution() != null) {
                fingerprintData.append("|screen:").append(deviceInfo.getScreenResolution());
            }
            
            // Timezone
            if (deviceInfo.getTimezone() != null) {
                fingerprintData.append("|tz:").append(deviceInfo.getTimezone());
            }
            
            // Language preferences
            if (deviceInfo.getLanguages() != null && !deviceInfo.getLanguages().isEmpty()) {
                fingerprintData.append("|lang:").append(String.join(",", deviceInfo.getLanguages()));
            }
            
            // Platform information
            if (deviceInfo.getPlatform() != null) {
                fingerprintData.append("|platform:").append(deviceInfo.getPlatform());
            }
            
            // Available fonts (if provided)
            if (deviceInfo.getFonts() != null && !deviceInfo.getFonts().isEmpty()) {
                fingerprintData.append("|fonts:").append(String.join(",", deviceInfo.getFonts()));
            }
            
            // Canvas fingerprint (if available)
            if (deviceInfo.getCanvasFingerprint() != null) {
                fingerprintData.append("|canvas:").append(deviceInfo.getCanvasFingerprint());
            }
            
            // WebGL information
            if (deviceInfo.getWebglInfo() != null) {
                fingerprintData.append("|webgl:").append(deviceInfo.getWebglInfo());
            }
            
            // Hash the combined fingerprint data
            return hashFingerprint(fingerprintData.toString());
            
        } catch (Exception e) {
            log.error("Failed to generate device fingerprint", e);
            return generateFallbackFingerprint(deviceInfo);
        }
    }

    /**
     * Check if a device is trusted for a user
     */
    public boolean isDeviceTrusted(DeviceFingerprintData fingerprint, String userId) {
        if (fingerprint == null || userId == null) {
            return false;
        }
        
        try {
            UUID userUuid = UUID.fromString(userId);
            List<UserDevice> userDevices = getUserDevices(userUuid);
            
            // Check if fingerprint exists and is trusted
            return userDevices.stream()
                .anyMatch(device -> device.getFingerprint().equals(fingerprint.getFingerprint()) 
                    && ("HIGH".equals(device.getTrustLevel()) || "TRUSTED".equals(device.getTrustLevel())));
        } catch (Exception e) {
            log.warn("Error checking device trust for user {}", userId, e);
            return false;
        }
    }
    
    /**
     * Validates if a device fingerprint matches expected fingerprint with tolerance
     */
    public boolean validateDeviceFingerprint(String expectedFingerprint, String actualFingerprint) {
        if (expectedFingerprint == null || actualFingerprint == null) {
            return false;
        }
        
        // Exact match
        if (expectedFingerprint.equals(actualFingerprint)) {
            return true;
        }
        
        // Check if devices are similar enough (for minor browser updates, etc.)
        try {
            DeviceFingerprintData expectedData = getStoredFingerprintData(expectedFingerprint);
            if (expectedData != null) {
                double similarity = calculateFingerprintSimilarity(expectedData, actualFingerprint);
                return similarity >= FINGERPRINT_SIMILARITY_THRESHOLD;
            }
        } catch (Exception e) {
            log.warn("Error during fingerprint similarity check", e);
        }
        
        return false;
    }

    /**
     * Registers a new device for a user
     */
    public DeviceRegistrationResult registerDevice(UUID userId, String deviceFingerprint, 
                                                  DeviceInformation deviceInfo, String ipAddress) {
        try {
            String deviceKey = DEVICE_FINGERPRINT_PREFIX + deviceFingerprint;
            String userDevicesKey = USER_DEVICES_PREFIX + userId.toString();
            
            // Check if device is already registered
            DeviceFingerprintData existingDevice = (DeviceFingerprintData) redisTemplate.opsForValue().get(deviceKey);
            
            if (existingDevice != null) {
                // Update last seen
                existingDevice.setLastSeen(LocalDateTime.now());
                existingDevice.setLastIpAddress(ipAddress);
                redisTemplate.opsForValue().set(deviceKey, existingDevice, DEVICE_TRUST_DAYS, TimeUnit.DAYS);
                
                return DeviceRegistrationResult.builder()
                    .deviceFingerprint(deviceFingerprint)
                    .isNewDevice(false)
                    .trustLevel(existingDevice.getTrustLevel())
                    .build();
            }
            
            // Register new device
            // Convert DeviceInformation to Map<String, Object>
            Map<String, Object> deviceInfoMap = convertDeviceInfoToMap(deviceInfo);
            
            DeviceFingerprintData deviceData = DeviceFingerprintData.builder()
                .fingerprint(deviceFingerprint)
                .userId(userId.toString())
                .deviceInfo(deviceInfoMap)
                .firstSeen(LocalDateTime.now())
                .lastSeen(LocalDateTime.now())
                .firstIpAddress(ipAddress)
                .lastIpAddress(ipAddress)
                .trustLevel(calculateInitialTrustLevel(userId, deviceInfoMap, ipAddress))
                .isVerified(false)
                .build();
            
            // Store device data
            redisTemplate.opsForValue().set(deviceKey, deviceData, DEVICE_TRUST_DAYS, TimeUnit.DAYS);
            
            // Add to user's device list
            redisTemplate.opsForSet().add(userDevicesKey, deviceFingerprint);
            redisTemplate.expire(userDevicesKey, DEVICE_TRUST_DAYS, TimeUnit.DAYS);
            
            log.info("Registered new device for user {}: {}", userId, deviceFingerprint);
            
            return DeviceRegistrationResult.builder()
                .deviceFingerprint(deviceFingerprint)
                .isNewDevice(true)
                .trustLevel(deviceData.getTrustLevel())
                .requiresVerification(true)
                .build();
            
        } catch (Exception e) {
            log.error("Failed to register device for user {}", userId, e);
            throw new SecurityException("Device registration failed", e);
        }
    }

    /**
     * Gets all devices for a user
     */
    public List<UserDevice> getUserDevices(UUID userId) {
        try {
            String userDevicesKey = USER_DEVICES_PREFIX + userId.toString();
            Set<Object> deviceFingerprints = redisTemplate.opsForSet().members(userDevicesKey);
            
            if (deviceFingerprints == null || deviceFingerprints.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<UserDevice> devices = new ArrayList<>();
            
            for (Object fingerprintObj : deviceFingerprints) {
                String fingerprint = (String) fingerprintObj;
                DeviceFingerprintData deviceData = getStoredFingerprintData(fingerprint);
                
                if (deviceData != null) {
                    devices.add(UserDevice.builder()
                        .fingerprint(fingerprint)
                        .deviceInfo(deviceData.getDeviceInfo())
                        .firstSeenAt(deviceData.getFirstSeen())
                        .lastUsedAt(deviceData.getLastSeen())
                        .trustLevel(deviceData.getTrustLevel())
                        .isVerified(deviceData.getIsVerified())
                        .build());
                }
            }
            
            // Sort by last seen (most recent first)
            devices.sort((a, b) -> b.getLastSeen().compareTo(a.getLastSeen()));
            
            return devices;
            
        } catch (Exception e) {
            log.error("Failed to get devices for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Verifies a device as trusted
     */
    public boolean verifyDevice(UUID userId, String deviceFingerprint) {
        try {
            String deviceKey = DEVICE_FINGERPRINT_PREFIX + deviceFingerprint;
            DeviceFingerprintData deviceData = getStoredFingerprintData(deviceFingerprint);
            
            if (deviceData == null || !deviceData.getUserId().equals(userId)) {
                return false;
            }
            
            deviceData.setIsVerified(true);
            deviceData.setVerifiedAt(LocalDateTime.now());
            
            // Upgrade trust level
            String currentLevel = deviceData.getTrustLevel();
            String newLevel = upgradeTrustLevel(currentLevel);
            deviceData.setTrustLevel(newLevel);
            
            redisTemplate.opsForValue().set(deviceKey, deviceData, DEVICE_TRUST_DAYS, TimeUnit.DAYS);
            
            log.info("Verified device for user {}: {}", userId, deviceFingerprint);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to verify device {} for user {}", deviceFingerprint, userId, e);
            return false;
        }
    }

    /**
     * Removes a device from user's trusted devices
     */
    public boolean removeDevice(UUID userId, String deviceFingerprint) {
        try {
            String deviceKey = DEVICE_FINGERPRINT_PREFIX + deviceFingerprint;
            String userDevicesKey = USER_DEVICES_PREFIX + userId.toString();
            
            // Verify ownership
            DeviceFingerprintData deviceData = getStoredFingerprintData(deviceFingerprint);
            if (deviceData == null || !deviceData.getUserId().equals(userId)) {
                return false;
            }
            
            // Remove device data and from user's device list
            redisTemplate.delete(deviceKey);
            redisTemplate.opsForSet().remove(userDevicesKey, deviceFingerprint);
            
            log.info("Removed device for user {}: {}", userId, deviceFingerprint);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to remove device {} for user {}", deviceFingerprint, userId, e);
            return false;
        }
    }

    /**
     * Calculates device trust score based on various factors
     */
    public double calculateDeviceTrustScore(UUID userId, String deviceFingerprint) {
        try {
            DeviceFingerprintData deviceData = getStoredFingerprintData(deviceFingerprint);
            if (deviceData == null) {
                return 0.0;
            }
            
            // Convert trust level string to numeric score
            double trustScore = convertTrustLevelToScore(deviceData.getTrustLevel());
            
            // Increase trust based on device age
            long daysSinceFirstSeen = java.time.Duration.between(
                deviceData.getFirstSeen(), LocalDateTime.now()).toDays();
            
            if (daysSinceFirstSeen > 7) {
                trustScore += 0.1;
            }
            if (daysSinceFirstSeen > 30) {
                trustScore += 0.1;
            }
            
            // Increase trust if verified
            if (deviceData.getIsVerified() != null && deviceData.getIsVerified()) {
                trustScore += 0.2;
            }
            
            // Decrease trust if not seen recently
            long daysSinceLastSeen = java.time.Duration.between(
                deviceData.getLastSeen(), LocalDateTime.now()).toDays();
            
            if (daysSinceLastSeen > 30) {
                trustScore -= 0.2;
            }
            
            return Math.max(0.0, Math.min(1.0, trustScore));
            
        } catch (Exception e) {
            log.error("Failed to calculate device trust score", e);
            return 0.0;
        }
    }

    // Private helper methods

    private String normalizeUserAgent(String userAgent) {
        // Remove version numbers and specific build information to reduce fingerprint volatility
        return userAgent.replaceAll("\\d+\\.\\d+\\.\\d+", "x.x.x")
                       .replaceAll("\\([^)]*\\)", "()");
    }

    private String hashFingerprint(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to hash fingerprint", e);
            return String.valueOf(data.hashCode());
        }
    }

    private String generateFallbackFingerprint(DeviceInformation deviceInfo) {
        // Simple fallback based on user agent only
        String userAgent = deviceInfo.getUserAgent() != null ? deviceInfo.getUserAgent() : "unknown";
        return hashFingerprint("fallback:" + userAgent);
    }

    private DeviceFingerprintData getStoredFingerprintData(String fingerprint) {
        try {
            String deviceKey = DEVICE_FINGERPRINT_PREFIX + fingerprint;
            return (DeviceFingerprintData) redisTemplate.opsForValue().get(deviceKey);
        } catch (Exception e) {
            log.warn("Failed to get stored fingerprint data for {}", fingerprint, e);
            return null;
        }
    }

    private double calculateFingerprintSimilarity(DeviceFingerprintData stored, String actualFingerprint) {
        // Simplified similarity calculation
        // In production, this would be more sophisticated
        return stored.getFingerprint().equals(actualFingerprint) ? 1.0 : 0.0;
    }

    private double calculateInitialTrustLevel(UUID userId, DeviceInformation deviceInfo, String ipAddress) {
        double trustLevel = 0.3; // Base trust level for new devices
        
        // Increase trust for complete device information
        if (deviceInfo.getScreenResolution() != null) trustLevel += 0.1;
        if (deviceInfo.getTimezone() != null) trustLevel += 0.1;
        if (deviceInfo.getLanguages() != null && !deviceInfo.getLanguages().isEmpty()) trustLevel += 0.1;
        
        // Additional factors could include:
        // - IP reputation
        // - Geolocation consistency
        // - Browser security features
        
        return Math.min(trustLevel, 0.7); // Cap initial trust
    }

    // Data classes

    public static class DeviceInformation {
        private final String userAgent;
        private final String screenResolution;
        private final String timezone;
        private final List<String> languages;
        private final String platform;
        private final List<String> fonts;
        private final String canvasFingerprint;
        private final String webglInfo;

        public DeviceInformation(String userAgent, String screenResolution, String timezone,
                               List<String> languages, String platform, List<String> fonts,
                               String canvasFingerprint, String webglInfo) {
            this.userAgent = userAgent;
            this.screenResolution = screenResolution;
            this.timezone = timezone;
            this.languages = languages;
            this.platform = platform;
            this.fonts = fonts;
            this.canvasFingerprint = canvasFingerprint;
            this.webglInfo = webglInfo;
        }

        // Getters
        public String getUserAgent() { return userAgent; }
        public String getScreenResolution() { return screenResolution; }
        public String getTimezone() { return timezone; }
        public List<String> getLanguages() { return languages; }
        public String getPlatform() { return platform; }
        public List<String> getFonts() { return fonts; }
        public String getCanvasFingerprint() { return canvasFingerprint; }
        public String getWebglInfo() { return webglInfo; }
    }

    // Additional data classes would use Lombok @Data/@Builder annotations
    // Simplified for brevity
    
    /**
     * Upgrade trust level based on current level
     */
    private String upgradeTrustLevel(String currentLevel) {
        if (currentLevel == null) return "LOW";
        
        return switch (currentLevel) {
            case "UNTRUSTED" -> "LOW";
            case "LOW" -> "MEDIUM";
            case "MEDIUM" -> "HIGH";
            case "HIGH" -> "TRUSTED";
            case "TRUSTED" -> "TRUSTED";
            default -> "LOW";
        };
    }
    
    /**
     * Calculate initial trust level for a new device
     */
    private String calculateInitialTrustLevel(UUID userId, Map<String, Object> deviceInfo, String ipAddress) {
        // Check various risk factors
        int riskScore = 0;
        
        // Check if VPN/Proxy detected
        if (deviceInfo != null) {
            Boolean vpnDetected = (Boolean) deviceInfo.get("vpnDetected");
            Boolean proxyDetected = (Boolean) deviceInfo.get("proxyDetected");
            if (Boolean.TRUE.equals(vpnDetected) || Boolean.TRUE.equals(proxyDetected)) {
                riskScore += 2;
            }
        }
        
        // Check device type
        String deviceType = (String) (deviceInfo != null ? deviceInfo.get("deviceType") : null);
        if ("MOBILE".equals(deviceType)) {
            riskScore -= 1; // Mobile devices are generally more trusted
        }
        
        // Check if user has other trusted devices
        List<UserDevice> existingDevices = getUserDevices(userId);
        long trustedDeviceCount = existingDevices.stream()
            .filter(d -> "TRUSTED".equals(d.getTrustLevel()) || "HIGH".equals(d.getTrustLevel()))
            .count();
        
        if (trustedDeviceCount > 0) {
            riskScore -= 1; // User has established trust
        }
        
        // Determine initial trust level
        if (riskScore >= 2) return "UNTRUSTED";
        if (riskScore >= 1) return "LOW";
        if (riskScore <= -1) return "MEDIUM";
        return "LOW";
    }
    
    /**
     * Convert DeviceInformation object to Map for storage
     */
    private Map<String, Object> convertDeviceInfoToMap(DeviceInformation deviceInfo) {
        Map<String, Object> map = new HashMap<>();
        
        if (deviceInfo == null) {
            return map;
        }
        
        // Add device information to map
        if (deviceInfo.getUserAgent() != null) {
            map.put("userAgent", deviceInfo.getUserAgent());
        }
        if (deviceInfo.getScreenResolution() != null) {
            map.put("screenResolution", deviceInfo.getScreenResolution());
        }
        if (deviceInfo.getTimezone() != null) {
            map.put("timezone", deviceInfo.getTimezone());
        }
        if (deviceInfo.getLanguages() != null) {
            map.put("languages", deviceInfo.getLanguages());
        }
        if (deviceInfo.getPlatform() != null) {
            map.put("platform", deviceInfo.getPlatform());
        }
        if (deviceInfo.getFonts() != null) {
            map.put("fonts", deviceInfo.getFonts());
        }
        if (deviceInfo.getCanvasFingerprint() != null) {
            map.put("canvasFingerprint", deviceInfo.getCanvasFingerprint());
        }
        if (deviceInfo.getWebglInfo() != null) {
            map.put("webglInfo", deviceInfo.getWebglInfo());
        }
        
        // Derive device type from user agent
        String userAgent = deviceInfo.getUserAgent();
        if (userAgent != null) {
            if (userAgent.toLowerCase().contains("mobile") || userAgent.toLowerCase().contains("android") || userAgent.toLowerCase().contains("iphone")) {
                map.put("deviceType", "MOBILE");
            } else if (userAgent.toLowerCase().contains("tablet") || userAgent.toLowerCase().contains("ipad")) {
                map.put("deviceType", "TABLET");
            } else {
                map.put("deviceType", "DESKTOP");
            }
        }
        
        // Add VPN/Proxy detection placeholders (would be filled by actual detection logic)
        map.put("vpnDetected", false);
        map.put("proxyDetected", false);
        
        return map;
    }
    
    /**
     * Convert trust level string to numeric score
     */
    private double convertTrustLevelToScore(String trustLevel) {
        if (trustLevel == null) return 0.0;
        
        return switch (trustLevel) {
            case "TRUSTED" -> 1.0;
            case "HIGH" -> 0.8;
            case "MEDIUM" -> 0.6;
            case "LOW" -> 0.3;
            case "UNTRUSTED" -> 0.0;
            default -> 0.0;
        };
    }
}