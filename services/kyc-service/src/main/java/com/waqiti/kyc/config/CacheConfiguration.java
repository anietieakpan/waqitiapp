package com.waqiti.kyc.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfiguration {

    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // KYC verification cache - longer TTL for approved verifications
        cacheConfigurations.put("kyc-verifications", 
                defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // User status cache - moderate TTL
        cacheConfigurations.put("kyc-user-status", 
                defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // User verification status - longer TTL for performance
        cacheConfigurations.put("kyc-user-verified", 
                defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // User action permissions - moderate TTL
        cacheConfigurations.put("kyc-user-actions", 
                defaultConfig.entryTtl(Duration.ofMinutes(20)));
        
        // Document cache - shorter TTL for security
        cacheConfigurations.put("kyc-documents", 
                defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Provider session cache - short TTL
        cacheConfigurations.put("kyc-provider-sessions", 
                defaultConfig.entryTtl(Duration.ofMinutes(2)));
        
        // Compliance checks cache - longer TTL
        cacheConfigurations.put("kyc-compliance-checks", 
                defaultConfig.entryTtl(Duration.ofHours(2)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}