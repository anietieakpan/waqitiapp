package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.CacheSecurityService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.security.service.ThreatResponseService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
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

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheMemoryAlertsConsumer {

    private final CacheSecurityService cacheSecurityService;
    private final SecurityNotificationService securityNotificationService;
    private final ThreatResponseService threatResponseService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("cache_memory_alerts_processed_total")
            .description("Total number of successfully processed cache memory alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("cache_memory_alerts_errors_total")
            .description("Total number of cache memory alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("cache_memory_alerts_processing_duration")
            .description("Time taken to process cache memory alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"cache-memory-alerts", "cache-memory-warnings", "memory-cache-alerts"},
        groupId = "security-service-cache-memory-alerts-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "2"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "cache-memory-alerts", fallbackMethod = "handleCacheMemoryAlertFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCacheMemoryAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("cache-memory-alert-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String alertId = (String) event.get("alertId");
            String cacheInstanceId = (String) event.get("cacheInstanceId");
            String alertType = (String) event.get("alertType");
            String severity = (String) event.get("severity");
            String eventKey = String.format("%s-%s-%s", alertId, cacheInstanceId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing cache memory alert: alertId={}, cacheInstanceId={}, type={}, severity={}",
                alertId, cacheInstanceId, alertType, severity);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Double memoryUsagePercent = ((Number) event.get("memoryUsagePercent")).doubleValue();
            Long totalMemoryMB = ((Number) event.get("totalMemoryMB")).longValue();
            Long usedMemoryMB = ((Number) event.get("usedMemoryMB")).longValue();
            Long availableMemoryMB = ((Number) event.get("availableMemoryMB")).longValue();
            LocalDateTime detectedAt = LocalDateTime.parse((String) event.get("detectedAt"));
            @SuppressWarnings("unchecked")
            Map<String, Object> cacheMetrics = (Map<String, Object>) event.getOrDefault("cacheMetrics", Map.of());
            Double threshold = ((Number) event.getOrDefault("threshold", 0.0)).doubleValue();
            String cacheType = (String) event.get("cacheType");
            String nodeId = (String) event.get("nodeId");
            Boolean isCluster = (Boolean) event.getOrDefault("isCluster", false);

            // Process cache memory alert based on type
            switch (alertType) {
                case "MEMORY_HIGH_USAGE":
                    processMemoryHighUsage(alertId, cacheInstanceId, memoryUsagePercent,
                        totalMemoryMB, usedMemoryMB, threshold, severity, correlationId);
                    break;

                case "MEMORY_CRITICAL":
                    processMemoryCritical(alertId, cacheInstanceId, memoryUsagePercent,
                        availableMemoryMB, cacheMetrics, severity, correlationId);
                    break;

                case "MEMORY_LEAK_SUSPECTED":
                    processMemoryLeakSuspected(alertId, cacheInstanceId, memoryUsagePercent,
                        cacheMetrics, detectedAt, severity, correlationId);
                    break;

                case "MEMORY_FRAGMENTATION":
                    processMemoryFragmentation(alertId, cacheInstanceId, cacheMetrics,
                        severity, correlationId);
                    break;

                case "EVICTION_RATE_HIGH":
                    processEvictionRateHigh(alertId, cacheInstanceId, cacheMetrics,
                        severity, correlationId);
                    break;

                case "OOM_RISK":
                    processOutOfMemoryRisk(alertId, cacheInstanceId, memoryUsagePercent,
                        availableMemoryMB, cacheMetrics, severity, correlationId);
                    break;

                default:
                    processGenericMemoryAlert(alertId, cacheInstanceId, alertType,
                        memoryUsagePercent, cacheMetrics, severity, correlationId);
                    break;
            }

            // Analyze security implications
            analyzeCacheSecurityImplications(alertId, cacheInstanceId, alertType, severity,
                memoryUsagePercent, cacheType, isCluster, correlationId);

            // Handle critical alerts
            if ("CRITICAL".equals(severity) || memoryUsagePercent > 90.0) {
                handleCriticalMemoryAlert(alertId, cacheInstanceId, alertType, memoryUsagePercent,
                    availableMemoryMB, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("CACHE_MEMORY_ALERT_PROCESSED", cacheInstanceId,
                Map.of("alertId", alertId, "alertType", alertType, "severity", severity,
                    "memoryUsagePercent", memoryUsagePercent, "cacheType", cacheType,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process cache memory alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("cache-memory-alerts-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCacheMemoryAlertFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("cache-memory-alert-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for cache memory alert: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("cache-memory-alerts-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Cache Memory Alert Circuit Breaker Triggered",
                String.format("Cache memory alert processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCacheMemoryAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-cache-memory-alert-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Cache memory alert permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String alertId = (String) event.get("alertId");
            String cacheInstanceId = (String) event.get("cacheInstanceId");
            String alertType = (String) event.get("alertType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("CACHE_MEMORY_ALERT_DLT_EVENT", cacheInstanceId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "alertId", alertId, "alertType", alertType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Cache Memory Alert Dead Letter Event",
                String.format("Cache memory alert %s for instance %s sent to DLT: %s", alertId, cacheInstanceId, exceptionMessage),
                Map.of("alertId", alertId, "cacheInstanceId", cacheInstanceId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse cache memory alert DLT event: {}", eventJson, ex);
        }
    }

    private void processMemoryHighUsage(String alertId, String cacheInstanceId, Double memoryUsagePercent,
                                      Long totalMemoryMB, Long usedMemoryMB, Double threshold,
                                      String severity, String correlationId) {
        try {
            cacheSecurityService.processMemoryHighUsage(alertId, cacheInstanceId, memoryUsagePercent,
                totalMemoryMB, usedMemoryMB, threshold, severity);

            log.info("Memory high usage processed: alertId={}, cacheInstanceId={}, usage={}%",
                alertId, cacheInstanceId, memoryUsagePercent);

        } catch (Exception e) {
            log.error("Failed to process memory high usage: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Memory high usage processing failed", e);
        }
    }

    private void processMemoryCritical(String alertId, String cacheInstanceId, Double memoryUsagePercent,
                                     Long availableMemoryMB, Map<String, Object> cacheMetrics,
                                     String severity, String correlationId) {
        try {
            cacheSecurityService.processMemoryCritical(alertId, cacheInstanceId, memoryUsagePercent,
                availableMemoryMB, cacheMetrics, severity);

            log.error("Memory critical processed: alertId={}, cacheInstanceId={}, usage={}%, available={}MB",
                alertId, cacheInstanceId, memoryUsagePercent, availableMemoryMB);

        } catch (Exception e) {
            log.error("Failed to process memory critical: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Memory critical processing failed", e);
        }
    }

    private void processMemoryLeakSuspected(String alertId, String cacheInstanceId,
                                          Double memoryUsagePercent, Map<String, Object> cacheMetrics,
                                          LocalDateTime detectedAt, String severity, String correlationId) {
        try {
            cacheSecurityService.processMemoryLeakSuspected(alertId, cacheInstanceId,
                memoryUsagePercent, cacheMetrics, detectedAt, severity);

            log.warn("Memory leak suspected processed: alertId={}, cacheInstanceId={}, usage={}%",
                alertId, cacheInstanceId, memoryUsagePercent);

        } catch (Exception e) {
            log.error("Failed to process memory leak suspected: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Memory leak suspected processing failed", e);
        }
    }

    private void processMemoryFragmentation(String alertId, String cacheInstanceId,
                                          Map<String, Object> cacheMetrics, String severity,
                                          String correlationId) {
        try {
            cacheSecurityService.processMemoryFragmentation(alertId, cacheInstanceId,
                cacheMetrics, severity);

            log.info("Memory fragmentation processed: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId);

        } catch (Exception e) {
            log.error("Failed to process memory fragmentation: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Memory fragmentation processing failed", e);
        }
    }

    private void processEvictionRateHigh(String alertId, String cacheInstanceId,
                                       Map<String, Object> cacheMetrics, String severity,
                                       String correlationId) {
        try {
            cacheSecurityService.processEvictionRateHigh(alertId, cacheInstanceId,
                cacheMetrics, severity);

            log.info("Eviction rate high processed: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId);

        } catch (Exception e) {
            log.error("Failed to process eviction rate high: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Eviction rate high processing failed", e);
        }
    }

    private void processOutOfMemoryRisk(String alertId, String cacheInstanceId,
                                      Double memoryUsagePercent, Long availableMemoryMB,
                                      Map<String, Object> cacheMetrics, String severity,
                                      String correlationId) {
        try {
            cacheSecurityService.processOutOfMemoryRisk(alertId, cacheInstanceId,
                memoryUsagePercent, availableMemoryMB, cacheMetrics, severity);

            log.error("Out of memory risk processed: alertId={}, cacheInstanceId={}, available={}MB",
                alertId, cacheInstanceId, availableMemoryMB);

        } catch (Exception e) {
            log.error("Failed to process out of memory risk: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Out of memory risk processing failed", e);
        }
    }

    private void processGenericMemoryAlert(String alertId, String cacheInstanceId, String alertType,
                                         Double memoryUsagePercent, Map<String, Object> cacheMetrics,
                                         String severity, String correlationId) {
        try {
            cacheSecurityService.processGenericMemoryAlert(alertId, cacheInstanceId, alertType,
                memoryUsagePercent, cacheMetrics, severity);

            log.info("Generic memory alert processed: alertId={}, cacheInstanceId={}, type={}",
                alertId, cacheInstanceId, alertType);

        } catch (Exception e) {
            log.error("Failed to process generic memory alert: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Generic memory alert processing failed", e);
        }
    }

    private void analyzeCacheSecurityImplications(String alertId, String cacheInstanceId,
                                                String alertType, String severity,
                                                Double memoryUsagePercent, String cacheType,
                                                Boolean isCluster, String correlationId) {
        try {
            threatResponseService.analyzeCacheSecurityImplications(alertId, cacheInstanceId,
                alertType, severity, memoryUsagePercent, cacheType, isCluster);

            log.debug("Cache security implications analyzed: alertId={}, cacheInstanceId={}, type={}",
                alertId, cacheInstanceId, alertType);

        } catch (Exception e) {
            log.error("Failed to analyze cache security implications: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            // Don't throw exception as security analysis failure shouldn't block processing
        }
    }

    private void handleCriticalMemoryAlert(String alertId, String cacheInstanceId, String alertType,
                                         Double memoryUsagePercent, Long availableMemoryMB,
                                         String correlationId) {
        try {
            cacheSecurityService.handleCriticalMemoryAlert(alertId, cacheInstanceId, alertType,
                memoryUsagePercent, availableMemoryMB);

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Critical Cache Memory Alert",
                String.format("Critical memory issue in cache %s: %s with %.1f%% usage",
                    cacheInstanceId, alertType, memoryUsagePercent),
                Map.of("alertId", alertId, "cacheInstanceId", cacheInstanceId,
                    "memoryUsagePercent", memoryUsagePercent, "correlationId", correlationId)
            );

            log.error("Critical memory alert handled: alertId={}, cacheInstanceId={}, usage={}%",
                alertId, cacheInstanceId, memoryUsagePercent);

        } catch (Exception e) {
            log.error("Failed to handle critical memory alert: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            // Don't throw exception as critical handling failure shouldn't block processing
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
}