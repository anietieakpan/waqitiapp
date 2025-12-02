package com.waqiti.common.security.encryption;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Metrics service for encryption operations
 *
 * Tracks:
 * - Encryption/decryption success/failure rates
 * - Operation duration
 * - Cache hit/miss rates
 * - Data key generation frequency
 * - KMS health checks
 *
 * @author Waqiti Security Team
 */
@Slf4j
@Service
public class EncryptionMetricsService {

    private final MeterRegistry meterRegistry;
    private final Counter encryptionSuccessCounter;
    private final Counter encryptionFailureCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter dataKeyGenerationCounter;
    private final Counter kmsHealthCheckCounter;
    private final Timer encryptionDurationTimer;

    public EncryptionMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.encryptionSuccessCounter = Counter.builder("encryption.operations")
                .tag("result", "success")
                .description("Successful encryption operations")
                .register(meterRegistry);

        this.encryptionFailureCounter = Counter.builder("encryption.operations")
                .tag("result", "failure")
                .description("Failed encryption operations")
                .register(meterRegistry);

        this.cacheHitCounter = Counter.builder("encryption.cache")
                .tag("result", "hit")
                .description("Data key cache hits")
                .register(meterRegistry);

        this.cacheMissCounter = Counter.builder("encryption.cache")
                .tag("result", "miss")
                .description("Data key cache misses")
                .register(meterRegistry);

        this.dataKeyGenerationCounter = Counter.builder("encryption.datakey.generated")
                .description("Number of data keys generated")
                .register(meterRegistry);

        this.kmsHealthCheckCounter = Counter.builder("encryption.kms.health")
                .description("KMS health check results")
                .register(meterRegistry);

        this.encryptionDurationTimer = Timer.builder("encryption.duration")
                .description("Encryption operation duration")
                .register(meterRegistry);
    }

    public void recordEncryptionSuccess(String operation) {
        encryptionSuccessCounter.increment();
        meterRegistry.counter("encryption.operations.by_type",
                "operation", operation, "result", "success").increment();
    }

    public void recordEncryptionFailure(String operation, String errorType) {
        encryptionFailureCounter.increment();
        meterRegistry.counter("encryption.operations.by_type",
                "operation", operation, "result", "failure", "error", errorType).increment();
    }

    public void recordEncryptionDuration(String operation, long durationMs) {
        encryptionDurationTimer.record(durationMs, TimeUnit.MILLISECONDS);
        meterRegistry.timer("encryption.duration.by_type", "operation", operation)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordCacheHit(String keyType) {
        cacheHitCounter.increment();
    }

    public void recordCacheMiss(String keyType) {
        cacheMissCounter.increment();
    }

    public void recordDataKeyGeneration() {
        dataKeyGenerationCounter.increment();
    }

    public void recordKmsHealthCheck(boolean success) {
        kmsHealthCheckCounter.increment();
        meterRegistry.counter("encryption.kms.health",
                "status", success ? "success" : "failure").increment();
    }

    public void recordKeyCacheCleanup(int removed) {
        meterRegistry.counter("encryption.cache.cleanup",
                "keys_removed", String.valueOf(removed)).increment();
    }
}
