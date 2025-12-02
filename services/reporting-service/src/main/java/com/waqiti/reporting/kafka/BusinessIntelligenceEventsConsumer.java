package com.waqiti.reporting.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.reporting.BusinessIntelligenceEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.reporting.service.BusinessIntelligenceService;
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
public class BusinessIntelligenceEventsConsumer extends BaseKafkaConsumer<BusinessIntelligenceEvent> {

    private final BusinessIntelligenceService biService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("business_intelligence_events_processed_total")
            .description("Total number of business intelligence events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("business_intelligence_events_error_total")
            .description("Total number of business intelligence events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("business_intelligence_processing_duration")
            .description("Time taken to process business intelligence events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.business-intelligence-events}",
        groupId = "${kafka.consumer.groups.business-intelligence}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "business-intelligence-consumer", fallbackMethod = "fallbackProcessBI")
    @Retry(name = "business-intelligence-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing business intelligence event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            BusinessIntelligenceEvent event = deserializeEvent(record.value(), BusinessIntelligenceEvent.class);
            validateEvent(event);

            biService.processBusinessIntelligenceEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed business intelligence event - EventId: {}, AnalysisType: {}, DataSource: {}, CorrelationId: {}",
                    event.getEventId(), event.getAnalysisType(), event.getDataSource(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process business intelligence event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessBI(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for business intelligence event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        biService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected BusinessIntelligenceEvent deserializeEvent(String eventData, Class<BusinessIntelligenceEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize business intelligence event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(BusinessIntelligenceEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Business intelligence event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getAnalysisType() == null) {
            throw new IllegalArgumentException("Analysis type cannot be null");
        }
        if (event.getDataSource() == null || event.getDataSource().trim().isEmpty()) {
            throw new IllegalArgumentException("Data source cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            biService.logBIAuditFailure(record, error, correlationId);
            biService.createBIIncident(record, error, correlationId);
            biService.alertDataScienceTeam(record, error, correlationId);
            biService.triggerAlternativeAnalysis(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle business intelligence processing failure - CorrelationId: {}, Error: {}",
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