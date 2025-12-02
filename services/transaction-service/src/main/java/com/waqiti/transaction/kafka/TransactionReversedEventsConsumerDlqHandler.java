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
 * DLQ Handler for TransactionReversedEventsConsumer
 *
 * Handles failed messages from the dead letter topic
 *
 * @author Waqiti Engineering Team (Auto-generated)
 * @version 1.0.0
 */
@Service
@Slf4j
public class TransactionReversedEventsConsumerDlqHandler extends BaseDlqConsumer<Object> {

    public TransactionReversedEventsConsumerDlqHandler(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @PostConstruct
    public void init() {
        initializeMetrics("TransactionReversedEventsConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.TransactionReversedEventsConsumer.dlq:TransactionReversedEventsConsumer.dlq}",
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
            // FIXED: Implement transaction reversed recovery logic
            log.warn("Processing DLQ transaction reversed event for recovery");

            // Extract event details
            String transactionId = headers.getOrDefault("transactionId", "").toString();
            String originalTransactionId = headers.getOrDefault("originalTransactionId", "").toString();
            String reversalReason = headers.getOrDefault("reversalReason", "").toString();
            String failureReason = headers.getOrDefault("failureReason", "").toString();

            // Attempt recovery based on failure reason
            if (failureReason.contains("Database") || failureReason.contains("Connection")) {
                log.info("DLQ: Database error detected, marking for retry. Transaction: {}", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("OriginalTransactionNotFound")) {
                log.warn("DLQ: Original transaction not found for reversal. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("Duplicate") || failureReason.contains("AlreadyReversed")) {
                log.info("DLQ: Transaction already reversed. Transaction: {}. Marking as resolved.", transactionId);
                return DlqProcessingResult.DISCARDED;
            } else if (failureReason.contains("FundsReversalFailed")) {
                log.error("DLQ: Funds reversal failed. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("NotificationFailed")) {
                log.warn("DLQ: Reversal notification failed. Transaction: {}. Retrying.", transactionId);
                return DlqProcessingResult.RETRY;
            } else if (failureReason.contains("ReconUpdateFailed")) {
                log.warn("DLQ: Reconciliation update failed for reversal. Transaction: {}. Manual review required.", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else if (failureReason.contains("Validation") || failureReason.contains("Invalid")) {
                log.error("DLQ: Validation error in transaction reversed. Transaction: {}. Manual review required.", transactionId);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            } else {
                log.error("DLQ: Unknown error in transaction reversed event. Event: {}, Headers: {}. Requires investigation.",
                        event, headers);
                return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
            }
        } catch (Exception e) {
            log.error("Error handling DLQ transaction reversed event", e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    @Override
    protected String getServiceName() {
        return "TransactionReversedEventsConsumer";
    }
}
