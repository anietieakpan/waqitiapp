package com.waqiti.reporting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.reporting.ReportingGenerationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.reporting.service.ReportingGenerationService;
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
public class ReportingGenerationEventsConsumer extends BaseKafkaConsumer<ReportingGenerationEvent> {

    private final ReportingGenerationService reportingService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("reporting_generation_events_processed_total")
            .description("Total number of reporting generation events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("reporting_generation_events_error_total")
            .description("Total number of reporting generation events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("reporting_generation_processing_duration")
            .description("Time taken to process reporting generation events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.reporting-generation-events}",
        groupId = "${kafka.consumer.groups.reporting-generation}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "reporting-generation-consumer", fallbackMethod = "fallbackProcessReporting")
    @Retry(name = "reporting-generation-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing reporting generation event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            ReportingGenerationEvent event = deserializeEvent(record.value(), ReportingGenerationEvent.class);
            validateEvent(event);

            // Process reporting event with document generation
            reportingService.processReportingEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed reporting generation event - EventId: {}, ReportType: {}, RequestedBy: {}, CorrelationId: {}",
                    event.getEventId(), event.getReportType(), event.getRequestedBy(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process reporting generation event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e; // Re-throw to trigger retry mechanism

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessReporting(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for reporting generation event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        // Send to DLQ for manual processing
        reportingService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected ReportingGenerationEvent deserializeEvent(String eventData, Class<ReportingGenerationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize reporting generation event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(ReportingGenerationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Reporting generation event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getReportType() == null) {
            throw new IllegalArgumentException("Report type cannot be null");
        }
        if (event.getRequestedBy() == null || event.getRequestedBy().trim().isEmpty()) {
            throw new IllegalArgumentException("Requested by cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            // Log to reporting audit trail
            reportingService.logReportingAuditFailure(record, error, correlationId);

            // Create reporting incident
            reportingService.createReportingIncident(record, error, correlationId);

            // Alert reporting team and requester
            reportingService.alertReportingTeam(record, error, correlationId);

            // Trigger manual report generation if critical
            reportingService.triggerManualGeneration(record, error, correlationId);

        } catch (Exception e) {
            log.error("Failed to handle reporting generation processing failure - CorrelationId: {}, Error: {}",
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