package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.analytics.DashboardUpdateEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.analytics.service.DashboardUpdateService;
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

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardUpdateEventsConsumer extends BaseKafkaConsumer<DashboardUpdateEvent> {

    private final DashboardUpdateService dashboardService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final UniversalDLQHandler universalDLQHandler;

    private final Counter processedCounter = Counter.builder("dashboard_update_events_processed_total")
            .description("Total number of dashboard update events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("dashboard_update_events_error_total")
            .description("Total number of dashboard update events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("dashboard_update_processing_duration")
            .description("Time taken to process dashboard update events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.dashboard-update-events}",
        groupId = "${kafka.consumer.groups.dashboard-update}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "dashboard-update-consumer", fallbackMethod = "fallbackProcessDashboard")
    @Retry(name = "dashboard-update-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing dashboard update event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            DashboardUpdateEvent event = deserializeEvent(record.value(), DashboardUpdateEvent.class);
            validateEvent(event);

            dashboardService.processDashboardUpdate(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed dashboard update event - EventId: {}, DashboardId: {}, UpdateType: {}, CorrelationId: {}",
                    event.getEventId(), event.getDashboardId(), event.getUpdateType(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process dashboard update event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);

            // Send to DLQ via UniversalDLQHandler
            try {
                universalDLQHandler.handleFailedMessage(record, e);
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ: {}", dlqException.getMessage());
            }

            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessDashboard(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for dashboard update event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        dashboardService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected DashboardUpdateEvent deserializeEvent(String eventData, Class<DashboardUpdateEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize dashboard update event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(DashboardUpdateEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Dashboard update event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getDashboardId() == null || event.getDashboardId().trim().isEmpty()) {
            throw new IllegalArgumentException("Dashboard ID cannot be null or empty");
        }
        if (event.getUpdateType() == null) {
            throw new IllegalArgumentException("Update type cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            dashboardService.logDashboardAuditFailure(record, error, correlationId);
            dashboardService.createDashboardIncident(record, error, correlationId);
            dashboardService.alertAnalyticsTeam(record, error, correlationId);
            dashboardService.triggerFallbackRefresh(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle dashboard update processing failure - CorrelationId: {}, Error: {}",
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