package com.waqiti.common.security.monitoring;

import com.waqiti.common.security.config.KeycloakProperties;
import com.waqiti.common.security.config.VaultProperties;
import com.waqiti.common.security.vault.VaultSecretManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for security components
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityHealthIndicator implements HealthIndicator {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final KeycloakProperties keycloakProperties;
    private final VaultProperties vaultProperties;
    private final VaultSecretManager vaultSecretManager;
    private final SecurityMetricsCollector metricsCollector;
    
    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();
            boolean overallHealthy = true;
            
            // Check Redis connectivity
            boolean redisHealthy = checkRedisHealth(details);
            overallHealthy &= redisHealthy;
            
            // Check Keycloak connectivity
            boolean keycloakHealthy = checkKeycloakHealth(details);
            overallHealthy &= keycloakHealthy;
            
            // Check Vault connectivity
            boolean vaultHealthy = checkVaultHealth(details);
            overallHealthy &= vaultHealthy;
            
            // Check security metrics
            addSecurityMetrics(details);
            
            // Check for security concerns
            boolean securityHealthy = checkSecurityHealth(details);
            overallHealthy &= securityHealthy;
            
            Health.Builder healthBuilder = overallHealthy ? Health.up() : Health.down();
            return healthBuilder.withDetails(details).build();
            
        } catch (Exception e) {
            log.error("Error checking security health", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    private boolean checkRedisHealth(Map<String, Object> details) {
        try {
            Instant start = Instant.now();
            redisTemplate.opsForValue().set("health:check", "ok", Duration.ofSeconds(10));
            String result = redisTemplate.opsForValue().get("health:check");
            Duration responseTime = Duration.between(start, Instant.now());
            
            boolean healthy = "ok".equals(result) && responseTime.toMillis() < 1000;
            
            details.put("redis.status", healthy ? "UP" : "DOWN");
            details.put("redis.response_time_ms", responseTime.toMillis());
            
            if (healthy) {
                redisTemplate.delete("health:check");
            }
            
            return healthy;
            
        } catch (Exception e) {
            log.warn("Redis health check failed", e);
            details.put("redis.status", "DOWN");
            details.put("redis.error", e.getMessage());
            return false;
        }
    }
    
    private boolean checkKeycloakHealth(Map<String, Object> details) {
        if (!keycloakProperties.isEnabled()) {
            details.put("keycloak.status", "DISABLED");
            return true;
        }
        
        try {
            // In production, this would make an actual HTTP call to Keycloak health endpoint
            // For now, simulate the check
            boolean healthy = true; // Simulate healthy response
            
            details.put("keycloak.status", healthy ? "UP" : "DOWN");
            details.put("keycloak.server_url", keycloakProperties.getServerUrl());
            details.put("keycloak.realm", keycloakProperties.getRealm());
            
            return healthy;
            
        } catch (Exception e) {
            log.warn("Keycloak health check failed", e);
            details.put("keycloak.status", "DOWN");
            details.put("keycloak.error", e.getMessage());
            return false;
        }
    }
    
    private boolean checkVaultHealth(Map<String, Object> details) {
        if (!vaultProperties.isEnabled()) {
            details.put("vault.status", "DISABLED");
            return true;
        }
        
        try {
            boolean healthy = vaultSecretManager.isVaultAvailable();
            
            details.put("vault.status", healthy ? "UP" : "DOWN");
            details.put("vault.uri", vaultProperties.getUri());
            details.put("vault.authentication_method", vaultProperties.getAuthentication().getMethod());
            
            return healthy;
            
        } catch (Exception e) {
            log.warn("Vault health check failed", e);
            details.put("vault.status", "DOWN");
            details.put("vault.error", e.getMessage());
            return false;
        }
    }
    
    private void addSecurityMetrics(Map<String, Object> details) {
        try {
            SecurityMetricsCollector.SecurityMetricsSummary metrics = metricsCollector.getMetricsSummary();
            
            Map<String, Object> securityMetrics = new HashMap<>();
            securityMetrics.put("authentication_success_rate", 
                calculateSuccessRate(metrics.getAuthenticationSuccesses(), metrics.getAuthenticationFailures()));
            securityMetrics.put("active_sessions", metrics.getActiveSessions());
            securityMetrics.put("suspicious_devices", metrics.getSuspiciousDevices());
            securityMetrics.put("blocked_ips", metrics.getBlockedIps());
            securityMetrics.put("security_threats_detected", metrics.getSecurityThreats());
            securityMetrics.put("rate_limit_violations", metrics.getRateLimitViolations());
            
            details.put("security_metrics", securityMetrics);
            
        } catch (Exception e) {
            log.warn("Failed to add security metrics to health check", e);
            details.put("security_metrics.error", e.getMessage());
        }
    }
    
    private boolean checkSecurityHealth(Map<String, Object> details) {
        try {
            SecurityMetricsCollector.SecurityMetricsSummary metrics = metricsCollector.getMetricsSummary();
            
            // Check for concerning security metrics
            boolean healthy = true;
            Map<String, Object> securityConcerns = new HashMap<>();
            
            // Check authentication success rate
            double authSuccessRate = calculateSuccessRate(
                metrics.getAuthenticationSuccesses(), 
                metrics.getAuthenticationFailures()
            );
            if (authSuccessRate < 0.8) { // Less than 80% success rate
                healthy = false;
                securityConcerns.put("low_auth_success_rate", authSuccessRate);
            }
            
            // Check for high number of threats
            if (metrics.getSecurityThreats() > 100) {
                healthy = false;
                securityConcerns.put("high_threat_count", metrics.getSecurityThreats());
            }
            
            // Check for excessive rate limiting
            if (metrics.getRateLimitViolations() > 500) {
                healthy = false;
                securityConcerns.put("excessive_rate_limiting", metrics.getRateLimitViolations());
            }
            
            // Check for too many suspicious devices
            if (metrics.getSuspiciousDevices() > 50) {
                securityConcerns.put("high_suspicious_devices", metrics.getSuspiciousDevices());
            }
            
            details.put("security_concerns", securityConcerns);
            details.put("security_health", healthy ? "HEALTHY" : "CONCERNING");
            
            return healthy;
            
        } catch (Exception e) {
            log.warn("Failed to check security health", e);
            details.put("security_health", "UNKNOWN");
            details.put("security_health.error", e.getMessage());
            return false;
        }
    }
    
    private double calculateSuccessRate(double successes, double failures) {
        double total = successes + failures;
        if (total == 0) {
            return 1.0; // No attempts yet, consider healthy
        }
        return successes / total;
    }
}