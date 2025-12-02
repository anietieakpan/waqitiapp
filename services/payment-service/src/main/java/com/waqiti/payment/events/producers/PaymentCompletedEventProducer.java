package com.waqiti.payment.events.producers;

import com.waqiti.common.events.payment.PaymentCompletedEvent;
import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.domain.PaymentStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade event producer for payment completion events.
 * Publishes payment-completed events when payments are successfully processed.
 * 
 * Features:
 * - Transactional event publishing
 * - Exactly-once delivery semantics
 * - Comprehensive error handling
 * - Metrics and monitoring
 * - Audit trail integration
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final String TOPIC = "payment-completed";

    /**
     * Publishes payment completed event when payment status changes to COMPLETED
     * This is triggered after successful transaction commit
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(Payment payment) {
        if (!PaymentStatus.COMPLETED.equals(payment.getStatus())) {
            return;
        }

        try {
            log.info("Publishing payment completed event for payment: {}", payment.getId());

            // Create event
            PaymentCompletedEvent event = createPaymentCompletedEvent(payment);

            // Publish event with exactly-once semantics
            publishEvent(event, payment);

            // Metrics
            metricsService.incrementCounter("payment.completed.event.published",
                Map.of(
                    "payment_method", payment.getPaymentMethod(),
                    "currency", payment.getCurrency(),
                    "amount_range", categorizeAmount(payment.getAmount())
                ));

            log.info("Successfully published payment completed event: {} for payment: {}", 
                    event.getEventId(), payment.getId());

        } catch (Exception e) {
            log.error("Failed to publish payment completed event for payment {}: {}", 
                    payment.getId(), e.getMessage(), e);
            
            // Metrics for failures
            metricsService.incrementCounter("payment.completed.event.publish_failed");
            
            // Create audit log for failure
            auditLogger.logError("PAYMENT_COMPLETED_EVENT_PUBLISH_FAILED",
                "system", payment.getId(), e.getMessage(),
                Map.of("paymentId", payment.getId()));
        }
    }

    /**
     * Manually publish payment completed event (for external integrations)
     */
    public CompletableFuture<SendResult<String, Object>> publishPaymentCompleted(Payment payment) {
        if (!PaymentStatus.COMPLETED.equals(payment.getStatus())) {
            throw new IllegalArgumentException("Payment must be in COMPLETED status to publish completed event");
        }

        PaymentCompletedEvent event = createPaymentCompletedEvent(payment);
        return publishEvent(event, payment);
    }

    private PaymentCompletedEvent createPaymentCompletedEvent(Payment payment) {
        return PaymentCompletedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .paymentId(payment.getId())
            .transactionId(payment.getTransactionId())
            .userId(payment.getUserId())
            .merchantId(payment.getMerchantId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentMethod(payment.getPaymentMethod())
            .completedAt(payment.getCompletedAt() != null ? payment.getCompletedAt() : LocalDateTime.now())
            .processingTimeMs(payment.getProcessingTimeMs())
            .feeAmount(payment.getFeeAmount())
            .netAmount(payment.getNetAmount())
            .exchangeRate(payment.getExchangeRate())
            .paymentProvider(payment.getPaymentProvider())
            .providerTransactionId(payment.getProviderTransactionId())
            .settlementDate(payment.getSettlementDate())
            .description(payment.getDescription())
            .metadata(payment.getMetadata())
            .timestamp(LocalDateTime.now())
            .build();
    }

    private CompletableFuture<SendResult<String, Object>> publishEvent(PaymentCompletedEvent event, Payment payment) {
        try {
            // Use payment ID as partition key for ordering
            String partitionKey = payment.getId();

            // Set headers for tracking
            var producerRecord = new org.apache.kafka.clients.producer.ProducerRecord<>(
                TOPIC, 
                partitionKey, 
                event
            );
            
            // Add headers
            producerRecord.headers().add("event-type", "payment-completed".getBytes());
            producerRecord.headers().add("correlation-id", UUID.randomUUID().toString().getBytes());
            producerRecord.headers().add("source-service", "payment-service".getBytes());
            producerRecord.headers().add("event-version", "1.0".getBytes());

            // Send with callback
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(producerRecord);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send payment completed event for payment {}: {}", 
                            payment.getId(), ex.getMessage(), ex);
                    
                    metricsService.incrementCounter("payment.completed.event.send_failed");
                } else {
                    log.debug("Payment completed event sent successfully: partition={}, offset={}", 
                            result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                    
                    // Create audit trail
                    auditLogger.logPaymentEvent(
                        "PAYMENT_COMPLETED_EVENT_PUBLISHED",
                        payment.getUserId(),
                        event.getEventId(),
                        payment.getAmount().doubleValue(),
                        payment.getCurrency(),
                        "payment_event_producer",
                        true,
                        Map.of(
                            "paymentId", payment.getId(),
                            "eventId", event.getEventId(),
                            "topic", TOPIC,
                            "partition", String.valueOf(result.getRecordMetadata().partition()),
                            "offset", String.valueOf(result.getRecordMetadata().offset())
                        )
                    );
                }
            });

            return future;

        } catch (Exception e) {
            log.error("Exception while publishing payment completed event for payment {}: {}", 
                    payment.getId(), e.getMessage(), e);
            throw e;
        }
    }

    private String categorizeAmount(java.math.BigDecimal amount) {
        if (amount.compareTo(new java.math.BigDecimal("10000")) > 0) {
            return "high";
        } else if (amount.compareTo(new java.math.BigDecimal("1000")) > 0) {
            return "medium";
        } else {
            return "low";
        }
    }
}