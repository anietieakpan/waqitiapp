package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.BaseDlqConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * DLQ Handler for PaymentMethodUpdateEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class PaymentMethodUpdateEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public PaymentMethodUpdateEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PaymentMethodUpdateEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PaymentMethodUpdateEventsConsumer.dlq:PaymentMethodUpdateEventsConsumer.dlq}",
        groupId = "${kafka.consumer.group-id:waqiti-services}-dlq"
    )
    public void handleDlqMessage(
            @Payload Object event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(Object event, Map<String, Object> headers) {
        try {
            log.info("DLQ: Processing payment method update recovery");
            String paymentMethodId = headers.getOrDefault("paymentMethodId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String updateType = headers.getOrDefault("updateType", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Payment method not found
            if (failureReason.contains("payment method not found")) {
                log.error("DLQ: Update for non-existent payment method: {}", paymentMethodId);
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Strategy 2: Validation failure (expired card, invalid data)
            if (failureReason.contains("validation") || failureReason.contains("expired")) {
                log.warn("DLQ: Payment method validation failed: {}", paymentMethodId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 3: Duplicate update
            if (failureReason.contains("duplicate") || failureReason.contains("no changes")) {
                log.info("DLQ: Duplicate payment method update: {}", paymentMethodId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 4: Payment provider sync failure (Stripe, etc.)
            if (failureReason.contains("provider") || failureReason.contains("Stripe")) {
                log.warn("DLQ: Payment provider sync failed, retrying: {}", paymentMethodId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 5: Fraud check failure
            if (failureReason.contains("fraud") || failureReason.contains("suspicious")) {
                log.warn("DLQ: Payment method flagged by fraud check: {}", paymentMethodId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 6: Transient
            if (failureReason.contains("timeout") || failureReason.contains("database")) {
                return DlqProcessingResult.RETRY;
            }

            log.warn("DLQ: Unknown payment method update failure: {}", failureReason);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error handling payment method update", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "PaymentMethodUpdateEventsConsumer";
    }
}
