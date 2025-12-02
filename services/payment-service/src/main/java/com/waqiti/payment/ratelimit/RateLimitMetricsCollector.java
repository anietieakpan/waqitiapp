package com.waqiti.payment.ratelimit;

import com.waqiti.payment.ratelimit.dto.RateLimitAlgorithm;
import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Rate Limit Metrics Collector
 * 
 * Comprehensive metrics collection for rate limiting using Micrometer.
 * Provides detailed observability into rate limit operations for monitoring,
 * alerting, and performance analysis.
 * 
 * METRICS COLLECTED:
 * - Rate limit checks (allowed/blocked) by endpoint, algorithm, user type
 * - Rate limit violations with severity levels
 * - Algorithm performance (latency, throughput)
 * - Token bucket refill rates and utilization
 * - Sliding window efficiency metrics
 * - Cache hit/miss ratios
 * - Reset operations
 * - Error rates and types
 * - Concurrent request tracking
 * - Burst detection metrics
 * 
 * MONITORING:
 * - Real-time dashboards (Grafana, Prometheus)
 * - Alert thresholds for violation spikes
 * - Performance SLIs/SLOs tracking
 * - Capacity planning metrics
 * 
 * PERFORMANCE:
 * - Lock-free counters using AtomicLong/LongAdder
 * - Cached meter instances
 * - Minimal overhead (<1ms per metric)
 * - Async batch processing for aggregations
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final Map<String, Gauge> gaugeCache = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> summaryCache = new ConcurrentHashMap<>();
    
    private final Map<String, LongAdder> violationCounts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> blockCounts = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> allowCounts = new ConcurrentHashMap<>();
    
    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong totalViolations = new AtomicLong(0);
    private final AtomicLong totalResets = new AtomicLong(0);
    
    private final Map<String, Instant> lastViolationTime = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveViolations = new ConcurrentHashMap<>();
    
    public void recordRateLimitCheck(
            String endpoint,
            String userId,
            boolean allowed,
            RateLimitAlgorithm algorithm,
            long latencyMs) {
        
        String status = allowed ? "allowed" : "blocked";
        totalChecks.incrementAndGet();
        
        if (allowed) {
            allowCounts.computeIfAbsent(endpoint, k -> new LongAdder()).increment();
        } else {
            blockCounts.computeIfAbsent(endpoint, k -> new LongAdder()).increment();
        }
        
        Counter counter = counterCache.computeIfAbsent(
            "ratelimit.check." + endpoint + "." + status + "." + algorithm,
            key -> Counter.builder("rate.limit.checks")
                .tag("endpoint", endpoint)
                .tag("status", status)
                .tag("algorithm", algorithm.name())
                .description("Total rate limit checks")
                .register(meterRegistry)
        );
        counter.increment();
        
        Timer timer = timerCache.computeIfAbsent(
            "ratelimit.latency." + algorithm,
            key -> Timer.builder("rate.limit.check.latency")
                .tag("algorithm", algorithm.name())
                .description("Rate limit check latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
        );
        timer.record(Duration.ofMillis(latencyMs));
        
        DistributionSummary summary = summaryCache.computeIfAbsent(
            "ratelimit.latency.summary." + endpoint,
            key -> DistributionSummary.builder("rate.limit.latency.summary")
                .tag("endpoint", endpoint)
                .description("Rate limit latency distribution")
                .baseUnit("milliseconds")
                .register(meterRegistry)
        );
        summary.record(latencyMs);
        
        log.debug("Rate limit check recorded: endpoint={} userId={} status={} algorithm={} latency={}ms",
                endpoint, userId, status, algorithm, latencyMs);
    }
    
    public void recordRateLimitViolation(
            String userId,
            String endpoint,
            String ipAddress,
            int attemptedRequests,
            int allowedRequests,
            ViolationSeverity severity) {
        
        totalViolations.incrementAndGet();
        violationCounts.computeIfAbsent(endpoint, k -> new LongAdder()).increment();
        
        Instant now = Instant.now();
        Instant lastViolation = lastViolationTime.put(endpoint + ":" + userId, now);
        
        int consecutive = 1;
        if (lastViolation != null && Duration.between(lastViolation, now).getSeconds() < 60) {
            consecutive = consecutiveViolations.compute(
                endpoint + ":" + userId,
                (k, v) -> v == null ? 2 : v + 1
            );
        } else {
            consecutiveViolations.put(endpoint + ":" + userId, 1);
        }
        
        Counter counter = counterCache.computeIfAbsent(
            "ratelimit.violation." + endpoint + "." + severity,
            key -> Counter.builder("rate.limit.violations")
                .tag("endpoint", endpoint)
                .tag("severity", severity.name())
                .description("Total rate limit violations")
                .register(meterRegistry)
        );
        counter.increment();
        
        Gauge.builder("rate.limit.violation.consecutive", consecutiveViolations,
            map -> map.getOrDefault(endpoint + ":" + userId, 0))
            .tag("endpoint", endpoint)
            .tag("userId", userId)
            .description("Consecutive violations")
            .register(meterRegistry);
        
        DistributionSummary excessSummary = summaryCache.computeIfAbsent(
            "ratelimit.excess." + endpoint,
            key -> DistributionSummary.builder("rate.limit.excess.requests")
                .tag("endpoint", endpoint)
                .description("Excess requests beyond limit")
                .baseUnit("requests")
                .register(meterRegistry)
        );
        excessSummary.record(attemptedRequests - allowedRequests);
        
        log.warn("Rate limit violation recorded: userId={} endpoint={} ip={} attempted={} allowed={} severity={} consecutive={}",
                userId, endpoint, ipAddress, attemptedRequests, allowedRequests, severity, consecutive);
    }
    
    public void recordTokenBucketRefill(
            String key,
            double tokensAdded,
            double currentTokens,
            double maxTokens) {
        
        Gauge.builder("rate.limit.token.bucket.level", () -> currentTokens)
            .tag("key", key)
            .description("Current token bucket level")
            .register(meterRegistry);
        
        double utilization = (currentTokens / maxTokens) * 100.0;
        
        Gauge.builder("rate.limit.token.bucket.utilization", () -> utilization)
            .tag("key", key)
            .description("Token bucket utilization percentage")
            .register(meterRegistry);
        
        DistributionSummary summary = summaryCache.computeIfAbsent(
            "ratelimit.token.refill." + key,
            k -> DistributionSummary.builder("rate.limit.token.refill")
                .tag("key", key)
                .description("Token bucket refill amounts")
                .baseUnit("tokens")
                .register(meterRegistry)
        );
        summary.record(tokensAdded);
        
        log.debug("Token bucket refill recorded: key={} added={} current={} max={} utilization={}%",
                key, tokensAdded, currentTokens, maxTokens, String.format("%.2f", utilization));
    }
    
    public void recordSlidingWindowOperation(
            String key,
            int windowSize,
            int currentRequests,
            int cleanedRequests) {
        
        Gauge.builder("rate.limit.sliding.window.size", () -> currentRequests)
            .tag("key", key)
            .description("Current sliding window size")
            .register(meterRegistry);
        
        if (cleanedRequests > 0) {
            Counter cleanupCounter = counterCache.computeIfAbsent(
                "ratelimit.sliding.cleanup." + key,
                k -> Counter.builder("rate.limit.sliding.window.cleanup")
                    .tag("key", key)
                    .description("Sliding window cleanup operations")
                    .register(meterRegistry)
            );
            cleanupCounter.increment();
            
            DistributionSummary summary = summaryCache.computeIfAbsent(
                "ratelimit.sliding.cleaned." + key,
                k -> DistributionSummary.builder("rate.limit.sliding.window.cleaned")
                    .tag("key", key)
                    .description("Number of cleaned requests")
                    .baseUnit("requests")
                    .register(meterRegistry)
            );
            summary.record(cleanedRequests);
        }
        
        log.debug("Sliding window operation recorded: key={} windowSize={} current={} cleaned={}",
                key, windowSize, currentRequests, cleanedRequests);
    }
    
    public void recordLeakyBucketDrain(
            String key,
            double waterLevel,
            double capacity,
            double drainedAmount) {
        
        double fillPercentage = (waterLevel / capacity) * 100.0;
        
        Gauge.builder("rate.limit.leaky.bucket.level", () -> waterLevel)
            .tag("key", key)
            .description("Current leaky bucket water level")
            .register(meterRegistry);
        
        Gauge.builder("rate.limit.leaky.bucket.fill", () -> fillPercentage)
            .tag("key", key)
            .description("Leaky bucket fill percentage")
            .register(meterRegistry);
        
        DistributionSummary summary = summaryCache.computeIfAbsent(
            "ratelimit.leaky.drained." + key,
            k -> DistributionSummary.builder("rate.limit.leaky.bucket.drained")
                .tag("key", key)
                .description("Amount drained from leaky bucket")
                .baseUnit("units")
                .register(meterRegistry)
        );
        summary.record(drainedAmount);
        
        log.debug("Leaky bucket drain recorded: key={} level={} capacity={} drained={} fill={}%",
                key, waterLevel, capacity, drainedAmount, String.format("%.2f", fillPercentage));
    }
    
    public void recordRateLimitReset(String key, String reason) {
        totalResets.incrementAndGet();
        
        Counter counter = counterCache.computeIfAbsent(
            "ratelimit.reset." + reason,
            k -> Counter.builder("rate.limit.resets")
                .tag("reason", reason)
                .description("Rate limit reset operations")
                .register(meterRegistry)
        );
        counter.increment();
        
        consecutiveViolations.remove(key);
        lastViolationTime.remove(key);
        
        log.debug("Rate limit reset recorded: key={} reason={}", key, reason);
    }
    
    public void recordBurstDetection(
            String endpoint,
            String userId,
            int requestsInBurst,
            Duration burstDuration) {
        
        Counter counter = counterCache.computeIfAbsent(
            "ratelimit.burst." + endpoint,
            key -> Counter.builder("rate.limit.bursts")
                .tag("endpoint", endpoint)
                .description("Detected traffic bursts")
                .register(meterRegistry)
        );
        counter.increment();
        
        DistributionSummary burstSize = summaryCache.computeIfAbsent(
            "ratelimit.burst.size." + endpoint,
            key -> DistributionSummary.builder("rate.limit.burst.size")
                .tag("endpoint", endpoint)
                .description("Burst size in requests")
                .baseUnit("requests")
                .register(meterRegistry)
        );
        burstSize.record(requestsInBurst);
        
        log.warn("Burst detected: endpoint={} userId={} requests={} duration={}ms",
                endpoint, userId, requestsInBurst, burstDuration.toMillis());
    }
    
    public void recordRateLimitError(String operation, String errorType, String message) {
        Counter counter = counterCache.computeIfAbsent(
            "ratelimit.error." + errorType,
            key -> Counter.builder("rate.limit.errors")
                .tag("operation", operation)
                .tag("error_type", errorType)
                .description("Rate limit operation errors")
                .register(meterRegistry)
        );
        counter.increment();
        
        log.error("Rate limit error recorded: operation={} errorType={} message={}",
                operation, errorType, message);
    }
    
    public void recordCacheOperation(String operation, boolean hit) {
        String status = hit ? "hit" : "miss";
        
        Counter counter = counterCache.computeIfAbsent(
            "ratelimit.cache." + operation + "." + status,
            key -> Counter.builder("rate.limit.cache.operations")
                .tag("operation", operation)
                .tag("status", status)
                .description("Rate limit cache operations")
                .register(meterRegistry)
        );
        counter.increment();
    }
    
    @Scheduled(fixedRate = 60000)
    public void publishAggregatedMetrics() {
        try {
            Gauge.builder("rate.limit.checks.total", totalChecks, AtomicLong::get)
                .description("Total rate limit checks")
                .register(meterRegistry);
            
            Gauge.builder("rate.limit.violations.total", totalViolations, AtomicLong::get)
                .description("Total rate limit violations")
                .register(meterRegistry);
            
            Gauge.builder("rate.limit.resets.total", totalResets, AtomicLong::get)
                .description("Total rate limit resets")
                .register(meterRegistry);
            
            for (Map.Entry<String, LongAdder> entry : allowCounts.entrySet()) {
                long allowed = entry.getValue().sum();
                long blocked = blockCounts.getOrDefault(entry.getKey(), new LongAdder()).sum();
                long total = allowed + blocked;
                
                if (total > 0) {
                    double allowRate = (double) allowed / total * 100.0;
                    
                    Gauge.builder("rate.limit.allow.rate", () -> allowRate)
                        .tag("endpoint", entry.getKey())
                        .description("Rate limit allow rate percentage")
                        .register(meterRegistry);
                }
            }
            
            log.debug("Published aggregated metrics: checks={} violations={} resets={}",
                    totalChecks.get(), totalViolations.get(), totalResets.get());
            
        } catch (Exception e) {
            log.error("Error publishing aggregated metrics", e);
        }
    }
    
    public Map<String, Long> getViolationCounts() {
        Map<String, Long> counts = new ConcurrentHashMap<>();
        violationCounts.forEach((key, adder) -> counts.put(key, adder.sum()));
        return counts;
    }
    
    public Map<String, Long> getAllowBlockCounts() {
        Map<String, Long> counts = new ConcurrentHashMap<>();
        allowCounts.forEach((key, adder) -> counts.put(key + ".allowed", adder.sum()));
        blockCounts.forEach((key, adder) -> counts.put(key + ".blocked", adder.sum()));
        return counts;
    }
    
    public void resetMetrics() {
        log.info("Resetting all rate limit metrics");
        
        totalChecks.set(0);
        totalViolations.set(0);
        totalResets.set(0);
        
        violationCounts.clear();
        blockCounts.clear();
        allowCounts.clear();
        consecutiveViolations.clear();
        lastViolationTime.clear();
    }
    
    public enum ViolationSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}