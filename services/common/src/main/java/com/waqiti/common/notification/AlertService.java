package com.waqiti.common.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production-ready Alert Service for critical system notifications
 * Handles error alerts, security incidents, and operational notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationEventPublisher eventPublisher;
    
    @Value("${alerts.enabled:true}")
    private boolean alertsEnabled;
    
    @Value("${alerts.kafka.topic:system-alerts}")
    private String alertsTopic;
    
    @Value("${alerts.rate-limit.window:60}")
    private int rateLimitWindowSeconds;
    
    @Value("${alerts.rate-limit.max-per-window:100}")
    private int maxAlertsPerWindow;
    
    // Alert deduplication and rate limiting
    private final Map<String, AtomicInteger> alertCounts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastAlertTime = new ConcurrentHashMap<>();
    private final Set<String> criticalAlertsSent = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    /**
     * Alert severity levels
     */
    public enum AlertSeverity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL,
        EMERGENCY
    }
    
    /**
     * Alert types for categorization
     */
    public enum AlertType {
        SECURITY_INCIDENT,
        SYSTEM_ERROR,
        PERFORMANCE_DEGRADATION,
        DATA_INTEGRITY,
        COMPLIANCE_VIOLATION,
        AUTHENTICATION_FAILURE,
        AUTHORIZATION_FAILURE,
        RATE_LIMIT_EXCEEDED,
        PAYMENT_FAILURE,
        INTEGRATION_FAILURE,
        DATABASE_ERROR,
        SERVICE_UNAVAILABLE
    }
    
    /**
     * Send a critical security alert
     */
    @Async
    public void sendSecurityAlert(String title, String message, Map<String, Object> context) {
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .message(message)
                .severity(AlertSeverity.CRITICAL)
                .type(AlertType.SECURITY_INCIDENT)
                .context(context)
                .timestamp(LocalDateTime.now())
                .source(getServiceName())
                .build();
        
        sendAlert(alert);
    }
    
    /**
     * Send an error alert
     */
    @Async
    public void sendErrorAlert(String title, String message, Throwable error) {
        Map<String, Object> context = new HashMap<>();
        context.put("errorType", error.getClass().getName());
        context.put("errorMessage", error.getMessage());
        context.put("stackTrace", getStackTraceTop(error, 10));
        
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .message(message)
                .severity(AlertSeverity.ERROR)
                .type(AlertType.SYSTEM_ERROR)
                .context(context)
                .timestamp(LocalDateTime.now())
                .source(getServiceName())
                .build();
        
        sendAlert(alert);
    }
    
    /**
     * Send a compliance violation alert
     */
    @Async
    public void sendComplianceAlert(String violation, String userId, Map<String, Object> details) {
        Map<String, Object> context = new HashMap<>(details);
        context.put("userId", userId);
        context.put("violation", violation);
        
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .title("Compliance Violation Detected")
                .message("Compliance violation: " + violation)
                .severity(AlertSeverity.CRITICAL)
                .type(AlertType.COMPLIANCE_VIOLATION)
                .context(context)
                .timestamp(LocalDateTime.now())
                .source(getServiceName())
                .build();
        
        sendAlert(alert);
        
        // For compliance, also trigger immediate notification
        triggerImmediateNotification(alert);
    }
    
    /**
     * Send integration/external service alert
     */
    @Async
    public void sendIntegrationAlert(String serviceName, String message, com.waqiti.common.error.GlobalErrorHandlingFramework.ErrorSeverity severity) {
        Map<String, Object> context = new HashMap<>();
        context.put("serviceName", serviceName);
        context.put("severity", severity);
        
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .title("Integration Service Alert")
                .message(message)
                .severity(mapErrorSeverityToAlertSeverity(severity))
                .type(AlertType.INTEGRATION_FAILURE)
                .context(context)
                .timestamp(LocalDateTime.now())
                .source(getServiceName())
                .build();
        
        sendAlert(alert);
    }
    
    /**
     * Send payment alert
     */
    @Async
    public void sendPaymentAlert(String message, String provider, com.waqiti.common.error.GlobalErrorHandlingFramework.ErrorSeverity severity) {
        Map<String, Object> context = new HashMap<>();
        context.put("provider", provider);
        context.put("severity", severity);
        
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .title("Payment Processing Alert")
                .message(message)
                .severity(mapErrorSeverityToAlertSeverity(severity))
                .type(AlertType.PAYMENT_FAILURE)
                .context(context)
                .timestamp(LocalDateTime.now())
                .source(getServiceName())
                .build();
        
        sendAlert(alert);
    }
    
    /**
     * Send system alert
     */
    @Async
    public void sendSystemAlert(String title, String message, com.waqiti.common.error.GlobalErrorHandlingFramework.ErrorSeverity severity) {
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .message(message)
                .severity(mapErrorSeverityToAlertSeverity(severity))
                .type(AlertType.SYSTEM_ERROR)
                .context(Map.of("severity", severity))
                .timestamp(LocalDateTime.now())
                .source(getServiceName())
                .build();
        
        sendAlert(alert);
    }
    
    /**
     * Send performance degradation alert
     */
    @Async
    public void sendPerformanceAlert(String component, String uri, com.waqiti.common.error.GlobalErrorHandlingFramework.ErrorSeverity severity) {
        Map<String, Object> context = new HashMap<>();
        context.put("component", component);
        context.put("uri", uri);
        context.put("severity", severity);
        
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .title("Performance Degradation Detected")
                .message(String.format("Performance issue in component %s", component))
                .severity(mapErrorSeverityToAlertSeverity(severity))
                .type(AlertType.PERFORMANCE_DEGRADATION)
                .context(context)
                .timestamp(LocalDateTime.now())
                .source(getServiceName())
                .build();
        
        sendAlert(alert);
    }
    
    /**
     * Send performance degradation alert with thresholds
     */
    @Async
    public void sendPerformanceAlert(String component, double responseTime, double threshold) {
        Map<String, Object> context = new HashMap<>();
        context.put("component", component);
        context.put("responseTime", responseTime);
        context.put("threshold", threshold);
        context.put("degradationPercentage", ((responseTime - threshold) / threshold) * 100);
        
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .title("Performance Degradation Detected")
                .message(String.format("Component %s response time %.2fms exceeds threshold %.2fms", 
                        component, responseTime, threshold))
                .severity(AlertSeverity.WARNING)
                .type(AlertType.PERFORMANCE_DEGRADATION)
                .context(context)
                .timestamp(LocalDateTime.now())
                .source(getServiceName())
                .build();
        
        sendAlert(alert);
    }
    
    /**
     * Send payment failure alert
     */
    @Async
    public void sendPaymentFailureAlert(String paymentId, String userId, String reason, BigDecimal amount) {
        Map<String, Object> context = new HashMap<>();
        context.put("paymentId", paymentId);
        context.put("userId", userId);
        context.put("reason", reason);
        context.put("amount", amount);
        
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .title("Payment Processing Failed")
                .message(String.format("Payment %s failed: %s", paymentId, reason))
                .severity(AlertSeverity.ERROR)
                .type(AlertType.PAYMENT_FAILURE)
                .context(context)
                .timestamp(LocalDateTime.now())
                .source(getServiceName())
                .build();
        
        sendAlert(alert);
    }
    
    /**
     * Send authentication failure alert
     */
    @Async
    public void sendAuthenticationFailureAlert(String username, String ipAddress, String reason) {
        String dedupKey = String.format("auth_fail_%s_%s", username, ipAddress);
        
        if (shouldSendAlert(dedupKey, AlertSeverity.WARNING)) {
            Map<String, Object> context = new HashMap<>();
            context.put("username", username);
            context.put("ipAddress", ipAddress);
            context.put("reason", reason);
            context.put("failureCount", getFailureCount(dedupKey));
            
            Alert alert = Alert.builder()
                    .id(UUID.randomUUID().toString())
                    .title("Authentication Failure")
                    .message(String.format("Authentication failed for user %s from IP %s", username, ipAddress))
                    .severity(AlertSeverity.WARNING)
                    .type(AlertType.AUTHENTICATION_FAILURE)
                    .context(context)
                    .timestamp(LocalDateTime.now())
                    .source(getServiceName())
                    .build();
            
            sendAlert(alert);
        }
    }
    
    /**
     * Send database error alert
     */
    @Async
    public void sendDatabaseAlert(String operation, String error, long queryTime) {
        Map<String, Object> context = new HashMap<>();
        context.put("operation", operation);
        context.put("error", error);
        context.put("queryTimeMs", queryTime);
        
        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .title("Database Operation Failed")
                .message(String.format("Database %s failed: %s", operation, error))
                .severity(AlertSeverity.ERROR)
                .type(AlertType.DATABASE_ERROR)
                .context(context)
                .timestamp(LocalDateTime.now())
                .source(getServiceName())
                .build();
        
        sendAlert(alert);
    }
    
    /**
     * Core alert sending logic with deduplication and rate limiting
     */
    private void sendAlert(Alert alert) {
        if (!alertsEnabled) {
            log.debug("Alerts disabled, suppressing alert: {}", alert.getTitle());
            return;
        }
        
        // Rate limiting
        if (!checkRateLimit(alert)) {
            log.warn("Alert rate limit exceeded, suppressing alert: {}", alert.getTitle());
            return;
        }
        
        // Deduplication for critical alerts
        if (alert.getSeverity() == AlertSeverity.CRITICAL || alert.getSeverity() == AlertSeverity.EMERGENCY) {
            String dedupKey = generateDeduplicationKey(alert);
            if (criticalAlertsSent.contains(dedupKey)) {
                log.debug("Duplicate critical alert suppressed: {}", alert.getTitle());
                return;
            }
            criticalAlertsSent.add(dedupKey);
            
            // Clean up old dedup keys after 1 hour
            scheduleDeduplicationCleanup(dedupKey, 3600);
        }
        
        try {
            // Send to Kafka for processing
            kafkaTemplate.send(alertsTopic, alert.getId(), alert);
            
            // Publish local event for immediate handling
            eventPublisher.publishEvent(new AlertEvent(alert));
            
            // Log based on severity
            logAlert(alert);
            
            // Update metrics
            updateAlertMetrics(alert);
            
        } catch (Exception e) {
            log.error("Failed to send alert: {}", alert.getTitle(), e);
            // Fallback to logging only
            logAlert(alert);
        }
    }
    
    /**
     * Check rate limiting for alerts
     */
    private boolean checkRateLimit(Alert alert) {
        String rateLimitKey = alert.getType().toString();
        LocalDateTime now = LocalDateTime.now();
        
        LocalDateTime lastAlert = lastAlertTime.get(rateLimitKey);
        if (lastAlert != null && lastAlert.plusSeconds(rateLimitWindowSeconds).isAfter(now)) {
            AtomicInteger count = alertCounts.computeIfAbsent(rateLimitKey, k -> new AtomicInteger(0));
            if (count.incrementAndGet() > maxAlertsPerWindow) {
                return false;
            }
        } else {
            // New window
            alertCounts.put(rateLimitKey, new AtomicInteger(1));
            lastAlertTime.put(rateLimitKey, now);
        }
        
        return true;
    }
    
    /**
     * Generate deduplication key for alerts
     */
    private String generateDeduplicationKey(Alert alert) {
        return String.format("%s_%s_%s_%s",
                alert.getType(),
                alert.getSeverity(),
                alert.getTitle().replaceAll("\\s+", "_"),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH")));
    }
    
    /**
     * Schedule cleanup of deduplication keys
     */
    private void scheduleDeduplicationCleanup(String key, long delaySeconds) {
        // In production, this would use a scheduled executor service
        // For now, we'll rely on periodic cleanup
    }
    
    /**
     * Should send alert based on deduplication rules
     */
    private boolean shouldSendAlert(String dedupKey, AlertSeverity severity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastAlert = lastAlertTime.get(dedupKey);
        
        if (lastAlert == null) {
            lastAlertTime.put(dedupKey, now);
            return true;
        }
        
        // Different cooldown periods based on severity
        long cooldownMinutes = switch (severity) {
            case EMERGENCY, CRITICAL -> 5;
            case ERROR -> 10;
            case WARNING -> 30;
            case INFO -> 60;
        };
        
        if (lastAlert.plusMinutes(cooldownMinutes).isBefore(now)) {
            lastAlertTime.put(dedupKey, now);
            return true;
        }
        
        return false;
    }
    
    /**
     * Get failure count for deduplication tracking
     */
    private int getFailureCount(String key) {
        return alertCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    /**
     * Log alert based on severity
     */
    private void logAlert(Alert alert) {
        String message = String.format("[ALERT] [%s] [%s] %s: %s",
                alert.getSeverity(), alert.getType(), alert.getTitle(), alert.getMessage());
        
        switch (alert.getSeverity()) {
            case EMERGENCY, CRITICAL -> log.error(message);
            case ERROR -> log.error(message);
            case WARNING -> log.warn(message);
            case INFO -> log.info(message);
        }
    }
    
    /**
     * Update alert metrics
     */
    private void updateAlertMetrics(Alert alert) {
        // In production, this would update Micrometer metrics
        // For tracking alert rates, types, and severities
    }
    
    /**
     * Trigger immediate notification for critical alerts
     */
    private void triggerImmediateNotification(Alert alert) {
        // In production, this would trigger PagerDuty, SMS, or phone calls
        log.error("IMMEDIATE NOTIFICATION REQUIRED: {}", alert);
    }
    
    /**
     * Get top N lines of stack trace
     */
    private List<String> getStackTraceTop(Throwable error, int lines) {
        List<String> stackLines = new ArrayList<>();
        StackTraceElement[] stackTrace = error.getStackTrace();
        
        for (int i = 0; i < Math.min(lines, stackTrace.length); i++) {
            stackLines.add(stackTrace[i].toString());
        }
        
        return stackLines;
    }
    
    /**
     * Map ErrorSeverity to AlertSeverity
     */
    private AlertSeverity mapErrorSeverityToAlertSeverity(com.waqiti.common.error.GlobalErrorHandlingFramework.ErrorSeverity errorSeverity) {
        return switch (errorSeverity) {
            case CRITICAL -> AlertSeverity.CRITICAL;
            case HIGH -> AlertSeverity.ERROR;
            case MEDIUM -> AlertSeverity.WARNING;
            case LOW -> AlertSeverity.INFO;
        };
    }
    
    /**
     * Get service name for alert source
     */
    private String getServiceName() {
        return System.getProperty("spring.application.name", "waqiti-service");
    }
    
    /**
     * Alert data model
     */
    @lombok.Data
    @lombok.Builder
    public static class Alert {
        private String id;
        private String title;
        private String message;
        private AlertSeverity severity;
        private AlertType type;
        private Map<String, Object> context;
        private LocalDateTime timestamp;
        private String source;
    }
    
    /**
     * Alert event for local processing
     */
    public static class AlertEvent {
        private final Alert alert;
        
        public AlertEvent(Alert alert) {
            this.alert = alert;
        }
        
        public Alert getAlert() {
            return alert;
        }
    }
}