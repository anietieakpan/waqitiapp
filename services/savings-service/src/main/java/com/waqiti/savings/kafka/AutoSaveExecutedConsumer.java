package com.waqiti.savings.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.savings.service.SavingsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoSaveExecutedConsumer {

    private final SavingsService savingsService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public AutoSaveExecutedConsumer(SavingsService savingsService,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.savingsService = savingsService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("auto_save_executed_processed_total")
                .description("Total auto save executed events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("auto_save_executed_errors_total")
                .description("Total auto save executed errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("auto_save_executed_duration")
                .description("Auto save executed processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "auto-save-executed",
        groupId = "savings-service-auto-save-executed-group",
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
    @CircuitBreaker(name = "auto-save-executed", fallbackMethod = "fallbackAutoSaveExecuted")
    @Transactional
    public void handleAutoSaveExecuted(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Auto save executed event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing auto save executed event: topic={}, partition={}, offset={}, key={}",
                     topic, partition, offset, record.key());

            String eventData = record.value();
            String correlationId = UUID.randomUUID().toString();

            // Process auto save execution with savings goal tracking
            savingsService.processAutoSaveExecuted(
                eventData,
                record.key(),
                correlationId
            );

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed auto save executed event: {} with correlation: {}",
                     eventId, correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing auto save executed event: {}", eventId, e);
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

        log.error("Auto save executed event sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        // Auto save failures require immediate customer service escalation
        savingsService.escalateAutoSaveExecutedFailure(
            record.value(),
            record.key(),
            exceptionMessage
        );
    }

    public void fallbackAutoSaveExecuted(
            ConsumerRecord<String, String> record,
            Exception ex) {
        log.warn("Auto save executed circuit breaker activated for event: {}", record.key(), ex);

        // Fallback to manual savings processing
        savingsService.routeToManualSavingsProcessing(
            record.value(),
            record.key(),
            "CIRCUIT_BREAKER_ACTIVATED_AUTO_SAVE_EXECUTED"
        );
    }

    private String generateEventId(ConsumerRecord<String, String> record, String topic, int partition, long offset) {
        return String.format("%s-%d-%d-%s", topic, partition, offset, record.key());
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