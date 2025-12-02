package com.waqiti.tokenization.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.tokenization.service.TokenizationService;
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
public class TokenizationResponsesConsumer {

    private final TokenizationService tokenizationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public TokenizationResponsesConsumer(TokenizationService tokenizationService,
                                       ObjectMapper objectMapper,
                                       MeterRegistry meterRegistry) {
        this.tokenizationService = tokenizationService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("tokenization_responses_processed_total")
                .description("Total tokenization responses events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("tokenization_responses_errors_total")
                .description("Total tokenization responses errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("tokenization_responses_duration")
                .description("Tokenization responses processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "tokenization-responses",
        groupId = "tokenization-service-tokenization-responses-group",
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
    @CircuitBreaker(name = "tokenization-responses", fallbackMethod = "fallbackTokenizationResponses")
    @Transactional
    public void handleTokenizationResponses(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Tokenization responses event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing tokenization responses event: topic={}, partition={}, offset={}, key={}",
                     topic, partition, offset, record.key());

            String eventData = record.value();
            String correlationId = UUID.randomUUID().toString();

            // Process tokenization response with security validation
            tokenizationService.processTokenizationResponses(
                eventData,
                record.key(),
                correlationId
            );

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed tokenization responses event: {} with correlation: {}",
                     eventId, correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing tokenization responses event: {}", eventId, e);
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

        log.error("Tokenization responses event sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        // Tokenization failures require immediate security service escalation
        tokenizationService.escalateTokenizationResponsesFailure(
            record.value(),
            record.key(),
            exceptionMessage
        );
    }

    public void fallbackTokenizationResponses(
            ConsumerRecord<String, String> record,
            Exception ex) {
        log.warn("Tokenization responses circuit breaker activated for event: {}", record.key(), ex);

        // Fallback to manual tokenization review
        tokenizationService.routeToManualTokenizationReview(
            record.value(),
            record.key(),
            "CIRCUIT_BREAKER_ACTIVATED_TOKENIZATION_RESPONSES"
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