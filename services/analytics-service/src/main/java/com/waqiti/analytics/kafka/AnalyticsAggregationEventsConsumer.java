package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.analytics.AnalyticsAggregationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.analytics.service.AnalyticsAggregationService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
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
public class AnalyticsAggregationEventsConsumer extends BaseKafkaConsumer<AnalyticsAggregationEvent> {

    private final AnalyticsAggregationService aggregationService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final UniversalDLQHandler universalDLQHandler;

    private final Counter processedCounter = Counter.builder("analytics_aggregation_events_processed_total")
            .description("Total number of analytics aggregation events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("analytics_aggregation_events_error_total")
            .description("Total number of analytics aggregation events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("analytics_aggregation_processing_duration")
            .description("Time taken to process analytics aggregation events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.analytics-aggregation-events}",
        groupId = "${kafka.consumer.groups.analytics-aggregation}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "analytics-aggregation-consumer", fallbackMethod = "fallbackProcessAggregation")
    @Retry(name = "analytics-aggregation-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing analytics aggregation event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            AnalyticsAggregationEvent event = deserializeEvent(record.value(), AnalyticsAggregationEvent.class);
            validateEvent(event);

            // Process aggregation event with data warehouse integration
            aggregationService.processAggregationEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed analytics aggregation event - EventId: {}, AggregationType: {}, Period: {}, CorrelationId: {}",
                    event.getEventId(), event.getAggregationType(), event.getTimePeriod(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process analytics aggregation event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);

            // Send to DLQ via UniversalDLQHandler
            try {
                universalDLQHandler.handleFailedMessage(record, e);
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ: {}", dlqException.getMessage());
            }

            throw e; // Re-throw to trigger retry mechanism

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessAggregation(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for analytics aggregation event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        // Send to DLQ for manual processing
        aggregationService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected AnalyticsAggregationEvent deserializeEvent(String eventData, Class<AnalyticsAggregationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize analytics aggregation event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(AnalyticsAggregationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Analytics aggregation event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getAggregationType() == null) {
            throw new IllegalArgumentException("Aggregation type cannot be null");
        }
        if (event.getTimePeriod() == null) {
            throw new IllegalArgumentException("Time period cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            // Log to analytics audit trail
            aggregationService.logAnalyticsAuditFailure(record, error, correlationId);

            // Create analytics incident
            aggregationService.createAnalyticsIncident(record, error, correlationId);

            // Alert data engineering team
            aggregationService.alertDataEngineeringTeam(record, error, correlationId);

            // Trigger data quality check
            aggregationService.triggerDataQualityCheck(record, error, correlationId);

        } catch (Exception e) {
            log.error("Failed to handle analytics aggregation processing failure - CorrelationId: {}, Error: {}",
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