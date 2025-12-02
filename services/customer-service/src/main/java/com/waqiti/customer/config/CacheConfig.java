package com.waqiti.customer.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Cache Configuration for Customer Service.
 * Configures Redis-based caching with Jackson serialization,
 * custom TTL per cache, and optimized cache management.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    @Value("${spring.cache.redis.time-to-live:600000}")
    private long defaultTtlMillis;

    @Value("${spring.cache.redis.key-prefix:customer:}")
    private String keyPrefix;

    @Value("${spring.cache.redis.cache-null-values:false}")
    private boolean cacheNullValues;

    // Cache names
    public static final String CUSTOMERS_CACHE = "customers";
    public static final String CUSTOMER_ANALYTICS_CACHE = "customer-analytics";
    public static final String CUSTOMER_LIFECYCLE_CACHE = "customer-lifecycle";
    public static final String COMPLAINTS_CACHE = "complaints";

    /**
     * Configures ObjectMapper for Redis serialization.
     * Enables polymorphic type handling and Java time module.
     *
     * @return Configured ObjectMapper
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        log.info("Redis ObjectMapper configured with JavaTimeModule");
        return objectMapper;
    }

    /**
     * Configures RedisTemplate for generic Redis operations.
     * Uses String for keys and JSON serialization for values.
     *
     * @param connectionFactory Redis connection factory
     * @return Configured RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Use Jackson JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();

        log.info("RedisTemplate configured with Jackson JSON serialization");
        return template;
    }

    /**
     * Configures RedisCacheManager with custom cache configurations.
     * Each cache can have different TTL settings.
     *
     * @param connectionFactory Redis connection factory
     * @return Configured CacheManager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMillis(defaultTtlMillis))
            .disableCachingNullValues()
            .prefixCacheNameWith(keyPrefix)
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(redisObjectMapper())
                )
            );

        // Custom cache configurations with different TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Customers cache - 10 minutes
        cacheConfigurations.put(CUSTOMERS_CACHE,
            defaultCacheConfig.entryTtl(Duration.ofMinutes(10)));

        // Customer analytics cache - 15 minutes
        cacheConfigurations.put(CUSTOMER_ANALYTICS_CACHE,
            defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));

        // Customer lifecycle cache - 30 minutes
        cacheConfigurations.put(CUSTOMER_LIFECYCLE_CACHE,
            defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));

        // Complaints cache - 5 minutes
        cacheConfigurations.put(COMPLAINTS_CACHE,
            defaultCacheConfig.entryTtl(Duration.ofMinutes(5)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();

        log.info("RedisCacheManager configured with {} custom caches, default TTL: {}ms",
            cacheConfigurations.size(), defaultTtlMillis);

        return cacheManager;
    }
}
