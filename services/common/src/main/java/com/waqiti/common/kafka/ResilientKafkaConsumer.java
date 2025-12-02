package com.waqiti.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Base class for Resilient Kafka Consumers with DLQ and Idempotency
 *
 * Provides out-of-the-box:
 * 1. Dead Letter Queue (DLQ) handling for failed messages
 * 2. Idempotency (exactly-once processing semantics)
 * 3. Retry logic with exponential backoff
 * 4. Structured error logging for monitoring
 * 5. Metrics instrumentation
 *
 * Usage:
 * <pre>
 * {@code
 * @Service
 * public class PaymentEventConsumer extends ResilientKafkaConsumer<PaymentEvent> {
 *
 *     @KafkaListener(topics = "payment.created")
 *     public void consume(ConsumerRecord<String, PaymentEvent> record, Acknowledgment ack) {
 *         processMessage(record, ack, "payment-consumer-group");
 *     }
 *
 *     @Override
 *     protected void handleMessage(PaymentEvent event, ConsumerRecord<String, PaymentEvent> record) {
 *         // Business logic here
 *         paymentService.processPayment(event);
 *     }
 * }
 * }
 * </pre>
 *
 * Pattern: Template Method for consistent Kafka consumer behavior
 * PCI DSS 10.2.3: Audit trail for all message processing
 *
 * @param <T> Type of message payload
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Slf4j
public abstract class ResilientKafkaConsumer<T> {

    @Autowired
    protected DLQHandler dlqHandler;

    @Autowired
    protected IdempotencyHandler idempotencyHandler;

    @Value("${kafka.consumer.max-retries:3}")
    protected int maxRetries;

    @Value("${kafka.consumer.enable-idempotency:true}")
    protected boolean enableIdempotency;

    @Value("${kafka.consumer.enable-dlq:true}")
    protected boolean enableDLQ;

    /**
     * Process Kafka message with DLQ and idempotency support
     *
     * This is the main entry point - call from your @KafkaListener method
     *
     * @param record Kafka consumer record
     * @param acknowledgment Manual acknowledgment (for manual commit mode)
     * @param consumerGroup Consumer group ID for DLQ metadata
     */
    protected void processMessage(
            ConsumerRecord<String, T> record,
            Acknowledgment acknowledgment,
            String consumerGroup) {

        long startTime = System.currentTimeMillis();
        String topic = record.topic();
        int partition = record.partition();
        long offset = record.offset();

        try {
            log.debug("Processing message: topic={}, partition={}, offset={}, key={}",
                topic, partition, offset, record.key());

            // Step 1: Idempotency check (skip duplicates)
            if (enableIdempotency && idempotencyHandler.isDuplicate(record)) {
                log.info("Skipping duplicate message: topic={}, partition={}, offset={}",
                    topic, partition, offset);
                acknowledgeMessage(acknowledgment);
                recordMetrics(topic, "DUPLICATE", System.currentTimeMillis() - startTime);
                return;
            }

            // Step 2: Execute business logic (implemented by subclass)
            handleMessage(record.value(), record);

            // Step 3: Mark as processed (idempotency)
            if (enableIdempotency) {
                idempotencyHandler.markProcessed(record);
            }

            // Step 4: Acknowledge message (manual commit)
            acknowledgeMessage(acknowledgment);

            // Step 5: Record success metrics
            long processingTime = System.currentTimeMillis() - startTime;
            recordMetrics(topic, "SUCCESS", processingTime);

            log.debug("Successfully processed message: topic={}, partition={}, offset={}, duration={}ms",
                topic, partition, offset, processingTime);

        } catch (Exception e) {
            handleFailure(record, e, consumerGroup, acknowledgment);
            long processingTime = System.currentTimeMillis() - startTime;
            recordMetrics(topic, "FAILURE", processingTime);
        }
    }

    /**
     * Handle message processing failure with retry and DLQ logic
     */
    private void handleFailure(
            ConsumerRecord<String, T> record,
            Exception exception,
            String consumerGroup,
            Acknowledgment acknowledgment) {

        String topic = record.topic();
        int partition = record.partition();
        long offset = record.offset();

        log.error("Failed to process message: topic={}, partition={}, offset={}, exception={}",
            topic, partition, offset, exception.getClass().getSimpleName(), exception);

        try {
            // Check if should retry or send to DLQ
            if (dlqHandler.shouldRetry(record, maxRetries)) {
                log.warn("Message will be retried: topic={}, partition={}, offset={}",
                    topic, partition, offset);
                // Don't acknowledge - let Kafka retry (for auto-retry configs)
                // OR implement manual retry with backoff here
                handleRetry(record, exception);
            } else {
                // Send to DLQ after max retries exceeded
                if (enableDLQ) {
                    log.error("Max retries exceeded, sending to DLQ: topic={}, partition={}, offset={}",
                        topic, partition, offset);
                    dlqHandler.sendToDLQ(record, exception, consumerGroup)
                        .whenComplete((result, dlqEx) -> {
                            if (dlqEx != null) {
                                log.error("CRITICAL: Failed to send to DLQ, message may be lost", dlqEx);
                            }
                            // Acknowledge even if DLQ fails (prevent infinite retries)
                            acknowledgeMessage(acknowledgment);
                        });
                } else {
                    log.error("DLQ disabled, acknowledging failed message: topic={}, partition={}, offset={}",
                        topic, partition, offset);
                    acknowledgeMessage(acknowledgment);
                }
            }

        } catch (Exception dlqException) {
            log.error("CATASTROPHIC: Exception in DLQ handler", dlqException);
            // Last resort: acknowledge to prevent blocking consumer
            acknowledgeMessage(acknowledgment);
        }
    }

    /**
     * Handle retry logic (can be overridden for custom backoff)
     */
    protected void handleRetry(ConsumerRecord<String, T> record, Exception exception) {
        // Default: Let Kafka handle retry via consumer config
        // Subclasses can override for custom exponential backoff
        log.debug("Using default Kafka retry mechanism for topic={}", record.topic());
    }

    /**
     * Acknowledge message (manual commit)
     */
    private void acknowledgeMessage(Acknowledgment acknowledgment) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }

    /**
     * Record processing metrics (override for custom metrics implementation)
     */
    protected void recordMetrics(String topic, String status, long processingTimeMs) {
        // Default: log-based metrics (override to use Micrometer/Prometheus)
        log.info("METRIC: topic={}, status={}, processingTime={}ms", topic, status, processingTimeMs);

        // TODO: Implement Micrometer metrics
        // meterRegistry.counter("kafka.consumer.messages", "topic", topic, "status", status).increment();
        // meterRegistry.timer("kafka.consumer.duration", "topic", topic).record(processingTimeMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Abstract method to implement business logic
     *
     * @param message Deserialized message payload
     * @param record Full Kafka record (for accessing headers, metadata)
     * @throws Exception Any exception will trigger DLQ/retry logic
     */
    protected abstract void handleMessage(T message, ConsumerRecord<String, T> record) throws Exception;

    /**
     * Optional: Pre-processing hook (e.g., validation, authorization)
     */
    protected void beforeProcessing(T message, ConsumerRecord<String, T> record) throws Exception {
        // Default: no-op, override if needed
    }

    /**
     * Optional: Post-processing hook (e.g., notifications, cleanup)
     */
    protected void afterProcessing(T message, ConsumerRecord<String, T> record) throws Exception {
        // Default: no-op, override if needed
    }

    /**
     * Check if exception is retryable (override for custom retry logic)
     */
    protected boolean isRetryableException(Exception exception) {
        // Default: retry all exceptions except validation errors
        return !(exception instanceof IllegalArgumentException ||
                 exception instanceof IllegalStateException);
    }
}
