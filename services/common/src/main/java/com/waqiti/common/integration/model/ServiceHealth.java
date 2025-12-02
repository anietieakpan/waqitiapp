package com.waqiti.common.integration.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive service health monitoring with advanced metrics and alerting
 * Tracks service availability, performance, and provides predictive health analysis
 *
 * <p>Thread Safety: NOT thread-safe - external synchronization required for concurrent access</p>
 * <p>Note: Uses manual factory method createServiceHealth() instead of Lombok @Builder
 * due to final fields with initializers (AtomicInteger/AtomicLong metrics)</p>
 */
@Data
public class ServiceHealth {

    // Basic health information
    private String serviceName;
    private HealthStatus status;
    private String statusMessage;
    private double healthScore; // 0-100 composite health score

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant lastChecked;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant lastHealthy;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant lastUnhealthy;

    // Health check configuration
    private Duration checkInterval;
    private Duration checkTimeout;
    private int failureThreshold;
    private int recoveryThreshold;

    // Health metrics - final fields with initializers
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);

    // Performance metrics
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private long minResponseTime = Long.MAX_VALUE;
    private long maxResponseTime = 0;
    private double responseTimeP50;
    private double responseTimeP95;
    private double responseTimeP99;

    // Availability metrics
    private double uptimePercentage24h;
    private double uptimePercentage7d;
    private double uptimePercentage30d;
    private Duration totalDowntime24h;
    private Duration totalDowntime7d;
    private Duration totalDowntime30d;

    // Error tracking
    private Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private Map<String, Instant> lastErrorOccurrence = new ConcurrentHashMap<>();
    private List<HealthEvent> recentEvents;

    // Circuit breaker state
    private CircuitBreakerState circuitBreakerState;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Instant circuitBreakerStateChanged;

    private int circuitBreakerFailCount;

    // Dependencies health
    private Map<String, DependencyHealth> dependenciesHealth = new ConcurrentHashMap<>();

    // Alerting
    private List<HealthAlert> activeAlerts;
    private Map<String, Instant> alertCooldowns = new ConcurrentHashMap<>();

    // Predictive analysis
    private HealthTrend trend;
    private double degradationRisk; // 0-1 probability of degradation
    private List<String> riskFactors;
    private Instant predictedDowntime;

    /**
     * Builder pattern implementation with proper final field initialization
     */
    // Custom builder implementation below - Lombok builder disabled
    private static ServiceHealth createServiceHealth(
            String serviceName,
            HealthStatus status,
            String statusMessage,
            double healthScore,
            Instant lastChecked,
            Instant lastHealthy,
            Instant lastUnhealthy,
            Duration checkInterval,
            Duration checkTimeout,
            int failureThreshold,
            int recoveryThreshold,
            long minResponseTime,
            long maxResponseTime,
            double responseTimeP50,
            double responseTimeP95,
            double responseTimeP99,
            double uptimePercentage24h,
            double uptimePercentage7d,
            double uptimePercentage30d,
            Duration totalDowntime24h,
            Duration totalDowntime7d,
            Duration totalDowntime30d,
            Map<String, AtomicLong> errorCounts,
            Map<String, Instant> lastErrorOccurrence,
            List<HealthEvent> recentEvents,
            CircuitBreakerState circuitBreakerState,
            Instant circuitBreakerStateChanged,
            int circuitBreakerFailCount,
            Map<String, DependencyHealth> dependenciesHealth,
            List<HealthAlert> activeAlerts,
            Map<String, Instant> alertCooldowns,
            HealthTrend trend,
            double degradationRisk,
            List<String> riskFactors,
            Instant predictedDowntime
    ) {
        ServiceHealth health = new ServiceHealth(serviceName);
        health.status = status;
        health.statusMessage = statusMessage;
        health.healthScore = healthScore;
        health.lastChecked = lastChecked;
        health.lastHealthy = lastHealthy;
        health.lastUnhealthy = lastUnhealthy;
        health.checkInterval = checkInterval;
        health.checkTimeout = checkTimeout;
        health.failureThreshold = failureThreshold;
        health.recoveryThreshold = recoveryThreshold;
        health.minResponseTime = minResponseTime != 0 ? minResponseTime : Long.MAX_VALUE;
        health.maxResponseTime = maxResponseTime;
        health.responseTimeP50 = responseTimeP50;
        health.responseTimeP95 = responseTimeP95;
        health.responseTimeP99 = responseTimeP99;
        health.uptimePercentage24h = uptimePercentage24h;
        health.uptimePercentage7d = uptimePercentage7d;
        health.uptimePercentage30d = uptimePercentage30d;
        health.totalDowntime24h = totalDowntime24h;
        health.totalDowntime7d = totalDowntime7d;
        health.totalDowntime30d = totalDowntime30d;
        health.errorCounts = errorCounts != null ? errorCounts : new ConcurrentHashMap<>();
        health.lastErrorOccurrence = lastErrorOccurrence != null ? lastErrorOccurrence : new ConcurrentHashMap<>();
        health.recentEvents = recentEvents;
        health.circuitBreakerState = circuitBreakerState;
        health.circuitBreakerStateChanged = circuitBreakerStateChanged;
        health.circuitBreakerFailCount = circuitBreakerFailCount;
        health.dependenciesHealth = dependenciesHealth != null ? dependenciesHealth : new ConcurrentHashMap<>();
        health.activeAlerts = activeAlerts;
        health.alertCooldowns = alertCooldowns != null ? alertCooldowns : new ConcurrentHashMap<>();
        health.trend = trend;
        health.degradationRisk = degradationRisk;
        health.riskFactors = riskFactors;
        health.predictedDowntime = predictedDowntime;

        // Final fields are initialized via field initializers automatically
        return health;
    }

    /**
     * Factory method for creating ServiceHealth instances
     * Use: ServiceHealth health = new ServiceHealth("serviceName");
     * Or use the createServiceHealth() static factory method for full initialization
     */

    /**
     * Health status enumeration
     */
    public enum HealthStatus {
        HEALTHY("Service is operating normally"),
        DEGRADED("Service is experiencing issues but still functional"),
        UNHEALTHY("Service is not functioning properly"),
        CRITICAL("Service is in critical state"),
        UNKNOWN("Health status cannot be determined"),
        MAINTENANCE("Service is under maintenance");
        
        private final String description;
        
        HealthStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isOperational() {
            return this == HEALTHY || this == DEGRADED;
        }
        
        public boolean requiresAttention() {
            return this == UNHEALTHY || this == CRITICAL;
        }
    }
    
    /**
     * Circuit breaker states
     */
    public enum CircuitBreakerState {
        CLOSED,    // Normal operation
        OPEN,      // Failing fast
        HALF_OPEN  // Testing recovery
    }
    
    /**
     * Health trend analysis
     */
    public enum HealthTrend {
        IMPROVING("Health metrics are improving"),
        STABLE("Health metrics are stable"),
        DEGRADING("Health metrics are degrading"),
        VOLATILE("Health metrics are unstable");
        
        private final String description;
        
        HealthTrend(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Health event for audit trail
     */
    @Data
    @Builder
    @Jacksonized
    public static class HealthEvent {
        private String eventType;
        private HealthStatus previousStatus;
        private HealthStatus newStatus;
        private String description;
        private Map<String, Object> details;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        private Instant timestamp;
        
        private Duration duration;
        private String source;
    }
    
    /**
     * Dependency health tracking
     */
    @Data
    @Builder
    @Jacksonized
    public static class DependencyHealth {
        private String dependencyName;
        private HealthStatus status;
        private double healthScore;
        private boolean critical; // Is this dependency critical for service operation
        private String impact; // Impact level on main service
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        private Instant lastChecked;
        
        private Map<String, Object> metrics;
    }
    
    /**
     * Health alert
     */
    @Data
    @Builder
    @Jacksonized
    public static class HealthAlert {
        private String alertId;
        private String alertType;
        private String severity;
        private String message;
        private Map<String, Object> context;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        private Instant createdAt;
        
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        private Instant acknowledgedAt;
        
        private boolean acknowledged;
        private String acknowledgedBy;
    }
    
    /**
     * Constructor for new service health
     */
    public ServiceHealth(String serviceName) {
        this.serviceName = serviceName;
        this.status = HealthStatus.UNKNOWN;
        this.statusMessage = "Initial state - no checks performed";
        this.healthScore = 0.0;
        this.lastChecked = Instant.now();
        this.checkInterval = Duration.ofMinutes(1);
        this.checkTimeout = Duration.ofSeconds(5);
        this.failureThreshold = 3;
        this.recoveryThreshold = 2;
        this.circuitBreakerState = CircuitBreakerState.CLOSED;
        this.circuitBreakerStateChanged = Instant.now();
        this.trend = HealthTrend.STABLE;
        this.recentEvents = new java.util.ArrayList<>();
        this.activeAlerts = new java.util.ArrayList<>();
        this.riskFactors = new java.util.ArrayList<>();
    }
    
    /**
     * Record a successful health check
     */
    public synchronized void recordSuccess(long responseTimeMs) {
        totalChecks.incrementAndGet();
        totalSuccesses.incrementAndGet();
        consecutiveSuccesses.incrementAndGet();
        consecutiveFailures.set(0);
        
        // Update response time metrics
        totalResponseTime.addAndGet(responseTimeMs);
        updateResponseTimeMetrics(responseTimeMs);
        
        HealthStatus previousStatus = this.status;
        
        // Determine new status based on consecutive successes
        if (consecutiveSuccesses.get() >= recoveryThreshold) {
            if (this.status != HealthStatus.HEALTHY) {
                updateStatus(HealthStatus.HEALTHY, "Service recovered - consecutive successful checks");
                recordHealthEvent("RECOVERY", previousStatus, this.status, "Service health recovered");
            }
        }
        
        this.lastChecked = Instant.now();
        this.lastHealthy = Instant.now();
        
        // Update circuit breaker state
        updateCircuitBreakerState();
        
        // Update health score
        updateHealthScore();
        
        // Analyze trends
        analyzeTrends();
    }
    
    /**
     * Record a failed health check
     */
    public synchronized void recordFailure(String errorMessage, long responseTimeMs) {
        totalChecks.incrementAndGet();
        totalFailures.incrementAndGet();
        consecutiveFailures.incrementAndGet();
        consecutiveSuccesses.set(0);
        
        if (responseTimeMs > 0) {
            totalResponseTime.addAndGet(responseTimeMs);
            updateResponseTimeMetrics(responseTimeMs);
        }
        
        HealthStatus previousStatus = this.status;
        
        // Determine new status based on consecutive failures
        if (consecutiveFailures.get() >= failureThreshold) {
            if (this.status == HealthStatus.HEALTHY) {
                updateStatus(HealthStatus.DEGRADED, "Service experiencing issues - " + errorMessage);
            } else if (this.status == HealthStatus.DEGRADED && consecutiveFailures.get() >= failureThreshold * 2) {
                updateStatus(HealthStatus.UNHEALTHY, "Service unhealthy - " + errorMessage);
            } else if (consecutiveFailures.get() >= failureThreshold * 3) {
                updateStatus(HealthStatus.CRITICAL, "Service critical - " + errorMessage);
            }
        }
        
        this.lastChecked = Instant.now();
        this.lastUnhealthy = Instant.now();
        
        // Track error types
        recordError(errorMessage);
        
        // Update circuit breaker state
        updateCircuitBreakerState();
        
        // Update health score
        updateHealthScore();
        
        // Check for alerts
        checkForAlerts();
        
        // Analyze trends
        analyzeTrends();
        
        if (previousStatus != this.status) {
            recordHealthEvent("DEGRADATION", previousStatus, this.status, errorMessage);
        }
    }
    
    /**
     * Update response time metrics
     */
    private void updateResponseTimeMetrics(long responseTimeMs) {
        if (responseTimeMs < minResponseTime) {
            minResponseTime = responseTimeMs;
        }
        if (responseTimeMs > maxResponseTime) {
            maxResponseTime = responseTimeMs;
        }
        
        // Update percentiles (simplified calculation - in production would use a proper histogram)
        long totalChecksCount = totalChecks.get();
        if (totalChecksCount > 0) {
            double averageResponseTime = (double) totalResponseTime.get() / totalChecksCount;
            
            // Simplified percentile calculation
            responseTimeP50 = averageResponseTime * 0.8;
            responseTimeP95 = averageResponseTime * 1.5;
            responseTimeP99 = averageResponseTime * 2.0;
        }
    }
    
    /**
     * Update overall health score (0-100)
     */
    private void updateHealthScore() {
        double score = 0.0;
        
        // Base score on success rate (40% weight)
        long totalChecksCount = totalChecks.get();
        if (totalChecksCount > 0) {
            double successRate = (double) totalSuccesses.get() / totalChecksCount;
            score += successRate * 40.0;
        }
        
        // Response time score (30% weight)
        if (totalChecksCount > 0) {
            double avgResponseTime = (double) totalResponseTime.get() / totalChecksCount;
            double responseScore = Math.max(0, 30.0 - (avgResponseTime / 100.0)); // Penalty for slow responses
            score += Math.max(0, responseScore);
        }
        
        // Recent performance (20% weight)
        int recentWindow = Math.min(10, (int) totalChecksCount);
        if (recentWindow > 0) {
            double recentSuccessRate = Math.max(0, (double) (recentWindow - consecutiveFailures.get()) / recentWindow);
            score += recentSuccessRate * 20.0;
        }
        
        // Dependencies health (10% weight)
        if (!dependenciesHealth.isEmpty()) {
            double avgDependencyScore = dependenciesHealth.values().stream()
                .mapToDouble(DependencyHealth::getHealthScore)
                .average()
                .orElse(100.0);
            score += (avgDependencyScore / 100.0) * 10.0;
        } else {
            score += 10.0; // Full points if no dependencies
        }
        
        this.healthScore = Math.max(0.0, Math.min(100.0, score));
    }
    
    /**
     * Update circuit breaker state based on health
     */
    private void updateCircuitBreakerState() {
        CircuitBreakerState previousState = this.circuitBreakerState;
        
        switch (this.circuitBreakerState) {
            case CLOSED:
                if (consecutiveFailures.get() >= failureThreshold) {
                    this.circuitBreakerState = CircuitBreakerState.OPEN;
                    this.circuitBreakerStateChanged = Instant.now();
                    this.circuitBreakerFailCount = consecutiveFailures.get();
                }
                break;
                
            case OPEN:
                // Stay open for a cooling period, then try half-open
                if (Duration.between(circuitBreakerStateChanged, Instant.now()).toMinutes() >= 5) {
                    this.circuitBreakerState = CircuitBreakerState.HALF_OPEN;
                    this.circuitBreakerStateChanged = Instant.now();
                }
                break;
                
            case HALF_OPEN:
                if (consecutiveSuccesses.get() >= recoveryThreshold) {
                    this.circuitBreakerState = CircuitBreakerState.CLOSED;
                    this.circuitBreakerStateChanged = Instant.now();
                    this.circuitBreakerFailCount = 0;
                } else if (consecutiveFailures.get() > 0) {
                    this.circuitBreakerState = CircuitBreakerState.OPEN;
                    this.circuitBreakerStateChanged = Instant.now();
                }
                break;
        }
        
        if (previousState != this.circuitBreakerState) {
            recordHealthEvent("CIRCUIT_BREAKER_STATE_CHANGE", null, null, 
                "Circuit breaker state changed from " + previousState + " to " + this.circuitBreakerState);
        }
    }
    
    /**
     * Record error occurrence
     */
    private void recordError(String errorMessage) {
        String errorType = extractErrorType(errorMessage);
        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        lastErrorOccurrence.put(errorType, Instant.now());
    }
    
    /**
     * Extract error type from error message
     */
    private String extractErrorType(String errorMessage) {
        if (errorMessage == null) return "UNKNOWN";
        
        String upperMessage = errorMessage.toUpperCase();
        if (upperMessage.contains("TIMEOUT")) return "TIMEOUT";
        if (upperMessage.contains("CONNECTION")) return "CONNECTION_ERROR";
        if (upperMessage.contains("404")) return "NOT_FOUND";
        if (upperMessage.contains("500")) return "INTERNAL_SERVER_ERROR";
        if (upperMessage.contains("503")) return "SERVICE_UNAVAILABLE";
        if (upperMessage.contains("DNS")) return "DNS_ERROR";
        if (upperMessage.contains("SSL") || upperMessage.contains("TLS")) return "SSL_ERROR";
        
        return "GENERAL_ERROR";
    }
    
    /**
     * Update service status
     */
    private void updateStatus(HealthStatus newStatus, String message) {
        this.status = newStatus;
        this.statusMessage = message;
    }
    
    /**
     * Record health event
     */
    private void recordHealthEvent(String eventType, HealthStatus previousStatus, HealthStatus newStatus, String description) {
        HealthEvent event = HealthEvent.builder()
            .eventType(eventType)
            .previousStatus(previousStatus)
            .newStatus(newStatus)
            .description(description)
            .timestamp(Instant.now())
            .source("HealthMonitor")
            .build();
            
        recentEvents.add(event);
        
        // Keep only last 100 events
        if (recentEvents.size() > 100) {
            recentEvents.remove(0);
        }
    }
    
    /**
     * Check for alert conditions
     */
    private void checkForAlerts() {
        // High failure rate alert
        if (getFailureRate() > 0.5 && !isAlertActive("HIGH_FAILURE_RATE")) {
            createAlert("HIGH_FAILURE_RATE", "HIGH", 
                String.format("Service %s has failure rate of %.1f%%", serviceName, getFailureRate() * 100));
        }
        
        // Consecutive failures alert
        if (consecutiveFailures.get() >= failureThreshold && !isAlertActive("CONSECUTIVE_FAILURES")) {
            createAlert("CONSECUTIVE_FAILURES", "MEDIUM", 
                String.format("Service %s has %d consecutive failures", serviceName, consecutiveFailures.get()));
        }
        
        // Response time alert
        double avgResponseTime = getAverageResponseTime();
        if (avgResponseTime > 5000 && !isAlertActive("SLOW_RESPONSE")) {
            createAlert("SLOW_RESPONSE", "MEDIUM", 
                String.format("Service %s average response time is %.0fms", serviceName, avgResponseTime));
        }
    }
    
    /**
     * Create a new alert
     */
    private void createAlert(String alertType, String severity, String message) {
        // Check cooldown
        Instant lastAlert = alertCooldowns.get(alertType);
        if (lastAlert != null && Duration.between(lastAlert, Instant.now()).toMinutes() < 30) {
            return; // Still in cooldown
        }
        
        HealthAlert alert = HealthAlert.builder()
            .alertId(java.util.UUID.randomUUID().toString())
            .alertType(alertType)
            .severity(severity)
            .message(message)
            .createdAt(Instant.now())
            .acknowledged(false)
            .build();
            
        activeAlerts.add(alert);
        alertCooldowns.put(alertType, Instant.now());
    }
    
    /**
     * Check if alert is already active
     */
    private boolean isAlertActive(String alertType) {
        return activeAlerts.stream()
            .anyMatch(alert -> alert.getAlertType().equals(alertType) && !alert.isAcknowledged());
    }
    
    /**
     * Analyze health trends
     */
    private void analyzeTrends() {
        // Simplified trend analysis - in production would use more sophisticated algorithms
        if (recentEvents.size() < 5) {
            this.trend = HealthTrend.STABLE;
            return;
        }
        
        // Look at recent events to determine trend
        long improvingEvents = recentEvents.stream()
            .filter(event -> "RECOVERY".equals(event.getEventType()))
            .count();
            
        long degradingEvents = recentEvents.stream()
            .filter(event -> "DEGRADATION".equals(event.getEventType()))
            .count();
            
        if (improvingEvents > degradingEvents) {
            this.trend = HealthTrend.IMPROVING;
        } else if (degradingEvents > improvingEvents) {
            this.trend = HealthTrend.DEGRADING;
        } else {
            this.trend = HealthTrend.STABLE;
        }
        
        // Calculate degradation risk
        double failureRate = getFailureRate();
        this.degradationRisk = Math.min(1.0, failureRate + (consecutiveFailures.get() * 0.1));
    }
    
    /**
     * Get current failure rate
     */
    public double getFailureRate() {
        long totalChecksCount = totalChecks.get();
        return totalChecksCount > 0 ? (double) totalFailures.get() / totalChecksCount : 0.0;
    }
    
    /**
     * Get current success rate
     */
    public double getSuccessRate() {
        return 1.0 - getFailureRate();
    }
    
    /**
     * Get average response time
     */
    public double getAverageResponseTime() {
        long totalChecksCount = totalChecks.get();
        return totalChecksCount > 0 ? (double) totalResponseTime.get() / totalChecksCount : 0.0;
    }
    
    /**
     * Check if service is currently operational
     */
    public boolean isOperational() {
        return status.isOperational() && circuitBreakerState != CircuitBreakerState.OPEN;
    }
    
    /**
     * Get uptime percentage for last 24 hours
     */
    public double getUptime24h() {
        return uptimePercentage24h;
    }
    
    /**
     * Add dependency health tracking
     */
    public void addDependency(String dependencyName, boolean critical) {
        DependencyHealth depHealth = DependencyHealth.builder()
            .dependencyName(dependencyName)
            .status(HealthStatus.UNKNOWN)
            .healthScore(100.0)
            .critical(critical)
            .impact(critical ? "HIGH" : "LOW")
            .lastChecked(Instant.now())
            .metrics(new ConcurrentHashMap<>())
            .build();
            
        dependenciesHealth.put(dependencyName, depHealth);
    }
    
    /**
     * Update dependency health
     */
    public void updateDependencyHealth(String dependencyName, HealthStatus status, double healthScore) {
        DependencyHealth depHealth = dependenciesHealth.get(dependencyName);
        if (depHealth != null) {
            depHealth.setStatus(status);
            depHealth.setHealthScore(healthScore);
            depHealth.setLastChecked(Instant.now());
            
            // Recalculate overall health score
            updateHealthScore();
        }
    }
    
    /**
     * Get health summary
     */
    public String getHealthSummary() {
        return String.format(
            "Service: %s | Status: %s | Score: %.1f | Success Rate: %.1f%% | Avg Response: %.0fms | Circuit Breaker: %s",
            serviceName, status, healthScore, getSuccessRate() * 100, getAverageResponseTime(), circuitBreakerState
        );
    }

    /**
     * Check if service is healthy
     */
    public boolean isHealthy() {
        return status == HealthStatus.HEALTHY || status == HealthStatus.DEGRADED;
    }

    /**
     * Get last check time (convert from Instant to LocalDateTime)
     */
    public java.time.LocalDateTime getLastCheck() {
        if (lastChecked == null) {
            return java.time.LocalDateTime.now();
        }
        return java.time.LocalDateTime.ofInstant(lastChecked, java.time.ZoneId.systemDefault());
    }

    /**
     * Get error rate
     */
    public double getErrorRate() {
        return getFailureRate();
    }

    /**
     * Record failure with HTTP status code
     */
    public void recordFailure(int statusCode, String errorMessage) {
        recordFailure(errorMessage + " (HTTP " + statusCode + ")", 0);
    }

    /**
     * Record timeout
     */
    public void recordTimeout() {
        recordFailure("Request timeout", 0);
    }

    /**
     * Record connection failure
     */
    public void recordConnectionFailure() {
        recordFailure("Connection failed", 0);
    }
}