package com.waqiti.security.service;

import com.waqiti.security.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Device Analysis Service
 * Provides device fingerprinting and analysis capabilities
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAnalysisService {

    private final Parser uaParser;

    private static final Pattern BROWSER_VERSION_PATTERN = Pattern.compile("([\\d.]+)");

    /**
     * Analyze device from authentication event
     */
    public DeviceAnalysisResult analyzeDevice(AuthenticationEvent event) {
        try {
            String userAgent = event.getUserAgent();
            if (userAgent == null || userAgent.isEmpty()) {
                return createUnknownDeviceResult("No user agent provided");
            }

            // Parse user agent
            Client client = uaParser.parse(userAgent);

            // Extract device information
            String browserName = client.userAgent.family;
            String browserVersion = formatVersion(client.userAgent);
            String osName = client.os.family;
            String osVersion = formatVersion(client.os);
            String deviceType = determineDeviceType(client);
            String deviceBrand = client.device.family;

            // Analyze browser configuration
            BrowserConfig browserConfig = analyzeBrowserConfig(event, client);

            // Calculate device fingerprint
            String deviceFingerprint = calculateDeviceFingerprint(
                browserName, browserVersion, osName, osVersion, deviceType, event
            );

            // Determine if device is trusted
            boolean isTrusted = determineIfTrusted(event, deviceFingerprint);

            // Calculate risk score
            int riskScore = calculateDeviceRiskScore(
                browserName, osName, deviceType, browserConfig, isTrusted
            );

            return DeviceAnalysisResult.builder()
                .deviceId(deviceFingerprint)
                .deviceType(deviceType)
                .deviceBrand(deviceBrand != null ? deviceBrand : "Unknown")
                .browserName(browserName != null ? browserName : "Unknown")
                .browserVersion(browserVersion)
                .osName(osName != null ? osName : "Unknown")
                .osVersion(osVersion)
                .userAgent(userAgent)
                .isTrusted(isTrusted)
                .isKnownDevice(false) // Will be set by caller based on history
                .riskScore(riskScore)
                .browserConfig(browserConfig)
                .deviceFingerprint(deviceFingerprint)
                .build();

        } catch (Exception e) {
            log.error("Error analyzing device: {}", e.getMessage(), e);
            return createUnknownDeviceResult("Analysis error: " + e.getMessage());
        }
    }

    /**
     * Analyze user agent string
     */
    public UserAgentAnalysisResult analyzeUserAgent(String userAgent) {
        try {
            if (userAgent == null || userAgent.isEmpty()) {
                return createUnknownUserAgentResult("No user agent provided");
            }

            Client client = uaParser.parse(userAgent);

            boolean isSuspicious = detectSuspiciousUserAgent(userAgent, client);
            boolean isBot = detectBot(userAgent, client);
            boolean isAutomated = isBot || detectAutomatedBrowser(userAgent);

            return UserAgentAnalysisResult.builder()
                .userAgent(userAgent)
                .browserName(client.userAgent.family)
                .browserVersion(formatVersion(client.userAgent))
                .osName(client.os.family)
                .osVersion(formatVersion(client.os))
                .deviceType(determineDeviceType(client))
                .isSuspicious(isSuspicious)
                .isBot(isBot)
                .isAutomated(isAutomated)
                .riskScore(calculateUserAgentRiskScore(isSuspicious, isBot, isAutomated))
                .build();

        } catch (Exception e) {
            log.error("Error analyzing user agent: {}", e.getMessage(), e);
            return createUnknownUserAgentResult("Analysis error: " + e.getMessage());
        }
    }

    /**
     * Analyze browser configuration for anomalies
     */
    private BrowserConfig analyzeBrowserConfig(AuthenticationEvent event, Client client) {
        // Extract available browser configuration details
        Map<String, Object> configDetails = new HashMap<>();

        // Screen resolution (if available in metadata)
        if (event.getMetadata() != null) {
            Object screenWidth = event.getMetadata().get("screen_width");
            Object screenHeight = event.getMetadata().get("screen_height");
            Object colorDepth = event.getMetadata().get("color_depth");
            Object timezone = event.getMetadata().get("timezone");
            Object language = event.getMetadata().get("language");
            Object plugins = event.getMetadata().get("plugins");

            if (screenWidth != null) configDetails.put("screen_width", screenWidth);
            if (screenHeight != null) configDetails.put("screen_height", screenHeight);
            if (colorDepth != null) configDetails.put("color_depth", colorDepth);
            if (timezone != null) configDetails.put("timezone", timezone);
            if (language != null) configDetails.put("language", language);
            if (plugins != null) configDetails.put("plugins", plugins);
        }

        // Analyze for browser config anomalies
        BrowserConfigAnalysisResult analysis = analyzeBrowserConfigAnomalies(configDetails, client);

        return BrowserConfig.builder()
            .screenWidth(getIntValue(configDetails.get("screen_width")))
            .screenHeight(getIntValue(configDetails.get("screen_height")))
            .colorDepth(getIntValue(configDetails.get("color_depth")))
            .timezone(getStringValue(configDetails.get("timezone")))
            .language(getStringValue(configDetails.get("language")))
            .plugins(configDetails.get("plugins") != null ? configDetails.get("plugins").toString() : null)
            .hasAnomalies(analysis.isAnomalous())
            .anomalyDetails(analysis.getAnomalies())
            .configHash(calculateConfigHash(configDetails))
            .build();
    }

    /**
     * Analyze browser configuration for anomalies
     */
    private BrowserConfigAnalysisResult analyzeBrowserConfigAnomalies(
        Map<String, Object> configDetails,
        Client client
    ) {
        List<String> anomalies = new ArrayList<>();

        // Check for headless browser indicators
        if (isHeadlessBrowser(configDetails)) {
            anomalies.add("HEADLESS_BROWSER_DETECTED");
        }

        // Check for automation tools
        if (hasAutomationIndicators(configDetails)) {
            anomalies.add("AUTOMATION_TOOLS_DETECTED");
        }

        // Check for suspicious screen resolution
        Integer screenWidth = getIntValue(configDetails.get("screen_width"));
        Integer screenHeight = getIntValue(configDetails.get("screen_height"));
        if (screenWidth != null && screenHeight != null) {
            if (screenWidth < 800 || screenHeight < 600) {
                anomalies.add("UNUSUAL_SCREEN_RESOLUTION");
            }
        }

        // Check for missing expected features
        if (configDetails.isEmpty()) {
            anomalies.add("NO_BROWSER_CONFIG_DATA");
        }

        return BrowserConfigAnalysisResult.builder()
            .anomalous(!anomalies.isEmpty())
            .anomalies(anomalies)
            .riskScore(anomalies.size() * 20)
            .build();
    }

    /**
     * Calculate device fingerprint
     */
    private String calculateDeviceFingerprint(
        String browserName, String browserVersion,
        String osName, String osVersion,
        String deviceType, AuthenticationEvent event
    ) {
        try {
            StringBuilder fingerprintData = new StringBuilder();
            fingerprintData.append(browserName != null ? browserName : "");
            fingerprintData.append("|");
            fingerprintData.append(browserVersion != null ? browserVersion : "");
            fingerprintData.append("|");
            fingerprintData.append(osName != null ? osName : "");
            fingerprintData.append("|");
            fingerprintData.append(osVersion != null ? osVersion : "");
            fingerprintData.append("|");
            fingerprintData.append(deviceType != null ? deviceType : "");

            // Add browser config if available
            if (event.getMetadata() != null) {
                fingerprintData.append("|");
                fingerprintData.append(event.getMetadata().getOrDefault("screen_width", ""));
                fingerprintData.append("|");
                fingerprintData.append(event.getMetadata().getOrDefault("screen_height", ""));
                fingerprintData.append("|");
                fingerprintData.append(event.getMetadata().getOrDefault("timezone", ""));
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprintData.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            log.error("Error calculating device fingerprint", e);
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Calculate configuration hash
     */
    private String calculateConfigHash(Map<String, Object> configDetails) {
        try {
            String configData = configDetails.toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(configData.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.substring(0, 32); // First 32 characters

        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().substring(0, 32);
        }
    }

    /**
     * Determine device type from parsed client
     */
    private String determineDeviceType(Client client) {
        if (client.device.family != null && !client.device.family.equals("Other")) {
            return client.device.family;
        }

        // Fallback based on OS
        if (client.os.family != null) {
            String os = client.os.family.toLowerCase();
            if (os.contains("android") || os.contains("ios")) {
                return "Mobile";
            }
            if (os.contains("windows") || os.contains("mac") || os.contains("linux")) {
                return "Desktop";
            }
        }

        return "Unknown";
    }

    /**
     * Format version string
     */
    private String formatVersion(ua_parser.VersionedName versionedName) {
        if (versionedName.major == null) return null;

        StringBuilder version = new StringBuilder(versionedName.major);
        if (versionedName.minor != null) {
            version.append(".").append(versionedName.minor);
            if (versionedName.patch != null) {
                version.append(".").append(versionedName.patch);
            }
        }

        return version.toString();
    }

    /**
     * Detect suspicious user agent
     */
    private boolean detectSuspiciousUserAgent(String userAgent, Client client) {
        String uaLower = userAgent.toLowerCase();

        // Check for common attack tools
        String[] suspiciousKeywords = {
            "sqlmap", "nikto", "nmap", "masscan", "nessus",
            "burp", "metasploit", "havij", "acunetix", "appscan"
        };

        for (String keyword : suspiciousKeywords) {
            if (uaLower.contains(keyword)) {
                return true;
            }
        }

        // Check for very old or uncommon browsers
        if (client.userAgent.family != null) {
            String browser = client.userAgent.family.toLowerCase();
            if (browser.contains("ie") && client.userAgent.major != null) {
                try {
                    int version = Integer.parseInt(client.userAgent.major);
                    if (version < 11) {
                        return true; // Very old IE
                    }
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }

        return false;
    }

    /**
     * Detect bot user agents
     */
    private boolean detectBot(String userAgent, Client client) {
        String uaLower = userAgent.toLowerCase();

        String[] botKeywords = {
            "bot", "crawler", "spider", "scraper", "curl", "wget",
            "python-requests", "java/", "go-http-client", "axios"
        };

        for (String keyword : botKeywords) {
            if (uaLower.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Detect automated browsers (Selenium, Puppeteer, etc.)
     */
    private boolean detectAutomatedBrowser(String userAgent) {
        String uaLower = userAgent.toLowerCase();

        String[] automationKeywords = {
            "headless", "phantom", "selenium", "webdriver",
            "puppeteer", "playwright", "automation"
        };

        for (String keyword : automationKeywords) {
            if (uaLower.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check for headless browser indicators
     */
    private boolean isHeadlessBrowser(Map<String, Object> configDetails) {
        // Headless browsers often have missing or unusual config
        if (configDetails.isEmpty()) {
            return false; // Not enough info
        }

        // Check for headless indicators in metadata
        Object plugins = configDetails.get("plugins");
        if (plugins != null) {
            String pluginsStr = plugins.toString().toLowerCase();
            if (pluginsStr.contains("headless") || pluginsStr.contains("phantom")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check for automation tool indicators
     */
    private boolean hasAutomationIndicators(Map<String, Object> configDetails) {
        // Check for webdriver or automation properties
        for (Map.Entry<String, Object> entry : configDetails.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.contains("webdriver") || key.contains("automation") ||
                key.contains("selenium") || key.contains("phantom")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determine if device is trusted
     */
    private boolean determineIfTrusted(AuthenticationEvent event, String deviceFingerprint) {
        // A device is trusted if it's been successfully used before
        // This would typically check against a trusted device registry
        // For now, return false (will be determined by caller with history)
        return false;
    }

    /**
     * Calculate device risk score (0-100)
     */
    private int calculateDeviceRiskScore(
        String browserName, String osName, String deviceType,
        BrowserConfig browserConfig, boolean isTrusted
    ) {
        int riskScore = 0;

        // Trusted devices get low risk
        if (isTrusted) {
            return 5;
        }

        // Unknown browser/OS
        if ("Unknown".equals(browserName)) riskScore += 20;
        if ("Unknown".equals(osName)) riskScore += 20;
        if ("Unknown".equals(deviceType)) riskScore += 10;

        // Browser config anomalies
        if (browserConfig != null && browserConfig.isHasAnomalies()) {
            riskScore += Math.min(browserConfig.getAnomalyDetails().size() * 15, 40);
        }

        return Math.min(riskScore, 100);
    }

    /**
     * Calculate user agent risk score
     */
    private int calculateUserAgentRiskScore(boolean isSuspicious, boolean isBot, boolean isAutomated) {
        int riskScore = 0;

        if (isSuspicious) riskScore += 50;
        if (isBot) riskScore += 40;
        if (isAutomated) riskScore += 30;

        return Math.min(riskScore, 100);
    }

    /**
     * Create unknown device result
     */
    private DeviceAnalysisResult createUnknownDeviceResult(String reason) {
        return DeviceAnalysisResult.builder()
            .deviceId("unknown")
            .deviceType("Unknown")
            .deviceBrand("Unknown")
            .browserName("Unknown")
            .browserVersion(null)
            .osName("Unknown")
            .osVersion(null)
            .userAgent(null)
            .isTrusted(false)
            .isKnownDevice(false)
            .riskScore(50)
            .browserConfig(null)
            .deviceFingerprint("unknown")
            .build();
    }

    /**
     * Create unknown user agent result
     */
    private UserAgentAnalysisResult createUnknownUserAgentResult(String reason) {
        return UserAgentAnalysisResult.builder()
            .userAgent(null)
            .browserName("Unknown")
            .browserVersion(null)
            .osName("Unknown")
            .osVersion(null)
            .deviceType("Unknown")
            .isSuspicious(false)
            .isBot(false)
            .isAutomated(false)
            .riskScore(50)
            .build();
    }

    /**
     * Get integer value from object
     */
    private Integer getIntValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get string value from object
     */
    private String getStringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
