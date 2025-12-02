package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.IdempotentKafkaConsumer;
import com.waqiti.payment.service.PaymentProcessingService;
import com.waqiti.payment.dto.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * PRODUCTION-READY: Idempotent Payment Events Consumer
 *
 * This is a reference implementation showing how to use IdempotentKafkaConsumer
 * to prevent duplicate payment processing.
 *
 * Key Features:
 * - Automatic duplicate detection
 * - Safe retries on failures
 * - Manual acknowledgment for precise control
 * - Comprehensive error handling
 * - Metrics and monitoring
 *
 * CRITICAL: This pattern should be applied to ALL financial Kafka consumers
 *
 * @author Waqiti Engineering Team
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotentPaymentEventsConsumer {

    private final IdempotentKafkaConsumer idempotentConsumer;
    private final PaymentProcessingService paymentService;

    /**
     * Consume payment events with idempotency guarantees
     *
     * Configuration:
     * - Manual acknowledgment (ack only after successful processing)
     * - No auto-commit (precise control over offset commits)
     * - Concurrency: 3 (balance throughput vs. resource usage)
     */
    @KafkaListener(
            topics = "${kafka.topics.payment-events}",
            groupId = "${kafka.consumer-groups.payment-service}",
            concurrency = "3",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentEvent(
            ConsumerRecord<String, PaymentEvent> record,
            Acknowledgment acknowledgment) {

        try {
            log.info("KAFKA: Received payment event - Key: {}, Partition: {}, Offset: {}",
                    record.key(), record.partition(), record.offset());

            // Process with idempotency guarantees
            idempotentConsumer.processIdempotentlyVoid(
                    record,
                    event -> {
                        // This block will only execute ONCE per unique message
                        log.info("Processing payment event: {}", event.getPaymentId());
                        paymentService.processPaymentEvent(event);
                        log.info("Payment event processed successfully: {}", event.getPaymentId());
                    },
                    "payment-events-consumer"
            );

            // Acknowledge message ONLY after successful processing
            acknowledgment.acknowledge();

            log.debug("KAFKA: Payment event acknowledged - Offset: {}", record.offset());

        } catch (Exception e) {
            log.error("KAFKA ERROR: Failed to process payment event - " +
                    "Partition: {}, Offset: {}, Will retry after backoff",
                    record.partition(), record.offset(), e);

            // DON'T acknowledge - let Kafka retry
            // Message will be reprocessed after backoff period
            throw e;
        }
    }
}
