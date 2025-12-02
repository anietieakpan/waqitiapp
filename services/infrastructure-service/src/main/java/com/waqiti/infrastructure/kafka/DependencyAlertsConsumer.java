package com.waqiti.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.infrastructure.model.DependencyAlert;
import com.waqiti.infrastructure.service.InfrastructureAlertService;
import com.waqiti.infrastructure.service.DependencyMetricsService;
import com.waqiti.infrastructure.service.RecoveryService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumer for processing dependency failure alerts and service health events.
 * Handles P2 priority events related to service dependencies and cascading failures.
 */
@Component
public class DependencyAlertsConsumer {

    private static final Logger logger = LoggerFactory.getLogger(DependencyAlertsConsumer.class);
    private static final String CIRCUIT_BREAKER_NAME = "dependencyAlertsConsumer";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    @Autowired
    private InfrastructureAlertService infrastructureAlertService;

    @Autowired
    private DependencyMetricsService dependencyMetricsService;

    @Autowired
    private RecoveryService recoveryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    // Idempotency tracking
    private final Map<String, LocalDateTime> processedEvents = new ConcurrentHashMap<>();

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Timer processingTimer;
    private final AtomicLong criticalDependenciesDownGauge = new AtomicLong(0);
    private final AtomicLong cascadingFailuresGauge = new AtomicLong(0);
    private final AtomicLong dependencyTimeoutsGauge = new AtomicLong(0);

    public DependencyAlertsConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.eventsProcessedCounter = Counter.builder("dependency_alerts_processed_total")
                .description("Total number of dependency alerts processed")
                .register(meterRegistry);
        this.eventsFailedCounter = Counter.builder("dependency_alerts_failed_total")
                .description("Total number of dependency alerts that failed processing")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("dependency_alerts_processing_duration")
                .description("Time taken to process dependency alerts")
                .register(meterRegistry);

        Gauge.builder("dependency_critical_services_down")
                .description("Number of critical dependencies currently down")
                .register(meterRegistry, criticalDependenciesDownGauge, AtomicLong::get);

        Gauge.builder("dependency_cascading_failures_active")
                .description("Number of active cascading failures")
                .register(meterRegistry, cascadingFailuresGauge, AtomicLong::get);

        Gauge.builder("dependency_timeouts_active")
                .description("Number of dependencies experiencing timeouts")
                .register(meterRegistry, dependencyTimeoutsGauge, AtomicLong::get);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
            include = {Exception.class}
    )
    @KafkaListener(topics = "dependency-alerts", groupId = "infrastructure-service-dependency-group")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "fallbackProcessDependencyAlert")
    public void processDependencyAlert(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_KEY) String eventKey,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            logger.info("Processing dependency alert: key={}, topic={}, partition={}, offset={}",
                       eventKey, topic, partition, offset);

            // Check idempotency
            if (isDuplicate(eventKey)) {
                logger.warn("Duplicate dependency alert detected: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            // Parse event
            DependencyAlert alert = objectMapper.readValue(eventPayload, DependencyAlert.class);

            // Validate event
            validateDependencyAlert(alert);

            // Process based on dependency alert type
            processDependencyAlertByType(alert);

            // Update metrics
            updateMetrics(alert);

            // Record processing
            recordProcessedEvent(eventKey);

            eventsProcessedCounter.increment();
            acknowledgment.acknowledge();

            logger.info("Successfully processed dependency alert: {}", eventKey);

        } catch (Exception e) {
            logger.error("Error processing dependency alert: key={}, error={}", eventKey, e.getMessage(), e);
            eventsFailedCounter.increment();

            // Send alert for processing failure
            infrastructureAlertService.sendOperationalAlert(
                "Dependency Alert Processing Failed",
                String.format("Failed to process dependency alert %s: %s", eventKey, e.getMessage()),
                "HIGH"
            );

            throw e; // Trigger retry mechanism
        } finally {
            sample.stop(processingTimer);
            cleanupExpiredEvents();
        }
    }

    private void processDependencyAlertByType(DependencyAlert alert) {
        switch (alert.getAlertType()) {
            case "DEPENDENCY_DOWN":
                handleDependencyDown(alert);
                break;
            case "DEPENDENCY_DEGRADED":
                handleDependencyDegraded(alert);
                break;
            case "CASCADING_FAILURE":
                handleCascadingFailure(alert);
                break;
            case "TIMEOUT_SPIKE":
                handleTimeoutSpike(alert);
                break;
            case "CONNECTION_POOL_EXHAUSTED":
                handleConnectionPoolExhausted(alert);
                break;
            case "CIRCUIT_BREAKER_OPEN":
                handleCircuitBreakerOpen(alert);
                break;
            case "RETRY_EXHAUSTED":
                handleRetryExhausted(alert);
                break;
            case "FAILOVER_TRIGGERED":
                handleFailoverTriggered(alert);
                break;
            case "DEPENDENCY_RECOVERED":
                handleDependencyRecovered(alert);
                break;
            case "HEALTH_CHECK_FAILED":
                handleHealthCheckFailed(alert);
                break;
            case "SLA_BREACH":
                handleSLABreach(alert);
                break;
            case "CAPACITY_EXCEEDED":
                handleCapacityExceeded(alert);
                break;
            default:
                logger.warn("Unknown dependency alert type: {}", alert.getAlertType());
                handleUnknownAlertType(alert);
        }
    }

    private void handleDependencyDown(DependencyAlert alert) {
        logger.error("Critical dependency down: service={}, dependency={}, impact={}",
                    alert.getServiceName(), alert.getDependencyName(), alert.getImpactLevel());

        if ("CRITICAL".equals(alert.getCriticality())) {
            criticalDependenciesDownGauge.incrementAndGet();

            // Immediate executive alert for critical dependencies
            infrastructureAlertService.sendExecutiveAlert(
                "Critical Dependency Down",
                String.format("Critical dependency %s is down affecting service %s. Impact: %s",
                             alert.getDependencyName(), alert.getServiceName(), alert.getImpactLevel()),
                "CRITICAL"
            );

            // Trigger immediate failover
            recoveryService.initiateEmergencyFailover(alert);
        } else {
            infrastructureAlertService.sendOperationalAlert(
                "Dependency Down",
                String.format("Dependency %s is down affecting service %s",
                             alert.getDependencyName(), alert.getServiceName()),
                "HIGH"
            );
        }

        dependencyMetricsService.recordDependencyFailure(alert);
        recoveryService.initiateDependencyRecovery(alert);
    }

    private void handleDependencyDegraded(DependencyAlert alert) {
        logger.warn("Dependency degraded: service={}, dependency={}, degradation={}%",
                   alert.getServiceName(), alert.getDependencyName(), alert.getDegradationLevel());

        // Check degradation severity
        if (alert.getDegradationLevel() > 75) {
            infrastructureAlertService.sendExecutiveAlert(
                "Severe Dependency Degradation",
                String.format("Dependency %s severely degraded (%d%%) affecting service %s",
                             alert.getDependencyName(), alert.getDegradationLevel(), alert.getServiceName()),
                "HIGH"
            );
        } else if (alert.getDegradationLevel() > 50) {
            infrastructureAlertService.sendOperationalAlert(
                "Significant Dependency Degradation",
                String.format("Dependency %s degraded (%d%%) affecting service %s",
                             alert.getDependencyName(), alert.getDegradationLevel(), alert.getServiceName()),
                "MEDIUM"
            );
        }

        dependencyMetricsService.recordDependencyDegradation(alert);
        recoveryService.initiatePerformanceOptimization(alert);
    }

    private void handleCascadingFailure(DependencyAlert alert) {
        logger.error("Cascading failure detected: origin={}, affected={}, depth={}",
                    alert.getFailureOrigin(), alert.getAffectedServices(), alert.getCascadeDepth());

        cascadingFailuresGauge.incrementAndGet();

        // Always executive alert for cascading failures
        infrastructureAlertService.sendExecutiveAlert(
            "Cascading Failure Detected",
            String.format("Cascading failure from %s affecting %d services (depth: %d)",
                         alert.getFailureOrigin(), alert.getAffectedServices().size(), alert.getCascadeDepth()),
            "CRITICAL"
        );

        // Compliance alert if widespread
        if (alert.getAffectedServices().size() > 5) {
            infrastructureAlertService.sendComplianceAlert(
                "Widespread System Failure",
                String.format("Cascading failure affecting %d services may impact regulatory compliance",
                             alert.getAffectedServices().size()),
                "CRITICAL"
            );
        }

        dependencyMetricsService.recordCascadingFailure(alert);
        recoveryService.initiateSystemWideRecovery(alert);
    }

    private void handleTimeoutSpike(DependencyAlert alert) {
        logger.warn("Timeout spike detected: service={}, dependency={}, timeoutRate={}%",
                   alert.getServiceName(), alert.getDependencyName(), alert.getTimeoutRate());

        dependencyTimeoutsGauge.incrementAndGet();

        // Alert based on timeout severity
        if (alert.getTimeoutRate() > 50) {
            infrastructureAlertService.sendExecutiveAlert(
                "Severe Timeout Spike",
                String.format("Dependency %s experiencing %d%% timeout rate for service %s",
                             alert.getDependencyName(), alert.getTimeoutRate(), alert.getServiceName()),
                "HIGH"
            );
        } else {
            infrastructureAlertService.sendOperationalAlert(
                "Timeout Spike Detected",
                String.format("Dependency %s timeout rate increased to %d%% for service %s",
                             alert.getDependencyName(), alert.getTimeoutRate(), alert.getServiceName()),
                "MEDIUM"
            );
        }

        dependencyMetricsService.recordTimeoutSpike(alert);
        recoveryService.initiateTimeoutMitigation(alert);
    }

    private void handleConnectionPoolExhausted(DependencyAlert alert) {
        logger.error("Connection pool exhausted: service={}, dependency={}, poolUtilization={}%",
                    alert.getServiceName(), alert.getDependencyName(), alert.getPoolUtilization());

        infrastructureAlertService.sendOperationalAlert(
            "Connection Pool Exhausted",
            String.format("Connection pool exhausted for dependency %s (utilization: %d%%)",
                         alert.getDependencyName(), alert.getPoolUtilization()),
            "HIGH"
        );

        dependencyMetricsService.recordConnectionPoolExhaustion(alert);
        recoveryService.initiateConnectionPoolRecovery(alert);
    }

    private void handleCircuitBreakerOpen(DependencyAlert alert) {
        logger.warn("Circuit breaker opened: service={}, dependency={}, failureRate={}%",
                   alert.getServiceName(), alert.getDependencyName(), alert.getFailureRate());

        infrastructureAlertService.sendOperationalAlert(
            "Circuit Breaker Opened",
            String.format("Circuit breaker opened for dependency %s due to %d%% failure rate",
                         alert.getDependencyName(), alert.getFailureRate()),
            "MEDIUM"
        );

        dependencyMetricsService.recordCircuitBreakerOpen(alert);
    }

    private void handleRetryExhausted(DependencyAlert alert) {
        logger.error("Retry attempts exhausted: service={}, dependency={}, attempts={}",
                    alert.getServiceName(), alert.getDependencyName(), alert.getRetryAttempts());

        infrastructureAlertService.sendOperationalAlert(
            "Retry Attempts Exhausted",
            String.format("All %d retry attempts exhausted for dependency %s",
                         alert.getRetryAttempts(), alert.getDependencyName()),
            "HIGH"
        );

        dependencyMetricsService.recordRetryExhaustion(alert);
        recoveryService.initiateAlternativeRouting(alert);
    }

    private void handleFailoverTriggered(DependencyAlert alert) {
        logger.info("Failover triggered: service={}, primaryDependency={}, failoverDependency={}",
                   alert.getServiceName(), alert.getPrimaryDependency(), alert.getFailoverDependency());

        infrastructureAlertService.sendOperationalAlert(
            "Failover Triggered",
            String.format("Failover from %s to %s triggered for service %s",
                         alert.getPrimaryDependency(), alert.getFailoverDependency(), alert.getServiceName()),
            "MEDIUM"
        );

        dependencyMetricsService.recordFailoverEvent(alert);
    }

    private void handleDependencyRecovered(DependencyAlert alert) {
        logger.info("Dependency recovered: service={}, dependency={}, recoveryTime={}",
                   alert.getServiceName(), alert.getDependencyName(), alert.getRecoveryTime());

        // Update gauges for recovery
        if ("CRITICAL".equals(alert.getCriticality())) {
            criticalDependenciesDownGauge.decrementAndGet();
        }

        if (alert.isCascadingFailure()) {
            cascadingFailuresGauge.decrementAndGet();
        }

        if (alert.isTimeoutRelated()) {
            dependencyTimeoutsGauge.decrementAndGet();
        }

        infrastructureAlertService.sendOperationalAlert(
            "Dependency Recovered",
            String.format("Dependency %s recovered for service %s. Recovery time: %s",
                         alert.getDependencyName(), alert.getServiceName(), alert.getRecoveryTime()),
            "LOW"
        );

        dependencyMetricsService.recordDependencyRecovery(alert);
    }

    private void handleHealthCheckFailed(DependencyAlert alert) {
        logger.warn("Health check failed: service={}, dependency={}, checkType={}",
                   alert.getServiceName(), alert.getDependencyName(), alert.getHealthCheckType());

        infrastructureAlertService.sendOperationalAlert(
            "Dependency Health Check Failed",
            String.format("Health check failed for dependency %s (%s check)",
                         alert.getDependencyName(), alert.getHealthCheckType()),
            "MEDIUM"
        );

        dependencyMetricsService.recordHealthCheckFailure(alert);
    }

    private void handleSLABreach(DependencyAlert alert) {
        logger.error("SLA breach detected: service={}, dependency={}, metric={}, threshold={}",
                    alert.getServiceName(), alert.getDependencyName(), alert.getSlaMetric(), alert.getSlaThreshold());

        // SLA breaches require executive notification
        infrastructureAlertService.sendExecutiveAlert(
            "Dependency SLA Breach",
            String.format("SLA breach for dependency %s: %s exceeded threshold of %s",
                         alert.getDependencyName(), alert.getSlaMetric(), alert.getSlaThreshold()),
            "HIGH"
        );

        // Compliance notification for contractual SLAs
        if (alert.isContractualSLA()) {
            infrastructureAlertService.sendComplianceAlert(
                "Contractual SLA Breach",
                String.format("Contractual SLA breach for dependency %s may require vendor notification",
                             alert.getDependencyName()),
                "HIGH"
            );
        }

        dependencyMetricsService.recordSLABreach(alert);
    }

    private void handleCapacityExceeded(DependencyAlert alert) {
        logger.warn("Dependency capacity exceeded: service={}, dependency={}, utilization={}%",
                   alert.getServiceName(), alert.getDependencyName(), alert.getCapacityUtilization());

        infrastructureAlertService.sendOperationalAlert(
            "Dependency Capacity Exceeded",
            String.format("Dependency %s capacity exceeded (%d%% utilization)",
                         alert.getDependencyName(), alert.getCapacityUtilization()),
            "MEDIUM"
        );

        dependencyMetricsService.recordCapacityExceeded(alert);
        recoveryService.initiateCapacityScaling(alert);
    }

    private void handleUnknownAlertType(DependencyAlert alert) {
        logger.warn("Processing unknown dependency alert type as generic alert: {}", alert.getAlertType());

        infrastructureAlertService.sendOperationalAlert(
            "Unknown Dependency Alert",
            String.format("Unknown dependency alert type %s for service %s",
                         alert.getAlertType(), alert.getServiceName()),
            "LOW"
        );

        dependencyMetricsService.recordGenericDependencyAlert(alert);
    }

    private void updateMetrics(DependencyAlert alert) {
        // Update operational metrics
        meterRegistry.counter("dependency_alerts_by_type", "type", alert.getAlertType()).increment();
        meterRegistry.counter("dependency_alerts_by_service", "service", alert.getServiceName()).increment();
        meterRegistry.counter("dependency_alerts_by_dependency", "dependency", alert.getDependencyName()).increment();

        if (alert.getSeverity() != null) {
            meterRegistry.counter("dependency_alerts_by_severity", "severity", alert.getSeverity()).increment();
        }

        if (alert.getCriticality() != null) {
            meterRegistry.counter("dependency_alerts_by_criticality", "criticality", alert.getCriticality()).increment();
        }

        // Track impact metrics
        if (alert.getImpactScore() > 0) {
            meterRegistry.gauge("dependency_alert_impact_score", alert.getImpactScore());
        }

        // Track recovery metrics
        if (alert.getRecoveryTime() != null) {
            meterRegistry.timer("dependency_recovery_time").record(alert.getRecoveryTime());
        }
    }

    private void validateDependencyAlert(DependencyAlert alert) {
        if (alert.getAlertType() == null || alert.getAlertType().trim().isEmpty()) {
            throw new IllegalArgumentException("Dependency alert type cannot be null or empty");
        }

        if (alert.getServiceName() == null || alert.getServiceName().trim().isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be null or empty");
        }

        if (alert.getDependencyName() == null || alert.getDependencyName().trim().isEmpty()) {
            throw new IllegalArgumentException("Dependency name cannot be null or empty");
        }

        if (alert.getTimestamp() == null) {
            throw new IllegalArgumentException("Alert timestamp cannot be null");
        }
    }

    private boolean isDuplicate(String eventKey) {
        LocalDateTime lastProcessed = processedEvents.get(eventKey);
        return lastProcessed != null &&
               ChronoUnit.HOURS.between(lastProcessed, LocalDateTime.now()) < IDEMPOTENCY_TTL_HOURS;
    }

    private void recordProcessedEvent(String eventKey) {
        processedEvents.put(eventKey, LocalDateTime.now());
    }

    private void cleanupExpiredEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(IDEMPOTENCY_TTL_HOURS);
        processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    @DltHandler
    public void handleDltDependencyAlert(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_KEY) String eventKey,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        logger.error("Dependency alert sent to DLT: key={}, exception={}", eventKey, exceptionMessage);

        eventsFailedCounter.increment();

        // Send critical alert for DLT events
        infrastructureAlertService.sendExecutiveAlert(
            "Dependency Alert Processing Failed - DLT",
            String.format("Dependency alert %s sent to dead letter topic. Exception: %s", eventKey, exceptionMessage),
            "CRITICAL"
        );

        // Attempt manual recovery process
        recoveryService.initiateManualRecovery("DEPENDENCY_ALERT_DLT", eventKey, eventPayload);
    }

    // Circuit breaker fallback method
    public void fallbackProcessDependencyAlert(
            String eventPayload, String eventKey, String topic, int partition, long offset,
            Acknowledgment acknowledgment, Exception ex) {

        logger.error("Circuit breaker activated for dependency alert processing: key={}, error={}",
                    eventKey, ex.getMessage());

        // Send alert about circuit breaker activation
        infrastructureAlertService.sendOperationalAlert(
            "Dependency Alert Consumer Circuit Breaker Activated",
            String.format("Circuit breaker activated for dependency alert %s: %s", eventKey, ex.getMessage()),
            "HIGH"
        );

        // Attempt alternative processing or store for later retry
        recoveryService.storeForRetry("DEPENDENCY_ALERT", eventKey, eventPayload);

        acknowledgment.acknowledge();
    }
}