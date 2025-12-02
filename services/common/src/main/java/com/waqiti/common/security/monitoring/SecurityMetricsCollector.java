package com.waqiti.common.security.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Security metrics collector for monitoring security events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    // Counters for security events
    private final Counter authenticationSuccessCounter;
    private final Counter authenticationFailureCounter;
    private final Counter authorizationFailureCounter;
    private final Counter securityThreatCounter;
    private final Counter rateLimitExceededCounter;
    private final Counter suspiciousActivityCounter;
    
    // Timers for performance monitoring
    private final Timer authenticationTimer;
    private final Timer authorizationTimer;
    
    // Gauges for current state
    private final AtomicLong activeSessions = new AtomicLong(0);
    private final AtomicLong suspiciousDevices = new AtomicLong(0);
    private final AtomicLong blockedIps = new AtomicLong(0);
    
    public SecurityMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.authenticationSuccessCounter = Counter.builder("security.authentication.success")
            .description("Number of successful authentications")
            .register(meterRegistry);
        
        this.authenticationFailureCounter = Counter.builder("security.authentication.failure")
            .description("Number of failed authentications")
            .register(meterRegistry);
        
        this.authorizationFailureCounter = Counter.builder("security.authorization.failure")
            .description("Number of authorization failures")
            .register(meterRegistry);
        
        this.securityThreatCounter = Counter.builder("security.threats.detected")
            .description("Number of security threats detected")
            .register(meterRegistry);
        
        this.rateLimitExceededCounter = Counter.builder("security.ratelimit.exceeded")
            .description("Number of rate limit violations")
            .register(meterRegistry);
        
        this.suspiciousActivityCounter = Counter.builder("security.suspicious.activity")
            .description("Number of suspicious activities detected")
            .register(meterRegistry);
        
        // Initialize timers
        this.authenticationTimer = Timer.builder("security.authentication.duration")
            .description("Time taken for authentication")
            .register(meterRegistry);
        
        this.authorizationTimer = Timer.builder("security.authorization.duration")
            .description("Time taken for authorization")
            .register(meterRegistry);
        
        // Initialize gauges
        Gauge.builder("security.sessions.active", activeSessions, AtomicLong::get)
            .description("Number of active user sessions")
            .register(meterRegistry);
        
        Gauge.builder("security.devices.suspicious", suspiciousDevices, AtomicLong::get)
            .description("Number of suspicious devices")
            .register(meterRegistry);
        
        Gauge.builder("security.ips.blocked", blockedIps, AtomicLong::get)
            .description("Number of blocked IP addresses")
            .register(meterRegistry);
    }
    
    /**
     * Record successful authentication
     */
    public void recordAuthenticationSuccess(String method) {
        Counter.builder("security.authentication.success")
            .tag("method", method)
            .register(meterRegistry)
            .increment();
        log.debug("Recorded authentication success for method: {}", method);
    }
    
    /**
     * Record failed authentication
     */
    public void recordAuthenticationFailure(String method, String reason) {
        Counter.builder("security.authentication.failure")
            .tag("method", method)
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
        log.debug("Recorded authentication failure for method: {}, reason: {}", method, reason);
    }
    
    /**
     * Record authorization failure
     */
    public void recordAuthorizationFailure(String resource, String role) {
        Counter.builder("security.authorization.failure")
            .tag("resource", resource)
            .tag("required_role", role)
            .register(meterRegistry)
            .increment();
        log.debug("Recorded authorization failure for resource: {}, required role: {}", resource, role);
    }
    
    /**
     * Record security threat detection
     */
    public void recordSecurityThreat(String threatType, int severityLevel) {
        Counter.builder("security.threats.detected")
            .tag("threat_type", threatType)
            .tag("severity", String.valueOf(severityLevel))
            .register(meterRegistry)
            .increment();
        log.debug("Recorded security threat: {}, severity: {}", threatType, severityLevel);
    }
    
    /**
     * Record rate limit exceeded
     */
    public void recordRateLimitExceeded(String endpoint, String clientType) {
        Counter.builder("security.ratelimit.exceeded")
            .tag("endpoint", endpoint)
            .tag("client_type", clientType)
            .register(meterRegistry)
            .increment();
        log.debug("Recorded rate limit exceeded for endpoint: {}, client: {}", endpoint, clientType);
    }
    
    /**
     * Record suspicious activity
     */
    public void recordSuspiciousActivity(String activityType, String source) {
        Counter.builder("security.suspicious.activity")
            .tag("activity_type", activityType)
            .tag("source", source)
            .register(meterRegistry)
            .increment();
        log.debug("Recorded suspicious activity: {}, source: {}", activityType, source);
    }
    
    /**
     * Time authentication operations
     */
    public Timer.Sample startAuthenticationTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * Stop authentication timer
     */
    public void stopAuthenticationTimer(Timer.Sample sample, String method) {
        sample.stop(Timer.builder("security.authentication.duration")
            .tag("method", method)
            .register(meterRegistry));
    }
    
    /**
     * Time authorization operations
     */
    public Timer.Sample startAuthorizationTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * Stop authorization timer
     */
    public void stopAuthorizationTimer(Timer.Sample sample, String resource) {
        sample.stop(Timer.builder("security.authorization.duration")
            .tag("resource", resource)
            .register(meterRegistry));
    }
    
    /**
     * Update active sessions count
     */
    public void setActiveSessions(long count) {
        activeSessions.set(count);
    }
    
    /**
     * Increment active sessions
     */
    public void incrementActiveSessions() {
        activeSessions.incrementAndGet();
    }
    
    /**
     * Decrement active sessions
     */
    public void decrementActiveSessions() {
        activeSessions.decrementAndGet();
    }
    
    /**
     * Update suspicious devices count
     */
    public void setSuspiciousDevices(long count) {
        suspiciousDevices.set(count);
    }
    
    /**
     * Update blocked IPs count
     */
    public void setBlockedIps(long count) {
        blockedIps.set(count);
    }
    
    /**
     * Get current authentication success rate
     */
    public double getAuthenticationSuccessRate() {
        double successCount = authenticationSuccessCounter.count();
        double failureCount = authenticationFailureCounter.count();
        double total = successCount + failureCount;
        
        if (total == 0) {
            return 1.0; // No attempts yet
        }
        
        return successCount / total;
    }
    
    /**
     * Get security metrics summary
     */
    public SecurityMetricsSummary getMetricsSummary() {
        return new SecurityMetricsSummary(
            authenticationSuccessCounter.count(),
            authenticationFailureCounter.count(),
            authorizationFailureCounter.count(),
            securityThreatCounter.count(),
            rateLimitExceededCounter.count(),
            suspiciousActivityCounter.count(),
            activeSessions.get(),
            suspiciousDevices.get(),
            blockedIps.get()
        );
    }
    
    /**
     * Security metrics summary data class
     */
    public static class SecurityMetricsSummary {
        private final double authenticationSuccesses;
        private final double authenticationFailures;
        private final double authorizationFailures;
        private final double securityThreats;
        private final double rateLimitViolations;
        private final double suspiciousActivities;
        private final long activeSessions;
        private final long suspiciousDevices;
        private final long blockedIps;
        
        public SecurityMetricsSummary(double authenticationSuccesses, double authenticationFailures,
                                    double authorizationFailures, double securityThreats,
                                    double rateLimitViolations, double suspiciousActivities,
                                    long activeSessions, long suspiciousDevices, long blockedIps) {
            this.authenticationSuccesses = authenticationSuccesses;
            this.authenticationFailures = authenticationFailures;
            this.authorizationFailures = authorizationFailures;
            this.securityThreats = securityThreats;
            this.rateLimitViolations = rateLimitViolations;
            this.suspiciousActivities = suspiciousActivities;
            this.activeSessions = activeSessions;
            this.suspiciousDevices = suspiciousDevices;
            this.blockedIps = blockedIps;
        }
        
        // Getters
        public double getAuthenticationSuccesses() { return authenticationSuccesses; }
        public double getAuthenticationFailures() { return authenticationFailures; }
        public double getAuthorizationFailures() { return authorizationFailures; }
        public double getSecurityThreats() { return securityThreats; }
        public double getRateLimitViolations() { return rateLimitViolations; }
        public double getSuspiciousActivities() { return suspiciousActivities; }
        public long getActiveSessions() { return activeSessions; }
        public long getSuspiciousDevices() { return suspiciousDevices; }
        public long getBlockedIps() { return blockedIps; }
    }
}