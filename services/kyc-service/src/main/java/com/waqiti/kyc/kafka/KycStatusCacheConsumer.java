package com.waqiti.kyc.kafka;

import com.waqiti.common.events.compliance.KycStatusCacheEvent;
import com.waqiti.kyc.repository.KYCVerificationRepository;
import com.waqiti.kyc.service.CacheService;
import com.waqiti.kyc.service.KYCVerificationService;
import com.waqiti.kyc.domain.KYCVerification;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class KycStatusCacheConsumer {

    private final KYCVerificationRepository verificationRepository;
    private final CacheService cacheService;
    private final KYCVerificationService verificationService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Counter cacheHitCounter;
    private Counter cacheMissCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("kyc_status_cache_processed_total")
            .description("Total number of successfully processed KYC status cache events")
            .register(meterRegistry);
        errorCounter = Counter.builder("kyc_status_cache_errors_total")
            .description("Total number of KYC status cache processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("kyc_status_cache_processing_duration")
            .description("Time taken to process KYC status cache events")
            .register(meterRegistry);
        cacheHitCounter = Counter.builder("kyc_cache_hits_total")
            .description("Total number of KYC cache hits")
            .register(meterRegistry);
        cacheMissCounter = Counter.builder("kyc_cache_misses_total")
            .description("Total number of KYC cache misses")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"kyc-status-cache", "kyc-cache-operations", "kyc-status-updates"},
        groupId = "kyc-status-cache-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "kyc-status-cache", fallbackMethod = "handleKycStatusCacheEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleKycStatusCacheEvent(
            @Payload KycStatusCacheEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("kyc-cache-%s-p%d-o%d", event.getUserId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getUserId(), event.getCacheOperation(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing KYC cache operation: userId={}, operation={}, cacheKey={}",
                event.getUserId(), event.getCacheOperation(), event.getCacheKey());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getCacheOperation()) {
                case "SET":
                    handleCacheSet(event, correlationId);
                    break;

                case "GET":
                    handleCacheGet(event, correlationId);
                    break;

                case "DELETE":
                    handleCacheDelete(event, correlationId);
                    break;

                case "INVALIDATE":
                    handleCacheInvalidate(event, correlationId);
                    break;

                case "REFRESH":
                    handleCacheRefresh(event, correlationId);
                    break;

                case "EVICT":
                    handleCacheEvict(event, correlationId);
                    break;

                case "BULK_UPDATE":
                    handleBulkCacheUpdate(event, correlationId);
                    break;

                case "WARM_UP":
                    handleCacheWarmUp(event, correlationId);
                    break;

                case "HEALTH_CHECK":
                    handleCacheHealthCheck(event, correlationId);
                    break;

                default:
                    log.warn("Unknown cache operation: {}", event.getCacheOperation());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logUserEvent("KYC_CACHE_OPERATION_PROCESSED", event.getUserId(),
                Map.of("cacheOperation", event.getCacheOperation(), "cacheKey", event.getCacheKey(),
                    "cacheRegion", event.getCacheRegion(), "cacheHit", event.getCacheHit(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process KYC cache event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("kyc-status-cache-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleKycStatusCacheEventFallback(
            KycStatusCacheEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("kyc-cache-fallback-%s-p%d-o%d", event.getUserId(), partition, offset);

        log.error("Circuit breaker fallback triggered for KYC cache: userId={}, error={}",
            event.getUserId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("kyc-status-cache-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "KYC Cache Circuit Breaker Triggered",
                String.format("KYC cache operation for user %s failed: %s", event.getUserId(), ex.getMessage()),
                "MEDIUM"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltKycStatusCacheEvent(
            @Payload KycStatusCacheEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-kyc-cache-%s-%d", event.getUserId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - KYC cache operation permanently failed: userId={}, topic={}, error={}",
            event.getUserId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logUserEvent("KYC_CACHE_DLT_EVENT", event.getUserId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "cacheOperation", event.getCacheOperation(), "cacheKey", event.getCacheKey(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "KYC Cache Dead Letter Event",
                String.format("KYC cache operation for user %s sent to DLT: %s", event.getUserId(), exceptionMessage),
                Map.of("userId", event.getUserId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void handleCacheSet(KycStatusCacheEvent event, String correlationId) {
        try {
            log.info("Setting cache: key={}, status={}, level={}",
                event.getCacheKey(), event.getKycStatus(), event.getKycLevel());

            // Set cache with TTL if specified
            if (event.getCacheTtlSeconds() != null) {
                cacheService.putKycStatusWithTtl(
                    event.getUserId(),
                    event.getKycStatus(),
                    event.getKycLevel(),
                    event.getLastVerifiedAt(),
                    event.getCacheTtlSeconds()
                );
            } else {
                cacheService.putKycStatus(
                    event.getUserId(),
                    event.getKycStatus(),
                    event.getKycLevel(),
                    event.getLastVerifiedAt()
                );
            }

            // Update cache metrics
            publishCacheMetrics(event, true, "SET");

            log.info("Cache set successfully: userId={}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to set cache: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleCacheGet(KycStatusCacheEvent event, String correlationId) {
        try {
            log.info("Getting cache: key={}", event.getCacheKey());

            // Attempt to get from cache
            Optional<String> cachedStatus = cacheService.getKycStatus(event.getUserId());

            if (cachedStatus.isPresent()) {
                cacheHitCounter.increment();
                log.info("Cache hit: userId={}, status={}", event.getUserId(), cachedStatus.get());

                // Publish cache hit event
                publishCacheMetrics(event, true, "GET");
            } else {
                cacheMissCounter.increment();
                log.info("Cache miss: userId={}", event.getUserId());

                // Load from database and populate cache
                loadAndCacheFromDatabase(event.getUserId(), correlationId);

                // Publish cache miss event
                publishCacheMetrics(event, false, "GET");
            }

        } catch (Exception e) {
            log.error("Failed to get from cache: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleCacheDelete(KycStatusCacheEvent event, String correlationId) {
        try {
            log.info("Deleting cache: key={}", event.getCacheKey());

            cacheService.deleteKycStatus(event.getUserId());
            publishCacheMetrics(event, true, "DELETE");

            log.info("Cache deleted successfully: userId={}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to delete cache: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleCacheInvalidate(KycStatusCacheEvent event, String correlationId) {
        try {
            log.info("Invalidating cache: key={}", event.getCacheKey());

            if ("USER".equals(event.getCacheRegion())) {
                // Invalidate specific user cache
                cacheService.invalidateUserCache(event.getUserId());
            } else if ("REGION".equals(event.getCacheRegion())) {
                // Invalidate entire cache region
                cacheService.invalidateRegion(event.getCacheRegion());
            } else {
                // Invalidate specific key
                cacheService.deleteKycStatus(event.getUserId());
            }

            publishCacheMetrics(event, true, "INVALIDATE");

            log.info("Cache invalidated successfully: userId={}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to invalidate cache: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleCacheRefresh(KycStatusCacheEvent event, String correlationId) {
        try {
            log.info("Refreshing cache: key={}", event.getCacheKey());

            // Load fresh data from database
            Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());

            if (verification.isPresent()) {
                KYCVerification kycVerification = verification.get();

                // Update cache with fresh data
                cacheService.putKycStatus(
                    kycVerification.getUserId(),
                    kycVerification.getStatus(),
                    kycVerification.getKycLevel(),
                    kycVerification.getLastProcessedAt()
                );

                publishCacheMetrics(event, true, "REFRESH");

                log.info("Cache refreshed successfully: userId={}, status={}",
                    event.getUserId(), kycVerification.getStatus());
            } else {
                log.warn("No verification found for refresh: userId={}", event.getUserId());
                cacheService.deleteKycStatus(event.getUserId());
            }

        } catch (Exception e) {
            log.error("Failed to refresh cache: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleCacheEvict(KycStatusCacheEvent event, String correlationId) {
        try {
            log.info("Evicting cache: key={}, reason={}",
                event.getCacheKey(), event.getEvictionReason());

            cacheService.evictKycStatus(event.getUserId(), event.getEvictionReason());
            publishCacheMetrics(event, true, "EVICT");

            // Log eviction for monitoring
            auditService.logUserEvent("KYC_CACHE_EVICTED", event.getUserId(),
                Map.of("evictionReason", event.getEvictionReason(),
                    "cacheKey", event.getCacheKey(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            log.info("Cache evicted successfully: userId={}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to evict cache: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleBulkCacheUpdate(KycStatusCacheEvent event, String correlationId) {
        try {
            log.info("Bulk updating cache for region: {}", event.getCacheRegion());

            // Bulk update logic - could be based on updated fields
            if (event.getUpdatedFields() != null) {
                String[] fields = event.getUpdatedFields().split(",");
                cacheService.bulkUpdateKycCache(Arrays.asList(fields), correlationId);
            } else {
                // Full refresh of cache region
                cacheService.refreshCacheRegion(event.getCacheRegion());
            }

            publishCacheMetrics(event, true, "BULK_UPDATE");

            log.info("Bulk cache update completed for region: {}", event.getCacheRegion());

        } catch (Exception e) {
            log.error("Failed to bulk update cache: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleCacheWarmUp(KycStatusCacheEvent event, String correlationId) {
        try {
            log.info("Warming up cache for user: {}", event.getUserId());

            // Preload frequently accessed KYC data
            Optional<KYCVerification> verification = verificationRepository.findByUserId(event.getUserId());

            if (verification.isPresent()) {
                KYCVerification kycVerification = verification.get();

                // Warm up cache with verification data
                cacheService.warmUpUserCache(kycVerification);

                publishCacheMetrics(event, true, "WARM_UP");

                log.info("Cache warmed up successfully: userId={}", event.getUserId());
            } else {
                log.warn("No verification found for warm up: userId={}", event.getUserId());
            }

        } catch (Exception e) {
            log.error("Failed to warm up cache: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void handleCacheHealthCheck(KycStatusCacheEvent event, String correlationId) {
        try {
            log.info("Performing cache health check");

            // Check cache connectivity and performance
            boolean isHealthy = cacheService.performHealthCheck();

            // Publish health metrics
            if (isHealthy) {
                publishCacheMetrics(event, true, "HEALTH_CHECK");
                log.info("Cache health check passed");
            } else {
                publishCacheMetrics(event, false, "HEALTH_CHECK");
                log.warn("Cache health check failed");

                // Send alert if cache is unhealthy
                notificationService.sendOperationalAlert(
                    "KYC Cache Health Check Failed",
                    "KYC cache health check failed. Cache may be unavailable or performing poorly.",
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Failed to perform cache health check: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void loadAndCacheFromDatabase(String userId, String correlationId) {
        try {
            Optional<KYCVerification> verification = verificationRepository.findByUserId(userId);

            if (verification.isPresent()) {
                KYCVerification kycVerification = verification.get();

                // Cache the loaded data
                cacheService.putKycStatus(
                    kycVerification.getUserId(),
                    kycVerification.getStatus(),
                    kycVerification.getKycLevel(),
                    kycVerification.getLastProcessedAt()
                );

                log.info("Loaded and cached from database: userId={}, status={}",
                    userId, kycVerification.getStatus());
            } else {
                log.warn("No verification found in database: userId={}", userId);
            }

        } catch (Exception e) {
            log.error("Failed to load and cache from database: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void publishCacheMetrics(KycStatusCacheEvent event, boolean success, String operation) {
        try {
            kafkaTemplate.send("kyc-cache-metrics", Map.of(
                "userId", event.getUserId(),
                "operation", operation,
                "success", success,
                "cacheRegion", event.getCacheRegion(),
                "cacheSize", event.getCacheSize(),
                "timestamp", Instant.now()
            ));

        } catch (Exception e) {
            log.error("Failed to publish cache metrics: {}", e.getMessage());
            // Don't throw exception as this is not critical
        }
    }
}