package com.waqiti.audit.service;

import com.waqiti.audit.domain.AuditLog;
import com.waqiti.audit.domain.SecurityEvent;
import com.waqiti.audit.domain.UserActivity;
import com.waqiti.audit.repository.SecurityEventRepository;
import com.waqiti.audit.repository.UserActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Engine for detecting suspicious activities and anomalies in audit logs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuspiciousActivityDetectionEngine {
    
    private final UserActivityRepository userActivityRepository;
    private final SecurityEventRepository securityEventRepository;
    
    // Thresholds for detection
    @Value("${audit.detection.failed-login-threshold:5}")
    private int failedLoginThreshold;
    
    @Value("${audit.detection.rapid-activity-threshold:100}")
    private int rapidActivityThreshold;
    
    @Value("${audit.detection.unusual-hour-start:22}")
    private int unusualHourStart;
    
    @Value("${audit.detection.unusual-hour-end:6}")
    private int unusualHourEnd;
    
    @Value("${audit.detection.geo-distance-threshold:500}")
    private double geoDistanceThreshold; // in kilometers
    
    @Value("${audit.detection.privilege-escalation-window:60}")
    private int privilegeEscalationWindow; // in minutes
    
    // Cache for user patterns
    private final Map<String, UserPattern> userPatternCache = new ConcurrentHashMap<>();
    
    /**
     * Analyze audit log for suspicious activity
     */
    public SuspiciousActivityResult analyzeSuspiciousActivity(AuditLog auditLog) {
        SuspiciousActivityResult result = new SuspiciousActivityResult();
        result.setAuditId(auditLog.getAuditId().toString());
        result.setUserId(auditLog.getUserId());
        result.setTimestamp(auditLog.getTimestamp());
        
        List<String> suspiciousIndicators = new ArrayList<>();
        double riskScore = 0.0;
        
        // Check for various suspicious patterns
        if (isFailedAuthenticationPattern(auditLog)) {
            suspiciousIndicators.add("FAILED_AUTH_PATTERN");
            riskScore += 30;
        }
        
        if (isUnusualTimeActivity(auditLog)) {
            suspiciousIndicators.add("UNUSUAL_TIME");
            riskScore += 20;
        }
        
        if (isRapidActivityPattern(auditLog)) {
            suspiciousIndicators.add("RAPID_ACTIVITY");
            riskScore += 25;
        }
        
        if (isPrivilegeEscalationAttempt(auditLog)) {
            suspiciousIndicators.add("PRIVILEGE_ESCALATION");
            riskScore += 40;
        }
        
        if (isDataExfiltrationPattern(auditLog)) {
            suspiciousIndicators.add("DATA_EXFILTRATION");
            riskScore += 35;
        }
        
        if (isUnusualAccessPattern(auditLog)) {
            suspiciousIndicators.add("UNUSUAL_ACCESS");
            riskScore += 15;
        }
        
        if (isGeoAnomalyDetected(auditLog)) {
            suspiciousIndicators.add("GEO_ANOMALY");
            riskScore += 30;
        }
        
        // Normalize risk score to 0-100
        riskScore = Math.min(100, riskScore);
        
        result.setSuspiciousIndicators(suspiciousIndicators);
        result.setRiskScore(riskScore);
        result.setIsSuspicious(!suspiciousIndicators.isEmpty());
        result.setSeverity(calculateSeverity(riskScore));
        result.setRecommendedActions(generateRecommendations(suspiciousIndicators, riskScore));
        
        // Update user pattern cache
        updateUserPattern(auditLog);
        
        return result;
    }
    
    /**
     * Check for failed authentication pattern
     */
    private boolean isFailedAuthenticationPattern(AuditLog auditLog) {
        if (!"LOGIN_ATTEMPT".equals(auditLog.getEventType())) {
            return false;
        }
        
        LocalDateTime window = auditLog.getTimestamp().minus(15, ChronoUnit.MINUTES);
        List<SecurityEvent> recentFailures = securityEventRepository
            .findFailedLoginAttempts(window).stream()
            .filter(e -> e.getUserId().equals(auditLog.getUserId()))
            .collect(Collectors.toList());
        
        return recentFailures.size() >= failedLoginThreshold;
    }
    
    /**
     * Check for unusual time activity
     */
    private boolean isUnusualTimeActivity(AuditLog auditLog) {
        int hour = auditLog.getTimestamp().getHour();
        return hour >= unusualHourStart || hour <= unusualHourEnd;
    }
    
    /**
     * Check for rapid activity pattern
     */
    private boolean isRapidActivityPattern(AuditLog auditLog) {
        LocalDateTime window = auditLog.getTimestamp().minus(5, ChronoUnit.MINUTES);
        List<UserActivity> recentActivities = userActivityRepository
            .findByUserIdAndTimestampBetween(auditLog.getUserId(), window, auditLog.getTimestamp());
        
        return recentActivities.size() >= rapidActivityThreshold;
    }
    
    /**
     * Check for privilege escalation attempt
     */
    private boolean isPrivilegeEscalationAttempt(AuditLog auditLog) {
        if (!"PERMISSION_CHANGE".equals(auditLog.getEventType()) && 
            !"ROLE_ASSIGNMENT".equals(auditLog.getEventType())) {
            return false;
        }
        
        // Check if user recently had access denied
        LocalDateTime window = auditLog.getTimestamp().minus(privilegeEscalationWindow, ChronoUnit.MINUTES);
        List<SecurityEvent> accessDeniedEvents = securityEventRepository
            .findByUserIdAndTimestampBetween(auditLog.getUserId(), window, auditLog.getTimestamp())
            .stream()
            .filter(e -> "ACCESS_DENIED".equals(e.getEventType()))
            .collect(Collectors.toList());
        
        return !accessDeniedEvents.isEmpty();
    }
    
    /**
     * Check for data exfiltration pattern
     */
    private boolean isDataExfiltrationPattern(AuditLog auditLog) {
        if (!"DATA_EXPORT".equals(auditLog.getEventType()) && 
            !"BULK_DOWNLOAD".equals(auditLog.getEventType())) {
            return false;
        }
        
        // Check for unusual volume of data access
        LocalDateTime window = auditLog.getTimestamp().minus(1, ChronoUnit.HOURS);
        List<UserActivity> dataAccessActivities = userActivityRepository
            .findByUserIdAndTimestampBetween(auditLog.getUserId(), window, auditLog.getTimestamp())
            .stream()
            .filter(a -> a.getActivityType() != null && 
                        (a.getActivityType().contains("EXPORT") || a.getActivityType().contains("DOWNLOAD")))
            .collect(Collectors.toList());
        
        return dataAccessActivities.size() > 10; // Threshold for suspicious data access
    }
    
    /**
     * Check for unusual access pattern
     */
    private boolean isUnusualAccessPattern(AuditLog auditLog) {
        UserPattern pattern = userPatternCache.get(auditLog.getUserId());
        if (pattern == null) {
            return false;
        }
        
        // Check if accessing unusual resources
        return !pattern.getCommonResources().contains(auditLog.getEntityType()) &&
               !pattern.getCommonActions().contains(auditLog.getAction());
    }
    
    /**
     * Check for geographical anomaly
     */
    private boolean isGeoAnomalyDetected(AuditLog auditLog) {
        if (auditLog.getSourceIpAddress() == null) {
            return false;
        }
        
        // Get last known location
        Optional<UserActivity> lastActivity = userActivityRepository
            .findTopByUserIdOrderByTimestampDesc(auditLog.getUserId());
        
        if (lastActivity.isEmpty() || lastActivity.get().getIpAddress() == null) {
            return false;
        }
        
        // Check for impossible travel (simplified - would need actual geo lookup)
        if (!lastActivity.get().getIpAddress().equals(auditLog.getSourceIpAddress())) {
            long minutesBetween = ChronoUnit.MINUTES.between(
                lastActivity.get().getTimestamp(), 
                auditLog.getTimestamp()
            );
            
            // If IP changed in less than 30 minutes, could be suspicious
            return minutesBetween < 30;
        }
        
        return false;
    }
    
    /**
     * Calculate severity based on risk score
     */
    private String calculateSeverity(double riskScore) {
        if (riskScore >= 75) return "CRITICAL";
        if (riskScore >= 50) return "HIGH";
        if (riskScore >= 25) return "MEDIUM";
        return "LOW";
    }
    
    /**
     * Generate recommendations based on indicators
     */
    private List<String> generateRecommendations(List<String> indicators, double riskScore) {
        List<String> recommendations = new ArrayList<>();
        
        if (indicators.contains("FAILED_AUTH_PATTERN")) {
            recommendations.add("Consider account lockout or CAPTCHA");
        }
        if (indicators.contains("PRIVILEGE_ESCALATION")) {
            recommendations.add("Review user permissions immediately");
        }
        if (indicators.contains("DATA_EXFILTRATION")) {
            recommendations.add("Monitor data access and consider blocking bulk exports");
        }
        if (indicators.contains("GEO_ANOMALY")) {
            recommendations.add("Verify user identity through additional authentication");
        }
        
        if (riskScore >= 75) {
            recommendations.add("Immediate security review required");
            recommendations.add("Consider temporary account suspension");
        }
        
        return recommendations;
    }
    
    /**
     * Update user pattern cache
     */
    private void updateUserPattern(AuditLog auditLog) {
        userPatternCache.compute(auditLog.getUserId(), (key, pattern) -> {
            if (pattern == null) {
                pattern = new UserPattern();
                pattern.setUserId(auditLog.getUserId());
            }
            
            pattern.getCommonResources().add(auditLog.getEntityType());
            pattern.getCommonActions().add(auditLog.getAction());
            pattern.getAccessTimes().add(auditLog.getTimestamp().getHour());
            pattern.getIpAddresses().add(auditLog.getSourceIpAddress());
            pattern.setLastActivity(auditLog.getTimestamp());
            
            // Keep only recent patterns (last 100 entries)
            if (pattern.getCommonResources().size() > 100) {
                pattern.setCommonResources(new HashSet<>(
                    pattern.getCommonResources().stream()
                        .limit(100)
                        .collect(Collectors.toList())
                ));
            }
            
            return pattern;
        });
    }
    
    /**
     * Result class for suspicious activity analysis
     */
    public static class SuspiciousActivityResult {
        private String auditId;
        private String userId;
        private LocalDateTime timestamp;
        private boolean isSuspicious;
        private List<String> suspiciousIndicators;
        private double riskScore;
        private String severity;
        private List<String> recommendedActions;
        
        // Getters and setters
        public String getAuditId() { return auditId; }
        public void setAuditId(String auditId) { this.auditId = auditId; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public boolean getIsSuspicious() { return isSuspicious; }
        public void setIsSuspicious(boolean isSuspicious) { this.isSuspicious = isSuspicious; }
        
        public List<String> getSuspiciousIndicators() { return suspiciousIndicators; }
        public void setSuspiciousIndicators(List<String> suspiciousIndicators) { 
            this.suspiciousIndicators = suspiciousIndicators; 
        }
        
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        
        public List<String> getRecommendedActions() { return recommendedActions; }
        public void setRecommendedActions(List<String> recommendedActions) { 
            this.recommendedActions = recommendedActions; 
        }
    }
    
    /**
     * User pattern class for tracking normal behavior
     */
    private static class UserPattern {
        private String userId;
        private Set<String> commonResources = new HashSet<>();
        private Set<String> commonActions = new HashSet<>();
        private Set<Integer> accessTimes = new HashSet<>();
        private Set<String> ipAddresses = new HashSet<>();
        private LocalDateTime lastActivity;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public Set<String> getCommonResources() { return commonResources; }
        public void setCommonResources(Set<String> commonResources) { 
            this.commonResources = commonResources; 
        }
        
        public Set<String> getCommonActions() { return commonActions; }
        public void setCommonActions(Set<String> commonActions) { 
            this.commonActions = commonActions; 
        }
        
        public Set<Integer> getAccessTimes() { return accessTimes; }
        public void setAccessTimes(Set<Integer> accessTimes) { 
            this.accessTimes = accessTimes; 
        }
        
        public Set<String> getIpAddresses() { return ipAddresses; }
        public void setIpAddresses(Set<String> ipAddresses) { 
            this.ipAddresses = ipAddresses; 
        }
        
        public LocalDateTime getLastActivity() { return lastActivity; }
        public void setLastActivity(LocalDateTime lastActivity) {
            this.lastActivity = lastActivity;
        }
    }

    /**
     * Detect anomaly in audit event based on pattern definition
     */
    public boolean detectAnomaly(AuditEvent auditEvent, String patternDefinition) {
        log.debug("Detecting anomaly: eventId={}, pattern={}", auditEvent.getId(), patternDefinition);

        try {
            // Check for anomalous patterns
            boolean isAnomaly = checkForAnomalousPattern(auditEvent, patternDefinition);

            if (isAnomaly) {
                log.warn("Anomaly detected: eventId={}, type={}, pattern={}",
                    auditEvent.getId(), auditEvent.getEventType(), patternDefinition);
            }

            return isAnomaly;

        } catch (Exception e) {
            log.error("Error detecting anomaly: eventId={}, error={}",
                auditEvent.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Detect pattern in audit event
     */
    public boolean detectPattern(AuditEvent auditEvent, String patternDefinition) {
        log.debug("Detecting pattern: eventId={}, pattern={}", auditEvent.getId(), patternDefinition);

        try {
            // Check if event matches the pattern definition
            boolean patternMatched = matchesPattern(auditEvent, patternDefinition);

            if (patternMatched) {
                log.info("Pattern detected: eventId={}, type={}, pattern={}",
                    auditEvent.getId(), auditEvent.getEventType(), patternDefinition);
            }

            return patternMatched;

        } catch (Exception e) {
            log.error("Error detecting pattern: eventId={}, error={}",
                auditEvent.getId(), e.getMessage());
            return false;
        }
    }

    private boolean checkForAnomalousPattern(AuditEvent auditEvent, String patternDefinition) {
        // Implementation: Check if event deviates from expected pattern
        if (patternDefinition == null) return false;

        // Simple anomaly detection based on severity and result
        if (auditEvent.getSeverity() == AuditEvent.AuditSeverity.CRITICAL) {
            return true;
        }

        if (auditEvent.getResult() == AuditEvent.AuditResult.SYSTEM_ERROR ||
            auditEvent.getResult() == AuditEvent.AuditResult.UNAUTHORIZED) {
            return true;
        }

        return false;
    }

    private boolean matchesPattern(AuditEvent auditEvent, String patternDefinition) {
        // Implementation: Check if event matches the specified pattern
        if (patternDefinition == null || auditEvent == null) return false;

        // Simple pattern matching based on event type and pattern
        String eventType = auditEvent.getEventType();
        if (eventType != null && eventType.contains(patternDefinition.toUpperCase())) {
            return true;
        }

        // Check action field
        String action = auditEvent.getAction();
        if (action != null && action.contains(patternDefinition.toUpperCase())) {
            return true;
        }

        return false;
    }
}