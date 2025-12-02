package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.notification.SmsNotificationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.notification.service.SmsNotificationService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsNotificationEventsConsumer extends BaseKafkaConsumer<SmsNotificationEvent> {

    private final SmsNotificationService smsService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("sms_notification_events_processed_total")
            .description("Total number of SMS notification events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("sms_notification_events_error_total")
            .description("Total number of SMS notification events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("sms_notification_processing_duration")
            .description("Time taken to process SMS notification events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.sms-notification-events}",
        groupId = "${kafka.consumer.groups.sms-notification}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "sms-notification-consumer", fallbackMethod = "fallbackProcessSms")
    @Retry(name = "sms-notification-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing SMS notification event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            SmsNotificationEvent event = deserializeEvent(record.value(), SmsNotificationEvent.class);
            validateEvent(event);

            // Process SMS notification with carrier routing and delivery tracking
            smsService.processSmsNotification(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed SMS notification event - EventId: {}, SmsType: {}, PhoneNumber: {}, CorrelationId: {}",
                    event.getEventId(), event.getSmsType(), event.getPhoneNumber(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process SMS notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e; // Re-throw to trigger retry mechanism

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessSms(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for SMS notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        // Send to DLQ for manual processing
        smsService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected SmsNotificationEvent deserializeEvent(String eventData, Class<SmsNotificationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize SMS notification event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(SmsNotificationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("SMS notification event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getSmsType() == null) {
            throw new IllegalArgumentException("SMS type cannot be null");
        }
        if (event.getPhoneNumber() == null || event.getPhoneNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            // Log to SMS audit trail
            smsService.logSmsAuditFailure(record, error, correlationId);

            // Create SMS delivery incident
            smsService.createSmsIncident(record, error, correlationId);

            // Alert telecommunications team
            smsService.alertTelecomTeam(record, error, correlationId);

            // Trigger alternative carrier if needed
            smsService.triggerAlternativeCarrier(record, error, correlationId);

            // Check for international delivery requirements
            smsService.checkInternationalDelivery(record, error, correlationId);

        } catch (Exception e) {
            log.error("Failed to handle SMS notification processing failure - CorrelationId: {}, Error: {}",
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