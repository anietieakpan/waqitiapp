package com.waqiti.common.config;

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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Production Fraud Detection Cache Configuration
 * 
 * Provides enterprise-grade caching infrastructure for fraud detection services
 * with Redis-based distributed caching and local Caffeine caching for optimal performance.
 * 
 * Features:
 * - Redis-based distributed caching for fraud data sharing across instances
 * - Local Caffeine caching for high-frequency lookups
 * - Configurable TTL values for different cache types
 * - Cache-aside pattern implementation
 * - Monitoring and metrics integration
 * - Fallback mechanisms for cache failures
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-18
 */
@Slf4j
@Configuration
@EnableCaching
public class FraudDetectionCacheConfiguration {

    @Value("${fraud.cache.routing.ttl:3600}")
    private long routingCacheTtlSeconds;

    @Value("${fraud.cache.behavior.ttl:1800}")
    private long behaviorCacheTtlSeconds;

    @Value("${fraud.cache.geolocation.ttl:600}")
    private long geolocationCacheTtlSeconds;

    @Value("${fraud.cache.ml.ttl:300}")
    private long mlCacheTtlSeconds;

    /**
     * Primary cache manager for fraud detection using Redis
     */
    @Bean(name = "fraudCacheManager")
    @Primary
    @ConditionalOnMissingBean(name = "fraudCacheManager")
    public CacheManager fraudCacheManager(RedisConnectionFactory redisConnectionFactory) {
        log.info("Creating fraud detection Redis cache manager");
        
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(routingCacheTtlSeconds))
            .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues()
            .prefixCacheNameWith("fraud:");

        // Configure specific cache TTL values
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Routing number fraud cache - longer TTL since fraud data changes infrequently
        cacheConfigurations.put("routing-fraud-cache", 
            defaultConfig.entryTtl(Duration.ofSeconds(routingCacheTtlSeconds)));
        
        // User behavior profile cache - moderate TTL as behavior patterns evolve
        cacheConfigurations.put("userBehaviorProfile", 
            defaultConfig.entryTtl(Duration.ofSeconds(behaviorCacheTtlSeconds)));
        
        // Geolocation cache - shorter TTL as location data can change
        cacheConfigurations.put("geolocation-cache", 
            defaultConfig.entryTtl(Duration.ofSeconds(geolocationCacheTtlSeconds)));
        
        // ML model predictions cache - shortest TTL for dynamic predictions
        cacheConfigurations.put("ml-predictions", 
            defaultConfig.entryTtl(Duration.ofSeconds(mlCacheTtlSeconds)));
        
        // Velocity tracking cache - short TTL for real-time velocity checks
        cacheConfigurations.put("velocity-cache", 
            defaultConfig.entryTtl(Duration.ofSeconds(300)));
        
        // Risk profile cache - medium TTL for user risk assessments
        cacheConfigurations.put("risk-profile-cache", 
            defaultConfig.entryTtl(Duration.ofSeconds(1800)));

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
    }

    /**
     * Local cache manager for high-frequency fraud checks using Caffeine
     */
    @Bean(name = "localFraudCacheManager")
    @ConditionalOnMissingBean(name = "localFraudCacheManager")
    public CacheManager localFraudCacheManager() {
        log.info("Creating local fraud detection Caffeine cache manager");
        
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configure cache specifications for different fraud check types
        cacheManager.setCacheSpecification("maximumSize=10000,expireAfterWrite=5m,recordStats");
        
        // Pre-create commonly used caches
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "fraud-routing-local",
            "fraud-velocity-local", 
            "fraud-behavior-local",
            "fraud-geolocation-local"
        ));
        
        return cacheManager;
    }

    /**
     * Specialized Redis template for fraud detection data
     */
    @Bean(name = "fraudRedisTemplate")
    @ConditionalOnMissingBean(name = "fraudRedisTemplate")
    public RedisTemplate<String, Object> fraudRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("Creating specialized Redis template for fraud detection");
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        
        // Configure serializers for optimal fraud data storage
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        
        return template;
    }

    /**
     * Cache metrics and monitoring configuration
     */
    @Bean
    @ConditionalOnMissingBean
    public FraudCacheMetrics fraudCacheMetrics() {
        return new FraudCacheMetrics();
    }

    /**
     * Fraud cache health indicator
     */
    @Bean
    @ConditionalOnMissingBean
    public FraudCacheHealthIndicator fraudCacheHealthIndicator(
            CacheManager fraudCacheManager,
            RedisTemplate<String, Object> fraudRedisTemplate) {
        return new FraudCacheHealthIndicator(fraudCacheManager, fraudRedisTemplate);
    }

    /**
     * Cache warming service for critical fraud data
     */
    @Bean
    @ConditionalOnMissingBean
    public FraudCacheWarmupService fraudCacheWarmupService(
            CacheManager fraudCacheManager,
            RedisTemplate<String, Object> fraudRedisTemplate) {
        return new FraudCacheWarmupService(fraudCacheManager, fraudRedisTemplate);
    }

    /**
     * Fraud cache metrics collection
     */
    public static class FraudCacheMetrics {
        private volatile long cacheHits = 0;
        private volatile long cacheMisses = 0;
        private volatile long cacheErrors = 0;

        public void recordHit() {
            cacheHits++;
        }

        public void recordMiss() {
            cacheMisses++;
        }

        public void recordError() {
            cacheErrors++;
        }

        public double getHitRatio() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }

        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public long getCacheErrors() { return cacheErrors; }
    }

    /**
     * Health indicator for fraud detection caches
     */
    public static class FraudCacheHealthIndicator {
        private final CacheManager cacheManager;
        private final RedisTemplate<String, Object> redisTemplate;

        public FraudCacheHealthIndicator(CacheManager cacheManager, 
                                       RedisTemplate<String, Object> redisTemplate) {
            this.cacheManager = cacheManager;
            this.redisTemplate = redisTemplate;
        }

        public boolean isHealthy() {
            try {
                // Test Redis connectivity
                redisTemplate.hasKey("health-check");
                
                // Test cache manager
                org.springframework.cache.Cache cache = cacheManager.getCache("routing-fraud-cache");
                if (cache != null) {
                    cache.put("health-check", "ok");
                    return "ok".equals(cache.get("health-check", String.class));
                }
                
                return false;
            } catch (Exception e) {
                log.error("Fraud cache health check failed", e);
                return false;
            }
        }

        public Map<String, Object> getHealthDetails() {
            Map<String, Object> details = new HashMap<>();
            details.put("cacheManager", cacheManager.getClass().getSimpleName());
            details.put("healthy", isHealthy());
            details.put("timestamp", System.currentTimeMillis());
            return details;
        }
    }

    /**
     * Service for warming up critical fraud detection caches
     */
    public static class FraudCacheWarmupService {
        private final CacheManager cacheManager;
        private final RedisTemplate<String, Object> redisTemplate;

        public FraudCacheWarmupService(CacheManager cacheManager, 
                                     RedisTemplate<String, Object> redisTemplate) {
            this.cacheManager = cacheManager;
            this.redisTemplate = redisTemplate;
        }

        public void warmupCaches() {
            try {
                log.info("Starting fraud detection cache warmup");
                
                // Warmup routing fraud cache with known fraudulent routing numbers
                warmupRoutingFraudCache();
                
                // Warmup geolocation cache with common locations
                warmupGeolocationCache();
                
                // Warmup ML model cache with common feature patterns
                warmupMLCache();
                
                log.info("Fraud detection cache warmup completed");
                
            } catch (Exception e) {
                log.error("Failed to warmup fraud detection caches", e);
            }
        }

        private void warmupRoutingFraudCache() {
            org.springframework.cache.Cache cache = cacheManager.getCache("routing-fraud-cache");
            if (cache != null) {
                // Pre-load known test/invalid routing numbers
                String[] testRoutingNumbers = {
                    "000000000", "123456789", "111111111", "222222222", "999999999"
                };
                
                for (String routingNumber : testRoutingNumbers) {
                    cache.put(routingNumber, true); // Mark as fraudulent
                }
                
                log.debug("Warmed up routing fraud cache with {} entries", testRoutingNumbers.length);
            }
        }

        private void warmupGeolocationCache() {
            org.springframework.cache.Cache cache = cacheManager.getCache("geolocation-cache");
            if (cache != null) {
                // Pre-load common high-risk IP patterns
                Map<String, Boolean> highRiskPatterns = Map.of(
                    "10.0.0.0", true,
                    "192.168.0.0", true,
                    "172.16.0.0", true
                );
                
                highRiskPatterns.forEach(cache::put);
                log.debug("Warmed up geolocation cache with {} entries", highRiskPatterns.size());
            }
        }

        private void warmupMLCache() {
            org.springframework.cache.Cache cache = cacheManager.getCache("ml-predictions");
            if (cache != null) {
                // Pre-compute predictions for common feature combinations
                Map<String, Double> commonPredictions = Map.of(
                    "low-risk-pattern", 0.1,
                    "medium-risk-pattern", 0.5,
                    "high-risk-pattern", 0.9
                );
                
                commonPredictions.forEach(cache::put);
                log.debug("Warmed up ML cache with {} entries", commonPredictions.size());
            }
        }
    }
}