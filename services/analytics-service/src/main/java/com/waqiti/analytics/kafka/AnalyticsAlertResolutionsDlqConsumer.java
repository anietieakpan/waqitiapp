package com.waqiti.analytics.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.RecoverableException;
import com.waqiti.analytics.service.AnalyticsService;
import com.waqiti.analytics.service.AlertResolutionService;
import com.waqiti.analytics.service.AlertService;
import com.waqiti.analytics.service.NotificationService;
import com.waqiti.analytics.service.AnalyticsDashboardService;
import com.waqiti.analytics.service.ManualReviewQueueService;
import com.waqiti.analytics.service.EscalationService;
import com.waqiti.analytics.service.MetricsService;
import com.waqiti.analytics.dto.AlertResolutionRecoveryResult;
import com.waqiti.analytics.domain.AlertStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.kafka.retrytopic.DltStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsAlertResolutionsDlqConsumer extends BaseDlqConsumer {

    private final AnalyticsService analyticsService;
    private final AlertResolutionService alertResolutionService;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final AnalyticsDashboardService analyticsDashboardService;
    private final ManualReviewQueueService manualReviewQueue;
    private final EscalationService escalationService;
    private final MetricsService metricsService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public AnalyticsAlertResolutionsDlqConsumer(AnalyticsService analyticsService,
                                                AlertResolutionService alertResolutionService,
                                                AlertService alertService,
                                                NotificationService notificationService,
                                                AnalyticsDashboardService analyticsDashboardService,
                                                ManualReviewQueueService manualReviewQueue,
                                                EscalationService escalationService,
                                                MetricsService metricsService,
                                                MeterRegistry meterRegistry) {
        super("analytics-alert-resolutions-dlq");
        this.analyticsService = analyticsService;
        this.alertResolutionService = alertResolutionService;
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.analyticsDashboardService = analyticsDashboardService;
        this.manualReviewQueue = manualReviewQueue;
        this.escalationService = escalationService;
        this.metricsService = metricsService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("analytics_alert_resolutions_dlq_processed_total")
                .description("Total analytics alert resolutions DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("analytics_alert_resolutions_dlq_errors_total")
                .description("Total analytics alert resolutions DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("analytics_alert_resolutions_dlq_duration")
                .description("Analytics alert resolutions DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "analytics-alert-resolutions-dlq",
        groupId = "analytics-service-alert-resolutions-dlq-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.max-poll-interval-ms=600000",
            "spring.kafka.consumer.session-timeout-ms=30000"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "analytics-alert-resolutions-dlq", fallbackMethod = "handleAlertResolutionDlqFallback")
    public void handleAnalyticsAlertResolutionsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Alert-Type", required = false) String alertType,
            @Header(value = "X-Alert-Severity", required = false) String alertSeverity,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Analytics alert resolution DLQ event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing analytics alert resolution DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, alertType={}, alertSeverity={}",
                     topic, partition, offset, record.key(), correlationId, alertType, alertSeverity);

            String resolutionData = record.value();
            validateResolutionData(resolutionData, eventId);

            // Process alert resolution DLQ with recovery strategy
            AlertResolutionRecoveryResult result = alertResolutionService.processAlertResolutionDlq(
                resolutionData,
                record.key(),
                correlationId,
                alertType,
                alertSeverity,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result based on resolution status
            if (result.isResolved()) {
                handleSuccessfulResolution(result, correlationId);
            } else if (result.isEscalated()) {
                handleEscalatedResolution(result, correlationId);
            } else {
                handleFailedResolution(result, eventId, correlationId);
            }

            // Update analytics metrics
            updateAnalyticsMetrics(result, correlationId);

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed analytics alert resolution DLQ: eventId={}, alertId={}, " +
                    "correlationId={}, resolutionStatus={}",
                    eventId, result.getAlertId(), correlationId, result.getResolutionStatus());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in alert resolution DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in alert resolution DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in alert resolution DLQ: eventId={}, correlationId={}",
                     eventId, correlationId, e);
            handleCriticalFailure(record, e, correlationId);
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.ORIGINAL_OFFSET) long originalOffset,
            @Header(KafkaHeaders.ORIGINAL_PARTITION) int originalPartition) {

        String correlationId = generateCorrelationId();
        log.error("Analytics alert resolution sent to DLT: topic={}, originalPartition={}, " +
                 "originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        // Store for manual intervention
        storeInPermanentFailureStorage(record, topic, exceptionMessage, correlationId);

        // Send operational alert
        sendOperationalAlert(record, topic, exceptionMessage, correlationId);

        // Update DLT metrics
        Counter.builder("analytics_alert_resolutions_dlt_events_total")
                .description("Total alert resolution events sent to DLT")
                .tag("topic", topic)
                .register(meterRegistry)
                .increment();
    }

    public void handleAlertResolutionDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String alertType, String alertSeverity,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for alert resolution DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store for later processing
        storeForLaterProcessing(record, correlationId);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("analytics_alert_resolutions_dlq_circuit_breaker_activations_total")
                .register(meterRegistry)
                .increment();
    }

    private void validateResolutionData(String resolutionData, String eventId) {
        if (resolutionData == null || resolutionData.trim().isEmpty()) {
            throw new ValidationException("Resolution data is null or empty for eventId: " + eventId);
        }

        if (!resolutionData.contains("alertId")) {
            throw new ValidationException("Resolution data missing alertId for eventId: " + eventId);
        }

        if (!resolutionData.contains("resolutionType")) {
            throw new ValidationException("Resolution data missing resolutionType for eventId: " + eventId);
        }
    }

    private void handleSuccessfulResolution(AlertResolutionRecoveryResult result, String correlationId) {
        log.info("Alert successfully resolved: alertId={}, resolutionMethod={}, correlationId={}",
                result.getAlertId(), result.getResolutionMethod(), correlationId);

        // Update alert status
        alertService.updateAlertStatus(
            result.getAlertId(),
            AlertStatus.RESOLVED,
            result.getResolutionDetails(),
            correlationId
        );

        // Send resolution notification
        notificationService.sendResolutionNotification(
            result.getAlertId(),
            result.getResolutionMethod(),
            result.getResolvedBy(),
            correlationId
        );

        // Update analytics dashboard
        analyticsDashboardService.updateResolutionMetrics(
            result.getAlertType(),
            result.getResolutionTime(),
            correlationId
        );
    }

    private void handleEscalatedResolution(AlertResolutionRecoveryResult result, String correlationId) {
        log.warn("Alert escalated: alertId={}, escalationLevel={}, correlationId={}",
                result.getAlertId(), result.getEscalationLevel(), correlationId);

        // Create escalation ticket
        escalationService.createEscalationTicket(
            result.getAlertId(),
            result.getEscalationLevel(),
            result.getEscalationReason(),
            correlationId
        );

        // Notify escalation team
        notificationService.notifyEscalationTeam(
            result.getAlertId(),
            result.getEscalationLevel(),
            correlationId
        );
    }

    private void handleFailedResolution(AlertResolutionRecoveryResult result, String eventId, String correlationId) {
        log.error("Alert resolution failed: alertId={}, reason={}, correlationId={}",
                result.getAlertId(), result.getFailureReason(), correlationId);

        // Queue for manual review
        manualReviewQueue.add(
            ManualReviewItem.builder()
                .alertId(result.getAlertId())
                .failureReason(result.getFailureReason())
                .eventId(eventId)
                .correlationId(correlationId)
                .priority(Priority.HIGH)
                .build()
        );
    }

    private void updateAnalyticsMetrics(AlertResolutionRecoveryResult result, String correlationId) {
        // Update resolution metrics
        metricsService.recordAlertResolution(
            result.getAlertType(),
            result.getResolutionStatus(),
            result.getResolutionTime(),
            correlationId
        );

        // Update SLA metrics
        if (result.getResolutionTime() != null) {
            slaMetricsService.recordResolutionTime(
                result.getAlertType(),
                result.getAlertSeverity(),
                result.getResolutionTime(),
                correlationId
            );
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        if (processedEvents.size() > 10000) {
            cleanupOldProcessedEvents();
        }
    }

    private void cleanupOldProcessedEvents() {
        long cutoffTime = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        processedEvents.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }
}