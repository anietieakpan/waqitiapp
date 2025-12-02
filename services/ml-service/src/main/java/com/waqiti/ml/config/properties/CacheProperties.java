package com.waqiti.ml.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache configuration properties for ML service
 * Provides comprehensive caching configuration for both Caffeine and Redis
 */
@Data
@ConfigurationProperties(prefix = "ml.cache")
public class CacheProperties {
    
    private CaffeineConfig caffeine = new CaffeineConfig();
    private RedisConfig redis = new RedisConfig();
    private Map<String, CacheConfig> caches = new HashMap<>();
    
    @Data
    public static class CaffeineConfig {
        /**
         * Caffeine cache specification string
         * Format: maximumSize=1000,expireAfterWrite=1h,recordStats
         */
        private String spec = "maximumSize=1000,expireAfterWrite=1h,recordStats";
        
        /**
         * Maximum cache size
         */
        private long maximumSize = 1000;
        
        /**
         * Initial cache capacity
         */
        private int initialCapacity = 100;
        
        /**
         * Expire after write duration
         */
        private Duration expireAfterWrite = Duration.ofHours(1);
        
        /**
         * Expire after access duration
         */
        private Duration expireAfterAccess;
        
        /**
         * Refresh after write duration
         */
        private Duration refreshAfterWrite;
        
        /**
         * Weak keys - stores keys using weak references
         */
        private boolean weakKeys = false;
        
        /**
         * Weak values - stores values using weak references
         */
        private boolean weakValues = false;
        
        /**
         * Soft values - stores values using soft references
         */
        private boolean softValues = false;
        
        /**
         * Record statistics
         */
        private boolean recordStats = true;
        
        /**
         * Builds the Caffeine spec string from properties
         */
        public String buildSpec() {
            StringBuilder spec = new StringBuilder();
            
            if (maximumSize > 0) {
                spec.append("maximumSize=").append(maximumSize);
            }
            
            if (initialCapacity > 0) {
                if (spec.length() > 0) spec.append(",");
                spec.append("initialCapacity=").append(initialCapacity);
            }
            
            if (expireAfterWrite != null) {
                if (spec.length() > 0) spec.append(",");
                spec.append("expireAfterWrite=").append(expireAfterWrite.toSeconds()).append("s");
            }
            
            if (expireAfterAccess != null) {
                if (spec.length() > 0) spec.append(",");
                spec.append("expireAfterAccess=").append(expireAfterAccess.toSeconds()).append("s");
            }
            
            if (refreshAfterWrite != null) {
                if (spec.length() > 0) spec.append(",");
                spec.append("refreshAfterWrite=").append(refreshAfterWrite.toSeconds()).append("s");
            }
            
            if (weakKeys) {
                if (spec.length() > 0) spec.append(",");
                spec.append("weakKeys");
            }
            
            if (weakValues) {
                if (spec.length() > 0) spec.append(",");
                spec.append("weakValues");
            }
            
            if (softValues) {
                if (spec.length() > 0) spec.append(",");
                spec.append("softValues");
            }
            
            if (recordStats) {
                if (spec.length() > 0) spec.append(",");
                spec.append("recordStats");
            }
            
            return spec.toString();
        }
    }
    
    @Data
    public static class RedisConfig {
        /**
         * Default TTL in seconds
         */
        private int ttl = 3600; // 1 hour
        
        /**
         * Key prefix for all cache keys
         */
        private String keyPrefix = "ml-service:";
        
        /**
         * Enable cache statistics
         */
        private boolean enableStatistics = true;
        
        /**
         * Use Redis cluster
         */
        private boolean useCluster = false;
        
        /**
         * Cache null values
         */
        private boolean cacheNullValues = false;
        
        /**
         * Time to live for null values in seconds
         */
        private int nullValueTtl = 60;
        
        /**
         * Enable transactions
         */
        private boolean enableTransactions = false;
        
        /**
         * Lock timeout for distributed locks in milliseconds
         */
        private long lockTimeout = 5000;
        
        /**
         * Lock lease time in milliseconds
         */
        private long lockLeaseTime = 30000;
        
        /**
         * Enable compression
         */
        private boolean enableCompression = false;
        
        /**
         * Compression threshold in bytes
         */
        private int compressionThreshold = 1024;
        
        /**
         * Serialization type
         */
        private SerializationType serializationType = SerializationType.JSON;
        
        /**
         * Enable cache warming
         */
        private boolean enableCacheWarming = false;
        
        /**
         * Cache warming batch size
         */
        private int cacheWarmingBatchSize = 100;
    }
    
    @Data
    public static class CacheConfig {
        /**
         * Cache name
         */
        private String name;
        
        /**
         * Time to live in seconds
         */
        private int ttl = 3600;
        
        /**
         * Maximum cache size (for local caches)
         */
        private long maxSize = 1000;
        
        /**
         * Cache type (CAFFEINE, REDIS, or HYBRID)
         */
        private CacheType type = CacheType.HYBRID;
        
        /**
         * Enable this cache
         */
        private boolean enabled = true;
        
        /**
         * Cache statistics enabled
         */
        private boolean statisticsEnabled = true;
        
        /**
         * Expire after write duration
         */
        private Duration expireAfterWrite = Duration.ofHours(1);
        
        /**
         * Expire after access duration
         */
        private Duration expireAfterAccess;
        
        /**
         * Refresh after write duration
         */
        private Duration refreshAfterWrite;
    }
    
    /**
     * Cache type enum
     */
    public enum CacheType {
        /**
         * Local Caffeine cache only
         */
        CAFFEINE,
        
        /**
         * Redis cache only
         */
        REDIS,
        
        /**
         * Hybrid - Caffeine as L1, Redis as L2
         */
        HYBRID
    }
    
    /**
     * Serialization type for Redis
     */
    public enum SerializationType {
        /**
         * JSON serialization
         */
        JSON,
        
        /**
         * Java serialization
         */
        JAVA,
        
        /**
         * Protocol Buffers
         */
        PROTOBUF,
        
        /**
         * MessagePack
         */
        MSGPACK,
        
        /**
         * Kryo serialization
         */
        KRYO
    }
    
    /**
     * Initialize default cache configurations
     */
    public void initializeDefaultCaches() {
        // Model predictions cache
        caches.put("model-predictions", createCacheConfig(
            "model-predictions", 300, 10000, CacheType.HYBRID, Duration.ofMinutes(5)
        ));
        
        // Feature vectors cache
        caches.put("feature-vectors", createCacheConfig(
            "feature-vectors", 600, 5000, CacheType.HYBRID, Duration.ofMinutes(10)
        ));
        
        // User profiles cache
        caches.put("user-profiles", createCacheConfig(
            "user-profiles", 1800, 1000, CacheType.REDIS, Duration.ofMinutes(30)
        ));
        
        // Risk scores cache
        caches.put("risk-scores", createCacheConfig(
            "risk-scores", 60, 50000, CacheType.HYBRID, Duration.ofMinutes(1)
        ));
        
        // Model metadata cache
        caches.put("model-metadata", createCacheConfig(
            "model-metadata", 3600, 100, CacheType.CAFFEINE, Duration.ofHours(1)
        ));
        
        // Network analysis cache
        caches.put("network-analysis", createCacheConfig(
            "network-analysis", 900, 2000, CacheType.HYBRID, Duration.ofMinutes(15)
        ));
        
        // Behavioral patterns cache
        caches.put("behavioral-patterns", createCacheConfig(
            "behavioral-patterns", 1200, 3000, CacheType.HYBRID, Duration.ofMinutes(20)
        ));
    }
    
    private CacheConfig createCacheConfig(String name, int ttl, long maxSize, 
                                         CacheType type, Duration expireAfterWrite) {
        CacheConfig config = new CacheConfig();
        config.setName(name);
        config.setTtl(ttl);
        config.setMaxSize(maxSize);
        config.setType(type);
        config.setExpireAfterWrite(expireAfterWrite);
        config.setEnabled(true);
        config.setStatisticsEnabled(true);
        return config;
    }
    
    /**
     * Get cache configuration by name
     */
    public CacheConfig getCacheConfig(String cacheName) {
        return caches.getOrDefault(cacheName, createDefaultCacheConfig());
    }
    
    private CacheConfig createDefaultCacheConfig() {
        return createCacheConfig("default", 3600, 1000, CacheType.HYBRID, Duration.ofHours(1));
    }
}