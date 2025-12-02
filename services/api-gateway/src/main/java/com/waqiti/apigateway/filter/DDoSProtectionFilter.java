package com.waqiti.apigateway.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DDoSProtectionFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    // MEMORY LEAK FIX: Use Caffeine cache with size limits and expiration
    // Replaces unbounded ConcurrentHashMap that caused memory leaks
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .maximumSize(5000)  // Limit to 5000 IPs
        .expireAfterAccess(Duration.ofMinutes(15))  // Evict inactive IPs
        .recordStats()  // Enable monitoring
        .build();
    
    @Value("${ddos.protection.requests-per-second:100}")
    private long requestsPerSecond;
    
    @Value("${ddos.protection.burst-capacity:200}")
    private long burstCapacity;
    
    @Value("${ddos.protection.block-duration-minutes:15}")
    private long blockDurationMinutes;

    public DDoSProtectionFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = getClientIP(exchange);
        String key = "ddos:block:" + clientIp;
        
        // Check if IP is blocked
        return redisTemplate.hasKey(key)
            .flatMap(isBlocked -> {
                if (Boolean.TRUE.equals(isBlocked)) {
                    log.warn("Blocked request from IP: {} due to DDoS protection", clientIp);
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", 
                        String.valueOf(blockDurationMinutes * 60));
                    return exchange.getResponse().setComplete();
                }
                
                // Check rate limit
                Bucket bucket = getBucket(clientIp);
                if (bucket.tryConsume(1)) {
                    return chain.filter(exchange);
                } else {
                    // Block the IP
                    return blockIP(clientIp)
                        .then(Mono.defer(() -> {
                            log.warn("IP {} exceeded rate limit and has been blocked", clientIp);
                            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", 
                                String.valueOf(blockDurationMinutes * 60));
                            return exchange.getResponse().setComplete();
                        }));
                }
            });
    }

    private Bucket getBucket(String key) {
        return buckets.get(key, k -> {
            Bandwidth limit = Bandwidth.classic(burstCapacity, 
                Refill.intervally(requestsPerSecond, Duration.ofSeconds(1)));
            return Bucket4j.builder()
                .addLimit(limit)
                .build();
        });
    }

    private Mono<Boolean> blockIP(String ip) {
        String key = "ddos:block:" + ip;
        return redisTemplate.opsForValue()
            .set(key, "blocked", Duration.ofMinutes(blockDurationMinutes));
    }

    private String getClientIP(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIP = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        return exchange.getRequest().getRemoteAddress() != null ? 
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}