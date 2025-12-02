package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.customer.CustomerFeedbackEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.customer.service.CustomerFeedbackService;
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
public class CustomerFeedbackEventsConsumer extends BaseKafkaConsumer<CustomerFeedbackEvent> {

    private final CustomerFeedbackService feedbackService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("customer_feedback_events_processed_total")
            .description("Total number of customer feedback events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("customer_feedback_events_error_total")
            .description("Total number of customer feedback events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("customer_feedback_processing_duration")
            .description("Time taken to process customer feedback events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.customer-feedback-events}",
        groupId = "${kafka.consumer.groups.customer-feedback}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "customer-feedback-consumer", fallbackMethod = "fallbackProcessFeedback")
    @Retry(name = "customer-feedback-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing customer feedback event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            CustomerFeedbackEvent event = deserializeEvent(record.value(), CustomerFeedbackEvent.class);
            validateEvent(event);

            // Process feedback with sentiment analysis and routing
            feedbackService.processFeedbackEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed customer feedback event - EventId: {}, CustomerId: {}, Rating: {}, CorrelationId: {}",
                    event.getEventId(), event.getCustomerId(), event.getRating(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process customer feedback event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessFeedback(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for customer feedback event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        feedbackService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected CustomerFeedbackEvent deserializeEvent(String eventData, Class<CustomerFeedbackEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize customer feedback event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(CustomerFeedbackEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Customer feedback event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            feedbackService.logFeedbackAuditFailure(record, error, correlationId);
            feedbackService.createFeedbackIncident(record, error, correlationId);
            feedbackService.alertCustomerExperienceTeam(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle feedback processing failure - CorrelationId: {}, Error: {}",
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