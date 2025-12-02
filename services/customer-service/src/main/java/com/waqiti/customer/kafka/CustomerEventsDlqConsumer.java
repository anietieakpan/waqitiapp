package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.customer.CustomerEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.customer.service.CustomerDlqService;
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
public class CustomerEventsDlqConsumer extends BaseKafkaConsumer<CustomerEvent> {

    private final CustomerDlqService customerDlqService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter dlqProcessedCounter = Counter.builder("customer_dlq_events_processed_total")
            .description("Total number of customer DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter dlqErrorCounter = Counter.builder("customer_dlq_events_error_total")
            .description("Total number of customer DLQ events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer dlqProcessingTimer = Timer.builder("customer_dlq_processing_duration")
            .description("Time taken to process customer DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.customer-events-dlq}",
        groupId = "${kafka.consumer.groups.customer-dlq}",
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
            log.info("Processing customer DLQ event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            CustomerEvent event = deserializeEvent(record.value(), CustomerEvent.class);
            validateEvent(event);

            // Process customer DLQ with GDPR compliance
            customerDlqService.processDlqEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            dlqProcessedCounter.increment();

            log.info("Successfully processed customer DLQ event - EventId: {}, Type: {}, CustomerId: {}, CorrelationId: {}",
                    event.getEventId(), event.getEventType(), event.getCustomerId(), correlationId);

        } catch (Exception e) {
            dlqErrorCounter.increment();
            log.error("Failed to process customer DLQ event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            // Acknowledge DLQ events to prevent infinite loops
            acknowledgment.acknowledge();

            // Handle customer DLQ failure with privacy protection
            handleDlqProcessingFailure(record, e, correlationId);

        } finally {
            sample.stop(dlqProcessingTimer);
        }
    }

    @Override
    protected CustomerEvent deserializeEvent(String eventData, Class<CustomerEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize customer DLQ event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(CustomerEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Customer DLQ event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer DLQ event ID cannot be null or empty");
        }
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer DLQ event type cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Customer DLQ event timestamp cannot be null");
        }
    }

    private void handleDlqProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            // Log to customer audit trail with privacy compliance
            customerDlqService.logCustomerAuditFailure(record, error, correlationId);

            // Create incident for customer DLQ failures
            customerDlqService.createCustomerIncident(record, error, correlationId);

            // Alert customer service and privacy teams
            customerDlqService.alertCustomerTeams(record, error, correlationId);

            // Check for GDPR compliance impact
            customerDlqService.checkGdprCompliance(record, error, correlationId);

            // Trigger customer data integrity verification
            customerDlqService.triggerDataIntegrityCheck(record, error, correlationId);

        } catch (Exception e) {
            log.error("Failed to handle customer DLQ processing failure - CorrelationId: {}, Error: {}",
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