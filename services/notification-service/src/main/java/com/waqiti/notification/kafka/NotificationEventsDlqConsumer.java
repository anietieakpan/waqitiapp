package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.notification.NotificationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.notification.service.NotificationDlqService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventsDlqConsumer extends BaseKafkaConsumer<NotificationEvent> {

    private final NotificationDlqService notificationDlqService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter dlqProcessedCounter = Counter.builder("notification_dlq_events_processed_total")
            .description("Total number of notification DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter dlqErrorCounter = Counter.builder("notification_dlq_events_error_total")
            .description("Total number of notification DLQ events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer dlqProcessingTimer = Timer.builder("notification_dlq_processing_duration")
            .description("Time taken to process notification DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.notification-events-dlq}",
        groupId = "${kafka.consumer.groups.notification-dlq}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing notification DLQ event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            NotificationEvent event = deserializeEvent(record.value(), NotificationEvent.class);
            validateEvent(event);

            // Process notification DLQ with delivery retry analysis
            notificationDlqService.processDlqEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            dlqProcessedCounter.increment();

            log.info("Successfully processed notification DLQ event - EventId: {}, Type: {}, NotificationId: {}, CorrelationId: {}",
                    event.getEventId(), event.getEventType(), event.getNotificationId(), correlationId);

        } catch (Exception e) {
            dlqErrorCounter.increment();
            log.error("Failed to process notification DLQ event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            // Acknowledge DLQ events to prevent infinite loops
            acknowledgment.acknowledge();

            // Handle notification DLQ failure with communication impact analysis
            handleDlqProcessingFailure(record, e, correlationId);

        } finally {
            sample.stop(dlqProcessingTimer);
        }
    }

    @Override
    protected NotificationEvent deserializeEvent(String eventData, Class<NotificationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize notification DLQ event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(NotificationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Notification DLQ event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification DLQ event ID cannot be null or empty");
        }
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification DLQ event type cannot be null or empty");
        }
        if (event.getNotificationId() == null || event.getNotificationId().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification ID cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Notification DLQ event timestamp cannot be null");
        }
    }

    private void handleDlqProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            // Log to notification audit trail
            notificationDlqService.logNotificationAuditFailure(record, error, correlationId);

            // Create incident for notification DLQ failures
            notificationDlqService.createNotificationIncident(record, error, correlationId);

            // Alert communications and customer service teams
            notificationDlqService.alertCommunicationTeams(record, error, correlationId);

            // Analyze failed notification impact on customer experience
            notificationDlqService.analyzeCustomerImpact(record, error, correlationId);

            // Trigger alternative notification channels if critical
            notificationDlqService.triggerAlternativeChannels(record, error, correlationId);

        } catch (Exception e) {
            log.error("Failed to handle notification DLQ processing failure - CorrelationId: {}, Error: {}",
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