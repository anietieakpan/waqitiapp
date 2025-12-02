package com.waqiti.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive Redis Cache Configuration for Waqiti Platform
 *
 * Features:
 * - Multi-level caching with configurable TTLs
 * - JSON serialization for complex objects
 * - Cache metrics and monitoring
 * - Error handling with fallback
 * - Custom key generation strategy
 *
 * @author Platform Engineering Team
 * @version 1.0.0
 * @since 2025-11-22
 */
@Slf4j
@Configuration
@EnableCaching
@Profile("!test")  // Disable caching in test profile
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class WaqitiRedisCacheConfiguration implements CachingConfigurer {

    private final MeterRegistry meterRegistry;

    public WaqitiRedisCacheConfiguration(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Primary cache manager with service-specific TTL configurations
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Initializing Waqiti Redis Cache Manager");

        RedisCacheConfiguration defaultConfig = createDefaultCacheConfiguration();
        Map<String, RedisCacheConfiguration> cacheConfigurations = createCacheSpecificConfigurations(defaultConfig);

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();

        log.info("Redis Cache Manager initialized with {} cache configurations", cacheConfigurations.size());
        return cacheManager;
    }

    /**
     * Default cache configuration with 1-hour TTL
     */
    private RedisCacheConfiguration createDefaultCacheConfiguration() {
        ObjectMapper objectMapper = createCacheObjectMapper();

        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)))
            .computePrefixWith(cacheName -> cacheName + ":");
    }

    /**
     * Service-specific cache configurations with custom TTLs
     */
    private Map<String, RedisCacheConfiguration> createCacheSpecificConfigurations(
            RedisCacheConfiguration defaultConfig) {

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();

        // User & Authentication - 30 minutes
        configs.put("user-profiles", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put("user-permissions", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put("user-roles", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put("session-data", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Wallet & Balance - 5 minutes (high freshness)
        configs.put("wallet-balances", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        configs.put("wallet-details", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        configs.put("wallet-limits", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put("daily-limits", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Transactions - 10 minutes
        configs.put("transactions", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        configs.put("transaction-details", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put("transaction-status", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // Payments - 5 minutes
        configs.put("payment-status", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        configs.put("payment-methods", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put("payment-limits", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Account & Banking - 30 minutes
        configs.put("accounts", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put("account-balances", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        configs.put("account-statements", defaultConfig.entryTtl(Duration.ofHours(1)));

        // KYC & Compliance - 1 hour (relatively static)
        configs.put("kyc-status", defaultConfig.entryTtl(Duration.ofHours(1)));
        configs.put("kyc-documents", defaultConfig.entryTtl(Duration.ofHours(1)));
        configs.put("compliance-rules", defaultConfig.entryTtl(Duration.ofHours(1)));
        configs.put("sanctions-lists", defaultConfig.entryTtl(Duration.ofHours(2)));

        // Exchange Rates - 1 minute (very dynamic)
        configs.put("exchange-rates", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        configs.put("crypto-prices", defaultConfig.entryTtl(Duration.ofSeconds(30)));

        // Fraud Detection - 30 minutes
        configs.put("fraud-rules", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put("fraud-scores", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        configs.put("blacklists", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Cards - 30 minutes
        configs.put("card-details", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put("virtual-cards", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put("card-limits", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Notifications - 15 minutes
        configs.put("notification-templates", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        configs.put("notification-preferences", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // Analytics & Reporting - 1-2 hours (can be stale)
        configs.put("analytics", defaultConfig.entryTtl(Duration.ofHours(1)));
        configs.put("reports", defaultConfig.entryTtl(Duration.ofHours(2)));

        // Reference Data - 4 hours (very static)
        configs.put("countries", defaultConfig.entryTtl(Duration.ofHours(4)));
        configs.put("currencies", defaultConfig.entryTtl(Duration.ofHours(4)));
        configs.put("banks", defaultConfig.entryTtl(Duration.ofHours(4)));
        configs.put("merchant-categories", defaultConfig.entryTtl(Duration.ofHours(4)));

        return configs;
    }

    /**
     * ObjectMapper for Redis JSON serialization with type information
     */
    private ObjectMapper createCacheObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return objectMapper;
    }

    /**
     * RedisTemplate for direct Redis operations
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(createCacheObjectMapper());

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * Custom key generator for cache keys
     * Format: className.methodName:param1:param2:...
     */
    @Override
    @Bean("waqitiKeyGenerator")
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName());
            sb.append(".").append(method.getName());

            for (Object param : params) {
                sb.append(":");
                sb.append(param != null ? param.toString() : "null");
            }

            return sb.toString();
        };
    }

    /**
     * Custom cache error handler - log errors but don't fail the operation
     * This ensures cache failures don't break business logic
     */
    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception,
                                           org.springframework.cache.Cache cache,
                                           Object key) {
                log.error("Cache GET error for cache: {}, key: {}",
                    cache.getName(), key, exception);
                meterRegistry.counter("cache.error",
                    "operation", "get",
                    "cache", cache.getName()).increment();
            }

            @Override
            public void handleCachePutError(RuntimeException exception,
                                           org.springframework.cache.Cache cache,
                                           Object key, Object value) {
                log.error("Cache PUT error for cache: {}, key: {}",
                    cache.getName(), key, exception);
                meterRegistry.counter("cache.error",
                    "operation", "put",
                    "cache", cache.getName()).increment();
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception,
                                             org.springframework.cache.Cache cache,
                                             Object key) {
                log.error("Cache EVICT error for cache: {}, key: {}",
                    cache.getName(), key, exception);
                meterRegistry.counter("cache.error",
                    "operation", "evict",
                    "cache", cache.getName()).increment();
            }

            @Override
            public void handleCacheClearError(RuntimeException exception,
                                             org.springframework.cache.Cache cache) {
                log.error("Cache CLEAR error for cache: {}",
                    cache.getName(), exception);
                meterRegistry.counter("cache.error",
                    "operation", "clear",
                    "cache", cache.getName()).increment();
            }
        };
    }

    /**
     * Cache statistics bean for monitoring
     */
    @Bean
    public CacheStatistics cacheStatistics(CacheManager cacheManager) {
        return new CacheStatistics(cacheManager, meterRegistry);
    }

    /**
     * Inner class for cache statistics and monitoring
     */
    public static class CacheStatistics {
        private final CacheManager cacheManager;
        private final MeterRegistry meterRegistry;

        public CacheStatistics(CacheManager cacheManager, MeterRegistry meterRegistry) {
            this.cacheManager = cacheManager;
            this.meterRegistry = meterRegistry;

            // Register cache metrics
            cacheManager.getCacheNames().forEach(cacheName -> {
                meterRegistry.gauge("cache.size",
                    java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("cache", cacheName)),
                    cacheName,
                    this::getCacheSize);
            });
        }

        private long getCacheSize(String cacheName) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof org.springframework.data.redis.cache.RedisCache) {
                // Would need Redis commands to get actual size
                return 0; // Placeholder
            }
            return 0;
        }
    }
}
