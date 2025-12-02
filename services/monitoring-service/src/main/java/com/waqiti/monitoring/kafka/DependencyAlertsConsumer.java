package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.DependencyAlertEvent;
import com.waqiti.monitoring.service.DependencyMonitoringService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.InfrastructureMetricsService;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class DependencyAlertsConsumer {

    private final DependencyMonitoringService dependencyService;
    private final AlertingService alertingService;
    private final InfrastructureMetricsService metricsService;
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
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("dependency_alerts_processed_total")
            .description("Total number of successfully processed dependency alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("dependency_alerts_errors_total")
            .description("Total number of dependency alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("dependency_alerts_processing_duration")
            .description("Time taken to process dependency alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"dependency-alerts", "service-dependency-alerts", "microservice-dependency-health"},
        groupId = "dependency-monitoring-group",
        containerFactory = "criticalInfrastructureKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "dependency-alerts", fallbackMethod = "handleDependencyAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleDependencyAlertEvent(
            @Payload DependencyAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("dependency-%s-%s-p%d-o%d", event.getSourceService(), event.getTargetService(), partition, offset);
        String eventKey = String.format("%s-%s-%s-%s", event.getSourceService(), event.getTargetService(), event.getAlertType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing dependency alert: source={}, target={}, type={}, severity={}",
                event.getSourceService(), event.getTargetService(), event.getAlertType(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getAlertType()) {
                case SERVICE_UNAVAILABLE:
                    handleServiceUnavailable(event, correlationId);
                    break;

                case HIGH_LATENCY:
                    handleHighLatency(event, correlationId);
                    break;

                case CONNECTION_TIMEOUT:
                    handleConnectionTimeout(event, correlationId);
                    break;

                case CIRCUIT_BREAKER_OPEN:
                    handleCircuitBreakerOpen(event, correlationId);
                    break;

                case RATE_LIMIT_EXCEEDED:
                    handleRateLimitExceeded(event, correlationId);
                    break;

                case AUTHENTICATION_FAILURE:
                    handleAuthenticationFailure(event, correlationId);
                    break;

                case DEPENDENCY_CHAIN_FAILURE:
                    handleDependencyChainFailure(event, correlationId);
                    break;

                case VERSION_MISMATCH:
                    handleVersionMismatch(event, correlationId);
                    break;

                case HEALTH_CHECK_FAILED:
                    handleHealthCheckFailed(event, correlationId);
                    break;

                case FAILOVER_ACTIVATED:
                    handleFailoverActivated(event, correlationId);
                    break;

                case DEPENDENCY_RESTORED:
                    handleDependencyRestored(event, correlationId);
                    break;

                default:
                    log.warn("Unknown dependency alert type: {}", event.getAlertType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logInfrastructureEvent("DEPENDENCY_ALERT_PROCESSED", 
                String.format("%s->%s", event.getSourceService(), event.getTargetService()),
                Map.of("alertType", event.getAlertType(), "severity", event.getSeverity(),
                    "sourceService", event.getSourceService(), "targetService", event.getTargetService(),
                    "responseTime", event.getResponseTimeMs(), "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process dependency alert: {}", e.getMessage(), e);

            kafkaTemplate.send("dependency-alerts-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleDependencyAlertEventFallback(
            DependencyAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("dependency-fallback-%s-%s-p%d-o%d", 
            event.getSourceService(), event.getTargetService(), partition, offset);

        log.error("Circuit breaker fallback for dependency alert: source={}, target={}, error={}",
            event.getSourceService(), event.getTargetService(), ex.getMessage());

        kafkaTemplate.send("dependency-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Dependency Alert Processing Failure",
                String.format("Dependency alert processing failed (%s -> %s): %s", 
                    event.getSourceService(), event.getTargetService(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltDependencyAlertEvent(
            @Payload DependencyAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-dependency-%s-%s-%d", 
            event.getSourceService(), event.getTargetService(), System.currentTimeMillis());

        log.error("DLT handler - Dependency alert failed: source={}, target={}, topic={}, error={}",
            event.getSourceService(), event.getTargetService(), topic, exceptionMessage);

        auditService.logInfrastructureEvent("DEPENDENCY_ALERT_DLT_EVENT", 
            String.format("%s->%s", event.getSourceService(), event.getTargetService()),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Dependency Alert DLT Event",
                String.format("Dependency alert (%s -> %s) sent to DLT: %s", 
                    event.getSourceService(), event.getTargetService(), exceptionMessage),
                Map.of("sourceService", event.getSourceService(), "targetService", event.getTargetService(), 
                    "topic", topic, "correlationId", correlationId)
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

    private void handleServiceUnavailable(DependencyAlertEvent event, String correlationId) {
        log.error("Service unavailable: {} -> {}, downtime={}",
            event.getSourceService(), event.getTargetService(), event.getDowntimeDuration());

        dependencyService.handleServiceUnavailable(
            event.getSourceService(), event.getTargetService(), event.getDowntimeDuration());

        alertingService.sendCriticalAlert(
            "Dependency Service Unavailable",
            String.format("Service %s is unavailable for %s (called by %s)",
                event.getTargetService(), event.getSourceService(), event.getDowntimeDuration()),
            correlationId
        );

        // Trigger circuit breaker for dependent services
        dependencyService.triggerCircuitBreaker(event.getSourceService(), event.getTargetService());

        metricsService.recordServiceUnavailable(event.getSourceService(), event.getTargetService());

        // Activate failover if available
        dependencyService.activateFailover(event.getSourceService(), event.getTargetService());
    }

    private void handleHighLatency(DependencyAlertEvent event, String correlationId) {
        log.warn("High latency detected: {} -> {}, responseTime={}ms, threshold={}ms",
            event.getSourceService(), event.getTargetService(), event.getResponseTimeMs(), event.getLatencyThresholdMs());

        dependencyService.handleHighLatency(
            event.getSourceService(), event.getTargetService(), event.getResponseTimeMs(), event.getLatencyThresholdMs());

        if (event.getResponseTimeMs() > event.getLatencyThresholdMs() * 3) {
            alertingService.sendHighPriorityAlert(
                "Critical Dependency Latency",
                String.format("High latency: %s -> %s (%dms, threshold: %dms)",
                    event.getSourceService(), event.getTargetService(), event.getResponseTimeMs(), event.getLatencyThresholdMs()),
                correlationId
            );
        } else {
            alertingService.sendMediumPriorityAlert(
                "Dependency Latency Warning",
                String.format("Elevated latency: %s -> %s (%dms, threshold: %dms)",
                    event.getSourceService(), event.getTargetService(), event.getResponseTimeMs(), event.getLatencyThresholdMs()),
                correlationId
            );
        }

        metricsService.recordHighLatency(event.getSourceService(), event.getTargetService(), event.getResponseTimeMs());
    }

    private void handleConnectionTimeout(DependencyAlertEvent event, String correlationId) {
        log.warn("Connection timeout: {} -> {}, timeoutMs={}, retryCount={}",
            event.getSourceService(), event.getTargetService(), event.getTimeoutMs(), event.getRetryCount());

        dependencyService.handleConnectionTimeout(
            event.getSourceService(), event.getTargetService(), event.getTimeoutMs(), event.getRetryCount());

        alertingService.sendMediumPriorityAlert(
            "Dependency Connection Timeout",
            String.format("Connection timeout: %s -> %s (%dms, %d retries)",
                event.getSourceService(), event.getTargetService(), event.getTimeoutMs(), event.getRetryCount()),
            correlationId
        );

        metricsService.recordConnectionTimeout(event.getSourceService(), event.getTargetService());

        // Adjust timeout settings if frequent timeouts
        if (event.getRetryCount() > 3) {
            dependencyService.adjustTimeoutSettings(event.getSourceService(), event.getTargetService());
        }
    }

    private void handleCircuitBreakerOpen(DependencyAlertEvent event, String correlationId) {
        log.warn("Circuit breaker opened: {} -> {}, failureCount={}, failureRate={}%",
            event.getSourceService(), event.getTargetService(), event.getFailureCount(), event.getFailureRate());

        dependencyService.handleCircuitBreakerOpen(
            event.getSourceService(), event.getTargetService(), event.getFailureCount(), event.getFailureRate());

        alertingService.sendHighPriorityAlert(
            "Dependency Circuit Breaker Opened",
            String.format("Circuit breaker opened: %s -> %s (%d failures, %.1f%% rate)",
                event.getSourceService(), event.getTargetService(), event.getFailureCount(), event.getFailureRate()),
            correlationId
        );

        metricsService.recordCircuitBreakerOpen(event.getSourceService(), event.getTargetService());

        // Activate fallback mechanisms
        dependencyService.activateFallback(event.getSourceService(), event.getTargetService());
    }

    private void handleRateLimitExceeded(DependencyAlertEvent event, String correlationId) {
        log.warn("Rate limit exceeded: {} -> {}, currentRate={}, limit={}",
            event.getSourceService(), event.getTargetService(), event.getCurrentRate(), event.getRateLimit());

        dependencyService.handleRateLimitExceeded(
            event.getSourceService(), event.getTargetService(), event.getCurrentRate(), event.getRateLimit());

        alertingService.sendMediumPriorityAlert(
            "Dependency Rate Limit Exceeded",
            String.format("Rate limit exceeded: %s -> %s (%d/%d requests)",
                event.getSourceService(), event.getTargetService(), event.getCurrentRate(), event.getRateLimit()),
            correlationId
        );

        metricsService.recordRateLimitExceeded(event.getSourceService(), event.getTargetService());

        // Implement backoff strategy
        dependencyService.implementBackoffStrategy(event.getSourceService(), event.getTargetService());
    }

    private void handleAuthenticationFailure(DependencyAlertEvent event, String correlationId) {
        log.error("Authentication failure: {} -> {}, authType={}, failureReason={}",
            event.getSourceService(), event.getTargetService(), event.getAuthenticationType(), event.getAuthFailureReason());

        dependencyService.handleAuthenticationFailure(
            event.getSourceService(), event.getTargetService(), event.getAuthenticationType(), event.getAuthFailureReason());

        alertingService.sendHighPriorityAlert(
            "Dependency Authentication Failure",
            String.format("Auth failure: %s -> %s (%s: %s)",
                event.getSourceService(), event.getTargetService(), event.getAuthenticationType(), event.getAuthFailureReason()),
            correlationId
        );

        metricsService.recordAuthenticationFailure(event.getSourceService(), event.getTargetService());

        // Refresh authentication credentials
        dependencyService.refreshCredentials(event.getSourceService(), event.getTargetService());
    }

    private void handleDependencyChainFailure(DependencyAlertEvent event, String correlationId) {
        log.error("Dependency chain failure: {} -> {}, chainDepth={}, affectedServices={}",
            event.getSourceService(), event.getTargetService(), event.getChainDepth(), event.getAffectedServices());

        dependencyService.handleDependencyChainFailure(
            event.getSourceService(), event.getTargetService(), event.getAffectedServices());

        alertingService.sendCriticalAlert(
            "Dependency Chain Failure",
            String.format("Chain failure: %s -> %s (depth: %d, affected: %d services)",
                event.getSourceService(), event.getTargetService(), event.getChainDepth(), event.getAffectedServices().size()),
            correlationId
        );

        metricsService.recordDependencyChainFailure(event.getSourceService(), event.getAffectedServices().size());

        // Send to incident correlation
        kafkaTemplate.send("correlated-incidents", Map.of(
            "eventType", "DEPENDENCY_CHAIN_FAILURE",
            "sourceService", event.getSourceService(),
            "targetService", event.getTargetService(),
            "affectedServices", event.getAffectedServices(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleVersionMismatch(DependencyAlertEvent event, String correlationId) {
        log.warn("Version mismatch: {} -> {}, sourceVersion={}, targetVersion={}, compatibility={}",
            event.getSourceService(), event.getTargetService(), event.getSourceVersion(), 
            event.getTargetVersion(), event.getCompatibilityLevel());

        dependencyService.handleVersionMismatch(
            event.getSourceService(), event.getTargetService(), 
            event.getSourceVersion(), event.getTargetVersion(), event.getCompatibilityLevel());

        String alertLevel = "CRITICAL".equals(event.getCompatibilityLevel()) ? "HIGH" : "MEDIUM";
        
        alertingService.sendAlert(
            alertLevel,
            "Dependency Version Mismatch",
            String.format("Version mismatch: %s (v%s) -> %s (v%s), compatibility: %s",
                event.getSourceService(), event.getSourceVersion(), 
                event.getTargetService(), event.getTargetVersion(), event.getCompatibilityLevel()),
            correlationId
        );

        metricsService.recordVersionMismatch(event.getSourceService(), event.getTargetService());
    }

    private void handleHealthCheckFailed(DependencyAlertEvent event, String correlationId) {
        log.warn("Health check failed: {} -> {}, healthEndpoint={}, statusCode={}",
            event.getSourceService(), event.getTargetService(), event.getHealthEndpoint(), event.getHealthStatusCode());

        dependencyService.handleHealthCheckFailure(
            event.getSourceService(), event.getTargetService(), 
            event.getHealthEndpoint(), event.getHealthStatusCode());

        alertingService.sendMediumPriorityAlert(
            "Dependency Health Check Failed",
            String.format("Health check failed: %s -> %s (%s returned %d)",
                event.getSourceService(), event.getTargetService(), 
                event.getHealthEndpoint(), event.getHealthStatusCode()),
            correlationId
        );

        metricsService.recordHealthCheckFailure(event.getSourceService(), event.getTargetService());
    }

    private void handleFailoverActivated(DependencyAlertEvent event, String correlationId) {
        log.info("Failover activated: {} -> {}, failoverTarget={}, activationReason={}",
            event.getSourceService(), event.getTargetService(), event.getFailoverTarget(), event.getFailoverReason());

        dependencyService.handleFailoverActivated(
            event.getSourceService(), event.getTargetService(), 
            event.getFailoverTarget(), event.getFailoverReason());

        alertingService.sendInfoAlert(
            "Dependency Failover Activated",
            String.format("Failover activated: %s -> %s (target: %s, reason: %s)",
                event.getSourceService(), event.getTargetService(), 
                event.getFailoverTarget(), event.getFailoverReason()),
            correlationId
        );

        metricsService.recordFailoverActivated(event.getSourceService(), event.getTargetService());
    }

    private void handleDependencyRestored(DependencyAlertEvent event, String correlationId) {
        log.info("Dependency restored: {} -> {}, restorationTime={}, previousIssue={}",
            event.getSourceService(), event.getTargetService(), event.getRestorationTime(), event.getPreviousIssue());

        dependencyService.handleDependencyRestored(
            event.getSourceService(), event.getTargetService(), 
            event.getRestorationTime(), event.getPreviousIssue());

        alertingService.sendInfoAlert(
            "Dependency Restored",
            String.format("Dependency restored: %s -> %s (restoration time: %s, previous: %s)",
                event.getSourceService(), event.getTargetService(), 
                event.getRestorationTime(), event.getPreviousIssue()),
            correlationId
        );

        metricsService.recordDependencyRestored(event.getSourceService(), event.getTargetService());

        // Clear any ongoing dependency alerts
        dependencyService.clearDependencyAlerts(event.getSourceService(), event.getTargetService());
    }
}