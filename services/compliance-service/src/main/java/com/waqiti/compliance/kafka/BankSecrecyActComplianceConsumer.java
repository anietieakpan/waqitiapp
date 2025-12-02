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
public class BankSecrecyActComplianceConsumer {

    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public BankSecrecyActComplianceConsumer(ComplianceService complianceService,
                                          ObjectMapper objectMapper,
                                          MeterRegistry meterRegistry) {
        this.complianceService = complianceService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("bank_secrecy_act_compliance_processed_total")
                .description("Total Bank Secrecy Act compliance events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("bank_secrecy_act_compliance_errors_total")
                .description("Total Bank Secrecy Act compliance errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("bank_secrecy_act_compliance_duration")
                .description("Bank Secrecy Act compliance processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "bank-secrecy-act-compliance-events",
        groupId = "compliance-service-bank-secrecy-act-compliance-group",
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
    @CircuitBreaker(name = "bank-secrecy-act-compliance", fallbackMethod = "fallbackBankSecrecyActCompliance")
    @Transactional
    public void handleBankSecrecyActCompliance(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Bank Secrecy Act compliance event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing Bank Secrecy Act compliance event: topic={}, partition={}, offset={}, key={}",
                     topic, partition, offset, record.key());

            String eventData = record.value();
            String correlationId = UUID.randomUUID().toString();

            // Process BSA compliance with recordkeeping and reporting requirements
            complianceService.processBankSecrecyActCompliance(
                eventData,
                record.key(),
                correlationId
            );

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed Bank Secrecy Act compliance event: {} with correlation: {}",
                     eventId, correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing Bank Secrecy Act compliance event: {}", eventId, e);
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

        log.error("Bank Secrecy Act compliance event sent to DLT: topic={}, key={}, exception={}",
                  topic, record.key(), exceptionMessage);

        // BSA compliance failures require immediate federal compliance escalation
        complianceService.escalateBankSecrecyActComplianceFailure(
            record.value(),
            record.key(),
            exceptionMessage
        );
    }

    public void fallbackBankSecrecyActCompliance(
            ConsumerRecord<String, String> record,
            Exception ex) {
        log.warn("Bank Secrecy Act compliance circuit breaker activated for event: {}", record.key(), ex);

        // Fallback to immediate manual review for BSA compliance
        complianceService.routeToBsaManualReview(
            record.value(),
            record.key(),
            "CIRCUIT_BREAKER_ACTIVATED_BSA"
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