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
 * DLQ Handler for P2PPaymentEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class P2PPaymentEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public P2PPaymentEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("P2PPaymentEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.P2PPaymentEventConsumer.dlq:P2PPaymentEventConsumer.dlq}",
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
            log.warn("DLQ: P2P payment recovery");
            String paymentId = headers.getOrDefault("paymentId", "").toString();
            String senderId = headers.getOrDefault("senderId", "").toString();
            String receiverId = headers.getOrDefault("receiverId", "").toString();
            String amount = headers.getOrDefault("amount", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Strategy 1: Sender debit succeeded but receiver credit failed (CRITICAL)
            if (failureReason.contains("credit failed") || failureReason.contains("receiver failed")) {
                log.error("DLQ: P2P partial completion: paymentId={}", paymentId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 2: Insufficient balance
            if (failureReason.contains("insufficient")) {
                log.warn("DLQ: P2P insufficient balance: senderId={}", senderId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 3: Receiver account not found/inactive
            if (failureReason.contains("receiver") || failureReason.contains("not found")) {
                log.warn("DLQ: P2P receiver issue: receiverId={}", receiverId);
                return DlqProcessingResult.RETRY;
            }

            // Strategy 4: P2P limit exceeded
            if (failureReason.contains("limit")) {
                log.warn("DLQ: P2P limit exceeded: senderId={}", senderId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

            // Strategy 5: Notification failure (non-critical)
            if (failureReason.contains("notification")) {
                return DlqProcessingResult.DISCARDED;
            }

            // Strategy 6: Duplicate
            if (failureReason.contains("duplicate")) {
                return DlqProcessingResult.DISCARDED;
            }

            log.warn("DLQ: P2P payment failed: paymentId={}, sender={}, receiver={}", paymentId, senderId, receiverId);
            return DlqProcessingResult.RETRY;

        } catch (Exception e) {
            log.error("DLQ: Error in P2P payment handler", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "P2PPaymentEventConsumer";
    }
}
