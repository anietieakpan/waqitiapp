package com.waqiti.risk.kafka;

import com.waqiti.common.events.GeographicAlertEvent;
import com.waqiti.risk.domain.GeographicAlert;
import com.waqiti.risk.repository.GeographicAlertRepository;
import com.waqiti.risk.service.GeographicRiskService;
import com.waqiti.risk.service.RiskMetricsService;
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
public class GeographicAlertsConsumer {

    private final GeographicAlertRepository geographicAlertRepository;
    private final GeographicRiskService geographicRiskService;
    private final RiskMetricsService metricsService;
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
        successCounter = Counter.builder("geographic_alerts_processed_total")
            .description("Total number of successfully processed geographic alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("geographic_alerts_errors_total")
            .description("Total number of geographic alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("geographic_alerts_processing_duration")
            .description("Time taken to process geographic alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"geographic-alerts"},
        groupId = "geographic-alerts-service-group",
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
    @CircuitBreaker(name = "geographic-alerts", fallbackMethod = "handleGeographicAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleGeographicAlertEvent(
            @Payload GeographicAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("geo-alert-%s-p%d-o%d", event.getEntityId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getEntityId(), event.getAlertType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing geographic alert: entityId={}, alertType={}, location={}, severity={}",
                event.getEntityId(), event.getAlertType(), event.getLocation(), event.getSeverity());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getAlertType()) {
                case HIGH_RISK_LOCATION:
                    processHighRiskLocationAlert(event, correlationId);
                    break;

                case GEOFENCING_VIOLATION:
                    processGeofencingViolation(event, correlationId);
                    break;

                case UNUSUAL_GEOGRAPHIC_PATTERN:
                    processUnusualGeographicPattern(event, correlationId);
                    break;

                case SANCTIONS_REGION_ACCESS:
                    processSanctionsRegionAccess(event, correlationId);
                    break;

                case TRAVEL_PATTERN_ANOMALY:
                    processTravelPatternAnomaly(event, correlationId);
                    break;

                case RESTRICTED_COUNTRY_ACTIVITY:
                    processRestrictedCountryActivity(event, correlationId);
                    break;

                default:
                    log.warn("Unknown geographic alert type: {}", event.getAlertType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("GEOGRAPHIC_ALERT_PROCESSED", event.getEntityId(),
                Map.of("alertType", event.getAlertType(), "location", event.getLocation(),
                    "severity", event.getSeverity(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process geographic alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("geographic-alerts-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleGeographicAlertEventFallback(
            GeographicAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("geo-alert-fallback-%s-p%d-o%d", event.getEntityId(), partition, offset);

        log.error("Circuit breaker fallback triggered for geographic alert: entityId={}, error={}",
            event.getEntityId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("geographic-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Geographic Alert Circuit Breaker Triggered",
                String.format("Geographic alert for entity %s failed: %s", event.getEntityId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltGeographicAlertEvent(
            @Payload GeographicAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-geo-alert-%s-%d", event.getEntityId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Geographic alert permanently failed: entityId={}, topic={}, error={}",
            event.getEntityId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logRiskEvent("GEOGRAPHIC_ALERT_DLT_EVENT", event.getEntityId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Geographic Alert Dead Letter Event",
                String.format("Geographic alert for entity %s sent to DLT: %s", event.getEntityId(), exceptionMessage),
                Map.of("entityId", event.getEntityId(), "topic", topic, "correlationId", correlationId)
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

    private void processHighRiskLocationAlert(GeographicAlertEvent event, String correlationId) {
        GeographicAlert alert = GeographicAlert.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .alertType(event.getAlertType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .status("ACTIVE")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        geographicAlertRepository.save(alert);

        geographicRiskService.assessLocationRisk(event.getLocation());
        geographicRiskService.updateEntityRiskProfile(event.getEntityId(), event.getRiskScore());

        // Send notification for high-risk location activity
        notificationService.sendNotification(event.getEntityId(), "High Risk Location Alert",
            String.format("Activity detected in high-risk location: %s", event.getLocation()),
            correlationId);

        kafkaTemplate.send("geographic-review-queue", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "HIGH_RISK_LOCATION_REVIEW",
            "location", event.getLocation(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordGeographicAlert("HIGH_RISK_LOCATION", event.getSeverity());

        log.info("High risk location alert processed: entityId={}, location={}",
            event.getEntityId(), event.getLocation());
    }

    private void processGeofencingViolation(GeographicAlertEvent event, String correlationId) {
        GeographicAlert alert = GeographicAlert.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .alertType(event.getAlertType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .status("ACTIVE")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .violationDetails(event.getViolationDetails())
            .build();
        geographicAlertRepository.save(alert);

        geographicRiskService.processGeofenceViolation(event.getEntityId(), event.getLocation());

        // Send immediate notification for geofencing violations
        notificationService.sendCriticalAlert(
            "Geofencing Violation Detected",
            String.format("Entity %s violated geographic boundaries at %s", event.getEntityId(), event.getLocation()),
            Map.of("entityId", event.getEntityId(), "location", event.getLocation(), "correlationId", correlationId)
        );

        metricsService.recordGeographicAlert("GEOFENCING_VIOLATION", event.getSeverity());

        log.warn("Geofencing violation processed: entityId={}, location={}",
            event.getEntityId(), event.getLocation());
    }

    private void processUnusualGeographicPattern(GeographicAlertEvent event, String correlationId) {
        GeographicAlert alert = GeographicAlert.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .alertType(event.getAlertType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .status("UNDER_REVIEW")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .patternDetails(event.getPatternDetails())
            .build();
        geographicAlertRepository.save(alert);

        geographicRiskService.analyzeGeographicPattern(event.getEntityId(), event.getPatternDetails());

        kafkaTemplate.send("geographic-review-queue", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "UNUSUAL_PATTERN_REVIEW",
            "patternDetails", event.getPatternDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordGeographicAlert("UNUSUAL_PATTERN", event.getSeverity());

        log.info("Unusual geographic pattern alert processed: entityId={}, pattern={}",
            event.getEntityId(), event.getPatternDetails());
    }

    private void processSanctionsRegionAccess(GeographicAlertEvent event, String correlationId) {
        GeographicAlert alert = GeographicAlert.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .alertType(event.getAlertType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .severity("CRITICAL")
            .riskScore(100) // Maximum risk score for sanctions violations
            .status("BLOCKED")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .sanctionsDetails(event.getSanctionsDetails())
            .build();
        geographicAlertRepository.save(alert);

        geographicRiskService.processSanctionsViolation(event.getEntityId(), event.getCountry());

        // Immediate critical alert for sanctions violations
        notificationService.sendCriticalAlert(
            "Sanctions Region Access Detected",
            String.format("CRITICAL: Entity %s accessed sanctioned region %s", event.getEntityId(), event.getCountry()),
            Map.of("entityId", event.getEntityId(), "country", event.getCountry(),
                   "correlationId", correlationId, "severity", "CRITICAL")
        );

        // Send to compliance team immediately
        kafkaTemplate.send("compliance-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "SANCTIONS_VIOLATION",
            "country", event.getCountry(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordGeographicAlert("SANCTIONS_VIOLATION", "CRITICAL");

        log.error("Sanctions region access alert processed: entityId={}, country={}",
            event.getEntityId(), event.getCountry());
    }

    private void processTravelPatternAnomaly(GeographicAlertEvent event, String correlationId) {
        GeographicAlert alert = GeographicAlert.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .alertType(event.getAlertType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .status("UNDER_REVIEW")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .travelPattern(event.getTravelPattern())
            .build();
        geographicAlertRepository.save(alert);

        geographicRiskService.analyzeTravelPattern(event.getEntityId(), event.getTravelPattern());

        kafkaTemplate.send("behavioral-analysis-events", Map.of(
            "entityId", event.getEntityId(),
            "analysisType", "TRAVEL_PATTERN",
            "travelPattern", event.getTravelPattern(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordGeographicAlert("TRAVEL_ANOMALY", event.getSeverity());

        log.info("Travel pattern anomaly alert processed: entityId={}, pattern={}",
            event.getEntityId(), event.getTravelPattern());
    }

    private void processRestrictedCountryActivity(GeographicAlertEvent event, String correlationId) {
        GeographicAlert alert = GeographicAlert.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .alertType(event.getAlertType())
            .location(event.getLocation())
            .country(event.getCountry())
            .region(event.getRegion())
            .severity("HIGH")
            .riskScore(event.getRiskScore())
            .status("UNDER_REVIEW")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .restrictionDetails(event.getRestrictionDetails())
            .build();
        geographicAlertRepository.save(alert);

        geographicRiskService.processRestrictedCountryActivity(event.getEntityId(), event.getCountry());

        // Send to compliance for review
        kafkaTemplate.send("compliance-review-queue", Map.of(
            "entityId", event.getEntityId(),
            "reviewType", "RESTRICTED_COUNTRY_ACTIVITY",
            "country", event.getCountry(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert(
            "Restricted Country Activity",
            String.format("Entity %s detected in restricted country %s", event.getEntityId(), event.getCountry()),
            "HIGH"
        );

        metricsService.recordGeographicAlert("RESTRICTED_COUNTRY", "HIGH");

        log.warn("Restricted country activity alert processed: entityId={}, country={}",
            event.getEntityId(), event.getCountry());
    }
}