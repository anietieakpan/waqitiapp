package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.analytics.MetricsCollectionEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.analytics.service.MetricsCollectionService;
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
public class MetricsCollectionEventsConsumer extends BaseKafkaConsumer<MetricsCollectionEvent> {

    private final MetricsCollectionService metricsCollectionService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("metrics_collection_events_processed_total")
            .description("Total number of metrics collection events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("metrics_collection_events_error_total")
            .description("Total number of metrics collection events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("metrics_collection_processing_duration")
            .description("Time taken to process metrics collection events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.metrics-collection-events}",
        groupId = "${kafka.consumer.groups.metrics-collection}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "metrics-collection-consumer", fallbackMethod = "fallbackProcessMetrics")
    @Retry(name = "metrics-collection-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing metrics collection event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            MetricsCollectionEvent event = deserializeEvent(record.value(), MetricsCollectionEvent.class);
            validateEvent(event);

            metricsCollectionService.processMetricsCollection(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed metrics collection event - EventId: {}, MetricType: {}, Source: {}, CorrelationId: {}",
                    event.getEventId(), event.getMetricType(), event.getSource(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process metrics collection event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessMetrics(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for metrics collection event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        metricsCollectionService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected MetricsCollectionEvent deserializeEvent(String eventData, Class<MetricsCollectionEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize metrics collection event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(MetricsCollectionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Metrics collection event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getMetricType() == null) {
            throw new IllegalArgumentException("Metric type cannot be null");
        }
        if (event.getSource() == null || event.getSource().trim().isEmpty()) {
            throw new IllegalArgumentException("Source cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            metricsCollectionService.logMetricsAuditFailure(record, error, correlationId);
            metricsCollectionService.createMetricsIncident(record, error, correlationId);
            metricsCollectionService.alertObservabilityTeam(record, error, correlationId);
            metricsCollectionService.triggerBackupCollection(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle metrics collection processing failure - CorrelationId: {}, Error: {}",
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