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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class CapacityBreachesConsumer {

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
        successCounter = Counter.builder("capacity_breaches_processed_total")
            .description("Total number of successfully processed capacity breach events")
            .register(meterRegistry);
        errorCounter = Counter.builder("capacity_breaches_errors_total")
            .description("Total number of capacity breach processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("capacity_breaches_processing_duration")
            .description("Time taken to process capacity breach events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"capacity-breaches", "capacity-violations", "resource-limit-breaches"},
        groupId = "security-service-capacity-breaches-group",
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
    @CircuitBreaker(name = "capacity-breaches", fallbackMethod = "handleCapacityBreachFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCapacityBreach(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("capacity-breach-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String breachId = (String) event.get("breachId");
            String resourceType = (String) event.get("resourceType");
            String breachType = (String) event.get("breachType");
            String severity = (String) event.get("severity");
            String eventKey = String.format("%s-%s-%s", breachId, resourceType, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing capacity breach: breachId={}, resourceType={}, type={}, severity={}",
                breachId, resourceType, breachType, severity);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String serviceId = (String) event.get("serviceId");
            Double breachValue = ((Number) event.get("breachValue")).doubleValue();
            Double limitValue = ((Number) event.get("limitValue")).doubleValue();
            String unit = (String) event.get("unit");
            LocalDateTime breachedAt = LocalDateTime.parse((String) event.get("breachedAt"));
            Integer duration = (Integer) event.getOrDefault("duration", 0);
            @SuppressWarnings("unchecked")
            Map<String, Object> breachMetrics = (Map<String, Object>) event.getOrDefault("breachMetrics", Map.of());
            @SuppressWarnings("unchecked")
            List<String> affectedComponents = (List<String>) event.getOrDefault("affectedComponents", List.of());
            String nodeId = (String) event.get("nodeId");
            String region = (String) event.get("region");
            Boolean isRecurring = (Boolean) event.getOrDefault("isRecurring", false);
            String impactLevel = (String) event.getOrDefault("impactLevel", "MEDIUM");

            // Process capacity breach based on type
            switch (breachType) {
                case "CPU_LIMIT_BREACH":
                    processCpuLimitBreach(breachId, resourceType, serviceId, breachValue, limitValue,
                        unit, duration, breachMetrics, severity, correlationId);
                    break;

                case "MEMORY_LIMIT_BREACH":
                    processMemoryLimitBreach(breachId, resourceType, serviceId, breachValue, limitValue,
                        unit, duration, breachMetrics, severity, correlationId);
                    break;

                case "STORAGE_LIMIT_BREACH":
                    processStorageLimitBreach(breachId, resourceType, serviceId, breachValue, limitValue,
                        unit, duration, breachMetrics, severity, correlationId);
                    break;

                case "NETWORK_BANDWIDTH_BREACH":
                    processNetworkBandwidthBreach(breachId, resourceType, serviceId, breachValue,
                        limitValue, unit, breachMetrics, severity, correlationId);
                    break;

                case "CONNECTION_LIMIT_BREACH":
                    processConnectionLimitBreach(breachId, resourceType, serviceId, breachValue,
                        limitValue, duration, breachMetrics, severity, correlationId);
                    break;

                case "REQUEST_RATE_BREACH":
                    processRequestRateBreach(breachId, resourceType, serviceId, breachValue, limitValue,
                        unit, breachMetrics, severity, correlationId);
                    break;

                case "CONCURRENT_USER_BREACH":
                    processConcurrentUserBreach(breachId, resourceType, serviceId, breachValue,
                        limitValue, breachMetrics, severity, correlationId);
                    break;

                default:
                    processGenericCapacityBreach(breachId, resourceType, breachType, serviceId,
                        breachValue, limitValue, breachMetrics, severity, correlationId);
                    break;
            }

            // Handle recurring breaches
            if (isRecurring) {
                handleRecurringBreach(breachId, resourceType, serviceId, breachType, severity,
                    breachMetrics, correlationId);
            }

            // Assess impact and take action
            assessBreachImpact(breachId, resourceType, serviceId, impactLevel, affectedComponents,
                breachValue, limitValue, correlationId);

            // Initiate emergency response for critical breaches
            if ("CRITICAL".equals(severity) || "HIGH".equals(impactLevel)) {
                initiateEmergencyResponse(breachId, resourceType, serviceId, breachType, severity,
                    breachValue, limitValue, affectedComponents, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("CAPACITY_BREACH_PROCESSED", serviceId,
                Map.of("breachId", breachId, "resourceType", resourceType, "breachType", breachType,
                    "severity", severity, "breachValue", breachValue, "limitValue", limitValue,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process capacity breach event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("capacity-breaches-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCapacityBreachFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("capacity-breach-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for capacity breach: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("capacity-breaches-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Capacity Breach Circuit Breaker Triggered",
                String.format("Capacity breach processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCapacityBreach(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-capacity-breach-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Capacity breach permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String breachId = (String) event.get("breachId");
            String resourceType = (String) event.get("resourceType");
            String breachType = (String) event.get("breachType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("CAPACITY_BREACH_DLT_EVENT", resourceType,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "breachId", breachId, "breachType", breachType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Capacity Breach Dead Letter Event",
                String.format("Capacity breach %s for resource %s sent to DLT: %s", breachId, resourceType, exceptionMessage),
                Map.of("breachId", breachId, "resourceType", resourceType, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse capacity breach DLT event: {}", eventJson, ex);
        }
    }

    private void processCpuLimitBreach(String breachId, String resourceType, String serviceId,
                                     Double breachValue, Double limitValue, String unit, Integer duration,
                                     Map<String, Object> breachMetrics, String severity, String correlationId) {
        try {
            capacitySecurityService.processCpuLimitBreach(breachId, resourceType, serviceId,
                breachValue, limitValue, unit, duration, breachMetrics, severity);

            log.error("CPU limit breach processed: breachId={}, serviceId={}, breach={}%",
                breachId, serviceId, breachValue);

        } catch (Exception e) {
            log.error("Failed to process CPU limit breach: breachId={}, serviceId={}",
                breachId, serviceId, e);
            throw new RuntimeException("CPU limit breach processing failed", e);
        }
    }

    private void processMemoryLimitBreach(String breachId, String resourceType, String serviceId,
                                        Double breachValue, Double limitValue, String unit, Integer duration,
                                        Map<String, Object> breachMetrics, String severity, String correlationId) {
        try {
            capacitySecurityService.processMemoryLimitBreach(breachId, resourceType, serviceId,
                breachValue, limitValue, unit, duration, breachMetrics, severity);

            log.error("Memory limit breach processed: breachId={}, serviceId={}, breach={}{}",
                breachId, serviceId, breachValue, unit);

        } catch (Exception e) {
            log.error("Failed to process memory limit breach: breachId={}, serviceId={}",
                breachId, serviceId, e);
            throw new RuntimeException("Memory limit breach processing failed", e);
        }
    }

    private void processStorageLimitBreach(String breachId, String resourceType, String serviceId,
                                         Double breachValue, Double limitValue, String unit, Integer duration,
                                         Map<String, Object> breachMetrics, String severity, String correlationId) {
        try {
            capacitySecurityService.processStorageLimitBreach(breachId, resourceType, serviceId,
                breachValue, limitValue, unit, duration, breachMetrics, severity);

            log.error("Storage limit breach processed: breachId={}, serviceId={}, breach={}{}",
                breachId, serviceId, breachValue, unit);

        } catch (Exception e) {
            log.error("Failed to process storage limit breach: breachId={}, serviceId={}",
                breachId, serviceId, e);
            throw new RuntimeException("Storage limit breach processing failed", e);
        }
    }

    private void processNetworkBandwidthBreach(String breachId, String resourceType, String serviceId,
                                             Double breachValue, Double limitValue, String unit,
                                             Map<String, Object> breachMetrics, String severity,
                                             String correlationId) {
        try {
            capacitySecurityService.processNetworkBandwidthBreach(breachId, resourceType, serviceId,
                breachValue, limitValue, unit, breachMetrics, severity);

            log.error("Network bandwidth breach processed: breachId={}, serviceId={}, breach={}{}",
                breachId, serviceId, breachValue, unit);

        } catch (Exception e) {
            log.error("Failed to process network bandwidth breach: breachId={}, serviceId={}",
                breachId, serviceId, e);
            throw new RuntimeException("Network bandwidth breach processing failed", e);
        }
    }

    private void processConnectionLimitBreach(String breachId, String resourceType, String serviceId,
                                            Double breachValue, Double limitValue, Integer duration,
                                            Map<String, Object> breachMetrics, String severity,
                                            String correlationId) {
        try {
            capacitySecurityService.processConnectionLimitBreach(breachId, resourceType, serviceId,
                breachValue, limitValue, duration, breachMetrics, severity);

            log.error("Connection limit breach processed: breachId={}, serviceId={}, connections={}",
                breachId, serviceId, breachValue.intValue());

        } catch (Exception e) {
            log.error("Failed to process connection limit breach: breachId={}, serviceId={}",
                breachId, serviceId, e);
            throw new RuntimeException("Connection limit breach processing failed", e);
        }
    }

    private void processRequestRateBreach(String breachId, String resourceType, String serviceId,
                                        Double breachValue, Double limitValue, String unit,
                                        Map<String, Object> breachMetrics, String severity,
                                        String correlationId) {
        try {
            capacitySecurityService.processRequestRateBreach(breachId, resourceType, serviceId,
                breachValue, limitValue, unit, breachMetrics, severity);

            log.error("Request rate breach processed: breachId={}, serviceId={}, rate={}{}",
                breachId, serviceId, breachValue, unit);

        } catch (Exception e) {
            log.error("Failed to process request rate breach: breachId={}, serviceId={}",
                breachId, serviceId, e);
            throw new RuntimeException("Request rate breach processing failed", e);
        }
    }

    private void processConcurrentUserBreach(String breachId, String resourceType, String serviceId,
                                           Double breachValue, Double limitValue,
                                           Map<String, Object> breachMetrics, String severity,
                                           String correlationId) {
        try {
            capacitySecurityService.processConcurrentUserBreach(breachId, resourceType, serviceId,
                breachValue, limitValue, breachMetrics, severity);

            log.error("Concurrent user breach processed: breachId={}, serviceId={}, users={}",
                breachId, serviceId, breachValue.intValue());

        } catch (Exception e) {
            log.error("Failed to process concurrent user breach: breachId={}, serviceId={}",
                breachId, serviceId, e);
            throw new RuntimeException("Concurrent user breach processing failed", e);
        }
    }

    private void processGenericCapacityBreach(String breachId, String resourceType, String breachType,
                                            String serviceId, Double breachValue, Double limitValue,
                                            Map<String, Object> breachMetrics, String severity,
                                            String correlationId) {
        try {
            capacitySecurityService.processGenericCapacityBreach(breachId, resourceType, breachType,
                serviceId, breachValue, limitValue, breachMetrics, severity);

            log.error("Generic capacity breach processed: breachId={}, serviceId={}, type={}",
                breachId, serviceId, breachType);

        } catch (Exception e) {
            log.error("Failed to process generic capacity breach: breachId={}, serviceId={}",
                breachId, serviceId, e);
            throw new RuntimeException("Generic capacity breach processing failed", e);
        }
    }

    private void handleRecurringBreach(String breachId, String resourceType, String serviceId,
                                     String breachType, String severity, Map<String, Object> breachMetrics,
                                     String correlationId) {
        try {
            capacitySecurityService.handleRecurringBreach(breachId, resourceType, serviceId,
                breachType, severity, breachMetrics);

            log.warn("Recurring breach handled: breachId={}, serviceId={}, type={}",
                breachId, serviceId, breachType);

        } catch (Exception e) {
            log.error("Failed to handle recurring breach: breachId={}, serviceId={}",
                breachId, serviceId, e);
            // Don't throw exception as recurring handling failure shouldn't block processing
        }
    }

    private void assessBreachImpact(String breachId, String resourceType, String serviceId,
                                  String impactLevel, List<String> affectedComponents,
                                  Double breachValue, Double limitValue, String correlationId) {
        try {
            capacitySecurityService.assessBreachImpact(breachId, resourceType, serviceId,
                impactLevel, affectedComponents, breachValue, limitValue);

            log.info("Breach impact assessed: breachId={}, serviceId={}, impact={}",
                breachId, serviceId, impactLevel);

        } catch (Exception e) {
            log.error("Failed to assess breach impact: breachId={}, serviceId={}",
                breachId, serviceId, e);
            // Don't throw exception as impact assessment failure shouldn't block processing
        }
    }

    private void initiateEmergencyResponse(String breachId, String resourceType, String serviceId,
                                         String breachType, String severity, Double breachValue,
                                         Double limitValue, List<String> affectedComponents,
                                         String correlationId) {
        try {
            threatResponseService.initiateCapacityBreachEmergencyResponse(breachId, resourceType,
                serviceId, breachType, severity, breachValue, limitValue, affectedComponents);

            // Send emergency alert
            securityNotificationService.sendEmergencyNotification(
                "Critical Capacity Breach",
                String.format("EMERGENCY: Critical capacity breach in service %s: %s exceeded limit by %.1f%%",
                    serviceId, resourceType, ((breachValue - limitValue) / limitValue) * 100),
                Map.of("breachId", breachId, "serviceId", serviceId, "resourceType", resourceType,
                    "breachValue", breachValue, "limitValue", limitValue, "correlationId", correlationId)
            );

            log.error("Emergency response initiated: breachId={}, serviceId={}, breach={}",
                breachId, serviceId, breachValue);

        } catch (Exception e) {
            log.error("Failed to initiate emergency response: breachId={}, serviceId={}",
                breachId, serviceId, e);
            // Don't throw exception as emergency response failure shouldn't block processing
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