package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.customer.CustomerRetentionEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.customer.service.CustomerRetentionService;
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
public class CustomerRetentionEventsConsumer extends BaseKafkaConsumer<CustomerRetentionEvent> {

    private final CustomerRetentionService retentionService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("customer_retention_events_processed_total")
            .description("Total number of customer retention events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("customer_retention_events_error_total")
            .description("Total number of customer retention events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("customer_retention_processing_duration")
            .description("Time taken to process customer retention events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.customer-retention-events}",
        groupId = "${kafka.consumer.groups.customer-retention}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "customer-retention-consumer", fallbackMethod = "fallbackProcessRetention")
    @Retry(name = "customer-retention-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing customer retention event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            CustomerRetentionEvent event = deserializeEvent(record.value(), CustomerRetentionEvent.class);
            validateEvent(event);

            retentionService.processRetentionEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed customer retention event - EventId: {}, CustomerId: {}, RiskLevel: {}, CorrelationId: {}",
                    event.getEventId(), event.getCustomerId(), event.getChurnRiskLevel(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process customer retention event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessRetention(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for customer retention event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        retentionService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected CustomerRetentionEvent deserializeEvent(String eventData, Class<CustomerRetentionEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize customer retention event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(CustomerRetentionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Customer retention event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getChurnRiskLevel() == null) {
            throw new IllegalArgumentException("Churn risk level cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            retentionService.logRetentionAuditFailure(record, error, correlationId);
            retentionService.createRetentionIncident(record, error, correlationId);
            retentionService.alertRetentionTeam(record, error, correlationId);
            retentionService.triggerEmergencyRetentionCampaign(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle retention processing failure - CorrelationId: {}, Error: {}",
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