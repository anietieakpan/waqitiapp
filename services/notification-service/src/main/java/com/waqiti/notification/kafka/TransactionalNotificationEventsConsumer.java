package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.events.notification.TransactionalNotificationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.notification.service.TransactionalNotificationService;
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
public class TransactionalNotificationEventsConsumer extends BaseKafkaConsumer<TransactionalNotificationEvent> {

    private final TransactionalNotificationService transactionalService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final UniversalDLQHandler universalDLQHandler;

    private final Counter processedCounter = Counter.builder("transactional_notification_events_processed_total")
            .description("Total number of transactional notification events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("transactional_notification_events_error_total")
            .description("Total number of transactional notification events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("transactional_notification_processing_duration")
            .description("Time taken to process transactional notification events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.transactional-notification-events}",
        groupId = "${kafka.consumer.groups.transactional-notification}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "transactional-notification-consumer", fallbackMethod = "fallbackProcessTransactional")
    @Retry(name = "transactional-notification-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing transactional notification event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            TransactionalNotificationEvent event = deserializeEvent(record.value(), TransactionalNotificationEvent.class);
            validateEvent(event);

            transactionalService.processTransactionalNotification(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed transactional notification event - EventId: {}, TransactionId: {}, NotificationType: {}, CorrelationId: {}",
                    event.getEventId(), event.getTransactionId(), event.getNotificationType(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process transactional notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            // Use UniversalDLQHandler for enhanced DLQ routing
            universalDLQHandler.sendToDLQ(
                record.value(),
                topic,
                partition,
                record.offset(),
                e,
                Map.of(
                    "consumerGroup", "${kafka.consumer.groups.transactional-notification}",
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

    public void fallbackProcessTransactional(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for transactional notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        transactionalService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected TransactionalNotificationEvent deserializeEvent(String eventData, Class<TransactionalNotificationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize transactional notification event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(TransactionalNotificationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Transactional notification event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (event.getNotificationType() == null) {
            throw new IllegalArgumentException("Notification type cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            transactionalService.logTransactionalAuditFailure(record, error, correlationId);
            transactionalService.createTransactionalIncident(record, error, correlationId);
            transactionalService.alertTransactionOpsTeam(record, error, correlationId);
            transactionalService.triggerBackupNotification(record, error, correlationId);
            transactionalService.checkComplianceRequirements(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle transactional notification processing failure - CorrelationId: {}, Error: {}",
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