package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.domain.AuditEvent;
import com.waqiti.audit.repository.AuditEventRepository;
import com.waqiti.audit.service.AuditService;
import com.waqiti.audit.service.ComprehensiveAuditService;
import com.waqiti.audit.service.AuditNotificationService;
import com.waqiti.common.audit.AuditService as CommonAuditService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditHealthCheckConsumer {

    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;
    private final ComprehensiveAuditService comprehensiveAuditService;
    private final AuditNotificationService auditNotificationService;
    private final CommonAuditService commonAuditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Health tracking
    private final ConcurrentHashMap<String, LocalDateTime> serviceHealthStatus = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> healthCheckFailures = new ConcurrentHashMap<>();

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    private Counter healthCheckCounter;
    private Counter healthyServiceCounter;
    private Counter unhealthyServiceCounter;
    private Counter degradedServiceCounter;
    private Counter healthCheckFailureCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("audit_health_check_processed_total")
            .description("Total number of successfully processed audit health check events")
            .register(meterRegistry);
        errorCounter = Counter.builder("audit_health_check_errors_total")
            .description("Total number of audit health check processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("audit_health_check_processing_duration")
            .description("Time taken to process audit health check events")
            .register(meterRegistry);
        healthCheckCounter = Counter.builder("audit_health_checks_total")
            .description("Total number of audit health checks processed")
            .register(meterRegistry);
        healthyServiceCounter = Counter.builder("audit_healthy_services_total")
            .description("Total number of healthy audit services")
            .register(meterRegistry);
        unhealthyServiceCounter = Counter.builder("audit_unhealthy_services_total")
            .description("Total number of unhealthy audit services")
            .register(meterRegistry);
        degradedServiceCounter = Counter.builder("audit_degraded_services_total")
            .description("Total number of degraded audit services")
            .register(meterRegistry);
        healthCheckFailureCounter = Counter.builder("audit_health_check_failures_total")
            .description("Total number of audit health check failures")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"audit-health-check"},
        groupId = "audit-health-check-processor-group",
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
    @CircuitBreaker(name = "audit-health-check", fallbackMethod = "handleAuditHealthCheckEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuditHealthCheckEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("audit-health-%d-p%d-o%d", System.currentTimeMillis(), partition, offset);
        String eventKey = String.format("health-%d-%d-%d", partition, offset, System.currentTimeMillis());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing audit health check event: partition={}, offset={}, correlationId={}",
                partition, offset, correlationId);

            // Clean expired entries periodically
            cleanExpiredEntries();

            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);

            String serviceName = (String) eventData.get("serviceName");
            String healthStatus = (String) eventData.get("healthStatus");
            String checkType = (String) eventData.get("checkType");
            Map<String, Object> healthMetrics = (Map<String, Object>) eventData.get("healthMetrics");
            List<String> healthIssues = (List<String>) eventData.get("healthIssues");
            String auditSystemComponent = (String) eventData.get("auditSystemComponent");
            Boolean isSystemCritical = (Boolean) eventData.getOrDefault("isSystemCritical", false);
            Long checkDurationMs = eventData.get("checkDurationMs") != null ?
                Long.valueOf(eventData.get("checkDurationMs").toString()) : null;

            healthCheckCounter.increment();

            // Process audit health check
            processAuditHealthCheck(eventData, serviceName, healthStatus, checkType, healthMetrics,
                healthIssues, auditSystemComponent, isSystemCritical, checkDurationMs, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            commonAuditService.logAuditEvent("AUDIT_HEALTH_CHECK_PROCESSED", correlationId,
                Map.of("serviceName", serviceName, "healthStatus", healthStatus, "checkType", checkType,
                    "isSystemCritical", isSystemCritical, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process audit health check event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("audit-health-check-fallback-events", Map.of(
                "originalMessage", message, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuditHealthCheckEventFallback(
            String message,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("audit-health-fallback-%d-p%d-o%d",
            System.currentTimeMillis(), partition, offset);

        log.error("Circuit breaker fallback triggered for audit health check: partition={}, offset={}, error={}",
            partition, offset, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("audit-health-check-dlq", Map.of(
            "originalMessage", message,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Audit Health Check Circuit Breaker Triggered",
                String.format("Audit health check processing failed: %s", ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuditHealthCheckEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-audit-health-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Audit health check permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        commonAuditService.logAuditEvent("AUDIT_HEALTH_CHECK_DLT_EVENT", correlationId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Audit Health Check Dead Letter Event",
                String.format("CRITICAL: Audit health check sent to DLT: %s", exceptionMessage),
                Map.of("topic", topic, "correlationId", correlationId)
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

    private void processAuditHealthCheck(Map<String, Object> eventData, String serviceName,
            String healthStatus, String checkType, Map<String, Object> healthMetrics,
            List<String> healthIssues, String auditSystemComponent, Boolean isSystemCritical,
            Long checkDurationMs, String correlationId) {

        log.info("Processing audit health check: service={}, status={}, type={}, critical={}, correlationId={}",
            serviceName, healthStatus, checkType, isSystemCritical, correlationId);

        // Create audit event for the health check
        AuditEvent auditEvent = AuditEvent.builder()
            .eventType("AUDIT_HEALTH_CHECK")
            .serviceName(serviceName)
            .resourceId(auditSystemComponent)
            .resourceType("AUDIT_SYSTEM_COMPONENT")
            .action(checkType)
            .description(String.format("Audit health check: %s for service %s", checkType, serviceName))
            .result(mapHealthStatusToResult(healthStatus))
            .severity(mapHealthStatusToSeverity(healthStatus, isSystemCritical))
            .correlationId(correlationId)
            .metadata(convertToStringMap(eventData))
            .durationMs(checkDurationMs)
            .complianceTags("AUDIT_HEALTH,SYSTEM_MONITORING")
            .build();

        auditEventRepository.save(auditEvent);

        // Update service health status tracking
        updateServiceHealthStatus(serviceName, healthStatus, correlationId);

        // Process based on health check type
        switch (checkType.toUpperCase()) {
            case "SYSTEM_HEALTH":
                processSystemHealthCheck(serviceName, healthStatus, healthMetrics, isSystemCritical, correlationId);
                break;
            case "DATA_INTEGRITY":
                processDataIntegrityCheck(serviceName, healthStatus, healthIssues, correlationId);
                break;
            case "PERFORMANCE_CHECK":
                processPerformanceCheck(serviceName, healthStatus, healthMetrics, checkDurationMs, correlationId);
                break;
            case "CONNECTIVITY_CHECK":
                processConnectivityCheck(serviceName, healthStatus, healthIssues, correlationId);
                break;
            case "STORAGE_CHECK":
                processStorageCheck(serviceName, healthStatus, healthMetrics, correlationId);
                break;
            case "COMPLIANCE_CHECK":
                processComplianceCheck(serviceName, healthStatus, healthIssues, correlationId);
                break;
            default:
                processGenericHealthCheck(serviceName, healthStatus, correlationId);
        }

        // Handle based on health status
        switch (healthStatus.toUpperCase()) {
            case "HEALTHY":
                handleHealthyStatus(serviceName, auditSystemComponent, correlationId);
                healthyServiceCounter.increment();
                break;
            case "DEGRADED":
                handleDegradedStatus(serviceName, auditSystemComponent, healthIssues, isSystemCritical, correlationId);
                degradedServiceCounter.increment();
                break;
            case "UNHEALTHY":
                handleUnhealthyStatus(serviceName, auditSystemComponent, healthIssues, isSystemCritical, correlationId);
                unhealthyServiceCounter.increment();
                break;
            case "FAILED":
                handleFailedStatus(serviceName, auditSystemComponent, healthIssues, isSystemCritical, correlationId);
                healthCheckFailureCounter.increment();
                break;
            default:
                log.warn("Unknown health status: {} for service: {}, correlationId: {}",
                    healthStatus, serviceName, correlationId);
        }

        // Send downstream events
        kafkaTemplate.send("audit-health-check-processed", Map.of(
            "eventId", auditEvent.getId(),
            "serviceName", serviceName,
            "healthStatus", healthStatus,
            "checkType", checkType,
            "auditSystemComponent", auditSystemComponent != null ? auditSystemComponent : "",
            "isSystemCritical", isSystemCritical,
            "checkDurationMs", checkDurationMs != null ? checkDurationMs : 0,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Completed audit health check processing: service={}, status={}, correlationId={}",
            serviceName, healthStatus, correlationId);
    }

    private void processSystemHealthCheck(String serviceName, String healthStatus,
            Map<String, Object> healthMetrics, Boolean isSystemCritical, String correlationId) {

        log.info("Processing system health check: service={}, status={}, critical={}, correlationId={}",
            serviceName, healthStatus, isSystemCritical, correlationId);

        // Send to system monitoring
        kafkaTemplate.send("audit-system-health-monitoring", Map.of(
            "serviceName", serviceName,
            "healthStatus", healthStatus,
            "metrics", healthMetrics != null ? healthMetrics : new HashMap<>(),
            "isSystemCritical", isSystemCritical,
            "checkType", "SYSTEM_HEALTH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Critical system check
        if (isSystemCritical && !"HEALTHY".equals(healthStatus)) {
            escalateCriticalSystemHealth(serviceName, healthStatus, correlationId);
        }
    }

    private void processDataIntegrityCheck(String serviceName, String healthStatus,
            List<String> healthIssues, String correlationId) {

        log.info("Processing data integrity check: service={}, status={}, issues={}, correlationId={}",
            serviceName, healthStatus, healthIssues != null ? healthIssues.size() : 0, correlationId);

        kafkaTemplate.send("audit-data-integrity-monitoring", Map.of(
            "serviceName", serviceName,
            "healthStatus", healthStatus,
            "issues", healthIssues != null ? healthIssues : Collections.emptyList(),
            "checkType", "DATA_INTEGRITY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Data integrity failures are always critical
        if (!"HEALTHY".equals(healthStatus)) {
            escalateDataIntegrityIssue(serviceName, healthIssues, correlationId);
        }
    }

    private void processPerformanceCheck(String serviceName, String healthStatus,
            Map<String, Object> healthMetrics, Long checkDurationMs, String correlationId) {

        log.info("Processing performance check: service={}, status={}, duration={}ms, correlationId={}",
            serviceName, healthStatus, checkDurationMs, correlationId);

        kafkaTemplate.send("audit-performance-monitoring", Map.of(
            "serviceName", serviceName,
            "healthStatus", healthStatus,
            "metrics", healthMetrics != null ? healthMetrics : new HashMap<>(),
            "checkDurationMs", checkDurationMs != null ? checkDurationMs : 0,
            "checkType", "PERFORMANCE_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check for performance degradation
        if (checkDurationMs != null && checkDurationMs > 5000) { // More than 5 seconds
            handlePerformanceDegradation(serviceName, checkDurationMs, correlationId);
        }
    }

    private void processConnectivityCheck(String serviceName, String healthStatus,
            List<String> healthIssues, String correlationId) {

        log.info("Processing connectivity check: service={}, status={}, issues={}, correlationId={}",
            serviceName, healthStatus, healthIssues != null ? healthIssues.size() : 0, correlationId);

        kafkaTemplate.send("audit-connectivity-monitoring", Map.of(
            "serviceName", serviceName,
            "healthStatus", healthStatus,
            "issues", healthIssues != null ? healthIssues : Collections.emptyList(),
            "checkType", "CONNECTIVITY_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Connectivity issues affect audit data flow
        if (!"HEALTHY".equals(healthStatus)) {
            handleConnectivityIssue(serviceName, healthIssues, correlationId);
        }
    }

    private void processStorageCheck(String serviceName, String healthStatus,
            Map<String, Object> healthMetrics, String correlationId) {

        log.info("Processing storage check: service={}, status={}, correlationId={}",
            serviceName, healthStatus, correlationId);

        kafkaTemplate.send("audit-storage-monitoring", Map.of(
            "serviceName", serviceName,
            "healthStatus", healthStatus,
            "metrics", healthMetrics != null ? healthMetrics : new HashMap<>(),
            "checkType", "STORAGE_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Storage issues affect audit data retention
        if (!"HEALTHY".equals(healthStatus)) {
            handleStorageIssue(serviceName, healthMetrics, correlationId);
        }
    }

    private void processComplianceCheck(String serviceName, String healthStatus,
            List<String> healthIssues, String correlationId) {

        log.info("Processing compliance check: service={}, status={}, issues={}, correlationId={}",
            serviceName, healthStatus, healthIssues != null ? healthIssues.size() : 0, correlationId);

        kafkaTemplate.send("audit-compliance-monitoring", Map.of(
            "serviceName", serviceName,
            "healthStatus", healthStatus,
            "issues", healthIssues != null ? healthIssues : Collections.emptyList(),
            "checkType", "COMPLIANCE_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Compliance issues are always critical
        if (!"HEALTHY".equals(healthStatus)) {
            escalateComplianceIssue(serviceName, healthIssues, correlationId);
        }
    }

    private void processGenericHealthCheck(String serviceName, String healthStatus, String correlationId) {
        log.debug("Processing generic health check: service={}, status={}, correlationId={}",
            serviceName, healthStatus, correlationId);
    }

    private void updateServiceHealthStatus(String serviceName, String healthStatus, String correlationId) {
        serviceHealthStatus.put(serviceName, LocalDateTime.now());

        if (!"HEALTHY".equals(healthStatus)) {
            healthCheckFailures.merge(serviceName, 1, Integer::sum);
        } else {
            healthCheckFailures.remove(serviceName); // Reset on healthy status
        }

        // Check for persistent failures
        Integer failureCount = healthCheckFailures.get(serviceName);
        if (failureCount != null && failureCount >= 3) {
            handlePersistentFailure(serviceName, failureCount, correlationId);
        }
    }

    private void handleHealthyStatus(String serviceName, String auditSystemComponent, String correlationId) {
        log.debug("Service healthy: service={}, component={}, correlationId={}",
            serviceName, auditSystemComponent, correlationId);

        kafkaTemplate.send("audit-service-healthy", Map.of(
            "serviceName", serviceName,
            "auditSystemComponent", auditSystemComponent != null ? auditSystemComponent : "",
            "status", "HEALTHY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleDegradedStatus(String serviceName, String auditSystemComponent,
            List<String> healthIssues, Boolean isSystemCritical, String correlationId) {

        log.warn("Service degraded: service={}, component={}, critical={}, issues={}, correlationId={}",
            serviceName, auditSystemComponent, isSystemCritical, healthIssues, correlationId);

        kafkaTemplate.send("audit-service-degraded", Map.of(
            "serviceName", serviceName,
            "auditSystemComponent", auditSystemComponent != null ? auditSystemComponent : "",
            "status", "DEGRADED",
            "issues", healthIssues != null ? healthIssues : Collections.emptyList(),
            "isSystemCritical", isSystemCritical,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (isSystemCritical) {
            escalateServiceDegradation(serviceName, auditSystemComponent, healthIssues, correlationId);
        }
    }

    private void handleUnhealthyStatus(String serviceName, String auditSystemComponent,
            List<String> healthIssues, Boolean isSystemCritical, String correlationId) {

        log.error("Service unhealthy: service={}, component={}, critical={}, issues={}, correlationId={}",
            serviceName, auditSystemComponent, isSystemCritical, healthIssues, correlationId);

        kafkaTemplate.send("audit-service-unhealthy", Map.of(
            "serviceName", serviceName,
            "auditSystemComponent", auditSystemComponent != null ? auditSystemComponent : "",
            "status", "UNHEALTHY",
            "issues", healthIssues != null ? healthIssues : Collections.emptyList(),
            "isSystemCritical", isSystemCritical,
            "requiresAttention", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        escalateUnhealthyService(serviceName, auditSystemComponent, healthIssues, isSystemCritical, correlationId);
    }

    private void handleFailedStatus(String serviceName, String auditSystemComponent,
            List<String> healthIssues, Boolean isSystemCritical, String correlationId) {

        log.error("CRITICAL: Service failed: service={}, component={}, critical={}, issues={}, correlationId={}",
            serviceName, auditSystemComponent, isSystemCritical, healthIssues, correlationId);

        kafkaTemplate.send("audit-service-failed", Map.of(
            "serviceName", serviceName,
            "auditSystemComponent", auditSystemComponent != null ? auditSystemComponent : "",
            "status", "FAILED",
            "issues", healthIssues != null ? healthIssues : Collections.emptyList(),
            "isSystemCritical", isSystemCritical,
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        escalateFailedService(serviceName, auditSystemComponent, healthIssues, isSystemCritical, correlationId);
    }

    private void handlePersistentFailure(String serviceName, Integer failureCount, String correlationId) {
        log.error("CRITICAL: Persistent health check failures: service={}, count={}, correlationId={}",
            serviceName, failureCount, correlationId);

        kafkaTemplate.send("audit-persistent-failures", Map.of(
            "serviceName", serviceName,
            "failureCount", failureCount,
            "alertType", "PERSISTENT_FAILURE",
            "requiresImmediateAction", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        try {
            notificationService.sendCriticalAlert(
                "Persistent Audit Service Failure",
                String.format("CRITICAL: Service %s has failed %d consecutive health checks", serviceName, failureCount),
                Map.of("serviceName", serviceName, "failureCount", failureCount.toString(), "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to send persistent failure alert: {}", e.getMessage());
        }
    }

    private void escalateCriticalSystemHealth(String serviceName, String healthStatus, String correlationId) {
        try {
            auditNotificationService.sendCriticalAuditAlert(
                "Critical Audit System Health Issue",
                String.format("CRITICAL: Audit system service %s is %s", serviceName, healthStatus),
                Map.of("serviceName", serviceName, "healthStatus", healthStatus, "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to escalate critical system health: {}", e.getMessage());
        }
    }

    private void escalateDataIntegrityIssue(String serviceName, List<String> healthIssues, String correlationId) {
        try {
            auditNotificationService.sendCriticalAuditAlert(
                "Critical Audit Data Integrity Issue",
                String.format("CRITICAL: Data integrity issues detected in %s: %s", serviceName, healthIssues),
                Map.of("serviceName", serviceName, "issues", String.join(", ", healthIssues != null ? healthIssues : Collections.emptyList()), "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to escalate data integrity issue: {}", e.getMessage());
        }
    }

    private void escalateComplianceIssue(String serviceName, List<String> healthIssues, String correlationId) {
        try {
            auditNotificationService.sendCriticalAuditAlert(
                "Critical Audit Compliance Issue",
                String.format("CRITICAL: Compliance issues detected in %s: %s", serviceName, healthIssues),
                Map.of("serviceName", serviceName, "issues", String.join(", ", healthIssues != null ? healthIssues : Collections.emptyList()), "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to escalate compliance issue: {}", e.getMessage());
        }
    }

    private void escalateServiceDegradation(String serviceName, String auditSystemComponent,
            List<String> healthIssues, String correlationId) {
        try {
            auditNotificationService.sendAuditAlert(
                "Audit Service Degradation",
                String.format("WARNING: Audit service %s is degraded: %s", serviceName, healthIssues),
                Map.of("serviceName", serviceName, "component", auditSystemComponent != null ? auditSystemComponent : "", "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to escalate service degradation: {}", e.getMessage());
        }
    }

    private void escalateUnhealthyService(String serviceName, String auditSystemComponent,
            List<String> healthIssues, Boolean isSystemCritical, String correlationId) {
        try {
            if (isSystemCritical) {
                auditNotificationService.sendCriticalAuditAlert(
                    "Critical Audit Service Unhealthy",
                    String.format("CRITICAL: Critical audit service %s is unhealthy: %s", serviceName, healthIssues),
                    Map.of("serviceName", serviceName, "component", auditSystemComponent != null ? auditSystemComponent : "", "correlationId", correlationId)
                );
            } else {
                auditNotificationService.sendAuditAlert(
                    "Audit Service Unhealthy",
                    String.format("ERROR: Audit service %s is unhealthy: %s", serviceName, healthIssues),
                    Map.of("serviceName", serviceName, "component", auditSystemComponent != null ? auditSystemComponent : "", "correlationId", correlationId)
                );
            }
        } catch (Exception e) {
            log.error("Failed to escalate unhealthy service: {}", e.getMessage());
        }
    }

    private void escalateFailedService(String serviceName, String auditSystemComponent,
            List<String> healthIssues, Boolean isSystemCritical, String correlationId) {
        try {
            auditNotificationService.sendCriticalAuditAlert(
                "Critical Audit Service Failed",
                String.format("CRITICAL: Audit service %s has failed: %s", serviceName, healthIssues),
                Map.of("serviceName", serviceName, "component", auditSystemComponent != null ? auditSystemComponent : "",
                    "isSystemCritical", isSystemCritical.toString(), "correlationId", correlationId)
            );
        } catch (Exception e) {
            log.error("Failed to escalate failed service: {}", e.getMessage());
        }
    }

    private void handlePerformanceDegradation(String serviceName, Long checkDurationMs, String correlationId) {
        log.warn("Performance degradation detected: service={}, duration={}ms, correlationId={}",
            serviceName, checkDurationMs, correlationId);

        kafkaTemplate.send("audit-performance-degradation", Map.of(
            "serviceName", serviceName,
            "checkDurationMs", checkDurationMs,
            "threshold", 5000,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleConnectivityIssue(String serviceName, List<String> healthIssues, String correlationId) {
        log.warn("Connectivity issues detected: service={}, issues={}, correlationId={}",
            serviceName, healthIssues, correlationId);

        kafkaTemplate.send("audit-connectivity-issues", Map.of(
            "serviceName", serviceName,
            "issues", healthIssues != null ? healthIssues : Collections.emptyList(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleStorageIssue(String serviceName, Map<String, Object> healthMetrics, String correlationId) {
        log.warn("Storage issues detected: service={}, metrics={}, correlationId={}",
            serviceName, healthMetrics, correlationId);

        kafkaTemplate.send("audit-storage-issues", Map.of(
            "serviceName", serviceName,
            "metrics", healthMetrics != null ? healthMetrics : new HashMap<>(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private AuditEvent.AuditResult mapHealthStatusToResult(String healthStatus) {
        return switch (healthStatus.toUpperCase()) {
            case "HEALTHY" -> AuditEvent.AuditResult.SUCCESS;
            case "DEGRADED" -> AuditEvent.AuditResult.PARTIAL_SUCCESS;
            case "UNHEALTHY", "FAILED" -> AuditEvent.AuditResult.FAILURE;
            default -> AuditEvent.AuditResult.SYSTEM_ERROR;
        };
    }

    private AuditEvent.AuditSeverity mapHealthStatusToSeverity(String healthStatus, Boolean isSystemCritical) {
        return switch (healthStatus.toUpperCase()) {
            case "HEALTHY" -> AuditEvent.AuditSeverity.LOW;
            case "DEGRADED" -> isSystemCritical ? AuditEvent.AuditSeverity.HIGH : AuditEvent.AuditSeverity.MEDIUM;
            case "UNHEALTHY" -> isSystemCritical ? AuditEvent.AuditSeverity.CRITICAL : AuditEvent.AuditSeverity.HIGH;
            case "FAILED" -> AuditEvent.AuditSeverity.CRITICAL;
            default -> AuditEvent.AuditSeverity.MEDIUM;
        };
    }

    private Map<String, String> convertToStringMap(Map<String, Object> objectMap) {
        if (objectMap == null) return new HashMap<>();

        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
            stringMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
        }
        return stringMap;
    }
}