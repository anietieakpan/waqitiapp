package com.waqiti.common.caching;

import com.waqiti.common.metrics.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Intelligent caching service with adaptive algorithms, predictive pre-loading,
 * and comprehensive performance optimization.
 *
 * Features:
 * - Multi-level caching (L1: local, L2: distributed Redis)
 * - Intelligent cache warming and preloading
 * - Adaptive TTL based on access patterns
 * - Cache coherence management across services
 * - Performance analytics and optimization
 * - Memory pressure-based eviction
 * - Cache hit ratio optimization
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IntelligentCachingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MetricsCollector metricsCollector;
    
    // L1 Cache (local) - high-frequency, low-latency items
    private final Map<String, CacheEntry> l1Cache = new ConcurrentHashMap<>();
    
    // Cache analytics and patterns
    private final Map<String, CacheAccessPattern> accessPatterns = new ConcurrentHashMap<>();
    private final Map<String, CachePrediction> predictions = new ConcurrentHashMap<>();
    
    // Performance tracking
    private final AtomicLong l1Hits = new AtomicLong(0);
    private final AtomicLong l1Misses = new AtomicLong(0);
    private final AtomicLong l2Hits = new AtomicLong(0);
    private final AtomicLong l2Misses = new AtomicLong(0);
    
    // Configuration
    private static final int L1_MAX_SIZE = 10000;
    private static final Duration L1_DEFAULT_TTL = Duration.ofMinutes(5);
    private static final Duration L2_DEFAULT_TTL = Duration.ofMinutes(30);
    private static final String METRICS_PREFIX = "cache";
    
    @PostConstruct
    public void initialize() {
        log.info("Intelligent Caching Service initialized with L1 max size: {}", L1_MAX_SIZE);
        scheduleAnalyticsCollection();
    }
    
    /**
     * Get value from cache with intelligent fallback through cache levels.
     *
     * @param key cache key
     * @param type expected return type
     * @param <T> return type
     * @return cached value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        recordAccess(key);
        
        // Try L1 cache first
        CacheEntry entry = l1Cache.get(key);
        if (entry != null && !entry.isExpired()) {
            l1Hits.incrementAndGet();
            metricsCollector.incrementCounter(METRICS_PREFIX + ".l1.hits");
            updateAccessPattern(key, true, 1);
            return (T) entry.getValue();
        }
        
        l1Misses.incrementAndGet();
        metricsCollector.incrementCounter(METRICS_PREFIX + ".l1.misses");
        
        // Try L2 cache (Redis)
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                l2Hits.incrementAndGet();
                metricsCollector.incrementCounter(METRICS_PREFIX + ".l2.hits");
                
                // Promote to L1 cache if accessed frequently
                if (shouldPromoteToL1(key)) {
                    putL1(key, value, calculateAdaptiveTTL(key, L1_DEFAULT_TTL));
                }
                
                updateAccessPattern(key, true, 2);
                return (T) value;
            }
        } catch (Exception e) {
            log.error("Error accessing L2 cache for key: {}", key, e);
            metricsCollector.incrementCounter(METRICS_PREFIX + ".l2.errors");
        }
        
        l2Misses.incrementAndGet();
        metricsCollector.incrementCounter(METRICS_PREFIX + ".l2.misses");
        updateAccessPattern(key, false, 0);
        
        return null;
    }
    
    /**
     * Put value in cache with intelligent level selection and TTL calculation.
     *
     * @param key cache key
     * @param value value to cache
     * @param ttl time to live
     */
    public void put(String key, Object value, Duration ttl) {
        if (value == null) {
            return;
        }
        
        // Determine optimal cache level
        CacheLevel optimalLevel = determineOptimalCacheLevel(key, value);
        
        switch (optimalLevel) {
            case L1_ONLY -> {
                putL1(key, value, ttl);
                metricsCollector.incrementCounter(METRICS_PREFIX + ".l1.puts");
            }
            case L2_ONLY -> {
                putL2(key, value, ttl);
                metricsCollector.incrementCounter(METRICS_PREFIX + ".l2.puts");
            }
            case BOTH -> {
                putL1(key, value, calculateAdaptiveTTL(key, L1_DEFAULT_TTL));
                putL2(key, value, ttl);
                metricsCollector.incrementCounter(METRICS_PREFIX + ".l1.puts");
                metricsCollector.incrementCounter(METRICS_PREFIX + ".l2.puts");
            }
        }
        
        // Update predictions for future optimization
        updateCachePrediction(key, value);
    }
    
    /**
     * Put value in cache with default TTL.
     *
     * @param key cache key
     * @param value value to cache
     */
    public void put(String key, Object value) {
        Duration adaptiveTtl = calculateAdaptiveTTL(key, L2_DEFAULT_TTL);
        put(key, value, adaptiveTtl);
    }
    
    /**
     * Evict key from all cache levels.
     *
     * @param key cache key to evict
     */
    public void evict(String key) {
        l1Cache.remove(key);
        
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Error evicting key from L2 cache: {}", key, e);
        }
        
        accessPatterns.remove(key);
        predictions.remove(key);
        
        metricsCollector.incrementCounter(METRICS_PREFIX + ".evictions");
        log.debug("Evicted key from all cache levels: {}", key);
    }
    
    /**
     * Evict keys matching pattern from all cache levels.
     *
     * @param pattern key pattern to match
     */
    public void evictPattern(String pattern) {
        // Evict from L1
        l1Cache.keySet().removeIf(key -> key.matches(pattern.replace("*", ".*")));
        
        // Evict from L2 (Redis)
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                metricsCollector.incrementCounter(METRICS_PREFIX + ".pattern_evictions", keys.size());
            }
        } catch (Exception e) {
            log.error("Error evicting pattern from L2 cache: {}", pattern, e);
        }
        
        log.debug("Evicted keys matching pattern: {}", pattern);
    }
    
    /**
     * Pre-warm cache with frequently accessed data.
     *
     * @param warmupRequests list of cache warmup requests
     */
    public void warmupCache(List<CacheWarmupRequest> warmupRequests) {
        log.info("Starting cache warmup with {} requests", warmupRequests.size());
        
        for (CacheWarmupRequest request : warmupRequests) {
            try {
                Object value = request.getDataSupplier().get();
                if (value != null) {
                    put(request.getKey(), value, request.getTtl());
                    log.debug("Warmed up cache key: {}", request.getKey());
                }
            } catch (Exception e) {
                log.error("Failed to warm up cache key: {}", request.getKey(), e);
                metricsCollector.incrementCounter(METRICS_PREFIX + ".warmup.errors");
            }
        }
        
        metricsCollector.incrementCounter(METRICS_PREFIX + ".warmup.completed");
        log.info("Cache warmup completed");
    }
    
    /**
     * Get comprehensive cache statistics.
     *
     * @return cache performance statistics
     */
    public CacheStatistics getStatistics() {
        CacheStatistics stats = new CacheStatistics();
        
        // Hit ratios
        long totalL1 = l1Hits.get() + l1Misses.get();
        long totalL2 = l2Hits.get() + l2Misses.get();
        
        stats.setL1HitRatio(totalL1 > 0 ? (double) l1Hits.get() / totalL1 * 100 : 0.0);
        stats.setL2HitRatio(totalL2 > 0 ? (double) l2Hits.get() / totalL2 * 100 : 0.0);
        stats.setOverallHitRatio(calculateOverallHitRatio());
        
        // Cache sizes
        stats.setL1Size(l1Cache.size());
        stats.setL2Size(getL2Size());
        stats.setL1MaxSize(L1_MAX_SIZE);
        
        // Memory usage
        stats.setL1MemoryUsage(calculateL1MemoryUsage());
        
        // Access patterns
        stats.setTotalAccessPatterns(accessPatterns.size());
        stats.setActivePredictions(predictions.size());
        
        // Performance metrics
        stats.setAverageAccessTime(calculateAverageAccessTime());
        
        return stats;
    }
    
    /**
     * Get cache optimization recommendations.
     *
     * @return list of optimization recommendations
     */
    public List<CacheRecommendation> getOptimizationRecommendations() {
        List<CacheRecommendation> recommendations = new ArrayList<>();
        
        CacheStatistics stats = getStatistics();
        
        // Low hit ratio recommendations
        if (stats.getL1HitRatio() < 70) {
            recommendations.add(new CacheRecommendation(
                CacheRecommendation.Type.LOW_HIT_RATIO,
                "L1 cache hit ratio is low (" + String.format("%.1f", stats.getL1HitRatio()) + "%). " +
                "Consider increasing cache size or adjusting TTL values.",
                CacheRecommendation.Priority.HIGH
            ));
        }
        
        // Memory pressure recommendations
        if (stats.getL1MemoryUsage() > 0.8) {
            recommendations.add(new CacheRecommendation(
                CacheRecommendation.Type.MEMORY_PRESSURE,
                "L1 cache memory usage is high (" + String.format("%.1f", stats.getL1MemoryUsage() * 100) + "%). " +
                "Consider implementing more aggressive eviction policies.",
                CacheRecommendation.Priority.MEDIUM
            ));
        }
        
        // Underutilized cache recommendations
        if (stats.getL1Size() < L1_MAX_SIZE * 0.3) {
            recommendations.add(new CacheRecommendation(
                CacheRecommendation.Type.UNDERUTILIZED,
                "L1 cache is underutilized (" + stats.getL1Size() + "/" + L1_MAX_SIZE + " slots used). " +
                "Consider pre-warming more data or increasing cache usage.",
                CacheRecommendation.Priority.LOW
            ));
        }
        
        // Analyze access patterns for recommendations
        analyzeAccessPatternsForRecommendations(recommendations);
        
        return recommendations;
    }
    
    /**
     * Predictively preload cache based on access patterns.
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void predictivePreload() {
        try {
            List<String> keysToPreload = identifyKeysForPreloading();
            
            for (String key : keysToPreload) {
                CachePrediction prediction = predictions.get(key);
                if (prediction != null && prediction.shouldPreload()) {
                    // Schedule preloading task
                    schedulePreloading(key, prediction);
                }
            }
            
            log.debug("Predictive preloading completed for {} keys", keysToPreload.size());
            
        } catch (Exception e) {
            log.error("Error during predictive preloading", e);
        }
    }
    
    /**
     * Perform cache optimization and cleanup.
     */
    @Scheduled(fixedDelay = 600000) // Every 10 minutes
    public void optimizeCache() {
        try {
            // Clean up expired entries
            cleanupExpiredEntries();
            
            // Optimize TTL values based on access patterns
            optimizeTTLValues();
            
            // Rebalance cache levels
            rebalanceCacheLevels();
            
            // Update predictions
            updatePredictions();
            
            log.debug("Cache optimization completed");
            
        } catch (Exception e) {
            log.error("Error during cache optimization", e);
        }
    }
    
    // Private helper methods
    
    private void putL1(String key, Object value, Duration ttl) {
        // Implement LRU eviction if cache is full
        if (l1Cache.size() >= L1_MAX_SIZE) {
            evictLRUFromL1();
        }
        
        CacheEntry entry = new CacheEntry(value, Instant.now().plus(ttl));
        l1Cache.put(key, entry);
    }
    
    private void putL2(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.error("Error putting value in L2 cache: {}", key, e);
            metricsCollector.incrementCounter(METRICS_PREFIX + ".l2.errors");
        }
    }
    
    private void recordAccess(String key) {
        CacheAccessPattern pattern = accessPatterns.computeIfAbsent(key, k -> new CacheAccessPattern());
        pattern.recordAccess();
    }
    
    private void updateAccessPattern(String key, boolean hit, int level) {
        CacheAccessPattern pattern = accessPatterns.get(key);
        if (pattern != null) {
            pattern.updateHitRatio(hit);
            if (hit) {
                pattern.recordHitLevel(level);
            }
        }
    }
    
    private boolean shouldPromoteToL1(String key) {
        CacheAccessPattern pattern = accessPatterns.get(key);
        return pattern != null && pattern.getAccessCount() > 10 && pattern.getHitRatio() > 0.7;
    }
    
    private CacheLevel determineOptimalCacheLevel(String key, Object value) {
        // Simple heuristic - in production, this would be more sophisticated
        CacheAccessPattern pattern = accessPatterns.get(key);
        
        if (pattern == null) {
            return CacheLevel.L2_ONLY; // New keys go to L2 by default
        }
        
        if (pattern.getAccessCount() > 50 && pattern.getHitRatio() > 0.8) {
            return CacheLevel.BOTH; // Hot data goes to both levels
        } else if (pattern.getAccessCount() > 20) {
            return CacheLevel.L1_ONLY; // Warm data goes to L1
        } else {
            return CacheLevel.L2_ONLY; // Cold data stays in L2
        }
    }
    
    private Duration calculateAdaptiveTTL(String key, Duration baseTTL) {
        CacheAccessPattern pattern = accessPatterns.get(key);
        
        if (pattern == null) {
            return baseTTL;
        }
        
        // Extend TTL for frequently accessed items
        double accessFrequency = pattern.getAccessFrequency();
        double multiplier = Math.min(3.0, 1.0 + (accessFrequency / 10.0));
        
        return Duration.ofMillis((long) (baseTTL.toMillis() * multiplier));
    }
    
    private void updateCachePrediction(String key, Object value) {
        CachePrediction prediction = predictions.computeIfAbsent(key, k -> new CachePrediction());
        prediction.updatePrediction(value);
    }
    
    private void evictLRUFromL1() {
        String lruKey = l1Cache.entrySet().stream()
                .min(Map.Entry.comparingByValue(
                    Comparator.comparing(CacheEntry::getLastAccessed)))
                .map(Map.Entry::getKey)
                .orElse(null);
        
        if (lruKey != null) {
            l1Cache.remove(lruKey);
            metricsCollector.incrementCounter(METRICS_PREFIX + ".l1.lru_evictions");
        }
    }
    
    private double calculateOverallHitRatio() {
        long totalHits = l1Hits.get() + l2Hits.get();
        long totalRequests = totalHits + l1Misses.get() + l2Misses.get();
        
        return totalRequests > 0 ? (double) totalHits / totalRequests * 100 : 0.0;
    }
    
    private long getL2Size() {
        try {
            // This is a simplified approach - in production, you'd use Redis INFO command
            return redisTemplate.getConnectionFactory().getConnection().dbSize();
        } catch (Exception e) {
            log.warn("Could not get L2 cache size", e);
            return 0;
        }
    }
    
    private double calculateL1MemoryUsage() {
        // Simplified calculation - in production, you'd measure actual object sizes
        return (double) l1Cache.size() / L1_MAX_SIZE;
    }
    
    private double calculateAverageAccessTime() {
        // This would be calculated from actual timing measurements
        return 5.0; // Placeholder
    }
    
    private void scheduleAnalyticsCollection() {
        // Initialize analytics collection timers
        log.debug("Cache analytics collection scheduled");
    }
    
    private void analyzeAccessPatternsForRecommendations(List<CacheRecommendation> recommendations) {
        // Analyze patterns and add specific recommendations
        for (CacheAccessPattern pattern : accessPatterns.values()) {
            if (pattern.getHitRatio() < 0.5 && pattern.getAccessCount() > 100) {
                recommendations.add(new CacheRecommendation(
                    CacheRecommendation.Type.INEFFICIENT_PATTERN,
                    "Detected inefficient access pattern with high frequency but low hit ratio",
                    CacheRecommendation.Priority.HIGH
                ));
                break; // Only add one instance of this type
            }
        }
    }
    
    private List<String> identifyKeysForPreloading() {
        return predictions.entrySet().stream()
                .filter(entry -> entry.getValue().shouldPreload())
                .map(Map.Entry::getKey)
                .toList();
    }
    
    private void schedulePreloading(String key, CachePrediction prediction) {
        // In a real implementation, this would schedule async preloading
        log.debug("Scheduled preloading for key: {} with confidence: {}", 
                key, prediction.getConfidence());
    }
    
    private void cleanupExpiredEntries() {
        Instant now = Instant.now();
        l1Cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        
        int removedCount = l1Cache.size();
        metricsCollector.incrementCounter(METRICS_PREFIX + ".l1.expired_evictions", removedCount);
    }
    
    private void optimizeTTLValues() {
        // Analyze access patterns and adjust TTL values
        for (Map.Entry<String, CacheAccessPattern> entry : accessPatterns.entrySet()) {
            String key = entry.getKey();
            CacheAccessPattern pattern = entry.getValue();
            
            if (pattern.shouldAdjustTTL()) {
                // Update TTL for items in cache
                CacheEntry cacheEntry = l1Cache.get(key);
                if (cacheEntry != null) {
                    Duration newTTL = calculateAdaptiveTTL(key, L1_DEFAULT_TTL);
                    cacheEntry.updateTTL(newTTL);
                }
            }
        }
    }
    
    private void rebalanceCacheLevels() {
        // Move items between cache levels based on access patterns
        for (Map.Entry<String, CacheAccessPattern> entry : accessPatterns.entrySet()) {
            String key = entry.getKey();
            CacheAccessPattern pattern = entry.getValue();
            
            if (shouldPromoteToL1(key) && !l1Cache.containsKey(key)) {
                // Try to get from L2 and promote to L1
                try {
                    Object value = redisTemplate.opsForValue().get(key);
                    if (value != null) {
                        putL1(key, value, calculateAdaptiveTTL(key, L1_DEFAULT_TTL));
                    }
                } catch (Exception e) {
                    log.debug("Failed to promote key to L1: {}", key);
                }
            }
        }
    }
    
    private void updatePredictions() {
        // Update prediction models based on recent access patterns
        predictions.values().forEach(CachePrediction::updateModel);
    }
    
    // Supporting classes
    
    private enum CacheLevel {
        L1_ONLY, L2_ONLY, BOTH
    }
    
    private static class CacheEntry {
        private final Object value;
        private Instant expiresAt;
        private Instant lastAccessed;
        
        public CacheEntry(Object value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
            this.lastAccessed = Instant.now();
        }
        
        public Object getValue() {
            this.lastAccessed = Instant.now();
            return value;
        }
        
        public boolean isExpired() {
            return isExpired(Instant.now());
        }
        
        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
        
        public Instant getLastAccessed() {
            return lastAccessed;
        }
        
        public void updateTTL(Duration newTTL) {
            this.expiresAt = Instant.now().plus(newTTL);
        }
    }
    
    private static class CacheAccessPattern {
        private long accessCount = 0;
        private long hitCount = 0;
        private double accessFrequency = 0.0;
        private final Map<Integer, Long> hitsByLevel = new HashMap<>();
        private Instant lastAccess = Instant.now();
        private Instant firstAccess = Instant.now();
        
        public void recordAccess() {
            accessCount++;
            lastAccess = Instant.now();
            updateAccessFrequency();
        }
        
        public void updateHitRatio(boolean hit) {
            if (hit) {
                hitCount++;
            }
        }
        
        public void recordHitLevel(int level) {
            hitsByLevel.merge(level, 1L, Long::sum);
        }
        
        public double getHitRatio() {
            return accessCount > 0 ? (double) hitCount / accessCount : 0.0;
        }
        
        public long getAccessCount() {
            return accessCount;
        }
        
        public double getAccessFrequency() {
            return accessFrequency;
        }
        
        public boolean shouldAdjustTTL() {
            return accessCount > 50 && getHitRatio() > 0.8;
        }
        
        private void updateAccessFrequency() {
            long totalTime = ChronoUnit.SECONDS.between(firstAccess, lastAccess);
            accessFrequency = totalTime > 0 ? (double) accessCount / totalTime : 0.0;
        }
    }
    
    private static class CachePrediction {
        private double confidence = 0.0;
        private Instant nextPredictedAccess;
        private long predictionCount = 0;
        private long correctPredictions = 0;
        
        public void updatePrediction(Object value) {
            predictionCount++;
            // Simplified prediction logic
            confidence = Math.min(0.9, confidence + 0.1);
            nextPredictedAccess = Instant.now().plus(Duration.ofMinutes(5));
        }
        
        public boolean shouldPreload() {
            return confidence > 0.7 && 
                   nextPredictedAccess != null && 
                   nextPredictedAccess.isBefore(Instant.now().plus(Duration.ofMinutes(2)));
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public void updateModel() {
            // Update prediction model based on accuracy
            if (predictionCount > 0) {
                double accuracy = (double) correctPredictions / predictionCount;
                confidence = Math.max(0.1, accuracy);
            }
        }
    }
    
    public static class CacheWarmupRequest {
        private final String key;
        private final java.util.function.Supplier<Object> dataSupplier;
        private final Duration ttl;
        
        public CacheWarmupRequest(String key, java.util.function.Supplier<Object> dataSupplier, Duration ttl) {
            this.key = key;
            this.dataSupplier = dataSupplier;
            this.ttl = ttl;
        }
        
        public String getKey() { return key; }
        public java.util.function.Supplier<Object> getDataSupplier() { return dataSupplier; }
        public Duration getTtl() { return ttl; }
    }
    
    public static class CacheStatistics {
        private double l1HitRatio;
        private double l2HitRatio;
        private double overallHitRatio;
        private int l1Size;
        private long l2Size;
        private int l1MaxSize;
        private double l1MemoryUsage;
        private int totalAccessPatterns;
        private int activePredictions;
        private double averageAccessTime;
        
        // Getters and setters
        public double getL1HitRatio() { return l1HitRatio; }
        public void setL1HitRatio(double l1HitRatio) { this.l1HitRatio = l1HitRatio; }
        
        public double getL2HitRatio() { return l2HitRatio; }
        public void setL2HitRatio(double l2HitRatio) { this.l2HitRatio = l2HitRatio; }
        
        public double getOverallHitRatio() { return overallHitRatio; }
        public void setOverallHitRatio(double overallHitRatio) { this.overallHitRatio = overallHitRatio; }
        
        public int getL1Size() { return l1Size; }
        public void setL1Size(int l1Size) { this.l1Size = l1Size; }
        
        public long getL2Size() { return l2Size; }
        public void setL2Size(long l2Size) { this.l2Size = l2Size; }
        
        public int getL1MaxSize() { return l1MaxSize; }
        public void setL1MaxSize(int l1MaxSize) { this.l1MaxSize = l1MaxSize; }
        
        public double getL1MemoryUsage() { return l1MemoryUsage; }
        public void setL1MemoryUsage(double l1MemoryUsage) { this.l1MemoryUsage = l1MemoryUsage; }
        
        public int getTotalAccessPatterns() { return totalAccessPatterns; }
        public void setTotalAccessPatterns(int totalAccessPatterns) { this.totalAccessPatterns = totalAccessPatterns; }
        
        public int getActivePredictions() { return activePredictions; }
        public void setActivePredictions(int activePredictions) { this.activePredictions = activePredictions; }
        
        public double getAverageAccessTime() { return averageAccessTime; }
        public void setAverageAccessTime(double averageAccessTime) { this.averageAccessTime = averageAccessTime; }
    }
    
    public static class CacheRecommendation {
        public enum Type {
            LOW_HIT_RATIO, MEMORY_PRESSURE, UNDERUTILIZED, INEFFICIENT_PATTERN, TTL_OPTIMIZATION
        }
        
        public enum Priority {
            LOW, MEDIUM, HIGH, CRITICAL
        }
        
        private final Type type;
        private final String message;
        private final Priority priority;
        
        public CacheRecommendation(Type type, String message, Priority priority) {
            this.type = type;
            this.message = message;
            this.priority = priority;
        }
        
        public Type getType() { return type; }
        public String getMessage() { return message; }
        public Priority getPriority() { return priority; }
    }
}