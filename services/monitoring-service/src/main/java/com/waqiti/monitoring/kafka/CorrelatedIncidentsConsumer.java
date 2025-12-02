package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.CorrelatedIncidentEvent;
import com.waqiti.monitoring.service.IncidentCorrelationService;
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
public class CorrelatedIncidentsConsumer {

    private final IncidentCorrelationService correlationService;
    private final AlertingService alertingService;
    private final InfrastructureMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
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
        successCounter = Counter.builder("correlated_incidents_processed_total")
            .description("Total number of successfully processed correlated incident events")
            .register(meterRegistry);
        errorCounter = Counter.builder("correlated_incidents_errors_total")
            .description("Total number of correlated incident processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("correlated_incidents_processing_duration")
            .description("Time taken to process correlated incident events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"correlated-incidents", "incident-correlation", "related-incidents"},
        groupId = "incident-correlation-group",
        containerFactory = "criticalIncidentKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "correlated-incidents", fallbackMethod = "handleCorrelatedIncidentEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCorrelatedIncidentEvent(
            @Payload CorrelatedIncidentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("incident-corr-%s-p%d-o%d", event.getCorrelationGroupId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getCorrelationGroupId(), event.getCorrelationType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing correlated incident: groupId={}, type={}, incidentCount={}, severity={}",
                event.getCorrelationGroupId(), event.getCorrelationType(), event.getIncidentCount(), event.getSeverity());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getCorrelationType()) {
                case SERVICE_CASCADE_FAILURE:
                    handleServiceCascadeFailure(event, correlationId);
                    break;

                case INFRASTRUCTURE_DEPENDENCY:
                    handleInfrastructureDependency(event, correlationId);
                    break;

                case TEMPORAL_CORRELATION:
                    handleTemporalCorrelation(event, correlationId);
                    break;

                case GEOGRAPHIC_CORRELATION:
                    handleGeographicCorrelation(event, correlationId);
                    break;

                case ROOT_CAUSE_IDENTIFIED:
                    handleRootCauseIdentified(event, correlationId);
                    break;

                case INCIDENT_STORM:
                    handleIncidentStorm(event, correlationId);
                    break;

                case CROSS_TEAM_IMPACT:
                    handleCrossTeamImpact(event, correlationId);
                    break;

                case CUSTOMER_IMPACT_CORRELATION:
                    handleCustomerImpactCorrelation(event, correlationId);
                    break;

                case CORRELATION_RESOLVED:
                    handleCorrelationResolved(event, correlationId);
                    break;

                default:
                    log.warn("Unknown correlation type: {}", event.getCorrelationType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logIncidentEvent("CORRELATED_INCIDENT_PROCESSED", event.getCorrelationGroupId(),
                Map.of("correlationType", event.getCorrelationType(), "incidentCount", event.getIncidentCount(),
                    "severity", event.getSeverity(), "affectedServices", event.getAffectedServices(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process correlated incident event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("correlated-incidents-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCorrelatedIncidentEventFallback(
            CorrelatedIncidentEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("incident-corr-fallback-%s-p%d-o%d", event.getCorrelationGroupId(), partition, offset);

        log.error("Circuit breaker fallback triggered for correlated incident: groupId={}, error={}",
            event.getCorrelationGroupId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("correlated-incidents-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Correlated Incident Circuit Breaker Triggered",
                String.format("Correlated incident processing failed for group %s: %s", event.getCorrelationGroupId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCorrelatedIncidentEvent(
            @Payload CorrelatedIncidentEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-incident-corr-%s-%d", event.getCorrelationGroupId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Correlated incident permanently failed: groupId={}, topic={}, error={}",
            event.getCorrelationGroupId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logIncidentEvent("CORRELATED_INCIDENT_DLT_EVENT", event.getCorrelationGroupId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "correlationType", event.getCorrelationType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Correlated Incident Dead Letter Event",
                String.format("Correlated incident for group %s sent to DLT: %s", event.getCorrelationGroupId(), exceptionMessage),
                Map.of("correlationGroupId", event.getCorrelationGroupId(), "topic", topic, "correlationId", correlationId)
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

    private void handleServiceCascadeFailure(CorrelatedIncidentEvent event, String correlationId) {
        log.error("Service cascade failure detected: groupId={}, affectedServices={}, severity={}",
            event.getCorrelationGroupId(), event.getAffectedServices(), event.getSeverity());

        correlationService.handleCascadeFailure(event.getCorrelationGroupId(), event.getAffectedServices());

        alertingService.sendCriticalAlert(
            "Service Cascade Failure Detected",
            String.format("Cascade failure affecting %d services (group: %s)",
                event.getAffectedServices().size(), event.getCorrelationGroupId()),
            correlationId
        );

        // Trigger circuit breakers for dependent services
        correlationService.triggerCascadeProtection(event.getAffectedServices());

        metricsService.recordCascadeFailure(event.getCorrelationGroupId(), event.getAffectedServices().size());

        // Send to disaster recovery
        kafkaTemplate.send("disaster-recovery-events", Map.of(
            "eventType", "CASCADE_FAILURE",
            "correlationGroupId", event.getCorrelationGroupId(),
            "affectedServices", event.getAffectedServices(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleInfrastructureDependency(CorrelatedIncidentEvent event, String correlationId) {
        log.warn("Infrastructure dependency correlation: groupId={}, rootService={}, dependentServices={}",
            event.getCorrelationGroupId(), event.getRootService(), event.getDependentServices());

        correlationService.analyzeInfrastructureDependency(
            event.getCorrelationGroupId(), event.getRootService(), event.getDependentServices());

        alertingService.sendHighPriorityAlert(
            "Infrastructure Dependency Issue",
            String.format("Infrastructure issue in %s affecting %d dependent services",
                event.getRootService(), event.getDependentServices().size()),
            correlationId
        );

        // Map dependency chains for better understanding
        correlationService.mapDependencyChain(event.getRootService(), event.getDependentServices());

        metricsService.recordDependencyCorrelation(event.getRootService(), event.getDependentServices().size());
    }

    private void handleTemporalCorrelation(CorrelatedIncidentEvent event, String correlationId) {
        log.info("Temporal correlation detected: groupId={}, timeWindow={}, incidentCount={}",
            event.getCorrelationGroupId(), event.getTimeWindowMinutes(), event.getIncidentCount());

        correlationService.analyzeTemporalPattern(
            event.getCorrelationGroupId(), event.getTimeWindowMinutes(), event.getIncidentCount());

        if (event.getIncidentCount() > 10) { // High frequency
            alertingService.sendMediumPriorityAlert(
                "High Frequency Incident Pattern",
                String.format("%d incidents in %d minutes (group: %s)",
                    event.getIncidentCount(), event.getTimeWindowMinutes(), event.getCorrelationGroupId()),
                correlationId
            );
        }

        // Look for patterns that might indicate systemic issues
        correlationService.detectSystemicPatterns(event.getCorrelationGroupId(), event.getTimeWindowMinutes());

        metricsService.recordTemporalCorrelation(event.getCorrelationGroupId(), event.getIncidentCount());
    }

    private void handleGeographicCorrelation(CorrelatedIncidentEvent event, String correlationId) {
        log.info("Geographic correlation detected: groupId={}, regions={}, incidentCount={}",
            event.getCorrelationGroupId(), event.getAffectedRegions(), event.getIncidentCount());

        correlationService.analyzeGeographicPattern(
            event.getCorrelationGroupId(), event.getAffectedRegions(), event.getIncidentCount());

        alertingService.sendMediumPriorityAlert(
            "Geographic Incident Correlation",
            String.format("Incidents correlated across regions: %s (group: %s)",
                String.join(", ", event.getAffectedRegions()), event.getCorrelationGroupId()),
            correlationId
        );

        // Check for region-specific infrastructure issues
        correlationService.analyzeRegionalInfrastructure(event.getAffectedRegions());

        metricsService.recordGeographicCorrelation(event.getCorrelationGroupId(), event.getAffectedRegions().size());
    }

    private void handleRootCauseIdentified(CorrelatedIncidentEvent event, String correlationId) {
        log.info("Root cause identified: groupId={}, rootCause={}, affectedIncidents={}",
            event.getCorrelationGroupId(), event.getRootCause(), event.getAffectedIncidents());

        correlationService.handleRootCauseIdentification(
            event.getCorrelationGroupId(), event.getRootCause(), event.getAffectedIncidents());

        alertingService.sendInfoAlert(
            "Root Cause Identified",
            String.format("Root cause found for correlation group %s: %s",
                event.getCorrelationGroupId(), event.getRootCause()),
            correlationId
        );

        // Update all related incidents with root cause
        correlationService.updateIncidentsWithRootCause(event.getAffectedIncidents(), event.getRootCause());

        metricsService.recordRootCauseIdentified(event.getCorrelationGroupId());

        // Send to knowledge base for future correlation
        kafkaTemplate.send("incident-knowledge-base", Map.of(
            "correlationGroupId", event.getCorrelationGroupId(),
            "rootCause", event.getRootCause(),
            "affectedIncidents", event.getAffectedIncidents(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleIncidentStorm(CorrelatedIncidentEvent event, String correlationId) {
        log.error("Incident storm detected: groupId={}, incidentRate={}/hour, severity={}",
            event.getCorrelationGroupId(), event.getIncidentRatePerHour(), event.getSeverity());

        correlationService.handleIncidentStorm(event.getCorrelationGroupId(), event.getIncidentRatePerHour());

        alertingService.sendCriticalAlert(
            "Incident Storm Detected",
            String.format("High rate of incidents detected: %d/hour (group: %s)",
                event.getIncidentRatePerHour(), event.getCorrelationGroupId()),
            correlationId
        );

        // Trigger emergency response procedures
        correlationService.triggerEmergencyResponse(event.getCorrelationGroupId());

        metricsService.recordIncidentStorm(event.getCorrelationGroupId(), event.getIncidentRatePerHour());

        // Escalate to on-call team
        notificationService.escalateToOnCall(
            "Incident Storm",
            String.format("Critical incident storm in progress: %d incidents/hour", event.getIncidentRatePerHour()),
            Map.of("correlationGroupId", event.getCorrelationGroupId(), "correlationId", correlationId)
        );
    }

    private void handleCrossTeamImpact(CorrelatedIncidentEvent event, String correlationId) {
        log.warn("Cross-team impact detected: groupId={}, affectedTeams={}, impactLevel={}",
            event.getCorrelationGroupId(), event.getAffectedTeams(), event.getImpactLevel());

        correlationService.handleCrossTeamImpact(
            event.getCorrelationGroupId(), event.getAffectedTeams(), event.getImpactLevel());

        alertingService.sendHighPriorityAlert(
            "Cross-Team Impact Detected",
            String.format("Incident affecting multiple teams: %s (impact: %s)",
                String.join(", ", event.getAffectedTeams()), event.getImpactLevel()),
            correlationId
        );

        // Coordinate response across teams
        correlationService.coordinateTeamResponse(event.getAffectedTeams(), event.getCorrelationGroupId());

        metricsService.recordCrossTeamImpact(event.getCorrelationGroupId(), event.getAffectedTeams().size());
    }

    private void handleCustomerImpactCorrelation(CorrelatedIncidentEvent event, String correlationId) {
        log.error("Customer impact correlation: groupId={}, affectedCustomers={}, impactSeverity={}",
            event.getCorrelationGroupId(), event.getAffectedCustomers(), event.getCustomerImpactSeverity());

        correlationService.handleCustomerImpactCorrelation(
            event.getCorrelationGroupId(), event.getAffectedCustomers(), event.getCustomerImpactSeverity());

        alertingService.sendCriticalAlert(
            "Customer Impact Correlation",
            String.format("Correlated incidents affecting %d customers (severity: %s)",
                event.getAffectedCustomers(), event.getCustomerImpactSeverity()),
            correlationId
        );

        // Trigger customer communication protocols
        correlationService.triggerCustomerCommunication(event.getCorrelationGroupId(), event.getAffectedCustomers());

        metricsService.recordCustomerImpactCorrelation(event.getCorrelationGroupId(), event.getAffectedCustomers());

        // Send to customer success team
        kafkaTemplate.send("customer-success-events", Map.of(
            "eventType", "CORRELATED_CUSTOMER_IMPACT",
            "correlationGroupId", event.getCorrelationGroupId(),
            "affectedCustomers", event.getAffectedCustomers(),
            "severity", event.getCustomerImpactSeverity(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleCorrelationResolved(CorrelatedIncidentEvent event, String correlationId) {
        log.info("Correlation resolved: groupId={}, resolution={}, resolvedIncidents={}",
            event.getCorrelationGroupId(), event.getResolutionSummary(), event.getResolvedIncidents());

        correlationService.handleCorrelationResolved(
            event.getCorrelationGroupId(), event.getResolutionSummary(), event.getResolvedIncidents());

        alertingService.sendInfoAlert(
            "Incident Correlation Resolved",
            String.format("Correlation group %s resolved: %s",
                event.getCorrelationGroupId(), event.getResolutionSummary()),
            correlationId
        );

        // Update metrics and close correlation group
        metricsService.recordCorrelationResolved(event.getCorrelationGroupId(), event.getResolvedIncidents().size());

        // Archive correlation data for analysis
        correlationService.archiveCorrelationData(event.getCorrelationGroupId());
    }
}