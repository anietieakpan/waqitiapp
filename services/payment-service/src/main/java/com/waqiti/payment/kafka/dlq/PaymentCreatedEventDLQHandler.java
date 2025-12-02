package com.waqiti.payment.kafka.dlq;

import com.waqiti.common.kafka.dlq.AbstractDLQHandler;
import com.waqiti.common.kafka.dlq.model.*;
import com.waqiti.payment.kafka.PaymentEventsConsumer;
import com.waqiti.payment.model.events.PaymentCreatedEvent;
import com.waqiti.payment.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * DLQ Handler for Payment Created Events
 *
 * Handles failed payment creation events that could not be processed
 * by the primary consumer and were moved to the DLQ.
 *
 * Recovery Strategies:
 * - TRANSIENT_NETWORK: Retry payment processing
 * - IDEMPOTENCY_VIOLATION: Skip (payment already processed)
 * - BUSINESS_RULE_VIOLATION: Refund customer + notify
 * - DATA_VALIDATION_ERROR: Manual intervention required
 */
@Slf4j
@Component
public class PaymentCreatedEventDLQHandler extends AbstractDLQHandler<PaymentCreatedEvent> {

    private final PaymentEventsConsumer primaryConsumer;
    private final PaymentService paymentService;

    public PaymentCreatedEventDLQHandler(
            PaymentEventsConsumer primaryConsumer,
            PaymentService paymentService
    ) {
        this.primaryConsumer = primaryConsumer;
        this.paymentService = paymentService;
    }

    @KafkaListener(
            topics = "payment-created-events.dlq",
            groupId = "payment-service-dlq-group",
            containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void consumeDLQ(ConsumerRecord<String, PaymentCreatedEvent> record) {
        log.info("📨 DLQ message received: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        String originalTopic = "payment-created-events";
        String exceptionMessage = extractHeader(record, "kafka_dlt-exception-message");
        String stackTrace = extractHeader(record, "kafka_dlt-exception-stacktrace");

        handleDLQMessage(record.value(), originalTopic, exceptionMessage, stackTrace);
    }

    @Override
    protected Class<PaymentCreatedEvent> getEventType() {
        return PaymentCreatedEvent.class;
    }

    @Override
    protected String getServiceName() {
        return "payment-service";
    }

    @Override
    protected FailureCategory categorizeFailure(String exceptionMessage, String stackTrace) {
        String lowerMessage = exceptionMessage.toLowerCase();

        // Network/timeout errors
        if (lowerMessage.contains("timeout") ||
                lowerMessage.contains("connection refused") ||
                lowerMessage.contains("socket") ||
                lowerMessage.contains("unavailable")) {
            return FailureCategory.TRANSIENT_NETWORK;
        }

        // Idempotency violations
        if (lowerMessage.contains("duplicate") ||
                lowerMessage.contains("already exists") ||
                lowerMessage.contains("unique constraint") ||
                lowerMessage.contains("idempotency")) {
            return FailureCategory.IDEMPOTENCY_VIOLATION;
        }

        // Business rule violations
        if (lowerMessage.contains("insufficient funds") ||
                lowerMessage.contains("balance too low") ||
                lowerMessage.contains("limit exceeded") ||
                lowerMessage.contains("account suspended")) {
            return FailureCategory.BUSINESS_RULE_VIOLATION;
        }

        // Data validation errors
        if (lowerMessage.contains("validation") ||
                lowerMessage.contains("invalid") ||
                lowerMessage.contains("null") ||
                lowerMessage.contains("missing required")) {
            return FailureCategory.DATA_VALIDATION_ERROR;
        }

        // Serialization/deserialization errors
        if (lowerMessage.contains("serialization") ||
                lowerMessage.contains("deserialization") ||
                lowerMessage.contains("json") ||
                lowerMessage.contains("avro")) {
            return FailureCategory.SCHEMA_COMPATIBILITY;
        }

        return FailureCategory.UNKNOWN_ERROR;
    }

    @Override
    protected RecoveryStrategy determineRecoveryStrategy(FailureCategory category, PaymentCreatedEvent event) {
        return switch (category) {
            case TRANSIENT_NETWORK -> RecoveryStrategy.RETRY;
            case IDEMPOTENCY_VIOLATION -> RecoveryStrategy.SKIP; // Already processed
            case BUSINESS_RULE_VIOLATION -> RecoveryStrategy.COMPENSATE; // Refund
            case DATA_VALIDATION_ERROR -> RecoveryStrategy.MANUAL_INTERVENTION;
            case SCHEMA_COMPATIBILITY -> RecoveryStrategy.SCHEMA_MIGRATION;
            case POISON_PILL -> RecoveryStrategy.QUARANTINE;
            default -> RecoveryStrategy.MANUAL_INTERVENTION;
        };
    }

    @Override
    protected boolean retryProcessing(PaymentCreatedEvent event) {
        try {
            log.info("♻️ Retrying payment creation: paymentId={}, amount={}",
                    event.getPaymentId(), event.getAmount());

            // Delegate to primary consumer for reprocessing
            primaryConsumer.handlePaymentCreated(event);

            log.info("✅ Retry successful: paymentId={}", event.getPaymentId());
            return true;

        } catch (Exception e) {
            log.error("❌ Retry failed: paymentId={}, error={}",
                    event.getPaymentId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    protected boolean executeCompensation(PaymentCreatedEvent event) {
        try {
            log.info("🔄 Executing compensation for failed payment: paymentId={}", event.getPaymentId());

            // Check if payment was actually created (partial success)
            boolean paymentExists = paymentService.paymentExists(event.getPaymentId());

            if (paymentExists) {
                // Payment was created but downstream processing failed
                // Refund the customer
                UUID refundId = paymentService.initiateRefund(
                        event.getPaymentId(),
                        event.getAmount(),
                        "Automatic refund due to processing failure"
                );

                log.info("✅ Compensation successful: refundId={}", refundId);

                // Send notification to customer
                paymentService.notifyCustomerOfRefund(event.getUserId(), event.getPaymentId(), refundId);

                return true;
            } else {
                // Payment was never created, no compensation needed
                log.info("ℹ️ Payment does not exist, no compensation needed");
                return true;
            }

        } catch (Exception e) {
            log.error("❌ Compensation failed: paymentId={}, error={}",
                    event.getPaymentId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    protected Map<String, Object> extractEventMetadata(PaymentCreatedEvent event) {
        return Map.of(
                "payment_id", event.getPaymentId() != null ? event.getPaymentId().toString() : "UNKNOWN",
                "user_id", event.getUserId() != null ? event.getUserId().toString() : "UNKNOWN",
                "amount", event.getAmount() != null ? event.getAmount().toString() : "0",
                "currency", event.getCurrency() != null ? event.getCurrency() : "USD",
                "payment_method", event.getPaymentMethod() != null ? event.getPaymentMethod() : "UNKNOWN",
                "merchant_id", event.getMerchantId() != null ? event.getMerchantId().toString() : "NONE"
        );
    }

    /**
     * Extract header from Kafka record
     */
    private String extractHeader(ConsumerRecord<String, PaymentCreatedEvent> record, String headerName) {
        var header = record.headers().lastHeader(headerName);
        return header != null ? new String(header.value()) : "Unknown";
    }
}
