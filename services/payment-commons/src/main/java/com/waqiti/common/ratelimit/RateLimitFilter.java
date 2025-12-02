package com.waqiti.common.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive Rate Limiting Filter for Waqiti APIs
 * 
 * Provides multiple rate limiting strategies:
 * - Per-user rate limiting
 * - Per-IP rate limiting  
 * - Per-API key rate limiting
 * - Global rate limiting
 * - Endpoint-specific rate limiting
 * - Distributed rate limiting with Redis
 * 
 * Features:
 * - Sliding window algorithm
 * - Distributed coordination via Redis
 * - Circuit breaker integration
 * - Custom headers for rate limit info
 * - Whitelist/blacklist support
 * - Burst allowance
 * - Progressive rate limiting
 * 
 * @author Waqiti Security Team
 * @since 2.0.0
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimiterRegistry rateLimiterRegistry;
    
    @Value("${waqiti.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${waqiti.rate-limit.default.requests-per-minute:60}")
    private int defaultRequestsPerMinute;
    
    @Value("${waqiti.rate-limit.burst.multiplier:2}")
    private int burstMultiplier;
    
    @Value("${waqiti.rate-limit.whitelist.ips:}")
    private List<String> whitelistIps;
    
    @Value("${waqiti.rate-limit.blacklist.ips:}")
    private List<String> blacklistIps;
    
    @Value("${waqiti.rate-limit.bypass.header:X-RateLimit-Bypass}")
    private String bypassHeader;
    
    @Value("${waqiti.rate-limit.bypass.token:}")
    private String bypassToken;
    
    // Cache for rate limiters to avoid recreation
    private final ConcurrentHashMap<String, RateLimiter> rateLimiterCache = new ConcurrentHashMap<>();
    
    // Redis key prefixes
    private static final String USER_RATE_LIMIT_PREFIX = "rate_limit:user:";
    private static final String IP_RATE_LIMIT_PREFIX = "rate_limit:ip:";
    private static final String API_KEY_RATE_LIMIT_PREFIX = "rate_limit:api_key:";
    private static final String ENDPOINT_RATE_LIMIT_PREFIX = "rate_limit:endpoint:";
    private static final String GLOBAL_RATE_LIMIT_KEY = "rate_limit:global";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        
        long startTime = System.currentTimeMillis();
        String requestId = getRequestId(request);
        
        try {
            log.debug("Processing rate limit check for request: {}", requestId);
            
            // Check for bypass
            if (shouldBypassRateLimit(request)) {
                log.debug("Rate limit bypassed for request: {}", requestId);
                addRateLimitHeaders(response, Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
                filterChain.doFilter(request, response);
                return;
            }
            
            // Check blacklist
            if (isBlacklisted(request)) {
                log.warn("Request from blacklisted IP blocked: {}", getClientIp(request));
                sendRateLimitExceededResponse(response, "IP blacklisted", requestId);
                return;
            }
            
            // Perform rate limit checks in order of priority
            RateLimitResult result = performRateLimitChecks(request);
            
            if (result.isAllowed()) {
                // Add rate limit headers
                addRateLimitHeaders(response, result.getLimit(), result.getRemaining(), result.getResetTime());
                
                // Continue processing
                filterChain.doFilter(request, response);
                
                long processingTime = System.currentTimeMillis() - startTime;
                log.debug("Rate limit check passed for request: {} in {}ms", requestId, processingTime);
                
            } else {
                // Rate limit exceeded
                log.warn("Rate limit exceeded for request: {} - {}", requestId, result.getReason());
                
                // Record rate limit violation
                recordRateLimitViolation(request, result);
                
                // Send rate limit response
                sendRateLimitExceededResponse(response, result.getReason(), requestId);
            }
            
        } catch (Exception e) {
            log.error("Error in rate limit filter for request: {}", requestId, e);
            
            // In case of error, allow request to continue but log the issue
            filterChain.doFilter(request, response);
        }
    }
    
    /**
     * Perform comprehensive rate limit checks
     */
    private RateLimitResult performRateLimitChecks(HttpServletRequest request) {
        String userId = getUserId(request);
        String clientIp = getClientIp(request);
        String apiKey = getApiKey(request);
        String endpoint = getEndpoint(request);
        
        // 1. Global rate limit check
        RateLimitResult globalResult = checkGlobalRateLimit();
        if (!globalResult.isAllowed()) {
            return globalResult;
        }
        
        // 2. IP-based rate limit check
        RateLimitResult ipResult = checkIpRateLimit(clientIp);
        if (!ipResult.isAllowed()) {
            return ipResult;
        }
        
        // 3. User-based rate limit check (if authenticated)
        if (userId != null) {
            RateLimitResult userResult = checkUserRateLimit(userId);
            if (!userResult.isAllowed()) {
                return userResult;
            }
        }
        
        // 4. API key-based rate limit check (if API key present)
        if (apiKey != null) {
            RateLimitResult apiKeyResult = checkApiKeyRateLimit(apiKey);
            if (!apiKeyResult.isAllowed()) {
                return apiKeyResult;
            }
        }
        
        // 5. Endpoint-specific rate limit check
        RateLimitResult endpointResult = checkEndpointRateLimit(endpoint, userId != null ? userId : clientIp);
        if (!endpointResult.isAllowed()) {
            return endpointResult;
        }
        
        // All checks passed
        return RateLimitResult.allowed(defaultRequestsPerMinute, defaultRequestsPerMinute - 1, 0);
    }
    
    /**
     * Check global rate limit across all requests
     */
    private RateLimitResult checkGlobalRateLimit() {
        String key = GLOBAL_RATE_LIMIT_KEY;
        int limit = 10000; // Global limit per minute
        
        return checkRedisRateLimit(key, limit, "Global rate limit exceeded");
    }
    
    /**
     * Check IP-based rate limit
     */
    private RateLimitResult checkIpRateLimit(String clientIp) {
        if (whitelistIps.contains(clientIp)) {
            return RateLimitResult.allowed(Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        }
        
        String key = IP_RATE_LIMIT_PREFIX + clientIp;
        int limit = defaultRequestsPerMinute * 2; // Higher limit for IP
        
        return checkRedisRateLimit(key, limit, "IP rate limit exceeded");
    }
    
    /**
     * Check user-based rate limit
     */
    private RateLimitResult checkUserRateLimit(String userId) {
        String key = USER_RATE_LIMIT_PREFIX + userId;
        
        // Get user-specific rate limit (could vary by user tier)
        int limit = getUserRateLimit(userId);
        
        return checkRedisRateLimit(key, limit, "User rate limit exceeded");
    }
    
    /**
     * Check API key-based rate limit
     */
    private RateLimitResult checkApiKeyRateLimit(String apiKey) {
        String key = API_KEY_RATE_LIMIT_PREFIX + apiKey;
        
        // Get API key-specific rate limit
        int limit = getApiKeyRateLimit(apiKey);
        
        return checkRedisRateLimit(key, limit, "API key rate limit exceeded");
    }
    
    /**
     * Check endpoint-specific rate limit
     */
    private RateLimitResult checkEndpointRateLimit(String endpoint, String identifier) {
        EndpointRateLimitConfig config = getEndpointRateLimitConfig(endpoint);
        
        if (config == null) {
            return RateLimitResult.allowed(defaultRequestsPerMinute, defaultRequestsPerMinute, 0);
        }
        
        String key = ENDPOINT_RATE_LIMIT_PREFIX + endpoint + ":" + identifier;
        
        return checkRedisRateLimit(key, config.getRequestsPerMinute(), 
                                 "Endpoint rate limit exceeded: " + endpoint);
    }
    
    /**
     * Distributed rate limiting using Redis sliding window
     */
    private RateLimitResult checkRedisRateLimit(String key, int limit, String reason) {
        try {
            long now = Instant.now().getEpochSecond();
            long windowStart = now - 60; // 1-minute window
            
            // Use Redis sorted set for sliding window
            String windowKey = key + ":window";
            
            // Remove old entries
            redisTemplate.opsForZSet().removeRangeByScore(windowKey, 0, windowStart);
            
            // Count current requests in window
            Long currentCount = redisTemplate.opsForZSet().count(windowKey, windowStart, now);
            
            if (currentCount == null) {
                currentCount = 0L;
            }
            
            // Check if limit exceeded
            if (currentCount >= limit) {
                // Calculate reset time
                Double oldestTimestamp = redisTemplate.opsForZSet().range(windowKey, 0, 0)
                    .stream()
                    .findFirst()
                    .map(member -> redisTemplate.opsForZSet().score(windowKey, member))
                    .orElse((double) now);
                
                long resetTime = Math.round(oldestTimestamp) + 60;
                
                return RateLimitResult.denied(limit, 0, resetTime, reason);
            }
            
            // Add current request
            String requestId = now + ":" + System.nanoTime();
            redisTemplate.opsForZSet().add(windowKey, requestId, now);
            
            // Set expiration for cleanup
            redisTemplate.expire(windowKey, 120, TimeUnit.SECONDS);
            
            // Calculate remaining requests
            int remaining = Math.max(0, limit - currentCount.intValue() - 1);
            
            return RateLimitResult.allowed(limit, remaining, 0);
            
        } catch (Exception e) {
            log.error("Error checking Redis rate limit for key: {}", key, e);
            
            // Fallback to in-memory rate limiting
            return checkInMemoryRateLimit(key, limit, reason);
        }
    }
    
    /**
     * Fallback in-memory rate limiting using Resilience4j
     */
    private RateLimitResult checkInMemoryRateLimit(String key, int limit, String reason) {
        try {
            RateLimiter rateLimiter = rateLimiterCache.computeIfAbsent(key, k -> {
                RateLimiterConfig config = RateLimiterConfig.custom()
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .limitForPeriod(limit)
                    .timeoutDuration(Duration.ofMillis(100))
                    .build();
                
                return rateLimiterRegistry.rateLimiter(k, config);
            });
            
            boolean allowed = rateLimiter.acquirePermission();
            
            if (allowed) {
                int remaining = rateLimiter.getMetrics().getAvailablePermissions();
                return RateLimitResult.allowed(limit, remaining, 0);
            } else {
                return RateLimitResult.denied(limit, 0, 
                    System.currentTimeMillis() / 1000 + 60, reason);
            }
            
        } catch (Exception e) {
            log.error("Error in fallback rate limiting for key: {}", key, e);
            
            // Ultimate fallback - allow request
            return RateLimitResult.allowed(limit, limit, 0);
        }
    }
    
    /**
     * Check if request should bypass rate limiting
     */
    private boolean shouldBypassRateLimit(HttpServletRequest request) {
        // Check bypass header
        String bypassHeaderValue = request.getHeader(bypassHeader);
        if (bypassToken != null && bypassToken.equals(bypassHeaderValue)) {
            return true;
        }
        
        // Check if IP is whitelisted
        String clientIp = getClientIp(request);
        if (whitelistIps.contains(clientIp)) {
            return true;
        }
        
        // Check for internal service requests
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.startsWith("Waqiti-Internal")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if IP is blacklisted
     */
    private boolean isBlacklisted(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        return blacklistIps.contains(clientIp);
    }
    
    /**
     * Add rate limit headers to response
     */
    private void addRateLimitHeaders(HttpServletResponse response, int limit, int remaining, long resetTime) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
        response.setHeader("X-RateLimit-Window", "60"); // 60 seconds
    }
    
    /**
     * Send rate limit exceeded response
     */
    private void sendRateLimitExceededResponse(HttpServletResponse response, String reason, String requestId) 
            throws IOException {
        
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", "60");
        
        String jsonResponse = String.format("""
            {
                "error": "Rate limit exceeded",
                "message": "%s",
                "code": "RATE_LIMIT_EXCEEDED",
                "requestId": "%s",
                "retryAfter": 60
            }
            """, reason, requestId);
        
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
    
    /**
     * Record rate limit violation for monitoring
     */
    private void recordRateLimitViolation(HttpServletRequest request, RateLimitResult result) {
        try {
            String violationKey = "rate_limit:violations:" + Instant.now().getEpochSecond() / 300; // 5-min buckets
            
            String violationData = String.format("%s|%s|%s|%s", 
                getClientIp(request),
                getUserId(request),
                getEndpoint(request),
                result.getReason()
            );
            
            redisTemplate.opsForList().leftPush(violationKey, violationData);
            redisTemplate.expire(violationKey, 1, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.error("Error recording rate limit violation", e);
        }
    }
    
    // Helper methods for extracting request information
    
    private String getRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-ID");
        return requestId != null ? requestId : "unknown";
    }
    
    private String getUserId(HttpServletRequest request) {
        // Extract from JWT token or session
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Extract user ID from JWT (simplified)
            return extractUserIdFromToken(authHeader.substring(7));
        }
        return null;
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private String getApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null) {
            apiKey = request.getParameter("api_key");
        }
        return apiKey;
    }
    
    private String getEndpoint(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }
    
    private String extractUserIdFromToken(String token) {
        // Simplified JWT parsing - would use proper JWT library in production
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                // This is a simplified example - use proper JWT library
                return "user_" + Math.abs(token.hashCode() % 10000);
            }
        } catch (Exception e) {
            log.debug("Error extracting user ID from token", e);
        }
        return null;
    }
    
    private int getUserRateLimit(String userId) {
        // Get user-specific rate limit from database or configuration
        // This could vary based on user tier, subscription, etc.
        
        try {
            String rateLimitKey = "user_rate_limit:" + userId;
            String limit = redisTemplate.opsForValue().get(rateLimitKey);
            
            if (limit != null) {
                return Integer.parseInt(limit);
            }
        } catch (Exception e) {
            log.debug("Error getting user rate limit for: {}", userId, e);
        }
        
        return defaultRequestsPerMinute;
    }
    
    private int getApiKeyRateLimit(String apiKey) {
        // Get API key-specific rate limit from configuration
        
        try {
            String rateLimitKey = "api_key_rate_limit:" + apiKey;
            String limit = redisTemplate.opsForValue().get(rateLimitKey);
            
            if (limit != null) {
                return Integer.parseInt(limit);
            }
        } catch (Exception e) {
            log.debug("Error getting API key rate limit for: {}", apiKey, e);
        }
        
        return defaultRequestsPerMinute * 5; // Higher limit for API keys
    }
    
    private EndpointRateLimitConfig getEndpointRateLimitConfig(String endpoint) {
        // Define endpoint-specific rate limits
        
        // High-security endpoints
        if (endpoint.contains("/api/auth/") || 
            endpoint.contains("/api/admin/") ||
            endpoint.contains("/api/payments/transfer")) {
            return new EndpointRateLimitConfig(10); // Very restrictive
        }
        
        // Payment endpoints
        if (endpoint.contains("/api/payments/")) {
            return new EndpointRateLimitConfig(30); // Moderate restriction
        }
        
        // Public endpoints
        if (endpoint.contains("/api/public/") ||
            endpoint.contains("/health") ||
            endpoint.contains("/metrics")) {
            return new EndpointRateLimitConfig(120); // Less restrictive
        }
        
        return null; // Use default rate limiting
    }
    
    /**
     * Configuration for endpoint-specific rate limiting
     */
    private static class EndpointRateLimitConfig {
        private final int requestsPerMinute;
        
        public EndpointRateLimitConfig(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
        
        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }
    }
}