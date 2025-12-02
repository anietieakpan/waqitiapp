package com.waqiti.security.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.security.service.SecurityService;
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
public class CriticalSecurityAlertsDlqConsumer extends BaseDlqConsumer {

    private final SecurityService securityService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public CriticalSecurityAlertsDlqConsumer(SecurityService securityService, MeterRegistry meterRegistry) {
        super("critical-security-alerts-dlq");
        this.securityService = securityService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("critical_security_alerts_dlq_processed_total")
                .description("Total critical security alerts DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("critical_security_alerts_dlq_errors_total")
                .description("Total critical security alerts DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("critical_security_alerts_dlq_duration")
                .description("Critical security alerts DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "critical-security-alerts-dlq",
        groupId = "security-service-critical-security-alerts-dlq-group",
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
    public void handleCriticalSecurityAlertsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Critical security alerts DLQ event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing critical security alerts DLQ event: topic={}, partition={}, offset={}, key={}",
                     topic, partition, offset, record.key());

            String securityData = record.value();

            // Process critical security alerts DLQ with immediate escalation
            securityService.processCriticalSecurityAlertsDlq(
                securityData,
                record.key(),
                generateCorrelationId()
            );

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed critical security alerts DLQ event: {}", eventId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing critical security alerts DLQ event: {}", eventId, e);
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
        handleDltRecord(record, topic, exceptionMessage, "critical security alerts DLQ");
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