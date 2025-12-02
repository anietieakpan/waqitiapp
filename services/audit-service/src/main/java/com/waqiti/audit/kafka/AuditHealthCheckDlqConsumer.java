package com.waqiti.audit.kafka;

import com.waqiti.common.events.AuditHealthCheckEvent;
import com.waqiti.audit.domain.AuditHealthStatus;
import com.waqiti.audit.repository.AuditHealthStatusRepository;
import com.waqiti.audit.service.AuditHealthService;
import com.waqiti.audit.service.AuditEscalationService;
import com.waqiti.audit.metrics.AuditMetricsService;
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
public class AuditHealthCheckDlqConsumer {

    private final AuditHealthStatusRepository healthStatusRepository;
    private final AuditHealthService auditHealthService;
    private final AuditEscalationService escalationService;
    private final AuditMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter criticalHealthFailureCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("audit_health_check_dlq_processed_total")
            .description("Total number of successfully processed audit health check DLQ events")
            .register(meterRegistry);
        errorCounter = Counter.builder("audit_health_check_dlq_errors_total")
            .description("Total number of audit health check DLQ processing errors")
            .register(meterRegistry);
        criticalHealthFailureCounter = Counter.builder("audit_health_critical_failures_total")
            .description("Total number of critical audit health failures requiring executive escalation")
            .register(meterRegistry);
        processingTimer = Timer.builder("audit_health_check_dlq_processing_duration")
            .description("Time taken to process audit health check DLQ events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"audit-health-check-dlq", "audit-system-health-dlq", "audit-service-health-dlq"},
        groupId = "audit-health-check-dlq-service-group",
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
    @CircuitBreaker(name = "audit-health-check-dlq", fallbackMethod = "handleAuditHealthCheckDlqEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuditHealthCheckDlqEvent(
            @Payload AuditHealthCheckEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("audit-health-dlq-%s-p%d-o%d", event.getServiceName(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getServiceName(), event.getHealthStatus(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Health check DLQ event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL audit health check from DLQ: service={}, status={}, topic={}",
                event.getServiceName(), event.getHealthStatus(), topic);

            // Clean expired entries periodically
            cleanExpiredEntries();

            // DLQ health check events indicate critical system failures
            if (isCriticalHealthFailure(event)) {
                criticalHealthFailureCounter.increment();
                escalateHealthFailureToExecutives(event, correlationId, topic);
            }

            switch (event.getHealthStatus()) {
                case DOWN:
                    processServiceDownDlq(event, correlationId, topic);
                    break;

                case DEGRADED:
                    processServiceDegradedDlq(event, correlationId, topic);
                    break;

                case UNKNOWN:
                    processServiceUnknownDlq(event, correlationId, topic);
                    break;

                case OUT_OF_SERVICE:
                    processServiceOutOfServiceDlq(event, correlationId, topic);
                    break;

                default:
                    processGenericHealthIssueDlq(event, correlationId, topic);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAuditEvent("AUDIT_HEALTH_CHECK_DLQ_PROCESSED", event.getServiceName(),
                Map.of("healthStatus", event.getHealthStatus(), "checkType", event.getCheckType(),
                    "serviceName", event.getServiceName(), "correlationId", correlationId,
                    "dlqTopic", topic, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process audit health check DLQ event: {}", e.getMessage(), e);

            // Send to executive escalation for health check DLQ failures
            sendExecutiveHealthEscalation(event, correlationId, topic, e);

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuditHealthCheckDlqEventFallback(
            AuditHealthCheckEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("audit-health-dlq-fallback-%s-p%d-o%d", event.getServiceName(), partition, offset);

        log.error("Circuit breaker fallback triggered for audit health check DLQ: service={}, topic={}, error={}",
            event.getServiceName(), topic, ex.getMessage());

        // Critical: health check DLQ circuit breaker means audit system failure
        sendExecutiveHealthEscalation(event, correlationId, topic, ex);

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuditHealthCheckEvent(
            @Payload AuditHealthCheckEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-audit-health-%s-%d", event.getServiceName(), System.currentTimeMillis());

        log.error("CRITICAL: Audit health check DLQ permanently failed - service={}, topic={}, error={}",
            event.getServiceName(), topic, exceptionMessage);

        // Save to audit trail for system monitoring
        auditService.logAuditEvent("AUDIT_HEALTH_CHECK_DLQ_DLT_EVENT", event.getServiceName(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "healthStatus", event.getHealthStatus(), "correlationId", correlationId,
                "requiresImmediateSystemIntervention", true, "timestamp", Instant.now()));

        // Immediate executive escalation for DLT health check failures
        sendExecutiveHealthEscalation(event, correlationId, topic, new RuntimeException(exceptionMessage));
    }

    private boolean isCriticalHealthFailure(AuditHealthCheckEvent event) {
        return Arrays.asList("DOWN", "OUT_OF_SERVICE", "UNKNOWN").contains(event.getHealthStatus().toString()) ||
               Arrays.asList("AUDIT_TRAIL", "COMPLIANCE_ENGINE", "REGULATORY_MONITOR").contains(event.getServiceName());
    }

    private void processServiceDownDlq(AuditHealthCheckEvent event, String correlationId, String topic) {
        AuditHealthStatus healthStatus = AuditHealthStatus.builder()
            .serviceName(event.getServiceName())
            .status("DOWN")
            .severity("CRITICAL")
            .description(String.format("Service down from DLQ: %s", event.getDescription()))
            .checkType(event.getCheckType())
            .correlationId(correlationId)
            .source(topic)
            .lastChecked(LocalDateTime.now())
            .requiresImmediateAction(true)
            .build();
        healthStatusRepository.save(healthStatus);

        auditHealthService.initiateServiceRecovery(event.getServiceName(), "DLQ_SERVICE_DOWN");
        escalationService.escalateServiceDown(event, correlationId);

        // Immediate operations team notification
        notificationService.sendOperationalAlert(
            "CRITICAL: Audit Service Down (DLQ)",
            String.format("Audit service %s is down - from DLQ indicates multiple failure attempts", event.getServiceName()),
            "CRITICAL"
        );

        log.error("Service down DLQ processed: service={}", event.getServiceName());
    }

    private void processServiceDegradedDlq(AuditHealthCheckEvent event, String correlationId, String topic) {
        auditHealthService.recordServiceDegradation(event.getServiceName(), event.getDescription(), "DLQ_SOURCE");
        escalationService.escalateServiceDegradation(event, correlationId);

        // Performance team notification
        notificationService.sendPerformanceAlert(
            "Audit Service Degraded (DLQ)",
            String.format("Audit service %s is degraded - requires performance investigation", event.getServiceName()),
            "HIGH"
        );

        log.warn("Service degraded DLQ processed: service={}", event.getServiceName());
    }

    private void processServiceUnknownDlq(AuditHealthCheckEvent event, String correlationId, String topic) {
        auditHealthService.recordUnknownServiceState(event.getServiceName(), event.getDescription());
        escalationService.escalateUnknownServiceState(event, correlationId);

        // Monitoring team notification
        notificationService.sendMonitoringAlert(
            "Audit Service Unknown State (DLQ)",
            String.format("Audit service %s state unknown - monitoring system may be compromised", event.getServiceName()),
            "HIGH"
        );

        log.error("Service unknown state DLQ processed: service={}", event.getServiceName());
    }

    private void processServiceOutOfServiceDlq(AuditHealthCheckEvent event, String correlationId, String topic) {
        auditHealthService.recordServiceOutOfService(event.getServiceName(), event.getDescription());
        escalationService.escalateServiceOutOfService(event, correlationId);

        // Critical: out of service affects audit compliance
        notificationService.sendComplianceAlert(
            "CRITICAL: Audit Service Out of Service (DLQ)",
            String.format("Audit service %s out of service - potential compliance impact", event.getServiceName()),
            "CRITICAL"
        );

        log.error("Service out of service DLQ processed: service={}", event.getServiceName());
    }

    private void processGenericHealthIssueDlq(AuditHealthCheckEvent event, String correlationId, String topic) {
        auditHealthService.recordGenericHealthIssue(event.getServiceName(), event.getDescription(), "DLQ_GENERIC");
        escalationService.escalateGenericHealthIssue(event, correlationId);

        log.warn("Generic health issue DLQ processed: service={}, status={}",
            event.getServiceName(), event.getHealthStatus());
    }

    private void escalateHealthFailureToExecutives(AuditHealthCheckEvent event, String correlationId, String topic) {
        try {
            notificationService.sendExecutiveAlert(
                "CRITICAL: Audit System Health Failure from DLQ",
                String.format("Critical audit system health failure for service %s from DLQ topic %s. " +
                    "Status: %s, Description: %s. This indicates repeated failures and potential system compromise.",
                    event.getServiceName(), topic, event.getHealthStatus(), event.getDescription()),
                Map.of(
                    "serviceName", event.getServiceName(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "healthStatus", event.getHealthStatus(),
                    "checkType", event.getCheckType(),
                    "priority", "AUDIT_SYSTEM_CRITICAL"
                )
            );
        } catch (Exception ex) {
            log.error("Failed to send executive health escalation: {}", ex.getMessage());
        }
    }

    private void sendExecutiveHealthEscalation(AuditHealthCheckEvent event, String correlationId, String topic, Exception ex) {
        try {
            notificationService.sendExecutiveAlert(
                "SYSTEM CRITICAL: Audit Health Check DLQ Processing Failure",
                String.format("CRITICAL SYSTEM FAILURE: Unable to process audit health check from DLQ for service %s. " +
                    "This indicates a serious audit system failure requiring immediate executive intervention. " +
                    "Topic: %s, Error: %s", event.getServiceName(), topic, ex.getMessage()),
                Map.of(
                    "serviceName", event.getServiceName(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "errorMessage", ex.getMessage(),
                    "priority", "AUDIT_SYSTEM_CRITICAL_FAILURE"
                )
            );
        } catch (Exception notificationEx) {
            log.error("CRITICAL: Failed to send executive escalation for audit health DLQ failure: {}", notificationEx.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

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
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}