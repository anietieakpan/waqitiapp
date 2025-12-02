package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.notification.MarketingNotificationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.notification.service.MarketingNotificationService;
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
public class MarketingNotificationEventsConsumer extends BaseKafkaConsumer<MarketingNotificationEvent> {

    private final MarketingNotificationService marketingService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("marketing_notification_events_processed_total")
            .description("Total number of marketing notification events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("marketing_notification_events_error_total")
            .description("Total number of marketing notification events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("marketing_notification_processing_duration")
            .description("Time taken to process marketing notification events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.marketing-notification-events}",
        groupId = "${kafka.consumer.groups.marketing-notification}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "marketing-notification-consumer", fallbackMethod = "fallbackProcessMarketing")
    @Retry(name = "marketing-notification-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing marketing notification event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            MarketingNotificationEvent event = deserializeEvent(record.value(), MarketingNotificationEvent.class);
            validateEvent(event);

            marketingService.processMarketingNotification(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed marketing notification event - EventId: {}, CampaignId: {}, SegmentId: {}, CorrelationId: {}",
                    event.getEventId(), event.getCampaignId(), event.getSegmentId(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process marketing notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e;

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessMarketing(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for marketing notification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        marketingService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected MarketingNotificationEvent deserializeEvent(String eventData, Class<MarketingNotificationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize marketing notification event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(MarketingNotificationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Marketing notification event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getCampaignId() == null || event.getCampaignId().trim().isEmpty()) {
            throw new IllegalArgumentException("Campaign ID cannot be null or empty");
        }
        if (event.getSegmentId() == null || event.getSegmentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Segment ID cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            marketingService.logMarketingAuditFailure(record, error, correlationId);
            marketingService.createMarketingIncident(record, error, correlationId);
            marketingService.alertMarketingOpsTeam(record, error, correlationId);
            marketingService.checkComplianceImpact(record, error, correlationId);
            marketingService.pauseCampaignIfNeeded(record, error, correlationId);
        } catch (Exception e) {
            log.error("Failed to handle marketing notification processing failure - CorrelationId: {}, Error: {}",
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