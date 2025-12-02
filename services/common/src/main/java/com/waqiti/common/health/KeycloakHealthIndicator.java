package com.waqiti.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Health indicator for Keycloak integration
 * Monitors Keycloak availability and performance
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
public class KeycloakHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;
    
    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;
    
    @Value("${keycloak.realm}")
    private String realm;
    
    // Metrics tracking
    private final AtomicLong totalHealthChecks = new AtomicLong(0);
    private final AtomicLong successfulHealthChecks = new AtomicLong(0);
    private final AtomicLong failedHealthChecks = new AtomicLong(0);
    private final AtomicReference<Instant> lastSuccessfulCheck = new AtomicReference<>(Instant.now());
    private final AtomicReference<Instant> lastFailedCheck = new AtomicReference<>();
    private final AtomicReference<Long> lastResponseTime = new AtomicReference<>(0L);
    
    // Circuit breaker
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private final AtomicReference<Instant> circuitOpenTime = new AtomicReference<>();
    private static final long CIRCUIT_BREAKER_THRESHOLD = 5;
    private static final Duration CIRCUIT_BREAKER_TIMEOUT = Duration.ofMinutes(1);
    
    @Override
    public Health health() {
        totalHealthChecks.incrementAndGet();
        
        // Check if circuit breaker is open
        if (isCircuitBreakerOpen()) {
            failedHealthChecks.incrementAndGet();
            return Health.down()
                    .withDetail("status", "Circuit breaker open")
                    .withDetail("reason", "Too many consecutive failures")
                    .withDetail("retry_after", circuitOpenTime.get().plus(CIRCUIT_BREAKER_TIMEOUT))
                    .withDetail("consecutive_failures", consecutiveFailures.get())
                    .build();
        }
        
        try {
            // Perform health check
            Instant startTime = Instant.now();
            Health.Builder healthBuilder = performHealthCheck();
            long responseTime = Duration.between(startTime, Instant.now()).toMillis();
            lastResponseTime.set(responseTime);
            
            // Add metrics
            healthBuilder
                    .withDetail("response_time_ms", responseTime)
                    .withDetail("total_checks", totalHealthChecks.get())
                    .withDetail("successful_checks", successfulHealthChecks.get())
                    .withDetail("failed_checks", failedHealthChecks.get())
                    .withDetail("success_rate", calculateSuccessRate())
                    .withDetail("last_successful_check", lastSuccessfulCheck.get())
                    .withDetail("keycloak_url", keycloakUrl)
                    .withDetail("realm", realm);
            
            if (lastFailedCheck.get() != null) {
                healthBuilder.withDetail("last_failed_check", lastFailedCheck.get());
            }
            
            // Reset circuit breaker on success
            consecutiveFailures.set(0);
            circuitOpenTime.set(null);
            successfulHealthChecks.incrementAndGet();
            lastSuccessfulCheck.set(Instant.now());
            
            return healthBuilder.build();
            
        } catch (Exception e) {
            log.error("Keycloak health check failed", e);
            failedHealthChecks.incrementAndGet();
            lastFailedCheck.set(Instant.now());
            consecutiveFailures.incrementAndGet();
            
            // Open circuit breaker if threshold reached
            if (consecutiveFailures.get() >= CIRCUIT_BREAKER_THRESHOLD) {
                circuitOpenTime.set(Instant.now());
                log.warn("Circuit breaker opened due to {} consecutive failures", consecutiveFailures.get());
            }
            
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("consecutive_failures", consecutiveFailures.get())
                    .withDetail("total_checks", totalHealthChecks.get())
                    .withDetail("failed_checks", failedHealthChecks.get())
                    .withDetail("last_successful_check", lastSuccessfulCheck.get())
                    .withException(e)
                    .build();
        }
    }
    
    private Health.Builder performHealthCheck() {
        Map<String, Object> details = new HashMap<>();
        
        // Check Keycloak main endpoint
        String healthUrl = keycloakUrl + "/health";
        ResponseEntity<Map> healthResponse = restTemplate.getForEntity(healthUrl, Map.class);
        
        if (healthResponse.getStatusCode() != HttpStatus.OK) {
            return Health.down()
                    .withDetail("keycloak_status", healthResponse.getStatusCode());
        }
        
        // Check realm endpoint
        String realmUrl = keycloakUrl + "/realms/" + realm;
        ResponseEntity<Map> realmResponse = restTemplate.getForEntity(realmUrl, Map.class);
        
        if (realmResponse.getStatusCode() != HttpStatus.OK) {
            return Health.down()
                    .withDetail("realm_status", realmResponse.getStatusCode())
                    .withDetail("realm", realm);
        }
        
        Map<String, Object> realmInfo = realmResponse.getBody();
        if (realmInfo != null) {
            details.put("realm_name", realmInfo.get("realm"));
            details.put("realm_display_name", realmInfo.get("displayName"));
            details.put("realm_enabled", realmInfo.get("enabled"));
            details.put("token_endpoint", realmInfo.get("token-service"));
            details.put("account_endpoint", realmInfo.get("account-service"));
        }
        
        // Check JWKS endpoint
        String jwksUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
        ResponseEntity<Map> jwksResponse = restTemplate.getForEntity(jwksUrl, Map.class);
        
        if (jwksResponse.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> jwks = jwksResponse.getBody();
            if (jwks != null && jwks.containsKey("keys")) {
                List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
                details.put("jwks_key_count", keys.size());
                details.put("jwks_available", true);
            }
        } else {
            details.put("jwks_available", false);
        }
        
        // Check OpenID configuration
        String openidUrl = keycloakUrl + "/realms/" + realm + "/.well-known/openid-configuration";
        ResponseEntity<Map> openidResponse = restTemplate.getForEntity(openidUrl, Map.class);
        
        if (openidResponse.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> openidConfig = openidResponse.getBody();
            if (openidConfig != null) {
                details.put("issuer", openidConfig.get("issuer"));
                details.put("authorization_endpoint", openidConfig.get("authorization_endpoint"));
                details.put("token_endpoint", openidConfig.get("token_endpoint"));
                details.put("userinfo_endpoint", openidConfig.get("userinfo_endpoint"));
                details.put("introspection_endpoint", openidConfig.get("introspection_endpoint"));
                details.put("openid_configuration_available", true);
            }
        } else {
            details.put("openid_configuration_available", false);
        }
        
        return Health.up()
                .withDetail("status", "Keycloak is healthy")
                .withDetails(details);
    }
    
    private boolean isCircuitBreakerOpen() {
        if (circuitOpenTime.get() == null) {
            return false;
        }
        
        Instant openTime = circuitOpenTime.get();
        if (Instant.now().isAfter(openTime.plus(CIRCUIT_BREAKER_TIMEOUT))) {
            // Circuit breaker timeout has passed, allow retry
            log.info("Circuit breaker timeout expired, allowing retry");
            circuitOpenTime.set(null);
            consecutiveFailures.set(0);
            return false;
        }
        
        return true;
    }
    
    private String calculateSuccessRate() {
        long total = totalHealthChecks.get();
        if (total == 0) {
            return "N/A";
        }
        long successful = successfulHealthChecks.get();
        double rate = (double) successful / total * 100;
        return String.format("%.2f%%", rate);
    }
    
    /**
     * Get current health metrics
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("total_health_checks", totalHealthChecks.get());
        metrics.put("successful_health_checks", successfulHealthChecks.get());
        metrics.put("failed_health_checks", failedHealthChecks.get());
        metrics.put("success_rate", calculateSuccessRate());
        metrics.put("last_response_time_ms", lastResponseTime.get());
        metrics.put("consecutive_failures", consecutiveFailures.get());
        metrics.put("circuit_breaker_open", isCircuitBreakerOpen());
        
        if (lastSuccessfulCheck.get() != null) {
            metrics.put("last_successful_check", lastSuccessfulCheck.get().toString());
        }
        
        if (lastFailedCheck.get() != null) {
            metrics.put("last_failed_check", lastFailedCheck.get().toString());
        }
        
        return metrics;
    }
    
    /**
     * Reset health metrics (for testing or maintenance)
     */
    public void resetMetrics() {
        totalHealthChecks.set(0);
        successfulHealthChecks.set(0);
        failedHealthChecks.set(0);
        consecutiveFailures.set(0);
        circuitOpenTime.set(null);
        lastResponseTime.set(0L);
        log.info("Keycloak health metrics reset");
    }
}