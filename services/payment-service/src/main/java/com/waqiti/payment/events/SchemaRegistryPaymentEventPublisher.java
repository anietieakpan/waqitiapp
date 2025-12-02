package com.waqiti.payment.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture; /**
 * Payment Event Publisher with Schema Registry Support
 * 
 * This is an enhanced version of the existing PaymentEventPublisher that adds
 * support for Avro schema serialization via the Schema Registry.
 * 
 * When schema registry is enabled, this will use Avro serialization for
 * better type safety and schema evolution support.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kafka.schema-registry.enabled", havingValue = "true")
public class SchemaRegistryPaymentEventPublisher {

    @Qualifier("avroKafkaTemplate")
    private final KafkaTemplate<String, Object> avroKafkaTemplate;

    @Qualifier("jsonKafkaTemplate") 
    private final KafkaTemplate<String, Object> jsonKafkaTemplate;

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    /**
     * Publish payment event using Avro schema
     */
    public CompletableFuture<SendResult<String, Object>> publishPaymentEventAvro(PaymentEventAvro event) {
        log.info("Publishing payment event with Avro schema: paymentId={}, eventType={}", 
                event.getPaymentId(), event.getEventType());

        try {
            return avroKafkaTemplate.send(PAYMENT_EVENTS_TOPIC, event.getPaymentId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Payment event published successfully: offset={}", 
                                    result.getRecordMetadata().offset());
                        } else {
                            log.error("Failed to publish payment event", ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing payment event with Avro schema", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish payment event using JSON schema (backward compatibility)
     */
    public CompletableFuture<SendResult<String, Object>> publishPaymentEventJson(Object event, String paymentId) {
        log.info("Publishing payment event with JSON serialization: paymentId={}", paymentId);

        try {
            return jsonKafkaTemplate.send(PAYMENT_EVENTS_TOPIC, paymentId, event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Payment event (JSON) published successfully: offset={}", 
                                    result.getRecordMetadata().offset());
                        } else {
                            log.error("Failed to publish payment event (JSON)", ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing payment event with JSON serialization", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Helper method to create PaymentEventAvro from domain objects
     */
    public PaymentEventAvro createPaymentEventAvro(String eventId, String eventType, String paymentId, 
                                                   String userId, String amountValue, String currency) {
        return PaymentEventAvro.newBuilder()
                .setEventId(eventId)
                .setEventType(PaymentEventTypeAvro.valueOf(eventType))
                .setPaymentId(paymentId)
                .setUserId(userId)
                .setAmount(MonetaryAmountAvro.newBuilder()
                        .setValue(amountValue)
                        .setCurrency(currency)
                        .build())
                .setPaymentMethod(PaymentMethodAvro.WALLET) // Default
                .setStatus(PaymentStatusAvro.PENDING) // Default
                .setTimestamp(System.currentTimeMillis())
                .setVersion("1.0")
                .build();
    }

    // Note: The actual PaymentEventAvro, PaymentEventTypeAvro, etc. classes would be
    // generated from the Avro schema files using the Avro Maven plugin
}
