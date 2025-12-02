package com.waqiti.kyc.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.kyc.CustomerVerificationEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.kyc.service.CustomerVerificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
public class CustomerVerificationEventsConsumer extends BaseKafkaConsumer<CustomerVerificationEvent> {

    private final CustomerVerificationService verificationService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    private final Counter processedCounter = Counter.builder("customer_verification_events_processed_total")
            .description("Total number of customer verification events processed")
            .register(metricsService.getMeterRegistry());

    private final Counter errorCounter = Counter.builder("customer_verification_events_error_total")
            .description("Total number of customer verification events failed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("customer_verification_processing_duration")
            .description("Time taken to process customer verification events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = "${kafka.topics.customer-verification-events}",
        groupId = "${kafka.consumer.groups.customer-verification}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "customer-verification-consumer", fallbackMethod = "fallbackProcessVerification")
    @Retry(name = "customer-verification-consumer")
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing customer verification event - Topic: {}, Partition: {}, Key: {}, CorrelationId: {}",
                    topic, partition, key, correlationId);

            CustomerVerificationEvent event = deserializeEvent(record.value(), CustomerVerificationEvent.class);
            validateEvent(event);

            // Process verification event with compliance tracking
            verificationService.processVerificationEvent(event, buildEventContext(record, correlationId));

            acknowledgment.acknowledge();
            processedCounter.increment();

            log.info("Successfully processed customer verification event - EventId: {}, CustomerId: {}, VerificationType: {}, CorrelationId: {}",
                    event.getEventId(), event.getCustomerId(), event.getVerificationType(), correlationId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process customer verification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                    topic, key, correlationId, e.getMessage(), e);

            handleProcessingFailure(record, e, correlationId);
            throw e; // Re-throw to trigger retry mechanism

        } finally {
            sample.stop(processingTimer);
        }
    }

    public void fallbackProcessVerification(
            ConsumerRecord<String, String> record,
            String topic,
            int partition,
            String key,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = UUID.randomUUID().toString();
        log.warn("Circuit breaker activated for customer verification event - Topic: {}, Key: {}, CorrelationId: {}, Error: {}",
                topic, key, correlationId, ex.getMessage());

        // Send to DLQ for manual processing
        verificationService.sendToDlq(record, ex, correlationId);
        acknowledgment.acknowledge();
    }

    @Override
    protected CustomerVerificationEvent deserializeEvent(String eventData, Class<CustomerVerificationEvent> eventClass) {
        try {
            return objectMapper.readValue(eventData, eventClass);
        } catch (Exception e) {
            log.error("Failed to deserialize customer verification event: {}", e.getMessage());
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @Override
    protected void validateEvent(CustomerVerificationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Customer verification event cannot be null");
        }
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getVerificationType() == null) {
            throw new IllegalArgumentException("Verification type cannot be null");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp cannot be null");
        }
    }

    private void handleProcessingFailure(ConsumerRecord<String, String> record, Exception error, String correlationId) {
        try {
            // Log to compliance audit trail
            verificationService.logComplianceFailure(record, error, correlationId);

            // Create compliance incident
            verificationService.createComplianceIncident(record, error, correlationId);

            // Alert compliance and KYC teams
            verificationService.alertComplianceTeams(record, error, correlationId);

            // Trigger manual verification review
            verificationService.triggerManualReview(record, error, correlationId);

        } catch (Exception e) {
            log.error("Failed to handle verification processing failure - CorrelationId: {}, Error: {}",
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