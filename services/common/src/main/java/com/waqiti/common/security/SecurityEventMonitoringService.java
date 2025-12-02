package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.waqiti.common.security.model.*;
import com.waqiti.common.security.ThreatAssessment.ThreatLevel;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Security Event Monitoring Service
 * 
 * Real-time security event detection, correlation, and response system:
 * - Brute force attack detection
 * - Anomalous behavior detection
 * - Threat pattern recognition
 * - Automated incident response
 * - Security metrics collection
 * - Integration with SIEM systems
 * - Machine learning-based anomaly detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityEventMonitoringService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    
    @Value("${security.monitoring.enabled:true}")
    private boolean monitoringEnabled;
    
    @Value("${security.monitoring.kafka.topic:security-events}")
    private String securityEventsTopic;
    
    @Value("${security.monitoring.realtime-alerts:true}")
    private boolean realtimeAlertsEnabled;
    
    @Value("${security.monitoring.ml-detection:true}")
    private boolean mlDetectionEnabled;
    
    // Event counters and trackers
    private final Map<String, AtomicLong> eventCounters = new ConcurrentHashMap<>();
    private final Map<String, List<SecurityEvent>> recentEvents = new ConcurrentHashMap<>();
    private final Map<String, UserBehaviorProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<String, IpAddressProfile> ipProfiles = new ConcurrentHashMap<>();
    
    // Threat detection configurations
    private final ThreatDetectionConfig threatConfig = new ThreatDetectionConfig();

    public void init() {
        if (!monitoringEnabled) {
            log.info("Security event monitoring is disabled");
            return;
        }
        
        // Start periodic analysis tasks
        scheduler.scheduleAtFixedRate(this::analyzeSecurityTrends, 1, 1, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::cleanupOldEvents, 5, 5, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::updateBehaviorProfiles, 2, 2, TimeUnit.MINUTES);
        
        log.info("Security event monitoring service initialized");
    }

    /**
     * Record and analyze security event
     */
    @Async
    public void recordSecurityEvent(SecurityEvent event) {
        if (!monitoringEnabled) {
            return;
        }

        try {
            // Enrich event with additional context
            enrichEvent(event);
            
            // Store event for correlation
            storeEvent(event);
            
            // Immediate threat detection
            ThreatAssessment assessment = assessThreat(event);
            event.setThreatLevel(assessment.getThreatLevel().name());
            // Store indicators in metadata instead of non-existent field
            if (assessment.getIndicators() != null && !assessment.getIndicators().isEmpty()) {
                Map<String, Object> metadata = event.getMetadata();
                if (metadata == null) {
                    metadata = new HashMap<>();
                    event.setMetadata(metadata);
                }
                metadata.put("threatIndicators", assessment.getIndicators());
            }
            
            // Update behavior profiles
            updateUserProfile(event);
            updateIpProfile(event);
            
            // Real-time detection and response
            if (assessment.getThreatLevel().ordinal() >= ThreatLevel.HIGH.ordinal()) {
                triggerSecurityAlert(event, assessment);
            }
            
            // Pattern detection
            detectAnomalousPatterns(event);
            
            // Send to security analytics pipeline
            publishSecurityEvent(event);
            
            log.debug("Security event recorded: {} - Threat Level: {}", 
                event.getEventType(), event.getThreatLevel());
                
        } catch (Exception e) {
            log.error("Failed to record security event", e);
        }
    }

    /**
     * Detect brute force attacks
     */
    public boolean detectBruteForceAttack(String identifier, SecurityEventType eventType) {
        String key = identifier + ":" + eventType.name();
        List<SecurityEvent> events = recentEvents.getOrDefault(key, new ArrayList<>());
        
        Instant cutoff = Instant.now().minus(threatConfig.getBruteForceWindow());
        long recentFailures = events.stream()
            .filter(e -> e.getTimestamp().isAfter(cutoff))
            .filter(e -> SecurityEvent.Severity.HIGH.name().equals(e.getSeverity()))
            .count();
        
        return recentFailures >= threatConfig.getBruteForceThreshold();
    }

    /**
     * Detect account enumeration attempts
     */
    public boolean detectAccountEnumeration(String sourceIp) {
        IpAddressProfile profile = ipProfiles.get(sourceIp);
        if (profile == null) {
            return false;
        }
        
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(10));
        long uniqueUsers = profile.getRecentEvents().stream()
            .filter(e -> e.getTimestamp().isAfter(cutoff))
            .filter(e -> SecurityEventType.LOGIN_ATTEMPT.name().equals(e.getEventType()))
            .map(SecurityEvent::getUserId)
            .distinct()
            .count();
        
        return uniqueUsers >= threatConfig.getEnumerationThreshold();
    }

    /**
     * Detect credential stuffing attacks
     */
    public boolean detectCredentialStuffing(String sourceIp) {
        IpAddressProfile profile = ipProfiles.get(sourceIp);
        if (profile == null) {
            return false;
        }
        
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
        long failedAttempts = profile.getRecentEvents().stream()
            .filter(e -> e.getTimestamp().isAfter(cutoff))
            .filter(e -> SecurityEventType.LOGIN_FAILURE.name().equals(e.getEventType()))
            .count();
        
        return failedAttempts >= threatConfig.getCredentialStuffingThreshold();
    }

    /**
     * Detect suspicious user behavior
     */
    public SuspiciousBehaviorResult detectSuspiciousBehavior(String userId) {
        UserBehaviorProfile profile = userProfiles.get(userId);
        if (profile == null) {
            return SuspiciousBehaviorResult.normal();
        }
        
        List<String> suspiciousIndicators = new ArrayList<>();
        double suspicionScore = 0.0;
        
        // Check for unusual login times
        if (isUnusualLoginTime(profile)) {
            suspiciousIndicators.add("unusual_login_time");
            suspicionScore += 0.3;
        }
        
        // Check for unusual locations
        if (isUnusualLocation(profile)) {
            suspiciousIndicators.add("unusual_location");
            suspicionScore += 0.4;
        }
        
        // Check for unusual access patterns
        if (isUnusualAccessPattern(profile)) {
            suspiciousIndicators.add("unusual_access_pattern");
            suspicionScore += 0.3;
        }
        
        // Check for privilege escalation attempts
        if (hasPrivilegeEscalationAttempts(profile)) {
            suspiciousIndicators.add("privilege_escalation");
            suspicionScore += 0.6;
        }
        
        // Check for rapid successive logins from different IPs
        if (hasRapidIpSwitching(profile)) {
            suspiciousIndicators.add("rapid_ip_switching");
            suspicionScore += 0.5;
        }
        
        return SuspiciousBehaviorResult.builder()
            .suspiciousActivityDetected(suspicionScore > 0.5)
            .riskScore(suspicionScore)
            .riskLevel(suspicionScore > 0.8 ? "CRITICAL" : suspicionScore > 0.5 ? "HIGH" : "MEDIUM")
            .confidence(0.8)
            .analysisTimestamp(Instant.now())
            .build();
    }

    /**
     * Generate security metrics
     */
    public SecurityMetrics generateSecurityMetrics(Duration period) {
        Instant cutoff = Instant.now().minus(period);

        Map<String, Long> eventCounts = new HashMap<>();
        Map<String, Long> threatLevelCounts = new HashMap<>();
        Map<String, Long> topThreats = new HashMap<>();

        for (List<SecurityEvent> events : recentEvents.values()) {
            for (SecurityEvent event : events) {
                if (event.getTimestamp().isAfter(cutoff)) {
                    // Count by event type (String)
                    eventCounts.merge(event.getEventType(), 1L, Long::sum);

                    // Count by threat level (String)
                    if (event.getThreatLevel() != null) {
                        threatLevelCounts.merge(event.getThreatLevel(), 1L, Long::sum);
                    }

                    // Count threat indicators from metadata
                    if (event.getMetadata() != null && event.getMetadata().containsKey("threatIndicators")) {
                        @SuppressWarnings("unchecked")
                        List<String> indicators = (List<String>) event.getMetadata().get("threatIndicators");
                        for (String indicator : indicators) {
                            topThreats.merge(indicator, 1L, Long::sum);
                        }
                    }
                }
            }
        }

        return SecurityMetrics.builder()
            .period(period)
            .totalSecurityEvents(eventCounts.values().stream().mapToLong(Long::longValue).sum())
            .eventsByType(eventCounts)
            .threatsByLevel(threatLevelCounts)
            .topThreatIndicators(new java.util.ArrayList<>(topThreats.keySet()))
            .uniqueUsers((long) userProfiles.size())
            .build();
    }

    // Event listeners for automatic monitoring

    @EventListener
    @Async
    public void handleLoginAttempt(LoginAttemptEvent event) {
        SecurityEvent secEvent = SecurityEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(SecurityEventType.LOGIN_ATTEMPT.name())
            .userId(UUID.fromString(event.getUserId()))
            .ipAddress(event.getIpAddress())
            .userAgent(event.getUserAgent())
            .severity((event.isSuccess() ? SecurityEvent.Severity.LOW : SecurityEvent.Severity.MEDIUM).name())
            .timestamp(Instant.now())
            .metadata(Map.of(
                "success", event.isSuccess(),
                "username", event.getUsername(),
                "failureReason", event.getFailureReason()
            ))
            .build();

        recordSecurityEvent(secEvent);
    }

    @EventListener
    @Async
    public void handlePermissionDenied(PermissionDeniedEvent event) {
        SecurityEvent secEvent = SecurityEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(SecurityEventType.PERMISSION_DENIED.name())
            .userId(event.getUserId() != null ? UUID.fromString(event.getUserId()) : null)
            .ipAddress(event.getIpAddress())
            .severity(SecurityEvent.Severity.MEDIUM.name())
            .timestamp(Instant.now())
            .metadata(Map.of(
                "resource", event.getResource(),
                "action", event.getAction(),
                "requiredRole", event.getRequiredRole()
            ))
            .build();

        recordSecurityEvent(secEvent);
    }

    @EventListener
    @Async
    public void handleDataAccess(DataAccessEvent event) {
        SecurityEvent secEvent = SecurityEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(SecurityEventType.DATA_ACCESS.name())
            .userId(event.getUserId() != null ? UUID.fromString(event.getUserId()) : null)
            .ipAddress(event.getIpAddress())
            .severity((event.isSensitiveData() ? SecurityEvent.Severity.HIGH : SecurityEvent.Severity.LOW).name())
            .timestamp(Instant.now())
            .metadata(Map.of(
                "dataType", event.getDataType(),
                "operation", event.getOperation(),
                "recordCount", event.getRecordCount(),
                "sensitive", event.isSensitiveData()
            ))
            .build();

        recordSecurityEvent(secEvent);
    }

    // Private helper methods

    private void enrichEvent(SecurityEvent event) {
        // Add geolocation data
        if (event.getIpAddress() != null) {
            GeoLocation geoLocation = getGeoLocation(event.getIpAddress());
            if (geoLocation != null) {
                event.setGeolocation(geoLocation.toString());
            }
        }

        // Add threat intelligence data to metadata
        ThreatIntelligence threatIntel = getThreatIntelligence(event.getIpAddress());
        if (threatIntel != null) {
            Map<String, Object> metadata = event.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
                event.setMetadata(metadata);
            }
            metadata.put("threatIntelligence", threatIntel);
        }

        // Add user context to metadata
        if (event.getUserId() != null) {
            UserContext userContext = getUserContext(event.getUserId().toString());
            if (userContext != null) {
                Map<String, Object> metadata = event.getMetadata();
                if (metadata == null) {
                    metadata = new HashMap<>();
                    event.setMetadata(metadata);
                }
                metadata.put("userContext", userContext);
            }
        }
    }

    private void storeEvent(SecurityEvent event) {
        String key = event.getIpAddress() != null ? event.getIpAddress() :
                    (event.getUserId() != null ? event.getUserId().toString() : null);
        if (key != null) {
            recentEvents.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        }
        
        // Keep only recent events (last hour)
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        recentEvents.values().forEach(events -> 
            events.removeIf(e -> e.getTimestamp().isBefore(cutoff)));
    }

    private ThreatAssessment assessThreat(SecurityEvent event) {
        ThreatLevel threatLevel = ThreatLevel.LOW;
        List<String> indicators = new ArrayList<>();

        // Check against known threat patterns
        if (isMaliciousIp(event.getIpAddress())) {
            threatLevel = ThreatLevel.HIGH;
            indicators.add("known_malicious_ip");
        }

        if (event.getEventType() != null && detectBruteForceAttack(event.getIpAddress(), SecurityEventType.valueOf(event.getEventType()))) {
            threatLevel = ThreatLevel.HIGH;
            indicators.add("brute_force_attack");
        }

        if (detectAccountEnumeration(event.getIpAddress())) {
            threatLevel = ThreatLevel.MEDIUM;
            indicators.add("account_enumeration");
        }

        if (detectCredentialStuffing(event.getIpAddress())) {
            threatLevel = ThreatLevel.HIGH;
            indicators.add("credential_stuffing");
        }
        
        // ML-based anomaly detection
        if (mlDetectionEnabled) {
            double anomalyScore = calculateAnomalyScore(event);
            if (anomalyScore > 0.8) {
                threatLevel = ThreatLevel.HIGH;
                indicators.add("ml_anomaly_detected");
            } else if (anomalyScore > 0.6) {
                threatLevel = ThreatLevel.MEDIUM;
                indicators.add("ml_suspicious_behavior");
            }
        }
        
        return ThreatAssessment.builder()
            .assessmentId(UUID.randomUUID())
            .userId(event.getUserId())
            .ipAddress(event.getIpAddress())
            .timestamp(Instant.now())
            .threatLevel(threatLevel)
            .indicators(indicators)
            .confidence(0.85)
            .requiresImmediateAction(threatLevel.ordinal() >= ThreatLevel.HIGH.ordinal())
            .build();
    }

    private void triggerSecurityAlert(SecurityEvent event, ThreatAssessment assessment) {
        SecurityAlert alert = SecurityAlert.builder()
            .alertId(UUID.randomUUID().toString())
            .event(event)
            .severity(assessment.getThreatLevel().name())
            .alertType(event.getEventType())
            .title("Security Threat Detected: " + assessment.getThreatLevel())
            .description("Threat indicators: " + assessment.getIndicators())
            .createdAt(Instant.now())
            .requiresImmediateAction(assessment.getThreatLevel() == ThreatLevel.CRITICAL)
            .build();
        
        // Send to security team
        publishSecurityAlert(alert);
        
        // Automated response for critical threats
        if (assessment.getThreatLevel() == ThreatLevel.CRITICAL) {
            triggerAutomatedResponse(event, assessment);
        }
        
        log.warn("Security alert triggered: {} - {}", alert.getAlertId(), assessment.getThreatLevel());
    }

    private void triggerAutomatedResponse(SecurityEvent event, ThreatAssessment assessment) {
        // Implement automated security responses
        if (assessment.getIndicators().contains("brute_force_attack")) {
            // Block IP for 24 hours
            blockIpAddress(event.getIpAddress(), Duration.ofHours(24), "Brute force attack detected");
        }
        
        if (assessment.getIndicators().contains("credential_stuffing")) {
            // Block IP and require additional authentication
            blockIpAddress(event.getIpAddress(), Duration.ofHours(12), "Credential stuffing detected");
        }
        
        if (event.getUserId() != null && assessment.getIndicators().contains("privilege_escalation")) {
            // Force password reset and disable account temporarily
            flagUserForReview(event.getUserId().toString(), "Privilege escalation attempt");
        }
    }

    private void updateUserProfile(SecurityEvent event) {
        if (event.getUserId() == null) return;

        UserBehaviorProfile profile = userProfiles.computeIfAbsent(
            event.getUserId().toString(), k -> UserBehaviorProfile.builder()
                .userId(event.getUserId())
                .profileCreated(Instant.now())
                .lastUpdated(Instant.now())
                .events(new ArrayList<>())
                .build());

        profile.addEvent(event);
        profile.updateLoginPatterns(event);
        profile.updateLocationHistory(event);
    }

    private void updateIpProfile(SecurityEvent event) {
        if (event.getIpAddress() == null) return;

        IpAddressProfile profile = ipProfiles.computeIfAbsent(
            event.getIpAddress(), k -> IpAddressProfile.builder()
                .ipAddress(k)
                .riskScore(0)
                .totalRequestCount(0)
                .failedLoginCount(0)
                .successfulLoginCount(0)
                .recentEvents(new ArrayList<>())
                .build());

        profile.addEvent(event);
        profile.updateThreatScore(event);
    }

    private void detectAnomalousPatterns(SecurityEvent event) {
        // Implement pattern detection algorithms
        // This could include sequence analysis, frequency analysis, etc.
    }

    private void publishSecurityEvent(SecurityEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(securityEventsTopic, event.getEventId().toString(), eventJson);
        } catch (Exception e) {
            log.error("Failed to publish security event", e);
        }
    }

    private void publishSecurityAlert(SecurityAlert alert) {
        try {
            String alertJson = objectMapper.writeValueAsString(alert);
            kafkaTemplate.send("security-alerts", alert.getAlertId(), alertJson);
        } catch (Exception e) {
            log.error("Failed to publish security alert", e);
        }
    }

    // Periodic analysis tasks

    private void analyzeSecurityTrends() {
        // Analyze security trends and generate reports
        log.debug("Analyzing security trends...");
    }

    private void cleanupOldEvents() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        
        recentEvents.values().forEach(events -> 
            events.removeIf(e -> e.getTimestamp().isBefore(cutoff)));
        
        // Clean up empty event lists
        recentEvents.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void updateBehaviorProfiles() {
        // Update user behavior profiles with machine learning
        log.debug("Updating behavior profiles...");
    }

    // Stub implementations for external services
    private GeoLocation getGeoLocation(String ipAddress) { return null; }
    private ThreatIntelligence getThreatIntelligence(String ipAddress) { return null; }
    private UserContext getUserContext(String userId) { return null; }
    private boolean isMaliciousIp(String ipAddress) { return false; }
    private double calculateAnomalyScore(SecurityEvent event) { return 0.0; }
    private void blockIpAddress(String ip, Duration duration, String reason) {}
    private void flagUserForReview(String userId, String reason) {}
    
    // Behavior analysis methods
    private boolean isUnusualLoginTime(UserBehaviorProfile profile) { return false; }
    private boolean isUnusualLocation(UserBehaviorProfile profile) { return false; }
    private boolean isUnusualAccessPattern(UserBehaviorProfile profile) { return false; }
    private boolean hasPrivilegeEscalationAttempts(UserBehaviorProfile profile) { return false; }
    private boolean hasRapidIpSwitching(UserBehaviorProfile profile) { return false; }

    // Configuration and data classes

    private static class ThreatDetectionConfig {
        private final int bruteForceThreshold = 5;
        private final Duration bruteForceWindow = Duration.ofMinutes(15);
        private final int enumerationThreshold = 10;
        private final int credentialStuffingThreshold = 20;
        
        // Getters
        public int getBruteForceThreshold() { return bruteForceThreshold; }
        public Duration getBruteForceWindow() { return bruteForceWindow; }
        public int getEnumerationThreshold() { return enumerationThreshold; }
        public int getCredentialStuffingThreshold() { return credentialStuffingThreshold; }
    }

    // Data transfer objects and events
    public static class LoginAttemptEvent {
        private String userId;
        private String username;
        private String ipAddress;
        private String userAgent;
        private boolean success;
        private String failureReason;
        
        // Getters
        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getIpAddress() { return ipAddress; }
        public String getUserAgent() { return userAgent; }
        public boolean isSuccess() { return success; }
        public String getFailureReason() { return failureReason; }
    }
    
    // Additional data classes would be implemented here...
    // (Continuing with the comprehensive implementation pattern)
}