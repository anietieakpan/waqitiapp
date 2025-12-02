package com.waqiti.reporting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.reporting.FinancialReportingEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.reporting.service.FinancialReportingService;
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
public class FinancialReportingEventsConsumer extends BaseKafkaConsumer<FinancialReportingEvent> {

    private final FinancialReportingService financialService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("financial_reporting_events_processed_total")
            .description("Total number of financial reporting events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("financial_reporting_events_error_total")
            .description("Total number of financial reporting events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("financial_reporting_processing_duration")
            .description("Time taken to process financial reporting events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.financial-reporting-events}",
        groupId = "${kafka.consumer.groups.financial-reporting}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "financial-reporting-consumer", fallbackMethod = "fallbackProcessFinancial")
    @Retry(name = "financial-reporting-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing financial reporting event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            FinancialReportingEvent event = deserializeEvent(record.value(), FinancialReportingEvent.class);
            validateEvent(event);

            financialService.processFinancialReportingEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed financial reporting event - EventId: {}, ReportPeriod: {}, ReportType: {}, CorrelationId: {}",
                    event.getEventId(), event.getReportPeriod(), event.getReportType(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process financial reporting event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessFinancial(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for financial reporting event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        financialService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected FinancialReportingEvent deserializeEvent(String eventData, Class<FinancialReportingEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize financial reporting event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(FinancialReportingEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Financial reporting event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getReportPeriod() == null) {
            throw new IllegalArgumentException("Report period cannot be null");
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
            financialService.logFinancialAuditFailure(record, error, correlationId);
            financialService.createFinancialIncident(record, error, correlationId);
            financialService.alertCFOOffice(record, error, correlationId);
            financialService.notifyExternalAuditors(record, error, correlationId);
            financialService.escalateToAuditCommittee(record, error, correlationId);
            financialService.triggerFinancialContingencyPlan(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle financial reporting processing failure - CorrelationId: {}, Error: {}",
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