package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.customer.CustomerComplaintEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.customer.service.CustomerComplaintService;
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
public class CustomerComplaintEventsConsumer extends BaseKafkaConsumer<CustomerComplaintEvent> {

    private final CustomerComplaintService complaintService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("customer_complaint_events_processed_total")
            .description("Total number of customer complaint events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("customer_complaint_events_error_total")
            .description("Total number of customer complaint events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("customer_complaint_processing_duration")
            .description("Time taken to process customer complaint events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.customer-complaint-events}",
        groupId = "${kafka.consumer.groups.customer-complaint}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "customer-complaint-consumer", fallbackMethod = "fallbackProcessComplaint")
    @Retry(name = "customer-complaint-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing customer complaint event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            CustomerComplaintEvent event = deserializeEvent(record.value(), CustomerComplaintEvent.class);
            validateEvent(event);

            // Process complaint with priority routing and escalation
            complaintService.processComplaintEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed customer complaint event - EventId: {}, CustomerId: {}, Severity: {}, CorrelationId: {}",
                    event.getEventId(), event.getCustomerId(), event.getSeverity(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process customer complaint event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessComplaint(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for customer complaint event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        complaintService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected CustomerComplaintEvent deserializeEvent(String eventData, Class<CustomerComplaintEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize customer complaint event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(CustomerComplaintEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Customer complaint event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getSeverity() == null) {
            throw new IllegalArgumentException("Severity cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            complaintService.logComplaintAuditFailure(record, error, correlationId);
            complaintService.createComplaintIncident(record, error, correlationId);
            complaintService.alertCustomerServiceManager(record, error, correlationId);
            complaintService.escalateToSeniorManagement(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle complaint processing failure - CorrelationId: {}, Error: {}",
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