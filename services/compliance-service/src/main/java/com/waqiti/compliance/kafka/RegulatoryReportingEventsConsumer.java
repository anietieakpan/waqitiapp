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
public class RegulatoryReportingEventsConsumer {

    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public RegulatoryReportingEventsConsumer(ComplianceService complianceService,
                                           ObjectMapper objectMapper,
                                           MeterRegistry meterRegistry) {
        this.complianceService = complianceService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("regulatory_reporting_events_processed_total")
                .description("Total regulatory reporting events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("regulatory_reporting_events_errors_total")
                .description("Total regulatory reporting events errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("regulatory_reporting_events_duration")
                .description("Regulatory reporting events processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "regulatory-reporting-events",
        groupId = "compliance-service-regulatory-reporting-events-group",
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
    @CircuitBreaker(name = "regulatory-reporting-events", fallbackMethod = "fallbackRegulatoryReportingEvents")
    @Transactional
    public void handleRegulatoryReportingEvents(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Regulatory reporting event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing regulatory reporting event: topic={}, partition={}, offset={}, key={}",
                     topic, partition, offset, record.key());

            String eventData = record.value();
            String correlationId = UUID.randomUUID().toString();

            // Process regulatory reporting with compliance framework
            complianceService.processRegulatoryReportingEvents(
                eventData,
                record.key(),
                correlationId
            );

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed regulatory reporting event: {} with correlation: {}",
                     eventId, correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing regulatory reporting event: {}", eventId, e);
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

        log.error("Regulatory reporting event sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        // Regulatory reporting failures require immediate compliance escalation
        complianceService.escalateRegulatoryReportingFailure(
            record.value(),
            record.key(),
            exceptionMessage
        );
    }

    public void fallbackRegulatoryReportingEvents(
            ConsumerRecord<String, String> record,
            Exception ex) {
        log.warn("Regulatory reporting circuit breaker activated for event: {}", record.key(), ex);

        // Fallback to urgent manual review for regulatory compliance
        complianceService.routeToRegulatoryReview(
            record.value(),
            record.key(),
            "CIRCUIT_BREAKER_ACTIVATED_REGULATORY_REPORTING"
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