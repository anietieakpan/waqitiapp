package com.waqiti.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard controller for monitoring resilience patterns across all services
 * Provides comprehensive visibility into circuit breakers, retries, timeouts, and bulkheads
 */
@RestController
@RequestMapping("/api/v1/resilience")
@RequiredArgsConstructor
@Slf4j
public class ResilienceDashboardController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final ResilienceMetricsService metricsService;

    /**
     * Get comprehensive resilience dashboard overview
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'SRE', 'SUPPORT', 'SYSTEM')")
    public ResponseEntity<ResilienceDashboard> getDashboard() {
        log.debug("Generating resilience dashboard");
        
        ResilienceDashboard dashboard = ResilienceDashboard.builder()
            .timestamp(LocalDateTime.now())
            .overallHealth(calculateOverallHealth())
            .circuitBreakers(getCircuitBreakerSummaries())
            .retryPolicies(getRetryPolicySummaries())
            .timeLimiters(getTimeLimiterSummaries())
            .bulkheads(getBulkheadSummaries())
            .serviceHealth(getServiceHealthSummary())
            .metrics(metricsService.getHealthSummary())
            .alerts(getActiveAlerts())
            .build();
            
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Get circuit breaker details for a specific service
     */
    @GetMapping("/circuit-breakers/{serviceName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SRE', 'SUPPORT', 'SYSTEM')")
    public ResponseEntity<CircuitBreakerDetails> getCircuitBreakerDetails(@PathVariable String serviceName) {
        log.debug("Getting circuit breaker details for service: {}", serviceName);
        
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(serviceName)
            .orElseThrow(() -> new IllegalArgumentException("Circuit breaker not found: " + serviceName));
            
        CircuitBreakerDetails details = CircuitBreakerDetails.builder()
            .name(circuitBreaker.getName())
            .state(circuitBreaker.getState())
            .config(getCircuitBreakerConfig(circuitBreaker))
            .metrics(getCircuitBreakerMetrics(circuitBreaker))
            .events(getRecentEvents(circuitBreaker))
            .lastUpdated(LocalDateTime.now())
            .build();
            
        return ResponseEntity.ok(details);
    }

    /**
     * Manually transition circuit breaker state (for testing/emergency)
     */
    @PostMapping("/circuit-breakers/{serviceName}/transition")
    @PreAuthorize("hasAnyRole('ADMIN', 'SRE', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> transitionCircuitBreaker(
            @PathVariable String serviceName,
            @RequestBody Map<String, String> request) {
        
        String targetState = request.get("state");
        log.warn("Manual circuit breaker transition requested: {} -> {}", serviceName, targetState);
        
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.find(serviceName)
            .orElseThrow(() -> new IllegalArgumentException("Circuit breaker not found: " + serviceName));
        
        CircuitBreaker.State currentState = circuitBreaker.getState();
        
        try {
            switch (targetState.toUpperCase()) {
                case "OPEN":
                    circuitBreaker.transitionToOpenState();
                    break;
                case "CLOSED":
                    circuitBreaker.transitionToClosedState();
                    break;
                case "HALF_OPEN":
                    circuitBreaker.transitionToHalfOpenState();
                    break;
                default:
                    throw new IllegalArgumentException("Invalid target state: " + targetState);
            }
            
            Map<String, Object> response = Map.of(
                "service", serviceName,
                "previousState", currentState.toString(),
                "currentState", circuitBreaker.getState().toString(),
                "transitionedAt", LocalDateTime.now(),
                "success", true
            );
            
            log.warn("Circuit breaker {} manually transitioned: {} -> {}", 
                serviceName, currentState, circuitBreaker.getState());
                
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to transition circuit breaker {}: {}", serviceName, e.getMessage());
            
            Map<String, Object> response = Map.of(
                "service", serviceName,
                "currentState", currentState.toString(),
                "error", e.getMessage(),
                "success", false
            );
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get retry statistics for a specific service
     */
    @GetMapping("/retries/{serviceName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SRE', 'SUPPORT', 'SYSTEM')")
    public ResponseEntity<RetryDetails> getRetryDetails(@PathVariable String serviceName) {
        log.debug("Getting retry details for service: {}", serviceName);
        
        Retry retry = retryRegistry.find(serviceName)
            .orElseThrow(() -> new IllegalArgumentException("Retry policy not found: " + serviceName));
            
        RetryDetails details = RetryDetails.builder()
            .name(retry.getName())
            .config(getRetryConfig(retry))
            .metrics(getRetryMetrics(retry))
            .lastUpdated(LocalDateTime.now())
            .build();
            
        return ResponseEntity.ok(details);
    }

    /**
     * Get system-wide resilience health check
     */
    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'SRE', 'SUPPORT', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> getResilienceHealth() {
        log.debug("Generating resilience health check");
        
        List<CircuitBreakerSummary> openCircuitBreakers = circuitBreakerRegistry.getAllCircuitBreakers()
            .stream()
            .filter(cb -> cb.getState() == CircuitBreaker.State.OPEN)
            .map(this::createCircuitBreakerSummary)
            .collect(Collectors.toList());
            
        Status overallStatus = openCircuitBreakers.isEmpty() ? Status.UP : 
            openCircuitBreakers.size() > 3 ? Status.DOWN : Status.UP;
            
        Map<String, Object> health = Map.of(
            "status", overallStatus,
            "timestamp", LocalDateTime.now(),
            "openCircuitBreakers", openCircuitBreakers.size(),
            "totalCircuitBreakers", circuitBreakerRegistry.getAllCircuitBreakers().stream().collect(Collectors.toList()).size(),
            "criticalServicesDown", getCriticalServicesDown(openCircuitBreakers),
            "details", openCircuitBreakers.isEmpty() ? 
                "All circuit breakers healthy" : 
                "Some services experiencing issues"
        );
        
        return ResponseEntity.ok(health);
    }

    /**
     * Get resilience metrics for monitoring systems
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'SRE', 'SUPPORT', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> getResilienceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Circuit breaker metrics
        Map<String, Object> circuitBreakerMetrics = circuitBreakerRegistry.getAllCircuitBreakers()
            .stream()
            .collect(Collectors.toMap(
                CircuitBreaker::getName,
                cb -> Map.of(
                    "state", cb.getState().toString(),
                    "failureRate", cb.getMetrics().getFailureRate(),
                    "slowCallRate", cb.getMetrics().getSlowCallRate(),
                    "calls", cb.getMetrics().getNumberOfBufferedCalls()
                )
            ));
            
        metrics.put("circuitBreakers", circuitBreakerMetrics);
        
        // Retry metrics
        Map<String, Object> retryMetrics = retryRegistry.getAllRetries()
            .stream()
            .collect(Collectors.toMap(
                Retry::getName,
                retry -> Map.of(
                    "successWithoutRetry", retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                    "successWithRetry", retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt(),
                    "failedWithRetry", retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()
                )
            ));
            
        metrics.put("retries", retryMetrics);
        metrics.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(metrics);
    }

    // Private helper methods

    private String calculateOverallHealth() {
        long openCircuitBreakers = circuitBreakerRegistry.getAllCircuitBreakers()
            .stream()
            .filter(cb -> cb.getState() == CircuitBreaker.State.OPEN)
            .count();
            
        if (openCircuitBreakers == 0) return "HEALTHY";
        if (openCircuitBreakers <= 2) return "DEGRADED";
        return "CRITICAL";
    }

    private List<CircuitBreakerSummary> getCircuitBreakerSummaries() {
        return circuitBreakerRegistry.getAllCircuitBreakers()
            .stream()
            .map(this::createCircuitBreakerSummary)
            .collect(Collectors.toList());
    }

    private CircuitBreakerSummary createCircuitBreakerSummary(CircuitBreaker cb) {
        CircuitBreaker.Metrics metrics = cb.getMetrics();
        
        return CircuitBreakerSummary.builder()
            .name(cb.getName())
            .state(cb.getState().toString())
            .failureRate(Math.round(metrics.getFailureRate() * 100.0) / 100.0)
            .slowCallRate(Math.round(metrics.getSlowCallRate() * 100.0) / 100.0)
            .bufferedCalls(metrics.getNumberOfBufferedCalls())
            .failedCalls(metrics.getNumberOfFailedCalls())
            .successfulCalls(metrics.getNumberOfSuccessfulCalls())
            .notPermittedCalls(metrics.getNumberOfNotPermittedCalls())
            .build();
    }

    private List<RetryPolicySummary> getRetryPolicySummaries() {
        return retryRegistry.getAllRetries()
            .stream()
            .map(retry -> {
                Retry.Metrics metrics = retry.getMetrics();
                return RetryPolicySummary.builder()
                    .name(retry.getName())
                    .successWithoutRetry(metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt())
                    .successWithRetry(metrics.getNumberOfSuccessfulCallsWithRetryAttempt())
                    .failedWithoutRetry(metrics.getNumberOfFailedCallsWithoutRetryAttempt())
                    .failedWithRetry(metrics.getNumberOfFailedCallsWithRetryAttempt())
                    .build();
            })
            .collect(Collectors.toList());
    }

    private List<TimeLimiterSummary> getTimeLimiterSummaries() {
        return timeLimiterRegistry.getAllTimeLimiters()
            .stream()
            .map(tl -> {
                // In Resilience4j 2.x, TimeLimiter doesn't have metrics
                // We need to track these separately or use event listeners
                return TimeLimiterSummary.builder()
                    .name(tl.getName())
                    .successfulCalls(0L) // Would need to track via events
                    .failedCalls(0L) // Would need to track via events
                    .timeoutCalls(0L) // Would need to track via events
                    .build();
            })
            .collect(Collectors.toList());
    }

    private List<BulkheadSummary> getBulkheadSummaries() {
        return bulkheadRegistry.getAllBulkheads()
            .stream()
            .map(bh -> BulkheadSummary.builder()
                .name(bh.getName())
                .availableConcurrentCalls(bh.getMetrics().getAvailableConcurrentCalls())
                .maxAllowedConcurrentCalls(bh.getMetrics().getMaxAllowedConcurrentCalls())
                .build())
            .collect(Collectors.toList());
    }

    private Map<String, String> getServiceHealthSummary() {
        return circuitBreakerRegistry.getAllCircuitBreakers()
            .stream()
            .collect(Collectors.toMap(
                CircuitBreaker::getName,
                cb -> cb.getState() == CircuitBreaker.State.OPEN ? "DOWN" : "UP"
            ));
    }

    private List<String> getActiveAlerts() {
        List<String> alerts = new ArrayList<>();
        
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            if (cb.getState() == CircuitBreaker.State.OPEN) {
                alerts.add(String.format("Circuit breaker %s is OPEN", cb.getName()));
            }
            if (cb.getMetrics().getFailureRate() > 25.0f && cb.getState() == CircuitBreaker.State.CLOSED) {
                alerts.add(String.format("Circuit breaker %s has high failure rate: %.1f%%", 
                    cb.getName(), cb.getMetrics().getFailureRate()));
            }
        });
        
        return alerts;
    }

    private List<String> getCriticalServicesDown(List<CircuitBreakerSummary> openCircuitBreakers) {
        return openCircuitBreakers.stream()
            .filter(cb -> isCriticalService(cb.getName()))
            .map(CircuitBreakerSummary::getName)
            .collect(Collectors.toList());
    }

    private boolean isCriticalService(String serviceName) {
        return serviceName.contains("payment") || 
               serviceName.contains("wallet") || 
               serviceName.contains("ledger") || 
               serviceName.contains("fraud") ||
               serviceName.contains("compliance");
    }

    private Map<String, Object> getCircuitBreakerConfig(CircuitBreaker cb) {
        return Map.of(
            "failureRateThreshold", cb.getCircuitBreakerConfig().getFailureRateThreshold(),
            "slowCallRateThreshold", cb.getCircuitBreakerConfig().getSlowCallRateThreshold(),
            "waitDurationInOpenState", cb.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1),
            "slidingWindowSize", cb.getCircuitBreakerConfig().getSlidingWindowSize(),
            "minimumNumberOfCalls", cb.getCircuitBreakerConfig().getMinimumNumberOfCalls()
        );
    }

    private Map<String, Object> getCircuitBreakerMetrics(CircuitBreaker cb) {
        CircuitBreaker.Metrics metrics = cb.getMetrics();
        return Map.of(
            "failureRate", metrics.getFailureRate(),
            "slowCallRate", metrics.getSlowCallRate(),
            "bufferedCalls", metrics.getNumberOfBufferedCalls(),
            "failedCalls", metrics.getNumberOfFailedCalls(),
            "successfulCalls", metrics.getNumberOfSuccessfulCalls(),
            "slowCalls", metrics.getNumberOfSlowCalls(),
            "notPermittedCalls", metrics.getNumberOfNotPermittedCalls()
        );
    }

    private List<String> getRecentEvents(CircuitBreaker cb) {
        // This would collect recent events - simplified for now
        return List.of("Recent events would be shown here");
    }

    private Map<String, Object> getRetryConfig(Retry retry) {
        return Map.of(
            "maxAttempts", retry.getRetryConfig().getMaxAttempts(),
            "waitDuration", retry.getRetryConfig().getIntervalFunction().apply(1)
        );
    }

    private Map<String, Object> getRetryMetrics(Retry retry) {
        Retry.Metrics metrics = retry.getMetrics();
        return Map.of(
            "successWithoutRetry", metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt(),
            "successWithRetry", metrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
            "failedWithoutRetry", metrics.getNumberOfFailedCallsWithoutRetryAttempt(),
            "failedWithRetry", metrics.getNumberOfFailedCallsWithRetryAttempt()
        );
    }

    // Data classes for responses

    @lombok.Data
    @lombok.Builder
    public static class ResilienceDashboard {
        private LocalDateTime timestamp;
        private String overallHealth;
        private List<CircuitBreakerSummary> circuitBreakers;
        private List<RetryPolicySummary> retryPolicies;
        private List<TimeLimiterSummary> timeLimiters;
        private List<BulkheadSummary> bulkheads;
        private Map<String, String> serviceHealth;
        private ResilienceMetricsService.ServiceHealthSummary metrics;
        private List<String> alerts;
    }

    @lombok.Data
    @lombok.Builder
    public static class CircuitBreakerSummary {
        private String name;
        private String state;
        private Double failureRate;
        private Double slowCallRate;
        private Integer bufferedCalls;
        private Integer failedCalls;
        private Integer successfulCalls;
        private Long notPermittedCalls;
    }

    @lombok.Data
    @lombok.Builder
    public static class CircuitBreakerDetails {
        private String name;
        private CircuitBreaker.State state;
        private Map<String, Object> config;
        private Map<String, Object> metrics;
        private List<String> events;
        private LocalDateTime lastUpdated;
    }

    @lombok.Data
    @lombok.Builder
    public static class RetryPolicySummary {
        private String name;
        private Long successWithoutRetry;
        private Long successWithRetry;
        private Long failedWithoutRetry;
        private Long failedWithRetry;
    }

    @lombok.Data
    @lombok.Builder
    public static class RetryDetails {
        private String name;
        private Map<String, Object> config;
        private Map<String, Object> metrics;
        private LocalDateTime lastUpdated;
    }

    @lombok.Data
    @lombok.Builder
    public static class TimeLimiterSummary {
        private String name;
        private Long successfulCalls;
        private Long failedCalls;
        private Long timeoutCalls;
    }

    @lombok.Data
    @lombok.Builder
    public static class BulkheadSummary {
        private String name;
        private Integer availableConcurrentCalls;
        private Integer maxAllowedConcurrentCalls;
    }
}