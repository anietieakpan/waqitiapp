package com.waqiti.user.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for User Service
 * Provides L1 (Caffeine) and L2 (Redis) cache managers
 */
@Slf4j
@Configuration
@EnableCaching
public class UserServiceCacheConfiguration {

    @Value("${cache.l1.maximum-size:10000}")
    private long l1MaximumSize;

    @Value("${cache.l1.expire-after-write:10}")
    private long l1ExpireAfterWriteMinutes;

    @Value("${cache.l2.ttl:30}")
    private long l2TtlMinutes;

    @Value("${cache.l2.key-prefix:user:}")
    private String l2KeyPrefix;

    /**
     * L1 Cache Manager (Caffeine) - Fast local cache
     */
    @Bean("l1CacheManager")
    @ConditionalOnMissingBean(name = "l1CacheManager")
    public CacheManager l1CacheManager() {
        log.info("Configuring L1 Cache Manager with Caffeine");
        
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(l1MaximumSize)
                .expireAfterWrite(l1ExpireAfterWriteMinutes, TimeUnit.MINUTES)
                .recordStats()
                .removalListener((key, value, cause) -> 
                    log.debug("L1 cache eviction - Key: {}, Cause: {}", key, cause))
        );
        
        // Pre-register cache names for N+1 query optimization
        cacheManager.setCacheNames(java.util.Set.of("userSearch", "userExists", "kycStatuses", "userPreferences"));
        
        return cacheManager;
    }

    /**
     * L2 Cache Manager (Redis) - Distributed cache
     */
    @Bean("l2CacheManager")
    @Primary
    @ConditionalOnMissingBean(name = "l2CacheManager")
    public CacheManager l2CacheManager(RedisConnectionFactory redisConnectionFactory) {
        log.info("Configuring L2 Cache Manager with Redis");
        
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(l2TtlMinutes))
                .computePrefixWith(cacheName -> l2KeyPrefix + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfig)
                .transactionAware()
                .build();
    }

    /**
     * Fallback cache manager for backwards compatibility
     */
    @Bean("cacheManager")
    @ConditionalOnMissingBean(name = "cacheManager")
    public CacheManager defaultCacheManager() {
        return l1CacheManager();
    }
}