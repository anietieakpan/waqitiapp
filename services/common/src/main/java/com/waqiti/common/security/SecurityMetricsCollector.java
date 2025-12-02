package com.waqiti.common.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Security Metrics Collector for monitoring security-related events and metrics
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityMetricsCollector implements HealthIndicator {

    private final MeterRegistry meterRegistry;
    private final WaqitiSecurityProperties securityProperties;

    // Counters
    private Counter successfulAuthCounter;
    private Counter failedAuthCounter;
    private Counter authorizationDeniedCounter;
    private Counter cspViolationCounter;
    private Counter suspiciousRequestCounter;
    private Counter rateLimitExceededCounter;

    // Timers
    private Timer authenticationTimer;
    private Timer authorizationTimer;

    // Gauges
    private final AtomicLong activeSessionCount = new AtomicLong(0);
    private final AtomicLong suspiciousActivityCount = new AtomicLong(0);

    // Rate tracking
    private final ConcurrentHashMap<String, RateTracker> ipRateTrackers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateTracker> userRateTrackers = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeMetrics() {
        log.info("Initializing security metrics collectors");

        // Initialize counters
        successfulAuthCounter = Counter.builder("waqiti.security.authentication.success")
            .description("Number of successful authentication attempts")
            .register(meterRegistry);

        failedAuthCounter = Counter.builder("waqiti.security.authentication.failure")
            .description("Number of failed authentication attempts")
            .tag("type", "all")
            .register(meterRegistry);

        authorizationDeniedCounter = Counter.builder("waqiti.security.authorization.denied")
            .description("Number of authorization denied events")
            .register(meterRegistry);

        cspViolationCounter = Counter.builder("waqiti.security.csp.violation")
            .description("Number of Content Security Policy violations")
            .register(meterRegistry);

        suspiciousRequestCounter = Counter.builder("waqiti.security.suspicious.request")
            .description("Number of suspicious requests detected")
            .register(meterRegistry);

        rateLimitExceededCounter = Counter.builder("waqiti.security.rate.limit.exceeded")
            .description("Number of rate limit exceeded events")
            .register(meterRegistry);

        // Initialize timers
        authenticationTimer = Timer.builder("waqiti.security.authentication.duration")
            .description("Time taken for authentication operations")
            .register(meterRegistry);

        authorizationTimer = Timer.builder("waqiti.security.authorization.duration")
            .description("Time taken for authorization operations")
            .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("waqiti.security.sessions.active", this, SecurityMetricsCollector::getActiveSessionCount)
            .description("Number of active user sessions")
            .register(meterRegistry);

        Gauge.builder("waqiti.security.suspicious.activity", this, SecurityMetricsCollector::getSuspiciousActivityCount)
            .description("Current level of suspicious activity")
            .register(meterRegistry);

        log.info("Security metrics collectors initialized successfully");
    }

    /**
     * Record successful authentication
     */
    public void recordSuccessfulAuthentication(String username, String ipAddress) {
        successfulAuthCounter.increment();
        log.debug("Recorded successful authentication for user: {} from IP: {}", username, ipAddress);
    }

    /**
     * Record failed authentication
     */
    public void recordFailedAuthentication(String username, String ipAddress, String reason) {
        failedAuthCounter.increment();
        
        // Track rate by IP
        trackIpRate(ipAddress);
        
        // Track rate by user
        if (username != null && !username.isEmpty()) {
            trackUserRate(username);
        }
        
        log.debug("Recorded failed authentication for user: {} from IP: {} reason: {}", 
            username, ipAddress, reason);
    }

    /**
     * Record authorization denied event
     */
    public void recordAuthorizationDenied(String username, String resource) {
        authorizationDeniedCounter.increment();
        suspiciousActivityCount.incrementAndGet();
        
        log.debug("Recorded authorization denied for user: {} accessing resource: {}", username, resource);
    }

    /**
     * Record CSP violation
     */
    public void recordCspViolation(String violationType, String blockedUri, String sourceFile) {
        cspViolationCounter.increment();
        
        log.warn("CSP violation recorded - type: {} blocked URI: {} source: {}", 
            violationType, blockedUri, sourceFile);
    }

    /**
     * Record suspicious request
     */
    public void recordSuspiciousRequest(String ipAddress, String userAgent, String reason) {
        suspiciousRequestCounter.increment();
        suspiciousActivityCount.incrementAndGet();
        
        log.warn("Suspicious request detected from IP: {} User-Agent: {} Reason: {}", 
            ipAddress, userAgent, reason);
    }

    /**
     * Record rate limit exceeded
     */
    public void recordRateLimitExceeded(String identifier, String limitType) {
        rateLimitExceededCounter.increment();
        
        log.warn("Rate limit exceeded for {} limit type: {}", identifier, limitType);
    }

    /**
     * Time authentication operations
     */
    public Timer.Sample startAuthenticationTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordAuthenticationTime(Timer.Sample sample) {
        sample.stop(authenticationTimer);
    }

    /**
     * Update active session count
     */
    public void updateActiveSessionCount(long count) {
        activeSessionCount.set(count);
    }

    /**
     * Get metrics for health check
     */
    public double getActiveSessionCount() {
        return activeSessionCount.get();
    }

    public double getSuspiciousActivityCount() {
        return suspiciousActivityCount.get();
    }

    /**
     * Track IP-based rate limiting
     */
    private void trackIpRate(String ipAddress) {
        RateTracker tracker = ipRateTrackers.computeIfAbsent(ipAddress, 
            k -> new RateTracker(securityProperties.getMonitoring().getAlertThresholds().getMaxFailedAuthPerMinute()));
        
        if (tracker.isRateExceeded()) {
            recordRateLimitExceeded(ipAddress, "IP_FAILED_AUTH");
            log.warn("IP {} exceeded failed authentication rate limit", ipAddress);
        }
    }

    /**
     * Track user-based rate limiting
     */
    private void trackUserRate(String username) {
        RateTracker tracker = userRateTrackers.computeIfAbsent(username, 
            k -> new RateTracker(securityProperties.getMonitoring().getAlertThresholds().getMaxFailedAuthPerMinute()));
        
        if (tracker.isRateExceeded()) {
            recordRateLimitExceeded(username, "USER_FAILED_AUTH");
            log.warn("User {} exceeded failed authentication rate limit", username);
        }
    }

    /**
     * Health check implementation
     */
    @Override
    public Health health() {
        Health.Builder healthBuilder = Health.up();
        
        // Check for high suspicious activity
        long suspiciousCount = suspiciousActivityCount.get();
        if (suspiciousCount > 100) {
            healthBuilder.down()
                .withDetail("suspicious_activity", suspiciousCount)
                .withDetail("reason", "High level of suspicious security activity detected");
        } else if (suspiciousCount > 50) {
            healthBuilder.status("WARNING")
                .withDetail("suspicious_activity", suspiciousCount)
                .withDetail("reason", "Elevated suspicious security activity");
        }
        
        // Add metrics details
        healthBuilder
            .withDetail("active_sessions", activeSessionCount.get())
            .withDetail("suspicious_activity_count", suspiciousCount)
            .withDetail("failed_auth_rate_trackers", ipRateTrackers.size())
            .withDetail("user_rate_trackers", userRateTrackers.size());
        
        return healthBuilder.build();
    }

    /**
     * Rate tracker for monitoring request rates
     */
    private static class RateTracker {
        private final int maxRequests;
        private final Duration timeWindow = Duration.ofMinutes(1);
        private final ConcurrentHashMap<Long, AtomicLong> requestCounts = new ConcurrentHashMap<>();
        
        public RateTracker(int maxRequests) {
            this.maxRequests = maxRequests;
        }
        
        public boolean isRateExceeded() {
            long currentMinute = Instant.now().getEpochSecond() / 60;
            
            // Clean up old entries
            requestCounts.entrySet().removeIf(entry -> 
                entry.getKey() < currentMinute - 1);
            
            // Increment current minute count
            AtomicLong count = requestCounts.computeIfAbsent(currentMinute, k -> new AtomicLong(0));
            return count.incrementAndGet() > maxRequests;
        }
    }
}