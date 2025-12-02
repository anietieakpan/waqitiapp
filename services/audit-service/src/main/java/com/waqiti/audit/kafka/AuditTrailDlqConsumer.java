package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.audit.AuditEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.audit.service.AuditDlqService;
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
public class AuditTrailDlqConsumer extends BaseKafkaConsumer<AuditEvent> {

    private final AuditDlqService auditDlqService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter dlqProcessedCounter = Counter.builder("audit_trail_dlq_events_processed_total")
            .description("Total number of audit trail DLQ events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter dlqErrorCounter = Counter.builder("audit_trail_dlq_events_error_total")
            .description("Total number of audit trail DLQ events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer dlqProcessingTimer = Timer.builder("audit_trail_dlq_processing_duration")
            .description("Time taken to process audit trail DLQ events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.audit-trail-dlq}",
        groupId = "${kafka.consumer.groups.audit-dlq}",
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
            log.info("Processing audit trail DLQ event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            AuditEvent event = deserializeEvent(record.value(), AuditEvent.class);
            validateEvent(event);

            // Process audit DLQ with immutable storage requirements
            auditDlqService.processDlqEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            dlqProcessedCounter.increment();

            log.info("Successfully processed audit trail DLQ event - EventId: {}, Type: {}, EntityId: {}, CorrelationId: {}",
                    event.getEventId(), event.getEventType(), event.getEntityId(), correlationId);

        } catch (Exception e) {
            dlqErrorCounter.increment();
            log.error("Failed to process audit trail DLQ event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            // Acknowledge DLQ events to prevent infinite loops
            acknowledgment.acknowledge();

            // Handle audit DLQ failure with regulatory compliance
            handleDlqProcessingFailure(record, e, correlationId);

        } finally {
            sample.stop(dlqProcessingTimer);
        }
    }

    @Override
    protected AuditEvent deserializeEvent(String eventData, Class<AuditEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize audit trail DLQ event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Audit trail DLQ event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Audit trail DLQ event ID cannot be null or empty");
        }
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Audit trail DLQ event type cannot be null or empty");
        }
        if (event.getEntityId() == null || event.getEntityId().trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID cannot be null or empty");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Audit trail DLQ event timestamp cannot be null");
        }
    }

    private void handleDlqProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            // Log to immutable backup audit store
            auditDlqService.logToBackupAuditStore(record, error, correlationId);

            // Create critical incident - audit failures are extremely serious
            auditDlqService.createCriticalAuditIncident(record, error, correlationId);

            // Alert compliance, legal, and security teams immediately
            auditDlqService.alertComplianceTeams(record, error, correlationId);

            // Trigger regulatory notification if required
            auditDlqService.triggerRegulatoryNotification(record, error, correlationId);

            // Initiate audit gap analysis
            auditDlqService.initiateAuditGapAnalysis(record, error, correlationId);

            // Store in cold storage for forensic analysis
            auditDlqService.storeForForensics(record, error, correlationId);

        } catch (Exception e) {
            log.error("Failed to handle audit trail DLQ processing failure - CorrelationId: {}, Error: {}",
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