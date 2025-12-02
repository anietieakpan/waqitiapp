package com.waqiti.rewards.kafka;

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
 * DLQ Handler for PaymentCompletedEventConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class PaymentCompletedEventConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public PaymentCompletedEventConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PaymentCompletedEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.PaymentCompletedEventConsumer.dlq:PaymentCompletedEventConsumer.dlq}",
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
            // FIXED: Implement payment completed recovery logic
            log.warn("Processing DLQ payment completed event for recovery");

            // Extract event details
            String paymentId = headers.getOrDefault("paymentId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Payment: {}", paymentId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in payment completion. Payment: {}, User: {}. Manual review required.",
                        paymentId, userId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyProcessed")) {
                // Payment already processed - can be safely ignored
                log.info("DLQ: Duplicate payment completion event detected. Payment: {}. Marking as resolved.",
                        paymentId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("RewardCalculationFailed")) {
                // Reward calculation failed but payment succeeded - retry reward calculation
                log.warn("DLQ: Reward calculation failed for completed payment. Payment: {}. Retrying.", paymentId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("UserNotFound") || failureReason.contains("WalletNotFound")) {
                // User/wallet data not available yet - retry later (eventual consistency)
                log.warn("DLQ: User/wallet not found for payment. User: {}, Payment: {}. Retrying.",
                        userId, paymentId);
                return DlqProcessingResult.RETRY;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in payment completion event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ payment completed event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "PaymentCompletedEventConsumer";
    }
}
