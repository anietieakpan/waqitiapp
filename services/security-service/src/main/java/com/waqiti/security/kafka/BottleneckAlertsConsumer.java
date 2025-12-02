package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.PerformanceSecurityService;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class BottleneckAlertsConsumer {

    private final PerformanceSecurityService performanceSecurityService;
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
        successCounter = Counter.builder("bottleneck_alerts_processed_total")
            .description("Total number of successfully processed bottleneck alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("bottleneck_alerts_errors_total")
            .description("Total number of bottleneck alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("bottleneck_alerts_processing_duration")
            .description("Time taken to process bottleneck alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"bottleneck-alerts", "performance-bottlenecks", "system-bottleneck-alerts"},
        groupId = "security-service-bottleneck-alerts-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "bottleneck-alerts", fallbackMethod = "handleBottleneckAlertFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBottleneckAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("bottleneck-alert-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String alertId = (String) event.get("alertId");
            String serviceId = (String) event.get("serviceId");
            String bottleneckType = (String) event.get("bottleneckType");
            String severity = (String) event.get("severity");
            String eventKey = String.format("%s-%s-%s", alertId, serviceId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing bottleneck alert: alertId={}, serviceId={}, type={}, severity={}",
                alertId, serviceId, bottleneckType, severity);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String componentName = (String) event.get("componentName");
            Double performanceImpact = ((Number) event.getOrDefault("performanceImpact", 0.0)).doubleValue();
            LocalDateTime detectedAt = LocalDateTime.parse((String) event.get("detectedAt"));
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) event.getOrDefault("metrics", Map.of());
            @SuppressWarnings("unchecked")
            List<String> affectedOperations = (List<String>) event.getOrDefault("affectedOperations", List.of());
            String cause = (String) event.get("cause");
            @SuppressWarnings("unchecked")
            Map<String, Object> systemContext = (Map<String, Object>) event.getOrDefault("systemContext", Map.of());
            Double thresholdValue = ((Number) event.getOrDefault("thresholdValue", 0.0)).doubleValue();
            Double currentValue = ((Number) event.getOrDefault("currentValue", 0.0)).doubleValue();
            String trend = (String) event.getOrDefault("trend", "STABLE");

            // Process bottleneck alert based on type
            switch (bottleneckType) {
                case "CPU_BOTTLENECK":
                    processCpuBottleneck(alertId, serviceId, componentName, performanceImpact,
                        detectedAt, metrics, severity, correlationId);
                    break;

                case "MEMORY_BOTTLENECK":
                    processMemoryBottleneck(alertId, serviceId, componentName, performanceImpact,
                        detectedAt, metrics, severity, correlationId);
                    break;

                case "DATABASE_BOTTLENECK":
                    processDatabaseBottleneck(alertId, serviceId, componentName, performanceImpact,
                        detectedAt, metrics, affectedOperations, severity, correlationId);
                    break;

                case "NETWORK_BOTTLENECK":
                    processNetworkBottleneck(alertId, serviceId, componentName, performanceImpact,
                        detectedAt, metrics, severity, correlationId);
                    break;

                case "THREAD_POOL_BOTTLENECK":
                    processThreadPoolBottleneck(alertId, serviceId, componentName, performanceImpact,
                        detectedAt, metrics, severity, correlationId);
                    break;

                case "IO_BOTTLENECK":
                    processIoBottleneck(alertId, serviceId, componentName, performanceImpact,
                        detectedAt, metrics, severity, correlationId);
                    break;

                case "CONNECTION_POOL_BOTTLENECK":
                    processConnectionPoolBottleneck(alertId, serviceId, componentName,
                        performanceImpact, detectedAt, metrics, severity, correlationId);
                    break;

                default:
                    processGenericBottleneck(alertId, serviceId, bottleneckType, componentName,
                        performanceImpact, detectedAt, metrics, severity, correlationId);
                    break;
            }

            // Analyze security implications
            analyzeSecurityImplications(alertId, serviceId, bottleneckType, severity,
                performanceImpact, cause, systemContext, correlationId);

            // Handle critical bottlenecks
            if ("CRITICAL".equals(severity) || performanceImpact > 80.0) {
                handleCriticalBottleneck(alertId, serviceId, bottleneckType, performanceImpact,
                    affectedOperations, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("BOTTLENECK_ALERT_PROCESSED", serviceId,
                Map.of("alertId", alertId, "bottleneckType", bottleneckType, "severity", severity,
                    "performanceImpact", performanceImpact, "componentName", componentName,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process bottleneck alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("bottleneck-alerts-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBottleneckAlertFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("bottleneck-alert-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for bottleneck alert: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("bottleneck-alerts-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Bottleneck Alert Circuit Breaker Triggered",
                String.format("Bottleneck alert processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBottleneckAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-bottleneck-alert-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Bottleneck alert permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String alertId = (String) event.get("alertId");
            String serviceId = (String) event.get("serviceId");
            String bottleneckType = (String) event.get("bottleneckType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("BOTTLENECK_ALERT_DLT_EVENT", serviceId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "alertId", alertId, "bottleneckType", bottleneckType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Bottleneck Alert Dead Letter Event",
                String.format("Bottleneck alert %s for service %s sent to DLT: %s", alertId, serviceId, exceptionMessage),
                Map.of("alertId", alertId, "serviceId", serviceId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse bottleneck alert DLT event: {}", eventJson, ex);
        }
    }

    private void processCpuBottleneck(String alertId, String serviceId, String componentName,
                                    Double performanceImpact, LocalDateTime detectedAt,
                                    Map<String, Object> metrics, String severity, String correlationId) {
        try {
            performanceSecurityService.processCpuBottleneck(alertId, serviceId, componentName,
                performanceImpact, detectedAt, metrics, severity);

            log.info("CPU bottleneck processed: alertId={}, serviceId={}, impact={}%",
                alertId, serviceId, performanceImpact);

        } catch (Exception e) {
            log.error("Failed to process CPU bottleneck: alertId={}, serviceId={}",
                alertId, serviceId, e);
            throw new RuntimeException("CPU bottleneck processing failed", e);
        }
    }

    private void processMemoryBottleneck(String alertId, String serviceId, String componentName,
                                       Double performanceImpact, LocalDateTime detectedAt,
                                       Map<String, Object> metrics, String severity, String correlationId) {
        try {
            performanceSecurityService.processMemoryBottleneck(alertId, serviceId, componentName,
                performanceImpact, detectedAt, metrics, severity);

            log.info("Memory bottleneck processed: alertId={}, serviceId={}, impact={}%",
                alertId, serviceId, performanceImpact);

        } catch (Exception e) {
            log.error("Failed to process memory bottleneck: alertId={}, serviceId={}",
                alertId, serviceId, e);
            throw new RuntimeException("Memory bottleneck processing failed", e);
        }
    }

    private void processDatabaseBottleneck(String alertId, String serviceId, String componentName,
                                         Double performanceImpact, LocalDateTime detectedAt,
                                         Map<String, Object> metrics, List<String> affectedOperations,
                                         String severity, String correlationId) {
        try {
            performanceSecurityService.processDatabaseBottleneck(alertId, serviceId, componentName,
                performanceImpact, detectedAt, metrics, affectedOperations, severity);

            log.info("Database bottleneck processed: alertId={}, serviceId={}, operations={}",
                alertId, serviceId, affectedOperations.size());

        } catch (Exception e) {
            log.error("Failed to process database bottleneck: alertId={}, serviceId={}",
                alertId, serviceId, e);
            throw new RuntimeException("Database bottleneck processing failed", e);
        }
    }

    private void processNetworkBottleneck(String alertId, String serviceId, String componentName,
                                        Double performanceImpact, LocalDateTime detectedAt,
                                        Map<String, Object> metrics, String severity, String correlationId) {
        try {
            performanceSecurityService.processNetworkBottleneck(alertId, serviceId, componentName,
                performanceImpact, detectedAt, metrics, severity);

            log.info("Network bottleneck processed: alertId={}, serviceId={}, impact={}%",
                alertId, serviceId, performanceImpact);

        } catch (Exception e) {
            log.error("Failed to process network bottleneck: alertId={}, serviceId={}",
                alertId, serviceId, e);
            throw new RuntimeException("Network bottleneck processing failed", e);
        }
    }

    private void processThreadPoolBottleneck(String alertId, String serviceId, String componentName,
                                           Double performanceImpact, LocalDateTime detectedAt,
                                           Map<String, Object> metrics, String severity, String correlationId) {
        try {
            performanceSecurityService.processThreadPoolBottleneck(alertId, serviceId, componentName,
                performanceImpact, detectedAt, metrics, severity);

            log.info("Thread pool bottleneck processed: alertId={}, serviceId={}, impact={}%",
                alertId, serviceId, performanceImpact);

        } catch (Exception e) {
            log.error("Failed to process thread pool bottleneck: alertId={}, serviceId={}",
                alertId, serviceId, e);
            throw new RuntimeException("Thread pool bottleneck processing failed", e);
        }
    }

    private void processIoBottleneck(String alertId, String serviceId, String componentName,
                                   Double performanceImpact, LocalDateTime detectedAt,
                                   Map<String, Object> metrics, String severity, String correlationId) {
        try {
            performanceSecurityService.processIoBottleneck(alertId, serviceId, componentName,
                performanceImpact, detectedAt, metrics, severity);

            log.info("IO bottleneck processed: alertId={}, serviceId={}, impact={}%",
                alertId, serviceId, performanceImpact);

        } catch (Exception e) {
            log.error("Failed to process IO bottleneck: alertId={}, serviceId={}",
                alertId, serviceId, e);
            throw new RuntimeException("IO bottleneck processing failed", e);
        }
    }

    private void processConnectionPoolBottleneck(String alertId, String serviceId, String componentName,
                                               Double performanceImpact, LocalDateTime detectedAt,
                                               Map<String, Object> metrics, String severity,
                                               String correlationId) {
        try {
            performanceSecurityService.processConnectionPoolBottleneck(alertId, serviceId,
                componentName, performanceImpact, detectedAt, metrics, severity);

            log.info("Connection pool bottleneck processed: alertId={}, serviceId={}, impact={}%",
                alertId, serviceId, performanceImpact);

        } catch (Exception e) {
            log.error("Failed to process connection pool bottleneck: alertId={}, serviceId={}",
                alertId, serviceId, e);
            throw new RuntimeException("Connection pool bottleneck processing failed", e);
        }
    }

    private void processGenericBottleneck(String alertId, String serviceId, String bottleneckType,
                                        String componentName, Double performanceImpact,
                                        LocalDateTime detectedAt, Map<String, Object> metrics,
                                        String severity, String correlationId) {
        try {
            performanceSecurityService.processGenericBottleneck(alertId, serviceId, bottleneckType,
                componentName, performanceImpact, detectedAt, metrics, severity);

            log.info("Generic bottleneck processed: alertId={}, serviceId={}, type={}",
                alertId, serviceId, bottleneckType);

        } catch (Exception e) {
            log.error("Failed to process generic bottleneck: alertId={}, serviceId={}",
                alertId, serviceId, e);
            throw new RuntimeException("Generic bottleneck processing failed", e);
        }
    }

    private void analyzeSecurityImplications(String alertId, String serviceId, String bottleneckType,
                                           String severity, Double performanceImpact, String cause,
                                           Map<String, Object> systemContext, String correlationId) {
        try {
            threatResponseService.analyzeBottleneckSecurityImplications(alertId, serviceId,
                bottleneckType, severity, performanceImpact, cause, systemContext);

            log.debug("Security implications analyzed: alertId={}, serviceId={}, type={}",
                alertId, serviceId, bottleneckType);

        } catch (Exception e) {
            log.error("Failed to analyze security implications: alertId={}, serviceId={}",
                alertId, serviceId, e);
            // Don't throw exception as security analysis failure shouldn't block processing
        }
    }

    private void handleCriticalBottleneck(String alertId, String serviceId, String bottleneckType,
                                        Double performanceImpact, List<String> affectedOperations,
                                        String correlationId) {
        try {
            performanceSecurityService.handleCriticalBottleneck(alertId, serviceId, bottleneckType,
                performanceImpact, affectedOperations);

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Critical System Bottleneck Detected",
                String.format("Critical bottleneck in service %s: %s with %.1f%% performance impact",
                    serviceId, bottleneckType, performanceImpact),
                Map.of("alertId", alertId, "serviceId", serviceId, "performanceImpact", performanceImpact,
                    "correlationId", correlationId)
            );

            log.error("Critical bottleneck handled: alertId={}, serviceId={}, impact={}%",
                alertId, serviceId, performanceImpact);

        } catch (Exception e) {
            log.error("Failed to handle critical bottleneck: alertId={}, serviceId={}",
                alertId, serviceId, e);
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