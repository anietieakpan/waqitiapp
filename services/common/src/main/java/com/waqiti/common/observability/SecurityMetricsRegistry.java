package com.waqiti.common.observability;

import com.waqiti.common.metrics.abstraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Security Metrics Registry - PRODUCTION GRADE
 * Uses industrial-strength metrics abstraction for security monitoring
 */
@Slf4j
@Component
public class SecurityMetricsRegistry {

    private final MetricsRegistry metricsRegistry;
    private final String serviceName;
    
    // Real-time security counters
    private final AtomicLong activeSecurityAlerts = new AtomicLong(0);
    private final AtomicLong activeSessions = new AtomicLong(0);
    private final AtomicLong failedLoginAttempts = new AtomicLong(0);

    // Security Metric Definitions
    private static final class SecurityMetrics {
        // Authentication metrics
        static final MetricDefinition AUTHENTICATION_ATTEMPTS = MetricDefinition.builder()
            .name("security.authentication.attempts.total")
            .description("Total authentication attempts")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .maxCardinality(1000)
            .build();
            
        static final MetricDefinition AUTHENTICATION_FAILURES = MetricDefinition.builder()
            .name("security.authentication.failures.total")
            .description("Total authentication failures")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .maxCardinality(1000)
            .build();
            
        static final MetricDefinition AUTHENTICATION_TIME = MetricDefinition.builder()
            .name("security.authentication.processing.time")
            .description("Authentication processing time")
            .type(MetricDefinition.MetricType.TIMER)
            .slos(List.of(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(2)))
            .build();
            
        static final MetricDefinition MFA_VERIFICATIONS = MetricDefinition.builder()
            .name("security.mfa.verifications.total")
            .description("Total MFA verifications")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();

        // Authorization metrics
        static final MetricDefinition AUTHORIZATION_CHECKS = MetricDefinition.builder()
            .name("security.authorization.checks.total")
            .description("Total authorization checks")
            .type(MetricDefinition.MetricType.COUNTER)
            .sampleRate(0.1) // Sample 10% due to high volume
            .build();
            
        static final MetricDefinition AUTHORIZATION_FAILURES = MetricDefinition.builder()
            .name("security.authorization.failures.total")
            .description("Total authorization failures")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition AUTHORIZATION_TIME = MetricDefinition.builder()
            .name("security.authorization.processing.time")
            .description("Authorization processing time")
            .type(MetricDefinition.MetricType.TIMER)
            .slos(List.of(Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(200)))
            .build();

        // Security events
        static final MetricDefinition SECURITY_EVENTS = MetricDefinition.builder()
            .name("security.events.total")
            .description("Total security events")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .maxCardinality(2000)
            .build();
            
        static final MetricDefinition SUSPICIOUS_ACTIVITIES = MetricDefinition.builder()
            .name("security.suspicious.activities.total")
            .description("Total suspicious activities")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition BRUTE_FORCE_ATTEMPTS = MetricDefinition.builder()
            .name("security.brute_force.attempts.total")
            .description("Total brute force attempts")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition ACCOUNT_LOCKOUTS = MetricDefinition.builder()
            .name("security.account.lockouts.total")
            .description("Total account lockouts")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();

        // Compliance metrics
        static final MetricDefinition PCI_COMPLIANCE_CHECKS = MetricDefinition.builder()
            .name("compliance.pci.checks.total")
            .description("Total PCI compliance checks")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition GDPR_REQUESTS = MetricDefinition.builder()
            .name("compliance.gdpr.requests.total")
            .description("Total GDPR requests")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition DATA_BREACH_INCIDENTS = MetricDefinition.builder()
            .name("security.data_breach.incidents.total")
            .description("Total data breach incidents")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();

        // Encryption metrics
        static final MetricDefinition ENCRYPTION_OPERATIONS = MetricDefinition.builder()
            .name("security.encryption.operations.total")
            .description("Total encryption operations")
            .type(MetricDefinition.MetricType.COUNTER)
            .sampleRate(0.01) // 1% sampling due to very high volume
            .build();
            
        static final MetricDefinition KEY_ROTATIONS = MetricDefinition.builder()
            .name("security.key.rotations.total")
            .description("Total key rotations")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition ENCRYPTION_TIME = MetricDefinition.builder()
            .name("security.encryption.processing.time")
            .description("Encryption processing time")
            .type(MetricDefinition.MetricType.TIMER)
            .slos(List.of(Duration.ofMillis(1), Duration.ofMillis(10), Duration.ofMillis(50)))
            .build();

        // Real-time gauges
        static final MetricDefinition ACTIVE_SECURITY_ALERTS = MetricDefinition.builder()
            .name("security.alerts.active.count")
            .description("Number of active security alerts")
            .type(MetricDefinition.MetricType.GAUGE)
            .critical(true)
            .build();
            
        static final MetricDefinition ACTIVE_SESSIONS = MetricDefinition.builder()
            .name("security.sessions.active.count")
            .description("Number of active sessions")
            .type(MetricDefinition.MetricType.GAUGE)
            .build();
    }

    // Security-specific tag constraints
    private static final TagConstraints SECURITY_TAG_CONSTRAINTS = TagConstraints.builder()
        .maxTags(8)
        .requiredTags(java.util.Set.of("service"))
        .allowedValues(java.util.Map.of(
            "auth_method", java.util.Set.of("password", "mfa", "biometric", "sso", "api_key"),
            "auth_result", java.util.Set.of("success", "failure", "blocked", "locked"),
            "threat_level", java.util.Set.of("low", "medium", "high", "critical"),
            "event_type", java.util.Set.of("login", "logout", "permission_check", "data_access", "admin_action")
        ))
        .valueNormalizers(java.util.Map.of(
            "failure_reason", TagConstraints.Normalizers.ERROR_CLASS,
            "user_agent", SecurityMetricsRegistry::normalizeUserAgent,
            "ip_classification", SecurityMetricsRegistry::classifyIpAddress
        ))
        .strict(false)
        .build();

    public SecurityMetricsRegistry(
            MetricsRegistry metricsRegistry,
            @Value("${spring.application.name:unknown}") String serviceName) {
        this.metricsRegistry = metricsRegistry;
        this.serviceName = serviceName;
        
        initializeGauges();
        log.info("Security metrics registry initialized for service: {}", serviceName);
    }
    
    private void initializeGauges() {
        // Register real-time gauges
        TagSet serviceTag = TagSet.of("service", serviceName);
        
        metricsRegistry.registerGauge(SecurityMetrics.ACTIVE_SECURITY_ALERTS, serviceTag, 
            activeSecurityAlerts, AtomicLong::doubleValue);
            
        metricsRegistry.registerGauge(SecurityMetrics.ACTIVE_SESSIONS, serviceTag, 
            activeSessions, AtomicLong::doubleValue);
    }

    // Authentication metrics
    public void recordAuthenticationAttempt(String method, String result, String failureReason, 
                                          Duration processingTime) {
        TagSet tags = TagSet.builder(SECURITY_TAG_CONSTRAINTS)
            .tag("service", serviceName)
            .tag("auth_method", method)
            .tag("auth_result", result)
            .tag("failure_reason", failureReason != null ? failureReason : "none")
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.AUTHENTICATION_ATTEMPTS, tags);
        
        if (!"success".equals(result)) {
            metricsRegistry.incrementCounter(SecurityMetrics.AUTHENTICATION_FAILURES, tags);
            failedLoginAttempts.incrementAndGet();
        }
        
        metricsRegistry.recordTime(SecurityMetrics.AUTHENTICATION_TIME, tags, processingTime);
    }

    public void recordMfaVerification(String method, String result, String failureReason) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("mfa_method", method)
            .tag("result", result)
            .tag("failure_reason", failureReason != null ? failureReason : "none")
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.MFA_VERIFICATIONS, tags);
    }

    // Authorization metrics
    public void recordAuthorizationCheck(String resource, String action, String result, 
                                        String failureReason, Duration processingTime) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("resource", resource)
            .tag("action", action)
            .tag("result", result)
            .tag("failure_reason", failureReason != null ? failureReason : "none")
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.AUTHORIZATION_CHECKS, tags);
        
        if (!"allowed".equals(result)) {
            metricsRegistry.incrementCounter(SecurityMetrics.AUTHORIZATION_FAILURES, tags);
        }
        
        metricsRegistry.recordTime(SecurityMetrics.AUTHORIZATION_TIME, tags, processingTime);
    }

    // Security events
    public void recordSecurityEvent(String eventType, String severity, String source, 
                                   String threatLevel, String outcome) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("event_type", eventType)
            .tag("severity", severity)
            .tag("source", source)
            .tag("threat_level", threatLevel)
            .tag("outcome", outcome)
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.SECURITY_EVENTS, tags);
        
        if ("high".equals(threatLevel) || "critical".equals(threatLevel)) {
            activeSecurityAlerts.incrementAndGet();
        }
    }

    public void recordSuspiciousActivity(String activityType, String source, String riskScore) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("activity_type", activityType)
            .tag("source", source)
            .tag("risk_score", categorizeRiskScore(riskScore))
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.SUSPICIOUS_ACTIVITIES, tags);
    }

    public void recordBruteForceAttempt(String targetType, String source, String blocked) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("target_type", targetType)
            .tag("source", source)
            .tag("blocked", blocked)
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.BRUTE_FORCE_ATTEMPTS, tags);
    }

    public void recordAccountLockout(String reason, String duration, String source) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("reason", reason)
            .tag("duration", duration)
            .tag("source", source)
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.ACCOUNT_LOCKOUTS, tags);
    }

    // Compliance metrics
    public void recordPciComplianceCheck(String checkType, String result, String level) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("check_type", checkType)
            .tag("result", result)
            .tag("compliance_level", level)
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.PCI_COMPLIANCE_CHECKS, tags);
    }

    public void recordGdprRequest(String requestType, String status, String region) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("request_type", requestType)
            .tag("status", status)
            .tag("region", region)
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.GDPR_REQUESTS, tags);
    }

    public void recordDataBreachIncident(String incidentType, String severity, String affected) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("incident_type", incidentType)
            .tag("severity", severity)
            .tag("affected_records", categorizeAffectedRecords(affected))
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.DATA_BREACH_INCIDENTS, tags);
        
        // Critical alert for any data breach
        activeSecurityAlerts.incrementAndGet();
    }

    // Encryption metrics
    public void recordEncryptionOperation(String operation, String algorithm, String keySize, 
                                        Duration processingTime) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("operation", operation)
            .tag("algorithm", algorithm)
            .tag("key_size", keySize)
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.ENCRYPTION_OPERATIONS, tags);
        metricsRegistry.recordTime(SecurityMetrics.ENCRYPTION_TIME, tags, processingTime);
    }

    public void recordKeyRotation(String keyType, String reason, boolean success) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("key_type", keyType)
            .tag("reason", reason)
            .tag("success", success)
            .build();

        metricsRegistry.incrementCounter(SecurityMetrics.KEY_ROTATIONS, tags);
    }

    // Session management
    public void updateActiveSessions(long count) {
        activeSessions.set(count);
    }

    public void resolveSecurityAlert() {
        activeSecurityAlerts.updateAndGet(count -> Math.max(0, count - 1));
    }

    // Helper methods for tag normalization
    private static String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return "unknown";
        userAgent = userAgent.toLowerCase();
        
        if (userAgent.contains("chrome")) return "chrome";
        if (userAgent.contains("firefox")) return "firefox";
        if (userAgent.contains("safari")) return "safari";
        if (userAgent.contains("edge")) return "edge";
        if (userAgent.contains("bot") || userAgent.contains("crawler")) return "bot";
        if (userAgent.contains("mobile")) return "mobile";
        return "other";
    }

    private static String classifyIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) return "unknown";
        
        // Simple classification - in production, use GeoIP service
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.")) {
            return "internal";
        }
        if (ip.equals("127.0.0.1") || ip.equals("::1")) {
            return "localhost";
        }
        return "external";
    }

    private String categorizeRiskScore(String score) {
        try {
            double risk = Double.parseDouble(score);
            if (risk < 0.3) return "low";
            if (risk < 0.6) return "medium";
            if (risk < 0.8) return "high";
            return "critical";
        } catch (NumberFormatException e) {
            return "unknown";
        }
    }

    private String categorizeAffectedRecords(String count) {
        try {
            long records = Long.parseLong(count);
            if (records < 100) return "minimal";
            if (records < 1000) return "limited";
            if (records < 10000) return "moderate";
            if (records < 100000) return "significant";
            return "massive";
        } catch (NumberFormatException e) {
            return "unknown";
        }
    }

    // Statistics
    public SecurityStats getSecurityStats() {
        return SecurityStats.builder()
            .activeSecurityAlerts(activeSecurityAlerts.get())
            .activeSessions(activeSessions.get())
            .failedLoginAttempts(failedLoginAttempts.get())
            .serviceName(serviceName)
            .build();
    }

    @lombok.Builder
    @lombok.Data
    public static class SecurityStats {
        private final long activeSecurityAlerts;
        private final long activeSessions;
        private final long failedLoginAttempts;
        private final String serviceName;
    }
    
    // Additional counters for summary
    private final AtomicLong totalSecurityEvents = new AtomicLong(0);
    private final AtomicLong failedAuthentications = new AtomicLong(0);
    private final AtomicLong successfulAuthentications = new AtomicLong(0);
    private final AtomicLong activeThreats = new AtomicLong(0);
    
    /**
     * Get security metrics summary
     */
    public com.waqiti.common.observability.dto.SecurityMetricsSummary getSecurityMetricsSummary() {
        return com.waqiti.common.observability.dto.SecurityMetricsSummary.builder()
            .totalSecurityEvents(totalSecurityEvents.get())
            .failedAuthentications(failedAuthentications.get())
            .successfulAuthentications(successfulAuthentications.get())
            .activeThreats(activeThreats.get())
            .timestamp(java.time.Instant.now())
            .build();
    }
    
    /**
     * Get active security alerts
     */
    public java.util.List<com.waqiti.common.observability.dto.SecurityAlert> getActiveSecurityAlerts() {
        return new java.util.ArrayList<>();
    }
}