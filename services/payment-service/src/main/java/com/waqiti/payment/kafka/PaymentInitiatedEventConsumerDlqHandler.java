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
 * DLQ Handler for PaymentInitiatedEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class PaymentInitiatedEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public PaymentInitiatedEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PaymentInitiatedEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PaymentInitiatedEventConsumer.dlq:PaymentInitiatedEventConsumer.dlq}",
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
            log.warn("DLQ: Payment initiated recovery");
            String paymentId = headers.getOrDefault("paymentId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String amount = headers.getOrDefault("amount", "").toString();
            String paymentMethod = headers.getOrDefault("paymentMethod", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Payment state tracking failure
            if (failureReason.contains("state") || failureReason.contains("tracking")) {
                log.warn("DLQ: Payment state tracking failed: paymentId={}", paymentId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 2: Fraud check timeout
            if (failureReason.contains("fraud") || failureReason.contains("risk")) {
                log.error("DLQ: Payment fraud check failed: paymentId={}", paymentId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Notification failure (non-critical)
            if (failureReason.contains("notification")) {
                log.info("DLQ: Payment notification failed (non-critical): paymentId={}", paymentId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 4: Analytics/tracking failure (non-critical)
            if (failureReason.contains("analytics") || failureReason.contains("tracking")) {
                log.info("DLQ: Payment analytics failed (non-critical): paymentId={}", paymentId);
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 5: Duplicate event
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Transient
            if (failureReason.contains("timeout")) {
                return DlqProcessingResult.RETRY;
            }

            log.warn("DLQ: Payment initiated failed: paymentId={}, userId={}", paymentId, userId);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error in payment initiated handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "PaymentInitiatedEventConsumer";
    }
}
