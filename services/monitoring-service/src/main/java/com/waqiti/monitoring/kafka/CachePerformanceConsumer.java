package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.entity.CacheMetrics;
import com.waqiti.monitoring.repository.CacheMetricsRepository;
import com.waqiti.monitoring.service.*;
import com.waqiti.monitoring.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CachePerformanceConsumer {

    private static final String TOPIC = "cache-performance";
    private static final String GROUP_ID = "monitoring-cache-performance-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;
    private static final double HIT_RATIO_THRESHOLD = 0.80;
    private static final double EVICTION_RATE_THRESHOLD = 100.0;
    private static final long OPERATION_LATENCY_THRESHOLD_MS = 10;
    private static final double MEMORY_UTILIZATION_THRESHOLD = 0.85;
    private static final int INVALIDATION_RATE_THRESHOLD = 50;
    private static final double FRAGMENTATION_THRESHOLD = 0.30;
    private static final int HOT_KEY_THRESHOLD = 1000;
    private static final double CACHE_SIZE_GROWTH_THRESHOLD = 0.20;
    private static final int TTL_EXPIRY_RATE_THRESHOLD = 200;
    private static final double REPLICATION_LAG_THRESHOLD_MS = 100;
    private static final int WARMUP_TIME_THRESHOLD_MS = 30000;
    private static final double CONNECTION_POOL_SATURATION_THRESHOLD = 0.90;
    private static final int SERIALIZATION_ERROR_THRESHOLD = 10;
    private static final double CACHE_COHERENCE_THRESHOLD = 0.95;
    private static final int ANALYSIS_WINDOW_MINUTES = 15;
    
    private final CacheMetricsRepository metricsRepository;
    private final AlertService alertService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final CacheOptimizationService optimizationService;
    private final EvictionAnalysisService evictionAnalysisService;
    private final KeyDistributionService keyDistributionService;
    private final CacheHealthService healthService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    private final Map<String, CachePerformanceState> cacheStates = new ConcurrentHashMap<>();
    private final Map<String, HitRatioAnalyzer> hitRatioAnalyzers = new ConcurrentHashMap<>();
    private final Map<String, EvictionTracker> evictionTrackers = new ConcurrentHashMap<>();
    private final Map<String, HotKeyDetector> hotKeyDetectors = new ConcurrentHashMap<>();
    private final Map<String, MemoryAnalyzer> memoryAnalyzers = new ConcurrentHashMap<>();
    private final Map<String, LatencyTracker> latencyTrackers = new ConcurrentHashMap<>();
    private final Map<String, InvalidationMonitor> invalidationMonitors = new ConcurrentHashMap<>();
    private final Map<String, ReplicationMonitor> replicationMonitors = new ConcurrentHashMap<>();
    private final Map<String, TtlAnalyzer> ttlAnalyzers = new ConcurrentHashMap<>();
    private final Map<String, CachePattern> cachePatterns = new ConcurrentHashMap<>();
    private final Map<String, OptimizationRecommendation> recommendations = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService analysisExecutor = Executors.newFixedThreadPool(10);
    private final BlockingQueue<CacheEvent> eventQueue = new LinkedBlockingQueue<>(10000);
    
    private Counter processedEventsCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Gauge queueSizeGauge;
    private Gauge hitRatioGauge;
    private Gauge evictionRateGauge;
    private Gauge memoryUtilizationGauge;

    public CachePerformanceConsumer(CacheMetricsRepository metricsRepository,
                                    AlertService alertService,
                                    MetricsService metricsService,
                                    NotificationService notificationService,
                                    CacheOptimizationService optimizationService,
                                    EvictionAnalysisService evictionAnalysisService,
                                    KeyDistributionService keyDistributionService,
                                    CacheHealthService healthService,
                                    ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry) {
        this.metricsRepository = metricsRepository;
        this.alertService = alertService;
        this.metricsService = metricsService;
        this.notificationService = notificationService;
        this.optimizationService = optimizationService;
        this.evictionAnalysisService = evictionAnalysisService;
        this.keyDistributionService = keyDistributionService;
        this.healthService = healthService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        startBackgroundTasks();
        initializeAnalyzers();
        establishBaselines();
        log.info("CachePerformanceConsumer initialized successfully");
    }
    
    private void initializeMetrics() {
        processedEventsCounter = meterRegistry.counter("cache.performance.events.processed");
        errorCounter = meterRegistry.counter("cache.performance.events.errors");
        processingTimer = meterRegistry.timer("cache.performance.processing.time");
        queueSizeGauge = meterRegistry.gauge("cache.performance.queue.size", eventQueue, Queue::size);
        
        hitRatioGauge = meterRegistry.gauge("cache.performance.hit.ratio", 
            cacheStates, states -> calculateAverageHitRatio(states));
        evictionRateGauge = meterRegistry.gauge("cache.performance.eviction.rate",
            evictionTrackers, trackers -> calculateAverageEvictionRate(trackers));
        memoryUtilizationGauge = meterRegistry.gauge("cache.performance.memory.utilization",
            memoryAnalyzers, analyzers -> calculateAverageMemoryUtilization(analyzers));
    }
    
    private void startBackgroundTasks() {
        scheduledExecutor.scheduleAtFixedRate(this::analyzePerformanceTrends, 1, 1, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::detectAnomalies, 2, 2, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::generateOptimizationRecommendations, 5, 5, TimeUnit.MINUTES);
        scheduledExecutor.scheduleAtFixedRate(this::cleanupOldData, 1, 6, TimeUnit.HOURS);
        scheduledExecutor.scheduleAtFixedRate(this::performHealthChecks, 30, 30, TimeUnit.SECONDS);
    }
    
    private void initializeAnalyzers() {
        Arrays.asList("redis", "hazelcast", "memcached", "ehcache", "caffeine").forEach(cacheType -> {
            hitRatioAnalyzers.put(cacheType, new HitRatioAnalyzer(cacheType));
            evictionTrackers.put(cacheType, new EvictionTracker(cacheType));
            hotKeyDetectors.put(cacheType, new HotKeyDetector(cacheType));
            memoryAnalyzers.put(cacheType, new MemoryAnalyzer(cacheType));
            latencyTrackers.put(cacheType, new LatencyTracker(cacheType));
            invalidationMonitors.put(cacheType, new InvalidationMonitor(cacheType));
            replicationMonitors.put(cacheType, new ReplicationMonitor(cacheType));
            ttlAnalyzers.put(cacheType, new TtlAnalyzer(cacheType));
            cacheStates.put(cacheType, new CachePerformanceState(cacheType));
        });
    }
    
    private void establishBaselines() {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        metricsRepository.findByTimestampAfter(oneWeekAgo)
            .forEach(metric -> {
                String cacheType = metric.getCacheType();
                CachePerformanceState state = cacheStates.get(cacheType);
                if (state != null) {
                    state.updateBaseline(metric);
                }
            });
        log.info("Established baselines for {} cache types", cacheStates.size());
    }
    
    @KafkaListener(
        topics = TOPIC,
        groupId = GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "cachePerformance", fallbackMethod = "handleMessageFallback")
    @Retry(name = "cachePerformance", fallbackMethod = "handleMessageFallback")
    public void consume(
            @Payload ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        MDC.put("traceId", UUID.randomUUID().toString());
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Processing cache performance event from partition {} offset {}", partition, offset);
            
            JsonNode eventData = objectMapper.readTree(record.value());
            String eventType = eventData.get("eventType").asText();
            
            processEventByType(eventType, eventData);
            
            processedEventsCounter.increment();
            acknowledgment.acknowledge();
            
            sample.stop(processingTimer);
            
        } catch (Exception e) {
            log.error("Error processing cache performance event: {}", e.getMessage(), e);
            errorCounter.increment();
            handleProcessingError(record, e, acknowledgment);
        } finally {
            MDC.clear();
        }
    }
    
    private void processEventByType(String eventType, JsonNode eventData) {
        try {
            switch (eventType) {
                case "CACHE_HIT_MISS":
                    processCacheHitMiss(eventData);
                    break;
                case "EVICTION_EVENT":
                    processEvictionEvent(eventData);
                    break;
                case "OPERATION_LATENCY":
                    processOperationLatency(eventData);
                    break;
                case "MEMORY_USAGE":
                    processMemoryUsage(eventData);
                    break;
                case "KEY_ACCESS_PATTERN":
                    processKeyAccessPattern(eventData);
                    break;
                case "INVALIDATION_EVENT":
                    processInvalidationEvent(eventData);
                    break;
                case "TTL_EXPIRY":
                    processTtlExpiry(eventData);
                    break;
                case "CACHE_SIZE_CHANGE":
                    processCacheSizeChange(eventData);
                    break;
                case "REPLICATION_STATUS":
                    processReplicationStatus(eventData);
                    break;
                case "FRAGMENTATION_ANALYSIS":
                    processFragmentationAnalysis(eventData);
                    break;
                case "WARMUP_COMPLETE":
                    processWarmupComplete(eventData);
                    break;
                case "CONNECTION_POOL_STATUS":
                    processConnectionPoolStatus(eventData);
                    break;
                case "SERIALIZATION_ERROR":
                    processSerializationError(eventData);
                    break;
                case "CACHE_COHERENCE_CHECK":
                    processCacheCoherenceCheck(eventData);
                    break;
                case "PERFORMANCE_ANOMALY":
                    processPerformanceAnomaly(eventData);
                    break;
                default:
                    log.warn("Unknown cache performance event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing event type {}: {}", eventType, e.getMessage(), e);
            errorCounter.increment();
        }
    }
    
    private void processCacheHitMiss(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        boolean isHit = eventData.get("isHit").asBoolean();
        String key = eventData.get("key").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        HitRatioAnalyzer analyzer = hitRatioAnalyzers.get(cacheType);
        if (analyzer != null) {
            analyzer.recordAccess(cacheName, isHit, key, timestamp);
            
            double hitRatio = analyzer.getHitRatio(cacheName);
            if (hitRatio < HIT_RATIO_THRESHOLD) {
                String message = String.format("Low cache hit ratio detected for %s/%s: %.2f%%", 
                    cacheType, cacheName, hitRatio * 100);
                alertService.createAlert("LOW_HIT_RATIO", "WARNING", message, 
                    Map.of("cacheType", cacheType, "cacheName", cacheName, "hitRatio", hitRatio));
                
                generateHitRatioImprovement(cacheType, cacheName, hitRatio);
            }
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.recordHitMiss(isHit);
            state.updateLastAccess(timestamp);
        });
        
        metricsService.recordCacheAccess(cacheType, cacheName, isHit);
        
        CacheMetrics metrics = CacheMetrics.builder()
            .cacheType(cacheType)
            .cacheName(cacheName)
            .hitRatio(analyzer != null ? analyzer.getHitRatio(cacheName) : 0.0)
            .timestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()))
            .build();
        
        metricsRepository.save(metrics);
    }
    
    private void processEvictionEvent(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        String evictionReason = eventData.get("reason").asText();
        int evictedCount = eventData.get("evictedCount").asInt();
        JsonNode evictedKeys = eventData.get("evictedKeys");
        long timestamp = eventData.get("timestamp").asLong();
        
        EvictionTracker tracker = evictionTrackers.get(cacheType);
        if (tracker != null) {
            tracker.recordEviction(cacheName, evictionReason, evictedCount, evictedKeys, timestamp);
            
            double evictionRate = tracker.getEvictionRate(cacheName);
            if (evictionRate > EVICTION_RATE_THRESHOLD) {
                String message = String.format("High eviction rate detected for %s/%s: %.2f evictions/min", 
                    cacheType, cacheName, evictionRate);
                alertService.createAlert("HIGH_EVICTION_RATE", "WARNING", message,
                    Map.of("cacheType", cacheType, "cacheName", cacheName, 
                           "evictionRate", evictionRate, "reason", evictionReason));
                
                analyzeEvictionPattern(cacheType, cacheName, evictionReason);
            }
        }
        
        evictionAnalysisService.analyzeEviction(cacheType, cacheName, evictionReason, evictedKeys);
        
        updateCacheState(cacheType, cacheName, state -> {
            state.recordEviction(evictedCount);
            state.updateEvictionReason(evictionReason);
        });
        
        metricsService.recordCacheEviction(cacheType, cacheName, evictedCount, evictionReason);
    }
    
    private void processOperationLatency(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        String operation = eventData.get("operation").asText();
        long latencyMs = eventData.get("latencyMs").asLong();
        String key = eventData.get("key").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        LatencyTracker tracker = latencyTrackers.get(cacheType);
        if (tracker != null) {
            tracker.recordLatency(cacheName, operation, latencyMs, key, timestamp);
            
            if (latencyMs > OPERATION_LATENCY_THRESHOLD_MS) {
                String message = String.format("High cache operation latency for %s/%s: %dms for %s", 
                    cacheType, cacheName, latencyMs, operation);
                alertService.createAlert("HIGH_CACHE_LATENCY", "WARNING", message,
                    Map.of("cacheType", cacheType, "cacheName", cacheName, 
                           "operation", operation, "latencyMs", latencyMs));
                
                analyzeLatencyCause(cacheType, cacheName, operation, latencyMs);
            }
            
            detectLatencySpike(tracker, cacheName, operation, latencyMs);
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.recordOperationLatency(operation, latencyMs);
        });
        
        metricsService.recordCacheLatency(cacheType, cacheName, operation, latencyMs);
    }
    
    private void processMemoryUsage(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        long usedMemoryBytes = eventData.get("usedMemoryBytes").asLong();
        long maxMemoryBytes = eventData.get("maxMemoryBytes").asLong();
        double fragmentation = eventData.get("fragmentation").asDouble();
        long timestamp = eventData.get("timestamp").asLong();
        
        MemoryAnalyzer analyzer = memoryAnalyzers.get(cacheType);
        if (analyzer != null) {
            analyzer.updateMemoryUsage(cacheName, usedMemoryBytes, maxMemoryBytes, fragmentation, timestamp);
            
            double utilization = (double) usedMemoryBytes / maxMemoryBytes;
            if (utilization > MEMORY_UTILIZATION_THRESHOLD) {
                String message = String.format("High cache memory utilization for %s/%s: %.2f%%", 
                    cacheType, cacheName, utilization * 100);
                alertService.createAlert("HIGH_MEMORY_UTILIZATION", "WARNING", message,
                    Map.of("cacheType", cacheType, "cacheName", cacheName, 
                           "utilization", utilization, "usedMemoryMB", usedMemoryBytes / 1024 / 1024));
                
                suggestMemoryOptimization(cacheType, cacheName, utilization, fragmentation);
            }
            
            if (fragmentation > FRAGMENTATION_THRESHOLD) {
                handleFragmentation(cacheType, cacheName, fragmentation);
            }
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.updateMemoryUsage(usedMemoryBytes, maxMemoryBytes);
            state.updateFragmentation(fragmentation);
        });
        
        metricsService.recordCacheMemory(cacheType, cacheName, usedMemoryBytes, maxMemoryBytes);
    }
    
    private void processKeyAccessPattern(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        String key = eventData.get("key").asText();
        int accessCount = eventData.get("accessCount").asInt();
        long windowMs = eventData.get("windowMs").asLong();
        long timestamp = eventData.get("timestamp").asLong();
        
        HotKeyDetector detector = hotKeyDetectors.get(cacheType);
        if (detector != null) {
            detector.recordKeyAccess(cacheName, key, accessCount, windowMs, timestamp);
            
            if (accessCount > HOT_KEY_THRESHOLD) {
                String message = String.format("Hot key detected in %s/%s: %s with %d accesses", 
                    cacheType, cacheName, key, accessCount);
                alertService.createAlert("HOT_KEY_DETECTED", "INFO", message,
                    Map.of("cacheType", cacheType, "cacheName", cacheName, 
                           "key", key, "accessCount", accessCount));
                
                handleHotKey(cacheType, cacheName, key, accessCount);
            }
            
            analyzeKeyDistribution(detector, cacheName);
        }
        
        keyDistributionService.analyzeDistribution(cacheType, cacheName, key, accessCount);
        
        updateCacheState(cacheType, cacheName, state -> {
            state.recordKeyAccess(key, accessCount);
        });
    }
    
    private void processInvalidationEvent(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        String invalidationType = eventData.get("invalidationType").asText();
        int keyCount = eventData.get("keyCount").asInt();
        String reason = eventData.get("reason").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        InvalidationMonitor monitor = invalidationMonitors.get(cacheType);
        if (monitor != null) {
            monitor.recordInvalidation(cacheName, invalidationType, keyCount, reason, timestamp);
            
            double invalidationRate = monitor.getInvalidationRate(cacheName);
            if (invalidationRate > INVALIDATION_RATE_THRESHOLD) {
                String message = String.format("High invalidation rate for %s/%s: %.2f invalidations/min", 
                    cacheType, cacheName, invalidationRate);
                alertService.createAlert("HIGH_INVALIDATION_RATE", "WARNING", message,
                    Map.of("cacheType", cacheType, "cacheName", cacheName, 
                           "invalidationRate", invalidationRate, "reason", reason));
                
                analyzeInvalidationPattern(cacheType, cacheName, invalidationType, reason);
            }
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.recordInvalidation(keyCount, invalidationType);
        });
        
        metricsService.recordCacheInvalidation(cacheType, cacheName, keyCount, invalidationType);
    }
    
    private void processTtlExpiry(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        int expiredCount = eventData.get("expiredCount").asInt();
        long avgTtlMs = eventData.get("avgTtlMs").asLong();
        JsonNode expiredKeys = eventData.get("expiredKeys");
        long timestamp = eventData.get("timestamp").asLong();
        
        TtlAnalyzer analyzer = ttlAnalyzers.get(cacheType);
        if (analyzer != null) {
            analyzer.recordTtlExpiry(cacheName, expiredCount, avgTtlMs, expiredKeys, timestamp);
            
            double expiryRate = analyzer.getExpiryRate(cacheName);
            if (expiryRate > TTL_EXPIRY_RATE_THRESHOLD) {
                String message = String.format("High TTL expiry rate for %s/%s: %.2f expiries/min", 
                    cacheType, cacheName, expiryRate);
                alertService.createAlert("HIGH_TTL_EXPIRY", "INFO", message,
                    Map.of("cacheType", cacheType, "cacheName", cacheName, 
                           "expiryRate", expiryRate, "avgTtlMs", avgTtlMs));
                
                optimizeTtlSettings(cacheType, cacheName, avgTtlMs, expiryRate);
            }
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.recordTtlExpiry(expiredCount, avgTtlMs);
        });
        
        metricsService.recordCacheTtlExpiry(cacheType, cacheName, expiredCount);
    }
    
    private void processCacheSizeChange(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        long oldSize = eventData.get("oldSize").asLong();
        long newSize = eventData.get("newSize").asLong();
        String changeReason = eventData.get("changeReason").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        double growthRate = (double) (newSize - oldSize) / oldSize;
        if (Math.abs(growthRate) > CACHE_SIZE_GROWTH_THRESHOLD) {
            String changeType = growthRate > 0 ? "growth" : "shrinkage";
            String message = String.format("Significant cache %s for %s/%s: %.2f%% (%d -> %d)", 
                changeType, cacheType, cacheName, growthRate * 100, oldSize, newSize);
            alertService.createAlert("CACHE_SIZE_CHANGE", "INFO", message,
                Map.of("cacheType", cacheType, "cacheName", cacheName, 
                       "oldSize", oldSize, "newSize", newSize, "changeReason", changeReason));
            
            analyzeSizeChange(cacheType, cacheName, oldSize, newSize, changeReason);
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.updateCacheSize(newSize);
            state.recordSizeChange(oldSize, newSize, changeReason);
        });
        
        metricsService.recordCacheSize(cacheType, cacheName, newSize);
    }
    
    private void processReplicationStatus(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        String replicationStatus = eventData.get("status").asText();
        long replicationLagMs = eventData.get("replicationLagMs").asLong();
        int pendingOperations = eventData.get("pendingOperations").asInt();
        long timestamp = eventData.get("timestamp").asLong();
        
        ReplicationMonitor monitor = replicationMonitors.get(cacheType);
        if (monitor != null) {
            monitor.updateReplicationStatus(cacheName, replicationStatus, replicationLagMs, 
                                           pendingOperations, timestamp);
            
            if (replicationLagMs > REPLICATION_LAG_THRESHOLD_MS) {
                String message = String.format("High replication lag for %s/%s: %dms with %d pending ops", 
                    cacheType, cacheName, replicationLagMs, pendingOperations);
                alertService.createAlert("HIGH_REPLICATION_LAG", "WARNING", message,
                    Map.of("cacheType", cacheType, "cacheName", cacheName, 
                           "lagMs", replicationLagMs, "pendingOps", pendingOperations));
                
                handleReplicationLag(cacheType, cacheName, replicationLagMs, pendingOperations);
            }
            
            if (!"HEALTHY".equals(replicationStatus)) {
                handleReplicationIssue(cacheType, cacheName, replicationStatus);
            }
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.updateReplicationStatus(replicationStatus, replicationLagMs);
        });
        
        metricsService.recordReplicationLag(cacheType, cacheName, replicationLagMs);
    }
    
    private void processFragmentationAnalysis(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        double fragmentationRatio = eventData.get("fragmentationRatio").asDouble();
        long wastedBytes = eventData.get("wastedBytes").asLong();
        String recommendation = eventData.get("recommendation").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        if (fragmentationRatio > FRAGMENTATION_THRESHOLD) {
            String message = String.format("High fragmentation in %s/%s: %.2f%% (%.2f MB wasted)", 
                cacheType, cacheName, fragmentationRatio * 100, wastedBytes / 1024.0 / 1024.0);
            alertService.createAlert("HIGH_FRAGMENTATION", "WARNING", message,
                Map.of("cacheType", cacheType, "cacheName", cacheName, 
                       "fragmentationRatio", fragmentationRatio, "wastedBytes", wastedBytes));
            
            scheduleDefragmentation(cacheType, cacheName, fragmentationRatio, recommendation);
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.updateFragmentation(fragmentationRatio);
            state.updateWastedMemory(wastedBytes);
        });
        
        metricsService.recordCacheFragmentation(cacheType, cacheName, fragmentationRatio);
    }
    
    private void processWarmupComplete(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        long warmupTimeMs = eventData.get("warmupTimeMs").asLong();
        int keysLoaded = eventData.get("keysLoaded").asInt();
        long dataSizeBytes = eventData.get("dataSizeBytes").asLong();
        boolean success = eventData.get("success").asBoolean();
        long timestamp = eventData.get("timestamp").asLong();
        
        if (!success) {
            String message = String.format("Cache warmup failed for %s/%s after %dms", 
                cacheType, cacheName, warmupTimeMs);
            alertService.createAlert("WARMUP_FAILED", "ERROR", message,
                Map.of("cacheType", cacheType, "cacheName", cacheName, "warmupTimeMs", warmupTimeMs));
        } else if (warmupTimeMs > WARMUP_TIME_THRESHOLD_MS) {
            String message = String.format("Slow cache warmup for %s/%s: %dms for %d keys (%.2f MB)", 
                cacheType, cacheName, warmupTimeMs, keysLoaded, dataSizeBytes / 1024.0 / 1024.0);
            alertService.createAlert("SLOW_WARMUP", "WARNING", message,
                Map.of("cacheType", cacheType, "cacheName", cacheName, 
                       "warmupTimeMs", warmupTimeMs, "keysLoaded", keysLoaded));
            
            optimizeWarmupProcess(cacheType, cacheName, warmupTimeMs, keysLoaded);
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.recordWarmup(warmupTimeMs, keysLoaded, success);
        });
        
        metricsService.recordCacheWarmup(cacheType, cacheName, warmupTimeMs, keysLoaded, success);
    }
    
    private void processConnectionPoolStatus(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        int activeConnections = eventData.get("activeConnections").asInt();
        int maxConnections = eventData.get("maxConnections").asInt();
        int waitingRequests = eventData.get("waitingRequests").asInt();
        long avgWaitTimeMs = eventData.get("avgWaitTimeMs").asLong();
        long timestamp = eventData.get("timestamp").asLong();
        
        double saturation = (double) activeConnections / maxConnections;
        if (saturation > CONNECTION_POOL_SATURATION_THRESHOLD) {
            String message = String.format("Connection pool saturation for %s/%s: %.2f%% (%d/%d) with %d waiting", 
                cacheType, cacheName, saturation * 100, activeConnections, maxConnections, waitingRequests);
            alertService.createAlert("CONNECTION_POOL_SATURATION", "WARNING", message,
                Map.of("cacheType", cacheType, "cacheName", cacheName, 
                       "saturation", saturation, "waitingRequests", waitingRequests));
            
            optimizeConnectionPool(cacheType, cacheName, activeConnections, maxConnections, waitingRequests);
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.updateConnectionPool(activeConnections, maxConnections, waitingRequests);
        });
        
        metricsService.recordConnectionPoolMetrics(cacheType, cacheName, activeConnections, maxConnections);
    }
    
    private void processSerializationError(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        String errorType = eventData.get("errorType").asText();
        String className = eventData.get("className").asText();
        int errorCount = eventData.get("errorCount").asInt();
        String errorMessage = eventData.get("errorMessage").asText();
        long timestamp = eventData.get("timestamp").asLong();
        
        updateCacheState(cacheType, cacheName, state -> {
            state.recordSerializationError(errorType, className, errorCount);
        });
        
        if (errorCount > SERIALIZATION_ERROR_THRESHOLD) {
            String message = String.format("High serialization errors for %s/%s: %d errors for %s", 
                cacheType, cacheName, errorCount, className);
            alertService.createAlert("SERIALIZATION_ERRORS", "ERROR", message,
                Map.of("cacheType", cacheType, "cacheName", cacheName, 
                       "errorType", errorType, "className", className, "errorCount", errorCount));
            
            analyzeSerializationIssue(cacheType, cacheName, errorType, className, errorMessage);
        }
        
        metricsService.recordSerializationError(cacheType, cacheName, errorType, errorCount);
    }
    
    private void processCacheCoherenceCheck(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        double coherenceScore = eventData.get("coherenceScore").asDouble();
        int inconsistentKeys = eventData.get("inconsistentKeys").asInt();
        JsonNode inconsistencies = eventData.get("inconsistencies");
        long timestamp = eventData.get("timestamp").asLong();
        
        if (coherenceScore < CACHE_COHERENCE_THRESHOLD) {
            String message = String.format("Cache coherence issue for %s/%s: %.2f%% coherence with %d inconsistent keys", 
                cacheType, cacheName, coherenceScore * 100, inconsistentKeys);
            alertService.createAlert("CACHE_COHERENCE_ISSUE", "ERROR", message,
                Map.of("cacheType", cacheType, "cacheName", cacheName, 
                       "coherenceScore", coherenceScore, "inconsistentKeys", inconsistentKeys));
            
            resolveCoherenceIssues(cacheType, cacheName, inconsistencies);
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.updateCoherence(coherenceScore, inconsistentKeys);
        });
        
        metricsService.recordCacheCoherence(cacheType, cacheName, coherenceScore);
    }
    
    private void processPerformanceAnomaly(JsonNode eventData) {
        String cacheType = eventData.get("cacheType").asText();
        String cacheName = eventData.get("cacheName").asText();
        String anomalyType = eventData.get("anomalyType").asText();
        double severity = eventData.get("severity").asDouble();
        String description = eventData.get("description").asText();
        JsonNode metrics = eventData.get("metrics");
        long timestamp = eventData.get("timestamp").asLong();
        
        String message = String.format("Performance anomaly detected in %s/%s: %s (severity: %.2f)", 
            cacheType, cacheName, anomalyType, severity);
        
        String alertLevel = severity > 0.8 ? "CRITICAL" : severity > 0.5 ? "WARNING" : "INFO";
        alertService.createAlert("PERFORMANCE_ANOMALY", alertLevel, message,
            Map.of("cacheType", cacheType, "cacheName", cacheName, 
                   "anomalyType", anomalyType, "severity", severity));
        
        investigateAnomaly(cacheType, cacheName, anomalyType, metrics);
        
        if (severity > 0.7) {
            implementMitigation(cacheType, cacheName, anomalyType, severity);
        }
        
        updateCacheState(cacheType, cacheName, state -> {
            state.recordAnomaly(anomalyType, severity, description);
        });
        
        metricsService.recordPerformanceAnomaly(cacheType, cacheName, anomalyType, severity);
    }
    
    private void updateCacheState(String cacheType, String cacheName, 
                                  java.util.function.Consumer<CachePerformanceState> updater) {
        String key = cacheType + ":" + cacheName;
        cacheStates.computeIfAbsent(key, k -> new CachePerformanceState(cacheType))
                   .update(updater);
    }
    
    private void generateHitRatioImprovement(String cacheType, String cacheName, double hitRatio) {
        analysisExecutor.execute(() -> {
            try {
                List<String> recommendations = new ArrayList<>();
                
                HitRatioAnalyzer analyzer = hitRatioAnalyzers.get(cacheType);
                if (analyzer != null) {
                    Map<String, Integer> missingKeys = analyzer.getMostMissedKeys(cacheName, 10);
                    if (!missingKeys.isEmpty()) {
                        recommendations.add("Preload frequently missed keys: " + missingKeys.keySet());
                    }
                    
                    double peakHitRatio = analyzer.getPeakHitRatio(cacheName);
                    if (peakHitRatio - hitRatio > 0.1) {
                        recommendations.add(String.format("Hit ratio degraded from peak %.2f%% to %.2f%%", 
                            peakHitRatio * 100, hitRatio * 100));
                    }
                }
                
                recommendations.add("Consider increasing cache size");
                recommendations.add("Review TTL settings for frequently accessed data");
                recommendations.add("Implement cache warming for critical data");
                
                optimizationService.createOptimizationPlan(cacheType, cacheName, 
                    "HIT_RATIO_IMPROVEMENT", recommendations);
                
            } catch (Exception e) {
                log.error("Error generating hit ratio improvements: {}", e.getMessage(), e);
            }
        });
    }
    
    private void analyzeEvictionPattern(String cacheType, String cacheName, String evictionReason) {
        analysisExecutor.execute(() -> {
            try {
                EvictionTracker tracker = evictionTrackers.get(cacheType);
                if (tracker != null) {
                    Map<String, Integer> evictionReasons = tracker.getEvictionReasonDistribution(cacheName);
                    
                    if ("SIZE".equals(evictionReason)) {
                        optimizationService.suggestSizeIncrease(cacheType, cacheName, 
                            tracker.getEvictionRate(cacheName));
                    } else if ("TTL".equals(evictionReason)) {
                        optimizationService.suggestTtlAdjustment(cacheType, cacheName,
                            tracker.getAverageTtl(cacheName));
                    }
                    
                    evictionAnalysisService.analyzeEvictionPattern(cacheType, cacheName, evictionReasons);
                }
            } catch (Exception e) {
                log.error("Error analyzing eviction pattern: {}", e.getMessage(), e);
            }
        });
    }
    
    private void analyzeLatencyCause(String cacheType, String cacheName, String operation, long latencyMs) {
        analysisExecutor.execute(() -> {
            try {
                List<String> possibleCauses = new ArrayList<>();
                
                CachePerformanceState state = cacheStates.get(cacheType + ":" + cacheName);
                if (state != null) {
                    if (state.getMemoryUtilization() > 0.9) {
                        possibleCauses.add("High memory utilization causing swapping");
                    }
                    
                    if (state.getFragmentation() > 0.3) {
                        possibleCauses.add("Memory fragmentation affecting performance");
                    }
                    
                    if (state.getConnectionPoolSaturation() > 0.8) {
                        possibleCauses.add("Connection pool saturation");
                    }
                }
                
                if ("GET".equals(operation) && latencyMs > 50) {
                    possibleCauses.add("Possible network latency or serialization overhead");
                }
                
                if ("SET".equals(operation) && latencyMs > 100) {
                    possibleCauses.add("Possible replication delay or persistence overhead");
                }
                
                optimizationService.analyzeLatencyCauses(cacheType, cacheName, operation, 
                    latencyMs, possibleCauses);
                
            } catch (Exception e) {
                log.error("Error analyzing latency cause: {}", e.getMessage(), e);
            }
        });
    }
    
    private void detectLatencySpike(LatencyTracker tracker, String cacheName, 
                                   String operation, long latencyMs) {
        double avgLatency = tracker.getAverageLatency(cacheName, operation);
        double stdDev = tracker.getLatencyStdDev(cacheName, operation);
        
        if (latencyMs > avgLatency + (3 * stdDev)) {
            String message = String.format("Latency spike detected for %s: %dms (avg: %.2fms, stddev: %.2fms)",
                operation, latencyMs, avgLatency, stdDev);
            alertService.createAlert("LATENCY_SPIKE", "WARNING", message,
                Map.of("operation", operation, "latencyMs", latencyMs, 
                       "avgLatency", avgLatency, "stdDev", stdDev));
        }
    }
    
    private void suggestMemoryOptimization(String cacheType, String cacheName, 
                                          double utilization, double fragmentation) {
        List<String> suggestions = new ArrayList<>();
        
        if (utilization > 0.9) {
            suggestions.add("Increase maximum memory allocation");
            suggestions.add("Implement more aggressive eviction policies");
            suggestions.add("Consider data compression");
        }
        
        if (fragmentation > 0.2) {
            suggestions.add("Schedule regular defragmentation");
            suggestions.add("Optimize data structure usage");
        }
        
        optimizationService.createMemoryOptimizationPlan(cacheType, cacheName, 
            utilization, fragmentation, suggestions);
    }
    
    private void handleFragmentation(String cacheType, String cacheName, double fragmentation) {
        if ("redis".equals(cacheType)) {
            optimizationService.scheduleRedisDefragmentation(cacheName, fragmentation);
        } else {
            optimizationService.suggestFragmentationMitigation(cacheType, cacheName, fragmentation);
        }
    }
    
    private void handleHotKey(String cacheType, String cacheName, String key, int accessCount) {
        keyDistributionService.handleHotKey(cacheType, cacheName, key, accessCount);
        
        if (accessCount > HOT_KEY_THRESHOLD * 2) {
            optimizationService.suggestHotKeyMitigation(cacheType, cacheName, key, accessCount);
        }
    }
    
    private void analyzeKeyDistribution(HotKeyDetector detector, String cacheName) {
        Map<String, Integer> topKeys = detector.getTopKeys(cacheName, 20);
        double giniCoefficient = calculateGiniCoefficient(topKeys.values());
        
        if (giniCoefficient > 0.8) {
            alertService.createAlert("UNEVEN_KEY_DISTRIBUTION", "WARNING", 
                String.format("Highly uneven key distribution in %s (Gini: %.3f)", cacheName, giniCoefficient),
                Map.of("cacheName", cacheName, "giniCoefficient", giniCoefficient, "topKeys", topKeys));
        }
    }
    
    private double calculateGiniCoefficient(Collection<Integer> values) {
        if (values.isEmpty()) return 0.0;
        
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        
        int n = sorted.size();
        double sum = 0;
        double cumulativeSum = 0;
        
        for (int i = 0; i < n; i++) {
            cumulativeSum += sorted.get(i);
            sum += (n - i) * sorted.get(i);
        }
        
        return (2.0 * sum) / (n * cumulativeSum) - (n + 1.0) / n;
    }
    
    private void analyzeInvalidationPattern(String cacheType, String cacheName, 
                                           String invalidationType, String reason) {
        InvalidationMonitor monitor = invalidationMonitors.get(cacheType);
        if (monitor != null) {
            Map<String, Integer> patterns = monitor.getInvalidationPatterns(cacheName);
            
            if (patterns.size() > 10) {
                optimizationService.suggestInvalidationOptimization(cacheType, cacheName, patterns);
            }
        }
    }
    
    private void optimizeTtlSettings(String cacheType, String cacheName, 
                                    long avgTtlMs, double expiryRate) {
        TtlAnalyzer analyzer = ttlAnalyzers.get(cacheType);
        if (analyzer != null) {
            Map<String, Long> keyTtlPatterns = analyzer.getKeyTtlPatterns(cacheName);
            optimizationService.optimizeTtlConfiguration(cacheType, cacheName, 
                avgTtlMs, expiryRate, keyTtlPatterns);
        }
    }
    
    private void analyzeSizeChange(String cacheType, String cacheName, 
                                  long oldSize, long newSize, String changeReason) {
        double changeRate = (double) (newSize - oldSize) / oldSize;
        
        if (Math.abs(changeRate) > 0.5) {
            healthService.investigateDrasticSizeChange(cacheType, cacheName, 
                oldSize, newSize, changeReason);
        }
    }
    
    private void handleReplicationLag(String cacheType, String cacheName, 
                                     long replicationLagMs, int pendingOperations) {
        if (replicationLagMs > 1000) {
            healthService.handleCriticalReplicationLag(cacheType, cacheName, 
                replicationLagMs, pendingOperations);
        } else {
            optimizationService.optimizeReplication(cacheType, cacheName, 
                replicationLagMs, pendingOperations);
        }
    }
    
    private void handleReplicationIssue(String cacheType, String cacheName, String status) {
        if ("DISCONNECTED".equals(status)) {
            healthService.handleReplicationDisconnection(cacheType, cacheName);
        } else if ("LAGGING".equals(status)) {
            healthService.handleReplicationLagging(cacheType, cacheName);
        }
    }
    
    private void scheduleDefragmentation(String cacheType, String cacheName, 
                                        double fragmentationRatio, String recommendation) {
        optimizationService.scheduleDefragmentation(cacheType, cacheName, 
            fragmentationRatio, recommendation);
    }
    
    private void optimizeWarmupProcess(String cacheType, String cacheName, 
                                      long warmupTimeMs, int keysLoaded) {
        double keysPerSecond = (keysLoaded * 1000.0) / warmupTimeMs;
        optimizationService.optimizeWarmupStrategy(cacheType, cacheName, 
            warmupTimeMs, keysLoaded, keysPerSecond);
    }
    
    private void optimizeConnectionPool(String cacheType, String cacheName, 
                                       int activeConnections, int maxConnections, int waitingRequests) {
        if (waitingRequests > 10) {
            int suggestedMax = Math.min(maxConnections * 2, 1000);
            optimizationService.suggestConnectionPoolSize(cacheType, cacheName, 
                activeConnections, maxConnections, suggestedMax);
        }
    }
    
    private void analyzeSerializationIssue(String cacheType, String cacheName, 
                                          String errorType, String className, String errorMessage) {
        optimizationService.analyzeSerializationProblem(cacheType, cacheName, 
            errorType, className, errorMessage);
    }
    
    private void resolveCoherenceIssues(String cacheType, String cacheName, JsonNode inconsistencies) {
        List<String> inconsistentKeys = new ArrayList<>();
        inconsistencies.forEach(node -> inconsistentKeys.add(node.asText()));
        
        healthService.resolveCoherenceIssues(cacheType, cacheName, inconsistentKeys);
    }
    
    private void investigateAnomaly(String cacheType, String cacheName, 
                                   String anomalyType, JsonNode metrics) {
        Map<String, Object> metricsMap = objectMapper.convertValue(metrics, Map.class);
        healthService.investigatePerformanceAnomaly(cacheType, cacheName, anomalyType, metricsMap);
    }
    
    private void implementMitigation(String cacheType, String cacheName, 
                                    String anomalyType, double severity) {
        healthService.implementAnomalyMitigation(cacheType, cacheName, anomalyType, severity);
    }
    
    @Scheduled(fixedDelay = 60000)
    private void analyzePerformanceTrends() {
        try {
            cacheStates.forEach((key, state) -> {
                String[] parts = key.split(":");
                String cacheType = parts[0];
                String cacheName = parts.length > 1 ? parts[1] : "default";
                
                if (state.hasSignificantChange()) {
                    String trend = state.getPerformanceTrend();
                    notificationService.notifyPerformanceTrend(cacheType, cacheName, trend);
                }
                
                detectPerformanceDegradation(cacheType, cacheName, state);
                generatePerformanceReport(cacheType, cacheName, state);
            });
        } catch (Exception e) {
            log.error("Error analyzing performance trends: {}", e.getMessage(), e);
        }
    }
    
    private void detectPerformanceDegradation(String cacheType, String cacheName, 
                                             CachePerformanceState state) {
        if (state.getHitRatioDegradation() > 0.1) {
            alertService.createAlert("PERFORMANCE_DEGRADATION", "WARNING",
                String.format("Cache performance degradation detected for %s/%s", cacheType, cacheName),
                Map.of("cacheType", cacheType, "cacheName", cacheName, 
                       "degradation", state.getHitRatioDegradation()));
        }
    }
    
    private void generatePerformanceReport(String cacheType, String cacheName, 
                                          CachePerformanceState state) {
        Map<String, Object> report = new HashMap<>();
        report.put("cacheType", cacheType);
        report.put("cacheName", cacheName);
        report.put("hitRatio", state.getHitRatio());
        report.put("evictionRate", state.getEvictionRate());
        report.put("avgLatency", state.getAverageLatency());
        report.put("memoryUtilization", state.getMemoryUtilization());
        report.put("performanceScore", state.getPerformanceScore());
        
        metricsService.recordPerformanceReport(report);
    }
    
    @Scheduled(fixedDelay = 120000)
    private void detectAnomalies() {
        try {
            Map<String, List<CacheMetrics>> recentMetrics = getRecentMetricsByCacheType();
            
            recentMetrics.forEach((cacheType, metrics) -> {
                detectHitRatioAnomalies(cacheType, metrics);
                detectLatencyAnomalies(cacheType, metrics);
                detectEvictionAnomalies(cacheType, metrics);
            });
        } catch (Exception e) {
            log.error("Error detecting anomalies: {}", e.getMessage(), e);
        }
    }
    
    private Map<String, List<CacheMetrics>> getRecentMetricsByCacheType() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(ANALYSIS_WINDOW_MINUTES);
        return metricsRepository.findByTimestampAfter(since).stream()
            .collect(Collectors.groupingBy(CacheMetrics::getCacheType));
    }
    
    private void detectHitRatioAnomalies(String cacheType, List<CacheMetrics> metrics) {
        if (metrics.size() < 10) return;
        
        double[] hitRatios = metrics.stream()
            .mapToDouble(CacheMetrics::getHitRatio)
            .toArray();
        
        double mean = Arrays.stream(hitRatios).average().orElse(0.0);
        double stdDev = calculateStandardDeviation(hitRatios, mean);
        
        for (CacheMetrics metric : metrics) {
            if (Math.abs(metric.getHitRatio() - mean) > 2 * stdDev) {
                alertService.createAlert("HIT_RATIO_ANOMALY", "INFO",
                    String.format("Hit ratio anomaly detected for %s: %.2f%% (mean: %.2f%%, stddev: %.2f%%)",
                        metric.getCacheName(), metric.getHitRatio() * 100, mean * 100, stdDev * 100),
                    Map.of("cacheType", cacheType, "cacheName", metric.getCacheName(),
                           "hitRatio", metric.getHitRatio(), "mean", mean, "stdDev", stdDev));
            }
        }
    }
    
    private void detectLatencyAnomalies(String cacheType, List<CacheMetrics> metrics) {
        Map<String, List<Double>> latenciesByCache = new HashMap<>();
        
        metrics.forEach(metric -> {
            if (metric.getAverageLatencyMs() != null) {
                latenciesByCache.computeIfAbsent(metric.getCacheName(), k -> new ArrayList<>())
                    .add(metric.getAverageLatencyMs());
            }
        });
        
        latenciesByCache.forEach((cacheName, latencies) -> {
            if (latencies.size() >= 5) {
                double mean = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double stdDev = calculateStandardDeviation(
                    latencies.stream().mapToDouble(Double::doubleValue).toArray(), mean);
                
                latencies.stream()
                    .filter(latency -> latency > mean + 2 * stdDev)
                    .forEach(latency -> {
                        alertService.createAlert("LATENCY_ANOMALY", "WARNING",
                            String.format("Latency anomaly for %s/%s: %.2fms", cacheType, cacheName, latency),
                            Map.of("cacheType", cacheType, "cacheName", cacheName, "latency", latency));
                    });
            }
        });
    }
    
    private void detectEvictionAnomalies(String cacheType, List<CacheMetrics> metrics) {
        Map<String, List<Double>> evictionRatesByCache = new HashMap<>();
        
        metrics.forEach(metric -> {
            if (metric.getEvictionRate() != null) {
                evictionRatesByCache.computeIfAbsent(metric.getCacheName(), k -> new ArrayList<>())
                    .add(metric.getEvictionRate());
            }
        });
        
        evictionRatesByCache.forEach((cacheName, rates) -> {
            if (rates.size() >= 5) {
                double mean = rates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                
                boolean suddenSpike = rates.stream()
                    .anyMatch(rate -> rate > mean * 3);
                
                if (suddenSpike) {
                    alertService.createAlert("EVICTION_SPIKE", "WARNING",
                        String.format("Sudden eviction spike for %s/%s", cacheType, cacheName),
                        Map.of("cacheType", cacheType, "cacheName", cacheName));
                }
            }
        });
    }
    
    private double calculateStandardDeviation(double[] values, double mean) {
        double variance = Arrays.stream(values)
            .map(v -> Math.pow(v - mean, 2))
            .sum() / values.length;
        return Math.sqrt(variance);
    }
    
    @Scheduled(fixedDelay = 300000)
    private void generateOptimizationRecommendations() {
        try {
            cacheStates.forEach((key, state) -> {
                if (state.needsOptimization()) {
                    String[] parts = key.split(":");
                    String cacheType = parts[0];
                    String cacheName = parts.length > 1 ? parts[1] : "default";
                    
                    List<String> recommendations = generateRecommendations(state);
                    
                    OptimizationRecommendation recommendation = new OptimizationRecommendation(
                        cacheType, cacheName, recommendations, state.getPerformanceScore());
                    
                    this.recommendations.put(key, recommendation);
                    optimizationService.processRecommendations(recommendation);
                }
            });
        } catch (Exception e) {
            log.error("Error generating optimization recommendations: {}", e.getMessage(), e);
        }
    }
    
    private List<String> generateRecommendations(CachePerformanceState state) {
        List<String> recommendations = new ArrayList<>();
        
        if (state.getHitRatio() < 0.8) {
            recommendations.add("Increase cache size or adjust TTL to improve hit ratio");
        }
        
        if (state.getEvictionRate() > 100) {
            recommendations.add("Cache size may be insufficient for workload");
        }
        
        if (state.getMemoryUtilization() > 0.9) {
            recommendations.add("Consider increasing memory allocation");
        }
        
        if (state.getFragmentation() > 0.3) {
            recommendations.add("Schedule defragmentation to reclaim memory");
        }
        
        if (state.getAverageLatency() > 50) {
            recommendations.add("Investigate causes of high latency");
        }
        
        return recommendations;
    }
    
    @Scheduled(fixedDelay = 21600000)
    private void cleanupOldData() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            int deleted = metricsRepository.deleteByTimestampBefore(cutoff);
            log.info("Cleaned up {} old cache metrics records", deleted);
            
            cacheStates.values().forEach(state -> state.cleanupOldData(cutoff));
            hitRatioAnalyzers.values().forEach(analyzer -> analyzer.cleanup(cutoff));
            evictionTrackers.values().forEach(tracker -> tracker.cleanup(cutoff));
            
        } catch (Exception e) {
            log.error("Error cleaning up old data: {}", e.getMessage(), e);
        }
    }
    
    @Scheduled(fixedDelay = 30000)
    private void performHealthChecks() {
        try {
            cacheStates.forEach((key, state) -> {
                String[] parts = key.split(":");
                String cacheType = parts[0];
                String cacheName = parts.length > 1 ? parts[1] : "default";
                
                boolean isHealthy = healthService.checkCacheHealth(cacheType, cacheName, state);
                
                if (!isHealthy) {
                    handleUnhealthyCache(cacheType, cacheName, state);
                }
            });
        } catch (Exception e) {
            log.error("Error performing health checks: {}", e.getMessage(), e);
        }
    }
    
    private void handleUnhealthyCache(String cacheType, String cacheName, CachePerformanceState state) {
        Map<String, Object> healthIssues = state.getHealthIssues();
        
        alertService.createAlert("CACHE_UNHEALTHY", "ERROR",
            String.format("Cache %s/%s is unhealthy", cacheType, cacheName),
            healthIssues);
        
        healthService.attemptRecovery(cacheType, cacheName, healthIssues);
    }
    
    private void handleProcessingError(ConsumerRecord<String, String> record, Exception error, 
                                      Acknowledgment acknowledgment) {
        try {
            log.error("Failed to process cache performance event after {} attempts. Sending to DLQ.", 
                MAX_RETRY_ATTEMPTS, error);
            
            Map<String, Object> errorContext = Map.of(
                "topic", record.topic(),
                "partition", record.partition(),
                "offset", record.offset(),
                "error", error.getMessage(),
                "timestamp", Instant.now().toEpochMilli()
            );
            
            notificationService.notifyError("CACHE_PERFORMANCE_PROCESSING_ERROR", errorContext);
            sendToDeadLetterQueue(record, error);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error handling processing failure: {}", e.getMessage(), e);
        }
    }
    
    private void sendToDeadLetterQueue(ConsumerRecord<String, String> record, Exception error) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "originalTopic", record.topic(),
                "originalValue", record.value(),
                "errorMessage", error.getMessage(),
                "errorType", error.getClass().getName(),
                "timestamp", Instant.now().toEpochMilli(),
                "retryCount", MAX_RETRY_ATTEMPTS
            );
            
            log.info("Message sent to DLQ: {}", dlqMessage);
            
        } catch (Exception e) {
            log.error("Failed to send message to DLQ: {}", e.getMessage(), e);
        }
    }
    
    public void handleMessageFallback(ConsumerRecord<String, String> record, Exception ex) {
        log.error("Fallback triggered for cache performance event processing", ex);
        errorCounter.increment();
    }
    
    private double calculateAverageHitRatio(Map<String, CachePerformanceState> states) {
        return states.values().stream()
            .mapToDouble(CachePerformanceState::getHitRatio)
            .average()
            .orElse(0.0);
    }
    
    private double calculateAverageEvictionRate(Map<String, EvictionTracker> trackers) {
        return trackers.values().stream()
            .mapToDouble(tracker -> tracker.getOverallEvictionRate())
            .average()
            .orElse(0.0);
    }
    
    private double calculateAverageMemoryUtilization(Map<String, MemoryAnalyzer> analyzers) {
        return analyzers.values().stream()
            .mapToDouble(analyzer -> analyzer.getAverageUtilization())
            .average()
            .orElse(0.0);
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            log.info("Shutting down CachePerformanceConsumer...");
            scheduledExecutor.shutdown();
            analysisExecutor.shutdown();
            
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            if (!analysisExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                analysisExecutor.shutdownNow();
            }
            
            log.info("CachePerformanceConsumer shut down successfully");
        } catch (InterruptedException e) {
            log.error("Error during shutdown: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }
}