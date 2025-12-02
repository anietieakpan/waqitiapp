package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.service.ComplianceService;
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
public class OfacSanctionsScreeningConsumer {

    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public OfacSanctionsScreeningConsumer(ComplianceService complianceService,
                                        ObjectMapper objectMapper,
                                        MeterRegistry meterRegistry) {
        this.complianceService = complianceService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("ofac_sanctions_screening_processed_total")
                .description("Total OFAC sanctions screening events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("ofac_sanctions_screening_errors_total")
                .description("Total OFAC sanctions screening errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("ofac_sanctions_screening_duration")
                .description("OFAC sanctions screening processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "ofac-sanctions-screening-events",
        groupId = "compliance-service-ofac-sanctions-screening-group",
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
    @CircuitBreaker(name = "ofac-sanctions-screening", fallbackMethod = "fallbackOfacSanctionsScreening")
    @Transactional
    public void handleOfacSanctionsScreening(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("OFAC sanctions screening event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing OFAC sanctions screening event: topic={}, partition={}, offset={}, key={}",
                     topic, partition, offset, record.key());

            String eventData = record.value();
            String correlationId = UUID.randomUUID().toString();

            // Process OFAC sanctions screening with national security compliance
            complianceService.processOfacSanctionsScreening(
                eventData,
                record.key(),
                correlationId
            );

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed OFAC sanctions screening event: {} with correlation: {}",
                     eventId, correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing OFAC sanctions screening event: {}", eventId, e);
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

        log.error("OFAC sanctions screening event sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        // OFAC screening failures require immediate national security escalation
        complianceService.escalateOfacSanctionsScreeningFailure(
            record.value(),
            record.key(),
            exceptionMessage
        );
    }

    public void fallbackOfacSanctionsScreening(
            ConsumerRecord<String, String> record,
            Exception ex) {
        log.warn("OFAC sanctions screening circuit breaker activated for event: {}", record.key(), ex);

        // Fallback to immediate manual review for OFAC compliance
        complianceService.routeToOfacManualReview(
            record.value(),
            record.key(),
            "CIRCUIT_BREAKER_ACTIVATED_OFAC"
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