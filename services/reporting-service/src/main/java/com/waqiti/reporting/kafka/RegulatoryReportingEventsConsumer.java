package com.waqiti.reporting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.reporting.RegulatoryReportingEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.reporting.service.RegulatoryReportingService;
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
public class RegulatoryReportingEventsConsumer extends BaseKafkaConsumer<RegulatoryReportingEvent> {

    private final RegulatoryReportingService regulatoryService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("regulatory_reporting_events_processed_total")
            .description("Total number of regulatory reporting events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("regulatory_reporting_events_error_total")
            .description("Total number of regulatory reporting events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("regulatory_reporting_processing_duration")
            .description("Time taken to process regulatory reporting events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.regulatory-reporting-events}",
        groupId = "${kafka.consumer.groups.regulatory-reporting}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "regulatory-reporting-consumer", fallbackMethod = "fallbackProcessRegulatory")
    @Retry(name = "regulatory-reporting-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing regulatory reporting event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            RegulatoryReportingEvent event = deserializeEvent(record.value(), RegulatoryReportingEvent.class);
            validateEvent(event);

            regulatoryService.processRegulatoryReportingEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed regulatory reporting event - EventId: {}, Regulator: {}, ReportType: {}, CorrelationId: {}",
                    event.getEventId(), event.getRegulator(), event.getReportType(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process regulatory reporting event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessRegulatory(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for regulatory reporting event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        regulatoryService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected RegulatoryReportingEvent deserializeEvent(String eventData, Class<RegulatoryReportingEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize regulatory reporting event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(RegulatoryReportingEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Regulatory reporting event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getRegulator() == null || event.getRegulator().trim().isEmpty()) {
            throw new IllegalArgumentException("Regulator cannot be null or empty");
        }
        if (event.getReportType() == null) {
            throw new IllegalArgumentException("Report type cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            regulatoryService.logRegulatoryAuditFailure(record, error, correlationId);
            regulatoryService.createRegulatoryIncident(record, error, correlationId);
            regulatoryService.alertComplianceOfficer(record, error, correlationId);
            regulatoryService.notifyLegalTeam(record, error, correlationId);
            regulatoryService.escalateToChiefComplianceOfficer(record, error, correlationId);
            regulatoryService.triggerRegulatoryContingencyPlan(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle regulatory reporting processing failure - CorrelationId: {}, Error: {}",
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