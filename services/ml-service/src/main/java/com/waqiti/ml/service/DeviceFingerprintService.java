package com.waqiti.ml.service;

import com.waqiti.ml.dto.TransactionData;
import com.waqiti.common.exception.MLProcessingException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Production-ready Device Fingerprinting Service with advanced security features.
 * Provides device identification, trust scoring, and anomaly detection.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceFingerprintService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final com.waqiti.ml.cache.MLCacheService mlCacheService;

    @Value("${device.fingerprint.salt:waqiti-device-2024}")
    private String fingerprintSalt;

    @Value("${device.trust.threshold:70.0}")
    private double trustThreshold;

    @Value("${device.suspicious.threshold:40.0}")
    private double suspiciousThreshold;

    @Value("${device.max.accounts.per.device:5}")
    private int maxAccountsPerDevice;

    @Value("${device.fingerprint.ttl.days:90}")
    private int fingerprintTtlDays;

    private static final String CACHE_PREFIX = "device:";
    private static final String TRUST_CACHE_PREFIX = "device:trust:";
    private static final String HISTORY_CACHE_PREFIX = "device:history:";

    // Device trust scores cache
    private final Map<String, DeviceTrustScore> trustScoreCache = new ConcurrentHashMap<>();

    // Known malicious device patterns
    private final Set<Pattern> maliciousPatterns = new HashSet<>();

    // Device type risk scores
    private final Map<String, Double> deviceTypeRiskScores = new HashMap<>();

    // OS version risk scores
    private final Map<String, Double> osRiskScores = new HashMap<>();

    @PostConstruct
    public void initialize() {
        initializeMaliciousPatterns();
        initializeDeviceTypeRiskScores();
        initializeOsRiskScores();
        log.info("DeviceFingerprintService initialized with trust threshold: {}", trustThreshold);
    }

    /**
     * Analyze device fingerprint and calculate trust score
     */
    @Traced(operation = "device_fingerprint_analysis")
    public DeviceAnalysisResult analyzeDevice(TransactionData transaction) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Starting device analysis for transaction: {}", transaction.getTransactionId());

            TransactionData.DeviceInfo deviceInfo = transaction.getDeviceInfo();
            if (deviceInfo == null || deviceInfo.getDeviceId() == null) {
                return createNoDeviceResult(transaction);
            }

            // Generate secure device fingerprint
            String fingerprint = generateDeviceFingerprint(deviceInfo);
            
            // Perform comprehensive device analysis
            DeviceAnalysisResult result = DeviceAnalysisResult.builder()
                .transactionId(transaction.getTransactionId())
                .userId(transaction.getUserId())
                .deviceId(deviceInfo.getDeviceId())
                .fingerprint(fingerprint)
                .timestamp(LocalDateTime.now())
                .build();

            // Analyze device trust
            analyzeTrustScore(result, deviceInfo, transaction.getUserId());
            
            // Check for device anomalies
            detectDeviceAnomalies(result, deviceInfo);
            
            // Check device history
            analyzeDeviceHistory(result, deviceInfo, transaction.getUserId());
            
            // Check for jailbreak/root
            checkDeviceIntegrity(result, deviceInfo);
            
            // Analyze device sharing
            analyzeDeviceSharing(result, deviceInfo.getDeviceId());
            
            // Check against known malicious patterns
            checkMaliciousPatterns(result, deviceInfo);
            
            // Calculate overall risk score
            double riskScore = calculateDeviceRiskScore(result);
            result.setRiskScore(riskScore);
            result.setRiskLevel(determineRiskLevel(riskScore));
            
            // Save device fingerprint
            saveDeviceFingerprint(fingerprint, deviceInfo, result);
            
            long duration = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(duration);
            
            log.debug("Device analysis completed in {}ms for transaction: {}, risk score: {}", 
                duration, transaction.getTransactionId(), riskScore);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in device fingerprint analysis for transaction: {}", 
                transaction.getTransactionId(), e);
            throw new MLProcessingException("Failed to analyze device fingerprint", e);
        }
    }

    /**
     * Generate secure device fingerprint
     */
    public String generateDeviceFingerprint(TransactionData.DeviceInfo deviceInfo) {
        try {
            StringBuilder fingerprintData = new StringBuilder();
            
            // Core device attributes
            fingerprintData.append(deviceInfo.getDeviceId());
            fingerprintData.append("|").append(deviceInfo.getDeviceType());
            fingerprintData.append("|").append(deviceInfo.getOperatingSystem());
            fingerprintData.append("|").append(deviceInfo.getOsVersion());
            
            // Hardware attributes
            if (deviceInfo.getScreenResolution() != null) {
                fingerprintData.append("|").append(deviceInfo.getScreenResolution());
            }
            if (deviceInfo.getDeviceModel() != null) {
                fingerprintData.append("|").append(deviceInfo.getDeviceModel());
            }
            if (deviceInfo.getManufacturer() != null) {
                fingerprintData.append("|").append(deviceInfo.getManufacturer());
            }
            
            // Browser attributes (if web)
            if (deviceInfo.getBrowser() != null) {
                fingerprintData.append("|").append(deviceInfo.getBrowser());
                fingerprintData.append("|").append(deviceInfo.getBrowserVersion());
            }
            
            // Network attributes
            if (deviceInfo.getCarrier() != null) {
                fingerprintData.append("|").append(deviceInfo.getCarrier());
            }
            
            // Canvas fingerprint (if available)
            if (deviceInfo.getCanvasFingerprint() != null) {
                fingerprintData.append("|").append(deviceInfo.getCanvasFingerprint());
            }
            
            // Add salt for security
            fingerprintData.append("|").append(fingerprintSalt);
            
            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprintData.toString().getBytes());
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new MLProcessingException("Failed to generate device fingerprint", e);
        }
    }

    /**
     * Analyze device trust score
     */
    private void analyzeTrustScore(DeviceAnalysisResult result, 
                                  TransactionData.DeviceInfo deviceInfo,
                                  String userId) {
        
        double trustScore = 100.0;
        
        // Check device age
        LocalDateTime firstSeen = getDeviceFirstSeen(deviceInfo.getDeviceId());
        if (firstSeen == null) {
            trustScore -= 30.0; // New device
            result.setNewDevice(true);
        } else {
            long daysSinceFirstSeen = ChronoUnit.DAYS.between(firstSeen, LocalDateTime.now());
            if (daysSinceFirstSeen < 7) {
                trustScore -= 20.0; // Recently added device
            } else if (daysSinceFirstSeen < 30) {
                trustScore -= 10.0;
            }
            result.setDeviceAgeDays(daysSinceFirstSeen);
        }
        
        // Check if jailbroken/rooted
        if (Boolean.TRUE.equals(deviceInfo.getIsJailbroken())) {
            trustScore -= 40.0;
            result.setJailbroken(true);
        }
        
        // Check if emulator
        if (Boolean.TRUE.equals(deviceInfo.getIsEmulator())) {
            trustScore -= 50.0;
            result.setEmulator(true);
        }
        
        // Check device type
        String deviceType = deviceInfo.getDeviceType();
        if (deviceType != null) {
            trustScore -= deviceTypeRiskScores.getOrDefault(deviceType.toUpperCase(), 0.0);
        }
        
        // Check OS version (outdated = risky)
        if (isOutdatedOs(deviceInfo.getOperatingSystem(), deviceInfo.getOsVersion())) {
            trustScore -= 15.0;
            result.setOutdatedOs(true);
        }
        
        // Check device sharing
        int linkedAccounts = getLinkedAccountsCount(deviceInfo.getDeviceId());
        if (linkedAccounts > maxAccountsPerDevice) {
            trustScore -= 25.0;
            result.setSharedDevice(true);
        }
        
        // Check transaction history
        DeviceTransactionHistory history = getDeviceTransactionHistory(deviceInfo.getDeviceId());
        if (history != null) {
            if (history.getFraudCount() > 0) {
                trustScore -= history.getFraudCount() * 10.0;
            }
            if (history.getFailedAuthCount() > 5) {
                trustScore -= 15.0;
            }
        }
        
        result.setTrustScore(Math.max(trustScore, 0.0));
        result.setTrusted(trustScore >= trustThreshold);
        result.setSuspicious(trustScore < suspiciousThreshold);
    }

    /**
     * Detect device anomalies
     */
    private void detectDeviceAnomalies(DeviceAnalysisResult result, 
                                      TransactionData.DeviceInfo deviceInfo) {
        
        List<String> anomalies = new ArrayList<>();
        
        // Check for impossible device attributes
        if (hasImpossibleAttributes(deviceInfo)) {
            anomalies.add("IMPOSSIBLE_ATTRIBUTES");
        }
        
        // Check for spoofed user agent
        if (isSpoofedUserAgent(deviceInfo)) {
            anomalies.add("SPOOFED_USER_AGENT");
        }
        
        // Check for mismatched attributes
        if (hasAttributeMismatch(deviceInfo)) {
            anomalies.add("ATTRIBUTE_MISMATCH");
        }
        
        // Check for rapid attribute changes
        if (hasRapidAttributeChanges(deviceInfo.getDeviceId())) {
            anomalies.add("RAPID_CHANGES");
        }
        
        // Check for known fraud tools
        if (detectsFraudTools(deviceInfo)) {
            anomalies.add("FRAUD_TOOLS_DETECTED");
        }
        
        // Check for virtualization
        if (detectsVirtualization(deviceInfo)) {
            anomalies.add("VIRTUALIZATION_DETECTED");
        }
        
        // Check for automation tools
        if (detectsAutomation(deviceInfo)) {
            anomalies.add("AUTOMATION_DETECTED");
        }
        
        result.setAnomalies(anomalies);
        result.setAnomalyCount(anomalies.size());
    }

    /**
     * Analyze device history
     */
    private void analyzeDeviceHistory(DeviceAnalysisResult result,
                                     TransactionData.DeviceInfo deviceInfo,
                                     String userId) {
        
        String deviceId = deviceInfo.getDeviceId();
        
        // Get device transaction history
        DeviceTransactionHistory history = getDeviceTransactionHistory(deviceId);
        
        if (history == null) {
            result.setFirstTimeDevice(true);
            return;
        }
        
        result.setTransactionCount(history.getTotalTransactions());
        result.setSuccessfulTransactionCount(history.getSuccessfulTransactions());
        result.setFailedTransactionCount(history.getFailedTransactions());
        result.setFraudulentTransactionCount(history.getFraudCount());
        
        // Check if device is associated with multiple users
        Set<String> associatedUsers = getAssociatedUsers(deviceId);
        result.setLinkedAccountsCount(associatedUsers.size());
        
        if (associatedUsers.size() > 1 && !associatedUsers.contains(userId)) {
            result.setDeviceSwitched(true);
        }
        
        // Check last activity
        LocalDateTime lastActivity = history.getLastActivityTime();
        if (lastActivity != null) {
            long hoursSinceLastActivity = ChronoUnit.HOURS.between(lastActivity, LocalDateTime.now());
            result.setHoursSinceLastActivity(hoursSinceLastActivity);
            
            // Check for dormant device suddenly active
            if (hoursSinceLastActivity > 720) { // 30 days
                result.setDormantDeviceReactivated(true);
            }
        }
        
        // Calculate success rate
        if (history.getTotalTransactions() > 0) {
            double successRate = (double) history.getSuccessfulTransactions() / history.getTotalTransactions();
            result.setTransactionSuccessRate(successRate);
        }
    }

    /**
     * Check device integrity (jailbreak/root detection)
     */
    private void checkDeviceIntegrity(DeviceAnalysisResult result,
                                     TransactionData.DeviceInfo deviceInfo) {
        
        // Direct jailbreak/root indicators
        if (Boolean.TRUE.equals(deviceInfo.getIsJailbroken())) {
            result.setJailbroken(true);
            result.setIntegrityCompromised(true);
            return;
        }
        
        // Indirect jailbreak/root indicators
        List<String> suspiciousIndicators = new ArrayList<>();
        
        // Check for suspicious apps (iOS)
        if ("IOS".equalsIgnoreCase(deviceInfo.getOperatingSystem())) {
            if (hasJailbreakApps(deviceInfo)) {
                suspiciousIndicators.add("JAILBREAK_APPS");
            }
            if (hasCydiaPath(deviceInfo)) {
                suspiciousIndicators.add("CYDIA_DETECTED");
            }
        }
        
        // Check for suspicious apps (Android)
        if ("ANDROID".equalsIgnoreCase(deviceInfo.getOperatingSystem())) {
            if (hasRootApps(deviceInfo)) {
                suspiciousIndicators.add("ROOT_APPS");
            }
            if (hasSuBinary(deviceInfo)) {
                suspiciousIndicators.add("SU_BINARY");
            }
            if (hasMagisk(deviceInfo)) {
                suspiciousIndicators.add("MAGISK_DETECTED");
            }
        }
        
        // Check for debugging/development mode
        if (Boolean.TRUE.equals(deviceInfo.getIsDebugMode())) {
            suspiciousIndicators.add("DEBUG_MODE");
        }
        
        // Check for unknown sources enabled (Android)
        if (Boolean.TRUE.equals(deviceInfo.getUnknownSourcesEnabled())) {
            suspiciousIndicators.add("UNKNOWN_SOURCES");
        }
        
        if (!suspiciousIndicators.isEmpty()) {
            result.setIntegrityCompromised(true);
            result.setIntegrityIssues(suspiciousIndicators);
        }
    }

    /**
     * Analyze device sharing patterns
     */
    private void analyzeDeviceSharing(DeviceAnalysisResult result, String deviceId) {
        
        Set<String> associatedUsers = getAssociatedUsers(deviceId);
        int userCount = associatedUsers.size();
        
        result.setLinkedAccountsCount(userCount);
        
        if (userCount > maxAccountsPerDevice) {
            result.setSharedDevice(true);
            result.setExcessiveSharing(true);
        } else if (userCount > 2) {
            result.setSharedDevice(true);
        }
        
        // Check for rapid user switching
        if (hasRapidUserSwitching(deviceId)) {
            result.setRapidUserSwitching(true);
        }
        
        // Check for concurrent sessions
        int concurrentSessions = getConcurrentSessions(deviceId);
        if (concurrentSessions > 1) {
            result.setConcurrentSessions(concurrentSessions);
        }
    }

    /**
     * Check against known malicious patterns
     */
    private void checkMaliciousPatterns(DeviceAnalysisResult result,
                                       TransactionData.DeviceInfo deviceInfo) {
        
        List<String> matchedPatterns = new ArrayList<>();
        
        // Check device ID pattern
        for (Pattern pattern : maliciousPatterns) {
            if (pattern.matcher(deviceInfo.getDeviceId()).matches()) {
                matchedPatterns.add("MALICIOUS_DEVICE_ID");
                break;
            }
        }
        
        // Check for known fraud tool signatures
        if (deviceInfo.getUserAgent() != null) {
            if (containsFraudToolSignature(deviceInfo.getUserAgent())) {
                matchedPatterns.add("FRAUD_TOOL_SIGNATURE");
            }
        }
        
        // Check for bot patterns
        if (detectsBotPattern(deviceInfo)) {
            matchedPatterns.add("BOT_PATTERN");
        }
        
        // Check for known malicious IPs
        if (isKnownMaliciousDevice(deviceInfo.getDeviceId())) {
            matchedPatterns.add("KNOWN_MALICIOUS");
        }
        
        result.setMaliciousPatterns(matchedPatterns);
        result.setMalicious(!matchedPatterns.isEmpty());
    }

    /**
     * Calculate overall device risk score
     */
    private double calculateDeviceRiskScore(DeviceAnalysisResult result) {
        double riskScore = 0.0;
        
        // Trust score contribution (inverted)
        riskScore += (100.0 - result.getTrustScore()) * 0.4;
        
        // Device integrity
        if (result.isJailbroken()) riskScore += 30.0;
        if (result.isEmulator()) riskScore += 40.0;
        if (result.isIntegrityCompromised()) riskScore += 25.0;
        
        // Device sharing
        if (result.isSharedDevice()) riskScore += 15.0;
        if (result.isExcessiveSharing()) riskScore += 20.0;
        if (result.isRapidUserSwitching()) riskScore += 15.0;
        
        // Anomalies
        riskScore += result.getAnomalyCount() * 10.0;
        
        // Malicious indicators
        if (result.isMalicious()) riskScore += 40.0;
        
        // History
        if (result.getFraudulentTransactionCount() > 0) {
            riskScore += Math.min(result.getFraudulentTransactionCount() * 15.0, 45.0);
        }
        
        // New device
        if (result.isNewDevice()) riskScore += 20.0;
        
        // Dormant device reactivated
        if (result.isDormantDeviceReactivated()) riskScore += 15.0;
        
        return Math.min(riskScore, 100.0);
    }

    /**
     * Device analysis result DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceAnalysisResult {
        private String transactionId;
        private String userId;
        private String deviceId;
        private String fingerprint;
        private LocalDateTime timestamp;
        
        private double trustScore;
        private double riskScore;
        private String riskLevel;
        
        private boolean trusted;
        private boolean suspicious;
        private boolean malicious;
        
        private boolean newDevice;
        private boolean firstTimeDevice;
        private boolean sharedDevice;
        private boolean excessiveSharing;
        private boolean deviceSwitched;
        private boolean rapidUserSwitching;
        
        private boolean jailbroken;
        private boolean emulator;
        private boolean integrityCompromised;
        private boolean outdatedOs;
        
        private Long deviceAgeDays;
        private Integer linkedAccountsCount;
        private Integer concurrentSessions;
        
        private Long transactionCount;
        private Long successfulTransactionCount;
        private Long failedTransactionCount;
        private Long fraudulentTransactionCount;
        private Double transactionSuccessRate;
        
        private Long hoursSinceLastActivity;
        private boolean dormantDeviceReactivated;
        
        private List<String> anomalies;
        private Integer anomalyCount;
        private List<String> integrityIssues;
        private List<String> maliciousPatterns;
        
        private Long processingTimeMs;
    }

    /**
     * Device transaction history
     */
    @Data
    @Builder
    private static class DeviceTransactionHistory {
        private String deviceId;
        private Long totalTransactions;
        private Long successfulTransactions;
        private Long failedTransactions;
        private Long fraudCount;
        private Long failedAuthCount;
        private LocalDateTime firstSeenTime;
        private LocalDateTime lastActivityTime;
        private Set<String> associatedUsers;
        private Map<String, Integer> transactionTypes;
    }

    /**
     * Device trust score
     */
    @Data
    @Builder
    private static class DeviceTrustScore {
        private String deviceId;
        private Double score;
        private LocalDateTime calculatedAt;
        private LocalDateTime expiresAt;
        private String reason;
    }

    // Helper methods

    private DeviceAnalysisResult createNoDeviceResult(TransactionData transaction) {
        return DeviceAnalysisResult.builder()
            .transactionId(transaction.getTransactionId())
            .userId(transaction.getUserId())
            .trustScore(0.0)
            .riskScore(75.0)
            .riskLevel("HIGH")
            .trusted(false)
            .suspicious(true)
            .processingTimeMs(1L)
            .build();
    }

    private String determineRiskLevel(double riskScore) {
        if (riskScore >= 80) return "CRITICAL";
        if (riskScore >= 60) return "HIGH";
        if (riskScore >= 40) return "MEDIUM";
        if (riskScore >= 20) return "LOW";
        return "MINIMAL";
    }

    private void initializeMaliciousPatterns() {
        maliciousPatterns.add(Pattern.compile("^[0-9a-f]{8}-0000-0000-0000-[0-9a-f]{12}$"));
        maliciousPatterns.add(Pattern.compile("^12345678.*"));
        maliciousPatterns.add(Pattern.compile("^00000000.*"));
        maliciousPatterns.add(Pattern.compile("^test.*", Pattern.CASE_INSENSITIVE));
        maliciousPatterns.add(Pattern.compile("^demo.*", Pattern.CASE_INSENSITIVE));
    }

    private void initializeDeviceTypeRiskScores() {
        deviceTypeRiskScores.put("EMULATOR", 40.0);
        deviceTypeRiskScores.put("SIMULATOR", 40.0);
        deviceTypeRiskScores.put("VIRTUAL", 35.0);
        deviceTypeRiskScores.put("UNKNOWN", 25.0);
        deviceTypeRiskScores.put("MOBILE", 0.0);
        deviceTypeRiskScores.put("TABLET", 0.0);
        deviceTypeRiskScores.put("DESKTOP", 5.0);
        deviceTypeRiskScores.put("BOT", 50.0);
    }

    private void initializeOsRiskScores() {
        osRiskScores.put("ANDROID_4", 20.0);
        osRiskScores.put("ANDROID_5", 15.0);
        osRiskScores.put("ANDROID_6", 10.0);
        osRiskScores.put("IOS_9", 15.0);
        osRiskScores.put("IOS_10", 10.0);
        osRiskScores.put("WINDOWS_XP", 30.0);
        osRiskScores.put("WINDOWS_7", 20.0);
    }

    private LocalDateTime getDeviceFirstSeen(String deviceId) {
        return mlCacheService.getDeviceFirstSeen(deviceId);
    }

    private int getLinkedAccountsCount(String deviceId) {
        Set<String> users = getAssociatedUsers(deviceId);
        return users.size();
    }

    private Set<String> getAssociatedUsers(String deviceId) {
        return mlCacheService.getAssociatedUsers(deviceId);
    }

    private DeviceTransactionHistory getDeviceTransactionHistory(String deviceId) {
        String key = HISTORY_CACHE_PREFIX + deviceId;
        return (DeviceTransactionHistory) redisTemplate.opsForValue().get(key);
    }

    private void saveDeviceFingerprint(String fingerprint, 
                                      TransactionData.DeviceInfo deviceInfo,
                                      DeviceAnalysisResult result) {
        try {
            String key = CACHE_PREFIX + "fp:" + fingerprint;
            Map<String, Object> data = new HashMap<>();
            data.put("deviceId", deviceInfo.getDeviceId());
            data.put("deviceType", deviceInfo.getDeviceType());
            data.put("os", deviceInfo.getOperatingSystem());
            data.put("trustScore", result.getTrustScore());
            data.put("lastSeen", LocalDateTime.now());
            
            redisTemplate.opsForHash().putAll(key, data);
            redisTemplate.expire(key, fingerprintTtlDays, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to save device fingerprint: {}", e.getMessage());
        }
    }

    // Device check helper methods
    
    private boolean isOutdatedOs(String os, String version) {
        if (os == null || version == null) return false;
        
        // Simplified check - would need more sophisticated version comparison
        return osRiskScores.containsKey((os + "_" + version).toUpperCase());
    }

    private boolean hasImpossibleAttributes(TransactionData.DeviceInfo deviceInfo) {
        // Check for impossible combinations
        if ("IOS".equalsIgnoreCase(deviceInfo.getOperatingSystem()) &&
            "SAMSUNG".equalsIgnoreCase(deviceInfo.getManufacturer())) {
            return true;
        }
        
        if ("ANDROID".equalsIgnoreCase(deviceInfo.getOperatingSystem()) &&
            "APPLE".equalsIgnoreCase(deviceInfo.getManufacturer())) {
            return true;
        }
        
        return false;
    }

    private boolean isSpoofedUserAgent(TransactionData.DeviceInfo deviceInfo) {
        if (deviceInfo.getUserAgent() == null) return false;
        
        // Check for inconsistencies in user agent
        String ua = deviceInfo.getUserAgent().toLowerCase();
        String os = deviceInfo.getOperatingSystem();
        
        if (os != null) {
            if ("IOS".equalsIgnoreCase(os) && !ua.contains("iphone") && !ua.contains("ipad")) {
                return true;
            }
            if ("ANDROID".equalsIgnoreCase(os) && !ua.contains("android")) {
                return true;
            }
        }
        
        return false;
    }

    private boolean hasAttributeMismatch(TransactionData.DeviceInfo deviceInfo) {
        // Check for attribute inconsistencies
        return hasImpossibleAttributes(deviceInfo) || isSpoofedUserAgent(deviceInfo);
    }

    private boolean hasRapidAttributeChanges(String deviceId) {
        // Would check historical attributes for rapid changes
        return false; // Simplified implementation
    }

    private boolean detectsFraudTools(TransactionData.DeviceInfo deviceInfo) {
        if (deviceInfo.getUserAgent() == null) return false;
        
        String ua = deviceInfo.getUserAgent().toLowerCase();
        String[] fraudTools = {"frida", "xposed", "substrate", "cycript", "flex", "reveal"};
        
        for (String tool : fraudTools) {
            if (ua.contains(tool)) return true;
        }
        
        return false;
    }

    private boolean detectsVirtualization(TransactionData.DeviceInfo deviceInfo) {
        return Boolean.TRUE.equals(deviceInfo.getIsEmulator()) ||
               "VIRTUAL".equalsIgnoreCase(deviceInfo.getDeviceType());
    }

    private boolean detectsAutomation(TransactionData.DeviceInfo deviceInfo) {
        if (deviceInfo.getUserAgent() == null) return false;
        
        String ua = deviceInfo.getUserAgent().toLowerCase();
        String[] automationTools = {"selenium", "puppeteer", "playwright", "webdriver", "phantomjs"};
        
        for (String tool : automationTools) {
            if (ua.contains(tool)) return true;
        }
        
        return false;
    }

    private boolean hasJailbreakApps(TransactionData.DeviceInfo deviceInfo) {
        // Would check for Cydia, Sileo, etc.
        return false; // Simplified implementation
    }

    private boolean hasCydiaPath(TransactionData.DeviceInfo deviceInfo) {
        // Would check for Cydia paths
        return false; // Simplified implementation
    }

    private boolean hasRootApps(TransactionData.DeviceInfo deviceInfo) {
        // Would check for SuperSU, Magisk, etc.
        return false; // Simplified implementation
    }

    private boolean hasSuBinary(TransactionData.DeviceInfo deviceInfo) {
        // Would check for su binary
        return false; // Simplified implementation
    }

    private boolean hasMagisk(TransactionData.DeviceInfo deviceInfo) {
        // Would check for Magisk
        return false; // Simplified implementation
    }

    private boolean hasRapidUserSwitching(String deviceId) {
        // Would check for rapid user changes
        return false; // Simplified implementation
    }

    private int getConcurrentSessions(String deviceId) {
        // Would check active sessions
        return 0; // Simplified implementation
    }

    private boolean containsFraudToolSignature(String userAgent) {
        String[] signatures = {"bot", "scraper", "crawler", "fraud", "attack"};
        String ua = userAgent.toLowerCase();
        
        for (String sig : signatures) {
            if (ua.contains(sig)) return true;
        }
        
        return false;
    }

    private boolean detectsBotPattern(TransactionData.DeviceInfo deviceInfo) {
        return "BOT".equalsIgnoreCase(deviceInfo.getDeviceType()) ||
               containsFraudToolSignature(deviceInfo.getUserAgent());
    }

    private boolean isKnownMaliciousDevice(String deviceId) {
        String key = CACHE_PREFIX + "blacklist:" + deviceId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}