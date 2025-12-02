package com.waqiti.common.ratelimit;

import com.waqiti.common.cache.CacheProperties;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;
import org.redisson.api.RedissonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.time.Duration;

/**
 * Configuration for rate limiting using Bucket4j with Redis backend
 */
@Slf4j
@Configuration
@EnableAspectJAutoProxy
@ConditionalOnClass({ProxyManager.class, RedissonClient.class})
@ConditionalOnProperty(value = "rate-limiting.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;
    
    @Value("${spring.data.redis.password:}")
    private String redisPassword;
    
    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;
    
    @Value("${spring.data.redis.timeout:2000ms}")
    private Duration redisTimeout;

    @Bean
    public RedissonClient rateLimitRedissonClient() {
        org.redisson.config.Config config = new org.redisson.config.Config();
        String redisUrl = "redis://" + redisHost + ":" + redisPort;
        config.useSingleServer()
            .setAddress(redisUrl)
            .setDatabase(redisDatabase)
            .setTimeout((int) redisTimeout.toMillis());
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.useSingleServer().setPassword(redisPassword);
        }
        
        RedissonClient client = org.redisson.Redisson.create(config);
        log.info("Created Redisson client for rate limiting: {}:{}", redisHost, redisPort);
        return client;
    }

    @Bean
    public ProxyManager<String> bucketProxyManager(RedissonClient rateLimitRedissonClient, RateLimitProperties properties) {
        RedissonBasedProxyManager<String> proxyManager = RedissonBasedProxyManager.builderFor(rateLimitRedissonClient)
            .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                Duration.ofMinutes(properties.getBucketExpirationMinutes())
            ))
            .build();
        
        log.info("Initialized Bucket4j ProxyManager with Redis backend");
        return proxyManager;
    }

    @Bean
    public RateLimitingAspect rateLimitingAspect(ProxyManager<String> proxyManager) {
        log.info("Rate limiting aspect enabled");
        return new RateLimitingAspect(proxyManager);
    }
}