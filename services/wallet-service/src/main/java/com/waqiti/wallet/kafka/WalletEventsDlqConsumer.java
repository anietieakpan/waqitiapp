package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.wallet.WalletEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.wallet.service.WalletDlqService;
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
public class WalletEventsDlqConsumer extends BaseKafkaConsumer<WalletEvent> {

    private final WalletDlqService walletDlqService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter dlqProcessedCounter = Counter.builder("wallet_dlq_events_processed_total")
            .description("Total number of wallet DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter dlqErrorCounter = Counter.builder("wallet_dlq_events_error_total")
            .description("Total number of wallet DLQ events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer dlqProcessingTimer = Timer.builder("wallet_dlq_processing_duration")
            .description("Time taken to process wallet DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.wallet-events-dlq}",
        groupId = "${kafka.consumer.groups.wallet-dlq}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing wallet DLQ event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            WalletEvent event = deserializeEvent(record.value(), WalletEvent.class);
            validateEvent(event);

            // Process wallet DLQ with balance verification
            walletDlqService.processDlqEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            dlqProcessedCounter.increment();

            log.info("Successfully processed wallet DLQ event - EventId: {}, Type: {}, WalletId: {}, CorrelationId: {}",
                    event.getEventId(), event.getEventType(), event.getWalletId(), correlationId);

        } catch (Exception e) {
            dlqErrorCounter.increment();
            log.error("Failed to process wallet DLQ event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            // Acknowledge DLQ events to prevent infinite loops
            acknowledgment.acknowledge();

            // Handle wallet DLQ failure with balance protection
            handleDlqProcessingFailure(record, e, correlationId);

        } finally {
            sample.stop(dlqProcessingTimer);
        }
    }

    @Override
    protected WalletEvent deserializeEvent(String eventData, Class<WalletEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize wallet DLQ event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(WalletEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Wallet DLQ event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet DLQ event ID cannot be null or empty");
        }
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet DLQ event type cannot be null or empty");
        }
        if (event.getWalletId() == null || event.getWalletId().trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet ID cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Wallet DLQ event timestamp cannot be null");
        }
    }

    private void handleDlqProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            // Log to wallet audit trail
            walletDlqService.logWalletAuditFailure(record, error, correlationId);

            // Create incident for wallet DLQ failures
            walletDlqService.createWalletIncident(record, error, correlationId);

            // Alert wallet operations team
            walletDlqService.alertWalletOpsTeam(record, error, correlationId);

            // Trigger wallet balance verification
            walletDlqService.triggerBalanceVerification(record, error, correlationId);

            // Check for potential wallet freeze requirements
            walletDlqService.evaluateWalletSecurity(record, error, correlationId);

        } catch (Exception e) {
            log.error("Failed to handle wallet DLQ processing failure - CorrelationId: {}, Error: {}",
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