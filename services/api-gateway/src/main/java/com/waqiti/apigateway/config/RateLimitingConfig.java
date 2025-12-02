package com.waqiti.apigateway.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Rate limiting configuration for API Gateway
 * Implements token bucket algorithm with distributed caching
 */
@Configuration
@Slf4j
public class RateLimitingConfig {

    @Value("${rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;

    @Value("${rate-limiting.default.requests-per-minute:60}")
    private int defaultRequestsPerMinute;

    @Value("${rate-limiting.default.burst-capacity:100}")
    private int defaultBurstCapacity;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * P0-010 CRITICAL FIX: Redis-based ProxyManager for distributed rate limiting
     *
     * BEFORE: JCache in-memory storage - not distributed ❌
     * AFTER: Redis Lettuce-based distributed storage ✅
     */
    @Bean
    public ProxyManager<String> redisProxyManager() {
        log.info("Initializing Redis-based rate limiting - host: {}, port: {}", redisHost, redisPort);

        try {
            // Build Redis URI
            RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withTimeout(java.time.Duration.ofSeconds(5));

            if (redisPassword != null && !redisPassword.isEmpty()) {
                uriBuilder.withPassword(redisPassword.toCharArray());
            }

            RedisURI redisUri = uriBuilder.build();

            // Create Redis client
            RedisClient redisClient = RedisClient.create(redisUri);

            // Create connection with String codec
            StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
            );

            // Create Lettuce-based ProxyManager for distributed buckets
            ProxyManager<String> proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .build();

            log.info("✅ Redis-based distributed rate limiting initialized successfully");
            return proxyManager;

        } catch (Exception e) {
            log.error("❌ Failed to initialize Redis ProxyManager: {}", e.getMessage(), e);
            throw new RuntimeException("Redis rate limiting initialization failed", e);
        }
    }

    @Component
    public static class RateLimitGatewayFilterFactory extends AbstractGatewayFilterFactory<RateLimitGatewayFilterFactory.Config> {

        private final ProxyManager<String> proxyManager;
        private final Map<String, RateLimitTier> tierConfigurations;
        private final boolean rateLimitingEnabled;
        private final int defaultRequestsPerMinute;
        private final int defaultBurstCapacity;

        public RateLimitGatewayFilterFactory(ProxyManager<String> proxyManager, 
                                           @Value("${rate-limiting.enabled:true}") boolean rateLimitingEnabled,
                                           @Value("${rate-limiting.default.requests-per-minute:60}") int defaultRequestsPerMinute,
                                           @Value("${rate-limiting.default.burst-capacity:100}") int defaultBurstCapacity) {
            super(Config.class);
            this.proxyManager = proxyManager;
            this.rateLimitingEnabled = rateLimitingEnabled;
            this.defaultRequestsPerMinute = defaultRequestsPerMinute;
            this.defaultBurstCapacity = defaultBurstCapacity;
            this.tierConfigurations = initializeTierConfigurations();
        }

        @Override
        public GatewayFilter apply(Config config) {
            return (exchange, chain) -> {
                if (!rateLimitingEnabled) {
                    return chain.filter(exchange);
                }

                String clientId = extractClientId(exchange);
                String endpoint = exchange.getRequest().getPath().value();
                RateLimitTier tier = determineTier(clientId, endpoint);
                
                Bucket bucket = proxyManager.builder()
                    .build(clientId, () -> createBucket(tier));

                if (bucket.tryConsume(1)) {
                    // Add rate limit headers
                    exchange.getResponse().getHeaders().add("X-RateLimit-Limit", 
                        String.valueOf(tier.getRequestsPerMinute()));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", 
                        String.valueOf(bucket.getAvailableTokens()));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Reset", 
                        String.valueOf(System.currentTimeMillis() + 60000));
                    
                    return chain.filter(exchange);
                } else {
                    // Rate limit exceeded
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", "60");
                    
                    log.warn("Rate limit exceeded for client: {} on endpoint: {}", clientId, endpoint);
                    
                    return exchange.getResponse().setComplete();
                }
            };
        }

        private String extractClientId(org.springframework.web.server.ServerWebExchange exchange) {
            // Try to extract from JWT
            String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                // Extract user ID from JWT (simplified - in production use proper JWT parsing)
                return "user:" + authorization.substring(7, Math.min(20, authorization.length()));
            }
            
            // Fall back to IP address
            String remoteAddr = exchange.getRequest().getRemoteAddress() != null ? 
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
            return "ip:" + remoteAddr;
        }

        private RateLimitTier determineTier(String clientId, String endpoint) {
            // Critical endpoints get stricter limits
            if (endpoint.contains("/api/v1/payments") || endpoint.contains("/api/v1/crypto")) {
                return tierConfigurations.get("critical");
            }
            
            // Authentication endpoints
            if (endpoint.contains("/auth") || endpoint.contains("/login") || endpoint.contains("/register")) {
                return tierConfigurations.get("auth");
            }
            
            // Public endpoints
            if (endpoint.contains("/public") || endpoint.contains("/health")) {
                return tierConfigurations.get("public");
            }
            
            // Premium users (simplified check - in production check user tier from database)
            if (clientId.startsWith("user:premium")) {
                return tierConfigurations.get("premium");
            }
            
            return tierConfigurations.get("standard");
        }

        private Bucket createBucket(RateLimitTier tier) {
            return Bucket4j.builder()
                .addLimit(Bandwidth.classic(
                    tier.getBurstCapacity(),
                    Refill.intervally(tier.getRequestsPerMinute(), java.time.Duration.ofMinutes(1))
                ))
                .build();
        }

        private Map<String, RateLimitTier> initializeTierConfigurations() {
            Map<String, RateLimitTier> tiers = new ConcurrentHashMap<>();
            
            // Critical endpoints - strict limits
            tiers.put("critical", new RateLimitTier(30, 50, "critical"));
            
            // Authentication endpoints - prevent brute force
            tiers.put("auth", new RateLimitTier(10, 15, "auth"));
            
            // Public endpoints - generous limits
            tiers.put("public", new RateLimitTier(100, 150, "public"));
            
            // Premium tier - higher limits
            tiers.put("premium", new RateLimitTier(200, 300, "premium"));
            
            // Standard tier - default limits
            tiers.put("standard", new RateLimitTier(defaultRequestsPerMinute, defaultBurstCapacity, "standard"));
            
            return tiers;
        }

        public static class Config {
            private String keyResolver;
            private int requestsPerMinute;
            private int burstCapacity;

            // Getters and setters
            public String getKeyResolver() { return keyResolver; }
            public void setKeyResolver(String keyResolver) { this.keyResolver = keyResolver; }
            public int getRequestsPerMinute() { return requestsPerMinute; }
            public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
            public int getBurstCapacity() { return burstCapacity; }
            public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }
        }
    }

    /**
     * Rate limit tier configuration
     */
    public static class RateLimitTier {
        private final int requestsPerMinute;
        private final int burstCapacity;
        private final String name;

        public RateLimitTier(int requestsPerMinute, int burstCapacity, String name) {
            this.requestsPerMinute = requestsPerMinute;
            this.burstCapacity = burstCapacity;
            this.name = name;
        }

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public int getBurstCapacity() { return burstCapacity; }
        public String getName() { return name; }
    }

    /**
     * Distributed rate limiter for microservices
     */
    @Component
    public static class DistributedRateLimiter {
        
        private final ProxyManager<String> proxyManager;

        public DistributedRateLimiter(ProxyManager<String> proxyManager) {
            this.proxyManager = proxyManager;
        }

        public boolean tryConsume(String key, int tokens, RateLimitTier tier) {
            Bucket bucket = proxyManager.builder()
                .build(key, () -> Bucket4j.builder()
                    .addLimit(Bandwidth.classic(
                        tier.getBurstCapacity(),
                        Refill.intervally(tier.getRequestsPerMinute(), java.time.Duration.ofMinutes(1))
                    ))
                    .build());
            
            return bucket.tryConsume(tokens);
        }

        public long estimateTimeToConsume(String key, int tokens, RateLimitTier tier) {
            Bucket bucket = proxyManager.builder()
                .build(key, () -> Bucket4j.builder()
                    .addLimit(Bandwidth.classic(
                        tier.getBurstCapacity(),
                        Refill.intervally(tier.getRequestsPerMinute(), java.time.Duration.ofMinutes(1))
                    ))
                    .build());
            
            return bucket.estimateAbilityToConsume(tokens).getRoundedSecondsToWait();
        }
    }
}