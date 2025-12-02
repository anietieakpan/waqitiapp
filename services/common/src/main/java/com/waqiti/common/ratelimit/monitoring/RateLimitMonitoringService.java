package com.waqiti.common.ratelimit.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CRITICAL SECURITY MONITORING - Rate Limit Monitoring Service
 * 
 * Provides comprehensive monitoring and metrics for rate limiting across all services:
 * - Real-time rate limit violation tracking
 * - Performance metrics for rate limiting operations
 * - Health monitoring of Redis-backed rate limiting
 * - Alerting thresholds for security incidents
 * - Dashboard metrics for operational visibility
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitMonitoringService {

    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, String> redisTemplate;

    // Counters for rate limit events
    private Counter rateLimitViolationsCounter;
    private Counter rateLimitChecksCounter;
    private Counter rateLimitBlocksCounter;
    private Counter rateLimitAllowedCounter;
    
    // Timers for performance monitoring
    private Timer rateLimitCheckTimer;
    private Timer redisOperationTimer;
    
    // Gauges for current state
    private final AtomicLong activeRateLimits = new AtomicLong(0);
    private final AtomicLong blockedIps = new AtomicLong(0);
    private final AtomicLong blockedUsers = new AtomicLong(0);
    
    // Cache for rate limit statistics
    private final Map<String, AtomicLong> endpointViolations = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> ipViolations = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> userViolations = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeMetrics() {
        // Initialize counters
        rateLimitViolationsCounter = Counter.builder("waqiti.rate_limit.violations")
            .description("Total number of rate limit violations")
            .tag("service", "rate-limiting")
            .register(meterRegistry);
            
        rateLimitChecksCounter = Counter.builder("waqiti.rate_limit.checks")
            .description("Total number of rate limit checks performed")
            .tag("service", "rate-limiting")
            .register(meterRegistry);
            
        rateLimitBlocksCounter = Counter.builder("waqiti.rate_limit.blocks")
            .description("Total number of IPs/users blocked due to rate limiting")
            .tag("service", "rate-limiting")
            .register(meterRegistry);
            
        rateLimitAllowedCounter = Counter.builder("waqiti.rate_limit.allowed")
            .description("Total number of requests allowed by rate limiting")
            .tag("service", "rate-limiting")
            .register(meterRegistry);
        
        // Initialize timers
        rateLimitCheckTimer = Timer.builder("waqiti.rate_limit.check_duration")
            .description("Time taken to perform rate limit checks")
            .tag("service", "rate-limiting")
            .register(meterRegistry);
            
        redisOperationTimer = Timer.builder("waqiti.rate_limit.redis_duration")
            .description("Time taken for Redis operations in rate limiting")
            .tag("service", "rate-limiting")
            .register(meterRegistry);
        
        // Initialize gauges
        Gauge.builder("waqiti.rate_limit.active_limits")
            .description("Current number of active rate limits")
            .tag("service", "rate-limiting")
            .register(meterRegistry, this, service -> service.activeRateLimits.get());
            
        Gauge.builder("waqiti.rate_limit.blocked_ips")
            .description("Current number of blocked IP addresses")
            .tag("service", "rate-limiting")
            .register(meterRegistry, this, service -> service.blockedIps.get());
            
        Gauge.builder("waqiti.rate_limit.blocked_users")
            .description("Current number of blocked users")
            .tag("service", "rate-limiting")
            .register(meterRegistry, this, service -> service.blockedUsers.get());
        
        log.info("Rate limiting monitoring metrics initialized successfully");
    }

    /**
     * Record a rate limit check operation
     */
    public Timer.Sample startRateLimitCheck() {
        rateLimitChecksCounter.increment();
        return Timer.start(meterRegistry);
    }

    /**
     * Record completion of rate limit check
     */
    public void recordRateLimitCheck(Timer.Sample sample, boolean allowed, String endpoint, String keyType) {
        sample.stop(rateLimitCheckTimer);
        
        if (allowed) {
            rateLimitAllowedCounter.increment(
                "endpoint", endpoint,
                "key_type", keyType
            );
        }
    }

    /**
     * Record a rate limit violation
     */
    public void recordRateLimitViolation(String endpoint, String keyType, String identifier) {
        rateLimitViolationsCounter.increment(
            "endpoint", endpoint,
            "key_type", keyType
        );
        
        // Track violations by endpoint
        endpointViolations.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
        
        // Track violations by IP or user
        if ("IP".equals(keyType)) {
            ipViolations.computeIfAbsent(identifier, k -> new AtomicLong(0)).incrementAndGet();
        } else if ("USER".equals(keyType)) {
            userViolations.computeIfAbsent(identifier, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        log.warn("RATE_LIMIT_VIOLATION_RECORDED: endpoint={} keyType={} identifier={}", 
                endpoint, keyType, identifier);
    }

    /**
     * Record a rate limit block (IP or user blocked)
     */
    public void recordRateLimitBlock(String keyType, String identifier, String reason, long durationSeconds) {
        rateLimitBlocksCounter.increment(
            "key_type", keyType,
            "reason", reason
        );
        
        if ("IP".equals(keyType)) {
            blockedIps.incrementAndGet();
        } else if ("USER".equals(keyType)) {
            blockedUsers.incrementAndGet();
        }
        
        log.error("RATE_LIMIT_BLOCK_RECORDED: keyType={} identifier={} reason={} duration={}s", 
                keyType, identifier, reason, durationSeconds);
    }

    /**
     * Record Redis operation timing
     */
    public Timer.Sample startRedisOperation() {
        return Timer.start(meterRegistry);
    }

    /**
     * Complete Redis operation timing
     */
    public void recordRedisOperation(Timer.Sample sample, String operation) {
        sample.stop(Timer.builder("waqiti.rate_limit.redis_duration")
            .tag("operation", operation)
            .register(meterRegistry));
    }

    /**
     * Get rate limiting health status
     */
    public RateLimitHealthStatus getHealthStatus() {
        try {
            // Check Redis connectivity
            Timer.Sample sample = startRedisOperation();
            redisTemplate.hasKey("health_check");
            recordRedisOperation(sample, "health_check");
            
            return RateLimitHealthStatus.builder()
                .healthy(true)
                .redisConnected(true)
                .activeRateLimits(activeRateLimits.get())
                .blockedIps(blockedIps.get())
                .blockedUsers(blockedUsers.get())
                .totalViolations(getTotalViolations())
                .message("Rate limiting service is healthy")
                .build();
                
        } catch (Exception e) {
            log.error("Rate limiting health check failed", e);
            return RateLimitHealthStatus.builder()
                .healthy(false)
                .redisConnected(false)
                .message("Rate limiting service unhealthy: " + e.getMessage())
                .build();
        }
    }

    /**
     * Get top violating endpoints
     */
    public Map<String, Long> getTopViolatingEndpoints(int limit) {
        return endpointViolations.entrySet().stream()
            .sorted(Map.Entry.<String, AtomicLong>comparingByValue((a, b) -> 
                Long.compare(b.get(), a.get())))
            .limit(limit)
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().get(),
                (e1, e2) -> e1,
                java.util.LinkedHashMap::new
            ));
    }

    /**
     * Get top violating IPs
     */
    public Map<String, Long> getTopViolatingIps(int limit) {
        return ipViolations.entrySet().stream()
            .sorted(Map.Entry.<String, AtomicLong>comparingByValue((a, b) -> 
                Long.compare(b.get(), a.get())))
            .limit(limit)
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().get(),
                (e1, e2) -> e1,
                java.util.LinkedHashMap::new
            ));
    }

    /**
     * Update active rate limits count periodically
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void updateActiveRateLimitsCount() {
        try {
            Timer.Sample sample = startRedisOperation();
            
            // Count active rate limit keys in Redis
            var keys = redisTemplate.keys("rate_limit:*");
            long count = keys != null ? keys.size() : 0;
            activeRateLimits.set(count);
            
            recordRedisOperation(sample, "count_active_limits");
            
        } catch (Exception e) {
            log.warn("Failed to update active rate limits count", e);
        }
    }

    /**
     * Update blocked counts periodically
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void updateBlockedCounts() {
        try {
            Timer.Sample sample = startRedisOperation();
            
            // Count blocked IPs
            var blockedIpKeys = redisTemplate.keys("blocked_ips:*");
            long ipCount = blockedIpKeys != null ? blockedIpKeys.size() : 0;
            blockedIps.set(ipCount);
            
            // Count blocked users
            var blockedUserKeys = redisTemplate.keys("blocked_users:*");
            long userCount = blockedUserKeys != null ? blockedUserKeys.size() : 0;
            blockedUsers.set(userCount);
            
            recordRedisOperation(sample, "count_blocked");
            
        } catch (Exception e) {
            log.warn("Failed to update blocked counts", e);
        }
    }

    /**
     * Alert when violation thresholds are exceeded
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void checkViolationThresholds() {
        // Check for endpoints with high violation rates
        endpointViolations.entrySet().stream()
            .filter(entry -> entry.getValue().get() > 100) // More than 100 violations
            .forEach(entry -> {
                log.error("HIGH_VIOLATION_ALERT: Endpoint {} has {} violations", 
                        entry.getKey(), entry.getValue().get());
                
                // Here you would trigger alerts to monitoring systems
                // alertingService.sendAlert("HIGH_RATE_LIMIT_VIOLATIONS", entry.getKey(), entry.getValue().get());
            });
        
        // Check for IPs with excessive violations
        ipViolations.entrySet().stream()
            .filter(entry -> entry.getValue().get() > 50) // More than 50 violations
            .forEach(entry -> {
                log.error("SUSPICIOUS_IP_ALERT: IP {} has {} violations", 
                        entry.getKey(), entry.getValue().get());
                
                // Trigger IP investigation/blocking
                // securityService.investigateIp(entry.getKey());
            });
    }

    /**
     * Reset violation counters daily
     */
    @Scheduled(cron = "0 0 0 * * *") // Daily at midnight
    public void resetDailyCounters() {
        endpointViolations.clear();
        ipViolations.clear();
        userViolations.clear();
        
        log.info("Daily rate limit violation counters reset");
    }

    private long getTotalViolations() {
        return endpointViolations.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
    }

    @lombok.Data
    @lombok.Builder
    public static class RateLimitHealthStatus {
        private boolean healthy;
        private boolean redisConnected;
        private long activeRateLimits;
        private long blockedIps;
        private long blockedUsers;
        private long totalViolations;
        private String message;
    }
}