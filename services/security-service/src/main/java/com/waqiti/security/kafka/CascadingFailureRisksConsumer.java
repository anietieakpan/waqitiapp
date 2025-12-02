package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.SystemResilienceService;
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
public class CascadingFailureRisksConsumer {

    private final SystemResilienceService systemResilienceService;
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
        successCounter = Counter.builder("cascading_failure_risks_processed_total")
            .description("Total number of successfully processed cascading failure risk events")
            .register(meterRegistry);
        errorCounter = Counter.builder("cascading_failure_risks_errors_total")
            .description("Total number of cascading failure risk processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("cascading_failure_risks_processing_duration")
            .description("Time taken to process cascading failure risk events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"cascading-failure-risks", "system-cascade-risks", "failure-propagation-alerts"},
        groupId = "security-service-cascading-failure-risks-group",
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
    @CircuitBreaker(name = "cascading-failure-risks", fallbackMethod = "handleCascadingFailureRiskFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCascadingFailureRisk(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("cascade-failure-risk-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String riskId = (String) event.get("riskId");
            String originService = (String) event.get("originService");
            String riskType = (String) event.get("riskType");
            String severity = (String) event.get("severity");
            String eventKey = String.format("%s-%s-%s", riskId, originService, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing cascading failure risk: riskId={}, originService={}, type={}, severity={}",
                riskId, originService, riskType, severity);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Double riskScore = ((Number) event.get("riskScore")).doubleValue();
            LocalDateTime detectedAt = LocalDateTime.parse((String) event.get("detectedAt"));
            @SuppressWarnings("unchecked")
            List<String> affectedServices = (List<String>) event.get("affectedServices");
            @SuppressWarnings("unchecked")
            List<String> dependentServices = (List<String>) event.getOrDefault("dependentServices", List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> failureMetrics = (Map<String, Object>) event.getOrDefault("failureMetrics", Map.of());
            String propagationPath = (String) event.get("propagationPath");
            Double propagationSpeed = ((Number) event.getOrDefault("propagationSpeed", 0.0)).doubleValue();
            Integer estimatedImpactRadius = (Integer) event.getOrDefault("estimatedImpactRadius", 0);
            @SuppressWarnings("unchecked")
            List<String> criticalPaths = (List<String>) event.getOrDefault("criticalPaths", List.of());
            String mitigationStrategy = (String) event.get("mitigationStrategy");
            Boolean requiresImmediate = (Boolean) event.getOrDefault("requiresImmediateAction", false);

            // Process cascading failure risk based on type
            switch (riskType) {
                case "SERVICE_DEPENDENCY_FAILURE":
                    processServiceDependencyFailure(riskId, originService, affectedServices,
                        dependentServices, riskScore, failureMetrics, severity, correlationId);
                    break;

                case "DATABASE_CASCADE_RISK":
                    processDatabaseCascadeRisk(riskId, originService, affectedServices, riskScore,
                        propagationPath, failureMetrics, severity, correlationId);
                    break;

                case "NETWORK_PARTITION_RISK":
                    processNetworkPartitionRisk(riskId, originService, affectedServices, riskScore,
                        propagationSpeed, estimatedImpactRadius, severity, correlationId);
                    break;

                case "RESOURCE_EXHAUSTION_CASCADE":
                    processResourceExhaustionCascade(riskId, originService, affectedServices,
                        riskScore, failureMetrics, criticalPaths, severity, correlationId);
                    break;

                case "CIRCUIT_BREAKER_CASCADE":
                    processCircuitBreakerCascade(riskId, originService, dependentServices, riskScore,
                        failureMetrics, severity, correlationId);
                    break;

                case "TIMEOUT_PROPAGATION":
                    processTimeoutPropagation(riskId, originService, affectedServices, riskScore,
                        propagationSpeed, failureMetrics, severity, correlationId);
                    break;

                case "THREAD_POOL_EXHAUSTION":
                    processThreadPoolExhaustion(riskId, originService, affectedServices, riskScore,
                        failureMetrics, severity, correlationId);
                    break;

                default:
                    processGenericCascadingFailureRisk(riskId, originService, riskType, affectedServices,
                        riskScore, failureMetrics, severity, correlationId);
                    break;
            }

            // Implement mitigation strategies
            implementMitigationStrategy(riskId, originService, riskType, mitigationStrategy,
                affectedServices, riskScore, correlationId);

            // Handle immediate action requirements
            if (requiresImmediate || "CRITICAL".equals(severity) || riskScore > 0.9) {
                handleImmediateAction(riskId, originService, riskType, severity, riskScore,
                    affectedServices, estimatedImpactRadius, correlationId);
            }

            // Monitor cascade progression
            monitorCascadeProgression(riskId, originService, affectedServices, propagationPath,
                propagationSpeed, riskScore, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("CASCADING_FAILURE_RISK_PROCESSED", originService,
                Map.of("riskId", riskId, "riskType", riskType, "severity", severity,
                    "riskScore", riskScore, "affectedServicesCount", affectedServices.size(),
                    "estimatedImpactRadius", estimatedImpactRadius, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process cascading failure risk event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("cascading-failure-risks-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCascadingFailureRiskFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("cascade-failure-risk-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for cascading failure risk: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("cascading-failure-risks-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Cascading Failure Risk Circuit Breaker Triggered",
                String.format("Cascading failure risk processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCascadingFailureRisk(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-cascade-failure-risk-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Cascading failure risk permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String riskId = (String) event.get("riskId");
            String originService = (String) event.get("originService");
            String riskType = (String) event.get("riskType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("CASCADING_FAILURE_RISK_DLT_EVENT", originService,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "riskId", riskId, "riskType", riskType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send emergency alert for cascading failure risks
            securityNotificationService.sendEmergencyNotification(
                "CRITICAL: Cascading Failure Risk DLT Event",
                String.format("EMERGENCY: Cascading failure risk %s from service %s sent to DLT: %s. " +
                    "This indicates a critical system stability issue that requires immediate attention.",
                    riskId, originService, exceptionMessage),
                Map.of("riskId", riskId, "originService", originService, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse cascading failure risk DLT event: {}", eventJson, ex);
        }
    }

    private void processServiceDependencyFailure(String riskId, String originService,
                                               List<String> affectedServices, List<String> dependentServices,
                                               Double riskScore, Map<String, Object> failureMetrics,
                                               String severity, String correlationId) {
        try {
            systemResilienceService.processServiceDependencyFailure(riskId, originService,
                affectedServices, dependentServices, riskScore, failureMetrics, severity);

            log.error("Service dependency failure processed: riskId={}, origin={}, affected={}",
                riskId, originService, affectedServices.size());

        } catch (Exception e) {
            log.error("Failed to process service dependency failure: riskId={}, origin={}",
                riskId, originService, e);
            throw new RuntimeException("Service dependency failure processing failed", e);
        }
    }

    private void processDatabaseCascadeRisk(String riskId, String originService, List<String> affectedServices,
                                          Double riskScore, String propagationPath,
                                          Map<String, Object> failureMetrics, String severity,
                                          String correlationId) {
        try {
            systemResilienceService.processDatabaseCascadeRisk(riskId, originService, affectedServices,
                riskScore, propagationPath, failureMetrics, severity);

            log.error("Database cascade risk processed: riskId={}, origin={}, path={}",
                riskId, originService, propagationPath);

        } catch (Exception e) {
            log.error("Failed to process database cascade risk: riskId={}, origin={}",
                riskId, originService, e);
            throw new RuntimeException("Database cascade risk processing failed", e);
        }
    }

    private void processNetworkPartitionRisk(String riskId, String originService, List<String> affectedServices,
                                           Double riskScore, Double propagationSpeed, Integer estimatedImpactRadius,
                                           String severity, String correlationId) {
        try {
            systemResilienceService.processNetworkPartitionRisk(riskId, originService, affectedServices,
                riskScore, propagationSpeed, estimatedImpactRadius, severity);

            log.error("Network partition risk processed: riskId={}, origin={}, radius={}",
                riskId, originService, estimatedImpactRadius);

        } catch (Exception e) {
            log.error("Failed to process network partition risk: riskId={}, origin={}",
                riskId, originService, e);
            throw new RuntimeException("Network partition risk processing failed", e);
        }
    }

    private void processResourceExhaustionCascade(String riskId, String originService,
                                                List<String> affectedServices, Double riskScore,
                                                Map<String, Object> failureMetrics, List<String> criticalPaths,
                                                String severity, String correlationId) {
        try {
            systemResilienceService.processResourceExhaustionCascade(riskId, originService,
                affectedServices, riskScore, failureMetrics, criticalPaths, severity);

            log.error("Resource exhaustion cascade processed: riskId={}, origin={}, criticalPaths={}",
                riskId, originService, criticalPaths.size());

        } catch (Exception e) {
            log.error("Failed to process resource exhaustion cascade: riskId={}, origin={}",
                riskId, originService, e);
            throw new RuntimeException("Resource exhaustion cascade processing failed", e);
        }
    }

    private void processCircuitBreakerCascade(String riskId, String originService, List<String> dependentServices,
                                            Double riskScore, Map<String, Object> failureMetrics,
                                            String severity, String correlationId) {
        try {
            systemResilienceService.processCircuitBreakerCascade(riskId, originService, dependentServices,
                riskScore, failureMetrics, severity);

            log.error("Circuit breaker cascade processed: riskId={}, origin={}, dependent={}",
                riskId, originService, dependentServices.size());

        } catch (Exception e) {
            log.error("Failed to process circuit breaker cascade: riskId={}, origin={}",
                riskId, originService, e);
            throw new RuntimeException("Circuit breaker cascade processing failed", e);
        }
    }

    private void processTimeoutPropagation(String riskId, String originService, List<String> affectedServices,
                                         Double riskScore, Double propagationSpeed,
                                         Map<String, Object> failureMetrics, String severity,
                                         String correlationId) {
        try {
            systemResilienceService.processTimeoutPropagation(riskId, originService, affectedServices,
                riskScore, propagationSpeed, failureMetrics, severity);

            log.error("Timeout propagation processed: riskId={}, origin={}, speed={}",
                riskId, originService, propagationSpeed);

        } catch (Exception e) {
            log.error("Failed to process timeout propagation: riskId={}, origin={}",
                riskId, originService, e);
            throw new RuntimeException("Timeout propagation processing failed", e);
        }
    }

    private void processThreadPoolExhaustion(String riskId, String originService, List<String> affectedServices,
                                            Double riskScore, Map<String, Object> failureMetrics,
                                            String severity, String correlationId) {
        try {
            systemResilienceService.processThreadPoolExhaustion(riskId, originService, affectedServices,
                riskScore, failureMetrics, severity);

            log.error("Thread pool exhaustion processed: riskId={}, origin={}, affected={}",
                riskId, originService, affectedServices.size());

        } catch (Exception e) {
            log.error("Failed to process thread pool exhaustion: riskId={}, origin={}",
                riskId, originService, e);
            throw new RuntimeException("Thread pool exhaustion processing failed", e);
        }
    }

    private void processGenericCascadingFailureRisk(String riskId, String originService, String riskType,
                                                  List<String> affectedServices, Double riskScore,
                                                  Map<String, Object> failureMetrics, String severity,
                                                  String correlationId) {
        try {
            systemResilienceService.processGenericCascadingFailureRisk(riskId, originService, riskType,
                affectedServices, riskScore, failureMetrics, severity);

            log.error("Generic cascading failure risk processed: riskId={}, origin={}, type={}",
                riskId, originService, riskType);

        } catch (Exception e) {
            log.error("Failed to process generic cascading failure risk: riskId={}, origin={}",
                riskId, originService, e);
            throw new RuntimeException("Generic cascading failure risk processing failed", e);
        }
    }

    private void implementMitigationStrategy(String riskId, String originService, String riskType,
                                           String mitigationStrategy, List<String> affectedServices,
                                           Double riskScore, String correlationId) {
        try {
            systemResilienceService.implementMitigationStrategy(riskId, originService, riskType,
                mitigationStrategy, affectedServices, riskScore);

            log.info("Mitigation strategy implemented: riskId={}, origin={}, strategy={}",
                riskId, originService, mitigationStrategy);

        } catch (Exception e) {
            log.error("Failed to implement mitigation strategy: riskId={}, origin={}",
                riskId, originService, e);
            // Don't throw exception as mitigation failure shouldn't block processing
        }
    }

    private void handleImmediateAction(String riskId, String originService, String riskType, String severity,
                                     Double riskScore, List<String> affectedServices, Integer estimatedImpactRadius,
                                     String correlationId) {
        try {
            threatResponseService.handleCascadingFailureEmergency(riskId, originService, riskType,
                severity, riskScore, affectedServices, estimatedImpactRadius);

            // Send emergency alert
            securityNotificationService.sendEmergencyNotification(
                "CRITICAL: Cascading Failure Risk",
                String.format("EMERGENCY: High-risk cascading failure detected from service %s. " +
                    "Risk score: %.2f, Impact radius: %d services. Immediate action required.",
                    originService, riskScore, estimatedImpactRadius),
                Map.of("riskId", riskId, "originService", originService, "riskScore", riskScore,
                    "impactRadius", estimatedImpactRadius, "correlationId", correlationId)
            );

            log.error("Immediate action handled: riskId={}, origin={}, score={}, radius={}",
                riskId, originService, riskScore, estimatedImpactRadius);

        } catch (Exception e) {
            log.error("Failed to handle immediate action: riskId={}, origin={}",
                riskId, originService, e);
            // Don't throw exception as immediate action failure shouldn't block processing
        }
    }

    private void monitorCascadeProgression(String riskId, String originService, List<String> affectedServices,
                                         String propagationPath, Double propagationSpeed, Double riskScore,
                                         String correlationId) {
        try {
            systemResilienceService.monitorCascadeProgression(riskId, originService, affectedServices,
                propagationPath, propagationSpeed, riskScore);

            log.debug("Cascade progression monitored: riskId={}, origin={}, speed={}",
                riskId, originService, propagationSpeed);

        } catch (Exception e) {
            log.error("Failed to monitor cascade progression: riskId={}, origin={}",
                riskId, originService, e);
            // Don't throw exception as monitoring failure shouldn't block processing
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