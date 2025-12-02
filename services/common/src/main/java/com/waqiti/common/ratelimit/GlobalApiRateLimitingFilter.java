package com.waqiti.common.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global API Rate Limiting Filter for the Waqiti Platform
 * 
 * This filter implements comprehensive rate limiting to protect APIs from:
 * - DDoS attacks
 * - Brute force attempts
 * - Resource exhaustion
 * - Unfair usage patterns
 * 
 * Rate Limiting Tiers:
 * 1. CRITICAL APIs - 10 req/min (KYC, Compliance, Large Transfers)
 * 2. FINANCIAL APIs - 100 req/min (Payments, Transfers, Balance)
 * 3. STANDARD APIs - 1000 req/min (Account info, History)
 * 4. PUBLIC APIs - 60 req/min (Unauthenticated endpoints)
 * 
 * Features:
 * - Per-user rate limiting
 * - Per-IP rate limiting for anonymous requests
 * - API tier-based limits
 * - Sliding window algorithm
 * - Automatic blacklisting for repeat offenders
 * 
 * @author Waqiti Platform Team
 * @since Phase 2 - P1 Remediation
 */
@Slf4j
@Component
@Order(1) // Execute early in filter chain
public class GlobalApiRateLimitingFilter extends OncePerRequestFilter {
    
    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    // Metrics
    private Counter rateLimitExceededCounter;
    private Counter blacklistCounter;
    
    // Track violations for blacklisting
    private final ConcurrentHashMap<String, ViolationTracker> violationTrackers = new ConcurrentHashMap<>();
    
    // Blacklist with expiry
    private final ConcurrentHashMap<String, LocalDateTime> blacklist = new ConcurrentHashMap<>();
    
    // API tier classifications
    private static final String CRITICAL_API_PATTERN = ".*(kyc|compliance|sanctions|large-transfer).*";
    private static final String FINANCIAL_API_PATTERN = ".*(payment|transfer|settlement|wallet).*";
    private static final String STANDARD_API_PATTERN = ".*(account|history|transaction|statement).*";
    private static final String PUBLIC_API_PATTERN = ".*(public|health|status|login|register).*";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientId = extractClientIdentifier(request);
        
        // Skip rate limiting for health checks and metrics
        if (isExemptPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Check blacklist
        if (isBlacklisted(clientId)) {
            log.warn("Blacklisted client attempted access. ClientId: {}, Path: {}", clientId, path);
            blacklistCounter.increment();
            sendRateLimitResponse(response, "Client temporarily banned due to excessive violations", 0);
            return;
        }
        
        // Determine API tier and get appropriate rate limiter
        String apiTier = determineApiTier(path);
        RateLimiter rateLimiter = getRateLimiterForTier(apiTier, clientId);
        
        try {
            // Attempt to acquire permission
            boolean permitted = rateLimiter.acquirePermission();
            
            if (permitted) {
                // Add rate limit headers
                addRateLimitHeaders(response, rateLimiter);
                
                // Reset violation count on successful request
                resetViolations(clientId);
                
                // Continue with request
                filterChain.doFilter(request, response);
                
            } else {
                handleRateLimitExceeded(clientId, path, response, rateLimiter);
            }
            
        } catch (RequestNotPermitted e) {
            handleRateLimitExceeded(clientId, path, response, rateLimiter);
        }
    }
    
    /**
     * Extract client identifier (user ID or IP address)
     */
    private String extractClientIdentifier(HttpServletRequest request) {
        // Try to get authenticated user
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return "user:" + userId;
        }
        
        // Try to get API key
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isEmpty()) {
            return "api:" + apiKey;
        }
        
        // Fall back to IP address
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }
        
        return "ip:" + ipAddress;
    }
    
    /**
     * Determine API tier based on path
     */
    private String determineApiTier(String path) {
        if (path.matches(CRITICAL_API_PATTERN)) {
            return "critical";
        } else if (path.matches(FINANCIAL_API_PATTERN)) {
            return "financial";
        } else if (path.matches(PUBLIC_API_PATTERN)) {
            return "public";
        } else {
            return "standard";
        }
    }
    
    /**
     * Get rate limiter for specific tier and client
     */
    private RateLimiter getRateLimiterForTier(String tier, String clientId) {
        String rateLimiterName = tier + ":" + clientId;
        
        // Try to get existing rate limiter
        try {
            return rateLimiterRegistry.rateLimiter(rateLimiterName);
        } catch (Exception e) {
            // Create new rate limiter for this client
            return createRateLimiterForTier(tier, rateLimiterName);
        }
    }
    
    /**
     * Create tier-specific rate limiter
     */
    private RateLimiter createRateLimiterForTier(String tier, String name) {
        io.github.resilience4j.ratelimiter.RateLimiterConfig config;
        
        switch (tier) {
            case "critical":
                config = io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .limitForPeriod(10) // 10 requests per minute
                    .timeoutDuration(Duration.ofSeconds(0)) // Don't wait
                    .build();
                break;
                
            case "financial":
                config = io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .limitForPeriod(100) // 100 requests per minute
                    .timeoutDuration(Duration.ofSeconds(0))
                    .build();
                break;
                
            case "public":
                config = io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .limitForPeriod(60) // 60 requests per minute
                    .timeoutDuration(Duration.ofSeconds(0))
                    .build();
                break;
                
            default: // standard
                config = io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .limitForPeriod(1000) // 1000 requests per minute
                    .timeoutDuration(Duration.ofSeconds(0))
                    .build();
        }
        
        return RateLimiter.of(name, config);
    }
    
    /**
     * Handle rate limit exceeded
     */
    private void handleRateLimitExceeded(String clientId, String path, 
                                        HttpServletResponse response, 
                                        RateLimiter rateLimiter) throws IOException {
        log.warn("Rate limit exceeded. ClientId: {}, Path: {}", clientId, path);
        
        // Track violation
        trackViolation(clientId);
        
        // Check if should blacklist
        if (shouldBlacklist(clientId)) {
            blacklistClient(clientId);
        }
        
        // Send rate limit response
        long waitTime = calculateWaitTime(rateLimiter);
        sendRateLimitResponse(response, "Rate limit exceeded", waitTime);
        
        // Update metrics
        rateLimitExceededCounter.increment();
    }
    
    /**
     * Add rate limit headers to response
     */
    private void addRateLimitHeaders(HttpServletResponse response, RateLimiter rateLimiter) {
        RateLimiter.Metrics metrics = rateLimiter.getMetrics();
        
        response.addHeader("X-RateLimit-Limit", String.valueOf(metrics.getNumberOfWaitingThreads()));
        response.addHeader("X-RateLimit-Remaining", String.valueOf(metrics.getAvailablePermissions()));
        response.addHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() + 60000));
    }
    
    /**
     * Send rate limit exceeded response
     */
    private void sendRateLimitResponse(HttpServletResponse response, 
                                      String message, 
                                      long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        if (retryAfterSeconds > 0) {
            response.addHeader("Retry-After", String.valueOf(retryAfterSeconds));
        }
        
        String jsonResponse = String.format(
            "{\"error\": \"RATE_LIMIT_EXCEEDED\", \"message\": \"%s\", \"retryAfter\": %d}",
            message, retryAfterSeconds
        );
        
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
    
    /**
     * Track violations for potential blacklisting
     */
    private void trackViolation(String clientId) {
        violationTrackers.compute(clientId, (key, tracker) -> {
            if (tracker == null) {
                tracker = new ViolationTracker();
            }
            tracker.recordViolation();
            return tracker;
        });
    }
    
    /**
     * Check if client should be blacklisted
     */
    private boolean shouldBlacklist(String clientId) {
        ViolationTracker tracker = violationTrackers.get(clientId);
        if (tracker == null) {
            return false;
        }
        
        // Blacklist if more than 10 violations in 5 minutes
        return tracker.getViolationCount() > 10 && 
               tracker.getTimeSinceFirstViolation().compareTo(Duration.ofMinutes(5)) < 0;
    }
    
    /**
     * Blacklist client temporarily
     */
    private void blacklistClient(String clientId) {
        LocalDateTime expiryTime = LocalDateTime.now().plusHours(1); // 1 hour ban
        blacklist.put(clientId, expiryTime);
        log.error("Client blacklisted for 1 hour due to excessive violations. ClientId: {}", clientId);
    }
    
    /**
     * Check if client is blacklisted
     */
    private boolean isBlacklisted(String clientId) {
        LocalDateTime expiryTime = blacklist.get(clientId);
        if (expiryTime == null) {
            return false;
        }
        
        if (LocalDateTime.now().isAfter(expiryTime)) {
            // Remove expired blacklist entry
            blacklist.remove(clientId);
            return false;
        }
        
        return true;
    }
    
    /**
     * Reset violation count on successful request
     */
    private void resetViolations(String clientId) {
        violationTrackers.remove(clientId);
    }
    
    /**
     * Calculate wait time for retry
     */
    private long calculateWaitTime(RateLimiter rateLimiter) {
        // Simple calculation - could be more sophisticated
        return 60; // 60 seconds
    }
    
    /**
     * Check if path is exempt from rate limiting
     */
    private boolean isExemptPath(String path) {
        return path.contains("/health") || 
               path.contains("/metrics") || 
               path.contains("/actuator") ||
               path.contains("/swagger") ||
               path.contains("/v3/api-docs");
    }
    
    /**
     * Violation tracker for blacklisting
     */
    private static class ViolationTracker {
        private int violationCount = 0;
        private LocalDateTime firstViolationTime;
        
        public void recordViolation() {
            if (firstViolationTime == null) {
                firstViolationTime = LocalDateTime.now();
            }
            violationCount++;
        }
        
        public int getViolationCount() {
            return violationCount;
        }
        
        public Duration getTimeSinceFirstViolation() {
            if (firstViolationTime == null) {
                return Duration.ZERO;
            }
            return Duration.between(firstViolationTime, LocalDateTime.now());
        }
    }
}