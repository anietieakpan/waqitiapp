package com.waqiti.common.caching;

import com.waqiti.common.metrics.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Comprehensive cache warming service that intelligently preloads frequently
 * accessed data into cache layers for optimal performance.
 *
 * Features:
 * - Startup cache warming with prioritized data loading
 * - Scheduled periodic warming based on usage patterns
 * - Predictive warming using access pattern analysis
 * - Performance-aware warming with load balancing
 * - Metrics and monitoring for warming effectiveness
 * - Configurable warming strategies per cache type
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CacheWarmupService implements ApplicationRunner {

    private final IntelligentCachingService intelligentCachingService;
    private final CacheManager l1CacheManager;
    private final CacheManager l2CacheManager;
    private final MetricsCollector metricsCollector;
    private final CacheConfigurationManager.CacheProperties cacheProperties;

    // Warmup data providers registered by services
    private final Map<String, CacheWarmupProvider> warmupProviders = new ConcurrentHashMap<>();

    // Warmup statistics tracking
    private final Map<String, WarmupStats> warmupStats = new ConcurrentHashMap<>();

    /**
     * Runs cache warming on application startup.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (cacheProperties.isWarmupEnabled()) {
            log.info("Starting application startup cache warming");
            performStartupWarmup();
        } else {
            log.info("Cache warmup is disabled");
        }
    }

    /**
     * Registers a cache warmup provider for a specific cache.
     *
     * @param cacheName name of the cache
     * @param provider warmup data provider
     */
    public void registerWarmupProvider(String cacheName, CacheWarmupProvider provider) {
        warmupProviders.put(cacheName, provider);
        warmupStats.put(cacheName, new WarmupStats());

        log.info("Registered cache warmup provider for cache: {} with priority: {}",
                cacheName, provider.getPriority());
    }

    /**
     * Performs manual cache warming for specific cache.
     *
     * @param cacheName name of cache to warm
     * @return warmup result future
     */
    @Async
    public CompletableFuture<WarmupResult> warmCache(String cacheName) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting manual cache warming for: {}", cacheName);

            WarmupResult result = new WarmupResult(cacheName);
            result.setStartTime(Instant.now());

            try {
                CacheWarmupProvider provider = warmupProviders.get(cacheName);
                if (provider == null) {
                    result.setSuccess(false);
                    result.setErrorMessage("No warmup provider registered for cache: " + cacheName);
                    return result;
                }

                int warmedCount = performCacheWarmup(cacheName, provider);

                result.setSuccess(true);
                result.setItemsWarmed(warmedCount);
                result.setEndTime(Instant.now());

                updateWarmupStats(cacheName, warmedCount, true);

                log.info("Manual cache warming completed for {}: {} items warmed in {}ms",
                        cacheName, warmedCount, result.getDurationMs());

            } catch (Exception e) {
                log.error("Failed to warm cache: {}", cacheName, e);
                result.setSuccess(false);
                result.setErrorMessage(e.getMessage());
                result.setEndTime(Instant.now());

                updateWarmupStats(cacheName, 0, false);
            }

            return result;
        });
    }

    /**
     * Scheduled periodic cache warming based on usage patterns.
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void performScheduledWarmup() {
        if (!cacheProperties.isWarmupEnabled()) {
            return;
        }

        log.info("Starting scheduled cache warming cycle");

        try {
            List<String> cachesToWarm = identifyCachesNeedingWarmup();

            for (String cacheName : cachesToWarm) {
                if (shouldWarmCache(cacheName)) {
                    warmCache(cacheName);

                    // Add delay between cache warming to prevent system overload
                    Thread.sleep(cacheProperties.getWarmup().getDelayBetweenBatchesMs());
                }
            }

            log.info("Scheduled cache warming completed for {} caches", cachesToWarm.size());

        } catch (Exception e) {
            log.error("Error during scheduled cache warming", e);
            metricsCollector.incrementCounter("cache.warmup.scheduled.errors");
        }
    }

    /**
     * Predictive cache warming based on access patterns and upcoming load.
     */
    @Scheduled(fixedDelay = 1800000) // Every 30 minutes
    public void performPredictiveWarmup() {
        if (!cacheProperties.isWarmupEnabled()) {
            return;
        }

        log.debug("Starting predictive cache warming");

        try {
            Map<String, Double> cachePriorities = calculateWarmupPriorities();

            cachePriorities.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0.7) // High priority threshold
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(3) // Warm top 3 priority caches
                    .forEach(entry -> {
                        String cacheName = entry.getKey();
                        log.debug("Predictive warming for cache {} (priority: {})",
                                cacheName, entry.getValue());
                        warmCache(cacheName);
                    });

        } catch (Exception e) {
            log.error("Error during predictive cache warming", e);
            metricsCollector.incrementCounter("cache.warmup.predictive.errors");
        }
    }

    /**
     * Gets comprehensive warmup statistics for all caches.
     *
     * @return map of cache names to their warmup statistics
     */
    public Map<String, WarmupStats> getWarmupStatistics() {
        return new HashMap<>(warmupStats);
    }

    /**
     * Gets warmup statistics for a specific cache.
     *
     * @param cacheName name of the cache
     * @return warmup statistics or null if cache not found
     */
    public WarmupStats getCacheWarmupStats(String cacheName) {
        return warmupStats.get(cacheName);
    }

    // Private helper methods

    private void performStartupWarmup() {
        List<String> startupCaches = cacheProperties.getWarmup().getWarmupCaches();

        log.info("Performing startup cache warming for {} caches", startupCaches.size());

        // Sort providers by priority (higher priority first)
        List<Map.Entry<String, CacheWarmupProvider>> sortedProviders = warmupProviders.entrySet().stream()
                .filter(entry -> startupCaches.contains(entry.getKey()))
                .sorted(Map.Entry.<String, CacheWarmupProvider>comparingByValue(
                        Comparator.comparingInt(CacheWarmupProvider::getPriority).reversed()))
                .toList();

        int totalWarmed = 0;
        Instant startupStartTime = Instant.now();

        for (Map.Entry<String, CacheWarmupProvider> entry : sortedProviders) {
            String cacheName = entry.getKey();
            CacheWarmupProvider provider = entry.getValue();

            try {
                log.info("Warming startup cache: {} (priority: {})", cacheName, provider.getPriority());
                int warmedCount = performCacheWarmup(cacheName, provider);
                totalWarmed += warmedCount;

                updateWarmupStats(cacheName, warmedCount, true);

                // Small delay between caches to prevent startup overload
                Thread.sleep(100);

            } catch (Exception e) {
                log.error("Failed to warm startup cache: {}", cacheName, e);
                updateWarmupStats(cacheName, 0, false);
            }
        }

        long startupDuration = Duration.between(startupStartTime, Instant.now()).toMillis();
        log.info("Startup cache warming completed: {} items warmed across {} caches in {}ms",
                totalWarmed, sortedProviders.size(), startupDuration);

        metricsCollector.recordTimer("cache.warmup.startup.duration", startupDuration);
        metricsCollector.recordGauge("cache.warmup.startup.items", "total", totalWarmed);
    }

    private int performCacheWarmup(String cacheName, CacheWarmupProvider provider) {
        WarmupData warmupData = provider.getWarmupData();
        if (warmupData == null || warmupData.getEntries().isEmpty()) {
            return 0;
        }

        Cache cache = getCache(cacheName);
        if (cache == null) {
            log.warn("Cache not found: {}", cacheName);
            return 0;
        }

        List<WarmupEntry> entries = warmupData.getEntries();
        int batchSize = cacheProperties.getWarmup().getBatchSize();
        int warmedCount = 0;

        // Process entries in batches
        for (int i = 0; i < entries.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, entries.size());
            List<WarmupEntry> batch = entries.subList(i, endIndex);

            for (WarmupEntry entry : batch) {
                try {
                    Object value = entry.getValueSupplier().get();
                    if (value != null) {
                        cache.put(entry.getKey(), value);
                        warmedCount++;
                    }
                } catch (Exception e) {
                    log.debug("Failed to warm cache entry {} in cache {}: {}",
                            entry.getKey(), cacheName, e.getMessage());
                }
            }

            // Pause between batches to prevent overload
            if (endIndex < entries.size()) {
                try {
                    Thread.sleep(cacheProperties.getWarmup().getDelayBetweenBatchesMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return warmedCount;
    }

    private Cache getCache(String cacheName) {
        // Try L1 cache first
        Cache cache = l1CacheManager.getCache(cacheName);
        if (cache != null) {
            return cache;
        }

        // Try L2 cache
        return l2CacheManager.getCache(cacheName);
    }

    private List<String> identifyCachesNeedingWarmup() {
        List<String> cachesToWarm = new ArrayList<>();

        for (String cacheName : warmupProviders.keySet()) {
            WarmupStats stats = warmupStats.get(cacheName);
            if (stats != null && shouldScheduleWarmup(stats)) {
                cachesToWarm.add(cacheName);
            }
        }

        return cachesToWarm;
    }

    private boolean shouldWarmCache(String cacheName) {
        WarmupStats stats = warmupStats.get(cacheName);
        if (stats == null) {
            return true; // First time warmup
        }

        // Check if last warmup was successful and recent
        return stats.getLastWarmupTime() == null ||
                stats.getLastWarmupTime().isBefore(Instant.now().minus(Duration.ofHours(2))) ||
                !stats.isLastWarmupSuccessful();
    }

    private boolean shouldScheduleWarmup(WarmupStats stats) {
        // Schedule warmup if:
        // 1. Never warmed before
        // 2. Last warmup failed
        // 3. Last warmup was more than 4 hours ago
        // 4. Cache hit ratio is declining

        if (stats.getLastWarmupTime() == null) {
            return true;
        }

        if (!stats.isLastWarmupSuccessful()) {
            return true;
        }

        if (stats.getLastWarmupTime().isBefore(Instant.now().minus(Duration.ofHours(4)))) {
            return true;
        }

        return stats.getAverageHitRatio() < 0.7; // Low hit ratio threshold
    }

    private Map<String, Double> calculateWarmupPriorities() {
        Map<String, Double> priorities = new HashMap<>();

        for (String cacheName : warmupProviders.keySet()) {
            double priority = 0.0;
            WarmupStats stats = warmupStats.get(cacheName);

            if (stats != null) {
                // Factor in hit ratio (lower = higher priority for warmup)
                priority += (1.0 - stats.getAverageHitRatio()) * 0.4;

                // Factor in time since last warmup
                if (stats.getLastWarmupTime() != null) {
                    long hoursSinceWarmup = Duration.between(stats.getLastWarmupTime(), Instant.now()).toHours();
                    priority += Math.min(1.0, hoursSinceWarmup / 24.0) * 0.3; // Max 24 hours
                }

                // Factor in previous warmup success
                if (!stats.isLastWarmupSuccessful()) {
                    priority += 0.3;
                }
            } else {
                priority = 1.0; // Never warmed before - highest priority
            }

            priorities.put(cacheName, Math.min(1.0, priority));
        }

        return priorities;
    }

    private void updateWarmupStats(String cacheName, int itemsWarmed, boolean success) {
        WarmupStats stats = warmupStats.get(cacheName);
        if (stats == null) {
            stats = new WarmupStats();
            warmupStats.put(cacheName, stats);
        }

        stats.recordWarmup(itemsWarmed, success);

        // Update metrics
        metricsCollector.incrementCounter("cache.warmup.executions", Map.of("cache", cacheName));
        metricsCollector.recordGauge("cache.warmup.items", cacheName, itemsWarmed);

        if (success) {
            metricsCollector.incrementCounter("cache.warmup.success", Map.of("cache", cacheName));
        } else {
            metricsCollector.incrementCounter("cache.warmup.failures", Map.of("cache", cacheName));
        }
    }

    // Supporting interfaces and classes

    /**
     * Interface for providing cache warmup data.
     */
    public interface CacheWarmupProvider {
        /**
         * Gets warmup data for the cache.
         *
         * @return warmup data containing entries to preload
         */
        WarmupData getWarmupData();

        /**
         * Gets the priority of this cache for warmup (higher number = higher priority).
         *
         * @return priority value (0-100)
         */
        default int getPriority() {
            return 50; // Default medium priority
        }
    }

    /**
     * Container for cache warmup data.
     */
    public static class WarmupData {
        private final List<WarmupEntry> entries;

        public WarmupData(List<WarmupEntry> entries) {
            this.entries = entries != null ? entries : Collections.emptyList();
        }

        public List<WarmupEntry> getEntries() {
            return entries;
        }
    }

    /**
     * Individual cache entry for warmup.
     */
    public static class WarmupEntry {
        private final String key;
        private final Supplier<Object> valueSupplier;

        public WarmupEntry(String key, Supplier<Object> valueSupplier) {
            this.key = key;
            this.valueSupplier = valueSupplier;
        }

        public String getKey() {
            return key;
        }

        public Supplier<Object> getValueSupplier() {
            return valueSupplier;
        }
    }

    /**
     * Statistics tracking for cache warmup operations.
     */
    public static class WarmupStats {
        private int totalWarmups = 0;
        private int successfulWarmups = 0;
        private long totalItemsWarmed = 0;
        private Instant lastWarmupTime;
        private boolean lastWarmupSuccessful = false;
        private double averageHitRatio = 0.0;

        public void recordWarmup(int itemsWarmed, boolean success) {
            totalWarmups++;
            totalItemsWarmed += itemsWarmed;
            lastWarmupTime = Instant.now();
            lastWarmupSuccessful = success;

            if (success) {
                successfulWarmups++;
            }
        }

        public double getSuccessRate() {
            return totalWarmups > 0 ? (double) successfulWarmups / totalWarmups : 0.0;
        }

        public double getAverageItemsPerWarmup() {
            return totalWarmups > 0 ? (double) totalItemsWarmed / totalWarmups : 0.0;
        }

        // Getters
        public int getTotalWarmups() { return totalWarmups; }
        public int getSuccessfulWarmups() { return successfulWarmups; }
        public long getTotalItemsWarmed() { return totalItemsWarmed; }
        public Instant getLastWarmupTime() { return lastWarmupTime; }
        public boolean isLastWarmupSuccessful() { return lastWarmupSuccessful; }
        public double getAverageHitRatio() { return averageHitRatio; }
        public void setAverageHitRatio(double averageHitRatio) { this.averageHitRatio = averageHitRatio; }
    }

    /**
     * Result of a cache warmup operation.
     */
    public static class WarmupResult {
        private final String cacheName;
        private boolean success;
        private int itemsWarmed;
        private String errorMessage;
        private Instant startTime;
        private Instant endTime;

        public WarmupResult(String cacheName) {
            this.cacheName = cacheName;
        }

        public long getDurationMs() {
            if (startTime != null && endTime != null) {
                return Duration.between(startTime, endTime).toMillis();
            }
            return 0;
        }

        // Getters and setters
        public String getCacheName() { return cacheName; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public int getItemsWarmed() { return itemsWarmed; }
        public void setItemsWarmed(int itemsWarmed) { this.itemsWarmed = itemsWarmed; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
    }
}