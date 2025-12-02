package com.waqiti.saga.kafka;

import com.waqiti.common.saga.SagaStepEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL P0: Saga Step Producer
 *
 * Publishes SagaStepEvent to participant services (ledger, wallet, payment) to execute
 * saga steps as part of distributed transaction orchestration.
 *
 * This was identified as a CRITICAL P0 blocker - saga orchestration was completely
 * broken without this producer. Participant services were never receiving step
 * execution commands.
 *
 * Topics:
 * - ledger-saga-steps: Ledger service operations (record entries, compensate)
 * - wallet-saga-steps: Wallet service operations (debit, credit, reserve)
 * - payment-saga-steps: Payment service operations (process, refund)
 *
 * Features:
 * - Automatic retry with exponential backoff
 * - Comprehensive error handling
 * - Metrics and monitoring
 * - Distributed tracing support
 * - Idempotency headers
 * - Partition key for ordering guarantees
 *
 * @author Waqiti Engineering Team - Production Fix
 * @since 2.0.0
 * @priority P0-CRITICAL
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SagaStepProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter ledgerStepPublishedCounter;
    private Counter walletStepPublishedCounter;
    private Counter paymentStepPublishedCounter;
    private Counter publishFailureCounter;
    private Timer publishLatencyTimer;

    @PostConstruct
    public void initMetrics() {
        ledgerStepPublishedCounter = meterRegistry.counter("saga.step.published.ledger");
        walletStepPublishedCounter = meterRegistry.counter("saga.step.published.wallet");
        paymentStepPublishedCounter = meterRegistry.counter("saga.step.published.payment");
        publishFailureCounter = meterRegistry.counter("saga.step.publish.failure");
        publishLatencyTimer = meterRegistry.timer("saga.step.publish.latency");
    }

    /**
     * Publish saga step to ledger service
     *
     * @param event Saga step event containing execution instructions
     * @return CompletableFuture with send result
     */
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        retryFor = {Exception.class}
    )
    public CompletableFuture<SendResult<String, Object>> publishLedgerStep(SagaStepEvent event) {
        return publishLatencyTimer.record(() -> {
            log.info("Publishing ledger saga step: sagaId={}, step={}, operation={}",
                    event.getSagaId(), event.getStepName(), event.getOperation());

            try {
                CompletableFuture<SendResult<String, Object>> future =
                        sendToTopic("ledger-saga-steps", event);

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ledger saga step: sagaId={}, error={}",
                                event.getSagaId(), ex.getMessage(), ex);
                        publishFailureCounter.increment();
                    } else {
                        ledgerStepPublishedCounter.increment();
                        log.info("Ledger saga step published: sagaId={}, partition={}, offset={}",
                                event.getSagaId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });

                return future;

            } catch (Exception e) {
                log.error("Error publishing ledger saga step: sagaId={}, error={}",
                        event.getSagaId(), e.getMessage(), e);
                publishFailureCounter.increment();
                throw new RuntimeException("Failed to publish ledger saga step", e);
            }
        });
    }

    /**
     * Publish saga step to wallet service
     *
     * @param event Saga step event containing execution instructions
     * @return CompletableFuture with send result
     */
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        retryFor = {Exception.class}
    )
    public CompletableFuture<SendResult<String, Object>> publishWalletStep(SagaStepEvent event) {
        return publishLatencyTimer.record(() -> {
            log.info("Publishing wallet saga step: sagaId={}, step={}, operation={}",
                    event.getSagaId(), event.getStepName(), event.getOperation());

            try {
                CompletableFuture<SendResult<String, Object>> future =
                        sendToTopic("wallet-saga-steps", event);

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish wallet saga step: sagaId={}, error={}",
                                event.getSagaId(), ex.getMessage(), ex);
                        publishFailureCounter.increment();
                    } else {
                        walletStepPublishedCounter.increment();
                        log.info("Wallet saga step published: sagaId={}, partition={}, offset={}",
                                event.getSagaId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });

                return future;

            } catch (Exception e) {
                log.error("Error publishing wallet saga step: sagaId={}, error={}",
                        event.getSagaId(), e.getMessage(), e);
                publishFailureCounter.increment();
                throw new RuntimeException("Failed to publish wallet saga step", e);
            }
        });
    }

    /**
     * Publish saga step to payment service
     *
     * @param event Saga step event containing execution instructions
     * @return CompletableFuture with send result
     */
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        retryFor = {Exception.class}
    )
    public CompletableFuture<SendResult<String, Object>> publishPaymentStep(SagaStepEvent event) {
        return publishLatencyTimer.record(() -> {
            log.info("Publishing payment saga step: sagaId={}, step={}, operation={}",
                    event.getSagaId(), event.getStepName(), event.getOperation());

            try {
                CompletableFuture<SendResult<String, Object>> future =
                        sendToTopic("payment-saga-steps", event);

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment saga step: sagaId={}, error={}",
                                event.getSagaId(), ex.getMessage(), ex);
                        publishFailureCounter.increment();
                    } else {
                        paymentStepPublishedCounter.increment();
                        log.info("Payment saga step published: sagaId={}, partition={}, offset={}",
                                event.getSagaId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });

                return future;

            } catch (Exception e) {
                log.error("Error publishing payment saga step: sagaId={}, error={}",
                        event.getSagaId(), e.getMessage(), e);
                publishFailureCounter.increment();
                throw new RuntimeException("Failed to publish payment saga step", e);
            }
        });
    }

    /**
     * Generic method to route saga step to appropriate service
     *
     * Routes based on serviceName field in SagaStepEvent:
     * - "ledger-service" -> ledger-saga-steps
     * - "wallet-service" -> wallet-saga-steps
     * - "payment-service" -> payment-saga-steps
     *
     * @param event Saga step event
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, Object>> publishSagaStep(SagaStepEvent event) {
        String serviceName = event.getServiceName();

        if (serviceName == null) {
            throw new IllegalArgumentException("ServiceName is required in SagaStepEvent");
        }

        switch (serviceName.toLowerCase()) {
            case "ledger-service":
            case "ledger":
                return publishLedgerStep(event);

            case "wallet-service":
            case "wallet":
                return publishWalletStep(event);

            case "payment-service":
            case "payment":
                return publishPaymentStep(event);

            default:
                throw new IllegalArgumentException("Unknown service name: " + serviceName);
        }
    }

    /**
     * Publish saga step with timeout
     *
     * @param event Saga step event
     * @param timeoutSeconds Timeout in seconds
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, Object>> publishSagaStepWithTimeout(
            SagaStepEvent event, long timeoutSeconds) {

        CompletableFuture<SendResult<String, Object>> future = publishSagaStep(event);

        return future.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.error("Saga step publish timeout: sagaId={}, service={}, timeout={}s",
                            event.getSagaId(), event.getServiceName(), timeoutSeconds);
                    publishFailureCounter.increment();
                    throw new RuntimeException("Saga step publish timeout", ex);
                });
    }

    /**
     * Send event to Kafka topic with proper headers and partitioning
     *
     * @param topic Kafka topic name
     * @param event Saga step event
     * @return CompletableFuture with send result
     */
    private CompletableFuture<SendResult<String, Object>> sendToTopic(
            String topic, SagaStepEvent event) {

        // Use sagaId as partition key for ordering guarantees
        // All steps for the same saga will go to the same partition
        String partitionKey = event.getSagaId();

        ProducerRecord<String, Object> record = new ProducerRecord<>(
                topic,
                partitionKey,
                event
        );

        // Add comprehensive headers for tracing, monitoring, and debugging
        addHeaders(record, event);

        log.debug("Sending saga step to topic: {}, sagaId={}, step={}, partition key={}",
                topic, event.getSagaId(), event.getStepName(), partitionKey);

        return kafkaTemplate.send(record);
    }

    /**
     * Add Kafka headers for distributed tracing and metadata
     */
    private void addHeaders(ProducerRecord<String, Object> record, SagaStepEvent event) {
        // Event metadata
        record.headers().add("event-type", "saga-step".getBytes());
        record.headers().add("event-version", "1.0".getBytes());
        record.headers().add("source-service", "saga-orchestration-service".getBytes());

        // Saga metadata
        record.headers().add("saga-id", event.getSagaId().getBytes());
        record.headers().add("saga-type", event.getSagaType().getBytes());
        record.headers().add("step-name", event.getStepName().getBytes());
        record.headers().add("service-name", event.getServiceName().getBytes());
        record.headers().add("operation", event.getOperation().getBytes());

        // Correlation and tracing
        String correlationId = event.getCorrelationId() != null ?
                event.getCorrelationId() : UUID.randomUUID().toString();
        record.headers().add("correlation-id", correlationId.getBytes());

        if (event.getTransactionId() != null) {
            record.headers().add("transaction-id", event.getTransactionId().getBytes());
        }

        // Retry metadata
        record.headers().add("attempt-number", String.valueOf(event.getAttemptNumber()).getBytes());
        record.headers().add("max-attempts", String.valueOf(event.getMaxAttempts()).getBytes());

        // Compensation flag
        if (event.isCompensation()) {
            record.headers().add("compensation", "true".getBytes());
        }

        // Timestamp
        record.headers().add("timestamp",
                (event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now())
                        .toString().getBytes());

        // Idempotency key (for deduplication)
        String idempotencyKey = generateIdempotencyKey(event);
        record.headers().add("idempotency-key", idempotencyKey.getBytes());
    }

    /**
     * Generate idempotency key for deduplication
     */
    private String generateIdempotencyKey(SagaStepEvent event) {
        return String.format("%s:%s:%s:%d",
                event.getSagaId(),
                event.getStepName(),
                event.getOperation(),
                event.getAttemptNumber()
        );
    }

    /**
     * Build saga step event with builder pattern
     *
     * @param sagaId Saga instance ID
     * @param sagaType Type of saga
     * @param stepName Step name to execute
     * @param serviceName Target service
     * @param operation Operation to perform
     * @param data Step-specific data
     * @return SagaStepEvent
     */
    public static SagaStepEvent buildSagaStepEvent(
            String sagaId,
            String sagaType,
            String stepName,
            String serviceName,
            String operation,
            Map<String, Object> data) {

        return SagaStepEvent.builder()
                .sagaId(sagaId)
                .sagaType(sagaType)
                .stepName(stepName)
                .serviceName(serviceName)
                .operation(operation)
                .data(data)
                .compensation(false)
                .attemptNumber(1)
                .maxAttempts(3)
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Build compensation saga step event
     *
     * @param originalEvent Original saga step event
     * @param compensationData Data for compensation
     * @return Compensation saga step event
     */
    public static SagaStepEvent buildCompensationEvent(
            SagaStepEvent originalEvent,
            Map<String, Object> compensationData) {

        return SagaStepEvent.builder()
                .sagaId(originalEvent.getSagaId())
                .sagaType(originalEvent.getSagaType())
                .stepName(originalEvent.getStepName() + "_COMPENSATE")
                .serviceName(originalEvent.getServiceName())
                .operation("COMPENSATE")
                .data(compensationData)
                .compensation(true)
                .attemptNumber(1)
                .maxAttempts(3)
                .timestamp(LocalDateTime.now())
                .correlationId(originalEvent.getCorrelationId())
                .transactionId(originalEvent.getTransactionId())
                .build();
    }

    /**
     * Get metrics for monitoring
     */
    public Map<String, Object> getMetrics() {
        return Map.of(
                "ledgerStepsPublished", ledgerStepPublishedCounter.count(),
                "walletStepsPublished", walletStepPublishedCounter.count(),
                "paymentStepsPublished", paymentStepPublishedCounter.count(),
                "publishFailures", publishFailureCounter.count(),
                "avgPublishLatencyMs", publishLatencyTimer.mean(TimeUnit.MILLISECONDS)
        );
    }
}
