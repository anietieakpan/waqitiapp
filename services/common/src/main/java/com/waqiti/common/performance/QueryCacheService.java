package com.waqiti.common.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive database query result caching service
 * Implements multi-level caching with memory and Redis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // Local memory cache for frequently accessed data
    private final Map<String, CachedQueryResult> memoryCache = new ConcurrentHashMap<>();
    private final Map<String, CacheStatistics> cacheStats = new ConcurrentHashMap<>();
    
    // Cache configuration
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final Duration USER_DATA_TTL = Duration.ofMinutes(10);
    private static final Duration TRANSACTION_TTL = Duration.ofMinutes(2);
    private static final Duration STATIC_DATA_TTL = Duration.ofHours(1);
    
    private static final int MAX_MEMORY_CACHE_SIZE = 1000;
    
    /**
     * Cache user data query result
     */
    @Cacheable(value = "userQueries", key = "#userId + ':' + #queryType")
    public <T> T cacheUserQuery(String userId, String queryType, QuerySupplier<T> querySupplier) {
        String cacheKey = generateCacheKey("user", userId, queryType);
        
        try {
            // Check memory cache first
            CachedQueryResult cached = getFromMemoryCache(cacheKey);
            if (cached != null && !cached.isExpired()) {
                recordCacheHit(cacheKey, "memory");
                return (T) cached.getData();
            }
            
            // Check Redis cache
            T redisResult = getFromRedisCache(cacheKey, querySupplier.getResultType());
            if (redisResult != null) {
                recordCacheHit(cacheKey, "redis");
                putInMemoryCache(cacheKey, redisResult, USER_DATA_TTL);
                return redisResult;
            }
            
            // Cache miss - execute query
            recordCacheMiss(cacheKey);
            T result = querySupplier.execute();
            
            // Store in both caches
            putInRedisCache(cacheKey, result, USER_DATA_TTL);
            putInMemoryCache(cacheKey, result, USER_DATA_TTL);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in user query cache for key: {}", cacheKey, e);
            return querySupplier.execute();
        }
    }
    
    /**
     * Cache transaction query result
     */
    @Cacheable(value = "transactionQueries", key = "#transactionId")
    public <T> T cacheTransactionQuery(String transactionId, QuerySupplier<T> querySupplier) {
        String cacheKey = generateCacheKey("transaction", transactionId);
        
        try {
            // Check memory cache first
            CachedQueryResult cached = getFromMemoryCache(cacheKey);
            if (cached != null && !cached.isExpired()) {
                recordCacheHit(cacheKey, "memory");
                return (T) cached.getData();
            }
            
            // Check Redis cache
            T redisResult = getFromRedisCache(cacheKey, querySupplier.getResultType());
            if (redisResult != null) {
                recordCacheHit(cacheKey, "redis");
                putInMemoryCache(cacheKey, redisResult, TRANSACTION_TTL);
                return redisResult;
            }
            
            // Cache miss - execute query
            recordCacheMiss(cacheKey);
            T result = querySupplier.execute();
            
            // Store in both caches
            putInRedisCache(cacheKey, result, TRANSACTION_TTL);
            putInMemoryCache(cacheKey, result, TRANSACTION_TTL);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in transaction query cache for key: {}", cacheKey, e);
            return querySupplier.execute();
        }
    }
    
    /**
     * Cache aggregation query result
     */
    public <T> T cacheAggregationQuery(String queryId, Map<String, Object> parameters, 
                                       QuerySupplier<T> querySupplier, Duration ttl) {
        String cacheKey = generateCacheKey("aggregation", queryId, parameters.toString());
        
        try {
            // Check memory cache first
            CachedQueryResult cached = getFromMemoryCache(cacheKey);
            if (cached != null && !cached.isExpired()) {
                recordCacheHit(cacheKey, "memory");
                return (T) cached.getData();
            }
            
            // Check Redis cache
            T redisResult = getFromRedisCache(cacheKey, querySupplier.getResultType());
            if (redisResult != null) {
                recordCacheHit(cacheKey, "redis");
                putInMemoryCache(cacheKey, redisResult, ttl);
                return redisResult;
            }
            
            // Cache miss - execute query
            recordCacheMiss(cacheKey);
            T result = querySupplier.execute();
            
            // Store in both caches
            putInRedisCache(cacheKey, result, ttl);
            putInMemoryCache(cacheKey, result, ttl);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in aggregation query cache for key: {}", cacheKey, e);
            return querySupplier.execute();
        }
    }
    
    /**
     * Cache static/reference data query
     */
    @Cacheable(value = "staticQueries", key = "#dataType")
    public <T> T cacheStaticQuery(String dataType, QuerySupplier<T> querySupplier) {
        String cacheKey = generateCacheKey("static", dataType);
        
        try {
            // Check memory cache first
            CachedQueryResult cached = getFromMemoryCache(cacheKey);
            if (cached != null && !cached.isExpired()) {
                recordCacheHit(cacheKey, "memory");
                return (T) cached.getData();
            }
            
            // Check Redis cache
            T redisResult = getFromRedisCache(cacheKey, querySupplier.getResultType());
            if (redisResult != null) {
                recordCacheHit(cacheKey, "redis");
                putInMemoryCache(cacheKey, redisResult, STATIC_DATA_TTL);
                return redisResult;
            }
            
            // Cache miss - execute query
            recordCacheMiss(cacheKey);
            T result = querySupplier.execute();
            
            // Store in both caches
            putInRedisCache(cacheKey, result, STATIC_DATA_TTL);
            putInMemoryCache(cacheKey, result, STATIC_DATA_TTL);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in static query cache for key: {}", cacheKey, e);
            return querySupplier.execute();
        }
    }
    
    /**
     * Invalidate user-related caches
     */
    @CacheEvict(value = "userQueries", key = "#userId + ':*'")
    public void invalidateUserCache(String userId) {
        String pattern = generateCacheKey("user", userId, "*");
        invalidateCachePattern(pattern);
        log.debug("Invalidated user cache for userId: {}", userId);
    }
    
    /**
     * Invalidate transaction-related caches
     */
    @CacheEvict(value = "transactionQueries", key = "#transactionId")
    public void invalidateTransactionCache(String transactionId) {
        String cacheKey = generateCacheKey("transaction", transactionId);
        memoryCache.remove(cacheKey);
        redisTemplate.delete(cacheKey);
        log.debug("Invalidated transaction cache for transactionId: {}", transactionId);
    }
    
    /**
     * Invalidate all caches
     */
    @CacheEvict(value = {"userQueries", "transactionQueries", "staticQueries"}, allEntries = true)
    public void invalidateAllCaches() {
        memoryCache.clear();
        Set<String> keys = redisTemplate.keys("query:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("Invalidated all query caches");
    }
    
    /**
     * Get cache statistics
     */
    public QueryCacheStatistics getStatistics() {
        long totalHits = cacheStats.values().stream()
            .mapToLong(stats -> stats.getHits())
            .sum();
            
        long totalMisses = cacheStats.values().stream()
            .mapToLong(stats -> stats.getMisses())
            .sum();
            
        double hitRate = totalHits + totalMisses > 0 
            ? (double) totalHits / (totalHits + totalMisses) * 100 
            : 0;
            
        return QueryCacheStatistics.builder()
            .totalHits(totalHits)
            .totalMisses(totalMisses)
            .hitRate(hitRate)
            .memoryCacheSize(memoryCache.size())
            .memoryCacheMaxSize(MAX_MEMORY_CACHE_SIZE)
            .cacheKeyCount(cacheStats.size())
            .topCacheKeys(getTopCacheKeys(5))
            .build();
    }
    
    // Private helper methods
    
    private String generateCacheKey(String type, String... parts) {
        return "query:" + type + ":" + String.join(":", parts);
    }
    
    private CachedQueryResult getFromMemoryCache(String key) {
        CachedQueryResult result = memoryCache.get(key);
        if (result != null && result.isExpired()) {
            memoryCache.remove(key);
            return null;
        }
        return result;
    }
    
    private <T> T getFromRedisCache(String key, Class<T> type) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.convertValue(cached, type);
            }
        } catch (Exception e) {
            log.debug("Error retrieving from Redis cache: {}", e.getMessage());
        }
        return null;
    }
    
    private void putInMemoryCache(String key, Object data, Duration ttl) {
        // Implement LRU eviction if cache is full
        if (memoryCache.size() >= MAX_MEMORY_CACHE_SIZE) {
            evictOldestEntry();
        }
        
        memoryCache.put(key, CachedQueryResult.builder()
            .data(data)
            .timestamp(System.currentTimeMillis())
            .ttl(ttl.toMillis())
            .build());
    }
    
    private void putInRedisCache(String key, Object data, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, data, ttl);
        } catch (Exception e) {
            log.debug("Error storing in Redis cache: {}", e.getMessage());
        }
    }
    
    private void evictOldestEntry() {
        memoryCache.entrySet().stream()
            .min(Comparator.comparing(e -> e.getValue().getTimestamp()))
            .ifPresent(entry -> memoryCache.remove(entry.getKey()));
    }
    
    private void invalidateCachePattern(String pattern) {
        // Invalidate memory cache
        memoryCache.entrySet().removeIf(entry -> 
            entry.getKey().matches(pattern.replace("*", ".*")));
            
        // Invalidate Redis cache
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
    
    private void recordCacheHit(String key, String level) {
        cacheStats.computeIfAbsent(key, k -> new CacheStatistics())
            .recordHit(level);
    }
    
    private void recordCacheMiss(String key) {
        cacheStats.computeIfAbsent(key, k -> new CacheStatistics())
            .recordMiss();
    }
    
    private List<CacheKeyStats> getTopCacheKeys(int limit) {
        return cacheStats.entrySet().stream()
            .map(entry -> CacheKeyStats.builder()
                .key(entry.getKey())
                .hits(entry.getValue().getHits())
                .misses(entry.getValue().getMisses())
                .hitRate(entry.getValue().getHitRate())
                .build())
            .sorted(Comparator.comparing(CacheKeyStats::getHits).reversed())
            .limit(limit)
            .toList();
    }
    
    /**
     * Functional interface for query execution
     */
    @FunctionalInterface
    public interface QuerySupplier<T> {
        T execute();
        
        default Class<T> getResultType() {
            return (Class<T>) Object.class;
        }
    }
    
    /**
     * Cache statistics for monitoring
     */
    @lombok.Data
    private static class CacheStatistics {
        private final AtomicLong hits = new AtomicLong();
        private final AtomicLong misses = new AtomicLong();
        private final AtomicLong memoryHits = new AtomicLong();
        private final AtomicLong redisHits = new AtomicLong();
        
        public void recordHit(String level) {
            hits.incrementAndGet();
            if ("memory".equals(level)) {
                memoryHits.incrementAndGet();
            } else if ("redis".equals(level)) {
                redisHits.incrementAndGet();
            }
        }
        
        public void recordMiss() {
            misses.incrementAndGet();
        }
        
        public double getHitRate() {
            long total = hits.get() + misses.get();
            return total > 0 ? (double) hits.get() / total * 100 : 0;
        }
    }
}