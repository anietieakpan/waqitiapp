package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.notification.WebhookNotificationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.notification.service.WebhookNotificationService;
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
@RequiredArgsConstructor
public class WebhookNotificationEventsConsumer extends BaseKafkaConsumer<WebhookNotificationEvent> {

    private final WebhookNotificationService webhookService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("webhook_notification_events_processed_total")
            .description("Total number of webhook notification events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("webhook_notification_events_error_total")
            .description("Total number of webhook notification events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("webhook_notification_processing_duration")
            .description("Time taken to process webhook notification events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.webhook-notification-events}",
        groupId = "${kafka.consumer.groups.webhook-notification}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "webhook-notification-consumer", fallbackMethod = "fallbackProcessWebhook")
    @Retry(name = "webhook-notification-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing webhook notification event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            WebhookNotificationEvent event = deserializeEvent(record.value(), WebhookNotificationEvent.class);
            validateEvent(event);

            webhookService.processWebhookNotification(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed webhook notification event - EventId: {}, WebhookUrl: {}, EventType: {}, CorrelationId: {}",
                    event.getEventId(), event.getWebhookUrl(), event.getEventType(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process webhook notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessWebhook(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for webhook notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        webhookService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected WebhookNotificationEvent deserializeEvent(String eventData, Class<WebhookNotificationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize webhook notification event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(WebhookNotificationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Webhook notification event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getWebhookUrl() == null || event.getWebhookUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook URL cannot be null or empty");
        }
        if (event.getEventType() == null) {
            throw new IllegalArgumentException("Event type cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            webhookService.logWebhookAuditFailure(record, error, correlationId);
            webhookService.createWebhookIncident(record, error, correlationId);
            webhookService.alertIntegrationTeam(record, error, correlationId);
            webhookService.scheduleRetryWithBackoff(record, error, correlationId);
            webhookService.notifyWebhookOwner(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle webhook notification processing failure - CorrelationId: {}, Error: {}",
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