package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.events.notification.EmergencyNotificationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.notification.service.EmergencyNotificationService;
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
public class EmergencyNotificationEventsConsumer extends BaseKafkaConsumer<EmergencyNotificationEvent> {

    private final EmergencyNotificationService emergencyService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final UniversalDLQHandler universalDLQHandler;

    private final Counter processedCounter = Counter.builder("emergency_notification_events_processed_total")
            .description("Total number of emergency notification events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("emergency_notification_events_error_total")
            .description("Total number of emergency notification events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("emergency_notification_processing_duration")
            .description("Time taken to process emergency notification events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.emergency-notification-events}",
        groupId = "${kafka.consumer.groups.emergency-notification}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "emergency-notification-consumer", fallbackMethod = "fallbackProcessEmergency")
    @Retry(name = "emergency-notification-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing emergency notification event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            EmergencyNotificationEvent event = deserializeEvent(record.value(), EmergencyNotificationEvent.class);
            validateEvent(event);

            emergencyService.processEmergencyNotification(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed emergency notification event - EventId: {}, EmergencyLevel: {}, Recipients: {}, CorrelationId: {}",
                    event.getEventId(), event.getEmergencyLevel(), event.getRecipientCount(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process emergency notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            // Use UniversalDLQHandler for enhanced DLQ routing
            universalDLQHandler.sendToDLQ(
                record.value(),
                topic,
                partition,
                record.offset(),
                e,
                Map.of(
                    "consumerGroup", "${kafka.consumer.groups.emergency-notification}",
                    "errorType", e.getClass().getSimpleName(),
                    "correlationId", correlationId
                )
            );

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessEmergency(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for emergency notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        emergencyService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected EmergencyNotificationEvent deserializeEvent(String eventData, Class<EmergencyNotificationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize emergency notification event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(EmergencyNotificationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Emergency notification event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getEmergencyLevel() == null) {
            throw new IllegalArgumentException("Emergency level cannot be null");
        }
        if (event.getRecipientCount() == null || event.getRecipientCount() <= 0) {
            throw new IllegalArgumentException("Recipient count must be positive");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            emergencyService.logEmergencyAuditFailure(record, error, correlationId);
            emergencyService.createEmergencyIncident(record, error, correlationId);
            emergencyService.alertIncidentCommandCenter(record, error, correlationId);
            emergencyService.activateBackupNotificationSystems(record, error, correlationId);
            emergencyService.escalateToExecutiveTeam(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle emergency notification processing failure - CorrelationId: {}, Error: {}",
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