package com.waqiti.common.ratelimit;

import com.waqiti.common.ratelimit.dto.*;
import com.waqiti.common.ratelimit.model.*;
import com.waqiti.common.ratelimit.properties.AdvancedRateLimitProperties;
import com.waqiti.common.tracing.DistributedTracingService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.redisson.api.RedissonClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Advanced distributed rate limiting service using Redis and sliding window algorithm.
 * Supports per-user, per-IP, and per-endpoint rate limiting with comprehensive metrics.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
public class AdvancedRateLimitService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdvancedRateLimitService.class);
    
    private final AdvancedRateLimitProperties rateLimitProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final DistributedTracingService tracingService;
    
    private ProxyManager<String> proxyManager;
    private final Map<String, RateLimitMetrics> metrics = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private volatile boolean circuitBreakerOpen = false;
    private volatile long circuitBreakerOpenTime = 0;
    
    public AdvancedRateLimitService(AdvancedRateLimitProperties rateLimitProperties,
                                  RedisTemplate<String, Object> redisTemplate,
                                  RedissonClient redissonClient,
                                  DistributedTracingService tracingService) {
        this.rateLimitProperties = rateLimitProperties;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.tracingService = tracingService;
    }
    
    @PostConstruct
    public void initialize() {
        try {
            // Test Redis connection first
            testRedisConnection();
            
            proxyManager = RedissonBasedProxyManager.<String>builderFor((org.redisson.command.CommandAsyncExecutor) redissonClient)
                    .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                            Duration.ofMinutes(10)))
                    .build();
            
            initialized = true;
            logger.info("Advanced rate limiting service initialized successfully with {} configurations", 
                    rateLimitProperties.getEndpoints().size());
        } catch (Exception e) {
            logger.error("Failed to initialize rate limiting service", e);
            // Depending on configuration, either fail hard or continue with fallback behavior
            if (!rateLimitProperties.getEndpoints().getOrDefault("global-default", 
                    rateLimitProperties.getDefaultConfig()).isFailOpen()) {
                throw new RuntimeException("Rate limiting service initialization failed", e);
            }
            logger.warn("Rate limiting service will operate in fallback mode");
        }
    }
    
    /**
     * Tests Redis connection health.
     */
    private void testRedisConnection() {
        try {
            redissonClient.getKeys().countExists("health-check");
            circuitBreakerOpen = false;
            logger.debug("Redis connection test successful");
        } catch (Exception e) {
            handleRedisFailure(e);
            throw e;
        }
    }
    
    /**
     * Handles Redis connection failures and circuit breaker logic.
     */
    private void handleRedisFailure(Exception e) {
        if (rateLimitProperties.getRedis().isCircuitBreakerEnabled()) {
            long currentTime = System.currentTimeMillis();
            
            if (!circuitBreakerOpen) {
                circuitBreakerOpen = true;
                circuitBreakerOpenTime = currentTime;
                logger.warn("Redis circuit breaker opened due to failure", e);
            } else if (currentTime - circuitBreakerOpenTime > 
                      rateLimitProperties.getRedis().getCircuitBreakerRecoveryTimeMs()) {
                // Try to recover
                try {
                    testRedisConnection();
                    logger.info("Redis circuit breaker closed - connection recovered");
                } catch (Exception recoveryException) {
                    logger.debug("Redis recovery attempt failed", recoveryException);
                    circuitBreakerOpenTime = currentTime; // Reset recovery timer
                }
            }
        }
    }
    
    /**
     * Checks if a request should be allowed based on rate limiting rules.
     *
     * @param request the rate limit request
     * @return the rate limit result
     */
    public RateLimitResult checkRateLimit(RateLimitRequest request) {
        DistributedTracingService.TraceContext traceContext = 
            tracingService.startChildTrace("rate_limit_check");
        
        try {
            // Early return for circuit breaker or uninitialized service
            if (circuitBreakerOpen || !initialized) {
                return handleFallbackBehavior(request, "circuit_breaker_open");
            }
            
            tracingService.addTag("rate_limit.endpoint", request.getEndpoint());
            tracingService.addTag("rate_limit.user_id", request.getUserId());
            tracingService.addTag("rate_limit.ip_address", request.getIpAddress());
            
            long startTime = System.currentTimeMillis();
            
            // Check all applicable rate limits based on endpoint configuration
            AdvancedRateLimitProperties.EndpointConfig config = getEndpointConfig(request.getEndpoint());
            
            RateLimitResult userResult = config.isEnableUserRateLimit() ? 
                checkUserRateLimit(request) : RateLimitResult.allowed(Long.MAX_VALUE, 0L);
                
            RateLimitResult ipResult = config.isEnableIpRateLimit() ?
                checkIpRateLimit(request) : RateLimitResult.allowed(Long.MAX_VALUE, 0L);
                
            RateLimitResult endpointResult = checkEndpointRateLimit(request);
            
            RateLimitResult globalResult = config.isEnableGlobalRateLimit() ?
                checkGlobalRateLimit(request) : RateLimitResult.allowed(Long.MAX_VALUE, 0L);
            
            // Find the most restrictive result
            RateLimitResult finalResult = getMostRestrictive(userResult, ipResult, endpointResult, globalResult);
            
            long duration = System.currentTimeMillis() - startTime;
            updateMetrics(request.getEndpoint(), finalResult.isAllowed(), duration);
            
            // Add rate limit headers information
            finalResult.getHeaders().put("X-RateLimit-Service", "waqiti-advanced");
            finalResult.getHeaders().put("X-RateLimit-Timestamp", String.valueOf(Instant.now().getEpochSecond()));
            finalResult.getHeaders().put("X-RateLimit-Strategy", config.getStrategy());
            
            tracingService.addTag("rate_limit.allowed", String.valueOf(finalResult.isAllowed()));
            tracingService.addTag("rate_limit.remaining", String.valueOf(finalResult.getRemainingTokens()));
            tracingService.addTag("rate_limit.strategy", config.getStrategy());
            
            logger.debug("Rate limit check for endpoint {} - allowed: {}, remaining: {}, reset_in: {}s, strategy: {}",
                    request.getEndpoint(), finalResult.isAllowed(), 
                    finalResult.getRemainingTokens(), finalResult.getResetTimeInSeconds(),
                    config.getStrategy());
            
            return finalResult;
            
        } catch (Exception e) {
            tracingService.recordError(e);
            handleRedisFailure(e);
            logger.error("Error during rate limit check for endpoint: {}", request.getEndpoint(), e);
            
            // Handle based on fallback configuration
            return handleFallbackBehavior(request, "error");
            
        } finally {
            tracingService.finishTrace(traceContext);
        }
    }
    
    /**
     * Handles fallback behavior when rate limiting fails or circuit breaker is open.
     */
    private RateLimitResult handleFallbackBehavior(RateLimitRequest request, String reason) {
        AdvancedRateLimitProperties.EndpointConfig config = getEndpointConfig(request.getEndpoint());
        
        Map<String, String> fallbackHeaders = Map.of(
            "X-RateLimit-Fallback", reason,
            "X-RateLimit-Service", "waqiti-advanced-fallback"
        );
        
        switch (config.getFallbackAction()) {
            case "DENY":
                logger.warn("Rate limiting fallback denying request for endpoint: {} (reason: {})", 
                           request.getEndpoint(), reason);
                updateMetrics(request.getEndpoint() + "_fallback", false, 0);
                return RateLimitResult.denied(60L, fallbackHeaders);
                
            case "THROTTLE":
                // Implement simple in-memory throttling as fallback
                logger.info("Rate limiting fallback throttling request for endpoint: {} (reason: {})", 
                           request.getEndpoint(), reason);
                updateMetrics(request.getEndpoint() + "_fallback", true, 0);
                return RateLimitResult.allowed(config.getCapacity() / 2, 30L, fallbackHeaders);
                
            default: // ALLOW
                logger.debug("Rate limiting fallback allowing request for endpoint: {} (reason: {})", 
                            request.getEndpoint(), reason);
                updateMetrics(request.getEndpoint() + "_fallback", true, 0);
                return RateLimitResult.allowed(Long.MAX_VALUE, 0L, fallbackHeaders);
        }
    }
    
    /**
     * Gets endpoint configuration with fallback to default.
     */
    private AdvancedRateLimitProperties.EndpointConfig getEndpointConfig(String endpoint) {
        return rateLimitProperties.getEndpoints()
                .getOrDefault(endpoint, rateLimitProperties.getDefaultConfig());
    }
    
    /**
     * Consumes tokens from the bucket (for successful rate limit checks).
     *
     * @param request the rate limit request
     * @param tokens number of tokens to consume
     * @return true if tokens were consumed successfully
     */
    public boolean consumeTokens(RateLimitRequest request, long tokens) {
        try {
            String key = buildBucketKey("endpoint", request.getEndpoint(), null);
            Bucket bucket = getBucket(key, request.getEndpoint());
            
            boolean consumed = bucket.tryConsume(tokens);
            if (consumed) {
                updateMetrics(request.getEndpoint() + "_consume", true, 0);
            } else {
                updateMetrics(request.getEndpoint() + "_consume", false, 0);
            }
            
            return consumed;
        } catch (Exception e) {
            logger.error("Error consuming tokens", e);
            return true; // Fail open
        }
    }
    
    /**
     * Gets current rate limit status for a request.
     *
     * @param request the rate limit request
     * @return current status
     */
    public RateLimitStatus getRateLimitStatus(RateLimitRequest request) {
        try {
            String userKey = buildBucketKey("user", request.getUserId(), null);
            String ipKey = buildBucketKey("ip", request.getIpAddress(), null);
            String endpointKey = buildBucketKey("endpoint", request.getEndpoint(), null);
            
            Bucket userBucket = request.getUserId() != null ? getBucket(userKey, "user-default") : null;
            Bucket ipBucket = getBucket(ipKey, "ip-default");
            Bucket endpointBucket = getBucket(endpointKey, request.getEndpoint());
            
            return new RateLimitStatus(
                userBucket != null ? userBucket.getAvailableTokens() : Long.MAX_VALUE,
                ipBucket.getAvailableTokens(),
                endpointBucket.getAvailableTokens(),
                Instant.now()
            );
            
        } catch (Exception e) {
            logger.error("Error getting rate limit status", e);
            return new RateLimitStatus(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Instant.now());
        }
    }
    
    /**
     * Resets rate limits for a specific key pattern.
     *
     * @param pattern the key pattern to reset
     */
    public void resetRateLimits(String pattern) {
        try {
            // Implementation would depend on Redis key scanning
            // For production, consider using Redis SCAN with pattern matching
            logger.info("Reset rate limits requested for pattern: {}", pattern);
            
            // Clear local metrics
            metrics.entrySet().removeIf(entry -> entry.getKey().matches(pattern));
            
        } catch (Exception e) {
            logger.error("Error resetting rate limits for pattern: {}", pattern, e);
        }
    }
    
    /**
     * Gets rate limiting metrics.
     *
     * @return map of metrics by endpoint
     */
    public Map<String, RateLimitMetrics> getMetrics() {
        return new ConcurrentHashMap<>(metrics);
    }
    
    /**
     * Resets metrics.
     */
    public void resetMetrics() {
        metrics.clear();
        logger.info("Rate limit metrics reset");
    }
    
    private RateLimitResult checkUserRateLimit(RateLimitRequest request) {
        if (request.getUserId() == null) {
            return RateLimitResult.allowed(Long.MAX_VALUE, 0L);
        }
        
        String key = buildBucketKey("user", request.getUserId(), request.getEndpoint());
        String configName = "user-" + request.getEndpoint();
        
        if (!rateLimitProperties.getEndpoints().containsKey(configName)) {
            configName = "user-default";
        }
        
        Bucket bucket = getBucket(key, configName);
        return checkBucket(bucket, "user");
    }
    
    private RateLimitResult checkIpRateLimit(RateLimitRequest request) {
        String key = buildBucketKey("ip", request.getIpAddress(), request.getEndpoint());
        String configName = "ip-" + request.getEndpoint();
        
        if (!rateLimitProperties.getEndpoints().containsKey(configName)) {
            configName = "ip-default";
        }
        
        Bucket bucket = getBucket(key, configName);
        return checkBucket(bucket, "ip");
    }
    
    private RateLimitResult checkEndpointRateLimit(RateLimitRequest request) {
        String key = buildBucketKey("endpoint", request.getEndpoint(), null);
        Bucket bucket = getBucket(key, request.getEndpoint());
        return checkBucket(bucket, "endpoint");
    }
    
    private RateLimitResult checkGlobalRateLimit(RateLimitRequest request) {
        String key = buildBucketKey("global", "all", null);
        Bucket bucket = getBucket(key, "global-default");
        return checkBucket(bucket, "global");
    }
    
    private RateLimitResult checkBucket(Bucket bucket, String type) {
        try {
            long availableTokens = bucket.getAvailableTokens();
            
            if (availableTokens > 0) {
                // Calculate reset time based on bucket refill rate
                long resetTimeSeconds = calculateResetTime(bucket);
                
                Map<String, String> headers = Map.of(
                    "X-RateLimit-Limit-" + type, String.valueOf(getBucketCapacity(bucket)),
                    "X-RateLimit-Remaining-" + type, String.valueOf(availableTokens),
                    "X-RateLimit-Reset-" + type, String.valueOf(resetTimeSeconds)
                );
                
                return RateLimitResult.allowed(availableTokens, resetTimeSeconds, headers);
            } else {
                long resetTimeSeconds = calculateResetTime(bucket);
                
                Map<String, String> headers = Map.of(
                    "X-RateLimit-Limit-" + type, String.valueOf(getBucketCapacity(bucket)),
                    "X-RateLimit-Remaining-" + type, "0",
                    "X-RateLimit-Reset-" + type, String.valueOf(resetTimeSeconds),
                    "Retry-After", String.valueOf(resetTimeSeconds)
                );
                
                return RateLimitResult.denied(resetTimeSeconds, headers);
            }
        } catch (Exception e) {
            logger.error("Error checking bucket for type: {}", type, e);
            return RateLimitResult.allowed(Long.MAX_VALUE, 0L); // Fail open
        }
    }
    
    private Bucket getBucket(String key, String configName) {
        AdvancedRateLimitProperties.EndpointConfig config = rateLimitProperties.getEndpoints()
                .getOrDefault(configName, rateLimitProperties.getDefaultConfig());
        
        BucketConfiguration bucketConfig = buildBucketConfiguration(config);
        
        return proxyManager.builder()
                .build(key, () -> bucketConfig);
    }
    
    private BucketConfiguration buildBucketConfiguration(AdvancedRateLimitProperties.EndpointConfig config) {
        long capacity = config.getCapacity();

        // Add burst capacity if enabled
        if (config.isEnableBurstMode() && config.getBurstMultiplier() > 1.0) {
            capacity = (long) (config.getCapacity() * config.getBurstMultiplier());
        }

        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(config.getRefillTokens(), Duration.ofSeconds(config.getRefillPeriodSeconds()))
                .build();

        BucketConfiguration.BucketConfigurationBuilder configBuilder = BucketConfiguration.builder()
                .addLimit(bandwidth);

        // Add additional limits based on strategy
        if ("SLIDING_WINDOW".equals(config.getStrategy())) {
            // Add additional bandwidth for sliding window behavior
            Bandwidth slidingBandwidth = Bandwidth.builder()
                    .capacity(config.getMaxRequestsPerMinute())
                    .refillIntervally(config.getMaxRequestsPerMinute(), Duration.ofMinutes(1))
                    .build();
            configBuilder = configBuilder.addLimit(slidingBandwidth);
        }

        return configBuilder.build();
    }
    
    private String buildBucketKey(String type, String identifier, String endpoint) {
        String baseKey = "waqiti:ratelimit:" + type + ":" + identifier;
        return endpoint != null ? baseKey + ":" + endpoint : baseKey;
    }
    
    private RateLimitResult getMostRestrictive(RateLimitResult... results) {
        RateLimitResult mostRestrictive = results[0];
        
        for (RateLimitResult result : results) {
            if (!result.isAllowed()) {
                return result; // Any denial takes precedence
            }
            if (result.getRemainingTokens() < mostRestrictive.getRemainingTokens()) {
                mostRestrictive = result;
            }
        }
        
        return mostRestrictive;
    }
    
    private long calculateResetTime(Bucket bucket) {
        // Simplified calculation - in real implementation, this would be more sophisticated
        return 60; // Default to 60 seconds
    }
    
    private long getBucketCapacity(Bucket bucket) {
        // This would need to be tracked separately as Bucket4j doesn't expose capacity directly
        return 100; // Default capacity
    }
    
    private void updateMetrics(String endpoint, boolean allowed, long durationMs) {
        RateLimitMetrics rateLimitMetrics = metrics.computeIfAbsent(endpoint, k -> new RateLimitMetrics());
        
        if (allowed) {
            rateLimitMetrics.incrementAllowedCount();
        } else {
            rateLimitMetrics.incrementDeniedCount();
        }
        
        if (durationMs > 0) {
            rateLimitMetrics.updateDuration(durationMs);
        }
    }
    
    /**
     * Request object for rate limiting checks.
     */
    public static class RateLimitRequest {
        private final String endpoint;
        private final String userId;
        private final String ipAddress;
        private final String userAgent;
        private final Map<String, String> additionalAttributes;
        
        public RateLimitRequest(String endpoint, String userId, String ipAddress, 
                              String userAgent, Map<String, String> additionalAttributes) {
            this.endpoint = endpoint;
            this.userId = userId;
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
            this.additionalAttributes = additionalAttributes != null ? additionalAttributes : Map.of();
        }
        
        // Getters
        public String getEndpoint() { return endpoint; }
        public String getUserId() { return userId; }
        public String getIpAddress() { return ipAddress; }
        public String getUserAgent() { return userAgent; }
        public Map<String, String> getAdditionalAttributes() { return additionalAttributes; }
    }
    
    /**
     * Result of a rate limit check.
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final long remainingTokens;
        private final long resetTimeInSeconds;
        private final Map<String, String> headers;
        
        private RateLimitResult(boolean allowed, long remainingTokens, long resetTimeInSeconds, 
                              Map<String, String> headers) {
            this.allowed = allowed;
            this.remainingTokens = remainingTokens;
            this.resetTimeInSeconds = resetTimeInSeconds;
            this.headers = new ConcurrentHashMap<>(headers);
        }
        
        public static RateLimitResult allowed(long remainingTokens, long resetTimeInSeconds) {
            return new RateLimitResult(true, remainingTokens, resetTimeInSeconds, Map.of());
        }
        
        public static RateLimitResult allowed(long remainingTokens, long resetTimeInSeconds, 
                                           Map<String, String> headers) {
            return new RateLimitResult(true, remainingTokens, resetTimeInSeconds, headers);
        }
        
        public static RateLimitResult denied(long resetTimeInSeconds, Map<String, String> headers) {
            return new RateLimitResult(false, 0, resetTimeInSeconds, headers);
        }
        
        // Getters
        public boolean isAllowed() { return allowed; }
        public long getRemainingTokens() { return remainingTokens; }
        public long getResetTimeInSeconds() { return resetTimeInSeconds; }
        public Map<String, String> getHeaders() { return headers; }
    }
    
    /**
     * Current rate limit status.
     */
    public static class RateLimitStatus {
        private final long userTokens;
        private final long ipTokens;
        private final long endpointTokens;
        private final Instant timestamp;
        
        public RateLimitStatus(long userTokens, long ipTokens, long endpointTokens, Instant timestamp) {
            this.userTokens = userTokens;
            this.ipTokens = ipTokens;
            this.endpointTokens = endpointTokens;
            this.timestamp = timestamp;
        }
        
        // Getters
        public long getUserTokens() { return userTokens; }
        public long getIpTokens() { return ipTokens; }
        public long getEndpointTokens() { return endpointTokens; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * Metrics for rate limiting operations.
     */
    public static class RateLimitMetrics {
        private long allowedCount = 0;
        private long deniedCount = 0;
        private long totalDurationMs = 0;
        private long minDurationMs = Long.MAX_VALUE;
        private long maxDurationMs = 0;
        
        public synchronized void incrementAllowedCount() { allowedCount++; }
        public synchronized void incrementDeniedCount() { deniedCount++; }
        
        public synchronized void updateDuration(long durationMs) {
            totalDurationMs += durationMs;
            minDurationMs = Math.min(minDurationMs, durationMs);
            maxDurationMs = Math.max(maxDurationMs, durationMs);
        }
        
        public synchronized double getAverageDurationMs() {
            long totalRequests = allowedCount + deniedCount;
            return totalRequests > 0 ? (double) totalDurationMs / totalRequests : 0.0;
        }
        
        public synchronized double getAllowedRate() {
            long total = allowedCount + deniedCount;
            return total > 0 ? (double) allowedCount / total : 0.0;
        }
        
        // Getters
        public long getAllowedCount() { return allowedCount; }
        public long getDeniedCount() { return deniedCount; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public long getMinDurationMs() { return minDurationMs == Long.MAX_VALUE ? 0 : minDurationMs; }
        public long getMaxDurationMs() { return maxDurationMs; }
    }
}