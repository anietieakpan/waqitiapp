package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.customer.CustomerWinbackEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.customer.service.CustomerWinbackService;
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
public class CustomerWinbackEventsConsumer extends BaseKafkaConsumer<CustomerWinbackEvent> {

    private final CustomerWinbackService winbackService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("customer_winback_events_processed_total")
            .description("Total number of customer winback events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("customer_winback_events_error_total")
            .description("Total number of customer winback events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("customer_winback_processing_duration")
            .description("Time taken to process customer winback events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.customer-winback-events}",
        groupId = "${kafka.consumer.groups.customer-winback}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "customer-winback-consumer", fallbackMethod = "fallbackProcessWinback")
    @Retry(name = "customer-winback-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing customer winback event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            CustomerWinbackEvent event = deserializeEvent(record.value(), CustomerWinbackEvent.class);
            validateEvent(event);

            winbackService.processWinbackEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed customer winback event - EventId: {}, CustomerId: {}, CampaignType: {}, CorrelationId: {}",
                    event.getEventId(), event.getCustomerId(), event.getCampaignType(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process customer winback event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessWinback(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for customer winback event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        winbackService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected CustomerWinbackEvent deserializeEvent(String eventData, Class<CustomerWinbackEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize customer winback event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(CustomerWinbackEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Customer winback event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getCampaignType() == null) {
            throw new IllegalArgumentException("Campaign type cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            winbackService.logWinbackAuditFailure(record, error, correlationId);
            winbackService.createWinbackIncident(record, error, correlationId);
            winbackService.alertMarketingTeam(record, error, correlationId);
            winbackService.pauseCampaignIfNeeded(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle winback processing failure - CorrelationId: {}, Error: {}",
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