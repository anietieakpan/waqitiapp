package com.waqiti.apigateway.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter using Token Bucket algorithm
 */
@Slf4j
@Component
public class RateLimitingFilter extends AbstractGatewayFilterFactory<RateLimitingFilter.Config> {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();
    
    public RateLimitingFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String key = getKey(exchange, config);
            Bucket bucket = resolveBucket(key, config);
            
            if (bucket.tryConsume(1)) {
                return chain.filter(exchange)
                    .then(Mono.fromRunnable(() -> {
                        exchange.getResponse().getHeaders().add("X-Rate-Limit-Remaining", 
                            String.valueOf(bucket.getAvailableTokens()));
                    }));
            } else {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().add("X-Rate-Limit-Retry-After", 
                    String.valueOf(config.getRefillDuration().getSeconds()));
                return exchange.getResponse().setComplete();
            }
        };
    }
    
    private String getKey(ServerWebExchange exchange, Config config) {
        switch (config.getKeyType()) {
            case IP:
                return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            case USER:
                return exchange.getRequest().getHeaders().getFirst("X-User-Id");
            case API_KEY:
                return exchange.getRequest().getHeaders().getFirst("X-API-Key");
            default:
                return "global";
        }
    }
    
    private Bucket resolveBucket(String key, Config config) {
        return bucketCache.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.classic(config.getCapacity(), 
                Refill.intervally(config.getRefillTokens(), config.getRefillDuration()));
            return Bucket4j.builder()
                .addLimit(limit)
                .build();
        });
    }
    
    public static class Config {
        private int capacity = 100;
        private int refillTokens = 100;
        private Duration refillDuration = Duration.ofMinutes(1);
        private KeyType keyType = KeyType.IP;
        
        // Getters and setters
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        
        public int getRefillTokens() { return refillTokens; }
        public void setRefillTokens(int refillTokens) { this.refillTokens = refillTokens; }
        
        public Duration getRefillDuration() { return refillDuration; }
        public void setRefillDuration(Duration refillDuration) { this.refillDuration = refillDuration; }
        
        public KeyType getKeyType() { return keyType; }
        public void setKeyType(KeyType keyType) { this.keyType = keyType; }
    }
    
    public enum KeyType {
        IP, USER, API_KEY, GLOBAL
    }
}