package com.waqiti.reporting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.reporting.ExecutiveReportingEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.reporting.service.ExecutiveReportingService;
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
public class ExecutiveReportingEventsConsumer extends BaseKafkaConsumer<ExecutiveReportingEvent> {

    private final ExecutiveReportingService executiveService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("executive_reporting_events_processed_total")
            .description("Total number of executive reporting events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("executive_reporting_events_error_total")
            .description("Total number of executive reporting events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("executive_reporting_processing_duration")
            .description("Time taken to process executive reporting events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.executive-reporting-events}",
        groupId = "${kafka.consumer.groups.executive-reporting}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "executive-reporting-consumer", fallbackMethod = "fallbackProcessExecutive")
    @Retry(name = "executive-reporting-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing executive reporting event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            ExecutiveReportingEvent event = deserializeEvent(record.value(), ExecutiveReportingEvent.class);
            validateEvent(event);

            executiveService.processExecutiveReportingEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed executive reporting event - EventId: {}, ReportLevel: {}, Executive: {}, CorrelationId: {}",
                    event.getEventId(), event.getReportLevel(), event.getExecutiveLevel(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process executive reporting event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessExecutive(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for executive reporting event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        executiveService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected ExecutiveReportingEvent deserializeEvent(String eventData, Class<ExecutiveReportingEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize executive reporting event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(ExecutiveReportingEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Executive reporting event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getReportLevel() == null) {
            throw new IllegalArgumentException("Report level cannot be null");
        }
        if (event.getExecutiveLevel() == null) {
            throw new IllegalArgumentException("Executive level cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            executiveService.logExecutiveAuditFailure(record, error, correlationId);
            executiveService.createExecutiveIncident(record, error, correlationId);
            executiveService.alertExecutiveAssistants(record, error, correlationId);
            executiveService.escalateToChiefOfStaff(record, error, correlationId);
            executiveService.generateFallbackSummary(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle executive reporting processing failure - CorrelationId: {}, Error: {}",
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