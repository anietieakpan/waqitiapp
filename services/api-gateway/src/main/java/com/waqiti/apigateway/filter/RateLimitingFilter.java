package com.waqiti.apigateway.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Rate Limiting Filter
 * Implements flexible rate limiting based on user, IP, and API endpoint
 */
@Component("globalRateLimitingFilter")
@Slf4j
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final Counter rateLimitExceededCounter;
    private final RedisRateLimiter distributedRateLimiter;
    private final ProxyManager<String> redisProxyManager;

    @Autowired
    public RateLimitingFilter(RedisTemplate<String, Object> redisTemplate,
                              MeterRegistry meterRegistry,
                              RedisRateLimiter distributedRateLimiter,
                              ProxyManager<String> redisProxyManager) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.distributedRateLimiter = distributedRateLimiter;
        this.redisProxyManager = redisProxyManager;
        this.rateLimitExceededCounter = Counter.builder("rate_limit_exceeded")
            .description("Number of requests that exceeded rate limit")
            .register(meterRegistry);
    }
    
    @Value("${rate-limiting.enabled:true}")
    private boolean enabled;
    
    @Value("${rate-limiting.default-requests-per-minute:60}")
    private int defaultRequestsPerMinute;
    
    @Value("${rate-limiting.authenticated-requests-per-minute:120}")
    private int authenticatedRequestsPerMinute;
    
    @Value("${rate-limiting.premium-requests-per-minute:300}")
    private int premiumRequestsPerMinute;
    
    /**
     * P0-010 CRITICAL FIX: Redis-based distributed rate limiting
     *
     * BEFORE: Caffeine in-memory cache - doesn't work in distributed environment ❌
     * AFTER: Redis-based distributed bucket storage via Bucket4j ProxyManager ✅
     *
     * Benefits:
     * - Works across multiple API gateway instances
     * - Consistent rate limiting in distributed deployments
     * - Survives service restarts
     * - Central visibility and management
     */
    
    // User tier definitions with more granular limits
    public enum UserTier {
        BASIC(10, 100),           // 10 req/sec, 100 req/min
        STANDARD(100, 1000),      // 100 req/sec, 1000 req/min  
        PREMIUM(1000, 10000),     // 1000 req/sec, 10000 req/min
        ENTERPRISE(5000, 50000);  // 5000 req/sec, 50000 req/min
        
        private final long requestsPerSecond;
        private final long requestsPerMinute;
        
        UserTier(long requestsPerSecond, long requestsPerMinute) {
            this.requestsPerSecond = requestsPerSecond;
            this.requestsPerMinute = requestsPerMinute;
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        return Mono.fromSupplier(() -> {
            ServerHttpRequest request = exchange.getRequest();
            String key = resolveKey(request);
            Bucket bucket = resolveBucket(key, request);
            
            boolean consumed = bucket.tryConsume(1);
            
            if (consumed) {
                // Add rate limit headers
                ServerHttpResponse response = exchange.getResponse();
                response.getHeaders().add("X-RateLimit-Limit", 
                    String.valueOf(getRateLimitForRequest(request)));
                response.getHeaders().add("X-RateLimit-Remaining", 
                    String.valueOf(bucket.getAvailableTokens()));
                response.getHeaders().add("X-RateLimit-Reset", 
                    String.valueOf(System.currentTimeMillis() + 60000));
                
                return true;
            } else {
                // Rate limit exceeded
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                response.getHeaders().add("X-RateLimit-Limit", 
                    String.valueOf(getRateLimitForRequest(request)));
                response.getHeaders().add("X-RateLimit-Remaining", "0");
                response.getHeaders().add("X-RateLimit-Retry-After", "60");
                
                // Track metrics
                rateLimitExceededCounter.increment();
                meterRegistry.counter("rate_limit_exceeded_by_endpoint", 
                    "endpoint", request.getPath().value(),
                    "key", key).increment();
                
                log.warn("Rate limit exceeded for key: {} on endpoint: {}", key, request.getPath().value());
                return false;
            }
        })
        .flatMap(allowed -> {
            if (allowed) {
                return chain.filter(exchange);
            } else {
                return exchange.getResponse().setComplete();
            }
        });
    }

    private String resolveKey(ServerHttpRequest request) {
        // Priority: User ID > API Key > IP Address
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return "rate:user:" + userId;
        }
        
        String apiKey = request.getHeaders().getFirst("X-API-Key");
        if (apiKey != null && !apiKey.isEmpty()) {
            return "rate:api:" + apiKey;
        }
        
        // Fall back to IP address
        String clientIp = getClientIp(request);
        return "rate:ip:" + clientIp;
    }

    /**
     * P0-010 CRITICAL FIX: Resolve bucket using Redis-based distributed storage
     *
     * BEFORE: Local Caffeine cache - inconsistent across instances ❌
     * AFTER: Redis ProxyManager for distributed consistency ✅
     */
    private Bucket resolveBucket(String key, ServerHttpRequest request) {
        try {
            // Use Redis-backed bucket for distributed rate limiting
            int rateLimit = getRateLimitForRequest(request);

            // Configure bucket with token bucket algorithm
            BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(rateLimit, Refill.intervally(rateLimit, Duration.ofMinutes(1))))
                .build();

            // Get or create distributed bucket stored in Redis
            // This ensures consistent rate limiting across all API gateway instances
            return redisProxyManager.builder().build(key, configuration);

        } catch (Exception e) {
            log.error("Failed to resolve Redis bucket for key: {}, falling back to local rate limiter", key, e);
            meterRegistry.counter("rate_limit_redis_error").increment();

            // Fallback: use local rate limiter if Redis fails
            return resolveFallbackBucket(key, request);
        }
    }

    /**
     * Fallback bucket when Redis is unavailable
     */
    private Bucket resolveFallbackBucket(String key, ServerHttpRequest request) {
        int rateLimit = getRateLimitForRequest(request);
        Bandwidth bandwidth = Bandwidth.classic(
            rateLimit,
            Refill.intervally(rateLimit, Duration.ofMinutes(1))
        );

        // Create local bucket (not distributed - for emergency fallback only)
        return Bucket.builder()
            .addLimit(bandwidth)
            .build();
    }

    private BucketConfiguration createBucketConfiguration(ServerHttpRequest request) {
        int rateLimit = getRateLimitForRequest(request);
        
        // Create bandwidth with refill
        Bandwidth bandwidth = Bandwidth.classic(
            rateLimit,
            Refill.intervally(rateLimit, Duration.ofMinutes(1))
        );
        
        return BucketConfiguration.builder()
            .addLimit(bandwidth)
            .build();
    }

    private int getRateLimitForRequest(ServerHttpRequest request) {
        // First check endpoint-specific limits
        String path = request.getPath().value();
        EndpointRateLimitConfig endpointConfig = new EndpointRateLimitConfig();
        int endpointLimit = endpointConfig.getRateLimitForEndpoint(path);
        if (endpointLimit > 0) {
            return endpointLimit;
        }
        
        // Check user tier from headers
        String userTier = request.getHeaders().getFirst("X-User-Tier");
        if (userTier != null) {
            try {
                UserTier tier = UserTier.valueOf(userTier.toUpperCase());
                return (int) tier.requestsPerMinute;
            } catch (IllegalArgumentException e) {
                log.debug("Unknown user tier: {}", userTier);
            }
        }
        
        // Check user roles for backward compatibility
        String userRoles = request.getHeaders().getFirst("X-User-Roles");
        if (userRoles != null) {
            if (userRoles.contains("ENTERPRISE")) {
                return (int) UserTier.ENTERPRISE.requestsPerMinute;
            } else if (userRoles.contains("PREMIUM") || userRoles.contains("BUSINESS")) {
                return (int) UserTier.PREMIUM.requestsPerMinute;
            } else {
                return (int) UserTier.STANDARD.requestsPerMinute;
            }
        }
        
        // Check if it's an authenticated request
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return (int) UserTier.STANDARD.requestsPerMinute;
        }
        
        // Default rate limit for unauthenticated requests
        return (int) UserTier.BASIC.requestsPerMinute;
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddress() != null ? 
            request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return -99; // Run after authentication filter
    }
}

