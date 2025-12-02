package com.waqiti.risk.kafka;

import com.waqiti.common.events.GeographicReviewQueueEvent;
import com.waqiti.common.security.SecureRandomService;
import com.waqiti.risk.domain.GeographicReview;
import com.waqiti.risk.repository.GeographicReviewRepository;
import com.waqiti.risk.service.GeographicRiskService;
import com.waqiti.risk.service.RiskReviewService;
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
public class GeographicReviewQueueConsumer {

    private final GeographicReviewRepository geographicReviewRepository;
    private final GeographicRiskService geographicRiskService;
    private final RiskReviewService riskReviewService;
    private final RiskMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final SecureRandomService secureRandomService;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("geographic_review_queue_processed_total")
            .description("Total number of successfully processed geographic review queue events")
            .register(meterRegistry);
        errorCounter = Counter.builder("geographic_review_queue_errors_total")
            .description("Total number of geographic review queue processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("geographic_review_queue_processing_duration")
            .description("Time taken to process geographic review queue events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"geographic-review-queue"},
        groupId = "geographic-review-queue-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "geographic-review-queue", fallbackMethod = "handleGeographicReviewQueueEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleGeographicReviewQueueEvent(
            @Payload GeographicReviewQueueEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("geo-review-%s-p%d-o%d", event.getEntityId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getEntityId(), event.getReviewType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing geographic review queue: entityId={}, reviewType={}, priority={}, location={}",
                event.getEntityId(), event.getReviewType(), event.getPriority(), event.getLocation());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getReviewType()) {
                case HIGH_RISK_LOCATION_REVIEW:
                    processHighRiskLocationReview(event, correlationId);
                    break;

                case GEOFENCING_VIOLATION_REVIEW:
                    processGeofencingViolationReview(event, correlationId);
                    break;

                case SANCTIONS_COMPLIANCE_REVIEW:
                    processSanctionsComplianceReview(event, correlationId);
                    break;

                case TRAVEL_PATTERN_REVIEW:
                    processTravelPatternReview(event, correlationId);
                    break;

                case RESTRICTED_COUNTRY_REVIEW:
                    processRestrictedCountryReview(event, correlationId);
                    break;

                case UNUSUAL_PATTERN_REVIEW:
                    processUnusualPatternReview(event, correlationId);
                    break;

                case CROSS_BORDER_TRANSACTION_REVIEW:
                    processCrossBorderTransactionReview(event, correlationId);
                    break;

                default:
                    log.warn("Unknown geographic review type: {}", event.getReviewType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logRiskEvent("GEOGRAPHIC_REVIEW_QUEUED", event.getEntityId(),
                Map.of("reviewType", event.getReviewType(), "priority", event.getPriority(),
                    "location", event.getLocation(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process geographic review queue event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("geographic-review-queue-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleGeographicReviewQueueEventFallback(
            GeographicReviewQueueEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("geo-review-fallback-%s-p%d-o%d", event.getEntityId(), partition, offset);

        log.error("Circuit breaker fallback triggered for geographic review queue: entityId={}, error={}",
            event.getEntityId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("geographic-review-queue-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Geographic Review Queue Circuit Breaker Triggered",
                String.format("Geographic review for entity %s failed: %s", event.getEntityId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltGeographicReviewQueueEvent(
            @Payload GeographicReviewQueueEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-geo-review-%s-%d", event.getEntityId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Geographic review queue permanently failed: entityId={}, topic={}, error={}",
            event.getEntityId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logRiskEvent("GEOGRAPHIC_REVIEW_DLT_EVENT", event.getEntityId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "reviewType", event.getReviewType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Geographic Review Queue Dead Letter Event",
                String.format("Geographic review for entity %s sent to DLT: %s", event.getEntityId(), exceptionMessage),
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

    private void processHighRiskLocationReview(GeographicReviewQueueEvent event, String correlationId) {
        GeographicReview review = GeographicReview.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .reviewType(event.getReviewType())
            .priority(event.getPriority())
            .location(event.getLocation())
            .country(event.getCountry())
            .status("PENDING_REVIEW")
            .assignedTo(assignReviewer(event.getPriority()))
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .riskIndicators(event.getRiskIndicators())
            .build();
        geographicReviewRepository.save(review);

        geographicRiskService.initiateLocationRiskReview(event.getEntityId(), event.getLocation());

        // Assign to risk analyst based on priority
        String assignedReviewer = assignReviewer(event.getPriority());
        notificationService.sendNotification(assignedReviewer, "High Risk Location Review Required",
            String.format("Review required for entity %s in location %s", event.getEntityId(), event.getLocation()),
            correlationId);

        metricsService.recordReviewQueued("HIGH_RISK_LOCATION", event.getPriority());

        log.info("High risk location review queued: entityId={}, location={}, assignedTo={}",
            event.getEntityId(), event.getLocation(), assignedReviewer);
    }

    private void processGeofencingViolationReview(GeographicReviewQueueEvent event, String correlationId) {
        GeographicReview review = GeographicReview.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .reviewType(event.getReviewType())
            .priority("URGENT") // Geofencing violations are always urgent
            .location(event.getLocation())
            .country(event.getCountry())
            .status("PENDING_URGENT_REVIEW")
            .assignedTo(assignReviewer("URGENT"))
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .violationDetails(event.getViolationDetails())
            .build();
        geographicReviewRepository.save(review);

        riskReviewService.escalateGeofencingViolation(event.getEntityId(), event.getViolationDetails());

        // Immediate escalation for geofencing violations
        String assignedReviewer = assignReviewer("URGENT");
        notificationService.sendUrgentNotification(assignedReviewer, "Geofencing Violation - Immediate Review Required",
            String.format("URGENT: Geofencing violation for entity %s at %s", event.getEntityId(), event.getLocation()),
            correlationId);

        metricsService.recordReviewQueued("GEOFENCING_VIOLATION", "URGENT");

        log.warn("Geofencing violation review queued: entityId={}, location={}, assignedTo={}",
            event.getEntityId(), event.getLocation(), assignedReviewer);
    }

    private void processSanctionsComplianceReview(GeographicReviewQueueEvent event, String correlationId) {
        GeographicReview review = GeographicReview.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .reviewType(event.getReviewType())
            .priority("CRITICAL") // Sanctions are always critical
            .location(event.getLocation())
            .country(event.getCountry())
            .status("PENDING_CRITICAL_REVIEW")
            .assignedTo(assignComplianceReviewer())
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .sanctionsDetails(event.getSanctionsDetails())
            .build();
        geographicReviewRepository.save(review);

        riskReviewService.initiateSanctionsReview(event.getEntityId(), event.getCountry());

        // Immediate notification to compliance team
        String complianceReviewer = assignComplianceReviewer();
        notificationService.sendCriticalAlert(
            "Sanctions Compliance Review Required",
            String.format("CRITICAL: Sanctions review required for entity %s in country %s",
                event.getEntityId(), event.getCountry()),
            Map.of("entityId", event.getEntityId(), "country", event.getCountry(),
                   "correlationId", correlationId, "assignedTo", complianceReviewer)
        );

        // Also notify legal team
        kafkaTemplate.send("legal-alerts", Map.of(
            "entityId", event.getEntityId(),
            "alertType", "SANCTIONS_REVIEW",
            "country", event.getCountry(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReviewQueued("SANCTIONS_COMPLIANCE", "CRITICAL");

        log.error("Sanctions compliance review queued: entityId={}, country={}, assignedTo={}",
            event.getEntityId(), event.getCountry(), complianceReviewer);
    }

    private void processTravelPatternReview(GeographicReviewQueueEvent event, String correlationId) {
        GeographicReview review = GeographicReview.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .reviewType(event.getReviewType())
            .priority(event.getPriority())
            .location(event.getLocation())
            .country(event.getCountry())
            .status("PENDING_REVIEW")
            .assignedTo(assignReviewer(event.getPriority()))
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .travelPattern(event.getTravelPattern())
            .build();
        geographicReviewRepository.save(review);

        riskReviewService.analyzeTravelPatternAnomaly(event.getEntityId(), event.getTravelPattern());

        String assignedReviewer = assignReviewer(event.getPriority());
        notificationService.sendNotification(assignedReviewer, "Travel Pattern Review Required",
            String.format("Travel pattern anomaly detected for entity %s", event.getEntityId()),
            correlationId);

        metricsService.recordReviewQueued("TRAVEL_PATTERN", event.getPriority());

        log.info("Travel pattern review queued: entityId={}, pattern={}, assignedTo={}",
            event.getEntityId(), event.getTravelPattern(), assignedReviewer);
    }

    private void processRestrictedCountryReview(GeographicReviewQueueEvent event, String correlationId) {
        GeographicReview review = GeographicReview.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .reviewType(event.getReviewType())
            .priority("HIGH") // Restricted countries are high priority
            .location(event.getLocation())
            .country(event.getCountry())
            .status("PENDING_HIGH_PRIORITY_REVIEW")
            .assignedTo(assignReviewer("HIGH"))
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .restrictionDetails(event.getRestrictionDetails())
            .build();
        geographicReviewRepository.save(review);

        riskReviewService.reviewRestrictedCountryActivity(event.getEntityId(), event.getCountry());

        String assignedReviewer = assignReviewer("HIGH");
        notificationService.sendHighPriorityNotification(assignedReviewer, "Restricted Country Activity Review",
            String.format("High priority: Entity %s activity in restricted country %s",
                event.getEntityId(), event.getCountry()),
            correlationId);

        // Also notify compliance team
        kafkaTemplate.send("compliance-review-queue", Map.of(
            "entityId", event.getEntityId(),
            "reviewType", "RESTRICTED_COUNTRY",
            "country", event.getCountry(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReviewQueued("RESTRICTED_COUNTRY", "HIGH");

        log.warn("Restricted country review queued: entityId={}, country={}, assignedTo={}",
            event.getEntityId(), event.getCountry(), assignedReviewer);
    }

    private void processUnusualPatternReview(GeographicReviewQueueEvent event, String correlationId) {
        GeographicReview review = GeographicReview.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .reviewType(event.getReviewType())
            .priority(event.getPriority())
            .location(event.getLocation())
            .country(event.getCountry())
            .status("PENDING_REVIEW")
            .assignedTo(assignReviewer(event.getPriority()))
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .patternDetails(event.getPatternDetails())
            .build();
        geographicReviewRepository.save(review);

        riskReviewService.analyzeUnusualGeographicPattern(event.getEntityId(), event.getPatternDetails());

        String assignedReviewer = assignReviewer(event.getPriority());
        notificationService.sendNotification(assignedReviewer, "Unusual Geographic Pattern Review",
            String.format("Unusual pattern detected for entity %s", event.getEntityId()),
            correlationId);

        metricsService.recordReviewQueued("UNUSUAL_PATTERN", event.getPriority());

        log.info("Unusual pattern review queued: entityId={}, pattern={}, assignedTo={}",
            event.getEntityId(), event.getPatternDetails(), assignedReviewer);
    }

    private void processCrossBorderTransactionReview(GeographicReviewQueueEvent event, String correlationId) {
        GeographicReview review = GeographicReview.builder()
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .reviewType(event.getReviewType())
            .priority(event.getPriority())
            .location(event.getLocation())
            .country(event.getCountry())
            .status("PENDING_REVIEW")
            .assignedTo(assignReviewer(event.getPriority()))
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .transactionDetails(event.getTransactionDetails())
            .build();
        geographicReviewRepository.save(review);

        riskReviewService.reviewCrossBorderTransaction(event.getEntityId(), event.getTransactionDetails());

        String assignedReviewer = assignReviewer(event.getPriority());
        notificationService.sendNotification(assignedReviewer, "Cross-Border Transaction Review",
            String.format("Cross-border transaction review required for entity %s", event.getEntityId()),
            correlationId);

        // Also send to AML team for potential money laundering review
        kafkaTemplate.send("aml-review-queue", Map.of(
            "entityId", event.getEntityId(),
            "reviewType", "CROSS_BORDER_TRANSACTION",
            "transactionDetails", event.getTransactionDetails(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordReviewQueued("CROSS_BORDER_TRANSACTION", event.getPriority());

        log.info("Cross-border transaction review queued: entityId={}, assignedTo={}",
            event.getEntityId(), assignedReviewer);
    }

    private String assignReviewer(String priority) {
        // SECURITY FIX: Using SecureRandomService instead of Math.random()
        // Simple round-robin assignment based on priority
        switch (priority.toUpperCase()) {
            case "CRITICAL":
            case "URGENT":
                return "senior-risk-analyst-" + (secureRandomService.nextInt(1, 4));
            case "HIGH":
                return "risk-analyst-" + (secureRandomService.nextInt(1, 6));
            case "MEDIUM":
                return "junior-risk-analyst-" + (secureRandomService.nextInt(1, 9));
            default:
                return "risk-analyst-" + (secureRandomService.nextInt(1, 11));
        }
    }

    private String assignComplianceReviewer() {
        // SECURITY FIX: Using SecureRandomService instead of Math.random()
        // Assign to compliance team for sanctions-related reviews
        return "compliance-analyst-" + (secureRandomService.nextInt(1, 4));
    }
}