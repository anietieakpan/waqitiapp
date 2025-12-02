package com.waqiti.reconciliation.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.reconciliation.ReconciliationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.reconciliation.service.ReconciliationDlqService;
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
public class ReconciliationEventsDlqConsumer extends BaseKafkaConsumer<ReconciliationEvent> {

    private final ReconciliationDlqService reconciliationDlqService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter dlqProcessedCounter = Counter.builder("reconciliation_dlq_events_processed_total")
            .description("Total number of reconciliation DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter dlqErrorCounter = Counter.builder("reconciliation_dlq_events_error_total")
            .description("Total number of reconciliation DLQ events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer dlqProcessingTimer = Timer.builder("reconciliation_dlq_processing_duration")
            .description("Time taken to process reconciliation DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.reconciliation-events-dlq}",
        groupId = "${kafka.consumer.groups.reconciliation-dlq}",
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
            log.info("Processing reconciliation DLQ event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            ReconciliationEvent event = deserializeEvent(record.value(), ReconciliationEvent.class);
            validateEvent(event);

            // Process reconciliation DLQ with financial accuracy verification
            reconciliationDlqService.processDlqEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            dlqProcessedCounter.increment();

            log.info("Successfully processed reconciliation DLQ event - EventId: {}, Type: {}, ReconciliationId: {}, CorrelationId: {}",
                    event.getEventId(), event.getEventType(), event.getReconciliationId(), correlationId);

        } catch (Exception e) {
            dlqErrorCounter.increment();
            log.error("Failed to process reconciliation DLQ event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            // Acknowledge DLQ events to prevent infinite loops
            acknowledgment.acknowledge();

            // Handle reconciliation DLQ failure with financial impact analysis
            handleDlqProcessingFailure(record, e, correlationId);

        } finally {
            sample.stop(dlqProcessingTimer);
        }
    }

    @Override
    protected ReconciliationEvent deserializeEvent(String eventData, Class<ReconciliationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize reconciliation DLQ event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(ReconciliationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Reconciliation DLQ event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Reconciliation DLQ event ID cannot be null or empty");
        }
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Reconciliation DLQ event type cannot be null or empty");
        }
        if (event.getReconciliationId() == null || event.getReconciliationId().trim().isEmpty()) {
            throw new IllegalArgumentException("Reconciliation ID cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Reconciliation DLQ event timestamp cannot be null");
        }
    }

    private void handleDlqProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            // Log to financial audit trail
            reconciliationDlqService.logFinancialAuditFailure(record, error, correlationId);

            // Create critical incident for reconciliation failures
            reconciliationDlqService.createCriticalIncident(record, error, correlationId);

            // Alert finance and accounting teams immediately
            reconciliationDlqService.alertFinanceTeams(record, error, correlationId);

            // Trigger manual reconciliation process if automated fails
            reconciliationDlqService.triggerManualReconciliation(record, error, correlationId);

            // Notify external accounting systems
            reconciliationDlqService.notifyExternalSystems(record, error, correlationId);

        } catch (Exception e) {
            log.error("Failed to handle reconciliation DLQ processing failure - CorrelationId: {}, Error: {}",
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