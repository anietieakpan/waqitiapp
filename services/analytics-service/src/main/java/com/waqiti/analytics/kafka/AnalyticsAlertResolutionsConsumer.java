package com.waqiti.analytics.kafka;

import com.waqiti.common.events.AmlAlertEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.analytics.domain.AlertResolution;
import com.waqiti.analytics.repository.AlertResolutionRepository;
import com.waqiti.analytics.service.AlertAnalyticsService;
import com.waqiti.analytics.service.PatternRecognitionService;
import com.waqiti.analytics.metrics.AnalyticsMetricsService;
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
public class AnalyticsAlertResolutionsConsumer {

    private final AlertResolutionRepository alertResolutionRepository;
    private final AlertAnalyticsService alertAnalyticsService;
    private final PatternRecognitionService patternRecognitionService;
    private final AnalyticsMetricsService metricsService;
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
        successCounter = Counter.builder("analytics_alert_resolutions_processed_total")
            .description("Total number of successfully processed alert resolution events")
            .register(meterRegistry);
        errorCounter = Counter.builder("analytics_alert_resolutions_errors_total")
            .description("Total number of alert resolution processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("analytics_alert_resolutions_processing_duration")
            .description("Time taken to process alert resolution events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"analytics-alert-resolutions", "alert-resolution-events", "compliance-alert-resolutions"},
        groupId = "analytics-alert-resolutions-service-group",
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
    @CircuitBreaker(name = "analytics-alert-resolutions", fallbackMethod = "handleAlertResolutionEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAlertResolutionEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String alertId = String.valueOf(event.get("alertId"));
        String correlationId = String.format("alert-resolution-%s-p%d-o%d", alertId, partition, offset);
        String eventKey = String.format("%s-%s-%s", alertId, event.get("eventType"), event.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing alert resolution: alertId={}, status={}, resolutionType={}",
                alertId, event.get("status"), event.get("resolutionType"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String eventType = String.valueOf(event.get("eventType"));
            switch (eventType) {
                case "ALERT_RESOLVED":
                    processAlertResolution(event, correlationId);
                    break;

                case "ALERT_ESCALATED":
                    processAlertEscalation(event, correlationId);
                    break;

                case "ALERT_DISMISSED":
                    processAlertDismissal(event, correlationId);
                    break;

                case "ALERT_REOPENED":
                    processAlertReopening(event, correlationId);
                    break;

                case "RESOLUTION_REVIEWED":
                    processResolutionReview(event, correlationId);
                    break;

                case "FALSE_POSITIVE_MARKED":
                    processFalsePositiveMarking(event, correlationId);
                    break;

                default:
                    log.warn("Unknown alert resolution event type: {}", eventType);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("ANALYTICS_ALERT_RESOLUTION_PROCESSED", alertId,
                Map.of("eventType", eventType, "status", event.get("status"),
                    "resolutionType", event.get("resolutionType"), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process alert resolution event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("analytics-alert-resolutions-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAlertResolutionEventFallback(
            Map<String, Object> event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String alertId = String.valueOf(event.get("alertId"));
        String correlationId = String.format("alert-resolution-fallback-%s-p%d-o%d", alertId, partition, offset);

        log.error("Circuit breaker fallback triggered for alert resolution: alertId={}, error={}",
            alertId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("analytics-alert-resolutions-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Alert Resolution Analytics Circuit Breaker Triggered",
                String.format("Alert %s resolution analytics failed: %s", alertId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAlertResolutionEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String alertId = String.valueOf(event.get("alertId"));
        String correlationId = String.format("dlt-alert-resolution-%s-%d", alertId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Alert resolution permanently failed: alertId={}, topic={}, error={}",
            alertId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("ANALYTICS_ALERT_RESOLUTION_DLT_EVENT", alertId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.get("eventType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Alert Resolution Analytics Dead Letter Event",
                String.format("Alert %s resolution analytics sent to DLT: %s", alertId, exceptionMessage),
                Map.of("alertId", alertId, "topic", topic, "correlationId", correlationId)
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

    private void processAlertResolution(Map<String, Object> event, String correlationId) {
        String alertId = String.valueOf(event.get("alertId"));
        String resolutionType = String.valueOf(event.get("resolutionType"));
        String resolutionReason = String.valueOf(event.get("resolutionReason"));
        Integer resolutionTimeMinutes = (Integer) event.get("resolutionTimeMinutes");

        AlertResolution resolution = AlertResolution.builder()
            .id(UUID.randomUUID().toString())
            .alertId(alertId)
            .resolutionType(resolutionType)
            .resolutionReason(resolutionReason)
            .resolutionTimeMinutes(resolutionTimeMinutes)
            .resolvedBy(String.valueOf(event.get("resolvedBy")))
            .resolvedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        alertResolutionRepository.save(resolution);

        // Analyze resolution patterns
        alertAnalyticsService.analyzeResolutionPatterns(alertId, resolutionType, resolutionTimeMinutes);
        patternRecognitionService.updateResolutionPatterns(resolutionType, resolutionReason);

        // Update metrics
        metricsService.recordAlertResolution(resolutionType, resolutionTimeMinutes);

        // Generate insights for future alert handling
        kafkaTemplate.send("analytics-insights", Map.of(
            "insightType", "ALERT_RESOLUTION_PATTERN",
            "alertId", alertId,
            "resolutionType", resolutionType,
            "resolutionEfficiency", calculateResolutionEfficiency(resolutionTimeMinutes),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Alert resolution processed: alertId={}, type={}, time={}min",
            alertId, resolutionType, resolutionTimeMinutes);
    }

    private void processAlertEscalation(Map<String, Object> event, String correlationId) {
        String alertId = String.valueOf(event.get("alertId"));
        String escalationReason = String.valueOf(event.get("escalationReason"));
        String escalatedTo = String.valueOf(event.get("escalatedTo"));

        alertAnalyticsService.recordEscalation(alertId, escalationReason, escalatedTo);
        patternRecognitionService.analyzeEscalationTriggers(alertId, escalationReason);

        metricsService.recordAlertEscalation(escalationReason);

        log.info("Alert escalation processed: alertId={}, reason={}, escalatedTo={}",
            alertId, escalationReason, escalatedTo);
    }

    private void processAlertDismissal(Map<String, Object> event, String correlationId) {
        String alertId = String.valueOf(event.get("alertId"));
        String dismissalReason = String.valueOf(event.get("dismissalReason"));

        alertAnalyticsService.recordDismissal(alertId, dismissalReason);

        // Track false positive patterns
        if ("FALSE_POSITIVE".equals(dismissalReason)) {
            patternRecognitionService.updateFalsePositivePatterns(alertId);
        }

        metricsService.recordAlertDismissal(dismissalReason);

        log.info("Alert dismissal processed: alertId={}, reason={}", alertId, dismissalReason);
    }

    private void processAlertReopening(Map<String, Object> event, String correlationId) {
        String alertId = String.valueOf(event.get("alertId"));
        String reopenReason = String.valueOf(event.get("reopenReason"));

        alertAnalyticsService.recordReopening(alertId, reopenReason);
        patternRecognitionService.analyzeReopenPatterns(alertId, reopenReason);

        metricsService.recordAlertReopening(reopenReason);

        log.info("Alert reopening processed: alertId={}, reason={}", alertId, reopenReason);
    }

    private void processResolutionReview(Map<String, Object> event, String correlationId) {
        String alertId = String.valueOf(event.get("alertId"));
        String reviewResult = String.valueOf(event.get("reviewResult"));
        String reviewerFeedback = String.valueOf(event.get("reviewerFeedback"));

        alertAnalyticsService.recordResolutionReview(alertId, reviewResult, reviewerFeedback);

        metricsService.recordResolutionReview(reviewResult);

        log.info("Resolution review processed: alertId={}, result={}", alertId, reviewResult);
    }

    private void processFalsePositiveMarking(Map<String, Object> event, String correlationId) {
        String alertId = String.valueOf(event.get("alertId"));
        String falsePositiveReason = String.valueOf(event.get("falsePositiveReason"));

        alertAnalyticsService.recordFalsePositive(alertId, falsePositiveReason);
        patternRecognitionService.learnFromFalsePositive(alertId, falsePositiveReason);

        // Update ML models with false positive feedback
        kafkaTemplate.send("ml-feedback-events", Map.of(
            "feedbackType", "FALSE_POSITIVE",
            "alertId", alertId,
            "reason", falsePositiveReason,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordFalsePositive(falsePositiveReason);

        log.info("False positive marked: alertId={}, reason={}", alertId, falsePositiveReason);
    }

    private double calculateResolutionEfficiency(Integer resolutionTimeMinutes) {
        if (resolutionTimeMinutes == null || resolutionTimeMinutes <= 0) {
            return 0.0;
        }

        // Define efficiency based on resolution time thresholds
        if (resolutionTimeMinutes <= 15) return 1.0;      // Excellent
        if (resolutionTimeMinutes <= 60) return 0.8;      // Good
        if (resolutionTimeMinutes <= 240) return 0.6;     // Average
        if (resolutionTimeMinutes <= 1440) return 0.4;    // Poor
        return 0.2; // Very poor
    }
}