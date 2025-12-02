package com.waqiti.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive Redis caching configuration for optimal performance
 */
@Configuration
@EnableCaching
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisCachingConfig extends CachingConfigurerSupport {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:2000}")
    private long redisTimeout;

    @Value("${spring.cache.redis.time-to-live:3600000}")
    private long defaultTtl;

    /**
     * Redis connection factory with connection pooling
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(redisHost);
        redisStandaloneConfiguration.setPort(redisPort);
        redisStandaloneConfiguration.setDatabase(redisDatabase);
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisStandaloneConfiguration.setPassword(redisPassword);
        }

        // Connection pool configuration
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(30);
        poolConfig.setMinIdle(10);
        poolConfig.setMaxWait(Duration.ofMillis(2000));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));

        // Client configuration with timeout and pooling
        LettucePoolingClientConfiguration lettucePoolingClientConfiguration = LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(redisTimeout))
                .shutdownTimeout(Duration.ofMillis(100))
                .poolConfig(poolConfig)
                .build();

        return new LettuceConnectionFactory(redisStandaloneConfiguration, lettucePoolingClientConfiguration);
    }

    /**
     * Redis template for manual cache operations
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Use JSON serializer for values
        GenericJackson2JsonRedisSerializer jsonSerializer = configureJsonSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * Configure JSON serializer with type information
     */
    private GenericJackson2JsonRedisSerializer configureJsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // Enable type information for proper deserialization
        objectMapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    /**
     * Cache manager with different cache configurations
     */
    @Bean
    @Override
    public CacheManager cacheManager() {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMillis(defaultTtl))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(configureJsonSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // User cache - 30 minutes TTL
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Transaction cache - 10 minutes TTL
        cacheConfigurations.put("transactions", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        
        // Payment cache - 5 minutes TTL
        cacheConfigurations.put("payments", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Wallet balance cache - 1 minute TTL (critical data)
        cacheConfigurations.put("walletBalance", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        
        // Exchange rates cache - 30 seconds TTL
        cacheConfigurations.put("exchangeRates", defaultConfig.entryTtl(Duration.ofSeconds(30)));
        
        // KYC verification cache - 1 hour TTL
        cacheConfigurations.put("kycVerification", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // Security permissions cache - 15 minutes TTL
        cacheConfigurations.put("permissions", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // API rate limiting cache - 1 minute TTL
        cacheConfigurations.put("rateLimits", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        
        // Session cache - 30 minutes TTL
        cacheConfigurations.put("sessions", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Static data cache - 24 hours TTL
        cacheConfigurations.put("staticData", defaultConfig.entryTtl(Duration.ofHours(24)));
        
        // Merchant data cache - 1 hour TTL
        cacheConfigurations.put("merchants", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // Notification templates cache - 6 hours TTL
        cacheConfigurations.put("notificationTemplates", defaultConfig.entryTtl(Duration.ofHours(6)));

        return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory()))
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /**
     * Custom key generator for complex cache keys
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
                sb.append(".");
                if (param != null) {
                    sb.append(param.toString());
                } else {
                    sb.append("null");
                }
            }
            return sb.toString();
        };
    }

    /**
     * Cache error handler to prevent cache failures from breaking the application
     */
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.error("Cache get error for cache: {} key: {}", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) {
                log.error("Cache put error for cache: {} key: {}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.error("Cache evict error for cache: {} key: {}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                log.error("Cache clear error for cache: {}", cache.getName(), exception);
            }
        };
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisCachingConfig.class);
}