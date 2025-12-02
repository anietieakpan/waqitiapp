package com.waqiti.common.caching;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive cache configuration manager that provides:
 * - Multi-level cache configuration (L1: local, L2: Redis)
 * - Dynamic cache configuration updates
 * - Cache-specific TTL and eviction policies
 * - Performance-optimized serialization
 * - Cache warming strategies
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Configuration
@EnableCaching
@Slf4j
@RequiredArgsConstructor
public class CacheConfigurationManager {

    private final RedisConnectionFactory redisConnectionFactory;
    private final CacheProperties cacheProperties;

    /**
     * Primary cache manager for L1 (local) caching.
     */
    @Bean
    @Primary
    public CacheManager l1CacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        
        // Configure cache names for L1
        cacheManager.setCacheNames(cacheProperties.getL1CacheNames());
        cacheManager.setAllowNullValues(false);
        
        log.info("Configured L1 cache manager with caches: {}", cacheProperties.getL1CacheNames());
        return cacheManager;
    }

    /**
     * Secondary cache manager for L2 (distributed Redis) caching.
     */
    @Bean("l2CacheManager")
    public CacheManager l2CacheManager() {
        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory))
                .cacheDefaults(createDefaultRedisCacheConfiguration());

        // Configure cache-specific TTLs and configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = createCacheSpecificConfigurations();
        builder.withInitialCacheConfigurations(cacheConfigurations);

        RedisCacheManager cacheManager = builder.build();
        
        log.info("Configured L2 (Redis) cache manager with {} cache configurations", 
                cacheConfigurations.size());
        
        return cacheManager;
    }

    /**
     * Create default Redis cache configuration.
     */
    private RedisCacheConfiguration createDefaultRedisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(cacheProperties.getDefaultTtlMinutes()))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }

    /**
     * Create cache-specific configurations with optimized TTL and policies.
     */
    private Map<String, RedisCacheConfiguration> createCacheSpecificConfigurations() {
        Map<String, RedisCacheConfiguration> configurations = new HashMap<>();

        // User data cache - medium TTL, high frequency
        configurations.put("users", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer())));

        // Authentication cache - short TTL, very high frequency
        configurations.put("auth", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues());

        // Transaction cache - long TTL, high consistency requirements
        configurations.put("transactions", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(2))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer())));

        // Analytics cache - very long TTL, computed data
        configurations.put("analytics", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(6))
                .disableCachingNullValues());

        // Search results cache - short TTL, high volume
        configurations.put("search", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues());

        // Configuration cache - very long TTL, rarely changes
        configurations.put("config", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(12))
                .disableCachingNullValues());

        // Session cache - medium TTL, user-specific
        configurations.put("sessions", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(60))
                .disableCachingNullValues());

        // Rate limiting cache - very short TTL, high frequency
        configurations.put("ratelimit", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(1))
                .disableCachingNullValues());

        // File metadata cache - long TTL, rarely changes
        configurations.put("files", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(4))
                .disableCachingNullValues());

        // API response cache - medium TTL, external data
        configurations.put("api_responses", RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(20))
                .disableCachingNullValues());

        return configurations;
    }

    /**
     * Cache properties configuration.
     */
    @Component
    @ConfigurationProperties(prefix = "waqiti.cache")
    public static class CacheProperties {

        /**
         * Default TTL for cache entries in minutes
         */
        @Min(value = 1, message = "Default TTL must be at least 1 minute")
        private long defaultTtlMinutes = 30;

        /**
         * Cache names for L1 (local) caching
         */
        private java.util.List<String> l1CacheNames = java.util.List.of(
                "auth", "sessions", "ratelimit", "config"
        );

        /**
         * Cache names for L2 (Redis) caching
         */
        private java.util.List<String> l2CacheNames = java.util.List.of(
                "users", "transactions", "analytics", "search", "files", "api_responses"
        );

        /**
         * Enable cache statistics collection
         */
        private boolean statisticsEnabled = true;

        /**
         * Enable cache warming on startup
         */
        private boolean warmupEnabled = true;

        /**
         * Redis key prefix for all cache entries
         */
        @NotBlank(message = "Redis key prefix cannot be blank")
        private String redisKeyPrefix = "waqiti:cache:";

        /**
         * Maximum size for L1 caches
         */
        @Min(value = 100, message = "L1 cache max size must be at least 100")
        private int l1MaxSize = 10000;

        /**
         * Enable compression for large cache values
         */
        private boolean compressionEnabled = true;

        /**
         * Compression threshold in bytes
         */
        @Min(value = 1024, message = "Compression threshold must be at least 1KB")
        private int compressionThresholdBytes = 1024;

        /**
         * Cache access logging configuration
         */
        private CacheLogging logging = new CacheLogging();

        /**
         * Cache eviction policies
         */
        private CacheEviction eviction = new CacheEviction();

        /**
         * Cache warming configuration
         */
        private CacheWarmup warmup = new CacheWarmup();

        // Getters and setters
        public long getDefaultTtlMinutes() { return defaultTtlMinutes; }
        public void setDefaultTtlMinutes(long defaultTtlMinutes) { this.defaultTtlMinutes = defaultTtlMinutes; }

        public java.util.List<String> getL1CacheNames() { return l1CacheNames; }
        public void setL1CacheNames(java.util.List<String> l1CacheNames) { this.l1CacheNames = l1CacheNames; }

        public java.util.List<String> getL2CacheNames() { return l2CacheNames; }
        public void setL2CacheNames(java.util.List<String> l2CacheNames) { this.l2CacheNames = l2CacheNames; }

        public boolean isStatisticsEnabled() { return statisticsEnabled; }
        public void setStatisticsEnabled(boolean statisticsEnabled) { this.statisticsEnabled = statisticsEnabled; }

        public boolean isWarmupEnabled() { return warmupEnabled; }
        public void setWarmupEnabled(boolean warmupEnabled) { this.warmupEnabled = warmupEnabled; }

        public String getRedisKeyPrefix() { return redisKeyPrefix; }
        public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }

        public int getL1MaxSize() { return l1MaxSize; }
        public void setL1MaxSize(int l1MaxSize) { this.l1MaxSize = l1MaxSize; }

        public boolean isCompressionEnabled() { return compressionEnabled; }
        public void setCompressionEnabled(boolean compressionEnabled) { this.compressionEnabled = compressionEnabled; }

        public int getCompressionThresholdBytes() { return compressionThresholdBytes; }
        public void setCompressionThresholdBytes(int compressionThresholdBytes) { this.compressionThresholdBytes = compressionThresholdBytes; }

        public CacheLogging getLogging() { return logging; }
        public void setLogging(CacheLogging logging) { this.logging = logging; }

        public CacheEviction getEviction() { return eviction; }
        public void setEviction(CacheEviction eviction) { this.eviction = eviction; }

        public CacheWarmup getWarmup() { return warmup; }
        public void setWarmup(CacheWarmup warmup) { this.warmup = warmup; }

        /**
         * Cache logging configuration.
         */
        public static class CacheLogging {
            private boolean enabled = false;
            private boolean logHits = false;
            private boolean logMisses = true;
            private boolean logEvictions = true;
            private String logLevel = "DEBUG";

            // Getters and setters
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public boolean isLogHits() { return logHits; }
            public void setLogHits(boolean logHits) { this.logHits = logHits; }

            public boolean isLogMisses() { return logMisses; }
            public void setLogMisses(boolean logMisses) { this.logMisses = logMisses; }

            public boolean isLogEvictions() { return logEvictions; }
            public void setLogEvictions(boolean logEvictions) { this.logEvictions = logEvictions; }

            public String getLogLevel() { return logLevel; }
            public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
        }

        /**
         * Cache eviction policies.
         */
        public static class CacheEviction {
            private String policy = "LRU";
            private double memoryThreshold = 0.8;
            private int maxIdleTimeMinutes = 60;
            private boolean backgroundEviction = true;

            // Getters and setters
            public String getPolicy() { return policy; }
            public void setPolicy(String policy) { this.policy = policy; }

            public double getMemoryThreshold() { return memoryThreshold; }
            public void setMemoryThreshold(double memoryThreshold) { this.memoryThreshold = memoryThreshold; }

            public int getMaxIdleTimeMinutes() { return maxIdleTimeMinutes; }
            public void setMaxIdleTimeMinutes(int maxIdleTimeMinutes) { this.maxIdleTimeMinutes = maxIdleTimeMinutes; }

            public boolean isBackgroundEviction() { return backgroundEviction; }
            public void setBackgroundEviction(boolean backgroundEviction) { this.backgroundEviction = backgroundEviction; }
        }

        /**
         * Cache warming configuration.
         */
        public static class CacheWarmup {
            private boolean enabled = true;
            private int batchSize = 100;
            private int delayBetweenBatchesMs = 100;
            private java.util.List<String> warmupCaches = java.util.List.of("users", "config");
            private String warmupStrategy = "EAGER";

            // Getters and setters
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public int getBatchSize() { return batchSize; }
            public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

            public int getDelayBetweenBatchesMs() { return delayBetweenBatchesMs; }
            public void setDelayBetweenBatchesMs(int delayBetweenBatchesMs) { this.delayBetweenBatchesMs = delayBetweenBatchesMs; }

            public java.util.List<String> getWarmupCaches() { return warmupCaches; }
            public void setWarmupCaches(java.util.List<String> warmupCaches) { this.warmupCaches = warmupCaches; }

            public String getWarmupStrategy() { return warmupStrategy; }
            public void setWarmupStrategy(String warmupStrategy) { this.warmupStrategy = warmupStrategy; }
        }
    }
}