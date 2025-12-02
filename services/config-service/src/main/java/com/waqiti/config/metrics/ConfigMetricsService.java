package com.waqiti.config.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Custom metrics service for configuration operations
 * Tracks: config CRUD operations, cache hits/misses, encryption operations, feature flag evaluations
 */
@Slf4j
@Service
public class ConfigMetricsService {

    private final MeterRegistry meterRegistry;

    // Configuration operation counters
    private final Counter configCreatedCounter;
    private final Counter configUpdatedCounter;
    private final Counter configDeletedCounter;
    private final Counter configReadCounter;

    // Feature flag counters
    private final Counter featureFlagEvaluatedCounter;
    private final Counter featureFlagCreatedCounter;
    private final Counter featureFlagUpdatedCounter;

    // Cache metrics
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    // Encryption metrics
    private final Counter encryptionCounter;
    private final Counter decryptionCounter;
    private final Counter batchDecryptionCounter;

    // Error counters
    private final Counter encryptionErrorCounter;
    private final Counter decryptionErrorCounter;
    private final Counter validationErrorCounter;

    // Timers
    private final Timer configReadTimer;
    private final Timer configWriteTimer;
    private final Timer encryptionTimer;
    private final Timer decryptionTimer;
    private final Timer batchDecryptionTimer;
    private final Timer featureFlagEvaluationTimer;

    public ConfigMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize configuration operation counters
        this.configCreatedCounter = Counter.builder("config.created")
            .description("Number of configurations created")
            .tag("service", "config-service")
            .register(meterRegistry);

        this.configUpdatedCounter = Counter.builder("config.updated")
            .description("Number of configurations updated")
            .tag("service", "config-service")
            .register(meterRegistry);

        this.configDeletedCounter = Counter.builder("config.deleted")
            .description("Number of configurations deleted")
            .tag("service", "config-service")
            .register(meterRegistry);

        this.configReadCounter = Counter.builder("config.read")
            .description("Number of configurations read")
            .tag("service", "config-service")
            .register(meterRegistry);

        // Initialize feature flag counters
        this.featureFlagEvaluatedCounter = Counter.builder("feature.flag.evaluated")
            .description("Number of feature flag evaluations")
            .tag("service", "config-service")
            .register(meterRegistry);

        this.featureFlagCreatedCounter = Counter.builder("feature.flag.created")
            .description("Number of feature flags created")
            .tag("service", "config-service")
            .register(meterRegistry);

        this.featureFlagUpdatedCounter = Counter.builder("feature.flag.updated")
            .description("Number of feature flags updated")
            .tag("service", "config-service")
            .register(meterRegistry);

        // Initialize cache counters
        this.cacheHitCounter = Counter.builder("config.cache.hit")
            .description("Number of cache hits")
            .tag("cache", "configuration")
            .register(meterRegistry);

        this.cacheMissCounter = Counter.builder("config.cache.miss")
            .description("Number of cache misses")
            .tag("cache", "configuration")
            .register(meterRegistry);

        // Initialize encryption counters
        this.encryptionCounter = Counter.builder("config.encryption.total")
            .description("Number of encryption operations")
            .tag("operation", "encrypt")
            .register(meterRegistry);

        this.decryptionCounter = Counter.builder("config.decryption.total")
            .description("Number of decryption operations")
            .tag("operation", "decrypt")
            .register(meterRegistry);

        this.batchDecryptionCounter = Counter.builder("config.batch.decryption.total")
            .description("Number of batch decryption operations")
            .tag("operation", "batch-decrypt")
            .register(meterRegistry);

        // Initialize error counters
        this.encryptionErrorCounter = Counter.builder("config.encryption.error")
            .description("Number of encryption errors")
            .tag("error", "encryption")
            .register(meterRegistry);

        this.decryptionErrorCounter = Counter.builder("config.decryption.error")
            .description("Number of decryption errors")
            .tag("error", "decryption")
            .register(meterRegistry);

        this.validationErrorCounter = Counter.builder("config.validation.error")
            .description("Number of validation errors")
            .tag("error", "validation")
            .register(meterRegistry);

        // Initialize timers
        this.configReadTimer = Timer.builder("config.read.duration")
            .description("Time taken to read configuration")
            .tag("operation", "read")
            .register(meterRegistry);

        this.configWriteTimer = Timer.builder("config.write.duration")
            .description("Time taken to write configuration")
            .tag("operation", "write")
            .register(meterRegistry);

        this.encryptionTimer = Timer.builder("config.encryption.duration")
            .description("Time taken to encrypt value")
            .tag("operation", "encrypt")
            .register(meterRegistry);

        this.decryptionTimer = Timer.builder("config.decryption.duration")
            .description("Time taken to decrypt value")
            .tag("operation", "decrypt")
            .register(meterRegistry);

        this.batchDecryptionTimer = Timer.builder("config.batch.decryption.duration")
            .description("Time taken for batch decryption")
            .tag("operation", "batch-decrypt")
            .register(meterRegistry);

        this.featureFlagEvaluationTimer = Timer.builder("feature.flag.evaluation.duration")
            .description("Time taken to evaluate feature flag")
            .tag("operation", "evaluate")
            .register(meterRegistry);
    }

    // Configuration operation metrics

    public void recordConfigCreated() {
        configCreatedCounter.increment();
    }

    public void recordConfigCreated(String service, String environment) {
        Counter.builder("config.created")
            .tag("service", service != null ? service : "unknown")
            .tag("environment", environment != null ? environment : "unknown")
            .register(meterRegistry)
            .increment();
    }

    public void recordConfigUpdated() {
        configUpdatedCounter.increment();
    }

    public void recordConfigDeleted() {
        configDeletedCounter.increment();
    }

    public void recordConfigRead() {
        configReadCounter.increment();
    }

    public void recordConfigReadTime(long startNanos) {
        long durationNanos = System.nanoTime() - startNanos;
        configReadTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordConfigWriteTime(long startNanos) {
        long durationNanos = System.nanoTime() - startNanos;
        configWriteTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    // Feature flag metrics

    public void recordFeatureFlagEvaluated(String flagName, boolean enabled) {
        featureFlagEvaluatedCounter.increment();

        Counter.builder("feature.flag.evaluated")
            .tag("flag", flagName)
            .tag("result", enabled ? "enabled" : "disabled")
            .register(meterRegistry)
            .increment();
    }

    public void recordFeatureFlagEvaluationTime(long startNanos) {
        long durationNanos = System.nanoTime() - startNanos;
        featureFlagEvaluationTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordFeatureFlagCreated() {
        featureFlagCreatedCounter.increment();
    }

    public void recordFeatureFlagUpdated() {
        featureFlagUpdatedCounter.increment();
    }

    // Cache metrics

    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    public void recordCacheStats(long size, double hitRate, double missRate) {
        meterRegistry.gauge("config.cache.size", size);
        meterRegistry.gauge("config.cache.hit.rate", hitRate);
        meterRegistry.gauge("config.cache.miss.rate", missRate);
    }

    // Encryption metrics

    public void recordEncryption() {
        encryptionCounter.increment();
    }

    public void recordDecryption() {
        decryptionCounter.increment();
    }

    public void recordBatchDecryption(int count) {
        batchDecryptionCounter.increment();

        meterRegistry.counter("config.batch.decryption.items", "count", String.valueOf(count))
            .increment(count);
    }

    public void recordEncryptionTime(long startNanos) {
        long durationNanos = System.nanoTime() - startNanos;
        encryptionTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordDecryptionTime(long startNanos) {
        long durationNanos = System.nanoTime() - startNanos;
        decryptionTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordBatchDecryptionTime(long startNanos, int count) {
        long durationNanos = System.nanoTime() - startNanos;
        batchDecryptionTimer.record(durationNanos, TimeUnit.NANOSECONDS);

        // Calculate average time per item
        if (count > 0) {
            long avgNanos = durationNanos / count;
            Timer.builder("config.batch.decryption.avg.duration")
                .description("Average time per item in batch decryption")
                .tag("operation", "batch-decrypt-avg")
                .register(meterRegistry)
                .record(avgNanos, TimeUnit.NANOSECONDS);
        }
    }

    // Error metrics

    public void recordEncryptionError() {
        encryptionErrorCounter.increment();
    }

    public void recordDecryptionError() {
        decryptionErrorCounter.increment();
    }

    public void recordValidationError(String dataType) {
        validationErrorCounter.increment();

        Counter.builder("config.validation.error")
            .tag("dataType", dataType != null ? dataType : "unknown")
            .register(meterRegistry)
            .increment();
    }

    // Bulk operation metrics

    public void recordBulkUpdate(int successCount, int failureCount) {
        Counter.builder("config.bulk.update.success")
            .description("Successful bulk updates")
            .register(meterRegistry)
            .increment(successCount);

        Counter.builder("config.bulk.update.failure")
            .description("Failed bulk updates")
            .register(meterRegistry)
            .increment(failureCount);
    }

    // Service configuration metrics

    public void recordServiceConfigFetch(String serviceName, int configCount) {
        Counter.builder("config.service.fetch")
            .tag("service", serviceName)
            .description("Service configuration fetches")
            .register(meterRegistry)
            .increment();

        meterRegistry.gauge("config.service.count",
            "service", serviceName,
            configCount);
    }
}
