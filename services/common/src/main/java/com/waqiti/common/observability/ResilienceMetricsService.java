package com.waqiti.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Enterprise-grade resilience metrics service providing comprehensive monitoring
 * and analytics for circuit breakers, bulkheads, rate limiters, and other
 * resilience patterns within the Waqiti platform.
 */
@Slf4j
@Service
public class ResilienceMetricsService implements HealthIndicator {
    
    private final MeterRegistry meterRegistry;
    private final Map<String, CircuitBreakerMetrics> circuitBreakerMetrics;
    private final Map<String, BulkheadMetrics> bulkheadMetrics;
    private final Map<String, RateLimiterMetrics> rateLimiterMetrics;
    private final Map<String, RetryMetrics> retryMetrics;
    private final Map<String, TimeoutMetrics> timeoutMetrics;
    private final Map<String, CacheMetrics> cacheMetrics;
    private final ScheduledExecutorService metricsCollector;
    private final AtomicReference<ResilienceHealthStatus> healthStatus;
    private final ResilienceConfigurationRepository configRepository;
    
    // Performance tracking
    private final Timer metricsCollectionTimer;
    private final Counter healthCheckCounter;
    private final Gauge overallResilienceScore;
    private final DistributionSummary responseTimeDistribution;
    
    // Alerting thresholds
    private static final double CRITICAL_FAILURE_RATE = 0.5;
    private static final double WARNING_FAILURE_RATE = 0.25;
    private static final long CRITICAL_RESPONSE_TIME_MS = 5000;
    private static final long WARNING_RESPONSE_TIME_MS = 2000;
    
    public ResilienceMetricsService(MeterRegistry meterRegistry, 
                                  ResilienceConfigurationRepository configRepository) {
        this.meterRegistry = meterRegistry;
        this.configRepository = configRepository;
        this.circuitBreakerMetrics = new ConcurrentHashMap<>();
        this.bulkheadMetrics = new ConcurrentHashMap<>();
        this.rateLimiterMetrics = new ConcurrentHashMap<>();
        this.retryMetrics = new ConcurrentHashMap<>();
        this.timeoutMetrics = new ConcurrentHashMap<>();
        this.cacheMetrics = new ConcurrentHashMap<>();
        this.healthStatus = new AtomicReference<>(ResilienceHealthStatus.HEALTHY);
        this.metricsCollector = Executors.newScheduledThreadPool(2, 
            r -> new Thread(r, "resilience-metrics-collector"));
        
        // Initialize performance metrics
        this.metricsCollectionTimer = io.micrometer.core.instrument.Timer.builder("resilience.metrics.collection.time")
            .description("Time taken to collect resilience metrics")
            .register(meterRegistry);
            
        this.healthCheckCounter = Counter.builder("resilience.health.checks")
            .description("Number of resilience health checks performed")
            .register(meterRegistry);
            
        this.overallResilienceScore = Gauge.builder("resilience.overall.score", this, ResilienceMetricsService::calculateOverallResilienceScore)
            .description("Overall resilience health score (0-100)")
            .register(meterRegistry);
            
        this.responseTimeDistribution = DistributionSummary.builder("resilience.response.time")
            .description("Distribution of resilience pattern response times")
            .publishPercentiles(0.5, 0.75, 0.90, 0.95, 0.99)
            .register(meterRegistry);
    }
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing ResilienceMetricsService with comprehensive monitoring");
        
        // Schedule periodic metrics collection
        metricsCollector.scheduleAtFixedRate(this::collectAllMetrics, 0, 30, TimeUnit.SECONDS);
        metricsCollector.scheduleAtFixedRate(this::performHealthAssessment, 10, 60, TimeUnit.SECONDS);
        metricsCollector.scheduleAtFixedRate(this::cleanupStaleMetrics, 5, 300, TimeUnit.MINUTES);
        
        log.info("ResilienceMetricsService initialized successfully");
    }
    
    // Circuit Breaker Metrics
    
    public void recordCircuitBreakerCall(String name, boolean success, Duration executionTime) {
        CircuitBreakerMetrics metrics = circuitBreakerMetrics.computeIfAbsent(name, 
            k -> new CircuitBreakerMetrics(name, meterRegistry));
        
        metrics.recordCall(success, executionTime);
        responseTimeDistribution.record(executionTime.toMillis());
        
        if (!success) {
            log.debug("Circuit breaker '{}' recorded failure with execution time: {}", name, executionTime);
        }
    }
    
    public void recordCircuitBreakerStateChange(String name, CircuitBreakerState from, CircuitBreakerState to) {
        CircuitBreakerMetrics metrics = circuitBreakerMetrics.computeIfAbsent(name,
            k -> new CircuitBreakerMetrics(name, meterRegistry));
            
        metrics.recordStateChange(from, to);
        
        if (to == CircuitBreakerState.OPEN) {
            log.warn("Circuit breaker '{}' transitioned to OPEN state from {}", name, from);
        } else if (to == CircuitBreakerState.CLOSED && from == CircuitBreakerState.OPEN) {
            log.info("Circuit breaker '{}' recovered and transitioned to CLOSED state", name);
        }
    }
    
    public CircuitBreakerMetrics getCircuitBreakerMetrics(String name) {
        return circuitBreakerMetrics.get(name);
    }
    
    // Bulkhead Metrics
    
    public void recordBulkheadExecution(String name, boolean success, Duration executionTime, int availablePermits) {
        BulkheadMetrics metrics = bulkheadMetrics.computeIfAbsent(name,
            k -> new BulkheadMetrics(name, meterRegistry));
            
        metrics.recordExecution(success, executionTime, availablePermits);
        responseTimeDistribution.record(executionTime.toMillis());
    }
    
    public void recordBulkheadRejection(String name, String reason) {
        BulkheadMetrics metrics = bulkheadMetrics.computeIfAbsent(name,
            k -> new BulkheadMetrics(name, meterRegistry));
            
        metrics.recordRejection(reason);
        log.debug("Bulkhead '{}' rejected execution due to: {}", name, reason);
    }
    
    // Rate Limiter Metrics
    
    public void recordRateLimiterCall(String name, boolean permitted, Duration waitTime) {
        RateLimiterMetrics metrics = rateLimiterMetrics.computeIfAbsent(name,
            k -> new RateLimiterMetrics(name, meterRegistry));
            
        metrics.recordCall(permitted, waitTime);
        
        if (!permitted) {
            log.debug("Rate limiter '{}' rejected request", name);
        }
    }
    
    // Retry Metrics
    
    public void recordRetryAttempt(String name, int attemptNumber, boolean success, Duration executionTime, Throwable exception) {
        RetryMetrics metrics = retryMetrics.computeIfAbsent(name,
            k -> new RetryMetrics(name, meterRegistry));
            
        metrics.recordAttempt(attemptNumber, success, executionTime, exception);
        responseTimeDistribution.record(executionTime.toMillis());
    }
    
    // Timeout Metrics
    
    public void recordTimeoutResult(String name, boolean timedOut, Duration executionTime) {
        TimeoutMetrics metrics = timeoutMetrics.computeIfAbsent(name,
            k -> new TimeoutMetrics(name, meterRegistry));
            
        metrics.recordResult(timedOut, executionTime);
        
        if (timedOut) {
            log.debug("Timeout '{}' occurred after {}", name, executionTime);
        }
    }
    
    // Cache Metrics
    
    public void recordCacheOperation(String name, CacheOperationType operation, boolean hit, Duration executionTime) {
        CacheMetrics metrics = cacheMetrics.computeIfAbsent(name,
            k -> new CacheMetrics(name, meterRegistry));
            
        metrics.recordOperation(operation, hit, executionTime);
    }
    
    // Health Assessment
    
    @Override
    public Health health() {
        healthCheckCounter.increment();
        
        return metricsCollectionTimer.recordCallable(() -> {
            ResilienceHealthStatus status = performHealthAssessment();
            double overallScore = calculateOverallResilienceScore();
            
            Health.Builder builder = new Health.Builder();
            
            switch (status) {
                case HEALTHY -> builder.up();
                case DEGRADED -> builder.status("DEGRADED");
                case CRITICAL -> builder.down();
                default -> builder.unknown();
            }
            
            return builder
                .withDetail("overallScore", overallScore)
                .withDetail("status", status.name())
                .withDetail("circuitBreakers", getCircuitBreakerSummary())
                .withDetail("bulkheads", getBulkheadSummary())
                .withDetail("rateLimiters", getRateLimiterSummary())
                .withDetail("retries", getRetrySummary())
                .withDetail("timeouts", getTimeoutSummary())
                .withDetail("lastAssessment", LocalDateTime.now())
                .build();
        });
    }
    
    private ResilienceHealthStatus performHealthAssessment() {
        try {
            double overallScore = calculateOverallResilienceScore();
            ResilienceHealthStatus newStatus;
            
            if (overallScore >= 80.0) {
                newStatus = ResilienceHealthStatus.HEALTHY;
            } else if (overallScore >= 60.0) {
                newStatus = ResilienceHealthStatus.DEGRADED;
            } else {
                newStatus = ResilienceHealthStatus.CRITICAL;
            }
            
            ResilienceHealthStatus previousStatus = healthStatus.getAndSet(newStatus);
            
            if (previousStatus != newStatus) {
                log.info("Resilience health status changed from {} to {} (score: {})", 
                    previousStatus, newStatus, overallScore);
            }
            
            return newStatus;
            
        } catch (Exception e) {
            log.error("Error during resilience health assessment", e);
            healthStatus.set(ResilienceHealthStatus.CRITICAL);
            return ResilienceHealthStatus.CRITICAL;
        }
    }
    
    private double calculateOverallResilienceScore() {
        if (isEmpty()) {
            return 100.0; // Perfect score if no patterns are being used
        }
        
        double totalScore = 0.0;
        int componentCount = 0;
        
        // Circuit Breaker scores (weight: 25%)
        for (CircuitBreakerMetrics metrics : circuitBreakerMetrics.values()) {
            totalScore += metrics.calculateHealthScore() * 0.25;
            componentCount++;
        }
        
        // Bulkhead scores (weight: 20%)
        for (BulkheadMetrics metrics : bulkheadMetrics.values()) {
            totalScore += metrics.calculateHealthScore() * 0.20;
            componentCount++;
        }
        
        // Rate Limiter scores (weight: 15%)
        for (RateLimiterMetrics metrics : rateLimiterMetrics.values()) {
            totalScore += metrics.calculateHealthScore() * 0.15;
            componentCount++;
        }
        
        // Retry scores (weight: 20%)
        for (RetryMetrics metrics : retryMetrics.values()) {
            totalScore += metrics.calculateHealthScore() * 0.20;
            componentCount++;
        }
        
        // Timeout scores (weight: 10%)
        for (TimeoutMetrics metrics : timeoutMetrics.values()) {
            totalScore += metrics.calculateHealthScore() * 0.10;
            componentCount++;
        }
        
        // Cache scores (weight: 10%)
        for (CacheMetrics metrics : cacheMetrics.values()) {
            totalScore += metrics.calculateHealthScore() * 0.10;
            componentCount++;
        }
        
        return componentCount > 0 ? totalScore / componentCount : 100.0;
    }
    
    private boolean isEmpty() {
        return circuitBreakerMetrics.isEmpty() && 
               bulkheadMetrics.isEmpty() && 
               rateLimiterMetrics.isEmpty() && 
               retryMetrics.isEmpty() && 
               timeoutMetrics.isEmpty() && 
               cacheMetrics.isEmpty();
    }
    
    // Summary Methods
    
    private Map<String, Object> getCircuitBreakerSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", circuitBreakerMetrics.size());
        
        long openCount = circuitBreakerMetrics.values().stream()
            .mapToLong(m -> m.getCurrentState() == CircuitBreakerState.OPEN ? 1 : 0)
            .sum();
        summary.put("open", openCount);
        summary.put("closed", circuitBreakerMetrics.size() - openCount);
        
        OptionalDouble avgFailureRate = circuitBreakerMetrics.values().stream()
            .mapToDouble(CircuitBreakerMetrics::getFailureRate)
            .average();
        summary.put("avgFailureRate", avgFailureRate.orElse(0.0));
        
        return summary;
    }
    
    private Map<String, Object> getBulkheadSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", bulkheadMetrics.size());
        
        OptionalDouble avgUtilization = bulkheadMetrics.values().stream()
            .mapToDouble(BulkheadMetrics::getUtilizationPercentage)
            .average();
        summary.put("avgUtilization", avgUtilization.orElse(0.0));
        
        long totalRejections = bulkheadMetrics.values().stream()
            .mapToLong(BulkheadMetrics::getTotalRejections)
            .sum();
        summary.put("totalRejections", totalRejections);
        
        return summary;
    }
    
    private Map<String, Object> getRateLimiterSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", rateLimiterMetrics.size());
        
        OptionalDouble avgPermitRate = rateLimiterMetrics.values().stream()
            .mapToDouble(RateLimiterMetrics::getPermitRate)
            .average();
        summary.put("avgPermitRate", avgPermitRate.orElse(1.0));
        
        long totalThrottled = rateLimiterMetrics.values().stream()
            .mapToLong(RateLimiterMetrics::getTotalThrottled)
            .sum();
        summary.put("totalThrottled", totalThrottled);
        
        return summary;
    }
    
    private Map<String, Object> getRetrySummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", retryMetrics.size());
        
        OptionalDouble avgRetries = retryMetrics.values().stream()
            .mapToDouble(RetryMetrics::getAverageRetries)
            .average();
        summary.put("avgRetries", avgRetries.orElse(0.0));
        
        long totalSuccessfulRetries = retryMetrics.values().stream()
            .mapToLong(RetryMetrics::getSuccessfulRetries)
            .sum();
        summary.put("totalSuccessfulRetries", totalSuccessfulRetries);
        
        return summary;
    }
    
    private Map<String, Object> getTimeoutSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", timeoutMetrics.size());
        
        OptionalDouble avgTimeoutRate = timeoutMetrics.values().stream()
            .mapToDouble(TimeoutMetrics::getTimeoutRate)
            .average();
        summary.put("avgTimeoutRate", avgTimeoutRate.orElse(0.0));
        
        long totalTimeouts = timeoutMetrics.values().stream()
            .mapToLong(TimeoutMetrics::getTotalTimeouts)
            .sum();
        summary.put("totalTimeouts", totalTimeouts);
        
        return summary;
    }
    
    // Metrics Collection
    
    private void collectAllMetrics() {
        try {
            log.debug("Collecting resilience metrics for {} patterns", getTotalPatternCount());
            
            // Update all metrics
            circuitBreakerMetrics.values().forEach(CircuitBreakerMetrics::updateMetrics);
            bulkheadMetrics.values().forEach(BulkheadMetrics::updateMetrics);
            rateLimiterMetrics.values().forEach(RateLimiterMetrics::updateMetrics);
            retryMetrics.values().forEach(RetryMetrics::updateMetrics);
            timeoutMetrics.values().forEach(TimeoutMetrics::updateMetrics);
            cacheMetrics.values().forEach(CacheMetrics::updateMetrics);
            
        } catch (Exception e) {
            log.error("Error collecting resilience metrics", e);
        }
    }
    
    private void cleanupStaleMetrics() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        
        circuitBreakerMetrics.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().isBefore(cutoff));
        bulkheadMetrics.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().isBefore(cutoff));
        rateLimiterMetrics.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().isBefore(cutoff));
        retryMetrics.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().isBefore(cutoff));
        timeoutMetrics.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().isBefore(cutoff));
        cacheMetrics.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().isBefore(cutoff));
    }
    
    // Public API Methods
    
    public ResilienceMetricsSummary getAllMetrics() {
        return ResilienceMetricsSummary.builder()
            .overallHealthStatus(healthStatus.get())
            .overallScore(calculateOverallResilienceScore())
            .circuitBreakers(new HashMap<>(circuitBreakerMetrics))
            .bulkheads(new HashMap<>(bulkheadMetrics))
            .rateLimiters(new HashMap<>(rateLimiterMetrics))
            .retries(new HashMap<>(retryMetrics))
            .timeouts(new HashMap<>(timeoutMetrics))
            .caches(new HashMap<>(cacheMetrics))
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    public List<ResilienceAlert> getActiveAlerts() {
        List<ResilienceAlert> alerts = new ArrayList<>();
        
        // Circuit breaker alerts
        circuitBreakerMetrics.values().forEach(metrics -> {
            if (metrics.getFailureRate() > CRITICAL_FAILURE_RATE) {
                alerts.add(ResilienceAlert.critical(metrics.getName(), 
                    "Circuit breaker failure rate exceeds critical threshold"));
            } else if (metrics.getFailureRate() > WARNING_FAILURE_RATE) {
                alerts.add(ResilienceAlert.warning(metrics.getName(),
                    "Circuit breaker failure rate exceeds warning threshold"));
            }
        });
        
        // Add other pattern alerts...
        
        return alerts;
    }
    
    public int getTotalPatternCount() {
        return circuitBreakerMetrics.size() + 
               bulkheadMetrics.size() + 
               rateLimiterMetrics.size() + 
               retryMetrics.size() + 
               timeoutMetrics.size() + 
               cacheMetrics.size();
    }
    
    public ResilienceHealthStatus getCurrentHealthStatus() {
        return healthStatus.get();
    }
    
    // Shutdown
    
    public void shutdown() {
        log.info("Shutting down ResilienceMetricsService");
        metricsCollector.shutdown();
        try {
            if (!metricsCollector.awaitTermination(10, TimeUnit.SECONDS)) {
                metricsCollector.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metricsCollector.shutdownNow();
        }
    }
}

// Supporting Enums and Classes

enum ResilienceHealthStatus {
    HEALTHY, DEGRADED, CRITICAL
}

enum CircuitBreakerState {
    CLOSED, OPEN, HALF_OPEN
}

enum CacheOperationType {
    GET, PUT, REMOVE, CLEAR, EVICT
}

// Abstract base class for pattern metrics
abstract class PatternMetrics {
    protected final String name;
    protected final MeterRegistry meterRegistry;
    protected final AtomicLong totalCalls;
    protected final AtomicLong successfulCalls;
    protected final AtomicLong failedCalls;
    protected volatile LocalDateTime lastActivity;
    
    protected PatternMetrics(String name, MeterRegistry meterRegistry) {
        this.name = name;
        this.meterRegistry = meterRegistry;
        this.totalCalls = new AtomicLong();
        this.successfulCalls = new AtomicLong();
        this.failedCalls = new AtomicLong();
        this.lastActivity = LocalDateTime.now();
    }
    
    public String getName() { return name; }
    public long getTotalCalls() { return totalCalls.get(); }
    public long getSuccessfulCalls() { return successfulCalls.get(); }
    public long getFailedCalls() { return failedCalls.get(); }
    public LocalDateTime getLastActivity() { return lastActivity; }
    
    public double getSuccessRate() {
        long total = totalCalls.get();
        return total > 0 ? (double) successfulCalls.get() / total : 1.0;
    }
    
    public double getFailureRate() {
        return 1.0 - getSuccessRate();
    }
    
    public abstract double calculateHealthScore();
    public abstract void updateMetrics();
}

// Circuit Breaker Metrics Implementation
class CircuitBreakerMetrics extends PatternMetrics {
    private final AtomicReference<CircuitBreakerState> currentState;
    private final AtomicLong stateChanges;
    private final Timer executionTimer;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Gauge failureRateGauge;

    public CircuitBreakerMetrics(String name, MeterRegistry meterRegistry) {
        super(name, meterRegistry);
        this.currentState = new AtomicReference<>(CircuitBreakerState.CLOSED);
        this.stateChanges = new AtomicLong();

        this.executionTimer = io.micrometer.core.instrument.Timer.builder("circuit.breaker.execution.time")
            .tag("name", name)
            .register(meterRegistry);

        this.successCounter = Counter.builder("circuit.breaker.calls")
            .tag("name", name)
            .tag("result", "success")
            .register(meterRegistry);

        this.failureCounter = Counter.builder("circuit.breaker.calls")
            .tag("name", name)
            .tag("result", "failure")
            .register(meterRegistry);

        this.failureRateGauge = Gauge.builder("circuit.breaker.failure.rate", this, CircuitBreakerMetrics::getFailureRate)
            .tag("name", name)
            .register(meterRegistry);
    }
    
    public void recordCall(boolean success, Duration executionTime) {
        totalCalls.incrementAndGet();
        if (success) {
            successfulCalls.incrementAndGet();
            successCounter.increment();
        } else {
            failedCalls.incrementAndGet();
            failureCounter.increment();
        }

        executionTimer.record(executionTime);
        lastActivity = LocalDateTime.now();
    }
    
    public void recordStateChange(CircuitBreakerState from, CircuitBreakerState to) {
        currentState.set(to);
        stateChanges.incrementAndGet();
        lastActivity = LocalDateTime.now();
    }
    
    public CircuitBreakerState getCurrentState() {
        return currentState.get();
    }
    
    public long getStateChanges() {
        return stateChanges.get();
    }
    
    @Override
    public double calculateHealthScore() {
        double failureRate = getFailureRate();
        CircuitBreakerState state = currentState.get();
        
        if (state == CircuitBreakerState.OPEN) {
            return 0.0; // Critical when circuit is open
        } else if (failureRate > 0.5) {
            return 25.0; // Poor health with high failure rate
        } else if (failureRate > 0.25) {
            return 50.0; // Degraded health
        } else if (failureRate > 0.1) {
            return 75.0; // Good health
        } else {
            return 100.0; // Excellent health
        }
    }
    
    @Override
    public void updateMetrics() {
        // Metrics are updated in real-time via recordCall and recordStateChange
    }
}

// Other metrics implementations follow similar patterns...
class BulkheadMetrics extends PatternMetrics {
    private final AtomicLong rejections;
    private final AtomicLong availablePermits;
    private final AtomicLong maxPermits;
    
    public BulkheadMetrics(String name, MeterRegistry meterRegistry) {
        super(name, meterRegistry);
        this.rejections = new AtomicLong();
        this.availablePermits = new AtomicLong(100);
        this.maxPermits = new AtomicLong(100);
    }
    
    public void recordExecution(boolean success, Duration executionTime, int permits) {
        totalCalls.incrementAndGet();
        if (success) successfulCalls.incrementAndGet();
        else failedCalls.incrementAndGet();
        
        availablePermits.set(permits);
        lastActivity = LocalDateTime.now();
    }
    
    public void recordRejection(String reason) {
        rejections.incrementAndGet();
        lastActivity = LocalDateTime.now();
    }
    
    public long getTotalRejections() { return rejections.get(); }
    public double getUtilizationPercentage() { 
        return ((double)(maxPermits.get() - availablePermits.get()) / maxPermits.get()) * 100.0; 
    }
    
    @Override
    public double calculateHealthScore() {
        double rejectionRate = getTotalCalls() > 0 ? (double) rejections.get() / getTotalCalls() : 0.0;
        double utilization = getUtilizationPercentage();
        
        if (rejectionRate > 0.5) return 0.0;
        if (rejectionRate > 0.25) return 25.0;
        if (utilization > 90.0) return 50.0;
        if (utilization > 75.0) return 75.0;
        return 100.0;
    }
    
    @Override
    public void updateMetrics() {}
}

class RateLimiterMetrics extends PatternMetrics {
    private final AtomicLong throttled;
    private final AtomicLong permitted;
    
    public RateLimiterMetrics(String name, MeterRegistry meterRegistry) {
        super(name, meterRegistry);
        this.throttled = new AtomicLong();
        this.permitted = new AtomicLong();
    }
    
    public void recordCall(boolean wasPermitted, Duration waitTime) {
        totalCalls.incrementAndGet();
        if (wasPermitted) {
            permitted.incrementAndGet();
            successfulCalls.incrementAndGet();
        } else {
            throttled.incrementAndGet();
            failedCalls.incrementAndGet();
        }
        lastActivity = LocalDateTime.now();
    }
    
    public long getTotalThrottled() { return throttled.get(); }
    public double getPermitRate() { 
        return getTotalCalls() > 0 ? (double) permitted.get() / getTotalCalls() : 1.0; 
    }
    
    @Override
    public double calculateHealthScore() {
        double permitRate = getPermitRate();
        if (permitRate < 0.5) return 0.0;
        if (permitRate < 0.75) return 50.0;
        if (permitRate < 0.9) return 75.0;
        return 100.0;
    }
    
    @Override
    public void updateMetrics() {}
}

class RetryMetrics extends PatternMetrics {
    private final AtomicLong totalRetries;
    private final AtomicLong successfulRetries;
    
    public RetryMetrics(String name, MeterRegistry meterRegistry) {
        super(name, meterRegistry);
        this.totalRetries = new AtomicLong();
        this.successfulRetries = new AtomicLong();
    }
    
    public void recordAttempt(int attemptNumber, boolean success, Duration executionTime, Throwable exception) {
        if (attemptNumber > 1) {
            totalRetries.incrementAndGet();
            if (success) successfulRetries.incrementAndGet();
        }
        
        totalCalls.incrementAndGet();
        if (success) successfulCalls.incrementAndGet();
        else failedCalls.incrementAndGet();
        
        lastActivity = LocalDateTime.now();
    }
    
    public long getSuccessfulRetries() { return successfulRetries.get(); }
    public double getAverageRetries() { 
        return getTotalCalls() > 0 ? (double) totalRetries.get() / getTotalCalls() : 0.0; 
    }
    
    @Override
    public double calculateHealthScore() {
        double avgRetries = getAverageRetries();
        double successRate = getSuccessRate();
        
        if (successRate < 0.5) return 0.0;
        if (avgRetries > 3.0) return 25.0;
        if (avgRetries > 1.5) return 50.0;
        if (successRate > 0.9) return 100.0;
        return 75.0;
    }
    
    @Override
    public void updateMetrics() {}
}

class TimeoutMetrics extends PatternMetrics {
    private final AtomicLong timeouts;
    
    public TimeoutMetrics(String name, MeterRegistry meterRegistry) {
        super(name, meterRegistry);
        this.timeouts = new AtomicLong();
    }
    
    public void recordResult(boolean timedOut, Duration executionTime) {
        totalCalls.incrementAndGet();
        if (timedOut) {
            timeouts.incrementAndGet();
            failedCalls.incrementAndGet();
        } else {
            successfulCalls.incrementAndGet();
        }
        lastActivity = LocalDateTime.now();
    }
    
    public long getTotalTimeouts() { return timeouts.get(); }
    public double getTimeoutRate() { 
        return getTotalCalls() > 0 ? (double) timeouts.get() / getTotalCalls() : 0.0; 
    }
    
    @Override
    public double calculateHealthScore() {
        double timeoutRate = getTimeoutRate();
        if (timeoutRate > 0.5) return 0.0;
        if (timeoutRate > 0.25) return 25.0;
        if (timeoutRate > 0.1) return 50.0;
        if (timeoutRate > 0.05) return 75.0;
        return 100.0;
    }
    
    @Override
    public void updateMetrics() {}
}

class CacheMetrics extends PatternMetrics {
    private final AtomicLong hits;
    private final AtomicLong misses;
    private final Map<CacheOperationType, AtomicLong> operationCounts;
    
    public CacheMetrics(String name, MeterRegistry meterRegistry) {
        super(name, meterRegistry);
        this.hits = new AtomicLong();
        this.misses = new AtomicLong();
        this.operationCounts = new EnumMap<>(CacheOperationType.class);
        for (CacheOperationType type : CacheOperationType.values()) {
            operationCounts.put(type, new AtomicLong());
        }
    }
    
    public void recordOperation(CacheOperationType operation, boolean hit, Duration executionTime) {
        totalCalls.incrementAndGet();
        operationCounts.get(operation).incrementAndGet();
        
        if (operation == CacheOperationType.GET) {
            if (hit) {
                hits.incrementAndGet();
                successfulCalls.incrementAndGet();
            } else {
                misses.incrementAndGet();
                failedCalls.incrementAndGet();
            }
        } else {
            successfulCalls.incrementAndGet();
        }
        
        lastActivity = LocalDateTime.now();
    }
    
    public double getHitRate() { 
        long total = hits.get() + misses.get();
        return total > 0 ? (double) hits.get() / total : 1.0; 
    }
    
    @Override
    public double calculateHealthScore() {
        double hitRate = getHitRate();
        if (hitRate > 0.9) return 100.0;
        if (hitRate > 0.75) return 75.0;
        if (hitRate > 0.5) return 50.0;
        if (hitRate > 0.25) return 25.0;
        return 0.0;
    }
    
    @Override
    public void updateMetrics() {}
}

// Supporting classes
interface ResilienceConfigurationRepository {
    // Placeholder for configuration repository
}

@lombok.Data
@lombok.Builder
class ResilienceMetricsSummary {
    private ResilienceHealthStatus overallHealthStatus;
    private double overallScore;
    private Map<String, CircuitBreakerMetrics> circuitBreakers;
    private Map<String, BulkheadMetrics> bulkheads;
    private Map<String, RateLimiterMetrics> rateLimiters;
    private Map<String, RetryMetrics> retries;
    private Map<String, TimeoutMetrics> timeouts;
    private Map<String, CacheMetrics> caches;
    private LocalDateTime lastUpdated;
}

@lombok.Data
@lombok.Builder
class ResilienceAlert {
    private String name;
    private String severity;
    private String message;
    private LocalDateTime timestamp;
    
    public static ResilienceAlert critical(String name, String message) {
        return ResilienceAlert.builder()
            .name(name)
            .severity("CRITICAL")
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static ResilienceAlert warning(String name, String message) {
        return ResilienceAlert.builder()
            .name(name)
            .severity("WARNING")
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
}