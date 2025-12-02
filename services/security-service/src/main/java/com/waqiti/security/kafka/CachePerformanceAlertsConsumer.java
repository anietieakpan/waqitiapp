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
public class CachePerformanceAlertsConsumer {

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
        successCounter = Counter.builder("cache_performance_alerts_processed_total")
            .description("Total number of successfully processed cache performance alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("cache_performance_alerts_errors_total")
            .description("Total number of cache performance alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("cache_performance_alerts_processing_duration")
            .description("Time taken to process cache performance alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"cache-performance-alerts", "cache-performance-warnings", "cache-latency-alerts"},
        groupId = "security-service-cache-performance-alerts-group",
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
    @CircuitBreaker(name = "cache-performance-alerts", fallbackMethod = "handleCachePerformanceAlertFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCachePerformanceAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("cache-performance-alert-p%d-o%d", partition, offset);

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

            log.info("Processing cache performance alert: alertId={}, cacheInstanceId={}, type={}, severity={}",
                alertId, cacheInstanceId, alertType, severity);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Double hitRatio = ((Number) event.getOrDefault("hitRatio", 0.0)).doubleValue();
            Double missRatio = ((Number) event.getOrDefault("missRatio", 0.0)).doubleValue();
            Double averageLatencyMs = ((Number) event.getOrDefault("averageLatencyMs", 0.0)).doubleValue();
            Double maxLatencyMs = ((Number) event.getOrDefault("maxLatencyMs", 0.0)).doubleValue();
            Long operationsPerSecond = ((Number) event.getOrDefault("operationsPerSecond", 0L)).longValue();
            LocalDateTime detectedAt = LocalDateTime.parse((String) event.get("detectedAt"));
            @SuppressWarnings("unchecked")
            Map<String, Object> performanceMetrics = (Map<String, Object>) event.getOrDefault("performanceMetrics", Map.of());
            String cacheType = (String) event.get("cacheType");
            Double threshold = ((Number) event.getOrDefault("threshold", 0.0)).doubleValue();
            String nodeId = (String) event.get("nodeId");
            Boolean isCluster = (Boolean) event.getOrDefault("isCluster", false);

            // Process cache performance alert based on type
            switch (alertType) {
                case "LOW_HIT_RATIO":
                    processLowHitRatio(alertId, cacheInstanceId, hitRatio, missRatio, threshold,
                        performanceMetrics, severity, correlationId);
                    break;

                case "HIGH_LATENCY":
                    processHighLatency(alertId, cacheInstanceId, averageLatencyMs, maxLatencyMs,
                        threshold, performanceMetrics, severity, correlationId);
                    break;

                case "THROUGHPUT_DEGRADATION":
                    processThroughputDegradation(alertId, cacheInstanceId, operationsPerSecond,
                        performanceMetrics, severity, correlationId);
                    break;

                case "CONNECTION_TIMEOUT":
                    processConnectionTimeout(alertId, cacheInstanceId, performanceMetrics,
                        severity, correlationId);
                    break;

                case "SLOW_QUERY":
                    processSlowQuery(alertId, cacheInstanceId, averageLatencyMs, maxLatencyMs,
                        performanceMetrics, severity, correlationId);
                    break;

                case "CACHE_UNAVAILABLE":
                    processCacheUnavailable(alertId, cacheInstanceId, detectedAt,
                        performanceMetrics, severity, correlationId);
                    break;

                case "PERFORMANCE_DEGRADATION":
                    processPerformanceDegradation(alertId, cacheInstanceId, hitRatio, averageLatencyMs,
                        operationsPerSecond, performanceMetrics, severity, correlationId);
                    break;

                default:
                    processGenericPerformanceAlert(alertId, cacheInstanceId, alertType,
                        performanceMetrics, severity, correlationId);
                    break;
            }

            // Analyze security implications
            analyzeCachePerformanceSecurityImplications(alertId, cacheInstanceId, alertType,
                severity, hitRatio, averageLatencyMs, cacheType, isCluster, correlationId);

            // Handle critical performance alerts
            if ("CRITICAL".equals(severity) || hitRatio < 10.0 || averageLatencyMs > 1000.0) {
                handleCriticalPerformanceAlert(alertId, cacheInstanceId, alertType, hitRatio,
                    averageLatencyMs, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("CACHE_PERFORMANCE_ALERT_PROCESSED", cacheInstanceId,
                Map.of("alertId", alertId, "alertType", alertType, "severity", severity,
                    "hitRatio", hitRatio, "averageLatencyMs", averageLatencyMs,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process cache performance alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("cache-performance-alerts-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCachePerformanceAlertFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("cache-performance-alert-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for cache performance alert: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("cache-performance-alerts-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Cache Performance Alert Circuit Breaker Triggered",
                String.format("Cache performance alert processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCachePerformanceAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-cache-performance-alert-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Cache performance alert permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String alertId = (String) event.get("alertId");
            String cacheInstanceId = (String) event.get("cacheInstanceId");
            String alertType = (String) event.get("alertType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("CACHE_PERFORMANCE_ALERT_DLT_EVENT", cacheInstanceId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "alertId", alertId, "alertType", alertType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Cache Performance Alert Dead Letter Event",
                String.format("Cache performance alert %s for instance %s sent to DLT: %s", alertId, cacheInstanceId, exceptionMessage),
                Map.of("alertId", alertId, "cacheInstanceId", cacheInstanceId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse cache performance alert DLT event: {}", eventJson, ex);
        }
    }

    private void processLowHitRatio(String alertId, String cacheInstanceId, Double hitRatio,
                                  Double missRatio, Double threshold, Map<String, Object> performanceMetrics,
                                  String severity, String correlationId) {
        try {
            cacheSecurityService.processLowHitRatio(alertId, cacheInstanceId, hitRatio, missRatio,
                threshold, performanceMetrics, severity);

            log.warn("Low hit ratio processed: alertId={}, cacheInstanceId={}, hitRatio={}%",
                alertId, cacheInstanceId, hitRatio);

        } catch (Exception e) {
            log.error("Failed to process low hit ratio: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Low hit ratio processing failed", e);
        }
    }

    private void processHighLatency(String alertId, String cacheInstanceId, Double averageLatencyMs,
                                  Double maxLatencyMs, Double threshold, Map<String, Object> performanceMetrics,
                                  String severity, String correlationId) {
        try {
            cacheSecurityService.processHighLatency(alertId, cacheInstanceId, averageLatencyMs,
                maxLatencyMs, threshold, performanceMetrics, severity);

            log.warn("High latency processed: alertId={}, cacheInstanceId={}, avgLatency={}ms",
                alertId, cacheInstanceId, averageLatencyMs);

        } catch (Exception e) {
            log.error("Failed to process high latency: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("High latency processing failed", e);
        }
    }

    private void processThroughputDegradation(String alertId, String cacheInstanceId,
                                            Long operationsPerSecond, Map<String, Object> performanceMetrics,
                                            String severity, String correlationId) {
        try {
            cacheSecurityService.processThroughputDegradation(alertId, cacheInstanceId,
                operationsPerSecond, performanceMetrics, severity);

            log.warn("Throughput degradation processed: alertId={}, cacheInstanceId={}, ops/sec={}",
                alertId, cacheInstanceId, operationsPerSecond);

        } catch (Exception e) {
            log.error("Failed to process throughput degradation: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Throughput degradation processing failed", e);
        }
    }

    private void processConnectionTimeout(String alertId, String cacheInstanceId,
                                        Map<String, Object> performanceMetrics, String severity,
                                        String correlationId) {
        try {
            cacheSecurityService.processConnectionTimeout(alertId, cacheInstanceId,
                performanceMetrics, severity);

            log.error("Connection timeout processed: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId);

        } catch (Exception e) {
            log.error("Failed to process connection timeout: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Connection timeout processing failed", e);
        }
    }

    private void processSlowQuery(String alertId, String cacheInstanceId, Double averageLatencyMs,
                                Double maxLatencyMs, Map<String, Object> performanceMetrics,
                                String severity, String correlationId) {
        try {
            cacheSecurityService.processSlowQuery(alertId, cacheInstanceId, averageLatencyMs,
                maxLatencyMs, performanceMetrics, severity);

            log.warn("Slow query processed: alertId={}, cacheInstanceId={}, maxLatency={}ms",
                alertId, cacheInstanceId, maxLatencyMs);

        } catch (Exception e) {
            log.error("Failed to process slow query: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Slow query processing failed", e);
        }
    }

    private void processCacheUnavailable(String alertId, String cacheInstanceId, LocalDateTime detectedAt,
                                       Map<String, Object> performanceMetrics, String severity,
                                       String correlationId) {
        try {
            cacheSecurityService.processCacheUnavailable(alertId, cacheInstanceId, detectedAt,
                performanceMetrics, severity);

            log.error("Cache unavailable processed: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId);

        } catch (Exception e) {
            log.error("Failed to process cache unavailable: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Cache unavailable processing failed", e);
        }
    }

    private void processPerformanceDegradation(String alertId, String cacheInstanceId, Double hitRatio,
                                             Double averageLatencyMs, Long operationsPerSecond,
                                             Map<String, Object> performanceMetrics, String severity,
                                             String correlationId) {
        try {
            cacheSecurityService.processPerformanceDegradation(alertId, cacheInstanceId, hitRatio,
                averageLatencyMs, operationsPerSecond, performanceMetrics, severity);

            log.warn("Performance degradation processed: alertId={}, cacheInstanceId={}, hitRatio={}%",
                alertId, cacheInstanceId, hitRatio);

        } catch (Exception e) {
            log.error("Failed to process performance degradation: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Performance degradation processing failed", e);
        }
    }

    private void processGenericPerformanceAlert(String alertId, String cacheInstanceId, String alertType,
                                              Map<String, Object> performanceMetrics, String severity,
                                              String correlationId) {
        try {
            cacheSecurityService.processGenericPerformanceAlert(alertId, cacheInstanceId, alertType,
                performanceMetrics, severity);

            log.info("Generic performance alert processed: alertId={}, cacheInstanceId={}, type={}",
                alertId, cacheInstanceId, alertType);

        } catch (Exception e) {
            log.error("Failed to process generic performance alert: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            throw new RuntimeException("Generic performance alert processing failed", e);
        }
    }

    private void analyzeCachePerformanceSecurityImplications(String alertId, String cacheInstanceId,
                                                           String alertType, String severity, Double hitRatio,
                                                           Double averageLatencyMs, String cacheType,
                                                           Boolean isCluster, String correlationId) {
        try {
            threatResponseService.analyzeCachePerformanceSecurityImplications(alertId, cacheInstanceId,
                alertType, severity, hitRatio, averageLatencyMs, cacheType, isCluster);

            log.debug("Cache performance security implications analyzed: alertId={}, cacheInstanceId={}, type={}",
                alertId, cacheInstanceId, alertType);

        } catch (Exception e) {
            log.error("Failed to analyze cache performance security implications: alertId={}, cacheInstanceId={}",
                alertId, cacheInstanceId, e);
            // Don't throw exception as security analysis failure shouldn't block processing
        }
    }

    private void handleCriticalPerformanceAlert(String alertId, String cacheInstanceId, String alertType,
                                              Double hitRatio, Double averageLatencyMs, String correlationId) {
        try {
            cacheSecurityService.handleCriticalPerformanceAlert(alertId, cacheInstanceId, alertType,
                hitRatio, averageLatencyMs);

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Critical Cache Performance Alert",
                String.format("Critical performance issue in cache %s: %s (hit ratio: %.1f%%, latency: %.1fms)",
                    cacheInstanceId, alertType, hitRatio, averageLatencyMs),
                Map.of("alertId", alertId, "cacheInstanceId", cacheInstanceId,
                    "hitRatio", hitRatio, "averageLatencyMs", averageLatencyMs,
                    "correlationId", correlationId)
            );

            log.error("Critical performance alert handled: alertId={}, cacheInstanceId={}, hitRatio={}%",
                alertId, cacheInstanceId, hitRatio);

        } catch (Exception e) {
            log.error("Failed to handle critical performance alert: alertId={}, cacheInstanceId={}",
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