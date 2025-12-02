package com.waqiti.investment.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache configuration for investment service
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Stock quotes cache - 1 minute TTL
        cacheConfigurations.put("stockQuotes", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        
        // Batch stock quotes cache - 1 minute TTL
        cacheConfigurations.put("batchStockQuotes", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        
        // Historical data cache - 1 hour TTL
        cacheConfigurations.put("historicalData", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // Market indices cache - 5 minutes TTL
        cacheConfigurations.put("marketIndices", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Top movers cache - 15 minutes TTL
        cacheConfigurations.put("topMovers", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}