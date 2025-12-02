package com.waqiti.corebanking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.*;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.grid.jcache.JCacheBucketBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.cache.Cache;
import jakarta.cache.CacheManager;
import jakarta.cache.Caching;
import jakarta.cache.configuration.MutableConfiguration;
import jakarta.cache.spi.CachingProvider;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced Rate Limiting Filter with DDoS Protection
 * Implements multiple rate limiting strategies and DDoS mitigation
 */
@Component
@Order(1)
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // Rate limiting buckets
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> apiBuckets = new ConcurrentHashMap<>();
    
    // DDoS protection
    private final Map<String, AtomicInteger> ipRequestCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> ipBlockedUntil = new ConcurrentHashMap<>();
    private final Set<String> blacklistedIps = ConcurrentHashMap.newKeySet();
    private final Set<String> whitelistedIps = ConcurrentHashMap.newKeySet();
    
    // Distributed rate limiting
    private ProxyManager<String> proxyManager;
    
    // Configuration
    @Value("${rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;
    
    @Value("${rate-limiting.user.requests-per-minute:60}")
    private int userRequestsPerMinute;
    
    @Value("${rate-limiting.user.requests-per-hour:1000}")
    private int userRequestsPerHour;
    
    @Value("${rate-limiting.ip.requests-per-minute:100}")
    private int ipRequestsPerMinute;
    
    @Value("${rate-limiting.ip.requests-per-hour:2000}")
    private int ipRequestsPerHour;
    
    @Value("${rate-limiting.api.global-requests-per-second:1000}")
    private int globalRequestsPerSecond;
    
    @Value("${ddos.protection.enabled:true}")
    private boolean ddosProtectionEnabled;
    
    @Value("${ddos.protection.threshold-per-second:50}")
    private int ddosThresholdPerSecond;
    
    @Value("${ddos.protection.block-duration-minutes:60}")
    private int blockDurationMinutes;

    public RateLimitingFilter(RedisTemplate<String, Object> redisTemplate, 
                             ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        initializeDistributedRateLimiting();
        loadWhitelistAndBlacklist();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        if (!rateLimitingEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String userId = getUserId();
        String apiEndpoint = request.getRequestURI();
        
        try {
            // Check if IP is blacklisted
            if (isBlacklisted(clientIp)) {
                sendRateLimitResponse(response, "IP address is blacklisted", 0);
                return;
            }
            
            // Skip rate limiting for whitelisted IPs
            if (isWhitelisted(clientIp)) {
                filterChain.doFilter(request, response);
                return;
            }
            
            // DDoS protection check
            if (ddosProtectionEnabled && isUnderDDoSAttack(clientIp)) {
                blockIpAddress(clientIp);
                sendRateLimitResponse(response, "Suspicious activity detected", 0);
                return;
            }
            
            // Check if IP is temporarily blocked
            if (isTemporarilyBlocked(clientIp)) {
                long remainingBlockTime = getRemainingBlockTime(clientIp);
                sendRateLimitResponse(response, "Too many requests. IP temporarily blocked", remainingBlockTime);
                return;
            }
            
            // Apply rate limiting strategies
            RateLimitResult result = applyRateLimiting(clientIp, userId, apiEndpoint);
            
            if (!result.isAllowed()) {
                handleRateLimitExceeded(clientIp, userId, apiEndpoint);
                sendRateLimitResponse(response, result.getMessage(), result.getRetryAfter());
                return;
            }
            
            // Add rate limit headers to response
            addRateLimitHeaders(response, result);
            
            // Continue with request
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Error in rate limiting filter", e);
            // In case of error, allow the request but log it
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Apply multiple rate limiting strategies
     */
    private RateLimitResult applyRateLimiting(String clientIp, String userId, String apiEndpoint) {
        // 1. Global API rate limiting
        RateLimitResult globalResult = checkGlobalRateLimit();
        if (!globalResult.isAllowed()) {
            return globalResult;
        }
        
        // 2. Per-IP rate limiting
        RateLimitResult ipResult = checkIpRateLimit(clientIp);
        if (!ipResult.isAllowed()) {
            return ipResult;
        }
        
        // 3. Per-user rate limiting (if authenticated)
        if (userId != null) {
            RateLimitResult userResult = checkUserRateLimit(userId);
            if (!userResult.isAllowed()) {
                return userResult;
            }
        }
        
        // 4. Per-endpoint rate limiting
        RateLimitResult endpointResult = checkEndpointRateLimit(apiEndpoint);
        if (!endpointResult.isAllowed()) {
            return endpointResult;
        }
        
        // 5. Adaptive rate limiting based on system load
        RateLimitResult adaptiveResult = checkAdaptiveRateLimit();
        if (!adaptiveResult.isAllowed()) {
            return adaptiveResult;
        }
        
        // All checks passed
        return RateLimitResult.allowed(globalResult.getRemainingRequests());
    }

    /**
     * Check global API rate limit
     */
    private RateLimitResult checkGlobalRateLimit() {
        Bucket bucket = apiBuckets.computeIfAbsent("global", k -> createGlobalBucket());
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
            return RateLimitResult.allowed(probe.getRemainingTokens());
        } else {
            long waitTime = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            return RateLimitResult.denied("Global rate limit exceeded", waitTime);
        }
    }

    /**
     * Check per-IP rate limit
     */
    private RateLimitResult checkIpRateLimit(String clientIp) {
        Bucket bucket = ipBuckets.computeIfAbsent(clientIp, k -> createIpBucket());
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
            return RateLimitResult.allowed(probe.getRemainingTokens());
        } else {
            long waitTime = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            
            // Track repeated violations
            incrementIpViolations(clientIp);
            
            return RateLimitResult.denied("IP rate limit exceeded", waitTime);
        }
    }

    /**
     * Check per-user rate limit
     */
    private RateLimitResult checkUserRateLimit(String userId) {
        // Use distributed rate limiting for user buckets
        Bucket bucket = getDistributedBucket(userId);
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
            return RateLimitResult.allowed(probe.getRemainingTokens());
        } else {
            long waitTime = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            return RateLimitResult.denied("User rate limit exceeded", waitTime);
        }
    }

    /**
     * Check per-endpoint rate limit
     */
    private RateLimitResult checkEndpointRateLimit(String endpoint) {
        // Different limits for different endpoints
        int requestsPerMinute = getEndpointRateLimit(endpoint);
        
        Bucket bucket = apiBuckets.computeIfAbsent(endpoint, k -> 
            Bucket.builder()
                .addLimit(Bandwidth.classic(requestsPerMinute, Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))))
                .build()
        );
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
            return RateLimitResult.allowed(probe.getRemainingTokens());
        } else {
            long waitTime = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            return RateLimitResult.denied("Endpoint rate limit exceeded", waitTime);
        }
    }

    /**
     * Adaptive rate limiting based on system load
     */
    private RateLimitResult checkAdaptiveRateLimit() {
        double systemLoad = getSystemLoad();
        
        if (systemLoad > 0.8) {
            // System under high load - apply stricter limits
            int reducedLimit = (int)(globalRequestsPerSecond * 0.5);
            Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.classic(reducedLimit, Refill.intervally(reducedLimit, Duration.ofSeconds(1))))
                .build();
            
            if (!bucket.tryConsume(1)) {
                return RateLimitResult.denied("System under high load", 5);
            }
        }
        
        return RateLimitResult.allowed(100);
    }

    /**
     * DDoS attack detection
     */
    private boolean isUnderDDoSAttack(String clientIp) {
        String key = "ddos:" + clientIp;
        AtomicInteger counter = ipRequestCounts.computeIfAbsent(key, k -> new AtomicInteger(0));
        
        int count = counter.incrementAndGet();
        
        // Reset counter every second
        scheduleCounterReset(key, counter);
        
        if (count > ddosThresholdPerSecond) {
            log.warn("Potential DDoS attack detected from IP: {} ({} requests/second)", clientIp, count);
            return true;
        }
        
        // Check for distributed DDoS patterns
        if (detectDistributedAttackPattern()) {
            log.warn("Distributed DDoS pattern detected");
            return true;
        }
        
        return false;
    }

    /**
     * Detect distributed DDoS attack patterns
     */
    private boolean detectDistributedAttackPattern() {
        // Count unique IPs in the last second
        long uniqueIps = ipRequestCounts.keySet().stream()
            .filter(k -> k.startsWith("ddos:"))
            .count();
        
        // If too many unique IPs with similar request patterns
        if (uniqueIps > 100) {
            long totalRequests = ipRequestCounts.entrySet().stream()
                .filter(e -> e.getKey().startsWith("ddos:"))
                .mapToInt(e -> e.getValue().get())
                .sum();
            
            double averageRequestsPerIp = (double) totalRequests / uniqueIps;
            
            // If average is suspiciously uniform (botnet characteristic)
            if (averageRequestsPerIp > 10 && averageRequestsPerIp < 15) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Block IP address temporarily
     */
    private void blockIpAddress(String clientIp) {
        long blockUntil = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(blockDurationMinutes);
        ipBlockedUntil.put(clientIp, new AtomicLong(blockUntil));
        
        // Persist to Redis for distributed blocking
        redisTemplate.opsForValue().set(
            "blocked:ip:" + clientIp, 
            blockUntil, 
            blockDurationMinutes, 
            TimeUnit.MINUTES
        );
        
        log.warn("IP {} blocked until {}", clientIp, new Date(blockUntil));
    }

    /**
     * Create rate limit buckets
     */
    private Bucket createGlobalBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(globalRequestsPerSecond, 
                Refill.intervally(globalRequestsPerSecond, Duration.ofSeconds(1))))
            .build();
    }

    private Bucket createIpBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(ipRequestsPerMinute, 
                Refill.intervally(ipRequestsPerMinute, Duration.ofMinutes(1))))
            .addLimit(Bandwidth.classic(ipRequestsPerHour,
                Refill.intervally(ipRequestsPerHour, Duration.ofHours(1))))
            .build();
    }

    private Bucket createUserBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(userRequestsPerMinute,
                Refill.intervally(userRequestsPerMinute, Duration.ofMinutes(1))))
            .addLimit(Bandwidth.classic(userRequestsPerHour,
                Refill.intervally(userRequestsPerHour, Duration.ofHours(1))))
            .build();
    }

    /**
     * Get distributed bucket for user (Redis-backed)
     */
    private Bucket getDistributedBucket(String userId) {
        if (proxyManager == null) {
            // Fallback to local bucket if distributed not available
            return userBuckets.computeIfAbsent(userId, k -> createUserBucket());
        }
        
        BucketConfiguration configuration = BucketConfiguration.builder()
            .addLimit(Bandwidth.classic(userRequestsPerMinute,
                Refill.intervally(userRequestsPerMinute, Duration.ofMinutes(1))))
            .addLimit(Bandwidth.classic(userRequestsPerHour,
                Refill.intervally(userRequestsPerHour, Duration.ofHours(1))))
            .build();
        
        return proxyManager.builder().build(userId, () -> configuration);
    }

    /**
     * Initialize distributed rate limiting with Redis
     */
    private void initializeDistributedRateLimiting() {
        try {
            CachingProvider cachingProvider = Caching.getCachingProvider();
            CacheManager cacheManager = cachingProvider.getCacheManager();
            
            MutableConfiguration<String, byte[]> config = new MutableConfiguration<>();
            config.setExpiryPolicyFactory(() -> new javax.cache.expiry.CreatedExpiryPolicy(
                javax.cache.expiry.Duration.ONE_HOUR));
            
            Cache<String, byte[]> cache = cacheManager.createCache("rate-limit-buckets", config);
            
            this.proxyManager = JCacheBucketBuilder.builder()
                .build(cache);
                
            log.info("Distributed rate limiting initialized with Redis");
        } catch (Exception e) {
            log.warn("Failed to initialize distributed rate limiting, falling back to local", e);
        }
    }

    /**
     * Get endpoint-specific rate limit
     */
    private int getEndpointRateLimit(String endpoint) {
        // Critical endpoints have lower limits
        if (endpoint.contains("/transfer") || endpoint.contains("/payment")) {
            return 10; // 10 requests per minute for payment endpoints
        } else if (endpoint.contains("/account/create")) {
            return 5; // 5 requests per minute for account creation
        } else if (endpoint.contains("/balance")) {
            return 30; // 30 requests per minute for balance checks
        }
        
        return 60; // Default: 60 requests per minute
    }

    /**
     * Track IP violations for progressive blocking
     */
    private void incrementIpViolations(String clientIp) {
        String violationKey = "violations:" + clientIp;
        Long violations = (Long) redisTemplate.opsForValue().increment(violationKey, 1);
        
        if (violations != null && violations > 10) {
            // Block IP after 10 violations
            blockIpAddress(clientIp);
        }
        
        // Set expiry for violation counter
        redisTemplate.expire(violationKey, Duration.ofHours(1));
    }

    /**
     * Send rate limit response
     */
    private void sendRateLimitResponse(HttpServletResponse response, String message, long retryAfter) 
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        if (retryAfter > 0) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
        }
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "RATE_LIMIT_EXCEEDED");
        errorResponse.put("message", message);
        errorResponse.put("timestamp", LocalDateTime.now());
        
        if (retryAfter > 0) {
            errorResponse.put("retryAfter", retryAfter);
        }
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Add rate limit headers to response
     */
    private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingRequests()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetTime()));
    }

    /**
     * Handle rate limit exceeded
     */
    private void handleRateLimitExceeded(String clientIp, String userId, String endpoint) {
        // Log rate limit violation
        log.warn("Rate limit exceeded - IP: {}, User: {}, Endpoint: {}", clientIp, userId, endpoint);
        
        // Send alert for suspicious patterns
        if (userId != null) {
            checkForAbusivePatterns(userId, clientIp);
        }
        
        // Update metrics
        updateRateLimitMetrics(clientIp, userId, endpoint);
    }

    /**
     * Check for abusive patterns
     */
    private void checkForAbusivePatterns(String userId, String clientIp) {
        String patternKey = "abuse:pattern:" + userId;
        Long violations = (Long) redisTemplate.opsForValue().increment(patternKey, 1);
        
        if (violations != null && violations > 50) {
            // User showing abusive pattern - potential account compromise
            log.error("Potential account compromise detected for user: {}", userId);
            // Trigger security alert
            sendSecurityAlert(userId, clientIp, "Excessive rate limit violations");
        }
        
        redisTemplate.expire(patternKey, Duration.ofHours(24));
    }

    /**
     * Helper methods
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "ANONYMOUS"; // Return explicit anonymous identifier for unauthenticated users
    }

    private boolean isBlacklisted(String ip) {
        return blacklistedIps.contains(ip) || 
               redisTemplate.hasKey("blacklist:ip:" + ip);
    }

    private boolean isWhitelisted(String ip) {
        return whitelistedIps.contains(ip) ||
               redisTemplate.hasKey("whitelist:ip:" + ip);
    }

    private boolean isTemporarilyBlocked(String ip) {
        AtomicLong blockUntil = ipBlockedUntil.get(ip);
        if (blockUntil != null) {
            if (System.currentTimeMillis() < blockUntil.get()) {
                return true;
            } else {
                ipBlockedUntil.remove(ip);
            }
        }
        
        // Check Redis for distributed blocking
        Long redisBlockUntil = (Long) redisTemplate.opsForValue().get("blocked:ip:" + ip);
        return redisBlockUntil != null && System.currentTimeMillis() < redisBlockUntil;
    }

    private long getRemainingBlockTime(String ip) {
        AtomicLong blockUntil = ipBlockedUntil.get(ip);
        if (blockUntil != null) {
            return TimeUnit.MILLISECONDS.toSeconds(blockUntil.get() - System.currentTimeMillis());
        }
        return 0;
    }

    private void scheduleCounterReset(String key, AtomicInteger counter) {
        // Reset counter after 1 second
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                counter.set(0);
            }
        }, 1000);
    }

    private double getSystemLoad() {
        // Get system load average
        return ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    }

    private void loadWhitelistAndBlacklist() {
        // Load from configuration or database
        // Example IPs - would be loaded from config in production
        whitelistedIps.add("127.0.0.1");
        whitelistedIps.add("::1");
    }

    private void sendSecurityAlert(String userId, String clientIp, String reason) {
        // Send security alert via notification service
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "SECURITY_ALERT");
        alert.put("userId", userId);
        alert.put("clientIp", clientIp);
        alert.put("reason", reason);
        alert.put("timestamp", LocalDateTime.now());
        
        // Send to security monitoring system
        kafkaTemplate.send("security-alerts", alert);
    }

    private void updateRateLimitMetrics(String clientIp, String userId, String endpoint) {
        // Update metrics for monitoring
        // meterRegistry.counter("rate.limit.exceeded", "ip", clientIp, "user", userId, "endpoint", endpoint).increment();
    }

    /**
     * Rate limit result
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class RateLimitResult {
        private boolean allowed;
        private String message;
        private long remainingRequests;
        private long retryAfter;
        private long limit;
        private long resetTime;
        
        public static RateLimitResult allowed(long remaining) {
            return new RateLimitResult(true, null, remaining, 0, 100, System.currentTimeMillis() + 60000);
        }
        
        public static RateLimitResult denied(String message, long retryAfter) {
            return new RateLimitResult(false, message, 0, retryAfter, 100, System.currentTimeMillis() + retryAfter * 1000);
        }
    }
}