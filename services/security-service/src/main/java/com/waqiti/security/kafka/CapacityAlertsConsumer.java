package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.CapacitySecurityService;
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
public class CapacityAlertsConsumer {

    private final CapacitySecurityService capacitySecurityService;
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
        successCounter = Counter.builder("capacity_alerts_processed_total")
            .description("Total number of successfully processed capacity alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("capacity_alerts_errors_total")
            .description("Total number of capacity alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("capacity_alerts_processing_duration")
            .description("Time taken to process capacity alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"capacity-alerts", "system-capacity-warnings", "resource-capacity-alerts"},
        groupId = "security-service-capacity-alerts-group",
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
    @CircuitBreaker(name = "capacity-alerts", fallbackMethod = "handleCapacityAlertFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCapacityAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("capacity-alert-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String alertId = (String) event.get("alertId");
            String resourceType = (String) event.get("resourceType");
            String alertType = (String) event.get("alertType");
            String severity = (String) event.get("severity");
            String eventKey = String.format("%s-%s-%s", alertId, resourceType, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing capacity alert: alertId={}, resourceType={}, type={}, severity={}",
                alertId, resourceType, alertType, severity);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String serviceId = (String) event.get("serviceId");
            Double currentUtilization = ((Number) event.get("currentUtilization")).doubleValue();
            Double threshold = ((Number) event.get("threshold")).doubleValue();
            String unit = (String) event.get("unit");
            LocalDateTime detectedAt = LocalDateTime.parse((String) event.get("detectedAt"));
            @SuppressWarnings("unchecked")
            Map<String, Object> capacityMetrics = (Map<String, Object>) event.getOrDefault("capacityMetrics", Map.of());
            String nodeId = (String) event.get("nodeId");
            String region = (String) event.get("region");
            Long totalCapacity = ((Number) event.getOrDefault("totalCapacity", 0L)).longValue();
            Long usedCapacity = ((Number) event.getOrDefault("usedCapacity", 0L)).longValue();
            Long availableCapacity = ((Number) event.getOrDefault("availableCapacity", 0L)).longValue();

            // Process capacity alert based on type
            switch (alertType) {
                case "HIGH_UTILIZATION":
                    processHighUtilization(alertId, resourceType, serviceId, currentUtilization,
                        threshold, unit, capacityMetrics, severity, correlationId);
                    break;

                case "CAPACITY_WARNING":
                    processCapacityWarning(alertId, resourceType, serviceId, currentUtilization,
                        totalCapacity, usedCapacity, availableCapacity, severity, correlationId);
                    break;

                case "RESOURCE_EXHAUSTION":
                    processResourceExhaustion(alertId, resourceType, serviceId, currentUtilization,
                        availableCapacity, capacityMetrics, severity, correlationId);
                    break;

                case "SCALING_THRESHOLD_REACHED":
                    processScalingThresholdReached(alertId, resourceType, serviceId, currentUtilization,
                        threshold, capacityMetrics, severity, correlationId);
                    break;

                case "CAPACITY_LIMIT_EXCEEDED":
                    processCapacityLimitExceeded(alertId, resourceType, serviceId, currentUtilization,
                        totalCapacity, capacityMetrics, severity, correlationId);
                    break;

                case "RESOURCE_SHORTAGE":
                    processResourceShortage(alertId, resourceType, serviceId, availableCapacity,
                        capacityMetrics, severity, correlationId);
                    break;

                default:
                    processGenericCapacityAlert(alertId, resourceType, alertType, serviceId,
                        currentUtilization, capacityMetrics, severity, correlationId);
                    break;
            }

            // Analyze security implications of capacity issues
            analyzeCapacitySecurityImplications(alertId, resourceType, alertType, severity,
                currentUtilization, serviceId, nodeId, region, correlationId);

            // Handle critical capacity alerts
            if ("CRITICAL".equals(severity) || currentUtilization > 95.0) {
                handleCriticalCapacityAlert(alertId, resourceType, serviceId, currentUtilization,
                    availableCapacity, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("CAPACITY_ALERT_PROCESSED", serviceId,
                Map.of("alertId", alertId, "resourceType", resourceType, "alertType", alertType,
                    "severity", severity, "currentUtilization", currentUtilization,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process capacity alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("capacity-alerts-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCapacityAlertFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("capacity-alert-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for capacity alert: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("capacity-alerts-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Capacity Alert Circuit Breaker Triggered",
                String.format("Capacity alert processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCapacityAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-capacity-alert-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Capacity alert permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String alertId = (String) event.get("alertId");
            String resourceType = (String) event.get("resourceType");
            String alertType = (String) event.get("alertType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("CAPACITY_ALERT_DLT_EVENT", resourceType,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "alertId", alertId, "alertType", alertType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Capacity Alert Dead Letter Event",
                String.format("Capacity alert %s for resource %s sent to DLT: %s", alertId, resourceType, exceptionMessage),
                Map.of("alertId", alertId, "resourceType", resourceType, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse capacity alert DLT event: {}", eventJson, ex);
        }
    }

    private void processHighUtilization(String alertId, String resourceType, String serviceId,
                                      Double currentUtilization, Double threshold, String unit,
                                      Map<String, Object> capacityMetrics, String severity,
                                      String correlationId) {
        try {
            capacitySecurityService.processHighUtilization(alertId, resourceType, serviceId,
                currentUtilization, threshold, unit, capacityMetrics, severity);

            log.warn("High utilization processed: alertId={}, resourceType={}, utilization={}%",
                alertId, resourceType, currentUtilization);

        } catch (Exception e) {
            log.error("Failed to process high utilization: alertId={}, resourceType={}",
                alertId, resourceType, e);
            throw new RuntimeException("High utilization processing failed", e);
        }
    }

    private void processCapacityWarning(String alertId, String resourceType, String serviceId,
                                      Double currentUtilization, Long totalCapacity, Long usedCapacity,
                                      Long availableCapacity, String severity, String correlationId) {
        try {
            capacitySecurityService.processCapacityWarning(alertId, resourceType, serviceId,
                currentUtilization, totalCapacity, usedCapacity, availableCapacity, severity);

            log.warn("Capacity warning processed: alertId={}, resourceType={}, available={}",
                alertId, resourceType, availableCapacity);

        } catch (Exception e) {
            log.error("Failed to process capacity warning: alertId={}, resourceType={}",
                alertId, resourceType, e);
            throw new RuntimeException("Capacity warning processing failed", e);
        }
    }

    private void processResourceExhaustion(String alertId, String resourceType, String serviceId,
                                         Double currentUtilization, Long availableCapacity,
                                         Map<String, Object> capacityMetrics, String severity,
                                         String correlationId) {
        try {
            capacitySecurityService.processResourceExhaustion(alertId, resourceType, serviceId,
                currentUtilization, availableCapacity, capacityMetrics, severity);

            log.error("Resource exhaustion processed: alertId={}, resourceType={}, available={}",
                alertId, resourceType, availableCapacity);

        } catch (Exception e) {
            log.error("Failed to process resource exhaustion: alertId={}, resourceType={}",
                alertId, resourceType, e);
            throw new RuntimeException("Resource exhaustion processing failed", e);
        }
    }

    private void processScalingThresholdReached(String alertId, String resourceType, String serviceId,
                                               Double currentUtilization, Double threshold,
                                               Map<String, Object> capacityMetrics, String severity,
                                               String correlationId) {
        try {
            capacitySecurityService.processScalingThresholdReached(alertId, resourceType, serviceId,
                currentUtilization, threshold, capacityMetrics, severity);

            log.info("Scaling threshold reached processed: alertId={}, resourceType={}, threshold={}%",
                alertId, resourceType, threshold);

        } catch (Exception e) {
            log.error("Failed to process scaling threshold reached: alertId={}, resourceType={}",
                alertId, resourceType, e);
            throw new RuntimeException("Scaling threshold reached processing failed", e);
        }
    }

    private void processCapacityLimitExceeded(String alertId, String resourceType, String serviceId,
                                            Double currentUtilization, Long totalCapacity,
                                            Map<String, Object> capacityMetrics, String severity,
                                            String correlationId) {
        try {
            capacitySecurityService.processCapacityLimitExceeded(alertId, resourceType, serviceId,
                currentUtilization, totalCapacity, capacityMetrics, severity);

            log.error("Capacity limit exceeded processed: alertId={}, resourceType={}, utilization={}%",
                alertId, resourceType, currentUtilization);

        } catch (Exception e) {
            log.error("Failed to process capacity limit exceeded: alertId={}, resourceType={}",
                alertId, resourceType, e);
            throw new RuntimeException("Capacity limit exceeded processing failed", e);
        }
    }

    private void processResourceShortage(String alertId, String resourceType, String serviceId,
                                       Long availableCapacity, Map<String, Object> capacityMetrics,
                                       String severity, String correlationId) {
        try {
            capacitySecurityService.processResourceShortage(alertId, resourceType, serviceId,
                availableCapacity, capacityMetrics, severity);

            log.warn("Resource shortage processed: alertId={}, resourceType={}, available={}",
                alertId, resourceType, availableCapacity);

        } catch (Exception e) {
            log.error("Failed to process resource shortage: alertId={}, resourceType={}",
                alertId, resourceType, e);
            throw new RuntimeException("Resource shortage processing failed", e);
        }
    }

    private void processGenericCapacityAlert(String alertId, String resourceType, String alertType,
                                           String serviceId, Double currentUtilization,
                                           Map<String, Object> capacityMetrics, String severity,
                                           String correlationId) {
        try {
            capacitySecurityService.processGenericCapacityAlert(alertId, resourceType, alertType,
                serviceId, currentUtilization, capacityMetrics, severity);

            log.info("Generic capacity alert processed: alertId={}, resourceType={}, type={}",
                alertId, resourceType, alertType);

        } catch (Exception e) {
            log.error("Failed to process generic capacity alert: alertId={}, resourceType={}",
                alertId, resourceType, e);
            throw new RuntimeException("Generic capacity alert processing failed", e);
        }
    }

    private void analyzeCapacitySecurityImplications(String alertId, String resourceType, String alertType,
                                                   String severity, Double currentUtilization,
                                                   String serviceId, String nodeId, String region,
                                                   String correlationId) {
        try {
            threatResponseService.analyzeCapacitySecurityImplications(alertId, resourceType, alertType,
                severity, currentUtilization, serviceId, nodeId, region);

            log.debug("Capacity security implications analyzed: alertId={}, resourceType={}, type={}",
                alertId, resourceType, alertType);

        } catch (Exception e) {
            log.error("Failed to analyze capacity security implications: alertId={}, resourceType={}",
                alertId, resourceType, e);
            // Don't throw exception as security analysis failure shouldn't block processing
        }
    }

    private void handleCriticalCapacityAlert(String alertId, String resourceType, String serviceId,
                                           Double currentUtilization, Long availableCapacity,
                                           String correlationId) {
        try {
            capacitySecurityService.handleCriticalCapacityAlert(alertId, resourceType, serviceId,
                currentUtilization, availableCapacity);

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Critical Capacity Alert",
                String.format("Critical capacity issue in service %s: %s at %.1f%% utilization",
                    serviceId, resourceType, currentUtilization),
                Map.of("alertId", alertId, "resourceType", resourceType, "serviceId", serviceId,
                    "currentUtilization", currentUtilization, "correlationId", correlationId)
            );

            log.error("Critical capacity alert handled: alertId={}, resourceType={}, utilization={}%",
                alertId, resourceType, currentUtilization);

        } catch (Exception e) {
            log.error("Failed to handle critical capacity alert: alertId={}, resourceType={}",
                alertId, resourceType, e);
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