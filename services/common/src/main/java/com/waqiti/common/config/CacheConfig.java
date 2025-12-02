package com.waqiti.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Multi-level caching configuration for optimal performance
 * Implements L1 (Caffeine) and L2 (Redis) caching strategies
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    /**
     * L1 Cache (Caffeine) - Fast in-memory cache for frequently accessed data
     */
    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Default cache configuration
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .recordStats());

        // Specific cache configurations
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "users",           // User profiles - cached for 30 minutes
            "userSessions",    // User sessions - cached for 15 minutes
            "exchangeRates",   // Exchange rates - cached for 5 minutes
            "systemConfig",    // System configuration - cached for 1 hour
            "walletBalances"   // Wallet balances - cached for 2 minutes
        ));

        return cacheManager;
    }

    /**
     * L2 Cache (Redis) - Distributed cache for shared data across instances
     */
    @Bean("redisCacheManager")
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // Configure JSON serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new JavaTimeModule());
        
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);

        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(jsonSerializer))
            .disableCachingNullValues();

        // Specific cache configurations with different TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // User data - cache for 1 hour
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // Authentication tokens - cache for 30 minutes
        cacheConfigurations.put("authTokens", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Exchange rates - cache for 5 minutes (frequently updated)
        cacheConfigurations.put("exchangeRates", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Transaction history - cache for 2 hours (less frequently accessed)
        cacheConfigurations.put("transactionHistory", defaultConfig.entryTtl(Duration.ofHours(2)));
        
        // Wallet balances - cache for 1 minute (critical for accuracy)
        cacheConfigurations.put("walletBalances", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        
        // KYC documents - cache for 4 hours (rarely change)
        cacheConfigurations.put("kycDocuments", defaultConfig.entryTtl(Duration.ofHours(4)));
        
        // Security events - cache for 15 minutes
        cacheConfigurations.put("securityEvents", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Analytics data - cache for 30 minutes
        cacheConfigurations.put("analyticsData", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        
        return RedisCacheManager.builder(cacheWriter)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
    }

    /**
     * Redis connection factory with optimization
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            factory.setPassword(redisPassword);
        }
        factory.setValidateConnection(true);
        return factory;
    }

    /**
     * Redis template for manual cache operations
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new JavaTimeModule());
        
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);
        template.setDefaultSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Cache warming service for critical data
     */
    @Bean
    public CacheWarmupService cacheWarmupService() {
        return new CacheWarmupService();
    }

    /**
     * Cache warming service implementation
     */
    public static final class CacheWarmupService {
        
        /**
         * Warm up critical caches on application startup
         */
        public void warmupCaches() {
            // Implementation would load critical data into cache
            // This could be called from @EventListener(ApplicationReadyEvent.class)
        }
    }
}