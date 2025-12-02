/**
 * Redis Cache Configuration
 * Configures caching strategy for credit assessments, external API calls, and banking data
 */
package com.waqiti.bnpl.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
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
 * Production-grade Redis cache configuration for BNPL service
 * Implements caching for:
 * - Credit assessments (30 days TTL)
 * - Credit bureau data (24 hours TTL)
 * - Banking data analysis (6 hours TTL)
 * - BNPL applications (1 hour TTL)
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    /**
     * Configure Redis cache manager with custom TTLs per cache
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Configuring Redis cache manager with custom TTLs");

        // Configure Jackson for Redis serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        // Custom configurations for specific caches
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Credit assessments cache - 30 days (matches assessment validity period)
        cacheConfigurations.put("creditAssessments",
                defaultConfig.entryTtl(Duration.ofDays(30))
                        .prefixCacheNameWith("bnpl:credit:assessment:"));

        // Credit bureau data cache - 24 hours (external API call, expensive)
        cacheConfigurations.put("creditBureauData",
                defaultConfig.entryTtl(Duration.ofHours(24))
                        .prefixCacheNameWith("bnpl:credit:bureau:"));

        // Banking data analysis cache - 6 hours (open banking API, moderately expensive)
        cacheConfigurations.put("bankingData",
                defaultConfig.entryTtl(Duration.ofHours(6))
                        .prefixCacheNameWith("bnpl:banking:"));

        // BNPL applications cache - 1 hour (frequently updated)
        cacheConfigurations.put("bnplApplications",
                defaultConfig.entryTtl(Duration.ofHours(1))
                        .prefixCacheNameWith("bnpl:applications:"));

        // BNPL plans cache - 1 hour
        cacheConfigurations.put("bnplPlans",
                defaultConfig.entryTtl(Duration.ofHours(1))
                        .prefixCacheNameWith("bnpl:plans:"));

        // Installments cache - 30 minutes (changes frequently with payments)
        cacheConfigurations.put("installments",
                defaultConfig.entryTtl(Duration.ofMinutes(30))
                        .prefixCacheNameWith("bnpl:installments:"));

        // User credit limits cache - 5 minutes (for high-traffic credit limit checks)
        cacheConfigurations.put("creditLimits",
                defaultConfig.entryTtl(Duration.ofMinutes(5))
                        .prefixCacheNameWith("bnpl:limits:"));

        // Build and return cache manager
        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();

        log.info("Redis cache manager configured with {} custom caches", cacheConfigurations.size());
        return cacheManager;
    }

    /**
     * Custom key generator for cache keys
     * Generates keys in format: className.methodName:arg1:arg2:...
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName());
            sb.append(".");
            sb.append(method.getName());
            for (Object param : params) {
                if (param != null) {
                    sb.append(":");
                    sb.append(param.toString());
                }
            }
            return sb.toString();
        };
    }

    /**
     * Custom cache error handler
     * Logs cache errors but doesn't fail the operation
     * This ensures cache failures don't break the application
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.error("Cache GET error in cache '{}' for key '{}': {}", cache.getName(), key, exception.getMessage());
                // Don't throw exception - fail gracefully
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
                log.error("Cache PUT error in cache '{}' for key '{}': {}", cache.getName(), key, exception.getMessage());
                // Don't throw exception - fail gracefully
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.error("Cache EVICT error in cache '{}' for key '{}': {}", cache.getName(), key, exception.getMessage());
                // Don't throw exception - fail gracefully
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                log.error("Cache CLEAR error in cache '{}': {}", cache.getName(), exception.getMessage());
                // Don't throw exception - fail gracefully
            }
        };
    }
}
