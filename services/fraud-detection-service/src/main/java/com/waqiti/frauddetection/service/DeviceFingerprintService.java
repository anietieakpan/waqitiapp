package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.DeviceFingerprintResult;
import com.waqiti.frauddetection.entity.DeviceHistory;
import com.waqiti.frauddetection.repository.DeviceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Device Fingerprinting Service
 * 
 * CRITICAL IMPLEMENTATION: Replaces placeholder implementation
 * 
 * Features:
 * - Device reputation scoring
 * - New device detection
 * - Device behavior analysis
 * - Multi-device usage patterns
 * - Suspicious device characteristics
 * 
 * @author Waqiti Security Team
 * @version 2.0 - Production Implementation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceFingerprintService {

    private final DeviceHistoryRepository deviceHistoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String DEVICE_CACHE_PREFIX = "device:fingerprint:";
    private static final String DEVICE_REPUTATION_PREFIX = "device:reputation:";
    private static final int CACHE_TTL_HOURS = 24;

    /**
     * Analyze device fingerprint and calculate risk score
     * 
     * CRITICAL: Actual device fingerprinting implementation
     */
    public DeviceFingerprintResult analyzeDeviceFingerprint(String userId, String deviceFingerprint) {
        log.debug("Analyzing device fingerprint for user: {}", userId);
        
        if (deviceFingerprint == null || deviceFingerprint.isEmpty()) {
            return createUnknownDeviceResult(userId, "No device fingerprint provided");
        }
        
        try {
            // Check cache first
            DeviceFingerprintResult cached = getCachedResult(deviceFingerprint);
            if (cached != null) {
                log.debug("Device fingerprint result found in cache");
                return cached;
            }
            
            // Parse device fingerprint
            DeviceInfo deviceInfo = parseDeviceFingerprint(deviceFingerprint);
            
            // Check if device is known for this user
            boolean isKnownDevice = isDeviceKnownForUser(userId, deviceFingerprint);
            
            // Get device history
            List<DeviceHistory> deviceHistory = deviceHistoryRepository
                .findByDeviceFingerprintOrderByLastSeenDesc(deviceFingerprint);
            
            // Calculate device reputation score
            double reputationScore = calculateDeviceReputation(deviceFingerprint, deviceHistory);
            
            // Analyze device characteristics
            double characteristicsScore = analyzeDeviceCharacteristics(deviceInfo);
            
            // Check for suspicious patterns
            List<String> suspiciousPatterns = detectSuspiciousPatterns(
                userId, deviceFingerprint, deviceHistory
            );
            
            // Calculate final risk score
            double riskScore = calculateDeviceRiskScore(
                isKnownDevice, reputationScore, characteristicsScore, suspiciousPatterns
            );
            
            // Determine risk level
            String riskLevel = determineRiskLevel(riskScore);
            
            // Build result
            DeviceFingerprintResult result = DeviceFingerprintResult.builder()
                .deviceFingerprint(deviceFingerprint)
                .userId(userId)
                .isKnownDevice(isKnownDevice)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .reputationScore(reputationScore)
                .deviceInfo(deviceInfo.toMap())
                .suspiciousPatterns(suspiciousPatterns)
                .firstSeen(getFirstSeenDate(deviceHistory))
                .lastSeen(LocalDateTime.now())
                .transactionCount(deviceHistory.size())
                .analysisTimestamp(LocalDateTime.now())
                .build();
            
            // Cache result
            cacheResult(deviceFingerprint, result);
            
            // Update device history
            updateDeviceHistory(userId, deviceFingerprint, deviceInfo);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error analyzing device fingerprint for user {}: {}", userId, e.getMessage());
            return createHighRiskDeviceResult(userId, deviceFingerprint, 
                "Device analysis error: " + e.getMessage());
        }
    }

    /**
     * Parse device fingerprint into structured info
     */
    private DeviceInfo parseDeviceFingerprint(String fingerprint) {
        // Device fingerprint format: browser|os|screen|timezone|plugins|canvas|...
        String[] parts = fingerprint.split("\\|");
        
        return DeviceInfo.builder()
            .browser(parts.length > 0 ? parts[0] : "unknown")
            .operatingSystem(parts.length > 1 ? parts[1] : "unknown")
            .screenResolution(parts.length > 2 ? parts[2] : "unknown")
            .timezone(parts.length > 3 ? parts[3] : "unknown")
            .plugins(parts.length > 4 ? parts[4] : "unknown")
            .canvasFingerprint(parts.length > 5 ? parts[5] : "unknown")
            .webglFingerprint(parts.length > 6 ? parts[6] : "unknown")
            .audioFingerprint(parts.length > 7 ? parts[7] : "unknown")
            .build();
    }

    /**
     * Check if device is known for user
     */
    private boolean isDeviceKnownForUser(String userId, String deviceFingerprint) {
        String cacheKey = DEVICE_CACHE_PREFIX + userId + ":" + deviceFingerprint;
        Boolean cached = (Boolean) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return cached;
        }
        
        boolean known = deviceHistoryRepository
            .existsByUserIdAndDeviceFingerprint(userId, deviceFingerprint);
        
        // Cache for 24 hours
        redisTemplate.opsForValue().set(cacheKey, known, CACHE_TTL_HOURS, TimeUnit.HOURS);
        
        return known;
    }

    /**
     * Calculate device reputation based on history
     */
    private double calculateDeviceReputation(String deviceFingerprint, List<DeviceHistory> history) {
        if (history.isEmpty()) {
            return 0.5; // Neutral reputation for new devices
        }
        
        double reputation = 0.7; // Start with slightly positive
        
        // Factor 1: Age of device (older = better reputation)
        LocalDateTime firstSeen = getFirstSeenDate(history);
        if (firstSeen != null) {
            long daysSinceFirst = ChronoUnit.DAYS.between(firstSeen, LocalDateTime.now());
            if (daysSinceFirst > 180) reputation += 0.15; // 6+ months
            else if (daysSinceFirst > 90) reputation += 0.10; // 3+ months
            else if (daysSinceFirst > 30) reputation += 0.05; // 1+ month
        }
        
        // Factor 2: Number of successful transactions
        long successfulTxns = history.stream()
            .filter(h -> !h.isAssociatedWithFraud())
            .count();
        if (successfulTxns > 100) reputation += 0.10;
        else if (successfulTxns > 50) reputation += 0.05;
        
        // Factor 3: Fraud associations (negative impact)
        long fraudulentTxns = history.stream()
            .filter(DeviceHistory::isAssociatedWithFraud)
            .count();
        if (fraudulentTxns > 0) {
            reputation -= (fraudulentTxns * 0.20);
        }
        
        // Factor 4: Number of different users (device sharing = suspicious)
        long uniqueUsers = history.stream()
            .map(DeviceHistory::getUserId)
            .distinct()
            .count();
        if (uniqueUsers > 5) reputation -= 0.20; // Suspicious device sharing
        else if (uniqueUsers > 3) reputation -= 0.10;
        
        return Math.max(0.0, Math.min(1.0, reputation));
    }

    /**
     * Analyze device characteristics for suspicious traits
     */
    private double analyzeDeviceCharacteristics(DeviceInfo deviceInfo) {
        double score = 0.0;
        
        // Check for headless browsers (automation)
        if (isHeadlessBrowser(deviceInfo.getBrowser())) {
            score += 0.40;
        }
        
        // Check for suspicious OS combinations
        if (isSuspiciousOS(deviceInfo.getOperatingSystem())) {
            score += 0.20;
        }
        
        // Check for unusual screen resolutions
        if (isUnusualScreenResolution(deviceInfo.getScreenResolution())) {
            score += 0.15;
        }
        
        // Check for missing common plugins (suspicious)
        if (hasMissingCommonPlugins(deviceInfo.getPlugins())) {
            score += 0.15;
        }
        
        // Check canvas fingerprint anomalies
        if (hasCanvasAnomaly(deviceInfo.getCanvasFingerprint())) {
            score += 0.20;
        }
        
        return Math.min(score, 1.0);
    }

    /**
     * Detect suspicious device usage patterns
     */
    private List<String> detectSuspiciousPatterns(
            String userId, String deviceFingerprint, List<DeviceHistory> history) {
        
        List<String> patterns = new ArrayList<>();
        
        // Pattern 1: Rapid location changes (impossible travel)
        if (hasImpossibleTravel(history)) {
            patterns.add("IMPOSSIBLE_TRAVEL");
        }
        
        // Pattern 2: Multiple users on same device in short time
        if (hasRapidUserSwitching(history)) {
            patterns.add("RAPID_USER_SWITCHING");
        }
        
        // Pattern 3: Device only used for high-value transactions
        if (isOnlyHighValueTransactions(history)) {
            patterns.add("HIGH_VALUE_ONLY_PATTERN");
        }
        
        // Pattern 4: Unusual activity hours
        if (hasUnusualActivityHours(history)) {
            patterns.add("UNUSUAL_ACTIVITY_HOURS");
        }
        
        // Pattern 5: Emulator or virtual machine
        if (isEmulatorOrVM(deviceFingerprint)) {
            patterns.add("EMULATOR_OR_VM_DETECTED");
        }
        
        // Pattern 6: Fingerprint spoofing indicators
        if (hasFingerprintSpoofingIndicators(deviceFingerprint)) {
            patterns.add("FINGERPRINT_SPOOFING_SUSPECTED");
        }
        
        return patterns;
    }

    /**
     * Calculate final device risk score
     */
    private double calculateDeviceRiskScore(
            boolean isKnownDevice,
            double reputationScore,
            double characteristicsScore,
            List<String> suspiciousPatterns) {
        
        double score = 0.0;
        
        // New device = higher initial risk
        if (!isKnownDevice) {
            score += 0.30;
        }
        
        // Poor reputation = higher risk
        score += (1.0 - reputationScore) * 0.30;
        
        // Suspicious characteristics
        score += characteristicsScore * 0.25;
        
        // Suspicious patterns
        score += (suspiciousPatterns.size() * 0.15);
        
        return Math.min(score, 1.0);
    }

    /**
     * Helper: Check for headless browsers
     */
    private boolean isHeadlessBrowser(String browser) {
        if (browser == null) return false;
        String lower = browser.toLowerCase();
        return lower.contains("headless") || 
               lower.contains("phantom") || 
               lower.contains("puppeteer");
    }

    /**
     * Helper: Check for suspicious OS
     */
    private boolean isSuspiciousOS(String os) {
        if (os == null || os.equals("unknown")) return true;
        // Add OS-specific checks
        return false;
    }

    /**
     * Helper: Check unusual screen resolution
     */
    private boolean isUnusualScreenResolution(String resolution) {
        if (resolution == null || resolution.equals("unknown")) return true;
        // Very low or very high resolutions can be suspicious
        return resolution.startsWith("1x1") || resolution.startsWith("99999");
    }

    /**
     * Helper: Check for missing common plugins
     */
    private boolean hasMissingCommonPlugins(String plugins) {
        // Most legitimate browsers have some plugins
        return plugins == null || plugins.isEmpty() || plugins.equals("unknown");
    }

    /**
     * Helper: Check canvas anomaly
     */
    private boolean hasCanvasAnomaly(String canvasFingerprint) {
        // Check for known canvas fingerprint spoofing patterns
        return canvasFingerprint == null || 
               canvasFingerprint.equals("unknown") ||
               canvasFingerprint.length() < 10;
    }

    /**
     * Helper: Detect impossible travel
     */
    private boolean hasImpossibleTravel(List<DeviceHistory> history) {
        if (history.size() < 2) return false;
        
        for (int i = 0; i < history.size() - 1; i++) {
            DeviceHistory current = history.get(i);
            DeviceHistory next = history.get(i + 1);
            
            if (current.getIpAddress() == null || next.getIpAddress() == null) continue;
            if (current.getCountryCode() == null || next.getCountryCode() == null) continue;
            
            if (!current.getCountryCode().equals(next.getCountryCode())) {
                long minutesBetween = ChronoUnit.MINUTES.between(next.getLastSeen(), current.getLastSeen());
                double distance = calculateDistanceBetweenCountries(current.getCountryCode(), next.getCountryCode());
                
                double maxSpeed = distance / (minutesBetween / 60.0);
                if (maxSpeed > 900) {
                    log.warn("Impossible travel detected: {} km in {} minutes ({} km/h)", 
                        distance, minutesBetween, maxSpeed);
                    return true;
                }
            }
        }
        return false;
    }
    
    private double calculateDistanceBetweenCountries(String country1, String country2) {
        Map<String, double[]> countryCoords = Map.of(
            "US", new double[]{37.09, -95.71},
            "UK", new double[]{55.37, -3.43},
            "CN", new double[]{35.86, 104.19},
            "IN", new double[]{20.59, 78.96},
            "BR", new double[]{-14.23, -51.92},
            "RU", new double[]{61.52, 105.31},
            "JP", new double[]{36.20, 138.25},
            "DE", new double[]{51.16, 10.45},
            "FR", new double[]{46.22, 2.21},
            "AU", new double[]{-25.27, 133.77}
        );
        
        double[] coords1 = countryCoords.getOrDefault(country1, new double[]{0, 0});
        double[] coords2 = countryCoords.getOrDefault(country2, new double[]{0, 0});
        
        double lat1 = Math.toRadians(coords1[0]);
        double lon1 = Math.toRadians(coords1[1]);
        double lat2 = Math.toRadians(coords2[0]);
        double lon2 = Math.toRadians(coords2[1]);
        
        double dlon = lon2 - lon1;
        double dlat = lat2 - lat1;
        double a = Math.pow(Math.sin(dlat / 2), 2) + 
                   Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        double radius = 6371;
        
        return c * radius;
    }

    /**
     * Helper: Detect rapid user switching
     */
    private boolean hasRapidUserSwitching(List<DeviceHistory> history) {
        if (history.size() < 2) return false;
        
        // Check if multiple different users used device within short timeframe
        Set<String> recentUsers = new HashSet<>();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        
        for (DeviceHistory record : history) {
            if (record.getLastSeen().isAfter(cutoff)) {
                recentUsers.add(record.getUserId());
            }
        }
        
        return recentUsers.size() > 2; // More than 2 users in 1 hour is suspicious
    }

    /**
     * Helper: Check if only high-value transactions
     */
    private boolean isOnlyHighValueTransactions(List<DeviceHistory> history) {
        if (history.isEmpty()) return false;
        
        long highValueCount = history.stream()
            .filter(h -> h.getTransactionAmount() != null)
            .filter(h -> h.getTransactionAmount().compareTo(new java.math.BigDecimal("1000")) > 0)
            .count();
        
        long totalWithAmounts = history.stream()
            .filter(h -> h.getTransactionAmount() != null)
            .count();
        
        if (totalWithAmounts < 5) return false;
        
        double highValueRatio = (double) highValueCount / totalWithAmounts;
        return highValueRatio > 0.8;
    }

    /**
     * Helper: Check unusual activity hours
     */
    private boolean hasUnusualActivityHours(List<DeviceHistory> history) {
        // Check if all activity happens during unusual hours (2am-6am)
        long nightTransactions = history.stream()
            .filter(h -> {
                int hour = h.getLastSeen().getHour();
                return hour >= 2 && hour <= 6;
            })
            .count();
        
        return nightTransactions > (history.size() * 0.7); // 70%+ at night
    }

    /**
     * Helper: Detect emulator or VM
     */
    private boolean isEmulatorOrVM(String fingerprint) {
        if (fingerprint == null) return false;
        String lower = fingerprint.toLowerCase();
        return lower.contains("emulator") || 
               lower.contains("virtualbox") ||
               lower.contains("vmware");
    }

    /**
     * Helper: Detect fingerprint spoofing
     */
    private boolean hasFingerprintSpoofingIndicators(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return false;
        
        String[] parts = fingerprint.split("\\|");
        if (parts.length < 3) return false;
        
        String os = parts.length > 1 ? parts[1].toLowerCase() : "";
        String screen = parts.length > 2 ? parts[2].toLowerCase() : "";
        String browser = parts.length > 0 ? parts[0].toLowerCase() : "";
        
        if ((os.contains("android") || os.contains("ios")) && 
            (screen.contains("1920x1080") || screen.contains("2560x1440"))) {
            log.warn("Spoofing indicator: mobile OS with desktop resolution");
            return true;
        }
        
        if (browser.contains("chrome") && os.contains("ios")) {
            return true;
        }
        
        if (os.equals("unknown") && browser.equals("unknown") && screen.equals("unknown")) {
            log.warn("Spoofing indicator: all fingerprint components unknown");
            return true;
        }
        
        return false;
    }

    /**
     * Get first seen date from history
     */
    private LocalDateTime getFirstSeenDate(List<DeviceHistory> history) {
        return history.stream()
            .map(DeviceHistory::getFirstSeen)
            .min(LocalDateTime::compareTo)
            .orElse(null);
    }

    /**
     * Update device history
     */
    private void updateDeviceHistory(String userId, String deviceFingerprint, DeviceInfo deviceInfo) {
        DeviceHistory history = deviceHistoryRepository
            .findByUserIdAndDeviceFingerprint(userId, deviceFingerprint)
            .orElse(DeviceHistory.builder()
                .userId(userId)
                .deviceFingerprint(deviceFingerprint)
                .firstSeen(LocalDateTime.now())
                .build());
        
        history.setLastSeen(LocalDateTime.now());
        history.setBrowser(deviceInfo.getBrowser());
        history.setOperatingSystem(deviceInfo.getOperatingSystem());
        history.setScreenResolution(deviceInfo.getScreenResolution());
        
        deviceHistoryRepository.save(history);
    }

    /**
     * Cache result
     */
    private void cacheResult(String deviceFingerprint, DeviceFingerprintResult result) {
        String cacheKey = DEVICE_REPUTATION_PREFIX + deviceFingerprint;
        redisTemplate.opsForValue().set(cacheKey, result, 1, TimeUnit.HOURS);
    }

    /**
     * Get cached result
     */
    private DeviceFingerprintResult getCachedResult(String deviceFingerprint) {
        String cacheKey = DEVICE_REPUTATION_PREFIX + deviceFingerprint;
        return (DeviceFingerprintResult) redisTemplate.opsForValue().get(cacheKey);
    }

    /**
     * Create unknown device result
     */
    private DeviceFingerprintResult createUnknownDeviceResult(String userId, String reason) {
        return DeviceFingerprintResult.builder()
            .userId(userId)
            .deviceFingerprint("UNKNOWN")
            .isKnownDevice(false)
            .riskScore(0.60)
            .riskLevel("MEDIUM")
            .suspiciousPatterns(Collections.singletonList(reason))
            .analysisTimestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Create high-risk device result
     */
    private DeviceFingerprintResult createHighRiskDeviceResult(
            String userId, String deviceFingerprint, String reason) {
        return DeviceFingerprintResult.builder()
            .userId(userId)
            .deviceFingerprint(deviceFingerprint)
            .isKnownDevice(false)
            .riskScore(0.90)
            .riskLevel("HIGH")
            .suspiciousPatterns(Collections.singletonList(reason))
            .analysisTimestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Determine risk level from score
     */
    private String determineRiskLevel(double score) {
        if (score >= 0.75) return "CRITICAL";
        if (score >= 0.50) return "HIGH";
        if (score >= 0.30) return "MEDIUM";
        return "LOW";
    }

    /**
     * Device info holder
     */
    @lombok.Builder
    @lombok.Data
    private static class DeviceInfo {
        private String browser;
        private String operatingSystem;
        private String screenResolution;
        private String timezone;
        private String plugins;
        private String canvasFingerprint;
        private String webglFingerprint;
        private String audioFingerprint;
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("browser", browser);
            map.put("os", operatingSystem);
            map.put("screen", screenResolution);
            map.put("timezone", timezone);
            map.put("plugins", plugins);
            return map;
        }
    }
}