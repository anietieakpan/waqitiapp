package com.waqiti.account.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.account.service.AccountService;
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
public class CriticalAccountServiceDlqConsumer extends BaseDlqConsumer {

    private final AccountService accountService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public CriticalAccountServiceDlqConsumer(AccountService accountService, MeterRegistry meterRegistry) {
        super("critical-account-service-dlq");
        this.accountService = accountService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("critical_account_service_dlq_processed_total")
                .description("Total critical account service DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("critical_account_service_dlq_errors_total")
                .description("Total critical account service DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("critical_account_service_dlq_duration")
                .description("Critical account service DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "critical-account-service-dlq",
        groupId = "account-service-critical-account-service-dlq-group",
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
    public void handleCriticalAccountServiceDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Critical account service DLQ event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing critical account service DLQ event: topic={}, partition={}, offset={}, key={}",
                     topic, partition, offset, record.key());

            String accountData = record.value();

            // Process critical account service DLQ with high priority handling
            accountService.processCriticalAccountServiceDlq(
                accountData,
                record.key(),
                generateCorrelationId()
            );

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed critical account service DLQ event: {}", eventId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing critical account service DLQ event: {}", eventId, e);
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
        handleDltRecord(record, topic, exceptionMessage, "critical account service DLQ");
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