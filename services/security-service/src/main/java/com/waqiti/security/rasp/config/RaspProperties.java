package com.waqiti.security.rasp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.HashMap;

/**
 * RASP configuration properties
 */
@Data
@ConfigurationProperties(prefix = "rasp")
public class RaspProperties {

    private boolean enabled = true;
    private DetectorConfig detectors = new DetectorConfig();
    private ResponseConfig response = new ResponseConfig();
    private MonitoringConfig monitoring = new MonitoringConfig();

    @Data
    public static class DetectorConfig {
        private SqlInjectionConfig sqlInjection = new SqlInjectionConfig();
        private XssConfig xss = new XssConfig();
        private CommandInjectionConfig commandInjection = new CommandInjectionConfig();
        private RateLimitConfig rateLimit = new RateLimitConfig();
        private PathTraversalConfig pathTraversal = new PathTraversalConfig();
    }

    @Data
    public static class SqlInjectionConfig {
        private boolean enabled = true;
        private int sensitivity = 5; // 1-10 scale
        private boolean detectEncoded = true;
        private boolean detectBlind = true;
    }

    @Data
    public static class XssConfig {
        private boolean enabled = true;
        private int sensitivity = 5;
        private boolean detectEncoded = true;
        private boolean detectPolyglot = true;
        private boolean detectDomBased = true;
    }

    @Data
    public static class CommandInjectionConfig {
        private boolean enabled = true;
        private int sensitivity = 5;
        private boolean detectEncoded = true;
        private boolean detectChaining = true;
    }

    @Data
    public static class RateLimitConfig {
        private boolean enabled = true;
        private int requestsPerMinute = 100;
        private int requestsPerHour = 1000;
        private int burstThreshold = 50;
        private int burstWindowSeconds = 10;
    }

    @Data
    public static class PathTraversalConfig {
        private boolean enabled = true;
        private int sensitivity = 5;
        private boolean detectEncoded = true;
        private boolean detectNullByte = true;
    }

    @Data
    public static class ResponseConfig {
        private boolean blockRequests = true;
        private int blockDurationMinutes = 15;
        private boolean sendAlerts = true;
        private String kafkaTopic = "security-alerts";
        private boolean logEvents = true;
        private String logLevel = "WARN";
    }

    @Data
    public static class MonitoringConfig {
        private boolean enabled = true;
        private boolean collectMetrics = true;
        private boolean enableDashboard = true;
        private int metricsRetentionDays = 30;
        private Map<String, String> alertingConfig = new HashMap<>();
    }
}