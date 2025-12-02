package com.waqiti.transaction.kafka;

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
 * DLQ Handler for TransactionAuthorizedEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class TransactionAuthorizedEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public TransactionAuthorizedEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("TransactionAuthorizedEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.TransactionAuthorizedEventsConsumer.dlq:TransactionAuthorizedEventsConsumer.dlq}",
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
            // FIXED: Implement transaction authorized recovery logic
            log.warn("Processing DLQ transaction authorized event for recovery");

            // Extract event details
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String userId = headers.getOrDefault("userId", "").toString();
            String amount = headers.getOrDefault("amount", "0").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                // Transient database error - safe to retry
                log.info("DLQ: Database error detected, marking for retry. Transaction: {}", transactionId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("WalletNotFound") || failureReason.contains("AccountNotFound")) {
                // Wallet/account doesn't exist yet - retry later
                log.warn("DLQ: Wallet/account not found. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;

            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                // Data validation error - needs manual fix
                log.error("DLQ: Validation error in transaction authorized. Transaction: {}. Manual review required.",
                        transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;

            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyAuthorized")) {
                // Transaction already authorized - can be safely ignored
                log.info("DLQ: Duplicate authorization event. Transaction: {}. Marking as resolved.",
                        transactionId);
                return DlqProcessingResult.DISCARDED;

            } else if (failureReason.contains("RewardCalculationFailed")) {
                // Reward calculation failed but authorization succeeded - retry rewards
                log.warn("DLQ: Reward calculation failed. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;

            } else {
                // Unknown error - log for investigation
                log.error("DLQ: Unknown error in transaction authorized event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }

        } catch (Exception e) {
            log.error("Error handling DLQ transaction authorized event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "TransactionAuthorizedEventsConsumer";
    }
}
