package com.waqiti.ledger.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.ledger.LedgerEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.ledger.service.LedgerDlqService;
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
public class LedgerEventsDlqConsumer extends BaseKafkaConsumer<LedgerEvent> {

    private final LedgerDlqService ledgerDlqService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter dlqProcessedCounter = Counter.builder("ledger_dlq_events_processed_total")
            .description("Total number of ledger DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter dlqErrorCounter = Counter.builder("ledger_dlq_events_error_total")
            .description("Total number of ledger DLQ events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer dlqProcessingTimer = Timer.builder("ledger_dlq_processing_duration")
            .description("Time taken to process ledger DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.ledger-events-dlq}",
        groupId = "${kafka.consumer.groups.ledger-dlq}",
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
            log.info("Processing ledger DLQ event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            LedgerEvent event = deserializeEvent(record.value(), LedgerEvent.class);
            validateEvent(event);

            // Process ledger DLQ with double-entry validation
            ledgerDlqService.processDlqEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            dlqProcessedCounter.increment();

            log.info("Successfully processed ledger DLQ event - EventId: {}, Type: {}, AccountId: {}, CorrelationId: {}",
                    event.getEventId(), event.getEventType(), event.getAccountId(), correlationId);

        } catch (Exception e) {
            dlqErrorCounter.increment();
            log.error("Failed to process ledger DLQ event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            // Acknowledge DLQ events to prevent infinite loops
            acknowledgment.acknowledge();

            // Handle ledger DLQ failure with accounting integrity checks
            handleDlqProcessingFailure(record, e, correlationId);

        } finally {
            sample.stop(dlqProcessingTimer);
        }
    }

    @Override
    protected LedgerEvent deserializeEvent(String eventData, Class<LedgerEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize ledger DLQ event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(LedgerEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Ledger DLQ event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Ledger DLQ event ID cannot be null or empty");
        }
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Ledger DLQ event type cannot be null or empty");
        }
        if (event.getAccountId() == null || event.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Ledger DLQ event timestamp cannot be null");
        }
    }

    private void handleDlqProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            // Log to accounting audit trail with immutable record
            ledgerDlqService.logAccountingAuditFailure(record, error, correlationId);

            // Create critical incident - ledger failures are high priority
            ledgerDlqService.createCriticalIncident(record, error, correlationId);

            // Alert accounting and treasury teams immediately
            ledgerDlqService.alertAccountingTeams(record, error, correlationId);

            // Trigger ledger integrity verification
            ledgerDlqService.triggerIntegrityVerification(record, error, correlationId);

            // Freeze affected accounts if necessary
            ledgerDlqService.evaluateAccountFreeze(record, error, correlationId);

        } catch (Exception e) {
            log.error("Failed to handle ledger DLQ processing failure - CorrelationId: {}, Error: {}",
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