/**
 * File: ./api-gateway/src/src/main/java/com/waqiti/gateway/config/RateLimiterConfig.java
 */
package com.waqiti.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Value("${rate-limiter.default.limit:10}")
    private int defaultLimit;

    @Value("${rate-limiter.default.refresh-period:1}")
    private int defaultRefreshPeriod;

    @Value("${rate-limiter.ip.limit:20}")
    private int ipLimit;

    @Value("${rate-limiter.ip.refresh-period:1}")
    private int ipRefreshPeriod;

    @Value("${rate-limiter.user.limit:50}")
    private int userLimit;

    @Value("${rate-limiter.user.refresh-period:1}")
    private int userRefreshPeriod;

    @Value("${rate-limiter.auth.limit:5}")
    private int authLimit;

    @Value("${rate-limiter.auth.refresh-period:1}")
    private int authRefreshPeriod;

    @Value("${rate-limiter.basic.limit:100}")
    private int basicTierLimit;
    
    @Value("${rate-limiter.standard.limit:1000}")
    private int standardTierLimit;
    
    @Value("${rate-limiter.premium.limit:10000}")
    private int premiumTierLimit;
    
    @Value("${rate-limiter.enterprise.limit:50000}")
    private int enterpriseTierLimit;

    @Bean
    public RedisRateLimiter defaultRedisRateLimiter() {
        return new RedisRateLimiter(defaultLimit, defaultLimit * 2, defaultRefreshPeriod);
    }
    
    @Bean("redisRateLimiter")
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(50, 100, 1);
    }

    @Bean
    public RedisRateLimiter ipRedisRateLimiter() {
        return new RedisRateLimiter(ipLimit, ipLimit * 2, ipRefreshPeriod);
    }

    @Bean
    public RedisRateLimiter userRedisRateLimiter() {
        return new RedisRateLimiter(userLimit, userLimit * 2, userRefreshPeriod);
    }

    @Bean
    public RedisRateLimiter authRedisRateLimiter() {
        return new RedisRateLimiter(authLimit, authLimit * 2, authRefreshPeriod);
    }
    
    @Bean
    public RedisRateLimiter basicTierRateLimiter() {
        return new RedisRateLimiter(basicTierLimit / 60, basicTierLimit, 1);
    }
    
    @Bean
    public RedisRateLimiter standardTierRateLimiter() {
        return new RedisRateLimiter(standardTierLimit / 60, standardTierLimit, 1);
    }
    
    @Bean
    public RedisRateLimiter premiumTierRateLimiter() {
        return new RedisRateLimiter(premiumTierLimit / 60, premiumTierLimit, 1);
    }
    
    @Bean
    public RedisRateLimiter enterpriseTierRateLimiter() {
        return new RedisRateLimiter(enterpriseTierLimit / 60, enterpriseTierLimit, 1);
    }

    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }

    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
            return Mono.justOrEmpty(userId).defaultIfEmpty("anonymous");
        };
    }

    @Bean
    KeyResolver apiKeyResolver() {
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            return Mono.justOrEmpty(apiKey).defaultIfEmpty("anonymous");
        };
    }

    @Bean
    KeyResolver compositeKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
            String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();

            if (userId != null && !userId.isEmpty()) {
                return Mono.just("user:" + userId);
            } else {
                return Mono.just("ip:" + ip);
            }
        };
    }
}