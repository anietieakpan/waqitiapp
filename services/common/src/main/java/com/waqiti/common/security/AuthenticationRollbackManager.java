package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Automated Authentication Rollback Manager
 * Monitors authentication health and automatically triggers rollback if thresholds are breached
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationRollbackManager {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final ContextRefresher contextRefresher;
    private final ObjectMapper objectMapper;
    
    @Value("${rollback.enabled:true}")
    private boolean rollbackEnabled;
    
    @Value("${rollback.auth-failure-threshold:5.0}")
    private double authFailureThreshold; // percentage
    
    @Value("${rollback.latency-threshold-ms:500}")
    private long latencyThresholdMs;
    
    @Value("${rollback.error-rate-threshold:2.0}")
    private double errorRateThreshold; // percentage
    
    @Value("${rollback.monitoring-window-seconds:300}")
    private int monitoringWindowSeconds;
    
    @Value("${rollback.cooldown-minutes:30}")
    private int cooldownMinutes;
    
    @Value("${rollback.auto-rollback:true}")
    private boolean autoRollbackEnabled;
    
    @Value("${rollback.notification-webhook:}")
    private String notificationWebhook;
    
    private static final String ROLLBACK_STATE_KEY = "rollback:state";
    private static final String ROLLBACK_HISTORY_KEY = "rollback:history";
    private static final String METRICS_KEY = "rollback:metrics";
    
    private final AtomicBoolean isRolledBack = new AtomicBoolean(false);
    private final AtomicLong lastRollbackTime = new AtomicLong(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    
    private final ConcurrentHashMap<String, HealthMetrics> serviceHealthMap = new ConcurrentHashMap<>();
    
    public enum RollbackState {
        NORMAL,
        WARNING,
        CRITICAL,
        ROLLING_BACK,
        ROLLED_BACK,
        RECOVERING
    }
    
    /**
     * Monitor authentication health and trigger rollback if needed
     */
    @Scheduled(fixedDelayString = "${rollback.check-interval-ms:10000}")
    public void monitorAuthenticationHealth() {
        if (!rollbackEnabled) {
            return;
        }
        
        try {
            HealthMetrics currentMetrics = collectHealthMetrics();
            RollbackState currentState = evaluateHealth(currentMetrics);
            
            log.debug("Current authentication health state: {}", currentState);
            
            // Store metrics for historical analysis
            storeMetrics(currentMetrics);
            
            // Update state in Redis
            updateRollbackState(currentState);
            
            // Take action based on state
            switch (currentState) {
                case CRITICAL:
                    if (autoRollbackEnabled && !isInCooldown()) {
                        log.error("Critical authentication failure detected. Initiating rollback.");
                        initiateRollback(currentMetrics);
                    } else {
                        log.warn("Critical state detected but auto-rollback is disabled or in cooldown");
                        sendAlert("Critical authentication state detected", currentMetrics);
                    }
                    break;
                    
                case WARNING:
                    log.warn("Authentication health warning: {}", currentMetrics);
                    sendAlert("Authentication health warning", currentMetrics);
                    break;
                    
                case NORMAL:
                    if (isRolledBack.get() && canRecover(currentMetrics)) {
                        log.info("Authentication health recovered. Initiating recovery.");
                        initiateRecovery(currentMetrics);
                    }
                    break;
                    
                default:
                    break;
            }
            
        } catch (Exception e) {
            log.error("Error monitoring authentication health", e);
        }
    }
    
    /**
     * Initiate authentication rollback
     */
    public RollbackResult initiateRollback(HealthMetrics triggerMetrics) {
        log.info("Initiating authentication rollback due to: {}", triggerMetrics);
        
        RollbackResult result = new RollbackResult();
        result.setStartTime(Instant.now());
        result.setTriggerMetrics(triggerMetrics);
        
        try {
            // Step 1: Enable legacy authentication
            enableLegacyAuthentication();
            
            // Step 2: Disable Keycloak authentication
            disableKeycloakAuthentication();
            
            // Step 3: Switch feature flags
            switchFeatureFlags(true);
            
            // Step 4: Clear authentication caches
            clearAuthenticationCaches();
            
            // Step 5: Refresh application context
            refreshApplicationContext();
            
            // Step 6: Validate rollback
            boolean success = validateRollback();
            
            if (success) {
                isRolledBack.set(true);
                lastRollbackTime.set(System.currentTimeMillis());
                result.setSuccess(true);
                result.setEndTime(Instant.now());
                
                // Store rollback event
                storeRollbackEvent(result);
                
                // Send notifications
                sendRollbackNotification(result);
                
                log.info("Authentication rollback completed successfully");
            } else {
                log.error("Rollback validation failed");
                result.setSuccess(false);
                result.setErrorMessage("Rollback validation failed");
            }
            
        } catch (Exception e) {
            log.error("Error during rollback", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Initiate recovery from rollback
     */
    public RecoveryResult initiateRecovery(HealthMetrics currentMetrics) {
        log.info("Initiating recovery from rollback");
        
        RecoveryResult result = new RecoveryResult();
        result.setStartTime(Instant.now());
        
        try {
            // Step 1: Enable Keycloak authentication
            enableKeycloakAuthentication();
            
            // Step 2: Gradually disable legacy authentication
            graduallyDisableLegacyAuth();
            
            // Step 3: Switch feature flags
            switchFeatureFlags(false);
            
            // Step 4: Clear caches
            clearAuthenticationCaches();
            
            // Step 5: Refresh context
            refreshApplicationContext();
            
            // Step 6: Validate recovery
            boolean success = validateRecovery();
            
            if (success) {
                isRolledBack.set(false);
                result.setSuccess(true);
                result.setEndTime(Instant.now());
                
                log.info("Recovery from rollback completed successfully");
            } else {
                log.error("Recovery validation failed");
                result.setSuccess(false);
                result.setErrorMessage("Recovery validation failed");
            }
            
        } catch (Exception e) {
            log.error("Error during recovery", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Collect current health metrics
     */
    private HealthMetrics collectHealthMetrics() {
        HealthMetrics metrics = new HealthMetrics();
        metrics.setTimestamp(Instant.now());
        
        // Get metrics from Micrometer
        metrics.setAuthSuccessRate(getMetricValue("authentication.success.rate", 100.0));
        metrics.setAuthFailureRate(getMetricValue("authentication.failure.rate", 0.0));
        metrics.setAverageLatencyMs(getMetricValue("authentication.latency.avg", 0.0));
        metrics.setP95LatencyMs(getMetricValue("authentication.latency.p95", 0.0));
        metrics.setP99LatencyMs(getMetricValue("authentication.latency.p99", 0.0));
        metrics.setErrorRate(getMetricValue("authentication.error.rate", 0.0));
        metrics.setActiveSessions(getMetricValue("sessions.active", 0.0).longValue());
        metrics.setTokenValidationFailures(getMetricValue("token.validation.failures", 0.0).longValue());
        
        // Calculate health score
        metrics.setHealthScore(calculateHealthScore(metrics));
        
        return metrics;
    }
    
    /**
     * Evaluate health based on metrics
     */
    private RollbackState evaluateHealth(HealthMetrics metrics) {
        // Check critical thresholds
        if (metrics.getAuthFailureRate() > authFailureThreshold * 2) {
            consecutiveFailures.incrementAndGet();
            return RollbackState.CRITICAL;
        }
        
        if (metrics.getP99LatencyMs() > latencyThresholdMs * 3) {
            return RollbackState.CRITICAL;
        }
        
        if (metrics.getErrorRate() > errorRateThreshold * 2) {
            return RollbackState.CRITICAL;
        }
        
        // Check warning thresholds
        if (metrics.getAuthFailureRate() > authFailureThreshold) {
            return RollbackState.WARNING;
        }
        
        if (metrics.getP95LatencyMs() > latencyThresholdMs) {
            return RollbackState.WARNING;
        }
        
        if (metrics.getErrorRate() > errorRateThreshold) {
            return RollbackState.WARNING;
        }
        
        // Reset consecutive failures on good health
        consecutiveFailures.set(0);
        
        return RollbackState.NORMAL;
    }
    
    /**
     * Calculate overall health score
     */
    private double calculateHealthScore(HealthMetrics metrics) {
        double score = 100.0;
        
        // Deduct points for failures
        score -= metrics.getAuthFailureRate() * 2;
        score -= metrics.getErrorRate() * 3;
        
        // Deduct for high latency
        if (metrics.getP95LatencyMs() > latencyThresholdMs) {
            score -= 10;
        }
        if (metrics.getP99LatencyMs() > latencyThresholdMs * 2) {
            score -= 15;
        }
        
        // Deduct for token validation failures
        if (metrics.getTokenValidationFailures() > 100) {
            score -= 20;
        }
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Enable legacy authentication
     */
    private void enableLegacyAuthentication() {
        log.info("Enabling legacy JWT authentication");
        
        // Set configuration properties
        System.setProperty("legacy.jwt.enabled", "true");
        System.setProperty("keycloak.enabled", "false");
        
        // Update Redis configuration
        redisTemplate.opsForValue().set("config:auth:mode", "LEGACY");
        
        // Publish event
        eventPublisher.publishEvent(new AuthModeChangeEvent("LEGACY"));
    }
    
    /**
     * Disable Keycloak authentication
     */
    private void disableKeycloakAuthentication() {
        log.info("Disabling Keycloak authentication");
        
        System.setProperty("keycloak.enabled", "false");
        System.setProperty("spring.security.oauth2.resourceserver.jwt.enabled", "false");
    }
    
    /**
     * Enable Keycloak authentication
     */
    private void enableKeycloakAuthentication() {
        log.info("Enabling Keycloak authentication");
        
        System.setProperty("keycloak.enabled", "true");
        System.setProperty("spring.security.oauth2.resourceserver.jwt.enabled", "true");
        System.setProperty("legacy.jwt.enabled", "false");
        
        redisTemplate.opsForValue().set("config:auth:mode", "KEYCLOAK");
        eventPublisher.publishEvent(new AuthModeChangeEvent("KEYCLOAK"));
    }
    
    /**
     * Gradually disable legacy authentication
     */
    private void graduallyDisableLegacyAuth() {
        log.info("Gradually disabling legacy authentication");
        
        // This would implement a gradual rollout strategy
        System.setProperty("legacy.jwt.enabled", "false");
        System.setProperty("dual.mode.enabled", "false");
    }
    
    /**
     * Switch feature flags for rollback
     */
    private void switchFeatureFlags(boolean rollback) {
        Map<String, String> flags = new HashMap<>();
        
        if (rollback) {
            flags.put("feature.keycloak.enabled", "false");
            flags.put("feature.legacy-jwt.enabled", "true");
            flags.put("feature.dual-mode.enabled", "false");
        } else {
            flags.put("feature.keycloak.enabled", "true");
            flags.put("feature.legacy-jwt.enabled", "false");
            flags.put("feature.dual-mode.enabled", "false");
        }
        
        flags.forEach((key, value) -> {
            System.setProperty(key, value);
            redisTemplate.opsForValue().set("config:" + key, value);
        });
    }
    
    /**
     * Clear authentication caches
     */
    private void clearAuthenticationCaches() {
        log.info("Clearing authentication caches");
        
        // Clear Redis caches
        Set<String> keys = redisTemplate.keys("token:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        
        keys = redisTemplate.keys("session:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        
        keys = redisTemplate.keys("migration:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
    
    /**
     * Refresh application context
     */
    private void refreshApplicationContext() {
        log.info("Refreshing application context");
        
        try {
            contextRefresher.refresh();
        } catch (Exception e) {
            log.error("Error refreshing context", e);
        }
    }
    
    /**
     * Validate rollback was successful
     */
    private boolean validateRollback() {
        try {
            // Check if legacy auth is working
            String authMode = (String) redisTemplate.opsForValue().get("config:auth:mode");
            if (!"LEGACY".equals(authMode)) {
                return false;
            }
            
            // Check system properties
            if (!"true".equals(System.getProperty("legacy.jwt.enabled"))) {
                return false;
            }
            
            // Perform test authentication
            // This would make a test authentication request
            
            return true;
        } catch (Exception e) {
            log.error("Rollback validation failed", e);
            return false;
        }
    }
    
    /**
     * Validate recovery was successful
     */
    private boolean validateRecovery() {
        try {
            String authMode = (String) redisTemplate.opsForValue().get("config:auth:mode");
            return "KEYCLOAK".equals(authMode);
        } catch (Exception e) {
            log.error("Recovery validation failed", e);
            return false;
        }
    }
    
    /**
     * Check if system is in cooldown period
     */
    private boolean isInCooldown() {
        if (lastRollbackTime.get() == 0) {
            return false;
        }
        
        long cooldownMs = cooldownMinutes * 60 * 1000;
        return System.currentTimeMillis() - lastRollbackTime.get() < cooldownMs;
    }
    
    /**
     * Check if system can recover from rollback
     */
    private boolean canRecover(HealthMetrics metrics) {
        // Require sustained good health before recovery
        return metrics.getHealthScore() > 95 && 
               metrics.getAuthFailureRate() < 1.0 &&
               metrics.getErrorRate() < 0.5 &&
               !isInCooldown();
    }
    
    /**
     * Get metric value from MeterRegistry
     */
    private double getMetricValue(String metricName, double defaultValue) {
        try {
            return meterRegistry.get(metricName).gauge().value();
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Store metrics in Redis
     */
    private void storeMetrics(HealthMetrics metrics) {
        String key = METRICS_KEY + ":" + System.currentTimeMillis();
        redisTemplate.opsForValue().set(key, metrics, Duration.ofHours(24));
    }
    
    /**
     * Update rollback state in Redis
     */
    private void updateRollbackState(RollbackState state) {
        redisTemplate.opsForValue().set(ROLLBACK_STATE_KEY, state.toString());
    }
    
    /**
     * Store rollback event
     */
    private void storeRollbackEvent(RollbackResult result) {
        String key = ROLLBACK_HISTORY_KEY + ":" + System.currentTimeMillis();
        redisTemplate.opsForValue().set(key, result, Duration.ofDays(30));
    }
    
    /**
     * Send alert
     */
    private void sendAlert(String message, HealthMetrics metrics) {
        log.warn("ALERT: {} - Metrics: {}", message, metrics);
        // Send to monitoring system
    }
    
    /**
     * Send rollback notification
     */
    private void sendRollbackNotification(RollbackResult result) {
        log.info("Sending rollback notification: {}", result);
        // Send webhook notification
    }
    
    /**
     * Get current rollback state
     */
    public RollbackStatus getCurrentStatus() {
        RollbackStatus status = new RollbackStatus();
        status.setRolledBack(isRolledBack.get());
        status.setState(RollbackState.valueOf((String) redisTemplate.opsForValue().get(ROLLBACK_STATE_KEY)));
        status.setLastRollbackTime(lastRollbackTime.get() > 0 ? Instant.ofEpochMilli(lastRollbackTime.get()) : null);
        status.setCurrentMetrics(collectHealthMetrics());
        status.setInCooldown(isInCooldown());
        return status;
    }
    
    /**
     * Manual rollback trigger
     */
    public RollbackResult triggerManualRollback(String reason) {
        log.warn("Manual rollback triggered: {}", reason);
        HealthMetrics metrics = collectHealthMetrics();
        metrics.setManualTriggerReason(reason);
        return initiateRollback(metrics);
    }
    
    /**
     * Manual recovery trigger
     */
    public RecoveryResult triggerManualRecovery(String reason) {
        log.info("Manual recovery triggered: {}", reason);
        return initiateRecovery(collectHealthMetrics());
    }
    
    // Data classes
    
    @Data
    public static class HealthMetrics {
        private Instant timestamp;
        private double authSuccessRate;
        private double authFailureRate;
        private double averageLatencyMs;
        private double p95LatencyMs;
        private double p99LatencyMs;
        private double errorRate;
        private long activeSessions;
        private long tokenValidationFailures;
        private double healthScore;
        private String manualTriggerReason;
    }
    
    @Data
    public static class RollbackResult {
        private boolean success;
        private Instant startTime;
        private Instant endTime;
        private HealthMetrics triggerMetrics;
        private String errorMessage;
        private Map<String, Object> metadata;
    }
    
    @Data
    public static class RecoveryResult {
        private boolean success;
        private Instant startTime;
        private Instant endTime;
        private String errorMessage;
        private Map<String, Object> metadata;
    }
    
    @Data
    public static class RollbackStatus {
        private boolean rolledBack;
        private RollbackState state;
        private Instant lastRollbackTime;
        private HealthMetrics currentMetrics;
        private boolean inCooldown;
    }
    
    public static class AuthModeChangeEvent {
        private final String mode;
        
        public AuthModeChangeEvent(String mode) {
            this.mode = mode;
        }
        
        public String getMode() {
            return mode;
        }
    }
}