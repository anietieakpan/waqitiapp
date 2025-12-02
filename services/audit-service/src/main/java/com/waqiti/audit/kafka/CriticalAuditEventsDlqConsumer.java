package com.waqiti.audit.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.audit.service.AuditService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CriticalAuditEventsDlqConsumer extends BaseDlqConsumer {

    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public CriticalAuditEventsDlqConsumer(AuditService auditService, MeterRegistry meterRegistry) {
        super("critical-audit-events-dlq");
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("critical_audit_events_dlq_processed_total")
                .description("Total critical audit events DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("critical_audit_events_dlq_errors_total")
                .description("Total critical audit events DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("critical_audit_events_dlq_duration")
                .description("Critical audit events DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "critical-audit-events-dlq",
        groupId = "audit-service-critical-audit-events-dlq-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleCriticalAuditEventsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Critical audit events DLQ event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing critical audit events DLQ event: topic={}, partition={}, offset={}, key={}",
                     topic, partition, offset, record.key());

            String auditData = record.value();

            // Process critical audit events DLQ with compliance priority
            auditService.processCriticalAuditEventsDlq(
                auditData,
                record.key(),
                generateCorrelationId()
            );

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed critical audit events DLQ event: {}", eventId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing critical audit events DLQ event: {}", eventId, e);
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        handleDltRecord(record, topic, exceptionMessage, "critical audit events DLQ");
    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
    }
}