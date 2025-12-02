package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.notification.AlertNotificationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.notification.service.AlertNotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class AlertNotificationEventsConsumer extends BaseKafkaConsumer<AlertNotificationEvent> {

    private final AlertNotificationService alertService;
    private final MetricsService metricsService;

    private Counter processedCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    public AlertNotificationEventsConsumer(
            AlertNotificationService alertService,
            ObjectMapper objectMapper,
            MetricsService metricsService) {
        super(objectMapper, "alert-notification-events");
        this.alertService = alertService;
        this.metricsService = metricsService;
    }

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        this.processedCounter = Counter.builder("alert_notification_events_processed_total")
                .description("Total number of alert notification events processed")
                .register(metricsService.getMeterRegistry());

        this.errorCounter = Counter.builder("alert_notification_events_error_total")
                .description("Total number of alert notification events failed")
                .register(metricsService.getMeterRegistry());

        this.processingTimer = Timer.builder("alert_notification_processing_duration")
                .description("Time taken to process alert notification events")
                .register(metricsService.getMeterRegistry());
    }

    @KafkaListener(
        topics = "${kafka.topics.alert-notification-events}",
        groupId = "${kafka.consumer.groups.alert-notification}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "alert-notification-consumer", fallbackMethod = "fallbackProcessAlert")
    @Retry(name = "alert-notification-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing alert notification event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            AlertNotificationEvent event = deserializeEvent(record.value(), AlertNotificationEvent.class);
            validateEvent(event);

            alertService.processAlertNotification(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed alert notification event - EventId: {}, Severity: {}, AlertType: {}, CorrelationId: {}",
                    event.getEventId(), event.getSeverity(), event.getAlertType(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process alert notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessAlert(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for alert notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        alertService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected AlertNotificationEvent deserializeEvent(String eventData, Class<AlertNotificationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize alert notification event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(AlertNotificationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Alert notification event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getSeverity() == null) {
            throw new IllegalArgumentException("Severity cannot be null");
        }
        if (event.getAlertType() == null) {
            throw new IllegalArgumentException("Alert type cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            alertService.logAlertAuditFailure(record, error, correlationId);
            alertService.createAlertIncident(record, error, correlationId);
            alertService.alertOpsTeam(record, error, correlationId);
            alertService.triggerEmergencyProtocol(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle alert notification processing failure - CorrelationId: {}, Error: {}",
                    correlationId, e.getMessage(), e);
        }
    }

    private EventContext buildEventContext(ConsumerRecord<String, String> record, String correlationId) {
        return EventContext.builder()
                .correlationId(correlationId)
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .key(record.key())
                .timestamp(Instant.ofEpochMilli(record.timestamp()))
                .build();
    }
}